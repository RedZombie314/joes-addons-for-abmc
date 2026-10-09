package cn.autoforged.joes_addons_for_abmc.worldgen;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.*;

public class ModBiomes {
    public static final ResourceKey<Biome> LUCKY_PLAINS = ResourceKey.create(
        Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "lucky_plains")
    );

    public static final ResourceKey<Biome> PHYSICS_VOID = ResourceKey.create(
        Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "physics_void")
    );

    public static void bootstrap(BootstrapContext<Biome> context) {
        BiomeGenerationSettings.PlainBuilder generationBuilder = new BiomeGenerationSettings.PlainBuilder();

        BiomeSpecialEffects specialEffects = new BiomeSpecialEffects.Builder()
            .fogColor(12638463)
            .waterColor(4159204)
            .waterFogColor(329011)
            .skyColor(7907327)
            .grassColorModifier(BiomeSpecialEffects.GrassColorModifier.NONE)
            .build();

        // 物理虚空维度用的刷怪设置（该维度需要正常刷怪，保持原样）
        MobSpawnSettings mobSpawnSettings = new MobSpawnSettings.Builder()
            .creatureGenerationProbability(0.1F)
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.PIG, 10, 4, 4))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.COW, 10, 4, 4))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.SHEEP, 10, 4, 4))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.CHICKEN, 10, 4, 4))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.VILLAGER, 5, 2, 4))
            // 铁傀儡：生成后在本维度不会主动攻击怪物（配合 AI 调整）
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.IRON_GOLEM, 2, 1, 1))
            // 亡灵/怪物：生成后都会被中立化；村民不再躲避它们，铁傀儡也不主动攻击
            .addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.ZOMBIE, 10, 4, 4))
            .addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.SKELETON, 10, 4, 4))
            .addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.SPIDER, 10, 4, 4))
            .build();

        // 幸运维度：**不生成任何生物**（需求）。所以这里用一张空的刷怪表，
        // 配合 NoiseGeneratorSettings 的 disableMobGeneration = true（见 ModNoiseSettings）。
        // 注意：早期注释里写的"怪物会被中立化 / 铁傀儡不主动攻击"其实只对音符盒维度生效，
        // 本维度没有任何对应实现 —— 这里直接不刷怪。
        MobSpawnSettings noSpawns = new MobSpawnSettings.Builder().build();

        context.register(LUCKY_PLAINS, new Biome.BiomeBuilder()
            .hasPrecipitation(true)
            .temperature(0.8F)
            .downfall(0.4F)
            .specialEffects(specialEffects)
            .mobSpawnSettings(noSpawns)
            .generationSettings(generationBuilder.build())
            .build());

        context.register(PHYSICS_VOID, new Biome.BiomeBuilder()
            .hasPrecipitation(false)
            .temperature(0.5F)
            .downfall(0.0F)
            .specialEffects(new BiomeSpecialEffects.Builder()
                .fogColor(0)
                .waterColor(4159204)
                .waterFogColor(329011)
                .skyColor(0)
                .grassColorModifier(BiomeSpecialEffects.GrassColorModifier.NONE)
                .build())
            .mobSpawnSettings(mobSpawnSettings)
            .generationSettings(new BiomeGenerationSettings.PlainBuilder().build())
            .build());
    }
}
