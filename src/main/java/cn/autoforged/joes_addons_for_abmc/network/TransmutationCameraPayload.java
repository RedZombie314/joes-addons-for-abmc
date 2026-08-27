package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 变形药水视角（服务端→客户端）：玩家被变形药水变成物品/方块/生物壳/玩家空壳时，服务端告知客户端
 * 强制切换到第三人称视角（并把初始俯仰设为斜向下45°，便于看到脚下的“自己”）；
 * 复原时再通知恢复第一人称。
 *
 * @param thirdPerson  true=强制第三人称，false=恢复第一人称
 * @param initialPitch 进入第三人称时应用到玩家身上的初始俯仰角（仅 thirdPerson=true 时生效）
 */
public record TransmutationCameraPayload(boolean thirdPerson, float initialPitch) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TransmutationCameraPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "transmutation_camera"));

    public static final StreamCodec<FriendlyByteBuf, TransmutationCameraPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.BOOL, TransmutationCameraPayload::thirdPerson,
            ByteBufCodecs.FLOAT, TransmutationCameraPayload::initialPitch,
            TransmutationCameraPayload::new);

    @Override
    public Type<TransmutationCameraPayload> type() {
        return TYPE;
    }
}