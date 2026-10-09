package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.mixin.MobPersistenceAccessor;
import cn.autoforged.joes_addons_for_abmc.worldgen.ModDimensions;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 幸运维度里「生物」这一套规则的集中地：和平共处 AI、水生生物的空游、以及自然生成的标记。
 *
 * <h3>和平共处 AI 是照抄音符盒维度那套</h3>
 * 音符盒维度（{@code ModDimensions.NOTE_DIM_LEVEL}）的做法见 {@code ModMain#onEntityJoinLevel} 与
 * {@code ModMain#onLivingChangeTarget}：<b>两条</b>——
 * <ol>
 *   <li><b>索敌闸</b>：任何生物在该维度都不允许锁定目标（{@link #onLivingChangeTarget}），
 *       捕食关系（狼扑羊、豹猫扑鸡）也一并消失；</li>
 *   <li><b>进维度时拆掉"逃跑/躲避"类 Goal</b>（{@link ModMain#applyPeacefulCoexistenceAi}）：
 *       动物躲玩家、狼躲羊驼、狐狸躲北极熊、村民感知亡灵、铁傀儡主动打怪、苦力怕躲猫……
 *       这些行为不是靠"锁目标"实现的，只清目标拦不住，必须把 Goal 拆掉。</li>
 * </ol>
 * 本类不重复实现第 2 条，直接调 {@link ModMain#applyPeacefulCoexistenceAi}（那一份是从音符盒维度那段
 * 原样抽出来的，将来改一处记得另一处）。
 *
 * <h3>水生生物"把空气当水"</h3>
 * 靠把 {@code Entity#isInWater()} 在<b>本维度的水生生物</b>身上伪造为 true
 * （{@code EntityAirSwimMixin}）。原生代码里所有关键判断读的都是它：
 * <ul>
 *   <li>{@code LivingEntity#travel}（LivingEntity.java:2228-2261）→ 走水中移动分支：阻力、缓降；</li>
 *   <li>{@code WaterBoundPathNavigation#canUpdatePath}（:26-28）读 {@code mob.isInLiquid()}，
 *       而 {@code isInLiquid()} = {@code isInWaterOrBubble() || isInLava()}（Entity.java:1256-1258）
 *       → 水生导航在空气里也能算出路径；</li>
 *   <li>{@code SmoothSwimmingMoveControl#tick}（:26-45）两处读 {@code mob.isInWater()}
 *       → 鱼/海豚/美西螈/蝌蚪的游动控制照常工作。</li>
 * </ul>
 * 另外每刻补两件事（{@link #tickAquatic}）：把氧气补满（免得原版缺氧伤害），以及压住 y 上限
 * {@link #AQUATIC_CEILING_Y}（需求：最高不高过 y=200）。
 */
public final class LuckyDimensionMobs {

    /** 水生生物在本维度的飞行上限（需求：最高不会高过这个 y）。 */
    public static final int AQUATIC_CEILING_Y = 200;

    /** 离玩家多远就删除（需求 4）。 */
    public static final double DESPAWN_DISTANCE = 128.0D;

    /**
     * 「正在由 {@link LuckyMobSpawner} 自然生成」的窗口标记。
     * <p>
     * 用来区分"我们自己刷出来的生物"和"玩家带进来 / 其它途径来的生物"：只有前者会被清掉
     * {@code PersistenceRequired}（让需求 4 的 128 格删除对它们生效），玩家自己命名过的宠物不会被误删。
     */
    private static boolean naturalSpawnInProgress;

    private LuckyDimensionMobs() {
    }

    /** 是否幸运维度。 */
    public static boolean isLuckyDimension(Level level) {
        return level.dimension().equals(ModDimensions.LUCKY_DIM_LEVEL);
    }

    public static void beginNaturalSpawn() {
        naturalSpawnInProgress = true;
    }

    public static void endNaturalSpawn() {
        naturalSpawnInProgress = false;
    }

    /**
     * 是不是"水生生物"。
     * <p>
     * 主要按原版刷怪分类判断（鱼/鱿鱼/海豚是 WATER_AMBIENT / WATER_CREATURE、发光鱿鱼是
     * UNDERGROUND_WATER_CREATURE、美西螈是 AXOLOTLS），但有三只不在这些分类里，单独列出：
     * <b>守卫者 / 远古守卫者</b>是 {@code MONSTER}（EntityType.java:409/:304），
     * <b>蝌蚪</b>是 {@code CREATURE}（:663）。海龟虽然也亲水，但它本来就上岸不死，不需要处理。
     */
    public static boolean isAquatic(EntityType<?> type) {
        if (type == EntityType.GUARDIAN || type == EntityType.ELDER_GUARDIAN || type == EntityType.TADPOLE) {
            return true;
        }
        return switch (type.getCategory()) {
            case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> true;
            default -> false;
        };
    }

    /** 该实体在本维度是否要把空气当水（给 {@code Entity#isInWater} 的 mixin 用）。 */
    public static boolean swimsInAir(Entity entity) {
        return entity instanceof Mob
            && isAquatic(entity.getType())
            && isLuckyDimension(entity.level());
    }

    /** 生物进入本维度时套用维度规则（无论它是刷出来的、走传送门来的还是命令召唤的）。 */
    public static void onMobJoinLevel(Mob mob) {
        // 需求 1 的后半：本维度永远不出现能捡装备的生物
        if (mob.canPickUpLoot()) {
            mob.setCanPickUpLoot(false);
        }
        // 和平共处：拆掉躲避/逃跑类 Goal（索敌那条由 onLivingChangeTarget 负责）
        ModMain.applyPeacefulCoexistenceAi(mob);
        // 我们自己自然生成的那些：清掉 PersistenceRequired，让它们受"离玩家 128 格删除"约束
        if (naturalSpawnInProgress) {
            ((MobPersistenceAccessor) (Object) mob).jafa_setPersistenceRequired(false);
        }
    }

    /**
     * 水生生物的每刻维护（由 {@link LuckyMobSpawner} 的每刻扫描顺手调用，不额外扫一遍实体）。
     * <ul>
     *   <li>氧气补满：它们这辈子不会真的进水，不补就会走原版的缺氧伤害（LivingEntity.java:2851）；</li>
     *   <li>y 上限：漂到 {@link #AQUATIC_CEILING_Y} 以上就按回去，并掐掉向上的速度。</li>
     * </ul>
     */
    public static void tickAquatic(Mob mob) {
        if (!isAquatic(mob.getType())) {
            return;
        }
        if (mob.getAirSupply() < mob.getMaxAirSupply()) {
            mob.setAirSupply(mob.getMaxAirSupply());
        }
        if (mob.getY() > AQUATIC_CEILING_Y) {
            Vec3 movement = mob.getDeltaMovement();
            mob.setPos(mob.getX(), AQUATIC_CEILING_Y, mob.getZ());
            if (movement.y > 0.0D) {
                mob.setDeltaMovement(movement.x, 0.0D, movement.z);
            }
        }
    }

    // ===== 事件处理（在 ModMain 的构造里 addListener 注册）=====

    /** 和平共处：本维度内谁都不许锁定攻击目标。 */
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!isLuckyDimension(event.getEntity().level())) {
            return;
        }
        event.setNewAboutToBeSetTarget(null);
    }

    /** 生物进入本维度：套用维度规则。 */
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (!isLuckyDimension(event.getLevel())) {
            return;
        }
        onMobJoinLevel(mob);
    }

    /**
     * 水生生物不受缺水伤害：本维度里它们的"水"是空气，所以原版那两种伤害一律取消——
     * {@code dry_out}（海豚 Dolphin.java:236、美西螈 Axolotl.java:197）与
     * {@code drown}（WaterAnimal 基类 :41，鱼/鱿鱼/海豚共用；缺氧也是这个类型）。
     */
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (!isAquatic(entity.getType()) || !isLuckyDimension(entity.level())) {
            return;
        }
        if (event.getSource().is(DamageTypes.DRY_OUT) || event.getSource().is(DamageTypes.DROWN)) {
            event.setCanceled(true);
        }
    }
}
