package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 弹奏工具演奏请求（客户端 → 服务器）。
 * 只用于让"服务器把它转播给除演奏者以外的其他玩家"——演奏者本人由客户端本地即时播放（零延迟）。
 * 携带该键按下要播的半音（已含八度变调）。事件驱动：每次按键发一个。
 */
public record StrummingPlayPayload(int timbre, int[] semitones) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StrummingPlayPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "strumming_play"));

    public static final StreamCodec<FriendlyByteBuf, StrummingPlayPayload> STREAM_CODEC =
        CustomPacketPayload.codec(StrummingPlayPayload::write, StrummingPlayPayload::new);

    private StrummingPlayPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarIntArray());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.timbre);
        buf.writeVarIntArray(this.semitones);
    }

    @Override
    public Type<StrummingPlayPayload> type() {
        return TYPE;
    }
}