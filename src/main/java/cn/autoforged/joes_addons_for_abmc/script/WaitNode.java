package cn.autoforged.joes_addons_for_abmc.script;

import cn.autoforged.joes_addons_for_abmc.script.expr.Expr;

/**
 * 延迟节点：暂停指定游戏刻数后再继续执行。刻数由表达式在首次进入时求值。
 */
public class WaitNode extends ScriptNode {
    private final Expr ticksExpr;

    public WaitNode(Expr ticksExpr) {
        this.ticksExpr = ticksExpr;
    }

    /** 在给定作用域下求值刻数。 */
    public int ticks(VariableScope scope) {
        return Math.max(0, (int) ticksExpr.eval(scope).asNumber());
    }

    public Expr ticksExpr() {
        return ticksExpr;
    }
}