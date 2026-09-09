package cn.autoforged.joes_addons_for_abmc.potion;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;

import java.util.List;
import java.util.Optional;

/**
 * 觉醒药水的酿造配方（与传送门药水一致）：不存在直饮形式，酿造出来就是喷溅觉醒药水。
 * <ul>
 *   <li>闹鬼的药水 +  雕刻南瓜 → 喷溅觉醒药水（无直饮）；</li>
 *   <li>喷溅觉醒药水 + 红石     → 喷溅长效觉醒药水。</li>
 * </ul>
 * 所有输出均为 {@code SPLASH_POTION}，不产出 {@code Items.POTION}（可饮用）的觉醒药水。
 */
public class AwakeningBrewingRecipe implements IBrewingRecipe {

    private static boolean isPotionItem(ItemStack stack) {
        return stack.getItem() == Items.POTION
            || stack.getItem() == Items.SPLASH_POTION
            || stack.getItem() == Items.LINGERING_POTION;
    }

    private static Optional<Holder<Potion>> potionOf(ItemStack stack) {
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return contents.potion();
    }

    private static ItemStack splashPotion(Holder<Potion> potion) {
        ItemStack result = new ItemStack(Items.SPLASH_POTION);
        result.set(DataComponents.POTION_CONTENTS,
            new PotionContents(Optional.of(potion), Optional.empty(), List.of()));
        return result;
    }

    @Override
    public boolean isInput(ItemStack input) {
        if (!isPotionItem(input)) return false;
        Optional<Holder<Potion>> p = potionOf(input);
        if (p.isEmpty()) return false;
        return p.get().is(ModPotions.HAUNTED.getKey())
            || p.get().is(ModPotions.AWAKENING.getKey());
    }

    @Override
    public boolean isIngredient(ItemStack ingredient) {
        if (ingredient.isEmpty()) return false;
        return ingredient.getItem() == Items.CARVED_PUMPKIN
            || ingredient.getItem() == Items.REDSTONE;
    }

    @Override
    public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        if (!isInput(input) || !isIngredient(ingredient)) return ItemStack.EMPTY;
        Optional<Holder<Potion>> p = potionOf(input);
        if (p.isEmpty()) return ItemStack.EMPTY;
        Holder<Potion> inputPotion = p.get();

        // 闹鬼的药水 + 雕刻南瓜 → 喷溅觉醒药水（无直饮）
        if (inputPotion.is(ModPotions.HAUNTED.getKey())
                && ingredient.getItem() == Items.CARVED_PUMPKIN) {
            return splashPotion(ModPotions.AWAKENING);
        }
        // 喷溅觉醒药水 + 红石 → 喷溅长效觉醒药水
        if (inputPotion.is(ModPotions.AWAKENING.getKey())
                && ingredient.getItem() == Items.REDSTONE) {
            return splashPotion(ModPotions.LONG_AWAKENING);
        }
        return ItemStack.EMPTY;
    }
}