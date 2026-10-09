package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.entity.ThrownGlisteringMelonKnife;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * 闪烁西瓜刀。
 * <p>白板攻击力与下界合金斧一致（{@link #BASE_ATTACK_DAMAGE} = 10），近战完全走原版结算：
 * 伤害 = 白板 × 蓄力 × 暴击 + 附魔加成 × 蓄力，锋利魔咒即原版增伤。
 * <p>只有命中<b>亡灵</b>（{@code #minecraft:sensitive_to_smite}）时由
 * {@code ModMain#applyKnifeDamage} 改成 {@code (白板 + 白板 × 0.2 × 亡灵杀手等级) × 20}
 * ——先按亡灵杀手等级线性追加，再进 ×20 乘区；1 级即额外 白板×20×20% = 40 点。
 * 投掷命中用同一套公式（见 {@code ModMain#computeKnifeDamage}）。
 *
 * <p><b>获取途径（6.7.2 起）</b>：<b>没有合成配方</b> —— 只能从幸运方块开出
 * （本物品在幸运物品池 {@code #joes_addons_for_abmc:lucky_items} 里）以及创造模式物品栏里拿。
 * 原来那条"西瓜片 / 岩浆膏 / 不死图腾"竖排三格的配方已按需求删除，
 * 详见 {@code datagen/ModRecipeProvider} 顶部的说明（连自动生成的配方解锁进度一起删了）。
 */
public class GlisteringMelonKnifeItem extends SwordItem {
    /** 白板攻击力：与下界合金斧相同（玩家基础 1 + 本武器 9 = 10）。 */
    public static final float BASE_ATTACK_DAMAGE = 10.0F;

    public GlisteringMelonKnifeItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    /**
     * 让这把刀能吃<b>忠诚</b>。
     * <p>
     * 原版忠诚的 {@code supported_items} 只含 {@code minecraft:trident}，所以默认情况下铁砧/附魔
     * 都不会把它给到这把刀上（表现就是"附不上、铁砧产物上看不到忠诚"）。
     * <p>
     * 两条路都补上了：
     * <ol>
     *   <li>本模组补了数据包标签 {@code data/minecraft/tags/item/enchantable/trident.json}；</li>
     *   <li>这里再覆写 NeoForge 的 {@link net.neoforged.neoforge.common.extensions.IItemExtension#supportsEnchantment}
     *       —— <b>铁砧这类"施加附魔"的机制正是看它</b>（Neo 的文档原话：其它施加机制检查
     *       {@code supportsEnchantment}，"想让那些机制能施加某个附魔，就得把物品加进对应标签
     *       <b>或者覆写这个方法</b>"）。于是即便别的整合包用数据包覆盖/替换了那个标签，忠诚依然能上。
     * </ol>
     */
    @Override
    public boolean supportsEnchantment(ItemStack stack,
                                       net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
        if (enchantment.is(net.minecraft.world.item.enchantment.Enchantments.LOYALTY)) {
            return true;
        }
        return super.supportsEnchantment(stack, enchantment);
    }

    /**
     * 附魔台那条路的第二道判定。
     * <p>
     * 附魔台列出候选附魔时，NeoForge 把原版写死的过滤改成了
     * {@code stack::isPrimaryItemFor}，而它的默认实现是
     * {@code supportsEnchantment(stack, 魔咒) && (primary_items 为空 或 物品在 primary_items 里)}。
     * <p>
     * 忠诚<b>没有</b> {@code primary_items}（原版 json 里只有 {@code supported_items}），
     * 所以这里跟上面一样放开就够：附魔台也会把忠诚列给这把刀。
     */
    @Override
    public boolean isPrimaryItemFor(ItemStack stack,
                                    net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
        if (enchantment.is(net.minecraft.world.item.enchantment.Enchantments.LOYALTY)) {
            return true;
        }
        return super.isPrimaryItemFor(stack, enchantment);
    }

    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
            .add(
                Attributes.ATTACK_DAMAGE,
                // 下界合金斧 = 5.0 + 下界合金攻击力加成 4.0 = 9.0；这里同为 9.0，合计即 10 点白板伤害
                new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 9.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND
            )
            .add(
                Attributes.ATTACK_SPEED,
                new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.4F, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND
            )
            .build();
    }

    /** 蓄力姿势与三叉戟完全一致（{@code UseAnim.SPEAR} → 手臂摆出投掷标枪的姿势）。 */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        int countBefore = stack.getCount();
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        if (stack.getCount() < countBefore && attacker instanceof Player player) {
            if (player.getRandom().nextFloat() < 0.5F) {
                player.spawnAtLocation(new ItemStack(Items.TOTEM_OF_UNDYING));
            }
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isTooDamagedToUse(stack)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /**
     * 投掷：动作参数与三叉戟一致——至少蓄力 10 刻、出手速度 2.5、播放三叉戟投掷音效。
     * <p>命中伤害不在这里写死，由投掷物用 {@code ModMain#computeKnifeDamage} 按近战同一套公式结算。
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        int i = this.getUseDuration(stack, entity) - timeLeft;
        if (i < 10) return;
        if (isTooDamagedToUse(stack)) return;

        if (!level.isClientSide) {
            int countBefore = stack.getCount();
            stack.hurtAndBreak(2, player, LivingEntity.getSlotForHand(entity.getUsedItemHand()));
            if (stack.getCount() < countBefore) {
                if (player.getRandom().nextFloat() < 0.5F) {
                    player.spawnAtLocation(new ItemStack(Items.TOTEM_OF_UNDYING));
                }
            }
            ThrownGlisteringMelonKnife knife = new ThrownGlisteringMelonKnife(level, player, stack);
            knife.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 2.5F, 1.0F);
            knife.setCreativeOnly(player.hasInfiniteMaterials());
            level.addFreshEntity(knife);
            level.playSound(null, knife, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
            if (!player.hasInfiniteMaterials()) {
                player.getInventory().removeItem(stack);
            }
        }
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    public static boolean isTooDamagedToUse(ItemStack stack) {
        return stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    @Override
    public int getEnchantmentValue() {
        return 14;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, net.neoforged.neoforge.common.ItemAbility itemAbility) {
        return net.neoforged.neoforge.common.ItemAbilities.DEFAULT_SWORD_ACTIONS.contains(itemAbility);
    }
}
