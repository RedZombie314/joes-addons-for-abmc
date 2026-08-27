package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 移植头（服务端→客户端）：通知客户端某实体身上带有一个“移植头”，以及该头的来源实体类型资源键。
 * 客户端据此在世界空间渲染另一个生物的头。
 *
 * @param entityId   拥有移植头的实体 id
 * @param headTypeId 头来源生物实体类型资源键（如 "minecraft:zombie"），非空
 */
public record TransplantedHeadPayload(int entityId, String headTypeId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TransplantedHeadPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "transplanted_head"));

    public static final StreamCodec<FriendlyByteBuf, TransplantedHeadPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, TransplantedHeadPayload::entityId,
            ByteBufCodecs.STRING_UTF8, TransplantedHeadPayload::headTypeId,
            TransplantedHeadPayload::new);

    @Override
    public Type<TransplantedHeadPayload> type() {
        return TYPE;
    }
}