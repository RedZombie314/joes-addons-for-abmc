package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 蜂巢权杖：对准生物按下攻击键（左键）时的触发信号（客户端→服务端）。
 * 服务端据此进行 128 格预判，命中生物则释放权杖中全部蜜蜂并让其仇视该生物。
 * 空载荷，仅作为触发信号。
 */
public record BeeStaffAttackPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BeeStaffAttackPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "bee_staff_attack"));

    public static final StreamCodec<FriendlyByteBuf, BeeStaffAttackPayload> STREAM_CODEC =
        CustomPacketPayload.codec(BeeStaffAttackPayload::write, BeeStaffAttackPayload::new);

    private BeeStaffAttackPayload(FriendlyByteBuf buf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    @Override
    public Type<BeeStaffAttackPayload> type() {
        return TYPE;
    }
}