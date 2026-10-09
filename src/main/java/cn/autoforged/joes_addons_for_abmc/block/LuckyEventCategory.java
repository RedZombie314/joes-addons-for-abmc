package cn.autoforged.joes_addons_for_abmc.block;

/**
 * 幸运事件的分类（需求给定）。
 *
 * <p>抽取是"<b>全局平铺</b>"的：把所有分类的子事件合成一张表按权重抽，
 * 分类本身<b>不影响概率</b>，只起归类作用（目前每个子事件权重都是 1，所以概率 = 1 / 全部子事件总数）。
 *
 * <p>以后可能加入更多分类（例如"幸运效果""幸运天气""幸运交易"等）：
 * 在这里<b>追加枚举值</b>即可 —— 抽取、命令、列出等逻辑都是按枚举遍历的，不需要改别的地方。
 * 如果某个分类暂时没有子事件，抽取时会自动跳过它（不会把概率浪费在空分类上）。
 */
public enum LuckyEventCategory {
    /** 幸运物品：给玩家物品（来源之一是 {@code #joes_addons_for_abmc:lucky_items} 幸运物品池，但两者并不对等）。 */
    LUCKY_ITEM("幸运物品"),
    /** 幸运实体：在方块位置生成实体。 */
    LUCKY_ENTITY("幸运实体"),
    /** 幸运结构：在方块位置搭建结构。 */
    LUCKY_STRUCTURE("幸运结构");

    // ===== 预留位置：以后的新分类加在这行上面 =====

    private final String displayName;

    LuckyEventCategory(String displayName) {
        this.displayName = displayName;
    }

    /** 中文显示名（命令反馈与调试列表用；不参与本地化，属于开发者/调试用途）。 */
    public String displayName() {
        return displayName;
    }
}
