package cn.autoforged.joes_addons_for_abmc.script.graph;

/**
 * 命令节点：执行一条命令。
 * <p>
 * 命令模板支持两类占位符：
 * <ul>
 *   <li>{@code $(变量名)}：引用脚本变量（沿用运行时 D12 语法）；</li>
 *   <li>{@code @引脚名}：引用命名输入引脚的绑定值（复杂值节点或简单内嵌式）。</li>
 * </ul>
 */
public class CommandGraphNode extends ScriptGraphNode {
    private String template = "";

    public CommandGraphNode() {
        super(GraphNodeType.COMMAND);
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }
}