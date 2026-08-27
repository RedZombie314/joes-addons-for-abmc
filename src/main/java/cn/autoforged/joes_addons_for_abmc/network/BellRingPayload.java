package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BellRingPayload(int entityId, float pitch) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BellRingPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "bell_ring"));

    public static final StreamCodec<FriendlyByteBuf, BellRingPayload> STREAM_CODEC =
        CustomPacketPayload.codec(BellRingPayload::write, BellRingPayload::new);

    private BellRingPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readFloat());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeFloat(pitch);
    }

    @Override
    public Type<BellRingPayload> type() {
        return TYPE;
    }
}
