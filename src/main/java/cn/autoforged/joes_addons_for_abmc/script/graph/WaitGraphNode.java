package cn.autoforged.joes_addons_for_abmc.script.graph;

/**
 * 延迟节点：暂停指定刻数（游戏 tick）后再继续。
 * 刻数可为内嵌表达式或值节点引用（求值取整）。
 */
public class WaitGraphNode extends ScriptGraphNode {
    private GraphValue ticks;

    public WaitGraphNode() {
        super(GraphNodeType.WAIT);
    }

    public GraphValue getTicks() {
        return ticks;
    }

    public void setTicks(GraphValue ticks) {
        this.ticks = ticks;
    }
}