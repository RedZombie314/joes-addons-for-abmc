package cn.autoforged.joes_addons_for_abmc.sound;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * “移植头”生物的音效改写（服务端）。
 *
 * 规则：身首异处的生物发出的是“头来源生物”的声音，而不是身体本身的声音。
 *  - 若头来源生物存在“对应”音效（该情形下的 ambient / hurt / death），直接播放它；
 *  - 若头来源生物没有对应音效（例如僵尸头 + 村民身，村民会尝试发“同意(yes)”音效，而僵尸并没有
 *    yes 音效），则从头来源生物可用的音效里随机挑一个播放；
 *  - 受伤时若头与身体存在共用音效（如僵尸和村民都有受伤音效），仍播放头来源（僵尸）的受伤音效。
 *
 * 通过 {@code getAmbientSound}/getHurtSound/getDeathSound 三个 getter 拦截实现（分别挂在
 * {@code Mob}/{@code LivingEntity} 上）。只有带移植头 NBT 的生物才会被改写，普通生物完全不受影响。
 *
 * 通过反射调用头来源生物的 protected 音效方法（Mojmap 名），并用临时实体获取其音效，
 * 用完立即 discard，避免污染世界。
 */
public final class TransplantHeadSound {

    private TransplantHeadSound() {
    }

    /** 玩家/生物发出环境音时调用；返回 null 表示不改写（沿用身体原音效）。 */
    public static SoundEvent getAmbient(LivingEntity self) {
        return resolve(self, "getAmbientSound", new Object[0], null);
    }

    /** 受伤时调用。 */
    public static SoundEvent getHurt(LivingEntity self, DamageSource source) {
        return resolve(self, "getHurtSound", new Object[]{source}, source);
    }

    /** 死亡时调用。 */
    public static SoundEvent getDeath(LivingEntity self) {
        return resolve(self, "getDeathSound", new Object[0], null);
    }

    /**
     * 由 {@code Entity.playSound} 拦截调用，处理 getter 覆盖不到/不生效的场景（如村民“同意”等显式音效，
     * 或某些环境下 getter 未被触发）。只检查<b>本实体自身</b>的移植头 NBT，普通生物完全不受影响。
     *
     * - 若头来源本身就能提供该音效，放行（返回 null）；
     * - 否则返回一个随机的头来源音效，由调用方替代播放。
     */
    public static SoundEvent routePlayback(LivingEntity self, SoundEvent event) {
        LivingEntity head = headEntity(self);
        if (head == null) return null;
        try {
            List<SoundEvent> pool = buildPool(head, self.damageSources().generic());
            if (pool.isEmpty()) return null;
            if (pool.contains(event)) return null; // 该音效本身就是头来源的音效，放行
            return pool.get(self.getRandom().nextInt(pool.size()));
        } finally {
            head.discard();
        }
    }

    private static SoundEvent resolve(LivingEntity self, String method, Object[] args, DamageSource srcForFallback) {
        LivingEntity head = headEntity(self);
        if (head == null) return null;
        try {
            SoundEvent direct = invokeSound(head, method, args);
            if (direct != null) return direct;
            // 头来源生物没有“对应”音效：从头来源可用的几个音效中随机挑一个播放。
            return randomHeadSound(head, srcForFallback);
        } finally {
            head.discard();
        }
    }

    /** 克隆出“头来源生物”临时实体；失败或非生物返回 null。 */
    private static LivingEntity headEntity(LivingEntity self) {
        try {
            String headType = ModMain.getTransplantedHead(self);
            if (headType.isEmpty()) return null;
            EntityType<?> et = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.tryParse(headType));
            if (et == null) return null;
            Entity e = et.create(self.level());
            if (e instanceof LivingEntity head) return head;
            if (e != null) e.discard();
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static SoundEvent randomHeadSound(LivingEntity head, DamageSource src) {
        DamageSource source = src != null ? src : head.damageSources().generic();
        List<SoundEvent> pool = buildPool(head, source);
        if (pool.isEmpty()) return null;
        return pool.get(head.getRandom().nextInt(pool.size()));
    }

    /** 从头来源生物的常见 getter 中收集可用音效（ambient/hurt/death）。 */
    private static List<SoundEvent> buildPool(LivingEntity head, DamageSource source) {
        List<SoundEvent> pool = new ArrayList<>();
        SoundEvent ambient = invokeSound(head, "getAmbientSound", new Object[0]);
        SoundEvent death = invokeSound(head, "getDeathSound", new Object[0]);
        SoundEvent hurt = invokeSound(head, "getHurtSound", new Object[]{source});
        if (ambient != null) pool.add(ambient);
        if (death != null) pool.add(death);
        if (hurt != null) pool.add(hurt);
        return pool;
    }

    /** 反射调用头来源生物的 protected 音效方法；失败或返回非 SoundEvent 时返回 null。 */
    private static SoundEvent invokeSound(LivingEntity head, String method, Object[] args) {
        try {
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                types[i] = (args[i] == null) ? DamageSource.class : args[i].getClass();
            }
            // getAmbientSound 声明在 Mob 上、getHurtSound/getDeathSound 在 LivingEntity 上，
            // 需沿父类链查找声明该方法的类。
            Method m = null;
            for (Class<?> c = head.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    m = c.getDeclaredMethod(method, types);
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
            if (m == null) return null;
            m.setAccessible(true);
            Object r = m.invoke(head, args);
            return r instanceof SoundEvent s ? s : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}