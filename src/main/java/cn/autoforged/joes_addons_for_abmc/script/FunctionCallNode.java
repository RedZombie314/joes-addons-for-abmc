package cn.autoforged.joes_addons_for_abmc.script;

import cn.autoforged.joes_addons_for_abmc.script.expr.Expr;

import java.util.ArrayList;
import java.util.List;

/**
 * 函数调用节点：以当前作用域求值实参，绑定到函数形参后进入函数体。
 * 该节点的 next() 作为函数返回后的续接点。
 */
public class FunctionCallNode extends ScriptNode {
    private final ScriptFunction function;
    private final List<Expr> arguments = new ArrayList<>();

    public FunctionCallNode(ScriptFunction function) {
        this.function = function;
    }

    public ScriptFunction function() {
        return function;
    }

    public List<Expr> arguments() {
        return arguments;
    }

    public void addArgument(Expr arg) {
        arguments.add(arg);
    }
}