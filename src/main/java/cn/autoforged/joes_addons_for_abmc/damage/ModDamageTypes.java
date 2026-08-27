package cn.autoforged.joes_addons_for_abmc.damage;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 与“物品/方块体验卡过期”死亡消息绑定的自定义伤害类型。
 * 通过它们击杀目标，会复用 {@code death.attack.<msgId>} 对应的死亡消息，
 * 从而由原版死亡播报直接展示自定义文案，避免双重播报。
 */
public class ModDamageTypes {
    public static final DeferredRegister<DamageType> DAMAGE_TYPES =
        DeferredRegister.create(Registries.DAMAGE_TYPE, ModMain.MODID);

    public static final DeferredHolder<DamageType, DamageType> TRANSMUTATION_ITEM_EXPIRED =
        DAMAGE_TYPES.register("transmutation_item_expired",
            () -> new DamageType("transmutation.item",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1F));

    public static final DeferredHolder<DamageType, DamageType> TRANSMUTATION_BLOCK_EXPIRED =
        DAMAGE_TYPES.register("transmutation_block_expired",
            () -> new DamageType("transmutation.block",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1F));

    // 玩家空壳被杀死时，原生物（玩家/宠物）被此伤害击杀，播报“XX的玩家体验卡到期了”
    public static final DeferredHolder<DamageType, DamageType> TRANSMUTATION_PLAYER_EXPIRED =
        DAMAGE_TYPES.register("transmutation_player_expired",
            () -> new DamageType("transmutation.player",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1F));

    // 刷怪蛋变形出的生物壳被杀死时，原生物（玩家/宠物）被此伤害击杀，播报“XX的生物体验卡过期了”
    public static final DeferredHolder<DamageType, DamageType> TRANSMUTATION_BIOM_EXPIRED =
        DAMAGE_TYPES.register("transmutation_biom_expired",
            () -> new DamageType("transmutation.biom",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1F));

    // Herobrine 头颅的真实伤害：无视护甲、抗性提升与附魔减伤，直接扣血。
    // 通过数据包标签（bypasses_armor / bypasses_resistance / bypasses_enchantments）实现。
    public static final DeferredHolder<DamageType, DamageType> HEROBRINE_HEAD =
        DAMAGE_TYPES.register("herobrine_head",
            () -> new DamageType("herobrine.head",
                DamageScaling.NEVER, 0.0F));

    // 红石块权杖激光击杀：播报“XX被天选之子的激光烧穿了”
    public static final DeferredHolder<DamageType, DamageType> LASER =
        DAMAGE_TYPES.register("laser",
            () -> new DamageType("laser",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1F));

    // TNT 权杖特制苦力怕爆炸。其 death.attack.<msgId> 在 lang 中设为空字符串，
    // 从而吞掉原版死亡播报，由 onLivingDeath 统一播报“被自己/被某人召唤的苦力怕炸死”两种文案。
    public static final DeferredHolder<DamageType, DamageType> JOES_TNT_STAFF_CREEPER =
        DAMAGE_TYPES.register("joes_tnt_staff_creeper",
            () -> new DamageType("joes_tnt_staff_creeper",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1F));

    // 冰块权杖的霜冰被非融化因素破坏时，被困生物受到的伤害。
    // 播放死亡信息：“XX随着冰的消融而碎裂”。
    public static final DeferredHolder<DamageType, DamageType> FROST_ICE_SHATTER =
        DAMAGE_TYPES.register("frost_ice_shatter",
            () -> new DamageType("frost_ice_shatter",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, 0.1F));
}