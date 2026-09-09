package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.ModMain;
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
 * 女巫Boss 刷怪蛋：在创造模式物品栏中提供，右键直接召唤一只女巫Boss。
 * 右键空气/方块时，在玩家朝向的视线落点（或脚前）生成并初始化女巫Boss，消耗 1 个物品。
 */
public class WitchBossSpawnEggItem extends Item {

    public WitchBossSpawnEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            // 客户端：视作成功以保持握持动画，实际生成在服务端执行
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.fail(stack);
        }
        Vec3 spawnPos = findSpawnPos(serverLevel, player);
        if (spawnPos == null) return InteractionResultHolder.fail(stack);

        // 召唤女巫Boss（含全部初始化：持久化/血条/基地/弹药）
        boolean ok = ModMain.spawnStandaloneWitchBoss(serverLevel, spawnPos) != null;
        if (!ok) return InteractionResultHolder.fail(stack);

        // 音效与消耗
        serverLevel.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
            SoundEvents.WITCH_DRINK, SoundSource.HOSTILE, 1.0F, 0.8F);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** 找一个足够宽敞、可立足的生成点：优先取玩家朝向视线与前方的方块表面，
     *  否则取玩家前方 2 格脚下。 */
    private Vec3 findSpawnPos(ServerLevel level, Player player) {
        // 尝试取玩家视线命中的方块表面交点，作为生成地面
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        double maxDist = 8.0;
        Vec3 end = eye.add(look.scale(maxDist));
        net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(
            eye, end,
            net.minecraft.world.level.ClipContext.Block.OUTLINE,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            player);
        net.minecraft.world.phys.BlockHitResult hit =
            level.clip(ctx);
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            BlockPos blockPos = hit.getBlockPos();
            // 在击中面的一侧放置（若击中的是顶面，则放在其上方）
            net.minecraft.core.Direction side = hit.getDirection();
            BlockPos spawnBlock = blockPos.relative(side);
            if (level.getBlockState(spawnBlock).isAir()
                    && level.getBlockState(spawnBlock.above()).isAir()) {
                return new Vec3(spawnBlock.getX() + 0.5, spawnBlock.getY(), spawnBlock.getZ() + 0.5);
            }
        }
        // 兜底：玩家前方 3 格、脚下方块上方生成
        Vec3 front = player.position().add(player.getLookAngle().scale(3.0D));
        BlockPos frontBlock = BlockPos.containing(front);
        BlockPos ground = frontBlock.below();
        if (level.getBlockState(ground).isSolid()) {
            return new Vec3(frontBlock.getX() + 0.5, frontBlock.getY(), frontBlock.getZ() + 0.5);
        }
        // 最后兜底：玩家自己的方块位置
        BlockPos ppos = player.blockPosition();
        return new Vec3(ppos.getX() + 0.5, ppos.getY() + 0.5, ppos.getZ() + 0.5);
    }
}