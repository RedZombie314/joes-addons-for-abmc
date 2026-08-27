package cn.autoforged.joes_addons_for_abmc.script.graph;

/**
 * 引脚名常量：约定各节点类型暴露的输入/输出引脚名称，供编辑器与编译器统一引用。
 */
public final class Pins {
    private Pins() {
    }

    /** 值产出节点默认的输出引脚名。 */
    public static final String VALUE = "value";

    // 值来源节点（VALUE_SOURCE）的常用输入引脚
    public static final String ENTITY = "entity";
    public static final String SLOT = "slot";
    public static final String TARGET = "target";

    // 数组操作（ARRAY_OP）输入引脚
    public static final String ARRAY = "array";
    public static final String INDEX = "index";
    public static final String ELEMENT = "element";

    // 集合操作（SET_OP）输入引脚
    public static final String SET = "set";
    public static final String MEMBER = "member";

    // 转换（CONVERT）输入引脚
    public static final String SOURCE = "source";

    // 值来源节点（VALUE_SOURCE）的物品输入引脚
    public static final String ITEM = "item";
}