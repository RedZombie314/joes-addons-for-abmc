package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * <b>女巫Boss</b>的支援行为：发现周围有<b>被变形药水变成其他生物</b>（亡灵生物除外）且<b>生命值高于 1 点</b>的目标时，
 * 朝它投一瓶<b>剧毒 II</b>，冷却 2 秒（40 刻）。
 *
 * <p><b>只对女巫Boss生效</b>：判据是它身上的 {@code jafa_is_witch_boss} 标签，与 {@code WitchMixin} 用的是同一个。
 * 普通女巫不受影响。
 *
 * <p><b>目标分两条路</b>（模组里"变成其他生物"有两种落地方式，缺一不可）：
 * <ol>
 *   <li><b>生物壳</b>：原生生物/宠物被变形时，原实体被删除、新建一只目标类型的生物，
 *       它带持久化标记 {@code jafa_transmutation_shell}（定义见 ModMain.java:1719，写入见 ModMain.java:19547）。</li>
 *   <li><b>玩家</b>：玩家被变形走的是"渲染替换"，场上<b>不会</b>生成生物壳，状态记在玩家自己身上。
 *       判据是 {@link ModMain#isPlayerTransmuted(Player)} 加上 morph 目标类型——
 *       只有 morph 解析出来是个<b>实体类型</b>才算"变成了生物"（方块/物品 id 解析不出实体，自然被排除；
 *       {@code player_shell:} 玩家空壳也不是"其他生物"，同样排除）。</li>
 * </ol>
 *
 * <p><b>亡灵除外</b>：生物壳用它自己的实体类型判断；玩家用 morph 出来的那个实体类型判断。
 * 用的是实体类型标签 {@code EntityTypeTags.UNDEAD}（原版 {@code #undead}，覆盖僵尸/尸壳/溺尸/骷髅/流浪者/幻翼/凋灵等）。
 * 1.21.1 已经没有 {@code MobType} 了，标签才是正确做法。所以"变成尸壳"这种结果不会被投毒。
 *
 * <p><b>为什么要求生命值高于 1 点</b>：剧毒在原版里最多把生命压到 1 点、不会致死，
 * 目标已经只剩 1 点血时再投没有意义。
 *
 * <p>投掷方式与参数完全照抄原版女巫（{@code Witch#performRangedAttack}，Witch.java:259-262）：
 * 溅射药水、速度 0.75、散布 8、仰角 +20，抛物线里带 {@code 水平距离 * 0.2} 的补偿。
 * 剧毒 II 用的是 {@code Potions.STRONG_POISON}（Poison 放大器 1）。
 *
 * <p>本行为不替换女巫原有的攻击，只是在冷却好、有合适目标时额外投一瓶；喝药期间不投（与原版一致）。
 */
public final class WitchTransmutedPoison {

    /** 只对打有这个标签的女巫生效——与 {@code WitchMixin} 用的是同一个标签。 */
    private static final String WITCH_BOSS_TAG = "jafa_is_witch_boss";

    /** 变形后生物壳的持久化标记（ModMain.java:1719 的 {@code TRANSMUTATION_SHELL_TAG}）。 */
    private static final String TRANSMUTATION_SHELL_TAG = "jafa_transmutation_shell";

    /** 玩家 morph 类型里的两个前缀（见 PlayerMorphSync 的注释）。 */
    private static final String MOB_SHELL_PREFIX = "mob_shell:";
    private static final String PLAYER_SHELL_PREFIX = "player_shell:";

    /** 冷却：2 秒 = 40 刻。 */
    private static final int COOLDOWN_TICKS = 40;

    /** 「周围」的搜索半径（格）。 */
    private static final double RANGE = 16.0D;

    /** 冷却存在女巫自己的持久化数据里：不占全局表，也不会随地图跑久而泄漏。 */
    private static final String COOLDOWN_TAG = "jafa_poison_transmuted_cooldown";

    private WitchTransmutedPoison() {
    }

    /** 每刻由 {@code WitchTransmutedPoisonMixin} 注入 {@code Witch#aiStep} 调用。 */
    public static void tick(Witch witch) {
        if (witch.level().isClientSide()) {
            return;
        }
        // 只对女巫Boss生效
        if (!witch.getPersistentData().getBoolean(WITCH_BOSS_TAG)) {
            return;
        }
        // 喝药期间不投（与原版一致）
        if (witch.isDrinkingPotion()) {
            return;
        }
        long now = witch.level().getGameTime();
        if (witch.getPersistentData().getLong(COOLDOWN_TAG) > now) {
            return;
        }
        LivingEntity target = findTarget(witch);
        if (target == null) {
            return;
        }
        throwPoison(witch, target);
        witch.getPersistentData().putLong(COOLDOWN_TAG, now + COOLDOWN_TICKS);
    }

    /** 找「被变形药水变成其他生物、非亡灵、生命值高于 1 点、且在视线内」的最近目标；没有就返回 null。 */
    private static LivingEntity findTarget(Witch witch) {
        LivingEntity best = null;
        double bestSqr = RANGE * RANGE;
        for (LivingEntity candidate : witch.level().getEntitiesOfClass(LivingEntity.class,
                witch.getBoundingBox().inflate(RANGE))) {
            if (candidate == witch || !candidate.isAlive()) {
                continue;
            }
            if (!isTransmutedCreature(candidate)) {
                continue;
            }
            // 生命值必须高于 1 点
            if (candidate.getHealth() <= 1.0F) {
                continue;
            }
            if (!witch.hasLineOfSight(candidate)) {
                continue;
            }
            double distanceSqr = witch.distanceToSqr(candidate);
            if (distanceSqr < bestSqr) {
                bestSqr = distanceSqr;
                best = candidate;
            }
        }
        return best;
    }

    /** 是否「被变形药水变成了其他生物」，且那生物不是亡灵。两种落地方式都要覆盖，详见类注释。 */
    private static boolean isTransmutedCreature(LivingEntity entity) {
        // 一、变形后的生物壳：原生生物/宠物被删除后新建的那只生物
        if (entity.getPersistentData().getBoolean(TRANSMUTATION_SHELL_TAG)) {
            return !entity.getType().is(EntityTypeTags.UNDEAD);
        }
        // 二、玩家被变形：场上没有生物壳，状态记在玩家自己身上
        if (entity instanceof Player player && ModMain.isPlayerTransmuted(player)) {
            EntityType<?> morphed = morphEntityType(player);
            return morphed != null && !morphed.is(EntityTypeTags.UNDEAD);
        }
        return false;
    }

    /**
     * 玩家当前 morph 目标若是<b>实体类型</b>就返回它，否则返回 null。
     * <p>
     * morph 字符串的形态见 {@code PlayerMorphSync}：{@code mob_shell:<实体id>}、{@code player_shell:<名字>}，
     * 方块/物品形态则直接是方块/物品 id。后两者解析不出实体类型，于是自然被排除——这正好等于
     * "变成了其他生物"这个条件。{@code mob_shell:} 前缀这里容错处理（有没有都认）。
     */
    @Nullable
    private static EntityType<?> morphEntityType(Player player) {
        String morph = PlayerMorphSync.getMorphType(player);
        if (morph == null || morph.isBlank()) {
            return null;
        }
        if (morph.startsWith(PLAYER_SHELL_PREFIX)) {
            return null; // 玩家空壳，不是"其他生物"
        }
        if (morph.startsWith(MOB_SHELL_PREFIX)) {
            morph = morph.substring(MOB_SHELL_PREFIX.length());
        }
        ResourceLocation id = ResourceLocation.tryParse(morph);
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    /** 按原版女巫的抛物线与参数投出一瓶剧毒 II（溅射）。 */
    private static void throwPoison(Witch witch, LivingEntity target) {
        Vec3 motion = target.getDeltaMovement();
        double dx = target.getX() + motion.x - witch.getX();
        double dy = target.getEyeY() - 1.1D - witch.getY();
        double dz = target.getZ() + motion.z - witch.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        ThrownPotion potion = new ThrownPotion(witch.level(), witch);
        potion.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_POISON));
        potion.setXRot(potion.getXRot() + 20.0F);
        potion.shoot(dx, dy + horizontal * 0.2D, dz, 0.75F, 8.0F);
        witch.level().addFreshEntity(potion);
    }
}
