package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * <b>iron_hook（铁抓钩）</b>：甩出铁链，要么把玩家自己拉过去，要么把别人拉过来。
 *
 * <h3>右键的三种情况</h3>
 * <ol>
 *   <li><b>对准方块</b>（{@value #HOOK_RANGE} 格以内，含近处的 {@link #useOn}）→
 *       铁链钉在命中点上，把<b>玩家自己拉过去</b>。拉扯手感完全复用<b>蜘蛛网权杖的"蛛丝游走"</b>
 *       （见 {@code ModMain.startCobwebPull}：S 形加速度、保留切向动量形成弧线摆动、
 *       拉动期间与结束后 3 秒免疫摔落/动能伤害），只是客户端把这条绳渲染成<b>铁链</b>；</li>
 *   <li><b>对准实体</b>（{@link #interactLivingEntity}，或远距离时由射线命中，见
 *       {@code ModMain.fireIronHook}）→ 铁链缠住它并把它<b>拉到玩家身边</b>：
 *       套用<b>铁块权杖的抓取逻辑</b>（"拉生物本体"的绳摆模式）。<b>只拉生物</b> ——
 *       掉落物不是合法目标；也<b>不会缴械</b>（不像权杖那样先把生物主手的物品抽走），
 *       无论它手里拿着什么，拉的都是生物本体；</li>
 *   <li>什么都没对准 → 铁链甩出去再收回（只有动画与音效）。</li>
 * </ol>
 *
 * <p>铁链贴图用的是铁块权杖那套（{@code textures/block/chain1.png} + {@code chain2.png} 的十字链节），
 * 由客户端 {@code ChainBeamClient} 渲染，因此不需要本物品自己画任何东西。
 *
 * <p><b>右键是开/关</b>：钩着生物、或正被铁链拉着走时，再按一次右键就是<b>松开</b> ——
 * 钩住的生物按当前摆锤速度甩出去，拉扯中的则断开铁链、保留当前动量飞出去
 * （见 {@code ModMain.releaseIronHook}）。所以想换目标就是"右键松开 → 再右键钩新的"。
 *
 * <p>耐久 {@value #DURABILITY}，每次成功使用扣 1 点；可附魔等级 14（= 铁质工具），配合
 * {@code #minecraft:enchantable/durability} 与 {@code #minecraft:enchantable/vanishing} 标签，
 * 可以附上<b>经验修补 / 耐久 / 消失诅咒</b>。属于幸运物品池（{@link ModItemTags#LUCKY_ITEMS}）。
 */
public class IronHookItem extends Item {
    /** 射程（格）：对准这个距离以内的方块右击即可把自己拉过去。 */
    public static final double HOOK_RANGE = 64.0D;
    /** 耐久（需求：512）。 */
    public static final int DURABILITY = 512;

    public IronHookItem(Properties properties) {
        super(properties);
    }

    /** 附魔台可附魔等级（14 = 铁质工具，与本模组其它可附魔物品一致）。 */
    @Override
    public int getEnchantmentValue() {
        return 14;
    }

    /**
     * 右键空气 / 远处目标：自己做一次 {@value #HOOK_RANGE} 格的射线检测
     * （先生物后方块），具体判定与效果都在服务端的 {@link ModMain#fireIronHook} 里。
     * <p>注：原版"右键方块"只有在交互距离内才会走 {@link #useOn}，所以够远的方块一定是从这里进来的。
     * <p><b>同一个右键也是"松开"</b>：如果已经钩着生物、或正在被铁链拉着走，
     * 这一下右键只做断开（见 {@link ModMain#releaseIronHook}），不会发起新的钩取。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof net.minecraft.server.level.ServerLevel && player instanceof ServerPlayer sp) {
            if (!ModMain.releaseIronHook(sp)) {
                ModMain.fireIronHook(sp, stack, hand);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * 右键近处方块：直接把玩家拉向命中点（不做射线检测，命中点就是点击位置）。
     * <p>已经在钩中/被拉中时，这一下右键同样是"松开"。
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (context.getLevel() instanceof net.minecraft.server.level.ServerLevel && player instanceof ServerPlayer sp) {
            if (!ModMain.releaseIronHook(sp)) {
                ModMain.startHookGrapple(sp, context.getItemInHand(), context.getHand(), context.getClickLocation());
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    /**
     * 右键近处实体（生物）：把它拉到玩家身边（只拉生物、不缴械）。
     * <p>已经在钩中/被拉中时，这一下右键同样是"松开"。
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (player.level() instanceof net.minecraft.server.level.ServerLevel && player instanceof ServerPlayer sp) {
            if (!ModMain.releaseIronHook(sp)) {
                ModMain.startHookGrab(sp, stack, hand, target);
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }
}
