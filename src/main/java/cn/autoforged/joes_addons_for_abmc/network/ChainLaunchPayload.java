package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 铁块权杖（服务端→客户端）：未命中任何目标时，铁链射出后自动收回的短暂动画。
 * 客户端在“玩家发射点 → 末端”渲染数刻铁链后自动消失（无钩取效果）。
 *
 * @param ex 铁链末端 x
 * @param ey 铁链末端 y
 * @param ez 铁链末端 z
 */
public record ChainLaunchPayload(double ex, double ey, double ez) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ChainLaunchPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "chain_launch"));

    public static final StreamCodec<FriendlyByteBuf, ChainLaunchPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeDouble(p.ex);
                buf.writeDouble(p.ey);
                buf.writeDouble(p.ez);
            },
            buf -> new ChainLaunchPayload(buf.readDouble(), buf.readDouble(), buf.readDouble())
        );

    @Override
    public Type<ChainLaunchPayload> type() {
        return TYPE;
    }
}
