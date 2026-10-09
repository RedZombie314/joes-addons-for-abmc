package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import javax.annotation.Nullable;

/**
 * 幸运方块：完整方块，硬度与挖掘速度同干草块（{@code strength(0.5F)}），适合用镐挖（{@code mineable/pickaxe}）。
 *
 * <p><b>挖掘后永远不会掉落任何东西</b>，只触发一次幸运事件（见 {@link LuckyBlockEvents}）。
 * 因此这里刻意不调用 {@code super.playerDestroy}（那正是原版生成掉落物的入口），
 * 方块本身也设了 {@code noLootTable()}，保证爆炸/活塞等其它破坏方式同样不掉落。
 *
 * <h3>触发时机</h3>
 * 幸运事件挂在 {@link #onDestroyedByPlayer}（原版 {@code ServerPlayerGameMode.removeBlock} →
 * {@code BlockState.onDestroyedByPlayer}），<b>创造模式与生存模式都会走到</b> —— 而原版
 * {@code playerDestroy} 在创造模式下不会被调用，所以不能只挂在那里（否则创造模式挖掉方块毫无反应）。
 * 爆炸等非玩家破坏则走 {@link #wasExploded}（没有挖掘者，传入 {@code null}）。
 */
public class LuckyBlock extends Block {
    public LuckyBlock(Properties properties) {
        super(properties);
    }

    /**
     * 玩家破坏（生存 / 创造通用）：<b>幸运事件唯一的玩家触发点</b>。
     * <p>先让原版完成方块移除（{@code super}），成功后再触发事件：
     * 这样事件（例如刷猫、抛洒物品）执行时方块已经消失，不会与移除流程互相干扰。
     * <p><b>创造模式仅在 {@code DEBUG_MODE} 为 true 时触发</b>（方便调试；生存模式始终触发）。
     */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        boolean removed = super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
        if (!removed || !(level instanceof ServerLevel serverLevel)) {
            return removed;
        }
        // 解锁配方与"创造模式是否触发事件"无关：玩家破坏过就解锁（授予本身幂等）
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            LuckyBlockEvents.awardLuckyBlockRecipe(serverPlayer);
        }
        boolean creativeWithoutDebug = player.isCreative()
            && !cn.autoforged.joes_addons_for_abmc.config.ModConfig.DEBUG_MODE.get();
        if (!creativeWithoutDebug) {
            LuckyBlockEvents.trigger(serverLevel, pos, player);
        }
        return removed;
    }

    /** 不调用 {@code super}，避免走 {@code dropResources} 生成掉落物（事件已在上面的方法里触发）。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                             @Nullable BlockEntity blockEntity, ItemStack tool) {
        // 故意留空：幸运方块不掉落任何物品
    }

    /**
     * 非玩家破坏（例如爆炸）同样触发一次幸运事件，但没有挖掘者 → 传入 {@code null}。
     * <p>子事件据此区分"玩家破坏"与"非玩家破坏"（例如幸运猫事件只在玩家破坏时驯服）。
     */
    @Override
    public void wasExploded(Level level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        if (level instanceof ServerLevel serverLevel) {
            LuckyBlockEvents.trigger(serverLevel, pos, null);
        }
    }
}
