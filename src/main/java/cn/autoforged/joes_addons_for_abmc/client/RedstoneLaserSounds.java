package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.config.ModConfig;
import cn.autoforged.joes_addons_for_abmc.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * 红石块权杖的激光音效状态机（客户端）。
 *
 * 右键按下 → 播放一次 laser_start；
 * laser_start 播完且右键仍按住 → 循环播放 laser_middle 直至右键松开；
 * 松开右键（或在 laser_start 播完时右键已松开）→ 播放一次 laser_end。
 * 由 ClientEvents.ClientTickHandler 每刻驱动。
 */
public final class RedstoneLaserSounds {

    private static final int STATE_IDLE = 0;
    private static final int STATE_START = 1;
    private static final int STATE_MIDDLE = 2;

    // laser_start.ogg 实测时长约 0.279s，按 20 TPS 折算为 6 刻后进入 middle/end 判定
    private static final int START_TICKS = 6;

    private static int state = STATE_IDLE;
    private static int startElapsed = 0;
    private static MiddleLoopSound middleSound = null;
    private static OneShotSound startSound = null;
    // 抑制标记：玩家在女仆交互等场景下阻止了权杖右键行为后置为 true。
    // 只要右键仍被按住（useDown=true）就一直保持静默（不响应启动），直到右键松开才清除，
    // 避免“右击女仆时 stopAndReset 只停一次、下刻又因右键仍按住而重新播放”。
    private static boolean suppressUntilRelease = false;

    private RedstoneLaserSounds() {
    }

    /** 每客户端刻调用一次。holdingStaff 表示是否持有红石块权杖，useDown 表示当前右键是否按下。 */
    public static void tick(boolean holdingStaff, boolean useDown) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // 红石块权杖激光音效开关：关闭时停止并复位所有激光音效（激光光束本身仍照常渲染）
        if (!ModConfig.REDSTONE_LASER_SOUNDS.get()) {
            reset(mc);
            return;
        }

        if (suppressUntilRelease) {
            // 右键仍按住：保持静默，不启动任何音效；右键松开后解除抑制并复位。
            if (!useDown) {
                suppressUntilRelease = false;
                reset(mc);
            }
            return;
        }

        if (!holdingStaff) {
            reset(mc);
            return;
        }

        if (useDown && state == STATE_IDLE) {
            // 开始按下右键：播放一次 laser_start
            startSound = new OneShotSound(ModSounds.LASER_START.get());
            mc.getSoundManager().play(startSound);
            state = STATE_START;
            startElapsed = 0;
        }

        if (!useDown && state == STATE_MIDDLE) {
            // middle 循环中松开右键：停止循环并播放一次 laser_end
            stopMiddle(mc);
            mc.getSoundManager().play(new OneShotSound(ModSounds.LASER_END.get()));
            state = STATE_IDLE;
        }

        // 兜底：只要玩家不再处于“使用中”（item use 因任何原因结束后，例如强行取消、死亡、
        // 切换物品等），立刻停止循环音效并播放结束音，防止 laser_middle 无限循环。
        if (state == STATE_MIDDLE && !mc.player.isUsingItem()) {
            stopMiddle(mc);
            mc.getSoundManager().play(new OneShotSound(ModSounds.LASER_END.get()));
            state = STATE_IDLE;
        }

        if (state == STATE_START) {
            startElapsed++;
            if (startElapsed >= START_TICKS) {
                if (useDown) {
                    // laser_start 播完且右键仍按住：开始循环 laser_middle
                    middleSound = new MiddleLoopSound();
                    mc.getSoundManager().play(middleSound);
                    state = STATE_MIDDLE;
                } else {
                    // laser_start 播完时右键已松开：播放一次 laser_end
                    mc.getSoundManager().play(new OneShotSound(ModSounds.LASER_END.get()));
                    state = STATE_IDLE;
                }
            }
        }
    }

    private static void stopMiddle(Minecraft mc) {
        if (middleSound != null) {
            // 置位停止标志：isStopped() 由恒 false 改为返回 true，SoundEngine 每刻的循环会据此
            // 真正关闭该循环音效占用的 OpenAL 通道。这是终止 TickableSoundInstance 的正规机制——
            // 此前 isStopped() 恒为 false 会告知引擎“永远别停我”，导致即使调用了 stop()，
            // 激光持续音效仍可能一直循环播放。
            middleSound.stopMe();
            mc.getSoundManager().stop(middleSound);
            middleSound = null;
        }
        // 注意：不能再用“按音效位置 + 音源类别”的全局停止（stop(ResourceLocation, SoundSource)）——
        // 女仆红石块权杖的循环音效（MaidRedstoneLaserSounds）使用相同的位置（LASER_MIDDLE）与相同
        // 音源类别（PLAYERS），全局停止会误伤女仆正在播放的 laser_middle，导致女仆激光只有开始/结束
        // 音效、持续音效永远无法发出。middleSound 实例 + stopMe() 已足以停止玩家自己的循环音效。
    }

    /** 停止尚未播完的 laser_start 单次音效（若正在播放则立即静音，防止右击女仆时残留发射声）。 */
    private static void stopStart(Minecraft mc) {
        if (startSound != null) {
            mc.getSoundManager().stop(startSound);
            startSound = null;
        }
    }

    private static void reset(Minecraft mc) {
        stopMiddle(mc);
        stopStart(mc);
        state = STATE_IDLE;
        startElapsed = 0;
    }

    /** 外部强制停止并复位音效状态机（如玩家右击女仆阻止权杖右键行为时）。
     *  若此前正在循环播放 laser_middle，则顺带播放一次结束音，避免音效悬停；
     *  若仅处于 laser_start 阶段则直接复位（start 音效会自然播完）。
     *  同时置起“抑制直到右键松开”标记：只要右键仍按住就不再重新启动任何激光音效。 */
    public static void stopAndReset(Minecraft mc) {
        if (state == STATE_MIDDLE) {
            stopMiddle(mc);
            mc.getSoundManager().play(new OneShotSound(ModSounds.LASER_END.get()));
        }
        stopStart(mc);
        state = STATE_IDLE;
        startElapsed = 0;
        suppressUntilRelease = true;
    }

    /** 单次播放的激光音效（laser_start / laser_end）。 */
    private static class OneShotSound extends AbstractSoundInstance {
        OneShotSound(SoundEvent event) {
            super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.volume = 1.0F;
            this.pitch = 1.0F;
            this.looping = false;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }
    }

    /** 循环播放的 laser_middle 音效实例，由 SoundManager 持续播放，松开右键时停止。 */
    private static class MiddleLoopSound extends AbstractSoundInstance implements TickableSoundInstance {
        private boolean stopped = false;

        MiddleLoopSound() {
            super(ModSounds.LASER_MIDDLE.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.volume = 1.0F;
            this.pitch = 1.0F;
            this.looping = true;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        /** 置位终止标志：让 SoundEngine 在下一刻关闭该循环音效的通道。 */
        void stopMe() {
            this.stopped = true;
        }

        @Override
        public void tick() {
        }

        @Override
        public boolean isStopped() {
            return this.stopped;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
