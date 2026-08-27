package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * 蜘蛛网权杖（服务端→客户端）：开始拉扯，通知客户端蛛丝锚点坐标，
 * 客户端据此在“玩家→锚点”之间持续渲染蛛丝线段（起始位置跟随玩家）。
 *
 * @param anchor 蛛丝命中的方块锚点
 */
public record CobwebPullPayload(Vec3 anchor) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CobwebPullPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "cobweb_pull"));

    public static final StreamCodec<FriendlyByteBuf, CobwebPullPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                buf.writeDouble(payload.anchor().x);
                buf.writeDouble(payload.anchor().y);
                buf.writeDouble(payload.anchor().z);
            },
            buf -> new CobwebPullPayload(
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()))
        );

    @Override
    public Type<CobwebPullPayload> type() {
        return TYPE;
    }
}