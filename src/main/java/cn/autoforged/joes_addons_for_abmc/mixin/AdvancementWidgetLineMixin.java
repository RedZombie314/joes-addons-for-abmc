package cn.autoforged.joes_addons_for_abmc.mixin;

import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * 成就页「独立成就簇」的渲染调整：与同页其它成就**没有关系**、只是共用同一个标签页的成就。
 * <p>
 * 原版一个标签页 = 一棵成就树（{@code AdvancementsScreen} 按 {@code AdvancementTree.roots()} 建页），
 * 页内每个非根节点都会在 {@code AdvancementWidget.drawConnectivity} 里画一条连向
 * “最近可见祖先”的线（祖先由 {@code getFirstVisibleParent} 向上跳过没有 display 的节点得到）。
 * 因此若只是把成就 {@code parent} 指向同页的另一个成就（本模组有多个成就共用一页，
 * 目的仅是"出现在同一页"），视觉上就会被画成“那个成就的子成就”。
 * <p>
 * 本 Mixin 只做三件事，且**仅影响客户端渲染**（不改动服务端逻辑、成就数据与存档）：
 * <ol>
 *   <li>对 {@link #LINELESS_ADVANCEMENTS} 中列出的成就，跳过它与父节点之间的那条连线
 *       （子节点的连线仍照常递归绘制）——它们是各自“独立簇”的簇根；</li>
 *   <li>在构造期给独立簇**整棵子树**的 x 坐标额外加 0.5 格（14px）：既让簇根与页根明显分开，
 *       又保证簇内父子间距保持原版尺度（若只挪簇根，它的子节点会与它重叠）；</li>
 *   <li>对「隐藏且尚未完成」的节点，同样跳过它连向父节点的那条线（见
 *       {@link #jafa$isHiddenAndUndone()}）——原版只藏节点本身，连线照画，
 *       等于提前泄露"这里藏着一个成就"。</li>
 * </ol>
 * 今后若还想新增“与同页其它成就无关联”的成就簇，把它的 id 加进 {@link #LINELESS_ADVANCEMENTS} 即可；
 * 簇内的后代成就无需列出（它们靠 {@link #jafa$inLinelessSubtree()} 自动跟随位移）。
 * <p>
 * <b>“页头”根成就</b>：一个标签页的名字与背景都取自该页<b>根成就</b>的 display（见
 * {@code AdvancementTab#create} / 构造器），而根成就必须带 display 才会建页
 * （没有 display 的根会被整个跳过）。因此本模组的「AVM的维度」「AVM的生物」两页各有一个
 * **只作为页头存在**的根成就：它 {@code hidden: true} + {@code minecraft:impossible}，
 * 永远不授予——而 {@code AdvancementWidget#draw} 对「hidden 且未完成」的节点什么都不画，
 * 于是这个页头在页内不可见，标签页名字与背景却照常生效。
 * 它的直接子节点（各独立簇的簇根）必须列进 {@link #LINELESS_ADVANCEMENTS}：
 * {@code getFirstVisibleParent} 只跳过“没有 display”的祖先，hidden 的页头仍会被当作连线目标，
 * 不屏蔽就会画出一条指向空白处的悬空连线。
 */
@Mixin(AdvancementWidget.class)
public abstract class AdvancementWidgetLineMixin {

    /** 需要隐藏父连线（并整簇右移）的成就 id：它们是"与同页其它成就共用标签页、但彼此无依赖"的簇根。 */
    private static final Set<String> LINELESS_ADVANCEMENTS = Set.of(
        "joes_addons_for_abmc:witch_boss_defeated",
        "joes_addons_for_abmc:angry_birds",
        "joes_addons_for_abmc:greed",
        "joes_addons_for_abmc:more_dimensions"
    );

    @Shadow @Final private AdvancementNode advancementNode;
    @Shadow @Final private List<AdvancementWidget> children;
    @Shadow @Final private DisplayInfo display;
    @Shadow @Nullable private AdvancementProgress progress;

    /** 本 widget 是否就是某个"独立簇"的簇根（不画父连线）。 */
    private boolean jafa$isLinelessRoot() {
        return this.advancementNode != null
            && LINELESS_ADVANCEMENTS.contains(this.advancementNode.holder().id().toString());
    }

    /** 本 widget 是否位于某个"独立簇"内（簇根自身或其后代）——它们整体右移，保持簇内相对布局不变。 */
    private boolean jafa$inLinelessSubtree() {
        for (AdvancementNode node = this.advancementNode; node != null; node = node.parent()) {
            if (LINELESS_ADVANCEMENTS.contains(node.holder().id().toString())) return true;
        }
        return false;
    }

    /**
     * 本 widget 是不是"隐藏且尚未完成"的——原版 {@code draw} / {@code drawHover} 对这种节点
     * 什么都不画（图标、边框、名字全都不出现），但 {@code drawConnectivity} <b>照样会画出</b>
     * 它连向父节点的那条线，等于把"这里藏着一个成就"提前泄露出去。
     * 隐藏成就（本模组的「见证奇迹」）要的正是"拿到之前完全看不见"，所以那条线也一并抹掉。
     */
    private boolean jafa$isHiddenAndUndone() {
        return this.display.isHidden() && (this.progress == null || !this.progress.isDone());
    }

    /** 跳过"自己与父节点之间"的那条线；子节点的连线保持原版行为。 */
    @Inject(method = "drawConnectivity", at = @At("HEAD"), cancellable = true)
    private void jafa$skipParentLine(GuiGraphics guiGraphics, int x, int y, boolean dropShadow, CallbackInfo ci) {
        if (!this.jafa$isLinelessRoot() && !this.jafa$isHiddenAndUndone()) return;
        for (AdvancementWidget child : this.children) {
            child.drawConnectivity(guiGraphics, x, y, dropShadow);
        }
        ci.cancel();
    }

    /** 原版排布把可见子节点放在父节点右侧 1 格（28px）；独立簇再加 0.5 格以与页根明显分开。 */
    @Redirect(method = "<init>",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/DisplayInfo;getX()F"))
    private float jafa$extraGapForLinelessCluster(DisplayInfo display) {
        return display.getX() + (this.jafa$inLinelessSubtree() ? 0.5F : 0.0F);
    }
}
