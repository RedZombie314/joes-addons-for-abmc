package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 变形状态（服务端→客户端）：玩家被变形药水变成物品/下落方块/生物壳/玩家空壳时，
 * 告知客户端“玩家正处于变形状态”，并给出被变成实体的实体ID；
 * 复原/终止时再通知解除。客户端据此：
 * 1) 每 tick 把被变成实体贴到本地玩家脚下（消除服务端 20Hz 快照造成的延迟/瞬移感）；
 * 2) 彻底隐藏变形玩家自身的渲染（含手持物与穿戴装备），实现完全隐身。
 *
 * @param transmuted     true=正处于变形，false=变形已结束
 * @param followEntityId 被变成实体的实体ID（transmuted=false 时为 -1）
 */
public record TransmutationStatePayload(boolean transmuted, int followEntityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TransmutationStatePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "transmutation_state"));

    public static final StreamCodec<FriendlyByteBuf, TransmutationStatePayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.BOOL, TransmutationStatePayload::transmuted,
            ByteBufCodecs.VAR_INT, TransmutationStatePayload::followEntityId,
            TransmutationStatePayload::new);

    @Override
    public Type<TransmutationStatePayload> type() {
        return TYPE;
    }
}
