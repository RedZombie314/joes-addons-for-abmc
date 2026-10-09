package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 幸运选择器的<b>旁观操控</b>：客户端 → 服务端，每刻发一次。
 *
 * <h3>为什么需要自己发包</h3>
 * 旁观一个实体时，原版<b>不</b>把玩家的输入/朝向发给服务端：客户端只在
 * 「自己就是摄像机实体」时才发移动包（{@code LocalPlayer#sendPosition} 里那句
 * {@code if (this.isControlledCamera())}，LocalPlayer.java:277），而旁观时摄像机是<b>被旁观的那只实体</b>，
 * 所以朝向与 WASD 全都不出客户端。原版也没法复用：鼠标转的是<b>玩家自己</b>
 * （{@code MouseHandler#turnPlayer} 最后调的是 {@code minecraft.player.turn(...)}，MouseHandler.java:329），
 * 而被旁观实体的朝向由服务端说了算。所以这里自己把"看向哪儿 + 按了哪些方向键"报上去。
 *
 * <h3>字段</h3>
 * @param entityId 正在被旁观的那只选择器的实体 id（服务端会核对，防止错位）
 * @param yaw      玩家视线的水平角（已归一化到 ±180°，直接就是选择器该朝的方向）
 * @param pitch    玩家视线的俯仰角（抬头时按 W 会往上飞，见 {@code LuckySelectorEntity#pilotDirection}）
 * @param forward  W
 * @param backward S
 * @param left     A
 * @param right    D
 * @param jump     空格：匀速上升（与其它方向一起参与合成，所以"W+空格"是斜着往上飞）
 */
public record SelectorPilotPayload(int entityId, float yaw, float pitch,
                                   boolean forward, boolean backward, boolean left, boolean right,
                                   boolean jump)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SelectorPilotPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "selector_pilot"));

    /** 五个方向键打包成一个字节（前 5 位），省得写五遍布尔。 */
    private static final int FLAG_FORWARD = 1;
    private static final int FLAG_BACKWARD = 2;
    private static final int FLAG_LEFT = 4;
    private static final int FLAG_RIGHT = 8;
    private static final int FLAG_JUMP = 16;

    public static final StreamCodec<FriendlyByteBuf, SelectorPilotPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeFloat(p.yaw);
                buf.writeFloat(p.pitch);
                buf.writeByte((p.forward ? FLAG_FORWARD : 0)
                    | (p.backward ? FLAG_BACKWARD : 0)
                    | (p.left ? FLAG_LEFT : 0)
                    | (p.right ? FLAG_RIGHT : 0)
                    | (p.jump ? FLAG_JUMP : 0));
            },
            buf -> {
                int entityId = buf.readVarInt();
                float yaw = buf.readFloat();
                float pitch = buf.readFloat();
                int flags = buf.readUnsignedByte();
                return new SelectorPilotPayload(entityId, yaw, pitch,
                    (flags & FLAG_FORWARD) != 0,
                    (flags & FLAG_BACKWARD) != 0,
                    (flags & FLAG_LEFT) != 0,
                    (flags & FLAG_RIGHT) != 0,
                    (flags & FLAG_JUMP) != 0);
            }
        );

    @Override
    public Type<SelectorPilotPayload> type() {
        return TYPE;
    }
}
