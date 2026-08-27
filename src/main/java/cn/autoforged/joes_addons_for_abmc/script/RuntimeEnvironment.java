package cn.autoforged.joes_addons_for_abmc.script;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * 脚本运行环境：持有共享的全局变量作用域、命令源与服务器上下文。
 * <p>
 * 命令源默认使用服务器控制台；当由某位玩家触发运行时，应通过
 * {@link #setCommandSource(CommandSourceStack)} 设为该玩家的命令源，
 * 使脚本命令以玩家身份/位置执行。
 */
public class RuntimeEnvironment {
    private final MinecraftServer server;
    private final VariableScope globalScope;
    private CommandSourceStack commandSource;
    private CommandExecutor commandExecutor;

    public RuntimeEnvironment(MinecraftServer server) {
        this.server = server;
        this.globalScope = new VariableScope(null);
        this.commandSource = server != null ? server.createCommandSourceStack() : null;
        this.commandExecutor = new ServerCommandExecutor();
    }

    public MinecraftServer server() {
        return server;
    }

    public CommandExecutor commandExecutor() {
        return commandExecutor;
    }

    public void setCommandExecutor(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public CommandSourceStack commandSource() {
        return commandSource;
    }

    public void setCommandSource(CommandSourceStack commandSource) {
        this.commandSource = commandSource;
    }

    public ServerLevel overworld() {
        return server != null ? server.overworld() : null;
    }

    public VariableScope globalScope() {
        return globalScope;
    }

    /** 新建一个子作用域（用于函数调用/循环体局部变量）。 */
    public VariableScope childScope() {
        return new VariableScope(globalScope);
    }
}