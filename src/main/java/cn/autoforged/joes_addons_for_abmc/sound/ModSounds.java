package cn.autoforged.joes_addons_for_abmc.sound;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, ModMain.MODID);

    public static final Supplier<SoundEvent> EAR_RINGING = SOUNDS.register("ear_ringing",
        () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "ear_ringing")));

    public static final Supplier<SoundEvent> STAFF_COMMAND_FLY = SOUNDS.register("staff_command_fly",
        () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "staff_command_fly")));

    public static final Supplier<SoundEvent> VILLAGER_BIG_BAND = SOUNDS.register("villagerbigband",
        () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "villagerbigband")));

    public static final Supplier<SoundEvent> ZOMBIES = SOUNDS.register("zombies",
        () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "zombies")));

    public static final Supplier<SoundEvent> WANDERING_TRADER = SOUNDS.register("wanderingtrader",
        () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "wanderingtrader")));

    public static final Supplier<SoundEvent> GREEN_AND_PURPLE = SOUNDS.register("greenandpurple",
        () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "greenandpurple")));

    public static final Supplier<SoundEvent> PURPLE_THEME = SOUNDS.register("purplestheme",
        () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "purplestheme")));

    public static final Supplier<SoundEvent> LASER_START = SOUNDS.register("laser_start",
        () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "laser_start")));

    public static final Supplier<SoundEvent> LASER_MIDDLE = SOUNDS.register("laser_middle",
        () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "laser_middle")));

    public static final Supplier<SoundEvent> LASER_END = SOUNDS.register("laser_end",
        () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "laser_end")));
}
