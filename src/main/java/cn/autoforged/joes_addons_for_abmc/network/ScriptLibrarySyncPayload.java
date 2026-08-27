package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C 程序库全量同步 payload（服务端→客户端）：
 * 携带当前存档全部程序图的 JSON（{@code programsJson}，由 ScriptGraphCodec.toJsonCollection 生成）。
 */
public record ScriptLibrarySyncPayload(String programsJson) implements CustomPacketPayload {

    public static final int MAX_JSON_LEN = 2_000_000;

    public static final CustomPacketPayload.Type<ScriptLibrarySyncPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "script_library_sync"));

    public static final StreamCodec<FriendlyByteBuf, ScriptLibrarySyncPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ScriptLibrarySyncPayload::write, ScriptLibrarySyncPayload::new);

    private ScriptLibrarySyncPayload(FriendlyByteBuf buf) {
        this(buf.readUtf(MAX_JSON_LEN));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(programsJson, MAX_JSON_LEN);
    }

    @Override
    public Type<ScriptLibrarySyncPayload> type() {
        return TYPE;
    }
}