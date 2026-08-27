package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.item.ModDataComponents;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 屏障权杖相关客户端辅助判断。
 */
public class BarrierStaffHelper {

    /**
     * 判断当前客户端玩家是否在正手（主手）持有屏障权杖。
     * 屏障权杖 = ModItems.STAFF 且 BLOCKTYPE 为 "barrier"。
     */
    public static boolean isHoldingBarrierStaff() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return false;
        return isBarrierStaff(player.getMainHandItem());
    }

    /**
     * 判断物品堆是否就是屏障权杖。
     */
    public static boolean isBarrierStaff(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() != ModItems.STAFF.get()) return false;
        return "barrier".equals(stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"));
    }
}