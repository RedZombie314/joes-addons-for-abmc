package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.entity.LuckySelectorEntity;
import cn.autoforged.joes_addons_for_abmc.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 幸运方块选择器刷怪蛋：右键在玩家视线前方召唤一个「选择框」。
 * <p>
 * 外观暂用<b>白色混凝土</b>贴图（见 {@code models/item/lucky_selector_spawn_egg.json}），后续可换。
 * <p>
 * 与苦力蜂蛋不同的是：选择器是<b>飞行</b>实体，所以生成点取视线前方的空中，而不是找地面。
 */
public class LuckySelectorSpawnEggItem extends Item {

    public LuckySelectorSpawnEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            // 客户端：视作成功以保持握持动画，实际生成在服务端执行
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.fail(stack);
        }

        Vec3 spawnPos = findSpawnPos(serverLevel, player);
        LuckySelectorEntity selector = ModEntities.LUCKY_SELECTOR.get().create(serverLevel);
        if (selector == null) return InteractionResultHolder.fail(stack);
        selector.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, player.getYRot(), 0.0F);
        serverLevel.addFreshEntity(selector);

        serverLevel.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.7F, 1.4F);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /**
     * 找一个不在方块里的空中生成点：沿视线从 3 格往回退到 1 格，取第一处「本身与上方都是空气」的位置
     * （1 格碰撞箱，所以要留出上下两格空间）；都不行就退到玩家眼睛所在处。
     */
    private Vec3 findSpawnPos(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        for (double dist = 3.0D; dist >= 1.0D; dist -= 0.5D) {
            Vec3 candidate = eye.add(look.scale(dist));
            BlockPos pos = BlockPos.containing(candidate);
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
                return candidate;
            }
        }
        return new Vec3(player.getX(), player.getEyeY(), player.getZ());
    }
}
