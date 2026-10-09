package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import javax.annotation.Nullable;

/**
 * 幸运事件"落石 + TNT 连发"专用的 TNT 方块。
 *
 * <p>外观与行为全部继承原版 TNT（模型直接以 {@code minecraft:block/tnt} 为父，
 * 点燃/红石/爆炸/连锁逻辑都与原版一致），只在<b>引信长度</b>上做两处定制：
 * <ol>
 *   <li><b>手动点燃</b>（打火石 → 火焰，或红石信号）→ 引信 <b>{@value #IGNITED_FUSE_TICKS} 刻</b>
 *       （原版默认 80 刻）；</li>
 *   <li><b>被爆炸波及</b> → 引信 <b>0</b>：下一刻立刻爆炸，无视自身引信，因此彼此会连锁引爆。</li>
 * </ol>
 *
 * <p>把这些行为做进方块本体（而不是靠外部位置追踪），无论是玩家点燃、红石触发还是别的爆炸波及，
 * 都必然生效 —— 与谁点的、点在哪无关。
 */
public class LuckyTntBlock extends TntBlock {
    /** 手动点燃后的引信长度（游戏刻）。 */
    public static final int IGNITED_FUSE_TICKS = 60;

    public LuckyTntBlock(Properties properties) {
        super(properties);
    }

    /** 火焰/打火石点燃。 */
    @Override
    public void onCaughtFire(BlockState state, Level level, BlockPos pos, @Nullable Direction direction,
                            @Nullable LivingEntity igniter) {
        prime(level, pos, igniter, IGNITED_FUSE_TICKS);
    }

    /**
     * <b>固化即点燃</b>：下落 TNT 落地（方块被放回世界）的瞬间立即点燃 —— 于是它马上变成没有碰撞箱的
     * 已点燃 TNT，下一个下落的 TNT 会从它原来的位置穿过去继续落到红石块上被点燃（连锁、不会堆积）。
     *
     * <p>这里不再依赖"邻居是红石块"这一条件：红石块只是设计上的点火来源（会被第一次爆炸炸掉），
     * 一旦它没了，后面的 TNT 就再也点不着、会堆在地上。所以对本方块而言，落地就是点燃。
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            prime(level, pos, null, IGNITED_FUSE_TICKS);
        }
    }

    /** 被红石信号激活（邻居变化）时同样点燃。 */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (level.hasNeighborSignal(pos)) {
            prime(level, pos, null, IGNITED_FUSE_TICKS);
        }
    }

    /** 被爆炸波及：引信归零，下一刻立即爆炸（原版是随机缩短到 1/8~3/8）。 */
    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        prime(level, pos, explosion.getIndirectSourceEntity(), 0);
    }

    /** 点燃：与 {@code TntBlock.prime} 相同的行为，只是引信由我们指定。 */
    private static void prime(Level level, BlockPos pos, @Nullable LivingEntity igniter, int fuse) {
        if (level.isClientSide) return;
        // ★ 关键：点燃后必须把这格方块移除（原版也是这么做的）。
        //   不移除的话方块还在，下一个下落的 TNT 就会落在它上面 —— 表现就是"TNT 叠起来"，
        //   而且它也不会像已点燃 TNT 那样"失去碰撞箱"。
        level.removeBlock(pos, false);
        PrimedTnt primed = new PrimedTnt(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, igniter);
        primed.setFuse(fuse);
        level.addFreshEntity(primed);
        level.playSound(null, primed.getX(), primed.getY(), primed.getZ(),
            SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(igniter, GameEvent.PRIME_FUSE, pos);
    }
}
