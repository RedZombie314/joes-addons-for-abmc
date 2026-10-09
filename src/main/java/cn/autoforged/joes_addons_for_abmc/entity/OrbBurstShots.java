package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <b>附体远程攻击的"连发"调度器</b>：一次攻击连续打出若干发，每发之间隔 1~2 游戏刻。
 *
 * <h2>用户需求</h2>
 * <ul>
 *   <li>"远程攻击的射普通箭改为高频射出 3~5 支箭（间隔 1~2gt），箭的飞行速度不变"；</li>
 *   <li>"远程的投掷三叉戟改为高频扔出 3~5 只三叉戟（间隔 1~2gt），三叉戟的飞行速度不变"；</li>
 *   <li>天境联动那几张签也走这里：毒镖 3~4 只、惊雷飞刀 2~3 只、凤舞长弓的火箭 1~2 支
 *       （见 {@code AetherCompat}，它们各自实现 {@link Shot} 后交给
 *       {@link #start(ServerLevel, LivingEntity, LivingEntity, Shot, int, int)}）。</li>
 * </ul>
 * 速度、附魔、伤害结算全部沿用各自的实现（{@link #ARROW_SHOT} 与原版满蓄力弓一致、
 * 力量 V 挂在作为"发射武器"传进去的弓上；三叉戟 2.5 格/刻 + 散布 1.0 与原版投掷一致），
 * <b>只是从"打一发"变成"打一梭子"</b>。
 *
 * <h2>为什么需要一个调度表</h2>
 * 出手是"跨刻"的：一次 {@code run} 只能做当下这一刻的事，而连发的后续几发必须晚几刻再发。
 * 做法与 {@link OrbAnvilRain 铁砧雨}完全一致 —— 一张静态待办表，由服务端 tick 钩子
 * （{@code ModMain#onServerTickPre}）每刻推进一次。
 *
 * <h2>第一发立刻打，后续才排队</h2>
 * {@link #start} 里<b>当场</b>打第一发，剩下的进表 —— 否则第一发会被推迟到下一个服务端刻
 * （{@code ServerTickEvent.Pre} 在实体 tick 之前），抬手的观感会慢一拍。
 *
 * <h2>每一发都重新瞄准</h2>
 * 每发都在<b>发射那一刻</b>重新取目标的当前位置与瞄准点（见
 * {@link OrbPossessedAttackEvents#aimAt}），所以目标边跑边挨打时这一梭子会跟着走，
 * 而不是五发全部打在同一个旧坐标上。
 *
 * <h2>目标没了就停</h2>
 * 每刻用 UUID 取回发射者与目标：任一不存在/已死，这一梭子就直接作废（剩下几发不再打）。
 * 用 UUID 而不是实体引用，是为了不把可能已经卸载的实体强引用留在静态表里。
 */
public final class OrbBurstShots {

    /** 一梭子最少几发（弓/三叉戟那两张签）。 */
    private static final int MIN_SHOTS = 3;

    /** 一梭子最多几发（弓/三叉戟那两张签）。 */
    private static final int MAX_SHOTS = 5;

    /** 两发之间的最短间隔（游戏刻）。 */
    private static final int MIN_INTERVAL_TICKS = 1;

    /** 两发之间的最长间隔（游戏刻）。 */
    private static final int MAX_INTERVAL_TICKS = 2;

    /** 箭矢速度（格/刻）：3 格/刻 = 60 格/秒，与原版满蓄力弓一致（用户要求"速度不变"）。 */
    private static final float ARROW_SPEED = 3.0F;

    /** 普通箭用的力量附魔等级：V = 5（与原来的单发版本一致）。 */
    private static final int ARROW_POWER_LEVEL = 5;

    /** 三叉戟投掷速度（格/刻）：与原版玩家投掷一致。 */
    private static final float TRIDENT_SPEED = 2.5F;

    /** 三叉戟的散布（原版 {@code shootFromRotation(..., 2.5F, 1.0F)} 里的 1.0）。 */
    private static final float TRIDENT_INACCURACY = 1.0F;

    /** 本 mod 自带的弹药类型（供 {@link #start(ServerLevel, LivingEntity, LivingEntity, Kind)} 用）。 */
    public enum Kind {
        /** 普通箭（主手那把力量 V 的弓只是"发射武器"，决定增伤）。 */
        ARROW,
        /** 三叉戟。 */
        TRIDENT
    }

    /**
     * <b>"打一发"的实现</b>：方向已经算好（每刻重新瞄，见类注释），这里只管把那一发打出去。
     * <p>
     * 天境那几张连发签（毒镖/惊雷飞刀/凤舞长弓）在 {@code AetherCompat} 里各自实现本接口，
     * 于是"连发调度"这套逻辑不需要认识天境的任何类型。
     */
    public interface Shot {
        void fire(ServerLevel level, LivingEntity shooter, LivingEntity target, Vec3 direction);
    }

    /** 一梭子待办：谁打的、打谁、怎么打、还剩几发、下一发在第几刻。 */
    private record Burst(ServerLevel level, UUID shooterUuid, UUID targetUuid, Shot shot,
                         int remaining, long nextTick) {
    }

    private static final List<Burst> ACTIVE = new ArrayList<>();

    private OrbBurstShots() {
    }

    /**
     * 开一梭子（本 mod 自带的两种弹药）：<b>当场打第一发</b>，其余进表排队。
     *
     * @param level   服务端世界
     * @param shooter 发射者（那具空壳）
     * @param target  目标（决定每一发的方向）
     * @param kind    弹药类型
     */
    public static void start(ServerLevel level, LivingEntity shooter, LivingEntity target, Kind kind) {
        start(level, shooter, target, shotFor(kind), MIN_SHOTS, MAX_SHOTS);
    }

    /**
     * 开一梭子（通用版）：装上任意一种 {@link Shot}，发数由调用方给。
     * <p>
     * 间隔固定 {@link #MIN_INTERVAL_TICKS}~{@link #MAX_INTERVAL_TICKS} 刻（用户指定的 1~2gt）。
     *
     * @param minShots 最少几发（含当场打出的第一发）
     * @param maxShots 最多几发（含当场打出的第一发）
     */
    public static void start(ServerLevel level, LivingEntity shooter, LivingEntity target, Shot shot,
                             int minShots, int maxShots) {
        int count = minShots + level.random.nextInt(Math.max(1, maxShots - minShots + 1));
        fireOne(level, shooter, target, shot);
        if (count > 1) {
            ACTIVE.add(new Burst(level, shooter.getUUID(), target.getUUID(), shot,
                count - 1, level.getGameTime() + rollInterval(level)));
        }
        if (ACTIVE.size() > 64) {
            ACTIVE.clear();   // 保险丝：异常情况下不让这张表无限长
        }
    }

    /**
     * 取消某个发射者名下还没打完的连发（{@code NoAI} 冻住、附体结束、死亡等）。
     * <p>
     * 连发是<b>跨刻</b>的：一次 {@code run} 之后还有几发排在表里，
     * 所以"不许出手"这条规则必须也能把队列清掉，否则解冻/收场之后那几发照样会飞出来。
     *
     * @return 取消了几梭子
     */
    public static int cancel(LivingEntity shooter) {
        UUID id = shooter.getUUID();
        int before = ACTIVE.size();
        ACTIVE.removeIf(burst -> burst.shooterUuid().equals(id));
        return before - ACTIVE.size();
    }

    /** 由服务端 tick 钩子（{@code ModMain#onServerTickPre}）每刻调用。 */
    public static void tick() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        java.util.ListIterator<Burst> iterator = ACTIVE.listIterator();
        while (iterator.hasNext()) {
            Burst burst = iterator.next();
            ServerLevel level = burst.level();
            if (level.getGameTime() < burst.nextTick()) {
                continue;
            }
            Entity shooterEntity = level.getEntity(burst.shooterUuid());
            Entity targetEntity = level.getEntity(burst.targetUuid());
            if (!(shooterEntity instanceof LivingEntity shooter) || !shooter.isAlive()
                || !(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
                iterator.remove();
                continue;
            }
            fireOne(level, shooter, target, burst.shot());
            int left = burst.remaining() - 1;
            if (left <= 0) {
                iterator.remove();
            } else {
                iterator.set(new Burst(level, burst.shooterUuid(), burst.targetUuid(), burst.shot(),
                    left, level.getGameTime() + rollInterval(level)));
            }
        }
    }

    /** 掷一次"两发之间隔几刻"：{@link #MIN_INTERVAL_TICKS}~{@link #MAX_INTERVAL_TICKS}。 */
    private static int rollInterval(ServerLevel level) {
        return MIN_INTERVAL_TICKS
            + level.random.nextInt(MAX_INTERVAL_TICKS - MIN_INTERVAL_TICKS + 1);
    }

    /** 打出一发。方向在<b>这一刻</b>重新算（见类注释）。 */
    private static void fireOne(ServerLevel level, LivingEntity shooter, LivingEntity target, Shot shot) {
        Vec3 direction = OrbPossessedAttackEvents.aimAt(shooter, target);
        if (direction == null) {
            return;
        }
        shot.fire(level, shooter, target, direction);
    }

    /** 本 mod 自带的两种弹药对应的实现。 */
    private static Shot shotFor(Kind kind) {
        return kind == Kind.TRIDENT ? TRIDENT_SHOT : ARROW_SHOT;
    }

    /** 普通箭：力量 V 的弓当"发射武器"（决定增伤），箭从眼位射出。 */
    private static final Shot ARROW_SHOT = (level, shooter, target, direction) -> {
        Vec3 from = shooter.getEyePosition();
        ItemStack bow = OrbPossessedAttackEvents.powerBow(level, ARROW_POWER_LEVEL);
        Arrow arrow = new Arrow(level, shooter, new ItemStack(Items.ARROW), bow);
        arrow.setPos(from.x, from.y - 0.1, from.z);
        arrow.shoot(direction.x, direction.y, direction.z, ARROW_SPEED, 0.0F);
        level.addFreshEntity(arrow);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ARROW_SHOOT,
            SoundSource.HOSTILE, 1.0F,
            1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + 0.5F);
    };

    /** 三叉戟：2.5 格/刻 + 散布 1.0（与原版玩家投掷一致），音效用原版的投掷声。 */
    private static final Shot TRIDENT_SHOT = (level, shooter, target, direction) -> {
        Vec3 from = shooter.getEyePosition();
        ThrownTrident trident = new ThrownTrident(level, shooter, new ItemStack(Items.TRIDENT));
        trident.setPos(from.x, from.y - 0.1, from.z);
        trident.shoot(direction.x, direction.y, direction.z, TRIDENT_SPEED, TRIDENT_INACCURACY);
        level.addFreshEntity(trident);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_THROW,
            SoundSource.HOSTILE, 1.0F, 1.0F);
    };
}
