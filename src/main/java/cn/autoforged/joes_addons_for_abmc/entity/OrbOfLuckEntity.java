package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 幸运核心（Orb of Luck），注册 id {@code joes_addons_for_abmc:orb_of_luck}。
 * 动物类（{@link Animal}）实体，暂时没有任何 AI；本体是一个会呼吸的发光球，
 * 渲染由 {@link OrbOfLuckRenderer} + 自定义 core shader
 * {@code joes_addons_for_abmc:shaders/core/orbofluck.*} 完成，不使用贴图。
 * 生成/召唤用刷怪蛋 {@code joes_addons_for_abmc:orb_of_luck_spawn_egg}。
 *
 * <h2>碰撞箱：底面对齐坐标，不是以坐标为中心</h2>
 * 用的是原版活体实体的建箱方式（{@code y .. y + height}），所以 <b>箱子下表面正好落在实体位置上</b>：
 * {@code /summon ... ~ ~ ~} 或刷怪蛋放在方块顶面时，核心就是"坐"在那个高度上，而不是一半埋进地里。
 * 渲染器对应地把面片整体上移 {@code height / 2}，保证画出来的球和碰撞箱同心。
 * <p>
 * 尺寸是固定的 {@link #RADIUS}（直径 {@code 2 * RADIUS}），不随玩家距离变化 ——
 * 远处看起来变小完全是透视的自然结果，不需要也不应该去改碰撞箱或位置。
 * <p>
 * 注意 {@link net.minecraft.world.entity.LivingEntity#getDimensions(Pose)} 是 {@code final} 的，
 * 活体实体要改尺寸只能覆写 {@link #getDefaultDimensions(Pose)}（它的返回值还会再乘
 * {@code Attributes.SCALE}），或者改 {@link EntityType.Builder#sized}。
 * 未来 AI 若要让核心长大/缩小，推荐直接改 {@code Attributes.SCALE}：
 * {@link OrbOfLuckRenderer} 读的是 {@code getBbWidth()}，箱子/剔除盒/画面会一起跟着变。
 *
 * <h2>为什么像个"幽灵"一样不动</h2>
 * <ul>
 *   <li>{@code setNoGravity(true)}：不受重力（同步字段，双端一致）。</li>
 *   <li>{@link #isPushable()} 返回 false：别的实体推不动它，它也不会去推别人
 *       （{@code LivingEntity} 默认是 true，不改的话玩家一碰它就把它顶走）。</li>
 *   <li>{@link #isPushedByFluid(FluidType)} 返回 false：水流/岩浆流不会把它冲走。</li>
 *   <li>{@link #canDrownInFluidType(FluidType)} 返回 false：泡在水里也不会溺水（活体才会溺水）。</li>
 *   <li>{@link #causeFallDamage} 返回 false：免疫摔落伤害。</li>
 *   <li>{@link #isInvulnerableTo} 额外挡住 {@code in_wall}（窒息）与 {@code drown}（溺水）——
 *       不分阶段、永远生效。</li>
 *   <li>每 10 游戏刻自回 {@link #REGEN_AMOUNT} 点生命值（每秒 4 点，见 {@link #tick()}）。</li>
 *   <li>免疫变形药水 —— <b>只限核心本体</b>（实现在
 *       {@code ModMain.isTransmutationPotionImmune}）：核心自己变成物品/方块会让附体这件事无法收场。
 *       它依附的那具主空壳（附体真玩家时产生的 boss 身体）按用户要求<b>不再免疫</b>：
 *       被变成物品/方块/生物壳时由核心特殊处理（见 {@link #onVesselMorphed} /
 *       {@link #onVesselReverted}：变形期间不补新壳、跟着产物走，复原时重新认领）。</li>
 *   <li>{@link #registerGoals()} 空实现：一个会移动的 goal 都不加，AI 留到以后再说。</li>
 * </ul>
 * 唯一的例外是附体阶段：那时它<b>骑在</b>自己生成的那具"玩家"身体上（见 {@link #followVessel}），
 * 身体走到哪就跟到哪 —— 位置不再由自己决定，而是由载具决定。
 * <p>
 * 附体阶段还有两条专属规则（都是"核心藏进身体里"的自然结果）：
 * <ul>
 *   <li><b>碰撞箱体积为 0</b>（{@link #getDefaultDimensions}）：打不到、炸不到，也不挡路；</li>
 *   <li><b>不承伤</b>（{@link #isInvulnerableTo}）：这一阶段的"一条命"是那具空壳的
 *       20 点血（自带 20 护甲 / 12 韧性 / 保护 16，见 {@link PlayerShellEntity}），
 *       空壳血量归零 = 附体被击败（{@link #onVesselDefeated}）。
 *       曾经是反过来做的 —— 空壳把伤害全转给核心的 100 点血池，现已改掉。</li>
 * </ul>
 */
public class OrbOfLuckEntity extends Animal {

    // ====================== 状态机 ======================

    /**
     * 幸运核心的三个阶段。
     * <ul>
     *   <li>{@link #INITIAL}：刷怪蛋放出来的默认阶段。静止悬浮、<b>无敌</b>（NBT 里是
     *       {@code Invulnerable:1b}），玩家<b>空手右击</b>即被收走——实体消失、空手变成 Orb of Luck 物品。</li>
     *   <li>{@link #POSSESSING}：附体阶段。物品计数器归零时触发（见 {@code OrbOfLuckEvents}）：
     *       核心在玩家坐标生成、自己不渲染光球形态，而是生成一具"玩家"身体
     *       （{@link PlayerShellEntity} + 头部光球）并把玩家转为旁观；<b>核心骑在那具身体上</b>
     *       跟着它走（身体是会走位/索敌的 AI，见 {@code OrbPossessionKiteGoal}）。
     *       这一阶段核心<b>没有碰撞箱、也不承伤</b>，败北判定看那具空壳的血量
     *       （归零 = 被击败，见 {@link #onVesselDefeated}）。
     *       玩家切回创造模式、附体倒计时归零、<b>索敌冷却到点</b>（{@link #TARGET_COOLDOWN_TICKS}：
     *       空壳手上连续 3 分钟没有活的目标，核心回拾取点而不消失）、或空壳被打死，都会结束附体。</li>
     *   <li>{@link #ULTIMATE}：最终阶段。行为待实装。</li>
     * </ul>
     */
    public enum OrbState {
        INITIAL,
        POSSESSING,
        ULTIMATE;

        static OrbState byId(int id) {
            OrbState[] values = values();
            return id >= 0 && id < values.length ? values[id] : INITIAL;
        }
    }

    /** 当前阶段（同步字段，双端一致）。 */
    private static final EntityDataAccessor<Integer> DATA_STATE =
        SynchedEntityData.defineId(OrbOfLuckEntity.class, EntityDataSerializers.INT);

    /**
     * 附体用的皮肤（玩家名）。客户端据此解析正版皮肤画在那具"玩家"身体上。
     * <p>
     * 同步数据不会进存档，所以 {@link #addAdditionalSaveData}/{@link #readAdditionalSaveData} 里
     * 另外存了一份字符串，读档时再写回同步数据。
     */
    private static final EntityDataAccessor<String> DATA_SKIN =
        SynchedEntityData.defineId(OrbOfLuckEntity.class, EntityDataSerializers.STRING);

    private static final String TAG_SKIN = "PossessSkin";
    private static final String TAG_POSSESSOR = "PossessorUuid";
    private static final String TAG_VESSEL = "VesselUuid";
    private static final String TAG_PREV_GAMEMODE = "PrevGameMode";
    private static final String TAG_DEADLINE = "PossessDeadline";
    private static final String TAG_SEEN_TARGETS = "PossessSeenTargets";
    /** 索敌冷却的截止游戏刻（见 {@link #TARGET_COOLDOWN_TICKS}）。 */
    private static final String TAG_TARGET_COOLDOWN = "PossessTargetCooldown";
    private static final String TAG_ASSIMILATED = "PossessAssimilated";
    /** 这颗核心被捡起来的位置（维度名 + BlockPos.asLong）；见 {@link #pickupSite}。 */
    private static final String TAG_PICKUP_DIM = "PickupDimension";
    private static final String TAG_PICKUP_POS = "PickupPos";

    /** 被附体的玩家（服务端，随 NBT 持久化）。 */
    private UUID possessorUuid;
    /** 附体时生成的那具"玩家"身体（服务端，随 NBT 持久化）。 */
    private UUID vesselUuid;
    /**
     * 主空壳"当前以什么形态存在"：它被变形药水变成物品/方块/生物壳时记的是产物的实体 UUID。
     * <p>
     * 变形期间这具壳已经不在了（原实体被 discard），但它是<b>换了形态</b>而不是没了 ——
     * 所以核心不能补新壳（见 {@link #ensureVessel}），而要跟着产物走（见 {@link #followVessel}）。
     * 不落盘：变形数据表只在内存里，退出存档后这次变形本就作废，重进照常补新壳。
     */
    @Nullable
    private UUID vesselMorphProductUuid;
    /** 被附体前的游戏模式（{@link GameType#getId()}；-1 = 未知），Retreat 时恢复用。 */
    private int previousGameTypeId = -1;

    /** 附体倒计时结束的游戏时间（游戏刻）；-1 = 未在计时。 */
    private long possessionDeadlineGameTime = -1L;

    /**
     * <b>索敌冷却</b>的截止游戏刻；-1 = 未在计时。
     * <p>
     * 到点（见 {@link #TARGET_COOLDOWN_TICKS}）= 这么久<b>手上没有活的目标</b> → 解除附体、
     * 核心回拾取点、<b>不发奖励</b>。空壳每刻通过 {@link #onPossessionTargetHeld()}
     * 把它顶回满值，所以它实际计的是"从丢掉最后一个目标起"的时间。
     */
    private long targetCooldownDeadlineGameTime = -1L;

    /**
     * 本次附体已经"见过"的索敌目标 UUID。
     * <p>
     * 用来实现"只有<b>新</b>目标才延长倒计时"：同一个目标反复出现（哪怕中间丢失又重获）
     * 都不再给时间；换了个新的才会 +1 分钟。
     */
    private final Set<UUID> possessionSeenTargets = new HashSet<>();

    // ====================== 同化（Assimilation） ======================

    /**
     * 被本核心<b>同化</b>的玩家：玩家 UUID → 他留下的那具空壳 UUID。
     * <p>
     * 详见 {@link OrbAssimilation}：本核心的空壳（或它召唤出来的东西）对玩家造成致命伤害时，
     * 玩家不会被杀死，而是转成旁观、原地留下一具同皮肤的空壳；杀死那具空壳就等于"复活"他。
     * 本核心（"原初"）被击杀或附体结束时会一次性复活所有被同化者。
     * <p>
     * 用 {@link LinkedHashMap} 只是为了持久化与遍历顺序稳定，语义上是个映射表。
     */
    private final Map<UUID, UUID> assimilatedPlayers = new LinkedHashMap<>();

    // ====================== "从哪捡起来的" ======================

    /**
     * 这颗核心<b>被捡起来的位置</b>（维度 + 方块坐标，null = 不知道）。
     * <p>
     * 用户指定："如果幸运核心附体的玩家母体被击败，则幸运核心会回到其原来被捡起来的位置，
     * 并恢复 INITIAL 阶段。（如果是跨维度则直接在该维度的位置生成新的幸运核心）"
     * <p>
     * 来源：玩家空手右击 INITIAL 阶段的核心时，位置会被写进那颗核心<b>物品</b>的数据组件
     * （见 {@link #mobInteract} 与 {@code ModDataComponents.ORB_PICKUP}），
     * 附体开始时（{@link #setPickupSite}）再交给这个实体，随 NBT 落盘。
     */
    @Nullable
    private GlobalPos pickupSite;

    /** 记下"这颗核心是从哪捡起来的"（附体开始时由 {@code OrbOfLuckEvents#triggerPossession} 传入）。 */
    public void setPickupSite(@Nullable GlobalPos site) {
        this.pickupSite = site;
    }

    /** 这颗核心被捡起来的位置；没记过（旧存档的核心、命令直接生成的核心）返回 null。 */
    @Nullable
    public GlobalPos getPickupSite() {
        return this.pickupSite;
    }

    // ====================== 手持物品右击：飞向玩家并进背包（用户指定） ======================

    /**
     * 被"手持物品右击"之后正在飞向的那位玩家；null = 没有正在进行的拾取飞行。
     * <p>见 {@link #mobInteract} 与 {@link #tickPickupFlight()}。
     */
    @Nullable
    private UUID pickupPlayerUuid;

    /** 拾取飞行已经进行了多少刻（超时兜底，见 {@link #PICKUP_FLY_MAX_TICKS}）。 */
    private int pickupFlyTicks;

    /**
     * 拾取飞行的速度（格/刻）：<b>玩家跑步（疾跑）的速度 5.612 格/秒</b>（用户指定"以玩家跑步的速度匀速运动"）。
     * <p>原版口径：行走 4.317 格/秒、疾跑 5.612 格/秒；1 秒 20 刻。
     */
    private static final double PICKUP_FLY_SPEED_PER_TICK = 5.612D / 20.0D;

    /** 飞到离玩家多近就算"到了"（格）。 */
    private static final double PICKUP_ARRIVE_DISTANCE = 1.0D;

    /**
     * 拾取飞行最长持续多少刻（10 秒）—— 到点就把物品塞进背包、核心消失。
     * <p>兜底用：玩家一直以同样的速度跑（核心追不上）、或者中间隔着一堵墙时，
     * 不能让它永远跟着飞；反正"背包里有没有空位"在起手那一刻就查过了，直接交付不会丢东西。
     */
    private static final int PICKUP_FLY_MAX_TICKS = 200;

    /** 开始"飞向这位玩家"（起手时就查过背包有没有空位，见 {@link #mobInteract}）。 */
    private void beginPickupFlight(Player player) {
        this.pickupPlayerUuid = player.getUUID();
        this.pickupFlyTicks = 0;
        // <b>状态保持 INITIAL</b>：渲染器只在 INITIAL 阶段画这颗光球（POSSESSING 阶段它藏在空壳里），
        // 一改成 POSSESSING 这段飞行就"看不见"了 —— 而用户要的正是"看着它飞过来"。
        // 这段时间别的玩家不能再捡：{@link #mobInteract} 开头会挡住（见那里的注释）。
        this.setOrbState(OrbState.INITIAL);
    }

    /**
     * 每刻推进"飞向玩家"：朝玩家当前位置走固定的一步（方向每刻重算，所以玩家跑动时它会拐弯跟上，
     * 但<b>速度恒定</b> = 玩家跑步速度）。贴到 {@link #PICKUP_ARRIVE_DISTANCE} 格以内
     * （或者超时）就把物品交给玩家并消失。
     */
    private void tickPickupFlight() {
        if (!(this.level() instanceof ServerLevel serverLevel) || serverLevel.getServer() == null) {
            return;
        }
        Player player = serverLevel.getServer().getPlayerList().getPlayer(this.pickupPlayerUuid);
        if (player == null || !player.isAlive()) {
            // 玩家下线/死了：取消这次拾取，核心留在原地（仍是 INITIAL，别人还能再捡）
            this.pickupPlayerUuid = null;
            return;
        }
        this.pickupFlyTicks++;
        Vec3 toPlayer = player.getEyePosition().subtract(this.position());
        double distance = toPlayer.length();
        if (distance <= PICKUP_ARRIVE_DISTANCE || this.pickupFlyTicks >= PICKUP_FLY_MAX_TICKS) {
            this.deliverTo(player);
            return;
        }
        // 匀速：把"本刻该走的那一步"直接写成速度（方向每刻重算 → 是一条追着玩家拐的匀速轨迹）
        this.setDeltaMovement(toPlayer.scale(PICKUP_FLY_SPEED_PER_TICK / distance));
        this.hasImpulse = true;
        // 朝向飞行方向（光球各向同性，主要是让客户端的插值/朝向一致，不会有"倒着飞"的观感）
        this.setYRot((float) (Mth.atan2(toPlayer.x, toPlayer.z) * (180.0F / Math.PI)));
        this.setYHeadRot(this.getYRot());
    }

    /** 交付：物品进背包（满则提示并留在原地）、核心消失。 */
    private void deliverTo(Player player) {
        this.pickupPlayerUuid = null;
        ItemStack orbItem = this.createOrbItem();
        // 隐藏倒计时（1 分钟）从"真正拿到手"这一刻起算
        cn.autoforged.joes_addons_for_abmc.item.OrbOfLuckItem.startCountdown(this.level(), orbItem);
        if (!player.getInventory().add(orbItem)) {
            // 飞行途中背包被塞满了：提示一句，核心留在原地（仍是 INITIAL，腾出位置就能再捡）
            this.sendInventoryFullMessage(player);
            return;
        }
        player.getInventory().setChanged();
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.6F);
        this.discard();
    }

    /** 造一件"这颗核心"的物品（带上"被捡起来的位置"）。 */
    private ItemStack createOrbItem() {
        ItemStack orbItem = new ItemStack(ModItems.ORB_OF_LUCK.get());
        // 记下"这颗核心是从哪里被捡起来的"（用户指定：母体被击败后核心要回到这个位置并恢复 INITIAL）。
        // 两种拾取方式（空手 / 手持物品）都走这里，免得两处各写一遍。
        orbItem.set(cn.autoforged.joes_addons_for_abmc.item.ModDataComponents.ORB_PICKUP.get(),
            GlobalPos.of(this.level().dimension(), this.blockPosition()));
        return orbItem;
    }

    /**
     * "物品栏已满！"的提示（白字，走聊天栏）。
     * <p>用户指定：有 <b>0.1%</b> 概率（<b>debug 模式下 50%</b>）换成那句整活文本。
     */
    private static void sendInventoryFullMessage(Player player) {
        float rareChance = cn.autoforged.joes_addons_for_abmc.config.ModConfig.DEBUG_MODE.get()
            ? 0.5F : 0.001F;
        boolean rare = player.getRandom().nextFloat() < rareChance;
        player.displayClientMessage(Component.translatable(rare
            ? "message.joes_addons_for_abmc.orb_inventory_full_rare"
            : "message.joes_addons_for_abmc.orb_inventory_full"), false);
    }

    // ====================== 附体倒计时 ======================

    /** 附体倒计时基准：20 分钟，按<b>游戏刻</b>计（20 分 × 60 秒 × 20 刻）。 */
    public static final int POSSESSION_BASE_TICKS = 20 * 60 * 20;

    /** 每发现一个新的索敌目标，倒计时延长：1 分钟。 */
    public static final int POSSESSION_NEW_TARGET_BONUS_TICKS = 60 * 20;

    /**
     * <b>索敌冷却</b>（用户指定）：附体期间连续这么久<b>手上没有活的目标</b>，
     * 这次附体就主动收场 —— 玩家被放回来、核心回到"原来被捡起来的位置/维度"并恢复 INITIAL，
     * 但<b>不发放击败奖励</b>（奖励只认"空壳被打死"，见 {@link #grantDefeatRewards()}）。
     * 所以这条路的收场是"核心回家"，而不是"核心消失"。
     * <p>
     * <b>只要手上还有活的目标，冷却就一直不算</b>（用户指定）：那具空壳在每刻的索敌里
     * 通过 {@link #onPossessionTargetHeld()} 把截止时间顶回满值，所以真正在计时的是
     * "从它丢掉最后一个目标那一刻起"；目标死了、跑了被丢掉、或者切成了创造/旁观
     * （{@code PlayerShellEntity#dropUntouchableTarget}）都算"没目标"。
     * <p>
     * 与 {@link #POSSESSION_BASE_TICKS}（20 分钟基准倒计时）的分工：那条是"附体拖得太久"的硬上限
     * （归零 = {@code retreat()} + {@code discard()}，核心直接消失），这条是"没活干就回家"的软收场。
     * 两条同时有效，但冷却只有 3 分钟，所以实战里几乎总是冷却先到点。
     * <p>
     * 计时在附体开始的那一刻起算（{@link #startPossession}）：
     * 附体后 3 分钟一个目标都没锁到，核心就回拾取点。
     * <p>
     * 刷怪蛋放出来的那具空壳背后没有核心（{@code possessedOrbUuid == null}），
     * 计时器挂在核心身上，所以它<b>天然没有</b>这条行为（用户指定）；
     * 被同化的空壳只要记着母体（{@code OrbAssimilation} 的 TAG_ORB），
     * 它的目标同样会顶住<b>同一颗母体</b>的这份计时（用户指定："倒计时与母体同步"）。
     */
    public static final int TARGET_COOLDOWN_TICKS = 3 * 60 * 20;

    /** debug 模式下的索敌冷却（用户指定：10 秒，方便立刻验证）。 */
    public static final int TARGET_COOLDOWN_DEBUG_TICKS = 10 * 20;

    /** 当前生效的索敌冷却刻数（debug 模式 10 秒，正常 3 分钟）。 */
    public static int targetCooldownTicks() {
        return cn.autoforged.joes_addons_for_abmc.config.ModConfig.DEBUG_MODE.get()
            ? TARGET_COOLDOWN_DEBUG_TICKS : TARGET_COOLDOWN_TICKS;
    }

    /**
     * 什么算一次"致命打击"：单次攻击的<b>原始伤害</b>（未经护甲/附魔/吸收减免）
     * 达到目标最大生命值的这个比例（用户指定 85%）。
     * <p>
     * 之所以按<b>原始</b>伤害而不是实际掉血：实战里玩家必然穿满盔甲、还可能喝英雄药水，
     * 减伤 60% 以上很常见 —— 20 点原始伤害实际只掉 8 点血，但那已经是一记实打实的重击。
     * <p>
     * 门槛历史：80% → 75% → 50% → 75% → <b>85%</b>（当前）。50% 那一版实测下来太容易攒满
     * （20 血玩家的门槛只有 10 点，闪烁西瓜刀白板正好 10、力量 V 的箭约 12.6、
     * 爆心爆炸 15~43、重锤 186，全都过线），所以回调到 75%；75% 仍然偏松
     * （20 血玩家门槛 15 点，附体的空壳随便一发重击就够），于是抬到 85%
     * —— 20 血玩家的门槛来到 17 点，只有真正的大招才算数。
     */
    public static final double FATAL_HIT_MAX_HEALTH_FRACTION = 0.85D;

    /**
     * 附体阶段核心自己的最大生命值（INITIAL 阶段是 {@link #MAX_HEALTH}）。
     * <p>
     * <b>现在只是个占位数值</b>：附体阶段核心没有碰撞箱、也免疫一切伤害
     * （见 {@link #isInvulnerableTo}），真正会被打的是那具空壳（20 点血，
     * 见 {@code PlayerShellEntity#CORE_BACKED_MAX_HEALTH}）。
     */
    public static final double POSSESSING_MAX_HEALTH = 100.0;

    // ====================== INITIAL 阶段：随机发射 ======================

    /**
     * 下一次"随机发射"的游戏刻；-1 = 还没排期。
     * <p>
     * 不持久化：读档后重新排一次即可（这个特性本身是持续的随机行为，差一次不影响观感）。
     */
    private long nextLaunchGameTime = -1L;

    /**
     * INITIAL 阶段的随机发射定时器：按<b>游戏刻</b>计（{@code /tick freeze} 期间不流逝，
     * 与附体倒计时、变形倒计时同一个判据）。真正的"扔什么、怎么扔"在 {@link OrbInitialLaunch} 里。
     */
    private void tickInitialLaunch() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = serverLevel.getGameTime();
        if (this.nextLaunchGameTime < 0L) {
            this.nextLaunchGameTime = now + OrbInitialLaunch.rollInterval(this.getRandom());
            return;
        }
        if (now < this.nextLaunchGameTime) {
            return;
        }
        this.nextLaunchGameTime = now + OrbInitialLaunch.rollInterval(this.getRandom());
        OrbInitialLaunch.launch(serverLevel, this);
    }

    // ====================== 尺寸 ======================

    /**
     * 核心半径（格）。
     * <p>
     * 这是唯一的世界尺寸来源：{@link ModEntities} 注册时用它、{@link #getDefaultDimensions(Pose)} 用它、
     * 渲染面片也按它换算。改大改小只改这一个数即可。
     */
    public static final float RADIUS = 0.40F;

    /** 呼吸周期（秒），与着色器配置区语义一致。 */
    public static final float BREATH_PERIOD_SECONDS = 8.0F;

    public OrbOfLuckEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
        // 不受重力。这是同步字段，客户端也会同步到。
        this.setNoGravity(true);
        // 默认阶段是 INITIAL，该阶段无敌（NBT 里就是 Invulnerable:1b）。
        this.setInvulnerable(true);
    }

    // ====================== 状态读写 ======================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, OrbState.INITIAL.ordinal());
        builder.define(DATA_SKIN, "");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("OrbState", this.getOrbState().name());
        if (!this.getSkinName().isEmpty()) {
            tag.putString(TAG_SKIN, this.getSkinName());
        }
        if (this.possessorUuid != null) {
            tag.putUUID(TAG_POSSESSOR, this.possessorUuid);
        }
        if (this.vesselUuid != null) {
            tag.putUUID(TAG_VESSEL, this.vesselUuid);
        }
        if (this.previousGameTypeId >= 0) {
            tag.putInt(TAG_PREV_GAMEMODE, this.previousGameTypeId);
        }
        // 附体倒计时与其"见过的目标"名单也要进存档，否则读档后倒计时会从零重来
        if (this.possessionDeadlineGameTime >= 0L) {
            tag.putLong(TAG_DEADLINE, this.possessionDeadlineGameTime);
        }
        if (this.targetCooldownDeadlineGameTime >= 0L) {
            tag.putLong(TAG_TARGET_COOLDOWN, this.targetCooldownDeadlineGameTime);
        }
        if (!this.possessionSeenTargets.isEmpty()) {
            ListTag seen = new ListTag();
            for (UUID id : this.possessionSeenTargets) {
                seen.add(StringTag.valueOf(id.toString()));
            }
            tag.put(TAG_SEEN_TARGETS, seen);
        }
        // 被同化的玩家名单也要进存档：读档后"杀死原初即可一起复活"这条规则才继续有效
        if (!this.assimilatedPlayers.isEmpty()) {
            ListTag list = new ListTag();
            for (Map.Entry<UUID, UUID> entry : this.assimilatedPlayers.entrySet()) {
                CompoundTag one = new CompoundTag();
                one.putUUID("Player", entry.getKey());
                one.putUUID("Shell", entry.getValue());
                list.add(one);
            }
            tag.put(TAG_ASSIMILATED, list);
        }
        // "从哪捡起来的"：维度名 + 打包后的方块坐标（母体被击败时要回到这里）
        if (this.pickupSite != null) {
            tag.putString(TAG_PICKUP_DIM, this.pickupSite.dimension().location().toString());
            tag.putLong(TAG_PICKUP_POS, this.pickupSite.pos().asLong());
        }
        // 注意：Invulnerable 字段由原版 Entity 自己写，INITIAL 阶段存档里就是 Invulnerable:1b
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("OrbState")) {
            try {
                this.setOrbState(OrbState.valueOf(tag.getString("OrbState")));
            } catch (IllegalArgumentException ignored) {
                // 存档里出现了不认识的状态名：保持默认 INITIAL
            }
        }
        this.setSkinName(tag.getString(TAG_SKIN));
        this.possessorUuid = tag.hasUUID(TAG_POSSESSOR) ? tag.getUUID(TAG_POSSESSOR) : null;
        this.vesselUuid = tag.hasUUID(TAG_VESSEL) ? tag.getUUID(TAG_VESSEL) : null;
        this.previousGameTypeId = tag.contains(TAG_PREV_GAMEMODE) ? tag.getInt(TAG_PREV_GAMEMODE) : -1;
        this.possessionDeadlineGameTime = tag.contains(TAG_DEADLINE) ? tag.getLong(TAG_DEADLINE) : -1L;
        this.targetCooldownDeadlineGameTime = tag.contains(TAG_TARGET_COOLDOWN)
            ? tag.getLong(TAG_TARGET_COOLDOWN) : -1L;
        this.possessionSeenTargets.clear();
        if (tag.contains(TAG_SEEN_TARGETS)) {
            ListTag seen = tag.getList(TAG_SEEN_TARGETS, 8); // 8 = StringTag
            for (int i = 0; i < seen.size(); i++) {
                try {
                    this.possessionSeenTargets.add(UUID.fromString(seen.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // 存档里出现坏 UUID：跳过即可，顶多是这个目标再被算作"新面孔"
                }
            }
        }
        this.assimilatedPlayers.clear();
        if (tag.contains(TAG_ASSIMILATED)) {
            ListTag list = tag.getList(TAG_ASSIMILATED, 10); // 10 = CompoundTag
            for (int i = 0; i < list.size(); i++) {
                CompoundTag one = list.getCompound(i);
                if (one.hasUUID("Player") && one.hasUUID("Shell")) {
                    this.assimilatedPlayers.put(one.getUUID("Player"), one.getUUID("Shell"));
                }
            }
        }
        // "从哪捡起来的"：维度名对不上（数据包被删掉了）就当没记过，退回老行为（核心消失）
        this.pickupSite = null;
        if (tag.contains(TAG_PICKUP_DIM) && tag.contains(TAG_PICKUP_POS)) {
            try {
                this.pickupSite = GlobalPos.of(
                    ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.parse(tag.getString(TAG_PICKUP_DIM))),
                    BlockPos.of(tag.getLong(TAG_PICKUP_POS)));
            } catch (Exception ignored) {
                // 存档里维度名坏了：保持 null
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_STATE.equals(key)) {
            // 状态变了，无敌标记跟着走
            this.setInvulnerable(this.getOrbState() == OrbState.INITIAL);
            // 碰撞箱也跟着走：POSSESSING 阶段体积为 0（服务端与本地的 onSyncedDataUpdated 都会走到，
            // 所以两端的箱子一致；属性 SCALE 变化时由原版自己再刷一次，见 LivingEntity#aiStep）
            this.refreshDimensions();
        }
        super.onSyncedDataUpdated(key);
    }

    public OrbState getOrbState() {
        return OrbState.byId(this.entityData.get(DATA_STATE));
    }

    /** 切换阶段。INITIAL 阶段自动无敌（对应 NBT 的 {@code Invulnerable:1b}）。 */
    public void setOrbState(OrbState state) {
        if (this.getOrbState() == state) {
            return;
        }
        this.entityData.set(DATA_STATE, state.ordinal());
        this.setInvulnerable(state == OrbState.INITIAL);
    }

    // ====================== 附体（Orb Possession） ======================

    /** 附体形态用的皮肤（玩家名）。 */
    public String getSkinName() {
        return this.entityData.get(DATA_SKIN);
    }

    public void setSkinName(String name) {
        this.entityData.set(DATA_SKIN, name == null ? "" : name);
    }

    /** 被附体的玩家 UUID（没有附体时为 null）。 */
    public UUID getPossessorUuid() {
        return this.possessorUuid;
    }

    /**
     * 附体期间发现了一个索敌目标：<b>没见过的</b>目标才把倒计时延长
     * {@link #POSSESSION_NEW_TARGET_BONUS_TICKS}（1 分钟），见过的目标什么都不做。
     * <p>
     * 由那具"玩家"空壳在每次索敌时调用（见 {@code PlayerShellEntity#notifyPossessedOrbOfTarget}），
     * 去重放在这里而不是空壳：空壳是临时实体，见过谁只有核心自己记着才靠得住（也随存档持久化）。
     */
    public void onPossessionTargetAcquired(@Nullable UUID targetUuid) {
        if (targetUuid == null || this.getOrbState() != OrbState.POSSESSING) {
            return;
        }
        if (this.possessionSeenTargets.add(targetUuid)) {
            this.possessionDeadlineGameTime += POSSESSION_NEW_TARGET_BONUS_TICKS;
        }
        // 新目标当然也是"活的目标"：顺手把索敌冷却顶满（同一条规则，见 onPossessionTargetHeld）
        this.onPossessionTargetHeld();
    }

    /**
     * 附体期间那具空壳上报"<b>我手上还有活的目标</b>"：把索敌冷却顶回满值。
     * <p>
     * 由空壳<b>每刻</b>调用（见 {@code PlayerShellEntity#reportTargetPresenceToOrb}），
     * 所以"冷却"真正计的是<b>从丢掉最后一个目标那一刻起</b>的时间 —— 只要还有目标在打，
     * 这次附体就不会因为冷却而收场（用户指定）。
     * <p>
     * 注意它和 {@link #onPossessionTargetAcquired(UUID)} 是两件事：那个管"新面孔 +1 分钟"的
     * 20 分钟基准倒计时（同一个目标反复锁上也只算一次），这个只管"有没有活干"。
     */
    public void onPossessionTargetHeld() {
        if (this.getOrbState() != OrbState.POSSESSING) {
            return;
        }
        this.targetCooldownDeadlineGameTime = this.level().getGameTime() + targetCooldownTicks();
    }

    /** 附体倒计时还剩多少游戏刻；未在计时返回 -1。 */
    public long getPossessionTicksLeft() {
        if (this.possessionDeadlineGameTime < 0L) {
            return -1L;
        }
        return Math.max(0L, this.possessionDeadlineGameTime - this.level().getGameTime());
    }

    /** 本次附体已见过的索敌目标数量（调试用）。 */
    public int getPossessionSeenTargetCount() {
        return this.possessionSeenTargets.size();
    }

    /**
     * 索敌冷却还剩多少游戏刻（没发现新目标就归零）；未在计时返回 -1。
     * <p>
     * 给 {@code /jafa orbinfo} 看"还有多久核心就放弃这次附体"。
     */
    public long getTargetCooldownTicksLeft() {
        if (this.targetCooldownDeadlineGameTime < 0L) {
            return -1L;
        }
        return Math.max(0L, this.targetCooldownDeadlineGameTime - this.level().getGameTime());
    }

    // ====================== 同化的读写（详见 OrbAssimilation） ======================

    /** 这名玩家是否正被本核心同化。 */
    public boolean isAssimilating(UUID playerUuid) {
        return this.assimilatedPlayers.containsKey(playerUuid);
    }

    /** 被本核心同化的玩家数量。 */
    public int getAssimilatedCount() {
        return this.assimilatedPlayers.size();
    }

    /** 被同化的全部玩家 UUID（快照，遍历时不会因为复活而边遍历边改）。 */
    public java.util.List<UUID> assimilatedPlayerIds() {
        return new ArrayList<>(this.assimilatedPlayers.keySet());
    }

    /** 某名被同化玩家留下的空壳 UUID；没被同化返回 null。 */
    @Nullable
    public UUID assimilatedShell(UUID playerUuid) {
        return this.assimilatedPlayers.get(playerUuid);
    }

    /** 记下一次同化（由 {@link OrbAssimilation} 调用）。 */
    public void addAssimilated(UUID playerUuid, UUID shellUuid) {
        this.assimilatedPlayers.put(playerUuid, shellUuid);
    }

    /** 移除一条同化记录（复活/清场时调用）。 */
    public void removeAssimilated(UUID playerUuid) {
        this.assimilatedPlayers.remove(playerUuid);
    }

    // ====================== 致命打击计数器（fatal hits） ======================
    //
    // 计数器本身记在**空壳**身上（见 PlayerShellEntity#getFatalHits），不在这里。原因：
    // 由幸运核心产生的空壳有两种 —— 附体主空壳（背后有这颗核心）与刷怪蛋放出来的那一具
    // （背后没有核心）。记在核心上时后者无处可记，整条"致命打击→同化"链会失效。
    // 这里只留给 /jafa orbinfo 取数的辅助。

    /** 附体主空壳当前剩下的致命打击额度（给 /jafa orbinfo 用；没有空壳时返回 -1）。 */
    public int getVesselFatalHits() {
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof PlayerShellEntity shell) {
                return shell.getFatalHits();
            }
        }
        return -1;
    }

    /** 清空附体倒计时的状态（结束附体时调用）。 */
    private void resetPossessionCountdown() {
        this.possessionDeadlineGameTime = -1L;
        this.targetCooldownDeadlineGameTime = -1L;
        this.possessionSeenTargets.clear();
    }

    /**
     * 开始附体：切到 POSSESSING（没有碰撞箱、也不承伤）、记下玩家的皮肤、在玩家坐标生成一具"玩家"身体
     * （{@link PlayerShellEntity} + 头部光球），并把玩家转成旁观。
     * <p>
     * 核心自己不渲染光球形态——它藏在这具身体里，光球由身体渲染在头顶（见
     * {@code PlayerShellRenderer}）。
     *
     * @param pickupSite 这颗核心"原来被捡起来的位置"（可能为 null：旧存档/命令直接生成）；
     *                   母体被击败时核心要回到这里（见 {@link #returnToPickup()}）。
     *                   不落盘的话读档后就找不回来了，所以它随实体 NBT 一起存。
     */
    public void startPossession(ServerPlayer player, @Nullable GlobalPos pickupSite) {
        this.setPickupSite(pickupSite);
        this.setOrbState(OrbState.POSSESSING);
        // 显式声明一次附体阶段的两条规则：没有碰撞箱、不承伤（setOrbState 已经设过无敌标记，
        // 这里防以后改动漏掉 —— 两条判定都读 getOrbState()，见 getDefaultDimensions/isInvulnerableTo）
        this.setInvulnerable(false);
        // 血量上限仍是核心自己那套数值，但附体阶段它不会掉血（承伤方是那具空壳）
        AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(POSSESSING_MAX_HEALTH);
        }
        this.setHealth((float) POSSESSING_MAX_HEALTH);
        // 附体倒计时：基础 20 分钟（游戏刻），之后每发现一个新目标 +1 分钟（见 onPossessionTargetAcquired）
        this.possessionSeenTargets.clear();
        this.possessionDeadlineGameTime = this.level().getGameTime() + POSSESSION_BASE_TICKS;
        // 索敌冷却：从附体这一刻起算（3 分钟 / debug 10 秒）。空壳每刻都会在"手上还有活的目标"时
        // 把它顶回满值（见 onPossessionTargetHeld），所以真正计的是"连续没活干"的时间 ——
        // 附体后一个目标都没锁到、或者目标丢了，满 3 分钟就主动收场（核心回拾取点）
        this.targetCooldownDeadlineGameTime = this.level().getGameTime() + targetCooldownTicks();
        this.setSkinName(player.getGameProfile().getName());
        this.possessorUuid = player.getUUID();
        // 记下附体前的游戏模式，Retreat 时原样还回去
        this.previousGameTypeId = player.gameMode.getGameModeForPlayer().getId();
        // 同时在玩家数据里存一份：玩家离线期间核心若被打死，上线时靠它把模式补回来
        // （见 OrbPossessionEvents）
        player.getPersistentData().putInt(OrbPossessionEvents.TAG_PREV_GAMEMODE, this.previousGameTypeId);
        this.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        // 附体期间核心绝不能被刷掉：它是"附体"这件事的唯一凭据（玩家的旁观状态是它每刻在维持的），
        // 一旦被 checkDespawn 清掉，玩家就永远卡在旁观里出不来了。
        this.setPersistenceRequired();

        this.spawnVessel(player);

        player.setGameMode(GameType.SPECTATOR);
    }

    /**
     * 生成附体用的那具"玩家"身体（空壳 + 头顶光球），并让核心骑上去。
     * <p>
     * 抽出来是因为它有两个使用场景：{@link #startPossession}（首次附体）与
     * {@link #ensureVessel}（读档后身体丢了要补一具）。
     */
    private PlayerShellEntity spawnVessel(ServerPlayer owner) {
        PlayerShellEntity vessel = new PlayerShellEntity(ModEntities.PLAYER_SHELL.get(), this.level());
        vessel.setSkinTexture(this.getSkinName());
        vessel.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), 0.0F);
        // 附体形态要会动：解除 noAi，加入骷髅式走位（变形药水那套空壳不受影响）
        vessel.enablePossessionCombat();
        vessel.setPersistenceRequired();
        vessel.setOrbAttached(true);       // 渲染头部光球
        vessel.setPossessedOrb(this.getUUID());
        // 核心支撑的体质：<b>生命上限 = 被附体前这位玩家的最大生命值</b>（用户指定）
        // + 20 护甲 + 12 韧性 + 保护 16（承伤方就是这具空壳）
        vessel.applyCoreBackedStats(owner.getMaxHealth());
        this.level().addFreshEntity(vessel);
        this.vesselUuid = vessel.getUUID();

        // 核心实体骑到这具身体上：身体是会走位的 AI，会到处跑，核心必须跟着走。
        // 骑乘的作用：① 位置永远对齐（核心已经藏进身体里，必须同进同出）；
        // ② 两者始终待在同一个区块里（乘客由载具的实体刻列表驱动），核心的 tick 不会被区块卸载吞掉；
        // ③ "乘客里有核心"是变形药水免疫与同化归属的判定依据之一
        //    （见 ModMain#isTransmutationPotionImmune 与 OrbAssimilation#possessionOrbOf）。
        this.startRiding(vessel, true);
        return vessel;
    }

    /**
     * 附体期间身体丢了就补一具。
     * <p>
     * 这个检查可以放心地每刻做：核心是骑在身体上的乘客，能 tick 就说明两者的区块都加载着
     * （乘客由载具所在的实体刻列表驱动），所以"查不到身体"只可能是它真的没了
     * （存档异常、被别的机制清理掉等），不会是"暂时没加载"。
     */
    private void ensureVessel(ServerPlayer owner) {
        if (this.vesselUuid != null && this.level() instanceof ServerLevel serverLevel
            && serverLevel.getEntity(this.vesselUuid) instanceof PlayerShellEntity vessel) {
            // 读档后 goal 不会随实体保存下来（命令式注册的），这里补装一次 —— 方法幂等（按 goal 在不在判断），
            // 每刻调也只是遍历两三个 goal。注意：这具主空壳其实现在自己每刻也会补装一遍
            // （见 PlayerShellEntity#enablePossessionCombat 的注释），这里算是双保险。
            vessel.enablePossessionCombat();
            return;
        }
        // 主空壳正被变形药水变成别的东西（物品/方块/生物壳）：产物就是它，别急着补新壳 ——
        // 否则场上会同时出现"产物 + 一具新壳"。
        // 判据用变形数据表，而不是只看产物实体：变成方块时那个下落方块实体落地就没了，
        // 但方块与它的变形数据还在，仍然算"主空壳还在，只是换了个形态"。
        if (cn.autoforged.joes_addons_for_abmc.ModMain.isVesselMorphed(this.getUUID())) {
            return;
        }
        this.vesselMorphProductUuid = null;
        this.spawnVessel(owner);
    }

    /** 主空壳当前的 UUID（附体期间那具 boss 身体）；没有就返回 null。
     *  召唤物们靠它找到"现在这具壳"——壳被变形复原过的话 UUID 是新的（见 {@link #onVesselReverted}）。 */
    @Nullable
    public UUID getVesselUuid() {
        return this.vesselUuid;
    }

    /** 变形中的主空壳产物（物品/下落方块/生物壳）；不在/没加载返回 null。 */
    @Nullable
    private Entity findVesselMorphProduct() {
        if (this.vesselMorphProductUuid == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(this.vesselMorphProductUuid);
    }

    /**
     * 主空壳被变形药水变成了别的东西（见 {@code ModMain#notifyVesselMorphed}）：
     * 记下产物，变形期间不再补新壳（{@link #ensureVessel}），并改为跟着产物走（{@link #followVessel}）。
     * <p>
     * 反过来"变回来"由 {@link #onVesselReverted} 接手。这份记录<b>不落盘</b>：
     * 变形数据表本身只在内存里，退出存档等于这次变形已经作废，重进后照常补一具新壳即可。
     */
    public void onVesselMorphed(@Nullable UUID productUuid) {
        this.vesselMorphProductUuid = productUuid;
    }

    /**
     * 主空壳变回来了（变形解药 / 倒计时到期复原）：重新认领这具新壳。
     * <p>
     * 复原是"照 NBT 重新 new 一个实体"，<b>UUID 变了</b>，核心记的 {@code vesselUuid} 当场失效；
     * 不重认的话核心会以为身体丢了、再补一具新的，于是出现两具 boss 身体。
     */
    public void onVesselReverted(PlayerShellEntity shell) {
        this.vesselMorphProductUuid = null;
        this.vesselUuid = shell.getUUID();
        shell.setPossessedOrb(this.getUUID());
        shell.setOrbAttached(true);
        shell.enablePossessionCombat();
        // 召唤物身上记的"盟友"和它们的共享索敌 goal 都还指着<b>旧壳的 UUID</b>（复原换了 UUID），
        // 不改指的话它们再也找不到这具空壳（站着发呆），空壳真死时也认不出它们。
        if (this.level().getServer() != null) {
            cn.autoforged.joes_addons_for_abmc.entity.OrbPossessionSummons
                .rebindAlly(this.level().getServer(), this.getUUID(), shell.getUUID());
        }
        if (!this.startRiding(shell, true)) {
            this.teleportTo(shell.getX(), shell.getY(), shell.getZ());
        }
    }

    /**
     * 玩家重进存档后的重新接管：把他拉回旁观、并把"附体前的游戏模式"备份补回玩家数据里。
     * <p>
     * 由 {@link OrbPossessionEvents} 在登录时（找到属于他的 POSSESSING 核心后）调用。
     * <b>不</b>在这里碰那具身体：登录那一刻目标区块/实体可能还没加载完，
     * 查不到就去新建会造出第二具空壳；身体的存在性交给 {@link #ensureVessel} 在 tick 里管
     * （那时加载一定已经完成）。
     */
    public void reattachOnLogin(ServerPlayer owner) {
        // 只有"被附体的本人"才把核心记的那份附体前模式补回玩家数据；
        // 被同化的人自己那份备份（在他们自己的 persistentData 里）不能被覆盖掉。
        if (owner.getUUID().equals(this.possessorUuid) && this.previousGameTypeId >= 0) {
            owner.getPersistentData().putInt(OrbPossessionEvents.TAG_PREV_GAMEMODE, this.previousGameTypeId);
        }
        // 附体者与被同化者，重进后都必须是旁观
        if (owner.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            owner.setGameMode(GameType.SPECTATOR);
        }
    }

    /** 结束附体：把生成的那具身体一起收掉（不涉及玩家，Retreat 用 {@link #retreat()}）。 */
    public void endPossession() {
        if (this.vesselUuid != null && this.level() instanceof ServerLevel serverLevel) {
            Entity vessel = serverLevel.getEntity(this.vesselUuid);
            if (vessel != null) {
                vessel.discard();
            }
        }
        this.discardVesselMorphProduct();
        this.discardSummons();
        this.vesselUuid = null;
        this.possessorUuid = null;
        this.resetPossessionCountdown();
    }

    /**
     * 收掉"主空壳变形后的产物"：它要是还在，早晚会按自己的倒计时把主空壳"复原"出来，
     * 而核心那时早已离场 —— 世界里就会多出一具没有核心的 boss 身体。
     * 所以附体结束时除了删掉它的变形数据（见 {@code ModMain#destroyVesselMorphProducts}），
     * 顺手把已经生成的那件产物也收掉（物品/生物壳直接 discard，掉在地上的东西不留）。
     */
    private void discardVesselMorphProduct() {
        Entity product = this.findVesselMorphProduct();
        if (product != null) {
            product.discard();
        }
        this.vesselMorphProductUuid = null;
        cn.autoforged.joes_addons_for_abmc.ModMain.destroyVesselMorphProducts(this.getUUID());
    }

    /** 收掉本次附体召唤出来的一切（Bob、爆炸性苦力怕/猫/僵尸），免得它们留在世界里越积越多。 */
    private void discardSummons() {
        if (this.level().getServer() != null) {
            OrbPossessionSummons.discardAll(this.level().getServer(), this.getUUID());
        }
    }

    /**
     * 确保核心还骑在那具身体上（服务端，每刻调用）。
     * <p>
     * 正常路径下 {@link #startPossession} 已经骑好了，这里只是兜底：读档后乘客关系异常、
     * 身体被别的机制换掉/重新生成时，核心会掉在原地不动 —— 那样它就跟身体分家了
     * （位置对不上、区块可能被卸载，而它又已经藏进身体里、打不到也看不到）。
     * 骑不上去（理论上不会发生）就直接贴到身体坐标上，宁可位置粗一点也不能掉队。
     */
    private void followVessel() {
        if (this.getOrbState() != OrbState.POSSESSING || this.vesselUuid == null) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity vessel = serverLevel.getEntity(this.vesselUuid);
        if (vessel == null) {
            // 主空壳正被变形（或已经没了）：跟着产物走。产物（物品/方块/生物壳）多半不是载具，
            // 骑不上去，所以直接贴坐标 —— 位置对得上就够了。
            Entity product = this.findVesselMorphProduct();
            if (product != null) {
                this.teleportTo(product.getX(), product.getY(), product.getZ());
            }
            return;
        }
        if (this.getVehicle() == vessel) {
            return;
        }
        if (!this.startRiding(vessel, true)) {
            this.teleportTo(vessel.getX(), vessel.getY(), vessel.getZ());
        }
    }

    /**
     * 声明"本核心不会驾驶所骑的载具"——<b>这不是可选项，必须为 false</b>。
     * <p>
     * 原版 {@code Mob#getControllingPassenger()}（Mob.java:218）会把"第一个乘客是 {@link Mob}
     * 且 {@code canControlVehicle()} 为真"当成<b>驾驶员</b>；而 {@code Mob#updateControlFlags()}
     * （每 5 刻执行一次，Mob.java:369）一旦发现有驾驶员，就把该生物 goal 选择器的
     * {@code Goal.Flag.MOVE} 与 {@code Goal.Flag.LOOK} 两个控制位<b>关掉</b>。
     * 后果是空壳身上所有需要走路/转头的 goal（包括 {@link OrbPossessionKiteGoal}）
     * 永远无法启动——表现就是"被附体的空壳只能站桩"，但它照样会站在原地射箭（攻击不走 goal）。
     * <p>
     * 于是这里覆写成 false：核心没有任何 goal、根本不需要走路，而空壳的控制位保持开启。
     * 附带还切断了 {@code getControlledVehicle()} 那条链（Entity.java:3241）——
     * 否则核心的 {@code getNavigation()}/{@code getMoveControl()} 会被转发成<b>空壳的</b>那一套，
     * 等于空壳的导航/移动控制每刻被多 tick 一遍。
     */
    @Override
    public boolean canControlVehicle() {
        return false;
    }

    /**
     * Retreat 事件：那具附体空壳被打死（{@link #onVesselDefeated}）、附体倒计时归零、
     * 或玩家用命令切回创造模式时触发。
     * <ol>
     *   <li>删除那具"玩家"身体；</li>
     *   <li>把玩家传送到身体所在位置；</li>
     *   <li>把玩家的游戏模式恢复成<b>被附体前</b>的那个（创造/生存/冒险）。</li>
     * </ol>
     * 顺序上先恢复模式再传送：免得先传送、再切模式时中间隔了一帧还在旁观。
     */
    public void retreat() {
        this.retreat(false);
    }

    /**
     * Retreat 的实现。
     *
     * @param keepVessel 为 true 时<b>不收掉</b>那具空壳：只给"空壳自己正在死亡"那条路用
     *                   （{@link #onVesselDefeated}）。它后面会被原版正常移除，这里先
     *                   {@code discard()} 会让 {@code isRemoved()} 变 true，把
     *                   {@code LivingEntity#die} 的死亡动画与掉落结算整个跳过。
     */
    private void retreat(boolean keepVessel) {
        ServerPlayer owner = (this.possessorUuid != null && this.level().getServer() != null)
            ? this.level().getServer().getPlayerList().getPlayer(this.possessorUuid)
            : null;

        Vec3 where = this.position();
        if (this.vesselUuid != null && this.level() instanceof ServerLevel serverLevel) {
            Entity vessel = serverLevel.getEntity(this.vesselUuid);
            if (vessel != null) {
                where = vessel.position();
                if (!keepVessel) {
                    vessel.discard();
                }
            }
        }
        // 附体结束 → 把本次召唤出来的 Bob / 爆炸性召唤物一并清场
        this.discardSummons();
        // 主空壳若正以变形形态存在（被变形药水变成物品/方块/生物壳），那件产物也一并收掉，
        // 免得它稍后按自己的倒计时把主空壳"复原"出来（那时核心已经离场了）
        this.discardVesselMorphProduct();
        // "原初"被击杀：被同化的玩家全部一起复活（这就是"杀死原初即可一同复活所有人"）
        OrbAssimilation.reviveAll(this);

        if (owner != null) {
            GameType back = GameType.byId(this.previousGameTypeId);
            if (owner.gameMode.getGameModeForPlayer() != back) {
                owner.setGameMode(back);
            }
            owner.teleportTo(where.x, where.y, where.z);
            owner.getPersistentData().remove(OrbPossessionEvents.TAG_PREV_GAMEMODE);
        }

        this.vesselUuid = null;
        this.possessorUuid = null;
        this.previousGameTypeId = -1;
        this.resetPossessionCountdown();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        // 自回血：每 10 游戏刻 2 点（= 每秒 4 点），不分阶段。满血时跳过，免得白发同步包
        if (this.tickCount % REGEN_INTERVAL_TICKS == 0 && this.getHealth() < this.getMaxHealth()) {
            this.heal(REGEN_AMOUNT);
        }
        // 被"手持物品右击"之后：匀速飞向那位玩家，贴身后把物品交出去（见 mobInteract）。
        // 这一段时间里它只做这一件事（状态已经推到 POSSESSING，下面那些分支都不适用）。
        if (this.pickupPlayerUuid != null) {
            this.tickPickupFlight();
            return;
        }
        // 先保证还挂在身体上：身体会走位，核心掉队的话就跟它分家了
        // （它已经藏进身体里，打不到也看不到，位置必须由载具带着走）
        this.followVessel();
        // INITIAL 阶段：每 10~60 游戏刻朝随机方向"发射"一件随机物品 / 一只随机生物
        // （放在 possessorUuid 判空之前 —— INITIAL 的核心没有附体对象，否则永远走不到）
        if (this.getOrbState() == OrbState.INITIAL) {
            this.tickInitialLaunch();
        }
        if (this.possessorUuid == null || this.level().getServer() == null) {
            return;
        }
        // 同化维护：被同化的玩家一律保持旁观；谁切回创造就当场解除他的同化
        OrbAssimilation.tick(this);
        ServerPlayer owner = this.level().getServer().getPlayerList().getPlayer(this.possessorUuid);
        if (owner == null) {
            // 玩家离线：附体状态原样留着等他回来（核心自己不会被刷掉，见 startPossession）
            return;
        }
        // 玩家用命令切回创造模式 → 此次附体结束：Retreat，然后核心消失
        if (owner.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
            this.retreat();
            this.discard();
            return;
        }
        // 索敌冷却到点（3 分钟 / debug 10 秒手上没有活的目标）→ 解除附体：玩家放回来、
        // 被同化者一并复活（都在 retreat 里），核心回到拾取点并恢复 INITIAL，<b>不发奖励</b>。
        // 放在 20 分钟那条之前：两条同时到点时，优先走"核心回家"这个软收场，而不是让核心直接消失。
        if (this.getOrbState() == OrbState.POSSESSING && this.targetCooldownDeadlineGameTime >= 0L
            && this.level().getGameTime() >= this.targetCooldownDeadlineGameTime) {
            ModMain.LOGGER.info("[附体] 索敌冷却到点（{} 刻内没有活的目标，本次附体共见过 {} 个）：解除附体，核心回拾取点，不发放奖励",
                targetCooldownTicks(), this.possessionSeenTargets.size());
            this.retreat();
            this.returnToPickup();
            return;
        }
        // 附体倒计时归零（20 分钟 + 每个新目标 1 分钟）→ 同样 Retreat、核心消失。
        // 计的是游戏刻，所以 /tick freeze 期间不会流逝（和变形倒计时一个判据）。
        if (this.getOrbState() == OrbState.POSSESSING && this.possessionDeadlineGameTime >= 0L
            && this.level().getGameTime() >= this.possessionDeadlineGameTime) {
            this.retreat();
            this.discard();
            return;
        }
        // 附体期间玩家必须是旁观：重进存档、被服务端 force-gamemode 改成生存、被人用命令改模式……
        // 一律拉回旁观，附体照常继续（只有创造模式才是"结束附体"，见上）。
        if (owner.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            owner.setGameMode(GameType.SPECTATOR);
        }
        // 身体丢了就补一具（读档后壳体被清理之类）
        this.ensureVessel(owner);
    }

    @Override
    public void die(DamageSource source) {
        // 核心被打死 → 同样走 Retreat：收掉身体、把玩家送回身体位置并恢复原游戏模式。
        // 附体阶段正常打不到核心（没有碰撞箱 + 免疫伤害），能走到这里的只有
        // /kill、变形结算这类 BYPASSES_INVULNERABILITY 的伤害；真正的败北判定在
        // onVesselDefeated()（空壳血量归零）。
        this.retreat();
        super.die(source);
    }

    // ====================== 属性 ======================

    /** 最大生命值（点）。 */
    public static final double MAX_HEALTH = 300.0;

    /**
     * 生物属性。基础值照搬 {@link Mob#createMobAttributes()}（移速 0.7 等），生命值覆盖为 {@link #MAX_HEALTH}；
     * 1.21.1 的 {@code createMobAttributes()} 不含攻击属性，而 {@code Mob.aiStep()} 拾取物品时
     * 会查询 {@code ATTACK_DAMAGE}，缺失会崩 —— 与 {@code PLAYER_SHELL} 同理，手动补上。
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, MAX_HEALTH)
            // 抗击退拉满：isPushable()=false 只管"被实体挤开"，管不住被打时的击退位移。
            // 300 点血意味着它会被打很多下，不锁住的话会被一路揍飞（想保留击退就删这一行）。
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.ATTACK_DAMAGE)
            .add(Attributes.ATTACK_SPEED);
    }

    // ====================== AI（暂无） ======================

    /** 目前不加任何 goal：不移动、不乱走。未来的 AI 写在这里。 */
    @Override
    protected void registerGoals() {
    }

    /** 不繁殖：核心没有幼体形态（{@link Animal} 的抽象方法，只能实现）。 */
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    /** 什么都不吃（右键喂食无效）。 */
    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    // ====================== 空手回收 ======================

    /**
     * INITIAL 阶段被玩家右击时把核心交给他：
     * <ul>
     *   <li><b>空手</b>：照旧 —— 那只空手直接变成 Orb of Luck 物品（拿到之后会锁在主手，
     *       见 {@code OrbOfLuckLock}）；</li>
     *   <li><b>手里拿着别的东西</b>（用户指定）：核心<b>以玩家跑步的速度匀速飞向该玩家</b>，
     *       贴身后进他的<b>背包</b>（见 {@link #beginPickupFlight}）。背包满则只发一句提示、
     *       核心留在原地。</li>
     * </ul>
     * 两种都返回 {@code SUCCESS}：<b>这次右击被核心吃掉了</b>，原版不会再去"使用"玩家手里的那件东西
     * （用户指定"并取消使用物品的操作"）。
     * <p>
     * 状态都推进到 {@link OrbState#POSSESSING}（核心"附"到玩家身上），Ultimate 留给以后。
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // 正在飞向某位玩家：这段飞行只属于他，别人再右击一概不理（否则会出现"两个人一起抢"）
        if (this.pickupPlayerUuid != null) {
            return super.mobInteract(player, hand);
        }
        if (this.getOrbState() == OrbState.INITIAL) {
            if (!this.level().isClientSide) {
                ItemStack held = player.getItemInHand(hand);
                if (held.isEmpty()) {
                    // 优先放进主手：拿到之后核心会锁在主手（见 OrbOfLuckLock），
                    // 副手空着却把核心塞进副手的话就锁了个寂寞。
                    InteractionHand target = player.getMainHandItem().isEmpty() ? InteractionHand.MAIN_HAND : hand;
                    ItemStack orbItem = this.createOrbItem();
                    // 隐藏倒计时（1 分钟，见 OrbOfLuckItem#COUNTDOWN_TICKS）从现在起算
                    cn.autoforged.joes_addons_for_abmc.item.OrbOfLuckItem.startCountdown(this.level(), orbItem);
                    player.setItemInHand(target, orbItem);
                    this.setOrbState(OrbState.POSSESSING);
                    this.playPickupSound();
                    this.discard();
                } else if (player.getInventory().getFreeSlot() == -1) {
                    // 背包满了：只提示，核心留在原地（还是 INITIAL，腾出位置可以再来捡）
                    sendInventoryFullMessage(player);
                } else {
                    // 手里拿着东西：核心飞过去、进背包
                    this.beginPickupFlight(player);
                    this.playPickupSound();
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    /** 拾取那一下的动静（两种拾取方式共用）。 */
    private void playPickupSound() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.6F);
    }

    // ====================== 碰撞箱 ======================

    /**
     * 碰撞箱尺寸 = 边长 {@code 2 * RADIUS} 的立方；<b>附体阶段例外，体积为 0</b>
     * （{@link #getDefaultDimensions}）。
     * <p>
     * 不能覆写 {@code getDimensions(Pose)}（它是 final），这个钩子的返回值之后会被乘上
     * {@code Attributes.SCALE}。
     * <p>
     * 这里<b>故意不覆写建箱方法</b>：沿用原版的 {@code y .. y + height}，
     * 箱子下表面就等于实体坐标，召唤/放置时核心正好落在给定高度上。
     */
    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        // 附体阶段：核心没有碰撞箱（体积 0）—— 它藏在那具"玩家"身体里，打不到、炸不到、
        // 也不再有"替身体承伤"这回事（承伤方整体换成了那具空壳，见 PlayerShellEntity）。
        if (this.getOrbState() == OrbState.POSSESSING) {
            return EntityDimensions.scalable(0.0F, 0.0F);
        }
        float diameter = RADIUS * 2.0F;
        return EntityDimensions.scalable(diameter, diameter);
    }

    /**
     * 光晕（公告板面片）比碰撞箱宽 {@link OrbOfLuckRenderer#QUAD_HALF_PER_RADIUS} 倍，
     * 剔除盒必须一起放大，否则站在核心侧面时整团光会被视锥剔除、出现闪烁消失。
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(1.2F * this.getBbWidth() * 0.5F);
    }

    // ====================== 交互与"物理免疫" ======================

    /**
     * 命中判定：附体阶段交给那具"玩家"身体。
     * <p>
     * 核心在 POSSESSING 阶段不渲染自己的形态、藏在身体里，如果自己还可被命中，
     * 玩家会打到一团空气（没有受击反馈）。身体的 {@code hurt} 会把伤害转回核心，
     * 所以"打身体 = 打核心"，只是多了一次受击动画。
     */
    @Override
    public boolean isPickable() {
        return this.getOrbState() != OrbState.POSSESSING;
    }

    /**
     * 不允许被推动（{@code LivingEntity} 默认返回 true）。
     * 保持 false 才能保住"零位移"：玩家走进核心里不会被顶开，核心也不会被别人撞跑。
     * <p>
     * {@code canBeCollidedWith()} 沿用 {@code Entity} 的 false，所以核心不是实体墙，玩家可以穿过去。
     */
    @Override
    public boolean isPushable() {
        return false;
    }

    /** 水流/岩浆流冲不动它（NeoForge 的按流体类型判定版本，非过时的无参版本）。 */
    @Override
    public boolean isPushedByFluid(FluidType type) {
        return false;
    }

    /** 泡在水里也不会溺水：核心没有"氧气"这个概念。 */
    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false;
    }

    /**
     * 免疫摔落伤害。
     * <p>
     * 直接掐掉伤害入口，而不是把 {@code FALL_DAMAGE_MULTIPLIER} 设成 0：后者只是把伤害算成 0，
     * 落地音效/粒子/摔落距离结算都还会走一遍。返回 false 表示"这次落地不产生伤害"。
     */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    /** 发光核心不该被火烧掉。 */
    @Override
    public boolean fireImmune() {
        return true;
    }

    /**
     * <b>永远</b>免疫窒息与溺水伤害（不分阶段，INITIAL/POSSESSING/ULTIMATE 一视同仁）；
     * <b>附体阶段额外免疫一切伤害</b>。
     * <ul>
     *   <li><b>窒息</b>：{@code in_wall} —— 被埋在方块里时原版每刻扣 1 点
     *       （{@code LivingEntity#baseTick} 里 {@code if (this.isInWall()) hurt(inWall, 1.0F)}）。
     *       核心不会自己动，被活塞/落石/放方块埋住是完全可能的，所以直接掐掉伤害入口。
     *       这里只免疫<b>伤害</b>，不改 {@code isInWall()} 的判定本身（那会影响别的逻辑）。</li>
     *   <li><b>溺水</b>：{@code drown} —— 正常途径本来就已经堵住了（覆写的
     *       {@link #canDrownInFluidType} 返回 false，NeoForge 的 {@code onLivingBreathe}
     *       据此判定"能呼吸"，氧气不减、气泡不冒、伤害不发），这里再按伤害类型兜一层：
     *       {@code /damage} 指令、其它 mod 直接施加的溺水伤害也一并免疫。</li>
     *   <li><b>附体阶段</b>：核心"藏进身体里"，既没有碰撞箱（见 {@link #getDefaultDimensions}），
     *       也不再替那具空壳承伤 —— 这一阶段的败北判定整体交给空壳的血量
     *       （见 {@code PlayerShellEntity#die} → {@link #onVesselDefeated}）。
     *       爆炸/坠落方块这类"按碰撞箱结算"的伤害本来还能蹭到它，这里直接按伤害入口封死，
     *       免得出现"空壳还活着，核心先被打死"的第二条败北路径。
     *       <b>仍然放行</b> {@code BYPASSES_INVULNERABILITY}（{@code /kill}、变形结算那类伤害），
     *       否则连 OP 都没法在出问题时清场。</li>
     * </ul>
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (this.getOrbState() == OrbState.POSSESSING
            && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        return source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || super.isInvulnerableTo(source);
    }

    // ====================== 自回血 ======================

    /** 自回血量：每 {@link #REGEN_INTERVAL_TICKS} 游戏刻恢复多少点生命值（2 点 / 10 刻 = 每秒 4 点）。 */
    public static final float REGEN_AMOUNT = 2.0F;

    /**
     * 自回血的计时周期（刻）：10 刻（半秒）。
     * <p>
     * <b>只给核心本体用</b>：那几具"由幸运核心支撑"的空壳原本也照这套数值回血，
     * 现在已改成靠附魔金苹果（见 {@code PlayerShellEntity} 的苹果逻辑）。
     */
    public static final int REGEN_INTERVAL_TICKS = 10;

    /** 对附体远程攻击射出的苦力怕弹爆炸的伤害减免（0.75 = 减伤 75%，只吃四分之一）。
     *  承伤方是<b>那具附体空壳</b>（见 {@code PlayerShellEntity#hurt}）；核心在附体阶段不承伤。 */
    public static final float CREEPER_BLAST_RESISTANCE = 0.75F;

    /** 对附体远程攻击召唤的铁砧雨的伤害减免（0.75 = 减伤 75%，只吃四分之一）。
     *  承伤方同上，是那具附体空壳。 */
    public static final float ANVIL_RAIN_RESISTANCE = 0.75F;

    /**
     * 击败附体母体掉落的<b>经验瓶</b>组数（用户指定：两组 = 128 个）。
     * <p>
     * 一组按原版的 64 个算（{@code ItemStack} 上限）。
     */
    public static final int MOTHER_DEFEAT_XP_BOTTLE_STACKS = 2;

    /** 击败附体母体掉落的<b>幸运方块</b>组数（用户指定：一组 = 64 个）。 */
    public static final int MOTHER_DEFEAT_LUCKY_BLOCK_STACKS = 1;

    /**
     * 附体空壳被打死：这次附体<b>被击败</b>（由 {@code PlayerShellEntity#die} 调用）。
     * <p>
     * 收场流程分两步：
     * <ol>
     *   <li>{@link #retreat(boolean) retreat(true)} —— 把玩家送回空壳倒下的位置、恢复他附体前的游戏模式，
     *       并<b>一次性解除所有被同化者的附体状态</b>（见 {@link OrbAssimilation#reviveAll}：
     *       核心账本里记着所有人，包括"被同化者再同化的人"，因为他们都挂在同一颗核心名下）；</li>
     *   <li>{@link #returnToPickup()} —— <b>核心本体不消失</b>，而是回到"原来被捡起来的位置"
     *       并恢复 INITIAL 阶段（用户指定）。附体阶段核心没有碰撞箱且免疫伤害
     *       （见 {@link #isInvulnerableTo}），所以这是 POSSESSING 阶段唯一的败北判定。</li>
     * </ol>
     * 与"核心自己血量归零"（{@link #die}）、"倒计时归零"、"切回创造模式"那几条路的区别：
     * 那些是"正常收场"，核心会直接消失；只有<b>母体被击败</b>才把核心送回原位、让它重新可被捡取。
     */
    public void onVesselDefeated() {
        if (this.getOrbState() != OrbState.POSSESSING) {
            return;
        }
        // keepVessel = true：那具空壳正在走自己的 die()，让它照常演完死亡动画
        this.retreat(true);
        // 奖励发放点：两组经验瓶 + 一组幸运方块，掉在母体倒下的位置
        this.grantDefeatRewards();
        // 被击败 → 核心回到原来被捡起来的位置并恢复 INITIAL
        this.returnToPickup();
    }

    /**
     * <b>击败附体母体的奖励</b>（用户指定）：在母体倒下的位置掉落
     * <ul>
     *   <li><b>两组经验瓶</b>（2 × 64 个 {@code minecraft:experience_bottle}）；</li>
     *   <li><b>一组幸运方块</b>（64 个 {@code joes_addons_for_abmc:lucky_block}）。</li>
     * </ul>
     * 幸运方块本身挖掉不掉落任何东西（见 {@code LuckyBlock}），这里掉的是实物方块，
     * 所以"击败母体"是它唯一的成规模来源。
     * <p>
     * 只有<b>母体被击败</b>这一条路会走到这里（{@link #onVesselDefeated()}）；
     * 索敌冷却到点那条路<b>不发奖励</b>（直接 {@code retreat()} + {@link #returnToPickup()}），
     * 刷怪蛋/被同化的空壳（背后没有母体）走的则是
     * {@code PlayerShellEntity#die} 里"掉落 100 点经验"那条。
     */
    private void grantDefeatRewards() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        // 掉落位置：此刻核心还骑在那具正在死亡的空壳身上（keepVessel=true），所以这就是母体倒下的地方
        Vec3 at = this.position();
        for (int i = 0; i < MOTHER_DEFEAT_XP_BOTTLE_STACKS; i++) {
            this.dropRewardStack(level, at, new ItemStack(Items.EXPERIENCE_BOTTLE, 64));
        }
        for (int i = 0; i < MOTHER_DEFEAT_LUCKY_BLOCK_STACKS; i++) {
            this.dropRewardStack(level, at,
                new ItemStack(cn.autoforged.joes_addons_for_abmc.block.ModBlocks.LUCKY_BLOCK.get().asItem(), 64));
        }
        ModMain.LOGGER.info("[附体] 击败母体奖励：经验瓶 ×{} 幸运方块 ×{} @({}, {}, {})",
            MOTHER_DEFEAT_XP_BOTTLE_STACKS * 64, MOTHER_DEFEAT_LUCKY_BLOCK_STACKS * 64,
            String.format("%.1f", at.x), String.format("%.1f", at.y), String.format("%.1f", at.z));
    }

    /** 在指定位置掉一份奖励（一小段延迟，免得刚落地就被玩家吸走看不见）。 */
    private void dropRewardStack(ServerLevel level, Vec3 at, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, at.x, at.y + 0.5D, at.z, stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    /**
     * 把核心送回"原来被捡起来的位置"，并恢复 {@link OrbState#INITIAL} 阶段。
     *
     * <h2>同维度 / 跨维度</h2>
     * 用户指定"如果是跨维度则直接在该维度的位置生成新的幸运核心"：
     * <ul>
     *   <li><b>同一个维度</b>：把这颗核心本体（UUID 不变）传回去，重置血量与外观状态；</li>
     *   <li><b>跨维度</b>：实体没法用 {@code teleportTo} 换维度，所以直接在目标维度<b>新生成一颗</b>
     *       INITIAL 核心，原来这颗就地作废。</li>
     * </ul>
     *
     * <h2>没记过位置怎么办</h2>
     * {@link #pickupSite} 为 null（旧存档的核心、命令直接生成的核心）时退回老行为 —— 核心消失。
     */
    private void returnToPickup() {
        GlobalPos site = this.pickupSite;
        MinecraftServer server = this.level().getServer();
        if (site == null || server == null) {
            this.discard();
            return;
        }
        ServerLevel target = server.getLevel(site.dimension());
        if (target == null) {
            ModMain.LOGGER.warn("[附体] 母体被击败，但拾取点所在维度 {} 已不存在，核心就地消失",
                site.dimension().location());
            this.discard();
            return;
        }

        BlockPos pos = site.pos();
        if (target == this.level()) {
            // 同一维度：把这颗核心自己送回去
            this.stopRiding();          // 它可能还骑在那具正在死亡的空壳上
            this.setOrbState(OrbState.INITIAL);   // 同时会把 Invulnerable 设回 true
            this.setSkinName("");
            // 附体时把血量上限改成了 POSSESSING_MAX_HEALTH（100），恢复阶段要还回 300 并补满
            AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(MAX_HEALTH);
            }
            this.setHealth((float) MAX_HEALTH);
            this.setPersistenceRequired();
            this.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            ModMain.LOGGER.info("[附体] 母体被击败：核心已回到拾取点 {} @({}, {}, {}) 并恢复 INITIAL",
                site.dimension().location(), pos.getX(), pos.getY(), pos.getZ());
            return;
        }

        // 跨维度：不能把实体搬过去，就在那个维度的拾取点新生成一颗 INITIAL 核心
        this.discard();
        OrbOfLuckEntity fresh = ModEntities.ORB_OF_LUCK.get().create(target);
        if (fresh == null) {
            ModMain.LOGGER.warn("[附体] 母体被击败，但跨维度重建核心失败（维度 {}）",
                site.dimension().location());
            return;
        }
        fresh.setPickupSite(site);   // 新核心接着记同一个"捡起来的位置"
        fresh.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        fresh.setOrbState(OrbState.INITIAL);   // 同时会把 Invulnerable 设回 true
        fresh.setHealth((float) MAX_HEALTH);
        fresh.setPersistenceRequired();
        target.addFreshEntity(fresh);
        ModMain.LOGGER.info("[附体] 母体被击败：已在拾取点所在维度 {} 重新生成 INITIAL 核心 @({}, {}, {})",
            site.dimension().location(), pos.getX(), pos.getY(), pos.getZ());
    }

    // ====================== 呼吸相位 ======================

    /**
     * 呼吸相位（0 = 最弱，1 = 最强），与着色器配置区的 MIN/MAX_INTENSITY 配合使用。
     * <p>
     * 用关卡游戏时间而不是实体自身的 tickCount：同一帧内所有核心相位一致（不会各呼吸各的），
     * 而且这个值对整批顶点都相同，顶点色传参天然对批渲染安全。
     */
    public static float breathAt(long gameTime, float partialTick) {
        double seconds = (gameTime + (double) partialTick) / 20.0;
        double phase = 2.0 * Math.PI * seconds / Math.max(BREATH_PERIOD_SECONDS, 0.001);
        return (float) (0.5 - 0.5 * Math.cos(phase));
    }
}
