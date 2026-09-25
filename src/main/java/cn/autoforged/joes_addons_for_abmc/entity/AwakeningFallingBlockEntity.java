package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * 觉醒药水专用的下落方块实体。方块先被短暂“唤起”：原地绕着「玩家视线在水平面上的投影」方向
 * 快速左右小幅摆动 1.5 秒（30 刻），随后向上弹起再落回地面，落地后保持实体状态（不再变回方块）。
 * 唤起时捕获原方块的 BlockEntity（含箱/木桶内容物），避免移除方块时掉落内容物。
 */
public class AwakeningFallingBlockEntity extends FallingBlockEntity {

    private static final org.slf4j.Logger LOGGER =
        org.slf4j.LoggerFactory.getLogger("ABMC-AssistDebug");

    /** 摆动持续时长（刻）：2.5 秒=50，1.5 秒=30。 */
    public static final int WOBBLE_DURATION_TICKS = 30;

    private static final EntityDataAccessor<Float> DATA_WOBBLE_DIR_X =
        SynchedEntityData.defineId(AwakeningFallingBlockEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_WOBBLE_DIR_Z =
        SynchedEntityData.defineId(AwakeningFallingBlockEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_CREATION_TICK =
        SynchedEntityData.defineId(AwakeningFallingBlockEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_AWAKENING_LANDED =
        SynchedEntityData.defineId(AwakeningFallingBlockEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CHEST_OPEN =
        SynchedEntityData.defineId(AwakeningFallingBlockEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_HOP_DIR_X =
        SynchedEntityData.defineId(AwakeningFallingBlockEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HOP_DIR_Z =
        SynchedEntityData.defineId(AwakeningFallingBlockEntity.class, EntityDataSerializers.FLOAT);
    /** 酿造台 0..2 槽的药水（含 potion_contents），同步给客户端渲染瓶身。 */
    private static final EntityDataAccessor<CompoundTag> DATA_BREW_BOTTLES =
        SynchedEntityData.defineId(AwakeningFallingBlockEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private BlockState myBlockState = Blocks.STONE.defaultBlockState();
    @Nullable
    public CompoundTag myBlockData;
    private boolean awakeningLanded = false;
    /** 起跳前还需在原地摆动的剩余刻数（服务器端计时）。 */
    private int wobbleRemaining = WOBBLE_DURATION_TICKS;
    /** 蹦跳冷却：落地后两次起跳之间的间隔刻数（史莱姆式一跳一跳）。 */
    private int hopCooldown = 0;
    /** 觉醒倒计时（刻）：3分钟=3600，延长版8分钟=9600；到期后固化回方块。 */
    private int solidifyTicks = 3600;
    private boolean solidified = false;
    /** 斧子击败机制：累计有效攻击次数。 */
    private int axeAttackCount = 0;
    /** 上次斧子攻击的游戏刻时间（-1 表示尚未被攻击过）。 */
    private long lastAxeAttackTime = -1L;
    /** 寻路结果：从当前格到目标附近的可站立路径点（仅服务器用）。 */
    private final List<BlockPos> hopPath = new ArrayList<>();
    private int hopPathIndex = 0;
    private int pathRefreshTimer = 0;

    public AwakeningFallingBlockEntity(EntityType<? extends AwakeningFallingBlockEntity> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
        this.dropItem = false;
        this.disableDrop();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WOBBLE_DIR_X, 0.0F);
        builder.define(DATA_WOBBLE_DIR_Z, 0.0F);
        builder.define(DATA_CREATION_TICK, 0);
        builder.define(DATA_AWAKENING_LANDED, false);
        builder.define(DATA_CHEST_OPEN, false);
        builder.define(DATA_HOP_DIR_X, 0.0F);
        builder.define(DATA_HOP_DIR_Z, 0.0F);
        builder.define(DATA_BREW_BOTTLES, new CompoundTag());
    }

    public void initFromBlock(Level level, BlockPos pos, BlockState state) {
        this.myBlockState = state.hasProperty(BlockStateProperties.WATERLOGGED)
            ? state.setValue(BlockStateProperties.WATERLOGGED, false)
            : state;
        this.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.setStartPos(pos);
        // 捕获方块 BlockEntity（含箱/木桶等内容物）并先从方块上解绑，避免 removeBlock 掉落内容物
        this.myBlockData = cn.autoforged.joes_addons_for_abmc.ModMain.captureBlockEntityData(level, pos, state);
        level.removeBlock(pos, false);
        // 记录创建时刻（供客户端渲染摆动动画）
        this.entityData.set(DATA_CREATION_TICK, (int) level.getGameTime());
    }

    public void setWobbleDir(float ax, float az) {
        this.entityData.set(DATA_WOBBLE_DIR_X, ax);
        this.entityData.set(DATA_WOBBLE_DIR_Z, az);
    }

    public float getWobbleDirX() {
        return this.entityData.get(DATA_WOBBLE_DIR_X);
    }

    public float getWobbleDirZ() {
        return this.entityData.get(DATA_WOBBLE_DIR_Z);
    }

    public int getCreationTick() {
        return this.entityData.get(DATA_CREATION_TICK);
    }

    public boolean isAwakeningLanded() {
        return this.entityData.get(DATA_AWAKENING_LANDED);
    }

    public void setAwakeningLanded(boolean landed) {
        this.entityData.set(DATA_AWAKENING_LANDED, landed);
    }

    public boolean isChestOpen() {
        return this.entityData.get(DATA_CHEST_OPEN);
    }

    public void setChestOpen(boolean open) {
        this.entityData.set(DATA_CHEST_OPEN, open);
    }

    /** 返回酿造台 0..2 槽药水（客户端渲染瓶身用）；空酿造台返回空 CompoundTag。 */
    public CompoundTag getBrewBottles() {
        return this.entityData.get(DATA_BREW_BOTTLES);
    }

    /** 把酿造台 0..2 槽的药水（含 potion_contents，保留药水颜色）同步给客户端。 */
    public void syncBrewBottles() {
        CompoundTag bottles = new CompoundTag();
        if (this.myBlockData != null) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            net.minecraft.nbt.ListTag items = getOrCreateItems(this.myBlockData);
            for (int i = 0; i < items.size(); i++) {
                CompoundTag it = items.getCompound(i);
                int slot = it.getInt("Slot");
                if (slot >= 0 && slot <= 2) list.add(it.copy());
            }
            bottles.put("Bottles", list);
        }
        this.entityData.set(DATA_BREW_BOTTLES, bottles);
    }

    public float getHopDirX() {
        return this.entityData.get(DATA_HOP_DIR_X);
    }

    public float getHopDirZ() {
        return this.entityData.get(DATA_HOP_DIR_Z);
    }

    public void setHopDir(float ax, float az) {
        this.entityData.set(DATA_HOP_DIR_X, ax);
        this.entityData.set(DATA_HOP_DIR_Z, az);
    }

    @Override
    public BlockState getBlockState() {
        // 酿造台始终显示空瓶贴图，不渲染药水
        if (this.myBlockState.is(Blocks.BREWING_STAND)) {
            return this.myBlockState
                .setValue(BrewingStandBlock.HAS_BOTTLE[0], false)
                .setValue(BrewingStandBlock.HAS_BOTTLE[1], false)
                .setValue(BrewingStandBlock.HAS_BOTTLE[2], false);
        }
        return this.myBlockState;
    }

    // Jade/WAILA 等查看实体时显示“唤醒的XX”而非父类 FallingBlockEntity 的“下落的XX”
    @Override
    protected Component getTypeName() {
        return Component.translatable(
            "entity.joes_addons_for_abmc.awakening_falling_block_type",
            this.getBlockState().getBlock().getName());
    }

    // 1×1×1 的碰撞箱（贴合一个方块），使其与真实方块/其他实体碰撞形态一致
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(1.0F, 1.0F);
    }

    // 可被推送，从而参与实体间碰撞检测（避免相互重叠）
    @Override
    public boolean isPushable() {
        return true;
    }

    // 不阻挡任何弹射物（箭/药水/火球等直接穿过）
    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    // 必须可被选取/攻击，否则玩家近战（斧子）无法命中断言 hurt() 逻辑
    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    /**
     * 斧子击败机制：玩家手持任意斧子近战攻击本组合体，累计 7 次且每次间隔在 1s~10s 之间，
     * 即可使其提前固化（击败）。每次有效攻击播放“箱子：上锁”音效，无效（间隔过短/过长）则
     * 播放“箱子：关闭”音效并重置攻击计数。已固化的组合体不再响应斧子攻击。
     */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (this.solidified || this.level().isClientSide) return false;
        if (!(source.getDirectEntity() instanceof Player player)) return false;

        net.minecraft.world.item.ItemStack weapon = player.getMainHandItem();
        if (!(weapon.getItem() instanceof net.minecraft.world.item.AxeItem)) return false;

        long currentTime = this.level().getGameTime();
        // 首次攻击直接有效；此后间隔必须 >=1s(20刻) 且 <10s(200刻)
        boolean valid = this.lastAxeAttackTime < 0;
        if (!valid) {
            long interval = currentTime - this.lastAxeAttackTime;
            valid = interval >= 20 && interval < 200;
        }
        this.lastAxeAttackTime = currentTime;

        if (valid) {
            this.axeAttackCount++;
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                net.minecraft.sounds.SoundEvents.CHEST_LOCKED,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            if (this.axeAttackCount >= 7) {
                this.solidify();
            }
        } else {
            this.axeAttackCount = 0;
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                net.minecraft.sounds.SoundEvents.CHEST_CLOSE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return true;
    }

    @Override
    public void push(Entity entity) {
        // 只与同类觉醒方块互斥；若重叠则相互推开，避免堆叠穿透
        if (entity instanceof AwakeningFallingBlockEntity) {
            double dx = entity.getX() - this.getX();
            double dz = entity.getZ() - this.getZ();
            double dist2 = dx * dx + dz * dz;
            if (dist2 < 0.64 && dist2 > 1.0E-6) {
                double dist = Math.sqrt(dist2);
                double push = (0.4 - dist) * 0.5;
                double nx = dx / dist * push;
                double nz = dz / dist * push;
                entity.setPos(entity.getX() + nx, entity.getY(), entity.getZ() + nz);
                this.setPos(this.getX() - nx, this.getY(), this.getZ() - nz);
            }
        } else {
            super.push(entity);
        }
    }

    @Override
    public void tick() {
        if (this.myBlockState.isAir()) {
            this.discard();
            return;
        }

        // 客户端同步模拟：落地蹦跳阶段也按同样的「重力 + 滞空恒速水平」物理运动，保证渲染平滑
        if (this.level().isClientSide) {
            if (this.isAwakeningLanded()) {
                this.applyGravity();
                this.move(MoverType.SELF, this.getDeltaMovement());
                if (!this.onGround()) {
                    float hx = this.getHopDirX();
                    float hz = this.getHopDirZ();
                    if (hx != 0.0F || hz != 0.0F) {
                        this.setDeltaMovement((double) hx * JUMP_SPEED_H, this.getDeltaMovement().y, (double) hz * JUMP_SPEED_H);
                    }
                }
            }
            return;
        }

        // 觉醒倒计时：到期后寻找最近的合法坐标固化回方块
        if (this.solidifyTicks > 0) {
            if (--this.solidifyTicks <= 0) {
                this.solidify();
                return;
            }
        }

        // 落地后：进入史莱姆式蹦跳 AI，一跳一跳地靠近最近的玩家
        if (this.awakeningLanded) {
            doSlimeHop();
            return;
        }

        // 摆动阶段：原地保持到计时结束再起跳
        if (this.wobbleRemaining > 0) {
            this.wobbleRemaining--;
            if (this.wobbleRemaining > 0) {
                return;
            }
            // 计时结束：给予较小的向上初速度起跳
            this.setDeltaMovement(this.getDeltaMovement().x, 0.25, this.getDeltaMovement().z);
        }

        this.applyGravity();
        this.move(MoverType.SELF, this.getDeltaMovement());

        if (this.onGround() || this.horizontalCollision) {
            // 落地后不再放置回方块，而是作为实体在地面开始蹦跳
            this.awakeningLanded = true;
            this.setAwakeningLanded(true);
            this.hopCooldown = 0;
            this.setDeltaMovement(Vec3.ZERO);
            this.setNoGravity(false);
            return;
        }

        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
    }

    // 侦测玩家的水平范围、垂直允许高度差
    private static final int HOP_TARGET_RANGE = 16;
    private static final double HOP_TARGET_MAX_DY = 6.0;
    // 距离玩家小于等于5格时停止跟随
    private static final double STOP_BLOCKS = 5.0;
    // 越障时用的向上初速（约1.2格高，前次1.5倍过高已回退），默认低跳初速
    private static final double JUMP_UP_OBSTACLE = 0.31;
    private static final double JUMP_UP_DEFAULT = 0.21;
    // 起跳水平速度与两跳间隔刻数（间隔更短使连续弹跳更平滑）
    private static final double JUMP_SPEED_H = 0.18;
    private static final int HOP_COOLDOWN_TICKS = 5;
    // 宠物传送：距玩家超过该距离且玩家站在地面时，传送到玩家身边
    private static final double PET_TELEPORT_DIST = 15.0;
    // 配对：箱子和酿造台在 32 格内互相检测到对方则尝试互相匹配
    public static final int PAIR_DIST = 32;
    /** 酿造台骑乘后向下渲染的像素数（16 像素/格）。 */
    public static final int PAIR_RIDER_DOWN_PIXELS = 5;
    /** 配对时把箱子标记为“打开”的持久数据键。 */
    public static final String PAIR_CHEST_OPEN_TAG = "jafa_awaken_pair_chest_open";
    /** 抛掷掉落物身上记录的"应装入的酿造台槽位"键（仅药水需要；烈焰粉不写）。 */
    public static final String THROW_SLOT_TAG = "jafa_throw_slot";
    /** 酿造原料掉落物身上记录的酿造类型键：0=基础(粗制)，1=传送，2=缠魂，3=解药。 */
    public static final String BREW_TYPE_TAG = "jafa_brew_type";
    /** 配对搭档的实体 id，-1=未配对。 */
    private int partnerId = -1;
    /** 由箱子抛出的、待被酿造台“吸收”的掉落物实体 id 列表（仅服务器）。 */
    private final java.util.List<Integer> thrownItemIds = new java.util.ArrayList<>();
    /** 自动装填节流：每 N 刻跑一次 doPairRefill。 */
    private int refillCooldown = 0;
    public static final int REFILL_INTERVAL = 10;
    /** 主人“靠得比较近”的判定距离（格）。 */
    private static final double CLOSE_OWNER_DIST = 6.0;
    /** 给主人丢/施用药水的节流间隔（刻）。 */
    private static final int OWNER_ASSIST_INTERVAL = 20;
    /** 命名纸指定的目标坐标（null=未指定，默认靠近主人/玩家）。 */
    private BlockPos homePos = null;
    /** 是否已初次到达跟随目标（用于“装好药水后停车”判定）。 */
    private boolean firstArrivalDone = false;
    /** 给主人丢/施用药水的节流计数。 */
    private int ownerAssistCooldown = 0;
    /** 援助模式：0=友善（给主人增益），1=敌对（给敌人减益/变形），2=无（仅跟随，不给药）。 */
    private int assistMode = 0;
    /** 敌对模式下上次锁定的敌人 UUID（持久记忆，避免 100tick 超时丢目标）。 */
    private java.util.UUID lastEnemyUuid = null;
    /** 敌对模式下因超距（>30格）传送回来而放弃的旧敌人 UUID，等待主人出现新目标后解除。 */
    private java.util.UUID lostEnemyUuid = null;
    /** 敌人被变形为方块/物品时记录其原始 UUID，待恢复后继续索敌。 */
    private java.util.UUID waitingEnemyUuid = null;
    /** 敌人被变形为方块/物品时的位置（UUID 可能变化，用位置兜底扫描）。 */
    private BlockPos waitingEnemyPos = null;
    /** 自动酿造调度计数器：0=基础(粗制)，1=传送，2=缠魂，3=解药（四类交替循环）。 */
    private int brewSchedule = 0;
    /** 自动酿造节流冷却。 */
    private int advancedBrewCooldown = 0;
    /** 物品拾取冷却。 */
    private int pickupCooldown = 0;

    /**
     * 史莱姆式蹦跳：朝向最近的玩家（水平 16 格内、高度差不超过 6 格），落地且冷却结束后
     * 沿面向方向跳起并施加水平速度，形成「一跳一跳地靠近玩家」的效果。
     * 空中阶段保持水平速度恒定（不施加水平阻尼，也不在中途清零），确保越障与平滑移动；
     * 距玩家 3 格内时停止并清零速度。
     */
    private void doSlimeHop() {
        // 配对：等「抖动动画完成后」才进行匹配（箱子/酿造台互相检测并匹配）
        if (this.level() instanceof ServerLevel && this.wobbleRemaining <= 0) {
            pairTick();
        }
        // 每 5 秒同步一次箱子的 myBlockData，防止玩家替换箱子后数据不同步
        if (this.isChest() && this.level() instanceof ServerLevel && this.tickCount % 100 == 0) {
            syncChestData();
        }
        // 箱子侧自动装填：若已是箱子且搭档为酿造台（燃料自限 + 节流，避免每刻重复）
        if (this.isChest() && this.isPaired() && this.level() instanceof ServerLevel) {
            Entity partner = ((ServerLevel) this.level()).getEntity(this.partnerId);
            if (partner instanceof AwakeningFallingBlockEntity pb && pb.isBrewingStand()) {
                this.tickThrownItems(pb);
                if (this.refillCooldown <= 0) {
                    this.doPairRefill(pb);
                    this.refillCooldown = REFILL_INTERVAL;
                } else {
                    this.refillCooldown--;
                }
            }
        }
        // 主人为女巫Boss时自动切换援助模式（友好/敌对，永不"无"模式）
        if (this.isChest() && this.isPaired() && this.level() instanceof ServerLevel
                && this.tickCount % 20 == 0) {
            updateWitchBossAssistMode();
        }
        // 主人援助：给召唤者丢/施用药水（仅箱子、已配对且有主人时，节流执行）
        if (this.isChest() && this.isPaired() && this.level() instanceof ServerLevel) {
            if (this.ownerAssistCooldown <= 0) {
                this.doOwnerAssist();
                this.ownerAssistCooldown = OWNER_ASSIST_INTERVAL;
            } else {
                this.ownerAssistCooldown--;
            }
        }
        // 全模式自动酿造：消耗箱子物品酿造传送药水/变形解药（1:2 比例，不受 assistMode 影响）
        if (this.isChest() && this.isPaired() && this.level() instanceof ServerLevel) {
            if (this.advancedBrewCooldown <= 0) {
                if (this.tickAdvancedBrewing()) {
                    this.advancedBrewCooldown = 1;
                }
                // 返回 false 时不重置冷却，下一 tick 继续尝试
            } else {
                this.advancedBrewCooldown--;
            }
        }
        // 物品拾取：扫描附近 ItemEntity 并收纳到箱子
        if (this.isChest() && this.level() instanceof ServerLevel) {
            if (this.pickupCooldown <= 0) {
                this.pickupNearbyItems();
                this.pickupCooldown = 10; // 每 0.5 秒扫描一次
            } else {
                this.pickupCooldown--;
            }
        }
        // 敌对模式视觉反馈：火焰粒子
        if (this.assistMode == 1 && this.level() instanceof ServerLevel sl && this.tickCount % 10 == 0) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                this.getX(), this.getY() + 1.0, this.getZ(),
                1, 0.2, 0.2, 0.2, 0.0);
        }
        // 敌对模式：距主人超过 30 格则传送回主人身边并失去索敌
        maybeTeleportBackToOwnerHostile();
        // 跟随目标：敌对模式跟随索敌目标，无/友好模式优先命名纸坐标，其次主人，再其次最近玩家
        net.minecraft.world.phys.Vec3 follow = followPos();
        // 面向跟随目标（即使（酿造台）已骑乘也保持面向它）
        if (follow != null) {
            this.setYRot((float) (Mth.atan2(
                follow.z - this.getZ(), follow.x - this.getX()) * (180.0 / Math.PI) - 90.0));
        }
        // 已骑乘（酿造台骑在箱子上）：自身不再移动，跟随载具
        if (this.isPassenger()) {
            return;
        }
        this.applyGravity();
        this.move(MoverType.SELF, this.getDeltaMovement());
        // 服务端主动把重叠的同类觉醒方块硬性分开，保证相互不叠（潜影贝式硬碰撞）
        separateFromOthers();
        // 若被卡进实心方块，就近拽到空气格，避免卡墙
        resolveStuckInBlocks();
        // 宠物能力：离玩家太远且玩家在地面时传送到玩家身边（不发传送音效）
        maybeTeleportToPlayer();

        if (this.pathRefreshTimer > 0) {
            this.pathRefreshTimer--;
        }

        if (follow == null) {
            this.clearHop();
            return;
        }

        if (this.onGround()) {
            if (this.distanceToSqr(follow.x, this.getY(), follow.z) <= STOP_BLOCKS * STOP_BLOCKS) {
                // 达到目标 5 格内：停止跟随并清零速度，记录已初次抵达
                this.firstArrivalDone = true;
                this.clearHop();
                this.hopCooldown = 0;
                return;
            }
            if (this.hopCooldown > 0) {
                this.hopCooldown--;
                this.clearHop();
                return;
            }

            // 目标瞄准点：正前方无障碍时直接朝目标直线移动；被挡（如贴墙/遇障碍）时才用寻路绕行
            double rawDx = follow.x - this.getX();
            double rawDz = follow.z - this.getZ();
            double directLen = Math.hypot(rawDx, rawDz);
            boolean blockedDirect = directLen > 1.0E-3
                && isOneBlockObstacleAhead(rawDx / directLen, rawDz / directLen);
            Vec3 aim;
            if (!blockedDirect) {
                this.hopPath.clear();
                this.hopPathIndex = 0;
                aim = follow;
            } else {
                aim = getNextWaypoint(follow);
            }
            double dx = aim.x - this.getX();
            double dz = aim.z - this.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-4) {
                dx = 0.0;
                dz = 0.0;
            } else {
                dx /= len;
                dz /= len;
            }

            this.hopCooldown = HOP_COOLDOWN_TICKS;
            this.setYRot((float) (Mth.atan2(dz, dx) * (180.0 / Math.PI) - 90.0));
            // 记录并同步本次蹦跳的水平方向（供滞空重施与客户端同步模拟）
            this.setHopDir((float) dx, (float) dz);
            // 前方被 1 格高障碍挡住时提高跳跃以越障，否则用默认的低跳初速
            double up = isOneBlockObstacleAhead(dx, dz) ? JUMP_UP_OBSTACLE : JUMP_UP_DEFAULT;
            this.setDeltaMovement(dx * JUMP_SPEED_H, up, dz * JUMP_SPEED_H);
        } else if (this.hopCooldown > 0) {
            this.hopCooldown--;
        }

        // 滞空时持续强制水平速度：即使被墙体碰撞清零，也会在下一次滞空重施，确保能越障且不滞空停摆
        float hx = this.getHopDirX();
        float hz = this.getHopDirZ();
        if (hx != 0.0F || hz != 0.0F) {
            this.setDeltaMovement((double) hx * JUMP_SPEED_H, this.getDeltaMovement().y, (double) hz * JUMP_SPEED_H);
        }
    }

    /** 清理蹦跳方向并清零速度。 */
    private void clearHop() {
        this.setHopDir(0.0F, 0.0F);
        this.setDeltaMovement(Vec3.ZERO);
    }

    private boolean isChest() {
        return this.myBlockState.is(Blocks.CHEST);
    }

    /** 取容器 NBT 的 Items 列表；不存在则创建并挂接。Items 是 ListTag（类型9），勿用 contains(...,10) 判定。 */
    private net.minecraft.nbt.ListTag getOrCreateItems(CompoundTag data) {
        if (data.get("Items") instanceof net.minecraft.nbt.ListTag list) {
            return list;
        }
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        data.put("Items", list);
        return list;
    }

    private boolean isBrewingStand() {
        return this.myBlockState.is(Blocks.BREWING_STAND);
    }

    public boolean isPaired() {
        return this.partnerId >= 0;
    }

    /**
     * 箱子和酿造台互相配对：32 格内互相检测到对方则匹配。匹配后：
     * 双方锁定（不再与其他方块匹配）、倒计时同步为更长的一方、酿造台骑乘箱子、
     * 箱子标记为“打开”。由 entityId 较小者发起，避免两端重复处理。
     */
    private void pairTick() {
        if (this.level() instanceof ServerLevel sl) {
            // 已配对：校验搭档仍在且类型互补；否则解除
            if (this.isPaired()) {
                if (sl.getEntity(this.partnerId) instanceof AwakeningFallingBlockEntity p
                    && ((this.isChest() && p.isBrewingStand()) || (this.isBrewingStand() && p.isChest()))) {
                    return; // 保持配对
                }
                this.partnerId = -1; // 搭档消失，解除
                return;
            }
            if (!this.isChest() && !this.isBrewingStand()) return;

            // 未配对：找 32 格内最近的未配对异类（且必须是同一个主人召唤的，不同主人不匹配）
            AwakeningFallingBlockEntity best = null;
            double bestSqr = (double) PAIR_DIST * PAIR_DIST;
            String myOwner = getOwnerUuid();
            for (AwakeningFallingBlockEntity o : sl.getEntitiesOfClass(
                AwakeningFallingBlockEntity.class, this.getBoundingBox().inflate(PAIR_DIST))) {
                if (o == this || o.isPaired()) continue;
                // 必须同一主人召唤才能配对
                if (!myOwner.equals(o.getOwnerUuid())) continue;
                boolean complement = (this.isChest() && o.isBrewingStand()) || (this.isBrewingStand() && o.isChest());
                if (!complement) continue;
                double d = this.distanceToSqr(o);
                if (d < bestSqr) {
                    bestSqr = d;
                    best = o;
                }
            }
            if (best == null) return;
            if (this.getId() < best.getId()) {
                this.partnerId = best.getId();
                best.partnerId = this.getId();
                // 倒计时同步为更长的一方
                int longer = Math.max(this.solidifyTicks, best.solidifyTicks);
                this.solidifyTicks = longer;
                best.solidifyTicks = longer;
                // 酿造台骑乘箱子；箱子标记为打开（同步标记，客户端可读到）
                if (this.isBrewingStand() && best.isChest()) {
                    best.getPersistentData().putBoolean(PAIR_CHEST_OPEN_TAG, true);
                    best.setChestOpen(true);
                    if (!this.isPassenger() && best.isAlive()) {
                        this.startRiding(best);
                    }
                } else if (this.isChest() && best.isBrewingStand()) {
                    this.getPersistentData().putBoolean(PAIR_CHEST_OPEN_TAG, true);
                    this.setChestOpen(true);
                    if (!best.isPassenger() && this.isAlive()) {
                        best.startRiding(this);
                    }
                }
            }
        }
    }

    /**
     * 配对后（箱子侧）自动装填酿造台：
     * 1) 酿造台烈焰粉充能≤0 且箱子有烈焰粉 → 消耗一个，生成向上小速度的不可拾取烈焰粉掉落物，
     *    落到酿造台实体时被吸收并把“可使用次数”设为20；
     * 2) 酿造台有空的药水瓶槽且箱子有药水 → 抛掷吸收，装入该槽（优先级：喷溅>滞留>直饮，
     *    有实际效果>无效果>水瓶）。
     */
    private void doPairRefill(AwakeningFallingBlockEntity brewing) {
        if (this.myBlockData == null || brewing.myBlockData == null) {
            return;
        }
        // 每轮（REFILL_INTERVAL=10 刻）只抛一个物品，按间隔依次丢出，不一次性全丢。
        // 1. 烈焰粉：酿造台确实未充能（Fuel≤0）且没有烈焰粉在飞行中时，固定只投一个，
        //    避免在滞空结算前 fuel 仍为 0 导致重复投掷。
        int fuel = brewing.myBlockData.getInt("Fuel");
        boolean blazeInFlight = hasBlazeInFlight();
        boolean tookBlaze = !blazeInFlight && fuel <= 0
            && takeOneItemFrom(this.myBlockData, s -> s.is(net.minecraft.world.item.Items.BLAZE_POWDER));
        if (tookBlaze) {
            spawnThrownItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_POWDER), brewing, -1);
            return; // 本轮只丢烈焰粉，药水等下一轮
        }

        // 2. 药水：酿造原料飞行中时跳过药水装填，避免与酿造结果冲突
        if (hasBrewingIngredientInFlight()) return;
        // 自动酿造有资源可运行时跳过水瓶装填，避免 doPairRefill 的飞行物阻塞酿造周期
        if (hasBrewingMaterials(brewing)) return;
        java.util.List<Integer> empty = findEmptySlots(brewing.myBlockData);
        java.util.Set<Integer> inFlight = getInFlightSlots();
        int slot = -1;
        for (Integer s : empty) if (!inFlight.contains(s)) { slot = s; break; }
        if (slot >= 0) {
            net.minecraft.world.item.ItemStack chosen = pickPotionFromChest();
            if (!chosen.isEmpty()) {
                spawnThrownItem(chosen, brewing, slot);
            }
        }
    }

    /** 酿造台当前为空的药水瓶槽位（0/1/2）。 */
    private java.util.List<Integer> findEmptySlots(CompoundTag brewingData) {
        java.util.Set<Integer> filled = new java.util.HashSet<>();
        net.minecraft.nbt.ListTag items = getOrCreateItems(brewingData);
        for (int i = 0; i < items.size(); i++) {
            filled.add(items.getCompound(i).getInt("Slot"));
        }
        java.util.List<Integer> result = new java.util.ArrayList<>();
        for (int s = 0; s <= 2; s++) if (!filled.contains(s)) result.add(s);
        return result;
    }

    /** 当前在飞行中的药水所占用的酿造台槽位集合。 */
    private java.util.Set<Integer> getInFlightSlots() {
        java.util.Set<Integer> slots = new java.util.HashSet<>();
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return slots;
        for (int id : this.thrownItemIds) {
            net.minecraft.world.entity.Entity e = sl.getEntity(id);
            if (e instanceof net.minecraft.world.entity.item.ItemEntity ie
                && !ie.getItem().is(net.minecraft.world.item.Items.BLAZE_POWDER)) {
                slots.add(e.getPersistentData().getInt(THROW_SLOT_TAG));
            }
        }
        return slots;
    }

    /** 是否有烈焰粉仍在飞行中（尚未被吸收结算）。 */
    private boolean hasBlazeInFlight() {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return false;
        for (int id : this.thrownItemIds) {
            net.minecraft.world.entity.Entity e = sl.getEntity(id);
            if (e instanceof net.minecraft.world.entity.item.ItemEntity ie
                && ie.getItem().is(net.minecraft.world.item.Items.BLAZE_POWDER)) {
                return true;
            }
        }
        return false;
    }

    /** 从容器 NBT 的 Items 中取出（消耗一个）首个匹配物品；成功返回 true。 */
    private boolean takeOneItemFrom(CompoundTag data, java.util.function.Predicate<net.minecraft.world.item.ItemStack> pred) {
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag items = getOrCreateItems(data);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag it = items.getCompound(i);
            var opt = net.minecraft.world.item.ItemStack.parse(reg, it);
            if (opt.isPresent() && pred.test(opt.get())) {
                net.minecraft.world.item.ItemStack st = opt.get();
                st.shrink(1);
                if (st.isEmpty()) {
                    items.remove(i);
                } else {
                    it.putInt("count", st.getCount());
                    items.set(i, it);
                }
                return true;
            }
        }
        return false;
    }

    /** 从箱子挑一瓶药水（移除）并返回；按优先级挑选。 */
    private net.minecraft.world.item.ItemStack pickPotionFromChest() {
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag items = getOrCreateItems(this.myBlockData);
        CompoundTag bestTag = null;
        int bestScore = Integer.MIN_VALUE;
        net.minecraft.world.item.ItemStack bestStack = null;
        for (int i = 0; i < items.size(); i++) {
            CompoundTag it = items.getCompound(i);
            var opt = net.minecraft.world.item.ItemStack.parse(reg, it);
            if (opt.isEmpty()) continue;
            net.minecraft.world.item.ItemStack st = opt.get();
            int score = potionScore(st);
            if (score > bestScore && score > 0) {
                bestScore = score;
                bestStack = st;
                bestTag = it;
            }
        }
        if (bestStack == null) return net.minecraft.world.item.ItemStack.EMPTY;
        // 先复制要装填的一份（保留药水效果组件），再扣减箱子原栈，避免对已减到 0 的空栈
        // 调用 copyWithCount 返回 EMPTY，导致药水被从箱子移除却无返回物可装填。
        net.minecraft.world.item.ItemStack result = bestStack.copyWithCount(1);
        bestStack.shrink(1);
        if (bestStack.isEmpty()) {
            items.remove(items.indexOf(bestTag));
        } else {
            bestTag.putInt("count", bestStack.getCount());
            items.set(items.indexOf(bestTag), bestTag);
        }
        return result;
    }

    /** 药水优先级评分；非药水返回 0（不选）。 */
    private int potionScore(net.minecraft.world.item.ItemStack st) {
        net.minecraft.world.item.Item item = st.getItem();
        int type;
        if (item == net.minecraft.world.item.Items.SPLASH_POTION) type = 3;
        else if (item == net.minecraft.world.item.Items.LINGERING_POTION) type = 2;
        else if (item == net.minecraft.world.item.Items.POTION) type = 1;
        else return 0;
        net.minecraft.world.item.alchemy.PotionContents pc =
            st.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS, net.minecraft.world.item.alchemy.PotionContents.EMPTY);
        // 成品药水（传送/解药）不参与自动装填，避免被扔回酿造台导致箱子被掏空
        if (pc.is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSPORTATION)) return 0;
        if (pc.is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSMUTATION_ANTIDOTE)) return 0;
        boolean water = pc.potion().map(h -> h.is(net.minecraft.world.item.alchemy.Potions.WATER)).orElse(false);
        int effectful = pc.hasEffects() ? 2 : (water ? 0 : 1);
        return type + effectful;
    }

    /** 把药水放入酿造台指定槽位的 Items（用完整 NBT 序列化，保留药水效果组件）。 */
    private void putItemToSlot(CompoundTag brewingData, int slot, net.minecraft.world.item.ItemStack stack) {
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag items = getOrCreateItems(brewingData);
        for (int i = 0; i < items.size(); i++) {
            if (items.getCompound(i).getInt("Slot") == slot) {
                items.remove(i);
                break;
            }
        }
        net.minecraft.nbt.CompoundTag t = (net.minecraft.nbt.CompoundTag) stack.save(reg);
        t.putByte("Slot", (byte) slot);
        items.add(t);
    }

    /** 生成一个向上飞行、不可被生物拾取的掉落物，待落到酿造台附近时被“吸收”消失。
     *  @param slot 目标酿造台槽位（药水需写入）；烈焰粉传 -1。 */
    private void spawnThrownItem(net.minecraft.world.item.ItemStack stack, AwakeningFallingBlockEntity target, int slot) {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        // 保留完整组件（尤其 potion_contents），使飞行掉落物显示为对应药水的颜色，
        // 而非默认的无效果药水。
        net.minecraft.world.entity.item.ItemEntity e = new net.minecraft.world.entity.item.ItemEntity(
            sl, this.getX(), this.getY() + 0.5, this.getZ(), stack.copy());
        e.setDeltaMovement(0.0, 0.5, 0.0);
        e.setNoGravity(false);
        e.setPickUpDelay(32767);
        e.lifespan = 100;
        e.getPersistentData().putBoolean("jafa_no_pickup", true);
        if (slot >= 0) {
            e.getPersistentData().putInt(THROW_SLOT_TAG, slot);
        }
        sl.addFreshEntity(e);
        this.thrownItemIds.add(e.getId());
    }

    /** 生成酿造原料的飞行掉落物，与唤醒时装填药水同款机制。
     *  使用 {@link #spawnThrownItem} 相同的追踪机制，被吸收时触发酿造结果产出。 */
    private void spawnBrewingIngredient(net.minecraft.world.item.ItemStack stack, AwakeningFallingBlockEntity target, int slot, int brewType) {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        net.minecraft.world.entity.item.ItemEntity e = new net.minecraft.world.entity.item.ItemEntity(
            sl, this.getX(), this.getY() + 0.5, this.getZ(), stack.copy());
        e.setDeltaMovement(0.0, 0.5, 0.0);
        e.setNoGravity(false);
        e.setPickUpDelay(32767);
        e.lifespan = 100;
        e.getPersistentData().putBoolean("jafa_no_pickup", true);
        e.getPersistentData().putInt(THROW_SLOT_TAG, slot);
        e.getPersistentData().putInt(BREW_TYPE_TAG, brewType);
        sl.addFreshEntity(e);
        this.thrownItemIds.add(e.getId());
    }

    /** 每刻推进已抛掉落物：上抛（抛出）速度翻倍为 1.0/刻；下落速度、上升时长与吸收时机保持原样
     *  （先上抛 10 刻，之后按重力自然下落，落回(>20刻)时贴到酿造台被吸收）。
     *  只有在掉落物被吸收消失的那一刻，才真正让酿造台“拥有该物品”（充能/装填并渲染）。 */
    private void tickThrownItems(AwakeningFallingBlockEntity target) {
        if (this.level().isClientSide || this.thrownItemIds.isEmpty()) return;
        net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) this.level();
        java.util.Iterator<Integer> it = this.thrownItemIds.iterator();
        while (it.hasNext()) {
            int id = it.next();
            net.minecraft.world.entity.Entity e = sl.getEntity(id);
            if (e == null || e.isRemoved()) {
                it.remove();
                continue;
            }
            if (e.tickCount <= 10) {
                // 上升段持续上抛（抛出速度 0.5/刻）
                e.setDeltaMovement(0.0, 0.5, 0.0);
            } else if (e.tickCount > 20) {
                // 落回后吸附到酿造台位置并消失
                e.moveTo(target.getX(), target.getY() + 0.25, target.getZ());
                e.setDeltaMovement(0.0, 0.0, 0.0);
                commitRefill(target, e);
                e.discard();
                it.remove();
            }
        }
    }

    /** 掉落物“消失”时把对应效果结算到酿造台：烈焰粉→充能 Fuel=20；药水→写入对应槽位并同步瓶身渲染。 */
    private void commitRefill(AwakeningFallingBlockEntity brewing, net.minecraft.world.entity.Entity dropped) {
        if (!(dropped instanceof net.minecraft.world.entity.item.ItemEntity ie)) return;
        // 酿造原料：被吸收时产出酿造结果
        if (dropped.getPersistentData().contains(BREW_TYPE_TAG)) {
            int brewType = dropped.getPersistentData().getInt(BREW_TYPE_TAG);
            int slot = dropped.getPersistentData().getInt(THROW_SLOT_TAG);
            commitBrewingResult(brewing, brewType, slot);
            return;
        }
        net.minecraft.world.item.ItemStack carried = ie.getItem();
        if (carried.is(net.minecraft.world.item.Items.BLAZE_POWDER)) {
            brewing.myBlockData.putInt("Fuel", 20);
        } else {
            int slot = dropped.getPersistentData().getInt(THROW_SLOT_TAG);
            if (slot < 0 || slot > 2) return;
            putItemToSlot(brewing.myBlockData, slot, carried);
            // 装填完成后才同步瓶身到客户端，让酿造台此刻才显示"装有药水"
            brewing.syncBrewBottles();
        }
    }

    /** 酿造原料被吸收后产出对应药水到酿造台槽位。
     *  产出前先把目标槽位中的成品药水（非粗制、非缠魂）移回箱子，防止被覆盖吞掉。 */
    private void commitBrewingResult(AwakeningFallingBlockEntity brewing, int brewType, int slot) {
        if (brewing.myBlockData == null) return;
        // 先保存目标槽位的旧药水到箱子（防吞）
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag brewItems = getOrCreateItems(brewing.myBlockData);
        for (int i = 0; i < brewItems.size(); i++) {
            if (brewItems.getCompound(i).getInt("Slot") != slot) continue;
            var opt = net.minecraft.world.item.ItemStack.parse(reg, brewItems.getCompound(i));
            if (opt.isEmpty()) break;
            net.minecraft.world.item.alchemy.PotionContents oldPc =
                opt.get().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    net.minecraft.world.item.alchemy.PotionContents.EMPTY);
            // 中间产物（粗制/缠魂/水瓶）是酿造输入，正常覆盖；成品药水则先移回箱子
            if (!oldPc.is(net.minecraft.world.item.alchemy.Potions.AWKWARD)
                && !oldPc.is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.HAUNTED)
                && !oldPc.is(net.minecraft.world.item.alchemy.Potions.WATER)) {
                net.minecraft.world.item.ItemStack saved = opt.get().copy();
                tryAddToData(this.myBlockData, saved);
            }
            break;
        }
        // 产出结果
        net.minecraft.world.item.ItemStack result;
        switch (brewType) {
            case 0: // 粗制药水
                result = awkwardPotion();
                break;
            case 1: // 传送药水
                result = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION);
                result.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    new net.minecraft.world.item.alchemy.PotionContents(
                        java.util.Optional.of(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSPORTATION),
                        java.util.Optional.empty(), java.util.List.of()));
                result = tryMakeSplash(result);
                break;
            case 2: // 缠魂的药水
                result = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION);
                result.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    new net.minecraft.world.item.alchemy.PotionContents(
                        java.util.Optional.of(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.HAUNTED),
                        java.util.Optional.empty(), java.util.List.of()));
                break;
            case 3: // 变形解药
                result = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION);
                result.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    new net.minecraft.world.item.alchemy.PotionContents(
                        java.util.Optional.of(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSMUTATION_ANTIDOTE),
                        java.util.Optional.empty(), java.util.List.of()));
                result = tryMakeSplash(result);
                break;
            default:
                return;
        }
        putItemToSlot(brewing.myBlockData, slot, result);
        brewing.syncBrewBottles();
    }

    /** 给主人丢/施用药水（每 OWNER_ASSIST_INTERVAL 刻调用一次）：
     *  远距(>6格)对主人直接施放增益药水效果（排除传送/变形/觉醒）；近距(≤6格)把药水物品抛给主人（不限类型）。
     *  两种情况都必须从酿造台或箱子里取出真实存在的药水，不会凭空生成。 */
    private void doOwnerAssist() {
        AwakeningFallingBlockEntity brewing = partnerBrewing();
        if (brewing == null) {
            return;
        }
        net.minecraft.world.entity.LivingEntity owner = getOwner();
        if (owner == null || owner.isRemoved()) {
            // 主人本体可能已被变形并 discard（只剩方块/物品壳）：向壳位置丢变形解药
            tryThrowAntidoteToOwnerShell(brewing);
            return;
        }

        // 主人为女巫Boss：友好模式下直接修改弹药数据而非丢药水
        if (isOwnerWitchBoss() && this.assistMode == 0) {
            supplyWitchBossAmmo(brewing, (net.minecraft.world.entity.monster.Witch) owner);
            return;
        }

        boolean close = this.distanceToSqr(owner.getX(), owner.getY(), owner.getZ())
            <= CLOSE_OWNER_DIST * CLOSE_OWNER_DIST;
        // 全模式优先：主人被方块/物品变形药水影响时，优先丢变形解药
        if (tryThrowAntidoteToOwner(brewing, owner)) {
            return;
        }
        // 援助模式分发
        if (this.assistMode == 2) return; // 无模式：仅跟随，不给药
        if (this.assistMode == 1) {
            doHostileAssist(brewing);
            return;
        }
        // assistMode == 0：友善模式，给主人增益
        if (close) {
            // 近距：取任意“非黑名单”药水物品，像悦灵/玩家互丢那样抛出真实物品实体给主人拾取
            net.minecraft.world.item.ItemStack potion = takeAnyPotion(brewing);
            if (!potion.isEmpty()) {
                tossItemToOwner(owner, potion);
                brewing.syncBrewBottles(); // 取走后刷新酿造台瓶身显示
            }
        } else {
            // 远距：取增益药水（排除传送/变形/觉醒），像女巫那样抛出真实溅射药水，砸中主人碎裂生效
            net.minecraft.world.item.ItemStack potion = takeBeneficialPotion(brewing);
            if (!potion.isEmpty()) {
                throwRealSplash(owner, potion);
                brewing.syncBrewBottles(); // 取走后刷新酿造台瓶身显示
            }
        }
    }

    /** 敌对模式：向敌人投掷减益/变形药水。近距抛物品实体，远距抛溅射药水。
     *  若敌人被变形为生物→只丢减益药水；若被变形为方块/物品→暂停攻击，恢复后继续。 */
    private void doHostileAssist(AwakeningFallingBlockEntity brewing) {
        net.minecraft.world.entity.LivingEntity enemy = findEnemy();
        if (enemy == null || enemy.isRemoved()) {
            return;
        }
        // 最终安全守卫：绝不向主人丢药水
        net.minecraft.world.entity.LivingEntity owner = getOwner();
        if (owner != null && enemy.getUUID().equals(owner.getUUID())) {
            return;
        }
        // 敌人被变形处理
        boolean isBlockOrItem = false;
        boolean isMobTransmuted = false;
        java.util.UUID enemyUid = enemy.getUUID();
        if (enemy instanceof net.minecraft.world.entity.player.Player) {
            isBlockOrItem = cn.autoforged.joes_addons_for_abmc.ModMain.isPlayerTransmutedAsBlockOrItem(enemyUid);
            isMobTransmuted = cn.autoforged.joes_addons_for_abmc.ModMain.isPlayerTransmutedAsMob(enemyUid);
        } else {
            // 非玩家敌人：检查是否有 TRANSMUTATION 效果
            isBlockOrItem = enemy.hasEffect(cn.autoforged.joes_addons_for_abmc.potion.ModMobEffects.TRANSMUTATION);
            isMobTransmuted = enemy.hasEffect(cn.autoforged.joes_addons_for_abmc.potion.ModMobEffects.TRANSMUTATION);
        }
        if (isBlockOrItem) {
            // 敌人被变成方块/物品：暂停攻击，记住 UUID 和位置等待恢复
            this.waitingEnemyUuid = enemyUid;
            this.waitingEnemyPos = enemy.blockPosition();
            return;
        }
        if (isMobTransmuted) {
            // 敌人被变成生物：只丢减益药水，不丢变形药水
            net.minecraft.world.item.ItemStack potion = takeHarmfulPotion(brewing);
            if (!potion.isEmpty()) {
                throwRealSplash(enemy, potion);
                brewing.syncBrewBottles();
            }
            return;
        }
        // 敌人恢复：清除等待状态
        if ((this.waitingEnemyUuid != null && this.waitingEnemyUuid.equals(enemyUid))
            || (this.waitingEnemyPos != null && this.waitingEnemyPos.distSqr(enemy.blockPosition()) <= 4.0)) {
            this.waitingEnemyUuid = null;
            this.waitingEnemyPos = null;
        }
        // 正常敌对：变形药水优先，其次减益
        boolean close = this.distanceToSqr(enemy.getX(), enemy.getY(), enemy.getZ())
            <= CLOSE_OWNER_DIST * CLOSE_OWNER_DIST;
        net.minecraft.world.item.ItemStack potion = takeTransmutationPotion(brewing);
        if (potion.isEmpty()) potion = takeHarmfulPotion(brewing);
        if (!potion.isEmpty()) {
            assignWitchBossPoolToTransmutation(potion);
            throwRealSplash(enemy, potion);
            brewing.syncBrewBottles();
        }
    }

    /** 变形药水：目标类型改为从女巫Boss的“变形池”按阶段抽取（不是完全随机）。
     *  仅当主人是女巫Boss时生效；否则保持药水原有目标类型。 */
    private void assignWitchBossPoolToTransmutation(net.minecraft.world.item.ItemStack potion) {
        if (!isTransmutationPotion(potion)) return;
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel)) return;
        net.minecraft.world.entity.LivingEntity owner = getOwner();
        if (!(owner instanceof net.minecraft.world.entity.monster.Witch witch)) return;
        if (!witch.getPersistentData().getBoolean(cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_TAG)) return;
        int stage = cn.autoforged.joes_addons_for_abmc.ModMain.getWitchBossStage(witch);
        String type = cn.autoforged.joes_addons_for_abmc.ModMain.stageTransmutationItemType(
            stage, net.minecraft.util.RandomSource.create(witch.getRandom().nextLong()));
        if (type != null) {
            potion.set(cn.autoforged.joes_addons_for_abmc.item.ModDataComponents.ITEM_TYPE.get(), type);
        }
    }

    /** 近距：抛出一个真实的物品实体（药水物品）飞向主人，主人走过即可拾取（原版拾取音效）。 */
    private void tossItemToOwner(net.minecraft.world.entity.LivingEntity owner, net.minecraft.world.item.ItemStack potion) {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        net.minecraft.world.entity.item.ItemEntity e = new net.minecraft.world.entity.item.ItemEntity(
            sl, this.getX(), this.getY() + 0.9, this.getZ(), potion.copy());
        double dx = owner.getX() - this.getX();
        double dz = owner.getZ() - this.getZ();
        double h = Math.hypot(dx, dz);
        double up = Math.min(0.45, Math.max(0.25, h * 0.08));
        if (h < 1.0E-4) { dx = 0.0; dz = 0.0; }
        // 弹道公式：飞行时间 t = 2*vy/g，水平速度 vx = h/t，确保落点 = 目标距离
        double t = 2.0 * up / 0.04; // ItemEntity 重力 0.04/刻，无空气阻力
        double vx = Math.min(h / t, 0.5);
        e.setDeltaMovement(dx / (h + 1.0E-4) * vx, up, dz / (h + 1.0E-4) * vx);
        e.setPickUpDelay(30); // 避免组合体自己捡回刚扔出的物品
        e.lifespan = 200;
        sl.addFreshEntity(e);
    }

    /** 远距：像女巫丢药水那样抛出一个真实溅射药水实体(ThrownPotion)，砸中主人/地面时碎裂并给予效果。
     *  初速/仰角随主人距离调整，确保药水能飞到主人头部附近（溅射半径可兜底）。 */
    private void throwRealSplash(net.minecraft.world.entity.LivingEntity owner, net.minecraft.world.item.ItemStack potion) {
        throwRealSplashToward(owner.getX(), owner.getY() + 1.0, owner.getZ(), potion);
    }

    /** 向任意目标点抛出真实溅射药水（供解药丢向变形壳实体等非 LivingEntity 目标使用）。 */
    private void throwRealSplashToward(double targetX, double targetY, double targetZ,
            net.minecraft.world.item.ItemStack potion) {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        // 把普通/滞留药水转成喷溅药水（保留 potion_contents），作为真实 ThrownPotion 抛出
        net.minecraft.world.item.ItemStack splash = potion.transmuteCopy(net.minecraft.world.item.Items.SPLASH_POTION, 1);
        net.minecraft.world.entity.projectile.ThrownPotion tp =
            new net.minecraft.world.entity.projectile.ThrownPotion(sl, this.getX(), this.getY() + 1.0, this.getZ());
        tp.setItem(splash);
        // 水平方向 + 水平距离 + 目标身体中心相对发射点的垂直落差
        double dx = targetX - this.getX();
        double dz = targetZ - this.getZ();
        double h = Math.hypot(dx, dz);
        double dy = targetY - (this.getY() + 1.0); // 目标在下方时 dy<0，需压低弹道
        if (h < 1.0E-4) { dx = 0.0; dz = 0.0; }
        // 带空气阻力的弹道精确解：ThrownPotion 重力 0.05/刻、空气阻力 0.99/刻。
        // 以估算飞行刻数 t 为参量：水平位移 = vx*100*(1-0.99^t)，竖直位移 = 100*(1-0.99^t)*(vy+5) - 5*t。
        // 反解 vx、vy 即可在任意高低差（目标在上/在下）下精确命中，避免从低处目标头顶飞过。
        double t = Mth.clamp(6.0 + h * 0.6, 8.0, 50.0);
        double dragFactor = 100.0 * (1.0 - Math.pow(0.99, t));
        if (dragFactor < 1.0E-3) dragFactor = 1.0E-3;
        // 直接用 setDeltaMovement 而非 shoot()，因为 shoot() 会归一化方向向量导致速度恒为 1.0，远距离无法到达。
        double speedH = Mth.clamp(h / dragFactor, 0.0, 2.5);
        double vy = Mth.clamp((dy + 5.0 * t) / dragFactor - 5.0, -1.5, 1.5);
        double vx = dx / (h + 1.0E-4) * speedH;
        double vz = dz / (h + 1.0E-4) * speedH;
        tp.setDeltaMovement(vx, vy, vz);
        // 设置朝向与速度方向一致（shoot 中也会做这一步）
        double horizDist = Math.hypot(vx, vz);
        tp.setYRot((float)(Mth.atan2(vx, vz) * (180.0 / Math.PI)));
        tp.setXRot((float)(Mth.atan2(vy, horizDist) * (180.0 / Math.PI)));
        tp.yRotO = tp.getYRot();
        tp.xRotO = tp.getXRot();
        sl.addFreshEntity(tp);
    }

    /** 从箱子或酿造台里取一瓶任意药水并移除（返回副本）；无则返回空栈。 */
    private net.minecraft.world.item.ItemStack takeAnyPotion(AwakeningFallingBlockEntity brewing) {
        net.minecraft.world.item.ItemStack fromChest = takePotionFrom(this.myBlockData, s -> !isBlacklistedPotion(s));
        if (!fromChest.isEmpty()) return fromChest;
        return takePotionFrom(brewing.myBlockData, s -> !isBlacklistedPotion(s));
    }

    /** 是否为水瓶（PotionContents 指向原版 WATER）。 */
    private boolean isWaterBottle(net.minecraft.world.item.ItemStack st) {
        if (!isPotionItem(st.getItem())) return false;
        net.minecraft.world.item.alchemy.PotionContents pc = st.getOrDefault(
            net.minecraft.core.component.DataComponents.POTION_CONTENTS,
            net.minecraft.world.item.alchemy.PotionContents.EMPTY);
        return pc.potion().map(h -> h.is(net.minecraft.world.item.alchemy.Potions.WATER)).orElse(false);
    }

    /** 是否为黑名单药水：列入后不会被箱子酿造台组合体丢出或丢给玩家。
     *  黑名单：水瓶、粗制、粘稠、平凡、准变形、准传送、不可酿造（无药水数据）。 */
    private boolean isBlacklistedPotion(net.minecraft.world.item.ItemStack st) {
        if (!isPotionItem(st.getItem())) return false;
        net.minecraft.world.item.alchemy.PotionContents pc = st.getOrDefault(
            net.minecraft.core.component.DataComponents.POTION_CONTENTS,
            net.minecraft.world.item.alchemy.PotionContents.EMPTY);
        var op = pc.potion();
        if (op.isEmpty()) return true; // 不可酿造（PotionContents 无 holder）
        net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> h = op.get();
        return h.is(net.minecraft.world.item.alchemy.Potions.WATER)
            || h.is(net.minecraft.world.item.alchemy.Potions.AWKWARD)
            || h.is(net.minecraft.world.item.alchemy.Potions.THICK)
            || h.is(net.minecraft.world.item.alchemy.Potions.MUNDANE)
            || h.is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.PRE_TRANSMUTATION)
            || h.is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.PRE_TRANSPORTATION);
    }

    /** 自动酿药（测试配方）：若组合体里有水瓶 + 地狱疣 + 燃料，则把水瓶酿成粗制药水并存进组合体
     *  （粗制药水在黑名单里，不会丢给玩家）。无耗材则不做任何事。 */
    private boolean tryAutoBrew(AwakeningFallingBlockEntity brewing) {
        if (brewing.myBlockData.getInt("Fuel") <= 0) return false;
        // 找水瓶：箱子任意槽 或 酿造台 0..2 槽
        int chestIdx = findWaterInData(this.myBlockData);
        int brewSlot = (chestIdx < 0) ? findWaterInBrewingSlots(brewing.myBlockData) : -1;
        if (chestIdx < 0 && brewSlot < 0) return false;
        // 地狱疣：箱子 或 酿造台第3槽
        boolean wart = takeOneItemFrom(this.myBlockData, s -> s.is(net.minecraft.world.item.Items.NETHER_WART));
        if (!wart) wart = removeFromSlot(brewing.myBlockData, 3, net.minecraft.world.item.Items.NETHER_WART);
        if (!wart) return false;
        spawnBrewingAnimation(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHER_WART));
        // 移除水瓶
        if (chestIdx >= 0) {
            getOrCreateItems(this.myBlockData).remove(chestIdx);
        } else {
            removeFromSlot(brewing.myBlockData, brewSlot, net.minecraft.world.item.Items.POTION);
        }
        // 燃料
        int fuel = brewing.myBlockData.getInt("Fuel");
        brewing.myBlockData.putInt("Fuel", Math.max(0, fuel - 1));
        // 产出粗制药水：优先放入酿造台空瓶槽，否则放进箱子
        net.minecraft.world.item.ItemStack awkward = awkwardPotion();
        net.minecraft.nbt.ListTag brewingItems = getOrCreateItems(brewing.myBlockData);
        int freeSlot = -1;
        boolean[] occupied = { false, false, false };
        for (int i = 0; i < brewingItems.size(); i++) {
            int s = brewingItems.getCompound(i).getInt("Slot");
            if (s >= 0 && s <= 2) occupied[s] = true;
        }
        for (int s = 0; s <= 2; s++) if (!occupied[s]) { freeSlot = s; break; }
        if (freeSlot >= 0) {
            putItemToSlot(brewing.myBlockData, freeSlot, awkward);
            brewing.syncBrewBottles();
        } else {
            tryAddToData(this.myBlockData, awkward);
        }
        return true;
    }

    private net.minecraft.world.item.ItemStack awkwardPotion() {
        net.minecraft.world.item.ItemStack o = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION);
        o.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
            new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.AWKWARD));
        return o;
    }

    // ==================== 全模式自动酿造 ====================

    /** 自动酿造：基础/传送/缠魂/解药四类交替循环，不受 assistMode 影响。
     *  使用与初次唤醒装填相同的 thrownItem 机制：一次只丢一个原料，飞行物被吸收后产出结果。
     *  只有当某个酿造类型真正启动时，才把其他成品药水移回箱子释放槽位；
     *  若所有类型都无法启动（原材料不足），成品药水保留在酿造台，不触发无效的移回。
     *  @return true 表示成功启动了酿造（有原料飞行中），false 表示跳过（等待飞行物吸收或原料不足）。 */
    private boolean tickAdvancedBrewing() {
        // 有任何飞行物（酿造原料或药水）在空中：等待吸收
        if (hasBrewingIngredientInFlight()) return false;
        // 空闲阶段：检查条件并开始酿造
        AwakeningFallingBlockEntity brewing = partnerBrewing();
        if (brewing == null) return false;
        if (brewing.myBlockData == null) return false;
        if (brewing.myBlockData.getInt("Fuel") <= 0) return false;
        // 按调度顺序依次尝试：0=基础(粗制)，1=传送，2=缠魂，3=解药
        int maxAttempts = 4;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int type = this.brewSchedule % 4;
            this.brewSchedule = (this.brewSchedule + 1) % 4;
            boolean started = false;
            if (type == 0) {
                started = tryStartAutoBrew(brewing);
            } else if (type == 1) {
                started = tryStartTransportation(brewing);
            } else if (type == 2) {
                started = tryStartHaunted(brewing);
            } else {
                started = tryStartAntidote(brewing);
            }
            if (started) {
                // 酿造启动后，把其他成品药水移回箱子释放槽位（供下一轮酿造使用）
                moveFinishedPotionsToChest(brewing);
                return true;
            }
        }
        return false;
    }

    /** 把酿造台 0..2 槽中的成品药水（非粗制、非缠魂）移回箱子，释放槽位供新酿造使用。 */
    private void moveFinishedPotionsToChest(AwakeningFallingBlockEntity brewing) {
        if (this.myBlockData == null || brewing.myBlockData == null) return;
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag brewItems = getOrCreateItems(brewing.myBlockData);
        boolean changed = false;
        for (int i = brewItems.size() - 1; i >= 0; i--) {
            int s = brewItems.getCompound(i).getInt("Slot");
            if (s < 0 || s > 2) continue;
            var opt = net.minecraft.world.item.ItemStack.parse(reg, brewItems.getCompound(i));
            if (opt.isEmpty()) continue;
            net.minecraft.world.item.alchemy.PotionContents pc =
                opt.get().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    net.minecraft.world.item.alchemy.PotionContents.EMPTY);
            // 粗制药水、缠魂的药水、水瓶是中间产物，保留在酿造台
            if (pc.is(net.minecraft.world.item.alchemy.Potions.AWKWARD)) continue;
            if (pc.is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.HAUNTED)) continue;
            if (pc.is(net.minecraft.world.item.alchemy.Potions.WATER)) continue;
            // 成品药水移回箱子
            net.minecraft.world.item.ItemStack copy = opt.get().copy();
            net.minecraft.world.item.ItemStack remaining = tryAddToData(this.myBlockData, copy);
            if (remaining.isEmpty()) {
                brewItems.remove(i);
                changed = true;
            }
        }
        if (changed) {
            brewing.myBlockData.put("Items", brewItems);
            brewing.syncBrewBottles();
        }
    }

    /** 调试：打印酿造台 0..2 槽位的药水类型快照，如 "0:awkward,1:empty,2:water"。 */

    /** 从实体当前位置的箱子方块同步 myBlockData。若该位置有箱子/木桶则读取其物品，保留原有 NBT 中非物品字段。 */
    private void syncChestData() {
        if (this.level() == null || this.myBlockData == null) return;
        // 使用原始方块位置（getStartPos），而非实体当前位置（蹦跳后已离开箱子位置）
        BlockPos pos = this.getStartPos();
        net.minecraft.world.level.block.entity.BlockEntity be = this.level().getBlockEntity(pos);
        if (be == null) {
            return;
        }
        // 只同步箱子/木桶类容器
        net.minecraft.nbt.CompoundTag fresh = be.saveWithoutMetadata(this.level().registryAccess());
        if (fresh == null || !fresh.contains("Items")) return;
        net.minecraft.nbt.ListTag newItems = fresh.getList("Items", 10);
        // 保留原有非物品字段（如 jafa_* 等自定义键），仅替换 Items
        this.myBlockData.put("Items", newItems);
    }

    private String dumpBrewSlots(AwakeningFallingBlockEntity brewing) {
        if (brewing == null || brewing.myBlockData == null) return "null";
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag items = getOrCreateItems(brewing.myBlockData);
        String[] slots = { "empty", "empty", "empty" };
        for (int i = 0; i < items.size(); i++) {
            int s = items.getCompound(i).getInt("Slot");
            if (s < 0 || s > 2) continue;
            var opt = net.minecraft.world.item.ItemStack.parse(reg, items.getCompound(i));
            if (opt.isEmpty()) continue;
            net.minecraft.world.item.alchemy.PotionContents pc =
                opt.get().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    net.minecraft.world.item.alchemy.PotionContents.EMPTY);
            if (pc.potion().isPresent()) {
                slots[s] = pc.potion().get().getRegisteredName();
            } else {
                slots[s] = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(opt.get().getItem()).toString();
            }
        }
        return slots[0] + "," + slots[1] + "," + slots[2];
    }

    /** 是否有任何非烈焰粉的飞行物在空中（尚未被吸收）。包括酿造原料和 doPairRefill 的药水。 */
    private boolean hasBrewingIngredientInFlight() {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return false;
        for (int id : this.thrownItemIds) {
            net.minecraft.world.entity.Entity e = sl.getEntity(id);
            if (e != null && !e.isRemoved()
                && e instanceof net.minecraft.world.entity.item.ItemEntity ie
                && !ie.getItem().is(net.minecraft.world.item.Items.BLAZE_POWDER)) {
                return true;
            }
        }
        return false;
    }

    /** 箱子中是否有酿造原料（地狱疣/末影珍珠/灵魂沙/发酵蛛眼），即自动酿造可能运行。
     *  不检查燃料——燃料由 doPairRefill 独立补充，不应阻塞此判断。 */
    private boolean hasBrewingMaterials(AwakeningFallingBlockEntity brewing) {
        if (brewing == null || brewing.myBlockData == null) return false;
        if (this.myBlockData == null) return false;
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag items = getOrCreateItems(this.myBlockData);
        boolean hasWart = false, hasPearl = false, hasSoul = false, hasEye = false;
        for (int i = 0; i < items.size(); i++) {
            var opt = net.minecraft.world.item.ItemStack.parse(reg, items.getCompound(i));
            if (opt.isEmpty()) continue;
            net.minecraft.world.item.Item item = opt.get().getItem();
            if (item == net.minecraft.world.item.Items.NETHER_WART) hasWart = true;
            if (item == net.minecraft.world.item.Items.ENDER_PEARL) hasPearl = true;
            if (item == net.minecraft.world.item.Items.SOUL_SAND) hasSoul = true;
            if (item == net.minecraft.world.item.Items.FERMENTED_SPIDER_EYE) hasEye = true;
        }
        return hasWart || hasPearl || hasSoul || hasEye;
    }

    /** 开始基础酿造：消耗原料、丢出地狱疣飞行物，被吸收时产出粗制药水。 */
    private boolean tryStartAutoBrew(AwakeningFallingBlockEntity brewing) {
        if (brewing.myBlockData.getInt("Fuel") <= 0) { return false; }
        // 优先用酿造台水瓶，避免浪费箱子水瓶
        int brewSlot = findWaterInBrewingSlots(brewing.myBlockData);
        int chestIdx = (brewSlot < 0) ? findWaterInData(this.myBlockData) : -1;
        if (brewSlot < 0 && chestIdx < 0) { return false; }
        boolean wart = takeOneItemFrom(this.myBlockData, s -> s.is(net.minecraft.world.item.Items.NETHER_WART));
        if (!wart) wart = removeFromSlot(brewing.myBlockData, 3, net.minecraft.world.item.Items.NETHER_WART);
        if (!wart) { return false; }
        // 移除水瓶：酿造台优先，否则从箱子消耗1瓶
        if (brewSlot >= 0) {
            removeFromSlot(brewing.myBlockData, brewSlot, net.minecraft.world.item.Items.POTION);
        } else {
            // 从箱子消耗1瓶（不是移除整叠！）
            net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
            net.minecraft.nbt.ListTag items = getOrCreateItems(this.myBlockData);
            net.minecraft.nbt.CompoundTag tag = items.getCompound(chestIdx);
            var opt = net.minecraft.world.item.ItemStack.parse(reg, tag);
            if (opt.isPresent()) {
                net.minecraft.world.item.ItemStack st = opt.get();
                st.shrink(1);
                if (st.isEmpty()) {
                    items.remove(chestIdx);
                } else {
                    items.set(chestIdx, st.save(reg));
                }
            }
        }
        // 消耗燃料
        int fuel = brewing.myBlockData.getInt("Fuel");
        brewing.myBlockData.putInt("Fuel", Math.max(0, fuel - 1));
        // 产出槽位：水瓶在哪就产到哪，避免飞到其他槽位覆盖成品药水
        int targetSlot;
        if (brewSlot >= 0) {
            targetSlot = brewSlot;
        } else {
            targetSlot = findFreeBrewSlot(brewing.myBlockData);
            if (targetSlot < 0) targetSlot = 0;
        }
        // 丢出酿造原料飞行物，被吸收时由 commitRefill → commitBrewingResult 产出结果
        spawnBrewingIngredient(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHER_WART), brewing, targetSlot, 0);
        return true;
    }

    /** 开始传送药水酿造 */
    private boolean tryStartTransportation(AwakeningFallingBlockEntity brewing) {
        int slot = findAwkwardInBrewing(brewing.myBlockData);
        if (slot < 0) slot = pullAwkwardFromChest(brewing);
        if (slot < 0) { return false; }
        if (!takeOneItemFrom(this.myBlockData, s -> s.is(net.minecraft.world.item.Items.ENDER_PEARL))) {
            return false;
        }
        int fuel = brewing.myBlockData.getInt("Fuel");
        brewing.myBlockData.putInt("Fuel", Math.max(0, fuel - 1));
        spawnBrewingIngredient(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL), brewing, slot, 1);
        return true;
    }

    /** 开始缠魂药水酿造 */
    private boolean tryStartHaunted(AwakeningFallingBlockEntity brewing) {
        int waterSlot = findWaterInBrewingSlots(brewing.myBlockData);
        if (waterSlot < 0) waterSlot = pullWaterFromChest(brewing);
        if (waterSlot < 0) { return false; }
        if (!takeOneItemFrom(this.myBlockData, s -> s.is(net.minecraft.world.item.Items.SOUL_SAND))) {
            return false;
        }
        int fuel = brewing.myBlockData.getInt("Fuel");
        brewing.myBlockData.putInt("Fuel", Math.max(0, fuel - 1));
        spawnBrewingIngredient(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SOUL_SAND), brewing, waterSlot, 2);
        return true;
    }

    /** 开始变形解药酿造 */
    private boolean tryStartAntidote(AwakeningFallingBlockEntity brewing) {
        int slot = findHauntedInBrewing(brewing.myBlockData);
        if (slot < 0) slot = pullHauntedFromChest(brewing);
        if (slot < 0) { return false; }
        if (!takeOneItemFrom(this.myBlockData, s -> s.is(net.minecraft.world.item.Items.FERMENTED_SPIDER_EYE))) {
            return false;
        }
        int fuel = brewing.myBlockData.getInt("Fuel");
        brewing.myBlockData.putInt("Fuel", Math.max(0, fuel - 1));
        spawnBrewingIngredient(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FERMENTED_SPIDER_EYE), brewing, slot, 3);
        return true;
    }

    /** 查找酿造台 0..2 槽中第一个空槽位。 */
    private int findFreeBrewSlot(CompoundTag data) {
        if (data == null) return -1;
        net.minecraft.nbt.ListTag items = getOrCreateItems(data);
        boolean[] occupied = { false, false, false };
        for (int i = 0; i < items.size(); i++) {
            int s = items.getCompound(i).getInt("Slot");
            if (s >= 0 && s <= 2) occupied[s] = true;
        }
        for (int s = 0; s <= 2; s++) if (!occupied[s]) return s;
        return -1;
    }

    /** 从箱子中查找粗制药水并移入酿造台瓶槽（替换已有药水或放入空槽），返回槽位；未找到则返回 -1。 */
    private int pullAwkwardFromChest(AwakeningFallingBlockEntity brewing) {
        return pullPotionFromChest(brewing, net.minecraft.world.item.alchemy.Potions.AWKWARD);
    }

    /** 从箱子中查找缠魂的药水并移入酿造台瓶槽，返回槽位；未找到则返回 -1。 */
    private int pullHauntedFromChest(AwakeningFallingBlockEntity brewing) {
        return pullPotionFromChest(brewing, cn.autoforged.joes_addons_for_abmc.potion.ModPotions.HAUNTED);
    }

    /** 从箱子中查找指定药水类型并移入酿造台瓶槽（空槽优先，否则替换非粗制/非缠魂药水），返回槽位。
     *  只移动 1 瓶，不会移动整叠。 */
    private int pullPotionFromChest(AwakeningFallingBlockEntity brewing,
                                     net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> targetPotion) {
        if (this.myBlockData == null || brewing.myBlockData == null) return -1;
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag chestItems = getOrCreateItems(this.myBlockData);
        for (int i = 0; i < chestItems.size(); i++) {
            var opt = net.minecraft.world.item.ItemStack.parse(reg, chestItems.getCompound(i));
            if (opt.isEmpty()) continue;
            net.minecraft.world.item.alchemy.PotionContents pc =
                opt.get().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    net.minecraft.world.item.alchemy.PotionContents.EMPTY);
            if (!pc.is(targetPotion)) continue;
            // 找到了：从箱子消耗1瓶，移入酿造台
            net.minecraft.world.item.ItemStack chestStack = opt.get();
            net.minecraft.world.item.ItemStack one = chestStack.split(1);
            if (chestStack.isEmpty()) {
                chestItems.remove(i);
            } else {
                chestItems.set(i, chestStack.save(reg));
            }
            net.minecraft.nbt.ListTag brewItems = getOrCreateItems(brewing.myBlockData);
            boolean[] occupied = { false, false, false };
            for (int j = 0; j < brewItems.size(); j++) {
                int s = brewItems.getCompound(j).getInt("Slot");
                if (s >= 0 && s <= 2) occupied[s] = true;
            }
            // 优先找空槽
            int freeSlot = -1;
            for (int s = 0; s <= 2; s++) if (!occupied[s]) { freeSlot = s; break; }
            if (freeSlot >= 0) {
                net.minecraft.nbt.CompoundTag tag = (net.minecraft.nbt.CompoundTag) one.save(reg);
                tag.putInt("Slot", freeSlot);
                brewItems.add(tag);
                brewing.myBlockData.put("Items", brewItems);
                this.myBlockData.put("Items", chestItems);
                brewing.syncBrewBottles();
                return freeSlot;
            }
            // 无空槽：替换第一个非粗制/非缠魂/非水瓶的瓶槽
            for (int j = 0; j < brewItems.size(); j++) {
                int s = brewItems.getCompound(j).getInt("Slot");
                if (s < 0 || s > 2) continue;
                var bOpt = net.minecraft.world.item.ItemStack.parse(reg, brewItems.getCompound(j));
                if (bOpt.isEmpty()) continue;
                net.minecraft.world.item.alchemy.PotionContents bPc =
                    bOpt.get().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                        net.minecraft.world.item.alchemy.PotionContents.EMPTY);
                if (bPc.is(net.minecraft.world.item.alchemy.Potions.AWKWARD)) continue;
                if (bPc.is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.HAUNTED)) continue;
                if (bPc.is(net.minecraft.world.item.alchemy.Potions.WATER)) continue;
                // 交换：旧药水回箱子，1瓶新药水进酿造台
                net.minecraft.nbt.CompoundTag brewTag = brewItems.getCompound(j);
                brewTag.putInt("Slot", -1); // 箱子不限槽位
                chestItems.add(brewTag);
                net.minecraft.nbt.CompoundTag newTag = (net.minecraft.nbt.CompoundTag) one.save(reg);
                newTag.putInt("Slot", s);
                brewItems.set(j, newTag);
                brewing.myBlockData.put("Items", brewItems);
                this.myBlockData.put("Items", chestItems);
                brewing.syncBrewBottles();
                return s;
            }
            // 酿造台三瓶都不可替换：把1瓶放回箱子
            net.minecraft.world.item.ItemStack remaining = tryAddToData(this.myBlockData, one);
            if (!remaining.isEmpty()) {
                // 放回箱子失败，丢弃在地上
                if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                        sl, this.getX(), this.getY() + 0.5, this.getZ(), remaining);
                    drop.setPickUpDelay(40);
                    sl.addFreshEntity(drop);
                }
            }
            return -1;
        }
        return -1;
    }

    /** 查找酿造台 0..2 槽中粗制药水的位置。 */
    private int findAwkwardInBrewing(CompoundTag data) {
        if (data == null) return -1;
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag items = getOrCreateItems(data);
        for (int i = 0; i < items.size(); i++) {
            int s = items.getCompound(i).getInt("Slot");
            if (s < 0 || s > 2) continue;
            var opt = net.minecraft.world.item.ItemStack.parse(reg, items.getCompound(i));
            if (opt.isPresent()) {
                net.minecraft.world.item.alchemy.PotionContents pc =
                    opt.get().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                        net.minecraft.world.item.alchemy.PotionContents.EMPTY);
                if (pc.is(net.minecraft.world.item.alchemy.Potions.AWKWARD)) return s;
            }
        }
        return -1;
    }

    /** 酿造传送药水：粗制药水 + 末影珍珠 → 传送药水（自动加火药→喷溅型） */
    private boolean tryBrewTransportation(AwakeningFallingBlockEntity brewing) {
        // 找到粗制药水槽位
        int slot = findAwkwardInBrewing(brewing.myBlockData);
        if (slot < 0) slot = pullAwkwardFromChest(brewing);
        if (slot < 0) return false;
        // 消耗末影珍珠
        if (!takeOneItemFrom(this.myBlockData, s -> s.is(net.minecraft.world.item.Items.ENDER_PEARL))) {
            return false;
        }
        spawnBrewingAnimation(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL));
        // 消耗燃料
        int fuel = brewing.myBlockData.getInt("Fuel");
        brewing.myBlockData.putInt("Fuel", Math.max(0, fuel - 1));
        // 产出传送药水
        net.minecraft.world.item.ItemStack result = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION);
        result.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
            new net.minecraft.world.item.alchemy.PotionContents(
                java.util.Optional.of(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSPORTATION),
                java.util.Optional.empty(), java.util.List.of()));
        // 自动加火药转为喷溅型
        result = tryMakeSplash(result);
        putItemToSlot(brewing.myBlockData, slot, result);
        brewing.syncBrewBottles();
        return true;
    }

    /** 酿造缠魂的药水：水瓶 + 灵魂沙 → 缠魂的药水 */
    private boolean tryBrewHaunted(AwakeningFallingBlockEntity brewing) {
        // 找到水瓶槽位（酿造台或箱子）
        int waterSlot = findWaterInBrewingSlots(brewing.myBlockData);
        if (waterSlot < 0) {
            // 从箱子找水瓶并移入酿造台
            waterSlot = pullWaterFromChest(brewing);
        }
        if (waterSlot < 0) return false;
        // 消耗灵魂沙
        if (!takeOneItemFrom(this.myBlockData, s -> s.is(net.minecraft.world.item.Items.SOUL_SAND))) {
            return false;
        }
        spawnBrewingAnimation(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SOUL_SAND));
        // 消耗燃料
        int fuel = brewing.myBlockData.getInt("Fuel");
        brewing.myBlockData.putInt("Fuel", Math.max(0, fuel - 1));
        // 产出缠魂的药水
        net.minecraft.world.item.ItemStack result = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION);
        result.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
            new net.minecraft.world.item.alchemy.PotionContents(
                java.util.Optional.of(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.HAUNTED),
                java.util.Optional.empty(), java.util.List.of()));
        putItemToSlot(brewing.myBlockData, waterSlot, result);
        brewing.syncBrewBottles();
        return true;
    }

    /** 从箱子中查找水瓶并移入酿造台瓶槽，返回槽位；未找到则返回 -1。 */
    private int pullWaterFromChest(AwakeningFallingBlockEntity brewing) {
        if (this.myBlockData == null || brewing.myBlockData == null) return -1;
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag chestItems = getOrCreateItems(this.myBlockData);
        for (int i = 0; i < chestItems.size(); i++) {
            var opt = net.minecraft.world.item.ItemStack.parse(reg, chestItems.getCompound(i));
            if (opt.isEmpty()) continue;
            if (!opt.get().is(net.minecraft.world.item.Items.POTION)) continue;
            net.minecraft.world.item.alchemy.PotionContents pc =
                opt.get().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    net.minecraft.world.item.alchemy.PotionContents.EMPTY);
            if (!pc.is(net.minecraft.world.item.alchemy.Potions.WATER)) continue;
            // 移入酿造台空瓶槽或替换非粗制/非缠魂药水
            return pullPotionFromChest(brewing, net.minecraft.world.item.alchemy.Potions.WATER);
        }
        return -1;
    }

    /** 酿造变形解药：缠魂的药水 + 发酵蛛眼 → 变形解药（自动加火药→喷溅型） */
    private boolean tryBrewAntidote(AwakeningFallingBlockEntity brewing) {
        // 找到缠魂的药水槽位
        int slot = findHauntedInBrewing(brewing.myBlockData);
        if (slot < 0) slot = pullHauntedFromChest(brewing);
        if (slot < 0) return false;
        // 消耗发酵蛛眼
        if (!takeOneItemFrom(this.myBlockData, s -> s.is(net.minecraft.world.item.Items.FERMENTED_SPIDER_EYE))) {
            return false;
        }
        spawnBrewingAnimation(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FERMENTED_SPIDER_EYE));
        // 消耗燃料
        int fuel = brewing.myBlockData.getInt("Fuel");
        brewing.myBlockData.putInt("Fuel", Math.max(0, fuel - 1));
        // 产出变形解药
        net.minecraft.world.item.ItemStack result = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION);
        result.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
            new net.minecraft.world.item.alchemy.PotionContents(
                java.util.Optional.of(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSMUTATION_ANTIDOTE),
                java.util.Optional.empty(), java.util.List.of()));
        // 自动加火药转为喷溅型
        result = tryMakeSplash(result);
        putItemToSlot(brewing.myBlockData, slot, result);
        brewing.syncBrewBottles();
        return true;
    }

    /** 查找酿造台 0..2 槽中缠魂的药水的位置。 */
    private int findHauntedInBrewing(CompoundTag data) {
        if (data == null) return -1;
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag items = getOrCreateItems(data);
        for (int i = 0; i < items.size(); i++) {
            int s = items.getCompound(i).getInt("Slot");
            if (s < 0 || s > 2) continue;
            var opt = net.minecraft.world.item.ItemStack.parse(reg, items.getCompound(i));
            if (opt.isPresent()) {
                net.minecraft.world.item.alchemy.PotionContents pc =
                    opt.get().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                        net.minecraft.world.item.alchemy.PotionContents.EMPTY);
                if (pc.is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.HAUNTED)) return s;
            }
        }
        return -1;
    }

    /** 尝试从箱子消耗火药，将药水转为喷溅型。若箱子无火药则保持原样。
     *  火药静默消耗（不丢飞行物），避免与酿造原料的飞行物冲突。 */
    private net.minecraft.world.item.ItemStack tryMakeSplash(net.minecraft.world.item.ItemStack potion) {
        if (takeOneItemFrom(this.myBlockData, s -> s.is(net.minecraft.world.item.Items.GUNPOWDER))) {
            net.minecraft.world.item.ItemStack splash = new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.SPLASH_POTION);
            splash.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                potion.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS));
            return splash;
        }
        return potion;
    }

    // ==================== 物品拾取 ====================

    /** 箱子→酿造台物品飞行过场动画，与唤醒时装填药水同款。 */
    private void spawnBrewingAnimation(net.minecraft.world.item.ItemStack ingredient) {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        net.minecraft.world.entity.item.ItemEntity e = new net.minecraft.world.entity.item.ItemEntity(
            sl, this.getX(), this.getY() + 0.5, this.getZ(), ingredient.copy());
        e.setDeltaMovement(0.0, 0.5, 0.0);
        e.setNoGravity(false);
        e.setPickUpDelay(32767);
        e.lifespan = 100;
        e.getPersistentData().putBoolean("jafa_no_pickup", true);
        sl.addFreshEntity(e);
    }

    /** 扫描附近 ItemEntity 并收纳到箱子。 */
    private void pickupNearbyItems() {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        double range = 1.8;
        java.util.List<net.minecraft.world.entity.item.ItemEntity> items =
            this.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                this.getBoundingBox().inflate(range));
        for (net.minecraft.world.entity.item.ItemEntity ie : items) {
            if (!ie.isAlive() || ie.isRemoved()) continue;
            // 跳过拾取延迟未结束的（避免拾取自己刚扔出的）
            if (ie.hasPickUpDelay()) continue;
            net.minecraft.world.item.ItemStack stack = ie.getItem();
            if (stack.isEmpty()) continue;
            // 尝试插入箱子
            net.minecraft.world.item.ItemStack remaining = tryAddToData(this.myBlockData, stack.copy());
            if (remaining.isEmpty()) {
                // 全部插入成功
                ie.discard();
                sl.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.2F,
                    (sl.random.nextFloat() - sl.random.nextFloat()) * 0.7F + 1.0F);
            } else if (remaining.getCount() < stack.getCount()) {
                // 部分插入
                ie.setItem(remaining);
                sl.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.2F,
                    (sl.random.nextFloat() - sl.random.nextFloat()) * 0.7F + 1.0F);
            }
            // 箱子满了：remaining.getCount() == stack.getCount()，不拾取，item 留在原地
        }
    }

    private int findWaterInData(CompoundTag data) {
        if (data == null) return -1;
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag items = getOrCreateItems(data);
        for (int i = 0; i < items.size(); i++) {
            var opt = net.minecraft.world.item.ItemStack.parse(reg, items.getCompound(i));
            if (opt.isPresent() && isWaterBottle(opt.get())) return i;
        }
        return -1;
    }

    private int findWaterInBrewingSlots(CompoundTag data) {
        if (data == null) return -1;
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag items = getOrCreateItems(data);
        for (int i = 0; i < items.size(); i++) {
            int slot = items.getCompound(i).getInt("Slot");
            if (slot < 0 || slot > 2) continue;
            var opt = net.minecraft.world.item.ItemStack.parse(reg, items.getCompound(i));
            if (opt.isPresent() && isWaterBottle(opt.get())) return slot;
        }
        return -1;
    }

    private void addToData(CompoundTag data, net.minecraft.world.item.ItemStack stack) {
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        getOrCreateItems(data).add(stack.save(reg));
    }

    /** 尝试将物品插入箱子 NBT，优先合并到已有同类物品。返回未插入的剩余。 */
    private net.minecraft.world.item.ItemStack tryAddToData(CompoundTag data, net.minecraft.world.item.ItemStack stack) {
        if (data == null) return stack;
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag items = getOrCreateItems(data);
        // 先尝试合并到已有同类物品
        net.minecraft.world.item.ItemStack remaining = stack.copy();
        for (int i = 0; i < items.size(); i++) {
            var opt = net.minecraft.world.item.ItemStack.parse(reg, items.getCompound(i));
            if (opt.isEmpty()) continue;
            net.minecraft.world.item.ItemStack existing = opt.get();
            if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(existing, remaining)) continue;
            int maxSize = existing.getMaxStackSize();
            int space = maxSize - existing.getCount();
            if (space <= 0) continue;
            int toMove = Math.min(space, remaining.getCount());
            existing.grow(toMove);
            remaining.shrink(toMove);
            items.set(i, existing.save(reg));
            if (remaining.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
        }
        // 未完全合并，尝试新增槽位
        if (!remaining.isEmpty()) {
            // 箱子最多 27 槽
            if (items.size() >= 27) return remaining;
            // 找到第一个未使用的槽位编号（0-26）
            java.util.Set<Integer> usedSlots = new java.util.HashSet<>();
            for (int i = 0; i < items.size(); i++) {
                usedSlots.add(items.getCompound(i).getInt("Slot"));
            }
            int newSlot = 0;
            while (usedSlots.contains(newSlot) && newSlot < 27) newSlot++;
            if (newSlot >= 27) return remaining; // 所有槽位都被占用
            net.minecraft.nbt.CompoundTag tag = (net.minecraft.nbt.CompoundTag) remaining.save(reg);
            tag.putByte("Slot", (byte) newSlot);
            items.add(tag);
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    /** 从容器 NBT 的指定槽位移除 1 个指定物品；成功返回 true。 */
    private boolean removeFromSlot(CompoundTag data, int slot, net.minecraft.world.item.Item item) {
        if (data == null) return false;
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag items = getOrCreateItems(data);
        for (int i = 0; i < items.size(); i++) {
            if (items.getCompound(i).getInt("Slot") != slot) continue;
            var opt = net.minecraft.world.item.ItemStack.parse(reg, items.getCompound(i));
            if (opt.isEmpty()) continue;
            net.minecraft.world.item.ItemStack st = opt.get();
            if (!st.is(item)) continue;
            st.shrink(1);
            if (st.isEmpty()) items.remove(i);
            else { items.getCompound(i).putInt("count", st.getCount()); items.set(i, items.getCompound(i)); }
            return true;
        }
        return false;
    }

    /** 从箱子或酿造台取一瓶“增益且非传送/变形/觉醒且不在黑名单”的药水并移除；无则返回空栈。 */
    private net.minecraft.world.item.ItemStack takeBeneficialPotion(AwakeningFallingBlockEntity brewing) {
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> ok =
            s -> isBeneficialNotForbidden(s) && !isBlacklistedPotion(s);
        net.minecraft.world.item.ItemStack fromChest = takePotionFrom(this.myBlockData, ok);
        if (!fromChest.isEmpty()) return fromChest;
        return takePotionFrom(brewing.myBlockData, ok);
    }

    /** 从容器 NBT 中取一瓶满足条件的药水并移除（返回单份副本）。 */
    private net.minecraft.world.item.ItemStack takePotionFrom(CompoundTag data,
                                                              java.util.function.Predicate<net.minecraft.world.item.ItemStack> filter) {
        if (data == null) return net.minecraft.world.item.ItemStack.EMPTY;
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag items = getOrCreateItems(data);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag it = items.getCompound(i);
            var opt = net.minecraft.world.item.ItemStack.parse(reg, it);
            if (opt.isEmpty()) continue;
            net.minecraft.world.item.ItemStack st = opt.get();
            if (!isPotionItem(st.getItem()) || !filter.test(st)) continue;
            // 先复制要投出的一份，再扣减原栈；避免对已减到 0 的空栈 copyWithCount 返回 EMPTY，
            // 导致药水被从容器移除却无返回物可用（凭空消失）。
            net.minecraft.world.item.ItemStack result = st.copyWithCount(1);
            st.shrink(1);
            if (st.isEmpty()) items.remove(i);
            else { it.putInt("count", st.getCount()); items.set(i, it); }
            return result;
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    /** 是否为“增益且非传送/变形/觉醒”的药水。 */
    private boolean isBeneficialNotForbidden(net.minecraft.world.item.ItemStack st) {
        net.minecraft.world.item.alchemy.PotionContents pc =
            st.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                net.minecraft.world.item.alchemy.PotionContents.EMPTY);
        // 排除传送/变形/觉醒药水
        var potion = pc.potion();
        if (potion.isPresent()) {
            if (potion.get().is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSPORTATION)
                || potion.get().is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.PRE_TRANSPORTATION)
                || potion.get().is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSMUTATION)
                || potion.get().is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.LONG_TRANSMUTATION)
                || potion.get().is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.PRE_TRANSMUTATION)
                || potion.get().is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.AWAKENING)
                || potion.get().is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.LONG_AWAKENING)) {
                return false;
            }
        }
        // 必须是增益（至少一个正向效果，且不包含负面效果/伤害）
        boolean[] hasBeneficial = { false };
        boolean[] hasNonBeneficial = { false };
        java.util.List<net.minecraft.world.effect.MobEffectInstance> effects = new java.util.ArrayList<>();
        pc.forEachEffect(effects::add);
        for (net.minecraft.world.effect.MobEffectInstance ei : effects) {
            if (ei.getEffect().value().isBeneficial()) hasBeneficial[0] = true;
            else hasNonBeneficial[0] = true;
        }
        return hasBeneficial[0] && !hasNonBeneficial[0];
    }

    /** 取减益药水（至少一个有害效果，且不含增益效果，排除传送/变形/觉醒）。 */
    private net.minecraft.world.item.ItemStack takeHarmfulPotion(AwakeningFallingBlockEntity brewing) {
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> ok =
            s -> isHarmfulPotion(s) && !isBlacklistedPotion(s);
        net.minecraft.world.item.ItemStack fromChest = takePotionFrom(this.myBlockData, ok);
        if (!fromChest.isEmpty()) return fromChest;
        return takePotionFrom(brewing.myBlockData, ok);
    }

    /** 是否为减益药水：至少有 1 个非 beneficial 效果，且无 beneficial 效果。 */
    private boolean isHarmfulPotion(net.minecraft.world.item.ItemStack st) {
        net.minecraft.world.item.alchemy.PotionContents pc = st.getOrDefault(
            net.minecraft.core.component.DataComponents.POTION_CONTENTS,
            net.minecraft.world.item.alchemy.PotionContents.EMPTY);
        var potion = pc.potion();
        // 排除传送/变形/觉醒
        if (potion.isPresent()) {
            if (potion.get().is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSPORTATION)
                || potion.get().is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.PRE_TRANSPORTATION)
                || potion.get().is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSMUTATION)
                || potion.get().is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.LONG_TRANSMUTATION)
                || potion.get().is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.PRE_TRANSMUTATION)
                || potion.get().is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.AWAKENING)
                || potion.get().is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.LONG_AWAKENING)) {
                return false;
            }
        }
        boolean[] hasBeneficial = { false };
        boolean[] hasHarmful = { false };
        java.util.List<net.minecraft.world.effect.MobEffectInstance> effects = new java.util.ArrayList<>();
        pc.forEachEffect(effects::add);
        for (net.minecraft.world.effect.MobEffectInstance ei : effects) {
            if (ei.getEffect().value().isBeneficial()) hasBeneficial[0] = true;
            else hasHarmful[0] = true;
        }
        return hasHarmful[0] && !hasBeneficial[0];
    }

    /** 取变形药水（TRANSMUTATION 或 LONG_TRANSMUTATION）。 */
    private net.minecraft.world.item.ItemStack takeTransmutationPotion(AwakeningFallingBlockEntity brewing) {
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> ok = this::isTransmutationPotion;
        net.minecraft.world.item.ItemStack fromChest = takePotionFrom(this.myBlockData, ok);
        if (!fromChest.isEmpty()) return fromChest;
        return takePotionFrom(brewing.myBlockData, ok);
    }

    private boolean isTransmutationPotion(net.minecraft.world.item.ItemStack st) {
        if (!isPotionItem(st.getItem())) return false;
        net.minecraft.world.item.alchemy.PotionContents pc = st.getOrDefault(
            net.minecraft.core.component.DataComponents.POTION_CONTENTS,
            net.minecraft.world.item.alchemy.PotionContents.EMPTY);
        return pc.potion().map(h ->
            h.is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSMUTATION)
                || h.is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.LONG_TRANSMUTATION)
        ).orElse(false);
    }

    /** 取任意非黑名单、非增益药水（包括减益和中性，用于敌对模式近距）。 */
    private net.minecraft.world.item.ItemStack takeAnyNonBeneficial(AwakeningFallingBlockEntity brewing) {
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> ok =
            s -> !isBeneficialNotForbidden(s) && !isBlacklistedPotion(s);
        net.minecraft.world.item.ItemStack fromChest = takePotionFrom(this.myBlockData, ok);
        if (!fromChest.isEmpty()) return fromChest;
        return takePotionFrom(brewing.myBlockData, ok);
    }

    /** 寻找水平范围内、高度差允许的最近玩家。 */
    private Player findNearestPlayer() {
        Player best = null;
        double bestSqr = (double) HOP_TARGET_RANGE * HOP_TARGET_RANGE;
        for (Player p : this.level().getEntitiesOfClass(Player.class,
            this.getBoundingBox().inflate(HOP_TARGET_RANGE))) {
            if (p.isCreative() || p.isSpectator()) continue; // 无视创造/旁观者玩家
            double d = this.distanceToSqr(p);
            if (d < bestSqr && Math.abs(p.getY() - this.getY()) <= HOP_TARGET_MAX_DY) {
                bestSqr = d;
                best = p;
            }
        }
        return best;
    }

    /** 主人的 UUID（字符串形式，可能为空表示无主人）。 */
    private String getOwnerUuid() {
        return this.getPersistentData().getString(cn.autoforged.joes_addons_for_abmc.ModMain.AWAKENING_OWNER_TAG);
    }

    /** 解析主人实体：先按玩家，再全范围扫其他 LivingEntity；找不到返回 null。 */
    private net.minecraft.world.entity.LivingEntity getOwner() {
        String s = getOwnerUuid();
        if (s == null || s.isEmpty()) return null;
        try {
            java.util.UUID uid = java.util.UUID.fromString(s);
            // 玩家
            Player p = this.level().getPlayerByUUID(uid);
            if (p != null) return p;
            // 其他活体
            if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                for (net.minecraft.world.entity.LivingEntity le
                    : sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        this.getBoundingBox().inflate(256.0))) {
                    if (le.getUUID().equals(uid)) return le;
                }
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    /** 判断主人是否为女巫Boss。 */
    private boolean isOwnerWitchBoss() {
        net.minecraft.world.entity.LivingEntity owner = getOwner();
        return owner instanceof net.minecraft.world.entity.monster.Witch w
            && w.getPersistentData().getBoolean(cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_TAG);
    }

    /** 统计组合体（箱子+酿造台）中指定类型药水的数量。
     *  type: "transmutation" / "antidote" / "transport" */
    private int countPotionsOfType(String type) {
        int count = 0;
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> pred;
        if ("transmutation".equals(type)) {
            pred = this::isTransmutationPotion;
        } else if ("antidote".equals(type)) {
            pred = this::isAntidotePotion;
        } else if ("transport".equals(type)) {
            pred = this::isTransportPotion;
        } else {
            return 0;
        }
        count += countPotionsInData(this.myBlockData, pred);
        AwakeningFallingBlockEntity brewing = partnerBrewing();
        if (brewing != null) {
            count += countPotionsInData(brewing.myBlockData, pred);
        }
        return count;
    }

    private int countPotionsInData(CompoundTag data,
            java.util.function.Predicate<net.minecraft.world.item.ItemStack> filter) {
        if (data == null) return 0;
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag items = getOrCreateItems(data);
        int count = 0;
        for (int i = 0; i < items.size(); i++) {
            var opt = net.minecraft.world.item.ItemStack.parse(reg, items.getCompound(i));
            if (opt.isEmpty()) continue;
            net.minecraft.world.item.ItemStack st = opt.get();
            if (isPotionItem(st.getItem()) && filter.test(st)) count += st.getCount();
        }
        return count;
    }

    private boolean isAntidotePotion(net.minecraft.world.item.ItemStack st) {
        if (!isPotionItem(st.getItem())) return false;
        net.minecraft.world.item.alchemy.PotionContents pc = st.getOrDefault(
            net.minecraft.core.component.DataComponents.POTION_CONTENTS,
            net.minecraft.world.item.alchemy.PotionContents.EMPTY);
        return pc.potion().map(h ->
            h.is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSMUTATION_ANTIDOTE)
        ).orElse(false);
    }

    private boolean isTransportPotion(net.minecraft.world.item.ItemStack st) {
        if (!isPotionItem(st.getItem())) return false;
        net.minecraft.world.item.alchemy.PotionContents pc = st.getOrDefault(
            net.minecraft.core.component.DataComponents.POTION_CONTENTS,
            net.minecraft.world.item.alchemy.PotionContents.EMPTY);
        return pc.potion().map(h ->
            h.is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSPORTATION)
        ).orElse(false);
    }

    /** 友好模式·主人为女巫Boss：直接修改女巫弹药数据，同时消耗自身储备的一瓶对应药水。
     *  优先补给储量最低的类型。每次仅补给1瓶。 */
    private void supplyWitchBossAmmo(AwakeningFallingBlockEntity brewing,
            net.minecraft.world.entity.monster.Witch witch) {
        // 三类弹药当前值与最大值
        int tCur = cn.autoforged.joes_addons_for_abmc.ModMain.getWitchBossAmmo(witch,
            cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_AMMO_TRANSMUTATION_TAG);
        int aCur = cn.autoforged.joes_addons_for_abmc.ModMain.getWitchBossAmmo(witch,
            cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_AMMO_ANTIDOTE_TAG);
        int pCur = cn.autoforged.joes_addons_for_abmc.ModMain.getWitchBossAmmo(witch,
            cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_AMMO_TRANSPORT_TAG);
        // 优先补给未满且储量最低的类型
        String bestType = null;
        int bestDeficit = 0;
        int tMax = cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_AMMO_TRANSMUTATION_MAX;
        int aMax = cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_AMMO_ANTIDOTE_MAX;
        int pMax = cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_AMMO_TRANSPORT_MAX;
        if (tCur < tMax && tMax - tCur > bestDeficit) { bestDeficit = tMax - tCur; bestType = "transmutation"; }
        if (aCur < aMax && aMax - aCur > bestDeficit) { bestDeficit = aMax - aCur; bestType = "antidote"; }
        if (pCur < pMax && pMax - pCur > bestDeficit) { bestDeficit = pMax - pCur; bestType = "transport"; }
        if (bestType == null) return; // 全满
        // 尝试从箱子或酿造台取一瓶对应药水
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> pred;
        String ammoTag;
        if ("transmutation".equals(bestType)) {
            pred = this::isTransmutationPotion;
            ammoTag = cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_AMMO_TRANSMUTATION_TAG;
        } else if ("antidote".equals(bestType)) {
            pred = this::isAntidotePotion;
            ammoTag = cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_AMMO_ANTIDOTE_TAG;
        } else {
            pred = this::isTransportPotion;
            ammoTag = cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_AMMO_TRANSPORT_TAG;
        }
        net.minecraft.world.item.ItemStack taken = takePotionFrom(this.myBlockData, pred);
        if (taken.isEmpty() && brewing != null) {
            taken = takePotionFrom(brewing.myBlockData, pred);
        }
        if (taken.isEmpty()) return;
        // 消耗成功：直接增加女巫弹药+1
        cn.autoforged.joes_addons_for_abmc.ModMain.addWitchBossAmmo(witch, ammoTag, 1);
        if (brewing != null) brewing.syncBrewBottles();
    }

    /** 根据女巫Boss的弹药储量自动切换援助模式：
     *  友好模式(0)：变形药水/解药/传送药水均低于一半且组合体能够补给至一半及以上
     *  敌对模式(1)：女巫任一弹药超过1/3或组合体补给量不足以恢复至一半以上
     *  永远不会切换为"无"模式(2) */
    private void updateWitchBossAssistMode() {
        net.minecraft.world.entity.LivingEntity owner = getOwner();
        if (!(owner instanceof net.minecraft.world.entity.monster.Witch witch)) return;
        if (!witch.getPersistentData().getBoolean(cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_TAG)) return;
        int tCur = cn.autoforged.joes_addons_for_abmc.ModMain.getWitchBossAmmo(witch,
            cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_AMMO_TRANSMUTATION_TAG);
        int aCur = cn.autoforged.joes_addons_for_abmc.ModMain.getWitchBossAmmo(witch,
            cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_AMMO_ANTIDOTE_TAG);
        int pCur = cn.autoforged.joes_addons_for_abmc.ModMain.getWitchBossAmmo(witch,
            cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_AMMO_TRANSPORT_TAG);
        int tMax = cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_AMMO_TRANSMUTATION_MAX;
        int aMax = cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_AMMO_ANTIDOTE_MAX;
        int pMax = cn.autoforged.joes_addons_for_abmc.ModMain.WITCH_BOSS_AMMO_TRANSPORT_MAX;
        // 敌对条件1：任一弹药超过1/3
        if (tCur > tMax / 3 || aCur > aMax / 3 || pCur > pMax / 3) {
            this.assistMode = 1;
            return;
        }
        // 友好条件：三类均低于一半
        boolean allBelowHalf = tCur < tMax / 2 && aCur < aMax / 2 && pCur < pMax / 2;
        if (!allBelowHalf) {
            // 有类型在1/3到1/2之间 → 敌对
            this.assistMode = 1;
            return;
        }
        // 检查组合体储备是否足够补给至一半及以上
        int tNeed = Math.max(0, tMax / 2 - tCur);
        int aNeed = Math.max(0, aMax / 2 - aCur);
        int pNeed = Math.max(0, pMax / 2 - pCur);
        int tHave = countPotionsOfType("transmutation");
        int aHave = countPotionsOfType("antidote");
        int pHave = countPotionsOfType("transport");
        if (tHave >= tNeed && aHave >= aNeed && pHave >= pNeed) {
            this.assistMode = 0; // 友好模式
        } else {
            this.assistMode = 1; // 补给量不足 → 敌对
        }
    }

    /** 跟随目标位置：敌对模式跟随主人的索敌目标；无/友好模式优先命名纸坐标，
     *  其次主人，再其次最近玩家。 */
    private net.minecraft.world.phys.Vec3 followPos() {
        // 敌对模式：跟随主人的索敌目标（攻击目标/被攻击对象），无目标时回退到主人
        if (this.assistMode == 1) {
            net.minecraft.world.entity.LivingEntity enemy = findEnemy();
            if (enemy != null) return enemy.position();
        }
        if (this.homePos != null) {
            return net.minecraft.world.phys.Vec3.atCenterOf(this.homePos);
        }
        net.minecraft.world.entity.LivingEntity owner = getOwner();
        if (owner != null) return owner.position();
        Player nearest = findNearestPlayer();
        return nearest != null ? nearest.position() : null;
    }

    // ==================== 变形解药/敌人变形跟踪 ====================

    /** 检查主人是否被方块/物品变形药水影响，如果是则优先丢变形解药。 */
    private boolean tryThrowAntidoteToOwner(AwakeningFallingBlockEntity brewing, net.minecraft.world.entity.LivingEntity owner) {
        if (!(owner instanceof net.minecraft.world.entity.player.Player player)) {
            return false;
        }
        java.util.UUID uid = player.getUUID();
        boolean inSet = cn.autoforged.joes_addons_for_abmc.ModMain.isPlayerTransmuted(uid);
        // 兜底：检查玩家持久化数据中的变形剩余时间（TRANSMUTED_ENTITIES 可能因时序问题未包含）
        boolean hasNbt = player.getPersistentData().contains("jafa_morph_remaining")
            && player.getPersistentData().getInt("jafa_morph_remaining") > 0;
        if (!inSet && !hasNbt) return false;
        // 检查变形形态：必须为方块/物品
        boolean isBlockOrItem = cn.autoforged.joes_addons_for_abmc.ModMain.isPlayerTransmutedAsBlockOrItem(uid);
        if (!isBlockOrItem && hasNbt) {
            // TRANSMUTED_ENTITIES 未包含，但 NBT 有数据：从 NBT 读取形态
            String formStr = player.getPersistentData().getString("jafa_morph_form");
            isBlockOrItem = "BLOCK".equals(formStr) || "ITEM".equals(formStr);
        }
        if (!isBlockOrItem) {
            return false;
        }
        // 取变形解药：优先酿造台，其次箱子（换入酿造台后取用）
        net.minecraft.world.item.ItemStack antidote = takeAntidotePotion(brewing);
        if (antidote.isEmpty()) {
            antidote = pullAntidoteFromChest(brewing);
            if (antidote.isEmpty()) {
                return false;
            }
            // 换入后从酿造台取出
            antidote = takeAntidotePotion(brewing);
            if (antidote.isEmpty()) {
                return false;
            }
            brewing.syncBrewBottles();
        }
        // 解药始终用溅射药水，确保砸到玩家（被变形的方块/物品）上生效
        throwRealSplash(owner, antidote);
        brewing.syncBrewBottles();
        return true;
    }

    /** 主人本体已被变形并 discard（只剩方块/物品壳）时，向其壳位置丢变形解药。 */
    private boolean tryThrowAntidoteToOwnerShell(AwakeningFallingBlockEntity brewing) {
        if (brewing.myBlockData == null) return false;
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return false;
        String s = getOwnerUuid();
        if (s == null || s.isEmpty()) return false;
        java.util.UUID uid;
        try {
            uid = java.util.UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return false;
        }
        // 先按壳实体（下落方块/物品实体）找，找不到再按已落地静态方块/物品的位置找
        net.minecraft.world.entity.Entity shell = cn.autoforged.joes_addons_for_abmc.ModMain.findTransmutedShell(sl, uid);
        net.minecraft.core.BlockPos targetPos = shell != null
            ? shell.blockPosition()
            : cn.autoforged.joes_addons_for_abmc.ModMain.findTransmutedBlockPos(sl, uid);
        if (targetPos == null) return false;
        // 取变形解药：优先酿造台，其次箱子（换入酿造台后取用）
        net.minecraft.world.item.ItemStack antidote = takeAntidotePotion(brewing);
        if (antidote.isEmpty()) {
            antidote = pullAntidoteFromChest(brewing);
            if (antidote.isEmpty()) {
                return false;
            }
            antidote = takeAntidotePotion(brewing);
            if (antidote.isEmpty()) {
                return false;
            }
            brewing.syncBrewBottles();
        }
        throwRealSplashToward(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, antidote);
        brewing.syncBrewBottles();
        return true;
    }

    /** 从酿造台取一瓶变形解药。 */
    private net.minecraft.world.item.ItemStack takeAntidotePotion(AwakeningFallingBlockEntity brewing) {
        if (brewing.myBlockData == null) return net.minecraft.world.item.ItemStack.EMPTY;
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag items = getOrCreateItems(brewing.myBlockData);
        for (int i = 0; i < items.size(); i++) {
            int s = items.getCompound(i).getInt("Slot");
            if (s < 0 || s > 2) continue;
            var opt = net.minecraft.world.item.ItemStack.parse(reg, items.getCompound(i));
            if (opt.isEmpty()) continue;
            net.minecraft.world.item.alchemy.PotionContents pc =
                opt.get().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    net.minecraft.world.item.alchemy.PotionContents.EMPTY);
            if (pc.is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSMUTATION_ANTIDOTE)) {
                items.remove(i);
                return opt.get();
            }
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    /** 从箱子中查找变形解药，若找到则与酿造台中的一瓶普通药水交换位置。返回换入酿造台的解药（用于后续取用），未找到返回空。 */
    private net.minecraft.world.item.ItemStack pullAntidoteFromChest(AwakeningFallingBlockEntity brewing) {
        if (this.myBlockData == null || brewing.myBlockData == null) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        net.minecraft.core.HolderLookup.Provider reg = this.level().registryAccess();
        net.minecraft.nbt.ListTag chestItems = getOrCreateItems(this.myBlockData);
        // 在箱子中查找解药
        int chestIdx = -1;
        net.minecraft.world.item.ItemStack found = net.minecraft.world.item.ItemStack.EMPTY;
        for (int i = 0; i < chestItems.size(); i++) {
            var opt = net.minecraft.world.item.ItemStack.parse(reg, chestItems.getCompound(i));
            if (opt.isEmpty()) continue;
            net.minecraft.world.item.alchemy.PotionContents pc =
                opt.get().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    net.minecraft.world.item.alchemy.PotionContents.EMPTY);
            if (pc.is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSMUTATION_ANTIDOTE)) {
                chestIdx = i;
                found = opt.get();
                break;
            }
        }
        if (chestIdx < 0) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        // 在酿造台中找一瓶非解药药水，交换位置
        net.minecraft.nbt.ListTag brewItems = getOrCreateItems(brewing.myBlockData);
        int brewIdx = -1;
        for (int i = 0; i < brewItems.size(); i++) {
            int s = brewItems.getCompound(i).getInt("Slot");
            if (s < 0 || s > 2) continue;
            var opt = net.minecraft.world.item.ItemStack.parse(reg, brewItems.getCompound(i));
            if (opt.isEmpty()) continue;
            net.minecraft.world.item.alchemy.PotionContents pc =
                opt.get().getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    net.minecraft.world.item.alchemy.PotionContents.EMPTY);
            if (!pc.is(cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSMUTATION_ANTIDOTE)) {
                brewIdx = i;
                break;
            }
        }
        if (brewIdx < 0) {
            // 酿造台三瓶全是解药（不应该），但解药在箱子里，直接把酿造台一瓶换到箱子
            for (int i = 0; i < brewItems.size(); i++) {
                int s = brewItems.getCompound(i).getInt("Slot");
                if (s >= 0 && s <= 2) { brewIdx = i; break; }
            }
        }
        if (brewIdx >= 0) {
            // 交换：箱子解药 ←→ 酿造台药水（修正 Slot 值使其适合酿造台 0-2 槽）
            net.minecraft.nbt.CompoundTag chestTag = chestItems.getCompound(chestIdx);
            net.minecraft.nbt.CompoundTag brewTag = brewItems.getCompound(brewIdx);
            int brewSlot = brewTag.getInt("Slot"); // 酿造台槽位（0-2）
            chestTag.putInt("Slot", brewSlot); // 解药换入酿造台，Slot 改为酿造台槽位
            brewTag.putInt("Slot", -1); // 普通药水换入箱子，Slot 设为 -1（箱子不限槽位）
            brewItems.set(brewIdx, chestTag);
            chestItems.set(chestIdx, brewTag);
            brewing.myBlockData.put("Items", brewItems);
            this.myBlockData.put("Items", chestItems);
            return found;
        }
        // 酿造台无空位：直接把解药移入酿造台，箱子清空该槽
        chestItems.remove(chestIdx);
        // 确保酿造台 Items 存在
        getOrCreateItems(brewing.myBlockData);
        // 解药写入酿造台 0-2 槽中第一个空位
        net.minecraft.nbt.ListTag finalBrewItems = getOrCreateItems(brewing.myBlockData);
        for (int slot = 0; slot <= 2; slot++) {
            boolean occupied = false;
            for (int i = 0; i < finalBrewItems.size(); i++) {
                if (finalBrewItems.getCompound(i).getInt("Slot") == slot) { occupied = true; break; }
            }
            if (!occupied) {
                net.minecraft.nbt.CompoundTag newTag = new net.minecraft.nbt.CompoundTag();
                newTag.putInt("Slot", slot);
                found.save(reg, newTag);
                finalBrewItems.add(newTag);
                this.myBlockData.put("Items", chestItems);
                brewing.myBlockData.put("Items", finalBrewItems);
                return found;
            }
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    /** 检查实体是否处于变形状态（玩家用 TRANSMUTED_ENTITIES，生物用效果检查）。 */
    private boolean isEntityTransmuted(net.minecraft.world.entity.LivingEntity entity) {
        if (entity instanceof net.minecraft.world.entity.player.Player) {
            return cn.autoforged.joes_addons_for_abmc.ModMain.isPlayerTransmuted(entity.getUUID());
        }
        return entity.hasEffect(cn.autoforged.joes_addons_for_abmc.potion.ModMobEffects.TRANSMUTATION);
    }

    /** 敌对模式下查找应攻击的敌人：优先主人的攻击目标（如 witchboss 的 target），
     *  其次主人正在攻击的生物，再其次最近伤害过主人的生物，最后使用持久记忆。
     *  排除主人自身。结果会写入 lastEnemyUuid 持久记忆。 */
    private net.minecraft.world.entity.LivingEntity findEnemy() {
        net.minecraft.world.entity.LivingEntity owner = getOwner();
        if (owner == null) return null;
        java.util.UUID ownerUuid = owner.getUUID();
        // 1. 若主人是 Mob（如 witchboss），优先取其当前攻击目标
        if (owner instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null) {
            net.minecraft.world.entity.LivingEntity r = acquireEnemy(mob.getTarget(), ownerUuid);
            if (r != null) return r;
        }
        // 2. 主人正在攻击的生物（玩家右击/左键的生物）
        net.minecraft.world.entity.LivingEntity ownerTarget = owner.getLastHurtMob();
        if (ownerTarget != null) {
            net.minecraft.world.entity.LivingEntity r = acquireEnemy(ownerTarget, ownerUuid);
            if (r != null) return r;
        }
        // 3. 最近伤害过主人的生物
        net.minecraft.world.entity.LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker != null) {
            net.minecraft.world.entity.LivingEntity r = acquireEnemy(attacker, ownerUuid);
            if (r != null) return r;
        }
        // 4. 持久记忆：以上都过期时，回退到上次锁定的敌人
        if (this.lastEnemyUuid != null && this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            net.minecraft.world.entity.Entity e = sl.getEntity(this.lastEnemyUuid);
            if (e instanceof net.minecraft.world.entity.LivingEntity le) {
                net.minecraft.world.entity.LivingEntity r = acquireEnemy(le, ownerUuid);
                if (r != null) return r;
            }
        }
        // 5. 等待恢复的敌人（UUID 可能因变形而变化，用位置扫描兜底）
        if (this.waitingEnemyPos != null && this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            // 先尝试 UUID 查找
            if (this.waitingEnemyUuid != null) {
                net.minecraft.world.entity.Entity e = sl.getEntity(this.waitingEnemyUuid);
                if (e instanceof net.minecraft.world.entity.LivingEntity le) {
                    // 检查是否已恢复（不再被变形）
                    if (!le.hasEffect(cn.autoforged.joes_addons_for_abmc.potion.ModMobEffects.TRANSMUTATION)) {
                        net.minecraft.world.entity.LivingEntity r = acquireEnemy(le, ownerUuid);
                        if (r != null) {
                            this.waitingEnemyUuid = null;
                            this.waitingEnemyPos = null;
                            return r;
                        }
                    }
                }
            }
            // UUID 查找失败，用位置扫描附近生物（绕过 UUID 变化问题）
            int scanRadius = 5;
            net.minecraft.world.entity.LivingEntity closest = null;
            double closestDist = Double.MAX_VALUE;
            for (net.minecraft.world.entity.Entity entity : sl.getAllEntities()) {
                if (!(entity instanceof net.minecraft.world.entity.LivingEntity le)) continue;
                if (entity instanceof net.minecraft.world.entity.player.Player) continue; // 跳过玩家
                if (le.getUUID().equals(ownerUuid)) continue;
                double dist = entity.blockPosition().distSqr(this.waitingEnemyPos);
                if (dist <= scanRadius * scanRadius && dist < closestDist) {
                    closest = le;
                    closestDist = dist;
                }
            }
            if (closest != null) {
                net.minecraft.world.entity.LivingEntity r = acquireEnemy(closest, ownerUuid);
                if (r != null) {
                    this.waitingEnemyUuid = null;
                    this.waitingEnemyPos = null;
                    return r;
                }
            }
        }
        return null;
    }

    /** 尝试锁定一个候选敌人：排除主人自身、已死亡/移除、创造/观察者玩家，
     *  以及刚因超距传送而被放弃的旧目标（等待主人出现新目标后解除忽略）。
     *  成功锁定时会把 UUID 写入 lastEnemyUuid。 */
    private net.minecraft.world.entity.LivingEntity acquireEnemy(
            net.minecraft.world.entity.LivingEntity candidate, java.util.UUID ownerUuid) {
        if (candidate == null || !candidate.isAlive()) return null;
        if (candidate.getUUID().equals(ownerUuid)) return null;
        if (candidate instanceof net.minecraft.world.entity.player.Player p
            && (p.isCreative() || p.isSpectator())) return null;
        if (this.lostEnemyUuid != null && this.lostEnemyUuid.equals(candidate.getUUID())) {
            return null; // 仍是刚放弃的旧目标，继续等待新目标
        }
        this.lostEnemyUuid = null; // 出现新目标，解除对旧目标的忽略
        this.lastEnemyUuid = candidate.getUUID();
        return candidate;
    }

    /** 配对的酿造台搭档（仅当本实体是箱子且已配对时返回）。 */
    private AwakeningFallingBlockEntity partnerBrewing() {
        if (!this.isChest() || !this.isPaired() || !(this.level() instanceof ServerLevel sl)) return null;
        Entity p = sl.getEntity(this.partnerId);
        return (p instanceof AwakeningFallingBlockEntity ep && ep.isBrewingStand()) ? ep : null;
    }

    private boolean isPotionItem(net.minecraft.world.item.Item item) {
        return item == net.minecraft.world.item.Items.POTION
            || item == net.minecraft.world.item.Items.SPLASH_POTION
            || item == net.minecraft.world.item.Items.LINGERING_POTION;
    }

    /** 用命名纸给箱子设置目标坐标「X Y Z」。 */
    @Override
    public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player,
                                                          net.minecraft.world.InteractionHand hand) {
        if (!this.isChest() || this.level().isClientSide) return net.minecraft.world.InteractionResult.PASS;
        // 只处理主手交互，避免副手重复触发导致状态被连续切换两次
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) return net.minecraft.world.InteractionResult.PASS;
        net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
        // 空手右击：循环切换援助模式 0→1→2→0
        if (held.isEmpty()) {
            this.assistMode = (this.assistMode + 1) % 3;
            this.lastEnemyUuid = null; // 切换模式时清除敌人记忆
            this.lostEnemyUuid = null; // 清除被放弃的旧目标记忆
            this.waitingEnemyUuid = null; // 清除等待敌人记忆
            this.waitingEnemyPos = null;
            String msg;
            switch (this.assistMode) {
                case 1: msg = "§c组合体已切换为敌对模式"; break;
                case 2: msg = "§7组合体已切换为无模式（仅跟随）"; break;
                default: msg = "§a组合体已切换为友善模式"; break;
            }
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(msg), true);
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (!held.is(net.minecraft.world.item.Items.PAPER)) return net.minecraft.world.InteractionResult.PASS;
        String name = held.getHoverName().getString().trim();
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("^(-?\\d+)[ ](-?\\d+)[ ](-?\\d+)$").matcher(name);
        if (!m.matches()) return net.minecraft.world.InteractionResult.PASS;
        try {
            this.homePos = new BlockPos(
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)));
            this.firstArrivalDone = false;
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "已把箱子目标坐标设为 " + this.homePos.getX() + " " + this.homePos.getY() + " " + this.homePos.getZ()), true);
            return net.minecraft.world.InteractionResult.SUCCESS;
        } catch (NumberFormatException e) {
            return net.minecraft.world.InteractionResult.PASS;
        }
    }
    private Vec3 getNextWaypoint(net.minecraft.world.phys.Vec3 target) {
        // 定时重建路径 / 路径走完 / 路径失效时重新寻路
        if (this.pathRefreshTimer <= 0 || this.hopPath.isEmpty() || this.hopPathIndex >= this.hopPath.size()) {
            this.hopPath.clear();
            this.hopPath.addAll(computePathTo(net.minecraft.core.BlockPos.containing(target)));
            this.hopPathIndex = 0;
            this.pathRefreshTimer = 30;
        }
        if (this.hopPathIndex >= this.hopPath.size()) {
            return target;
        }
        // 连续跳过已到达/已越过（水平距离 < 1.4 格）的路径点，瞄准前方更远的点
        int idx = this.hopPathIndex;
        BlockPos wp = this.hopPath.get(idx);
        Vec3 c = wp.getCenter();
        while (Math.hypot(c.x - this.getX(), c.z - this.getZ()) < 1.4
            && idx < this.hopPath.size() - 1) {
            idx++;
            wp = this.hopPath.get(idx);
            c = wp.getCenter();
        }
        this.hopPathIndex = idx;
        return c;
    }

    /**
     * 基于方格的无障碍 BFS 寻路：从当前格到目标格（或与其相邻格），找到一串可站立的路径点，
     * 供方块逐跳接近玩家并绕开障碍。
     */
    private List<BlockPos> computePathTo(BlockPos goal) {
        BlockPos start = this.blockPosition();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        HashMap<BlockPos, BlockPos> prev = new HashMap<>();
        HashSet<BlockPos> seen = new HashSet<>();
        queue.add(start);
        seen.add(start);
        int limit = 256;
        while (!queue.isEmpty() && limit-- > 0) {
            BlockPos cur = queue.poll();
            if (cur.equals(goal)
                || (Math.abs(cur.getX() - goal.getX())
                    + Math.abs(cur.getY() - goal.getY())
                    + Math.abs(cur.getZ() - goal.getZ())) <= 1) {
                ArrayList<BlockPos> path = new ArrayList<>();
                BlockPos node = cur;
                while (node != null) {
                    path.add(node);
                    node = prev.get(node);
                }
                Collections.reverse(path);
                return path;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos next = cur.offset(d.getNormal());
                if (!seen.contains(next) && canHopStand(next)) {
                    prev.put(next, cur);
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return Collections.emptyList();
    }

    /** 该格是否可站立：为空气/可替换，且下方是实心（可落脚）。 */
    private boolean canHopStand(BlockPos p) {
        BlockState cell = this.level().getBlockState(p);
        if (!(cell.isAir() || cell.canBeReplaced())) return false;
        return this.level().getBlockState(p.below()).isSolid();
    }

    /**
     * 若本方块被卡进实心方块（其所在格有碰撞），就近拽到相邻无碰撞的空气格；无相邻空气则向上抬一格。
     */
    private void resolveStuckInBlocks() {
        BlockPos pos = this.blockPosition();
        if (this.level().getBlockState(pos).getCollisionShape(this.level(), pos).isEmpty()) {
            return; // 不在实心方块内
        }
        for (Direction d : Direction.values()) {
            BlockPos cand = pos.offset(d.getNormal());
            if (this.level().getBlockState(cand).getCollisionShape(this.level(), cand).isEmpty()) {
                this.setPos(cand.getX() + 0.5, cand.getY(), cand.getZ() + 0.5);
                this.setDeltaMovement(Vec3.ZERO);
                return;
            }
        }
        // 全部被围死：向上抬一格
        this.setPos(this.getX(), this.getY() + 1.0, this.getZ());
        this.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * 宠物能力：当觉醒方块距离最近的玩家过远（且玩家站立在地面上）时，
     * 静默传送到玩家身边的一个可站立位置（不播放传送音效）。
     */
    private void maybeTeleportToPlayer() {
        // 敌对模式由 30 格主人牵引逻辑（maybeTeleportBackToOwnerHostile）负责，不再按最近玩家牵引
        if (this.assistMode == 1) return;
        // 全范围找最近的玩家（等同宠物主人）
        Player nearest = null;
        double bestSqr = Double.MAX_VALUE;
        for (Player p : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(128.0))) {
            if (p.isCreative() || p.isSpectator()) continue; // 无视创造/旁观者玩家
            double d = this.distanceToSqr(p);
            if (d < bestSqr) {
                bestSqr = d;
                nearest = p;
            }
        }
        if (nearest == null) return;
        if (!nearest.onGround()) return;                                   // 玩家须站在地面
        if (bestSqr <= PET_TELEPORT_DIST * PET_TELEPORT_DIST) return;     // 距离还不够远
        teleportNear(nearest, 4);
    }

    /**
     * 敌对模式：若箱子距主人超过 30 格，则传送回主人身边并放弃当前索敌目标（失去索敌），
     * 直到主人出现新的索敌目标才会重新锁定。
     */
    private void maybeTeleportBackToOwnerHostile() {
        if (this.assistMode != 1) return;
        net.minecraft.world.entity.LivingEntity owner = getOwner();
        if (owner == null || owner.isRemoved()) return;
        final double leash = 30.0;
        if (this.distanceToSqr(owner.getX(), owner.getY(), owner.getZ()) <= leash * leash) return;
        if (teleportNear(owner, 4)) {
            this.lostEnemyUuid = this.lastEnemyUuid; // 放弃刚追的旧目标
            this.lastEnemyUuid = null;
            this.waitingEnemyUuid = null;
            this.waitingEnemyPos = null;
        }
    }

    /** 在目标附近的水平环（最多半径 radius 格）里找一个可站立空位传送过去；成功返回 true。 */
    private boolean teleportNear(net.minecraft.world.entity.Entity target, int radius) {
        BlockPos center = target.blockPosition();
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    BlockPos cand = center.offset(dx, 0, dz);
                    if (canHopStand(cand)) {
                        this.setPos(cand.getX() + 0.5, cand.getY(), cand.getZ() + 0.5);
                        this.setDeltaMovement(Vec3.ZERO);
                        this.clearHop();
                        this.hopCooldown = 0;
                        this.hopPath.clear();
                        this.hopPathIndex = 0;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 设定觉醒倒计时（刻），供普通/延长版觉醒药水区分。 */
    public void setSolidifyTicks(int ticks) {
        this.solidifyTicks = ticks;
        this.solidified = false;
    }

    /**
     * 固化：寻找离自己最近的合法（空气/可替换）坐标，把方块放回世界（还原 BlockEntity 内容物），
     * 并移除该实体。可被倒计时到期或 /jafa solidify 命令触发。
     */
    public void solidify() {
        if (this.solidified) return;
        this.solidified = true;
        if (this.myBlockState.isAir()) return;
        BlockPos center = this.blockPosition();
        BlockPos target = null;
        double bestDist = Double.MAX_VALUE;
        for (int dy = -4; dy <= 8; dy++) {
            for (int dx = -16; dx <= 16; dx++) {
                for (int dz = -16; dz <= 16; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    BlockState existing = this.level().getBlockState(p);
                    if (!(existing.isAir() || existing.canBeReplaced())) continue;
                    double d = p.distSqr(center);
                    if (d < bestDist) {
                        bestDist = d;
                        target = p;
                    }
                }
            }
        }
        if (target != null && this.level().setBlock(target, this.myBlockState, 3)) {
            ((ServerLevel) this.level())
                .getChunkSource()
                .chunkMap
                .broadcast(this, new ClientboundBlockUpdatePacket(target, this.level().getBlockState(target)));
            cn.autoforged.joes_addons_for_abmc.ModMain.placeBlockEntityData(
                this.level(), target, this.myBlockState, this.myBlockData);
        }
        this.discard();
    }

    /**
     * 潜影贝式硬性分离：扫描重叠的同类觉醒方块并把双方沿水平方向推开，使中心距离尽量不小于 1 格，
     * 从而保证它们之间无法重叠。服务端每刻调用，不依赖 MC 的 pushEntities 时序。
     */
    private void separateFromOthers() {
        double reach = 1.05;
        java.util.List<AwakeningFallingBlockEntity> others =
            this.level().getEntitiesOfClass(AwakeningFallingBlockEntity.class,
                this.getBoundingBox().inflate(reach));
        for (AwakeningFallingBlockEntity other : others) {
            if (other == this) continue;
            // 跳过已骑乘/可载乘的方块（如配对的酿造台骑在箱子上），否则会把载具推走导致速度异常
            if (other.isPassenger() || other == this.getVehicle() || this.getVehicle() == other) continue;
            double dx = other.getX() - this.getX();
            double dz = other.getZ() - this.getZ();
            double dist2 = dx * dx + dz * dz;
            if (dist2 > 1.0E-6 && dist2 < 1.0) {
                double dist = Math.sqrt(dist2);
                // 把中心推到不小于 1 格（各让一半），硬碰撞不留重叠
                double resolve = (1.0 - dist) * 0.5;
                double nx = dx / dist * resolve;
                double nz = dz / dist * resolve;
                other.setPos(other.getX() + nx, other.getY(), other.getZ() + nz);
                this.setPos(this.getX() - nx, this.getY(), this.getZ() - nz);
            }
        }
    }

    /**
     * 判断跳跃方向前方是否被“一格高的障碍”挡住：即与方块脚部同高的前方格子为实心方块，
     * 而它上方一格为空气（可越过去）。用于在该情形下把跳跃高度提高到约 1.2 格。
     */
    private boolean isOneBlockObstacleAhead(double dx, double dz) {
        BlockPos front = BlockPos.containing(
            this.getX() + dx * 0.9, this.getY() + 0.5, this.getZ() + dz * 0.9);
        BlockState frontState = this.level().getBlockState(front);
        return frontState.isSolid() && this.level().getBlockState(front.above()).isAir();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.put("MyBlockState", NbtUtils.writeBlockState(this.myBlockState));
        compound.putBoolean("AwakeningLanded", this.awakeningLanded);
        compound.putInt("WobbleRemaining", this.wobbleRemaining);
        compound.putInt("SolidifyTicks", this.solidifyTicks);
        compound.putInt("AxeAttackCount", this.axeAttackCount);
        compound.putLong("LastAxeAttackTime", this.lastAxeAttackTime);
        compound.putBoolean("FirstArrivalDone", this.firstArrivalDone);
        compound.putInt("AssistMode", this.assistMode);
        compound.putInt("BrewSchedule", this.brewSchedule);
        if (this.lastEnemyUuid != null) {
            compound.putUUID("LastEnemyUuid", this.lastEnemyUuid);
        }
        if (this.lostEnemyUuid != null) {
            compound.putUUID("LostEnemyUuid", this.lostEnemyUuid);
        }
        if (this.waitingEnemyUuid != null) {
            compound.putUUID("WaitingEnemyUuid", this.waitingEnemyUuid);
        }
        if (this.waitingEnemyPos != null) {
            compound.putLong("WaitingEnemyPos", this.waitingEnemyPos.asLong());
        }
        if (this.homePos != null) {
            compound.putLong("HomePos", this.homePos.asLong());
        }
        if (this.myBlockData != null) {
            compound.put("MyTileEntityData", this.myBlockData);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.myBlockState = NbtUtils.readBlockState(
            this.level().holderLookup(net.minecraft.core.registries.Registries.BLOCK),
            compound.getCompound("MyBlockState"));
        this.awakeningLanded = compound.getBoolean("AwakeningLanded");
        this.setAwakeningLanded(this.awakeningLanded);
        this.wobbleRemaining = compound.getInt("WobbleRemaining");
        this.solidifyTicks = compound.getInt("SolidifyTicks");
        if (this.solidifyTicks <= 0) this.solidifyTicks = 3600;
        this.axeAttackCount = compound.getInt("AxeAttackCount");
        this.lastAxeAttackTime = compound.getLong("LastAxeAttackTime");
        this.firstArrivalDone = compound.getBoolean("FirstArrivalDone");
        this.assistMode = compound.getInt("AssistMode");
        this.brewSchedule = compound.getInt("BrewSchedule");
        if (compound.contains("LastEnemyUuid")) {
            this.lastEnemyUuid = compound.getUUID("LastEnemyUuid");
        }
        if (compound.contains("LostEnemyUuid")) {
            this.lostEnemyUuid = compound.getUUID("LostEnemyUuid");
        }
        if (compound.contains("WaitingEnemyUuid")) {
            this.waitingEnemyUuid = compound.getUUID("WaitingEnemyUuid");
        }
        if (compound.contains("WaitingEnemyPos", 4)) {
            this.waitingEnemyPos = BlockPos.of(compound.getLong("WaitingEnemyPos"));
        }
        if (compound.contains("HomePos", 4)) {
            this.homePos = BlockPos.of(compound.getLong("HomePos"));
        }
        if (compound.contains("MyTileEntityData", 10)) {
            this.myBlockData = compound.getCompound("MyTileEntityData").copy();
        }
        if (this.myBlockState.isAir()) {
            this.myBlockState = Blocks.STONE.defaultBlockState();
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity, Block.getId(this.getBlockState()));
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.myBlockState = Block.stateById(packet.getData());
        this.blocksBuilding = true;
        double d0 = packet.getX();
        double d1 = packet.getY();
        double d2 = packet.getZ();
        this.setPos(d0, d1, d2);
        this.setStartPos(this.blockPosition());
    }
}