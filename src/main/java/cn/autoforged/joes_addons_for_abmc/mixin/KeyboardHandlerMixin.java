package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.client.StrummingInputHelper;
import cn.autoforged.joes_addons_for_abmc.client.StrummingKeyHandler;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端键盘拦截 mixin（作用于 KeyboardHandler）。
 * 当 StrummingInputHelper.inputFrozen 为 true（主手持弹奏工具且持有结构）时，
 * 吞掉所有字母和数字按键事件（含数字小键盘），使移动/切栏等被禁用。
 * 对音阶键（asdfghjkl/wetyuop）的按下（GLFW_PRESS）在拦截前立即转发给
 * StrummingKeyHandler.press —— 按键到达瞬间就演奏，无 tick 轮询延迟。
 * 八度变调键（zxcv/bnm）作为修饰键，由 press 内部读 GLFW 状态即时生效。
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"), cancellable = true)
    private void jafa_strummingFreezeKeys(long windowPointer, int key, int scancode, int action, int mods,
                                          CallbackInfo ci) {
        if (StrummingInputHelper.inputFrozen && isLetterOrDigit(key)) {
            // 音阶键按下的瞬间（仅首次按下，忽略重复触发）立即演奏
            if (action == GLFW.GLFW_PRESS && StrummingKeyHandler.isNoteKey(key)) {
                StrummingKeyHandler.press(key);
            }
            ci.cancel();
        }
    }

    /** A-Z、0-9 主键盘区及数字小键盘是否属于「字母或数字」。 */
    private static boolean isLetterOrDigit(int key) {
        return (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z)
            || (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9)
            || (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9);
    }
}