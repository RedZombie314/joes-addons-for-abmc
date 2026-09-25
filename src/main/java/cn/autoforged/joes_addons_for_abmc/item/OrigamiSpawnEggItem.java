package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.entity.EnchantedOrigamiEntity;
import cn.autoforged.joes_addons_for_abmc.entity.ModEntities;
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
 * 附魔千纸鹤 刷怪蛋：右键直接召唤一只附魔千纸鹤。
 * 生成在玩家面前（视线方向约 3 格处），创建模式不消耗，生存模式消耗 1 个。
 */
public class OrigamiSpawnEggItem extends Item {

    public OrigamiSpawnEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.fail(stack);
        }

        Vec3 front = player.position().add(player.getLookAngle().scale(3.0D));
        Vec3 spawnPos = new Vec3(front.x, player.getY() + 0.3, front.z);

        EnchantedOrigamiEntity origami =
            ModEntities.ENCHANTED_ORIGAMI.get().create(serverLevel);
        if (origami == null) return InteractionResultHolder.fail(stack);
        origami.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        serverLevel.addFreshEntity(origami);

        serverLevel.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
            SoundEvents.BAT_TAKEOFF, SoundSource.NEUTRAL, 0.5F, 1.0F);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}