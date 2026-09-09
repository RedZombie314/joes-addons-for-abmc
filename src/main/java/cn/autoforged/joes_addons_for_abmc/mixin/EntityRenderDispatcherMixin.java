package cn.autoforged.joes_addons_for_abmc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家变形（渲染替换：生物/方块/物品）时，整体重画玩家为对应模型，本体保持"可见"。
 * 而原版 {@code EntityRenderDispatcher} 画 F3+B 碰撞箱线框的条件包含"实体不可隐身"才会跳过——
 * 一旦本体可见，第一人称下就会看到自己的 1×1(变形方块)碰撞箱边框挡在眼前。
 * 这里在 {@code renderHitbox} 处直接跳过本地"正在变形"的玩家，从而第一人称看不到碰撞箱边框，
 * 第三人称也不再显示干扰线框。方块/物品形态采用该玩家本体直渲，无跟随壳，仅判断本地玩家即可。
 *
 * 另外，变形生物发射的弹射物在第一人称下距离自己3格以内时不渲染，避免遮挡视线。
 */
@Mixin(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private static void jafa_skipTransmutedPlayerHitbox(
            PoseStack poseStack, VertexConsumer vertexConsumer, Entity entity,
            float partialTick, float red, float green, float blue, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player
            && cn.autoforged.joes_addons_for_abmc.client.TransmutationCameraClient.isTransmuted()) {
            ci.cancel();
        }
    }

    /** 变形生物发射的弹射物在第一人称3格内不渲染，避免遮挡视线。 */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public <E extends Entity> void jafa_skipNearbyMorphProjectile(
            E entity, double x, double y, double z, float yRot, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;
        if (!cn.autoforged.joes_addons_for_abmc.client.TransmutationCameraClient.isMorphActive()) return;
        if (!(entity instanceof Projectile projectile)) return;
        if (projectile.getOwner() != mc.player) return;
        if (entity.distanceToSqr(mc.player) < 9.0) { // 3格以内
            ci.cancel();
        }
    }
}