package cn.autoforged.joes_addons_for_abmc.script.graph.cond;

/**
 * 取反条件：对子条件结果取反。
 */
public class GraphNotCondition extends GraphCondition {
    private GraphCondition operand;

    public GraphNotCondition() {
        super(GraphConditionType.NOT);
    }

    public GraphCondition getOperand() {
        return operand;
    }

    public void setOperand(GraphCondition operand) {
        this.operand = operand;
    }
}