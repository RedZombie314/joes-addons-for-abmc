package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.block.CreeperPortalShape;
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
 * 让所有能产生火的途径（打火石、发射器、火焰弹、岩浆、火蔓延等）都能激活
 * 音符方块传送门与 Creeper Clan 传送门。
 *
 * 逻辑与原版 {@link BaseFireBlock#onPlace} 检测黑曜石框架完全一致，只是框架换成各传送门材质：
 * 在方法开头检测火是否位于一个合法的框架内部，若是则生成对应传送门并取消原版逻辑
 * （避免火被当作普通方块处理，也避免在原版维度误触下界传送门检测）。
 */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin {

    @Inject(method = "onPlace", at = @At("HEAD"), cancellable = true)
    private void jafa_trySpawnPortals(BlockState state, Level level, BlockPos pos,
                                      BlockState oldState, boolean isMoving, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        if (oldState.is(state.getBlock())) {
            return;
        }
        // 音符传送门：主世界 <-> 音符方块宇宙
        if (level.dimension() == Level.OVERWORLD || level.dimension() == ModDimensions.NOTE_DIM_LEVEL) {
            Optional<NotePortalShape> noteShape = NotePortalShape.findEmptyPortalShape(level, pos, Direction.Axis.X);
            if (noteShape.isPresent()) {
                noteShape.get().createPortalBlocks();
                ci.cancel();
                return;
            }
        }
        // Creeper Clan 传送门：主世界 <-> Creeper Clan（TNT+黑曜石框架）。
        // 点火后不直接生成传送门，而是点燃框架内所有 TNT，120 刻后爆炸（无破坏/无伤害/仅冲击波）、
        // 复原 TNT 并开启传送门。
        if (level.dimension() == Level.OVERWORLD || level.dimension() == ModDimensions.CREEPER_CLAN_DIM_LEVEL) {
            Optional<CreeperPortalShape> creeperShape = CreeperPortalShape.findEmptyPortalShape(level, pos, Direction.Axis.X);
            if (creeperShape.isPresent()) {
                ci.cancel();
                cn.autoforged.joes_addons_for_abmc.ModMain.activateCreeperPortal(level, creeperShape.get());
            }
        }
    }
}
