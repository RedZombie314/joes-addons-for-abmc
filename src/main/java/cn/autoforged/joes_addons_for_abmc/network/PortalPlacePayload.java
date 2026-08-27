package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端在“松开右键放置传送门”时发送。
 * 携带被放置传送门的目标坐标、朝向与翻转状态。
 */
public record PortalPlacePayload(boolean placingExit, double x, double y, double z,
                                 float yaw, float pitch, boolean flip) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PortalPlacePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "portal_place"));

    public static final StreamCodec<FriendlyByteBuf, PortalPlacePayload> STREAM_CODEC =
        CustomPacketPayload.codec(PortalPlacePayload::write, PortalPlacePayload::new);

    private PortalPlacePayload(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.placingExit);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeFloat(this.yaw);
        buf.writeFloat(this.pitch);
        buf.writeBoolean(this.flip);
    }

    @Override
    public Type<PortalPlacePayload> type() {
        return TYPE;
    }
}