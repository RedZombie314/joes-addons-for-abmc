package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Him 权杖模式切换请求（客户端→服务端）：无载荷，触发服务端在“近战模式/远程模式”之间切换。
 */
public record HerobrineStaffModeTogglePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HerobrineStaffModeTogglePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "herobrine_staff_toggle"));

    public static final StreamCodec<FriendlyByteBuf, HerobrineStaffModeTogglePayload> STREAM_CODEC =
        StreamCodec.unit(new HerobrineStaffModeTogglePayload());

    @Override
    public Type<HerobrineStaffModeTogglePayload> type() {
        return TYPE;
    }
}