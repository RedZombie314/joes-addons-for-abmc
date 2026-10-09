package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * 幸运实体子事件：<b>骑着蝙蝠的雪傀儡</b>（需求 4）。
 *
 * <p>先刷一只蝙蝠（顺手 {@code setResting(false)}，免得它白天就地挂着不动），再刷一只雪傀儡并
 * {@code startRiding(蝙蝠, true)} 强制骑上去。之后两者各跑各的原版 AI：蝙蝠乱飞、雪傀儡照常朝怪物丢雪球。
 *
 * <p>高度用 {@link LuckyEvents#findFreeY} 按<b>雪傀儡的碰撞箱</b>（0.7×1.9）找，避免被塞进方块里。
 *
 * <p>注意雪傀儡的原版行为会在<b>自己所在的那一格</b>铺雪（{@code SnowGolem#aiStep}）——骑在蝙蝠身上飞时
 * 就是"一路留下一条空中雪痕"。这是原版逻辑，这里<b>不覆盖</b>（需求没要求改，改了反而是全局魔改）。
 */
public final class LuckySnowGolemBatEvent {
    private LuckySnowGolemBatEvent() {
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("entity/snow_golem_bat"), LuckyEventCategory.LUCKY_ENTITY,
            "生成一只骑着蝙蝠的雪傀儡",
            LuckySnowGolemBatEvent::spawnGolemOnBat);
        LuckyEvents.register(event);
        return event;
    }

    private static void spawnGolemOnBat(ServerLevel level, BlockPos pos, @Nullable Player player) {
        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;

        SnowGolem golem = EntityType.SNOW_GOLEM.create(level);
        if (golem == null) return;
        // 用雪傀儡自己当探针：从幸运方块那一格由下往上找第一个塞得下它的高度
        double y = LuckyEvents.findFreeY(level, golem, x, pos.getY(), z);

        Bat bat = EntityType.BAT.create(level);
        if (bat == null) return;
        bat.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
        bat.setResting(false);
        bat.setPersistenceRequired();
        if (!level.addFreshEntity(bat)) return;

        golem.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
        golem.setPersistenceRequired();
        if (!level.addFreshEntity(golem)) {
            bat.discard();
            return;
        }
        // 强制骑乘：蝙蝠默认只允许一个乘客，force 绕过尺寸/规则检查
        golem.startRiding(bat, true);
    }
}
