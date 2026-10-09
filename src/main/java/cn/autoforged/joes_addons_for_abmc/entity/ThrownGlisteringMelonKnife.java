package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 闪烁西瓜刀的投掷物。
 * <p>命中伤害与近战共用 {@link ModMain#computeKnifeDamage}（白板 10；亡灵为先线性追加亡灵杀手加成、再 ×20）。
 * <p>渲染见 {@code cn.autoforged.joes_addons_for_abmc.client.ThrownGlisteringMelonKnifeRenderer}：
 * 直接画物品模型本身，并让刀尖朝向飞行方向。
 *
 * <h2>落地：钉在原地，不再变成掉落物（同三叉戟）</h2>
 * 掷出后命中方块<b>不会</b>再吐出一把刀实体，而是像三叉戟那样<b>保持弹射物形态、插在命中的方块上</b>：
 * <ul>
 *   <li><b>命中即停</b>：实体挪到命中面外侧一点点（原版箭/三叉戟同款的 0.05 格退让）、速度清零，
 *       之后每刻只走 {@link #tickStuck()}（实体基础刻），不再走投掷物物理 —— 所以重力不会再把它
 *       从方块里拽下来，也不会再触发任何命中结算；</li>
 *   <li><b>走过去就能收回</b>：{@link #playerTouch} 把刀还进背包（背包满就留在原地等下次踩上来，
 *       不会掉在地上弄丢）。认主人：掷出者本人随时可捡；主人已死/离线/不是玩家时，谁都能捡；</li>
 *   <li><b>插住 {@link #STUCK_LIFETIME_TICKS} 刻</b>（5 分钟，与掉落物一致）后自动消失，不会无限堆积。
 *       （原版三叉戟插地是 1 分钟；这把刀比箭珍贵得多，所以按掉落物的时长来，改常量即可调整。）</li>
 * </ul>
 * <p>"插住"存的是<b>同步数据</b>（{@link #DATA_STUCK}）而不是普通字段：客户端据此立刻停手，
 * 不会自己按重力往下掉；服务端置位时顺带把数据标脏，本刻就把插住的位置同步过去。
 * <p><b>例外</b>：{@code creativeOnly} 的刀（创造模式玩家掷出的、以及附体空壳掷出的）落地照旧
 * <b>直接消失、不插地</b> —— 那些刀本来就是凭空掏出来的，插一地既会满地是刀，也能被玩家捡走刷物品。
 *
 * <h2>忠诚（Loyalty）</h2>
 * 与三叉戟同款：刀上带忠诚附魔时，<b>命中（生物或方块）之后会自动飞回掷出者</b>，
 * 而不是落地变成掉落物 —— 也就是"连实体本身送回玩家身边"。整套逻辑照搬原版 {@code ThrownTrident}：
 * <ul>
 *   <li>命中后进入返程：{@code noPhysics = true} 穿墙飞回，
 *       逐刻朝主人推进（推进量 = 剩余距离的 30%，夹在 0.4~2.0 格/刻），并播一次三叉戟回收音效；</li>
 *   <li>返程途中不再造成伤害（重写 {@link #canHitEntity}，等价于三叉戟的
 *       "命中过就不再 findHitEntity"）；</li>
 *   <li>贴到主人身边：玩家的刀直接进背包（装不下就掉在脚边），创造模式/非玩家主人直接消失；</li>
 *   <li>主人死亡或转旁观：按原版做法留在原地变成掉落物。</li>
 * </ul>
 * 带忠诚的刀<b>永远</b>不会插在地上（{@link #onHit} 里忠诚优先于插地判定）。
 */
public class ThrownGlisteringMelonKnife extends ThrowableItemProjectile {
    /**
     * "已经插住"的同步标记。
     * <p>用同步数据而不是普通字段，是为了让客户端也能在<b>自己没判定出命中</b>时（例如服务端先一步
     * 命中、或实体是读档出来的）立刻停止投掷物物理；置位方只有服务端，客户端永远不会自己乱改。
     */
    private static final EntityDataAccessor<Boolean> DATA_STUCK =
        SynchedEntityData.defineId(ThrownGlisteringMelonKnife.class, EntityDataSerializers.BOOLEAN);

    /** 插住之后存活多少刻再消失：6000 刻 = 5 分钟（与原版掉落物一致）。 */
    private static final int STUCK_LIFETIME_TICKS = 6000;

    /** 插进方块时沿来路退让的距离（格）：免得实体整个嵌进方块表面（原版箭/三叉戟同款 0.05）。 */
    private static final double STICK_BACKOFF = 0.05D;

    /** 判定"回到主人身上"的距离（格）：稍微放宽一点，免得擦身而过。 */
    private static final double ARRIVE_DISTANCE = 2.0D;

    private ItemStack thrownStack = ItemStack.EMPTY;
    private boolean creativeOnly = false;

    /** 忠诚等级（&gt;0 才会自动返回）：建实体时从刀栈上读，和原版三叉戟一致。 */
    private int loyalty;

    /** 是否已经命中过（命中之后才开始返程，也是"不再造成伤害"的开关）。 */
    private boolean dealtDamage;

    /** 返程音效是否已经播过。 */
    private boolean returnSoundPlayed;

    /** 插住之后过了多少刻（只服务端累加，到 {@link #STUCK_LIFETIME_TICKS} 就消失）。 */
    private int stuckTime;

    public ThrownGlisteringMelonKnife(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    public ThrownGlisteringMelonKnife(Level level, LivingEntity shooter, ItemStack stack) {
        super(ModEntities.THROWN_GLISTERING_MELON_KNIFE.get(), shooter, level);
        this.thrownStack = stack.copy();
        this.setItem(stack.copy());
        this.loyalty = loyaltyOf(level, stack, shooter);
    }

    public ThrownGlisteringMelonKnife(Level level, double x, double y, double z) {
        super(ModEntities.THROWN_GLISTERING_MELON_KNIFE.get(), x, y, z, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STUCK, false);
    }

    /**
     * 从刀栈上读忠诚等级。
     * <p>
     * 优先用原版三叉戟那条路（{@code getTridentReturnToOwnerAcceleration}，会走忠诚的
     * {@code trident_return_acceleration} 效果，数据包改了数值也能跟上）；它需要实体上下文，
     * 所以把掷出者传进去 —— <b>不能传 null</b>，效果结算会解引用它，一 NPE 就把"右键蓄力丢刀"
     * 整个搞挂（丢不出去就是这么来的）。
     * <p>
     * 万一这条路过不去（异常/拿不到注册表），退回"直接读栈上的附魔等级"，绝不因为读个等级
     * 就让丢刀失败。
     */
    private static int loyaltyOf(Level level, ItemStack stack,
                                 @Nullable net.minecraft.world.entity.Entity shooter) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel) || stack.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, net.minecraft.world.item.enchantment.EnchantmentHelper
                .getTridentReturnToOwnerAcceleration(serverLevel, stack, shooter));
        } catch (RuntimeException ignored) {
            // 退回最朴素的读法
        }
        try {
            var holder = serverLevel.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.LOYALTY);
            return Math.max(0, net.minecraft.world.item.enchantment.EnchantmentHelper
                .getItemEnchantmentLevel(holder, stack));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    /** 这把刀是不是带忠诚（调试/渲染用）。 */
    public boolean isLoyal() {
        return this.loyalty > 0;
    }

    /** 这把刀是不是已经插在地上/墙上了（渲染/调试用）。 */
    public boolean isStuck() {
        return this.getEntityData().get(DATA_STUCK);
    }

    /**
     * 把"插住"标记写进同步数据（只由服务端调用）。
     * <p>{@code hasImpulse = true} 是为了让 {@code ServerEntity} 本刻就走一次位置同步：
     * 客户端拿到的是服务端算好的插住位置，而不是它自己多飞了一刻的位置。
     */
    private void setStuck(boolean stuck) {
        this.getEntityData().set(DATA_STUCK, stuck);
        this.hasImpulse = true;
    }

    public void setCreativeOnly(boolean creative) {
        this.creativeOnly = creative;
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.GLISTERING_MELON_KNIFE.get();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.08;
    }

    /** 返程中的刀不再造成伤害（等价于原版三叉戟"命中过就不再找目标"）。 */
    @Override
    public boolean canHitEntity(Entity entity) {
        return !this.dealtDamage && super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        // 投掷伤害与近战同一套公式（满蓄力、不暴击）：白板 10；亡灵为先线性追加亡灵杀手加成、再 ×20
        float damage = ModMain.computeKnifeDamage(this.getItem(),
            target instanceof LivingEntity living ? living : null, 1.0F, false, this.level());
        target.hurt(this.damageSources().thrown(this, owner != null ? owner : this), damage);
        this.dealtDamage = true;   // 打中目标 → 带忠诚的话从这里开始返程
    }

    @Override
    protected void onHit(HitResult result) {
        if (this.isStuck()) {
            return;   // 已经插住：不再参与任何命中结算（防重入，也免得反复播插地音效）
        }
        // 先让原版的命中结算照走：伤害/方块效果都在 super.onHit 里分派给 onHitEntity/onHitBlock。
        // （早先这里一发现带忠诚就直接 return，等于把这一击的伤害吞了 —— 现在先 super 再判断。）
        super.onHit(result);
        if (this.level().isClientSide) {
            return;   // 插地/掉落的决定权只在服务端，客户端等同步数据
        }
        // 带忠诚的刀：命中生物或方块都转入返程，永远不会落地变成掉落物
        if (this.loyalty > 0) {
            this.dealtDamage = true;
            return;
        }
        // 空壳/创造模式掷出的刀是"凭空掏出来的"：落地照旧直接消失，不插在地上
        if (this.creativeOnly) {
            this.discard();
            return;
        }
        if (result.getType() == HitResult.Type.BLOCK) {
            if (this.pickupStack().isEmpty()) {
                this.discard();   // 连刀栈都空了（理论上不该发生）：照旧清掉，免得留个捡不起来也画不出来的实体
                return;
            }
            this.stick(result);
        }
        // 命中生物：刀照旧继续飞（canHitEntity 已经关掉后续伤害），落地时再插住
    }

    /**
     * 把刀钉在命中的方块表面上（同三叉戟插地）。
     * <p>位置取命中点沿来路退让 {@link #STICK_BACKOFF} 格，这样实体中心落在方块表面外侧、
     * 不会有一半埋进方块里；速度清零后就不再走投掷物物理，刀自然停在那儿。
     */
    private void stick(HitResult result) {
        Vec3 hit = result.getLocation();
        Vec3 motion = this.getDeltaMovement();
        Vec3 pos = hit;
        if (motion.lengthSqr() > 1.0E-7) {
            pos = hit.subtract(motion.normalize().scale(STICK_BACKOFF));
        }
        this.setPos(pos.x, pos.y, pos.z);   // 用 setPos 而不是 setPosRaw：碰撞箱要跟着一起挪，玩家才能踩到它
        this.setDeltaMovement(Vec3.ZERO);
        this.stuckTime = 0;
        this.setStuck(true);
        this.playSound(SoundEvents.TRIDENT_HIT_GROUND, 1.0F, 1.0F);   // "插进地里"的那一下闷响
    }

    @Override
    public void tick() {
        // 已经插住：只走实体基础刻（计时/着火/入水标记），不再走投掷物物理
        if (this.isStuck()) {
            this.tickStuck();
            return;
        }
        if (!this.level().isClientSide && this.loyalty > 0 && this.dealtDamage) {
            if (!this.tickLoyalReturn()) {
                return;   // 已经回到主人身上/主人没了：本刻结束，不再走原版逻辑
            }
        }
        super.tick();
    }

    /**
     * 插住之后的一刻：实体基础刻 + 存活倒计时。
     * <p>刻意不调 {@code super.tick()}（那会跑投掷物物理：重力 → 从方块里滑下来 + 再次命中方块）。
     */
    private void tickStuck() {
        this.baseTick();
        if (this.level().isClientSide) {
            return;
        }
        this.stuckTime++;
        if (this.stuckTime >= STUCK_LIFETIME_TICKS) {
            this.discard();
        }
    }

    /**
     * 玩家踩到插住的刀 → 把刀收进背包（原版三叉戟就是这么捡的）。
     * <p>只管插住的刀：飞行中的刀不认（免得飞过去擦到人就凭空回手）。
     */
    @Override
    public void playerTouch(Player player) {
        if (this.level().isClientSide || !this.isStuck() || this.creativeOnly) {
            return;
        }
        if (!this.canBeRetrievedBy(player)) {
            return;
        }
        ItemStack stack = this.pickupStack();
        if (stack.isEmpty()) {
            this.discard();
            return;
        }
        if (!player.getInventory().add(stack)) {
            return;   // 背包满了：留在原地，下次踩上来再捡（掉在地上反而会丢）
        }
        player.take(this, 1);
        this.discard();
    }

    /**
     * 谁能把插住的刀捡回去：掷出者本人随时可以；主人已死/离线/不是玩家（例如空壳的非 creativeOnly 刀）
     * 时，谁都能捡 —— 免得一把刀插在那儿谁也拿不走。
     */
    private boolean canBeRetrievedBy(Player player) {
        Entity owner = this.getOwner();
        return owner == null || owner == player || !(owner instanceof Player) || !owner.isAlive();
    }

    /** 该还给玩家/该掉出来的那把刀：优先用实体身上那份（耐久可能已经掉过），退而用建实体时的备份。 */
    private ItemStack pickupStack() {
        ItemStack stack = this.getItem().copy();
        if (stack.isEmpty() && !this.thrownStack.isEmpty()) {
            stack = this.thrownStack.copy();
        }
        return stack;
    }

    /**
     * 忠诚返程的一刻。
     *
     * @return true = 返程还在继续（调用方接着走 {@code super.tick()}）；false = 这件事已经了结
     */
    private boolean tickLoyalReturn() {
        Entity owner = this.getOwner();
        boolean ownerGone = owner == null || !owner.isAlive()
            || (owner instanceof net.minecraft.world.entity.player.Player player && player.isSpectator());
        if (ownerGone) {
            // 和原版三叉戟一样：主人没了就留在原地变成掉落物
            this.noPhysics = false;
            if (!creativeOnly) {
                ItemStack toDrop = this.pickupStack();
                if (!toDrop.isEmpty()) {
                    this.spawnAtLocation(toDrop, 0.1F);
                }
            }
            this.discard();
            return false;
        }

        // 1.21 的这个字段是 public 的（没有 setNoPhysics 方法）：置真后 Entity#move 直接跳过碰撞
        this.noPhysics = true;
        Vec3 toOwner = owner.getEyePosition().subtract(this.position());
        double distance = toOwner.length();
        if (distance < ARRIVE_DISTANCE) {
            this.giveBackTo(owner);
            return false;
        }
        if (!this.returnSoundPlayed) {
            this.playSound(net.minecraft.sounds.SoundEvents.TRIDENT_RETURN, 1.0F, 1.0F);
            this.returnSoundPlayed = true;
        }

        // 返程推进：按"剩余距离的一定比例"走一步，并夹在 [0.4, 2.0] 格/刻之间。
        // 原版三叉戟用的是"每刻 +0.05 格/刻²、速度 ×0.95"的加速写法，那套在 0.99 的摩擦下
        // 终速能到 5 格/刻，很容易一帧跨过主人、在两边来回晃（表现就是"只听到回收音效、刀没回来"）。
        // 这里改成按剩余距离插值：越近步子越小，必定停进 ARRIVE_DISTANCE，不会冲过头。
        double step = net.minecraft.util.Mth.clamp(distance * 0.3D, 0.4D, 2.0D);
        this.setDeltaMovement(toOwner.normalize().scale(step));
        return true;
    }

    /** 回到主人身上：玩家的刀进背包（装不下就掉在脚边），其它情况直接消失。 */
    private void giveBackTo(Entity owner) {
        ItemStack stack = this.pickupStack();
        if (!creativeOnly && !stack.isEmpty() && owner instanceof net.minecraft.world.entity.player.Player player
            && !player.getAbilities().instabuild && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        this.discard();
    }

    /**
     * 存档：插住状态、计时、忠诚等级、掷出者是不是"凭空掏的"等都要存。
     * <p>以前这个类不存档，飞行中的刀一读档就退化成"普通投掷物"（忠诚没了）；现在刀会长时间留在
     * 世界里（插在地上等回收），区块卸载/读档后必须原样恢复，否则会从地里滑出来、或者忠诚刀不回家。
     */
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("KnifeStuck", this.isStuck());
        compound.putInt("KnifeStuckTime", this.stuckTime);
        compound.putBoolean("KnifeDealtDamage", this.dealtDamage);
        compound.putBoolean("KnifeCreativeOnly", this.creativeOnly);
        compound.putBoolean("KnifeReturnSound", this.returnSoundPlayed);
        compound.putByte("KnifeLoyalty", (byte) Math.min(127, this.loyalty));
        if (!this.thrownStack.isEmpty()) {
            compound.put("KnifeStack", this.thrownStack.save(this.registryAccess()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.getEntityData().set(DATA_STUCK, compound.getBoolean("KnifeStuck"));
        this.stuckTime = compound.getInt("KnifeStuckTime");
        this.dealtDamage = compound.getBoolean("KnifeDealtDamage");
        this.creativeOnly = compound.getBoolean("KnifeCreativeOnly");
        this.returnSoundPlayed = compound.getBoolean("KnifeReturnSound");
        if (compound.contains("KnifeLoyalty")) {
            this.loyalty = compound.getByte("KnifeLoyalty");
        } else {
            // 旧存档（本版之前掷出的刀）没有这个字段：按原版三叉戟的做法从刀栈上重算
            this.loyalty = loyaltyOf(this.level(), this.getItem(), this.getOwner());
        }
        if (compound.contains("KnifeStack", 10)) {
            this.thrownStack = ItemStack.parse(this.registryAccess(), compound.getCompound("KnifeStack"))
                .orElse(ItemStack.EMPTY);
        }
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }
}
