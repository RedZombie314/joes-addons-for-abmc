package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.config.ModConfig;
import cn.autoforged.joes_addons_for_abmc.network.NoteMusicContextPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/**
 * 音符方块宇宙的专属情境音乐系统。
 *
 * 服务端（NoteUniverseContextHandler）计算玩家所处情境并下发，客户端在此根据情境
 * 淡入/淡出对应的循环音乐：淡出时记住该情境音乐轨道的“播放时间戳”，再次进入同一情境时
 * 沿用同一轨道并从该时间戳处淡入；一直处于特定情境时则持续循环播放该情境音乐。
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME, modid = ModMain.MODID)
public class NoteUniverseMusicClient {

    private static final int FADE_TICKS = 40; // 2 秒淡入/淡出
    private static final ResourceLocation MUSIC_VILLAGE =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "villagerbigband");
    private static final ResourceLocation MUSIC_UNDERWATER =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "zombies");
    private static final ResourceLocation MUSIC_TRADER =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "wanderingtrader");
    private static final ResourceLocation MUSIC_HIGH_A =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "greenandpurple");
    private static final ResourceLocation MUSIC_HIGH_B =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "purplestheme");

    private static final float VOLUME_STEP = 1.0F / FADE_TICKS;

    /** 服务端最近一次下发的情境（网络线程写入，客户端 tick 线程读取）。 */
    private static volatile byte activeContext = NoteMusicContextPayload.CONTEXT_NONE;

    /** 当前正在播放的情境。 */
    private static byte playingContext = NoteMusicContextPayload.CONTEXT_NONE;
    private static LoopingMusicSound currentSound;
    private static float currentVolume = 0.0F;

    // 每个情境记住的轨道（用于再次进入同一情境时沿用同一条轨道继续播放）
    private static final ResourceLocation[] rememberedTrack = new ResourceLocation[5];

    /** 由网络回调调用：仅记录最新情境，真正的切换逻辑在 tick 中处理。 */
    public static void onContextPacket(NoteMusicContextPayload payload) {
        activeContext = payload.context();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        byte ctx = activeContext;

        if (ctx == NoteMusicContextPayload.CONTEXT_NONE) {
            // 脱离特殊情境：淡出现有音乐，但不停止声音（驻留），从而保留其播放位置
            if (playingContext != NoteMusicContextPayload.CONTEXT_NONE && currentSound != null) {
                currentVolume -= VOLUME_STEP;
                if (currentVolume <= 0.0F) {
                    currentVolume = 0.0F;
                    currentSound.setVolume(0.0F);
                    playingContext = NoteMusicContextPayload.CONTEXT_NONE;
                } else {
                    currentSound.setVolume(currentVolume);
                }
            }
            return;
        }

        // 特殊情境
        if (playingContext != ctx) {
            ResourceLocation targetTrack = pickTrack(ctx);
            if (currentSound != null && currentSound.getLocation().equals(targetTrack)) {
                // 与驻留中的轨道相同：直接淡入恢复，从驻留时的播放位置继续
                playingContext = ctx;
            } else {
                // 不同轨道：停掉旧的，播放新的
                if (currentSound != null) {
                    mc.getSoundManager().stop(currentSound);
                    currentSound = null;
                }
                currentSound = new LoopingMusicSound(targetTrack);
                currentSound.setVolume(0.0F);
                mc.getSoundManager().play(currentSound);
                rememberedTrack[ctx & 0xFF] = targetTrack;
                playingContext = ctx;
                currentVolume = 0.0F;
            }
        } else if (currentSound != null) {
            // 淡入/保持
            if (currentVolume < 1.0F) {
                currentVolume = Math.min(1.0F, currentVolume + VOLUME_STEP);
                currentSound.setVolume(currentVolume);
            }
        }
    }

    @SubscribeEvent
    public static void onSelectMusic(SelectMusicEvent event) {
        // 有情境音乐正在播放时，屏蔽原版音乐以完成“淡出原版音乐”
        if (playingContext != NoteMusicContextPayload.CONTEXT_NONE
            && currentSound != null && currentVolume > 0.01F) {
            event.setMusic(null);
        }
    }

    private static ResourceLocation pickTrack(byte ctx) {
        switch (ctx) {
            case NoteMusicContextPayload.CONTEXT_VILLAGE:
                return rememberedTrack[ctx] != null ? rememberedTrack[ctx] : MUSIC_VILLAGE;
            case NoteMusicContextPayload.CONTEXT_UNDERWATER:
                return rememberedTrack[ctx] != null ? rememberedTrack[ctx] : MUSIC_UNDERWATER;
            case NoteMusicContextPayload.CONTEXT_TRADER:
                return rememberedTrack[ctx] != null ? rememberedTrack[ctx] : MUSIC_TRADER;
            case NoteMusicContextPayload.CONTEXT_HIGH: {
                if (rememberedTrack[ctx] != null) return rememberedTrack[ctx];
                return Minecraft.getInstance().level != null
                    && Minecraft.getInstance().level.random.nextBoolean() ? MUSIC_HIGH_A : MUSIC_HIGH_B;
            }
            default:
                return MUSIC_VILLAGE;
        }
    }

    /** 解析配置的播放类别，默认唱片机（records），使关闭游戏音乐滑块的玩家仍能听到。 */
    private static SoundSource resolveSoundSource() {
        String name = ModConfig.NOTE_MUSIC_SOUND_SOURCE.get();
        if (name != null) {
            for (SoundSource s : SoundSource.values()) {
                if (s.getName().equalsIgnoreCase(name)) {
                    return s;
                }
            }
        }
        return SoundSource.RECORDS;
    }

    /** 可循环、音量可淡入淡出的音乐实例。 */
    private static class LoopingMusicSound extends AbstractSoundInstance implements TickableSoundInstance {
        LoopingMusicSound(ResourceLocation loc) {
            super(loc, resolveSoundSource(), SoundInstance.createUnseededRandom());
            this.looping = true;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.delay = 0;
            this.volume = 0.0F;
        }

        @Override
        public void tick() {
            // 音量由外部 NoteUniverseMusicClient 驱动
        }

        @Override
        public boolean isStopped() {
            return false;
        }

        @Override
        public boolean canStartSilent() {
            // 允许以 0 音量启动（淡入），否则 SoundEngine 会因音量 0 直接跳过本声音
            return true;
        }

        void setVolume(float v) {
            this.volume = v;
        }
    }
}