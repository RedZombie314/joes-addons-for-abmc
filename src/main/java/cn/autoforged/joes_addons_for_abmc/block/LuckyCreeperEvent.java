package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * 幸运实体子事件：<b>召唤一只苦力怕</b>（需求 11）。
 *
 * <p>就是一只<b>普通的原版苦力怕</b>（不预先点燃、不加任何强化、也不改爆炸威力），
 * 用 {@link LuckyEvents#findFreeY} 找个塞得下的高度（它 1.7 格高），并设为不被自然消失。
 *
 * <p>在幸运维度里它会照常被"和平共处 AI"处理（不许索敌），所以那儿的苦力怕不会主动贴脸，
 * 主世界则按原版行为追玩家 —— 两边都是各自维度既有的规则，不做特判。
 */
public final class LuckyCreeperEvent {
    private LuckyCreeperEvent() {
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("entity/creeper"), LuckyEventCategory.LUCKY_ENTITY,
            "召唤一只苦力怕",
            LuckyCreeperEvent::spawnCreeper);
        LuckyEvents.register(event);
        return event;
    }

    private static void spawnCreeper(ServerLevel level, BlockPos pos, @Nullable Player player) {
        Creeper creeper = EntityType.CREEPER.create(level);
        if (creeper == null) return;

        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        double y = LuckyEvents.findFreeY(level, creeper, x, pos.getY(), z);

        creeper.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
        creeper.setPersistenceRequired();
        level.addFreshEntity(creeper);
    }
}
