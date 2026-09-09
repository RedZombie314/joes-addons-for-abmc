package cn.autoforged.joes_addons_for_abmc.loot;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 自定义战利品函数类型注册。
 */
public class ModLootFunctions {
    public static final DeferredRegister<LootItemFunctionType<?>> LOOT_FUNCTIONS =
        DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, ModMain.MODID);

    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<SetModPotionFunction>> SET_MOD_POTION =
        LOOT_FUNCTIONS.register("set_mod_potion", () -> new LootItemFunctionType<>(SetModPotionFunction.CODEC));
}