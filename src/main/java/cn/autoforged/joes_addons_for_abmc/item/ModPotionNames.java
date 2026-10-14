package cn.autoforged.joes_addons_for_abmc.item;

import net.minecraft.network.chat.Component;

/**
 * <b>变形药水 / 传送药水那些"自定义名字"的统一出口</b>
 * （写进 {@code DataComponents.CUSTOM_NAME} 的显示名）。
 *
 * <h2>为什么要收在一起</h2>
 * 这些名字过去是<b>硬编码中文字面量</b>，散在三个地方各写一份：
 * <ul>
 *   <li>{@link ModCreativeTab}（创造栏里那几个固定目标/随机变体）；</li>
 *   <li>{@code TransmutationBrewingRecipe}（酿造出来的"变形为 XX / 传送至 XX"）；</li>
 *   <li>{@code ModMain#onBuildCreativeTab}（改造原版食物页签里那两瓶）。</li>
 * </ul>
 * 后果有两个：英文客户端拿到的是中文；同一个名字多处各写一份，改一处漏一处。
 * 现在全部走<b>可翻译组件</b>——文案在 lang 文件里，由<b>客户端</b>按自己的语言解析。
 *
 * <h2>两个要点</h2>
 * <ul>
 *   <li><b>目标那一截用目标自己的显示名</b>：原版物品/方块/生物名本来就是可翻译组件，
 *       所以"变形为 骨头"在英文端会自动变成 "Transmute to Bone"，不需要我们再维护一份对照表
 *       （见 {@link #transmuteTo(Component)}、{@link #transportTo(Component)}）；</li>
 *   <li><b>名字是写在物品堆上的组件</b>，会随物品一起发到客户端再解析：
 *       所以中文端酿出来的酒拿到英文端看，显示的是英文（反过来也一样）。</li>
 * </ul>
 */
public final class ModPotionNames {

    /** 随机变形药水（命中目标的那一刻才抽目标）。 */
    private static final String TRANSMUTE_RANDOM = "item.joes_addons_for_abmc.potion_name.transmute_random";

    /** 固定目标变形药水：`%s` = 目标自己的显示名。 */
    private static final String TRANSMUTE_TO = "item.joes_addons_for_abmc.potion_name.transmute_to";

    /** 随机传送药水。 */
    private static final String TRANSPORT_RANDOM = "item.joes_addons_for_abmc.potion_name.transport_random";

    /** 定点传送药水（目标实体）：`%s` = 目标名。 */
    private static final String TRANSPORT_TO = "item.joes_addons_for_abmc.potion_name.transport_to";

    /** 定点传送药水（目标坐标）：`%s` = 玩家写在命名牌上的那串坐标。 */
    private static final String TRANSPORT_NEAR = "item.joes_addons_for_abmc.potion_name.transport_near";

    /** 定向传送药水：`%s` = 前进格数。 */
    private static final String TRANSPORT_FORWARD = "item.joes_addons_for_abmc.potion_name.transport_forward";

    /** 定点传送药水（目标实体不在服务器上）：保留原来那个 §kMissingno 整活。 */
    private static final String TRANSPORT_MISSING = "item.joes_addons_for_abmc.potion_name.transport_missing";

    private ModPotionNames() {
    }

    /** 随机变形药水（"变形为§krandom"）。 */
    public static Component transmuteRandom() {
        return Component.translatable(TRANSMUTE_RANDOM);
    }

    /**
     * 固定目标变形药水。
     *
     * @param target 目标自己的显示名 —— 原版物品/方块/生物名，或玩家名（玩家名用
     *               {@link Component#literal(String)}，它不属于任何语言）
     */
    public static Component transmuteTo(Component target) {
        return Component.translatable(TRANSMUTE_TO, target);
    }

    /** 随机传送药水。 */
    public static Component transportRandom() {
        return Component.translatable(TRANSPORT_RANDOM);
    }

    /** 定点传送药水：传送到某个实体那儿。 */
    public static Component transportTo(Component target) {
        return Component.translatable(TRANSPORT_TO, target);
    }

    /** 定点传送药水：传送到命名牌上写的那串坐标附近。 */
    public static Component transportNear(String coords) {
        return Component.translatable(TRANSPORT_NEAR, coords);
    }

    /** 定向传送药水：朝投掷方向水平前进若干格。 */
    public static Component transportForward(String distance) {
        return Component.translatable(TRANSPORT_FORWARD, distance);
    }

    /** 定点传送药水：目标实体不在服务器上（§kMissingno 那个整活）。 */
    public static Component transportMissing() {
        return Component.translatable(TRANSPORT_MISSING);
    }
}
