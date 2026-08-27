package cn.autoforged.joes_addons_for_abmc.script;

import cn.autoforged.joes_addons_for_abmc.script.expr.Expr;

/**
 * 循环节点：将 bodyStart 起始的子链作为循环体，重复执行次数由 count 表达式
 * 在进入循环时求值决定。循环体结束后继续执行本节点的 next()。
 */
public class LoopNode extends ScriptNode {
    private final Expr countExpr;
    private final ScriptNode bodyStart;

    public LoopNode(Expr countExpr, ScriptNode bodyStart) {
        this.countExpr = countExpr;
        this.bodyStart = bodyStart;
    }

    /** 在给定作用域下求值循环次数。 */
    public int count(VariableScope scope) {
        return Math.max(0, (int) countExpr.eval(scope).asNumber());
    }

    public Expr countExpr() {
        return countExpr;
    }

    public ScriptNode bodyStart() {
        return bodyStart;
    }
}