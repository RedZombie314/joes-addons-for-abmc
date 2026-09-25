package cn.autoforged.joes_addons_for_abmc.potion;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 无限酿造（策略性击败女巫Boss的奖励）：解除原版“红石/萤石只能各施加一次”的酿造上限。
 * <ul>
 *   <li>红石粉：每个非瞬时效果的持续时间 +8 分钟（9600 tick）。</li>
 *   <li>萤石粉：每个效果的等级 +1（上限 255）。</li>
 * </ul>
 * 仅对“持策略性击败奖励的玩家正在使用的酿造台”生效：由 BrewingStandBlockEntityMixin 在每刻的
 * serverTick 开头设置全局无限酿造标志，本配方读取该标志；标志未激活时返回空，回退到原版酿造逻辑。
 * 此外 isInput 仅接受原版命名空间（minecraft）的带效果药水，避免干扰本 mod 的变形/传送药水。
 */
public class UnlimitedBrewingRecipe implements IBrewingRecipe {

    /** 一次红石延长 8 分钟 = 480 秒 = 9600 tick（对应原版大多数“长效”药水时长）。 */
    private static final int REDSTONE_DURATION_INCREMENT = 9600;

    private static boolean isPotionItem(ItemStack stack) {
        return stack.getItem() == Items.POTION
            || stack.getItem() == Items.SPLASH_POTION
            || stack.getItem() == Items.LINGERING_POTION;
    }

    @Override
    public boolean isInput(ItemStack input) {
        if (!isPotionItem(input)) return false;
        PotionContents contents = input.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        // 只接受原版命名空间的带效果药水；本 mod 的变形/传送/觉醒药水由其它配方处理。
        Optional<Holder<Potion>> potion = contents.potion();
        if (potion.isPresent()) {
            ResourceKey<Potion> key = potion.get().getKey();
            if (!"minecraft".equals(key.location().getNamespace())) return false;
        }
        return contents.hasEffects();
    }

    @Override
    public boolean isIngredient(ItemStack ingredient) {
        if (ingredient.isEmpty()) return false;
        return ingredient.getItem() == Items.REDSTONE
            || ingredient.getItem() == Items.GLOWSTONE_DUST;
    }

    @Override
    public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        if (!isInput(input) || !isIngredient(ingredient)) return ItemStack.EMPTY;
        // 非奖励玩家（或非酿造台流程）不生效，回退原版逻辑
        if (!ModMain.isUnlimitedBrewingActive()) return ItemStack.EMPTY;

        boolean glowstone = ingredient.getItem() == Items.GLOWSTONE_DUST;
        boolean redstone = ingredient.getItem() == Items.REDSTONE;

        PotionContents contents = input.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        List<MobEffectInstance> modified = new ArrayList<>();
        boolean changed = false;
        for (MobEffectInstance inst : contents.getAllEffects()) {
            int amplifier = inst.getAmplifier();
            int duration = inst.getDuration();
            if (glowstone) {
                if (amplifier < 255) {
                    amplifier += 1;
                    changed = true;
                }
            } else if (redstone) {
                // 瞬时效果（瞬间治疗/瞬间伤害等）无法延长，跳过
                if (!inst.getEffect().value().isInstantenous()) {
                    duration = (int) Math.min(Integer.MAX_VALUE, (long) duration + REDSTONE_DURATION_INCREMENT);
                    changed = true;
                }
            }
            modified.add(new MobEffectInstance(inst.getEffect(), duration, amplifier,
                inst.isAmbient(), inst.isVisible(), inst.showIcon()));
        }
        if (!changed) return ItemStack.EMPTY;

        // 名字与投入的初始药水保持一致（保留“喷溅型/滞留型”前缀），而非显示“不可酿造的药水”：
        // 首次叠加取原药水的说明键，二次及以上叠加复用上一次产物已固化的自定义名。
        net.minecraft.network.chat.Component name = input.get(DataComponents.CUSTOM_NAME);
        if (name == null) {
            name = net.minecraft.network.chat.Component.translatable(input.getItem().getDescriptionId(input));
        }

        ItemStack result = new ItemStack(input.getItem());
        result.set(DataComponents.POTION_CONTENTS,
            new PotionContents(Optional.empty(), Optional.empty(), modified));
        result.set(DataComponents.CUSTOM_NAME, name);
        return result;
    }
}