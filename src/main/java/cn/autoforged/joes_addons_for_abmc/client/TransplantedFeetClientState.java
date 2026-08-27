package cn.autoforged.joes_addons_for_abmc.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端“移植脚”状态：记录哪些实体身上带有移植脚以及脚来源类型。
 * 由 TransplantedFeetPayload（服务端→客户端）驱动，供 TransplantedFeetLayer 渲染。
 */
public final class TransplantedFeetClientState {

    private static final Map<Integer, String> FEET_TYPES = new HashMap<>();

    private TransplantedFeetClientState() {
    }

    /** 记录/更新某实体的移植脚来源类型；空串表示清除（无移植脚）。 */
    public static void setFeetType(int entityId, String feetTypeId) {
        if (feetTypeId == null || feetTypeId.isEmpty()) {
            FEET_TYPES.remove(entityId);
        } else {
            FEET_TYPES.put(entityId, feetTypeId);
        }
    }

    /** 读取某实体的移植脚来源类型；无则返回空串。 */
    public static String getFeetType(int entityId) {
        return FEET_TYPES.getOrDefault(entityId, "");
    }

    /** 清空所有状态（退出存档/服务器时调用）。 */
    public static void clear() {
        FEET_TYPES.clear();
    }
}