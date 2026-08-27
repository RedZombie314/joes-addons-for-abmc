package cn.autoforged.joes_addons_for_abmc.script.graph.cond;

/**
 * 布尔字面量条件：恒真 / 恒假。
 */
public class GraphBooleanLiteralCondition extends GraphCondition {
    private boolean value;

    public GraphBooleanLiteralCondition() {
        super(GraphConditionType.BOOLEAN);
    }

    public boolean isValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }
}