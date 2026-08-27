package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C 函数库全量同步 payload（服务端→客户端）：
 * 携带全局函数库全部函数体 JSON（{@code functionsJson}，由 ScriptGraphCodec.toJsonCollection 生成）。
 */
public record ScriptFunctionSyncPayload(String functionsJson) implements CustomPacketPayload {

    public static final int MAX_JSON_LEN = 2_000_000;

    public static final CustomPacketPayload.Type<ScriptFunctionSyncPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "script_function_sync"));

    public static final StreamCodec<FriendlyByteBuf, ScriptFunctionSyncPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ScriptFunctionSyncPayload::write, ScriptFunctionSyncPayload::new);

    private ScriptFunctionSyncPayload(FriendlyByteBuf buf) {
        this(buf.readUtf(MAX_JSON_LEN));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(functionsJson, MAX_JSON_LEN);
    }

    @Override
    public Type<ScriptFunctionSyncPayload> type() {
        return TYPE;
    }
}