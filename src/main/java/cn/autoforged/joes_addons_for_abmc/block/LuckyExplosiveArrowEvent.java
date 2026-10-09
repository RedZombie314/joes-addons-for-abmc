package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;

/**
 * 幸运物品子事件：<b>爆炸之箭</b>（一次 16~64 支）。
 *
 * <p>爆炸之箭同时登记在幸运物品池标签 {@code #joes_addons_for_abmc:lucky_items} 里
 * （需求："位于幸运物品池"），但<b>它的发放数量是随机的</b>，池子的默认规则只会"给 1 个"，
 * 所以这里用同 id（{@code item/explosive_arrow}）硬编码注册一个自己的子事件；
 * {@code LuckyEvents.registerPoolItemEvents} 遇到"同 id 已存在"的池内物品会跳过，
 * 因此不会出现两个爆炸之箭事件。
 */
public final class LuckyExplosiveArrowEvent {
    /** 一次发放的数量区间（含两端）。 */
    private static final int MIN_COUNT = 16;
    private static final int MAX_COUNT = 64;

    private LuckyExplosiveArrowEvent() {
    }

    /** 注册子事件（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("item/explosive_arrow"),
            LuckyEventCategory.LUCKY_ITEM,
            "给 16~64 支爆炸之箭（命中实体立刻爆炸；命中方块 5 秒后爆炸；威力 4、不破坏地形、不伤及射箭的人）",
            LuckyExplosiveArrowEvent::giveArrows);
        LuckyEvents.register(event);
        return event;
    }

    private static void giveArrows(ServerLevel level, BlockPos pos, @Nullable Player player) {
        int count = MIN_COUNT + level.random.nextInt(MAX_COUNT - MIN_COUNT + 1);
        Block.popResource(level, pos, new ItemStack(ModItems.EXPLOSIVE_ARROW.get(), count));
    }
}
