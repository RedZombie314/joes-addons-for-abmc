package cn.autoforged.joes_addons_for_abmc.script;

/**
 * 脚本节点基类：构成线性执行链（每个节点持有指向下一个节点的引用）。
 * 最终持久化的节点图（阶段A）会编译到这些运行时节点。
 */
public abstract class ScriptNode {
    private ScriptNode next;

    public ScriptNode next() {
        return next;
    }

    public void setNext(ScriptNode next) {
        this.next = next;
    }
}