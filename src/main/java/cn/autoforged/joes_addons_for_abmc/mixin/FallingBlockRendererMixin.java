package cn.autoforged.joes_addons_for_abmc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让“下落方块实体”能显示非 MODEL 类方块（箱子、木桶等 EntityBlock）的外观。
 * <p>根因：原版 {@link FallingBlockRenderer} 只在 {@link RenderShape#MODEL} 时渲染；而箱子/木桶是
 * {@link RenderShape#ENTITYBLOCK_ANIMATED}，外观由其 BlockEntityRenderer 绘制，下落方块实体本身不带
 * BlockEntity，故原版直接跳过 → 隐形。
 * <p>这里对被跳过的非 MODEL 方块回退到“物品模型”渲染：这些方块几乎都有物品形式（箱子、木桶等），
 * 用物品模型即可在落下时看到对应外观，通用且对 Mod 方块同样有效。普通 MODEL 方块仍走原逻辑。
 */
@Mixin(FallingBlockRenderer.class)
public abstract class FallingBlockRendererMixin extends EntityRenderer<FallingBlockEntity> {

    protected FallingBlockRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/item/FallingBlockEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"), cancellable = true)
    private void jafa_renderNonModelFallingBlock(FallingBlockEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        BlockState state = entity.getBlockState();
        // 只有普通方块模型走原逻辑；空气/非 MODEL 交给我们处理
        if (state.getRenderShape() == RenderShape.MODEL || state.isAir()) {
            return;
        }
        Level level = entity.level();
        if (level == null) {
            return;
        }
        // 容器类方块（箱子/木桶等）：用其自身的 BlockEntityRenderer 精确渲染，并严格贴合所在格
        if (state.hasBlockEntity() && state.getBlock() instanceof EntityBlock eb) {
            BlockPos pos = entity.blockPosition();
            BlockEntity be = eb.newBlockEntity(pos, state);
            if (be != null) {
                be.setLevel(level);
                // 配对箱子：强制把盖子揭到打开状态（反射操作内部 ChestLidController，失败则静默保持原样）
                boolean chestOpen = entity instanceof cn.autoforged.joes_addons_for_abmc.entity.AwakeningFallingBlockEntity awe
                    && awe.isChestOpen();
                if (chestOpen && be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity) {
                        try {
                            java.lang.reflect.Field f = net.minecraft.world.level.block.entity.ChestBlockEntity.class
                                .getDeclaredField("chestLidController");
                            f.setAccessible(true);
                            Object lid = f.get(be);
                            if (lid != null) {
                                for (java.lang.reflect.Field lf : lid.getClass().getDeclaredFields()) {
                                    lf.setAccessible(true);
                                    if (lf.getType() == boolean.class) {
                                        lf.setBoolean(lid, true);
                                    } else if (lf.getType() == float.class) {
                                        lf.setFloat(lid, 1.0F);
                                    }
                                }
                                try {
                                    java.lang.reflect.Method open = lid.getClass().getDeclaredMethod("shouldBeOpen", boolean.class);
                                    open.setAccessible(true);
                                    open.invoke(lid, true);
                                } catch (Throwable ignored2) {
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                var dispatcher = net.minecraft.client.Minecraft.getInstance().getBlockEntityRenderDispatcher();
                var renderer = dispatcher.getRenderer(be);
                if (renderer != null) {
                    poseStack.pushPose();
                    // 下落方块实体原点位于格底中心(x+0.5, y, z+0.5)，平移到所在格最小角，
                    // 使渲染出的 1×1 方块严格贴合所在格，不再向东/南/下偏移半格。
                    poseStack.translate(-0.5F, 0.0F, -0.5F);
                    renderer.render(be, partialTick, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
                    poseStack.popPose();
                    ci.cancel();
                    return;
                }
            }
        }
        // 兜底：回退到物品模型（几乎都有物品形式）。普通 MODEL 方块不受影响。
        ItemStack stack = new ItemStack(state.getBlock());
        if (stack.isEmpty()) {
            return; // 无物品形式（纯技术方块）：交给原逻辑
        }
        ItemRenderer itemRenderer = net.minecraft.client.Minecraft.getInstance().getItemRenderer();
        poseStack.pushPose();
        // 对齐到方块位置：中心平移到格点，放大到约一个方块大小（FIXED 物品模型底边在 y=0）
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.scale(2.0F, 2.0F, 2.0F);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight,
            OverlayTexture.NO_OVERLAY, poseStack, bufferSource, level, entity.getRandom().nextInt());
        poseStack.popPose();
        ci.cancel();
    }
}