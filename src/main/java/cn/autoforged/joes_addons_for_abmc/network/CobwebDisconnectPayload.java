package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 蜘蛛网权杖（客户端→服务端）：持有权杖按左键时，主动断开当前拉扯中的蛛丝。
 * 空载荷，仅作为触发信号。
 */
public record CobwebDisconnectPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CobwebDisconnectPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "cobweb_disconnect"));

    public static final StreamCodec<FriendlyByteBuf, CobwebDisconnectPayload> STREAM_CODEC =
        CustomPacketPayload.codec(CobwebDisconnectPayload::write, CobwebDisconnectPayload::new);

    private CobwebDisconnectPayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public Type<CobwebDisconnectPayload> type() {
        return TYPE;
    }
}