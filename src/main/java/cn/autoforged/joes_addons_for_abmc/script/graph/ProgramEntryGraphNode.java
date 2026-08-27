package cn.autoforged.joes_addons_for_abmc.script.graph;

/**
 * 程序入口节点（当点击「运行」时）：作为图形程序的起点。
 * <p>
 * 编译时若图中存在本节点，则从本节点的输出端（nextId）开始执行，
 * 后续节点按图形逻辑继续运行。本身不执行任何命令。
 */
public class ProgramEntryGraphNode extends ScriptGraphNode {
    public ProgramEntryGraphNode() {
        super(GraphNodeType.PROGRAM_ENTRY);
    }
}