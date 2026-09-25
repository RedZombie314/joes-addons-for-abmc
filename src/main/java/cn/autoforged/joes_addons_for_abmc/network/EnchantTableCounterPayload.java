package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 附魔台计数器同步（服务端→客户端）：把某坐标附魔台的剩余次数发给客户端，
 * 供客户端在“计数器归零”时抑制附魔台上方书的渲染。
 */
public record EnchantTableCounterPayload(String dimId, int x, int y, int z, int count)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EnchantTableCounterPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "enchant_table_counter"));

    public static final StreamCodec<FriendlyByteBuf, EnchantTableCounterPayload> STREAM_CODEC =
        CustomPacketPayload.codec(EnchantTableCounterPayload::write, EnchantTableCounterPayload::new);

    private EnchantTableCounterPayload(FriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.dimId);
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeInt(this.count);
    }

    @Override
    public Type<EnchantTableCounterPayload> type() {
        return TYPE;
    }
}
