package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C 程序数据 payload（客户端→服务端）：请求同步 / 保存 / 删除 / 重命名一个程序图。
 * <p>
 * 程序图以 {@code graphJson}（{@link cn.autoforged.joes_addons_for_abmc.script.graph.serialize.ScriptGraphCodec#toJson}）
 * 传输；因 JSON 体积较大，读写使用扩大的最大长度 {@link #MAX_JSON_LEN}。
 */
public record ScriptGraphPayload(int actionType, String programId, String graphJson, String newId)
        implements CustomPacketPayload {

    public static final int ACTION_REQUEST_SYNC = 0;
    public static final int ACTION_SAVE = 1;
    public static final int ACTION_DELETE = 2;
    public static final int ACTION_RENAME = 3;

    /** 程序图 JSON 允许的最大长度（UTF）字节字符数。 */
    public static final int MAX_JSON_LEN = 2_000_000;

    public static final CustomPacketPayload.Type<ScriptGraphPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "script_graph"));

    public static final StreamCodec<FriendlyByteBuf, ScriptGraphPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ScriptGraphPayload::write, ScriptGraphPayload::new);

    private ScriptGraphPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(), buf.readUtf(MAX_JSON_LEN), buf.readUtf());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(actionType);
        buf.writeUtf(programId);
        buf.writeUtf(graphJson, MAX_JSON_LEN);
        buf.writeUtf(newId);
    }

    public static ScriptGraphPayload requestSync() {
        return new ScriptGraphPayload(ACTION_REQUEST_SYNC, "", "", "");
    }

    public static ScriptGraphPayload save(String id, String json) {
        return new ScriptGraphPayload(ACTION_SAVE, id, json, "");
    }

    public static ScriptGraphPayload delete(String id) {
        return new ScriptGraphPayload(ACTION_DELETE, id, "", "");
    }

    public static ScriptGraphPayload rename(String id, String newId) {
        return new ScriptGraphPayload(ACTION_RENAME, id, "", newId);
    }

    @Override
    public Type<ScriptGraphPayload> type() {
        return TYPE;
    }
}