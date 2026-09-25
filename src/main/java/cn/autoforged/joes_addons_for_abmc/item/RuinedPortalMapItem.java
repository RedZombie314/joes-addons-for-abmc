package cn.autoforged.joes_addons_for_abmc.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 废弃传送门藏宝图 (Ruined Portal Map)。
 *
 * 使用后，若所处世界为主世界或下界，则尝试定位最近的废弃传送门并生成一张指向它的地图；
 * 若无法定位（末地/其它维度，或该范围内没有废弃传送门）则保持为空地图并提示"无法定位废弃传送门"。
 *
 * 继承 vanilla 的 {@link MapItem}（已填地图的原版 Item，原版无 FilledMapItem，地图数据/更新包都走 MapItem），
 * 通过 {@link DataComponents#MAP_ID} 保存地图 id（MapId）。没有该数据组件时物品呈现空地图样貌
 * （模型复用原版 filled_map / minecraft:item/map 贴图）；获得地图 id 后即"变成藏宝图"。
 */
public class RuinedPortalMapItem extends MapItem {

    private static final Logger LOGGER = LoggerFactory.getLogger("joes_addons_for_abmc.ruined_portal_map");

    /** 最近废弃传送门搜索半径（区块）。 */
    private static final int SEARCH_RADIUS = 10000;

    /** 废弃传送门结构标签（data/minecraft/tags/worldgen/structure/ruined_portal.json）。 */
    private static final TagKey<Structure> RUINED_PORTAL_TAG =
        TagKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("ruined_portal"));

    public RuinedPortalMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        LOGGER.info("[ruinedportal] use called dim={} pos={} hand={}", level.dimension().location(),
            player.blockPosition(), hand);
        ItemStack stack = player.getItemInHand(hand);

        // 已经是藏宝图，不做任何处理
        if (stack.has(DataComponents.MAP_ID)) {
            LOGGER.info("[ruinedportal] already a filled map, no-op");
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide) {
            // 服务端做实际定位并输出消息
            return InteractionResultHolder.success(stack);
        }

        // 只允许主世界 / 下界定位
        if (level.dimension() != Level.OVERWORLD && level.dimension() != Level.NETHER) {
            LOGGER.info("[ruinedportal] wrong dimension {}, cannot locate", level.dimension().location());
            player.displayClientMessage(Component.literal("Cannot find ruined portal"), false);
            return InteractionResultHolder.pass(stack);
        }

        ServerLevel serverLevel = (ServerLevel) level;
        BlockPos playerPos = player.blockPosition();

        BlockPos portalPos = serverLevel.findNearestMapStructure(RUINED_PORTAL_TAG, playerPos, SEARCH_RADIUS, false);
        LOGGER.info("[ruinedportal] findNearestMapStructure result portalPos={}", portalPos);
        if (portalPos == null) {
            player.displayClientMessage(Component.literal("Cannot find ruined portal"), false);
            return InteractionResultHolder.pass(stack);
        }

        // 仿照原版藏宝图：以废弃传送门为中心生成一张地图（zoom 2），把结构框在地图中央并打上红 X
        ItemStack createdMap = MapItem.create(serverLevel, portalPos.getX(), portalPos.getZ(), (byte) 2, true, false);
        var mapId = createdMap.get(DataComponents.MAP_ID);
        LOGGER.info("[ruinedportal] MapItem.create done mapId={}", mapId);
        if (mapId == null) {
            player.displayClientMessage(Component.literal("Cannot find ruined portal"), false);
            return InteractionResultHolder.pass(stack);
        }
        stack.set(DataComponents.MAP_ID, mapId);

        MapItemSavedData data = MapItem.getSavedData(mapId, serverLevel);
        if (data != null) {
            data.addDecoration(
                MapDecorationTypes.TARGET_X,
                serverLevel,
                "ruined_portal",
                (double) portalPos.getX(),
                (double) portalPos.getZ(),
                0.0,
                null
            );
        }
        LOGGER.info("[ruinedportal] decoration added, map located at {}", portalPos);

        player.displayClientMessage(
            Component.literal("Located nearest ruined portal at " + portalPos.getX() + ", " + portalPos.getZ()),
            false
        );
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (stack.has(DataComponents.MAP_ID)) {
            tooltip.add(
                Component.translatable("item.joes_addons_for_abmc.ruined_portal_map.located").withStyle(ChatFormatting.GREEN)
            );
        }
    }
}