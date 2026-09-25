package cn.autoforged.joes_addons_for_abmc.sound;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, ModMain.MODID);

    /** didgeridoo 十个八度带的基底音源（带 0=最低 … 带 9=最高），索引即八度带号。 */
    public static final List<Supplier<SoundEvent>> DIDGERIDOO_BANDS = new ArrayList<>();

    /** banjo 十个八度带的基底音源，索引即八度带号。 */
    public static final List<Supplier<SoundEvent>> BANJO_BANDS = new ArrayList<>();

    /** bass（贝斯）十个八度带的基底音源，索引即八度带号。 */
    public static final List<Supplier<SoundEvent>> BASS_BANDS = new ArrayList<>();

    /** do（电吉他，C 音色）十个八度带的基底音源，索引即八度带号。 */
    public static final List<Supplier<SoundEvent>> ELECTRIC_GUITAR_BANDS = new ArrayList<>();

    /** 原版吉他（音符盒 guitar）十个八度带的基底音源，索引即八度带号。 */
    public static final List<Supplier<SoundEvent>> GUITAR_BANDS = new ArrayList<>();

    /** 注册 10 个 didgeridoo 八度带 SoundEvent，索引带 0..9。 */
    private static void registerDidgeridooBands(int count) {
        for (int b = 0; b < count; b++) {
            final int band = b;
            String name = "didgeridoo_band_" + band;
            DIDGERIDOO_BANDS.add(SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(ModMain.MODID, name))));
        }
    }

    /** 注册 10 个 banjo 八度带 SoundEvent，索引带 0..9。 */
    private static void registerBanjoBands(int count) {
        for (int b = 0; b < count; b++) {
            final int band = b;
            String name = "banjo_band_" + band;
            BANJO_BANDS.add(SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(ModMain.MODID, name))));
        }
    }

    /** 注册 10 个 bass 八度带 SoundEvent，索引带 0..9。 */
    private static void registerBassBands(int count) {
        for (int b = 0; b < count; b++) {
            final int band = b;
            String name = "bass_band_" + band;
            BASS_BANDS.add(SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(ModMain.MODID, name))));
        }
    }

    /** 注册 10 个 do（电吉他）八度带 SoundEvent，索引带 0..9。do 音源基准为 C（半音 54），时长 2.14s。 */
    private static void registerElectricGuitarBands(int count) {
        for (int b = 0; b < count; b++) {
            final int band = b;
            String name = "electric_guitar_band_" + band;
            ELECTRIC_GUITAR_BANDS.add(SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(ModMain.MODID, name))));
        }
    }

    /** 注册 10 个原版吉他八度带 SoundEvent，索引带 0..9。 */
    private static void registerGuitarBands(int count) {
        for (int b = 0; b < count; b++) {
            final int band = b;
            String name = "guitar_band_" + band;
            GUITAR_BANDS.add(SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(ModMain.MODID, name))));
        }
    }

    static {
        registerDidgeridooBands(10);
        registerBanjoBands(10);
        registerBassBands(10);
        registerElectricGuitarBands(10);
        registerGuitarBands(10);
    }

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
