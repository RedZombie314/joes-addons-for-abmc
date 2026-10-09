package cn.autoforged.joes_addons_for_abmc;

import cn.autoforged.joes_addons_for_abmc.entity.BeeBossRangedGoal;
import cn.autoforged.joes_addons_for_abmc.entity.BeeBossTargetGoal;
import cn.autoforged.joes_addons_for_abmc.item.ModDataComponents;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import cn.autoforged.joes_addons_for_abmc.item.StaffItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * BeeBoss 的状态与默认配置。
 *
 * <p><b>标志为什么用 NeoForgeData 而不是实体同步数据：</b>1.20.5 起 {@code SynchedEntityData.Builder}
 * 在<b>构造时</b>就按 {@code ClassTreeIdRegistry.getCount(实体类)} 定长分配数据项数组，而 {@code defineId}
 * 按契约必须在该实体<b>自己的静态初始化</b>中执行。给原版 {@code Bee} 追加数据项时这两条都无法满足：
 * <ul>
 *   <li>若本类被懒初始化（首次 {@code Bee.defineSynchedData} 才触达），id 会在数组定长之后才分配 → 越界；
 *       且 {@code Builder#define} 的边界检查写成 {@code i > length} 而非 {@code >=}，连它本该给出的
 *       "Data value id is too big" 友好报错都被跳过，最终表现为 {@code index 19 out of bounds for length 19}。</li>
 *   <li>照搬 {@link cn.autoforged.joes_addons_for_abmc.entity.PlayerMorphSync#init()} 的「提前触发类加载」
 *       也不可靠：那个做法成立是因为 {@code Player} 的数据链上只有 {@code Entity}/{@code LivingEntity}
 *       （启动早期必定已初始化）。{@code Bee} 的链还多出 {@code Mob}、{@code AgeableMob} 两个带数据项的层级，
 *       它们未必已初始化——那样我们的 id 会与后初始化的 {@code Mob} 撞号，变成
 *       {@code Duplicate id value for 15!}。</li>
 * </ul>
 * 用持久化数据（随 NBT 存读、服务端权威）即可完全绕开 id 分配时序。
 * <p>客户端不需要这个标志：主手物品本身随装备同步下发，渲染层直接按「主手是否为蜂巢权杖」判断。
 *
 * <p>本类同时负责 BeeBoss 的默认配置：生命上限 {@link #MAX_HEALTH} 点 + {@link #BOSS_SCALE} 倍体型
 * + {@link #FOLLOW_RANGE} 格索敌距离 + 主手蜂巢权杖（内装 {@link #STAFF_BEES_MIN}~{@link #STAFF_BEES_MAX} 只蜜蜂）
 * + 索敌 AI {@link BeeBossTargetGoal} + 远程行为 {@link BeeBossRangedGoal}（保持距离 + 放蜂群/回收/休息循环）
 * + 两套攻击规格（见 {@link #isRangedMode}）：持杖时只用权杖放蜂群，不持杖时才近战
 * {@link #MELEE_ATTACK_DAMAGE} 点并附加 30 秒 / 困难 54 秒中毒。
 */
public final class BeeBossData {

    /** BeeBoss 标记的持久化键：存于实体的 NeoForgeData（自动随 NBT 存读）。 */
    public static final String BOSS_TAG = "jafa_beeboss";

    /** 「已切换为近战模式」的持久化键：蜂群耗尽、且休息时权杖里也没有蜜蜂后置位（单向状态）。
     *  置位后 {@link #isRangedMode} 恒为 false，Boss 转入原版近战流程。 */
    public static final String MELEE_MODE_TAG = "jafa_beeboss_melee";

    /** 「蛰针脱落时刻」的持久化键（值为 {@code level.getGameTime()}）。
     *  用游戏刻而不是 {@code tickCount}：读档后 tickCount 会归零，游戏刻不会。 */
    public static final String STINGER_LOST_TAG = "jafa_beeboss_stinger_lost";

    /** 蛰针再生时长（刻）：10 秒。
     *  <p>原版蜜蜂毒刺脱落即不再生（并会在约 1 分钟内随机自毙）；Boss 保留「脱落 → 10 秒后再生」
     *  是为了让它能持续近战，而不是蛰一口就废掉。自毙则由 ModMain 的伤害拦截屏蔽。 */
    public static final int STINGER_REGROW_TICKS = 10 * 20;

    /** 「蛰针再生正在做内部 NBT 往返」的临时标记（只在该往返期间存在）。
     *  用于让 {@code readAdditionalSaveData} 的注入跳过默认配置的重放——那次往返只是为了让
     *  {@code HasStung} 复位，不该顺带重挂属性/重发权杖。 */
    public static final String REGROW_IN_PROGRESS_TAG = "jafa_beeboss_regrowing";

    /** 蛰刺附加中毒的等级（amplifier）：1 = 中毒 II，固定不随难度变化（难度只影响时长）。 */
    public static final int STING_POISON_AMPLIFIER = 1;

    /** BeeBoss 默认权杖内预装的蜜蜂数量下限（含端点）。 */
    public static final int STAFF_BEES_MIN = 20;
    /** BeeBoss 默认权杖内预装的蜜蜂数量上限（含端点）。 */
    public static final int STAFF_BEES_MAX = 30;

    /** BeeBoss 体型放大倍数。 */
    public static final double BOSS_SCALE = 5.0;

    /** BeeBoss 生命上限（点）：原版蜜蜂 10 点。 */
    public static final double MAX_HEALTH = 50.0;

    /** BeeBoss 索敌距离（格）：FOLLOW_RANGE 属性与 {@link BeeBossTargetGoal#DETECT_RANGE} 一致。 */
    public static final double FOLLOW_RANGE = 200.0;

    /** BeeBoss 本体近战（蛰刺）伤害：10 点，覆盖原版蜜蜂的 2 点。
     *  原版 {@code Bee.doHurtTarget} 会把该属性值强转 int 后再结算，故 10.0 → 10 点。 */
    public static final double MELEE_ATTACK_DAMAGE = 10.0;

    /** BeeBoss 蛰刺附加的中毒时长（刻）：默认 30 秒。 */
    public static final int STING_POISON_TICKS = 30 * 20;
    /** 困难模式下的蛰刺中毒时长（刻）：54 秒（与原版 10 秒 → 18 秒同为 1.8 倍）。 */
    public static final int STING_POISON_TICKS_HARD = 54 * 20;

    /** 当前难度下 BeeBoss 蛰刺的中毒时长（刻）：困难 54 秒，其余 30 秒。 */
    public static int stingPoisonTicks(Difficulty difficulty) {
        return difficulty == Difficulty.HARD ? STING_POISON_TICKS_HARD : STING_POISON_TICKS;
    }

    /** BeeBoss 是否处于「远程模式」：<b>未切换为近战模式</b>且主手持蜂巢权杖时成立。
     *  <ul>
     *    <li><b>远程模式</b>——不使用本体近战，并尽量保持在目标
     *        {@link BeeBossRangedGoal#STANDOFF_DISTANCE} 格以外的站位（由 {@link BeeBossRangedGoal} 负责位移
     *        与放蜂群/回收/休息循环）；攻击手段只有「把权杖里所有蜜蜂放出去」；</li>
     *    <li><b>近战模式</b>——主手没有蜂巢权杖时（权杖被拿走、被替换），<b>或者</b>蜂群耗尽后
     *        {@link #setMeleeMode} 被置位时，改用本体近战：{@link #MELEE_ATTACK_DAMAGE} 点伤害
     *        + {@link #STING_POISON_AMPLIFIER} 级（中毒 II）中毒。</li>
     *  </ul>
     *  <p>注意：判定「主手持蜂巢权杖」看的是持有这一事实，与权杖当前有几只蜜蜂无关——蜂群放出去、
     *  正在回收时权杖本来就是空的。真正「蜂群没了」的判断发生在回收结束（休息开始）那一刻，
     *  见 {@link #isMeleeMode}。 */
    public static boolean isRangedMode(Bee bee) {
        return !isMeleeMode(bee) && BeehiveStaffHelper.isBeeStaff(bee.getMainHandItem());
    }

    /** 是否已切换为近战模式（蜂群耗尽后的单向状态）。 */
    public static boolean isMeleeMode(Bee bee) {
        return bee.getPersistentData().getBoolean(MELEE_MODE_TAG);
    }

    /** 切换为近战模式：此后 {@link #isRangedMode} 恒为 false，Boss 走原版近战流程。 */
    public static void setMeleeMode(Bee bee, boolean melee) {
        bee.getPersistentData().putBoolean(MELEE_MODE_TAG, melee);
    }

    private BeeBossData() {
    }

    public static boolean isBeeBoss(Bee bee) {
        return bee.getPersistentData().getBoolean(BOSS_TAG);
    }

    public static void setBeeBoss(Bee bee, boolean boss) {
        bee.getPersistentData().putBoolean(BOSS_TAG, boss);
    }

    /** BeeBoss 蜜蜂生成时的默认配置：生命上限 {@link #MAX_HEALTH} 点、体型放大到 {@link #BOSS_SCALE} 倍、
     *  索敌距离 {@link #FOLLOW_RANGE} 格、本体近战伤害 {@link #MELEE_ATTACK_DAMAGE} 点、主手默认持有蜂巢权杖
     *  （权杖内预装 {@link #STAFF_BEES_MIN}~{@link #STAFF_BEES_MAX} 只蜜蜂），
     *  并挂载索敌 AI {@link BeeBossTargetGoal} 与远程行为 {@link BeeBossRangedGoal}。
     *  由 BeeBossMixin 读取 NBT 后与 /jafa beeboss 命令共同调用。
     *  <p><b>属性与 AI 目标每次都重申</b>（幂等，也能纠正被 {@code /data} 改坏的属性；目标不随存档保存，
     *  实体重建后必须重挂）；<b>而「补满生命值」与「发放默认权杖」只发生在新建时</b>。
     *  @param freshSpawn 是否属于「新建」（命令召唤，或 NBT 里没有 {@code Health} 的 {@code /summon}）。
     *                    读档必须传 false，否则每次区块载入都会把受伤的 Boss 治满，
     *                    并且会把玩家用 {@code /item replace ... with air} 清掉的权杖凭空补回来。 */
    public static void applyBeeBossDefaults(Bee bee, boolean freshSpawn) {
        if (!isBeeBoss(bee)) {
            return;
        }
        // 生命上限：50 点（原版蜜蜂 10 点）
        var maxHealth = bee.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && Math.abs(maxHealth.getBaseValue() - MAX_HEALTH) > 1.0E-5) {
            maxHealth.setBaseValue(MAX_HEALTH);
        }
        if (freshSpawn) {
            bee.setHealth(bee.getMaxHealth());
        }
        // 体型：SCALE 属性 base = 5（同时影响碰撞箱与渲染大小）
        var scale = bee.getAttribute(Attributes.SCALE);
        if (scale != null && Math.abs(scale.getBaseValue() - BOSS_SCALE) > 1.0E-5) {
            scale.setBaseValue(BOSS_SCALE);
        }
        // 索敌距离：FOLLOW_RANGE base = 200（与 BeeBossTargetGoal 的检索半径保持一致）
        var followRange = bee.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null && Math.abs(followRange.getBaseValue() - FOLLOW_RANGE) > 1.0E-5) {
            followRange.setBaseValue(FOLLOW_RANGE);
        }
        // 本体近战伤害：10 点（远高于原版蜜蜂的 2 点；只在近战模式——主手没有蜂巢权杖——时才会用上）
        var attackDamage = bee.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null && Math.abs(attackDamage.getBaseValue() - MELEE_ATTACK_DAMAGE) > 1.0E-5) {
            attackDamage.setBaseValue(MELEE_ATTACK_DAMAGE);
        }
        // 默认蜂巢权杖（内装随机 20~30 只蜜蜂）：<b>只在新建时发放</b>。
        // 读档时主手为空必须保持为空——否则玩家用 /item replace ... with air 拿掉的权杖会「凭空长回来」；
        // 蛰针再生的内部 NBT 往返（BeeBossMixin#regrowStinger）也会走到这里，同样必须跳过。
        if (freshSpawn && bee.getMainHandItem().isEmpty()) {
            bee.setItemInHand(InteractionHand.MAIN_HAND, createDefaultStaff(bee));
        }
        // 索敌 AI（200 格索敌 + 蜘蛛优先）：挂载是幂等的，重复调用不会重复添加
        BeeBossTargetGoal.ensureInstalled(bee);
        // 远程行为（保持距离 + 放蜂群 / 回收 / 休息循环，只在远程模式运行，同样幂等挂载）
        BeeBossRangedGoal.ensureInstalled(bee);
    }

    /** 生成 BeeBoss 的默认蜂巢权杖：{@code bee_nest} 形态、方块耐久 0、内部预装 20~30 只蜜蜂。 */
    public static ItemStack createDefaultStaff(Bee owner) {
        ItemStack staff = new ItemStack(ModItems.STAFF.get());
        staff.set(ModDataComponents.BLOCKTYPE.get(), "bee_nest");
        StaffItem.setBlockDamage(staff, 0);
        fillStaffWithBees(staff, owner);
        return staff;
    }

    /** 给蜂巢权杖预装随机数量（{@link #STAFF_BEES_MIN}~{@link #STAFF_BEES_MAX}，含端点）的蜜蜂存档数据。
     *  <p>数据由临时蜜蜂实体经 {@link BeehiveBlockEntity.Occupant#of} 序列化取得——与玩家用权杖实际吸收产生的
     *  数据完全同构，因此这些蜜蜂能走既有释放路径（左键群放、左 Alt 逐只、投掷蜂巢）正常放出。
     *  <p>每只都用独立临时实体，使其各自带独立 UUID 与默认状态；临时实体不加入世界，序列化后即被回收。
     *  <p>注意：这些蜜蜂的 {@code BeeBoss} 标记为 false，放出的是普通蜜蜂而非 BeeBoss。 */
    private static void fillStaffWithBees(ItemStack staff, Bee owner) {
        var random = owner.getRandom();
        int count = STAFF_BEES_MIN + random.nextInt(STAFF_BEES_MAX - STAFF_BEES_MIN + 1);
        List<BeehiveBlockEntity.Occupant> bees = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Bee stored = new Bee(EntityType.BEE, owner.level());
            stored.setPos(owner.getX(), owner.getY(), owner.getZ());
            bees.add(BeehiveBlockEntity.Occupant.of(stored));
        }
        staff.set(DataComponents.BEES, List.copyOf(bees));
    }
}