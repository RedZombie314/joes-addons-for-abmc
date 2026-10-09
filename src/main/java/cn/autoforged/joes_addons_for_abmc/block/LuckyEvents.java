package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 幸运子事件注册表与抽取逻辑（<b>只提供机制，不含任何具体事件</b>）。
 *
 * <h3>结构（按需求）</h3>
 * <ol>
 *   <li><b>分类</b>：{@link LuckyEventCategory}（幸运物品 / 幸运实体 / 幸运结构，未来可加），只起<b>归类</b>作用；</li>
 *   <li><b>全局平铺抽取</b>：把所有分类的子事件合成一张表，每个子事件的权重目前都是 1，
 *       所以<b>触发概率 = 1 / 全部子事件总数</b>（条目多的分类整体更容易被抽到）；</li>
 *   <li>以后要改概率，用 {@link LuckyEvent#weighted} 给不同子事件不同权重即可。</li>
 * </ol>
 *
 * <h3>子事件从哪来</h3>
 * <ol>
 *   <li>写死的事件：在 {@link #registerAll()} 里逐个注册（启动时一次性执行）；</li>
 *   <li>幸运物品池 {@code #joes_addons_for_abmc:lucky_items} 里的物品：标签是数据包内容，
 *       要拿到 {@link ServerLevel} 才能解析，所以由 {@link #registerPoolItemEvents(ServerLevel)}
 *       在抽取 / 列表时惰性注册成一个个独立的"给一件该物品"子事件（幂等，重复调用安全）。</li>
 * </ol>
 */
public final class LuckyEvents {
    /** 已注册的子事件（按分类）。 */
    private static final Map<LuckyEventCategory, List<LuckyEvent>> REGISTERED = new EnumMap<>(LuckyEventCategory.class);

    /**
     * <b>debug 池</b>：{@code ModConfig.DEBUG_MODE} 打开时，破坏幸运方块（或 {@code /jafa lucky event}）
     * 会从这里的子事件里<b>等概率</b>抽一个执行，方便直接验证子事件。
     *
     * <p>池子有两个来源：
     * <ol>
     *   <li>写死的事件：在 {@link #registerAll()} 里用 {@link #markDebug(LuckyEvent...)} 登记
     *       （登记几个就是几选一）；</li>
     *   <li><b>物品池里的某几件物品</b>：由 {@link #DEBUG_POOL_ITEM_IDS} 按物品 id 声明 ——
     *       它们的子事件是运行时惰性注册的，所以是"建出来的时候顺手进池"。
     *       <b>当前 debug 池就是这一种</b>：默认是铁抓钩 {@code iron_hook} 与地狱疣生成器
     *       {@code wart_on_a_stick}，另外可以用命令（见下）在游戏里随时增删。</li>
     * </ol>
     *
     * <p><b>运行时可改（6.7.3 起）</b>：{@code /jafa luckyblockresult <物品名>} 往池里加一件物品、
     * {@code /jafa luckyblockclear} 清空这份物品列表 —— 不用重编译、不用重启游戏就能改
     * "debug 模式下能开出什么"。加几件就是几选一（等概率），与 {@link #addDebugItem(Item)}
     * 与 {@link #clearDebugItems()} 是同一套逻辑。
     *
     * <p>历史：6.5.5 之前是单个 {@code latest} 字段（debug 下必定触发它，一次只能验一个），
     * 6.5.6 改成这个可以放多个、等概率抽的池子。
     *
     * <p><b>等概率与权重无关</b>：抽的时候不看 {@link LuckyEvent#weight()}，纯粹按下标均匀取。
     *
     * <p>池子为空时（比如声明的物品都不在标签里）debug 模式退回"按名单 / 随机抽"，不会开出空事件。
     */
    private static final List<LuckyEvent> DEBUG_POOL = new ArrayList<>();

    static {
        for (LuckyEventCategory category : LuckyEventCategory.values()) {
            REGISTERED.put(category, new ArrayList<>());
        }
        registerAll();
    }

    private LuckyEvents() {
    }

    /**
     * 把子事件登记进 <b>debug 池</b>（{@link #DEBUG_POOL}），debug 模式下从池里等概率抽一个。
     *
     * <p><b>它不负责注册</b>事件本身：注册仍由各事件类的 {@code register()} 完成，
     * 所以调用点写成 {@code markDebug(LuckySomethingEvent.register())}。
     *
     * <p>去重<b>按子事件 id</b>：同一个 id 只算一次，否则会变相提高它被抽到的概率。
     * （按 id 而不是按对象去重是有原因的：命令 {@code /jafa luckyblockresult} 可能在那个物品的
     * 正式子事件被惰性注册<b>之前</b>就先建了一个"临时副本"，两者 id 相同、对象不同。）
     */
    public static void markDebug(LuckyEvent... events) {
        for (LuckyEvent event : events) {
            if (event == null || hasDebugEvent(event.id())) {
                continue;
            }
            DEBUG_POOL.add(event);
        }
    }

    /** debug 池里是否已经有这个 id 的子事件（按 id 去重，见 {@link #markDebug}）。 */
    private static boolean hasDebugEvent(ResourceLocation eventId) {
        for (LuckyEvent event : DEBUG_POOL) {
            if (event.id().equals(eventId)) {
                return true;
            }
        }
        return false;
    }

    /** debug 池的当前内容（只读副本，命令/调试输出用）。 */
    public static List<LuckyEvent> debugPool() {
        return List.copyOf(DEBUG_POOL);
    }

    /** 这个子事件是不是 debug 池成员（{@code /jafa lucky list} 的标记用）。按 id 判定，理由见 {@link #markDebug}。 */
    public static boolean isDebugEvent(LuckyEvent event) {
        return hasDebugEvent(event.id());
    }

    /**
     * 从 debug 池里<b>等概率</b>抽一个（池空返回 {@code null}）。
     * <p>用下标均匀取，<b>不看权重</b>：需求要的是"这些事件在 debug 模式下概率相同"。
     */
    @Nullable
    public static LuckyEvent debugEvent(RandomSource random) {
        if (DEBUG_POOL.isEmpty()) {
            return null;
        }
        return DEBUG_POOL.get(random.nextInt(DEBUG_POOL.size()));
    }

    /** 注册一个子事件（启动时调用；将来也可以做成数据包/配置驱动）。 */
    public static void register(LuckyEvent event) {
        REGISTERED.computeIfAbsent(event.category(), k -> new ArrayList<>()).add(event);
    }

    /**
     * <b>这里注册所有写死（非数据包驱动）的幸运子事件</b>，启动时执行一次。
     *
     * <p>每个子事件类都提供自己的 {@code register()}，返回注册后的 {@link LuckyEvent}；
     * 想让它进 <b>debug 池</b>（debug 模式下等概率被抽到）的，用 {@link #markDebug(LuckyEvent...)}
     * 接收它的返回值即可 —— 登记几个就是几选一。
     *
     * <p>幸运物品池里的物品<b>不</b>在这里注册：标签要 {@code ServerLevel} 才能解析，
     * 见 {@link #registerPoolItemEvents(ServerLevel)}。
     */
    private static void registerAll() {
        // ===== 事件表：在此注册 =====
        // 幸运实体：刷新 5~8 只随机毛色的成年猫（最多一只豹猫；玩家破坏时猫被驯服、豹猫被信任）
        LuckyCatsEvent.register();
        // 幸运物品：向四周抛洒钻石/金锭/绿宝石，并播放玩家升级音效
        LuckyScatterEvent.register();
        // 幸运实体：正上方落红石块，随后每 10 刻落一个未点燃的下落 TNT，共 5 个
        LuckyFallingVolleyEvent.register();
        // 幸运结构：生成幸运水井（丢金粒许愿；每座井只能生效一次）
        LuckyWellEvent.register();
        // 幸运实体：僵尸 Bob（全套保护4/耐久3/荆棘3 钻石甲 + 击退2/锋利5/耐久3 钻石剑，20% 小僵尸）
        LuckyBobEvent.register();
        // 幸运实体：猪套娃（6 只猪叠成塔，最上面背一个无业游民）
        LuckyPigTowerEvent.register();
        // 幸运实体：女巫骑着恶魂（恶魂照常乱飞丢火球，女巫在背上扔药水）
        LuckyWitchGhastEvent.register();
        // 幸运实体：骷髅骑士（骷髅骑着会像蝙蝠一样乱飞、不落地的骷髅马；两者免疫摔落，骷髅自带抗火）
        LuckySkeletonKnightEvent.register();
        // 幸运实体：随机毛色的狼（玩家破坏时该狼的主人就是该玩家）
        LuckyWolfEvent.register();
        // 幸运物品：英雄药水（一瓶）+ 英雄套装（一件，四件等概率，权重 4 = 原先四件之和）
        LuckyHeroEvents.registerAll();
        // ===== 6.5.5 新增的 12 个子事件（需求 1~12）=====
        // 注：这 12 个在 6.5.5~6.5.7 期间曾被登记进 debug 池；6.5.8 起 debug 池改绑到
        //     "铁抓钩 + 地狱疣生成器"（见 DEBUG_POOL_ITEM_IDS），所以这里都只是普通注册。
        // 幸运实体：有概率开出一只远古守卫者
        LuckyElderGuardianEvent.register();
        // 幸运实体：骑着鸡的兔子
        LuckyRabbitChickenEvent.register();
        // 幸运实体：头顶不断喷发金粒（不可被捡起）的猪
        LuckyGoldNuggetPigEvent.register();
        // 幸运实体：骑着蝙蝠的雪傀儡
        LuckySnowGolemBatEvent.register();
        // 幸运实体：穿金马铠 + 马鞍的马（移速 15 格/秒、跳跃力量 1；玩家破坏时立刻归该玩家）
        LuckyGoldenHorseEvent.register();
        // 幸运物品：64~128 个金锭向四周喷出
        LuckyGoldIngotBurstEvent.register();
        // 幸运实体：3~4 个冲天烟花火箭（水平方向随机，仰角 60~80 度之间随机波动）
        LuckyFireworkVolleyEvent.register();
        // 幸运实体：烟花猪（尾部喷烟花、20 格/秒前飞，撞方块或 5 秒后在消失点散射大量烟花粒子）
        LuckyFireworkPigEvent.register();
        // 幸运实体：在玩家头顶 10 格处召唤一个下落的铁砧
        LuckyFallingAnvilEvent.register();
        // 幸运物品：诱猪胡萝卜（吸引 20 格内的猪、玩家捡不走、碰到猪或 1 分钟后消失）
        LuckyPigBaitCarrotEvent.register();
        // 幸运实体：召唤一只苦力怕
        LuckyCreeperEvent.register();
        // 幸运实体：持续 5 秒、会反弹弹射物的盾牌物品展示实体（正常大小；6.5.7 起去掉了 10 倍放大）
        LuckyReflectShieldEvent.register();
        // 上面这 12 个里，属于"生物"的那些（远古守卫者 / 兔子骑鸡 / 金粒猪 / 雪傀儡骑蝙蝠 / 金马 / 烟花猪 / 苦力怕）
        // 已经登记进幸运生物池（见 LuckyMobSpawner#CREATURE_POOL_IDS），所以幸运维度会直接把它们刷出来。
        // 幸运物品：爆炸之箭（16~64 支，硬编码数量；池内同名 id 会被跳过，不会重复注册）
        LuckyExplosiveArrowEvent.register();
        // ★ debug 池：6.5.8 起绑定"铁抓钩 iron_hook + 地狱疣生成器 wart_on_a_stick"（各 1/2），
        //   这两件都是幸运物品池里的物品，子事件在 registerPoolItemEvents(level) 里惰性注册，
        //   所以登记写在 DEBUG_POOL_ITEM_IDS 上，不在这里。
        //   6.7.3 起这份名单还能在游戏里随时改：/jafa luckyblockresult <物品名> 加、
        //   /jafa luckyblockclear 清空（见 debugPoolItemIds / addDebugItem / clearDebugItems）。
        // 幸运物品：幸运物品池（#joes_addons_for_abmc:lucky_items）里的每个物品，各自成为一个
        //   "给一件该物品"的独立子事件，权重与其它子事件相同（都是 1）。
        //   注意：标签属于数据包内容，只有拿到 ServerLevel 才能解析，所以这里注册不了，
        //   由 registerPoolItemEvents(level) 在抽取 / 列表时惰性补齐（见该方法注释）。
    }

    /** 幸运物品池里每个物品的子事件一次给多少个（"给一件该物品"）。 */
    private static final int POOL_ITEM_COUNT = 1;

    /** 已经按标签注册过的池内物品（幂等：重复调用不会重复注册）。 */
    private static final java.util.Set<ResourceLocation> REGISTERED_POOL_ITEMS = new java.util.HashSet<>();

    /**
     * <b>debug 池里"由物品构成"的那一份名单</b>（按<b>物品 id</b> 记；有序、去重）。
     *
     * <p>两个来源：
     * <ol>
     *   <li><b>代码里的默认值</b>：<b>铁抓钩</b> {@code iron_hook} 与 <b>地狱疣生成器</b>
     *       {@code wart_on_a_stick}（6.5.8 的需求：debug 下反复拿这两件东西测功能）；</li>
     *   <li><b>命令</b>（6.7.3 新增）：{@code /jafa luckyblockresult <物品名>} 往里加、
     *       {@code /jafa luckyblockclear} 清空 —— 池子内容可在游戏里随时改，不用重编译也不用重启。</li>
     * </ol>
     *
     * <p>为什么不能像写死事件那样在 {@link #registerAll()} 里直接 {@code markDebug(...)}：
     * 池内物品的子事件是 {@link #registerPoolItemEvents(ServerLevel)} 在<b>运行时</b>按标签惰性注册的
     * （标签是数据包内容，类初始化时拿不到，也不该在那时候去碰物品注册表）。
     * 所以这里只记下物品 id，等那个物品的子事件真的被建出来时，顺手把它塞进 debug 池。
     *
     * <p>命令加进来的物品：本来就在幸运物品池里的，复用那个已注册的子事件（与默认值同一条路）；
     * 不在池里的，由 {@link #addDebugItem(Item)} 现场建一个<b>只属于 debug 池</b>的子事件
     * （不注册进常规事件表，免得悄悄改掉不开 debug 时的正常抽取概率）。
     *
     * <p>注意这个名单是<b>内存态</b>：重启游戏会回到上面那条默认值，命令加的会丢。
     */
    private static final java.util.Set<ResourceLocation> DEBUG_POOL_ITEM_IDS =
        new java.util.LinkedHashSet<>(List.of(
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "iron_hook"),
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "wart_on_a_stick")));

    /**
     * 把幸运物品池（{@code #joes_addons_for_abmc:lucky_items}）里的每个物品注册成<b>独立</b>的子事件
     * （"给一件该物品"，分类为幸运物品，权重 1 —— 与其它子事件一样）。
     *
     * <p>标签是数据包内容，只有拿到 {@link ServerLevel} 才能解析（数据包还可以随时增删成员），
     * 所以不能在 {@link #registerAll()} 里静态注册；改为在抽取 / 列表时调用本方法惰性补齐，
     * 用 {@link #REGISTERED_POOL_ITEMS} 去重，重复调用是安全的。
     *
     * <p>{@link #DEBUG_POOL_ITEM_IDS} 里的物品在这里被建出来之后会<b>顺手进 debug 池</b>。
     */
    public static void registerPoolItemEvents(ServerLevel level) {
        for (Item item : LuckyBlockEvents.luckyPoolItems(level)) {
            ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (!REGISTERED_POOL_ITEMS.add(itemId)) continue;
            // 已经有了同 id 的子事件（例如爆炸之箭：需要随机的发放数量，所以自己硬编码注册了）
            // → 不重复注册，避免同一件物品出现两个子事件、变相提高它的权重。
            if (hasEventWithId(id("item/" + itemId.getPath()))) continue;
            LuckyEvent event = registerItemReward(item, POOL_ITEM_COUNT);
            if (event != null && DEBUG_POOL_ITEM_IDS.contains(itemId)) {
                markDebug(event);
            }
        }
    }

    /** debug 池里当前"由物品构成"的那份名单（按加入顺序的物品 id；命令输出用）。 */
    public static List<ResourceLocation> debugPoolItemIds() {
        return List.copyOf(DEBUG_POOL_ITEM_IDS);
    }

    /**
     * 把一件物品加进 <b>debug 池</b>（命令 {@code /jafa luckyblockresult <物品名>} 用）。
     *
     * <p>加几件就是几选一（{@link #debugEvent} 按下标等概率抽，不看权重），可反复输入把想测的物品
     * 一件件凑进来。
     *
     * <p><b>子事件优先复用</b>：这件物品本来就有同 id 的子事件时（例如它就在幸运物品池
     * {@code #joes_addons_for_abmc:lucky_items} 里，或像爆炸之箭那样自己硬编码注册过），直接用那一个 ——
     * 不会出现两个子事件、也不会变相提高它的概率；否则现场建一个"给一件该物品"的子事件，
     * 这个子事件<b>只进 debug 池、不进常规事件表</b>（理由见 {@link #DEBUG_POOL_ITEM_IDS}）。
     *
     * <p>重复加同一件物品不会重复计数（名单按 id 去重）。
     *
     * @return 放进池里的子事件；物品不在物品注册表里时返回 {@code null}
     */
    @Nullable
    public static LuckyEvent addDebugItem(Item item) {
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) {
            return null;
        }
        ResourceLocation eventId = id("item/" + itemId.getPath());
        LuckyEvent event = byId(eventId); // 已经是正式子事件 → 复用，别再建一个同 id 的
        if (event == null) {
            event = buildItemReward(item, POOL_ITEM_COUNT);
        }
        if (event == null) {
            return null;
        }
        DEBUG_POOL_ITEM_IDS.add(itemId);
        if (!hasDebugEvent(event.id())) {
            DEBUG_POOL.add(event);
        }
        return event;
    }

    /**
     * 清空 debug 池里<b>由物品构成</b>的那部分（命令 {@code /jafa luckyblockclear} 用）：
     * 把 {@link #DEBUG_POOL_ITEM_IDS} 名单与它们在 {@link #DEBUG_POOL} 里的子事件一起摘掉。
     *
     * <p>用 {@link #markDebug(LuckyEvent...)} 直接登记的<b>写死事件不动</b>：那些是代码里写死的，
     * 清掉也会在下次启动时回来，硬清只会让人困惑。
     *
     * <p>清空后池子若为空，debug 模式就退回"幸运名单 / 随机抽取"（见 {@link #roll}）。
     *
     * @return 实际从 debug 池里移除的子事件个数
     */
    public static int clearDebugItems() {
        java.util.Set<ResourceLocation> eventIds = new java.util.HashSet<>();
        for (ResourceLocation itemId : DEBUG_POOL_ITEM_IDS) {
            eventIds.add(id("item/" + itemId.getPath()));
        }
        int before = DEBUG_POOL.size();
        DEBUG_POOL.removeIf(event -> eventIds.contains(event.id()));
        DEBUG_POOL_ITEM_IDS.clear();
        return before - DEBUG_POOL.size();
    }

    /** 事件表里是否已经存在该 id 的子事件（池内物品注册前的去重检查）。 */
    private static boolean hasEventWithId(ResourceLocation eventId) {
        return byId(eventId) != null;
    }

    /**
     * 按 id 找一个已注册的子事件（找不到返回 {@code null}）。
     * <p>
     * 用途：幸运名单里的元素可能是"整个生物事件"（见 {@link LuckyRoster#enqueueMob}），
     * 它只存了事件 id，放出来时要按 id 把事件本体找回来（{@link LuckyRosterEvents#next}）。
     */
    @Nullable
    public static LuckyEvent byId(ResourceLocation eventId) {
        for (List<LuckyEvent> events : REGISTERED.values()) {
            for (LuckyEvent event : events) {
                if (event.id().equals(eventId)) return event;
            }
        }
        return null;
    }

    /**
     * 从 {@code y0} 开始<b>由下往上</b>找一个"实体放进去不会卡进方块"的最低高度，用于把生物刷在空中。
     *
     * <p>每格试一次：把探测实体摆到该位置，用 {@link net.minecraft.world.level.CollisionGetter#noCollision(net.minecraft.world.entity.Entity)}
     * 判断它的碰撞箱是否与方块相交，<b>返回第一个不相交的高度</b>。因为是从下往上找的第一个可用位置，
     * 所以实体的碰撞箱底面正好贴在<b>下方方块的上表面</b>上（例如 {@code y0} 处就是空气、下面一格是地面时，
     * 实体就正好站在那个地面上，也就是"贴着方块上方"），同时不会被塞进方块里窒息。
     *
     * <p>一直找不到（比如幸运方块被埋在被封死的地下、或上方完全没有足够空间）就退回到该列
     * <b>地表之上 3 格</b>，至少保证是露天的，免得大体积生物（比如 4×4×4 的恶魂）刷进石头里憋死。
     */
    public static double findFreeY(ServerLevel level, net.minecraft.world.entity.Entity probe,
                                   double x, double y0, double z) {
        for (double y = y0; y <= y0 + 12.0D; y += 1.0D) {
            probe.setPos(x, y, z);
            if (level.noCollision(probe)) return y;
        }
        int groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(z));
        return Math.max(y0, groundY + 3.0D);
    }

    /** 某个分类当前全部子事件（会先按标签惰性注册幸运物品池里的物品子事件）。 */
    public static List<LuckyEvent> eventsOf(ServerLevel level, LuckyEventCategory category) {
        registerPoolItemEvents(level);
        return new ArrayList<>(REGISTERED.getOrDefault(category, List.of()));
    }

    /**
     * 抽一次幸运事件并执行。
     *
     * <p><b>优先级</b>（从高到低）：
     * <ol>
     *   <li><b>debug 模式</b>（{@code ModConfig.DEBUG_MODE}）：不再按权重随机，而是从 <b>debug 池</b>
     *       （{@link #DEBUG_POOL}，见 {@link #markDebug}）里<b>等概率</b>抽一个 —— 池里登记了几个就是几选一，
     *       方便一次验证一批新事件。池子为空时退回下面两条。
     *       它<b>永远压过幸运名单</b>（需求）：开着 debug 时开方块一定出自 debug 池，名单留着不动；</li>
     *   <li><b>幸运名单</b>：名单（{@link LuckyRoster}）非空就从<b>队首</b>取一个元素放出来
     *       （FIFO，见 {@link LuckyRosterEvents}）——这是需求里的"优先从名单中选取元素"；</li>
     *   <li>正常随机抽取（{@link #draw}）。</li>
     * </ol>
     *
     * @return 实际触发的子事件（名单为空且事件表也为空时返回 {@code null}）
     */
    @Nullable
    public static LuckyEvent roll(ServerLevel level, net.minecraft.core.BlockPos pos, @Nullable Player player) {
        registerPoolItemEvents(level); // 幸运物品池里的物品子事件（标签要 ServerLevel 才能解析）
        // 1) debug 模式：从 debug 池等概率抽一个，压倒一切（包括幸运名单）
        LuckyEvent event = null;
        if (cn.autoforged.joes_addons_for_abmc.config.ModConfig.DEBUG_MODE.get()) {
            event = debugEvent(level.random);
        }
        // 2) 名单优先
        if (event == null) {
            event = LuckyRosterEvents.next(level);
        }
        // 3) 再不行才随机抽
        if (event == null) {
            event = draw(level, null);
        }
        if (event == null) return null;
        event.run(level, pos, player);
        return event;
    }

    /**
     * 只抽不执行（命令/调试用）。
     *
     * <p><b>全局平铺</b>（需求）：把所有分类的子事件合成一张表，按权重抽一个 ——
     * 目前权重全为 1，所以每个子事件的概率 = 1 / <b>全部</b>子事件总数。
     *
     * @param onlyCategory 只在该分类内抽（调试用）；为 {@code null} 时全局平铺
     */
    @Nullable
    public static LuckyEvent draw(ServerLevel level, @Nullable LuckyEventCategory onlyCategory) {
        List<LuckyEvent> pool = new ArrayList<>();
        if (onlyCategory != null) {
            pool.addAll(eventsOf(level, onlyCategory));
        } else {
            for (LuckyEventCategory category : LuckyEventCategory.values()) {
                pool.addAll(eventsOf(level, category));
            }
        }
        return pickWeighted(pool, level.random);
    }

    /** 按权重抽一个（权重全为 1 时就是等概率 1/N）。 */
    @Nullable
    private static LuckyEvent pickWeighted(List<LuckyEvent> list, RandomSource random) {
        if (list.isEmpty()) return null;
        int total = 0;
        for (LuckyEvent event : list) total += event.weight();
        int roll = random.nextInt(Math.max(1, total));
        for (LuckyEvent event : list) {
            roll -= event.weight();
            if (roll < 0) return event;
        }
        return list.get(list.size() - 1);
    }

    /** 子事件 id 的便捷构造（命名空间 = 本模组）。 */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ModMain.MODID, path);
    }

    /**
     * 便捷方法：把"给一件该物品"注册成"幸运物品"分类的子事件，并返回注册后的 {@link LuckyEvent}
     * （调用方可能还要把它塞进 debug 池，见 {@link #registerPoolItemEvents}）。
     */
    @Nullable
    public static LuckyEvent registerItemReward(Item item, int count) {
        LuckyEvent event = buildItemReward(item, count);
        if (event != null) {
            register(event);
        }
        return event;
    }

    /**
     * 只<b>构造</b>"给 N 个该物品"的子事件，<b>不注册</b>。
     *
     * <p>给命令 {@code /jafa luckyblockresult} 用：命令临时加进来的物品只能进 debug 池，
     * 不能顺手改掉正常游玩时的抽取概率（那要改幸运物品池标签，见 {@link #DEBUG_POOL_ITEM_IDS}）。
     * 常规注册走 {@link #registerItemReward(Item, int)}。
     */
    @Nullable
    public static LuckyEvent buildItemReward(Item item, int count) {
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) {
            return null;
        }
        return LuckyEvent.of(id("item/" + itemId.getPath()), LuckyEventCategory.LUCKY_ITEM,
            "给 " + count + " 个 " + item.getDescription().getString(),
            (level, pos, player) -> Block.popResource(level, pos, new ItemStack(item, count)));
    }

    /**
     * 一份"物品名"的解析结果（命令 {@code /jafa luckyblockresult} 用）。
     *
     * @param item    唯一命中的物品；没命中或有歧义时为 {@code null}
     * @param matches 命中到的物品 id：唯一命中时是那一个，有歧义时是<b>全部</b>候选（列给玩家看）
     */
    public record ItemQuery(@Nullable Item item, List<ResourceLocation> matches) {
        /** 有歧义：输入的名字对上了多件物品（例如各种"音乐唱片"）。 */
        public boolean ambiguous() {
            return this.item == null && this.matches.size() > 1;
        }
    }

    /**
     * 解析玩家输入的一份"物品名"（命令 {@code /jafa luckyblockresult <物品名>} 用），依次尝试：
     * <ol>
     *   <li><b>完整 id</b>：{@code minecraft:diamond}、{@code joes_addons_for_abmc:iron_hook}；</li>
     *   <li><b>裸路径</b>：{@code diamond} —— <b>先按本模组命名空间</b>找、再按 {@code minecraft} 找；
     *       顺手容忍 {@code item/xxx} 这种写法（跟 {@code /jafa lucky event} 的 id 形式一致）；</li>
     *   <li><b>游戏内名字</b>：例如"铁抓钩"、"钻石" —— 拿每件物品的名字逐字比较，
     *       唯一命中才采用；多件同名（例如一堆"音乐唱片"）时返回全部候选，
     *       由调用方提示玩家改用 id。</li>
     * </ol>
     *
     * <p>名字匹配靠的是 {@link net.minecraft.locale.Language} 里加载的语言表：单人游戏的服务端与客户端
     * 在同一个 JVM 里、用的是客户端选的语言，所以中文名一般能匹配上；纯服务端通常只有 en_us。
     */
    public static ItemQuery resolveItem(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return new ItemQuery(null, List.of());
        }
        // 容忍 /jafa lucky event 里那种 "item/xxx" 写法
        if (text.startsWith("item/") && text.indexOf(':') < 0) {
            text = text.substring("item/".length());
        }
        // 1) 完整 id
        if (text.indexOf(':') >= 0) {
            ResourceLocation full = ResourceLocation.tryParse(text);
            Item item = full == null ? null : itemOrNull(full);
            return item == null ? new ItemQuery(null, List.of()) : new ItemQuery(item, List.of(full));
        }
        // 2) 裸路径：先本模组、再原版
        for (String namespace : List.of(ModMain.MODID, "minecraft")) {
            ResourceLocation guess = ResourceLocation.tryBuild(namespace, text);
            if (guess == null) continue;
            Item item = itemOrNull(guess);
            if (item != null) {
                return new ItemQuery(item, List.of(guess));
            }
        }
        // 3) 游戏内名字
        List<ResourceLocation> matches = new ArrayList<>();
        for (Item candidate : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (candidate == net.minecraft.world.item.Items.AIR) continue;
            if (!candidate.getDescription().getString().equals(text)) continue;
            matches.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(candidate));
        }
        if (matches.size() == 1) {
            return new ItemQuery(itemOrNull(matches.get(0)), matches);
        }
        return new ItemQuery(null, List.copyOf(matches));
    }

    /** 按 id 取物品；查不到（默认项 {@code minecraft:air}）返回 {@code null}。 */
    @Nullable
    private static Item itemOrNull(ResourceLocation itemId) {
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
        return item == net.minecraft.world.item.Items.AIR ? null : item;
    }
}
