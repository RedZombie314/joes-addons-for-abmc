package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PortalStaffInputPayload(int action, float amount) implements CustomPacketPayload {
    public static final int ACTION_START = 0;
    public static final int ACTION_FLIP = 1;
    public static final int ACTION_PLACE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_EXTEND = 4;
    public static final int ACTION_RETRACT = 5;
    public static final int ACTION_COLLAPSE = 6;

    public PortalStaffInputPayload(int action) {
        this(action, 0.0F);
    }

    public static final CustomPacketPayload.Type<PortalStaffInputPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "portal_staff_input"));

    public static final StreamCodec<FriendlyByteBuf, PortalStaffInputPayload> STREAM_CODEC =
        CustomPacketPayload.codec(PortalStaffInputPayload::write, PortalStaffInputPayload::new);

    private PortalStaffInputPayload(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readFloat());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.action);
        buf.writeFloat(this.amount);
    }

    @Override
    public Type<PortalStaffInputPayload> type() {
        return TYPE;
    }
}
