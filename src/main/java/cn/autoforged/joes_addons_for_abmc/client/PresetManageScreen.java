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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class PresetManageScreen extends Screen {

    private final Screen parent;
    private PresetList presetList;
    private final Map<String, Long> redMarkedPresets = new HashMap<>();
    private static final long RED_MARK_DURATION_MS = 10_000;

    protected PresetManageScreen(Screen parent) {
        super(Component.translatable("screen.joes_addons_for_abmc.preset_manage"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        redMarkedPresets.clear();
        PacketDistributor.sendToServer(CommandStaffActionPayload.requestSync());
        int bottomButtonsY = this.height - 35;
        super.init();
        int listWidth = Math.min(this.width - 40, 260);
        int listX = (this.width - listWidth) / 2;
        int listTop = 32;
        int rowHeight = 30;
        int visibleRows = 5;
        int listHeight = visibleRows * rowHeight + 4;
        this.presetList = new PresetList(this.minecraft, listWidth, listHeight, listTop, listX, rowHeight);
        this.addRenderableWidget(this.presetList);

        for (Map.Entry<String, List<String>> entry : CommandStaffDataCache.presets.entrySet()) {
            this.presetList.addEntry(new PresetEntry(entry.getKey(), entry.getValue()));
        }

        this.addRenderableWidget(
            Button.builder(Component.translatable("screen.joes_addons_for_abmc.new_preset"),
                btn -> openPresetEditor(null))
                .bounds(this.width / 2 - 100, bottomButtonsY, 95, 20).build());

        this.addRenderableWidget(
            Button.builder(Component.translatable("screen.joes_addons_for_abmc.back"),
                btn -> this.onClose())
                .bounds(this.width / 2 + 5, bottomButtonsY, 95, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        redMarkedPresets.entrySet().removeIf(e -> now - e.getValue() > RED_MARK_DURATION_MS);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private void openPresetEditor(String presetName) {
        List<String> commands = new ArrayList<>();
        if (presetName != null) {
            commands = CommandStaffDataCache.presets.getOrDefault(presetName, new ArrayList<>());
        }
        this.minecraft.setScreen(new PresetEditScreen(this, presetName, commands));
    }

    private void executePreset(String name) {
        PacketDistributor.sendToServer(CommandStaffActionPayload.execute("/runpreset " + name));
        this.minecraft.setScreen(null);
    }

    private void deletePreset(String name) {
        PacketDistributor.sendToServer(CommandStaffActionPayload.deletePreset(name));
        CommandStaffDataCache.presets.remove(name);
    }

    private class PresetList extends ObjectSelectionList<PresetEntry> {
        public PresetList(net.minecraft.client.Minecraft mc, int width, int height, int y, int x, int itemHeight) {
            super(mc, width, height, y, itemHeight);
            this.setX(x);
        }

        @Override
        public int getRowWidth() {
            return this.width - 20;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 1 || button == 2) {
                if (!this.isMouseOver(mouseX, mouseY)) return false;
                PresetEntry e = this.getEntryAtPosition(mouseX, mouseY);
                if (e != null) {
                    return e.mouseClicked(mouseX, mouseY, button);
                }
                return false;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        public int addEntry(PresetEntry entry) {
            return super.addEntry(entry);
        }
    }

    private class PresetEntry extends ObjectSelectionList.Entry<PresetEntry> {
        private final String name;
        private final List<String> commands;
        private long lastClickTime;

        PresetEntry(String name, List<String> commands) {
            this.name = name;
            this.commands = commands;
        }

        @Override
        public Component getNarration() {
            return Component.literal(name);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            int textColor = redMarkedPresets.containsKey(name) ? 0xFF5555 : 0xFFFFFF;
            guiGraphics.drawString(PresetManageScreen.this.font, name, left + 5, top + 2, textColor);
            String firstCmd = commands.isEmpty() ? "" : commands.get(0);
            if (firstCmd.length() > 40) firstCmd = firstCmd.substring(0, 40) + "...";
            guiGraphics.drawString(PresetManageScreen.this.font, firstCmd, left + 5, top + 14, 0x888888);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 2) {
                if (redMarkedPresets.containsKey(name)) {
                    deletePreset(name);
                    redMarkedPresets.remove(name);
                    presetList.children().removeIf(e -> e.name.equals(name));
                    return true;
                } else {
                    redMarkedPresets.put(name, System.currentTimeMillis());
                }
                return true;
            }
            if (button == 1) {
                openPresetEditor(name);
                return true;
            }
            if (button == 0) {
                long now = System.currentTimeMillis();
                if (now - lastClickTime < 400) {
                    executePreset(name);
                    return true;
                }
                lastClickTime = now;
            }
            return false;
        }
    }
}
