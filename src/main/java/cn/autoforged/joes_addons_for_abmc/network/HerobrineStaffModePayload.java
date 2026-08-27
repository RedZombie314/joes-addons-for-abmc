package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Him 权杖模式同步（服务端→客户端）：true=远程模式，false=近战模式。
 * 用于客户端保存当前模式（例如提示下一次右键的行为），供 {@code StaffClientState} 使用。
 */
public record HerobrineStaffModePayload(boolean ranged) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HerobrineStaffModePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "herobrine_staff_mode"));

    public static final StreamCodec<FriendlyByteBuf, HerobrineStaffModePayload> STREAM_CODEC =
        CustomPacketPayload.codec(HerobrineStaffModePayload::write, HerobrineStaffModePayload::new);

    private HerobrineStaffModePayload(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.ranged);
    }

    @Override
    public Type<HerobrineStaffModePayload> type() {
        return TYPE;
    }
}