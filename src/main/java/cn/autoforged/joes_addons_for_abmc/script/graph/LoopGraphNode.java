package cn.autoforged.joes_addons_for_abmc.script.graph;

/**
 * 循环节点：重复执行循环体 count 次，循环体结束后继续 next。
 * 循环次数既可为内嵌表达式，也可为值节点引用（求值取整）。
 */
public class LoopGraphNode extends ScriptGraphNode {
    private GraphValue count;
    private String bodyStartId;

    public LoopGraphNode() {
        super(GraphNodeType.LOOP);
    }

    public GraphValue getCount() {
        return count;
    }

    public void setCount(GraphValue count) {
        this.count = count;
    }

    public String getBodyStartId() {
        return bodyStartId;
    }

    public void setBodyStartId(String bodyStartId) {
        this.bodyStartId = bodyStartId;
    }
}