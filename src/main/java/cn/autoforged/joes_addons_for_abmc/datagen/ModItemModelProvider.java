package cn.autoforged.joes_addons_for_abmc.datagen;

import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, String modid, ExistingFileHelper existingFileHelper) {
        super(output, modid, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        handheldItem(ModItems.GLISTERING_MELON_KNIFE.get());
    }
}
