package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：玩家主手和副手都持有 minecraft game icon 时按下中键，
 * 请求将两者消耗并在主手合成为 omega game icon。
 */
public record GameIconCraftPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GameIconCraftPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "game_icon_craft"));

    public static final StreamCodec<FriendlyByteBuf, GameIconCraftPayload> STREAM_CODEC =
        CustomPacketPayload.codec(GameIconCraftPayload::write, GameIconCraftPayload::new);

    private GameIconCraftPayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public Type<GameIconCraftPayload> type() {
        return TYPE;
    }
}