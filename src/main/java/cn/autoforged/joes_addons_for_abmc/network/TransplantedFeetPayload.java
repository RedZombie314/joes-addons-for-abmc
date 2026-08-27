package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 移植脚（服务端→客户端）：通知客户端某实体身上带有一双“移植脚”，以及该脚的来源实体类型资源键。
 * 客户端据此用另一个生物腿部件替换本生物的腿部件。
 *
 * @param entityId   拥有移植脚的实体 id
 * @param feetTypeId 脚来源生物实体类型资源键（如 "minecraft:zombie"），非空
 */
public record TransplantedFeetPayload(int entityId, String feetTypeId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TransplantedFeetPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "transplanted_feet"));

    public static final StreamCodec<FriendlyByteBuf, TransplantedFeetPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, TransplantedFeetPayload::entityId,
            ByteBufCodecs.STRING_UTF8, TransplantedFeetPayload::feetTypeId,
            TransplantedFeetPayload::new);

    @Override
    public Type<TransplantedFeetPayload> type() {
        return TYPE;
    }
}