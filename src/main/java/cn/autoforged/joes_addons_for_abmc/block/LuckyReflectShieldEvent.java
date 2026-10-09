package cn.autoforged.joes_addons_for_abmc.block;

import cn.autoforged.joes_addons_for_abmc.entity.ModEntities;
import cn.autoforged.joes_addons_for_abmc.entity.ReflectingShieldDisplay;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;

/**
 * 幸运实体子事件：<b>会反弹弹射物的盾牌（item display）</b>（需求 12）。
 *
 * <p>刷出 {@link ReflectingShieldDisplay}：一个<b>正常大小</b>的盾牌物品展示实体，持续 5 秒，
 * 期间把撞进它体积里的弹射物<b>沿径向镜面反弹</b>回去（细节见该实体类）。
 *
 * <h3>位置与朝向</h3>
 * <p>摆在幸运方块上方 {@value #HEIGHT} 格（就一格，正常大小的盾牌悬在方块正上方）。
 * 朝向按"实体面向背离触发玩家的方向"来算 —— 物品展示实体的渲染会把模型绕 Y 轴翻 180 度
 * （{@code DisplayRenderer.ItemDisplayRenderer#renderInner} 里那句 {@code mulPose(Axis.YP.rotation(PI))}），
 * 所以面向背离玩家、盾面就正好朝着玩家；没有玩家（自然生成/爆炸触发）时取随机朝向。
 */
public final class LuckyReflectShieldEvent {

    /** 摆放高度（幸运方块上方多少格）。 */
    public static final int HEIGHT = 1;

    private LuckyReflectShieldEvent() {
    }

    /** 注册到幸运事件表（由 {@link LuckyEvents#registerAll()} 调用），返回注册后的事件对象。 */
    public static LuckyEvent register() {
        LuckyEvent event = LuckyEvent.of(LuckyEvents.id("entity/reflect_shield"), LuckyEventCategory.LUCKY_ENTITY,
            "召唤一个持续 5 秒、会反弹弹射物的盾牌物品展示实体",
            LuckyReflectShieldEvent::spawnShield);
        LuckyEvents.register(event);
        return event;
    }

    private static void spawnShield(ServerLevel level, BlockPos pos, @Nullable Player player) {
        ReflectingShieldDisplay shield = ModEntities.REFLECTING_SHIELD.get().create(level);
        if (shield == null) return;

        double x = pos.getX() + 0.5D;
        double y = pos.getY() + HEIGHT;
        double z = pos.getZ() + 0.5D;
        float yaw = level.random.nextFloat() * 360.0F;
        if (player != null) {
            double dx = x - player.getX();
            double dz = z - player.getZ();
            if (dx * dx + dz * dz > 1.0E-4D) {
                // 实体的 forward = (-sin yaw, 0, cos yaw)，令它等于"背离玩家"的方向
                yaw = (float) Math.toDegrees(Mth.atan2(-dx, dz));
            }
        }

        // 位置/朝向都在 moveTo 里一次写掉；外观（盾牌物品）走物品槽，不需要再动坐标
        // （6.5.7 起去掉了 10 倍放大，所以也不用再靠抬 5 格来避免"埋进地里"）
        shield.configure(new ItemStack(Items.SHIELD));
        shield.moveTo(x, y, z, yaw, 0.0F);
        level.addFreshEntity(shield);
        // 注意 1.21.1 里 SoundEvents.ARMOR_EQUIP_IRON 是 Holder<SoundEvent>，playSound 要的是 SoundEvent，故 .value()
        level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
