package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.entity.PlayerMorphSync;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为玩家实体的同步数据注册一个用于“渲染替换”变形目标的字符串字段，
 * 使变形目标成为玩家元数据的一部分，从而能被 Replay Mod 等录制并回放。
 */
@Mixin(Player.class)
public abstract class PlayerMorphDataMixin {

    @Inject(method = "defineSynchedData", at = @At("RETURN"))
    private void jafa_defineMorphData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(PlayerMorphSync.MORPH_TYPE, "");
    }
}