package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EnchantStaffModePayload(boolean crazy) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EnchantStaffModePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "enchant_staff_mode"));

    public static final StreamCodec<FriendlyByteBuf, EnchantStaffModePayload> STREAM_CODEC =
        CustomPacketPayload.codec(EnchantStaffModePayload::write, EnchantStaffModePayload::new);

    private EnchantStaffModePayload(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.crazy);
    }

    @Override
    public Type<EnchantStaffModePayload> type() {
        return TYPE;
    }
}