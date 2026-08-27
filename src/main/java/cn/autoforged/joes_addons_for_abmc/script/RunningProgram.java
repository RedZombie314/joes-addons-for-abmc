package cn.autoforged.joes_addons_for_abmc.script;

import cn.autoforged.joes_addons_for_abmc.script.expr.Expr;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * 一个正在运行的程序实例（状态机）。
 * 持有当前节点指针与该程序局部作用域，通过 tickStep() 逐步推进。
 * <p>
 * 推进规则（对应设计决策"连续执行到等待才停"）：
 * <ul>
 *   <li>命令节点：立即执行并连续推进到下一个节点；</li>
 *   <li>延迟节点：暂停，等待指定刻数后才继续；</li>
 *   <li>循环节点：推入循环帧，重复执行循环体；</li>
 *   <li>跳出循环节点：弹出最内层循环帧，跳到其 next()；</li>
 *   <li>函数调用节点：推入函数帧（携带局部作用域与续接点），进入函数体；</li>
 *   <li>函数体结束：弹出函数帧，恢复调用者作用域，继续调用点的 next()。</li>
 * </ul>
 */
public class RunningProgram {
    private static final int MAX_STEPS_PER_TICK = 100000;
    private static final int MAX_CALL_DEPTH = 128;

    private final RuntimeEnvironment env;
    private final ScriptScheduler scheduler;
    private VariableScope scope;
    private final UUID id;
    private final Deque<Frame> frameStack = new ArrayDeque<>();

    private ScriptNode current;
    private int waitRemaining;
    private boolean waitingBroadcast;
    private String waitChannel;
    private ScriptNode broadcastResume;
    private boolean finished;

    public RunningProgram(RuntimeEnvironment env, ScriptNode entry, ScriptScheduler scheduler) {
        this.env = env;
        this.scheduler = scheduler;
        this.id = UUID.randomUUID();
        this.scope = env.childScope();
        this.current = entry;
    }

    public UUID id() {
        return id;
    }

    public RuntimeEnvironment environment() {
        return env;
    }

    public VariableScope scope() {
        return scope;
    }

    public boolean isFinished() {
        return finished;
    }

    public void tickStep() {
        if (finished) return;
        if (waitingBroadcast) return; // 阻塞等待广播
        int budget = MAX_STEPS_PER_TICK;
        while (!finished && budget-- > 0) {
            if (current == null) {
                // 链尾：若处于循环体中则进入下一轮迭代；若处于函数体中则返回调用点；否则程序结束
                if (!frameStack.isEmpty() && frameStack.peek() instanceof LoopFrame lf) {
                    current = nextLoopIteration(lf);
                    continue;
                }
                if (!frameStack.isEmpty() && frameStack.peek() instanceof FunctionFrame ff) {
                    frameStack.pop();
                    scope = ff.callerScope;
                    current = ff.after;
                    continue;
                }
                finish();
                return;
            }

            if (current instanceof ConditionNode condNode) {
                boolean result = condNode.condition().test(scope);
                current = result ? condNode.trueBranch() : condNode.falseBranch();
                continue;
            }

            if (current instanceof WaitNode wait) {
                if (waitRemaining <= 0) {
                    waitRemaining = wait.ticks(scope) + 1;
                }
                waitRemaining--;
                if (waitRemaining > 0) return; // 本刻被阻塞
                current = current.next();
                continue;
            }

            if (current instanceof AssignNode assign) {
                scope.set(assign.varName(), assign.resolver().resolve(env, scope));
                current = current.next();
                continue;
            }

            if (current instanceof CommandNode cmd) {
                env.commandExecutor().execute(env, scope, cmd.commandTemplate());
                current = current.next();
                continue;
            }

            if (current instanceof LoopNode loop) {
                int count = loop.count(scope);
                if (count <= 0) {
                    current = loop.next();
                    continue;
                }
                frameStack.push(new LoopFrame(loop, count));
                current = loop.bodyStart();
                continue;
            }

            if (current instanceof FunctionCallNode call) {
                ScriptFunction fn = call.function();
                if (fn == null || fn.bodyStart() == null) {
                    current = call.next();
                    continue;
                }
                if (callDepth() >= MAX_CALL_DEPTH) {
                    // 递归过深：跳过本次调用，避免爆栈
                    current = call.next();
                    continue;
                }
                VariableScope local = new VariableScope(scope);
                List<String> params = fn.parameters();
                List<Expr> args = call.arguments();
                for (int i = 0; i < params.size(); i++) {
                    ScriptValue argVal = (i < args.size()) ? args.get(i).eval(scope) : ScriptValue.nullValue();
                    local.declare(params.get(i), argVal);
                }
                frameStack.push(new FunctionFrame(call.next(), scope));
                scope = local;
                current = fn.bodyStart();
                continue;
            }

            if (current instanceof BreakNode) {
                breakOutOfLoop();
                continue;
            }

            if (current instanceof BroadcastNode br) {
                String ch = br.channel().eval(scope).asString();
                scheduler.postBroadcast(ch);
                current = current.next();
                continue;
            }

            if (current instanceof ReceiveBroadcastNode recv) {
                String ch = recv.channel().eval(scope).asString();
                scheduler.registerBroadcastWait(this, ch);
                waitChannel = ch;
                broadcastResume = current.next();
                waitingBroadcast = true;
                return; // 阻塞，等待广播投递
            }

            // 未知/扩展节点：跳过
            current = current.next();
        }
    }

    private ScriptNode nextLoopIteration(LoopFrame lf) {
        lf.remaining--;
        if (lf.remaining > 0) {
            return lf.loopNode.bodyStart();
        }
        frameStack.pop();
        return lf.loopNode.next();
    }

    private void breakOutOfLoop() {
        while (!frameStack.isEmpty()) {
            Frame f = frameStack.peek();
            if (f instanceof LoopFrame lf) {
                frameStack.pop();
                current = lf.loopNode.next();
                return;
            }
            if (f instanceof FunctionFrame) {
                // 不跨函数边界跳出循环
                current = null;
                return;
            }
            frameStack.pop();
        }
        current = null;
    }

    private int callDepth() {
        int depth = 0;
        for (Frame f : frameStack) {
            if (f instanceof FunctionFrame) depth++;
        }
        return depth;
    }

    private void finish() {
        finished = true;
        frameStack.clear();
        waitingBroadcast = false;
        scheduler.removeBroadcastWait(this);
    }

    /** 广播投递回调：若频道匹配则恢复执行。 */
    public void onBroadcastFired(String channel) {
        if (waitingBroadcast && channel != null && channel.equals(waitChannel)) {
            waitingBroadcast = false;
            waitChannel = null;
            current = broadcastResume;
            broadcastResume = null;
        }
    }

    /** 运行时帧。 */
    private interface Frame {
    }

    private static class LoopFrame implements Frame {
        final LoopNode loopNode;
        int remaining;

        LoopFrame(LoopNode loopNode, int count) {
            this.loopNode = loopNode;
            this.remaining = count;
        }
    }

    /** 函数调用帧：记录返回后的续接点与调用者作用域。 */
    private static class FunctionFrame implements Frame {
        final ScriptNode after;
        final VariableScope callerScope;

        FunctionFrame(ScriptNode after, VariableScope callerScope) {
            this.after = after;
            this.callerScope = callerScope;
        }
    }
}