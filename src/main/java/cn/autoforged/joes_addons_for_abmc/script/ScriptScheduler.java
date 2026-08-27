package cn.autoforged.joes_addons_for_abmc.script;

import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 脚本调度器（单例）：管理所有正在运行的程序，每个服务端 tick 驱动它们。
 * <p>
 * 同时充当广播事件总线：广播在投递时加入待处理队列，在下一 tick 起点统一投递，
 * 解耦事件产生与消费，避免同一 tick 内自广播导致的状态重入问题。
 */
public class ScriptScheduler {
    private static final ScriptScheduler INSTANCE = new ScriptScheduler();

    private final Set<RunningProgram> running =
        Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, RunningProgram> runningById = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> broadcastWaits = new ConcurrentHashMap<>();
    private final Deque<String> pendingBroadcasts = new ArrayDeque<>();

    private ScriptScheduler() {
    }

    public static ScriptScheduler getInstance() {
        return INSTANCE;
    }

    /** 启动一个程序，返回其运行句柄。 */
    public RunningProgram start(RuntimeEnvironment env, ScriptNode entry) {
        RunningProgram program = new RunningProgram(env, entry, this);
        running.add(program);
        runningById.put(program.id(), program);
        return program;
    }

    /** 每个服务端 tick 调用，推进所有运行中的程序。 */
    public void tick(MinecraftServer server) {
        deliverPendingBroadcasts();
        Iterator<RunningProgram> it = running.iterator();
        while (it.hasNext()) {
            RunningProgram p = it.next();
            p.tickStep();
            if (p.isFinished()) {
                it.remove();
                runningById.remove(p.id());
                removeBroadcastWait(p);
            }
        }
    }

    /** 登记某程序等待某广播频道。 */
    public void registerBroadcastWait(RunningProgram p, String channel) {
        broadcastWaits.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(p.id());
    }

    /** 移除某程序的所有广播等待。 */
    public void removeBroadcastWait(RunningProgram p) {
        for (Set<UUID> ids : broadcastWaits.values()) {
            ids.remove(p.id());
        }
    }

    /** 触发广播：进入待投递队列，下一 tick 投递。 */
    public void postBroadcast(String channel) {
        if (channel == null) return;
        pendingBroadcasts.add(channel);
    }

    /** 投递待处理广播：唤醒所有等待对应频道的程序。 */
    private void deliverPendingBroadcasts() {
        while (!pendingBroadcasts.isEmpty()) {
            String channel = pendingBroadcasts.poll();
            Set<UUID> waiters = broadcastWaits.remove(channel);
            if (waiters == null) continue;
            for (UUID id : waiters) {
                RunningProgram p = runningById.get(id);
                if (p != null) {
                    p.onBroadcastFired(channel);
                }
            }
        }
    }

    public void stopAll() {
        running.clear();
        runningById.clear();
        broadcastWaits.clear();
        pendingBroadcasts.clear();
    }

    public int runningCount() {
        return running.size();
    }
}