package cn.autoforged.joes_addons_for_abmc.block.renderer;

import cn.autoforged.joes_addons_for_abmc.block.entity.LuckyPortalBlockEntity;
import cn.autoforged.joes_addons_for_abmc.entity.OrbOfLuckRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

/**
 * <b>传送门"屋里"那颗幸运核心同款光球</b>（用户指定）。
 *
 * <h2>位置：模型内部坐标，不是世界坐标</h2>
 * 一开始我把它钉在世界坐标 (8,10,8) 上 —— 那是错的：用户要的是<b>模型内部</b>的 (8,10,8)，
 * 也就是 {@code lucky_portal.json} 自己的 0..16 坐标系里那个点。所以这里改用<b>方块实体渲染器</b>：
 * 它的 {@link PoseStack} 进来时原点就是<b>这个方块的最小角</b>（方块局部坐标，单位是格），
 * 把模型坐标除以 16 就是局部偏移。
 *
 * <p>这个点落在模型的哪儿（对照 json）：整间小屋是 0..16³、地板在 y=0、中间台面 y=6..8、
 * 内圈方环 x/z ∈ [4,12] 且高 14 —— 所以 (8,10,8) 正好是<b>方环正中、台面之上、"中间偏上"</b>，
 * 也就是用户说的那个位置。
 *
 * <h2>为什么这样就不用管维度和坐标</h2>
 * 方块实体渲染器只在这个方块进入视野时被调用，跟传送门被建在主世界还是幸运维度、
 * 建在哪个坐标都无关；传送门结构是 2×2（见 {@code LuckyPortalBlockEntity} 的 portalOffsets），
 * 于是 2×2 的门会得到 2×2 四颗球（每个方块自己那间屋里一颗）——
 * 想改成"整个结构只画一颗"（画在四个方块的公共中心）就得再定一条去重规则，那是另一个需求。
 *
 * <h2>画法</h2>
 * 与核心实体、空壳头顶那颗球<b>共用同一段代码</b>
 * （{@link OrbOfLuckRenderer#drawOrb}）：同几何、同 core shader、同呼吸相位、同半径换算，
 * 唯一区别是这里的"位置"钉在方块模型内部。摄像机朝向从主摄像机取（与
 * {@code ShellHeadOrbLayer} 的做法一致）。
 */
@OnlyIn(Dist.CLIENT)
public class LuckyPortalOrbRenderer implements BlockEntityRenderer<LuckyPortalBlockEntity> {

    /** 光球在模型坐标系里的位置（json 的 0..16 空间），除以 16 变成方块局部偏移（格）。 */
    private static final float ORB_MODEL_X = 8.0F / 16.0F;

    /** 模型空间 y=10：台面（6~8）之上、内环（0~14）之内，用户指定的"中间偏上"。 */
    private static final float ORB_MODEL_Y = 10.0F / 16.0F;

    private static final float ORB_MODEL_Z = 8.0F / 16.0F;

    /** 光球半径（格）：0.5 = 幸运核心实体的碰撞箱半径，所以是"同款大小"。 */
    private static final float ORB_RADIUS = 0.5F;

    public LuckyPortalOrbRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull LuckyPortalBlockEntity blockEntity, float partialTick,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        if (blockEntity.getLevel() == null) {
            return;
        }
        OrbOfLuckRenderer.drawOrb(
            poseStack, bufferSource,
            ORB_MODEL_X, ORB_MODEL_Y, ORB_MODEL_Z, ORB_RADIUS,
            blockEntity.getLevel().getGameTime(), partialTick,
            Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
    }
}
