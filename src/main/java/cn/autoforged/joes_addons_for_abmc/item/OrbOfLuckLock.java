package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.config.ModConfig;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 「核心锁在主手」规则：玩家手里拿着 Orb of Luck 时，它再也离不开主手。
 *
 * <h2>规则</h2>
 * <ul>
 *   <li>主手拿着核心 → 记下它所在的热键栏槽位；</li>
 *   <li>之后不管用什么方式把选择格换走（滚轮 / 数字键 / 任何改变 {@code Inventory#selected} 的途径），
 *       下一 tick 立刻弹回该槽位；</li>
 *   <li>被 F 键换到副手 → 由 {@link #onSwapHands} 直接取消（{@code tick} 里还留了一层兜底）；</li>
 *   <li>丢弃由 {@link OrbOfLuckItem#onDroppedByPlayer} 拦下（原版 {@code ServerPlayer#drop} 会直接早退，
 *       物品根本不会离开背包）；<b>客户端还要单独拦一次</b>，因为 {@code LocalPlayer#drop} 是
 *       "先本地删、再发包"，只拦服务端会让两端状态分叉（见 {@code LocalPlayerOrbLockMixin}）；</li>
 *   <li>背包/容器里对它的点击由 {@code ContainerOrbLockMixin} 拦下（双端同一个 mixin，客户端不会本地预测、
 *       服务端也不会执行，两边状态一致）；</li>
 *   <li>缴械（铁块权杖）见 {@code ModMain#executeChainStaffAbility}，对核心直接跳过。</li>
 * </ul>
 *
 * <h2>创造模式</h2>
 * 创造模式下只有当配置 {@code debug.debug_mode} 为 true 时才锁；否则核心可以正常取下/丢弃，
 * 方便摆场景。规则集中在 {@link #lockAllowed}。
 *
 * <h2>为什么用 tick 弹回而不是拦按键</h2>
 * 1.21.1 的 {@code InputEvent.Key} 不可取消（只有滚轮事件可以），而改 {@code Inventory#selected} 的入口
 * 有好几个；统一在 {@link PlayerTickEvent.Post} 里校正最省事也最不容易漏。
 * 客户端 tick 顺序是「gameMode.tick → handleKeybinds → 实体/玩家 tick」，所以校正发生在
 * 同一 tick 内、渲染之前——既不会闪一下，也不会把错误的槽位发给服务端（{@code MultiPlayerGameMode}
 * 是在下一 tick 才比对并发包的）。服务端同样跑这段逻辑，防止有人绕过客户端。
 */
@EventBusSubscriber(modid = ModMain.MODID)
public final class OrbOfLuckLock {

    /** 被锁住的玩家 → 核心所在的热键栏槽位（0..8）。双端各维护一份。 */
    private static final Map<UUID, Integer> LOCKED_SLOT = new HashMap<>();

    private OrbOfLuckLock() {
    }

    /** 该玩家当前是否允许上锁（创造模式需要 debug 模式）。 */
    public static boolean lockAllowed(Player player) {
        return !player.isCreative() || ModConfig.DEBUG_MODE.get();
    }

    /** 主手正拿着核心、并且允许上锁 → 此刻处于锁定状态。 */
    public static boolean isLockedInHand(Player player) {
        return lockAllowed(player) && player.getMainHandItem().is(ModItems.ORB_OF_LUCK.get());
    }

    /** 玩家身上（主手 / 快捷栏 / 背包 / 副手）有没有核心。 */
    public static boolean carriesOrb(Player player) {
        return OrbOfLuckItem.findInInventory(player) != null;
    }

    /**
     * 「背包锁」是否生效：核心在背包里、且允许上锁。
     * <p>
     * 与 {@link #isLockedInHand} 的区别（用户指定）：核心<b>不在主手</b>时，允许它在背包里自由挪动，
     * 但<b>不许丢弃、也不许塞进任何容器</b>（箱子/漏斗/合成格……）。
     * 具体拦截见 {@code ContainerOrbLockMixin}（GUI 点击）与 {@link #isPlayerInventorySlot}。
     */
    public static boolean isCarriedLocked(Player player) {
        return lockAllowed(player) && carriesOrb(player);
    }

    /**
     * 这个格子是不是"玩家自己的背包"（快捷栏 / 主背包 / 盔甲 / 副手）。
     * <p>
     * 判据是"格子背后的容器就是玩家的 {@code Inventory}"：箱子/漏斗/合成格/交易格/铁砧输入格……
     * 背后的容器都不是它，于是都算"容器"—— 核心只允许待在玩家自己的背包格子里。
     */
    public static boolean isPlayerInventorySlot(net.minecraft.world.inventory.Slot slot) {
        return slot.container instanceof net.minecraft.world.entity.player.Inventory;
    }

    /**
     * "选中格丢弃"（Q 键）是否该被拦下：选中的那件就是核心、且允许上锁。
     * <p>客户端 {@code LocalPlayerOrbLockMixin} 与服务端 {@code OrbOfLuckItem#onDroppedByPlayer}
     * 用同一条判据，两端才不会分叉。
     */
    public static boolean dropBlocked(Player player) {
        return lockAllowed(player)
            && player.getInventory().getSelected().is(ModItems.ORB_OF_LUCK.get());
    }

    /** 该玩家被锁住的热键栏槽位（0..8）；没有锁时返回 -1。 */
    public static int lockedSlot(Player player) {
        Integer slot = LOCKED_SLOT.get(player.getUUID());
        return slot == null ? -1 : slot;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        tick(event.getEntity());
    }

    /**
     * F 键（交换主副手）拦截。
     * <p>
     * 服务端处理 {@code SWAP_ITEM_WITH_OFFHAND} 包时会先发这个可取消事件，取消掉就什么都不会发生
     * （没有 1 tick 的闪动，也不需要靠 tick 再换回来——{@link #tick} 里的兜底逻辑留着防别的途径）。
     */
    @SubscribeEvent
    public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (event.getEntity() instanceof Player player && isLockedInHand(player)) {
            event.setCanceled(true);
        }
    }

    /** 玩家下线：清掉记录，避免 UUID 复用/内存堆积。 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LOCKED_SLOT.remove(event.getEntity().getUUID());
    }

    private static void tick(Player player) {
        // 隐藏倒计时：到点直接触发附体（只服务端判一次就够）
        if (!player.level().isClientSide()) {
            tickCountdown(player);
        }
        UUID id = player.getUUID();
        Integer locked = LOCKED_SLOT.get(id);

        // 主手拿着核心：锁定当前槽位（创造模式未开 debug 则解锁）
        if (player.getMainHandItem().is(ModItems.ORB_OF_LUCK.get())) {
            if (lockAllowed(player)) {
                LOCKED_SLOT.put(id, player.getInventory().selected);
            } else {
                LOCKED_SLOT.remove(id);
            }
            return;
        }

        if (locked == null || !lockAllowed(player)) {
            LOCKED_SLOT.remove(id);
            return;
        }

        // 核心还在原来那一格 → 把选择格弹回去（滚轮/数字键/其它任何换法都在这里被纠正）
        ItemStack atLocked = player.getInventory().getItem(locked);
        if (atLocked.is(ModItems.ORB_OF_LUCK.get())) {
            player.getInventory().selected = locked;
            return;
        }

        // 被 F 键换到了副手 → 换回来
        if (player.getOffhandItem().is(ModItems.ORB_OF_LUCK.get())) {
            ItemStack off = player.getOffhandItem();
            ItemStack main = player.getInventory().getItem(locked);
            player.getInventory().setItem(locked, off);
            player.setItemInHand(InteractionHand.OFF_HAND, main);
            player.getInventory().selected = locked;
            return;
        }

        // 核心确实不在手上了（被清空、创造模式拿走等）→ 解锁
        LOCKED_SLOT.remove(id);
    }

    /**
     * <b>隐藏倒计时</b>的每刻检查（用户指定）：核心在身上、且到期时刻已经过了 → 触发附体。
     * <p>
     * 倒计时存的是"到期时刻"（见 {@link ModDataComponents#ORB_COUNTDOWN}），所以这里只做一次比较，
     * 不需要每刻回写组件。到期时 {@link OrbOfLuckEvents#triggerPossession} 会把核心从背包里收走
     * （不管它在哪个格子）并开始附体。
     * <p>
     * 旧存档里没有这个组件的核心：{@link OrbOfLuckItem#ensureCountdown} 会补一次"从现在起 1 分钟"，
     * 绝不因为读了个空组件就当场附体。
     */
    private static void tickCountdown(Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        ItemStack orb = OrbOfLuckItem.findInInventory(player);
        if (orb == null) {
            return;
        }
        long deadline = OrbOfLuckItem.ensureCountdown(player.level(), orb);
        if (player.level().getGameTime() >= deadline) {
            ModMain.LOGGER.info("[幸运核心] 隐藏倒计时到点（{} 刻），触发附体：{}",
                OrbOfLuckItem.COUNTDOWN_TICKS, serverPlayer.getGameProfile().getName());
            OrbOfLuckEvents.triggerPossession(player);
        }
    }
}
