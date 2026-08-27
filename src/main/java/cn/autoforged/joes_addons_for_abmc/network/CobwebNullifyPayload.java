package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 蜘蛛网权杖（服务端→客户端）：通知客户端某实体的权杖被“无效化”，
 * 需要在其权杖上方渲染蛛网覆盖层。
 *
 * @param entityId 被无效化权杖的实体 id
 */
public record CobwebNullifyPayload(int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CobwebNullifyPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "cobweb_nullify"));

    public static final StreamCodec<FriendlyByteBuf, CobwebNullifyPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, CobwebNullifyPayload::entityId,
            CobwebNullifyPayload::new);

    @Override
    public Type<CobwebNullifyPayload> type() {
        return TYPE;
    }
}