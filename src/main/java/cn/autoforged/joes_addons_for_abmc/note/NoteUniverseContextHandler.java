package cn.autoforged.joes_addons_for_abmc.note;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.network.NoteMusicContextPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = ModMain.MODID, bus = EventBusSubscriber.Bus.GAME)
public class NoteUniverseContextHandler {

    private static final ResourceLocation NOTE_DIMENSION =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "note_block_universe");

    private static final TagKey<Structure> VILLAGE_TAG =
        TagKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("village"));

    private static final int SCAN_RADIUS_CHUNKS = 14;
    private static final int CHECK_INTERVAL = 80;

    private static final Map<ServerPlayer, Byte> lastSent = new IdentityHashMap<>();
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // 服务器主线程运行期间，event.getServer() 一定非空
        if (event.getServer() == null) return;
        tickCounter++;
        if (tickCounter % CHECK_INTERVAL != 0) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel serverLevel)) continue;
            if (!player.level().dimension().location().equals(NOTE_DIMENSION)) {
                // 离开音符方块宇宙时，若之前发送过特殊情境则发送 NONE，让客户端恢复原版音乐
                Byte previous = lastSent.remove(player);
                if (previous != null && previous != NoteMusicContextPayload.CONTEXT_NONE) {
                    sendTo(player, NoteMusicContextPayload.CONTEXT_NONE);
                }
                continue;
            }

            byte context = computeContext(player, serverLevel);
            Byte previous = lastSent.get(player);
            if (previous == null || previous != context) {
                lastSent.put(player, context);
                sendTo(player, context);
            }
        }
    }

    private static void sendTo(ServerPlayer player, byte context) {
        PacketDistributor.sendToPlayer(player, new NoteMusicContextPayload(context));
    }

    /**
     * 计算玩家的特殊情境，优先级：④ 高空 > ① 村庄 > ③ 流浪商人 > ② 水下。
     */
    private static byte computeContext(ServerPlayer player, ServerLevel serverLevel) {
        BlockPos pos = player.blockPosition();

        // ④ 玩家高度高于 200 格
        if (player.getY() > 200.0) {
            return NoteMusicContextPayload.CONTEXT_HIGH;
        }

        // ① 位于村庄结构中或距离村庄结构 100 格以内
        if (isNearVillage(serverLevel, pos)) {
            return NoteMusicContextPayload.CONTEXT_VILLAGE;
        }

        // ③ 玩家在流浪商人 10 格范围内
        if (isNearWanderingTrader(serverLevel, pos)) {
            return NoteMusicContextPayload.CONTEXT_TRADER;
        }

        // ② 海平面高度以下且不位于海洋（周围 5*5*5 范围内水源方块数量不超过 12）
        if (player.getY() < 63.0 && countWaterAround(serverLevel, pos) < 12) {
            return NoteMusicContextPayload.CONTEXT_UNDERWATER;
        }

        return NoteMusicContextPayload.CONTEXT_NONE;
    }

    private static boolean isNearVillage(ServerLevel serverLevel, BlockPos pos) {
        ChunkPos center = new ChunkPos(pos);
        int r = SCAN_RADIUS_CHUNKS;
        for (int cx = center.x - r; cx <= center.x + r; cx++) {
            for (int cz = center.z - r; cz <= center.z + r; cz++) {
                // 绝不强制生成区块：仅检查已生成到 STRUCTURE_STARTS 的区块，未生成则跳过
                ChunkAccess chunk = serverLevel.getChunkSource()
                    .getChunk(cx, cz, ChunkStatus.STRUCTURE_STARTS, false);
                if (chunk == null) continue;
                for (StructureStart start : chunk.getAllStarts().values()) {
                    if (start.isValid()
                        && isVillage(serverLevel, start.getStructure())
                        && start.getBoundingBox().inflatedBy(100).isInside(pos.getX(), pos.getY(), pos.getZ())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isVillage(ServerLevel serverLevel, Structure structure) {
        net.minecraft.core.Registry<Structure> registry =
            serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE);
        return registry.getResourceKey(structure)
            .flatMap(registry::getHolder)
            .map(holder -> holder.is(VILLAGE_TAG))
            .orElse(false);
    }

    private static boolean isNearWanderingTrader(ServerLevel serverLevel, BlockPos pos) {
        AABB box = new AABB(pos).inflate(10.0);
        return !serverLevel.getEntitiesOfClass(WanderingTrader.class, box).isEmpty();
    }

    private static int countWaterAround(ServerLevel serverLevel, BlockPos pos) {
        int count = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (serverLevel.getBlockState(pos.offset(dx, dy, dz)).is(Blocks.WATER)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}