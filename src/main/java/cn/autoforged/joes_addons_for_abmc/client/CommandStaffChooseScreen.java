package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.client.editor.GraphEditorScreen;
import cn.autoforged.joes_addons_for_abmc.network.ScriptNetworking;
import cn.autoforged.joes_addons_for_abmc.script.graph.ScriptGraph;
import cn.autoforged.joes_addons_for_abmc.script.graph.serialize.ScriptGraphCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * 命令方块权杖入口选择：文本命令编辑 / 图形程序编辑。
 */
@OnlyIn(Dist.CLIENT)
public class CommandStaffChooseScreen extends Screen {

    public CommandStaffChooseScreen() {
        super(Component.translatable("screen.joes_addons_for_abmc.choose_mode"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.joes_addons_for_abmc.text_mode"),
            b -> Minecraft.getInstance().setScreen(new CommandStaffScreen()))
            .bounds(cx - 120, this.height / 2 - 30, 110, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.joes_addons_for_abmc.graph_mode"),
            b -> openGraphEditor())
            .bounds(cx + 10, this.height / 2 - 30, 110, 20).build());
    }

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

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 60, 0xFFFFFF);
    }
}