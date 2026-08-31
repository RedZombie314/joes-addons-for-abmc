package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 变形状态（服务端→客户端）：
 * - 物品/方块/玩家空壳/生物壳形态：告知客户端“玩家正处于变形状态”，并给出被变成实体的实体ID；
 * - 生物(morph)形态：给出目标生物实体类型 id，供客户端把玩家渲染成该生物（渲染替换），玩家本体保持操控。
 * - 复原/终止时再通知解除。
 *
 * @param transmuted     true=正处于变形，false=变形已结束
 * @param followEntityId 被变成实体的实体ID（transmuted=false 时为 -1）
 * @param morphEntityType 渲染替换要呈现的生物实体类型 id（如 "minecraft:creeper"）；非生物形态为空串
 */
public record TransmutationStatePayload(boolean transmuted, int followEntityId, String morphEntityType) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TransmutationStatePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "transmutation_state"));

    public static final StreamCodec<FriendlyByteBuf, TransmutationStatePayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.BOOL, TransmutationStatePayload::transmuted,
            ByteBufCodecs.VAR_INT, TransmutationStatePayload::followEntityId,
            ByteBufCodecs.STRING_UTF8, TransmutationStatePayload::morphEntityType,
            TransmutationStatePayload::new);

    @Override
    public Type<TransmutationStatePayload> type() {
        return TYPE;
    }
}