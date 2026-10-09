package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 工作台帽子：客户端"戴着帽子 + 主手为空 + 按下右键"时发来的空包。
 * <p>服务端收到后由 {@code CraftingTableHatItem#handleUseRequest} 自行做射线判定三种情况，
 * 所以包里不需要携带任何数据（也避免信任客户端的命中结果）。
 */
public record CraftingHatUsePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CraftingHatUsePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "crafting_hat_use"));

    public static final StreamCodec<FriendlyByteBuf, CraftingHatUsePayload> STREAM_CODEC =
        CustomPacketPayload.codec(CraftingHatUsePayload::write, CraftingHatUsePayload::new);

    private CraftingHatUsePayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public Type<CraftingHatUsePayload> type() {
        return TYPE;
    }
}
