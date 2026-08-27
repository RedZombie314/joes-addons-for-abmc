package cn.autoforged.joes_addons_for_abmc.script.cond;

import cn.autoforged.joes_addons_for_abmc.script.VariableScope;
import cn.autoforged.joes_addons_for_abmc.script.expr.Expr;

/** 布尔值条件：把单个表达式按真值规则判断。 */
public class BooleanCondition implements Condition {
    private final Expr expr;

    public BooleanCondition(Expr expr) {
        this.expr = expr;
    }

    @Override
    public boolean test(VariableScope scope) {
        return Truthiness.isTruthy(expr.eval(scope));
    }
}