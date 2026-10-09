package cn.autoforged.joes_addons_for_abmc.entity;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import cn.autoforged.joes_addons_for_abmc.item.ModItems;

/**
 * 黑暗分身的行为接入点。全部由 ModMain 在构造阶段注册到 {@code NeoForge.EVENT_BUS}。
 */
public final class DarkCloneEvents {

    /** 「已记录！」提示。 */
    private static final Component RECORDED_MESSAGE =
        Component.translatable("joes_addons_for_abmc.dark_clone.recorded");

    private DarkCloneEvents() {
    }

    /**
     * 每刻检测，目前只承载召唤链第 1 步（首次双手同时持有 Minecraft Game Icon 时的重命名）。
     *
     * <p><b>为什么用 {@link PlayerTickEvent} 而不是 {@code EntityTickEvent}：</b>
     * 服务端玩家并不通过 {@code Entity#tick()} 计时，而是走
     * {@code ServerPlayer#doTick()}（由 {@code ServerGamePacketListenerImpl#tick()} 调用），
     * 而 {@code EntityTickEvent} 是在 {@code Entity#tick()} 处触发的——用它收不到服务端玩家。
     */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (!isUnrenamedGameIcon(main) || !isUnrenamedGameIcon(off)) return;

        DarkCloneSavedData data = DarkCloneSavedData.get(level);
        if (data.iconPairRenamed) return;

        DarkCloneHelper.renameIcon(main, DarkCloneHelper.ICON_POSITIVE, DarkCloneHelper.POSITIVE_ICON_NAME);
        DarkCloneHelper.renameIcon(off, DarkCloneHelper.ICON_NEGATIVE, DarkCloneHelper.NEGATIVE_ICON_NAME);
        data.iconPairRenamed = true;
        data.setDirty();
    }

    /**
     * 是否是「未被重命名过的」Minecraft Game Icon。
     *
     * <p>加上「没有自定义名」这一条，是为了让判定忠于「持有 Minecraft Game Icon」的字面含义：
     * 重命名后的物品注册 id 依然是 {@code game_icon}，若只比对物品类型，
     * 一对已经叫白 / 黑草方块的图标也会被算作「同时持有 Minecraft Game Icon」。
     */
    private static boolean isUnrenamedGameIcon(ItemStack stack) {
        return stack.getItem() == ModItems.GAME_ICON.get() && !stack.has(DataComponents.CUSTOM_NAME);
    }

    /**
     * 左键动作：客户端按下左键后由 {@code DarkCloneRecordPayload} 调到这里。
     *
     * <p>一次左键按准星命中的东西分成两种：
     * <ul>
     *   <li>命中<b>黑暗分身</b>且持有黑草方块 → 原地黑色粒子后直接删除该实体；</li>
     *   <li>命中<b>其它生物</b>且处于完整姿态（主手黑草方块、副手白草方块）→ 记录进召唤池。</li>
     * </ul>
     *
     * <p>为什么走网络包而不是 {@code AttackEntityEvent}：原版客户端只在准星命中且目标在
     * <b>交互距离内</b>时才发实体交互包，服务端因此天然只能看到约 3 格内的目标，
     * 无法满足「最远 256 格」。左键是纯客户端输入，只能由客户端上报；
     * 而距离、姿态与射线全部由服务端重算（包体为空），客户端无法伪造目标。
     */
    public static void onLeftClickRequest(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        LivingEntity aimed = DarkCloneHelper.raycastLivingEntity(serverPlayer, DarkCloneHelper.DARK_CLONE_REACH);
        if (aimed == null) return;

        // 黑暗分身：抹除。只要求持有黑草方块，不要求完整姿态；黑暗分身也不进召唤池。
        if (DarkCloneAttachments.isDarkClone(aimed)) {
            if (!DarkCloneHelper.holdsBlackGrassBlock(serverPlayer)) return;
            DarkCloneHelper.spawnVanishParticles(level, aimed);
            aimed.discard();
            return;
        }

        // 其它生物：记录
        if (!DarkCloneHelper.isDarkCloneStance(serverPlayer)) return;
        DarkCloneHelper.rememberCreature(level, serverPlayer, aimed.getType());
        serverPlayer.displayClientMessage(RECORDED_MESSAGE, true);
    }

    /**
     * 持有黑草方块左键黑暗分身：原地留下一团黑色粒子后<b>直接删除</b>该实体。
     *
     * <p>用 {@code discard()} 而不是 {@code kill()}：需求明确是「不是杀死，是直接删除」，
     * 所以不会有死亡动画、不会触发死亡相关逻辑、也不会有掉落与经验
     * （掉落与经验那两条监听器因此只是对「其它途径致死」的兜底）。
     *
     * <p>这是「近距离」的冗余路径：256 格那条走 {@link #onLeftClickRequest}（网络包），
     * 但原版左键命中交互距离内的实体会另发一个实体交互包，这条路径负责把它取消掉，
     * 免得分身先挨一下再消失。两条路径都靠 {@code isRemoved()} 判重，不会重复出粒子。
     */
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        if (target.isRemoved()) return;
        if (!DarkCloneAttachments.isDarkClone(target)) return;
        if (!DarkCloneHelper.isNegativeIcon(player.getMainHandItem())
            && !DarkCloneHelper.isNegativeIcon(player.getOffhandItem())) {
            return;
        }

        event.setCanceled(true);
        DarkCloneHelper.spawnVanishParticles(level, target);
        target.discard();
    }

    /**
     * 目标变更收口。
     *
     * <p>原版 {@code Mob#setTarget} 是<b>所有</b>目标的唯一写入口（Mob.java:245）：
     * 既包括 targetSelector 里的各种 TargetGoal，也包括大量绕过 goal 的直接调用
     * ——例如 {@code IronGolem#doPush} 靠物理推挤触发、1/20 概率把撞到的敌人设为目标，
     * 这种就不受 {@code removeAllGoals} 约束。挂在这里可以一次覆盖全部。
     *
     * <p>判定用 {@link DarkCloneHelper#shouldNotAttack}，因此
     * 「不索敌 Owner」这条也顺带覆盖到了所有直接调 {@code setTarget} 的生物
     * （此前它只写在选目标 goal 里，对绕开 goal 的生物无效）。
     *
     * <p>用 {@code setCanceled(true)} 而不是把新目标改成 {@code null}：
     * 前者保留当前（合法的）目标，后者会在分身正打别人、只是碰到队友一下时把目标清掉。
     */
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        LivingEntity proposed = event.getNewAboutToBeSetTarget();
        if (proposed == null) return;
        if (DarkCloneHelper.shouldNotAttack(living, proposed)) {
            event.setCanceled(true);
        }
    }

    /**
     * 伤害收口：黑暗分身对自己不该攻击的目标不造成伤害。
     *
     * <p>这是最后一道、也是覆盖面最广的一道防线，用来兜住<b>根本不经过 {@code setTarget}</b> 的攻击：
     * <ul>
     *   <li>脑记忆类生物（监守者、猪灵、猪灵蛮兵、疣猪兽、僵尸疣猪兽、美西螈、青蛙、旋风人、守卫者）——
     *       它们的攻击目标存在 {@code MemoryModuleType.ATTACK_TARGET} 里，
     *       {@code Mob#getTargetFromBrain()} 与 {@code #getTarget()} 是两个不同的读取口；</li>
     *   <li>凋灵侧头——目标存在同步数据槽 {@code DATA_TARGET_A/B/C} 里；</li>
     *   <li>接触伤害（岩浆怪、史莱姆、河豚等）与凋灵那种「朝随机位置盲射」的溅射伤害。</li>
     * </ul>
     *
     * <p>判定同样用 {@link DarkCloneHelper#shouldNotAttack}，
     * 因此 Owner 不会被打到，创造 / 旁观玩家也一并覆盖。
     * 反方向（Owner 打自己的分身、或 Owner 不同的分身互殴）不受影响。
     */
    public static void onDarkCloneDamageGuard(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (DarkCloneHelper.shouldNotAttack(attacker, victim)) {
            event.setCanceled(true);
        }
    }

    /**
     * 效果收口：黑暗分身不给自己不该攻击的目标上负面效果（Owner、友方分身、创造 / 旁观玩家）。
     *
     * <p><b>为什么一个监听器就能通吃：</b>监守者的黑暗与远古守卫者的挖掘疲劳
     * <b>走的是同一个助手</b>
     * {@code MobEffectUtil.addEffectToPlayersAround(level, source, ...)}，
     * 它在最后一步把施加者一路传下去：
     * <pre>
     * // MobEffectUtil.java:47-61
     * list.forEach(p -> p.addEffect(new MobEffectInstance(effect), source));
     * </pre>
     * 而 {@code LivingEntity#addEffect} 做的第一件事就是
     * {@code CommonHooks.canMobEffectBeApplied(this, effectInstance, entity)}
     * ——即触发本事件，且 {@code MobEffectEvent.Applicable#getEffectSource()} 就带着这个施加者。
     * 所以不需要为监守者 / 远古守卫者各写一处 mixin。
     *
     * <p><b>顺带说明 bug 成因：</b>该助手自己只过滤了两种玩家
     * ——创造 / 旁观（{@code gameMode.isSurvival()}）与<b>同队</b>（{@code !source.isAlliedTo(p)}）。
     * Owner 关系不是队伍，所以 Owner 会被照常施加效果。
     *
     * <p>只拦 {@code MobEffectCategory.HARMFUL}，即需求所说的 debuff；
     * 分身若给 Owner 上增益效果不会被拦。若要连增益一起拦，去掉那一行判断即可。
     *
     * <p>事件用的是 {@code Result} 三态（{@code APPLY / DEFAULT / DO_NOT_APPLY}）而非
     * {@code ICancellableEvent}，所以这里用 {@code setResult(DO_NOT_APPLY)}。
     */
    public static void onDarkCloneEffectGuard(MobEffectEvent.Applicable event) {
        if (!(event.getEffectSource() instanceof LivingEntity source)) return;
        if (event.getEffectInstance().getEffect().value().getCategory() != MobEffectCategory.HARMFUL) return;
        if (DarkCloneHelper.shouldNotAttack(source, event.getEntity())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /**
     * 黑暗分身加入世界（含读档、区块重载、跨维度传送）时重新接管索敌。
     * AI goal 不随存档保存，实体重建后必须重挂——与 BeeBoss 那次「读档后行为丢失」是同一类坑。
     */
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (!DarkCloneAttachments.isDarkClone(living)) return;
        DarkCloneHelper.installTargetGoal(living);
    }

    /**
     * Owner 参战：Owner 攻击了某生物，或 Owner 被某生物攻击时，
     * 把它名下（附近的）黑暗分身的最高优先级目标设为该对手，优先级高于自然索敌。
     *
     * <p>创造 / 旁观模式玩家造成的伤害不触发此逻辑（需求原文的「不包括创造模式玩家」）。
     * 多个对手时按触发顺序排队（{@link DarkCloneHelper#pushPriorityTarget} 把新对手插入队首）。
     */
    public static void onOwnerCombat(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;

        Entity attackerEntity = event.getSource().getEntity();
        // 创造 / 旁观玩家的攻击不引起分身的反应
        if (attackerEntity instanceof Player attacker && DarkCloneHelper.isNonCombatant(attacker)) return;

        Player owner = null;
        LivingEntity foe = null;
        if (victim instanceof Player victimPlayer && attackerEntity instanceof LivingEntity attackerLiving) {
            // Owner 被某生物攻击
            owner = victimPlayer;
            foe = attackerLiving;
        } else if (attackerEntity instanceof Player attackerPlayer) {
            // Owner 攻击了某生物
            owner = attackerPlayer;
            foe = victim;
        }
        if (owner == null || foe == null || foe == owner) return;

        for (LivingEntity clone : clonesOwnedBy(level, owner)) {
            DarkCloneHelper.pushPriorityTarget(clone, foe);
        }
    }

    private static List<LivingEntity> clonesOwnedBy(ServerLevel level, Player owner) {
        return level.getEntitiesOfClass(LivingEntity.class,
            owner.getBoundingBox().inflate(DarkCloneHelper.OWNER_RESPONSE_RANGE),
            entity -> DarkCloneAttachments.isDarkClone(entity) && DarkCloneHelper.isOwner(entity, owner));
    }

    /** 黑暗分身「因其它途径致死」时也不掉落任何物品（正常抹除路径走 discard，不经过这里）。 */
    public static void onLivingDrops(LivingDropsEvent event) {
        if (DarkCloneAttachments.isDarkClone(event.getEntity())) {
            event.getDrops().clear();
        }
    }

    /** 黑暗分身「因其它途径致死」时也不掉落任何经验。 */
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (DarkCloneAttachments.isDarkClone(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
