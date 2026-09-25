package cn.autoforged.joes_addons_for_abmc.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 可演奏结构的音色库：每一种音色 = 10 个八度带的基底 SoundEvent（同 didgeridoo 的扩展规则）。
 * 结构按 {@code structureId} 决定音色：chicken_guitar_* → banjo，其余默认 didgeridoo。
 * 音阶/半音换算复用 {@link DidgeridooTones}（C=54、每带 12 半音、pitch=2^(note/12)）。
 */
public final class StrumTones {

    public static final int TIMBRE_DIDGERIDOO = 0;
    public static final int TIMBRE_BANJO = 1;
    public static final int TIMBRE_BASS = 2;
    public static final int TIMBRE_ELECTRIC_GUITAR = 3;
    public static final int TIMBRE_GUITAR = 4;

    private static final List<Holder<SoundEvent>[]> TIMBRES = List.of(
        toHolderArray(ModSounds.DIDGERIDOO_BANDS),
        toHolderArray(ModSounds.BANJO_BANDS),
        toHolderArray(ModSounds.BASS_BANDS),
        toHolderArray(ModSounds.ELECTRIC_GUITAR_BANDS),
        toHolderArray(ModSounds.GUITAR_BANDS));

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Holder<SoundEvent>[] toHolderArray(List<Supplier<SoundEvent>> bands) {
        Holder<SoundEvent>[] arr = new Holder[bands.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = (Holder<SoundEvent>) (Object) bands.get(i);
        }
        return arr;
    }

    private StrumTones() {
    }

    /** 结构 id → 音色。charged 的 green_guitar / chicken_guitar_2 用电吉他；chicken_guitar_1 用 banjo，
     *  blue_guitar 用 bass，green_guitar 与 chicken_guitar_2（未充电）用原版吉他，其余默认 didgeridoo。 */
    public static int timbreForStructure(@Nullable String structureId, boolean charged) {
        if (charged && ("green_guitar".equals(structureId) || "chicken_guitar_2".equals(structureId))) {
            return TIMBRE_ELECTRIC_GUITAR;
        }
        if ("chicken_guitar_1".equals(structureId)) {
            return TIMBRE_BANJO;
        }
        if ("blue_guitar".equals(structureId)) {
            return TIMBRE_BASS;
        }
        if ("green_guitar".equals(structureId) || "chicken_guitar_2".equals(structureId)) {
            return TIMBRE_GUITAR;
        }
        return TIMBRE_DIDGERIDOO;
    }

    /** 某音色某八度带的基底音源。 */
    public static Holder<SoundEvent> bandSound(int timbre, int band) {
        if (timbre < 0 || timbre >= TIMBRES.size()) timbre = TIMBRE_DIDGERIDOO;
        int b = Math.floorMod(band, DidgeridooTones.OCTAVES);
        return TIMBRES.get(timbre)[b];
    }

    /** 在本地玩家处立即播放：演奏者本人零延迟听闻。 */
    public static void playLocal(Minecraft mc, int timbre, int semitone) {
        if (mc.level == null || mc.player == null) return;
        int s = Math.floorMod(semitone, DidgeridooTones.TOTAL);
        Holder<SoundEvent> ev = bandSound(timbre, DidgeridooTones.octaveBandOf(s));
        float pitch = DidgeridooTones.pitchOf(s);
        mc.level.playSound(mc.player, mc.player.getX(), mc.player.getY(), mc.player.getZ(),
            ev, SoundSource.PLAYERS, 1.0F, pitch);
    }

    /** 把声音转播给演奏者附近的其他玩家（跳过本人，本人已本地播放）。 */
    public static void relay(ServerLevel sl, ServerPlayer performer, int timbre, int semitone) {
        double px = performer.getX(), py = performer.getY(), pz = performer.getZ();
        int s = Math.floorMod(semitone, DidgeridooTones.TOTAL);
        Holder<SoundEvent> ev = bandSound(timbre, DidgeridooTones.octaveBandOf(s));
        float pitch = DidgeridooTones.pitchOf(s);
        long seed = sl.random.nextLong();
        double maxSq = 16.0 * 16.0;
        for (ServerPlayer other : sl.players()) {
            if (other == performer) continue;
            double dx = other.getX() - px, dy = other.getY() - py, dz = other.getZ() - pz;
            if (dx * dx + dy * dy + dz * dz <= maxSq) {
                other.connection.send(new ClientboundSoundPacket(
                    ev, SoundSource.PLAYERS, px, py, pz, 1.0F, pitch, seed));
            }
        }
    }

    /** 兼容旧的无音色调用（默认 didgeridoo），供 /jafa playsound 等保留使用。 */
    public static void playAt(ServerLevel level, net.minecraft.world.phys.Vec3 pos, int semitone, @Nullable UUID playToSelf) {
        int s = Math.floorMod(semitone, DidgeridooTones.TOTAL);
        Holder<SoundEvent> ev = bandSound(TIMBRE_DIDGERIDOO, DidgeridooTones.octaveBandOf(s));
        float pitch = DidgeridooTones.pitchOf(s);
        if (playToSelf != null) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(playToSelf);
            if (p != null) {
                p.level().playSound(null, pos.x, pos.y, pos.z, ev, SoundSource.PLAYERS, 1.0F, pitch);
            }
        } else {
            level.playSound(null, pos.x, pos.y, pos.z, ev, SoundSource.PLAYERS, 1.0F, pitch);
        }
    }
}