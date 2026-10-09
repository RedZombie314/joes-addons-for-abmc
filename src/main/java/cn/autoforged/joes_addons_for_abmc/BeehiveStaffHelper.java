package cn.autoforged.joes_addons_for_abmc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import cn.autoforged.joes_addons_for_abmc.item.ModDataComponents;
import cn.autoforged.joes_addons_for_abmc.item.StaffItem;

/**
 * 蜂巢权杖（StaffItem 的 {@code bee_nest} 方块形态）能力：
 * <ul>
 *   <li>长按右键吸收 50 格内蜜蜂存入权杖的 BEES 数据组件（无数量上限），蜜蜂朝权杖起始点汇聚后消失；</li>
 *   <li>BeeBoss 蜜蜂（NBT {@code BeeBoss:1b}，含 {@code /jafa beeboss} 召唤的）<b>不会被吸收</b>：不牵引、不计入 BEES、不消耗耐久；</li>
 *   <li>对准生物左键：释放全部蜜蜂（HasStung=false、清空 hive_pos、索敌范围扩到 160），全部仇视该生物；</li>
 *   <li>按左 Alt：每隔 10 刻释放一只蜜蜂（HasStung=false、清空 hive_pos），这些蜜蜂永不仇视持有本权杖的玩家；</li>
 *   <li>主手从蜂巢权杖切到蜂巢方块物品时，权杖里已存的蜜蜂自动转移到该蜂巢物品。</li>
 * </ul>
 */
public final class BeehiveStaffHelper {

    public static final int ABSORB_RADIUS = 50;
    public static final int RELEASE_RANGE = 128;
    public static final int FOLLOW_RANGE_AGGRESSIVE = 160;
    /** 权杖起始点：玩家眼睛前方多少个方块的距离。 */
    public static final double ABSORB_POINT_DISTANCE = 1.8;

    /** 标记「永不仇视该玩家」的持久化键，值为持有权杖玩家的 UUID。 */
    public static final String FRIENDLY_TAG = "jafa_bee_friendly_owner";

    /** 挤压伤害免疫的持久化键，值为免疫截止的 tickCount。 */
    public static final String CRUSH_UNTIL_TAG = "jafa_crush_until";
    /** 释放的蜜蜂获得 1 秒（20 刻）挤压伤害免疫。 */
    public static final int CRUSH_IMMUNITY_TICKS = 20;
    /** 吸收进行中的标记（值为截止 tickCount，每刻吸收时刷新）：被吸收的蜜蜂/Beeper 置为此，
     *  使 Beeper 的追逐/自爆逻辑让位于吸收流程（避免两套速度互相覆盖）。吸收停止后自然过期，Beeper 自动恢复追逐。 */
    public static final String ABSORBING_TAG = "jafa_absorbing";

    /** 记录「本次由吸收流程下发的寻路指令有效到哪一刻」，供 {@link #absorbBoost} 判断当前导航路径是否通向权杖。 */
    public static final String ABSORB_REPATH_TAG = "jafa_absorb_repath";

    /** 吸收过程中给同一只蜜蜂重新下发寻路指令的间隔（刻）——<b>必须节流</b>。
     *  <p>原因：{@code PathNavigation} 的 A* 节点预算与搜索半径都由 FOLLOW_RANGE 决定
     *  （构造时 {@code maxVisitedNodes = FOLLOW_RANGE × 16}，搜索区域半径为 {@code FOLLOW_RANGE + 16}）。
     *  本 mod 释放出的蜜蜂 {@code FOLLOW_RANGE = 160}，于是每次 {@code moveTo} 都要
     *  <b>新建一个 353³ 方块的搜索区域并跑一次上限 2560 节点的 A*</b>。
     *  原先每刻给每只蜜蜂调一次，25 只蜜蜂就是 500 次/秒，回收时的卡顿就来自这里；
     *  改成每 10 刻一次后降为 50 次/秒，位移仍由每刻的 {@link #absorbBoost} 直接给速度保证。 */
    public static final int ABSORB_REPATH_INTERVAL = 10;

    /** 距离权杖起始点近于此值就不再重算路径，直接靠速度吸附（省掉绝大多数无意义的 A*）。 */
    public static final double ABSORB_NO_PATH_DISTANCE = 10.0;

    private BeehiveStaffHelper() {
    }
    // ---------------- 存取 BEES 组件 ----------------

    public static boolean isBeeStaff(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof StaffItem
            && "bee_nest".equals(stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"));
    }

    public static List<BeehiveBlockEntity.Occupant> getBees(ItemStack stack) {
        // 防御性拷贝：避免调用方拿到共享列表实例（用户观察到“所有蜂巢共享数据”即可能源于此）
        return List.copyOf(stack.getOrDefault(DataComponents.BEES, List.of()));
    }

    public static void setBees(ItemStack stack, List<BeehiveBlockEntity.Occupant> bees) {
        stack.set(DataComponents.BEES, List.copyOf(bees));
    }

    public static int countBees(ItemStack stack) {
        return getBees(stack).size();
    }

    /** 把一只蜜蜂捕获进物品的 BEES 组件并销毁该实体；播放一次蜜蜂入巢音效，并消耗 1 点耐久（非创造）。
     *  <p>持有者可以是玩家，也可以是 BeeBoss 这类生物（回收流程同源）：非玩家持有者不消耗耐久。 */
    public static void captureAndDiscardBee(LivingEntity owner, ItemStack stack, Bee bee) {
        // 双保险：BeeBoss 不可被吸收（正常已在 absorbTick 中提前跳过，此处防御其它调用方/未来新增路径）
        if (BeeBossData.isBeeBoss(bee)) {
            return;
        }
        // 清除吸收提速/吸收中标记，避免把加速状态或「吸收中」残留存进 BEES 数据（否则释放会恢复不到原速/卡在吸收态）
        clearAbsorbState(bee);
        List<BeehiveBlockEntity.Occupant> next = new ArrayList<>(getBees(stack));
        next.add(BeehiveBlockEntity.Occupant.of(bee));
        setBees(stack, next);
        // 每回收一只播放一次蜜蜂入巢音效（服务端广播）
        owner.level().playSound(null, bee.getX(), bee.getY(), bee.getZ(),
            net.minecraft.sounds.SoundEvents.BEEHIVE_ENTER,
            net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        // 每回收一只消耗耐久（优先方块耐久，方块耐久耗尽才扣权杖本体）；仅玩家持有者消耗
        if (owner instanceof ServerPlayer player && !player.getAbilities().instabuild) {
            ModMain.hurtStaff(stack, 1, player,
                net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        bee.discard();
    }

    /** 让实体在接下来 ticks 刻内免疫挤压伤害（写入持久化标记，由 BeeCrushImmunityMixin 判定）。 */
    public static void grantCrushImmunity(LivingEntity entity, int ticks) {
        entity.getPersistentData().putInt(CRUSH_UNTIL_TAG, entity.tickCount + ticks);
    }

    // ---------------- 功能1：长按吸收 ----------------

    /** 每刻吸收：让 50 格内蜜蜂主动寻路到权杖起始点，靠近即捕获存入权杖；整个寻路过程免疫挤压伤害。
     *  <p>持有者可以是玩家（长按右键的原路径），也可以是 BeeBoss 这类生物——Boss 的「右键长按」回收直接复用它。
     *  <p>会排除持有者自身：Boss 自己就是 Bee，若不排除会把自己也当成可吸收目标。 */
    public static void absorbTick(LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack stack = owner.getMainHandItem();
        if (!isBeeStaff(stack)) {
            return;
        }
        Vec3 point = owner.getEyePosition(1.0F).add(owner.getLookAngle().scale(ABSORB_POINT_DISTANCE))
            .subtract(0.0, 0.1, 0.0);
        AABB box = owner.getBoundingBox().inflate(ABSORB_RADIUS);
        for (Bee bee : level.getEntitiesOfClass(Bee.class, box, e -> e.isAlive() && e != owner)) {
            if (bee.isRemoved()) {
                continue;
            }
            // BeeBoss（NBT 标记 BeeBoss:1b，或由 /jafa beeboss 召唤）不会被蜂巢权杖吸收：
            // 整段跳过——不授予挤压免疫、不标记「吸收中」、不寻路牵引，让 Boss 保持在权杖范围内自由行动。
            if (BeeBossData.isBeeBoss(bee)) {
                continue;
            }
            // 整个吸收过程免疫挤压伤害（每刻刷新）；标记为「吸收中」（截止刻），让 Beeper 的追逐逻辑让位
            grantCrushImmunity(bee, CRUSH_IMMUNITY_TICKS);
            bee.getPersistentData().putInt(ABSORBING_TAG, bee.tickCount + 2);
            Vec3 to = point.subtract(bee.position());
            double dist = to.length();
            if (dist < 2.0) {
                captureAndDiscardBee(owner, stack, bee);
            } else if (dist <= ABSORB_RADIUS + 2) {
                // 主动寻路到权杖起始点（保留导航/动画），并在导航路径上叠加速度以提速回收：
                // 蜜蜂的平滑 MoveControl 无视 moveTo 速度和任何速度属性，只能靠直接设速度让蜜蜂更快飞向权杖。
                // 节流见 ABSORB_REPATH_INTERVAL：moveTo 的代价是「新建 FOLLOW_RANGE 大小的搜索区域 + 一次 A*」，
                // 每刻每只调一次会造成明显卡顿；离得近时干脆不重算路径，靠速度吸附即可。
                if (dist > ABSORB_NO_PATH_DISTANCE
                    && bee.tickCount >= bee.getPersistentData().getInt(ABSORB_REPATH_TAG)) {
                    bee.getPersistentData().putInt(ABSORB_REPATH_TAG, bee.tickCount + ABSORB_REPATH_INTERVAL);
                    bee.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT);
                    bee.getNavigation().moveTo(point.x, point.y, point.z, 3.2);
                }
                absorbBoost(bee, point, ABSORB_BOOST);
            }
        }
    }

    /** 回收时让蜜蜂加速飞向权杖起始点的速度：原版蜜蜂飞行速度的 2 倍（正常约 0.25 → 2 倍约 0.5 格/刻）。 */
    private static final double ABSORB_BOOST = 0.5;

    /** 直接设速度让蜜蜂加速——以「导航路径当前节点」为方向，使蜜蜂循导航路径真正"寻路"回权杖，
     *  而非被强行拉直线（可绕障、看起来是主动飞回来）。
     *  <p>只有在<b>当前导航路径确实通向权杖</b>时才沿路径加速：寻路指令是节流下发的
     *  （{@link #ABSORB_REPATH_INTERVAL}），中间那几刻蜜蜂自己的 AI 可能已经把它改成追击目标的路径，
     *  此时若仍沿路径加速，就会把蜜蜂越推离权杖越远。判定失败时直接朝权杖起始点加速。 */
    private static void absorbBoost(Bee bee, Vec3 point, double speed) {
        Path path = bee.getNavigation().getPath();
        BlockPos pathTarget = path == null ? null : path.getTarget();
        boolean pathLeadsToStaff = path != null && !path.isDone()
            && pathTarget != null && pathTarget.closerToCenterThan(point, 4.0);
        Vec3 dest = pathLeadsToStaff ? path.getNextEntityPos(bee) : point;
        Vec3 to = dest.subtract(bee.position());
        double d = to.length();
        if (d < 1.0E-3) {
            return;
        }
        bee.setDeltaMovement(to.scale(speed / d));
    }

    /** 清理吸收相关持久化标记（捕获入库、释放出巢时调用），确保释放出的蜜蜂/苦力蜂不残留「吸收中」状态。 */
    private static void clearAbsorbState(Bee bee) {
        bee.getPersistentData().remove(ABSORBING_TAG);
        bee.getPersistentData().remove(ABSORB_REPATH_TAG);
    }

    // ---------------- 功能2：左键释放全部并仇视目标 ----------------

    /** 释放出的蜜蜂的仇视时长（刻）：玩家左键释放沿用旧值 320（16 秒）。
     *  蜜蜂飞行速度约 0.25 格/刻，16 秒只够飞约 80 格——所以玩家从 128 格射程远距离群放时，
     *  本来就会有相当一部分蜜蜂在半路放弃追击。 */
    public static final int RELEASED_BEE_ANGER_TICKS = 160 * 2;

    /** BeeBoss 释放蜂群时的仇视时长（刻）：600（30 秒）。
     *  <p>比玩家那档长得多，因为 Boss 的整条「放蜂群 → 等它们蛰够 → 回收」循环依赖蜂群真的能扑到目标：
     *  用玩家那档 16 秒时，蜂群常在途中放弃，HasStung 比例永远上不去，回收也就永不触发。 */
    public static final int RELEASED_BEE_ANGER_TICKS_BOSS = 30 * 20;

    /** 把权杖中所有蜜蜂释放在持有者面前，全部仇视 target，索敌范围扩到 160。释放 N 只则一次性消耗 N 点耐久。
     *  <p>持有者可以是玩家（左键权杖的原路径），也可以是 BeeBoss 这类生物：非玩家持有者不扣耐久
     *  （生物没有创造模式豁免，且方块耐久耗尽会把权杖改写成方块物品，对 Boss 无意义）。
     *  <p>仇视时长取 {@link #RELEASED_BEE_ANGER_TICKS}（玩家档）；Boss 用自己的那一档走三参重载。
     *  @return 本次实际生成的蜜蜂实体列表（未释放出任何蜜蜂时为空列表）。BeeBoss 需要按这份列表
     *          跟踪它们后续的 {@code HasStung} 状态，以决定何时回收。 */
    public static List<Bee> releaseAllToTarget(LivingEntity owner, LivingEntity target) {
        return releaseAllToTarget(owner, target, RELEASED_BEE_ANGER_TICKS);
    }

    /** 同 {@link #releaseAllToTarget(LivingEntity, LivingEntity)}，但可指定释放出的蜜蜂的仇视时长。 */
    public static List<Bee> releaseAllToTarget(LivingEntity owner, LivingEntity target, int angerTicks) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return List.of();
        }
        ItemStack stack = owner.getMainHandItem();
        if (!isBeeStaff(stack)) {
            return List.of();
        }
        List<BeehiveBlockEntity.Occupant> occupants = getBees(stack);
        if (occupants.isEmpty()) {
            return List.of();
        }
        Vec3 p = owner.getEyePosition(1.0F).add(owner.getLookAngle().scale(1.0));
        // 释放时持有者自己也获得 1 秒挤压免疫
        grantCrushImmunity(owner, CRUSH_IMMUNITY_TICKS);
        List<Bee> spawned = new ArrayList<>(occupants.size());
        for (BeehiveBlockEntity.Occupant occ : occupants) {
            Bee bee = spawnStoredBee(level, occ, p.x, p.y, p.z, target,
                null, FOLLOW_RANGE_AGGRESSIVE, angerTicks);
            if (bee != null) {
                spawned.add(bee);
            }
        }
        setBees(stack, List.of());
        // 一次性扣除对应蜜蜂数量的耐久（优先方块耐久，非创造/旁观）；仅玩家持有者消耗耐久
        if (owner instanceof ServerPlayer player && !player.getAbilities().instabuild) {
            ModMain.hurtStaff(stack, occupants.size(), player,
                net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        return spawned;
    }

    // ---------------- 功能3：左Alt 逐个释放（友善） ----------------

    /** 每隔 10 刻释放一只友善蜜蜂（永不仇视持有本权杖的玩家）。
     *  限流由客户端 10-tick 倒计时负责，服务端不再做包到达计数，避免「每来一个包才-1」造成 ~100 刻假冷却。 */
    public static boolean releaseOneFriendly(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!isBeeStaff(stack)) {
            return false;
        }
        List<BeehiveBlockEntity.Occupant> occupants = new ArrayList<>(getBees(stack));
        if (occupants.isEmpty()) {
            return false;
        }
        BeehiveBlockEntity.Occupant occ = occupants.remove(occupants.size() - 1);
        setBees(stack, occupants);
        Vec3 p = player.getEyePosition(1.0F).add(player.getLookAngle().scale(0.6));
        // 逐只释放时玩家自己也获得挤压免疫
        grantCrushImmunity(player, CRUSH_IMMUNITY_TICKS);
        spawnStoredBee((ServerLevel) player.level(), occ, p.x, p.y - 0.1, p.z,
            null, player.getUUID(), 0, 0);
        // 释放一只消耗 1 点耐久（优先方块耐久，非创造/旁观）
        if (!player.getAbilities().instabuild) {
            ModMain.hurtStaff(stack, 1, player,
                net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        return true;
    }

    /** 生成一只存储的蜜蜂。@return 实际生成的蜜蜂实体；数据为空或类型不存在时返回 {@code null}。 */
    private static Bee spawnStoredBee(ServerLevel level, BeehiveBlockEntity.Occupant occ,
                                      double x, double y, double z, LivingEntity target,
                                      UUID friendlyOwner, int followRange, int angerTicks) {
        CompoundTag entityData = occ.entityData().copyTag();
        if (entityData.isEmpty()) {
            return null;
        }
        Bee bee;
        if (entityData.getBoolean("jafa_beeper")) {
            bee = cn.autoforged.joes_addons_for_abmc.entity.ModEntities.BEEPER.get().create(level);
        } else {
            bee = new Bee(EntityType.BEE, level);
        }
        if (bee == null) {
            return null;
        }
        entityData.putBoolean(Bee.TAG_HAS_STUNG, false);
        bee.readAdditionalSaveData(entityData);
        // 强制关闭 NoAI（存储 NBT 可能残留 NoAI=true，会抑制导航/索敌）
        bee.setNoAi(false);
        // 兜底清理吸收提速/吸收中标记（正常由 capture 清理，这里防残留：释放出的蜜蜂/苦力蜂应恢复原速并恢复追逐能力）
        clearAbsorbState(bee);
        // 清空 hive_pos，防止飞回旧巢
        bee.setSavedFlowerPos(null);
        bee.setHivePos(null);
        // 释放时给每只蜜蜂一个随机的出生位置偏移，让集体释放时物理上从不同点散开成一群，
        // 而不是全部叠在同一点（视觉上看起来像只有一只）。
        // 注意：不能用「初速度」散开——蜜蜂自己的 MoveControl/导航会在下一刻立刻把它覆盖回同一点，只有位置偏移才有效。
        double offX = (bee.getRandom().nextDouble() - 0.5) * 2.0; // ±1 格
        double offY = (bee.getRandom().nextDouble() - 0.2) * 0.4; // 轻微上扩
        double offZ = (bee.getRandom().nextDouble() - 0.5) * 2.0;
        bee.setPos(x + offX, y + 0.3 + offY, z + offZ);
        // 释放后 1 秒免疫挤压伤害（避免卡进墙/方块内被压死）
        grantCrushImmunity(bee, CRUSH_IMMUNITY_TICKS);
        // 每释放一只播放一次蜜蜂出巢音效（同时释放 N 只则同时播放 N 次）
        level.playSound(null, x, y, z,
            net.minecraft.sounds.SoundEvents.BEEHIVE_EXIT,
            net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        if (friendlyOwner != null) {
            bee.getPersistentData().putUUID(FRIENDLY_TAG, friendlyOwner);
        }
        if (followRange > 0 && bee.getAttribute(Attributes.FOLLOW_RANGE) != null) {
            bee.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(followRange);
        }
        if (target != null) {
            if (bee instanceof cn.autoforged.joes_addons_for_abmc.entity.BeeperEntity beeper) {
                // 苦力蜂：记录攻击目标，飞向目标并自爆（不蛰刺）
                beeper.getPersistentData().putUUID("beeper_target_uuid", target.getUUID());
            } else {
                bee.setTarget(target);
                bee.setPersistentAngerTarget(target.getUUID());
                bee.setRemainingPersistentAngerTime(angerTicks);
            }
        }
        level.addFreshEntity(bee);
        return bee;
    }

    // ---------------- 射程预判（服务端 128 格） ----------------

    /** 从玩家眼睛沿视线做最大 RELEASE_RANGE 的实体预判，命中且可及视线即返回该生物。 */
    public static LivingEntity raycastLiving(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
            player, eye, end, box,
            e -> !e.isSpectator() && e.isPickable() && e instanceof LivingEntity && e != player,
            range * range);
        if (hit != null && hit.getEntity() instanceof LivingEntity le && player.hasLineOfSight(le)) {
            return le;
        }
        return null;
    }

    // ---------------- 主手持蜂巢：从物品栏权杖自动转移 ----------------

    /** 若主手拿着「空的蜂巢物品」且物品栏某处有「带蜜蜂的蜂巢权杖」，把蜜蜂转移到主手蜂巢并清空权杖。
     *  返回转移后的主手蜂巢栈（带蜜蜂）；未发生转移返回 null。
     *  注意：清空权杖必须用 inv.setItem(i, 拷贝) 显式写回槽位，并强制物品栏同步，
     *  否则客户端仍持旧数据会在下次同步时把服务端修改覆盖回去。 */
    public static ItemStack ensureBeesFromStaff(Player player) {
        if (!(player.level() instanceof ServerLevel)) {
            return null;
        }
        ItemStack cur = player.getMainHandItem();
        if (!cur.is(Items.BEE_NEST)) {
            return null;
        }
        if (countBees(cur) > 0) {
            return cur; // 主手蜂巢已有蜜蜂
        }
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof StaffItem && countBees(s) > 0) {
                ItemStack modified = cur.copy();
                setBees(modified, getBees(s));
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, modified);
                // 显式写回权杖槽位（清空蜜蜂），并强制物品栏同步
                ItemStack cleared = s.copy();
                setBees(cleared, List.of());
                inv.setItem(i, cleared);
                player.inventoryMenu.broadcastChanges();
                return modified;
            }
        }
        // 副手中的权杖
        ItemStack off = inv.offhand.get(0);
        if (off.getItem() instanceof StaffItem && countBees(off) > 0) {
            ItemStack modified = cur.copy();
            setBees(modified, getBees(off));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, modified);
            ItemStack cleared = off.copy();
            setBees(cleared, List.of());
            inv.offhand.set(0, cleared);
            player.inventoryMenu.broadcastChanges();
            return modified;
        }
        return null;
    }
}