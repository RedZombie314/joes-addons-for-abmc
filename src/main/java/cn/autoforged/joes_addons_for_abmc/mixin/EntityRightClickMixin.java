package cn.autoforged.joes_addons_for_abmc.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让“玩家变形后的跟随壳”（被打上 {@code jafa_transmutation_follow} 标记的生物壳）
 * 以及“变形中的玩家本体”右键点击可穿透，不再因碰撞箱挡住玩家与其身后的方块/实体互动
 * （否则玩家变形成生物壳后会被困在壳体内，右键命中自己本体而被吞掉，导致丢药水等右键操作失败）。
 * 普通实体不受影响。
 */
@Mixin(Entity.class)
public abstract class EntityRightClickMixin {

    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void jafa_transmutationNoPick(CallbackInfoReturnable<Boolean> cir) {
        // 1) 跟随壳（生物壳/玩家壳）：服务端按持久化标记穿透（该标记不同步到客户端）
        if (this.followMarked()) {
            cir.setReturnValue(false);
            return;
        }
        net.minecraft.world.entity.Entity self = (net.minecraft.world.entity.Entity) ((Object) this);
        // 2) 变形中的玩家本体（服务端）：避免右键命中自己吞掉操作
        if (self instanceof net.minecraft.server.level.ServerPlayer sp) {
            if (cn.autoforged.joes_addons_for_abmc.ModMain.isPlayerTransmuting(sp.getUUID())) {
                cir.setReturnValue(false);
            }
            return;
        }
        // 3) 客户端：命中的是"本地玩家自己的跟随壳"（followEntityId）或"变形中的本体"时穿透
        if (self.level() != null && self.level().isClientSide()) {
            boolean isOwnShell = cn.autoforged.joes_addons_for_abmc.client.TransmutationCameraClient.getFollowEntityId()
                == self.getId();
            boolean isOwnBody = self instanceof net.minecraft.client.player.LocalPlayer
                && cn.autoforged.joes_addons_for_abmc.client.TransmutationCameraClient.isTransmuted();
            if (isOwnBody || isOwnShell) {
                cir.setReturnValue(false);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean followMarked() {
        net.minecraft.world.entity.Entity self = (net.minecraft.world.entity.Entity) ((Object) this);
        return self.getPersistentData().getBoolean("jafa_transmutation_follow");
    }
}