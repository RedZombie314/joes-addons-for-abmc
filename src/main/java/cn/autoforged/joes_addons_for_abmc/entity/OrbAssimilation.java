package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <b>同化（Assimilation）</b>：POSSESSING 阶段的幸运核心"杀死"玩家时，不让他死，而是把他变成同类。
 *
 * <h2>规则</h2>
 * <ol>
 *   <li>本核心的空壳、或它召唤出来的一切（Bob / 爆炸性苦力怕·猫·僵尸 / 骷髅马骑士，
 *       以及它们打出的箭矢、三叉戟、西瓜刀）对某名玩家造成<b>致命</b>伤害时，
 *       这次伤害会被抹掉：玩家不死，转成<b>旁观</b>，并在原地留下一具<b>同皮肤</b>、
 *       头顶挂着幸运核心光球的玩家空壳（行为与附体空壳一致：骷髅式走位 + 抽签攻击）。</li>
 *   <li><b>杀死那具空壳 = 复活那名玩家</b>：恢复他"被同化前"的游戏模式，并把他送到空壳所在的位置。</li>
 *   <li><b>杀死"原初"</b>（同化别人的那具空壳及其核心）——也就是附体主空壳被打死（血量归零）、
 *       或原初玩家切回创造模式导致附体结束——会<b>一次性复活所有被同化者</b>。</li>
 *   <li>被同化的玩家自己<b>切回创造模式</b>也能提前解除同化（与附体一个规则）。</li>
 *   <li>期间一律保持旁观：重进存档后也一样（{@link OrbPossessionEvents} 会找到这颗核心并把他按回旁观）。</li>
 * </ol>
 *
 * <h2>状态放在哪</h2>
 * "谁被同化了、空壳是哪一具"记在<b>核心</b>的 {@code assimilatedPlayers} 里（随 NBT 落盘）；
 * 空壳身上另存一份反向记录（属于哪颗核心 + 对应哪名玩家），这样：
 * <ul>
 *   <li>空壳被打死时能立刻找到要复活的人和核心（{@link PlayerShellEntity#die}）；</li>
 *   <li>被同化的空壳再去杀人时，也能追溯到同一颗核心 —— 于是"杀原初复活所有人"始终成立。</li>
 * </ul>
 *
 * <p>注意：被同化的空壳<b>不</b>设 {@code possessedOrbUuid}（那是"我就是那颗核心的附体主空壳"的标记，
 * 打死了会判成"核心被击败"），
 * 它必须是一具能被打死的普通壳体，否则玩家永远复活不了。
 */
@EventBusSubscriber(modid = ModMain.MODID)
public final class OrbAssimilation {

    /** 空壳身上：它属于哪颗核心。 */
    private static final String TAG_ORB = "jafa_assimilated_orb";
    /** 空壳身上：杀死它能复活哪名玩家。 */
    private static final String TAG_PLAYER = "jafa_assimilated_player";

    // ---- 被同化玩家自己身上的记录（权威来源，见 assimilate）----
    /** 玩家身上：他正被同化。 */
    private static final String TAG_ASSUMED = "jafa_assimilated";
    /** 玩家身上：他被同化时留下的那具空壳。 */
    private static final String TAG_MY_SHELL = "jafa_assimilated_shell";
    /** 玩家身上：同化他的那具空壳归属哪颗核心（可能没有）。 */
    private static final String TAG_MY_ORB = "jafa_assimilated_orb_of_shell";

    private OrbAssimilation() {
    }

    // ====================== 伤害/死亡钩子 ======================

    /**
     * <b>临时诊断（无任何过滤）</b>：记录每一次打到玩家的 {@code LivingIncomingDamageEvent}，
     * 只服务于"致命打击计数器为什么从不生效"这件事的定位。
     * <p>
     * 为什么需要一条"什么都不过滤"的：前面几条探针都带条件（来源必须是空壳/能查到归属核心），
     * 于是"事件到底有没有派发到这里"这个最基本的问题反而看不出来。这条<b>只判"是玩家"</b>，
     * 把伤害来源的类型、直接来源、原始伤害全写下来。
     * <p>
     * 注意：它<b>不</b>改变任何行为，纯读。定位完成后会连同其它几条诊断一起删掉。
     */
    @SubscribeEvent
    public static void onDiagnoseIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        Entity owner = event.getSource().getEntity();
        String directName = direct == null ? "无" : direct.getType().toShortString();
        String ownerName = owner == null ? "无" : owner.getType().toShortString();
        // 只关心"和附体空壳有关"的那些，免得被环境伤害刷屏：
        // 直接来源/归属来源是空壳，或者它们就在玩家 40 格内。
        boolean relevant = direct instanceof PlayerShellEntity || owner instanceof PlayerShellEntity;
        if (!relevant) {
            double best = 40.0D * 40.0D;
            for (PlayerShellEntity shell : player.level().getEntitiesOfClass(
                    PlayerShellEntity.class, player.getBoundingBox().inflate(40.0D))) {
                if (player.distanceToSqr(shell) < best) {
                    relevant = true;
                    break;
                }
            }
        }
        if (!relevant) {
            return;
        }
        ModMain.LOGGER.info("[诊断] 玩家受伤事件: 玩家={} 模式={} 原始伤害={} 血量={}/{} direct={} owner={} 类型={}",
            player.getGameProfile().getName(), player.gameMode.getGameModeForPlayer().getName(),
            event.getOriginalAmount(), player.getHealth(), player.getMaxHealth(),
            directName, ownerName, event.getSource().getMsgId());
    }

    /**
     * 主钩子：致命伤害在结算前被抹掉，改为同化。
     * <p>
     * 用 {@link LivingDamageEvent.Pre} 而不是更早的 {@code LivingIncomingDamageEvent}：
     * 这个事件的 {@code getNewDamage()} 已经过护甲/附魔/吸收结算，判断"这一下会不会致死"才准确。
     * <p>
     * 副作用：带不死图腾的玩家也会被同化（伤害被抹掉，图腾不会消耗）。这是"不让他死"的自然结果。
     */
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        PlayerShellEntity attacker = attackerShellOf(event.getSource());
        // <b>记录"哪具壳正在打 Red_Zombie"</b>：SunnySeren 的"护主"规则要用
        // （见 OrbAdversaryRelations）。这里是每一次真正打进来的伤害都会经过的口子，
        // 而且此刻伤害还没被同化/减伤处理掉，"打过了"这个事实是确定的。
        // 传的是<b>直接来源</b>（可能是弹射物/召唤物）：由那边自己顺着召唤物标记找回那具主空壳。
        OrbAdversaryRelations.recordShellAttack(player, event.getSource().getDirectEntity());
        // <b>诊断</b>：这一条打进来就说明"减伤后"的事件链条是通的，且认出了出手的空壳。
        if (attacker != null) {
            ModMain.LOGGER.info("[同化] 收到空壳攻击(减伤后): 玩家={} newDamage={} 原始伤害={} 血量={}/{} 空壳=#{} 来源={}",
                player.getGameProfile().getName(), event.getNewDamage(), event.getOriginalDamage(),
                player.getHealth(), player.getMaxHealth(), attacker.getId(), describeSource(event.getSource()));
        }
        // 先判"这一击会不会致死"：必须在 tickFatalHits 之前算，因为它可能当场触发同化
        // （额度归零那条路）。同化之后玩家变旁观，但血量与这条判定的结果都还没变，
        // 不先算的话下面还会再走一遍"致命→同化"，白跑一趟（assimilate 内部判重，不会出双壳）。
        boolean lethal = event.getNewDamage() >= player.getHealth();
        // ===== 致命打击计数器（必须放在这里，理由见方法注释）=====
        boolean assimilated = tickFatalHits(player, event);
        if (assimilated || !lethal || attacker == null) {
            return;
        }
        event.setNewDamage(0.0F);
        assimilate(player, attacker, player.serverLevel());
    }

    /**
     * 兜底：万一玩家还是死了（绕过伤害事件的直接置血、其它模组强杀等），
     * 取消这次死亡并改走同化流程（{@link LivingDeathEvent} 是可取消事件，取消即"不死"）。
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerShellEntity attacker = attackerShellOf(event.getSource());
        if (attacker == null) {
            return;
        }
        event.setCanceled(true);
        if (player.getHealth() <= 0.0F) {
            player.setHealth(1.0F);   // 既然不死，血量得留一点，否则下一刻照样判定为死
        }
        assimilate(player, attacker, player.serverLevel());
    }

    // ====================== 附体体系的三种攻击附带效果 ======================
    //
    // 下面三个钩子都是"被幸运核心的附体空壳打到时会怎样"。它们互相独立：
    //   ① 近战打中 → 玩家身上"剩余时间 ≥ 10 秒"的药水效果时长减半；
    //   ② 被它的弹射物打中（不含爆炸性实体）→ 10% 概率缴械（主手物品被强行丢出去）；
    //   ③ 单次原始伤害 ≥ 玩家最大生命值 × 85% → 致命打击计数器 -1，归零则同化。

    /** 近战"药水减半"的门槛：剩余时间不低于这么多刻（10 秒）才会被减半。 */
    private static final int MELEE_POTION_MIN_TICKS = 10 * 20;

    /**
     * 近战时真的把药水时长减半的<b>概率</b>（用户指定 25%）。
     * <p>
     * 也就是说"被近身打中"只是<b>有机会</b>触发这个效果，不是必中；
     * 触发之后才去逐个检查身上哪些效果剩余时间 ≥ {@link #MELEE_POTION_MIN_TICKS}。
     */
    private static final float MELEE_POTION_DRAIN_CHANCE = 0.25F;

    /** 弹射物命中时触发缴械的概率（用户指定 10%）。 */
    private static final float PROJECTILE_DISARM_CHANCE = 0.1F;

    /**
     * ① 近战附带效果：被附体空壳<b>近身</b>打中时，有
     * {@link #MELEE_POTION_DRAIN_CHANCE}（25%）的概率把玩家身上剩余时间 ≥
     * {@link #MELEE_POTION_MIN_TICKS}（10 秒）的药水效果<b>时长减半</b>。
     * <p>
     * 判据是"这次伤害的直接来源就是那具空壳本体"（{@code getDirectEntity() instanceof PlayerShellEntity}）
     * —— 也就是徒手/近战武器直击。空壳的近战手段有闪烁西瓜刀、三叉戟、Bob 的拳头（Bob 是召唤物，
     * 打出来的伤害直接来源是 Bob 而不是空壳，所以不算"近身攻击"）。
     * <p>
     * 概率在<b>效果筛选之前</b>掷：没中就整次不生效。
     * 命中之后，剩余时间不足 10 秒的效果<b>不参与减半</b>（用户指定）——
     * 那些本来就是"快没了"的效果，再砍半没有意义。
     * <p>
     * 走 {@link LivingDamageEvent.Post} 而不是 Pre：这是"打中了之后"的附带效果，
     * 不该受"这次伤害会不会被取消/免疫"影响 —— 死了就是死了，效果照减半。
     */
    @SubscribeEvent
    public static void onMeleePotionDrain(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if (event.getNewDamage() <= 0.0F) {
            return;   // 这一下被完全挡下（无敌帧/吸收等）：不算"被打中"
        }
        if (!(event.getSource().getDirectEntity() instanceof PlayerShellEntity shell)) {
            return;
        }
        // 这里走"从空壳查核心"（而不是从伤害来源查）：近战伤害的直接来源就是空壳本体。
        // 判据用"是不是核心产生的空壳"（含刷怪蛋那一具，它背后没有核心实体）。
        if (shellOfEntity(shell) == null) {
            return;
        }
        // 只是"有机会"触发（用户指定 25%），掷骰在效果筛选之前：
        // 没中就整次不生效，不会出现"只减半了一部分效果"的半成品。
        if (player.getRandom().nextFloat() >= MELEE_POTION_DRAIN_CHANCE) {
            return;
        }
        halvePotionDurations(player);
    }

    /**
     * ① <b>会飞的空壳被弹射物击中 → 打落</b>：落地并失去 80~120 刻的飞行能力
     * （用户指定，见 {@link PlayerShellEntity#knockDownFlight}）。
     * <p>
     * 条件是纯粹的"状态"：目标空壳<b>已经会飞</b>（{@link PlayerShellEntity#hasFlightAbility()}），
     * 而这一击的直接来源是个 {@link Projectile}。<b>不限制弹射物是谁射的</b> ——
     * 需求原文讲的是"飞到天上就会被弹射物打下来"这件事本身，所以玩家拿爆炸箭把它射下来同样成立
     * （"包括爆炸箭"那句正是冲着这种玩法说的；爆炸箭是 {@code AbstractArrow} 的子类，
     * 属于 {@code Projectile}，落在条件内）。
     * <p>
     * <b>为什么用 {@link LivingIncomingDamageEvent} 而不是 {@code LivingDamageEvent.Post}</b>：
     * Post 那条路上 {@code getNewDamage()} 是<b>减伤之后</b>的值，而这具空壳自带
     * 20 护甲 + 12 韧性 + 保护 16 —— 一支普通箭打上去经常算出 <b>0</b>，
     * 于是"被击中了、但因护甲没掉血"会被 {@code > 0} 那道判断挡掉，
     * 表现就是"有时能打落、有时打不下来"（用户实测）。
     * 这里的 {@code getOriginalAmount()} 是伤害还没被任何东西削过的原始值，
     * 只要箭真的碰到了它，就一定是正数，不会漏判。
     * <p>
     * 重复命中会重新计时（见 {@link PlayerShellEntity#knockDownFlight}）。
     */
    @SubscribeEvent
    public static void onProjectileKnockDownFlight(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof PlayerShellEntity shell)) {
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof Projectile)) {
            return;   // 近战/爆炸/环境伤害不"打落"
        }
        shell.onPossessionProjectileHit();
    }

    /**
     * ② 弹射物附带效果：被<b>直接来自</b>附体空壳的弹射物打中时，有
     * {@link #PROJECTILE_DISARM_CHANCE}（10%）概率<b>缴械</b> —— 主手物品被强行丢出去。
     * <p>
     * "不计入爆炸性实体"落实为只看 {@code getDirectEntity()} 是不是 {@link Projectile}：
     * <ul>
     *   <li>箭矢 / 海晶箭 / 三叉戟 / 闪烁西瓜刀（{@code ThrowableItemProjectile} 派生）/
     *       恶魂火球 —— 都是 {@code Projectile}，算；</li>
     *   <li>爆炸性召唤物（苦力怕/猫/僵尸）与铁砧雨打出来的是<b>爆炸伤害</b>，
     *       其 {@code getDirectEntity()} 为空，天然落不进这里；</li>
     *   <li>铁砧雨掉的是 {@code FallingBlockEntity}，同样不是 {@code Projectile}。</li>
     * </ul>
     * <p>
     * 缴械出来的物品用原版 {@code Player#drop}：与"按 Q 丢东西"完全同一条路
     * （带 {@code PickupDelay}、掉落物随机初速与自己的动量），所以别人能捡、玩家也能追回来。
     * <p>
     * <b>归属口径</b>：与同化、致命打击计数共用 {@link #orbOfSource} ——
     * 除了空壳自己射出的弹射物，它<b>召唤物</b>射出的也算（骷髅马骑士的箭、恶魂火球……）。
     * 如果只想要"空壳本人射出的"，把这里的判断换成
     * {@code getDirectEntity() instanceof Projectile && getEntity() instanceof PlayerShellEntity} 即可，
     * 但那样"打玩家"这件事就会随召唤物而漏判。
     */
    @SubscribeEvent
    public static void onProjectileDisarm(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if (event.getNewDamage() <= 0.0F) {
            return;   // 被完全挡下的弹射物不缴械（打空/无敌帧）
        }
        Entity direct = event.getSource().getDirectEntity();
        if (!(direct instanceof Projectile)) {
            return;   // 爆炸性实体 / 近战 / 环境伤害：不是弹射物，不缴械
        }
        if (attackerShellOf(event.getSource()) == null) {
            return;
        }
        if (player.getMainHandItem().isEmpty()) {
            return;   // 空手没什么可缴的
        }
        if (player.getRandom().nextFloat() >= PROJECTILE_DISARM_CHANCE) {
            return;
        }
        disarmMainHand(player);
    }

    /**
     * ③ <b>致命打击计数器</b>：本核心对玩家打出<b>原始伤害 ≥ 其最大生命值 ×
     * {@link OrbOfLuckEntity#FATAL_HIT_MAX_HEALTH_FRACTION}（85%）</b>的一击时，计数器减一；
     * 归零则同样触发<b>同化</b>（与"被这一击打死"走同一条路）。
     * <p>
     * 用 {@code getOriginalDamage()}（{@code hurt(source, amount)} 传进来、<b>还没被任何东西削过</b>的数值，
     * 正是需求里说的"原始的，未被计算减伤的"），不用 {@code getNewDamage()}（已过护甲/抗性）。
     *
     * <h2>为什么挂在 LivingDamageEvent.Pre 上，而不是更早的 LivingIncomingDamageEvent（踩过的坑）</h2>
     * <pre>
     * ① LivingIncomingDamageEvent（"减伤前"）
     * ② 护甲 / 附魔 / 抗性 / 吸收结算
     * ③ LivingDamageEvent.Pre      ← 同化钩子在这里把"会致死的那一击"清零
     * ④ 掉血 + LivingDamageEvent.Post
     * </pre>
     * 挂在 ① 上时，只要这一击<b>够致命</b>，③ 就会把它清零：伤害变成 0，④ 根本不发生。
     * 而重锤那种大伤害（实测 186 点打在 20 血玩家身上）<b>永远</b>满足"会致死"，
     * 于是每次都被同化钩子当场清零、玩家一血不掉、计数器<b>一次都不会被扣</b> ——
     * 表现就是"这个机制根本不存在"。
     * <p>
     * 放在 ③ 里就都对上了：原始伤害仍然可读，而且本方法在"致命→同化"那条逻辑之前先跑，
     * 所以"额度归零"与"被这一击打死"两条路不会打架：数值够大时先扣额度，
     * 正好扣到 0 就由这里触发同化；否则再交给下面那条致命判定。
     */
    private static boolean tickFatalHits(ServerPlayer player, LivingDamageEvent.Pre event) {
        PlayerShellEntity shell = attackerShellOf(event.getSource());
        if (shell == null) {
            return false;   // 这一击不是"由幸运核心产生的空壳"打的
        }
        float raw = event.getOriginalDamage();
        double threshold = player.getMaxHealth() * OrbOfLuckEntity.FATAL_HIT_MAX_HEALTH_FRACTION;
        // <b>诊断日志</b>：对每一次"核心产物的空壳命中玩家"都记一行（含未达阈值的），
        // 用来区分"没打中 / 没够阈值 / 够了但额度没归零"这三种情况。
        // TODO(诊断): 定位清楚之后可以把这条降级成只在 debug 模式下打印。
        ModMain.LOGGER.info("[同化] 附体命中: 玩家={} 原始伤害={} 阈值={} 血量={}/{} 空壳=#{} 剩余额度={} 来源={}",
            player.getGameProfile().getName(), raw, threshold,
            player.getHealth(), player.getMaxHealth(),
            shell.getId(), shell.getFatalHits(), describeSource(event.getSource()));
        if (raw < threshold) {
            return false;   // 不够"重"，不扣额度
        }
        if (isAssimilating(player)) {
            return false;   // 他已经被同化了：这一击不必再扣一次额度
        }
        boolean exhausted = shell.consumeFatalHit();
        ModMain.LOGGER.info("[同化] 致命打击成立: 玩家={} 原始伤害={} 阈值={} 剩余额度={}",
            player.getGameProfile().getName(), raw, threshold, shell.getFatalHits());
        if (exhausted) {
            assimilate(player, shell, player.serverLevel());
            return true;
        }
        return false;
    }

    /** 诊断用：把一次伤害的来源念成一句人话（直接来源 / 归属来源 / 出手空壳）。 */
    private static String describeSource(DamageSource source) {
        Entity direct = source.getDirectEntity();
        Entity owner = source.getEntity();
        return "type=" + source.getMsgId()
            + " direct=" + (direct == null ? "无" : direct.getType().toShortString())
            + " owner=" + (owner == null ? "无" : owner.getType().toShortString())
            + " 出手空壳=" + (attackerShellOf(source) == null ? "查不到" : "有");
    }

    /**
     * 把玩家身上"剩余时间 ≥ {@link #MELEE_POTION_MIN_TICKS}"的药水效果时长减半。
     * <p>
     * 先<b>快照</b>再改：{@code getActiveEffects()} 返回的是活的效果表，
     * 边遍历边 {@code addEffect} 替换同一个键是拿石头砸自己的脚（并发修改 / 改到一半的状态）。
     * <p>
     * 用 {@code addEffect(..., force = true)} 覆盖原效果：原版这个方法会保留原有等级、
     * 只把时长换成新的那份；{@code force} 是为了不被"同等级效果不能覆盖"的规则挡下。
     */
    private static void halvePotionDurations(ServerPlayer player) {
        List<MobEffectInstance> snapshot = new ArrayList<>(player.getActiveEffects());
        for (MobEffectInstance effect : snapshot) {
            int remaining = effect.getDuration();
            if (remaining < MELEE_POTION_MIN_TICKS) {
                continue;   // 不足 10 秒：不触发（用户指定）
            }
            MobEffectInstance shortened = new MobEffectInstance(effect.getEffect(), remaining / 2,
                effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
            player.addEffect(shortened);
        }
    }

    /**
     * 强行把玩家主手的物品丢出去（缴械）。
     * <p>
     * 丢完要把主手清空：{@code Player#drop} 只是生成掉落物，<b>不会</b>动玩家手里的栈，
     * 不清的话就变成"地上有一把、手里还有一把"。清空主手会引起物品同步，
     * 客户端手上的东西随之消失，不需要额外发包。
     */
    private static void disarmMainHand(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }
        // 手里拿着幸运核心时不被抽走（与铁链权杖那条"核心免疫缴械"一致）
        if (held.is(cn.autoforged.joes_addons_for_abmc.item.ModItems.ORB_OF_LUCK.get())) {
            return;
        }
        ItemStack drop = held.copy();
        held.shrink(held.getCount());
        player.drop(drop, true, false);
        ModMain.LOGGER.info("[附体] 缴械: 玩家={} 丢出={}",
            player.getGameProfile().getName(), drop.getItem());
    }

    /** 这具空壳身上记的核心本身（不是 UUID）；没有/找不到返回 null。 */
    @Nullable
    private static OrbOfLuckEntity orbOfShellEntity(PlayerShellEntity shell) {
        if (shell.level().getServer() == null) {
            return null;
        }
        UUID orbUuid = shell.getPossessedOrb();
        if (orbUuid == null) {
            orbUuid = orbOfShell(shell);   // 被同化的空壳走这条
        }
        return orbUuid == null ? null
            : (resolveOrbAnywhere(shell.level().getServer(), orbUuid) instanceof OrbOfLuckEntity orb ? orb : null);
    }

    /** 这颗核心是不是正处在附体阶段（不在附体阶段就没有"附体空壳"这一说）。 */
    private static boolean isPossessingOrb(@Nullable OrbOfLuckEntity orb) {
        return orb != null && orb.getOrbState() == OrbOfLuckEntity.OrbState.POSSESSING;
    }

    /**
     * 这具空壳背后那颗核心实体（<b>可能没有</b>）。
     * <p>
     * 两种情况：附体主空壳身上记着 {@code possessedOrbUuid}；被同化的空壳记在
     * {@code OrbPossessionSummons} 的标记里。而<b>刷怪蛋放出来的那一具两者皆无</b> ——
     * 它照样能同化别人，只是没有核心可以记账。
     */
    @Nullable
    private static OrbOfLuckEntity orbBehind(@Nullable PlayerShellEntity shell) {
        if (shell == null || shell.level().getServer() == null) {
            return null;
        }
        UUID orbUuid = shell.getPossessedOrb() != null ? shell.getPossessedOrb() : orbOfShell(shell);
        if (orbUuid == null) {
            return null;
        }
        return resolveOrbAnywhere(shell.level().getServer(), orbUuid) instanceof OrbOfLuckEntity orb ? orb : null;
    }

    // ====================== 同化 / 复活 ======================

    /**
     * 同化一名玩家：转旁观 + 原地生成一具同皮肤、挂着头顶光球的空壳。
     *
     * @param attacker 出手的空壳（决定新壳挂在谁名下：有核心就记进核心的同化名单，
     *                 没有核心就只记在玩家自己身上）
     */
    public static void assimilate(ServerPlayer player, PlayerShellEntity attacker, ServerLevel level) {
        UUID playerId = player.getUUID();
        if (isAssimilating(player)) {
            return;   // 已经被同化过
        }
        OrbOfLuckEntity orb = orbBehind(attacker);
        if (orb != null && playerId.equals(orb.getPossessorUuid())) {
            return;   // 他就是附体者本人
        }

        // 记下"被同化前"的游戏模式（复活时原样还回去）；同时让登录兜底也能认出这是附体体系的状态
        GameType back = player.gameMode.getGameModeForPlayer();
        player.getPersistentData().putInt(OrbPossessionEvents.TAG_PREV_GAMEMODE, back.getId());

        PlayerShellEntity shell = new PlayerShellEntity(ModEntities.PLAYER_SHELL.get(), level);
        shell.setSkinTexture(player.getGameProfile().getName());
        shell.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        // 一具"由幸运核心支撑"的空壳：走位/索敌/抽签攻击 + 头顶光球
        // + <b>生命上限 = 被同化前这位玩家的最大生命值</b>（用户指定）/ 20 护甲 / 12 韧性 / 保护 16
        // + 靠附魔金苹果回血（与附体主空壳、刷怪蛋放出来的那一具共用同一套配置，
        // 见 PlayerShellEntity#configureAsPossessedShell）
        shell.configureAsPossessedShell(player.getMaxHealth());
        // 出手壳的核心归属（可能没有）：新壳记着它，于是"同化壳再同化别人"能追溯回同一颗核心
        UUID attackerOrb = attacker.getPossessedOrb() != null
            ? attacker.getPossessedOrb() : orbOfShell(attacker);
        if (attackerOrb != null) {
            shell.getPersistentData().putUUID(TAG_ORB, attackerOrb);
        }
        shell.getPersistentData().putUUID(TAG_PLAYER, playerId);
        level.addFreshEntity(shell);

        // 玩家自己身上记一份：<b>这是复活与"是否已被同化"的唯一权威来源</b>。
        // 以前只记在核心上，于是<b>没有核心的同化（刷怪蛋空壳干的）根本无处记账</b> ——
        // 玩家变旁观之后就再没有任何东西知道他为什么是旁观、也就永远复活不了。
        player.getPersistentData().putBoolean(TAG_ASSUMED, true);
        player.getPersistentData().putUUID(TAG_MY_SHELL, shell.getUUID());
        if (attackerOrb != null) {
            player.getPersistentData().putUUID(TAG_MY_ORB, attackerOrb);
            if (orb != null) {
                orb.addAssimilated(playerId, shell.getUUID());   // 核心那份账本照旧维护（"杀原初"那条规则要用）
            }
        }
        player.setGameMode(GameType.SPECTATOR);
        // <b>同化成功 = 出手空壳的致命打击额度回满</b>（用户指定）。
        // 含义：一具空壳的额度是"每同化一个人就重置一次"的 —— 攒够 4 次重击同化掉一个目标之后，
        // 它又能去同化下一个，而不会因为额度已经用光而对后续目标失去威胁。
        // 放在最后一行（同化确实发生了才重置），上面任何一条 return 都不会走到这里。
        attacker.setFatalHits(PlayerShellEntity.DEFAULT_FATAL_HITS);
        ModMain.LOGGER.info("[同化] 完成: 玩家={} 出手空壳=#{} 额度已重置为 {}",
            player.getGameProfile().getName(), attacker.getId(), attacker.getFatalHits());
    }

    /** 这具空壳是不是"被同化"产生的那一具（它身上记着属于哪颗核心）。 */
    public static boolean isAssimilationShell(Entity entity) {
        return entity.getPersistentData().hasUUID(TAG_ORB);
    }

    /** 这名玩家是否正被同化（看<b>玩家自己</b>身上的记录，与场上有无核心无关）。 */
    public static boolean isAssimilating(ServerPlayer player) {
        return player.getPersistentData().getBoolean(TAG_ASSUMED);
    }

    /** 复活一名被同化玩家：恢复游戏模式、送到空壳所在位置、收掉空壳。 */
    public static boolean revive(ServerPlayer player, @Nullable OrbOfLuckEntity orb) {
        UUID playerId = player.getUUID();
        if (!isAssimilating(player)) {
            return false;
        }

        // 空壳优先从玩家自己的记录里取；旧存档（只在核心账本里记过）退回核心那份
        Vec3 where = player.position();
        Entity shell = null;
        if (player.getPersistentData().hasUUID(TAG_MY_SHELL)) {
            shell = player.serverLevel().getEntity(player.getPersistentData().getUUID(TAG_MY_SHELL));
        }
        if (shell == null && orb != null) {
            shell = shellOf(orb, playerId);
        }
        if (shell != null) {
            where = shell.position();
            shell.discard();   // discard 不会触发 die()，所以不会和"被打死"那条路互相递归
        }

        GameType back = GameType.byId(player.getPersistentData().getInt(OrbPossessionEvents.TAG_PREV_GAMEMODE));
        player.getPersistentData().remove(OrbPossessionEvents.TAG_PREV_GAMEMODE);
        player.getPersistentData().remove(TAG_ASSUMED);
        player.getPersistentData().remove(TAG_MY_SHELL);
        player.getPersistentData().remove(TAG_MY_ORB);
        if (player.gameMode.getGameModeForPlayer() != back) {
            player.setGameMode(back);
        }
        player.teleportTo(where.x, where.y, where.z);
        if (orb != null) {
            orb.removeAssimilated(playerId);
        }
        return true;
    }

    /**
     * 被同化的空壳被打死时调用（{@link PlayerShellEntity#die}）：把那玩家复活。
     * <p>
     * 只需要空壳身上记着"我对应哪名玩家"（{@link #TAG_PLAYER}）—— <b>不再要求它有核心</b>：
     * 刷怪蛋空壳同化出来的那一具身上就没有核心标记，但打死它照样得把人放出来。
     *
     * @return 是否处理了这具空壳（true = 它是一具"被同化"的空壳）
     */
    public static boolean reviveFromShell(PlayerShellEntity shell) {
        CompoundTag data = shell.getPersistentData();
        if (!data.hasUUID(TAG_PLAYER)) {
            return false;
        }
        if (shell.level().getServer() == null) {
            return false;
        }
        UUID playerId = data.getUUID(TAG_PLAYER);
        ServerPlayer player = shell.level().getServer().getPlayerList().getPlayer(playerId);
        if (player == null) {
            // 玩家离线：只能先把壳收掉，等他上线时由 OrbPossessionEvents 的登录兜底按他自己的记录复活
            shell.discard();
            return true;
        }
        OrbOfLuckEntity orb = data.hasUUID(TAG_ORB)
            ? (resolveOrbAnywhere(shell.level().getServer(), data.getUUID(TAG_ORB)) instanceof OrbOfLuckEntity o ? o : null)
            : null;
        return revive(player, orb);
    }

    /** 核心被击杀 / 附体结束：一次性复活所有被同化者（这是"杀原初救大家"那条规则）。 */
    public static void reviveAll(OrbOfLuckEntity orb) {
        if (orb.getAssimilatedCount() == 0 || orb.level().getServer() == null) {
            return;
        }
        for (UUID playerId : orb.assimilatedPlayerIds()) {
            ServerPlayer player = orb.level().getServer().getPlayerList().getPlayer(playerId);
            // 离线的：只收掉空壳、清掉记录（他下次登录时 persistentData 里的模式备份会由 OrbPossessionEvents 补回）
            Entity shell = shellOf(orb, playerId);
            if (shell != null) {
                shell.discard();
            }
            if (player != null) {
                revive(player, orb);
            } else {
                orb.removeAssimilated(playerId);
            }
        }
    }

    /**
     * 每刻维护（由 {@link OrbOfLuckEntity#tick()} 在 POSSESSING 阶段调用）：
     * 被同化的玩家一律保持旁观；谁切回了创造模式，就当场解除他的同化。
     */
    public static void tick(OrbOfLuckEntity orb) {
        if (orb.getAssimilatedCount() == 0 || orb.level().getServer() == null) {
            return;
        }
        for (UUID playerId : orb.assimilatedPlayerIds()) {
            ServerPlayer player = orb.level().getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;   // 离线：同化状态原样留着，等他回来
            }
            if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
                revive(player, orb);
                continue;
            }
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                player.setGameMode(GameType.SPECTATOR);
            }
        }
    }

    /**
     * 每刻维护（无核心那一份账）：由服务端 tick 钩子调用，
     * 管住"被刷怪蛋空壳同化"的玩家 —— 他们不在任何核心的名单里，所以上面那个
     * {@link #tick(OrbOfLuckEntity)} 永远看不到他们。
     * <p>
     * 做两件事：一律保持旁观；谁切回创造模式就当场解除。
     */
    public static void tickCorelessAssimilations(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isAssimilating(player)) {
                continue;
            }
            // 有核心账本的那一份由 tick(OrbOfLuckEntity) 管，这里只管"没有核心"的
            if (player.getPersistentData().hasUUID(TAG_MY_ORB)) {
                continue;
            }
            if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
                revive(player, null);
                continue;
            }
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                player.setGameMode(GameType.SPECTATOR);
            }
        }
    }

    // ====================== 查询辅助 ======================

    /** 空壳身上记的"属于哪颗核心"。 */
    @Nullable
    public static UUID orbOfShell(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.hasUUID(TAG_ORB) ? data.getUUID(TAG_ORB) : null;
    }

    /** 这颗核心为某玩家留下的空壳实体；不在（未加载/已被清掉）返回 null。 */
    @Nullable
    private static Entity shellOf(OrbOfLuckEntity orb, UUID playerId) {
        UUID shellUuid = orb.assimilatedShell(playerId);
        if (shellUuid == null || !(orb.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getEntity(shellUuid);
    }

    @Nullable
    private static Entity resolveOrbAnywhere(net.minecraft.server.MinecraftServer server, UUID orbUuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity orb = level.getEntity(orbUuid);
            if (orb != null) {
                return orb;
            }
        }
        return null;
    }

    /**
     * 这次伤害的<b>出手空壳</b>（不管它背后有没有核心）。
     * <p>
     * 先看直接来源，再看归属来源 —— 覆盖三种出手方式：
     * <ul>
     *   <li><b>空壳本体近战</b>：直接来源就是那具空壳（{@code mobAttack(shell)}）；</li>
     *   <li><b>空壳自己射的弹射物</b>：{@code getEntity()}（发射者）是空壳；</li>
     *   <li><b>召唤物打出来的伤害</b>（爆炸性苦力怕/猫/僵尸、骷髅马骑士，以及它们射出的箭）：
     *       这些召唤物身上带 {@code OrbPossessionSummons} 的核心标记，顺着标记找回主空壳。</li>
     * </ul>
     * <b>关键点：这里不再要求"能查到核心实体"。</b>
     * 用户指定"所有由幸运核心产生的玩家空壳（无论是本体还是同化产生的）都具备同化其它玩家的能力"，
     * 而<b>刷怪蛋放出来的那一具背后没有核心实体</b>（见 {@code OrbPlayerShellSpawnEggItem}：
     * 它只调 {@code configureAsPossessedShell()}，从不 {@code setPossessedOrb}）。
     * 旧写法先查核心、查不到就整条链返回 null，于是致命打击计数与同化对刷怪蛋空壳完全失效
     * （实测日志里的 {@code orb=查不到} 就是这一步）。
     */
    @Nullable
    private static PlayerShellEntity attackerShellOf(DamageSource source) {
        PlayerShellEntity direct = shellOfEntity(source.getDirectEntity());
        if (direct != null) {
            return direct;
        }
        PlayerShellEntity owner = shellOfEntity(source.getEntity());
        if (owner != null) {
            return owner;
        }
        // 召唤物（爆炸性实体等）：它身上记着"属于哪颗核心"，顺着找那具主空壳。
        // 取不到实体就直接收工（摔落/虚空/命令这类无实体伤害源两者都是 null）——
        // 6.4.31 的 NPE 崩溃就是少了这一步判断。
        Entity summon = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
        if (summon == null || source.getEntity() == null || source.getEntity().level().getServer() == null) {
            return null;
        }
        UUID summonOrb = OrbPossessionSummons.orbOf(summon);
        if (summonOrb == null) {
            return null;
        }
        return possessorShellOf(source.getEntity().level().getServer(), summonOrb);
    }

    /** 这个实体本身是不是一具"由幸运核心产生的空壳"（本体 / 被同化的 / 刷怪蛋放的）。 */
    @Nullable
    private static PlayerShellEntity shellOfEntity(@Nullable Entity entity) {
        return entity instanceof PlayerShellEntity shell && isPossessionShell(shell) ? shell : null;
    }

    /**
     * 这具空壳是不是"由幸运核心产生的"那一类 —— 也就是<b>能同化别人</b>的空壳。
     * <p>
     * 判据取 {@link PlayerShellEntity#isOrbAttached()}：三种生成入口
     * （附体主空壳、被同化的、刷怪蛋放的）都把它置真，而变形药水造出来的临时空壳
     * 与普通生物壳都是假 —— 正好就是"核心产生的"这一集合。
     */
    public static boolean isPossessionShell(PlayerShellEntity shell) {
        return shell.isOrbAttached();
    }

    /**
     * 找这颗核心名下的<b>附体主空壳</b>（召唤物归属到空壳时用）。
     * 被同化的空壳也算：它能追溯回同一颗核心，于是"同化壳再同化别人"时，
     * 新壳同样挂在这颗核心名下。
     */
    @Nullable
    private static PlayerShellEntity possessorShellOf(net.minecraft.server.MinecraftServer server, UUID orbUuid) {
        Entity orbEntity = resolveOrbAnywhere(server, orbUuid);
        if (!(orbEntity instanceof OrbOfLuckEntity orb)) {
            return null;
        }
        // 核心骑在它那具"玩家"身体上，所以"乘客里的那个玩家空壳"就是它名下的主空壳。
        // 不用核心的 vesselUuid 字段：那是核心自己记的，万一不同步（读档/换壳）就查不到人。
        for (Entity passenger : orb.getPassengers()) {
            if (passenger instanceof PlayerShellEntity shell) {
                return shell;
            }
        }
        return null;
    }

    /** 诊断用：把实体念成"类型#id"（带载具/乘客，附体核心是骑在空壳身上的）。 */
    private static String describeEntity(@Nullable Entity entity) {
        if (entity == null) {
            return "无";
        }
        String vehicle = entity.getVehicle() == null ? "-"
            : entity.getVehicle().getType().toShortString() + "#" + entity.getVehicle().getId();
        StringBuilder passengers = new StringBuilder();
        for (Entity p : entity.getPassengers()) {
            if (passengers.length() > 0) {
                passengers.append(',');
            }
            passengers.append(p.getType().toShortString()).append('#').append(p.getId());
        }
        return entity.getType().toShortString() + "#" + entity.getId()
            + "(载具=" + vehicle + " 乘客=" + (passengers.length() == 0 ? "-" : passengers) + ")";
    }
}
