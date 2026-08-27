package cn.autoforged.joes_addons_for_abmc.script.graph;

/**
 * 一个“值”，采用混合模型：
 * <ul>
 *   <li>简单值（数字 / 字符串 / 变量引用）以内嵌表达式文本表示（{@code expr}）；</li>
 *   <li>复杂值（物品、UUID 等）以对值产出节点的引用表示（{@code ref}）。</li>
 * </ul>
 * 两者恰好设置其一。
 */
public class GraphValue {
    private String expr;
    private GraphValueRef ref;

    public GraphValue() {
    }

    public static GraphValue ofExpr(String expr) {
        GraphValue v = new GraphValue();
        v.expr = expr;
        return v;
    }

    public static GraphValue ofRef(GraphValueRef ref) {
        GraphValue v = new GraphValue();
        v.ref = ref;
        return v;
    }

    /** 是否为对外部值节点的引用。 */
    public boolean isRef() {
        return ref != null;
    }

    public String getExpr() {
        return expr;
    }

    public void setExpr(String expr) {
        this.expr = expr;
    }

    public GraphValueRef getRef() {
        return ref;
    }

    public void setRef(GraphValueRef ref) {
        this.ref = ref;
    }
}