package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C 运行指令 payload（客户端→服务端）：运行 / 停止某个图形化程序。
 * graphJson 为可选的程序快照：非空时服务端直接运行该图，无需先从程序库读取。
 */
public record ScriptRunPayload(int actionType, String programId, String graphJson) implements CustomPacketPayload {

    public static final int ACTION_RUN = 0;
    public static final int ACTION_STOP = 1;

    public static final CustomPacketPayload.Type<ScriptRunPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "script_run"));

    public static final StreamCodec<FriendlyByteBuf, ScriptRunPayload> STREAM_CODEC =
        CustomPacketPayload.codec(ScriptRunPayload::write, ScriptRunPayload::new);

    private ScriptRunPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(), buf.readUtf());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(actionType);
        buf.writeUtf(programId);
        buf.writeUtf(graphJson);
    }

    public static ScriptRunPayload run(String programId) {
        return new ScriptRunPayload(ACTION_RUN, programId, "");
    }

    /** 携带程序快照运行，服务端无需先从程序库读取，保证未保存也能运行最新编辑内容。 */
    public static ScriptRunPayload runWithGraph(String programId, String graphJson) {
        return new ScriptRunPayload(ACTION_RUN, programId, graphJson);
    }

    public static ScriptRunPayload stop(String programId) {
        return new ScriptRunPayload(ACTION_STOP, programId, "");
    }

    @Override
    public Type<ScriptRunPayload> type() {
        return TYPE;
    }
}