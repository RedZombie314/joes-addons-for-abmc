package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * 幸运实体子事件：<b>猪套娃</b>。
 *
 * <p>生成 {@value #PIG_COUNT} 只猪自上而下叠成一座塔（第 2 只骑在第 1 只上、第 3 只骑在第 2 只上……），
 * 最上面那只（第 {@value #PIG_COUNT} 只）背上再骑着一位<b>无业游民</b>（职业未定的村民）。
 *
 * <p>用 {@code startRiding(vehicle, true)} 强制骑乘，所以不需要鞍；全部设为不被自然消失，
 * 免得塔散掉。村民是直接创建后加入世界的（不走 {@code finalizeSpawn}），
 * 因此职业保持默认的 NONE，也就是"无业游民"。
 */
public final class LuckyPigTowerEvent {
    /** 猪的数量（第 6 只背上骑村民）。 */
    private static final int PIG_COUNT = 6;

    private LuckyPigTowerEvent() {
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("entity/pig_tower"), LuckyEventCategory.LUCKY_ENTITY,
            "生成 6 只叠成塔的猪，最上面那只背着一个无业游民",
            LuckyPigTowerEvent::spawnTower);
        LuckyEvents.register(event);
        return event;
    }

    private static void spawnTower(ServerLevel level, BlockPos pos, @Nullable Player player) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY();
        double z = pos.getZ() + 0.5D;

        Entity below = null;
        for (int i = 0; i < PIG_COUNT; i++) {
            Pig pig = EntityType.PIG.create(level);
            if (pig == null) return;
            pig.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
            pig.setPersistenceRequired();
            level.addFreshEntity(pig);
            if (below != null) {
                pig.startRiding(below, true); // 骑在下面那只猪身上
            }
            below = pig;
        }

        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null || below == null) return;
        villager.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
        villager.setPersistenceRequired();
        level.addFreshEntity(villager);
        villager.startRiding(below, true); // 骑在第 6 只猪背上；职业默认 NONE = 无业游民
    }
}
