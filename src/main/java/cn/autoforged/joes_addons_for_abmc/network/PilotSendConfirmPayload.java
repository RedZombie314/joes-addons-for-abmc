package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 客户端 → 服务端：<b>在下拉列表里按空格确认了目标</b>（需求 6.5.21）。
 *
 * <p>收到它之后服务端才真正执行 send：把那件东西装进一个支援幸运方块、生成在目标玩家附近
 * （见 {@code cn.autoforged.joes_addons_for_abmc.block.SupportGift}）。
 *
 * <p>{@code target} 等于 {@link PilotSendListPayload#NO_PLAYER}（或服务端查不到这个玩家）时
 * <b>什么都不做</b>——那是 Technoblade / Dream 这两个调试项、以及"暂无需要支援的玩家"占位项的情况。
 *
 * <p>取消（Esc 关闭界面）不发任何包：服务端在此之前<b>什么都没做</b>，东西还在选择器手里，
 * 玩家可以再按一次右键重新打开列表。
 *
 * @param selectorId 那只被旁观的选择器
 * @param target     选中的玩家 UUID
 */
public record PilotSendConfirmPayload(int selectorId, UUID target) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PilotSendConfirmPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "pilot_send_confirm"));

    public static final StreamCodec<FriendlyByteBuf, PilotSendConfirmPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.selectorId);
                buf.writeUUID(p.target);
            },
            buf -> new PilotSendConfirmPayload(buf.readVarInt(), buf.readUUID())
        );

    @Override
    public Type<PilotSendConfirmPayload> type() {
        return TYPE;
    }
}
