package cn.autoforged.joes_addons_for_abmc.sound;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.UUID;

/**
 * didgeridoo 音色的 120 个半音框架。
 * <p>布局：以 didgeridoo 为基准八度（band 4），向低扩充 4 个八度、向高扩充 5 个八度，
 * 共 10 个八度 × 12 半音 = 120 个不同音高。半音索引 {@code 0..119}：
 * {@code band = index / 12}（0=最低 … 9=最高），{@code note = index % 12}。
 * <p>播放策略：OpenAL/原版声引擎会将 pitch 钳制在 [0.5, 2.0]，跨 10 个八度用单一 pitch 乘子
 * 会令极低频全部坍缩到同音。故每带的 pitch 只用带内半音 {@code 2^(note/12) ∈ [1.0,2.0)}，
 * 带内 12 个音清晰可辨；带与带之间暂共用原版 didgeridoo 音源（跨带听感暂相同），待各带
 * 绑定专属基底样本后即还原真实八度纵深。结构本身暂不接发声机制。 */
public final class DidgeridooTones {

    public static final int OCTAVES = 10;            // 10 个八度
    public static final int SEMITONES = 12;          // 每八度 12 半音
    public static final int BACKWARD_OCTAVES = 4;    // didgeridoo 基准八度向低扩充的八度数
    public static final int FORWARD_OCTAVES = 5;     // 向高扩充的八度数
    public static final int TOTAL = OCTAVES * SEMITONES; // 120
    /** didgeridoo 基准八度带（带 0 = 最低）。 */
    public static final int BASE_BAND = BACKWARD_OCTAVES; // 4

    /** didgeridoo 十个八度带的基底音源。 */
    private static final Holder<SoundEvent>[] BAND_SOUNDS = didgeridooBandSounds();

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Holder<SoundEvent>[] didgeridooBandSounds() {
        Holder<SoundEvent>[] arr = new Holder[OCTAVES];
        for (int b = 0; b < OCTAVES; b++) {
            // DeferredHolder 本身即 Holder<SoundEvent>（同时是 Supplier<SoundEvent>）
            arr[b] = (Holder<SoundEvent>) ModSounds.DIDGERIDOO_BANDS.get(b);
        }
        return arr;
    }

    private static final Random RNG = new Random();

    private DidgeridooTones() {
    }

    /** 随机一个半音索引 {@code 0..TOTAL-1}。 */
    public static int randomSemitoneIndex() {
        return RNG.nextInt(TOTAL);
    }

    /** 半音索引的八度带（0=最低 … 9=最高）。 */
    public static int octaveBandOf(int semitone) {
        return Math.floorMod(semitone, TOTAL) / SEMITONES;
    }

    /** 半音索引在该八度带内的音（0..11）。 */
    public static int noteInBand(int semitone) {
        return Math.floorMod(semitone, TOTAL) % SEMITONES;
    }

    /** 相对 didgeridoo 原始音（F#，升 fa）的半音偏移。基准带（band 4）的 note 0 = 原版 didgeridoo = F#。
     *  {@code band b 的 note n} 相对 F# 的偏移 = {@code (b-4)*12 + n}；该偏移的 pitch = {@code 2^(偏移/12)}。 */
    public static int semitoneOffsetFromFSharp(int semitone) {
        return Math.floorMod(semitone, TOTAL) - BASE_BAND * SEMITONES;
    }

    /** 播放 pitch：只用带内半音 {@code 2^(note/12) ∈ [1.0, 2.0)}，保证落在可听且不触发引擎钳制的区间。 */
    public static float pitchOf(int semitone) {
        return (float) Math.pow(2.0, (double) noteInBand(semitone) / SEMITONES);
    }

    /** 某八度带对应的音源（带 0..9 → didgeridoo_band_0..9 基底样本）。 */
    public static Holder<SoundEvent> bandSound(int band) {
        return BAND_SOUNDS[Math.floorMod(band, OCTAVES)];
    }

    /** 在指定世界位置播放某个 didgeridoo 半音（按八度带选音源，带内半音抬升 pitch）。
     *  @param semitone  半音索引（自动取模到 0..119）
     *  @param playToSelf 若指定玩家 UUID，则只在该玩家处播放（否则全维度可闻） */
    public static void playAt(ServerLevel level, Vec3 pos, int semitone, @Nullable UUID playToSelf) {
        int s = Math.floorMod(semitone, TOTAL);
        Holder<SoundEvent> ev = bandSound(octaveBandOf(s));
        float pitch = pitchOf(s);
        if (playToSelf != null) {
            net.minecraft.server.level.ServerPlayer p = level.getServer().getPlayerList().getPlayer(playToSelf);
            if (p != null) {
                p.level().playSound(null, pos.x, pos.y, pos.z, ev, SoundSource.PLAYERS, 1.0F, pitch);
            }
        } else {
            level.playSound(null, pos.x, pos.y, pos.z, ev, SoundSource.PLAYERS, 1.0F, pitch);
        }
    }
}