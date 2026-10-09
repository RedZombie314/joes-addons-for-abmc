package cn.autoforged.joes_addons_for_abmc.block.entity;

import cn.autoforged.joes_addons_for_abmc.block.SupportGift;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * <b>支援幸运方块</b>的内容物：里面装着"操控右键送出"的那一只生物或那一件物品，
 * 出现 {@link #LIFETIME_TICKS} 刻（需求：5 秒）之后自己碎裂、把内容爆出来。
 *
 * <h3>为什么用方块实体装内容</h3>
 * 内容必须跟着方块走：区块卸载、退出游戏、切维度都得还在（方块实体连同它的 NBT 一起存档），
 * 而"5 秒后自己碎"这件事也要能跨读档继续（{@link #age} 一起存）。
 * 用一张静态表记"哪个坐标里装着什么"在读档/卸载时会全丢，所以不采用。
 *
 * <h3>为什么自爆不等同于"玩家挖掉幸运方块"</h3>
 * 这是<b>预定内容的容器</b>，不是普通幸运方块：碎裂直接调用
 * {@link Level#destroyBlock(BlockPos, boolean)}（不掉落自己、不触发幸运事件），
 * 内容由 {@link #popAndBreak} 按存下来的那一份原样放出。所以它既不会被 {@code LuckyBlock} 那套
 * "玩家破坏 → 抽幸运事件"接走，也不会污染幸运名单。
 */
public class SupportLuckyBlockEntity extends BlockEntity {

    /** 出现多久之后自己碎裂（刻）：需求给的 5 秒。 */
    public static final int LIFETIME_TICKS = 100;

    private static final String TAG_AGE = "Age";
    private static final String TAG_MOB = "Mob";
    private static final String TAG_ITEM = "Item";

    /** 已经存在了多少刻（存档，读档后接着数）。 */
    private int age;

    /** 内容之一：生物的完整 NBT（与物品互斥）。 */
    @Nullable
    private CompoundTag mobTag;

    /** 内容之二：物品（与生物互斥）。 */
    private ItemStack stack = ItemStack.EMPTY;

    public SupportLuckyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUPPORT_LUCKY_BLOCK_ENTITY.get(), pos, state);
    }

    /** 生成时调用：把要送的东西装进来（生物 NBT 与物品二选一，另一个传 null / EMPTY）。 */
    public void setContent(@Nullable CompoundTag mob, ItemStack item) {
        this.mobTag = mob;
        this.stack = item == null ? ItemStack.EMPTY : item;
        this.setChanged();
    }

    /** 方块实体的每刻 tick（见 {@code SupportLuckyBlock#getTicker}，只有服务端会拿到）。 */
    public static void tick(Level level, BlockPos pos, BlockState state, SupportLuckyBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (++be.age < LIFETIME_TICKS) {
            return;
        }
        be.popAndBreak(serverLevel, pos);
    }

    /** 到点了：先把内容放出来，再把方块自己碎掉（内容因此正好出现在方块原来的位置）。 */
    private void popAndBreak(ServerLevel level, BlockPos pos) {
        CompoundTag mob = this.mobTag;
        ItemStack item = this.stack;
        this.mobTag = null;
        this.stack = ItemStack.EMPTY;
        if (mob != null) {
            SupportGift.spawnStoredMob(level, pos, mob);
        } else if (!item.isEmpty()) {
            Block.popResource(level, pos, item);
        }
        // 不掉落自己：方块本身是 noLootTable，这里也不传 false 之外的参数
        level.destroyBlock(pos, false);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_AGE, this.age);
        if (this.mobTag != null) {
            tag.put(TAG_MOB, this.mobTag);
        }
        if (!this.stack.isEmpty()) {
            tag.put(TAG_ITEM, this.stack.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.age = tag.getInt(TAG_AGE);
        this.mobTag = tag.contains(TAG_MOB, Tag.TAG_COMPOUND) ? tag.getCompound(TAG_MOB) : null;
        this.stack = tag.contains(TAG_ITEM, Tag.TAG_COMPOUND)
            ? ItemStack.parseOptional(registries, tag.getCompound(TAG_ITEM))
            : ItemStack.EMPTY;
    }
}
