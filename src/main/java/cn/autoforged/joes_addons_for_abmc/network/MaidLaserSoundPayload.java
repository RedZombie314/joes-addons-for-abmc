package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 红石块权杖（服务端→客户端）：通知客户端某女仆的激光音效事件与光束位置。
 * <p>
 * 音效跟随规则：
 * <ul>
 *   <li>laser_start / laser_end（发射/结束音效）：定位在女仆眼位 {@code (x,y,z)}，离女仆越近越响；</li>
 *   <li>laser_middle（持续音效）：定位在女仆眼位 {@code (x,y,z)} 到光束末端 {@code (endX,endY,endZ)}
 *       的连线上，客户端会把循环音效放在连线离听者最近的点，离线越近越响。</li>
 * </ul>
 *
 * @param maidId 发射激光的女仆实体 id
 * @param action 事件类型：{@link #ACTION_START} 开始 / {@link #ACTION_UPDATE} 更新光束位置 / {@link #ACTION_END} 结束
 * @param x      女仆眼位 x
 * @param y      女仆眼位 y
 * @param z      女仆眼位 z
 * @param endX   光束末端点 x（ACTION_END 时无意义）
 * @param endY   光束末端点 y
 * @param endZ   光束末端点 z
 */
public record MaidLaserSoundPayload(int maidId, int action, double x, double y, double z,
                                    double endX, double endY, double endZ)
    implements CustomPacketPayload {

    /** 开始激光：播放 laser_start 并在女仆→光束末端连线上循环播放 laser_middle。 */
    public static final int ACTION_START = 0;
    /** 更新光束末端位置：让循环音效跟随变化的连线（不播放任何音效）。 */
    public static final int ACTION_UPDATE = 1;
    /** 结束激光：停止循环并播放 laser_end。 */
    public static final int ACTION_END = 2;

    public static final CustomPacketPayload.Type<MaidLaserSoundPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "maid_laser_sound"));

    public static final StreamCodec<FriendlyByteBuf, MaidLaserSoundPayload> STREAM_CODEC =
        CustomPacketPayload.codec(MaidLaserSoundPayload::write, MaidLaserSoundPayload::new);

    private MaidLaserSoundPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(maidId);
        buf.writeVarInt(action);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(endX);
        buf.writeDouble(endY);
        buf.writeDouble(endZ);
    }

    @Override
    public Type<MaidLaserSoundPayload> type() {
        return TYPE;
    }
}
