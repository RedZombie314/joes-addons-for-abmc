package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C 广播事件 payload（双向）：
 * <ul>
 *   <li>客户端→服务端：{@code actionType=TRIGGER} 触发某频道的广播（服务端运行时唤醒等待该频道的程序）；
 *       {@code SUBSCRIBE}/{@code UNSUBSCRIBE} 订阅/取消订阅该频道（以便接收服务端广播通知）；</li>
 *   <li>服务端→客户端：{@code actionType=NOTIFY} 广播事件通知（当订阅的频道被触发时推送）。</li>
 * </ul>
 */
public record ScriptBroadcastPayload(int actionType, String channel, String data)
        implements CustomPacketPayload {

    public static final int ACTION_TRIGGER = 0;
    public static final int ACTION_SUBSCRIBE = 1;
    public static final int ACTION_UNSUBSCRIBE = 2;
    public static final int ACTION_NOTIFY = 3;

    public static final CustomPacketPayload.Type<ScriptBroadcastPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "script_broadcast"));

    public static final StreamCodec<FriendlyByteBuf, ScriptBroadcastPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ScriptBroadcastPayload::write, ScriptBroadcastPayload::new);

    private ScriptBroadcastPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(), buf.readUtf());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(actionType);
        buf.writeUtf(channel);
        buf.writeUtf(data);
    }

    public static ScriptBroadcastPayload trigger(String channel) {
        return new ScriptBroadcastPayload(ACTION_TRIGGER, channel, "");
    }

    public static ScriptBroadcastPayload subscribe(String channel) {
        return new ScriptBroadcastPayload(ACTION_SUBSCRIBE, channel, "");
    }

    public static ScriptBroadcastPayload unsubscribe(String channel) {
        return new ScriptBroadcastPayload(ACTION_UNSUBSCRIBE, channel, "");
    }

    public static ScriptBroadcastPayload notify(String channel, String data) {
        return new ScriptBroadcastPayload(ACTION_NOTIFY, channel, data);
    }

    @Override
    public Type<ScriptBroadcastPayload> type() {
        return TYPE;
    }
}