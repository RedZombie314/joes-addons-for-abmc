package cn.autoforged.joes_addons_for_abmc.script.expr;

import cn.autoforged.joes_addons_for_abmc.script.ScriptValue;
import cn.autoforged.joes_addons_for_abmc.script.VariableScope;

/** 字面量表达式：返回固定值。 */
public class LiteralExpr implements Expr {
    private final ScriptValue value;

    public LiteralExpr(ScriptValue value) {
        this.value = value;
    }

    @Override
    public ScriptValue eval(VariableScope scope) {
        return value;
    }
}