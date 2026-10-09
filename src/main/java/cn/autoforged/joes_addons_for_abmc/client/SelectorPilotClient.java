package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.entity.LuckySelectorEntity;
import cn.autoforged.joes_addons_for_abmc.entity.SelectorPilotMotion;
import cn.autoforged.joes_addons_for_abmc.entity.SelectorPilotPick;
import cn.autoforged.joes_addons_for_abmc.network.PilotSendListPayload;
import cn.autoforged.joes_addons_for_abmc.network.SelectorPilotPayload;
import cn.autoforged.joes_addons_for_abmc.network.SelectorPilotUsePayload;
import cn.autoforged.joes_addons_for_abmc.worldgen.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 幸运选择器的<b>旁观操控</b>（客户端）：
 * <ol>
 *   <li>每刻把"看向哪儿 + 按了哪些键"报给服务端（服务端是权威，负责真正移动与校验）；</li>
 *   <li><b>本地先转一步</b>：把玩家视线直接写进那只实体，视角与盒子朝向当场生效；</li>
 *   <li><b>本地先走一步</b>（{@link #predictMovement}）：用与服务端<b>逐字相同</b>的
 *       {@link SelectorPilotMotion} 在本地推一帧并移动，所以按 W 的那一刻画面就开始动，
 *       不用等"客户端 → 服务端 → 再同步回来"这一个来回；</li>
 *   <li><b>右键</b>（{@code InteractionKeyMappingTriggered}）：把准星所指的掉落物/生物报给服务端，
 *       由服务端决定 seek 还是 send（见 {@link SelectorPilotUsePayload}）。</li>
 * </ol>
 *
 * <h3>为什么必须"本地先走一步"（延迟的第二个来源）</h3>
 * 服务端是权威：它收到输入 → 本刻移动 → 本刻末把新位置广播回来，客户端下一 tick 才拿得到。
 * 这中间差着一个传输来回（约一刻的位移）。之前已经做到"服务端每刻都发包"
 * （{@code hasImpulse}，否则生物默认 3 刻才发一次），但一个来回的差距仍在，
 * 手感就是"按下去要过一会儿才动"。原版玩家自己没这个问题，是因为玩家实体本来就是<b>客户端预测</b>的
 * （客户端先走、服务端再对账）。这里给这只被操控的选择器补上同一套思路，只是简化了一层：
 * <b>不做输入回放，只做"差距超过 {@link SelectorPilotMotion#RESYNC_DISTANCE} 才认服务端"</b>——
 * 两个模型完全一致，正常飞行的差距恒定在一个来回的位移（约 0.43 格），永远不会误触发；
 * 真撞墙/被传送时差距迅速拉开，就会认服务端的位置，不会穿墙。
 *
 * <p>写在 {@code ClientTickEvent.Post}：那时本地实体本刻已经 tick 完
 * （与 {@code TransmutationCameraClient} 同一个理由），我们写进去的朝向与位置会直接用于这一帧渲染。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = ModMain.MODID)
public final class SelectorPilotClient {

    /** 上一次被本客户端操控的那只选择器（用来在结束操控时清掉预测状态）。 */
    @Nullable
    private static LuckySelectorEntity lastPiloted;

    private SelectorPilotClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            forgetPiloted();
            return;
        }
        // 注：可操控飞行金马（需求 3'）**不**在这里上报任何东西——它的操控走原版骑乘那一套
        // （客户端自己就会预测、服务端跟着对），竖直方向由服务端/客户端各自的 tick 按骑手视线算。
        if (!(minecraft.getCameraEntity() instanceof LuckySelectorEntity selector)
            || !selector.level().dimension().equals(ModDimensions.LUCKY_DIM_LEVEL)) {
            forgetPiloted();
            return;
        }
        lastPiloted = selector;

        // 归一化到 ±180°，免得连转几圈之后角度越滚越大（原版旁观时不会把这个值发给服务端，没人帮我们收）
        float yaw = Mth.wrapDegrees(player.getYRot());
        float pitch = player.getXRot();
        Options options = minecraft.options;
        // 开着界面（例如"送到谁"的下拉列表）时：不许飞、不许转视角，但**仍然每刻上报一次"没按键"**。
        // 为什么不干脆什么都不发：服务端把"输入是否新鲜"当作"玩家还在不在操控"的判据
        // （见 SelectorPilot.INPUT_TIMEOUT_TICKS）。停发超过几刻，服务端就认为没人操控了 →
        // 盒子滑停后 piloted 变假 → AI 接管，甚至可能趁玩家在挑目标时把它手里那把东西
        // 按默认流程送进幸运名单。上报"没按键"则两个目的都达到：盒子平滑停下，控制权仍在玩家手里。
        boolean uiOpen = minecraft.screen != null;
        boolean forward = !uiOpen && options.keyUp.isDown();
        boolean backward = !uiOpen && options.keyDown.isDown();
        boolean left = !uiOpen && options.keyLeft.isDown();
        boolean right = !uiOpen && options.keyRight.isDown();
        boolean jump = !uiOpen && options.keyJump.isDown();

        /*
         * 抓取/发送动画期间"暂时失去控制权"：那只盒子由服务端动画自己飞，客户端这边
         *   ① 不写朝向（让盒子自己转向目标，而不是死盯着玩家看的方向）；
         *   ② 不预测位移（否则会和权威轨迹越差越远、被纠正包一次次拽回来，看起来是抖），
         *      并把预测状态清掉，动画结束后从权威位置重新起步。
         * 输入照旧上报：服务端在忙的时候会忽略它，这样动画一结束控制权立刻就回来。
         */
        if (uiOpen) {
            selector.clearClientPilotState();
        } else if (selector.isBusySynced()) {
            selector.clearClientPilotState();
        } else {
            // ① 本地先转：视角（yHeadRot）与盒子朝向（yRot/yBodyRot）当场生效，不等服务端来回
            selector.setYRot(yaw);
            selector.setXRot(pitch);
            selector.setYHeadRot(yaw);
            selector.yBodyRot = yaw;
            // ② 本地先走：与服务端同一套模型推一帧
            predictMovement(selector, yaw, pitch, forward, backward, left, right, jump);
        }

        // ③ 上报输入（服务端按它移动权威实体，并每刻把权威位置发回来供上面那条"差距过大才认账"使用）
        PacketDistributor.sendToServer(new SelectorPilotPayload(
            selector.getId(), yaw, pitch, forward, backward, left, right, jump));
    }

    /**
     * 旁观操控下的<b>右键</b>（需求 6.5.18）：准星指着掉落物/生物时按右键 → 交给服务端去 seek；
     * 手里已经拿着东西时再按右键 → 交给服务端去 send（由服务端判定，见 {@link SelectorPilotUsePayload}）。
     *
     * <p>用 {@code InteractionKeyMappingTriggered} 而不是在 {@code ClientTickEvent} 里读按键：
     * 原版就是 {@code while (keyUse.consumeClick()) startUseItem()}，而 {@code startUseItem()} 里
     * 正好触发一次本事件（Minecraft.java:1721），不会漏掉"同一刻按下又松开"的快速点击；
     * 自己去 {@code consumeClick()} 反而会把这次点击从原版逻辑里抢走。
     *
     * <p>命中之后 {@code setCanceled(true)}：旁观时这个右键本来也不该干别的（原版
     * {@code Player#interactOn} 对旁观者基本是空操作），拦掉可以避免"顺手和生物交互一下"之类的意外。
     *
     * <p>射线从<b>盒子</b>的眼睛出发、用的是<b>玩家当前的视线</b>（不是盒子上一次同步下来的朝向，
     * 那个要晚一刻）：方块先挡一道（不能隔墙抓），然后在剩下的射程里找最近的掉落物/生物。
     */
    @SubscribeEvent
    public static void onPilotUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return; // 只管右键；左键那条已经被原版的"旁观实体"占用了
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        if (!(minecraft.getCameraEntity() instanceof LuckySelectorEntity selector)
            || !selector.level().dimension().equals(ModDimensions.LUCKY_DIM_LEVEL)) {
            return;
        }
        event.setCanceled(true);
        // 射线用"玩家此刻的视线"（本地预测的朝向也来自它），而不是盒子上一刻同步下来的朝向
        float yaw = Mth.wrapDegrees(player.getYRot());
        float pitch = player.getXRot();
        Entity target = SelectorPilotPick.pick(selector, yaw, pitch);
        ModMain.LOGGER.info("[选择器] 操控右键：客户端准星命中 {}（id={}）",
            target == null ? "无" : target.getType().toShortString(),
            target == null ? SelectorPilotUsePayload.NO_TARGET : target.getId());
        PacketDistributor.sendToServer(new SelectorPilotUsePayload(selector.getId(),
            target == null ? SelectorPilotUsePayload.NO_TARGET : target.getId()));
    }

    /**
     * 本地预测一帧：先决定"从哪儿开始推"，再按 {@link SelectorPilotMotion} 更新速度、本地
     * {@code move} 一次（碰撞用客户端自己的世界，撞墙一样会停）。
     */
    private static void predictMovement(LuckySelectorEntity selector, float yaw, float pitch,
                                        boolean forward, boolean backward, boolean left, boolean right, boolean jump) {
        Vec3 serverPosition = selector.position();
        Vec3 predicted = selector.clientPilotPredicted();
        boolean hasInput = forward || backward || left || right || jump;

        // 没有人在按键、而且上一帧就已经停住：不需要预测（服务端那边也停了），交给权威位置即可
        Vec3 velocity = selector.clientPilotVelocity();
        if (!hasInput && (velocity == null || SelectorPilotMotion.atRest(velocity))) {
            selector.setClientPilotState(Vec3.ZERO, serverPosition);
            return;
        }

        // 从哪儿开始推：默认接着上一帧的预测（否则会被一刻之前的权威位置拽回去，看起来是抖动）；
        // 只有差距大到 RESYNC_DISTANCE（撞墙、被传送、区块刚加载）才改用权威位置。
        Vec3 base = predicted != null && serverPosition.distanceTo(predicted) <= SelectorPilotMotion.RESYNC_DISTANCE
            ? predicted
            : serverPosition;

        Vec3 newVelocity = hasInput
            ? SelectorPilotMotion.step(velocity == null ? selector.getDeltaMovement() : velocity,
                yaw, pitch, forward, backward, left, right, jump)
            : SelectorPilotMotion.coast(velocity == null ? selector.getDeltaMovement() : velocity);

        selector.setPos(base);
        selector.setDeltaMovement(newVelocity);
        selector.move(MoverType.SELF, newVelocity);
        selector.setClientPilotState(newVelocity, selector.position());
    }

    /** 结束操控：把预测状态清掉，之后这只实体完全听服务端的（位置包会自然把它对齐）。 */
    private static void forgetPiloted() {
        if (lastPiloted != null) {
            lastPiloted.clearClientPilotState();
            lastPiloted = null;
        }
    }

    /**
     * 服务端让我们挑"送到谁"：弹出下拉列表（需求 6.5.21）。
     * <p>
     * 由 {@code PilotSendListPayload} 的客户端处理器调用（见 {@code ModMain#registerPayloads}）。
     * 确认之前服务端不会执行 send，所以关掉界面就等于取消。
     */
    public static void openSendUi(int selectorId, List<PilotSendListPayload.Entry> entries) {
        Minecraft.getInstance().setScreen(new PilotSendScreen(selectorId, entries));
    }
}
