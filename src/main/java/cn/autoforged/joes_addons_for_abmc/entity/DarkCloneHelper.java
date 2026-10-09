package cn.autoforged.joes_addons_for_abmc.entity;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 黑暗分身框架的通用逻辑层。
 *
 * <h2>克隆方式</h2>
 * 不为任何生物新建实体类型，而是<b>用原生物自己的 {@link EntityType} 生成一个实例</b>，
 * 需要保真时再灌入原主的 NBT。这样做的收益正是「包括其它 mod 的生物」这一条：
 * <ul>
 *   <li>走的就是它自己的存档格式，属性、AI 目标表、装备、掉落表、音效、动画、碰撞箱全部照搬；</li>
 *   <li>其它 mod 的生物自动支持——只要它的 {@code EntityType} 有工厂（{@code type.create(level) != null}），
 *       不需要为任何 mod 写适配代码；</li>
 *   <li>不需要注册新实体、新渲染器、新刷怪蛋。</li>
 * </ul>
 *
 * <h2>能力判定</h2>
 * 「具备攻击力」以 {@link Attributes#ATTACK_DAMAGE} 属性为准：{@code LivingEntity.createLivingAttributes()}
 * 里<b>没有</b> ATTACK_DAMAGE（羊只加了生命与速度），只有真正会攻击的生物才会带上它，
 * 所以这个判定对原版与 mod 生物都通用，比 {@code instanceof Enemy} 更贴合「有攻击力」的语义
 * （例如狼不是 Enemy 但确实具备攻击力）。
 */
public final class DarkCloneHelper {

    /** 原主的 UUID（放在实体持久化数据里，服务端使用，随存档保存）。 */
    public static final String OWNER_KEY = "jafa_dark_clone_owner";
    /** Owner 仇恨队列（按顺序索敌）。 */
    public static final String PRIORITY_KEY = "jafa_dark_clone_priority";

    // ===== 白草方块 / 黑草方块（重命名后的 Minecraft Game Icon） =====

    /**
     * 写在物品自定义数据里的标记键，用来稳定区分白草方块与黑草方块。
     * 刻意不靠显示名判定：显示名是可翻译文本，且玩家可以再用铁砧改名，
     * 而这个标记是不可见的、且能扛住铁砧改名。
     */
    public static final String ICON_MARKER_KEY = "jafa_game_icon";
    public static final String ICON_POSITIVE = "positive";
    public static final String ICON_NEGATIVE = "negative";

    /** 白草方块（原 Positive Game Icon）的显示名。 */
    public static final Component POSITIVE_ICON_NAME =
        Component.translatable("joes_addons_for_abmc.game_icon.positive");
    /** 黑草方块（原 Negative Game Icon）的显示名。 */
    public static final Component NEGATIVE_ICON_NAME =
        Component.translatable("joes_addons_for_abmc.game_icon.negative");

    /** 生物记录 / 召唤姿态：主手黑草方块 + 副手白草方块。 */
    public static boolean isDarkCloneStance(Player player) {
        return isNegativeIcon(player.getMainHandItem()) && isPositiveIcon(player.getOffhandItem());
    }

    /** 是否持有黑草方块（主手或副手）——左键抹除黑暗分身的判定条件。 */
    public static boolean holdsBlackGrassBlock(Player player) {
        return isNegativeIcon(player.getMainHandItem()) || isNegativeIcon(player.getOffhandItem());
    }

    public static boolean isPositiveIcon(ItemStack stack) {
        return ICON_POSITIVE.equals(iconMarker(stack));
    }

    public static boolean isNegativeIcon(ItemStack stack) {
        return ICON_NEGATIVE.equals(iconMarker(stack));
    }

    /** 重命名为白 / 黑草方块，并写上不可见的身份标记。 */
    public static void renameIcon(ItemStack stack, String marker, Component name) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(ICON_MARKER_KEY, marker));
        stack.set(DataComponents.CUSTOM_NAME, name);
    }

    private static String iconMarker(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(ICON_MARKER_KEY);
    }

    // ===== 准星射线 =====

    /**
     * 沿视线找一个生物碰撞箱：把每个候选生物的包围盒外扩一点后与视线线段求交，取最近的。
     * 没有直接用 {@code ProjectileUtil} 是因为它面向弹射物、语义与「准星指向」并不一致。
     */
    @Nullable
    public static LivingEntity raycastLivingEntity(Player player, double reach) {
        Vec3 from = player.getEyePosition(1.0F);
        Vec3 to = from.add(player.getViewVector(1.0F).scale(reach));
        AABB search = player.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0);

        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class, search,
                e -> e != player && e.isAlive() && e.isPickable() && !e.isSpectator())) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(0.3).clip(from, to);
            if (hit.isEmpty()) continue;
            double distance = from.distanceToSqr(hit.get());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** 沿视线找方块，未命中返回 {@code null}。 */
    @Nullable
    public static BlockHitResult raycastBlock(Player player, double reach) {
        Vec3 from = player.getEyePosition(1.0F);
        Vec3 to = from.add(player.getViewVector(1.0F).scale(reach));
        BlockHitResult hit = player.level().clip(
            new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /**
     * 右键召唤的落点，射线长度 {@link #DARK_CLONE_REACH}（256 格，不受交互距离限制）：
     * 对准实体 → 该实体位置；对准方块 → 与方块相交处；两者都命中取更近的；
     * 都没命中（例如对准天空）返回 {@code null} 表示不召唤。
     */
    @Nullable
    public static Vec3 summonPosition(Player player) {
        LivingEntity aimedEntity = raycastLivingEntity(player, DARK_CLONE_REACH);
        BlockHitResult aimedBlock = raycastBlock(player, DARK_CLONE_REACH);
        if (aimedEntity == null && aimedBlock == null) return null;
        if (aimedEntity == null) return aimedBlock.getLocation();
        if (aimedBlock == null) return aimedEntity.position();
        Vec3 eye = player.getEyePosition(1.0F);
        return eye.distanceToSqr(aimedEntity.position()) <= eye.distanceToSqr(aimedBlock.getLocation())
            ? aimedEntity.position()
            : aimedBlock.getLocation();
    }

    // ===== 召唤池与随机播放列表 =====

    /** 把一个生物类型记录进该玩家的召唤池；已在池中则什么都不做。 */
    public static void rememberCreature(ServerLevel level, Player player, EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) return;
        List<ResourceLocation> pool = DarkCloneSavedData.get(level).pool(player);
        if (pool.contains(id)) return;
        pool.add(id);
        DarkCloneSavedData.get(level).setDirty();
    }

    /**
     * 从池中抽取一项：像音乐的随机播放列表一样，先把整个池子随机洗一遍依次抽完，
     * 抽空后才重新洗出另一份遍历顺序。
     */
    @Nullable
    public static ResourceLocation drawFromPool(ServerLevel level, Player player) {
        DarkCloneSavedData data = DarkCloneSavedData.get(level);
        List<ResourceLocation> pool = data.pool(player);
        if (pool.isEmpty()) return null;

        List<ResourceLocation> bag = data.bag(player);
        if (bag.isEmpty()) {
            bag.addAll(pool);
            shuffle(bag, level.getRandom());
        }
        ResourceLocation picked = bag.remove(0);
        data.setDirty();
        return picked;
    }

    /** Fisher–Yates。刻意手写：{@code Collections.shuffle} 要的是 {@code java.util.Random}，而这里只有 {@link RandomSource}。 */
    private static void shuffle(List<ResourceLocation> list, RandomSource random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(list, i, j);
        }
    }

    /**
     * 右键召唤：从召唤池随机抽一种生物，在准星命中处召唤其黑暗分身。
     *
     * <p>先算落点再抽池子——落点无效（对准天空）时不该白白消耗一次抽取。
     * 召唤没有上限也没有代价；召唤出来的分身归属于召唤者（因此不会攻击召唤者，
     * 且会在召唤者参战时优先响应）。
     */
    public static boolean trySummonFromPool(ServerLevel level, ServerPlayer player) {
        if (!isDarkCloneStance(player)) return false;

        Vec3 position = summonPosition(player);
        if (position == null) return false;

        ResourceLocation typeId = drawFromPool(level, player);
        if (typeId == null) return false;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(typeId);
        if (type == null) return false;

        return spawnDarkClone(level, type, position, player.getYRot(), player) != null;
    }

    // ===== 克隆 =====

    /**
     * 由「生物类型」召唤黑暗分身（召唤池走这条路）。
     *
     * <p>与 {@link #spawnDarkClone(ServerLevel, LivingEntity, LivingEntity)} 的区别：这里没有原主可复制，
     * 因此会走一次 {@code finalizeSpawn}，让村民拿到职业、史莱姆拿到体型等，
     * 得到一个「该物种的、初始化完整的新个体」——<b>而不是</b>被扫描那只的精确复制
     * （包括颜色、幼年、装备都不会被带过来）。这是本功能最大的一处取舍。
     */
    @Nullable
    public static LivingEntity spawnDarkClone(ServerLevel level, EntityType<?> type, Vec3 position, float yRot,
            @Nullable LivingEntity owner) {
        Entity created = type.create(level);
        if (!(created instanceof LivingEntity clone)) {
            if (created != null) created.discard();
            return null;
        }

        clone.moveTo(position.x, position.y, position.z, yRot, 0.0F);
        clone.setYHeadRot(yRot);
        if (clone instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(position)),
                MobSpawnType.COMMAND, null);
            mob.setPersistenceRequired();
        }
        clone.setHealth(clone.getMaxHealth());

        finishCloneSetup(clone, owner);
        level.addFreshEntity(clone);
        return clone;
    }

    /**
     * 由「一只具体的生物」召唤黑暗分身：复制它的 NBT，得到它的精确复制。
     *
     * <p>注意几个刻意的取舍：
     * <ul>
     *   <li>不走 {@code finalizeSpawn}——分身要的是原主的精确复制，而不是一次新的随机生成；</li>
     *   <li>生命值取满血而不是照抄原主当前血量——分身的语义是「复制出一个新的个体」，
     *       否则对一个残血生物使用会得到一个残血分身；若要改成精确照抄，删掉那一行 {@code setHealth} 即可；</li>
     *   <li>{@code NeoForgeData}（持久化数据）保留，其它 mod 挂在生物上的自定义数据因此得以保留；
     *       我们自己的归属 / 仇恨队列则在 {@code load} 之后重新写入，避免从「原主本身也是分身」的情况继承错误归属。</li>
     * </ul>
     */
    @Nullable
    public static LivingEntity spawnDarkClone(ServerLevel level, LivingEntity source, @Nullable LivingEntity owner) {
        EntityType<?> type = source.getType();
        Entity created = type.create(level);
        if (!(created instanceof LivingEntity clone)) {
            if (created != null) created.discard();
            return null;
        }

        CompoundTag tag = source.saveWithoutId(new CompoundTag());
        for (String key : STRIPPED_KEYS) {
            tag.remove(key);
        }
        clone.load(tag);

        clone.moveTo(source.getX(), source.getY(), source.getZ(), source.getYRot(), source.getXRot());
        clone.setYHeadRot(source.getYHeadRot());
        clone.setDeltaMovement(Vec3.ZERO);
        clone.setHealth(clone.getMaxHealth());
        if (clone instanceof Mob mob) {
            mob.setPersistenceRequired();
        }

        finishCloneSetup(clone, owner);
        level.addFreshEntity(clone);
        return clone;
    }

    /** 两条召唤路径共用的收尾：归属、标记、接管索敌。 */
    private static void finishCloneSetup(LivingEntity clone, @Nullable LivingEntity owner) {
        clearPriorityTargets(clone);
        setOwner(clone, owner);
        DarkCloneAttachments.setDarkClone(clone, true);
        installTargetGoal(clone);
    }

    /**
     * 左键抹除黑暗分身时的一团黑色粒子。
     * 用黑色尘粒（dust）而不是原版烟雾：烟雾是灰白的，不成「黑色粒子」。
     */
    public static void spawnVanishParticles(ServerLevel level, LivingEntity target) {
        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.5;
        double z = target.getZ();
        double spread = Math.max(0.4, target.getBbWidth());
        level.sendParticles(new DustParticleOptions(new Vector3f(0.0F, 0.0F, 0.0F), 1.4F),
            x, y, z, 40, spread, target.getBbHeight() * 0.5, spread, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
            x, y, z, 12, spread * 0.5, target.getBbHeight() * 0.3, spread * 0.5, 0.01);
    }

    /**
     * 接管黑暗分身的索敌：清掉原版注册的全部选目标 goal，换成我们的通用索敌 goal。
     *
     * <p>必须清掉原版 goal，否则「索敌一切生物」这条规则无法成立——例如僵尸的原版
     * {@code NearestAttackableTargetGoal} 每 10 tick 都会把目标改回玩家。
     * 这只动 {@code targetSelector}，{@code goalSelector}（追击、近战、远程等行为）保持原样，
     * 所以分身仍然完全按原生物的方式移动与攻击。
     *
     * <p>本方法是幂等的：读档与区块重载都会再次调用它，因为 goal 不随存档保存。
     */
    public static void installTargetGoal(LivingEntity clone) {
        if (!(clone instanceof Mob mob)) return;
        if (!DarkCloneAttachments.isDarkClone(clone)) return;
        mob.targetSelector.removeAllGoals(goal -> true);
        mob.targetSelector.addGoal(0, new DarkCloneTargetGoal(mob));
    }

    // ===== 归属 =====

    public static void setOwner(LivingEntity clone, @Nullable LivingEntity owner) {
        if (owner == null) {
            clone.getPersistentData().remove(OWNER_KEY);
        } else {
            clone.getPersistentData().putString(OWNER_KEY, owner.getUUID().toString());
        }
    }

    @Nullable
    public static UUID getOwnerUUID(LivingEntity clone) {
        String raw = clone.getPersistentData().getString(OWNER_KEY);
        if (raw.isEmpty()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isOwner(LivingEntity clone, Entity candidate) {
        UUID owner = getOwnerUUID(clone);
        return owner != null && owner.equals(candidate.getUUID());
    }

    // ===== 能力判定 =====

    /** 是否具备攻击力：拥有 ATTACK_DAMAGE 属性且数值大于 0（羊这类被动生物没有该属性）。 */
    public static boolean hasAttackCapability(LivingEntity entity) {
        return entity.getAttribute(Attributes.ATTACK_DAMAGE) != null
            && entity.getAttributeValue(Attributes.ATTACK_DAMAGE) > 0.0;
    }

    /** 是否具备飞行能力：原版飞行生物统一使用 {@link FlyingPathNavigation}（蜜蜂、鹦鹉、蝙蝠、恶魂、幻翼、悦灵、恼鬼）。 */
    public static boolean canFly(LivingEntity entity) {
        return entity instanceof Mob mob && mob.getNavigation() instanceof FlyingPathNavigation;
    }

    /**
     * 是否可以索敌该目标。规则：
     * <ul>
     *   <li>不索敌自己；</li>
     *   <li>{@link #shouldNotAttack} 涵盖的一律不索敌（Owner、创造 / 旁观玩家、友方黑暗分身）；</li>
     *   <li>恶魂与末影龙只有分身自己会飞时才会被索敌（地面生物对它们无从下手）。</li>
     * </ul>
     *
     * <p><b>注意</b>：本方法只服务于 {@code DarkCloneTargetGoal}（targetSelector 那条管线）。
     * 监守者走脑记忆、凋灵侧头走同步数据槽，都不经过 goal，
     * 所以它们必须各自调用 {@link #shouldNotAttack}——这条曾经漏掉，导致「不索敌 Owner」对它们失效。
     */
    public static boolean canTarget(LivingEntity clone, @Nullable LivingEntity target) {
        if (target == null || target == clone) return false;
        if (!target.isAlive() || target.isRemoved()) return false;
        if (shouldNotAttack(clone, target)) return false;
        if (isFlyingPrey(target) && !canFly(clone)) return false;
        return true;
    }

    /**
     * 黑暗分身是否「不该攻击」该目标。<b>所有收口共用的唯一判定</b>——
     * 选目标（{@link #canTarget}）、目标变更收口（{@code LivingChangeTargetEvent}）、
     * 伤害收口（{@code LivingIncomingDamageEvent}）、
     * 以及监守者与凋灵这两处不经过上述任何事件的专用收口，全都调它，
     * 避免规则在多处各写一遍而走偏（「不索敌 Owner」就是因为只写在选目标那一处才漏掉的）。
     *
     * <p>成立条件（满足其一）：
     * <ol>
     *   <li><b>是自己的 Owner</b>——需求原文「也会有 Owner 标签，不会索敌 Owner」；</li>
     *   <li><b>创造 / 旁观模式玩家</b>；</li>
     *   <li><b>友方黑暗分身</b>（见 {@link #isFriendlyPair}）。</li>
     * </ol>
     *
     * <p>刻意<b>不</b>把「目标是自己」算进来：那样会连带屏蔽分身对自己造成的伤害
     * （例如苦力怕分身自爆），所以自身由 {@link #canTarget} 单独排除，这里不碰。
     *
     * <p>非黑暗分身调用时一律返回 {@code false}，普通生物保持原版行为不变。
     */
    public static boolean shouldNotAttack(LivingEntity clone, @Nullable LivingEntity target) {
        if (target == null) return true;
        if (!DarkCloneAttachments.isDarkClone(clone)) return false;
        if (isOwner(clone, target)) return true;
        if (isNonCombatant(target)) return true;
        return isFriendlyPair(clone, target);
    }

    /**
     * 两个黑暗分身之间是否「不应互相攻击」。
     *
     * <p><b>这条规则是所有收口共用的唯一判定</b>：选目标（{@link #canTarget}）、
     * 目标变更收口（{@code LivingChangeTargetEvent}）、伤害收口（{@code LivingIncomingDamageEvent}）
     * 都调它，避免三处规则各写一遍而走偏。
     *
     * <p>成立条件（满足其一）：
     * <ol>
     *   <li><b>其中任一方是监守者的黑暗分身</b>——镜像原版 {@code Warden#canTargetEntity}
     *       里的 {@code livingentity.getType() != EntityType.WARDEN}：
     *       监守者把「同类」排除在目标之外，那么监守者的黑暗分身就把黑暗分身当同类。
     *       这里做成<b>双向</b>的：既然监守者分身不该打别人，别人也不该打它；
     *       「铁傀儡打监守者、监守者还击」这个案例里两个方向都归这条管。</li>
     *   <li><b>同一个 Owner</b>（Owner 都为空也算同一方）——无主的分身都是「野生的」，
     *       让它们互相残杀没有意义。</li>
     * </ol>
     * 两个 Owner 不同的黑暗分身（且都不涉及监守者）仍然可以互相攻击。
     */
    public static boolean isFriendlyPair(LivingEntity first, LivingEntity second) {
        if (first == second) return false;
        if (!DarkCloneAttachments.isDarkClone(first) || !DarkCloneAttachments.isDarkClone(second)) return false;
        if (refusesDarkClonesAsPrey(first) || refusesDarkClonesAsPrey(second)) return true;
        return Objects.equals(getOwnerUUID(first), getOwnerUUID(second));
    }

    /**
     * 镜像原版 {@code Warden#canTargetEntity} 里「不攻击同类」那一条
     * （{@code livingentity.getType() != EntityType.WARDEN}，见 Warden.java:398）。
     *
     * <p>对监守者的黑暗分身来说，它的「同类」就是黑暗分身，因此它不索敌任何黑暗分身。
     * 监守者原本还排除创造 / 旁观玩家（{@code EntitySelector.NO_CREATIVE_OR_SPECTATOR}），
     * 那一条已由 {@link #isNonCombatant} 对所有分身统一生效。
     *
     * <p>注意监守者的选目标完全走<b>脑记忆</b>（{@code MemoryModuleType.ATTACK_TARGET}），
     * 不经过 {@code Mob#setTarget}，所以事件收口拦不住它——它还需要
     * {@code WardenDarkCloneMixin} 从 {@code canTargetEntity} 这一源头挡住。
     *
     * <p>以后若要让别的「不攻击同类」的生物也享受同类待遇，往这里追加判断即可。
     */
    private static boolean refusesDarkClonesAsPrey(LivingEntity clone) {
        return clone.getType() == EntityType.WARDEN;
    }

    /** 创造 / 旁观模式玩家：既不作为仇恨来源，也不被索敌。 */
    public static boolean isNonCombatant(LivingEntity entity) {
        return entity instanceof Player player && (player.isCreative() || player.isSpectator());
    }

    private static boolean isFlyingPrey(LivingEntity entity) {
        return entity instanceof Ghast || entity instanceof EnderDragon;
    }

    // ===== 选目标 =====

    /**
     * 选一个目标：Owner 的仇恨队列优先，队列为空或全部失效时退化为「范围内最近的合法生物」。
     * 队列中的失效条目会被顺带清理。
     */
    @Nullable
    public static LivingEntity pickTarget(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) return null;

        UUID priority = peekPriorityTarget(mob);
        while (priority != null) {
            Entity found = level.getEntity(priority);
            if (found instanceof LivingEntity living && canTarget(mob, living)) {
                return living;
            }
            popPriorityTarget(mob);
            priority = peekPriorityTarget(mob);
        }

        double followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        double range = Math.min(MAX_TARGET_RANGE, Math.max(MIN_TARGET_RANGE, followRange));
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
            mob.getBoundingBox().inflate(range), candidate -> canTarget(mob, candidate));

        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double distance = mob.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    // ===== Owner 仇恨队列（按顺序索敌） =====

    /** 把目标排到队首；已在队列中的目标会被提到队首而不是重复入队。 */
    public static void pushPriorityTarget(LivingEntity clone, LivingEntity target) {
        String id = target.getUUID().toString();
        ListTag existing = clone.getPersistentData().getList(PRIORITY_KEY, Tag.TAG_STRING);
        ListTag updated = new ListTag();
        updated.add(StringTag.valueOf(id));
        for (int i = 0; i < existing.size() && updated.size() < MAX_PRIORITY_TARGETS; i++) {
            String entry = existing.getString(i);
            if (!entry.equals(id)) {
                updated.add(StringTag.valueOf(entry));
            }
        }
        clone.getPersistentData().put(PRIORITY_KEY, updated);
    }

    @Nullable
    public static UUID peekPriorityTarget(LivingEntity clone) {
        ListTag list = clone.getPersistentData().getList(PRIORITY_KEY, Tag.TAG_STRING);
        if (list.isEmpty()) return null;
        try {
            return UUID.fromString(list.getString(0));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void popPriorityTarget(LivingEntity clone) {
        ListTag list = clone.getPersistentData().getList(PRIORITY_KEY, Tag.TAG_STRING);
        if (list.isEmpty()) {
            clone.getPersistentData().remove(PRIORITY_KEY);
            return;
        }
        ListTag updated = new ListTag();
        for (int i = 1; i < list.size(); i++) {
            updated.add(StringTag.valueOf(list.getString(i)));
        }
        if (updated.isEmpty()) {
            clone.getPersistentData().remove(PRIORITY_KEY);
        } else {
            clone.getPersistentData().put(PRIORITY_KEY, updated);
        }
    }

    public static void clearPriorityTargets(LivingEntity clone) {
        clone.getPersistentData().remove(PRIORITY_KEY);
    }

    // ===== 常量 =====

    /** 仇恨队列上限，避免长期战斗后无限增长。 */
    private static final int MAX_PRIORITY_TARGETS = 8;
    /** 自然索敌的最小 / 最大半径（会与生物自身的 FOLLOW_RANGE 取交集）。 */
    private static final double MIN_TARGET_RANGE = 16.0;
    private static final double MAX_TARGET_RANGE = 64.0;
    /** Owner 参战时报信给分身的搜索半径。 */
    public static final double OWNER_RESPONSE_RANGE = 64.0;
    /**
     * 记录与召唤的最大距离。<b>刻意远大于交互距离</b>（原版约 3 格 / 创造 5 格）：
     * 需求要求这两个操作「不受交互距离限制，最远 256 格」。
     * 记录能这么做的前提是左键走网络包（原版只在交互距离内才发实体交互包）。
     */
    public static final double DARK_CLONE_REACH = 256.0;

    /**
     * 克隆时从原主 NBT 中剔除的字段：分身应当继承「它是什么」，而不该继承「它此刻处于什么状态」，
     * 更不该继承原主的身份（UUID）与社交关系（拴绳、乘客、记分板标签、对原主的仇恨）。
     * 位置 / 朝向由调用方显式设置。
     */
    private static final String[] STRIPPED_KEYS = {
        "UUID",
        "Pos", "Motion", "Rotation",
        "FallDistance", "Fire", "Air", "OnGround",
        "Invulnerable", "PortalCooldown",
        "HurtTime", "HurtByTimestamp", "HurtBy", "DeathTime",
        "AbsorptionAmount",
        "Leash", "Passengers", "Tags",
        "AngryAt", "AngerTime"
    };

    private DarkCloneHelper() {
    }
}
