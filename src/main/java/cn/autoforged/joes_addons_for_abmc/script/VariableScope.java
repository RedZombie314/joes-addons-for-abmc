package cn.autoforged.joes_addons_for_abmc.script;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 变量作用域：支持父子链，实现全局/局部变量与函数局部作用域。
 * set() 遵循"就近更新"——若父作用域已存在同名变量则就地更新，否则在当前创建。
 */
public class VariableScope {
    private final VariableScope parent;
    private final Map<String, ScriptValue> variables = new LinkedHashMap<>();

    public VariableScope(VariableScope parent) {
        this.parent = parent;
    }

    public VariableScope getParent() {
        return parent;
    }

    /** 声明/覆盖当前作用域中的变量（不向上查找）。 */
    public void declare(String name, ScriptValue value) {
        variables.put(name, value);
    }

    /** 写入变量：就近更新，否则在当前新建。 */
    public void set(String name, ScriptValue value) {
        if (parent != null && parent.contains(name)) {
            parent.set(name, value);
            return;
        }
        variables.put(name, value);
    }

    public ScriptValue get(String name) {
        if (variables.containsKey(name)) {
            return variables.get(name);
        }
        if (parent != null) {
            return parent.get(name);
        }
        return ScriptValue.nullValue();
    }

    public boolean contains(String name) {
        if (variables.containsKey(name)) {
            return true;
        }
        return parent != null && parent.contains(name);
    }

    public void remove(String name) {
        if (variables.containsKey(name)) {
            variables.remove(name);
            return;
        }
        if (parent != null) {
            parent.remove(name);
        }
    }

    /** 当前作用域全量快照（不含父级）。 */
    public Map<String, ScriptValue> snapshot() {
        Map<String, ScriptValue> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ScriptValue> e : variables.entrySet()) {
            copy.put(e.getKey(), e.getValue().copy());
        }
        return copy;
    }
}