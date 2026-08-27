package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StaffBlockTypePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StaffBlockTypePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "staff_blocktype"));

    public static final StreamCodec<FriendlyByteBuf, StaffBlockTypePayload> STREAM_CODEC =
        CustomPacketPayload.codec(StaffBlockTypePayload::write, StaffBlockTypePayload::new);

    private StaffBlockTypePayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public Type<StaffBlockTypePayload> type() {
        return TYPE;
    }
}
