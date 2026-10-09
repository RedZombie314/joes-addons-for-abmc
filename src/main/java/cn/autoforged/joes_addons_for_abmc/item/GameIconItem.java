package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.entity.DarkCloneHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class GameIconItem extends Item {
    public GameIconItem(Properties properties) {
        super(properties);
    }

    /**
     * 黑草方块（重命名后的 Negative Game Icon）右键：从黑暗分身召唤池随机抽取一种生物并召唤其黑暗分身。
     *
     * <p>走 {@code Item#use} 而不是右键事件，是因为它<b>每次右键恰好被调用一次</b>：
     * 原版在「方块交互失败」与「实体交互失败」之后都会落到 {@code useItem}，
     * 用事件的话同一次右键会同时收到 {@code RightClickBlock} / {@code EntityInteract} 与
     * {@code RightClickItem}，还得自己加去重。
     *
     * <p>代价是：右键可交互的方块（工作台、箱子）或可交互实体（村民）时，
     * 原版交互会成功消费掉这次点击，{@code use} 不会被调用，因而不会召唤。
     * 瞄准普通方块（地面等）、非交互实体、空气时都正常。
     *
     * <p>返回 {@code success} 会让客户端产生一次挥手与物品使用冷却，顺带把连点节流掉。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level instanceof ServerLevel serverLevel
            && player instanceof ServerPlayer serverPlayer
            && DarkCloneHelper.isNegativeIcon(stack)
            && DarkCloneHelper.trySummonFromPool(serverLevel, serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }
}
