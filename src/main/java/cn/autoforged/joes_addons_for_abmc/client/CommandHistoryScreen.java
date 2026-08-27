package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.network.CommandStaffActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public class CommandHistoryScreen extends Screen {

    private final CommandStaffScreen parent;
    private HistoryList historyList;

    protected CommandHistoryScreen(CommandStaffScreen parent) {
        super(Component.translatable("screen.joes_addons_for_abmc.command_history"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        PacketDistributor.sendToServer(CommandStaffActionPayload.requestSync());
        int bottomButtonsY = this.height - 35;
        this.historyList = new HistoryList(this.minecraft, this.width, this.height, 40, 12);
        this.addRenderableWidget(this.historyList);

        for (String cmd : CommandStaffDataCache.commandHistory) {
            this.historyList.addEntry(new HistoryEntry(cmd));
        }

        this.addRenderableWidget(
            Button.builder(Component.translatable("screen.joes_addons_for_abmc.back"),
                btn -> this.onClose())
                .bounds(this.width / 2 - 50, bottomButtonsY, 100, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private class HistoryList extends ObjectSelectionList<HistoryEntry> {
        public HistoryList(net.minecraft.client.Minecraft mc, int width, int height, int y0, int y1) {
            super(mc, width, height, y0, y1);
        }

        public int addEntry(HistoryEntry entry) {
            return super.addEntry(entry);
        }
    }

    private class HistoryEntry extends ObjectSelectionList.Entry<HistoryEntry> {
        private final String command;

        HistoryEntry(String command) {
            this.command = command;
        }

        @Override
        public Component getNarration() {
            return Component.literal(command);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            guiGraphics.drawString(CommandHistoryScreen.this.font, command, left + 5, top + 2, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            parent.setPendingHistorySelection(command);
            CommandHistoryScreen.this.onClose();
            return true;
        }
    }
}
