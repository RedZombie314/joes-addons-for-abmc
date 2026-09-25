package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 可持有的结构实体 —— 将一整个结构（若干方块 + 相对偏移）渲染为实体形态。
 * 继承原版 {@link Display.BlockDisplay}，从而天然获得 Display 的变换体系
 * （平移/缩放/xyz 三轴视觉旋转，均通过 transformation 数据组件承载，客户端由
 * {@link net.minecraft.client.renderer.entity.DisplayRenderer} 统一应用）。
 * <p>结构方块按「方块状态 + 相对偏移」存进同步的 StructureTag；<b>目前仅加入实体，
 * 召唤方式暂未实装</b>。</p>
 */
public class HoldableStructureEntity extends Display.BlockDisplay {

    /** 同步的数据 key：整体结构（"Blocks" 列表，每项含 x/y/z 相对偏移与方块状态）。 */
    private static final EntityDataAccessor<CompoundTag> DATA_STRUCTURE =
        SynchedEntityData.defineId(HoldableStructureEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** 同步的数据 key：相对玩家摄像头的渲染参数（x/y/z 位置偏移，rx/ry/rz 旋转角度，单位度）。 */
    private static final EntityDataAccessor<CompoundTag> DATA_AVATAR =
        SynchedEntityData.defineId(HoldableStructureEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** 同步的数据 key：三者——x/y/z 三个方向是否镜像（布尔）。 */
    private static final EntityDataAccessor<CompoundTag> DATA_MIRROR =
        SynchedEntityData.defineId(HoldableStructureEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** 同步的数据 key：身体锁定参数（含锁定玩家 UUID、头部水平系偏移 bx/by/bz、朝向差 yo）。
     *  空 tag = 未锁定。客户端据此在锁定到本地玩家时直接用本地精确位姿覆写渲染，消除网络量化阶梯。 */
    private static final EntityDataAccessor<CompoundTag> DATA_LOCK =
        SynchedEntityData.defineId(HoldableStructureEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** 同步的数据 key：结构分类（如 "playable"）。实体身份仅存于 NBT 时客户端不可见，
     *  需经 entityData 同步，客户端才能据此判断分类（如弹奏工具输入冻结只对 playable 生效）。 */
    private static final EntityDataAccessor<String> DATA_CATEGORY =
        SynchedEntityData.defineId(HoldableStructureEntity.class, EntityDataSerializers.STRING);
    /** 同步的数据 key：结构模板 id（如 "blue_guitar"）。普通字段 structureId 仅存 NBT 不随实体同步，
     *  客户端恒空，故需经 entityData 同步，客户端才能据此选定演奏音色。 */
    private static final EntityDataAccessor<String> DATA_STRUCTURE_ID =
        SynchedEntityData.defineId(HoldableStructureEntity.class, EntityDataSerializers.STRING);
    /** 同步的数据 key：是否被闪电充能（charged 变体）。仅 green_guitar / chicken_guitar_2 可被置真，
     *  置真后该结构弹奏工具演奏电吉他音色。外观暂不变，未来实装。 */
    private static final EntityDataAccessor<Boolean> DATA_CHARGED =
        SynchedEntityData.defineId(HoldableStructureEntity.class, EntityDataSerializers.BOOLEAN);
    /** 客户端渲染用的渲染状态（结构方块快照）。 */
    @Nullable
    private StructureRenderState structureRenderState;

    // ────────────────────────── 身体锁定状态 ──────────────────────────
    // 锁定逻辑纯服务端执行，客户端渲染完全由实体位置 + yRot + avatar 变换驱动（这些本就会同步），
    // 因此锁定状态无需 entityData 同步，普通字段 + NBT 持久化即可。
    // 跟随规则：位置基准为玩家身体（脚部位置）；旋转跟随头部绕 y 轴的水平转角（getYRot），
    // 不跟随俯仰——玩家转头时结构绕身体水平转动，身体不动仅仰头/低头时结构不动。

    /** 锁定到的玩家 UUID；null = 未锁定。 */
    @Nullable
    private java.util.UUID bodyLockPlayer;
    /** 锁定瞬间结构相对玩家身体的偏移，保存在「头部水平系」（x=右、y=上、z=前，由锁定瞬间头部 yaw 定义）中；
     *  头部 yaw 变化时按当前头部 yaw 旋转该向量即得世界偏移。 */
    private double bodyLockBX, bodyLockBY, bodyLockBZ;
    /** 锁定瞬间实体朝向与头部 yaw 的差（entityYRot − headYaw），锁定期间保持该差值不变。 */
    private float bodyLockYawOffset;

    // ── 已记住的挂载参数（木锄调试结束时固化） ──
    // 一旦记住，之后锁定不再按右键瞬间的玩家相对关系重新捕获，而是直接套用这套参数——
    // 结构永远出现在调试好的相对位置与角度。

    /** 是否已有记住的挂载参数。 */
    private boolean hasRememberedMount;
    /** 记住的挂载参数：头部水平系偏移 + 朝向差（含义同 bodyLockB* / bodyLockYawOffset）。 */
    private double remBX, remBY, remBZ;
    private float remYawOffset;

    /** 结构整体在世界中的碰撞箱（应用 avatar 变换后，以实体位置为锚点）。 */
    @Nullable
    private net.minecraft.world.phys.AABB structureBounds;
    /** 结构相对实体位置的碰撞箱（avatar 变换后，位置无关；跟随时平移即可复用）。 */
    @Nullable
    private net.minecraft.world.phys.AABB structureBoundsRel;

    /** 结构身份：模板 ID（如 "chicken_guitar_1"）与分类（如 "playable"）。采集时写入，NBT 持久化。 */
    private String structureId = "";
    private String structureCategory = "";
    /** 是否被闪电充能（charged 变体）；普通字段用于 NBT 持久化，同步见 DATA_CHARGED。 */
    private boolean charged;

    /** 设置结构身份（采集生成时调用）。分类写入同步数据，客户端据此判断分类。 */
    public void setStructureIdentity(String id, String category) {
        this.structureId = id != null ? id : "";
        this.structureCategory = category != null ? category : "";
        this.entityData.set(DATA_CATEGORY, this.structureCategory);
        this.entityData.set(DATA_STRUCTURE_ID, this.structureId);
    }

    /** 结构模板 ID；未登记身份时为空串。读同步数据，双端可用。 */
    public String getStructureId() {
        return this.entityData.get(DATA_STRUCTURE_ID);
    }

    /** 是否被闪电充能；读同步数据，双端可用。 */
    public boolean getCharged() {
        return this.entityData.get(DATA_CHARGED);
    }

    /** 标记/解除充能状态；同步数据下发，客户端据此切换演奏音色。 */
    public void setCharged(boolean v) {
        this.charged = v;
        this.entityData.set(DATA_CHARGED, v);
    }

    /** 结构分类（如 "playable"）；未登记身份时为空串。读同步数据，双端可用。 */
    public String getStructureCategory() {
        return this.entityData.get(DATA_CATEGORY);
    }

    public HoldableStructureEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STRUCTURE, new CompoundTag());
        builder.define(DATA_AVATAR, new CompoundTag());
        builder.define(DATA_MIRROR, new CompoundTag());
        builder.define(DATA_LOCK, new CompoundTag());
        builder.define(DATA_CATEGORY, "");
        builder.define(DATA_STRUCTURE_ID, "");
        builder.define(DATA_CHARGED, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        // 自定义结构数据不在 vanilla 的 RENDER_STATE_IDS 中，不会自动刷新渲染状态；
        // 手动标记 updateRenderState=true，让本刻客户端 tick 重建 structureRenderState，
        // 否则渲染器 getSubState() 恒为 null，实体什么都不渲染。
        if (key.equals(DATA_STRUCTURE) || key.equals(DATA_AVATAR) || key.equals(DATA_MIRROR)) {
            this.updateRenderState = true;
            // 客户端收到结构/avatar/镜像同步时同刻重建碰撞箱；否则 structureBounds 为 null 或过期，
            // 碰撞箱回退到 super.makeBoundingBox()（以 position 为体心、按默认尺寸）。
            this.rebuildStructureBounds();
        }
    }

    @Override
    public void tick() {
        super.tick();
        // 锁定跟随：位置随玩家身体（脚部）平移；旋转跟随头部绕 y 轴的水平转角（getYRot）。
        if (!this.level().isClientSide) {
            // 服务端权威跟随
            if (this.bodyLockPlayer != null) {
                Player player = this.level().getPlayerByUUID(this.bodyLockPlayer);
                if (player != null && player.isAlive()) {
                    followLockedPlayer(player);
                }
                // 玩家不在线/不在本维度/死亡：本刻不更新，结构停在原地
            }
        } else {
            // 客户端：锁定到本地玩家时，直接用本地玩家的精确位置/朝向覆写本地位姿。
            // 网络同步的 yaw 经 byte 量化（≈1.4°/级）且 lerp 分刻追赶，快速转头时有阶梯感；
            // 本地玩家的位姿每帧连续，配合 setOldPosAndRot 让渲染的帧内插值完全平滑。
            if (isLockedToLocalPlayer()) {
                CompoundTag lock = this.entityData.get(DATA_LOCK);
                Player player = this.level().getPlayerByUUID(lock.getUUID("p"));
                if (player != null && player.isAlive()) {
                    double bx = lock.getDouble("bx"), by = lock.getDouble("by"), bz = lock.getDouble("bz");
                    float yo = lock.getFloat("yo");
                    double yawRad = Math.toRadians(player.getYRot());
                    double cos = Math.cos(yawRad), sin = Math.sin(yawRad);
                    double wx = -cos * bx - sin * bz;
                    double wz = -sin * bx + cos * bz;
                    this.setOldPosAndRot();
                    this.setPos(player.getX() + wx, player.getY() + by, player.getZ() + wz);
                    this.setYRot(player.getYRot() + yo);
                }
            }
        }
    }

    /** 锁定目标是否为本地玩家（仅客户端有意义）。 */
    private boolean isLockedToLocalPlayer() {
        CompoundTag lock = this.entityData.get(DATA_LOCK);
        if (!lock.hasUUID("p")) return false;
        Player player = this.level().getPlayerByUUID(lock.getUUID("p"));
        return player != null && player.isLocalPlayer();
    }

    /** 锁定到本地玩家期间丢弃网络同步的位姿：否则 lerp 插值每刻先把实体位姿往服务端的
     *  量化值拉一步，污染随后 setOldPosAndRot 记录的插值旧值，渲染帧内插值基于脏旧值而抽搐。 */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (this.level().isClientSide && isLockedToLocalPlayer()) return;
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    /** 按当前头部 yaw 把结构就位到玩家身上（位置 = 脚部 + 旋转后的偏移，朝向 = 头部 yaw + 朝向差）。 */
    private void followLockedPlayer(Player player) {
        double yawRad = Math.toRadians(player.getYRot());
        double cos = Math.cos(yawRad), sin = Math.sin(yawRad);
        // 头部水平系偏移 → 世界偏移（按当前头部 yaw 旋转；MC yaw 增长方向 = 从上看顺时针）
        double wx = -cos * this.bodyLockBX - sin * this.bodyLockBZ;
        double wz = -sin * this.bodyLockBX + cos * this.bodyLockBZ;
        this.setPos(player.getX() + wx, player.getY() + this.bodyLockBY, player.getZ() + wz);
        this.setYRot(player.getYRot() + this.bodyLockYawOffset);
    }

    // ────────────────────────── 身体锁定（空手右键拿起/放下） ──────────────────────────

    /** 把结构锁定到玩家：位置基准为身体脚部；旋转以头部 yaw（绕 y 轴）为参照。
     *  已有记住的挂载参数（木锄调试固化过）时直接套用，不按右键瞬间的相对关系重新捕获。 */
    public void lockToBody(Player player) {
        this.bodyLockPlayer = player.getUUID();
        if (this.hasRememberedMount) {
            // 直接套用调试固化的挂载参数，并立即就位（避免等待下一 tick 的短暂错位）
            this.bodyLockBX = this.remBX;
            this.bodyLockBY = this.remBY;
            this.bodyLockBZ = this.remBZ;
            this.bodyLockYawOffset = this.remYawOffset;
            syncLockTag();
            followLockedPlayer(player);
            return;
        }
        // 首次锁定：按当前相对位置捕获快照
        double wx = this.getX() - player.getX();
        double wy = this.getY() - player.getY();
        double wz = this.getZ() - player.getZ();
        double yawRad = Math.toRadians(player.getYRot());
        double cos = Math.cos(yawRad), sin = Math.sin(yawRad);
        // 世界偏移 → 头部水平系偏移（按 −yaw 旋转）
        this.bodyLockBX = -cos * wx - sin * wz;
        this.bodyLockBY = wy;
        this.bodyLockBZ = -sin * wx + cos * wz;
        this.bodyLockYawOffset = this.getYRot() - player.getYRot();
        syncLockTag();
    }

    /** 解除锁定：结构就地停在当前位置与朝向，不做任何复位。 */
    public void unlockFromBody() {
        this.bodyLockPlayer = null;
        syncLockTag();
    }

    /** 当前是否处于身体锁定状态（服务端权威值）。 */
    public boolean isBodyLocked() {
        return this.bodyLockPlayer != null;
    }

    /** 当前是否处于身体锁定状态（双端可读，客户端用于木锄调试与本地平滑跟随）。 */
    public boolean isBodyLockedSynced() {
        return this.entityData.get(DATA_LOCK).hasUUID("p");
    }

    /** 锁定到的玩家 UUID（双端可读；未锁定返回 null）。由同步的 DATA_LOCK 读得。 */
    @Nullable
    public java.util.UUID getBodyLockPlayerUuid() {
        net.minecraft.nbt.CompoundTag t = this.entityData.get(DATA_LOCK);
        return t.hasUUID("p") ? t.getUUID("p") : null;
    }

    /** 把当前锁定参数写入同步 tag（锁定时调用一次即可，参数在锁定期间不变）。 */
    private void syncLockTag() {
        if (this.bodyLockPlayer == null) {
            this.entityData.set(DATA_LOCK, new CompoundTag());
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putUUID("p", this.bodyLockPlayer);
        t.putDouble("bx", this.bodyLockBX);
        t.putDouble("by", this.bodyLockBY);
        t.putDouble("bz", this.bodyLockBZ);
        t.putFloat("yo", this.bodyLockYawOffset);
        this.entityData.set(DATA_LOCK, t);
    }

    /** 木锄调试会话结束时固化挂载参数：把当前锁定快照（调试第一帧捕获的相对偏移与朝向差，
     *  调试期间不变）记为固定参数；avatar 偏移/旋转/镜像本就在实体上持久保留。
     *  之后锁定直接套用该参数，不再按右键瞬间的玩家相对关系重新捕获。 */
    public void rememberCurrentMount() {
        if (this.bodyLockPlayer == null) return;
        this.hasRememberedMount = true;
        this.remBX = this.bodyLockBX;
        this.remBY = this.bodyLockBY;
        this.remBZ = this.bodyLockBZ;
        this.remYawOffset = this.bodyLockYawOffset;
    }

    /** 必须可被射线选中：Entity.isPickable() 默认 false（Display 未覆写），
     *  否则客户端射线不命中本实体，右键交互包根本不会发到服务端。 */
    @Override
    public boolean isPickable() {
        return true;
    }

    // ────────────────────────── 结构装载 ──────────────────────────

    /** 设置结构方块（含相对偏移，以实体位置为结构原点）。 */
    public void setStructureBlocks(List<StructureBlock> blocks) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (StructureBlock b : blocks) {
            if (b == null || b.state() == null) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", b.pos().getX());
            entry.putInt("y", b.pos().getY());
            entry.putInt("z", b.pos().getZ());
            entry.put("state", NbtUtils.writeBlockState(b.state()));
            list.add(entry);
        }
        tag.put("Blocks", list);
        this.entityData.set(DATA_STRUCTURE, tag);
        this.rebuildStructureBounds();
    }

    /** 读取结构方块（客户端用于渲染）。 */
    public List<StructureBlock> getStructureBlocks() {
        CompoundTag tag = this.entityData.get(DATA_STRUCTURE);
        java.util.List<StructureBlock> out = new ArrayList<>();
        net.minecraft.core.HolderGetter<Block> blockReg = this.level().registryAccess().lookupOrThrow(Registries.BLOCK);
        if (tag == null || !tag.contains("Blocks", Tag.TAG_LIST)) return out;
        ListTag list = tag.getList("Blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos pos = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
            BlockState state = NbtUtils.readBlockState(blockReg, entry.getCompound("state"));
            out.add(new StructureBlock(pos, state));
        }
        return out;
    }

    // ────────────────────────── 相对玩家摄像头的渲染参数 ──────────────────────────

    /**
     * 设置相对玩家摄像头的渲染位置与旋转角度。坐标轴均相对观察者（玩家摄像头）：
     * x=右、y=上、z=前 方向的距离（格），rx/ry/rz 为绕对应轴的旋转角度（度）。
     * 存进同步的 AvatarTag，供召唤方（未来实装）换算世界坐标/朝向使用。
     */
    public void setAvatar(float x, float y, float z, float rx, float ry, float rz) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("x", x);
        tag.putFloat("y", y);
        tag.putFloat("z", z);
        tag.putFloat("rx", rx);
        tag.putFloat("ry", ry);
        tag.putFloat("rz", rz);
        this.entityData.set(DATA_AVATAR, tag);
        this.rebuildStructureBounds();
    }

    public float getAvatarX() { return avatarOr(0).getFloat("x"); }
    public float getAvatarY() { return avatarOr(0).getFloat("y"); }
    public float getAvatarZ() { return avatarOr(0).getFloat("z"); }
    public float getAvatarRX() { return avatarOr(0).getFloat("rx"); }
    public float getAvatarRY() { return avatarOr(0).getFloat("ry"); }
    public float getAvatarRZ() { return avatarOr(0).getFloat("rz"); }

    private CompoundTag avatarOr(float defaultValue) {
        CompoundTag tag = tagsOrEmpty(this.entityData.get(DATA_AVATAR));
        if (!tag.contains("x")) tag.putFloat("x", defaultValue);
        if (!tag.contains("y")) tag.putFloat("y", defaultValue);
        if (!tag.contains("z")) tag.putFloat("z", defaultValue);
        if (!tag.contains("rx")) tag.putFloat("rx", defaultValue);
        if (!tag.contains("ry")) tag.putFloat("ry", defaultValue);
        if (!tag.contains("rz")) tag.putFloat("rz", defaultValue);
        return tag;
    }

    /** 把可能为空的 CompoundTag 规整为可查的（查不到键返回空 tag，不会 NPE）。 */
    private static CompoundTag tagsOrEmpty(CompoundTag tag) {
        return tag != null ? tag : new CompoundTag();
    }

    // ────────────────────────── 三轴镜像 ──────────────────────────

    /** 设置 x/y/z 三个方向是否镜像渲染（布尔）。 */
    public void setMirror(boolean mirrorX, boolean mirrorY, boolean mirrorZ) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("x", mirrorX);
        tag.putBoolean("y", mirrorY);
        tag.putBoolean("z", mirrorZ);
        this.entityData.set(DATA_MIRROR, tag);
        this.rebuildStructureBounds();
    }

    public boolean getMirrorX() { return mirrorOr().getBoolean("x"); }
    public boolean getMirrorY() { return mirrorOr().getBoolean("y"); }
    public boolean getMirrorZ() { return mirrorOr().getBoolean("z"); }

    private CompoundTag mirrorOr() {
        return tagsOrEmpty(this.entityData.get(DATA_MIRROR));
    }

    // ────────────────────────── 调试：按索引读写 9 个值 ──────────────────────────
    // 索引顺序：0=offsetX 1=offsetY 2=offsetZ 3=rotX 4=rotY 5=rotZ（avatar 6 项）＋
    //          6=mirrorX 7=mirrorY 8=mirrorZ（镜像 3 项布尔）。供木锄调试用。
    public static final int DEBUG_INDEX_OFFSET_X = 0;
    public static final int DEBUG_INDEX_OFFSET_Y = 1;
    public static final int DEBUG_INDEX_OFFSET_Z = 2;
    public static final int DEBUG_INDEX_ROT_X = 3;
    public static final int DEBUG_INDEX_ROT_Y = 4;
    public static final int DEBUG_INDEX_ROT_Z = 5;
    public static final int DEBUG_INDEX_MIRROR_X = 6;
    public static final int DEBUG_INDEX_MIRROR_Y = 7;
    public static final int DEBUG_INDEX_MIRROR_Z = 8;
    public static final int DEBUG_VALUE_COUNT = 9;

    /** 索引对应的人类可读名（用于 actionbar / 保存输出）。 */
    private static final String[] DEBUG_VALUE_NAMES = {
        "偏移X", "偏移Y", "偏移Z", "旋转X", "旋转Y", "旋转Z", "镜像X", "镜像Y", "镜像Z"
    };

    public static String debugValueName(int index) {
        return index < 0 || index >= DEBUG_VALUE_COUNT ? "?" : DEBUG_VALUE_NAMES[index];
    }

    /** 读某索引的当前值（镜像为 0/1）。 */
    public double getDebugValue(int index) {
        switch (index) {
            case DEBUG_INDEX_OFFSET_X: return getAvatarX();
            case DEBUG_INDEX_OFFSET_Y: return getAvatarY();
            case DEBUG_INDEX_OFFSET_Z: return getAvatarZ();
            case DEBUG_INDEX_ROT_X:   return getAvatarRX();
            case DEBUG_INDEX_ROT_Y:   return getAvatarRY();
            case DEBUG_INDEX_ROT_Z:   return getAvatarRZ();
            case DEBUG_INDEX_MIRROR_X: return getMirrorX() ? 1 : 0;
            case DEBUG_INDEX_MIRROR_Y: return getMirrorY() ? 1 : 0;
            case DEBUG_INDEX_MIRROR_Z: return getMirrorZ() ? 1 : 0;
            default: return 0;
        }
    }

    /** 写某索引的值（镜像写入布尔）。 */
    public void setDebugValue(int index, double value) {
        switch (index) {
            case DEBUG_INDEX_OFFSET_X: setAvatar((float) value, getAvatarY(), getAvatarZ(), getAvatarRX(), getAvatarRY(), getAvatarRZ()); break;
            case DEBUG_INDEX_OFFSET_Y: setAvatar(getAvatarX(), (float) value, getAvatarZ(), getAvatarRX(), getAvatarRY(), getAvatarRZ()); break;
            case DEBUG_INDEX_OFFSET_Z: setAvatar(getAvatarX(), getAvatarY(), (float) value, getAvatarRX(), getAvatarRY(), getAvatarRZ()); break;
            case DEBUG_INDEX_ROT_X:   setAvatar(getAvatarX(), getAvatarY(), getAvatarZ(), (float) value, getAvatarRY(), getAvatarRZ()); break;
            case DEBUG_INDEX_ROT_Y:   setAvatar(getAvatarX(), getAvatarY(), getAvatarZ(), getAvatarRX(), (float) value, getAvatarRZ()); break;
            case DEBUG_INDEX_ROT_Z:   setAvatar(getAvatarX(), getAvatarY(), getAvatarZ(), getAvatarRX(), getAvatarRY(), (float) value); break;
            case DEBUG_INDEX_MIRROR_X: setMirror(value != 0, getMirrorY(), getMirrorZ()); break;
            case DEBUG_INDEX_MIRROR_Y: setMirror(getMirrorX(), value != 0, getMirrorZ()); break;
            case DEBUG_INDEX_MIRROR_Z: setMirror(getMirrorX(), getMirrorY(), value != 0); break;
            default: break;
        }
    }

    /** 切换某布尔镜像索引。 */
    public void toggleDebugMirror(int index) {
        switch (index) {
            case DEBUG_INDEX_MIRROR_X: setMirror(!getMirrorX(), getMirrorY(), getMirrorZ()); break;
            case DEBUG_INDEX_MIRROR_Y: setMirror(getMirrorX(), !getMirrorY(), getMirrorZ()); break;
            case DEBUG_INDEX_MIRROR_Z: setMirror(getMirrorX(), getMirrorY(), !getMirrorZ()); break;
            default: break;
        }
    }

    // ────────────────────────── 碰撞箱 ──────────────────────────

    /**
     * 把结构相对盒（含 avatar 偏移+内容，实体本地坐标）的 8 个角点按实体朝向旋转后求世界轴包围盒。
     * 吸附态实体朝向 = 归属玩家朝向，与渲染端 FIXED billboard 的旋转完全一致，从而碰撞箱与方块视觉贴合；
     * 非吸附态实体朝向为 0，旋转为单位阵，退化为原有行为。
     */
    private net.minecraft.world.phys.AABB rotatedRelBounds() {
        net.minecraft.world.phys.AABB rel = this.structureBoundsRel;
        if (rel == null) return null;
        float yaw = (float) Math.toRadians(this.getYRot());
        float pitch = (float) Math.toRadians(this.getXRot());
        if (yaw == 0.0F && pitch == 0.0F) return rel;
        org.joml.Quaternionf q = new org.joml.Quaternionf().rotationYXZ(-yaw, pitch, 0.0F);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    org.joml.Vector3f c = new org.joml.Vector3f(
                        (float) (dx == 0 ? rel.minX : rel.maxX),
                        (float) (dy == 0 ? rel.minY : rel.maxY),
                        (float) (dz == 0 ? rel.minZ : rel.maxZ));
                    q.transform(c);
                    minX = Math.min(minX, c.x); minY = Math.min(minY, c.y); minZ = Math.min(minZ, c.z);
                    maxX = Math.max(maxX, c.x); maxY = Math.max(maxY, c.y); maxZ = Math.max(maxZ, c.z);
                }
            }
        }
        return new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** 结构全体在世界中的碰撞箱；始终用相对盒平移到当前实体位置，保证跟随移动时碰撞箱实时跟随、射线可命中。 */
    @Override
    protected net.minecraft.world.phys.AABB makeBoundingBox() {
        net.minecraft.world.phys.AABB rel = rotatedRelBounds();
        return rel != null
            ? rel.move(this.getX(), this.getY(), this.getZ())
            : super.makeBoundingBox();
    }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        net.minecraft.world.phys.AABB rel = rotatedRelBounds();
        return rel != null
            ? rel.move(this.getX(), this.getY(), this.getZ()).inflate(2.0)
            : super.getBoundingBoxForCulling();
    }

    /**
     * 把一个结构局部坐标（相对 anchor，方块左下角为整数即可）应用 avatar 变换：
     * 先对需要镜像的轴绕结构中心翻坐标（保留方块朝向，避免面法线翻转 → 不“内翻”）；
     * 再绕结构中心按 rx/ry/rz 旋转；最后加上 offset(x/y/z)。返回变换后的相对坐标。
     * 渲染与碰撞箱共用此函数，保证视觉与碰撞一致。
     */
    public double[] avatarTransform(double x, double y, double z) {
        List<StructureBlock> blocks = getStructureBlocks();
        if (blocks.isEmpty()) return new double[]{ x, y, z };
        // 结构包围盒中心（方块覆盖 [rel, rel+1] 的完整格）
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (StructureBlock b : blocks) {
            double ox = b.pos().getX(), oy = b.pos().getY(), oz = b.pos().getZ();
            minX = Math.min(minX, ox); minY = Math.min(minY, oy); minZ = Math.min(minZ, oz);
            maxX = Math.max(maxX, ox + 1); maxY = Math.max(maxY, oy + 1); maxZ = Math.max(maxZ, oz + 1);
        }
        double cx = (minX + maxX) / 2.0;
        double cy = (minY + maxY) / 2.0;
        double cz = (minZ + maxZ) / 2.0;

        // 1) 镜像（只翻坐标，保留朝向）
        if (getMirrorX()) x = 2.0 * cx - x;
        if (getMirrorY()) y = 2.0 * cy - y;
        if (getMirrorZ()) z = 2.0 * cz - z;

        // 2) 绕中心旋转
        double rxr = Math.toRadians(getAvatarRX());
        double ryr = Math.toRadians(getAvatarRY());
        double rzr = Math.toRadians(getAvatarRZ());
        if (rxr != 0 || ryr != 0 || rzr != 0) {
            org.joml.Vector3d v = new org.joml.Vector3d(x - cx, y - cy, z - cz);
            v.rotate(new org.joml.Quaterniond().rotationXYZ(rxr, ryr, rzr));
            x = v.x + cx;
            y = v.y + cy;
            z = v.z + cz;
        }

        // 3) 偏移
        x += getAvatarX();
        y += getAvatarY();
        z += getAvatarZ();
        return new double[]{ x, y, z };
    }

    /** 根据当前结构方块重建碰撞箱（应用 avatar 变换后取全体角点包围盒）。
     *  存相对实体位置的盒子（位置无关），绝对盒子按需由 makeBoundingBox 平移得到。 */
    private void rebuildStructureBounds() {
        List<StructureBlock> blocks = getStructureBlocks();
        if (blocks.isEmpty()) { structureBounds = null; structureBoundsRel = null; return; }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (StructureBlock b : blocks) {
            double ox = b.pos().getX(), oy = b.pos().getY(), oz = b.pos().getZ();
            // 方块 8 个角点（[ox,ox+1] 等）逐个变换后取包围盒
            for (int dx = 0; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        double[] p = avatarTransform(ox + dx, oy + dy, oz + dz);
                        minX = Math.min(minX, p[0]); minY = Math.min(minY, p[1]); minZ = Math.min(minZ, p[2]);
                        maxX = Math.max(maxX, p[0]); maxY = Math.max(maxY, p[1]); maxZ = Math.max(maxZ, p[2]);
                    }
                }
            }
        }
        structureBoundsRel = new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX, maxY, maxZ);
        structureBounds = structureBoundsRel.move(this.getX(), this.getY(), this.getZ());
        this.setBoundingBox(structureBounds);
    }

    // ────────────────────────── xyz 三轴视觉旋转 ──────────────────────────

    /**
     * 设置围绕结构原点（实体位置）的三轴视觉旋转（度）。本质是写入 Display 的缩放/旋转
     * 变换；客户端渲染时会随 transformation 一起应用。setTransformation 为私有方法，用反射调用。
     */
    public void setRotationXYZ(float rotX, float rotY, float rotZ) {
        float x = (float) Math.toRadians(rotX);
        float y = (float) Math.toRadians(rotY);
        float z = (float) Math.toRadians(rotZ);
        Quaternionf q = new Quaternionf().rotationXYZ(x, y, z);
        com.mojang.math.Transformation t = new com.mojang.math.Transformation(
            new Vector3f(0, 0, 0), q, new Vector3f(1, 1, 1), new Quaternionf());
        try {
            java.lang.reflect.Method m = Display.class.getDeclaredMethod("setTransformation", com.mojang.math.Transformation.class);
            m.setAccessible(true);
            m.invoke(this, t);
        } catch (Exception ignored) {
        }
    }

    // ────────────────────────── NBT 持久化 ──────────────────────────

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("StructureTag", Tag.TAG_COMPOUND)) {
            this.entityData.set(DATA_STRUCTURE, compound.getCompound("StructureTag").copy());
        }
        if (compound.contains("AvatarTag", Tag.TAG_COMPOUND)) {
            this.entityData.set(DATA_AVATAR, compound.getCompound("AvatarTag").copy());
        }
        if (compound.contains("MirrorTag", Tag.TAG_COMPOUND)) {
            this.entityData.set(DATA_MIRROR, compound.getCompound("MirrorTag").copy());
        }
        if (compound.hasUUID("BodyLockPlayer")) {
            this.bodyLockPlayer = compound.getUUID("BodyLockPlayer");
            this.bodyLockBX = compound.getDouble("BodyLockBX");
            this.bodyLockBY = compound.getDouble("BodyLockBY");
            this.bodyLockBZ = compound.getDouble("BodyLockBZ");
            this.bodyLockYawOffset = compound.getFloat("BodyLockYawOffset");
            syncLockTag();
        }
        if (compound.getBoolean("RemMount")) {
            this.hasRememberedMount = true;
            this.remBX = compound.getDouble("RemBX");
            this.remBY = compound.getDouble("RemBY");
            this.remBZ = compound.getDouble("RemBZ");
            this.remYawOffset = compound.getFloat("RemYawOffset");
        }
        if (compound.contains("StructureId", Tag.TAG_STRING)) {
            this.structureId = compound.getString("StructureId");
            this.structureCategory = compound.getString("StructureCategory");
            this.entityData.set(DATA_CATEGORY, this.structureCategory);
            this.entityData.set(DATA_STRUCTURE_ID, this.structureId);
        }
        if (compound.getBoolean("Charged")) {
            this.charged = true;
            this.entityData.set(DATA_CHARGED, true);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        CompoundTag tag = this.entityData.get(DATA_STRUCTURE);
        if (tag != null) {
            compound.put("StructureTag", tag.copy());
        }
        CompoundTag avatar = this.entityData.get(DATA_AVATAR);
        if (avatar != null) {
            compound.put("AvatarTag", avatar.copy());
        }
        CompoundTag mirror = this.entityData.get(DATA_MIRROR);
        if (mirror != null) {
            compound.put("MirrorTag", mirror.copy());
        }
        if (this.bodyLockPlayer != null) {
            compound.putUUID("BodyLockPlayer", this.bodyLockPlayer);
            compound.putDouble("BodyLockBX", this.bodyLockBX);
            compound.putDouble("BodyLockBY", this.bodyLockBY);
            compound.putDouble("BodyLockBZ", this.bodyLockBZ);
            compound.putFloat("BodyLockYawOffset", this.bodyLockYawOffset);
        }
        if (this.hasRememberedMount) {
            compound.putBoolean("RemMount", true);
            compound.putDouble("RemBX", this.remBX);
            compound.putDouble("RemBY", this.remBY);
            compound.putDouble("RemBZ", this.remBZ);
            compound.putFloat("RemYawOffset", this.remYawOffset);
        }
        if (!this.structureId.isEmpty()) {
            compound.putString("StructureId", this.structureId);
            compound.putString("StructureCategory", this.structureCategory);
        }
        if (this.charged) {
            compound.putBoolean("Charged", true);
        }
    }

    // ────────────────────────── 渲染状态 ──────────────────────────

    @Override
    protected void updateRenderSubState(boolean interpolate, float partialTick) {
        this.structureRenderState = new StructureRenderState(getStructureBlocks());
    }

    @Nullable
    public StructureRenderState structureRenderState() {
        return this.structureRenderState;
    }

    /** 结构内单个方块：相对实体位置的原点偏移 + 方块状态。 */
    public record StructureBlock(BlockPos pos, BlockState state) {
    }

    /** 客户端渲染状态：结构方块快照。 */
    public record StructureRenderState(List<StructureBlock> blocks) {
    }
}