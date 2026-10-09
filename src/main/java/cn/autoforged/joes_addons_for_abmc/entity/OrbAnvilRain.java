package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * "铁砧雨"：被附体空壳的一种远程攻击 —— 在目标头顶连续砸下 10~15 颗铁砧。
 *
 * <h2>投放规则（用户定义）</h2>
 * <ul>
 *   <li>每 {@link #ANVIL_INTERVAL_TICKS} 游戏刻一颗，共 {@link #MIN_ANVILS}~{@link #MAX_ANVILS} 颗
 *       （用户后续要求：数量由 10~15 削减为 <b>5~7</b>）；</li>
 *   <li>高度：目标<b>碰撞箱顶面</b>再往上 {@link #MIN_HEIGHT_ABOVE_BOX}~{@link #MAX_HEIGHT_ABOVE_BOX} 格；</li>
 *   <li>水平位置：在目标碰撞箱的 XZ 范围上随机取一点，并且<b>四边各外扩
 *       {@link #HORIZONTAL_MARGIN} 格</b> —— 所以目标碰撞箱在 XZ 方向越大，铁砧撒开的范围也越大
 *       （这就是"围绕碰撞箱变动、可 ±1"的意思）。</li>
 * </ul>
 * 每颗铁砧都在<b>投放那一刻</b>重新读目标的碰撞箱，所以目标边跑边挨砸，铁砧会跟着跑。
 *
 * <h2>为什么用 NBT 造铁砧而不是 {@code FallingBlockEntity.fall(...)}</h2>
 * {@code fall(level, pos, state)} 会把 {@code pos} 那一格<b>替换成流体方块</b>（通常是空气，
 * 但若铁砧生成点正好卡在实心方块里——比如室内/洞穴——就会在墙上凿个洞）。
 * 这里改成"建实体 + 灌 NBT"，一个方块都不动。
 *
 * <h2>伤害</h2>
 * 走原版坠落方块的那套：{@code causeFallDamage} 里按坠落距离结算
 * （{@link #FALL_DAMAGE_PER_DISTANCE} 点/格，上限 {@link #FALL_DAMAGE_MAX}），
 * 并沿用 {@code AnvilBlock} 的专属伤害源（死亡播报是"被铁砧砸死"那套）。
 * 落地后铁砧会变成方块留在原地（原版行为），长时间下来会积一堆 —— 见 {@link #ANVIL_BLOCK}。
 */
public final class OrbAnvilRain {

    /** 两颗铁砧之间的间隔（游戏刻）。 */
    private static final int ANVIL_INTERVAL_TICKS = 4;

    /** 一次铁砧雨最少几颗（用户指定：由 10~15 削减为 5~7）。 */
    private static final int MIN_ANVILS = 5;

    /** 一次铁砧雨最多几颗（用户指定：由 10~15 削减为 5~7）。 */
    private static final int MAX_ANVILS = 7;

    /** 生成高度：碰撞箱顶面往上 10 格起。 */
    private static final int MIN_HEIGHT_ABOVE_BOX = 10;

    /** 生成高度：碰撞箱顶面往上 15 格止。 */
    private static final int MAX_HEIGHT_ABOVE_BOX = 15;

    /** 水平外扩（格）：碰撞箱 XZ 范围四边各外扩这么多。 */
    private static final double HORIZONTAL_MARGIN = 1.0D;

    /** 每格坠落距离的伤害：与原版铁砧手感一致（2 点/格）。 */
    private static final float FALL_DAMAGE_PER_DISTANCE = 2.0F;

    /** 单颗铁砧的伤害上限。 */
    private static final int FALL_DAMAGE_MAX = 40;

    /** 落的铁砧方块。想换成"开裂/损坏的铁砧"改这里即可。 */
    private static final net.minecraft.world.level.block.Block ANVIL_BLOCK = Blocks.ANVIL;

    /**
     * 铁砧身上的标记：它是由本机制召唤出来的。
     * <p>
     * {@link OrbOfLuckEntity#hurt} 靠它认出"这是自己召唤的铁砧雨"，从而给被附体的玩家减伤 75%
     * （和苦力怕弹那条规则对称）；标记随实体一起存盘，读档后依然有效。
     */
    private static final String TAG_RAIN_ANVIL = "jafa_orb_anvil_rain";

    /** 一场进行中的铁砧雨。 */
    private record Rain(ServerLevel level, UUID targetUuid, int remaining, long nextTick) {
    }

    private static final List<Rain> ACTIVE = new ArrayList<>();

    private OrbAnvilRain() {
    }

    /** 开始一场铁砧雨（由 {@link OrbPossessedAttackEvents} 的远程事件调用）。 */
    public static void start(ServerLevel level, LivingEntity target) {
        int count = MIN_ANVILS + level.random.nextInt(MAX_ANVILS - MIN_ANVILS + 1);
        ACTIVE.add(new Rain(level, target.getUUID(), count, level.getGameTime()));
    }

    /** 由服务端 tick 钩子（{@code ModMain.onServerTickPre}）每刻调用。 */
    public static void tick() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        // 用 ListIterator：需要在遍历中把"这一场雨"改成"少一颗、下一颗的刻数已推进"的记录
        java.util.ListIterator<Rain> iterator = ACTIVE.listIterator();
        while (iterator.hasNext()) {
            Rain rain = iterator.next();
            ServerLevel level = rain.level();
            if (level.getGameTime() < rain.nextTick()) {
                continue;
            }
            // 目标没了（死了/卸载了）就停：铁砧雨跟着目标走，没有目标就不再砸
            Entity target = level.getEntity(rain.targetUuid());
            if (!(target instanceof LivingEntity living) || !living.isAlive()) {
                iterator.remove();
                continue;
            }
            dropAnvil(level, living);

            int left = rain.remaining() - 1;
            if (left <= 0) {
                iterator.remove();
            } else {
                // 每 ANVIL_INTERVAL_TICKS 刻一颗
                iterator.set(new Rain(level, rain.targetUuid(), left, rain.nextTick() + ANVIL_INTERVAL_TICKS));
            }
        }
    }

    /** 在目标头顶砸下一颗铁砧。 */
    private static void dropAnvil(ServerLevel level, LivingEntity target) {
        AABB box = target.getBoundingBox();
        RandomSource random = level.random;
        // 水平：碰撞箱 XZ 范围随机取点，四边各外扩 1 格（箱子越大撒得越开）
        double x = box.minX - HORIZONTAL_MARGIN
            + random.nextDouble() * (box.getXsize() + HORIZONTAL_MARGIN * 2.0D);
        double z = box.minZ - HORIZONTAL_MARGIN
            + random.nextDouble() * (box.getZsize() + HORIZONTAL_MARGIN * 2.0D);
        // 垂直：碰撞箱顶面往上 10~15 格
        double y = box.maxY + MIN_HEIGHT_ABOVE_BOX
            + random.nextInt(MAX_HEIGHT_ABOVE_BOX - MIN_HEIGHT_ABOVE_BOX + 1);

        FallingBlockEntity anvil = createAnvil(level, BlockPos.containing(x, y, z));
        if (anvil == null) {
            return;
        }
        anvil.setPos(x, y, z);
        level.addFreshEntity(anvil);
    }

    /** 造一颗"会砸人"的铁砧坠落方块：不走 {@code fall()}，所以不会动任何方块。 */
    private static FallingBlockEntity createAnvil(ServerLevel level, BlockPos pos) {
        FallingBlockEntity anvil = EntityType.FALLING_BLOCK.create(level);
        if (anvil == null) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        tag.put("BlockState", NbtUtils.writeBlockState(ANVIL_BLOCK.defaultBlockState()));
        anvil.load(tag);
        // 必须显式设：NBT 里没写 HurtEntities 时原版只把标志置真、每格伤害仍是 0（砸下去不掉血）
        anvil.setHurtsEntities(FALL_DAMAGE_PER_DISTANCE, FALL_DAMAGE_MAX);
        anvil.setStartPos(pos);
        // 打个标记：被附体的玩家挨到"自己召唤的铁砧雨"时减伤 75%（见 OrbOfLuckEntity#hurt）
        anvil.getPersistentData().putBoolean(TAG_RAIN_ANVIL, true);
        return anvil;
    }

    /**
     * 这颗坠落方块是不是本机制召唤的铁砧雨（{@link OrbOfLuckEntity#hurt} 的减伤判定用）。
     * <p>
     * 任何实体都能查：伤害源里直接来源与归属来源都过一遍即可（铁砧的伤害源是
     * {@code AnvilBlock.getFallDamageSource} 构造的，实体就是这颗坠落方块本身）。
     */
    public static boolean isRainAnvil(@Nullable Entity entity) {
        return entity != null && entity.getPersistentData().getBoolean(TAG_RAIN_ANVIL);
    }
}
