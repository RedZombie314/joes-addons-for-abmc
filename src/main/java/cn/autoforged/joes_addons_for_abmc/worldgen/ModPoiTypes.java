package cn.autoforged.joes_addons_for_abmc.worldgen;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.block.ModBlocks;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;
import java.util.function.Supplier;

/**
 * 音符传送门的 POI 类型。
 *
 * 原版下界传送门正是通过 POI（Point of Interest）系统记录已存在的传送门位置，
 * 以便在传送时复用距离最近的传送门。这里为音符传送门注册一个等效的 POI 类型，
 * 使原版的传送门查找逻辑可以直接套用。
 */
public class ModPoiTypes {
    public static final DeferredRegister<PoiType> POI_TYPES =
        DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, ModMain.MODID);

    public static final ResourceKey<PoiType> NOTE_PORTAL =
        ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "note_portal"));

    public static final Supplier<PoiType> NOTE_PORTAL_POI = POI_TYPES.register("note_portal", () -> {
        Set<net.minecraft.world.level.block.state.BlockState> states = ImmutableSet.copyOf(
            ModBlocks.NOTE_PORTAL.get().getStateDefinition().getPossibleStates());
        return new PoiType(states, 0, 1);
    });

    /** Creeper Clan 传送门的 POI 类型。 */
    public static final ResourceKey<PoiType> CREEPER_PORTAL =
        ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "creeper_portal"));

    public static final Supplier<PoiType> CREEPER_PORTAL_POI = POI_TYPES.register("creeper_portal", () -> {
        Set<net.minecraft.world.level.block.state.BlockState> states = ImmutableSet.copyOf(
            ModBlocks.CREEPER_PORTAL.get().getStateDefinition().getPossibleStates());
        return new PoiType(states, 0, 1);
    });
}
