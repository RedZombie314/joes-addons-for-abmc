package cn.autoforged.joes_addons_for_abmc.client;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端：<b>附体空壳那条铁抓钩铁链</b>的状态（用户指定）。
 *
 * <p>只存两个实体 id（空壳与被拉的玩家），铁链每帧按这两个实体的实时插值位置现画 ——
 * 起点终点都跟着实体走，所以拉扯过程中"人越拉越近、链越收越短"是自然发生的，
 * 不需要服务端逐刻同步坐标（对比铁块权杖那条链，那边起点是本地玩家、终点要服务端周期同步）。
 *
 * <p><b>按空壳分别记</b>（一张表：空壳 id → 玩家 id）：几具空壳同时钩人是可能的，
 * 用单个全局状态的话后一条链会把前一条顶掉。收链也按空壳 id 单独收。
 *
 * <p>清除有三条路：服务端发来的"收链"包（{@code OrbHookChainPayload}），
 * 以及两个实体里任意一个在客户端已经不存在（空壳死了/走远了、玩家换维度）。
 * 后者是兜底：包一旦丢了、或者实体先一步消失，链也不会永远挂在半空中。
 */
public final class OrbHookChainClient {

    /** 空壳实体 id → 被拉的玩家实体 id。 */
    private static final Map<Integer, Integer> CHAINS = new HashMap<>();

    private OrbHookChainClient() {
    }

    /**
     * 收到服务端的铁链同步。
     *
     * @param shellId  空壳实体 id；{@link cn.autoforged.joes_addons_for_abmc.network.OrbHookChainPayload#NONE}
     *                 = 把所有铁链都收掉
     * @param targetId 玩家实体 id；{@code NONE} = 只收掉这条（{@code shellId} 那条）
     */
    public static void update(int shellId, int targetId) {
        if (shellId < 0) {
            CHAINS.clear();
            return;
        }
        if (targetId < 0) {
            CHAINS.remove(shellId);
            return;
        }
        CHAINS.put(shellId, targetId);
    }

    /** 收掉某一具空壳那条链（客户端自己发现实体没了时也走这里）。 */
    public static void remove(int shellId) {
        CHAINS.remove(shellId);
    }

    /** 全部收掉。 */
    public static void clear() {
        CHAINS.clear();
    }

    public static boolean isEmpty() {
        return CHAINS.isEmpty();
    }

    /** 当前所有铁链的快照（渲染时遍历用；遍历中可以安全调 {@link #remove(int)}）。 */
    public static Map<Integer, Integer> chains() {
        return CHAINS;
    }
}
