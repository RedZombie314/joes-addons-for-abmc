package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.block.LuckyRoster;
import cn.autoforged.joes_addons_for_abmc.block.SupportGift;
import cn.autoforged.joes_addons_for_abmc.config.ModConfig;
import cn.autoforged.joes_addons_for_abmc.crafting.CraftingVisuals;
import cn.autoforged.joes_addons_for_abmc.mixin.MobPersistenceAccessor;
import com.mojang.math.Transformation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 幸运方块选择器（Lucky Selector）：一个飞行的「选择框」实体。
 * <p>
 * <b>AI</b>：平时用 {@link SelectorWanderGoal} 在距地表 3~10 格的高度空中游荡；
 * 收到 {@code /jafa seek} 后进入<b>抓取流程</b>（见 {@link #startSeek()}），期间游荡 AI 让位。
 * <p>
 * <b>抓取与发送的对象可以是生物</b>（{@code /jafa seek} → 找最近的生物 → 播抓取动画 →「无AI化」并骑到本体上；
 * {@code /jafa send} → 播发送动画 → 上升、缩小、删除）。<b>物品抓取流程保留但已不再由命令触发</b>——
 * 入口是 {@link #startSeekItem()}，两条流程共用同一套 {@link SeekPhase} 状态机，用 {@link SeekTargetKind} 区分。
 * <p>
 * <b>动画</b>：三段动画（展开 / 收回 / 发送）不自动播放。目前 {@code seek} 会触发
 * {@link SelectorAnimation#GRAB}（客户端表现为 stretch 播完紧接 retreat，中途不回到默认姿态）。
 * {@code AnimationState} 只在客户端被模型读取，而流程逻辑跑在服务端，所以这里用<b>同步实体数据当信号</b>：
 * 服务端把对应计数 +1 → 客户端 {@code tick} 发现计数变了就执行对应指令。
 * 这正是原版生物驱动动画的常规做法，不需要额外的网络包。
 * <p>
 * <b>移动</b>：走原版那套——{@code FlyingMoveControl} 读 {@code Attributes.FLYING_SPEED} 并把朝向/升降交给它，
 * 位移仍然是 {@code LivingEntity#travel}（所以推力、击退、方块碰撞全是原版行为，没有被覆写）。
 * 想按情况换速度就用 {@link #getFlightSpeedMultiplier()}（抓取/发送时它返回 2 倍）。
 * <p>
 * <b>碰撞</b>：碰撞箱正好 1×1 格（见 {@code ModEntities.LUCKY_SELECTOR} 的 {@code sized(1.0F, 1.0F)}），
 * 走的是<b>常规碰撞</b>——不覆写 {@code isPushable()}、不设 {@code noPhysics}，
 * 所以它能被推、会被方块挡住，不是潜影贝那种「硬碰撞箱」。
 * <p>
 * <b>重力</b>：构造时 {@code setNoGravity(true)}，属性里 {@code GRAVITY} 也设成 0，双保险（见 {@link #createAttributes()}）。
 */
public class LuckySelectorEntity extends PathfinderMob {

    // ===== 动画 =====
    /** 展开：选择框从 1 格撑开成选区大小。 */
    public final AnimationState stretchAnimationState = new AnimationState();
    /** 收回：从展开态缩回 1 格。 */
    public final AnimationState retreatAnimationState = new AnimationState();
    /** 发送：确认 / 下发时的脉冲（带缩放）。目前还没有流程用它。 */
    public final AnimationState sendAnimationState = new AnimationState();

    // 触发信号：自增计数而不是布尔，这样同一条指令连按两次也能重新播。
    // 放在同步实体数据里，服务端一改客户端就能收到（见 requestAnimation 注释）。
    private static final EntityDataAccessor<Integer> DATA_GRAB_SIGNAL =
        SynchedEntityData.defineId(LuckySelectorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEND_SIGNAL =
        SynchedEntityData.defineId(LuckySelectorEntity.class, EntityDataSerializers.INT);
    /**
     * 「我身上驮着的是一只生物（而不是物品展示实体）」。
     * <p>
     * <b>为什么这个必须同步</b>：落座位置是<b>客户端算的</b>——乘客的位置不由服务端发包，而是两端各自用
     * {@code Entity#positionRider} 每刻从载具位置推出来（ServerEntity.java:122-134 对乘客只发朝向）。
     * 而生物落座要「居中装在框里」（见 {@link #getPassengerRidingPosition}），如果只让服务端这么算，
     * 客户端还会按原版挂点把它摆在框顶上，看到的就是错的。所以这个状态要同步过去。
     * <p>
     * 不用生物实体 ID 而用布尔：载具的乘客位只有 1 个（{@code Entity#canAddPassenger}），
     * 「是不是生物」这一位信息就够了，而且读档后实体 ID 会变、布尔不会失真。
     */
    private static final EntityDataAccessor<Boolean> DATA_CARRIED_MOB =
        SynchedEntityData.defineId(LuckySelectorEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * 同步给客户端的"本刻正在执行脚本化动作（抓取/发送）"位。
     * <p>
     * 服务端的 {@link #isBusy()} 是当场算出来的（阶段字段不过网），而客户端需要知道这件事来
     * <b>暂停旁观操控的本地预测</b>：seek/send 期间那只盒子由动画自己飞，这时候还按玩家的按键去预测，
     * 就会和权威轨迹越差越远、然后被纠正包一次次拽回来（看起来是抖）。见 {@code SelectorPilotClient}。
     * <p>
     * 顺带的好处：动画期间服务端也每刻发位置（{@link #tickPilot()} 里那个 {@code hasImpulse}），
     * 抓取/发送动画在客户端比原来（3 刻一个位置包）顺得多。
     */
    private static final EntityDataAccessor<Boolean> DATA_BUSY =
        SynchedEntityData.defineId(LuckySelectorEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * 一段动画的时长（刻）：stretch / retreat 都是 1.0 秒 = 20 刻。
     * <p>
     * 这个值必须等于 {@code LuckySelectorAnimations.STRETCH} 的时长。之所以写死而不是去读动画定义：
     * 动画定义在客户端专属的 {@code LuckySelectorAnimations} 里，而这段逻辑跑在通用侧
     * （服务端也会构造这个实体），引用它会连累专用服务器。
     * <b>以后若在 Blockbench 里改了时长，这里要跟着改。</b>
     */
    private static final int ANIMATION_TICKS = 20;

    /** 客户端已处理到的信号值，用来判断「又收到一次指令」。 */
    private int seenGrabSignal;
    private int seenSendSignal;
    /** grab 的链式倒计时：&gt;0 表示 stretch 正在播、还剩这么多刻后接 retreat。 */
    private int grabChainTicks;

    /** 动画指令。 */
    public enum SelectorAnimation {
        /** 抓取：先展开（stretch），播完紧接着收回（retreat），中途不回到默认姿态。 */
        GRAB,
        /** 发送：播放发送脉冲（send）。 */
        SEND
    }

    // ===== 移速 =====
    /** 默认移速倍率：1.0 = 用属性的原值。 */
    public static final double DEFAULT_FLIGHT_SPEED_MULTIPLIER = 1.0D;

    /**
     * 抓<b>生物</b>时前往目标的移速倍率：4 倍。
     * <p>
     * 比抓物品快一倍（{@link #SEEK_ITEM_FLIGHT_SPEED_MULTIPLIER}）：生物会自己走、还会被地形挡，
     * 悬停点每刻都在动，2 倍追起来太慢，追不上就只能一直重算悬停点。
     */
    public static final double SEEK_MOB_FLIGHT_SPEED_MULTIPLIER = 4.0D;

    /** 抓物品时前往目标的移速倍率：2 倍（掉落物不会动，不用那么急）。 */
    public static final double SEEK_ITEM_FLIGHT_SPEED_MULTIPLIER = 2.0D;

    /**
     * 移速倍率（乘在 {@code Attributes.FLYING_SPEED} 上）。
     * <p>
     * 原版 {@code MoveControl} 的每个目标点都自带一个 speedModifier（{@code setWantedPosition(x, y, z, speed)}
     * 的第 4 个参数），将来某些特殊情况要用别的速度时，在这个方法里按状态分支返回不同倍率即可
     * （比如按 {@link #seekPhase}、{@link #seekTargetKind}、某个同步标志，或「是否正在执行某段 AI」来判断），
     * <b>不需要动移动逻辑本身</b>。目前：抓生物 4 倍、抓物品 2 倍、其余（含游荡）1 倍。
     */
    public double getFlightSpeedMultiplier() {
        if (this.seekPhase == SeekPhase.NONE) {
            return DEFAULT_FLIGHT_SPEED_MULTIPLIER;
        }
        return this.seekTargetKind == SeekTargetKind.MOB
            ? SEEK_MOB_FLIGHT_SPEED_MULTIPLIER
            : SEEK_ITEM_FLIGHT_SPEED_MULTIPLIER;
    }

    // ===== 抓取流程 =====
    /** 1 格 = 16 像素（配置里的高度都用像素表示）。 */
    private static final double PIXELS_PER_BLOCK = 16.0D;
    /** 搜索掉落物的半径（格）。 */
    private static final double SEEK_ITEM_RANGE = 32.0D;
    /** 搜索可抓生物的半径（格）。 */
    private static final double SEEK_MOB_RANGE = 32.0D;
    /**
     * 「已经飞到悬停点」的判定距离平方（0.5 格²）。
     * <p>
     * 0.5 格是给「本体碰撞箱下表面贴住生物碰撞箱上表面」留的余量：悬停点每刻都跟着生物动，
     * 而 {@code FlyingMoveControl} 到点前<b>不会减速</b>（它只在 2.5e-7 格以内才停下），
     * 4 倍速下每刻能走 0.24 格，判定太紧就会绕着目标转圈。需求里「也可高 0.5 格」正好对上这个容差。
     */
    private static final double SEEK_ARRIVE_SQR = 0.25D;
    /** retreat 里被抓的东西开始上升的时间码：0.5 秒 = 10 刻。 */
    private static final int LIFT_START_TICKS = 10;
    /** retreat 里被抓的东西升到位的时间码：0.75 秒 = 15 刻。 */
    private static final int LIFT_END_TICKS = 15;

    /**
     * 抓取时把生物逐渐压进框所用的刻数：整个 RETREAT 段（20 刻）。
     * <p>
     * 每一步只是「关键帧」——真正看起来是连续的关键在客户端的
     * {@code LivingEntityScaleInterpolationMixin}：属性每刻同步一次，而它在渲染时按帧在
     * 「上一刻的值 → 这一刻的值」之间插值。所以这里 20 步铺满 1 秒，视觉上是平滑压缩，
     * <b>不是</b>「锁定那一刻啪一下缩到最小」。
     * <p>
     * 放在 RETREAT（收回）段而不是 STRETCH（展开）段，是刻意的：展开段是选择框张开、
     * 露出里面要抓的东西；收回段才是「框合拢、把它压/吸进去」，缩小跟着合拢走才讲得通。
     */
    private static final int CARRY_FIT_STEPS = ANIMATION_TICKS;

    // ===== 抓取生物的持久化标记（写在<b>被抓的生物</b>自己的持久化数据里）=====
    /**
     * 为什么标记写在生物身上、而不是记在选择器字段里：这些标记要跟着<b>生物</b>的 NBT 存档。
     * 选择器在读档后可能已经不是同一段代码路径进来的（字段丢了），但生物的标记还在，
     * 于是 {@code getCarriedMob()} 能靠它把「这只生物正被抓着」的状态找回来，
     * {@code releaseCapturedMob()} 也能凭 {@code TAG_WAS_*} 把抓取前的 AI / 重力 / 持久化原样还回去。
     */
    public static final String TAG_CAPTURED = "jafa_selector_captured";
    public static final String TAG_WAS_NO_AI = "jafa_selector_was_no_ai";
    public static final String TAG_WAS_NO_GRAVITY = "jafa_selector_was_no_gravity";
    public static final String TAG_WAS_PERSISTENT = "jafa_selector_was_persistent";
    /**
     * 抓到之后「手上生物不在这台载具上」是从哪一刻开始算的（{@code gameTime}）。
     * 由 {@link #healOrphanedCapture(Mob)} 在第一次看到它悬空时写下，用来区分
     * 「send 流程中途的正常悬空」和「选择器没了、生物被留下的残留」。
     */
    public static final String TAG_DETACHED_SINCE = "jafa_selector_detached_since";

    /**
     * 悬空多久之后判定为抓取残留并放掉（刻）。取 100 = 5 秒，两个正常窗口都远在它之内：
     * 抓取流程里「抓住到骑上」是 stretch + retreat 共 40 刻，send 全程 17 刻。
     */
    public static final int CAPTURE_ORPHAN_GRACE_TICKS = 100;

    /** send 缩小时用的属性修饰符 id：写 SCALE 而不是改基础值，避免冲掉别人（比如巨型生物）设的体型。 */
    private static final ResourceLocation SHRINK_MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "selector_send_shrink");

    /**
     * 抓在手上时把生物缩进 1×1×1 选择框用的属性修饰符 id。
     * <p>
     * 和 {@link #SHRINK_MODIFIER_ID} 分开两个 id：这个负责「装得下」（抓取全程都在），
     * 那个负责 send 时的「缩到消失」（只在发送时叠上去），两层相乘互不干扰。
     */
    private static final ResourceLocation CARRY_FIT_MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "selector_carry_fit");

    /** 选择框的碰撞箱是 1×1×1（见 {@code ModEntities.LUCKY_SELECTOR} 的 {@code sized(1.0F, 1.0F)}）。 */
    private static final double SELECTOR_BOX_SIZE = 1.0D;

    /**
     * 缩放留的余量：碰撞箱装进框的 {@code 0.9} 倍即止，而不是刚好贴着框壁。
     * <p>
     * 留这一成是因为<b>模型不一定和碰撞箱一样大</b>——蜘蛛的腿、监守者的手臂之类会伸出碰撞箱，
     * 按 1.0 贴着缩的话它们还是会从框里戳出来。给 0.9 的余量，视觉上才是「装进去了」。
     * 本来就装得下的小生物不受影响（倍率上限是 1）。
     */
    private static final double CARRY_FIT_MARGIN = 0.9D;

    /** 抓取流程的目标类型。 */
    public enum SeekTargetKind {
        /** 生物：抓到手之后它会骑到本体上。 */
        MOB,
        /** 掉落物：抓到手之后换成物品展示实体。 */
        ITEM
    }

    /** 抓取流程的阶段。 */
    public enum SeekPhase {
        /** 没有在抓取，走平时的游荡 AI。 */
        NONE,
        /** 正以 2 倍速飞向目标正上方的悬停点。 */
        APPROACH,
        /** 已锁定目标并<b>立刻停住</b>（不再移动），正在播 stretch。 */
        STRETCH,
        /** 仍在原地不动，正在播 retreat，并在 0.5~0.75 时间码之间把目标匀速抬上来。 */
        RETREAT
    }

    private SeekTargetKind seekTargetKind = SeekTargetKind.MOB;
    private SeekPhase seekPhase = SeekPhase.NONE;
    /** 当前阶段已过的刻数。 */
    private int seekTicks;
    /** 抓取目标的掉落物（只有服务端持有；换上展示实体后清空）。 */
    @Nullable
    private ItemEntity seekItemTarget;
    /** 抓取目标的生物（只有服务端持有；进 STRETCH 那一刻就转成 {@link #carriedMob} 并清空）。 */
    @Nullable
    private Mob seekMobTarget;
    /**
     * 本次抓取向 {@link SelectorTargetClaims} <b>认领</b>的那个目标。
     * <p>
     * 认领发生在锁定那一刻（{@link #startSeekMob(Mob)} / {@link #startSeekItem(ItemEntity)}），
     * 作用是让别的选择器不再选同一个东西；这里是"我占的是谁"，收尾/中止时照着它释放。
     */
    @Nullable
    private Entity claimedTarget;
    /**
     * 当前携带的物品展示实体（只有服务端持有）。
     * <p>
     * 从 {@link SeekPhase#STRETCH} 结束那一刻起，掉落物被删除、由它代表那个物品；抓取收尾时它会骑到本体上。
     * 所以流程结束后<b>刻意不清空</b>——它是物品的新载体，交给后续流程（{@code /jafa send}）处置。
     */
    @Nullable
    private Display.ItemDisplay carriedDisplay;
    /**
     * 当前携带的生物（只有服务端持有；骑乘关系随存档保存，字段读档后靠乘客 + 标记找回，见 {@link #getCarriedMob()}）。
     * <p>
     * 和 {@link #carriedDisplay} 一样，抓取结束后刻意保留。
     */
    @Nullable
    private Mob carriedMob;
    /** 抬升的起、终点（在时间码 0.5 那一该定下，之后逐刻在两点间插值）。 */
    private Vec3 seekLiftFrom = Vec3.ZERO;
    private Vec3 seekLiftTo = Vec3.ZERO;
    /**
     * 抓取时把生物压到「装得进框」的目标倍率（抓住那一刻按它<b>当时</b>的碰撞箱算一次，之后不再重算）。
     * <p>
     * 为什么只算一次：倍率是<b>相对</b>它当前大小的，而缩小的过程本身就在改它的大小，
     * 每刻重算会变成「边缩边降目标」的追尾，永远缩不到位。
     */
    private double carryFitTarget = 1.0D;
    /**
     * 被抓生物当前应当待的位置。
     * <p>
     * 抓稳之后到骑上座位之前，它和本体是两个各自独立的实体，而两者的碰撞箱是<b>重叠</b>的——
     * 重叠就会被每刻互相推开（{@code Entity#push}），不按着的话它会一点点从框里滑出去。
     * 所以这里记住「此刻它该在哪」，每刻摆回去（抬升段会逐刻更新这个值）。
     */
    private Vec3 carriedMobHold = Vec3.ZERO;

    // ===== 发送流程（/jafa send）=====
    /** 发送流程两段的时长（刻）：上升 0~0.5 秒 = 10 刻；物品缩小消失 0.5~0.75 秒 = 5 刻。 */
    private static final int SEND_RISE_TICKS = 10;
    private static final int SEND_SHRINK_TICKS = 5;
    /**
     * 缩小走完之后再多等 2 刻才删除展示实体：1 刻是 {@code start_interpolation} 的延迟，1 刻留给网络。
     * 多等是无害的——插值结束时缩放已经是 0，展示实体早就看不见了。
     */
    private static final int SEND_SHRINK_LINGER_TICKS = SEND_SHRINK_TICKS + 2;

    /**
     * 生物 send 全程（刻）：和选择器自己的 send 动画一样长（{@link #ANIMATION_TICKS} = 1 秒）。
     * <p>
     * 体型<b>全程</b>逐刻缩小，所以步数就是这里给的刻数。步数不是随便定的：客户端只能在服务端每刻给的
     * 值之间插值（{@code LivingEntityScaleInterpolationMixin}），帧率低于 20 时连插值都插不出来，
     * 于是<b>肉眼看到的档数就等于步数</b>。想「看得出在缩」，就只能把时间拉长、把步数堆多。
     */
    private static final int MOB_SEND_TICKS = ANIMATION_TICKS;
    /** 生物缩小的步数：全程每刻一步。 */
    private static final int MOB_SHRINK_STEPS = MOB_SEND_TICKS;
    /** 缩到最小之后再停 1 刻才删除，好让客户端那一步的插值走完。 */
    private static final int MOB_SEND_TOTAL_TICKS = MOB_SEND_TICKS + 1;

    /** {@code Attributes.SCALE} 的下限（Attributes.java:124-126），也是缩小的终点倍率。 */
    private static final double SHRINK_MIN_FACTOR = 0.0625D;

    /** 发送流程的阶段。 */
    public enum SendPhase {
        /** 没有在发送。 */
        NONE,
        /** 时间码 0~0.5：带着被抓的东西匀速向上（距离由配置决定）。 */
        RISE,
        /** 时间码 0.5~0.75：被抓的东西匀速缩小到消失，结束后删除它。 */
        SHRINK
    }

    private SendPhase sendPhase = SendPhase.NONE;
    /** 当前阶段已过的刻数。 */
    private int sendTicks;
    private Vec3 sendRiseFrom = Vec3.ZERO;
    private Vec3 sendRiseTo = Vec3.ZERO;
    /**
     * 正在被 send 流程处置的生物。
     * <p>
     * send 要自己摆它的位置，所以开始时会 {@code stopRiding()} 把它摘下来；而
     * {@link #removePassenger(Entity)} 里「一脱离就还原 AI/重力」的规则必须在<b>这一次</b>主动摘下时让路
     * （否则它会在上升途中恢复重力直接掉下去），所以这里单独记一笔。
     */
    @Nullable
    private Mob sendingMob;

    /**
     * 这一次 send 要"送到谁身边"（需求 6.5.21）；为 {@code null} 就是默认那条（收进幸运名单）。
     * <p>
     * 由 {@link #startPilotSend(net.minecraft.server.level.ServerPlayer)} 设上，收尾时用掉即清。
     * <b>不存档</b>：send 全程只有 1 秒，跨读档还留着目标反而会送错地方。
     */
    @Nullable
    private net.minecraft.server.level.ServerPlayer pilotGiftTarget;

    // ===== 旁观操控（需求 6.4.36）=====

    /** 操控模式的速度上限：<b>8.634 米/秒</b>（需求：所有移速 ×2；原值 4.317 米/秒 = 原版玩家步行速度）。 */
    public static final double PILOT_SPEED_PER_TICK = SelectorPilotMotion.SPEED_PER_TICK;

    /**
     * 旁观操控下右键"抓准星所指的东西"的最大距离（格）：<b>128</b>。
     * <p>
     * 取这个数的理由：它就是 AI 自己搜索目标的半径（{@code SelectorCollectGoal}），
     * 也是本维度生物的消失半径 —— 能看见、能指到的东西基本都在这个范围内。
     * 客户端做射线时用它，服务端复核时也用它（见 {@link SelectorPilot#handleUse}）。
     */
    public static final double PILOT_SEEK_REACH = 128.0D;

    /**
     * 操控模式的加速度（格/刻²）：0.1 → 约 <b>4.3 刻（0.22 秒）</b>加到全速；
     * 松手时用同一个值减速，所以是"滑停"而不是急刹（需求：停止按键时要有减速度）。
     * 数值与方向都定义在 {@link SelectorPilotMotion} 里（客户端预测要用同一份）。
     */
    public static final double PILOT_ACCEL_PER_TICK = SelectorPilotMotion.ACCEL_PER_TICK;

    /**
     * 本刻有没有"正被玩家操控"。
     * <p>
     * 为真时：{@link #travel(Vec3)} 只拦不动，位移全部由 {@link #tickPilot()} 当场完成，
     * 两个 AI goal（游荡 / 自动收集）也都会让位。
     * <p>
     * 松手之后它还会保持一小会儿——直到速度滑停到"静止"以下，这样"松开 W 之后慢慢停住"才成立；
     * 停住之后才把控制权交还给 AI。<b>不存档</b>：它纯粹是当前这一刻的运行状态。
     */
    private boolean piloted;

    /**
     * <b>本刻的位移是不是由操控输入驱动</b>（{@link #travel(Vec3)} 要不要让位）。
     * <p>
     * <b>为什么必须和 {@link #piloted} 分开</b>（6.5.20 修的 bug）：seek 的接近段是
     * <b>靠移动控制飞的</b>——{@code tickSeek()} 里 {@code getMoveControl().setWantedPosition(...)}，
     * 真正的位移由原版 {@code LivingEntity#travel} 完成。而 6.5.17 为了消延迟把 {@code travel}
     * 在 {@code piloted} 为真时整个拦掉了，于是"玩家操控着盒子时按右键去 seek"会出现：
     * 抓取流程确实开始了（服务端日志也在打"开始抓取"），但盒子<b>一步都不动</b>，
     * 客户端那边又因为动画期间暂停了预测，看起来就是"完全没反应"。
     * <p>
     * 现在分成两层：{@link #piloted} 表示"有玩家在操控"（AI 让位、动画期间也保持），
     * 这个字段表示"这一帧的位移归操控输入"（只在真正操控时才是真，动画期间是假，
     * 好让移动控制把盒子飞向目标）。
     */
    private boolean pilotDriving;

    /**
     * <b>只在客户端用</b>：本地预测的速度。
     * <p>
     * 旁观操控时画面里的盒子就是本地实体，而它每刻都会被服务端的权威位置包"拉"到一刻之前的位置。
     * 为了手感跟手，客户端自己也跑一遍 {@link SelectorPilotMotion}（与服务端逐字相同），
     * 并把预测结果记在这里，下一帧接着往下推。
     */
    @Nullable
    private Vec3 clientPilotVelocity;

    /** <b>只在客户端用</b>：本地预测到的位置（"我这一步走到哪儿了"）。 */
    @Nullable
    private Vec3 clientPilotPredicted;

    /**
     * 手里（或正在抓）的这把东西是不是玩家用 {@code /jafa seek} 抓的（不是 AI 自动抓的）。
     * <p>
     * 只存在于内存（不存档）：读档后按"AI 抓的"处理——那时候玩家早就不在场了，
     * 正好让 AI 把它送进名单，免得一只带着东西的选择器永久占着整个维度的名额。
     * 用途见 {@link SelectorCollectGoal}：手动抓的那把东西会多留
     * {@code MANUAL_SEND_GRACE_TICKS} 刻才自动送走。
     */
    private boolean manualCapture;

    public LuckySelectorEntity(EntityType<? extends LuckySelectorEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        // 需求 6.4.32：选择器一律无敌（Invulnerable = true），而且与<b>怎么来的</b>无关——
        // 刷怪蛋、/summon、自然生成、代码 create(...) 全都经过这个构造器，所以这里是唯一一个能
        // 一次盖住所有入口的地方。读档/命令带 NBT 的情况由 readAdditionalSaveData 再兜一道。
        this.setInvulnerable(true);
        // 飞行移动控制：maxTurn = 20、hoversInPlace = true（与原版蜜蜂/恶魂同参数）
        this.moveControl = new FlyingMoveControl(this, 20, true);
    }

    /**
     * 读档/NBT 之后再钉一次无敌。
     * <p>
     * {@code Entity#load} 会读 NBT 里的 {@code Invulnerable}，所以 {@code /summon} 带
     * {@code {Invulnerable:0b}}（或别的模组改过这一位）能把构造器设的值盖回去；这里在读完 NBT 之后
     * 再强制置 true，保证"无论何种方式生成/加载的选择器都无敌"。
     * <p>
     * 无敌<b>不影响</b> {@code /kill} 与本维度那条"离玩家 &gt;128 格即删除"的规则：它们走的是
     * {@code Entity#kill()/discard()}（直接移除实体），根本不经过伤害判定。
     */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setInvulnerable(true);
    }

    // ===== 自然生成 =====

    /**
     * 自然生成时的标记，由 {@link LuckySelectorSpawner} 在生成那一刻调用。
     * <p>
     * 刷怪蛋、{@code /summon}、代码里 {@code create(...)} 出来的选择器<b>不走这里</b>，所以那些不会自带发光。
     * <p>
     * 做两件事：
     * <ol>
     *   <li><b>永久发光</b>：{@code MobEffectInstance.INFINITE_DURATION} 就是 -1（MobEffectInstance.java:30），
     *       表示永不过期；状态效果会随实体一起存进 NBT，读档后依然在；</li>
     *   <li><b>PersistenceRequired 与"内含东西"对齐</b>，见 {@link #refreshPersistenceFromContent()}。</li>
     * </ol>
     */
    public void markNaturalSpawn() {
        this.addEffect(new MobEffectInstance(
            MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        // 记一笔"我是自然生成的"：healStalePersistence() 只肯给自然生成的选择器恢复持久化状态，
        // 刷怪蛋/命令放下的那些不动（它们的 PersistenceRequired 是玩家自己要的）。
        this.getPersistentData().putBoolean(TAG_NATURAL_SPAWN, true);
        this.refreshPersistenceFromContent();
    }

    /**
     * 是否<b>内含</b>了东西：携带的生物（{@code /jafa seek} 抓到的）或携带的物品展示实体。
     */
    public boolean hasContainedContent() {
        return this.getCarriedMob() != null || this.getCarriedDisplay() != null;
    }

    /**
     * 让 {@code PersistenceRequired} 跟着"是否内含东西"走：内含 → true，空手 → false。
     * <p>
     * <b>为什么要来回切</b>：自然生成的选择器默认不该被持久化——空手时允许按原版规则在远离玩家后消失；
     * 但一旦内含了东西（{@code /jafa seek} 抓到了生物/物品）就必须留住，不能连东西一起刷没。
     * {@code /jafa send} 把东西送走之后它又变回空手，于是要重新允许消失。
     * <p>
     * <b>为什么要用 accessor</b>：原版只提供置 true 的 {@code Mob#setPersistenceRequired()}
     * （Mob.java:1206，没有带参数的版本），置 false 只能借 {@link MobPersistenceAccessor} 直接写那个 private 字段。
     */
    public void refreshPersistenceFromContent() {
        if (this.hasContainedContent()) {
            this.setPersistenceRequired();
        } else {
            ((MobPersistenceAccessor) (Object) this).jafa_setPersistenceRequired(false);
        }
    }

    /**
     * 生物作为乘客时的落座位置——和原版 {@code Entity#positionRider}（Entity.java:2065-2069）算的是同一个点，
     * 所以抓取流程里先把生物 lerp 到这个点、再 {@code startRiding}，落座时不会有一下瞬移。
     */
    private Vec3 ridePositionFor(Mob mob) {
        Vec3 seat = this.getPassengerRidingPosition(mob);
        Vec3 footOffset = mob.getVehicleAttachmentPoint(this);
        return new Vec3(seat.x - footOffset.x, seat.y - footOffset.y, seat.z - footOffset.z);
    }

    /**
     * 乘客落座位置。被抓的生物<b>改为在 1×1×1 选择框里垂直居中</b>，其余（物品展示实体）走原版挂点。
     * <p>
     * <b>为什么必须改</b>：原版 {@code EntityAttachment.PASSENGER} 的默认值是 {@code AT_HEIGHT}
     * （EntityAttachment.java:7/25），载具高 1 格 → 落座点在框顶。生物即使被 {@link #carryFitFactor}
     * 缩到装得下，脚也还踩在框顶上，看着是「站在框上」而不是「装在框里」。
     * 改成「框高的一半减去生物高度的一半」，它就正好居中，加上缩放后整体落在框内。
     * <p>
     * <b>为什么这里能用同步状态</b>：位置是两端各自算的（乘客不发包，见 {@code ServerEntity.java:122-134}），
     * 所以两边必须得到同一个答案——判据取 {@link #DATA_CARRIED_MOB} 这个同步位。
     * <p>
     * 顺带一个好处：send 缩小时生物变矮，这个公式会让它一边缩小一边保持居中。
     */
    @Override
    public Vec3 getPassengerRidingPosition(Entity entity) {
        if (this.entityData.get(DATA_CARRIED_MOB) && entity instanceof Mob mob) {
            return this.position().add(0.0D, centeredSeatHeight(mob), 0.0D);
        }
        return super.getPassengerRidingPosition(entity);
    }

    /** 让<b>整摞东西</b>（本体 + 背上的骑士）居中于 1×1×1 选择框时，本体脚底应当离选择器脚底多高。 */
    private double centeredSeatHeight(Mob mob) {
        double stackHeight = 0.0D;
        for (Mob member : carryStack(mob)) {
            stackHeight += member.getBbHeight();
        }
        return Math.max(0.0D, (SELECTOR_BOX_SIZE - stackHeight) * 0.5D);
    }

    /**
     * 这一摞生物的<b>全部成员</b>：本体 + 它背上的骑士（递归——骑士背上还骑着东西时也一并算进来），
     * 顺序是<b>最上面那个排最前、本体排最后</b>。
     *
     * <h3>为什么需要"一整摞"这个概念</h3>
     * 需求（6.4.31）：抓/送"骑士类生物"（猪塔、女巫骑恶魂、骷髅骑士……）时，背上的骑士要
     * <b>和本体一起缩小、一起消失</b>，而幸运名单里只记最下面那一只（{@link LuckyRoster#enqueueMob}）。
     * 于是凡是"作用在被抓这一只身上"的体型操作都得对着整摞做：
     * <ul>
     *   <li>{@link #carryFitFactor} / {@link #rampCarryFit}：整摞一起缩进框（各自按成员的尺寸算总包围盒）；</li>
     *   <li>{@link #centeredSeatHeight}：按整摞的高度居中，不然塔尖会戳出框顶；</li>
     *   <li>{@link #setShrink}：send 时整摞一起缩到消失；</li>
     *   <li>{@link #clearScaleModifiers}：放掉时整摞一起还原；</li>
     *   <li>send 收尾：整摞一起 {@code discard}。</li>
     * </ul>
     * 不这么做的话，原先的实际表现是：底下那只缩成一点点、上面的骑士还是原尺寸悬在半空，
     * 最后底面那只被删掉，骑士被原版"放下车"留在原地——看着就是"骑士没跟着走"。
     *
     * <p>只收 {@link Mob}：骑在被抓生物身上的<b>玩家</b>不参与缩小，也不会被删掉
     * （本体消失时原版会把他正常放下车）。
     */
    private static List<Mob> carryStack(Mob root) {
        List<Mob> stack = new ArrayList<>();
        collectRiders(root, stack);
        stack.add(root); // 本体排最后
        return stack;
    }

    /** 后序收集乘客：先收"骑士的骑士"，最后收骑士自己——出来的顺序就是从上到下。 */
    private static void collectRiders(Entity vehicle, List<Mob> out) {
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Mob mob) {
                collectRiders(mob, out);
                out.add(mob);
            }
        }
    }

    /**
     * <b>永远不把控制权交给乘客。</b>
     * <p>
     * 不覆写的话会踩到原版这条链：{@code Mob#getControllingPassenger()} 会在「本体有 AI 且首个乘客能操纵载具」时
     * 把那个乘客当成驾驶员（Mob.java:216-225），而 {@code Entity#canControlVehicle()} 对绝大多数生物都返回 true
     * （Entity.java:2339-2341 只排除 {@code NON_CONTROLLING_RIDER} 标签里的少数几种）。
     * 于是 {@code Mob#updateControlFlags()}（Mob.java:369-375，每 5 刻跑一次，见 :358-360）会把本体的
     * {@code Goal.Flag.MOVE} 关掉——而 {@link SelectorWanderGoal} 正是用 MOVE 这个旗标注册的，
     * 结果就是<b>背上驮了生物之后选择器再也不动了</b>。
     * <p>
     * 选择器本来就该永远由自己的 AI 飞（没有让玩家或生物驾驶的设计），所以这里直接返回 null，
     * 从根上断掉那条链。
     */
    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return null;
    }

    /**
     * 把「正抓着生物」这条状态与实际情况对齐，并同步给客户端（{@link #DATA_CARRIED_MOB}）。
     * <p>
     * 每刻跑一次的原因是读档：{@code carriedMob} 字段不存档，而生物身上的标记和骑乘关系都存档，
     * 靠 {@link #getCarriedMob()} 把字段找回来之后，这里把同步位补上，
     * 否则读档后落座位置会退回原版挂点（生物「站到框顶上」）。
     */
    private void refreshCarriedMobState() {
        boolean carried = this.getCarriedMob() != null;
        if (this.entityData.get(DATA_CARRIED_MOB) != carried) {
            this.entityData.set(DATA_CARRIED_MOB, carried);
        }
    }

    /**
     * <b>清掉泄漏的 {@code PersistenceRequired}</b>：自然生成、空手、却还标着"不许消失"的选择器，
     * 恢复成"离玩家 &gt;128 格就会被删"的普通选择器。
     *
     * <h3>这个标志是怎么泄漏的</h3>
     * 抓东西时（{@link #captureMob} → {@link #refreshPersistenceFromContent()}）会把它置真，
     * 正常路径（send 收尾、{@link #abortSeek()}、{@link #releaseCapturedMob}）都会置回假。
     * 但只要有一条路没走到（区块卸载、被抓的生物中途死掉、读档时字段丢了……），
     * 这个标志就再也没人清 —— 而 {@code PersistenceRequired} 为真的生物<b>不受</b>
     * {@code MobLuckyDespawnMixin} 里"离玩家 &gt;128 格即删除"那条规则的约束，于是它会永远飘着。
     *
     * <h3>为什么必须自愈</h3>
     * 选择器上限（{@code LuckySelectorSpawner#CAP}）是<b>整个维度共享的一个总数</b>：
     * 只要这种赖着不走的存量凑够上限，生成器的第一道闸就直接返回，<b>新选择器一只都刷不出来</b>
     * —— 玩家看到的就是"一只都找不到"。实测存档里就有这种选择器（空手 + PersistenceRequired=1）。
     *
     * <p>只对自然生成的动手（{@link #TAG_NATURAL_SPAWN}；老存档靠永久发光认），
     * 刷怪蛋/命令放下的选择器不动；身上还骑着/正抓着东西的也不动（那是真的在干活）。
     * 每刻每个选择器只做几次字段读取，量级可以忽略。
     */
    private void healStalePersistence() {
        if (!this.isPersistenceRequired() || this.hasContainedContent() || this.isVehicle()) {
            return;
        }
        if (!this.getPersistentData().getBoolean(TAG_NATURAL_SPAWN) && !this.hasEffect(MobEffects.GLOWING)) {
            return;
        }
        ((MobPersistenceAccessor) (Object) this).jafa_setPersistenceRequired(false);
        ModMain.LOGGER.info("[选择器] 空手却标着 PersistenceRequired（抓过东西又没走完流程），"
            + "恢复为可消失的普通选择器：{}", this.position());
    }

    @Override
    protected void registerGoals() {
        // 空中游荡（平时唯一的移动 AI；抓取/发送期间由 SelectorWanderGoal 自己让位）
        this.goalSelector.addGoal(1, new SelectorWanderGoal(this));
        // 幸运维度的自动收集（搜索 → seek → 游荡一会儿 → send 进幸运名单）。
        // 刻意<b>不占任何 Goal.Flag</b>：它只做决策与计时，飞行仍旧交给上面那个游荡 goal，
        // 于是"一边四处偏移一边搜寻"是天然成立的（见 SelectorCollectGoal 的类注释）。
        this.goalSelector.addGoal(2, new SelectorCollectGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        // 照原版凋灵的配置（WitherBoss.java:90-96）：飞行导航，不开门、可漂浮、可穿门
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GRAB_SIGNAL, 0);
        builder.define(DATA_SEND_SIGNAL, 0);
        builder.define(DATA_CARRIED_MOB, false);
        builder.define(DATA_BUSY, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            // AnimationState 只在客户端被模型读取
            this.syncAnimationSignals();
        } else {
            // 读档后 carriedMob 字段会丢，靠生物身上的标记把它找回来（并同步给客户端）
            this.refreshCarriedMobState();
            // 空手却还标着"不许消失"的（抓取流程没走完留下的）：恢复成普通选择器，别让它占着上限名额
            this.healStalePersistence();
            // 把"正在执行脚本化动作"同步给客户端（旁观操控的本地预测要靠它暂停），
            // 记的是上一刻结束时的状态，差一刻无所谓
            this.entityData.set(DATA_BUSY, this.isBusy());
            this.tickSeek();
            this.tickSend();
            // 旁观操控放在最后：本刻的朝向与速度由操控输入说了算（AI 已经跑完，不会把它转回去）
            this.tickPilot();
        }
    }

    /** 客户端可读：本刻那只选择器是不是正在执行抓取/发送动作（见 {@link #DATA_BUSY}）。 */
    public boolean isBusySynced() {
        return this.entityData.get(DATA_BUSY);
    }

    // ===== 旁观操控（需求 6.4.36）=====

    /** 本刻是否正由玩家操控（含"松手后滑停"的那几刻）。见 {@link #piloted}。 */
    public boolean isPiloted() {
        return this.piloted;
    }

    /**
     * 正在操控本体的那个玩家；没有则返回 {@code null}。
     * <p>
     * 判据三条：<b>旁观模式</b> + 他的摄像机<b>就是本实体</b>（{@code getCamera()}，「左键点实体 → 开始旁观它」
     * 走的是原版 {@code ServerPlayer#attack} → {@code setCamera}，见 ServerPlayer.java:1767-1773）
     * + 客户端的上报是<b>新鲜的</b>（见 {@link SelectorPilot}）。
     * 第三条顺带解决了"退出旁观"：玩家按潜行键时原版会自己把摄像机收回自己身上
     * （{@code ServerPlayer#tick} 里 {@code if (this.wantsToStopRiding()) this.setCamera(this);}，
     * ServerPlayer.java:524-535），本方法下一 tick 就找不到操控者了。
     */
    @Nullable
    private net.minecraft.server.level.ServerPlayer activePilot() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
            if (!player.isSpectator() || player.getCamera() != this) {
                continue;
            }
            SelectorPilot.Input input = SelectorPilot.inputOf(player);
            if (input != null && input.selectorId() == this.getId()) {
                return player;
            }
        }
        return null;
    }

    /**
     * 每刻的操控推进：
     * <ol>
     *   <li>朝向 = 玩家视线（yaw/pitch 直接抄过来，客户端也会本地先转一步，见 {@code SelectorPilotClient}）；</li>
     *   <li>按 WASD/空格算出目标速度（见 {@link #pilotDirection}），再以 {@link #PILOT_ACCEL_PER_TICK}
     *       的加速度逼近它——松手时目标速度是 0，于是自然就是减速滑停；</li>
     *   <li><b>当场</b>按这个新速度移动一次（而不是写进 {@code deltaMovement} 等下一 tick 的
     *       {@code travel} 去消费）：少缓冲一 tick，手感就少一截延迟。为此
     *       {@link #travel(Vec3)} 在操控期间是空操作，位移只在这里发生（每 tick 恰好一次）；</li>
     *   <li>{@code hasImpulse = true}：逼服务端<b>每刻</b>都发一次位置/朝向包。原版实体默认按
     *       {@code updateInterval}（生物是 3 刻）才发一次位置（{@code ServerEntity#sendChanges} 那行
     *       {@code if (this.tickCount % this.updateInterval == 0 || this.entity.hasImpulse || ...)}，
     *       ServerEntity.java:121），客户端只能对着 3 刻的窗口插值 —— 那就是"动起来慢半拍"的主要来源；</li>
     *   <li>没人操控且已经滑停 → 交还给 AI。</li>
     * </ol>
     */
    private void tickPilot() {
        net.minecraft.server.level.ServerPlayer pilot = this.activePilot();
        SelectorPilot.Input input = pilot == null ? null : SelectorPilot.inputOf(pilot);
        if (input == null && !this.piloted) {
            return; // 没人在操控、也没有正在滑停：一切照旧（AI 自己飞）
        }
        if (this.isBusy()) {
            /*
             * 抓取/发送动画期间"暂时失去控制权"：
             *   - {@link #piloted} 保持为真 → 两个 AI goal 继续让位、玩家仍算操控者；
             *   - {@link #pilotDriving} 置假 → travel 恢复原版，seek 的接近段才能靠移动控制把盒子飞过去；
             *   - 只在"刚交出去"的那一瞬清一次操控惯性，之后就交给动画自己的移动模型
             *     （每刻都清零会把飞行速度一直按住起不来）；
             *   - 仍然每刻发一次位置包，让客户端看到的动画是顺的（原来 3 刻一个包会一跳一跳）。
             */
            if (this.pilotDriving) {
                this.setDeltaMovement(Vec3.ZERO);
            }
            this.pilotDriving = false;
            this.hasImpulse = true;
            return;
        }
        this.piloted = true;
        this.pilotDriving = true;

        if (input != null) {
            // 整只盒子都转过去：yRot（模型基点）、yHeadRot（视角/头）、yBodyRot（身体）
            this.setYRot(input.yaw());
            this.setXRot(input.pitch());
            this.setYHeadRot(input.yaw());
            this.yBodyRot = input.yaw();
        }

        Vec3 target = input == null
            ? Vec3.ZERO
            : SelectorPilotMotion.direction(input.yaw(), input.pitch(), input.forward(), input.backward(),
                input.left(), input.right(), input.jump()).scale(PILOT_SPEED_PER_TICK);
        Vec3 velocity = SelectorPilotMotion.approach(this.getDeltaMovement(), target, PILOT_ACCEL_PER_TICK);
        this.setDeltaMovement(velocity);
        // 本刻就按新速度走：碰撞照旧由原版 Entity#move 处理（撞墙会停，不会穿墙）
        this.move(net.minecraft.world.entity.MoverType.SELF, velocity);
        // 每刻同步一次位置与朝向（见方法注释第 4 条）
        this.hasImpulse = true;

        if (input == null && SelectorPilotMotion.atRest(velocity)) {
            this.setDeltaMovement(Vec3.ZERO);
            this.piloted = false;      // 滑停完成：下一 tick 起走原版那套（AI 重新接管）
            this.pilotDriving = false;
        }
    }

    /** <b>只在客户端用</b>：本地预测的速度（见 {@link #clientPilotVelocity}）。 */
    @Nullable
    public Vec3 clientPilotVelocity() {
        return this.clientPilotVelocity;
    }

    /** <b>只在客户端用</b>：本地预测到的位置。 */
    @Nullable
    public Vec3 clientPilotPredicted() {
        return this.clientPilotPredicted;
    }

    /** <b>只在客户端用</b>：记下这一步的预测结果，供下一帧接着推。 */
    public void setClientPilotState(Vec3 velocity, Vec3 predicted) {
        this.clientPilotVelocity = velocity;
        this.clientPilotPredicted = predicted;
    }

    /** <b>只在客户端用</b>：结束一段操控（松开旁观）时把预测状态清掉。 */
    public void clearClientPilotState() {
        this.clientPilotVelocity = null;
        this.clientPilotPredicted = null;
    }

    /**
     * 操控模式<b>正在驱动位移</b>时 {@code travel} 只负责不让原版那套动：位移已经在
     * {@link #tickPilot()} 里用本刻的新速度做完了（这样能少缓冲一 tick）。
     * <p>
     * 注意判据是 {@link #pilotDriving} 而<b>不是</b> {@link #piloted}：抓取/发送动画期间后者仍为真
     * （AI 要继续让位），但位移必须还给原版 —— seek 的接近段就是靠
     * {@code getMoveControl().setWantedPosition(...)} + {@code travel} 飞的。
     * 6.5.17~6.5.19 用 {@code piloted} 当判据，导致"操控中右键 seek"盒子一动不动（6.5.20 修）。
     * <p>
     * 之所以要覆写：原版 {@code LivingEntity#travel} 的位移来自 {@code xxa/zza} 与 {@code getSpeed()}
     * （由 {@code FlyingMoveControl} / 导航写进去），既不吃 {@code deltaMovement}，也会和玩家的方向打架；
     * 不拦住的话"固定 8.634 米/秒 + 加减速"根本无从谈起。碰撞仍然走原版 {@code Entity#move}。
     */
    @Override
    public void travel(Vec3 input) {
        if (this.pilotDriving) {
            return;
        }
        super.travel(input);
    }

    // ===== 抓取流程 =====

    /** 是否正在抓取。 */
    public boolean isSeeking() {
        return this.seekPhase != SeekPhase.NONE;
    }

    /** 是否正在执行脚本化动作（抓取或发送）：这期间游荡 AI 让位、本体原地不动。 */
    public boolean isBusy() {
        return this.seekPhase != SeekPhase.NONE || this.sendPhase != SendPhase.NONE;
    }

    /**
     * {@code /jafa seek}：抓<b>附近 32 格内最近的可以抓的生物</b>。
     * <p>
     * 新 AI（{@link SelectorCollectGoal}）不走这里——它自己按 128 格与"幸运物品池 / 幸运生物事件"筛选目标，
     * 然后调 {@link #startSeekTarget(Entity)}。这个方法只是命令的手动入口，行为保持不变。
     *
     * @return 找到了目标并开始流程返回 true；附近没有可抓的生物返回 false
     */
    public boolean startSeek() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        Mob nearest = null;
        double nearestSqr = SEEK_MOB_RANGE * SEEK_MOB_RANGE;
        for (Mob mob : serverLevel.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(SEEK_MOB_RANGE))) {
            if (!this.canCapture(mob)) {
                continue;
            }
            double distSqr = mob.distanceToSqr(this);
            if (distSqr < nearestSqr) {
                nearestSqr = distSqr;
                nearest = mob;
            }
        }
        if (nearest != null && this.startSeekMob(nearest)) {
            // 命令抓的：手里这把东西是玩家自己要的，AI 会多留它一会儿再去送（见 SelectorCollectGoal）
            this.manualCapture = true;
            return true;
        }
        return false;
    }

    /**
     * 抓<b>指定的</b>一只生物（新 AI 与 {@code /jafa seek} 都汇到这里）。
     *
     * @return 开始了流程返回 true；手上已经有东西、或这只不能抓返回 false
     */
    public boolean startSeekMob(Mob mob) {
        if (!this.canStartSeek() || !this.canCapture(mob)) {
            return false;
        }
        // 锁定 = 认领：别的选择器从此不会再选它（见 SelectorTargetClaims）
        if (!this.claimTarget(mob)) {
            return false;
        }
        this.stopAnimations();
        this.seekTargetKind = SeekTargetKind.MOB;
        this.seekMobTarget = mob;
        this.seekItemTarget = null;
        this.seekPhase = SeekPhase.APPROACH;
        this.seekTicks = 0;
        return true;
    }

    /**
     * 抓掉落物那套流程（{@link SeekTargetKind#ITEM}）：飞到它上方，抬起来换成一个物品展示实体。
     * <p>
     * 这条分支原先只服务 {@code /jafa seek}（后来命令改成只找生物，它就闲置了），现在被新 AI 用起来：
     * AI 要找的正是"幸运物品池里的掉落物"。
     *
     * @return 开始了流程返回 true；手上已经有东西返回 false
     */
    public boolean startSeekItem(ItemEntity item) {
        if (!this.canStartSeek() || item == null || !item.isAlive() || item.isRemoved()) {
            return false;
        }
        // 同上：锁定即认领，别的选择器不会再选这个掉落物
        if (!this.claimTarget(item)) {
            return false;
        }
        this.stopAnimations();
        this.seekTargetKind = SeekTargetKind.ITEM;
        this.seekItemTarget = item;
        this.seekMobTarget = null;
        this.seekPhase = SeekPhase.APPROACH;
        this.seekTicks = 0;
        return true;
    }

    /** 抓<b>附近 32 格内最近的掉落物</b>（保留的旧入口，命令已不再触发）。 */
    public boolean startSeekNearestItem() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        ItemEntity nearest = null;
        double nearestSqr = SEEK_ITEM_RANGE * SEEK_ITEM_RANGE;
        for (ItemEntity item : serverLevel.getEntitiesOfClass(ItemEntity.class,
                this.getBoundingBox().inflate(SEEK_ITEM_RANGE))) {
            double distSqr = item.distanceToSqr(this);
            if (distSqr < nearestSqr) {
                nearestSqr = distSqr;
                nearest = item;
            }
        }
        if (nearest != null && this.startSeekItem(nearest)) {
            this.manualCapture = true; // 同 startSeek()：手动抓的
            return true;
        }
        return false;
    }

    /**
     * 抓<b>指定的</b>目标（新 AI 的入口）：生物走生物那条，掉落物走物品那条。
     *
     * @return 开始了流程返回 true；正在忙、手上已经有东西、或目标类型/状态不对返回 false
     */
    public boolean startSeekTarget(Entity target) {
        // AI 抓的（不是命令抓的）：手里这把东西按"抓完游荡 100~200 刻就送"处理
        this.manualCapture = false;
        if (target instanceof Mob mob) {
            return this.startSeekMob(mob);
        }
        if (target instanceof ItemEntity item) {
            return this.startSeekItem(item);
        }
        return false;
    }

    /**
     * 手里（或正在抓）的这把东西是不是<b>玩家用命令抓的</b>（而不是 AI 自动抓的）。
     * <p>
     * 只在内存里记：读档之后一律按"AI 抓的"处理（那时候玩家早就不在场了，正好让 AI 把它送出去，
     * 别让一只带着东西的选择器永久占着上限名额）。
     */
    public boolean isManualCapture() {
        return this.manualCapture;
    }

    /** 现在能不能开一次新的抓取：手上有东西、或者正在抓/正在发都不行（载具只有一个乘客位）。 */
    private boolean canStartSeek() {
        return !this.isBusy() && !this.hasContainedContent();
    }

    /** 认领一个目标（见 {@link SelectorTargetClaims}）：登记成功才把它记在自己身上，供收尾时释放。 */
    private boolean claimTarget(Entity target) {
        if (!SelectorTargetClaims.claim(target, this)) {
            return false;
        }
        this.claimedTarget = target;
        return true;
    }

    /** 释放自己认领的那个目标（没认领过就是空操作）。 */
    private void releaseClaim() {
        if (this.claimedTarget != null) {
            SelectorTargetClaims.release(this.claimedTarget, this);
            this.claimedTarget = null;
        }
    }

    /**
     * 这只生物能不能抓。<b>不按种类筛</b>——按设定，任何生物都能抓，包括凋灵、监守者、末影龙
     * 这些 Boss（早先版本里有一张 {@code CAPTURE_BLACKLIST} 排除表，已按需求去掉）。
     * 只挡掉这几类「抓了没有意义或者会把别的状态弄坏」的：
     * <ul>
     *   <li>自己和别的选择器——否则两个选择器会互相抓成一串；</li>
     *   <li><b>自己是乘客</b>的：被别的东西载着（硬抓会把它从那个载具上拽下来）；</li>
     *   <li>已死 / 已被移除的。</li>
     * </ul>
     * <p><b>自己是载具（驮着别的生物）现在允许抓</b>：需求要的就是「生物处于骑乘状态时 seek 被骑乘的那只」——
     * 也就是叠罗汉最底下那只，它身上骑着别人。抓它时乘客会跟着一起上来（骑乘关系是嵌套的），不会被拆散。
     * <p><b>已经被别的选择器锁定/抓取的不抓</b>（需求）：判据在 {@link SelectorTargetClaims}，
     * 所以自动收集与 {@code /jafa seek} 两条路都会自动跳过别人正在抓的那只。
     * <p>玩家不是 {@link Mob}，本来就不在候选里。
     */
    public boolean canCapture(Mob mob) {
        if (mob == this || mob instanceof LuckySelectorEntity) {
            return false;
        }
        if (mob.isRemoved() || !mob.isAlive()) {
            return false;
        }
        if (mob.isPassenger()) {
            return false;
        }
        return !SelectorTargetClaims.isClaimedByOther(mob, this);
    }

    /** 服务端每刻推进抓取流程。 */
    private void tickSeek() {
        if (this.seekPhase == SeekPhase.NONE) {
            return;
        }
        this.seekTicks++;
        switch (this.seekPhase) {
            case APPROACH -> {
                Vec3 hover = this.liveHoverPoint();
                // 目标没了（被捡走 / 被杀 / 换维度）→ 中止
                if (hover == null) {
                    this.abortSeek();
                    return;
                }
                this.getMoveControl().setWantedPosition(
                    hover.x, hover.y, hover.z, this.getFlightSpeedMultiplier());
                if (this.distanceToSqr(hover.x, hover.y, hover.z) < SEEK_ARRIVE_SQR) {
                    // 已锁定：立刻停住，方便把它搬起来
                    this.holdPosition();
                    // 生物会自己乱走，所以在这里就当场抓住（无AI化 + 关重力）；
                    // 掉落物自己不会动，物品分支留到 STRETCH 播完再换载体（见 replaceItemWithDisplay）
                    if (this.seekTargetKind == SeekTargetKind.MOB) {
                        this.captureMob(this.seekMobTarget);
                    }
                    this.seekPhase = SeekPhase.STRETCH;
                    this.seekTicks = 0;
                    // 客户端：stretch 播完紧接 retreat（中途不回到默认姿态），与服务端这条时间轴对齐
                    this.requestAnimation(SelectorAnimation.GRAB);
                }
            }
            case STRETCH -> {
                if (!this.hasLiveSeekTarget()) {
                    this.abortSeek();
                    return;
                }
                // 锁定之后不再移动：本体钉在锁定时所在的位置
                this.holdPosition();
                if (this.seekTargetKind == SeekTargetKind.MOB) {
                    // 生物分支：STRETCH 段只把它钉住（免得被本体的碰撞箱推开）。
                    // 缩小<b>不</b>在这一段做——需求是「缩小发生在收回（RETREAT）阶段」，见 rampCarryFit。
                    this.holdCarriedMob();
                }
                if (this.seekTicks >= ANIMATION_TICKS) {
                    if (this.seekTargetKind == SeekTargetKind.ITEM) {
                        // stretch 播完：掉落物 → 物品展示实体（原因见 replaceItemWithDisplay）
                        this.carriedDisplay = this.replaceItemWithDisplay(this.seekItemTarget);
                        // 内含了东西：从此不允许被原版规则刷掉
                        this.refreshPersistenceFromContent();
                        this.seekItemTarget = null;
                    }
                    // 生物在进 STRETCH 那一刻就已经抓好并记进 carriedMob 了，这里不用再动
                    this.seekPhase = SeekPhase.RETREAT;
                    this.seekTicks = 0;
                }
            }
            case RETREAT -> {
                // 搬运动作期间本体不动（被搬的东西的位移由下面的逻辑直接摆）
                this.holdPosition();
                if (this.seekTargetKind == SeekTargetKind.MOB) {
                    // 收回段的这一秒里逐刻把生物压进框（需求：缩小发生在 RETREAT，而不是 STRETCH）。
                    // 必须排在抬升之前——同一刻的座位高度是按它当时的体型算的，顺序反了会差一刻。
                    this.rampCarryFit();
                }
                boolean targetAlive = this.seekTargetKind == SeekTargetKind.MOB
                    ? this.liftCarriedMob()
                    : this.liftCarriedDisplay();
                if (!targetAlive) {
                    this.abortSeek();
                    return;
                }
                if (this.seekTicks >= ANIMATION_TICKS) {
                    this.finishSeek();
                }
            }
            default -> {
            }
        }
    }

    /**
     * 悬停点：本体碰撞箱要停在哪里。目标已经不在（被捡走 / 死了 / 被移除 / 换维度）返回 null。
     * <p>
     * <b>生物分支</b>：点取<b>生物碰撞箱的上表面</b>（{@code getY() + getBbHeight()}），也就是让本体的
     * 碰撞箱<b>下表面正好贴上去</b>——而不是拿它脚下那块地面（{@code getY()}）当基准。区别很关键：
     * 生物只要高过 1 格，按脚下算出来的悬停点就落在它<b>身体里面</b>，选择框会插在它胸口上；
     * 按上表面算，框才是端端正正悬在它头顶。需求里给的余量是「也可高 0.5 格」，
     * 由 {@link #SEEK_ARRIVE_SQR} 的容差保证。
     * <p>
     * <b>物品分支</b>：仍按配置 {@code lucky_selector.seek_hover_px} 抬到掉落物正上方。
     */
    @Nullable
    private Vec3 liveHoverPoint() {
        if (this.seekTargetKind == SeekTargetKind.MOB) {
            Mob mob = this.seekMobTarget;
            if (mob == null || !mob.isAlive() || mob.isRemoved() || mob.level() != this.level()) {
                return null;
            }
            // 碰撞箱上表面 = 脚底 + 当前（含缩放）高度
            return new Vec3(mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ());
        }
        ItemEntity item = this.seekItemTarget;
        if (item == null || !item.isAlive() || item.level() != this.level()) {
            return null;
        }
        return new Vec3(item.getX(),
            item.getY() + ModConfig.SELECTOR_SEEK_HOVER_PX.get() / PIXELS_PER_BLOCK, item.getZ());
    }

    /** 已经锁定的目标还在不在（STRETCH 阶段用）。 */
    private boolean hasLiveSeekTarget() {
        if (this.seekTargetKind == SeekTargetKind.MOB) {
            Mob mob = this.carriedMob;
            return mob != null && mob.isAlive() && !mob.isRemoved();
        }
        ItemEntity item = this.seekItemTarget;
        return item != null && item.isAlive() && item.level() == this.level();
    }

    /**
     * 抓住一只生物：<b>无AI化</b>、关重力、标上抓取标记，并记进 {@link #carriedMob}。
     * <p>
     * <b>为什么要关 AI</b>：{@code Mob#isEffectiveAi()} = {@code super.isEffectiveAi() && !isNoAi()}
     * （Mob.java:1420），而 {@code LivingEntity#aiStep} 只在 {@code isEffectiveAi()} 时才 tick
     * {@code serverAiStep()}（LivingEntity.java:2765）——所以 {@code setNoAi(true)} 会把目标选择器、Goal、
     * 寻路（以及 Brain 类生物的大脑）<b>整套</b>停掉。这是原版「NoAI」位，会随实体一起存档。
     * <p>
     * <b>为什么要关重力</b>：乘客每刻都会被 {@code positionRider} 摆回座位（Entity.java:2054-2056），
     * 但顺序是「先 travel、后归位」——{@code travel} 期间重力照常累计下落距离，不关重力的话这只生物
     * 会在半空中一直被判定为下落，攒出一身摔落伤害。
     * <p>
     * <b>为什么要标持久化</b>：本维度（以及原版）的消失规则都会清掉没有 {@code PersistenceRequired} 的生物，
     * 而它一旦离最近玩家超过 128 格，正好符合本模组那条规则（{@code MobLuckyDespawnMixin}）——
     * 不标的话选择器飞远一点，背上的生物就没了。抓取前的原值记在 {@code TAG_WAS_*} 里，脱手时原样还回去。
     * <p>
     * <b>还要把它缩到装得下</b>：见 {@link #rampCarryFit()}——注意是<b>逐渐</b>压下去，
     * 不是抓住那一刻就压到底。
     */
    private void captureMob(@Nullable Mob mob) {
        if (mob == null) {
            return;
        }
        CompoundTag data = mob.getPersistentData();
        data.putBoolean(TAG_CAPTURED, true);
        data.putBoolean(TAG_WAS_NO_AI, mob.isNoAi());
        data.putBoolean(TAG_WAS_NO_GRAVITY, mob.isNoGravity());
        data.putBoolean(TAG_WAS_PERSISTENT, mob.isPersistenceRequired());

        mob.setTarget(null);
        mob.getNavigation().stop();
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.setPersistenceRequired();

        // 目标倍率按此刻（还没缩小时）的碰撞箱算一次；缩小本身交给 RETREAT 段逐刻推进
        this.carryFitTarget = carryFitFactor(mob);
        this.carriedMobHold = mob.position();

        this.carriedMob = mob;
        this.seekMobTarget = null;
        this.entityData.set(DATA_CARRIED_MOB, true);
        this.refreshPersistenceFromContent();
    }

    /**
     * RETREAT 段逐刻把生物压进框：倍率从 1 线性压到 {@link #carryFitTarget}。
     * <p>
     * 需求是「抓起来的过程中逐渐缩小」，而且<b>缩小要发生在收回段</b>——所以既不在
     * {@link #captureMob(Mob)} 里一次压到底，也不在 STRETCH（展开）段做，而是摊在 RETREAT 的
     * {@link #CARRY_FIT_STEPS} 刻里：选择框合拢的同时，它被一点点压进去、同时被抬上座位。
     * <p>
     * 线性在倍率上是均匀的，加上客户端按帧插值（属性每刻一次，渲染层在两次之间插），
     * 看起来是一条平滑的压缩曲线。收尾那一两步会贴到 {@code SCALE} 属性下限
     * （0.0625，Attributes.java:124-126），此时它已经只有 1/16 格，看不出停顿。
     * <p>
     * 整摞一起压（见 {@link #carryStack}）：背着骑士的那些也要跟着缩，否则塔尖会一直戳在框外。
     */
    private void rampCarryFit() {
        Mob mob = this.carriedMob;
        if (mob == null) {
            return;
        }
        double progress = Math.min(1.0D, this.seekTicks / (double) CARRY_FIT_STEPS);
        double factor = 1.0D + (this.carryFitTarget - 1.0D) * progress;
        for (Mob member : carryStack(mob)) {
            applyCarryFit(member, factor);
        }
    }

    /**
     * 把生物钉在 {@link #carriedMobHold} 上。
     * <p>
     * 不按的话它会被本体的碰撞箱每刻推开（两者重叠，{@code Entity#push} 会互相施加位移），
     * 20 刻下来就从框里滑出去了——而选择器是钉住的，看起来就是「框飘开了」。
     * 每刻重新摆回去，位移最多只有一个刻的推力量级。
     */
    private void holdCarriedMob() {
        Mob mob = this.carriedMob;
        if (mob == null || mob.getVehicle() == this) {
            return;
        }
        mob.setPos(this.carriedMobHold.x, this.carriedMobHold.y, this.carriedMobHold.z);
        mob.setDeltaMovement(Vec3.ZERO);
        // 位置每刻在动的时候（抬升段）强制每刻同步一次：生物的 updateInterval 通常是 3 刻，
        // 不推这一下，5 刻的抬升只会发出 1~2 个位置包，客户端怎么插都是一跳一跳的
        // （ServerEntity.java:121 那个 if 里就带 `|| entity.hasImpulse`）。
        mob.hasImpulse = true;
    }

    /**
     * 把生物缩到能<b>整个装进 1×1×1 选择框</b>：倍率 = {@code min(1, 目标/宽, 目标/高)}，
     * 其中目标是 {@code 1.0 × }{@link #CARRY_FIT_MARGIN}。
     * <p>
     * 尺寸取的是它<b>当前</b>的碰撞箱（{@code getBbWidth()/getBbHeight()}）——那个值里已经含了年龄缩放
     * （{@link net.minecraft.world.entity.LivingEntity#getAgeScale()}）和 {@code Attributes.SCALE}
     * （LivingEntity.java:3423-3429：{@code getDefaultDimensions(pose).scale(getScale())}），
     * 所以算出来的倍率直接乘在「它现在多大」上就对了，不论是幼年体还是被别的系统放大过的巨型生物。
     * <p>
     * 用 {@code ADD_MULTIPLIED_TOTAL}（最终值 = 各修饰符连乘，AttributeInstance.java:145-163）而不是
     * {@code setBaseValue}：只叠一层，脱手时摘掉就恢复原样，不会冲掉别人设的体型。
     * <p>
     * 注意 {@code SCALE} 属性的取值范围是 <b>0.0625~16</b>（Attributes.java:124-126），所以倍率会被底限截住。
     * 只有末影龙那种量级才会碰到：它 16 格宽、8 格高，算出来要 ×0.056，被截成 0.0625 →
     * 最终 1.0 格宽 × 0.5 格高，正好卡着框的上限「塞进去」（宽度贴着框壁，没有 {@link #CARRY_FIT_MARGIN} 的余量）。
     * 另外它的碰撞箱由 {@code EnderDragonPart}（独立实体、没有 {@code SCALE} 属性）拼成，
     * 那些part不会跟着缩，所以末影龙抓起来<b>只有本体缩</b>，部位判定仍是原尺寸——这是「任何生物都能抓」的已知代价。
     */
    private void applyCarryFit(Mob mob, double factor) {
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale == null) {
            return;
        }
        scale.removeModifier(CARRY_FIT_MODIFIER_ID);
        scale.addTransientModifier(new AttributeModifier(
            CARRY_FIT_MODIFIER_ID, factor - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /**
     * <b>客户端渲染用的判据</b>：这只生物的体型是不是正由本模组的抓取/发送动画驱动。
     * <p>
     * 判据取「它的 {@code SCALE} 属性上有没有我们这两个修饰符」——属性修饰符会同步到客户端，
     * 所以两端答案一致，而且范围天然收得很紧：世界上不会有别的实体的体型被我们这两个 id 改过，
     * 于是插值只作用在被选择器抓着/送走的那一只身上，其它任何缩放（巨型生物、玩家变形）都不受影响。
     */
    public static boolean isScaleAnimatedBySelector(LivingEntity entity) {
        net.minecraft.world.entity.ai.attributes.AttributeMap attributes = entity.getAttributes();
        return attributes.hasModifier(Attributes.SCALE, SHRINK_MODIFIER_ID)
            || attributes.hasModifier(Attributes.SCALE, CARRY_FIT_MODIFIER_ID);
    }

    /**
     * 让生物整个装进选择框所需的倍率（本来就装得下就返回 1）。
     * <p>
     * 量的是<b>一整摞</b>（本体 + 背上的骑士，见 {@link #carryStack}）：摞在一起的生物是
     * 「上面那只的脚踩在下面那只的头顶」（{@code EntityAttachment.PASSENGER} 默认 AT_HEIGHT，
     * EntityAttachment.java:7/25），所以整摞的高度就是各成员高度<b>相加</b>、宽度取最宽的那个。
     * 同一个倍率乘在<b>每个成员</b>身上，整摞的包围盒就等比例缩到装得下——
     * 单独一只生物（没有骑士）时这就是原来那套算法，结果完全一样。
     */
    private static double carryFitFactor(Mob mob) {
        double target = SELECTOR_BOX_SIZE * CARRY_FIT_MARGIN;
        double width = 0.0D;
        double height = 0.0D;
        for (Mob member : carryStack(mob)) {
            width = Math.max(width, member.getBbWidth());
            height += member.getBbHeight();
        }
        width = Math.max(1.0E-4D, width);
        height = Math.max(1.0E-4D, height);
        return Math.min(1.0D, Math.min(target / width, target / height));
    }

    /**
     * retreat 的抬升段（时间码 0.5 = 第 10 刻开始，0.75 = 第 15 刻到位）：把抓在手上的东西抬上来。
     * <p>
     * 生物分支抬向 {@link #ridePositionFor(Mob)}——正是它骑上来之后的实际位置，
     * 所以 {@link #finishSeek()} 里 {@code startRiding} 落座时不会再瞬移一下。
     *
     * @return 目标还在返回 true；丢了返回 false（调用方中止流程）
     */
    private boolean liftCarriedMob() {
        Mob mob = this.carriedMob;
        if (mob == null || !mob.isAlive() || mob.isRemoved()) {
            return false;
        }
        if (this.seekTicks == LIFT_START_TICKS) {
            this.seekLiftFrom = mob.position();
            this.seekLiftTo = this.ridePositionFor(mob);
        }
        if (this.seekTicks >= LIFT_START_TICKS && this.seekTicks <= LIFT_END_TICKS) {
            this.carriedMobHold = this.seekLiftFrom.lerp(this.seekLiftTo, this.liftProgress());
        }
        this.holdCarriedMob();
        return true;
    }

    /**
     * retreat 的抬升段：物品分支抬的是{@link #replaceItemWithDisplay 替换出来的}物品展示实体，
     * 高度取配置 {@code lucky_selector.seek_lift_px} 像素。
     *
     * @return 目标还在返回 true；丢了返回 false（调用方中止流程）
     */
    private boolean liftCarriedDisplay() {
        Display.ItemDisplay display = this.carriedDisplay;
        if (display == null || !display.isAlive()) {
            return false;
        }
        if (this.seekTicks == LIFT_START_TICKS) {
            double lift = ModConfig.SELECTOR_SEEK_LIFT_PX.get() / PIXELS_PER_BLOCK;
            this.seekLiftFrom = display.position();
            this.seekLiftTo = this.seekLiftFrom.add(0.0D, lift, 0.0D);
        }
        if (this.seekTicks >= LIFT_START_TICKS && this.seekTicks <= LIFT_END_TICKS) {
            // 与工作台权杖的飞行动画同一套写法（CraftingStaffCrafting#advance）：
            // 起终点之间逐刻 lerp，再 setPos。看起来连续的关键不是这个 lerp，
            // 而是那个展示实体的 teleport_duration=1（见 replaceItemWithDisplay）。
            Vec3 pos = this.seekLiftFrom.lerp(this.seekLiftTo, this.liftProgress());
            display.setPos(pos.x, pos.y, pos.z);
        }
        return true;
    }

    /** 抬升段的时间码进度（0~1）。 */
    private double liftProgress() {
        return (this.seekTicks - LIFT_START_TICKS) / (double) (LIFT_END_TICKS - LIFT_START_TICKS);
    }

    /**
     * 把掉落物实体换成<b>物品展示实体</b>：删除掉落物，原地生成一个显示同样物品的展示实体。
     * <p>
     * <b>为什么必须换</b>：{@code minecraft:item} 注册为 {@code updateInterval(20)}——位置包每 20 刻才发一次；
     * 而抬升只有 5 刻，客户端最多收到一次位置更新，于是看起来是瞬移（两处注册见 {@code EntityType.java:442-447}）。
     * <p>
     * 这里直接复用工作台权杖那套「飞行物品」生成函数 {@link CraftingVisuals#spawnFlyingItem}，它已经把三件事配好了：
     * <ul>
     *   <li>{@code item_display=ground}：外观与原版掉落物一致（含模型自带的 0.25 缩放和偏移）；</li>
     *   <li>{@code billboard=center}：始终面向玩家，不会侧看成一条线；</li>
     *   <li><b>{@code teleport_duration=1}</b>：逐刻插值，所以逐刻移动看起来是连续的。</li>
     * </ul>
     * <b>之前两版之所以生硬，就是因为漏了最后那条</b>——{@code teleport_duration} 默认是 0（不插值），
     * 每刻 {@code setPos} 都是直接归位，位置摆得再细也是"一格格跳"。
     */
    private Display.ItemDisplay replaceItemWithDisplay(ItemEntity item) {
        ServerLevel level = (ServerLevel) this.level();
        ItemStack stack = item.getItem().copy();
        Display.ItemDisplay display = CraftingVisuals.spawnFlyingItem(level, stack, item.position());
        item.discard();
        return display;
    }

    /**
     * 把本体<b>钉在原地</b>（锁定目标之后用）。
     * <p>
     * 两件事：
     * <ol>
     *   <li>给移动控制下发一个「就是自己当前位置、速度 0」的目标点——{@code FlyingMoveControl}
     *       对近到 2.5e-7 以内的目标会把上下输入清零并直接返回，等于不再推进（而且这个"目标"每刻都在
     *       跟着自己走，所以不会产生位移）；</li>
     *   <li>清掉残留速度：否则镜头里它还会按惯性滑一小段，而需求是「立刻停止移动」。</li>
     * </ol>
     */
    private void holdPosition() {
        this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
        this.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * 抓取正常收尾：让手上的东西<b>骑到本体上</b>（{@code startRiding}）。
     * <p>
     * 骑上之后它的位置就不再由我们摆——乘客每刻会被载具的 {@code positionRider} 放到座位点
     * （客户端算的是载具<b>插值后</b>的位置），于是它会平滑地一直跟着本体运动。
     * <p>
     * 生物分支的落座点和抬升段的目标点是同一个（{@link #ridePositionFor(Mob)}），所以这一下不会瞬移。
     * 拴绳不用自己处理：原版 {@code Mob#startRiding} 会在骑上时自动 {@code dropLeash}
     * （Mob.java:1409-1417）。
     * <p>
     * 骑上来的东西刻意<b>不</b>删除、也不清字段——它是这次抓取的成果，该由后续流程
     * （{@code /jafa send}）处置。
     */
    private void finishSeek() {
        this.releaseSeekTarget();
        if (this.seekTargetKind == SeekTargetKind.MOB) {
            Mob mob = this.carriedMob;
            if (mob != null && mob.isAlive() && !mob.isRemoved()) {
                mob.startRiding(this, true);
            }
        } else if (this.carriedDisplay != null && this.carriedDisplay.isAlive()) {
            this.carriedDisplay.startRiding(this);
        }
        this.seekPhase = SeekPhase.NONE;
        this.seekTicks = 0;
    }

    /**
     * 中止抓取（目标丢失等），回到平时的游荡。
     * <p>
     * 生物分支要额外注意：如果它已经抓在手上、但<b>还没骑上来</b>，必须当场放掉
     * （{@link #releaseCapturedMob(Mob)}）——不然会留下一只无AI、悬在半空、又不属于任何载具的生物。
     * 已经骑上来的（{@code getVehicle() == this}）不动，那是正常携带状态。
     */
    public void abortSeek() {
        this.releaseSeekTarget();
        Mob mob = this.carriedMob;
        if (mob != null && mob.getVehicle() != this) {
            this.releaseCapturedMob(mob);
        }
        // 没抓成：把认领还回去，别的选择器可以来抓它
        this.releaseClaim();
        // 抓取中止之后手里可能已经空了：持久化标志要跟着实际内容走，否则它会永远占着上限名额
        this.refreshPersistenceFromContent();
        // 这次"手动/自动"的标记也一并清掉（手上已经没东西了）
        this.manualCapture = false;
        this.seekPhase = SeekPhase.NONE;
        this.seekTicks = 0;
    }

    /**
     * 清空两个目标引用。
     * <p>
     * 只有在「还没换上载体」时就中止/收尾才会真的持有目标；换上之后引用已是 null，
     * 而原目标那时已经被删除/被抓住，所以这里不需要做任何恢复。
     * <p>
     * 注意<b>不</b>清 {@link #carriedDisplay} / {@link #carriedMob}：那是东西的新载体，要留在世界里。
     */
    private void releaseSeekTarget() {
        this.seekItemTarget = null;
        this.seekMobTarget = null;
    }

    // ===== 携带的生物 =====

    /**
     * 当前携带的生物。
     * <p>
     * 先看字段，再看乘客：骑乘关系和生物身上的 {@link #TAG_CAPTURED} 标记都会跟着存档，而字段不会，
     * 所以读档之后要靠「乘客里有谁带着标记」把它找回来。
     * <p>
     * 字段这一路也要验标记：万一生物已经被{@link #healOrphanedCapture(Mob) 自愈}放掉了（标记被抹掉），
     * 而选择器这边还留着旧引用，不验的话它会一直自认为「身上有东西」、永远保持持久化不肯消失。
     */
    @Nullable
    public Mob getCarriedMob() {
        if (this.carriedMob != null && this.carriedMob.isAlive() && !this.carriedMob.isRemoved()
                && isCaptured(this.carriedMob)) {
            return this.carriedMob;
        }
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof Mob mob && isCaptured(mob)) {
                this.carriedMob = mob;
                return mob;
            }
        }
        this.carriedMob = null;
        return null;
    }

    /** 这只生物是不是正被某个选择器抓着（标记写在生物自己的持久化数据里，见 {@link #TAG_CAPTURED}）。 */
    public static boolean isCaptured(Mob mob) {
        return !mob.level().isClientSide() && mob.getPersistentData().getBoolean(TAG_CAPTURED);
    }

    /**
     * 放掉手上的生物：AI / 重力 / 持久化按抓取时记下的 {@code TAG_WAS_*} 原值还回去，并抹掉标记。
     * <p>
     * 三条路都会走到这里：抓取被打断（{@link #abortSeek()}）、生物被摘下来
     * （{@link #removePassenger(Entity)}）、以及抓取残留自愈（{@link #healOrphanedCapture(Mob)}）。
     * 漏掉任何一条都会留下一只永远定住的生物。
     */
    private void releaseCapturedMob(Mob mob) {
        // 客户端也有一份乘客表（由 SetPassengers 包维护），但持久化数据不过网，
        // 所以标记只存在于服务端；这里顺手挡一道，免得将来有人在客户端路径上碰它。
        if (mob.level().isClientSide() || !mob.getPersistentData().getBoolean(TAG_CAPTURED)) {
            return;
        }
        restoreCapturedFlags(mob);
        if (this.carriedMob == mob) {
            this.carriedMob = null;
        }
        this.entityData.set(DATA_CARRIED_MOB, this.getCarriedMob() != null);
        // 东西放掉了：持久化标志立刻跟着实际内容走。漏掉这一步就会留下"空手却永远不许消失"的选择器，
        // 它们不受"离玩家 >128 格即删除"约束，会一直占着整个维度的选择器名额（见 healStalePersistence）
        this.refreshPersistenceFromContent();
    }

    /**
     * 按抓取前记下的原值还原 AI / 重力 / 持久化，并抹掉全部抓取标记（不管是谁在放它）。
     * <p>
     * 体型也要还：{@link #CARRY_FIT_MODIFIER_ID}（抓取时缩进框）和 {@link #SHRINK_MODIFIER_ID}
     * （send 时缩到消失）两个修饰符一并摘掉，生物回到被抓之前的大小。
     * <p>
     * 还的是<b>一整摞</b>（见 {@link #carryStack}）：背上的骑士也被这两个修饰符缩过，
     * 只还本体的话它们会永远保持被抓时那个小数点大小。
     */
    private static void restoreCapturedFlags(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        mob.setNoAi(data.getBoolean(TAG_WAS_NO_AI));
        mob.setNoGravity(data.getBoolean(TAG_WAS_NO_GRAVITY));
        ((MobPersistenceAccessor) (Object) mob)
            .jafa_setPersistenceRequired(data.getBoolean(TAG_WAS_PERSISTENT));
        for (Mob member : carryStack(mob)) {
            clearScaleModifiers(member);
        }
        data.remove(TAG_CAPTURED);
        data.remove(TAG_WAS_NO_AI);
        data.remove(TAG_WAS_NO_GRAVITY);
        data.remove(TAG_WAS_PERSISTENT);
        data.remove(TAG_DETACHED_SINCE);
    }

    /**
     * send 流程会主动把生物摘下来自己摆位置，那一次脱离<b>不能</b>还原（否则它在上升途中会恢复重力掉下去）。
     * 其余任何脱离——被击杀、被挤出载具、选择器自己被移除——都当场还原。
     */
    @Override
    protected void removePassenger(Entity passenger) {
        if (passenger instanceof Mob mob && passenger != this.sendingMob) {
            this.releaseCapturedMob(mob);
        }
        super.removePassenger(passenger);
    }

    /**
     * 本体被移除（被击杀 / 被 {@code discard} / 换维度）时，把自己占着的目标认领全部还回去
     * （见 {@link SelectorTargetClaims}）——不然那只物品/生物会被"一个已经不存在的选择器"占着，
     * 一直没人能抓（虽然 {@code SelectorTargetClaims} 有超时兜底，但能当场清掉就别留着）。
     */
    @Override
    public void remove(Entity.RemovalReason reason) {
        SelectorTargetClaims.releaseAllOf(this);
        this.claimedTarget = null;
        super.remove(reason);
    }

    /**
     * 抓取悬空计时用到的"自然生成选择器"标记（见 {@link #markNaturalSpawn()}）。
     * <p>老存档里的自然选择器没有这个标记，靠它们的永久发光效果（{@link MobEffects#GLOWING}）认。
     */
    public static final String TAG_NATURAL_SPAWN = "jafa_selector_natural";

    /**
     * 抓取残留自愈：一只带着 {@link #TAG_CAPTURED} 标记、却<b>不在任何载具上</b>的生物，如果这个状态
     * 持续超过 {@link #CAPTURE_ORPHAN_GRACE_TICKS} 刻，就按抓取前的原值放掉它。
     * <p>
     * <b>为什么需要它</b>：抓稳（{@link #captureMob(Mob)}）与骑上去之间有 2 秒（40 刻）的窗口，
     * 而区块卸载走的是 {@code Entity#setRemoved}（PersistentEntitySectionManager.java:235），
     * 那条路<b>不</b>会调用 {@link #removePassenger(Entity)}——于是选择器一被卸载，这只生物就留在了
     * 无AI + 无重力的状态里。这里给它一个自愈出口。
     * <p>
     * <b>为什么用时间戳而不是布尔</b>：send 流程中途也会「不在载具上」（它要自己上升），
     * 靠时间戳把这段正常窗口和真正的残留区分开；而只要它<b>骑着</b>（哪怕是骑着等待下一轮 send），
     * 计时就清零，于是每次悬空都从 0 重新起算，不会拿一个过期的旧时间戳当场误判。
     * <p>
     * <b>调用点</b>：{@code MobLuckyDespawnMixin}（那是本模组唯一一个「每个生物每刻都会经过」的地方，
     * 见 ServerLevel.java:403-410），并且调用前先用两个标志位筛过一遍，绝大多数生物不会走到这里。
     */
    public static void healOrphanedCapture(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (!data.getBoolean(TAG_CAPTURED)) {
            return;
        }
        if (mob.isPassenger()) {
            // 正常骑着：作废悬空计时（下次被 send 摘下来时重新起算）
            if (data.getLong(TAG_DETACHED_SINCE) != 0L) {
                data.putLong(TAG_DETACHED_SINCE, 0L);
            }
            return;
        }
        long now = mob.level().getGameTime();
        long since = data.getLong(TAG_DETACHED_SINCE);
        if (since == 0L) {
            data.putLong(TAG_DETACHED_SINCE, now);
        } else if (now - since > CAPTURE_ORPHAN_GRACE_TICKS) {
            // 选择器可能已经不在了，所以就地还原：标记里记的原值已经够用
            restoreCapturedFlags(mob);
        }
    }

    // ===== 发送流程（/jafa send）=====

    /**
     * <b>旁观操控右键的 send</b>（需求 6.5.21 已明确）：玩家在操控本选择器、手里已经有东西时，
     * 先在客户端弹出的下拉列表里挑一位"需要支援的玩家"（见 {@code PilotSendListPayload}），
     * 确认之后走这里。
     *
     * <h3>与默认 send 的区别（这就是 6.5.18 留的那个 TODO 的答案）</h3>
     * <ul>
     *   <li><b>不进幸运名单</b>：默认 send 收尾是 {@code enqueueMob}/{@code enqueueStack}，
     *       这一条改成把内容装进一个<b>支援幸运方块</b>，生成在目标玩家附近 3 格内
     *       （{@link SupportGift}），方块出现 5 秒后自己碎裂、把内容爆出来；</li>
     *   <li><b>余波</b>：方块出现时，目标玩家 50 格内的一具附体空壳会被随机挑中、坠机 2 秒；</li>
     *   <li>上升/缩小的动画仍是同一套（时间轴不变），只是收尾的去处不同。</li>
     * </ul>
     * <p>目标为 {@code null}（Technoblade / Dream 这两个调试项、或"暂无需要支援的玩家"占位项）时
     * <b>什么都不做</b>（需求原话：选择这两个选项不会有任何实际效果）。
     *
     * @return 开始了流程返回 true；目标无效、空手或正忙返回 false
     */
    public boolean startPilotSend(@Nullable net.minecraft.server.level.ServerPlayer target) {
        if (target == null) {
            ModMain.LOGGER.info("[支援] 选中的是调试项/占位项，不做任何事（东西仍留在选择器上）");
            return false;
        }
        this.pilotGiftTarget = target;
        if (!this.startSend()) {
            this.pilotGiftTarget = null;   // 没开成（空手/正忙）：清掉，别把下次 send 也带偏
            return false;
        }
        ModMain.LOGGER.info("[支援] 开始把 {} 送到 {} 身边", this.getCarriedSummary(), target.getGameProfile().getName());
        return true;
    }

    /** 日志用：手里那把东西的简述（生物类型或物品名）。 */
    private String getCarriedSummary() {
        Mob mob = this.getCarriedMob();
        if (mob != null) {
            return mob.getType().toShortString();
        }
        Display.ItemDisplay display = this.getCarriedDisplay();
        if (display == null) {
            return "（空）";
        }
        ItemStack stack = this.readDisplayItem(display);
        return stack.isEmpty() ? "（未知物品）" : stack.getHoverName().getString();
    }

    /**
     * {@code /jafa send}：播放 send 动画，并把它携带的东西送走——生物和物品走<b>同一条时间轴</b>
     * （1.0 秒 = 20 刻，与 send 动画对齐）：
     * <ul>
     *   <li>时间码 0~0.5（10 刻）：被抓的东西匀速向上，距离由配置 {@code lucky_selector.send_lift_px} 决定；</li>
     *   <li>时间码 0.5~0.75（5 刻）：匀速缩小到消失；</li>
     *   <li>时间码 0.75 之后：删除它（生物就地从世界里消失，不再放出）。</li>
     * </ul>
     * <p>
     * 生物分支见 {@link #startSendMob(Mob)}：两者缩小的实现不同——物品展示实体用
     * {@code Display} 自己的变换插值（见 {@link #startShrink(Display.ItemDisplay)}），
     * 生物没有通用的缩放变换，只能逐刻改 {@code Attributes.SCALE}（见 {@link #setShrink(Mob, double)}）。
     *
     * @return 身上有东西并开始了流程返回 true；空手返回 false
     */
    public boolean startSend() {
        if (this.level().isClientSide()) {
            return false;
        }
        // 无论怎么开送的，这把东西从此归 send 流程管：手动标记清掉
        this.manualCapture = false;
        Mob mob = this.getCarriedMob();
        if (mob != null) {
            // 只有已经骑上来的才算「身上有东西」。抓取中途（已抓稳、还没骑上来）不给送：
            // 那 1 秒里它归抓取流程管，硬插进来会把「抓住」和「放掉」两套状态搅在一起。
            return mob.getVehicle() == this && this.startSendMob(mob);
        }
        Display.ItemDisplay display = this.getCarriedDisplay();
        if (display == null) {
            return false;
        }
        // 万一还在抓取：先收尾（不会动展示实体），再开始发送
        this.abortSeek();
        // 位置要由自己驱动，所以先让它脱离载具
        display.stopRiding();
        this.carriedDisplay = display;
        this.sendRiseFrom = display.position();
        this.sendRiseTo = this.sendRiseFrom.add(
            0.0D, ModConfig.SELECTOR_SEND_LIFT_PX.get() / PIXELS_PER_BLOCK, 0.0D);
        this.sendPhase = SendPhase.RISE;
        this.sendTicks = 0;
        this.requestAnimation(SelectorAnimation.SEND);
        return true;
    }

    /**
     * 发送一只生物：和发送物品同一套时间轴，只有「怎么缩小」不一样。
     * <p>
     * 顺序有讲究：<b>先把 {@link #sendingMob} 记上，再 {@code stopRiding()}</b>——
     * 摘下来会走到 {@link #removePassenger(Entity)}，那里靠这个字段区分「send 主动摘下」和「被外力打断」，
     * 反过来的话生物会在这一瞬间被还原（重力回来 → 上升途中直接掉下去）。
     */
    private boolean startSendMob(Mob mob) {
        // 万一还在抓取：先收尾（这时它已经骑在本体上，abortSeek 不会放掉它），再开始发送
        this.abortSeek();
        this.sendingMob = mob;
        mob.stopRiding();
        this.sendRiseFrom = mob.position();
        this.sendRiseTo = this.sendRiseFrom.add(
            0.0D, ModConfig.SELECTOR_SEND_LIFT_PX.get() / PIXELS_PER_BLOCK, 0.0D);
        this.sendPhase = SendPhase.RISE;
        this.sendTicks = 0;
        this.requestAnimation(SelectorAnimation.SEND);
        return true;
    }

    /**
     * 当前携带的物品展示实体。
     * <p>
     * 先看字段，再看乘客：骑乘关系会跟着存档，而这个字段不会，所以读档之后要靠乘客找回来。
     */
    @Nullable
    public Display.ItemDisplay getCarriedDisplay() {
        if (this.carriedDisplay != null && this.carriedDisplay.isAlive()) {
            return this.carriedDisplay;
        }
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof Display.ItemDisplay itemDisplay) {
                this.carriedDisplay = itemDisplay;
                return itemDisplay;
            }
        }
        return null;
    }

    /** 服务端每刻推进发送流程。 */
    private void tickSend() {        if (this.sendPhase == SendPhase.NONE) {
            return;
        }
        if (this.sendingMob != null) {
            this.tickSendMob();
            return;
        }
        Display.ItemDisplay display = this.carriedDisplay;
        if (display == null || !display.isAlive()) {
            this.carriedDisplay = null;
            // 东西已经不在身上了：恢复成"空手"，重新允许被刷掉
            this.refreshPersistenceFromContent();
            this.sendPhase = SendPhase.NONE;
            this.sendTicks = 0;
            return;
        }
        this.sendTicks++;
        // 发送期间本体原地不动
        this.holdPosition();
        switch (this.sendPhase) {
            case RISE -> {
                // 时间码 0~0.5：起终点之间逐刻 lerp（和抓取抬升同一套写法）
                double progress = Math.min(1.0D, this.sendTicks / (double) SEND_RISE_TICKS);
                Vec3 pos = this.sendRiseFrom.lerp(this.sendRiseTo, progress);
                display.setPos(pos.x, pos.y, pos.z);
                if (this.sendTicks >= SEND_RISE_TICKS) {
                    this.sendPhase = SendPhase.SHRINK;
                    this.sendTicks = 0;
                    this.startShrink(display);
                }
            }
            case SHRINK -> {
                // 时间码 0.5~0.75：缩小完全交给客户端插值（见 startShrink），服务端只等它走完
                if (this.sendTicks >= SEND_SHRINK_LINGER_TICKS) {
                    // 送走：默认收进幸运名单，操控右键那条则装进支援幸运方块（需求 6.5.21）
                    ItemStack stack = this.readDisplayItem(display);
                    if (this.pilotGiftTarget != null) {
                        this.deliverPilotGift(null, stack);
                    } else {
                        this.enqueueCarriedItem(display);
                    }
                    display.discard();
                    this.carriedDisplay = null;
                    // 东西已经不在世界里了，认领也就没有意义
                    this.releaseClaim();
                    // send 把目标送出去了 = 又变回空手 → PersistenceRequired 置回 false
                    this.refreshPersistenceFromContent();
                    this.sendPhase = SendPhase.NONE;
                    this.sendTicks = 0;
                }
            }
            default -> {
            }
        }
    }

    /**
     * 服务端每刻推进「发送生物」。
     * <p>
     * 时间轴 = 选择器自己那一秒的 send 动画（{@link #MOB_SEND_TICKS} 刻）：
     * <ul>
     *   <li><b>体型全程逐刻线性缩小</b>（{@link #MOB_SHRINK_STEPS} 步，到 {@link #SHRINK_MIN_FACTOR}）；</li>
     *   <li><b>位置只在前 {@link #SEND_RISE_TICKS} 刻上升</b>（和动画里零件抬起的那半段对齐）；</li>
     *   <li>最后一刻删除。</li>
     * </ul>
     * <b>为什么缩小要铺满全程、而且用线性（这里改过一次，理由值得留着）</b>：上一版是
     * 「前 10 刻只管上升、后 10 刻<b>等比</b>缩到 1/16」。等比想让"相对变化"均匀，效果却相反——
     * 它把<b>四分之三的视觉尺寸变化挤进了前 20% 的时间</b>：0.9 格高的生物第 4 步就只剩 0.30 格，
     * 第 6 步 0.17 格，剩下 6 步全在 0.17 格以下（几个像素），肉眼就是「闪了两三帧然后没了」。
     * 线性则是每步缩掉同样多的<b>绝对</b>尺寸（20 步时每步 0.042 格），到第 14 步还有 0.31 格，
     * 看得见的收缩过程摊满了整秒；末段那几步相对变化虽大，但那时它已经只有几个像素。
     * 这同时解决了「步数太少」的问题：1 秒 20 步是服务端能给的最高时间分辨率。
     */
    private void tickSendMob() {
        Mob mob = this.sendingMob;
        if (mob == null || !mob.isAlive() || mob.isRemoved()) {
            this.sendingMob = null;
            this.carriedMob = null;
            this.refreshPersistenceFromContent();
            this.sendPhase = SendPhase.NONE;
            this.sendTicks = 0;
            return;
        }
        // 注意：这条流程里 sendTicks 是<b>全程</b>计数（切相时不清零），缩小按它算
        this.sendTicks++;
        // 发送期间本体原地不动
        this.holdPosition();
        // 全程线性缩小。整摞一起缩（见 carryStack）：背上骑着骑士的那些（猪塔、女巫骑恶魂、骷髅骑士……）
        // 要和本体同步变小，否则底面那只缩没了、骑士还是原尺寸悬在半空。
        double shrink = shrinkFactor(this.sendTicks / (double) MOB_SHRINK_STEPS);
        for (Mob member : carryStack(mob)) {
            setShrink(member, shrink);
        }
        if (this.sendPhase == SendPhase.RISE) {
            // 前 10 刻：起终点之间逐刻 lerp（和抓取抬升同一套写法）
            double progress = Math.min(1.0D, this.sendTicks / (double) SEND_RISE_TICKS);
            Vec3 pos = this.sendRiseFrom.lerp(this.sendRiseTo, progress);
            mob.setPos(pos.x, pos.y, pos.z);
            // 上升只有 10 刻，不强制每刻同步的话位置包会被 updateInterval 抽稀
            mob.hasImpulse = true;
            if (this.sendTicks >= SEND_RISE_TICKS) {
                this.sendPhase = SendPhase.SHRINK;
            }
        }
        if (this.sendTicks >= MOB_SEND_TOTAL_TICKS) {
            // 送走。顺序有讲究：
            // 1) 先把抓取时压上去的临时状态还原成"原来的那只"——否则存进名单/支援方块的 NBT 里带着
            //    NoAI / NoGravity / 抓取标记，将来放出来会是一只定住不动的僵尸；
            // 2) 再看这一次是哪种 send：
            //    · <b>操控右键的支援送</b>（{@link #pilotGiftTarget} 非空，需求 6.5.21）→ 把它装进支援幸运方块
            //      送到目标玩家身边，<b>不进幸运名单</b>；
            //    · 默认那条 → 走 enqueueMob 收进名单：属于幸运生物事件的生物（猫/猪/狼/僵尸……）
            //      收进去的是<b>整个事件</b>（整群猫、猪塔+村民、女巫骑恶魂……），只有没有对应事件的
            //      生物（凋灵之类）才存它自己的完整 NBT（需求 1~4）；
            // 3) 最后把<b>一整摞</b>从世界里删掉——名单里只记最下面那一只（需求：骑士只跟着消失、
            //    不单独计入），所以背上的骑士要在这里跟着一起没，不能留给原版"放下车"。
            this.releaseCapturedMob(mob);
            if (this.pilotGiftTarget != null) {
                this.deliverPilotGift(this.saveForGift(mob), ItemStack.EMPTY);
            } else {
                boolean stored = LuckyRoster.get((ServerLevel) this.level()).enqueueMob(mob);
                if (!stored) {
                    ModMain.LOGGER.warn("[幸运名单] 收不下这只生物（名单已满）：{}", mob.getType());
                }
            }
            // carryStack 是"从最上面那只开始、本体最后"，按这个顺序丢：先丢骑士，最后丢本体，
            // 免得中间出现"载具已经没了、乘客被放下车"的过渡状态
            for (Mob member : carryStack(mob)) {
                member.discard();
            }
            this.sendingMob = null;
            this.carriedMob = null;
            // 到这一刻东西才真的从世界上没了，认领也到此为止（send 途中它是脱离载具的状态，
            // 那 1 秒里如果放开认领，别的选择器会来抢一只马上要消失的生物）
            this.releaseClaim();
            // send 把目标送出去了 = 又变回空手 → PersistenceRequired 置回 false
            this.refreshPersistenceFromContent();
            this.sendPhase = SendPhase.NONE;
            this.sendTicks = 0;
        }
    }

    /**
     * <b>把这次 send 的内容送进"支援幸运方块"</b>（{@link #startPilotSend} 那条路，需求 6.5.21）：
     * 方块生成在目标玩家附近、5 秒后自碎并爆出内容。
     * <p>
     * 目标玩家在 1 秒动画期间下线了就退回默认那条（收进幸运名单）——东西不能凭空消失。
     */
    private void deliverPilotGift(@Nullable CompoundTag mobTag, ItemStack item) {
        net.minecraft.server.level.ServerPlayer target = this.pilotGiftTarget;
        this.pilotGiftTarget = null;
        ServerLevel level = (ServerLevel) this.level();
        net.minecraft.server.level.ServerPlayer online = target == null
            ? null
            : level.getServer().getPlayerList().getPlayer(target.getUUID());
        if (online == null) {
            ModMain.LOGGER.warn("[支援] 目标玩家已不在线，改走默认 send（收进幸运名单）");
            this.fallbackGiftToRoster(mobTag, item);
            return;
        }
        if (!SupportGift.deliver(level, online, mobTag, item)) {
            // 落点找不到：内容还在手上（还没 discard），但这条 send 的时间轴已经走完，
            // 所以退回默认那条把它收进名单，别让它凭空消失
            this.fallbackGiftToRoster(mobTag, item);
        }
    }

    /** 支援送失败时的兜底：把内容原样收进幸运名单（与默认 send 收尾一致，只是不用再走动画）。 */
    private void fallbackGiftToRoster(@Nullable CompoundTag mobTag, ItemStack item) {
        LuckyRoster roster = LuckyRoster.get((ServerLevel) this.level());
        if (mobTag != null) {
            if (!roster.enqueueStored(mobTag)) {
                ModMain.LOGGER.warn("[支援] 兜底入队失败（名单已满），内容丢失：{}", mobTag.getString("id"));
            }
            return;
        }
        if (!item.isEmpty() && !roster.enqueueStack((ServerLevel) this.level(), item)) {
            ModMain.LOGGER.warn("[支援] 兜底入队失败（名单已满），内容丢失：{}", item);
        }
    }

    /**
     * 把一只（已经 {@link #releaseCapturedMob} 还原过状态的）生物存成 NBT，供支援方块使用；
     * 存不了（原版不允许保存的实体）返回 {@code null}。
     */
    @Nullable
    private static CompoundTag saveForGift(Mob mob) {
        CompoundTag tag = new CompoundTag();
        return mob.save(tag) ? tag : null;
    }

    /**
     * 把物品展示实体里显示的那件物品收进幸运名单。
     * <p>
     * 读物品那一步抽成了 {@link #readDisplayItem(Display.ItemDisplay)}（支援方块那条路也要用）。
     */
    private void enqueueCarriedItem(Display.ItemDisplay display) {
        ItemStack stack = this.readDisplayItem(display);
        if (stack.isEmpty()) {
            return;
        }
        if (!LuckyRoster.get((ServerLevel) this.level()).enqueueStack((ServerLevel) this.level(), stack)) {
            ModMain.LOGGER.warn("[幸运名单] 收不下这件物品（名单已满）：{}", stack);
        }
    }

    /**
     * 读出物品展示实体里显示的那件物品；读不到返回空栈。
     * <p>
     * {@code Display.ItemDisplay#getItemStack()} 是 <b>private</b>，拿不到；它自己存的 NBT 里
     * 用的是 {@code "item"} 键（Display.java:718），所以只能走 NBT 读回来——
     * 和 {@link #startShrink(Display.ItemDisplay)} 改缩放走的是同一条路。
     */
    private ItemStack readDisplayItem(Display.ItemDisplay display) {
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        if (!tag.contains("item", Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parseOptional(display.registryAccess(), tag.getCompound("item"));
    }

    /**
     * send 缩小的倍率：{@code p ∈ [0,1]} 时从 1 <b>线性</b>收到 {@link #SHRINK_MIN_FACTOR}（1/16）。
     * <p>
     * <b>为什么是线性而不是等比</b>（这里改过一次，两边的账都值得留着）：等比（每步乘掉同样比例）
     * 想让「相对变化」均匀，结果反而把<b>四分之三的视觉尺寸变化挤进前 20% 的时间</b>——
     * 0.9 格高的生物等比 10 步时第 4 步就只剩 0.30 格、第 6 步 0.17 格，剩下 6 步全在几个像素里，
     * 肉眼就是「闪了两三帧然后没了」。线性则是每步缩掉同样多的<b>绝对</b>尺寸
     * （20 步时每步 0.042 格），第 14 步还有 0.31 格，看得见的收缩摊满整秒。
     * 末段每步的相对变化确实更大，但那时它已经只有几个像素，看不出来。
     * <p>
     * 终点取 {@code 0.0625} 是 {@code SCALE} 属性下限（Attributes.java:124-126），
     * 正好落在底限上，不会出现「缩到一半被属性截住」的停顿。
     */
    private static double shrinkFactor(double p) {
        return 1.0D - Mth.clamp(p, 0.0D, 1.0D) * (1.0D - SHRINK_MIN_FACTOR);
    }

    /**
     * 把生物的体型乘上 {@code factor}（1.0 = 不变）。这一层只负责 send 的缩小：
     * 它和 {@link #CARRY_FIT_MODIFIER_ID} 那层是连乘关系，所以是在「已经被缩进框里」的基础上继续缩。
     * <p>
     * <b>为什么用属性修饰符而不是改基础值</b>：{@code Attributes.SCALE} 的基础值可能已经被别人用过
     * （比如本模组「巨型生物」那套），修饰符只叠一层，收回时 {@link #clearScaleModifiers(Mob)} 一摘就回到原样。
     * 用 {@code ADD_MULTIPLIED_TOTAL}：最终值 = 各修饰符连乘（AttributeInstance.java:145-163），
     * 所以 {@code amount = factor - 1}。
     * <p>
     * <b>为什么收不到 0</b>：{@code SCALE} 是 {@code RangedAttribute}，取值范围 <b>0.0625 ~ 16</b>
     * （Attributes.java:124-126），所以最后会停在 1/16 大小，再由 {@link #tickSendMob()} 删除。
     * 同步粒度是每刻一次（{@code ServerEntity#sendChanges} 里 {@code getAttributesToSync()} 发完即清，
     * ServerEntity.java:333-340），所以「顺不顺」取决于步数，见 {@link #MOB_SHRINK_STEPS}。
     */
    private static void setShrink(Mob mob, double factor) {
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale == null) {
            return;
        }
        scale.removeModifier(SHRINK_MODIFIER_ID);
        scale.addTransientModifier(new AttributeModifier(
            SHRINK_MODIFIER_ID, factor - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** 摘掉「缩进框」和「缩到消失」两个修饰符，把体型还给生物（放掉它时调用）。 */
    private static void clearScaleModifiers(Mob mob) {
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale == null) {
            return;
        }
        scale.removeModifier(SHRINK_MODIFIER_ID);
        scale.removeModifier(CARRY_FIT_MODIFIER_ID);
    }

    /**
     * 开始缩小：<b>一次性</b>把变换写成"缩放 0"，然后让客户端在 {@link #SEND_SHRINK_TICKS} 刻内
     * 线性插值过去（时间码 0.5~0.75）。
     * <p>
     * <b>为什么必须是一次性写入 + 客户端插值</b>：变换插值的"起点刻"只在
     * {@code start_interpolation} 这个同步数据<b>发生变化</b>时才会被客户端重置
     * （Display.java:116-118 → 145-149），而 {@code SynchedEntityData#set} 对"值没变"的写入整段跳过
     * （SynchedEntityData.java:79-87）。所以逐刻改缩放时，为了让起点每次都刷新，只能让
     * {@code start_interpolation} 在 0/1 之间来回切——而那恰恰等于每刻把插值窗口挪一次，
     * 渲染出来的尺寸就会忽大忽小。一次写到位、剩下的交给插值，才是这个机制的正确用法。
     * <p>
     * <b>标签必须以实体自己保存的 NBT 为底</b>：{@code Entity#load} 在 NBT 里没有 {@code Pos} 键时
     * 会把实体摆到世界原点 (0,0,0)。早先几版就是踩在这里——缩放确实写进去了，
     * 但同一次 {@code load} 把实体扔到原点，看起来就是"物品瞬间消失"。
     * （日志证据：写完后 dump 出的 NBT 是 {@code Pos:[0.0d,0.0d,0.0d]}，而实体实际在 (68.07/-54.05/-282.40)。）
     * <p>
     * 变换只能走 NBT——{@code Display#setTransformation} 是 private；键名是公开常量
     * {@code Display.TAG_TRANSFORMATION}（Display.java:81），读取时用 {@code Transformation.EXTENDED_CODEC}
     * （Display.java:210-214）。{@code start_interpolation} 取 1 而不是 0：0 是它的默认值
     * （Display.java:189），写 0 等于没写，客户端不会重置起点。1 表示"晚一刻开始"，肉眼无差别。
     */
    private void startShrink(Display.ItemDisplay display) {
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        Tag scaleZero = Transformation.EXTENDED_CODEC
            .encodeStart(NbtOps.INSTANCE,
                new Transformation(null, null, new Vector3f(0.0F, 0.0F, 0.0F), null))
            .result().orElse(null);
        if (scaleZero != null) {
            tag.put(Display.TAG_TRANSFORMATION, scaleZero);
        }
        tag.putInt(Display.TAG_TRANSFORMATION_INTERPOLATION_DURATION, SEND_SHRINK_TICKS);
        tag.putInt(Display.TAG_TRANSFORMATION_START_INTERPOLATION, 1);
        display.load(tag);
    }

    // ===== 动画触发 =====

    /**
     * 请求播放某段动画。
     * <p>
     * 在服务端调用会通过同步实体数据把信号发给所有能看到它的客户端；
     * 在客户端调用则只影响本地那一份。三段动画动的是同一批骨骼，
     * 客户端收到信号后会先停掉另外两段（见 {@link #startAnimation}）。
     *
     * @param animation 要播放的动画
     */
    public void requestAnimation(SelectorAnimation animation) {
        EntityDataAccessor<Integer> signal = switch (animation) {
            case GRAB -> DATA_GRAB_SIGNAL;
            case SEND -> DATA_SEND_SIGNAL;
        };
        this.entityData.set(signal, this.entityData.get(signal) + 1);
    }

    /**
     * 客户端：同步数据里的计数一变就执行对应指令；同一条指令可以反复触发。
     * <p>
     * <b>顺序有讲究</b>：先推进链式倒计时、再处理新收到的指令。反过来的话，grab 到达的那一帧会把倒计时
     * 立刻多减一格，retreat 就会比 stretch 早 1 刻（50 毫秒）开始。
     */
    private void syncAnimationSignals() {
        // 1) 链式接续：倒计时归零的这一刻直接切到 retreat。
        //    startAnimation 会在同一次调用里「停掉 stretch + 启动 retreat」，
        //    所以中间不存在"没有任何动画在播"的那一帧，也就不会闪回默认姿态。
        if (this.grabChainTicks > 0 && --this.grabChainTicks == 0) {
            this.startAnimation(this.retreatAnimationState);
        }

        // 2) 处理新指令
        int grab = this.entityData.get(DATA_GRAB_SIGNAL);
        if (grab != this.seenGrabSignal) {
            this.seenGrabSignal = grab;
            this.startAnimation(this.stretchAnimationState);
            // 必须在 startAnimation 之后再设：startAnimation 会先 stopAnimations()，那里面会清掉链子
            this.grabChainTicks = ANIMATION_TICKS;
        }
        int send = this.entityData.get(DATA_SEND_SIGNAL);
        if (send != this.seenSendSignal) {
            this.seenSendSignal = send;
            // 新的 send 指令会打断未完成的 grab 链（stopAnimations 里清零倒计时）
            this.startAnimation(this.sendAnimationState);
        }
    }

    /**
     * 播放某段动画：先停掉另外两段再启动它。
     * <p>
     * 三段动画动的都是 {@code bone2} 下的同一批骨骼，而 {@code KeyframeAnimations} 是<b>叠加</b>写姿态的，
     * 同时播两段会互相污染，所以这里强制互斥。
     * <p>
     * 顺带一提：<b>不 stop 就会停在最后一帧</b>——非 looping 的动画越过末帧后每刻都在重复套用末帧的值。
     */
    public void startAnimation(AnimationState state) {
        this.stopAnimations();
        state.start(this.tickCount);
    }

    /** 停掉全部动画（并取消未完成的 grab 链），回到初始姿态。 */
    public void stopAnimations() {
        this.stretchAnimationState.stop();
        this.retreatAnimationState.stop();
        this.sendAnimationState.stop();
        this.grabChainTicks = 0;
    }

    // ===== 伤害免疫 =====

    /**
     * <b>永远免疫摔落伤害。</b>
     * <p>
     * 直接返回 false 而不是只在 {@link #hurt} 里拦：原版 {@code LivingEntity#causeFallDamage} 一旦算出伤害
     * （{@code calculateFallDamage(...) > 0}）就必然会 {@code playSound(摔落音效)} + {@code playBlockFallSound()}
     * 再调 {@code hurt}，只有从这里返回 false 才能连音效和落地粒子一起免掉。
     * <p>
     * 它平时 {@code setNoGravity(true)} + {@code GRAVITY = 0}，本来就掉不下来；这层是保证——
     * 比如以后被 AI 带着俯冲、被活塞/传送挪到高处、或者有人临时把重力打开，都不会摔伤。
     */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier,
            net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        // 兜底：别的代码直接拿 fall 类型调 hurt 的情况
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    /**
     * 属性：最大生命 20；飞行速度 <b>1.2</b>（原版蜜蜂 0.6 的两倍）；另附移动速度与索敌距离。
     * <p>
     * 想按情况改移速请改 {@link #getFlightSpeedMultiplier()}，不用动这里的基础值。
     * <p>
     * <b>重力归零</b>：1.21.1 里重力是这样算的——
     * {@code Entity#getGravity()} = {@code isNoGravity() ? 0.0 : getDefaultGravity()}（Entity.java:1174），
     * 而 {@code LivingEntity#getDefaultGravity()} 读的是 {@code Attributes.GRAVITY} 属性（LivingEntity.java:2215-2217），
     * {@code travel} 里用的就是它（LivingEntity.java:2221）。所以这里做两层保险：
     * 构造时 {@code setNoGravity(true)}（走 boolean 那条），属性里再把 GRAVITY 基础值设成 0（走属性那条）。
     * 这样即使以后有谁把它 {@code setNoGravity(false)}，它也不会突然开始下坠。
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.25D)
            .add(Attributes.FLYING_SPEED, 1.2D)
            .add(Attributes.FOLLOW_RANGE, 32.0D)
            .add(Attributes.GRAVITY, 0.0D);
    }
}
