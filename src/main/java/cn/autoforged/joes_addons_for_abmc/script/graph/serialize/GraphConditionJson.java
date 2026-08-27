package cn.autoforged.joes_addons_for_abmc.script.graph.serialize;

import cn.autoforged.joes_addons_for_abmc.script.cond.CompareOp;
import cn.autoforged.joes_addons_for_abmc.script.graph.GraphValue;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphBooleanLiteralCondition;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphCompareCondition;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphCondition;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphCondition.GraphConditionType;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphLogicalCondition;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphNotCondition;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphTruthinessCondition;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * 内嵌条件树的手动多态 (反)序列化适配器。
 * <p>
 * 以 {@code type} 字段判别具体条件子类；比较/逻辑/非的条件操作数与子条件
 * 递归序列化，值字段（GraphValue）委托给 {@link GsonUtil#LEAF}。
 */
public class GraphConditionJson implements JsonSerializer<GraphCondition>, JsonDeserializer<GraphCondition> {

    @Override
    public JsonElement serialize(GraphCondition condition, Type type, JsonSerializationContext ctx) {
        JsonObject o = new JsonObject();
        o.addProperty("type", condition.getType().name());
        switch (condition.getType()) {
            case COMPARE -> {
                GraphCompareCondition n = (GraphCompareCondition) condition;
                o.add("left", GsonUtil.LEAF.toJsonTree(n.getLeft(), GraphValue.class));
                o.addProperty("op", n.getOp().name());
                o.add("right", GsonUtil.LEAF.toJsonTree(n.getRight(), GraphValue.class));
            }
            case LOGICAL -> {
                GraphLogicalCondition n = (GraphLogicalCondition) condition;
                o.addProperty("logicalOp", n.getLogicalOp().name());
                JsonArray arr = new JsonArray();
                for (GraphCondition operand : n.getOperands()) {
                    arr.add(serialize(operand, GraphCondition.class, ctx));
                }
                o.add("operands", arr);
            }
            case NOT -> {
                GraphNotCondition n = (GraphNotCondition) condition;
                o.add("operand", serialize(n.getOperand(), GraphCondition.class, ctx));
            }
            case BOOLEAN -> {
                GraphBooleanLiteralCondition n = (GraphBooleanLiteralCondition) condition;
                o.addProperty("value", n.isValue());
            }
            case TRUTHINESS -> {
                GraphTruthinessCondition n = (GraphTruthinessCondition) condition;
                o.add("value", GsonUtil.LEAF.toJsonTree(n.getValue(), GraphValue.class));
            }
            default -> throw new JsonParseException("unknown condition type " + condition.getType());
        }
        return o;
    }

    @Override
    public GraphCondition deserialize(JsonElement el, Type type, JsonDeserializationContext ctx) throws JsonParseException {
        JsonObject o = el.getAsJsonObject();
        GraphConditionType t = GraphConditionType.valueOf(o.get("type").getAsString());
        switch (t) {
            case COMPARE -> {
                GraphCompareCondition n = new GraphCompareCondition();
                n.setLeft(GsonUtil.LEAF.fromJson(o.get("left"), GraphValue.class));
                n.setOp(CompareOp.valueOf(o.get("op").getAsString()));
                n.setRight(GsonUtil.LEAF.fromJson(o.get("right"), GraphValue.class));
                return n;
            }
            case LOGICAL -> {
                GraphLogicalCondition n = new GraphLogicalCondition();
                n.setLogicalOp(GraphLogicalCondition.LogicalOp.valueOf(o.get("logicalOp").getAsString()));
                for (JsonElement e : o.getAsJsonArray("operands")) {
                    n.add(deserialize(e, GraphCondition.class, ctx));
                }
                return n;
            }
            case NOT -> {
                GraphNotCondition n = new GraphNotCondition();
                n.setOperand(deserialize(o.get("operand"), GraphCondition.class, ctx));
                return n;
            }
            case BOOLEAN -> {
                GraphBooleanLiteralCondition n = new GraphBooleanLiteralCondition();
                n.setValue(o.get("value").getAsBoolean());
                return n;
            }
            case TRUTHINESS -> {
                GraphTruthinessCondition n = new GraphTruthinessCondition();
                n.setValue(GsonUtil.LEAF.fromJson(o.get("value"), GraphValue.class));
                return n;
            }
            default -> throw new JsonParseException("unknown condition type " + t);
        }
    }
}