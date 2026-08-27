package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class EarRingingSoundInstance extends AbstractTickableSoundInstance {
    private int tick;
    private final int rampUpDuration;

    public EarRingingSoundInstance(float pitch) {
        super(ModSounds.EAR_RINGING.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.volume = 0.0F;
        this.pitch = pitch;
        this.looping = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.relative = true;
        this.tick = 0;
        this.rampUpDuration = Minecraft.getInstance().level != null
            ? Minecraft.getInstance().level.random.nextInt(21) : 0;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        tick++;
        float elapsed = tick;

        if (elapsed <= rampUpDuration) {
            volume = rampUpDuration == 0 ? 0.75F : Math.min(0.75F, 0.75F * (elapsed / rampUpDuration));
        } else if (elapsed <= 300) {
            volume = 0.75F;
        } else if (elapsed <= 400) {
            volume = Math.max(0.0F, 0.75F * (1.0F - (elapsed - 300) / 100.0F));
            if (volume <= 0.01F) {
                stop();
            }
        } else {
            stop();
        }
    }
}
