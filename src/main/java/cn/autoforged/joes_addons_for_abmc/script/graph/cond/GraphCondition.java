package cn.autoforged.joes_addons_for_abmc.script.graph.cond;

/**
 * 内嵌条件树的抽象基类。条件由比较/逻辑/非等子节点组合（见设计决策），
 * 存储于 If 节点内部，不占用主图节点。
 */
public abstract class GraphCondition {

    public enum GraphConditionType {
        COMPARE,
        LOGICAL,
        NOT,
        BOOLEAN,
        TRUTHINESS
    }

    private final GraphConditionType type;

    protected GraphCondition(GraphConditionType type) {
        this.type = type;
    }

    public GraphConditionType getType() {
        return type;
    }
}