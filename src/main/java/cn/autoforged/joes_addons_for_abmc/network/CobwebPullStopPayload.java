package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 蜘蛛网权杖（服务端→客户端）：停止拉扯，通知客户端清除蛛丝线段渲染。
 */
public record CobwebPullStopPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CobwebPullStopPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "cobweb_pull_stop"));

    public static final StreamCodec<FriendlyByteBuf, CobwebPullStopPayload> STREAM_CODEC =
        StreamCodec.unit(new CobwebPullStopPayload());

    @Override
    public Type<CobwebPullStopPayload> type() {
        return TYPE;
    }
}