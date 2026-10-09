package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.entity.LuckyDimensionSpawner;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * 每游戏刻给幸运维度跑一次自然生成（见 {@link LuckyDimensionSpawner}）。
 *
 * <p><b>为什么挂在 {@code ServerLevel#tick} 而不是原来的 {@code ServerLevel#tickChunk}</b>：
 * {@code tickChunk} 的调用点被原版限制在「区块中心距玩家 128 格以内」
 * （ServerChunkCache.java:367-375），挂在那里就永远只能刷 128 格以内。挂在等级刻上，
 * 扫描半径由我们自己定（{@code LuckyDimensionSpawner} 里三套半径各管各的），
 * 而且完全不改动原版那个 128（它还管着原版的随机刻与刷怪，动它影响面太大）。
 *
 * <p>NeoForge 1.21.1 没有「随机刻事件」，等级刻事件倒是有一个（{@code LevelTickEvent}），
 * 但那个事件只给 {@code Level}，拿不到「哪些区块在刻」这层判断；这里注入 HEAD 更直接，也不依赖事件注册。
 *
 * <p>{@code require = 0}：万一以后 MC 改了签名导致注入不上，也只是不再自然生成，不会让游戏崩。
 */
@Mixin(ServerLevel.class)
public class ServerLevelLuckySpawnMixin {

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"), require = 0)
    private void joes_addons_for_abmc$spawnLuckyDimensionEntities(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        LuckyDimensionSpawner.tickLevel((ServerLevel) (Object) this);
    }
}
