package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 工作台帽子：玩家<b>松开右键</b>时发来的空包。
 * <p>服务端收到后取消该玩家所有"还在飞行中"的合成事件：已从物品栏取出的材料原样掉在触发点。
 */
public record CraftingHatCancelPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CraftingHatCancelPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "crafting_hat_cancel"));

    public static final StreamCodec<FriendlyByteBuf, CraftingHatCancelPayload> STREAM_CODEC =
        CustomPacketPayload.codec(CraftingHatCancelPayload::write, CraftingHatCancelPayload::new);

    private CraftingHatCancelPayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public Type<CraftingHatCancelPayload> type() {
        return TYPE;
    }
}
