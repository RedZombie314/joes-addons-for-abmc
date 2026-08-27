package cn.autoforged.joes_addons_for_abmc.script.graph;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 节点图节点的抽象基类。
 * <p>
 * 所有节点共有的字段：
 * <ul>
 *   <li>{@code id}：图内唯一标识（用于连线引用）；</li>
 *   <li>{@code type}：类型判别符；</li>
 *   <li>{@code x}/{@code y}：画布布局坐标（面向图形编辑器，持久化保存）；</li>
 *   <li>{@code nextId}：线性执行链上的下一个节点 id（链尾为 null）；</li>
 *   <li>{@code inputPins}：命名输入引脚 → 其输入值（简单式或值节点引用）。</li>
 * </ul>
 * 分支、循环体、条件等结构由具体子类扩展。
 */
public abstract class ScriptGraphNode {
    private String id;
    private GraphNodeType type;
    private double x;
    private double y;
    private String nextId;
    private Map<String, GraphValue> inputPins = new LinkedHashMap<>();

    protected ScriptGraphNode(GraphNodeType type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public GraphNodeType getType() {
        return type;
    }

    protected void setType(GraphNodeType type) {
        this.type = type;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public String getNextId() {
        return nextId;
    }

    public void setNextId(String nextId) {
        this.nextId = nextId;
    }

    public Map<String, GraphValue> getInputPins() {
        return inputPins;
    }

    /** 绑定某输入引脚的值。 */
    public void bindInput(String pin, GraphValue value) {
        inputPins.put(pin, value);
    }

    /** 以简单内嵌表达式绑定某输入引脚。 */
    public void bindExpr(String pin, String expr) {
        inputPins.put(pin, GraphValue.ofExpr(expr));
    }

    /** 以值节点引用绑定某输入引脚。 */
    public void bindRef(String pin, String sourceNodeId, String outputPin) {
        inputPins.put(pin, GraphValue.ofRef(new GraphValueRef(sourceNodeId, outputPin)));
    }
}