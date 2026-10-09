package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 服务端 → 客户端：<b>打开"送到谁"的下拉列表</b>（需求 6.5.21）。
 *
 * <h3>什么时候发</h3>
 * 旁观操控中按右键、且选择器手里已经有东西时：服务端<b>先不送</b>，而是把候选名单发下去，
 * 由客户端弹下拉 UI；玩家选中并按空格之后才回 {@link PilotSendConfirmPayload}，那时才真正 send。
 * 需求原话："这个 ui 会在 send 过程中被触发，只有确认后才会执行 send 操作"。
 *
 * <h3>名单怎么来</h3>
 * 符合"位于幸运核心附体的玩家空壳 50 格以内"的在线玩家；单人档（没有其它玩家）时附加
 * Technoblade / Dream 两个调试项；多人且一个都不符合时给一条"暂无需要支援的玩家"的占位项。
 * 三种"不指向真实玩家"的条目一律用 {@link #NO_PLAYER} 作 id，客户端照常显示、选中后服务端不做任何事。
 *
 * @param selectorId 那只被旁观的选择器（确认时原样回传，服务端据此核对）
 * @param entries    下拉项，顺序即显示顺序
 */
public record PilotSendListPayload(int selectorId, List<Entry> entries) implements CustomPacketPayload {

    /** 调试项 / 占位项的 id：不是真实玩家，选中后不会有任何实际效果（需求）。 */
    public static final UUID NO_PLAYER = new UUID(0L, 0L);

    /** 下拉里的一项。 */
    public record Entry(String name, UUID id) {
    }

    public static final CustomPacketPayload.Type<PilotSendListPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "pilot_send_list"));

    public static final StreamCodec<FriendlyByteBuf, PilotSendListPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.selectorId);
                buf.writeVarInt(p.entries.size());
                for (Entry entry : p.entries) {
                    buf.writeUtf(entry.name(), 64);
                    buf.writeUUID(entry.id());
                }
            },
            buf -> {
                int selectorId = buf.readVarInt();
                int size = buf.readVarInt();
                List<Entry> entries = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    entries.add(new Entry(buf.readUtf(64), buf.readUUID()));
                }
                return new PilotSendListPayload(selectorId, List.copyOf(entries));
            }
        );

    @Override
    public Type<PilotSendListPayload> type() {
        return TYPE;
    }
}
