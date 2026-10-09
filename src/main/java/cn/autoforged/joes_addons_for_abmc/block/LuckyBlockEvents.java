package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.item.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 幸运方块的「幸运事件」入口。
 *
 * <p>事件体系见 {@link LuckyEvents} / {@link LuckyEventCategory}：分类（幸运物品 / 幸运实体 / 幸运结构 …）
 * 之下各有若干<b>子事件</b>，抽取是全局平铺的（目前每个子事件权重都是 1 → 概率 = 1 / 全部子事件总数）。
 *
 * <p><b>当前没有任何子事件，也没有任何表现层效果</b>（音效/粒子已按需求移除）：
 * 挖掉幸运方块只会调用 {@link LuckyEvents#roll}，事件表为空时不发生任何事。
 * 等事件表确定后往 {@link LuckyEvents} 注册即可，这里的代码无需改动。
 */
public final class LuckyBlockEvents {
    private LuckyBlockEvents() {
    }

    /**
     * 触发一次幸运事件（服务端）。
     *
     * <p>若破坏者是玩家，会先尝试解锁幸运方块的合成配方（见 {@link #awardLuckyBlockRecipe}）——
     * 即"玩家第一次破坏幸运方块后，工作台配方书里出现 8 金锭 + 投掷器的配方"。
     *
     * @param level  所在世界
     * @param pos    被挖掘的幸运方块坐标（事件以它为中心）
     * @param player 挖掘者（可能为 null，例如被其他方式破坏）
     * @return 实际触发的子事件（事件表为空 → {@code null}）
     */
    @Nullable
    public static LuckyEvent trigger(ServerLevel level, BlockPos pos, @Nullable Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            awardLuckyBlockRecipe(serverPlayer);
        }
        return LuckyEvents.roll(level, pos, player);
    }

    /** 幸运方块配方 id（{@code data/joes_addons_for_abmc/recipe/lucky_block.json}）。 */
    private static final net.minecraft.resources.ResourceLocation LUCKY_BLOCK_RECIPE =
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            cn.autoforged.joes_addons_for_abmc.ModMain.MODID, "lucky_block");

    /**
     * 解锁幸运方块配方。重复调用是安全的（已解锁时不会重复授予）。
     *
     * <p>这里不走"配方进度（advancement）"路线，而是直接把配方授予该玩家 —— 相当于原版
     * {@code /recipe give} 的做法，配方随即出现在工作台（合成）配方书页里。
     */
    public static void awardLuckyBlockRecipe(net.minecraft.server.level.ServerPlayer player) {
        if (player.getRecipeBook().contains(LUCKY_BLOCK_RECIPE)) return;
        player.awardRecipesByKey(java.util.List.of(LUCKY_BLOCK_RECIPE));
    }

    /** 从 {@code #joes_addons_for_abmc:lucky_items} 里随机抽一件（池为空时返回空栈）。 */
    public static ItemStack rollLuckyPoolItem(ServerLevel level) {
        List<Item> candidates = luckyPoolItems(level);
        if (candidates.isEmpty()) return ItemStack.EMPTY;
        return new ItemStack(candidates.get(level.random.nextInt(candidates.size())));
    }

    /** 幸运物品池的全部物品（按标签解析；标签不存在或为空时返回空表）。 */
    public static List<Item> luckyPoolItems(ServerLevel level) {
        Optional<HolderSet.Named<Item>> tag = level.registryAccess()
            .registryOrThrow(Registries.ITEM)
            .getTag(ModItemTags.LUCKY_ITEMS);
        if (tag.isEmpty()) return List.of();
        List<Item> items = new ArrayList<>();
        for (Holder<Item> holder : tag.get()) {
            Item item = holder.value();
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                items.add(item);
            }
        }
        return items;
    }

    /** 幸运物品池当前是否为空（调试/命令用）。 */
    public static boolean luckyPoolEmpty(ServerLevel level) {
        return luckyPoolItems(level).isEmpty();
    }
}
