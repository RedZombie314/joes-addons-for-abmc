package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.item.GlisteringMelonKnifeItem;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * "幸运核心附体玩家攻击事件"列表。
 *
 * <h2>三大类：远程 / 近战 / 其它</h2>
 * 攻击方式分三类（{@link #CATEGORIES}），抽签<b>先等概率抽类别，再在类别内等概率抽具体手段</b>：
 * <ul>
 *   <li><b>远程攻击</b>（{@link #RANGED}）：{@link ArrowEvent} 拉普通弓<b>连射 3~5 箭</b>、
 *       {@link PrismarineBowEvent} 拉海晶弓、{@link TridentEvent} <b>连扔 3~5 只</b>三叉戟、
 *       {@link KnifeThrowEvent} 投掷闪烁西瓜刀、{@link CreeperEvent} 苦力怕弹、
 *       {@link ExplosiveCatEvent} 爆裂猫、{@link ExplosiveZombieEvent} 爆裂僵尸、
 *       {@link ExplosiveSlimeEvent} 爆裂史莱姆、{@link ExplosiveSkeletonEvent} 爆裂骷髅
 *       （<b>这五张"爆炸性实体"签的权重是其它攻击的 1/3</b>，见 {@link #EXPLOSIVE_ENTITY_WEIGHT}）、
 *       {@link ExplosiveFireworkEvent} 爆炸性烟花火箭、{@link GuardianBeamEvent} 守卫者光波、
 *       {@link SkeletonKnightEvent} 召唤骷髅马骑士、{@link AnvilRainEvent} 铁砧雨、
 *       {@link GhastFireballEvent} <b>手持烈焰手杖</b>放恶魂火球、{@link WitherSummonEvent} 召唤己方凋灵，
 *       按权重抽取（默认等概率）；</li>
 *   <li><b>近战攻击</b>（{@link #MELEE}）：{@link MeleeEvent} 手持闪烁西瓜刀贴身劈砍、
 *       {@link TridentMeleeEvent} 手持<b>穿刺 V 三叉戟</b>捅人、
 *       {@link BobSummonEvent} 召唤 2~3 只 Bob 协同作战、{@link HookPullEvent} <b>铁抓钩</b>
 *       把玩家从远处拽回来（权重 {@link #HOOK_WEIGHT} = 其它近战的 1/3，且只在
 *       "目标是玩家 + AI 处于近身阶段 + 玩家在 5 格之外"时才进候选）；
 *       <b>这一类里的每个事件都必须自己声明 {@code canRun} 的距离条件</b> ——
 *       否则它会变成"抽到就能用"，隔着十几格也会一直兑现（Bob 召唤一开始就踩了这个坑）；</li>
 *   <li><b>其它攻击</b>（{@link #OTHER}）：{@link LavaCageEvent} <b>岩浆牢笼</b>
 *       （仅针对玩家，权重 {@link #LAVA_CAGE_WEIGHT} = 一般攻击的 1/4）。
 *       因为抽签本身只有"近战 / 远程"两步，这一类会并进远程那一步一起抽
 *       （见 {@link #RANGED_WITH_OTHER}），否则它永远只会是"两者都抽不到"时的兜底、等于摆设。</li>
 * </ul>
 *
 * <h2>抽签规则：贴身必近战，远了必远程</h2>
 * <ul>
 *   <li>目标在 {@link #MELEE_RANGE} 格内 → <b>必定</b>从 {@link #MELEE} 里抽（按权重抽一张），
 *       除非该敌人的特攻行为要求"保持距离"（监守者）；</li>
 *   <li>目标在 3 格之外 → 只在 {@link #RANGED} 里抽（远程事件本就不限距离）；
 *       <b>唯一的例外是铁抓钩</b>：目标是玩家、AI 在近身阶段、玩家在 5 格之外时，
 *       也会从近战池里抽（那张签是唯一"够得着"的近战手段）；</li>
 *   <li><b>特攻行为</b>（{@link #MATCHUPS}）会改写上面两条：指定"优先用某事件"
 *       （如对凋灵优先闪烁西瓜刀），或"保持距离、只打远程"（监守者）；</li>
 *   <li>空类别（如目前的 {@link #OTHER}）只在两者都抽不出东西时兜底，不占概率；</li>
 *   <li>冷却（{@link PossessedAttackEvent#cooldownTicks()}）不在筛选范围内：抽到的事件若还在
 *       冷却里，这个窗口就只是继续走位，不会改抽别的、也不会因为冷却去重抽。
 *       <b>唯一例外是铁抓钩</b>：它自己把冷却写进了 {@code canRun}（远处近战池里只有它一张签，
 *       抽到却出不了手的话空壳会举着钩子干等）。</li>
 * </ul>
 *
 * <h2>动画约定（每一签都得有动作）</h2>
 * <ul>
 *   <li><b>弓箭类</b>（{@link ArrowEvent}、{@link PrismarineBowEvent}）：{@code startUsingItem}
 *       → {@code UseAnim.BOW} → {@code BOW_AND_ARROW}（主手拉弓、副手搭箭）；</li>
 *   <li><b>投掷类</b>（{@link TridentEvent}、{@link KnifeThrowEvent}）：{@code startUsingItem}
 *       → {@code UseAnim.SPEAR} → {@code THROW_SPEAR}（举起武器蓄力，1 秒）；</li>
 *   <li><b>近战类</b>（{@link MeleeEvent}、{@link TridentMeleeEvent}）：{@code swing(MAIN_HAND)}
 *       播原版挥臂；</li>
 *   <li><b>召唤/施法类</b>（苦力怕弹、爆裂猫、爆裂僵尸、爆裂史莱姆、爆裂骷髅、爆炸性烟花火箭、
 *       守卫者光波、骷髅马骑士、Bob、铁砧雨、岩浆牢笼、恶魂火球、凋灵）：
 *       同样 {@code swing(MAIN_HAND)} —— 它们原本什么都不演，东西凭空冒出来，很突兀。</li>
 * </ul>
 * 姿态由 {@code PlayerShellRenderer} 按"手里拿什么 + 是否正在使用"判定（照搬 {@code PlayerRenderer}）。
 *
 * <h2>召唤物之间不互相索敌</h2>
 * Bob 与五种爆炸性召唤物（苦力怕/猫/僵尸/史莱姆/骷髅）生成时都会登记为
 * {@link OrbPossessionSummons 附体召唤物}，附体体系的任何索敌都会跳过带标记的实体；
 * Bob 的共享索敌又直接抄空壳的目标（{@link OrbSummonTargetGoal}），因此天然不会互相打。
 * 将来再加召唤物，只要在生成处 mark 一下即可。
 *
 * <h2>流程：先抽签，再由抽到的事件自己决定怎么打</h2>
 * 被附体的玩家空壳（{@link PlayerShellEntity#isOrbAttached()}）在<b>有索敌目标</b>时，
 * 每隔 1~2 秒（随机）调用一次 {@link #drawUsable} <b>抽签</b>，抽到谁，这段时间就按谁的方式来：
 * <ul>
 *   <li>远程事件：走位照旧（骷髅式环绕/拉开距离），冷却好了就朝目标开火；</li>
 *   <li>{@link MeleeEvent}：走位照旧，但每刻检查目标是否已在近战距离内 ——
 *       在范围内就<b>停下走位</b>（{@link PossessedAttackEvent#shouldPauseMovement}）原地出刀，
 *       不在范围内就继续走位等机会。</li>
 * </ul>
 *
 * <h2>起手（蓄力）与武器外观</h2>
 * 抽到事件时，空壳会先把两手清空，再由事件自己的
 * {@link PossessedAttackEvent#beginWindUp} 拿出对应武器并进入蓄力姿态
 * （{@code startUsingItem} 会驱动拉弓 / 举三叉戟的动画，见 {@link PlayerShellRenderer}）；
 * 经过 {@link PossessedAttackEvent#windUpTicks()} 刻后由 {@link #run}（事件未起手时立即）
 * 真正出手，随后 {@link PossessedAttackEvent#endWindUp} 收尾。
 *
 * <h2>够不着的签会被立刻重抽</h2>
 * 抽到近战而目标还在 3 格之外时，这签兑现不了，不能让它白占一个窗口 ——
 * 于是当场重抽，重抽又是近战就继续重抽，直到抽到能马上用的手段。
 * 反过来，抽到近战<b>且</b>目标已在范围内就定下来：接下来这段时间它停下走位出刀，
 * 如果目标中途跑出 3 格，它就只是继续走位等下一次抽签。
 * <p>
 * 冷却（{@link PossessedAttackEvent#cooldownTicks()}）是另一道闸：抽到的事件兑现一次后开始计时，
 * 冷却没好的窗口里空壳就只是继续走位。
 */
public final class OrbPossessedAttackEvents {

    /** 近战距离上限（格）：目标进到这个距离内才够得着。 */
    public static final double MELEE_RANGE = 3.0;

    // ====================== 事件实例与分类 ======================
    //
    // 事件实例都先起个名字再进列表：特攻行为（MATCHUPS）要引用"同一个实例"，
    // 否则冷却表按实例记账，会变成同一个事件有两份互不相干的冷却。

    private static final PossessedAttackEvent RANGED_ARROW = new ArrowEvent();
    private static final PossessedAttackEvent RANGED_PRISMARINE_BOW = new PrismarineBowEvent();
    private static final PossessedAttackEvent RANGED_TRIDENT = new TridentEvent();
    private static final PossessedAttackEvent RANGED_KNIFE_THROW = new KnifeThrowEvent();
    private static final PossessedAttackEvent RANGED_CREEPER = new CreeperEvent();
    private static final PossessedAttackEvent RANGED_CAT = new ExplosiveCatEvent();
    private static final PossessedAttackEvent RANGED_ZOMBIE = new ExplosiveZombieEvent();
    private static final PossessedAttackEvent RANGED_SLIME = new ExplosiveSlimeEvent();
    private static final PossessedAttackEvent RANGED_SKELETON = new ExplosiveSkeletonEvent();
    private static final PossessedAttackEvent RANGED_FIREWORK = new ExplosiveFireworkEvent();
    private static final PossessedAttackEvent RANGED_GUARDIAN_BEAM = new GuardianBeamEvent();
    private static final PossessedAttackEvent RANGED_KNIGHT = new SkeletonKnightEvent();
    private static final PossessedAttackEvent RANGED_ANVIL_RAIN = new AnvilRainEvent();
    private static final PossessedAttackEvent RANGED_GHAST_FIREBALL = new GhastFireballEvent();
    private static final PossessedAttackEvent RANGED_WITHER = new WitherSummonEvent();
    /** 低概率远程签：给自己穿上 wings 并从此获得飞行能力（权重 0.1，见 {@link FlightEvent}）。 */
    private static final PossessedAttackEvent RANGED_FLIGHT = new FlightEvent();

    /**
     * <b>其它攻击</b>签：岩浆牢笼（仅针对玩家，权重 {@link #LAVA_CAGE_WEIGHT} = 一般攻击的 1/4）。
     * <p>
     * 它是 {@link #OTHER} 这一类里目前唯一的一张签；这一类的抽取方式见 {@link #RANGED_WITH_OTHER}。
     */
    private static final PossessedAttackEvent OTHER_LAVA_CAGE = new LavaCageEvent();

    private static final PossessedAttackEvent MELEE_KNIFE = new MeleeEvent();
    private static final PossessedAttackEvent MELEE_TRIDENT = new TridentMeleeEvent();
    private static final PossessedAttackEvent MELEE_BOB = new BobSummonEvent();
    /** 低概率近战签：鞘翅爬升 → 俯冲 → 重锤砸（权重 0.1；会飞之后永不抽中）。 */
    private static final PossessedAttackEvent MELEE_MACE = new MaceEvent();
    /**
     * 近战签（<b>铁抓钩</b>）：目标是玩家、AI 处于近身阶段、且玩家在 5 格之外时，
     * 甩出铁链把人拽回近战距离（见 {@link HookPullEvent}，权重 {@link #HOOK_WEIGHT} = 其它近战的 1/3）。
     */
    private static final PossessedAttackEvent MELEE_HOOK = new HookPullEvent();

    /** 远程攻击：按权重抽取（装了暮色森林还会追加暮色的远程手段）。 */
    private static final List<PossessedAttackEvent> RANGED = buildRanged();

    private static List<PossessedAttackEvent> buildRanged() {
        List<PossessedAttackEvent> list = new ArrayList<>(List.of(
            RANGED_ARROW, RANGED_PRISMARINE_BOW, RANGED_TRIDENT,
            RANGED_KNIFE_THROW, RANGED_CREEPER,
            RANGED_CAT, RANGED_ZOMBIE, RANGED_SLIME, RANGED_SKELETON,
            RANGED_FIREWORK, RANGED_GUARDIAN_BEAM, RANGED_KNIGHT,
            RANGED_ANVIL_RAIN, RANGED_GHAST_FIREBALL, RANGED_WITHER,
            RANGED_FLIGHT));
        // 软依赖一律先问 OptionalMods（它不引用任何可选 mod 的类型）：
        // 直接问 TwilightForestCompat.isLoaded()/AetherCompat.isLoaded() 会把联动类加载起来，
        // 而那个 mod 一旦被卸载，加载联动类本身就会抛 NoClassDefFoundError（实测崩过）
        if (OptionalMods.isTwilightForestLoaded()) {
            TwilightForestCompat.addRangedEvents(list);
        }
        if (OptionalMods.isAetherLoaded()) {
            AetherCompat.addRangedEvents(list);
        }
        return List.copyOf(list);
    }

    /** 近战攻击：按权重抽取（装了暮色森林还会追加暮色的近战手段）。 */
    private static final List<PossessedAttackEvent> MELEE = buildMelee();

    private static List<PossessedAttackEvent> buildMelee() {
        List<PossessedAttackEvent> list = new ArrayList<>(List.of(
            MELEE_KNIFE, MELEE_TRIDENT, MELEE_BOB, MELEE_MACE, MELEE_HOOK));
        if (OptionalMods.isTwilightForestLoaded()) {
            TwilightForestCompat.addMeleeEvents(list);
        }
        if (OptionalMods.isAetherLoaded()) {
            AetherCompat.addMeleeEvents(list);
        }
        return List.copyOf(list);
    }

    /**
     * 其它攻击：目前只有一张签 —— {@link #OTHER_LAVA_CAGE 岩浆牢笼}（仅针对玩家）。
     * <p>
     * 抽签规则是"贴身必近战、否则远程"，这个类别本来只在两者都抽不出东西时兜底，
     * 于是"往这里放一张签"等于永远抽不到。用户这次的说明是把岩浆牢笼归在"其它攻击"、
     * 权重为一般攻击的 1/4 —— 所以这里改成：走远程那一步时，从
     * {@link #RANGED_WITH_OTHER}（= {@link #RANGED} + 本类别的签）里一起加权抽。
     * 这样它既保留在"其它攻击"这个分类里，又真的会按 1/4 权重被抽到。
     */
    private static final List<PossessedAttackEvent> OTHER = List.of(OTHER_LAVA_CAGE);

    /**
     * 远程抽签实际使用的池子：默认远程池 <b>+ 其它攻击</b>。
     * <p>
     * 只在"该敌人没有专属远程池"时使用（有专属池的末影人/潜影贝/旋风人/九头蛇另算，
     * 它们本来就不该拿到岩浆牢笼 —— 那几张签的对手也都不是玩家）。
     */
    private static final List<PossessedAttackEvent> RANGED_WITH_OTHER = buildRangedWithOther();

    private static List<PossessedAttackEvent> buildRangedWithOther() {
        List<PossessedAttackEvent> list = new ArrayList<>(RANGED);
        list.addAll(OTHER);
        return List.copyOf(list);
    }

    /** 三大攻击方式。 */
    private static final List<List<PossessedAttackEvent>> CATEGORIES = List.of(RANGED, MELEE, OTHER);

    /**
     * <b>特攻行为（matchup）</b>：发现对面属于某类特定敌人时，对抽签的偏好。
     * 以后要继续完善就往 {@link #MATCHUPS} 里加条目，判定与抽签逻辑都不用动。
     *
     * @param condition       触发条件（按目标实体判断，可以写类型、也可以写护甲值之类的属性）
     * @param rangedPool      远程抽签用的池子；null = 默认 {@link #RANGED}
     * @param meleePool       近战抽签用的池子；null = 默认 {@link #MELEE}
     * @param rangedBias      远程"偏好事件"；null = 无偏好
     * @param rangedBiasChance 抽远程时优先兑现 {@code rangedBias} 的概率（1 = 必定）
     * @param meleeBias       近战"偏好事件"；null = 无偏好
     * @param meleeBiasChance 抽近战时优先兑现 {@code meleeBias} 的概率（1 = 必定）
     * @param alwaysMelee     贴身时是否一定走近战（false = 该敌人想保持距离，贴身也主要打远程）
     */
    // 包内可见（而不是 private）：TwilightForestCompat 要往 MATCHUPS 里加条目
    record Matchup(Predicate<LivingEntity> condition,                           @Nullable List<PossessedAttackEvent> rangedPool,
                           @Nullable List<PossessedAttackEvent> meleePool,
                           @Nullable PossessedAttackEvent rangedBias, float rangedBiasChance,
                           @Nullable PossessedAttackEvent meleeBias, float meleeBiasChance,
                           boolean alwaysMelee) {
    }

    /** 末影人保留的远程手段：只留"召唤爆炸性实体 + 铁砧雨"，其余远程一律不用。 */
    private static final List<PossessedAttackEvent> ENDERMAN_RANGED = List.of(
        RANGED_CREEPER, RANGED_CAT, RANGED_ZOMBIE, RANGED_SLIME, RANGED_SKELETON,
        RANGED_GHAST_FIREBALL, RANGED_ANVIL_RAIN);

    /** 潜影贝保留的远程手段：<b>只留爆炸性实体</b>（不含铁砧雨、不含其它任何远程）。 */
    private static final List<PossessedAttackEvent> SHULKER_RANGED = List.of(
        RANGED_CREEPER, RANGED_CAT, RANGED_ZOMBIE, RANGED_SLIME, RANGED_SKELETON,
        RANGED_GHAST_FIREBALL);

    /**
     * 旋风人用的远程池：把<b>爆炸性实体与爆炸弹全部剔除</b>
     * （召唤苦力怕/爆裂猫/爆裂僵尸、恶魂火球 —— 旋风人会把飞行物弹回来，扔这些纯亏），
     * 其余远程照旧。
     */
    private static final List<PossessedAttackEvent> BREEZE_RANGED = List.of(
        RANGED_ARROW, RANGED_PRISMARINE_BOW, RANGED_TRIDENT, RANGED_KNIFE_THROW,
        RANGED_KNIGHT, RANGED_ANVIL_RAIN, RANGED_WITHER);

    /**
     * 暮色九头蛇用的远程池：默认远程池<b>减去"召唤己方凋灵"</b>，其余照旧。
     * <p>
     * 用户要求：<b>对九头蛇特攻时不召唤凋灵作为远程攻击</b>。
     * 九头蛇本身就是 boss，再往场里丢凋灵（同样是 boss）纯属给自己添乱 ——
     * 凋灵之首的爆炸和它半血那次护甲爆发都会糊到九头蛇身上，把"打头"这件事彻底搅浑，
     * 而且凋灵体型大、会飞，很容易顶掉九头蛇头的受击判定。所以这条特攻只保留普通远程手段：
     * 拉弓/海晶弓/三叉戟/西瓜刀、召唤爆炸性实体、骷髅马骑士、铁砧雨、恶魂火球，
     * 以及那张低概率的"穿上鞘翅获得飞行"（它不是召唤物，留着不影响本条要求）。
     * <p>
     * 用白名单逐项列出（而不是从 {@link #RANGED} 里过滤）是为了避开类初始化顺序：
     * 这个字段要排在 {@link #RANGED} 之前，那时 {@code RANGED} 还没构造出来。
     * <p>
     * 只由 {@link TwilightForestCompat#addMatchups} 引用；没装暮色时它就是个没人碰的常量。
     */
    static final List<PossessedAttackEvent> HYDRA_RANGED = List.of(
        RANGED_ARROW, RANGED_PRISMARINE_BOW, RANGED_TRIDENT, RANGED_KNIFE_THROW,
        RANGED_CREEPER, RANGED_CAT, RANGED_ZOMBIE, RANGED_KNIGHT,
        RANGED_ANVIL_RAIN, RANGED_GHAST_FIREBALL, RANGED_FLIGHT);

    /**
     * 监守者用的远程池：全部远程手段 + "喊 Bob"。
     * <p>
     * Bob 的 {@code canRun} 本来就要求目标在 3 格内，所以把它塞进远程池正好表达
     * "贴身时仍有几率叫 Bob、其余情况一律远程" —— 不需要额外开关，权重就是 1/(11+1)。
     */
    private static final List<PossessedAttackEvent> WARDEN_RANGED = buildWardenRanged();

    private static List<PossessedAttackEvent> buildWardenRanged() {
        List<PossessedAttackEvent> list = new ArrayList<>(RANGED);
        list.add(MELEE_BOB);
        return List.copyOf(list);
    }

    /** 全部特攻行为。多个条目命中时取<b>第一个</b>（所以越特殊的条件越要往前放）。 */
    private static final List<Matchup> MATCHUPS = buildMatchups();

    private static List<Matchup> buildMatchups() {
        List<Matchup> list = new ArrayList<>(List.of(
            // 凋灵：远程/近战都"较大概率"用闪烁西瓜刀 —— 凋灵是亡灵，
            // 而西瓜刀对亡灵有 ×20 的乘区（见 ModMain#applyKnifeDamage），捅它最划算。
            // 0.75 = 较大概率但非必定，剩下 25% 照常从池子里等概率抽。
            new Matchup(e -> e.getType() == EntityType.WITHER,
                null, null, RANGED_KNIFE_THROW, 0.75F, MELEE_KNIFE, 0.75F, true),
            // 亡灵生物：同上，较高概率改用闪烁西瓜刀（远程/近战都是这 0.75）。
            // 判据用 #minecraft:sensitive_to_smite —— 与西瓜刀那个 ×20 乘区<b>完全同一集合</b>
            // （见 ModMain#applyKnifeDamage），所以"该用刀的目标"和"刀特别疼的目标"永远一致。
            // 注意它必须排在凋灵那条之后：凋灵同样属于亡灵。
            new Matchup(e -> e.getType().is(net.minecraft.tags.EntityTypeTags.SENSITIVE_TO_SMITE),
                null, null, RANGED_KNIFE_THROW, 0.75F, MELEE_KNIFE, 0.75F, true),
            // 监守者：尽量避开近战 —— 只在远程池里抽，但池子里留着"喊 Bob"这张近战签，
            // 所以贴身时仍有几率叫僵尸，其余情况一律远程。
            new Matchup(e -> e.getType() == EntityType.WARDEN,
                WARDEN_RANGED, null, null, 0.0F, null, 0.0F, false),
            // 末影人：尽量避免远程而采用近战（贴身必近战），远程只保留"召唤爆炸性实体 + 铁砧雨"。
            new Matchup(e -> e.getType() == EntityType.ENDERMAN,
                ENDERMAN_RANGED, null, null, 0.0F, null, 0.0F, true),
            // 潜影贝：尽量走近战（贴身必近战）；真要用远程也只挑爆炸性实体。
            new Matchup(e -> e.getType() == EntityType.SHULKER,
                SHULKER_RANGED, null, null, 0.0F, null, 0.0F, true),
            // 旋风人：它会弹开飞行物，所以尽量贴身近战（贴身必近战）；
            // 远程池里把爆炸性实体/爆炸弹全剃掉（扔过去只会被弹回来）。
            new Matchup(e -> e.getType() == EntityType.BREEZE,
                BREEZE_RANGED, null, null, 0.0F, null, 0.0F, true),
            // 苦力怕 / 铁傀儡 / 卫道士 / 女巫：贴身的代价太高（爆炸、重击、以及女巫的贴脸药水），
            // 一律保持距离打远程。
            new Matchup(e -> e.getType() == EntityType.CREEPER
                    || e.getType() == EntityType.IRON_GOLEM
                    || e.getType() == EntityType.WITCH
                    || e.getType() == EntityType.VINDICATOR,
                null, null, null, 0.0F, null, 0.0F, false)));
        // 暮色森林联动：装了这个 mod 才有那些武器/特攻（未安装时整个兼容类都不会被真正用到）
        if (OptionalMods.isTwilightForestLoaded()) {
            TwilightForestCompat.addMatchups(list);
        }
        return List.copyOf(list);
    };

    /**
     * 最高优先级的"特攻"：目标是<b>手持重锤</b>且<b>身上没有缓降</b>时，先给它来一瓶喷溅型缓降药水。
     * <p>
     * 它<b>不属于</b>远程池也不属于近战池（不参与等概率抽签），只有上面那一种情况才会被调用，
     * 而且是所有判断里最先看的：重锤的伤害取决于下落高度，把它的下落速度砍掉就等于拆了它的大招。
     * <p>
     * 冷却好了才丢；冷却没好就什么都不做、照常按池子抽（不然这一个事件会一直占着抽签窗口，
     * 让空壳在"目标拿着重锤但药水还在冷却"的时候完全不还手）。
     * <p>
     * 它还把 {@link PossessedAttackEvent#highestPriority()} 标成 true：条件成立时<b>会当场插队</b>，
     * 把空壳手里那张没出手的签（哪怕正在拉弓）作废，立刻改丢药水。
     */
    private static final PossessedAttackEvent SLOW_FALLING_POTION = new SlowFallingPotionEvent();

    /**
     * 最高优先级特攻（之一）：自己中了<b>凋灵</b>或<b>中毒</b>时，往脚下丢一桶奶解掉自己。
     * <p>
     * 和缓降药水同级、同样标 {@link PossessedAttackEvent#highestPriority()}：条件成立就插队，
     * 免得带着凋灵硬打。奶桶落地按 ±4 格范围清空状态效果，所以会连自己身上的增益一起清掉
     * （原版奶的脾气）—— 但空壳那个"保护"效果会自动补回来（见
     * {@code PlayerShellEntity#ensureCoreBackedProtection()}）。
     * <p>
     * 丢法：朝正下方、速度很小，落点就在脚边；砸的是方块而不是实体，
     * 因此不受投射物"离开投掷者之前不打实体"那条起手保护限制。
     */
    private static final PossessedAttackEvent MILK_BUCKET = new MilkBucketEvent();

    /**
     * 最高优先级特攻（之三）：<b>10 格内出现变形药水</b>（喷溅/滞留药水瓶，或它留下的状态效果云）时，
     * 朝天上抛一瓶<b>变形解药</b>（3 格/秒 = 0.15 格/刻），冷却 1 秒。
     * <p>
     * 起手：平时 0.5 秒（看得清"掏药瓶"），但若空壳<b>刚被解除变形</b>
     * （{@code PlayerShellEntity#isJustUntransmuted()}）则零帧出手 —— 那正是它刚变回来、最该立刻反应的时刻
     * （见 {@link PossessedAttackEvent#windUpTicks(LivingEntity)}）。
     * <p>
     * "同时移除其对变形药水的免疫"不在出手时做，而在<b>解除变形那一刻</b>：
     * 见 {@code ModMain#respawnTransmutedEntity} → {@code PlayerShellEntity#markUntransmuted()}。
     */
    private static final PossessedAttackEvent TRANSMUTATION_ANTIDOTE = new TransmutationAntidoteEvent();

    private static final class TransmutationAntidoteEvent implements PossessedAttackEvent {

        /** 出手间隔（刻）：8 刻。 */
        private static final int COOLDOWN_TICKS = 8;

        /** 平时起手刻数（0.5 秒）；"首次发现变形药水 / 刚被解除变形"时改用 0 = 零帧出手。 */
        private static final int WIND_UP_TICKS = 10;

        /** 朝天上抛的初速（格/刻）：0.65 格/刻 = 13 格/秒。 */
        private static final float TOSS_SPEED = 0.65F;

        /**
         * 抛出之后的收招刻数：这段时间空壳"暂时失去走位 AI"（站定，只转头盯人）。
         * <p>
         * 与普通出手的收招僵直（只对玩家目标）不同，这里是<b>不看目标的硬僵直</b>：
         * 变形解药是自救动作，丢完药瓶总要停一下，所以用
         * {@code PlayerShellEntity#lockMovementFor(int)} 直接锁。
         */
        private static final int RECOVER_TICKS = 10;

        /** 检测半径（格）。 */
        private static final double DETECT_RADIUS = 10.0;

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        @Override
        public int windUpTicks(LivingEntity attacker) {
            // 零帧起手的两种情形（都在空壳那边判定，见 PlayerShellEntity#isAntidoteInstant）：
            //   1) 这一轮"首次发现"身边有变形药水（上升沿）；
            //   2) 空壳刚被解除变形。
            return attacker instanceof PlayerShellEntity shell && shell.isAntidoteInstant()
                ? 0 : WIND_UP_TICKS;
        }

        @Override
        public boolean highestPriority() {
            return true;
        }

        @Override
        public boolean canRun(LivingEntity attacker, LivingEntity target) {
            return hasNearbyTransmutationPotion(attacker);
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPLASH_POTION));
            attacker.swing(InteractionHand.MAIN_HAND);
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            ItemStack antidote = new ItemStack(Items.SPLASH_POTION);
            antidote.set(DataComponents.POTION_CONTENTS, new PotionContents(
                cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSMUTATION_ANTIDOTE));
            Vec3 from = attacker.getEyePosition();
            ThrownPotion potion = new ThrownPotion(level, attacker);
            potion.setItem(antidote);
            potion.setPos(from.x, from.y - 0.1, from.z);
            potion.shoot(0.0, 1.0, 0.0, TOSS_SPEED, 0.0F);   // 朝正上方抛
            level.addFreshEntity(potion);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.SPLASH_POTION_THROW,
                SoundSource.HOSTILE, 1.0F, 1.2F);
            // 抛完短暂失去走位 AI（用户指定）：站定收招一下，然后该走走、该打打
            if (attacker instanceof PlayerShellEntity shell) {
                shell.lockMovementFor(RECOVER_TICKS);
            }
        }
    }

    /** 攻击者周围 {@link TransmutationAntidoteEvent#DETECT_RADIUS} 格内有没有"变形药水"（药水瓶或效果云）。 */
    private static boolean hasNearbyTransmutationPotion(LivingEntity attacker) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return false;
        }
        AABB box = attacker.getBoundingBox().inflate(TransmutationAntidoteEvent.DETECT_RADIUS);
        for (ThrownPotion potion : level.getEntitiesOfClass(ThrownPotion.class, box)) {
            if (cn.autoforged.joes_addons_for_abmc.ModMain.isTransmutationPotionEntity(potion)) {
                return true;
            }
        }
        for (net.minecraft.world.entity.AreaEffectCloud cloud
            : level.getEntitiesOfClass(net.minecraft.world.entity.AreaEffectCloud.class, box)) {
            if (cn.autoforged.joes_addons_for_abmc.ModMain.isTransmutationPotionEntity(cloud)) {
                return true;
            }
        }
        return false;
    }

    private static final class MilkBucketEvent implements PossessedAttackEvent {

        /** 出手间隔（刻）：3 秒。 */
        private static final int COOLDOWN_TICKS = 60;

        /** 起手（掏出奶桶）刻数。 */
        private static final int WIND_UP_TICKS = 6;

        /** 朝下丢的初速（格/刻）：小一点，让它落在脚边。 */
        static final float DROP_SPEED = 0.35F;

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        @Override
        public boolean highestPriority() {
            return true;
        }

        @Override
        public boolean canRun(LivingEntity attacker, LivingEntity target) {
            return attacker.hasEffect(MobEffects.WITHER) || attacker.hasEffect(MobEffects.POISON);
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MILK_BUCKET));
            attacker.swing(InteractionHand.MAIN_HAND);
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            dropMilkBucketAtFeet(attacker);
        }
    }

    /**
     * 往脚下丢一桶奶（砸地后按 ±4 格清空状态效果）。事件与"空壳自救"两条路径共用这一份实现。
     */
    private static void dropMilkBucketAtFeet(LivingEntity actor) {
        if (!(actor.level() instanceof ServerLevel level)) {
            return;
        }
        ThrownMilkBucket bucket = new ThrownMilkBucket(level, actor);
        bucket.setPos(actor.getX(), actor.getEyeY() - 0.1, actor.getZ());
        bucket.shoot(0.0, -1.0, 0.0, MilkBucketEvent.DROP_SPEED, 0.0F);
        level.addFreshEntity(bucket);
        level.playSound(null, actor.getX(), actor.getY(), actor.getZ(),
            SoundEvents.BUCKET_EMPTY, SoundSource.HOSTILE, 1.0F, 1.2F);
    }

    /**
     * <b>没有索敌目标时也要能自救</b>：中了凋灵/中毒就往脚下丢奶桶。
     * <p>
     * 为什么单独开这条路：攻击抽签整段代码都要求"有目标"（没目标时 {@code tick()} 直接 return），
     * 而中毒/凋灵是"随时会发生、和目标无关"的状态 —— 用户实测就是"空壳挂着凋灵、旁边没目标，
     * 它一桶奶都不丢"。和吃附魔金苹果一样，这属于保命动作，不该以"有敌人"为前提。
     * <p>
     * 冷却与抽签里那张奶桶签共用（{@code MILK_BUCKET} 自己的 {@code cooldownTicks}），
     * 所以两条路径不会互相刷。
     *
     * @return true = 这一次真的丢了（调用方本刻可以直接结束 tick）
     */
    public static boolean tryEmergencyMilkBucket(PlayerShellEntity shell) {
        long gameTime = shell.level().getGameTime();
        // canRun 只看自己身上的效果，与"目标"无关，所以这里把自己当成目标传进去即可
        if (!MILK_BUCKET.canRun(shell, shell) || !shell.isAttackReady(MILK_BUCKET, gameTime)) {
            return false;
        }
        dropMilkBucketAtFeet(shell);
        shell.markAttackUsed(MILK_BUCKET, gameTime);
        return true;
    }

    /**
     * "最高优先级特攻"此刻是不是真的可用：条件成立<b>且</b>冷却已好。
     * 供空壳在 tick 里决定要不要插队（见 {@code PossessedAttackEvent#highestPriority()}）。
     * <p>
     * 目前有两张这种签：<b>丢奶桶解毒</b>（{@link #MILK_BUCKET}）与<b>丢缓降药水</b>
     * （{@link #SLOW_FALLING_POTION}）。抽签时优先奶桶（先自救）。
     */
    public static boolean highestPriorityReady(PlayerShellEntity attacker, LivingEntity target) {
        long gameTime = attacker.level().getGameTime();
        return (MILK_BUCKET.canRun(attacker, target) && attacker.isAttackReady(MILK_BUCKET, gameTime))
            || (TRANSMUTATION_ANTIDOTE.canRun(attacker, target)
                && attacker.isAttackReady(TRANSMUTATION_ANTIDOTE, gameTime))
            || (SLOW_FALLING_POTION.canRun(attacker, target)
                && attacker.isAttackReady(SLOW_FALLING_POTION, gameTime));
    }

    /** 目标是不是"手持重锤"（主手或副手）。 */
    private static boolean holdsMace(LivingEntity target) {
        return target.getMainHandItem().is(Items.MACE) || target.getOffhandItem().is(Items.MACE);
    }

    /**
     * 特攻：朝目标丢<b>3~4 瓶</b>缓降药水（喷溅型，命中后目标获得缓降），每瓶带一点微小的随机散射。
     * <p>
     * 存在感全靠"重锤 + 无缓降"这个条件；条件不满足时 {@code canRun} 直接返回 false，
     * 所以它永远不会出现在普通抽签里。
     */
    private static final class SlowFallingPotionEvent implements PossessedAttackEvent {

        /** 出手间隔（刻）：5 秒。 */
        private static final int COOLDOWN_TICKS = 100;

        /**
         * 目标<b>带着重锤滞空</b>时的出手间隔（刻）：10。
         * <p>
         * 砸击窗口只有十几刻（跳起来到砸下去），按地面那套 {@value #COOLDOWN_TICKS} 刻等，
         * 结果必然是"药水等目标落地甚至砸完之后才丢得出来"（实测反馈）。
         * 缩到 10 刻：一次滞空里最多补两三轮，砸中之后目标带上缓降、{@code canRun} 随即为假，自然就停手了。
         */
        private static final int AIRBORNE_COOLDOWN_TICKS = 10;

        /** 起手（亮出药瓶）刻数：1 刻 —— 抬手就扔，不给目标反应时间。 */
        private static final int WIND_UP_TICKS = 1;

        /** 药水初速（格/秒）：<b>近距离</b>按这个值扔（20 格/秒 = 1 格/刻，{@code shoot} 的速度参数就是格/刻）。 */
        private static final float MIN_SPEED_BLOCKS_PER_SECOND = 20.0F;

        /** 远距离提速的余量系数：补偿空气阻力（见 {@link #PROJECTILE_DRAG}），1.2 实测够用。 */
        private static final double REACH_MARGIN = 1.2;

        /**
         * 拦截时间扫描：目标"未来第几刻的位置"作为瞄准点，从 0 扫到
         * {@link #INTERCEPT_SCAN_STEPS}×{@link #INTERCEPT_SCAN_STEP} 刻（每 2 刻一档）。
         */
        private static final int INTERCEPT_SCAN_STEPS = 30;
        private static final double INTERCEPT_SCAN_STEP = 2.0;

        /**
         * "拦截时间不自洽"的代价权重（格/刻）：把 {@code |飞行刻数 − 假设拦截时刻|} 折算成格数。
         * 按疾跑约 0.3 格/刻 取 0.3。
         */
        private static final double INTERCEPT_TICK_WEIGHT = 0.3;

        /** 解算器认作"够准"的落点误差（格）：够准的档位里优先选速度最低的那个。 */
        private static final double AIM_TOLERANCE = 0.35;

        /** 速度档：从"三维最低可行速度"起，每档 +8%，最多试到 1.8 倍。 */
        private static final int SPEED_STEPS = 10;
        private static final double SPEED_STEP = 0.08;

        /** 仰角扫描范围（度）：下限略微下压（近距离要往下扔），上限接近垂直（目标可能在正上方）。 */
        private static final int MIN_ANGLE_DEGREES = -20;
        private static final int MAX_ANGLE_DEGREES = 89;

        /**
         * 喷溅药水的重力（格/刻²）：<b>{@code ThrownPotion#getDefaultGravity()} 是 0.05</b>，
         * 不是投射物的默认 0.03 —— 这一点搞错的话弹道会算得偏平，药水半路就栽进地里。
         */
        private static final double POTION_GRAVITY = 0.05;

        /** 投射物每刻的速度衰减（{@code ThrowableProjectile#tick} 里的 {@code f = 0.99}）。 */
        private static final double PROJECTILE_DRAG = 0.99;

        /**
         * 生效距离（格）：和空壳的索敌范围一致 —— 一锁定目标就会掏药瓶。
         * <p>
         * 射程由 {@link #solve} 按"够得着"自选初速保证（它会用三维最低速度起算，
         * 玩家飞在头顶二十几格也能打上去），所以 50 格内都真的砸得到。
         */
        private static final double RANGE = 50.0;

        // ====================== 一次投掷的"分裂瓶数"与散射口径 ======================

        /** 一次丢出的缓降药水<b>最少</b>瓶数：3（用户指定 3~4 瓶）。 */
        private static final int VOLLEY_MIN_BOTTLES = 3;

        /** 在上限之上的随机余量：{@code 3 + [0,2)} → 3 或 4 瓶。 */
        private static final int VOLLEY_EXTRA_BOTTLES = 2;

        /**
         * 水平角度散射幅度（弧度，总跨度）：每瓶在 ±0.08 rad（约 ±4.6°）内随机旋转。
         * <p>
         * 女巫Boss那边用的是 ±0.15 rad，但她是"完全不预判、靠散射蒙"的打法（1~4 瓶撒一片）；
         * 这边每瓶都从预判拦截点出发，所以收紧到一半左右：20 格距离上横向散开约 ±1.6 格 ——
         * 既看得出是"分裂成好几瓶"，又全都落在喷溅半径（4 格）之内，不至于把药水撒到目标以外。
         */
        private static final double SPREAD_ANGLE = 0.16;

        /** 纵向散射幅度（总跨度，归一化方向向量上的 y 增量）：±0.02，约 ±1.1°。 */
        private static final double SPREAD_Y = 0.04;

        /** 初速抖动（相对值，总跨度）：每瓶 ±4%（女巫Boss是 ±10%）——它直接换算成纵深误差，所以收得更紧。 */
        private static final double SPREAD_SPEED = 0.08;

        /** 出手点横向抖动（格，总跨度）：±0.1 格（与女巫Boss相同），让几瓶不从同一个点飞出去。 */
        private static final double SPREAD_POS = 0.2;

        // ====================== 目标坠落轨迹预测（"提前一点预判"） ======================

        /**
         * 目标未来轨迹的模拟长度（刻）：覆盖整个拦截扫描窗口（{@link #INTERCEPT_SCAN_STEPS}×
         * {@link #INTERCEPT_SCAN_STEP} = 60 刻）。
         */
        private static final int TRAJECTORY_MAX_TICKS = 60;

        /** 空中水平阻力（原版 {@code LivingEntity#travel}：离地时 {@code f = 0.91}）。 */
        private static final double AIR_DRAG_HORIZONTAL = 0.91;

        /** 竖直运动：每刻 {@code vy = (vy - g) * 0.98}（{@code LivingEntity#travel}）。 */
        private static final double AIR_DRAG_VERTICAL = 0.98;

        /** 普通重力（格/刻²）：{@code LivingEntity#travel} 里的 {@code d = 0.08}。 */
        private static final double ENTITY_GRAVITY = 0.08;

        /** 缓降效果下的重力（格/刻²）：同处，{@code d = 0.01}（顺便会把坠落距离清零）。 */
        private static final double SLOW_FALLING_GRAVITY = 0.01;

        /**
         * "瞄点落在目标落地之后"的评分罚分（格）：足够大到优先选落地前的解，
         * 但又不是无限大 —— 万一药水根本赶不及，仍然会退回"照着落点扔"这条兜底解。
         */
        private static final double LANDED_PENALTY = 3.0;

        /** 目标未来轨迹：逐刻位置 + 落地刻（{@code -1} 表示窗口内不会落地）。 */
        private record TargetTrajectory(Vec3[] path, int landingTick) {

            /** 第 {@code ticks} 刻时目标的位置（越界取端点；落地之后钉在落点）。 */
            Vec3 at(double ticks) {
                int i = Math.max(0, Math.min(this.path.length - 1, (int) Math.round(ticks)));
                return this.path[i];
            }

            /** 到第 {@code ticks} 刻时目标是不是已经落地了。 */
            boolean landedBy(double ticks) {
                return this.landingTick >= 0 && ticks >= this.landingTick;
            }
        }

        /**
         * 预测目标未来 {@link #TRAJECTORY_MAX_TICKS} 刻的位置。
         * <p>
         * <b>用的是空壳自己那套落地预测的同一套物理</b>（{@code OrbPossessionMaceAttack#predictLanding}）：
         * 每刻先按当前速度位移，再 {@code vx,vz ×= 0.91}、{@code vy = (vy - 0.08) × 0.98}，
         * 碰到"本体所在格可替换、脚下一格是实心"就算落地，之后位置钉在落点、不再往下扎。
         * <p>
         * <b>为什么必须换成它</b>：原来是把目标速度<b>线性外推</b>，目标一下落，那个"未来位置"
         * 就一路扎到地底下 —— 解出来的弹道等于对着地面扔，药水自然只能赶在目标落地之后才炸
         * （实测反馈："玩家都落地了、甚至重锤都砸完了，药水才丢出来"）。
         * 按真实坠落轨迹取瞄点，瞄点就落在目标<b>还在空中</b>的那一段上（配合
         * {@link #LANDED_PENALTY} 优先选落地前的解）。
         */
        private static TargetTrajectory predictTargetTrajectory(LivingEntity target, ServerLevel level) {
            Vec3[] path = new Vec3[TRAJECTORY_MAX_TICKS + 1];
            Vec3 pos = targetPoint(target);
            Vec3 vel = target.getDeltaMovement();
            double gravity = target.hasEffect(MobEffects.SLOW_FALLING)
                ? SLOW_FALLING_GRAVITY : ENTITY_GRAVITY;
            // 鞘翅俯冲时水平速度几乎不掉（原版滑翔的空气动力学），不能按普通坠落的 0.91 衰减 ——
            // 否则每刻都在低估他的横向位移，越远偏得越多。
            double horizontalDrag = target.isFallFlying() ? 0.99 : AIR_DRAG_HORIZONTAL;
            path[0] = pos;
            int landingTick = -1;
            for (int i = 1; i <= TRAJECTORY_MAX_TICKS; i++) {
                if (landingTick < 0) {
                    pos = pos.add(vel);
                    vel = new Vec3(vel.x * horizontalDrag,
                        (vel.y - gravity) * AIR_DRAG_VERTICAL,
                        vel.z * horizontalDrag);
                    BlockPos at = BlockPos.containing(pos.x, pos.y, pos.z);
                    if (level.isLoaded(at)
                        && level.getBlockState(at).canBeReplaced()
                        && !level.getBlockState(at.below()).canBeReplaced()) {
                        landingTick = i;
                    }
                }
                path[i] = pos;
            }
            return new TargetTrajectory(path, landingTick);
        }

        /**
         * 目标此刻是不是"<b>正拿着重锤往下掉</b>"。
         * <p>
         * 重锤砸击的成立条件就是"<b>下落中 + 坠落距离</b>"（原版砸击加成要求坠落距离 &gt; 1.5 格），
         * 所以判据<b>只看运动状态</b>，不看 {@code onGround()} / {@code isFallFlying()} /
         * {@code getAbilities().flying} 这些"状态标记" ——上一版正是被这些标记坑了两次：
         * <ul>
         *   <li>排除了 {@code isFallFlying()} → <b>鞘翅俯冲</b>（"鞘翅 + 重锤"最典型的进场方式）永不触发；</li>
         *   <li>排除了 {@code getAbilities().flying} → <b>创造模式里往下压着飞</b>砸人时永不触发
         *       （而 {@code canRun} 本身并不排斥创造模式玩家，空壳是照打不误的）。</li>
         * </ul>
         * 改成"竖直速度向下 或 已坠落 &gt; 1.5 格"之后，跳跃下坠、塔上跳下、鞘翅俯冲、
         * 创造模式压着飞的俯冲全都算，而悬停/上升/站地上都不算。
         * <p>
         * 只排除水里/岩浆里：那两种地方砸不出重锤的坠落加成。
         */
        private static boolean isFallingMaceThreat(LivingEntity target) {
            if (target.onGround() || target.isInWater() || target.isInLava()) {
                return false;
            }
            return target.getDeltaMovement().y < -0.05 || target.fallDistance > 1.5F;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        @Override
        public boolean highestPriority() {
            return true;
        }

        /**
         * 目标带着重锤<b>滞空下落</b>时把出手间隔缩到 {@link #AIRBORNE_COOLDOWN_TICKS} 刻。
         * <p>
         * 砸击窗口只有十几刻（跳起来到砸下去），按地面那套 {@value #COOLDOWN_TICKS} 刻等，
         * 结果必然是"药水等目标落地甚至砸完之后才丢得出来"（实测反馈）。
         * 缩到 10 刻：一次滞空里最多补两三轮，砸中之后目标带上缓降、{@code canRun} 随即为假，自然就停手了。
         */
        @Override
        public int cooldownTicks(LivingEntity attacker, LivingEntity target) {
            return target != null && isFallingMaceThreat(target)
                ? AIRBORNE_COOLDOWN_TICKS : COOLDOWN_TICKS;
        }

        @Override
        public boolean canRun(LivingEntity attacker, LivingEntity target) {
            return attacker.distanceToSqr(target) <= RANGE * RANGE
                && holdsMace(target)
                && !target.hasEffect(MobEffects.SLOW_FALLING);
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            // 手里亮出喷溅药瓶，摆个投掷架势
            attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPLASH_POTION));
            attacker.swing(InteractionHand.MAIN_HAND);
        }

        @Override
        public void endWindUp(LivingEntity attacker) {
            // 药瓶丢出去了，手上不需要留东西（下一次抽签会清手）
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            ItemStack splash = new ItemStack(Items.SPLASH_POTION);
            splash.set(DataComponents.POTION_CONTENTS, new PotionContents(
                Optional.of(Potions.SLOW_FALLING), Optional.empty(), List.of()));
            Vec3 from = attacker.getEyePosition();
            // 打提前量（求拦截点）：药水要飞十几到几十刻才到，照着"现在的位置"扔的话，
            // 直线走动的目标会在药水落地前走出落点 —— 表现就是"方向差不多但对不上，差一个小角度"。
            // 做法是<b>直接扫"拦截时刻 t"</b>：把瞄准点放在"目标 t 刻后的位置"上解一次弹道，
            // 取"解出来的飞行刻数最接近 t、落点误差又最小"的那条。
            // 不用"用飞行刻数反推瞄准点再迭代"那种写法：远距离/横向移动时 t ↦ 飞行刻数
            // 映射会来回震荡、根本不收敛（实测 50 格外横向疾跑能差 7 格以上）。
            Vec3 targetNow = targetPoint(target);
            Vec3 targetVelocity = target.getDeltaMovement();
            // 目标在"正往下掉"时：用真实坠落轨迹当瞄点（见 predictTargetTrajectory）——
            // 这正是"再提前一点预判"的关键：线性外推会把下落中的目标一路推到地底下，
            // 于是药水只可能等它落地之后才炸。其它情况（站着走、站着不动）仍用线性外推。
            // <b>创造模式/飘浮那种"匀速压着飞"的下降不能用坠落模型</b>：它不加速，
            // 套上重力会把瞄点算得比本人更低 —— 那种仍然用线性外推。
            boolean urgent = isFallingMaceThreat(target);
            boolean poweredFlight = target instanceof Player flyingPlayer && flyingPlayer.getAbilities().flying;
            TargetTrajectory trajectory = urgent && !poweredFlight
                ? predictTargetTrajectory(target, level) : null;
            Aim aim = null;
            double chosenLead = 0.0;
            double bestScore = Double.MAX_VALUE;
            for (int step = 0; step <= INTERCEPT_SCAN_STEPS; step++) {
                double leadTicks = step * INTERCEPT_SCAN_STEP;
                Vec3 aimPoint = trajectory != null
                    ? trajectory.at(leadTicks)
                    : targetNow.add(targetVelocity.scale(leadTicks));
                Aim candidate = solve(from, aimPoint, POTION_GRAVITY, urgent);
                if (candidate == null) {
                    continue;
                }
                double score = Math.abs(candidate.flightTicks() - leadTicks) * INTERCEPT_TICK_WEIGHT
                    + candidate.miss();
                // 能在目标落地前把药水送到就别拖到落地之后（滞空时才有意义）
                if (trajectory != null && trajectory.landedBy(leadTicks)) {
                    score += LANDED_PENALTY;
                }
                if (score < bestScore) {
                    bestScore = score;
                    aim = candidate;
                    chosenLead = leadTicks;
                }
            }
            if (aim == null) {
                return;
            }
            Vec3 direction = aim.direction();
            // 一次丢 3~4 瓶（用户指定），每瓶在方向上叠一点微小的随机偏移 —— 口径照女巫Boss
            // 那套"多瓶散射"（{@code ModMain#throwWitchBossTransmutationPotion}）：水平角度随机旋转、
            // 纵向随机抬压、初速抖动、出手点横向抖动。
            // 与女巫Boss的区别：那边是 1~4 瓶且完全不预判（靠散射蒙），这边是 3~4 瓶、
            // 每瓶都以上面解出来的<b>预判拦截点</b>为基准再散开，所以整体仍是"冲着你去的"，只是不会三瓶叠成一条线。
            net.minecraft.util.RandomSource rand = level.getRandom();
            int volley = VOLLEY_MIN_BOTTLES + rand.nextInt(VOLLEY_EXTRA_BOTTLES); // 3~4
            for (int i = 0; i < volley; i++) {
                double ang = (rand.nextDouble() - 0.5) * SPREAD_ANGLE;          // 水平角度偏移(rad)
                double ca = Math.cos(ang);
                double sa = Math.sin(ang);
                double dx = direction.x * ca + direction.z * sa;
                double dz = -direction.x * sa + direction.z * ca;
                double dy = direction.y + (rand.nextDouble() - 0.5) * SPREAD_Y;  // 纵向偏移（抬高/压低抛物线）
                double speed = aim.speed() * (1.0 + (rand.nextDouble() - 0.5) * SPREAD_SPEED);
                ThrownPotion potion = new OrbSlowFallingPotion(level, attacker);
                potion.setItem(splash.copy());
                potion.setPos(from.x + (rand.nextDouble() - 0.5) * SPREAD_POS,
                    from.y - 0.1,
                    from.z + (rand.nextDouble() - 0.5) * SPREAD_POS);
                potion.shoot(dx, dy, dz, (float) speed, 1.0F);
                level.addFreshEntity(potion);
            }
            level.playSound(null, from.x, from.y, from.z, SoundEvents.SPLASH_POTION_THROW,
                SoundSource.HOSTILE, 1.0F, 1.0F);
            // 诊断日志（排查"药水方向不对/打不中"用）：投手/目标位置、目标速度、拦截点、基准方向与初速
            // 拦截点直接取解算时用的那个瞄点（走轨迹预测时它才是真正的瞄点，线性外推会算出地底下的点）
            Vec3 intercept = trajectory != null
                ? trajectory.at(chosenLead)
                : targetNow.add(targetVelocity.scale(chosenLead));
            Vec3 lead = targetVelocity;
            String airtime = trajectory == null ? "地面/线性外推"
                : (trajectory.landingTick() >= 0
                    ? trajectory.landingTick() + "刻后落地" : "窗口内不落地");
            cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
                "[缓降药水] 投手={} @({},{},{})  目标={} @({},{},{}) 速度=({},{},{})"
                    + "  现在距离={}  拦截点=({},{},{})（{}(刻)后，{}）  飞行刻数={} 预估误差={}"
                    + "  瓶数={}  基准方向=({},{},{})  基准初速={}",
                attacker.getName().getString(), fmt(from.x), fmt(from.y), fmt(from.z),
                target.getName().getString(), fmt(targetNow.x), fmt(targetNow.y), fmt(targetNow.z),
                fmt(lead.x), fmt(lead.y), fmt(lead.z),
                fmt(Math.sqrt(horizontalSqr(from, targetNow))),
                fmt(intercept.x), fmt(intercept.y), fmt(intercept.z), fmt(chosenLead), airtime,
                fmt(aim.flightTicks()), fmt(aim.miss()),
                volley,
                fmt(direction.x), fmt(direction.y), fmt(direction.z), fmt(aim.speed()));
        }

        /** 诊断日志用的小数格式化。 */
        private static String fmt(double value) {
            return String.format(java.util.Locale.ROOT, "%.2f", value);
        }

        /** 目标身上要瞄的那个点：身体中段（喷溅是 ±4 格横向 / ±2 格纵向，差一两格照样糊得到）。 */
        private static Vec3 targetPoint(LivingEntity target) {
            return new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
        }

        /** 两点在水平面上的距离平方。 */
        private static double horizontalSqr(Vec3 a, Vec3 b) {
            double dx = b.x - a.x;
            double dz = b.z - a.z;
            return dx * dx + dz * dz;
        }

        /**
         * "能打到这个点"的<b>最低</b>初速（格/刻）：{@code v_min = √(g·(h + √(d² + h²)))}，
         * 其中 d = 水平距离、h = 高差（经典的最小发射速度公式）。
         * <p>
         * h = 0 时退化成 {@code √(g·d)}（水平射程下限）；目标在正上方 h 格时约需 {@code √(2gh)}。
         * <b>必须用三维关系</b>：只看水平距离的话，"玩家飞到空壳正上方"会算出 20 格/秒，
         * 而 20 格/秒在 0.05 的重力下最多只能升高 {@code v²/(2g) = 10} 格 ——
         * 药水会在半空失去速度掉回来，表现就是"飞不上去、也碰不到人"。
         */
        private static double minimumReachSpeed(double d, double dy) {
            return Math.sqrt(POTION_GRAVITY * (dy + Math.sqrt(d * d + dy * dy)));
        }

        /**
         * 弹道解算：挑<b>初速 + 仰角</b>，并给出这一投预计飞多少刻（用于打提前量）。
         * <p>
         * <b>水平方向永远是"从空壳指向瞄准点"</b>（水平分量就是那个单位向量），这里只决定抬多高、扔多快。
         * 药水是带重力的抛射物（{@link #POTION_GRAVITY}），直着扔的话远处会掉在目标脚前好几格 ——
         * 所以必须抬枪口。做法不是解析求解，而是<b>把候选弹道各飞一遍</b>：
         * 按 {@code ThrowableProjectile#tick} 的真实物理（每刻先位移、再乘
         * {@link #PROJECTILE_DRAG}、再扣重力）模拟到飞过目标，取"全程离目标最近"的那条。
         * <p>
         * 速度从 {@link #minimumReachSpeed}（乘 {@link #REACH_MARGIN} 补阻力）起，按
         * {@link #SPEED_STEP} 逐档往上试，<b>一旦某档已经够准（误差 ≤ {@link #AIM_TOLERANCE}）就收手</b> ——
         * 这样速度总是"够得着里最慢的那一档"，不会为了精度一路飙快。
         * <p>
         * <b>例外</b>：{@code reachAsap} 为真时改成"够准的档里挑<b>到得最早</b>的那一档"
         * （不为精度一路飙快，但也不为了好看慢悠悠地飞）。用于目标<b>滞空</b>时 ——
         * 砸击窗口只有十几刻，药水晚到就等于白扔；把飞行刻数从 ~19 压到 ~9 才能赶在落地之前炸。
         * <p>
         * 仰角扫描 {@link #MIN_ANGLE_DEGREES}°~{@link #MAX_ANGLE_DEGREES}°：上界接近垂直，
         * 因为目标可能在<b>空壳正上方</b>。瞄准点与空壳的水平距离几乎为 0 时没有"水平方向"可言，
         * 直接朝正上/正下扔（竖直情况单独算飞行刻数）。
         *
         * @param reachAsap 是否以"尽早到达"优先（目标滞空时用）
         */
        @Nullable
        private static Aim solve(Vec3 from, Vec3 to, double gravity, boolean reachAsap) {
            double dx = to.x - from.x;
            double dz = to.z - from.z;
            double dy = to.y - from.y;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < 1.0E-4) {
                // 瞄准点几乎就在正上/正下方：方向固定竖直，只要定速度与飞行刻数
                double speed = Math.max(MIN_SPEED_BLOCKS_PER_SECOND / 20.0,
                    minimumReachSpeed(0.0, dy) * REACH_MARGIN);
                return new Aim(new Vec3(0.0, dy >= 0.0 ? 1.0 : -1.0, 0.0),
                    verticalFlightTicks(dy, speed, gravity), 0.0, speed);
            }
            double minSpeed = Math.max(MIN_SPEED_BLOCKS_PER_SECOND / 20.0,
                minimumReachSpeed(d, dy) * REACH_MARGIN);
            Aim best = null;
            Aim earliestAccurate = null;
            for (int step = 0; step <= SPEED_STEPS; step++) {
                double speed = minSpeed * (1.0 + step * SPEED_STEP);
                Aim candidate = bestAngle(dx / d, dz / d, d, dy, speed, gravity);
                if (best == null || candidate.miss() < best.miss()) {
                    best = candidate;
                }
                if (candidate.miss() <= AIM_TOLERANCE) {
                    if (!reachAsap) {
                        return candidate;   // 够准了：越慢越好，不再往快里试
                    }
                    // 抢时间：把所有"够准"的档都比一遍，留到得最早的那条
                    if (earliestAccurate == null || candidate.flightTicks() < earliestAccurate.flightTicks()) {
                        earliestAccurate = candidate;
                    }
                }
            }
            return earliestAccurate != null ? earliestAccurate : best;
        }

        /** 平时的解算：够准就收手（越慢越好），见 {@link #solve(Vec3, Vec3, double, boolean)}。 */
        @Nullable
        private static Aim solve(Vec3 from, Vec3 to, double gravity) {
            return solve(from, to, gravity, false);
        }

        /** 固定速度下扫一遍仰角，返回最优的那条弹道。 */
        private static Aim bestAngle(double dirX, double dirZ, double d, double dy,
                                     double speed, double gravity) {
            double bestTan = dy / d;          // 兜底：直接瞄准
            Flight best = null;
            for (int degrees = MIN_ANGLE_DEGREES; degrees <= MAX_ANGLE_DEGREES; degrees++) {
                double tan = Math.tan(Math.toRadians(degrees));
                Flight flight = simulate(d, dy, speed, gravity, tan);
                if (best == null || flight.miss() < best.miss()) {
                    best = flight;
                    bestTan = tan;
                }
            }
            return new Aim(new Vec3(dirX, bestTan, dirZ), best.ticks(), best.miss(), speed);
        }

        /** 竖直投掷（瞄准点在正上/正下方）的飞行刻数：按真实物理模拟到最接近目标那一刻。 */
        private static double verticalFlightTicks(double dy, double speed, double gravity) {
            double y = 0.0;
            double vy = dy >= 0.0 ? speed : -speed;
            double best = Double.MAX_VALUE;
            double bestTick = 1.0;
            for (int tick = 1; tick <= 400; tick++) {
                y += vy;
                vy = vy * PROJECTILE_DRAG - gravity;
                double miss = Math.abs(y - dy);
                if (miss < best) {
                    best = miss;
                    bestTick = tick;
                }
                if (vy < 0.0 && y < dy - 8.0) {
                    break;
                }
                if (vy > 0.0 && y > dy + 8.0) {
                    break;
                }
            }
            return bestTick;
        }

        /**
         * 用给定初速与仰角把药水"飞"一遍，返回最近距离与那一刻的刻数。
         * <p>
         * 物理与 {@code ThrowableProjectile#tick} 一致：每刻先按当前速度位移，
         * 再把速度乘 {@link #PROJECTILE_DRAG}，最后扣 {@link #POTION_GRAVITY}。
         * 目标超出该速度的射程时，"最近距离"就是最接近时差多远，同样能选出最靠谱的弹道。
         * <p>
         * <b>提前结束的条件必须带上"正在下落"</b>（{@code vy < 0}）：瞄准点在空壳<b>上方很高</b>时，
         * 药水起点本来就比目标低十几二十格，只按 {@code y < dy - 8} 判断会在<b>第一刻就退出循环</b>，
         * 于是解算器等于什么都没算（日志里"飞行刻数=1"就是这个症状），药水也就永远打不上去。
         * 反过来，目标在下方时这个条件本来就该在下落时触发，加 {@code vy < 0} 不影响。
         */
        private static Flight simulate(double d, double dy, double speed, double gravity, double tan) {
            double length = Math.sqrt(1.0 + tan * tan);
            double vx = speed / length;
            double vy = speed * tan / length;
            double x = 0.0;
            double y = 0.0;
            double bestMiss = Double.MAX_VALUE;
            double bestTick = 0.0;
            // 1 格/刻的速度下 400 刻足够飞到 300 格外；正常一二十刻就出结果
            for (int tick = 1; tick <= 400; tick++) {
                x += vx;
                y += vy;
                vx *= PROJECTILE_DRAG;
                vy = vy * PROJECTILE_DRAG - gravity;
                double miss = Math.sqrt((x - d) * (x - d) + (y - dy) * (y - dy));
                if (miss < bestMiss) {
                    bestMiss = miss;
                    bestTick = tick;
                }
                if (x > d + 8.0 || (y < dy - 8.0 && vy < 0.0)) {
                    break;   // 已经飞过目标足够远 / 已越过目标并往下掉得够深
                }
            }
            return new Flight(bestMiss, bestTick);
        }

        /** 一次模拟的结果：整段航程里离目标最近的距离，以及那一刻用掉的刻数。 */
        private record Flight(double miss, double ticks) {
        }

        /** 弹道解算的结果：发射方向、预计飞行刻数、预估落点误差、初速。 */
        private record Aim(Vec3 direction, double flightTicks, double miss, double speed) {
        }
    }

    // ====================== 「反重锤」应急出手（绕过抽签/起手/冷却） ======================

    /** 应急出手的诊断日志节流：每具空壳最多每 {@value #DIAG_INTERVAL_TICKS} 刻一条。 */
    private static final Map<UUID, Long> SLOW_FALLING_DIAG_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int DIAG_INTERVAL_TICKS = 40;

    /** 诊断账本的封顶：空壳换来换去时也不至于无限涨（与 {@code OrbAdversaryRelations} 同做法）。 */
    private static final int DIAG_MAP_MAX = 256;

    /**
     * <b>「反重锤」应急出手</b>：目标带着重锤<b>往下掉</b>、身上又没有缓降时，
     * <b>绕过整套"抽签 → 起手 → 出手"流程</b>，当场丢一发缓降药水。每刻由 {@code PlayerShellEntity#tick}
     * 在"重锤动作/吃东西那几条 return<b>之前</b>"调用。
     *
     * <h2>为什么必须绕过那套流程</h2>
     * 正常流程前面有一串"这一刻不出手"的闸门，每一个都足以盖掉整个砸击窗口：
     * <ul>
     *   <li><b>空壳自己的重锤动作</b>（{@code possessionMaceState != null}）——"冲天 → 俯冲 → 砸 → 落地水"
     *       动辄好几秒（上限 {@code TOTAL_TIMEOUT_TICKS} = 30 秒），期间 {@code tick} 在抽签/起手/出手
     *       之前就 return 了；</li>
     *   <li><b>吃东西</b>（受伤后啃附魔金苹果/补饥饿）期间同样直接 return；</li>
     *   <li><b>抽签间隔 1~2 秒</b> + 药水自己的冷却；</li>
     *   <li>1 刻起手。</li>
     * </ul>
     *
     * <h2>这一招自己的判据（只有三条）</h2>
     * <ol>
     *   <li>目标<b>正在下落</b>（{@link SlowFallingPotionEvent#isFallingMaceThreat}：看运动状态，
     *       跳跃下坠/塔上跳下/<b>鞘翅俯冲</b>都算）；</li>
     *   <li>手里有重锤、身上没有缓降、在射程内（{@code canRun}）；</li>
     *   <li>药水不在滞空短冷却里（10 刻，防连发）。</li>
     * </ol>
     *
     * <h2>候选对象<b>不依赖索敌</b></h2>
     * 先用当前索敌目标（零延迟）；若它不是"该泼的人"（没锁上、锁的是别人、被判定成不该打），
     * <b>就自己在射程内找</b>（{@link #findFallingMacePlayers}）——
     * 上一版只认索敌目标，而索敌是每 10 刻才扫一次、还要求看得见，
     * "人停在空壳头顶/刚从别的目标转过来"时会整整几秒没有人可泼（实测反馈）。
     * <p>
     * 直接调 {@code run}、不走 {@code beginWindUp}：它此刻手里可能正握着<b>重锤/水桶/烟花</b>
     * （自己那套动作用），换手会把那套动作弄坏。
     *
     * @return 这一发是不是真的丢出去了
     */
    public static boolean tryEmergencySlowFallingThrow(PlayerShellEntity shell) {
        if (shell.level().isClientSide) {
            return false;
        }
        long gameTime = shell.level().getGameTime();
        // 候选①：当前索敌目标（正常就该泼他，零延迟）
        LivingEntity locked = shell.getTarget();
        if (locked != null && !shell.mustNotAttack(locked)
            && SLOW_FALLING_POTION.canRun(shell, locked)
            && SlowFallingPotionEvent.isFallingMaceThreat(locked)) {
            if (fireSlowFallingIfReady(shell, locked, gameTime)) {
                return true;
            }
            diagnoseSlowFallingBlocked(shell, gameTime, locked, "药水还在冷却里");
            return false;
        }
        // 候选②：索敌没锁上 / 锁的是别人 / 目标被判定成不该打 → 自己在射程内找"正在下落的持锤玩家"
        boolean cooldownBlocked = false;
        for (Player candidate : findFallingMacePlayers(shell)) {
            if (shell.mustNotAttack(candidate)) {
                continue;
            }
            if (fireSlowFallingIfReady(shell, candidate, gameTime)) {
                return true;
            }
            cooldownBlocked = true;
            if (diagnoseSlowFallingBlocked(shell, gameTime, candidate, "药水还在冷却里")) {
                return false;
            }
        }
        if (!cooldownBlocked) {
            // 射程里连"正在下落的持锤玩家"都没有：看看是不是"人在掉、但还没把重锤掏出来"
            diagnoseSlowFallingNoMaceHolder(shell, gameTime);
        }
        return false;
    }

    /** 冷却允许就当场丢一发；返回这一发是否真的丢出去了。 */
    private static boolean fireSlowFallingIfReady(PlayerShellEntity shell, LivingEntity target, long gameTime) {
        if (!shell.isAttackReady(SLOW_FALLING_POTION, gameTime)) {
            return false;
        }
        SLOW_FALLING_POTION.run(shell, target);
        shell.markAttackUsed(SLOW_FALLING_POTION, gameTime);
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
            "[缓降药水] 应急出手成功（绕过抽签/起手）：目标 {} 正在下落 y={} vy={} 坠落={} 鞘翅={}，空壳状态={}",
            target.getName().getString(), f(target.getY()), f(target.getDeltaMovement().y),
            f(target.fallDistance), target.isFallFlying(),
            shell.isMaceSequenceActive() ? "重锤动作中" : "常规");
        return true;
    }

    /** 射程内的"正在下落的持锤玩家"（手持重锤、没有缓降、在下落）。 */
    private static List<Player> findFallingMacePlayers(PlayerShellEntity shell) {
        return shell.level().getEntitiesOfClass(Player.class,
            shell.getBoundingBox().inflate(SlowFallingPotionEvent.RANGE),
            p -> holdsMace(p) && !p.hasEffect(MobEffects.SLOW_FALLING)
                && SlowFallingPotionEvent.isFallingMaceThreat(p));
    }

    /**
     * 诊断：明明该泼却没泼出去。把该玩家当时的运动状态一并打出来，用来定位是哪条判据挡的
     * （{@code onGround} / {@code vy} / 坠落距离 / 鞘翅 / 水中 / 创造飞行，一次全有）。
     *
     * @return 这一条是否真的写进了日志（写过了就不再重复调用）
     */
    private static boolean diagnoseSlowFallingBlocked(PlayerShellEntity shell, long gameTime,
            LivingEntity who, String why) {
        if (!diagAllowed(shell, gameTime)) {
            return false;
        }
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
            "[缓降药水] 该丢却没丢：{}｜{} onGround={} vy={} 坠落={} 鞘翅={} 水中={} 创造飞行={}",
            why, who.getName().getString(), who.onGround(), f(who.getDeltaMovement().y),
            f(who.fallDistance), who.isFallFlying(), who.isInWater(),
            who instanceof Player p && p.getAbilities().flying);
        return true;
    }

    /**
     * 诊断：射程内有"已经掉够高、足以砸出加成"的玩家，但他<b>手里没有重锤</b> ——
     * 那就是"他还没把重锤掏出来"这种情形（空壳无从预判，只能等他换手）。
     */
    private static void diagnoseSlowFallingNoMaceHolder(PlayerShellEntity shell, long gameTime) {
        if (!diagAllowed(shell, gameTime)) {
            return;
        }
        List<Player> falling = shell.level().getEntitiesOfClass(Player.class,
            shell.getBoundingBox().inflate(SlowFallingPotionEvent.RANGE),
            p -> !p.onGround() && !p.hasEffect(MobEffects.SLOW_FALLING)
                && (p.fallDistance > 1.5F || p.getDeltaMovement().y < -0.5));
        if (falling.isEmpty()) {
            return;
        }
        Player p = falling.get(0);
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
            "[缓降药水] 没有可泼的目标：玩家 {} 正在下落（坠落={}）但没拿重锤（主手={} 副手={}）",
            p.getName().getString(), f(p.fallDistance),
            p.getMainHandItem().getItem(), p.getOffhandItem().getItem());
    }

    /** 日志用的小数格式化。 */
    private static String f(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    /** 诊断日志节流（同一具空壳每 {@value #DIAG_INTERVAL_TICKS} 刻最多一条）。 */
    private static boolean diagAllowed(PlayerShellEntity shell, long gameTime) {
        if (SLOW_FALLING_DIAG_TICK.size() > DIAG_MAP_MAX) {
            SLOW_FALLING_DIAG_TICK.clear();
        }
        Long last = SLOW_FALLING_DIAG_TICK.get(shell.getUUID());
        if (last != null && gameTime - last < DIAG_INTERVAL_TICKS) {
            return false;
        }
        SLOW_FALLING_DIAG_TICK.put(shell.getUUID(), gameTime);
        return true;
    }

    /** 对面的特攻行为；没有匹配返回 null。 */
    @Nullable
    private static Matchup matchupFor(LivingEntity target) {
        for (Matchup matchup : MATCHUPS) {
            if (matchup.condition().test(target)) {
                return matchup;
            }
        }
        return null;
    }

    /**
     * 这个目标是不是被特攻表要求"<b>尽量远程</b>"（即 {@code alwaysMelee == false} 的那些条目：
     * 监守者、女巫、苦力怕、铁傀儡、卫道士，以及满血的暮色巫妖等）。
     * <p>
     * 供走位使用：这类敌人贴身的代价太高（或根本不该靠近），所以要让空壳
     * <b>绝不主动靠近、并保持在 8~15 格</b>（见 {@code OrbPossessionKiteGoal} 的"保持距离"模式
     * 与 {@code PlayerShellEntity#mustAlwaysKite}）。
     * <p>
     * 判定是<b>每次现算</b>的：像"满血巫妖"这种条件，一旦它掉血、条目不再匹配，这里立刻回到 false，
     * 空壳马上恢复正常走位（该贴脸就贴脸）。
     */
    public static boolean prefersRanged(LivingEntity target) {
        Matchup matchup = matchupFor(target);
        return matchup != null && !matchup.alwaysMelee();
    }

    private OrbPossessedAttackEvents() {
    }

    /**
     * 抽签：<b>贴身必出近战（除非这个敌人要求保持距离）；否则只出远程</b>，
     * 并按对面的<b>特攻行为</b>（{@link #MATCHUPS}）调整：
     * <ol>
     *   <li><b>最高优先级特攻</b>（都不属于任何池子，只有对应情况才会被调用）：
     *       自己中凋灵/中毒 → 往脚下丢奶桶（{@link #MILK_BUCKET}）；
     *       10 格内有变形药水 → 朝天上抛变形解药（{@link #TRANSMUTATION_ANTIDOTE}）；
     *       目标手持重锤且没有缓降 → 丢缓降药水（{@link #SLOW_FALLING_POTION}）；</li>
     *   <li>目标在 {@link #MELEE_RANGE} 格内且该敌人要求"贴身走近战" → 从该敌人的近战池里抽
     *       （默认 {@link #MELEE}；监守者只有 Bob 一张签），偏好事件按概率优先；</li>
     *   <li>其余情况从该敌人的远程池里抽（默认 {@link #RANGED_WITH_OTHER} = 远程池 + 其它攻击；
     *       末影人只剩召唤爆炸物+铁砧雨），偏好事件按概率优先；</li>
     *   <li>两边都抽不出可用事件时，用 {@link #OTHER} 兜底（它同时也在上一条的池子里）。</li>
     * </ol>
     * 冷却（{@link PossessedAttackEvent#cooldownTicks()}）不参与筛选：抽到的事件若还在冷却里，
     * 这个窗口就只是继续走位，不会改抽别的、也不会因为冷却去重抽。
     * <b>唯一的例外</b>是上面那条最高优先级特攻 —— 它在返回之前就查了冷却，
     * 免得"目标拿重锤但药水还在冷却"时把整个攻击窗口白白让掉。
     */
    public static PossessedAttackEvent drawUsable(PlayerShellEntity attacker, LivingEntity target) {
        // 0) 最高优先级特攻①：自己中了凋灵/中毒 → 先往脚下丢奶桶自救。
        // 0b) 最高优先级特攻②：10 格内有变形药水 → 朝天上抛变形解药（刚解除变形时零帧起手）。
        // 0c) 最高优先级特攻③：目标手持重锤且没有缓降 → 丢缓降药水。
        //     这些都不在任何池子里、别的场合也不会被调用；冷却没好就跳过，照常走下面的池子。
        long gameTime = attacker.level().getGameTime();
        if (MILK_BUCKET.canRun(attacker, target) && attacker.isAttackReady(MILK_BUCKET, gameTime)) {
            return MILK_BUCKET;
        }
        if (TRANSMUTATION_ANTIDOTE.canRun(attacker, target)
            && attacker.isAttackReady(TRANSMUTATION_ANTIDOTE, gameTime)) {
            // 记一次"看到变形药水了"：这一轮若是首次发现，本次按零帧起手（见 windUpTicks(attacker)）
            attacker.noteTransmutationPotionSighting(gameTime);
            return TRANSMUTATION_ANTIDOTE;
        }
        if (SLOW_FALLING_POTION.canRun(attacker, target)
            && attacker.isAttackReady(SLOW_FALLING_POTION, gameTime)) {
            return SLOW_FALLING_POTION;
        }

        // 0c) 九头蛇特攻：只在"有头正张着嘴"时才出手。
        //     它的身体整段免疫伤害、只有头的碰撞箱吃伤害，而张嘴那一刻头基本不动、最好打。
        //     不是时机就返回 null —— 空壳抽签分支里 `drawnEvent == null` 是每刻都成立的，
        //     所以它会<b>每刻重抽</b>，嘴一张开就立刻打出去，不会白等一个 1~2 秒的窗口。
        if (OptionalMods.isTwilightForestLoaded() && TwilightForestCompat.isHydra(target)
            && !TwilightForestCompat.isHydraMouthOpen(target)) {
            return null;
        }

        // 0d) <b>强制攻击模式</b>（{@code /jafa luck_attack <模式>}，见 {@link ForcedAttack}）：
        //     命令行的模式优先于下面那条 debug 的"永远重锤"，因为它是玩家刚刚显式下的指令。
        ForcedAttack forced = forcedAttack;
        // 0d-① 岩浆牢笼：<b>直接取代整个抽签</b> —— 不再抽近战/远程，也不看目标是不是玩家（用户指定）。
        //       用不了（太远）就返回 null：这个模式下"本窗口不出手"，而不是改抽别的。
        //       注意这里走的是 {@code OTHER_LAVA_CAGE.canRun(...)}（它自己认强制模式），
        //       与空壳出手前那次复查是同一个判据，否则会出现"抽到了却永远不出手"。
        if (forced == ForcedAttack.LAVA_CAGE) {
            return OTHER_LAVA_CAGE.canRun(attacker, target) ? OTHER_LAVA_CAGE : null;
        }

        // 0e) debug 模式：<b>始终用重锤</b>（用户指定）—— 不看距离、不看抽到的是近战还是远程签、
        //     也不参与特攻偏好，能打就直接起手那一整套"冲天 → 俯冲 → 重锤"。
        //     仍然走 canRun：所以"已经会飞 → 永不抽重锤"（用户指定）与"正在砸的过程中不重复抽"照旧有效。
        if (cn.autoforged.joes_addons_for_abmc.config.ModConfig.DEBUG_MODE.get()
            && MELEE_MACE.canRun(attacker, target)) {
            return MELEE_MACE;
        }

        Matchup matchup = matchupFor(target);
        boolean inMeleeRange = attacker.distanceToSqr(target) <= MELEE_RANGE * MELEE_RANGE;
        boolean wantMelee = inMeleeRange && (matchup == null || matchup.alwaysMelee());
        // 会飞之后：默认<b>只抽远程</b>（走位也改成"空中保持距离"，见 OrbPossessionFlightAI）。
        // 唯一的例外是目标自己贴到 3 格以内（= {@link #MELEE_RANGE}）—— 那时才重新允许近战。
        if (attacker.isFlightMode()) {
            wantMelee = inMeleeRange;
        } else if (MELEE_HOOK.canRun(attacker, target)) {
            // 铁抓钩：目标是玩家 + AI 在近身阶段 + 玩家在 5 格之外 → 也走"近战"这一步。
            // 这一步里除了它，别的近战签都够不着（canRun 要求 3 格内），所以 drawFrom 实际只会抽到它。
            // 放在"会飞"那条之外：会飞时空壳的走位是保持距离，压根不算近战状态。
            wantMelee = true;
        }

        // 0f) <b>立刻重抽一次近战</b>：铁抓钩被方块挡住 / 刚把玩家拉到近战距离时由 {@code OrbHookPull} 置起
        //     （用户指定"AI 会立刻抽取下一次近战攻击"）。直接从近战池里抽一张；这一瞬近战池里
        //     一张都不可用（例如玩家又跑远了）就照常往下走正常流程，绝不卡死。
        if (attacker.consumeForcedMeleeDraw()) {
            PossessedAttackEvent forcedMelee = prefer(
                matchup == null ? null : matchup.meleeBias(),
                matchup == null ? 0.0F : matchup.meleeBiasChance(), attacker, target);
            if (forcedMelee == null) {
                forcedMelee = drawFrom(
                    matchup == null || matchup.meleePool() == null ? MELEE : matchup.meleePool(),
                    attacker, target);
            }
            if (forcedMelee != null) {
                return forcedMelee;
            }
        }

        // 1) 该走近战就走近战
        if (wantMelee) {
            // 强制模式 MACE：近战一律重锤（用户指定"若调用了近战攻击"）
            if (forced == ForcedAttack.MACE && MELEE_MACE.canRun(attacker, target)) {
                return MELEE_MACE;
            }
            PossessedAttackEvent biased = prefer(matchup == null ? null : matchup.meleeBias(),
                matchup == null ? 0.0F : matchup.meleeBiasChance(), attacker, target);
            if (biased != null) {
                return biased;
            }
            PossessedAttackEvent melee = drawFrom(
                matchup == null || matchup.meleePool() == null ? MELEE : matchup.meleePool(),
                attacker, target);
            if (melee != null) {
                return melee;
            }
            // 近战这一刻一个都不可用（监守者没目标时就可能是这样）：落到下面抽远程兜底
        }

        // 2) 远程
        // 强制模式 FIREWORK / GUARDIAN_BEAM / FLY：远程一律用指定的那一张
        // （FLY 靠 {@code FlightEvent#canRun} = "还不会飞"自然只生效一次，之后就退回正常抽签）
        PossessedAttackEvent forcedRanged = switch (forced) {
            case FIREWORK -> RANGED_FIREWORK;
            case GUARDIAN_BEAM -> RANGED_GUARDIAN_BEAM;
            case FLY -> RANGED_FLIGHT;
            default -> null;
        };
        if (forcedRanged != null && forcedRanged.canRun(attacker, target)) {
            return forcedRanged;
        }
        PossessedAttackEvent biased = prefer(matchup == null ? null : matchup.rangedBias(),
            matchup == null ? 0.0F : matchup.rangedBiasChance(), attacker, target);
        if (biased != null) {
            return biased;
        }
        PossessedAttackEvent ranged = drawFrom(
            matchup == null || matchup.rangedPool() == null ? RANGED_WITH_OTHER : matchup.rangedPool(),
            attacker, target);
        if (ranged != null) {
            return ranged;
        }

        // 3) 兜底：其它攻击类别（已经并进上面那条远程抽签了，这里只在它上面也没抽到时再试一次）
        return drawFrom(OTHER, attacker, target);
    }

    /**
     * 按概率优先兑现"偏好事件"：{@code chance} 为 1 时必定优先，0.75 就是"较大概率"，
     * 0 或没有偏好事件时返回 null（交给调用方正常抽签）。
     * <p>
     * 偏好事件本身也可能"此刻用不了"（例如冷却/距离条件不满足），这时同样返回 null 让它照常抽。
     */
    @Nullable
    private static PossessedAttackEvent prefer(@Nullable PossessedAttackEvent biased, float chance,
                                               PlayerShellEntity attacker, LivingEntity target) {
        if (biased == null || chance <= 0.0F || blockedByForcedMode(biased)
            || !biased.canRun(attacker, target)) {
            return null;
        }
        return chance >= 1.0F || attacker.getRandom().nextFloat() < chance ? biased : null;
    }

    /**
     * 当前强制模式是不是把这张签<b>禁掉</b>了。
     * <p>
     * 目前只有 {@link ForcedAttack#NO_SUMMONS} 会用：它禁掉所有 {@link PossessedAttackEvent#summons()}
     * （会留下永久实体/方块的）签。抽签的两条路（{@link #drawFrom} 与 {@link #prefer}）都要过这一关，
     * 否则"咒灵/监守者"那类特攻偏好、或池子里本来就有的召唤签照样会被抽出来。
     */
    private static boolean blockedByForcedMode(PossessedAttackEvent event) {
        return forcedAttack == ForcedAttack.NO_SUMMONS && event.summons();
    }

    /** 在某一类别"此刻可用"的事件里<b>按权重</b>抽一个；一个都不可用返回 null。
     *  <p>权重见 {@link PossessedAttackEvent#weight()}：默认都是 1（等概率），
     *  低概率签（飞行 / 重锤）是 {@link #LOW_WEIGHT}（0.1），即"权重为其他行为的 1/10"。 */
    private static PossessedAttackEvent drawFrom(List<PossessedAttackEvent> category,
                                                 PlayerShellEntity attacker, LivingEntity target) {
        List<PossessedAttackEvent> usable = new ArrayList<>(category.size());
        float total = 0.0F;
        for (PossessedAttackEvent event : category) {
            if (!blockedByForcedMode(event) && event.canRun(attacker, target)) {
                usable.add(event);
                total += Math.max(0.0F, event.weight());
            }
        }
        if (usable.isEmpty() || total <= 0.0F) {
            return null;
        }
        float roll = attacker.getRandom().nextFloat() * total;
        for (PossessedAttackEvent event : usable) {
            roll -= Math.max(0.0F, event.weight());
            if (roll < 0.0F) {
                return event;
            }
        }
        return usable.get(usable.size() - 1);
    }

    /** 从发射者眼睛指向"目标该瞄的那个点"的单位向量；两者重合时返回 null。 */
    static Vec3 aimAt(LivingEntity attacker, LivingEntity target) {
        Vec3 direction = aimPointOf(target).subtract(attacker.getEyePosition());
        return direction.lengthSqr() < 1.0E-6 ? null : direction.normalize();
    }

    /**
     * 目标身上该瞄的点：默认就是眼位；<b>暮色九头蛇</b>例外 —— 它的身体整段免疫伤害，
     * 只有头/脖子（{@code HydraPart}）能吃伤害，所以改成瞄"某个活着的头的碰撞箱中心"
     * （优先挑正张着嘴的那个，见 {@code TwilightForestCompat#hydraHeadAimPoint}）。
     * <p>
     * 所有远程事件都从这里取瞄准点（它们都走 {@link #aimAt}），所以这一处就够了。
     */
    static Vec3 aimPointOf(LivingEntity target) {
        if (OptionalMods.isTwilightForestLoaded()) {
            Vec3 hydraHead = TwilightForestCompat.hydraHeadAimPoint(target);
            if (hydraHead != null) {
                return hydraHead;
            }
        }
        return target.getEyePosition();
    }

    /**
     * 造一把带指定等级<b>力量</b>附魔的弓（普通射箭用的是力量 V）。
     * <p>
     * 附魔要在服务端注册表里查，所以拿不到注册表（少见）时退回白板弓，不让整次攻击崩掉。
     * <p>
     * 包内可见（而不是 private）：连发调度器 {@link OrbBurstShots} 每一发都要现造一把，
     * 两边必须用同一份实现，免得"附魔等级"这种数值在两处各写一遍后走偏。
     */
    static ItemStack powerBow(net.minecraft.world.level.Level level, int powerLevel) {
        ItemStack bow = new ItemStack(Items.BOW);
        if (level.registryAccess() != null) {
            var enchantments = level.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            bow.enchant(enchantments.getOrThrow(
                net.minecraft.world.item.enchantment.Enchantments.POWER), powerLevel);
        }
        return bow;
    }

    /**
     * 空壳挥动手里那把武器打一下：伤害 = 空壳基础 1 + 武器自身的攻击力加成，
     * 再交给 {@code EnchantmentHelper.modifyDamage} 叠附魔（锋利 V 就走这条路）。
     * <p>
     * 不用 {@code Mob#doHurtTarget}：它读的是 {@code ATTACK_DAMAGE} 属性，而那是<b>装备更新后</b>
     * 才把手里武器的加成算进去的 —— 我们是在同一刻换武器再打，属性还停留在上一把上。
     * <p>
     * 最后还会调一次 {@code Item#hurtEnemy}：几把近战武器的特殊效果都写在那里
     * （暮色：赤铁剑点火、寒冰剑挂霜、玻璃剑碎掉；天境：重力晶剑把人挑飞、疾电长剑落雷、
     * 吸血魔剑回血），不调就不会触发。注意它和原版一样收 {@code LivingEntity} 攻击者，
     * 所以空壳这种非玩家攻击者也能正常触发。
     * <p>
     * 包内可见：暮色联动（{@code TwilightForestCompat}）与天境联动（{@code AetherCompat}）共用。
     */
    static void swingWeapon(LivingEntity attacker, LivingEntity target, ItemStack weapon) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        attacker.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        attacker.swing(InteractionHand.MAIN_HAND);

        float damage = 1.0F;   // 空壳按玩家算基础攻击力
        net.minecraft.world.item.component.ItemAttributeModifiers modifiers = weapon.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS,
            net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        for (net.minecraft.world.item.component.ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().value() == net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) {
                damage += (float) entry.modifier().amount();
            }
        }
        var source = level.damageSources().mobAttack(attacker);
        damage = net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(
            level, weapon, target, source, damage);
        if (target.hurt(source, damage)) {
            // 武器的命中特效（见方法注释；原版剑是耐久消耗，对空壳手里的"假物品"无所谓）
            weapon.getItem().hurtEnemy(weapon, target, attacker);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }

    /**
     * 攻击者归属的核心 UUID：附体主空壳看它的附体登记；被<b>同化</b>的空壳没有那个登记，
     * 就退回它身上"我是被哪颗核心同化的"反向记录。
     * <p>
     * 统一走这里，是为了让"同化空壳叫出来的帮手"和"原初空壳叫出来的帮手"算在同一颗核心账上 ——
     * 既共用数量上限，也会在"杀原初一起清场"时被一并收掉。
     */
    @Nullable
    static UUID orbOfAttacker(LivingEntity attacker) {
        if (attacker instanceof PlayerShellEntity shell) {
            return shell.getPossessedOrb() != null
                ? shell.getPossessedOrb()
                : OrbAssimilation.orbOfShell(shell);
        }
        return null;
    }

    /**
     * 把召唤出来的东西登记为"附体召唤物"。
     * <p>
     * 登记后：附体体系的任何索敌都会跳过它（见 {@link OrbPossessionSummons}），
     * 附体结束时也会被一并清场。盟友记的是<b>发射者自己</b>（那具空壳），
     * 召唤物靠它实现共享索敌（{@link OrbSummonTargetGoal}）。
     */
    static void markSummon(LivingEntity attacker, Entity summon) {
        OrbPossessionSummons.mark(summon, orbOfAttacker(attacker), attacker.getUUID());
    }

    /**
     * <b>强制攻击模式</b>（调试用）：由 {@code /jafa luck_attack <模式>} 设置，对所有被附体空壳生效。
     *
     * <h2>为什么要有它</h2>
     * 原来只有 {@code debug.debug_mode} 一个总开关，打开之后空壳"永远用重锤"（见 {@link #drawUsable}），
     * 于是想验证别的攻击手段就必须把 debug 关掉、然后靠随机抽签碰运气。用户要求把这些行为
     * <b>拆成一条命令</b>，在 debug 关闭时也能单独钉死某一种攻击：
     * <ul>
     *   <li>{@link #MACE}：近战一律用重锤；</li>
     *   <li>{@link #LAVA_CAGE}：只用岩浆牢笼 —— 不再抽近战/远程，也不看目标是不是玩家；</li>
     *   <li>{@link #FIREWORK}：远程一律用爆炸性烟花火箭；</li>
     *   <li>{@link #FLY}：第一次远程抽签就给自己翅膀（之后就自然会飞，本模式随之下岗）；</li>
     *   <li>{@link #GUARDIAN_BEAM}：远程一律用守卫者激光。</li>
     * </ul>
     * 命令行的模式<b>优先于</b> debug 模式那条"永远重锤"，所以想回 debug 的行为就 {@code clear}。
     */
    public enum ForcedAttack {
        /** 不强制：完全走正常抽签。 */
        NONE("none", "不强制（正常抽签）"),
        /** 近战一律重锤。 */
        MACE("mace", "近战一律用重锤"),
        /** 只用岩浆牢笼（不看目标是不是玩家，也不抽近战/远程）。 */
        LAVA_CAGE("lava_cage", "只用岩浆牢笼（不看目标是否为玩家）"),
        /** 远程一律爆炸性烟花火箭。 */
        FIREWORK("firework", "远程一律用爆炸性烟花火箭"),
        /** 第一次远程抽签就给翅膀。 */
        FLY("fly", "第一次远程抽签就给自己翅膀"),
        /** 远程一律守卫者激光。 */
        GUARDIAN_BEAM("guardian_beam", "远程一律用守卫者激光"),
        /**
         * <b>不抽召唤/放置类的签</b>（调试用，用户指定）：其余照常抽签。
         * <p>
         * 挡的是"<b>会留下一直存在的东西</b>"的那几张 —— 召唤凋灵、召唤 Bob、召唤骷髅马骑士、
         * 铁砧雨、岩浆牢笼（以及装了暮色时的"僵尸权杖 → 忠诚僵尸"）。
         * 判据见 {@link PossessedAttackEvent#summons()}：会自己炸掉、不留东西的那五张
         * "飞行炸弹"（苦力怕/猫/僵尸/史莱姆/骷髅）<b>不算</b>，所以它们照常会抽到。
         */
        NO_SUMMONS("nosummons", "不抽取召唤/放置类攻击（其余照常抽签）");

        private final String commandName;
        private final String description;

        ForcedAttack(String commandName, String description) {
            this.commandName = commandName;
            this.description = description;
        }

        /** 命令里用的名字（{@code /jafa luck_attack <这个名字>}）。 */
        public String commandName() {
            return this.commandName;
        }

        /** 给玩家看的一句说明。 */
        public String description() {
            return this.description;
        }

        /** 解析命令名；{@code clear}/{@code none}（不分大小写）都算取消。未知名字返回 null。 */
        @Nullable
        public static ForcedAttack parse(String name) {
            if ("clear".equalsIgnoreCase(name)) {
                return NONE;
            }
            for (ForcedAttack mode : values()) {
                if (mode.commandName.equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            return null;
        }
    }

    /**
     * 当前强制模式（{@code volatile}：命令在主线程改，空壳 tick 也读，保证可见性即可）。
     * <p>
     * <b>不落盘</b>：它是个调试开关，重启游戏就回到 {@link ForcedAttack#NONE}。
     */
    private static volatile ForcedAttack forcedAttack = ForcedAttack.NONE;

    /** 设置强制模式（见 {@link ForcedAttack}）；传 null 等于取消。 */
    public static void setForcedAttack(@Nullable ForcedAttack mode) {
        forcedAttack = mode == null ? ForcedAttack.NONE : mode;
    }

    /** 当前强制模式。 */
    public static ForcedAttack getForcedAttack() {
        return forcedAttack;
    }

    /** 一个附体攻击事件。 */
    public interface PossessedAttackEvent {

        /** 真正执行这次攻击。 */
        void run(LivingEntity attacker, LivingEntity target);

        /**
         * 这个事件此刻能不能兑现（距离/射程等条件）。默认任何时候都能。
         * <p>
         * 只管"够不够得着"，冷却判断交给 {@link #cooldownTicks()}。
         */
        default boolean canRun(LivingEntity attacker, LivingEntity target) {
            return true;
        }

        /**
         * 这次抽到本事件时，攻击者要不要<b>停下走位</b>？默认不要（照常走位）。
         * <p>
         * {@link MeleeEvent} 用它摆"近战架势"：目标已经在近战距离内就站住出刀，
         * 否则返回 false 继续骷髅式走位（见 {@link OrbPossessionKiteGoal}）。
         */
        default boolean shouldPauseMovement(LivingEntity attacker, LivingEntity target) {
            return false;
        }

        /** 两次兑现之间至少要隔多少刻。默认 0（不额外限制，只由抽签间隔兜底）。 */
        default int cooldownTicks() {
            return 0;
        }

        /**
         * 冷却（刻），<b>按当前局面动态决定</b>：默认就是 {@link #cooldownTicks()}。
         * <p>
         * 少数特攻需要"窗口稍纵即逝时不要干等"（例如缓降药水：目标带着重锤滞空的那十几刻
         * 才是它真正该出手的时候，按地面那套 5 秒节奏等就只能等目标落地之后才丢得出来）。
         * 空壳真正读取的是这个方法（见 {@code PlayerShellEntity#isAttackReady}），
         * 目标取空壳当前的索敌目标。
         */
        default int cooldownTicks(LivingEntity attacker, @Nullable LivingEntity target) {
            return this.cooldownTicks();
        }

        /**
         * 起手（蓄力）刻数：拉弓、举三叉戟这类动作需要几刻才看得清。默认 0 = 抽到就立刻出手。
         * <p>
         * 大于 0 时，空壳会在抽到本事件的那一刻调用 {@link #beginWindUp}，
         * 等这么多刻之后再调 {@link #run}，然后调 {@link #endWindUp} 收尾
         * （目标跑出范围或冷却没好时只收尾、不出手）。
         * <p>
         * <b>注意</b>：{@link #beginWindUp} 只在起手刻数 &gt; 0 时才会被调用 ——
         * 想在手里亮出武器就必须给它一个非零值，否则"拿武器"那段是死代码。
         */
        default int windUpTicks() {
            return 0;
        }

        /**
         * 起手刻数（按攻击者动态决定）。默认就用 {@link #windUpTicks()}。
         * <p>
         * 少数特攻需要"看状态决定要不要起手"（例如变形解药：平时 0.5 秒起手，
         * 但空壳<b>刚被解除变形</b>时零帧出手），所以空壳真正读取的是这个方法。
         */
        default int windUpTicks(LivingEntity attacker) {
            return this.windUpTicks();
        }

        /**
         * 开始起手：拿出武器、进入蓄力姿态。
         * <p>
         * 两手在抽签时已经被空壳清空，所以这里只需摆自己需要的东西。
         * 需要"拉弓/举矛"动画的物品要在这里调 {@code attacker.startUsingItem(MAIN_HAND)} ——
         * 客户端就是靠 {@code isUsingItem()} + {@code getUseItemRemainingTicks() > 0}
         * 决定手臂姿态的（见 {@code PlayerShellRenderer#getArmPose}）。
         */
        default void beginWindUp(LivingEntity attacker) {
        }

        /** 结束起手：收武器、退出蓄力姿态。出手成功或起手被打断都会调用。 */
        default void endWindUp(LivingEntity attacker) {
        }

        /**
         * 是不是"最高优先级特攻"（目前有丢奶桶解毒、丢缓降药水两张签）。
         * <p>
         * 抽签本身是每 {@code 20~40} 刻才抽一次（抽到就抽到，中途不再改主意），
         * 所以"抽签里排在第一"并不等于"立刻就会用"：目标若是刚举起重锤、而空壳手里
         * 恰好已经攥着一张别的签（甚至在拉弓），那它照样会先把那一招打完。
         * <p>
         * 返回 true 的事件会被空壳在 {@code tick()} 里额外盯一眼：只要它条件成立且冷却已好，
         * 就当场作废手里那张签、立刻重抽（见 {@code PlayerShellEntity#tick()} 的抽签分支）。
         * 注意判断时必须跳过"手里已经就是这张签"的情况，否则起手会被每刻重置、永远出不了手。
         */
        default boolean highestPriority() {
            return false;
        }

        /**
         * 这次攻击会不会<b>留下会一直存在的东西</b>（召出一只实体、或放下方块）？
         * <p>
         * 只被 {@code /jafa luck_attack nosummons}（{@link ForcedAttack#NO_SUMMONS}）使用：
         * 那个模式下这类签<b>根本不会被抽到</b>，方便调试"场上不出现任何召唤物"时的攻击流程。
         * <p>
         * <b>判据是"永久"</b>：召唤物自己会炸掉、不留任何东西的那五张"飞行炸弹"
         * （爆炸性苦力怕/猫/僵尸/史莱姆/骷髅）<b>不算</b>召唤签，默认返回 false。
         */
        default boolean summons() {
            return false;
        }

        /**
         * 抽签权重：默认 1（同池子里等概率）。
         * <p>
         * 四个档位：
         * <ul>
         *   <li>{@code 1.0}（默认）—— 普通攻击；</li>
         *   <li>{@link #EXPLOSIVE_ENTITY_WEIGHT}（1/3 ≈ 0.333）—— <b>爆炸性实体</b>：爆炸性苦力怕弹、
         *       爆裂猫/僵尸/史莱姆/骷髅（用户指定"权重为其它攻击的 1/3"）；</li>
         *   <li>{@link #SUMMON_WEIGHT}（0.2）—— <b>召唤类</b>：Bob、己方凋灵、骷髅马骑士
         *       （用户指定"权重为原来的 1/5"）；</li>
         *   <li>{@link #LOW_WEIGHT}（0.1）—— 低概率签：{@link #RANGED_FLIGHT 飞行}、
         *       {@link #MELEE_MACE 重锤}（用户要求的"权重为其他行为的 1/10"）。</li>
         * </ul>
         * 池子里 N 张普通签 + 1 张低概率签时，它的概率是 {@code 0.1 / (N + 0.1)}。
         */
        default float weight() {
            return 1.0F;
        }
    }

    /**
     * 低概率签的权重（0.1）= 普通签（1.0）的 1/10。
     * <p>
     * 见 {@link PossessedAttackEvent#weight()}：抽签按权重加权，不是等概率。
     */
    static final float LOW_WEIGHT = 0.1F;

    /**
     * <b>爆炸性实体</b>签的权重（{@code 1.0 / 3} ≈ 0.333）= 普通签（1.0）的 <b>1/3</b>（用户指定）。
     * <p>
     * 五张：{@link CreeperEvent 爆炸性苦力怕弹}、{@link ExplosiveCatEvent 爆裂猫}、
     * {@link ExplosiveZombieEvent 爆裂僵尸}、{@link ExplosiveSlimeEvent 爆裂史莱姆}、
     * {@link ExplosiveSkeletonEvent 爆裂骷髅} —— 也就是"飞行炸弹"那一族
     * （打出来的是 {@code OrbExplosive*} 派生生物，自己会炸掉、不留东西，
     * 所以不属于 {@link PossessedAttackEvent#summons()}）。
     * <p>
     * 原来这五张里只有苦力怕弹被压过权重（挂在 {@link #SUMMON_WEIGHT} = 0.2 下），
     * 另外四张一直是默认 1.0；这次统一按用户要求改成 1/3（苦力怕弹的 0.2 → 0.333 是略微上调）。
     * <p>
     * 注意这是<b>加权抽签</b>（见 {@link #drawFrom}）：把这几张从 1.0 降到 1/3 之后，
     * 省下来的概率会按比例分给同池子里其余仍是 1.0 的签 —— "空壳出手的频率"不变，
     * 变的只是"这次出手抽到哪一张"。
     */
    static final float EXPLOSIVE_ENTITY_WEIGHT = 1.0F / 3.0F;

    /**
     * <b>爆炸性苦力怕弹</b>单张的权重 = 其它攻击的 <b>1/9</b>（用户指定，6.7.6 更正）。
     * <p>
     * 它是五张"爆炸性实体"签里最强的一张（爆炸威力 3 = 原版苦力怕，另外四张是 1~2），
     * 所以单独再压一档；其余四张（爆裂猫/僵尸/史莱姆/骷髅）仍是
     * {@link #EXPLOSIVE_ENTITY_WEIGHT} = 1/3。
     */
    static final float CREEPER_BOMB_WEIGHT = 1.0F / 9.0F;

    /**
     * <b>召唤类</b>签的权重（0.2）= 普通签（1.0）的 <b>1/5</b>（用户指定）。
     * <p>
     * 目前是三张：{@link BobSummonEvent 召唤 Bob}、{@link WitherSummonEvent 召唤己方凋灵}、
     * {@link SkeletonKnightEvent 召唤骷髅马骑士}。
     * 用户的原话是"改为原来的 1/5"，而这几张原来的权重<b>都是默认的 1.0</b>，
     * 所以这里直接就是 {@code 1.0 / 5 = 0.2}。
     * <p>
     * （原来的第四张"爆炸性苦力怕"已经改挂 {@link #EXPLOSIVE_ENTITY_WEIGHT} = 1/3 ——
     * 它属于"爆炸性实体"那一档，不再算召唤类。）
     * <p>
     * 注意这是<b>加权抽签</b>（见 {@code drawFrom}）：把这几张从 1.0 降到 0.2 之后，
     * 省下来的概率会等比例分给同池子里其余那些仍是 1.0 的签，池子总权重随之变小 ——
     * "空壳出手的频率"不变，变的只是"这次出手抽到的是哪一张"。
     * <p>
     * 与 {@link #LOW_WEIGHT}（0.1）的区别：0.2 是"召唤类"，0.1 是"低概率特攻"，两者互不相干
     * （低概率特攻现有的 0.1 保持不变；飞行/重锤也没出现在用户那次点名的那几张里）。
     */
    static final float SUMMON_WEIGHT = 0.2F;

    /**
     * <b>"其它攻击"</b>签的权重（0.25）= 普通签（1.0）的 <b>1/4</b>（用户指定）。
     * <p>
     * 目前只有一张：{@link LavaCageEvent 岩浆牢笼}（仅针对玩家）。
     * 用户原话是"抽取权重是其它一般攻击的 1/4"，而"一般攻击"的权重就是默认的 {@code 1.0}，
     * 所以这里直接写 {@code 1.0 / 4 = 0.25}。
     * <p>
     * 注意这是<b>加权抽签</b>（见 {@link #drawFrom}）：池子里 16 张 1.0 的签 + 它一张 0.25，
     * 它的实际概率是 {@code 0.25 / 16.25 ≈ 1.5%}；而且它是"仅针对玩家"的，
     * 目标不是玩家时 {@link LavaCageEvent#canRun} 直接把它筛掉，连那 0.25 都不占。
     */
    static final float LAVA_CAGE_WEIGHT = 0.25F;

    /**
     * <b>铁抓钩</b>签的权重 = 其它近战签（1.0）的 <b>1/3</b>（用户指定）。
     * <p>
     * "其它近战"的权重都是默认的 {@code 1.0}，所以直接写 {@code 1.0 / 3}。
     * 注意它平时<b>根本不进候选</b>（{@link HookPullEvent#canRun} 那三条触发条件全中才有它），
     * 所以这个权重只在"能钩的时候"参与分摊 —— 那时近战池里通常也只有它一张可用。
     */
    static final float HOOK_WEIGHT = 1.0F / 3.0F;

    /**
     * 远程事件（普通弓）：<b>右手弓</b>，拉弓 {@link #WIND_UP_TICKS} 刻后<b>连射 3~5 支普通箭</b>
     * （每发间隔 1~2 游戏刻，见 {@link OrbBurstShots}）。
     * <p>
     * 单发速度 60 格/秒 = 3 格/刻，与原版满蓄力弓一致（用户要求"箭的飞行速度不变"）；
     * 不关重力（16 格外只下坠约 0.7 格）。
     * 拉弓动画与 {@link PrismarineBowEvent} 同款：{@code startUsingItem(MAIN_HAND)} +
     * 原版弓的 {@code UseAnim.BOW} → 手臂摆成 {@code BOW_AND_ARROW}。
     * <p>
     * 副手<b>不</b>拿箭：箭是技能自己生成的，不需要弹药，也就没必要在左手摆一支
     * （要"左手海晶箭"的观感请用 {@link PrismarineBowEvent}）。
     */
    private static final class ArrowEvent implements PossessedAttackEvent {

        /** 起手（拉弓）刻数：约 0.6 秒，够看清拉弓动作。 */
        private static final int WIND_UP_TICKS = 12;

        /** 出手间隔（刻）：1.5 秒（一梭子 3~5 发本身还要几刻，这个冷却从"起手"开始算）。 */
        private static final int COOLDOWN_TICKS = 30;

        /** 力量附魔等级：V = 5。 */
        private static final int POWER_LEVEL = 5;

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            // 弓带力量 V：拉弓时就能看到附魔光效
            attacker.setItemInHand(InteractionHand.MAIN_HAND,
                powerBow(attacker.level(), POWER_LEVEL));
            attacker.startUsingItem(InteractionHand.MAIN_HAND);
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            // 连射 3~5 支（间隔 1~2 刻）：第一发当场打，其余交给连发调度器
            OrbBurstShots.start(level, attacker, target, OrbBurstShots.Kind.ARROW);
        }

        @Override
        public void endWindUp(LivingEntity attacker) {
            // 只退出拉弓状态，弓留在手里（和玩家射完一箭一样）。
            // 注意用 stopUsingItem 而不是 releaseUsingItem：后者会让弓真的自己射一箭。
            attacker.stopUsingItem();
        }
    }

    /**
     * 远程事件（海晶弓 + 海晶箭）：<b>右手海晶弓、左手海晶箭</b>，拉弓 {@link #WIND_UP_TICKS} 刻后
     * 射出一枚 {@link PrismarineArrow}（基础伤害 8）。
     * <p>
     * 拉弓动画靠 {@code startUsingItem(MAIN_HAND)} 驱动：客户端手臂姿态由
     * {@code UseAnim.BOW} 判定为 {@code BOW_AND_ARROW}，即"主手拉弓、副手搭箭"，
     * 与真实玩家拉弓完全同款（见 {@code PlayerShellRenderer}）。出手速度按原版满蓄力
     * （3 格/刻 = 60 格/秒）并带暴击标记，等同于玩家拉满弓射出的那一箭。
     */
    private static final class PrismarineBowEvent implements PossessedAttackEvent {

        /** 起手（拉弓）刻数：约 0.6 秒，够看清拉弓动作。 */
        private static final int WIND_UP_TICKS = 12;

        /** 出手间隔（刻）：1.5 秒。 */
        private static final int COOLDOWN_TICKS = 30;

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PRISMARINE_BOW.get()));
            attacker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.PRISMARINE_ARROW.get()));
            // 进入"使用中"状态：这既是拉弓动画的来源，也让 ItemInHandLayer 画出搭在弦上的箭
            attacker.startUsingItem(InteractionHand.MAIN_HAND);
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 direction = aimAt(attacker, target);
            if (direction == null) {
                return;
            }
            Vec3 from = attacker.getEyePosition();
            PrismarineArrow arrow = new PrismarineArrow(level, attacker,
                new ItemStack(ModItems.PRISMARINE_ARROW.get()),
                attacker.getMainHandItem());
            arrow.setPos(from.x, from.y - 0.1, from.z);
            // 原版满蓄力：3 格/刻；f >= 1.0 时带暴击
            arrow.shoot(direction.x, direction.y, direction.z, 3.0F, 0.0F);
            arrow.setCritArrow(true);
            level.addFreshEntity(arrow);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.ARROW_SHOOT,
                SoundSource.HOSTILE, 1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + 0.5F);
        }

        @Override
        public void endWindUp(LivingEntity attacker) {
            // 退出拉弓状态（弓留在主手，看着像"握着弓"；箭也不再需要）
            attacker.stopUsingItem();
        }
    }

    /**
     * 远程事件（三叉戟）：<b>主手三叉戟</b>，举矛 {@link #WIND_UP_TICKS} 刻后<b>连扔 3~5 只三叉戟</b>
     * （每发间隔 1~2 游戏刻，见 {@link OrbBurstShots}）。
     * <p>
     * 投掷动画靠 {@code startUsingItem(MAIN_HAND)} 驱动：{@code UseAnim.SPEAR} 被判定为
     * {@code THROW_SPEAR} 姿态（和玩家蓄力投掷三叉戟一样）。起手 12 刻也顺便满足了原版
     * "蓄力满 10 刻才能投出"的设定（{@code TridentItem.releaseUsing} 里的 {@code i >= 10}）。
     * <p>
     * 弹道与玩家投掷一致：单只速度 2.5 格/刻、散布 1.0（原版 {@code shootFromRotation(..., 2.5F, 1.0F)}），
     * 用户要求"三叉戟的飞行速度不变"，所以连发只改数量与节奏。
     * 三叉戟丢出去之后主手就空了，下次抽到本事件会重新拿一把。
     */
    private static final class TridentEvent implements PossessedAttackEvent {

        /** 起手（蓄力）刻数：20 刻（1 秒）—— 比原版门槛的 10 刻长一倍，投掷姿势看得清。 */
        private static final int WIND_UP_TICKS = 20;

        /** 出手间隔（刻）：2 秒（一梭子 3~5 只本身还要几刻）。 */
        private static final int COOLDOWN_TICKS = 40;

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TRIDENT));
            attacker.startUsingItem(InteractionHand.MAIN_HAND);
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            // 连扔 3~5 只（间隔 1~2 刻）：第一只当场扔出，其余交给连发调度器
            OrbBurstShots.start(level, attacker, target, OrbBurstShots.Kind.TRIDENT);
        }

        @Override
        public void endWindUp(LivingEntity attacker) {
            attacker.stopUsingItem();
            // 三叉戟已经丢出去了：手里那把清掉，下次抽到再拿新的
            attacker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    /**
     * 远程事件（投掷西瓜刀）：<b>把闪烁西瓜刀当三叉戟丢出去</b> —— 这是刀自带的机制
     * （{@code GlisteringMelonKnifeItem#releaseUsing}：蓄力 ≥10 刻、出手速度 2.5、散布 1.0、
     * 播三叉戟投掷音效，{@code UseAnim.SPEAR} 所以姿势就是投掷标枪）。
     * <p>
     * 弹道与玩家投掷完全一致（速度 2.5 格/刻、散布 1.0）；命中伤害由投掷物自己用
     * {@code ModMain#computeKnifeDamage} 结算 —— 与近战同一套公式（白板 10；亡灵先线性追加
     * 亡灵杀手加成、再进 ×20 乘区）。
     * <p>
     * 两个细节：
     * <ul>
     *   <li>投掷物设为 <b>creativeOnly</b>：落地不吐出一把刀实体。空壳手里那把本来就不是
     *       真实物品（凭空掏出来的），不然每丢一次地上就多一把闪烁西瓜刀。</li>
     *   <li>出手后立刻清空主手：投掷物命中时，伤害钩子会检查"投掷者手里是否握着西瓜刀"，
     *       手里还握着就会把亡灵乘区再算一遍（见 {@code ModMain} 里那段判断）。</li>
     * </ul>
     */
    private static final class KnifeThrowEvent implements PossessedAttackEvent {

        /** 起手（蓄力）刻数：20 刻（1 秒）—— 比刀自带的 10 刻门槛长一倍，投掷姿势看得清。 */
        private static final int WIND_UP_TICKS = 20;

        /** 出手间隔（刻）：2 秒（与三叉戟同为投掷类）。 */
        private static final int COOLDOWN_TICKS = 40;

        /** 投掷速度（格/刻），与刀自带的投掷机制一致。 */
        private static final float THROW_SPEED = 2.5F;

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            attacker.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.GLISTERING_MELON_KNIFE.get()));
            // 刀的 UseAnim 就是 SPEAR → 举刀准备投掷的姿态（THROW_SPEAR）
            attacker.startUsingItem(InteractionHand.MAIN_HAND);
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 direction = aimAt(attacker, target);
            if (direction == null) {
                return;
            }
            Vec3 from = attacker.getEyePosition();
            ThrownGlisteringMelonKnife knife = new ThrownGlisteringMelonKnife(level, attacker,
                new ItemStack(ModItems.GLISTERING_MELON_KNIFE.get()));
            knife.setPos(from.x, from.y - 0.1, from.z);
            knife.shoot(direction.x, direction.y, direction.z, THROW_SPEED, 1.0F);
            knife.setCreativeOnly(true);
            level.addFreshEntity(knife);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_THROW.value(),
                SoundSource.HOSTILE, 1.0F, 1.0F);
        }

        @Override
        public void endWindUp(LivingEntity attacker) {
            attacker.stopUsingItem();
            // 刀已经丢出去了：主手清空（也顺便让伤害钩子不再把这把刀算成"手里握着的近战武器"）
            attacker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    /**
     * 远程事件（苦力怕弹）：朝目标射出一只<b>爆炸威力 3、未被闪电充能</b>的特制苦力怕。
     * <p>
     * 用的是 TNT 权杖那只 {@link TntStaffCreeper}（点燃外观、免疫任何伤害、接触即爆），
     * 但走它的<b>直线飞行模式</b>（{@link TntStaffCreeper#launchFlying}）：关掉重力、锁定方向、
     * 每刻加速，撞到方块或生物即引爆 —— 权杖是"抛"（会抛物线落地），这里是"射"。
     * <p>
     * 数值：出膛 {@link #INITIAL_SPEED} 格/秒、每秒加 {@link #ACCELERATION} 格/秒²、
     * 上限与箭矢同为 60 格/秒；按这个加速度 16 格约 0.5 秒飞完，能看清、也来得及躲。
     */
    private static final class CreeperEvent implements PossessedAttackEvent {

        /** 爆炸威力：3 = 原版苦力怕（充能苦力怕才是 6）。 */
        private static final int EXPLOSION_RADIUS = 3;

        /** 出膛速度（格/秒）。 */
        private static final float INITIAL_SPEED = 10.0F;

        /** 每秒加速度（格/秒²）。 */
        private static final float ACCELERATION = 5.0F;

        /** 出手间隔（刻）：3 秒。 */
        private static final int COOLDOWN_TICKS = 60;

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        /** 爆炸性实体签，且是最强的那张：权重 {@link #CREEPER_BOMB_WEIGHT}（其它攻击的 1/9，用户指定）。 */
        @Override
        public float weight() {
            return CREEPER_BOMB_WEIGHT;
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 direction = aimAt(attacker, target);
            if (direction == null) {
                return;
            }
            // 甩臂动作：召唤/投掷类攻击原本什么都不演，看着像"东西凭空冒出来"
            attacker.swing(InteractionHand.MAIN_HAND);
            Vec3 from = attacker.getEyePosition();
            spawn(level, attacker, from, direction);
            // <b>多重射击</b>（用户指定）：带多重射击效果的玩家空壳，发射的爆炸性实体也按同一套公式
            // 额外分裂 2×level 只（它不在 Projectile 那条自动分裂里，只能在这里自己补）。
            for (Vec3 spread : cn.autoforged.joes_addons_for_abmc.ModMain.multishotSpreadDirections(attacker, direction)) {
                spawn(level, attacker, from, spread);
            }
            level.playSound(null, from.x, from.y, from.z, SoundEvents.CREEPER_PRIMED,
                SoundSource.HOSTILE, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        }

        /** 造一只（本体与分裂副本共用；副本只是方向不同）。 */
        private static void spawn(ServerLevel level, LivingEntity attacker, Vec3 from, Vec3 direction) {
            TntStaffCreeper creeper = new TntStaffCreeper(ModEntities.TNT_STAFF_CREEPER.get(), level);
            creeper.setPos(from.x, from.y - 0.1, from.z);
            creeper.setExplosionRadius(EXPLOSION_RADIUS);
            creeper.setPoweredState(false);            // 未被闪电充能（威力因此就是 3，不会翻倍）
            creeper.setOwnerUuid(attacker.getUUID());
            creeper.launchFlying(attacker, direction, INITIAL_SPEED, ACCELERATION);
            creeper.ignite();                          // 点燃外观（每刻脉冲闪烁）
            markSummon(attacker, creeper);
            level.addFreshEntity(creeper);
        }
    }

    /**
     * 远程事件（爆裂猫）：召唤一只<b>直线飞行、匀加速</b>的猫，命中方块或生物后爆出<b>威力 1</b>，
     * 飞满 {@link FlyingBombControl#FLY_MAX_LIFETIME_TICKS} 刻（200 刻 = 10 秒）也一定爆。
     * <p>
     * 三种爆炸性召唤物（猫/僵尸/苦力怕）共用同一个飞行控制，参数也一样：
     * 出膛 {@link #INITIAL_SPEED} 格/秒、每秒加 {@link #ACCELERATION} 格/秒²、上限 60 格/秒。
     * 打出来的东西是 {@link OrbExplosiveCat}（原版猫的派生类）。
     */
    private static final class ExplosiveCatEvent implements PossessedAttackEvent {

        /** 出膛速度（格/秒）。 */
        private static final float INITIAL_SPEED = 10.0F;

        /** 每秒加速度（格/秒²）。 */
        private static final float ACCELERATION = 5.0F;

        /** 出手间隔（刻）：1.5 秒（爆炸威力只有 1，可以比苦力怕勤快些）。 */
        private static final int COOLDOWN_TICKS = 30;

        /** 爆炸性实体签：权重 {@link #EXPLOSIVE_ENTITY_WEIGHT}（其它攻击的 1/3，用户指定）。 */
        @Override
        public float weight() {
            return EXPLOSIVE_ENTITY_WEIGHT;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 direction = aimAt(attacker, target);
            if (direction == null) {
                return;
            }
            // 甩臂动作：把它扔出去（同系列的召唤/投掷攻击都补了这一个动作）
            attacker.swing(InteractionHand.MAIN_HAND);
            Vec3 from = attacker.getEyePosition();
            spawn(level, attacker, from, direction);
            // 多重射击：爆炸性实体不在 Projectile 那条自动分裂里，这里按同一套公式补 2×level 只
            for (Vec3 spread : cn.autoforged.joes_addons_for_abmc.ModMain.multishotSpreadDirections(attacker, direction)) {
                spawn(level, attacker, from, spread);
            }
            level.playSound(null, from.x, from.y, from.z, SoundEvents.CAT_HISS,
                SoundSource.HOSTILE, 1.0F, 1.0F);
        }

        /** 造一只（本体与分裂副本共用；副本只是方向不同）。 */
        private static void spawn(ServerLevel level, LivingEntity attacker, Vec3 from, Vec3 direction) {
            OrbExplosiveCat cat = new OrbExplosiveCat(ModEntities.ORB_EXPLOSIVE_CAT.get(), level);
            cat.setPos(from.x, from.y - 0.1, from.z);
            cat.launchAsBomb(attacker, direction, INITIAL_SPEED, ACCELERATION);
            markSummon(attacker, cat);
            level.addFreshEntity(cat);
        }
    }

    /**
     * 远程事件（爆裂僵尸）：召唤一只<b>直线飞行、匀加速、不会燃烧</b>的僵尸，
     * 命中方块或生物后爆出<b>威力 2</b>，飞满 200 刻也一定爆。
     * <p>
     * 弹道参数与爆裂猫一致，只有威力和外观不同；"不会燃烧"由
     * {@link OrbExplosiveZombie#isSunSensitive()}/{@link OrbExplosiveZombie#fireImmune()} 保证。
     */
    private static final class ExplosiveZombieEvent implements PossessedAttackEvent {

        /** 出膛速度（格/秒）。 */
        private static final float INITIAL_SPEED = 10.0F;

        /** 每秒加速度（格/秒²）。 */
        private static final float ACCELERATION = 5.0F;

        /** 出手间隔（刻）：2 秒（威力 2，比猫略慢）。 */
        private static final int COOLDOWN_TICKS = 40;

        /** 爆炸性实体签：权重 {@link #EXPLOSIVE_ENTITY_WEIGHT}（其它攻击的 1/3，用户指定）。 */
        @Override
        public float weight() {
            return EXPLOSIVE_ENTITY_WEIGHT;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 direction = aimAt(attacker, target);
            if (direction == null) {
                return;
            }
            Vec3 from = attacker.getEyePosition();
            // 甩臂动作：把它扔出去（同系列的召唤/投掷攻击都补了这一个动作）
            attacker.swing(InteractionHand.MAIN_HAND);
            spawn(level, attacker, from, direction);
            // 多重射击：爆炸性实体不在 Projectile 那条自动分裂里，这里按同一套公式补 2×level 只
            for (Vec3 spread : cn.autoforged.joes_addons_for_abmc.ModMain.multishotSpreadDirections(attacker, direction)) {
                spawn(level, attacker, from, spread);
            }
            level.playSound(null, from.x, from.y, from.z, SoundEvents.ZOMBIE_AMBIENT,
                SoundSource.HOSTILE, 1.0F, 0.8F);
        }

        /** 造一只（本体与分裂副本共用；副本只是方向不同）。 */
        private static void spawn(ServerLevel level, LivingEntity attacker, Vec3 from, Vec3 direction) {
            OrbExplosiveZombie zombie = new OrbExplosiveZombie(ModEntities.ORB_EXPLOSIVE_ZOMBIE.get(), level);
            zombie.setPos(from.x, from.y - 0.1, from.z);
            zombie.launchAsBomb(attacker, direction, INITIAL_SPEED, ACCELERATION);
            markSummon(attacker, zombie);
            level.addFreshEntity(zombie);
        }
    }

    /**
     * 远程事件（爆裂史莱姆）：召唤一只<b>直线飞行、匀加速</b>的史莱姆，命中方块或生物后爆出
     * <b>威力 1</b>，飞满 {@link FlyingBombControl#FLY_MAX_LIFETIME_TICKS} 刻（200 刻 = 10 秒）也一定爆。
     * <p>
     * 用户原话："召唤会爆炸的史莱姆，爆炸威力为 1（可以理解为换皮的猫）"——
     * 所以它就是 {@link ExplosiveCatEvent 爆裂猫}的换皮版：弹道参数、冷却、威力完全一致，
     * 只有外观（{@link OrbExplosiveSlime}）与音效不同。
     */
    private static final class ExplosiveSlimeEvent implements PossessedAttackEvent {

        /** 出膛速度（格/秒）。 */
        private static final float INITIAL_SPEED = 10.0F;

        /** 每秒加速度（格/秒²）。 */
        private static final float ACCELERATION = 5.0F;

        /** 出手间隔（刻）：1.5 秒（与爆裂猫一致）。 */
        private static final int COOLDOWN_TICKS = 30;

        /** 爆炸性实体签：权重 {@link #EXPLOSIVE_ENTITY_WEIGHT}（其它攻击的 1/3，用户指定）。 */
        @Override
        public float weight() {
            return EXPLOSIVE_ENTITY_WEIGHT;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 direction = aimAt(attacker, target);
            if (direction == null) {
                return;
            }
            // 甩臂动作：把它扔出去（同系列的召唤/投掷攻击都补了这一个动作）
            attacker.swing(InteractionHand.MAIN_HAND);
            Vec3 from = attacker.getEyePosition();
            spawn(level, attacker, from, direction);
            // 多重射击：爆炸性实体不在 Projectile 那条自动分裂里，这里按同一套公式补 2×level 只
            for (Vec3 spread : cn.autoforged.joes_addons_for_abmc.ModMain.multishotSpreadDirections(attacker, direction)) {
                spawn(level, attacker, from, spread);
            }
            level.playSound(null, from.x, from.y, from.z, SoundEvents.SLIME_ATTACK,
                SoundSource.HOSTILE, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        }

        /** 造一只（本体与分裂副本共用；副本只是方向不同）。 */
        private static void spawn(ServerLevel level, LivingEntity attacker, Vec3 from, Vec3 direction) {
            OrbExplosiveSlime slime = new OrbExplosiveSlime(ModEntities.ORB_EXPLOSIVE_SLIME.get(), level);
            slime.setPos(from.x, from.y - 0.1, from.z);
            slime.launchAsBomb(attacker, direction, INITIAL_SPEED, ACCELERATION);
            markSummon(attacker, slime);
            level.addFreshEntity(slime);
        }
    }

    /**
     * 远程事件（爆裂骷髅）：召唤一只<b>直线飞行、匀加速、不会燃烧</b>的骷髅，命中方块或生物后
     * 爆出<b>威力 2</b>，飞满 200 刻也一定爆。
     * <p>
     * 用户原话："召唤会爆炸的骷髅，爆炸威力为 2（可以理解为换皮的僵尸）"——
     * 弹道参数与 {@link ExplosiveZombieEvent 爆裂僵尸}完全一致，只有外观
     * （{@link OrbExplosiveSkeleton}）与音效不同；"不会燃烧"由
     * {@link OrbExplosiveSkeleton#isSunBurnTick()}/{@link OrbExplosiveSkeleton#fireImmune()} 保证。
     */
    private static final class ExplosiveSkeletonEvent implements PossessedAttackEvent {

        /** 出膛速度（格/秒）。 */
        private static final float INITIAL_SPEED = 10.0F;

        /** 每秒加速度（格/秒²）。 */
        private static final float ACCELERATION = 5.0F;

        /** 出手间隔（刻）：2 秒（威力 2，与爆裂僵尸一致）。 */
        private static final int COOLDOWN_TICKS = 40;

        /** 爆炸性实体签：权重 {@link #EXPLOSIVE_ENTITY_WEIGHT}（其它攻击的 1/3，用户指定）。 */
        @Override
        public float weight() {
            return EXPLOSIVE_ENTITY_WEIGHT;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 direction = aimAt(attacker, target);
            if (direction == null) {
                return;
            }
            Vec3 from = attacker.getEyePosition();
            // 甩臂动作：把它扔出去（同系列的召唤/投掷攻击都补了这一个动作）
            attacker.swing(InteractionHand.MAIN_HAND);
            spawn(level, attacker, from, direction);
            // 多重射击：爆炸性实体不在 Projectile 那条自动分裂里，这里按同一套公式补 2×level 只
            for (Vec3 spread : cn.autoforged.joes_addons_for_abmc.ModMain.multishotSpreadDirections(attacker, direction)) {
                spawn(level, attacker, from, spread);
            }
            level.playSound(null, from.x, from.y, from.z, SoundEvents.SKELETON_AMBIENT,
                SoundSource.HOSTILE, 1.0F, 0.9F);
        }

        /** 造一只（本体与分裂副本共用；副本只是方向不同）。 */
        private static void spawn(ServerLevel level, LivingEntity attacker, Vec3 from, Vec3 direction) {
            OrbExplosiveSkeleton skeleton =
                new OrbExplosiveSkeleton(ModEntities.ORB_EXPLOSIVE_SKELETON.get(), level);
            skeleton.setPos(from.x, from.y - 0.1, from.z);
            skeleton.launchAsBomb(attacker, direction, INITIAL_SPEED, ACCELERATION);
            markSummon(attacker, skeleton);
            level.addFreshEntity(skeleton);
        }
    }

    /**
     * 远程事件（爆炸性烟花火箭）：一次放出 {@link #MIN_ROCKETS}~{@link #MAX_ROCKETS} 只<b>会爆炸的
     * 烟花火箭</b>，<b>样式与颜色全随机</b>（用户指定）。
     *
     * <h2>为什么用"斜射"模式</h2>
     * {@code FireworkRocketEntity} 有两种飞行：默认模式每刻加速上浮（那是给鞘翅助推用的），
     * {@code shotAtAngle = true} 时<b>保持初速不变、直线飞行</b> —— 我们要的是后者。
     * <p>
     * 火箭的自然寿命是 {@code 10 × (1 + flightDuration) + 0~12} 刻（原版写死，改不了），
     * 所以这里按"目标有多远"反推 {@code flightDuration}，保证它在寿命走完之前能飞到目标那边；
     * 就算中途撞到方块或没打中，寿命一到也会在原地炸开（原版行为）。
     *
     * <h2>伤害从哪来（以及为什么"看起来永远是 5 点"）</h2>
     * 爆炸伤害是原版 {@code FireworkRocketEntity#dealExplosionDamage} 自己算的，一共两个因子：
     * <ol>
     *   <li><b>基础值</b> {@code f = 5 + 2 × 爆炸星数} —— 注意是<b>按星数</b>算的，
     *       一颗星的烟花基础值就是 7。所以这里给每只火箭随机 {@link #MIN_STARS}~{@link #MAX_STARS}
     *       颗星（变值也变强）；</li>
     *   <li><b>距离衰减</b> {@code f × √((5 − d) / 5)}，其中 {@code d} 是爆炸点到目标
     *       <b>{@code Entity#distanceTo}</b> 的距离 —— 那是<b>脚底</b>坐标（{@code position()}），
     *       而火箭是在瞄准高度（眼位，脚底往上约 1.6 格）炸开的。这一格半的竖直差会稳定扣掉三成左右：
     *       {@code √((5−1.6)/5) ≈ 0.82}。于是"1 颗星 → 7 × 0.82 ≈ 5.7"，
     *       看起来就像"伤害永远是 5 点"（用户反馈的现象），而且几乎不随距离变化
     *       —— 因为每次炸开的位置到脚底的高度差基本是同一个值。</li>
     * </ol>
     * 另外要注意：原版 {@code LivingEntity#hurt} 有 10 刻无敌帧，同一次齐射的 2~3 只火箭几乎同时命中，
     * 只有<b>数值最大</b>的那一发会真正结算（其余即数值不大于上一发时被直接忽略）。
     * <p>
     * 它<b>不动地形</b>（那是一段手写的实体伤害循环，压根不走爆炸破坏方块那条路）。
     *
     * <h2>手上的"烟花弩"（用户指定）</h2>
     * 出手前先起手 {@link #WIND_UP_TICKS} 刻：主手换成一把<b>装填着烟花火箭的弩</b>
     * （{@code crossbow} + {@code charged_projectiles = 烟花火箭}），于是
     * <ul>
     *   <li>客户端按原版 {@code charged} / {@code firework} 两个 item 谓词把模型画成
     *       {@code item/crossbow_firework} —— 看上去就是一把"装好烟花的弩"；</li>
     *   <li>手臂姿态由 {@code PlayerShellRenderer#getArmPose} 判成 {@code CROSSBOW_HOLD}
     *       （举弩瞄准，判据照抄原版 {@code PlayerRenderer}）；</li>
     *   <li>出手后把装填清空 —— 弩变回空弩，和玩家射完一发一模一样。</li>
     * </ul>
     * 声音同时换成原版弩的 {@code CROSSBOW_SHOOT}（原版弩射烟花就是这一声）。
     */
    private static final class ExplosiveFireworkEvent implements PossessedAttackEvent {

        /** 一次放几只（最少）。 */
        private static final int MIN_ROCKETS = 2;

        /**
         * 起手（举弩瞄准）刻数：约 0.6 秒，和两张弓的起手一样长，够看清手里那把装好烟花的弩。
         * <p>
         * <b>必须非零</b>：起手为 0 时框架根本不会调 {@code beginWindUp}，
         * 那把弩就没机会出现在手上（见 {@link PossessedAttackEvent#windUpTicks()}）。
         */
        private static final int WIND_UP_TICKS = 12;

        /** 一次放几只（最多）。 */
        private static final int MAX_ROCKETS = 3;

        /**
         * 一只火箭最少几颗爆炸星。
         * <p>
         * 原版基础伤害是 {@code 5 + 2×星数}，而"一颗星 + 脚底距离衰减"只有 5.7 左右，
         * 所以给 2~4 颗（基础 9~13，扣掉衰减后实际约 7~11）——既不再是那个"固定 5 点"，
         * 也让每一发的数值随星数与距离浮动。
         */
        private static final int MIN_STARS = 2;

        /** 一只火箭最多几颗爆炸星（见 {@link #MIN_STARS}）。 */
        private static final int MAX_STARS = 4;

        /** 出手间隔（刻）：3 秒。 */
        private static final int COOLDOWN_TICKS = 60;

        /** 火箭飞行速度（格/刻）：1.0 = 20 格/秒。 */
        private static final double SPEED_PER_TICK = 1.0D;

        /** 多只火箭之间的瞄准抖动（格/刻）：免得两三只完全重叠、看起来只放了一只。 */
        private static final double AIM_JITTER = 0.05D;

        /** 飞行时长档位的上限（原版烟花物品最多 3 档 = 最长寿命 40+12 刻）。 */
        private static final int MAX_FLIGHT_DURATION = 3;

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        /** 起手：把"装好烟花的弩"塞进主手（装填靠组件，见 {@link #chargedFireworkCrossbow()}）。 */
        @Override
        public void beginWindUp(LivingEntity attacker) {
            attacker.setItemInHand(InteractionHand.MAIN_HAND, chargedFireworkCrossbow());
        }

        /**
         * 一把<b>装填着烟花火箭</b>的弩。
         * <p>
         * 原版弩的"装了没有"全看 {@code charged_projectiles} 组件（{@code CrossbowItem#isCharged}
         * 读的就是它），客户端模型又按 {@code charged}/{@code firework} 两个 item 谓词选
         * {@code item/crossbow_firework}，所以只要这一个组件就够"看上去是烟花弩"了。
         */
        private static ItemStack chargedFireworkCrossbow() {
            ItemStack crossbow = new ItemStack(Items.CROSSBOW);
            crossbow.set(DataComponents.CHARGED_PROJECTILES,
                ChargedProjectiles.of(new ItemStack(Items.FIREWORK_ROCKET)));
            return crossbow;
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 direction = aimAt(attacker, target);
            if (direction == null) {
                return;
            }
            double distance = attacker.getEyePosition().distanceTo(target.getEyePosition());
            int count = MIN_ROCKETS + level.getRandom().nextInt(MAX_ROCKETS - MIN_ROCKETS + 1);
            for (int i = 0; i < count; i++) {
                launchOne(level, attacker, direction, distance);
            }
            // 射完就把弩的装填清掉：模型跟着从"装好烟花的弩"变回空弩（原版弩射完就是这个状态）
            ItemStack held = attacker.getMainHandItem();
            if (held.is(Items.CROSSBOW)) {
                held.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
            }
            // 声音是弩的发射声（原版弩射烟花也是这一声，见 CrossbowItem#shoot）
            level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.0F, 1.0F);
        }

        /** 放一只：随机样式/颜色的爆炸烟花，按"够飞到目标"反推飞行时长。 */
        private static void launchOne(ServerLevel level, LivingEntity attacker, Vec3 direction,
                                      double distance) {
            Vec3 from = attacker.getEyePosition();
            // 自然寿命 = 10 × (1 + 飞行时长) + 0~12 刻：取"至少能飞完这段距离"的最小档位
            int neededTicks = (int) Math.ceil(distance / SPEED_PER_TICK);
            int flightDuration = net.minecraft.util.Mth.clamp(
                (int) Math.ceil(neededTicks / 10.0D) - 1, 0, MAX_FLIGHT_DURATION);

            ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
            stack.set(DataComponents.FIREWORKS, new net.minecraft.world.item.component.Fireworks(
                flightDuration, randomExplosions(attacker)));
            // shotAtAngle = true → 直线飞行、速度不变（见类注释）
            net.minecraft.world.entity.projectile.FireworkRocketEntity rocket =
                new net.minecraft.world.entity.projectile.FireworkRocketEntity(
                    level, stack, attacker, from.x, from.y - 0.1, from.z, true);
            // 每只加一点随机抖动，免得两三只叠成一只
            Vec3 aim = direction.add(
                (attacker.getRandom().nextDouble() - 0.5D) * AIM_JITTER,
                (attacker.getRandom().nextDouble() - 0.5D) * AIM_JITTER,
                (attacker.getRandom().nextDouble() - 0.5D) * AIM_JITTER).normalize();
            rocket.setDeltaMovement(aim.scale(SPEED_PER_TICK));
            rocket.hasImpulse = true;
            level.addFreshEntity(rocket);
        }

        /**
         * 这一只火箭里的<b>爆炸星</b>：{@link #MIN_STARS}~{@link #MAX_STARS} 颗，每颗样式与颜色都随机。
         * <p>
         * <b>为什么不是一颗</b>：原版基础伤害是 {@code 5 + 2×星数}（{@code dealExplosionDamage} 里
         * 用的就是这份列表的长度），一颗星只有 7 的基础值，扣掉"炸开点离脚底一格半"的距离衰减后
         * 就剩 5.7 —— 那正是用户看到的"伤害固定 5 点"。给 2~4 颗之后基础值 9~13、
         * 实际 7~11，而且每只火箭不一样。
         * <p>
         * 顺带观感也更好：多颗星就是"一次炸开好几朵"，比单朵更像"爆炸性烟花"。
         */
        private static List<net.minecraft.world.item.component.FireworkExplosion> randomExplosions(
                LivingEntity attacker) {
            int stars = MIN_STARS + attacker.getRandom().nextInt(MAX_STARS - MIN_STARS + 1);
            List<net.minecraft.world.item.component.FireworkExplosion> list = new ArrayList<>(stars);
            for (int i = 0; i < stars; i++) {
                list.add(randomExplosion(attacker));
            }
            return list;
        }

        /** 随机样式 + 随机颜色的一颗爆炸星。 */
        private static net.minecraft.world.item.component.FireworkExplosion randomExplosion(
                LivingEntity attacker) {
            var random = attacker.getRandom();
            var shapes = net.minecraft.world.item.component.FireworkExplosion.Shape.values();
            var shape = shapes[random.nextInt(shapes.length)];
            var dyes = net.minecraft.world.item.DyeColor.values();
            it.unimi.dsi.fastutil.ints.IntList colors = it.unimi.dsi.fastutil.ints.IntList.of(
                dyes[random.nextInt(dyes.length)].getFireworkColor());
            // 一半概率带渐变色（"颜色随机"：主色 + 可选淡出色）
            it.unimi.dsi.fastutil.ints.IntList fade = random.nextBoolean()
                ? it.unimi.dsi.fastutil.ints.IntList.of(
                    dyes[random.nextInt(dyes.length)].getFireworkColor())
                : it.unimi.dsi.fastutil.ints.IntList.of();
            return new net.minecraft.world.item.component.FireworkExplosion(shape, colors, fade,
                random.nextBoolean(), random.nextBoolean());
        }
    }

    /**
     * 远程事件（守卫者光波）：打出一条<b>随机颜色</b>的守卫者激光（见 {@link OrbGuardianLaser}）。
     * <p>
     * 用户指定："使用守卫者光波进行攻击（渲染守卫者激光的贴图……但是拥有随机的颜色），
     * 此类光波命中目标会造成 8 点魔法伤害并失去穿透能力，无法穿透方块，
     * 如果命中物体则可摧毁该物体并穿透。"（"物体"= 掉落物，不是方块）
     * <p>
     * 所以实现上就是"一条线 + 一次性判定"（详见 {@link OrbGuardianLaser}）：
     * <b>无法穿透方块</b>（撞上就停在方块表面，方块不动）、<b>碰到掉落物就摧毁并继续穿透</b>、
     * 打中生物造成 8 点魔法伤害后<b>失去穿透能力</b>（激光到此为止）。
     */
    private static final class GuardianBeamEvent implements PossessedAttackEvent {

        /** 出手间隔（刻）：2 秒。 */
        private static final int COOLDOWN_TICKS = 40;
        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 direction = aimAt(attacker, target);
            if (direction == null) {
                return;
            }
            attacker.swing(InteractionHand.MAIN_HAND);
            Vec3 from = attacker.getEyePosition();
            // 判定与视觉都在 OrbGuardianLaser 里做完：世界上不会多出任何实体，
            // 只有一条发给附近玩家的"激光"包（用户要求：参考原版守卫者 / 音符盒权杖那条路）。
            OrbGuardianLaser.fire(level, attacker, direction);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.GUARDIAN_ATTACK,
                SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }

    /**
     * <b>其它攻击（仅针对玩家）：岩浆牢笼</b> —— 把目标玩家当场关进铁栅栏 + 岩浆的结构里
     * （见 {@link OrbLavaCage}，结构来自用户上传的 {@code lava_cage.nbt}）。
     *
     * <h2>它是"其它攻击"这一类里目前唯一的一张签</h2>
     * 用户原话把这一条归在"其它攻击"下，并且规定"抽取权重是其它一般攻击的 1/4"。
     * 所以它进的是 {@link #OTHER} 这个类别（那一类以前是空的占位），
     * 权重给 {@link #LAVA_CAGE_WEIGHT}（0.25 = 普通签 1.0 的 1/4）。
     * <p>
     * 又因为这一招对非玩家目标毫无意义（笼子是照"玩家的位置"铺的），
     * {@link #canRun} 直接限定 {@code target instanceof Player} —— 打生物时它压根不进抽签候选，
     * 不会白占一份概率。
     */
    private static final class LavaCageEvent implements PossessedAttackEvent {

        /** 出手间隔（刻）：15 秒 —— 这一招是一次地形级的"处刑"，不该频繁。 */
        private static final int COOLDOWN_TICKS = 300;

        /**
         * "<b>这一招只会被抽到一次</b>"的持久化标记（用户指定）。
         * <p>
         * 记在<b>空壳自己身上</b>（{@code persistentData}，随实体 NBT 落盘）：一具空壳就是一次附体，
         * 所以效果是"每次附体最多放一次岩浆牢笼"；附体结束这具壳没了，标记也跟着消失，
         * 下次附体又是一具新壳、可以再用一次。
         * <p>
         * 写在 {@code persistentData} 而不是内存字段：空壳被变形药水变形再解药复原时是
         * <b>照 NBT 新造的实体</b>，内存字段会跟着丢，那样"只用一次"就会被变形解除绕过。
         */
        private static final String LAVA_CAGE_USED_TAG = "jafa_lava_cage_used";

        /** 放置签：会在地形上<b>留下</b>岩浆牢笼（方块），{@code /jafa luck_attack nosummons} 下不抽。 */
        @Override
        public boolean summons() {
            return true;
        }

        /** 生效距离（格）：与空壳的索敌范围一致。 */
        private static final double RANGE = 50.0D;

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        /** 权重：一般攻击的 1/4（用户指定）。 */
        @Override
        public float weight() {
            return LAVA_CAGE_WEIGHT;
        }

        /**
         * 能不能兑现这一签。
         * <p>
         * 默认<b>仅针对玩家</b>：目标不是玩家时这张签不可用（也就不会被抽到）。
         * <p>
         * <b>强制模式（{@code /jafa luck_attack lava_cage}）例外</b>：那时不看目标是不是玩家
         * （用户指定"不会管索敌目标是否为玩家"）。
         * <p>
         * ⚠️ 这一条<b>必须写在 {@code canRun} 里</b>，不能只写在 {@link #drawUsable} 的强制分支里：
         * 空壳出手前还会再问一次 {@code canRun}（见 {@code PlayerShellEntity#tick()}：
         * {@code if (drawn.canRun(this, target) && this.isAttackReady(drawn, gameTime))}），
         * 只放宽抽签那一侧的话，抽是抽到了、出手那一刻又被这一道门挡回去 ——
         * 表现就是"只在原地绕圈、什么都不做"（用户实测反馈的正是这个）。
         */
        @Override
        public boolean canRun(LivingEntity attacker, LivingEntity target) {
            // 强制模式（{@code /jafa luck_attack lava_cage}，纯调试）不受"只用一次"限制：
            // 那时玩家就是想反复看这一招。
            if (forcedAttack == ForcedAttack.LAVA_CAGE) {
                return this.canRunIgnoringTargetType(attacker, target);
            }
            // <b>只用一次</b>（用户指定）：这具空壳已经放过牢笼就不再进候选，连权重都不占。
            if (this.alreadyUsed(attacker)) {
                return false;
            }
            return target instanceof Player && this.canRunIgnoringTargetType(attacker, target);
        }

        /** 这具空壳（= 这颗核心的这一次附体）是不是已经放过岩浆牢笼了。 */
        private boolean alreadyUsed(LivingEntity attacker) {
            return attacker.getPersistentData().getBoolean(LAVA_CAGE_USED_TAG);
        }

        /**
         * 不看目标类型的判据：只看距离。
         * <p>
         * 抽签（{@link #drawUsable} 的强制分支）与 {@link #canRun} 都用它，
         * 保证"抽得到"和"出手时再确认一次"用的是同一条距离门槛。
         */
        public boolean canRunIgnoringTargetType(LivingEntity attacker, LivingEntity target) {
            return attacker.distanceToSqr(target) <= RANGE * RANGE;
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            // <b>只用一次</b>：出手这一刻就落标记（见 #canRun），本次附体之后再也抽不到它
            attacker.getPersistentData().putBoolean(LAVA_CAGE_USED_TAG, true);
            cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
                "[附体攻击] 岩浆牢笼出手（本次附体仅此一次）：目标 {}", target.getType().toShortString());
            // 抬手"施法"：这一招也是凭空起结构，补一个甩臂动作
            attacker.swing(InteractionHand.MAIN_HAND);
            OrbLavaCage.place(level, target);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0F, 0.7F);
        }
    }

    /**
     * 远程事件（骷髅马骑士）：召唤一只<b>骑着骷髅马的骷髅</b>协同作战。
     * <p>
     * 配置照搬幸运实体事件的骷髅骑士（{@code LuckySkeletonKnightEvent}）：骷髅马无重力、
     * 地面 AI 全清、由服务器 tick 驱动飞行（不落地、免疫摔落）；骑手自带抗火、手里有弓、
     * 免疫摔落。区别是<b>飞行目标不是随机漫游，而是共享目标</b> ——
     * 骑手挂 {@link OrbSummonTargetGoal} 抄空壳的目标，坐骑则由
     * {@link OrbSkeletonKnightFlight} 朝同一个目标冲过去。
     * <p>
     * 数量上限 {@link #KNIGHT_CAP} 只（按骑手计），冷却 {@link #COOLDOWN_TICKS} 刻（10 秒）——
     * 这是个"活体帮手"，不该和箭矢一个节奏。附体结束时和 Bob 一样会被清场。
     */
    private static final class SkeletonKnightEvent implements PossessedAttackEvent {

        /** 同一颗核心最多在场的骑士数量（按骑手计）。 */
        private static final int KNIGHT_CAP = 5;

        /** 出手间隔（刻）：10 秒。 */
        private static final int COOLDOWN_TICKS = 200;

        /** 召唤半径（格）：出现在空壳身边。 */
        private static final double SPAWN_RADIUS = 3.0;

        /** 召唤签：会<b>留下</b>骷髅马 + 骑士，{@code /jafa luck_attack nosummons} 下不抽。 */
        @Override
        public boolean summons() {
            return true;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        /** 召唤类签：权重压到 {@link #SUMMON_WEIGHT}（普通签的 1/5，用户指定）。 */
        @Override
        public float weight() {
            return SUMMON_WEIGHT;
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            UUID orbUuid = orbOfAttacker(attacker);
            int alive = orbUuid == null ? 0
                : OrbPossessionSummons.countRole(level, orbUuid, OrbPossessionSummons.ROLE_KNIGHT_RIDER);
            if (alive >= KNIGHT_CAP) {
                return;
            }

            // 甩臂动作：叫帮手也得有个动作（原本是站着不动凭空冒出骑士）
            attacker.swing(InteractionHand.MAIN_HAND);
            Vec3 spawn = findSpawnPos(level, attacker);
            SkeletonHorse horse = EntityType.SKELETON_HORSE.create(level);
            if (horse == null) {
                return;
            }
            horse.moveTo(spawn.x, spawn.y, spawn.z, attacker.getRandom().nextFloat() * 360.0F, 0.0F);
            horse.setTamed(true);              // 已驯服：不会把背上的骷髅甩下去
            horse.setPersistenceRequired();
            horse.setNoGravity(true);          // 不落地（飞行由 OrbSkeletonKnightFlight 驱动）
            // 马凯：30% 铁、30% 金、40% 不穿
            equipHorseArmor(horse);
            // 踢掉全部地面 AI：移动完全交给飞行驱动。
            // 注意不能改成 setNoAi(true)：那样连 LivingEntity#travel 的物理推进都会被跳过，马就不动了。
            horse.goalSelector.removeAllGoals(goal -> true);
            horse.targetSelector.removeAllGoals(goal -> true);
            markSummon(attacker, horse);
            OrbPossessionSummons.markRole(horse, OrbPossessionSummons.ROLE_KNIGHT_HORSE);
            level.addFreshEntity(horse);

            Skeleton rider = EntityType.SKELETON.create(level);
            if (rider == null) {
                horse.discard();
                return;
            }
            rider.moveTo(spawn.x, spawn.y, spawn.z, horse.getYRot(), 0.0F);
            rider.setPersistenceRequired();
            rider.fallDistance = 0.0F;
            // 铠甲：40% 全套无附魔铁甲、10% 全套弹射物保护 IV 铁甲、剩下 50% 无甲
            equipRiderArmor(rider, level);
            // 自带抗火：永久抗火（-1 = 无限时长，无粒子），免得白天被晒、顺带免疫火焰伤害
            rider.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
            if (rider.getMainHandItem().isEmpty()) {
                rider.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW)); // 保证它会还击
            }
            // 共享索敌：清掉原版骷髅自己的目标 goal，换成"抄空壳的目标"
            rider.targetSelector.removeAllGoals(goal -> true);
            rider.targetSelector.addGoal(0, new OrbSummonTargetGoal(rider, attacker.getUUID()));
            markSummon(attacker, rider);
            OrbPossessionSummons.markRole(rider, OrbPossessionSummons.ROLE_KNIGHT_RIDER);
            level.addFreshEntity(rider);
            rider.startRiding(horse, true);

            OrbSkeletonKnightFlight.register(level, horse, rider, attacker.getUUID());
            level.playSound(null, spawn.x, spawn.y, spawn.z, SoundEvents.SKELETON_HORSE_AMBIENT,
                SoundSource.HOSTILE, 1.0F, 1.0F);
        }

        /**
         * 骑手的铠甲：<b>40% 全套无附魔铁甲</b>、<b>10% 全套弹射物保护 IV 铁甲</b>、
         * 剩下 <b>50% 不穿甲</b>。
         * <p>
         * 先抽一个 [0,1) 的数：&lt;0.4 无附魔铁甲；[0.4,0.5) 附魔铁甲；≥0.5 裸奔。
         * 掉落率沿用原版（穿在身上的装备每件 8.5% 掉落），没有另设 guaranteed drop。
         * <p>
         * <b>这套装备只属于附体核心召唤的骑士</b>：幸运方块开出来的那个骷髅骑士
         * （{@code LuckySkeletonKnightEvent}）是另一条独立的生成代码，永远不穿甲、马也不配凯。
         */
        private static void equipRiderArmor(LivingEntity rider, ServerLevel level) {
            float roll = rider.getRandom().nextFloat();
            if (roll >= 0.5F) {
                return;
            }
            boolean projectileProtection = roll >= 0.4F;
            var enchantments = level.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            var projProt = enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROJECTILE_PROTECTION);
            equip(rider, EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET), projectileProtection, projProt);
            equip(rider, EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE), projectileProtection, projProt);
            equip(rider, EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS), projectileProtection, projProt);
            equip(rider, EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS), projectileProtection, projProt);
        }

        /** 往某个装备槽放一件铁甲；需要附魔就挂上弹射物保护 IV。 */
        private static void equip(LivingEntity rider, EquipmentSlot slot, ItemStack armor,
                                  boolean enchant, net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
            if (enchant) {
                armor.enchant(enchantment, 4);   // 弹射物保护 IV
            }
            rider.setItemSlot(slot, armor);
        }

        /**
         * 骷髅马的马凯：<b>30% 铁制</b>、<b>30% 金制</b>、剩下 <b>40% 不穿</b>。
         * <p>
         * 1.21 起马凯走 {@link EquipmentSlot#BODY}（鞍是 {@code SADDLE}）。
         * 注意原版 {@code UndeadHorseRenderer}（骷髅马/僵尸马共用）<b>没有</b>挂马凯渲染层，
         * 所以客户端另注册了 {@code OrbSkeletonHorseRenderer} 来把马凯画出来（见 ClientEvents）。
         */
        private static void equipHorseArmor(SkeletonHorse horse) {
            float roll = horse.getRandom().nextFloat();
            if (roll < 0.3F) {
                horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.IRON_HORSE_ARMOR));
            } else if (roll < 0.6F) {
                horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.GOLDEN_HORSE_ARMOR));
            }
        }

        /** 在空壳周围找一个能站人的落点（脚下一格实心、脚与头两格空气）；找不到就用空壳脚下。 */
        private static Vec3 findSpawnPos(ServerLevel level, LivingEntity center) {            for (int attempt = 0; attempt < 12; attempt++) {
                double angle = center.getRandom().nextDouble() * Math.PI * 2.0;
                double dist = 1.5 + center.getRandom().nextDouble() * SPAWN_RADIUS;
                BlockPos pos = BlockPos.containing(
                    center.getX() + Math.cos(angle) * dist,
                    center.getY(),
                    center.getZ() + Math.sin(angle) * dist);
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                    && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
                    return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                }
            }
            return center.position();
        }
    }

    /**
     * 近战事件（召唤 Bob）：一次叫来 {@link #MIN_BOBS}~{@link #MAX_BOBS} 只 Bob 协同作战。
     * <p>
     * 归在<b>近战</b>类别下（和贴身劈砍并列、各 50%）：Bob 是拿着剑上前砍人的帮手，
     * 和"近身作战"是同一件事。
     * <p>
     * Bob 就是幸运实体事件里那只（全套保护4/耐久3/荆棘3 钻石甲 + 击退2锋利5耐久3 钻石剑、
     * 命名"Bob"、20% 小僵尸），配置直接复用 {@code LuckyBobEvent.createBob}。
     * <ul>
     *   <li><b>共享索敌</b>：每只 Bob 都挂 {@link OrbSummonTargetGoal}，直接抄空壳的目标 ——
     *       而空壳的索敌本身已经排除了所有召唤物，所以它们天然集火同一个敌人、不会互相打。
     *       同时会清掉原版僵尸自己的目标 goal，否则它会自己去找玩家/村民，把"共享索敌"覆盖掉。</li>
     *   <li><b>数量上限</b>：同一颗核心在场上的 Bob 最多 {@link #BOB_CAP} 只 ——
     *       否则 20 分钟的附体里每几秒叫一批，能攒出上百只。</li>
     *   <li><b>清场</b>：附体结束时由 {@code OrbOfLuckEntity#retreat} 把它们一并收掉。</li>
     * </ul>
     */
    private static final class BobSummonEvent implements PossessedAttackEvent {

        /** 一次召唤的最少数量。 */
        private static final int MIN_BOBS = 2;

        /** 一次召唤的最多数量。 */
        private static final int MAX_BOBS = 3;

        /** 同一颗核心在场上的 Bob 上限（超过就少叫甚至不叫）。 */
        private static final int BOB_CAP = 10;

        /** 召唤半径（格）：围在空壳身边。 */
        private static final double SPAWN_RADIUS = 3.0;

        /** 出手间隔（刻）：5 秒。召唤是"攒人"，比攻击慢。 */
        private static final int COOLDOWN_TICKS = 100;

        /** 召唤签：会<b>留下</b> Bob（僵尸帮手），{@code /jafa luck_attack nosummons} 下不抽。 */
        @Override
        public boolean summons() {
            return true;
        }

        /**
         * 归在近战类别下，就同样受<b>近战距离</b>约束：目标不在 {@link #MELEE_RANGE} 格内时
         * 这张签不算数，{@link #drawUsable} 会当场重抽成远程手段。
         * <p>
         * 这一条是必须的：`canRun` 默认返回 true，光把它挪进近战类别并不会让它"只在近处出场"——
         * 结果就是隔着十几格也在不停喊 Bob。
         */
        @Override
        public boolean canRun(LivingEntity attacker, LivingEntity target) {
            return attacker.distanceToSqr(target) <= MELEE_RANGE * MELEE_RANGE;
        }

        @Override
        public boolean shouldPauseMovement(LivingEntity attacker, LivingEntity target) {
            // 和劈砍共用同一个"近战架势"：站住喊人（够得着才站，同 canRun）
            return this.canRun(attacker, target);
        }

        /** 召唤类签：权重压到 {@link #SUMMON_WEIGHT}（普通签的 1/5，用户指定）。 */
        @Override
        public float weight() {
            return SUMMON_WEIGHT;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            UUID orbUuid = orbOfAttacker(attacker);
            int alive = orbUuid == null ? 0
                : OrbPossessionSummons.countRole(level, orbUuid, OrbPossessionSummons.ROLE_BOB);
            int want = MIN_BOBS + attacker.getRandom().nextInt(MAX_BOBS - MIN_BOBS + 1);
            int allowed = Math.min(want, Math.max(0, BOB_CAP - alive));
            if (allowed <= 0) {
                return;
            }
            // 甩臂动作：喊人前来助战
            attacker.swing(InteractionHand.MAIN_HAND);

            for (int i = 0; i < allowed; i++) {
                Zombie bob = cn.autoforged.joes_addons_for_abmc.block.LuckyBobEvent.createBob(level);
                if (bob == null) {
                    return;
                }
                BlockPos pos = findSummonPos(level, attacker, attacker.getRandom());
                bob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    attacker.getRandom().nextFloat() * 360.0F, 0.0F);
                // 共享索敌：清掉原版僵尸自己的目标 goal，换成"抄空壳的目标"
                bob.targetSelector.removeAllGoals(goal -> true);
                bob.targetSelector.addGoal(0, new OrbSummonTargetGoal(bob, attacker.getUUID()));
                markSummon(attacker, bob);
                OrbPossessionSummons.markRole(bob, OrbPossessionSummons.ROLE_BOB);
                level.addFreshEntity(bob);
            }
            level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, 1.0F, 0.7F);
        }

        /** 在空壳周围找一个能站人的落点（脚下一格实心、脚与头两格空气）；找不到就用空壳脚下。 */
        private static BlockPos findSummonPos(ServerLevel level, LivingEntity center, net.minecraft.util.RandomSource random) {
            for (int attempt = 0; attempt < 12; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double dist = 1.5 + random.nextDouble() * SPAWN_RADIUS;
                BlockPos pos = BlockPos.containing(
                    center.getX() + Math.cos(angle) * dist,
                    center.getY(),
                    center.getZ() + Math.sin(angle) * dist);
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                    && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
                    return pos;
                }
            }
            return center.blockPosition();
        }
    }

    /**
     * 远程事件（铁砧雨）：在目标头顶连砸 10~15 颗铁砧，每 4 游戏刻一颗。
     * <p>
     * 具体投放规则（颗数、高度 10~15 格、水平围绕碰撞箱 ±1 格）与实现细节都在
     * {@link OrbAnvilRain} 里；这个事件只负责"开一场雨"。
     * <p>
     * 冷却给得比别的远程长（{@link #COOLDOWN_TICKS} 刻 = 5 秒）：一场雨本身就要 40~60 刻，
     * 而且单颗坠落 10~15 格的铁砧按原版公式是 18~28 点伤害，连砸十几颗属于重击。
     */
    private static final class AnvilRainEvent implements PossessedAttackEvent {

        /** 出手间隔（刻）：5 秒。 */
        private static final int COOLDOWN_TICKS = 100;

        /** 召唤签：会召来铁砧雨（落下的铁砧会留在地上），{@code /jafa luck_attack nosummons} 下不抽。 */
        @Override
        public boolean summons() {
            return true;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            // 抬手"施法"：铁砧雨原本也是站着不动就下起铁砧来了
            attacker.swing(InteractionHand.MAIN_HAND);
            OrbAnvilRain.start(level, target);
        }
    }

    /**
     * 远程事件（恶魂火球）：<b>手持烈焰手杖</b>挥杖，朝目标吐一颗<b>原版恶魂火球</b>
     * （{@code LargeFireball}）。
     * <p>
     * 用户要求："将远程攻击的召唤恶魂火球改为手持烈焰手杖召唤恶魂火球" ——
     * 所以这里补了 {@link #WIND_UP_TICKS} 刻起手，起手时把
     * {@code joes_addons_for_abmc:blaze_staff}（烈焰手杖）拿到主手上，
     * 挥杖那一下再出火球（{@code beginWindUp} 只在起手刻数 &gt; 0 时才会被调用）。
     * <p>
     * 和恶魂一样：初速 {@link #SPEED_PER_TICK} 格/刻，之后按原版火球的加速度自己加速；
     * 命中时以威力 {@link #EXPLOSION_POWER}（= 原版恶魂的 1）爆炸。
     * <p>
     * <b>不破坏地形</b>：火球的爆炸是原版 {@code Fireball} 自己造的（走 {@code mobGriefing} 规则），
     * 我们插不进手改参数，所以生成时给它打上标记，由 {@link OrbExplosionPolicy} 在爆炸结算前
     * 清空方块破坏清单 —— 实体伤害照旧，一个方块都不掉，哪怕 {@code mobGriefing} 为 true。
     */
    private static final class GhastFireballEvent implements PossessedAttackEvent {

        /** 出膛速度（格/刻）：0.5 = 10 格/秒。 */
        private static final float SPEED_PER_TICK = 0.5F;

        /** 爆炸威力：1 = 原版恶魂。 */
        private static final int EXPLOSION_POWER = 1;

        /** 起手（举杖）刻数：10 刻（0.5 秒）—— 看得清"掏出烈焰手杖"。 */
        private static final int WIND_UP_TICKS = 10;

        /** 出手间隔（刻）：3 秒。 */
        private static final int COOLDOWN_TICKS = 60;

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            // 手持烈焰手杖（用户指定）：火球是"用手杖放出来的"
            attacker.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.BLAZE_STAFF.get()));
            attacker.swing(InteractionHand.MAIN_HAND);
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 direction = aimAt(attacker, target);
            if (direction == null) {
                return;
            }
            // 挥杖出手：光有起手那一下不够，出手时再甩一次手臂
            attacker.swing(InteractionHand.MAIN_HAND);
            Vec3 from = attacker.getEyePosition();
            net.minecraft.world.entity.projectile.LargeFireball fireball =
                new net.minecraft.world.entity.projectile.LargeFireball(
                    level, attacker, direction.scale(SPEED_PER_TICK), EXPLOSION_POWER);
            fireball.setPos(from.x, from.y - 0.1, from.z);
            OrbExplosionPolicy.markNoTerrainGrief(fireball);
            level.addFreshEntity(fireball);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.GHAST_SHOOT,
                SoundSource.HOSTILE, 1.0F, 1.0F);
        }

        @Override
        public void endWindUp(LivingEntity attacker) {
            // 手杖留在手里（观感上"刚放完一颗火球"），下一次抽签会清空两手
        }
    }

    /**
     * 远程事件（召唤己方凋灵）：叫一只 {@link #MAX_HEALTH} 血的凋灵来帮忙，
     * 但<b>全场所有被召唤的凋灵加起来最多 {@link #WITHER_CAP} 只</b>（用户指定 5，
     * 是"在场总数"而不是"每具空壳各 5 只"）。
     *
     * <h2>为什么它不会打自己人</h2>
     * 原版凋灵的目标 goal 是"见谁打谁"（{@code NearestAttackableTargetGoal<LivingEntity>}），
     * 连幸运核心（{@code Animal}）和附体空壳都会打。这里把它的<b>目标选择器整个清空</b>，
     * 换成 {@link OrbSummonTargetGoal} —— 直接抄召唤者（那具空壳）的目标，
     * 而空壳的索敌本身已经排除了所有召唤物、其它核心与附体空壳，于是：
     * <ul>
     *   <li>它不会去打别的幸运核心及其附体的空壳；</li>
     *   <li>它也不会打我们自己的召唤物（Bob、爆炸弹、骷髅马骑士）；</li>
     *   <li>反过来，"自己这边"（空壳和所有召唤物）的索敌同样跳过它，因为它带着召唤物标记。</li>
     * </ul>
     *
     * <h2>它仍是原版凋灵</h2>
     * 飞行走位、凋灵之首、以及血量掉到一半时的"护甲爆发"都照旧（那一段它会短暂无敌并回血，
     * 结束时以威力 7 爆炸）。想要更温顺的版本就得自己注册一个凋灵子类实体，暂时不做。
     * 另外它是 boss，附近玩家会看到它的 boss 血条 —— 原版没有公开接口关掉它。
     */
    private static final class WitherSummonEvent implements PossessedAttackEvent {

        /** 召唤出来的凋灵血量上限（用户指定 100）。 */
        private static final double MAX_HEALTH = 50.0;

        /**
         * 场上同时存在的己方凋灵上限：<b>全服所有被召唤的凋灵加起来</b>最多这么多只。
         * <p>
         * 注意它<b>不是</b>"每具空壳各 5 只"：几只空壳同时在场时共用这一个名额
         * （用户明确要求：在场所有被召唤的凋灵总数不会超过 5）。
         * 计数走 {@link OrbPossessionSummons#countRole(net.minecraft.server.MinecraftServer, String)}，
         * 它扫全服所有维度、不看核心归属；所以别的壳已经叫满时，这一具就叫不出来了。
         */
        private static final int WITHER_CAP = 5;

        /** 出手间隔（刻）：20 秒 —— 这是叫一个 boss 级帮手，不该频繁。 */
        private static final int COOLDOWN_TICKS = 400;

        /** 生成半径（格）。 */
        private static final double SPAWN_RADIUS = 5.0;

        /** 召唤签：会<b>留下</b>己方凋灵（boss 级帮手），{@code /jafa luck_attack nosummons} 下不抽。 */
        @Override
        public boolean summons() {
            return true;
        }

        /** 凋灵高度（格）：至少要这么多格空气才放得下。 */
        private static final int WITHER_HEIGHT = 4;

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        /** 召唤类签：权重压到 {@link #SUMMON_WEIGHT}（普通签的 1/5，用户指定）。 */
        @Override
        public float weight() {
            return SUMMON_WEIGHT;
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            // 上限是"全场所有被召唤的凋灵总数"，不分核心归属 —— 几只空壳共用这一个名额
            // （见 WITHER_CAP）。所以这里直接问全服总数，不再按自己的核心 UUID 数。
            int alive = OrbPossessionSummons.countRole(level.getServer(), OrbPossessionSummons.ROLE_WITHER);
            if (alive >= WITHER_CAP) {
                return;
            }

            attacker.swing(InteractionHand.MAIN_HAND);
            Vec3 spawn = findWitherSpawn(level, attacker);
            net.minecraft.world.entity.boss.wither.WitherBoss wither =
                EntityType.WITHER.create(level);
            if (wither == null) {
                return;
            }
            wither.moveTo(spawn.x, spawn.y, spawn.z, attacker.getRandom().nextFloat() * 360.0F, 0.0F);
            var maxHealth = wither.getAttribute(
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(MAX_HEALTH);
            }
            wither.setHealth((float) MAX_HEALTH);
            wither.setPersistenceRequired();
            markSummon(attacker, wither);
            OrbPossessionSummons.markRole(wither, OrbPossessionSummons.ROLE_WITHER);
            // 己方改造（清掉原版"见谁打谁"的目标 goal、改挂共享索敌）与副头目标锁定都归 OrbAllyWitherControl 管：
            // 它还会在读档后自动重做一遍 —— 目标 goal 是命令式注册的、不进存档，
            // 读档后凋灵会带着原版目标 goal 复活，主头就会去锁最近的活体（通常就是那具空壳）。
            OrbAllyWitherControl.register(wither, attacker.getUUID());
            // 它的凋灵之首、以及半血时那次"护甲爆发"同样不破坏地形
            // （召唤标记已经能让 OrbExplosionPolicy 认出它的弹射物，这里再显式标一下本体）
            OrbExplosionPolicy.markNoTerrainGrief(wither);
            level.addFreshEntity(wither);
            level.playSound(null, spawn.x, spawn.y, spawn.z, SoundEvents.WITHER_SPAWN,
                SoundSource.HOSTILE, 1.0F, 1.0F);
        }

        /**
         * 找一个装得下凋灵（{@link #WITHER_HEIGHT} 格高）的空中落点；
         * 找不到就退回空的壳上方几格（凋灵会飞，不会摔，但别让它卡在方块里窒息）。
         */
        private static Vec3 findWitherSpawn(ServerLevel level, LivingEntity center) {
            for (int attempt = 0; attempt < 16; attempt++) {
                double angle = center.getRandom().nextDouble() * Math.PI * 2.0;
                double dist = 2.0 + center.getRandom().nextDouble() * SPAWN_RADIUS;
                BlockPos pos = BlockPos.containing(
                    center.getX() + Math.cos(angle) * dist,
                    center.getY() + 2.0 + center.getRandom().nextInt(3),
                    center.getZ() + Math.sin(angle) * dist);
                boolean clear = true;
                for (int dy = 0; dy < WITHER_HEIGHT; dy++) {
                    if (!level.getBlockState(pos.above(dy)).isAir()) {
                        clear = false;
                        break;
                    }
                }
                if (clear) {
                    return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                }
            }
            return center.position().add(0.0, 3.0, 0.0);
        }
    }

    /**
     * 近战事件：<b>手持闪烁西瓜刀</b>贴身劈砍，伤害走刀自己的公式。
     * <p>
     * 目标在 {@link #MELEE_RANGE} 格以内时才会被抽中（够不着会被 {@link #drawUsable} 当场重抽）。
     * 抽中后先摆 {@link #WIND_UP_TICKS} 刻起手（举刀），再劈下去：附带 {@code swing()} 挥臂动作
     * 与玩家重击音效，之后每 {@link #COOLDOWN_TICKS} 刻（1.5 秒）一刀。
     * <p>
     * <b>伤害 = 闪烁西瓜刀公式（满蓄力）</b>。这里只打出刀的<b>白板伤害</b>
     * （{@link GlisteringMelonKnifeItem#BASE_ATTACK_DAMAGE} = 10，满蓄力时公式
     * {@code 白板 × (0.2 + 蓄力² × 0.8)} 正好等于白板），亡灵那一段由 {@code ModMain#applyKnifeDamage}
     * 在伤害事件里补上 —— 那条钩子只要"攻击者主手握西瓜刀"就会触发（不要求是玩家），
     * 而附体空壳正是握着刀在砍，所以走的和玩家挥刀<b>完全同一条结算链</b>：
     * <ul>
     *   <li>普通目标：10 点（不吃护甲以外的额外乘区）；</li>
     *   <li>亡灵（{@code #minecraft:sensitive_to_smite}）：再进 ×20 乘区 → 200 点。</li>
     * </ul>
     * 不在这里调 {@code computeKnifeDamage} 的原因：那个函数算的是<b>最终值</b>，
     * 亡灵目标会被上面那条钩子再乘一次 ×20（就成了 ×400）。
     * <p>
     * 条件只看脚对脚距离，不看视线：调试用，隔着薄墙角也能砍中。
     */
    private static final class MeleeEvent implements PossessedAttackEvent {

        /** 起手（举刀）刻数：约 0.3 秒。 */
        private static final int WIND_UP_TICKS = 6;

        /** 出刀间隔（刻）：1.5 秒。 */
        private static final int COOLDOWN_TICKS = 30;

        @Override
        public boolean canRun(LivingEntity attacker, LivingEntity target) {
            return attacker.distanceToSqr(target) <= MELEE_RANGE * MELEE_RANGE;
        }

        @Override
        public boolean shouldPauseMovement(LivingEntity attacker, LivingEntity target) {
            // 够得着才站住；够不着就继续走位（同一个距离条件，不必再写一遍）
            return this.canRun(attacker, target);
        }

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            // 抽出闪烁西瓜刀；起手结束时留着不收回（"手持西瓜刀"是常态观感）
            attacker.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.GLISTERING_MELON_KNIFE.get()));
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            // 挥臂动作：玩家模型靠 attackTime 播这一下（见 PlayerShellRenderer）
            attacker.swing(InteractionHand.MAIN_HAND);
            // 白板伤害：亡灵乘区由 ModMain#applyKnifeDamage 在伤害事件里按同一套公式补上
            target.hurt(attacker.damageSources().mobAttack(attacker),
                GlisteringMelonKnifeItem.BASE_ATTACK_DAMAGE);
            // 用玩家重击的音效：这具身体的皮就是玩家，听着比怪物咬合声对
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }

    /**
     * 近战事件（<b>穿刺 V 三叉戟</b>）：握着一把附了穿刺 V（5 级）的三叉戟贴身捅。
     * <p>
     * 伤害走原版那一套，不自己发明公式：
     * <ul>
     *   <li>三叉戟本身的 +{@link #TRIDENT_ATTACK_DAMAGE} 攻击力（玩家基础 1 → 合计 9）；</li>
     *   <li>再叠穿刺 V 的附魔加成 —— 穿刺只对<b>水生生物</b>（{@code #minecraft:sensitive_to_impaling}）
     *       生效，每级 +2.5，V 级是 +12.5；对陆上目标则等于白板三叉戟，
     *       所以这一下的实际伤害取决于对手是谁（由 {@code EnchantmentHelper#modifyDamage} 现算，
     *       以后原版改数值会自动跟上）。</li>
     * </ul>
     * 也就是说：打水生生物约 21.5 点，打其它目标约 9 点（未计目标护甲）。
     * <p>
     * 和劈砍一样归在近战类别下，所以同样只在 3 格内才会被兑现（够不着时 {@code drawUsable} 会改抽远程）；
     * 出刀时也会停下走位（"近战架势"）。
     */
    private static final class TridentMeleeEvent implements PossessedAttackEvent {

        /** 三叉戟的近战攻击力加成（原版物品属性 +8）。 */
        private static final float TRIDENT_ATTACK_DAMAGE = 8.0F;

        /** 空壳的基础攻击力：按玩家算（1 点），保证"同样的三叉戟打出同样的伤害"。 */
        private static final float SHELL_BASE_ATTACK_DAMAGE = 1.0F;

        /** 穿刺等级：V = 5 级（用户指定：原来的锋利 X 换成穿刺 V）。 */
        private static final int IMPALING_LEVEL = 5;

        /** 出手间隔（刻）：1.5 秒，与闪烁西瓜刀一致。 */
        private static final int COOLDOWN_TICKS = 30;

        @Override
        public boolean canRun(LivingEntity attacker, LivingEntity target) {
            return attacker.distanceToSqr(target) <= MELEE_RANGE * MELEE_RANGE;
        }

        @Override
        public boolean shouldPauseMovement(LivingEntity attacker, LivingEntity target) {
            return this.canRun(attacker, target);
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            ItemStack trident = new ItemStack(Items.TRIDENT);
            var enchantments = level.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            trident.enchant(
                enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.IMPALING),
                IMPALING_LEVEL);
            attacker.setItemInHand(InteractionHand.MAIN_HAND, trident);

            // 原版挥臂动画：玩家模型靠 attackTime 播这一下（见 PlayerShellRenderer）
            attacker.swing(InteractionHand.MAIN_HAND);

            var source = level.damageSources().mobAttack(attacker);
            float damage = SHELL_BASE_ATTACK_DAMAGE + TRIDENT_ATTACK_DAMAGE;
            damage = net.minecraft.world.item.enchantment.EnchantmentHelper
                .modifyDamage(level, trident, attacker, source, damage);
            target.hurt(source, damage);

            // 与劈砍同一个音效：这具身体的皮就是玩家
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }

    /**
     * 近战事件（<b>铁抓钩</b>）：手持<b>铁抓钩</b>朝玩家甩出铁链，把人<b>拽回近战距离</b>
     * （权重 {@link #HOOK_WEIGHT} = 其它近战签的 1/3，用户指定）。
     *
     * <h2>触发条件（用户指定，三条全中才成立）</h2>
     * <ol>
     *   <li><b>索敌目标是玩家</b> —— 把生物拽过来没有意义，对非玩家目标这一签根本不进候选；</li>
     *   <li><b>AI 处于近战状态</b> —— 对玩家时走位那套"近身 15~20 秒 ↔ 远程 15~20 秒"周期里的
     *       <b>近身阶段</b>（{@link PlayerShellEntity#isEngageMeleePhase()}）。它此刻正想压上来打近战，
     *       所以才会用钩子把人拉回来；<b>已经会飞</b>时走位是"保持距离"，不算近战状态，也不触发；</li>
     *   <li><b>玩家在 5 格之外</b>（{@link #MIN_DISTANCE}）—— 5 格以内本来就够得着，直接出刀更划算；
     *       同时还得在 {@link #RANGE} 格以内、且视线没被方块挡住（挡住了钩子也拉不动）。</li>
     * </ol>
     *
     * <h2>出手之后</h2>
     * {@code run} 把这一整段交给 {@link OrbHookPull}：它每刻把玩家往空壳那边拽，直到
     * "拉到近战距离 / 铁链被方块挡住 / 超时"三种收场之一。被打断（或拉到位）时会立刻让空壳重抽一次近战
     * （见 {@code PlayerShellEntity#requestImmediateMeleeRedraw()}）。
     */
    private static final class HookPullEvent implements PossessedAttackEvent {

        /** 最近触发距离（格）：5 格以内不用钩子。 */
        static final double MIN_DISTANCE = 5.0D;

        /**
         * 最远触发距离（格）。
         * <p>
         * 铁抓钩这件物品自己的射程是 64 格，但空壳是<b>在近身阶段追人时</b>用它（见类注释），
         * 二十几格以外它本来就在"直冲过去"的路上，没必要隔半张地图拉人；
         * 铁链的视觉也是有限的，太长反而像一根横贯战场的线。
         */
        static final double RANGE = 24.0D;

        /** 起手（举钩）刻数：0.4 秒 —— 看得清"掏出铁抓钩、甩出去"这一下。 */
        private static final int WIND_UP_TICKS = 8;

        /** 出手间隔（刻）：2 秒（拉扯本身最多也就 2 秒）。 */
        private static final int COOLDOWN_TICKS = 40;

        @Override
        public float weight() {
            return HOOK_WEIGHT;
        }

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public boolean canRun(LivingEntity attacker, LivingEntity target) {
            if (!(attacker instanceof PlayerShellEntity shell) || !(target instanceof Player)) {
                return false;
            }
            // 会飞之后走位换成"保持距离"，不算近战状态（那边 isEngageMelee 的值是上一次地面走位留下的）
            if (shell.isFlightMode()) {
                return false;
            }
            // AI 处于近战状态（对玩家的"近身阶段"）
            if (!shell.isEngageMeleePhase()) {
                return false;
            }
            // 还在冷却里就不算"可用"：远处的近战池里只有这一张签，若抽到它却因冷却出不了手，
            // 空壳就会举着铁抓钩站在原地干等（近战架势会把走位也停掉）。冷却不算可用之后，
            // 这一轮会自然落到远程池去，冷却一好下一轮又能抽到它。
            if (!shell.isAttackReady(this, shell.level().getGameTime())) {
                return false;
            }
            double distSqr = attacker.distanceToSqr(target);
            if (distSqr <= MIN_DISTANCE * MIN_DISTANCE || distSqr > RANGE * RANGE) {
                return false;
            }
            // 视线被方块挡住：钩子甩过去也拉不动，这一签此刻不可用（起手都不起）
            return shell.shouldSeePossessionTarget(target);
        }

        /** 甩钩、收链期间站定不走位（近战架势）。 */
        @Override
        public boolean shouldPauseMovement(LivingEntity attacker, LivingEntity target) {
            return true;
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            // 起手就把铁抓钩掏出来：这一招的观感就是"手持铁抓钩"
            attacker.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.IRON_HOOK.get()));
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker instanceof PlayerShellEntity shell)
                || !(attacker.level() instanceof ServerLevel level)
                || !(target instanceof Player player)) {
                return;
            }
            attacker.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.IRON_HOOK.get()));
            // 甩臂动作 + 铁链声（与玩家自己用铁抓钩同一个音效）
            attacker.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.0F, 0.9F);
            OrbHookPull.start(shell, player);
        }

        @Override
        public void endWindUp(LivingEntity attacker) {
            // 铁抓钩留在手里（拉扯期间也是"手持铁抓钩"）
        }
    }

    // ====================== 低概率签①：飞行 ======================

    /**
     * 远程低概率特攻：<b>飞行</b>（权重 {@link #LOW_WEIGHT} = 其它行为的 1/10）。
     *
     * <h2>效果</h2>
     * <ol>
     *   <li>起手时把 {@code joes_addons_for_abmc:wings} 穿到<b>胸甲栏</b>
     *       —— 客户端 {@code WingsLayer} 就会在背上画出来，并按"是否离地"正弦扇动；</li>
     *   <li>出手时给空壳"飞行能力"（{@link PlayerShellEntity#startFlight()}：无重力 + 抬升一下），
     *       之后走位交给 {@link OrbPossessionFlightAI}（离地 4~5 格、与目标水平距离 7.5~15 格）；</li>
     *   <li><b>从此只会抽远程</b>：{@link #drawUsable} 里对"会飞"的空壳把近战签压掉，
     *       只有目标贴到 {@link #FLIGHT_MELEE_RANGE} 格以内才重新允许近战。</li>
     * </ol>
     * 一旦会飞就一直保持（{@link #canRun} 之后永远返回 false，这张签不会再被抽到）。
     * <p>
     * debug 模式（{@code debug.debug_mode}）下，本次附体的<b>第一张远程签必定是这张</b>，
     * 方便立刻验证（见 {@link #drawUsable} 与 {@code PlayerShellEntity#consumeDebugFirstRangedFlight()}）。
     */
    private static final class FlightEvent implements PossessedAttackEvent {

        /** 起手（展开翅膀）刻数：0.6 秒 —— 够看清"背上长出翅膀"这一下。 */
        private static final int WIND_UP_TICKS = 12;

        /** 出手间隔（刻）：60 秒。基本用不到 —— 会飞之后 {@link #canRun} 就一直是 false 了。 */
        private static final int COOLDOWN_TICKS = 1200;

        @Override
        public float weight() {
            return LOW_WEIGHT;
        }

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public boolean canRun(LivingEntity attacker, LivingEntity target) {
            // 已经会飞就不要再抽到这张签了（"获得飞行能力后就默认只会抽取远程攻击了"）
            return attacker instanceof PlayerShellEntity shell && !shell.isFlightMode();
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            if (!(attacker instanceof PlayerShellEntity shell)) {
                return;
            }
            // 起手就把翅膀穿上：这样"扇动 → 起飞"是连贯的一眼
            shell.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.WINGS.get()));
            shell.level().playSound(null, shell.getX(), shell.getY(), shell.getZ(),
                SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.HOSTILE, 1.0F, 1.1F);
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (attacker instanceof PlayerShellEntity shell) {
                shell.startFlight();
            }
        }

        @Override
        public void endWindUp(LivingEntity attacker) {
            // 起手被打断（目标跑了/中途作废）而这次没真的起飞：把翅膀收起来，
            // 免得留下"背着一副翅膀却不会飞"的穿帮画面。真起飞了就不动它（那是它的飞行装备）。
            if (attacker instanceof PlayerShellEntity shell && !shell.isFlightMode()) {
                shell.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            }
        }
    }

    // ====================== 低概率签②：重锤 ======================

    /**
     * 近战低概率特攻：<b>重锤俯冲</b>（权重 {@link #LOW_WEIGHT} = 其它行为的 1/10）。
     *
     * <p>这张签本身很薄：真正的一整套动作（穿鞘翅爬升 → 俯冲 → 换重锤 → 砸 → 落水自救）
     * 是<b>跨很多刻的状态机</b>，写在 {@link OrbPossessionMaceAttack} 里；
     * 空壳会在 {@link PlayerShellEntity#tick()} 里每刻推进它，期间不抽签、不走位。
     *
     * <p><b>会飞之后永远不抽这张签</b>（用户指定）：飞行形态下压根不该落地去砸人。
     */
    private static final class MaceEvent implements PossessedAttackEvent {

        /** 出手间隔（刻）：10 秒（整套动作本来就要好几百刻，这里只防连抽）。
         *  <p><b>debug 模式下是 0</b>：用户要求"每次近战签都抽重锤"，不给冷却才能每次都抽到
         *  （也免得"空间太矮被取消"之后要等 10 秒才轮到下一次尝试）。
         *  <p><b>强制模式 {@code /jafa luck_attack mace} 下同样是 0</b>：那条命令就是"永远用重锤"的
         *  调试开关，和 debug 模式同一个意图 —— 不给冷却，才能连着看那一整套动作。 */
        private static final int COOLDOWN_TICKS = 200;

        @Override
        public float weight() {
            return LOW_WEIGHT;
        }

        @Override
        public int cooldownTicks() {
            return cn.autoforged.joes_addons_for_abmc.config.ModConfig.DEBUG_MODE.get()
                || forcedAttack == ForcedAttack.MACE
                ? 0 : COOLDOWN_TICKS;
        }

        @Override
        public boolean canRun(LivingEntity attacker, LivingEntity target) {
            return attacker instanceof PlayerShellEntity shell
                && !shell.isFlightMode()          // 会飞之后永远不抽重锤
                && !shell.isMaceSequenceActive(); // 已经在砸的过程中不重复抽
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (attacker instanceof PlayerShellEntity shell) {
                OrbPossessionMaceAttack.begin(shell, target);
            }
        }
    }
}
