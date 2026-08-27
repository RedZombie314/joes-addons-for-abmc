package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.entity.TransmutationFallingBlockEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 变形药水（客户端）：
 * 1) 视角：把玩家视角强制切换为第三人称 / 恢复第一人称，并设置初始俯仰（斜向下45°，便于看到脚下的“自己”），
 *    由 {@link cn.autoforged.joes_addons_for_abmc.network.TransmutationCameraPayload} 驱动；
 * 2) 平滑跟随：处于变形状态时，把被变成的实体（物品/下落方块/壳）每 tick 平滑贴到本地玩家脚下。
 *    实现方式与 /tp 的平滑动画一致：用 setPos 移动实体而保留上一位置到 xo/yo/zo，让渲染器在
 *    xo→x 之间做插值；同时按玩家本 tick 位移同幅移动（零延迟贴身）并按剩余距离做指数校正。
 *    在 ClientTickEvent.Post 中调用（此时实体本 tick 已 tick 完，我们设置的位置会直接用于渲染）。
 * 3) 彻底隐身：供渲染事件判断是否隐藏变形玩家自身（含手持物与穿戴装备）。
 *    由 {@link cn.autoforged.joes_addons_for_abmc.network.TransmutationStatePayload} 驱动。
 */
public final class TransmutationCameraClient {

    private TransmutationCameraClient() {
    }

    /** 是否正处于变形状态（客户端）。 */
    private static volatile boolean transmuted = false;

    /** 被变成实体的实体ID（服务端每 tick 会把它的位置设到玩家处；客户端再贴一层保证平滑）。 */
    private static volatile int followEntityId = -1;

    /** 上一 tick 我们为实体设置的位置（用作渲染插值起点 xo/yo/zo），保证平滑过渡。 */
    private static double lastFollowX, lastFollowY, lastFollowZ;
    private static boolean hasLastFollow = false;

    /** 本地玩家上一 tick 的位置（用于“随玩家同幅移动”，保证零延迟贴身不落后）。 */
    private static double prevPlayerX, prevPlayerY, prevPlayerZ;
    private static boolean hasPrevPlayer = false;

    /** 切换当前玩家视角：third=true 强制第三人称（背后），false 恢复第一人称。 */
    public static void setThirdPerson(boolean third) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null) return;
        mc.options.setCameraType(third ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
    }

    /** 进入第三人称时，把玩家初始俯仰设为指定值（Minecraft 中正值=低头，便于看到脚下的“自己”）。 */
    public static void setInitialPitch(float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.setXRot(pitch);
        mc.player.xRotO = pitch;
    }

    /** 是否正处于变形状态（用于隐藏变形玩家自身渲染）。 */
    public static boolean isTransmuted() {
        return transmuted;
    }

    /** 服务端通知变形开始/结束（{@link cn.autoforged.joes_addons_for_abmc.network.TransmutationStatePayload}）。 */
    public static void onTransmutationState(boolean t, int entityId) {
        transmuted = t;
        followEntityId = t ? entityId : -1;
        // 重置平滑跟随的基准：新的变形过程从实体当前位置重新起步
        hasLastFollow = false;
        hasPrevPlayer = false;
    }

    /** 离开世界/切换存档时复位，避免跨存档残留。 */
    public static void reset() {
        transmuted = false;
        followEntityId = -1;
        hasLastFollow = false;
        hasPrevPlayer = false;
    }

    /**
     * 每客户端 tick（ClientTickEvent.Post）调用：让被变成实体平滑贴到本地玩家脚下。
     * 实现要点（与 /tp 平滑动画同原理）：
     * 1) 先按玩家本 tick 位移同幅移动实体，保证零延迟贴身、不落后；
     * 2) 再按剩余距离做指数校正：远（刚变身/玩家瞬移）时快速滑翔收敛，近时柔和贴合；
     * 3) 用 setPos 更新位置，并把上一 tick 位置写回 xo/yo/zo —— 渲染器据此在 xo→x 之间插值，
     *    从而在相邻 tick 之间产生连续平滑的运动（moveTo 会 setOldPosAndRot 把 xo 也改成新位置，
     *    导致完全没有插值、呈现一卡一卡的步进感，故这里必须改用 setPos）。
     */
    public static void tickFollow(Minecraft mc) {
        if (!transmuted || followEntityId < 0) return;
        if (mc.player == null || mc.level == null) return;
        Entity e = mc.level.getEntity(followEntityId);
        if (e == null) return;
        Player player = mc.player;
        // 与服务端 makeTransmutedFollowPlayer 保持一致的 Y 偏移：
        // 下落方块贴地面（实体 Y = 玩家脚底，无高度落差）；生物/壳脚底对齐；物品抬到中心点
        double targetY;
        if (e instanceof TransmutationFallingBlockEntity || e instanceof LivingEntity) {
            targetY = player.getY();
        } else {
            targetY = player.getY() + e.getBbHeight() * 0.5;
        }

        double curX = e.getX(), curY = e.getY(), curZ = e.getZ();

        // 1) 随玩家本 tick 位移同幅移动；首个 tick 无基准则原地起步
        double nx, ny, nz;
        if (hasPrevPlayer) {
            nx = curX + (player.getX() - prevPlayerX);
            ny = curY + (targetY - prevPlayerY);
            nz = curZ + (player.getZ() - prevPlayerZ);
        } else {
            nx = curX;
            ny = curY;
            nz = curZ;
        }
        prevPlayerX = player.getX();
        prevPlayerY = targetY;
        prevPlayerZ = player.getZ();
        hasPrevPlayer = true;

        // 2) 按剩余距离做指数校正：远处快速滑翔，近处柔和贴合
        double dx = player.getX() - nx;
        double dy = targetY - ny;
        double dz = player.getZ() - nz;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq > 1.0E-4) {
            double corr = distSq > 4.0 ? 0.8 : 0.45;
            nx += dx * corr;
            ny += dy * corr;
            nz += dz * corr;
        } else {
            // 极近直接贴合，避免无限逼近
            nx = player.getX();
            ny = targetY;
            nz = player.getZ();
        }

        // 3) 保留上一 tick 位置作为渲染插值起点，然后更新位置
        if (hasLastFollow) {
            e.xo = lastFollowX;
            e.yo = lastFollowY;
            e.zo = lastFollowZ;
        }
        e.setPos(nx, ny, nz);
        lastFollowX = nx;
        lastFollowY = ny;
        lastFollowZ = nz;
        hasLastFollow = true;

        // 清除可能残留的运动量，避免客户端自身物理把实体拖走造成抖动
        e.setDeltaMovement(Vec3.ZERO);
        e.setNoGravity(true);
    }
}
