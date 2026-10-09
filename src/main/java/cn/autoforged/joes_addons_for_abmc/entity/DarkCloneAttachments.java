package cn.autoforged.joes_addons_for_abmc.entity;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 「黑暗分身」标记所用的 NeoForge 数据附件（Data Attachment）。
 *
 * <p><b>为什么不用同步实体数据（SynchedEntityData）</b>：{@code SynchedEntityData#defineId} 需要一个
 * 在实体类层次里全局唯一的 id，而 id 是在各类自己的 {@code <clinit>} 阶段领取的，框架层无法在
 * 「所有生物（含其它 mod 生物）」这个粒度上插进去——这正是 BeeBoss 当初踩过的坑
 * （{@code index 19 out of bounds for length 19}）。数据附件挂在注册表里，不占用实体数据 id 空间。
 *
 * <p><b>为什么它对其它 mod 的生物也自动生效</b>：{@code Entity} 直接继承
 * {@code net.neoforged.neoforge.attachment.AttachmentHolder}（Entity.java:131），
 * 所以任何生物——原版或其它 mod——都是附件持有者，无需为任何 mod 写适配。
 *
 * <p><b>同步</b>：只要求注册 {@code sync(...)} 即可，NeoForge 会在四个时机自动下发
 * （见 {@code AttachmentSyncHandler} 的 javadoc）：玩家开始追踪该实体时首次同步、
 * {@code getData} 默认创建时、{@code setData} 更新时、{@code removeData} 移除时。
 * 因此客户端不需要任何自定义网络包。
 *
 * <p><b>为什么序列化</b>：标记随存档保存，读档后仍是黑暗分身，行为层才能在
 * {@code EntityJoinLevelEvent} 里把索敌 goal 重新挂上（goal 本身不随存档保存）。
 */
public final class DarkCloneAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ModMain.MODID);

    /** 「这是黑暗分身」标记。默认 {@code false}，只对真正的分身写入 {@code true}。 */
    public static final Supplier<AttachmentType<Boolean>> DARK_CLONE =
        ATTACHMENT_TYPES.register("dark_clone", () -> AttachmentType.builder(() -> Boolean.FALSE)
            .serialize(Codec.BOOL)
            .sync(ByteBufCodecs.BOOL)
            .build());

    private DarkCloneAttachments() {
    }

    /**
     * 判断是否为黑暗分身。<b>刻意用 {@code hasData} 先探一次</b>：{@code getData} 在附件不存在时
     * 会「默认创建」它，而该附件是同步附件——若在伤害/掉落这类全局事件里直接 {@code getData}，
     * 会给世界上每一个实体都凭空创建一个附件并触发一轮同步包。
     */
    public static boolean isDarkClone(Entity entity) {
        return entity != null && entity.hasData(DARK_CLONE) && Boolean.TRUE.equals(entity.getData(DARK_CLONE));
    }

    public static void setDarkClone(Entity entity, boolean value) {
        if (value) {
            entity.setData(DARK_CLONE, Boolean.TRUE);
        } else {
            entity.removeData(DARK_CLONE);
        }
    }
}
