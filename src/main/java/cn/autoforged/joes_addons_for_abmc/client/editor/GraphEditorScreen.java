package cn.autoforged.joes_addons_for_abmc.client.editor;

import cn.autoforged.joes_addons_for_abmc.network.ScriptFunctionPayload;
import cn.autoforged.joes_addons_for_abmc.network.ScriptGraphPayload;
import cn.autoforged.joes_addons_for_abmc.network.ScriptRunPayload;
import cn.autoforged.joes_addons_for_abmc.network.ScriptNetworking;
import cn.autoforged.joes_addons_for_abmc.script.graph.BreakGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.CommandGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.DataOperationGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.EventGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.FunctionCallGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.GraphNodeType;
import cn.autoforged.joes_addons_for_abmc.script.graph.GraphValue;
import cn.autoforged.joes_addons_for_abmc.script.graph.GraphValueRef;
import cn.autoforged.joes_addons_for_abmc.script.graph.IfGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.LoopGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.ProgramEntryGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraph;
import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.ValueSourceGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.VariableGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.WaitGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.serialize.ScriptGraphCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * E 图形程序编辑器主画布：节点渲染、拖拽移动、端口连线、平移缩放、工具栏，
 * 并内嵌节点面板侧栏与变量/广播展示。支持编辑 per-world 程序或全局自定义函数。
 */
@OnlyIn(Dist.CLIENT)
public class GraphEditorScreen extends Screen {

    private static final int NODE_W = 180;
    private static final int HEADER = 22;
    private static final int LINE_H = 16;
    private static final int SIDEBAR_W = 150;
    /** 侧栏候选区滚动偏移（像素）。 */
    private int sidebarScroll = 0;
    /** 节点绘制/命中顺序，列表末尾在最上层。用于重合节点时正确遮挡与拖动置顶。 */
    private final java.util.List<String> zOrder = new java.util.ArrayList<>();

    private final ScriptGraph graph;
    private final String programId;
    private final boolean functionMode;

    private final GraphEditorState state = new GraphEditorState();
    private Map<String, ScriptGraph> functionsCache = Map.of();

    private boolean showPalette = true;
    private long lastClickTime;
    private double lastClickX;
    private double lastClickY;
    private long idCounter;

    public GraphEditorScreen(ScriptGraph graph, String programId, boolean functionMode) {
        super(Component.translatable("screen.joes_addons_for_abmc.graph_editor"));
        this.graph = graph;
        this.programId = programId;
        this.functionMode = functionMode;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        try {
            functionsCache = new java.util.HashMap<>();
            for (ScriptGraph g : ScriptGraphCodec.fromJsonCollection(ScriptNetworking.clientFunctionsJson)) {
                if (g != null && g.getId() != null) {
                    functionsCache.put(g.getId(), g);
                }
            }
        } catch (Exception e) {
            functionsCache = Map.of();
        }

        int y = 10;
        addButton("运行", y, b -> {
            if (programId != null && !programId.isBlank()) {
                // 携带当前图形快照运行，服务端直接执行，无需先手动保存
                PacketDistributor.sendToServer(ScriptRunPayload.runWithGraph(programId, ScriptGraphCodec.toJson(graph)));
            }
        });
        y += 22;
        addButton("停止", y, b -> PacketDistributor.sendToServer(ScriptRunPayload.stop("")));
        y += 22;
        addButton("保存", y, b -> saveGraph());
        y += 22;
        addButton("加载", y, b -> loadFirstProgram());
        y += 22;
        addButton("清空", y, b -> graph.getNodes().clear());
        y += 22;
        addButton("删节点", y, b -> deleteSelected());
        y += 22;
        addButton("函数管理", y, b -> Minecraft.getInstance().setScreen(new FunctionManagerScreen(this)));
    }

    private void addButton(String label, int y, Button.OnPress onPress) {
        this.addRenderableWidget(Button.builder(Component.literal(label), onPress)
            .bounds(10, y, 70, 20).build());
    }

    private void saveGraph() {
        if (functionMode) {
            ScriptGraph fn = new ScriptGraph(programId, programId);
            fn.setParameters(graph.getParameters());
            fn.setEntryNodeId(graph.getEntryNodeId());
            fn.getNodes().putAll(graph.getNodes());
            PacketDistributor.sendToServer(ScriptFunctionPayload.update(programId, ScriptGraphCodec.toJson(fn)));
        } else {
            PacketDistributor.sendToServer(ScriptGraphPayload.save(programId, ScriptGraphCodec.toJson(graph)));
        }
    }

    private void loadFirstProgram() {
        List<ScriptGraph> programs = ScriptGraphCodec.fromJsonCollection(ScriptNetworking.clientProgramsJson);
        if (!programs.isEmpty()) {
            ScriptGraph g = programs.get(0);
            graph.getNodes().clear();
            graph.getNodes().putAll(g.getNodes());
            graph.setEntryNodeId(g.getEntryNodeId());
        }
    }

    @Override
    public void onClose() {
        // 关闭图形编辑器时自动保存草稿，确保按 Esc 返回玩家视角也不丢失编辑进度
        saveGraph();
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 按下 Del 键删除当前高亮选中的节点
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            deleteSelected();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void deleteSelected() {
        if (state.selectedNodeId == null) {
            return;
        }
        graph.getNodes().remove(state.selectedNodeId);
        disconnectReferences(state.selectedNodeId);
        state.selectedNodeId = null;
    }

    private void disconnectReferences(String removedId) {
        for (ScriptGraphNode n : graph.getNodes().values()) {
            if (removedId.equals(n.getNextId())) {
                n.setNextId(null);
            }
            if (n instanceof IfGraphNode ifn) {
                if (removedId.equals(ifn.getTrueNextId())) ifn.setTrueNextId(null);
                if (removedId.equals(ifn.getFalseNextId())) ifn.setFalseNextId(null);
            }
            if (n instanceof LoopGraphNode loop) {
                if (removedId.equals(loop.getBodyStartId())) loop.setBodyStartId(null);
            }
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, GraphValue> e : n.getInputPins().entrySet()) {
                if (e.getValue() != null && e.getValue().isRef()
                    && removedId.equals(e.getValue().getRef().getNodeId())) {
                    toRemove.add(e.getKey());
                }
            }
            for (String pin : toRemove) {
                n.getInputPins().remove(pin);
            }
        }
        if (removedId.equals(graph.getEntryNodeId())) {
            graph.setEntryNodeId(null);
        }
    }

    // ================= 渲染 =================

    /**
     * 禁用默认背景绘制：默认实现会在有世界时把当前画面高斯模糊后作为背景，
     * 导致画布网格与右侧节点面板显得发虚。编辑器自绘不透明纯色背景。
     */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 空实现：完全交由 render() 绘制不透明背景
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 半透明背景：透出世界画面，玩家能在编辑时看到命令运行的实际效果。
        // 已在 renderBackground 禁用默认高斯模糊路径，因此透出的是清晰世界画面。
        g.fill(0, 0, this.width, this.height, 0x6614141C);
        state.mouseWorldX = state.toWorldX(mouseX);
        state.mouseWorldY = state.toWorldY(mouseY);

        drawGrid(g);
        drawConnections(g);
        // 裁剪节点绘制范围，排除左侧按钮带与右侧候选栏，确保节点文字不会绘制到这些 UI 区域之上。
        int clipLeft = 86;   // 左侧按钮带右边界
        int clipTop = 8;     // 顶部提示区下边界
        int clipW = Math.max(1, this.width - SIDEBAR_W - clipLeft);
        g.enableScissor(clipLeft, clipTop, clipLeft + clipW, this.height);
        refreshZOrder();
        for (String id : zOrder) {
            ScriptGraphNode n = graph.getNodes().get(id);
            if (n != null) {
                drawNode(g, n);
                // 立即提交该节点的矩形与文字缓冲：fill 与 drawString 分属不同 RenderType，
                // 若不在此提交，所有节点的文字会整体排在所有矩形之上，导致下层节点文字
                // 覆盖上层节点矩形。逐节点 flush 使上层节点完整遮挡下层（含其文字）。
                g.flush();
            }
        }
        g.disableScissor();
        drawPendingLink(g, mouseX, mouseY);
        // 提交画布内容（含节点文字）的材质缓冲，使后续 UI 覆盖层（侧栏、按钮）正确绘制在其上。
        g.flush();
        drawSidebar(g);
        drawToolbarLabels(g);
        renderChatHistory(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** 手动渲染聊天历史，使 say 等命令的输出在编辑器界面中可见。 */
    private void renderChatHistory(GuiGraphics g, int mouseX, int mouseY) {
        if (this.minecraft.gui == null) {
            return;
        }
        net.minecraft.client.gui.components.ChatComponent chat = this.minecraft.gui.getChat();
        if (chat != null) {
            chat.render(g, this.minecraft.gui.getGuiTicks(), mouseX, mouseY, false);
        }
    }

    private void drawToolbarLabels(GuiGraphics g) {
        g.drawCenteredString(this.font,
            Component.literal("后缀: 拖拽移动 / 滚轮缩放 / 拖端口连线 / 双击编辑"),
            this.width / 2, 3, 0x808080);
    }

    private void drawGrid(GuiGraphics g) {
        int step = 24;
        int startX = (int) (state.panX % step);
        int startY = (int) (state.panY % step);
        for (int x = (int) startX - step; x < this.width; x += step) {
            g.vLine(x, 0, this.height, 0x803A3A48);
        }
        for (int y = (int) startY - step; y < this.height; y += step) {
            g.hLine(0, this.width, y, 0x803A3A48);
        }
    }

    // ---- 端口与矩形 ----

    private record Port(String name, double wx, double wy, boolean output) {
    }

    private double nodeHeight(ScriptGraphNode n) {
        int ins = effectiveInputs(n).size();
        int outs = NodePorts.logicOutputs(n).size() + (NodePorts.hasValueOutput(n) ? 1 : 0);
        // 顶部 1 行留给左侧逻辑输入端口，其下依次为值输入引脚
        return HEADER + (ins + 1) * LINE_H + 8 + outs * LINE_H + 6;
    }

    private List<String> effectiveInputs(ScriptGraphNode n) {
        List<String> pins = new ArrayList<>(NodePorts.valueInputs(n));
        if (n instanceof FunctionCallGraphNode fc && fc.getFunctionName() != null) {
            ScriptGraph target = functionsCache.get(fc.getFunctionName());
            if (target != null) {
                for (String p : target.getParameters()) {
                    if (!pins.contains(p)) pins.add(p);
                }
            }
        }
        return pins;
    }

    private List<Port> computePorts(ScriptGraphNode n) {
        List<Port> ports = new ArrayList<>();
        List<String> ins = effectiveInputs(n);
        double y = n.getY() + HEADER;
        // 左侧顶部逻辑输入端口（其它节点的逻辑输出连入此处实现顺序执行）
        ports.add(new Port("in", n.getX() - 6, y + 8, false));
        for (int i = 0; i < ins.size(); i++) {
            ports.add(new Port(ins.get(i), n.getX() - 6, y + (i + 1) * LINE_H + 8, false));
        }
        double outY = y + (ins.size() + 1) * LINE_H + 8;
        if (NodePorts.hasValueOutput(n)) {
            ports.add(new Port("value", n.getX() + NODE_W + 6, outY, true));
            outY += LINE_H;
        }
        for (String lo : NodePorts.logicOutputs(n)) {
            ports.add(new Port(lo, n.getX() + NODE_W + 6, outY, true));
            outY += LINE_H;
        }
        return ports;
    }

    private Port logicInputPort(ScriptGraphNode target) {
        return new Port("in", target.getX() - 6, target.getY() + HEADER + 8, false);
    }

    private boolean inNode(ScriptGraphNode n, double wx, double wy) {
        return wx >= n.getX() && wx <= n.getX() + NODE_W
            && wy >= n.getY() && wy <= n.getY() + nodeHeight(n);
    }

    private ScriptGraphNode hitNode(double wx, double wy) {
        // 从最上层节点开始命中（zOrder 末尾）
        for (int i = zOrder.size() - 1; i >= 0; i--) {
            ScriptGraphNode n = graph.getNodes().get(zOrder.get(i));
            if (n != null && inNode(n, wx, wy)) {
                return n;
            }
        }
        return null;
    }

    private Port hitPort(ScriptGraphNode n, double wx, double wy) {
        for (Port p : computePorts(n)) {
            double dx = wx - p.wx();
            double dy = wy - p.wy();
            if (dx * dx + dy * dy <= 49) {
                return p;
            }
        }
        return null;
    }

    private record HitResult(ScriptGraphNode node, Port port) {
    }

    private HitResult hitTarget(double wx, double wy) {
        // 从最上层节点开始命中端口
        for (int i = zOrder.size() - 1; i >= 0; i--) {
            ScriptGraphNode n = graph.getNodes().get(zOrder.get(i));
            if (n == null) {
                continue;
            }
            Port p = hitPort(n, wx, wy);
            if (p != null) {
                return new HitResult(n, p);
            }
        }
        return null;
    }

    /** 使 zOrder 与当前节点集合同步：保留既有顺序，追加新增节点，剔除已删除节点。 */
    private void refreshZOrder() {
        zOrder.retainAll(graph.getNodes().keySet());
        for (String id : graph.getNodes().keySet()) {
            if (!zOrder.contains(id)) {
                zOrder.add(id);
            }
        }
    }

    /** 将指定节点提升到最上层（绘制/命中顺序末尾）。 */
    private void bringToFront(String id) {
        zOrder.remove(id);
        zOrder.add(id);
    }

    // ---- 节点绘制 ----

    private void drawNode(GuiGraphics g, ScriptGraphNode n) {
        double sx = state.toScreenX(n.getX());
        double sy = state.toScreenY(n.getY());
        double w = NODE_W * state.zoom;
        double h = nodeHeight(n) * state.zoom;
        boolean selected = n.getId().equals(state.selectedNodeId);

        int bg = selected ? 0xFF3A3A4A : 0xFF2E2E3C;
        int border = selected ? 0xFF7AD0FF : 0xFF555566;
        g.fill((int) sx, (int) sy, (int) (sx + w), (int) (sy + h), border);
        g.fill((int) sx + 1, (int) sy + 1, (int) (sx + w - 1), (int) (sy + h - 1), bg);
        g.fill((int) sx + 1, (int) sy + 1, (int) (sx + w - 1), (int) (sy + HEADER * state.zoom + 1), 0xFF4A5060);

        // 标题文字渲染在节点矩形上方，使用固定屏幕像素偏移（不随缩放缩放），
        // 因此缩小窗口时节点虽变小，标题仍清晰可见、不会消失。
        String title = nodeTitle(n);
        g.drawCenteredString(this.font, title, (int) (sx + w / 2), (int) (sy - 12), 0xFFFFFF);

        // 左侧逻辑输入端口（顶部，蓝色）：其它节点的逻辑输出连入实现顺序执行
        double linY = state.toScreenY(n.getY() + HEADER + 8);
        g.fill((int) sx, (int) linY - 3, (int) sx + 7, (int) linY + 3, 0xFF66BBEE);
        if (state.zoom > 0.5) {
            g.drawString(this.font, "in", (int) sx + 8, (int) linY - 4, 0x90A0B0);
        }
        // 值输入引脚（逻辑输入端口下方）
        List<String> ins = effectiveInputs(n);
        for (int i = 0; i < ins.size(); i++) {
            double py = state.toScreenY(n.getY() + HEADER + (i + 1) * LINE_H + 8);
            g.fill((int) sx, (int) py - 1, (int) sx + 6, (int) py + 1, 0xFF66BB66);
            if (state.zoom > 0.5) {
                g.drawString(this.font, ins.get(i), (int) sx + 8, (int) py - 4, 0xB0D0B0);
            }
        }
        // 右侧输出端口
        for (Port p : computePorts(n)) {
            if (!p.output()) continue;
            double px = state.toScreenX(p.wx());
            double py = state.toScreenY(p.wy());
            g.fill((int) px - 3, (int) py - 3, (int) px + 3, (int) py + 3, 0xFF66BBEE);
            if (state.zoom > 0.5) {
                g.drawString(this.font, p.name(), (int) px + 6, (int) py - 4, 0x90A0B0);
            }
        }
    }

    private String nodeTitle(ScriptGraphNode n) {
        return switch (n.getType()) {
            case PROGRAM_ENTRY -> "当点击运行时";
            case COMMAND -> "命令";
            case IF -> "条件分支";
            case LOOP -> "循环";
            case WAIT -> "延迟";
            case BREAK -> "跳出循环";
            case EVENT_SEND -> "广播发送";
            case EVENT_RECEIVE -> "广播接收";
            case FUNCTION_CALL -> "调用函数";
            case VAR_GET -> "读取变量";
            case VAR_SET -> "写入变量";
            case ARRAY_OP -> "数组操作";
            case SET_OP -> "集合操作";
            case CONVERT -> "类型转换";
            case VALUE_SOURCE -> "值来源";
        };
    }

    private void drawConnections(GuiGraphics g) {
        for (ScriptGraphNode n : graph.getNodes().values()) {
            for (Port out : computePorts(n)) {
                if (!out.output()) continue;
                String targetId = logicTarget(n, out.name());
                if (targetId == null) continue;
                ScriptGraphNode target = graph.getNodes().get(targetId);
                if (target == null) continue;
                Port tin = logicInputPort(target);
                drawBezier(g,
                    state.toScreenX(out.wx()), state.toScreenY(out.wy()),
                    state.toScreenX(tin.wx()), state.toScreenY(tin.wy()), 0xFF66BBEE);
            }
            for (Map.Entry<String, GraphValue> e : n.getInputPins().entrySet()) {
                GraphValue v = e.getValue();
                if (v == null || !v.isRef()) continue;
                ScriptGraphNode source = graph.getNodes().get(v.getRef().getNodeId());
                if (source == null) continue;
                Port sp = valueOutputPort(source);
                Port tp = inputPort(n, e.getKey());
                if (sp == null || tp == null) continue;
                drawBezier(g,
                    state.toScreenX(sp.wx()), state.toScreenY(sp.wy()),
                    state.toScreenX(tp.wx()), state.toScreenY(tp.wy()), 0xFFEEDF66);
            }
        }
    }

    private String logicTarget(ScriptGraphNode n, String port) {
        return switch (port) {
            case "next" -> n.getNextId();
            case "true" -> n instanceof IfGraphNode ifn ? ifn.getTrueNextId() : null;
            case "false" -> n instanceof IfGraphNode ifn ? ifn.getFalseNextId() : null;
            case "body" -> n instanceof LoopGraphNode loop ? loop.getBodyStartId() : null;
            default -> null;
        };
    }

    private Port valueOutputPort(ScriptGraphNode n) {
        if (!NodePorts.hasValueOutput(n)) return null;
        for (Port p : computePorts(n)) {
            if (p.name().equals("value") && p.output()) return p;
        }
        return null;
    }

    private Port inputPort(ScriptGraphNode n, String pinName) {
        for (Port p : computePorts(n)) {
            if (p.name().equals(pinName) && !p.output()) return p;
        }
        return null;
    }

    private void drawPendingLink(GuiGraphics g, int mouseX, int mouseY) {
        if (state.drag != GraphEditorState.Drag.LOGIC_LINK
            && state.drag != GraphEditorState.Drag.VALUE_LINK) {
            return;
        }
        ScriptGraphNode src = graph.getNodes().get(state.linkSourceNode);
        double wx = state.toScreenX(src.getX() + NODE_W + 6);
        double wy = state.toScreenY(src.getY() + HEADER + 8);
        drawBezier(g, wx, wy, mouseX, mouseY, state.linkIsValue ? 0xFFEEDF66 : 0xFF66BBEE);
    }

    private void drawBezier(GuiGraphics g, double x1, double y1, double x2, double y2, int color) {
        double cx = (x1 + x2) / 2;
        int steps = 24;
        double prevX = x1, prevY = y1;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double bx = (1 - t) * (1 - t) * x1 + 2 * (1 - t) * t * cx + t * t * x2;
            double by = (1 - t) * (1 - t) * y1 + 2 * (1 - t) * t * y1 + t * t * y2;
            int x0 = (int) Math.round(prevX), y0 = (int) Math.round(prevY);
            int x1i = (int) Math.round(bx), y1i = (int) Math.round(by);
            g.hLine(Math.min(x0, x1i), Math.max(x0, x1i), y0, color);
            g.vLine(x1i, Math.min(y0, y1i), Math.max(y0, y1i), color);
            prevX = bx;
            prevY = by;
        }
    }

    private void drawSidebar(GuiGraphics g) {
        if (!showPalette) return;
        int x = this.width - SIDEBAR_W;
        g.fill(x, 0, this.width, this.height, 0xFF2A2A34);
        g.drawString(this.font, "节点面板", x + 6, 6, 0xFFFFFF);
        // 候选区可视范围为 y∈[24, height]，超出部分可滚动。
        int viewY = 24;
        int contentH = NodePaletteItem.ALL.length * 18;
        int viewH = this.height - viewY;
        int maxScroll = Math.max(0, contentH - viewH);
        sidebarScroll = Math.max(0, Math.min(sidebarScroll, maxScroll));
        g.enableScissor(x, viewY, this.width, this.height);
        int y = viewY - sidebarScroll;
        for (NodePaletteItem item : NodePaletteItem.ALL) {
            g.fill(x + 4, y, x + SIDEBAR_W - 4, y + 16, 0xFF3A3A4A);
            g.drawString(this.font, item.label, x + 8, y + 4, 0xCCCCCC);
            item.y = y;
            y += 18;
        }
        g.disableScissor();
    }

    private enum NodePaletteItem {
        PROGRAM_ENTRY("当点击运行时", GraphNodeType.PROGRAM_ENTRY),
        COMMAND("命令", GraphNodeType.COMMAND),
        IF("条件分支", GraphNodeType.IF),
        LOOP("循环", GraphNodeType.LOOP),
        WAIT("延迟", GraphNodeType.WAIT),
        BREAK("跳出", GraphNodeType.BREAK),
        EVENT_SEND("广播发送", GraphNodeType.EVENT_SEND),
        EVENT_RECEIVE("广播接收", GraphNodeType.EVENT_RECEIVE),
        FUNCTION_CALL("调用函数", GraphNodeType.FUNCTION_CALL),
        VAR_GET("读取变量", GraphNodeType.VAR_GET),
        VAR_SET("写入变量", GraphNodeType.VAR_SET),
        ARRAY_OP("数组操作", GraphNodeType.ARRAY_OP),
        CONVERT("类型转换", GraphNodeType.CONVERT),
        VALUE_SOURCE("值来源", GraphNodeType.VALUE_SOURCE);

        final String label;
        final GraphNodeType type;
        int y;

        NodePaletteItem(String label, GraphNodeType type) {
            this.label = label;
            this.type = type;
        }

        static final NodePaletteItem[] ALL = values();
    }

    // ================= 交互 =================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 先让按钮等可交互组件处理点击；若被处理则直接返回，
        // 避免点击按钮的同时触发画布平移（否则「运行」等按钮不会触发）。
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        // 侧栏命中（节点面板项为非组件，需手动命中）
        if (mouseX >= this.width - SIDEBAR_W) {
            for (NodePaletteItem item : NodePaletteItem.ALL) {
                if (mouseX >= this.width - SIDEBAR_W + 4 && mouseX <= this.width - 4
                    && mouseY >= item.y && mouseY <= item.y + 16) {
                    addNode(item.type);
                    return true;
                }
            }
            return false;
        }

        boolean doubleClick = isDoubleClick(mouseX, mouseY);
        double wx = state.toWorldX(mouseX);
        double wy = state.toWorldY(mouseY);

        // 端口命中优先
        HitResult hr = hitTarget(wx, wy);
        if (hr != null && hr.port() != null) {
            if (hr.port().output()) {
                startLink(hr.node(), hr.port());
                return true;
            } else {
                // 点击输入端口：清除该引脚绑定
                hr.node().getInputPins().remove(hr.port().name());
                return true;
            }
        }

        ScriptGraphNode node = hitNode(wx, wy);
        if (node != null) {
            state.selectedNodeId = node.getId();
            if (doubleClick) {
                Minecraft.getInstance().setScreen(new NodePropertyScreen(node, () -> { }, GraphEditorScreen.this));
                return true;
            }
            // 拖动该节点时将其提升到最上层，并作为本次拖动的目标
            bringToFront(node.getId());
            state.drag = GraphEditorState.Drag.MOVE_NODE;
            state.moveNodeId = node.getId();
            state.moveOffsetX = wx - node.getX();
            state.moveOffsetY = wy - node.getY();
            return true;
        }

        state.selectedNodeId = null;
        state.drag = GraphEditorState.Drag.PAN;
        state.panStartX = mouseX;
        state.panStartY = mouseY;
        return true;
    }

    private void startLink(ScriptGraphNode node, Port out) {
        state.linkSourceNode = node.getId();
        state.linkSourcePort = out.name();
        state.linkIsValue = out.name().equals("value");
        state.drag = state.linkIsValue ? GraphEditorState.Drag.VALUE_LINK : GraphEditorState.Drag.LOGIC_LINK;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double wx = state.toWorldX(mouseX);
        double wy = state.toWorldY(mouseY);
        switch (state.drag) {
            case MOVE_NODE -> {
                ScriptGraphNode n = graph.getNodes().get(state.moveNodeId);
                if (n != null) {
                    n.setX(wx - state.moveOffsetX);
                    n.setY(wy - state.moveOffsetY);
                }
            }
            case PAN -> {
                state.panX += mouseX - state.panStartX;
                state.panY += mouseY - state.panStartY;
                state.panStartX = mouseX;
                state.panStartY = mouseY;
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (state.drag == GraphEditorState.Drag.LOGIC_LINK
            || state.drag == GraphEditorState.Drag.VALUE_LINK) {
            double wx = state.toWorldX(mouseX);
            double wy = state.toWorldY(mouseY);
            finishLink(wx, wy);
        }
        state.drag = GraphEditorState.Drag.NONE;
        return true;
    }

    private void finishLink(double wx, double wy) {
        ScriptGraphNode source = graph.getNodes().get(state.linkSourceNode);
        if (source == null) return;
        HitResult hr = hitTarget(wx, wy);
        if (hr == null || hr.node() == null) return;
        ScriptGraphNode target = hr.node();
        if (target.getId().equals(source.getId())) return;

        if (state.linkIsValue) {
            // 目标必须是值输入引脚
            if (hr.port() != null && !hr.port().output()) {
                target.bindInput(hr.port().name(),
                    GraphValue.ofRef(new GraphValueRef(source.getId(), "value")));
            }
        } else {
            // 逻辑连线：目标节点逻辑输入
            setLogicTarget(source, state.linkSourcePort, target.getId());
        }
    }

    private void setLogicTarget(ScriptGraphNode source, String port, String targetId) {
        switch (port) {
            case "next" -> source.setNextId(targetId);
            case "true" -> { if (source instanceof IfGraphNode ifn) ifn.setTrueNextId(targetId); }
            case "false" -> { if (source instanceof IfGraphNode ifn) ifn.setFalseNextId(targetId); }
            case "body" -> { if (source instanceof LoopGraphNode loop) loop.setBodyStartId(targetId); }
            default -> { }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 鼠标悬停在右侧候选区时，滚动候选区而非缩放画布。
        if (mouseX >= this.width - SIDEBAR_W) {
            int contentH = NodePaletteItem.ALL.length * 18;
            int viewH = this.height - 24;
            int maxScroll = Math.max(0, contentH - viewH);
            sidebarScroll = (int) Math.max(0, Math.min(maxScroll, sidebarScroll - scrollY * 12));
            return true;
        }
        double factor = scrollY > 0 ? 1.1 : 1 / 1.1;
        double newZoom = state.zoom * factor;
        if (newZoom < 0.3) newZoom = 0.3;
        if (newZoom > 3.0) newZoom = 3.0;
        // 以鼠标为中心缩放
        double wx = state.toWorldX(mouseX);
        double wy = state.toWorldY(mouseY);
        state.zoom = newZoom;
        state.panX = mouseX - wx * state.zoom;
        state.panY = mouseY - wy * state.zoom;
        return true;
    }

    private boolean isDoubleClick(double mouseX, double mouseY) {
        long now = System.currentTimeMillis();
        boolean d = now - lastClickTime < 400
            && Math.abs(mouseX - lastClickX) < 6
            && Math.abs(mouseY - lastClickY) < 6;
        lastClickTime = now;
        lastClickX = mouseX;
        lastClickY = mouseY;
        return d;
    }

    private void addNode(GraphNodeType type) {
        double wx = state.toWorldX(this.width / 2);
        double wy = state.toWorldY(this.height / 2);
        ScriptGraphNode n = createNode(type);
        n.setId(newId());
        n.setX(wx - NODE_W / 2);
        n.setY(wy - 20);
        graph.addNode(n);
        // 入口节点不作为默认 entryNodeId（编译时由其输出端决定入口）
        if (type != GraphNodeType.PROGRAM_ENTRY && graph.getEntryNodeId() == null) {
            graph.setEntryNodeId(n.getId());
        }
        state.selectedNodeId = n.getId();
    }

    private String newId() {
        return "n" + (System.currentTimeMillis() % 100000) + "_" + (idCounter++);
    }

    private ScriptGraphNode createNode(GraphNodeType type) {
        return switch (type) {
            case PROGRAM_ENTRY -> new ProgramEntryGraphNode();
            case COMMAND -> {
                CommandGraphNode n = new CommandGraphNode();
                n.setTemplate("say hello");
                yield n;
            }
            case IF -> {
                IfGraphNode n = new IfGraphNode();
                cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphCompareCondition c =
                    new cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphCompareCondition();
                c.setLeft(GraphValue.ofExpr("a"));
                c.setOp(cn.autoforged.joes_addons_for_abmc.script.cond.CompareOp.EQ);
                c.setRight(GraphValue.ofExpr("b"));
                n.setCondition(c);
                yield n;
            }
            case LOOP -> {
                LoopGraphNode n = new LoopGraphNode();
                n.setCount(GraphValue.ofExpr("3"));
                yield n;
            }
            case WAIT -> {
                WaitGraphNode n = new WaitGraphNode();
                n.setTicks(GraphValue.ofExpr("20"));
                yield n;
            }
            case BREAK -> new BreakGraphNode();
            case EVENT_SEND -> {
                EventGraphNode n = new EventGraphNode();
                n.setKind(EventGraphNode.EventKind.SEND);
                n.setChannel(GraphValue.ofExpr("chan"));
                yield n;
            }
            case EVENT_RECEIVE -> {
                EventGraphNode n = new EventGraphNode();
                n.setKind(EventGraphNode.EventKind.RECEIVE);
                n.setChannel(GraphValue.ofExpr("chan"));
                yield n;
            }
            case FUNCTION_CALL -> {
                FunctionCallGraphNode n = new FunctionCallGraphNode();
                n.setFunctionName("");
                yield n;
            }
            case VAR_GET -> {
                VariableGraphNode n = new VariableGraphNode();
                n.setKind(VariableGraphNode.VarOpKind.GET);
                n.setVarName("x");
                yield n;
            }
            case VAR_SET -> {
                VariableGraphNode n = new VariableGraphNode();
                n.setKind(VariableGraphNode.VarOpKind.SET);
                n.setVarName("x");
                yield n;
            }
            case ARRAY_OP -> {
                DataOperationGraphNode n = new DataOperationGraphNode();
                n.setOpKind(DataOperationGraphNode.DataOpKind.ARRAY_GET);
                yield n;
            }
            case CONVERT -> {
                DataOperationGraphNode n = new DataOperationGraphNode();
                n.setOpKind(DataOperationGraphNode.DataOpKind.TO_STRING);
                yield n;
            }
            case SET_OP -> {
                DataOperationGraphNode n = new DataOperationGraphNode();
                n.setOpKind(DataOperationGraphNode.DataOpKind.SET_ADD);
                yield n;
            }
            case VALUE_SOURCE -> {
                ValueSourceGraphNode n = new ValueSourceGraphNode();
                n.setSourceKind(ValueSourceGraphNode.ValueSourceKind.ITEM_IN_HAND);
                yield n;
            }
        };
    }
}