package cn.autoforged.joes_addons_for_abmc.script.graph.serialize;

import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraph;
import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphCondition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;

/**
 * 程序图的 JSON 编解码入口（A2）。
 * <p>
 * 使用带有多态适配器（{@link GraphNodeJson}、{@link GraphConditionJson}）的 Gson，
 * 将 {@link ScriptGraph} 序列化为带 schema 版本号的结构化 JSON，并可反序列化还原。
 * <p>
 * 版本化：读取时若 {@code schemaVersion} 低于当前版本，走增量迁移（目前仅 v1）。
 */
public final class ScriptGraphCodec {

    /** 当前 schema 版本号。结构发生不兼容变更时递增，并实现对应迁移。 */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final Type ENVELOPE_TYPE = new TypeToken<ScriptGraphEnvelope>() {
    }.getType();

    private static final Type COLLECTION_ENVELOPE_TYPE = new TypeToken<GraphCollectionEnvelope>() {
    }.getType();

    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(ScriptGraphNode.class, new GraphNodeJson())
        .registerTypeAdapter(GraphCondition.class, new GraphConditionJson())
        .setPrettyPrinting()
        .create();

    private ScriptGraphCodec() {
    }

    /** 将程序图序列化为 JSON 字符串。 */
    public static String toJson(ScriptGraph graph) {
        return GSON.toJson(new ScriptGraphEnvelope(CURRENT_SCHEMA_VERSION, graph), ENVELOPE_TYPE);
    }

    /** 从 JSON 字符串反序列化程序图；若内容为空或无法解析返回 null。 */
    public static ScriptGraph fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        ScriptGraphEnvelope envelope = GSON.fromJson(json, ENVELOPE_TYPE);
        if (envelope == null || envelope.getGraph() == null) {
            return null;
        }
        return migrate(envelope.getSchemaVersion(), envelope.getGraph());
    }

    /** 将一批程序图序列化为 JSON 字符串（B4 集合持久化）。 */
    public static String toJsonCollection(java.util.List<ScriptGraph> graphs) {
        return GSON.toJson(new GraphCollectionEnvelope(CURRENT_SCHEMA_VERSION, graphs), COLLECTION_ENVELOPE_TYPE);
    }

    /** 从 JSON 字符串反序列化一批程序图；为空或不可解析返回空列表。 */
    public static java.util.List<ScriptGraph> fromJsonCollection(String json) {
        if (json == null || json.isBlank()) {
            return new java.util.ArrayList<>();
        }
        GraphCollectionEnvelope envelope = GSON.fromJson(json, COLLECTION_ENVELOPE_TYPE);
        if (envelope == null || envelope.getGraphs() == null) {
            return new java.util.ArrayList<>();
        }
        java.util.List<ScriptGraph> migrated = new java.util.ArrayList<>(envelope.getGraphs().size());
        for (ScriptGraph g : envelope.getGraphs()) {
            if (g != null) {
                migrated.add(migrate(envelope.getSchemaVersion(), g));
            }
        }
        return migrated;
    }

    /** 按 schema 版本执行增量迁移（不足当前版本时逐级升级）。 */
    private static ScriptGraph migrate(int schemaVersion, ScriptGraph graph) {
        int v = schemaVersion;
        while (v < CURRENT_SCHEMA_VERSION) {
            graph = migrateOne(v, graph);
            v++;
        }
        return graph;
    }

    /** 单步迁移：把版本 v 的图升级到 v+1。 */
    private static ScriptGraph migrateOne(int v, ScriptGraph graph) {
        // 目前仅 v1；后续结构变更在此按版本号添加迁移逻辑。
        return graph;
    }
}