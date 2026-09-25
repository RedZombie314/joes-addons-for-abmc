package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.entity.EnchantedOrigamiEntity;
import cn.autoforged.joes_addons_for_abmc.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * “装有千纸鹤的玻璃瓶”——收容载体。
 * - 手持它右键方块（useOn）在有空间的地方重新生成附魔千纸鹤，把存的数据（战斗模式、收容者、名字）写回。
 * - 瓶子存放的数据保存在 ItemStack 的 custom NBT/组件中（melee、containerUuid、storedName）。
 * - 若瓶子被铁砧改名（CUSTOM_NAME），放出的实体自动带上这个名字。
 * - 每次右键被调用时走 {@link ModMain} 的玻璃瓶-实体交互监听事件（装瓶/瓶子放生由单独逻辑处理）。
 */
public class OrigamiBottleItem extends Item {

    public OrigamiBottleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!(level instanceof ServerLevel serverLevel) || player == null) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();

        // 找到可放置位置：优先击中的相邻面，否则该方块上方
        BlockPos spawnPos = this.findSpawnPos(serverLevel, clicked.relative(face), face);
        if (spawnPos == null) {
            return InteractionResult.FAIL;
        }

        EnchantedOrigamiEntity origami = ModEntities.ENCHANTED_ORIGAMI.get().create(serverLevel);
        if (origami == null) return InteractionResult.FAIL;
        origami.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

        // 读取并写回瓶中数据（数据保存在 CUSTOM_DATA 自定义数据组件中）
        CompoundTag tag = stack.getOrDefault(
            net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        origami.setMelee(tag.getBoolean("melee"));
        String cuu = tag.getString("containerUuid");
        // 若之前没有收容者，将第一个右键放生它的人设为收容者
        if (cuu == null || cuu.isEmpty() || "-1".equals(cuu)) {
            cuu = player.getUUID().toString();
            tag.putString("containerUuid", cuu);
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        }
        origami.setContainerUuid(cuu);
        // 放生（无论收容过几次）始终重置存活时间为 30 分钟 = 36000 刻
        origami.setLifeTicks(36000);

        // 名字：优先取铁砧改名的 CustomName，否则取瓶中保留的名字
        @Nullable
        Component bakedName = stack.get(DataComponents.CUSTOM_NAME);
        if (bakedName == null && tag.contains("storedName")) {
            bakedName = Component.literal(tag.getString("storedName"));
        }
        if (bakedName != null) origami.setCustomName(bakedName);

        // 只生成成功时才清空瓶内数据（避免残留）
        serverLevel.addFreshEntity(origami);
        serverLevel.playSound(null, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
            SoundEvents.BAT_TAKEOFF, SoundSource.NEUTRAL, 0.5F, 1.2F);

        // 放出千纸鹤后返还一个空玻璃瓶（不消耗原瓶，类似倒空药水后归还玻璃瓶）
        if (!player.getAbilities().instabuild) {
            ItemStack hand = player.getItemInHand(context.getHand());
            if (hand.getCount() > 1) {
                hand.shrink(1);
                if (!player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
                    player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
                }
            } else {
                player.setItemInHand(context.getHand(), new ItemStack(Items.GLASS_BOTTLE));
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** 寻找可放置位置：优先指定方块，令其上方空、脚下有实体方块最安全；找相邻可放处。 */
    @Nullable
    private BlockPos findSpawnPos(ServerLevel level, BlockPos preferred, Direction face) {
        // 在 preferred（点击相邻面）位置判断是否为空且可替换
        if (level.isEmptyBlock(preferred) || level.getBlockState(preferred).canBeReplaced()) {
            return preferred;
        }
        // 遍历 6 个方向找相邻空气格
        for (Direction d : Direction.values()) {
            BlockPos candidate = preferred.relative(d);
            if (level.isEmptyBlock(candidate) || level.getBlockState(candidate).canBeReplaced()) {
                return candidate;
            }
        }
        // 逐渐上移找空间
        BlockPos cur = preferred;
        for (int i = 1; i < 5; i++) {
            cur = cur.above();
            if (level.isEmptyBlock(cur)) return cur;
        }
        return null;
    }
}