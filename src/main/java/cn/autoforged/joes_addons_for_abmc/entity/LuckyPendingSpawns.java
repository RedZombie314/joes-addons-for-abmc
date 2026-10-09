package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.block.LuckyRosterEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <b>幸运维度的"待生成位"刷新系统</b>（需求 6.5.23）。
 *
 * <h2>它解决什么</h2>
 * 原先 {@link LuckyMobSpawner} / {@link LuckyItemSpawner} 是"够密度就生成实体"，
 * 实体一旦生成就<b>一直活着</b>——而 {@code simulationDistance: 32} 意味着玩家能让 512 格内的区块
 * 全部保持加载，于是这些生物/掉落物全都在逐刻 tick（AI、寻路、碰撞、物品合并、实体追踪发包），
 * 存档里也躺着成千上万条实体记录（实测幸运维度有 22721 件掉落物）。
 *
 * <h2>做法（"给实体做 LOD"）</h2>
 * 每个区块地表登记 1~2 个<b>待生成位</b>（本类），位本身只是一条坐标 + 载荷记录，<b>不占实体</b>；
 * <ul>
 *   <li>玩家进到 <b>50 格</b>内 → <b>解除</b>：按位生成实体（随机生物 / 随机物品）；</li>
 *   <li>最近的玩家超出 <b>56 格</b> → <b>收回</b>：把实体的 NBT 存回位里再把它从世界删掉
 *       （滞回 6 格，免得站在边界上实体反复出现又消失）。</li>
 * </ul>
 * 于是"活着的实体"只存在于玩家 50 格气泡内，远处只剩几千条几十字节的位记录。
 *
 * <h2>为什么<b>不</b>复用变形表（{@code BLOCK_TRANSMUTATIONS}）</h2>
 * 虽然"空气 = 没实体"是同一个思路，但那张表有两个硬伤：① 它每服务端刻对每个维度<b>全表遍历</b>
 * （{@code ModMain#handleTransmutationTick}）；② 每条都要 {@code level.getBlockState(pos)}，
 * 而 {@code Level#getBlockState} 对未加载区块会<b>同步加载区块</b>（Level.java:406 → 201 → 207）。
 * 几千个位就是几千次区块加载/秒。那张表能那么写只因为它的条目少、且永远在已加载区块里。
 * 本类因此自带一张"只比坐标、不读方块"的轻表。
 *
 * <h2>只收编自己刷出来的东西（需求给的排除项）</h2>
 * <ul>
 *   <li><b>玩家丢出去的物品</b>：它们不是 {@link LuckyItemEntity}、也没打标记，本类一律不碰
 *       （收回只按"实体身上的位标记"认人）；</li>
 *   <li><b>结构里的生物（幸运核心等）</b>：那是刷怪笼/结构放的、同样没有任何标记，永不进入本系统；
 *       {@link #release} 里还有一道显式保险；</li>
 *   <li><b>与玩家互动过的</b>（被玩家打了、被右击交互、或它打了玩家）：{@link InteractionRelease}
 *       立刻把标记摘掉，从此它就是一具普通实体，<b>再也不会被收回</b>（位本身仍留在表里，
 *       以后玩家再来时会在原位重新解除一只新的）。</li>
 * </ul>
 *
 * <h2>开销</h2>
 * 每刻只做三件便宜事：① 每 20 刻扫一次本维度实体，把"带标记却没登记"的补登记（读档/重登/事件刷出）：
 * ② 遍历位表做距离比较（<b>不读方块</b>），对远处的在场实体收回；③ 对"空着 + 玩家 50 格内 +
 * 区块已加载"的位按每刻预算解除（{@link #MAX_MATERIALIZE_PER_TICK} / {@link #MAX_RETRACT_PER_TICK}）。
 */
public class LuckyPendingSpawns extends SavedData {

    // ==================== 需求参数 ====================

    /** 解除变形（生成实体）的半径：需求给的 50 格。 */
    public static final double ACTIVATE_RADIUS = 50.0D;

    /** 收回（变回"空气"）的半径：50 + 6 格滞回，避免边界抖动。 */
    public static final double RETRACT_RADIUS = 56.0D;

    /** 每区块登记几个位：沿用原来的密度（1.68 生物 + 0.43 物品 ≈ 2.1 位/区块）。 */
    public static final int SLOTS_PER_CHUNK = 2;

    /** 位的类型权重：生物 : 物品 = 4 : 1（沿用原来的 1.68 : 0.43）。 */
    public static final int MOB_WEIGHT = 4;
    public static final int ITEM_WEIGHT = 1;

    /** 生物位里"幸运生物事件（猫群/猪塔/女巫+恶魂/骷髅骑士/狼）"的权重，与 {@link LuckyMobSpawner} 的 1:4 对齐。 */
    public static final int LUCKY_CREATURE_WEIGHT = 1;
    public static final int NORMAL_MOB_WEIGHT = 4;

    /** 每刻最多解除 / 收回几个位（生成实体是这里最贵的一步，必须限量）。 */
    public static final int MAX_MATERIALIZE_PER_TICK = 2;
    public static final int MAX_RETRACT_PER_TICK = 4;

    /** 多久做一次"带标记却没登记"的补登记扫描（刻）。 */
    public static final int RESYNC_INTERVAL_TICKS = 20;

    /** 实体身上记"我属于哪个位"的持久化键（值是区块 long）。 */
    public static final String TAG_ENTITY_SLOT = "jafa_pending_slot";

    /** 一个位连续失败多少次就废掉它（防止"生成得出来但登记不上"这种位每刻无限刷，见 6.5.24 的教训）。 */
    private static final int MAX_STALL = 40;

    /** 一个位最多替玩家保管几份收回的载荷（幸运生物事件可能是一群）。 */
    private static final int MAX_PAYLOAD = 8;

    private static final String DATA_NAME = "jafa_lucky_pending_spawns";
    private static final String TAG_SLOTS = "Slots";
    private static final String TAG_POS = "Pos";
    private static final String TAG_KIND = "Kind";
    private static final String TAG_PAYLOAD = "Payload";

    private enum Kind {
        /** 生物位（解除时按权重决定"普通生物"还是"幸运生物事件"）。 */
        MOB,
        /** 物品位（解除时生成一个 {@link LuckyItemEntity}）。 */
        ITEM
    }

    /** 一个待生成位。 */
    private static final class Slot {
        final BlockPos pos;
        final Kind kind;
        /** 收回时存下的载荷：生物是实体 NBT，物品是物品栈 NBT（空 = 下次解除时现抽）。 */
        final List<CompoundTag> payload = new ArrayList<>();
        /** 当前在场、属于这个位的实体（一般是 1 个，幸运生物事件可能是一群）。 */
        final List<UUID> live = new ArrayList<>();
        /** 连续解除失败次数：到 {@link #MAX_STALL} 就把它标成 {@link #dead}，永不再试。 */
        int stall;
        /** 已废弃（位置被占/类型对不上之类的持续失败）。 */
        boolean dead;

        Slot(BlockPos pos, Kind kind) {
            this.pos = pos;
            this.kind = kind;
        }
    }

    /** 位表：区块 long → 该区块的位（0~{@link #SLOTS_PER_CHUNK} 个）。 */
    private final Map<Long, List<Slot>> slots = new HashMap<>();

    /** 上一次补登记扫描的刻。 */
    private long lastResyncTick = Long.MIN_VALUE;

    public static LuckyPendingSpawns get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(LuckyPendingSpawns::new, LuckyPendingSpawns::load), DATA_NAME);
    }

    // ==================== 播种 ====================

    /**
     * 给一个区块<b>补齐待生成位</b>（幂等：已有的位不动）。
     * <p>
     * 由 {@code LuckyDimensionSpawner.tickLevel} 在原有的逐区块扫描里调用——
     * 也就是说"玩家探索到哪，哪就播种"，与旧的生成半径一致，不额外增加扫描。
     * 位置直接用 {@link LuckyDimensionSpawner#findSpawnPos} 找的地表空位（含原有的地下概率）。
     */
    public static void seedChunk(ServerLevel level, LevelChunk chunk) {
        long key = chunk.getPos().toLong();
        LuckyPendingSpawns data = get(level);
        List<Slot> list = data.slots.get(key);
        if (list != null && list.size() >= SLOTS_PER_CHUNK) {
            return;
        }
        if (list == null) {
            list = new ArrayList<>(SLOTS_PER_CHUNK);
            data.slots.put(key, list);
        }
        while (list.size() < SLOTS_PER_CHUNK) {
            BlockPos pos = LuckyDimensionSpawner.findSpawnPos(level, chunk, LuckyMobSpawner.UNDERGROUND_CHANCE);
            if (pos == null) {
                break;   // 这个区块现在找不到空位（比如全是水/被占）：下次扫描再试
            }
            Kind kind = level.random.nextInt(MOB_WEIGHT + ITEM_WEIGHT) < MOB_WEIGHT ? Kind.MOB : Kind.ITEM;
            list.add(new Slot(pos, kind));
            data.setDirty();
        }
        if (list.isEmpty()) {
            data.slots.remove(key);
        }
    }

    // ==================== 每刻推进 ====================

    /** 由 {@code LuckyDimensionSpawner.tickLevel} 每刻调用一次（只对幸运维度）。 */
    public static void tick(ServerLevel level) {
        get(level).tickInternal(level);
    }

    private void tickInternal(ServerLevel level) {
        if (level.players().isEmpty()) {
            return;   // 没人在这个维度：什么都不做（位表原样留着）
        }
        long now = level.getGameTime();
        if (now - this.lastResyncTick >= RESYNC_INTERVAL_TICKS) {
            this.lastResyncTick = now;
            this.resync(level);
        }
        this.retractFar(level);
        this.materializeNear(level);
    }

    /**
     * 补登记：扫描本维度已加载实体，把"身上有位标记、但不在任何位的 live 表里"的收回去。
     * <p>
     * 覆盖三种情况：服务器读档（live 表是内存态，丢了）、区块卸载又加载（实体的位标记跟着 NBT 回来了）、
     * 幸运生物事件一次刷出一群（解除时只能捞到就近的，遗漏的下一轮补上）。
     */
    private void resync(ServerLevel level) {
        if (this.slots.isEmpty()) {
            return;
        }
        for (Entity entity : level.getAllEntities()) {
            long key = entity.getPersistentData().getLong(TAG_ENTITY_SLOT);
            if (key == 0L) {
                continue;
            }
            List<Slot> list = this.slots.get(key);
            if (list == null) {
                // 位没了（比如 /jafa 清过数据）：把它还原成普通实体，别再挂着标记
                entity.getPersistentData().remove(TAG_ENTITY_SLOT);
                continue;
            }
            Slot owner = null;
            for (Slot slot : list) {
                if (slot.live.contains(entity.getUUID())) {
                    owner = slot;
                    break;
                }
            }
            if (owner == null) {
                this.registerLive(list, entity.getUUID());
            }
        }
    }

    /** 把实体登记进"最合适的"那个位：同区块内位只有 1~2 个，取 live 最少的那个即可（不精算距离）。 */
    private void registerLive(List<Slot> list, UUID uuid) {
        Slot best = null;
        for (Slot slot : list) {
            if (slot.live.size() >= MAX_PAYLOAD) {
                continue;
            }
            if (best == null || slot.live.size() < best.live.size()) {
                best = slot;
            }
        }
        if (best != null) {
            best.live.add(uuid);
            this.setDirty();
        }
    }

    /** 收回：在场实体离最近玩家超过 {@link #RETRACT_RADIUS} 就存回位里、从世界删掉。 */
    private void retractFar(ServerLevel level) {
        int budget = MAX_RETRACT_PER_TICK;
        double limitSqr = RETRACT_RADIUS * RETRACT_RADIUS;
        for (Map.Entry<Long, List<Slot>> entry : this.slots.entrySet()) {
            if (budget <= 0) {
                return;
            }
            for (Slot slot : entry.getValue()) {
                if (budget <= 0) {
                    return;
                }
                if (slot.live.isEmpty()) {
                    continue;
                }
                // 先清掉"已经不在了"的（被捡走 / 被杀 / 换维度）——只在区块已加载时才敢这么判，
                // 否则会把"远在未加载区块里"误判成消失
                if (!level.hasChunkAt(slot.pos)) {
                    continue;
                }
                for (int i = slot.live.size() - 1; i >= 0; i--) {
                    UUID uuid = slot.live.get(i);
                    Entity entity = level.getEntity(uuid);
                    if (entity == null || entity.isRemoved()) {
                        slot.live.remove(i);
                        this.setDirty();
                    }
                }
                if (slot.live.isEmpty()) {
                    continue;
                }
                if (this.nearestPlayerDistSqr(level, slot.pos) <= limitSqr) {
                    continue;
                }
                budget -= this.retractSlot(level, slot);
            }
        }
    }

    /** 把一个位上所有在场实体收回（存载荷 + 删除），返回处理了几个实体。 */
    private int retractSlot(ServerLevel level, Slot slot) {
        int handled = 0;
        // 先删乘客、再删载具：Entity#setRemoved 会把乘客踢下车（骑士会留在原地），顺序反了就漏人
        List<Entity> roots = new ArrayList<>();
        for (UUID uuid : slot.live) {
            Entity entity = level.getEntity(uuid);
            if (entity != null && !entity.isRemoved()) {
                roots.add(entity);
            }
        }
        for (Entity entity : roots) {
            if (entity.isPassenger()) {
                continue;   // 乘客的 NBT 已经在载具的 Passengers 里
            }
            CompoundTag tag = new CompoundTag();
            if (!entity.save(tag)) {
                continue;
            }
            if (slot.payload.size() < MAX_PAYLOAD) {
                slot.payload.add(tag);
            }
        }
        for (Entity entity : roots) {
            if (entity.isPassenger()) {
                entity.discard();   // 乘客先没
            }
        }
        for (Entity entity : roots) {
            if (!entity.isPassenger()) {
                entity.discard();
            }
        }
        handled = roots.size();
        slot.live.clear();
        this.setDirty();
        return handled;
    }

    /** 解除：位空着、最近的玩家在 {@link #ACTIVATE_RADIUS} 内、区块已加载，就按预算生成。 */
    private void materializeNear(ServerLevel level) {
        int budget = MAX_MATERIALIZE_PER_TICK;
        double radiusSqr = ACTIVATE_RADIUS * ACTIVATE_RADIUS;
        for (Map.Entry<Long, List<Slot>> entry : this.slots.entrySet()) {
            if (budget <= 0) {
                return;
            }
            for (Slot slot : entry.getValue()) {
                if (budget <= 0 || slot.dead) {
                    continue;
                }
                // "想不想解除"：还有收回时存下的载荷就继续放（一群要一个一个放回来），
                // 否则只有空位才需要现抽一份。判据别写成"live 为空"——那会让载荷永远放不完。
                boolean wants = !slot.payload.isEmpty() || slot.live.isEmpty();
                if (!wants) {
                    continue;
                }
                if (this.nearestPlayerDistSqr(level, slot.pos) > radiusSqr) {
                    continue;
                }
                if (!level.hasChunkAt(slot.pos)) {
                    continue;
                }
                if (this.materialize(level, slot)) {
                    slot.stall = 0;
                    budget--;
                } else if (++slot.stall >= MAX_STALL) {
                    // 连续失败到上限：废掉这个位，免得它每刻都来试（6.5.23 那次的教训是
                    // "生成得出来但登记不上"导致无限刷，这里再兜一道底）
                    slot.dead = true;
                    ModMain.LOGGER.warn("[待生成位] 位 {} 连续 {} 次解除失败，已停用", slot.pos, MAX_STALL);
                }
            }
        }
    }

    /** 生成一个位的实体；成功（并且登记上了）返回 true。 */
    private boolean materialize(ServerLevel level, Slot slot) {
        // 有收回时存下的载荷 → 原样放回来（生物走名单那套"抹掉 UUID/Pos 再摆位"的还原逻辑）
        if (!slot.payload.isEmpty()) {
            CompoundTag tag = slot.payload.get(0);
            if (slot.kind == Kind.ITEM) {
                ItemStack stack = ItemStack.parseOptional(level.registryAccess(), tag);
                if (stack.isEmpty()) {
                    slot.payload.remove(0);   // 读不出来的载荷没有意义，丢掉
                    return true;
                }
                if (!this.spawnItemEntity(level, slot, stack)) {
                    return false;
                }
            } else {
                Entity restored = LuckyRosterEvents.spawnStoredEntity(level, slot.pos, tag);
                if (restored == null) {
                    slot.payload.remove(0);   // 实体类型不存在之类：这份载荷只能丢
                    return true;
                }
                if (!this.mark(restored, slot)) {
                    restored.discard();
                    return false;   // 载荷仍在表里，下次再说
                }
            }
            slot.payload.remove(0);
            this.setDirty();
            return true;
        }
        // 没有载荷 → 现抽一份
        if (slot.kind == Kind.ITEM) {
            ItemStack stack = LuckyItemPool.roll(level);
            if (stack.isEmpty()) {
                return false;
            }
            return this.spawnItemEntity(level, slot, stack);
        }
        int roll = level.random.nextInt(LUCKY_CREATURE_WEIGHT + NORMAL_MOB_WEIGHT);
        if (roll < LUCKY_CREATURE_WEIGHT) {
            // 幸运生物事件：一次可能刷一群，就近把它们全登记进这个位
            if (!LuckyMobSpawner.runLuckyCreatureEventAt(level, slot.pos)) {
                return false;
            }
            AABB box = new AABB(slot.pos).inflate(4.0D);
            boolean marked = false;
            for (Mob mob : level.getEntitiesOfClass(Mob.class, box, e -> !e.isPassenger() && !isManaged(e))) {
                marked |= this.mark(mob, slot);
            }
            return marked;
        }
        // 普通生物：直接拿生成出来的那一只去打标记（别在附近乱认，会把别的位的东西认领过来）
        Mob mob = LuckyMobSpawner.spawnRandomMobAt(level, slot.pos);
        if (mob == null) {
            return false;
        }
        if (!this.mark(mob, slot)) {
            mob.discard();
            return false;
        }
        return true;
    }

    /** 生成一个"自然生成"的幸运掉落物（与 {@link LuckyItemSpawner} 同一套：只能右键拿、不能被捡走）。 */
    private boolean spawnItemEntity(ServerLevel level, Slot slot, ItemStack stack) {
        LuckyItemEntity entity = ModEntities.LUCKY_ITEM.get().create(level);
        if (entity == null) {
            return false;
        }
        entity.setItem(stack);
        entity.setPos(slot.pos.getX() + 0.5D, slot.pos.getY(), slot.pos.getZ() + 0.5D);
        entity.markNaturalSpawn();
        if (!level.addFreshEntity(entity)) {
            return false;
        }
        this.mark(entity, slot);
        return true;
    }

    /** 给实体打上"属于这个位"的标记，并登记进 live 表；登记成功返回 true。 */
    private boolean mark(Entity entity, Slot slot) {
        if (!isAdoptable(entity)) {
            return false;
        }
        entity.getPersistentData().putLong(TAG_ENTITY_SLOT, slotKeyOf(slot));
        slot.live.add(entity.getUUID());
        this.setDirty();
        return true;
    }

    /**
     * 这个实体归不归本系统管。
     * <p>
     * <b>必须是白名单而不是"原版命名空间"</b>——这是 6.5.23 的教训：本系统自己刷出来的
     * {@link LuckyItemEntity} 属于模组命名空间，被"只要原版"那条闸门拦掉之后，
     * 位每刻都以为"我还空着"，于是每秒在同一格多刷 20 件物品，堆成一坨（实测 606 件一堆）。
     * 所以规则改成：
     * <ul>
     *   <li>自己刷的东西（{@link LuckyItemEntity}）与所有原版实体 → 收编；</li>
     *   <li>结构里的生物（幸运核心）、核心支撑的玩家空壳等模组产物 → 一律不碰（需求明确要求）。</li>
     * </ul>
     */
    private static boolean isAdoptable(Entity entity) {
        if (entity instanceof OrbOfLuckEntity || entity instanceof PlayerShellEntity) {
            return false;
        }
        if (entity instanceof LuckyItemEntity) {
            return true;   // 本系统自己刷的自然掉落物（模组命名空间）
        }
        net.minecraft.resources.ResourceLocation id =
            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && "minecraft".equals(id.getNamespace());
    }

    /** 找出实体所在区块的 key（{@link Slot} 本身不存 key，用坐标反算）。 */
    private static long slotKeyOf(Slot slot) {
        return ChunkPos.asLong(slot.pos.getX() >> 4, slot.pos.getZ() >> 4);
    }

    /** 到最近玩家（含旁观者以外的所有玩家）的距离平方；没人返回 {@link Double#MAX_VALUE}。 */
    private double nearestPlayerDistSqr(ServerLevel level, BlockPos pos) {
        double best = Double.MAX_VALUE;
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            best = Math.min(best, player.distanceToSqr(x, y, z));
        }
        return best;
    }

    // ==================== 与玩家互动 → 退出本系统（需求给的排除项） ====================

    /** 这个实体现在是不是"由本系统管理"的（带位标记且在某个位的 live 表里）。 */
    public static boolean isManaged(Entity entity) {
        return entity.getPersistentData().getLong(TAG_ENTITY_SLOT) != 0L;
    }

    /**
     * <b>让它彻底退出本系统</b>：摘掉位标记，从此它就是一具普通实体，再也不会被"变回空气"收回。
     * <p>
     * 需求："如果该生物通过任何方式与玩家互动了（比如被玩家攻击了），则不会再走这条路"。
     * 位本身<b>不</b>作废：玩家下次再来，原位会解除出一只新的（旧的留在世上，交给原有规则）。
     */
    public static void release(Entity entity) {
        if (entity == null || entity.level().isClientSide) {
            return;
        }
        if (entity.getPersistentData().getLong(TAG_ENTITY_SLOT) == 0L) {
            return;
        }
        // 保险：结构里的生物（幸运核心）永远不该被本系统碰到；真碰上了就地放行
        if (entity instanceof OrbOfLuckEntity) {
            entity.getPersistentData().remove(TAG_ENTITY_SLOT);
            return;
        }
        entity.getPersistentData().remove(TAG_ENTITY_SLOT);
        if (entity.level() instanceof ServerLevel serverLevel) {
            get(serverLevel).forgetLive(entity.getUUID());
        }
        ModMain.LOGGER.info("[待生成位] {} 与玩家互动过，已退出本系统（不再被收回）",
            entity.getType().toShortString());
    }

    /** 把某个实体从它所属位的 live 表里摘掉（它已经不归我们管了）。 */
    private void forgetLive(UUID uuid) {
        for (List<Slot> list : this.slots.values()) {
            for (Slot slot : list) {
                if (slot.live.remove(uuid)) {
                    this.setDirty();
                    return;
                }
            }
        }
    }

    /**
     * 需求排除项的实现：判定"与玩家互动过"就 {@link #release}。
     * <p>
     * 覆盖：被玩家造成伤害（打它的是玩家）、它伤害了玩家、被玩家右键交互（喂食/命名/拴绳/剪毛……）。
     * 每种都是"任何方式"里能可靠拦到的那几类；判定极便宜（只看持久化数据里有没有那个键）。
     */
    @EventBusSubscriber(modid = ModMain.MODID)
    public static final class InteractionRelease {

        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            // 被打的是它、动手的是玩家
            if (event.getSource().getEntity() instanceof Player) {
                release(event.getEntity());
            }
            // 它打了玩家（受害者是玩家、伤害来源是它）
            if (event.getEntity() instanceof Player && event.getSource().getEntity() != null) {
                release(event.getSource().getEntity());
            }
        }

        @SubscribeEvent
        public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
            release(event.getTarget());
        }
    }

    // ==================== 存档 ====================

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, List<Slot>> entry : this.slots.entrySet()) {
            for (Slot slot : entry.getValue()) {
                CompoundTag slotTag = new CompoundTag();
                slotTag.putLong("Chunk", entry.getKey());
                slotTag.putLong(TAG_POS, slot.pos.asLong());
                slotTag.putString(TAG_KIND, slot.kind.name());
                ListTag payload = new ListTag();
                for (CompoundTag stored : slot.payload) {
                    payload.add(stored.copy());
                }
                slotTag.put(TAG_PAYLOAD, payload);
                list.add(slotTag);
            }
        }
        tag.put(TAG_SLOTS, list);
        return tag;
    }

    private static LuckyPendingSpawns load(CompoundTag tag, HolderLookup.Provider registries) {
        LuckyPendingSpawns data = new LuckyPendingSpawns();
        ListTag list = tag.getList(TAG_SLOTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag slotTag = list.getCompound(i);
            long chunkKey = slotTag.getLong("Chunk");
            BlockPos pos = BlockPos.of(slotTag.getLong(TAG_POS));
            Kind kind;
            try {
                kind = Kind.valueOf(slotTag.getString(TAG_KIND));
            } catch (IllegalArgumentException e) {
                kind = Kind.MOB;
            }
            Slot slot = new Slot(pos, kind);
            ListTag payload = slotTag.getList(TAG_PAYLOAD, Tag.TAG_COMPOUND);
            for (int j = 0; j < payload.size() && slot.payload.size() < MAX_PAYLOAD; j++) {
                slot.payload.add(payload.getCompound(j).copy());
            }
            data.slots.computeIfAbsent(chunkKey, k -> new ArrayList<>(SLOTS_PER_CHUNK)).add(slot);
        }
        return data;
    }
}
