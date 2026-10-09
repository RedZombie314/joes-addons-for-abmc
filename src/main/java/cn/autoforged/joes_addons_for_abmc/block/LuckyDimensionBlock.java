package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.block.entity.LuckyDimensionBlockEntity;
import cn.autoforged.joes_addons_for_abmc.block.entity.ModBlockEntities;
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

public class LuckyDimensionBlock extends BaseEntityBlock {
    public static final MapCodec<LuckyDimensionBlock> CODEC = simpleCodec(LuckyDimensionBlock::new);

    public LuckyDimensionBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // 故意让它"不渲染自身模型"：外观完全由客户端负责
        //（幸运维度内由区块网格 LuckyDimensionSectionRenderer 画伪装材质/滤镜，维度外由 BER 画）。
        // 这样就不存在"伪装材质 vs 方块自身模型"的共面，远处（一两百格）也不会 z-fighting。
        // 关闭随机贴图配置时，两侧都会改画方块自身的模型，所以方块不会隐形。
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LuckyDimensionBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.LUCKY_DIMENSION_BLOCK_ENTITY.get(), LuckyDimensionBlockEntity::tick);
    }
}
