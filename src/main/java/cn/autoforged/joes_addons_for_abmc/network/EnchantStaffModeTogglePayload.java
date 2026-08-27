package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EnchantStaffModeTogglePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EnchantStaffModeTogglePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "enchant_staff_toggle"));

    public static final StreamCodec<FriendlyByteBuf, EnchantStaffModeTogglePayload> STREAM_CODEC =
        StreamCodec.unit(new EnchantStaffModeTogglePayload());

    @Override
    public Type<EnchantStaffModeTogglePayload> type() {
        return TYPE;
    }
}