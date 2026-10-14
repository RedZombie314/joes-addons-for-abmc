package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.item.GlisteringMelonKnifeItem;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

public class PrismarineArrow extends AbstractArrow {

    public PrismarineArrow(EntityType<? extends PrismarineArrow> type, Level level) {
        super(type, level);
        this.setBaseDamage(8.0);
        this.pickup = Pickup.DISALLOWED;
    }

    public PrismarineArrow(Level level, LivingEntity shooter, ItemStack pickupItemStack, ItemStack firedFromWeapon) {
        super(ModEntities.PRISMARINE_ARROW.get(), shooter, level, pickupItemStack, sanitizeWeapon(firedFromWeapon));
        this.setBaseDamage(8.0);
        this.pickup = Pickup.DISALLOWED;
    }

    public PrismarineArrow(Level level, double x, double y, double z, ItemStack pickupItemStack, ItemStack firedFromWeapon) {
        super(ModEntities.PRISMARINE_ARROW.get(), x, y, z, level, pickupItemStack, sanitizeWeapon(firedFromWeapon));
        this.setBaseDamage(8.0);
        this.pickup = Pickup.DISALLOWED;
    }

    /**
     * 把"武器"参数里的空栈折算成 {@code null}。
     * <p>
     * 原版 {@code AbstractArrow} 的构造器只认两种输入：{@code null} = 没拿武器，或者一把真武器；
     * <b>非 null 但为空</b>的 ItemStack 在服务端会直接抛
     * {@code IllegalArgumentException("Invalid weapon firing an arrow")}
     * （见 {@code AbstractArrow} 构造器里那句 {@code if (p_345487_.isEmpty())}）。
     * <p>
     * 调用方给的多半是"手上那件东西"（例如附体空壳的主手），而那件东西完全可能已经被消耗掉 ——
     * 那是调用方的状态问题，不该升级成"服务器崩在 Ticking entity"。折算成 null 的语义也完全正确：
     * 空手射出的箭本来就没有武器附魔（穿刺/火矢/力量）可以继承。
     */
    private static ItemStack sanitizeWeapon(ItemStack weapon) {
        return weapon == null || weapon.isEmpty() ? null : weapon;
    }

    /**
     * 视锥剔除用的包围盒：比碰撞箱放宽 1.5 格。
     * <p>
     * 这支箭是<b>用物品模型画的</b>（见 {@code PrismarineArrowRenderer}），渲染出来的长度由那边的
     * {@code REFERENCE_PIXELS} 决定，现在约 <b>2.27 格</b> —— 远远超出 {@code 0.5×0.5} 的碰撞箱。
     * 而 {@code EntityRenderDispatcher#shouldRender} 只按
     * {@code getBoundingBoxForCulling().inflate(0.5)} 判断，碰撞箱中心一出屏幕边缘就整支箭被剔除，
     * 表现是箭尖明明还在画面里、整支箭却"啪"地消失。
     * <p>
     * 放宽 1.5 格（算上原版再加的 0.5，合计 ±2.25 格）刚好覆盖这支箭的半长，代价只是多画几次。
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(1.5D);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.PRISMARINE_ARROW.get());
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        if (target instanceof Player player && ModMain.isPlayerBlocking(player.getUUID())) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack held = player.getItemInHand(hand);
                if (held.getItem() instanceof GlisteringMelonKnifeItem) {
                    player.spawnAtLocation(held.copy());
                    player.setItemInHand(hand, ItemStack.EMPTY);
                    ModMain.clearPlayerBlocking(player.getUUID());
                    break;
                }
            }
        }
        super.onHitEntity(result);
    }
}
