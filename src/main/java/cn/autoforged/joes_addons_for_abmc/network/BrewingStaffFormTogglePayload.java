package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 酿造台权杖「药瓶/药水云」模式切换请求（客户端→服务端），由持有权杖时按下左键触发。
 * 无载荷；服务端收到后翻转 form 值（0=药瓶 ↔ 1=药水云）并回传合并后的模式。
 */
public record BrewingStaffFormTogglePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BrewingStaffFormTogglePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "brewing_staff_form_toggle"));

    public static final StreamCodec<FriendlyByteBuf, BrewingStaffFormTogglePayload> STREAM_CODEC =
        StreamCodec.unit(new BrewingStaffFormTogglePayload());

    @Override
    public Type<BrewingStaffFormTogglePayload> type() {
        return TYPE;
    }
}