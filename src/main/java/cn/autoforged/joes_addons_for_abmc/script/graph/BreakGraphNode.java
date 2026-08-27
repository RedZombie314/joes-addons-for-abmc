package cn.autoforged.joes_addons_for_abmc.script.graph;

/**
 * 跳出循环节点：跳出当前最内层循环。
 */
public class BreakGraphNode extends ScriptGraphNode {

    public BreakGraphNode() {
        super(GraphNodeType.BREAK);
    }
}