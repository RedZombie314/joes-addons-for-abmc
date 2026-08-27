package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NoteMusicContextPayload(byte context) implements CustomPacketPayload {
    public static final byte CONTEXT_NONE = 0;
    public static final byte CONTEXT_VILLAGE = 1;
    public static final byte CONTEXT_UNDERWATER = 2;
    public static final byte CONTEXT_TRADER = 3;
    public static final byte CONTEXT_HIGH = 4;

    public static final CustomPacketPayload.Type<NoteMusicContextPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "note_music_context"));

    public static final StreamCodec<FriendlyByteBuf, NoteMusicContextPayload> STREAM_CODEC =
        CustomPacketPayload.codec(NoteMusicContextPayload::write, NoteMusicContextPayload::new);

    private NoteMusicContextPayload(FriendlyByteBuf buf) {
        this(buf.readByte());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeByte(context);
    }

    @Override
    public Type<NoteMusicContextPayload> type() {
        return TYPE;
    }
}