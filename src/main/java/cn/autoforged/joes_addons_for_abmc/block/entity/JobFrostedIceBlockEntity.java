package cn.autoforged.joes_addons_for_abmc.block.entity;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import org.jetbrains.annotations.Nullable;

/**
 * 冰块权杖生成的“霜冰”方块对应的方块实体。
 * 用于保存被替换原方块的状态（保留各数据，含容器内容）、
 * 被困住的生物 UUID，以及是否已被消费（融化/破坏还原过）。
 */
public class JobFrostedIceBlockEntity extends BlockEntity {
    private BlockState originalState = Blocks.AIR.defaultBlockState();
    @Nullable
    private CompoundTag originalBlockEntityData;
    @Nullable
    private UUID trappedEntity;
    private boolean consumed;

    public JobFrostedIceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.JOB_FROSTED_ICE_BLOCK_ENTITY.get(), pos, state);
    }

    public void setOriginal(BlockState state, @Nullable CompoundTag blockEntityData, @Nullable UUID trapped) {
        this.originalState = state;
        this.originalBlockEntityData = blockEntityData;
        this.trappedEntity = trapped;
        this.consumed = false;
        setChanged();
    }

    public boolean isConsumed() {
        return consumed;
    }

    /** 尚未被还原，且确实存有原方块信息（任一非空即视为有数据）。 */
    public boolean hasStoredOriginal() {
        return !consumed && (!originalState.isAir() || originalBlockEntityData != null || trappedEntity != null);
    }

    public BlockState getOriginalState() {
        return originalState;
    }

    @Nullable
    public CompoundTag getOriginalBlockEntityData() {
        return originalBlockEntityData;
    }

    @Nullable
    public UUID getTrappedEntity() {
        return trappedEntity;
    }

    /** 标记已消费，避免还原/伤害结算被重复触发。 */
    public void markConsumed() {
        consumed = true;
        originalState = Blocks.AIR.defaultBlockState();
        originalBlockEntityData = null;
        trappedEntity = null;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("consumed", consumed);
        if (consumed) return;
        tag.putString("origState", BlockStateParser.serialize(originalState));
        if (originalBlockEntityData != null) {
            tag.put("origData", originalBlockEntityData);
        }
        if (trappedEntity != null) {
            tag.putUUID("trapped", trappedEntity);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        consumed = tag.getBoolean("consumed");
        if (consumed) return;
        if (tag.contains("origState")) {
            try {
                originalState = BlockStateParser.parseForBlock(
                    registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
                    tag.getString("origState"), false).blockState();
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) {
                originalState = Blocks.AIR.defaultBlockState();
            }
        }
        if (tag.contains("origData")) {
            originalBlockEntityData = tag.getCompound("origData");
        }
        if (tag.hasUUID("trapped")) {
            trappedEntity = tag.getUUID("trapped");
        }
    }
}