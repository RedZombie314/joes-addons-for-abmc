package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.block.LuckyBlockEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 幸运核心 <b>INITIAL 阶段</b>的"随机发射"特性：
 * 每隔 {@link #MIN_INTERVAL_TICKS}~{@link #MAX_INTERVAL_TICKS} 游戏刻，
 * 朝<b>随机方向、随机仰角（10~30°）、随机速度（10~60 格/秒）</b>抛出一件随机物品或一只随机生物。
 * 定时由 {@link OrbOfLuckEntity#tick()} 按<b>游戏刻</b>驱动（{@code /tick freeze} 期间不流逝）。
 *
 * <h2>物品池：原版"能正常获取"的物品 ∪ 幸运物品池</h2>
 * <ul>
 *   <li>原版部分：遍历 {@code BuiltInRegistries.ITEM}，只取 {@code minecraft:} 命名空间，
 *       再剔除 {@link #NOT_OBTAINABLE_ITEMS}（命令/技术方块、创造专属等）与全部<b>刷怪蛋</b>
 *       （刷怪蛋在生存里拿不到）。这份黑名单是人工整理的"不算正常获取"清单，想放宽/收紧改它即可。</li>
 *   <li>幸运物品池：{@code #joes_addons_for_abmc:lucky_items}（{@link LuckyBlockEvents#luckyPoolItems}），
 *       它是数据包内容、可能被改，所以每次抽取时现并一次（按 {@link Item} 句柄去重）。</li>
 *   <li><b>权重一致</b>：两份池子合起来之后，每个物品等概率。</li>
 * </ul>
 *
 * <h2>生物池：原版所有生物，点名排除四种</h2>
 * 只取 {@code minecraft:} 命名空间、且<b>真的能建成一只 {@link LivingEntity}</b>的类型
 * （这样自动排掉了箭、船、掉落物、TNT 这些非生物实体；注意不能拿 {@code getBaseClass()} 判断，
 * 原因见 {@link #mobPool}），再排除<b>末影龙 / 凋灵 / 监守者 / 远古守卫者</b>（用户点名）、
 * <b>玩家</b>实体本身（它需要游戏档案才能建）与<b>盔甲架</b>。
 * <p>
 * <b>落地前免疫摔落伤害</b>：发射时给生物打上 {@link #TAG_LAUNCHED} 标记，
 * {@code LivingFallEvent} 见到标记就取消（摔落伤害的根事件），
 * 由 {@link #tickLaunched()} 在它落地那一刻摘掉标记，之后的坠落照常受伤。
 */
public final class OrbInitialLaunch {

    /** 两次发射的间隔下限（游戏刻）。 */
    private static final int MIN_INTERVAL_TICKS = 10;

    /** 两次发射的间隔上限（游戏刻）。 */
    private static final int MAX_INTERVAL_TICKS = 60;

    /** 仰角下限（度，向上）。 */
    private static final float MIN_ELEVATION_DEGREES = 10.0F;

    /** 仰角上限（度，向上）。 */
    private static final float MAX_ELEVATION_DEGREES = 30.0F;

    /** 初速度下限（格/秒）。 */
    private static final float MIN_SPEED_BLOCKS_PER_SECOND = 10.0F;

    /** 初速度上限（格/秒）：60 格/秒 = 3 格/刻。 */
    private static final float MAX_SPEED_BLOCKS_PER_SECOND = 60.0F;

    /** 抛生物的概率（其余抛物品）。 */
    private static final float MOB_CHANCE = 0.2F;

    /** 抛出去的物品存活多久（游戏刻）：1 分钟（原版默认 5 分钟）。 */
    private static final int ITEM_LIFETIME_TICKS = 60 * 20;

    /** 落地前免疫摔落伤害的标记。 */
    private static final String TAG_LAUNCHED = "jafa_orb_launched";

    /** 点名排除的生物：用户指定的四个 Boss 级生物 + 玩家实体本身 + 盔甲架。 */
    private static final Set<EntityType<?>> EXCLUDED_MOBS = Set.of(
        EntityType.ENDER_DRAGON,
        EntityType.WITHER,
        EntityType.WARDEN,
        EntityType.ELDER_GUARDIAN,
        EntityType.PLAYER,
        EntityType.ARMOR_STAND);

    /**
     * "不算正常获取"的原版物品：命令/结构/调试类方块、创造模式专属方块等。
     * <p>
     * 人工整理的清单（生存里拿不到的），刷怪蛋另外按 {@link SpawnEggItem} 统一排除。
     * 只写<b>真正有对应物品</b>的那些：像 tripwire/piston_head/moving_piston/tall_seagrass
     * 这类只有方块、没有物品的，本来就不会出现在物品注册表里，不需要也没法列在这里。
     */
    private static final Set<Item> NOT_OBTAINABLE_ITEMS = Set.of(
        Items.AIR,
        Items.COMMAND_BLOCK,
        Items.CHAIN_COMMAND_BLOCK,
        Items.REPEATING_COMMAND_BLOCK,
        Items.COMMAND_BLOCK_MINECART,
        Items.BARRIER,
        Items.LIGHT,
        Items.STRUCTURE_BLOCK,
        Items.STRUCTURE_VOID,
        Items.JIGSAW,
        Items.DEBUG_STICK,
        Items.KNOWLEDGE_BOOK,
        Items.BEDROCK,
        Items.REINFORCED_DEEPSLATE,
        Items.BUDDING_AMETHYST,
        Items.PETRIFIED_OAK_SLAB,
        Items.END_PORTAL_FRAME,
        Items.FARMLAND,
        Items.DIRT_PATH,
        Items.CHORUS_PLANT,
        Items.SMALL_DRIPLEAF);

    /** 原版物品池（只算一次；幸运物品池每次现并）。 */
    private static List<Item> cachedVanillaItems;

    /** 生物池（只算一次）。 */
    private static List<EntityType<? extends LivingEntity>> cachedMobs;

    /** 已发射、还没落地的生物（落地时摘标记）。 */
    private static final List<LivingEntity> LAUNCHED = new ArrayList<>();

    private OrbInitialLaunch() {
    }

    /** 抽一个发射间隔（刻）。 */
    public static int rollInterval(RandomSource random) {
        return MIN_INTERVAL_TICKS + random.nextInt(MAX_INTERVAL_TICKS - MIN_INTERVAL_TICKS + 1);
    }

    /** 发射一次：随机方向 / 仰角 10~30° / 速度 10~60 格每秒，50% 物品、50% 生物。 */
    public static void launch(ServerLevel level, OrbOfLuckEntity orb) {
        RandomSource random = level.random;
        float speed = MIN_SPEED_BLOCKS_PER_SECOND
            + random.nextFloat() * (MAX_SPEED_BLOCKS_PER_SECOND - MIN_SPEED_BLOCKS_PER_SECOND);
        Vec3 velocity = randomVelocity(random, speed);
        if (random.nextFloat() < MOB_CHANCE) {
            launchMob(level, orb, velocity, random);
        } else {
            launchItem(level, orb, velocity, random);
        }
    }

    /** 随机水平方向 + 10~30° 仰角，速度按格/秒换算成格/刻。 */
    private static Vec3 randomVelocity(RandomSource random, float speedBlocksPerSecond) {
        double yaw = random.nextDouble() * Math.PI * 2.0;
        double elevation = Math.toRadians(MIN_ELEVATION_DEGREES
            + random.nextFloat() * (MAX_ELEVATION_DEGREES - MIN_ELEVATION_DEGREES));
        double horizontal = Math.cos(elevation);
        // 格/秒 → 格/刻
        double speedPerTick = speedBlocksPerSecond / 20.0;
        return new Vec3(Math.cos(yaw) * horizontal * speedPerTick,
            Math.sin(elevation) * speedPerTick,
            Math.sin(yaw) * horizontal * speedPerTick);
    }

    /**
     * 吐出<b>物品</b>。
     *
     * <p><b>它必须是"幸运掉落物"而不是普通掉落物</b>（用户指定）：和幸运维度里自然刷出来的东西一样，
     * <b>走过去捡不起来、只能右键拿</b> —— 也就是 {@link LuckyItemEntity} 那套
     * （{@link LuckyItemEntity#markNaturalSpawn()} + 覆写的 {@code interact}/{@code playerTouch}）。
     * 抛物线的物理完全不变：它本身就是个 {@code ItemEntity}，速度/重力/落地的处理一模一样。
     */
    private static void launchItem(ServerLevel level, OrbOfLuckEntity orb, Vec3 velocity, RandomSource random) {
        List<Item> pool = itemPool(level);
        if (pool.isEmpty()) {
            return;
        }
        Item item = pool.get(random.nextInt(pool.size()));
        LuckyItemEntity entity = ModEntities.LUCKY_ITEM.get().create(level);
        if (entity == null) {
            return;   // 个别情况下建不出来：跳过这一次
        }
        entity.setItem(new ItemStack(item));
        entity.setPos(orb.getX(), orb.getY() + 0.5, orb.getZ());
        // 存活时间：原版 ItemEntity 的消失判定就是 age >= lifespan，而 lifespan 是公开字段
        // （默认 6000 刻 = 5 分钟），所以直接改它即可，不需要自己跟踪。
        entity.lifespan = ITEM_LIFETIME_TICKS;
        // ★ 走过去捡不起来、只能右键拿走（本模组"幸运掉落物"的固有特性）
        entity.markNaturalSpawn();
        entity.setDeltaMovement(velocity);
        entity.hasImpulse = true;
        level.addFreshEntity(entity);
    }

    private static void launchMob(ServerLevel level, OrbOfLuckEntity orb, Vec3 velocity, RandomSource random) {
        List<EntityType<? extends LivingEntity>> pool = mobPool(level);
        if (pool.isEmpty()) {
            return;
        }
        EntityType<? extends LivingEntity> type = pool.get(random.nextInt(pool.size()));
        LivingEntity mob = type.create(level);
        if (mob == null) {
            return;   // 个别类型需要额外上下文，建不出来就跳过这一次
        }
        mob.moveTo(orb.getX(), orb.getY() + 0.5, orb.getZ(), random.nextFloat() * 360.0F, 0.0F);
        mob.setDeltaMovement(velocity);
        mob.hasImpulse = true;
        // 落地前免疫摔落伤害：打标记（LivingFallEvent 据此取消），落地时由 tickLaunched 摘掉
        mob.getPersistentData().putBoolean(TAG_LAUNCHED, true);
        LAUNCHED.add(mob);
        level.addFreshEntity(mob);
    }

    /**
     * 可发射的物品池：原版"能正常获取"的物品 ∪ 幸运物品池，去重后每个等概率。
     * <p>
     * 原版部分只算一次并缓存；幸运物品池是数据包内容（可能被改），所以每次都现并一次。
     */
    public static List<Item> itemPool(ServerLevel level) {
        if (cachedVanillaItems == null) {
            List<Item> list = new ArrayList<>();
            Set<Item> seen = new HashSet<>();
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (!ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
                    continue;   // 只要原版
                }
                if (item instanceof SpawnEggItem || NOT_OBTAINABLE_ITEMS.contains(item)) {
                    continue;
                }
                if (seen.add(item)) {
                    list.add(item);
                }
            }
            cachedVanillaItems = list;
        }
        List<Item> result = new ArrayList<>(cachedVanillaItems);
        Set<Item> seen = new HashSet<>(cachedVanillaItems);
        for (Item item : LuckyBlockEvents.luckyPoolItems(level)) {
            if (seen.add(item)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 可发射的生物池：原版所有生物，排除点名的那几个。只在首次调用时构建一次。
     * <p>
     * <b>不能用 {@code EntityType#getBaseClass()} 判断"是不是生物"</b>：NeoForge 这个方法在原版
     * {@code EntityType} 上的实现恒为 {@code return Entity.class}（它只是留给自定义 EntityType
     * 覆写的钩子），拿它做 {@code LivingEntity.class.isAssignableFrom(...)} 会<b>一个都匹配不上</b>，
     * 结果就是"幸运核心从来只扔物品、不扔生物"。
     * <p>
     * 所以这里改成真的建一个出来试一下（{@code instanceof LivingEntity}）。只在首次构建时做一次，
     * 探测用的实体不会进世界，构建结果缓存起来。
     */
    public static List<EntityType<? extends LivingEntity>> mobPool(ServerLevel level) {
        if (cachedMobs == null) {
            List<EntityType<? extends LivingEntity>> list = new ArrayList<>();
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (!ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
                    continue;   // 只要原版
                }
                if (EXCLUDED_MOBS.contains(type)) {
                    continue;
                }
                if (!(type.create(level) instanceof LivingEntity)) {
                    continue;   // 箭、船、掉落物、TNT 这些非生物在这里出局
                }
                @SuppressWarnings("unchecked")
                EntityType<? extends LivingEntity> living = (EntityType<? extends LivingEntity>) type;
                list.add(living);
            }
            cachedMobs = list;
        }
        return cachedMobs;
    }

    /** 由服务端 tick 钩子（{@code ModMain.onServerTickPre}）每刻调用：发射物落地就撤销摔落免疫。 */
    public static void tickLaunched() {
        if (LAUNCHED.isEmpty()) {
            return;
        }
        Iterator<LivingEntity> iterator = LAUNCHED.iterator();
        while (iterator.hasNext()) {
            LivingEntity mob = iterator.next();
            if (mob == null || !mob.isAlive() || mob.isRemoved()) {
                iterator.remove();
                continue;
            }
            if (mob.onGround()) {
                // 落地了：摘掉标记，之后的坠落照常受伤
                mob.getPersistentData().remove(TAG_LAUNCHED);
                iterator.remove();
            }
        }
    }

    /** 该生物是不是"被发射出去、还没落地"（落地前免疫摔落伤害）。 */
    public static boolean isFallImmune(LivingEntity entity) {
        return entity.getPersistentData().getBoolean(TAG_LAUNCHED);
    }
}
