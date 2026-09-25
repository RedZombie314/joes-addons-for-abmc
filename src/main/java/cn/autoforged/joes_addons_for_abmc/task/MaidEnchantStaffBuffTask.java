package cn.autoforged.joes_addons_for_abmc.task;

import cn.autoforged.joes_addons_for_abmc.item.ModDataComponents;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import cn.autoforged.joes_addons_for_abmc.potion.ModMobEffects;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * 女仆“权杖攻击”任务中，附魔台权杖（enchanting_table）的专属行为（辅助增益，不参与攻击）。
 * <ul>
 *   <li>每 {@link #BUFF_INTERVAL} 游戏刻（2 秒）为女仆附近 {@link #BUFF_RANGE} 格内的玩家（主人）、
 *       其他女仆与已驯服宠物（不含女仆自身）附上一个“附魔状态效果”；</li>
 *   <li>效果按附魔台权杖“日常模式”随机：从原版附魔名注册的占位状态效果池
 *       （{@link ModMobEffects#ENCHANTMENT_EFFECTS} + {@link ModMobEffects#KNOCKBACK}）中抽取
 *       目标尚未拥有的随机效果（黑名单 {@link #BLACKLISTED_EFFECTS} 除外），等级 1~5，
 *       持续 {@link #BUFF_DURATION} 游戏刻（30 秒）；</li>
 *   <li>该行为不依赖攻击目标，只要主手持有附魔台权杖就持续运行；
 *       与通用近战/走位互斥，持有附魔台权杖时女仆专注为友方附魔。</li>
 * </ul>
 */
public class MaidEnchantStaffBuffTask extends Behavior<EntityMaid> {
    /** 施法间隔（tick）：每 2 秒（40 刻）一次。 */
    private static final long BUFF_INTERVAL = 40;
    /** 附魔状态效果持续时间（tick）：30 秒（600 刻）。 */
    private static final int BUFF_DURATION = 600;
    /** 附魔范围（格）：以女仆为中心搜索玩家/其他女仆/宠物的半径。 */
    private static final double BUFF_RANGE = 16.0;
    /** 黑名单效果（注册名路径）：不附给友方，避免其状态效果带来的副作用。目前包含引雷（channeling）、忠诚（loyalty）。 */
    private static final java.util.Set<String> BLACKLISTED_EFFECTS = java.util.Set.of("channeling", "loyalty");

    /** 下一次施法的游戏时间；-1 表示尚未开始。 */
    private long nextBuffAt = -1;

    public MaidEnchantStaffBuffTask() {
        super(ImmutableMap.of(), 1200);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        nextBuffAt = gameTime + BUFF_INTERVAL;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return isEnchantStaff(maid.getMainHandItem());
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return isEnchantStaff(maid.getMainHandItem());
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (gameTime < nextBuffAt) return;
        nextBuffAt = gameTime + BUFF_INTERVAL;

        // 为范围内每个友方（主人/其他女仆/已驯服宠物）各附上一个随机附魔状态效果
        for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class,
            maid.getBoundingBox().inflate(BUFF_RANGE), e -> e != maid && e.isAlive() && isAlly(maid, e))) {
            buffAlly(level, ally);
        }
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        nextBuffAt = -1;
    }

    /** 是否为受保护友方：主人（玩家）、其他女仆、已驯服宠物。 */
    private boolean isAlly(EntityMaid maid, LivingEntity e) {
        if (e == maid.getOwner()) return true;
        if (e instanceof EntityMaid) return true;
        return e instanceof TamableAnimal tamed && tamed.getOwnerUUID() != null;
    }

    /**
     * 按附魔台权杖“日常模式”为友方附上一个随机附魔状态效果（1~5 级，持续 {@link #BUFF_DURATION} 刻）。
     * 若目标已拥有刷出的效果则跳过该目标，避免重复叠加。
     */
    private void buffAlly(ServerLevel level, LivingEntity ally) {
        List<DeferredHolder<MobEffect, MobEffect>> candidates =
            new ArrayList<>(ModMobEffects.ENCHANTMENT_EFFECTS);
        candidates.add(ModMobEffects.KNOCKBACK);
        if (candidates.isEmpty()) return;
        // 黑名单效果不附给友方（引雷/忠诚等）
        candidates.removeIf(holder -> {
            ResourceLocation id = holder.getId();
            return id != null && BLACKLISTED_EFFECTS.contains(id.getPath());
        });
        candidates.removeIf(holder -> ally.hasEffect(holder));
        if (candidates.isEmpty()) return;

        DeferredHolder<MobEffect, MobEffect> effect =
            candidates.get(ally.getRandom().nextInt(candidates.size()));
        int enchLevel = 1 + ally.getRandom().nextInt(5); // 日常模式等级 1~5
        ally.addEffect(new MobEffectInstance(effect, BUFF_DURATION, enchLevel - 1, false, false, false));
        level.playSound(null, ally.getX(), ally.getY(), ally.getZ(),
            SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** 是否为附魔台权杖（enchanting_table 方块形态的 STAFF 物品）。供 TaskStaffAttack 复用。 */
    static boolean isEnchantStaff(ItemStack stack) {
        if (!stack.is(ModItems.STAFF.get())) return false;
        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        return "enchanting_table".equals(blockType);
    }
}
