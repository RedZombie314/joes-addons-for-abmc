package cn.autoforged.joes_addons_for_abmc.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端附魔台计数器缓存：由 {@code EnchantTableCounterPayload} 同步。
 * 渲染附魔台上方书时据此判断计数器是否耗尽（<=0 则不渲染书）。
 */
public final class EnchantTableCounterClient {
    /** dimId@x,y,z -> 剩余次数。 */
    private static final Map<String, Integer> COUNTER = new ConcurrentHashMap<>();

    private EnchantTableCounterClient() {}

    public static void set(ResourceLocation dimId, BlockPos pos, int count) {
        COUNTER.put(key(dimId, pos), count);
    }

    /** 返回该附魔台的剩余次数；未收到任何同步时返回 -1（表示“未知/无记录”）。 */
    public static int get(ResourceLocation dimId, BlockPos pos) {
        return COUNTER.getOrDefault(key(dimId, pos), -1);
    }

    private static String key(ResourceLocation dimId, BlockPos pos) {
        return dimId + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static int get(Level level, BlockPos pos) {
        return get(level.dimension().location(), pos);
    }
}
