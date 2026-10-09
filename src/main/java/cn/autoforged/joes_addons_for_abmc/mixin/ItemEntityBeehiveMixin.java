package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.BeehiveThrownHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

/**
 * 追踪被投掷的蜂巢掉落物：落地（onGround / 撞墙）或击中实体时，该蜂巢被摧毁并释放内部蜜蜂
 * （击中实体则蜜蜂仇视该实体）。仅处理带 {@link BeehiveThrownHelper#MARKER} 标记的蜂巢，
 * 普通掉落物不受影响。
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityBeehiveMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void jafa_thrownBeehiveTick(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        var tag = self.getPersistentData();
        if (!tag.getBoolean(BeehiveThrownHelper.MARKER)) {
            return;
        }

        // 命中实体检测（跳过投掷者自身）
        LivingEntity target = null;
        List<Entity> entities = self.level().getEntities(self, self.getBoundingBox().inflate(0.4));
        UUID owner = tag.hasUUID(BeehiveThrownHelper.OWNER_KEY)
            ? tag.getUUID(BeehiveThrownHelper.OWNER_KEY) : null;
        for (Entity e : entities) {
            if (!(e instanceof LivingEntity le)) {
                continue;
            }
            if (owner != null && le.getUUID().equals(owner)) {
                continue;
            }
            target = le;
            break;
        }

        // 落地 / 撞墙判定
        boolean hitBlock = self.onGround() || self.horizontalCollision || self.verticalCollision;
        // 安全兜底：长时间飞行未落地则强制打破
        boolean timeout = self.tickCount > 1200;

        if (target != null || hitBlock || timeout) {
            BeehiveThrownHelper.breakAndReleaseBees(self.level(), self, target);
        }
    }
}