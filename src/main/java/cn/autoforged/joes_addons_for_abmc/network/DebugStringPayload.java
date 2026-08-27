package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：请求在玩家周围渲染调试端点与线段。
 * <p>
 * 携带 2~3 个端点的世界坐标。客户端在每点处渲染一个 whitedot，并在每对端点之间渲染
 * 被拉伸的 string 贴图（3 个端点时组成三角形）。端点坐标由 {@code /jafa debug_string}
 * 随机生成。
 */
public record DebugStringPayload(double[][] points) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DebugStringPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "debug_string"));

    public static final StreamCodec<FriendlyByteBuf, DebugStringPayload> STREAM_CODEC =
        CustomPacketPayload.codec(DebugStringPayload::write, DebugStringPayload::new);

    private DebugStringPayload(FriendlyByteBuf buf) {
        this(readPoints(buf));
    }

    private static double[][] readPoints(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        double[][] pts = new double[n][3];
        for (int i = 0; i < n; i++) {
            pts[i][0] = buf.readDouble();
            pts[i][1] = buf.readDouble();
            pts[i][2] = buf.readDouble();
        }
        return pts;
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(points.length);
        for (double[] p : points) {
            buf.writeDouble(p[0]);
            buf.writeDouble(p[1]);
            buf.writeDouble(p[2]);
        }
    }

    @Override
    public Type<DebugStringPayload> type() {
        return TYPE;
    }
}