package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：<b>画一条守卫者激光</b>（起点 → 终点、颜色、持续刻数）。
 *
 * <h2>为什么不像以前那样用一个实体</h2>
 * 用户要求："可以参考原版守卫者的代码（也可以参考音符盒权杖的音符攻击，那种攻击就没涉及到任何实体），
 * 直接渲染一条激光，接触到激光的就触发判定。"
 * <p>
 * 所以光波不再是会飞的实体：
 * <ul>
 *   <li><b>判定</b>在服务端一次性完成（{@code OrbGuardianLaser#fire}：先被方块挡住定下射程，
 *       再在这段里找第一个该打的生物，沿途的掉落物一并摧毁），命中就是命中的那一刻，
 *       没有任何实体参与；</li>
 *   <li><b>视觉</b>就是这一条包：把"哪条线、什么颜色、亮多久"发给附近的玩家，
 *       客户端 {@code GuardianLaserClient} 在那段时间里把它画成一条正对镜头的守卫者激光贴图。</li>
 * </ul>
 * 终点是<b>服务端算好的</b>（射线被谁挡住、打到哪儿），客户端只负责照着画，不做任何判定。
 *
 * @param startX  起点（发射那一刻空壳的眼位）
 * @param startY  起点
 * @param startZ  起点
 * @param endX    终点（打中生物的位置 / 射程尽头）
 * @param endY    终点
 * @param endZ    终点
 * @param color   颜色（0xRRGGBB，每条随机）
 * @param ticks   持续多少刻（之后客户端不再画）
 */
public record GuardianLaserPayload(double startX, double startY, double startZ,
                                   double endX, double endY, double endZ,
                                   int color, int ticks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GuardianLaserPayload> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "guardian_laser"));

    public static final StreamCodec<FriendlyByteBuf, GuardianLaserPayload> STREAM_CODEC =
        CustomPacketPayload.codec(GuardianLaserPayload::write, GuardianLaserPayload::new);

    private GuardianLaserPayload(FriendlyByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readInt(), buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeDouble(this.startX);
        buf.writeDouble(this.startY);
        buf.writeDouble(this.startZ);
        buf.writeDouble(this.endX);
        buf.writeDouble(this.endY);
        buf.writeDouble(this.endZ);
        buf.writeInt(this.color);
        buf.writeVarInt(this.ticks);
    }

    @Override
    public CustomPacketPayload.Type<GuardianLaserPayload> type() {
        return TYPE;
    }
}
