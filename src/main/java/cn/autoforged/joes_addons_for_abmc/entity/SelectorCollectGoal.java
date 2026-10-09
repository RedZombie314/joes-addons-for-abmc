package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.block.LuckyCreatureTypes;
import cn.autoforged.joes_addons_for_abmc.item.ModItemTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * 幸运选择器在<b>幸运维度</b>里的自动收集 AI。
 *
 * <h3>行为（需求原文的顺序）</h3>
 * <ol>
 *   <li>一边四处偏移（飞行交给 {@link SelectorWanderGoal}）一边<b>每 200 游戏刻</b>在
 *       <b>128 格</b>内搜一次：{@code #joes_addons_for_abmc:lucky_items} 幸运物品池里的<b>掉落物</b>，
 *       或者"幸运生物事件"里的<b>生物</b>（见 {@link LuckyCreatureTypes}），两者权重 <b>1:1</b>；</li>
 *   <li>搜到就 <b>seek</b> 离自己最近的那一个（生物若处于骑乘状态，取<b>被骑乘</b>的那只；
 *       叠罗汉时取<b>最底下</b>那只——见 {@link #rootVehicle}）；</li>
 *   <li>seek 完成后<b>再游荡 100~200 刻</b>，随后 <b>send</b>，把它收进<b>幸运名单</b>
 *       （{@code LuckyRoster}，send 收尾时入队）；</li>
 *   <li>send 彻底结束之后，<b>周期性搜索才重新开始</b>（也就是"搜到之后下一次搜索只在 send 完成之后"）。</li>
 * </ol>
 * 不在幸运维度就整个不工作（手动 {@code /jafa seek} / {@code /jafa send} 不受影响）。
 *
 * <h3>谁才允许"找工作"</h3>
 * 由 {@link SelectorWorkSlots} 按刻选举：<b>一个玩家附近至多 3 只</b>选择器会去找目标，
 * 其余的只跑 {@link SelectorWanderGoal}（随机游荡）——本 goal 的
 * {@link #canUse()} / {@link #canContinueToUse()} 都要它点头。
 * 手动命令（{@code /jafa seek}）不走这个 goal，所以不受这条限制。
 *
 * <h3>为什么这个 goal 不占任何 Goal.Flag</h3>
 * 它只做"决策 + 计时"：真正往前飞的是 {@link SelectorWanderGoal}（占 {@code Flag.MOVE}）。
 * 如果它也去占 MOVE，两个 goal 会互斥——要么游荡不动、要么这边不跑，而需求要的恰恰是
 * <b>一边游荡一边搜</b>。{@code GoalSelector} 对"没有旗标"的 goal 不做任何占用检查
 * （{@code goalContainsAnyFlags} 空集恒 false），所以它能和游荡 goal 同时运行，每刻被 tick。
 *
 * <h3>和抓取/发送流程的关系</h3>
 * {@link LuckySelectorEntity#isBusy()} 为真（正在抓或正在发）时本 goal 只是等；
 * 手上已经有东西（{@link LuckySelectorEntity#hasContainedContent()}）时也不开新的搜索——
 * 载具只有一个乘客位，而且那是"玩家手动抓来的、还没送走"的东西，不该被 AI 抢。
 */
public class SelectorCollectGoal extends Goal {

    /** 搜索半径（格，需求给的 128）。 */
    private static final double SEARCH_RADIUS = 128.0D;

    /** 两次搜索之间的间隔（刻，需求给的 200 = 10 秒）。 */
    private static final int SEARCH_INTERVAL_TICKS = 200;

    /** seek 完成之后、send 之前的游荡时长范围（刻，需求给的 100~200）。 */
    private static final int WANDER_BEFORE_SEND_MIN = 100;
    private static final int WANDER_BEFORE_SEND_MAX = 200;

    /**
     * "手里攥着东西却一直送不出去"能忍多少刻（3 秒），超过就按抓取中止把它放掉。
     * <p>
     * 取 60 的理由：抓取流程里"抓住到骑上来"最长 40 刻（stretch + retreat）本来就会短暂处于
     * "有东西但没骑上"的状态，余量给到 60 就不会误伤正常抓取。
     */
    private static final int SEND_STUCK_GIVE_UP_TICKS = 60;

    /**
     * 玩家用 {@code /jafa seek} 手动抓来的东西，最多替他在手里留多少刻（30 秒）再自动送走。
     * <p>
     * 手动抓的东西本来不该被 AI 抢（这是老注释里的约定），但"留着不送"的选择器会
     * {@code PersistenceRequired = true} 且永远清不掉，最终把整个维度的选择器名额占满
     * （6.4.33 修的就是这个）。所以这里折中：给玩家 30 秒自己处置（{@code /jafa send}），
     * 超时由 AI 收尾；命令本身随时可以打断它。
     */
    private static final int MANUAL_SEND_GRACE_TICKS = 600;

    /** 本 goal 所处的阶段。 */
    private enum Phase {
        /** 等下一次搜索到点，然后挑目标。 */
        SEARCH,
        /** 已经把目标交给实体的 seek 流程，等它结束。 */
        SEEKING,
        /** 抓到手了，先游荡一会儿再送。 */
        WANDER_BEFORE_SEND,
        /** send 动画进行中，等它结束（结束后东西就进名单了）。 */
        SENDING
    }

    private final LuckySelectorEntity selector;
    private Phase phase = Phase.SEARCH;
    /** {@link Phase#SEARCH}：下一次搜索的时刻。 */
    private long nextSearchTick;
    /** {@link Phase#WANDER_BEFORE_SEND}：到这一刻就 send。 */
    private long sendAtTick;
    /** {@link Phase#WANDER_BEFORE_SEND}：连续多少刻"送不出去"（见 {@link #SEND_STUCK_GIVE_UP_TICKS}）。 */
    private int sendStuckTicks;

    public SelectorCollectGoal(LuckySelectorEntity selector) {
        this.selector = selector;
        // 刻意留空：不占任何旗标，见类注释
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    /**
     * 只在幸运维度工作，<b>而且本刻被 {@link SelectorWorkSlots} 授权</b>
     * （需求：一个玩家附近至多 3 只选择器同时找目标/抓东西，其余只游荡）；
     * 正被玩家旁观操控时也不工作（那段时间由玩家说了算，见 {@code LuckySelectorEntity#tickPilot}）。
     */
    @Override
    public boolean canUse() {
        return LuckyDimensionMobs.isLuckyDimension(this.selector.level())
            && !this.selector.isPiloted()
            && SelectorWorkSlots.mayWork(this.selector);
    }

    /**
     * 进来之后一直跑（阶段推进由 {@link #tick()} 管），除非离开幸运维度或被取消授权。
     * <p>
     * 被取消授权时这里会返回 false、goal 被停掉并重置回 SEARCH——但<b>正在抓/正在送/手上攥着东西的
     * 那几种不会掉出授权</b>（见 {@link SelectorWorkSlots} 的"两种选择器永远放行"），
     * 所以不会出现"手里抓着东西却再也不去送"的死锁。
     */
    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.phase = Phase.SEARCH;
        this.nextSearchTick = this.now();
    }

    @Override
    public void stop() {
        this.phase = Phase.SEARCH;
    }

    @Override
    public void tick() {
        switch (this.phase) {
            case SEARCH -> this.tickSearch();
            case SEEKING -> this.tickSeeking();
            case WANDER_BEFORE_SEND -> this.tickWanderBeforeSend();
            case SENDING -> this.tickSending();
        }
    }

    /** 到点了就挑一个目标交给实体的 seek 流程。 */
    private void tickSearch() {
        if (this.selector.isBusy()) {
            return; // 玩家手动在抓/在发（或者上一轮动画还没收尾）：让位
        }
        if (this.selector.hasContainedContent()) {
            /*
             * 手上已经攥着东西：<b>别再找新的，直接安排把它送走</b>。
             *
             * 这一段是 6.4.33 补的关键收尾口。以前这里是直接 return（"手上还攥着东西：让位"），
             * 于是只要"抓着东西"这个状态跨过了 goal 的一次中断，那只选择器就<b>永远不会再送</b>：
             *   SEARCH 因为手里有东西而让位 → 手里那把东西谁也送不走 → 选择器永久带着它。
             * 而"带着东西"意味着 PersistenceRequired = true，它又<b>不受</b>"离玩家 >128 格就删"的约束
             * （MobLuckyDespawnMixin），于是它带着那把东西在维度里飘一辈子、还一直占着上限名额。
             * goal 什么时候会被中断？读档（start() 把阶段重置回 SEARCH）、区块卸载、
             * canContinueToUse 变假……任何一种都会把 WANDER_BEFORE_SEND/SENDING 这两个阶段丢掉。
             *
             * 实测：存档里 45 只选择器有 32 只正带着东西（passengers=1）却再也不送，
             * 把上限（17）永久占满，生成器第一道闸直接返回 → 玩家"一只选择器都看不到"。
             */
            this.phase = Phase.WANDER_BEFORE_SEND;
            // 手动抓的那把东西多留一会儿（玩家可能正看着它、准备自己 /jafa send），
            // 但也不能无限留——留着不送的选择器会永久持久化、永久占名额，见上面那段注释
            this.sendAtTick = this.now() + (this.selector.isManualCapture()
                ? MANUAL_SEND_GRACE_TICKS
                : WANDER_BEFORE_SEND_MIN
                    + this.selector.getRandom().nextInt(WANDER_BEFORE_SEND_MAX - WANDER_BEFORE_SEND_MIN + 1));
            this.sendStuckTicks = 0;
            return;
        }
        long now = this.now();
        if (now < this.nextSearchTick) {
            return;
        }
        Entity target = this.findTarget();
        if (target == null || !this.selector.startSeekTarget(target)) {
            // 没搜到（或目标那一刻已经不能抓了）：等下一个 200 刻周期
            this.nextSearchTick = now + SEARCH_INTERVAL_TICKS;
            return;
        }
        this.phase = Phase.SEEKING;
    }

    /** 等 seek 结束：抓到了就去游荡，没抓到（中途丢了）就回去继续周期性搜索。 */
    private void tickSeeking() {
        if (this.selector.isBusy()) {
            return;
        }
        if (this.selector.hasContainedContent()) {
            this.phase = Phase.WANDER_BEFORE_SEND;
            this.sendAtTick = this.now() + WANDER_BEFORE_SEND_MIN
                + this.selector.getRandom().nextInt(WANDER_BEFORE_SEND_MAX - WANDER_BEFORE_SEND_MIN + 1);
        } else {
            this.phase = Phase.SEARCH;
            this.nextSearchTick = this.now() + SEARCH_INTERVAL_TICKS;
        }
    }

    /** 抓着东西游荡 100~200 刻，然后开送。 */
    private void tickWanderBeforeSend() {
        if (this.selector.isBusy()) {
            this.sendStuckTicks = 0;
            return;
        }
        if (!this.selector.hasContainedContent()) {
            // 手上的东西没了（被别的途径弄掉）：别傻等，回去搜
            this.phase = Phase.SEARCH;
            this.nextSearchTick = this.now() + SEARCH_INTERVAL_TICKS;
            this.sendStuckTicks = 0;
            return;
        }
        if (this.now() < this.sendAtTick) {
            return;
        }
        if (this.selector.startSend()) {
            this.phase = Phase.SENDING;
            this.sendStuckTicks = 0;
            return;
        }
        /*
         * 送不出去（{@link LuckySelectorEntity#startSend()} 只在"生物还骑在本体上"时才肯开送）：
         * 给它 {@link #SEND_STUCK_GIVE_UP_TICKS} 刻的余地（抓取流程那 40 刻里本来就会短暂处于这个状态），
         * 超时还是送不出去就按"抓取中止"把它手里那只原样放掉——宁可放它回野外，
         * 也不能让这些选择器带着东西卡死（它们带着东西就永久持久化、永久占名额，见 tickSearch 的注释）。
         */
        if (++this.sendStuckTicks > SEND_STUCK_GIVE_UP_TICKS) {
            this.selector.abortSeek();
            this.sendStuckTicks = 0;
            this.phase = Phase.SEARCH;
            this.nextSearchTick = this.now() + SEARCH_INTERVAL_TICKS;
        }
    }

    /** send 结束（东西已进名单）→ 周期性搜索重新开始。 */
    private void tickSending() {
        if (this.selector.isBusy()) {
            return;
        }
        this.phase = Phase.SEARCH;
        this.nextSearchTick = this.now() + SEARCH_INTERVAL_TICKS;
    }

    /**
     * 在 128 格内挑一个目标：<b>幸运物品池里的掉落物</b>或<b>幸运生物事件里的生物</b>，权重 1:1。
     * <p>
     * 1:1 的落法是"先随机决定这一轮找哪一类，再到那一类里取最近的"。如果选中的那一类一个都没有、
     * 另一类有，就直接取另一类——权重只在<b>两类都有</b>时才谈得上（只有一种可选时，1:1 没有意义，
     * 硬按权重会让一半的搜索周期白白浪费）。
     */
    @Nullable
    private Entity findTarget() {
        boolean itemFirst = this.selector.getRandom().nextBoolean();
        Entity first = itemFirst ? this.nearestLuckyItem() : this.nearestLuckyCreature();
        if (first != null) {
            return first;
        }
        return itemFirst ? this.nearestLuckyCreature() : this.nearestLuckyItem();
    }

    /** 128 格内最近的"幸运物品池"掉落物（已被别的选择器锁定的跳过）。 */
    @Nullable
    private ItemEntity nearestLuckyItem() {
        ItemEntity nearest = null;
        double nearestSqr = SEARCH_RADIUS * SEARCH_RADIUS;
        for (ItemEntity item : this.level().getEntitiesOfClass(ItemEntity.class, this.searchBox())) {
            // 判定直接打标签：池子是数据包内容，这样无须先把整张池子解析成列表
            if (!item.isAlive() || !item.getItem().is(ModItemTags.LUCKY_ITEMS)) {
                continue;
            }
            // 已被别的选择器锁定/抓取的跳过（需求）：不然同一件东西会被好几只一起扑
            if (SelectorTargetClaims.isClaimedByOther(item, this.selector)) {
                continue;
            }
            double distSqr = item.distanceToSqr(this.selector);
            if (distSqr < nearestSqr) {
                nearestSqr = distSqr;
                nearest = item;
            }
        }
        return nearest;
    }

    /**
     * 128 格内最近的"幸运生物事件里的生物"。
     * <p>
     * 找到的若是骑在别的东西上的（叠罗汉的上层），一律换成<b>最底下那只被骑乘的生物</b>
     * （{@link #rootVehicle}）——需求要的是连整摞一起收走，而不是把上面的揪下来。
     */
    @Nullable
    private Mob nearestLuckyCreature() {
        Mob nearest = null;
        double nearestSqr = SEARCH_RADIUS * SEARCH_RADIUS;
        for (Mob mob : this.level().getEntitiesOfClass(Mob.class, this.searchBox())) {
            if (!LuckyCreatureTypes.isLuckyCreature(mob) || !mob.isAlive() || mob.isRemoved()) {
                continue;
            }
            Mob root = rootVehicle(mob);
            if (!this.selector.canCapture(root)) {
                continue;
            }
            double distSqr = root.distanceToSqr(this.selector);
            if (distSqr < nearestSqr) {
                nearestSqr = distSqr;
                nearest = root;
            }
        }
        return nearest;
    }

    /**
     * 沿"被谁骑着"一路往上找，返回这一摞里<b>最底下那个被骑乘的生物</b>。
     * <p>
     * 例：猪3 → 猪2 → 猪1（站在地上），输入猪3/猪2/猪1 都返回猪1。
     * 链条顶端骑的不是生物（比如骑在船上、或被选择器抓着）时就停在那一只，
     * 由 {@link LuckySelectorEntity#canCapture(Mob)} 决定要不要（"自己是乘客"的不抓）。
     */
    private static Mob rootVehicle(Mob mob) {
        Mob current = mob;
        while (current.isPassenger() && current.getVehicle() instanceof Mob vehicle) {
            current = vehicle;
        }
        return current;
    }

    private ServerLevel level() {
        return (ServerLevel) this.selector.level();
    }

    private AABB searchBox() {
        return this.selector.getBoundingBox().inflate(SEARCH_RADIUS);
    }

    private long now() {
        return this.selector.level().getGameTime();
    }
}
