package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 幸运实体子事件：<b>骷髅骑士</b> —— 一只骑着<b>骷髅马</b>的<b>骷髅</b>。
 *
 * <h3>骷髅马：像蝙蝠一样到处乱飞、不落地、免疫摔落伤害</h3>
 * <ul>
 *   <li>出生时踢掉它<b>全部</b>地面 AI（{@code goalSelector/targetSelector.removeAllGoals}）并打开
 *       {@code setNoGravity(true)}，所以马的移动<b>完全由本事件的服务器 tick 驱动</b>，不会被原版的
 *       闲逛/游泳/受惊 AI 抢走控制权（也要注意不能改成 {@code setNoAi(true)}：那样连
 *       {@code LivingEntity#travel} 的物理推进都会被跳过，马就动不了了）；</li>
 *   <li>飞行方式仿蝙蝠：在出生点周围随机挑一个目标点，每刻把当前速度朝"指向目标、大小 = 巡航速度"
 *       的方向<b>慢慢靠拢</b>（{@code STEER}），到点或超时就换下一个目标，于是忽左忽右、上上下下；</li>
 *   <li><b>不落地</b>：除了无重力，每刻还检查一次地形高度，一旦低于"地表 + {@value #MIN_ABOVE_GROUND} 格"
 *       就额外往上抬，所以既不会落地也不会卡进地里；</li>
 *   <li><b>免疫摔落</b>：每刻把 {@code fallDistance} 清零，并且 {@code ModMain} 会取消它和骷髅骑士的
 *       {@code LivingFallEvent}（摔落伤害的根事件）——双保险。</li>
 * </ul>
 *
 * <h3>骷髅：自带抗火、免疫摔落伤害</h3>
 * <ul>
 *   <li>永久<b>抗火</b>效果（时长 {@code -1}，无粒子），另外每刻 {@code clearFire()}，
 *       所以白天既不会被晒伤也不会显示着火；</li>
 *   <li>手上没武器时补一把<b>弓</b>，保证它会还击；</li>
 *   <li>和坐骑一样免疫摔落伤害。</li>
 * </ul>
 *
 * <p>两者都设为不被自然消失；骑手每刻跟着坐骑转向，避免"横着骑马"。
 *
 * <p><b>生成位置</b>：骷髅马就生成在<b>幸运方块原本的坐标</b>上（破坏后该位置已经是空气），
 * 不做"往上找空位"的抬高；因为它无重力，出生后靠飞行逻辑里"低于地表就往上抬"自己爬升出来，
 * 漫游中心点也正好是这颗幸运方块的位置，所以它就在方块附近乱飞。
 *
 * <p>登记：本事件在 {@link LuckyEvents#registerAll()} 里注册；它<b>不在</b> debug 池里
 * （6.5.5 时曾是 latest，6.5.6 起 latest 换成"debug 池"、由 {@link LuckyEvents#markDebug} 显式登记，
 * 池里的 8 个事件见那里）。
 */
public final class LuckySkeletonKnightEvent {
    /** 目标点相对出生点的水平漫游半径（格）。 */
    private static final double ROAM_RADIUS = 16.0D;
    /** 目标点相对出生点的垂直漫游高度（格，上下各这么多，低处会被地表夹住）。 */
    private static final double ROAM_HEIGHT = 9.0D;
    /** 飞行时至少高出地表这么多格（保证"不落地"）。 */
    private static final double MIN_ABOVE_GROUND = 3.0D;
    /** 巡航速度（格/刻；约 6~7 格/秒）。 */
    private static final double CRUISE_SPEED = 0.34D;
    /** 每刻把速度朝巡航速度靠拢的比例（越小越飘、越大越像直线冲刺）。 */
    private static final double STEER = 0.25D;
    /** 换一个目标点的间隔（刻）：1~3 秒，所以飞行路线是乱窜的。 */
    private static final int RETARGET_MIN = 20;
    private static final int RETARGET_MAX = 60;
    /** 与目标点的距离小于这个值就算"到了"。 */
    private static final double ARRIVE_DISTANCE = 2.0D;
    /** 贴地时额外向上的加速度。 */
    private static final double CLIMB_BOOST = 0.16D;

    /** 已生成、仍在追随的骷髅骑士。 */
    private static final List<Knight> KNIGHTS = new ArrayList<>();

    private LuckySkeletonKnightEvent() {
    }

    /** 一只骷髅骑士：坐骑、骑手、漫游中心点与当前目标点。 */
    private static final class Knight {
        private final ServerLevel level;
        private final SkeletonHorse horse;
        private final Skeleton skeleton;
        /** 漫游中心（出生点），目标点始终围绕它随机取，防止马飞走导致区块没加载。 */
        private final Vec3 anchor;
        private Vec3 target;
        private int retarget;

        private Knight(ServerLevel level, SkeletonHorse horse, Skeleton skeleton, Vec3 anchor) {
            this.level = level;
            this.horse = horse;
            this.skeleton = skeleton;
            this.anchor = anchor;
            this.target = anchor;
        }
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("entity/skeleton_knight"), LuckyEventCategory.LUCKY_ENTITY,
            "生成一只骑着骷髅马的骷髅（骷髅马像蝙蝠一样乱飞且不落地，两者免疫摔落伤害，骷髅自带抗火）",
            LuckySkeletonKnightEvent::spawnKnight);
        LuckyEvents.register(event);
        return event;
    }

    private static void spawnKnight(ServerLevel level, BlockPos pos, @Nullable Player player) {
        // 注意：本事件（玩家用幸运方块开出的那个骷髅骑士）<b>故意不给任何护甲</b>。
        // 那套"40% 无附魔铁甲 / 10% 弹射物保护 IV 铁甲 / 马 30% 铁凯 30% 金凯"的配置是
        // 附体核心远程攻击专属的（OrbPossessedAttackEvents.SkeletonKnightEvent），
        // 两者是各自独立的生成代码，别把装备逻辑往上挪。
        SkeletonHorse horse = EntityType.SKELETON_HORSE.create(level);
        if (horse == null) return;

        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        // 骷髅马就生成在幸运方块原本的坐标上（破坏后这个位置已经是空气），不额外抬高：
        // 它是无重力的，出生后靠 fly() 里的"离地太近就往上抬"自己爬升出来。
        double y = pos.getY();

        horse.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
        horse.setTamed(true);              // 已驯服：不会把背上的骷髅甩下去
        horse.setPersistenceRequired();
        horse.setNoGravity(true);          // 不落地
        // 踢掉全部地面 AI：飞行完全交给本事件的 tick（不能用 setNoAi，那样连移动物理都会停掉）
        horse.goalSelector.removeAllGoals(goal -> true);
        horse.targetSelector.removeAllGoals(goal -> true);
        level.addFreshEntity(horse);

        Skeleton skeleton = EntityType.SKELETON.create(level);
        if (skeleton == null) {
            horse.discard();
            return;
        }
        skeleton.moveTo(x, y, z, horse.getYRot(), 0.0F);
        skeleton.setPersistenceRequired();
        skeleton.fallDistance = 0.0F;
        // 自带抗火：永久抗火（-1 = 无限时长，无粒子）
        skeleton.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        if (skeleton.getMainHandItem().isEmpty()) {
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW)); // 保证它手里有武器
        }
        level.addFreshEntity(skeleton);
        skeleton.startRiding(horse, true);

        KNIGHTS.add(new Knight(level, horse, skeleton, horse.position()));
    }

    /** 由服务端 tick 钩子（{@code ModMain.onServerTickPre}）每刻调用：驱动骷髅马乱飞。 */
    public static void tick() {
        if (KNIGHTS.isEmpty()) return;
        Iterator<Knight> iterator = KNIGHTS.iterator();
        while (iterator.hasNext()) {
            Knight knight = iterator.next();
            boolean horseAlive = knight.horse.isAlive() && !knight.horse.isRemoved();
            boolean riderAlive = knight.skeleton.isAlive() && !knight.skeleton.isRemoved();
            if (!horseAlive && !riderAlive) {
                iterator.remove(); // 两个都没了才停止追踪
                continue;
            }
            if (horseAlive) {
                fly(knight);
            } else {
                // 坐骑被打死了：骷髅会掉下来，这里继续帮它清零坠落距离（摔落免疫不断）
                knight.skeleton.fallDistance = 0.0F;
            }
        }
    }

    /** 该实体是不是"本事件刷出来的骷髅骑士或它的骷髅马"（用于免疫摔落伤害）。 */
    public static boolean isFallImmune(Entity entity) {
        for (Knight knight : KNIGHTS) {
            if (knight.horse == entity || knight.skeleton == entity) return true;
        }
        return false;
    }

    /** 蝙蝠式飞行：每刻朝"指向当前目标点、大小为巡航速度"的速度靠拢一点。 */
    private static void fly(Knight knight) {
        SkeletonHorse horse = knight.horse;
        horse.setNoGravity(true);   // 保险：任何情况下都不受重力
        horse.fallDistance = 0.0F;  // 免疫摔落伤害（配合 LivingFallEvent 取消）

        Skeleton skeleton = knight.skeleton;
        boolean riderAlive = skeleton.isAlive() && !skeleton.isRemoved();
        if (riderAlive) {
            skeleton.fallDistance = 0.0F;
            skeleton.clearFire();   // 抗火效果之外再顺手灭火，白天不会显示成着火
        }

        // 到点 / 超时 -> 换一个新的目标点
        if (--knight.retarget <= 0
            || horse.position().distanceToSqr(knight.target) < ARRIVE_DISTANCE * ARRIVE_DISTANCE) {
            knight.target = pickTarget(knight);
            knight.retarget = RETARGET_MIN + knight.level.random.nextInt(RETARGET_MAX - RETARGET_MIN + 1);
        }

        Vec3 toTarget = knight.target.subtract(horse.position());
        Vec3 wanted = toTarget.lengthSqr() < 1.0E-6D
            ? Vec3.ZERO
            : toTarget.normalize().scale(CRUISE_SPEED);

        // 离地太近就往上抬，保证"不会落地"
        int groundY = knight.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Mth.floor(horse.getX()), Mth.floor(horse.getZ()));
        if (horse.getY() < groundY + MIN_ABOVE_GROUND) {
            wanted = wanted.add(0.0D, CLIMB_BOOST, 0.0D);
        }

        Vec3 current = horse.getDeltaMovement();
        Vec3 next = current.add(wanted.subtract(current).scale(STEER));
        horse.setDeltaMovement(next);

        faceTowards(horse, next);
        if (riderAlive) {
            // 骑手跟着坐骑转，不然会"横着骑马"
            skeleton.setYRot(horse.getYRot());
            skeleton.setYHeadRot(horse.getYHeadRot());
            skeleton.setYBodyRot(horse.getYRot());
        }
    }

    /** 在漫游中心附近随机挑一个目标点（水平 ±{@value #ROAM_RADIUS} 格，上下 ±{@value #ROAM_HEIGHT} 格，
     *  低处会被"地表 + {@value #MIN_ABOVE_GROUND} 格"夹住，所以会贴着地面掠过去但不会落地）。 */
    private static Vec3 pickTarget(Knight knight) {
        RandomSource random = knight.level.random;
        double x = knight.anchor.x + (random.nextDouble() * 2.0D - 1.0D) * ROAM_RADIUS;
        double z = knight.anchor.z + (random.nextDouble() * 2.0D - 1.0D) * ROAM_RADIUS;
        int groundY = knight.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Mth.floor(x), Mth.floor(z));
        double y = Math.max(knight.anchor.y + (random.nextDouble() * 2.0D - 1.0D) * ROAM_HEIGHT,
            groundY + MIN_ABOVE_GROUND + 1.0D);
        return new Vec3(x, y, z);
    }

    /** 让坐骑朝着飞行方向（水平方向决定偏航角，垂直方向给一点点俯仰）。 */
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
}
