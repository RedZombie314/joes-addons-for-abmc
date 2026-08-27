package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：Omega 权杖在生存/冒险模式下尝试拆解被拒绝，
 * 客户端据此在屏幕中下方显示“既然装上了，就要为此负责……”。
 */
public record OmegaDismantlePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OmegaDismantlePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "omega_dismantle_forbidden"));

    public static final StreamCodec<FriendlyByteBuf, OmegaDismantlePayload> STREAM_CODEC =
        CustomPacketPayload.codec(OmegaDismantlePayload::write, OmegaDismantlePayload::new);

    private OmegaDismantlePayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public Type<OmegaDismantlePayload> type() {
        return TYPE;
    }
}
