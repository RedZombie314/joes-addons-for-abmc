package cn.autoforged.joes_addons_for_abmc.config;

import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * 模组配置：<b>按"谁读它"拆成两份</b>。
 *
 * <h2>为什么两份</h2>
 * 以前所有键全塞在一份 COMMON 里，于是专用服务器也会生成一堆<b>只有客户端才读</b>的视觉项
 * （随机材质、HUD 文本偏移、客户端音效……）。现在：
 * <ul>
 *   <li>{@link #CLIENT_SPEC} —— {@code config/joes_addons_for_abmc-client.toml}：
 *       只有客户端读的视觉 / 客户端音效 / HUD 微调；</li>
 *   <li>{@link #COMMON_SPEC} —— {@code config/joes_addons_for_abmc-common.toml}：
 *       玩法数值、服务端判定用到的值，以及<b>判定与渲染共用</b>的权杖偏移。</li>
 * </ul>
 *
 * <h2>挪键之前必须查它到底谁在读</h2>
 * 名字像"客户端"不代表真是：{@link #CONVERGE_OFFSET_X} 那组偏移是<b>发射点/汇聚点位置</b>，
 * {@code ModMain#applyLineEmitterOffset} 在服务端算命中、在客户端算渲染 → 只能留 COMMON。
 * 把服务端会读的键放进 CLIENT，专用服务器一读就抛异常。
 * <p>
 * 反过来也成立：想让某个开关进 CLIENT，就得先把<b>读它的那段代码</b>搬到客户端。
 * {@link #COMMAND_FLY_SOUND} 就是这么做的 —— 它原本由服务端
 * （{@code ModMain#handleCommandStaffFlight} 一边给玩家发飞行能力、一边播音效）读取，
 * 现在音效部分搬到了客户端的 {@code CommandStaffFlySound}，开关随之进了 CLIENT。
 *
 * <h2>分区结构</h2>
 * <ul>
 *   <li><b>COMMON</b>：{@code gameplay}、{@code combat}、
 *       {@code staff}（子分区 {@code line} / {@code third_person}）、{@code debug}</li>
 *   <li><b>CLIENT</b>：{@code visual}、{@code sound}</li>
 * </ul>
 * 以前的 {@code start} 是个杂物袋（开局赠礼 / 变形强制第三人称 / HUD 文本 / 觉醒酿造台摆位
 * 四件不相干的事挤在一起），已按语义拆到 {@code gameplay} 与 {@code visual}；
 * {@code line_converge_offset} 名不副实（同时装着线偏移与第三人称偏移），改成
 * {@code staff} 下的两个子分区。
 * <p>
 * 幸运选择器的三个像素值与觉醒酿造台的三瓶摆位<b>已经不再是配置</b>（用户指定移除）：
 * 它们的取值固定在用它们的类里（{@code LuckySelectorEntity} / {@code AwakeningFallingBlockRenderer}）。
 *
 * <p><b>注意</b>：重命名/移除分区与键会让老配置文件里用户改成过的值失效 —— NeoForge 不做迁移，
 * 旧键被忽略、新键写回默认值，旧文件会被它顺手存成 {@code *.bak}。
 */
public class ModConfig {
    /** 服务端与双端共用的一份（{@code Type.COMMON}）。 */
    public static final ModConfigSpec COMMON_SPEC;
    /** 只有客户端读的一份（{@code Type.CLIENT}）。 */
    public static final ModConfigSpec CLIENT_SPEC;

    // ====================== COMMON：combat ======================

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SMALL_PROJECTILES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LARGE_PROJECTILES;

    // ====================== COMMON：staff（判定与渲染共用的偏移） ======================

    /** 线/汇聚点偏移：X(右)、Y(上)、Z(前)，作用于所有“发射线或具备汇聚点”的权杖。 */
    public static final ModConfigSpec.DoubleValue CONVERGE_OFFSET_X;
    public static final ModConfigSpec.DoubleValue CONVERGE_OFFSET_Y;
    public static final ModConfigSpec.DoubleValue CONVERGE_OFFSET_Z;

    /** 第三人称相对于第一人称的额外偏移：X(右)、Y(上)、Z(前)。仅第三人称/其他持有者场景叠加，
     *  用于微调两种视角下权杖渲染位置（如高度）的差异。 */
    public static final ModConfigSpec.DoubleValue THIRD_PERSON_OFFSET_X;
    public static final ModConfigSpec.DoubleValue THIRD_PERSON_OFFSET_Y;
    public static final ModConfigSpec.DoubleValue THIRD_PERSON_OFFSET_Z;

    // ====================== COMMON：gameplay ======================

    /** 开局（首次进入世界）是否赠送一把 blocktype 为 empty 的权杖。 */
    public static final ModConfigSpec.BooleanValue GIVE_STAFF_ON_START;

    /** 变形药水使玩家变身（物品/方块/生物壳/玩家空壳）时，是否强制切换到第三人称视角（默认为开），
     *  复原时自动恢复第一人称。 */
    public static final ModConfigSpec.BooleanValue FORCE_THIRD_PERSON_ON_TRANSMUTATION;

    /** 铁链（铁块）权杖：玩家上下摆头时，被抓取生物被提起/下放的最大垂直偏移（格）。 */
    public static final ModConfigSpec.DoubleValue CHAIN_LIFT_STRENGTH;

    // ====================== COMMON：debug ======================

    /** 调试模式：开启后低概率事件以高概率发生。 */
    public static final ModConfigSpec.BooleanValue DEBUG_MODE;

    // ====================== CLIENT：visual ======================

    public static final ModConfigSpec.BooleanValue LUCKY_DIMENSION_RANDOM_TEXTURES;

    /** 末地传送门框架权杖做出来的传送门是否渲染对侧视角。
     *  <p><b>目前未实装</b>：这个键在代码里<b>没有任何读取点</b>，改它不会有任何效果；
     *  留着是为了以后接上"沉浸式传送门"渲染（详见键上的注释）。 */
    public static final ModConfigSpec.BooleanValue ENABLE_IMMERSIVE_PORTAL_RENDERING;

    /** 权杖模式文本的垂直偏移（像素，负值上移）。用于与物品描述文本错开，避免重叠。 */
    public static final ModConfigSpec.IntValue MODE_TEXT_Y_OFFSET;

    // ====================== CLIENT：sound ======================

    public static final ModConfigSpec.ConfigValue<String> NOTE_MUSIC_SOUND_SOURCE;

    public static final ModConfigSpec.BooleanValue BELL_TINNITUS;
    public static final ModConfigSpec.BooleanValue REDSTONE_LASER_SOUNDS;
    public static final ModConfigSpec.BooleanValue COMMAND_FLY_SOUND;

    static {
        // =========================================================================
        // COMMON：玩法 + 服务端判定用到的值 + 双端共用的偏移
        // =========================================================================
        ModConfigSpec.Builder common = new ModConfigSpec.Builder();

        common.translation("joes_addons_for_abmc.config.combat").push("combat");
        SMALL_PROJECTILES = common
            .comment(
                "Small projectiles that can be blocked. Blocking these does not cause the weapon to drop.",
                "Format: entity type registry names, e.g. \"minecraft:arrow\"",
                "Default: minecraft:small_fireball, minecraft:arrow, minecraft:shulker_bullet"
            )
            .translation("joes_addons_for_abmc.config.combat.small_projectiles")
            .defineListAllowEmpty("small_projectiles",
                List.of("minecraft:small_fireball", "minecraft:arrow", "minecraft:shulker_bullet"),
                o -> o instanceof String);
        LARGE_PROJECTILES = common
            .comment(
                "Large projectiles that can be blocked. Blocking these has a 10% chance (modified by Unbreaking) to drop the weapon.",
                "Format: entity type registry names, e.g. \"minecraft:trident\"",
                "Default: minecraft:trident, minecraft:fireball"
            )
            .translation("joes_addons_for_abmc.config.combat.large_projectiles")
            .defineListAllowEmpty("large_projectiles",
                List.of("minecraft:trident", "minecraft:fireball"),
                o -> o instanceof String);
        common.pop();

        common.translation("joes_addons_for_abmc.config.staff").push("staff");
        common.translation("joes_addons_for_abmc.config.staff.line").push("line");
        CONVERGE_OFFSET_X = common
            .comment(
                "Horizontal (right) offset of the line emitter / converge point relative to the staff holder.",
                "Applies to all staffs that emit a line or converge particles (redstone laser, cobweb beam,",
                "enchant link, command feedback line, Omega absorb).",
                "The offset is relative to the holder's head direction (visual angle), including look-up/down",
                "pitch, so the position moves relatively when the holder looks up or down.",
                "Range: -10 .. 10, default 0.25"
            )
            .translation("joes_addons_for_abmc.config.staff.line.x")
            .defineInRange("x", 0.25, -10.0, 10.0);
        CONVERGE_OFFSET_Y = common
            .comment(
                "Vertical (up) offset of the line emitter / converge point relative to the staff holder.",
                "Range: -10 .. 10, default 0.05"
            )
            .translation("joes_addons_for_abmc.config.staff.line.y")
            .defineInRange("y", 0.05, -10.0, 10.0);
        CONVERGE_OFFSET_Z = common
            .comment(
                "Forward (front) offset of the line emitter / converge point relative to the staff holder.",
                "Range: -10 .. 10, default 0.35"
            )
            .translation("joes_addons_for_abmc.config.staff.line.z")
            .defineInRange("z", 0.35, -10.0, 10.0);
        common.pop();   // line

        common.translation("joes_addons_for_abmc.config.staff.third_person").push("third_person");
        THIRD_PERSON_OFFSET_X = common
            .comment(
                "Extra X (right) offset applied only in third person / when another holder uses the staff,",
                "on top of the line offset. Used to fine-tune the position difference of the staff",
                "between first and third person views.",
                "Range: -10 .. 10, default 0"
            )
            .translation("joes_addons_for_abmc.config.staff.third_person.x")
            .defineInRange("x", 0.0, -10.0, 10.0);
        THIRD_PERSON_OFFSET_Y = common
            .comment(
                "Extra Y (up) offset applied only in third person / when another holder uses the staff.",
                "Range: -10 .. 10, default 0.5"
            )
            .translation("joes_addons_for_abmc.config.staff.third_person.y")
            .defineInRange("y", 0.5, -10.0, 10.0);
        THIRD_PERSON_OFFSET_Z = common
            .comment(
                "Extra Z (forward) offset applied only in third person / when another holder uses the staff.",
                "Range: -10 .. 10, default -0.1"
            )
            .translation("joes_addons_for_abmc.config.staff.third_person.z")
            .defineInRange("z", -0.1, -10.0, 10.0);
        common.pop();   // third_person
        common.pop();   // staff

        common.translation("joes_addons_for_abmc.config.gameplay").push("gameplay");
        GIVE_STAFF_ON_START = common
            .comment(
                "Whether to give the player a staff (with empty block type) when they first enter the world.",
                "Only granted once per player; when disabled, it is never granted.",
                "Default: false"
            )
            .translation("joes_addons_for_abmc.config.gameplay.give_staff_on_start")
            .define("give_staff_on_start", false);

        FORCE_THIRD_PERSON_ON_TRANSMUTATION = common
            .comment(
                "Whether the player is forced into third-person view while transmuted into an",
                "item / block / mob shell / player shell by the transmutation potion.",
                "When enabled, transmuting forces the camera to third-person and reverting",
                "restores it to first-person.",
                "Default: true"
            )
            .translation("joes_addons_for_abmc.config.gameplay.force_third_person_on_transmutation")
            .define("force_third_person_on_transmutation", true);

        CHAIN_LIFT_STRENGTH = common
            .comment(
                "Vertical lift strength of the Chain (Iron Block) staff: how far a grabbed creature is",
                "raised when the player looks straight up (and lowered when looking down).",
                "0 disables the lift effect.",
                "Range: 0 .. 10, default 10"
            )
            .translation("joes_addons_for_abmc.config.gameplay.chain_lift_strength")
            .defineInRange("chain_lift_strength", 10.0, 0.0, 10.0);
        common.pop();   // gameplay

        common.translation("joes_addons_for_abmc.config.debug").push("debug");
        DEBUG_MODE = common
            .comment(
                "Debug mode. When enabled, low-probability events will happen with much higher probability.",
                "(Only effective in new worlds.)",
                "Effects:",
                "- The first librarian encountered will always sell command blocks.",
                "- The first swamp hut encountered will always be replaced by a witch boss hut.",
                "Default: false"
            )
            .translation("joes_addons_for_abmc.config.debug.debug_mode")
            .define("debug_mode", false);
        common.pop();

        COMMON_SPEC = common.build();

        // =========================================================================
        // CLIENT：只有客户端读的视觉 / 音效 / HUD
        // =========================================================================
        ModConfigSpec.Builder client = new ModConfigSpec.Builder();

        client.translation("joes_addons_for_abmc.config.visual").push("visual");
        LUCKY_DIMENSION_RANDOM_TEXTURES = client
            .comment(
                "Whether lucky_dimension_block uses random textures from full cube blocks.",
                "When enabled, the block changes appearance every 10 ticks (staggered per-block by position)",
                "and draws only that random material. The 50% filter.png overlay is currently disabled;",
                "its code is kept in both renderers and can be re-enabled by uncommenting the call sites.",
                "When disabled, the block shows its own static texture without any overlay."
            )
            .translation("joes_addons_for_abmc.config.visual.lucky_dimension_block_random_textures")
            .define("lucky_dimension_block_random_textures", true);

        ENABLE_IMMERSIVE_PORTAL_RENDERING = client
            .comment(
                "Whether portals created by the End Portal Frame staff render the view from the linked portal.",
                "When enabled, the portal shows what's on the other side (immersive portal effect).",
                "When disabled, the portal renders as an end portal block face.",
                "NOT IMPLEMENTED YET: nothing in the code reads this value, so changing it has no effect.",
                "It is kept here (and in the lang files) so the option can be wired up later.",
                "Default: false"
            )
            .translation("joes_addons_for_abmc.config.visual.enable_immersive_portal_rendering")
            .define("enable_immersive_portal_rendering", false);

        MODE_TEXT_Y_OFFSET = client
            .comment(
                "Vertical offset (pixels) of the staff-mode HUD text; negative moves it up.",
                "Adjust so it does not overlap the item name/description text.",
                "Default: -20"
            )
            .translation("joes_addons_for_abmc.config.visual.mode_text_y_offset")
            .defineInRange("mode_text_y_offset", -20, -80, 40);
        client.pop();   // visual

        client.translation("joes_addons_for_abmc.config.sound").push("sound");
        NOTE_MUSIC_SOUND_SOURCE = client
            .comment(
                "The sound source/category used for the special looping music in the note block universe.",
                "Valid values: " + java.util.Arrays.toString(SoundSource.values()),
                "Set to 'records' (jukebox) so players who turn off the game music slider can still hear it.",
                "Default: records"
            )
            .translation("joes_addons_for_abmc.config.sound.note_music_sound_source")
            .define("note_music_sound_source", "records");

        BELL_TINNITUS = client
            .comment(
                "Whether the Bell staff plays a tinnitus (ear ringing) sound when rung.",
                "When disabled, ringing the bell still silences other sounds (the sound-dampening effect),",
                "but no ear-ringing sound is played.",
                "Default: false"
            )
            .translation("joes_addons_for_abmc.config.sound.bell_tinnitus")
            .define("bell_tinnitus", false);
        REDSTONE_LASER_SOUNDS = client
            .comment(
                "Whether the Redstone Block staff plays laser sound effects (start / looping / end) when firing.",
                "When disabled, the laser beam still renders but makes no sound.",
                "Default: true"
            )
            .translation("joes_addons_for_abmc.config.sound.redstone_laser_sounds")
            .define("redstone_laser_sounds", true);
        COMMAND_FLY_SOUND = client
            .comment(
                "Whether the Command Block staff plays a flight sound while the player is flying with it.",
                "Client-side: the sound is played locally (see CommandStaffFlySound), so the flying",
                "ability itself is unaffected by this toggle.",
                "Default: true"
            )
            .translation("joes_addons_for_abmc.config.sound.command_fly_sound")
            .define("command_fly_sound", true);
        client.pop();   // sound

        CLIENT_SPEC = client.build();
    }
}
