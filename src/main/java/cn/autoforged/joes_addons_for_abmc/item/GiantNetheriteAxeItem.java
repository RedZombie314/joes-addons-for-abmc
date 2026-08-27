package cn.autoforged.joes_addons_for_abmc.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public class GiantNetheriteAxeItem extends AxeItem {
    private static final ResourceLocation BLOCK_REACH_ID =
        ResourceLocation.fromNamespaceAndPath("joes_addons_for_abmc", "giant_netherite_axe_block_reach");
    private static final ResourceLocation ENTITY_REACH_ID =
        ResourceLocation.fromNamespaceAndPath("joes_addons_for_abmc", "giant_netherite_axe_entity_reach");

    public GiantNetheriteAxeItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 74.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED,
                new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -3.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(Attributes.BLOCK_INTERACTION_RANGE,
                new AttributeModifier(BLOCK_REACH_ID, 10.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.HAND)
            .add(Attributes.ENTITY_INTERACTION_RANGE,
                new AttributeModifier(ENTITY_REACH_ID, 10.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.HAND)
            .build();
    }
}
