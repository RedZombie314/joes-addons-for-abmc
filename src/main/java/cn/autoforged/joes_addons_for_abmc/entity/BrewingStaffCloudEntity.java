package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 酿造台权杖·药水云模式发射的状态效果云。
 * <p>从权杖端点沿玩家视线匀减速直线飞行（不受重力），初速为玩家丢药水的 3 倍（0.5×3=1.5），
 * 加速度为初速的 1/64（约 64 刻、48 格后速度减为 0）；飞行期间碰撞箱（半径）逐渐变大，
 * 撞到方块或速度减为 0 后完全停止运动，随后像正常药水云一样逐渐缩小直至消失。
 * 半径由本类直接控制，并把原版 AreaEffectCloud 的 radiusPerTick 置 0 使其半径逻辑变为空操作。</p>
 */
public class BrewingStaffCloudEntity extends net.minecraft.world.entity.AreaEffectCloud {

    /** 发射初期的极小半径。 */
    private static final float INITIAL_RADIUS = 0.2F;
    /** 飞行结束时达到的最大半径。 */
    private static final float MAX_RADIUS = 3.0F;
    /** 飞行期间半径每刻增长量。 */
    private static final float RADIUS_GROW_PER_TICK = 0.25F;
    /** 停止后半径每刻缩小量。 */
    private static final float RADIUS_SHRINK_PER_TICK = 0.02F;
    /** 缩小到该半径以下时消失。 */
    private static final double DISCARD_RADIUS = 0.15D;
    /** 兜底总寿命（刻），远大于实际存活时长，实际消失由半径决定。 */
    private static final int MAX_LIFETIME = 72000;

    /** 飞行方向（单位向量）。 */
    private Vec3 flightDir = Vec3.ZERO;
    /** 当前飞行速度（格/刻）。 */
    private double flightSpeed = 0.0D;
    /** 匀减速加速度（格/刻²）。 */
    private double flightDecel = 0.0D;
    /** 是否仍处于飞行（增长）阶段；false 后进入缩小阶段。 */
    private boolean flying = true;

    public BrewingStaffCloudEntity(EntityType<? extends BrewingStaffCloudEntity> type, Level level) {
        super(type, level);
    }

    /** 由服务端在发射时初始化：设置飞行方向、初速与减速，并配置半径增长参数。 */
    public void initFlight(Vec3 dir, double initialSpeed) {
        this.flightDir = dir.lengthSqr() > 1.0E-8 ? dir.normalize() : new Vec3(0.0, 0.0, 1.0);
        this.flightSpeed = initialSpeed;
        this.flightDecel = initialSpeed / 64.0D; // 匀减速加速度：初速的 1/64（飞约 64 刻、48 格后停下）
        this.flying = true;
        this.setRadius(INITIAL_RADIUS);
        // 原版半径逻辑置空（radiusPerTick=0）：半径完全由本类每刻控制。
        // radiusOnUse 必须为 0：1.21.1 中 radiusOnUse 非 0 时，云在“每次命中生物”都会 radius += radiusOnUse，
        // 会因多次命中而不断膨胀（可一路涨到 32），导致云变得特别大。
        this.setRadiusOnUse(0.0F);
        this.setRadiusPerTick(0.0F);
        this.setWaitTime(0);
        this.setDuration(MAX_LIFETIME);
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide()) {
            this.tickServer();
        }
        super.tick();
    }

    /** 服务端每刻：飞行（撞到方块即完全停止并开始缩小）+ 匀减速 + 半径增长/缩小 + 消失判定。 */
    private void tickServer() {
        if (this.flying) {
            Vec3 from = this.position();
            Vec3 to = from.add(this.flightDir.scale(this.flightSpeed));
            // 撞到方块：完全停止运动并开始缩小
            net.minecraft.world.phys.BlockHitResult hit = this.level().clip(
                new net.minecraft.world.level.ClipContext(from, to,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, this));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                this.setPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
                this.flightSpeed = 0.0D;
                this.flying = false;
                return;
            }
            this.setPos(to.x, to.y, to.z);
            this.setRadius(Math.min(this.getRadius() + RADIUS_GROW_PER_TICK, MAX_RADIUS));
            this.flightSpeed -= this.flightDecel;
            if (this.flightSpeed <= 0.0D) {
                this.flightSpeed = 0.0D;
                this.flying = false; // 速度减为 0：停止变大，进入缩小阶段
            }
        } else {
            this.setRadius(this.getRadius() - RADIUS_SHRINK_PER_TICK);
            if (this.getRadius() <= DISCARD_RADIUS) {
                this.discard();
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("JafaFlightDirX", this.flightDir.x);
        tag.putDouble("JafaFlightDirY", this.flightDir.y);
        tag.putDouble("JafaFlightDirZ", this.flightDir.z);
        tag.putDouble("JafaFlightSpeed", this.flightSpeed);
        tag.putDouble("JafaFlightDecel", this.flightDecel);
        tag.putBoolean("JafaFlying", this.flying);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.flightDir = new Vec3(
            tag.getDouble("JafaFlightDirX"),
            tag.getDouble("JafaFlightDirY"),
            tag.getDouble("JafaFlightDirZ"));
        this.flightSpeed = tag.getDouble("JafaFlightSpeed");
        this.flightDecel = tag.getDouble("JafaFlightDecel");
        this.flying = tag.getBoolean("JafaFlying");
    }
}
