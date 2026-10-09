package cn.autoforged.joes_addons_for_abmc.entity;

import net.neoforged.fml.ModList;

/**
 * <b>软依赖的"门"</b>：判断某个可选 mod 装没装。
 *
 * <h2>为什么必须单独开一个类（6.5.9 实测崩溃的教训）</h2>
 * 原本各调用点直接问 {@code TwilightForestCompat.isLoaded()} / {@code AetherCompat.isLoaded()}。
 * 那个调用<b>本身就会把联动类加载起来</b>，而联动类的方法体里全是对方 mod 的类型
 * （{@code CloudCrystal}、{@code AetherItems}、{@code TFItems}……）。一旦那个 mod 被卸载、
 * 而存档里还留着它的东西（天境维度、天境弹体……），JVM 在链接/校验这个类时就会去解析那些类型：
 *
 * <pre>
 * java.lang.NoClassDefFoundError: com/aetherteam/aether/entity/projectile/crystal/CloudCrystal
 *     at ...ModMain.onServerTickPre(ModMain.java:13597)      ← AetherCompat.tick()
 * Caused by: java.lang.ClassNotFoundException: ...CloudCrystal
 * </pre>
 *
 * 结果是"卸载天境 → 一进存档、第一个服务端刻就崩"。暮色那边是同一个坑，只是还没被踩到。
 * <p>
 * 所以"问在不在"这件事必须由一个<b>不引用任何可选 mod 类型</b>的类来做：本类只有
 * {@link ModList}（永远是 NeoForge 自己的），随时可调、绝不可能崩。
 * <b>调用方一律先问本类，为真才去碰联动类</b> —— 卸载那个 mod 时联动类根本不会被加载。
 */
public final class OptionalMods {

    /** 暮色森林（Twilight Forest）的 modid。 */
    public static final String TWILIGHT_FOREST = "twilightforest";

    /** 天境（Aether）的 modid。 */
    public static final String AETHER = "aether";

    private OptionalMods() {
    }

    /** 装了暮色森林吗。 */
    public static boolean isTwilightForestLoaded() {
        return ModList.get().isLoaded(TWILIGHT_FOREST);
    }

    /** 装了天境吗。 */
    public static boolean isAetherLoaded() {
        return ModList.get().isLoaded(AETHER);
    }
}
