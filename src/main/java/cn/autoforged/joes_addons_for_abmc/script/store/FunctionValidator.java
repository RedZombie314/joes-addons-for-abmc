package cn.autoforged.joes_addons_for_abmc.script.store;

import cn.autoforged.joes_addons_for_abmc.script.graph.FunctionCallGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.GraphValue;
import cn.autoforged.joes_addons_for_abmc.script.graph.IfGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.LoopGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraph;
import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphBooleanLiteralCondition;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphCompareCondition;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphCondition;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphLogicalCondition;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphNotCondition;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphTruthinessCondition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * B5 函数校验器：校验函数图结构、函数间调用关系与循环依赖。
 * <p>
 * 一切校验以返回错误列表的形式给出；空列表表示通过。
 * <ul>
 *   <li>{@link #validateFunction} 校验单个函数图：名称/入口/形参/节点引用完整性；</li>
 *   <li>{@link #validateCallRelations} 校验函数调用：目标存在性、实参匹配；</li>
 *   <li>{@link #detectCycles} 检测函数间循环依赖（避免无出口递归）。</li>
 * </ul>
 */
public final class FunctionValidator {

    private FunctionValidator() {
    }

    /** 校验单个函数图的结构。返回错误列表（空 = 通过）。 */
    public static List<String> validateFunction(ScriptGraph fn) {
        List<String> errors = new ArrayList<>();
        if (fn == null) {
            errors.add("函数为空");
            return errors;
        }
        String name = fn.getName();
        if (name == null || name.isBlank()) {
            errors.add("函数缺少名称");
        }
        Map<String, ScriptGraphNode> nodes = fn.getNodes();
        if (fn.getEntryNodeId() == null || !nodes.containsKey(fn.getEntryNodeId())) {
            errors.add("函数 '" + displayName(fn) + "' 缺少有效的入口节点");
        }
        // 形参合法性
        Set<String> seenParams = new HashSet<>();
        for (String p : fn.getParameters()) {
            if (p == null || p.isBlank()) {
                errors.add("函数 '" + displayName(fn) + "' 存在空形参名");
            } else if (!seenParams.add(p)) {
                errors.add("函数 '" + displayName(fn) + "' 存在重复形参: " + p);
            }
        }
        // 出边/分支/值引用完整性
        for (ScriptGraphNode node : nodes.values()) {
            checkRef(nodes, node.getNextId(), "节点 '" + node.getId() + "' 的 next", errors);
            if (node instanceof IfGraphNode ifn) {
                checkRef(nodes, ifn.getTrueNextId(), "节点 '" + node.getId() + "' 的 true 分支", errors);
                checkRef(nodes, ifn.getFalseNextId(), "节点 '" + node.getId() + "' 的 false 分支", errors);
            }
            if (node instanceof LoopGraphNode loop) {
                checkRef(nodes, loop.getBodyStartId(), "节点 '" + node.getId() + "' 的循环体", errors);
            }
            for (GraphValue v : node.getInputPins().values()) {
                if (v != null && v.isRef()) {
                    checkRef(nodes, v.getRef().getNodeId(), "节点 '" + node.getId() + "' 的值引用", errors);
                }
            }
            if (node instanceof IfGraphNode ifn) {
                collectConditionRefs(ifn.getCondition(), nodes, errors, node.getId());
            }
        }
        // 结构性死代码不在这里判断（可达性由运行时决定），仅保证引用不致崩溃。
        return errors;
    }

    /** 校验函数库整体：先做结构校验，再做调用关系与循环依赖。返回错误列表。 */
    public static List<String> validateLibrary(Map<String, ScriptGraph> functions) {
        List<String> errors = new ArrayList<>();
        if (functions == null || functions.isEmpty()) {
            return errors;
        }
        for (ScriptGraph fn : functions.values()) {
            errors.addAll(validateFunction(fn));
        }
        Map<String, Set<String>> callGraph = new HashMap<>();
        for (Map.Entry<String, ScriptGraph> e : functions.entrySet()) {
            String fname = e.getKey();
            ScriptGraph fn = e.getValue();
            Set<String> callees = new HashSet<>();
            for (ScriptGraphNode node : fn.getNodes().values()) {
                if (!(node instanceof FunctionCallGraphNode fc)) {
                    continue;
                }
                String target = fc.getFunctionName();
                if (target == null || target.isBlank() || !functions.containsKey(target)) {
                    errors.add("函数 '" + fname + "' 调用了不存在的函数 '" + target + "'");
                    continue;
                }
                callees.add(target);
                checkCallArguments(fname, target, fc, functions.get(target).getParameters(), errors);
            }
            callGraph.put(fname, callees);
        }
        detectCycles(callGraph, errors);
        return errors;
    }

    // ---------- 内部工具 ----------

    private static void checkCallArguments(String caller, String target, FunctionCallGraphNode fc,
                                           List<String> params, List<String> errors) {
        Set<String> provided = fc.getInputPins().keySet();
        for (String p : params) {
            if (!provided.contains(p)) {
                errors.add("函数 '" + caller + "' 调用 '" + target + "' 缺少实参 '" + p + "'");
            }
        }
        for (String p : provided) {
            if (!params.contains(p)) {
                errors.add("函数 '" + caller + "' 调用 '" + target + "' 传入了未声明的实参 '" + p + "'");
            }
        }
    }

    private static void checkRef(Map<String, ScriptGraphNode> nodes, String refId, String where, List<String> errors) {
        if (refId != null && !nodes.containsKey(refId)) {
            errors.add("悬空引用: " + where + " 指向不存在的节点 '" + refId + "'");
        }
    }

    private static void collectConditionRefs(GraphCondition cond, Map<String, ScriptGraphNode> nodes,
                                             List<String> errors, String ownerId) {
        if (cond == null) {
            return;
        }
        switch (cond.getType()) {
            case COMPARE -> {
                GraphCompareCondition n = (GraphCompareCondition) cond;
                checkValueRef(n.getLeft(), nodes, ownerId, errors);
                checkValueRef(n.getRight(), nodes, ownerId, errors);
            }
            case LOGICAL -> {
                for (GraphCondition op : ((GraphLogicalCondition) cond).getOperands()) {
                    collectConditionRefs(op, nodes, errors, ownerId);
                }
            }
            case NOT -> collectConditionRefs(((GraphNotCondition) cond).getOperand(), nodes, errors, ownerId);
            case TRUTHINESS -> checkValueRef(((GraphTruthinessCondition) cond).getValue(), nodes, ownerId, errors);
            case BOOLEAN -> {
            }
        }
    }

    private static void checkValueRef(GraphValue v, Map<String, ScriptGraphNode> nodes, String ownerId, List<String> errors) {
        if (v != null && v.isRef()) {
            checkRef(nodes, v.getRef().getNodeId(), "节点 '" + ownerId + "' 的条件值引用", errors);
        }
    }

    /** 三色 DFS 检测函数调用环，并把环路径法写入错误列表（按环成员去重）。 */
    private static void detectCycles(Map<String, Set<String>> callGraph, List<String> errors) {
        Map<String, Integer> state = new HashMap<>();
        Deque<String> path = new ArrayDeque<>();
        // 用排序后的环成员签名去重，避免同一环被重复报告
        java.util.Set<String> reported = new HashSet<>();
        for (String start : callGraph.keySet()) {
            if (state.getOrDefault(start, 0) == 0) {
                dfsCycle(start, callGraph, state, path, errors, reported);
            }
        }
    }

    private static void dfsCycle(String node, Map<String, Set<String>> callGraph, Map<String, Integer> state,
                                 Deque<String> path, List<String> errors, java.util.Set<String> reported) {
        state.put(node, 1); // visiting
        path.push(node);
        for (String callee : callGraph.getOrDefault(node, Set.of())) {
            int st = state.getOrDefault(callee, 0);
            if (st == 1) {
                // 发现环：从 callee 到当前 node 的路径构成闭环
                List<String> cycle = new ArrayList<>();
                boolean found = false;
                for (String s : path) {
                    if (s.equals(callee)) {
                        found = true;
                    }
                    if (found) {
                        cycle.add(s);
                    }
                }
                cycle.add(callee); // 回到起点，形成闭环
                java.util.Collections.reverse(cycle);
                String signature = String.join(",", new java.util.TreeSet<>(cycle));
                if (reported.add(signature)) {
                    errors.add("函数间存在循环依赖: " + String.join(" -> ", cycle));
                }
            } else if (st == 0) {
                dfsCycle(callee, callGraph, state, path, errors, reported);
            }
        }
        state.put(node, 2); // done
        path.pop();
    }

    private static String displayName(ScriptGraph fn) {
        return fn.getName() != null ? fn.getName() : "(未命名)";
    }
}