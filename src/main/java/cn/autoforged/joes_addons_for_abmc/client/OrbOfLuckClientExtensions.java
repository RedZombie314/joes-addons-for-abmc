package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * 把 {@link OrbOfLuckItemRenderer} 挂到「幸运核心」物品与它的刷怪蛋上（两者共用同一个光球渲染器）。
 * <p>
 * 用 {@link RegisterClientExtensionsEvent}（而不是已标记删除的 {@code Item#initializeClient}）
 * 注册 {@link IClientItemExtensions}。物品模型是 {@code builtin/entity}，原版据此走自定义渲染器。
 * <p>
 * 该事件实现 {@code IModBusEvent}，所以 {@code @EventBusSubscriber} 不需要（也不该）再写 {@code bus}。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = ModMain.MODID)
public final class OrbOfLuckClientExtensions {

    private OrbOfLuckClientExtensions() {
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return OrbOfLuckItemRenderer.get();
            }
        }, ModItems.ORB_OF_LUCK.get(), ModItems.ORB_OF_LUCK_SPAWN_EGG.get());
    }
}
