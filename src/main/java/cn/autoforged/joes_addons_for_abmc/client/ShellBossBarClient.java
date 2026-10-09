package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.entity.PlayerShellEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <b>附体空壳血条的客户端一半</b>：在①（本体血量、紫色、原版 boss 条）右边补画②（伤害吸收、黄色）。
 *
 * <h2>血条长什么样（用户指定）</h2>
 * <pre>
 *                  ┌──────── ① 紫色：本体血量 ────────┐┌─ ② 黄色 ─┐
 *   （屏幕顶部）     ██████████████████████████████████▓▓▓▓▓▓▓▓▓▓▓▓▓
 *                  137 像素（原版 182 的 3/4，仍然居中） 伤害吸收段，最长也是 137
 * </pre>
 * <ul>
 *   <li>① 本体血量（{@code boss_bar/purple_*}，女巫三阶段那条紫）<b>居中</b>；</li>
 *   <li>② 伤害吸收（{@code boss_bar/yellow_*}，女巫二阶段那条黄）从①<b>右边缘往外接</b>，
 *       长度 = {@code 伤害吸收 / 最大生命 × 条长}；没有黄心时整段不画；</li>
 *   <li><b>两段都比原版短</b>：整条缩到 3/4（{@link #BAR_LENGTH_SCALE}）—— 用户反馈"黄段会超出屏幕"；
 *       另外黄段还会按"屏幕右边缘还剩多少"再夹一次（{@link #EDGE_MARGIN}），
 *       所以窄 GUI（大 GUI 缩放/小窗口）下也绝不会画到屏幕外。</li>
 * </ul>
 *
 * <h2>为什么要自己画（而不是让原版画①、我们补②）</h2>
 * 原版 {@code BossHealthOverlay} 把条长写死成 182 像素（{@code x = 屏幕宽/2 - 91}），
 * 事件里也不给"改成多宽"的口子；想缩短①就只能<b>取消原版那一遍、自己按想要的长宽画两段</b>。
 * 于是这里连标题也一起自己画（原版把标题画在整条正上方，而我们现在只画到 3/4 宽）。
 * 事件里的 {@code increment}（竖直步进）不受取消影响，多条 boss 条的堆叠交给原版照旧处理。
 *
 * <h2>怎么知道"这条 boss 血条"是哪具空壳的</h2>
 * 原版事件只给一条 boss 条，没有实体信息。所以服务端把血条 UUID 同步到了空壳身上
 * （{@code PlayerShellEntity#getShellBarId()}），这里每隔
 * {@link #RESCAN_INTERVAL} 刻扫一遍<b>已加载的空壳</b>重建"血条 UUID → 实体 id"的对照表
 * （空壳数量很少，一秒一次的扫描可以忽略；这样也避免了每帧扫实体）。
 *
 * <h2>为什么伤害吸收要自己同步</h2>
 * 原版 {@code LivingEntity#getAbsorptionAmount()} 是<b>只在服务端算</b>的字段（客户端只会给自己画黄心），
 * 所以空壳把吸收量放进了自己的同步字段（{@code getVisualAbsorption()}）供这里读取。
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME, modid = ModMain.MODID)
public final class ShellBossBarClient {

    /** 原版 boss 条的尺寸（像素）：{@code BossHealthOverlay} 里写死的 182×5。 */
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;

    /**
     * 条长缩放：<b>3/4</b>（用户指定："两部分的血条都缩短一些，比如缩短到 3/4 长度看看效果"）。
     * <p>
     * 缩短的是<b>两段各自的最大长度</b>（137 像素），① 仍然居中；于是整条最宽也就是
     * ①137 + ②137 = 274 像素，比原来"①182 居中 + ②最多182"窄得多，普通 GUI 缩放下不会再顶到屏幕边。
     */
    private static final float BAR_LENGTH_SCALE = 0.75F;

    /** 缩短后的单段条长（像素）：182 × 3/4 ≈ 137。 */
    private static final int SEGMENT_WIDTH = Math.round(BAR_WIDTH * BAR_LENGTH_SCALE);

    /** 屏幕右边缘留白（像素）：② 最多画到离边缘这么远，再宽就截断。 */
    private static final int EDGE_MARGIN = 4;

    /**
     * 背景（那条"框"）右端的收尾列数：贴图第 <b>179~181</b> 列。
     * <p>
     * {@code boss_bar/*} 的贴图是 182×5、<b>两端各带 3 像素描边</b>的一根棒子
     * （实测像素：0~2 列与 179~181 列是描边/高光，中间是平色）。
     * 直接把贴图裁短 = 把右端描边一起切掉，看起来就像"右边被削了一刀"。
     */
    private static final int BACKGROUND_CAP = 3;

    /**
     * 填充色右端的收尾列数：贴图第 <b>177~181</b> 列（比框多 2 列）。
     * <p>
     * 填充贴图自己的右端还有一段收尾（177 是高光线、178~181 逐级压暗到描边），
     * 原版满血时画的正是这 5 列 —— 所以"填满整条"时必须把这段补上，
     * 否则满血时右边会比正常少这几个像素（用户反馈的正是这个）。
     */
    private static final int FILL_END_CAP = 5;

    /** ① 用的紫：女巫三阶段那条血条的颜色（用户指定）。 */
    private static final ResourceLocation PURPLE_BACKGROUND =
        ResourceLocation.withDefaultNamespace("boss_bar/purple_background");
    private static final ResourceLocation PURPLE_PROGRESS =
        ResourceLocation.withDefaultNamespace("boss_bar/purple_progress");

    /** ② 用的黄：女巫二阶段那条血条的颜色（用户指定）。 */
    private static final ResourceLocation YELLOW_BACKGROUND =
        ResourceLocation.withDefaultNamespace("boss_bar/yellow_background");
    private static final ResourceLocation YELLOW_PROGRESS =
        ResourceLocation.withDefaultNamespace("boss_bar/yellow_progress");

    /** 对照表多久重建一次（刻）：0.5 秒（空壳很少，扫一遍很便宜；扫得快一点，血条刚出现时不会"慢半拍"）。 */
    private static final int RESCAN_INTERVAL = 10;

    /** 屏幕上出现"不认识的 boss 血条"时，把下一次重建提前到最多这么多刻之后。 */
    private static final int UNKNOWN_BAR_RESCAN_DELAY = 5;

    /** 血条 UUID → 空壳实体 id（每 {@link #RESCAN_INTERVAL} 刻重建）。 */
    private static final Map<UUID, Integer> SHELL_BY_BAR = new HashMap<>();

    /**
     * <b>见过的所有"空壳血条"的 UUID</b>（只增不减，见下面 {@link #MAX_KNOWN_BARS} 的封顶）。
     * <p>
     * 为什么要留这本账：{@link #SHELL_BY_BAR} 每 {@link #RESCAN_INTERVAL} 刻按<b>当前维度</b>里
     * 已加载的空壳重建一次，玩家一换维度，旧维度那具空壳就扫不到了、映射随之消失。
     * 这时原版那条 boss 条其实还在客户端的 {@code BossHealthOverlay} 里（服务端那边也未必来得及
     * 发"移除"——空壳所在的区块早就没人、不 tick 了），于是它就会<b>退化成原版那条 182 像素的长条</b>
     * 一直挂在屏幕顶上（用户反馈的正是这个）。
     * <p>有了这本账，那种"认得出是我们的条、但这一帧找不到那具空壳"的情况就能被识破 ——
     * 直接取消原版那一遍、什么都不画，也就是"正常消失"；等玩家回到那具空壳身边、
     * 映射重新建立起来，它自然又会出现。
     */
    private static final java.util.Set<UUID> KNOWN_SHELL_BARS = new java.util.HashSet<>();

    /** {@link #KNOWN_SHELL_BARS} 的封顶：超过就整本清掉（换过很多次世界/刷过很多具空壳时也不至于无限涨）。 */
    private static final int MAX_KNOWN_BARS = 256;

    private static int rescanCooldown;

    private ShellBossBarClient() {
    }

    /** 一秒一次地重建对照表（顺带在退出世界/换维度时清空）。 */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (--rescanCooldown > 0) {
            return;
        }
        rescanCooldown = RESCAN_INTERVAL;
        rebuild();
    }

    private static void rebuild() {
        SHELL_BY_BAR.clear();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof PlayerShellEntity shell)) {
                continue;
            }
            String barId = shell.getShellBarId();
            if (barId == null || barId.isEmpty()) {
                continue;
            }
            try {
                UUID id = UUID.fromString(barId);
                SHELL_BY_BAR.put(id, shell.getId());
                remember(id);
            } catch (IllegalArgumentException ignored) {
                // 同步字段里出现了不是 UUID 的字符串：忽略这一条
            }
        }
    }

    /** 把一条血条 UUID 记进 {@link #KNOWN_SHELL_BARS}（封顶见 {@link #MAX_KNOWN_BARS}）。 */
    private static void remember(UUID barId) {
        if (KNOWN_SHELL_BARS.size() >= MAX_KNOWN_BARS) {
            KNOWN_SHELL_BARS.clear();
        }
        KNOWN_SHELL_BARS.add(barId);
    }

    /**
     * 原版每画一条 boss 血条都会先发这个事件。<b>我们这条自己画</b>，所以先取消原版那一遍，
     * 再按"缩短到 3/4"的尺寸把两段和标题都画出来（画法照抄 {@code BossHealthOverlay#drawBar}：
     * 贴图按 182×5 裁切，只取左边 N 像素，所以画短了也不会有拉伸变形）。
     */
    @SubscribeEvent
    public static void onBossBarProgress(CustomizeGuiOverlayEvent.BossEventProgress event) {
        UUID barId = event.getBossEvent().getId();
        Integer entityId = SHELL_BY_BAR.get(barId);
        if (entityId == null) {
            // 认得出是我们的条、但这一帧扫不到那具空壳（典型：玩家刚换了维度，空壳留在旧维度，
            // 那边区块已经不 tick、服务端也来不及摘观众）→ 就当它不存在：取消原版那一遍、什么都不画。
            // 不这么做的话它会退化成"原版那条 182 像素的长条"挂在屏幕上（用户反馈的正是这个）。
            if (KNOWN_SHELL_BARS.contains(barId)) {
                hideSlot(event);
            }
            // 其它情况可能是"刚出现的空壳血条"（对照表还没来得及重建）：把下一次重建提前，
            // 免得头半秒里它还是原版画的那条"长条、没有黄段"。别的 mod 的 boss 条也会走到这里，
            // 但也只是让重建提前到 5 刻一次，不会变成每帧扫实体。
            rescanCooldown = Math.min(rescanCooldown, UNKNOWN_BAR_RESCAN_DELAY);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !(level.getEntity(entityId) instanceof PlayerShellEntity shell)) {
            // 映射还在、实体却已经不在这个维度里了（换维度之后实体 id 可能指向别人/为空）：
            // 同样按"我们的条"处理 —— 藏起来，别让它以原版长条的样子留在屏幕上。
            if (KNOWN_SHELL_BARS.contains(barId)) {
                hideSlot(event);
                rescanCooldown = Math.min(rescanCooldown, UNKNOWN_BAR_RESCAN_DELAY);
            }
            return;
        }
        event.setCanceled(true);   // 原版画的是整条 182 像素、且不给②留位置

        GuiGraphics graphics = event.getGuiGraphics();
        int y = event.getY();
        // 缩短后仍然居中：原版给的是"182 宽那条"的左边缘，往右挪半个差值
        int x = event.getX() + (BAR_WIDTH - SEGMENT_WIDTH) / 2;

        // ① 本体血量：紫（女巫三阶段那条）
        //    背景（框）始终按"条有多长"画，并<b>补上右端描边</b>，这样空血那头也是完整的框
        drawBar(graphics, PURPLE_BACKGROUND, x, y, SEGMENT_WIDTH, BACKGROUND_CAP);
        //    填充：用四舍五入而不是原版的截断，免得满血时少 1 像素；填满整条时补上贴图自带的收尾
        int mainWidth = Math.round(Mth.clamp(event.getBossEvent().getProgress(), 0.0F, 1.0F) * SEGMENT_WIDTH);
        if (mainWidth > 0) {
            drawBar(graphics, PURPLE_PROGRESS, x, y, mainWidth,
                mainWidth >= SEGMENT_WIDTH ? FILL_END_CAP : 0);
        }

        // ② 伤害吸收（黄心）：接在①右侧；没有黄心就整段不画（用户指定）
        float absorption = shell.getVisualAbsorption();
        if (absorption > 0.0F) {
            float maxHealth = Math.max(1.0F, shell.getMaxHealth());
            int extra = Math.round(Math.min(1.0F, absorption / maxHealth) * SEGMENT_WIDTH);
            // 再按"屏幕右边还剩多少"夹一次：窄 GUI 下黄段可能真的没地方放，
            // 宁可画短一点也不画到屏幕外面去（用户反馈过"黄段超出屏幕"）
            int room = mc.getWindow().getGuiScaledWidth() - (x + SEGMENT_WIDTH) - EDGE_MARGIN;
            extra = Math.min(extra, Math.max(0, room));
            if (extra > 0) {
                int x2 = x + SEGMENT_WIDTH;
                drawBar(graphics, YELLOW_BACKGROUND, x2, y, extra, BACKGROUND_CAP);
                drawBar(graphics, YELLOW_PROGRESS, x2, y, extra,
                    extra >= SEGMENT_WIDTH ? FILL_END_CAP : 0);
            }
        }

        // 标题：取消原版之后得自己画（原本是画在整条 182 像素的正中，现在对齐缩短后的①）
        Component name = event.getBossEvent().getName();
        Font font = mc.font;
        graphics.drawString(font, name,
            x + SEGMENT_WIDTH / 2 - font.width(name) / 2, y - 9, 0xFFFFFF);
    }

    /**
     * <b>把这一条"藏起来"</b>：取消原版那一遍，并把这一步的竖直步进设成 0。
     *
     * <h2>为什么光 {@code setCanceled(true)} 不够（用户实测的 bug）</h2>
     * 原版 {@code BossHealthOverlay#render} 的循环是这么写的：
     * <pre>
     *   for (BossEvent bar : this.events.values()) {
     *       event = onCustomizeBossEventProgress(...);   // 我们这里把 event 取消掉
     *       if (!event.isCanceled()) { 画这一条 }
     *       j += event.getIncrement();                   // ← 取消与否都照样加
     *       if (j &gt;= guiHeight / 3) break;                // ← 超过屏幕高 1/3 就不画了
     *   }
     * </pre>
     * 也就是说"取消"只是<b>不画</b>，这条血条<b>依然占着一格竖直位置</b>。于是换维度之后：
     * 旧维度那些空壳的条还在客户端的 {@code events} 里（服务端那边区块早就不 tick，发不出"移除"），
     * 它们虽然被藏起来了，却把后面的条一路往下顶；顶过 {@code guiHeight / 3} 之后原版直接
     * {@code break} —— <b>新维度里刚放的空壳血条就一条都画不出来了</b>
     * （用户实测：主世界 → {@code /tp} 下界，在下界用刷怪蛋放的空壳没有血条）。
     * 把步进设成 0 就等于"这条不占位置"，被藏起来的条再也不会挤掉真正要显示的条。
     * <p>同一个道理：一次放出很多具空壳时，排在后面那几条原本也会被这个 {@code break} 吃掉。
     */
    private static void hideSlot(CustomizeGuiOverlayEvent.BossEventProgress event) {
        event.setCanceled(true);
        event.setIncrement(0);
    }

    /**
     * 画一条"<b>在指定宽度处收尾</b>"的条：左边按原样裁出来，再把这贴图<b>右端的收尾像素</b>贴到末尾。
     * <p>
     * 为什么必须补这一步：{@code boss_bar/*} 的贴图是一根 182 像素宽、<b>两端各 3 像素描边</b>的棒子
     * （填充色贴图右端另有 5 像素收尾）。原版靠"背景永远画满 182、填充从左边裁"来保证右端总是完整的；
     * 我们为了缩短把背景也裁短了，于是右端描边被切掉 —— 表现就是"右边少几个像素、像被削了一刀"
     * （用户反馈：满血且没有黄心时正是这样）。
     * <p>
     * {@code cap = 0} 时就是纯裁剪：中途的血量边缘照原版那样保持硬边。
     */
    private static void drawBar(GuiGraphics graphics, ResourceLocation sprite, int x, int y,
                                int width, int cap) {
        if (width <= 0) {
            return;
        }
        if (cap <= 0 || width <= cap) {
            graphics.blitSprite(sprite, BAR_WIDTH, BAR_HEIGHT, 0, 0, x, y, width, BAR_HEIGHT);
            return;
        }
        int body = width - cap;
        graphics.blitSprite(sprite, BAR_WIDTH, BAR_HEIGHT, 0, 0, x, y, body, BAR_HEIGHT);
        graphics.blitSprite(sprite, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH - cap, 0,
            x + body, y, cap, BAR_HEIGHT);
    }
}
