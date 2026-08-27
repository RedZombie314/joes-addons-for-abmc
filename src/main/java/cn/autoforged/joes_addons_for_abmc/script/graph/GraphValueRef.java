package cn.autoforged.joes_addons_for_abmc.script.graph;

/**
 * 对某个“值产出节点”输出引脚的引用（复杂值）。
 * <p>
 * 用于把复杂值（物品、UUID 等）从来源节点接入到其它节点的输入区。
 */
public class GraphValueRef {
    private String nodeId;
    private String outputPin = Pins.VALUE;

    public GraphValueRef() {
    }

    public GraphValueRef(String nodeId, String outputPin) {
        this.nodeId = nodeId;
        this.outputPin = outputPin;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getOutputPin() {
        return outputPin;
    }

    public void setOutputPin(String outputPin) {
        this.outputPin = outputPin;
    }
}