package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.BeehiveStaffHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 苦力蜂（Beeper）：蜜蜂模型 + 苦力怕式自爆行为 + 蜜蜂式入巢/出巢。
 * <ul>
 *   <li>主动自爆：靠近生存/冒险、手中未持花、且未喂过花的玩家，在 3 格内引燃（膨胀+白闪+嘶嘶声），
 *       20 刻后以威力 10 爆炸并死亡消失。引燃中若目标握花（未被激怒）则取消。</li>
 *   <li>被玩家或任何生物攻击也会触发自爆，此时握花不能取消（beeper_provoked）。</li>
 *   <li>计数器：用花右击可 -1（生存/冒险消耗 1 个、创造不消耗）并记录该玩家为友好方；
 *       计数器 ≤0 时不再主动自爆（除非被激怒），且永不攻击喂过花的玩家。</li>
 *   <li>入巢/出巢：附近 40 格内有玩家时跳过采蜜（不调用蜜蜂脑行为），否则正常采蜜/回巢。</li>
 * </ul>
 * 持久化：counter、friendly 玩家列表、provoked，以及 {@code jafa_beeper} 标记
 * （供蜂巢权杖从 BEES 组件释放时判定生成 beeper 而非普通蜜蜂）。
 */
public class BeeperEntity extends Bee {

    public static final int MAX_SWELL = 30; // 与原版苦力怕引燃倒计时一致（30 刻）
    public static final double AGGRO_RANGE = 16.0;
    public static final double FUSE_DISTANCE = 3.0;
    public static final float EXPLOSION_POWER = 10.0F;
    /** 可引燃的玩家需满足：生存/冒险、未持花、且不在友好列表内。 */
    public static final double SWELL_SYNC_RANGE = 64.0; // 渲染同步距离参考

    private static final EntityDataAccessor<Integer> DATA_SWELL =
        SynchedEntityData.defineId(BeeperEntity.class, EntityDataSerializers.INT);

    private static final String TAG_COUNTER = "beeper_counter";
    private static final String TAG_FRIENDLY = "beeper_friendly_players";
    private static final String TAG_PROVOKED = "beeper_provoked";
    private static final String TAG_BEEPER = "jafa_beeper";

    private int counter = 4;
    private boolean provoked = false;
    private final List<UUID> friendlyPlayers = new ArrayList<>();
    /** 防重入：同一刻内多次右键喂花只处理一次，避免计数器一次被扣多次。 */
    private int lastFeedTick = -1;

    public BeeperEntity(EntityType<? extends Bee> type, Level level) {
        super(type, level);
        this.counter = 4 + this.random.nextInt(3); // 4~6
    }

    /** 初始化蜜蜂内部字段后清空所有原版目标：苦力蜂不自击/不蛰刺/不采蜜，行为全由 customServerAiStep 接管。 */
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.removeAllGoals(g -> true);
        this.targetSelector.removeAllGoals(g -> true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SWELL, 0);
    }

    public int getSwell() {
        return this.entityData.get(DATA_SWELL);
    }

    public void setSwell(int swell) {
        this.entityData.set(DATA_SWELL, swell);
    }

    public boolean isProvoked() {
        return this.provoked;
    }

    public int getCounter() {
        return this.counter;
    }

    /** 是否喂过该玩家花（友好），友好玩家永不被攻击/激怒。 */
    public boolean isFriendly(UUID uuid) {
        return this.friendlyPlayers.contains(uuid);
    }

    public boolean isFriendly(Player p) {
        return p != null && this.isFriendly(p.getUUID());
    }

    /** 判断玩家主/副手是否持花。 */
    public static boolean holdingFlower(Player p) {
        if (p == null) return false;
        return p.getMainHandItem().is(ItemTags.FLOWERS) || p.getOffhandItem().is(ItemTags.FLOWERS);
    }

    /** 该玩家是否为可被攻击的玩家：生存/冒险、未持花。（喂过花只降全局计数器，不立即豁免） */
    private boolean attackable(Player p) {
        if (p == null || p.isCreative() || p.isSpectator() || p.isDeadOrDying()) return false;
        return !holdingFlower(p);
    }

    /** 在 AGGRO_RANGE 内找最近的攻击目标玩家。 */
    private Player findTargetPlayer(ServerLevel level) {
        AABB box = this.getBoundingBox().inflate(AGGRO_RANGE);
        Player best = null;
        double bestDist = AGGRO_RANGE * AGGRO_RANGE;
        for (Player p : level.getEntitiesOfClass(Player.class, box)) {
            if (!this.attackable(p)) continue;
            double d = this.distanceToSqr(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    @Override
    protected void customServerAiStep() {
        if (this.level().isClientSide) return;
        ServerLevel level = (ServerLevel) this.level();

        // 蜂巢权杖吸收期间（截止刻内有效）：暂停本实体的一切追逐/自爆逻辑，让吸收流程完全接管机体移动
        //（否则两套速度互相覆盖，无法回收）。吸收停止后自然过期，本实体自动恢复追逐。
        if (this.getPersistentData().getInt(BeehiveStaffHelper.ABSORBING_TAG) > this.tickCount) {
            return;
        }

        // 目标优先级：蜂巢释放/被激怒的攻击目标（锁定并反击它）> 主动瞄准的可攻击玩家
        LivingEntity fuseTarget = this.assignedTarget(level);
        boolean assignedMode = fuseTarget != null;
        Player playerTarget = null;
        if (!assignedMode && this.counter > 0) {
            playerTarget = this.findTargetPlayer(level);
            fuseTarget = playerTarget;
        }

        if (fuseTarget != null) {
            double dist = this.distanceTo(fuseTarget);
            boolean toWither = fuseTarget instanceof net.minecraft.world.entity.boss.wither.WitherBoss;
            // 引爆距离：普通 3 格，凋灵 6 格；取消自爆距离：玩家目标 5 格/凋灵 10 格，蜂巢释放/激怒目标更宽容（16 格）
            double fuseDist = toWither ? 6.0 : FUSE_DISTANCE;
            double cancelDist;
            if (assignedMode) {
                cancelDist = toWither ? 10.0 : 16.0;
            } else {
                cancelDist = toWither ? 10.0 : 5.0;
            }
            // 用导航向目标逼近：导航负责生成绕障路径并在到点后自然减速停住（行为正常的关键）
            this.getNavigation().moveTo(fuseTarget, 1.0);
            // 非玩家目标：在导航路径方向上加速度（蜜蜂/苦力蜂的平滑 MoveControl 无视 moveTo 速度和任何速度属性，
            // 只能靠 setDeltaMovement 提速），同时进入引爆区前平滑刹车，避免冲过头迟迟不自爆
            if (assignedMode) {
                boostTowardTarget(fuseTarget, fuseDist);
            }
            if (dist > cancelDist) {
                // 目标过远：取消引燃，但仍持续逼近（避免"过远就不追"导致看起来没索敌）
                this.setSwell(0);
            } else if (dist <= fuseDist || this.getSwell() > 0) {
                // 在取消距离内：保持逼近；swell 一旦开始就持续累加（不再在中间区衰减），只到取消距离才归零
                int swell = this.getSwell();
                if (swell == 0) {
                    this.level().playSound(null, this.blockPosition(),
                        SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.0F, 1.0F);
                }
                this.setSwell(Math.min(MAX_SWELL, swell + 1));
            }
            // 介于引爆与取消之间且尚未引燃：保持逼近（moveTo 已处理）
        } else {
            // 无目标：引燃衰减 + 空闲漫游。
            // 原版蜜蜂的"到处乱飞"靠导航 goal 驱动，而 registerGoals 已清空所有 goal，super.customServerAiStep() 也不会触发，
            // 因此由本实体手动随机选点飞行，保证空闲时也到处乱飞。
            int s = this.getSwell();
            if (s > 0) this.setSwell(s - 1);
            this.idleWander();
        }

        // 引燃满：爆炸并消失
        if (this.getSwell() >= MAX_SWELL) {
            this.explode();
            return;
        }

        // 握花取消：仅对主动瞄准的玩家目标生效；蜂巢释放/被激怒的目标（被反击对象）不可用花取消
        if (!assignedMode && playerTarget != null && this.getSwell() > 0 && holdingFlower(playerTarget)) {
            this.setSwell(0);
            this.getNavigation().stop();
        }
    }

    /** 蜂巢权杖释放攻击目标的 UUID 持久化键。 */
    private static final String TAG_TARGET = "beeper_target_uuid";

    /** 解析蜂巢释放的攻击目标（持久化 UUID）；目标死亡/失效则清除。
     *  getEntity 暂时找不到（如区块未加载）时保留 UUID，下一刻再试，避免"瞬间失去索敌目标"。 */
    private LivingEntity assignedTarget(ServerLevel level) {
        net.minecraft.nbt.CompoundTag tag = this.getPersistentData();
        if (!tag.hasUUID(TAG_TARGET)) return null;
        UUID u = tag.getUUID(TAG_TARGET);
        Entity e = level.getEntity(u);
        if (e == null) {
            // 实体不在已加载实体表中：在 128 格范围内按 UUID 兜底搜索；仍找不到则保留，稍后再试
            AABB search = this.getBoundingBox().inflate(128.0);
            for (Entity candidate : level.getEntities(this, search)) {
                if (u.equals(candidate.getUUID())) {
                    e = candidate;
                    break;
                }
            }
            if (e == null) {
                return null; // 保留 uuid，不 remove
            }
        }
        // 永不索敌其它 beeper / 蜜蜂；目标死亡/失效则清除
        if (e instanceof Bee || !(e instanceof LivingEntity le) || !le.isAlive()) {
            tag.remove(TAG_TARGET);
            return null;
        }
        return (LivingEntity) e;
    }

    /** 苦力蜂不自击/蛰刺，只靠自爆造成伤害。 */
    @Override
    public boolean doHurtTarget(Entity target) {
        return false;
    }

    /** 非玩家索敌目标的追击（释放蜂群扑向敌人）速度：原版蜜蜂飞行速度的 2 倍（正常约 0.25 → 2 倍约 0.5 格/刻）。 */
    private static final double CHASE_BOOST = 0.5;

    /** 朝导航路径当前节点方向加速度（保留绕过障碍的寻路外观），并在进入引爆区前平滑刹车：
     *  <ul>
     *    <li>远处：全速追赶（setDeltaMovement）——蜜蜂/苦力蜂的平滑 MoveControl 无视 moveTo 速度和任何速度属性，只能靠直接设速度提速；</li>
     *    <li>距目标 < 引爆距离+1.5 时：速度随距离线性递减，到距目标 1.5 格内归零，改由导航自然减速停住，从而停在引爆距离内完成引燃爆炸。</li>
     *  </ul> */
    private void boostTowardTarget(LivingEntity target, double fuseDist) {
        Vec3 dest;
        Path path = this.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            dest = path.getNextEntityPos(this); // 沿导航路径节点飞 → 看起来在主动寻路
        } else {
            dest = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        }
        Vec3 to = dest.subtract(this.position());
        double d = to.length();
        double distToTarget = this.distanceTo(target);
        double boost = CHASE_BOOST;
        double brakeStart = fuseDist + 1.5;
        if (distToTarget < brakeStart) {
            // 平滑刹车：进入引爆区把速度线性降到 0
            boost = CHASE_BOOST * (Math.max(0.0, distToTarget - 1.5) / fuseDist);
        }
        if (d < 1.0E-3 || boost <= 0.0) {
            return;
        }
        Vec3 dir = to.scale(1.0 / d);
        // 蜂群散开：给每只苦力蜂一个固定的横向偏移，避免多只快速同向飞向同一目标时挤成一团。
        // 偏移方向/大小长期稳定（不逐刻抖动），从而各自飞行路径略呈扇形而非完全重叠。
        dir = dir.add(this.flockJitter(dir)).normalize();
        this.setDeltaMovement(dir.scale(boost));
    }

    /** 蜂群散开用的稳定横向偏移符号与大小（0 = 未初始化；否则为 ±1 与固定横向偏移量）。 */
    private double flockSign = 0.0;
    private double flockSpread = 0.0;

    /** 计算与飞行方向垂直的固定横向偏移（每只苦力蜂取一次随机左右和大小，之后恒定）。 */
    private Vec3 flockJitter(Vec3 forward) {
        if (this.flockSign == 0.0) {
            this.flockSpread = 0.12 + this.random.nextDouble() * 0.28; // 横向偏移量
            this.flockSign = this.random.nextBoolean() ? 1.0 : -1.0;   // 随机偏左或偏右
        }
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 perp = forward.cross(up);
        if (perp.lengthSqr() < 1.0E-8) {
            perp = new Vec3(1.0, 0.0, 0.0);
        } else {
            perp = perp.normalize();
        }
        return perp.scale(this.flockSign * this.flockSpread);
    }

    /** 空闲漫游：维持一个随机漫游目标，模拟蜜蜂到处乱飞。
     *  原版蜜蜂的空闲乱飞靠导航 goal 驱动，而 registerGoals 已清空所有 goal，故由本实体手动选点。
     *  每 5 刻评估一次；一旦当前路径到位（navigation.isDone）立刻换上行的新漫游点，
     *  这样生成后几乎立即开始乱飞，并凭上行的目标抵消重力、不会先下坠一段时间。 */
    private void idleWander() {
        if ((this.tickCount & 5) != 0) {
            return;
        }
        if (!this.getNavigation().isDone()) {
            return; // 仍在途中，保持当前方向
        }
        double ang = this.random.nextDouble() * Math.PI * 2.0;
        double r = 2.0 + this.random.nextDouble() * 8.0;
        double tx = this.getX() + Math.cos(ang) * r;
        double tz = this.getZ() + Math.sin(ang) * r;
        double ty = Math.max(this.level().getMinBuildHeight() + 1, this.getY() + this.random.nextDouble() * 2.0);
        this.getNavigation().moveTo(tx, ty, tz, 1.0);
    }

    private void explode() {
        if (this.level().isClientSide) return;
        this.dead = true;
        this.level().explode(this, this.getX(), this.getY(), this.getZ(),
            EXPLOSION_POWER, Level.ExplosionInteraction.MOB);
        this.discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 对爆炸伤害 90% 减伤
        if (source.is(net.minecraft.world.damagesource.DamageTypes.EXPLOSION)
            || source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_EXPLOSION)) {
            amount *= 0.1F;
        }
        if (!this.level().isClientSide && source.getEntity() instanceof LivingEntity la) {
            this.provokeBy(la);
        }
        return super.hurt(source, amount);
    }

    /** 被非友好方攻击：锁定攻击者为反击目标并激怒。可由 hurt() 或攻击事件监听调用。
     *  永不索敌其它苦力蜂或蜜蜂；若已有存活反击目标则保持原目标（不被其它攻击者反复换锁）。 */
    public void provokeBy(LivingEntity attacker) {
        if (this.level().isClientSide) return;
        // 永不索敌其它 beeper / 蜜蜂
        if (attacker instanceof Bee) return;
        if (attacker instanceof Player p && this.isFriendly(p)) return;
        net.minecraft.nbt.CompoundTag tag = this.getPersistentData();
        // 已锁定且目标仍存活：保持原目标
        if (tag.hasUUID(TAG_TARGET) && this.level() instanceof ServerLevel sl
            && sl.getEntity(tag.getUUID(TAG_TARGET)) instanceof LivingEntity existing && existing.isAlive()) {
            return;
        }
        this.provoked = true;
        tag.putUUID(TAG_TARGET, attacker.getUUID());
    }

    /** 用花右击：消耗花 + 计数器 -1 + 记为友好玩家（同一刻只处理一次）。 */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(ItemTags.FLOWERS)) {
            if (this.tickCount == this.lastFeedTick) {
                // 同刻内重复触发：忽略本次，避免计数器跳减多扣
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            this.lastFeedTick = this.tickCount;
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            this.counter--;
            if (!this.isFriendly(player)) {
                this.friendlyPlayers.add(player.getUUID());
            }
            this.level().playSound(null, this.blockPosition(),
                SoundEvents.BEE_POLLINATE, SoundSource.BLOCKS, 1.0F, 1.5F);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean(TAG_BEEPER, true);
        compound.putInt(TAG_COUNTER, this.counter);
        compound.putBoolean(TAG_PROVOKED, this.provoked);
        ListTag list = new ListTag();
        for (UUID u : this.friendlyPlayers) {
            CompoundTag e = new CompoundTag();
            e.putUUID("u", u);
            list.add(e);
        }
        compound.put(TAG_FRIENDLY, list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains(TAG_COUNTER, Tag.TAG_INT)) {
            this.counter = compound.getInt(TAG_COUNTER);
        }
        if (compound.contains(TAG_PROVOKED)) {
            this.provoked = compound.getBoolean(TAG_PROVOKED);
        }
        this.friendlyPlayers.clear();
        ListTag list = compound.getList(TAG_FRIENDLY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            if (e.hasUUID("u")) this.friendlyPlayers.add(e.getUUID("u"));
        }
    }
}