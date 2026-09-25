package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.network.StrummingPlayPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 弹奏工具演奏输入（客户端）：当处于输入冻结（持弹奏工具 + 持有 playable 结构）时，
 * 由 KeyboardHandlerMixin 在按键到达瞬间（GLFW 事件，无 tick 轮询延迟）转发到 {@link #press(int)}。
 * 音阶键 asdfghjkl/wetyuop、八度变调键 zxcv/bnm。
 * <p>音阶键（基准 C=54 的大调/半音）：
 *   a=C(54) s=D(56) d=E(58) f=F(59) g=G(61) h=A(63) j=B(65) k=C'(66) l=D'(68)；
 *   w=C#(55) e=D#(57) t=F#(60) y=G#(62) u=A#(64) o=C#'(67) p=D#'(69)。
 *   <p>八度变调键（按住累积到音阶音上，可同时按；"度"=8 白键=跨一个八度=12 半音）：
 *   z=-4 八度 x=-3 八度 c=-2 八度 v=-1 八度（即 -48/-36/-24/-12 半音）；b=+1 n=+2 m=+3 八度（+12/+24/+36）。
 *   <p>按键瞬间：本地立即播放（零延迟给演奏者）＋ 发网络包让服务端转播给其他玩家（跳过本人）。
 */
public final class StrummingKeyHandler {

    /** 音阶键：{ GLFW key, 基准半音 }。 */
    private static final int[][] NOTE_MAP = {
        { GLFW.GLFW_KEY_A, 54 }, { GLFW.GLFW_KEY_S, 56 }, { GLFW.GLFW_KEY_D, 58 },
        { GLFW.GLFW_KEY_F, 59 }, { GLFW.GLFW_KEY_G, 61 }, { GLFW.GLFW_KEY_H, 63 },
        { GLFW.GLFW_KEY_J, 65 }, { GLFW.GLFW_KEY_K, 66 }, { GLFW.GLFW_KEY_L, 68 },
        { GLFW.GLFW_KEY_W, 55 }, { GLFW.GLFW_KEY_E, 57 }, { GLFW.GLFW_KEY_T, 60 },
        { GLFW.GLFW_KEY_Y, 62 }, { GLFW.GLFW_KEY_U, 64 }, { GLFW.GLFW_KEY_O, 67 },
        { GLFW.GLFW_KEY_P, 69 }
    };
    /** 八度变调键：{ GLFW key, 半音偏移 }。"度"=8 白键=跨一个八度=12 半音；zxcv 按 4/3/2/1 八度递减，bnm 按 1/2/3 递增。 */
    private static final int[][] OCTAVE_MAP = {
        { GLFW.GLFW_KEY_Z, -48 }, { GLFW.GLFW_KEY_X, -36 }, { GLFW.GLFW_KEY_C, -24 }, { GLFW.GLFW_KEY_V, -12 },
        { GLFW.GLFW_KEY_B, 12 }, { GLFW.GLFW_KEY_N, 24 }, { GLFW.GLFW_KEY_M, 36 }
    };

    private StrummingKeyHandler() {
    }

    /** 某 GLFW 键码是否是一个音阶键。 */
    public static boolean isNoteKey(int glfwKey) {
        for (int[] n : NOTE_MAP) {
            if (n[0] == glfwKey) return true;
        }
        return false;
    }

    /** 处理一次音阶键按下：读当前八度变调，本地立即播放，并让服务端转播给其他玩家。 */
    public static void press(int glfwKey) {
        Minecraft mc = Minecraft.getInstance();
        if (!StrummingInputHelper.inputFrozen || mc.level == null || mc.player == null) return;
        long win = mc.getWindow().getWindow();
        // 当前按住的所有八度变调键之和
        int transpose = 0;
        for (int[] o : OCTAVE_MAP) {
            if (GLFW.glfwGetKey(win, o[0]) == GLFW.GLFW_PRESS) transpose += o[1];
        }
        int base = -1;
        for (int[] n : NOTE_MAP) {
            if (n[0] == glfwKey) {
                base = n[1];
                break;
            }
        }
        if (base < 0) return;
        int semitone = java.lang.Math.floorMod(base + transpose, cn.autoforged.joes_addons_for_abmc.sound.DidgeridooTones.TOTAL);
        int timbre = cn.autoforged.joes_addons_for_abmc.sound.StrumTones.timbreForStructure(
            StrummingInputHelper.heldStructureId, StrummingInputHelper.heldStructureCharged);
        // 1) 本地即时播放（演奏者零延迟）
        cn.autoforged.joes_addons_for_abmc.sound.StrumTones.playLocal(mc, timbre, semitone);
        // 2) 让服务端转播给其他玩家（跳过本人）
        PacketDistributor.sendToServer(new StrummingPlayPayload(timbre, new int[] { semitone }));
    }
}