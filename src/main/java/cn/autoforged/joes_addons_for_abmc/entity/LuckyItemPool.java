package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.block.LuckyBlockEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 幸运维度里自然生成的掉落物<b>候选物品池</b>。
 *
 * <p>池子 = <b>全部原版「生存模式可获取」的物品</b> + <b>幸运物品池</b>
 * （{@code #joes_addons_for_abmc:lucky_items}，解析复用 {@link LuckyBlockEvents#luckyPoolItems(ServerLevel)}）。
 *
 * <p><b>「生存可获取」怎么判</b>：原版没有这样的标签，所以用「命名空间是 {@code minecraft}」+
 * <b>黑名单</b>（{@link #NOT_SURVIVAL}）来近似：黑名单里全是创造模式专属或生存拿不到的物品
 * （命令方块系列、屏障/结构方块/拼图/光源、调试棒、知识之书、基岩、刷怪笼、试炼刷怪笼、宝库、
 * 紫水晶母岩、加固深板岩、石化橡木台阶、末地传送门框架、损坏的铁砧、虫蚀方块系列、玩家头颅、收纳袋），
 * 另外按名字排除全部刷怪蛋（{@code *_spawn_egg}）。
 * 要增删直接改这张黑名单即可（写成字符串而不是 {@code Items.XXX} 常量，是为了改起来方便、
 * 也不怕某个常量在别的版本里被改名）。
 */
public final class LuckyItemPool {

    /** 创造模式专属 / 生存模式拿不到的物品（按注册名，不含命名空间）。 */
    private static final Set<String> NOT_SURVIVAL = Set.of(
        "air",
        "debug_stick",
        "command_block",
        "chain_command_block",
        "repeating_command_block",
        "command_block_minecart",
        "barrier",
        "light",
        "structure_block",
        "structure_void",
        "jigsaw",
        "knowledge_book",
        "bedrock",
        "spawner",
        "trial_spawner",
        "vault",
        "budding_amethyst",
        "reinforced_deepslate",
        "petrified_oak_slab",
        "end_portal_frame",
        "chipped_anvil",
        "damaged_anvil",
        "infested_stone",
        "infested_cobblestone",
        "infested_stone_bricks",
        "infested_mossy_stone_bricks",
        "infested_cracked_stone_bricks",
        "infested_chiseled_stone_bricks",
        "infested_deepslate",
        "player_head",
        // 1.21.1 还不能合成，属于创造专属（1.21.2 才进生存）
        "bundle"
    );

    /** 刷怪蛋后缀：全部排除（生存拿不到）。 */
    private static final String SPAWN_EGG_SUFFIX = "_spawn_egg";

    /**
     * 原版物品部分的缓存。
     * <p>
     * 注册表在启动后就不再变化（1.21.1 的物品注册表是冻结的），所以算一次就够；
     * 幸运物品池那张标签属于数据包内容、重载后会变，所以那一半每次都现解析。
     */
    private static List<Item> vanillaCache;

    private LuckyItemPool() {
    }

    /** 当前可用的全部候选物品。 */
    public static List<Item> candidates(ServerLevel level) {
        List<Item> vanilla = vanillaItems();
        List<Item> lucky = LuckyBlockEvents.luckyPoolItems(level);
        if (lucky.isEmpty()) {
            return vanilla;
        }
        List<Item> all = new ArrayList<>(vanilla.size() + lucky.size());
        all.addAll(vanilla);
        for (Item item : lucky) {
            if (!all.contains(item)) {
                all.add(item);
            }
        }
        return all;
    }

    /** 随机抽一件（数量 1）。池子为空时返回空栈。 */
    public static ItemStack roll(ServerLevel level) {
        List<Item> pool = candidates(level);
        if (pool.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(pool.get(level.random.nextInt(pool.size())));
    }

    /** 原版部分：命名空间为 {@code minecraft}、不在黑名单里、不是刷怪蛋。 */
    private static List<Item> vanillaItems() {
        if (vanillaCache == null) {
            List<Item> list = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (!"minecraft".equals(id.getNamespace())) {
                    continue;
                }
                String path = id.getPath();
                if (NOT_SURVIVAL.contains(path) || path.endsWith(SPAWN_EGG_SUFFIX)) {
                    continue;
                }
                list.add(item);
            }
            vanillaCache = List.copyOf(list);
        }
        return vanillaCache;
    }
}
