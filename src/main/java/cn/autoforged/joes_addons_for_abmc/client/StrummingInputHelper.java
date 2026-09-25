package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.entity.HoldableStructureEntity;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * 弹奏工具输入冻结辅助（客户端）。
 * 当玩家「主手持弹奏工具」且「正在持有（锁定）一个结构」时，冻结键盘上的所有字母与数字键
 * （例如按 WASD 无法移动、数字键无法切栏）。这些键的输入会被 KeyboardHandlerMixin 拦截，
 * 并在冻结期间强制松开移动键。主手换成其他物品后自动恢复。
 */
public final class StrummingInputHelper {

    /** 当前是否处于输入冻结状态。由 KeyboardHandlerMixin 读取。 */
    public static boolean inputFrozen = false;

    /** 当前被玩家持有的「playable」结构的 id（如 chicken_guitar_1），据此决定演奏音色；未持有为空串。 */
    public static String heldStructureId = "";

    /** 当前被持有结构是否为 charged 变体（闪电充能）；据此在充电结构上用电吉他音色。 */
    public static boolean heldStructureCharged = false;

    private StrummingInputHelper() {
    }

    /** 每帧更新冻结状态；冻结期间强制松开 WASD 移动键。 */
    public static void update(Minecraft mc) {
        boolean frozen = false;
        if (mc.player != null && mc.level != null) {
            boolean holdingTool = mc.player.getMainHandItem().is(ModItems.STRUMMING_TOOL.get());
            HoldableStructureEntity hs = findHeldPlayableStructure(mc);
            heldStructureId = hs != null ? hs.getStructureId() : "";
            heldStructureCharged = hs != null && hs.getCharged();
            boolean holdingPlayableStructure = !heldStructureId.isEmpty();
            frozen = holdingTool && holdingPlayableStructure;
        } else {
            heldStructureId = "";
            heldStructureCharged = false;
        }
        inputFrozen = frozen;
        if (frozen) {
            releaseMovementKeys(mc);
        }
    }

    /** 返回被锁定到自己身上的「playable」结构实体（未找到返回 null）。 */
    private static HoldableStructureEntity findHeldPlayableStructure(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        java.util.UUID self = mc.player.getUUID();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof HoldableStructureEntity hs) {
                java.util.UUID locked = hs.getBodyLockPlayerUuid();
                if (locked != null
                    && locked.equals(self)
                    && "playable".equals(hs.getStructureCategory())) {
                    return hs;
                }
            }
        }
        return null;
    }

    /** 强制松开 WASD 移动键，确保即使冻结前按键仍处于按住状态也不产生移动。 */
    private static void releaseMovementKeys(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
    }
}