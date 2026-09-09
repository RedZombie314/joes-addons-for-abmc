package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：请求播放一段“Omega 着色器”静态测试（后处理：3D 点汇聚到屏幕中心）。
 * 由 {@code /jafa omega_shader} 命令触发。空载荷，仅作为触发信号；种子与开始时间由客户端在
 * 收到信号时本地生成（替代 Shadertoy 的 Buffer A 跨帧状态）。
 */
public record OmegaShaderPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OmegaShaderPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "omega_shader"));

    public static final StreamCodec<FriendlyByteBuf, OmegaShaderPayload> STREAM_CODEC =
        CustomPacketPayload.codec(OmegaShaderPayload::write, OmegaShaderPayload::new);

    private OmegaShaderPayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public Type<OmegaShaderPayload> type() {
        return TYPE;
    }
}