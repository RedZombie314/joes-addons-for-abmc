package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端在“长按右键发射红石射线时滚动鼠标滚轮”发送。
 * 携带调整后的充能强度（1~8），由服务端更新该玩家的红石权杖充能。
 */
public record RedstoneStaffChargePayload(int charge) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RedstoneStaffChargePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "redstone_staff_charge"));

    public static final StreamCodec<FriendlyByteBuf, RedstoneStaffChargePayload> STREAM_CODEC =
        CustomPacketPayload.codec(RedstoneStaffChargePayload::write, RedstoneStaffChargePayload::new);

    private RedstoneStaffChargePayload(FriendlyByteBuf buf) {
        this(buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.charge);
    }

    @Override
    public Type<RedstoneStaffChargePayload> type() {
        return TYPE;
    }
}
