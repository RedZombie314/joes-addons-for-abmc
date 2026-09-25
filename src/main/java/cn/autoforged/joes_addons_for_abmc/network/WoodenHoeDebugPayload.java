package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 木锄调试 holdable structure 实体的输入（客户端 → 服务器）。
 * action：
 *  - ACTION_CYCLE    : 左键点按，切换到下一个可调整的值（0..8 循环）
 *  - ACTION_INCREASE : 右键按住（每刻发送），增加当前值（角度在 0~360 循环，布尔则置 true）
 *  - ACTION_DECREASE : shift+右键按住（每刻发送），减小当前值（角度减、偏移减）
 *  - ACTION_SAVE     : 调试结束（换物品/不再瞄准）时发送一次，输出当前 9 个值到聊天框
 */
public record WoodenHoeDebugPayload(int entityId, int action) implements CustomPacketPayload {
    public static final int ACTION_CYCLE = 0;
    public static final int ACTION_INCREASE = 1;
    public static final int ACTION_DECREASE = 2;
    public static final int ACTION_SAVE = 3;

    public static final CustomPacketPayload.Type<WoodenHoeDebugPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "wooden_hoe_debug"));

    public static final StreamCodec<FriendlyByteBuf, WoodenHoeDebugPayload> STREAM_CODEC =
        CustomPacketPayload.codec(WoodenHoeDebugPayload::write, WoodenHoeDebugPayload::new);

    private WoodenHoeDebugPayload(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeInt(this.action);
    }

    @Override
    public Type<WoodenHoeDebugPayload> type() {
        return TYPE;
    }
}