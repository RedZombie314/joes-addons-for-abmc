package cn.autoforged.joes_addons_for_abmc.client;

import net.minecraft.client.Minecraft;

/**
 * 客户端输入的一个小探针：<b>空格现在按着没有</b>（需求：飞行金马"按住空格持续上升"）。
 *
 * <h2>为什么需要它</h2>
 * 服务端那半边靠原版的骑乘跳跃包（{@code START_RIDING_JUMP}/{@code STOP_RIDING_JUMP} →
 * {@code handleStartJump}/{@code handleStopJump}）就能准确知道"按住/松开"，
 * 但<b>客户端</b>自己模拟这匹被骑的马时收不到那两个包（它们是客户端发出去的），
 * 而 {@code LivingEntity#jumping} 又是 protected、拿不到。骑乘实体是<b>客户端预测</b>的，
 * 客户端不跟着抬升就会出现"服务端抬、客户端压"的来回纠正。所以这里直接读本地按键。
 *
 * <p>只在 {@code level.isClientSide()} 的分支里被调用（见 {@code PilotGoldenHorseEntity#tick}），
 * 专用服务器上这段代码永远不会执行。
 */
public final class PilotHorseClientInput {

    private PilotHorseClientInput() {
    }

    /** 空格是否按着。开着界面（背包/聊天等）时一律算没按，免得在菜单里马还在往天上窜。 */
    public static boolean jumpHeld() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.screen == null && minecraft.options.keyJump.isDown();
    }
}
