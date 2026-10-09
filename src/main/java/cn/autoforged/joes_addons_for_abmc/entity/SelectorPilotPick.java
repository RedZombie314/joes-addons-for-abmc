package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 旁观操控下"准星指着谁"的射线，<b>客户端与服务端共用同一份</b>
 * （客户端拿它当首选、服务端拿它做兜底复核，见 {@link SelectorPilot#handleUse}）。
 *
 * <h3>为什么不能用 {@code isPickable()} 过滤（6.5.18 的坑，就是一个"没反应"的 bug）</h3>
 * 直觉上该用原版挑实体那一套（{@code !isSpectator() && isPickable()}），但
 * {@code Entity#isPickable()} 的<b>默认值是 false</b>（Entity.java:1667），
 * 而原版 {@code ItemEntity} <b>没有覆写它</b> —— 也就是说<b>原版掉落物在准星上根本选不中</b>，
 * 只有主动覆写过的实体（本模组的 {@code LuckyItemEntity}、以及 {@code LivingEntity}）才选得中。
 * 6.5.18 里照抄了这个过滤条件，于是"对准一个普通掉落物按右键"永远不会命中，
 * 表现就是"完全没反应"（生物因为 {@code LivingEntity#isPickable()} 返回 true 所以是好的）。
 *
 * <p>所以这里改成按<b>我们自己的需求</b>过滤：是<br>
 * {@link Mob} 或 {@link ItemEntity}、还活着、不是旁观者、不是本盒子自己。
 * 顺带一提，松这个口子只影响"能不能被瞄上"，射线本身仍然先被方块挡一道（不能隔墙抓）。
 */
public final class SelectorPilotPick {

    private SelectorPilotPick() {
    }

    /**
     * 从盒子的眼睛沿给定视线打一条 {@link LuckySelectorEntity#PILOT_SEEK_REACH} 格长的射线，
     * 返回最先命中的生物/掉落物；没命中返回 {@code null}。
     *
     * @param yaw   视线水平角（玩家自己视线的 yaw，客户端与服务端用的是同一个来源）
     * @param pitch 视线俯仰角
     */
    @Nullable
    public static Entity pick(LuckySelectorEntity selector, float yaw, float pitch) {
        double reach = LuckySelectorEntity.PILOT_SEEK_REACH;
        Vec3 eye = selector.getEyePosition(1.0F);
        Vec3 look = SelectorPilotMotion.direction(yaw, pitch, true, false, false, false, false);
        if (look.lengthSqr() < 1.0E-8D) {
            return null;
        }
        Vec3 end = eye.add(look.scale(reach));

        // 方块先挡一道：隔着墙不能抓
        BlockHitResult blockHit = selector.level().clip(new ClipContext(
            eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, selector));
        Vec3 clipEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        AABB searchBox = selector.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(selector.level(), selector, eye, clipEnd,
            searchBox,
            candidate -> candidate != selector
                && !candidate.isSpectator()
                && candidate.isAlive()
                && (candidate instanceof Mob || candidate instanceof ItemEntity),
            0.3F);
        return hit == null ? null : hit.getEntity();
    }
}
