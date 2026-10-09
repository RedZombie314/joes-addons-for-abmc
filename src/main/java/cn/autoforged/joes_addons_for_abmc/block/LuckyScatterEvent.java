package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;

/**
 * 幸运物品子事件：<b>向四周抛洒钻石 / 金锭 / 绿宝石，并播放玩家升级音效</b>。
 *
 * <p>三种物品<b>各</b>散落 3~5 个：每个掉落物的水平朝向随机、仰角也在 20°~60° 之间随机
 * （所以是"炸开"式的抛洒，而不是堆在脚下）。掉落物用 {@link ItemEntity} 直接给初速度，
 * 并设置默认拾取延迟（避免刚炸出来就被同刻吸走）。
 */
public final class LuckyScatterEvent {
    /** 每种物品的散落数量区间（含端点）。 */
    private static final int MIN_PER_TYPE = 3;
    private static final int MAX_PER_TYPE = 5;

    /** 抛洒仰角区间（度）。 */
    private static final double MIN_PITCH_DEGREES = 20.0D;
    private static final double MAX_PITCH_DEGREES = 60.0D;

    private LuckyScatterEvent() {
    }

    /**
     * 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象 ——
     * 调用方据此决定要不要把它登记进 debug 池（见 {@link LuckyEvents#markDebug}）。
     */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("item/gem_scatter"), LuckyEventCategory.LUCKY_ITEM,
            "向四周抛洒 3~5 个钻石、金锭、绿宝石，并播放玩家升级音效",
            LuckyScatterEvent::scatterGems);
        LuckyEvents.register(event);
        return event;
    }

    private static void scatterGems(ServerLevel level, BlockPos pos, @Nullable Player player) {
        RandomSource random = level.getRandom();
        scatter(level, pos, Items.DIAMOND, random);
        scatter(level, pos, Items.GOLD_INGOT, random);
        scatter(level, pos, Items.EMERALD, random);
        // 玩家升级音效
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** 把某一种物品按随机朝向 + 随机仰角抛洒 {@value #MIN_PER_TYPE}~{@value #MAX_PER_TYPE} 个。 */
    private static void scatter(ServerLevel level, BlockPos pos, Item item, RandomSource random) {
        int count = MIN_PER_TYPE + random.nextInt(MAX_PER_TYPE - MIN_PER_TYPE + 1);
        for (int i = 0; i < count; i++) {
            double yaw = random.nextDouble() * Math.PI * 2.0D; // 水平朝向随机
            double pitch = Math.toRadians(
                MIN_PITCH_DEGREES + random.nextDouble() * (MAX_PITCH_DEGREES - MIN_PITCH_DEGREES)); // 仰角随机
            double speed = 0.28D + random.nextDouble() * 0.22D;

            double vx = Math.cos(pitch) * Math.cos(yaw) * speed;
            double vz = Math.cos(pitch) * Math.sin(yaw) * speed;
            double vy = Math.sin(pitch) * speed;

            ItemEntity drop = new ItemEntity(level,
                pos.getX() + 0.5D, pos.getY() + 0.6D, pos.getZ() + 0.5D,
                new ItemStack(item), vx, vy, vz);
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
    }
}
