package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.config.ModConfig;
import cn.autoforged.joes_addons_for_abmc.network.BellRingPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME, modid = ModMain.MODID)
public class BellRingClientHandler {
    private static final int FADE_IN_DURATION = 20;
    private static final int HOLD_DURATION = 300;
    private static final int FADE_OUT_DURATION = 100;

    private static int startGameTick = -1;
    private static int rampUpDuration;

    public static float getGlobalSoundScale() {
        if (startGameTick < 0) return 1.0F;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.clientLevel == null) {
            startGameTick = -1;
            return 1.0F;
        }
        long currentTick = mc.player.clientLevel.getGameTime();
        long elapsed = currentTick - startGameTick;

        if (elapsed < 0) {
            startGameTick = -1;
            return 1.0F;
        }

        if (elapsed <= rampUpDuration) {
            if (rampUpDuration == 0) return 0.0F;
            return 1.0F - Math.min(1.0F, (float) elapsed / rampUpDuration);
        } else if (elapsed <= HOLD_DURATION) {
            return 0.0F;
        } else if (elapsed <= HOLD_DURATION + FADE_OUT_DURATION) {
            float fadeProgress = (float) (elapsed - HOLD_DURATION) / FADE_OUT_DURATION;
            return Math.min(1.0F, fadeProgress);
        } else {
            startGameTick = -1;
            return 1.0F;
        }
    }

    public static void handleBellRing(BellRingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            mc.getMusicManager().stopPlaying();

            rampUpDuration = mc.level.random.nextInt(FADE_IN_DURATION + 1);
            startGameTick = (int) mc.level.getGameTime();

            // 鸣钟权杖耳鸣音效开关：关闭时不播放耳鸣声，但“让其它音效静音”的效果仍然保留
            if (ModConfig.BELL_TINNITUS.get()) {
                EarRingingSoundInstance sound = new EarRingingSoundInstance(payload.pitch());
                mc.getSoundManager().play(sound);
            }
        });
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        // 始终包装所有音效，让它们携带“动态读取当前全局音量缩放”的能力：
        // 这样即使在鸣钟生效前就已开始播放的音效（例如正在播放的唱片机音乐），
        // 也会随着鸣钟的“静音→恢复”过程被正确压低/恢复。
        SoundInstance original = event.getOriginalSound();
        if (original instanceof ScaledSoundInstance || original instanceof EarRingingSoundInstance) return;
        event.setSound(new ScaledSoundInstance(original));
    }

    @SubscribeEvent
    public static void onSelectMusic(SelectMusicEvent event) {
        if (getGlobalSoundScale() < 1.0F) {
            event.setMusic(null);
        }
    }

    private static class ScaledSoundInstance implements TickableSoundInstance {
        private final SoundInstance delegate;

        ScaledSoundInstance(SoundInstance delegate) {
            this.delegate = delegate;
        }

        @Override
        public net.minecraft.resources.ResourceLocation getLocation() {
            return delegate.getLocation();
        }

        @javax.annotation.Nullable
        @Override
        public net.minecraft.client.sounds.WeighedSoundEvents resolve(net.minecraft.client.sounds.SoundManager manager) {
            return delegate.resolve(manager);
        }

        @Override
        public net.minecraft.client.resources.sounds.Sound getSound() {
            return delegate.getSound();
        }

        @Override
        public net.minecraft.sounds.SoundSource getSource() {
            return delegate.getSource();
        }

        @Override
        public boolean isLooping() {
            return delegate.isLooping();
        }

        @Override
        public boolean isRelative() {
            return delegate.isRelative();
        }

        @Override
        public int getDelay() {
            return delegate.getDelay();
        }

        @Override
        public float getVolume() {
            // 动态读取当前全局音量缩放，而非构造时固定：这样即使是鸣钟生效前就已开始播放且继续播放的音效
            // （如唱片机音乐这种可每刻刷新音量的 tickable 音效），也会随鸣钟的“静音→恢复”被正确压低/还原。
            return delegate.getVolume() * getGlobalSoundScale();
        }

        @Override
        public void tick() {
            if (delegate instanceof TickableSoundInstance t) t.tick();
        }

        @Override
        public boolean isStopped() {
            return delegate instanceof TickableSoundInstance t && t.isStopped();
        }

        @Override
        public float getPitch() {
            return delegate.getPitch();
        }

        @Override
        public double getX() {
            return delegate.getX();
        }

        @Override
        public double getY() {
            return delegate.getY();
        }

        @Override
        public double getZ() {
            return delegate.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return delegate.getAttenuation();
        }
    }
}
