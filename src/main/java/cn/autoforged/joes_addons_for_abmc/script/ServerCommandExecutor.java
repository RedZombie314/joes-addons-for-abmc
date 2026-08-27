package cn.autoforged.joes_addons_for_abmc.script;

import net.minecraft.commands.CommandSourceStack;

/**
 * 服务端命令执行器：占位符替换后，通过 performPrefixedCommand 以环境持有的命令源执行命令。
 */
public class ServerCommandExecutor implements CommandExecutor {

    @Override
    public void execute(RuntimeEnvironment env, VariableScope scope, String commandTemplate) {
        String resolved = CommandResolver.resolve(commandTemplate, scope);
        if (resolved == null || resolved.isBlank()) return;

        String command = resolved.startsWith("/") ? resolved.substring(1) : resolved;
        CommandSourceStack source = env.commandSource();
        if (source == null || env.server() == null) return;
        env.server().getCommands().performPrefixedCommand(source, command);
    }
}