package cn.autoforged.joes_addons_for_abmc.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 开放 {@link ExperienceOrb#value}（原 private）的写入访问。
 * <p>
 * 用于「经验修补·方块耐久」：玩家拾取经验球时，把修复方块耐久所消耗的经验
 * 直接从经验球的价值中扣除，使剩余的球值再走原版经验授予与附魔修复流程，
 * 保证经验记账与原版一致（修复优先于入账）。
 */
@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccessor {

    @Accessor("value")
    void jafa_setValue(int value);
}
