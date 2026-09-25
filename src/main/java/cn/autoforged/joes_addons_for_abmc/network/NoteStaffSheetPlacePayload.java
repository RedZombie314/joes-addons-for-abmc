package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 音符盒权杖「音谱模式」左键放置/重放谱子请求（客户端→服务端）。
 * 服务端沿权杖端点+玩家视线生成五线谱（5 条平行线，每条默认 50 格、遇非透明方块停止），
 * 并广播谱子状态给所有客户端渲染。
 */
public record NoteStaffSheetPlacePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<NoteStaffSheetPlacePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "note_staff_sheet_place"));

    public static final StreamCodec<FriendlyByteBuf, NoteStaffSheetPlacePayload> STREAM_CODEC =
        StreamCodec.unit(new NoteStaffSheetPlacePayload());

    @Override
    public Type<NoteStaffSheetPlacePayload> type() {
        return TYPE;
    }
}
