package cn.autoforged.joes_addons_for_abmc.script.graph.serialize;

import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * 多个程序图的持久化包络（B4）：顶层结构为 {@code {"schemaVersion":1,"graphs":[...]}}。
 * <p>
 * 用于 per-world 程序库与全局函数库的整批序列化，复用 {@link ScriptGraphCodec} 的多态适配器。
 */
public class GraphCollectionEnvelope {
    private int schemaVersion;
    private List<ScriptGraph> graphs;

    public GraphCollectionEnvelope() {
    }

    public GraphCollectionEnvelope(int schemaVersion, List<ScriptGraph> graphs) {
        this.schemaVersion = schemaVersion;
        this.graphs = graphs != null ? new ArrayList<>(graphs) : new ArrayList<>();
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public List<ScriptGraph> getGraphs() {
        return graphs;
    }

    public void setGraphs(List<ScriptGraph> graphs) {
        this.graphs = graphs;
    }
}