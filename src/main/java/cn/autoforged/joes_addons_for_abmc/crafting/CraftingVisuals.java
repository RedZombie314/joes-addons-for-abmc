package cn.autoforged.joes_addons_for_abmc.crafting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 「合成事件」共用的展示用实体：让一个物品看起来从 A 飞到 B。
 *
 * <p>用 {@code item_display} 而不是让真实掉落物飞行，是为了彻底避免复制：
 * 掉落物只要还在世界里（哪怕只是做动画的这 10 刻），就可能被漏斗吸走、被岩浆烧掉、
 * 被别人捡走，而物品此时已经入池/已作为材料取走。
 *
 * <p>1.21.1 里 {@code Display} 的 billboard / 插值 setter 都是私有的，所以外观走原版 NBT：
 * {@code billboard=center} 让它始终面向玩家（否则侧面看是一条线）、
 * {@code item_display=ground} 用原版掉落物那套变换、{@code teleport_duration=1} 让逐刻移动看起来连续。
 */
public final class CraftingVisuals {
    private CraftingVisuals() {
    }

    /**
     * 物品"吸收/拾取"音效：就是原版捡起掉落物时那声"啵"
     * （{@code ITEM_PICKUP}，音量与音调公式跟 {@code ItemEntity#playerTouch} 完全一致，
     * 所以听起来和真的捡东西一样）。
     * <p>工作台帽子在发射每件物品时、工作台权杖在吸收每个掉落物时都会播一次。
     */
    public static void playAbsorbSound(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z,
            net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS,
            0.2F, ((level.random.nextFloat() - level.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
    }

    /** 生成一个展示该物品的 {@code item_display} 实体（位置已设为 {@code at}，尚未加入世界）。 */
    public static Display.ItemDisplay spawnFlyingItem(ServerLevel level, ItemStack stack, Vec3 at) {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        CompoundTag tag = new CompoundTag();
        tag.put("Pos", doubles(at.x, at.y, at.z));
        tag.put("Motion", doubles(0.0, 0.0, 0.0));
        tag.put("Rotation", floats(0.0F, 0.0F));
        tag.put("item", stack.save(level.registryAccess()));
        tag.putString(Display.TAG_BILLBOARD, "center");
        tag.putString("item_display", ItemDisplayContext.GROUND.getSerializedName());
        tag.putInt(Display.TAG_POS_ROT_INTERPOLATION_DURATION, 1);
        display.load(tag);
        display.setPos(at.x, at.y, at.z);
        level.addFreshEntity(display);
        return display;
    }

    private static ListTag doubles(double a, double b, double c) {
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(a));
        list.add(DoubleTag.valueOf(b));
        list.add(DoubleTag.valueOf(c));
        return list;
    }

    private static ListTag floats(float a, float b) {
        ListTag list = new ListTag();
        list.add(FloatTag.valueOf(a));
        list.add(FloatTag.valueOf(b));
        return list;
    }
}
