package cn.autoforged.joes_addons_for_abmc.script.graph.serialize;

import cn.autoforged.joes_addons_for_abmc.script.graph.BreakGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.CommandGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.DataOperationGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.EventGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.FunctionCallGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.GraphNodeType;
import cn.autoforged.joes_addons_for_abmc.script.graph.GraphValue;
import cn.autoforged.joes_addons_for_abmc.script.graph.IfGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.LoopGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.ProgramEntryGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.ValueSourceGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.VariableGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.WaitGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphCondition;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * 节点的手动多态 (反)序列化适配器。
 * <p>
 * 以 {@code type} 字段判别具体节点子类；公共字段（id/type/x/y/nextId/inputPins）
 * 统一处理，子类专属字段按类型分支处理。条件字段（GraphCondition）经
 * 序列化上下文（已注册条件适配器）递归处理，值字段（GraphValue）委托给
 * {@link GsonUtil#LEAF}。
 */
public class GraphNodeJson implements JsonSerializer<ScriptGraphNode>, JsonDeserializer<ScriptGraphNode> {

    @Override
    public JsonElement serialize(ScriptGraphNode node, Type type, JsonSerializationContext ctx) {
        JsonObject o = new JsonObject();
        o.addProperty("id", node.getId());
        o.addProperty("type", node.getType().name());
        o.addProperty("x", node.getX());
        o.addProperty("y", node.getY());
        if (node.getNextId() != null) {
            o.addProperty("nextId", node.getNextId());
        }
        if (!node.getInputPins().isEmpty()) {
            o.add("inputPins", serializePins(node.getInputPins()));
        }

        switch (node.getType()) {
            case COMMAND -> o.addProperty("template", ((CommandGraphNode) node).getTemplate());
            case IF -> {
                IfGraphNode n = (IfGraphNode) node;
                o.add("condition", ctx.serialize(n.getCondition(), GraphCondition.class));
                if (n.getTrueNextId() != null) {
                    o.addProperty("trueNextId", n.getTrueNextId());
                }
                if (n.getFalseNextId() != null) {
                    o.addProperty("falseNextId", n.getFalseNextId());
                }
            }
            case LOOP -> {
                LoopGraphNode n = (LoopGraphNode) node;
                o.add("count", GsonUtil.LEAF.toJsonTree(n.getCount(), GraphValue.class));
                if (n.getBodyStartId() != null) {
                    o.addProperty("bodyStartId", n.getBodyStartId());
                }
            }
            case WAIT -> {
                WaitGraphNode n = (WaitGraphNode) node;
                o.add("ticks", GsonUtil.LEAF.toJsonTree(n.getTicks(), GraphValue.class));
            }
            case BREAK -> {
            }
            case EVENT_SEND, EVENT_RECEIVE -> {
                EventGraphNode n = (EventGraphNode) node;
                o.addProperty("kind", n.getKind().name());
                o.add("channel", GsonUtil.LEAF.toJsonTree(n.getChannel(), GraphValue.class));
            }
            case FUNCTION_CALL -> {
                FunctionCallGraphNode n = (FunctionCallGraphNode) node;
                o.addProperty("functionName", n.getFunctionName());
            }
            case VAR_GET, VAR_SET -> {
                VariableGraphNode n = (VariableGraphNode) node;
                o.addProperty("kind", n.getKind().name());
                o.addProperty("varName", n.getVarName());
            }
            case ARRAY_OP, SET_OP, CONVERT -> {
                DataOperationGraphNode n = (DataOperationGraphNode) node;
                o.addProperty("opKind", n.getOpKind().name());
                if (n.getResultVar() != null) {
                    o.addProperty("resultVar", n.getResultVar());
                }
            }
            case VALUE_SOURCE -> {
                ValueSourceGraphNode n = (ValueSourceGraphNode) node;
                o.addProperty("sourceKind", n.getSourceKind().name());
            }
            case PROGRAM_ENTRY -> {
            }
            default -> throw new JsonParseException("unknown node type " + node.getType());
        }
        return o;
    }

    @Override
    public ScriptGraphNode deserialize(JsonElement el, Type type, JsonDeserializationContext ctx) throws JsonParseException {
        JsonObject o = el.getAsJsonObject();
        GraphNodeType t = GraphNodeType.valueOf(o.get("type").getAsString());
        ScriptGraphNode node;
        switch (t) {
            case COMMAND -> {
                CommandGraphNode n = new CommandGraphNode();
                n.setTemplate(getStr(o, "template"));
                node = n;
            }
            case IF -> {
                IfGraphNode n = new IfGraphNode();
                if (o.has("condition")) {
                    n.setCondition(ctx.deserialize(o.get("condition"), GraphCondition.class));
                }
                n.setTrueNextId(getStr(o, "trueNextId"));
                n.setFalseNextId(getStr(o, "falseNextId"));
                node = n;
            }
            case LOOP -> {
                LoopGraphNode n = new LoopGraphNode();
                n.setCount(readValue(o, "count"));
                n.setBodyStartId(getStr(o, "bodyStartId"));
                node = n;
            }
            case WAIT -> {
                WaitGraphNode n = new WaitGraphNode();
                n.setTicks(readValue(o, "ticks"));
                node = n;
            }
            case BREAK -> node = new BreakGraphNode();
            case EVENT_SEND, EVENT_RECEIVE -> {
                EventGraphNode n = new EventGraphNode();
                n.setKind(EventGraphNode.EventKind.valueOf(getStr(o, "kind")));
                n.setChannel(readValue(o, "channel"));
                node = n;
            }
            case FUNCTION_CALL -> {
                FunctionCallGraphNode n = new FunctionCallGraphNode();
                n.setFunctionName(getStr(o, "functionName"));
                node = n;
            }
            case VAR_GET, VAR_SET -> {
                VariableGraphNode n = new VariableGraphNode();
                n.setKind(VariableGraphNode.VarOpKind.valueOf(getStr(o, "kind")));
                n.setVarName(getStr(o, "varName"));
                node = n;
            }
            case ARRAY_OP, SET_OP, CONVERT -> {
                DataOperationGraphNode n = new DataOperationGraphNode();
                n.setOpKind(DataOperationGraphNode.DataOpKind.valueOf(getStr(o, "opKind")));
                n.setResultVar(getStr(o, "resultVar"));
                node = n;
            }
            case VALUE_SOURCE -> {
                ValueSourceGraphNode n = new ValueSourceGraphNode();
                n.setSourceKind(ValueSourceGraphNode.ValueSourceKind.valueOf(getStr(o, "sourceKind")));
                node = n;
            }
            case PROGRAM_ENTRY -> node = new ProgramEntryGraphNode();
            default -> throw new JsonParseException("unknown node type " + t);
        }

        // 公共字段
        node.setId(getStr(o, "id"));
        node.setX(o.has("x") ? o.get("x").getAsDouble() : 0);
        node.setY(o.has("y") ? o.get("y").getAsDouble() : 0);
        node.setNextId(getStr(o, "nextId"));
        if (o.has("inputPins")) {
            deserializePins(o.getAsJsonObject("inputPins"), node);
        }
        return node;
    }

    private static JsonObject serializePins(Map<String, GraphValue> pins) {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, GraphValue> e : pins.entrySet()) {
            o.add(e.getKey(), GsonUtil.LEAF.toJsonTree(e.getValue(), GraphValue.class));
        }
        return o;
    }

    private static void deserializePins(JsonObject o, ScriptGraphNode node) {
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            node.getInputPins().put(e.getKey(), GsonUtil.LEAF.fromJson(e.getValue(), GraphValue.class));
        }
    }

    private static GraphValue readValue(JsonObject o, String key) {
        return o.has(key) ? GsonUtil.LEAF.fromJson(o.get(key), GraphValue.class) : null;
    }

    private static String getStr(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}