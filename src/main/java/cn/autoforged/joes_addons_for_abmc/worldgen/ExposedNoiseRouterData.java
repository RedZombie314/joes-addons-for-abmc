package cn.autoforged.joes_addons_for_abmc.worldgen;

import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class ExposedNoiseRouterData extends NoiseRouterData {
    public static NoiseRouter overworld(
            HolderGetter<DensityFunction> densityFunctions,
            HolderGetter<NormalNoise.NoiseParameters> noiseParameters,
            boolean large, boolean amplified) {
        return NoiseRouterData.overworld(densityFunctions, noiseParameters, large, amplified);
    }
}
