package cn.autoforged.joes_addons_for_abmc.script.graph.compile;

import cn.autoforged.joes_addons_for_abmc.script.AssignNode;
import cn.autoforged.joes_addons_for_abmc.script.BreakNode;
import cn.autoforged.joes_addons_for_abmc.script.BroadcastNode;
import cn.autoforged.joes_addons_for_abmc.script.CommandNode;
import cn.autoforged.joes_addons_for_abmc.script.ConditionNode;
import cn.autoforged.joes_addons_for_abmc.script.FunctionCallNode;
import cn.autoforged.joes_addons_for_abmc.script.LoopNode;
import cn.autoforged.joes_addons_for_abmc.script.NoOpNode;
import cn.autoforged.joes_addons_for_abmc.script.ReceiveBroadcastNode;
import cn.autoforged.joes_addons_for_abmc.script.RuntimeEnvironment;
import cn.autoforged.joes_addons_for_abmc.script.ScriptFunction;
import cn.autoforged.joes_addons_for_abmc.script.ScriptNode;
import cn.autoforged.joes_addons_for_abmc.script.ScriptValue;
import cn.autoforged.joes_addons_for_abmc.script.VariableScope;
import cn.autoforged.joes_addons_for_abmc.script.ValueResolver;
import cn.autoforged.joes_addons_for_abmc.script.WaitNode;
import cn.autoforged.joes_addons_for_abmc.script.cond.BooleanCondition;
import cn.autoforged.joes_addons_for_abmc.script.cond.CompareCondition;
import cn.autoforged.joes_addons_for_abmc.script.cond.Condition;
import cn.autoforged.joes_addons_for_abmc.script.cond.NotCondition;
import cn.autoforged.joes_addons_for_abmc.script.expr.DataOpExpr;
import cn.autoforged.joes_addons_for_abmc.script.expr.Expr;
import cn.autoforged.joes_addons_for_abmc.script.expr.LiteralExpr;
import cn.autoforged.joes_addons_for_abmc.script.expr.VariableExpr;
import cn.autoforged.joes_addons_for_abmc.script.graph.CommandGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.DataOperationGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.EventGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.FunctionCallGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.GraphNodeType;
import cn.autoforged.joes_addons_for_abmc.script.graph.GraphValue;
import cn.autoforged.joes_addons_for_abmc.script.graph.GraphValueRef;
import cn.autoforged.joes_addons_for_abmc.script.graph.IfGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.LoopGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.Pins;
import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraph;
import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.ValueSourceGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.VariableGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.WaitGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphBooleanLiteralCondition;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphCompareCondition;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphCondition;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphLogicalCondition;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphNotCondition;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphTruthinessCondition;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A3 编译入口：把节点图（{@link ScriptGraph}）翻译为运行时节点链（{@link ScriptNode} 树）。
 * <p>
 * 要点：
 * <ul>
 *   <li>控制流（顺序/分支/循环/延迟/跳出/广播/函数调用）按图结构直接编译；</li>
 *   <li>内联简单值用 {@link ExprParser} 解析为 {@link Expr}；</li>
 *   <li>复杂值（物品/UUID）的值来源节点需要运行时上下文，在程序开头物化为临时变量，
 *       各处引用改写为临时变量读取；</li>
 *   <li>命令节点把命名输入引脚物化为临时变量，并把模板中的 {@code @引脚} 改写为
 *       {@code $(临时变量)} 交给运行时命令解析器；</li>
 *   <li>数据操作节点编译为纯 {@link DataOpExpr}（按需求值）。</li>
 * </ul>
 */
public class GraphCompiler {
    private final ScriptGraph graph;
    private final Map<String, ScriptFunction> functions;
    private final Map<String, String> valueSourceVars = new HashMap<>();
    private final Map<String, Expr> dataOpCache = new HashMap<>();

    public GraphCompiler(ScriptGraph graph, Map<String, ScriptFunction> functions) {
        this.graph = graph;
        this.functions = functions != null ? functions : Map.of();
    }

    /** 编译主程序入口节点链（含值来源物化前置）。 */
    public ScriptNode compileEntry() {
        return compileBody();
    }

    /** 编译本图为一段函数体（供外部先注册函数骨架、再填充 body 以支持函数间双向调用）。 */
    public ScriptNode compileFunctionBody() {
        return compileBody();
    }

    /** 把本图编译为一个自定义函数体（供全局函数库使用）。 */
    public ScriptFunction buildFunction(String name, List<String> params) {
        ScriptFunction fn = new ScriptFunction(name);
        for (String p : params) {
            fn.addParameter(p);
        }
        fn.setBodyStart(compileBody());
        return fn;
    }

    private ScriptNode compileBody() {
        collectValueSourceRefs();
        ScriptNode materializers = buildValueSourceMaterializers();
        ScriptNode chain = compileChain(findEntryId());
        return appendChain(materializers, chain);
    }

    /**
     * 确定程序入口：若图中存在「当点击运行时」入口节点（PROGRAM_ENTRY），
     * 则从其输出端（nextId）开始执行；否则回退到图默认的入口节点 id。
     */
    private String findEntryId() {
        for (ScriptGraphNode n : graph.getNodes().values()) {
            if (n != null && n.getType() == GraphNodeType.PROGRAM_ENTRY && n.getNextId() != null) {
                return n.getNextId();
            }
        }
        return graph.getEntryNodeId();
    }

    // ---------- 链式编译 ----------

    private ScriptNode compileChain(String startId) {
        if (startId == null) {
            return null;
        }
        ScriptGraphNode gn = graph.node(startId);
        if (gn == null) {
            return null;
        }
        ScriptNode node = compileNode(gn);
        // 注意：部分图节点会编译为多节点链（如带引脚的命令会被物化为 AssignNode->CommandNode），
        // 因此续接必须挂在链尾（appendChain），而非覆盖链头自身的 next。
        return appendChain(node, compileChain(gn.getNextId()));
    }

    private ScriptNode compileNode(ScriptGraphNode gn) {
        switch (gn.getType()) {
            case COMMAND:
                return compileCommand((CommandGraphNode) gn);
            case IF:
                return compileIf((IfGraphNode) gn);
            case LOOP: {
                LoopGraphNode n = (LoopGraphNode) gn;
                return new LoopNode(compileValue(n.getCount()), compileChain(n.getBodyStartId()));
            }
            case WAIT: {
                WaitGraphNode n = (WaitGraphNode) gn;
                return new WaitNode(compileValue(n.getTicks()));
            }
            case BREAK:
                return new BreakNode();
            case EVENT_SEND: {
                EventGraphNode n = (EventGraphNode) gn;
                return new BroadcastNode(compileValue(n.getChannel()));
            }
            case EVENT_RECEIVE: {
                EventGraphNode n = (EventGraphNode) gn;
                return new ReceiveBroadcastNode(compileValue(n.getChannel()));
            }
            case FUNCTION_CALL:
                return compileFunctionCall((FunctionCallGraphNode) gn);
            case VAR_SET: {
                VariableGraphNode n = (VariableGraphNode) gn;
                GraphValue value = n.getInputPins().get(Pins.VALUE);
                return new AssignNode(n.getVarName(), ValueResolver.ofExpr(compileValue(value)));
            }
            case VAR_GET, VALUE_SOURCE, ARRAY_OP, SET_OP, CONVERT:
            default:
                return new NoOpNode();
        }
    }

    private ScriptNode compileIf(IfGraphNode n) {
        ScriptNode trueC = compileChain(n.getTrueNextId());
        ScriptNode falseC = compileChain(n.getFalseNextId());
        ScriptNode cont = compileChain(n.getNextId());
        ConditionNode cond = new ConditionNode(compileCondition(n.getCondition()));
        cond.setTrueBranch(appendChain(trueC, cont));
        cond.setFalseBranch(appendChain(falseC, cont));
        return cond;
    }

    private ScriptNode compileCommand(CommandGraphNode n) {
        String template = n.getTemplate();
        ScriptNode head = null;
        ScriptNode tail = null;
        for (Map.Entry<String, GraphValue> e : n.getInputPins().entrySet()) {
            String pin = e.getKey();
            String marker = "@" + pin;
            if (!template.contains(marker)) {
                continue; // 模板未引用该引脚，无需物化
            }
            String tempVar = "__p_" + n.getId() + "_" + pin;
            AssignNode an = new AssignNode(tempVar, ValueResolver.ofExpr(compileValue(e.getValue())));
            if (head == null) {
                head = an;
                tail = an;
            } else {
                tail.setNext(an);
                tail = an;
            }
            template = template.replace(marker, "$(" + tempVar + ")");
        }
        CommandNode cmd = new CommandNode(template);
        if (head == null) {
            return cmd;
        }
        tail.setNext(cmd);
        return head;
    }

    private ScriptNode compileFunctionCall(FunctionCallGraphNode n) {
        ScriptFunction fn = functions.get(n.getFunctionName());
        if (fn == null || fn.bodyStart() == null) {
            return new NoOpNode();
        }
        FunctionCallNode call = new FunctionCallNode(fn);
        for (String param : fn.parameters()) {
            GraphValue arg = n.getInputPins().get(param);
            call.addArgument(compileValue(arg));
        }
        return call;
    }

    // ---------- 值编译 ----------

    private Expr compileValue(GraphValue gv) {
        if (gv == null) {
            return new LiteralExpr(ScriptValue.nullValue());
        }
        if (gv.isRef()) {
            return compileValueRef(gv.getRef());
        }
        return ExprParser.parse(gv.getExpr());
    }

    private Expr compileValueRef(GraphValueRef ref) {
        ScriptGraphNode src = graph.node(ref.getNodeId());
        if (src == null) {
            return new LiteralExpr(ScriptValue.nullValue());
        }
        switch (src.getType()) {
            case VAR_GET: {
                VariableGraphNode v = (VariableGraphNode) src;
                return new VariableExpr(v.getVarName());
            }
            case VALUE_SOURCE:
                return new VariableExpr(tempVarFor(src.getId()));
            case ARRAY_OP, SET_OP, CONVERT:
                return compileDataOp((DataOperationGraphNode) src);
            default:
                return new VariableExpr(tempVarFor(src.getId()));
        }
    }

    private Expr compileDataOp(DataOperationGraphNode n) {
        return dataOpCache.computeIfAbsent(n.getId(), id -> buildDataOpExpr(n));
    }

    private Expr buildDataOpExpr(DataOperationGraphNode n) {
        Map<String, GraphValue> pins = n.getInputPins();
        switch (n.getOpKind()) {
            case ARRAY_LENGTH:
                return new DataOpExpr(DataOpExpr.Op.ARRAY_LENGTH, List.of(pinExpr(pins, Pins.ARRAY)));
            case ARRAY_GET:
                return new DataOpExpr(DataOpExpr.Op.ARRAY_GET,
                    List.of(pinExpr(pins, Pins.ARRAY), pinExpr(pins, Pins.INDEX)));
            case ARRAY_APPEND:
                return new DataOpExpr(DataOpExpr.Op.ARRAY_APPEND,
                    List.of(pinExpr(pins, Pins.ARRAY), pinExpr(pins, Pins.ELEMENT)));
            case SET_CONTAINS:
                return new DataOpExpr(DataOpExpr.Op.SET_CONTAINS,
                    List.of(pinExpr(pins, Pins.SET), pinExpr(pins, Pins.MEMBER)));
            case SET_ADD:
                return new DataOpExpr(DataOpExpr.Op.SET_ADD,
                    List.of(pinExpr(pins, Pins.SET), pinExpr(pins, Pins.MEMBER)));
            case TO_STRING:
                return new DataOpExpr(DataOpExpr.Op.TO_STRING, List.of(pinExpr(pins, Pins.SOURCE)));
            case TO_NUMBER:
                return new DataOpExpr(DataOpExpr.Op.TO_NUMBER, List.of(pinExpr(pins, Pins.SOURCE)));
            default:
                return new LiteralExpr(ScriptValue.nullValue());
        }
    }

    private Expr pinExpr(Map<String, GraphValue> pins, String pin) {
        return compileValue(pins.get(pin));
    }

    // ---------- 条件编译 ----------

    private Condition compileCondition(GraphCondition gc) {
        if (gc == null) {
            return s -> true;
        }
        switch (gc.getType()) {
            case COMPARE: {
                GraphCompareCondition n = (GraphCompareCondition) gc;
                return new CompareCondition(compileValue(n.getLeft()), n.getOp(), compileValue(n.getRight()));
            }
            case LOGICAL: {
                GraphLogicalCondition n = (GraphLogicalCondition) gc;
                boolean and = n.getLogicalOp() == GraphLogicalCondition.LogicalOp.AND;
                List<Condition> conds = new ArrayList<>();
                for (GraphCondition operand : n.getOperands()) {
                    conds.add(compileCondition(operand));
                }
                if (conds.isEmpty()) {
                    return s -> and;
                }
                Condition acc = conds.get(0);
                for (int i = 1; i < conds.size(); i++) {
                    Condition l = acc;
                    Condition r = conds.get(i);
                    acc = and ? (s -> l.test(s) && r.test(s)) : (s -> l.test(s) || r.test(s));
                }
                return acc;
            }
            case NOT:
                return new NotCondition(compileCondition(((GraphNotCondition) gc).getOperand()));
            case BOOLEAN: {
                boolean v = ((GraphBooleanLiteralCondition) gc).isValue();
                return new BooleanCondition(new LiteralExpr(ScriptValue.ofNumber(v ? 1 : 0)));
            }
            case TRUTHINESS:
                return new BooleanCondition(compileValue(((GraphTruthinessCondition) gc).getValue()));
            default:
                return s -> true;
        }
    }

    // ---------- 值来源物化 ----------

    private void collectValueSourceRefs() {
        for (ScriptGraphNode node : graph.getNodes().values()) {
            for (GraphValue v : node.getInputPins().values()) {
                scanValue(v);
            }
            if (node instanceof IfGraphNode ifn) {
                scanCondition(ifn.getCondition());
            }
            if (node instanceof LoopGraphNode l) {
                scanValue(l.getCount());
            }
            if (node instanceof WaitGraphNode w) {
                scanValue(w.getTicks());
            }
            if (node instanceof EventGraphNode e) {
                scanValue(e.getChannel());
            }
        }
    }

    private void scanValue(GraphValue v) {
        if (v == null || !v.isRef()) {
            return;
        }
        ScriptGraphNode src = graph.node(v.getRef().getNodeId());
        if (src != null && src.getType() == GraphNodeType.VALUE_SOURCE) {
            valueSourceVars.putIfAbsent(src.getId(), tempVarFor(src.getId()));
        }
    }

    private void scanCondition(GraphCondition gc) {
        if (gc == null) {
            return;
        }
        switch (gc.getType()) {
            case COMPARE -> {
                GraphCompareCondition n = (GraphCompareCondition) gc;
                scanValue(n.getLeft());
                scanValue(n.getRight());
            }
            case LOGICAL -> {
                for (GraphCondition op : ((GraphLogicalCondition) gc).getOperands()) {
                    scanCondition(op);
                }
            }
            case NOT -> scanCondition(((GraphNotCondition) gc).getOperand());
            case TRUTHINESS -> scanValue(((GraphTruthinessCondition) gc).getValue());
            case BOOLEAN -> {
            }
        }
    }

    private ScriptNode buildValueSourceMaterializers() {
        ScriptNode head = null;
        ScriptNode tail = null;
        for (ScriptGraphNode node : orderValueSources()) {
            AssignNode an = new AssignNode(tempVarFor(node.getId()),
                valueSourceResolver((ValueSourceGraphNode) node));
            if (head == null) {
                head = an;
                tail = an;
            } else {
                tail.setNext(an);
                tail = an;
            }
        }
        return head;
    }

    /** 值来源节点按依赖拓扑排序（被依赖者先物化）。 */
    private List<ScriptGraphNode> orderValueSources() {
        List<ScriptGraphNode> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (String id : valueSourceVars.keySet()) {
            dfsOrder(id, visited, order);
        }
        return order;
    }

    private void dfsOrder(String id, Set<String> visited, List<ScriptGraphNode> order) {
        if (!visited.add(id)) {
            return;
        }
        ScriptGraphNode node = graph.node(id);
        if (node == null) {
            return;
        }
        for (GraphValue v : node.getInputPins().values()) {
            if (v != null && v.isRef()) {
                ScriptGraphNode dep = graph.node(v.getRef().getNodeId());
                if (dep != null && dep.getType() == GraphNodeType.VALUE_SOURCE) {
                    dfsOrder(dep.getId(), visited, order);
                }
            }
        }
        order.add(node);
    }

    private ValueResolver valueSourceResolver(ValueSourceGraphNode n) {
        return (env, scope) -> evalValueSource(n, env, scope);
    }

    private ScriptValue evalValueSource(ValueSourceGraphNode n, RuntimeEnvironment env, VariableScope scope) {
        switch (n.getSourceKind()) {
            case SELF_PLAYER: {
                Player p = playerFromEnv(env);
                return p != null ? ScriptValue.ofUuid(p.getUUID()) : ScriptValue.nullValue();
            }
            case ENTITY_UUID: {
                Entity e = entityFromEnv(env);
                return e != null ? ScriptValue.ofUuid(e.getUUID()) : ScriptValue.nullValue();
            }
            case ITEM_IN_HAND: {
                Player p = playerFromEnv(env);
                return p != null ? ScriptValue.ofItem(p.getMainHandItem()) : ScriptValue.nullValue();
            }
            case ITEM_NAMESPACE: {
                ScriptValue item = evalPin(n, Pins.ITEM, scope);
                if (item.isItem() && !item.asItem().isEmpty()) {
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(item.asItem().getItem());
                    return ScriptValue.ofString(key.getNamespace());
                }
                return ScriptValue.nullValue();
            }
            default:
                return ScriptValue.nullValue();
        }
    }

    private ScriptValue evalPin(ScriptGraphNode node, String pin, VariableScope scope) {
        GraphValue gv = node.getInputPins().get(pin);
        if (gv == null) {
            return ScriptValue.nullValue();
        }
        return compileValue(gv).eval(scope);
    }

    private ServerPlayer playerFromEnv(RuntimeEnvironment env) {
        CommandSourceStack src = env != null ? env.commandSource() : null;
        return src != null ? src.getPlayer() : null;
    }

    private Entity entityFromEnv(RuntimeEnvironment env) {
        CommandSourceStack src = env != null ? env.commandSource() : null;
        return src != null ? src.getEntity() : null;
    }

    private String tempVarFor(String nodeId) {
        return "__v_" + nodeId;
    }

    // ---------- 工具 ----------

    private ScriptNode appendChain(ScriptNode head, ScriptNode tail) {
        if (tail == null) {
            return head;
        }
        if (head == null) {
            return tail;
        }
        ScriptNode cur = head;
        while (cur.next() != null) {
            cur = cur.next();
        }
        cur.setNext(tail);
        return head;
    }
}