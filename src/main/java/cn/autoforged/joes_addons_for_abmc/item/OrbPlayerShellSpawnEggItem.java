package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.entity.ModEntities;
import cn.autoforged.joes_addons_for_abmc.entity.PlayerShellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * 幸运核心玩家刷怪蛋：放下一具<b>被幸运核心附体的玩家空壳</b>。
 *
 * <h2>皮肤</h2>
 * 默认从 {@link #SKINS} 里<b>随机</b>取一个（允许重复）—— 名单是用户指定的十位玩家名。
 * 如果这颗蛋<b>被命名过</b>（铁砧改名、或 {@code /give} 带 {@code custom_name}/{@code item_name}），
 * 就直接用那个名字当皮肤（见 {@link #resolveSkinName}）。
 * 空壳的皮肤是按<b>玩家名</b>在客户端联网拉取的（见 {@code PlayerShellRenderer}），所以这里只存名字。
 *
 * <h2>生成出来的是什么</h2>
 * 和"被同化产生的那一具"完全同款（共用 {@link PlayerShellEntity#configureAsPossessedShell()}）：
 * 头顶挂着幸运核心光球、20 血 + 20 护甲 + 12 护甲韧性 + 保护 16 状态效果、靠附魔金苹果回血、
 * 骷髅式走位 + 抽签攻击、不自然消失。
 * <p>
 * 区别只在于它<b>没有</b>背后的玩家，也没有 {@code OrbAssimilation} 的同化关系：
 * 打死它就只是打死它（不会"复活"任何人）。
 *
 * <p>继承 {@code DeferredSpawnEggItem} 是为了拿到原版刷怪蛋的贴图与双色染色
 * （颜色由构造参数给，客户端由原版统一为 {@code SpawnEggItem} 注册 tint），
 * 但生成逻辑整个换掉：原版只会照实体类型放一具"白板"空壳。
 */
public class OrbPlayerShellSpawnEggItem extends net.neoforged.neoforge.common.DeferredSpawnEggItem {

    /** 可随机到的皮肤名（允许重复选取；客户端按名字联网拉皮肤）。 */
    private static final String[] SKINS = {
        "Dream",
        "Technoblade",
        "Sapnap",
        "GeorgeNotFound",
        "Red_Zombie",
        "Herobrine",
        "cubicmetre",
        "BionicLMAO",
        "SunnySeren",
        "Wemmbu",
    };

    /** 蛋的底色：核心的紫。 */
    private static final int BACKGROUND_COLOR = 0x6B3FA0;

    /** 蛋的斑点色：肤色。 */
    private static final int HIGHLIGHT_COLOR = 0xE8C39E;

    public OrbPlayerShellSpawnEggItem(Properties properties) {
        super(ModEntities.PLAYER_SHELL, BACKGROUND_COLOR, HIGHLIGHT_COLOR, properties);
    }

    /**
     * 决定这一颗蛋召唤出来的空壳用谁的皮肤：
     * <ol>
     *   <li><b>蛋被命名过</b>（铁砧改名 / {@code /give} 带 {@code custom_name} 或 {@code item_name}）
     *       → 直接拿那个名字当玩家名；</li>
     *   <li>否则从 {@link #SKINS} 里随机取一个（允许重复）。</li>
     * </ol>
     * 名字会被 {@code trim()}；命名成空白则视同没命名，退回随机。
     * <p>
     * 注意必须用 {@code has(CUSTOM_NAME)} 判断"有没有被命名"：没命名时 {@code getHoverName()}
     * 会返回物品的本地化名字（"幸运核心玩家刷怪蛋"），拿它当玩家名会去拉一个不存在的皮肤。
     */
    private static String resolveSkinName(ItemStack stack, RandomSource random) {
        net.minecraft.network.chat.Component custom = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (custom == null) {
            // 1.21 起 /give 也可以只写 item_name（物品名组件），一并认下
            custom = stack.get(net.minecraft.core.component.DataComponents.ITEM_NAME);
        }
        if (custom != null) {
            String name = custom.getString().trim();
            if (!name.isEmpty()) {
                return name;
            }
        }
        return SKINS[random.nextInt(SKINS.length)];
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            // 客户端：视作成功以保持挥动动画，实际生成在服务端执行
            return InteractionResult.SUCCESS;
        }

        // 落点与刷怪蛋一致：方块没有碰撞箱就放在这一格，否则放到命中面的相邻格
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState state = level.getBlockState(clicked);
        BlockPos spawnBlock = state.getCollisionShape(level, clicked).isEmpty()
            ? clicked : clicked.relative(face);

        RandomSource random = level.getRandom();
        PlayerShellEntity shell = new PlayerShellEntity(ModEntities.PLAYER_SHELL.get(), level);
        shell.setSkinTexture(resolveSkinName(context.getItemInHand(), random));
        shell.moveTo(spawnBlock.getX() + 0.5D, spawnBlock.getY(), spawnBlock.getZ() + 0.5D,
            random.nextFloat() * 360.0F, 0.0F);
        // 和"被同化产生的那一具"共用同一套配置：20 护甲 / 12 韧性 / 保护 16、附魔金苹果回血、
        // 走位与抽签攻击、头顶光球；<b>生命上限走默认那档 20</b>（刷怪蛋没有"被附体/同化的玩家"，
        // 所以给不了"按那位玩家的上限来"；要按放蛋的人算的话就传 context.getPlayer().getMaxHealth()）
        shell.configureAsPossessedShell();
        level.addFreshEntity(shell);
        level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, spawnBlock);

        Vec3 soundPos = Vec3.atBottomCenterOf(spawnBlock);
        serverLevel.playSound(null, soundPos.x, soundPos.y, soundPos.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.7F, 1.4F);

        ItemStack stack = context.getItemInHand();
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(false);
    }
}
