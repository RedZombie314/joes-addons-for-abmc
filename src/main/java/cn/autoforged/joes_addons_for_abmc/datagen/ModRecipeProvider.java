package cn.autoforged.joes_addons_for_abmc.datagen;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        // 闪烁西瓜刀（glistering_melon_knife）**故意没有合成配方**（需求 6.7.2：只能从幸运方块开出）。
        // 原来这里是 " 西瓜片 / 岩浆膏 / 不死图腾 " 竖排三格的配方，连同它自动生成的
        // recipe-book 解锁进度（advancement/recipes/combat/glistering_melon_knife.json）一起删掉了。
        // 获取途径只剩：幸运物品池 #joes_addons_for_abmc:lucky_items（幸运方块开出）与创造模式物品栏。
        // 注意"工作台帽子 / 合成权杖"那套合成事件是从 *配方注册表* 里取配方的
        // （见 crafting/CraftingScan：player.server.getRecipeManager().getOrderedRecipes()），
        // 所以配方一删，那边也自然合成不出这把刀，不需要额外处理。

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.NETHERITE_CORE.get())
            .pattern("NNN")
            .pattern("NNN")
            .pattern("NNN")
            .define('N', Items.NETHERITE_BLOCK)
            .unlockedBy("has_netherite_block", has(Items.NETHERITE_BLOCK))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.GIANT_NETHERITE_BOW.get())
            .pattern("XX ")
            .pattern("X X")
            .pattern("XX ")
            .define('X', ModItems.NETHERITE_CORE.get())
            .unlockedBy("has_netherite_core", has(ModItems.NETHERITE_CORE.get()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.GIANT_NETHERITE_ARROW.get(), 8)
            .pattern("  B")
            .pattern(" B ")
            .pattern("B  ")
            .define('B', Items.NETHERITE_BLOCK)
            .unlockedBy("has_netherite_block", has(Items.NETHERITE_BLOCK))
            .save(output);

        // 命名牌：纸 + 铁粒（无序），与纸 + 金粒（无序）各一条配方
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Items.NAME_TAG)
            .requires(Items.PAPER)
            .requires(Ingredient.of(Items.IRON_NUGGET))
            .unlockedBy("has_paper", has(Items.PAPER))
            .save(output, ResourceLocation.fromNamespaceAndPath(
                cn.autoforged.joes_addons_for_abmc.ModMain.MODID, "name_tag_paper_iron_nugget"));

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Items.NAME_TAG)
            .requires(Items.PAPER)
            .requires(Ingredient.of(Items.GOLD_NUGGET))
            .unlockedBy("has_paper", has(Items.PAPER))
            .save(output, ResourceLocation.fromNamespaceAndPath(
                cn.autoforged.joes_addons_for_abmc.ModMain.MODID, "name_tag_paper_gold_nugget"));

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.PRISMARINE_BOW.get())
            .pattern(" PC")
            .pattern("P C")
            .pattern(" PC")
            .define('P', Items.PRISMARINE_SHARD)
            .define('C', Items.CHAIN)
            .unlockedBy("has_prismarine_shard", has(Items.PRISMARINE_SHARD))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, ModItems.GIANT_NETHERITE_SWORD.get())
            .pattern(" X ")
            .pattern(" X ")
            .pattern(" B ")
            .define('X', ModItems.NETHERITE_CORE.get())
            .define('B', Items.NETHERITE_BLOCK)
            .unlockedBy("has_netherite_core", has(ModItems.NETHERITE_CORE.get()))
            .save(output);

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.GIANT_NETHERITE_AXE.get())
            .pattern(" XX")
            .pattern(" BX")
            .pattern(" B ")
            .define('X', ModItems.NETHERITE_CORE.get())
            .define('B', Items.NETHERITE_BLOCK)
            .unlockedBy("has_netherite_core", has(ModItems.NETHERITE_CORE.get()))
            .save(output);
    }
}
