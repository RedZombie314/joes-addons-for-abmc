package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record CommandStaffActionPayload(int actionType, String commandText, String presetName,
                                         List<String> presetCommands, String newPresetName)
        implements CustomPacketPayload {

    public static final int ACTION_EXECUTE = 0;
    public static final int ACTION_SAVE_PRESET = 1;
    public static final int ACTION_DELETE_PRESET = 2;
    public static final int ACTION_RENAME_PRESET = 3;
    public static final int ACTION_REQUEST_SYNC = 4;

    public static final CustomPacketPayload.Type<CommandStaffActionPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "command_staff_action"));

    public static final StreamCodec<FriendlyByteBuf, CommandStaffActionPayload> STREAM_CODEC =
        CustomPacketPayload.codec(CommandStaffActionPayload::write, CommandStaffActionPayload::new);

    private CommandStaffActionPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(), buf.readUtf(),
            buf.readList(FriendlyByteBuf::readUtf), buf.readUtf());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(actionType);
        buf.writeUtf(commandText);
        buf.writeUtf(presetName);
        buf.writeCollection(presetCommands, FriendlyByteBuf::writeUtf);
        buf.writeUtf(newPresetName);
    }

    public static CommandStaffActionPayload execute(String command) {
        return new CommandStaffActionPayload(ACTION_EXECUTE, command, "", List.of(), "");
    }

    public static CommandStaffActionPayload savePreset(String name, List<String> commands) {
        return new CommandStaffActionPayload(ACTION_SAVE_PRESET, "", name, commands, "");
    }

    public static CommandStaffActionPayload deletePreset(String name) {
        return new CommandStaffActionPayload(ACTION_DELETE_PRESET, "", name, List.of(), "");
    }

    public static CommandStaffActionPayload renamePreset(String oldName, String newName) {
        return new CommandStaffActionPayload(ACTION_RENAME_PRESET, "", oldName, List.of(), newName);
    }

    public static CommandStaffActionPayload requestSync() {
        return new CommandStaffActionPayload(ACTION_REQUEST_SYNC, "", "", List.of(), "");
    }

    @Override
    public Type<CommandStaffActionPayload> type() {
        return TYPE;
    }
}
