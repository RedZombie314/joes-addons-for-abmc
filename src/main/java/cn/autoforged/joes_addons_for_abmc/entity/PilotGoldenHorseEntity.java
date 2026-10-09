package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * <b>会飞的金马铠马</b>（需求 3'）。只借"操控方式"，位移全部交给<b>原版骑乘</b>那一套。
 *
 * <h2>竖直方向（6.5.30 改成现在这样）</h2>
 * <ul>
 *   <li><b>空格按住 = 持续上升</b>（需求）。"按住"是从原版骑乘跳跃的两个钩子读的：
 *       服务端 {@code START_RIDING_JUMP → AbstractHorse#onPlayerJump} 置位、
 *       {@code STOP_RIDING_JUMP → AbstractHorse#handleStopJump} 清位（客户端每刻都在发 START，
 *       见 {@code LocalPlayer#sendRidingJump}）；客户端则直接读本地空格键
 *       （{@code PilotHorseClientInput}）——骑乘实体是客户端预测的，两边必须都抬升才不会互相纠正。
 *       <b>原版的跳跃被取消了</b>：这两个钩子里只记状态、不再把 {@code playerJumpPendingScale}
 *       换成跳跃冲量。</li>
 *   <li><b>不按键 = 悬停</b>（{@code noGravity} + 竖直速度归零）。</li>
 *   <li><b>W / S 时才跟着视线上下</b>：这就是"操控方式同选择器"里的"沿视线前后"。
 *       <b>6.5.29 的 bug 就出在这里</b>——上一版不管有没有按前后键，竖直速度都被视线俯仰驱动，
 *       于是玩家只是扭头看路（视线略微朝下）时，马会一直被按向地面，
 *       贴地摩擦 + 碰撞把水平速度吃掉，表现就是"扭头时大幅减速"。</li>
 * </ul>
 *
 * <h2>水平方向</h2>
 * 完全原版：{@code AbstractHorse#travel} / {@code getRiddenInput}（跟随视线转向、A/D 横移、
 * 客户端自己预测）。我们只补"原版没有的那一维"——重力与爬升。
 */
public class PilotGoldenHorseEntity extends Horse {

    /** <b>飞行速度（格/秒）</b>：要调快调慢就改这一个数。30 格/秒 = 1.5 格/刻。 */
    public static final double FLY_SPEED_PER_SECOND = 30.0D;

    /** 写进 {@code MOVEMENT_SPEED} 属性的值（原版口径就是"格/刻"）。 */
    public static final double FLY_SPEED_PER_TICK = FLY_SPEED_PER_SECOND / 20.0D;

    /** 前后键死区：{@code zza} 超过它才算"在按前后"。 */
    private static final float INPUT_DEADZONE = 0.1F;

    /** 每刻把水平速度转向机体朝向的比例（0.35 ≈ 3~4 刻完全跟上）。转头不掉速就靠它。 */
    private static final double TURN_ALIGN_PER_TICK = 0.35D;

    /** 服务端记的"空格按着没有"（由原版骑乘跳跃钩子维护）。 */
    private boolean ascendRequested;

    public PilotGoldenHorseEntity(EntityType<? extends Horse> type, Level level) {
        super(type, level);
    }

    /** 属性沿用原版马那一套（血量 53 / 基础移速 0.225 / 跳跃 0.7）；飞行速度由生成方写进 MOVEMENT_SPEED。 */
    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.Mob.createMobAttributes()
            .add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 53.0D)
            .add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.22499999403953552D)
            .add(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH, 0.7D);
    }

    /** 骑乘速度：直接按需求给的值（原版就是读这个来决定"骑手推一下能多快"）。 */
    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) FLY_SPEED_PER_TICK;
    }

    /**
     * <b>免疫摔落伤害</b>（需求）。
     * <p>
     * 直接掐掉伤害入口（与 {@code OrbOfLuckEntity} 同一写法），而不是把属性里的摔落倍率设成 0：
     * 后者只是把伤害算成 0，落地音效/粒子/摔落距离结算还会走一遍。
     * 返回 false = 这次落地不产生伤害（骑马飞行时骑手的摔落距离我们在 tick 里也清掉了，
     * 两头都不吃摔落伤害）。
     */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    /**
     * 原版骑乘跳跃的"开始"钩子 —— <b>这才是服务端真正会调的那个</b>。
     * <p>
     * 6.5.33 修的 bug：上一版只覆写了 {@link #onPlayerJump(int)}，但服务端处理
     * {@code START_RIDING_JUMP} 走的是 {@code PlayerRideableJumping#handleStartJump}，
     * 而 {@code AbstractHorse#handleStartJump}（AbstractHorse.java:984）里<b>只做三件事</b>：
     * {@code allowStandSliding = true; standIfPossible(); playJumpSound();}——<b>根本没调 onPlayerJump</b>。
     * 于是：① 服务端永远不知道"空格按着"，上升状态一直是假；
     * ② 原版那套"马立起来 + 跳跃音效"照跑，飞行中被塞进 standing 状态，
     * 会命中 {@code getRiddenInput} 里那条"站着不动"分支（AbstractHorse.java:810），骑乘手感全乱。
     * 现在这里<b>完全接管</b>：只记状态，不调 {@code super}（不要立起来、不要音效、不要原版跳跃）。
     */
    @Override
    public void handleStartJump(int jumpPower) {
        if (this.getControllingPassenger() instanceof Player) {
            this.ascendRequested = true;
            this.playerJumpPendingScale = 0.0F;   // 原版跳跃冲量不要（见 executeRidersJump）
            return;
        }
        super.handleStartJump(jumpPower);
    }

    /**
     * 原版骑乘跳跃的"开始"钩子的另一条入口（客户端本地预测时会走它）。
     * <p>同样只记状态、不做原版跳跃。
     */
    @Override
    public void onPlayerJump(int jumpPower) {
        if (this.getControllingPassenger() instanceof Player) {
            this.ascendRequested = true;
            this.playerJumpPendingScale = 0.0F;   // 取消原版跳跃
            return;
        }
        super.onPlayerJump(jumpPower);
    }

    /**
     * 原版骑乘跳跃的"松开"钩子（服务端由 {@code STOP_RIDING_JUMP} 包触发）：
     * 这里清掉"空格按着"，同时确保原版跳跃力度归零。
     */
    @Override
    public void handleStopJump() {
        this.ascendRequested = false;
        this.playerJumpPendingScale = 0.0F;
        // 不调 super：原版这个方法在 AbstractHorse 里是空实现，但留个明确意图在这里，
        // 免得以后原版加了东西又被漏掉。
    }

    @Override
    public void tick() {
        super.tick();
        Player rider = this.getControllingPassenger() instanceof Player player ? player : null;
        if (rider == null) {
            // 没人骑：交回原版（落地、吃草、被拴着走都照旧）
            if (this.isNoGravity()) {
                this.setNoGravity(false);
            }
            this.ascendRequested = false;
            return;
        }
        // 骑乘飞行：不受重力；水平位移/碰撞/转向都是原版骑乘那一套，这里一个字都不碰
        this.setNoGravity(true);
        this.fallDistance = 0.0F;
        rider.fallDistance = 0.0F;   // 骑着飞行时摔落不该记在骑手头上

        float yaw = Mth.wrapDegrees(rider.getYRot());
        float pitch = rider.getXRot();
        // 机体跟着视线（外观上"朝哪看就朝哪飞"）
        this.setYRot(yaw);
        this.setYBodyRot(yaw);
        this.setYHeadRot(yaw);
        this.setXRot(pitch);

        boolean ascend = this.level().isClientSide()
            ? cn.autoforged.joes_addons_for_abmc.client.PilotHorseClientInput.jumpHeld()
            : this.ascendRequested;

        Vec3 movement = this.getDeltaMovement();
        double climb = 0.0D;
        float forward = rider.zza;
        if (Math.abs(forward) > INPUT_DEADZONE) {
            // 只有主动按前后键时才跟着视线上下：抬头前进＝爬升，低头前进＝俯冲；后退则相反（沿视线后退）
            climb = -Math.sin(Math.toRadians(pitch)) * FLY_SPEED_PER_TICK * Math.signum(forward);
        }
        if (ascend) {
            climb = FLY_SPEED_PER_TICK;   // 空格按住：持续上升（需求）
        }

        /*
         * 水平方向：把现有速度"贴着机体朝向旋过去"，但<b>保持大小</b>。
         *
         * 为什么必须有这一步（6.5.31 修的"空中扭头大幅减速"）：原版骑乘的转向模型是
         * 「每刻往<b>新</b>朝向补一点推力 + 旧速度按 0.91 的阻力衰减」，于是航向是靠推力一点点"追"上来的。
         * 速度越低越不明显；一旦速度高了（我们这匹马就是），持续扭头时那一大坨旧速度一直被阻力吃掉、
         * 新方向的推力又补不满，表现就是"一边转一边明显掉速"——空中没有地面摩擦，所以这跟贴地无关。
         * 这里改成：速度大小不动，只把它转到当前朝向（每刻走 {@link #TURN_ALIGN_PER_TICK} 的比例，
         * 大约 3~4 刻完全跟上），转头就完全不掉速了。加速度/减速仍然由原版推力与阻力负责。
         */
        double horizontal = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        if (horizontal > 1.0E-4D && Math.abs(forward) > INPUT_DEADZONE) {
            double dirX = -Math.sin(Math.toRadians(yaw));
            double dirZ = Math.cos(Math.toRadians(yaw));
            double newX = movement.x + (dirX * horizontal - movement.x) * TURN_ALIGN_PER_TICK;
            double newZ = movement.z + (dirZ * horizontal - movement.z) * TURN_ALIGN_PER_TICK;
            double length = Math.sqrt(newX * newX + newZ * newZ);
            if (length > 1.0E-6D) {
                // 上面那步是插值，会略微缩短向量，这里按原大小归一回去（"不掉速"就靠这一句）
                newX = newX / length * Math.min(horizontal, FLY_SPEED_PER_TICK);
                newZ = newZ / length * Math.min(horizontal, FLY_SPEED_PER_TICK);
            }
            movement = new Vec3(newX, climb, newZ);
        } else {
            movement = new Vec3(movement.x, climb, movement.z);
        }
        this.setDeltaMovement(movement);
    }

    /** 需求 6.5.27：属性必须在 MOD 总线上登记，否则这只马一生成就崩。 */
    @EventBusSubscriber(modid = ModMain.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class AttributeRegistration {
        @SubscribeEvent
        public static void onAttributes(EntityAttributeCreationEvent event) {
            event.put(ModEntities.PILOT_GOLDEN_HORSE.get(), PilotGoldenHorseEntity.createAttributes().build());
        }
    }
}
