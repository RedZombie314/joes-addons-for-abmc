package cn.autoforged.joes_addons_for_abmc.script.cond;

import cn.autoforged.joes_addons_for_abmc.script.VariableScope;

/** 条件：在给定作用域下判断真/假。 */
public interface Condition {
    boolean test(VariableScope scope);
}