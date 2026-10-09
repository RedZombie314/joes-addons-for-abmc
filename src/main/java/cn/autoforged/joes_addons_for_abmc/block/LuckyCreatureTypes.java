package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * 「符合幸运生物事件的生物」到底是哪些，以及<b>每一种分别属于哪一个幸运生物事件</b>。
 * <ul>
 *   <li>前半张表给幸运选择器的新 AI 筛选用（见 {@code SelectorCollectGoal}）：只有这里的生物才会被自动抓；</li>
 *   <li>后半张表（类型 → 事件 id）给<b>幸运名单</b>用：抓到一只猫，进名单的是
 *       <b>整个「猫群」事件</b>而不是那一只猫（见 {@link LuckyRoster#enqueueMob}）；</li>
 * </ul>
 *
 * <h3>表是怎么来的</h3>
 * 幸运方块"幸运实体"分类下那几个子事件各自刷什么、id 叫什么，这里照着抄一份：
 * <ul>
 *   <li>{@link LuckyCatsEvent}（{@code entity/cats}）→ 猫 / 豹猫；</li>
 *   <li>{@link LuckyBobEvent}（{@code entity/zombie_bob}）→ 僵尸（Bob）；</li>
 *   <li>{@link LuckyPigTowerEvent}（{@code entity/pig_tower}）→ 猪 + 村民；</li>
 *   <li>{@link LuckyWitchGhastEvent}（{@code entity/witch_ghast}）→ 女巫 + 恶魂；</li>
 *   <li>{@link LuckySkeletonKnightEvent}（{@code entity/skeleton_knight}）→ 骷髅 + 骷髅马；</li>
 *   <li>{@link LuckyWolfEvent}（{@code entity/wolf}）→ 狼；</li>
 *   <li><b>6.5.5 新增</b>：{@link LuckyElderGuardianEvent}（{@code entity/elder_guardian}）→ 远古守卫者、
 *       {@link LuckyRabbitChickenEvent}（{@code entity/rabbit_chicken}）→ 兔子 / 鸡、
 *       {@link LuckySnowGolemBatEvent}（{@code entity/snow_golem_bat}）→ 雪傀儡 / 蝙蝠、
 *       {@link LuckyGoldenHorseEvent}（{@code entity/golden_horse}）→ 马、
 *       {@link LuckyCreeperEvent}（{@code entity/creeper}）→ 苦力怕、
 *       {@link LuckyGoldNuggetPigEvent}（{@code entity/gold_nugget_pig}）→ 自定金粒猪、
 *       {@link LuckyFireworkPigEvent}（{@code entity/firework_pig}）→ 自定烟花猪。</li>
 * </ul>
 * 不刷生物的那几个（{@code entity/falling_volley} 落红石块与 TNT、{@code entity/falling_anvil} 落铁砧、
 * {@code entity/firework_volley} 冲天烟花、{@code entity/reflect_shield} 反弹盾牌，以及
 * {@code item/pig_bait_carrot} 那种归属"幸运物品"分类的）不在这张表里。
 *
 * <p><b>猪为什么只对应猪塔</b>：{@code EVENT_BY_TYPE} 是"一个类型 → 一个事件"，而 {Map.ofEntries} 不允许
 * 同一个键出现两次。原版猪现在同时出现在猪塔、金粒猪、烟花猪三个事件里，三选一只会有一个进表 ——
 * 保留原有的<b>猪塔</b>（选择器抓到一只普通猪时，放出来的仍然是猪塔，语义与 6.5.5 之前完全一致）。
 * 两只<b>自定猪</b>（金粒猪 / 烟花猪）是独立的实体类型，不冲突，所以按类型单独登记（见 {@link #customEventByType()}）。
 *
 * <p><b>为什么硬抄一份而不是让事件自己报</b>：{@link LuckyEvent} 目前只有一个"执行"行为，
 * 没有"这个事件会刷出哪些生物"的声明；要给每个事件都加一个字段，改动面比这张表大得多。
 * 代价是<b>新增/修改幸运实体事件时这里要跟着改（连 id 一起）</b>——所以放在事件类隔壁、
 * 并在每个事件类里留了反向指引。
 *
 * <p><b>id 写错了会怎样</b>：{@link LuckyRosterEvents#next} 查不到那个 id 时不会崩，
 * 会退回"只放出一只原样的这种生物"并打一条警告（见那里的注释）——所以表对不上是能立刻看出来的。
 *
 * <p>判定按<b>实体类型</b>（{@code EntityType}）而不是"是不是这个事件刷出来的"：同一个类型无论是
 * 幸运事件刷的、自然刷的还是玩家带进来的，都算「符合幸运生物事件的生物」（需求要的就是这个粒度）。
 */
public final class LuckyCreatureTypes {

    /**
     * 生物类型 → 它所属的那个幸运生物事件的 id。
     * <p>
     * 一个事件可能对应多种生物（猫群 = 猫 + 豹猫、猪塔 = 猪 + 村民……），所以这里是多对一。
     * 反过来（一个类型落进两个事件）不存在，所以用 {@code Map} 就够。
     */
    private static final Map<EntityType<?>, ResourceLocation> EVENT_BY_TYPE = Map.ofEntries(
        Map.entry(EntityType.CAT, LuckyEvents.id("entity/cats")),
        Map.entry(EntityType.OCELOT, LuckyEvents.id("entity/cats")),
        Map.entry(EntityType.ZOMBIE, LuckyEvents.id("entity/zombie_bob")),
        Map.entry(EntityType.PIG, LuckyEvents.id("entity/pig_tower")),
        Map.entry(EntityType.VILLAGER, LuckyEvents.id("entity/pig_tower")),
        Map.entry(EntityType.WITCH, LuckyEvents.id("entity/witch_ghast")),
        Map.entry(EntityType.GHAST, LuckyEvents.id("entity/witch_ghast")),
        Map.entry(EntityType.SKELETON, LuckyEvents.id("entity/skeleton_knight")),
        Map.entry(EntityType.SKELETON_HORSE, LuckyEvents.id("entity/skeleton_knight")),
        Map.entry(EntityType.WOLF, LuckyEvents.id("entity/wolf")),
        // ===== 6.5.5 =====
        Map.entry(EntityType.ELDER_GUARDIAN, LuckyEvents.id("entity/elder_guardian")),
        Map.entry(EntityType.RABBIT, LuckyEvents.id("entity/rabbit_chicken")),
        Map.entry(EntityType.CHICKEN, LuckyEvents.id("entity/rabbit_chicken")),
        Map.entry(EntityType.SNOW_GOLEM, LuckyEvents.id("entity/snow_golem_bat")),
        Map.entry(EntityType.BAT, LuckyEvents.id("entity/snow_golem_bat")),
        Map.entry(EntityType.HORSE, LuckyEvents.id("entity/golden_horse")),
        Map.entry(EntityType.CREEPER, LuckyEvents.id("entity/creeper")));

    /** 自定实体类型 → 事件 id 的缓存（第一次用到时才建，避开类初始化期的注册时序）。 */
    @Nullable
    private static Map<EntityType<?>, ResourceLocation> customTypes;

    /**
     * 自定实体类型 → 事件 id。<b>不放进上面那张 {@code Map.ofEntries}</b>，原因有两个：
     * <ol>
     *   <li>{@code Map.ofEntries} 的键在<b>类初始化</b>时就要拿到 {@code EntityType} 对象，
     *       而 {@code ModEntities.XXX.get()} 是 DeferredHolder 取值 —— 万一有谁在注册完成前碰到这个类，
     *       就会当场炸掉。放进方法里"用的时候再问"就没有这个时序风险；</li>
     *   <li>这两只是"猪"的子类，和原版猪是不同的 {@code EntityType}，天然不冲突。</li>
     * </ol>
     * 结果缓存一次（选择器 AI 会反复问"这只算不算幸运生物"）。
     */
    private static Map<EntityType<?>, ResourceLocation> customEventByType() {
        if (customTypes == null) {
            customTypes = Map.of(
                ModEntities.GOLD_NUGGET_PIG.get(), LuckyEvents.id("entity/gold_nugget_pig"),
                ModEntities.FIREWORK_PIG.get(), LuckyEvents.id("entity/firework_pig"));
        }
        return customTypes;
    }

    /** 幸运实体事件会刷出的生物类型（= 上面两张表的键，见类注释里的对照表）。 */
    private static final Set<EntityType<?>> TYPES = EVENT_BY_TYPE.keySet();

    private LuckyCreatureTypes() {
    }

    /** 这个实体类型是不是"幸运生物事件里的生物"。 */
    public static boolean isLuckyCreature(EntityType<?> type) {
        return TYPES.contains(type) || customEventByType().containsKey(type);
    }

    /** 这个实体是不是"幸运生物事件里的生物"。 */
    public static boolean isLuckyCreature(Entity entity) {
        return isLuckyCreature(entity.getType());
    }

    /**
     * 这个实体类型属于哪一个幸运生物事件（没有对应事件时返回 {@code null}）。
     * <p>
     * 名单用它把"一只生物"换算成"一整个生物事件"（需求 1~4：抓猫进名单的是猫群事件、
     * 抓猪进名单的是猪塔事件……）。
     */
    @Nullable
    public static ResourceLocation eventIdOf(EntityType<?> type) {
        ResourceLocation mapped = EVENT_BY_TYPE.get(type);
        return mapped != null ? mapped : customEventByType().get(type);
    }

    /** 这个实体属于哪一个幸运生物事件（没有对应事件时返回 {@code null}）。 */
    @Nullable
    public static ResourceLocation eventIdOf(Entity entity) {
        return eventIdOf(entity.getType());
    }

    /** 表里一共有几种（命令/调试输出用）。 */
    public static int size() {
        return TYPES.size() + customEventByType().size();
    }
}
