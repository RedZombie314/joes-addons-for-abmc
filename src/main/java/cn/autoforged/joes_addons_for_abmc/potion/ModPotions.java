package cn.autoforged.joes_addons_for_abmc.potion;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

    // 变形药水：showIcon=false —— 变形状态不在玩家 HUD 上渲染效果图标
    public static final DeferredHolder<Potion, Potion> TRANSMUTATION = POTIONS.register("transmutation",
        () -> new Potion(new MobEffectInstance(ModMobEffects.TRANSMUTATION, 2000, 0, false, false, false)));

    public static final DeferredHolder<Potion, Potion> LONG_TRANSMUTATION = POTIONS.register("long_transmutation",
        () -> new Potion("transmutation", new MobEffectInstance(ModMobEffects.TRANSMUTATION, 4000, 0, false, false, false)));

    public static final DeferredHolder<Potion, Potion> TRANSMUTATION_ANTIDOTE =
        POTIONS.register("transmutation_antidote",
            () -> new Potion(new MobEffectInstance(ModMobEffects.TRANSMUTATION_ANTIDOTE, 1)));

    /** 英雄药水的持续时间：3 分钟 = 3600 刻。 */
    public static final int HEROISM_DURATION_TICKS = 3 * 60 * 20;

    /**
     * 英雄药水（Potion of Heroism）：饮用后 3 分钟内获得
     * <b>力量 V、速度 V、抗性提升 IV、伤害吸收 V、再生 V、急迫 V、跳跃提升 II</b>
     * （I 级 = amplifier 0，所以 V 级 = 4、IV 级 = 3、II 级 = 1）。
     *
     * <p>名字走原版规则：语言键 {@code item.minecraft.potion.effect.heroism}（= Potion of Heroism）。
     * 由幸运物品子事件 {@code item/heroism_potion} 发放（见 {@code LuckyHeroEvents}）。
     */
    public static final DeferredHolder<Potion, Potion> HEROISM = POTIONS.register("heroism",
        () -> new Potion(
            new MobEffectInstance(MobEffects.DAMAGE_BOOST, HEROISM_DURATION_TICKS, 4),      // 力量 V
            new MobEffectInstance(MobEffects.MOVEMENT_SPEED, HEROISM_DURATION_TICKS, 4),    // 速度 V
            new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, HEROISM_DURATION_TICKS, 3), // 抗性提升 IV
            new MobEffectInstance(MobEffects.ABSORPTION, HEROISM_DURATION_TICKS, 4),        // 伤害吸收 V
            new MobEffectInstance(MobEffects.REGENERATION, HEROISM_DURATION_TICKS, 4),      // 再生 V
            new MobEffectInstance(MobEffects.DIG_SPEED, HEROISM_DURATION_TICKS, 4),         // 急迫 V
            new MobEffectInstance(MobEffects.JUMP, HEROISM_DURATION_TICKS, 1)));            // 跳跃提升 II
}
