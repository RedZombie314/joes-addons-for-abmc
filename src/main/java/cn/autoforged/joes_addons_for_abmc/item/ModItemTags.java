package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * 本模组的物品标签。
 */
public final class ModItemTags {
    /**
     * 幸运物品池：以后所有「幸运」相关的奖励/抽奖都从这里取物品。
     * <p>成员登记在 {@code data/joes_addons_for_abmc/tags/item/lucky_items.json}（数据包可扩展），
     * 代码里用 {@code stack.is(ModItemTags.LUCKY_ITEMS)} 判断。
     * <p>目前池子还没有任何消费方，属于先占位、以后启用。
     */
    public static final TagKey<Item> LUCKY_ITEMS = TagKey.create(Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "lucky_items"));

    private ModItemTags() {
    }
}
