package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.BeeBossData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * BeeBoss 刷怪蛋：召唤一只 BeeBoss。
 * <p>
 * <b>注意 BeeBoss 不是独立实体类型</b>，而是打了 {@link BeeBossData#BOSS_TAG} 标记的蜜蜂
 * （原版蜜蜂与苦力蜂都算，见 {@code BeeBossData} 与 {@code BeeBossMixin}）。
 * 所以这里的生成流程与 {@code /jafa beeboss} 命令<b>完全一致</b>：
 * 先 {@code setBeeBoss}，再 {@code applyBeeBossDefaults(freshSpawn = true)}
 * ——第二个参数传 true 表示「新建」，会补满生命值；读档路径不补，这个区别见该方法的注释。
 * <p>
 * 外观套用原版<b>蜜蜂刷怪蛋</b>：模型用 {@code minecraft:item/spawn_egg} + {@code spawn_egg_overlay} 两层，
 * 颜色在 {@code ClientEvents#onRegisterItemColors} 里从 {@code Items.BEE_SPAWN_EGG} 直接复制。
 */
public class BeeBossSpawnEggItem extends Item {

    public BeeBossSpawnEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.fail(stack);
        }

        Bee bee = EntityType.BEE.create(serverLevel);
        if (bee == null) return InteractionResultHolder.fail(stack);
        // 与 /jafa beeboss 一致：生成在执行者所在位置
        bee.setPos(player.getX(), player.getY(), player.getZ());
        BeeBossData.setBeeBoss(bee, true);
        // 命令/刷怪蛋召唤都属于「新建」：补满生命值并挂上索敌与远程 AI、默认蜂巢权杖
        BeeBossData.applyBeeBossDefaults(bee, true);
        serverLevel.addFreshEntity(bee);

        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BEE_HURT, SoundSource.HOSTILE, 1.0F, 0.8F);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
