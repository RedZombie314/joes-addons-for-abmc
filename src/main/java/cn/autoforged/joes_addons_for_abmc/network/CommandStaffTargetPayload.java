package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 命令方块权杖左键攻击请求（客户端→服务端）。
 * 携带客户端准星瞄准的目标实体 UUID 与当时能力模式，服务端据此执行对应动作：
 * - 击杀模式(1)：对被瞄准生物执行 /kill（并渲染 “/kill (UUID)” 文本 Display）。
 * - 抓取模式(2)：开始每刻对被瞄准生物执行 tp 到玩家前方 6 格（再次左键同一生物则停止）。
 * - 启用/禁用AI(3)：读取实体 NoAI 并取反，同时渲染 “/data modify entity (UUID) NoAI set value 0/1” 文本 Display。
 * - 无(0)/护盾(4)模式不会发送本请求。
 */
public record CommandStaffTargetPayload(String targetUuid, int mode) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CommandStaffTargetPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "command_staff_target"));

    public static final StreamCodec<FriendlyByteBuf, CommandStaffTargetPayload> STREAM_CODEC =
        CustomPacketPayload.codec(CommandStaffTargetPayload::write, CommandStaffTargetPayload::new);

    private CommandStaffTargetPayload(FriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.targetUuid);
        buf.writeInt(this.mode);
    }

    @Override
    public Type<CommandStaffTargetPayload> type() {
        return TYPE;
    }
}
