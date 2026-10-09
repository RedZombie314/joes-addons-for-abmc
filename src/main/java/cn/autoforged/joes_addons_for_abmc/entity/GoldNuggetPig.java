package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * 幸运子事件（{@code entity/gold_nugget_pig}）里的<b>金粒猪</b>：一只<b>头顶不断喷发金粒</b>的猪。
 *
 * <h3>行为</h3>
 * <ul>
 *   <li>每 {@link #EMIT_INTERVAL_TICKS} 刻（<b>4 刻 = 每秒 5 颗</b>）从<b>头顶正上方</b>喷出一颗
 *       {@code minecraft:gold_nugget}（带一点随机水平散开 + 向上的初速度，所以是"喷泉"式的抛物线，
 *       不是直上直下）；</li>
 *   <li>喷出的金粒<b>不可被捡起</b>（{@code setNeverPickUp()} → 拾取延迟 32767），只是纯装饰；</li>
 *   <li>金粒不会堆到天荒地老：出生时把 {@link ItemEntity#lifespan} 压到
 *       {@link #NUGGET_LIFESPAN_TICKS}（5 秒），到点自己消失 —— 否则这只猪会一直喷、维度里的掉落物只增不减。</li>
 * </ul>
 *
 * <p>本体就是一只普通的原版猪（原版 AI、可繁殖、可骑），区别只在于每刻多喷一次金粒。
 * 渲染直接复用原版 {@code PigRenderer}（见 {@code ClientEvents#registerEntityRenderers}）。
 */
public class GoldNuggetPig extends Pig {

    /**
     * 两次喷发之间隔多少刻。<b>4 刻 = 每秒 5 颗</b>（需求：喷发间隔 4gt）。
     */
    public static final int EMIT_INTERVAL_TICKS = 4;

    /**
     * 喷出的金粒存活多少刻。
     * <p>
     * 刻意比原版的 6000 刻（5 分钟）短得多：这只猪在幸运维度里会被当成普通生物刷出来，
     * 若金粒按原版寿命堆积，一只猪稳态就能留下上千个掉落物实体（每秒 5 个 × 5 分钟），
     * 几十只就是几万实体。压到 5 秒后，单只猪稳态只留约 <b>25 颗</b>（5 颗/秒 × 5 秒）。
     */
    public static final int NUGGET_LIFESPAN_TICKS = 100;

    /** 一次喷几粒。 */
    private static final int NUGGETS_PER_BURST = 1;

    /** 下一次喷发还有多少刻。 */
    private int emitCooldown = EMIT_INTERVAL_TICKS;

    public GoldNuggetPig(EntityType<? extends GoldNuggetPig> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        if (--this.emitCooldown > 0) {
            return;
        }
        this.emitCooldown = EMIT_INTERVAL_TICKS;
        this.emitNuggets();
    }

    /** 从头顶喷出金粒（掉落物实体，不可捡起）。 */
    private void emitNuggets() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        double x = this.getX();
        double y = this.getY() + (double) this.getBbHeight() + 0.15D;
        double z = this.getZ();
        for (int i = 0; i < NUGGETS_PER_BURST; i++) {
            ItemEntity nugget = new ItemEntity(level, x, y, z, new ItemStack(Items.GOLD_NUGGET));
            nugget.setDeltaMovement(
                (level.random.nextDouble() - 0.5D) * 0.24D,
                0.32D + level.random.nextDouble() * 0.12D,
                (level.random.nextDouble() - 0.5D) * 0.24D);
            // 需求：喷出来的金粒不可被捡起。setNeverPickUp = 拾取延迟 32767，玩家碰上去也捡不走，
            // 顺带让它不与旁边的金粒合并（isMergable 要求 pickupDelay != 32767）。
            nugget.setNeverPickUp();
            nugget.lifespan = NUGGET_LIFESPAN_TICKS;
            level.addFreshEntity(nugget);
        }
        // 喷口的一点火花，让"在喷"这件事看得见（金粒本身很小）
        level.sendParticles(ParticleTypes.CRIT, x, y, z, 3, 0.15D, 0.1D, 0.15D, 0.02D);
    }
}
