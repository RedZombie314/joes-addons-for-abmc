package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.block.SupportGift;
import cn.autoforged.joes_addons_for_abmc.network.PilotSendConfirmPayload;
import cn.autoforged.joes_addons_for_abmc.network.PilotSendListPayload;
import cn.autoforged.joes_addons_for_abmc.network.SelectorPilotPayload;
import cn.autoforged.joes_addons_for_abmc.network.SelectorPilotUsePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <b>旁观操控</b>的输入中转站（服务端，只在内存里）：记录每个玩家最近一次上报的"看向哪儿 + 按了哪些键"，
 * 由被旁观的那只 {@link LuckySelectorEntity} 每刻取用。
 *
 * <h3>为什么放在静态表里而不是实体字段上</h3>
 * 输入属于<b>玩家</b>、消费方是<b>实体</b>，而且只有"玩家正在旁观某只选择器"这一小段时间有意义。
 * 放在静态表里最省事：不用给实体加同步字段、不用给玩家挂数据附件、也不进存档。
 * 表项用<b>服务端游戏刻</b>打时间戳（不是客户端时间——两端时钟会漂），
 * 超过 {@link #INPUT_TIMEOUT_TICKS} 刻没更新就当作"没人操控了"，
 * 于是玩家退出旁观、掉线、甚至客户端卡住时会自动失效，不需要额外的清理钩子。
 *
 * <h3>服务端是权威</h3>
 * {@link #accept} 会核对：玩家必须是<b>旁观模式</b>、他的摄像机必须<b>正是</b> payload 里说的那只选择器、
 * 而且那只选择器必须在<b>幸运维度</b>里。所以客户端伪造的包最多也只能操控自己正在旁观的那只，
 * 不会凭空去开别人的选择器。
 */
public final class SelectorPilot {

    /** 输入多久没更新就作废（刻）。客户端每刻都发，3 刻足够容忍一次卡顿/丢包。 */
    public static final int INPUT_TIMEOUT_TICKS = 3;

    /**
     * 一次输入快照。
     *
     * @param selectorId 上报时正在旁观的选择器实体 id
     * @param tick       服务端收到它的那一刻（{@code gameTime}）
     */
    public record Input(int selectorId, float yaw, float pitch,
                        boolean forward, boolean backward, boolean left, boolean right,
                        boolean jump, long tick) {
    }

    /** 玩家 UUID → 最近一次输入。只在服务端主线程读写，不需要同步。 */
    private static final Map<UUID, Input> INPUTS = new HashMap<>();

    private SelectorPilot() {
    }

    /** 收到客户端的上报：三项校验都过了才记下来（见类注释）。 */
    public static void accept(Player player, SelectorPilotPayload payload) {
        ServerPlayer serverPlayer = pilotedBy(player, payload.entityId());
        if (serverPlayer == null) {
            return;
        }
        INPUTS.put(serverPlayer.getUUID(), new Input(payload.entityId(), payload.yaw(), payload.pitch(),
            payload.forward(), payload.backward(), payload.left(), payload.right(), payload.jump(),
            serverPlayer.serverLevel().getGameTime()));
    }

    /**
     * 收到客户端的<b>右键</b>（需求 6.5.18）：手里有东西就 {@link LuckySelectorEntity#startPilotSend() send}，
     * 否则朝准星所指的掉落物/生物 {@code seek}。
     * <p>
     * 判定顺序刻意是"先看手里有没有东西"：手里攥着东西时右键的语义就是"把这个送走"，
     * 跟准星指着什么无关（需求原文："如果在装有物品/生物的时候再次右键，则会执行 send"）。
     * <p>
     * 目标一律由服务端复核（见 {@link SelectorPilotUsePayload} 的注释）：同一维度、活着、
     * 在 {@link LuckySelectorEntity#PILOT_SEEK_REACH} 格以内，生物还要过 {@code canCapture}。
     * 任何一条不满足就安静地什么都不做（只在服务端日志里留一句，方便对着看为什么没反应）。
     */
    public static void handleUse(Player player, SelectorPilotUsePayload payload) {
        ServerPlayer serverPlayer = pilotedBy(player, payload.selectorId());
        if (serverPlayer == null) {
            return;
        }
        Entity camera = serverPlayer.getCamera();
        if (!(camera instanceof LuckySelectorEntity selector)) {
            return;
        }
        if (selector.hasContainedContent()) {
            // 需求 6.5.21：这条 send 要先在客户端的下拉列表里挑一位"需要支援的玩家"，
            // 确认之后才真正送（见 handleConfirm）。所以这里只把候选名单发下去。
            openSendUi(serverPlayer, selector);
            return;
        }
        Entity target = resolveTarget(serverPlayer, selector, payload);
        if (target == null) {
            ModMain.LOGGER.info("[选择器] 操控右键 → 准星上没有可抓的目标（客户端报文 id={}），忽略",
                payload.targetEntityId());
            return;
        }
        if (selector.startSeekTarget(target)) {
            ModMain.LOGGER.info("[选择器] 操控右键 → 开始抓取：{}（id={}，距离 {} 格）",
                target.getType().toShortString(), target.getId(), Math.round(selector.distanceTo(target)));
        } else {
            ModMain.LOGGER.info("[选择器] 操控右键 → 抓取没能开始（正忙 / 目标已失效）");
        }
    }

    /**
     * 定出这次右键要抓谁：<b>先用客户端报的那一只</b>（那是玩家真正看到、真正对准的目标，
     * 客户端用的是自己插值后的画面），不合法时<b>再用同一套射线在服务端打一遍兜底</b>。
     *
     * <p>兜底那一手是 6.5.19 补的：客户端和服务端的可见状态本来就可能差一刻（位置插值），
     * 万一客户端那边没报上来（6.5.18 就因为照抄 {@code isPickable()} 把普通掉落物全过滤掉了），
     * 服务端自己还能找到同一个目标，不至于"点了没反应"。兜底用的视线是玩家<b>最近一次上报</b>的
     * yaw/pitch（比客户端那一刻晚约一刻，可接受；两条路都找不到才真的算没瞄上）。
     */
    @Nullable
    private static Entity resolveTarget(ServerPlayer player, LuckySelectorEntity selector, SelectorPilotUsePayload payload) {
        Entity fromClient = payload.targetEntityId() == SelectorPilotUsePayload.NO_TARGET
            ? null
            : selector.level().getEntity(payload.targetEntityId());
        if (isSeekableBy(selector, fromClient)) {
            return fromClient;
        }
        Input input = inputOf(player);
        if (input == null || input.selectorId() != selector.getId()) {
            return null;
        }
        Entity fromServer = SelectorPilotPick.pick(selector, input.yaw(), input.pitch());
        return isSeekableBy(selector, fromServer) ? fromServer : null;
    }

    /** 这只实体现在能不能作为抓取目标：同一维度、还活着、在射程内、是生物或掉落物；生物还要过 {@code canCapture}。 */
    private static boolean isSeekableBy(LuckySelectorEntity selector, @Nullable Entity target) {
        if (target == null || target.isRemoved() || !target.isAlive()) {
            return false;
        }
        if (target.level() != selector.level()) {
            return false;
        }
        if (selector.distanceTo(target) > LuckySelectorEntity.PILOT_SEEK_REACH) {
            return false;
        }
        if (target instanceof Mob mob) {
            return selector.canCapture(mob);
        }
        return target instanceof ItemEntity;
    }

    /**
     * 组装并下发"送到谁"的下拉列表（需求 6.5.21）。
     *
     * <h3>候选</h3>
     * 「位于幸运核心附体的玩家空壳 50 格以内」的在线玩家（{@link #isNearPossessedShell}）。
     * <h3>两种特殊名单</h3>
     * <ul>
     *   <li><b>单人档</b>（服务器里只有他一个玩家）：附上 Technoblade / Dream 两个调试项。
     *       它们用 {@link PilotSendListPayload#NO_PLAYER} 作 id，选中后不做任何事（需求）；</li>
     *   <li><b>多人但一个都不符合</b>：只给一条占位项"暂无需要支援的玩家"（同样 {@code NO_PLAYER}）。</li>
     * </ul>
     */
    private static void openSendUi(ServerPlayer player, LuckySelectorEntity selector) {
        List<ServerPlayer> online = player.server.getPlayerList().getPlayers();
        List<PilotSendListPayload.Entry> entries = new ArrayList<>();
        for (ServerPlayer other : online) {
            if (isNearPossessedShell(other)) {
                entries.add(new PilotSendListPayload.Entry(other.getGameProfile().getName(), other.getUUID()));
            }
        }
        if (online.size() <= 1) {
            entries.add(new PilotSendListPayload.Entry("Technoblade", PilotSendListPayload.NO_PLAYER));
            entries.add(new PilotSendListPayload.Entry("Dream", PilotSendListPayload.NO_PLAYER));
        } else if (entries.isEmpty()) {
            entries.add(new PilotSendListPayload.Entry("暂无需要支援的玩家", PilotSendListPayload.NO_PLAYER));
        }
        player.connection.send(new PilotSendListPayload(selector.getId(), entries));
        ModMain.LOGGER.info("[支援] 给 {} 打开选择列表：{}（在线玩家 {} 人）",
            player.getGameProfile().getName(),
            entries.stream().map(PilotSendListPayload.Entry::name).toList(), online.size());
    }

    /** 这位玩家 50 格以内有没有"幸运核心附体的玩家空壳"（需求给的候选条件）。 */
    private static boolean isNearPossessedShell(ServerPlayer player) {
        return !player.serverLevel().getEntitiesOfClass(PlayerShellEntity.class,
            player.getBoundingBox().inflate(SupportGift.SHELL_RADIUS),
            shell -> shell.isAlive() && shell.isOrbAttached()).isEmpty();
    }

    /**
     * 客户端在下拉列表里按空格确认之后：真正执行这条"支援 send"（需求 6.5.21）。
     * <p>
     * 目标为 {@link PilotSendListPayload#NO_PLAYER}（调试项/占位项）或查不到这个玩家时什么都不做
     * ——东西仍然留在选择器手里，玩家可以再按一次右键重开列表。
     */
    public static void handleConfirm(Player player, PilotSendConfirmPayload payload) {
        ServerPlayer serverPlayer = pilotedBy(player, payload.selectorId());
        if (serverPlayer == null) {
            return;
        }
        if (!(serverPlayer.getCamera() instanceof LuckySelectorEntity selector)) {
            return;
        }
        if (!selector.hasContainedContent()) {
            ModMain.LOGGER.info("[支援] 确认时手里已经没东西了（可能刚被送走），忽略");
            return;
        }
        if (PilotSendListPayload.NO_PLAYER.equals(payload.target())) {
            ModMain.LOGGER.info("[支援] 确认的是调试项/占位项，不做任何事");
            return;
        }
        ServerPlayer target = serverPlayer.server.getPlayerList().getPlayer(payload.target());
        if (target == null) {
            ModMain.LOGGER.info("[支援] 确认的目标玩家已不在线，忽略：{}", payload.target());
            return;
        }
        if (selector.startPilotSend(target)) {
            ModMain.LOGGER.info("[支援] 已确认：把东西送到 {} 身边", target.getGameProfile().getName());
        } else {
            ModMain.LOGGER.info("[支援] 确认后没能开始 send（正忙 / 空手）");
        }
    }

    /**
     * 校验"这个玩家此刻确实正在旁观那只选择器"，通过则返回他的服务端玩家对象，否则 {@code null}。
     * <p>
     * 三条：玩家是<b>旁观模式</b>、他的摄像机<b>正是</b> payload 里说的那只选择器、那只选择器在<b>幸运维度</b>。
     * 客户端伪造的包最多只能作用在自己正在旁观的那只身上。
     */
    @Nullable
    private static ServerPlayer pilotedBy(Player player, int selectorId) {
        if (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.isSpectator()) {
            return null;
        }
        Entity camera = serverPlayer.getCamera();
        if (!(camera instanceof LuckySelectorEntity selector) || selector.getId() != selectorId) {
            return null;
        }
        if (!LuckyDimensionMobs.isLuckyDimension(selector.level())) {
            return null; // 需求：只在幸运维度里能操控
        }
        return serverPlayer;
    }

    /**
     * 取这个玩家最近的输入；没上报过、或者已经超过 {@link #INPUT_TIMEOUT_TICKS} 刻没更新则返回 {@code null}
     * （后者意味着"他现在没在操控"，选择器就该把控制权还给 AI）。
     */
    @Nullable
    public static Input inputOf(ServerPlayer player) {
        Input input = INPUTS.get(player.getUUID());
        if (input == null) {
            return null;
        }
        if (player.serverLevel().getGameTime() - input.tick() > INPUT_TIMEOUT_TICKS) {
            return null;
        }
        return input;
    }

    /** 主动忘掉某个玩家的输入（玩家退出时用；不调用也会因为超时自动失效）。 */
    public static void forget(ServerPlayer player) {
        INPUTS.remove(player.getUUID());
    }
}
