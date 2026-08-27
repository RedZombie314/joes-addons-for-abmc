package cn.autoforged.joes_addons_for_abmc.client.editor;

import cn.autoforged.joes_addons_for_abmc.script.cond.CompareOp;
import cn.autoforged.joes_addons_for_abmc.script.graph.BreakGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.CommandGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.DataOperationGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.EventGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.FunctionCallGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.GraphValue;
import cn.autoforged.joes_addons_for_abmc.script.graph.IfGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.LoopGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.Pins;
import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.ValueSourceGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.VariableGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.WaitGraphNode;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphCompareCondition;
import cn.autoforged.joes_addons_for_abmc.script.graph.cond.GraphTruthinessCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * E 节点属性编辑：双击节点弹出，按节点类型显示可编辑字段。
 * 条件节点以「左值 / 运算符 / 右值」三字段编辑为比较条件。
 */
public class NodePropertyScreen extends Screen {

    private final ScriptGraphNode node;
    private final Runnable onCloseApply;
    /** 点击「完成」后应返回的屏幕（通常为图形编辑器）。为 null 时退回玩家视角。 */
    private final Screen returnTo;

    private final List<EditBox> boxes = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final List<Runnable> appliers = new ArrayList<>();
    private record Candidate(String name, String desc) {
    }
    private final List<Candidate> candidates = new ArrayList<>();
    private double candidatesScroll = 0;
    private int boxY = 40;
    /** 枚举字段对应的输入框：点击候选时自动填入其名称。 */
    private EditBox enumBox;
    /** 候选区在屏幕上的范围（render 时更新，供点击/滚轮命中）。 */
    private int candidateAreaY;
    private int candidateAreaH;
    /** 每个候选行在屏幕上的 y 坐标（render 时更新，越界行为 Integer.MIN_VALUE）。 */
    private final List<Integer> candidateRowYs = new ArrayList<>();

    public NodePropertyScreen(ScriptGraphNode node, Runnable onCloseApply, Screen returnTo) {
        super(Component.translatable("screen.joes_addons_for_abmc.node_props"));
        this.node = node;
        this.onCloseApply = onCloseApply;
        this.returnTo = returnTo;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        boxes.clear();
        labels.clear();
        appliers.clear();
        candidates.clear();
        boxY = 40;
        buildFields();

        int panelW = 340;
        int panelH = boxY + 30;
        int px = this.width / 2 - panelW / 2;
        int py = 30;

        // 浅色标题
        this.addRenderableWidget(Button.builder(Component.translatable("screen.joes_addons_for_abmc.node_done"),
            b -> applyAndClose())
            .bounds(px + panelW - 90, panelH + 10, 80, 20).build());

        // 字段渲染在 render() 中手动绘制（EditBox 走组件渲染）
        for (EditBox box : boxes) {
            this.addRenderableWidget(box);
        }
    }

    private void applyAndClose() {
        for (Runnable r : appliers) {
            r.run();
        }
        if (onCloseApply != null) {
            onCloseApply.run();
        }
        if (returnTo != null) {
            Minecraft.getInstance().setScreen(returnTo);
        } else {
            this.onClose();
        }
    }

    private void buildFields() {
        switch (node.getType()) {
            case COMMAND -> {
                CommandGraphNode n = (CommandGraphNode) node;
                addField("命令模板（支持 $(变量) 与 @引脚）", n.getTemplate(), v -> n.setTemplate(v));
            }
            case IF -> buildIfFields((IfGraphNode) node);
            case LOOP -> {
                LoopGraphNode n = (LoopGraphNode) node;
                addField("循环次数（表达式）", gvExpr(n.getCount(), "3"), v -> n.setCount(GraphValue.ofExpr(v)));
            }
            case WAIT -> {
                WaitGraphNode n = (WaitGraphNode) node;
                addField("延迟刻数（表达式）", gvExpr(n.getTicks(), "20"), v -> n.setTicks(GraphValue.ofExpr(v)));
            }
            case EVENT_SEND -> {
                EventGraphNode n = (EventGraphNode) node;
                addField("广播频道", gvExpr(n.getChannel(), "chan"), v -> n.setChannel(GraphValue.ofExpr(v)));
            }
            case EVENT_RECEIVE -> {
                EventGraphNode n = (EventGraphNode) node;
                addField("订阅频道", gvExpr(n.getChannel(), "chan"), v -> n.setChannel(GraphValue.ofExpr(v)));
            }
            case VAR_GET -> {
                VariableGraphNode n = (VariableGraphNode) node;
                addField("变量名", n.getVarName() == null ? "" : n.getVarName(), v -> n.setVarName(v));
            }
            case VAR_SET -> {
                VariableGraphNode n = (VariableGraphNode) node;
                // 变量名与值表达式放在同一编辑界面，方便直接填写要写入的值。
                addField("变量名", n.getVarName() == null ? "" : n.getVarName(), v -> n.setVarName(v));
                GraphValue value = n.getInputPins().get(Pins.VALUE);
                // 若该引脚已通过从值节点连线提供（非表达式），则留空，避免误覆盖连线。
                boolean byRef = value != null && value.getExpr() == null;
                EditBox exprBox = addField("值表达式（从值节点连线时留空）", byRef ? "" : gvExpr(value, ""));
                exprBox.setY(exprBox.getY() + 7);
                appliers.add(() -> {
                    if (!exprBox.getValue().isBlank()) {
                        n.bindInput(Pins.VALUE, GraphValue.ofExpr(exprBox.getValue()));
                    }
                });
            }
            case FUNCTION_CALL -> {
                FunctionCallGraphNode n = (FunctionCallGraphNode) node;
                addField("函数名", n.getFunctionName() == null ? "" : n.getFunctionName(), v -> n.setFunctionName(v));
            }
            case ARRAY_OP, SET_OP, CONVERT -> {
                DataOperationGraphNode n = (DataOperationGraphNode) node;
                addField("操作", n.getOpKind().name(), v -> {
                    try {
                        n.setOpKind(DataOperationGraphNode.DataOpKind.valueOf(v.trim().toUpperCase()));
                    } catch (Exception ignored) {
                    }
                });
                enumBox = boxes.get(0);
                addField("结果变量（可空）", n.getResultVar() == null ? "" : n.getResultVar(), v -> n.setResultVar(v));
                setCandidates(DataOperationGraphNode.DataOpKind.values());
            }
            case VALUE_SOURCE -> {
                ValueSourceGraphNode n = (ValueSourceGraphNode) node;
                addField("来源", n.getSourceKind().name(), v -> {
                    try {
                        n.setSourceKind(ValueSourceGraphNode.ValueSourceKind.valueOf(v.trim().toUpperCase()));
                    } catch (Exception ignored) {
                    }
                });
                enumBox = boxes.get(0);
                setCandidates(ValueSourceGraphNode.ValueSourceKind.values());
            }
            case BREAK -> {
                // BreakGraphNode 无属性
            }
            default -> {
            }
        }
    }

    private void buildIfFields(IfGraphNode n) {
        GraphCompareCondition c = null;
        if (n.getCondition() instanceof GraphCompareCondition cc) {
            c = cc;
        }
        EditBox leftBox = addField("左值（表达式/变量）", c != null ? gvExpr(c.getLeft(), "") : "");
        EditBox opBox = addField("运算符（EQ/NE/GT/LT/GE/LE）", c != null && c.getOp() != null ? c.getOp().name() : "EQ");
        EditBox rightBox = addField("右值（表达式/变量）", c != null ? gvExpr(c.getRight(), "") : "");
        appliers.add(() -> {
            GraphCompareCondition cond = new GraphCompareCondition();
            cond.setLeft(GraphValue.ofExpr(leftBox.getValue()));
            try {
                cond.setOp(CompareOp.valueOf(opBox.getValue().trim().toUpperCase()));
            } catch (Exception e) {
                cond.setOp(CompareOp.EQ);
            }
            cond.setRight(GraphValue.ofExpr(rightBox.getValue()));
            n.setCondition(cond);
        });
    }

    private EditBox addField(String label, String initial) {
        labels.add(label);
        EditBox box = new EditBox(this.font, this.width / 2 - 150, boxY, 300, 18, Component.literal(label));
        box.setValue(initial == null ? "" : initial);
        box.setMaxLength(32500);
        boxes.add(box);
        // EditBox 高 18，此处 +38 使相邻输入框之间的间距为 20 像素。
        boxY += 38;
        return box;
    }

    private void addField(String label, String initial, java.util.function.Consumer<String> apply) {
        EditBox box = addField(label, initial);
        appliers.add(() -> apply.accept(box.getValue()));
    }

    /** 设置需要在面板底部展示的枚举候选值（每个候选附带中文说明）。 */
    private void setCandidates(Enum<?>[] values) {
        candidates.clear();
        for (Enum<?> e : values) {
            candidates.add(new Candidate(e.name(), candidateDesc(e)));
        }
    }

    /** 为枚举候选生成简短中文说明，帮助玩家理解每个选项的含义。 */
    private static String candidateDesc(Enum<?> e) {
        if (e instanceof ValueSourceGraphNode.ValueSourceKind kind) {
            return switch (kind) {
                case ITEM_IN_HAND -> "手持物品（主手）";
                case ITEM_IN_SLOT -> "指定槽位物品（需连接槽位值）";
                case ITEM_NAMESPACE -> "物品命名空间（如 minecraft:iron_ingot）";
                case ENTITY_UUID -> "实体UUID（需连接实体）";
                case SELECTED_ENTITY -> "选中的实体（右键目标）";
                case SELF_PLAYER -> "自己（玩家）";
            };
        }
        if (e instanceof DataOperationGraphNode.DataOpKind kind) {
            return switch (kind) {
                case ARRAY_GET -> "数组取值（array[index]）";
                case ARRAY_SET -> "数组设值（array[index]=element）";
                case ARRAY_LENGTH -> "数组长度（array.length）";
                case ARRAY_APPEND -> "数组追加（array.add）";
                case SET_ADD -> "集合添加（set.add）";
                case SET_REMOVE -> "集合移除（set.remove）";
                case SET_CONTAINS -> "集合包含（set.contains）";
                case TO_STRING -> "转为字符串";
                case TO_NUMBER -> "转为数字";
                case TO_UUID -> "转为UUID";
                case TO_ITEM -> "转为物品";
            };
        }
        return e.name();
    }

    private static String gvExpr(GraphValue v, String def) {
        if (v == null) {
            return def;
        }
        String e = v.getExpr();
        return e != null ? e : def;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int px = this.width / 2 - 150;
        Font f = this.font;
        for (int i = 0; i < labels.size(); i++) {
            guiGraphics.drawString(f, labels.get(i), px, boxes.get(i).getY() - 12, 0xA0A0A0);
        }
        guiGraphics.drawCenteredString(f, this.title, this.width / 2, 12, 0xFFFFFF);
        // 在面板底部展示候选值：每个选项占一行，右侧为说明；候选过多时支持滚轮滑动 + 滑条。
        if (!candidates.isEmpty()) {
            int panelH = boxY + 30;
            int candX = px;
            int candW = 340;
            int rowH = 16;
            int visibleCount = 5;
            int viewportH = visibleCount * rowH;
            int candStartY = Math.min(panelH + 42, this.height - viewportH - 8);
            int totalH = candidates.size() * rowH;
            int maxScroll = Math.max(0, totalH - viewportH);
            candidatesScroll = Math.max(0, Math.min(maxScroll, candidatesScroll));
            candidateAreaY = candStartY;
            candidateAreaH = viewportH;

            guiGraphics.drawString(f, "候选（点击填入）：", candX, candStartY - 12, 0x909090);
            guiGraphics.fill(candX, candStartY, candX + candW, candStartY + viewportH, 0x802A2A34);
            guiGraphics.enableScissor(candX, candStartY, candX + candW, candStartY + viewportH);
            candidateRowYs.clear();
            int y = candStartY - (int) candidatesScroll;
            for (Candidate c : candidates) {
                if (y + rowH < candStartY || y > candStartY + viewportH) {
                    candidateRowYs.add(Integer.MIN_VALUE);
                    y += rowH;
                    continue;
                }
                boolean hover = mouseY >= y && mouseY < y + rowH && mouseX >= candX && mouseX < candX + candW;
                if (hover) {
                    guiGraphics.fill(candX, y, candX + candW, y + rowH, 0xFF41414F);
                }
                guiGraphics.drawString(f, c.name(), candX + 4, y + 3, 0x90E090);
                guiGraphics.drawString(f, c.desc(), candX + 115, y + 3, 0xC0C0C0);
                candidateRowYs.add(y);
                y += rowH;
            }
            guiGraphics.disableScissor();
            // 滑条：候选超出可视区时显示
            if (totalH > viewportH) {
                int trackH = viewportH;
                int thumbH = Math.max(8, (int) ((double) viewportH / totalH * trackH));
                int thumbY = candStartY + (int) ((double) candidatesScroll / maxScroll * (trackH - thumbH));
                guiGraphics.fill(candX + candW - 3, candStartY, candX + candW, candStartY + trackH, 0xFF33333D);
                guiGraphics.fill(candX + candW - 3, thumbY, candX + candW, thumbY + thumbH, 0xFF808090);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // 点击候选行：把选项名称填入枚举字段输入框
        if (button == 0 && enumBox != null && !candidates.isEmpty()) {
            int candX = this.width / 2 - 150;
            if (mouseX >= candX && mouseX <= candX + 340
                && mouseY >= candidateAreaY && mouseY < candidateAreaY + candidateAreaH) {
                for (int i = 0; i < candidates.size(); i++) {
                    int ry = candidateRowYs.get(i);
                    if (mouseY >= ry && mouseY < ry + 16) {
                        enumBox.setValue(candidates.get(i).name());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 鼠标悬停在候选区时滚动候选列表，而不是滚动父级
        if (!candidates.isEmpty()) {
            int candX = this.width / 2 - 150;
            if (mouseX >= candX && mouseX <= candX + 340
                && mouseY >= candidateAreaY && mouseY < candidateAreaY + candidateAreaH) {
                int totalH = candidates.size() * 16;
                int maxScroll = Math.max(0, totalH - candidateAreaH);
                candidatesScroll = Math.max(0, Math.min(maxScroll, candidatesScroll - scrollY * 12));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}