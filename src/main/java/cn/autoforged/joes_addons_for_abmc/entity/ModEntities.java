package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, ModMain.MODID);

    public static final Supplier<EntityType<ThrownGlisteringMelonKnife>> THROWN_GLISTERING_MELON_KNIFE =
        ENTITIES.register("thrown_glistering_melon_knife",
            () -> EntityType.Builder.<ThrownGlisteringMelonKnife>of(ThrownGlisteringMelonKnife::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .clientTrackingRange(4)
                .updateInterval(10)
                .build("thrown_glistering_melon_knife"));

    public static final Supplier<EntityType<PrismarineArrow>> PRISMARINE_ARROW =
        ENTITIES.register("prismarine_arrow",
            () -> EntityType.Builder.<PrismarineArrow>of(PrismarineArrow::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .clientTrackingRange(4)
                .updateInterval(20)
                .build("prismarine_arrow"));

    /** 爆炸之箭：命中实体立刻爆炸、命中方块 100 刻后爆炸（威力 4、不破坏地形、不伤及射手）。 */
    public static final Supplier<EntityType<ExplosiveArrow>> EXPLOSIVE_ARROW =
        ENTITIES.register("explosive_arrow",
            () -> EntityType.Builder.<ExplosiveArrow>of(ExplosiveArrow::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .clientTrackingRange(4)
                .updateInterval(20)
                .build("explosive_arrow"));

    /** 烈焰手杖的恶魂火球：受重力、爆炸威力 2（参数与原版 FIREBALL 一致）。 */
    public static final Supplier<EntityType<BlazeStaffFireball>> BLAZE_STAFF_FIREBALL =
        ENTITIES.register("blaze_staff_fireball",
            () -> EntityType.Builder.<BlazeStaffFireball>of(BlazeStaffFireball::new, MobCategory.MISC)
                .sized(1.0F, 1.0F)
                .clientTrackingRange(4)
                .updateInterval(10)
                .build("blaze_staff_fireball"));

    public static final Supplier<EntityType<BedrockFallingBlockEntity>> BEDROCK_FALLING_BLOCK =
        ENTITIES.register("bedrock_falling_block",
            () -> EntityType.Builder.<BedrockFallingBlockEntity>of(BedrockFallingBlockEntity::new, MobCategory.MISC)
                .sized(0.98F, 0.98F)
                .clientTrackingRange(10)
                .updateInterval(20)
                .build("bedrock_falling_block"));

    public static final Supplier<EntityType<LapisFallingBlockEntity>> LAPIS_FALLING_BLOCK =
        ENTITIES.register("lapis_falling_block",
            () -> EntityType.Builder.<LapisFallingBlockEntity>of(LapisFallingBlockEntity::new, MobCategory.MISC)
                .sized(0.98F, 0.98F)
                .clientTrackingRange(10)
                .updateInterval(20)
                .build("lapis_falling_block"));

    public static final Supplier<EntityType<TransmutationFallingBlockEntity>> TRANSMUTATION_FALLING_BLOCK =
        ENTITIES.register("transmutation_falling_block",
            () -> EntityType.Builder.<TransmutationFallingBlockEntity>of(TransmutationFallingBlockEntity::new, MobCategory.MISC)
                .sized(0.98F, 0.98F)
                .clientTrackingRange(10)
                .updateInterval(20)
                .build("transmutation_falling_block"));

    public static final Supplier<EntityType<AwakeningFallingBlockEntity>> AWAKENING_FALLING_BLOCK =
        ENTITIES.register("awakening_falling_block",
            () -> EntityType.Builder.<AwakeningFallingBlockEntity>of(AwakeningFallingBlockEntity::new, MobCategory.MISC)
                .sized(0.98F, 0.98F)
                .clientTrackingRange(10)
                .updateInterval(1)
                .build("awakening_falling_block"));

    public static final Supplier<EntityType<DripstoneFallingBlockEntity>> DRIPSTONE_FALLING_BLOCK =
        ENTITIES.register("dripstone_falling_block",
            () -> EntityType.Builder.<DripstoneFallingBlockEntity>of(DripstoneFallingBlockEntity::new, MobCategory.MISC)
                .sized(0.98F, 0.98F)
                .clientTrackingRange(10)
                .updateInterval(1)
                .build("dripstone_falling_block"));

    public static final Supplier<EntityType<PortalEntity>> PORTAL =
        ENTITIES.register("portal",
            () -> EntityType.Builder.<PortalEntity>of(PortalEntity::new, MobCategory.MISC)
                .sized(2.0F, 2.0F)
                .clientTrackingRange(64)
                .updateInterval(20)
                .build("portal"));

    public static final Supplier<EntityType<PotionPortalEntity>> POTION_PORTAL =
        ENTITIES.register("potion_portal",
            () -> EntityType.Builder.<PotionPortalEntity>of(PotionPortalEntity::new, MobCategory.MISC)
                .sized(1.0F, 2.0F)
                .clientTrackingRange(64)
                .updateInterval(20)
                .build("potion_portal"));

    public static final Supplier<EntityType<PlayerShellEntity>> PLAYER_SHELL =
        ENTITIES.register("player_shell",
            () -> EntityType.Builder.<PlayerShellEntity>of(PlayerShellEntity::new, MobCategory.MISC)
                .sized(0.6F, 1.8F)
                // 与真人玩家一致的视高：原版玩家是 0.6×1.8 + eyeHeight 1.62，
                // 而 Builder 的默认视高是"身高 × 0.85"= 1.53（差 9 厘米，射线/索敌/挂点都会偏）。
                .eyeHeight(1.62F)
                // 载具挂点也必须写在**实体类型**上（原版 EntityType.PLAYER 就是这么写的）：
                // 坐船/坐矿车时 positionRider 会把这个点从座位点里减掉；不登记的话它取
                // EntityAttachment.VEHICLE 的兜底值 (0,0,0)，空壳就会比真人高出 0.6 格悬在船上。
                // （只写在 getDefaultDimensions 里不够 —— 那个值要等 refreshDimensions() 才进缓存，
                //  而客户端那条"船的 rideTick 重新摆乘客"走的是实体类型自带的 dimensions。）
                .vehicleAttachment(PlayerShellEntity.PLAYER_VEHICLE_ATTACHMENT)
                .clientTrackingRange(10)
                .updateInterval(3)
                .build("player_shell"));

    public static final Supplier<EntityType<HerobrineHeadEntity>> HEROBRINE_HEAD =
        ENTITIES.register("herobrine_head",
            () -> EntityType.Builder.<HerobrineHeadEntity>of(HerobrineHeadEntity::new, MobCategory.MISC)
                .sized(0.3125F, 0.3125F)
                .clientTrackingRange(4)
                .updateInterval(20)
                .build("herobrine_head"));

    public static final Supplier<EntityType<TntStaffPrimedTnt>> TNT_STAFF_PRIMED_TNT =
        ENTITIES.register("tnt_staff_primed_tnt",
            () -> EntityType.Builder.<TntStaffPrimedTnt>of(TntStaffPrimedTnt::new, MobCategory.MISC)
                .sized(0.98F, 0.98F)
                .clientTrackingRange(8)
                .updateInterval(10)
                .build("tnt_staff_primed_tnt"));

    public static final Supplier<EntityType<TntStaffCreeper>> TNT_STAFF_CREEPER =
        ENTITIES.register("tnt_staff_creeper",
            () -> EntityType.Builder.<TntStaffCreeper>of(TntStaffCreeper::new, MobCategory.MONSTER)
                .sized(0.6F, 1.7F)
                .clientTrackingRange(8)
                .updateInterval(2)
                .build("tnt_staff_creeper"));

    /**
     * 爆裂猫：幸运核心附体的远程召唤物之一 —— 直线飞行、匀加速，撞到方块或生物后爆出<b>威力 1</b>，
     * 飞满 200 游戏刻必爆（见 {@link FlyingBombControl}）。是原版猫的派生类，渲染直接复用
     * {@code CatRenderer}。
     */
    public static final Supplier<EntityType<OrbExplosiveCat>> ORB_EXPLOSIVE_CAT =
        ENTITIES.register("orb_explosive_cat",
            () -> EntityType.Builder.<OrbExplosiveCat>of(OrbExplosiveCat::new, MobCategory.CREATURE)
                .sized(0.6F, 0.7F)
                .clientTrackingRange(10)
                .updateInterval(2)
                .build("orb_explosive_cat"));

    /**
     * 爆裂僵尸：同爆裂猫，但爆炸<b>威力 2</b>，而且<b>不会燃烧</b>（白天不点着、免疫火焰）。
     * 原版僵尸的派生类，渲染复用 {@code ZombieRenderer}。
     */
    public static final Supplier<EntityType<OrbExplosiveZombie>> ORB_EXPLOSIVE_ZOMBIE =
        ENTITIES.register("orb_explosive_zombie",
            () -> EntityType.Builder.<OrbExplosiveZombie>of(OrbExplosiveZombie::new, MobCategory.MONSTER)
                .sized(0.6F, 1.95F)
                .clientTrackingRange(10)
                .updateInterval(2)
                .build("orb_explosive_zombie"));

    /**
     * 爆裂史莱姆：同爆裂猫，但外观是<b>史莱姆</b>（用户指定"换皮的猫"），爆炸<b>威力 1</b>。
     * 原版史莱姆的派生类，渲染复用 {@code SlimeRenderer}；尺寸 0.52 × ID_SIZE（构造里设成 2）。
     */
    public static final Supplier<EntityType<OrbExplosiveSlime>> ORB_EXPLOSIVE_SLIME =
        ENTITIES.register("orb_explosive_slime",
            () -> EntityType.Builder.<OrbExplosiveSlime>of(OrbExplosiveSlime::new, MobCategory.MONSTER)
                .sized(0.52F, 0.52F)
                .clientTrackingRange(10)
                .updateInterval(2)
                .build("orb_explosive_slime"));

    /**
     * 爆裂骷髅：同爆裂僵尸，但外观是<b>骷髅</b>（用户指定"换皮的僵尸"），爆炸<b>威力 2</b>，
     * 而且<b>不会燃烧</b>。原版骷髅的派生类，渲染复用 {@code SkeletonRenderer}。
     */
    public static final Supplier<EntityType<OrbExplosiveSkeleton>> ORB_EXPLOSIVE_SKELETON =
        ENTITIES.register("orb_explosive_skeleton",
            () -> EntityType.Builder.<OrbExplosiveSkeleton>of(OrbExplosiveSkeleton::new, MobCategory.MONSTER)
                .sized(0.6F, 1.99F)
                .clientTrackingRange(10)
                .updateInterval(2)
                .build("orb_explosive_skeleton"));

    public static final Supplier<EntityType<BrewingStaffCloudEntity>> BREWING_STAFF_CLOUD =
        ENTITIES.register("brewing_staff_cloud",
            () -> EntityType.Builder.<BrewingStaffCloudEntity>of(BrewingStaffCloudEntity::new, MobCategory.MISC)
                .sized(6.0F, 0.5F)
                .clientTrackingRange(10)
                .updateInterval(1)
                .build("brewing_staff_cloud"));

    public static final Supplier<EntityType<EnchantedOrigamiEntity>> ENCHANTED_ORIGAMI =
        ENTITIES.register("enchanted_origami",
            () -> EntityType.Builder.<EnchantedOrigamiEntity>of(EnchantedOrigamiEntity::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .clientTrackingRange(8)
                .updateInterval(1)
                .build("enchanted_origami"));

    public static final Supplier<EntityType<EnchantmentBullet>> ENCHANTMENT_BULLET =
        ENTITIES.register("enchantment_bullet",
            () -> EntityType.Builder.<EnchantmentBullet>of(EnchantmentBullet::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .clientTrackingRange(4)
                .updateInterval(1)
                .build("enchantment_bullet"));

    public static final Supplier<EntityType<ThrownMilkBucket>> THROWN_MILK_BUCKET =
        ENTITIES.register("thrown_milk_bucket",
            () -> EntityType.Builder.<ThrownMilkBucket>of(ThrownMilkBucket::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .clientTrackingRange(4)
                .updateInterval(10)
                .build("thrown_milk_bucket"));

    /** 可持有的结构：将一整个结构渲染为实体形态，支持 xyz 三轴视觉旋转。目前仅注册，召唤方式暂未实装。 */
    public static final Supplier<EntityType<HoldableStructureEntity>> HOLDABLE_STRUCTURE =
        ENTITIES.register("holdable_structure",
            () -> EntityType.Builder.<HoldableStructureEntity>of(HoldableStructureEntity::new, MobCategory.MISC)
                .sized(1.0F, 1.0F)
                .clientTrackingRange(16)
                .updateInterval(1)
                .build("holdable_structure"));

    /** 苦力蜂（Beeper）：使用蜜蜂模型与动画，暂无 AI。尺寸与原版蜜蜂一致。 */
    public static final Supplier<EntityType<BeeperEntity>> BEEPER =
        ENTITIES.register("beeper",
            () -> EntityType.Builder.<BeeperEntity>of(BeeperEntity::new, MobCategory.CREATURE)
                .sized(0.7F, 0.6F)
                .clientTrackingRange(8)
                .updateInterval(2)
                .build("beeper"));

    /**
     * 幸运方块选择器（Lucky Selector）：飞行的「选择框」实体。
     * <p>
     * 碰撞箱正好 1×1 格，走常规碰撞（可被推动、会被方块挡住，不是潜影贝那种硬碰撞箱）；
     * AI 暂未实装（只有飞行导航/移动控制），渲染用自带的 Blockbench 模型 {@code LuckySelectorModel}。
     */
    public static final Supplier<EntityType<LuckySelectorEntity>> LUCKY_SELECTOR =
        ENTITIES.register("lucky_selector",
            () -> EntityType.Builder.<LuckySelectorEntity>of(LuckySelectorEntity::new, MobCategory.MISC)
                .sized(1.0F, 1.0F)
                // 乘客挂点显式设成 0.375（= 6 像素，比碰撞箱顶面低 10 像素）。
                // 不写的话默认取 EntityAttachment.PASSENGER 的 fallback —— AT_HEIGHT = (0, height, 0)，
                // 对这个 1 格高的本体就是顶面 16 像素，物品展示实体骑上来会浮在本体上方
                // （见 EntityAttachment.java:7/25；AT_CENTER 才是 height/2，同文件 26 行）。
                .passengerAttachments(0.375F)
                .clientTrackingRange(10)
                .updateInterval(2)
                .build("lucky_selector"));

    /**
     * 幸运维度自然生成的掉落物。除了「走过去捡不起来、只能右键拿走」之外就是普通掉落物，
     * 所以尺寸/跟踪范围/更新间隔全部照搬原版 {@code EntityType.ITEM}（EntityType.java:442-445）。
     */
    public static final Supplier<EntityType<LuckyItemEntity>> LUCKY_ITEM =
        ENTITIES.register("lucky_item",
            () -> EntityType.Builder.<LuckyItemEntity>of(LuckyItemEntity::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .eyeHeight(0.2125F)
                .clientTrackingRange(6)
                .updateInterval(20)
                .build("lucky_item"));

    /**
     * 幸运核心（Orb of Luck，注册 id {@code orb_of_luck}）：动物类（{@link MobCategory#CREATURE}）实体，
     * 本体即一个会呼吸的发光球，渲染为「正对摄像机的面片 + 自定义 core shader」，不使用任何贴图。
     * 目前没有 AI，生成方式见刷怪蛋 {@code orb_of_luck_spawn_egg}。
     * <p>
     * 生命值 {@link OrbOfLuckEntity#MAX_HEALTH} 点、免疫摔落伤害；半径是固定常量
     * {@link OrbOfLuckEntity#RADIUS}，不随玩家距离变化（远处看起来变小纯粹是透视）；
     * 碰撞箱是边长 {@code 2 * RADIUS} 的立方，<b>下表面与实体坐标对齐</b>（原版活体建箱方式，
     * 即召唤/放置时核心坐在给定高度上，而不是以该高度为中心）。
     */
    public static final Supplier<EntityType<OrbOfLuckEntity>> ORB_OF_LUCK =
        ENTITIES.register("orb_of_luck",
            () -> EntityType.Builder.<OrbOfLuckEntity>of(OrbOfLuckEntity::new, MobCategory.CREATURE)
                .sized(OrbOfLuckEntity.RADIUS * 2.0F, OrbOfLuckEntity.RADIUS * 2.0F)
                .clientTrackingRange(10)
                .updateInterval(2)
                .build("orb_of_luck"));

    // ===== 6.5.5 新增的幸运事件专用实体 =====

    /**
     * 金粒猪：头顶不断喷发不可拾取的金粒。原版猪的派生类，渲染复用 {@code PigRenderer}，
     * 尺寸照搬原版猪（0.9 × 0.9）。
     */
    public static final Supplier<EntityType<GoldNuggetPig>> GOLD_NUGGET_PIG =
        ENTITIES.register("gold_nugget_pig",
            () -> EntityType.Builder.<GoldNuggetPig>of(GoldNuggetPig::new, MobCategory.CREATURE)
                .sized(0.9F, 0.9F)
                .clientTrackingRange(10)
                .updateInterval(3)
                .build("gold_nugget_pig"));

    /**
     * 需求 3'（6.5.27）：会飞、可被玩家完全操控的金马铠马
     * （见 {@link PilotGoldenHorseEntity}）。尺寸与跟踪范围照抄原版马；
     * 属性在 {@code PilotGoldenHorseEntity.AttributeRegistration} 里登记（漏登记会一生成就崩）。
     */
    public static final Supplier<EntityType<PilotGoldenHorseEntity>> PILOT_GOLDEN_HORSE =
        ENTITIES.register("pilot_golden_horse",
            () -> EntityType.Builder.of(PilotGoldenHorseEntity::new, MobCategory.CREATURE)
                .sized(1.3964844F, 1.6F)
                .clientTrackingRange(10)
                .build("pilot_golden_horse"));

    /**
     * 烟花猪：尾部喷烟花、以 20 格/秒直线前飞，撞方块或飞满 5 秒即消失（消失点散射大量烟花粒子）。
     * 同样是原版猪的派生类。
     */
    public static final Supplier<EntityType<FireworkPig>> FIREWORK_PIG =
        ENTITIES.register("firework_pig",
            () -> EntityType.Builder.<FireworkPig>of(FireworkPig::new, MobCategory.CREATURE)
                .sized(0.9F, 0.9F)
                .clientTrackingRange(10)
                .updateInterval(2)
                .build("firework_pig"));

    /**
     * 诱猪胡萝卜：吸引 20 格内的猪、玩家捡不起来（漏斗可以）、碰到猪或满 1 分钟消失。
     * 尺寸/跟踪范围照搬原版 {@code EntityType.ITEM}（EntityType.java:442-445）。
     */
    public static final Supplier<EntityType<PigBaitCarrotEntity>> PIG_BAIT_CARROT =
        ENTITIES.register("pig_bait_carrot",
            () -> EntityType.Builder.<PigBaitCarrotEntity>of(PigBaitCarrotEntity::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .eyeHeight(0.2125F)
                .clientTrackingRange(6)
                .updateInterval(20)
                .build("pig_bait_carrot"));

    /** 摇摆烟花火箭：直线飞行、仰角每刻在 60~80 度之间随机波动（渲染沿用原版烟花火箭）。 */
    public static final Supplier<EntityType<LuckyFireworkRocket>> LUCKY_FIREWORK_ROCKET =
        ENTITIES.register("lucky_firework_rocket",
            () -> EntityType.Builder.<LuckyFireworkRocket>of(LuckyFireworkRocket::new, MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .clientTrackingRange(4)
                .updateInterval(10)
                .build("lucky_firework_rocket"));

    /**
     * 反弹盾牌：<b>正常大小</b>的盾牌物品展示实体，5 秒后消失，期间反弹弹射物。
     * 尺寸照搬原版 {@code item_display}（0 × 0，EntityType.java:447）。
     */
    public static final Supplier<EntityType<ReflectingShieldDisplay>> REFLECTING_SHIELD =
        ENTITIES.register("reflecting_shield",
            () -> EntityType.Builder.<ReflectingShieldDisplay>of(ReflectingShieldDisplay::new, MobCategory.MISC)
                .sized(0.0F, 0.0F)
                .clientTrackingRange(10)
                .updateInterval(1)
                .build("reflecting_shield"));
}
