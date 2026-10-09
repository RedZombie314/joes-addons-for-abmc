package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 幸运结构子事件：<b>生成"幸运水井"</b>（{@code data/joes_addons_for_abmc/structure/lucky_well.nbt}）。
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>破坏幸运方块后按概率抽到本事件：把结构放在破坏点附近，<b>取结构四角中地表最低的那个角落作为起点</b>，
 *       并先把该体积内的方块<b>全部替换</b>（清空后再放置，等价于"替换掉其周围的所有方块"）；</li>
 *   <li>给玩家物品栏放入一枚<b>金粒</b>（伴随物品捡起音效）；物品栏满则掉在玩家脚下、无拾取延迟可立刻捡起；</li>
 *   <li>玩家把金粒<b>丢进结构里唯一的水源</b>后：金粒消失，等待 <b>2~3 秒</b>，按权重随机发生三件事之一
 *       （见 {@link #resolve}，权重 45 / 45 / 10）。</li>
 * </ol>
 *
 * <h3>"只能生效一次"</h3>
 * 每座水井<b>各自只能生效一次</b>：结算后立即从追踪表移除，之后再往同一口井里丢金粒不会有任何反应；
 * 破坏新的幸运方块仍可生成新的水井（允许多座并存，各自独立）。
 */
public final class LuckyWellEvent {
    /** 结构模板 id（对应 {@code data/joes_addons_for_abmc/structure/lucky_well.nbt}）。 */
    private static final ResourceLocation TEMPLATE_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "lucky_well");

    /** 投入金粒后到结算的等待时间（游戏刻）：2~3 秒。 */
    private static final int DELAY_MIN_TICKS = 40;
    private static final int DELAY_MAX_TICKS = 60;

    /** 结果一：结构上方 2 格生成 15~20 个引信 40 的 TNT。 */
    private static final int TNT_MIN = 15;
    private static final int TNT_MAX = 20;
    private static final int TNT_FUSE = 40;
    /** 结果二/三：10~20 个掉落物。 */
    private static final int DROP_MIN = 10;
    private static final int DROP_MAX = 20;
    /** 结果二的权重（金粒）= 45，结果一同为 45，土豆 10。 */
    private static final int WEIGHT_TNT = 45;
    private static final int WEIGHT_GOLD = 45;

    /** 尚未结算的水井（最多一条）。 */
    private static final List<Well> WELLS = new ArrayList<>();


    private LuckyWellEvent() {
    }

    /** 一座已生成的水井。 */
    private static final class Well {
        private final ServerLevel level;
        private final BlockPos waterPos;
        private final BlockPos origin;
        private final Vec3i size;
        /** < 0 表示还没投入金粒；>= 0 表示距结算的剩余刻数。 */
        private int countdown = -1;

        private Well(ServerLevel level, BlockPos waterPos, BlockPos origin, Vec3i size) {
            this.level = level;
            this.waterPos = waterPos;
            this.origin = origin;
            this.size = size;
        }
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("structure/lucky_well"), LuckyEventCategory.LUCKY_STRUCTURE,
            "生成一口幸运水井，并给玩家一枚金粒（丢进井中水源可许愿）",
            LuckyWellEvent::start);
        LuckyEvents.register(event);
        return event;
    }

    private static void start(ServerLevel level, BlockPos pos, @Nullable Player player) {
        // 每座井各自只能生效一次；但新的幸运方块仍可生成新的井（允许同时存在多座）

        StructureTemplate template = level.getStructureManager().get(TEMPLATE_ID).orElse(null);
        if (template == null) return;
        Vec3i size = template.getSize();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) return;

        BlockPos origin = lowestCornerOrigin(level, pos, size);
        BlockPos max = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        // 替换掉结构范围内的所有方块（先清空再放置）
        for (BlockPos p : BlockPos.betweenClosed(origin, max)) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2 | 16);

        BlockPos water = findWaterSource(level, origin, max);
        WELLS.add(new Well(level, water != null ? water : origin, origin, size));

        giveGoldNugget(level, player);
    }

    /**
     * 取结构四角中<b>地表最低</b>的角落作为起点：返回的坐标是"结构最小角"，
     * 使那个角落正好落在地表上（其余角落可能被埋或悬空，符合"以最低角落为起点"的要求）。
     */
    private static BlockPos lowestCornerOrigin(ServerLevel level, BlockPos pos, Vec3i size) {
        int baseX = pos.getX() - size.getX() / 2;
        int baseZ = pos.getZ() - size.getZ() / 2;
        int[][] corners = {{0, 0}, {size.getX() - 1, 0}, {0, size.getZ() - 1},
            {size.getX() - 1, size.getZ() - 1}};

        BlockPos best = null;
        int bestY = Integer.MAX_VALUE;
        for (int[] corner : corners) {
            int x = baseX + corner[0];
            int z = baseZ + corner[1];
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y < bestY) {
                bestY = y;
                best = new BlockPos(x - corner[0], y, z - corner[1]);
            }
        }
        return best != null ? best : pos;
    }

    /** 结构里唯一的水源（找不到则返回 null）。 */
    @Nullable
    private static BlockPos findWaterSource(ServerLevel level, BlockPos origin, BlockPos max) {
        for (BlockPos p : BlockPos.betweenClosed(origin, max)) {
            if (level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p).isSource()) {
                return p.immutable();
            }
        }
        return null;
    }

    /** 给玩家一枚金粒：物品栏放得下就放物品栏，放不下就掉在脚下（无拾取延迟，可立刻捡起），并播放捡起音效。 */
    private static void giveGoldNugget(ServerLevel level, @Nullable Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ItemStack nugget = new ItemStack(Items.GOLD_NUGGET);
        if (!serverPlayer.getInventory().add(nugget)) {
            ItemEntity drop = new ItemEntity(level, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), nugget);
            drop.setPickUpDelay(0);
            level.addFreshEntity(drop);
        }
        level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
            SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F,
            ((level.random.nextFloat() - level.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
    }

    /** 由服务端 tick 钩子（{@code ModMain.onServerTickPre}）每刻调用。 */
    public static void tick() {
        if (WELLS.isEmpty()) return;
        Iterator<Well> iterator = WELLS.iterator();
        while (iterator.hasNext()) {
            Well well = iterator.next();
            if (well.countdown < 0) {
                // 等玩家把金粒丢进水源
                List<ItemEntity> items = well.level.getEntitiesOfClass(ItemEntity.class,
                    new AABB(well.waterPos).inflate(0.5D));
                for (ItemEntity item : items) {
                    if (!item.getItem().is(Items.GOLD_NUGGET)) continue;
                    item.discard(); // 金粒消失
                    well.countdown = DELAY_MIN_TICKS
                        + well.level.random.nextInt(DELAY_MAX_TICKS - DELAY_MIN_TICKS + 1);
                    break;
                }
            } else if (--well.countdown <= 0) {
                resolve(well);
                iterator.remove(); // 用掉即作废：这座井再也不会生效
            }
        }
    }

    /** 结算：按权重 45 / 45 / 10 抽一件事发生。 */
    private static void resolve(Well well) {
        int roll = well.level.random.nextInt(WEIGHT_TNT + WEIGHT_GOLD + 10);
        if (roll < WEIGHT_TNT) {
            spawnTntRing(well);
        } else if (roll < WEIGHT_TNT + WEIGHT_GOLD) {
            spawnDrops(well, Items.GOLD_NUGGET);
        } else {
            spawnDrops(well, Items.POTATO);
        }
    }

    /** 结果一：结构正上方 2 格处生成 15~20 个引信 40 的 TNT，水平方向、四周随机分布、带较小初速度。 */
    private static void spawnTntRing(Well well) {
        RandomSource random = well.level.random;
        int count = TNT_MIN + random.nextInt(TNT_MAX - TNT_MIN + 1);
        double baseY = well.origin.getY() + well.size.getY() + 2.0D; // 结构正上方 2 格
        for (int i = 0; i < count; i++) {
            double x = well.origin.getX() + random.nextDouble() * well.size.getX();
            double z = well.origin.getZ() + random.nextDouble() * well.size.getZ();
            PrimedTnt tnt = new PrimedTnt(well.level, x, baseY, z, null);
            tnt.setFuse(TNT_FUSE);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double speed = 0.05D + random.nextDouble() * 0.08D; // 较小初速度
            tnt.setDeltaMovement(Math.cos(angle) * speed, 0.0D, Math.sin(angle) * speed); // 仅水平方向
            well.level.addFreshEntity(tnt);
        }
    }

    /** 结果二/三：结构周围 xz 取 -3~3、y 取 3~5 的随机位置生成 10~20 个掉落物。 */
    private static void spawnDrops(Well well, Item item) {
        RandomSource random = well.level.random;
        int count = DROP_MIN + random.nextInt(DROP_MAX - DROP_MIN + 1);
        for (int i = 0; i < count; i++) {
            double x = well.origin.getX() + 0.5D + (random.nextInt(7) - 3); // -3~3
            double z = well.origin.getZ() + 0.5D + (random.nextInt(7) - 3);
            double y = well.origin.getY() + 3 + random.nextInt(3);          // 3~5
            ItemEntity drop = new ItemEntity(well.level, x, y, z, new ItemStack(item));
            drop.setDeltaMovement((random.nextDouble() - 0.5D) * 0.1D, 0.0D, (random.nextDouble() - 0.5D) * 0.1D);
            well.level.addFreshEntity(drop);
        }
    }
}
