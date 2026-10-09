package cn.autoforged.joes_addons_for_abmc.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>让"地狱疣钓竿"种下的地狱疣不再被方块更新弹掉</b>。
 *
 * <h3>问题</h3>
 * 原版地狱疣是 {@link BushBlock}，只能长在灵魂沙上（{@code NetherWartBlock#mayPlaceOn}）；
 * {@code BushBlock#updateShape} 的内容是
 * {@code !state.canSurvive(level, currentPos) ? AIR : super.updateShape(...)} ——
 * <b>它根本不看传入的 facing 是哪个方向</b>，只要发生一次形状更新就检查一次存活，
 * 于是种在普通方块上的疣在下面两种情况下都会立刻消失：
 * <ul>
 *   <li>底座变化（facing = DOWN）；</li>
 *   <li><b>旁边放/挖一个方块</b>（facing 是水平方向）—— 表现为"在已有疣旁边再种一个，两个疣一起被摧毁"，
 *       甚至形成连锁：疣变空气又去更新它的邻居。</li>
 * </ul>
 *
 * <h3>做法</h3>
 * 在 {@code BushBlock#updateShape} 的 HEAD 拦截 <b>地狱疣</b>（任意 facing）：
 * <ul>
 *   <li>底座（下方那一格）<b>仍然有完整的上表面</b> → 直接返回原状态，这次更新完全不影响疣；</li>
 *   <li>底座被破坏（变成空气、台阶之类上表面不完整的东西）→ 放行给原版逻辑，
 *       于是疣照常被弹掉 —— 也就是需求里的"底座被破坏时才会被破坏"。</li>
 * </ul>
 *
 * <p>直接返回原状态是安全的：{@code BushBlock} 在能存活时调用的 {@code super.updateShape(...)}
 * 就是 {@code BlockBehaviour#updateShape} 的默认实现，内容只有 {@code return state;}
 * （地狱疣没有任何形状联动逻辑）。
 *
 * <p>范围收得很窄，所以对原版行为的影响只有一条：<b>地狱疣长在"非灵魂沙但上表面完整"的底座上时不会掉</b>。
 * 灵魂沙底座本来就满足 {@code canSurvive}，走哪条分支结果都一样，所以灵魂沙疣田完全不受影响；
 * 玩家用地狱疣物品去"放置方块"的合法性判定走的是 {@code canSurvive}（{@code BlockItem#canPlace}），
 * 本 mixin 没有改它，所以仍然只能放在灵魂沙上。
 */
@Mixin(BushBlock.class)
public abstract class NetherWartPersistenceMixin {

    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void jafa_keepPlantedNetherWart(BlockState state, Direction facing, BlockState facingState,
                                            LevelAccessor level, BlockPos currentPos, BlockPos facingPos,
                                            CallbackInfoReturnable<BlockState> cir) {
        // 只保护地狱疣；其它 BushBlock（树苗/花/作物）行为完全不变
        if (!state.is(Blocks.NETHER_WART)) return;

        BlockPos below = currentPos.below();
        BlockState belowState = level.getBlockState(below);
        if (Block.isFaceFull(belowState.getCollisionShape(level, below), Direction.UP)) {
            cir.setReturnValue(state); // 底座还在 → 不管这次是哪个方向的更新，疣都不该消失
        }
        // 底座没了/上表面不完整 → 不取消，交给原版弹掉
    }
}
