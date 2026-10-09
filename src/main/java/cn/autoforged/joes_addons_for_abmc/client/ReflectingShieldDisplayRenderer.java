package cn.autoforged.joes_addons_for_abmc.client;

import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 「反弹盾牌」（{@code ReflectingShieldDisplay}）的渲染器。
 *
 * <p>它就是一个普通的物品展示实体，渲染逻辑与 {@code minecraft:item_display} 完全一样，所以直接继承原版
 * {@link DisplayRenderer.ItemDisplayRenderer}。
 *
 * <p><b>为什么还要派生一个空壳</b>：原版那个内部类的构造器是 {@code protected}（
 * {@code DisplayRenderer.java:133}），跨包没法直接 {@code new}
 * （{@code ClientEvents} 里那种 {@code event.registerEntityRenderer(type, ItemDisplayRenderer::new)} 写法编译不过）；
 * 继承一层、暴露一个 public 构造器就能用，而且不用改任何渲染代码。
 */
@OnlyIn(Dist.CLIENT)
public class ReflectingShieldDisplayRenderer extends DisplayRenderer.ItemDisplayRenderer {

    public ReflectingShieldDisplayRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}
