package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * 幸运实体子事件：<b>骑着鸡的兔子</b>（需求 2）。
 *
 * <p>先刷一只鸡，再刷一只兔子并 {@code startRiding(鸡, true)} 强制骑上去（不需要鞍，所以和猪塔、女巫恶魂同一套写法）。
 * 兔子自己会像原版那样蹦跶（它在鸡背上，位移跟着鸡走），鸡照常下蛋、扑腾。
 *
 * <p>两者都设为不被自然消失，免得这只"组合"自己刷掉。
 */
public final class LuckyRabbitChickenEvent {
    private LuckyRabbitChickenEvent() {
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("entity/rabbit_chicken"), LuckyEventCategory.LUCKY_ENTITY,
            "生成一只骑着鸡的兔子",
            LuckyRabbitChickenEvent::spawnRabbitOnChicken);
        LuckyEvents.register(event);
        return event;
    }

    private static void spawnRabbitOnChicken(ServerLevel level, BlockPos pos, @Nullable Player player) {
        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        float yaw = level.random.nextFloat() * 360.0F;

        Chicken chicken = EntityType.CHICKEN.create(level);
        if (chicken == null) return;
        chicken.moveTo(x, pos.getY() + 0.2D, z, yaw, 0.0F);
        chicken.setPersistenceRequired();
        if (!level.addFreshEntity(chicken)) return;

        Rabbit rabbit = EntityType.RABBIT.create(level);
        if (rabbit == null) {
            chicken.discard();
            return;
        }
        rabbit.moveTo(x, pos.getY() + 0.2D, z, yaw, 0.0F);
        rabbit.setPersistenceRequired();
        level.addFreshEntity(rabbit);
        rabbit.startRiding(chicken, true); // 强制骑乘，不需要鞍
    }
}
