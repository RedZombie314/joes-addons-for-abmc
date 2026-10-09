package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Orb Possession 的<b>跨存档存续</b>与兜底收尾。
 *
 * <h2>附体要能"退出存档再重进还在"</h2>
 * 附体状态本来就活在<b>核心实体</b>上（{@code PossessorUuid}/{@code OrbState} 都随实体 NBT 落盘，
 * 玩家数据里另存了一份"附体前的游戏模式"）。但玩家一进存档，服务端会把他按存档里的模式放下来，
 * 而"附体期间必须是旁观"这件事只有核心在 tick 里维护 —— 核心所在区块还没加载的那几刻就是个空档。
 * 所以这里在登录时主动接一次管：
 * <ol>
 *   <li>找出<b>任意维度</b>里属于该玩家、且处于 POSSESSING 的核心；</li>
 *   <li>找到 → 调 {@link OrbOfLuckEntity#reattachOnLogin} 把他拉回旁观，附体继续（直到那具附体空壳被打死）；</li>
 *   <li>找不到 → <b>不急着下结论</b>：刚登录那一刻目标区块/实体可能还没加载完，
 *       先挂进 {@link #PENDING_RECOVERY} 宽限 {@link #RECOVERY_GRACE_TICKS} 刻，每刻复查；</li>
 *   <li>宽限期过了还是没有核心 → 说明它是在玩家离线期间被打死的（附体空壳会主动打架），
 *       这时才补一次 Retreat 的收尾：把游戏模式恢复成"附体前"的那个。</li>
 * </ol>
 * 之前那版是登录时直接按 {@code inflate(64)} 就近找核心、找不到立刻恢复模式，于是"重进存档"
 * 几乎必然踩到"核心还没加载"，附体就这么被自己人收掉了。
 */
@EventBusSubscriber(modid = ModMain.MODID)
public final class OrbPossessionEvents {

    /** 存在玩家 persistentData 里的"附体前游戏模式"（{@link GameType#getId()}）。 */
    public static final String TAG_PREV_GAMEMODE = "jafa_orb_prev_gamemode";

    /**
     * 登录后"还没找到核心"的玩家 → 宽限到期的游戏时间。
     * <p>
     * 100 刻（5 秒）足够玩家所在区块连实体一起加载完（核心和空壳就在他身边那一块）。
     */
    private static final int RECOVERY_GRACE_TICKS = 100;

    private static final Map<UUID, Long> PENDING_RECOVERY = new HashMap<>();

    private OrbPossessionEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        if (!player.getPersistentData().contains(TAG_PREV_GAMEMODE)) {
            PENDING_RECOVERY.remove(id);
            return;
        }

        OrbOfLuckEntity orb = findPossessingOrb(player);
        if (orb != null) {
            orb.reattachOnLogin(player);
            PENDING_RECOVERY.remove(id);
            return;
        }
        // 没有核心的同化（被刷怪蛋放出来的那具空壳干的）：<b>不挂"核心丢失"的收尾</b>。
        // 那种同化本来就没有核心，若走 finishRetreat 就会被当成"核心已死"当场放出来，
        // 留下一具没人管的空壳。这里交回普通的同化维护（见 tickCorelessAssimilations）。
        if (cn.autoforged.joes_addons_for_abmc.entity.OrbAssimilation.isAssimilating(player)) {
            PENDING_RECOVERY.remove(id);
            return;
        }

        // 还没找到核心：挂起等待加载，别急着把附体收掉
        PENDING_RECOVERY.put(id, player.level().getGameTime() + RECOVERY_GRACE_TICKS);
    }

    /**
     * <b>玩家换维度时：把空壳血条重新挂一遍</b>（用户实测的 bug：主世界 {@code /tp} 到下界之后，
     * 在下界放的空壳没有血条）。
     *
     * <p>原因在 {@code ServerBossEvent}：它对"<b>已经在观众集合里</b>的玩家"调 {@code addPlayer}
     * 是<b>空操作</b>（不会再发包）。而换维度那一刻客户端会把整块界面状态重建一遍，
     * 一旦那一次 ADD 没落到客户端，这条血条就再也回不来 —— 服务端以为"他早就看着呢"。
     * 这里只是记一个记号，真正的"先摘掉再挂上"在
     * {@link PlayerShellEntity#tickShellBossBar()} 里做（那边才知道有哪些血条）。
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerShellEntity.markBossBarResend(player);
        }
    }

    /** 宽限期内每刻复查一次；找到核心就接管，超过期限就按"核心已死"补收尾。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        Long deadline = PENDING_RECOVERY.get(id);
        if (deadline == null) {
            return;
        }

        OrbOfLuckEntity orb = findPossessingOrb(player);
        if (orb != null) {
            orb.reattachOnLogin(player);
            PENDING_RECOVERY.remove(id);
            return;
        }
        if (player.level().getGameTime() >= deadline) {
            PENDING_RECOVERY.remove(id);
            finishRetreat(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_RECOVERY.remove(event.getEntity().getUUID());
    }

    /**
     * 在<b>所有维度</b>里找"属于这个玩家、且处于 POSSESSING 的核心"：
     * 要么他是被<b>附体</b>的那个，要么他正被<b>同化</b>（{@link OrbAssimilation}）。
     * <p>
     * 不用就近搜索（{@code inflate(64)}）是因为那会连带要求"核心此刻正在玩家附近加载着"，
     * 而登录瞬间这个前提并不成立；遍历已加载实体则只看"它在不在这个世界里"。
     */
    private static OrbOfLuckEntity findPossessingOrb(ServerPlayer player) {
        if (player.getServer() == null) {
            return null;
        }
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof OrbOfLuckEntity orb
                    && orb.getOrbState() == OrbOfLuckEntity.OrbState.POSSESSING
                    && (player.getUUID().equals(orb.getPossessorUuid())
                        || orb.isAssimilating(player.getUUID()))) {
                    return orb;
                }
            }
        }
        return null;
    }

    /** 核心确实没了：把玩家的游戏模式恢复成附体前的那个，并清掉备份记录。 */
    private static void finishRetreat(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        GameType back = GameType.byId(data.getInt(TAG_PREV_GAMEMODE));
        if (player.gameMode.getGameModeForPlayer() != back) {
            player.setGameMode(back);
        }
        data.remove(TAG_PREV_GAMEMODE);
    }
}
