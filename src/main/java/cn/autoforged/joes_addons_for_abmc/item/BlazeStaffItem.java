package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.entity.BlazeStaffFireball;
import cn.autoforged.joes_addons_for_abmc.entity.ModEntities;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 烈焰手杖（{@code blaze_staff}）。
 * <p><b>发射物</b>与 {@link FireChargeLauncherItem 烈焰弹发射器} 不同：打的是<b>恶魂火球</b>
 * （{@link BlazeStaffFireball}，命中 6 点伤害 + 爆炸），并且<b>受重力影响</b>、
 * <b>爆炸威力 2</b>、<b>飞行速度是发射器的 2 倍</b>。
 * <p><b>触发方式也不同（单发）</b>：
 * <ul>
 *   <li>只有<b>右键按下的那一瞬间</b>发射一发；按住不放不会再补发，
 *       想再打必须<b>松开右键再按</b>（原理见下方 {@link #use}）；</li>
 *   <li>没有任何冷却/间隔：松开后立刻再按就能立刻再打（只剩原版全物品通用的 4 刻右键间隔）。</li>
 * </ul>
 * <p><b>动作</b>：不再是拉弓姿势 —— 右键时手臂挥一下（与原版<b>烈焰弹</b>一致的使用动作：
 * {@code InteractionResult.sidedSuccess} 在客户端返回 SUCCESS，原版就会让玩家挥臂），
 * 使用状态本身不套任何特殊姿势（{@link UseAnim#NONE}）。
 * <p>耐久 512、每发消耗 1 点、可附耐久/经验修补/消失诅咒、同属幸运物品池
 * （{@link ModItemTags#LUCKY_ITEMS}）—— 这些与发射器相同。
 */
public class BlazeStaffItem extends FireChargeLauncherItem {
    /** 飞行速度倍率：烈焰手杖 = 烈焰弹发射器的 2 倍（0.2 → 0.4）。 */
    public static final double SPEED_MULTIPLIER = 2.0D;

    public BlazeStaffItem(Properties properties) {
        super(properties);
    }

    /**
     * 单发：按下瞬间打一发，然后进入"正在使用"状态但什么都不做。
     * <p>这个使用状态有两个作用：
     * <ol>
     *   <li>客户端返回 {@code SUCCESS}（{@code sidedSuccess}）→ 原版会让玩家挥一下手臂，
     *       也就是右键的"使用"动作（原版烈焰弹就是这么写的）；</li>
     *   <li>客户端 {@code Minecraft#startUseItem} 的触发条件是
     *       {@code keyUse.isDown() && rightClickDelay == 0 && !player.isUsingItem()}，
     *       所以只要处于使用状态，按住右键就不会再次触发 —— 必须松开（状态结束）后再按，
     *       天然做到"一发一次按键"，不需要任何冷却。</li>
     * </ol>
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            // 只有服务端真正生成火球（客户端的 use 也会被调用，用于本地预测与挥臂）
            fireOnce(level, player, stack, hand);
        }
        if (!stack.isEmpty()) {
            player.startUsingItem(hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** 单发：长按期间什么都不做（基类是每 10 刻补一发）。 */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        // 故意留空：按住不放不再发射
    }

    /** 不套任何特殊姿势（原版拉弓是 {@link UseAnim#BOW}）；右键的动作由挥臂表现。 */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    protected double fireballSpeed() {
        return FIREBALL_SPEED * SPEED_MULTIPLIER;
    }

    /** 发射的是恶魂火球，用恶魂的发射音效。 */
    @Override
    protected SoundEvent fireSound() {
        return SoundEvents.GHAST_SHOOT;
    }

    @Override
    protected Projectile createFireball(Level level, Player player, Vec3 look, Vec3 muzzle) {
        BlazeStaffFireball fireball = new BlazeStaffFireball(
            ModEntities.BLAZE_STAFF_FIREBALL.get(), level, player, look);
        applySpeed(fireball, look, muzzle);
        return fireball;
    }
}
