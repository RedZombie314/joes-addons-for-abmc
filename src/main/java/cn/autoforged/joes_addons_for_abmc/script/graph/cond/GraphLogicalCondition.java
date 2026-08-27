package cn.autoforged.joes_addons_for_abmc.script.graph.cond;

import java.util.ArrayList;
import java.util.List;

/**
 * 逻辑组合条件：将多个子条件按 AND / OR 组合。
 */
public class GraphLogicalCondition extends GraphCondition {

    public enum LogicalOp {
        AND, OR
    }

    private LogicalOp logicalOp = LogicalOp.AND;
    private final List<GraphCondition> operands = new ArrayList<>();

    public GraphLogicalCondition() {
        super(GraphConditionType.LOGICAL);
    }

    public LogicalOp getLogicalOp() {
        return logicalOp;
    }

    public void setLogicalOp(LogicalOp logicalOp) {
        this.logicalOp = logicalOp;
    }

    public List<GraphCondition> getOperands() {
        return operands;
    }

    public void add(GraphCondition operand) {
        operands.add(operand);
    }
}