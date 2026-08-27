package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.client.editor.FunctionManagerScreen;
import cn.autoforged.joes_addons_for_abmc.client.editor.GraphEditorScreen;
import cn.autoforged.joes_addons_for_abmc.network.CommandStaffActionPayload;
import cn.autoforged.joes_addons_for_abmc.network.ScriptNetworking;
import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraph;
import cn.autoforged.joes_addons_for_abmc.script.graph.serialize.ScriptGraphCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class CommandStaffScreen extends Screen {

    private EditBox commandEdit;
    private Button confirmButton;
    private Button historyButton;
    private Button presetButton;
    private String pendingHistorySelection;
    private CommandSuggestions commandSuggestions;
    private static final int WINDOW_WIDTH = 300;

    public CommandStaffScreen() {
        super(Component.translatable("screen.joes_addons_for_abmc.command_staff"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int topY = 50;

        this.commandEdit = new EditBox(this.font, centerX - 150, topY, WINDOW_WIDTH, 20,
            Component.translatable("screen.joes_addons_for_abmc.command_input")) {
            @Override
            protected MutableComponent createNarrationMessage() {
                return super.createNarrationMessage().append(commandSuggestions.getNarrationMessage());
            }
        };
        this.commandEdit.setMaxLength(32500);
        this.commandEdit.setResponder(this::onCommandEdited);
        if (pendingHistorySelection != null) {
            this.commandEdit.setValue(pendingHistorySelection);
            pendingHistorySelection = null;
        }
        this.addRenderableWidget(this.commandEdit);
        this.commandSuggestions = new CommandSuggestions(this.minecraft, this, this.commandEdit, this.font,
            true, true, 0, 7, false, 7);
        this.commandSuggestions.setAllowSuggestions(true);
        this.commandSuggestions.updateCommandInfo();
        this.setInitialFocus(this.commandEdit);

        int buttonY = this.height - 30;
        this.confirmButton = this.addRenderableWidget(
            Button.builder(Component.translatable("screen.joes_addons_for_abmc.confirm"),
                btn -> executeCommand())
                .bounds(centerX - 150, buttonY, 90, 20).build());

        this.historyButton = this.addRenderableWidget(
            Button.builder(Component.translatable("screen.joes_addons_for_abmc.recorded_commands"),
                btn -> openHistory())
                .bounds(centerX - 50, buttonY, 100, 20).build());

        this.presetButton = this.addRenderableWidget(
            Button.builder(Component.translatable("screen.joes_addons_for_abmc.add_preset"),
                btn -> openPresetManage())
                .bounds(centerX + 60, buttonY, 90, 20).build());

        PacketDistributor.sendToServer(CommandStaffActionPayload.requestSync());
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        guiGraphics.drawString(this.font,
            Component.translatable("screen.joes_addons_for_abmc.command_label"),
            this.width / 2 - 150, 40, 0xA0A0A0);
        this.commandSuggestions.render(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.commandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            executeCommand();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.commandSuggestions.mouseScrolled(scrollY) ? true : super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.commandSuggestions.mouseClicked(mouseX, mouseY, button) ? true : super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String s = this.commandEdit.getValue();
        this.init(minecraft, width, height);
        this.commandEdit.setValue(s);
        this.commandSuggestions.updateCommandInfo();
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    private void onCommandEdited(String command) {
        this.commandSuggestions.updateCommandInfo();
    }

    public void setPendingHistorySelection(String command) {
        this.pendingHistorySelection = command;
    }

    private void executeCommand() {
        String command = this.commandEdit.getValue().trim();
        if (command.isEmpty()) return;
        PacketDistributor.sendToServer(CommandStaffActionPayload.execute(command));
        this.minecraft.setScreen(null);
    }

    private void openHistory() {
        Minecraft.getInstance().setScreen(new CommandHistoryScreen(this));
    }

    private void openPresetManage() {
        Minecraft.getInstance().setScreen(new PresetManageScreen(this));
    }

    /** 打开 GUI 可视化编程入口：加载已有程序或新建，进入图形程序编辑器。 */
    private void openGraphEditor() {
        List<ScriptGraph> programs = ScriptGraphCodec.fromJsonCollection(ScriptNetworking.clientProgramsJson);
        ScriptGraph graph;
        String progId;
        if (!programs.isEmpty()) {
            graph = programs.get(0);
            progId = graph.getId();
        } else {
            progId = "prog_" + (System.currentTimeMillis() % 1_000_000);
            graph = new ScriptGraph(progId, "新程序");
        }
        Minecraft.getInstance().setScreen(new GraphEditorScreen(graph, progId, false));
    }

    /** 打开用户自制的可运行函数库管理界面。 */
    private void openFunctionManager() {
        Minecraft.getInstance().setScreen(new FunctionManagerScreen(this));
    }
}
