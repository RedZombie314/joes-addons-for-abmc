package cn.autoforged.joes_addons_for_abmc.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.block.LuckyEvent;
import cn.autoforged.joes_addons_for_abmc.block.LuckyEvents;
import cn.autoforged.joes_addons_for_abmc.entity.ModEntities;
import cn.autoforged.joes_addons_for_abmc.entity.OrbOfLuckEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 幸运核心事件（Orb of Luck events）。
 *
 * <h2>流程</h2>
 * 玩家手持核心右键一次 → {@link OrbOfLuckItem#useOn} 调 {@link #trigger} 抽一个事件执行 →
 * 事件真的发生了（返回 {@code true}）才扣次数（debug 模式一次扣满 {@code DEFAULT_COUNTER}）→
 * 次数归零则触发 {@link #triggerPossession}（Orb Possession，目前是占位提示）。
 *
 * <h2>抽签规则（需求 6.5.25）</h2>
 * <ol>
 *   <li>先按"至多抽取一次"过滤：{@link Entry#once()} 为真、且这颗核心已经抽到过（位图记在
 *       {@code ModDataComponents.ORB_DRAWN} 上）的条目直接排除；</li>
 *   <li>再在剩下的条目里<b>按权重</b>抽（权重沿用幸运方块事件自己的 {@code LuckyEvent#weight()}，
 *       所以两边的手感一致）；</li>
 *   <li>事件返回 {@code false}（条件不满足，例如超出交互距离/坐着矿车）→ 不扣次数、<b>也不记入位图</b>，
 *       这次右键等于没发生；</li>
 *   <li>成功才把该条目标记进位图（仅当它是"至多一次"的）。</li>
 * </ol>
 *
 * <h2>条目的来源</h2>
 * 绝大部分直接复用<b>幸运方块抽取池</b>里现成的子事件（{@link LuckyEventRef} 按 id 调它的
 * {@code LuckyEvent#run}），所以"核心右击出什么"和"挖幸运方块出什么"是同一份实现、同一套配置，
 * 以后调平衡只改幸运事件那一处即可。少数几个是核心专属的新事件（见各自注释）。
 */
public final class OrbOfLuckEvents {

    /**
     * 已注册的幸运核心事件表。<b>顺序即 {@code ORB_DRAWN} 位图的下标</b>（往里插条目要留意老存档）。
     * <p>
     * 需求 1~10 的对应关系：
     * <ol>
     *   <li>金粒喷泉猪 → {@code entity/gold_nugget_pig}（至多一次）</li>
     *   <li>骑着蝙蝠的雪傀儡 → {@code entity/snow_golem_bat}（至多一次）</li>
     *   <li>骑着鸡的兔子 → {@code entity/rabbit_chicken}（至多一次）</li>
     *   <li>金马铠马 → {@code entity/golden_horse}（至多一次）★飞行+可操控待接（需求另述）</li>
     *   <li>64~128 个金锭 → {@code item/gold_ingot_burst}</li>
     *   <li>工作台帽子 → 直接给一件（幸运物品池物品）</li>
     *   <li>翅膀 → 直接给一件（至多一次）</li>
     *   <li>地狱疣生成器 → 直接给一件（至多一次）</li>
     *   <li>宝石喷泉 → {@code item/gem_scatter}（幸运方块的幸运物品事件）</li>
     *   <li>金苹果（原调试事件）保留，方便对照</li>
     * </ol>
     * 需求 8（{@code luck_roller_mini.nbt} 结构 + 铁轨上的矿车）与需求 9（坐矿车时给 100 m/s + 向后喷烟花）
     * 是核心专属新机制，还没接（结构文件在仓库里也还不存在）。
     */
    private static final List<Entry> EVENTS = List.of(
        Entry.once(new LuckyEventRef("entity/gold_nugget_pig")),
        Entry.once(new LuckyEventRef("entity/snow_golem_bat")),
        Entry.once(new LuckyEventRef("entity/rabbit_chicken")),
        Entry.once(OrbSpecialMechanics::spawnPilotHorse),   // 需求 3'：会飞、可操控的金马铠马
        Entry.of(new LuckyEventRef("item/gold_ingot_burst")),
        Entry.once(new GiveItemEvent(ModItems.CRAFTING_TABLE_HAT::get)),
        Entry.once(new GiveItemEvent(ModItems.WINGS::get)),
        Entry.once(new GiveItemEvent(ModItems.WART_ON_A_STICK::get)),
        Entry.of(new LuckyEventRef("item/gem_scatter")),
        // 需求 8：原地生成 luck_roller_mini 结构（取代被点方块）+ 随机铁轨上放一辆矿车。
        // 需求 9（坐矿车时 100 m/s + 向后喷彩色粒子）不在这张表里：它是"使用核心"的一条<b>优先分支</b>，
        // 由 OrbOfLuckItem 直接调 OrbSpecialMechanics#boostMinecart 抢在抽签之前处理。
        Entry.once(OrbSpecialMechanics::placeRollerMini),
        Entry.of(new GoldenAppleEvent())
    );

    /** 附体瞬间"向后弹开"的初速度：3 m/s。原版换算 1 m/s = 0.05 格/刻，故 3 × 0.05 = 0.15。 */
    private static final double POSSESSION_PUSHBACK_PER_TICK = 3.0D * 0.05D;

    /** 那一下附带的向上分量（格/刻）：让"被弹开"看得见，又不至于把人抛起来。 */
    private static final double POSSESSION_PUSHBACK_LIFT_PER_TICK = 0.08D;

    private OrbOfLuckEvents() {
    }

    /** 表里的一条：事件 + 权重 + 是否"至多抽取一次"。 */
    private record Entry(OrbOfLuckEvent event, float weight, boolean once) {
        static Entry of(OrbOfLuckEvent event) {
            return new Entry(event, 1.0F, false);
        }

        static Entry once(OrbOfLuckEvent event) {
            return new Entry(event, 1.0F, true);
        }
    }

    /** 这颗核心是不是已经抽到过第 {@code index} 条（位图在 {@code ORB_DRAWN} 上）。 */
    private static boolean alreadyDrawn(ItemStack stack, int index) {
        return (OrbOfLuckItem.getDrawnMask(stack) & (1 << index)) != 0;
    }

    /**
     * 抽一个事件执行。
     *
     * @return 事件是否真的执行了；{@code false} 表示条件不满足（例如交互范围之外）或可用条目已抽完，
     *         此时不扣次数
     */
    public static boolean trigger(UseOnContext context) {
        if (EVENTS.isEmpty()) {
            return false;
        }
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        // 1) 过滤掉"至多一次且已经出过"的
        List<Integer> pool = new java.util.ArrayList<>(EVENTS.size());
        float total = 0.0F;
        for (int i = 0; i < EVENTS.size(); i++) {
            Entry entry = EVENTS.get(i);
            if (entry.once() && alreadyDrawn(stack, i)) {
                continue;
            }
            pool.add(i);
            total += Math.max(0.0F, entry.weight());
        }
        if (pool.isEmpty() || total <= 0.0F) {
            return false;
        }
        // 2) 按权重抽（和 LuckyEvents 的抽签同一套口径）
        float roll = level.getRandom().nextFloat() * total;
        int chosen = pool.get(pool.size() - 1);
        for (int index : pool) {
            roll -= Math.max(0.0F, EVENTS.get(index).weight());
            if (roll < 0.0F) {
                chosen = index;
                break;
            }
        }
        // 3) 执行；没真的发生就当这次右键没发生（不扣次数、不记位图）
        Entry entry = EVENTS.get(chosen);
        if (!entry.event().trigger(context)) {
            return false;
        }
        if (entry.once()) {
            OrbOfLuckItem.markDrawn(stack, chosen);
        }
        return true;
    }

    /**
     * Orb Possession 事件：次数归零时触发。
     * <p>
     * 目前做这些事：聊天栏提示 → 在玩家坐标生成 POSSESSING 阶段的核心 → 核心再生成一具"玩家"身体
     * （皮肤=该玩家、头顶一颗光球），并把玩家转成旁观；玩家用命令切回创造模式即结束、核心消失。
     * 后续更复杂的内容按你的说明再加。
     */
    public static void triggerPossession(Player player) {
        player.displayClientMessage(
            Component.translatable("message.joes_addons_for_abmc.orb_possession"), false);

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        // 成就「贪婪的代价」：被幸运核心附体（隐藏倒计时到点、附体真正开始）即授予
        ModMain.awardAdvancement(serverPlayer, ModMain.GREED_ADV);
        // 先读出"这颗核心是从哪捡起来的"（写在物品的数据组件里，见 OrbOfLuckEntity#mobInteract）：
        // 母体被击败时核心要回到那个位置并恢复 INITIAL 阶段（用户指定）。
        // 必须在这里读 —— 下面会把物品清掉。
        // <b>在背包里找，不只看手</b>：核心不再锁死在主手（现在允许在背包里自由拖动），
        // 倒计时到点那一刻它可能在快捷栏/主背包/副手任意一格。
        ItemStack orbStack = OrbOfLuckItem.findInInventory(serverPlayer);
        net.minecraft.core.GlobalPos pickupSite =
            orbStack == null ? null : orbStack.get(ModDataComponents.ORB_PICKUP.get());
        // 身上的核心被这次附体"用掉"：倒计时已经走完，留着只会是一件锁死在身上、又没用的废品。
        // （如果你希望保留物品，把下面这两个循环删掉即可。）
        for (int i = 0; i < serverPlayer.getInventory().items.size(); i++) {
            if (serverPlayer.getInventory().items.get(i).is(ModItems.ORB_OF_LUCK.get())) {
                serverPlayer.getInventory().setItem(i, ItemStack.EMPTY);   // setItem 会顺手 setChanged，客户端跟着清
            }
        }
        for (InteractionHand hand : InteractionHand.values()) {
            if (serverPlayer.getItemInHand(hand).is(ModItems.ORB_OF_LUCK.get())) {
                serverPlayer.setItemInHand(hand, ItemStack.EMPTY);
            }
        }
        ServerLevel level = serverPlayer.serverLevel();
        OrbOfLuckEntity orb = ModEntities.ORB_OF_LUCK.get().create(level);
        if (orb == null) {
            return;
        }
        level.addFreshEntity(orb);
        orb.startPossession(serverPlayer, pickupSite);
        pushBackAfterPossession(serverPlayer);
    }

    /**
     * 附体发生后瞬间给玩家一个<b>向后</b>的初速度（用户指定 3 m/s）。
     * <p>
     * 数值换算是原版口径：<b>1 m/s = 0.05 格/刻</b>，所以 3 m/s = {@code 0.15} 格/刻。
     * "向后"取玩家当前朝向在水平面上的反方向（{@code getLookAngle()} 反过来，
     * 纵向分量丢掉再归一化）—— 这样玩家面朝哪边就朝哪边被推开，
     * 而不会像 {@code Entity#push} 那样（它推的是<b>远离某个实体</b>的方向，此处没有那个实体）。
     * <p>
     * 略微加一点向上分量（0.08 格/刻）：纯水平的推在"地面摩擦 + 附体瞬间"下几乎看不出位移，
     * 抬一下才有"被弹开"的观感，同时也不会把人抛到天上。
     * <p>
     * 保留玩家原本的竖直速度（下落中的玩家不会被这一下"定住"）。
     * 施加顺序放在 {@link OrbOfLuckEntity#startPossession(net.minecraft.server.level.ServerPlayer,
     * net.minecraft.core.GlobalPos)} <b>之后</b>：
     * 那时玩家已经被转成旁观，旁观者不受重力/摩擦影响，这个初速度能完整地飞完。
     */
    private static void pushBackAfterPossession(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 back = new Vec3(-look.x, 0.0D, -look.z);
        if (back.lengthSqr() < 1.0E-6D) {
            return;   // 正上/正下看：没有水平朝向可用，不推
        }
        back = back.normalize().scale(POSSESSION_PUSHBACK_PER_TICK);
        Vec3 movement = player.getDeltaMovement();
        player.setDeltaMovement(back.x, movement.y + POSSESSION_PUSHBACK_LIFT_PER_TICK, back.z);
        player.hurtMarked = true;   // 让客户端跟着这个速度（否则服务端推了、屏幕上不动）
    }

    /** 一个幸运核心事件。 */
    public interface OrbOfLuckEvent {
        /**
         * 执行事件。
         *
         * @return 是否真的执行了（{@code false} = 条件不满足，调用方不扣次数）
         */
        boolean trigger(UseOnContext context);
    }

    /**
     * <b>复用幸运方块事件</b>的条目（需求 6.5.25 里绝大多数都是这种）。
     * <p>
     * 按 id 取 {@code LuckyEvents.byId}，再调它的 {@link LuckyEvent#run}——所以"核心右击出什么"
     * 与"挖幸运方块出什么"是同一份实现：猫群、猪塔、金粒猪、雪傀儡骑蝙蝠、兔子骑鸡、金马、
     * 金锭喷发、宝石散射……以后它们改数值，核心这边自动跟着变。
     * <p>
     * 落点用"被点方块的相邻格"（{@code getClickedPos().relative(getClickedFace())}）：
     * 幸运方块那条传的是"方块自己那一格"（此时已被挖掉、是空气），这里给相邻的空气格最接近那个语义，
     * 免得把生物/掉落物塞进被点的实体方块里。
     */
    private record LuckyEventRef(String path) implements OrbOfLuckEvent {
        @Override
        public boolean trigger(UseOnContext context) {
            if (context.getLevel().isClientSide()) {
                return false;   // 真正做事只在服务端
            }
            Player player = context.getPlayer();
            if (player == null || !player.canInteractWithBlock(context.getClickedPos(), 1.0)) {
                return false;
            }
            LuckyEvent event = LuckyEvents.byId(LuckyEvents.id(path));
            if (event == null) {
                // 事件被删了/没注册（比如数据包动过）：当作没抽到，不扣次数
                ModMain.LOGGER.warn("[幸运核心] 事件 {} 不存在，这次右键作废", path);
                return false;
            }
            BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
            event.run((ServerLevel) context.getLevel(), pos, player);
            return true;
        }
    }

    /** 直接给一件物品的条目（工作台帽子 / 翅膀 / 地狱疣生成器——都是幸运物品池里的东西）。 */
    private record GiveItemEvent(java.util.function.Supplier<net.minecraft.world.item.Item> item)
            implements OrbOfLuckEvent {
        @Override
        public boolean trigger(UseOnContext context) {
            Player player = context.getPlayer();
            if (player == null || !player.canInteractWithBlock(context.getClickedPos(), 1.0)) {
                return false;
            }
            Level level = context.getLevel();
            if (level.isClientSide()) {
                return false;
            }
            // 与金苹果那条同一个落点写法：命中点沿被点面法线抬起 0.25 格
            Vec3 normal = new Vec3(
                context.getClickedFace().getStepX(),
                context.getClickedFace().getStepY(),
                context.getClickedFace().getStepZ()).scale(0.25);
            Vec3 point = context.getClickLocation().add(normal);
            ItemEntity drop = new ItemEntity(level, point.x, point.y, point.z, new ItemStack(item.get()));
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
            return true;
        }
    }

    /**
     * 调试事件：右击地面、且目标方块在交互范围之内时，在交互点生成一个金苹果掉落物。
     * <p>
     * 生成点用 {@link UseOnContext#getClickLocation()} 的精确命中点，再沿被点面的法线抬起 0.25 格，
     * 免得半个苹果卡在方块里。范围判定用原版的 {@code Player#canInteractWithBlock}（= 方块交互距离 + 1 格容错）。
     */
    private static final class GoldenAppleEvent implements OrbOfLuckEvent {
        @Override
        public boolean trigger(UseOnContext context) {
            Player player = context.getPlayer();
            if (player == null) {
                return false;
            }
            if (!player.canInteractWithBlock(context.getClickedPos(), 1.0)) {
                return false;
            }
            Level level = context.getLevel();
            if (level.isClientSide()) {
                return false; // 真正生成只在服务端做
            }
            Vec3 normal = new Vec3(
                context.getClickedFace().getStepX(),
                context.getClickedFace().getStepY(),
                context.getClickedFace().getStepZ()).scale(0.25);
            Vec3 point = context.getClickLocation().add(normal);
            ItemEntity apple = new ItemEntity(level, point.x, point.y, point.z,
                new ItemStack(Items.GOLDEN_APPLE));
            apple.setDefaultPickUpDelay();
            level.addFreshEntity(apple);
            return true;
        }
    }
}
