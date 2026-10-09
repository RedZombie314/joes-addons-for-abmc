package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.entity.GoldNuggetPig;
import cn.autoforged.joes_addons_for_abmc.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * 幸运实体子事件：<b>头顶不断喷发金粒（不可被捡起）的猪</b>（需求 3）。
 *
 * <p>本体是自定实体 {@link GoldNuggetPig}（原版猪的派生类）：每 10 刻从头顶喷出一颗金粒掉落物，
 * 金粒的拾取延迟被设成"永不"（玩家捡不走），并且寿命被压到 5 秒，不会在维度里堆成山。
 * 具体行为与取值理由见该实体类的注释。
 */
public final class LuckyGoldNuggetPigEvent {
    private LuckyGoldNuggetPigEvent() {
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("entity/gold_nugget_pig"), LuckyEventCategory.LUCKY_ENTITY,
            "生成一只头顶不断喷发金粒（不可被捡起）的猪",
            LuckyGoldNuggetPigEvent::spawnPig);
        LuckyEvents.register(event);
        return event;
    }

    private static void spawnPig(ServerLevel level, BlockPos pos, @Nullable Player player) {
        GoldNuggetPig pig = ModEntities.GOLD_NUGGET_PIG.get().create(level);
        if (pig == null) return;
        pig.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
            level.random.nextFloat() * 360.0F, 0.0F);
        pig.setPersistenceRequired();
        level.addFreshEntity(pig);
    }
}
