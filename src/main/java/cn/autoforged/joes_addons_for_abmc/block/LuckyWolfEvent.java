package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.WolfVariant;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 幸运实体子事件：<b>生成一只随机毛色的狼</b>。
 *
 * <ul>
 *   <li><b>随机毛色</b>：1.20.5 起狼有了变种（pale / spotted / snowy / black / ashen / rusty / woods /
 *       chestnut / striped），它们是<b>数据包注册表</b> {@code minecraft:wolf_variant} 的内容，
 *       所以这里通过 {@code level.registryAccess().lookup(Registries.WOLF_VARIANT)} 取出全部条目随机挑一个
 *       （数据包加/删变种都会自动生效），而不是写死某几种；</li>
 *   <li><b>归属</b>：如果这颗幸运方块是<b>玩家</b>破坏的，狼生成后立刻把 Owner 设为该玩家
 *       （{@code setOwnerUUID} + {@code setTame(true, true)}，并广播原版驯服的爱心粒子）；
 *       非玩家破坏（爆炸等）则保持野生；</li>
 *   <li>设为不被自然消失，免得这只"奖励狼"自己刷掉。</li>
 * </ul>
 */
public final class LuckyWolfEvent {
    private LuckyWolfEvent() {
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("entity/wolf"), LuckyEventCategory.LUCKY_ENTITY,
            "生成一只随机毛色的狼（玩家破坏时该狼的主人就是该玩家）",
            LuckyWolfEvent::spawnWolf);
        LuckyEvents.register(event);
        return event;
    }

    private static void spawnWolf(ServerLevel level, BlockPos pos, @Nullable Player player) {
        Wolf wolf = EntityType.WOLF.create(level);
        if (wolf == null) return;

        RandomSource random = level.getRandom();

        // 随机毛色：从数据包注册表 wolf_variant 的全部条目里随机挑一个
        List<Holder.Reference<WolfVariant>> variants = level.registryAccess()
            .lookup(Registries.WOLF_VARIANT)
            .map(registry -> registry.listElements().toList())
            .orElse(List.of());
        if (!variants.isEmpty()) {
            wolf.setVariant(variants.get(random.nextInt(variants.size())));
        }

        wolf.moveTo(pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 1.6D,
            pos.getY() + 0.2D,
            pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 1.6D,
            random.nextFloat() * 360.0F, 0.0F);
        wolf.setPersistenceRequired();

        // 玩家破坏 -> Owner 就是该玩家（和猫事件同样的做法：先设主人再驯服）
        if (player != null) {
            wolf.setOwnerUUID(player.getUUID());
            wolf.setTame(true, true);
        }

        if (!level.addFreshEntity(wolf)) return;
        if (player != null) {
            level.broadcastEntityEvent(wolf, (byte) 7); // 原版驯服爱心粒子
        }
    }
}
