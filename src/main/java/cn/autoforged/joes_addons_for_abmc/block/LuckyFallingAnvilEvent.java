package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;

/**
 * 幸运实体子事件：<b>在玩家头上 10 格处召唤一个下落的铁砧</b>（需求 9）。
 *
 * <ul>
 *   <li>玩家破坏时：落点在<b>该玩家头顶上方 {@value #HEIGHT} 格</b>（以玩家碰撞箱顶面起算）；</li>
 *   <li>非玩家触发（爆炸、幸运维度自然生成等）：没有"玩家"，落点改为幸运方块正上方 {@value #HEIGHT} 格；</li>
 *   <li>铁砧是原版下落方块，所以会有原版的落地音效、落地后可能"受损/开裂"、以及<b>砸伤下方实体</b>——
 *       砸伤靠的是 {@code setHurtsEntities(2, 40)}，这正是原版 {@code AnvilBlock#falling} 给铁砧设的参数
 *       （{@code FallingBlockEntity#fall} 自己<b>不会</b>设，不设的话掉下来是不疼的）。</li>
 * </ul>
 */
public final class LuckyFallingAnvilEvent {

    /** 落点高度（玩家头顶/方块上方多少格）。 */
    public static final int HEIGHT = 10;

    /** 原版铁砧的砸伤参数（与 {@code AnvilBlock#falling} 一致）。 */
    private static final float FALL_DAMAGE_PER_DISTANCE = 2.0F;
    private static final int FALL_DAMAGE_MAX = 40;

    private LuckyFallingAnvilEvent() {
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("entity/falling_anvil"), LuckyEventCategory.LUCKY_ENTITY,
            "在玩家头顶 " + HEIGHT + " 格处召唤一个下落的铁砧",
            LuckyFallingAnvilEvent::dropAnvil);
        LuckyEvents.register(event);
        return event;
    }

    private static void dropAnvil(ServerLevel level, BlockPos pos, @Nullable Player player) {
        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        double y = pos.getY() + HEIGHT;
        if (player != null) {
            x = player.getX();
            z = player.getZ();
            y = player.getY() + player.getBbHeight() + HEIGHT;
        }
        FallingBlockEntity anvil = FallingBlockEntity.fall(level, BlockPos.containing(x, y, z),
            Blocks.ANVIL.defaultBlockState());
        anvil.setHurtsEntities(FALL_DAMAGE_PER_DISTANCE, FALL_DAMAGE_MAX);
    }
}
