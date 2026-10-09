package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.block.LuckyEvents;
import cn.autoforged.joes_addons_for_abmc.entity.ModEntities;
import cn.autoforged.joes_addons_for_abmc.entity.PilotGoldenHorseEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 幸运核心的两条<b>专属机制</b>（需求 6.5.26 的第 8、9 条）。这两件都不适合丢进
 * {@link OrbOfLuckEvents} 的通用事件表里当普通条目，所以单独放一处：
 * <ul>
 *   <li>{@link #placeRollerMini(UseOnContext)} —— 原地生成 {@code luck_roller_mini} 结构（取代被点的方块），
 *       并在结构里随机一根铁轨上放一辆矿车；</li>
 *   <li>{@link #boostMinecart(Player)} —— 玩家<b>坐在矿车里</b>时"使用"核心：给矿车 100 m/s，
 *       并在车尾向后喷射大量彩色粒子（由 {@link Jet} 每刻推进）。</li>
 * </ul>
 */
public final class OrbSpecialMechanics {

    /** 结构名（{@code data/joes_addons_for_abmc/structure/luck_roller_mini.nbt}）。 */
    public static final String ROLLER_MINI = "luck_roller_mini";

    /** 需求给的矿车速度：100 m/s。原版换算 1 m/s = 0.05 格/刻，故 100 × 0.05 = 5 格/刻。 */
    public static final double MINECART_BOOST_PER_TICK = 100.0D * 0.05D;

    /** 喷射持续多久（刻）：5 秒。到点或车慢下来就停（见 {@link Jet}）。 */
    public static final int JET_TICKS = 100;

    /** 每刻喷几颗彩色尘粒子（"大量"）。 */
    public static final int JET_PARTICLES_PER_TICK = 12;

    /** 车慢到这个速度（格/刻）以下就不再喷了：说明这一脚已经跑完。 */
    private static final double JET_MIN_SPEED = 1.0D;

    /** 矿车持久化键：喷射持续到哪个游戏刻。 */
    private static final String TAG_JET_UNTIL = "jafa_orb_jet_until";

    private OrbSpecialMechanics() {
    }

    // ==================== 需求 8：luck_roller_mini 结构 + 铁轨上的矿车 ====================

    /**
     * <b>原地生成 {@code luck_roller_mini} 结构</b>（取代被点的那个方块），并在结构里随机一根铁轨上放一辆矿车。
     * <p>
     * 放置用原版 {@link StructureTemplate}（与女巫小屋那条同一个写法、同一套 {@link StructurePlaceSettings}）：
     * 以<b>被点方块</b>为原点、不旋转不镜像、flag 2（同步客户端但不触发邻接更新）。
     * 结构里的 {@code minecraft:air} 会自然把被点的方块替换掉——这正是需求说的"取代原来的方块"，
     * 不需要先手动破坏它。
     * <p>
     * 矿车：放置完直接在<b>世界里</b>扫这一小片找铁轨（{@code rail / powered_rail / detector_rail /
     * activator_rail}），随机挑一根，在它<b>正上方</b>生成一辆可乘的矿车（y 用 0.0625 的原版"压在轨上"偏移）。
     * 这样做的好处是不必去解析结构的调色板：结构长什么样、以后怎么改，这里都自动跟上。
     */
    public static boolean placeRollerMini(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.canInteractWithBlock(context.getClickedPos(), 1.0)) {
            return false;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return false;   // 做事只在服务端
        }
        StructureTemplate template = level.getStructureManager().getOrCreate(
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, ROLLER_MINI));
        if (template == null || template.getSize().equals(Vec3i.ZERO)) {
            ModMain.LOGGER.warn("[幸运核心] 结构 {} 不存在，这次右键作废", ROLLER_MINI);
            return false;
        }
        BlockPos origin = context.getClickedPos();
        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setMirror(Mirror.NONE)
            .setRotation(Rotation.NONE)
            .setIgnoreEntities(false);
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);

        Vec3i size = template.getSize();
        List<BlockPos> rails = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1,
                size.getZ() - 1))) {
            if (level.getBlockState(pos).is(Blocks.RAIL)
                || level.getBlockState(pos).is(Blocks.POWERED_RAIL)
                || level.getBlockState(pos).is(Blocks.DETECTOR_RAIL)
                || level.getBlockState(pos).is(Blocks.ACTIVATOR_RAIL)) {
                rails.add(pos.immutable());
            }
        }
        if (rails.isEmpty()) {
            ModMain.LOGGER.warn("[幸运核心] 结构 {} 里没找到铁轨，这一条只放了结构", ROLLER_MINI);
            return true;   // 结构放好了就算这次事件成功
        }
        BlockPos rail = rails.get(level.getRandom().nextInt(rails.size()));
        Minecart cart = EntityType.MINECART.create(level);
        if (cart != null) {
            cart.moveTo(rail.getX() + 0.5D, rail.getY() + 0.0625D, rail.getZ() + 0.5D, 0.0F, 0.0F);
            cart.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(cart);
        }
        ModMain.LOGGER.info("[幸运核心] 已生成 {} 结构于 {}，矿车放在铁轨 {}", ROLLER_MINI, origin, rail);
        return true;
    }

    // ==================== 需求 3'：会飞、可操控的金马铠马 ====================

    /** 地面/飞行金马那套的属性值：直接取实体上那个可调常量（30 格/秒），要改只改那一处。 */
    public static final double HORSE_MOVEMENT_SPEED = PilotGoldenHorseEntity.FLY_SPEED_PER_TICK;

    /**
     * <b>需求 3'</b>：在落点生成一匹"金马铠马"并让玩家<b>直接骑上去</b>——这匹马会飞，
     * 操控方式与旁观操控幸运选择器完全相同（见 {@link PilotGoldenHorseEntity}）。
     * <p>
     * 与幸运方块那条 {@code entity/golden_horse} 的区别就是"飞行 + 可操控"：那边是原版马，
     * 只能在地上按原版骑乘走。装备（鞍 + 金马铠）、归属、成年、生成粒子都照抄那一套，
     * 这样两匹"金马"看起来是同一件东西。
     * <p>
     * 直接 {@code startRiding} 而不是把马丢在地上：需求是"用核心就能飞着走"，
     * 让玩家再手动上马会多一步、而且刚生成时马会乱走。
     */
    public static boolean spawnPilotHorse(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.canInteractWithBlock(context.getClickedPos(), 1.0)) {
            return false;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        PilotGoldenHorseEntity horse = ModEntities.PILOT_GOLDEN_HORSE.get().create(level);
        if (horse == null) {
            return false;
        }
        horse.setAge(0);
        AttributeInstance speed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(HORSE_MOVEMENT_SPEED);
        }
        horse.equipSaddle(new ItemStack(Items.SADDLE), SoundSource.NEUTRAL);
        horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.GOLDEN_HORSE_ARMOR));
        horse.setOwnerUUID(player.getUUID());
        horse.setTamed(true);

        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        double y = LuckyEvents.findFreeY(level, horse, x, pos.getY(), z);
        horse.moveTo(x, y, z, player.getYRot(), 0.0F);
        horse.setPersistenceRequired();
        if (!level.addFreshEntity(horse)) {
            return false;
        }
        level.broadcastEntityEvent(horse, (byte) 7);   // 原版马铠爱心粒子
        player.startRiding(horse, true);
        ModMain.LOGGER.info("[幸运核心] 已生成可操控飞行金马于 {}，{} 已骑上", pos, player.getGameProfile().getName());
        return true;
    }

    // ==================== 需求 9：坐矿车时给 100 m/s + 向后喷彩色粒子 ====================

    /**
     * 玩家<b>坐在矿车里</b>"使用"核心：给矿车 {@link #MINECART_BOOST_PER_TICK}（= 100 m/s）并开启
     * 后续 {@link #JET_TICKS} 刻的车尾喷射。
     * <p>
     * 方向取玩家<b>当前水平朝向</b>（和需求里那条"用核心做点什么"的直觉一致：朝哪看就往哪冲）；
     * 竖直分量给 0——矿车本来就会被铁轨按轨道走向重算水平速度，这里只负责"把速度拉满"，
     * 原版的轨道摩擦/上坡减速照旧生效，所以冲出去之后会自然衰减。
     * <p>
     * 不在矿车上返回 {@code false}（调用方据此回落成"抽一个幸运核心事件"，见 {@code OrbOfLuckItem}）。
     */
    public static boolean boostMinecart(Player player) {
        if (!(player.getVehicle() instanceof AbstractMinecart cart)) {
            return false;
        }
        if (cart.level().isClientSide()) {
            return true;   // 客户端只认"这次右键算数"，真正加速在服务端
        }
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            // 正上/正下看：没有可用的水平朝向，退回矿车当前的行进方向
            Vec3 movement = cart.getDeltaMovement();
            horizontal = new Vec3(movement.x, 0.0D, movement.z);
            if (horizontal.lengthSqr() < 1.0E-6D) {
                return false;
            }
        }
        cart.setDeltaMovement(horizontal.normalize().scale(MINECART_BOOST_PER_TICK));
        cart.hurtMarked = true;   // 让客户端跟着这个速度
        cart.getPersistentData().putLong(TAG_JET_UNTIL, cart.level().getGameTime() + JET_TICKS);
        return true;
    }

    /**
     * 车尾喷射：每刻给开着喷射的矿车撒一把<b>随机颜色的尘粒子</b> + 一点烟花火星，
     * 方向是<b>车身后方</b>（按当前速度反向）。
     * <p>
     * 用尘粒子（{@code DustParticleOptions}）而不是纯 {@code FIREWORK}：原版烟花粒子是固定白色的
     * （{@code ParticleTypes.FIREWORK} 没有颜色参数），要"彩色"就得靠尘粒子自带颜色；
     * 两种混着发才有"烟花向后喷射"的观感。
     * <p>
     * 终止条件有两条：到点（{@link #JET_TICKS}）或车慢下来（{@link #JET_MIN_SPEED} 以下）——
     * 后者是为了不让粒子在车早就停下之后还空喷 5 秒。
     */
    @EventBusSubscriber(modid = ModMain.MODID)
    public static final class Jet {

        @SubscribeEvent
        public static void onEntityTick(EntityTickEvent.Post event) {
            if (!(event.getEntity() instanceof AbstractMinecart cart) || cart.level().isClientSide()) {
                return;
            }
            long until = cart.getPersistentData().getLong(TAG_JET_UNTIL);
            if (until == 0L) {
                return;
            }
            long now = cart.level().getGameTime();
            Vec3 movement = cart.getDeltaMovement();
            double speed = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
            if (now > until || speed < JET_MIN_SPEED) {
                cart.getPersistentData().remove(TAG_JET_UNTIL);
                return;
            }
            ServerLevel level = (ServerLevel) cart.level();
            RandomSource random = level.getRandom();
            // 车尾方向 = 速度反向（水平）
            double backX = -movement.x / speed;
            double backZ = -movement.z / speed;
            for (int i = 0; i < JET_PARTICLES_PER_TICK; i++) {
                // 1.21.1 的 DustParticleOptions 收的是 Vector3f（0~1 分量），不是打包成 int 的颜色
                org.joml.Vector3f color = new org.joml.Vector3f(
                    random.nextFloat(), random.nextFloat(), random.nextFloat());
                level.sendParticles(
                    new DustParticleOptions(color, 1.4F),
                    cart.getX(), cart.getY() + 0.3D, cart.getZ(),
                    1,                       // 每次一颗，颜色才能各不相同
                    backX * 1.6D, 0.25D, backZ * 1.6D,   // 扩散方向 = 向后
                    0.9D);                   // 速度
            }
            level.sendParticles(ParticleTypes.FIREWORK,
                cart.getX(), cart.getY() + 0.35D, cart.getZ(),
                6, backX * 1.2D, 0.2D, backZ * 1.2D, 0.35D);
        }
    }
}
