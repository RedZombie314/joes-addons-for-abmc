package cn.autoforged.joes_addons_for_abmc.script;

/**
 * 赋值节点：求值一个值并写入指定变量。
 * <p>
 * 用于两类场景：命令节点的命名输入引脚求值（写入临时变量后再注入命令），
 * 以及需要运行时上下文的值来源节点（实体/物品）物化到临时变量。
 */
public class AssignNode extends ScriptNode {
    private final String varName;
    private final ValueResolver resolver;

    public AssignNode(String varName, ValueResolver resolver) {
        this.varName = varName;
        this.resolver = resolver;
    }

    public String varName() {
        return varName;
    }

    public ValueResolver resolver() {
        return resolver;
    }
}