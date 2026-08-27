package cn.autoforged.joes_addons_for_abmc.script;

import cn.autoforged.joes_addons_for_abmc.script.expr.Expr;

/**
 * 值解析器：在给定运行环境与作用域下计算一个值。
 * <p>
 * 用于 {@link AssignNode} 把值写入临时/命名变量。纯表达式可直接委托
 * {@code expr.eval(scope)}；需要运行时上下文（如玩家、实体、物品）的值
 * 则借助 {@link RuntimeEnvironment} 求值。
 */
@FunctionalInterface
public interface ValueResolver {
    ScriptValue resolve(RuntimeEnvironment env, VariableScope scope);

    /** 由纯表达式构造解析器。 */
    static ValueResolver ofExpr(Expr expr) {
        return (env, scope) -> expr.eval(scope);
    }
}