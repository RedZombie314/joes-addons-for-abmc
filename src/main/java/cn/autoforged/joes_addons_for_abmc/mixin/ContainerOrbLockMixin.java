package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import cn.autoforged.joes_addons_for_abmc.item.OrbOfLuckLock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 幸运核心在背包/容器界面里的点击拦截。
 *
 * <h2>两套规则</h2>
 * <ol>
 *   <li><b>主手锁</b>（{@link OrbOfLuckLock#isLockedInHand}）：核心在主手时，涉及它所在格子的
 *       任何交互都整次取消 —— 它再也离不开主手（换格/丢弃/Shift 移动/数字键……）。</li>
 *   <li><b>背包锁</b>（{@link OrbOfLuckLock#isCarriedLocked}，用户指定）：核心在背包<b>其它</b>格子时
 *       <b>允许自由拖动</b>，但<b>不许丢弃、也不许塞进任何容器</b>
 *       （箱子/漏斗/潜影盒/合成格/交易格……判据见 {@link OrbOfLuckLock#isPlayerInventorySlot}）。</li>
 * </ol>
 *
 * <h2>为什么用 mixin 而不是事件</h2>
 * NeoForge 没有容器点击事件，而客户端会<b>先本地执行</b>
 * （{@code MultiPlayerGameMode#handleInventoryMouseClick} 先调 {@code clicked} 再发包），
 * 只在服务端拦会出现"本地换了、服务端没换"的错位。同一个 mixin 双端都会生效：
 * 客户端不预测、服务端不执行，两边状态一致（代价只是一次无害的空发包）。
 *
 * <p>手上（cursor）拿着核心的极端情况：主手锁那段本来就不拦它（那时核心已经不在主手），
 * 背包锁则按"要点进哪个格子"拦 —— 见下面的逐条判定。
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerOrbLockMixin {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void jafa_orbLockClicked(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        // ① 主手锁：维持原样（整次交互取消）
        if (OrbOfLuckLock.isLockedInHand(player)) {
            // 数字键（SWAP）交换的两头是"被点的格子 ↔ 热键栏 button"：就算点的不是核心所在格，
            // 只要另一头是锁住的那一格，也不能让它把核心换出去。
            if (clickType == ClickType.SWAP && button == OrbOfLuckLock.lockedSlot(player)) {
                ci.cancel();
                return;
            }
            AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
            if (slotId < 0 || slotId >= menu.slots.size()) {
                return;
            }
            if (menu.getSlot(slotId).getItem().is(ModItems.ORB_OF_LUCK.get())) {
                ci.cancel();
            }
            return;
        }

        // ② 背包锁：核心在身上（不在主手）时才管
        if (!OrbOfLuckLock.isCarriedLocked(player)) {
            return;
        }
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        Slot slot = slotId >= 0 && slotId < self.slots.size() ? self.getSlot(slotId) : null;
        boolean cursorHasOrb = self.getCarried().is(ModItems.ORB_OF_LUCK.get());
        boolean slotHasOrb = slot != null && slot.getItem().is(ModItems.ORB_OF_LUCK.get());
        if (!cursorHasOrb && !slotHasOrb) {
            return;   // 这次点击跟核心无关
        }

        // (a) 丢弃（背包界面里按 Q / 点到窗口外）：一律拦下 —— "无法将它丢弃"
        if (clickType == ClickType.THROW) {
            ci.cancel();
            return;
        }

        // (b) 光标上正拿着核心：只允许放进"玩家自己的背包格"，别的一律算塞进容器。
        //     注意 slot == null 这一支：那是"点到窗口外"—— 原版会把光标上的物品<b>丢到世界里</b>，
        //     所以也必须拦。
        if (cursorHasOrb) {
            if (slot == null || !OrbOfLuckLock.isPlayerInventorySlot(slot)) {
                ci.cancel();
            }
            return;
        }

        // 下面都是"被点的格子里是核心、光标空着"的情况
        // (c) Shift 快速移动：只在玩家自己的背包界面里允许（那里两头都是自己的格子）；
        //     在箱子等界面里 Shift 一下就是把核心塞进容器。
        if (clickType == ClickType.QUICK_MOVE && !(self instanceof InventoryMenu)) {
            ci.cancel();
            return;
        }

        // (d) 数字键（SWAP）：被点格是容器格、而热键栏那一格是核心时，这一下等于把核心送进容器 —— 拦下。
        //     反过来"从容器里把核心换回热键栏"是取回来，放行。
        if (clickType == ClickType.SWAP && slot != null && !OrbOfLuckLock.isPlayerInventorySlot(slot)
            && !slotHasOrb && player.getInventory().getItem(button).is(ModItems.ORB_OF_LUCK.get())) {
            ci.cancel();
        }
        // 其余（拾起 / 在背包内互换 / 拖拽到自己的格子）一律放行：用户要求"可以在物品栏内被移动"。
    }
}
