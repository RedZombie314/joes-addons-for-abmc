package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * 附体召唤的<b>骷髅马骑士</b>的飞行驱动：坐骑像蝙蝠一样飞（不落地、免疫摔落），
 * 但目标点不是随机漫游，而是<b>朝共享目标冲过去</b>。
 *
 * <p>飞行方式照搬幸运实体事件 {@code LuckySkeletonKnightEvent}（那里是随机漫游）：
 * 每刻把速度朝"指向目标、大小为巡航速度"的方向慢慢靠拢；离地太近就额外往上抬，
 * 保证不落地也不会卡进地里。区别只在于目标点的选择：
 * <ol>
 *   <li>骑手有目标（{@link OrbSummonTargetGoal} 抄来的空壳目标）→ 冲向它，近到
 *       {@link #KEEP_DISTANCE} 格内就减速悬停，免得整匹马糊在敌人身上；</li>
 *   <li>没目标 → 跟随盟友空壳（在它上方盘旋）；</li>
 *   <li>连盟友都找不到了 → 原地悬停。</li>
 * </ol>
 *
 * <p>为什么必须自己驱动：坐骑的地面 AI 全被清掉了，而它又是 {@code noGravity}
 * （不能改用 {@code setNoAi(true)}，那样连 {@code LivingEntity#travel} 的物理推进都会被跳过，
 * 马就彻底不动了）。骑手不需要驱动物理，它的弓术 goal 会自己朝目标射箭。
 */
public final class OrbSkeletonKnightFlight {

    /** 巡航速度（格/刻；约 6~7 格/秒，与幸运事件一致）。 */
    private static final double CRUISE_SPEED = 0.34D;

    /** 每刻把速度朝巡航速度靠拢的比例（越小越飘）。 */
    private static final double STEER = 0.22D;

    /** 飞行时至少高出地表这么多格（保证"不落地"）。 */
    private static final double MIN_ABOVE_GROUND = 3.0D;

    /** 贴地时额外向上的加速度。 */
    private static final double CLIMB_BOOST = 0.16D;

    /** 离共享目标近到这个距离就减速悬停（别糊在敌人身上）。 */
    private static final double KEEP_DISTANCE = 4.0D;

    /** 已召唤、仍在追随的骷髅马骑士。 */
    private static final List<Knight> KNIGHTS = new ArrayList<>();

    /** 重新认领"坐骑+骑手"的间隔（刻）：1 秒。见 {@link #rescan}。 */
    private static final int RESCAN_INTERVAL_TICKS = 20;

    /**
     * 上一次重扫的游戏刻。
     * <p>
     * 初值写成 {@code -RESCAN_INTERVAL_TICKS} 而不是 {@code Long.MIN_VALUE}：
     * 判断式是 {@code server.getTickCount() - lastRescanTick >= 间隔}，
     * 而 {@code 0L - Long.MIN_VALUE} 会溢出成负数、把重扫永久卡死（同
     * {@link OrbAllyWitherControl} 与 {@link OrbPossessionSummons} 里记的那个坑）。
     */
    private static long lastRescanTick = -RESCAN_INTERVAL_TICKS;

    private OrbSkeletonKnightFlight() {
    }

    /** 一只骷髅马骑士：坐骑、骑手、以及盟友（那具被附体的空壳）。 */
    private record Knight(ServerLevel level, SkeletonHorse horse, Skeleton rider, UUID allyUuid) {
    }

    /** 登记一只刚召唤出来的骷髅马骑士（由 {@code OrbPossessedAttackEvents} 调用）。 */
    public static void register(ServerLevel level, SkeletonHorse horse, Skeleton rider, UUID allyUuid) {
        KNIGHTS.add(new Knight(level, horse, rider, allyUuid));
    }

    /** 由服务端 tick 钩子（{@code ModMain.onServerTickPre}）每刻调用。 */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (server.getTickCount() - lastRescanTick >= RESCAN_INTERVAL_TICKS) {
            lastRescanTick = server.getTickCount();
            rescan(server);
        }
        if (KNIGHTS.isEmpty()) {
            return;
        }
        Iterator<Knight> iterator = KNIGHTS.iterator();
        while (iterator.hasNext()) {
            Knight knight = iterator.next();
            boolean horseAlive = knight.horse().isAlive() && !knight.horse().isRemoved();
            boolean riderAlive = knight.rider().isAlive() && !knight.rider().isRemoved();
            if (!horseAlive && !riderAlive) {
                iterator.remove();
                continue;
            }
            if (!horseAlive) {
                // 坐骑被打死了：骑手会掉下来，继续替它清零坠落距离（摔落免疫不断）
                knight.rider().fallDistance = 0.0F;
                continue;
            }
            fly(knight, riderAlive);
        }
    }

    /**
     * 重新认领"坐骑 + 骑手"。
     *
     * <h2>为什么需要它</h2>
     * 变形复原是"照 NBT 重新 new 一个实体"（{@code ModMain.revertLivingShell} /
     * {@code respawnTransmutedEntity}）：名册里那两只旧对象已经被 {@code discard}，
     * 复原出来的新坐骑/新骑手<b>没人驱动</b> —— 坐骑是 {@code noGravity} 的会一直飘，
     * 骑手则掉到地上自己走（它已经不在马背上了）。
     * 所以每秒扫一遍世界，把"召唤标记 + 角色标记"都对上的坐骑/骑手重新配对；
     * 顺便把名册里"坐骑还活着、骑手已经换人"的条目更新成马背上那位。
     */
    private static void rescan(net.minecraft.server.MinecraftServer server) {
        java.util.ListIterator<Knight> iterator = KNIGHTS.listIterator();
        while (iterator.hasNext()) {
            Knight knight = iterator.next();
            boolean horseAlive = knight.horse().isAlive() && !knight.horse().isRemoved();
            boolean riderAlive = knight.rider().isAlive() && !knight.rider().isRemoved();
            // 坐骑还在、骑手却没了（骑手被变形/打死）：如果马背上现在坐着另一只召唤骑手，就改认它
            if (horseAlive && !riderAlive
                && knight.horse().getFirstPassenger() instanceof Skeleton seated
                && isSummonRider(seated)) {
                iterator.set(new Knight(knight.level(), knight.horse(), seated, knight.allyUuid()));
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                // 这个整合包里 getAllEntities() 会混进 null 元素，逐个判空
                if (!(entity instanceof SkeletonHorse horse) || !horse.isAlive()
                    || isRegistered(horse)) {
                    continue;
                }
                if (!OrbPossessionSummons.isSummon(horse)
                    || !OrbPossessionSummons.ROLE_KNIGHT_HORSE.equals(OrbPossessionSummons.roleOf(horse))) {
                    continue;
                }
                if (horse.getFirstPassenger() instanceof Skeleton rider && isSummonRider(rider)) {
                    KNIGHTS.add(new Knight(level, horse, rider, OrbPossessionSummons.allyOf(horse)));
                }
            }
        }
        remountOrphanRiders(server);
    }

    /**
     * 给"落单的召唤骑手"找回它的马。
     * <p>
     * 骑手被变形又复原时，复原出来的是一只<b>站在地上的新骷髅</b>（骑乘关系不在 NBT 里，
     * 复原不会让它自动回到马背上），而那只己方坐骑还在天上飘着 —— 骷髅马骑士就这么散架了。
     * 这里按"同一颗核心 + 同维度 + 16 格内 + 马背上空着"把两者重新配起来，
     * 复原之后最多 1 秒就恢复成完整的骑士。
     */
    private static void remountOrphanRiders(net.minecraft.server.MinecraftServer server) {
        java.util.List<SkeletonHorse> freeHorses = new java.util.ArrayList<>();
        java.util.List<Skeleton> orphanRiders = new java.util.ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity == null) {
                    continue;
                }
                if (entity instanceof SkeletonHorse horse && horse.isAlive()
                    && OrbPossessionSummons.isSummon(horse)
                    && OrbPossessionSummons.ROLE_KNIGHT_HORSE.equals(OrbPossessionSummons.roleOf(horse))
                    && horse.getFirstPassenger() == null) {
                    freeHorses.add(horse);
                } else if (entity instanceof Skeleton rider && rider.isAlive() && rider.getVehicle() == null
                    && isSummonRider(rider)) {
                    orphanRiders.add(rider);
                }
            }
        }
        for (Skeleton rider : orphanRiders) {
            SkeletonHorse best = null;
            double bestDistance = REMOUNT_RANGE * REMOUNT_RANGE;
            for (SkeletonHorse horse : freeHorses) {
                if (horse.getFirstPassenger() != null || horse.level() != rider.level()) {
                    continue;
                }
                UUID horseOrb = OrbPossessionSummons.orbOf(horse);
                if (horseOrb == null || !horseOrb.equals(OrbPossessionSummons.orbOf(rider))) {
                    continue;   // 只认同一颗核心召唤出来的那一对
                }
                double distance = horse.distanceToSqr(rider);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = horse;
                }
            }
            if (best != null) {
                rider.startRiding(best, true);
                if (best.level() instanceof ServerLevel serverLevel) {
                    KNIGHTS.add(new Knight(serverLevel, best, rider, OrbPossessionSummons.allyOf(best)));
                    cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
                        "[召唤物] 骷髅马骑士复原归位: 骑手 {} 重新骑上己方骷髅马", rider.getUUID());
                }
            }
        }
    }

    /** 复原归位时允许骑手与坐骑相距的最大格数。 */
    private static final double REMOUNT_RANGE = 16.0D;

    /** 这只骷髅是不是"附体召唤的骑士骑手"。 */
    private static boolean isSummonRider(Skeleton rider) {
        return OrbPossessionSummons.isSummon(rider)
            && OrbPossessionSummons.ROLE_KNIGHT_RIDER.equals(OrbPossessionSummons.roleOf(rider));
    }

    // ====================== 变形药水的特殊处理：骑手 ↔ 坐骑 配对 ======================

    /**
     * 骑手/坐骑身上记着"我的另一半是谁"的键（写在 persistentData 里，随变形数据一起存下来）。
     */
    private static final String TAG_PARTNER = "jafa_knight_partner";

    /**
     * 变形前调用：把自己那一半（骑手→坐骑 / 坐骑→骑手）的 UUID 记进自己的持久化数据。
     * <p>
     * 骑乘关系<b>不在实体 NBT 里</b>，而变形复原是"照 NBT 重新 new 一个实体"，
     * 于是复原出来的骑手会站在地上、坐骑在天上飘着 —— 骑士当场散架。
     * 记在 persistentData 里就会随 {@code entity.save(entityNbt)} 一起进变形数据，
     * 复原出来的那一只身上便带着"我的另一半是谁"，可以立刻重新骑上去
     * （见 {@link #restoreMountAfterRevert}；另一半也还在变形形态时，交给每秒的重扫兜底配对）。
     */
    public static void notePartnerBeforeMorph(net.minecraft.world.entity.Entity entity) {
        net.minecraft.world.entity.Entity partner = null;
        if (entity instanceof Skeleton rider && isSummonRider(rider)) {
            partner = rider.getVehicle();
        } else if (entity instanceof SkeletonHorse horse && isSummonHorse(horse)) {
            partner = horse.getFirstPassenger();
        }
        if (partner != null && OrbPossessionSummons.isSummon(partner)) {
            entity.getPersistentData().putUUID(TAG_PARTNER, partner.getUUID());
        }
    }

    /**
     * 变形复原后调用（见 {@code ModMain#repairRebuiltPossessionSummon}）：
     * 按变形前记下的 {@link #TAG_PARTNER} 把骑手放回马背上。
     * <p>
     * 读不到记录、或另一半还没变回来（还在变形形态里）就什么都不做 ——
     * 剩下那种"两边都在变形"的情况由 {@link #remountOrphanRiders} 每秒兜底配对。
     */
    public static void restoreMountAfterRevert(net.minecraft.world.entity.Entity rebuilt) {
        net.minecraft.nbt.CompoundTag data = rebuilt.getPersistentData();
        if (!data.hasUUID(TAG_PARTNER) || !(rebuilt.level() instanceof ServerLevel level)) {
            return;
        }
        UUID partnerUuid = data.getUUID(TAG_PARTNER);
        data.remove(TAG_PARTNER);   // 用过就清，免得以后再乱配一对
        net.minecraft.world.entity.Entity partner = level.getEntity(partnerUuid);
        if (partner == null) {
            return;
        }
        if (rebuilt instanceof Skeleton rider && isSummonRider(rider)
            && partner instanceof SkeletonHorse horse && isSummonHorse(horse)
            && horse.getFirstPassenger() == null) {
            rider.startRiding(horse, true);
            registerIfAbsent(level, horse, rider);
            cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
                "[召唤物] 骷髅马骑士复原归位: 骑手重新骑上变形前那只己方骷髅马");
        } else if (rebuilt instanceof SkeletonHorse horse && isSummonHorse(horse)
            && partner instanceof Skeleton rider && isSummonRider(rider)
            && rider.getVehicle() == null) {
            rider.startRiding(horse, true);
            registerIfAbsent(level, horse, rider);
            cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
                "[召唤物] 骷髅马骑士复原归位: 坐骑找回变形前那位骑手");
        }
    }

    /** 这只骷髅马是不是"附体召唤的骑士坐骑"。 */
    private static boolean isSummonHorse(SkeletonHorse horse) {
        return OrbPossessionSummons.isSummon(horse)
            && OrbPossessionSummons.ROLE_KNIGHT_HORSE.equals(OrbPossessionSummons.roleOf(horse));
    }

    /** 把这一对补进飞行驱动名册（已在册就不重复，免得同一只马被驱动两遍）。 */
    private static void registerIfAbsent(ServerLevel level, SkeletonHorse horse, Skeleton rider) {
        if (!isRegistered(horse)) {
            KNIGHTS.add(new Knight(level, horse, rider, OrbPossessionSummons.allyOf(horse)));
        }
    }

    /** 这只坐骑是不是已经在名册里了（按对象身份比，不用 UUID：复原会换 UUID）。 */
    private static boolean isRegistered(SkeletonHorse horse) {
        for (Knight knight : KNIGHTS) {
            if (knight.horse() == horse) {
                return true;
            }
        }
        return false;
    }

    private static void fly(Knight knight, boolean riderAlive) {
        SkeletonHorse horse = knight.horse();
        horse.setNoGravity(true);   // 保险：任何情况下都不受重力
        horse.fallDistance = 0.0F;  // 免疫摔落伤害

        if (riderAlive) {
            Skeleton rider = knight.rider();
            rider.fallDistance = 0.0F;
            rider.clearFire();
            // 骑手跟着坐骑转，不然会"横着骑马"
            rider.setYRot(horse.getYRot());
            rider.setYHeadRot(horse.getYHeadRot());
            rider.setYBodyRot(horse.getYRot());
        }

        Vec3 wanted = wantedVelocity(knight);
        Vec3 current = horse.getDeltaMovement();
        Vec3 next = current.add(wanted.subtract(current).scale(STEER));
        horse.setDeltaMovement(next);

        faceTowards(horse, next);
    }

    /** 本刻"想要的速度"：朝目标巡航，太近就减速悬停，贴地就往上抬。 */
    private static Vec3 wantedVelocity(Knight knight) {
        SkeletonHorse horse = knight.horse();
        Vec3 goal = resolveGoal(knight);

        Vec3 toGoal = goal.subtract(horse.position());
        Vec3 wanted;
        if (toGoal.lengthSqr() < KEEP_DISTANCE * KEEP_DISTANCE) {
            // 已经贴上去了：减速悬停（0.5 衰减），让骑手安心射箭
            wanted = horse.getDeltaMovement().scale(0.5D);
        } else if (toGoal.lengthSqr() < 1.0E-6D) {
            wanted = Vec3.ZERO;
        } else {
            wanted = toGoal.normalize().scale(CRUISE_SPEED);
        }

        // 离地太近就往上抬，保证"不会落地"
        int groundY = knight.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Mth.floor(horse.getX()), Mth.floor(horse.getZ()));
        if (horse.getY() < groundY + MIN_ABOVE_GROUND) {
            wanted = wanted.add(0.0D, CLIMB_BOOST, 0.0D);
        }
        return wanted;
    }

    /** 想去的位置：共享目标 → 盟友空壳上方 → 原地。 */
    private static Vec3 resolveGoal(Knight knight) {
        LivingEntity target = knight.rider().getTarget();
        if (target != null && target.isAlive()) {
            return target.position();
        }
        if (knight.allyUuid() != null && knight.level().getEntity(knight.allyUuid()) instanceof PlayerShellEntity shell) {
            LivingEntity shellTarget = shell.getTarget();
            if (shellTarget != null && shellTarget.isAlive()) {
                return shellTarget.position();
            }
            // 没目标就盘旋在盟友上方
            return shell.position().add(0.0D, 3.0D, 0.0D);
        }
        return knight.horse().position();
    }

    /** 让坐骑朝着飞行方向（水平方向决定偏航角，垂直方向给一点点俯仰）。与幸运事件同一套算法。 */
    private static void faceTowards(SkeletonHorse horse, Vec3 velocity) {
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (horizontal > 0.02D) {
            float yaw = (float) (Mth.atan2(velocity.z, velocity.x) * (180.0D / Math.PI)) - 90.0F;
            horse.setYRot(yaw);
            horse.setYHeadRot(yaw);
            horse.setYBodyRot(yaw);
        }
        float pitch = (float) (-Mth.atan2(velocity.y, horizontal) * (180.0D / Math.PI)) * 0.5F;
        horse.setXRot(Mth.clamp(pitch, -30.0F, 30.0F));
    }

    /** 供摔落免疫查询：这个实体是不是本驱动管理的坐骑/骑手。 */
    public static boolean isManaged(@Nullable Object entity) {
        for (Knight knight : KNIGHTS) {
            if (knight.horse() == entity || knight.rider() == entity) {
                return true;
            }
        }
        return false;
    }
}
