package cn.autoforged.joes_addons_for_abmc.block.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ModMain.MODID);

    public static final Supplier<BlockEntityType<LuckyDimensionBlockEntity>> LUCKY_DIMENSION_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("lucky_dimension_block_entity",
            () -> BlockEntityType.Builder.of(
                LuckyDimensionBlockEntity::new,
                ModBlocks.LUCKY_DIMENSION_BLOCK.get()
            ).build(null));

    public static final Supplier<BlockEntityType<LuckyPortalBlockEntity>> LUCKY_PORTAL_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("lucky_portal_block_entity",
            () -> BlockEntityType.Builder.of(
                LuckyPortalBlockEntity::new,
                ModBlocks.LUCKY_PORTAL.get()
            ).build(null));

    public static final Supplier<BlockEntityType<JobFrostedIceBlockEntity>> JOB_FROSTED_ICE_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("job_frosted_ice_block_entity",
            () -> BlockEntityType.Builder.of(
                JobFrostedIceBlockEntity::new,
                ModBlocks.JOB_FROSTED_ICE.get()
            ).build(null));

    /**
     * 支援幸运方块：装着"操控送出"的生物/物品，出现 5 秒后自己碎裂把内容爆出来
     * （见 {@link SupportLuckyBlockEntity} 与 {@link cn.autoforged.joes_addons_for_abmc.block.SupportGift}）。
     */
    public static final Supplier<BlockEntityType<SupportLuckyBlockEntity>> SUPPORT_LUCKY_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register("support_lucky_block_entity",
            () -> BlockEntityType.Builder.of(
                SupportLuckyBlockEntity::new,
                ModBlocks.SUPPORT_LUCKY_BLOCK.get()
            ).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
