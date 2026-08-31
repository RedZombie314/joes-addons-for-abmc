package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玩家变形成生物时，把其碰撞箱尺寸真正设为对应生物的尺寸。
 * 原版 {@link Entity#getDimensions(Pose)} 按姿势返回尺寸；EntityEvent.Size 改的是缓存字段，
 * 对该方法不生效，导致服务端权威碰撞 / 客户端本地预测仍用默认大碰撞箱（穿细缝被拖慢）。
 * 这里在 getDimensions 处直接返回变形用尺寸（服务端从 ModMain 地图取，客户端用渲染生物类型计算）。
 * 注意：getDimensions 定义在基类 {@link Entity}（Player 不重写），因此 Mixin 目标须是 {@code Entity}。
 */
@Mixin(Entity.class)
public abstract class PlayerDimensionsMixin {

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void jafa_morphDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player)) {
            return;
        }
        try {
            // 服务端（权威）：直接取 ModMain 里登记的变形尺寸
            EntityDimensions d = ModMain.getMorphDimensions(self.getUUID());
            if (d == null && self.level() != null && self.level().isClientSide()) {
                // 客户端本地玩家：用渲染目标生物类型的默认尺寸
                d = cn.autoforged.joes_addons_for_abmc.client.TransmutationCameraClient.morphDimensionsLocal();
            }
            if (d != null) {
                cir.setReturnValue(d);
            }
        } catch (Throwable ignored) {
        }
    }
}