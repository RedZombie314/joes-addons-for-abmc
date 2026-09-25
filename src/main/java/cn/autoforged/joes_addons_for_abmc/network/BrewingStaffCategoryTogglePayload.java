package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 酿造台权杖「buff/debuff/变形」模式切换请求（客户端→服务端），由按住左Alt滚动滚轮触发。
 * 携带滚轮方向：{@code direction} 为 -1 表示切换到上一个类别（向上滚动），
 * +1 表示切换到下一个类别（向下滚动），循环（buff→debuff→变形→buff）。
 */
public record BrewingStaffCategoryTogglePayload(int direction) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BrewingStaffCategoryTogglePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "brewing_staff_category_toggle"));

    public static final StreamCodec<FriendlyByteBuf, BrewingStaffCategoryTogglePayload> STREAM_CODEC =
        CustomPacketPayload.codec(BrewingStaffCategoryTogglePayload::write, BrewingStaffCategoryTogglePayload::new);

    private BrewingStaffCategoryTogglePayload(FriendlyByteBuf buf) {
        this(buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.direction);
    }

    @Override
    public Type<BrewingStaffCategoryTogglePayload> type() {
        return TYPE;
    }
}