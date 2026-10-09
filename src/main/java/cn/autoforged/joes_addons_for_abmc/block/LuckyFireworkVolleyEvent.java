package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.entity.LuckyFireworkRocket;
import cn.autoforged.joes_addons_for_abmc.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 幸运实体子事件：<b>3~4 个向天空中飞的烟花火箭</b>（需求 7）。
 *
 * <p>每颗火箭：
 * <ul>
 *   <li><b>水平方向随机</b>（yaw 随机）；</li>
 *   <li><b>仰角随机</b>落在 {@link LuckyFireworkRocket#MIN_PITCH_DEGREES}~{@link LuckyFireworkRocket#MAX_PITCH_DEGREES}
 *       （60~80 度），并且飞行过程中<b>每刻都在这个区间里随机波动</b>（由 {@link LuckyFireworkRocket} 自己实现）；</li>
 *   <li>炸开的颜色/形状随机，飞到约 20 格高时按原版烟花逻辑炸开。</li>
 * </ul>
 *
 * <p>数量取 3~4（含两端）。
 */
public final class LuckyFireworkVolleyEvent {

    /** 数量下限（含）。 */
    public static final int MIN_COUNT = 3;

    /** 数量上限（含）。 */
    public static final int MAX_COUNT = 4;

    private LuckyFireworkVolleyEvent() {
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("entity/firework_volley"), LuckyEventCategory.LUCKY_ENTITY,
            "发射 " + MIN_COUNT + "~" + MAX_COUNT + " 个冲天烟花火箭（水平方向随机，仰角 60~80 度之间随机波动）",
            LuckyFireworkVolleyEvent::launchVolley);
        LuckyEvents.register(event);
        return event;
    }

    private static void launchVolley(ServerLevel level, BlockPos pos, @Nullable Player player) {
        RandomSource random = level.getRandom();
        int count = MIN_COUNT + random.nextInt(MAX_COUNT - MIN_COUNT + 1);
        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        double y = pos.getY() + 1.0D;

        for (int i = 0; i < count; i++) {
            LuckyFireworkRocket rocket = ModEntities.LUCKY_FIREWORK_ROCKET.get().create(level);
            if (rocket == null) return;
            float yaw = random.nextFloat() * 360.0F;
            double pitch = LuckyFireworkRocket.MIN_PITCH_DEGREES
                + random.nextDouble() * (LuckyFireworkRocket.MAX_PITCH_DEGREES - LuckyFireworkRocket.MIN_PITCH_DEGREES);
            // 先 configure 再 moveTo：configure 内部走的是 Entity#load，而 load 在没有 Pos/Motion 时
            // 会把坐标和速度清成 (0,0,0)（Entity.java:1829-1845 那几行是 getList 取空表再 getDouble(0)），
            // 所以摆位置的动作必须放在它后面，否则火箭全从世界原点飞出去。
            rocket.configure(randomFirework(random), yaw, pitch);
            rocket.moveTo(x, y, z, yaw, 0.0F);
            level.addFreshEntity(rocket);
        }
        level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 1.0F, 1.0F);
    }

    /** 随机颜色 + 随机形状的烟花火箭物品（决定炸开的样子）。 */
    private static ItemStack randomFirework(RandomSource random) {
        ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
        // 爆点构造与"烟花猪"的消失散射共用（见 LuckyFireworkRocket#randomExplosion）
        FireworkExplosion explosion = LuckyFireworkRocket.randomExplosion(random);
        // flightDuration 1：原版默认的"小烟花"，够用（真正飞多久由实体的 LifeTime 决定）
        stack.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(explosion)));
        return stack;
    }
}
