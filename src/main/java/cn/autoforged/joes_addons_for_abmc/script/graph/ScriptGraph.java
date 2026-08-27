package cn.autoforged.joes_addons_for_abmc.script.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个程序的节点图：一份节点集合 + 入口节点。
 * <p>
 * 节点以 id 为键存储；执行时从 {@code entryNodeId} 开始沿 {@code nextId}/分支连线推进。
 * 跨程序/跨世界复用的自定义函数存在独立全局函数库（B5），此处仅用函数名引用。
 * 当本图作为函数体时，{@code parameters} 声明该函数的形参名（B5 调用关系校验用）。
 */
public class ScriptGraph {
    private String id;
    private String name;
    private String entryNodeId;
    private List<String> parameters = new ArrayList<>();
    private Map<String, ScriptGraphNode> nodes = new LinkedHashMap<>();

    public ScriptGraph() {
    }

    public ScriptGraph(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEntryNodeId() {
        return entryNodeId;
    }

    public void setEntryNodeId(String entryNodeId) {
        this.entryNodeId = entryNodeId;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public void setParameters(List<String> parameters) {
        this.parameters = parameters != null ? new ArrayList<>(parameters) : new ArrayList<>();
    }

    /** 追加一个形参名。 */
    public void addParameter(String parameter) {
        if (parameter != null && !parameter.isBlank()) {
            parameters.add(parameter);
        }
    }

    public Map<String, ScriptGraphNode> getNodes() {
        return nodes;
    }

    public void addNode(ScriptGraphNode node) {
        nodes.put(node.getId(), node);
    }

    public ScriptGraphNode node(String id) {
        return nodes.get(id);
    }
}