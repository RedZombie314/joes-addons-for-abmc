package cn.autoforged.joes_addons_for_abmc.potion;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModPotions {
    public static final DeferredRegister<Potion> POTIONS =
        DeferredRegister.create(Registries.POTION, ModMain.MODID);

    public static final DeferredHolder<Potion, Potion> HAUNTED = POTIONS.register("haunted",
        () -> new Potion());

    public static final DeferredHolder<Potion, Potion> AWAKENING = POTIONS.register("awakening",
        () -> new Potion(new MobEffectInstance(ModMobEffects.AWAKENING, 900)));

    public static final DeferredHolder<Potion, Potion> LONG_AWAKENING = POTIONS.register("long_awakening",
        () -> new Potion("awakening", new MobEffectInstance(ModMobEffects.AWAKENING, 1800)));

    public static final DeferredHolder<Potion, Potion> TRANSPORTATION = POTIONS.register("transportation",
        () -> new Potion(new MobEffectInstance(ModMobEffects.TRANSPORTATION, 1)));

    // 准传送药水：由 闹鬼的药水 + 末影珍珠 酿成；再加下界疣即得 传送药水(transportation)
    public static final DeferredHolder<Potion, Potion> PRE_TRANSPORTATION = POTIONS.register("pre_transportation",
        () -> new Potion());

    public static final DeferredHolder<Potion, Potion> PRE_TRANSMUTATION = POTIONS.register("pre_transmutation",
        () -> new Potion());

    public static final DeferredHolder<Potion, Potion> TRANSMUTATION = POTIONS.register("transmutation",
        () -> new Potion(new MobEffectInstance(ModMobEffects.TRANSMUTATION, 2000)));

    public static final DeferredHolder<Potion, Potion> LONG_TRANSMUTATION = POTIONS.register("long_transmutation",
        () -> new Potion("transmutation", new MobEffectInstance(ModMobEffects.TRANSMUTATION, 4000)));

    public static final DeferredHolder<Potion, Potion> TRANSMUTATION_ANTIDOTE =
        POTIONS.register("transmutation_antidote",
            () -> new Potion(new MobEffectInstance(ModMobEffects.TRANSMUTATION_ANTIDOTE, 1)));
}
