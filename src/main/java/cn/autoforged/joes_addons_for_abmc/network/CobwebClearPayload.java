package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 蜘蛛网权杖（服务端→客户端）：通知客户端某实体的权杖“无效化”状态解除
 * （自动过期或玩家中键解除），移除其蛛网覆盖层渲染。
 *
 * @param entityId 解除无效化权杖的实体 id
 */
public record CobwebClearPayload(int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CobwebClearPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "cobweb_clear"));

    public static final StreamCodec<FriendlyByteBuf, CobwebClearPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, CobwebClearPayload::entityId,
            CobwebClearPayload::new);

    @Override
    public Type<CobwebClearPayload> type() {
        return TYPE;
    }
}