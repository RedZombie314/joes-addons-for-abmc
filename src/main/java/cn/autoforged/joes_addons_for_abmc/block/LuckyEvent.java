package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * 一个幸运<b>子事件</b>：分类 + 权重 + 触发行为。
 *
 * @param id          唯一标识（调试列表用；物品池生成的子事件用物品 id）
 * @param category    所属分类（见 {@link LuckyEventCategory}）
 * @param weight      权重。<b>目前全部为 1</b>，因此"每个子事件的触发概率 = 1 / 该分类子事件总数"（需求）。
 *                    以后要调整概率，直接改各自的权重即可，抽取逻辑无需改动。
 * @param action      实际行为（在方块位置执行）
 * @param description 一句话描述（调试列表用）
 */
public record LuckyEvent(ResourceLocation id, LuckyEventCategory category, int weight,
                         Action action, String description) {

    /** 子事件的行为。 */
    @FunctionalInterface
    public interface Action {
        void run(ServerLevel level, BlockPos pos, @Nullable Player player);
    }

    /** 便捷构造：权重 1（当前默认，等于"类内等概率"）。 */
    public static LuckyEvent of(ResourceLocation id, LuckyEventCategory category, String description, Action action) {
        return new LuckyEvent(id, category, 1, action, description);
    }

    /** 带权重的构造（将来调概率时用）。 */
    public static LuckyEvent weighted(ResourceLocation id, LuckyEventCategory category, int weight,
                                      String description, Action action) {
        return new LuckyEvent(id, category, Math.max(1, weight), action, description);
    }

    /** 执行这个子事件。 */
    public void run(ServerLevel level, BlockPos pos, @Nullable Player player) {
        action.run(level, pos, player);
    }
}
