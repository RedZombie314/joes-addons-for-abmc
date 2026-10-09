package cn.autoforged.joes_addons_for_abmc.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 开放 {@link Mob} 的 {@code persistenceRequired} 字段（原 private）的写入访问。
 * <p>
 * 用于幸运方块选择器：它需要把 {@code PersistenceRequired} <b>来回切</b>——
 * 内含物品时置 true（不能被刷掉），把东西 send 走之后再置回 false。
 * 而原版只提供了置 true 的 {@code Mob#setPersistenceRequired()}（Mob.java:1206，没有带参数的版本），
 * 置 false 只能直接写这个字段，所以这里开一个 accessor。
 * 写法与既有的 {@link ExperienceOrbAccessor} 一致。
 */
@Mixin(Mob.class)
public interface MobPersistenceAccessor {

    @Accessor("persistenceRequired")
    void jafa_setPersistenceRequired(boolean value);
}
