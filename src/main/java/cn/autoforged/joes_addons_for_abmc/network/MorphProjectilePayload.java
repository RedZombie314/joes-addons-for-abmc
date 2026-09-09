package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端→服务端：变形生物请求发射弹射物。无需携带数据，服务端直接调用 handleMorphProjectile。 */
public record MorphProjectilePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MorphProjectilePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "morph_projectile"));

    public static final StreamCodec<FriendlyByteBuf, MorphProjectilePayload> STREAM_CODEC =
        CustomPacketPayload.codec(MorphProjectilePayload::write, MorphProjectilePayload::new);

    private MorphProjectilePayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public Type<MorphProjectilePayload> type() {
        return TYPE;
    }
}