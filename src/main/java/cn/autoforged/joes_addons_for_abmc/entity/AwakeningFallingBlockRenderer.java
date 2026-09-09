package cn.autoforged.joes_addons_for_abmc.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 觉醒药水下落方块渲染器。普通渲染时始终绕 Y 轴转向实体的水平朝向，
 * 使方块「面对」玩家（服务端每刻把朝向对准目标玩家）；被唤起后的起跳前 1.5 秒（30 刻）内，
 * 额外绕「玩家视线在水平面上的投影」方向（水平轴）快速左右小幅摆动。
 * 若目标是酿造台，还会把其 0..2 槽里的药水瓶子渲染在方块上方，显示"装有药水"的外观。
 */
@OnlyIn(Dist.CLIENT)
public class AwakeningFallingBlockRenderer extends FallingBlockRenderer {

    /** 摆动振幅（度）。 */
    private static final float AMPLITUDE = 4.0F;
    /** 摆动角速度（弧度/刻）。 */
    private static final float SPEED = 1.6F;
    /** 酿造台三瓶药水的圆形排列半径（块）。 */
    private static final float BREW_RADIUS = 0.30F;

    private final ItemRenderer itemRenderer;

    public AwakeningFallingBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(FallingBlockEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity instanceof AwakeningFallingBlockEntity awe) {
            boolean wobbling = false;
            float wobbleX = 0.0F, wobbleZ = 0.0F, wobbleAngle = 0.0F;
            float ax = awe.getWobbleDirX();
            float az = awe.getWobbleDirZ();
            float len = Mth.sqrt(ax * ax + az * az);
            if (len > 1.0E-4F) {
                int elapsed = (int) (entity.level().getGameTime() - awe.getCreationTick());
                if (elapsed >= 0 && elapsed < AwakeningFallingBlockEntity.WOBBLE_DURATION_TICKS) {
                    wobbling = true;
                    float t = elapsed + partialTicks;
                    wobbleX = ax / len;
                    wobbleZ = az / len;
                    wobbleAngle = (float) Math.toRadians(AMPLITUDE * Mth.sin(t * SPEED));
                }
            }

            poseStack.pushPose();
            poseStack.translate(0.0F, 0.5F, 0.0F); // 以方块中心为旋转支点
            // 横向面对玩家：绕 Y 轴转到实体水平朝向（骑乘者沿用载具朝向，避免乘客旋转不同步）
            // 若方块带 HORIZONTAL_FACING（如箱子），按其 facing 相对 SOUTH 的角差补旋转，
            // 使任意朝向的方块都能像 facing=SOUTH 那样正对玩家。
            float yaw = entity.getYRot();
            if (entity.isPassenger() && entity.getVehicle() != null) {
                yaw = entity.getVehicle().getYRot();
            }
            float faceRot = -yaw;
            net.minecraft.world.level.block.state.BlockState bs = entity.getBlockState();
            if (bs.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                faceRot += bs.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).toYRot();
            }
            poseStack.mulPose(Axis.YP.rotationDegrees(faceRot));
            if (wobbling) {
                // 起跳前绕「玩家视线水平投影」方向摆动
                poseStack.mulPose(new Quaternionf().rotationAxis(wobbleAngle, new Vector3f(wobbleX, 0.0F, wobbleZ)));
            }
            poseStack.translate(0.0F, -0.5F, 0.0F);
            // 酿造台骑乘箱子时整体下移 5 像素，贴合箱子顶部
            if (entity.isPassenger()) {
                poseStack.translate(0.0F, -AwakeningFallingBlockEntity.PAIR_RIDER_DOWN_PIXELS / 16.0F, 0.0F);
            }
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            // 酿造台：把槽内药水瓶渲染在方块上方，显示"装有药水"的外观
            renderBrewStandBottles(entity, poseStack, buffer, packedLight);
            poseStack.popPose();
            return;
        }
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /** 把服务端同步过来的酿造台 0..2 槽药水渲染为瓶身（保留药水颜色）。 */
    private void renderBrewStandBottles(FallingBlockEntity entity, PoseStack pose,
                                        MultiBufferSource buffer, int packedLight) {
        if (!(entity instanceof AwakeningFallingBlockEntity awe)) return;
        CompoundTag bottles = awe.getBrewBottles();
        if (bottles == null || bottles.isEmpty()) return;
        net.minecraft.core.HolderLookup.Provider reg = entity.level().registryAccess();
        net.minecraft.nbt.ListTag list = bottles.getList("Bottles", 10);
        // 三瓶按俯视图圆形三等分排布；旋转角、缩放、高度均可由玩家配置调整
        double baseDeg = cn.autoforged.joes_addons_for_abmc.config.ModConfig.AWAKE_BREW_BOTTLES_ROTATION.get();
        double scale = cn.autoforged.joes_addons_for_abmc.config.ModConfig.AWAKE_BREW_BOTTLES_SCALE.get();
        double bottleY = cn.autoforged.joes_addons_for_abmc.config.ModConfig.AWAKE_BREW_BOTTLES_HEIGHT.get();
        float baseRad = (float) Math.toRadians(baseDeg);
        for (int i = 0; i < list.size(); i++) {
            int slot = list.getCompound(i).getInt("Slot");
            if (slot < 0 || slot > 2) continue;
            var item = net.minecraft.world.item.ItemStack.parse(reg, list.getCompound(i));
            if (item.isEmpty()) continue;
            // 该槽相对基础朝向逆时针旋转 120°×slot，按 (x = r·sin a, z = -r·cos a) 计算圆心上的落点
            float ang = baseRad + (float) (Math.PI * 2.0 / 3.0) * slot;
            float x = BREW_RADIUS * Mth.sin(ang);
            float z = -BREW_RADIUS * Mth.cos(ang);
            pose.pushPose();
            pose.translate(x, (float) bottleY, z);
            // renderStatic(FIXED) 默认按整块方块大小渲染，缩放由配置决定（默认 0.6 接近原版酿造台瓶身）
            float sc = (float) scale;
            pose.scale(sc, sc, sc);
            this.itemRenderer.renderStatic(item.get(), ItemDisplayContext.FIXED, packedLight,
                OverlayTexture.NO_OVERLAY, pose, buffer, entity.level(), 0);
            pose.popPose();
        }
    }
}