package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.block.entity.ModBlockEntities;
import cn.autoforged.joes_addons_for_abmc.block.entity.SupportLuckyBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * <b>支援幸运方块</b>（需求 6.5.21）：旁观操控下确认"送到某位玩家"之后，
 * 在那位玩家附近生成的就是这一种方块——里面装着刚送出的生物/物品，
 * <b>5 秒后自己碎裂</b>把内容爆出来。
 *
 * <h3>为什么单独做一种方块，而不是复用 {@link LuckyBlock}</h3>
 * <ul>
 *   <li>{@code LuckyBlock} 的语义是"玩家挖掉 → 抽一次幸运事件"，而这一种的内容是<b>预定好的</b>
 *       （就是刚 seek 到的那一只），碎裂时绝不能去抽事件、也不该碰幸运名单；</li>
 *   <li>内容要跟着方块存档（区块卸载/读档都不能丢），所以需要方块实体，而 {@code LuckyBlock} 没有；</li>
 *   <li>外观沿用幸运方块那张贴图（见 {@code assets/.../models/block/support_lucky_block.json}），
 *       一眼能看出是"幸运方块"，但不会和普通幸运方块的行为混淆。</li>
 * </ul>
 *
 * <p>方块本身是 {@code noLootTable}（见 {@code ModBlocks} 的注册）：碎裂不掉落自己，
 * 内容完全由 {@link SupportLuckyBlockEntity#tick} 决定。
 */
public class SupportLuckyBlock extends BaseEntityBlock {

    public static final MapCodec<SupportLuckyBlock> CODEC = simpleCodec(SupportLuckyBlock::new);

    public SupportLuckyBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** 要画成普通方块：{@code BaseEntityBlock} 默认是 {@code INVISIBLE}（那是给全自定义渲染的方块留的）。 */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SupportLuckyBlockEntity(pos, state);
    }

    /** 只有服务端需要 tick（客户端没有"5 秒后自己碎"这回事，靠方块更新包同步）。 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.SUPPORT_LUCKY_BLOCK_ENTITY.get(),
            SupportLuckyBlockEntity::tick);
    }
}
