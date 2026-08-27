package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.script.RuntimeEnvironment;
import cn.autoforged.joes_addons_for_abmc.script.ScriptFunction;
import cn.autoforged.joes_addons_for_abmc.script.ScriptNode;
import cn.autoforged.joes_addons_for_abmc.script.ScriptScheduler;
import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraph;
import cn.autoforged.joes_addons_for_abmc.script.graph.compile.GraphCompiler;
import cn.autoforged.joes_addons_for_abmc.script.graph.serialize.ScriptGraphCodec;
import cn.autoforged.joes_addons_for_abmc.script.store.FunctionLibrary;
import cn.autoforged.joes_addons_for_abmc.script.store.GlobalFunctionStore;
import cn.autoforged.joes_addons_for_abmc.script.store.ProgramStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * C 脚本网络中枢：承载程序/函数/运行/广播四类 payload 的服务端处理逻辑，
 * 并统一管理 per-world 程序库（{@link ProgramStore}）的生命周期。
 * <p>
 * 由 {@code ModMain} 在服务器启动/停止时调用 {@link #initProgramStore}/{@link #onServerStop}，
 * 并在 {@code registerPayloads} 中把各 payload 的 handler 委托到本类。
 */
public final class ScriptNetworking {

    /** 广播订阅表：频道 -> 订阅该频道的玩家 UUID。 */
    private static final Map<String, Set<UUID>> subscriberByChannel = new ConcurrentHashMap<>();

    private static volatile ProgramStore programStore;

    private ScriptNetworking() {
    }

    // ---------- 生命周期 ----------

    /** 服务器启动：加载全局函数库，并按世界根目录初始化 per-world 程序库。 */
    public static void initProgramStore(Path worldRoot) {
        GlobalFunctionStore.getInstance().load();
        ProgramStore ps = new ProgramStore();
        ps.init(worldRoot);
        programStore = ps;
    }

    /** 服务器停止：保存程序库并清空订阅。 */
    public static void onServerStop() {
        if (programStore != null) {
            programStore.save();
            programStore = null;
        }
        subscriberByChannel.clear();
    }

    public static ProgramStore programStore() {
        return programStore;
    }

    // ---------- 程序数据 ----------

    public static void handleGraphAction(Player player, ScriptGraphPayload payload) {
        if (programStore == null) {
            return;
        }
        switch (payload.actionType()) {
            case ScriptGraphPayload.ACTION_SAVE: {
                ScriptGraph g = ScriptGraphCodec.fromJson(payload.graphJson());
                if (g != null && payload.programId() != null && !payload.programId().isBlank()) {
                    g.setId(payload.programId());
                    programStore.putProgram(g);
                }
                break;
            }
            case ScriptGraphPayload.ACTION_DELETE: {
                programStore.removeProgram(payload.programId());
                break;
            }
            case ScriptGraphPayload.ACTION_RENAME: {
                renameProgram(payload.programId(), payload.newId());
                break;
            }
            case ScriptGraphPayload.ACTION_REQUEST_SYNC:
            default:
                break;
        }
        syncProgramLibrary(player);
    }

    private static void renameProgram(String oldId, String newId) {
        if (oldId == null || newId == null || newId.isBlank() || oldId.equals(newId)) {
            return;
        }
        ScriptGraph g = programStore.getProgram(oldId);
        if (g == null || programStore.contains(newId)) {
            return;
        }
        programStore.removeProgram(oldId);
        g.setId(newId);
        programStore.putProgram(g);
    }

    /** 向某客户端发送当前程序库全量。 */
    private static void syncProgramLibrary(Player player) {
        if (player instanceof ServerPlayer sp && programStore != null) {
            String json = ScriptGraphCodec.toJsonCollection(new ArrayList<>(programStore.snapshot().values()));
            PacketDistributor.sendToPlayer(sp, new ScriptLibrarySyncPayload(json));
        }
    }

    // ---------- 函数数据 ----------

    public static void handleFunctionAction(Player player, ScriptFunctionPayload payload) {
        FunctionLibrary lib = FunctionLibrary.getInstance();
        List<String> errors;
        switch (payload.actionType()) {
            case ScriptFunctionPayload.ACTION_CREATE: {
                ScriptGraph g = ScriptGraphCodec.fromJson(payload.graphJson());
                errors = g != null ? lib.createFunction(payload.functionName(), g)
                                   : List.of("函数数据无法解析");
                break;
            }
            case ScriptFunctionPayload.ACTION_UPDATE: {
                ScriptGraph g = ScriptGraphCodec.fromJson(payload.graphJson());
                errors = g != null ? lib.updateFunction(payload.functionName(), g)
                                   : List.of("函数数据无法解析");
                break;
            }
            case ScriptFunctionPayload.ACTION_DELETE:
                errors = lib.deleteFunction(payload.functionName());
                break;
            case ScriptFunctionPayload.ACTION_RENAME:
                errors = lib.renameFunction(payload.functionName(), payload.newName());
                break;
            case ScriptFunctionPayload.ACTION_REQUEST_SYNC:
            default:
                errors = List.of();
                break;
        }
        if (!errors.isEmpty() && player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("函数操作失败: " + String.join("; ", errors)));
        }
        syncFunctionLibrary(player);
    }

    private static void syncFunctionLibrary(Player player) {
        if (player instanceof ServerPlayer sp) {
            String json = ScriptGraphCodec.toJsonCollection(new ArrayList<>(GlobalFunctionStore.getInstance().snapshot().values()));
            PacketDistributor.sendToPlayer(sp, new ScriptFunctionSyncPayload(json));
        }
    }

    // ---------- 运行指令 ----------

    public static void handleRunAction(Player player, ScriptRunPayload payload) {
        if (!(player instanceof ServerPlayer sp) || programStore == null) {
            return;
        }
        switch (payload.actionType()) {
            case ScriptRunPayload.ACTION_RUN -> runProgram(sp, payload.programId(), payload.graphJson());
            case ScriptRunPayload.ACTION_STOP -> ScriptScheduler.getInstance().stopAll();
            default -> {
            }
        }
    }

    private static void runProgram(ServerPlayer player, String programId, String graphJson) {
        ScriptGraph graph;
        if (graphJson != null && !graphJson.isBlank()) {
            // 客户端携带程序快照：直接运行最新编辑内容，不依赖是否已保存到程序库
            graph = ScriptGraphCodec.fromJson(graphJson);
            if (graph != null) {
                graph.setId(programId);
            }
        } else {
            graph = programStore.getProgram(programId);
        }
        if (graph == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        RuntimeEnvironment env = new RuntimeEnvironment(server);
        env.setCommandSource(player.createCommandSourceStack());
        Map<String, ScriptFunction> functions = compileAllFunctions();
        ScriptNode entry = new GraphCompiler(graph, functions).compileEntry();
        ScriptScheduler.getInstance().start(env, entry);
    }

    /** 把全局函数库编译为可执行函数表（先注册骨架再填 body，支持函数间相互调用）。 */
    private static Map<String, ScriptFunction> compileAllFunctions() {
        Map<String, ScriptGraph> lib = GlobalFunctionStore.getInstance().snapshot();
        Map<String, ScriptFunction> functions = new HashMap<>();
        for (Map.Entry<String, ScriptGraph> e : lib.entrySet()) {
            ScriptFunction fn = new ScriptFunction(e.getKey());
            for (String p : e.getValue().getParameters()) {
                fn.addParameter(p);
            }
            functions.put(e.getKey(), fn);
        }
        for (Map.Entry<String, ScriptGraph> e : lib.entrySet()) {
            ScriptFunction fn = functions.get(e.getKey());
            ScriptNode body = new GraphCompiler(e.getValue(), functions).compileFunctionBody();
            fn.setBodyStart(body);
        }
        return functions;
    }

    // ---------- 广播事件（双向） ----------

    public static void handleBroadcastFromClient(Player sender, ScriptBroadcastPayload payload) {
        switch (payload.actionType()) {
            case ScriptBroadcastPayload.ACTION_TRIGGER -> {
                ScriptScheduler.getInstance().postBroadcast(payload.channel());
                notifySubscribers(sender, payload.channel());
            }
            case ScriptBroadcastPayload.ACTION_SUBSCRIBE -> subscribe(sender, payload.channel());
            case ScriptBroadcastPayload.ACTION_UNSUBSCRIBE -> unsubscribe(sender, payload.channel());
            default -> {
            }
        }
    }

    /** 服务端→客户端：客户端收到广播事件通知（当前供 GUI 后续刷新使用）。 */
    public static void handleBroadcastToClient(ScriptBroadcastPayload payload) {
        // 客户端侧处理：图形界面的广播状态面板可在此刷新；暂无UI逻辑时为空操作。
    }

    /** 服务端→客户端：客户端收到程序库全量同步，暂存 JSON 供图形编辑器使用。 */
    public static void onClientLibrarySync(ScriptLibrarySyncPayload payload) {
        clientProgramsJson = payload.programsJson();
    }

    /** 服务端→客户端：客户端收到函数库全量同步，暂存 JSON 供图形编辑器使用。 */
    public static void onClientFunctionSync(ScriptFunctionSyncPayload payload) {
        clientFunctionsJson = payload.functionsJson();
    }

    /** 客户端缓存的程序库 JSON（E 图形编辑器读取）。 */
    public static volatile String clientProgramsJson = "";
    /** 客户端缓存的函数库 JSON（E 图形编辑器读取）。 */
    public static volatile String clientFunctionsJson = "";

    private static void subscribe(Player player, String channel) {
        if (channel == null || channel.isBlank()) {
            return;
        }
        subscriberByChannel.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(player.getUUID());
    }

    private static void unsubscribe(Player player, String channel) {
        if (channel == null) {
            return;
        }
        Set<UUID> subbers = subscriberByChannel.get(channel);
        if (subbers != null) {
            subbers.remove(player.getUUID());
        }
    }

    private static void notifySubscribers(Player sender, String channel) {
        if (channel == null) {
            return;
        }
        Set<UUID> subbers = subscriberByChannel.get(channel);
        if (subbers == null) {
            return;
        }
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return;
        }
        for (UUID id : subbers) {
            if (id.equals(sender.getUUID())) {
                continue;
            }
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null) {
                PacketDistributor.sendToPlayer(sp, ScriptBroadcastPayload.notify(channel, ""));
            }
        }
    }
}