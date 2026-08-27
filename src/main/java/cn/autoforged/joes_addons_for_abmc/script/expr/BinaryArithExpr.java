package cn.autoforged.joes_addons_for_abmc.script.expr;

import cn.autoforged.joes_addons_for_abmc.script.ScriptValue;
import cn.autoforged.joes_addons_for_abmc.script.VariableScope;

/**
 * 二元算术表达式：+、-、*、/、%。
 * 加法：若两操作数均为数值则数值相加，否则做字符串拼接；其余运算按数值处理。
 */
public class BinaryArithExpr implements Expr {
    public enum Op {
        ADD, SUB, MUL, DIV, MOD
    }

    private final Expr left;
    private final Op op;
    private final Expr right;

    public BinaryArithExpr(Expr left, Op op, Expr right) {
        this.left = left;
        this.op = op;
        this.right = right;
    }

    @Override
    public ScriptValue eval(VariableScope scope) {
        ScriptValue a = left.eval(scope);
        ScriptValue b = right.eval(scope);

        if (op == Op.ADD) {
            boolean aNum = a.isNumber() || isNumericString(a);
            boolean bNum = b.isNumber() || isNumericString(b);
            if (aNum && bNum) {
                return ScriptValue.ofNumber(a.asNumber() + b.asNumber());
            }
            return ScriptValue.ofString(a.asString() + b.asString());
        }

        double x = a.asNumber();
        double y = b.asNumber();
        switch (op) {
            case SUB:
                return ScriptValue.ofNumber(x - y);
            case MUL:
                return ScriptValue.ofNumber(x * y);
            case DIV:
                return ScriptValue.ofNumber(y == 0 ? 0 : x / y);
            case MOD:
                return ScriptValue.ofNumber(y == 0 ? 0 : x % y);
            default:
                return ScriptValue.ofNumber(0);
        }
    }

    private static boolean isNumericString(ScriptValue v) {
        return v.isString() && !v.asString().isBlank();
    }
}