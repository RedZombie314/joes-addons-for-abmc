package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.potion.ModMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家空壳：仅使用玩家模型渲染的实体，自带一个字符串实体数据 SkinTexture。
 * - 无任何 AI（不注册任何目标/移动目标），也不会自己移动。
 * - 默认 20 点生命值，可被击杀；由变形药水创造时，被杀死会连带杀死原生物。
 * - 皮肤由 SkinTexture 决定（客户端渲染时从网上拉取对应玩家皮肤）。
 * <p>
 * <b>"由幸运核心支撑"的那几具</b>（附体主空壳、被同化的、刷怪蛋放出来的）会额外被
 * {@link #configureAsPossessedShell()} / {@link #applyCoreBackedStats()} 配置成核心的"体外生命"：
 * <b>生命上限 = 被附体/同化前那位玩家的最大生命值</b>（没有来源玩家时退回
 * {@link #CORE_BACKED_MAX_HEALTH} = 20）+ <b>自带 20 护甲、12 护甲韧性</b> +
 * <b>自带保护 16 状态效果</b>，
 * 并且是<b>唯一</b>承伤方 —— 核心本体在附体阶段没有碰撞箱、也不再承伤（见
 * {@link OrbOfLuckEntity#isInvulnerableTo}）。空壳血量归零 = 这次附体被击败。
 * <p>
 * 另外它还会<b>吃附魔金苹果</b>：生成时先吃一个（不论有没有索敌目标），之后血量低于
 * {@link #appleHealThreshold()}（<b>生命上限的 50%</b>）就再吃一个，两次之间至少隔
 * {@link #APPLE_COOLDOWN_TICKS} 刻
 * （两分钟）。食用走原版流程（时长减半：{@link #APPLE_EAT_SECONDS} 秒），
 * 所以照样有咀嚼音效和附魔金苹果那四个效果；食物碎屑粒子由本类自己发
 * （原版的粒子调用在服务端是空实现，见 {@link #spawnEatParticles}）。
 * <p>
 * 它<b>没有</b>被动回血：原本那条"每 10 游戏刻回 2 点"（照搬核心的自回血）已经移除，
 * 受伤后只能靠上面这口苹果恢复（核心本体那条自回血保持不变）。
 */
public class PlayerShellEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> DATA_SKIN_TEXTURE =
        SynchedEntityData.defineId(PlayerShellEntity.class, EntityDataSerializers.STRING);
    private static final String TAG_SKIN = "SkinTexture";
    private static final String TAG_ORIGIN_NBT = "TransmutationOrigin";
    private static final String TAG_ORIGIN_PLAYER = "OriginPlayerUuid";
    private static final String TAG_ORIGIN_KILLER = "OriginKillerUuid";
    private static final String TAG_REMAINING_TICKS = "RemainingTicks";
    private static final String TAG_ORB_ATTACHED = "OrbAttached";
    private static final String TAG_POSSESSED_ORB = "PossessedOrb";

    /**
     * 是否在头顶渲染一颗幸运核心光球（同步字段）。
     * <p>
     * 供"附体"形态使用：{@link OrbOfLuckEntity} 在 POSSESSING 阶段生成一具本实体当外观，
     * 自己则不渲染光球形态。渲染见 {@link PlayerShellRenderer}。
     */
    private static final EntityDataAccessor<Boolean> DATA_ORB_ATTACHED =
        SynchedEntityData.defineId(PlayerShellEntity.class, EntityDataSerializers.BOOLEAN);

    /** 附体来源的核心实体 UUID（仅服务端）。 */
    private UUID possessedOrbUuid;

    /**
     * 附体形态的 <b>boss 血条</b>（仅服务端持有；由原版 HUD 渲染）。
     * <p>
     * 用户要求"为被幸运核心附体的玩家空壳加一个血条"，分两段：
     * <b>①本体血量（紫色、居中）</b> + <b>②伤害吸收那段（黄色、接在①右侧）</b>。
     * ① 直接就是原版 boss 条（颜色用女巫三阶段那条紫），② 是客户端在本条右侧补画的一段
     * （见 {@code client.ShellBossBarClient}）。
     */
    @Nullable
    private ServerBossEvent shellBossBar;

    /**
     * 血条 UUID 的字符串形式（同步字段，空字符串 = 没有血条）。
     * <p>
     * 客户端只能拿到"一条 boss 血条"，不知道它对应哪具空壳；靠这个字段把两者对上，
     * 才能读到这具空壳的伤害吸收量去画②。同步成字符串是因为原版
     * {@code EntityDataSerializers} 里没有现成的 UUID 类型。
     */
    private static final EntityDataAccessor<String> DATA_SHELL_BAR_ID =
        SynchedEntityData.defineId(PlayerShellEntity.class, EntityDataSerializers.STRING);

    /**
     * <b>伤害吸收（黄心）点数</b>（同步字段）。
     * <p>
     * 原版 {@code LivingEntity#getAbsorptionAmount()} 只在服务端算、<b>不跨端同步</b>
     * （客户端只会给"自己"画黄心），而血条②要画在别的实体身上，所以这里自己同步一份。
     * 数值就是"黄心数 × 2"。
     */
    private static final EntityDataAccessor<Float> DATA_ABSORPTION =
        SynchedEntityData.defineId(PlayerShellEntity.class, EntityDataSerializers.FLOAT);

    // 由变形药水创造时记录的原生物信息（仅服务端使用，随实体 NBT 持久化）
    private CompoundTag originNbt;
    private UUID originPlayerUuid;
    private UUID originKillerUuid;
    // 剩余变形时长（tick），用于倒计时结束时变回原生物；-1 表示非变形来源（如 /summon）
    private int remainingTicks = -1;

    public PlayerShellEntity(EntityType<? extends PlayerShellEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /** 玩家空壳默认可被正常拾取/攻击：近战、弹射物均可命中，左键可命中本体（不再穿透到身后方块）。
     *  仅"玩家变形跟随壳"（服务端 jafa_transmutation_follow 标记）或客户端本地玩家自己的跟随壳保持穿透，
     *  避免阻挡变形中的玩家互动。 */
    @Override
    public boolean isPickable() {
        // 服务端：玩家变形产生的跟随壳保持右键穿透（该标记不同步到客户端）
        if (this.getPersistentData().getBoolean("jafa_transmutation_follow")) {
            return false;
        }
        // 客户端：本地玩家自己的跟随壳同样穿透（标记不同步到客户端，改用跟随实体 ID 判断）
        if (this.level() != null && this.level().isClientSide()
                && cn.autoforged.joes_addons_for_abmc.client.TransmutationCameraClient.getFollowEntityId() == this.getId()) {
            return false;
        }
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN_TEXTURE, "");
        builder.define(DATA_ORB_ATTACHED, false);
        builder.define(DATA_SHELL_BAR_ID, "");
        builder.define(DATA_ABSORPTION, 0.0F);
    }

    /** 这具空壳的 boss 血条 UUID（同步给客户端的字符串形式）；没有血条返回空串。 */
    public String getShellBarId() {
        return this.entityData.get(DATA_SHELL_BAR_ID);
    }

    /** 画血条②用的伤害吸收点数（黄心数 × 2；见 {@link #DATA_ABSORPTION}）。 */
    public float getVisualAbsorption() {
        return this.entityData.get(DATA_ABSORPTION);
    }

    // ====================== 碰撞箱：与真人玩家完全一致（用户指定） ======================

    /**
     * <b>载具挂点：与真人玩家同一处</b>（{@code Player.DEFAULT_VEHICLE_ATTACHMENT} = {@code (0, 0.6, 0)}）。
     *
     * <h2>这个点为什么必须显式登记（用户实测：空壳上船后比真人高一大截）</h2>
     * 原版让乘客坐上去的是 {@code Entity#positionRider}：
     * <pre>
     *   Vec3 seat = 载具的 PASSENGER 挂点;
     *   Vec3 mine = 乘客自己的 VEHICLE 挂点;      // ← 这里
     *   callback.accept(passenger, seat.x - mine.x, seat.y - mine.y, seat.z - mine.z);
     * </pre>
     * 真人这个点是 {@code (0, 0.6, 0)} → 坐上去时被<b>往下挪 0.6 格</b>正好坐在船上；
     * 而 {@code EntityAttachment.VEHICLE} 的兜底值是 {@code AT_FEET = (0, 0, 0)} ——
     * 没登记就是"一点都不下移"，于是空壳整具悬在座位点上方 <b>0.6 格</b>，看起来就是"高出快一格"。
     * <p>
     * <b>光写在 {@link #getDefaultDimensions} 里不够</b>：那个返回值只在 {@code refreshDimensions()}
     * 时才进缓存，而坐船靠的是实体类型自带的 {@code dimensions}（客户端也一样，船的 {@code rideTick}
     * 会在客户端重新摆一次乘客的位置）—— 所以必须同时用
     * {@code EntityType.Builder#vehicleAttachment(...)} 登记到<b>实体类型</b>上（见 {@code ModEntities}），
     * 和原版 {@code EntityType.PLAYER} 的写法一致。
     */
    public static final Vec3 PLAYER_VEHICLE_ATTACHMENT = new Vec3(0.0D, 0.6D, 0.0D);

    /**
     * <b>站立碰撞箱：与真人玩家一模一样</b>（用户指定："使它的碰撞箱和真人玩家保持一致"）。
     * <p>
     * 数值逐条照抄 {@code Player#STANDING_DIMENSIONS}：<b>0.6 × 1.8、眼高 1.62</b>
     * （以前走的是 {@code EntityType.Builder} 的默认视高 —— 身高 × 0.85 = <b>1.53</b>，
     * 于是"从眼睛打出去"的射线、索敌视线、载具挂点都比真人低 9 厘米），
     * 载具挂点也取玩家那一处 {@link #PLAYER_VEHICLE_ATTACHMENT}。
     */
    public static final EntityDimensions STANDING_DIMENSIONS = EntityDimensions.scalable(0.6F, 1.8F)
        .withEyeHeight(1.62F)
        .withAttachments(EntityAttachments.builder()
            .attach(EntityAttachment.VEHICLE, PLAYER_VEHICLE_ATTACHMENT));

    /** 潜行：0.6 × 1.5、眼高 1.27（同玩家）。 */
    private static final EntityDimensions CROUCHING_DIMENSIONS = EntityDimensions.scalable(0.6F, 1.5F)
        .withEyeHeight(1.27F)
        .withAttachments(EntityAttachments.builder()
            .attach(EntityAttachment.VEHICLE, PLAYER_VEHICLE_ATTACHMENT));

    /**
     * 滑翔 / 游泳 / 旋转攻击：<b>0.6 × 0.6、眼高 0.4</b>（同玩家）。
     * <p>重锤的爬升/俯冲会把姿势切成 {@link Pose#FALL_FLYING}（见 {@link #tick()}），真人这时就是
     * 这么一个"趴平的小箱子"，以前空壳却还是 1.8 高的立姿箱子。
     */
    private static final EntityDimensions SWIMMING_DIMENSIONS = EntityDimensions.scalable(0.6F, 0.6F)
        .withEyeHeight(0.4F);

    /** 濒死：0.2 × 0.2、眼高 1.62（同玩家）。 */
    private static final EntityDimensions DYING_DIMENSIONS = EntityDimensions.fixed(0.2F, 0.2F)
        .withEyeHeight(1.62F);

    /**
     * <b>按姿势决定碰撞箱：与真人玩家的 {@code Player#POSES} 表逐条对应</b>（用户指定）。
     * <p>
     * 原来这里走的是 {@code LivingEntity} 的默认实现 —— 不管什么姿势都返回实体类型那一个箱子
     * （0.6 × 1.8、眼高 1.53），于是"滑翔时趴平"只改了外观，碰撞箱还是站着的那个。
     * <p>
     * <b>姿势一变必须自己 {@link #refreshDimensions()}</b>：原版 {@code Entity#setPose} 只写同步字段、
     * 不会重算碰撞箱（玩家靠 {@code Player#updatePlayerPose} 里那一次 {@code refreshDimensions} 兜住）。
     * 本类的切姿势点只有 {@link #tick()} 里"滑翔 ↔ 站立"那两处，已经补上了。
     */
    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return switch (pose) {
            case SLEEPING -> SLEEPING_DIMENSIONS;
            case FALL_FLYING, SWIMMING, SPIN_ATTACK -> SWIMMING_DIMENSIONS;
            case CROUCHING -> CROUCHING_DIMENSIONS;
            case DYING -> DYING_DIMENSIONS;
            default -> STANDING_DIMENSIONS;
        };
    }

    /**
     * <b>视锥剔除盒比碰撞箱大一圈</b>：附体形态头顶那颗幸运核心光球的公告板（半径 0.3 格、
     * 面片半边长约 0.62 格）会伸出碰撞箱（1.8 高）之外约 0.7 格 —— 只按碰撞箱剔除的话，
     * 空壳贴着屏幕边缘时整团光会被判成"在视野外"而闪烁消失。
     * <p>核心本体那颗球有同样的处理（见 {@code OrbOfLuckEntity#getBoundingBoxForCulling}），
     * 这里是对着"头顶那颗"补一遍。剔除盒只用于视锥判断，不影响碰撞/判定。
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(0.75D);
    }

    /** 头顶是否挂一颗幸运核心光球（附体形态用）。 */
    public void setOrbAttached(boolean value) {
        this.entityData.set(DATA_ORB_ATTACHED, value);
    }

    public boolean isOrbAttached() {
        return this.entityData.get(DATA_ORB_ATTACHED);
    }

    /**
     * 记录"这具身体属于哪颗核心"。
     * <p>
     * 用途：索敌上报（{@link #notifyPossessedOrbOfTarget}）、被击败时找核心收场
     * （{@link #die}）、以及同化归属（{@code OrbAssimilation} 靠它认出"伤害是附体体系打的"）。
     * <p>
     * <b>它不再是"伤害转发标记"</b>：承伤方就是这具空壳自己
     * （曾经的实现是把伤害全转给核心的 100 点血池，现已改掉 —— 见 {@link #hurt}）。
     */
    public void setPossessedOrb(UUID orbUuid) {
        this.possessedOrbUuid = orbUuid;
    }

    public UUID getPossessedOrb() {
        return this.possessedOrbUuid;
    }

    /**
     * 这具空壳是不是"附体主空壳"—— 也就是幸运核心附体<b>真玩家</b>时产生的那具 boss 身体
     * （核心骑在它身上、玩家在旁边旁观）。
     * <p>
     * 与由变形药水造出来的临时空壳、以及"被同化"的空壳区分开：
     * 后两者身上没有 {@code possessedOrbUuid}（见 {@link OrbAssimilation}）。
     */
    public boolean isPossessionVessel() {
        return this.possessedOrbUuid != null;
    }

    /**
     * 壳体从世界里消失时触发：附体主空壳（以及被同化的空壳）一旦真的没了，
     * 它名下的一切立刻清场 —— 召唤物本体，以及召唤物被变形之后留下的产物
     * （物品/方块就地销毁、无掉落；见 {@link ModMain#destroyMorphedSummonProducts}）。
     *
     * <h2>三种情况不算"消失"，必须原样放过</h2>
     * <ul>
     *   <li><b>变形</b>（{@link ModMain#consumeMorphing}）：壳体只是换成了物品/方块/生物壳，
     *       马上还要变回来，召唤物得原样留着；</li>
     *   <li><b>区块卸载</b>（{@code UNLOADED_TO_CHUNK} / {@code UNLOADED_WITH_PLAYER}）：
     *       那是"没加载"，不是"没了"；</li>
     *   <li><b>换维度</b>（{@code CHANGED_DIMENSION}）：实体被搬到另一个维度，还是同一具。</li>
     * </ul>
     * 只判 {@code reason.shouldDestroy()} 的那两种（KILLED / DISCARDED）正好把上面三种排除掉。
     * <p>
     * 召唤物自己要是正待在没加载的区块里，这里够不着它 —— 那种情况由
     * {@code OrbPossessionSummons#allyVanished} 记住"主人没了"，
     * 等它随区块加载时（{@code ModMain#onEntityJoinLevel}）再抹掉。
     */
    @Override
    public void remove(Entity.RemovalReason reason) {
        // 血条：这具空壳要没了（被打死、被清场、变形换形态……）就先把它收掉 ——
        // 客户端那边是靠"移除包"把血条抹掉的，漏掉这一步屏幕上会冻结一条血条
        this.hideShellBossBar();
        // 重锤动作中途没了（被打死/被清掉）：把自己放的那桶水收掉，别在地上留个水坑
        if (reason.shouldDestroy() && this.possessionMaceState != null) {
            OrbPossessionMaceAttack.abort(this);
        }
        UUID ownerOrb = this.possessedOrbUuid != null
            ? this.possessedOrbUuid
            : OrbAssimilation.orbOfShell(this);
        if (ownerOrb != null && reason.shouldDestroy()
            && !ModMain.consumeMorphing(this)
            && this.level().getServer() != null) {
            ModMain.onPossessionShellGone(this.level().getServer(), this, ownerOrb);
        }
        super.remove(reason);
    }

    public void setSkinTexture(String value) {
        this.entityData.set(DATA_SKIN_TEXTURE, value == null ? "" : value);
    }

    public String getSkinTexture() {
        return this.entityData.get(DATA_SKIN_TEXTURE);
    }

    /** 记录变形初始信息：原生物 NBT、玩家（若原生物是玩家）、击杀责任玩家。 */
    public void setTransmutationOrigin(CompoundTag nbt, UUID playerUuid, UUID killerUuid) {
        this.originNbt = nbt;
        this.originPlayerUuid = playerUuid;
        this.originKillerUuid = killerUuid;
    }

    public CompoundTag getOriginNbt() {
        return originNbt;
    }

    public UUID getOriginPlayerUuid() {
        return originPlayerUuid;
    }

    public UUID getOriginKillerUuid() {
        return originKillerUuid;
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    public void setRemainingTicks(int remainingTicks) {
        this.remainingTicks = remainingTicks;
    }

    @Override
    public Component getDisplayName() {
        String skin = getSkinTexture();
        if (skin != null && !skin.isBlank()) {
            return Component.literal(skin);
        }
        return super.getDisplayName();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_SKIN, getSkinTexture());
        if (originNbt != null) {
            tag.put(TAG_ORIGIN_NBT, originNbt);
        }
        if (originPlayerUuid != null) {
            tag.putUUID(TAG_ORIGIN_PLAYER, originPlayerUuid);
        }
        if (originKillerUuid != null) {
            tag.putUUID(TAG_ORIGIN_KILLER, originKillerUuid);
        }
        if (remainingTicks >= 0) {
            tag.putInt(TAG_REMAINING_TICKS, remainingTicks);
        }
        tag.putBoolean(TAG_ORB_ATTACHED, this.isOrbAttached());
        if (this.possessedOrbUuid != null) {
            tag.putUUID(TAG_POSSESSED_ORB, this.possessedOrbUuid);
        }
        // 饥饿/饱和度也进存档：它是这具身体的"体力"，读档后不该白送一份满状态
        tag.putInt(TAG_FOOD_LEVEL, this.foodLevel);
        tag.putFloat(TAG_SATURATION, this.saturation);
        tag.putFloat(TAG_EXHAUSTION, this.exhaustion);
        // 变形药水免疫是否已移除（"被解除过一次变形"之后就不再免疫）
        tag.putBoolean(TAG_TRANSMUTE_IMMUNE_REMOVED, this.possessionTransmutationImmuneRemoved);
        // 已经会飞：读档后要接着飞（否则一读档就"翅膀还在、却掉回地面"）
        tag.putBoolean(TAG_FLYING, this.possessionFlying);
        // "被打落"的剩余刻数（<b>不是绝对刻数</b>：读档后 tickCount 会从头开始，
        // 存绝对值就变成"要等到很久以后"，所以按剩余量落盘、读档时再折算成新的截止刻）。
        if (this.possessionFlightLockedUntilTick >= 0) {
            tag.putInt(TAG_FLIGHT_LOCKED, Math.max(1, this.possessionFlightLockedUntilTick - this.tickCount));
        }
        // 致命打击额度：不落盘的话读档就等于白送四次
        tag.putInt(TAG_FATAL_HITS, this.possessionFatalHits);
        // "地狱疣生成器"那一秒的待办：存剩余刻数（读档后 tickCount 从头开始，存绝对值会变成"等很久以后"）
        if (this.possessionWartStripAtTick >= 0) {
            tag.putInt(TAG_WART_STRIP, Math.max(1, this.possessionWartStripAtTick - this.tickCount));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSkinTexture(tag.getString(TAG_SKIN));
        if (tag.contains(TAG_ORIGIN_NBT)) {
            originNbt = tag.getCompound(TAG_ORIGIN_NBT);
        }
        if (tag.contains(TAG_ORIGIN_PLAYER)) {
            originPlayerUuid = tag.getUUID(TAG_ORIGIN_PLAYER);
        }
        if (tag.contains(TAG_ORIGIN_KILLER)) {
            originKillerUuid = tag.getUUID(TAG_ORIGIN_KILLER);
        }
        this.remainingTicks = tag.contains(TAG_REMAINING_TICKS)
            ? tag.getInt(TAG_REMAINING_TICKS) : -1;
        this.setOrbAttached(tag.getBoolean(TAG_ORB_ATTACHED));
        this.possessedOrbUuid = tag.hasUUID(TAG_POSSESSED_ORB) ? tag.getUUID(TAG_POSSESSED_ORB) : null;
        this.foodLevel = tag.contains(TAG_FOOD_LEVEL) ? tag.getInt(TAG_FOOD_LEVEL) : 20;
        this.saturation = tag.contains(TAG_SATURATION) ? tag.getFloat(TAG_SATURATION) : 5.0F;
        this.exhaustion = tag.contains(TAG_EXHAUSTION) ? tag.getFloat(TAG_EXHAUSTION) : 0.0F;
        this.possessionTransmutationImmuneRemoved = tag.getBoolean(TAG_TRANSMUTE_IMMUNE_REMOVED);
        // 读档恢复飞行状态：AI 那边每刻都会重新 setNoGravity(true)，这里只要把标记接上
        if (tag.getBoolean(TAG_FLYING)) {
            this.possessionFlying = true;
            this.setNoGravity(true);
        }
        // "被打落"的剩余时间：存档里记的是绝对刻数，读档后 tickCount 从 0 重新开始，
        // 所以这里换算成"还需要多少刻"再折成新的绝对刻数（做法与别处的截止刻一致）。
        if (tag.contains(TAG_FLIGHT_LOCKED)) {
            int remaining = tag.getInt(TAG_FLIGHT_LOCKED);
            if (remaining > 0) {
                this.possessionFlightLockedUntilTick = this.tickCount + remaining;
                this.setNoGravity(false);   // 落着地读档：先把无重力关掉，到点再由计时器恢复
            }
        }
        // 旧存档没有这个字段：给满额度（新加的机制不该让老存档直接"零次即同化"）
        this.possessionFatalHits = tag.contains(TAG_FATAL_HITS)
            ? tag.getInt(TAG_FATAL_HITS) : DEFAULT_FATAL_HITS;
        // "地狱疣生成器"那一秒的待办：同样存剩余刻数，读档时折成新的绝对刻数
        if (tag.contains(TAG_WART_STRIP)) {
            int remaining = tag.getInt(TAG_WART_STRIP);
            if (remaining > 0) {
                this.possessionWartStripAtTick = this.tickCount + remaining;
            }
        }
    }

    @Override
    protected void registerGoals() {
        // 无任何 AI 目标
    }

    @Override
    protected void pickUpItem(ItemEntity itemEntity) {
        // 玩家空壳不拾取任何物品（保持“不互动”设定），
        // 也避免触发 Mob.canReplaceCurrentItem -> getApproximateAttackDamageWithItem 的属性查询
    }

    /** 附体形态：索敌范围（格）：50 格 —— 附体空壳会主动找上远处的敌人。 */
    private static final double POSSESS_ATTACK_RANGE = 50.0;

    /** 附体形态：每隔多少刻重新索敌一次（换目标就立刻重抽签）。 */
    private static final int POSSESS_TARGET_SCAN_INTERVAL = 10;

    /**
     * 目标<b>看不见了</b>之后还容忍多少刻才把它丢掉（20 刻 = 1 秒）。
     * <p>
     * 对应原版 {@code TargetGoal#unseenMemoryTicks}（默认 {@code 60}）那套"记恨"机制，
     * 只是把时限压到 1 秒：原版那 3 秒是给"玩家躲进掩体、怪物在门外守着"用的，
     * 而这里的空壳是主动追杀的一方，目标一钻墙就该立刻改打别人（见
     * {@link #findPossessionTarget()} 与 {@link #tickPossessionTargetScan()}）。
     * <p>
     * 之所以要这个宽限、而不是"看不见就当场丢"，是为了防抖动：目标从树后跑过、
     * 或者空壳自己在俯冲途中被子方块挡了半刻视线，都不该让它立刻翻脸。
     */
    private static final int POSSESS_TARGET_UNSEEN_MEMORY_TICKS = 20;

    /** 索敌可见性：当前目标连续多少刻没被看见（0 = 本刻看得见）。 */
    private int possessionTargetUnseenTicks;

    /** 索敌可见性：上一次判定"看得见/看不见"的结果，用来实现上面那个"连续"计数。 */
    private boolean possessionTargetSeen = true;

    /** 附体形态：抽签间隔下限（刻）—— 1 秒。 */
    private static final int POSSESS_DRAW_MIN_TICKS = 20;

    /** 附体形态：抽签间隔上限（刻）—— 2 秒。 */
    private static final int POSSESS_DRAW_MAX_TICKS = 40;

    /**
     * 附体形态：各攻击事件上次兑现的游戏时间，用来算各自的冷却。
     * <p>
     * 键直接用事件对象本身（{@link java.util.IdentityHashMap}）：事件都是
     * {@link OrbPossessedAttackEvents} 里的单例，身份比较足够，也不必实现 hashCode。
     * 记在实体身上而不是静态表里 —— 这具空壳一移除，冷却表跟着一起没了，不留需要清理的全局状态。
     */
    private final Map<OrbPossessedAttackEvents.PossessedAttackEvent, Long> possessAttackTimes = new IdentityHashMap<>();

    /**
     * 附体形态：当前抽到的事件（null = 没抽到 / 目标没了 / 刚换目标还没重抽）。
     * <p>
     * 它决定这段时间空壳"用什么手段"：射箭事件照常走位射击，近战事件摆近战架势
     * （见 {@link OrbPossessedAttackEvents}）。
     */
    private OrbPossessedAttackEvents.PossessedAttackEvent possessionDrawnEvent;

    /** 附体形态：下一次抽签的 tickCount。 */
    private int possessionNextDrawTick;

    /**
     * 附体形态：<b>下一次务必从近战池里抽</b>（一次性）。
     * <p>
     * 由铁抓钩那条路置起（见 {@code OrbHookPull}）：铁链被方块挡住、或者刚把玩家拉到近战距离时，
     * 用户要求"AI 会立刻抽取下一次近战攻击" —— 于是作废手里的签、把本标记置真，
     * 下一次 {@code drawUsable} 直接从近战池里抽一张（池子里这一瞬一张都用不了时照常走正常抽签）。
     */
    private boolean possessionForceMeleeDraw;

    /** 附体形态：是否正处于"近战架势"（停下走位、原地盯着目标）——由抽到的事件自己声明。 */
    private boolean possessionHoldingMelee;

    /** 附体形态：起手（蓄力）结束、该出手的 tickCount；-1 = 当前不在起手中。 */
    private int possessionWindUpEndTick = -1;

    /**
     * 附体形态：解除"站着不动"（{@code setNoAi(true)}），加入骷髅式走位。
     * <p>
     * 只给"被附体"这具空壳用，不影响变形药水那套空壳（它们仍然 {@code noAi} 站着）。
     * 走位交给 {@link OrbPossessionKiteGoal}（目标太近就后退、并环绕），
     * 攻击由 {@link #tick()} 里的抽签逻辑负责。
     *
     * <h2>为什么这里按"goal 在不在"判断，而不是用一个"装过没有"的布尔</h2>
     * goal 是<b>命令式</b>注册到 {@code goalSelector} 上的、<b>不进存档</b>，读档后必须重装。
     * 三种"由幸运核心支撑的空壳"里，只有<b>附体主空壳</b>有人替它补：
     * 核心每刻的 {@code OrbOfLuckEntity#ensureVessel} 会调一次本方法。
     * 另外两种（<b>被同化的</b>、<b>刷怪蛋放出来的</b>）没有任何东西会替它们补 ——
     * 用一个内存布尔记住"装过了"，读档后那个布尔虽然是 false、但也没人再来调本方法，
     * 于是它们就永久退化成"站着不动、只会出手"的木桩（表现就是"退出存档重进后不会走位了"）。
     * <p>
     * 所以改成每刻都能安全调用的形式：直接看 goal <b>在不在</b>，
     * 不在才（重新）装上。正常情况就是遍历两三个 goal，代价可以忽略。
     */
    public void enablePossessionCombat() {
        // 已经装好：顺便把调试用的引用补上（读档后字段是空的）
        for (WrappedGoal wrapped : this.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof OrbPossessionKiteGoal kite) {
                this.possessionKiteGoal = kite;
                return;
            }
        }
        this.setNoAi(false);
        // 保险：清掉任何来源的"无重力"残留。
        // （6.3.0 曾有个 bug 让空壳在射出爆炸召唤物时自己被设成 noGravity 而飘起来，这里兜一下）
        // <b>会飞的空壳例外</b>：无重力正是它的飞行形态，清掉就等于把它摔下来。
        if (!this.possessionFlying) {
            this.setNoGravity(false);
        }
        this.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(this));
        // 留一份引用：只给 /jafa orbinfo 读"当前走位状态"用（见 describePossessionKiteState）
        this.possessionKiteGoal = new OrbPossessionKiteGoal(this, 1.0, 15.0F);
        this.goalSelector.addGoal(4, this.possessionKiteGoal);
    }

    /** 走位 goal 的引用（仅调试用；由 {@link #enablePossessionCombat()} 填，读档后重新填上）。 */
    private OrbPossessionKiteGoal possessionKiteGoal;

    /**
     * <b>这具空壳现在能不能出手</b>：{@code NoAI} 为 true 时一律不能（用户指定）。
     * <p>
     * 这是所有攻击方式的<b>唯一总闸</b>，只在 {@link #tick()} 里判一次：现在的每一招
     * （抽签、起手、出手、重锤动作、跨刻连发）都排在它后面，以后新增的攻击方式
     * 只要走同一套流程就自动适用，不需要各自再加一遍判断。
     * <p>
     * 注意 {@code NoAI} 只挡"出手"，不挡受伤/回血/吃东西那些；被冻住的空壳就是一具木桩。
     */
    public boolean canPossessAttack() {
        return !this.isNoAi();
    }

    /**
     * 收掉一切"正在进行中"的攻击（总闸关闭时调用）：
     * <ul>
     *   <li>放下一半的起手（弓/弩/三叉戟/法杖）；</li>
     *   <li>手里那张签作废（否则解冻的瞬间会接着打出冻结前那一招）；</li>
     *   <li>重锤/落地水那套跨刻动作；</li>
     *   <li>{@link OrbBurstShots 连发调度}里还没打完的几发。</li>
     * </ul>
     */
    private void abortPossessionAttacks() {
        this.cancelPossessionWindUp();
        this.possessionDrawnEvent = null;
        this.possessionHoldingMelee = false;
        this.possessionForceMeleeDraw = false;
        OrbPossessionMaceAttack.abort(this);
        OrbBurstShots.cancel(this);
        // 天境那条链只在装了天境时才碰（门在 OptionalMods：卸载天境后加载 AetherCompat 会抛 NoClassDefFoundError）
        if (OptionalMods.isAetherLoaded()) {
            AetherCompat.cancelVolleys(this);
        }
    }

    // ====================== 附体 boss 血条 ======================

    /** 血条只发给这个距离内的玩家（格）：太远的玩家没必要看到每一具空壳的血条。 */
    private static final double SHELL_BAR_VIEW_DISTANCE = 48.0D;

    /**
     * 附体形态的 boss 血条（用户指定）：<b>①本体血量 = 紫色、居中</b>，
     * <b>②伤害吸收那段 = 黄色、接在①右侧</b>（②由客户端补画，见 {@code client.ShellBossBarClient}）。
     *
     * <h2>服务端这边只做三件事</h2>
     * <ol>
     *   <li>原版 {@link ServerBossEvent}：进度就是本体血量占比（①没有额外机制，用户指定）；</li>
     *   <li>把伤害吸收量同步出去（{@link #DATA_ABSORPTION}）—— 原版
     *       {@code LivingEntity#getAbsorptionAmount()} 不跨端同步，而②要画在"别的实体"身上；</li>
     *   <li>把血条 UUID 同步出去（{@link #DATA_SHELL_BAR_ID}），客户端靠它把
     *       "屏幕上这条 boss 血条"认成"这具空壳"。</li>
     * </ol>
     * 颜色用 {@code BossBarColor.PURPLE}（女巫三阶段那条紫），与"②用女巫二阶段的黄"相对应。
     *
     * <h2>哪些空壳有血条</h2>
     * 三种"由幸运核心支撑的空壳"都有（附体主空壳、被同化的那一具、刷怪蛋放出来的那一具）——
     * 它们用的是同一套配置；变形药水造出来的临时空壳没有（{@link #isOrbAttached()} 为假）。
     */
    private void tickShellBossBar() {
        if (!(this.level() instanceof ServerLevel serverLevel) || serverLevel.getServer() == null) {
            return;
        }
        if (!this.isOrbAttached() || !this.isAlive()) {
            this.hideShellBossBar();
            return;
        }
        ServerBossEvent bar = this.shellBossBar();
        // ① 本体血量：进度就是血量占比，没有任何额外机制（用户指定）
        bar.setName(this.getDisplayName());
        bar.setProgress(Mth.clamp(this.getHealth() / Math.max(1.0F, this.getMaxHealth()), 0.0F, 1.0F));
        bar.setVisible(true);
        // ② 伤害吸收（黄心）：同步给客户端去画右边那一段
        this.entityData.set(DATA_ABSORPTION, this.getAbsorptionAmount());
        // 观众：附近 48 格内的玩家（走远/换维度就把他摘掉，否则客户端会残留一条冻结的血条）
        long now = serverLevel.getGameTime();
        for (ServerPlayer sp : serverLevel.getServer().getPlayerList().getPlayers()) {
            boolean show = sp.level() == serverLevel
                && sp.distanceToSqr(this) <= SHELL_BAR_VIEW_DISTANCE * SHELL_BAR_VIEW_DISTANCE;
            if (show) {
                // <b>刚换过维度的玩家：先摘掉再挂上</b>，强制把这条血条重发一遍。
                // 原版 {@code addPlayer} 对"已经在集合里"的玩家是空操作，而换维度那一刻客户端
                // 会重建界面状态 —— 不强制重发的话，这一次 ADD 丢掉之后就再也补不回来了
                // （见 {@link OrbPossessionEvents#onPlayerChangedDimension}）。
                Long resendUntil = SHELL_BAR_RESEND_UNTIL.get(sp.getUUID());
                if (resendUntil != null) {
                    if (now <= resendUntil) {
                        bar.removePlayer(sp);
                    } else {
                        SHELL_BAR_RESEND_UNTIL.remove(sp.getUUID());
                    }
                }
                bar.addPlayer(sp);
            } else {
                bar.removePlayer(sp);
            }
        }
    }

    /**
     * "这位玩家刚换过维度，需要把血条重新挂一遍"的记号（玩家 UUID → 记号到期的游戏刻）。
     * <p>由 {@link OrbPossessionEvents#onPlayerChangedDimension} 写入；真正做"摘掉再挂上"的是
     * {@link #tickShellBossBar()}（那边才知道这具空壳有没有血条）。留一个
     * {@link #BOSS_BAR_RESEND_TICKS} 的窗口，是为了让<b>所有</b>空壳都有机会在自己那一 tick 里补挂。
     */
    private static final java.util.Map<UUID, Long> SHELL_BAR_RESEND_UNTIL = new java.util.HashMap<>();

    /** 换维度之后"补挂血条"的窗口（刻）：2 秒，足够附近每一具空壳都 tick 到。 */
    private static final int BOSS_BAR_RESEND_TICKS = 40;

    /** 记下"这位玩家刚换过维度"（见 {@link #SHELL_BAR_RESEND_UNTIL}）。 */
    public static void markBossBarResend(ServerPlayer player) {
        SHELL_BAR_RESEND_UNTIL.put(player.getUUID(), player.level().getGameTime() + BOSS_BAR_RESEND_TICKS);
    }

    /** 取（必要时新建）这具空壳的 boss 血条。 */
    private ServerBossEvent shellBossBar() {
        if (this.shellBossBar == null) {
            this.shellBossBar = new ServerBossEvent(this.getDisplayName(),
                net.minecraft.world.BossEvent.BossBarColor.PURPLE,
                net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
            this.entityData.set(DATA_SHELL_BAR_ID, this.shellBossBar.getId().toString());
        }
        return this.shellBossBar;
    }

    /**
     * 收掉血条：<b>必须</b>先 {@code removeAllPlayers()} 再丢弃引用 ——
     * 只把事件从内存里删掉的话客户端永远收不到"移除这条血条"的包，屏幕上会冻结一条血条
     * （女巫 Boss 那边踩过同一个坑，见 {@code ModMain#updateWitchBossBar} 的注释）。
     */
    private void hideShellBossBar() {
        if (this.shellBossBar == null) {
            return;
        }
        this.shellBossBar.setVisible(false);
        this.shellBossBar.removeAllPlayers();
        this.shellBossBar = null;
        this.entityData.set(DATA_SHELL_BAR_ID, "");
        this.entityData.set(DATA_ABSORPTION, 0.0F);
    }

    /**
     * 走位 AI 此刻是不是处于<b>近身阶段</b>（对玩家时的"近身意愿模式"，见
     * {@link OrbPossessionKiteGoal}）—— 也就是用户说的"AI 处于近战状态"。
     * <p>
     * 供铁抓钩攻击当触发条件用。会飞的情况下那套走位根本不在跑，这个值会停留在最后一次算出来的结果，
     * 所以调用方（{@code HookPullEvent}）还要自己排除"已经会飞"。
     */
    public boolean isEngageMeleePhase() {
        return this.possessionKiteGoal != null && this.possessionKiteGoal.isEngageMelee();
    }

    /**
     * <b>立刻作废手里的签、重抽一次，而且下一次从近战池里抽</b>（用户指定）。
     * <p>
     * 铁抓钩被打断（铁链被方块挡住）或拉到位之后由 {@code OrbHookPull} 调用：
     * "这个过程会被中断，AI 会立刻抽取下一次近战攻击"。
     * 不直接在这里抽签，是因为抽签只发生在 {@link #tickPossessionAttack} 那一小段流程里
     * （要清双手、走起手姿态），所以只置标志、由下一帧的抽签逻辑兑现。
     */
    public void requestImmediateMeleeRedraw() {
        this.possessionDrawnEvent = null;
        this.possessionNextDrawTick = 0;
        this.possessionHoldingMelee = false;
        this.possessionForceMeleeDraw = true;
    }

    /**
     * 取出并清掉"下一次务必抽近战"的标志（见 {@link #requestImmediateMeleeRedraw()}）。
     * <p>
     * 由 {@code OrbPossessedAttackEvents#drawUsable} 每次抽签开头问一次 —— 一次性语义，
     * 所以这里读一次就清掉，免得后面每一轮都被钉死在近战池里。
     */
    public boolean consumeForcedMeleeDraw() {
        boolean forced = this.possessionForceMeleeDraw;
        this.possessionForceMeleeDraw = false;
        return forced;
    }

    /**
     * 这具空壳现在还能不能对这名目标出手（创造/旁观/被保护玩家都算"不能"）。
     * <p>
     * 只给铁抓钩拉扯期间用：拉扯跨好几刻，中途玩家切旁观/创造就不该继续被拖。
     */
    public boolean canStillAttackTarget(@Nullable LivingEntity target) {
        return !this.mustNotAttack(target);
    }

    /**
     * 调试用：把走位 goal 的内部状态念成一句人话（{@code /jafa orbinfo} 的"走位"字段）。     * <p>
     * 用于分辨"空壳其实是站着/在直冲"还是"真的在环绕"—— 光看画面很难分辨。
     * 正在吃附魔金苹果时优先报食用状态（此时走位逻辑整个被跳过）。
     * 未装 goal（理论上不会）时返回"未装"。
     */
    public String describePossessionKiteState() {
        if (this.isEatingFood()) {
            return (this.isEatingApple() ? "吃附魔金苹果(剩" : "吃东西(剩")
                + this.getUseItemRemainingTicks() + "刻)";
        }
        return this.possessionKiteGoal == null ? "未装" : this.possessionKiteGoal.describeState();
    }

    /** 该攻击事件在这个攻击者身上是否已冷却完毕。 */
    public boolean isAttackReady(OrbPossessedAttackEvents.PossessedAttackEvent event, long gameTime) {
        Long last = this.possessAttackTimes.get(event);
        if (last == null) return true;
        // 冷却走"按局面动态"的那个重载：个别特攻的出手窗口只有十几刻
        // （最典型的是"目标带着重锤滞空"→ 丢缓降药水），不能按平时那套干等
        // （见 PossessedAttackEvent#cooldownTicks(LivingEntity, LivingEntity)）。
        return gameTime - last >= event.cooldownTicks(this, this.getTarget());
    }

    /** 记下这个事件这次的兑现时间，开始算它自己的冷却。 */
    public void markAttackUsed(OrbPossessedAttackEvents.PossessedAttackEvent event, long gameTime) {
        this.possessAttackTimes.put(event, gameTime);
        // 发起一次攻击之后，接下来 10 游戏刻"失去移动 AI"（站定收招）。
        // 走位 goal 每刻读 isPossessionMovementLocked()，为真就停步 —— 只是不走位，
        // 转头盯人、抽签、出手都不受影响。
        //
        // <b>只对"玩家目标"保留这个休息阶段</b>（用户指定）：打非玩家目标（生物/Boss）时出手即走，
        // 不再有收招僵直 —— 面对玩家才需要那点"交完手喘一口"的留白。
        // 没有目标时（例如自救丢奶桶）也不算玩家，同样不僵直。
        if (this.getTarget() instanceof Player) {
            this.possessionNoMoveUntilTick = this.tickCount + POSSESS_NO_MOVE_TICKS;
        }
    }

    /** 出手后的"收招僵直"刻数：这段时间内部走位（仅玩家目标，见 {@link #markAttackUsed}）。 */
    public static final int POSSESS_NO_MOVE_TICKS = 10;

    /** 下一次可以恢复走位的刻数；-1 表示没有僵直。 */
    private int possessionNoMoveUntilTick = -1;

    /**
     * 让空壳"暂时失去走位 AI"这么多刻（站定收招，转头盯人不受影响）。
     * <p>
     * 与 {@link #markAttackUsed} 里那段僵直的区别：那个只对<b>玩家</b>目标给；
     * 这个方法是不看目标的"硬僵直"，供需要单独收招的特攻使用
     * （目前是抛出变形解药之后，见
     * {@code OrbPossessedAttackEvents.TransmutationAntidoteEvent#run}）。
     */
    public void lockMovementFor(int ticks) {
        this.possessionNoMoveUntilTick = Math.max(this.possessionNoMoveUntilTick, this.tickCount + ticks);
    }

    /**
     * 当前是不是处在"出手后的收招僵直"里（攻击后 {@link #POSSESS_NO_MOVE_TICKS} 游戏刻内）。
     * <p>
     * {@link OrbPossessionKiteGoal} 每刻读它：为真就停掉导航、把前后/左右输入清零，
     * 只保留"转头盯着目标"—— 也就是"发起一次攻击后暂时失去移动 AI"。
     * <p>
     * 这个僵直只会由"对<b>玩家</b>目标出手"触发，见 {@link #markAttackUsed}。
     */
    public boolean isPossessionMovementLocked() {
        return this.possessionNoMoveUntilTick > 0 && this.tickCount < this.possessionNoMoveUntilTick;
    }

    /** 当前抽到的事件（可能为 null）；调试/排查用。 */
    public OrbPossessedAttackEvents.PossessedAttackEvent getPossessionDrawnEvent() {
        return this.possessionDrawnEvent;
    }

    /**
     * 是否处于"近战架势"：暂停走位、原地盯着目标出刀。
     * <p>
     * {@link OrbPossessionKiteGoal} 每刻读它：为真就停步并只转头，为假才继续骷髅式走位。
     * <p>
     * <b>监守者是例外</b>：对"必须一直走位"的敌人（见 {@link #mustAlwaysKite}）永远返回 false ——
     * 这具空壳只有 20 点血，被监守者贴脸打一下就没了，所以对它"无视相距距离一定走位"：
     * 哪怕目标已经贴到 3 格内（正常情况下会摆架势站桩出刀），也照常绕圈后退。
     * 特攻表里监守者的远程池还留着一张"喊 Bob"的近战签（贴身时才有几率抽到），
     * 那张签同样要求站桩，这里一并否掉 —— 叫帮手可以，站住不动不行。
     */
    public boolean isHoldingForMelee() {
        LivingEntity target = this.getTarget();
        if (this.possessionHoldingMelee && target != null && mustAlwaysKite(target)) {
            return false;
        }
        return this.possessionHoldingMelee;
    }

    /**
     * 这个敌人是不是"必须无视距离一直保持走位"的那一类（目前只有监守者）。
     * <p>
     * 判据与特攻表（{@code OrbPossessedAttackEvents#MATCHUPS}）保持一致：那边监守者那条
     * 就是按 {@code EntityType.WARDEN} 匹配的。以后若还有"贴脸必死、只能放风筝"的敌人，
     * 往这里加一条即可 —— 效果是"对它永远不摆近战架势，贴脸也继续环绕后退"。
     */
    /**
     * 这个敌人是不是"必须无视距离一直保持走位"的那一类。
     * <p>
     * 两类：
     * <ul>
     *   <li>监守者（{@code EntityType.WARDEN}）—— 贴脸一下就没，永远不摆近战架势；</li>
     *   <li>一切被特攻表要求"<b>尽量远程</b>"的敌人（{@link OrbPossessedAttackEvents#prefersRanged}：
     *       女巫、苦力怕、铁傀儡、卫道士、满血暮色巫妖……）—— 它们的走位又会走"保持距离"模式，
     *       绝不主动靠近，所以近战架势必须一并关掉，否则会站住不动被打。</li>
     * </ul>
     * 表现为 {@link #isHoldingForMelee()} 对它们一律返回 false。
     */
    private static boolean mustAlwaysKite(LivingEntity target) {
        return target.getType() == net.minecraft.world.entity.EntityType.WARDEN
            || OrbPossessedAttackEvents.prefersRanged(target);
    }

    /**
     * 结束当前起手（如果有）：让事件自己收回武器、退出蓄力姿态。
     * <p>
     * 换目标、目标消失、重抽签之前都要调一次，否则会留下"举着弓不放"的残留姿态。
     */
    private void cancelPossessionWindUp() {
        if (this.possessionWindUpEndTick < 0) {
            return;
        }
        this.possessionWindUpEndTick = -1;
        OrbPossessedAttackEvents.PossessedAttackEvent winding = this.possessionDrawnEvent;
        if (winding != null) {
            winding.endWindUp(this);
        }
    }

    /** 清空两手：每次抽到新事件都先把上一件的武器收掉，由事件自己决定拿什么。 */
    private void clearPossessionHands() {
        this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        this.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }

    // ====================== 低概率特攻：飞行（wings） / 重锤 ======================

    /**
     * 是否已经"会飞"。
     * <p>
     * 由远程低概率特攻"飞行"（{@code OrbPossessedAttackEvents.FlightEvent}）置起，
     * 之后<b>一直保持</b>：走位换成 {@link OrbPossessionFlightAI}（离地 4~5 格、与目标水平距离 7.5~15 格），
     * 抽签也默认只抽远程（只有目标贴到 3 格内才允许近战，见
     * {@code OrbPossessedAttackEvents#drawUsable}）。
     */
    private boolean possessionFlying;

    /**
     * "飞行能力被暂时剥夺"的截止刻（{@link #tickCount} 口径）；-1 = 没被剥夺。
     * <p>
     * 用户指定的规则：<b>已经会飞</b>的空壳在空中被<b>弹射物</b>击中（含爆炸箭）时会<b>落地</b>，
     * 并由此失去 {@link #FLIGHT_LOCKOUT_MIN_TICKS}~{@link #FLIGHT_LOCKOUT_MAX_TICKS} 刻的飞行能力：
     * 这段时间里 {@link #isFlightMode()} 为 false —— 于是走位退回地面那套
     * （{@link OrbPossessionKiteGoal} 的骷髅式走位 + 近战架势），
     * 抽签也退回"获得飞行能力之前"的规则（可以抽近战/召唤/重锤）。时间到了自动恢复飞行
     * （前提是翅膀还在胸甲栏，见 {@link #tickFlightLockout()}）。
     */
    private int possessionFlightLockedUntilTick = -1;

    /** 被"打落"之后失去飞行能力的最短刻数（80 刻 = 4 秒）。 */
    private static final int FLIGHT_LOCKOUT_MIN_TICKS = 80;

    /** 被"打落"之后失去飞行能力的最长刻数（120 刻 = 6 秒）。 */
    private static final int FLIGHT_LOCKOUT_MAX_TICKS = 120;

    /** 致命打击额度的默认值（用户指定 4）。 */
    public static final int DEFAULT_FATAL_HITS = 4;

    /**
     * 击败<b>不是母体</b>的附体空壳掉落的经验点数（用户指定 100）。
     * <p>
     * 覆盖两种"背后没有母体核心"的附体空壳：刷怪蛋放出来的那一具、以及被别人同化后留下的那一具。
     * 母体（背后有核心的那具 boss 身体）由核心那边发奖励 —— 两组经验瓶 + 一组幸运方块
     * （见 {@code OrbOfLuckEntity#grantDefeatRewards()}）。
     */
    public static final int NON_MOTHER_DEFEAT_EXPERIENCE = 100;

    /**
     * <b>致命打击计数器（fatal hits）</b>：默认 {@link #DEFAULT_FATAL_HITS}。
     * <p>
     * 每对玩家打出一次"重击"（原始伤害 ≥ 其最大生命值 ×
     * {@code OrbOfLuckEntity#FATAL_HIT_MAX_HEALTH_FRACTION}）就减一；归零则把那玩家<b>同化</b>
     * （见 {@code OrbAssimilation}）。
     * <p>
     * <b>为什么记在空壳身上，而不是像以前那样记在核心身上</b>：
     * 由幸运核心产生的玩家空壳有两种 —— 附体主空壳（背后有一颗核心实体）与
     * <b>没有核心实体</b>的那一具（刷怪蛋放出来的、或者核心已经不在了的同化壳）。
     * 记在核心上时，后一种根本无处可记，于是"归属解析"整条链在第一步就断了：
     * 致命打击计数与同化对刷怪蛋空壳完全失效（实测反馈 + 日志里的 {@code orb=查不到}）。
     * 记在空壳自己身上之后，<b>每一具由核心产生的空壳都具备同化他人的能力</b>（用户指定），
     * 不再依赖场上有核心实体。
     */
    private int possessionFatalHits = DEFAULT_FATAL_HITS;

    /** 还剩几次"致命打击"的额度。 */
    public int getFatalHits() {
        return this.possessionFatalHits;
    }

    /** 直接设定额度（读档/调试用）。 */
    public void setFatalHits(int value) {
        this.possessionFatalHits = Math.max(0, value);
    }

    /**
     * 记一次"致命打击"：额度减一。
     *
     * @return 减完之后是否为 0（true = 该触发同化了）
     */
    public boolean consumeFatalHit() {
        if (this.possessionFatalHits > 0) {
            this.possessionFatalHits--;
        }
        return this.possessionFatalHits <= 0;
    }

    /**
     * 这具空壳的翅膀是<b>被这套飞行能力发下去的</b>（而不是它自己捡来穿戴的）。
     * <p>
     * 用途只有一处：{@code FlightEvent#endWindUp} 在"没真的起飞"时要把翅膀收回去，
     * 但那一收回会误伤<b>正在被打落期间又抽到飞行签</b>的情况 —— 那一次它本来就会"起手 →
     * 被打落计时压着起不来 → 起手结束收翅膀"，把打落时刚补上的翅膀又剥掉。
     * 有了这个标记，只有"发下去的那副"才会被收；已经会飞（{@link #possessionFlying}）时永远不收。
     */
    private boolean wingsGranted;

    /** 飞行走位的绕圈方向与翻转计时（见 {@link OrbPossessionFlightAI}）。 */
    private boolean possessionFlightOrbitClockwise = true;
    private int possessionFlightOrbitFlipTick;

    public boolean isFlightMode() {
        // 被弹射物"打落"期间一律按"不会飞"处理（见 possessionFlightLockedUntilTick）：
        // 走位、抽签、绘制姿势全都跟着退回地面那套，这就是需求里的
        // "此时暂时恢复未获得飞行能力时的 AI"。
        return this.possessionFlying && this.tickCount >= this.possessionFlightLockedUntilTick;
    }

    /** 是否"已经获得过飞行能力"（不看当前有没有被打落）—— 调试与"恢复飞行"判定用。 */
    public boolean hasFlightAbility() {
        return this.possessionFlying;
    }

    /**
     * 被弹射物打落：<b>落地 + 剥夺 80~120 刻的飞行能力</b>（用户指定）。
     * <p>
     * 由 {@link #onPossessionProjectileHit} 在"会飞"时调用。做五件事：
     * <ol>
     *   <li>记下恢复刻数（80~120 刻之间随机，每次不一样）；</li>
     *   <li>把无重力关掉 —— 这一步就是"落地"：它本来靠 noGravity 悬在空中，
     *       一旦恢复重力，飞行动画那套推力就压不住它了；</li>
     *   <li>把身上的滑翔姿态取消（{@code setSharedFlag(7, false)}），
     *       否则客户端会一直画成"躺平滑翔"而与地面 AI 的走位对不上；</li>
     *   <li>顺手把已经记下的重锤动作作废（见 {@code OrbPossessionMaceAttack.abort}）：
     *       那套状态机自己控飞行/滑翔，还会为了俯冲把<b>胸甲换成钻石胸甲</b>，
     *       不打断的话"剥夺飞行"对它是无效的；</li>
     *   <li>把胸甲栏恢复成翅膀 —— 上一步的重锤动作可能刚把胸甲换掉了，
     *       而"有翅膀"是这套飞行设定的外观前提（重锤收场时会把原胸甲还回来，
     *       但那条路只在 {@code finish} 里走，被打落时走的是 {@code abort}）。</li>
     * </ol>
     * <p>
     * 这段时间里 {@link #isFlightMode()} 为 false，所以走位与抽签自动退回"获得飞行能力之前"的规则；
     * 到点由 {@link #tickFlightLockout()} 恢复。
     * <p>
     * <b>重复命中会重置计时</b>（而不是被"已经在落"挡掉）：玩家连射时它就一直在地上，
     * 这才是"被弹射物压制"该有的手感。
     *
     * @param reason 日志用的原因（目前只有"被弹射物击中"）
     */
    public void knockDownFlight(String reason) {
        if (!this.possessionFlying) {
            return;   // 本来就不会飞：这不叫"打落"
        }
        int lockTicks = FLIGHT_LOCKOUT_MIN_TICKS
            + this.getRandom().nextInt(FLIGHT_LOCKOUT_MAX_TICKS - FLIGHT_LOCKOUT_MIN_TICKS + 1);
        this.knockDownFlight(reason, lockTicks);
    }

    /**
     * 同 {@link #knockDownFlight(String)}，但时长由调用方指定。
     * <p>
     * 需求 6.5.21 的"支援幸运方块"要的是<b>正好 2 秒</b>的坠机，不能用上面那个随机时长，
     * 所以把它抽成重载；顺带这个时长也和 {@link #applyPilotStun(int)} 的"失去 AI"时长对齐，
     * 两者到点同时恢复（否则会出现"AI 回来了但还飞不起来"这种半截状态）。
     *
     * @param lockTicks 失去飞行能力的刻数
     */
    public void knockDownFlight(String reason, int lockTicks) {
        if (!this.possessionFlying) {
            return;   // 本来就不会飞：这不叫"打落"
        }
        this.possessionFlightLockedUntilTick = this.tickCount + lockTicks;
        this.setNoGravity(false);
        this.setSharedFlag(7, false);
        OrbPossessionMaceAttack.abort(this);
        // 重锤的 abort 会把胸甲留成钻石胸甲（它是在 begin 里换的），这里补回翅膀；
        // 已经戴着翅膀时 setItemSlot 是幂等的，不用先判。
        this.setItemSlot(EquipmentSlot.CHEST,
            new ItemStack(cn.autoforged.joes_addons_for_abmc.item.ModItems.WINGS.get()));
        this.wingsGranted = true;
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
            "[附体] 飞行被打落({}): 落地并失去飞行能力 {} 刻", reason, lockTicks);
    }

    /**
     * <b>被"支援幸运方块"波及：{@code ticks} 刻内失去 AI</b>（需求 6.5.21）。
     *
     * <p>做两件事，缺一不可：
     * <ol>
     *   <li>{@code setNoAi(true)}：这是"失去 AI"——goal 全停（{@code serverAiStep} 不再跑，
     *       飞控 {@code OrbPossessionKiteGoal} 自然也就停了），而 {@code canPossessAttack()} 是
     *       {@code !isNoAi()}，所以这段时间它也<b>不出手</b>；</li>
     *   <li>{@link #knockDownFlight(String, int)}：飞行形态下 {@code noGravity} 是开着的
     *       （{@code tickFlightLockout} 恢复时会重新打开），只关 AI 的话它只会"原地悬停"而不会掉——
     *       需求要的是<b>坠机</b>，所以这里显式把重力打开、并按同样时长锁掉飞行能力，
     *       翅膀仍然留在胸甲栏（外观就是"有翅膀却在坠"），落水、摔落这些原版物理一律照旧。</li>
     * </ol>
     * <p>时长到点由 {@link #tickPilotStun()} 恢复（连带把 {@code NoAI} 还原成被波及之前的值——
     * 有些空壳本来就是 {@code NoAI} 的木桩，不能被这次波及"治好"）。
     */
    public void applyPilotStun(int ticks) {
        CompoundTag data = this.getPersistentData();
        if (!data.contains(TAG_PILOT_STUN_UNTIL)) {
            data.putBoolean(TAG_PILOT_STUN_PREV_NO_AI, this.isNoAi());
        }
        data.putLong(TAG_PILOT_STUN_UNTIL, this.level().getGameTime() + ticks);
        this.knockDownFlight("支援幸运方块", ticks);
        this.setNoAi(true);
    }

    /** 每刻推进"支援坠机"计时：到点把 {@code NoAI} 还原（见 {@link #applyPilotStun(int)}）。 */
    private void tickPilotStun() {
        CompoundTag data = this.getPersistentData();
        long until = data.getLong(TAG_PILOT_STUN_UNTIL);
        if (until == 0L || this.level().getGameTime() < until) {
            return;
        }
        this.setNoAi(data.getBoolean(TAG_PILOT_STUN_PREV_NO_AI));
        data.remove(TAG_PILOT_STUN_UNTIL);
        data.remove(TAG_PILOT_STUN_PREV_NO_AI);
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info("[支援] 附体空壳恢复 AI");
    }


    /**
     * 每刻推进"打落"计时：到点<b>恢复飞行能力</b>。
     * <p>
     * 恢复时把无重力打开、给一小脚升力（让"重新起飞"看得见），并把胸甲保证成翅膀。
     * 高度不用管：{@code OrbPossessionFlightAI} 每刻都算"地面之上 4~5 格"的目标高度并朝它靠，
     * 所以到点之后它会自己回到设定高度。
     * <p>
     * 唯一"不恢复"的情况是玩家已经切回创造模式、附体结束（那时 {@link #possessionFlying}
     * 会被 {@link #readAdditionalSaveData} 或核心收场清掉）。
     */
    private void tickFlightLockout() {
        if (this.possessionFlightLockedUntilTick < 0 || this.tickCount < this.possessionFlightLockedUntilTick) {
            return;
        }
        this.possessionFlightLockedUntilTick = -1;
        if (!this.possessionFlying) {
            return;
        }
        // <b>重锤动作进行中：先别恢复</b>（6.7.8）。这一套自己控滑翔：胸甲必须是<b>鞘翅</b>、
        // 而且要靠"自由落体"攒重锤的坠落加成。在这里恢复飞行形态会同时破坏这两件事 ——
        // 把鞘翅换成翅膀（{@code canElytraFly} 恒为 false，原版每刻都会清掉滑翔标记），
        // 并打开 {@code noGravity}（俯冲就不再积坠落距离了）。把计时顶到下一刻，等这一套收场再说。
        if (this.possessionMaceState != null) {
            this.possessionFlightLockedUntilTick = this.tickCount + 1;
            return;
        }
        this.setItemSlot(EquipmentSlot.CHEST,
            new ItemStack(cn.autoforged.joes_addons_for_abmc.item.ModItems.WINGS.get()));
        this.wingsGranted = true;
        this.setNoGravity(true);
        // <b>不给升力</b>：飞控每刻会把高度拉回"地面之上 4~5 格"，而上一版在这里加了 0.42 的
        // 冲量 + 飞控那条"最多降 1.5 格/刻"的限速，两下一夹，它会在被打落前那个很高的位置
        // 磨很久才慢慢下来（用户实测："恢复飞行之后仍然会保持那个高度"）。
        // 现在改成只清掉竖直方向的残余速度，剩下的交给飞控平稳地滑回设定高度。
        this.setDeltaMovement(this.getDeltaMovement().x, 0.0D, this.getDeltaMovement().z);
        this.hasImpulse = true;
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info("[附体] 飞行能力恢复（离地 {} 格）",
            String.format("%.1f", this.getY() - this.level().getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                this.getBlockX(), this.getBlockZ())));
    }

    /**
     * <b>飞行形态的下降</b>（用户指定；6.7.8/6.7.9 定型）。
     *
     * <ul>
     *   <li><b>重锤动作进行中：一律不插手</b>。那一套自己控滑翔与竖直速度（爬升靠烟花、俯冲靠俯角），
     *       在这里把竖直分量钉成"5 格/秒匀速下降"会直接顶掉它的爬升 ——
     *       实测就是这个 bug：<b>"重锤攻击的飞行阶段目标死了之后，它一直保持滑翔、以一个很慢的
     *       速度匀速下降"</b>（飞控被这条按住了，重锤那一套又已经收场）。</li>
     *   <li><b>有活目标</b>：交给 {@link OrbPossessionFlightAI}（它维持"离地 4~5 格"的悬停），
     *       这里只在"高出 5 格"时兜一下匀速下降。</li>
     *   <li><b>没有目标</b>（用户指定）：<b>保持飞行形态</b>，以 5 格/秒降到"距地面
     *       <b>3~4 格</b>"就停住待命，等索敌找到新目标再飞回 4~5 格。
     *       <b>不会落地</b> —— 它会飞，没必要站桩。</li>
     *   <li><b>不是飞行形态</b>：这一条整个不适用（上面第一个 return）。那种情况就是普通自由落体，
     *       落地由水/末影珍珠那套落地保护负责
     *       （{@code OrbPossessionMaceAttack#tryStartEmergencyWaterSave}，每刻在没有重锤动作时调用）。</li>
     * </ul>
     */
    private void tickFlightDescent() {
        if (!this.isFlightMode()) {
            return;   // 不会飞 / 正被"打落"：那就是普通自由落体，交给重力与"落地水/落地珍珠"
        }
        if (this.possessionMaceState != null) {
            return;   // 重锤动作自己控竖直运动，别插手（见上面第 1 条）
        }
        if (this.onGround()) {
            return;   // 已经落地：不用再往下压
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            // 没有目标：降到"距地面 3~4 格"待命（保持飞行形态，不落地）
            OrbPossessionFlightAI.descendToIdleHover(this);
            return;
        }
        OrbPossessionFlightAI.descendIfTooHigh(this);
    }

    /**
     * 被弹射物击中的钩子（由 {@code OrbAssimilation} 的伤害事件调用，只对空壳生效）。
     * <p>
     * <b>条件是纯粹的"状态"</b>：已经会飞（{@link #possessionFlying}）。
     * 不限制这颗弹射物是谁射的 —— 需求原文是"如果幸运核心已拥有翅膀且处于飞行状态，
     * 此时若被弹射物击中则会落地"，讲的是"飞到天上就会被打下来"这件事本身，
     * 所以玩家用爆炸箭把它射下来同样成立（那也正是"包括爆炸箭"这句话想覆盖的玩法）。
     * <p>
     * <b>这里不再要求"此刻没被打落"</b>：上一版加了这一条，结果是连射时只有第一下算数
     * （后面几箭因为"已经在落"被忽略，计时也不会刷新）。现在每一击都会重新计时。
     */
    public void onPossessionProjectileHit() {
        if (!this.possessionFlying) {
            return;   // 不会飞：不算"打落"
        }
        this.knockDownFlight("弹射物");
    }

    // ====================== 地狱疣生成器（wart on a stick）对空壳的效果 ======================

    /**
     * "移除头盔栏地狱疣"的到期刻（见 {@link #applyWartTrap}）；-1 = 没有待办。
     */
    private int possessionWartStripAtTick = -1;

    /** NBT 键：上一条的<b>剩余</b>刻数（与 {@link #TAG_FLIGHT_LOCKED} 同一个理由，存剩余而不是绝对刻）。 */
    private static final String TAG_WART_STRIP = "ShellWartStrip";

    /**
     * 玩家拿"地狱疣生成器"（{@code wart_on_a_stick}）右击这具被附体的空壳时调用
     * （见 {@code WartOnAStickItem#interactLivingEntity}）。
     * <p>
     * 用户指定："如果玩家尝试对被幸运方块附体的玩家空壳使用'地狱疣生成器'，
     * 则该玩家空壳会在接下来的一秒内失去移动 AI，一秒结束后移除其头盔栏的地狱疣。"
     * <p>
     * 于是这里做两件事：
     * <ol>
     *   <li>{@link #lockMovementFor(int)} —— 就是"失去移动 AI"：走位 goal 每刻读
     *       {@link #isPossessionMovementLocked()} 并据此停步（转头盯人、抽签、出手都不受影响）；</li>
     *   <li>记下 {@code ticks} 刻之后把头盔栏的地狱疣摘掉（在 {@link #tickWartStrip()} 里做）。
     *       之所以要延迟，是因为"挂着地狱疣"本身就是这套 debuff 的来源 ——
     *       立刻摘掉的话，"失去移动 AI"这一秒就没有任何可见的因果了。</li>
     * </ol>
     */
    public void applyWartTrap(int ticks) {
        this.lockMovementFor(ticks);
        this.possessionWartStripAtTick = this.tickCount + Math.max(1, ticks);
    }

    /**
     * 到期就把头盔栏里的地狱疣摘掉。
     * <p>
     * <b>只摘地狱疣</b>（{@code is(Items.NETHER_WART)}）：玩家的头盔栏里本来就可能是别的东西
     * （或者压根没被塞进地狱疣 —— 那说明这次右键其实没生效），那种情况下什么都不该动。
     */
    private void tickWartStrip() {
        if (this.possessionWartStripAtTick < 0 || this.tickCount < this.possessionWartStripAtTick) {
            return;
        }
        this.possessionWartStripAtTick = -1;
        if (!this.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHER_WART)) {
            return;
        }
        this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
            "[附体] 地狱疣脱落: 空壳=#{} 已移除头盔栏的地狱疣", this.getId());
    }

    /**
     * 开始飞行：胸甲栏此刻已经戴着 wings（见 {@code FlightEvent#beginWindUp}），
     * 这里只负责"离地 + 把走位交给飞行 AI"。
     * <p>
     * 抬一下速度是必要的：{@code WingsLayer} 按"是否离地"驱动扇动动画，
     * 脚不离地翅膀就只会平摊着不动。
     */
    public void startFlight() {
        if (this.possessionFlying) {
            return;
        }
        this.possessionFlying = true;
        this.setNoGravity(true);
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.42D, 0.0D));
        this.hasImpulse = true;
    }

    /** 飞行绕圈方向：每 {@code minTicks}~{@code maxTicks} 刻随机翻一次；返回 {@code [±1]}。 */
    double[] flightOrbitStep(int minTicks, int maxTicks) {
        if (--this.possessionFlightOrbitFlipTick <= 0) {
            this.possessionFlightOrbitFlipTick = minTicks + this.getRandom().nextInt(maxTicks - minTicks + 1);
            this.possessionFlightOrbitClockwise = this.getRandom().nextBoolean();
        }
        return new double[] {this.possessionFlightOrbitClockwise ? 1.0D : -1.0D};
    }

    /**
     * 会飞之后：<b>身体一律朝"头看的方向"</b>（也就是目标）。
     *
     * <h2>为什么必须自己掰</h2>
     * 原版这套 {@code tickHeadTurn} 让<b>身体朝移动方向</b>平滑转 —— 它拿
     * {@code x - xo, z - zo}（本刻位移）当朝向依据（见 {@code LivingEntity#tickHeadTurn}）。
     * 而飞行形态的位移是 {@link OrbPossessionFlightAI} 每刻直接写速度推出来的
     * （径向拉距离 + 切向绕圈 + 压高度），于是身体就朝着"漂移方向"、
     * 只有头（{@code yHeadRot}）看着目标 —— 看起来就是"侧着身子从你旁边飘过去"。
     * 更糟的是头相对身体最多只能转 {@code getMaxHeadRotationRelativeToBody()}（默认 75°），
     * 身体偏太多时连头都别不过来。
     *
     * <h2>为什么两端都用同一句就够了</h2>
     * 服务端：{@link OrbPossessionFlightAI} 每刻 {@code setLookAt(目标)}，头已经对着目标，
     * 这里把身体对齐头即可；客户端：头由 {@code ClientboundRotateHeadPacket} 同步过来
     * （服务端头一变就发，另有每 20 刻的保底），客户端照同一句把身体对齐头，
     * 渲染出来的整具身体也就朝着目标了。
     */
    @Override
    protected float tickHeadTurn(float yRot, float animStep) {
        float result = super.tickHeadTurn(yRot, animStep);
        if (this.possessionFlying) {
            float yaw = this.getYHeadRot();
            this.yBodyRot = yaw;
            this.yBodyRotO = yaw;
            this.setYRot(yaw);
            this.yRotO = yaw;
        }
        return result;
    }

    /** 重锤动作的状态（null = 没在打）；状态本体与整套动作都在 {@link OrbPossessionMaceAttack}。 */
    @Nullable
    private OrbPossessionMaceAttack.State possessionMaceState;

    public boolean isMaceSequenceActive() {
        return this.possessionMaceState != null;
    }

    @Nullable
    OrbPossessionMaceAttack.State getMaceState() {
        return this.possessionMaceState;
    }

    void setMaceState(@Nullable OrbPossessionMaceAttack.State state) {
        this.possessionMaceState = state;
    }

    // ====================== 进食（附魔金苹果 / 熟猪排 / 紫颂果） ======================

    /**
     * 触发"再吃一个附魔金苹果"的<b>血量比例</b>：最大生命值的 <b>50%</b>（血量<b>低于</b>它才吃）。
     * <p>原来是写死的 {@code 10.0} 点 —— 那正是"20 点体质（一个满血玩家）的一半"。
     * 用户要求改成按上限的<b>比例</b>算：被附体的玩家有 40 点上限，空壳就低于 <b>20</b> 点才掏苹果
     * （46 点上限则低于 23 点），和玩家自己那套手感一致，而不是不管多少血都在 10 点这条线上吃。
     */
    public static final float APPLE_HEAL_THRESHOLD_FRACTION = 10.0F / 20.0F;

    /**
     * 触发"啃紫颂果"的<b>血量比例</b>：最大生命值的 <b>40%</b>。
     * <p>同上：原来的写死值 {@code 8.0} 点 = 20 点体质的 40%，现在跟着上限走
     * （40 点上限 → 低于 16 点啃紫颂果；这条线以上只吃熟猪排）。
     */
    public static final float CHORUS_FRUIT_THRESHOLD_FRACTION = 8.0F / 20.0F;

    /**
     * 吃这颗苹果用多少秒：<b>原版 1.6 秒的一半</b>（原版 32 刻，这里 16 刻）。
     * <p>
     * 1.21.1 的食用时长写在 {@code FoodProperties.eatSeconds} 里，所以见
     * {@link #createEatingApple()}：复制一份食物数据、把秒数减半即可。
     */
    public static final float APPLE_EAT_SECONDS = 0.8F;

    /**
     * 两次吃附魔金苹果之间的最小间隔（游戏刻）：<b>两分钟</b>。
     * <p>
     * 从"这一次开始吃"算起。生成时那一口也会开始计时，所以刚生成不会立刻连吃第二个。
     */
    public static final int APPLE_COOLDOWN_TICKS = 2400;

    /**
     * 两次吃普通食物（熟猪排/紫颂果）之间的最小间隔（游戏刻）：10 秒。
     * <p>
     * 普通食物不像苹果那么金贵，但不能没有冷却：紫颂果"随时可吃"，而"想吃"的条件里
     * 有一条是"受伤"—— 没有冷却的话空壳会一边挨打一边无限掏果子，彻底不打人。
     */
    public static final int FOOD_COOLDOWN_TICKS = 200;

    /**
     * <b>窒息自救</b>：两次啃"逃命紫颂果"之间的最小间隔（刻）：1 秒
     * （原版给玩家的紫颂果冷却也是 1 秒）。
     * <p>一次啃完如果随机传送没能把它送出方块（周围全是实心），下一秒再啃一颗，不会被卡死。
     */
    private static final int SUFFOCATION_ESCAPE_COOLDOWN_TICKS = 20;

    /**
     * <b>窒息自救</b>用的紫颂果食用时长（秒）：<b>0.5 秒（10 刻）</b>，比原版那 1.6 秒快得多。
     * <p>
     * 理由很直白：卡在方块里是<b>每刻 1 点</b>窒息伤害（原版 {@code LivingEntity#baseTick} 里
     * {@code isInWall() → hurt(inWall, 1)}），原版那 1.6 秒够它掉 30 多点血、基本必死；
     * 压到 0.5 秒才叫"能自救"。数值照 {@link #createEatingApple()} 那套改食物数据。
     */
    private static final float SUFFOCATION_FRUIT_EAT_SECONDS = 0.5F;

    /** 咀嚼期间每把碎屑的颗数（对齐原版 {@code triggerItemUseEffects(…, 5)}）。 */
    private static final int EAT_PARTICLES_PER_BURST = 5;

    /** 吃完那一下的碎屑颗数（对齐原版 {@code triggerItemUseEffects(…, 16)}）。 */
    private static final int EAT_PARTICLES_ON_FINISH = 16;

    /**
     * 附魔金苹果那四个效果的时长（刻）——<b>原版数值</b>，见 {@code Foods.ENCHANTED_GOLDEN_APPLE}。
     * <p>
     * 为什么要写死而不是靠原版吃苹果自动给：见 {@link #applyAppleEffects()}。
     */
    private static final int APPLE_REGEN_TICKS = 400;             // 再生 II：20 秒
    private static final int APPLE_RESISTANCE_TICKS = 6000;       // 抗性提升 I：5 分钟
    private static final int APPLE_FIRE_RESISTANCE_TICKS = 6000;  // 抗火：5 分钟
    private static final int APPLE_ABSORPTION_TICKS = 2400;       // 伤害吸收 IV：2 分钟

    /** 下一次可以开始吃附魔金苹果的游戏刻；-1 = 还没吃过（随时可吃）。不持久化。 */
    private long possessionNextAppleTick = -1L;

    /** 下一次可以开始吃普通食物的游戏刻；-1 = 还没吃过（随时可吃）。不持久化。 */
    private long possessionNextFoodTick = -1L;

    /** 下一次可以啃"逃命紫颂果"（窒息自救）的游戏刻；-1 = 还没啃过。不持久化。 */
    private long possessionNextEscapeFruitTick = -1L;

    /** 生成时那一口附魔金苹果还没吃（由 {@link #applyCoreBackedStats()} 置位，第一刻 tick 消费掉）。 */
    private boolean possessionSpawnApplePending;

    // ====================== 跟玩家一起蹲起（T-bag，用户指定） ======================

    /** 跟蹲一次蹲几下的范围：用户指定"1~2 次"。 */
    private static final int CROUCH_MIMIC_MIN_TIMES = 1;
    private static final int CROUCH_MIMIC_MAX_TIMES = 2;

    /**
     * 跟蹲次数的权重：<b>蹲 1 次的权重是 2 次的 4 倍</b>（用户指定）。
     * <p>于是 1 次 = 4/5 = 80%、2 次 = 1/5 = 20%（见 {@link #rollCrouchMimicTimes()}）。
     */
    private static final int CROUCH_MIMIC_WEIGHT_ONE = 4;
    private static final int CROUCH_MIMIC_WEIGHT_TWO = 1;

    /** 还剩几下没蹲（&gt;0 = 正在跟）。不持久化：读档之后不需要接着蹲。 */
    private int crouchMimicRemaining;

    /** 当前处于"蹲着"（true）还是"站着"（false）那一段。 */
    private boolean crouchMimicDown;

    /** 当前这一段什么时候结束（世界刻）。 */
    private long crouchMimicPhaseEndTick;

    /** 跟的节奏：按住多久 / 松开后停多久（刻），来自发起者（见 {@link CrouchMimic#rhythmOf}）。 */
    private int crouchMimicHoldTicks = CrouchMimic.DEFAULT_HOLD_TICKS;
    private int crouchMimicGapTicks = CrouchMimic.DEFAULT_GAP_TICKS;

    /** 已经看到过的"发起者最近一次蹲下"的世界刻：比它新就说明对方又蹲了一下 → 重置次数。 */
    private long crouchMimicSeenCrouchTick = -1L;

    /** 发起者（那位在蹲的玩家）；null = 没在跟。 */
    @Nullable
    private UUID crouchMimicPartner;

    /** 现在是不是正在"跟着玩家蹲起"（蹲起结束前不索敌，见 {@link #tick()} 里的索敌闸）。 */
    public boolean isCrouchMimicking() {
        return this.crouchMimicRemaining > 0;
    }

    // ---- 饥饿 / 饱和度（照搬玩家那套：回血要烧饱和度和饥饿）----

    /** 饱食度（0~20）：默认 20（不饿）。 */
    private int foodLevel = 20;

    /** 饱和度（0~饱食度）：默认 5，和原版新玩家一致。 */
    private float saturation = 5.0F;

    /** 消耗度：攒过 4 点就折成 1 点饱和度（饱和度没了就折成 1 点饥饿）。 */
    private float exhaustion;

    /** 自然回复的计时器：饱和回复每 10 刻一次、普通回复每 80 刻一次。 */
    private int foodTickTimer;

    private static final String TAG_FOOD_LEVEL = "ShellFoodLevel";
    private static final String TAG_SATURATION = "ShellSaturation";
    private static final String TAG_EXHAUSTION = "ShellExhaustion";

    /**
     * 现在是不是正在吃"食物类"的东西（附魔金苹果 / 熟猪排 / 紫颂果）。
     * <p>
     * 用"正在使用物品 + 那件物品有 FOOD 组件"判定，而不是只看 {@code isUsingItem()}：
     * 拉弓（{@link OrbPossessedAttackEvents} 的射箭事件）同样是"正在使用物品"状态。
     */
    public boolean isEatingFood() {
        return this.isUsingItem() && this.getUseItem().has(DataComponents.FOOD);
    }

    /** 现在是不是正在吃附魔金苹果。 */
    public boolean isEatingApple() {
        return this.isUsingItem() && this.getUseItem().is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    /** 饱食度（0~20）；调试用（{@code /jafa orbinfo}）。 */
    public int getShellFoodLevel() {
        return this.foodLevel;
    }

    /** 饱和度；调试用（{@code /jafa orbinfo}）。 */
    public float getShellSaturation() {
        return this.saturation;
    }

    /** 附魔金苹果的冷却是否已过。 */
    private boolean isAppleReady() {
        return this.possessionNextAppleTick < 0L
            || this.level().getGameTime() >= this.possessionNextAppleTick;
    }

    /**
     * 当前"该吃附魔金苹果"的血量线 = <b>生命上限 × {@link #APPLE_HEAL_THRESHOLD_FRACTION}</b>。
     * <p>跟着生命上限走，所以被附体的玩家有多少上限，这条线就按同比例抬高
     * （用户指定：40 点上限 → 低于 20 点才吃，而不是永远卡在 10 点）。
     */
    public float appleHealThreshold() {
        return this.getMaxHealth() * APPLE_HEAL_THRESHOLD_FRACTION;
    }

    /**
     * 当前"该啃紫颂果"的血量线 = <b>生命上限 × {@link #CHORUS_FRUIT_THRESHOLD_FRACTION}</b>。
     * <p>这条线以上只吃熟猪排（真的饿着才吃），到线以下才改成"随时可吃"的紫颂果。
     */
    public float chorusFruitThreshold() {
        return this.getMaxHealth() * CHORUS_FRUIT_THRESHOLD_FRACTION;
    }

    /** 还活着但没满血（原版 {@code Player#isHurt()} 同款判据）。 */
    private boolean isWounded() {
        return this.getHealth() > 0.0F && this.getHealth() < this.getMaxHealth();
    }

    /**
     * 开始吃一个附魔金苹果（<b>不管冷却</b>，冷却由调用方判断/记账）。
     * 见 {@link #tryStartEating} 了解食用流程与限制。
     */
    public boolean startEatingApple() {
        if (!this.tryStartEating(createEatingApple())) {
            return false;
        }
        this.possessionNextAppleTick = this.level().getGameTime() + APPLE_COOLDOWN_TICKS;
        return true;
    }

    /**
     * 开始吃一样东西 —— 走<b>原版食用流程</b>：东西拿在主手 → {@code startUsingItem}，
     * 到点由原版自己 {@code completeUsingItem} 结算（吃掉物品、挂上该食物的效果）。
     * <p>
     * 起手前会把手上正在进行的动作收掉：拉弓/举矛这类"起手"占用的是使用状态与主手，
     * 不收掉的话 {@code startUsingItem} 会被原版静默忽略（它有一道 {@code !isUsingItem()} 的前置判断）；
     * 而且原版每刻都会核对"手里拿的是不是正在用的那件"，不一致就当场取消食用。
     * <p>
     * 食用期间 {@link #tick()} 会整个跳过（不索敌、不抽签、不出手），见那里的注释。
     *
     * @return 是否真的开始吃了
     */
    private boolean tryStartEating(ItemStack food) {
        if (this.level().isClientSide || !this.isOrbAttached()) {
            return false;
        }
        if (this.isUsingItem()) {
            return false;   // 已经在吃/在用别的东西：不打断
        }
        this.cancelPossessionWindUp();
        if (this.isUsingItem()) {
            this.stopUsingItem();   // 兜底：起手事件没把自己收回使用状态的，这里强制收
        }
        this.clearPossessionHands();
        this.setItemInHand(InteractionHand.MAIN_HAND, food);
        this.startUsingItem(InteractionHand.MAIN_HAND);
        return this.isEatingFood() || this.isEatingApple();
    }

    /**
     * 血量低于 {@link #appleHealThreshold()}（<b>生命上限的 50%</b>）且冷却已过 → 再吃一个附魔金苹果。
     * <p>
     * 由 {@link #tick()} 每刻调用（在索敌与普通食物之前），所以有目标没目标都会吃；
     * 和普通食物撞车时<b>附魔金苹果优先</b>。
     */
    private void tryEatAppleWhenHurt() {
        if (this.getHealth() >= this.appleHealThreshold()) {
            return;
        }
        if (!this.isAppleReady()) {
            return;
        }
        this.startEatingApple();
    }

    /**
     * 该吃普通食物了就去吃：
     * <ul>
     *   <li><b>饥饿值 ≤ 19 且血量 &gt; 8</b> → {@link Items#COOKED_PORKCHOP 熟猪排}
     *       （+8 饥饿 / +12.8 饱和度）；</li>
     *   <li><b>否则</b>（已经吃饱所以猪排吃不下、或者血太少想先垫一口）→
     *       {@link Items#CHORUS_FRUIT 紫颂果}（+4 饥饿 / +2.4 饱和度，原版"随时可吃"）。</li>
     * </ul>
     * 只在"真需要"时才掏东西：
     * <ul>
     *   <li>受伤时，只有自然回复的燃料真的不够了才吃（饱和回复要"饱和度 &gt; 0 且饥饿满"，
     *       普通回复要"饥饿 ≥ 18"）—— 不然它会在饱和回复正跑着的时候没完没了地啃果子；</li>
     *   <li>没受伤时，饥饿没满就补满（下一场架开打时燃料是现成的）。</li>
     * </ul>
     * 没有这层门槛 + {@link #FOOD_COOLDOWN_TICKS} 冷却的话，紫颂果随时可吃这一点
     * 会让它一边挨打一边无限进食。
     */
    private void tryEatFoodWhenNeeded() {
        if (this.isWounded()) {
            // 两种自然回复都还转得起来 → 不用吃
            if (this.saturation > 0.0F && this.foodLevel >= 18) {
                return;
            }
        } else if (this.foodLevel > 19) {
            return;
        }
        if (this.possessionNextFoodTick >= 0L
            && this.level().getGameTime() < this.possessionNextFoodTick) {
            return;
        }
        // 吃哪样：血量在紫颂果线以上只吃熟猪排（且必须真的饿着，否则它吃不下）；
        // 掉到紫颂果线以下才啃紫颂果；血量还够、又吃饱了 → 什么都不吃。
        // 这条线是<b>生命上限的 40%</b>（原来是写死的 8 点，见 CHORUS_FRUIT_THRESHOLD_FRACTION）。
        // 注意不能写成"饿了且血多就猪排、否则紫颂果"：那样吃饱了（饥饿 20）但掉了一点血时
        // 会去啃紫颂果，而紫颂果随时可吃 —— 表现就是"血量在线以上还在吃紫颂果"。
        ItemStack food;
        float chorusLine = this.chorusFruitThreshold();
        if (this.foodLevel <= 19 && this.getHealth() > chorusLine) {
            food = new ItemStack(Items.COOKED_PORKCHOP);
        } else if (this.getHealth() <= chorusLine) {
            food = new ItemStack(Items.CHORUS_FRUIT);
        } else {
            return;
        }
        if (!this.tryStartEating(food)) {
            return;
        }
        this.possessionNextFoodTick = this.level().getGameTime() + FOOD_COOLDOWN_TICKS;
    }

    /**
     * <b>被窒息时的自救（用户指定）：掏一颗紫颂果吃掉，借原版那一下随机传送逃出方块。</b>
     *
     * <h2>判据</h2>
     * 用原版 {@code LivingEntity#isInWall()} —— 这正是窒息伤害的同一个判据
     * （{@code LivingEntity#baseTick} 里就是 {@code if (isInWall()) hurt(inWall, 1)}），
     * 所以"正在掉窒息血"和"触发自救"永远是同一件事，不会出现"明明在挨窒息却无动于衷"。
     *
     * <h2>优先级</h2>
     * 排在所有进食之上（见 {@link #tick()} 的调用点），连"正在吃的东西"都会被打断：
     * 卡在方块里是每刻 1 点伤害，半颗附魔金苹果没有一条命值钱。
     * 紫颂果原版就"随时可吃"，不受饥饿值限制，所以不需要走饥饿那套门槛。
     *
     * <h2>吃完之后</h2>
     * 原版 {@code ChorusFruitItem#finishUsingItem} 会试最多 16 次随机传送（±8 格）——
     * 成功就出去了；16 次全失败（四周全是实心）说明还在墙里，
     * {@link #SUFFOCATION_ESCAPE_COOLDOWN_TICKS} 之后会再啃一颗，直到出去为止。
     *
     * @return 是否真的开始啃了（调用方据此 return，本刻不再做别的）
     */
    private boolean tryEscapeSuffocationWithChorusFruit() {
        if (!this.isInWall()) {
            return false;
        }
        if (this.possessionNextEscapeFruitTick >= 0L
            && this.level().getGameTime() < this.possessionNextEscapeFruitTick) {
            return false;
        }
        // 打断手上正在进行的"使用"（正在啃的附魔金苹果/猪排、正在拉的弓……）：
        // tryStartEating 有一条"已经在用别的东西就不打断"的前置判断，所以必须先主动停。
        // 例外：已经在啃逃命紫颂果 → 让它啃完（原版要等吃完那一下才会结算传送），
        // 否则这里会每刻"停下 → 重开"，永远吃不到嘴。
        if (this.isUsingItem()) {
            if (this.getUseItem().is(Items.CHORUS_FRUIT)) {
                return false;
            }
            this.stopUsingItem();
        }
        if (!this.tryStartEating(createEscapeChorusFruit())) {
            return false;
        }
        this.possessionNextEscapeFruitTick = this.level().getGameTime() + SUFFOCATION_ESCAPE_COOLDOWN_TICKS;
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
            "[附体] 空壳被卡在方块里（窒息），啃紫颂果逃命: #{}", this.getId());
        return true;
    }

    /**
     * 造一颗"吃得快"的紫颂果：只把食用时长压到 {@link #SUFFOCATION_FRUIT_EAT_SECONDS}，
     * 营养/饱和度/随时可吃/用后转化物全部照旧（写法与 {@link #createEatingApple()} 一致）。
     * <p>吃的时长是这套自救的关键：原版 1.6 秒够它在墙里掉 30 多点血，0.5 秒才救得回来。
     */
    private static ItemStack createEscapeChorusFruit() {
        ItemStack fruit = new ItemStack(Items.CHORUS_FRUIT);
        FoodProperties food = fruit.get(DataComponents.FOOD);
        if (food != null) {
            fruit.set(DataComponents.FOOD, new FoodProperties(
                food.nutrition(), food.saturation(), food.canAlwaysEat(),
                SUFFOCATION_FRUIT_EAT_SECONDS, food.usingConvertsTo(), food.effects()));
        }
        return fruit;
    }

    // ====================== 跟玩家一起蹲起（T-bag，用户指定） ======================

    /**
     * <b>跟着附近的玩家一起蹲起（T-bag）</b>（用户指定）：
     * <ul>
     *   <li><b>触发条件</b>：自己<b>没有索敌目标</b>，且 {@value CrouchMimic#NEARBY_RADIUS} 格内有玩家
     *       正在蹲起（时间窗内蹲起 ≥2 次才算，单纯按着 Shift 走路不算）；</li>
     *   <li><b>动作</b>：跟着蹲 {@value #CROUCH_MIMIC_MIN_TIMES}~{@value #CROUCH_MIMIC_MAX_TIMES} 次
     *       （按下 → 等待 → 松开 算一次；<b>等待时长照着发起者来</b>，见 {@link CrouchMimic#rhythmOf}）。
     *       蹲几次按权重抽：<b>1 次的权重是 2 次的 4 倍</b>（80% 蹲 1 次、20% 蹲 2 次，用户指定），
     *       见 {@link #rollCrouchMimicTimes()}；</li>
     *   <li><b>重置</b>：跟蹲期间对方<b>又蹲了一下</b>（而且在附近）→ 次数按权重重新抽一次
     *       （回到 1~2 次），节奏也跟着更新 —— 于是两个人会一直蹲到其中一个停下；</li>
     *   <li><b>看人</b>：蹲的时候<b>一直看着那位玩家</b>（用户指定）；附近同时有好几个人在蹲时，
     *       一开始就<b>随机挑一位</b>（见 {@link CrouchMimic#randomTBaggingPlayer}）——
     *       中途那位走远/下线了，会从剩下还在蹲的人里再随机挑一个接着看；</li>
     *   <li><b>索敌闸</b>：从开始跟到蹲完，<b>不重新索敌</b>（见 {@link #tick()} 里
     *       {@link #isCrouchMimicking()} 那道判断），所以不会"蹲到一半突然去打人"。</li>
     * </ul>
     * <p>
     * "蹲"的表现就是原版的 {@link Pose#CROUCHING} 姿势：碰撞箱跟着缩到 0.6 × 1.5、
     * 渲染的模型也是蹲姿（{@code PlayerShellRenderer#setModelProperties} 读 {@code isCrouching()}），
     * 与真人玩家完全一致。
     */
    private void tickCrouchMimic() {
        if (this.level().isClientSide) {
            return;
        }
        long now = this.level().getGameTime();
        // 重锤动作（含落地水自救）期间不掺和：那一套自己控姿势与装备
        if (this.possessionMaceState != null) {
            this.stopCrouchMimic();
            return;
        }

        if (this.crouchMimicRemaining > 0) {
            // ---- 正在跟：先看发起者有没有"又蹲了一下" → 重置次数（用户指定）----
            ServerPlayer partner = this.crouchMimicPartner == null ? null
                : this.level().getServer() == null ? null
                : this.level().getServer().getPlayerList().getPlayer(this.crouchMimicPartner);
            if (partner == null || !CrouchMimic.isNearby(this, partner)) {
                // 人走了/下线/跑远了：节奏留着把这几下蹲完，但看看附近还有没有别人在蹲 ——
                // 有就换成他（用户指定：多个蹲起的玩家时随机看其中一个），没有就先不看了。
                this.crouchMimicPartner = null;
                partner = CrouchMimic.randomTBaggingPlayer(this);
                if (partner != null) {
                    this.crouchMimicPartner = partner.getUUID();
                    this.crouchMimicSeenCrouchTick = CrouchMimic.lastCrouchTick(partner);
                    CrouchMimic.Rhythm rhythm = CrouchMimic.rhythmOf(partner);
                    this.crouchMimicHoldTicks = rhythm.holdTicks();
                    this.crouchMimicGapTicks = rhythm.gapTicks();
                }
            } else {
                // 发起者又蹲了一下 → 次数按权重重抽（用户指定），节奏也跟着更新
                long lastCrouch = CrouchMimic.lastCrouchTick(partner);
                if (lastCrouch > this.crouchMimicSeenCrouchTick) {
                    this.crouchMimicSeenCrouchTick = lastCrouch;
                    this.crouchMimicRemaining = this.rollCrouchMimicTimes();
                    CrouchMimic.Rhythm rhythm = CrouchMimic.rhythmOf(partner);
                    this.crouchMimicHoldTicks = rhythm.holdTicks();
                    this.crouchMimicGapTicks = rhythm.gapTicks();
                }
            }
            // <b>蹲的时候一直看着那位玩家</b>（用户指定）：与"有索敌目标就盯着目标"用的是同一个
            // LookControl，头（以及玩家模型的朝向）会转过去。
            if (partner != null) {
                this.getLookControl().setLookAt(partner, 30.0F, 30.0F);
            }

            if (now < this.crouchMimicPhaseEndTick) {
                return;
            }
            if (this.crouchMimicDown) {
                // 这一段蹲完了：站起来，进入"停一会儿"那段
                this.setCrouchingMimic(false);
                this.crouchMimicDown = false;
                this.crouchMimicPhaseEndTick = now + this.crouchMimicGapTicks;
                if (--this.crouchMimicRemaining <= 0) {
                    this.stopCrouchMimic();
                }
            } else {
                // 停够了：蹲下去
                this.setCrouchingMimic(true);
                this.crouchMimicDown = true;
                this.crouchMimicPhaseEndTick = now + this.crouchMimicHoldTicks;
            }
            return;
        }

        // ---- 没在跟：看看要不要开始 ----
        // 条件：没有索敌目标 + 会飞的状态下不掺和（躺平滑翔时蹲不了）+ 附近有正在蹲起的玩家
        if (this.getTarget() != null || !this.isOrbAttached() || this.isFallFlying()) {
            return;
        }
        ServerPlayer partner = CrouchMimic.randomTBaggingPlayer(this);
        if (partner == null) {
            return;
        }
        CrouchMimic.Rhythm rhythm = CrouchMimic.rhythmOf(partner);
        this.crouchMimicPartner = partner.getUUID();
        this.crouchMimicHoldTicks = rhythm.holdTicks();
        this.crouchMimicGapTicks = rhythm.gapTicks();
        this.crouchMimicSeenCrouchTick = CrouchMimic.lastCrouchTick(partner);
        this.crouchMimicRemaining = this.rollCrouchMimicTimes();
        this.crouchMimicDown = true;
        this.crouchMimicPhaseEndTick = now + this.crouchMimicHoldTicks;
        this.setCrouchingMimic(true);
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
            "[附体] 附近有玩家在蹲起，空壳跟着蹲 {} 次（发起者={}，节奏 {} 刻/{} 刻）: #{}",
            this.crouchMimicRemaining, partner.getGameProfile().getName(),
            this.crouchMimicHoldTicks, this.crouchMimicGapTicks, this.getId());
    }

    /**
     * 这一轮跟蹲蹲几下：<b>1 次的权重是 2 次的 4 倍</b>（用户指定）。
     * <p>
     * 也就是在 4 + 1 = 5 份里抽：落在前 4 份 → 蹲 1 次（80%），落在最后 1 份 → 蹲 2 次（20%）。
     * 开蹲那一刻抽一次；跟蹲期间对方"又蹲了一下"时也用它重抽（用户指定"重置蹲起次数计数"）。
     */
    private int rollCrouchMimicTimes() {
        int roll = this.getRandom().nextInt(CROUCH_MIMIC_WEIGHT_ONE + CROUCH_MIMIC_WEIGHT_TWO);
        return roll < CROUCH_MIMIC_WEIGHT_ONE ? CROUCH_MIMIC_MIN_TIMES : CROUCH_MIMIC_MAX_TIMES;
    }

    /** 把姿势切成"蹲着/站着"（碰撞箱跟着换，见 {@link #getDefaultDimensions}）。 */
    private void setCrouchingMimic(boolean down) {        Pose want = down ? Pose.CROUCHING : Pose.STANDING;
        if (this.getPose() != want) {
            this.setPose(want);
            this.refreshDimensions();
        }
    }

    /** 收尾：站起来、把跟蹲状态清干净（最后一次"松开"已经站起来的话这里只清状态）。 */
    private void stopCrouchMimic() {
        this.crouchMimicRemaining = 0;
        this.crouchMimicDown = false;
        this.crouchMimicPhaseEndTick = 0L;
        this.crouchMimicPartner = null;
        this.setCrouchingMimic(false);
    }

    /**
     * 每刻维护"饥饿 / 饱和度"，并按原版玩家的规则自然回复。
     * <p>
     * 照搬 {@code FoodData#tick}：
     * <ul>
     *   <li>消耗度攒过 4 点 → 折掉 4 点消耗度：先扣 1 点饱和度，饱和度没了再扣 1 点饥饿；</li>
     *   <li><b>饱和回复</b>：饱和度 &gt; 0 且饥饿满 20 且受伤 → 每 10 刻回
     *       {@code min(饱和度, 6) / 6} 点血，并消耗等量饱和度（通过消耗度）；</li>
     *   <li><b>普通回复</b>：饥饿 ≥ 18 且受伤 → 每 80 刻回 1 点血，消耗 6 点消耗度。</li>
     * </ul>
     * 与玩家的唯一区别：<b>不做饥饿伤害</b>（饿到 0 只是回不了血）。
     * 这具空壳的战斗节奏本来就归抽签管，再叠一层饿死没有意义。
     */
    private void tickFoodAndNaturalRegen() {
        if (this.exhaustion > 4.0F) {
            this.exhaustion -= 4.0F;
            if (this.saturation > 0.0F) {
                this.saturation = Math.max(this.saturation - 1.0F, 0.0F);
            } else {
                this.foodLevel = Math.max(this.foodLevel - 1, 0);
            }
        }

        boolean naturalRegen = this.level().getGameRules()
            .getBoolean(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION);
        boolean wounded = this.isWounded();
        if (naturalRegen && this.saturation > 0.0F && wounded && this.foodLevel >= 20) {
            if (++this.foodTickTimer >= 10) {
                float spent = Math.min(this.saturation, 6.0F);
                this.heal(spent / 6.0F);
                this.addExhaustion(spent);
                this.foodTickTimer = 0;
            }
        } else if (naturalRegen && this.foodLevel >= 18 && wounded) {
            if (++this.foodTickTimer >= 80) {
                this.heal(1.0F);
                this.addExhaustion(6.0F);
                this.foodTickTimer = 0;
            }
        } else {
            this.foodTickTimer = 0;
        }
    }

    /** 攒消耗度（原版 {@code FoodData#addExhaustion}，上限 40）。 */
    private void addExhaustion(float amount) {
        this.exhaustion = Math.min(this.exhaustion + amount, 40.0F);
    }

    /**
     * 吃东西补饥饿 / 饱和度 —— 照搬 {@code FoodData#eat}。
     * <p>
     * 这一步在<b>玩家</b>身上是 {@code Player#eat → FoodData#eat} 做的，
     * 而我们的空壳走的是 {@code LivingEntity#eat}（只给效果、不给饥饿），所以这里自己补上。
     * 注意 {@code FoodProperties#saturation()} 已经是"营养 × 系数 × 2"的成品数值。
     */
    private void eatFood(FoodProperties food) {
        int nutrition = food.nutrition();
        this.foodLevel = Math.min(this.foodLevel + nutrition, 20);
        this.saturation = Math.min(this.saturation + food.saturation(), (float) this.foodLevel);
    }

    /**
     * 造一颗"吃得快"的附魔金苹果：食用时长压到 {@link #APPLE_EAT_SECONDS}
     * （原版 1.6 秒 → 这里 0.8 秒 = 16 刻）。
     * <p>
     * 做法就是复制原版的食物数据组件、只改秒数：营养、效果、是否随时可吃、
     * 用后转化物全部照旧（效果时长由 {@link #applyAppleEffects()} 另行钉死）。
     */
    private static ItemStack createEatingApple() {
        ItemStack apple = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
        FoodProperties food = apple.get(DataComponents.FOOD);
        if (food != null) {
            apple.set(DataComponents.FOOD, new FoodProperties(
                food.nutrition(), food.saturation(), food.canAlwaysEat(),
                APPLE_EAT_SECONDS, food.usingConvertsTo(), food.effects()));
        }
        return apple;
    }

    /**
     * 把附魔金苹果那四个效果钉在<b>原版时长</b>上（再生 II 20 秒 / 抗性提升 I 5 分钟 /
     * 抗火 5 分钟 / 伤害吸收 IV 2 分钟）。
     * <p>
     * 为什么不干脆靠原版吃苹果自动给：
     * <ul>
     *   <li>原版 {@code MobEffectInstance#update} 对已有同名效果<b>只延长不缩短</b>，
     *       吃第二颗苹果时时长会接着旧的走、不精确；这里先移除再挂，保证"每次都正好是原版时长"；</li>
     *   <li>同时把数值写死，任何别处对药水时长的改动都影响不到这四个效果。</li>
     * </ul>
     */
    private void applyAppleEffects() {
        this.forceEffect(net.minecraft.world.effect.MobEffects.REGENERATION,
            APPLE_REGEN_TICKS, 1);
        this.forceEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,
            APPLE_RESISTANCE_TICKS, 0);
        this.forceEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE,
            APPLE_FIRE_RESISTANCE_TICKS, 0);
        this.forceEffect(net.minecraft.world.effect.MobEffects.ABSORPTION,
            APPLE_ABSORPTION_TICKS, 3);
    }

    /** 先移除再挂：确保时长/等级就是我们指定的那一份（见 {@link #applyAppleEffects()}）。 */
    private void forceEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                             int ticks, int amplifier) {
        this.removeEffect(effect);
        this.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, true));
    }

    /**
     * 播放"吃东西"的食物碎屑粒子。
     * <p>
     * 原版 {@code LivingEntity#spawnItemParticles} 走的是 {@code Level#addParticle}，
     * 而 1.21.1 里那个方法在<b>服务端是空实现</b>（只有客户端 {@code ClientLevel} 真生成粒子）——
     * 玩家自己吃能看见粒子，是因为本地客户端也在跑同一段逻辑。
     * 空壳是纯服务端实体，所以必须自己把粒子包发出去：{@code ServerLevel#sendParticles}
     * 会把粒子发给周围 32 格内的玩家。
     * <p>
     * 位置取"嘴部"（眼睛稍下、朝前一点），碎屑随机散开并下落，观感与原版一致。
     */
    private void spawnEatParticles(ItemStack icon, int count) {
        if (icon.isEmpty() || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 look = this.getLookAngle();
        double x = this.getX() + look.x * 0.35;
        double y = this.getEyeY() - 0.15 + look.y * 0.35;
        double z = this.getZ() + look.z * 0.35;
        serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, icon),
            x, y, z, count, 0.12, 0.1, 0.12, 0.02);
    }

    @Override
    protected void completeUsingItem() {
        // 吃完那一下原版会补一发碎屑（triggerItemUseEffects(…, 16)），但那同样走
        // Level#addParticle（服务端空实现），所以这里自己补一发。
        // 先留一份"还没被吃掉"的副本：super 之后手里那份会被 shrink 成空栈，
        // 而空栈不能拿来构造 ItemParticleOption。
        ItemStack used = this.getUseItem().copyWithCount(1);
        FoodProperties food = used.isEmpty() ? null : used.get(DataComponents.FOOD);
        boolean apple = used.is(Items.ENCHANTED_GOLDEN_APPLE);
        super.completeUsingItem();
        if (food != null) {
            // 玩家吃任何东西都会补饥饿/饱和度（Player#eat），空壳走的是 LivingEntity#eat，这里补上
            this.eatFood(food);
        }
        if (apple) {
            this.applyAppleEffects();
            this.spawnEatParticles(used, EAT_PARTICLES_ON_FINISH);
        }
    }

    /**
     * 把这具空壳配置成"由幸运核心支撑"的形态：
     * <ul>
     *   <li>解除 {@code noAi}、装上骷髅式走位与抽签攻击（{@link #enablePossessionCombat()}）；</li>
     *   <li>头顶渲染幸运核心光球（{@link #setOrbAttached}）；</li>
     *   <li>核心同款体质（见 {@link #applyCoreBackedStats()}）+ 附魔金苹果回血
     *       （生成时先吃一个，之后血量低于上限的 50% 再吃）；</li>
     *   <li>不自然消失。</li>
     * </ul>
     * 被<b>同化</b>产生的那一具（{@link OrbAssimilation}）与刷怪蛋放出来的那一具都走这里，
     * 保证它们和附体主空壳除了"玩家外观 / 同化关系"之外完全一致
     * （附体主空壳由 {@code OrbOfLuckEntity#spawnVessel} 直接调 {@link #applyCoreBackedStats()}，
     * 不走这里 —— 它不需要再设一遍"头顶光球 + 属于哪颗核心"）。
     */
    public void configureAsPossessedShell() {
        this.configureAsPossessedShell(CORE_BACKED_MAX_HEALTH);
    }

    /**
     * 同上，但<b>生命上限按调用方给的值</b>（= 被附体/同化前那位玩家的最大生命值，用户指定）。
     * <p>
     * 用于"有明确来源玩家"的两条路：附体（{@code OrbOfLuckEntity#spawnVessel}）与
     * 同化（{@link OrbAssimilation#assimilate}）。刷怪蛋没有来源玩家，走无参那条。
     */
    public void configureAsPossessedShell(double playerMaxHealth) {
        this.enablePossessionCombat();
        this.setPersistenceRequired();
        this.setOrbAttached(true);
        this.applyCoreBackedStats(playerMaxHealth);
    }

    /**
     * "由幸运核心支撑"的空壳的体质：
     * <ul>
     *   <li><b>生命值上限 = 被附体/同化前那位玩家的最大生命值</b>（用户指定），并拉满
     *       —— 无参那条重载退回 {@link #CORE_BACKED_MAX_HEALTH}（20 点，= 一个满血玩家）；
     *   <li>自带护甲 {@link #CORE_BACKED_ARMOR}（20 点）与护甲韧性 {@link #CORE_BACKED_ARMOR_TOUGHNESS}（12 点）；</li>
     *   <li>自带"保护"状态效果 {@link #CORE_BACKED_PROTECTION_LEVEL} 级 —— 走的是本模组
     *       "魔咒状态效果"那套结算（{@code protection} 占位效果按名字映射成
     *       {@code minecraft:protection}，见 {@code ModMain#handleEnchantEffectsOnHit}），
     *       全类型减伤 {@code min(20, 等级) / 25}（16 级 = 64%）。</li>
     *   <li><b>生成时立刻吃一个附魔金苹果</b>（见 {@link #startEatingApple()}）——
     *       不论此刻有没有索敌目标，吃完才开始索敌。</li>
     * </ul>
     * 幂等：重复调用只是把属性/血量/效果刷成同一套值，不会叠加。
     * <p>
     * 属性与效果都会随实体存档走，读档后不需要重新配置。
     * <p>
     * <b>三个生成入口都走这里</b>（附体主空壳见 {@code OrbOfLuckEntity#spawnVessel}，
     * 被同化的与刷怪蛋放的走 {@link #configureAsPossessedShell()}），
     * 所以"无论生成方式都先吃一口"是自动成立的。
     */
    public void applyCoreBackedStats() {
        this.applyCoreBackedStats(CORE_BACKED_MAX_HEALTH);
    }

    /**
     * 同上，但<b>生命上限按调用方给的值</b>：被附体/同化<b>之前</b>那位玩家的最大生命值
     * （用户指定：玩家原本多少上限，这具空壳就是多少上限）。
     * <p>
     * 于是"这具壳有多耐打"直接跟着被附体的玩家走：40 点上限的玩家 → 40 点血的空壳，
     * 上面那条"低于上限一半就吃附魔金苹果"的血线也随之抬到 20 点
     * （见 {@link #appleHealThreshold()}）。
     * <p>
     * 生命上限是<b>生成那一刻的快照</b>：之后玩家换装备/上状态把自己的上限改了，不会回头再改这具壳
     * （属性随实体存档，读档也不会重算）。除非上游给的是 0/负数/NaN 这种离谱值，
     * 否则不做任何"修正" —— 玩家有多少就给多少。
     */
    public void applyCoreBackedStats(double playerMaxHealth) {
        // 由幸运核心产生的空壳：致命打击额度用默认值（每具壳一副新额度）
        this.possessionFatalHits = DEFAULT_FATAL_HITS;
        // 上游给的值先兜一层：NaN/无穷/≤0 都退回默认 20（正常玩家不会给出这种值）
        double maxHealthValue = Double.isFinite(playerMaxHealth) && playerMaxHealth > 0.0D
            ? playerMaxHealth : CORE_BACKED_MAX_HEALTH;
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(maxHealthValue);
        }
        this.setHealth((float) maxHealthValue);
        AttributeInstance armor = this.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(CORE_BACKED_ARMOR);
        }
        AttributeInstance toughness = this.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughness != null) {
            toughness.setBaseValue(CORE_BACKED_ARMOR_TOUGHNESS);
        }
        // 保护状态效果：不显示粒子、不显示图标（和核心那套"魔咒状态效果"一样藏起来）
        this.addEffect(new MobEffectInstance(ModMobEffects.PROTECTION, Integer.MAX_VALUE,
            CORE_BACKED_PROTECTION_LEVEL - 1, false, false, false));
        // 每加一个"魔咒状态效果"，ModMain 的 MobEffectEvent.Added 都会顺手给实体挂上"自体附魔"标记，
        // 客户端据此在模型上叠一层紫红附魔光晕（EnchantGlintLayer）。空壳是"玩家的身体"，
        // 不该凭空多出一层光效，这里把标记撤掉 —— 减伤结算只看效果本身，不受影响。
        // 本方法只在空壳进世界之前调用（见三处调用点），所以此刻还没有任何客户端被告知过"已附魔"，
        // 清标记即可，不必再补一个广播。
        // （要那层光效：把下面这三行删掉。）
        if (ModMain.getEnchantSelf(this)) {
            ModMain.setEnchantSelf(this, false);
        }
        // 生成时的那一口附魔金苹果：这里只挂个标记，真正的"开始吃"放在第一刻 tick 里
        // （食用走的是原版物品使用流程，让它在实体已经进世界之后再开始更稳妥）。
        this.possessionSpawnApplePending = true;
    }

    /**
     * 保证"核心同款体质"里那个<b>保护</b>效果一直在。
     * <p>
     * 它只是个普通状态效果，会被奶桶/牛奶之类"清除全部效果"的手段顺手清掉
     * （空壳自己丢奶桶解凋灵/中毒时就会连它一起清掉），所以每 20 刻核对一次、缺了就补回来。
     * 只补这一个效果：不回血、不动别的效果 —— 免得变成"用奶桶回满血"的漏洞。
     */
    private void ensureCoreBackedProtection() {
        if (!this.isOrbAttached() || this.hasEffect(ModMobEffects.PROTECTION)) {
            return;
        }
        this.addEffect(new MobEffectInstance(ModMobEffects.PROTECTION, Integer.MAX_VALUE,
            CORE_BACKED_PROTECTION_LEVEL - 1, false, false, false));
    }

    /** 跳跃助力（爬坎/后退被拦）两次之间的最短间隔（刻）。 */
    private static final int JUMP_ASSIST_COOLDOWN_TICKS = 8;

    // ====================== 变形（transmutation）相关状态 ======================

    /**
     * "刚被解除变形"的宽限刻数：这段时间内，变形解药特攻<b>零帧起手</b>
     * （见 {@code OrbPossessedAttackEvents.TransmutationAntidoteEvent#windUpTicks(LivingEntity)}）。
     */
    public static final int UNTRANSMUTED_GRACE_TICKS = 100;

    /** 被解除变形的时刻（游戏刻）；没被解除过就是 {@link Long#MIN_VALUE}。不持久化（"刚"是瞬时状态）。 */
    private long possessionUntransmutedTick = Long.MIN_VALUE;

    /**
     * 对变形药水的免疫是否已被移除（历史遗留标记）。
     * <p>
     * 早期实现里"核心骑在身上 = 免疫变形药水"，用户后来指定"被解除过一次变形之后就不再免疫"。
     * 现在<b>附体主空壳一律不免疫</b>（见 {@code ModMain#isTransmutationPotionImmune}），
     * 这个标记因此不再参与免疫判定；但仍随存档落盘，只为旧存档读写字段保持一致。
     */
    private boolean possessionTransmutationImmuneRemoved;

    private static final String TAG_TRANSMUTE_IMMUNE_REMOVED = "ShellTransmuteImmuneRemoved";

    /** NBT 键：已经会飞（"飞行"特攻生效过）。读档后接着飞。 */
    private static final String TAG_FLYING = "ShellFlying";
    /** "被打落"的剩余刻数（见 {@link #possessionFlightLockedUntilTick}）。 */
    private static final String TAG_FLIGHT_LOCKED = "ShellFlightLocked";

    /** 持久化键：被"支援幸运方块"波及、失去 AI 的截止时刻（{@code gameTime}，需求 6.5.21）。 */
    private static final String TAG_PILOT_STUN_UNTIL = "jafa_pilot_stun_until";

    /** 持久化键：被波及之前的 {@code NoAI} 值（到点原样还回去，别把本来就不会动的木桩"治好"）。 */
    private static final String TAG_PILOT_STUN_PREV_NO_AI = "jafa_pilot_stun_prev_noai";
    /** 致命打击额度（见 {@link #possessionFatalHits}）。 */
    private static final String TAG_FATAL_HITS = "ShellFatalHits";

    /** 是不是"刚被解除变形"（宽限期内）。 */
    public boolean isJustUntransmuted() {
        return this.possessionUntransmutedTick != Long.MIN_VALUE
            && this.level().getGameTime() - this.possessionUntransmutedTick <= UNTRANSMUTED_GRACE_TICKS;
    }

    // ---- 变形解药特攻的"零帧起手"判定 ----

    /** 两次"看到变形药水"间隔超过这么多刻，就算作新一轮的<b>首次发现</b>（上升沿）。 */
    public static final int TRANSMUTATION_SIGHTING_GAP_TICKS = 40;

    /** 上一次在 10 格内看到变形药水的时刻，见 {@link #noteTransmutationPotionSighting(long)}。 */
    private long possessionLastTransmutationSightingTick = Long.MIN_VALUE;

    /** 本 tick 抽到变形解药时，这一次要不要零帧起手。 */
    private boolean possessionAntidoteInstant;

    /**
     * 抽签到变形解药时调用：记下"这一刻看到变形药水了"，并判定这一次是不是<b>首次发现</b>。
     * <p>
     * 首次发现（距上次发现超过 {@link #TRANSMUTATION_SIGHTING_GAP_TICKS} 刻）与"刚被解除变形"
     * 一样走<b>零帧起手</b>；持续看到药水的后续几发则按常规 0.5 秒起手（不然会连珠炮一样瞬间连丢）。
     * 判定结果存到 {@link #isAntidoteInstant()}，由事件的 {@code windUpTicks(attacker)} 读取 ——
     * 两者在同一刻先后执行，所以来得及。
     */
    public void noteTransmutationPotionSighting(long gameTime) {
        boolean first = this.possessionLastTransmutationSightingTick == Long.MIN_VALUE
            || gameTime - this.possessionLastTransmutationSightingTick > TRANSMUTATION_SIGHTING_GAP_TICKS;
        this.possessionLastTransmutationSightingTick = gameTime;
        this.possessionAntidoteInstant = first || this.isJustUntransmuted();
    }

    /** 这一发变形解药是不是零帧起手。 */
    public boolean isAntidoteInstant() {
        return this.possessionAntidoteInstant;
    }

    /** 对变形药水的免疫是否已被移除。 */
    public boolean isTransmutationImmuneRemoved() {
        return this.possessionTransmutationImmuneRemoved;
    }

    /**
     * 被解除变形时调用（见 {@code ModMain#respawnTransmutedEntity}）：
     * 记下"刚刚变回来"，并<b>永久移除</b>对变形药水的免疫（用户指定）。
     */
    public void markUntransmuted() {
        this.possessionUntransmutedTick = this.level().getGameTime();
        this.possessionTransmutationImmuneRemoved = true;
    }

    /** 上一次"助力跳"的游戏刻。 */
    private int possessionLastJumpTick = -1000;

    /**
     * 被方块拦住了？跳一下试试 —— 由走位 goal 在"正在后退"或"正前方是一格坎"时调用。
     * <p>
     * 为什么原版那套不管用：空壳的走位是 {@code moveControl.strafe(前后, 左右)}
     * （见 {@link OrbPossessionKiteGoal}），<b>完全绕过寻路</b>；而原版"卡住就跳一下"的逻辑
     * （{@code PathNavigation#doStuckDetection}）只挂在寻路的路上，所以空壳会被方块死死顶住原地磨。
     * 放在 goal 里调还有一个好处：那里<b>直接知道前后/左右的输入</b>，
     * 不必去猜 {@code zza}/{@code xxa} 在 tick 的哪个时刻还留着值。
     * <p>
     * 跳法是直接给竖直初速（与 {@code LivingEntity#jumpFromGround} 一致），不走
     * {@code JumpControl} 的计时；8 刻冷却，免得贴着墙连跳。
     *
     * @param anyHeight true = 不管方块多高都跳（<b>后退</b>被拦时用，用户指定）；
     *                  false = 只有"一格高的坎"才跳（前进时用，两格以上跳上去也没意义）
     */
    public void tryJumpOverObstacle(boolean anyHeight) {
        if (this.level().isClientSide || !this.onGround() || !this.horizontalCollision) {
            return;
        }
        if (this.tickCount - this.possessionLastJumpTick < JUMP_ASSIST_COOLDOWN_TICKS) {
            return;
        }
        if (!anyHeight) {
            LivingEntity target = this.getTarget();
            double dx;
            double dz;
            if (target != null) {
                dx = target.getX() - this.getX();
                dz = target.getZ() - this.getZ();
            } else {
                Vec3 look = this.getLookAngle();
                dx = look.x;
                dz = look.z;
            }
            BlockPos ahead = this.blockPosition().relative(Direction.getNearest(dx, 0.0, dz));
            if (!isOneBlockStep(this.level(), ahead)) {
                return;
            }
        }
        Vec3 movement = this.getDeltaMovement();
        this.setDeltaMovement(movement.x, (double) this.getJumpPower(), movement.z);
        this.hasImpulse = true;
        this.possessionLastJumpTick = this.tickCount;
    }

    /** 这个位置是不是"一格高的坎"：脚那一格挡路，上面两格都是空的（跳一下就能站上去）。 */
    private static boolean isOneBlockStep(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
            && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
            && level.getBlockState(pos.above(2)).getCollisionShape(level, pos.above(2)).isEmpty();
    }

    /**
     * "由幸运核心支撑"的空壳的<b>默认</b>生命值上限（点）：20 —— 也就是一个满血玩家的上限。
     * <p>
     * 现在只在"没有来源玩家"时用（刷怪蛋放的、或调用方没给值）：
     * 附体与同化两条路都会传<b>被附体/同化前那位玩家的最大生命值</b>
     * （见 {@link #applyCoreBackedStats(double)}）。
     */
    public static final double CORE_BACKED_MAX_HEALTH = 20.0;

    /** 上述空壳自带的护甲值（点）。 */
    public static final double CORE_BACKED_ARMOR = 20.0;

    /** 上述空壳自带的护甲韧性（点）。 */
    public static final double CORE_BACKED_ARMOR_TOUGHNESS = 12.0;

    /** 上述空壳自带的"保护"状态效果等级（1 = 保护 I）。 */
    public static final int CORE_BACKED_PROTECTION_LEVEL = 16;

    /**
     * 这具空壳背后那颗<b>母体核心</b>（附体主空壳记在自己字段里，被同化的那一具记在持久化数据里）；
     * 两者皆无（刷怪蛋放出来的那一具、变形药水造的临时壳）返回 null。
     */
    @Nullable
    private OrbOfLuckEntity motherOrb() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        UUID orbUuid = this.possessedOrbUuid != null ? this.possessedOrbUuid : OrbAssimilation.orbOfShell(this);
        if (orbUuid == null) {
            return null;
        }
        return serverLevel.getEntity(orbUuid) instanceof OrbOfLuckEntity orb ? orb : null;
    }

    /**
     * 把"当前索敌目标"报给背后的那颗核心：<b>没见过</b>的目标会让它的附体倒计时 +1 分钟
     * （见 {@link OrbOfLuckEntity#onPossessionTargetAcquired}）。
     * <p>
     * 每 10 刻的索敌都调一次，去重交给核心自己（它记着本次附体见过谁，并且随存档持久化）。
     * <p>
     * <b>两种空壳都报</b>：附体主空壳的归属记在自己字段里，被同化的那一具记在持久化数据里
     * （{@code OrbAssimilation} 的 {@code jafa_assimilated_orb}）—— 后者只要记着母体，
     * 它的倒计时就与母体同步（用户指定）。刷怪蛋放出来的那一具两者皆无，没人可报，
     * 所以它天然没有索敌冷却这条行为（用户指定）。
     */
    private void notifyPossessedOrbOfTarget(@Nullable LivingEntity target) {
        if (target == null) {
            return;
        }
        OrbOfLuckEntity orb = this.motherOrb();
        if (orb != null) {
            orb.onPossessionTargetAcquired(target.getUUID());
        }
    }

    /**
     * 每刻上报"<b>手上还有没有活的目标</b>"：有就把母体的索敌冷却顶回满值
     * （见 {@link OrbOfLuckEntity#onPossessionTargetHeld}）。
     * <p>
     * 冷却只在这具空壳<b>手上没目标</b>时才开始走，所以"一直在打"的空壳永远不会被冷却收场
     * （用户指定）。必须<b>每刻</b>报、而不是挂在 10 刻一次的索敌里：重锤动作、进食、解毒那几条
     * 岔路都会在中途 {@code return}，走不到索敌那一步，冷却就会把"正在砸人"误判成"没活干"。
     */
    private void reportTargetPresenceToOrb() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;   // 没目标（或目标已经死了）：让冷却自己走
        }
        OrbOfLuckEntity orb = this.motherOrb();
        if (orb != null) {
            orb.onPossessionTargetHeld();
        }
    }

    @Override
    public void tick() {
        // 落地保护必须抢在 super.tick() <b>之前</b>做完：摔落伤害是在 super.tick() 内部的
        // "移动 → 落地检查"里结算的，等到移动之后再放水，落地那一刻水还不存在 ——
        // 那就是"先受伤、再放水"（实测反馈）。见 OrbPossessionMaceAttack#preMoveStep。
        // （ultrawarm 维度里这一步是"每刻清零坠落距离"，落地交给丢出去的末影珍珠。）
        // 起手判定也一并放到移动之前：这样"判断要救 + 铺好水"能在同一 tick 里做完。
        if (!this.level().isClientSide) {
            if (this.possessionMaceState == null && this.isOrbAttached() && this.canPossessAttack()) {
                OrbPossessionMaceAttack.tryStartEmergencyWaterSave(this);
            }
            if (this.possessionMaceState != null) {
                OrbPossessionMaceAttack.preMoveStep(this);
            }
        }
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        // 被弹射物"打落"的计时放在这里（<b>早于</b>下面那条"没挂核心就直接 return"）：
        // 那具空壳万一在被打落的这几秒里失去了核心（附体结束等），计时也得照走到点，
        // 否则它会永远卡在"不会飞"的状态里。到点恢复飞行（翅膀还在的话，见 tickFlightLockout）。
        this.tickFlightLockout();
        // <b>飞行形态：离地高于 5 格就按 5 格/秒匀速下降</b>（用户指定）。
        // <b>有目标</b>时这一段由 {@link OrbPossessionFlightAI} 每刻做（它顺手把水平走位也算好）；
        // <b>没有目标</b>时那个 goal 的 canUse 直接为假、飞控整段不跑，而这具壳是 noGravity 的 ——
        // 不在这里补一下，它就会永远挂在被打到的那个高度上（用户反馈的那个 bug）。
        // 放在 super.tick() 之后：飞控已经写完速度，这里只在"高出 5 格"时把竖直分量钉成匀速下降。
        this.tickFlightDescent();
        // 需求 6.5.21：被"支援幸运方块"波及的"失去 AI"计时（见 applyPilotStun）。
        // 同样放在"没挂核心就直接 return"之前：这段时间里核心没了，AI 也得照点恢复，
        // 否则它会永远停在 NoAI 的木桩状态。
        this.tickPilotStun();
        // "地狱疣生成器"那一秒的到点摘除（见 applyWartTrap）。同样放在"没挂核心就直接 return"之前：
        // 就算这一秒里核心没了，该摘的地狱疣也得摘掉。
        this.tickWartStrip();
        // 附体 boss 血条：①本体血量（紫、居中）+ ②伤害吸收那段（黄、右侧）。
        // 放在下面两条 return <b>之前</b>：① 没了核心的壳要把血条收掉（isOrbAttached 为假时收），
        // ② 被 NoAI 冻住的壳照样该看得见血条（它不是攻击行为）。
        this.tickShellBossBar();
        // "由幸运核心支撑"的空壳：受伤后只能靠附魔金苹果回血（见下面的苹果逻辑），
        // 原本那条"每 10 刻回 2 点"的被动回血已经移除。
        if (!this.isOrbAttached()) {
            return;
        }
        // 索敌冷却的"有活干就不算冷却"判定：每刻上报手上还有没有活的目标（见方法注释）。
        // 放在 NoAI 总闸之前：那不是攻击行为，冻住期间没必要把核心的索敌冷却也一起放开。
        this.reportTargetPresenceToOrb();
        // <b>NoAI = 一律不出手</b>（用户指定）：这是所有攻击方式的<b>唯一总闸</b>。
        // 现在的每一招都从这之后才开始 —— 抽签（{@code drawUsable}）、起手（{@code beginWindUp}）、
        // 出手（{@code run}）、以及跨刻的重锤动作与连发调度，所以以后新增的攻击方式
        // 只要挂在同一套"抽签 → 起手 → 出手"流程上，就自动受这条约束。
        if (!this.canPossessAttack()) {
            this.abortPossessionAttacks();
            return;
        }
        // goal 不进存档：读档/区块重载后自己补装（三种"核心支撑的空壳"都走这里，
        // 不指望核心替另两种补 —— 见 enablePossessionCombat 的注释）。幂等，每刻调也很便宜。
        this.enablePossessionCombat();
        // 滑翔时把身体放平（{@code Pose.FALL_FLYING}）。
        // <b>原版只给玩家设这个姿势</b>（{@code Player#updatePlayerPose()}），生物不会自动有 ——
        // 不设的话，重锤俯冲时它是"笔直站着往下掉"，完全看不出在滑翔。
        // 姿势是同步字段（{@code DATA_POSE}），所以服务端设一次、客户端渲染就是躺平姿态。
        // 只在"滑翔 ↔ 不滑翔"之间切换，不碰其它姿势（游泳/死亡之类）。
        // <b>切姿势时碰撞箱也得跟着换</b>：见 {@link #getDefaultDimensions}，原版 setPose 不会重算。
        if (this.isFallFlying()) {
            if (this.getPose() != Pose.FALL_FLYING) {
                this.setPose(Pose.FALL_FLYING);
                this.refreshDimensions();   // 0.6 × 1.8 → 0.6 × 0.6（与真人玩家滑翔时一致）
            }
        } else if (this.getPose() == Pose.FALL_FLYING) {
            this.setPose(Pose.STANDING);
            this.refreshDimensions();       // 恢复 0.6 × 1.8 / 眼高 1.62
        }
        // <b>跟着附近的玩家一起蹲起（T-bag）</b>（用户指定）：放在这里 = 每刻都跑、
        // 且排在所有 return 之前（进食/索敌那几条岔路都拦不住它，蹲到一半也不会卡住姿势）。
        this.tickCrouchMimic();
        // 重锤砸中后记下的"冲击点"只保这一次坠落：落地了、或者过了一段时间还没落地（比如砸完掉进水里），
        // 就作废 —— 否则会莫名其妙免掉很久以后另一次摔落。
        if (!Double.isNaN(this.possessionSmashImpactY)
            && (this.onGround() || this.tickCount > this.possessionSmashImpactExpireTick)) {
            this.possessionSmashImpactY = Double.NaN;
        }
        // <b>「反重锤」应急出手</b>：目标带着重锤往下掉时，<b>哪怕空壳此刻正忙</b>
        // （自己那套"冲天→俯冲→砸→落地水"要好几秒、正在啃附魔金苹果、大招还在起手……）
        // 也照样把缓降药水丢出去 —— 见 OrbPossessedAttackEvents#tryEmergencySlowFallingThrow。
        // 位置很关键：必须排在下面那条"重锤动作进行中就 return"<b>之前</b>，
        // 否则它自己那套动作会把整个砸击窗口盖住，实测反馈就是"药水等人落地、甚至砸完了才出来"。
        // canPossessAttack()（NoAI 总闸）已经在上面判过，这里不再重复；
        // 候选对象由那个方法自己找（先用索敌目标，找不到就自己在射程内扫）。
        OrbPossessedAttackEvents.tryEmergencySlowFallingThrow(this);
        // 没在重锤动作里时，先看要不要为这次坠落起一次"临时落地水自救"：
        // 手里没拿重锤也照样救（用户要求），起手后走的是同一套状态机（见 OrbPossessionMaceAttack）。
        if (this.possessionMaceState == null) {
            OrbPossessionMaceAttack.tryStartEmergencyWaterSave(this);
        }
        // 重锤动作 / 落地水自救进行中：这一刻的走位、抽签、出手<b>全部</b>交给它 ——
        // 它自己会换装备（鞘翅/烟花/重锤/水桶）、自己开滑翔、自己砸、自己落水自救。
        // 必须拦在这里：下面的抽签会 clearPossessionHands() 把它手里的东西清掉。
        if (this.possessionMaceState != null) {
            // 目标切成了创造/旁观：这套动作也没必要做下去了（它自己控飞行/滑翔，
            // 不在这里收掉的话，下面的目标校验根本轮不到执行，它会照样砸过去）。
            LivingEntity maceTarget = this.getTarget();
            if (this.mustNotAttack(maceTarget)) {
                this.dropUntouchableTarget();
                return;
            }
            OrbPossessionMaceAttack.tick(this);
            return;
        }
        // <b>血量归零后的死亡动画（原版 20 刻）里不再动嘴</b>（用户反馈：空壳死了还在吃东西）。
        // 死亡动画期间实体照样在 tick，而这段进食逻辑挂在 super.tick() 之后、没有任何存活判断，
        // 于是两头都会漏：① {@link #tryEatAppleWhenHurt} 的判据是"血量 < 上限的 50%"，
        // 血量 0 永远成立 → 当场再起一口附魔金苹果；② 啃到一半就死的那一口，
        // 原版也会照常推完并结算（{@code LivingEntity#tick} 里的 updatingUsingItem 不判存活，
        // LivingEntity.java:2455），所以这里要主动 stopUsingItem 把它收掉。
        // 放在所有进食调用之前（含生成时那一口苹果与窒息逃命紫颂果），一处管住全部入口。
        if (this.isDeadOrDying()) {
            if (this.isUsingItem()) {
                this.stopUsingItem();
            }
            return;
        }
        // 饥饿 / 饱和度 + 原版玩家那套自然回复（回血要烧饱和度和饥饿）
        this.tickFoodAndNaturalRegen();

        // "保护"效果被奶桶/牛奶清掉了就补回来（空壳自己解毒时会顺手清掉它）
        if (this.tickCount % 20 == 0) {
            this.ensureCoreBackedProtection();
        }

        // 生成时的那一口附魔金苹果（不论此刻有没有索敌目标）：
        // 这是"生成 → 先吃完一个 → 随后再开始索敌"的实现点，因为下面就是进食/索敌与抽签。
        if (this.possessionSpawnApplePending) {
            this.possessionSpawnApplePending = false;
            this.startEatingApple();
        }
        // <b>被卡在方块里（窒息）→ 先啃紫颂果逃命</b>（用户指定）。
        // 位置是所有进食之前的<b>最优先</b>：卡在墙里是每刻 1 点伤害，连"正在吃的东西"也打断
        // （见 tryEscapeSuffocationWithChorusFruit）。
        if (this.tryEscapeSuffocationWithChorusFruit()) {
            return;
        }
        // 正在吃（附魔金苹果 / 熟猪排 / 紫颂果）：这期间什么都不做 —— 不索敌、不抽签、不出手，
        // 手指头只干"吃"这一件事；顺便每 4 刻洒一把食物碎屑（原版那套在服务端是空实现，
        // 见 spawnEatParticles），让"在吃东西"这件事看得见。
        if (this.isEatingFood()) {
            if (this.tickCount % 4 == 0) {
                this.spawnEatParticles(this.getUseItem(), EAT_PARTICLES_PER_BURST);
            }
            return;
        }
        // 中了凋灵/中毒 → 先丢奶桶自救。<b>不需要索敌目标</b>：这是保命动作，
        // 和"没目标也会吃附魔金苹果"同级（以前只写在攻击抽签里，没目标时中毒也不解毒）。
        if (OrbPossessedAttackEvents.tryEmergencyMilkBucket(this)) {
            this.cancelPossessionWindUp();   // 正在拉弓/举矛也先放下：解毒优先
            return;
        }
        // 血线到了先吃附魔金苹果（和普通食物撞车时苹果优先）
        this.tryEatAppleWhenHurt();
        if (this.isEatingFood()) {
            return;
        }
        // 其次是普通食物：补饥饿/饱和度，让自然回复有燃料
        this.tryEatFoodWhenNeeded();
        if (this.isEatingFood()) {
            return;
        }

        // 每 10 刻重新索敌一次（16 格 AABB 查询不必每刻做）；换目标就立刻重抽签
        // <b>正在跟着玩家蹲起时不索敌</b>（用户指定：蹲起结束前不会去攻击新出现的索敌目标）——
        // 目标保持为 null，下面那条"没目标就 return"自然也不会出手。
        if (!this.isCrouchMimicking() && this.tickCount % POSSESS_TARGET_SCAN_INTERVAL == 0) {
            this.tickPossessionTargetScan();
        }

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            // 没有索敌目标：不抽签、也不摆架势。走位本来就只在有目标时才发生（见 OrbPossessionKiteGoal）
            this.cancelPossessionWindUp();
            this.possessionDrawnEvent = null;
            this.possessionHoldingMelee = false;
            return;
        }
        // <b>目标切到创造/旁观、或者是 SunnySeren 规则里被保护的那位玩家 → 当场放手</b>
        // （用户反馈的 bug：玩家切旁观后它还在对着人打）。
        // 检查放在"每刻"这一层（而不是每 10 刻的索敌里）：索敌是 10 刻一次，
        // 靠它的话玩家切模式后还要被打半秒才收手。
        if (this.mustNotAttack(target)) {
            this.dropUntouchableTarget();
            return;
        }

        long gameTime = this.level().getGameTime();

        // 有索敌目标就一直盯着它：不只依赖走位 goal（它只在跑的时候转头），
        // 这样起手（拉弓/举矛/举刀）和近战架势期间视线也不会飘走 ——
        // 玩家模型的朝向跟着头部走，弓/矛的指向才和实际弹道对得上。
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // ===== 插队（必须放在"起手完成"之前）=====
        // 最高优先级特攻（目前是"目标拿重锤且没缓降 → 丢缓降药水"）的条件一旦成立，
        // 就把手里这张还没出手的签当场作废、立刻重抽 —— <b>哪怕它正在拉弓/举矛也要放下</b>。
        // 放在"起手完成"之前是关键：否则蓄力中的那一招（弓要 20~40 刻）会先把箭射出去，
        // 药水只能等下一轮，看起来就是"它还是先远程攻击再丢药水"。
        // 手里本来就是这张签时不重抽，不然 1 刻的前摇会被每刻重置、药水永远丢不出去。
        boolean topPriorityWaiting = OrbPossessedAttackEvents.highestPriorityReady(this, target)
            && (this.possessionDrawnEvent == null || !this.possessionDrawnEvent.highestPriority());
        if (topPriorityWaiting) {
            // 让正在蓄力的那一招自己收尾（放下弓/收起矛），并把签作废
            this.cancelPossessionWindUp();
            this.possessionDrawnEvent = null;
        }

        // 起手完成 → 出手（目标跑了或冷却没好就只收尾，不出手）
        if (this.possessionWindUpEndTick >= 0) {
            if (this.tickCount < this.possessionWindUpEndTick) {
                return; // 还在蓄力：保持姿态，本刻不抽签、不出手
            }
            OrbPossessedAttackEvents.PossessedAttackEvent winding = this.possessionDrawnEvent;
            this.possessionWindUpEndTick = -1;
            if (winding != null) {
                if (winding.canRun(this, target) && this.isAttackReady(winding, gameTime)) {
                    winding.run(this, target);
                    this.markAttackUsed(winding, gameTime);
                }
                winding.endWindUp(this);
            }
        }

        // 每 1~2 秒抽一次签，决定这段时间是用弓、用矛、用刀还是扔苦力怕。
        // 抽到"够不着"的签（近战但目标还在 3 格开外）会当场重抽，不会白占一个窗口
        // —— 见 OrbPossessedAttackEvents#drawUsable。
        // topPriorityWaiting（上面那个插队）也会让这里"无视计时"立刻重抽。
        // <b>铁抓钩拉扯期间不重抽</b>：这一招是跨刻动作（拉人期间空壳站定收链），
        // 中途换签会把钩子收掉、手上换别的武器，看起来就像"钩到一半放弃了"。
        if (!OrbHookPull.isPulling(this)
            && (topPriorityWaiting || this.possessionDrawnEvent == null
                || this.tickCount >= this.possessionNextDrawTick)) {
            this.possessionDrawnEvent = OrbPossessedAttackEvents.drawUsable(this, target);
            this.possessionNextDrawTick = this.tickCount + POSSESS_DRAW_MIN_TICKS
                + this.getRandom().nextInt(POSSESS_DRAW_MAX_TICKS - POSSESS_DRAW_MIN_TICKS + 1);
            // 新的一签：先清空双手，再由事件拿出自己的武器并进入起手姿态。
            // 起手刻数用"按攻击者动态"的那个重载：个别特攻（变形解药）会看空壳状态决定要不要起手。
            this.clearPossessionHands();
            int windUp = this.possessionDrawnEvent == null
                ? 0 : this.possessionDrawnEvent.windUpTicks(this);
            if (this.possessionDrawnEvent != null && windUp > 0) {
                // 起手前先把"使用中"状态彻底清干净：LivingEntity#startUsingItem 有一道
                // `!isUsingItem()` 的前置判断，万一上一件武器留下了残留的使用状态
                // （比如读档、或某个事件的 endWindUp 没走到），新武器的举弓/举矛姿态就会被静默跳过，
                // 表现就是"手里拿着三叉戟却完全不做投掷动作"。这里先 stopUsingItem 再起手，
                // 从根上避免那种情况（对没在使用的情况是空操作）。
                this.stopUsingItem();
                this.possessionDrawnEvent.beginWindUp(this);
                this.possessionWindUpEndTick = this.tickCount + windUp;
            }
        }

        OrbPossessedAttackEvents.PossessedAttackEvent drawn = this.possessionDrawnEvent;
        if (drawn == null) {
            this.possessionHoldingMelee = false;
            return;
        }

        // 抽到近战且目标已进范围 → 停下走位（走位由 OrbPossessionKiteGoal 负责停）；
        // 抽到别的、或近战够不着 → 继续骷髅式走位。
        // （监守者是例外：isHoldingForMelee 对它一律返回 false，所以它贴脸也会继续绕圈。）
        this.possessionHoldingMelee = drawn.shouldPauseMovement(this, target);

        // 需要起手的事件在上面"起手完成"处出手，这里不重复
        if (this.possessionWindUpEndTick >= 0) {
            return;
        }

        if (drawn.canRun(this, target) && this.isAttackReady(drawn, gameTime)) {
            drawn.run(this, target);
            this.markAttackUsed(drawn, gameTime);
        }
    }

    /**
     * 每 {@link #POSSESS_TARGET_SCAN_INTERVAL} 刻的重新索敌：先更新"当前目标还看不看得见"，
     * 定下来之后再换目标、再上报给核心。
     * <p>
     * 上报用的是<b>最终</b>那个目标（不是扫描结果）：盯着看不见的目标硬打的那段宽限期里，
     * 不该把它当成"新面孔"反复报给核心。
     */
    private void tickPossessionTargetScan() {
        LivingEntity current = this.getTarget();
        // 视线判定：与走位那边 {@code OrbPossessionKiteGoal} 用的是同一个入口
        // （{@code getSensing().hasLineOfSight}：眼位对眼位、按流体不遮挡的方块碰撞箱做射线，
        // 与原版 {@code TargetingConditions} 的 checkLineOfSight 完全同一条）。
        // 目标已经没了/死了：没有"还在盯着谁"这回事，直接按看不见处理（计时归零、走换目标那条路）。
        // <b>这里不能 return</b>：没有目标时"换目标"那一步＝第一次索敌，
        // 提前返回会让空壳永远索不到任何人（曾经就是这么错的）。
        boolean unseenTooLong = true;
        if (current != null && current.isAlive()) {
            boolean seen = this.shouldSeePossessionTarget(current);
            if (seen != this.possessionTargetSeen) {
                this.possessionTargetUnseenTicks = 0;   // 与走位那边 seeTime 的写法一致：状态一变就重新起算
            }
            this.possessionTargetSeen = seen;
            this.possessionTargetUnseenTicks += seen ? -1 : 1;
            // 卡在 -1 就够表示"看得见"了，不让它无限往下掉
            this.possessionTargetUnseenTicks = Math.max(this.possessionTargetUnseenTicks, seen ? -1 : 0);
            // 看不见但还在宽限期内：这一轮<b>不换目标</b>。
            // 目标刚从树后跑过去、或者只是本刻被方块挡了一下，不该让空壳立刻翻脸去追别人。
            unseenTooLong = this.possessionTargetUnseenTicks >= POSSESS_TARGET_UNSEEN_MEMORY_TICKS;
        } else {
            this.possessionTargetUnseenTicks = 0;
            this.possessionTargetSeen = true;
        }

        if (unseenTooLong) {
            // 宽限期过了（或本来就没目标）：只认看得见的目标 —— 有别人看得见就换过去打，
            // 一个都看不见就干脆丢掉目标（丢掉之后连抽签、走位一起停，空壳进入待命；
            // 目标再露出头，下一次扫描自然会重新锁上）。
            LivingEntity found = this.findPossessionTarget();
            if (found != this.getTarget()) {
                this.cancelPossessionWindUp();
                this.setTarget(found);
                this.possessionDrawnEvent = null;
                // 换了目标就重新起算视线计时：新目标这一轮还没被判定过
                this.possessionTargetUnseenTicks = 0;
                this.possessionTargetSeen = true;
            }
        }
        // 把"当前盯着谁"告诉核心：没见过的新面孔会给附体倒计时 +1 分钟（去重在核心那边做）。
        // 上报用的是<b>最终</b>那个目标（不是扫描结果）：盯着看不见的目标硬打的那段宽限期里，
        // 不该把它当成"新面孔"反复报给核心。
        this.notifyPossessedOrbOfTarget(this.getTarget());
    }

    /**
     * 视线扇形的<b>横向采样列数</b>（必须是奇数，中线那一列会被先试）。
     * <p>
     * 3 列 = 身体的左缘 / 中线 / 右缘；配合纵向 5 层（脚底、下、中、上、眼睛），
     * 一次最多 15 条射线，覆盖目标整个碰撞箱 —— 这就是"一个大扇形"。
     */
    private static final int SIGHT_FAN_SAMPLES = 3;

    /**
     * 这具空壳<b>看不看得见</b>某个目标。
     *
     * <h2>不是"一条射线"，而是一个覆盖目标整个身体的大扇形</h2>
     * 原版索敌（{@code TargetingConditions#test} 里那句
     * {@code attacker instanceof Mob mob && !mob.getSensing().hasLineOfSight(target)}）用的是
     * <b>眼位对眼位的一条射线</b>。那条射线太窄了，实测会出现两类误判：
     * <ul>
     *   <li><b>高个子目标自己挡自己</b>：九头蛇、暮色巨人这类目标比空壳高，站近一点时
     *       "空壳的眼 → 它的眼"这条线会从它<b>自己的身体</b>里穿过去，明明正对着却判成看不见；</li>
     *   <li><b>只露一部分身体</b>：躲在方块后只露出肩膀、或者头被树叶挡住但身子在外面，
     *       一条射线打在哪就是哪，稍微偏一点就全判成看不见。</li>
     * </ul>
     * 所以这里改成<b>扇形采样</b>：从空壳的眼位出发，朝目标碰撞箱上
     * {@link #SIGHT_FAN_SAMPLES} 列 × 5 层采样点各打一条射线，
     * <b>任意一条通</b>就算看得见（等价于"视野覆盖面前那一整片，而不是一个针眼"）。
     * 注意采样点取自目标的整个碰撞箱（脚底 ~ 头顶），所以它的<b>头顶、身侧、脚边</b>都算数。
     * <p>
     * 射线本身照旧只被方块的碰撞箱挡住（{@code ClipContext.Block.COLLIDER} +
     * {@code Fluid.NONE}）：水、草、火这类不挡视线的东西不构成遮挡 ——
     * 于是"隔着墙"依然看不见，只是不再被"身高差 / 偏一格"这种小事卡住。
     * <p>
     * <b>优化</b>：中线那一列排在最前，最常见的"正对着目标"一两条射线就返回了；
     * 左右两列只在"连中线都全被挡"时才付出代价。原版那个带缓存的
     * {@code getSensing().hasLineOfSight} 仍然先试一次（它按 tick 缓存，命中时最省）。
     */
    boolean shouldSeePossessionTarget(LivingEntity target) {
        // 快路径：原版那条（带缓存）能看见就直接算看见
        if (this.getSensing().hasLineOfSight(target)) {
            return true;
        }
        Vec3 eye = new Vec3(this.getX(), this.getEyeY(), this.getZ());
        AABB box = target.getBoundingBox();
        float eyeHeight = (float) target.getEyeHeight();
        // 纵向 5 层：脚底、下、中、上、眼睛（全是"目标身上"的点，所以任何一条通都说明目标露在外面）
        float[] heightFractions = {0.0F, 0.25F, 0.5F, 0.75F, 1.0F};
        int half = SIGHT_FAN_SAMPLES / 2;
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        // 先中线（offset = 0）、再左右两列
        for (int column = 0; column <= half; column++) {
            for (int side = 0; side < (column == 0 ? 1 : 2); side++) {
                double offset = column == 0 ? 0.0D : (side == 0 ? -0.5D : 0.5D) * box.getXsize() * column / half;
                for (float fraction : heightFractions) {
                    Vec3 sample = new Vec3(
                        centerX + offset,
                        target.getY() + Mth.lerp(fraction, eyeHeight, target.getBbHeight()),
                        centerZ);
                    if (this.canSeePossessionPoint(eye, sample)) {
                        return true;   // 任意一条通 → 这个目标在视野里
                    }
                }
            }
        }
        return false;
    }

    /** 空壳眼位到某个采样点之间有没有方块挡着（水/草/火不挡，与原版视线判定一致）。 */
    private boolean canSeePossessionPoint(Vec3 eye, Vec3 sample) {
        // 与原版 hasLineOfSight 同一个上限：太远的一律不算看见
        if (sample.distanceTo(eye) > 128.0D) {
            return false;
        }
        return this.level().clip(new net.minecraft.world.level.ClipContext(
            eye, sample,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType()
            == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /**
     * 这具空壳<b>绝不该打</b>的目标：切创造/旁观的玩家，以及 SunnySeren 规则里那位被保护的玩家。
     * <p>
     * 两件事合成一个判据，是因为它们都必须<b>每刻</b>被检查（不只索敌时）：
     * 索敌只管"选目标"，已经锁上的那个得靠 {@link #dropUntouchableTarget()} 主动放掉。
     * <p>
     * 包外可见：{@code OrbPossessedAttackEvents#tryEmergencySlowFallingThrow} 自己扫候选时要跳过这些人
     * （那是一条不经过索敌的出手通道，不能泼到不该打的人身上）。
     */
    public boolean mustNotAttack(@Nullable LivingEntity target) {
        if (target == null) {
            return false;
        }
        if (isUntouchablePlayer(target)) {
            return true;
        }
        // SunnySeren 特例①：绝不攻击那位玩家 —— 这一条是绝对的，索敌里不选、锁上了也当场放掉
        return OrbAdversaryRelations.isSpecialShell(this) && OrbAdversaryRelations.isProtectedPlayer(target);
    }

    /** "目标是不是已经远远离开"的判定半径（格）：{@link #POSSESS_ATTACK_RANGE} 的四倍，见下面的方法注释。 */
    private static final double POSSESS_TARGET_ABANDON_RANGE = POSSESS_ATTACK_RANGE * 4.0D;

    /**
     * 目标是不是还在"身边"（没有被拖走/跑掉）—— <b>以空壳自己为中心、半径
     * {@value #POSSESS_TARGET_ABANDON_RANGE} 格</b>。
     * <p>
     * 供 {@code OrbPossessionMaceAttack} 的"失败后滞空重来"用：用户指定"如果此时目标已在索敌目标之外，
     * 则正常地不再索敌" —— 也就是<b>不重来</b>，把这件事交回给正常索敌。
     *
     * <h2>为什么半径要放到 200 格、而不是索敌那个 50 格</h2>
     * 这条判据是"<b>目标</b>离开了"，不是"空壳自己飞开了"：重锤俯冲末速每秒几十格，
     * 失败（自身高度低于目标）之后这 10 刻里，空壳<b>自己</b>就能跑出几十格 —— 那是它自己的惯性，
     * 不是目标跑掉，不该因此取消补招。所以这里只拦"目标确实已经离开战场"这种离谱情况：
     * 10 刻（0.5 秒）内要跑出 200 格，得 400 格/秒，正常目标做不到。
     * <p>
     * （实测教训：上一版直接用了索敌那个"竖直只有 ±4 格余量"的盒子，结果日志里空壳与目标的 y
     * 只差 0.1~0.8 格却报"目标已经不在索敌范围内"，补招一次都没发出来。）
     */
    boolean isPossessionTargetNearby(LivingEntity target) {
        return this.distanceToSqr(target) <= POSSESS_TARGET_ABANDON_RANGE * POSSESS_TARGET_ABANDON_RANGE;
    }

    /**
     * 丢掉"不该打的目标"：收招、清签、放开目标。
     * <p>
     * 为什么必须单独有这一步：索敌里的 {@link #isUntouchablePlayer} 只管"<b>选</b>新目标"时
     * 不选这两类玩家，<b>已经锁上的那个</b>它管不着 —— 目标切旁观后，
     * 空壳会继续举弓/举刀对着人打（伤害被无敌吃掉的空响），要到下一次索敌才可能换人。
     * <p>
     * 同时把重锤那套动作也收掉：它自己在控飞行/滑翔，不打断的话照样往人身上砸。
     * 目标清掉之后，每刻的"手上还有没有活的目标"上报自然就停了，索敌冷却从这一刻开始走
     * （见 {@link #reportTargetPresenceToOrb}）。
     */
    private void dropUntouchableTarget() {
        this.cancelPossessionWindUp();
        this.setTarget(null);
        this.possessionDrawnEvent = null;
        this.possessionHoldingMelee = false;
        this.possessionTargetUnseenTicks = 0;
        this.possessionTargetSeen = true;
        OrbPossessionMaceAttack.abort(this);
    }

    /**
     * 索敌：范围内任意生物，排除自己、<b>创造模式与旁观模式的玩家</b>、<b>其它被附体的空壳</b>、
     * 核心本体，以及<b>一切附体召唤物</b>（见 {@link OrbPossessionSummons}：召唤出来的 Bob、
     * 爆炸性苦力怕/猫/僵尸都带统一标记）。
     * <p>
     * 创造/旁观玩家要排除：创造模式玩家本来就打不动（伤害会被无敌挡下），
     * 旁观模式则根本不该被卷入战斗 —— 对它们索敌只会白白浪费攻击窗口。
     * <p>
     * 排除召唤物有两个理由：它们是自己人（Bob 是来帮忙的、爆炸弹是射出去的弹药），
     * 而且爆炸弹免疫任何伤害（{@link TntStaffCreeper#hurt} 直接返回 false）。
     * Bob 们靠 {@link OrbSummonTargetGoal} 抄这里的结果，所以它们也就自动不会互相打。
     *
     * <h2>视线（本次改良的重点）</h2>
     * 参考原版：{@code NearestAttackableTargetGoal} 的目标筛选条件
     * （{@code TargetingConditions.forCombat()}）里 {@code checkLineOfSight} 默认为真，
     * 所以在原版里"隔着一堵墙的敌人"根本不会进入候选；{@code TargetGoal} 则用
     * {@code unseenMemoryTicks} 记恨——看不见超过一段时间就放弃。
     * 这里照同一套思路：<b>只取看得见的最近目标</b>，视线被挡住的（隔墙、躲在掩体后）
     * 一律不进候选。宽限期那一层在 {@link #tickPossessionTargetScan()} 里
     * （{@link #POSSESS_TARGET_UNSEEN_MEMORY_TICKS} 刻内不换目标，超过就丢掉它、
     * 改去选看得见的那些，一个都看不见就待命）。
     * <p>
     * 换句话说：<b>视线被挡住的敌人不会被（重新）锁定</b>；已经锁上而后被挡住的，
     * 只在 {@link #POSSESS_TARGET_UNSEEN_MEMORY_TICKS} 刻的宽限期内保留，
     * 超过就改为去打看得见的别人、一个都看不见就待命（见 {@link #tickPossessionTargetScan()}）。
     * 找不到看得见的目标时返回 null。
     */
    private LivingEntity findPossessionTarget() {
        // 搜索盒带上视线余量：45° 斜向上的目标也要能被看见（原版 getTargetSearchArea 同款）
        AABB area = this.getBoundingBox().inflate(POSSESS_ATTACK_RANGE, 4.0D, POSSESS_ATTACK_RANGE);
        boolean special = OrbAdversaryRelations.isSpecialShell(this);
        ServerPlayer protectedPlayer = special
            ? OrbAdversaryRelations.findProtectedPlayer(this.level().getServer()) : null;
        List<LivingEntity> candidates = this.level().getEntitiesOfClass(LivingEntity.class, area,
            e -> e != this
                && e.isAlive()
                && !isUntouchablePlayer(e)
                && !this.isExcludedForRelationRule(e, protectedPlayer)
                && !(e instanceof PlayerShellEntity shell && shell.isOrbAttached())
                && !OrbPossessionSummons.isSummon(e)
                && !(e instanceof OrbOfLuckEntity));
        // <b>先按距离排序，再由近到远逐个做视线判定、命中第一个就收工</b>：
        // 视线判定是一条方块射线，不能像原来那样"遍历每个人算个距离"那么随便；
        // 排序后常见情况（附近就有看得见的敌人）往往一两次射线就出结果，
        // 只有"整片区域一个都看不见"时才会把所有人都射一遍。
        candidates.sort(java.util.Comparator.comparingDouble(this::distanceToSqr));
        for (LivingEntity candidate : candidates) {
            // 视线被挡 → 跳过（原版 TargetingConditions 的 checkLineOfSight）
            if (this.shouldSeePossessionTarget(candidate)) {
                return candidate;
            }
        }
        // ===== SunnySeren 特例：再补上两类"例外目标" =====
        // 上面那圈是<b>正常索敌</b>（打一切普通生物）；下面这两类被附体空壳平时是被排除的
        // （"自己人"那条老规矩），只有和那位玩家结下梁子时才临时算进来。
        if (special && protectedPlayer != null) {
            return findRelationTarget(area, protectedPlayer);
        }
        return null;
    }

    /**
     * SunnySeren 特例的<b>排除项</b>：正常索敌里要把这两类剔掉。
     * <ol>
     *   <li><b>Red_Zombie 本人</b>（规则①，绝对的）；</li>
     *   <li>他<b>饲养的宠物</b>（原版可驯服动物 + 车万女仆）—— 也属于"不攻击"的范围。</li>
     * </ol>
     * 注意：<b>其它普通生物照打</b>（用户澄清：这具壳"会正常索敌其他生物"），
     * 只有"被附体空壳"这一类在正常索敌里本来就被排除（上面那条 {@code isOrbAttached}）。
     * 不是特例壳、或那位玩家不在线时，{@code protectedPlayer} 为 null，这里一律不排除。
     */
    private boolean isExcludedForRelationRule(LivingEntity candidate, @Nullable ServerPlayer protectedPlayer) {
        if (protectedPlayer == null) {
            return false;
        }
        return OrbAdversaryRelations.isProtectedPlayer(candidate)
            || OrbAdversaryRelations.isPetOf(candidate, protectedPlayer);
    }

    /**
     * SunnySeren 特例的<b>例外目标</b>：与那位玩家结下梁子的被附体空壳。
     * <ol>
     *   <li><b>正在攻击他</b>的壳（"护主"，最急）；</li>
     *   <li><b>他攻击过</b>的壳（"帮打"）。</li>
     * </ol>
     * 这两类要过同一个视线判定（看不见就不锁），并且按 {@code rank} 分层 ——
     * 护主比帮打更急，同层再取最近的。找不到返回 null（那就继续等下一轮索敌）。
     */
    private LivingEntity findRelationTarget(AABB area, ServerPlayer protectedPlayer) {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : this.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e instanceof PlayerShellEntity shell && shell != this && shell.isOrbAttached()
                    && shell.isAlive() && !isUntouchablePlayer(shell))) {
            PlayerShellEntity other = (PlayerShellEntity) candidate;
            int rank = OrbAdversaryRelations.relationRank(protectedPlayer, other);
            if (rank < 0) {
                continue;   // 与该玩家无关的其它空壳：照旧不打
            }
            if (!this.shouldSeePossessionTarget(other)) {
                continue;   // 看不见就不锁（与正常索敌同一条规矩）
            }
            // 先按优先级分层（护主 > 帮打），同层再取最近的
            double score = rank * 1_000_000.0D + this.distanceToSqr(other);
            if (score < bestScore) {
                bestScore = score;
                best = other;
            }
        }
        return best;
    }

    /**
     * 这名生物是不是"不该被索敌的玩家"：创造模式或旁观模式。
     * <p>
     * 创造模式玩家的伤害会被无敌挡下（打不动），旁观模式玩家本就不参与战斗 ——
     * 对这两种人开火纯属浪费攻击窗口，所以一律不选。
     */
    private static boolean isUntouchablePlayer(LivingEntity entity) {
        return entity instanceof Player player && (player.isCreative() || player.isSpectator());
    }

    /**
     * 重锤砸中时记下的"冲击点"高度（{@link Double#NaN} = 没有）。
     * <p>原版 {@code MaceItem#hurtEnemy} 会记 {@code currentImpulseImpactPos}，
     * 然后 {@code Player#causeFallDamage} 拿它把坠落距离截断 —— "砸中之后，冲击点以上的那段落差不再算伤害"。
     * 空壳这边照同款实现（见 {@link #causeFallDamage}）：不然重锤砸中后落地还会吃一次满额摔落伤害。
     */
    private double possessionSmashImpactY = Double.NaN;

    /** 冲击点的失效刻（砸中后 5 秒内没落地就作废）。 */
    private long possessionSmashImpactExpireTick;

    /** 重锤砸中：记下冲击点（由 {@code OrbPossessionMaceAttack#smash} 调用）。 */
    public void noteSmashImpact() {
        this.possessionSmashImpactY = this.getY();
        this.possessionSmashImpactExpireTick = this.tickCount + 100L;
    }

    /**
     * 摔落伤害：只要这具空壳<b>刚用重锤砸中过目标</b>，这次坠落的摔落伤害<b>整个免掉</b>。
     * <p>触发点是 {@code OrbPossessionMaceAttack#smash}（砸中那一刻调 {@link #noteSmashImpact()}）。
     * <p>原版 {@code Player#causeFallDamage} 的做法是"把冲击点以上的落差截掉、只结算以下那一小段"
     * （配合 {@code MaceItem#hurtEnemy} 记下的 {@code currentImpulseImpactPos}），
     * 但按需求这里直接<b>全免</b> —— 砸中就是砸中，落地不该再掉血。
     * <p>没砸中过就完全走原版：空壳现在<b>不免疫</b>摔落伤害，没砸中的话摔下来照常疼（那正是落水自救存在的意义）。
     */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (!Double.isNaN(this.possessionSmashImpactY)) {
            this.possessionSmashImpactY = Double.NaN;
            this.fallDistance = 0.0F;
            return false;
        }
        return super.causeFallDamage(fallDistance, multiplier, source);
    }

    /**
     * 承伤：<b>这具空壳自己吃伤害</b>，不再把伤害转给核心。
     * <p>
     * 附体形态的"一条命"整个搬到了这具身体上：核心本体在这个阶段既没有碰撞箱、又免疫一切
     * 非 BYPASSES_INVULNERABILITY 的伤害（见 {@link OrbOfLuckEntity#isInvulnerableTo}），
     * 所以血量归零的判定完全由这里负责 —— 打到 0 就是这次附体被击败（见 {@link #die}）。
     * <p>
     * 唯一保留的减伤是"自己人的远程攻击"：苦力弹爆炸与铁砧雨从这具身体手上打出去，
     * 贴脸/被逼到墙角时会波及自己。这两个减免原来写在核心的 {@code hurt} 里，
     * 承伤方换成空壳后跟着搬过来（数值仍在核心那边定义）。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.possessedOrbUuid != null) {
            if (source.getDirectEntity() instanceof TntStaffCreeper flying && flying.isFlying()) {
                amount *= 1.0F - OrbOfLuckEntity.CREEPER_BLAST_RESISTANCE;
            } else if (OrbAnvilRain.isRainAnvil(source.getDirectEntity())
                || OrbAnvilRain.isRainAnvil(source.getEntity())) {
                amount *= 1.0F - OrbOfLuckEntity.ANVIL_RAIN_RESISTANCE;
            }
        }
        // 玩家空壳可被攻击（核心支撑的那几具是 20 血 + 20 护甲 + 12 韧性 + 保护 16），但不会与任何东西互动
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        // 附体主空壳被打死 = 这次附体<b>被击败</b>：交给核心走它自己那套收场流程
        // （Retreat：把玩家送回这里、恢复附体前的游戏模式，然后核心消失）。
        // 与"核心被 /kill 打死"完全同一条路径，只是判定入口从核心换成了这具空壳。
        if (this.possessedOrbUuid != null && this.level() instanceof ServerLevel serverLevel
            && serverLevel.getEntity(this.possessedOrbUuid) instanceof OrbOfLuckEntity orb) {
            // 先把两手清空：附体期间的武器是每次抽签现场塞进来的真货（穿刺 V 三叉戟、忠诚西瓜刀……），
            // 而核心的 Retreat 特意留着这具空壳（好让死亡动画照常演完），
            // 不清的话原版死亡掉落会把它们丢在地上 —— 打死一次空壳就能捡到一把穿刺 V 三叉戟。
            this.clearPossessionHands();
            orb.onVesselDefeated();
        }
        // 不是母体的附体空壳（刷怪蛋放出来的、被同化的那一具）：击败奖励 = 100 点经验
        // （母体那份奖励由核心自己发，见 OrbOfLuckEntity#grantDefeatRewards）。
        // 判据用头顶光球：三种"由幸运核心支撑的空壳"都会点亮它（configureAsPossessedShell /
        // spawnVessel），而变形药水造出来的临时空壳不会 —— 那些不该发这个奖励。
        if (this.possessedOrbUuid == null && this.isOrbAttached()
            && this.level() instanceof ServerLevel serverLevel) {
            ExperienceOrb.award(serverLevel, this.position(), NON_MOTHER_DEFEAT_EXPERIENCE);
            ModMain.LOGGER.info("[附体] 击败非母体空壳: id={} 掉落 {} 点经验 @({}, {}, {})",
                this.getId(), NON_MOTHER_DEFEAT_EXPERIENCE,
                String.format("%.1f", this.getX()), String.format("%.1f", this.getY()),
                String.format("%.1f", this.getZ()));
        }
        // 被同化的玩家空壳被杀 → "复活"那名玩家（恢复其游戏模式并送到这里）
        // 注意它没有 possessedOrbUuid，所以不会像附体主空壳那样被判成"核心被击败" —— 它只是一具普通壳体。
        OrbAssimilation.reviveFromShell(this);
        // 由变形药水创造的空壳被杀死时：连带杀死原生物（玩家/宠物/生物）
        if (this.level() instanceof ServerLevel serverLevel) {
            ModMain.handleLivingShellDeath(serverLevel, this, source);
        }
        super.die(source);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}