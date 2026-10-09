package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ModMain.MODID);

    public static final DeferredItem<GlisteringMelonKnifeItem> GLISTERING_MELON_KNIFE =
        ITEMS.register("glistering_melon_knife",
            () -> new GlisteringMelonKnifeItem(ModTiers.GLISTERING_MELON_KNIFE, new Item.Properties()
                .durability(50)
                .stacksTo(1)
                .attributes(GlisteringMelonKnifeItem.createAttributes())));

    public static final DeferredItem<Item> NETHERITE_CORE =
        ITEMS.register("netherite_core",
            () -> new Item(new Item.Properties().stacksTo(64)));

    /** 弹奏工具：用于演奏 playable 类可持有结构。贴图暂为空白（16×16 全透明）。 */
    public static final DeferredItem<Item> STRUMMING_TOOL =
        ITEMS.register("strumming_tool",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> GIANT_NETHERITE_BOW =
        ITEMS.register("giant_netherite_bow",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> GIANT_NETHERITE_ARROW =
        ITEMS.register("giant_netherite_arrow",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final DeferredItem<PrismarineBowItem> PRISMARINE_BOW =
        ITEMS.register("prismarine_bow",
            () -> new PrismarineBowItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<GiantNetheriteSwordItem> GIANT_NETHERITE_SWORD =
        ITEMS.register("giant_netherite_sword",
            () -> new GiantNetheriteSwordItem(ModTiers.GIANT_NETHERITE_SWORD, new Item.Properties()
                .durability(7100)
                .stacksTo(1)
                .attributes(GiantNetheriteSwordItem.createAttributes())));

    public static final DeferredItem<GiantNetheriteAxeItem> GIANT_NETHERITE_AXE =
        ITEMS.register("giant_netherite_axe",
            () -> new GiantNetheriteAxeItem(ModTiers.GIANT_NETHERITE_AXE, new Item.Properties()
                .durability(7100)
                .stacksTo(1)
                .attributes(GiantNetheriteAxeItem.createAttributes())));

    public static final DeferredItem<Item> PRISMARINE_ARROW =
        ITEMS.register("prismarine_arrow",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final DeferredItem<StaffItem> STAFF =
        ITEMS.register("staff",
            () -> new StaffItem(new Item.Properties()
                .durability(100000)
                .stacksTo(1)));

    public static final DeferredItem<GameIconItem> GAME_ICON =
        ITEMS.register("game_icon",
            () -> new GameIconItem(new Item.Properties()
                .stacksTo(1)));

    public static final DeferredItem<GameIconItem> OMEGA_GAME_ICON =
        ITEMS.register("omega_game_icon",
            () -> new GameIconItem(new Item.Properties()
                .stacksTo(1)));

    public static final DeferredItem<WitchBossSpawnEggItem> WITCH_BOSS_SPAWN_EGG =
        ITEMS.register("witch_boss_spawn_egg",
            () -> new WitchBossSpawnEggItem(new Item.Properties()
                .stacksTo(64)));

    public static final DeferredItem<BeeperSpawnEggItem> BEEPER_SPAWN_EGG =
        ITEMS.register("beeper_spawn_egg",
            () -> new BeeperSpawnEggItem(new Item.Properties()
                .stacksTo(64)));

    /**
     * 附魔千纸鹤的「贴图载体」物品：只为给 {@code EnchantedOrigamiEntity} 提供模型/贴图而存在。
     * <p>
     * 千纸鹤实体用的是 {@code ThrownItemRenderer}，它按 {@code ItemSupplier#getItem()} 返回的物品
     * 去查 {@code models/item/*.json}，因此换实体贴图 = 换一个物品模型。
     * 该物品不加入创造模式物品栏、没有配方、不参与任何玩法（正常途径无法获得）。
     */
    public static final DeferredItem<Item> ORIGAMI =
        ITEMS.register("origami",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final DeferredItem<OrigamiSpawnEggItem> ORIGAMI_SPAWN_EGG =
        ITEMS.register("origami_spawn_egg",
            () -> new OrigamiSpawnEggItem(new Item.Properties()
                .stacksTo(64)));

    public static final DeferredItem<OrigamiBottleItem> ORIGAMI_BOTTLE =
        ITEMS.register("origami_bottle",
            () -> new OrigamiBottleItem(new Item.Properties()
                .stacksTo(64)));

    public static final DeferredItem<RuinedPortalMapItem> RUINED_PORTAL_MAP =
        ITEMS.register("ruined_portal_map",
            () -> new RuinedPortalMapItem(new Item.Properties()
                .stacksTo(1)));

    /**
     * 烈焰弹发射器：右键（可长按）每 10 刻朝视线方向发射一颗烈焰人火球（速度是基准的 2 倍），
     * 拉弓动画、512 耐久、每发 1 点。属于幸运物品池（{@link ModItemTags#LUCKY_ITEMS}）。
     */
    public static final DeferredItem<FireChargeLauncherItem> FIRE_CHARGE_LAUNCHER =
        ITEMS.register("fire_charge_launcher",
            () -> new FireChargeLauncherItem(new Item.Properties()
                .durability(512)
                .stacksTo(1)));

    /**
     * wart on a stick（地狱疣生成器）：256 耐久，可附经验修补 / 耐久 / 消失诅咒。
     * <p>右击"上表面完整的方块"可在其上方（空气/草/蕨）种一个成熟地狱疣；
     * 右击生物可把地狱疣塞进它空着的头盔栏，之后只要疣还在，它就一直有缓慢 I + 失明 + 挖掘疲劳 I。
     */
    public static final DeferredItem<WartOnAStickItem> WART_ON_A_STICK =
        ITEMS.register("wart_on_a_stick",
            () -> new WartOnAStickItem(new Item.Properties()
                .durability(WartOnAStickItem.DURABILITY)
                .stacksTo(1)));

    /**
     * 爆炸之箭：任何弓/弩都能用；命中实体立刻爆炸，命中方块 100 刻后爆炸
     * （威力 4、不破坏地形、不伤及射手）。属于幸运物品池，一次发 16~64 支。
     * 贴图暂时沿用原版箭。
     */
    public static final DeferredItem<ExplosiveArrowItem> EXPLOSIVE_ARROW =
        ITEMS.register("explosive_arrow",
            () -> new ExplosiveArrowItem(new Item.Properties().stacksTo(64)));

    /**
     * 翅膀：穿在胸甲栏的鞘翅外形物品，装备后获得飞行能力（mayfly），1024 耐久，
     * 生存/冒险模式飞行中每 3 秒扣 1 点，可在铁砧用羽毛修理。属于幸运物品池。
     */
    public static final DeferredItem<WingsItem> WINGS =
        ITEMS.register("wings",
            () -> new WingsItem(new Item.Properties()
                .durability(WingsItem.DURABILITY)
                .stacksTo(1)));

    /**
     * 铁抓钩：右键 64 格以内的方块 → 用铁链把自己拉过去（复用蛛网权杖的拉扯物理）；
     * 右键实体 → 用铁链把它拉到身边（复用铁块权杖的抓取逻辑）。512 耐久，属于幸运物品池。
     */
    public static final DeferredItem<IronHookItem> IRON_HOOK =
        ITEMS.register("iron_hook",
            () -> new IronHookItem(new Item.Properties()
                .durability(IronHookItem.DURABILITY)
                .stacksTo(1)));

    /**
     * 烈焰手杖：与烈焰弹发射器同样的使用逻辑，但发射的是受重力、爆炸威力 2 的恶魂火球，
     * 速度是发射器的 2 倍。512 耐久，同属幸运物品池。
     */
    public static final DeferredItem<BlazeStaffItem> BLAZE_STAFF =
        ITEMS.register("blaze_staff",
            () -> new BlazeStaffItem(new Item.Properties()
                .durability(512)
                .stacksTo(1)));

    /**
     * 工作台帽子：戴在头上、不提供护甲值、没有耐久（同雕刻南瓜）。外观暂借皮革头盔；
     * 戴着且主手为空时按下右键会触发"合成"事件（见 {@link CraftingTableHatItem}）。属于幸运物品池。
     */
    public static final DeferredItem<CraftingTableHatItem> CRAFTING_TABLE_HAT =
        ITEMS.register("crafting_table_hat",
            () -> new CraftingTableHatItem(ModArmorMaterials.CRAFTING_TABLE_HAT,
                new Item.Properties().stacksTo(1)));

    /** 幸运方块选择器刷怪蛋：右键在视线前方召唤一个选择框。外观暂用白色混凝土贴图。 */
    public static final DeferredItem<LuckySelectorSpawnEggItem> LUCKY_SELECTOR_SPAWN_EGG =
        ITEMS.register("lucky_selector_spawn_egg",
            () -> new LuckySelectorSpawnEggItem(new Item.Properties()
                .stacksTo(64)));

    /** BeeBoss 刷怪蛋：生成一只打了 BeeBoss 标记的蜜蜂，外观套原版蜜蜂刷怪蛋（颜色在客户端注册）。 */
    public static final DeferredItem<BeeBossSpawnEggItem> BEEBOSS_SPAWN_EGG =
        ITEMS.register("beeboss_spawn_egg",
            () -> new BeeBossSpawnEggItem(new Item.Properties()
                .stacksTo(64)));

    /** 幸运核心（Orb of Luck）刷怪蛋：右键在视线落点的方块上召唤一个核心。外观暂用光源方块贴图。 */
    public static final DeferredItem<OrbOfLuckSpawnEggItem> ORB_OF_LUCK_SPAWN_EGG =
        ITEMS.register("orb_of_luck_spawn_egg",
            () -> new OrbOfLuckSpawnEggItem(new Item.Properties()
                .stacksTo(64)));

    /**
     * 幸运核心玩家刷怪蛋：直接放下一具"被幸运核心附体的玩家空壳"，
     * 皮肤从那十位玩家名里随机取（允许重复）。
     * 空壳规格与被同化产生的那一具完全一致（20 血 + 20 护甲 + 12 护甲韧性 + 保护 16、
     * 附魔金苹果回血、走位与抽签攻击、头顶光球）。
     */
    public static final DeferredItem<OrbPlayerShellSpawnEggItem> ORB_PLAYER_SHELL_SPAWN_EGG =
        ITEMS.register("orb_player_shell_spawn_egg",
            () -> new OrbPlayerShellSpawnEggItem(new Item.Properties()
                .stacksTo(64)));

    /**
     * 幸运核心（Orb of Luck）物品：世界里 INITIAL 阶段的核心被空手右击后变成它。
     * 第一人称手持时会渲染「手臂 + 手臂末端的光球」，见 client/OrbOfLuckHandRenderer。
     */
    public static final DeferredItem<OrbOfLuckItem> ORB_OF_LUCK =
        ITEMS.register("orb_of_luck",
            () -> new OrbOfLuckItem(new Item.Properties()
                .stacksTo(1)));
}
