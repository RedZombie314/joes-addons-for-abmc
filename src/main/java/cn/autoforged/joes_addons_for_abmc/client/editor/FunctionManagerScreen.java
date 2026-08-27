package cn.autoforged.joes_addons_for_abmc.client.editor;

import cn.autoforged.joes_addons_for_abmc.network.ScriptFunctionPayload;
import cn.autoforged.joes_addons_for_abmc.network.ScriptNetworking;
import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraph;
import cn.autoforged.joes_addons_for_abmc.script.graph.serialize.ScriptGraphCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * E 函数管理界面：列出全局自定义函数，支持新建 / 编辑 / 删除，并回到图形编辑器。
 */
@OnlyIn(Dist.CLIENT)
public class FunctionManagerScreen extends Screen {

    private final Screen parent;
    private EditBox nameEdit;
    private List<String> functionNames = new ArrayList<>();
    private int listTop = 50;
    private int selectedIndex = -1;

    public FunctionManagerScreen(Screen parent) {
        super(Component.translatable("screen.joes_addons_for_abmc.function_manager"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        refreshNames();
        int cx = this.width / 2;
        this.nameEdit = new EditBox(this.font, cx - 150, 20, 300, 18, Component.literal("函数名"));
        this.addRenderableWidget(this.nameEdit);

        int y = this.height - 34;
        this.addRenderableWidget(Button.builder(Component.literal("新建并编辑"),
            b -> createFunction()).bounds(cx - 200, y, 90, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("编辑"),
            b -> editSelected()).bounds(cx - 100, y, 60, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("删除"),
            b -> deleteSelected()).bounds(cx - 30, y, 60, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("刷新"),
            b -> { PacketDistributor.sendToServer(ScriptFunctionPayload.requestSync()); refreshNames(); })
            .bounds(cx + 40, y, 60, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("返回"),
            b -> { Minecraft.getInstance().setScreen(parent); })
            .bounds(cx + 110, y, 60, 20).build());
    }

    private void refreshNames() {
        functionNames = new ArrayList<>();
        for (ScriptGraph g : ScriptGraphCodec.fromJsonCollection(ScriptNetworking.clientFunctionsJson)) {
            if (g != null && g.getId() != null) {
                functionNames.add(g.getId());
            }
        }
    }

    private void createFunction() {
        String name = nameEdit.getValue().trim();
        if (name.isEmpty()) return;
        ScriptGraph fn = new ScriptGraph(name, name);
        PacketDistributor.sendToServer(ScriptFunctionPayload.create(name, ScriptGraphCodec.toJson(fn)));
        Minecraft.getInstance().setScreen(new GraphEditorScreen(fn, name, true));
    }

    private void editSelected() {
        if (selectedIndex < 0 || selectedIndex >= functionNames.size()) return;
        String name = functionNames.get(selectedIndex);
        for (ScriptGraph g : ScriptGraphCodec.fromJsonCollection(ScriptNetworking.clientFunctionsJson)) {
            if (g != null && name.equals(g.getId())) {
                Minecraft.getInstance().setScreen(new GraphEditorScreen(g, name, true));
                return;
            }
        }
    }

    private void deleteSelected() {
        if (selectedIndex < 0 || selectedIndex >= functionNames.size()) return;
        String name = functionNames.get(selectedIndex);
        PacketDistributor.sendToServer(ScriptFunctionPayload.delete(name));
        functionNames.remove(selectedIndex);
        selectedIndex = -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cx = this.width / 2 - 150;
        int idx = (int) ((mouseY - listTop) / 18);
        if (mouseX >= cx && mouseX <= cx + 300 && idx >= 0 && idx < functionNames.size()) {
            selectedIndex = idx;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);
        int cx = this.width / 2 - 150;
        for (int i = 0; i < functionNames.size(); i++) {
            int y = listTop + i * 18;
            if (i == selectedIndex) {
                g.fill(cx, y, cx + 300, y + 16, 0xFF3A3A4A);
            }
            g.drawString(this.font, functionNames.get(i), cx + 4, y + 4, 0xCCCCCC);
        }
    }
}