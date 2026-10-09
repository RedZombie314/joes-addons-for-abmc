package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ModMain.MODID);

    public static final Supplier<DataComponentType<String>> BLOCKTYPE =
        DATA_COMPONENTS.register("blocktype",
            () -> DataComponentType.<String>builder()
                .persistent(Codec.STRING)
                .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                .build());

    public static final Supplier<DataComponentType<Integer>> BLOCK_DAMAGE =
        DATA_COMPONENTS.register("block_damage",
            () -> DataComponentType.<Integer>builder()
                .persistent(Codec.INT)
                .networkSynchronized(ByteBufCodecs.INT)
                .build());

    public static final Supplier<DataComponentType<Map<String, Integer>>> BLOCK_DURABILITIES =
        DATA_COMPONENTS.register("block_durabilities",
            () -> DataComponentType.<Map<String, Integer>>builder()
                .persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
                .build());

    public static final Supplier<DataComponentType<String>> ITEM_TYPE =
        DATA_COMPONENTS.register("item_type",
            () -> DataComponentType.<String>builder()
                .persistent(Codec.STRING)
                .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                .build());

    // 传送药水模式：point=定点、directional=定向、random=随机
    public static final Supplier<DataComponentType<String>> TRANSPORT_MODE =
        DATA_COMPONENTS.register("transport_mode",
            () -> DataComponentType.<String>builder()
                .persistent(Codec.STRING)
                .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                .build());

    // 定点传送药水的目标坐标（世界坐标）
    public static final Supplier<DataComponentType<Vec3>> TARGET_POS =
        DATA_COMPONENTS.register("target_pos",
            () -> DataComponentType.<Vec3>builder()
                .persistent(Vec3.CODEC)
                .build());

    // 定点传送药水的目标实体 UUID（传送到该实体附近）
    public static final Supplier<DataComponentType<UUID>> TARGET_ENTITY_UUID =
        DATA_COMPONENTS.register("target_entity_uuid",
            () -> DataComponentType.<UUID>builder()
                .persistent(Codec.STRING.xmap(UUID::fromString, UUID::toString))
                .build());

    // 定向传送药水的前进格数（浮点，玩家投掷方向水平前进的距离）
    public static final Supplier<DataComponentType<Double>> TARGET_DIST =
        DATA_COMPONENTS.register("target_dist",
            () -> DataComponentType.<Double>builder()
                .persistent(Codec.DOUBLE)
                .build());

    /**
     * 幸运核心（Orb of Luck）的<b>隐藏倒计时</b>：到期时刻（{@code Level#getGameTime()} 口径，游戏刻）。
     * <p>
     * 拿到核心时写一次 = 当时 + {@code OrbOfLuckItem.COUNTDOWN_TICKS}（<b>1 分钟</b>）；
     * 之后每"用"一次直接减去 {@code OrbOfLuckItem.USE_COST_TICKS}（2 秒；debug 模式减 60 秒）。
     * 到期（≤ 当前时间）就触发附体，检查在 {@code OrbOfLuckLock} 的每刻钩子里做。
     * <p>
     * <b>存"到期时刻"而不是"剩余刻数"</b>：剩余刻数得每刻回写组件，而数据组件一变就要同步一个包
     * （等于每秒 20 个包）；到期时刻只在"用一次"时才变，平时一个包都不发。
     * <p>
     * 组件不存在时视为"永不到期"（旧存档里的核心不会因为读了个空组件就当场附体），
     * 第一次用到时由 {@code OrbOfLuckItem#ensureCountdown} 补一次"从现在起 1 分钟"。
     */
    public static final Supplier<DataComponentType<Long>> ORB_COUNTDOWN =
        DATA_COMPONENTS.register("orb_countdown",
            () -> DataComponentType.<Long>builder()
                .persistent(Codec.LONG)
                .networkSynchronized(ByteBufCodecs.VAR_LONG)
                .build());

    /**
     * 幸运核心（Orb of Luck）<b>被捡起来的位置</b>（维度 + 方块坐标）。
     * <p>
     * 由 {@code OrbOfLuckEntity#mobInteract} 在"空手右击 INITIAL 阶段的核心、把它收进手里"那一刻写进物品；
     * 附体（{@code OrbOfLuckEvents#triggerPossession}）时再转交给新生成的核心实体，
     * 用于"母体被击败后核心回到原来被捡起来的位置并恢复 INITIAL 阶段"（用户指定）。
     * <p>
     * 记在<b>物品上</b>而不是玩家身上：核心是可以被转手的（非 debug 的创造模式里也不锁手），
     * 记在物品上才跟得住"这颗核心是从哪捡的"。要同步给客户端只是因为它是个普通数据组件，
     * 客户端拿着同一份 tooltip/校验数据不会错。
     */
    public static final Supplier<DataComponentType<GlobalPos>> ORB_PICKUP =
        DATA_COMPONENTS.register("orb_pickup",
            () -> DataComponentType.<GlobalPos>builder()
                .persistent(GlobalPos.CODEC)
                .networkSynchronized(GlobalPos.STREAM_CODEC)
                .build());

    /**
     * 幸运核心<b>已经抽到过哪些事件</b>（需求 6.5.25："至多抽取一次"的那些事件）。
     * <p>
     * 位图：第 n 位对应 {@code OrbOfLuckEvents.EVENTS} 里第 n 个条目。用位图而不是 id 列表，
     * 是因为条目总数就十几个、一个 int 足够，读写和存档都最省事。
     * <p>
     * <b>注意</b>：位序号＝条目在表里的下标，所以<b>往表中间插入条目会让老存档的位图错位</b>。
     * 现在的量级（十几个）完全够用；真要频繁插条目，就改成存 id 列表。
     * <p>
     * 记在物品上：核心是可以被转手的（见 {@link #ORB_PICKUP}），"这颗核心已经出过什么"要跟着它走。
     */
    public static final Supplier<DataComponentType<Integer>> ORB_DRAWN =
        DATA_COMPONENTS.register("orb_drawn",
            () -> DataComponentType.<Integer>builder()
                .persistent(Codec.INT)
                .networkSynchronized(ByteBufCodecs.INT)
                .build());
}
