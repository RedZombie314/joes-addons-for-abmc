package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.worldgen.ModDimensions;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/**
 * Creeper Clan 维度的客户端特效注册。
 *
 * 天空颜色并非由 DimensionSpecialEffects 提供（1.21.1 已移除 computeSkyColor，
 * 天空色由 ClientLevel.getSkyColor 依据生物群系 + 昼夜/天气计算），因此这里的
 * 特效与主世界一致（SkyType.NORMAL 保证渲染天空、雾色与主世界相同），
 * 而固定绿色天空（#79B83F）由 {@link cn.autoforged.joes_addons_for_abmc.mixin.ClientLevelMixin}
 * 在该维度直接返回固定颜色实现。
 *
 * 雾色：覆写 {@link #getBrightnessDependentFogColor} 让本维度空气（雾）呈绿色调，
 * 与天空呼应，形成「整个空气弥漫绿色气息」的视觉效果（方块本身不被染色，
 * 只是远近的空气/雾被染上绿色滤镜）。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = ModMain.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class CreeperClanDimensionEffects {

    /** 本维度空气/雾的目标绿色 #79B83F（RGB 归一化）。 */
    private static final double R = 0x79 / 255.0;
    private static final double G = 0xB8 / 255.0;
    private static final double B = 0x3F / 255.0;

    private CreeperClanDimensionEffects() {
    }

    @SubscribeEvent
    public static void registerEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ModDimensions.CREEPER_CLAN_EFFECTS, new DimensionSpecialEffects(
                192.0F, true, DimensionSpecialEffects.SkyType.NORMAL, false, false) {
            @Override
            public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float brightness) {
                // 以绿色为主，随昼夜亮度微调（白天稍亮、夜晚稍暗），让空气始终泛绿但不至于刺眼
                double m = 0.55F + 0.45F * brightness;
                return new Vec3(R * m, G * m, B * m);
            }

            @Override
            public boolean isFoggyAt(int x, int y) {
                return false;
            }
        });
    }
}
