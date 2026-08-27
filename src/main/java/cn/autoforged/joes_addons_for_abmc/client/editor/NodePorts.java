package cn.autoforged.joes_addons_for_abmc.client.editor;

import cn.autoforged.joes_addons_for_abmc.script.graph.CommandGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.DataOperationGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.EventGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.FunctionCallGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.IfGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.LoopGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.Pins;
import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.ValueSourceGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.VariableGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.WaitGraphNode;

import java.util.ArrayList;
import java.util.List;

/**
 * E 端口模型：为每种节点类型计算其左侧值输入引脚与右侧逻辑/值输出端口。
 * 供图形编辑器渲染端口、命中检测与连线使用。
 */
public final class NodePorts {

    /** 逻辑输出端口名（普通节点为 next）。 */
    public static List<String> logicOutputs(ScriptGraphNode n) {
        List<String> out = new ArrayList<>();
        switch (n.getType()) {
            case IF -> {
                out.add("true");
                out.add("false");
            }
            case LOOP -> {
                out.add("body");
                out.add("next");
            }
            default -> out.add("next");
        }
        return out;
    }

    /** 值输入引脚名列表（左侧端口）。 */
    public static List<String> valueInputs(ScriptGraphNode n) {
        List<String> pins = new ArrayList<>();
        switch (n.getType()) {
            case COMMAND -> {
                String template = ((CommandGraphNode) n).getTemplate();
                scanCommandPins(template, pins);
            }
            case VAR_SET -> pins.add(Pins.VALUE);
            case LOOP -> pins.add("count");
            case WAIT -> pins.add("ticks");
            case EVENT_SEND, EVENT_RECEIVE -> pins.add("channel");
            case ARRAY_OP, SET_OP, CONVERT -> addDataOpPins((DataOperationGraphNode) n, pins);
            case VALUE_SOURCE -> addValueSourcePins((ValueSourceGraphNode) n, pins);
            case FUNCTION_CALL -> {
                // 实参引脚由编辑器根据函数库形参动态补充；此处保留已绑定引脚
            }
            default -> {
            }
        }
        // 去重（避免重复名）
        List<String> unique = new ArrayList<>();
        for (String p : pins) {
            if (!unique.contains(p)) {
                unique.add(p);
            }
        }
        return unique;
    }

    /** 该节点是否产出值（右侧 value 输出端口）。 */
    public static boolean hasValueOutput(ScriptGraphNode n) {
        switch (n.getType()) {
            case VAR_GET, VAR_SET, VALUE_SOURCE, ARRAY_OP, SET_OP, CONVERT:
                return true;
            default:
                return false;
        }
    }

    /** 该节点是否可被作为逻辑连线的合法源头（即拥有逻辑输出）。 */
    public static boolean hasLogicOutput(ScriptGraphNode n) {
        return true;
    }

    /** Minecraft 实体选择器，不应被当作自定义 @引脚。 */
    private static final java.util.Set<String> SELECTORS =
        java.util.Set.of("p", "a", "e", "r", "s", "n");

    private static void scanCommandPins(String template, List<String> pins) {
        if (template == null) {
            return;
        }
        int i = 0;
        int len = template.length();
        while (i < len) {
            char c = template.charAt(i);
            if (c == '@' && i + 1 < len && Character.isLetterOrDigit(template.charAt(i + 1))) {
                int j = i + 1;
                while (j < len && Character.isLetterOrDigit(template.charAt(j))) {
                    j++;
                }
                String token = template.substring(i + 1, j);
                if (!SELECTORS.contains(token)) {
                    pins.add(token);
                }
                i = j;
            } else {
                i++;
            }
        }
    }

    private static void addDataOpPins(DataOperationGraphNode n, List<String> pins) {
        switch (n.getOpKind()) {
            case ARRAY_GET -> {
                pins.add(Pins.ARRAY);
                pins.add(Pins.INDEX);
            }
            case ARRAY_SET -> {
                pins.add(Pins.ARRAY);
                pins.add(Pins.INDEX);
                pins.add(Pins.ELEMENT);
            }
            case ARRAY_LENGTH -> pins.add(Pins.ARRAY);
            case ARRAY_APPEND -> {
                pins.add(Pins.ARRAY);
                pins.add(Pins.ELEMENT);
            }
            case SET_ADD, SET_REMOVE, SET_CONTAINS -> {
                pins.add(Pins.SET);
                pins.add(Pins.MEMBER);
            }
            default -> pins.add(Pins.SOURCE);
        }
    }

    private static void addValueSourcePins(ValueSourceGraphNode n, List<String> pins) {
        switch (n.getSourceKind()) {
            case ITEM_IN_SLOT -> pins.add(Pins.SLOT);
            case ITEM_NAMESPACE -> pins.add(Pins.ITEM);
            case ENTITY_UUID -> pins.add(Pins.ENTITY);
            case SELECTED_ENTITY -> pins.add(Pins.TARGET);
            default -> {
            }
        }
    }

    private NodePorts() {
    }
}