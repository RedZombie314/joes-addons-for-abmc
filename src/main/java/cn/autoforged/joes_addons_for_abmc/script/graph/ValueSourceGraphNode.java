package cn.autoforged.joes_addons_for_abmc.script.graph;

/**
 * 复杂值来源节点：产出物品、UUID 等运行时复杂值。
 * <p>
 * 输入引脚（entity/slot/target 等，见 {@link Pins}）指定来源，
 * 输出引脚统一为 {@link Pins#VALUE}。典型用法：
 * “获取某处物品命名空间 / 实体 UUID”等，供其它节点输入区连接。
 */
public class ValueSourceGraphNode extends ScriptGraphNode {

    public enum ValueSourceKind {
        ITEM_IN_HAND,
        ITEM_IN_SLOT,
        ITEM_NAMESPACE,
        ENTITY_UUID,
        SELECTED_ENTITY,
        SELF_PLAYER
    }

    private ValueSourceKind sourceKind = ValueSourceKind.ITEM_IN_HAND;

    public ValueSourceGraphNode() {
        super(GraphNodeType.VALUE_SOURCE);
    }

    public ValueSourceKind getSourceKind() {
        return sourceKind;
    }

    public void setSourceKind(ValueSourceKind sourceKind) {
        this.sourceKind = sourceKind;
    }
}