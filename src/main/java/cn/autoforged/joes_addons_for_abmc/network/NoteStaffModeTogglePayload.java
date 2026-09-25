package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 音符盒权杖「音符/音谱」模式切换请求（客户端→服务端），由持有权杖时按住左Alt 滚动滚轮触发。
 * {@code direction} 为 -1 向前、+1 向后；服务端循环模式值（0=音符，1=音谱）并回传。
 */
public record NoteStaffModeTogglePayload(int direction) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<NoteStaffModeTogglePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "note_staff_mode_toggle"));

    public static final StreamCodec<FriendlyByteBuf, NoteStaffModeTogglePayload> STREAM_CODEC =
        CustomPacketPayload.codec(NoteStaffModeTogglePayload::write, NoteStaffModeTogglePayload::new);

    private NoteStaffModeTogglePayload(FriendlyByteBuf buf) {
        this(buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.direction);
    }

    @Override
    public Type<NoteStaffModeTogglePayload> type() {
        return TYPE;
    }
}
