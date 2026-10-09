package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.network.GuardianLaserPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * <b>守卫者光波（激光）</b>：被附体空壳的一种远程攻击 —— <b>判定在服务端一次性算完，视觉只是一条线</b>。
 *
 * <h2>为什么不用实体（用户指定）</h2>
 * "可以参考原版守卫者的代码（也可以参考音符盒权杖的音符攻击，那种攻击就没涉及到任何实体），
 * 直接渲染一条激光，接触到激光的就触发判定。"
 * <p>
 * 上一版是一条会飞的实体（{@code OrbGuardianBeam}），用户要求改成本实现，于是判定就是开火那一刻完成的，
 * 世界上不会多出任何实体；视觉则把"起点 / 终点 / 颜色 / 亮多久"用 {@link GuardianLaserPayload}
 * 发给附近玩家，客户端 {@code GuardianLaserClient} 照着画一条守卫者激光（{@link #VISUAL_TICKS} 刻后淡出）。
 * 终点是服务端算好的，客户端不做任何判定。
 *
 * <h2>它一路上会碰到什么（用户口径）</h2>
 * <ul>
 *   <li><b>方块</b>：<b>无法穿透</b> —— 撞上就停在那里，方块本身毫发无损；</li>
 *   <li><b>掉落物（"物体"）</b>：<b>摧毁并穿透</b> —— 碰到就抹掉，激光继续往前（这才是需求里
 *       "如果命中物体则可摧毁该物体并穿透"的本来意思，不是砸方块）；</li>
 *   <li><b>生物</b>：吃 {@link #DAMAGE} 点魔法伤害，并<b>失去穿透能力</b> —— 激光到此为止。</li>
 * </ul>
 * 顺序上先问方块定下"这一发最远到哪"，再在这段里找第一个生物（它会截住激光），
 * 最后清掉这一段里的掉落物 —— 所以躲在墙后、或者站在被打中那具身体后面的人和东西都不会被波及。
 * <p>
 * 这条激光打出的伤害源是 {@code indirectMagic(空壳, 空壳)}（与原版守卫者同一条）——
 * 直接来源与归属来源都是那具空壳，所以"这具空壳在打谁"的既有逻辑（同化、附体命中日志）照常认得出来。
 * 它不是 {@code Projectile}，因此不会触发"弹射物缴械 / 打落飞行"那两条规则（激光不是弹射物）。
 */
public final class OrbGuardianLaser {

    /** 命中伤害（点）：用户指定 8 点魔法伤害。 */
    public static final float DAMAGE = 8.0F;

    /** 最大射程（格）：飞满就没了。 */
    public static final double MAX_RANGE = 32.0D;

    /** 客户端把这条激光画多少刻（之后自动淡出、自动从表里清掉）。 */
    public static final int VISUAL_TICKS = 12;

    /** 射线采样步长（格）：越小越不容易从两个采样点之间"漏过"一个细小目标。 */
    private static final double STEP = 0.5D;

    /** 判定半径（格）：射线到目标碰撞箱的距离在这个范围内就算"接触到激光"。 */
    private static final double HIT_RADIUS = 0.5D;

    /** 清掉落物从离枪口多远开始（不要把空壳自己脚边刚丢出去的东西一起抹了）。 */
    private static final double ITEM_SCAN_MIN_DISTANCE = 1.0D;

    /** 把激光发给多大范围内的玩家（格）：要覆盖整条 32 格的线。 */
    private static final double BROADCAST_RADIUS = 48.0D;

    private OrbGuardianLaser() {
    }

    /**
     * 打出一发激光：定射程 → 找生物 → 清掉落物 → 结算伤害 → 通知客户端画线。
     *
     * @param level     服务端世界
     * @param shooter   发射者（那具空壳）
     * @param direction 朝向（会归一化）
     */
    public static void fire(ServerLevel level, LivingEntity shooter, Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-8D) {
            return;
        }
        Vec3 dir = direction.normalize();
        Vec3 start = shooter.getEyePosition();

        // ① 先问方块：<b>激光无法穿透方块</b>（用户指定）—— 撞上就停在那儿，方块本身毫发无损。
        //    这一步同时定下"这一发最远能到哪"，后面找生物、清掉落物都只在城这一段里做。
        Vec3 rangeEnd = start.add(dir.scale(MAX_RANGE));
        net.minecraft.world.phys.BlockHitResult blockHit = level.clip(new net.minecraft.world.level.ClipContext(
            start, rangeEnd, net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE, shooter));
        boolean blocked = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
        double reach = blocked ? start.distanceTo(blockHit.getLocation()) : MAX_RANGE;
        Vec3 end = blocked ? blockHit.getLocation() : rangeEnd;

        // ② 这一段里"第一个接触到激光的生物"：吃 8 点魔法伤害，并<b>失去穿透能力</b>（激光到此为止）
        LivingEntity victim = null;
        double stop = reach;
        for (double d = 0.0D; d <= reach; d += STEP) {
            List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class,
                probeAt(start.add(dir.scale(d))), candidate -> canHit(shooter, candidate));
            if (!found.isEmpty()) {
                victim = found.get(0);
                stop = d;
                break;
            }
        }

        // ③ <b>掉落物（物体）：碰到就摧毁，并继续穿透</b>（用户指定 —— 这是"物体"两个字的本来意思，
        //    不是方块）。被打中的生物会把激光截住，所以只清到 stop 为止。
        for (double d = ITEM_SCAN_MIN_DISTANCE; d <= stop; d += STEP) {
            for (net.minecraft.world.entity.item.ItemEntity drop
                : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    probeAt(start.add(dir.scale(d))))) {
                drop.discard();
            }
        }

        // ④ 命中判定：8 点魔法伤害（与原版守卫者同一条伤害源，归因给这具空壳）
        if (victim != null) {
            victim.hurt(level.damageSources().indirectMagic(shooter, shooter), DAMAGE);
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.GUARDIAN_HURT, SoundSource.HOSTILE, 0.7F, 1.4F);
            // 视觉终点落在被打中的那具身体中段，看起来就是"激光打在他身上"
            end = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
        }

        // ⑤ 通知附近玩家画一条线（颜色每条随机）
        int color = Mth.hsvToRgb(shooter.getRandom().nextFloat(), 0.85F, 1.0F);
        Vec3 mid = start.add(end).scale(0.5D);
        PacketDistributor.sendToPlayersNear(level, null, mid.x, mid.y, mid.z, BROADCAST_RADIUS,
            new GuardianLaserPayload(start.x, start.y, start.z, end.x, end.y, end.z, color,
                VISUAL_TICKS));
    }

    /** 射线采样点周围的一个小方盒：用来问"这一刻激光碰到了谁"。 */
    private static AABB probeAt(Vec3 point) {
        return new AABB(point.x - HIT_RADIUS, point.y - HIT_RADIUS, point.z - HIT_RADIUS,
            point.x + HIT_RADIUS, point.y + HIT_RADIUS, point.z + HIT_RADIUS);
    }

    /**
     * 这条激光能不能打这个生物。
     * <p>
     * 排除：发射者本人、它身上的乘客（附体时那颗核心骑在空壳头上）、
     * 以及整个"我方一侧"（见 {@link OrbAllyWitherControl#isAllySide} —— 核心 / 其它被附体的空壳 /
     * 附体召唤物）。不排除的话激光一出生就打在自己脸上。
     */
    private static boolean canHit(LivingEntity shooter, LivingEntity candidate) {
        if (!candidate.isAlive() || candidate.isSpectator() || candidate.isInvulnerable()) {
            return false;
        }
        if (shooter.getUUID().equals(candidate.getUUID())) {
            return false;
        }
        if (shooter.hasPassenger(candidate)) {
            return false;
        }
        return !OrbAllyWitherControl.isAllySide(candidate);
    }
}
