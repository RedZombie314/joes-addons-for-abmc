package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * TNT 权杖：持有权杖按下攻击键（左键）时，立即引爆该权杖丢出的所有 TNT/苦力怕（客户端→服务端）。
 * 空载荷，仅作为触发信号。
 */
public record TntDetonatePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TntDetonatePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "tnt_detonate"));

    public static final StreamCodec<FriendlyByteBuf, TntDetonatePayload> STREAM_CODEC =
        CustomPacketPayload.codec(TntDetonatePayload::write, TntDetonatePayload::new);

    private TntDetonatePayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public Type<TntDetonatePayload> type() {
        return TYPE;
    }
}