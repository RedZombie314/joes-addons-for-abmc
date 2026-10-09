package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 幸运实体子事件：<b>正上方落下红石块，随后一连串下落的 TNT</b>。
 *
 * <ol>
 *   <li>破坏点<b>正上方 6 格</b>生成一个下落的<b>红石块</b>（立即）——它是设计上的"点火器"；</li>
 *   <li>随后在<b>正上方 12 格</b>处，<b>每 5 游戏刻</b>生成一个下落的 <b>TNT</b>，共 <b>5 个</b>（依次、不是同时）。</li>
 * </ol>
 *
 * <h3>机制（下落 → 固化 → 立刻点燃 → 失去碰撞箱 → 下一个继续落到红石块上）</h3>
 * 生成的是<b>未点燃的下落 TNT 方块</b>（{@link ModBlocks#LUCKY_TNT}）。它落地"固化"成方块的瞬间，
 * 方块自身会立即点燃（见 {@link LuckyTntBlock#onPlace}）—— 也就是落点旁边有红石块时的那种效果；
 * 点燃后它变成没有碰撞箱的已点燃 TNT 实体，于是<b>下一个下落的 TNT 会穿过它继续落到红石块上</b>，
 * 形成连锁，而不会堆成一摞。
 *
 * <p>引信为 {@link LuckyTntBlock#IGNITED_FUSE_TICKS} 刻（3 秒）。若在半空或落地后<b>被爆炸波及</b>，
 * 则立刻引爆（无视自身引信）。
 */
public final class LuckyFallingVolleyEvent {
    /** 红石块生成高度（破坏点正上方格数）。 */
    private static final int REDSTONE_HEIGHT = 6;
    /** TNT 生成高度（破坏点正上方格数）。 */
    private static final int TNT_HEIGHT = 12;
    /** TNT 个数。 */
    private static final int TNT_COUNT = 5;
    /** TNT 生成间隔（游戏刻）。 */
    private static final int TNT_INTERVAL_TICKS = 5;
    /** 标在"被爆炸波及，需要立刻引爆"的 TNT 上的标记。 */
    private static final String IMMEDIATE_TAG = "jafa_lucky_volley_immediate";

    /** 正在进行的"TNT 连发"排程。 */
    private static final List<Volley> VOLLEYS = new ArrayList<>();
    /** 仍在半空的下落 TNT（仅用于判定"在半空被爆炸波及"）。 */
    private static final List<FallingBlockEntity> FALLING_TNT = new ArrayList<>();

    private LuckyFallingVolleyEvent() {
    }

    /** 一次连发排程。 */
    private static final class Volley {
        private final ServerLevel level;
        private final BlockPos pos;
        private int spawned;
        private int cooldown = TNT_INTERVAL_TICKS;

        private Volley(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("entity/falling_volley"), LuckyEventCategory.LUCKY_ENTITY,
            "正上方 6 格落下红石块，随后每 5 刻自 12 格高处落下一个 TNT（固化即点燃、引信 60 刻；被炸则立即爆），共 5 个",
            LuckyFallingVolleyEvent::start);
        LuckyEvents.register(event);
        return event;
    }

    private static void start(ServerLevel level, BlockPos pos, @Nullable Player player) {
        // ① 立即：正上方 6 格的下落红石块（点火器）
        FallingBlockEntity.fall(level, pos.above(REDSTONE_HEIGHT), Blocks.REDSTONE_BLOCK.defaultBlockState());
        // ② 排程：正上方 12 格、每 5 刻一个、共 5 个下落 TNT
        VOLLEYS.add(new Volley(level, pos.immutable()));
    }

    /** 由服务端 tick 钩子（{@code ModMain.onServerTickPre}）每刻调用。 */
    public static void tick() {
        tickVolleys();
        FALLING_TNT.removeIf(FallingBlockEntity::isRemoved);
    }

    private static void tickVolleys() {
        if (VOLLEYS.isEmpty()) return;
        Iterator<Volley> iterator = VOLLEYS.iterator();
        while (iterator.hasNext()) {
            Volley volley = iterator.next();
            if (volley.spawned >= TNT_COUNT) {
                iterator.remove();
                continue;
            }
            if (--volley.cooldown > 0) {
                continue;
            }
            volley.cooldown = TNT_INTERVAL_TICKS;
            if (volley.level.isLoaded(volley.pos)) {
                // 用原版 TNT：落地固化时紧邻红石块 → 被原版逻辑点燃 → 方块移除、变成无碰撞箱的已点燃 TNT，
                // 下一个 TNT 便穿过它继续落到红石块上（不会叠起来）
                FallingBlockEntity tnt = FallingBlockEntity.fall(volley.level, volley.pos.above(TNT_HEIGHT),
                    Blocks.TNT.defaultBlockState());
                FALLING_TNT.add(tnt);
            }
            volley.spawned++;
        }
    }

    /**
     * 爆炸波及判定（由 {@code ModMain.onExplosionDetonate} 转发）。
     * <p>已固化的 TNT 由方块自身的 {@code wasExploded} 处理（引信 0）；
     * 这里只补"还在半空的下落 TNT"：原地生成引信 0 的已点燃 TNT，让它立刻爆炸。
     */
    public static void onExplosionDetonate(ServerLevel level, List<BlockPos> affectedBlocks,
                                           List<Entity> affectedEntities) {
        if (FALLING_TNT.isEmpty()) return;
        Iterator<FallingBlockEntity> iterator = FALLING_TNT.iterator();
        while (iterator.hasNext()) {
            FallingBlockEntity entity = iterator.next();
            if (entity.level() != level || !affectedEntities.contains(entity)) continue;
            BlockPos pos = entity.blockPosition();
            entity.discard();
            iterator.remove();
            PrimedTnt primed = new PrimedTnt(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, null);
            primed.setFuse(0);
            primed.getPersistentData().putBoolean(IMMEDIATE_TAG, true);
            level.addFreshEntity(primed);
        }
    }
}
