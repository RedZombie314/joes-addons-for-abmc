package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ModMain.MODID);

    public static final Supplier<DataComponentType<String>> BLOCKTYPE =
        DATA_COMPONENTS.register("blocktype",
            () -> DataComponentType.<String>builder()
                .persistent(Codec.STRING)
                .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                .build());

    public static final Supplier<DataComponentType<Integer>> BLOCK_DAMAGE =
        DATA_COMPONENTS.register("block_damage",
            () -> DataComponentType.<Integer>builder()
                .persistent(Codec.INT)
                .networkSynchronized(ByteBufCodecs.INT)
                .build());

    public static final Supplier<DataComponentType<Map<String, Integer>>> BLOCK_DURABILITIES =
        DATA_COMPONENTS.register("block_durabilities",
            () -> DataComponentType.<Map<String, Integer>>builder()
                .persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
                .build());

    public static final Supplier<DataComponentType<String>> ITEM_TYPE =
        DATA_COMPONENTS.register("item_type",
            () -> DataComponentType.<String>builder()
                .persistent(Codec.STRING)
                .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                .build());

    // 传送药水模式：point=定点、directional=定向、random=随机
    public static final Supplier<DataComponentType<String>> TRANSPORT_MODE =
        DATA_COMPONENTS.register("transport_mode",
            () -> DataComponentType.<String>builder()
                .persistent(Codec.STRING)
                .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                .build());

    // 定点传送药水的目标坐标（世界坐标）
    public static final Supplier<DataComponentType<Vec3>> TARGET_POS =
        DATA_COMPONENTS.register("target_pos",
            () -> DataComponentType.<Vec3>builder()
                .persistent(Vec3.CODEC)
                .build());

    // 定点传送药水的目标实体 UUID（传送到该实体附近）
    public static final Supplier<DataComponentType<UUID>> TARGET_ENTITY_UUID =
        DATA_COMPONENTS.register("target_entity_uuid",
            () -> DataComponentType.<UUID>builder()
                .persistent(Codec.STRING.xmap(UUID::fromString, UUID::toString))
                .build());

    // 定向传送药水的前进格数（浮点，玩家投掷方向水平前进的距离）
    public static final Supplier<DataComponentType<Double>> TARGET_DIST =
        DATA_COMPONENTS.register("target_dist",
            () -> DataComponentType.<Double>builder()
                .persistent(Codec.DOUBLE)
                .build());
}
