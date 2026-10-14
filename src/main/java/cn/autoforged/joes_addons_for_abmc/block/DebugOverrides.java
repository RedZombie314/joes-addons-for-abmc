package cn.autoforged.joes_addons_for_abmc.block;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * <b>落盘的两份调试开关</b>（用户指定：退出重进存档之后仍然保留）。
 *
 * <p>两份互不相干的东西放在同一个 {@link SavedData} 里：它们都是"全局调试开关"、
 * 都写在主世界那一份维度数据里、读写路径完全一样，拆成两个类只会多一遍样板代码。
 *
 * <ol>
 *   <li>{@link #luckyBlockResult()} —— {@code /jafa luckyblockresult <选项>} 指定的
 *       <b>幸运方块强制产物</b>（一个幸运子事件的 id）。设了它，破坏幸运方块就<b>只</b>开出那一个，
 *       不再是"从池子里等概率抽"（用户指定：不再是一个池）；</li>
 *   <li>{@link #forcedAttack()} —— {@code /jafa luck_attack <模式>} 指定的
 *       <b>被附体空壳强制攻击模式</b>（{@code OrbPossessedAttackEvents.ForcedAttack} 的命令名）。</li>
 * </ol>
 *
 * <p>存在<b>主世界</b>的维度数据里（与 {@link LuckyRoster} 同一套写法），于是：整个存档只有一份、
 * 跨维度一致、退出重进仍在；换个存档自然就是另一份（数据跟着存档目录走）。
 * 两份都可以"取消"（置空），取消后各自回到默认行为。
 */
public final class DebugOverrides extends SavedData {

    /** 存档里的数据名（主世界维度数据文件中的一个条目）。 */
    private static final String DATA_NAME = "jafa_debug_overrides";

    private static final String TAG_LUCKY_RESULT = "lucky_block_result";
    private static final String TAG_FORCED_ATTACK = "forced_attack";

    /** 幸运方块强制产物的子事件 id（{@code joes_addons_for_abmc:entity/cats} 这种）；未指定为 null。 */
    @Nullable
    private String luckyBlockResult;

    /** 强制攻击模式的命令名（{@code mace} 这种）；未指定为 null。 */
    @Nullable
    private String forcedAttack;

    /** 幸运方块强制产物的子事件 id；未指定返回 null。 */
    @Nullable
    public String luckyBlockResult() {
        return this.luckyBlockResult;
    }

    /** 设置（传 null/空串 = 取消）幸运方块的强制产物。会标脏，退出时自动写盘。 */
    public void setLuckyBlockResult(@Nullable String eventId) {
        this.luckyBlockResult = (eventId == null || eventId.isEmpty()) ? null : eventId;
        this.setDirty();
    }

    /** 强制攻击模式的命令名；未指定返回 null。 */
    @Nullable
    public String forcedAttack() {
        return this.forcedAttack;
    }

    /** 设置（传 null/空串 = 取消）强制攻击模式。 */
    public void setForcedAttack(@Nullable String commandName) {
        this.forcedAttack = (commandName == null || commandName.isEmpty()) ? null : commandName;
        this.setDirty();
    }

    public static DebugOverrides load(CompoundTag tag, HolderLookup.Provider registries) {
        DebugOverrides data = new DebugOverrides();
        // 用 getString + 空串判断而不是 contains(TAG, type)：这样老数据/手改过的数据都能容忍
        String result = tag.getString(TAG_LUCKY_RESULT);
        data.luckyBlockResult = result.isEmpty() ? null : result;
        String attack = tag.getString(TAG_FORCED_ATTACK);
        data.forcedAttack = attack.isEmpty() ? null : attack;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (this.luckyBlockResult != null) {
            tag.putString(TAG_LUCKY_RESULT, this.luckyBlockResult);
        }
        if (this.forcedAttack != null) {
            tag.putString(TAG_FORCED_ATTACK, this.forcedAttack);
        }
        return tag;
    }

    /** 统一取主世界那一份，保证整个存档只有一份（跨维度、跨玩家都一致）。 */
    public static DebugOverrides get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(DebugOverrides::new, DebugOverrides::load), DATA_NAME);
    }

    /** 从任意维度取同一份（便捷重载）。 */
    public static DebugOverrides get(ServerLevel level) {
        return get(level.getServer());
    }
}
