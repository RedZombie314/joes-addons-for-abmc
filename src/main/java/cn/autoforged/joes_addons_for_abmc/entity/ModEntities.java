package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, ModMain.MODID);

    public static final Supplier<EntityType<ThrownGlisteringMelonKnife>> THROWN_GLISTERING_MELON_KNIFE =
        ENTITIES.register("thrown_glistering_melon_knife",
            () -> EntityType.Builder.<ThrownGlisteringMelonKnife>of(ThrownGlisteringMelonKnife::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .clientTrackingRange(4)
                .updateInterval(10)
                .build("thrown_glistering_melon_knife"));

    public static final Supplier<EntityType<PrismarineArrow>> PRISMARINE_ARROW =
        ENTITIES.register("prismarine_arrow",
            () -> EntityType.Builder.<PrismarineArrow>of(PrismarineArrow::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .clientTrackingRange(4)
                .updateInterval(20)
                .build("prismarine_arrow"));

    public static final Supplier<EntityType<BedrockFallingBlockEntity>> BEDROCK_FALLING_BLOCK =
        ENTITIES.register("bedrock_falling_block",
            () -> EntityType.Builder.<BedrockFallingBlockEntity>of(BedrockFallingBlockEntity::new, MobCategory.MISC)
                .sized(0.98F, 0.98F)
                .clientTrackingRange(10)
                .updateInterval(20)
                .build("bedrock_falling_block"));

    public static final Supplier<EntityType<LapisFallingBlockEntity>> LAPIS_FALLING_BLOCK =
        ENTITIES.register("lapis_falling_block",
            () -> EntityType.Builder.<LapisFallingBlockEntity>of(LapisFallingBlockEntity::new, MobCategory.MISC)
                .sized(0.98F, 0.98F)
                .clientTrackingRange(10)
                .updateInterval(20)
                .build("lapis_falling_block"));

    public static final Supplier<EntityType<TransmutationFallingBlockEntity>> TRANSMUTATION_FALLING_BLOCK =
        ENTITIES.register("transmutation_falling_block",
            () -> EntityType.Builder.<TransmutationFallingBlockEntity>of(TransmutationFallingBlockEntity::new, MobCategory.MISC)
                .sized(0.98F, 0.98F)
                .clientTrackingRange(10)
                .updateInterval(20)
                .build("transmutation_falling_block"));

    public static final Supplier<EntityType<DripstoneFallingBlockEntity>> DRIPSTONE_FALLING_BLOCK =
        ENTITIES.register("dripstone_falling_block",
            () -> EntityType.Builder.<DripstoneFallingBlockEntity>of(DripstoneFallingBlockEntity::new, MobCategory.MISC)
                .sized(0.98F, 0.98F)
                .clientTrackingRange(10)
                .updateInterval(1)
                .build("dripstone_falling_block"));

    public static final Supplier<EntityType<PortalEntity>> PORTAL =
        ENTITIES.register("portal",
            () -> EntityType.Builder.<PortalEntity>of(PortalEntity::new, MobCategory.MISC)
                .sized(2.0F, 2.0F)
                .clientTrackingRange(64)
                .updateInterval(20)
                .build("portal"));

    public static final Supplier<EntityType<PotionPortalEntity>> POTION_PORTAL =
        ENTITIES.register("potion_portal",
            () -> EntityType.Builder.<PotionPortalEntity>of(PotionPortalEntity::new, MobCategory.MISC)
                .sized(1.0F, 2.0F)
                .clientTrackingRange(64)
                .updateInterval(20)
                .build("potion_portal"));

    public static final Supplier<EntityType<PlayerShellEntity>> PLAYER_SHELL =
        ENTITIES.register("player_shell",
            () -> EntityType.Builder.<PlayerShellEntity>of(PlayerShellEntity::new, MobCategory.MISC)
                .sized(0.6F, 1.8F)
                .clientTrackingRange(10)
                .updateInterval(3)
                .build("player_shell"));

    public static final Supplier<EntityType<HerobrineHeadEntity>> HEROBRINE_HEAD =
        ENTITIES.register("herobrine_head",
            () -> EntityType.Builder.<HerobrineHeadEntity>of(HerobrineHeadEntity::new, MobCategory.MISC)
                .sized(0.3125F, 0.3125F)
                .clientTrackingRange(4)
                .updateInterval(20)
                .build("herobrine_head"));

    public static final Supplier<EntityType<TntStaffPrimedTnt>> TNT_STAFF_PRIMED_TNT =
        ENTITIES.register("tnt_staff_primed_tnt",
            () -> EntityType.Builder.<TntStaffPrimedTnt>of(TntStaffPrimedTnt::new, MobCategory.MISC)
                .sized(0.98F, 0.98F)
                .clientTrackingRange(8)
                .updateInterval(10)
                .build("tnt_staff_primed_tnt"));

    public static final Supplier<EntityType<TntStaffCreeper>> TNT_STAFF_CREEPER =
        ENTITIES.register("tnt_staff_creeper",
            () -> EntityType.Builder.<TntStaffCreeper>of(TntStaffCreeper::new, MobCategory.MONSTER)
                .sized(0.6F, 1.7F)
                .clientTrackingRange(8)
                .updateInterval(2)
                .build("tnt_staff_creeper"));
}
