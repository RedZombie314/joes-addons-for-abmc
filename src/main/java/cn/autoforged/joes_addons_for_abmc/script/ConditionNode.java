package cn.autoforged.joes_addons_for_abmc.script;

import cn.autoforged.joes_addons_for_abmc.script.cond.Condition;

/**
 * 条件分支节点：根据条件真假跳到 trueBranch 或 falseBranch。
 * （不继承单一 next 链，两个分支各自成链，最终汇合到后续节点。）
 */
public class ConditionNode extends ScriptNode {
    private final Condition condition;
    private ScriptNode trueBranch;
    private ScriptNode falseBranch;

    public ConditionNode(Condition condition) {
        this.condition = condition;
    }

    public Condition condition() {
        return condition;
    }

    public ScriptNode trueBranch() {
        return trueBranch;
    }

    public void setTrueBranch(ScriptNode trueBranch) {
        this.trueBranch = trueBranch;
    }

    public ScriptNode falseBranch() {
        return falseBranch;
    }

    public void setFalseBranch(ScriptNode falseBranch) {
        this.falseBranch = falseBranch;
    }
}