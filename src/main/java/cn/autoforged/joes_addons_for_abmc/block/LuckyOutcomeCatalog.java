package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <b>"幸运方块默认能开出什么"的清单</b> —— {@code /jafa luckyblockresult} 的候选表（用户指定 6.8.9）。
 *
 * <h2>为什么要有这张表</h2>
 * 那条命令以前是"往一个池子里塞<b>任意物品</b>"（候选来自整个物品注册表）。用户改成：
 * <ol>
 *   <li>候选只围绕<b>默认情况下幸运方块能开出的东西</b>展开（物品 / 生物 / 结构），不再支持所有物品；</li>
 *   <li>不再是"池"（塞几件就是几选一），而是<b>指定哪一个就只开出哪一个</b>。</li>
 * </ol>
 * 于是候选表 = <b>默认事件表本身</b>（{@link LuckyEvents#eventsOf}，含按标签惰性注册的幸运物品），
 * 不需要另外维护一份名单 —— 事件表加一项，这里自动多一项。
 *
 * <h2>名字从哪来</h2>
 * <ul>
 *   <li><b>物品</b>：用物品自己的显示名（"钻石"、"铁抓钩"），随客户端语言走；</li>
 *   <li><b>生物 / 结构</b>：它们没有"物品名"可用，所以在这里用 {@link #NAMES} 起中文名
 *       （用户要求"生物和结构请你想好对应的名字"）—— 例如 {@code entity/cats} 叫"猫群"、
 *       {@code entity/witch_ghast} 叫"女巫骑恶魂"、{@code structure/lucky_well} 叫"幸运水井"；</li>
 *   <li>表里没写、也不是物品的（以后新增的事件忘了起名）：退回事件 id 的路径当名字，
 *       至少不会显示成空白。</li>
 * </ul>
 * 命令里三种写法都认：完整 id（{@code joes_addons_for_abmc:entity/cats}）、
 * id 路径（{@code entity/cats} 或 {@code cats}）、名字（{@code 猫群}）。
 */
public final class LuckyOutcomeCatalog {

    /** 一项默认产物：子事件 id、给玩家看的名字、所属分类。 */
    public record Entry(ResourceLocation id, String name, LuckyEventCategory category) {
    }

    /**
     * 名字查询结果（照 {@code LuckyEvents.ItemQuery} 的写法）。
     *
     * @param entry   唯一命中时的那一项；没命中或有歧义时为 {@code null}
     * @param matches 命中到的项：唯一命中就是它自己，有歧义时是<b>全部</b>候选（列给玩家看）
     */
    public record Query(@Nullable Entry entry, List<Entry> matches) {
        /** 有歧义：输入的名字对上了多项（例如"钻石"既是物品也可能撞上别的名字）。 */
        public boolean ambiguous() {
            return this.entry == null && this.matches.size() > 1;
        }
    }

    /** 物品类子事件 id 的前缀（{@code item/<物品路径>}，见 {@code LuckyEvents.buildItemReward}）。 */
    private static final String ITEM_PREFIX = "item/";

    /**
     * <b>生物与结构事件的中文名</b>（用户指定：想好对应的名字）。
     *
     * <p>键 = 子事件 id 的路径（{@code entity/xxx} / {@code structure/xxx}）；
     * 值 = 命令里可以直接输入、也用于列表显示的名字。
     * 这里只放"没有物品名可用"的那些；物品类事件的名字由物品自己提供（见 {@link #displayName}）。
     */
    private static final Map<String, String> NAMES = Map.ofEntries(
        // ===== 幸运生物 =====
        Map.entry("entity/cats", "猫群"),
        Map.entry("entity/zombie_bob", "僵尸 Bob"),
        Map.entry("entity/creeper", "苦力怕"),
        Map.entry("entity/elder_guardian", "远古守卫者"),
        Map.entry("entity/falling_anvil", "下落铁砧"),
        Map.entry("entity/falling_volley", "红石块落石"),
        Map.entry("entity/firework_pig", "烟花猪"),
        Map.entry("entity/firework_volley", "冲天烟花"),
        Map.entry("entity/gold_nugget_pig", "金粒猪"),
        Map.entry("entity/golden_horse", "金马"),
        Map.entry("entity/pig_tower", "猪套娃"),
        Map.entry("entity/rabbit_chicken", "兔子骑鸡"),
        Map.entry("entity/reflect_shield", "反弹盾"),
        Map.entry("entity/skeleton_knight", "骷髅骑士"),
        Map.entry("entity/snow_golem_bat", "雪傀儡骑蝙蝠"),
        Map.entry("entity/witch_ghast", "女巫骑恶魂"),
        Map.entry("entity/wolf", "狼"),
        // ===== 幸运物品里"不是单纯给一件物品"的那几个 =====
        Map.entry("item/explosive_arrow", "爆炸之箭"),
        Map.entry("item/gem_scatter", "宝石撒落"),
        Map.entry("item/gold_ingot_burst", "金锭喷发"),
        Map.entry("item/heroism_potion", "英雄药水"),
        Map.entry("item/hero_set", "英雄套装"),
        Map.entry("item/hero_sword", "英雄之剑"),
        Map.entry("item/pig_bait_carrot", "诱猪胡萝卜"),
        // ===== 幸运结构 =====
        Map.entry("structure/lucky_well", "幸运水井"));

    private LuckyOutcomeCatalog() {
    }

    /**
     * 全部默认产物（物品 + 生物 + 结构），按分类顺序排列。
     * <p>会先补一次 {@link LuckyEvents#registerPoolItemEvents} —— 幸运物品池里的物品子事件是按标签
     * <b>惰性注册</b>的（标签要 {@code ServerLevel} 才能解析），不补就会漏掉它们。
     */
    public static List<Entry> all(ServerLevel level) {
        LuckyEvents.registerPoolItemEvents(level);
        List<Entry> list = new ArrayList<>();
        for (LuckyEventCategory category : LuckyEventCategory.values()) {
            for (LuckyEvent event : LuckyEvents.eventsOf(level, category)) {
                list.add(new Entry(event.id(), displayName(event), category));
            }
        }
        return list;
    }

    /** 一项产物的显示名：优先查 {@link #NAMES}，物品类退回物品自己的名字，都没有就用 id 路径。 */
    public static String displayName(LuckyEvent event) {
        String path = event.id().getPath();
        String fixed = NAMES.get(path);
        if (fixed != null) {
            return fixed;
        }
        if (path.startsWith(ITEM_PREFIX)) {
            Item item = itemByPath(path.substring(ITEM_PREFIX.length()));
            if (item != null) {
                return item.getDescription().getString();
            }
        }
        return path;
    }

    /**
     * 解析玩家输入的一项产物，依次尝试三种写法：
     * <ol>
     *   <li><b>完整 id</b>：{@code joes_addons_for_abmc:entity/cats}；</li>
     *   <li><b>id 路径</b>：{@code entity/cats}，也可以只写末段 {@code cats}；</li>
     *   <li><b>名字</b>：{@code 猫群}、{@code 钻石}（不分大小写；唯一命中才采用，
     *       多命中返回全部候选，由调用方提示改用 id）。</li>
     * </ol>
     */
    public static Query find(ServerLevel level, String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return new Query(null, List.of());
        }
        List<Entry> all = all(level);
        // 1) 完整 id
        if (text.indexOf(':') >= 0) {
            ResourceLocation id = ResourceLocation.tryParse(text);
            if (id != null) {
                for (Entry entry : all) {
                    if (entry.id().equals(id)) {
                        return new Query(entry, List.of(entry));
                    }
                }
            }
            return new Query(null, List.of());
        }
        String lower = text.toLowerCase(Locale.ROOT);
        // 2) id 路径：完整路径或末段都认
        List<Entry> byPath = new ArrayList<>();
        for (Entry entry : all) {
            String path = entry.id().getPath().toLowerCase(Locale.ROOT);
            if (path.equals(lower) || path.endsWith("/" + lower)) {
                byPath.add(entry);
            }
        }
        if (!byPath.isEmpty()) {
            return byPath.size() == 1 ? new Query(byPath.get(0), byPath) : new Query(null, List.copyOf(byPath));
        }
        // 3) 名字
        List<Entry> byName = new ArrayList<>();
        for (Entry entry : all) {
            if (entry.name().toLowerCase(Locale.ROOT).equals(lower)) {
                byName.add(entry);
            }
        }
        if (byName.size() == 1) {
            return new Query(byName.get(0), byName);
        }
        return new Query(null, List.copyOf(byName));
    }

    /** 按路径取物品：先本模组命名空间、再 {@code minecraft}（与 {@code LuckyEvents.resolveItem} 一致）。 */
    @Nullable
    private static Item itemByPath(String path) {
        for (String namespace : List.of(ModMain.MODID, "minecraft")) {
            ResourceLocation id = ResourceLocation.tryBuild(namespace, path);
            if (id == null) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != Items.AIR) {
                return item;
            }
        }
        return null;
    }
}
