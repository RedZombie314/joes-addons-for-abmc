package cn.autoforged.joes_addons_for_abmc.item;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

/**
 * 本模组的护甲材质（1.21.1 里 {@link ArmorMaterial} 是一个注册表）。
 */
public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
        DeferredRegister.create(Registries.ARMOR_MATERIAL, ModMain.MODID);

    /**
     * 工作台帽子的护甲材质：
     * <ul>
     *   <li>防御力表为空（{@code Map.of()}）→ 各部位防御力都是 0，<b>不提供护甲值</b>；</li>
     *   <li>贴图层指向<b>本模组的全透明贴图</b>
     *       （{@code textures/models/armor/crafting_table_hat_layer_1.png}，64×32 全透明）：
     *       戴在头上时，原版那层头盔贴图<b>什么都不画</b>。</li>
     * </ul>
     *
     * <p><b>为什么用全透明贴图而不是原版 {@code leather}</b>：帽子的外观现在由<b>物品模型</b>负责
     * （Blockbench 导出的 3D 模型，见 {@code client/CraftingHatLayer}，戴在头上时用
     * {@code ItemDisplayContext.HEAD} 画出来）。如果这层还指着 {@code minecraft:leather}，
     * 头上会同时出现"皮革头盔 + 工作台"两顶帽子。换成透明贴图后，护甲层等于不存在，
     * 头盔槽里看到的就只有那个模型。
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CRAFTING_TABLE_HAT =
        ARMOR_MATERIALS.register("crafting_table_hat", () -> new ArmorMaterial(
            Map.of(),                       // 各部位防御力：全 0（= 不提供护甲值）
            0,                              // 附魔性
            SoundEvents.ARMOR_EQUIP_LEATHER,
            () -> Ingredient.EMPTY,         // 无修理材料
            List.of(new ArmorMaterial.Layer(
                ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "crafting_table_hat"))),
            0.0F,                           // 盔甲韧性
            0.0F                            // 击退抗性
        ));

    private ModArmorMaterials() {
    }
}
