package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：玩家按下左键（攻击键）时，请求把准星所指生物记录进黑暗分身召唤池。
 *
 * <p>为什么必须走网络包而不能在服务端直接判断左键：原版客户端只在
 * <b>准星命中且目标在交互距离内</b>时才发 {@code ServerboundInteractPacket}，
 * 因此 {@code AttackEntityEvent} 在服务端收到的距离天然被限制在 ~3 格，
 * 无法满足「最远 256 格」。左键本身是纯客户端输入，只能由客户端上报。
 *
 * <p>包体为空：距离、姿态与准星射线都由服务端自行计算，客户端不上报任何可信数据。
 */
public record DarkCloneRecordPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DarkCloneRecordPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "dark_clone_record"));

    public static final StreamCodec<FriendlyByteBuf, DarkCloneRecordPayload> STREAM_CODEC =
        CustomPacketPayload.codec(DarkCloneRecordPayload::write, DarkCloneRecordPayload::new);

    private DarkCloneRecordPayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public Type<DarkCloneRecordPayload> type() {
        return TYPE;
    }
}
