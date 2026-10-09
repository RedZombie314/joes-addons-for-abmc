package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.entity.PlayerShellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <b>wart on a stick（地狱疣钓竿）</b>：一根插着地狱疣的棍子。
 *
 * <ul>
 *   <li>耐久 {@value #DURABILITY}（每次成功使用扣 1 点）；可附魔等级 14（= 铁质工具），
 *       配合 {@code #minecraft:enchantable/durability} 与 {@code #minecraft:enchantable/vanishing}
 *       标签，可以附上<b>经验修补 / 耐久 / 消失诅咒</b>（原版这三个附魔分别由这两个标签决定适用范围）；</li>
 *   <li><b>右击方块</b>：若该方块<b>上表面完整</b>（顶面碰撞形状是整格），且它<b>上方一格是空气/草/蕨</b>，
 *       就在那一格放置一个 <b>age = 3 的地狱疣</b>（即成熟状态）；</li>
 *   <li><b>右击生物</b>：若它的<b>头盔栏为空</b>，就往头盔栏里塞一个地狱疣物品。之后只要这个地狱疣
 *       还在头盔栏里，它就会一直被施加<b>缓慢 I + 失明 + 挖掘疲劳 I</b>
 *       （每 {@value #HEAD_EFFECT_INTERVAL_TICKS} 刻刷新一次、每次持续 {@value #HEAD_EFFECT_DURATION_TICKS} 刻 = 5 秒，
 *       所以摘下地狱疣后最多 5 秒效果就消失）—— 见 {@link #tickHeadWarts(MinecraftServer)}；</li>
 *   <li><b>右击"被附体的玩家空壳"是例外</b>：地狱疣只留一秒，而且那一秒里空壳会失去移动 AI
 *       （用户指定，见 {@link #wartTrapOnShell}）—— 对空壳用这一招是白费力气；
 *       若它的<b>头盔栏已经戴着别的东西</b>，则这一下<b>完全不生效</b>：不挂地狱疣、不僵住 AI、不扣耐久。</li>
 *   <li>头盔栏里挂着地狱疣时，第一人称视角会被地狱疣贴图铺满（客户端 {@code WartHelmetOverlayMixin}，
 *       用摄像机实体判断，所以在旁观模式下旁观一只"戴着地狱疣"的生物同样会看到）。</li>
 * </ul>
 */
public class WartOnAStickItem extends Item {
    /** 耐久（需求：256）。 */
    public static final int DURABILITY = 256;
    /** 头盔槽效果刷新间隔：1 秒。 */
    public static final int HEAD_EFFECT_INTERVAL_TICKS = 20;
    /** 头盔槽效果持续时间：5 秒（每秒刷新，所以"疣一直在就一直有"）。 */
    public static final int HEAD_EFFECT_DURATION_TICKS = 100;

    public WartOnAStickItem(Properties properties) {
        super(properties);
    }

    /**
     * 附魔台可附魔等级（14 = 铁质工具，与烈焰弹发射器保持一致）。
     * 具体能附到哪些附魔由物品标签决定：{@code enchantable/durability} → 耐久 + 经验修补，
     * {@code enchantable/vanishing} → 消失诅咒。
     */
    @Override
    public int getEnchantmentValue() {
        return 14;
    }

    // ------------------------------------------------------------------
    // 右击方块：在"上表面完整的方块"上方种一个成熟地狱疣
    // ------------------------------------------------------------------
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState support = level.getBlockState(pos);

        // 1) 被右击的方块上表面必须完整（顶面碰撞形状为整格，比如石头/泥土/木板；台阶、耕地边缘不算）
        if (!Block.isFaceFull(support.getCollisionShape(level, pos), Direction.UP)) {
            return InteractionResult.PASS;
        }

        // 2) 上方一格必须是空气 / 草 / 蕨
        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        if (!aboveState.isAir() && !aboveState.is(Blocks.SHORT_GRASS) && !aboveState.is(Blocks.FERN)) {
            return InteractionResult.PASS;
        }

        if (level instanceof ServerLevel serverLevel) {
            // 用 flags = 2 | 16（UPDATE_CLIENTS | UPDATE_SUPPRESS_DROPS）放置：
            //   * 2  → 正常同步给客户端，玩家立刻能看到；
            //   * 16 → 这次放置本身不做邻居更新（同时让被替换掉的草/蕨不掉落物品）。
            // 之后"不再被任何方向的方块更新弹掉"由 mixin NetherWartPersistenceMixin 保证：
            // 只要底座还有完整上表面，BushBlock#updateShape 就不会把疣清掉（包括旁边放/挖方块的情况）；
            // 底座被破坏时才正常弹掉。
            serverLevel.setBlock(above,
                Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, NetherWartBlock.MAX_AGE),
                Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            serverLevel.playSound(null, above, SoundEvents.NETHER_WART_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
            Player player = context.getPlayer();
            if (player != null) {
                context.getItemInHand().hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ------------------------------------------------------------------
    // 右击生物：把地狱疣塞进空头盔栏
    // ------------------------------------------------------------------
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        // 被幸运核心/幸运方块附体的玩家空壳：走另一条路 —— 它只"僵一秒"，然后自己把地狱疣甩掉。
        // <b>头盔栏里已经有别的东西时完全不生效</b>（用户指定）：不挂地狱疣、不僵住 AI、也不扣耐久。
        if (target instanceof PlayerShellEntity shell && shell.isOrbAttached()) {
            if (!shell.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                return InteractionResult.PASS;
            }
            return wartTrapOnShell(shell, stack, player, hand);
        }
        // 头盔栏不为空（已经戴着东西）就不生效
        if (!target.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (target.level() instanceof ServerLevel serverLevel) {
            target.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHER_WART));
            serverLevel.playSound(null, target.blockPosition(), SoundEvents.NETHER_WART_PLANTED,
                SoundSource.PLAYERS, 1.0F, 1.0F);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            applyHeadWartEffects(target); // 立刻生效一次，不用等下一次刷新
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    /**
     * 对被附体空壳使用"地狱疣生成器"：<b>只僵一秒，一秒后地狱疣自动脱落</b>。
     *
     * <p>用户指定："如果玩家尝试对被幸运方块附体的玩家空壳使用'地狱疣生成器'，
     * 则该玩家空壳会在接下来的一秒内失去移动 AI，一秒结束后移除其头盔栏的地狱疣。"
     * 之后又补充："如果头盔栏有别的物品，则玩家对其使用地狱疣生成器不会生效，也不会僵住其 AI。"
     *
     * <p>于是整条流程是（调用方已经确认<b>头盔栏是空的</b>）：
     * <ol>
     *   <li>把地狱疣挂到头盔栏，效果立刻生效一次；</li>
     *   <li>让空壳<b>失去移动 AI</b> {@link #SHELL_WART_MOVE_LOCK_TICKS} 刻 —— 用户说的是"玩家<b>尝试</b>使用"这件事本身；</li>
     *   <li>记下"一秒后把头盔栏的地狱疣摘掉"（{@link PlayerShellEntity#applyWartTrap}）。</li>
     * </ol>
     * 结果是玩家以为能靠地狱疣持续削弱它，实际只换来一秒的停顿 —— 这一招对空壳是无效的。
     */
    private static InteractionResult wartTrapOnShell(PlayerShellEntity shell, ItemStack stack,
                                                     Player player, InteractionHand hand) {
        if (shell.level() instanceof ServerLevel serverLevel) {
            shell.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHER_WART));
            applyHeadWartEffects(shell); // 立刻生效一次，不用等下一次刷新
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            serverLevel.playSound(null, shell.blockPosition(), SoundEvents.NETHER_WART_PLANTED,
                SoundSource.PLAYERS, 1.0F, 1.0F);
            // 失去移动 AI 一秒；到点由 PlayerShellEntity#tickWartStrip 摘掉头盔栏的地狱疣
            shell.applyWartTrap(SHELL_WART_MOVE_LOCK_TICKS);
            cn.autoforged.joes_addons_for_abmc.ModMain.LOGGER.info(
                "[附体] 地狱疣生成器命中空壳: 空壳=#{} 失去移动AI {} 刻，随后自动脱落",
                shell.getId(), SHELL_WART_MOVE_LOCK_TICKS);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    /** 对附体空壳使用地狱疣生成器时，"失去移动 AI"的时长（刻）：1 秒（用户指定）。 */
    public static final int SHELL_WART_MOVE_LOCK_TICKS = 20;

    // ------------------------------------------------------------------
    // 服务端：维持"头盔栏里有地狱疣"的生物身上的三种效果
    // ------------------------------------------------------------------

    /**
     * 由服务端 tick 钩子（{@code ModMain.onServerTickPre}）每刻调用，但只在
     * {@value #HEAD_EFFECT_INTERVAL_TICKS} 刻的整数倍真正干活（= 每秒一次）。
     *
     * <p>判定条件只有一条：<b>头盔栏里放着地狱疣</b> —— 所以无论是本物品塞进去的、还是用命令/发射器
     * 放进去的，都一样生效；一旦地狱疣被拿走，效果会在最多 5 秒后自然过期。
     */
    public static void tickHeadWarts(MinecraftServer server) {
        if (server.getTickCount() % HEAD_EFFECT_INTERVAL_TICKS != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living
                    && living.isAlive()
                    && living.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHER_WART)) {
                    applyHeadWartEffects(living);
                }
            }
        }
    }

    /** 缓慢 I + 失明 + 挖掘疲劳 I：时长 5 秒、无粒子（图标仍会显示）。 */
    public static void applyHeadWartEffects(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
            HEAD_EFFECT_DURATION_TICKS, 0, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
            HEAD_EFFECT_DURATION_TICKS, 0, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,
            HEAD_EFFECT_DURATION_TICKS, 0, false, false));
    }
}
