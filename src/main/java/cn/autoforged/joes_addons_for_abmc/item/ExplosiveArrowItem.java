package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.entity.ExplosiveArrow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * <b>爆炸之箭</b>：任何弓/弩都能发射（原版 {@code ProjectileWeaponItem#createProjectile} 对
 * {@link ArrowItem} 会调用 {@link #createArrow}，所以只要继承 {@code ArrowItem}，所有弓物品都能用它当弹药）。
 *
 * <p>行为见 {@link ExplosiveArrow}：
 * <ul>
 *   <li>命中<b>实体</b> → 立刻触发一次威力 {@value #EXPLOSION_POWER} 的爆炸；</li>
 *   <li>命中<b>方块</b> → 插在原地，等待 {@value #FUSE_TICKS} 刻（5 秒）后爆炸；</li>
 *   <li>两种爆炸都<b>不破坏地形</b>（{@code Level.ExplosionInteraction.NONE}）、
 *       并且<b>不会伤及射出者</b>（爆炸结算时把射手从受影响实体里剔除，见 {@code ModMain.onExplosionDetonate}）。</li>
 * </ul>
 *
 * <p>目前沿用原版箭的贴图（物品模型与实体渲染都是），属于幸运物品池；
 * 由幸运物品子事件 {@code item/explosive_arrow} 一次发放 16~64 支。
 */
public class ExplosiveArrowItem extends ArrowItem {
    /** 爆炸威力（需求：4，与原版 TNT 相同）。 */
    public static final float EXPLOSION_POWER = 4.0F;
    /** 命中方块后的引爆倒计时（需求：100 刻 = 5 秒）。 */
    public static final int FUSE_TICKS = 100;

    public ExplosiveArrowItem(Properties properties) {
        super(properties);
    }

    /** 弓/弩把这种箭当弹药时，实际射出的是 {@link ExplosiveArrow}。 */
    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, ItemStack weapon) {
        return new ExplosiveArrow(level, shooter, ammo.copyWithCount(1), weapon);
    }

    /** 发射器发射时同样用 {@link ExplosiveArrow}（走的是另一条 {@code ProjectileItem} 路径）。 */
    @Override
    public net.minecraft.world.entity.projectile.Projectile asProjectile(
        Level level, net.minecraft.core.Position pos, ItemStack stack, net.minecraft.core.Direction direction) {
        return new ExplosiveArrow(level, pos.x(), pos.y(), pos.z(), stack.copyWithCount(1), null);
    }
}
