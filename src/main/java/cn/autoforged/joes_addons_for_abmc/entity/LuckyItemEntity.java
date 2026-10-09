package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.stats.Stats;

/**
 * 幸运维度里<b>自然生成</b>的掉落物实体。
 *
 * <p>它是一个普通的 {@link ItemEntity}（所以掉地上会被重力拉下去、会被岩浆烧掉、会在
 * {@link ItemEntity#LIFETIME} 即 <b>6000 刻 = 5 分钟</b>后自然消失，这些全是原版行为，
 * 生命周期还会随 {@code Lifespan}/{@code Age} 一起存档），只改了「怎么被玩家拿走」这一件事：
 *
 * <ul>
 *   <li><b>走过去捡不起来</b>：生成时调 {@code setNeverPickUp()}（把 {@code pickupDelay} 设成 32767），
 *       而且这个值本身就会存档（ItemEntity.java:322 与 :344-346）。这里再覆写
 *       {@link #playerTouch(Player)} 成空方法作为硬保证 —— 万一有别的代码把它改回 0，
 *       走路依然捡不走。顺带一提，原版 {@code ItemEntity#isMergable()} 会把
 *       {@code pickupDelay == 32767} 的物品排除，所以它们之间也不会互相合并。</li>
 *   <li><b>右键拿走</b>：覆写 {@link #interact(Player, InteractionHand)}。右键实体这条路是原版现成的——
 *       客户端命中实体后发 {@code ServerboundInteractPacket}，服务端在
 *       {@code ServerGamePacketListenerImpl#handleInteract} 里落到 {@code Player#interactOn}
 *       （ServerGamePacketListenerImpl.java:1618 → Player.java:1098 调 {@code entity.interact(...)}）。</li>
 *   <li><b>能被准星选中</b>：必须覆写 {@link #isPickable()} 返回 true。原版
 *       {@code Entity#isPickable()} 默认就是 false（Entity.java:1667），而客户端挑实体时用它过滤
 *       （GameRenderer.java:829），所以不覆写的话右键永远不会触发。
 *       代价是 {@code canBeHitByProjectile()} 也吃这个开关（Entity.java:1663），
 *       于是同时覆写它返回 false，免得箭矢把物品打坏。</li>
 * </ul>
 */
public class LuckyItemEntity extends ItemEntity {

    public LuckyItemEntity(EntityType<? extends LuckyItemEntity> entityType, Level level) {
        super(entityType, level);
    }

    /** 自然生成时的标记（由 {@link LuckyItemSpawner} 调用，与选择器的同名方法各有各的语义）。 */
    public void markNaturalSpawn() {
        this.setNeverPickUp();
    }

    /** 让准星能选中它（原版掉落物选不中）。见类注释。 */
    @Override
    public boolean isPickable() {
        return true;
    }

    /** 别因为上一条而变成箭矢/弹射物的靶子。 */
    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    /** 走路踩上去不会有任何事（硬保证，见类注释）。 */
    @Override
    public void playerTouch(Player player) {
        // 故意留空：本实体只能靠右键拿走
    }

    /**
     * 右键：把这一件放进玩家物品栏；<b>放不下就什么都不做</b>。
     *
     * <p>「放不下」的判定刻意与 {@code Inventory#add} 的落位逻辑一致：
     * 它只会找<b>能继续堆叠的已有槽</b>（{@code getSlotWithRemainingSpace}，先看选中槽、再看副手、再遍历 36 格）
     * 或者<b>空槽</b>（{@code getFreeSlot}，只看 36 格主背包）（Inventory.java:180-226），
     * 所以「两者都返回 -1」就等于「放不下」。带耐久损耗的物品原版还额外要求空槽（Inventory.java:257-271），
     * 这里也照做。
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = this.getItem();
        if (!this.isAlive() || stack.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!canFullyAccept(player, stack)) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide()) {
            return InteractionResult.sidedSuccess(true);
        }

        // 前面已经判过容量，正常一定放得下；万一还是剩下（例如被别的代码塞满），
        // 就把剩下的掉在玩家脚边，绝不让物品凭空消失。
        ItemStack toAdd = stack.copy();
        if (!player.getInventory().add(toAdd) && !toAdd.isEmpty()) {
            player.drop(toAdd, false);
        }
        player.awardStat(Stats.ITEM_PICKED_UP.get(stack.getItem()), stack.getCount());
        player.onItemPickup(this);
        this.discard();
        return InteractionResult.sidedSuccess(false);
    }

    /** 玩家物品栏是否放得下这一整叠。 */
    private static boolean canFullyAccept(Player player, ItemStack stack) {
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        if (inventory.getFreeSlot() != -1) {
            return true;
        }
        // 带耐久损耗的（例如工具）必须占一个空槽，不能并进已有堆叠
        if (stack.isDamaged()) {
            return false;
        }
        return inventory.getSlotWithRemainingSpace(stack) != -1;
    }
}
