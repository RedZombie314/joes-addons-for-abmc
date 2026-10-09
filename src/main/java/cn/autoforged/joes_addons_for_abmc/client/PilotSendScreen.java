package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.network.PilotSendConfirmPayload;
import cn.autoforged.joes_addons_for_abmc.network.PilotSendListPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * <b>「送到谁」的下拉列表</b>（需求 6.5.21，过渡性 UI）。
 *
 * <h3>需求里的交互</h3>
 * <ul>
 *   <li><b>鼠标点击</b>打开下拉列表、点某一项选中（再次点框可以收起/展开）；</li>
 *   <li><b>键盘上/下</b>直接遍历选项（会自动展开列表）；</li>
 *   <li><b>空格</b>确认当前高亮的那一项——这时才发包给服务端、才真正执行 send；
 *       {@code Enter} 作为等价键一并接受；</li>
 *   <li>确认之前服务端<b>什么都没做</b>，所以 {@code Esc}（或"关闭"）就是取消：
 *       东西还在选择器手里，再按一次右键可以重新打开列表。</li>
 * </ul>
 *
 * <h3>列表内容由服务端决定</h3>
 * 见 {@code SelectorPilot#openSendUi}：符合"位于附体空壳 50 格以内"的玩家名；
 * 单人档附加 Technoblade / Dream 两个调试项；多人且无人符合时给一条
 * "暂无需要支援的玩家"占位项。后三种条目的 id 都是
 * {@link PilotSendListPayload#NO_PLAYER}，选中并确认后服务端不做任何事（需求）。
 *
 * <p><b>临时 UI</b>：没有做分页/滚动，条目多了会往下画（超屏的部分看不见）；
 * 当前候选本来就只有寥寥几个，够用。等需求定型再考虑换成正式控件。
 */
public class PilotSendScreen extends Screen {

    /** 下拉框宽度（像素）。 */
    private static final int BOX_WIDTH = 200;
    /** 下拉框高度。 */
    private static final int BOX_HEIGHT = 20;
    /** 每一项的高度。 */
    private static final int ROW_HEIGHT = 14;
    /** 最多画几项（超出部分不显示；临时 UI 不做滚动）。 */
    private static final int MAX_VISIBLE_ROWS = 12;

    private final int selectorId;
    private final List<PilotSendListPayload.Entry> entries;
    /** 下拉是否展开。 */
    private boolean open;
    /** 当前高亮项。 */
    private int selected;

    public PilotSendScreen(int selectorId, List<PilotSendListPayload.Entry> entries) {
        super(Component.literal("选择支援目标"));
        this.selectorId = selectorId;
        this.entries = List.copyOf(entries);
    }

    // ===== 布局（每帧现算，窗口缩放不用额外处理） =====

    private int boxX() {
        return this.width / 2 - BOX_WIDTH / 2;
    }

    private int boxY() {
        return this.height / 2 - 40;
    }

    private boolean isOverBox(double mouseX, double mouseY) {
        int x = this.boxX();
        int y = this.boxY();
        return mouseX >= x && mouseX < x + BOX_WIDTH && mouseY >= y && mouseY < y + BOX_HEIGHT;
    }

    /** 鼠标落在第几项上；不在任何一项上返回 -1。 */
    private int indexAt(double mouseX, double mouseY) {
        if (!this.open) {
            return -1;
        }
        int x = this.boxX();
        int first = this.boxY() + BOX_HEIGHT;
        if (mouseX < x || mouseX >= x + BOX_WIDTH) {
            return -1;
        }
        int row = (int) ((mouseY - first) / ROW_HEIGHT);
        if (row < 0 || row >= this.visibleRows() || mouseY < first) {
            return -1;
        }
        return row;
    }

    private int visibleRows() {
        return Math.min(this.entries.size(), MAX_VISIBLE_ROWS);
    }

    private String currentLabel() {
        if (this.entries.isEmpty()) {
            return "暂无需要支援的玩家";
        }
        return this.entries.get(this.selected).name();
    }

    // ===== 渲染 =====

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);   // 背景（变暗/模糊）
        int x = this.boxX();
        int y = this.boxY();
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, y - 26, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, "↑/↓ 选择 · 空格确认 · Esc 取消",
            this.width / 2, y - 14, 0xA0A0A0);

        // 下拉框本体（外黑框 + 内底色，右侧一个小三角示意可展开）
        guiGraphics.fill(x - 1, y - 1, x + BOX_WIDTH + 1, y + BOX_HEIGHT + 1, 0xFF000000);
        guiGraphics.fill(x, y, x + BOX_WIDTH, y + BOX_HEIGHT, 0xFF303030);
        guiGraphics.drawString(this.font, this.currentLabel(), x + 5, y + 6, 0xFFFFFF);
        guiGraphics.drawString(this.font, this.open ? "▲" : "▼", x + BOX_WIDTH - 12, y + 6, 0xFFFFFF);

        if (!this.open) {
            return;
        }
        int first = y + BOX_HEIGHT;
        for (int i = 0; i < this.visibleRows(); i++) {
            int rowY = first + i * ROW_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + BOX_WIDTH
                && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            int background = i == this.selected ? 0xFF4A6FA5 : (hovered ? 0xFF3C3C3C : 0xFF202020);
            guiGraphics.fill(x, rowY, x + BOX_WIDTH, rowY + ROW_HEIGHT, background);
            guiGraphics.drawString(this.font, this.entries.get(i).name(), x + 5, rowY + 3, 0xFFFFFF);
        }
    }

    // ===== 输入 =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (this.isOverBox(mouseX, mouseY)) {
            if (!this.entries.isEmpty()) {
                this.open = !this.open;   // 点框 = 展开/收起
            }
            return true;
        }
        int index = this.indexAt(mouseX, mouseY);
        if (index >= 0) {
            this.selected = index;   // 点某项 = 选中（确认仍然是空格，见类注释）
            this.open = false;
            return true;
        }
        if (this.open) {
            this.open = false;       // 点在列表外面：收起
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_DOWN -> {
                this.move(1);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                this.move(-1);
                return true;
            }
            case GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                this.confirm();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (this.open) {
                    this.open = false;   // 先收起列表；再按一次才是取消整件事
                    return true;
                }
            }
            default -> {
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 上/下遍历（到两端就绕回去，方便一直按）。 */
    private void move(int delta) {
        if (this.entries.isEmpty()) {
            return;
        }
        this.open = true;
        this.selected = Math.floorMod(this.selected + delta, this.entries.size());
    }

    /** 空格确认：把选中的那一项发给服务端，由服务端决定做不做（调试项/占位项它什么都不做）。 */
    private void confirm() {
        if (this.entries.isEmpty()) {
            this.onClose();   // "暂无需要支援的玩家"：没有可确认的东西，直接关掉（服务端本来也没在等）
            return;
        }
        PilotSendListPayload.Entry entry = this.entries.get(this.selected);
        ModMain.LOGGER.info("[选择器] 支援下拉确认：{}（{}）", entry.name(), entry.id());
        PacketDistributor.sendToServer(new PilotSendConfirmPayload(this.selectorId, entry.id()));
        this.onClose();
    }
}
