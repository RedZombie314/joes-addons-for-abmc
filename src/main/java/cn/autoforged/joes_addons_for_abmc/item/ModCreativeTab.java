package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.block.ModBlocks;
import cn.autoforged.joes_addons_for_abmc.item.ModDataComponents;
import cn.autoforged.joes_addons_for_abmc.potion.ModPotions;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 创造模式物品栏 - Joes Addons for ABMC
 *
 * 当添加新的权杖类型（blocktype）时，请记得在此处添加对应的 ItemStack，
 * 以确保新权杖能够在创造模式物品栏中出现。
 * 添加位置：在 staffIcons 数组中添加新的条目即可。
 */
public class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ModMain.MODID);

    /**
     * 所有已注册的权杖方块类型（blocktype）。
     * 当添加新的 blocktype 时，请在此数组中添加对应的字符串。
     * 注意：此数组用于创建所有权杖变体的 ItemStack。
     */
    private static final String[] ALL_STAFF_BLOCK_TYPES = {
        "empty",
        "gold_block",
        "diamond_block",
        "netherite_block",
        "bedrock",
        "obsidian",
        "bone_block",
        "furnace",
        "bell",
        "anvil",
        "lapis_block",
        "magma_block",
        "command_block",
        "end_portal_frame",
        "enchanting_table",
        "player_head",
        "herobrine_head",
        "barrier",
        "dripstone_block",
        "minecraft_game_icon",
        "omega",
        "cauldron",
        "crafting_table",
        "emerald_block",
        "ice",
        "iron_block",
        "netherrack",
        "note_block",
        "oak_log",
        "piston",
        "red_mushroom_block",
        "redstone_block",
        "snow_block",
        "bee_nest",
        "amethyst_block",
        "cobweb",
        "spawner",
        "tnt"
    };

    public static final Supplier<CreativeModeTab> JOES_ADDONS_TAB = CREATIVE_MODE_TABS.register(
        "joes_addons_tab",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + ModMain.MODID + ".joes_addons_tab"))
            .icon(() -> new ItemStack(ModItems.STAFF.get()))
            .displayItems((parameters, output) -> {
                // ===== 权杖（所有 blocktype 变体） =====
                // 当添加新的 blocktype 时，只需在 ALL_STAFF_BLOCK_TYPES 数组中添加对应字符串即可
                for (String blockType : ALL_STAFF_BLOCK_TYPES) {
                    ItemStack staffStack = new ItemStack(ModItems.STAFF.get());
                    if (!"empty".equals(blockType)) {
                        staffStack.set(ModDataComponents.BLOCKTYPE.get(), blockType);
                    }
                    // 设置权杖的初始耐久值
                    StaffItem.setBlockDamage(staffStack, 0);
                    output.accept(staffStack);
                }

                // ===== 其他工具和物品 =====
                output.accept(ModItems.GLISTERING_MELON_KNIFE.get());
                output.accept(ModItems.GLISTERING_MELON_KNIFE.get()); // 第二把用于展示在创造栏中

                output.accept(ModItems.NETHERITE_CORE.get());
                output.accept(ModItems.GIANT_NETHERITE_BOW.get());
                output.accept(ModItems.GIANT_NETHERITE_ARROW.get());
                output.accept(ModItems.PRISMARINE_BOW.get());
                output.accept(ModItems.GIANT_NETHERITE_SWORD.get());
                output.accept(ModItems.GIANT_NETHERITE_AXE.get());
                output.accept(ModItems.PRISMARINE_ARROW.get());
                output.accept(ModItems.GAME_ICON.get());
                output.accept(ModItems.OMEGA_GAME_ICON.get());

                // ===== 方块 =====
                output.accept(new ItemStack(ModBlocks.HORIZONTAL_DRIPSTONE.get()));

                // ===== 变形药水 =====
                // 随机变形喷溅药水：未绑定固定目标，命中生物的瞬间从原版方块中随机选取一个作为变形目标
                ItemStack randomPotion = splashPotion(ModPotions.TRANSMUTATION, 0x9370DB);
                randomPotion.set(DataComponents.CUSTOM_NAME, Component.literal("变形为§krandom"));
                output.accept(randomPotion);

                // 喷溅型变形药水：骨头物品
                ItemStack bonePotion = splashPotion(ModPotions.TRANSMUTATION, 0xE8E8E8);
                bonePotion.set(ModDataComponents.ITEM_TYPE.get(), "minecraft:bone");
                bonePotion.set(DataComponents.CUSTOM_NAME, Component.literal("变形为 骨头"));
                output.accept(bonePotion);

                // 喷溅型变形药水：黑石方块
                ItemStack blackstonePotion = splashPotion(ModPotions.TRANSMUTATION, 0x2A2A2A);
                blackstonePotion.set(ModDataComponents.ITEM_TYPE.get(), "minecraft:blackstone");
                blackstonePotion.set(DataComponents.CUSTOM_NAME, Component.literal("变形为 黑石"));
                output.accept(blackstonePotion);

                // 喷溅型变形药水：尸壳
                ItemStack huskPotion = splashPotion(ModPotions.TRANSMUTATION, 0x8B4513);
                huskPotion.set(ModDataComponents.ITEM_TYPE.get(), "mob_shell:minecraft:husk");
                huskPotion.set(DataComponents.CUSTOM_NAME, Component.literal("变形为 尸壳"));
                output.accept(huskPotion);

                // 喷溅型变形药水：Dream 玩家空壳
                ItemStack dreamPotion = splashPotion(ModPotions.TRANSMUTATION, 0x50C878);
                String dreamEncoded = "player_shell:" + Base64.getEncoder()
                    .encodeToString("Dream".getBytes(StandardCharsets.UTF_8));
                dreamPotion.set(ModDataComponents.ITEM_TYPE.get(), dreamEncoded);
                dreamPotion.set(DataComponents.CUSTOM_NAME, Component.literal("变形为 Dream"));
                output.accept(dreamPotion);

                // 变形解药（喷溅）：使被溅射到的变身方块/物品提前复原为生物形态
                output.accept(splashPotion(ModPotions.TRANSMUTATION_ANTIDOTE, 0x7FBF7F));
            })
            .build()
    );

    // 构造给定药水的喷溅形态 ItemStack
    private static ItemStack splashPotion(Holder<Potion> potion, int color) {
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);
        stack.set(DataComponents.POTION_CONTENTS,
            new PotionContents(Optional.of(potion), Optional.of(color), List.of()));
        return stack;
    }
}