package cn.autoforged.joes_addons_for_abmc.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 开放 {@link LivingEntity#updateWalkAnimation(float)}（原 protected）。
 * 供客户端"渲染替换"代理生物按玩家位移驱动四肢摆动，从而产生走/跑动画。
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessorMixin {

    @Invoker("updateWalkAnimation")
    void jafa_updateWalkAnimation(float speed);
}