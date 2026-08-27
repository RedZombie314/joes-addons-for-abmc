package cn.autoforged.joes_addons_for_abmc.script;

import cn.autoforged.joes_addons_for_abmc.script.expr.Expr;

/** 广播节点（发出事件）：求值频道名并投递到事件总线。 */
public class BroadcastNode extends ScriptNode {
    private final Expr channel;

    public BroadcastNode(Expr channel) {
        this.channel = channel;
    }

    public Expr channel() {
        return channel;
    }
}