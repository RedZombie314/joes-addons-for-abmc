package cn.autoforged.joes_addons_for_abmc.script.graph;

/**
 * 数据操作节点：对数组 / 集合 / 转换相关操作。
 * <p>
 * 输入引脚见 {@link Pins}（array/index/element/set/member/source 等），
 * 输出引脚统一为 {@link Pins#VALUE}。可同时把结果写入某个变量（可选）。
 */
public class DataOperationGraphNode extends ScriptGraphNode {

    public enum DataOpKind {
        ARRAY_GET,
        ARRAY_SET,
        ARRAY_LENGTH,
        ARRAY_APPEND,
        SET_ADD,
        SET_REMOVE,
        SET_CONTAINS,
        TO_STRING,
        TO_NUMBER,
        TO_UUID,
        TO_ITEM
    }

    private DataOpKind opKind = DataOpKind.ARRAY_GET;
    private String resultVar;

    public DataOperationGraphNode() {
        super(GraphNodeType.ARRAY_OP);
    }

    public DataOpKind getOpKind() {
        return opKind;
    }

    public void setOpKind(DataOpKind opKind) {
        this.opKind = opKind;
        switch (opKind) {
            case ARRAY_GET, ARRAY_SET, ARRAY_LENGTH, ARRAY_APPEND -> setType(GraphNodeType.ARRAY_OP);
            case SET_ADD, SET_REMOVE, SET_CONTAINS -> setType(GraphNodeType.SET_OP);
            default -> setType(GraphNodeType.CONVERT);
        }
    }

    /** 可选：把结果写入的变量名，null 表示不写入。 */
    public String getResultVar() {
        return resultVar;
    }

    public void setResultVar(String resultVar) {
        this.resultVar = resultVar;
    }
}