package cn.autoforged.joes_addons_for_abmc.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Omega 权杖的功能模式。未来会提供多种能力与切换方式，目前框架先只具备一种：吸收模式。
 * <p>
 * 模式保存在权杖自身的 {@link DataComponents#CUSTOM_DATA} 上（键 {@code omega_staff_mode}），
 * 因此同一玩家可持有不同模式的多个 Omega 权杖互不干扰。后续新增模式时，只需在枚举里追加
 * 并在使用逻辑里按 {@code switch(mode)} 分发即可。
 */
public enum OmegaStaffMode {

    /** 吸收模式：右键并持续瞄准生物，把生物不断拉近玩家，近身后完全删除。 */
    ABSORB;

    /** 模式在物品 CUSTOM_DATA 中的存储键。 */
    private static final String TAG = "omega_staff_mode";

    /** 读取权杖当前模式；未设置时默认吸收模式。 */
    public static OmegaStaffMode get(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(TAG)) {
            int id = tag.getInt(TAG);
            OmegaStaffMode[] all = values();
            if (id >= 0 && id < all.length) {
                return all[id];
            }
        }
        return ABSORB;
    }

    /** 把当前模式写入权杖。 */
    public void set(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
            tag -> tag.putInt(TAG, ordinal()));
    }
}