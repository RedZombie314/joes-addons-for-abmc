package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 铁块权杖（服务端→客户端）：铁链钩取的状态同步。
 * <p>
 * 开始钩取时发送一次（携带 mode 与起点/终点坐标）；拉取过程中周期性更新（此时客户端忽略 mode），
 * 客户端据此持续渲染“玩家发射点 → 目标当前点”的铁链（十字贴图沿连线排列）。
 * 起点由客户端跟随玩家实时重算（移动/转头铁链跟手），终点为目标（生物/物品）当前坐标。
 *
 * @param mode 目标类型：{@link #MODE_ITEM} 物品/掉落物（拉到玩家脚下）；{@link #MODE_LIVING} 生物（拉到停止距离后甩出）
 * @param sx  发射起点 x
 * @param sy  发射起点 y
 * @param sz  发射起点 z
 * @param ex  目标当前 x
 * @param ey  目标当前 y
 * @param ez  目标当前 z
 * @param entityId  被钩取的目标实体 id（客户端据此跟踪其实时渲染位置作为链端；-1 表示无）
 */
public record ChainGrabPayload(int mode, double sx, double sy, double sz,
                               double ex, double ey, double ez,
                               int entityId) implements CustomPacketPayload {

    /** 物品/掉落物（含被缴械的物品）：拉到玩家脚下停住。 */
    public static final int MODE_ITEM = 0;
    /** 生物：拉到停止距离后断开并保持速度惯性甩出。 */
    public static final int MODE_LIVING = 1;

    public static final CustomPacketPayload.Type<ChainGrabPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "chain_grab"));

    public static final StreamCodec<FriendlyByteBuf, ChainGrabPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.mode);
                buf.writeDouble(p.sx);
                buf.writeDouble(p.sy);
                buf.writeDouble(p.sz);
                buf.writeDouble(p.ex);
                buf.writeDouble(p.ey);
                buf.writeDouble(p.ez);
                buf.writeVarInt(p.entityId);
            },
            buf -> new ChainGrabPayload(
                buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readVarInt())
        );

    @Override
    public Type<ChainGrabPayload> type() {
        return TYPE;
    }
}
