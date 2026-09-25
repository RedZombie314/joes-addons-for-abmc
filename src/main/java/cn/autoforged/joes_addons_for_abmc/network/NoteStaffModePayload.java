package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 音符盒权杖模式同步（服务端→客户端）：0=音符模式，1=音谱模式。
 * 客户端据此在屏幕中下方显示当前模式文本，并决定左键是否触发放置谱子。
 */
public record NoteStaffModePayload(int mode) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<NoteStaffModePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "note_staff_mode"));

    public static final StreamCodec<FriendlyByteBuf, NoteStaffModePayload> STREAM_CODEC =
        CustomPacketPayload.codec(NoteStaffModePayload::write, NoteStaffModePayload::new);

    private NoteStaffModePayload(FriendlyByteBuf buf) {
        this(buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.mode);
    }

    @Override
    public Type<NoteStaffModePayload> type() {
        return TYPE;
    }
}
