package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;

/**
 * 幸运物品子事件：<b>64~128 个金锭，向四周喷出</b>（需求 6）。
 *
 * <p>和"宝石抛洒"（{@link LuckyScatterEvent}）同一套抛洒写法：每个金锭的水平朝向随机、仰角在
 * {@link #MIN_PITCH_DEGREES}~{@link #MAX_PITCH_DEGREES} 之间随机、速度也随机，所以是一朵"炸开"的金锭花；
 * 数量走 {@link #MIN_COUNT}~{@link #MAX_COUNT} 的随机（需求区间含两端）。
 *
 * <p>每个金锭都是<b>单独一个掉落物实体</b>（不合成大堆）——需求要的是"喷出"的观感，
 * 合成 64 个一摞就只剩两个实体在天上飞了。拾取延迟用原版默认（0.5 秒），免得刚喷出来就被同刻吸走。
 */
public final class LuckyGoldIngotBurstEvent {

    /** 数量下限（含）。 */
    public static final int MIN_COUNT = 64;

    /** 数量上限（含）。 */
    public static final int MAX_COUNT = 128;

    /** 抛洒仰角区间（度）。 */
    private static final double MIN_PITCH_DEGREES = 20.0D;
    private static final double MAX_PITCH_DEGREES = 60.0D;

    /** 速度区间（格/刻）。 */
    private static final double MIN_SPEED = 0.28D;
    private static final double SPEED_RANGE = 0.22D;

    private LuckyGoldIngotBurstEvent() {
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("item/gold_ingot_burst"), LuckyEventCategory.LUCKY_ITEM,
            "向四周喷出 " + MIN_COUNT + "~" + MAX_COUNT + " 个金锭",
            LuckyGoldIngotBurstEvent::burst);
        LuckyEvents.register(event);
        return event;
    }

    private static void burst(ServerLevel level, BlockPos pos, @Nullable Player player) {
        RandomSource random = level.getRandom();
        int count = MIN_COUNT + random.nextInt(MAX_COUNT - MIN_COUNT + 1);
        for (int i = 0; i < count; i++) {
            double yaw = random.nextDouble() * Math.PI * 2.0D;
            double pitch = Math.toRadians(
                MIN_PITCH_DEGREES + random.nextDouble() * (MAX_PITCH_DEGREES - MIN_PITCH_DEGREES));
            double speed = MIN_SPEED + random.nextDouble() * SPEED_RANGE;

            double vx = Math.cos(pitch) * Math.cos(yaw) * speed;
            double vz = Math.cos(pitch) * Math.sin(yaw) * speed;
            double vy = Math.sin(pitch) * speed;

            ItemEntity drop = new ItemEntity(level,
                pos.getX() + 0.5D, pos.getY() + 0.6D, pos.getZ() + 0.5D,
                new ItemStack(Items.GOLD_INGOT), vx, vy, vz);
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
    }
}
