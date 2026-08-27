package cn.autoforged.joes_addons_for_abmc.client;

import cn.autoforged.joes_addons_for_abmc.network.MaidLaserSoundPayload;
import cn.autoforged.joes_addons_for_abmc.sound.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 女仆红石块权杖的激光音效状态机（客户端）。
 * <p>
 * 由服务端通过 {@code MaidLaserSoundPayload} 驱动，携带事件类型与光束连线（女仆眼位 → 光束末端）：
 * <ul>
 *   <li>{@link MaidLaserSoundPayload#ACTION_START}：播放一次 laser_start，并循环播放 laser_middle；</li>
 *   <li>{@link MaidLaserSoundPayload#ACTION_UPDATE}：更新光束连线（不播放任何音效）；</li>
 *   <li>{@link MaidLaserSoundPayload#ACTION_END}：停止该女仆的 laser_middle 循环，并播放一次 laser_end。</li>
 * </ul>
 * 音效定位与音量规则（音量全部由本类手动计算，不依赖 OpenAL 距离模型——
 * 1.21.1 的 {@code Channel.linearAttenuation} 把 AL_ROLLOFF_FACTOR 设为 0，实际不产生距离衰减）：
 * <ul>
 *   <li>laser_start / laser_end（发射/结束音效）：音源定位在女仆眼位，音量按「听者 → 女仆眼位」距离衰减，离女仆越近越响；</li>
 *   <li>laser_middle（持续音效）：循环音效定位在「女仆眼位 → 光束末端」连线上离听者最近的投影点，
 *       音量按「听者 → 连线」距离衰减，离线越近越响。</li>
 * </ul>
 * 与玩家版（RedstoneLaserSounds，相对玩家耳朵播放）不同：女仆激光音效是定位音效，随距离衰减。
 * <p>
 * 循环音效的位置与音量在每客户端刻的 tick() 中重算（光束起点跟随女仆实体、投影点跟随玩家听者位置）；
 * 女仆消失时由 {@link #tick()} 清理，避免循环音效残留。
 */
public final class MaidRedstoneLaserSounds {

    /** 当前正在循环播放 laser_middle 的女仆：实体 id -> 循环音效实例。 */
    private static final Map<Integer, MaidMiddleLoopSound> ACTIVE_LOOPS = new HashMap<>();

    /** 女仆实体消失的宽限期（刻）：实体可能因刚进入追踪范围尚未加载，宽限期内不清理循环音效。 */
    private static final long GRACE_TICKS = 40L;

    /** 激光音效的最大可听距离（格）：听者距离音源超过该值后音量为 0。 */
    private static final float SOUND_RANGE = 16.0F;

    /** 按距离线性计算音量：0 ~ {@link #SOUND_RANGE} 内从 1.0 衰减到 0.0，超过后静音。
     *  音量完全由本类手动控制（attenuation=NONE），不依赖 OpenAL 距离模型。 */
    private static float volumeAt(double distance) {
        return (float) Mth.clamp(1.0 - distance / SOUND_RANGE, 0.0, 1.0);
    }

    /** 听者到「起点 → 末端」线段连线的最近距离（用于激光持续音效的音量衰减）。 */
    private static double distanceToLine(Vec3 listener, Vec3 start, Vec3 end) {
        Vec3 seg = end.subtract(start);
        double lenSq = seg.lengthSqr();
        if (lenSq < 1.0E-6) {
            return listener.distanceTo(start);
        }
        double t = Mth.clamp(listener.subtract(start).dot(seg) / lenSq, 0.0, 1.0);
        return listener.distanceTo(start.add(seg.scale(t)));
    }

    private MaidRedstoneLaserSounds() {
    }

    /** 收到服务端事件：按事件类型处理女仆激光音效。
     *  <ul>
     *   <li>START：播放 laser_start（女仆眼位）并开始循环 laser_middle（跟随光束连线）；</li>
     *   <li>UPDATE：更新光束连线，让循环音效跟随变化的光束（不播放音效）；</li>
     *   <li>END：停止循环并播放 laser_end（女仆眼位）。</li>
     *  </ul>
     *  @param x,y,z   女仆眼位（laser_start / laser_end 的音源位置）
     *  @param endX,endY,endZ  光束末端点（用于定位 laser_middle 的循环音效） */
    public static void handleMaidLaser(int maidId, int action, double x, double y, double z,
                                       double endX, double endY, double endZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        switch (action) {
            case MaidLaserSoundPayload.ACTION_START -> {
                // 先停掉同一女仆可能残留的旧循环（目标切换/重新开火时），避免叠音
                stopLoop(maidId);
                mc.getSoundManager().play(new OneShotSound(ModSounds.LASER_START.get(), x, y, z));
                MaidMiddleLoopSound loop = new MaidMiddleLoopSound(maidId, x, y, z, endX, endY, endZ);
                ACTIVE_LOOPS.put(maidId, loop);
                mc.getSoundManager().play(loop);
            }
            case MaidLaserSoundPayload.ACTION_UPDATE -> {
                // 光束末端移动：更新连线（起点由循环音效在 tick 中跟随女仆实体），不播放音效
                MaidMiddleLoopSound loop = ACTIVE_LOOPS.get(maidId);
                if (loop != null) {
                    loop.setEnd(endX, endY, endZ);
                }
            }
            case MaidLaserSoundPayload.ACTION_END -> {
                stopLoop(maidId);
                mc.getSoundManager().play(new OneShotSound(ModSounds.LASER_END.get(), x, y, z));
            }
            default -> {
            }
        }
    }

    /** 每客户端刻调用一次：清理已消失/死亡的女仆循环音效。
     *  女仆实体可能因刚进入追踪范围尚未加载而暂时找不到，因此给予一定的宽限期，
     *  避免 START 事件刚到达就因实体未加载而被误清理。 */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            reset();
            return;
        }
        long gameTime = mc.level.getGameTime();
        Iterator<Map.Entry<Integer, MaidMiddleLoopSound>> it = ACTIVE_LOOPS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, MaidMiddleLoopSound> entry = it.next();
            Entity entity = mc.level.getEntity(entry.getKey());
            // 位置跟随在循环实例的 tick() 中完成；此处仅做消失检测
            if (entity.isAlive() && cn.autoforged.joes_addons_for_abmc.ModMain.isTouhouMaid(entity)) {
                entry.getValue().lastSeenTick = gameTime;
            } else if (gameTime - entry.getValue().lastSeenTick > GRACE_TICKS) {
                // 女仆实体持续消失（死亡/卸载）超过宽限期：停止循环音效
                mc.getSoundManager().stop(entry.getValue());
                it.remove();
            }
        }
    }

    /** 离开世界/切换存档时停止所有女仆激光循环音效。 */
    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        for (MaidMiddleLoopSound loop : ACTIVE_LOOPS.values()) {
            mc.getSoundManager().stop(loop);
        }
        ACTIVE_LOOPS.clear();
    }

    private static void stopLoop(int maidId) {
        MaidMiddleLoopSound loop = ACTIVE_LOOPS.remove(maidId);
        if (loop != null) {
            Minecraft.getInstance().getSoundManager().stop(loop);
        }
    }

    /** 单次播放的定位激光音效（laser_start / laser_end）：音源固定在女仆眼位，
     *  音量按「听者 → 女仆眼位」距离手动计算（离女仆越近越响），每刻随玩家移动更新。
     *  attenuation=NONE：不依赖 OpenAL 距离模型，音量完全由 volume 字段控制。 */
    private static class OneShotSound extends AbstractSoundInstance implements TickableSoundInstance {
        private final double posX;
        private final double posY;
        private final double posZ;

        OneShotSound(SoundEvent event, double x, double y, double z) {
            super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.posX = x;
            this.posY = y;
            this.posZ = z;
            this.pitch = 1.0F;
            this.looping = false;
            this.relative = false;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = x;
            this.y = y;
            this.z = z;
            this.volume = currentVolume();
        }

        @Override
        public void tick() {
            this.volume = currentVolume();
        }

        private float currentVolume() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return 1.0F;
            return volumeAt(mc.player.getEyePosition().distanceTo(new Vec3(posX, posY, posZ)));
        }

        @Override
        public boolean isStopped() {
            return false;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    /** 循环播放的 laser_middle 定位音效：音源定位在「女仆眼位 → 光束末端」连线上离听者最近的投影点，
     *  音量按「听者 → 连线」距离手动计算（离线越近越响），每刻随光束与玩家移动更新。
     *  attenuation=NONE：不依赖 OpenAL 距离模型，音量完全由 volume 字段控制。 */
    private static class MaidMiddleLoopSound extends AbstractSoundInstance implements TickableSoundInstance {
        private final int maidId;

        /** 光束起点（女仆眼位），在 tick() 中跟随女仆实体当前眼位。 */
        private double startX;
        private double startY;
        private double startZ;

        /** 光束末端点（来自服务端 START / UPDATE 事件，随目标移动而更新）。 */
        private double endX;
        private double endY;
        private double endZ;

        /** 最近一次在客户端世界中看到该女仆实体的刻，用于消失宽限期判断。 */
        private long lastSeenTick;

        MaidMiddleLoopSound(int maidId, double x, double y, double z,
                            double endX, double endY, double endZ) {
            super(ModSounds.LASER_MIDDLE.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.maidId = maidId;
            this.pitch = 1.0F;
            this.looping = true;
            this.relative = false;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.startX = x;
            this.startY = y;
            this.startZ = z;
            this.endX = endX;
            this.endY = endY;
            this.endZ = endZ;
            this.lastSeenTick = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getGameTime() : 0L;
            this.volume = currentVolume();
        }

        /** 更新光束末端点（服务端发送 ACTION_UPDATE 时）。 */
        void setEnd(double endX, double endY, double endZ) {
            this.endX = endX;
            this.endY = endY;
            this.endZ = endZ;
        }

        /** 依据当前听者位置计算音量：听者到光束连线（起点 → 末端）的最近距离越近越响。 */
        private float currentVolume() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return 1.0F;
            double dist = distanceToLine(mc.player.getEyePosition(),
                new Vec3(startX, startY, startZ), new Vec3(endX, endY, endZ));
            return volumeAt(dist);
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;

            // 光束起点跟随女仆实体当前眼位（女仆移动时连线也随之移动）
            Entity entity = mc.level.getEntity(maidId);
            if (entity != null) {
                this.startX = entity.getX();
                this.startY = entity.getY() + entity.getEyeHeight();
                this.startZ = entity.getZ();
            }

            // 把循环音效定位到连线上离听者最近的投影点，并按该距离计算音量（离线越近越响）
            Vec3 listener = mc.player.getEyePosition();
            Vec3 start = new Vec3(startX, startY, startZ);
            Vec3 seg = new Vec3(endX - startX, endY - startY, endZ - startZ);
            double lenSq = seg.lengthSqr();
            Vec3 closest;
            if (lenSq < 1.0E-6) {
                // 连线退化为点（光束末端几乎贴脸）：直接定位在起点
                closest = start;
            } else {
                double t = Mth.clamp(listener.subtract(start).dot(seg) / lenSq, 0.0, 1.0);
                closest = start.add(seg.scale(t));
            }
            this.x = closest.x;
            this.y = closest.y;
            this.z = closest.z;
            this.volume = volumeAt(listener.distanceTo(closest));
        }

        @Override
        public boolean isStopped() {
            return false;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
