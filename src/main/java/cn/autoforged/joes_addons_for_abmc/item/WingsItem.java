package cn.autoforged.joes_addons_for_abmc.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * <b>wings（翅膀）</b>：一件"胸甲类"物品 —— 穿在<b>胸甲栏</b>里，外形按鞘翅（{@code ElytraModel}）渲染，
 * 但效果是<b>飞行能力（mayfly）</b>而不是滑翔。
 *
 * <ul>
 *   <li><b>穿戴方式</b>：继承 {@link ElytraItem} → 右键即可穿到胸甲栏、发射器也能装到胸甲栏
 *       （{@code Equipable#getEquipmentSlot() = CHEST}）、穿戴音效是鞘翅音；</li>
 *   <li><b>不提供滑翔</b>：{@link #canElytraFly} 恒为 false —— 飞行能力改由服务端每刻授予
 *       {@code abilities.mayfly}（见 {@code ModMain.handleWingsTick}），所以不需要也不应该再叠一个滑翔；</li>
 *   <li><b>耐久 {@value #DURABILITY}</b>：装备且处于生存/冒险模式、并且正在飞行时，
 *       每 3 秒（60 刻）扣 1 点；耐久只剩最后 1 点时视为"用坏了"（与鞘翅同样的
 *       {@link #isUsable} 规则）→ 不再给飞行能力，也就不会继续掉耐久；</li>
 *   <li><b>铁砧修理</b>：修理材料是<b>羽毛</b>；</li>
 *   <li>附魔与鞘翅一致（耐久 / 经验修补 / 消失诅咒，见 {@code enchantable/durability} 与
 *       {@code enchantable/vanishing} 标签）。属于幸运物品池。</li>
 * </ul>
 */
public class WingsItem extends ElytraItem {
    /** 耐久（需求：1024）。 */
    public static final int DURABILITY = 1024;

    public WingsItem(Properties properties) {
        super(properties);
    }

    /** 附魔台可附魔等级（14 = 铁质工具，与本模组其它物品一致；鞘翅本身是 0）。 */
    @Override
    public int getEnchantmentValue() {
        return 14;
    }

    /** 铁砧修理材料：羽毛。 */
    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.is(Items.FEATHER);
    }

    /** 本物品不提供鞘翅滑翔（飞行能力由 mayfly 提供，见 {@code ModMain.handleWingsTick}）。 */
    @Override
    public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        return false;
    }

    /**
     * 这件翅膀还能不能用：规则与鞘翅的 {@code isFlyEnabled} 相同 ——
     * 耐久只要还剩最后 1 点就算"用坏"，此时不再授予飞行能力（因此也不会被飞行耗到彻底消失）。
     */
    public static boolean isUsable(ItemStack stack) {
        return stack.getDamageValue() < stack.getMaxDamage() - 1;
    }
}
