package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CommandStaffSyncPayload(List<String> history, Map<String, List<String>> presets)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CommandStaffSyncPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "command_staff_sync"));

    public static final StreamCodec<FriendlyByteBuf, CommandStaffSyncPayload> STREAM_CODEC =
        CustomPacketPayload.codec(CommandStaffSyncPayload::write, CommandStaffSyncPayload::new);

    private CommandStaffSyncPayload(FriendlyByteBuf buf) {
        this(
            buf.readList(FriendlyByteBuf::readUtf),
            buf.readMap(
                FriendlyByteBuf::readUtf,
                b -> b.readList(FriendlyByteBuf::readUtf)
            )
        );
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeCollection(history, FriendlyByteBuf::writeUtf);
        buf.writeMap(presets, FriendlyByteBuf::writeUtf, (b, list) -> b.writeCollection(list, FriendlyByteBuf::writeUtf));
    }

    @Override
    public Type<CommandStaffSyncPayload> type() {
        return TYPE;
    }
}
