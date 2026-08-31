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

    /** “渲染替换”目标生物实体类型 id（如 "minecraft:creeper"）；非生物形态为空串。 */
    private static volatile String morphEntityType = "";

    /** 内存中的代理生物实体（不进世界），仅用于把玩家渲染成对应生物。 */
    private static net.minecraft.world.entity.LivingEntity morphProxy;

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

    /** 本地玩家被变成的跟随实体ID（未变形时为 -1）。供客户端穿透判定：投药水等右键操作需穿过自己的壳。 */
    public static int getFollowEntityId() {
        return followEntityId;
    }

    /** 是否处于“渲染替换”生物形态（客户端把玩家渲染为 {@link #morphEntityType} 对应的生物）。 */
    public static boolean isMorphActive() {
        return transmuted && morphEntityType != null && !morphEntityType.isBlank();
    }

    /** 渲染替换的目标生物实体类型 id（空串表示非生物形态）。 */
    public static String getMorphEntityType() {
        return morphEntityType;
    }

    /** 渲染替换用的代理生物实体（无则返回 null）。 */
    public static net.minecraft.world.entity.LivingEntity getMorphProxy() {
        return morphProxy;
    }

    /** 渲染替换的目标生物默认碰撞箱尺寸（供 Player#getDimensions 用）；非变形时返回 null。 */
    public static net.minecraft.world.entity.EntityDimensions morphDimensionsLocal() {
        if (!isMorphActive()) return null;
        net.minecraft.resources.ResourceLocation rl =
            net.minecraft.resources.ResourceLocation.tryParse(morphEntityType);
        if (rl == null || !net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
            return null;
        }
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(rl).getDimensions();
    }

    /** 服务端通知变形开始/结束（{@link cn.autoforged.joes_addons_for_abmc.network.TransmutationStatePayload}）。 */
    public static void onTransmutationState(boolean t, int entityId, String morphType) {
        transmuted = t;
        followEntityId = t ? entityId : -1;
        morphEntityType = (t && morphType != null) ? morphType : "";
        morphProxy = null;
        // 本地玩家碰撞箱尺寸同步：变形开始/结束都刷新一次，使客户端本地玩家立即采用/释放生物尺寸，
        // 否则尺寸只在服务端生效，客户端要等姿势变化(下蹲)才重算，导致“必须下蹲才调整”，
        // 且小碰撞箱穿缝时被客户端默认大碰撞箱拖慢。
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.refreshDimensions();
        }
        // 重置平滑跟随的基准：新的变形过程从实体当前位置重新起步
        hasLastFollow = false;
        hasPrevPlayer = false;
    }

    /** 离开世界/切换存档时复位，避免跨存档残留。 */
    public static void reset() {
        transmuted = false;
        followEntityId = -1;
        morphEntityType = "";
        morphProxy = null;
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
        if (!transmuted) return;
        // 渲染替换形态：维护一个内存代理生物，把玩家渲染成它（无独立跟随实体）
        if (isMorphActive()) {
            tickMorphProxy(mc);
            return;
        }
        if (followEntityId < 0) return;
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

    /** 渲染替换：每客户端 tick 维护一个内存代理生物实体（不进世界），供渲染时把它画在玩家位置。
     *  关键：勿用 moveTo 每帧复位旋转（会重置 yRotO 等导致头部鬼畜来回看），须把玩家带动画的
     *  旋转旧值/位移/四肢摆动字段同步给代理，才能正确走路且头不乱转。 */
    private static void tickMorphProxy(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (morphProxy == null) {
            net.minecraft.resources.ResourceLocation rl =
                net.minecraft.resources.ResourceLocation.tryParse(morphEntityType);
            if (rl == null || !net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
                return;
            }
            net.minecraft.world.entity.Entity e =
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(rl).create(mc.level);
            if (e instanceof net.minecraft.world.entity.LivingEntity le) {
                morphProxy = le;
            } else {
                return;
            }
        }
        Player player = mc.player;
        // 每 tick 强制本地玩家碰撞箱为生物尺寸，抵抗姿势切换/其他 refreshDimensions 用默认箱覆盖（否则钻细缝减速）
        net.minecraft.world.entity.EntityDimensions md = morphDimensionsLocal();
        if (md != null) {
            player.dimensions = md;
            // 低矮空间：MC 会按玩家身高把其置为“游泳/爬行(crawl)”导致减速；箱已变小可直接站立钻过，
            // 故非水中却处于 SWIMMING(即爬行) 时强制回站立，以正常走路速度通过一格高空间。
            if (player.getPose() == net.minecraft.world.entity.Pose.SWIMMING && !player.isInWater()) {
                player.setPose(net.minecraft.world.entity.Pose.STANDING);
            }
        }
        // 1) 先同步 tick 所依赖的状态
        morphProxy.setPos(player.getX(), player.getY(), player.getZ());
        morphProxy.onGround = player.onGround;
        morphProxy.setDeltaMovement(player.getDeltaMovement());
        morphProxy.setPortalCooldown(300); // 抑制传送门附近触发传送动画/粒子
        // 2) 让代理真正 tick 一次：推进其内部默认动画字段（鸡/鹦鹉的 flap/flapSpeed、末影螨摆动等）。
        //    客户端 tick 只推进动画与状态、不执行移动物理（travel 仅在服务端 aiStep 调用），故安全。
        try {
            morphProxy.tick();
        } catch (Throwable ignored) {
        }
        // 防御：极端情况下代理被判定死亡/内部伤害时，重置存活与状态
        if (morphProxy.getHealth() <= 0.0F) {
            morphProxy.setHealth(morphProxy.getMaxHealth());
        }
        // 3) 用玩家状态覆盖 tick 可能改动的旋转/位置/插值，保证渲染正确
        morphProxy.moveTo(player.getX(), player.getY(), player.getZ(),
            player.getYRot(), player.getXRot());
        morphProxy.onGround = player.onGround;
        morphProxy.fallDistance = player.fallDistance;
        // 依赖实体的“渲染年龄(tickCount)”的默认动画持续播放（如末影螨左右摆动、僵尸臂摆动等）。
        // 以玩家的 tickCount 作为其动画时间轴，随游戏时间推进。
        morphProxy.tickCount = player.tickCount;
        // 位置插值旧值
        morphProxy.xo = player.xo;
        morphProxy.yo = player.yo;
        morphProxy.zo = player.zo;
        morphProxy.xOld = player.xOld;
        morphProxy.yOld = player.yOld;
        morphProxy.zOld = player.zOld;
        // 旋转平滑：同步新旧值，避免头部鬼畜
        morphProxy.yRotO = player.yRotO;
        morphProxy.xRotO = player.xRotO;
        morphProxy.yBodyRotO = player.yBodyRotO;
        morphProxy.setYBodyRot(player.yBodyRot);
        morphProxy.yHeadRotO = player.yHeadRotO;
        morphProxy.setYHeadRot(player.yHeadRot);
        // 走路动画：位移同步玩家（四肢摆动由 updateWalkAnimation 驱动）
        morphProxy.walkDistO = player.walkDistO;
        morphProxy.walkDist = player.walkDist;
        // 用玩家本帧 XZ 位移驱动代理四肢摆动，从而产生走/跑的腿脚动画（Morph 由玩家移动驱动生物动画）
        try {
            float mx = (float) (player.getX() - player.xo);
            float mz = (float) (player.getZ() - player.zo);
            ((cn.autoforged.joes_addons_for_abmc.mixin.LivingEntityAccessorMixin) (Object) morphProxy)
                .jafa_updateWalkAnimation((float) Math.sqrt(mx * mx + mz * mz));
        } catch (Throwable ignored) {
        }
        morphProxy.setDeltaMovement(player.getDeltaMovement());
    }
}
