package cn.autoforged.joes_addons_for_abmc.script.expr;

import cn.autoforged.joes_addons_for_abmc.script.ScriptValue;
import cn.autoforged.joes_addons_for_abmc.script.VariableScope;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据操作表达式：在值层面执行数组/集合/转换运算（纯函数，无副作用）。
 * <p>
 * 支持目前图模型所需的核心读操作；可变操作（如向数组追加）以“返回新集合”
 * 的函数式语义表达。未实现的操作返回 null。
 */
public class DataOpExpr implements Expr {

    public enum Op {
        ARRAY_LENGTH,
        ARRAY_GET,
        ARRAY_APPEND,
        SET_CONTAINS,
        SET_ADD,
        TO_STRING,
        TO_NUMBER
    }

    private final Op op;
    private final List<Expr> args;

    public DataOpExpr(Op op, List<Expr> args) {
        this.op = op;
        this.args = args;
    }

    @Override
    public ScriptValue eval(VariableScope scope) {
        switch (op) {
            case ARRAY_LENGTH: {
                List<ScriptValue> list = arg(scope, 0).asList();
                return ScriptValue.ofNumber(list.size());
            }
            case ARRAY_GET: {
                List<ScriptValue> list = arg(scope, 0).asList();
                int index = (int) arg(scope, 1).asNumber();
                if (index >= 0 && index < list.size()) {
                    return list.get(index);
                }
                return ScriptValue.nullValue();
            }
            case ARRAY_APPEND: {
                List<ScriptValue> list = new ArrayList<>(arg(scope, 0).asList());
                list.add(arg(scope, 1).copy());
                return ScriptValue.ofArray(list);
            }
            case SET_CONTAINS: {
                ScriptValue needle = arg(scope, 1);
                for (ScriptValue e : arg(scope, 0).asList()) {
                    if (e.asString().equals(needle.asString())) {
                        return ScriptValue.ofNumber(1);
                    }
                }
                return ScriptValue.ofNumber(0);
            }
            case SET_ADD: {
                List<ScriptValue> set = new ArrayList<>(arg(scope, 0).asList());
                ScriptValue member = arg(scope, 1);
                boolean has = false;
                for (ScriptValue e : set) {
                    if (e.asString().equals(member.asString())) {
                        has = true;
                        break;
                    }
                }
                if (!has) {
                    set.add(member.copy());
                }
                return ScriptValue.ofArray(set);
            }
            case TO_STRING:
                return ScriptValue.ofString(arg(scope, 0).asString());
            case TO_NUMBER:
                return ScriptValue.ofNumber(arg(scope, 0).asNumber());
            default:
                return ScriptValue.nullValue();
        }
    }

    private ScriptValue arg(VariableScope scope, int index) {
        if (index < args.size()) {
            return args.get(index).eval(scope);
        }
        return ScriptValue.nullValue();
    }
}