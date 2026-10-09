package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.potion.ModPotions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;

/**
 * 幸运物品子事件：<b>英雄药水</b>、<b>英雄套装</b>与<b>英雄之剑</b>。
 *
 * <h3>三个子事件</h3>
 * <ol>
 *   <li>{@code item/heroism_potion} —— 一瓶<b>英雄药水</b>
 *       （{@link ModPotions#HEROISM}：力量 V / 速度 V / 抗性提升 IV / 伤害吸收 V / 再生 V / 急迫 V / 跳跃提升 II，
 *       各持续 3 分钟）；</li>
 *   <li>{@code item/hero_set} —— <b>英雄套装</b>：开出时在<b>钻石头盔 / 胸甲 / 护腿 / 靴子</b>中
 *       <b>等概率</b>选一件发出来（一次只给一件）；</li>
 *   <li>{@code item/hero_sword} —— <b>英雄之剑</b>：原版钻石剑，附锋利 V / 亡灵克星 V / 节肢杀手 V /
 *       抢夺 V / 横扫之刃 V / 火焰附加 II / 耐久 V / 经验修补，且<b>不带任何累计惩罚</b>
 *       （见 {@link #heroSword}）。</li>
 * </ol>
 *
 * <p><b>权重</b>：英雄套装事件用 {@link LuckyEvent#weighted} 给了 <b>4</b> 的权重 ——
 * 原来是"头盔/胸甲/护腿/靴子"四个各权重 1 的独立子事件（合计 4），现在合成一个事件后
 * 总权重仍是 4，所以<b>整体抽取概率与之前完全一致</b>，只是"哪一件"改成在事件内部等概率掷一次。
 * （当年合并是为了让 latest 能挂在一个事件上 —— latest 只有一个字段；6.5.6 起 latest 已换成
 * 多选一的 debug 池，但这条合并仍保留不动的理由依然成立：总权重不变、只需登记一个条目。）
 *
 * <p>四件（含靴子）的附魔相同：保护 IV、弹射物保护 IV、火焰保护 IV、爆炸保护 IV、耐久 III、经验修补；
 * 靴子额外带<b>摔落缓冲 IV</b>。
 *
 * <p>附魔一律通过服务器的附魔注册表解析（{@code registryAccess().lookupOrThrow(Registries.ENCHANTMENT)}），
 * 因此数据包对附魔的改动同样生效（写法与 {@link LuckyBobEvent} 一致）。
 *
 * <p>物品以掉落物形式出现在幸运方块的位置（与池内物品子事件 {@code registerItemReward} 的做法一致）。
 */
public final class LuckyHeroEvents {
    /** 英雄套装事件的权重：等于原先"四件各权重 1"的合计，保证整体概率不变。 */
    private static final int HERO_SET_WEIGHT = 4;

    private LuckyHeroEvents() {
    }

    /** 注册英雄系列的三个幸运物品子事件（由 {@link LuckyEvents#registerAll()} 调用）。 */
    public static void registerAll() {
        // 1) 英雄药水
        LuckyEvents.register(LuckyEvent.of(LuckyEvents.id("item/heroism_potion"),
            LuckyEventCategory.LUCKY_ITEM,
            "给一瓶英雄药水（力量V、速度V、抗性提升IV、伤害吸收V、再生V、急迫V、跳跃提升II，各 3 分钟）",
            (level, pos, player) -> drop(level, pos,
                PotionContents.createItemStack(Items.POTION, ModPotions.HEROISM))));

        // 2) 英雄套装：一个事件，内部四件等概率
        LuckyEvents.register(LuckyEvent.weighted(LuckyEvents.id("item/hero_set"),
            LuckyEventCategory.LUCKY_ITEM, HERO_SET_WEIGHT,
            "给一件英雄套装（钻石头盔/胸甲/护腿/靴子之一，四件等概率；保护IV、弹射物保护IV、火焰保护IV、爆炸保护IV、荆棘III、耐久III、经验修补，靴子另有摔落缓冲IV）",
            (level, pos, player) -> {
                Item piece = switch (level.random.nextInt(4)) {
                    case 0 -> Items.DIAMOND_HELMET;
                    case 1 -> Items.DIAMOND_CHESTPLATE;
                    case 2 -> Items.DIAMOND_LEGGINGS;
                    default -> Items.DIAMOND_BOOTS;
                };
                drop(level, pos, heroPiece(level, piece, piece == Items.DIAMOND_BOOTS));
            }));

        // 3) 英雄之剑：原版钻石剑 + 一整套附魔（不带任何累计惩罚）
        LuckyEvents.register(LuckyEvent.of(LuckyEvents.id("item/hero_sword"),
            LuckyEventCategory.LUCKY_ITEM,
            "给一把英雄之剑（钻石剑：锋利V、亡灵克星V、节肢杀手V、抢夺V、横扫之刃V、火焰附加II、耐久V、经验修补，且无累计惩罚）",
            (level, pos, player) -> drop(level, pos, heroSword(level.registryAccess()))));
    }

    /**
     * <b>英雄之剑</b>：原版钻石剑 + 锋利 V、亡灵克星 V、节肢杀手 V、抢夺 V、横扫之刃 V、火焰附加 II、
     * 耐久 V、经验修补，并把 {@code REPAIR_COST} 显式设为 <b>0</b> —— 也就是<b>不带任何累计惩罚</b>
     * （附魔台/铁砧加工过的物品会累积 prior work penalty，这里从源头清零，后续在铁砧上继续加工更便宜）。
     *
     * <p><b>锋利 / 亡灵克星 / 节肢杀手三者共存</b>：原版把这三条放进
     * {@code #minecraft:exclusive_set/damage}，但那条互斥只在<b>附魔台与铁砧</b>里生效
     * （{@code EnchantmentMenu}/{@code AnvilMenu} 会拦），而 {@code ItemStack#enchant} 只是往
     * {@code ENCHANTMENTS} 组件里写键值对（{@code ItemEnchantments.Mutable#upgrade} → {@code merge}，
     * 没有任何互斥处理）。这里干脆一次性构造整份附魔表再写入组件，让"三条同时存在"成为代码里明确的意图，
     * 不受任何互斥规则影响；三种伤害加成的效果也都会各自生效（对亡灵吃锋利+亡灵克星，对节肢生物吃锋利+节肢杀手）。
     *
     * <p>参数用 {@code HolderLookup.Provider}（{@code ServerLevel#registryAccess()} 与
     * 创造栏的 {@code ItemDisplayParameters#holders()} 都是它），所以同一份代码既能给事件发奖、
     * 也能给创造模式物品栏造样品。
     */
    public static ItemStack heroSword(net.minecraft.core.HolderLookup.Provider registries) {
        var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
            enchantmentsOf(enchantments,
                entry(Enchantments.SHARPNESS, 5),
                entry(Enchantments.SMITE, 5),
                entry(Enchantments.BANE_OF_ARTHROPODS, 5),
                entry(Enchantments.LOOTING, 5),
                entry(Enchantments.SWEEPING_EDGE, 5),
                entry(Enchantments.FIRE_ASPECT, 2),
                entry(Enchantments.UNBREAKING, 5),
                entry(Enchantments.MENDING, 1)));
        stack.set(net.minecraft.core.component.DataComponents.REPAIR_COST, 0); // 不附带任何累计惩罚
        return stack;
    }

    /** 附魔表里的一项（附魔 key + 等级）。 */
    private record EnchantEntry(net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key,
                                int level) {
    }

    private static EnchantEntry entry(net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key,
                                      int level) {
        return new EnchantEntry(key, level);
    }

    /**
     * 一次性构造整份附魔表（<b>不做任何互斥过滤</b>，全部按给定等级写入）。
     * 走 {@code Mutable#set} 而不是 {@code ItemStack#enchant}，是为了让"这一件上到底有哪几条附魔"
     * 在代码里一目了然，也避免以后原版改动 {@code enchant()} 的语义时把互斥的那几条吃掉。
     */
    private static ItemEnchantments enchantmentsOf(
        net.minecraft.core.HolderLookup.RegistryLookup<net.minecraft.world.item.enchantment.Enchantment> enchantments,
        EnchantEntry... entries) {
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (EnchantEntry e : entries) {
            mutable.set(enchantments.getOrThrow(e.key()), e.level());
        }
        return mutable.toImmutable();
    }

    /** 掉在幸运方块的位置（与池内物品子事件的发放方式一致）。 */
    private static void drop(ServerLevel level, BlockPos pos, ItemStack stack) {
        Block.popResource(level, pos, stack);
    }

    /**
     * 一件英雄套装部件：保护 IV + 弹射物保护 IV + 火焰保护 IV + 爆炸保护 IV + <b>荆棘 III</b> +
     * 耐久 III + 经验修补，靴子再加摔落缓冲 IV。
     *
     * <p>注意：这几种"保护"在原版里是<b>互斥</b>的（附魔台/铁砧不会同时给出），
     * 但附魔表是直接构造后写入组件的，不受那条限制，所以一件装备上可以同时拥有四种保护 + 荆棘，
     * 效果也都会生效 —— 这正是"英雄套装"想要的。
     */
    private static ItemStack heroPiece(ServerLevel level, Item item, boolean boots) {
        ItemStack stack = new ItemStack(item);
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        if (boots) {
            stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                enchantmentsOf(enchantments,
                    entry(Enchantments.PROTECTION, 4),
                    entry(Enchantments.PROJECTILE_PROTECTION, 4),
                    entry(Enchantments.FIRE_PROTECTION, 4),
                    entry(Enchantments.BLAST_PROTECTION, 4),
                    entry(Enchantments.THORNS, 3),
                    entry(Enchantments.UNBREAKING, 3),
                    entry(Enchantments.MENDING, 1),
                    entry(Enchantments.FEATHER_FALLING, 4)));
        } else {
            stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                enchantmentsOf(enchantments,
                    entry(Enchantments.PROTECTION, 4),
                    entry(Enchantments.PROJECTILE_PROTECTION, 4),
                    entry(Enchantments.FIRE_PROTECTION, 4),
                    entry(Enchantments.BLAST_PROTECTION, 4),
                    entry(Enchantments.THORNS, 3),
                    entry(Enchantments.UNBREAKING, 3),
                    entry(Enchantments.MENDING, 1)));
        }
        return stack;
    }
}
