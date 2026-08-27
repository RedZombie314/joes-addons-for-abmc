package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 铁块权杖（客户端→服务端）：玩家在拉取中途按下左键，请求中断当前钩取。
 * 服务端断开铁链，目标（生物/物品）以当前速度惯性甩出。
 */
public record ChainCancelPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ChainCancelPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "chain_cancel"));

    public static final StreamCodec<FriendlyByteBuf, ChainCancelPayload> STREAM_CODEC =
        StreamCodec.unit(new ChainCancelPayload());

    @Override
    public Type<ChainCancelPayload> type() {
        return TYPE;
    }
}
