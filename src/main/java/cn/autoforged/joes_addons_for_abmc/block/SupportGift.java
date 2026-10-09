package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.block.entity.SupportLuckyBlockEntity;
import cn.autoforged.joes_addons_for_abmc.entity.PlayerShellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * <b>操控送出的落点与余波</b>（需求 6.5.21）：把刚送出的东西装进一个
 * {@link SupportLuckyBlock}，生成在目标玩家附近；同时让附近的一具附体空壳短暂"坠机"。
 *
 * <h3>生成位置（需求原文的优先级）</h3>
 * <ol>
 *   <li>水平方向：目标玩家 <b>3 格以内</b>（{@link #PLACE_RADIUS}）；</li>
 *   <li>竖直方向：<b>优先选玩家 y 值等高处或更高</b>（先试 {@code dy ∈ [0, +3]}），
 *       那一圈全都不行（被方块占着 / 出世界高度）才退到<b>下半球</b>（{@code dy ∈ [-3, -1]}）；</li>
 *   <li>只取代<b>空气</b>方块；</li>
 *   <li>尽量不与玩家所在位置重合：候选点若与玩家的碰撞箱相交就跳过。</li>
 * </ol>
 * 找不到落点就返回 false（调用方会把这种情况记进日志；内容本身不会因此丢失——
 * 调用方在真正放好之前不会把东西从选择器上摘下来）。
 */
public final class SupportGift {

    /** 生成位置到目标玩家的水平最大距离（格）：需求给的 3。 */
    public static final int PLACE_RADIUS = 3;

    /** 余波作用的空壳半径（格）：需求给的 50。 */
    public static final double SHELL_RADIUS = 50.0D;

    /** 空壳失去 AI 的时长（刻）：需求给的 2 秒。 */
    public static final int SHELL_STUN_TICKS = 40;

    /** 每个高度层最多试几个候选点（水平偏移是随机取的）。 */
    private static final int ATTEMPTS_PER_LAYER = 8;

    private SupportGift() {
    }

    /**
     * 把内容送到目标玩家身边：摆一个支援幸运方块、装上内容、再让附近一具附体空壳坠机。
     *
     * @param mobTag 生物内容（{@code Entity#save} 的结果）；物品内容传 {@code null}
     * @param item   物品内容；没有物品传 {@link ItemStack#EMPTY}
     * @return 成功放下返回 true；没有合法落点返回 false（此时<b>什么都没发生</b>，内容还在选择器上）
     */
    public static boolean deliver(ServerLevel level, ServerPlayer target, @Nullable CompoundTag mobTag, ItemStack item) {
        BlockPos pos = findPlacement(level, target);
        if (pos == null) {
            ModMain.LOGGER.warn("[支援] 在 {} 附近 3 格内找不到可用的空气方块，这次送出取消（内容留在选择器上）",
                target.getGameProfile().getName());
            return false;
        }
        level.setBlock(pos, ModBlocks.SUPPORT_LUCKY_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof SupportLuckyBlockEntity be) {
            be.setContent(mobTag, item);
        } else {
            ModMain.LOGGER.warn("[支援] 方块实体没建起来（{}），内容可能丢失", pos);
        }
        stunOneShellNear(level, target);
        ModMain.LOGGER.info("[支援] 已在 {} 附近 {} 生成支援幸运方块（内容：{}）",
            target.getGameProfile().getName(), pos,
            mobTag != null ? mobTag.getString("id") : item.getHoverName().getString());
        return true;
    }

    /** 找一个落点：先"玩家等高处或更高"的一圈，再退到下半球；只取代空气、不与玩家重合。 */
    @Nullable
    private static BlockPos findPlacement(ServerLevel level, ServerPlayer target) {
        BlockPos origin = target.blockPosition();
        AABB playerBox = target.getBoundingBox();
        // 竖直方向分两轮：第一轮 dy ∈ [0, +3]（等高或更高），第二轮 dy ∈ [-3, -1]（下半球）
        for (int round = 0; round < 2; round++) {
            for (int attempt = 0; attempt < PLACE_RADIUS * 2 * ATTEMPTS_PER_LAYER; attempt++) {
                int dx = level.random.nextInt(PLACE_RADIUS * 2 + 1) - PLACE_RADIUS;
                int dz = level.random.nextInt(PLACE_RADIUS * 2 + 1) - PLACE_RADIUS;
                int dy = round == 0
                    ? level.random.nextInt(PLACE_RADIUS + 1)                  // 0 .. +3
                    : -1 - level.random.nextInt(PLACE_RADIUS);                // -1 .. -3
                BlockPos pos = origin.offset(dx, dy, dz);
                if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
                    continue;
                }
                // 尽量不与玩家重合
                if (playerBox.intersects(new AABB(pos))) {
                    continue;
                }
                if (!level.isEmptyBlock(pos)) {
                    continue;
                }
                return pos;
            }
        }
        return null;
    }

    /**
     * 需求：这种方块出现时，玩家 50 格以内的<b>被附体空壳</b>随机挑一具，让它 2 秒内失去 AI
     * （表现为"坠机"：飞行是 goals 推的，NoAI 之后飞不起来，但重力照旧、也照旧能落地水）。
     */
    private static void stunOneShellNear(ServerLevel level, ServerPlayer target) {
        List<PlayerShellEntity> shells = level.getEntitiesOfClass(PlayerShellEntity.class,
            target.getBoundingBox().inflate(SHELL_RADIUS),
            shell -> shell.isAlive() && shell.isOrbAttached());
        if (shells.isEmpty()) {
            return;
        }
        PlayerShellEntity shell = shells.get(level.random.nextInt(shells.size()));
        shell.applyPilotStun(SHELL_STUN_TICKS);
        ModMain.LOGGER.info("[支援] {} 附近 {} 格内挑中一具附体空壳让它坠机 {} 刻：{}",
            target.getGameProfile().getName(), (int) SHELL_RADIUS, SHELL_STUN_TICKS,
            new Vec3(shell.getX(), shell.getY(), shell.getZ()));
    }

    /**
     * 把存下来的生物 NBT 原样放出来（支援方块碎裂时调用）。
     * <p>
     * 复用幸运名单那一套"还原元素"的做法：抹掉 {@code UUID}/{@code Pos}（新实体要拿新 UUID、
     * 位置要以方块为准），其余数据（名字、血量、装备、驯服状态……）原样保留。
     * <p>
     * <b>放出来之后还要登记成"支援生物"</b>（用户指定）：具备攻击性的那只（比如 Bob）
     * 索敌对象将永远是 50 格以内的幸运核心（附体的玩家），且绝不会锁被核心索敌的玩家 ——
     * 见 {@link cn.autoforged.joes_addons_for_abmc.entity.SupportMobControl}。
     */
    public static void spawnStoredMob(ServerLevel level, BlockPos pos, CompoundTag stored) {
        net.minecraft.world.entity.Entity spawned =
            LuckyRosterEvents.spawnStoredEntity(level, pos, stored);
        cn.autoforged.joes_addons_for_abmc.entity.SupportMobControl.markAndInstall(spawned);
    }
}
