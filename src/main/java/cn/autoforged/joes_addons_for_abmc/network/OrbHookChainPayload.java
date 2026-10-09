package cn.autoforged.joes_addons_for_abmc.network;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：<b>附体空壳的铁抓钩铁链</b>（用户指定）。
 *
 * <p>铁链不落实体，只在两个实体之间画：这条包把"哪具空壳"和"哪位玩家"发给附近玩家，
 * 客户端每帧读这两个实体的实时位置、在它们之间铺一串铁链链节
 * （贴图与画法与玩家自己用铁抓钩那条链完全一致，见 {@code ClientEvents#renderOrbHookChain}）。
 *
 * <p>拉扯期间会周期性重发（让中途进入视野的玩家也能看到），拉扯结束时发一条
 * {@link #clear()} 把链收掉。
 *
 * @param shellId  甩钩的那具空壳实体 id；{@link #NONE} = 这条链没了（清掉渲染）
 * @param targetId 被拉的玩家实体 id；{@link #NONE} = 同上
 */
public record OrbHookChainPayload(int shellId, int targetId) implements CustomPacketPayload {

    /** "没有铁链"的哨兵值：两个字段都用它表示清除。 */
    public static final int NONE = -1;

    public static final CustomPacketPayload.Type<OrbHookChainPayload> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "orb_hook_chain"));

    public static final StreamCodec<FriendlyByteBuf, OrbHookChainPayload> STREAM_CODEC =
        CustomPacketPayload.codec(OrbHookChainPayload::write, OrbHookChainPayload::new);

    /** "把这条铁链收掉"。 */
    public static OrbHookChainPayload clear() {
        return new OrbHookChainPayload(NONE, NONE);
    }

    /** "把这一具空壳那条铁链收掉"（同一时刻可能有好几具空壳在钩人，所以收链也要按空壳分）。 */
    public static OrbHookChainPayload remove(int shellId) {
        return new OrbHookChainPayload(shellId, NONE);
    }

    private OrbHookChainPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.shellId);
        buf.writeVarInt(this.targetId);
    }

    @Override
    public CustomPacketPayload.Type<OrbHookChainPayload> type() {
        return TYPE;
    }
}
