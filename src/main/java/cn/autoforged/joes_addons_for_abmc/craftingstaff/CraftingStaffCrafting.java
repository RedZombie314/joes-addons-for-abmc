package cn.autoforged.joes_addons_for_abmc.craftingstaff;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.crafting.CraftingScan;
import cn.autoforged.joes_addons_for_abmc.crafting.CraftingVisuals;
import cn.autoforged.joes_addons_for_abmc.item.StaffItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 工作台权杖的「合成事件」（服务端）。
 *
 * <h3>一次右键 = 一个不可中断的会话</h3>
 * <ol>
 *   <li><b>吸收</b>：以玩家所在位置为球心、半径 {@link #RANGE} 的球体内的
 *       <b>掉落物实体</b>，每 {@link #ABSORB_INTERVAL_TICKS} 游戏刻按"由近到远"吸收一个
 *       （整叠一次性吸收，含组件），被吸收的实体立刻变为不可拾取，并在
 *       {@link #ABSORB_FLIGHT_TICKS} 游戏刻内飞向「权杖起点」后消失；
 *       <b>中途松开右键则立刻停止继续吸收</b>，直接用已吸收的材料进入合成阶段；</li>
 *   <li><b>合成</b>：吸收阶段结束后，以权杖里的物品池当材料，每 {@link #CRAFT_INTERVAL_TICKS} 游戏刻
 *       合成一件（检索顺序/跳过规则与工作台帽子完全一致，共用 {@link CraftingScan}）；
 *       每合成一次消耗 1 点耐久（{@code hurtStaff} → 优先扣方块耐久）；</li>
 *   <li><b>喷发</b>：当某一次遍历什么都做不出来时，把<b>产物 + 池中所有剩余物</b>在「权杖起点」
 *       一次性全部喷出：随机水平方向、与水平面 {@link #EJECT_ANGLE_DEGREES} 度夹角、
 *       速度 = 原版玩家扔出物品的速度 {@link #EJECT_SPEED}；</li>
 *   <li><b>兜底</b>：玩家掉线/换维度、权杖丢失或方块耐久耗尽时，一律把池中物品原地喷出，绝不吞物品。</li>
 * </ol>
 *
 * <p>「权杖起点」与红石激光的发射点同一个位置：
 * {@link ModMain#applyLineEmitterOffset(net.minecraft.world.entity.LivingEntity, Vec3)}
 * （即配置项 {@code line_converge_offset}，随玩家头部朝向旋转，会跟着玩家移动）。
 */
public final class CraftingStaffCrafting {
    /** 作用半径：以玩家所在位置为球心的球体半径。 */
    public static final double RANGE = 7.0D;
    /** 每多少个游戏刻吸收一个掉落物实体。 */
    public static final int ABSORB_INTERVAL_TICKS = 1;
    /** 被吸收的实体飞向权杖起点并消失所需的时间（游戏刻）。 */
    public static final int ABSORB_FLIGHT_TICKS = 10;
    /** 每多少个游戏刻合成一次（比工作台帽子快得多）。 */
    public static final int CRAFT_INTERVAL_TICKS = 2;
    /** 喷发速度 = 原版玩家扔出物品的速度。 */
    public static final double EJECT_SPEED = 0.3D;
    /** 喷发时水平方向的散开范围：玩家视角 yaw ±这个角度。 */
    public static final float EJECT_YAW_SPREAD_DEGREES = 45.0F;
    /** 喷发的仰角下限（相对水平面，负值朝下）。 */
    public static final float EJECT_ELEVATION_MIN_DEGREES = -30.0F;
    /** 喷发的仰角上限（相对水平面，正值朝上）。 */
    public static final float EJECT_ELEVATION_MAX_DEGREES = 20.0F;

    /** 玩家 ID → 正在进行的会话（每个玩家同时只有一个，进行中不可重入）。 */
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private CraftingStaffCrafting() {
    }

    /** 是否有正在进行的会话（进行中时再次右键不响应）。 */
    public static boolean isBusy(UUID playerId) {
        return SESSIONS.containsKey(playerId);
    }

    /**
     * 右键工作台权杖：尝试开始一次会话。
     *
     * @return 是否真的开始（作用范围内没有掉落物、或已有会话在进行时返回 {@code false}）
     */
    public static boolean start(ServerPlayer player) {
        UUID id = player.getUUID();
        if (SESSIONS.containsKey(id)) return false; // 不可中断：进行中不响应新的右键
        ServerLevel level = player.serverLevel();
        // 范围内没有任何掉落物 → 不开始（免得空转一轮）
        if (findNearest(level, player, Set.of()) == null) return false;

        Session session = new Session(id, level);
        session.lastOrigin = emitterOrigin(player);
        SESSIONS.put(id, session);
        return true;
    }

    /** 每个服务端刻推进所有会话。 */
    public static void tick() {
        if (SESSIONS.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator(); it.hasNext(); ) {
            Session session = it.next().getValue();
            session.advance();
            if (session.finished) it.remove();
        }
    }

    /** 玩家掉线等：把池中物品原地喷出（绝不吞物品）。 */
    public static void discard(UUID playerId) {
        Session session = SESSIONS.remove(playerId);
        if (session != null) {
            session.flushEverything();
        }
    }

    /** 是否有正在进行的会话。 */
    public static boolean hasSession(net.minecraft.world.entity.player.Player player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /**
     * 会话进行中再次右键：<b>重新打开吸收阶段</b>，把范围内新出现的掉落物继续吸进同一个池子。
     * <p>关键：<b>不重置遍历过程</b> —— {@code done}（本轮已做过的配方）与品质门槛原样保留，
     * 只是多了材料，所以合成会从上次中断的地方继续。遍历归零只发生在会话结束（喷发完）后重新开始时。
     */
    public static void absorbMore(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        session.crafting = false;
        session.absorbStopped = false;
        session.timer = 0;
    }

    /**
     * 玩家中途松开右键：<b>不再继续吸收</b>新的掉落物，直接用已经吸进池子的材料进入合成阶段。
     * <p>合成流程本身仍然不可中断（照常合成、照常喷发）；吸收阶段已经结束或会话不存在时是空操作。
     */
    public static void stopAbsorbing(net.minecraft.world.entity.player.Player player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            session.absorbStopped = true;
        }
    }

    // ============================ 工具方法 ============================

    /**
     * 作用范围内是否已经有可吸收的掉落物。
     * <p>客户端用它来预测"要不要进入拉弓状态"（"使用中"是客户端预测的状态，服务端单独调用
     * {@code startUsingItem} 玩家自己看不到姿势）；服务端仍然以 {@link #start} 的判定为准。
     */
    public static boolean hasItemInRange(net.minecraft.world.level.Level level,
                                         net.minecraft.world.entity.player.Player player) {
        return findNearest(level, player, Set.of()) != null;
    }

    /** 「权杖起点」：与红石激光发射点同一个位置（配置的汇聚偏移，随头部朝向旋转）。 */
    private static Vec3 emitterOrigin(ServerPlayer player) {
        return ModMain.applyLineEmitterOffset(player, player.getEyePosition());
    }

    /** 作用范围内离玩家最近、且还没被本次会话认领的掉落物（整叠非空）。 */
    @Nullable
    private static ItemEntity findNearest(net.minecraft.world.level.Level level,
                                          net.minecraft.world.entity.player.Player player,
                                          Set<UUID> claimed) {
        Vec3 center = player.getBoundingBox().getCenter();
        double rangeSq = RANGE * RANGE;
        AABB box = player.getBoundingBox().inflate(RANGE);
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (!entity.isAlive() || claimed.contains(entity.getUUID())) continue;
            if (entity.getItem().isEmpty()) continue;
            double dist = entity.position().distanceToSqr(center);
            if (dist > rangeSq || dist >= bestDist) continue;
            best = entity;
            bestDist = dist;
        }
        return best;
    }

    /** 玩家身上（主手 → 副手 → 背包）正在生效的工作台权杖。 */
    @Nullable
    private static StaffRef findStaff(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (isCraftingStaff(main)) return new StaffRef(main, EquipmentSlot.MAINHAND);
        ItemStack off = player.getOffhandItem();
        if (isCraftingStaff(off)) return new StaffRef(off, EquipmentSlot.OFFHAND);
        for (int i = 0; i < CraftingScan.INVENTORY_SIZE; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isCraftingStaff(stack)) return new StaffRef(stack, EquipmentSlot.MAINHAND);
        }
        return null;
    }

    private static boolean isCraftingStaff(ItemStack stack) {
        return stack.getItem() instanceof StaffItem
            && "crafting_table".equals(stack.getOrDefault(
                cn.autoforged.joes_addons_for_abmc.item.ModDataComponents.BLOCKTYPE.get(), "empty"));
    }

    /** 权杖栈 + 它所在的装备槽。 */
    private record StaffRef(ItemStack stack, EquipmentSlot slot) {
    }

    // ============================ 会话 ============================

    private static final class Session {
        private final UUID playerId;
        private ServerLevel level;
        /** 权杖里的物品池：吸收来的材料 + 已经合成出来的产物，最后一起喷出。 */
        private final List<ItemStack> pool = new ArrayList<>();
        /** 本轮遍历已经合成过的配方（避免同一配方被反复合成）。 */
        private final Set<ResourceLocation> done = new HashSet<>();
        /** 正在做飞行动画的掉落物实体。 */
        private final List<Flight> flights = new ArrayList<>();
        /** 已被本次会话认领的掉落物实体（避免重复吸收/重复入池）。 */
        private final Set<UUID> claimed = new HashSet<>();
        private Vec3 lastOrigin = Vec3.ZERO;
        /** 最后一刻的玩家视角朝向（喷发方向的基准 yaw；玩家掉线时兜底用）。 */
        private float lastYaw;
        private int gate;
        private int timer;
        private boolean crafting;
        /** 玩家中途松开了右键：不再吸收新掉落物，直接进入合成阶段。 */
        private boolean absorbStopped;
        private boolean finished;

        Session(UUID playerId, ServerLevel level) {
            this.playerId = playerId;
            this.level = level;
        }

        void advance() {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player == null || player.isRemoved()) {
                flushEverything(); // 玩家不在了：原地喷出
                return;
            }
            if (player.level() != level) {
                // 换维度：飞行中的那些还没到终点，直接变回掉落物实体留在原维度（绝不吞物品）
                returnFlightsToWorld();
                level = player.serverLevel();
            }
            Vec3 origin = emitterOrigin(player);
            lastOrigin = origin;
            lastYaw = player.getYRot();
            // 让飞行中的展示实体掉头追向当前的权杖起点；到达终点（10 刻）才计入权杖数据
            for (Iterator<Flight> it = flights.iterator(); it.hasNext(); ) {
                Flight flight = it.next();
                flight.elapsed++;
                if (flight.elapsed >= ABSORB_FLIGHT_TICKS || !flight.display.isAlive()) {
                    flight.display.discard();
                    it.remove();
                    addToPool(flight.stack);
                    // 吸收"结束"（飞到权杖起点、真正计入权杖数据）时才播物品吸收音效
                    CraftingVisuals.playAbsorbSound(level, origin);
                    continue;
                }
                double progress = flight.elapsed / (double) ABSORB_FLIGHT_TICKS;
                Vec3 pos = flight.from.lerp(origin, progress);
                flight.display.setPos(pos.x, pos.y, pos.z);
            }

            if (!crafting) {
                absorbStep(player);
                return;
            }
            craftStep(player);
        }

        /** 吸收阶段：每 1 刻认领范围内最近的一个掉落物，让它飞向权杖起点（到终点才计入权杖数据）。 */
        private void absorbStep(ServerPlayer player) {
            if (timer % ABSORB_INTERVAL_TICKS == 0 && !absorbStopped) {
                ItemEntity next = findNearest(level, player, claimed);
                if (next != null) {
                    claimed.add(next.getUUID());
                    // 真实掉落物当场抹掉，只留一个 item_display 做飞行动画
                    // （避免这 10 刻里被漏斗吸走/被岩浆烧掉导致复制）；物品暂存在 Flight 里，
                    // 飞到终点才 addToPool —— 这样中途结束只需把它变回掉落物实体即可，不会吞也不会复制。
                    ItemStack stack = next.getItem().copy();
                    Vec3 from = next.position();
                    next.discard();
                    flights.add(new Flight(CraftingVisuals.spawnFlyingItem(level, stack.copy(), from), from, stack));
                }
            }
            timer++;

            // 松手后不再等飞行播完：还在飞的直接变回掉落物实体（40 刻捡起冷却），用已到终点的材料开始合成
            boolean more = !absorbStopped && findNearest(level, player, claimed) != null;
            if (!more && (flights.isEmpty() || absorbStopped)) {
                if (absorbStopped) returnFlightsToWorld();
                // 吸收完毕（或中途停吸）→ 更新一次品质门槛，进入合成阶段。
                // 门槛传入的是当前值 → 只在已有门槛基础上往上升，不会因为材料被消耗掉而掉档，
                // 因此"会话途中再次右键补充吸收"不会重置遍历、也不会掉回头去做低级装备。
                crafting = true;
                timer = 0;
                List<RecipeHolder<?>> ordered = CraftingScan.orderedRecipes(player);
                gate = CraftingScan.bestCraftableQualityTier(ordered, CraftingScan.poolSource(pool),
                    level.registryAccess(), gate);
                if (pool.isEmpty()) {
                    finished = true;
                }
            }
        }

        /** 合成阶段：每 2 刻合成一件；某轮什么都做不出来 → 全部喷出并结束。 */
        private void craftStep(ServerPlayer player) {
            timer++;
            if (timer % CRAFT_INTERVAL_TICKS != 0) return;

            List<RecipeHolder<?>> ordered = CraftingScan.orderedRecipes(player);
            CraftingScan.Source source = CraftingScan.poolSource(pool);
            CraftingScan.Pick pick = CraftingScan.find(ordered, source, level.registryAccess(), done, gate,
                product -> CraftingScan.inventoryOwns(player, product) || CraftingScan.poolOwns(pool, product));
            if (pick == null) {
                // 一轮遍历结束 → 产物 + 剩余物一次性全部喷出
                eject(player);
                return;
            }
            CraftingScan.consumeDescending(source, pick.reserved());
            addToPool(pick.product());

            // 每合成一次损失 1 点耐久（hurtStaff → 优先消耗方块耐久）
            StaffRef staff = findStaff(player);
            if (staff == null) return; // 权杖不在身上：流程不中断，只是没地方扣耐久
            ModMain.hurtStaff(staff.stack(), 1, player, staff.slot());
            if (!isCraftingStaff(staff.stack())) {
                // 方块耐久耗尽（权杖被打回空权杖）→ 立刻收尾，把东西喷出来
                eject(player);
            }
        }

        /** 在权杖起点把池中所有东西喷出，然后结束会话（顺带收弓，结束拉弓动画）。 */
        private void eject(ServerPlayer player) {
            Vec3 origin = emitterOrigin(player);
            lastOrigin = origin;
            lastYaw = player.getYRot();
            returnFlightsToWorld(); // 兜底：正常情况下合成阶段已经没有被中断的飞行体了
            spray(origin);
            player.stopUsingItem();
            finished = true;
        }

        /** 兜底（玩家掉线/消失/换维度）：还没到终点的变回掉落物，池中物品用最后记录的起点喷出。 */
        void flushEverything() {
            returnFlightsToWorld();
            if (!pool.isEmpty()) {
                spray(lastOrigin);
            }
            finished = true;
        }

        /**
         * 把还在飞的展示实体变回掉落物实体（落在它当前所在位置，40 游戏刻捡起冷却）。
         * <p>这些物品<b>没有</b>计入权杖数据（到终点才计数），所以变回掉落物既不吞也不复制。
         * 每次会话结束（喷发 / 掉线 / 换维度）都必须走这里，否则展示实体会永远停在空中。
         */
        private void returnFlightsToWorld() {
            for (Flight flight : flights) {
                Vec3 pos = flight.display.position();
                flight.display.discard();
                ItemEntity item = new ItemEntity(level, pos.x, pos.y, pos.z, flight.stack.copy());
                item.setPickUpDelay(40); // 与玩家手动扔出物品一致的捡起冷却
                level.addFreshEntity(item);
            }
            flights.clear();
        }

        /**
         * 一次性全部喷出：<b>主要朝玩家视角前方</b> —— 水平方向在视角 yaw 的 ±45° 内随机，
         * 竖直仰角在 -30°~+20° 内随机（仰角相对水平面，正值朝上）；
         * 速度 = 原版玩家扔出物品的速度（0.3），起点仍是「权杖起点」。
         * <p>产物与剩余物<b>一律按物品丢出</b>（曾实现过"方块产物喷成下落方块实体"，效果不理想已回退）。
         */
        private void spray(Vec3 origin) {
            for (ItemStack stack : pool) {
                if (stack.isEmpty()) continue;
                // 起点同一处稍微散开一点，喷出来才像"四处喷出"而不是叠在一个点上
                ItemEntity item = new ItemEntity(level,
                    origin.x + jitter(), origin.y + jitter(), origin.z + jitter(), stack.copy());
                item.setDeltaMovement(randomEjectDirection().scale(EJECT_SPEED));
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
            }
            pool.clear();
        }

        /** 起点处的随机小抖动，避免所有东西叠在一个点上。 */
        private double jitter() {
            return (level.random.nextDouble() - 0.5) * 0.3;
        }

        /** 随机喷发方向：水平在玩家视角 yaw ±45°，仰角 -30°~+20°（原版 directionFromRotation 的 pitch 正值朝下）。 */
        private Vec3 randomEjectDirection() {
            float yaw = lastYaw + (level.random.nextFloat() * 2.0F - 1.0F) * EJECT_YAW_SPREAD_DEGREES;
            float elevation = EJECT_ELEVATION_MIN_DEGREES
                + level.random.nextFloat() * (EJECT_ELEVATION_MAX_DEGREES - EJECT_ELEVATION_MIN_DEGREES);
            return Vec3.directionFromRotation(-elevation, yaw);
        }

        /** 入池：能并进已有同类栈就并，否则新开一条（含组件比较）。 */
        private void addToPool(ItemStack stack) {
            if (stack.isEmpty()) return;
            for (ItemStack existing : pool) {
                if (ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() + stack.getCount() <= existing.getMaxStackSize()) {
                    existing.grow(stack.getCount());
                    return;
                }
            }
            pool.add(stack.copy());
        }
    }

    /** 一个正在做飞行动画的物品展示实体（从 from 出发，ABSORB_FLIGHT_TICKS 刻内飞到权杖起点）。 */
    private static final class Flight {
        final net.minecraft.world.entity.Display.ItemDisplay display;
        final Vec3 from;
        /** 这一叠物品本体：到达终点才计入权杖数据；中途结束则变回掉落物实体。 */
        final ItemStack stack;
        int elapsed;

        Flight(net.minecraft.world.entity.Display.ItemDisplay display, Vec3 from, ItemStack stack) {
            this.display = display;
            this.from = from;
            this.stack = stack;
        }
    }
}
