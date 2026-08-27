package cn.autoforged.joes_addons_for_abmc.worldgen;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.SurfaceRules;

import java.util.List;

public class ModNoiseSettings {
    public static final ResourceKey<NoiseGeneratorSettings> LUCKY_DIM = ResourceKey.create(
        Registries.NOISE_SETTINGS,
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "lucky_dim")
    );

    public static final ResourceKey<NoiseGeneratorSettings> PHYSICS_VOID = ResourceKey.create(
        Registries.NOISE_SETTINGS,
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "physics_void")
    );

    public static void bootstrap(BootstrapContext<NoiseGeneratorSettings> context) {
        context.register(LUCKY_DIM, new NoiseGeneratorSettings(
            NoiseSettings.create(-64, 384, 1, 2),
            ModBlocks.LUCKY_DIMENSION_BLOCK.get().defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            ExposedNoiseRouterData.overworld(
                context.lookup(Registries.DENSITY_FUNCTION),
                context.lookup(Registries.NOISE),
                false,
                false
            ),
            SurfaceRules.ifTrue(
                SurfaceRules.abovePreliminarySurface(),
                SurfaceRules.state(ModBlocks.LUCKY_DIMENSION_BLOCK.get().defaultBlockState())
            ),
            List.of(),
            0,
            false,
            false,
            false,
            false
        ));

        context.register(PHYSICS_VOID, new NoiseGeneratorSettings(
            NoiseSettings.create(-2032, 4064, 1, 2),
            Blocks.AIR.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            ExposedNoiseRouterData.overworld(
                context.lookup(Registries.DENSITY_FUNCTION),
                context.lookup(Registries.NOISE),
                false,
                false
            ),
            SurfaceRules.state(Blocks.AIR.defaultBlockState()),
            List.of(),
            0,
            false,
            false,
            false,
            false
        ));
    }
}
