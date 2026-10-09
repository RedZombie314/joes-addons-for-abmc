package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 蜂巢权杖：按下左 Alt 时的触发信号（客户端→服务端）。
 * 服务端每隔 10 刻释放权杖中一只友善蜜蜂（永不仇视持有本权杖的玩家）。
 * 空载荷，仅作为触发信号。
 */
public record BeeStaffReleaseOnePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BeeStaffReleaseOnePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "bee_staff_release_one"));

    public static final StreamCodec<FriendlyByteBuf, BeeStaffReleaseOnePayload> STREAM_CODEC =
        CustomPacketPayload.codec(BeeStaffReleaseOnePayload::write, BeeStaffReleaseOnePayload::new);

    private BeeStaffReleaseOnePayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public Type<BeeStaffReleaseOnePayload> type() {
        return TYPE;
    }
}