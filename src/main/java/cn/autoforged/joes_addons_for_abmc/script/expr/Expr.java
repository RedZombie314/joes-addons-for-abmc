package cn.autoforged.joes_addons_for_abmc.script.expr;

import cn.autoforged.joes_addons_for_abmc.script.ScriptValue;
import cn.autoforged.joes_addons_for_abmc.script.VariableScope;

/**
 * 值表达式：在给定变量作用域下求值，返回一个 ScriptValue。
 */
public interface Expr {
    ScriptValue eval(VariableScope scope);
}