package cn.autoforged.joes_addons_for_abmc.script.graph.cond;

import cn.autoforged.joes_addons_for_abmc.script.cond.CompareOp;
import cn.autoforged.joes_addons_for_abmc.script.graph.GraphValue;

/**
 * 比较条件：比较左右两个操作数（内嵌式或值节点引用）。
 */
public class GraphCompareCondition extends GraphCondition {
    private GraphValue left;
    private CompareOp op;
    private GraphValue right;

    public GraphCompareCondition() {
        super(GraphConditionType.COMPARE);
    }

    public GraphValue getLeft() {
        return left;
    }

    public void setLeft(GraphValue left) {
        this.left = left;
    }

    public CompareOp getOp() {
        return op;
    }

    public void setOp(CompareOp op) {
        this.op = op;
    }

    public GraphValue getRight() {
        return right;
    }

    public void setRight(GraphValue right) {
        this.right = right;
    }
}