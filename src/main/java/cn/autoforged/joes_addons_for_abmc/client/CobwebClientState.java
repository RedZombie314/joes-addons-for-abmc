package cn.autoforged.joes_addons_for_abmc.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 蜘蛛网权杖：客户端维护的“权杖无效化”实体状态。
 *
 * 服务端通过 CobwebNullifyPayload/CobwebClearPayload 通知客户端某实体的权杖被
 * 无效化或已解除。客户端据此在对应实体手持的权杖上方渲染蛛网覆盖层（仅渲染层，
 * 服务端负责 30 秒到期与中键解除）。
 */
public final class CobwebClientState {
    private CobwebClientState() {
    }

    /** 无效化渲染持续时长（毫秒），与服务端 600 刻（30 秒）保持一致。 */
    public static final long NULLIFY_DURATION_MS = 30_000L;

    /** entityId -> (时，渲染到系统时间戳) 。 */
    private static final Map<Integer, Long> NULLIFIED_UNTIL = new ConcurrentHashMap<>();

    /** 标记某实体（按 id）的权杖为无效化，覆盖层渲染从现在起持续 30 秒。 */
    public static void nullify(int entityId) {
        NULLIFIED_UNTIL.put(entityId, System.currentTimeMillis() + NULLIFY_DURATION_MS);
    }

    /** 解除某实体的权杖无效化，立即移除覆盖层渲染。 */
    public static void clear(int entityId) {
        NULLIFIED_UNTIL.remove(entityId);
    }

    /** 查询某实体的权杖当前是否处于无效化（未过期）状态。 */
    public static boolean isNullified(int entityId) {
        Long until = NULLIFIED_UNTIL.get(entityId);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            NULLIFIED_UNTIL.remove(entityId);
            return false;
        }
        return true;
    }

    /** 所有当前处于无效化（未过期）状态的实体 id。供渲染层在对应实体身上绘制蛛网覆盖层。 */
    public static java.util.List<Integer> getNullifiedIds() {
        long now = System.currentTimeMillis();
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Integer, Long> en : NULLIFIED_UNTIL.entrySet()) {
            if (en.getValue() > now) {
                out.add(en.getKey());
            } else {
                NULLIFIED_UNTIL.remove(en.getKey());
            }
        }
        return out;
    }

    /** 离开世界时清空所有无效化状态，避免跨存档残留。 */
    public static void reset() {
        NULLIFIED_UNTIL.clear();
    }
}