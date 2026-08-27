package cn.autoforged.joes_addons_for_abmc.script.graph;

/**
 * 函数调用节点：调用全局函数库中的某个自定义函数。
 * <p>
 * 实参通过命名输入引脚绑定（引脚名 = 形参名，继承自基类 {@code inputPins}）。
 */
public class FunctionCallGraphNode extends ScriptGraphNode {
    private String functionName;

    public FunctionCallGraphNode() {
        super(GraphNodeType.FUNCTION_CALL);
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }
}