package cn.autoforged.joes_addons_for_abmc.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

public class ModTiers {
    public static final Tier GLISTERING_MELON_KNIFE = new SimpleTier(
        BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
        50,
        8.0F,
        0.0F,
        14,
        () -> Ingredient.of(Items.GLISTERING_MELON_SLICE)
    );

    public static final Tier GIANT_NETHERITE_SWORD = new SimpleTier(
        BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
        7100,
        9.0F,
        4.0F,
        15,
        () -> Ingredient.of(Items.NETHERITE_INGOT)
    );

    public static final Tier GIANT_NETHERITE_AXE = new SimpleTier(
        BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
        7100,
        9.0F,
        4.0F,
        15,
        () -> Ingredient.of(Items.NETHERITE_INGOT)
    );
}
