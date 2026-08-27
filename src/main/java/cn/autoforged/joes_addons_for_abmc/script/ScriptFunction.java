package cn.autoforged.joes_addons_for_abmc.script;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 自定义函数定义：函数名、形参列表、函数体入口节点。
 * 函数体是一条可由调用节点执行的节点链。
 */
public class ScriptFunction {
    private final String name;
    private final List<String> parameters = new ArrayList<>();
    private ScriptNode bodyStart;

    public ScriptFunction(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public List<String> parameters() {
        return parameters;
    }

    public void addParameter(String name) {
        parameters.add(name);
    }

    public ScriptNode bodyStart() {
        return bodyStart;
    }

    public void setBodyStart(ScriptNode bodyStart) {
        this.bodyStart = bodyStart;
    }

    public static ScriptFunction of(String name, ScriptNode bodyStart, String... params) {
        ScriptFunction fn = new ScriptFunction(name);
        fn.setBodyStart(bodyStart);
        fn.parameters.addAll(Arrays.asList(params));
        return fn;
    }
}