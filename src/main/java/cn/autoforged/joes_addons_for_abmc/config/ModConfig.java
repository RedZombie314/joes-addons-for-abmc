package cn.autoforged.joes_addons_for_abmc.config;

import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class ModConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SMALL_PROJECTILES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LARGE_PROJECTILES;

    public static final ModConfigSpec.BooleanValue LUCKY_DIMENSION_RANDOM_TEXTURES;

    public static final ModConfigSpec.BooleanValue ENABLE_IMMERSIVE_PORTAL_RENDERING;

    public static final ModConfigSpec.ConfigValue<String> NOTE_MUSIC_SOUND_SOURCE;

    public static final ModConfigSpec.BooleanValue BELL_TINNITUS;
    public static final ModConfigSpec.BooleanValue REDSTONE_LASER_SOUNDS;
    public static final ModConfigSpec.BooleanValue COMMAND_FLY_SOUND;

    /** 线/汇聚点偏移：X(右)、Y(上)、Z(前)，作用于所有“发射线或具备汇聚点”的权杖。 */
    public static final ModConfigSpec.DoubleValue CONVERGE_OFFSET_X;
    public static final ModConfigSpec.DoubleValue CONVERGE_OFFSET_Y;
    public static final ModConfigSpec.DoubleValue CONVERGE_OFFSET_Z;

    /** 第三人称相对于第一人称的额外偏移：X(右)、Y(上)、Z(前)。仅第三人称/其他持有者场景叠加，
     *  用于微调两种视角下权杖渲染位置（如高度）的差异。 */
    public static final ModConfigSpec.DoubleValue THIRD_PERSON_OFFSET_X;
    public static final ModConfigSpec.DoubleValue THIRD_PERSON_OFFSET_Y;
    public static final ModConfigSpec.DoubleValue THIRD_PERSON_OFFSET_Z;

    /** 铁链（铁块）权杖：玩家上下摆头时，被抓取生物被提起/下放的最大垂直偏移（格）。 */
    public static final ModConfigSpec.DoubleValue CHAIN_LIFT_STRENGTH;

    /** 开局（首次进入世界）是否赠送一把 blocktype 为 empty 的权杖。 */
    public static final ModConfigSpec.BooleanValue GIVE_STAFF_ON_START;

    /** 变形药水使玩家变身（物品/方块/生物壳/玩家空壳）时，是否强制切换到第三人称视角（默认为开），
     *  复原时自动恢复第一人称。 */
    public static final ModConfigSpec.BooleanValue FORCE_THIRD_PERSON_ON_TRANSMUTATION;

    /** 权杖模式文本的垂直偏移（像素，负值上移）。用于与物品描述文本错开，避免重叠。 */
    public static final ModConfigSpec.IntValue MODE_TEXT_Y_OFFSET;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.translation("joes_addons_for_abmc.config.blocking").push("blocking");
        SMALL_PROJECTILES = builder
            .comment(
                "Small projectiles that can be blocked. Blocking these does not cause the weapon to drop.",
                "Format: entity type registry names, e.g. \"minecraft:arrow\"",
                "Default: minecraft:small_fireball, minecraft:arrow, minecraft:shulker_bullet"
            )
            .translation("joes_addons_for_abmc.config.blocking.small_projectiles")
            .defineListAllowEmpty("small_projectiles",
                List.of("minecraft:small_fireball", "minecraft:arrow", "minecraft:shulker_bullet"),
                o -> o instanceof String);
        LARGE_PROJECTILES = builder
            .comment(
                "Large projectiles that can be blocked. Blocking these has a 10%% chance (modified by Unbreaking) to drop the weapon.",
                "Format: entity type registry names, e.g. \"minecraft:trident\"",
                "Default: minecraft:trident, minecraft:fireball"
            )
            .translation("joes_addons_for_abmc.config.blocking.large_projectiles")
            .defineListAllowEmpty("large_projectiles",
                List.of("minecraft:trident", "minecraft:fireball"),
                o -> o instanceof String);
        builder.pop();

        builder.translation("joes_addons_for_abmc.config.lucky_dimension_block").push("lucky_dimension_block");
        LUCKY_DIMENSION_RANDOM_TEXTURES = builder
            .comment(
                "Whether lucky_dimension_block uses random textures from full cube blocks.",
                "When enabled, the block changes appearance every 10 ticks (staggered per-block by position)",
                "with the filter.png texture overlay at 50% opacity.",
                "When disabled, the block shows its own static texture without any overlay."
            )
            .translation("joes_addons_for_abmc.config.lucky_dimension_block.lucky_dimension_block_random_textures")
            .define("lucky_dimension_block_random_textures", true);
        builder.pop();

        builder.translation("joes_addons_for_abmc.config.portal").push("portal");
        ENABLE_IMMERSIVE_PORTAL_RENDERING = builder
            .comment(
                "Whether portals created by the End Portal Frame staff render the view from the linked portal.",
                "When enabled, the portal shows what's on the other side (immersive portal effect).",
                "When disabled, the portal renders as an end portal block face.",
                "Default: false"
            )
            .translation("joes_addons_for_abmc.config.portal.enable_immersive_portal_rendering")
            .define("enable_immersive_portal_rendering", false);
        builder.pop();

        builder.translation("joes_addons_for_abmc.config.note_music").push("note_music");
        NOTE_MUSIC_SOUND_SOURCE = builder
            .comment(
                "The sound source/category used for the special looping music in the note block universe.",
                "Valid values: " + java.util.Arrays.toString(SoundSource.values()),
                "Set to 'records' (jukebox) so players who turn off the game music slider can still hear it.",
                "Default: records"
            )
            .translation("joes_addons_for_abmc.config.note_music.note_music_sound_source")
            .define("note_music_sound_source", "records");
        builder.pop();

        builder.translation("joes_addons_for_abmc.config.sound").push("sound");
        BELL_TINNITUS = builder
            .comment(
                "Whether the Bell staff plays a tinnitus (ear ringing) sound when rung.",
                "When disabled, ringing the bell still silences other sounds (the sound-dampening effect),",
                "but no ear-ringing sound is played.",
                "Default: false"
            )
            .translation("joes_addons_for_abmc.config.sound.bell_tinnitus")
            .define("bell_tinnitus", false);
        REDSTONE_LASER_SOUNDS = builder
            .comment(
                "Whether the Redstone Block staff plays laser sound effects (start / looping / end) when firing.",
                "When disabled, the laser beam still renders but makes no sound.",
                "Default: true"
            )
            .translation("joes_addons_for_abmc.config.sound.redstone_laser_sounds")
            .define("redstone_laser_sounds", true);
        COMMAND_FLY_SOUND = builder
            .comment(
                "Whether the Command Block staff plays a flight sound while the player is flying with it.",
                "Default: true"
            )
            .translation("joes_addons_for_abmc.config.sound.command_fly_sound")
            .define("command_fly_sound", true);
        builder.pop();

        builder.translation("joes_addons_for_abmc.config.line_converge_offset").push("line_converge_offset");
        CONVERGE_OFFSET_X = builder
            .comment(
                "Horizontal (right) offset of the line emitter / converge point relative to the staff holder.",
                "Applies to all staffs that emit a line or converge particles (redstone laser, cobweb beam,",
                "enchant link, command feedback line, Omega absorb).",
                "The offset is relative to the holder's head direction (visual angle), including look-up/down",
                "pitch, so the position moves relatively when the holder looks up or down.",
                "Range: -10 .. 10, default 0"
            )
            .translation("joes_addons_for_abmc.config.line_converge_offset.converge_offset_x")
            .defineInRange("converge_offset_x", 0.25, -10.0, 10.0);
        CONVERGE_OFFSET_Y = builder
            .comment(
                "Vertical (up) offset of the line emitter / converge point relative to the staff holder.",
                "Range: -10 .. 10, default 0"
            )
            .translation("joes_addons_for_abmc.config.line_converge_offset.converge_offset_y")
            .defineInRange("converge_offset_y", 0.05, -10.0, 10.0);
        CONVERGE_OFFSET_Z = builder
            .comment(
                "Forward (front) offset of the line emitter / converge point relative to the staff holder.",
                "Range: -10 .. 10, default 0"
            )
            .translation("joes_addons_for_abmc.config.line_converge_offset.converge_offset_z")
            .defineInRange("converge_offset_z", 0.35, -10.0, 10.0);
        THIRD_PERSON_OFFSET_X = builder
            .comment(
                "Extra X (right) offset applied only in third person / when another holder uses the staff,",
                "on top of the converge offset. Used to fine-tune the position difference of the staff",
                "between first and third person views.",
                "Range: -10 .. 10, default 0"
            )
            .translation("joes_addons_for_abmc.config.line_converge_offset.third_person_offset_x")
            .defineInRange("third_person_offset_x", 0.0, -10.0, 10.0);
        THIRD_PERSON_OFFSET_Y = builder
            .comment(
                "Extra Y (up) offset applied only in third person / when another holder uses the staff.",
                "Range: -10 .. 10, default 0"
            )
            .translation("joes_addons_for_abmc.config.line_converge_offset.third_person_offset_y")
            .defineInRange("third_person_offset_y", 0.5, -10.0, 10.0);
        THIRD_PERSON_OFFSET_Z = builder
            .comment(
                "Extra Z (forward) offset applied only in third person / when another holder uses the staff.",
                "Range: -10 .. 10, default 0"
            )
            .translation("joes_addons_for_abmc.config.line_converge_offset.third_person_offset_z")
            .defineInRange("third_person_offset_z", -0.1, -10.0, 10.0);
        builder.pop();

        builder.translation("joes_addons_for_abmc.config.chain").push("chain");
        CHAIN_LIFT_STRENGTH = builder
            .comment(
                "Vertical lift strength of the Chain (Iron Block) staff: how far a grabbed creature is",
                "raised when the player looks straight up (and lowered when looking down).",
                "0 disables the lift effect.",
                "Range: 0 .. 10, default 10"
            )
            .translation("joes_addons_for_abmc.config.chain.chain_lift_strength")
            .defineInRange("chain_lift_strength", 10.0, 0.0, 10.0);
        builder.pop();

        builder.translation("joes_addons_for_abmc.config.start").push("start");
        GIVE_STAFF_ON_START = builder
            .comment(
                "Whether to give the player a staff (with empty block type) when they first enter the world.",
                "Only granted once per player; when disabled, it is never granted.",
                "Default: false"
            )
            .translation("joes_addons_for_abmc.config.start.give_staff_on_start")
            .define("give_staff_on_start", false);

        FORCE_THIRD_PERSON_ON_TRANSMUTATION = builder
            .comment(
                "Whether the player is forced into third-person view while transmuted into an",
                "item / block / mob shell / player shell by the transmutation potion.",
                "When enabled, transmuting forces the camera to third-person and reverting",
                "restores it to first-person.",
                "Default: true"
            )
            .translation("joes_addons_for_abmc.config.start.force_third_person_on_transmutation")
            .define("force_third_person_on_transmutation", true);

        MODE_TEXT_Y_OFFSET = builder
            .comment(
                "Vertical offset (pixels) of the staff-mode HUD text; negative moves it up.",
                "Adjust so it does not overlap the item name/description text.",
                "Default: -20"
            )
            .translation("joes_addons_for_abmc.config.start.mode_text_y_offset")
            .defineInRange("mode_text_y_offset", -20, -80, 40);
        builder.pop();

        SPEC = builder.build();
    }
}
