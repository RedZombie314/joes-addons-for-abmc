package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import javax.annotation.Nullable;

/**
 * 幸运实体子事件：<b>生成僵尸 "Bob"</b>。
 *
 * <ul>
 *   <li>全身<b>钻石甲</b>，每件都带 <b>保护 IV / 耐久 III / 荆棘 III</b>；</li>
 *   <li>主手<b>钻石剑</b>，带 <b>击退 II / 锋利 V / 耐久 III</b>；</li>
 *   <li>命名为 <b>Bob</b>（自定义名，玩家看着它时会显示）；</li>
 *   <li><b>20%</b> 概率生成<b>小僵尸</b>变种（小僵尸移动更快、白天不燃烧）。</li>
 * </ul>
 *
 * <p>附魔一律通过服务器的附魔注册表解析（{@code level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)}），
 * 因此数据包对附魔的改动同样生效；也避免了 1.21 之后 {@code Enchantments} 常量不可直接当 holder 用的问题。
 * 这样生成的 Bob 设为<b>不被自然消失</b>（persistence required），否则一件"奖励怪"会自己刷掉。
 */
public final class LuckyBobEvent {
    /** 生成小僵尸的概率。 */
    private static final float BABY_CHANCE = 0.2F;

    private LuckyBobEvent() {
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("entity/zombie_bob"), LuckyEventCategory.LUCKY_ENTITY,
            "生成全身保护4/耐久3/荆棘3钻石甲、手持击退2锋利5耐久3钻石剑的僵尸 Bob（20% 为小僵尸）",
            LuckyBobEvent::spawnBob);
        LuckyEvents.register(event);
        return event;
    }

    private static void spawnBob(ServerLevel level, BlockPos pos, @Nullable Player player) {
        Zombie bob = createBob(level);
        if (bob == null) return;

        bob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
            level.random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(bob);
    }

    /**
     * 造一只（还没放进世界的）Bob：全套附魔钻石甲 + 击退2锋利5钻石剑、命名 Bob、20% 小僵尸、
     * 设为不被自然消失。
     * <p>
     * 公开出来是给"幸运核心附体"的召唤技能复用的（{@code OrbPossessedAttackEvents} 的其它攻击），
     * 这样两条路径的 Bob 永远是同一套配置，不会各写一份走偏。
     */
    @Nullable
    public static Zombie createBob(ServerLevel level) {
        Zombie bob = EntityType.ZOMBIE.create(level);
        if (bob == null) return null;

        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        // 全套钻石甲：保护 IV / 耐久 III / 荆棘 III
        bob.setItemSlot(EquipmentSlot.HEAD, armor(enchantments, Items.DIAMOND_HELMET));
        bob.setItemSlot(EquipmentSlot.CHEST, armor(enchantments, Items.DIAMOND_CHESTPLATE));
        bob.setItemSlot(EquipmentSlot.LEGS, armor(enchantments, Items.DIAMOND_LEGGINGS));
        bob.setItemSlot(EquipmentSlot.FEET, armor(enchantments, Items.DIAMOND_BOOTS));

        // 主手钻石剑：击退 II / 锋利 V / 耐久 III
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(enchantments.getOrThrow(Enchantments.KNOCKBACK), 2);
        sword.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 5);
        sword.enchant(enchantments.getOrThrow(Enchantments.UNBREAKING), 3);
        bob.setItemSlot(EquipmentSlot.MAINHAND, sword);

        // 命名 + 20% 小僵尸变种
        bob.setCustomName(Component.literal("Bob"));
        bob.setBaby(level.random.nextFloat() < BABY_CHANCE);
        bob.setPersistenceRequired(); // 奖励怪/召唤物不应自然消失
        return bob;
    }

    /** 一件带 保护 IV / 耐久 III / 荆棘 III 的钻石甲。 */
    private static ItemStack armor(net.minecraft.core.HolderLookup.RegistryLookup<Enchantment> enchantments,
                                   net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(enchantments.getOrThrow(Enchantments.PROTECTION), 4);
        stack.enchant(enchantments.getOrThrow(Enchantments.UNBREAKING), 3);
        stack.enchant(enchantments.getOrThrow(Enchantments.THORNS), 3);
        return stack;
    }
}
