package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C 函数数据 payload（客户端→服务端）：请求同步 / 创建 / 更新 / 删除 / 重命名一个自定义函数。
 * <p>
 * 函数体为一张图，以 {@code graphJson}（ScriptGraphCodec.toJson）传输；函数名存于 {@code functionName}。
 */
public record ScriptFunctionPayload(int actionType, String functionName, String graphJson, String newName)
        implements CustomPacketPayload {

    public static final int ACTION_REQUEST_SYNC = 0;
    public static final int ACTION_CREATE = 1;
    public static final int ACTION_UPDATE = 2;
    public static final int ACTION_DELETE = 3;
    public static final int ACTION_RENAME = 4;

    public static final int MAX_JSON_LEN = 2_000_000;

    public static final CustomPacketPayload.Type<ScriptFunctionPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "script_function"));

    public static final StreamCodec<FriendlyByteBuf, ScriptFunctionPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ScriptFunctionPayload::write, ScriptFunctionPayload::new);

    private ScriptFunctionPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(), buf.readUtf(MAX_JSON_LEN), buf.readUtf());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(actionType);
        buf.writeUtf(functionName);
        buf.writeUtf(graphJson, MAX_JSON_LEN);
        buf.writeUtf(newName);
    }

    public static ScriptFunctionPayload requestSync() {
        return new ScriptFunctionPayload(ACTION_REQUEST_SYNC, "", "", "");
    }

    public static ScriptFunctionPayload create(String name, String json) {
        return new ScriptFunctionPayload(ACTION_CREATE, name, json, "");
    }

    public static ScriptFunctionPayload update(String name, String json) {
        return new ScriptFunctionPayload(ACTION_UPDATE, name, json, "");
    }

    public static ScriptFunctionPayload delete(String name) {
        return new ScriptFunctionPayload(ACTION_DELETE, name, "", "");
    }

    public static ScriptFunctionPayload rename(String oldName, String newName) {
        return new ScriptFunctionPayload(ACTION_RENAME, oldName, "", newName);
    }

    @Override
    public Type<ScriptFunctionPayload> type() {
        return TYPE;
    }
}