package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 自体附魔（服务端→客户端）：通知客户端某实体被“自体附魔”（应为空手生物），需要在其材质上
 * 持续显示附魔光效。客户端据此记录/清除该实体的自附魔状态。
 *
 * @param entityId  被自体附魔的实体 id
 * @param enchanted true=赋予自附魔，false=清除（目前只会发 true）
 */
public record EnchantSelfPayload(int entityId, boolean enchanted) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EnchantSelfPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "enchant_self"));

    public static final StreamCodec<FriendlyByteBuf, EnchantSelfPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, EnchantSelfPayload::entityId,
            ByteBufCodecs.BOOL, EnchantSelfPayload::enchanted,
            EnchantSelfPayload::new);

    @Override
    public Type<EnchantSelfPayload> type() {
        return TYPE;
    }
}