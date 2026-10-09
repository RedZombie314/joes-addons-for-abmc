package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 近战低概率特攻"重锤"的<b>整套动作</b>（跨很多刻的状态机）。
 *
 * <h2>用户指定的动作流程</h2>
 * <ol>
 *   <li><b>准备</b>：胸甲栏 ← 鞘翅，主手 ← 烟花火箭，副手 ← 钻石胸甲；
 *       先检查头顶空间：最高爬到<b>目标高度上方 {@value #MAX_CLIMB_BLOCKS} 格</b>（跟目标走，不是跟起手点）、
 *       且<b>离天花板不少于 {@value #CEILING_MARGIN} 格</b>；从起手点到最高点不足
 *       {@value #MIN_HEADROOM} 格就<b>取消这次重锤</b>，什么都不做；</li>
 *   <li><b>爬升</b>：进入鞘翅滑翔，抬头朝上，用<b>时长 1 秒</b>的烟花火箭（每颗推进
 *       {@value #ROCKET_BOOST_TICKS} 刻，到点收掉）一路往上顶；</li>
 *   <li><b>俯冲</b>：到达高度后低头（俯角 {@value #DIVE_PITCH_MIN}~{@value #DIVE_PITCH_MAX} 度）、
 *       <b>始终面向目标</b>，并且每 {@value #DIVE_ROCKET_INTERVAL} 刻补一颗烟花继续加速；</li>
 *   <li><b>换重锤</b>（距目标 {@value #MACE_EQUIP_DISTANCE} 格）：主手 ← 致密 V 重锤，
 *       同时把背上的鞘翅换成副手那件钻石胸甲（胸甲因此开始提供护甲，也不再滑翔 —— 直接进入自由落体，
 *       重锤的"坠落加成"就靠这一段攒）；</li>
 *   <li><b>砸</b>（距目标 {@value #MACE_HIT_DISTANCE} 格）：零帧起手，一锤子下去
 *       （伤害走原版重锤公式：坠落距离 × 致密附魔，见 {@code MaceItem#getAttackDamageBonus}）；</li>
 *   <li><b>落水自救</b>：万一到<b>离地 {@value #WATER_TRIGGER_HEIGHT} 格</b>还没砸中，
 *       主手换成水桶、算好落点先放一桶水（零帧起手），手里随即换成空桶；
 *       落进水里之后再用空桶把水收回去。</li>
 * </ol>
 *
 * <h2>暮色九头蛇：这一锤砸的是头，不是身体（用户指定）</h2>
 * 九头蛇的身体<b>整段免疫伤害</b>（{@code Hydra#hurt} 只放行"无视无敌"的伤害类型），
 * 能吃的只有头/脖子那些 {@code HydraPart}；所以目标若是九头蛇，
 * {@link #begin} 起手时就<b>锁定一个活着的头</b>（优先嘴大张着的那个），
 * 之后俯冲瞄准、换锤距离、砸击线段判定、伤害结算、粒子位置<b>全部冲着那个头去</b>
 * （见 {@link #aimBodyOf}；头被打死会另挑一个）。
 *
 * <h2>为什么写成"状态机 + 空壳每刻推进"</h2>
 * {@code PossessedAttackEvent#run} 只有一瞬间，而这套动作要几百刻；
 * 所以 {@code run} 只负责 {@link #begin}（登记状态），之后由
 * {@link PlayerShellEntity#tick()} 每刻调 {@link #tick} 推进 ——
 * 期间空壳不抽签、不走位（走位/抽签在 {@code PlayerShellEntity#tick} 开头就被这条路截走了）。
 */
public final class OrbPossessionMaceAttack {

    /** 最高爬升高度（格）：<b>目标高度上方</b>这么多，用户指定 50（原为"起点上方 50"）。 */
    private static final int MAX_CLIMB_BLOCKS = 50;
    /** 与天花板保持的最小距离（格）：用户指定 5。 */
    private static final double CEILING_MARGIN = 5.0D;
    /** 头顶空间不足这么多格就取消这次重锤（用户："如果空间太低则会取消此次重锤事件"）。 */
    private static final int MIN_HEADROOM = 10;
    /** 每颗烟花推进的时长（刻）：用户指定 1 秒。到点就把那颗烟花收掉，做到"正好 1 秒"。 */
    private static final int ROCKET_BOOST_TICKS = 20;
    /** 俯冲阶段每多少刻补一颗烟花。 */
    private static final int DIVE_ROCKET_INTERVAL = 10;
    /** 俯冲俯角范围（度）：用户指定 60~90。 */
    private static final float DIVE_PITCH_MIN = 60.0F;
    private static final float DIVE_PITCH_MAX = 90.0F;
    /** 距目标这么远就换重锤（格）：用户指定 10。 */
    private static final double MACE_EQUIP_DISTANCE = 10.0D;
    /** 距目标这么近就砸下去（格）：用户指定 2。 */
    private static final double MACE_HIT_DISTANCE = 2.0D;
    /** 离地不足这么多格还没砸中 → 落水自救（用户指定半格）。 */
    private static final double WATER_TRIGGER_HEIGHT = 0.5D;
    /** 俯冲/砸落阶段转落水自救的提前量（刻）：只留 1 刻 —— 砸击优先，水留到最后一刻。
     *  <p>落地保护本身是在"移动之前"完成的（{@code preMoveStep}），所以哪怕只提前 1 刻也来得及；
 *     下界这类 ultrawarm 维度没有水，改成提前 {@value #PEARL_LOOKAHEAD_TICKS} 刻丢一颗末影珍珠
 *     （{@code Phase.PEARL}）：珍珠砸地时原版把空壳传送过去并清零坠落距离，
 *     空壳只吃末影珍珠那 {@value #ENDER_PEARL_FALL_DAMAGE} 点摔落伤害。
     *  这样就不会出现"还没轮到砸击就先放水"的冲突（实测反馈：空壳更倾向于落地水而不是重锤）。 */
    private static final int MACE_WATER_LOOKAHEAD_TICKS = 1;
    /** 俯冲/砸落阶段的高度提前量（格）：同样只留一点点，把机会先让给砸击。 */
    private static final double MACE_WATER_HEIGHT_MARGIN = 0.5D;
    /** 临时落地水自救（与砸击无冲突，不属于重锤动作）的提前量：2 刻 / 2 格余量。 */
    private static final int WATER_LOOKAHEAD_TICKS = 2;
    private static final double WATER_HEIGHT_MARGIN = 2.0D;
    /** 放水点与空壳的水平距离上限（格）：超过就改用"正下方"那格，免得水放到老远的地方去。 */
    private static final double WATER_MAX_HORIZONTAL_OFFSET = 2.5D;
    /** 临时落地水自救的最低坠落高度（格）：比这矮就不折腾了（摔不出伤害）。 */
    private static final float EMERGENCY_MIN_FALL_DISTANCE = 3.0F;
    /** 临时落地水自救的超时（刻）：2 秒还没收场就强制结束（正常一两刻就完事）。 */
    private static final int EMERGENCY_TIMEOUT_TICKS = 40;
    /**
     * <b>ultrawarm 维度（下界）的"落地水替代品"：末影珍珠</b>（用户指定）。
     * 那里水一倒出来就蒸发，所以改成"将要落地时向下丢一颗末影珍珠，把自己传送到地面，
     * 只吃末影珍珠那一下摔落伤害"。
     */
    private static final float PEARL_SPEED = 4.0F;
    /**
     * ultrawarm 维度里提前多少刻就改丢珍珠。
     * <p>比落水那条（{@value #MACE_WATER_LOOKAHEAD_TICKS} 刻）宽得多：水是"移动之前铺好"所以 1 刻就够，
     * 珍珠却要自己飞下去砸地 —— 而且它这一 tick 刚生成、要到<b>下一 tick</b>才第一次 tick
     * （{@code Level#addFreshEntity} 在实体遍历过程中只入队），所以至少要留 2~3 刻，
     * 才能保证"珍珠先落地、把人传送走"，而不是人先摔在地上。
     */
    private static final int PEARL_LOOKAHEAD_TICKS = 3;
    /** ultrawarm 维度里"离地多低就必须丢珍珠"的余量（格）：同理要比落水那条宽。 */
    private static final double PEARL_HEIGHT_MARGIN = 2.0D;
    /**
     * 末影珍珠那一下的摔落伤害（点）：<b>与原版给玩家结算的完全相同</b>
     * （{@code ThrownEnderpearl#onHit} 玩家分支里写死的 {@code 5.0F}）。
     * <p>为什么要我们自己补：原版那条"主人不是玩家"的分支只把主人传送过去 + 清零坠落距离，
     * <b>不结算伤害</b> —— 空壳正好走的是那一条。
     */
    private static final float ENDER_PEARL_FALL_DAMAGE = 5.0F;
    /** 末影珍珠阶段的兜底时长（刻）：珍珠迟迟没能把人传送走时，也别一直等下去。 */
    private static final int PEARL_SETTLE_TIMEOUT_TICKS = 20;
    /** 俯冲阶段的超时（刻）：6 秒还没进到 10 格就放弃这次俯冲（正常几秒内必然进）。 */
    private static final int DIVE_TIMEOUT_TICKS = 120;
    /** "卡住"检查的间隔（刻）。 */
    private static final int STUCK_CHECK_TICKS = 20;
    /** 一个检查周期内至少要涨/降这么多格，否则算被卡住（爬升/俯冲各自的判定）。 */
    private static final double STUCK_MIN_GAIN = 2.0D;
    /** 整套动作的总时长上限（刻）：30 秒，超时强制收场，免得卡在半空。 */
    private static final int TOTAL_TIMEOUT_TICKS = 600;
    /**
     * 重锤<b>失败之后的"滞空重来"等待刻数</b>（用户指定 10gt）：
     * 失败那一刻起算，只要还没落地就把这一套动作<b>就地重置</b>成全新的一整套
     * （见 {@link #tickFailureRestart}），而不是等它落地之后再发动。
     */
    private static final int FAILURE_RETRY_TICKS = 10;
    /** {@link State#failedTick} 的取值：还没失败。 */
    private static final int FAILURE_NONE = -1;
    /** {@link State#failedTick} 的取值：失败过，但这次"重来一套"的机会已经用掉。 */
    private static final int FAILURE_SPENT = -2;
    /**
     * "失败后重来一套"时，爬升期<b>不判视线</b>的宽限刻数（3 秒）。
     * <p>
     * 失败那一刻空壳正掉在目标下方，视线常常被岩壁/平台挡着；而重来一套的目的就是飞到
     * 目标上方 {@value #MAX_CLIMB_BLOCKS} 格再砸 —— 从高处看下去，视线一般自然就通了。
     * 所以给它 3 秒往上冲的时间，别刚起手就被"目标失去视线"收场。
     * 宽限期一过仍然看不见，就照原来的规则收场（不会再无限重来：本套动作收场后这本账一起作废）。
     */
    private static final int RESTART_LOS_GRACE_TICKS = 60;
    /**
     * 同一套动作里最多"失败后重来"几次（用户指定的是"立刻发动下一次"，但总得收得住）：
     * 连续重来这么多次还是砸不中（目标在天上乱窜、或者被地形挡着）就收手，
     * 交回给落水自救与正常抽签 —— 否则空壳会一直在天上重来、永远不落地。
     */
    private static final int MAX_FAILURE_RESTARTS = 3;
    /** 落点预测最多向前模拟多少刻。 */
    private static final int LANDING_SIM_MAX_TICKS = 200;

    /** 动作阶段。 */
    enum Phase {
        /** 穿鞘翅、举着烟花往上冲。 */
        CLIMB,
        /** 低头俯冲，边冲边补烟花。 */
        DIVE,
        /** 已经换成重锤，等着进 2 格砸下去。 */
        SMASH,
        /** 没砸中，改用落水自救。 */
        WATER,
        /** ultrawarm 维度（下界）：没有水可用，改用"向下丢末影珍珠传送落地"。 */
        PEARL,
    }

    /** 一套重锤动作的全部状态（挂在空壳身上，见 {@code PlayerShellEntity#possessionMaceState}）。 */
    static final class State {
        Phase phase = Phase.CLIMB;
        /** 本阶段经过的刻数。 */
        int phaseTicks;
        /** 整套动作经过的刻数（超时兜底）。 */
        int totalTicks;
        /** 起跳高度与本次允许爬到的最高点。 */
        double startY;
        double maxY;
        /** 本次动作实际到达过的最高点（俯冲高度 = 它 - 砸中时的高度，见 {@link #smash}）。 */
        double apexY;
        /** 上一刻的位置：砸击判定用"本刻位移线段"扫过目标，而不是只比"当前距离"（见 {@link #sweepDistance}）。 */
        double prevX;
        double prevY;
        double prevZ;
        /**
         * "卡住"判定用的参考点：{@link #progressRefTick} 刻时的 Y。
         * <p>爬升/俯冲一旦被地形真正卡住（贴岩壁、卡斜坡、顶在方块上），高度就不再变化；
         * 那时继续放烟花毫无意义，只会"卡在地里一直使用烟花"（实测反馈），要尽快放弃这次攻击。
         */
        int progressRefTick;
        double progressRefY;
        /**
         * 这一次是不是"临时落地水自救"（{@link #tryStartEmergencyWaterSave}）而不是重锤那一套。
         * <p>区别只在收场：临时自救要把主/副手的东西<b>还回去</b>（它只是借主手放个水），
         * 而重锤那套是空手收场（鞘翅/烟花/胸甲/重锤全清）。
         */
        boolean emergencySave;
        /** 临时自救开始时手里的东西（收场时还回去）。 */
        ItemStack savedMain = ItemStack.EMPTY;
        ItemStack savedOff = ItemStack.EMPTY;
        /** 本次俯冲固定用的俯角（60~90 之间随机一次）。 */
        float divePitch;
        /** 当前推进烟花：实体 UUID 与它被放下的时刻。 */
        @Nullable
        UUID rocketUuid;
        int rocketTick;
        /**
         * 这一套是什么时候被判定为<b>失败</b>的（{@code shell.tickCount} 口径）：
         * {@link #FAILURE_NONE} = 还没失败，{@link #FAILURE_SPENT} = 失败过但补招机会已用掉，
         * 非负 = 失败那一刻的刻数（见 {@link #tickFailureRestart}）。
         * <p>记在 {@link State} 里而不是空壳身上：这样"失败"只跟着这一套动作走 ——
         * 动作一旦收场（落地/卡住/超时），这本账自然一起作废。
         */
        int failedTick = FAILURE_NONE;
        /**
         * 视线判定的宽限截止刻（{@code shell.tickCount} 口径；-1 = 没有宽限）。
         * <p>
         * 只在 {@link #restart}（失败后重来一套）时设置：那一段爬升期不判"目标失去视线"
         * （见 {@link #RESTART_LOS_GRACE_TICKS}）。
         */
        int losGraceUntilTick = -1;
        /** 这一套动作里已经"失败后重来"过几次（上限见 {@link #MAX_FAILURE_RESTARTS}）。 */
        int failureRestarts;
        /** 落水自救放下的水源位置（收水时用）。 */
        @Nullable
        BlockPos waterPos;
        /** 手上是否已经是空桶（放了水之后）。 */
        boolean bucketEmptied;
        /** ultrawarm 维度：末影珍珠是不是已经丢出去了（每个阶段只丢一颗）。 */
        boolean pearlThrown;
        /** ultrawarm 维度：末影珍珠那一下摔落伤害是不是已经结算过（只结算一次）。 */
        boolean pearlDamageApplied;
        /**
         * <b>暮色九头蛇专用</b>：这一套重锤锁定的是哪个<b>头</b>（用户指定：锁头而不是身体）。
         * <p>
         * 存成 {@code Entity} 而不是 {@code HydraHead}：本类不是联动类，不能把暮色类型写进常量池。
         * 挑法/沿用/失效重挑见 {@link #aimBodyOf}。
         */
        @Nullable
        net.minecraft.world.entity.Entity lockedHead;
    }

    private OrbPossessionMaceAttack() {
    }

    /**
     * 抽中"重锤"这张签：登记状态并做准备工作。
     * <p>
     * 头顶空间不够就直接放弃（<b>连装备都不换</b>），空壳照常过它自己的日子。
     */
    static void begin(PlayerShellEntity shell, LivingEntity target) {
        if (!(shell.level() instanceof ServerLevel level) || shell.isFlightMode()) {
            return;
        }
        if (shell.isMaceSequenceActive()) {
            return;
        }
        State state = new State();
        state.startY = shell.getY();
        state.apexY = state.startY;
        state.prevX = shell.getX();
        state.prevY = shell.getY();
        state.prevZ = shell.getZ();
        state.progressRefY = state.startY;
        state.progressRefTick = 0;
        // 最高点 = <b>目标高度 + 50 格</b>（不再是"起手高度 + 50"）；仍然受天花板限制（离天花板 5 格）。
        double climbTop = target.getY() + MAX_CLIMB_BLOCKS;
        double ceilingY = findCeilingY(level, shell, climbTop);
        state.maxY = Math.min(climbTop, ceilingY - CEILING_MARGIN);
        if (state.maxY - state.startY < MIN_HEADROOM) {
            // 空间太低（贴着天花板/在低矮洞穴里）：取消这次重锤，不留任何痕迹。
            // 日志做节流：debug 模式下"每次近战签都抽重锤"，这行会被反复触发，不能每 1~2 秒刷一条。
            if (mayLogLowSpace(shell)) {
                cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
                    "[重锤] 头顶空间不足（可用 {} 格 < {} 格），取消这次重锤",
                    String.format("%.1f", state.maxY - state.startY), MIN_HEADROOM);
            }
            return;
        }
        // 目标本身就在"这次能爬到的最高点"之上：这套动作根本不可能够到它。
        // 不取消的话，俯冲阶段会一直朝目标（向上）飞、烟花一路把它顶在天上，整套动作挂到超时才收场
        // （实测反馈：目标比最高点高时，空壳会一直滑翔停不下来）。这里直接放弃这次攻击。
        if (target.getY() > state.maxY) {
            if (mayLogLowSpace(shell)) {
                cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
                    "[重锤] 目标（y={}）高于本次可爬到的最高点（y={}），取消这次重锤",
                    String.format("%.1f", target.getY()), String.format("%.1f", state.maxY));
            }
            return;
        }
        state.divePitch = DIVE_PITCH_MIN
            + shell.getRandom().nextFloat() * (DIVE_PITCH_MAX - DIVE_PITCH_MIN);

        // 装备：胸甲栏鞘翅、主手烟花、副手钻石胸甲
        shell.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        shell.setItemInHand(InteractionHand.MAIN_HAND, rocketStack());
        shell.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND_CHESTPLATE));
        // 起跳：先离地（滑翔要求"不在方块上"），升力交给烟花。
        // "失败后重来一套"走的也是这条路（见 {@link #restart}）：那一刻人往往还在空中，
        // 这一脚升力正好把"重新冲上去"的观感做出来 —— 用户要的就是"再飞上去，来一整套完整的流程"。
        shell.setSharedFlag(7, true);
        shell.setDeltaMovement(0.0D, 0.42D, 0.0D);
        shell.hasImpulse = true;
        shell.setMaceState(state);
        // <b>暮色九头蛇：这一整套要砸的是它的一个头，不是身体</b>（用户指定）——
        // 在这里就锁好（日志也早一条，方便排查）；中途那个头被打死会另挑一个，见 {@link #aimBodyOf}。
        aimBodyOf(shell, state, target);
        // 穿鞘翅的动静：用"装备鞘翅"那条短音效。<b>不要</b>拿 {@code ELYTRA_FLYING} 当一次性音效 ——
        // 那是一条循环用的长音频（滑翔时的持续风声），一次性播出来会变成一个又长又响、
        // 还钉在起跳点不动的风声（实测反馈：听起来像在玩家耳边响，而不是从空壳那里来）。
        // 持续的风声现在由客户端 {@code ShellElytraSoundInstance} 跟着空壳放（位置随它走）。
        level.playSound(null, shell.getX(), shell.getY(), shell.getZ(),
            SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.HOSTILE, 1.0F, 1.2F);
    }

    /**
     * <b>临时落地水自救</b>：<b>与重锤无关</b>——只要这具空壳正从高处摔向地面，
     * 哪怕手里拿的是弓/西瓜刀/什么都没拿，也会抢在撞地前把水铺在落点、落地再收回来。
     * <p>由 {@link PlayerShellEntity#tick()} 在<b>没有重锤动作时</b>每刻调用；
     * 起手后走的是同一套状态机（{@code Phase.WATER}），只是收场时<b>把手里的东西还回去</b>而不是清空。
     *
     * <h2>触发条件（都满足才起手）</h2>
     * <ul>
     *   <li>不在滑翔（有鞘翅就直接滑下去了，不需要水）、不在骑乘、不在水里、没落地；</li>
     *   <li><b>坠落 ≥ {@value #EMERGENCY_MIN_FALL_DISTANCE} 格</b>（小跳一下不值得掏水桶），且确实在下坠；</li>
     *   <li>预测 {@value #WATER_LOOKAHEAD_TICKS} 刻内会撞地（离地 ≤ 下落速度 + {@value #WATER_HEIGHT_MARGIN} 格），
     *       并且脚下确实有能放水的位置。</li>
     * </ul>
     * 每刻的判定先过前几条便宜的短路条件，走到落点模拟的次数很少，不会拖性能。
     */
    static void tryStartEmergencyWaterSave(PlayerShellEntity shell) {
        if (!(shell.level() instanceof ServerLevel level)) {
            return;
        }
        if (shell.onGround() || shell.isFallFlying() || shell.isPassenger() || shell.isInWater()) {
            return;
        }
        if (shell.fallDistance < EMERGENCY_MIN_FALL_DISTANCE) {
            return;
        }
        if (shell.getDeltaMovement().y > -0.5D) {
            return; // 没在下坠（被什么顶着/正在上升）
        }
        // ultrawarm 维度（下界）用不了水，改成丢末影珍珠（用户指定）—— 所以那条"必须有放水格"的
        // 检查也跳过（它只对水有意义），提前量同样放宽到珍珠那套。
        boolean nether = waterSaveImpossible(level);
        Landing landing = predictLanding(shell, level);
        if (landing == null || landing.pos() == null
            || landing.ticks() > (nether ? PEARL_LOOKAHEAD_TICKS : WATER_LOOKAHEAD_TICKS)) {
            mayLogWaterSaveBail(shell, "落点预测还太远或不可用", landing);
            return;
        }
        if (!nether && findWaterSpot(shell, level) == null) {
            mayLogWaterSaveBail(shell, "附近找不到能放水的格子", landing);
            return;
        }
        State state = new State();
        state.emergencySave = true;
        state.savedMain = shell.getMainHandItem().copy();
        state.savedOff = shell.getOffhandItem().copy();
        state.startY = shell.getY();
        state.apexY = shell.getY();
        state.prevX = shell.getX();
        state.prevY = shell.getY();
        state.prevZ = shell.getZ();
        shell.setMaceState(state);
        enterLandingPhase(shell, level, state);
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
            nether
                ? "[落地保护] 空壳即将撞地（已坠落 {} 格，{} 刻内落地），下界用不了水：丢末影珍珠传送落地"
                : "[落水自救] 空壳即将撞地（已坠落 {} 格，{} 刻内落地），临时掏水桶",
            String.format("%.1f", shell.fallDistance), landing.ticks());
    }

    /** 每刻推进（由空壳的 tick 调，见 {@link PlayerShellEntity#tick()}）。 */
    static void tick(PlayerShellEntity shell) {
        State state = shell.getMaceState();
        if (state == null) {
            return;
        }
        if (!(shell.level() instanceof ServerLevel level)) {
            shell.setMaceState(null);
            return;
        }
        LivingEntity target = shell.getTarget();
        state.totalTicks++;
        state.phaseTicks++;        // 记录本次实际到过的最高点：俯冲高度（= 最高点 - 砸中时的高度）决定重锤伤害，见 smash()
        state.apexY = Math.max(state.apexY, shell.getY());
        if (state.totalTicks > (state.emergencySave ? EMERGENCY_TIMEOUT_TICKS : TOTAL_TIMEOUT_TICKS)) {
            finish(shell, "超时");
            return;
        }
        // 目标没了/死了：收场（把装备与滑翔状态清干净）。
        // 临时落地水自救不需要目标，不参与这一条。
        if (!state.emergencySave && (target == null || !target.isAlive())) {
            finish(shell, "目标消失");
            return;
        }
        // 目标<b>看不见了</b>（躲进掩体/隔了堵墙）：也不用再打了，直接收场。
        // 索敌那边每 10 刻才重扫一次、且"看不见"要 20 刻才生效，所以这里只看<b>此刻</b>的视线
        // （与索敌走的是同一个入口 {@code PlayerShellEntity#shouldSeePossessionTarget}：
        // 从空壳眼位朝目标整个身体打一片扇形射线，任意一条通就算看得见），
        // 爬升期一旦没视线就放弃 —— 不收的话它会先顶着烟花冲到目标头上 50 格、
        // 再一头俯冲下去砸空气，白烧一堆烟花。
        // 俯冲/砸落阶段不查：那时距离已经很近，俯冲末速好几格每刻、摔落伤害也记下了，
        // 中途放弃反而变成"跳下去却不砸"，比砸空更糟；俯冲本身有超时与卡住两道保险。
        if (!state.emergencySave && state.phase == Phase.CLIMB && target != null
            && shell.tickCount >= state.losGraceUntilTick
            && !shell.shouldSeePossessionTarget(target)) {
            finish(shell, "目标失去视线");
            return;
        }
        // <b>失败判定 + 滞空重来一整套</b>（用户指定，判据见 {@link #tickFailureRestart}）：
        // 自身高度已经降到目标高度以下而重锤没砸出去 → 再滞空 10 刻还没落地，就把这一套
        // 就地重置成"刚起手"（换鞘翅 → 重新飞到目标上方 50 格 → 俯冲 → 砸），
        // <b>而不是落地之后再发动</b>。
        if (tickFailureRestart(shell, level, state, target)) {
            return;
        }
        switch (state.phase) {
            case CLIMB -> tickClimb(shell, level, state, target);
            case DIVE -> tickDive(shell, level, state, target);
            case SMASH -> tickSmash(shell, level, state, target);
            case WATER -> tickWater(shell, level, state);
            case PEARL -> tickPearl(shell, level, state, target);
        }
        // 记下本刻结束时的位置：下一 tick 的砸击判定要用它组成"本刻位移线段"
        state.prevX = shell.getX();
        state.prevY = shell.getY();
        state.prevZ = shell.getZ();
    }

    /**
     * <b>这一锤该砸谁</b>：普通目标就是它自己；<b>暮色九头蛇则锁定它的一个头</b>（用户指定）。
     *
     * <h2>为什么非要锁头</h2>
     * 九头蛇的身体<b>整段免疫伤害</b>（{@code Hydra#hurt} 只放行"无视无敌"的伤害类型，
     * 连 {@code isPickable()} 都是 false），能吃伤害的只有头/脖子那些 {@code HydraPart} 部件
     * （部件的 {@code hurt} 会把伤害转给 {@code Hydra#attackEntityFromPart}）。
     * 而重锤的伤害是打在目标<b>本体</b>上的（{@link #smash} 里那一句 {@code victim.hurt}），
     * 砸身体等于这一整套飞上天、俯冲、砸落全都白做（实测：命中=false、没有伤害、没有粒子）。
     * 所以目标若是九头蛇，这里就挑一个活着的头（优先嘴大张着的 —— 它正在攻击、位置最稳）
     * 当作"命中体"，<b>俯冲瞄准、砸击距离、伤害结算、粒子位置全部冲着那个头去</b>。
     *
     * <h2>锁一次就沿用</h2>
     * 头一旦被打死（{@code HydraHead#isActive()} 变 false）就当场另挑一个；其余情况一路沿用 ——
     * 每刻重挑的话，弹道会在几个头之间来回横跳，永远收敛不到一个点上。
     *
     * <p>头用 {@code Entity} 存取（不是 {@code HydraHead}），这样本类不会把暮色类型写进常量池；
     * 联动调用一律先问 {@link OptionalMods#isTwilightForestLoaded()}，没装暮色时连类都不会去加载。
     */
    private static net.minecraft.world.entity.Entity aimBodyOf(PlayerShellEntity shell, State state,
                                                               LivingEntity target) {
        if (!OptionalMods.isTwilightForestLoaded() || !TwilightForestCompat.isHydra(target)) {
            state.lockedHead = null;
            return target;
        }
        net.minecraft.world.entity.Entity locked = state.lockedHead;
        if (locked != null && !locked.isRemoved() && TwilightForestCompat.isHydraHeadOf(target, locked)) {
            return locked;   // 还活着：沿用同一个头
        }
        net.minecraft.world.entity.Entity head = TwilightForestCompat.pickHydraHead(target);
        state.lockedHead = head;
        if (head != null) {
            cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
                "[重锤] 目标是暮色九头蛇 → 这一套锁定它的一个头（身体免疫伤害，锁头才打得到）：头位置 {}",
                new Vec3(head.getX(), head.getY(), head.getZ()));
        }
        return head != null ? head : target;
    }

    /**
     * 命中体的"身体中心"：俯冲瞄准与"本刻位移线段"判定都用它。
     * <p>
     * 普通目标沿用原来的算法（脚底 + 半个身高，行为一个字节都没变）；
     * <b>头部件</b>（不是 {@code LivingEntity}）用它自己碰撞箱的中心 —— 脖子伸来伸去，
     * 头的位置跟"本体脚底 + 半个身高"完全不是一回事。
     */
    private static Vec3 aimCenter(net.minecraft.world.entity.Entity body) {
        return body instanceof LivingEntity living
            ? living.position().add(0.0D, living.getBbHeight() * 0.5D, 0.0D)
            : body.getBoundingBox().getCenter();
    }

    /**
     * 目标身体中心到"空壳本刻位移线段"（上一刻位置 → 当前位置）的距离。
     * <p>俯冲末速好几格每刻，只比"当前距离 ≤ 2 格"的话，很容易在一刻之间<b>从 2 格外直接越过目标</b>
     * 而永远判不中（实测反馈："完全无法命中"）。用线段距离就等价于"这一帧有没有扫过它"。
     */
    private static double sweepDistance(PlayerShellEntity shell, State state, LivingEntity target) {
        Vec3 a = new Vec3(state.prevX, state.prevY, state.prevZ);
        Vec3 b = shell.position();
        Vec3 p = aimCenter(aimBodyOf(shell, state, target));
        Vec3 ab = b.subtract(a);
        double lenSq = ab.lengthSqr();
        double t = lenSq < 1.0E-6D ? 0.0D : Mth.clamp(p.subtract(a).dot(ab) / lenSq, 0.0D, 1.0D);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    /** 这一锤够不够得着（当前距离或本刻扫过的线段进 2 格）；九头蛇按"锁定的那个头"算距离。 */
    private static boolean inSmashReach(PlayerShellEntity shell, State state, LivingEntity target) {
        return shell.distanceTo(aimBodyOf(shell, state, target)) <= MACE_HIT_DISTANCE
            || sweepDistance(shell, state, target) <= MACE_HIT_DISTANCE;
    }

    /** 抬头朝上，用 1 秒一颗的烟花一路顶到 {@link State#maxY}（= 目标高度 + 50，且不贴天花板）。 */
    private static void tickClimb(PlayerShellEntity shell, ServerLevel level, State state, LivingEntity target) {
        // 还没进入滑翔（起跳那一两刻就是这样：原版 updateFallFlying 要求"已经离地"才肯保留标记）
        // → 主动补标记、并再给一脚升力；两秒还飞不起来才认输收场。
        if (!shell.isFallFlying()) {
            shell.setSharedFlag(7, true);
            if (shell.onGround()) {
                shell.setDeltaMovement(shell.getDeltaMovement().add(0.0D, 0.42D, 0.0D));
                shell.hasImpulse = true;
            }
            if (state.phaseTicks > 40) {
                finish(shell, "无法起飞");
                return;
            }
        }
        // 最高点跟着目标走：目标在爬升途中升高/降低，这里每 10 刻重算一次
        // （否则爬到一半目标飞上去，又会出现"够不到"的老问题）。天花板扫描不便宜，别每刻做。
        if (state.phaseTicks % 10 == 0) {
            double climbTop = target.getY() + MAX_CLIMB_BLOCKS;
            double ceilingY = findCeilingY(level, shell, climbTop);
            state.maxY = Math.min(climbTop, ceilingY - CEILING_MARGIN);
        }
        // 抬头朝上（烟花推力的方向 = 视线方向，见 FireworkRocketEntity#tick）
        shell.setXRot(-85.0F);
        shell.setYHeadRot(shell.getYRot());
        // <b>卡住判定</b>：一秒内高度一点没涨（贴岩壁/卡斜坡/顶在方块上）就放弃这次爬升 ——
        // 再放烟花也只是"卡在地里一直使用烟花"（实测反馈）。放弃后空壳会重新抽签、发动新一轮攻击。
        if (state.phaseTicks - state.progressRefTick >= STUCK_CHECK_TICKS) {
            if (shell.getY() - state.progressRefY < STUCK_MIN_GAIN) {
                finish(shell, "爬升受阻(卡住)");
                return;
            }
            state.progressRefY = shell.getY();
            state.progressRefTick = state.phaseTicks;
        }
        // 上一颗推进满 1 秒就收掉，然后立刻补下一颗 —— 一颗接一颗，全程都有推力
        retireRocketIfExpired(shell, level, state);
        if (state.rocketUuid == null) {
            launchRocket(shell, level, state);
        }
        if (shell.getY() >= state.maxY - 1.0D) {
            state.phase = Phase.DIVE;
            state.phaseTicks = 0;
            state.progressRefTick = 0;
            state.progressRefY = shell.getY();
        }
    }

    /**
     * 俯冲：低头、偏航对准目标、每 10 刻补一颗烟花；到 10 格换重锤。
     *
     * <h2>为什么只对偏航、俯角自己定</h2>
     * 烟花推力方向 = 实体<b>视线方向</b>（见 {@code FireworkRocketEntity#tick}）。
     * 之前这里用 {@code lookAt(target)} 连俯角一起对准目标 —— 目标一旦在<b>上方</b>，
     * 俯角就变成"抬头往上看"，烟花于是把空壳一路往天上顶：永远俯冲不下来、整套动作挂到超时
     * （实测反馈："目标高于最高点时会一直滑翔停不下来"）。现在俯角固定成 {@link State#divePitch}
     * （60~90 的<b>向下</b>俯角），只把水平朝向对准目标 —— 俯冲才是真的往下冲。
     */
    private static void tickDive(PlayerShellEntity shell, ServerLevel level, State state, LivingEntity target) {
        // ① 够到 10 格就换重锤（这一步永远最先判：说不定这一 tick 就能砸）
        //    九头蛇按"锁定的那个头"算距离（身体那么大一坨，用身体算会提前十几格就换锤）
        if (shell.distanceTo(aimBodyOf(shell, state, target)) <= MACE_EQUIP_DISTANCE) {
            equipMace(shell, level);
            state.phase = Phase.SMASH;
            state.phaseTicks = 0;
            return;
        }
        // ② 马上要撞地：转"落地保护"。能放水的维度提前量只留 1 刻 / 半格 —— <b>把最后一刻先让给砸击</b>
        //    （用户反馈：以前提前量太大，空壳总是先转落地水、砸不出来）。
        //    水是在移动之前铺好的（见 preMoveStep），所以 1 刻也来得及；
        //    ultrawarm 维度（下界）改丢末影珍珠，那玩意儿得自己飞下去砸地，提前量要宽一些。
        if (aboutToNeedLandingHelp(shell, level)) {
            enterLandingPhase(shell, level, state);
            return;
        }
        // ③ 已经落地/被地形卡住：这次俯冲没戏了，立刻收场 ——
        //    绝不能继续放烟花（实测反馈："低于目标时会卡在地里一直使用烟花"）。
        //    收场后空壳会重新抽签、发动新一轮攻击（不一定还是重锤）。
        if (shell.onGround()) {
            finish(shell, "俯冲落地(够不到)");
            return;
        }
        if (state.phaseTicks - state.progressRefTick >= STUCK_CHECK_TICKS * 2) {
            if (state.progressRefY - shell.getY() < STUCK_MIN_GAIN) {
                finish(shell, "俯冲受阻(卡住)");
                return;
            }
            state.progressRefY = shell.getY();
            state.progressRefTick = state.phaseTicks;
        }
        if (!shell.isFallFlying()) {
            // 同爬升：滑翔标记偶尔会被原版逻辑清掉（刚离地那一刻最常见），补回来就是
            shell.setSharedFlag(7, true);
        }
        aimDive(shell, state, target);
        if (state.phaseTicks % DIVE_ROCKET_INTERVAL == 0) {
            launchRocket(shell, level, state);
        }
        // 俯冲阶段的超时兜底：正常情况下几秒内就该进 10 格。
        if (state.phaseTicks > DIVE_TIMEOUT_TICKS) {
            finish(shell, "俯冲超时(够不到)");
        }
    }

    /**
     * <b>俯冲朝向</b>：水平朝向对准目标；俯角取"指向目标"的那个角度，并夹在
     * {@value #DIVE_PITCH_MIN}~{@value #DIVE_PITCH_MAX}（都是向下的俯角）之间。
     *
     * <h2>为什么不能只用一个固定俯角</h2>
     * 固定俯角 = 轨迹是一条固定斜率的直线：只要目标不在正下方，空壳就会在够到
     * {@value #MACE_EQUIP_DISTANCE} 格之前先撞地，于是每次都转成落地水、永远砸不出去
     * （实测反馈："更倾向于落地水而不是重锤砸击"）。
     * 改成"每刻重新指向目标"，轨迹就是一条收敛到目标的俯冲弹道 —— 换重锤那一步自然能触发。
     * 夹在 60~90 之间则保证它<b>始终是向下俯冲</b>（既满足"俯角 60~90"的设定，
     * 又不会出现目标在上方时抬头被烟花顶上天的情况）。
     */
    private static void aimDive(PlayerShellEntity shell, State state, LivingEntity target) {
        // 命中体：普通目标是本体；九头蛇是锁定的那个头（见 aimBodyOf）
        Vec3 aim = aimCenter(aimBodyOf(shell, state, target));
        double dx = aim.x - shell.getX();
        double dz = aim.z - shell.getZ();
        double dy = aim.y - (shell.getY() + shell.getBbHeight() * 0.5D);
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal > 1.0E-4D) {
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
            shell.setYRot(yaw);
            shell.yRotO = yaw;
            shell.setYHeadRot(yaw);
            shell.yHeadRot = yaw;
            shell.yHeadRotO = yaw;
        }
        // 指向目标的俯角（正数 = 低头向下），再夹到 60~90
        float towardTarget = (float) (-Mth.atan2(dy, Math.max(horizontal, 1.0E-4D)) * (180.0D / Math.PI));
        state.divePitch = Mth.clamp(towardTarget, DIVE_PITCH_MIN, DIVE_PITCH_MAX);
        shell.setXRot(state.divePitch);
    }

    /** 只把水平朝向（偏航）对准目标，俯角沿用上一次算好的俯冲角（砸落阶段用）。 */
    private static void faceYawOnly(PlayerShellEntity shell, State state, LivingEntity target) {
        // 同上：九头蛇瞄的是锁定的那个头
        Vec3 aim = aimCenter(aimBodyOf(shell, state, target));
        double dx = aim.x - shell.getX();
        double dz = aim.z - shell.getZ();
        if (dx * dx + dz * dz > 1.0E-4D) {
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
            shell.setYRot(yaw);
            shell.yRotO = yaw;
            shell.setYHeadRot(yaw);
            shell.yHeadRot = yaw;
            shell.yHeadRotO = yaw;
        }
        shell.setXRot(state.divePitch);
    }

    /** 拿锤子砸：进 2 格就出手（零帧起手）；到离地半格还没砸中就走落水自救。 */
    private static void tickSmash(PlayerShellEntity shell, ServerLevel level, State state, LivingEntity target) {
        // 砸落阶段同样只对偏航：俯角保持向下的俯冲角（见 tickDive 的注释）
        faceYawOnly(shell, state, target);
        if (inSmashReach(shell, state, target)) {
            smash(shell, level, state, target);
            finish(shell, "砸中");
            return;
        }
        // 落地保护的触发：用户要求"距地面半格处还没砸中"就放水。
        // 提前量只留 {@value #MACE_WATER_LOOKAHEAD_TICKS} 刻 / 半格，<b>砸击优先</b>：
        // 这个阶段每刻都先判"够不够得着砸"（含本刻位移线段），砸不到才轮到放水，
        // 于是两者不再互相抢（用户反馈：以前空壳更倾向于落地水而不是重锤）。
        // ultrawarm 维度（下界）没有水，改丢末影珍珠 —— 那条提前量宽一些（见 PEARL_LOOKAHEAD_TICKS）。
        if (aboutToNeedLandingHelp(shell, level)) {
            enterLandingPhase(shell, level, state);
        }
    }

    /**
     * 是不是"必须开始做落地保护了"：预测很快就要撞地，或者离地已经低到落水/丢珍珠的触发线。
     * <p>提前量分两套：能放水的维度只要 {@value #MACE_WATER_LOOKAHEAD_TICKS} 刻
     * （水在移动之前就铺好，1 刻足够）；<b>ultrawarm 维度</b>要 {@value #PEARL_LOOKAHEAD_TICKS} 刻 ——
     * 末影珍珠得自己飞下去砸地，而且它生成当刻不 tick，得赶在人落地之前把它送到地上。
     */
    private static boolean aboutToNeedLandingHelp(PlayerShellEntity shell, ServerLevel level) {
        boolean nether = waterSaveImpossible(level);
        int lookahead = nether ? PEARL_LOOKAHEAD_TICKS : MACE_WATER_LOOKAHEAD_TICKS;
        double margin = nether ? PEARL_HEIGHT_MARGIN : MACE_WATER_HEIGHT_MARGIN;
        Landing landing = predictLanding(shell, level);
        return (landing != null && landing.ticks() <= lookahead)
            || heightAboveGround(shell, level) <= waterHeightTrigger(shell, margin);
    }

    /**
     * 进入"落地保护"阶段：能放水的维度走 {@link Phase#WATER 落水自救}；
     * <b>ultrawarm 维度（下界）走 {@link Phase#PEARL 末影珍珠}</b>（用户指定，水在那里一倒就蒸发）
     * —— 珍珠当场就丢出去（它当刻不 tick，越早丢越稳）。
     */
    private static void enterLandingPhase(PlayerShellEntity shell, ServerLevel level, State state) {
        if (waterSaveImpossible(level)) {
            state.phase = Phase.PEARL;
            state.phaseTicks = 0;
            throwPearl(shell, level, state);
            return;
        }
        state.phase = Phase.WATER;
        state.phaseTicks = 0;
    }

    /**
     * 还要离地多低就必须进落水自救：至少留出"再落一 tick 的量 + {@code margin} 格"。
     * <p>下落速度一览无余地反映在 {@code vy} 上（俯冲时烟花还在加速），所以这条纯高度的兜底
     * 能对冲"实际比模拟更快"的偏差；{@code margin} 由调用方给：
     * 重锤的俯冲/砸落阶段只给 {@value #MACE_WATER_HEIGHT_MARGIN}（把机会先让给砸击），
     * 与重锤无关的临时落地水自救给 {@value #WATER_HEIGHT_MARGIN}（没有抢优先级的问题，多留点余量）。
     */
    private static double waterHeightTrigger(PlayerShellEntity shell, double margin) {
        double fallSpeed = Math.max(0.0D, -shell.getDeltaMovement().y);
        return Math.max(WATER_TRIGGER_HEIGHT, fallSpeed + margin);
    }

    /**
     * 找一个能放水的格子（按优先级）：
     * <ol>
     *   <li>按速度预测的落点 —— 但<b>水平偏移不能超过 {@link #WATER_MAX_HORIZONTAL_OFFSET}</b>
     *       （否则就是"隔空放水"：水飘在离空壳老远的地方）；</li>
     *   <li>空壳<b>正下方</b>那一格（从脚下往下一路找到"可替换且下面是实心"的最高一格）——
     *       这就是玩家落水自救的标准做法，位置一定贴近、也不悬空；</li>
     *   <li>空壳当前所在的格子（已经贴地/已经落地时就是这里）。</li>
     * </ol>
     * 每个候选都要求：<b>方块下方是实心</b>（不悬空）+ 本身可替换。
     * 全部不行就返回 null（调用方会重试并最终认输收场）。
     */
    @Nullable
    private static BlockPos findWaterSpot(PlayerShellEntity shell, ServerLevel level) {
        List<BlockPos> candidates = new ArrayList<>(3);
        Landing landing = predictLanding(shell, level);
        if (landing != null && landing.pos() != null
            && Math.hypot(landing.pos().getX() + 0.5D - shell.getX(),
                landing.pos().getZ() + 0.5D - shell.getZ()) <= WATER_MAX_HORIZONTAL_OFFSET) {
            candidates.add(landing.pos());
        }
        // 正下方：从脚下往下扫，取"可替换 + 下面是实心"的最高一格
        BlockPos from = shell.blockPosition();
        BlockPos down = null;
        for (int y = from.getY(); y >= level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(from.getX(), y, from.getZ());
            if (!level.isLoaded(pos)) {
                break;
            }
            if (level.getBlockState(pos).canBeReplaced()
                && !level.getBlockState(pos.below()).canBeReplaced()) {
                down = pos;
                break;
            }
            if (!level.getBlockState(pos).canBeReplaced()
                && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                break; // 撞到实体方块（脚下就是地面），再往下没必要找了
            }
        }
        if (down != null) {
            candidates.add(down);
        }
        candidates.add(from);
        for (BlockPos pos : candidates) {
            if (pos.getY() >= level.getMaxBuildHeight() || !level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockState(pos).canBeReplaced()
                && !level.getBlockState(pos.below()).canBeReplaced()) {
                return pos;
            }
        }
        return null;
    }

    /**
     * 砸击粒子事件的强度参数：<b>与 {@code MaceItem#knockback} 里的 750 完全一致</b>。
     * <p>客户端 {@code ParticleUtils#spawnSmashAttackParticles} 会生成 {@code power/3 = 250} 颗中心尘柱
     * 与 {@code power/1.5 = 500} 颗、半径 3.5 格的圆环尘柱（{@code ParticleTypes.DUST_PILLAR}）。
     */
    private static final int SMASH_ATTACK_EVENT_POWER = 750;

    /**
     * 一锤子砸下去（零帧起手）。
     *
     * <h2>伤害怎么算（原版公式，参考 wiki / {@code MaceItem}）</h2>
     * 最终伤害 = 属性攻击力（装备槽里的重锤自带 +5）→ 附魔（锋利之类）→
     * <b>+ 重锤的坠落加成</b>：
     * <pre>
     *   坠落 d 格：d ≤ 3 → 4d；3 &lt; d ≤ 8 → 12 + 2(d-3)；d &gt; 8 → 22 + (d-8)
     *   再加重锤的"致密（Density）"附魔：每级每格 +0.5，即致密 V = 2.5 × d
     * </pre>
     * 这一段是 {@code MaceItem#getAttackDamageBonus} 自己算的 —— 所以这里直接调它，不另抄一份。
     *
     * <h2>为什么要先"把坠落距离写成俯冲高度"</h2>
     * 原版重锤的加成看的是攻击者当前的 {@code fallDistance}。而空壳在滑翔期间
     * 坠落距离会被原版那套 {@code checkSlowFallDistance()} 一直清零，只有"脱掉鞘翅之后"
     * 真正自由落体的那一两格才算数 —— 于是同一个招式，伤害全看"离目标还有多远时砸中"，
     * 忽高忽低（实测反馈）。这里改成：出手前先把 {@code fallDistance} 设成
     * <b>本次俯冲高度</b>（最高点 - 当前高度，见 {@link State#apexY}），
     * 再走原版那条公式 —— 于是"从多高砸下来"就决定了伤害，同一套动作伤害稳定。
     *
     * <p>砸中之后照原版 {@code MaceItem#postHurtEnemy} 把坠落距离清零：这一锤已经结算过了，
     * 落地不该再吃一次摔落伤害（没砸中的话则由落水自救负责，见 {@link #tickWater}）。
     */
    private static void smash(PlayerShellEntity shell, ServerLevel level, State state, LivingEntity target) {
        // 本次俯冲高度写进 fallDistance：原版公式随后按它算重锤加成
        float diveHeight = (float) Math.max(0.0D, state.apexY - shell.getY());
        shell.fallDistance = diveHeight;

        // <b>这一锤实际砸在哪个实体上</b>：普通目标是它自己；暮色九头蛇是它的一个头
        // （身体整段免疫伤害，砸身体就是白砸 —— 见 aimBodyOf）。
        // 头的 {@code hurt} 会把伤害转给 {@code Hydra#attackEntityFromPart}，所以伤害、音效、粒子
        // 都按"打中了一个头"来走。
        net.minecraft.world.entity.Entity victim = aimBodyOf(shell, state, target);

        ItemStack mace = shell.getMainHandItem();
        var source = level.damageSources().mobAttack(shell);
        float damage = (float) shell.getAttributeValue(
            net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        damage = net.minecraft.world.item.enchantment.EnchantmentHelper
            .modifyDamage(level, mace, victim, source, damage);
        damage += mace.getItem().getAttackDamageBonus(victim, damage, source);
        shell.swing(InteractionHand.MAIN_HAND);
        float healthBefore = target.getHealth();
        boolean hit = victim.hurt(source, damage);
        // <b>诊断日志</b>：写在"出手方"这一侧，不依赖受害者的伤害事件。
        // 必须这样做的原因见 {@code Player#hurt}：玩家处于创造/旁观（{@code abilities.invulnerable}）时
        // 会在<b>创建 DamageContainer 之前</b>直接 return false，于是整个
        // {@code LivingIncomingDamageEvent} / {@code LivingDamageEvent} 链条根本不会触发 ——
        // 对"致命打击计数器从不生效"这类问题，只看受害者侧的事件日志会永远看不到任何线索。
        // hit=true 但血量没变，基本就是"伤害被某处清零了"（例如同化把致命伤害抹掉）。
        if (target instanceof net.minecraft.world.entity.player.Player) {
            String mode = target instanceof net.minecraft.server.level.ServerPlayer sp
                ? sp.gameMode.getGameModeForPlayer().getName()
                : "?";
            cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
                "[同化] 重锤打玩家: 目标={} 原始伤害={} 命中={} 无敌={} 模式={} 血量={} -> {}",
                target.getName().getString(), damage, hit, target.isInvulnerable(), mode,
                healthBefore, target.getHealth());
        }
        if (hit) {
            // 原版 MaceItem#hurtEnemy：记下"冲击点"，这一锤<b>落地时不再承受冲击点以上的那段坠落</b>
            // （见 PlayerShellEntity#causeFallDamage 里的同款实现 —— 不然砸完还会吃一次满额摔落伤害）
            shell.noteSmashImpact();
            shell.resetFallDistance();
            // 音效：原版的选择 —— 目标站在地上 → 砸地（坠落 > 5 格是"重"版）；在空中 → 空击音。
            // 位置也照原版：播在<b>攻击者（空壳）</b>身上，而不是目标身上。
            net.minecraft.sounds.SoundEvent smashSound = !target.onGround()
                ? SoundEvents.MACE_SMASH_AIR
                : diveHeight > 5.0F ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND;
            level.playSound(null, shell.getX(), shell.getY(), shell.getZ(),
                smashSound, SoundSource.HOSTILE, 1.0F, 1.0F);
            // 粒子：<b>原版就一行</b> —— MaceItem#knockback 开头的砸击级别事件 2013，
            // 客户端由 ParticleUtils#spawnSmashAttackParticles 生成：
            //   · 中心一团：DUST_PILLAR（带命中处方块外观的尘柱），power/3 = 250 颗，高斯偏移 ±0.5、速度 ±0.2；
            //   · 半径 3.5 格的圆环：power/1.5 = 500 颗，同样带方块外观。
            // 这里<b>不自己拼任何粒子</b>：原版没有风爆（Wind Burst）时不会出现阵风粒子，
            // 命中处也没有那些白/暴击粒子 —— 交给原版事件最稳，而且其它客户端/录像端也一致。
            // 位置取<b>命中体</b>脚下（九头蛇时就是那个头的位置，尘柱才会在打中的地方炸开）。
            level.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_SMASH_ATTACK,
                victim.getOnPos(), SMASH_ATTACK_EVENT_POWER);
        }
    }

    /**
     * 落水自救：主手换水桶 → 算落点放水 → 手里换空桶 → 落地后把水收回来。
     *
     * <h2>为什么这里要"多试几个落点 + 认输"</h2>
     * 只认预测落点的话，只要那一格不可替换（落在台阶/雪层/告示牌上、或区块没加载导致预测返回 null），
     * 就会一直放不下水 —— 而 {@code waterPos} 一直是 null，收集分支永远进不去，
     * 空壳就<b>拿着空桶原地干站着</b>直到整套动作超时（实测反馈）。
     * 现在按优先级试一串候选格，都不行就等一小会儿认输收场，绝不卡在那里。
     */
    /**
     * <b>每刻在空壳"移动之前"做落地保护的那点事</b>：由 {@link PlayerShellEntity#tick()} 在
     * {@code super.tick()} 之前调用。
     *
     * <h2>为什么必须抢在移动之前</h2>
     * 摔落伤害是在 {@code super.tick()} 内部的"移动 → {@code checkFallDamage} 落地检查"里结算的。
     * 之前铺水这一步是跟在移动<b>之后</b>跑的，于是只要落点预测差一刻（俯冲时还在吃烟花加速，
     * 实际比模拟更快），就会出现"落地那刻水还没铺好"—— 先受伤、水才出现（实测反馈）。
     * 挪到移动之前以后，只要这一 tick 会落地，水已经先在那儿了；哪怕预测偏了一刻也救得回来。
     *
     * <p>{@link Phase#WATER}：幂等地补铺一次水（真正的收水/认输仍在移动之后的 {@link #tickWater} 里）。
     * <p>{@link Phase#PEARL}：把这一 tick 的坠落距离<b>清零</b> —— 下界那一趟落地由末影珍珠负责
     * （它会把空壳传送过去），所以在这一段里不该再攒摔落伤害；要补的只有末影珍珠那 5 点，
     * 见 {@link #tickPearl}。这样即使珍珠慢了一两刻、人先落地，也不会先吃一记长距离摔落。
     */
    static void preMoveStep(PlayerShellEntity shell) {
        State state = shell.getMaceState();
        if (state == null) {
            return;
        }
        if (state.phase == Phase.PEARL) {
            shell.fallDistance = 0.0F;
            return;
        }
        if (state.phase != Phase.WATER || state.waterPos != null) {
            return;
        }
        if (shell.level() instanceof ServerLevel level && !waterSaveImpossible(level)) {
            placeWater(shell, level, state);
        }
    }

    /**
     * 这个维度能不能"落地水"：<b>不能</b>的只有原版 {@code dimensionType().ultraWarm()} 的维度
     * —— 下界，以及任何把 ultrawarm 打开的自定义维度（那里的水一倒出来就蒸发）。
     * <p>
     * 判据与原版 {@code BucketItem#emptyContents} 里"水在下界蒸发"那一条完全一致
     * （{@code level.dimensionType().ultraWarm() && content.is(FluidTags.WATER)}），
     * 所以不写死 {@code Level.NETHER}：模组维度只要开了 ultrawarm 也一样放不出水。
     */
    private static boolean waterSaveImpossible(ServerLevel level) {
        return level.dimensionType().ultraWarm();
    }

    /** 铺水：主手水桶 → 在落点放水 → 手里换空桶。幂等（已放好就直接返回）。 */
    private static void placeWater(PlayerShellEntity shell, ServerLevel level, State state) {
        if (state.waterPos != null) {
            return;
        }
        // 下界这类维度：水放不出来（见 waterSaveImpossible）。这里只兜底，正常路径在
        // preMoveWaterStep / tickWater 就已经让开了。
        if (waterSaveImpossible(level)) {
            return;
        }
        shell.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        BlockPos spot = findWaterSpot(shell, level);
        if (spot != null) {
            level.setBlockAndUpdate(spot, Blocks.WATER.defaultBlockState());
            state.waterPos = spot;
            level.playSound(null, spot.getX() + 0.5D, spot.getY() + 0.5D, spot.getZ() + 0.5D,
                SoundEvents.BUCKET_EMPTY, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        shell.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        state.bucketEmptied = true;
    }

    private static void tickWater(PlayerShellEntity shell, ServerLevel level, State state) {
        // 还在下落途中：如果这时候目标进入够得着的范围，照样补一锤（别白放过）。
        // 但"临时落地水自救"（见 tryStartEmergencyWaterSave）手里没有重锤，不补这一锤。
        LivingEntity target = shell.getTarget();
        if (!state.emergencySave && target != null && target.isAlive() && inSmashReach(shell, state, target)) {
            smash(shell, level, state, target);
            finish(shell, "砸中(落水途中)");
            return;
        }
        if (state.waterPos == null) {
            // 移动之前已经由 preMoveStep 铺过一次；这里是兜底（例如刚进 WATER 阶段的那一 tick 之后）
            placeWater(shell, level, state);
            // 临时自救：已经落地（来不及了）就立刻收场，别拿着空桶站着
            if (state.emergencySave && shell.onGround()) {
                finish(shell, "落地水来不及");
                return;
            }
            // 放不下（各种奇怪地形）就别耗着：给 2 秒再试，还是不行就收场
            if (state.phaseTicks > 40) {
                finish(shell, "落水无位置");
            }
            return;
        }
        // 已经放了水：等落地，落地后用空桶把水收回去
        if (shell.onGround()) {
            if (level.getBlockState(state.waterPos).is(Blocks.WATER)) {
                level.setBlockAndUpdate(state.waterPos, Blocks.AIR.defaultBlockState());
                level.playSound(null, state.waterPos.getX() + 0.5D, state.waterPos.getY() + 0.5D,
                    state.waterPos.getZ() + 0.5D, SoundEvents.BUCKET_FILL, SoundSource.HOSTILE, 1.0F, 1.0F);
            }
            finish(shell, "落水收场");
        }
    }

    /**
     * <b>ultrawarm 维度（下界）的落地保护：末影珍珠</b>（用户指定）。
     * <p>
     * 期望的流程：将要落地时向下丢一颗末影珍珠 → 珍珠砸到地面 → 原版把主人（空壳）
     * 传送过去并清零坠落距离（{@code ThrownEnderpearl#onHit} 的"主人不是玩家"那条分支）
     * → 空壳因此只吃末影珍珠那一下摔落伤害，而不是几十格的长距离摔落。
     *
     * <p>唯一要自己补的是<b>伤害</b>：原版那条分支不结算末影珍珠的摔落伤害（只有玩家分支才有），
     * 所以这里在人落地（或兜底超时）时补一次 {@link #ENDER_PEARL_FALL_DAMAGE}，
     * 用的也是 {@code damageSources().fall()} —— 与玩家挨的那一下同一种伤害。
     *
     * <p>坠落距离在这一阶段被 {@link #preMoveStep} 每刻清零：万一珍珠慢了一两刻、人先落地，
     * 也不会先吃一记长距离摔落（那正是这一整套要避免的）。
     */
    private static void tickPearl(PlayerShellEntity shell, ServerLevel level, State state, @Nullable LivingEntity target) {
        // 还在下落途中：够得着就照样补一锤（与落水阶段那条同义）。临时自卫（emergencySave）没拿重锤，不补。
        if (!state.emergencySave && target != null && target.isAlive() && inSmashReach(shell, state, target)) {
            smash(shell, level, state, target);
            finish(shell, "砸中(末影珍珠途中)");
            return;
        }
        if (!state.pearlThrown) {
            throwPearl(shell, level, state);
        }
        // 传送完成（人已经站在地上）或等太久了：补上末影珍珠那一下伤害，然后收场
        boolean settled = shell.onGround() || state.phaseTicks > PEARL_SETTLE_TIMEOUT_TICKS;
        if (!settled) {
            return;
        }
        if (!state.pearlDamageApplied) {
            state.pearlDamageApplied = true;
            applyPearlFallDamage(shell, level);
        }
        if (shell.onGround()) {
            finish(shell, "末影珍珠落地");
        } else {
            finish(shell, "末影珍珠没落地(超时)");
        }
    }

    /**
     * 结算"末影珍珠那一下摔落伤害"。
     *
     * <h2>为什么要"补足差额"（实测：走原版那一遍几乎扣不到血）</h2>
     * 先照原版那条路走一遍：{@code hurt(damageSources().fall(), 5)}。但这具空壳身上的防御太厚，
     * 5 点会被层层削到"看不见"：
     * <ol>
     *   <li><b>保护 16</b>（{@code PlayerShellEntity#CORE_BACKED_PROTECTION_LEVEL}，以"魔咒状态效果"的形式
     *       常驻）—— 模组自己的受击侧减伤 {@code ModMain#handleEnchantEffectsOnHit} 会按
     *       {@code 1 - min(20, epf)/25} 缩放，16 级就是 ×0.36；</li>
     *   <li>副手/身上的 <b>20 护甲 + 12 韧性</b>再削一刀（原版 {@code getDamageAfterArmorAbsorb}）；</li>
     *   <li>附魔金苹果的<b>抗性提升</b>、以及可能存在的<b>摔落缓冲</b>（再 ×(1-0.8)）继续削。</li>
     * </ol>
     * 叠下来 5 点只剩零点几 —— 血量条上根本看不出来，实测表现就是"珍珠传送成功了、
     * 却完全没受到摔落伤害"（用户反馈）。用户要的是"这一下必须扣血"，所以这里再<b>把差额直接补到
     * 本体血量</b>上：无论伤害是被护甲/保护附魔减掉、被伤害吸收吃掉、还是被无敌帧驳回，
     * 本体血量最终都正好少 {@value #ENDER_PEARL_FALL_DAMAGE} 点。
     * <p>顺带把原版那一遍啃掉的黄心（伤害吸收）还回去：这一记是"冲着本体血量去的"。
     * <p>血量扣到 0 也不用特殊处理 —— 原版 {@code LivingEntity#baseTick} 里有
     * {@code isDeadOrDying() → tickDeath()} 那条路。
     */
    /**
     * 结算"末影珍珠那一下摔落伤害"。
     *
     * <p>就是原版那一下：{@code hurt(damageSources().fall(), 5)} —— 与玩家挨的完全同源同额。
     * <b>它会照常受这具空壳自己那一身防御影响</b>，这里<b>一条都不绕过</b>：
     * <ul>
     *   <li><b>「耐久」魔咒状态效果</b>：有 (等级/100) 的概率<b>整段免疫</b>任何入射伤害
     *       （见 {@code ModMain#handleEnchantEffectsOnHit}，命中时 {@code setCanceled(true)}，
     *       连受伤音效都不会播）—— 实测"传送成功了却像没受伤"就是它；</li>
     *   <li><b>「保护」等护甲类魔咒状态效果</b>（空壳常驻 {@code CORE_BACKED_PROTECTION_LEVEL} 级）、
     *       原版护甲/韧性、抗性提升、摔落缓冲依次减伤 —— 叠下来这 5 点通常只剩零点几；</li>
     *   <li><b>无敌帧</b>：{@code invulnerableTime > 10} 时，原版会驳回"不大于上次"的伤害。</li>
     * </ul>
     * 这些都是这具空壳平时挨打的正常规则（用户确认："没受伤"正是耐久免掉的，属正常表现），
     * 所以这里保持原版语义，只在日志里把实际结果打出来方便对照。
     *
     * <p><b>如果以后想让这一下"必定扣满 5 点"</b>（无视耐久/护甲/保护），在下面补一句差额即可：
     * {@code if (lost < ENDER_PEARL_FALL_DAMAGE) shell.setHealth(shell.getHealth() - (ENDER_PEARL_FALL_DAMAGE - lost));}
     * —— 6.6.11 ~ 6.6.13 那几版就是这么做的。
     */
    private static void applyPearlFallDamage(PlayerShellEntity shell, ServerLevel level) {
        DamageSource fall = level.damageSources().fall();
        float healthBefore = shell.getHealth();
        float absorptionBefore = shell.getAbsorptionAmount();
        int invulnerableBefore = shell.invulnerableTime;
        boolean hurt = shell.hurt(fall, ENDER_PEARL_FALL_DAMAGE);
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
            "[落地保护] 末影珍珠伤害结算: 命中={} 血量 {} -> {}（护甲 {}；伤害吸收 {} -> {}；打之前无敌帧 {}）",
            hurt, String.format("%.1f", healthBefore), String.format("%.1f", shell.getHealth()),
            shell.getArmorValue(),
            String.format("%.1f", absorptionBefore), String.format("%.1f", shell.getAbsorptionAmount()),
            invulnerableBefore);
    }

    /**
     * 朝正下方丢一颗末影珍珠（主手会短暂地拿着珍珠，看得见"是它丢的"）。
     * <p>
     * 主人就是空壳：原版会把主人传送走，所以落点由珍珠自己决定（与"落水"铺在预测落点上等价）。
     * 出膛速度给得比下坠快（{@value #PEARL_SPEED} 格/刻），保证珍珠先砸到地面。
     */
    private static void throwPearl(PlayerShellEntity shell, ServerLevel level, State state) {
        state.pearlThrown = true;
        shell.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_PEARL));
        shell.swing(InteractionHand.MAIN_HAND);
        Vec3 from = shell.getEyePosition();
        net.minecraft.world.entity.projectile.ThrownEnderpearl pearl =
            new net.minecraft.world.entity.projectile.ThrownEnderpearl(level, shell);
        pearl.setPos(from.x, from.y - 0.1D, from.z);
        pearl.shoot(0.0D, -1.0D, 0.0D, PEARL_SPEED, 0.0F);
        level.addFreshEntity(pearl);
        shell.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDER_PEARL_THROW,
            SoundSource.NEUTRAL, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    /** 距目标 10 格：主手换致密 V 重锤，背上的鞘翅换成副手那件钻石胸甲（于是开始提供护甲值）。 */
    private static void equipMace(PlayerShellEntity shell, ServerLevel level) {
        ItemStack chestplate = shell.getItemBySlot(EquipmentSlot.OFFHAND);
        if (!(chestplate.getItem() instanceof net.minecraft.world.item.ArmorItem)) {
            chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
        }
        shell.setItemSlot(EquipmentSlot.CHEST, chestplate.copy());
        shell.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        shell.setItemInHand(InteractionHand.MAIN_HAND, densityMace(level));
        // 换掉鞘翅之后 updateFallFlying 会自动清掉滑翔标记 —— 从此进入自由落体，重锤的坠落加成开始积累
        shell.setSharedFlag(7, false);
        level.playSound(null, shell.getX(), shell.getY(), shell.getZ(),
            SoundEvents.ARMOR_EQUIP_DIAMOND.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    /**
     * 收尾的<b>公共部分</b>（{@link #finish 正常收场} 与 {@link #abort 被打断} 共用）：
     * 收掉自己放的水与还挂着的烟花，然后把<b>胸甲 / 双手 / 滑翔标记</b>归位。
     *
     * <p><b>滑翔标记（{@code setSharedFlag(7, false)}）是这里的重点</b>：原版
     * {@code LivingEntity#isFallFlying()} 只看这个标记，而重锤的爬升/俯冲<b>全程都把它开着</b>
     * （滑翔是那一套的物理基础）。少清这一下，被打断时它就会<b>一直保持滑翔</b>：
     * 客户端画成躺平滑翔姿态、物理走"滑翔"那条分支（速度由朝向决定），表现就是
     * <b>"平缓地慢慢往下飘"</b>（用户实测反馈：切创造丢掉目标之后就是这样）。
     */
    private static void cleanup(PlayerShellEntity shell, @Nullable State state) {
        if (state == null) {
            return;
        }
        // 万一还留着自己放的水，收掉
        if (state.waterPos != null && shell.level() instanceof ServerLevel level
            && level.getBlockState(state.waterPos).is(Blocks.WATER)) {
            level.setBlockAndUpdate(state.waterPos, Blocks.AIR.defaultBlockState());
        }
        // 还挂着的烟花也收掉（不然它会跟着空壳飘一段）
        if (state.rocketUuid != null && shell.level() instanceof ServerLevel rocketLevel) {
            net.minecraft.world.entity.Entity rocket = rocketLevel.getEntity(state.rocketUuid);
            if (rocket != null) {
                rocket.discard();
            }
        }
        if (state.emergencySave) {
            // 临时落地水自救：只是借主手放了个水，收场时把原来的东西还回去，胸甲与滑翔状态一概不碰
            shell.setItemInHand(InteractionHand.MAIN_HAND, state.savedMain);
            shell.setItemInHand(InteractionHand.OFF_HAND, state.savedOff);
            return;
        }
        // 胸甲：会飞的空壳把翅膀还回去（重锤起手时换成了钻石胸甲）。
        // 不会飞的才清空 —— 否则一次重锤动作就等于把它那副翅膀永久剥掉了。
        // <b>不会飞的那一支必须清空</b>：起手时穿的那件鞘翅留着的话，它带着"滑翔标记 + 鞘翅"
        // 就是一副永远滑下去的样子（这一次用户实测的正是这个：目标没死，是切创造丢了目标）。
        shell.setItemSlot(EquipmentSlot.CHEST, shell.hasFlightAbility()
            ? new ItemStack(cn.autoforged.joes_addons_for_abmc.item.ModItems.WINGS.get())
            : ItemStack.EMPTY);
        shell.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        shell.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        shell.setSharedFlag(7, false);
    }

    /** 收场：把装备、滑翔、状态全部清干净。 */
    private static void finish(PlayerShellEntity shell, String reason) {
        cleanup(shell, shell.getMaceState());
        // "失败"的账记在 {@link State#failedTick} 上（不进这里）：那一套动作一旦收场，
        // 这本账就跟着作废 —— 落地/超时/卡住这些收场本来就该走老路（落地后照常抽签）。
        shell.setMaceState(null);
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info("[重锤] 动作结束：{}", reason);
    }

    /**
     * 这一套是不是<b>已经失败</b>了（用户指定的判据）：<b>自身高度低于目标高度</b>，而重锤始终没砸出去。
     * <p>
     * 还要求"人此刻还在空中"——已经落地就不算"滞空"，那时走的是老路（落地后照常抽签）。
     * 临时落地水自救（{@code emergencySave}）根本不是重锤动作，永远不算失败。
     */
    private static boolean failedBelowTarget(PlayerShellEntity shell, @Nullable State state) {
        if (state != null && state.emergencySave) {
            return false;
        }
        if (shell.onGround()) {
            return false;
        }
        LivingEntity target = shell.getTarget();
        return target != null && target.isAlive() && shell.getY() < target.getY();
    }

    /**
     * <b>失败判定 + "滞空重来一整套"</b>（用户指定）：在 {@link #tick} 里每刻调用。
     *
     * <h2>判据</h2>
     * <b>失败 = 自身高度已经低于目标高度、而这一套的重锤始终没砸出去</b>（俯冲越过目标了；
     * 俯角被夹在 {@value #DIVE_PITCH_MIN}~{@value #DIVE_PITCH_MAX} 度，越过之后只会越掉越低）。
     * 失败之后再<b>滞空超过 {@value #FAILURE_RETRY_TICKS} 刻</b>还没落地，就把这一套
     * <b>就地重置</b>成全新的一整套动作（{@link #restart}）：换鞘翅 → 飞到目标上方
     * {@value #MAX_CLIMB_BLOCKS} 格 → 俯冲 → 换重锤 → 砸 —— 而不是等落地之后再发动。
     *
     * <h2>为什么把账记在 {@link State} 上、写在状态机里面</h2>
     * 上一版把"待补招"的时刻记在空壳身上、由 {@code PlayerShellEntity#tick} 另开一个计时器去查，
     * 结果实测"账记上了却从来没补招"：外面任何一次收场（落水收场/卡住/超时…）、
     * 或者"落地/进水/目标看不见"之类的复查，都会在 10 刻内把这个记号悄悄清掉。
     * 现在"失败"只跟着这一套动作走：记在 {@link State#failedTick}，由状态机自己在原地重置 ——
     * 没有中间环节可以把它吃掉。
     *
     * <h2>哪些情况不重来（这次机会作废）</h2>
     * <ul>
     *   <li><b>爬升阶段</b>：那时它本来就在目标下方（例如目标站在塔顶），一判就会每 10 刻把爬升打断；</li>
     *   <li><b>已经落地 / 马上就要落地 / 上了载具</b>：这不是"滞空"，交给原来的落水自救与收场流程
     *       （也就是用户说的"落地后照常"那条老路）；</li>
     *   <li><b>目标已经不在索敌范围内</b>（用户指定"正常地不再索敌"）、没了、死了：作废，
     *       交给正常索敌去换目标或待命；</li>
     *   <li><b>头顶空间不够再爬 {@value #MIN_HEADROOM} 格</b>：重来一套也爬不上去，同样作废。</li>
     * </ul>
     * 作废之后 {@link State#failedTick} 变成 {@link #FAILURE_SPENT}，这一套不会再判第二次。
     *
     * @return true = 这一 tick 已经把动作重置成"刚起手"（调用方直接 return，下一 tick 接着跑）
     */
    private static boolean tickFailureRestart(PlayerShellEntity shell, ServerLevel level, State state,
                                              @Nullable LivingEntity target) {
        if (state.emergencySave) {
            return false;   // 临时落地水自救不是重锤动作，没有"失败"这回事
        }
        // ① 失败判定：已经降到目标高度以下，而重锤没砸出去
        if (state.failedTick == FAILURE_NONE) {
            if (state.phase == Phase.CLIMB || !failedBelowTarget(shell, state) || target == null) {
                return false;
            }
            state.failedTick = shell.tickCount;
            cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
                "[重锤] 已降到目标高度以下（自身 y={} < 目标 y={}）而重锤没砸出去："
                    + "{} 刻后若仍在滞空就重来一整套",
                String.format("%.1f", shell.getY()), String.format("%.1f", target.getY()), FAILURE_RETRY_TICKS);
            return false;
        }
        if (state.failedTick == FAILURE_SPENT) {
            return false;
        }
        // ② 还没滞空够：继续等
        if (shell.tickCount - state.failedTick < FAILURE_RETRY_TICKS) {
            return false;
        }
        // ③ 已经落地 / 马上就要落地 / 上了载具：不是"滞空"，这次机会作废（走老路）
        if (shell.onGround() || shell.isPassenger() || aboutToLand(shell, level)) {
            logFailureSkipped(shell, state, target, "已经落地（或马上就要落地）");
            state.failedTick = FAILURE_SPENT;
            return false;
        }
        // ④ 目标已经不在索敌范围内（用户指定"正常地不再索敌"）/ 没了 / 死了：作废
        if (target == null || !target.isAlive()) {
            logFailureSkipped(shell, state, target, "目标已经没了");
            state.failedTick = FAILURE_SPENT;
            return false;
        }
        if (!shell.isPossessionTargetNearby(target)) {
            logFailureSkipped(shell, state, target, "目标已经远远离开（视作已不在索敌范围）");
            state.failedTick = FAILURE_SPENT;
            return false;
        }
        // 连续重来太多次还是砸不中：收手，交回给原来的落水自救/收场流程（免得一直在天上重来）
        if (state.failureRestarts >= MAX_FAILURE_RESTARTS) {
            logFailureSkipped(shell, state, target,
                "已经连续重来 " + MAX_FAILURE_RESTARTS + " 次仍未砸中");
            state.failedTick = FAILURE_SPENT;
            return false;
        }
        // <b>不查视线</b>：空壳此刻正掉在目标下方，视线被岩壁/平台挡住是常事（那恰恰是它一开始
        // 往下掉的原因之一）；重来一整套本来就是要飞到目标上方 50 格再去砸，所以这里放它上去 ——
        // 见 {@link State#losGraceUntilTick}：重来后的爬升期也会宽限一段时间再判视线，
        // 免得刚起手就被"目标失去视线"收场。
        // ⑤ 重来一整套：把这一套就地重置成"刚起手"（含重新飞上去的高度）
        if (!restart(shell, level, state, target)) {
            state.failedTick = FAILURE_SPENT;
            return false;
        }
        return true;
    }

    /**
     * <b>把这一套动作就地重置成"刚起手"</b>：收掉自己留下的水与烟花，重新算一遍
     * "目标高度 + {@value #MAX_CLIMB_BLOCKS} 格"（仍受天花板限制），装备换回鞘翅 + 烟花 + 副手钻石胸甲，
     * 再给一脚起跳升力。
     * <p>与 {@link #begin} 的区别只有一处：沿用<b>同一个</b> {@link State}（不新起一个），
     * 于是正在推进这一套的状态机可以在原地接着跑，不需要先收场、也不会被外面的任何逻辑夹掉。
     *
     * @return false = 条件不允许重来（头顶空间不够），调用方应当把这次机会作废
     */
    private static boolean restart(PlayerShellEntity shell, ServerLevel level, State state, LivingEntity target) {
        double climbTop = target.getY() + MAX_CLIMB_BLOCKS;
        double ceilingY = findCeilingY(level, shell, climbTop);
        double maxY = Math.min(climbTop, ceilingY - CEILING_MARGIN);
        if (maxY - shell.getY() < MIN_HEADROOM) {
            // 重来一套也爬不上去（低矮洞穴/贴着天花板）：别折腾了，照老路掉下去
            if (mayLogLowSpace(shell)) {
                cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
                    "[重锤] 失败后想重来一套，但头顶空间不足（可用 {} 格 < {} 格），作废",
                    String.format("%.1f", maxY - shell.getY()), MIN_HEADROOM);
            }
            return false;
        }
        int airborneTicks = shell.tickCount - state.failedTick;
        // 收掉这一套留下的痕迹：自己铺的水、还挂着的烟花
        if (state.waterPos != null) {
            if (level.getBlockState(state.waterPos).is(Blocks.WATER)) {
                level.setBlockAndUpdate(state.waterPos, Blocks.AIR.defaultBlockState());
            }
            state.waterPos = null;
        }
        if (state.rocketUuid != null) {
            if (level.getEntity(state.rocketUuid) != null) {
                level.getEntity(state.rocketUuid).discard();
            }
            state.rocketUuid = null;
        }
        state.bucketEmptied = false;
        // 状态整体重置成"刚起手"那一刻
        state.phase = Phase.CLIMB;
        state.phaseTicks = 0;
        state.totalTicks = 0;
        state.failedTick = FAILURE_NONE;
        state.startY = shell.getY();
        state.apexY = shell.getY();
        state.prevX = shell.getX();
        state.prevY = shell.getY();
        state.prevZ = shell.getZ();
        state.progressRefTick = 0;
        state.progressRefY = shell.getY();
        state.maxY = maxY;
        state.failureRestarts++;
        // 爬升期不判视线的宽限（见 RESTART_LOS_GRACE_TICKS）：失败时它正掉在目标下方，
        // 视线被地形挡着很常见，别刚起手就被"目标失去视线"收场。
        state.losGraceUntilTick = shell.tickCount + RESTART_LOS_GRACE_TICKS;
        state.divePitch = DIVE_PITCH_MIN
            + shell.getRandom().nextFloat() * (DIVE_PITCH_MAX - DIVE_PITCH_MIN);
        // 装备：与 begin 起手那一套完全一致（鞘翅、烟花、副手钻石胸甲）
        shell.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        shell.setItemInHand(InteractionHand.MAIN_HAND, rocketStack());
        shell.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND_CHESTPLATE));
        shell.setSharedFlag(7, true);
        shell.setDeltaMovement(0.0D, 0.42D, 0.0D);
        shell.hasImpulse = true;
        level.playSound(null, shell.getX(), shell.getY(), shell.getZ(),
            SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.HOSTILE, 1.0F, 1.2F);
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
            "[重锤] 失败后已滞空 {} 刻仍未落地：换回鞘翅，重新飞到目标上方 {} 格再砸一遍",
            airborneTicks, MAX_CLIMB_BLOCKS);
        return true;
    }

    /**
     * 是不是"马上就要撞地"（与"临时落地水自救"用的是同一条判据：预测 {@value #WATER_LOOKAHEAD_TICKS}
     * 刻内落地）。
     * <p>用它而不是"离地高度"来判：掉进水里时高度图上的"地面"是水面，用高度判会把它误判成"要落地了"。
     */
    private static boolean aboutToLand(PlayerShellEntity shell, ServerLevel level) {
        Landing landing = predictLanding(shell, level);
        return landing != null && landing.ticks() <= WATER_LOOKAHEAD_TICKS;
    }

    /** "滞空满 {@value #FAILURE_RETRY_TICKS} 刻却没重来"的日志：把原因与相对位置写清楚，方便对照实测。 */
    private static void logFailureSkipped(PlayerShellEntity shell, State state, @Nullable LivingEntity target,
                                          String reason) {
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
            "[重锤] 失败后已滞空 {} 刻但没有重来：{}（自身 y={} / 目标 y={} / 相距 {} 格：水平 {}、竖直 {}；离地 {} 格）",
            shell.tickCount - state.failedTick, reason,
            String.format("%.1f", shell.getY()),
            target == null ? "无" : String.format("%.1f", target.getY()),
            target == null ? "?" : String.format("%.1f", shell.distanceTo(target)),
            target == null ? "?" : String.format("%.1f",
                Math.hypot(target.getX() - shell.getX(), target.getZ() - shell.getZ())),
            target == null ? "?" : String.format("%.1f", target.getY() - shell.getY()),
            String.format("%.1f", shell.level() instanceof ServerLevel level
                ? heightAboveGround(shell, level) : 0.0D));
    }

    /**
     * 空壳退出重锤动作时调用（<b>一切"被打断"的路径</b>）：切创造/旁观丢掉目标
     * （{@code PlayerShellEntity#dropUntouchableTarget}）、被支援幸运方块冻住、被弹射物打落、
     * NoAI 总闸关闭……都走这里。
     * <p>
     * 它做的收尾与 {@link #finish} <b>完全一致</b>（都走 {@link #cleanup}）：收水、收烟花、
     * 胸甲归位、双手清空、<b>清掉滑翔标记</b>。
     * <p>以前这里只清状态 + 补翅膀，漏了滑翔标记 —— 于是"被打断"时它会保留鞘翅与滑翔标记，
     * 客户端画成躺平滑翔、物理走滑翔分支，<b>表现就是一直平缓地慢慢往下飘</b>
     * （用户实测：生存→创造之后目标被丢掉，空壳就那样飘着不下来）。
     */
    static void abort(PlayerShellEntity shell) {
        State state = shell.getMaceState();
        if (state == null) {
            return;
        }
        // <b>与正常收场走同一套清理</b>：以前这里只清状态 + 补翅膀，漏了「滑翔标记」和「另一只手」——
        // 于是任何"被打断"的路径（切创造/旁观丢掉目标、被支援幸运方块冻住、被弹射物打落……）
        // 都会把它留在一副<b>永远平缓滑翔</b>的样子里（{@code isFallFlying()} 只看那个标记，
        // 而重锤那套全程开着它；物理随后就走滑翔分支：速度由朝向决定、慢慢往下飘）。
        cleanup(shell, state);
        shell.setMaceState(null);
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info("[重锤] 动作被打断（收尾：装备 + 滑翔标记已清）");
    }

    // ====================== 工具 ======================

    /** "头顶空间不足"日志的节流表（空壳 UUID → 上次打印的游戏刻）。 */
    private static final java.util.Map<UUID, Long> LOW_SPACE_LOG_TICKS = new java.util.HashMap<>();

    /** "落地保护没起手"诊断日志的节流表（空壳 UUID → 上次打印的游戏刻）。 */
    private static final java.util.Map<UUID, Long> WATER_SAVE_DEBUG_TICKS = new java.util.HashMap<>();

    /**
     * 诊断：<b>"该救却没救"时留一行日志</b>（每条空壳 5 秒最多一行）。
     * <p>
     * 只在"已经在危险高度下坠、却起不了手"时打 —— 这正是用户反馈的
     * <b>"直接落地、没有落地水/落地珍珠"</b>的场景。有这一行就能一眼看出卡在哪条闸上
     * （落点预测不出去 / 找不到放水格），不用再靠猜。
     */
    private static void mayLogWaterSaveBail(PlayerShellEntity shell, String reason, @Nullable Landing landing) {
        long now = shell.level().getGameTime();
        Long last = WATER_SAVE_DEBUG_TICKS.get(shell.getUUID());
        if (last != null && now - last < 100L) {
            return;
        }
        if (WATER_SAVE_DEBUG_TICKS.size() > 64) {
            WATER_SAVE_DEBUG_TICKS.clear();
        }
        WATER_SAVE_DEBUG_TICKS.put(shell.getUUID(), now);
        cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
            "[落地保护] 未起手（{}）：坠落 {} 格、竖直速度 {}、离地 {} 格、落点预测 {}",
            reason,
            String.format("%.1f", shell.fallDistance),
            String.format("%.2f", shell.getDeltaMovement().y),
            String.format("%.1f", shell.level() instanceof ServerLevel lvl ? heightAboveGround(shell, lvl) : 0.0D),
            landing == null ? "失败" : (landing.pos() == null
                ? ("无落点，还有 " + landing.ticks() + " 刻")
                : (landing.pos() + "（还有 " + landing.ticks() + " 刻）")));
    }

    /** 同一条"空间不足"日志 5 秒内只打一次（debug 模式下它可能每 1~2 秒被触发一次）。 */
    private static boolean mayLogLowSpace(PlayerShellEntity shell) {
        long now = shell.level().getGameTime();
        Long last = LOW_SPACE_LOG_TICKS.get(shell.getUUID());
        if (last != null && now - last < 100L) {
            return false;
        }
        if (LOW_SPACE_LOG_TICKS.size() > 64) {
            LOW_SPACE_LOG_TICKS.clear();
        }
        LOW_SPACE_LOG_TICKS.put(shell.getUUID(), now);
        return true;
    }

    /** 致密 V 重锤（致密 = Density，1.21 的重锤附魔，按坠落距离加伤）。 */
    private static ItemStack densityMace(ServerLevel level) {
        ItemStack mace = new ItemStack(Items.MACE);
        var enchantments = level.registryAccess()
            .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        mace.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.DENSITY), 5);
        return mace;
    }

    /** 一颗"只推进、不爆炸"的烟花（飞行时长 1 = 推进 20~32 刻，我们再按 {@link #ROCKET_BOOST_TICKS} 收掉它）。 */
    private static ItemStack rocketStack() {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of()));
        return rocket;
    }

    /** 放一颗推进烟花（挂在空壳身上，推力方向 = 空壳视线方向）。 */
    private static void launchRocket(PlayerShellEntity shell, ServerLevel level, State state) {
        retireRocketIfExpired(shell, level, state);
        FireworkRocketEntity rocket = new FireworkRocketEntity(level, rocketStack(), shell);
        level.addFreshEntity(rocket);
        state.rocketUuid = rocket.getUUID();
        state.rocketTick = shell.tickCount;
    }

    /** 满 {@link #ROCKET_BOOST_TICKS} 刻就把当前这颗烟花收掉（做到"正好推进 1 秒"）。 */
    private static void retireRocketIfExpired(PlayerShellEntity shell, ServerLevel level, State state) {
        if (state.rocketUuid == null) {
            return;
        }
        if (shell.tickCount - state.rocketTick < ROCKET_BOOST_TICKS) {
            return;
        }
        if (level.getEntity(state.rocketUuid) instanceof FireworkRocketEntity rocket) {
            rocket.discard();
        }
        state.rocketUuid = null;
    }

    /** 当前离地（含水面）多少格。 */
    private static double heightAboveGround(PlayerShellEntity shell, ServerLevel level) {
        int groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Mth.floor(shell.getX()), Mth.floor(shell.getZ()));
        return shell.getY() - groundY;
    }

    /**
     * 头顶第一格"挡住去路"的方块高度（没有天花板就返回 {@code upTo + 1}）。
     * <p>扫描从空壳头顶一直扫到 {@code upTo}（= 本次想爬到的最高点，由目标高度算出），
     * 并且不会越过世界高度上限 —— 所以"目标在很高处"时也扫得到该扫的地方。
     * <p>每一层扫的是<b>空壳水平截面覆盖到的所有方块列</b>（不只是中心那一列）：
     * 贴着岩壁、卡在斜坡/树冠里时，挡住它的方块常常不在中心列上，
     * 只扫中心会把"头顶其实被堵住"误判成"没东西"，于是一直被卡着还在放烟花（实测反馈）。
     */
    private static double findCeilingY(ServerLevel level, PlayerShellEntity shell, double upTo) {
        net.minecraft.world.phys.AABB box = shell.getBoundingBox();
        int fromY = Mth.floor(shell.getY()) + 1;
        int top = Math.min(Mth.floor(upTo) + 2, level.getMaxBuildHeight() - 1);
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ);
        for (int y = fromY; y <= top; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                        return y;
                    }
                }
            }
        }
        return top + 1;
    }

    /**
     * 落点预测的结果：预计落在哪一格（可能为 null，例如在虚空里一路掉下去）+ 还要几刻。
     */
    private record Landing(int ticks, @Nullable BlockPos pos) {
    }

    /**
     * 按当前速度预测落点（原版重力：每刻 {@code vy = (vy - 0.08) * 0.98}，水平各乘 0.98）。
     * <p>
     * 找到第一格"脚下是实心、本体所在格可替换"的位置就记下它，并返回<b>到那时还要几刻</b> ——
     * 落水自救靠这个"还剩几刻"提前出手（见 {@link #tickSmash}）。
     * 模拟不出落点（掉落虚空/区块没加载）时 pos 为 null，但刻数仍然给出来。
     *
     * <h2>为什么必须扫"这一步跨过的一整段"（6.7.11 修正）</h2>
     * 自由落体到终端速度时每刻走 <b>≈3.9 格</b>（{@code 0.08 × 0.98 / (1 - 0.98)}），
     * 而"地面之上那一格空气"只有 1 格高 —— 只看每刻的采样点，就有约 <b>3/4 的概率一步跳过它、
     * 直接扎进地里</b>：之后模拟一路穿过实心方块直到基岩，判成"没有落点"，
     * 落地保护于是永不起手，空壳就那么<b>直接砸在地上</b>（用户实测：切创造丢了目标之后就是这样）。
     * 所以这里改成扫 {@link #firstLandingOnSegment 这一步的整段}，一格都不漏。
     */
    private static Landing predictLanding(PlayerShellEntity shell, ServerLevel level) {
        Vec3 pos = shell.position();
        Vec3 vel = shell.getDeltaMovement();
        for (int i = 1; i <= LANDING_SIM_MAX_TICKS; i++) {
            Vec3 from = pos;
            vel = new Vec3(vel.x * 0.98D, (vel.y - 0.08D) * 0.98D, vel.z * 0.98D);
            pos = from.add(vel);
            if (pos.y < level.getMinBuildHeight()) {
                return new Landing(i, null);
            }
            BlockPos landing = firstLandingOnSegment(level, from, pos);
            if (landing != null) {
                return new Landing(i, landing);
            }
        }
        return new Landing(LANDING_SIM_MAX_TICKS, null);
    }

    /**
     * 在 {@code from → to} 这一步跨过的所有方块里，<b>从高到低</b>找第一个
     * "本体所在格可替换、下面一格不可替换"的位置（也就是"地面之上那一格空气"）。找不到返回 null。
     * <p>
     * 只扫这一段的<b>竖直</b>范围（x/z 用这一步的终点）：自由落体时水平位移可以忽略，
     * 而竖直方向上每一格都必须看 —— 原因见 {@link #predictLanding} 的类注释。
     */
    @Nullable
    private static BlockPos firstLandingOnSegment(ServerLevel level, Vec3 from, Vec3 to) {
        // 只从"当前脚底"往下扫：落点必然在本刻位置或更低处（上升时这一步直接不扫，本来也没在落地）
        int top = Mth.floor(from.y);
        int bottom = Mth.floor(to.y);
        int x = Mth.floor(to.x);
        int z = Mth.floor(to.z);
        for (int y = top; y >= bottom; y--) {
            BlockPos at = new BlockPos(x, y, z);
            if (!level.isLoaded(at)) {
                continue;   // 区块没加载：这一格先不算（等它加载出来再判）
            }
            if (level.getBlockState(at).canBeReplaced() && !level.getBlockState(at.below()).canBeReplaced()) {
                return at;
            }
        }
        return null;
    }

    /** 调试用：当前阶段名（{@code /jafa orbinfo} 的"走位"字段用）。 */
    static String describe(PlayerShellEntity shell) {
        State state = shell.getMaceState();
        if (state == null) {
            return "无";
        }
        return switch (state.phase) {
            case CLIMB -> "重锤-爬升";
            case DIVE -> "重锤-俯冲";
            case SMASH -> "重锤-砸落";
            case WATER -> "重锤-落水";
            case PEARL -> "落地-末影珍珠";
        };
    }
}
