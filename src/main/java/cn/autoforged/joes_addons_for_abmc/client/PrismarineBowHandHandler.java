package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.item.PrismarineBowItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * 让<b>海晶弓</b>在拉弓时也享受原版弓那套"只画持弓那只手"的待遇（隐藏另一只手）。
 *
 * <h2>原版是怎么做的</h2>
 * 拉原版弓时副手整只手（物品 + 手臂）都不画，靠的是
 * {@code ItemInHandRenderer#evaluateWhichHandsToRender(LocalPlayer)}：
 * <pre>
 *   boolean flag = itemstack.is(Items.BOW) || itemstack1.is(Items.BOW);   // 主手 / 副手
 *   ...
 *   if (!flag && !flag1) return RENDER_BOTH_HANDS;
 *   else if (player.isUsingItem()) return selectionUsingItemWhileHoldingBowLike(player);
 * </pre>
 * 而 {@code selectionUsingItemWhileHoldingBowLike} 里又是：
 * <pre>
 *   if (!useItem.is(Items.BOW) && !useItem.is(Items.CROSSBOW)) { ...RENDER_BOTH_HANDS... }
 *   else return HandRenderSelection.onlyForHand(usedItemHand);   // ← 只画持弓那只手
 * </pre>
 * 两处都是<b>硬编码判断 {@code Items.BOW}</b>。海晶弓是自定义物品（不是 {@code Items.BOW}），
 * 于是两条判断都落空、一路走到 {@code RENDER_BOTH_HANDS} —— 表现就是拉弓时副手连物品带手臂一起露出来，
 * 和原版弓不一致。
 *
 * <h2>为什么不用 mixin</h2>
 * 想改那两个方法得面对它们所在的 {@code HandRenderSelection}：那是个
 * <b>包级私有（package-private）的嵌套枚举</b>，字段和 {@code onlyForHand} 也都不对外，
 * 混入类不在 {@code net.minecraft.client.renderer} 包里就引用不到，还得额外挂 access transformer。
 * <p>
 * 而 NeoForge 已经在更外层留了口子：{@code ItemInHandRenderer#renderHandsWithItems} 里每一只手都是
 * <pre>
 *   if (!ClientHooks.renderSpecificFirstPersonHand(hand, ...))
 *       this.renderArmWithItem(...);
 * </pre>
 * 也就是说<b>取消 {@link RenderHandEvent} 就等于"这一手整个不画"</b>（物品和手臂都没了），
 * 效果和原版的 {@code onlyForHand} 完全等价，而且用的是公开事件 API ——
 * 本模组的 {@code OrbOfLuckHandRenderer} 早就用了同一条路。
 *
 * <h2>行为</h2>
 * 只在"<b>正在使用（拉）海晶弓</b>"时生效：持弓那只手照常渲染，另一只手整个取消。
 * 不在拉弓状态（只是拿在手里、或收了弓）时完全不动 —— 这也和原版一致：
 * 原版 {@code evaluateWhichHandsToRender} 在那个分支同样返回 {@code RENDER_BOTH_HANDS}。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = ModMain.MODID)
public final class PrismarineBowHandHandler {

    private PrismarineBowHandHandler() {
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        // 只有"正在拉海晶弓"这一种情况需要特殊处理。
        // 用 getUseItem() 而不是直接看手上的物品：原版那条 onlyForHand 分支判的也正是"正在使用的那件东西"。
        if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof PrismarineBowItem)) {
            return;
        }
        // 持弓那只手照常渲染；另一只手整个不画（等价于原版的 HandRenderSelection.onlyForHand）。
        if (event.getHand() == player.getUsedItemHand()) {
            return;
        }
        event.setCanceled(true);
    }
}
