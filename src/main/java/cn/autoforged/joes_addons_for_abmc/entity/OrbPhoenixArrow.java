package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * <b>天境「凤舞长弓」射出的火箭</b>：普通原版箭，唯一的区别是命中后把目标点燃
 * {@link #FIRE_SECONDS} 秒。
 *
 * <h2>天境本体是怎么做的（读 {@code PhoenixBowItem} + {@code AbilityHooks}）</h2>
 * <ol>
 *   <li>{@code PhoenixBowItem#customArrow} 给箭挂上天境自己的 {@code PHOENIX_ARROW} 附件，
 *       写上 {@code isPhoenixArrow = true} 与 {@code fireTime}：
 *       <b>无火焰附魔 = 20</b>，有火焰附魔 = 40；</li>
 *   <li>命中时 {@code AbilityHooks#phoenixArrowHit} 读那份附件，
 *       对被命中的实体调 {@code igniteForSeconds(fireTime)} —— 注意单位是<b>秒</b>，
 *       所以是"点燃 20 秒"（原版火焰箭只有 5 秒）；</li>
 *   <li>末影人豁免（天境的判断就是 {@code impactedEntity.getType() == EntityType.ENDERMAN}）。</li>
 * </ol>
 *
 * <h2>为什么要写一个子类</h2>
 * 那份附件是天境自有的数据类型，空壳这边拿不到也不该依赖它；而"命中后点燃 N 秒"这件事
 * 只需要在 {@code onHitEntity} 里补一句。所以照 {@link OrbTripleArrow} 的思路做一个轻量子类：
 * 它用的仍是原版 {@code EntityType.ARROW}（不需要注册新实体），其余全部交给原版那条伤害链。
 *
 * <p>箭身自己也会被点上火（{@code setRemainingFireTicks}），这样飞行途中渲染成<b>火焰箭</b>
 * —— 原版箭只有在 {@code isOnFire()} 时才会带火焰效果，而原版那 5 秒的点燃也顺带保留
 * （我们的 20 秒是在 {@code super.onHitEntity} <b>之后</b>调的，会覆盖成更长的那个值）。
 */
public class OrbPhoenixArrow extends Arrow {

    /**
     * 命中后点燃目标的秒数：天境 {@code PhoenixBowItem#customArrow} 里
     * "无火焰附魔"那一支的默认值 <b>20</b>（有火焰附魔才是 40）。
     */
    public static final float FIRE_SECONDS = 20.0F;

    public OrbPhoenixArrow(Level level, LivingEntity shooter, ItemStack pickupItemStack,
                           @Nullable ItemStack firedFromWeapon) {
        super(level, shooter, pickupItemStack, firedFromWeapon);
        // 箭身带火：既让它在空中渲染成火焰箭，也让原版那条"命中点燃 5 秒"照常生效
        this.setRemainingFireTicks((int) (FIRE_SECONDS * 20.0F));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity hit = result.getEntity();
        super.onHitEntity(result);
        // 天境的 AbilityHooks#phoenixArrowHit：末影人豁免，其余点燃 fireTime 秒
        if (!this.level().isClientSide() && hit.getType() != EntityType.ENDERMAN) {
            hit.igniteForSeconds(FIRE_SECONDS);
        }
    }
}
