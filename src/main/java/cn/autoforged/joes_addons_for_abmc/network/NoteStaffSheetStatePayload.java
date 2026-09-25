package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 音符盒权杖「音谱」状态同步（服务端→客户端，广播给谱子所在维度的所有玩家）。
 * {@code active=false} 表示谱子已消失（移除对应渲染）；{@code active=true} 时携带 5 条线
 * 的端点（每线 6 个 float：起点/终点 xyz，共 30 个）与剩余存在刻数，客户端据此渲染白色粒子线。
 */
public record NoteStaffSheetStatePayload(boolean active, UUID owner, float[] segments, int remainingTicks)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<NoteStaffSheetStatePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "note_staff_sheet_state"));

    public static final StreamCodec<FriendlyByteBuf, NoteStaffSheetStatePayload> STREAM_CODEC =
        CustomPacketPayload.codec(NoteStaffSheetStatePayload::write, NoteStaffSheetStatePayload::new);

    private NoteStaffSheetStatePayload(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readUUID(), buf.readBoolean() ? readFloats(buf) : null, buf.readInt());
    }

    private static float[] readFloats(FriendlyByteBuf buf) {
        float[] arr = new float[30];
        for (int i = 0; i < 30; i++) {
            arr[i] = buf.readFloat();
        }
        return arr;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.active);
        buf.writeUUID(this.owner);
        boolean hasSegments = this.active && this.segments != null;
        buf.writeBoolean(hasSegments);
        if (hasSegments) {
            for (float f : this.segments) {
                buf.writeFloat(f);
            }
        }
        buf.writeInt(this.remainingTicks);
    }

    @Override
    public Type<NoteStaffSheetStatePayload> type() {
        return TYPE;
    }
}
