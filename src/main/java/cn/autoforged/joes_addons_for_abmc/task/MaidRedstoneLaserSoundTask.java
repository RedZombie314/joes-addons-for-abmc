package cn.autoforged.joes_addons_for_abmc.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;

/**
 * 女仆红石块权杖的激光音效会话管理行为（与 {@link MaidRedstoneStaffAttackTask} 搭配使用）。
 * <p>
 * 该行为只要女仆主手持有红石块权杖就持续运行，负责：
 * <ul>
 *   <li>每刻检查 {@link MaidRedstoneStaffAttackTask#tickLaserSoundSession}：激光停止开火超过
 *       {@link MaidRedstoneStaffAttackTask#END_DEBOUNCE_TICKS} 宽限期仍未恢复时，结束音效会话（停止
 *       循环并播放 laser_end）。这样击杀目标后短暂的重新索敌空档不会触发结束音 + 开始音的突兀切换；</li>
 *   <li>行为停止（女仆换下权杖/切换职业/活动切换）时立即结束音效会话（laser_end），避免循环音效残留。</li>
 * </ul>
 * 会话的开始事件（laser_start + 开始循环）由 {@link MaidRedstoneStaffAttackTask} 在首次开火时发送。
 */
public class MaidRedstoneLaserSoundTask extends Behavior<EntityMaid> {

    public MaidRedstoneLaserSoundTask() {
        super(ImmutableMap.of(), 1200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return MaidRedstoneStaffAttackTask.isRedstoneStaff(maid.getMainHandItem());
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return MaidRedstoneStaffAttackTask.isRedstoneStaff(maid.getMainHandItem());
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        // 激光停止超过宽限期仍未恢复开火 → 结束音效会话
        MaidRedstoneStaffAttackTask.tickLaserSoundSession(level, maid, gameTime);
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        // 女仆不再持有红石块权杖（换下/切换职业等）→ 立即结束音效会话
        MaidRedstoneStaffAttackTask.endLaserSoundSession(level, maid);
    }
}
