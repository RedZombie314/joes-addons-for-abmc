package cn.autoforged.joes_addons_for_abmc.client;

import net.minecraft.world.phys.Vec3;

/**
 * 客户端蛛丝线段状态：保存当前蛛丝锚点，用于从玩家（起始跟随）持续渲染到锚点。
 * 锚点为 null 表示当前没有活跃的蛛丝。
 */
public final class CobwebBeamClient {

    private static volatile Vec3 anchor;

    private CobwebBeamClient() {
    }

    /** 设置当前蛛丝锚点（发射新的蛛丝时覆盖旧值）。 */
    public static void start(Vec3 anchor) {
        CobwebBeamClient.anchor = anchor;
    }

    /** 清除蛛丝（到达目的地 / 手动断开 / 下线）。 */
    public static void clear() {
        CobwebBeamClient.anchor = null;
    }

    /** 当前活跃的蛛丝锚点，无则返回 null。 */
    public static Vec3 getAnchor() {
        return anchor;
    }
}