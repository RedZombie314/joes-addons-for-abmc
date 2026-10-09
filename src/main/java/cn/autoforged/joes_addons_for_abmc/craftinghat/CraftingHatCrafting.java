package cn.autoforged.joes_addons_for_abmc.craftinghat;

import cn.autoforged.joes_addons_for_abmc.crafting.CraftingScan;
import cn.autoforged.joes_addons_for_abmc.crafting.CraftingVisuals;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 工作台帽子的「合成事件」本体（服务端）。
 *
 * <h3>流程</h3>
 * <ol>
 *   <li><b>检索</b>：按 {@link #CATEGORY_ORDER}（装备 → 红石 → 杂项 → 建筑方块）遍历页签；
 *       每个页签内按"配方书页面的先后"——用服务端同步给客户端的同一份顺序
 *       {@code RecipeManager#getOrderedRecipes()}，再按客户端 {@code ClientRecipeBook} 的分组规则
 *       （{@code group} 非空用 group，否则用配方 id）归并，所以顺序与玩家在页面上看到的一致；</li>
 *   <li>逐个配方尝试：<b>产物玩家已经拥有 → 跳过</b>；<b>材料不齐 → 跳过</b>
 *       （本次遍历不回头，要等下一次触发重新从头遍历）；第一个通过的配方就是本次要合成的东西；</li>
 *   <li><b>工具品质门槛</b>：本轮只要出现过"材料齐全"的更高品质工具
 *       （木 &lt; 石 &lt; 金 &lt; 铁 &lt; 钻石 &lt; 下界合金 &lt; 本模组巨型），
 *       低于该品质的工具全部跳过 —— 例如同时有 2 铁锭 2 钻石 2 木棍时，
 *       即使铁剑配方排在钻石剑前面，也只会合成钻石剑；钻石被消耗后本轮也不会回头做铁剑；</li>
 *   <li><b>取出材料</b>：按 9 宫格"从左到右、从上到下"逐格抽 1 个（形状配方 = 网格行优先，
 *       无序配方 = 配方列表顺序），抽出的栈保留原有组件；</li>
 *   <li><b>展示</b>：从玩家头部生成 {@code item_display} 实体，相邻两个相隔
 *       {@link #SPAWN_INTERVAL_TICKS} 刻生成，每个都在 {@link #FLIGHT_TICKS} 刻内<b>匀速</b>飞到触发点；</li>
 *   <li><b>到达</b>：全部到达后在该坐标产出合成结果（掉落物）；</li>
 *   <li><b>取消</b>：还在飞行途中松开右键 → 已取出的材料原样（含组件）掉在该坐标。</li>
 * </ol>
 * 按住右键可连续触发，冷却 {@link #COOLDOWN_TICKS} 游戏刻（服务端强制）。
 */
public final class CraftingHatCrafting {
    /** 按住右键连续触发的冷却（游戏刻）。 */
    public static final int COOLDOWN_TICKS = 15;
    /** 单个展示实体从头部匀速飞到触发点所需的时间（游戏刻）。 */
    public static final int FLIGHT_TICKS = 10;
    /** 相邻两个展示实体之间的生成间隔（游戏刻）。 */
    public static final int SPAWN_INTERVAL_TICKS = 2;
    /** 发射点相对玩家摄像机（眼位）的高度偏移：高 6 像素 = 6/16 格，即从头顶射出而不是从眼睛射出。 */
    public static final double LAUNCH_HEIGHT_OFFSET = 6.0D / 16.0D;

    /** 玩家 ID → 正在进行的合成作业（允许并发：15 刻冷却可能短于材料多的配方总飞行时长）。 */
    private static final Map<UUID, List<Job>> JOBS = new HashMap<>();
    /** 玩家 ID → 上次触发时的游戏刻（冷却用）。 */
    private static final Map<UUID, Integer> LAST_TRIGGER = new HashMap<>();
    /**
     * 玩家 ID → <b>本轮遍历已经合成过的配方</b>。
     * <p>作用：避免同一个配方被反复合成（例如只有一堆钻石、没有木棍时，
     * 可做的只有几件钻石防具，若不做记录就会每次都挑中页面上排在最前的同一件）。
     * <p>一整轮（装备 → 红石 → 杂项 → 建筑方块 全部翻完）都没找到可合成的东西时，
     * 说明本轮遍历结束 —— 清空记录，下一次触发重新从「装备」页签开始。
     * <p>{@code /jafa resetrecipe} 可以手动立即清空它。
     */
    private static final Map<UUID, Set<ResourceLocation>> TRAVERSAL_DONE = new HashMap<>();
    /**
     * 玩家 ID → <b>本轮遍历的"工具品质门槛"</b>。
     * <p>规则：只要本轮出现过"材料齐全"的更高品质工具（木 &lt; 石 &lt; 金 &lt; 铁 &lt; 钻石 &lt; 下界合金），
     * 那么<b>低于该品质的工具全部跳过</b>（铁剑不会在钻石剑之后被合成，哪怕它的配方排在钻石剑前面）。
     * <p>门槛在本轮遍历内<b>只升不降</b>：所以即使钻石在合成钻石剑时被消耗掉了，
     * 本轮后续也不会掉回头去做铁剑。遍历结束（或 {@code /jafa resetrecipe}）时归零。
     */
    private static final Map<UUID, Integer> TRAVERSAL_TOOL_TIER = new HashMap<>();

    private CraftingHatCrafting() {
    }

    // ============================ 入口 ============================

    /**
     * 尝试在 {@code target} 处开始一次合成事件。
     * <p>冷却未到、或翻遍四个页签都没有可合成的配方时什么都不做。
     * <p>触发即进入冷却（无论是否找到配方），避免按住右键时每刻都全量翻配方。
     */
    public static void tryStart(ServerPlayer player, Vec3 target) {
        int now = player.server.getTickCount();
        UUID id = player.getUUID();
        Integer last = LAST_TRIGGER.get(id);
        if (last != null && now - last < COOLDOWN_TICKS) return;
        LAST_TRIGGER.put(id, now);

        List<RecipeHolder<?>> ordered = CraftingScan.orderedRecipes(player);
        CraftingScan.Source source = CraftingScan.inventorySource(player);
        Set<ResourceLocation> done = TRAVERSAL_DONE.computeIfAbsent(id, k -> new HashSet<>());
        // 品质门槛（工具 + 盔甲，只在已有门槛基础上往上升，见 CraftingScan#bestCraftableQualityTier）
        int gate = CraftingScan.bestCraftableQualityTier(ordered, source, player.level().registryAccess(),
            TRAVERSAL_TOOL_TIER.getOrDefault(id, 0));
        TRAVERSAL_TOOL_TIER.put(id, gate);

        CraftingScan.Pick pick = CraftingScan.find(ordered, source, player.level().registryAccess(), done, gate,
            product -> CraftingScan.inventoryOwns(player, product));
        if (pick == null) {
            // 一整轮翻完都没找到 → 本轮遍历结束，记录与门槛清空，下次触发从头再来
            done.clear();
            TRAVERSAL_TOOL_TIER.remove(id);
            return;
        }

        // 真正取出材料（每格 1 个；栈保留组件，用于展示、以及取消时掉落）
        List<ItemStack> taken = new ArrayList<>();
        for (CraftingScan.Reserved reserved : pick.reserved()) {
            taken.add(reserved.preview().copy());
        }
        CraftingScan.consumeDescending(source, pick.reserved());
        if (taken.isEmpty()) return;
        player.inventoryMenu.broadcastChanges();

        JOBS.computeIfAbsent(id, k -> new ArrayList<>())
            .add(new Job((ServerLevel) player.level(), target,
                player.getEyePosition().add(0.0D, LAUNCH_HEIGHT_OFFSET, 0.0D), pick.product(), taken));
    }

    /** 玩家松开右键：取消他所有"还在飞行中"的合成事件（材料原样掉在触发点）。 */
    public static void cancel(ServerPlayer player) {
        cancelAll(player.getUUID());
    }

    /** 玩家退出等：清掉他的作业（材料掉在原地）、冷却记录与遍历历史。 */
    public static void discard(UUID playerId) {
        cancelAll(playerId);
        LAST_TRIGGER.remove(playerId);
        TRAVERSAL_DONE.remove(playerId);
        TRAVERSAL_TOOL_TIER.remove(playerId);
    }

    /**
     * 重置某个玩家的遍历历史（{@code /jafa resetrecipe}）：
     * 下一次触发会重新从「装备」页签开始遍历，工具品质门槛也回到最低。
     *
     * @return 被清掉的记录条数（用于命令反馈）
     */
    public static int resetTraversal(UUID playerId) {
        TRAVERSAL_TOOL_TIER.remove(playerId);
        Set<ResourceLocation> done = TRAVERSAL_DONE.remove(playerId);
        return done == null ? 0 : done.size();
    }

    /** 当前遍历进度：本轮已经合成过的配方数（用于命令反馈）。 */
    public static int traversalProgress(UUID playerId) {
        Set<ResourceLocation> done = TRAVERSAL_DONE.get(playerId);
        return done == null ? 0 : done.size();
    }

    private static void cancelAll(UUID playerId) {
        List<Job> jobs = JOBS.remove(playerId);
        if (jobs == null) return;
        for (Job job : jobs) {
            job.cancelAndDrop();
        }
    }

    /** 每个服务端刻推进所有作业。 */
    public static void tick() {
        if (JOBS.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, List<Job>>> it = JOBS.entrySet().iterator(); it.hasNext(); ) {
            List<Job> jobs = it.next().getValue();
            for (Iterator<Job> jt = jobs.iterator(); jt.hasNext(); ) {
                Job job = jt.next();
                job.advance();
                if (job.finished) jt.remove();
            }
            if (jobs.isEmpty()) it.remove();
        }
    }

    // ============================ 作业 ============================

    /** 一次进行中的合成事件。 */
    private static final class Job {
        private final ServerLevel level;
        private final Vec3 target;
        private final ItemStack product;
        private final List<Flight> flights = new ArrayList<>();
        private int age;
        private int spawned;
        private int arrived;
        private boolean finished;

        Job(ServerLevel level, Vec3 target, Vec3 from, ItemStack product, List<ItemStack> taken) {
            this.level = level;
            this.target = target;
            this.product = product;
            for (int i = 0; i < taken.size(); i++) {
                this.flights.add(new Flight(taken.get(i), from, i));
            }
        }

        void advance() {
            // 1) 按间隔生成展示实体（第 0、2、4… 刻各一个），每发射一件播一次物品吸收音效
            while (spawned < flights.size() && age >= spawned * SPAWN_INTERVAL_TICKS) {
                Flight flight = flights.get(spawned);
                flight.display = createDisplay(level, flight.stack, flight.from);
                level.addFreshEntity(flight.display);
                CraftingVisuals.playAbsorbSound(level, flight.from);
                spawned++;
            }

            // 2) 匀速飞向目标：每个实体各在 FLIGHT_TICKS 刻内走完全程
            for (Flight flight : flights) {
                if (flight.display == null || flight.arrived) continue;
                int flown = age - flight.index * SPAWN_INTERVAL_TICKS;
                float progress = Mth.clamp(flown / (float) FLIGHT_TICKS, 0.0F, 1.0F);
                Vec3 pos = flight.from.lerp(target, progress);
                flight.display.setPos(pos.x, pos.y, pos.z);
                if (progress >= 1.0F) {
                    flight.display.discard();
                    flight.arrived = true;
                    arrived++;
                }
            }

            // 3) 全部到位 → 在触发点产出合成结果（材料在开始时已取出，这里不再扣）
            if (spawned == flights.size() && arrived == flights.size()) {
                produce();
                finished = true;
                return;
            }
            age++;
        }

        private void produce() {
            if (product.isEmpty()) return;
            spawnDrop(product.copy());
        }

        /** 取消：清掉还在飞的展示实体，并把已取出的材料原样（含组件）掉在触发点。 */
        void cancelAndDrop() {
            if (finished) return;
            for (Flight flight : flights) {
                if (flight.display != null && !flight.arrived) {
                    flight.display.discard();
                }
            }
            for (Flight flight : flights) {
                spawnDrop(flight.stack.copy());
            }
            finished = true;
        }

        private void spawnDrop(ItemStack stack) {
            ItemEntity item = new ItemEntity(level, target.x, target.y, target.z, stack);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }

        /** 一个飞行中的材料（index = 它是第几个生成的，用来算生成时刻）。 */
        private static final class Flight {
            final ItemStack stack;
            final Vec3 from;
            final int index;
            Display.ItemDisplay display;
            boolean arrived;

            Flight(ItemStack stack, Vec3 from, int index) {
                this.stack = stack;
                this.from = from;
                this.index = index;
            }
        }

        /**
         * 生成一个展示该物品的 {@code item_display} 实体（本体在 {@link CraftingVisuals}，与工作台权杖共用）。
         */
        private static Display.ItemDisplay createDisplay(ServerLevel level, ItemStack stack, Vec3 at) {
            return CraftingVisuals.spawnFlyingItem(level, stack, at);
        }
    }
}
