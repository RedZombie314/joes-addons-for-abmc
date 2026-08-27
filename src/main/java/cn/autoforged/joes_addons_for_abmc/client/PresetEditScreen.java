package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.network.CommandStaffActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class PresetEditScreen extends Screen {

    private final Screen parent;
    private final String existingName;
    private EditBox nameEdit;
    private int commandsEditTopY;

    private StringBuilder commandBuffer = new StringBuilder();
    private int cursorPos;
    private boolean commandsAreaFocused;
    private long cursorBlinkTime;

    protected PresetEditScreen(Screen parent, String existingName, List<String> existingCommands) {
        super(Component.translatable("screen.joes_addons_for_abmc.preset_edit"));
        this.parent = parent;
        this.existingName = existingName;
        if (existingCommands != null && !existingCommands.isEmpty()) {
            String text = String.join("\n", existingCommands);
            this.commandBuffer = new StringBuilder(text);
            this.cursorPos = text.length();
        } else {
            this.commandBuffer = new StringBuilder();
            this.cursorPos = 0;
        }
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int topY = 40;

        this.nameEdit = new EditBox(this.font, centerX - 150, topY, 300, 20,
            Component.translatable("screen.joes_addons_for_abmc.preset_name"));
        this.nameEdit.setMaxLength(256);
        if (existingName != null) {
            this.nameEdit.setValue(existingName);
        }
        this.addRenderableWidget(this.nameEdit);

        topY += 40;
        this.commandsEditTopY = topY;
        this.commandsAreaFocused = true;

        int buttonY = topY + 130;
        this.addRenderableWidget(
            Button.builder(Component.translatable("screen.joes_addons_for_abmc.save_preset"),
                btn -> savePreset())
                .bounds(centerX - 100, buttonY, 95, 20).build());

        this.addRenderableWidget(
            Button.builder(Component.translatable("screen.joes_addons_for_abmc.cancel"),
                btn -> this.onClose())
                .bounds(centerX + 5, buttonY, 95, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        guiGraphics.drawString(this.font,
            Component.translatable("screen.joes_addons_for_abmc.preset_name_label"),
            this.width / 2 - 150, 28, 0xA0A0A0);
        guiGraphics.drawString(this.font,
            Component.translatable("screen.joes_addons_for_abmc.preset_commands_label"),
            this.width / 2 - 150, commandsEditTopY - 12, 0xA0A0A0);

        drawMultiLineCommands(guiGraphics);
    }

    private void drawMultiLineCommands(GuiGraphics guiGraphics) {
        int left = this.width / 2 - 150;
        int top = commandsEditTopY;
        int w = 300;
        int h = 120;

        int borderColor = commandsAreaFocused ? 0xFFFFFFFF : 0xFFA0A0A0;
        guiGraphics.fill(left - 1, top - 1, left + w + 1, top + h + 1, borderColor);
        guiGraphics.fill(left, top, left + w, top + h, 0xFF000000);

        String fullText = commandBuffer.toString();
        String[] lines = fullText.split("\n", -1);
        int lineHeight = this.font.lineHeight + 1;
        int visibleLines = (h - 4) / lineHeight;

        int cursorLine = 0;
        int cursorCol = 0;
        int charCount = 0;
        for (int li = 0; li < lines.length; li++) {
            if (cursorPos <= charCount + lines[li].length()) {
                cursorLine = li;
                cursorCol = cursorPos - charCount;
                break;
            }
            charCount += lines[li].length() + 1;
        }

        int scrollOffset = Math.max(0, cursorLine - visibleLines + 1);

        for (int li = scrollOffset; li < Math.min(lines.length, scrollOffset + visibleLines); li++) {
            String line = lines[li];
            int y = top + 4 + (li - scrollOffset) * lineHeight;
            guiGraphics.drawString(this.font, line, left + 4, y, 0xE0E0E0);

            if (commandsAreaFocused && li == cursorLine) {
                long now = System.currentTimeMillis();
                if ((now / 500) % 2 == 0) {
                    String beforeCursor = line.substring(0, Math.min(cursorCol, line.length()));
                    int cursorX = left + 4 + this.font.width(beforeCursor);
                    int cursorYCaret = y - 1;
                    guiGraphics.fill(cursorX, cursorYCaret, cursorX + 1, cursorYCaret + this.font.lineHeight, -1 ^ 0xFFFFFFFF);
                }
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!commandsAreaFocused) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commandBuffer.insert(cursorPos, "\n");
            cursorPos++;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (cursorPos > 0) {
                commandBuffer.deleteCharAt(cursorPos - 1);
                cursorPos--;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (cursorPos < commandBuffer.length()) {
                commandBuffer.deleteCharAt(cursorPos);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (cursorPos > 0) cursorPos--;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (cursorPos < commandBuffer.length()) cursorPos++;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            String fullText = commandBuffer.toString();
            String[] lines = fullText.split("\n", -1);
            int charCount = 0;
            int curLine = 0;
            for (int li = 0; li < lines.length; li++) {
                if (cursorPos <= charCount + lines[li].length()) {
                    curLine = li;
                    break;
                }
                charCount += lines[li].length() + 1;
            }
            if (curLine > 0) {
                int colInLine = cursorPos - charCount;
                int prevLineLen = lines[curLine - 1].length();
                cursorPos = charCount - prevLineLen - 1 + Math.min(colInLine, prevLineLen);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            String fullText = commandBuffer.toString();
            String[] lines = fullText.split("\n", -1);
            int charCount = 0;
            int curLine = 0;
            for (int li = 0; li < lines.length; li++) {
                if (cursorPos <= charCount + lines[li].length()) {
                    curLine = li;
                    break;
                }
                charCount += lines[li].length() + 1;
            }
            if (curLine < lines.length - 1) {
                int colInLine = cursorPos - charCount;
                int nextLineLen = lines[curLine + 1].length();
                cursorPos = charCount + lines[curLine].length() + 1 + Math.min(colInLine, nextLineLen);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            String fullText = commandBuffer.toString();
            int idx = fullText.lastIndexOf('\n', cursorPos - 1);
            cursorPos = idx + 1;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            String fullText = commandBuffer.toString();
            int idx = fullText.indexOf('\n', cursorPos);
            cursorPos = idx == -1 ? fullText.length() : idx;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!commandsAreaFocused) {
            return super.charTyped(codePoint, modifiers);
        }
        if (codePoint >= 32 && codePoint != 127) {
            commandBuffer.insert(cursorPos, String.valueOf(codePoint));
            cursorPos++;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOverCommandsArea(mouseX, mouseY)) {
            commandsAreaFocused = true;
            if (this.nameEdit != null) this.nameEdit.setFocused(false);
            this.setFocused(null);
            int left = this.width / 2 - 150;
            int relX = (int) mouseX - (left + 4);
            int relY = (int) mouseY - (commandsEditTopY + 4);
            int lineHeight = this.font.lineHeight + 1;
            int line = Math.max(0, relY / lineHeight);

            String fullText = commandBuffer.toString();
            String[] lines = fullText.split("\n", -1);
            if (lines.length == 0) {
                cursorPos = 0;
                return true;
            }
            int idx = 0;
            for (int li = 0; li < lines.length; li++) {
                if (li == line) {
                    idx += this.font.plainSubstrByWidth(lines[li], Math.max(0, relX)).length();
                    break;
                }
                idx += lines[li].length() + 1;
                if (li == lines.length - 1) {
                    idx += this.font.plainSubstrByWidth(lines[li], Math.max(0, relX)).length();
                }
            }
            cursorPos = Math.min(idx, fullText.length());
            return true;
        } else {
            commandsAreaFocused = false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isMouseOverCommandsArea(double mouseX, double mouseY) {
        int left = this.width / 2 - 150;
        return mouseX >= left - 1 && mouseX <= left + 301
            && mouseY >= commandsEditTopY - 1 && mouseY <= commandsEditTopY + 121;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private void savePreset() {
        String name = this.nameEdit.getValue().trim();
        if (name.isEmpty()) return;

        String rawCommands = commandBuffer.toString();
        List<String> commands = new ArrayList<>();
        for (String line : rawCommands.split("\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                commands.add(trimmed);
            }
        }
        if (commands.isEmpty()) return;

        if (existingName != null && !existingName.equals(name)) {
            PacketDistributor.sendToServer(CommandStaffActionPayload.renamePreset(existingName, name));
            CommandStaffDataCache.presets.remove(existingName);
        }
        PacketDistributor.sendToServer(CommandStaffActionPayload.savePreset(name, commands));
        CommandStaffDataCache.presets.put(name, new ArrayList<>(commands));
        this.onClose();
    }
}
