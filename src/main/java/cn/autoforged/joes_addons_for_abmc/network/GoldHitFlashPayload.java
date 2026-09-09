package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：请求在玩家视野中央播放一段“金色命中闪光”后处理（金色四角星脉动闪光）。
 * 由 {@code /jafa gold_hit_flash} 命令触发。空载荷，仅作为触发信号。
 */
public record GoldHitFlashPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GoldHitFlashPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "gold_hit_flash"));

    public static final StreamCodec<FriendlyByteBuf, GoldHitFlashPayload> STREAM_CODEC =
        CustomPacketPayload.codec(GoldHitFlashPayload::write, GoldHitFlashPayload::new);

    private GoldHitFlashPayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public Type<GoldHitFlashPayload> type() {
        return TYPE;
    }
}