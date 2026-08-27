package cn.autoforged.joes_addons_for_abmc.worldgen;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

public class ModDimensions {
    public static final ResourceKey<DimensionType> LUCKY_DIM_TYPE = ResourceKey.create(
        Registries.DIMENSION_TYPE,
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "lucky_dim_type")
    );

    public static final ResourceKey<LevelStem> LUCKY_DIMENSION = ResourceKey.create(
        Registries.LEVEL_STEM,
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "lucky_dimension")
    );

    public static final ResourceKey<Level> LUCKY_DIM_LEVEL = ResourceKey.create(
        Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "lucky_dimension")
    );

    public static final ResourceKey<DimensionType> PHYSICS_DIM_TYPE = ResourceKey.create(
        Registries.DIMENSION_TYPE,
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "physics_dim_type")
    );

    public static final ResourceKey<LevelStem> PHYSICS_DIMENSION = ResourceKey.create(
        Registries.LEVEL_STEM,
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "physics_dimension")
    );

    public static final ResourceKey<Level> PHYSICS_DIM_LEVEL = ResourceKey.create(
        Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "physics_dimension")
    );

    public static final ResourceKey<DimensionType> NOTE_DIM_TYPE = ResourceKey.create(
        Registries.DIMENSION_TYPE,
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "note_dim_type")
    );

    public static final ResourceKey<LevelStem> NOTE_BLOCK_UNIVERSE = ResourceKey.create(
        Registries.LEVEL_STEM,
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "note_block_universe")
    );

    public static final ResourceKey<Level> NOTE_DIM_LEVEL = ResourceKey.create(
        Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "note_block_universe")
    );

    public static void bootstrapDimensionType(BootstrapContext<DimensionType> context) {
        context.register(LUCKY_DIM_TYPE, new DimensionType(
            OptionalLong.empty(),
            true,
            false,
            false,
            true,
            1.0,
            true,
            false,
            -64,
            384,
            384,
            net.minecraft.tags.BlockTags.INFINIBURN_OVERWORLD,
            BuiltinDimensionTypes.OVERWORLD_EFFECTS,
            0.0F,
            new DimensionType.MonsterSettings(false, false, net.minecraft.util.valueproviders.ConstantInt.of(0), 0)
        ));

        context.register(PHYSICS_DIM_TYPE, new DimensionType(
            OptionalLong.of(18000L),
            true,
            false,
            false,
            false,
            1.0,
            false,
            false,
            -2032,
            4064,
            2032,
            net.minecraft.tags.BlockTags.INFINIBURN_OVERWORLD,
            BuiltinDimensionTypes.OVERWORLD_EFFECTS,
            0.15F,
            new DimensionType.MonsterSettings(false, false, net.minecraft.util.valueproviders.ConstantInt.of(0), 0)
        ));

        // Note Block Universe：与主世界完全一致的地形，怪物照常生成（生成后会被本 mod 强制中立化）。
        context.register(NOTE_DIM_TYPE, new DimensionType(
            OptionalLong.empty(),
            true,
            false,
            false,
            true,
            1.0,
            true,
            false,
            -64,
            384,
            384,
            net.minecraft.tags.BlockTags.INFINIBURN_OVERWORLD,
            BuiltinDimensionTypes.OVERWORLD_EFFECTS,
            0.0F,
            new DimensionType.MonsterSettings(false, true, UniformInt.of(0, 7), 0)
        ));
    }

    public static void bootstrapLevelStem(BootstrapContext<LevelStem> context) {
        context.register(LUCKY_DIMENSION, new LevelStem(
            context.lookup(Registries.DIMENSION_TYPE).getOrThrow(LUCKY_DIM_TYPE),
            new NoiseBasedChunkGenerator(
                new FixedBiomeSource(context.lookup(Registries.BIOME).getOrThrow(ModBiomes.LUCKY_PLAINS)),
                context.lookup(Registries.NOISE_SETTINGS).getOrThrow(ModNoiseSettings.LUCKY_DIM)
            )
        ));

        Holder<Biome> physicsBiome = context.lookup(Registries.BIOME).getOrThrow(ModBiomes.PHYSICS_VOID);

        FlatLevelGeneratorSettings physicsFlatSettings = new FlatLevelGeneratorSettings(
            Optional.empty(),
            physicsBiome,
            List.of()
        );
        physicsFlatSettings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.AIR));
        physicsFlatSettings.updateLayers();

        context.register(PHYSICS_DIMENSION, new LevelStem(
            context.lookup(Registries.DIMENSION_TYPE).getOrThrow(PHYSICS_DIM_TYPE),
            new FlatLevelSource(physicsFlatSettings)
        ));

        // Note Block Universe：复用主世界的生物群系参数列表与噪声设置，因此地形与主世界完全一致；
        // 使用 NoteBlockChunkGenerator 在装饰阶段将生成的原木替换为音符盒。
        context.register(NOTE_BLOCK_UNIVERSE, new LevelStem(
            context.lookup(Registries.DIMENSION_TYPE).getOrThrow(NOTE_DIM_TYPE),
            new NoteBlockChunkGenerator(
                MultiNoiseBiomeSource.createFromPreset(
                    context.lookup(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)),
                context.lookup(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD)
            )
        ));
    }
}
