package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 酿造台权杖模式同步（服务端→客户端）。同时携带两个正交维度：
 * <ul>
 *   <li>{@code form}：0=药瓶，1=药水云；</li>
 *   <li>{@code category}：0=buff，1=debuff，2=变形。</li>
 * </ul>
 * 客户端据此在屏幕中下方渲染两行模式文本（form 在下方）。
 */
public record BrewingStaffModePayload(int form, int category) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BrewingStaffModePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "brewing_staff_mode"));

    public static final StreamCodec<FriendlyByteBuf, BrewingStaffModePayload> STREAM_CODEC =
        CustomPacketPayload.codec(BrewingStaffModePayload::write, BrewingStaffModePayload::new);

    private BrewingStaffModePayload(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.form);
        buf.writeInt(this.category);
    }

    @Override
    public Type<BrewingStaffModePayload> type() {
        return TYPE;
    }
}