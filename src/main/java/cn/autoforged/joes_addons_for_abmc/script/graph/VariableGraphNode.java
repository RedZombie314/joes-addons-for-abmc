package cn.autoforged.joes_addons_for_abmc.script.graph;

/**
 * 变量操作节点：读取（GET）或写入（SET）某个变量。
 * <ul>
 *   <li>VAR_GET：输出引脚 {@code value} 返回变量当前值；</li>
 *   <li>VAR_SET：输入引脚 {@code value} 为待写入值，输出引脚 {@code value} 透传该值。</li>
 * </ul>
 */
public class VariableGraphNode extends ScriptGraphNode {

    public enum VarOpKind {
        GET, SET
    }

    private VarOpKind kind = VarOpKind.GET;
    private String varName;

    public VariableGraphNode() {
        super(GraphNodeType.VAR_GET);
    }

    public VarOpKind getKind() {
        return kind;
    }

    public void setKind(VarOpKind kind) {
        this.kind = kind;
        this.setType(kind == VarOpKind.SET ? GraphNodeType.VAR_SET : GraphNodeType.VAR_GET);
    }

    public String getVarName() {
        return varName;
    }

    public void setVarName(String varName) {
        this.varName = varName;
    }
}