package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.block.NotePortalShape;
import cn.autoforged.joes_addons_for_abmc.worldgen.ModDimensions;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让所有能产生火的途径（打火石、发射器、火焰弹、岩浆、火蔓延等）都能激活音符方块传送门。
 *
 * 逻辑与原版 {@link BaseFireBlock#onPlace} 检测黑曜石框架完全一致，只是框架换成音符盒：
 * 在方法开头检测火是否位于一个合法的音符盒框架内部，若是则生成音符传送门并取消原版逻辑
 * （避免火被当作普通方块处理，也避免在原版维度误触下界传送门检测）。
 */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin {

    @Inject(method = "onPlace", at = @At("HEAD"), cancellable = true)
    private void jafm_trySpawnNotePortal(BlockState state, Level level, BlockPos pos,
                                         BlockState oldState, boolean isMoving, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        // 音符传送门只在主世界与音符方块宇宙之间往返，故只在这两个维度检测
        if (level.dimension() != Level.OVERWORLD && level.dimension() != ModDimensions.NOTE_DIM_LEVEL) {
            return;
        }
        if (oldState.is(state.getBlock())) {
            return;
        }
        Optional<NotePortalShape> shape = NotePortalShape.findEmptyPortalShape(level, pos, Direction.Axis.X);
        if (shape.isPresent()) {
            shape.get().createPortalBlocks();
            ci.cancel();
        }
    }
}
