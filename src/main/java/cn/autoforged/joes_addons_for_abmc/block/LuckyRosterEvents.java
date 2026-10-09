package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;

import javax.annotation.Nullable;

/**
 * 把<b>幸运名单</b>（{@link LuckyRoster}）里的元素变成一次幸运事件。
 *
 * <p>整条链是：开启幸运方块 → {@link LuckyEvents#roll} → 名单非空就先问这里要一个事件
 * （{@link #next(ServerLevel)}）→ {@code roll} 照常 {@code event.run(...)} 执行它 →
 * 元素被放到方块位置并加进世界（{@link #release}；生物事件元素则是直接执行那个事件本体）。
 *
 * <p><b>为什么打包成 {@link LuckyEvent} 而不是直接生成实体</b>：{@code roll} 的契约是
 * 「抽一个事件并执行，返回抽到的那个」，把名单元素做成事件之后，它就和平时的幸运子事件走同一条路——
 * 返回值、调试列表、{@code /jafa} 那些按事件查看的代码都不用为名单开特例。
 * 事件 id 取 {@code joes_addons_for_abmc:roster/<元素路径>}，分类按元素本身是生物还是掉落物来定。
 */
public final class LuckyRosterEvents {

    private LuckyRosterEvents() {
    }

    /**
     * 从名单<b>队首</b>取一个元素，打包成一次幸运事件（<b>只出队、不执行</b>，执行由 {@code roll} 负责）。
     *
     * <p><b>两种元素，两条路</b>：
     * <ul>
     *   <li><b>整个生物事件</b>（{@link LuckyRoster#eventOf} 非空，见 {@link LuckyRoster#enqueueMob}）：
     *       查事件表拿到那个子事件，直接借用它的行为——所以放出来的是<b>整群猫 / 猪塔+村民 / 女巫骑恶魂</b>……
     *       并且照常把"开方块的那位玩家"传进去（玩家破坏时狼是驯服的、猫被驯服，与原版开方块完全一致）。
     *       外面只套一层"来自名单"的 id 与描述，方便调试列表里看出这一发是从名单来的；</li>
     *   <li><b>普通实体元素</b>：照旧走 {@link #release}，把那一个实体原样放出来。</li>
     * </ul>
     * 事件 id 查不到（改过 id、对应事件被删了）时不崩：退回 {@link #release} 放一只原样的生物出来，
     * 并打一条警告——这样 {@link LuckyCreatureTypes} 那张表万一和事件类对不上，是能立刻发现的。
     *
     * @return 名单为空返回 {@code null}（调用方就照常随机抽取）
     */
    @Nullable
    public static LuckyEvent next(ServerLevel level) {
        LuckyRoster roster = LuckyRoster.get(level);
        if (roster.isEmpty()) {
            return null;
        }
        CompoundTag tag = roster.poll();
        if (tag == null) {
            return null;
        }
        // 生物事件元素：借事件本体的行为（整群/整摞一起出来），而不是还原那一只
        LuckyEvent.Action action = null;
        ResourceLocation eventId = LuckyRoster.eventOf(tag);
        if (eventId == null) {
            // 旧格式元素（6.4.30 及以前进队的普通实体 NBT）补一次换算，见 LuckyRoster#legacyEventOf
            ResourceLocation upgraded = LuckyRoster.legacyEventOf(tag);
            if (upgraded != null) {
                eventId = upgraded;
                ModMain.LOGGER.info("[幸运名单] 出队：{} 是旧格式元素，按新语义换算成 {} 处理",
                    LuckyRoster.describe(tag), eventId);
            }
        }
        if (eventId != null) {
            LuckyEvent creatureEvent = LuckyEvents.byId(eventId);
            if (creatureEvent != null) {
                action = creatureEvent.action();
                ModMain.LOGGER.info("[幸运名单] 出队：{} → 执行整个事件 {}（名单剩余 {} 个）",
                    LuckyRoster.describe(tag), eventId, roster.size());
            } else {
                ModMain.LOGGER.warn("[幸运名单] 元素指向的幸运事件不存在，退回只放一只原样的生物：{}（元素 {}）",
                    eventId, LuckyRoster.describe(tag));
            }
        }
        if (action == null) {
            action = (eventLevel, pos, player) -> release(eventLevel, pos, tag);
            ModMain.LOGGER.info("[幸运名单] 出队：{} → 按原样放出这一只（名单剩余 {} 个）",
                LuckyRoster.describe(tag), roster.size());
        }
        ResourceLocation id = LuckyEvents.id("roster/" + LuckyRoster.shortId(tag));
        String description = "幸运名单：" + LuckyRoster.describe(tag);
        return LuckyEvent.of(id, LuckyRoster.categoryOf(tag), description, action);
    }

    /**
     * 把名单里的<b>普通实体元素</b>放到幸运方块的位置上（生物事件元素不走这里，见 {@link #next}）。
     *
     * <p><b>位置一律以幸运方块为准</b>：进名单时存的那份 NBT 里带着它当时所在的位置，
     * 直接照搬的话东西会出现在它被收走的地方，而不是开方块的地方——所以这里有两点覆盖：
     * <ul>
     *   <li>{@code Pos} 键先删掉，再由 {@link #place} 摆到方块处；</li>
     *   <li>{@code UUID} 键也删掉：新造出来的实体要拿一个<b>新的</b> UUID，否则撞上存档里已有的同号实体时
     *       原版会拒绝把它加进世界（{@code PersistentEntitySectionManager} 按 UUID 去重）。</li>
     * </ul>
     * 其余数据（名字、血量、装备、驯服/拴绳、掉落物内容……）原样保留，所以放出来的是原样的那只。
     *
     * <p>用 {@link EntityType#loadEntityRecursive} 而不是 {@code EntityType.create}：
     * 前者连 {@code Passengers} 一起还原，万一元素本身驮着东西也不会丢。
     */
    private static void release(ServerLevel level, BlockPos pos, CompoundTag stored) {
        spawnStoredEntity(level, pos, stored);
    }

    /**
     * <b>把一份实体 NBT 原样放到指定位置</b>——幸运名单出队与"支援幸运方块碎裂"共用这一份。
     * <p>
     * 两条调用链都要求同样的两件事：位置以<b>目标方块</b>为准、新实体拿一个<b>新的</b> UUID。
     * 所以这里先把 {@code Pos}/{@code UUID} 抹掉再交给 {@link #place} 摆放（理由见上）。
     */
    public static Entity spawnStoredEntity(ServerLevel level, BlockPos pos, CompoundTag stored) {
        CompoundTag tag = stored.copy();
        tag.remove("UUID");
        tag.remove("Pos");
        if (LuckyRoster.isItemTag(tag)) {
            // 掉落物：寿命重新计（可能已经躺了很久），否则放出来可能立刻就到期消失。
            // ItemEntity 没有 setAge，age 只能从 NBT 的 "Age" 键读进来（ItemEntity.java:321/343）。
            tag.putShort("Age", (short) 0);
        }
        Entity entity = EntityType.loadEntityRecursive(tag, level, e -> e);
        if (entity == null) {
            // 元素里的实体类型在这个存档里不存在（例如那个模组被卸了）：这一条只能丢掉
            ModMain.LOGGER.warn("[幸运名单] 无法还原元素（实体类型不存在？）：{}", LuckyRoster.describe(stored));
            return null;
        }
        place(level, entity, pos);
        level.addFreshEntity(entity);
        return entity;
    }

    /** 摆放：生物从方块那一格往上找第一个塞得下的高度；掉落物从方块上方弹出。 */
    private static void place(ServerLevel level, Entity entity, BlockPos pos) {
        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        if (entity instanceof Mob) {
            // 和幸运实体事件同一套做法：避免大体积生物被塞进石头里
            double y = LuckyEvents.findFreeY(level, entity, x, pos.getY(), z);
            entity.moveTo(x, y, z, entity.getYRot(), entity.getXRot());
            return;
        }
        entity.moveTo(x, pos.getY() + 0.5D, z, entity.getYRot(), entity.getXRot());
        if (entity instanceof ItemEntity item) {
            // 同原版"方块掉物品"：给一段拾取延迟，免得刚弹出来就被玩家吸走
            item.setDefaultPickUpDelay();
        }
    }
}
