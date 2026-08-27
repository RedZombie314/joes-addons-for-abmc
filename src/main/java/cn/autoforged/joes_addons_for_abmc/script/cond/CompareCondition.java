package cn.autoforged.joes_addons_for_abmc.script.cond;

import cn.autoforged.joes_addons_for_abmc.script.ScriptValue;
import cn.autoforged.joes_addons_for_abmc.script.VariableScope;
import cn.autoforged.joes_addons_for_abmc.script.expr.Expr;

/**
 * 比较条件：比较左右两个表达式。两操作数均为数值（或数字字符串）时按数值比较，
 * 否则按字符串比较（==/!= 用 equals，序比较用字典序）。
 */
public class CompareCondition implements Condition {
    private final Expr left;
    private final CompareOp op;
    private final Expr right;

    public CompareCondition(Expr left, CompareOp op, Expr right) {
        this.left = left;
        this.op = op;
        this.right = right;
    }

    @Override
    public boolean test(VariableScope scope) {
        ScriptValue a = left.eval(scope);
        ScriptValue b = right.eval(scope);
        return compare(a, op, b);
    }

    public static boolean compare(ScriptValue a, CompareOp op, ScriptValue b) {
        boolean aNum = isNumeric(a);
        boolean bNum = isNumeric(b);
        if (aNum && bNum) {
            double x = a.asNumber();
            double y = b.asNumber();
            switch (op) {
                case EQ: return x == y;
                case NE: return x != y;
                case GT: return x > y;
                case LT: return x < y;
                case GE: return x >= y;
                case LE: return x <= y;
            }
        } else {
            String x = a.asString();
            String y = b.asString();
            switch (op) {
                case EQ: return x.equals(y);
                case NE: return !x.equals(y);
                case GT: return x.compareTo(y) > 0;
                case LT: return x.compareTo(y) < 0;
                case GE: return x.compareTo(y) >= 0;
                case LE: return x.compareTo(y) <= 0;
            }
        }
        return false;
    }

    private static boolean isNumeric(ScriptValue v) {
        if (v.isNumber()) return true;
        if (v.isString()) {
            String s = v.asString().trim();
            if (s.isEmpty()) return false;
            try {
                Double.parseDouble(s);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }
}