package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * <b>幸运名单</b>：一个存档级的<b>先进先出队列</b>，里面装的是「生物实体」或「掉落物实体」。
 *
 * <h3>作用</h3>
 * 名单<b>非空</b>时，开启幸运方块会优先从队首取一个元素放出来（见 {@link LuckyRosterEvents}），
 * 而不是照常随机抽幸运事件（见 {@link LuckyEvents#roll}）。队列取空之后自动回到原来的随机抽取。
 *
 * <h3>存的是什么</h3>
 * 两种元素（都存成 {@link CompoundTag}）：
 * <ol>
 *   <li><b>掉落物元素</b>：那一个掉落物的完整 NBT（{@code Entity#save} 的结果），放出来的是<b>原样的那件</b>
 *       ——数量、附魔、自定义名字、耐久都在；</li>
 *   <li><b>生物元素</b>：分两种走法（见 {@link #enqueueMob}）——
 *       <ul>
 *         <li>属于某个「幸运生物事件」的生物（猫、猪、狼、僵尸……见 {@link LuckyCreatureTypes}）：
 *             收进名单的是<b>整个生物事件</b>，只记一个事件 id（{@link #TAG_LUCKY_EVENT}）加一个触发类型。
 *             放出来时执行的是那个事件本体（整群猫 / 猪塔+村民 / 女巫骑恶魂……），
 *             还会照常带上开方块的那位玩家（所以玩家破坏时出来的狼是驯服的，见 {@link LuckyWolfEvent}）；
 *             这样才符合需求"选取了某生物，则将整个生物事件作为名单的元素，而不是只放那一个生物"；</li>
 *         <li>没有对应事件的生物（例如凋灵、末影龙这种被手动 {@code /jafa send} 进来的）：
 *             退回老做法，存它自己的完整 NBT，放出来就是原样的那一只。</li>
 *       </ul>
 *   </li>
 * </ol>
 * 队列本身<b>不</b>存实体对象引用：名单要跟着存档走，实体引用跨存档/跨区块都没意义。
 *
 * <h3>存在哪</h3>
 * 和 {@code DarkCloneSavedData}、{@code jafa_shared_counts} 一样存在<b>主世界</b>的维度数据里，
 * 因此整个存档只有一份，跨维度、跨玩家、跨会话都一致（与玩家数量无关，和本模组其它上限同一原则）。
 * 用的是 {@link ArrayDeque}，进出都在两端、O(1)，顺序就是存档里 {@code ListTag} 的顺序。
 *
 * <h3>怎么进元素</h3>
 * {@link #enqueueMob}（收一只生物 —— 事件优先）、{@link #enqueue(Entity)}（收一个实体，掉落物走它）、
 * {@link #enqueueStack(ServerLevel, ItemStack)}（收一件物品，按掉落物实体入队）就是全部入口，
 * <b>调用方由后续需求决定</b>——这个类只负责队列本身，不自己去找东西往里塞。
 */
public final class LuckyRoster extends SavedData {

    /** 存档里的数据名（主世界维度数据文件中的一个条目）。 */
    private static final String DATA_NAME = "jafa_lucky_roster";

    /** 队列在 NBT 里的键（一个 {@code ListTag<CompoundTag>}，按队首→队尾顺序存）。 */
    private static final String TAG_ENTRIES = "entries";

    /**
     * 生物元素上「这是一个整个生物事件」的标记键，值 = 事件 id（{@code joes_addons_for_abmc:entity/cats} 之类）。
     * <p>
     * 带这个键的元素，放出来时执行的是那个事件，而不是还原成一只生物（见 {@link LuckyRosterEvents#next}）。
     * 同一个 tag 里的 {@code id}（实体类型）仍然写着，用途有二：调试列表里看得出是"由什么选定的"，
     * 以及万一事件 id 对不上（改过 id、那个事件被删了）时还能退回去刷一只原样的生物。
     * <p>
     * 老存档里没有这个键的元素（都是实体 NBT）读进来照旧工作：没有这个键 = 实体元素。
     */
    public static final String TAG_LUCKY_EVENT = "jafa_lucky_event";

    /**
     * 队列长度上限：纯粹是防跑飞的保险（比如将来某个入口写了个死循环往里塞）。
     * 65536 条按每条 1KB 估也就 64MB，正常玩法远远碰不到；满了 {@link #enqueue(Entity)} 返回 false。
     */
    public static final int MAX_ENTRIES = 65536;

    /** 队首 = 下一次被取出的那个。 */
    private final Deque<CompoundTag> entries = new ArrayDeque<>();

    // ===== 进出队 =====

    /**
     * 把一只实体收进名单（<b>进队尾</b>，FIFO）。
     * <p>
     * 只接受<b>生物</b>与<b>掉落物</b>两类实体（需求：名单里装的就是这两种）；
     * 其它类型（船、矿车、展示实体、经验球……）一律拒绝并返回 false。
     * <p>
     * <b>生物请优先用 {@link #enqueueMob(Mob)}</b>：它会先看这只生物有没有对应的"整个生物事件"。
     *
     * @return 收下了返回 true；类型不支持、没有 {@code id}（原版不允许保存的实体）或队列已满返回 false
     */
    public boolean enqueue(Entity entity) {
        if (!isSupported(entity) || this.entries.size() >= MAX_ENTRIES) {
            return false;
        }
        CompoundTag tag = new CompoundTag();
        if (!entity.save(tag)) {
            return false;
        }
        this.entries.addLast(tag);
        this.setDirty();
        return true;
    }

    /**
     * 把一只<b>生物</b>收进名单，<b>整个生物事件优先</b>（需求 1~4）。
     * <p>
     * 若这只生物属于某个幸运生物事件（{@link LuckyCreatureTypes}），收进去的是<b>那个事件</b>
     * ——猫 → 猫群事件、猪 → 猪塔+村民事件、狼 → 狼事件（玩家破坏方块时那只狼是驯服的）……
     * 而不是单单一生物；没有对应事件的生物（凋灵之类）才退回 {@link #enqueue(Entity)} 存它自己。
     *
     * @return 收下了返回 true；队列已满返回 false
     */
    public boolean enqueueMob(Mob mob) {
        ResourceLocation eventId = LuckyCreatureTypes.eventIdOf(mob);
        if (eventId == null) {
            boolean ok = this.enqueue(mob);
            if (ok) {
                cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
                    "[幸运名单] 进队：{} 没有对应的幸运生物事件，按原样存这一只（名单现有 {} 个）",
                    mob.getType().toShortString(), this.entries.size());
            }
            return ok;
        }
        return this.enqueueEvent(mob.getType(), eventId);
    }

    /**
     * 收一个「整个幸运生物事件」元素：只记事件 id 与"由哪种生物选定"，<b>不存那只生物本身</b>。
     * <p>
     * 为什么连那只生物的 NBT 都不存：抓来的那只此刻是"无 AI + 被缩到 1/16"的抓取态，
     * 存下来的是个带临时状态的残缺品，而事件元素根本用不到它（真要退回时现造一只干净的更合适）。
     *
     * @param triggerType 是哪一种生物让这个元素进名单的（只用于描述与兜底）
     * @param eventId     要执行的幸运子事件 id
     * @return 收下了返回 true；队列已满返回 false
     */
    public boolean enqueueEvent(EntityType<?> triggerType, ResourceLocation eventId) {
        if (this.entries.size() >= MAX_ENTRIES) {
            return false;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(triggerType).toString());
        tag.putString(TAG_LUCKY_EVENT, eventId.toString());
        this.entries.addLast(tag);
        this.setDirty();
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
            "[幸运名单] 进队：整个生物事件 {} ← {}（名单现有 {} 个）",
            eventId, tag.getString("id"), this.entries.size());
        return true;
    }

    /**
     * 把一件物品当掉落物收进名单（便捷入口）。
     * <p>
     * 内部就是造一个 {@link ItemEntity} 再走 {@link #enqueue(Entity)}，所以存下来的东西和
     * 「地上真的掉了一个这样的物品」完全等价（含 NBT 数据与数量）。
     *
     * @return 收下了返回 true；物品是空栈或队列已满返回 false
     */
    public boolean enqueueStack(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return this.enqueue(new ItemEntity(level, 0.0D, 0.0D, 0.0D, stack.copy()));
    }

    /**
     * 把一份<b>已经存好的实体 NBT</b> 直接收进名单（不再经过实体对象）。
     * <p>
     * 给"支援送失败时的兜底"用（见 {@code LuckySelectorEntity#fallbackGiftToRoster}）：
     * 那时生物已经被删除、手上只剩 NBT，但仍然要保证内容不丢。
     *
     * @return 收下了返回 true；队列已满返回 false
     */
    public boolean enqueueStored(CompoundTag stored) {
        if (this.entries.size() >= MAX_ENTRIES) {
            return false;
        }
        this.entries.addLast(stored.copy());
        this.setDirty();
        return true;
    }

    /** 取走队首那个元素（<b>出队</b>，FIFO）。名单为空返回 {@code null}。 */
    @Nullable
    public CompoundTag poll() {
        CompoundTag tag = this.entries.pollFirst();
        if (tag != null) {
            this.setDirty();
        }
        return tag;
    }

    /** 名单里现有几个元素。 */
    public int size() {
        return this.entries.size();
    }

    /** 名单是不是空的（开启幸运方块时"优先取名单"的判据）。 */
    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    /** 清空名单，返回清掉几个。 */
    public int clear() {
        int removed = this.entries.size();
        if (removed > 0) {
            this.entries.clear();
            this.setDirty();
        }
        return removed;
    }

    /** 快照（队首在前，元素是副本）——只给列表/调试看，改它不影响名单。 */
    public List<CompoundTag> snapshot() {
        List<CompoundTag> list = new ArrayList<>(this.entries.size());
        for (CompoundTag tag : this.entries) {
            list.add(tag.copy());
        }
        return list;
    }

    // ===== 元素信息（命令/调试用，都不需要注册表）=====

    /** 名单能装的类型：生物实体或掉落物实体。 */
    public static boolean isSupported(Entity entity) {
        return entity instanceof Mob || entity instanceof ItemEntity;
    }

    /** 这个元素是不是掉落物（按实体类型 id 判断，{@code minecraft:item}）。 */
    public static boolean isItemTag(CompoundTag tag) {
        return "minecraft:item".equals(tag.getString("id"));
    }

    /**
     * 这个元素是不是「整个幸运生物事件」（带 {@link #TAG_LUCKY_EVENT}）；
     * 是的话返回事件 id，普通实体元素返回 {@code null}。
     */
    @Nullable
    public static ResourceLocation eventOf(CompoundTag tag) {
        String id = tag.getString(TAG_LUCKY_EVENT);
        return id.isEmpty() ? null : ResourceLocation.tryParse(id);
    }

    /**
     * <b>旧格式</b>元素（6.4.30 及以前存进去的：整个实体 NBT、没有 {@link #TAG_LUCKY_EVENT}）
     * 按新语义应该对应哪个生物事件；物品与"没有对应事件的生物"返回 {@code null}。
     *
     * <h3>为什么需要它</h3>
     * 生物元素改成"整个生物事件"是 6.4.31 才有的；在那之前进去的是一条普通实体 NBT。
     * 名单是<b>先进先出</b>的，所以老存档里那些元素会排在新元素前面先被放出来——
     * 于是一只 6.4.30 存进去的猪，在 6.4.31 里开方块仍然只出一只猪，看起来就像"整摞进名单"没生效。
     * （实测：老存档 `jafa_lucky_roster.dat` 里就躺着 3 条这种元素：wolf / witch / zombie，
     * 排在队首，不放它们出来就永远轮不到后面的猪塔元素。）
     * <p>
     * 所以放出来的时候补一次换算：是"有对应幸运生物事件"的生物就按事件处理，语义与 6.4.31 之后进队的一致。
     */
    @Nullable
    public static ResourceLocation legacyEventOf(CompoundTag tag) {
        if (isItemTag(tag) || tag.contains(TAG_LUCKY_EVENT)) {
            return null;
        }
        ResourceLocation typeId = ResourceLocation.tryParse(tag.getString("id"));
        if (typeId == null) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(typeId);
        return type == null ? null : LuckyCreatureTypes.eventIdOf(type);
    }

    /** 元素对应哪一个幸运分类（掉落物与生物事件都按"是生物还是物品"归类）。 */
    public static LuckyEventCategory categoryOf(CompoundTag tag) {
        return isItemTag(tag) ? LuckyEventCategory.LUCKY_ITEM : LuckyEventCategory.LUCKY_ENTITY;
    }

    /**
     * 元素的一句话描述（调试列表/事件描述用）：
     * {@code 生物事件 · entity/cats（由 minecraft:cat 选定）} /
     * {@code 生物 · minecraft:zombie} / {@code 掉落物 · minecraft:diamond ×3}。
     */
    public static String describe(CompoundTag tag) {
        String typeId = tag.getString("id");
        if (isItemTag(tag)) {
            CompoundTag item = tag.getCompound("Item");
            String itemId = item.getString("id");
            int count = Math.max(1, item.getInt("count"));
            return "掉落物 · " + (itemId.isEmpty() ? typeId : itemId) + " ×" + count;
        }
        ResourceLocation eventId = eventOf(tag);
        if (eventId != null) {
            return "生物事件 · " + eventId.getPath() + "（由 " + (typeId.isEmpty() ? "?" : typeId) + " 选定）";
        }
        return "生物 · " + (typeId.isEmpty() ? "?" : typeId);
    }

    /**
     * 元素类型 id 的路径部分（做幸运事件 id 用）：
     * 事件元素用事件路径（{@code entity/cats}）、掉落物用物品路径（{@code diamond}）、
     * 其余用实体路径（{@code zombie}）。
     */
    public static String shortId(CompoundTag tag) {
        ResourceLocation eventId = eventOf(tag);
        if (eventId != null) {
            return eventId.getPath();
        }
        if (isItemTag(tag)) {
            ResourceLocation itemId = ResourceLocation.tryParse(tag.getCompound("Item").getString("id"));
            if (itemId != null) {
                return itemId.getPath();
            }
        }
        ResourceLocation type = ResourceLocation.tryParse(tag.getString("id"));
        return type == null ? "unknown" : type.getPath();
    }

    // ===== 持久化 =====

    public static LuckyRoster load(CompoundTag tag, HolderLookup.Provider registries) {
        LuckyRoster data = new LuckyRoster();
        ListTag list = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.entries.addLast(list.getCompound(i));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (CompoundTag entry : this.entries) {
            list.add(entry);
        }
        tag.put(TAG_ENTRIES, list);
        return tag;
    }

    /** 统一取主世界那份，保证整个存档只有一份（跨维度、跨玩家都一致）。 */
    public static LuckyRoster get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(LuckyRoster::new, LuckyRoster::load), DATA_NAME);
    }
}
