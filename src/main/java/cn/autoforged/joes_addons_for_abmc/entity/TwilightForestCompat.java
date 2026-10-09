package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.entity.OrbPossessedAttackEvents.PossessedAttackEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * <b>暮色森林（Twilight Forest）联动</b>：装了暮色时，附体空壳会额外抽取一批暮色武器，
 * 并加入两条专攻行为（护甲高的目标 → 骑士剑；烈焰人 → 寒冰剑）。
 *
 * <h2>软依赖怎么保证安全（务必照做）</h2>
 * <ul>
 *   <li>{@code build.gradle} 里只有 {@code compileOnly libs/twilightforest-....jar}：
 *       <b>不打包、不声明依赖</b>，没装暮色时游戏照常启动；</li>
 *   <li><b>绝不能在"暮色缺席时"被加载</b>：本类的方法体里全是暮色的类型，卸载暮色后一旦被加载，
 *       JVM 解析它们就会抛 {@code NoClassDefFoundError}（天境那边实测崩过一次：
 *       卸载 mod 后进存档、第一个服务端刻就崩在 {@code ModMain#onServerTickPre}）。
 *       所以<b>所有调用点都先问 {@link OptionalMods#isTwilightForestLoaded()}</b> ——
 *       那个类不引用任何暮色类型，问它不会把本类加载起来；</li>
 *   <li>引用暮色类的代码全部关在<b>方法体与私有嵌套类</b>里，本类自身没有暮色类型的字段、
 *       静态初始化也不碰暮色。</li>
 * </ul>
 *
 * <h2>本文件实现的九种手段</h2>
 * <ol>
 *   <li>远程：暮色权杖（{@code twilight_scepter}）射出 {@code wand_bolt}（暮色自己的 6 点伤害）；</li>
 *   <li>近战：僵尸权杖（{@code zombie_scepter}）召唤忠诚僵尸（{@code loyal_zombie}，驯服给空壳）；</li>
 *   <li>远程：力量 V 的三发弓 / 追踪弓 —— <b>抽到本项后内部五五开</b>
 *       （三发弓照暮色本体来：三支箭同方向、只在竖直初速上散开，且三支都无视无敌帧，
 *       见 {@link TwilightBowEvent}）；</li>
 *   <li>近战：锋利 V 钻石米诺陶战斧 —— 原地蓄力 1 秒后朝目标冲刺（见 {@link ChargeDash}）；</li>
 *   <li>近战：锋利 V 玻璃剑；</li>
 *   <li>近战：锋利 V 寒冰剑；</li>
 *   <li>近战：锋利 V 赤铁剑；</li>
 *   <li>近战：锋利 V 骑士剑；</li>
 *   <li>远程：链锤（{@code block_and_chain}）投出 {@code chain_block}（暮色自己的 10 点伤害，命中后回手）；</li>
 *   <li>远程：力量 V 的寒冰弓（{@code ice_bow}）—— 射出暮色自己的 {@code ice_arrow}。
 *       对<b>烈焰人</b>时它会明显更常被选中（见 {@link #addMatchups}）。</li>
 * </ol>
 *
 * <p>一处与你的说法不同、必须说明的地方：<b>链锤哥布林并不投掷 {@code ChainBlock}</b> ——
 * 它用的是未注册的分体实体 {@code SpikeBlock}（走 {@code ThrowSpikeBlockGoal}），
 * 没法照搬。所以这里走的是<b>玩家那条路</b>：{@code ChainBlockItem} 用的
 * {@code chain_block} 投掷物，效果（命中 10 点、粘住后回手）与玩家甩链锤一致。
 */
public final class TwilightForestCompat {
    /** 暮色森林的 modid（真正的"装没装"判断在 {@link OptionalMods#isTwilightForestLoaded()}）。 */
    public static final String MOD_ID = OptionalMods.TWILIGHT_FOREST;

    /**
     * 目标是不是正暴露在雨中（露天 + 下雨/雷暴）。
     * <p>
     * 用原版 {@code Level#isRainingAt(BlockPos)}：它已经包含了"下雨/雷暴"与"该位置能看见天"
     * 两道判定，还额外看过群系降水类型（沙漠下雨不算）。
     */
    static boolean isExposedToRain(LivingEntity entity) {
        return entity.level().isRainingAt(entity.blockPosition());
    }

    // ====================== 九头蛇（Hydra） ======================

    /**
     * 暮色九头蛇头部"嘴张开"的判定阈值。
     * <p>
     * {@code HydraHead#getMouthOpen()} 是 0~1 的动画量，暮色自己给各状态的取值是：
     * {@code IDLE}/{@code ATTACK_COOLDOWN} = 0、{@code BITING} = 0.2、
     * {@code BITE_READY}/{@code FLAMING}/{@code MORTAR_SHOOTING}/{@code ROAR_RAWR} = 1
     * （见 {@code HydraHeadContainer#setupStateRotations} 里的 {@code setAnimation} 常量）。
     * 取 0.5 就是"嘴确实大张着"（咬下去那一下 0.2 不算）。
     */
    private static final float HYDRA_MOUTH_OPEN = 0.5F;

    /** 目标是不是暮色九头蛇。 */
    static boolean isHydra(LivingEntity target) {
        return target instanceof twilightforest.entity.boss.Hydra;
    }

    /**
     * 九头蛇是不是有<b>活着的头正张着嘴</b>（咬/喷火/吐火球/咆哮期间）。
     * <p>
     * 用于"只在张嘴时出手"：九头蛇的身体整段免疫伤害，只有 {@code HydraPart}（头/脖子）
     * 能吃伤害，而张嘴那一刻头基本不动、最好打。
     */
    static boolean isHydraMouthOpen(LivingEntity target) {
        if (!(target instanceof twilightforest.entity.boss.Hydra hydra)) {
            return false;
        }
        for (twilightforest.entity.boss.HydraHeadContainer container : hydra.hc) {
            if (container == null || container.headEntity == null) {
                continue;
            }
            if (container.headEntity.isActive()
                && container.headEntity.getMouthOpen() > HYDRA_MOUTH_OPEN) {
                return true;
            }
        }
        return false;
    }

    /**
     * 九头蛇该瞄的点：某个<b>活着的头的碰撞箱中心</b>（优先挑"正张着嘴"的那个 —— 它正在攻击、
     * 位置最稳，也正好对应"张嘴时出手"）。
     * <p>
     * 返回 {@code null} = 不是九头蛇（或一个头都不在），调用方退回原版瞄法（瞄眼位）。
     */
    @Nullable
    static Vec3 hydraHeadAimPoint(LivingEntity target) {
        if (!(target instanceof twilightforest.entity.boss.Hydra hydra)) {
            return null;
        }
        Vec3 fallback = null;
        for (twilightforest.entity.boss.HydraHeadContainer container : hydra.hc) {
            if (container == null || container.headEntity == null || !container.headEntity.isActive()) {
                continue;
            }
            Vec3 center = container.headEntity.getBoundingBox().getCenter();
            if (container.headEntity.getMouthOpen() > HYDRA_MOUTH_OPEN) {
                return center;
            }
            if (fallback == null) {
                fallback = center;
            }
        }
        return fallback;
    }

    /**
     * <b>挑一个"这次该锁定哪个头"</b>（重锤砸击专用，用户指定：锁定头而不是身体）。
     * <p>
     * 挑法与 {@link #hydraHeadAimPoint} 一致：优先<b>正张着嘴</b>的那个头（它正在攻击、位置最稳，
     * 嘴也正好对着外面），没有就退回第一个活着的头。
     * <p>
     * 返回 {@code null} = 现在一个活着的头都没有（那就只能还按身体打，反正打不动）。
     * 用 {@link Entity} 而不是 {@code HydraHead} 当返回类型，是为了让调用方
     * （{@code OrbPossessionMaceAttack}，非联动类）不必把暮色类型写进自己的常量池。
     */
    @Nullable
    static Entity pickHydraHead(LivingEntity target) {
        if (!(target instanceof twilightforest.entity.boss.Hydra hydra)) {
            return null;
        }
        Entity fallback = null;
        for (twilightforest.entity.boss.HydraHeadContainer container : hydra.hc) {
            if (container == null || container.headEntity == null || !container.headEntity.isActive()) {
                continue;
            }
            if (container.headEntity.getMouthOpen() > HYDRA_MOUTH_OPEN) {
                return container.headEntity;
            }
            if (fallback == null) {
                fallback = container.headEntity;
            }
        }
        return fallback;
    }

    /**
     * 这个部件是不是这头九头蛇身上<b>现在还活着</b>的一个头。
     * <p>
     * 给重锤用：锁定某个头之后一路沿用（每刻重挑会让弹道在几个头之间横跳），
     * 只有它被打死（{@code HydraHead#isActive()} 变 false）时才另挑一个 —— 判定就是这一条。
     */
    static boolean isHydraHeadOf(LivingEntity hydra, Entity part) {
        if (!(hydra instanceof twilightforest.entity.boss.Hydra boss) || part == null) {
            return false;
        }
        for (twilightforest.entity.boss.HydraHeadContainer container : boss.hc) {
            if (container == null || container.headEntity == null) {
                continue;
            }
            if (container.headEntity == part) {
                return container.headEntity.isActive();
            }
        }
        return false;
    }

    /** 附魔等级：力量 V、锋利 V。 */
    private static final int ENCHANT_LEVEL = 5;

    /**
     * 三发弓三支箭的<b>竖直初速差</b>（格/刻）：照搬暮色本体的
     * {@code TripleBowItem#shoot} 里那一句 {@code setDeltaMovement(...add(0, 0.0075 * 20 * j, 0))}
     * （{@code 0.0075 × 20 = 0.15}，j = -1 / 0 / +1）。
     */
    private static final double TRIPLE_BOW_VERTICAL_STEP = 0.0075D * 20.0D;

    /** 链锤出膛速度（格/刻）：与暮色 {@code ChainBlockItem} 一致。 */
    private static final float CHAIN_SPEED = 1.5F;

    /** 冲刺持续时间上限（刻）：5 秒还没撞上就放弃。 */
    private static final int CHARGE_MAX_TICKS = 100;

    /** 冲刺中"卡住"判定：连续这么多刻没有位移就中断（等价于"寻路被打断"）。 */
    private static final int CHARGE_STUCK_TICKS = 12;

    /** 冲刺中断距离（格）：目标跑出这个范围就停。 */
    private static final double CHARGE_ABORT_DISTANCE = 20.0D;

    /** "护甲较高"的门槛（护甲点数）：达到就更容易被骑士剑招呼。 */
    private static final int ARMORED_THRESHOLD = 10;

    /** 专攻行为里"较高概率"的具体数值。 */
    private static final float SPECIAL_BIAS_CHANCE = 0.75F;

    /**
     * 寒冰系武器（<b>寒冰剑 / 寒冰弓</b>）的抽签权重：普通签的 <b>1/2</b>（用户指定）。
     * <p>
     * 由 {@link TfSwordEvent#weight()}（仅 {@code SwordKind.ICE} 那一支）与
     * {@link IceBowEvent#weight()} 返回。注意这两张签同时也出现在"偏好事件"的位置上
     * （烈焰人那条特攻里 {@code rangedBias}/{@code meleeBias}）：那条路走的是
     * {@code prefer(...)}（按概率直接兑现），<b>不看权重</b>，
     * 所以对烈焰人时寒冰弓/剑依旧会被优先兑现，权重只影响"正常抽签"时它被抽中的机会。
     */
    private static final float ICE_WEAPON_WEIGHT = 0.5F;

    // ===== 事件实例（都是本 mod 的类型，可以安全地作为静态字段） =====
    private static PossessedAttackEvent rangedScepter;
    private static PossessedAttackEvent rangedBow;
    private static PossessedAttackEvent rangedIceBow;
    private static PossessedAttackEvent rangedChain;
    private static PossessedAttackEvent meleeZombieScepter;
    private static PossessedAttackEvent meleeMinotaurAxe;
    private static PossessedAttackEvent meleeGlassSword;
    private static PossessedAttackEvent meleeIceSword;
    private static PossessedAttackEvent meleeFierySword;
    private static PossessedAttackEvent meleeKnightSword;

    private TwilightForestCompat() {
    }

    /** 把暮色的远程手段加进远程池（只在装了暮色时调用）。 */
    static void addRangedEvents(List<PossessedAttackEvent> list) {
        rangedScepter = new TwilightScepterEvent();
        rangedBow = new TwilightBowEvent();
        rangedIceBow = new IceBowEvent();
        rangedChain = new ChainBlockEvent();
        list.add(rangedScepter);
        list.add(rangedBow);
        list.add(rangedIceBow);
        list.add(rangedChain);
    }

    /** 把暮色的近战手段加进近战池（只在装了暮色时调用）。 */
    static void addMeleeEvents(List<PossessedAttackEvent> list) {
        meleeZombieScepter = new ZombieScepterEvent();
        meleeMinotaurAxe = new MinotaurAxeChargeEvent();
        meleeGlassSword = new TfSwordEvent(SwordKind.GLASS);
        meleeIceSword = new TfSwordEvent(SwordKind.ICE);
        meleeFierySword = new TfSwordEvent(SwordKind.FIERY);
        meleeKnightSword = new TfSwordEvent(SwordKind.KNIGHTMETAL);
        list.add(meleeZombieScepter);
        list.add(meleeMinotaurAxe);
        list.add(meleeGlassSword);
        list.add(meleeIceSword);
        list.add(meleeFierySword);
        list.add(meleeKnightSword);
    }

    /**
     * 追加暮色专属的特攻行为（只在装了暮色时调用）。
     * <p>
     * 顺序有讲究：列表是"第一个命中的生效"，所以<b>类型条件（烈焰人）排在属性条件（护甲值）前面</b>，
     * 免得"带甲的烈焰人"被护甲那条截胡。
     */
    static void addMatchups(List<OrbPossessedAttackEvents.Matchup> matchups) {
        // 巫妖（Lich）：<b>它还是满血时</b>一律改用暮色权杖打远程（偏好概率 100%），
        // 直到它的血量第一次掉下最大值为止 —— 条件每次抽签都重新判定，所以"破了盾/掉了血"
        // 之后这条特攻自动失效，抽签回到正常池子。
        // alwaysMelee = false：<b>贴身也照样先掏权杖</b>（满血阶段一律走远程池，而远程池的偏好
        // 就是 100% 权杖），不然目标一贴脸就会被"贴身必近战"抢走，变成拿剑去砍。
        //
        // ⚠ 这条<b>必须插在列表最前面</b>，不能像其它暮色条目那样追加到末尾：
        //   巫妖在暮色的 data/twilightforest/tags/entity_type/skeletons.json 里，
        //   而原版 #minecraft:sensitive_to_smite 包含 #minecraft:skeletons
        //   —— 也就是说巫妖<b>属于亡灵</b>，会先命中前面那条"亡灵 → 偏好闪烁西瓜刀"（0.75）。
        //   西瓜刀打不破巫妖的盾：只有伤害类型 twilightforest:twilight_scepter
        //   （见暮色的 data/twilightforest/tags/damage_type/breaks_lich_shields.json）
        //   以及 lich_bolt / magic / indirect_magic / sonic_boom 能破盾。
        //   之前追加在末尾时，实战表现就是"老是丢西瓜刀、破不了盾"（用户反馈）。
        //   移到最前面之后：护盾期必定先掏权杖破盾；血掉下来之后那条亡灵偏好接棒，
        //   西瓜刀对亡灵有 ×20 乘区，正好用来收尾 —— 两段衔接，各打各的强项。
        matchups.add(0, new OrbPossessedAttackEvents.Matchup(
            target -> target instanceof twilightforest.entity.boss.Lich
                && target.getHealth() >= target.getMaxHealth(),
            null, null, rangedScepter, 1.0F, null, 0.0F, false));
        // 九头蛇（Hydra）：身体整段免疫伤害、只有头/脖子（HydraPart）能吃伤害，
        // 所以贴身的劈砍等于白打 —— 一律保持距离打远程（远程弹道会瞄头的碰撞箱，
        // 见 OrbPossessedAttackEvents#aimPointOf），"只在某个头张嘴时才出手"也由那边把关。
        // 远程池用 HYDRA_RANGED：默认远程池<b>减去"召唤己方凋灵"</b>（用户要求，详见该常量的注释）。
        matchups.add(new OrbPossessedAttackEvents.Matchup(
            target -> target instanceof twilightforest.entity.boss.Hydra,
            OrbPossessedAttackEvents.HYDRA_RANGED, null, null, 0.0F, null, 0.0F, false));
        // 烈焰人：近战较大概率改用寒冰剑（寒冰剑命中会给目标挂霜冻，见暮色 IceSwordItem），
        // 远程则较大概率改用寒冰弓（射出的 ice_arrow 同样带霜冻）—— 冰系对烈焰人最合适。
        matchups.add(new OrbPossessedAttackEvents.Matchup(
            target -> target.getType() == EntityType.BLAZE,
            null, null, rangedIceBow, SPECIAL_BIAS_CHANCE, meleeIceSword, SPECIAL_BIAS_CHANCE, true));
        // 护甲高的目标：近战较大概率改用骑士剑（骑士剑对带甲目标额外 +2，见暮色 ToolEvents）
        matchups.add(new OrbPossessedAttackEvents.Matchup(
            target -> target.getArmorValue() >= ARMORED_THRESHOLD,
            null, null, null, 0.0F, meleeKnightSword, SPECIAL_BIAS_CHANCE, true));
    }

    // ====================== 工具 ======================

    /** 造一把带指定附魔的暮色武器。附魔拿不到注册表时退回白板，不让整次攻击崩掉。 */
    private static ItemStack enchanted(net.minecraft.world.level.Level level, net.minecraft.world.item.Item item,
                                       net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment) {
        ItemStack stack = new ItemStack(item);
        if (level.registryAccess() != null) {
            stack.enchant(enchantment, ENCHANT_LEVEL);
        }
        return stack;
    }

    /** 力量 V 的附魔 holder；拿不到返回 null。 */
    @Nullable
    private static net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> power(
        net.minecraft.world.level.Level level) {
        return level.registryAccess() == null ? null
            : level.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.POWER);
    }

    /** 锋利 V 的附魔 holder；拿不到返回 null。 */
    @Nullable
    private static net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> sharpness(
        net.minecraft.world.level.Level level) {
        return level.registryAccess() == null ? null
            : level.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
    }

    /** 组装一把带锋利 V 的武器（附魔缺失时就是白板）。 */
    private static ItemStack sharpened(net.minecraft.world.level.Level level, net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        var holder = sharpness(level);
        if (holder != null) {
            stack.enchant(holder, ENCHANT_LEVEL);
        }
        return stack;
    }

    /**
     * 空壳挥动手里那把武器打一下。
     * <p>
     * 实现已经搬到 {@link OrbPossessedAttackEvents#swingWeapon}（天境联动也要用同一份），
     * 这里只留一个转发，免得两边的伤害公式走偏。
     */
    private static void swingWeapon(LivingEntity attacker, LivingEntity target, ItemStack weapon) {
        OrbPossessedAttackEvents.swingWeapon(attacker, target, weapon);
    }

    // ====================== 近战：暮色四剑 ======================

    /** 四把"锋利 V"暮色剑。 */
    private enum SwordKind {
        GLASS, ICE, FIERY, KNIGHTMETAL;

        /** 对应的暮色物品（引用暮色类的代码只在这个枚举的方法体里）。 */
        net.minecraft.world.item.Item item() {
            return switch (this) {
                case GLASS -> twilightforest.init.TFItems.GLASS_SWORD.get();
                case ICE -> twilightforest.init.TFItems.ICE_SWORD.get();
                case FIERY -> twilightforest.init.TFItems.FIERY_SWORD.get();
                case KNIGHTMETAL -> twilightforest.init.TFItems.KNIGHTMETAL_SWORD.get();
            };
        }

        String label() {
            return switch (this) {
                case GLASS -> "玻璃剑";
                case ICE -> "寒冰剑";
                case FIERY -> "赤铁剑";
                case KNIGHTMETAL -> "骑士剑";
            };
        }
    }

    /** 近战：手持锋利 V 的某把暮色剑贴身劈砍。 */
    private static final class TfSwordEvent implements PossessedAttackEvent {

        private static final int COOLDOWN_TICKS = 30;

        /**
         * 起手（举剑）刻数，与模组基础近战（闪烁西瓜刀）的 6 刻一致。
         * <p>
         * 同 {@code TwilightScepterEvent}：空壳只在 {@code windUpTicks() > 0} 时才调用
         * {@link #beginWindUp}，少了这个值就"看不到举剑"，剑只在挥砍那一瞬间闪一下
         * （挥砍本身在 {@code swingWeapon} 里会再设一次手持物）。
         */
        private static final int WIND_UP_TICKS = 6;

        private final SwordKind kind;

        TfSwordEvent(SwordKind kind) {
            this.kind = kind;
        }

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        @Override
        public boolean canRun(LivingEntity attacker, LivingEntity target) {
            // 炽铁剑的卖点是"点燃目标"，而目标淋着雨时那点火根本挂不住 —— 雨天近战就不出这张签，
            // 让抽签落到别的近战手段上（抽到它也会被 canRun 挡回去、当场重抽）。
            if (this.kind == SwordKind.FIERY && isExposedToRain(target)) {
                return false;
            }
            return attacker.distanceToSqr(target) <= OrbPossessedAttackEvents.MELEE_RANGE
                * OrbPossessedAttackEvents.MELEE_RANGE;
        }

        @Override
        public boolean shouldPauseMovement(LivingEntity attacker, LivingEntity target) {
            return this.canRun(attacker, target);
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        /**
         * 寒冰剑的权重是普通签的 {@link #ICE_WEAPON_WEIGHT}（1/2）；其余剑（玻璃/炽铁/骑士）保持默认 1。
         * <p>
         * 用户要求只降"寒冰剑/寒冰弓"，所以这里按 {@link SwordKind} 分支，不整个类一起降。
         */
        @Override
        public float weight() {
            return this.kind == SwordKind.ICE ? ICE_WEAPON_WEIGHT : 1.0F;
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            // 起手就把剑亮出来（锋利 V 的光效）
            attacker.setItemInHand(InteractionHand.MAIN_HAND, sharpened(attacker.level(), this.kind.item()));
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            swingWeapon(attacker, target, sharpened(attacker.level(), this.kind.item()));
        }
    }

    // ====================== 近战：僵尸权杖 ======================

    /** 近战：僵尸权杖召唤忠诚僵尸（和 Bob 一样挂共享索敌、打召唤物标记、附体结束一并清场）。 */
    private static final class ZombieScepterEvent implements PossessedAttackEvent {

        /** 同一颗核心最多在场的忠诚僵尸数量。 */
        private static final int CAP = 3;

        private static final int COOLDOWN_TICKS = 100;

        /** 召唤签：会<b>留下</b>忠诚僵尸，{@code /jafa luck_attack nosummons} 下不抽。 */
        @Override
        public boolean summons() {
            return true;
        }

        @Override
        public boolean canRun(LivingEntity attacker, LivingEntity target) {
            return attacker.distanceToSqr(target) <= OrbPossessedAttackEvents.MELEE_RANGE
                * OrbPossessedAttackEvents.MELEE_RANGE;
        }

        @Override
        public boolean shouldPauseMovement(LivingEntity attacker, LivingEntity target) {
            return this.canRun(attacker, target);
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            UUID orbUuid = OrbPossessedAttackEvents.orbOfAttacker(attacker);
            int alive = orbUuid == null ? 0
                : OrbPossessionSummons.countRole(level, orbUuid, OrbPossessionSummons.ROLE_TF_LOYAL_ZOMBIE);
            if (alive >= CAP) {
                return;
            }

            attacker.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(twilightforest.init.TFItems.ZOMBIE_SCEPTER.get()));
            attacker.swing(InteractionHand.MAIN_HAND);

            twilightforest.entity.monster.LoyalZombie zombie =
                twilightforest.init.TFEntities.LOYAL_ZOMBIE.get().create(level);
            if (zombie == null) {
                return;
            }
            // 与僵尸权杖本体一致：驯服 + 认主（认的是这具空壳）+ 力量 II 两分钟
            zombie.setTame(true, false);
            zombie.setOwnerUUID(attacker.getUUID());
            zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1200, 1));
            zombie.moveTo(attacker.getX() + (attacker.getRandom().nextDouble() - 0.5) * 2.0,
                attacker.getY(),
                attacker.getZ() + (attacker.getRandom().nextDouble() - 0.5) * 2.0,
                attacker.getRandom().nextFloat() * 360.0F, 0.0F);
            zombie.setPersistenceRequired();
            // 共享索敌：清掉它自己的目标 goal，换成抄空壳的目标（于是不会去打自己人）
            zombie.targetSelector.removeAllGoals(goal -> true);
            zombie.targetSelector.addGoal(0, new OrbSummonTargetGoal(zombie, attacker.getUUID()));
            OrbPossessedAttackEvents.markSummon(attacker, zombie);
            OrbPossessionSummons.markRole(zombie, OrbPossessionSummons.ROLE_TF_LOYAL_ZOMBIE);
            level.addFreshEntity(zombie);
        }
    }

    // ====================== 近战：米诺陶战斧冲刺 ======================

    /**
     * 近战：锋利 V 钻石米诺陶战斧 —— 原地蓄力 1 秒（{@link #WIND_UP_TICKS}），
     * 然后朝目标<b>冲刺</b>（{@code setSprinting(true)}，暮色那把斧子的冲刺额外伤害
     * 正是靠 {@code isSprinting()} 判定的），20 格内不中断，除非目标离开范围或自己卡住。
     */
    private static final class MinotaurAxeChargeEvent implements PossessedAttackEvent {

        private static final int WIND_UP_TICKS = 20;
        private static final int COOLDOWN_TICKS = 80;

        @Override
        public boolean canRun(LivingEntity attacker, LivingEntity target) {
            return attacker.distanceToSqr(target) <= OrbPossessedAttackEvents.MELEE_RANGE
                * OrbPossessedAttackEvents.MELEE_RANGE;
        }

        @Override
        public boolean shouldPauseMovement(LivingEntity attacker, LivingEntity target) {
            // 蓄力期间站定（冲刺开始后由 ChargeDash 接管走位）
            return this.canRun(attacker, target);
        }

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            attacker.setItemInHand(InteractionHand.MAIN_HAND, sharpened(attacker.level(),
                twilightforest.init.TFItems.DIAMOND_MINOTAUR_AXE.get()));
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            ChargeDash.start(level, attacker, target);
        }

        @Override
        public void endWindUp(LivingEntity attacker) {
            // 蓄力结束（无论是否真的冲出去）都收起"疾跑"状态，免得下次抽签时还挂着
            if (!ChargeDash.isDashing(attacker)) {
                attacker.setSprinting(false);
            }
        }
    }

    /** 进行中的米诺陶战斧冲刺。 */
    private record Dash(ServerLevel level, UUID shellUuid, UUID targetUuid, long endTick) {
    }

    private static final List<Dash> DASHES = new ArrayList<>();

    /**
     * 冲刺驱动（由 {@code ModMain} 每服务端刻调用一次；没装暮色时列表恒空，空转）。
     *
     * <p>每刻把空壳朝目标推进（用导航 + 疾跑），到近战距离就用战斧抡一下并结束冲刺；
     * 目标死亡/卸载、目标跑出 20 格、超时、或连续若干刻没有位移（"寻路被打断"）都会中断。
     */
    public static void tick() {
        if (DASHES.isEmpty()) {
            return;
        }
        Iterator<Dash> iterator = DASHES.iterator();
        while (iterator.hasNext()) {
            Dash dash = iterator.next();
            Entity shellEntity = dash.level().getEntity(dash.shellUuid());
            Entity targetEntity = dash.level().getEntity(dash.targetUuid());
            if (!(shellEntity instanceof Mob shell)
                || !(targetEntity instanceof LivingEntity target)
                || !target.isAlive()
                || dash.level().getGameTime() > dash.endTick()) {
                stopDash(iterator, shellEntity);
                continue;
            }
            if (shell.distanceToSqr(target) > CHARGE_ABORT_DISTANCE * CHARGE_ABORT_DISTANCE) {
                stopDash(iterator, shell);   // 目标跑出 20 格 → 中断
                continue;
            }
            if (shell.distanceToSqr(target)
                <= OrbPossessedAttackEvents.MELEE_RANGE * OrbPossessedAttackEvents.MELEE_RANGE) {
                // 撞上了：用战斧抡一下（此刻处于疾跑 + 手持米诺陶战斧，暮色的冲刺额外伤害会自动叠上）
                swingWeapon(shell, target, sharpened(dash.level(),
                    twilightforest.init.TFItems.DIAMOND_MINOTAUR_AXE.get()));
                stopDash(iterator, shell);
                continue;
            }
            // 卡住判定：连续若干刻几乎没移动，说明被挡住/寻路失败
            if (shell.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4) {
                if (stuckTicks(shell) > CHARGE_STUCK_TICKS) {
                    stopDash(iterator, shell);
                    continue;
                }
            } else {
                clearStuckTicks(shell);
            }
            shell.setSprinting(true);
            shell.getNavigation().moveTo(target, 1.5D);
        }
    }

    /** 每具空壳的"卡住"计数（只在冲刺期间用）。 */
    private static final java.util.Map<UUID, Integer> STUCK_TICKS = new java.util.HashMap<>();

    private static int stuckTicks(LivingEntity shell) {
        int next = STUCK_TICKS.getOrDefault(shell.getUUID(), 0) + 1;
        STUCK_TICKS.put(shell.getUUID(), next);
        return next;
    }

    private static void clearStuckTicks(LivingEntity shell) {
        STUCK_TICKS.remove(shell.getUUID());
    }

    private static void stopDash(Iterator<Dash> iterator, @Nullable Entity shellEntity) {
        iterator.remove();
        if (shellEntity instanceof Mob shell) {
            shell.setSprinting(false);
            shell.getNavigation().stop();
            clearStuckTicks(shell);
        }
    }

    /** 冲刺调度。 */
    private static final class ChargeDash {

        static void start(ServerLevel level, LivingEntity shell, LivingEntity target) {
            DASHES.removeIf(dash -> dash.shellUuid().equals(shell.getUUID()));
            DASHES.add(new Dash(level, shell.getUUID(), target.getUUID(),
                level.getGameTime() + CHARGE_MAX_TICKS));
        }

        static boolean isDashing(LivingEntity shell) {
            for (Dash dash : DASHES) {
                if (dash.shellUuid().equals(shell.getUUID())) {
                    return true;
                }
            }
            return false;
        }
    }

    // ====================== 远程：暮色权杖 / 三发弓·追踪弓 / 链锤 ======================

    /** 远程：暮色权杖，射出暮色自己的 {@code wand_bolt}（伤害 6 点由弹体自己结算）。 */
    private static final class TwilightScepterEvent implements PossessedAttackEvent {

        private static final int COOLDOWN_TICKS = 40;
        private static final float SPEED = 1.5F;

        /**
         * 起手（举起权杖）刻数。
         * <p>
         * <b>这个值不能省</b>：空壳只在 {@code windUpTicks() > 0} 时才会调用 {@link #beginWindUp}，
         * 而"手里拿着权杖"就是在那里面做的 —— 少了它，{@code beginWindUp} 成了死代码，
         * 权杖永远不会出现在手上（用户反馈的 bug：丢出 wand_bolt 但手上什么都没有）。
         * 原版骷髅拉弓、模组自己的弓/三叉戟也都是靠这段起手期显示武器的。
         */
        private static final int WIND_UP_TICKS = 10;

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            attacker.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(twilightforest.init.TFItems.TWILIGHT_SCEPTER.get()));
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 direction = OrbPossessedAttackEvents.aimAt(attacker, target);
            if (direction == null) {
                return;
            }
            // 施法手势：挥一下手（和挥剑/投掷一样靠 attackTime 播动画，见 PlayerShellRenderer）
            attacker.swing(InteractionHand.MAIN_HAND);
            Vec3 from = attacker.getEyePosition();
            twilightforest.entity.projectile.TwilightWandBolt bolt =
                new twilightforest.entity.projectile.TwilightWandBolt(level, attacker);
            bolt.setPos(from.x, from.y - 0.1, from.z);
            bolt.shoot(direction.x, direction.y, direction.z, SPEED, 0.0F);
            level.addFreshEntity(bolt);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.ILLUSIONER_CAST_SPELL,
                SoundSource.HOSTILE, 1.0F, 1.2F);
        }
    }

    /**
     * 远程：力量 V 的三发弓 / 追踪弓 —— <b>抽到本项后内部五五开</b>。
     * <ul>
     *   <li><b>三发弓</b>（{@code triple_bow}）：照搬暮色本体的打法（见下）；
     *       弓自带力量 V，所以三支都吃力量加成；</li>
     *   <li><b>追踪弓</b>（{@code seeker_bow}）：射出暮色自己的 {@code seeker_arrow}（会朝目标修正弹道）。</li>
     * </ul>
     * 两把弓都是原版 {@code BowItem} 的子类，所以拉弓姿态（{@code UseAnim.BOW}）自动成立。
     *
     * <h2>三发弓到底怎么射（读暮色 {@code TripleBowItem#shoot} 抄的）</h2>
     * 暮色的实现不是在<b>水平</b>方向散开（那是原版多重射击的玩法），而是：
     * <ol>
     *   <li>三支箭的<b>方向完全相同</b>（都是瞄同一个点，没有任何偏航角偏移）；</li>
     *   <li>射出去之后，只在<b>初速</b>上叠一个竖直分量 {@code 0.15 × j}（j = -1/0/+1），
     *       即 {@link #TRIPLE_BOW_VERTICAL_STEP}；</li>
     *   <li>于是三支箭始终待在"瞄准方向所在的那个<b>竖平面</b>"里，越飞越开
     *       （到 20 格处上下各差约 1 格）。</li>
     * </ol>
     * 另外那三支箭是<b>同一刻</b>命中的，原版 10 刻无敌帧会把后面两支整段吃掉，
     * 所以这里用 {@link OrbTripleArrow} 在命中前清零目标的无敌帧，让三支都真正结算伤害。
     */
    private static final class TwilightBowEvent implements PossessedAttackEvent {

        private static final int WIND_UP_TICKS = 20;
        private static final int COOLDOWN_TICKS = 40;
        private static final float SPEED_PER_TICK = 3.0F;

        /** 普通弓的散布（原版 {@code BowItem#releaseUsing} 传的就是 1.0F）。 */
        private static final float BOW_INACCURACY = 1.0F;

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            attacker.setItemInHand(InteractionHand.MAIN_HAND,
                powerBow(attacker, attacker.getRandom().nextBoolean()));
            attacker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW));
            attacker.startUsingItem(InteractionHand.MAIN_HAND);
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 direction = OrbPossessedAttackEvents.aimAt(attacker, target);
            if (direction == null) {
                return;
            }
            // 这一发按"手里那把弓"来打：起手时抽到哪把就已经拿到主手了，
            // 所以观感和实际一致 —— 上一版 beginWindUp 与 run 各自随机一次，
            // 会出现"手里握着追踪弓、射出来的却是三支箭"的穿帮。
            ItemStack held = attacker.getMainHandItem();
            boolean seeking = held.is(twilightforest.init.TFItems.SEEKER_BOW.get());
            ItemStack bow = held.isEmpty() ? powerBow(attacker, seeking) : held;
            Vec3 from = attacker.getEyePosition();

            if (seeking) {
                twilightforest.entity.projectile.SeekerArrow arrow =
                    new twilightforest.entity.projectile.SeekerArrow(level, attacker,
                        new ItemStack(Items.ARROW), bow);
                arrow.setPos(from.x, from.y - 0.1, from.z);
                arrow.shoot(direction.x, direction.y, direction.z, SPEED_PER_TICK, 0.0F);
                level.addFreshEntity(arrow);
            } else {
                // 暮色三发弓：方向完全一致，只在竖直初速上差 0.15 格/刻（见类注释）
                for (int j = -1; j <= 1; j++) {
                    OrbTripleArrow arrow = new OrbTripleArrow(level, attacker,
                        new ItemStack(Items.ARROW), bow);
                    arrow.setPos(from.x, from.y - 0.1, from.z);
                    arrow.shoot(direction.x, direction.y, direction.z, SPEED_PER_TICK,
                        BOW_INACCURACY);
                    arrow.setDeltaMovement(arrow.getDeltaMovement()
                        .add(0.0D, TRIPLE_BOW_VERTICAL_STEP * j, 0.0D));
                    level.addFreshEntity(arrow);
                }
            }
            level.playSound(null, from.x, from.y, from.z, SoundEvents.ARROW_SHOOT,
                SoundSource.HOSTILE, 1.0F, 1.0F);
        }

        @Override
        public void endWindUp(LivingEntity attacker) {
            attacker.stopUsingItem();
        }

        /** 力量 V 的暮色弓。<b>靠代码附魔</b>：暮色没有把自家弓加进原版可附魔标签，锻造台上附不了。 */
        private static ItemStack powerBow(LivingEntity attacker, boolean seeking) {
            var item = seeking
                ? twilightforest.init.TFItems.SEEKER_BOW.get()
                : twilightforest.init.TFItems.TRIPLE_BOW.get();
            ItemStack stack = new ItemStack(item);
            var holder = power(attacker.level());
            if (holder != null) {
                stack.enchant(holder, ENCHANT_LEVEL);
            }
            return stack;
        }
    }

    /**
     * 远程：力量 V 的寒冰弓（{@code ice_bow}）—— 射出暮色自己的 {@code ice_arrow}
     * （命中会给目标挂霜冻）。对烈焰人时这张签会被优先兑现（见 {@link #addMatchups}）。
     */
    private static final class IceBowEvent implements PossessedAttackEvent {

        private static final int WIND_UP_TICKS = 20;
        private static final int COOLDOWN_TICKS = 40;
        private static final float SPEED_PER_TICK = 3.0F;

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        /** 寒冰弓的权重是普通签的 {@link #ICE_WEAPON_WEIGHT}（1/2，用户指定）。 */
        @Override
        public float weight() {
            return ICE_WEAPON_WEIGHT;
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            attacker.setItemInHand(InteractionHand.MAIN_HAND, iceBow(attacker));
            attacker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW));
            attacker.startUsingItem(InteractionHand.MAIN_HAND);
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 direction = OrbPossessedAttackEvents.aimAt(attacker, target);
            if (direction == null) {
                return;
            }
            Vec3 from = attacker.getEyePosition();
            twilightforest.entity.projectile.IceArrow arrow =
                new twilightforest.entity.projectile.IceArrow(level, attacker,
                    new ItemStack(Items.ARROW), iceBow(attacker));
            arrow.setPos(from.x, from.y - 0.1, from.z);
            arrow.shoot(direction.x, direction.y, direction.z, SPEED_PER_TICK, 0.0F);
            level.addFreshEntity(arrow);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.ARROW_SHOOT,
                SoundSource.HOSTILE, 1.0F, 1.2F);
        }

        @Override
        public void endWindUp(LivingEntity attacker) {
            attacker.stopUsingItem();
        }

        /** 力量 V 的寒冰弓（附魔同样靠代码给：暮色自家的弓不在原版可附魔标签里）。 */
        private static ItemStack iceBow(LivingEntity attacker) {
            ItemStack stack = new ItemStack(twilightforest.init.TFItems.ICE_BOW.get());
            var holder = power(attacker.level());
            if (holder != null) {
                stack.enchant(holder, ENCHANT_LEVEL);
            }
            return stack;
        }
    }

    /** 远程：链锤，投出暮色自己的 {@code chain_block}（命中 10 点，之后回手）。 */    private static final class ChainBlockEvent implements PossessedAttackEvent {

        private static final int COOLDOWN_TICKS = 60;

        @Override
        public int cooldownTicks() {
            return COOLDOWN_TICKS;
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 direction = OrbPossessedAttackEvents.aimAt(attacker, target);
            if (direction == null) {
                return;
            }
            ItemStack chain = new ItemStack(twilightforest.init.TFItems.BLOCK_AND_CHAIN.get());
            attacker.setItemInHand(InteractionHand.MAIN_HAND, chain);
            attacker.swing(InteractionHand.MAIN_HAND);

            twilightforest.entity.projectile.ChainBlock thrown =
                new twilightforest.entity.projectile.ChainBlock(
                    twilightforest.init.TFEntities.CHAIN_BLOCK.get(), level, attacker,
                    InteractionHand.MAIN_HAND, chain);
            Vec3 from = attacker.getEyePosition();
            thrown.setPos(from.x, from.y - 0.1, from.z);
            thrown.shoot(direction.x, direction.y, direction.z, CHAIN_SPEED, 0.0F);
            level.addFreshEntity(thrown);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_THROW,
                SoundSource.HOSTILE, 1.0F, 0.8F);
        }
    }
}
