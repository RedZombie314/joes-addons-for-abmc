package cn.autoforged.joes_addons_for_abmc.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 烈焰弹发射器（{@code fire_charge_launcher}）。
 * <p>右键（单击一次也会打一发）或长按右键：<b>从按下右键的瞬间</b>开始，每
 * {@link #FIRE_INTERVAL_TICKS} 游戏刻朝玩家视线方向发射一颗<b>烈焰人火球</b>
 * （{@link SmallFireball}，命中 5 点伤害并点燃）；举着的时候手臂播放拉弓动画
 * （{@link UseAnim#BOW}，长按期间移动变慢，与原版弓一致）。
 * <p><b>速度</b>：火球的初速度与每刻加速度都取自
 * {@code AbstractHurtingProjectile.INITAL_ACCELERATION_POWER}(= 0.1)，
 * 这里设成 {@link #FIREBALL_SPEED}(= 0.2)，即整条飞行速度曲线是基准的 2 倍。
 * <p><b>耐久</b>：512 点，每发火球消耗 1 点（打空即损坏并自动停止发射）；
 * 可被附上耐久、经验修补、消失诅咒（见 {@code data/minecraft/tags/item/enchantable/durability.json}）。
 * <p>本物品属于「幸运物品池」（{@link ModItemTags#LUCKY_ITEMS}）。
 * <p>烈焰手杖（{@link BlazeStaffItem}）直接继承本类，只换发射物与速度。
 */
public class FireChargeLauncherItem extends Item {
    /** 发射间隔（游戏刻）。 */
    public static final int FIRE_INTERVAL_TICKS = 10;
    /**
     * 火球的初速度与每刻加速度。
     * 原版烈焰人/恶魂火球都是 0.1（{@code AbstractHurtingProjectile.INITAL_ACCELERATION_POWER}），2 倍即 0.2。
     */
    public static final double FIREBALL_SPEED = 0.2D;
    /** 火球生成位置：玩家眼睛沿视线前方这么远，避免一出手就贴在自己身上。 */
    private static final double MUZZLE_OFFSET = 1.2D;

    public FireChargeLauncherItem(Properties properties) {
        super(properties);
    }

    /** 附魔台可附魔等级（14 = 铁质工具），配合 enchantable/durability 标签即可附上耐久/经验修补/消失诅咒。 */
    @Override
    public int getEnchantmentValue() {
        return 14;
    }

    /** 拉弓动画。 */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    /**
     * 按下右键：立即打一发（保证单击也有效，不必等 10 刻），然后进入「正在使用」状态，
     * 后续由 {@link #onUseTick} 每 10 刻补一发。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            fireOnce(level, player, stack, hand);
        }
        if (!stack.isEmpty()) {
            player.startUsingItem(hand);
        }
        return InteractionResultHolder.consume(stack);
    }

    /** 长按期间：第 10、20、30… 刻各来一发（第 0 刻已经由 {@link #use} 打过了）。 */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (level.isClientSide || !(entity instanceof Player player)) return;
        int usedTicks = this.getUseDuration(stack, entity) - remainingUseTicks;
        if (usedTicks <= 0 || usedTicks % FIRE_INTERVAL_TICKS != 0) return;
        fireOnce(level, player, stack, player.getUsedItemHand());
    }

    /** 火球的初速度与加速度（烈焰手杖覆写成 2 倍）。 */
    protected double fireballSpeed() {
        return FIREBALL_SPEED;
    }

    /** 发射音效。 */
    protected SoundEvent fireSound() {
        return SoundEvents.FIRECHARGE_USE;
    }

    /** 构造发射物（已设置好朝向、初速与加速度）。子类换发射物时覆写这里。 */
    protected Projectile createFireball(Level level, Player player, Vec3 look, Vec3 muzzle) {
        SmallFireball fireball = new SmallFireball(level, player, look);
        applySpeed(fireball, look, muzzle);
        return fireball;
    }

    /** 统一设置出膛位置、初速度与每刻加速度（速度 = {@link #fireballSpeed()}）。 */
    protected final void applySpeed(Projectile fireball, Vec3 look, Vec3 muzzle) {
        fireball.setPos(muzzle.x, muzzle.y, muzzle.z);
        if (fireball instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile hurting) {
            hurting.accelerationPower = fireballSpeed();
        }
        fireball.setDeltaMovement(look.normalize().scale(fireballSpeed()));
    }

    /**
     * 发射一颗火球：方向 = 玩家视线；每次消耗 1 点耐久。
     * <p>子类（烈焰手杖）也用这个方法打它的单发。
     */
    protected void fireOnce(Level level, Player player, ItemStack stack, InteractionHand hand) {
        if (stack.isEmpty()) return;

        Vec3 look = player.getLookAngle();
        Vec3 muzzle = player.getEyePosition().add(look.scale(MUZZLE_OFFSET));
        level.addFreshEntity(this.createFireball(level, player, look, muzzle));

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            this.fireSound(), SoundSource.PLAYERS, 1.0F, 1.0F);

        // 打空即损坏；ItemStack#hurtAndBreak 会触发 onEquippedItemBroken → 自动停止使用
        stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND
            ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    }
}
