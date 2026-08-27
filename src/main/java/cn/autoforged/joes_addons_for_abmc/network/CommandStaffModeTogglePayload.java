package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 命令方块权杖能力切换请求（客户端→服务端）。
 * <p>携带滚轮方向：{@code direction} 为 -1 表示切换到前一个模式（向上滚动），
 * +1 表示切换到后一个模式（向下滚动）。参照红石块权杖的充能滚轮处理。</p>
 */
public record CommandStaffModeTogglePayload(int direction) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CommandStaffModeTogglePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "command_staff_mode_toggle"));

    public static final StreamCodec<FriendlyByteBuf, CommandStaffModeTogglePayload> STREAM_CODEC =
        CustomPacketPayload.codec(CommandStaffModeTogglePayload::write, CommandStaffModeTogglePayload::new);

    private CommandStaffModeTogglePayload(FriendlyByteBuf buf) {
        this(buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.direction);
    }

    @Override
    public Type<CommandStaffModeTogglePayload> type() {
        return TYPE;
    }
}