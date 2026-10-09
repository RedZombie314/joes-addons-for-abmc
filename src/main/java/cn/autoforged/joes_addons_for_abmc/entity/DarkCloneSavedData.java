package cn.autoforged.joes_addons_for_abmc.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 黑暗分身召唤链的存档级状态。
 *
 * <p>存在主世界维度数据文件里（与女巫 Boss 的 {@code jafa_shared_counts} 同样的做法），
 * 因此是<b>整个存档唯一一份</b>，跨会话持久化。
 *
 * <p>「首次双手同时持有 Minecraft Game Icon」刻意用存档标记而不是静态字段：
 * 静态字段会跨存档、跨世界存活（同一 JVM 里退出存档再进另一个存档时不会重置），
 * 而需求是「同一个存档中只会发生一次」。
 *
 * <p>召唤池与抽取队列按玩家 UUID 分别保存：记录池是玩家自己扫出来的收藏，
 * 多人游戏下各人一份更符合直觉。
 */
public final class DarkCloneSavedData extends SavedData {

    private static final String DATA_NAME = "jafa_dark_clone";

    /**
     * 「主手与副手同时持有 Minecraft Game Icon」这一首次事件是否已在本存档发生过。
     * 发生时会把手上的两枚图标重命名为 Positive / Negative Game Icon（即白 / 黑草方块）。
     */
    public boolean iconPairRenamed = false;

    /** 玩家 UUID -> 已记录的生物类型（按记录顺序）。这个顺序只用于存档可读性，抽取顺序由 bag 决定。 */
    private final Map<UUID, List<ResourceLocation>> pools = new LinkedHashMap<>();
    /**
     * 玩家 UUID -> 当前这一轮洗牌后尚未抽完的队列。
     * 这就是「随机播放列表」：抽空之后才会用整个池子重新洗出新的一轮遍历顺序。
     */
    private final Map<UUID, List<ResourceLocation>> bags = new LinkedHashMap<>();

    /** 取（必要时创建）某玩家的召唤池。返回的是活列表，可直接增删。 */
    public List<ResourceLocation> pool(Player player) {
        return this.pools.computeIfAbsent(player.getUUID(), key -> new ArrayList<>());
    }

    /** 取（必要时创建）某玩家当前这一轮的抽取队列。 */
    public List<ResourceLocation> bag(Player player) {
        return this.bags.computeIfAbsent(player.getUUID(), key -> new ArrayList<>());
    }

    /**
     * 清空某玩家的召唤池，<b>连同当前这一轮没抽完的抽取队列一起清</b>。
     * 只清池不清队列的话，清空后下一次抽取仍会从残留队列里取出东西，看起来像没清干净。
     *
     * @return 被清掉的池内条目数
     */
    public int clearPool(Player player) {
        List<ResourceLocation> pool = this.pool(player);
        int size = pool.size();
        pool.clear();
        this.bag(player).clear();
        this.setDirty();
        return size;
    }

    public static DarkCloneSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        DarkCloneSavedData data = new DarkCloneSavedData();
        data.iconPairRenamed = tag.getBoolean("icon_pair_renamed");
        readPlayerLists(tag.getCompound("pools"), data.pools);
        readPlayerLists(tag.getCompound("bags"), data.bags);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("icon_pair_renamed", this.iconPairRenamed);
        tag.put("pools", writePlayerLists(this.pools));
        tag.put("bags", writePlayerLists(this.bags));
        return tag;
    }

    /** 统一取主世界那份，保证跨维度、跨玩家只有一份。 */
    public static DarkCloneSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(DarkCloneSavedData::new, DarkCloneSavedData::load), DATA_NAME);
    }

    private static void readPlayerLists(CompoundTag tag, Map<UUID, List<ResourceLocation>> target) {
        for (String key : tag.getAllKeys()) {
            UUID playerId;
            try {
                playerId = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            List<ResourceLocation> list = new ArrayList<>();
            ListTag entries = tag.getList(key, Tag.TAG_STRING);
            for (int i = 0; i < entries.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(entries.getString(i));
                if (id != null) {
                    list.add(id);
                }
            }
            target.put(playerId, list);
        }
    }

    private static CompoundTag writePlayerLists(Map<UUID, List<ResourceLocation>> source) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<UUID, List<ResourceLocation>> entry : source.entrySet()) {
            ListTag list = new ListTag();
            for (ResourceLocation id : entry.getValue()) {
                list.add(StringTag.valueOf(id.toString()));
            }
            tag.put(entry.getKey().toString(), list);
        }
        return tag;
    }
}
