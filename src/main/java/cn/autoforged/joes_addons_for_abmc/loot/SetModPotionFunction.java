package cn.autoforged.joes_addons_for_abmc.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;

/**
 * 自定义战利品函数：将物品的 POTION_CONTENTS 设为指定药水。
 * JSON 用法：{ "function": "joes_addons_for_abmc:set_mod_potion", "potion": "joes_addons_for_abmc:transmutation" }
 */
public class SetModPotionFunction extends LootItemConditionalFunction {

    public static final MapCodec<SetModPotionFunction> CODEC = RecordCodecBuilder.mapCodec(inst ->
        commonFields(inst).and(
            Codec.STRING.fieldOf("potion").forGetter(f -> f.potionId)
        ).apply(inst, SetModPotionFunction::new));

    private final String potionId;

    private SetModPotionFunction(List<LootItemCondition> conditions, String potionId) {
        super(conditions);
        this.potionId = potionId;
    }

    @Override
    public LootItemFunctionType<? extends LootItemConditionalFunction> getType() {
        return ModLootFunctions.SET_MOD_POTION.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        context.getLevel().registryAccess()
            .registryOrThrow(Registries.POTION)
            .getHolder(ResourceLocation.parse(potionId))
            .ifPresent(holder -> {
                int color = cn.autoforged.joes_addons_for_abmc.ModMain.getEntityTextureColorForPotion(potionId);
                if (color >= 0) {
                    stack.set(DataComponents.POTION_CONTENTS, new PotionContents(
                        java.util.Optional.of(holder),
                        java.util.Optional.of(color),
                        java.util.List.of()
                    ));
                } else {
                    stack.set(DataComponents.POTION_CONTENTS, new PotionContents(holder));
                }
            });
        return stack;
    }

    public static LootItemConditionalFunction.Builder<?> setModPotion(String potionId) {
        return simpleBuilder(conditions -> new SetModPotionFunction(conditions, potionId));
    }
}