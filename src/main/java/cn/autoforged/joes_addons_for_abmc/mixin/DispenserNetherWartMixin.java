package cn.autoforged.joes_addons_for_abmc.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 发射器发射地狱疣时：若其前方为空气、且空气下方一格是灵魂沙，则改为在灵魂沙上种植地狱疣
 * （就地放置疣方块），而不是把地狱疣作为物品发射出去。
 */
@Mixin(DefaultDispenseItemBehavior.class)
public abstract class DispenserNetherWartMixin {

    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private void jafa_plantNetherWart(BlockSource source, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (!stack.is(Items.NETHER_WART)) return;
        Level level = source.level();
        Direction facing = source.state().getValue(DispenserBlock.FACING);
        BlockPos front = source.pos().relative(facing);
        if (level.getBlockState(front).isAir()
            && level.getBlockState(front.below()).is(Blocks.SOUL_SAND)) {
            level.setBlock(front, Blocks.NETHER_WART.defaultBlockState(), 3);
            level.playSound(null, source.pos(), net.minecraft.sounds.SoundEvents.DISPENSER_DISPENSE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            stack.shrink(1);
            cir.setReturnValue(stack);
        }
    }
}