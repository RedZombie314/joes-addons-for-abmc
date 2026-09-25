package cn.autoforged.joes_addons_for_abmc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 计数器耗尽（{@code jafa_counter <= 0}）时，不渲染附魔台上方悬浮的书。
 * <p>只在键存在且 <=0 时才抑制：普通（未初始化/原版）附魔台没有该键，书照常渲染。
 * 计数由服务端写入 BE 的持久化数据并经 {@link EnchantingTableBlockEntityMixin} 同步到客户端。
 */
@Mixin(EnchantTableRenderer.class)
public abstract class EnchantingTableRendererMixin
        implements BlockEntityRenderer<EnchantingTableBlockEntity> {

    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/EnchantingTableBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At("HEAD"), cancellable = true)
    private void jafa_hideBookWhenExhausted(EnchantingTableBlockEntity blockEntity, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay,
            CallbackInfo ci) {
        // 优先查客户端计数缓存（由自定义 payload 同步，最可靠）；缺失时回退读 BE 持久化数据
        int counter = -1;
        net.minecraft.world.level.Level lv = blockEntity.getLevel();
        if (lv != null) {
            counter = cn.autoforged.joes_addons_for_abmc.client.EnchantTableCounterClient.get(lv, blockEntity.getBlockPos());
        }
        if (counter == -1) {
            var data = blockEntity.getPersistentData();
            if (data.contains("jafa_counter")) {
                counter = data.getInt("jafa_counter");
            }
        }
        if (counter <= 0) {
            ci.cancel();
        }
    }
}