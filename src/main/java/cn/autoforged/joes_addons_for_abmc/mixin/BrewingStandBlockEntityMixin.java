package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 无限酿造奖励开关：每刻在酿造台 serverTick 开头，根据“附近是否有持策略性击败奖励的玩家”设置
 * 全局无限酿造标志（供 UnlimitedBrewingRecipe 读取）。serverTick 内的 isBrewable / doBrew 都会经由
 * 该配方进行判断，因此在 HEAD 处设置即可；下一台酿造台的 serverTick 又会在 HEAD 处重设该标志，
 * 不会跨方块泄漏。
 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void jafa_setUnlimitedBrewingFlag(Level level, BlockPos pos, BlockState state,
            BrewingStandBlockEntity blockEntity, CallbackInfo ci) {
        boolean active = level instanceof ServerLevel sl && ModMain.isUnlimitedBrewerNear(sl, pos);
        ModMain.setUnlimitedBrewingActive(active);
    }
}