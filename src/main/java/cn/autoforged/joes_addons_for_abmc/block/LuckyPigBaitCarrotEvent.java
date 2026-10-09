package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.entity.ModEntities;
import cn.autoforged.joes_addons_for_abmc.entity.PigBaitCarrotEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;

/**
 * 幸运物品子事件：<b>诱猪胡萝卜</b>（需求 10）。
 *
 * <p>掉出一个 {@link PigBaitCarrotEntity}（自定的掉落物实体），行为见该实体类：
 * 吸引 20 格内的猪、玩家捡不起来、碰到猪或满 1 分钟消失。
 *
 * <h3>那个名字</h3>
 * 需求："如果尝试用漏斗或者漏斗矿车捡起，物品栏里的名字会显示为'不要让它过来！'"。
 * 实现方式是把这句警告<b>写在胡萝卜这件物品的自定义名字上</b>：
 * <ul>
 *   <li>玩家亲手捡不走（拾取延迟 32767），所以平时根本没机会在物品栏里看到它；</li>
 *   <li>漏斗/漏斗矿车走的是另一条路（{@code HopperBlockEntity} 不看拾取延迟），能把胡萝卜吸进容器，
 *       这时容器里显示的就是这个名字 —— 也就是需求描述的那个画面。</li>
 * </ul>
 */
public final class LuckyPigBaitCarrotEvent {

    /** 漏斗里显示的名字（需求原文）。 */
    public static final String BAIT_NAME = "不要让它过来！";

    private LuckyPigBaitCarrotEvent() {
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("item/pig_bait_carrot"), LuckyEventCategory.LUCKY_ITEM,
            "掉出一个诱猪胡萝卜（吸引 20 格内的猪、玩家捡不起来、碰到猪或 1 分钟后消失）",
            LuckyPigBaitCarrotEvent::dropCarrot);
        LuckyEvents.register(event);
        return event;
    }

    private static void dropCarrot(ServerLevel level, BlockPos pos, @Nullable Player player) {
        PigBaitCarrotEntity carrot = ModEntities.PIG_BAIT_CARROT.get().create(level);
        if (carrot == null) return;

        ItemStack stack = new ItemStack(Items.CARROT);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(BAIT_NAME));
        carrot.setItem(stack);
        carrot.setPos(pos.getX() + 0.5D, pos.getY() + 0.25D, pos.getZ() + 0.5D);
        carrot.setDeltaMovement(
            (level.random.nextDouble() - 0.5D) * 0.1D,
            0.2D,
            (level.random.nextDouble() - 0.5D) * 0.1D);
        // 拾取规则（玩家捡不走）在实体构造里已经设好，这里不再重复。
        level.addFreshEntity(carrot);
    }
}
