package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;

/**
 * 幸运实体子事件：<b>穿着金制马铠和马鞍的马</b>（需求 5）。
 *
 * <h3>取值</h3>
 * <ul>
 *   <li><b>移速 15 格/秒</b>：原版马的速度属性单位就是"格/刻"，15 格/秒 = 15 ÷ 20 = <b>0.75</b>
 *       （原版自然生成的好马大约 0.2~0.34，即 4~7 格/秒，所以这匹明显快得多）；</li>
 *   <li><b>跳跃力量 1</b>：原版马该属性在 0.4~1.0 之间随机（跳跃高度随之变化），这里直接顶到上限 1.0；</li>
 *   <li><b>瞬时驯服</b>：只有当这颗幸运方块是<b>玩家</b>破坏的时候才成立 —— 把 Owner 设成该玩家并
 *       {@code setTamed(true)}，同时广播原版驯服爱心粒子（和狼事件同一套做法）；非玩家破坏则保持未驯服；</li>
 *   <li>马铠走 {@code EquipmentSlot.BODY}（1.21 起马铠是 BODY 装备槽，渲染由原版
 *       {@code HorseArmorLayer} 读 {@code getBodyArmorItem()} 完成），马鞍走 {@code equipSaddle}。</li>
 * </ul>
 *
 * <p>属性在<b>创建之后</b>直接写死：这里不走 {@code finalizeSpawn}，所以原版那套随机属性不会被套用。
 */
public final class LuckyGoldenHorseEvent {

    /** 移速：0.75 格/刻 = 15 格/秒（需求）。 */
    public static final double MOVEMENT_SPEED = 0.75D;

    /** 跳跃力量（需求：1）。 */
    public static final double JUMP_STRENGTH = 1.0D;

    private LuckyGoldenHorseEvent() {
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("entity/golden_horse"), LuckyEventCategory.LUCKY_ENTITY,
            "生成一匹穿金马铠、带鞍的马（移速 15 格/秒、跳跃力量 1；玩家破坏时立刻归该玩家所有）",
            LuckyGoldenHorseEvent::spawnHorse);
        LuckyEvents.register(event);
        return event;
    }

    private static void spawnHorse(ServerLevel level, BlockPos pos, @Nullable Player player) {
        Horse horse = EntityType.HORSE.create(level);
        if (horse == null) return;

        horse.setAge(0); // 成年马（成年才骑得上）

        AttributeInstance speed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(MOVEMENT_SPEED);
        }
        AttributeInstance jump = horse.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            jump.setBaseValue(JUMP_STRENGTH);
        }

        // 装备：鞍 + 金马铠
        // 马铠在 1.21 走 EquipmentSlot.BODY（不是 CHEST），本模组别处（OrbPossessedAttackEvents 给骷髅马配铠）
        // 用的也是这一句，保持同一写法；鞍走原版 Saddleable#equipSaddle（写进马的 inventory 第 0 格，
        // 由 AbstractHorse#containerChanged -> syncSaddleToClients 置上"已上鞍"标志）。
        horse.equipSaddle(new ItemStack(Items.SADDLE), SoundSource.NEUTRAL);
        horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.GOLDEN_HORSE_ARMOR));

        // 玩家破坏 -> 立刻归该玩家所有（先写 Owner 再置驯服，和猫/狼事件的顺序一致）
        if (player != null) {
            horse.setOwnerUUID(player.getUUID());
            horse.setTamed(true);
        }

        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        double y = LuckyEvents.findFreeY(level, horse, x, pos.getY(), z);
        horse.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
        horse.setPersistenceRequired();

        if (!level.addFreshEntity(horse)) return;
        if (player != null) {
            level.broadcastEntityEvent(horse, (byte) 7); // 原版驯服爱心粒子
        }
    }
}
