package cn.autoforged.joes_addons_for_abmc.potion;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
        DeferredRegister.create(Registries.MOB_EFFECT, ModMain.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> AWAKENING = MOB_EFFECTS.register("awakening",
        () -> new AwakeningMobEffect());

    public static final DeferredHolder<MobEffect, MobEffect> TRANSPORTATION = MOB_EFFECTS.register("transportation",
        () -> new TransportationMobEffect());

    public static final DeferredHolder<MobEffect, MobEffect> TRANSMUTATION = MOB_EFFECTS.register("transmutation",
        () -> new TransmutationMobEffect());

    public static final DeferredHolder<MobEffect, MobEffect> TRANSMUTATION_ANTIDOTE =
        MOB_EFFECTS.register("transmutation_antidote",
            () -> new TransmutationAntidoteMobEffect());

    public static final DeferredHolder<MobEffect, MobEffect> KNOCKBACK = MOB_EFFECTS.register("knockback",
        () -> new KnockbackMobEffect());

    // ============ 原版附魔名批量注册为占位状态效果 ============
    // 仅注册为新的状态效果（无实际效果、无贴图），对应附魔魔咒名称。
    // 若同名状态效果已在注册表中存在则跳过，避免重复注册。
    public static final java.util.List<DeferredHolder<MobEffect, MobEffect>> ENCHANTMENT_EFFECTS =
        registerEnchantmentEffects();

    private static java.util.List<DeferredHolder<MobEffect, MobEffect>> registerEnchantmentEffects() {
        String[] names = {
            // 原版附魔英文注册名
            "aqua_affinity", "bane_of_arthropods", "blast_protection", "breach", "channeling",
            "binding_curse", "vanishing_curse", "density", "depth_strider", "efficiency",
            "feather_falling", "fire_aspect", "fire_protection", "flame", "fortune",
            "frost_walker", "impaling", "infinity", "looting",
            "loyalty", "luck_of_the_sea", "lure", "mending", "multishot",
            "piercing", "power", "projectile_protection", "protection", "punch",
            "quick_charge", "respiration", "riptide", "sharpness", "silk_touch",
            "smite", "soul_speed", "sweeping_edge", "swift_sneak", "thorns", "unbreaking",
            "wind_burst"
        };
        java.util.List<DeferredHolder<MobEffect, MobEffect>> list = new java.util.ArrayList<>();
        for (String n : names) {
            net.minecraft.resources.ResourceLocation rl =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ModMain.MODID, n);
            if (net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.containsKey(rl)) {
                // 已存在则跳过
                continue;
            }
            list.add(MOB_EFFECTS.register(n, () -> new PlaceholderMobEffect()));
        }
        return list;
    }

    /** 占位状态效果：无实际效果、无贴图，仅用于注册。 */
    public static class PlaceholderMobEffect extends MobEffect {
        public PlaceholderMobEffect() {
            super(MobEffectCategory.NEUTRAL, 0x6B8E6B);
        }
    }

    public static class AwakeningMobEffect extends MobEffect {
        public AwakeningMobEffect() {
            super(MobEffectCategory.BENEFICIAL, 0x23A248);
        }
    }

    public static class TransportationMobEffect extends MobEffect {
        public TransportationMobEffect() {
            super(MobEffectCategory.BENEFICIAL, 0x818A8F);
        }

        @Override
        public boolean isInstantenous() {
            return true;
        }
    }

    public static class KnockbackMobEffect extends MobEffect {
        public KnockbackMobEffect() {
            super(MobEffectCategory.NEUTRAL, 0xD4C8A8);
        }
    }

    public static class TransmutationAntidoteMobEffect extends MobEffect {
        public TransmutationAntidoteMobEffect() {
            super(MobEffectCategory.BENEFICIAL, 0x7FBF7F);
        }

        @Override
        public boolean isInstantenous() {
            return true;
        }
    }
}
