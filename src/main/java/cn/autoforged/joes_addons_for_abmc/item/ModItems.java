package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ModMain.MODID);

    public static final DeferredItem<GlisteringMelonKnifeItem> GLISTERING_MELON_KNIFE =
        ITEMS.register("glistering_melon_knife",
            () -> new GlisteringMelonKnifeItem(ModTiers.GLISTERING_MELON_KNIFE, new Item.Properties()
                .durability(50)
                .stacksTo(1)
                .attributes(GlisteringMelonKnifeItem.createAttributes())));

    public static final DeferredItem<Item> NETHERITE_CORE =
        ITEMS.register("netherite_core",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final DeferredItem<Item> GIANT_NETHERITE_BOW =
        ITEMS.register("giant_netherite_bow",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> GIANT_NETHERITE_ARROW =
        ITEMS.register("giant_netherite_arrow",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final DeferredItem<PrismarineBowItem> PRISMARINE_BOW =
        ITEMS.register("prismarine_bow",
            () -> new PrismarineBowItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<GiantNetheriteSwordItem> GIANT_NETHERITE_SWORD =
        ITEMS.register("giant_netherite_sword",
            () -> new GiantNetheriteSwordItem(ModTiers.GIANT_NETHERITE_SWORD, new Item.Properties()
                .durability(7100)
                .stacksTo(1)
                .attributes(GiantNetheriteSwordItem.createAttributes())));

    public static final DeferredItem<GiantNetheriteAxeItem> GIANT_NETHERITE_AXE =
        ITEMS.register("giant_netherite_axe",
            () -> new GiantNetheriteAxeItem(ModTiers.GIANT_NETHERITE_AXE, new Item.Properties()
                .durability(7100)
                .stacksTo(1)
                .attributes(GiantNetheriteAxeItem.createAttributes())));

    public static final DeferredItem<Item> PRISMARINE_ARROW =
        ITEMS.register("prismarine_arrow",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final DeferredItem<StaffItem> STAFF =
        ITEMS.register("staff",
            () -> new StaffItem(new Item.Properties()
                .durability(100000)
                .stacksTo(1)));

    public static final DeferredItem<GameIconItem> GAME_ICON =
        ITEMS.register("game_icon",
            () -> new GameIconItem(new Item.Properties()
                .stacksTo(1)));

    public static final DeferredItem<GameIconItem> OMEGA_GAME_ICON =
        ITEMS.register("omega_game_icon",
            () -> new GameIconItem(new Item.Properties()
                .stacksTo(1)));
}
