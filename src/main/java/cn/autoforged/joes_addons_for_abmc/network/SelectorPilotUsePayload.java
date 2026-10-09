package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 旁观操控下的<b>右键</b>：客户端 → 服务端（每次点击一个包）。
 *
 * <h3>为什么需要一个单独的包</h3>
 * 需求：操控时右键 → 手里没东西就朝准星所指的<b>掉落物/生物</b>执行 seek、手里有东西就执行 send。
 * 而"手里有没有东西"只有服务端知道（带着的生物靠实体持久化数据的抓取标记判定，不过网），
 * 所以客户端只负责报告"我点了右键、准星上是谁"，<b>seek 还是 send 由服务端决定</b>。
 *
 * <h3>目标 id 只是"意图"，不是权限</h3>
 * 服务端会自己复核：那只实体必须在同一维度、还活着、在 {@code LuckySelectorEntity.PILOT_SEEK_REACH}
 * 格以内，生物还要过一遍 {@code canCapture}（别的选择器已经锁定、自己是乘客之类都会被拒）。
 * 所以客户端伪造一个远处的实体也不会有用。
 *
 * @param selectorId     正在被旁观的那只选择器的实体 id（服务端核对，防止错位）
 * @param targetEntityId 准星所指的实体 id；什么都没指到时是 {@link #NO_TARGET}
 */
public record SelectorPilotUsePayload(int selectorId, int targetEntityId) implements CustomPacketPayload {

    /** 准星上什么都没有。 */
    public static final int NO_TARGET = -1;

    public static final CustomPacketPayload.Type<SelectorPilotUsePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "selector_pilot_use"));

    public static final StreamCodec<FriendlyByteBuf, SelectorPilotUsePayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.selectorId);
                buf.writeVarInt(p.targetEntityId);
            },
            buf -> new SelectorPilotUsePayload(buf.readVarInt(), buf.readVarInt())
        );

    @Override
    public Type<SelectorPilotUsePayload> type() {
        return TYPE;
    }
}
