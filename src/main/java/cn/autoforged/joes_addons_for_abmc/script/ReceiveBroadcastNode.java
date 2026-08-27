package cn.autoforged.joes_addons_for_abmc.script;

import cn.autoforged.joes_addons_for_abmc.script.expr.Expr;

/** 接收广播节点：阻塞当前程序，直到指定频道的广播在下一 tick 投递后继续。 */
public class ReceiveBroadcastNode extends ScriptNode {
    private final Expr channel;

    public ReceiveBroadcastNode(Expr channel) {
        this.channel = channel;
    }

    public Expr channel() {
        return channel;
    }
}