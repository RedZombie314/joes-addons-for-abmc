package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * 幸运实体子事件：<b>女巫骑着恶魂</b>（恶魂骑士）。
 *
 * <p>生成一只<b>恶魂</b>，并在它背上放一只<b>女巫</b>（{@code startRiding(ghast, true)} 强制骑乘，
 * 不需要鞍）。之后两者各跑各的原版 AI：恶魂照常随机飘飞并发射火球，女巫坐在背上照样朝玩家扔药水。
 *
 * <h3>生成位置</h3>
 * <p>就从<b>被破坏的那颗幸运方块所在的位置</b>开始（不是往上抬 6 格），并且用
 * {@link LuckyEvents#findFreeY} 从这一格<b>由下往上</b>找第一个"恶魂碰撞箱不与方块相交"的高度：
 * <ul>
 *   <li>正常情况（方块摆在地表或空中，这一格就是空气）→ 直接取 <b>{@code pos.getY()}</b>，
 *       也就是恶魂的碰撞箱底面正好贴在<b>下方那块方块的上表面</b>上，与方块坐标一致；</li>
 *   <li>如果这一格不够放（比如方块被嵌在地板里、四周同层都是实心方块，或者上面空间不够 4 格高），
 *       就往上抬最少的那几格 —— 抬到的位置同样满足"碰撞箱底面贴着下方方块的上表面"，
 *       从而避免 4×4×4 的恶魂被塞进方块里<b>窒息</b>。</li>
 * </ul>
 *
 * <p>两者都设为<b>不被自然消失</b>（persistence required），否则这只"奖励怪"会自己刷掉。
 */
public final class LuckyWitchGhastEvent {
    private LuckyWitchGhastEvent() {
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("entity/witch_ghast"), LuckyEventCategory.LUCKY_ENTITY,
            "生成一只骑着恶魂的女巫",
            LuckyWitchGhastEvent::spawnWitchOnGhast);
        LuckyEvents.register(event);
        return event;
    }

    private static void spawnWitchOnGhast(ServerLevel level, BlockPos pos, @Nullable Player player) {
        Ghast ghast = EntityType.GHAST.create(level);
        if (ghast == null) return;

        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        // 从"幸运方块原本那一格"开始：正常情况就是这一格（碰撞箱底面贴住下方方块的上表面），
        // 只有这一格塞不下恶魂时才往上抬最少格数，避免窒息。
        double y = LuckyEvents.findFreeY(level, ghast, x, pos.getY(), z);

        ghast.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
        ghast.setPersistenceRequired();
        level.addFreshEntity(ghast);

        Witch witch = EntityType.WITCH.create(level);
        if (witch == null) {
            ghast.discard();
            return;
        }
        witch.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
        witch.setPersistenceRequired();
        level.addFreshEntity(witch);
        witch.startRiding(ghast, true); // 强制骑乘，不需要鞍
    }
}
