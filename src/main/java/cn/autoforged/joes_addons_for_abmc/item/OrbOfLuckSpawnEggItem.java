package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.entity.ModEntities;
import cn.autoforged.joes_addons_for_abmc.entity.OrbOfLuckEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 幸运核心（Orb of Luck）刷怪蛋：右键在视线落点的方块上召唤一个核心，非创造模式消耗 1 个。
 * <p>
 * 外观<b>暂时</b>借用原版「光源方块」的满亮贴图（{@code minecraft:item/light_15}，
 * 见 {@code models/item/orb_of_luck_spawn_egg.json}），后续可换成专用贴图。
 * <p>
 * 生成点取「方块顶面」（{@code y = 该方块的 y}）：核心的碰撞箱是底面对齐实体坐标的，
 * 所以这样放下它就正好坐在方块上，而不是半个身子埋进地里。
 */
public class OrbOfLuckSpawnEggItem extends Item {

    public OrbOfLuckSpawnEggItem(Properties properties) {
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
        OrbOfLuckEntity orb = ModEntities.ORB_OF_LUCK.get().create(serverLevel);
        if (orb == null) {
            return InteractionResultHolder.fail(stack);
        }
        orb.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        serverLevel.addFreshEntity(orb);

        serverLevel.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.7F, 1.4F);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /**
     * 找生成点：优先取玩家视线命中的方块、在命中面那一格的地面高度（命中顶面时就是该方块上方那格）；
     * 命中面不合适时退回「玩家前方 3 格脚下」，再不行就用玩家自己所在的方块。
     * <p>
     * 核心高 0.8 格，所以只要求它占的那一格是空气。
     */
    private Vec3 findSpawnPos(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(8.0D));
        BlockHitResult hit = level.clip(new ClipContext(
            eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos spawnBlock = hit.getBlockPos().relative(hit.getDirection());
            if (level.getBlockState(spawnBlock).isAir()) {
                return new Vec3(spawnBlock.getX() + 0.5D, spawnBlock.getY(), spawnBlock.getZ() + 0.5D);
            }
        }
        // 兜底一：玩家前方 3 格、脚下方块之上
        Vec3 front = player.position().add(look.scale(3.0D));
        BlockPos frontBlock = BlockPos.containing(front);
        if (level.getBlockState(frontBlock.below()).isSolid()) {
            return new Vec3(frontBlock.getX() + 0.5D, frontBlock.getY(), frontBlock.getZ() + 0.5D);
        }
        // 兜底二：玩家自己所在的方块
        BlockPos ppos = player.blockPosition();
        return new Vec3(ppos.getX() + 0.5D, ppos.getY(), ppos.getZ() + 0.5D);
    }
}
