package cn.autoforged.joes_addons_for_abmc.task;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.item.ModDataComponents;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import cn.autoforged.joes_addons_for_abmc.network.MaidLaserSoundPayload;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 女仆“权杖攻击”任务中，红石块权杖（redstone_block）的专属行为。
 * <p>
 * 每刻对准攻击目标发射充能为 8 的红石激光（模拟玩家长按右键持续发射），
 * 仅在满足安全条件时才开火（由 {@link ModMain#executeMaidRedstoneStaffTick} 校验）：
 * <ul>
 *   <li>女仆与目标之间的连线上不存在玩家或其他宠物（含女仆）；</li>
 *   <li>目标 1 格范围内不存在玩家或其他宠物。</li>
 * </ul>
 * 安全条件不满足时女仆不开火（等待条件满足）；若因火线被方块遮挡而无法命中目标，
 * 可移动的女仆会向目标靠近以获得清晰火线，无法移动（坐下/骑乘/睡觉/被拴绳）则原地待命。
 * <p>
 * 激光音效（laser_start → 循环 laser_middle → laser_end）由服务端以「激光是否在持续发射」为会话单位驱动：
 * 首次开火时发送开始事件（laser_start + 开始循环）；持续发射期间若光束末端明显移动则发送位置更新事件，
 * 让循环音效跟随光束连线（离线越近越响）；光束真正停止（无目标/无法开火）并持续超过
 * {@link #END_DEBOUNCE_TICKS} 宽限期后才发送结束事件（停止循环 + laser_end）。因此只要激光持续音效未结束
 * （光束中断不超过宽限期），中途锁定新目标不会播放结束/开始音效，而是继续播放循环音效。
 * 宽限期计时与换下权杖时的结束处理由 {@link MaidRedstoneLaserSoundTask} 统一负责。
 * <p>
 * 该行为只在主手持有红石块权杖且有攻击目标时启动；不持红石块权杖时由
 * {@link TaskStaffAttack} 中的通用近战行为接管（两者通过条件互斥，不会双重攻击）。
 */
public class MaidRedstoneStaffAttackTask extends Behavior<EntityMaid> {

    /** 激光持续音效会话：女仆 UUID -> 会话状态（最近开火时刻 + 最近一次通知客户端的光束末端）。
     *  以「光束是否持续」为会话单位：目标切换不中断会话，仅光束停止超过宽限期才结束会话。 */
    private static final Map<UUID, LaserSession> LASER_SESSIONS = new HashMap<>();

    /** 结束会话的宽限期（刻）：光束停止超过该时长仍未恢复开火才结束激光音效会话（播放 laser_end），
     *  用于避免击杀目标后短暂空档（1~几刻）立刻重新锁敌时出现结束音 + 开始音的突兀切换。 */
    static final long END_DEBOUNCE_TICKS = 10L;

    /** 光束末端移动超过该距离（格）时才发送位置更新，让循环音效跟随连线，减少无谓的包发送。 */
    private static final double UPDATE_DISTANCE_THRESHOLD = 0.25D;

    public MaidRedstoneStaffAttackTask() {
        super(ImmutableMap.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
            1200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return isRedstoneStaff(maid.getMainHandItem()) && getAttackTarget(maid) != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return isRedstoneStaff(maid.getMainHandItem()) && getAttackTarget(maid) != null;
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        LivingEntity target = getAttackTarget(maid);
        if (target == null || !target.isAlive()) {
            // 无目标/目标已死：本次不开火。结束会话与否交由 MaidRedstoneLaserSoundTask
            // 依据宽限期（END_DEBOUNCE_TICKS）判断，避免短暂空档触发结束+开始音效。
            return;
        }
        BehaviorUtils.lookAtEntity(maid, target);

        // 每刻发射充能为 8 的红石激光；安全校验（连线/目标 1 格内无玩家或宠物）不通过时不开火。
        Vec3 beamEnd = ModMain.executeMaidRedstoneStaffTick(maid, target);

        if (beamEnd != null) {
            Vec3 origin = maid.getEyePosition();
            LaserSession session = LASER_SESSIONS.get(maid.getUUID());
            if (session == null) {
                // 全新会话：发送开始事件（laser_start + 在连线上开始循环 laser_middle）
                LASER_SESSIONS.put(maid.getUUID(), new LaserSession(gameTime, beamEnd));
                sendLaserSound(level, maid, MaidLaserSoundPayload.ACTION_START, origin, beamEnd);
            } else if (gameTime - session.lastFiredTick > END_DEBOUNCE_TICKS) {
                // 会话残留但已超时（与音效任务同刻竞争）：先关闭旧循环再开启新会话
                sendLaserSound(level, maid, MaidLaserSoundPayload.ACTION_END, origin, beamEnd);
                LASER_SESSIONS.put(maid.getUUID(), new LaserSession(gameTime, beamEnd));
                sendLaserSound(level, maid, MaidLaserSoundPayload.ACTION_START, origin, beamEnd);
            } else {
                // 会话进行中：只续期；光束末端明显移动时发送位置更新，让循环音效跟随连线
                session.lastFiredTick = gameTime;
                if (session.lastSentEnd == null
                    || session.lastSentEnd.distanceToSqr(beamEnd) > UPDATE_DISTANCE_THRESHOLD * UPDATE_DISTANCE_THRESHOLD) {
                    session.lastSentEnd = beamEnd;
                    sendLaserSound(level, maid, MaidLaserSoundPayload.ACTION_UPDATE, origin, beamEnd);
                }
            }
            // 开火成功则保持站位
            maid.getNavigation().stop();
        } else if (maid.canBrainMoving() && !maid.getSensing().hasLineOfSight(target)) {
            // 未开火（安全条件不满足 / 射线被遮挡）：不立即结束会话，交由音效任务按宽限期处理
            // 火线被方块遮挡（无法直接看到目标）：可移动时向目标靠近以重新获得火线。
            // 仅当“看不到目标”才靠近 —— 若是因为玩家/宠物挡路而无法开火（仍能看到目标），
            // 则原地等待，绝不向玩家/宠物聚集处移动。
            maid.getNavigation().moveTo(target, 1.0);
        }
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        // 攻击行为停止（目标消失等）不立即结束音效会话，由 MaidRedstoneLaserSoundTask 统一管理
        maid.getNavigation().stop();
    }

    /** 每刻由 {@link MaidRedstoneLaserSoundTask} 调用：若激光停止开火超过宽限期仍未恢复，则结束音效会话。 */
    static void tickLaserSoundSession(ServerLevel level, EntityMaid maid, long gameTime) {
        LaserSession session = LASER_SESSIONS.get(maid.getUUID());
        if (session != null && gameTime - session.lastFiredTick > END_DEBOUNCE_TICKS) {
            LASER_SESSIONS.remove(maid.getUUID());
            sendLaserSound(level, maid, MaidLaserSoundPayload.ACTION_END, maid.getEyePosition(), null);
        }
    }

    /** 立即结束激光音效会话（换下权杖/切换职业等）：停止循环并播放 laser_end。 */
    static void endLaserSoundSession(ServerLevel level, EntityMaid maid) {
        if (LASER_SESSIONS.remove(maid.getUUID()) != null) {
            sendLaserSound(level, maid, MaidLaserSoundPayload.ACTION_END, maid.getEyePosition(), null);
        }
    }

    /** 通知附近客户端女仆激光音效事件；音源起点为女仆眼位，光束末端用于定位循环音效。 */
    private static void sendLaserSound(ServerLevel level, EntityMaid maid, int action, Vec3 origin, Vec3 beamEnd) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(maid,
            new MaidLaserSoundPayload(maid.getId(), action,
                origin.x(), origin.y(), origin.z(),
                beamEnd == null ? origin.x() : beamEnd.x(),
                beamEnd == null ? origin.y() : beamEnd.y(),
                beamEnd == null ? origin.z() : beamEnd.z()));
    }

    private LivingEntity getAttackTarget(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    /** 主手是否持有红石块权杖。 */
    private static boolean isHoldingRedstoneStaff(EntityMaid maid) {
        return isRedstoneStaff(maid.getMainHandItem());
    }

    /** 是否为红石块权杖（redstone_block 方块形态的 STAFF 物品）。供 TaskStaffAttack 复用。 */
    static boolean isRedstoneStaff(ItemStack stack) {
        if (!stack.is(ModItems.STAFF.get())) return false;
        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        return "redstone_block".equals(blockType);
    }

    /** 激光音效会话状态。 */
    private static final class LaserSession {
        /** 最近一次成功开火的游戏刻。 */
        long lastFiredTick;
        /** 最近一次通知客户端的激光光束末端点（用于判断是否需要发送位置更新）。 */
        Vec3 lastSentEnd;

        LaserSession(long lastFiredTick, Vec3 lastSentEnd) {
            this.lastFiredTick = lastFiredTick;
            this.lastSentEnd = lastSentEnd;
        }
    }
}
