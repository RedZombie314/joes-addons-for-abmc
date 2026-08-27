package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BlockingStatePayload(boolean blocking) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BlockingStatePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "blocking_state"));

    public static final StreamCodec<FriendlyByteBuf, BlockingStatePayload> STREAM_CODEC =
        CustomPacketPayload.codec(BlockingStatePayload::write, BlockingStatePayload::new);

    private BlockingStatePayload(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.blocking);
    }

    @Override
    public Type<BlockingStatePayload> type() {
        return TYPE;
    }
}
