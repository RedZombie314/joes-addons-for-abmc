package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 铁块权杖（服务端→客户端）：停止铁链钩取（目标已到达 / 被玩家松开 / 左键中断 / 目标消失），
 * 通知客户端清除铁链渲染。
 */
public record ChainGrabStopPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ChainGrabStopPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "chain_grab_stop"));

    public static final StreamCodec<FriendlyByteBuf, ChainGrabStopPayload> STREAM_CODEC =
        StreamCodec.unit(new ChainGrabStopPayload());

    @Override
    public Type<ChainGrabStopPayload> type() {
        return TYPE;
    }
}
