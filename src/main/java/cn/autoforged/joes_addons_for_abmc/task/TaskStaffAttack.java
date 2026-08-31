package cn.autoforged.joes_addons_for_abmc.task;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.item.ModDataComponents;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidUseShieldTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromAttackTargetIfTargetOutOfReach;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 车万女仆联动职业：权杖攻击。
 * <p>
 * 生效前提：女仆主手持有受支持的权杖 —— 被动属性型权杖（金块/钻石块/下界合金块/鸣钟，铁砧除外），
 * Him 权杖（herobrine_head）或红石块权杖（redstone_block）。
 * <ul>
 *   <li>被动属性型权杖：女仆像原版“攻击”职业一样左键近战攻击目标；</li>
 *   <li>Him 权杖：由 {@link MaidHimStaffAttackTask} 接管 —— 默认近战模式（先传送至目标身边再近战），
 *       血量低于最大生命值 1/3 时切换远程模式（在确保头颅不波及友方的前提下发射 Him 头颅，目标过近则近战）；</li>
 *   <li>红石块权杖：由 {@link MaidRedstoneStaffAttackTask} 接管 —— 在女仆与目标之间、以及目标 1 格范围内
 *       均不存在玩家或宠物时，发射充能为 8 的红石激光（模拟玩家长按右键）。</li>
 * </ul>
 * <p>
 * 实现 {@link IAttackTask} 以完全复用女仆原版“攻击”职业的目标筛选逻辑：
 * <ul>
 *   <li>默认只攻击怪物（按 {@code DefaultMonsterType} 分类判定）；</li>
 *   <li>玩家可在女仆的“攻击列表”配置界面进行白名单设置（每只女仆独立的 {@code AttackListData}）；</li>
 *   <li>遵守全局黑名单配置 {@code MaidConfig.MAID_ATTACK_IGNORE}；</li>
 *   <li>不攻击玩家、盔甲架、物品实体、已驯服宠物及“MaidNoAttack”前缀命名的生物。</li>
 * </ul>
 */
public class TaskStaffAttack implements IAttackTask {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "staff_attack");

    /** 被动属性型权杖（铁砧权杖除外）：仅这些权杖会触发女仆近战攻击。 */
    private static final Set<String> PASSIVE_STAFF_TYPES = Set.of(
        "gold_block", "diamond_block", "netherite_block", "bell"
    );

    /** Him 权杖方块形态：由 MaidHimStaffAttackTask 提供传送 + 近战/远程头颅攻击。 */
    public static final String HIM_STAFF_TYPE = "herobrine_head";

    /** 红石块权杖方块形态：由 MaidRedstoneStaffAttackTask 提供充能为 8 的红石激光攻击。 */
    public static final String REDSTONE_STAFF_TYPE = "redstone_block";

    /** 命令方块权杖方块形态：由 MaidCommandStaffAttackTask 按主人的权杖模式执行 kill/禁AI 行为。 */
    public static final String COMMAND_STAFF_TYPE = "command_block";

    /** 索敌半径（格）：权杖攻击固定 64 格，不受女仆工作范围限制。 */
    public static final float SEARCH_RADIUS = 64.0F;

    /** 垂直索敌范围（格）：与女仆默认垂直搜索范围保持一致。 */
    private static final double VERTICAL_SEARCH = 16.0D;

    /**
     * 无法移动（坐下/骑乘/睡觉/被拴绳）时近战自卫的范围（格）。
     * 女仆未注册 {@code ENTITY_INTERACTION_RANGE}，其 {@code isWithinMeleeAttackRange} 仅有默认 3 格，
     * 而 Him 头颅发射要求目标 ≥ 6 格，导致 3~6 格成为“够不着也不射击”的空档；
     * 站姿女仆能走过去，坐下的女仆却卡死在空档里。此范围取 5.5 与 6 格发射下限衔接，
     * 保证坐下女仆对 3~6 格内的敌人也能近战自卫。
     */
    public static final double SITTING_MELEE_RANGE = 5.5D;

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(ModItems.STAFF.get());
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        // 站姿（可移动）任务：含通用走位。
        List<Pair<Integer, BehaviorControl<? super EntityMaid>>> tasks = createCommonAttackTasks(maid);
        // 通用走位（仅在“非 Him 权杖且非红石块权杖”时启用），供普通权杖走近目标近战。
        tasks.add(Pair.of(5, new ConditionalBehavior(
            m -> !MaidHimStaffAttackTask.isHimStaff(m.getMainHandItem())
                && !MaidRedstoneStaffAttackTask.isRedstoneStaff(m.getMainHandItem())
                && !MaidCommandStaffAttackTask.isCommandStaff(m.getMainHandItem()),
            SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(0.6F)
        )));
        return tasks;
    }

    /**
     * 骑乘/坐下（待命）状态下执行的攻击 AI：此时女仆无法移动，只能站桩攻击。
     * <p>
     * 注意：女仆“坐下”时 {@link MaidUpdateActivityFromSchedule} 会把活跃活动从
     * {@code Activity.WORK} 切换到 RIDE_WORK（走 {@code createRideBrainTasks}），
     * 若不覆写本方法（默认空列表），坐下女仆将完全没有攻击行为。
     * <p>
     * 这里复用站姿的攻击行为，但不加入走位（无法移动）；Him 权杖在无法移动时
     * 由 {@link MaidHimStaffAttackTask} 自动切换到远程发射头颅（不传送）。
     */
    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        return createCommonAttackTasks(maid);
    }

    /**
     * 站姿与坐下共用的攻击行为：目标寻找/停手 + Him 权杖专属行为 + 红石块权杖专属行为
     * + 通用近战（条件互斥）+ 盾牌。
     * 通用近战与 Him/红石专属行为互斥，避免同一刻执行多次攻击：
     * 由于 Brain 会尝试启动所有未运行的行为，若不互斥，持 Him 权杖时通用 MaidMeleeAttack
     * 与 himAttack 会在同一刻都执行 doHurtTarget，造成双重伤害；红石块权杖同理（redstoneAttack
     * 自带激光攻击，通用近战与走位均须为其让路）。
     */
    private List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createCommonAttackTasks(EntityMaid maid) {
        // 目标寻找与停手逻辑对所有支持的权杖生效；目标筛选复用 IAttackTask.findFirstValidAttackTarget
        // （内部经 maid.canAttack 走 IAttackTask.canAttack：默认只攻击怪物 + 白名单/黑名单配置），
        // 额外通过 findAttackTarget 过滤：持有 Him 权杖时不对末影龙索敌。
        BehaviorControl<? super EntityMaid> startAttacking = StartAttacking.create(
            this::hasSupportedStaff,
            this::findAttackTarget
        );
        BehaviorControl<? super EntityMaid> stopAttacking = StopAttackingIfTargetInvalid.create(
            target -> !this.hasSupportedStaff(maid) || farAway(target, maid)
        );

        // Him 权杖专属行为：近战模式传送 + 低血量/无法移动时切远程发射头颅（内部自带近战，含攻击冷却）。
        BehaviorControl<? super EntityMaid> himAttack = new MaidHimStaffAttackTask();

        // 红石块权杖专属行为：每刻在安全前提下发射充能为 8 的红石激光（内部自带走位逻辑）。
        BehaviorControl<? super EntityMaid> redstoneAttack = new MaidRedstoneStaffAttackTask();

        // 红石块权杖激光音效会话管理：宽限期结束后结束音效会话、换下权杖时立即结束（laser_end）。
        BehaviorControl<? super EntityMaid> redstoneSound = new MaidRedstoneLaserSoundTask();

        // 命令方块权杖专属行为：按主人设置的权杖模式，击杀模式→瞄准敌怪用 kill 命令击杀并渲染白线；
        // 启用/禁用AI模式→瞄准敌怪，若其 NoAI=0b 则设为 1b（禁AI）并渲染白线；护盾模式由常驻反弹处理；抓取/无模式无行为。
        BehaviorControl<? super EntityMaid> commandAttack = new MaidCommandStaffAttackTask();

        // 通用近战用自研 MaidStaffMeleeAttack（不依赖传感器缓存，且无法移动时近战自卫范围更大）；
        // 与 Him/红石专属行为条件互斥，避免同一刻双重攻击。
        BehaviorControl<? super EntityMaid> meleeAttack = new ConditionalBehavior(
            m -> !MaidHimStaffAttackTask.isHimStaff(m.getMainHandItem())
                && !MaidRedstoneStaffAttackTask.isRedstoneStaff(m.getMainHandItem())
                && !MaidCommandStaffAttackTask.isCommandStaff(m.getMainHandItem()),
            new MaidStaffMeleeAttack()
        );
        BehaviorControl<? super EntityMaid> useShield = new MaidUseShieldTask();

        return Lists.newArrayList(
            Pair.of(5, startAttacking),
            Pair.of(5, stopAttacking),
            Pair.of(5, himAttack),
            Pair.of(5, redstoneAttack),
            Pair.of(5, redstoneSound),
            Pair.of(5, commandAttack),
            Pair.of(5, meleeAttack),
            Pair.of(5, useShield)
        );
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Lists.newArrayList(Pair.of("hold_supported_staff", this::hasSupportedStaff));
    }

    /**
     * 是否视为“武器”：主手持有被动属性型权杖（铁砧除外）、Him 权杖或红石块权杖时视为武器。
     * 供 {@code IAttackTask#onFunctionCallSwitch} 使用，避免女仆切换到此职业时
     * 尝试从背包换装备而顶掉手中的权杖。
     */
    @Override
    public boolean isWeapon(EntityMaid maid, ItemStack stack) {
        if (!stack.is(ModItems.STAFF.get())) return false;
        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        return PASSIVE_STAFF_TYPES.contains(blockType)
            || HIM_STAFF_TYPE.equals(blockType)
            || REDSTONE_STAFF_TYPE.equals(blockType)
            || COMMAND_STAFF_TYPE.equals(blockType);
    }

    /**
     * 生效前提：主手持有受支持的权杖（被动属性型权杖（铁砧除外）、Him 权杖或红石块权杖）。
     */
    private boolean hasSupportedStaff(EntityMaid maid) {
        return isWeapon(maid, maid.getMainHandItem());
    }

    /**
     * 索敌半径固定为 {@link #SEARCH_RADIUS}（64 格），不受女仆工作范围限制，
     * 同时扩大女仆传感器供给 {@code NEAREST_VISIBLE_LIVING_ENTITIES} 的搜索范围。
     */
    @Override
    public float searchRadius(EntityMaid maid) {
        return SEARCH_RADIUS;
    }

    /**
     * 目标寻找：每刻直接在服务端以女仆为中心、半径 {@link #SEARCH_RADIUS} 格内扫描目标，
     * 实时性不受女仆传感器（每 20 刻才刷新一次目标缓存）限制，看到目标即可立刻索敌。
     * 筛选复用 {@code maid.canAttack}（内部走 {@link IAttackTask#canAttack}：默认怪物 + 白名单/黑名单），
     * 并额外要求可见；持有 Him 权杖时不对末影龙索敌。
     */
    private Optional<? extends LivingEntity> findAttackTarget(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) return Optional.empty();
        boolean himStaff = MaidHimStaffAttackTask.isHimStaff(maid.getMainHandItem());
        AABB aabb = maid.getBoundingBox().inflate(SEARCH_RADIUS, VERTICAL_SEARCH, SEARCH_RADIUS);
        List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, aabb, e -> {
            if (e == maid || !e.isAlive()) return false;
            if (himStaff && e.getType() == EntityType.ENDER_DRAGON) return false;
            if (!maid.canAttack(e)) return false;
            return maid.getSensing().hasLineOfSight(e);
        });
        if (list.isEmpty()) return Optional.empty();
        list.sort(Comparator.comparingDouble(maid::distanceToSqr));
        return Optional.of(list.get(0));
    }

    /**
     * 目标无效判定：目标死亡，或女仆未再持有受支持权杖 / 目标超出 {@link #SEARCH_RADIUS}（64 格）时停止攻击。
     */
    private boolean farAway(LivingEntity target, EntityMaid maid) {
        if (!target.isAlive()) return true;
        boolean homeMode = maid.isHomeModeEnable();
        if (!homeMode && maid.getOwner() != null) {
            return maid.getOwner().distanceTo(target) > SEARCH_RADIUS;
        }
        return maid.distanceTo(target) > SEARCH_RADIUS;
    }

    /**
     * 按条件包装一个行为：条件成立时完全委托给内部行为，条件不成立时内部行为无法启动；
     * 若内部行为正在运行而条件失效，则立即将其停止。用于让 Him 权杖专属行为与通用近战行为互斥。
     */
    private static final class ConditionalBehavior implements BehaviorControl<EntityMaid> {
        private final Predicate<EntityMaid> condition;
        private final BehaviorControl<? super EntityMaid> inner;

        ConditionalBehavior(Predicate<EntityMaid> condition, BehaviorControl<? super EntityMaid> inner) {
            this.condition = condition;
            this.inner = inner;
        }

        @Override
        public Behavior.Status getStatus() {
            return inner.getStatus();
        }

        @Override
        public boolean tryStart(ServerLevel level, EntityMaid entity, long gameTime) {
            if (!condition.test(entity)) return false;
            return inner.tryStart(level, entity, gameTime);
        }

        @Override
        public void tickOrStop(ServerLevel level, EntityMaid entity, long gameTime) {
            if (!condition.test(entity)) {
                if (inner.getStatus() == Behavior.Status.RUNNING) {
                    inner.doStop(level, entity, gameTime);
                }
                return;
            }
            inner.tickOrStop(level, entity, gameTime);
        }

        @Override
        public void doStop(ServerLevel level, EntityMaid entity, long gameTime) {
            inner.doStop(level, entity, gameTime);
        }

        @Override
        public String debugString() {
            return "conditional(" + inner.debugString() + ")";
        }
    }
}
