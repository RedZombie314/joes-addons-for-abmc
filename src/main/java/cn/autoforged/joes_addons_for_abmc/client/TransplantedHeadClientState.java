package cn.autoforged.joes_addons_for_abmc.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端“移植头”状态：记录哪些实体身上带有移植头以及头来源类型。
 * 由 TransplantedHeadPayload（服务端→客户端）驱动，供 TransplantedHeadRenderer 在世界空间渲染。
 */
public final class TransplantedHeadClientState {

    private static final Map<Integer, String> HEAD_TYPES = new HashMap<>();

    private TransplantedHeadClientState() {
    }

    /** 记录/更新某实体的移植头来源类型；空串表示清除（无移植头）。 */
    public static void setHeadType(int entityId, String headTypeId) {
        if (headTypeId == null || headTypeId.isEmpty()) {
            HEAD_TYPES.remove(entityId);
        } else {
            HEAD_TYPES.put(entityId, headTypeId);
        }
    }

    /** 读取某实体的移植头来源类型；无则返回空串。 */
    public static String getHeadType(int entityId) {
        return HEAD_TYPES.getOrDefault(entityId, "");
    }

    /** 返回当前记录的所有实体 id 快照（供渲染时遍历）。 */
    public static List<Integer> getEntityIds() {
        return new ArrayList<>(HEAD_TYPES.keySet());
    }

    /** 清空所有状态（如退出存档/服务器时调用，避免跨世界残留）。 */
    public static void clear() {
        HEAD_TYPES.clear();
    }
}