package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家“渲染替换”变形目标的同步数据（字符串 = morph 目标类型 id，如 {@code minecraft:stone}、
 * {@code mob_shell:minecraft:zombie}、{@code player_shell:xxx}）。
 *
 * 为什么需要它：此前玩家的变形外观只靠 {@code TransmutationStatePayload}（自定义网络包）驱动，
 * Replay Mod 等录制实体元数据的模组不会记录这种自定义包，导致回放里玩家看起来“没有变形”。
 * 把变形目标写进玩家的同步实体数据（DataWatcher）后，服务端 set 时会自动发出
 * {@code SetEntityDataPacket}，Replay Mod 会一并录下并在回放时恢复——客户端据此渲染替换。
 */
public final class PlayerMorphSync {

    public static final EntityDataAccessor<String> MORPH_TYPE =
        SynchedEntityData.defineId(Player.class, EntityDataSerializers.STRING);

    private PlayerMorphSync() {
    }

    /**
     * 提前触发本类加载：必须在任意 Player 实体被构造之前调用。
     * 否则 MORPH_TYPE 会在首次被引用（即 Player#defineSynchedData 的 mixin 注入点）时才惰性初始化，
     * 此时 SynchedEntityData.Builder 已按旧的 getCount(Player.class) 分配好数组，随后 define(id) 越界崩溃。
     * 由 ModMain 构造阶段调用。
     */
    public static void init() {
        // 空方法：仅用于触发类加载，从而执行 MORPH_TYPE 的静态初始化（defineId 注册进 ClassTreeIdRegistry）
    }

    public static String getMorphType(Player player) {
        return player.getEntityData().get(MORPH_TYPE);
    }

    public static void setMorphType(Player player, String morphType) {
        player.getEntityData().set(MORPH_TYPE, morphType == null ? "" : morphType);
    }
}