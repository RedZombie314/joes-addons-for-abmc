package cn.autoforged.joes_addons_for_abmc.script;

/**
 * 命令执行器：调度器遇到命令节点时回调。
 * 阶段 D12 将实现占位符替换与 performPrefixedCommand 实际执行。
 */
@FunctionalInterface
public interface CommandExecutor {
    void execute(RuntimeEnvironment env, VariableScope scope, String commandTemplate);
}