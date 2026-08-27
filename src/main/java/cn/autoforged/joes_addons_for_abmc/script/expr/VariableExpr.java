package cn.autoforged.joes_addons_for_abmc.script.expr;

import cn.autoforged.joes_addons_for_abmc.script.ScriptValue;
import cn.autoforged.joes_addons_for_abmc.script.VariableScope;

/** 变量引用表达式：从作用域读取变量。 */
public class VariableExpr implements Expr {
    private final String name;

    public VariableExpr(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public ScriptValue eval(VariableScope scope) {
        return scope.get(name);
    }
}