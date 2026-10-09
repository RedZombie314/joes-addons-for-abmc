package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.BeehiveThrownHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 蜂巢（{@code minecraft:bee_nest}）可做"投掷弓"使用：
 * <ul>
 *   <li>use：对准空气且手持蜂巢时，进入"使用中"状态（拉弓）；对准方块仍走原版放置逻辑；</li>
 *   <li>getUseAnimation：蜂巢显示拉弓动画；</li>
 *   <li>getUseDuration：按住时间上限，保证可长按；</li>
 *   <li>releaseUsing：松手时（服务端）把蜂巢作为高速掉落物发射。</li>
 * </ul>
 * 注意 1.21.1 中 BlockItem 已不再覆写 use，方块物品的右键始终走 Item.use，故在此统一拦截。
 */
@Mixin(Item.class)
public abstract class ItemBeehiveUseMixin {

    /** 满拉的拉弓动画时长（刻）：只有蓄力到这个时长及之后松开才发射蜂巢。 */
    private static final int FULL_DRAW_TICKS = 20;

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void jafa_beehiveBowUse(Level level, Player player, InteractionHand hand,
                                    CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (!BeehiveThrownHelper.isBeeNest(stack)) {
            return;
        }
        // 兜底：使用蜂巢（放置/投掷入口）前，若主手蜂巢为空且物品栏有权杖带蜜蜂则当场转移
        if (!level.isClientSide()) {
            cn.autoforged.joes_addons_for_abmc.BeehiveStaffHelper.ensureBeesFromStaff(player);
            stack = player.getItemInHand(hand);
        }
        // 仅对准空气时进入拉弓状态；对准方块时不拦截，交给原版放置。
        HitResult hr = level.clip(new ClipContext(
            player.getEyePosition(1.0F),
            player.getEyePosition(1.0F).add(player.getLookAngle().scale(5.0)),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hr.getType() != HitResult.Type.MISS) {
            return;
        }
        player.startUsingItem(hand);
        cir.setReturnValue(InteractionResultHolder.consume(stack));
    }

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void jafa_beehiveBowAnimation(ItemStack stack, CallbackInfoReturnable<UseAnim> cir) {
        if (BeehiveThrownHelper.isBeeNest(stack)) {
            cir.setReturnValue(UseAnim.BOW);
        }
    }

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void jafa_beehiveBowDuration(ItemStack stack, LivingEntity entity,
                                         CallbackInfoReturnable<Integer> cir) {
        if (BeehiveThrownHelper.isBeeNest(stack)) {
            cir.setReturnValue(72000);
        }
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void jafa_beehiveRelease(ItemStack stack, Level level,
                                     LivingEntity livingEntity, int timeLeft, CallbackInfo ci) {
        if (level.isClientSide() || !BeehiveThrownHelper.isBeeNest(stack)) {
            return;
        }
        if (livingEntity instanceof Player player) {
            // 松手发射前当场转移（若主手蜂巢为空且物品栏有权杖带蜜蜂），直接用转移后的栈投掷，避免同步竞态
            ItemStack current = cn.autoforged.joes_addons_for_abmc.BeehiveStaffHelper.ensureBeesFromStaff(player);
            if (current == null) {
                current = player.getMainHandItem();
            }
            if (!BeehiveThrownHelper.isBeeNest(current)) {
                ci.cancel();
                return;
            }
            // 只有拉满弓（蓄力达到满拉动画时长及之后）才发射；提前松手则取消，不消耗蜂巢
            int usedTicks = 72000 - timeLeft;
            if (usedTicks < FULL_DRAW_TICKS) {
                ci.cancel();
                return;
            }
            BeehiveThrownHelper.throwBeehive(level, player, current);
            ci.cancel();
        }
    }
}