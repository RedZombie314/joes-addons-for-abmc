package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.config.ModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Orb of Luck（幸运核心）物品：玩家手中的核心。
 *
 * <h2>怎么获得</h2>
 * 世界里处于 {@code INITIAL} 阶段的 {@link cn.autoforged.joes_addons_for_abmc.entity.OrbOfLuckEntity}
 * 被右击时消失，并把这个物品交给玩家：
 * <ul>
 *   <li><b>空手</b>右击 → 直接进那只手（拿到之后会锁在主手，见 {@link OrbOfLuckLock}）；</li>
 *   <li><b>手里拿着别的东西</b>右击 → 核心以<b>玩家跑步的速度</b>匀速飞向该玩家，贴身后进<b>背包</b>
 *       （并吃掉这次右击，不会顺手把手里的东西用掉）；背包满则只发一条提示
 *       （见 {@code OrbOfLuckEntity#mobInteract}）。</li>
 * </ul>
 *
 * <h2>第一人称外观</h2>
 * 物品模型是 {@code builtin/entity}，第一人称由
 * {@link cn.autoforged.joes_addons_for_abmc.client.OrbOfLuckHandRenderer} 接管：
 * 拦掉原版的手部渲染，改画「空手手臂 + 手臂末端的光球」；其它场合由
 * {@link cn.autoforged.joes_addons_for_abmc.client.OrbOfLuckItemRenderer} 画成同一颗光球。
 *
 * <h2>隐藏倒计时（用户指定，取代原来的"20 次"计数器）</h2>
 * 拿到核心的那一刻起算 <b>{@link #COUNTDOWN_TICKS} 刻（1 分钟）</b>，存的是"到期时刻"
 * （{@link ModDataComponents#ORB_COUNTDOWN}）。它<b>不显示在任何地方</b>（tooltip 里没有），
 * 但每"用"一次就<b>直接减去 {@link #USE_COST_TICKS}（2 秒）</b>
 * —— debug 模式下一次减 {@link #DEBUG_USE_COST_TICKS}（60 秒，等于当场用光）。
 * 倒计时到期（≤ 0）就触发 Orb Possession（{@link OrbOfLuckEvents#triggerPossession}），
 * 由 {@link OrbOfLuckLock} 每刻检查。
 * <p>
 * 于是"次数上限"没有了：想多用几次就多用，代价只是离附体更近一点（30 次 ≈ 一分钟用完）。
 *
 * <h2>拿不下来</h2>
 * 主手上时它会锁在主手（换格/丢弃/F 键/背包里点击全部失效）；
 * <b>放在背包其它格子里时</b>可以自由拖动，但不能丢弃、也不能塞进任何容器
 * （合成格、箱子、漏斗……见 {@link OrbOfLuckLock} 与 {@code ContainerOrbLockMixin}）。
 */
public class OrbOfLuckItem extends Item {

    /**
     * 隐藏倒计时的总长（刻）：<b>1 分钟</b>（用户指定）。
     * <p>拿到核心时写一次"到期时刻" = 当时 + 这值。
     */
    public static final int COUNTDOWN_TICKS = 60 * 20;

    /**
     * 每"用"一次扣掉的倒计时（刻）：<b>2 秒</b>（用户指定）。
     * <p>也就是 1 分钟最多用 30 次左右，之后就会触发附体 —— 取代了原来的"20 次"上限。
     */
    public static final int USE_COST_TICKS = 2 * 20;

    /** debug 模式下每"用"一次扣掉的倒计时（刻）：<b>60 秒</b>（用户指定：一次直接用光 → 当场附体）。 */
    public static final int DEBUG_USE_COST_TICKS = 60 * 20;

    public OrbOfLuckItem(Properties properties) {
        super(properties);
    }

    // ==================== 隐藏倒计时 ====================

    /** 从"现在"起算一整段倒计时（拿到核心时写一次）。 */
    public static void startCountdown(Level level, ItemStack stack) {
        stack.set(ModDataComponents.ORB_COUNTDOWN.get(), level.getGameTime() + COUNTDOWN_TICKS);
    }

    /**
     * 这件核心的倒计时到期时刻（游戏刻）。
     * <p>没有这个组件（旧存档 / 别人塞进来的核心）时返回 {@link Long#MAX_VALUE}：
     * 当作"永远不到期"，绝不因为读了个空组件就把玩家当场附体。
     */
    public static long getDeadline(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.ORB_COUNTDOWN.get(), Long.MAX_VALUE);
    }

    /** 保证这件核心有一段正在走的倒计时：没写过（旧存档）就补一次"从现在起 1 分钟"。 */
    public static long ensureCountdown(Level level, ItemStack stack) {
        if (!stack.has(ModDataComponents.ORB_COUNTDOWN.get())) {
            startCountdown(level, stack);
        }
        return getDeadline(stack);
    }

    /**
     * 用掉一次机会：把倒计时直接减去 {@link #USE_COST_TICKS}（debug 模式减
     * {@link #DEBUG_USE_COST_TICKS}），减到 ≤ 0 就触发附体。
     * <p>抽中事件那条路与"坐矿车加速"那条路共用，免得两边各写一遍归零逻辑。
     */
    private static void spendUse(Player player, ItemStack stack) {
        Level level = player.level();
        long deadline = ensureCountdown(level, stack)
            - (ModConfig.DEBUG_MODE.get() ? DEBUG_USE_COST_TICKS : USE_COST_TICKS);
        stack.set(ModDataComponents.ORB_COUNTDOWN.get(), deadline);
        if (deadline <= level.getGameTime()) {
            OrbOfLuckEvents.triggerPossession(player);
        }
    }

    /**
     * 在玩家身上（快捷栏 + 主背包 + 副手）找那颗核心。
     * <p>返回的是背包里那个<b>活的</b> ItemStack 引用，调用方拿到就能直接改/清空（见
     * {@code OrbOfLuckEvents#triggerPossession} 与 {@link OrbOfLuckLock} 的倒计时检查）。
     */
    @Nullable
    public static ItemStack findInInventory(Player player) {
        if (player.getMainHandItem().is(ModItems.ORB_OF_LUCK.get())) {
            return player.getMainHandItem();
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.ORB_OF_LUCK.get())) {
                return stack;
            }
        }
        return player.getOffhandItem().is(ModItems.ORB_OF_LUCK.get()) ? player.getOffhandItem() : null;
    }

    // ==================== "至多抽取一次"的位图（需求 6.5.25） ====================

    /** 这颗核心已经抽到过哪些事件（位图，下标 = {@code OrbOfLuckEvents.EVENTS} 的条目序号）。 */
    public static int getDrawnMask(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.ORB_DRAWN.get(), 0);
    }

    /** 记下"第 {@code index} 条事件已经出过"（只对"至多一次"的条目调用）。 */
    public static void markDrawn(ItemStack stack, int index) {
        stack.set(ModDataComponents.ORB_DRAWN.get(), getDrawnMask(stack) | (1 << index));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        // 需求 9：坐在矿车里"使用"核心 → 直接给矿车 100 m/s（+ 车尾喷射），不抽事件。
        // 放在最前面：不论右击的是方块还是空气，先看"是不是坐在矿车里"。
        if (OrbSpecialMechanics.boostMinecart(player)) {
            if (!context.getLevel().isClientSide()) {
                spendUse(player, stack);
            }
            return InteractionResult.SUCCESS;
        }

        if (context.getLevel().isClientSide()) {
            // 客户端只回"这次右键算数"，真正的逻辑在服务端跑
            return InteractionResult.SUCCESS;
        }

        // 触发一次幸运核心事件；条件不满足（例如交互范围之外）就当这次右键没发生，不扣倒计时
        if (!OrbOfLuckEvents.trigger(context)) {
            return InteractionResult.PASS;
        }
        spendUse(player, stack);
        return InteractionResult.SUCCESS;
    }

    /**
     * <b>右击空气</b>（需求 9）：坐在矿车里对着空气用核心同样要生效——{@code useOn} 只在"右击方块"时触发，
     * 所以必须把这条也接上，否则骑着矿车朝天上用就没反应。
     * <p>
     * 不在矿车上则原样 {@code PASS}，交回原版（核心没有"对着空气用"的其它行为）。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!OrbSpecialMechanics.boostMinecart(player)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide()) {
            spendUse(player, stack);
        }
        return InteractionResultHolder.success(stack);
    }

    /**
     * 工具提示：<b>不再显示次数</b>（用户指定倒计时是"隐藏"的）。
     * <p>原来那行"剩余次数：n / 20"随计数器一起去掉了；要再加提示就往这里加。
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    /**
     * 丢弃拦截。
     * <p>
     * 核心<b>只要还在身上就不许丢</b>（不管在主手还是背包里）：用户指定"你无法将它丢弃"。
     * 这里拦的是"选中格丢弃"（Q 键）这条路 —— {@code item} 就是被丢的那件，所以条件只剩"允许不允许锁"。
     * 背包界面里对任意格子按 Q 的丢弃走 {@code ContainerOrbLockMixin}，客户端本地删除走
     * {@code LocalPlayerOrbLockMixin}。
     * <p>
     * 原版 {@code ServerPlayer#drop} 是「先问这句、再动背包」的顺序，返回 false 是最干净的拦法。
     */
    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        return !OrbOfLuckLock.lockAllowed(player);
    }
}
