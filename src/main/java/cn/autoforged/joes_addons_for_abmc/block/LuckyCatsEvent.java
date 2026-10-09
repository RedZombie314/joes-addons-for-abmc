package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 幸运实体子事件：<b>刷新 5~8 只猫，最多再带一只豹猫</b>。
 *
 * <ul>
 *   <li>先掷一次 {@value #OCELOT_CHANCE}：掷中则这一批里有<b>且只有一只豹猫</b>（位置随机），
 *       其余全是<b>猫</b>（成年、毛色随机）；没掷中就全是猫 —— 所以豹猫<b>最多一只</b>；</li>
 *   <li>方块由<b>玩家</b>破坏 → 下一个游戏刻：
 *       <b>猫</b>被该玩家驯服（Owner = 该玩家、爱心粒子、项圈），<b>豹猫</b>的 <b>Trusting 置为 true</b>
 *       （1.21 的 {@code Ocelot} 只有 {@code Trusting} 布尔值，没有 UUID 字段，所以"信任该玩家"就是置位）；</li>
 *   <li>非玩家破坏（爆炸等）→ 一律保持野生（不驯服、不信任）。</li>
 * </ul>
 */
public final class LuckyCatsEvent {
    /** 刷新数量区间（含端点）。 */
    private static final int MIN_ANIMALS = 5;
    private static final int MAX_ANIMALS = 8;
    /** 这一批里出现一只豹猫的概率（其余全是猫；豹猫最多一只）。 */
    private static final float OCELOT_CHANCE = 0.5F;

    /** 待处理的猫/豹猫（在下一个游戏刻执行驯服 / 设置信任）。 */
    private static final List<PendingTame> PENDING = new ArrayList<>();

    private LuckyCatsEvent() {
    }

    /** 待处理记录：世界 + 刷出的动物 + 主人 UUID。 */
    private record PendingTame(ServerLevel level, List<Entity> animals, UUID owner) {
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("entity/cats"), LuckyEventCategory.LUCKY_ENTITY,
            "刷新 5~8 只随机毛色的成年猫（最多一只豹猫；玩家破坏时猫被驯服、豹猫被信任）",
            LuckyCatsEvent::spawnAnimals);
        LuckyEvents.register(event);
        return event;
    }

    /** 事件本体：刷猫/豹猫；只有"由玩家破坏"时才登记下一刻的驯服 / 信任。 */
    private static void spawnAnimals(ServerLevel level, BlockPos pos, @Nullable Player player) {
        RandomSource random = level.getRandom();
        int count = MIN_ANIMALS + random.nextInt(MAX_ANIMALS - MIN_ANIMALS + 1);
        // 豹猫最多一只：先决定这一批里有没有豹猫，有的话随机挑一只的位置，其余一律是猫
        int ocelotIndex = random.nextFloat() < OCELOT_CHANCE ? random.nextInt(count) : -1;
        List<Holder.Reference<CatVariant>> variants = BuiltInRegistries.CAT_VARIANT.holders().toList();
        List<Entity> animals = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            boolean asOcelot = (i == ocelotIndex);
            Entity animal = (asOcelot ? EntityType.OCELOT : EntityType.CAT).create(level);
            if (animal == null) continue;

            if (animal instanceof Cat cat) {
                cat.setBaby(false);
                cat.setAge(0); // 成年
                if (!variants.isEmpty()) {
                    cat.setVariant(variants.get(random.nextInt(variants.size()))); // 毛色随机
                }
            }

            double x = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 1.6D;
            double z = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 1.6D;
            animal.moveTo(x, pos.getY() + 0.2D, z, random.nextFloat() * 360.0F, 0.0F);
            if (!level.addFreshEntity(animal)) continue;
            animals.add(animal);
        }

        if (!animals.isEmpty() && player != null) {
            PENDING.add(new PendingTame(level, animals, player.getUUID()));
        }
    }

    /**
     * {@code Ocelot.setTrusting(boolean)} 在 1.21.1 是 private（无法直接调用，也没有公开 setter），
     * 这里用反射调用它。NeoForge 运行时使用官方（Mojang）名称，所以按名字取方法即可。
     * <p>如果你更偏好"编译期可见"的写法，可以在模组的访问转换器里放开这个方法，然后直接调用。
     */
    private static final java.lang.reflect.Method SET_TRUSTING = findSetTrusting();

    @Nullable
    private static java.lang.reflect.Method findSetTrusting() {
        try {
            java.lang.reflect.Method method = Ocelot.class.getDeclaredMethod("setTrusting", boolean.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** 令豹猫信任玩家（Trusting = true）。 */
    private static void makeTrusting(Ocelot ocelot) {
        if (SET_TRUSTING == null) return;
        try {
            SET_TRUSTING.invoke(ocelot, true);
        } catch (ReflectiveOperationException ignored) {
            // 反射失败时保持野生，不影响其它部分
        }
    }

    /** 由服务端 tick 钩子（{@code ModMain.onServerTickPre}）每刻调用。 */
    public static void tick() {
        if (PENDING.isEmpty()) return;
        List<PendingTame> pendings = new ArrayList<>(PENDING);
        PENDING.clear();
        for (PendingTame pending : pendings) {
            for (Entity animal : pending.animals()) {
                if (animal.isRemoved()) continue;
                if (animal instanceof Cat cat) {
                    cat.setOwnerUUID(pending.owner());
                    cat.setTame(true, true);
                    cat.setPersistenceRequired();
                    pending.level().broadcastEntityEvent(cat, (byte) 7); // 原版驯服爱心粒子
                } else if (animal instanceof Ocelot ocelot) {
                    // 1.21 的豹猫只有 Trusting 布尔值（没有 UUID 字段）：信任该玩家即置位
                    makeTrusting(ocelot);
                    ocelot.setPersistenceRequired();
                }
            }
        }
    }
}
