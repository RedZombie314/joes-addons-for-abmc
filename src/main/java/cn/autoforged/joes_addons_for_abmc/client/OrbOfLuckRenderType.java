package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/**
 * 幸运核心（Orb of Luck）的自定义 core shader 与渲染层。
 *
 * <h2>为什么不用后处理（PostChain）</h2>
 * 1.21.1 的 {@code GameRenderer} 同一时刻只有一个后处理槽位，且处理后紧接着
 * {@code RenderSystem.disableBlend()} 再整屏 blit——预乘 Alpha 根本合不上，还会盖住第一人称手。
 * 核心是"有世界半径、要驱动碰撞箱"的实体物件，必须走公告板 + core shader 这条路。
 *
 * <h2>渲染层状态</h2>
 * <ul>
 *   <li>着色器：自定义 {@code joes_addons_for_abmc:orbofluck}（顶点格式 Position/Color/UV0）；</li>
 *   <li>混合：{@code TRANSLUCENT_TRANSPARENCY}（SRC_ALPHA, ONE_MINUS_SRC_ALPHA），
 *       配合 fsh 输出直通 Alpha，等价于原版的"预乘 Alpha 输出"；</li>
 *   <li>深度：测试保持 LEQUAL（会被方块正确遮挡），但不写深度（COLOR_WRITE），避免挡住后面要画的东西；</li>
 *   <li>剔除：NO_CULL（公告板绕序无所谓）；</li>
 *   <li>光照：不参与，永远全亮。</li>
 * </ul>
 * 想要"穿墙可见的威胁光晕"，把 {@code setDepthTestState} 换成 {@code RenderStateShard.NO_DEPTH_TEST} 即可。
 *
 * <p>注意：{@code @EventBusSubscriber} 不再需要（也不应）指定 {@code bus}——本版 NeoForge 会按事件类型
 * 自动路由，{@link RegisterShadersEvent} 实现 {@code IModBusEvent}，因此自动落到模组总线。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = ModMain.MODID)
public final class OrbOfLuckRenderType {

    /** core shader 路径：assets/joes_addons_for_abmc/shaders/core/orbofluck.{json,vsh,fsh}。 */
    private static final ResourceLocation SHADER_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "orbofluck");

    /** 只含核心着色器真正需要的三个属性。 */
    public static final VertexFormat FORMAT = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("UV0", VertexFormatElement.UV0)
        .build();

    /**
     * 当前已加载的 shader 实例。
     * <p>
     * 必须由 {@link RegisterShadersEvent} 的 onLoaded 回调写入，不能保存事件里 new 出来的那个实例
     * （NeoForge 明确要求，且 F3+T 重载资源时会重新走一遍注册）。
     */
    private static volatile ShaderInstance shader;

    private static final RenderStateShard.ShaderStateShard SHADER_STATE =
        new RenderStateShard.ShaderStateShard(() -> shader);

    public static final RenderType ORB_OF_LUCK = RenderType.create(
        "jafa_orb_of_luck",
        FORMAT,
        VertexFormat.Mode.QUADS,
        1536,
        false,
        false,
        RenderType.CompositeState.builder()
            .setShaderState(SHADER_STATE)
            .setTextureState(RenderStateShard.NO_TEXTURE)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setLightmapState(RenderStateShard.NO_LIGHTMAP)
            .setOverlayState(RenderStateShard.NO_OVERLAY)
            .createCompositeState(true));

    /**
     * 手持版：与 {@link #ORB_OF_LUCK} 同一个着色器，唯一区别是<b>关掉深度测试</b>。
     * <p>
     * 手里的光球画在"手"的位置上，也就是拳头内部；若还做深度测试，会被手臂模型切掉一半、
     * 只剩下一个残月。关掉深度测试后它整颗盖在手上，看起来就是"一团光裹住手"。
     */
    public static final RenderType ORB_OF_LUCK_HAND = RenderType.create(
        "jafa_orb_of_luck_hand",
        FORMAT,
        VertexFormat.Mode.QUADS,
        1536,
        false,
        false,
        RenderType.CompositeState.builder()
            .setShaderState(SHADER_STATE)
            .setTextureState(RenderStateShard.NO_TEXTURE)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setLightmapState(RenderStateShard.NO_LIGHTMAP)
            .setOverlayState(RenderStateShard.NO_OVERLAY)
            .createCompositeState(true));

    private OrbOfLuckRenderType() {
    }

    /** shader 是否已就绪。加载完成前直接跳过渲染，避免 {@code ShaderStateShard} 因空实例断言失败。 */
    public static boolean isReady() {
        return shader != null;
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
            new ShaderInstance(event.getResourceProvider(), SHADER_ID, FORMAT),
            loaded -> shader = loaded);
    }
}
