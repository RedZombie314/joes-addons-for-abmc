package cn.autoforged.joes_addons_for_abmc.script.graph.serialize;

import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraph;

/**
 * 持久化包络：携带 schema 版本号的程序图。
 * <p>
 * 顶层结构为 {@code {"schemaVersion":1,"graph":{...}}}，便于跨版本迁移（A2 版本化）。
 */
public class ScriptGraphEnvelope {
    private int schemaVersion;
    private ScriptGraph graph;

    public ScriptGraphEnvelope() {
    }

    public ScriptGraphEnvelope(int schemaVersion, ScriptGraph graph) {
        this.schemaVersion = schemaVersion;
        this.graph = graph;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public ScriptGraph getGraph() {
        return graph;
    }

    public void setGraph(ScriptGraph graph) {
        this.graph = graph;
    }
}