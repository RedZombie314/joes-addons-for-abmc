package cn.autoforged.joes_addons_for_abmc.script.expr;

import cn.autoforged.joes_addons_for_abmc.script.ScriptValue;
import cn.autoforged.joes_addons_for_abmc.script.VariableScope;

/** 一元表达式：取负。 */
public class UnaryArithExpr implements Expr {
    private final Expr inner;

    public UnaryArithExpr(Expr inner) {
        this.inner = inner;
    }

    @Override
    public ScriptValue eval(VariableScope scope) {
        return ScriptValue.ofNumber(-inner.eval(scope).asNumber());
    }
}