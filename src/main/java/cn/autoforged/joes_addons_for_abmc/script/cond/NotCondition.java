package cn.autoforged.joes_addons_for_abmc.script.cond;

import cn.autoforged.joes_addons_for_abmc.script.VariableScope;

/** 取反条件。 */
public class NotCondition implements Condition {
    private final Condition inner;

    public NotCondition(Condition inner) {
        this.inner = inner;
    }

    @Override
    public boolean test(VariableScope scope) {
        return !inner.test(scope);
    }
}