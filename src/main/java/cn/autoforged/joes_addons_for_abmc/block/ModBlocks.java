package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ModMain.MODID);

    public static final DeferredBlock<LuckyDimensionBlock> LUCKY_DIMENSION_BLOCK =
        BLOCKS.register("lucky_dimension_block",
            () -> new LuckyDimensionBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.GOLD)
                .strength(1.5f, 6.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE)));

    public static final DeferredBlock<LuckyPortalBlock> LUCKY_PORTAL =
        BLOCKS.register("lucky_portal",
            () -> new LuckyPortalBlock(BlockBehaviour.Properties.of()
                .noOcclusion()
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .lightLevel(state -> 11)
                .sound(SoundType.GLASS)
                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

    public static final DeferredBlock<HorizontalDripstoneBlock> HORIZONTAL_DRIPSTONE =
        BLOCKS.register("horizontal_dripstone",
            () -> new HorizontalDripstoneBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(1.5F, 6.0F)
                .requiresCorrectToolForDrops()
                .noOcclusion()
                .sound(SoundType.POINTED_DRIPSTONE)));

    public static final DeferredBlock<NotePortalBlock> NOTE_PORTAL =
        BLOCKS.register("note_portal",
            () -> new NotePortalBlock(BlockBehaviour.Properties.of()
                .noCollission()
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .lightLevel(state -> 11)
                .sound(SoundType.GLASS)
                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

    // 冰块权杖生成的“霜冰”：½速融化、融化/破坏均还原原方块、破坏时结算被困生物伤害
    public static final DeferredBlock<JobFrostedIceBlock> JOB_FROSTED_ICE =
        BLOCKS.register("job_frosted_ice",
            () -> new JobFrostedIceBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.ICE)
                .friction(0.98F)
                .randomTicks()
                .strength(0.5F)
                .sound(SoundType.GLASS)
                // 不视为“令人窒息的阻塞方块”（与玻璃一致）：生物即使整个陷入其中也不会窒息；
                // 但仍保留完整碰撞箱，从而能真正困住生物。
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .noOcclusion()));

    static {
        ModItems.ITEMS.registerSimpleBlockItem(LUCKY_DIMENSION_BLOCK);
        ModItems.ITEMS.registerSimpleBlockItem(HORIZONTAL_DRIPSTONE);
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
