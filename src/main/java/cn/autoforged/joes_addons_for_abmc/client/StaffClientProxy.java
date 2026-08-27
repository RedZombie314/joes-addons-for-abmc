package cn.autoforged.joes_addons_for_abmc.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class StaffClientProxy {
    public static void openCommandStaffScreen() {
        Minecraft.getInstance().setScreen(new CommandStaffChooseScreen());
    }
}
