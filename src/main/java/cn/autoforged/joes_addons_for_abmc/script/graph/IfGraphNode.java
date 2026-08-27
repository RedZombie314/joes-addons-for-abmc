package cn.autoforged.joes_addons_for_abmc.script.graph;

import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphCondition;

/**
 * 条件分支节点：根据内嵌条件树真假跳转到 true 或 false 分支。
 */
public class IfGraphNode extends ScriptGraphNode {
    private GraphCondition condition;
    private String trueNextId;
    private String falseNextId;

    public IfGraphNode() {
        super(GraphNodeType.IF);
    }

    public GraphCondition getCondition() {
        return condition;
    }

    public void setCondition(GraphCondition condition) {
        this.condition = condition;
    }

    public String getTrueNextId() {
        return trueNextId;
    }

    public void setTrueNextId(String trueNextId) {
        this.trueNextId = trueNextId;
    }

    public String getFalseNextId() {
        return falseNextId;
    }

    public void setFalseNextId(String falseNextId) {
        this.falseNextId = falseNextId;
    }
}