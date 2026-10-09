package cn.autoforged.joes_addons_for_abmc.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 工作台帽子（{@code crafting_table_hat}）。
 * <p><b>性质</b>：戴在头上（{@link ArmorItem.Type#HELMET}），<b>不提供护甲值</b>
 * （{@link #getDefaultAttributeModifiers()} 直接返回空），<b>没有耐久</b>
 * （注册时不设 {@code durability}，与雕刻南瓜一样不会损坏）。
 * 外观暂时完全借用皮革头盔：物品图标用 {@code minecraft:item/leather_helmet}，
 * 戴在头上的模型/贴图用 {@link ModArmorMaterials#CRAFTING_TABLE_HAT} 里指向的
 * {@code minecraft:leather} 护甲贴图层。属于幸运物品池（{@link ModItemTags#LUCKY_ITEMS}）。
 *
 * <h3>右键触发"合成"（框架）</h3>
 * 触发条件：<b>戴着这顶帽子</b>且<b>主手为空</b>时按下右键（客户端按键按下瞬间发一次请求，
 * 服务端再二次校验并自行做射线判定，避免依赖客户端的命中结果）。
 * 触发点按以下三种情况决定，最终都会汇入 {@link #onCraftingTriggered}：
 * <ol>
 *   <li>{@link CraftingTrigger#BLOCK}：对着方块右键（在方块交互距离内，且该方块不是岩浆、
 *       接触点及其相邻格也不接触岩浆）→ 触发点 = 方块表面的接触点；</li>
 *   <li>{@link CraftingTrigger#ENTITY}：对着生物右键（在实体交互距离内）→ 触发点 =
 *       视线与该生物碰撞箱的交点；</li>
 *   <li>{@link CraftingTrigger#SELF}：其余情况（对着空气、方块是岩浆/接触岩浆、没有命中生物）
 *       → 触发点 = 玩家所在坐标。</li>
 * </ol>
 */
public class CraftingTableHatItem extends ArmorItem {
    /** 右键触发来源（三种情况）。 */
    public enum CraftingTrigger {
        /** 对着方块：点在方块表面的接触点。 */
        BLOCK,
        /** 对着生物：点在视线与碰撞箱的交点。 */
        ENTITY,
        /** 其余情况：点在玩家坐标。 */
        SELF
    }

    public CraftingTableHatItem(Holder<ArmorMaterial> material, Properties properties) {
        super(material, ArmorItem.Type.HELMET, properties);
    }

    /**
     * 不提供护甲值：直接返回空属性表。
     * <p>（没有这一步的话，{@code ArmorItem} 会按材质防御力塞一个 +0 的护甲修饰符，
     * 虽然数值是 0，但没必要留。）
     */
    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return ItemAttributeModifiers.EMPTY;
    }

    /** 玩家头上是否戴着工作台帽子。 */
    public static boolean isWearing(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof CraftingTableHatItem;
    }

    // ================= 服务端：处理客户端的右键请求 =================

    /**
     * 客户端"戴着帽子 + 主手为空 + 按下右键"时发来请求，这里做服务端二次校验与情况判定。
     * <p>判定用服务端自己的射线（{@link Item#getPlayerPOVHitResult} / {@link ProjectileUtil#getEntityHitResult}），
     * 半径就是玩家当前的交互距离，因此天然满足"在交互距离以内"。
     */
    public static void handleUseRequest(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        // 条件：戴着帽子 + 主手为空（+ 非旁观）
        if (serverPlayer.isSpectator()) return;
        if (!isWearing(serverPlayer)) return;
        if (!serverPlayer.getMainHandItem().isEmpty()) return;

        ServerLevel level = serverPlayer.serverLevel();

        // 情况 1：对着方块
        BlockHitResult blockHit = Item.getPlayerPOVHitResult(level, serverPlayer, ClipContext.Fluid.NONE);
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            // 可交互方块（会开界面 / 右键本身有动作）→ 这次右键交给方块，不触发合成事件
            if (isInteractiveBlock(level, blockHit)) return;
            // 不涉及岩浆 → 在方块表面接触点触发
            if (!touchesLava(level, serverPlayer, blockHit)) {
                onCraftingTriggered(level, serverPlayer, blockHit.getLocation(),
                    CraftingTrigger.BLOCK, blockHit.getBlockPos(), null);
                return;
            }
            // 涉及岩浆 → 落到情况 3（玩家坐标）
        }

        // 情况 2：对着生物 → 视线与碰撞箱的交点
        EntityHitResult entityHit = raycastLiving(serverPlayer);
        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
            onCraftingTriggered(level, serverPlayer, entityHit.getLocation(),
                CraftingTrigger.ENTITY, null, target);
            return;
        }

        // 情况 3：其余情况 → 玩家坐标
        onCraftingTriggered(level, serverPlayer, serverPlayer.position(), CraftingTrigger.SELF, null, null);
    }

    /** 视线命中的第一个生物（射线长度 = 玩家当前的实体交互距离）。 */
    @Nullable
    private static EntityHitResult raycastLiving(ServerPlayer player) {
        double range = player.entityInteractionRange();
        Vec3 from = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 to = from.add(look.scale(range));
        AABB search = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
            player, from, to, search,
            e -> !e.isSpectator() && e.isPickable() && e instanceof LivingEntity && e != player,
            range * range);
        if (hit != null && hit.getEntity() instanceof LivingEntity living && player.hasLineOfSight(living)) {
            return hit;
        }
        return null;
    }

    /**
     * 判定这次方块命中是否"涉及岩浆" → 涉及就不走情况 1（改走情况 3）。
     * <p>对应需求里的"该方块不是岩浆、且交互点不与岩浆接触"，分三层检查：
     * <ol>
     *   <li>命中的方块本身是岩浆；</li>
     *   <li>接触点所在格、以及接触面外侧那一格接触岩浆；</li>
     *   <li>视线在到达接触点之前穿过了岩浆 —— 方块射线用的是 {@link ClipContext.Fluid#NONE}
     *       （和客户端准星一致，会忽略液体），所以对着岩浆湖看时射线会直接命中后面的方块；
     *       这里再单独用 {@link ClipContext.Fluid#ANY} 沿同一段视线补一次液体检测，
     *       中途撞到岩浆就判为涉及岩浆。</li>
     * </ol>
     */
    private static boolean touchesLava(ServerLevel level, ServerPlayer player, BlockHitResult hit) {
        if (isLavaAt(level, hit.getBlockPos())) return true;
        if (isLavaAt(level, BlockPos.containing(hit.getLocation()))) return true;
        if (isLavaAt(level, hit.getBlockPos().relative(hit.getDirection()))) return true;
        BlockHitResult alongSight = level.clip(new ClipContext(
            player.getEyePosition(), hit.getLocation(),
            ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        return alongSight.getType() == HitResult.Type.BLOCK && isLavaAt(level, alongSight.getBlockPos());
    }

    private static boolean isLavaAt(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getFluidState().is(FluidTags.LAVA);
    }

    /**
     * 是否"可交互方块"：右键它时这次点击会被方块自己消费（开界面 / 开门 / 按按钮…）。
     * <p>按需求：<b>右击可交互方块不触发合成事件</b>（这次右键交给方块）。
     * <p>判定方式（服务端、无副作用，不去真的调用 {@code useWithoutItem}，避免把门打开之类）：
     * <ol>
     *   <li>{@code getMenuProvider(...) != null} —— 所有会打开界面的方块：箱子/熔炉/工作台/铁砧/讲台/
     *       酿造台/漏斗/发射器/织布机/切石机/附魔台/信标/潜影盒…；</li>
     *   <li>原版"空手右键就有动作"的方块标签：按钮、门、活板门、栅栏门、花盆、床、蜡烛、告示牌、
     *       铁砧、炼药锅、潜影盒；</li>
     *   <li>零散的几个：拉杆、音符盒、唱片机、蛋糕、钟、龙蛋。</li>
     * </ol>
     * 注意：模组方块若既没有界面、也不在这些标签里，会被当作"非交互"处理（即会触发合成事件）。
     */
    private static boolean isInteractiveBlock(ServerLevel level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.getMenuProvider(level, pos) != null) return true;
        if (state.is(BlockTags.BUTTONS)
            || state.is(BlockTags.DOORS)
            || state.is(BlockTags.TRAPDOORS)
            || state.is(BlockTags.FENCE_GATES)
            || state.is(BlockTags.FLOWER_POTS)
            || state.is(BlockTags.BEDS)
            || state.is(BlockTags.CANDLES)
            || state.is(BlockTags.ALL_SIGNS)
            || state.is(BlockTags.ANVIL)
            || state.is(BlockTags.CAULDRONS)
            || state.is(BlockTags.SHULKER_BOXES)) {
            return true;
        }
        Block block = state.getBlock();
        return block == Blocks.LEVER
            || block == Blocks.NOTE_BLOCK
            || block == Blocks.JUKEBOX
            || block == Blocks.CAKE
            || block == Blocks.BELL
            || block == Blocks.DRAGON_EGG;
    }

    // ================= 合成事件 =================

    /**
     * <b>合成事件入口</b>：帽子的三种右键情况最终都会调用到这里。
     * <p>这里只负责"在哪触发"，真正的合成流程（检索配方 → 取材料 → 物品展示实体飞行 →
     * 点击到达后产出 / 途中松开则取消掉落）都在
     * {@link cn.autoforged.joes_addons_for_abmc.craftinghat.CraftingHatCrafting#tryStart}。
     *
     * @param level    触发所在的服务端世界
     * @param player   戴着帽子并按右键的玩家
     * @param point    触发点（世界坐标）：
     *                 {@link CraftingTrigger#BLOCK} = 方块表面的接触点；
     *                 {@link CraftingTrigger#ENTITY} = 视线与该生物碰撞箱的交点；
     *                 {@link CraftingTrigger#SELF} = 玩家所在坐标
     * @param trigger  触发来源（三种情况，见枚举）
     * @param blockPos 情况 {@link CraftingTrigger#BLOCK} 命中的方块坐标，其余情况为 {@code null}
     * @param entity   情况 {@link CraftingTrigger#ENTITY} 命中的生物，其余情况为 {@code null}
     */
    public static void onCraftingTriggered(ServerLevel level, ServerPlayer player, Vec3 point,
                                           CraftingTrigger trigger, @Nullable BlockPos blockPos,
                                           @Nullable Entity entity) {
        cn.autoforged.joes_addons_for_abmc.craftinghat.CraftingHatCrafting.tryStart(player, point);
    }

    /**
     * 客户端松开右键：取消还在飞行中的合成事件（材料原样掉在触发点）。
     * <p>没有正在进行的合成事件时是空操作。
     */
    public static void handleCancelRequest(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        cn.autoforged.joes_addons_for_abmc.craftinghat.CraftingHatCrafting.cancel(serverPlayer);
    }
}
