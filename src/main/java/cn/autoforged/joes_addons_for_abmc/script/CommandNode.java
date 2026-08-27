package cn.autoforged.joes_addons_for_abmc.script;

/**
 * 命令节点：执行一条命令。命令模板可含 $(变量名) 占位符，
 * 实际替换与触发执行由阶段 D12 的命令执行器完成。
 */
public class CommandNode extends ScriptNode {
    private final String commandTemplate;

    public CommandNode(String commandTemplate) {
        this.commandTemplate = commandTemplate;
    }

    public String commandTemplate() {
        return commandTemplate;
    }
}