package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * 幸运实体子事件：<b>有概率开出一只远古守卫者</b>（需求 1）。
 *
 * <p>远古守卫者碰撞箱接近 2×2×2，所以和恶魂那套一样用 {@link LuckyEvents#findFreeY} 找一个不会把它塞进方块里的
 * 高度（正常情况就是幸运方块原本那一格），并设为不被自然消失。
 *
 * <p>它属于水生生物：在幸运维度里会被 {@code LuckyDimensionMobs} 当成"空气即水"处理（照常游动、不缺氧），
 * 在主世界则按原版行为扑腾（原版就是这样，不做特判）。
 */
public final class LuckyElderGuardianEvent {
    private LuckyElderGuardianEvent() {
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("entity/elder_guardian"), LuckyEventCategory.LUCKY_ENTITY,
            "有概率开出一只远古守卫者",
            LuckyElderGuardianEvent::spawnElderGuardian);
        LuckyEvents.register(event);
        return event;
    }

    private static void spawnElderGuardian(ServerLevel level, BlockPos pos, @Nullable Player player) {
        ElderGuardian guardian = EntityType.ELDER_GUARDIAN.create(level);
        if (guardian == null) return;

        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        double y = LuckyEvents.findFreeY(level, guardian, x, pos.getY(), z);

        guardian.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
        guardian.setPersistenceRequired();
        level.addFreshEntity(guardian);
    }
}
