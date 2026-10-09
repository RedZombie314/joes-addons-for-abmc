package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * <b>暮色三发弓射出的箭</b>：普通原版箭，唯一的区别是——<b>命中前先把目标的无敌帧清零</b>。
 *
 * <h2>为什么需要它</h2>
 * 原版 {@code LivingEntity#hurt}（{@code LivingEntity.java:1190}）里有一段：
 * <pre>
 *   if (invulnerableTime &gt; 10 &amp;&amp; !source.is(BYPASSES_COOLDOWN)) {
 *       if (amount &lt;= lastHurt) { 直接 return false; }     // 完全忽略
 *       actuallyHurt(amount - lastHurt);                    // 更大时只补差值
 *   }
 * </pre>
 * 三发弓那三支箭是<b>同一刻</b>命中同一个目标的（只有竖直初速差 0.15 格/刻），
 * 于是第 2、3 支会被这段逻辑整段吃掉 —— 表现就是"明明射了三支、只掉一支的伤害"。
 * 清零之后再调 {@code super.onHitEntity}，{@code hurt} 里那道判断会走 else 分支：
 * 满额结算、并重新起算 20 刻无敌帧（和挨一下普通箭完全一样）。
 *
 * <h2>为什么用子类，而不是给"我们的伤害类型"加 bypasses_cooldown 标签</h2>
 * 原版箭的伤害类型是写死在 {@code AbstractArrow#onHitEntity} 里的（{@code damageSources().arrow(...)}），
 * 想换成自定义伤害类型就得把整段箭伤害结算（暴击、药水、穿透、击退……）抄一遍，很容易和原版走偏。
 * 这里只改"命中之前"这一件事，其余全部交给原版那条链。
 * <p>
 * 也不需要注册新的实体类型：{@code Arrow} 的构造用的是原版的 {@code EntityType.ARROW}，
 * 所以它落盘/跨端时就是一个普通箭（读档后变回普通 {@code Arrow} 也无所谓 —— 它只活几刻）。
 *
 * <h2>和暮色本体的差别</h2>
 * 暮色 {@code TripleBowItem} 只是把非中间那两支标记成 {@code INTANGIBLE_PROJECTILE}
 * （1.21.1 里这个组件的作用只有一条：{@code AbstractArrow} 构造时把 {@code pickup} 设成
 * {@code CREATIVE_ONLY}，即"不能被普通玩家捡起来"）。
 * 我们这边不需要：这具空壳不是玩家，{@code AbstractArrow#setOwner} 不会把 pickup 放开，
 * 本来就是 DISALLOWED（谁都捡不走），所以三支箭都不加那个组件。
 */
public class OrbTripleArrow extends Arrow {

    public OrbTripleArrow(Level level, LivingEntity shooter, ItemStack pickupItemStack,
                          @Nullable ItemStack firedFromWeapon) {
        super(level, shooter, pickupItemStack, firedFromWeapon);
    }

    /**
     * 命中实体之前把它的无敌帧清零（见类注释）。
     * <p>
     * 只动 {@code invulnerableTime} 这一个公开字段：原版那段判断的第一个条件就是它，
     * 清零即走"满额结算"分支；至于第二层保险 {@code lastHurt} 是 {@code protected}，
     * 跨包改不了，也不需要 —— 已经走不到那一步了。
     */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity hit = result.getEntity();
        if (hit instanceof LivingEntity living && living.isAlive()) {
            living.invulnerableTime = 0;
        }
        super.onHitEntity(result);
    }
}
