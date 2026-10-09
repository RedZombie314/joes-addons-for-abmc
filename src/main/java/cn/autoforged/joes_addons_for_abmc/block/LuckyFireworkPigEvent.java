package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.entity.FireworkPig;
import cn.autoforged.joes_addons_for_abmc.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * 幸运实体子事件：<b>烟花猪</b>（需求 8）。
 *
 * <p>本体是自定实体 {@link FireworkPig}（原版猪的派生类）：随机水平朝向，尾部每刻向后喷烟花粒子，
 * <b>自己以 20 格/秒（1 格/刻）笔直往前飞</b>，<b>撞到方块</b>或<b>飞满 5 秒</b>就在原地散射大量烟花粒子消失。
 *
 * <p>出生高度从"幸运方块上一格"开始用 {@link LuckyEvents#findFreeY} 往上找第一个塞得下的位置：
 * 正常情况就是方块上方那一格（不贴地，免得刚起飞就被判定成撞方块）；若上方被封死则抬到最近的空间，
 * 抬不出去也没关系 —— 它下一帧就会"撞到方块"然后按需求在消失点炸开一片烟花。
 */
public final class LuckyFireworkPigEvent {
    private LuckyFireworkPigEvent() {
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("entity/firework_pig"), LuckyEventCategory.LUCKY_ENTITY,
            "生成一只烟花猪：尾部喷烟花、以 20 格/秒往前飞，撞到方块或飞满 5 秒后在消失点散射大量烟花粒子",
            LuckyFireworkPigEvent::spawnFireworkPig);
        LuckyEvents.register(event);
        return event;
    }

    private static void spawnFireworkPig(ServerLevel level, BlockPos pos, @Nullable Player player) {
        FireworkPig pig = ModEntities.FIREWORK_PIG.get().create(level);
        if (pig == null) return;

        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        double y = LuckyEvents.findFreeY(level, pig, x, pos.getY() + 1.0D, z);
        float yaw = level.random.nextFloat() * 360.0F;

        pig.moveTo(x, y, z, yaw, 0.0F);
        pig.launch(yaw);
        pig.setPersistenceRequired();
        level.addFreshEntity(pig);
    }
}
