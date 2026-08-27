package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Map;
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
}
