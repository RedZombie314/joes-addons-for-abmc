package cn.autoforged.joes_addons_for_abmc.script.graph;

/**
 * 广播事件节点：SEND 触发广播，RECEIVE 阻塞等待广播。
 */
public class EventGraphNode extends ScriptGraphNode {

    public enum EventKind {
        SEND, RECEIVE
    }

    private EventKind kind = EventKind.SEND;
    private GraphValue channel;

    public EventGraphNode() {
        super(GraphNodeType.EVENT_SEND);
    }

    public EventKind getKind() {
        return kind;
    }

    public void setKind(EventKind kind) {
        this.kind = kind;
        this.setType(kind == EventKind.RECEIVE ? GraphNodeType.EVENT_RECEIVE : GraphNodeType.EVENT_SEND);
    }

    public GraphValue getChannel() {
        return channel;
    }

    public void setChannel(GraphValue channel) {
        this.channel = channel;
    }
}