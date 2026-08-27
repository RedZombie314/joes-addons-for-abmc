package cn.autoforged.joes_addons_for_abmc.script.cond;

import cn.autoforged.joes_addons_for_abmc.script.VariableScope;

/** 逻辑与/或：组合两个条件。 */
public class LogicalCondition implements Condition {
    private final Condition left;
    private final boolean and;
    private final Condition right;

    public LogicalCondition(Condition left, boolean and, Condition right) {
        this.left = left;
        this.and = and;
        this.right = right;
    }

    @Override
    public boolean test(VariableScope scope) {
        boolean l = left.test(scope);
        if (and) {
            return l && right.test(scope);
        }
        return l || right.test(scope);
    }
}