package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 命令方块权杖能力模式同步（服务端→客户端）。模式编号：
 * 0=击杀模式，1=抓取模式，2=启用/禁用AI，3=护盾模式。
 * 客户端据此在屏幕正下方渲染当前能力，并在收到该包时开始 3 秒淡出计时。
 */
public record CommandStaffModePayload(int mode) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CommandStaffModePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "command_staff_mode"));

    public static final StreamCodec<FriendlyByteBuf, CommandStaffModePayload> STREAM_CODEC =
        CustomPacketPayload.codec(CommandStaffModePayload::write, CommandStaffModePayload::new);

    private CommandStaffModePayload(FriendlyByteBuf buf) {
        this(buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.mode);
    }

    @Override
    public Type<CommandStaffModePayload> type() {
        return TYPE;
    }
}
