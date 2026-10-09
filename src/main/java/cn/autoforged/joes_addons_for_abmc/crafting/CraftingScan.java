package cn.autoforged.joes_addons_for_abmc.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 「合成事件」共用的配方检索：工作台帽子（{@link cn.autoforged.joes_addons_for_abmc.craftinghat.CraftingHatCrafting}）
 * 与工作台权杖（{@link cn.autoforged.joes_addons_for_abmc.craftingstaff.CraftingStaffCrafting}）都用这一套。
 *
 * <h3>顺序</h3>
 * <ol>
 *   <li>页签顺序：装备 → 红石 → 杂项 → 建筑方块（{@link #CATEGORY_ORDER}）；</li>
 *   <li>页签内顺序 = 配方书页面的先后：用服务端同步给客户端的同一份顺序
 *       {@code RecipeManager#getOrderedRecipes()}，再按客户端 {@code ClientRecipeBook} 的分组规则
 *       （{@code group} 非空用 group，否则用配方 id）归并，同组只看第一次出现的位置；</li>
 *   <li>只考虑合成台配方、非特殊配方、非残缺配方 —— 与配方书页面显示条件一致。</li>
 * </ol>
 *
 * <h3>跳过规则</h3>
 * <ul>
 *   <li>{@code done}（本轮已经做过）里的配方跳过；</li>
 *   <li>产物已经被 {@code owns} 判定为"拥有"的跳过；</li>
 *   <li><b>品质门槛</b>（工具与盔甲共用）：本轮只要出现过"材料齐全"的更高档位装备
 *       （皮革/木 &lt; 锁链/石 &lt; 金 &lt; 铁 &lt; 钻石 &lt; 下界合金 &lt; 本模组巨型），
 *       低于它的装备全部跳过；</li>
 *   <li>材料不齐的跳过（本次遍历错过它，等下一轮从头再来）。</li>
 * </ul>
 */
public final class CraftingScan {
    /** 页签遍历顺序（需求指定）：装备 → 红石 → 杂项 → 建筑方块。 */
    public static final List<CraftingBookCategory> CATEGORY_ORDER = List.of(
        CraftingBookCategory.EQUIPMENT,
        CraftingBookCategory.REDSTONE,
        CraftingBookCategory.MISC,
        CraftingBookCategory.BUILDING);
    /** 玩家物品栏检索范围：0..35（快捷栏 0-8 + 主背包 9-35）。 */
    public static final int INVENTORY_SIZE = 36;

    /** 材料来源：玩家物品栏，或一次合成会话吸收到的物品池。 */
    public interface Source {
        int size();

        ItemStack get(int slot);

        void take(int slot, int amount);
    }

    /** 一条"要取的材料"：从哪个槽位取、取出来的栈长什么样（1 个，含组件）。 */
    public record Reserved(int slot, ItemStack preview) {
    }

    /** 一次检索结果：选中的配方 + 抽取方案 + 产物。 */
    public record Pick(RecipeHolder<CraftingRecipe> recipe, List<Reserved> reserved, ItemStack product) {
    }

    private CraftingScan() {
    }

    /** 与同步给客户端完全一致的配方顺序（配方书页面顺序）。 */
    public static List<RecipeHolder<?>> orderedRecipes(ServerPlayer player) {
        return new ArrayList<>(player.server.getRecipeManager().getOrderedRecipes());
    }

    /** 玩家物品栏（0..35）作为材料来源。 */
    public static Source inventorySource(ServerPlayer player) {
        return new Source() {
            @Override
            public int size() {
                return INVENTORY_SIZE;
            }

            @Override
            public ItemStack get(int slot) {
                return player.getInventory().getItem(slot);
            }

            @Override
            public void take(int slot, int amount) {
                player.getInventory().removeItem(slot, amount);
            }
        };
    }

    /**
     * 一个物品池作为材料来源（工作台权杖吸收来的东西）。
     * <p>注意：{@code take} 可能在栈被取空时把该条目从池里移除，导致后面的下标前移 ——
     * 所以调用方必须按下标<b>从大到小</b>消费，见 {@link #consumeDescending}。
     */
    public static Source poolSource(List<ItemStack> pool) {
        return new Source() {
            @Override
            public int size() {
                return pool.size();
            }

            @Override
            public ItemStack get(int slot) {
                return pool.get(slot);
            }

            @Override
            public void take(int slot, int amount) {
                ItemStack stack = pool.get(slot);
                stack.shrink(amount);
                if (stack.isEmpty()) {
                    pool.remove(slot);
                }
            }
        };
    }

    /**
     * 按抽取方案真正扣材料。
     * <p>必须<b>按下标从大到小</b>消费：物品池在栈取空时会把条目移除，若从小往大会让后面的下标错位。
     */
    public static void consumeDescending(Source source, List<Reserved> reserved) {
        List<Reserved> sorted = new ArrayList<>(reserved);
        sorted.sort((a, b) -> Integer.compare(b.slot(), a.slot()));
        for (Reserved r : sorted) {
            if (r.slot() >= 0 && r.slot() < source.size()) {
                source.take(r.slot(), 1);
            }
        }
    }

    /**
     * 本轮<b>品质门槛</b>：遍历所有非特殊合成配方，产物是<b>已知品质的工具或盔甲</b>
     * 且<b>材料齐全</b>的取最高档位。
     * <p>判定"材料齐全"时<b>不看</b>产物是否已拥有、也不看本轮是否已经做过 —— 这样消耗掉材料之后，
     * 本轮的门槛依然保持在高位，低级装备不会被回头合成。门槛只升不降（{@code currentGate} 是已有门槛）。
     */
    public static int bestCraftableQualityTier(List<RecipeHolder<?>> ordered, Source source,
                                              HolderLookup.Provider registries, int currentGate) {
        int gate = currentGate;
        for (RecipeHolder<?> holder : ordered) {
            if (!(holder.value() instanceof CraftingRecipe recipe)) continue;
            if (!isBookVisible(recipe)) continue;
            int rank = qualityTierRank(recipe.getResultItem(registries));
            if (rank <= gate) continue;
            if (resolveIngredients(source, recipe) == null) continue; // 材料不齐就谈不上"能够合成"
            gate = rank;
        }
        return gate;
    }

    /**
     * 按顺序找第一个"本轮还没做过 & 材料齐 & 产物还没有"的配方。
     *
     * @param owns 判断"产物是否已经拥有"（帽子看玩家物品栏；权杖看物品栏 + 吸收池）
     * @return 选中的配方；一整轮都没有可做的返回 {@code null}（调用方据此判定"本轮遍历结束"）
     */
    @Nullable
    public static Pick find(List<RecipeHolder<?>> ordered, Source source, HolderLookup.Provider registries,
                            Set<ResourceLocation> done, int gate, Predicate<ItemStack> owns) {
        for (CraftingBookCategory category : CATEGORY_ORDER) {
            for (RecipeHolder<CraftingRecipe> holder : pageOrder(ordered, category)) {
                if (done.contains(holder.id())) continue; // 本轮遍历已经做过 → 跳过
                CraftingRecipe recipe = holder.value();
                ItemStack product = recipe.getResultItem(registries);
                if (product.isEmpty()) continue;
                int rank = qualityTierRank(product);
                // 品质门槛：本轮能做出更高档位的装备 → 这件低级装备跳过
                // （要在"已拥有"之前判断：钻石剑本轮做过之后，铁剑仍要被门槛挡掉）
                if (rank > 0 && rank < gate) continue;
                if (owns.test(product)) continue;
                List<Reserved> reserved = resolveIngredients(source, recipe);
                if (reserved == null) continue; // 材料不齐
                done.add(holder.id());
                return new Pick(holder, reserved, product.copy());
            }
        }
        return null;
    }

    /** 配方书页面是否显示这个配方（特殊配方/残缺配方不上页面）。 */
    private static boolean isBookVisible(CraftingRecipe recipe) {
        return !recipe.isSpecial() && !recipe.isIncomplete();
    }

    /** 某个页签内，按"页面格子先后（同组内按原顺序）"列出配方。 */
    public static List<RecipeHolder<CraftingRecipe>> pageOrder(List<RecipeHolder<?>> ordered, CraftingBookCategory category) {
        Map<String, List<RecipeHolder<CraftingRecipe>>> groups = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : ordered) {
            if (!(holder.value() instanceof CraftingRecipe recipe)) continue;
            if (!isBookVisible(recipe)) continue;
            if (recipe.category() != category) continue;
            String key = recipe.getGroup().isEmpty() ? holder.id().toString() : recipe.getGroup();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(cast(holder));
        }
        List<RecipeHolder<CraftingRecipe>> flat = new ArrayList<>();
        for (List<RecipeHolder<CraftingRecipe>> group : groups.values()) {
            flat.addAll(group);
        }
        return flat;
    }

    @SuppressWarnings("unchecked")
    private static RecipeHolder<CraftingRecipe> cast(RecipeHolder<?> holder) {
        return (RecipeHolder<CraftingRecipe>) holder;
    }

    /**
     * 按"9 宫格从左到右、从上到下"逐格抽取材料（只模拟，不改来源）。
     * <p>形状配方：{@code getIngredients()} 就是网格行优先（含空位，空位跳过）；
     * 无序配方：就是配方自己的列表顺序。
     *
     * @return 抽取方案；材料不齐返回 {@code null}
     */
    @Nullable
    public static List<Reserved> resolveIngredients(Source source, CraftingRecipe recipe) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        int size = source.size();
        int[] remaining = new int[size];
        for (int i = 0; i < size; i++) {
            ItemStack stack = source.get(i);
            remaining[i] = stack.isEmpty() ? 0 : stack.getCount();
        }
        List<Reserved> reserved = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue; // 网格空位
            int slot = -1;
            for (int i = 0; i < size; i++) {
                if (remaining[i] <= 0) continue;
                if (ingredient.test(source.get(i))) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) return null; // 材料不齐
            remaining[slot]--;
            reserved.add(new Reserved(slot, source.get(slot).copyWithCount(1)));
        }
        return reserved.isEmpty() ? null : reserved;
    }

    /**
     * 品质档位（<b>工具与盔甲共用同一套档次</b>，按需求：钻石 &gt; 铁 &gt; 金 &gt; 石 &gt; 木）：
     * <pre>皮革/木 1 &lt; 锁链/石 2 &lt; 金 3 &lt; 铁 4 &lt; 钻石 5 &lt; 下界合金 6 &lt; 本模组巨型下界合金 7</pre>
     * <p>返回 0 = 不参与品质门槛：既不会顶起门槛，也不会被门槛挡掉。包括
     * 非装备（方块、材料等）、<b>模组自定义材质</b>（闪烁西瓜刀、工作台帽子等）、
     * 以及海龟壳/狼铠这类"特殊用途"装备。
     */
    public static int qualityTierRank(ItemStack stack) {
        return Math.max(toolTierRank(stack), armorTierRank(stack));
    }

    /** 工具的档位；非工具或模组自定义品质返回 0。 */
    public static int toolTierRank(ItemStack stack) {
        if (!(stack.getItem() instanceof TieredItem tiered)) return 0;
        Tier tier = tiered.getTier();
        if (tier == Tiers.WOOD) return 1;
        if (tier == Tiers.STONE) return 2;
        if (tier == Tiers.GOLD) return 3;
        if (tier == Tiers.IRON) return 4;
        if (tier == Tiers.DIAMOND) return 5;
        if (tier == Tiers.NETHERITE) return 6;
        if (tier == cn.autoforged.joes_addons_for_abmc.item.ModTiers.GIANT_NETHERITE_SWORD
            || tier == cn.autoforged.joes_addons_for_abmc.item.ModTiers.GIANT_NETHERITE_AXE) {
            return 7;
        }
        return 0;
    }

    /**
     * 盔甲的档位：按盔甲材质的注册名判定（只认原版材质），与工具同一套档次。
     * <p>锁链甲原版没有配方、不会出现在配方书里，这里给个与石头同档的值仅为了完整性。
     * <p>海龟壳（turtle_scute）、狼铠（armadillo_scute）以及本模组材质返回 0 = 不参与门槛
     * —— 它们是"特殊用途"装备，不应该因为能做出钻石装备就做不出来。
     */
    public static int armorTierRank(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem armor)) return 0;
        ResourceLocation id = armor.getMaterial().unwrapKey()
            .map(key -> key.location())
            .orElse(null);
        if (id == null || !"minecraft".equals(id.getNamespace())) return 0;
        return switch (id.getPath()) {
            case "leather" -> 1;
            case "chainmail" -> 2;
            case "gold" -> 3;
            case "iron" -> 4;
            case "diamond" -> 5;
            case "netherite" -> 6;
            default -> 0; // 海龟壳 / 狼铠 / 其他特殊材质
        };
    }

    /** 物品栏里是否已经有这个物品（按物品种类比较，忽略数量与组件）。 */
    public static boolean inventoryOwns(ServerPlayer player, ItemStack product) {
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(product.getItem())) return true;
        }
        return false;
    }

    /** 是否能在某个物品池里找到这个物品（按物品种类比较）。 */
    public static boolean poolOwns(List<ItemStack> pool, ItemStack product) {
        for (ItemStack stack : pool) {
            if (!stack.isEmpty() && stack.is(product.getItem())) return true;
        }
        return false;
    }

    /** 便捷方法：从玩家物品栏取一个能匹配该材料槽的下标（没有则空）。 */
    public static Optional<Integer> findSlot(Source source, Ingredient ingredient) {
        for (int i = 0; i < source.size(); i++) {
            ItemStack stack = source.get(i);
            if (!stack.isEmpty() && ingredient.test(stack)) return Optional.of(i);
        }
        return Optional.empty();
    }
}
