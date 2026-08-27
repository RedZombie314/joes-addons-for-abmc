package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.LightLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 滴水石权杖“群组”单块下落方块的自定义渲染器。
 *
 * 解决钻出动画“一闪一闪”的问题：
 * 原版 FallingBlockRenderer 用实体的中心点({@code getLightProbePosition})采样光照，
 * 而群组在“从地里钻出”的过程中，中心点仍在地面以下，导致已经露出的部分被渲染成暗色，
 * 待中心点越过地面后突然变亮，造成明显的明暗闪烁。
 *
 * 这里改为用实体的顶部({@code getBoundingBox().maxY})采样光照：钻出时顶部已在地面以上，
 * 露出的部分始终正常受光，钻出过程平滑不闪烁，同时保持原生的表面剔除效果
 * （仍只显示露出地面的部分，仿佛是“长出来”的）。
 */
@OnlyIn(Dist.CLIENT)
public class DripstoneFallingBlockRenderer extends FallingBlockRenderer {

    public DripstoneFallingBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getSkyLightLevel(FallingBlockEntity entity, BlockPos pos) {
        return entity.level().getBrightness(LightLayer.SKY, topLightPos(entity));
    }

    @Override
    protected int getBlockLightLevel(FallingBlockEntity entity, BlockPos pos) {
        return entity.isOnFire() ? 15 : entity.level().getBrightness(LightLayer.BLOCK, topLightPos(entity));
    }

    /** 用实体顶部作为光照采样点，避免中心点埋在地面之下导致露出的部分变暗闪烁 */
    private static BlockPos topLightPos(FallingBlockEntity entity) {
        return BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
    }
}