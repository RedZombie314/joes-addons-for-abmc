package cn.autoforged.joes_addons_for_abmc.script.graph.cond;

import cn.autoforged.joes_addons_for_abmc.script.graph.GraphValue;

/**
 * 真值条件：以某个值的真/假（Truthiness）作为条件。
 */
public class GraphTruthinessCondition extends GraphCondition {
    private GraphValue value;

    public GraphTruthinessCondition() {
        super(GraphConditionType.TRUTHINESS);
    }

    public GraphValue getValue() {
        return value;
    }

    public void setValue(GraphValue value) {
        this.value = value;
    }
}