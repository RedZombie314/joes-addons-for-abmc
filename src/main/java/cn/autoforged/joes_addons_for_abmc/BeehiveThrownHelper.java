package cn.autoforged.joes_addons_for_abmc;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 蜂巢投掷工具：原版蜂巢方块物品（{@code minecraft:bee_nest}）在长按右键（拉弓动画）后松手，
 * 会被发射为携带同款物品数据的高速掉落物实体；落到地面或击中实体后，蜂巢被摧毁并释放内部所有蜜蜂
 * （清空它们的 hive_pos），若击中实体则蜜蜂仇视该实体。蜂巢自身不造成命中伤害。
 */
public final class BeehiveThrownHelper {

    /** 标记被投掷的蜂巢，仅此类掉落物会被打破释放蜜蜂，普通掉落不受影响。 */
    public static final String MARKER = "jafa_thrown_beehive";
    /** 记录投掷者 UUID，避免刚出手就命中自己而被蜜蜂仇视。 */
    public static final String OWNER_KEY = "jafa_thrown_beehive_owner";

    private BeehiveThrownHelper() {
    }

    public static boolean isBeeNest(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.BEE_NEST);
    }

    /** 松手发射：生成一个携带同款物品数据的高速 ItemEntity，并消耗 1 个（创造模式不消耗）。 */
    public static void throwBeehive(Level level, Player player, ItemStack stack) {
        if (level.isClientSide() || stack.isEmpty()) {
            return;
        }
        ItemEntity item = new ItemEntity(level,
            player.getX(), player.getEyeY() - 0.1, player.getZ(), stack.copy());
        item.setPickUpDelay(32767);
        item.getPersistentData().putBoolean(MARKER, true);
        item.getPersistentData().putUUID(OWNER_KEY, player.getUUID());
        // 箭一样的速度与方向：以玩家朝向为基准
        Vec3 look = player.getLookAngle();
        item.setDeltaMovement(look.scale(2.0));
        level.addFreshEntity(item);
        // 消耗（创造模式不消耗）
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    /** 落地 / 命中实体：摧毁蜂巢，释放内部所有蜜蜂，若指定目标则全部蜜蜂仇视该实体。 */
    public static void breakAndReleaseBees(Level level, ItemEntity item, LivingEntity target) {
        if (level.isClientSide()) {
            return;
        }
        releaseBeesFromStack(level, item.getItem(), item.getX(), item.getY(), item.getZ(), target);
        item.discard();
    }

    /** 读取蜂巢的蜜蜂数据组件（DataComponents.BEES），逐个生成蜜蜂实体并清空其 hive_pos、HasStung=false。 */
    private static void releaseBeesFromStack(Level level, ItemStack stack,
                                             double x, double y, double z, LivingEntity target) {
        List<BeehiveBlockEntity.Occupant> occupants = stack.getOrDefault(DataComponents.BEES, List.of());
        for (BeehiveBlockEntity.Occupant occ : occupants) {
            CompoundTag entityData = occ.entityData().copyTag();
            if (entityData.isEmpty()) {
                continue;
            }
            Bee bee;
            if (entityData.getBoolean("jafa_beeper")) {
                bee = cn.autoforged.joes_addons_for_abmc.entity.ModEntities.BEEPER.get().create(level);
            } else {
                bee = new Bee(EntityType.BEE, level);
            }
            if (bee == null) {
                continue;
            }
            // 确保释放出的蜜蜂 HasStung=false（即长出新的毒刺，可再次攻击）
            entityData.putBoolean(Bee.TAG_HAS_STUNG, false);
            bee.readAdditionalSaveData(entityData);
            // 强制关闭 NoAI（存储 NBT 可能残留 NoAI=true，会抑制导航/索敌）
            bee.setNoAi(false);
            // 清空 hive_pos 数据，防止蜜蜂飞回旧巢
            bee.setSavedFlowerPos(null);
            bee.setHivePos(null);
            // 释放后 1 秒免疫挤压伤害（避免堆叠/卡方块被压死）
            BeehiveStaffHelper.grantCrushImmunity(bee, 20);
            bee.setPos(x, y + 0.3, z);
            if (target != null) {
                if (bee instanceof cn.autoforged.joes_addons_for_abmc.entity.BeeperEntity beeper) {
                    // 苦力蜂：记录攻击目标，飞向目标并自爆（不蛰刺）
                    beeper.getPersistentData().putUUID("beeper_target_uuid", target.getUUID());
                } else {
                    bee.setTarget(target);
                    bee.setPersistentAngerTarget(target.getUUID());
                    bee.setRemainingPersistentAngerTime(400);
                }
            }
            level.addFreshEntity(bee);
        }
    }
}