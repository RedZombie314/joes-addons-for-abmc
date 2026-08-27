package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 屏障权杖：左右键同时按下时触发“整体平移屏障群”的操作信号（客户端→服务端）。
 * 空载荷，仅作为触发信号。
 */
public record BarrierShiftPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BarrierShiftPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "barrier_shift"));

    public static final StreamCodec<FriendlyByteBuf, BarrierShiftPayload> STREAM_CODEC =
        CustomPacketPayload.codec(BarrierShiftPayload::write, BarrierShiftPayload::new);

    private BarrierShiftPayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public Type<BarrierShiftPayload> type() {
        return TYPE;
    }
}