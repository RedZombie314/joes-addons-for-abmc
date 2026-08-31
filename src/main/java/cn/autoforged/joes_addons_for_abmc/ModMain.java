package cn.autoforged.joes_addons_for_abmc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;

import cn.autoforged.joes_addons_for_abmc.block.LuckyPortalBlock;
import cn.autoforged.joes_addons_for_abmc.block.ModBlocks;
import cn.autoforged.joes_addons_for_abmc.block.entity.JobFrostedIceBlockEntity;
import cn.autoforged.joes_addons_for_abmc.block.entity.LuckyDimensionBlockEntity;
import cn.autoforged.joes_addons_for_abmc.block.entity.LuckyPortalBlockEntity;
import cn.autoforged.joes_addons_for_abmc.block.entity.ModBlockEntities;
import cn.autoforged.joes_addons_for_abmc.command.CommandStaffStorage;
import cn.autoforged.joes_addons_for_abmc.config.ModConfig;
import cn.autoforged.joes_addons_for_abmc.entity.BedrockFallingBlockEntity;
import cn.autoforged.joes_addons_for_abmc.entity.DripstoneFallingBlockEntity;
import cn.autoforged.joes_addons_for_abmc.entity.HerobrineHeadEntity;
import cn.autoforged.joes_addons_for_abmc.entity.LapisFallingBlockEntity;
import cn.autoforged.joes_addons_for_abmc.entity.ModEntities;
import cn.autoforged.joes_addons_for_abmc.entity.PlayerShellEntity;
import cn.autoforged.joes_addons_for_abmc.entity.PortalEntity;
import cn.autoforged.joes_addons_for_abmc.entity.PotionPortalEntity;
import cn.autoforged.joes_addons_for_abmc.entity.TntStaffCreeper;
import cn.autoforged.joes_addons_for_abmc.entity.TntStaffPrimedTnt;
import cn.autoforged.joes_addons_for_abmc.entity.TransmutationFallingBlockEntity;
import cn.autoforged.joes_addons_for_abmc.item.GlisteringMelonKnifeItem;
import cn.autoforged.joes_addons_for_abmc.item.ModCreativeTab;
import cn.autoforged.joes_addons_for_abmc.item.ModDataComponents;
import cn.autoforged.joes_addons_for_abmc.damage.ModDamageTypes;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import cn.autoforged.joes_addons_for_abmc.item.PrismarineArrowRecipe;
import cn.autoforged.joes_addons_for_abmc.item.StaffItem;
import cn.autoforged.joes_addons_for_abmc.network.BellRingPayload;
import cn.autoforged.joes_addons_for_abmc.network.BlockingStatePayload;
import cn.autoforged.joes_addons_for_abmc.network.CommandStaffActionPayload;
import cn.autoforged.joes_addons_for_abmc.network.CommandStaffSyncPayload;
import cn.autoforged.joes_addons_for_abmc.network.ScriptBroadcastPayload;
import cn.autoforged.joes_addons_for_abmc.network.ScriptFunctionPayload;
import cn.autoforged.joes_addons_for_abmc.network.ScriptFunctionSyncPayload;
import cn.autoforged.joes_addons_for_abmc.network.ScriptGraphPayload;
import cn.autoforged.joes_addons_for_abmc.network.ScriptLibrarySyncPayload;
import cn.autoforged.joes_addons_for_abmc.network.ScriptNetworking;
import cn.autoforged.joes_addons_for_abmc.network.ScriptRunPayload;
import cn.autoforged.joes_addons_for_abmc.network.PortalStaffInputPayload;
import cn.autoforged.joes_addons_for_abmc.network.StaffBlockTypePayload;
import cn.autoforged.joes_addons_for_abmc.network.OmegaDismantlePayload;
import cn.autoforged.joes_addons_for_abmc.network.TntDetonatePayload;
import cn.autoforged.joes_addons_for_abmc.network.CobwebClearPayload;
import cn.autoforged.joes_addons_for_abmc.network.CobwebDisconnectPayload;
import cn.autoforged.joes_addons_for_abmc.network.CobwebNullifyPayload;
import cn.autoforged.joes_addons_for_abmc.network.CobwebPullPayload;
import cn.autoforged.joes_addons_for_abmc.network.CobwebPullStopPayload;
import cn.autoforged.joes_addons_for_abmc.network.GameIconCraftPayload;
import cn.autoforged.joes_addons_for_abmc.potion.ModMobEffects;
import cn.autoforged.joes_addons_for_abmc.potion.ModPotions;
import cn.autoforged.joes_addons_for_abmc.potion.TransmutationBrewingRecipe;
import cn.autoforged.joes_addons_for_abmc.sound.ModSounds;
import cn.autoforged.joes_addons_for_abmc.worldgen.ModDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.UseItemGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SculkCatalystBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.Container;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(ModMain.MODID)
public class ModMain {
    public static final String MODID = "joes_addons_for_abmc";
    private static final Logger LOGGER = LoggerFactory.getLogger("ABMC-TransmutationShell");

    // --- 怪物学校末影人变种：近处玩家敌对半径（格） ---
    private static final double MONSTER_SCHOOL_AGGRO_RADIUS = 16.0;
    // 末影人持久数据中的标记键
    private static final String MONSTER_SCHOOL_TAG = "IsMonsterSchool";
    private static final String MONSTER_SCHOOL_ENHANCED_TAG = "MonsterSchoolEnhanced";

    // 出售命令方块的图书管理员：强制携带该标签（debug 直接召唤），否则 1% 概率授予交易
    private static final String SELLS_COMMAND_BLOCK_TAG = "SellsCommandBlock";
    private static final String COMMAND_BLOCK_OFFERED_TAG = "CommandBlockTradeOffered";
    private static final String COMMAND_BLOCK_SOLD_TAG = "CommandBlockTradeSold";
    // 大师级交易每格放入 64 个绿宝石块，共 27 格 = 1728 个
    private static final int COMMAND_BLOCK_TRADE_SLOTS = 27;
    private static final int EMERALD_BLOCKS_PER_SLOT = 64;

    // --- 女巫小屋（沼泽小屋）·女巫 Boss 变种：1% 概率取代小屋女巫生成；200 座小屋未出现则第 201 座必出 ---
    private static final String WITCH_BOSS_TAG = "jafa_is_witch_boss";
    // 女巫 Boss 玩家察觉（一次性）：首次将「玩家」（生存/冒险模式）锁定为攻击目标时写入。
    // 仅作为“发现过玩家”的凭证：发现后第一次进入「要喝迅捷药水」的场景时（WitchMixin 中）进行一次
    // 70% 隐身 roll（无论成败只此一次），之后所有场景均为常规 10% roll。
    private static final String WITCH_BOSS_DISCOVERED_TAG = "jafa_witch_boss_discovered";
    /** 女巫Boss阶段：实体阶段(1，浅蓝条)、方块阶段(2，黄条)、物品阶段(3，紫条)。阶段名暂只驱动Boss条颜色，
     *  女巫本身始终保留原外貌（各阶段外观另有他用）；阶段4 非真实阶段，表示“被击败”（原地粒子+消失）。 */
    private static final String WITCH_BOSS_STAGE_TAG = "jafa_witch_boss_stage";
    private static final int WITCH_BOSS_STAGE_ENTITY = 1;
    private static final int WITCH_BOSS_STAGE_BLOCK = 2;
    private static final int WITCH_BOSS_STAGE_ITEM = 3;
    private static final int WITCH_BOSS_STAGE_DEFEATED = 4;
    // 女巫Boss投掷变形药水的候选目标：阶段1生物(mob_shell)/阶段2方块/阶段3物品，各阶段只投对应类
    private static final String[] WITCH_BOSS_MOB_CANDIDATES = {
        "minecraft:pig", "minecraft:llama", "minecraft:cod", "minecraft:salmon",
        "minecraft:sheep", "minecraft:cow", "minecraft:chicken" };
    private static final String[] WITCH_BOSS_BLOCK_CANDIDATES = {
        "minecraft:stone", "minecraft:dirt", "minecraft:gravel", "minecraft:sand", "minecraft:netherrack",
        "minecraft:cobblestone", "minecraft:tnt", "minecraft:oak_log", "minecraft:pumpkin", "minecraft:melon"};
    private static final String[] WITCH_BOSS_ITEM_CANDIDATES = {
        "minecraft:red_dye", "minecraft:blue_dye", "minecraft:light_blue_dye", "minecraft:yellow_dye", 
        "minecraft:brown_dye", "minecraft:pink_dye", "minecraft:magenta_dye", "minecraft:purple_dye", 
        "minecraft:green_dye", "minecraft:lime_dye", "minecraft:cyan_dye", "minecraft:white_dye", 
        "minecraft:black_dye", "minecraft:gray_dye", "minecraft:light_gray_dye", "minecraft:orange_dye" };
    /** 每个在场女巫Boss 一个独立Boss条（原版 ServerBossEvent，由原版 HUD 渲染）。
     *  多个女巫Boss同时在场时各自显示一条；原版客户端最多同时渲染 4 条，且自动把包括凋灵/末影龙
     *  及任何其它模组在内的所有 Boss 条统一计数，因此总数天然不会超过 4 个。 */
    private static final java.util.Map<java.util.UUID, net.minecraft.server.level.ServerBossEvent> WITCH_BOSS_EVENTS =
        new java.util.concurrent.ConcurrentHashMap<>();
    /** 已登记的女巫Boss UUID 集：由 {@link #updateWitchBossBar} 每刻遍历，替代“全世界扫描所有女巫”。
     *  每个被标记为女巫Boss 的实体在此登记，避免每刻 getEntitiesOfClass 全图遍历造成卡顿。 */
    private static final Set<UUID> WITCH_BOSS_TRACKED = ConcurrentHashMap.newKeySet();
    /** “发现玩家”需连续索敌的刻数，避免召唤瞬间/擦肩而过就触发（防止立刻喝隐身药水）。 */
    private static final String WITCH_BOSS_NOTICE_TICKS_TAG = "jafa_witch_boss_notice_ticks";
    private static final int WITCH_BOSS_NOTICE_REQUIRED_TICKS = 40; // 2秒
    // 女巫Boss 随身药水量计数器：变形药水36 / 变形解药12 / 传送药水9 / 隐身药水6（喷溅与直饮共享）
    private static final String WITCH_BOSS_AMMO_TRANSMUTATION_TAG = "jafa_witch_boss_ammo_transmutation";
    private static final String WITCH_BOSS_AMMO_ANTIDOTE_TAG = "jafa_witch_boss_ammo_antidote";
    private static final String WITCH_BOSS_AMMO_TRANSPORT_TAG = "jafa_witch_boss_ammo_transport";
    private static final String WITCH_BOSS_AMMO_INVISIBILITY_TAG = "jafa_witch_boss_ammo_invisibility";
    private static final int WITCH_BOSS_AMMO_TRANSMUTATION_MAX = 36;
    private static final int WITCH_BOSS_AMMO_ANTIDOTE_MAX = 12;
    private static final int WITCH_BOSS_AMMO_TRANSPORT_MAX = 9;
    private static final int WITCH_BOSS_AMMO_INVISIBILITY_MAX = 6;
    /** 任一种药水低于该余量时返回小屋补给。 */
    private static final int WITCH_BOSS_AMMO_LOW = 2;
    /** 女巫Boss“家”（小屋中心）坐标：补给传送目的地。 */
    private static final String WITCH_BOSS_HOME_X_TAG = "jafa_witch_boss_home_x";
    private static final String WITCH_BOSS_HOME_Y_TAG = "jafa_witch_boss_home_y";
    private static final String WITCH_BOSS_HOME_Z_TAG = "jafa_witch_boss_home_z";
    // 女巫Boss 阶段3近战状态：玩家靠太近时 50% 进入近战（持钻石剑），期间不再传送/不再弹道自伤规避
    private static final String WITCH_BOSS_MELEE_TAG = "jafa_witch_boss_melee";
    private static final String WITCH_BOSS_MELEE_LAST_TAG = "jafa_witch_boss_melee_last";
    private static final String WITCH_BOSS_MELEE_WAS_NEAR_TAG = "jafa_witch_boss_melee_was_near";
    /** 触发近战判定的玩家距离（格），以及近战攻击范围。 */
    private static final double WITCH_BOSS_MELEE_RANGE = 3.5D;
    /** 每次靠近时进入近战状态的概率。 */
    private static final double WITCH_BOSS_MELEE_ENTER_CHANCE = 0.5D;
    /** 近战攻击间隔（刻）。 */
    private static final long WITCH_BOSS_MELEE_COOLDOWN = 20L;
    /** 近战攻击伤害。 */
    private static final float WITCH_BOSS_MELEE_DAMAGE = 6.0F;
    // 阶段2工具攻击：锁定已变方块玩家，按方块类型掏不同工具攻击；阶段3打火石缓慢骚扰
    private static final String WITCH_BOSS_TOOL_LAST_TAG = "jafa_witch_boss_tool_last";
    private static final String WITCH_BOSS_FLINT_PENDING_TAG = "jafa_witch_boss_flint_pending";
    private static final String WITCH_BOSS_FLINT_REC_TAG = "jafa_witch_boss_flint_record_tick";
    private static final String WITCH_BOSS_FLINT_USE_TAG = "jafa_witch_boss_flint_use_tick";
    private static final String WITCH_BOSS_FLINT_X_TAG = "jafa_witch_boss_flint_x";
    private static final String WITCH_BOSS_FLINT_Y_TAG = "jafa_witch_boss_flint_y";
    private static final String WITCH_BOSS_FLINT_Z_TAG = "jafa_witch_boss_flint_z";
    /** 阶段2工具攻击间隔（刻）。 */
    private static final long WITCH_BOSS_TOOL_COOLDOWN = 20L;
    /** 阶段2工具攻击伤害。 */
    private static final float WITCH_BOSS_TOOL_DAMAGE = 8.0F;
    /** 3阶段打火石：每多少刻记录一次玩家坐标（秒），以及延迟多少刻后才在记录点打火。 */
    private static final long WITCH_BOSS_FLINT_RECORD_INTERVAL = 100L; // 5秒
    private static final long WITCH_BOSS_FLINT_USE_MIN = 60L;   // 3秒
    private static final long WITCH_BOSS_FLINT_USE_JITTER = 21L; // +0~20刻 → 3~4秒
    /** 女巫Boss自我变形成生物后，生物实体携带此标记：需要往脚下丢变形解药让自己还原为 witchboss 本体。 */
    private static final String WITCH_BOSS_SELF_TRANS_TAG = "jafa_witch_boss_self_transmuted";
    /** 女巫Boss血量倍率：普通女巫的 10 倍（普通女巫基础 26 → 260）。 */
    private static final double WITCH_BOSS_HEALTH_MULTIPLIER = 10.0D;
    // 女巫Boss近身逃逸（阶段1/2）：玩家靠得太近时向脚下丢点传送药水，传送到离玩家15~20格处
    private static final String WITCH_BOSS_TP_CD_TAG = "jafa_witch_boss_tp_cd";
    /** 触发逃逸的玩家达到距离（格）：太近判定。 */
    private static final double WITCH_BOSS_TP_AVOID_RANGE = 3.0D;
    /** 传送目标距玩家的最小/最大距离（格）。 */
    private static final double WITCH_BOSS_TP_DIST_MIN = 10.0D;
    private static final double WITCH_BOSS_TP_DIST_MAX = 15.0D;
    /** 逃逸冷却（刻）：防止连续每刻触发刷距离。 */
    private static final long WITCH_BOSS_TP_COOLDOWN = 120L;
    /** 玩家被变形为 TNT：女巫持打火石点燃的距离（格）。 */
    private static final double WITCH_BOSS_TNT_IGNITE_RANGE = 4.0D;
    /** 点燃的 TNT 的 fuse 时长（刻）。 */
    private static final int WITCH_BOSS_TNT_FUSE = 200;
    /** 点燃 TNT 实体上记录的目标玩家 UUID 的 tag。 */
    private static final String WITCH_BOSS_TNT_PLAYER_TAG = "jafa_witch_boss_tnt_player";
    private static final int WITCH_BOSS_PITY_HUTS = 200;
    // 拥有女巫 Boss 的小屋周围多少格内不再自然刷新女巫（结构自带的女巫 Boss 除外）
    private static final int WITCH_BOSS_NO_SPAWN_RADIUS = 100;
    // 图书管理员保底：连续 300 只大师级图书管理员未卖出命令方块，则下一次必给
    private static final int LIBRARIAN_PITY_COUNT = 300;
    // 保底计数持久化文件名（存主世界维度数据，多人共享）
    private static final String SHARED_COUNTS_DATA_NAME = "jafa_shared_counts";

    /**
     * 女巫 Boss / 卖命令方块图书管理员的共享保底计数（跨会话持久化，多人共享）。
     * 存于主世界维度数据文件，因此同一存档的所有玩家共享同一份计数。
     */
    public static final class SharedCounts extends SavedData {
        /** 连续未出现女巫 Boss 的女巫小屋数量。 */
        public int witchHutCount = 0;
        /** 连续未卖出命令方块的大师级图书管理员数量。 */
        public int librarianCount = 0;
        /** 已生成过女巫 Boss 的小屋所在区块（ChunkPos.asLong），这些小屋不再自然刷新女巫。 */
        public final Set<Long> bossHutChunks = new HashSet<>();

        public static SharedCounts load(CompoundTag tag, HolderLookup.Provider registries) {
            SharedCounts c = new SharedCounts();
            c.witchHutCount = tag.getInt("witch_hut_count");
            c.librarianCount = tag.getInt("librarian_count");
            for (long l : tag.getLongArray("boss_hut_chunks")) {
                c.bossHutChunks.add(l);
            }
            return c;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("witch_hut_count", this.witchHutCount);
            tag.putInt("librarian_count", this.librarianCount);
            long[] chunks = new long[this.bossHutChunks.size()];
            int i = 0;
            for (long l : this.bossHutChunks) {
                chunks[i++] = l;
            }
            tag.putLongArray("boss_hut_chunks", chunks);
            return tag;
        }
    }

    private static SharedCounts getSharedCounts(ServerLevel level) {
        // 统一存主世界维度数据，保证多人游戏（及跨维度事件）共享同一份计数
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(SharedCounts::new, SharedCounts::load), SHARED_COUNTS_DATA_NAME);
    }

    private static final Set<UUID> blockingPlayers = new HashSet<>();

    // Omega 权杖·吸收模式：玩家 UUID → 当前被吸收的生物状态（恢复重力/AI 时使用）
    private static final java.util.Map<UUID, OmegaAbsorbState> OMEGA_ABSORB_STATES = new java.util.HashMap<>();

    /** Omega 吸收模式中被吸收生物的快照：记录原重力/AI，释放时据此恢复。 */
    private static final class OmegaAbsorbState {
        final LivingEntity target;
        final boolean origNoGravity;
        final boolean origNoAi;
        /** 接触玩家后计划删除的游戏刻（-1 表示尚未进入接触删除流程）。 */
        long deleteTick = -1;
        /** 首次接触时的位置：接触删除等待期间按此位置锁定，防止物理把生物推开导致接触中断。 */
        Vec3 contactPos = null;
        OmegaAbsorbState(LivingEntity target) {
            this.target = target;
            this.origNoGravity = target.isNoGravity();
            this.origNoAi = target instanceof net.minecraft.world.entity.Mob m && m.isNoAi();
        }
    }
    // 屏障权杖：左右键同时按下时处于“整体平移”模式，期间抑制普通放置/破坏
    private static final Map<UUID, Integer> barrierShiftSuppress = new HashMap<>();
    private static final Set<BlockPos> areaMiningInProgress = new HashSet<>();
    private static final List<StaffMoveTask> PENDING_STAFF_MOVES = new ArrayList<>();
    private static final List<BonemealTask> PENDING_BONEMEAL_TASKS = new ArrayList<>();
    // 幸运维度传送门激活延迟任务：投掷器入水后延迟 1~1.5 秒再召唤闪电并激活
    private static final List<PortalActivationTask> PENDING_PORTAL_ACTIVATIONS = new ArrayList<>();
    private static final Map<java.util.UUID, Integer> SMELT_COOLDOWNS = new java.util.HashMap<>();
    private static final java.util.Map<BlockPos, Integer> FURNACE_BLOCK_COOLDOWNS = new java.util.HashMap<>();

    // 红石块权杖：玩家 UUID → 当前发射会话状态（充能强度、刻计数、已被强充的方块）
    private static final java.util.Map<UUID, RedstoneStaffState> REDSTONE_STAFF_STATES = new java.util.HashMap<>();

    /** 红石权杖当前正在驱动（延长）的活塞位置（跨维度）。值记录驱动它的玩家 UUID，
     *  供 {@link PistonBaseBlockMixin} 在服务端处理方块事件时对这些活塞放行 getNeighborSignal，
     *  从而无需任何临时红石方块即可让活塞延长。 */
    private static final Map<StaffPistonKey, UUID> STAFF_EXTEND_PISTONS = new HashMap<>();

    /** 红石权杖驱动的活塞追踪键：维度 + 坐标。 */
    private record StaffPistonKey(ResourceKey<Level> dimension, BlockPos pos) {}

    /** 红石块权杖发射会话状态。 */
    private static final class RedstoneStaffState {
        /** 所属玩家 UUID（用于 STAFF_EXTEND_PISTONS 登记与清理）。 */
        UUID owner;
        /** 充能强度（1~8，默认 5），滚轮调整，命中紫水晶簇时衰减 1~2，归零后射线消失。 */
        int charge = 5;
        /** 本段发射会话的累计游戏刻（用于周期性强的相位计算）。 */
        int tick = 0;
        /** 当前是否已把 3×3×3 区域内的红石粉写入等级 15。 */
        boolean powerApplied = false;
        /** 当前强充能目标的方块坐标（用于检测瞄准点变化）。 */
        BlockPos lastTarget = null;
        /** 已被改成等级 15 的方块及其原始方块状态（恢复时使用）。 */
        final java.util.Map<BlockPos, BlockState> poweredBlocks = new java.util.HashMap<>();
        /** 本会话通过临时红石信号驱动过（延长）的活塞位置（休息相位触发收回）。 */
        final java.util.Set<BlockPos> drivenPistons = new java.util.HashSet<>();
        /** 破坏判定倒计时（0 时掷骰并重置为 10~30 刻）。 */
        int breakTimer = 0;
        /** 掉落物摧毁判定倒计时（与方块破坏独立，0 时掷骰并重置为 10~30 刻）。 */
        int itemBreakTimer = 0;
        /** 当前相位中已播放过音效/执行过特殊动作的方块位置（相位切换时清空）。 */
        final java.util.Set<BlockPos> phaseActions = new java.util.HashSet<>();
        /** 本相位中已触发过周期性动作的合成器位置（休息相位时复位 CRAFTING=false）。 */
        final java.util.Set<BlockPos> crafterBlocks = new java.util.HashSet<>();
    }

    /** 玩家蹲下右键燃烧熔炉时，持续加速烹饪的映射表 */
    private static class FurnaceAccelInfo {
        final UUID playerId;
        final ResourceKey<Level> dimension;
        FurnaceAccelInfo(UUID playerId, ResourceKey<Level> dimension) {
            this.playerId = playerId;
            this.dimension = dimension;
        }
    }
    private static final java.util.Map<BlockPos, FurnaceAccelInfo> FURNACE_ACCEL_MAP = new java.util.HashMap<>();
    private static final java.util.Map<UUID, Long> HEROBRINE_TELEPORT_COOLDOWNS = new java.util.HashMap<>();

    private static final Map<UUID, Long> BELL_STAFF_IMMUNITY = new ConcurrentHashMap<>();

    private static final Set<UUID> LAPIS_FLIGHT_PLAYERS = new HashSet<>();

    private static final Set<UUID> COMMAND_FLIGHT_PLAYERS = new HashSet<>();

    private static final Set<UUID> HEROBRINE_FLIGHT_PLAYERS = new HashSet<>();

    // 持有 Omega 游戏图标 / Omega 权杖的玩家：授予飞行、夜视、免疫除虚空外伤害的特性
    private static final Set<UUID> OMEGA_POWER_PLAYERS = new HashSet<>();

    private static final Map<UUID, Integer> LAPIS_GRABBED_ENTITIES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAPIS_GRAB_XP_TIMERS = new ConcurrentHashMap<>();

    private static final Map<UUID, PortalStaffState> PORTAL_STAFF_STATES = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> PORTAL_PAIRS = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> PORTAL_LIFESPANS = new ConcurrentHashMap<>();

    private static final Map<UUID, long[]> PORTAL_CONTACT_TIMERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PORTAL_EXIT_LOCKS = new ConcurrentHashMap<>();
    // 玩家最新创建的一对传送门（记录其中一扇的 id，另一扇通过 PORTAL_PAIRS 关联）
    private static final Map<UUID, Integer> PLAYER_LATEST_PAIR = new ConcurrentHashMap<>();
    // R 键收敛动画：键为玩家最新对中一扇门的 id
    private static final Map<Integer, PortalCollapseState> PORTAL_COLLAPSES = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> PHYSICS_RETURN_TARGETS = new ConcurrentHashMap<>();

    static class PortalStaffState {
        int previewId = -1;              // 正在放置的预览传送门实体 id，-1 表示无
        boolean placingExit = false;     // false=入口, true=出口
        int pendingEntranceId = -1;      // 已放置入口的 id，供出口链接
        double previewDistance = 2.0;    // 当前预览延伸距离，按住右键会持续增大
    }

    // R 键收敛：让一对传送门以越来越快的速度相互靠近，重合后触发物理维度传送
    static class PortalCollapseState {
        int portalAId;
        int portalBId;
        double speed;                    // 当前每 tick 移动距离
        double accel;                    // 每 tick 速度增量
    }

    private static final ResourceLocation PHYSICS_DIM_ID = ResourceLocation.fromNamespaceAndPath(MODID, "physics_dimension");
    private static final Set<ResourceLocation> PHYSICS_BLOCKS_PLACED_DIMS = new HashSet<>();
    private static final Set<UUID> PHYSICS_NIGHT_VISION_PLAYERS = new HashSet<>();
    private static final Map<Integer, Vec3> PHYSICS_DELTA_MOVEMENTS = new ConcurrentHashMap<>();
    private static final Map<Integer, Vec3> PHYSICS_PRE_TICK_POSITIONS = new ConcurrentHashMap<>();
    private static final Set<UUID> PHYSICS_DIM_ENTITIES = ConcurrentHashMap.newKeySet();

    private static final Map<Integer, String> TRANSMUTATION_POTION_ITEM_TYPES = new ConcurrentHashMap<>();
    // 使用实体 UUID 作为键，以便跨区块卸载后仍能通过 reload 恢复变身并按时复原
    private static final Map<UUID, TransmutationData> ITEM_TRANSMUTATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> ITEM_TRANSMUTATION_POSITIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, TransmutationData> FALLING_TRANSMUTATIONS = new ConcurrentHashMap<>();
    // 生物变 TNT：被点燃后转交给 PrimedTnt 实体（PrimedTnt UUID -> 生物数据），待其爆炸瞬间才判死生物。
    private static final Map<UUID, TransmutationData> TNT_TRANSMUTATIONS = new ConcurrentHashMap<>();
    // 方块变身：同一个位置可能堆叠多条生物数据（多生物同格变成同一方块），因此值是 List
    private static final Map<ResourceLocation, Map<BlockPos, java.util.List<TransmutationData>>> BLOCK_TRANSMUTATIONS = new ConcurrentHashMap<>();

    // 玩家变身专用：复原时恢复其原始的游玩模式
    private static final Map<UUID, GameType> PLAYER_ORIGINAL_GAMEMODE = new ConcurrentHashMap<>();
    // 特殊物品授予创造模式：记录持有前玩家的原始游玩模式（生存/冒险），物品移除后恢复
    private static final Map<UUID, GameType> CREATIVE_GRANT_ORIGINAL = new ConcurrentHashMap<>();
    // 处于变身流程中的实体 UUID（用于避免滞留云重复触发二次变身）
    private static final Set<UUID> TRANSMUTED_ENTITIES = ConcurrentHashMap.newKeySet();
    // 玩家空壳与生物壳变身：壳体实体 UUID -> 原生物信息（含剩余时长）。
    // 玩家空壳由命名牌药水创造，生物壳由刷怪蛋药水创造；两者共用倒计时复原与解药复原。
    private static final Map<UUID, LivingShellData> LIVING_SHELLS = new ConcurrentHashMap<>();
    // 玩家“渲染替换”变身（Morph：玩家本体操控 + 客户端渲染为生物）：玩家UUID -> 剩余刻数。
    private static final Map<UUID, Integer> MORPH_REMAINING = new ConcurrentHashMap<>();
    // 玩家“渲染替换”变身时被替换的碰撞箱尺寸（玩家UUID -> 生物默认碰撞箱），复原时恢复玩家默认尺寸。
    private static final Map<UUID, net.minecraft.world.entity.EntityDimensions> MORPH_DIMENSIONS =
        new ConcurrentHashMap<>();
    // 玩家变形形态信息：决定伤害免疫规则与实体缩放
    private static final Map<UUID, PlayerTransmutationInfo> PLAYER_TRANSMUTATION_INFO = new ConcurrentHashMap<>();
    // 玩家变身后的原始 SCALE 属性值（复原时恢复）
    private static final Map<UUID, Double> PLAYER_ORIGINAL_SCALE = new ConcurrentHashMap<>();
    // 玩家“渲染替换”变身的生命信息（玩家UUID -> 原始最大生命值 / 生物最大生命值），
    // 变形开始把玩家最大生命设为生物的最大生命并按比例换算当前生命；复原时恢复并按比例换算回来（向上取整）。
    private static final Map<UUID, MorphHealthInfo> MORPH_HEALTH_INFO = new ConcurrentHashMap<>();

    /** 变形生命换算信息：originalMaxHealth=玩家变形前的最大生命值；mobMaxHealth=目标生物的最大生命值。 */
    record MorphHealthInfo(float originalMaxHealth, float mobMaxHealth) {
    }

    // /revive 机制：被 /kill 命令杀死的实体存档。外层键为实体 id（如 minecraft:pig），
    // 内层为多次 /kill 累计存入的多份实体 NBT（与变形药水存储格式一致）。
    // 初次 /kill 某实体时创建列表（含一个元素），之后 /kill 追加新元素而非重建列表；
    // /revive <id> 会随机抽取一个同 id 元素并删除。
    private static final Map<ResourceLocation, java.util.List<CompoundTag>> KILLED_ENTITY_STORAGE =
        new ConcurrentHashMap<>();

    record TransmutationData(
        CompoundTag entityNbt,
        int remainingTicks,
        java.util.UUID killerPlayerUuid,
        String itemType,
        java.util.UUID playerUuid
    ) {}

    // 生物壳/玩家空壳的变形数据：isPlayerShell 决定死亡播报用“玩家”还是“生物”体验卡消息
    record LivingShellData(
        CompoundTag entityNbt,
        java.util.UUID playerUuid,
        java.util.UUID killerUuid,
        int remainingTicks,
        boolean isPlayerShell
    ) {}

    // 玩家变形形态：物品 / 方块 / 玩家空壳 / 生物壳
    enum TransmutationForm { ITEM, BLOCK, PLAYER_SHELL, MOB }

    // 玩家变形形态信息：form 决定伤害免疫规则；itemType 为物品/方块 id 或生物实体 id
    record PlayerTransmutationInfo(TransmutationForm form, String itemType) {}

    private static final Map<Block, Block> MOSS_CONVERSION = new java.util.HashMap<>();
    static {
        MOSS_CONVERSION.put(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE);
        MOSS_CONVERSION.put(Blocks.COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_SLAB);
        MOSS_CONVERSION.put(Blocks.COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_STAIRS);
        MOSS_CONVERSION.put(Blocks.COBBLESTONE_WALL, Blocks.MOSSY_COBBLESTONE_WALL);
        MOSS_CONVERSION.put(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS);
        MOSS_CONVERSION.put(Blocks.STONE_BRICK_SLAB, Blocks.MOSSY_STONE_BRICK_SLAB);
        MOSS_CONVERSION.put(Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS);
        MOSS_CONVERSION.put(Blocks.STONE_BRICK_WALL, Blocks.MOSSY_STONE_BRICK_WALL);
    }
    private static final Map<Item, String> STAFF_BLOCKTYPE_WHITELIST = Map.ofEntries(
        Map.entry(net.minecraft.world.item.Items.AIR, "empty"),
        Map.entry(net.minecraft.world.item.Items.GOLD_BLOCK, "gold_block"),
        Map.entry(net.minecraft.world.item.Items.DIAMOND_BLOCK, "diamond_block"),
        Map.entry(net.minecraft.world.item.Items.NETHERITE_BLOCK, "netherite_block"),
        Map.entry(net.minecraft.world.item.Items.BEDROCK, "bedrock"),
        Map.entry(net.minecraft.world.item.Items.OBSIDIAN, "obsidian"),
        Map.entry(net.minecraft.world.item.Items.BONE_BLOCK, "bone_block"),
        Map.entry(net.minecraft.world.item.Items.FURNACE, "furnace"),
        Map.entry(net.minecraft.world.item.Items.BELL, "bell"),
        Map.entry(net.minecraft.world.item.Items.ANVIL, "anvil"),
        Map.entry(net.minecraft.world.item.Items.LAPIS_BLOCK, "lapis_block"),
        Map.entry(net.minecraft.world.item.Items.MAGMA_BLOCK, "magma_block"),
        Map.entry(net.minecraft.world.item.Items.COMMAND_BLOCK, "command_block"),
        Map.entry(net.minecraft.world.item.Items.END_PORTAL_FRAME, "end_portal_frame"),
        Map.entry(net.minecraft.world.item.Items.ENCHANTING_TABLE, "enchanting_table"),
        Map.entry(net.minecraft.world.item.Items.PLAYER_HEAD, "player_head"),
        Map.entry(net.minecraft.world.item.Items.BARRIER, "barrier"),
        Map.entry(net.minecraft.world.item.Items.DRIPSTONE_BLOCK, "dripstone_block"),
        Map.entry(net.minecraft.world.item.Items.CAULDRON, "cauldron"),
        Map.entry(net.minecraft.world.item.Items.CRAFTING_TABLE, "crafting_table"),
        Map.entry(net.minecraft.world.item.Items.EMERALD_BLOCK, "emerald_block"),
        Map.entry(net.minecraft.world.item.Items.ICE, "ice"),
        Map.entry(net.minecraft.world.item.Items.IRON_BLOCK, "iron_block"),
        Map.entry(net.minecraft.world.item.Items.NETHERRACK, "netherrack"),
        Map.entry(net.minecraft.world.item.Items.NOTE_BLOCK, "note_block"),
        Map.entry(net.minecraft.world.item.Items.OAK_LOG, "oak_log"),
        Map.entry(net.minecraft.world.item.Items.PISTON, "piston"),
        Map.entry(net.minecraft.world.item.Items.RED_MUSHROOM_BLOCK, "red_mushroom_block"),
        Map.entry(net.minecraft.world.item.Items.REDSTONE_BLOCK, "redstone_block"),
        Map.entry(net.minecraft.world.item.Items.SNOW_BLOCK, "snow_block"),
        Map.entry(net.minecraft.world.item.Items.BEE_NEST, "bee_nest"),
        Map.entry(net.minecraft.world.item.Items.AMETHYST_BLOCK, "amethyst_block"),
        Map.entry(net.minecraft.world.item.Items.COBWEB, "cobweb"),
        Map.entry(net.minecraft.world.item.Items.SPAWNER, "spawner"),
        Map.entry(net.minecraft.world.item.Items.TNT, "tnt")
    );

    private static final Map<String, Item> STAFF_BLOCKTYPE_REVERSE = Map.ofEntries(
        Map.entry("empty", net.minecraft.world.item.Items.AIR),
        Map.entry("gold_block", net.minecraft.world.item.Items.GOLD_BLOCK),
        Map.entry("diamond_block", net.minecraft.world.item.Items.DIAMOND_BLOCK),
        Map.entry("netherite_block", net.minecraft.world.item.Items.NETHERITE_BLOCK),
        Map.entry("bedrock", net.minecraft.world.item.Items.BEDROCK),
        Map.entry("obsidian", net.minecraft.world.item.Items.OBSIDIAN),
        Map.entry("bone_block", net.minecraft.world.item.Items.BONE_BLOCK),
        Map.entry("furnace", net.minecraft.world.item.Items.FURNACE),
        Map.entry("bell", net.minecraft.world.item.Items.BELL),
        Map.entry("anvil", net.minecraft.world.item.Items.ANVIL),
        Map.entry("lapis_block", net.minecraft.world.item.Items.LAPIS_BLOCK),
        Map.entry("magma_block", net.minecraft.world.item.Items.MAGMA_BLOCK),
        Map.entry("command_block", net.minecraft.world.item.Items.COMMAND_BLOCK),
        Map.entry("end_portal_frame", net.minecraft.world.item.Items.END_PORTAL_FRAME),
        Map.entry("enchanting_table", net.minecraft.world.item.Items.ENCHANTING_TABLE),
        Map.entry("player_head", net.minecraft.world.item.Items.PLAYER_HEAD),
        Map.entry("barrier", net.minecraft.world.item.Items.BARRIER),
        Map.entry("dripstone_block", net.minecraft.world.item.Items.DRIPSTONE_BLOCK),
        Map.entry("cauldron", net.minecraft.world.item.Items.CAULDRON),
        Map.entry("crafting_table", net.minecraft.world.item.Items.CRAFTING_TABLE),
        Map.entry("emerald_block", net.minecraft.world.item.Items.EMERALD_BLOCK),
        Map.entry("ice", net.minecraft.world.item.Items.ICE),
        Map.entry("iron_block", net.minecraft.world.item.Items.IRON_BLOCK),
        Map.entry("netherrack", net.minecraft.world.item.Items.NETHERRACK),
        Map.entry("note_block", net.minecraft.world.item.Items.NOTE_BLOCK),
        Map.entry("oak_log", net.minecraft.world.item.Items.OAK_LOG),
        Map.entry("piston", net.minecraft.world.item.Items.PISTON),
        Map.entry("red_mushroom_block", net.minecraft.world.item.Items.RED_MUSHROOM_BLOCK),
        Map.entry("redstone_block", net.minecraft.world.item.Items.REDSTONE_BLOCK),
        Map.entry("snow_block", net.minecraft.world.item.Items.SNOW_BLOCK),
        Map.entry("bee_nest", net.minecraft.world.item.Items.BEE_NEST),
        Map.entry("amethyst_block", net.minecraft.world.item.Items.AMETHYST_BLOCK),
        Map.entry("cobweb", net.minecraft.world.item.Items.COBWEB),
        Map.entry("spawner", net.minecraft.world.item.Items.SPAWNER),
        Map.entry("tnt", net.minecraft.world.item.Items.TNT)
    );

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);

    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.chunk.ChunkGenerator>> CHUNK_GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);

    static {
        RECIPE_SERIALIZERS.register("prismarine_arrow", () -> PrismarineArrowRecipe.SERIALIZER);
        CHUNK_GENERATORS.register("note_block_chunk_generator",
            () -> cn.autoforged.joes_addons_for_abmc.worldgen.NoteBlockChunkGenerator.CODEC);
    }

    public ModMain(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        CHUNK_GENERATORS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModMobEffects.MOB_EFFECTS.register(modEventBus);
        ModPotions.POTIONS.register(modEventBus);
        ModDamageTypes.DAMAGE_TYPES.register(modEventBus);
        ModCreativeTab.CREATIVE_MODE_TABS.register(modEventBus);
        cn.autoforged.joes_addons_for_abmc.worldgen.ModPoiTypes.POI_TYPES.register(modEventBus);

        modContainer.registerConfig(Type.COMMON, ModConfig.SPEC);

        // 在 Neoforge 模组菜单中为该 mod 显示“配置”按钮，点击进入基于 ModConfigSpec 生成的配置编辑界面。
        // ConfigurationScreen 为客户端类，仅存在于客户端分发；专用服务器上不注册以免解析客户端类。
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (minecraft, modListScreen) -> new ConfigurationScreen(modContainer, modListScreen));
        }

        NeoForge.EVENT_BUS.addListener(ModMain::onLivingIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ModMain::onLivingDamagePre);
        NeoForge.EVENT_BUS.addListener(ModMain::onCobwebPullFall);
        NeoForge.EVENT_BUS.addListener(ModMain::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(ModMain::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(ModMain::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(ModMain::onAdvancementEarn);
        NeoForge.EVENT_BUS.addListener(ModMain::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(ModMain::onPlayerLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(ModMain::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(ModMain::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(ModMain::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(ModMain::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ModMain::onServerStart);
        NeoForge.EVENT_BUS.addListener(ModMain::onServerStop);
        NeoForge.EVENT_BUS.addListener(ModMain::onServerTickPre);
        NeoForge.EVENT_BUS.addListener(ModMain::onEntityLeaveLevel);
        NeoForge.EVENT_BUS.addListener(ModMain::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(ModMain::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(ModMain::onEntityTickPost);
        NeoForge.EVENT_BUS.addListener(ModMain::onLivingBreathe);
        NeoForge.EVENT_BUS.addListener(ModMain::onRegisterBrewingRecipes);
        NeoForge.EVENT_BUS.addListener(ModMain::onMobEffectAdded);
        // 变形中饮用普通解药：vanilla 的效果施加通路在饮用完成后未生效（效果从未被 addEffect，
        // onMobEffectAdded 的解药分支因此不触发），这里在饮用完成时对变形中的原玩家直接执行解药复原。
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish ev) -> {
            try {
                ItemStack st = ev.getItem();
                if (st != null
                    && st.getItem() instanceof net.minecraft.world.item.PotionItem
                    && isAntidotePotion(st)
                    && ev.getEntity() instanceof ServerPlayer sp
                    && TRANSMUTED_ENTITIES.contains(sp.getUUID())
                    && sp.level() instanceof ServerLevel sl) {
                    applyAntidoteToEntity(sl, sp);
                }
            } catch (Exception ignored) {}
        });
        NeoForge.EVENT_BUS.addListener(ModMain::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(ModMain::onBlockBreakTransmutation);
        NeoForge.EVENT_BUS.addListener(ModMain::onEntityStruckByLightning);
        NeoForge.EVENT_BUS.addListener(ModMain::onExplosionDetonate);
        NeoForge.EVENT_BUS.addListener(ModMain::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(ModMain::onLivingChangeTarget);
        NeoForge.EVENT_BUS.addListener(ModMain::onTradeWithVillager);
        NeoForge.EVENT_BUS.addListener(ModMain::onStartTracking);
        NeoForge.EVENT_BUS.addListener(ModMain::onPlayerClone);
        // 游戏模式切换：切到创造/旁观默认解锁变形药水，切回生存/冒险恢复基线值
        NeoForge.EVENT_BUS.addListener(ModMain::onPlayerChangeGameMode);
        // 附魔生物特殊效果：弹射物命中（穿透/火矢）与死亡掉落（消失诅咒/精准采集）
        NeoForge.EVENT_BUS.addListener(ModMain::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(ModMain::onLivingDrops);
        // 附魔生物·经验修补：优先用经验回饱食度/饱和度
        NeoForge.EVENT_BUS.addListener(ModMain::onPlayerPickupXp);
        // 附魔生物·快速装填：受击后减少目标无敌帧
        NeoForge.EVENT_BUS.addListener(ModMain::onLivingDamagePost);
        // 附魔生物·迅捷潜行：行走时有概率不发出脚步音效
        NeoForge.EVENT_BUS.addListener(ModMain::onPlayLevelSound);
        // 附魔生物·魔咒状态效果到期：清除“自体附魔”标记与附魔光效，允许再次附魔
        NeoForge.EVENT_BUS.addListener(ModMain::onMobEffectExpired);
        // 女巫小屋·女巫 Boss：拥有 Boss 的小屋不再在其中自然刷新女巫
        NeoForge.EVENT_BUS.addListener(ModMain::onMobFinalizeSpawn);
        // Creeper Clan 维度：自然生成只允许苦力怕（无视光照由 SpawnPlacementCheck 强制）
        NeoForge.EVENT_BUS.addListener(ModMain::onMobSpawnPlacementCheck);
        // 区块加载：清理上个存档遗留的指令 Text Display 实体
        NeoForge.EVENT_BUS.addListener(ModMain::onChunkLoad);
        // 玩家变形：按生物尺寸动态改写玩家碰撞箱
        NeoForge.EVENT_BUS.addListener(ModMain::onEntitySize);

        modEventBus.addListener(ModMain::registerPayloads);
        modEventBus.addListener(ModMain::registerEntityAttributes);
        modEventBus.addListener(ModMain::onBuildCreativeTab);
    }

    // 玩家空壳：登记基本生物属性即可（无 AI，仅渲染用）。
    // 注意：1.21.1 的 Mob.createMobAttributes() 不再包含攻击属性，而 Mob.aiStep() 拾取物品时
    // 会查询 ATTACK_DAMAGE，缺失会导致崩溃，故需手动补上 ATTACK_DAMAGE/ATTACK_SPEED。
    private static void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.PLAYER_SHELL.get(),
            net.minecraft.world.entity.Mob.createMobAttributes()
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)
                .build());
        // 特制苦力怕是 LivingEntity，必须登记属性，否则首刻 tick 会崩溃而无法生成
        event.put(ModEntities.TNT_STAFF_CREEPER.get(),
            net.minecraft.world.entity.monster.Creeper.createAttributes().build());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MODID);
        registrar.playToServer(
            BlockingStatePayload.TYPE,
            BlockingStatePayload.STREAM_CODEC,
            (payload, context) -> {
                Player player = context.player();
                if (payload.blocking()) {
                    blockingPlayers.add(player.getUUID());
                } else {
                    blockingPlayers.remove(player.getUUID());
                }
            }
        );
        registrar.playToServer(
            StaffBlockTypePayload.TYPE,
            StaffBlockTypePayload.STREAM_CODEC,
            (payload, context) -> handleStaffBlockTypeSwap(context.player())
        );
        registrar.playToServer(
            GameIconCraftPayload.TYPE,
            GameIconCraftPayload.STREAM_CODEC,
            (payload, context) -> handleGameIconCraft(context.player())
        );
        registrar.playToClient(
            OmegaDismantlePayload.TYPE,
            OmegaDismantlePayload.STREAM_CODEC,
            (payload, context) ->
                cn.autoforged.joes_addons_for_abmc.item.StaffClientState.omegaDismantleForbiddenTicks =
                    cn.autoforged.joes_addons_for_abmc.item.StaffClientState.OMEGA_DISMANTLE_FORBIDDEN_DURATION
        );
        registrar.playToServer(
            CommandStaffActionPayload.TYPE,
            CommandStaffActionPayload.STREAM_CODEC,
            (payload, context) -> handleCommandStaffAction(context.player(), payload)
        );
        registrar.playToServer(
            PortalStaffInputPayload.TYPE,
            PortalStaffInputPayload.STREAM_CODEC,
            (payload, context) -> handlePortalStaffInput(context.player(), payload)
        );
        registrar.playToServer(
            cn.autoforged.joes_addons_for_abmc.network.PortalPlacePayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.PortalPlacePayload.STREAM_CODEC,
            (payload, context) -> handlePortalPlace(context.player(), payload)
        );
        registrar.playToServer(
            cn.autoforged.joes_addons_for_abmc.network.BarrierShiftPayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.BarrierShiftPayload.STREAM_CODEC,
            (payload, context) -> handleBarrierShift(context.player())
        );
        registrar.playToServer(
            cn.autoforged.joes_addons_for_abmc.network.EnchantStaffModeTogglePayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.EnchantStaffModeTogglePayload.STREAM_CODEC,
            (payload, context) -> handleEnchantStaffModeToggle(context.player())
        );
        registrar.playToServer(
            cn.autoforged.joes_addons_for_abmc.network.HerobrineStaffModeTogglePayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.HerobrineStaffModeTogglePayload.STREAM_CODEC,
            (payload, context) -> handleHerobrineStaffModeToggle(context.player())
        );
        registrar.playToServer(
            cn.autoforged.joes_addons_for_abmc.network.CommandStaffModeTogglePayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.CommandStaffModeTogglePayload.STREAM_CODEC,
            (payload, context) -> handleCommandStaffModeToggle(context.player(), payload.direction())
        );
        registrar.playToServer(
            cn.autoforged.joes_addons_for_abmc.network.CommandStaffTargetPayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.CommandStaffTargetPayload.STREAM_CODEC,
            (payload, context) -> handleCommandStaffTarget(context.player(), payload)
        );
        registrar.playToServer(
            cn.autoforged.joes_addons_for_abmc.network.RedstoneStaffChargePayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.RedstoneStaffChargePayload.STREAM_CODEC,
            (payload, context) -> handleRedstoneStaffCharge(context.player(), payload.charge())
        );
        registrar.playToServer(
            TntDetonatePayload.TYPE,
            TntDetonatePayload.STREAM_CODEC,
            (payload, context) -> handleTntStaffDetonate(context.player())
        );
        registrar.playToServer(
            CobwebDisconnectPayload.TYPE,
            CobwebDisconnectPayload.STREAM_CODEC,
            (payload, context) -> handleCobwebDisconnect(context.player())
        );
        registrar.playToClient(
            CobwebNullifyPayload.TYPE,
            CobwebNullifyPayload.STREAM_CODEC,
            (payload, context) ->
                cn.autoforged.joes_addons_for_abmc.client.CobwebClientState.nullify(payload.entityId())
        );
        registrar.playToClient(
            CobwebClearPayload.TYPE,
            CobwebClearPayload.STREAM_CODEC,
            (payload, context) ->
                cn.autoforged.joes_addons_for_abmc.client.CobwebClientState.clear(payload.entityId())
        );
        // 移植头：服务端告知客户端某实体拥有“移植头”以及头来源类型，客户端据此渲染。
        registrar.playToClient(
            cn.autoforged.joes_addons_for_abmc.network.TransplantedHeadPayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.TransplantedHeadPayload.STREAM_CODEC,
            (payload, context) -> cn.autoforged.joes_addons_for_abmc.client.TransplantedHeadClientState.setHeadType(
                payload.entityId(), payload.headTypeId())
        );
        // 移植脚：服务端告知客户端某实体拥有“移植脚”以及脚来源类型，客户端据此渲染。
        registrar.playToClient(
            cn.autoforged.joes_addons_for_abmc.network.TransplantedFeetPayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.TransplantedFeetPayload.STREAM_CODEC,
            (payload, context) -> cn.autoforged.joes_addons_for_abmc.client.TransplantedFeetClientState.setFeetType(
                payload.entityId(), payload.feetTypeId())
        );
        // 自体附魔：服务端告知客户端某实体被“自体附魔”（空手），需持续显示附魔光效。
        registrar.playToClient(
            cn.autoforged.joes_addons_for_abmc.network.EnchantSelfPayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.EnchantSelfPayload.STREAM_CODEC,
            (payload, context) -> cn.autoforged.joes_addons_for_abmc.item.StaffClientState.setEnchantSelf(
                payload.entityId(), payload.enchanted())
        );
        // 变形药水视角：服务端告知客户端强制第三人称（并把初始俯仰设为斜向下45°）/ 恢复第一人称。
        registrar.playToClient(
            cn.autoforged.joes_addons_for_abmc.network.TransmutationCameraPayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.TransmutationCameraPayload.STREAM_CODEC,
            (payload, context) -> {
                cn.autoforged.joes_addons_for_abmc.client.TransmutationCameraClient.setThirdPerson(
                    payload.thirdPerson());
                if (payload.thirdPerson()) {
                    cn.autoforged.joes_addons_for_abmc.client.TransmutationCameraClient.setInitialPitch(
                        payload.initialPitch());
                }
            }
        );
        // 变形状态：服务端告知客户端玩家正/未处于变形，并给出被变成实体的实体ID，
        // 客户端据此把实体贴到本地玩家脚下（平滑跟随）并彻底隐藏变形玩家自身。
        registrar.playToClient(
            cn.autoforged.joes_addons_for_abmc.network.TransmutationStatePayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.TransmutationStatePayload.STREAM_CODEC,
            (payload, context) ->
                cn.autoforged.joes_addons_for_abmc.client.TransmutationCameraClient.onTransmutationState(
                    payload.transmuted(), payload.followEntityId(), payload.morphEntityType())
        );
        // 蛛丝线段：开始拉扯 / 停止拉扯
        registrar.playToClient(
            CobwebPullPayload.TYPE,
            CobwebPullPayload.STREAM_CODEC,
            (payload, context) ->
                cn.autoforged.joes_addons_for_abmc.client.CobwebBeamClient.start(payload.anchor())
        );
        registrar.playToClient(
            CobwebPullStopPayload.TYPE,
            CobwebPullStopPayload.STREAM_CODEC,
            (payload, context) ->
                cn.autoforged.joes_addons_for_abmc.client.CobwebBeamClient.clear()
        );
        // 铁链权杖：开始/更新钩取（渲染玩家发射点→目标铁链；后续包仅刷新终点）
        registrar.playToClient(
            cn.autoforged.joes_addons_for_abmc.network.ChainGrabPayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.ChainGrabPayload.STREAM_CODEC,
            (payload, context) -> cn.autoforged.joes_addons_for_abmc.client.ChainBeamClient.start(
                payload.mode(), payload.sx(), payload.sy(), payload.sz(),
                payload.ex(), payload.ey(), payload.ez(), payload.entityId())
        );
        // 铁链权杖：停止钩取（清除铁链渲染）
        registrar.playToClient(
            cn.autoforged.joes_addons_for_abmc.network.ChainGrabStopPayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.ChainGrabStopPayload.STREAM_CODEC,
            (payload, context) -> cn.autoforged.joes_addons_for_abmc.client.ChainBeamClient.clear()
        );
        // 铁链权杖：未命中发射收回动画
        registrar.playToClient(
            cn.autoforged.joes_addons_for_abmc.network.ChainLaunchPayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.ChainLaunchPayload.STREAM_CODEC,
            (payload, context) -> cn.autoforged.joes_addons_for_abmc.client.ChainBeamClient.launch(
                payload.ex(), payload.ey(), payload.ez())
        );
        // 铁链权杖：左键中断（客户端→服务端）
        registrar.playToServer(
            cn.autoforged.joes_addons_for_abmc.network.ChainCancelPayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.ChainCancelPayload.STREAM_CODEC,
            (payload, context) -> ModMain.cancelChainGrab(context.player())
        );
        // ===== C 脚本网络层 =====
        registrar.playToServer(
            ScriptGraphPayload.TYPE,
            ScriptGraphPayload.STREAM_CODEC,
            (payload, context) -> ScriptNetworking.handleGraphAction(context.player(), payload)
        );
        registrar.playToServer(
            ScriptFunctionPayload.TYPE,
            ScriptFunctionPayload.STREAM_CODEC,
            (payload, context) -> ScriptNetworking.handleFunctionAction(context.player(), payload)
        );
        registrar.playToServer(
            ScriptRunPayload.TYPE,
            ScriptRunPayload.STREAM_CODEC,
            (payload, context) -> ScriptNetworking.handleRunAction(context.player(), payload)
        );
        registrar.playBidirectional(
            ScriptBroadcastPayload.TYPE,
            ScriptBroadcastPayload.STREAM_CODEC,
            (payload, context) -> {
                if (context.flow() == net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
                    ScriptNetworking.handleBroadcastFromClient(context.player(), payload);
                } else {
                    ScriptNetworking.handleBroadcastToClient(payload);
                }
            }
        );
        registrar.playToClient(
            ScriptLibrarySyncPayload.TYPE,
            ScriptLibrarySyncPayload.STREAM_CODEC,
            (payload, context) -> ScriptNetworking.onClientLibrarySync(payload)
        );
        registrar.playToClient(
            ScriptFunctionSyncPayload.TYPE,
            ScriptFunctionSyncPayload.STREAM_CODEC,
            (payload, context) -> ScriptNetworking.onClientFunctionSync(payload)
        );
        registrar.playToClient(
            cn.autoforged.joes_addons_for_abmc.network.NoteMusicContextPayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.NoteMusicContextPayload.STREAM_CODEC,
            (payload, context) -> cn.autoforged.joes_addons_for_abmc.client.NoteUniverseMusicClient.onContextPacket(payload)
        );
        registrar.playToClient(
            cn.autoforged.joes_addons_for_abmc.network.DebugStringPayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.DebugStringPayload.STREAM_CODEC,
            (payload, context) -> cn.autoforged.joes_addons_for_abmc.client.DebugStringRenderer.activate(payload)
        );
    }

    // 是否已因完成全部原版成就而赠送过权杖
    private static final String ALL_VANILLA_ACHIEVEMENTS_REWARDED_TAG = "jafa_all_vanilla_rewarded";
    // 是否已因完成全部成就（原版 + 所有模组）而赠送过 minecraft game icon
    private static final String ALL_ACHIEVEMENTS_REWARDED_TAG = "jafa_all_achievements_rewarded";
    // 是否已按“开局赠送权杖”配置赠送过初始权杖（仅首次进入世界赠送一次）
    private static final String STAFF_GIVEN_ON_START_TAG = "jafa_staff_given_on_start";

    // 附魔台权杖模式标记（true=疯狂模式，false=日常模式）
    private static final String ENCHANT_STAFF_CRAZY_TAG = "jafa_enchant_staff_crazy";
    // 附魔台权杖·疯狂模式解锁标记。目前不存在任何解锁途径，生存/冒险模式下恒为 false，
    // 因此生存模式暂时无法使用疯狂模式。未来可在此加入解锁条件（例如完成特定成就/进度/tag 等），
    // 解锁后把该标记写入 true 即可放行。创造模式（及旁观自动解锁逻辑）不受此标记限制。
    private static final String ENCHANT_STAFF_UNLOCKED_TAG = "jafa_enchant_staff_unlocked";
    // 变形药水的“掷出者免疫”标记（永久，不受游戏模式切换影响）。持有该标记的玩家对自己丢出的
    // 变形药水免疫。可通过击败女巫Boss解锁，或用 /jafa toggletransmutationdebug <true|false> 自由开关。
    private static final String TRANSFORM_POTION_IMMUNE_TAG = "jafa_transform_potion_immune";
    // 红石块权杖充能数（1~8）：持久化到玩家数据，退出存档重进后恢复（需求：保留各权杖模式/充能数）。
    private static final String REDSTONE_STAFF_CHARGE_TAG = "jafa_redstone_staff_charge";
    /** 变形物品被摧毁/被漏斗吸走时重建并击杀的原生物标记：跳过 /kill 存档（防内存膨胀与卡顿）。 */
    private static final String TRANSMUTATION_REKILL_TAG = "jafa_transmutation_rekill";

    /** 疯狂模式给生物自体附魔后，记录“给予该状态效果的权杖使用者”（忠诚传送用）。 */
    private static final String ENCHANT_STAFF_GRANTER_TAG = "jafa_enchant_staff_granter";
    /** 多重射击分裂出的弹射物标记（防再次分裂）。 */
    private static final String MULTISHOT_COPY_TAG = "jafa_multishot_copy";
    /** 引雷生成的闪电：存储攻击者（附魔生物）UUID（使其不伤及攻击者）。 */
    private static final String CHANNELING_LIGHTNING_OWNER = "jafa_channeling_owner";
    /** 穿透剩余可穿透次数 NBT 键（每次成功穿透减 1，归零后不再穿透）。 */
    private static final String PIERCE_REMAIN_TAG = "jafa_pierce_remaining";
    /** 多重射击已完成分裂标记（NBT 持久化，防止块加载后旧弹射物再次分裂）。 */
    private static final String MULTISHOT_DONE_TAG = "jafa_multishot_done";
    /** 多重射击：副本相对原弹的水平散布半宽（弧度）。 */
    private static final double MULTISHOT_HALF_SPREAD = 0.5;
    /** 附魔生物·激流：水中/雨中移动速度属性修改器 id。 */
    private static final ResourceLocation RIPTIDE_MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "enchant_riptide");
    /** 附魔生物·深海探索者：水中移动速度属性修改器 id。 */
    private static final ResourceLocation DEPTH_STRIDER_MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "enchant_depth_strider");
    /** 附魔生物·灵魂疾行：灵魂沙/灵魂土上速度属性修改器 id。 */
    private static final ResourceLocation SOUL_SPEED_MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "enchant_soul_speed");
    /** 附魔生物·快速装填：攻击速度属性修改器 id。 */
    private static final ResourceLocation QUICK_CHARGE_ATTACK_SPEED_MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "enchant_quick_charge_attack_speed");
    /** 绑定诅咒/忠诚传送距离阈值（平方距离，12 格）。 */
    private static final double ENCHANT_CURSE_TELEPORT_THRESHOLD_SQ = 144.0;
    /** 经验修补吸引经验球的最大半径（格）。 */
    private static final double MENDING_ORB_ATTRACT_RADIUS = 2.0;
    /** 附魔生物·穿透：单支弹射物最多可连续穿透的实体数。 */
    private static final int ENCHANT_PIERCING_MAX_PASS = 8;
    /** 附魔生物·破甲：正在以真实伤害结算的目标实体 id（防止重复结算）。 */
    private static final java.util.Set<Integer> ENCH_BREACH_APPLYING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 附魔生物·火矢：记录本刻应强制产生火焰爆炸、且不伤及射手的弹射物 id。 */
    private static final Map<Integer, UUID> FIRE_EXPLOSIVE_PROJECTILES = new java.util.HashMap<>();

    // Him 权杖模式标记（true=远程模式，false=近战模式），默认近战模式
    private static final String HEROBRINE_STAFF_RANGED_TAG = "jafa_herobrine_staff_ranged";

    // 命令方块权杖能力模式标记（int：0=无，1=击杀，2=抓取，3=启用/禁用AI，4=护盾），默认无模式(0)
    private static final String COMMAND_STAFF_MODE_TAG = "jafa_command_staff_mode";
    /** 命令方块权杖模式：无（左键无任何行为）。 */
    private static final int COMMAND_STAFF_MODE_NONE = 0;
    /** 命令方块权杖模式：击杀。 */
    private static final int COMMAND_STAFF_MODE_KILL = 1;
    /** 命令方块权杖模式：抓取。 */
    private static final int COMMAND_STAFF_MODE_GRAB = 2;
    /** 命令方块权杖模式：启用/禁用AI。 */
    private static final int COMMAND_STAFF_MODE_TOGGLE_AI = 3;
    /** 命令方块权杖模式：护盾。 */
    private static final int COMMAND_STAFF_MODE_SHIELD = 4;
    /** 命令方块权杖模式总数（切换时循环）。 */
    private static final int COMMAND_STAFF_MODE_COUNT = 5;

    /** 读取玩家的命令方块权杖模式；标签不存在时默认无模式(0)。 */
    private static int getCommandStaffMode(CompoundTag data) {
        return data.contains(COMMAND_STAFF_MODE_TAG)
            ? data.getInt(COMMAND_STAFF_MODE_TAG) : COMMAND_STAFF_MODE_NONE;
    }

    // ===== 蜘蛛网权杖 =====
    /** 无效化持续时间（刻）：30 秒。 */
    private static final int COBWEB_NULLIFY_DURATION = 600;
    /** 中键解除无效化所需次数。 */
    private static final int COBWEB_CANCEL_CLICKS = 3;
    /** 豁免无效化的权杖类型：minecraft / omega / spider_web / 屏障 / 绿宝石块。 */
    private static final java.util.Set<String> COBWEB_EXEMPT_BLOCKTYPES =
        java.util.Set.of("minecraft_game_icon", "omega", "cobweb", "barrier", "emerald_block");
    /** 蛛丝最大射程（格）。 */
    private static final double COBWEB_MAX_RANGE = 128.0;

    // ===== 沉船溺尸刷新 =====
    /** 原版沉船结构对应的资源键（minecraft:shipwreck）。 */
    private static final ResourceKey<Structure> SHIPWRECK_STRUCTURE =
        ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace("shipwreck"));
    /** 沉船结构周围刷溺尸的范围（格）：结构包围盒 ±10 格。 */
    private static final int SHIPWRECK_SPAWN_RANGE = 10;
    /** 刷新间隔区间（刻）：每 10~20 分钟随机取一次（10*60*20=12000，20*60*20=24000）。 */
    private static final int SHIPWRECK_MIN_INTERVAL_TICKS = 12000;
    private static final int SHIPWRECK_MAX_INTERVAL_TICKS = 24000;
    /** 每批溺尸数量区间。 */
    private static final int SHIPWRECK_DROWNED_MIN = 10;
    private static final int SHIPWRECK_DROWNED_MAX = 20;
    /** 光照抑制阈值：候选生成格的最大原始亮度需 ≤ 该值（越亮越抑制，原版溺尸需黑暗）。 */
    private static final int SHIPWRECK_SPAWN_MAX_LIGHT = 7;
    /** 以玩家为中心扫描沉船的区块半径（只查已生成的区块，绝不强制生成）。 */
    private static final int SHIPWRECK_SCAN_RADIUS_CHUNKS = 10;
    /** 若沉船周围该格数内已存在溺尸领袖，则本波不再刷新。 */
    private static final int SHIPWRECK_LEADER_EXIST_RANGE = 50;
    /** 玩家距结构的刷新有效距离：距结构过近(≤该值)或过远(>该值)都不刷新。 */
    private static final double SHIPWRECK_PLAYER_MIN_DISTANCE = 24.0;
    private static final double SHIPWRECK_PLAYER_MAX_DISTANCE = 120.0;
    /** 「世界生成必刷」持久批次的检查间隔（刻）：每 5 秒（5*20=100 刻）补查一次已加载沉船。 */
    private static final int SHIPWRECK_INITIAL_CHECK_INTERVAL = 100;
    /** 沉船溺尸领袖的持久标记（用于在死亡时结算 4 倍经验）。 */
    private static final String SHIPWRECK_LEADER_TAG = "joes_addons_for_abmc_shipwreck_leader";
    /** 沉船溺尸刷新计时器（刻计数器）与当前随机间隔（刻）。 */
    private static int shipwreckDrownedTick = 0;
    private static int shipwreckSpawnInterval = SHIPWRECK_MIN_INTERVAL_TICKS;
    /** 「世界生成必刷」持久批次的检查计时器（刻）。 */
    private static int shipwreckInitialCheckTick = 0;
    /** 已刷过「世界生成必刷批次」的沉船中心坐标（避免重复）。 */
    private static final Set<Long> SHIPWRECK_INITIAL_SPAWNED = new HashSet<>();
    /** 「到点但条件未满足」时的重试间隔（刻）：每 2 秒（2*20=40 刻）重试一次，
     *  避免每个 tick 都做整轮沉船扫描拖慢服务端主线程（也影响退出存档保存）。 */
    private static final int SHIPWRECK_RETRY_INTERVAL = 40;
    /** 到点后的重试计数（刻）。 */
    private static int shipwreckRetryTick = 0;

    /** 无效化状态：实体 UUID -> 状态。 */
    private static final java.util.Map<java.util.UUID, CobwebNullifyState> COBWEB_NULLIFIED =
        new java.util.concurrent.ConcurrentHashMap<>();
    /** 拉扯状态：玩家 UUID -> 拉扯目标。 */
    private static final java.util.Map<java.util.UUID, CobwebPullState> COBWEB_PULLING =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** 最近一次拉扯的结束时刻（毫秒）：拉动结束后 3 秒内仍免疫摔落/动能伤害，避免断丝后下落摔伤。 */
    private static final java.util.Map<java.util.UUID, Long> COBWEB_PULL_END_TIME =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** 玩家是否处于“拉动中，或拉动刚结束 3 秒内”的摔落/动能伤害免疫期。 */
    private static boolean isCobwebPullDamageImmune(java.util.UUID playerId) {
        if (COBWEB_PULLING.containsKey(playerId)) return true;
        Long end = COBWEB_PULL_END_TIME.get(playerId);
        return end != null && (System.currentTimeMillis() - end) < 3000L;
    }

    /** 记录拉动结束时刻，为断开后的免疫宽限期计时。 */
    private static void recordCobwebPullEnd(java.util.UUID playerId) {
        COBWEB_PULL_END_TIME.put(playerId, System.currentTimeMillis());
    }

    private static class CobwebNullifyState {
        final int entityId;
        final long expireTick;
        int clickCount;
        CobwebNullifyState(int entityId, long expireTick) {
            this.entityId = entityId;
            this.expireTick = expireTick;
        }
    }

    private static class CobwebPullState {
        final Vec3 target;
        final int rampTicks;
        final double apexAccel;
        /** 起始初速度（拉到瞬间给一个不从 0 起步的速度，指向目标）。 */
        final Vec3 initialVelocity;
        /** 累积速度（在“无阻力”空间中累加加速度；每刻直接写入玩家速度，避免被每刻空气阻力
         *  反复衰减导致加速完成后速度自行降回低速）。 */
        Vec3 vel;
        int elapsed;
        /** 连续被方块阻挡的刻度数：超过 100（5 秒）仍未脱困则断开；期间快速前移或脱困则重置。 */
        int blockedTicks;
        CobwebPullState(Vec3 target, int rampTicks, double apexAccel, Vec3 initialVelocity) {
            this.target = target;
            this.rampTicks = rampTicks;
            this.apexAccel = apexAccel;
            this.initialVelocity = initialVelocity;
            // 抗阻挡判定在首帧（elapsed==1 赋值前）就会读取 vel.length()，必须在此预先初始化以避免 NPE。
            this.vel = initialVelocity;
        }
    }

    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        var data = serverPlayer.getPersistentData();
        // 变形药水“掷出者免疫”只由击败女巫Boss或 /jafa toggletransmutationdebug <true|false> 控制，
        // 不在此初始化，默认 false（变形药水可影响自己）。
        // 同步附魔台权杖模式到客户端（用于 HUD 显示）
        PacketDistributor.sendToPlayer(serverPlayer,
            new cn.autoforged.joes_addons_for_abmc.network.EnchantStaffModePayload(
                data.getBoolean(ENCHANT_STAFF_CRAZY_TAG)));
        // 同步 Him 权杖模式到客户端（用于 HUD 提示）
        PacketDistributor.sendToPlayer(serverPlayer,
            new cn.autoforged.joes_addons_for_abmc.network.HerobrineStaffModePayload(
                data.getBoolean(HEROBRINE_STAFF_RANGED_TAG)));
        // 同步命令方块权杖能力模式到客户端（用于 HUD 提示）
        PacketDistributor.sendToPlayer(serverPlayer,
            new cn.autoforged.joes_addons_for_abmc.network.CommandStaffModePayload(
                getCommandStaffMode(data)));
        // 兼容已安装本 mod 前就完成全部原版成就的玩家：登录时补发奖励
        checkAndRewardAllVanillaAchievements(serverPlayer);
        // 兼容已安装本 mod 前就完成全部成就（原版 + 所有模组）的玩家：登录时补发 minecraft game icon
        checkAndRewardAllAchievements(serverPlayer);
        // 开局赠送权杖：开启配置且玩家首次进入世界时，赠送一把 blocktype 为 empty 的权杖（仅一次）
        giveStaffOnStartIfEnabled(serverPlayer);
        // 玩家中途退出存档再进入时，若仍处于变形状态（物品/方块/壳仍在世界中），重新同步客户端状态，
        // 避免平滑跟随与彻底隐身失效。
        resyncTransmutationState(serverPlayer);
        // 容错：若玩家并非变形状态（如服务器重启后变形数据已清空），但 SCALE 属性残留了缩放值，
        // 强制恢复为默认 1.0，避免玩家保持微小体型。
        if (!TRANSMUTED_ENTITIES.contains(serverPlayer.getUUID())) {
            AttributeInstance scaleAttr = serverPlayer.getAttribute(Attributes.SCALE);
            if (scaleAttr != null && Math.abs(scaleAttr.getBaseValue() - 1.0) > 1.0E-4) {
                scaleAttr.setBaseValue(1.0);
            }
            PLAYER_ORIGINAL_SCALE.remove(serverPlayer.getUUID());
        }
    }

    /** 玩家登录时若仍处于变形状态，重新下发变形状态（携带被变成实体的实体ID），恢复客户端跟随/隐身。 */
    private static void resyncTransmutationState(ServerPlayer player) {
        UUID uid = player.getUUID();
        if (!TRANSMUTED_ENTITIES.contains(uid)) return;
        ServerLevel level = player.serverLevel();
        for (var e : ITEM_TRANSMUTATIONS.entrySet()) {
            if (uid.equals(e.getValue().playerUuid())) {
                Entity ent = level.getEntity(e.getKey());
                if (ent != null && ent.isAlive()) {
                    sendTransmutationState(player, true, ent.getId());
                    return;
                }
            }
        }
        for (var e : FALLING_TRANSMUTATIONS.entrySet()) {
            if (uid.equals(e.getValue().playerUuid())) {
                Entity ent = level.getEntity(e.getKey());
                if (ent != null && ent.isAlive()) {
                    sendTransmutationState(player, true, ent.getId());
                    return;
                }
            }
        }
        for (var e : LIVING_SHELLS.entrySet()) {
            if (uid.equals(e.getValue().playerUuid())) {
                for (ServerLevel l : player.getServer().getAllLevels()) {
                    Entity ent = l.getEntity(e.getKey());
                    if (ent != null && ent.isAlive()) {
                        sendTransmutationState(player, true, ent.getId());
                        return;
                    }
                }
            }
        }
    }

    /**
     * 若“开局赠送权杖”配置开启且该玩家尚未领取过，则赠送一把 blocktype 为 empty 的权杖（仅一次）。
     */
    private static void giveStaffOnStartIfEnabled(ServerPlayer serverPlayer) {
        if (!ModConfig.GIVE_STAFF_ON_START.get()) return;
        var data = serverPlayer.getPersistentData();
        if (data.getBoolean(STAFF_GIVEN_ON_START_TAG)) return;

        data.putBoolean(STAFF_GIVEN_ON_START_TAG, true);

        ItemStack reward = new ItemStack(ModItems.STAFF.get());
        StaffItem.setBlockDamage(reward, 0);
        if (!serverPlayer.getInventory().add(reward)) {
            serverPlayer.spawnAtLocation(reward);
        }
        serverPlayer.displayClientMessage(
            Component.translatable("message.joes_addons_for_abmc.staff_given_on_start"),
            false);
    }

    /**
     * 当玩家完成一项成就时触发。若此时玩家已点亮全部原版成就，则赠送一把权杖（仅一次）；
     * 若已点亮全部成就（原版 + 所有模组，配方类除外），则赠送一个 minecraft game icon（仅一次）。
     */
    private static void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        if ("minecraft".equals(event.getAdvancement().id().getNamespace())) {
            checkAndRewardAllVanillaAchievements(serverPlayer);
        }
        checkAndRewardAllAchievements(serverPlayer);
    }

    /**
     * 检查玩家是否已点亮全部原版成就；若已点亮且尚未赠送过，则赠送一把权杖。
     */
    private static void checkAndRewardAllVanillaAchievements(ServerPlayer serverPlayer) {
        var data = serverPlayer.getPersistentData();
        if (data.getBoolean(ALL_VANILLA_ACHIEVEMENTS_REWARDED_TAG)) return;
        if (!hasAllVanillaAchievements(serverPlayer)) return;

        data.putBoolean(ALL_VANILLA_ACHIEVEMENTS_REWARDED_TAG, true);

        ItemStack reward = new ItemStack(ModItems.STAFF.get());
        StaffItem.setBlockDamage(reward, 0);
        if (!serverPlayer.getInventory().add(reward)) {
            serverPlayer.spawnAtLocation(reward);
        }
        serverPlayer.displayClientMessage(
            Component.translatable("message.joes_addons_for_abmc.all_vanilla_achievements_reward"),
            false);
    }

    /**
     * 判断玩家是否已点亮原版成就界面（Minecraft / 冒险 / 下界 / 末地 / 农牧业，含隐藏成就）中的全部成就。
     * 配方类成就（minecraft:recipes/...）不属于成就界面，需排除。
     */
    private static boolean hasAllVanillaAchievements(ServerPlayer serverPlayer) {
        PlayerAdvancements advancements = serverPlayer.getAdvancements();
        for (AdvancementHolder holder : serverPlayer.server.getAdvancements().getAllAdvancements()) {
            if (!"minecraft".equals(holder.id().getNamespace())) continue;
            // 排除配方成就（recipe book 解锁，不显示在成就界面）
            if (holder.id().getPath().startsWith("recipes/")) continue;
            AdvancementProgress progress = advancements.getOrStartProgress(holder);
            if (!progress.isDone()) return false;
        }
        return true;
    }

    /**
     * 检查玩家是否已点亮全部成就（原版 + 所有模组，配方类除外）；若已点亮且尚未赠送过，
     * 则赠送一个 minecraft game icon（仅一次）。
     */
    private static void checkAndRewardAllAchievements(ServerPlayer serverPlayer) {
        var data = serverPlayer.getPersistentData();
        if (data.getBoolean(ALL_ACHIEVEMENTS_REWARDED_TAG)) return;
        if (!hasAllAchievements(serverPlayer)) return;

        data.putBoolean(ALL_ACHIEVEMENTS_REWARDED_TAG, true);

        ItemStack reward = new ItemStack(ModItems.GAME_ICON.get());
        if (!serverPlayer.getInventory().add(reward)) {
            serverPlayer.spawnAtLocation(reward);
        }
        serverPlayer.displayClientMessage(
            Component.translatable("message.joes_addons_for_abmc.all_achievements_reward"),
            false);
    }

    /**
     * 判断玩家是否已点亮全部成就（原版 + 所有模组，配方类除外）：
     * 遍历服务端加载的全部成就，任一未完成即视为未达成。
     */
    private static boolean hasAllAchievements(ServerPlayer serverPlayer) {
        PlayerAdvancements advancements = serverPlayer.getAdvancements();
        for (AdvancementHolder holder : serverPlayer.server.getAdvancements().getAllAdvancements()) {
            // 配方成就（recipe book 解锁）不显示在成就界面，需排除
            if (holder.id().getPath().startsWith("recipes/")) continue;
            AdvancementProgress progress = advancements.getOrStartProgress(holder);
            if (!progress.isDone()) return false;
        }
        return true;
    }

    /**
     * 客户端请求切换附魔台权杖模式（日常模式 &lt;-&gt; 疯狂模式），服务端持久化并回传新的模式。
     * <p>生存/冒险模式下，疯狂模式默认未解锁（见 {@link #ENCHANT_STAFF_UNLOCKED_TAG}），
     * 此时切到疯狂模式的请求会被忽略；创造模式可直接使用疯狂模式。</p>
     */
    private static void handleEnchantStaffModeToggle(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        var data = serverPlayer.getPersistentData();
        // 创造模式直接放行；生存/冒险模式需先解锁（目前无解锁途径，恒为 false）
        boolean canUseCrazy = serverPlayer.gameMode.getGameModeForPlayer() == GameType.CREATIVE
            || data.getBoolean(ENCHANT_STAFF_UNLOCKED_TAG);
        boolean crazy = !data.getBoolean(ENCHANT_STAFF_CRAZY_TAG);
        // 请求切到疯狂模式但未解锁：拒绝（保持日常模式）
        if (crazy && !canUseCrazy) return;
        data.putBoolean(ENCHANT_STAFF_CRAZY_TAG, crazy);
        PacketDistributor.sendToPlayer(serverPlayer,
            new cn.autoforged.joes_addons_for_abmc.network.EnchantStaffModePayload(crazy));
    }

    /**
     * 客户端请求切换 Him 权杖模式（近战模式 &lt;-&gt; 远程模式），服务端持久化并回传新的模式。
     * 玩家 persistent data 与 ItemStack 无关，同一玩家无论用几把 Him 权杖共享同一模式。
     */
    private static void handleHerobrineStaffModeToggle(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        var data = serverPlayer.getPersistentData();
        boolean ranged = !data.getBoolean(HEROBRINE_STAFF_RANGED_TAG);
        data.putBoolean(HEROBRINE_STAFF_RANGED_TAG, ranged);
        PacketDistributor.sendToPlayer(serverPlayer,
            new cn.autoforged.joes_addons_for_abmc.network.HerobrineStaffModePayload(ranged));
    }

    /** 命令方块权杖：抓取模式中每刻被拉拽的目标（玩家 UUID → 目标实体 UUID）。 */
    private static final java.util.Map<java.util.UUID, java.util.UUID> COMMAND_STAFF_GRAB_TARGETS =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** 命令方块权杖：抓取模式中玩家头顶常驻的指令 Text Display（玩家 UUID → TextDisplay）。 */
    private static final java.util.Map<java.util.UUID, net.minecraft.world.entity.Display.TextDisplay> COMMAND_STAFF_GRAB_DISPLAYS =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** 命令方块权杖：护盾模式中玩家头顶常驻的指令 Text Display（玩家 UUID → TextDisplay）。 */
    private static final java.util.Map<java.util.UUID, net.minecraft.world.entity.Display.TextDisplay> COMMAND_STAFF_SHIELD_DISPLAYS =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** 命令方块权杖：护盾模式反弹半径（格）。 */
    private static final double COMMAND_STAFF_SHIELD_RADIUS = 7.0;
    /** 命令方块权杖：护盾模式探测余量（格）。用于在弹射物穿越球面的那一刻于球面处拦截，需大于弹射物单刻最大位移。 */
    private static final double COMMAND_STAFF_SHIELD_DETECT_MARGIN = 6.0;
    /** 命令方块权杖：护盾模式对「诞生于球内」弹射物的径向斥力加速度（格/刻²）。随距离衰减，到球面边界衰减为 0。 */
    private static final double COMMAND_STAFF_SHIELD_REPULSE_ACCEL = 0.5;
    /** 命令方块权杖：护盾模式常驻头顶显示的指令文本。 */
    private static final String COMMAND_STAFF_SHIELD_TEXT =
        "/attribute @p generic.shield_size base set 7";
    /** 指令 Text Display 实体的持久数据标记：用于区块加载时识别并清理跨存档遗留实体（退出存档时未销毁、随区块保存的残留）。 */
    private static final String COMMAND_TEXT_TAG = "jafa_command_text";

    /**
     * 客户端请求切换命令方块权杖能力（无 &lt;-&gt; 击杀 &lt;-&gt; 抓取 &lt;-&gt; 启用/禁用AI &lt;-&gt; 护盾 循环），
     * {@code direction} 为 -1 表示向前（上一个模式）、+1 表示向后（下一个模式），
     * 参照红石块权杖的滚轮充能处理。服务端持久化并回传新的模式。
     * 离开抓取/护盾模式时销毁对应的常驻指令文本 Display。
     */
    private static void handleCommandStaffModeToggle(Player player, int direction) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        int dir = direction < 0 ? -1 : 1;
        var data = serverPlayer.getPersistentData();
        int mode = getCommandStaffMode(data);
        // 离开常驻文本 Display 模式时，先销毁对应头顶文本
        if (mode == COMMAND_STAFF_MODE_GRAB) {
            endGrabTextDisplay(serverPlayer.getUUID());
        } else if (mode == COMMAND_STAFF_MODE_SHIELD) {
            endShieldTextDisplay(serverPlayer.getUUID());
        }
        int next = Math.floorMod(mode + dir, COMMAND_STAFF_MODE_COUNT);
        data.putInt(COMMAND_STAFF_MODE_TAG, next);
        PacketDistributor.sendToPlayer(serverPlayer,
            new cn.autoforged.joes_addons_for_abmc.network.CommandStaffModePayload(next));
    }

    /**
     * 命令方块权杖左键：根据当前能力模式处理被瞄准的生物，并在玩家头上渲染对应指令文本 Display。
     */
    private static void handleCommandStaffTarget(Player player,
                                                 cn.autoforged.joes_addons_for_abmc.network.CommandStaffTargetPayload payload) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        java.util.UUID targetUuid;
        try {
            targetUuid = java.util.UUID.fromString(payload.targetUuid());
        } catch (IllegalArgumentException e) {
            return;
        }
        net.minecraft.world.entity.Entity target = null;
        for (net.minecraft.server.level.ServerLevel lv : serverLevel.getServer().getAllLevels()) {
            target = lv.getEntities().get(targetUuid);
            if (target != null) break;
        }
        if (target == null) return;

        // 击杀模式：执行 /kill，并渲染对应指令文本
        if (payload.mode() == COMMAND_STAFF_MODE_KILL) {
            // 先渲染玩家到被击杀目标之间的 END_ROD 粒子连线，再执行击杀
            spawnCommandAffectedParticles(serverLevel, player, java.util.List.of(target));
            String cmd = "kill " + targetUuid;
            List<String> texts = java.util.List.of(String.format("/kill %s", targetUuid));
            serverLevel.getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withSuppressedOutput(), cmd);
            showCommandTexts(serverLevel, player, texts);
            return;
        }

        // 启用/禁用AI模式：读取实体 NoAI 并取反，渲染 “/data modify entity (UUID) NoAI set value 0/1”
        if (payload.mode() == COMMAND_STAFF_MODE_TOGGLE_AI) {
            toggleCommandStaffNoAi(serverLevel, player, target, targetUuid);
            return;
        }

        // 抓取模式：距离超过 30 格时不生效
        if (payload.mode() != COMMAND_STAFF_MODE_GRAB) return;
        if (!player.blockPosition().closerThan(target.blockPosition(), 30.0)) return;

        java.util.UUID pUuid = serverPlayer.getUUID();
        java.util.UUID cur = COMMAND_STAFF_GRAB_TARGETS.get(pUuid);
        String grabCmd = String.format("execute as @p at @p run tp %s ^ ^1 ^6", targetUuid);
        if (targetUuid.equals(cur)) {
            // 再次左键同一生物：停止抓取
            COMMAND_STAFF_GRAB_TARGETS.remove(pUuid);
            endGrabTextDisplay(pUuid);
            return;
        }
        COMMAND_STAFF_GRAB_TARGETS.put(pUuid, targetUuid);
        // 切换抓取目标时，先销毁旧的常驻文本 Display
        endGrabTextDisplay(pUuid);
        // 渲染对应指令文本 Display：常驻于玩家头顶，直到玩家放下该实体
        net.minecraft.world.entity.Display.TextDisplay td =
            spawnGrabTextDisplay(serverLevel, player, grabCmd);
        if (td != null) {
            COMMAND_STAFF_GRAB_DISPLAYS.put(pUuid, td);
        }
    }

    /** 在玩家头顶生成一个常驻的抓取指令 Text Display（不排队 20 刻销毁）。 */
    private static net.minecraft.world.entity.Display.TextDisplay spawnGrabTextDisplay(
            ServerLevel level, Player player, String text) {
        return spawnCommandTextDisplay(level, player, text, player.getEyeHeight() + 20.0 / 16.0);
    }

    /** 销毁玩家头顶的常驻抓取指令 Text Display（若存在）。 */
    private static void endGrabTextDisplay(java.util.UUID pUuid) {
        net.minecraft.world.entity.Display.TextDisplay td = COMMAND_STAFF_GRAB_DISPLAYS.remove(pUuid);
        if (td != null) {
            discardCommandText(td);
        }
    }

    /** 命令方块权杖·启用/禁用AI：读取目标实体当前 NoAI 值并取反，然后在玩家头顶渲染对应指令文本。
     *  对 Mob 直接用 setNoAi（最可靠且会正确写入存档）；对其它实体通过 /data modify entity 指令切换。
     *  指令文本按用户要求的格式显示（不带 NBT 字节后缀 b）。 */
    private static void toggleCommandStaffNoAi(ServerLevel level, Player player,
            Entity target, java.util.UUID targetUuid) {
        boolean noAi;
        if (target instanceof Mob mob) {
            noAi = mob.isNoAi();
        } else {
            CompoundTag tag = target.saveWithoutId(new CompoundTag());
            noAi = tag.getBoolean("NoAI");
        }
        boolean newVal = !noAi;
        if (target instanceof Mob mob) {
            mob.setNoAi(newVal);
        } else {
            level.getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withSuppressedOutput(),
                String.format("data modify entity %s NoAI set value %db", targetUuid, newVal ? 1 : 0));
        }
        spawnCommandAffectedParticles(level, player, java.util.List.of(target));
        String text = String.format("/data modify entity %s NoAI set value %d", targetUuid, newVal ? 1 : 0);
        showCommandTexts(level, player, java.util.List.of(text));
    }

    /** 命令方块权杖·护盾模式：在玩家头顶生成/保持常驻的护盾指令 Text Display（不排队 20 刻销毁）。 */
    private static void ensureShieldTextDisplay(ServerLevel level, Player player) {
        java.util.UUID pid = player.getUUID();
        net.minecraft.world.entity.Display.TextDisplay td = COMMAND_STAFF_SHIELD_DISPLAYS.get(pid);
        if (td != null && td.isAlive() && td.level() == level) return;
        if (td != null) discardCommandText(td);
        td = spawnCommandTextDisplay(level, player, COMMAND_STAFF_SHIELD_TEXT,
            player.getEyeHeight() + 20.0 / 16.0);
        COMMAND_STAFF_SHIELD_DISPLAYS.put(pid, td);
    }

    /** 命令方块权杖·护盾模式：销毁玩家头顶的常驻护盾指令 Text Display（若存在）。 */
    private static void endShieldTextDisplay(java.util.UUID pUuid) {
        net.minecraft.world.entity.Display.TextDisplay td = COMMAND_STAFF_SHIELD_DISPLAYS.remove(pUuid);
        if (td != null) {
            discardCommandText(td);
        }
    }

    /** 判断玩家当前主手是否持有命令方块权杖（服务端用）。仅主手生效：副手中的命令方块权杖视为普通物品，不触发任何模式能力。 */
    private static boolean holdsCommandStaffServer(Player p) {
        ItemStack main = p.getMainHandItem();
        return main.getItem() instanceof StaffItem
            && "command_block".equals(main.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"));
    }

    /**
     * 命令方块权杖·护盾模式的每刻执行：对所有处于护盾模式并持有命令方块权杖的玩家，
     * 1) 在头顶维持常驻指令文本 Display；
     * 2) 将以玩家为中心、半径 7 格球体内朝玩家飞来的弹射物（玩家自己发射的除外）
     *    速度方向取反、大小不变，并把弹射物挪回球面边界处（在边界处被弹回，不穿入后才反弹）；
     *    弹射物离开半径前不重复弹反，离开后再进入才会再次弹反。
     * 该模式不依赖左键，持续生效直到切换成其它模式。
     */
    private static void runCommandStaffShieldTick(net.minecraft.server.MinecraftServer server) {
        java.util.Set<java.util.UUID> active = new java.util.HashSet<>();
        for (net.minecraft.server.level.ServerPlayer p : new java.util.ArrayList<>(server.getPlayerList().getPlayers())) {
            if (getCommandStaffMode(p.getPersistentData()) != COMMAND_STAFF_MODE_SHIELD) continue;
            if (!holdsCommandStaffServer(p)) continue;
            active.add(p.getUUID());
            ServerLevel level = p.serverLevel();
            ensureShieldTextDisplay(level, p);

            // 检测范围需覆盖球面外的单刻位移，确保弹射物在穿越球面的那一刻就被拦截（在球面处弹回）
            net.minecraft.world.phys.AABB box = p.getBoundingBox().inflate(
                COMMAND_STAFF_SHIELD_RADIUS + COMMAND_STAFF_SHIELD_DETECT_MARGIN);
            net.minecraft.world.phys.Vec3 center = p.position();
            for (Entity e : new java.util.ArrayList<>(level.getEntities(p, box,
                    ent -> ent instanceof Projectile && ent.isAlive()))) {
                Projectile proj = (Projectile) e;
                // 权杖持有者自己发射的弹射物不计入
                if (proj.getOwner() == p) continue;
                // 已插地/静止的弹射物速度已被原版清零，交由下方速度阈值过滤
                net.minecraft.world.phys.Vec3 vel = proj.getDeltaMovement();
                double speed = vel.length();
                if (speed < 1.0E-4) continue;
                net.minecraft.world.phys.Vec3 projPos = proj.position();
                net.minecraft.world.phys.Vec3 rel = projPos.subtract(center); // 球心(玩家)→弹射物
                double dist = rel.length();
                if (dist < 1.0E-4) continue; // 与玩家重合，无方向可言

                if (dist < COMMAND_STAFF_SHIELD_RADIUS) {
                    // 弹射物诞生在护盾球内：沿（球心→弹射物）连线向外施加径向斥力。
                    // 初速度的切向分量保持不变，与斥力导致的径向加速矢量叠加；斥力强度随距离衰减，到球面边界衰减为 0。
                    // 根因修复（近距离弹射物抽搐）：攻击者站得近时，弹射物会在球内停留很多刻。
                    // 若沿用“每刻施加一次小斥力加速度”，就会每刻都 setDeltaMovement + hasImpulse 强制广播
                    // 一个新速度，而弹射物在客户端有运动/朝向预测，服务端反复改速度会与客户端预测反复打架，
                    // 肉眼表现为抽搐抖动（尤其弹射物掠过/穿过玩家中心时径向方向急剧翻转，抖动被放大）。
                    // 因此改为「一次性决定性修正」：把径向速度分量直接翻转为向外，且外向速度 = 当前内向
                    // 径向速度 + 随距离衰减的斥力强度，确保修正后 rel·vel > 0（已在向外飞行）。
                    // 后续刻不会再被本逻辑改速，客户端预测不再被反复打断，抽搐消失。
                    if (rel.dot(vel) > 0.0) continue; // 已在向外飞行，无需修正
                    Vec3 radialOut = rel.scale(1.0 / dist); // 球心→弹射物的单位方向（向外）
                    double vIn = -rel.dot(vel) / dist;       // 当前内向径向速度（>0 表示正朝球心靠近）
                    double strength = COMMAND_STAFF_SHIELD_REPULSE_ACCEL
                        * (1.0 - dist / COMMAND_STAFF_SHIELD_RADIUS);
                    if (strength < 0.0) strength = 0.0;
                    double vOut = vIn + strength;            // 修正后的外向径向速度（> vIn，保证确实朝外）
                    // 新速度 = 原切向分量 + 外向径向分量 = vel + (vIn + vOut)·radialOut
                    proj.setDeltaMovement(vel.add(radialOut.scale(vIn + vOut)));
                    // 必须置 hasImpulse=true：弹射物在客户端有运动预测，服务端改速度后若不强制广播
                    // 运动包，客户端仍按旧速度预测，会与服务端位置校正产生橡皮筋式抽搐。
                    proj.hasImpulse = true;
                    continue;
                }

                // 球外：仅当弹射物确实朝球心方向飞来（径向速度分量指向球心）时才做镜面反射。
                // 该条件排除「刚被斥力推出、正在远离、或沿球面切线掠过」的弹射物，
                // 避免其在球面边界处被反复拦截、反复 setPos 拽回而表现成抽搐。
                if (rel.dot(vel) >= 0.0) continue;
                // 解 |rel + t·vel|² = R² 求本刻内轨迹与球面的交点（进入球面的较小正根 tEnter）。
                // 在接触点处以球面法线为镜面做镜面反射，使弹射物在刚接触球面时就被弹回，不再穿入。
                double a = vel.lengthSqr();
                double b = 2.0 * rel.dot(vel);
                double c = dist * dist - COMMAND_STAFF_SHIELD_RADIUS * COMMAND_STAFF_SHIELD_RADIUS;
                double disc = b * b - 4.0 * a * c;
                if (disc < 0.0) continue; // 轨迹不经过球面
                double tEnter = (-b - Math.sqrt(disc)) / (2.0 * a);
                if (tEnter < 0.0 || tEnter > 1.0) continue; // 正在飞离，或下一刻才触及球面
                net.minecraft.world.phys.Vec3 hit = projPos.add(vel.scale(tEnter));
                net.minecraft.world.phys.Vec3 normal = hit.subtract(center).normalize();
                net.minecraft.world.phys.Vec3 reflected = vel.subtract(normal.scale(2.0 * vel.dot(normal)));
                proj.setDeltaMovement(reflected);
                // 强制广播运动包，避免客户端仍按旧速度预测导致反弹位置来回抽搐
                proj.hasImpulse = true;
                // 将弹射物精确放回球面接触点（略微外推），视觉上即在球面处被弹回
                proj.setPos(hit.x + normal.x * 0.05, hit.y + normal.y * 0.05, hit.z + normal.z * 0.05);
                // 反弹视觉反馈
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    hit.x, hit.y, hit.z,
                    3, 0.1, 0.1, 0.1, 0.05);
            }

            // 护盾额外弹开：点燃的 TNT（PrimedTnt，含 TNT 权杖丢出的特制 TNT）与点燃的苦力怕
            //（Creeper，含 TNT 权杖丢出的特制苦力怕）——沿 球心→实体 方向施加外向速度弹开。
            for (Entity e : new java.util.ArrayList<>(level.getEntities(p, box,
                    ent -> ent.isAlive()
                        && (ent instanceof net.minecraft.world.entity.item.PrimedTnt
                            || ent instanceof net.minecraft.world.entity.monster.Creeper)))) {
                net.minecraft.world.phys.Vec3 pos = e.position();
                net.minecraft.world.phys.Vec3 rel = pos.subtract(center);
                double dist = rel.length();
                if (dist < 1.0E-4) continue;
                double speed = e.getDeltaMovement().length();
                if (speed < 1.0E-4) continue;
                Vec3 radialOut = rel.scale(1.0 / dist);
                e.setDeltaMovement(radialOut.scale(Math.max(0.6, speed + 0.3)));
                e.hasImpulse = true;
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    pos.x, pos.y, pos.z, 3, 0.1, 0.1, 0.1, 0.05);
            }
        }

        // 清理离开护盾模式/下线的常驻文本 Display
        java.util.Iterator<java.util.Map.Entry<java.util.UUID, net.minecraft.world.entity.Display.TextDisplay>> it =
            COMMAND_STAFF_SHIELD_DISPLAYS.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<java.util.UUID, net.minecraft.world.entity.Display.TextDisplay> en = it.next();
            if (!active.contains(en.getKey())) {
                net.minecraft.world.entity.Display.TextDisplay td = en.getValue();
                if (td != null) discardCommandText(td);
                it.remove();
            }
        }
    }

    /**
     * 命令方块权杖抓取模式的每刻执行：对所有正在抓取的玩家，对被选中目标执行
     * “execute as @p at @p run tp (目标) ^ ^1 ^6”（朝玩家前方偏上 1 格、前方 6 格拉拽）。
     */
    private static void runCommandStaffGrabTick(net.minecraft.server.MinecraftServer server) {
        if (COMMAND_STAFF_GRAB_TARGETS.isEmpty()) return;
        for (net.minecraft.server.level.ServerPlayer p : new java.util.ArrayList<>(server.getPlayerList().getPlayers())) {
            java.util.UUID pUuid = p.getUUID();
            java.util.UUID targetUuid = COMMAND_STAFF_GRAB_TARGETS.get(pUuid);
            if (targetUuid == null) continue;
            net.minecraft.world.entity.Entity target = null;
            for (net.minecraft.server.level.ServerLevel lv : server.getAllLevels()) {
                target = lv.getEntities().get(targetUuid);
                if (target != null) break;
            }
            if (target == null) {
                // 目标已不存在：自动停止抓取
                COMMAND_STAFF_GRAB_TARGETS.remove(pUuid);
                endGrabTextDisplay(pUuid);
                continue;
            }
            if (!(p.level() instanceof ServerLevel playerLevel)) continue;
            // 抓取模式每刻都会对目标实体执行拉拽指令，因此每刻渲染玩家到目标之间的连线
            spawnCommandAffectedParticles(playerLevel, p, java.util.List.of(target));
            String cmd = "execute as @p at @p run tp " + targetUuid + " ^ ^1 ^6";
            playerLevel.getServer().getCommands().performPrefixedCommand(
                p.createCommandSourceStack(), cmd);
        }
    }

    /** Herobrine 权杖是否处于远程模式（false=近战模式）。 */
    public static boolean isHerobrineRanged(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return serverPlayer.getPersistentData().getBoolean(HEROBRINE_STAFF_RANGED_TAG);
        }
        return false;
    }

    /**
     * 附魔台权杖：每 4 刻调用一次。对玩家瞄准的生物（自身除外）若其主手持有物品，
     * 则为其附上一个随机附魔并播放附魔台音效。
     */
    public static void executeEnchantStaffTick(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ServerLevel level = serverPlayer.serverLevel();
        boolean crazy = serverPlayer.getPersistentData().getBoolean(ENCHANT_STAFF_CRAZY_TAG);

        // 定位权杖本体（主手或副手），用于按本次施加的附魔等级/状态效果等级累计损耗耐久
        ItemStack mainHand = serverPlayer.getMainHandItem();
        boolean mainIsStaff = mainHand.getItem() instanceof StaffItem;
        ItemStack staffStack = mainIsStaff ? mainHand : serverPlayer.getOffhandItem();
        EquipmentSlot staffSlot = mainIsStaff ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        boolean hasEnchantStaff = staffStack.getItem() instanceof StaffItem;
        int totalCost = 0; // 本次实际施加的所有附魔（装备附魔+状态效果）等级之和

        // 副手附魔书：若持有，则用书中魔咒替代随机附魔（日常模式=书中等级，疯狂模式=99级）
        ItemEnchantments bookEnchants = serverPlayer.getOffhandItem()
            .getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        Holder<Enchantment> bookEnchant = null;
        int bookLevel = 0;
        for (Holder<Enchantment> holder : bookEnchants.keySet()) {
            bookEnchant = holder;
            bookLevel = bookEnchants.getLevel(holder);
            break;
        }

        // 射线检测瞄准的生物（排除自己）
        Vec3 eye = serverPlayer.getEyePosition();
        Vec3 look = serverPlayer.getLookAngle();
        double range = 20.0;
        AABB searchBox = serverPlayer.getBoundingBox()
            .expandTowards(look.scale(range))
            .inflate(1.0);
        LivingEntity target = null;
        double bestDist = range * range;
        // 末影龙为多部件实体，射线打中的是 EnderDragonPart（非 LivingEntity），
        // 这里将其解析为父实体 EnderDragon，使附魔台权杖能给末影龙施加状态效果。
        for (Entity e : level.getEntities(serverPlayer, searchBox,
            e -> (e instanceof LivingEntity || e instanceof EnderDragonPart) && e != serverPlayer)) {
            LivingEntity resolved = e instanceof EnderDragonPart part ? part.getParent() : (LivingEntity) e;
            AABB bb = e.getBoundingBox().inflate(0.3);
            java.util.Optional<Vec3> hit = bb.clip(eye, eye.add(look.scale(range)));
            if (hit.isPresent()) {
                double d = eye.distanceToSqr(hit.get());
                if (d < bestDist) {
                    bestDist = d;
                    target = resolved;
                }
            }
        }
        if (target == null) return;

        // 生物空手 -> 给生物自身“附魔”（渲染附魔光效并附上一种随机魔咒状态效果）：设置持久标记并广播，
        // 让附魔光效在生物身上持续显示，而非只在瞄准期间。空手时不附魔装备栏物品。
        if (target.getMainHandItem().isEmpty()) {
            if (!getEnchantSelf(target)) {
                setEnchantSelf(target, true);
                broadcastEnchantSelf(target);
            }
            // 被“附魔”的生物获得魔咒状态效果：副手持附魔书时施加书中对应魔咒（日常=书中等级，疯狂=99级），
            // 否则随机 1~3 / 1~99 级；疯狂模式且无附魔书时不再限于一种，而是随机叠加多种（5~99级随机）。
            totalCost += applyEnchantmentStatusEffect(target, crazy, bookEnchant, bookLevel);
            // 若获得的是“忠诚”状态效果，记录给予它的权杖使用者，供忠诚传送定位。
            if (hasEnchantEffect(target, Enchantments.LOYALTY)) {
                target.getPersistentData().putUUID(ENCHANT_STAFF_GRANTER_TAG, serverPlayer.getUUID());
            } else {
                target.getPersistentData().remove(ENCHANT_STAFF_GRANTER_TAG);
            }
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (hasEnchantStaff && totalCost > 0) hurtStaff(staffStack, totalCost, serverPlayer, staffSlot);
            return;
        }

        // 副手附魔书时：仅用书中魔咒附魔装备（日常模式=书中等级，疯狂模式=99级），不再随机。
        if (bookEnchant != null) {
            int enchantLevel = crazy ? 99 : Math.max(bookLevel, 1);
            int bookCost = 0;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack invItem = target.getItemBySlot(slot);
                if (invItem.isEmpty()) continue;
                // 权杖本体视为适用于一切魔咒；日常模式仅限适用于该物品的魔咒
                boolean isStaff = invItem.getItem() == ModItems.STAFF.get();
                if (!crazy && !bookEnchant.value().isSupportedItem(invItem) && !isStaff) continue;
                invItem.enchant(bookEnchant, enchantLevel);
                bookCost += enchantLevel;
            }
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (hasEnchantStaff && bookCost > 0) hurtStaff(staffStack, bookCost, serverPlayer, staffSlot);
            return;
        }

        Registry<Enchantment> reg = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        // 收集全部附魔候选（排除消失诅咒，避免宿主死亡时物品不掉落）。
        java.util.List<Holder.Reference<Enchantment>> allEnchants = new java.util.ArrayList<>();
        for (Holder.Reference<Enchantment> holder : reg.holders().toList()) {
            if (holder.is(Enchantments.VANISHING_CURSE)) continue;
            allEnchants.add(holder);
        }
        if (allEnchants.isEmpty()) return;

        // 对生物所有装备栏（主手/副手/四件护甲）中的物品逐一尝试附魔。
        // 需求 3：日常模式下每件物品追加至多 3 个随机附魔（相同魔咒跳过），等级不超过生存模式允许的最大等级；
        // 需求 6：疯狂模式下附魔等级恒为 99。
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack invItem = target.getItemBySlot(slot);
            if (invItem.isEmpty()) continue;

            // 权杖本体视为适用于一切魔咒，日常模式下也可附上任意附魔
            boolean isStaff = invItem.getItem() == ModItems.STAFF.get();

            // 日常模式仅限适用于该物品的原版附魔；疯狂模式为任意附魔（含不可附魔的物品/方块，如权杖）。
            java.util.List<Holder.Reference<Enchantment>> candidates = new java.util.ArrayList<>();
            for (Holder.Reference<Enchantment> holder : allEnchants) {
                if (!crazy && !holder.value().isSupportedItem(invItem) && !isStaff) continue;
                candidates.add(holder);
            }
            if (candidates.isEmpty()) continue;

            // 已经附在物品上的魔咒键，用于跳过重复
            java.util.Set<net.minecraft.core.Holder<Enchantment>> existing =
                new java.util.HashSet<>(invItem.getEnchantments().keySet());

            // 每件物品最多追加 3 个随机附魔（相同魔咒跳过，刷满即停）
            int added = 0;
            java.util.List<Holder.Reference<Enchantment>> pool = new java.util.ArrayList<>(candidates);
            while (added < 3 && !pool.isEmpty()) {
                Holder<Enchantment> chosen = pool.get(level.random.nextInt(pool.size()));
                pool.remove(chosen); // 同一件物品不再重复选同一魔咒
                if (existing.contains(chosen)) continue;
                existing.add(chosen);
                int enchantLevel;
                if (crazy) {
                    enchantLevel = 99; // 疯狂模式附魔等级恒为 99（创造模式）
                } else {
                    // 日常模式：等级不超过生存模式允许的最大等级（getMaxLevel 即生存上限）
                    enchantLevel = Mth.randomBetweenInclusive(level.random,
                        chosen.value().getMinLevel(), chosen.value().getMaxLevel());
                }
                invItem.enchant(chosen, enchantLevel);
                totalCost += enchantLevel;
                added++;
            }
        }

        level.playSound(null, target.getX(), target.getY(), target.getZ(),
            SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (hasEnchantStaff && totalCost > 0) hurtStaff(staffStack, totalCost, serverPlayer, staffSlot);
    }

    /**
     * 给被“自体附魔”的生物附魔咒状态效果：
     * <ul>
     *   <li>副手持附魔书时：施加书中对应魔咒（日常=书中等级，疯狂=99级）；</li>
     *   <li>副手无附魔书且非疯狂模式：随机 1~3 级，每只生物仅保留一种（已拥有则保持不变）；</li>
     *   <li>副手无附魔书且疯狂模式：随机叠加多种效果（等级 5~99），不再限于一种；</li>
     *   <li>不渲染药水粒子（仅保留图标）。</li>
     * </ul>
     * 随机候选池 = 原版附魔名占位效果 + 击退（击退单独注册于 {@link ModMobEffects#KNOCKBACK}，避免重复注册）。
     */
    private static void applyEnchantmentStatusEffect(LivingEntity target, boolean crazy) {
        applyEnchantmentStatusEffect(target, crazy, null, 0);
    }

    private static int applyEnchantmentStatusEffect(LivingEntity target, boolean crazy,
                                                     Holder<Enchantment> bookEnchant, int bookLevel) {
        int totalCost = 0; // 本次实际施加的所有附魔状态效果等级之和（用作权杖耐久损耗）
        // ===== 疯狂模式（仅创造模式 / 已解锁可用）：附魔等级恒为 99 =====
        if (crazy) {
            if (bookEnchant == null) {
                // 无附魔书：随机叠加多种附魔状态效果（等级 99）
                int count = 1 + target.level().random.nextInt(3); // 随机 1~3 种
                for (int i = 0; i < count; i++) {
                    java.util.List<net.neoforged.neoforge.registries.DeferredHolder<MobEffect, MobEffect>> candidates =
                        new java.util.ArrayList<>(ModMobEffects.ENCHANTMENT_EFFECTS);
                    candidates.add(ModMobEffects.KNOCKBACK);
                    if (candidates.isEmpty()) return totalCost;
                    Holder<MobEffect> effect = candidates.get(target.level().random.nextInt(candidates.size()));
                    int level = 99; // 疯狂模式附魔等级恒为 99
                    totalCost += level;
                    applyEnchantEffectInstance(target,
                        new MobEffectInstance(effect, level * 18 * 20, level - 1, false, false, true));
                }
            } else {
                // 附魔书（疯狂模式）：映射为对应魔咒状态效果，等级恒为 99
                ResourceLocation effectId = ResourceLocation.fromNamespaceAndPath(
                    ModMain.MODID, ResourceLocation.parse(bookEnchant.getRegisteredName()).getPath());
                java.util.Optional<Holder.Reference<MobEffect>> effectOpt =
                    BuiltInRegistries.MOB_EFFECT.getHolder(effectId);
                if (effectOpt.isEmpty()) return totalCost;
                int level = 99;
                totalCost += level;
                applyEnchantEffectInstance(target,
                    new MobEffectInstance(effectOpt.get(), level * 18 * 20, level - 1, false, false, true));
            }
            return totalCost;
        }

        // ===== 日常模式 =====
        if (bookEnchant != null) {
            // 附魔书：施加书中对应魔咒（等级=书中等级）；若已拥有相同效果则不重复/不刷新
            ResourceLocation effectId = ResourceLocation.fromNamespaceAndPath(
                ModMain.MODID, ResourceLocation.parse(bookEnchant.getRegisteredName()).getPath());
            java.util.Optional<Holder.Reference<MobEffect>> effectOpt =
                BuiltInRegistries.MOB_EFFECT.getHolder(effectId);
            if (effectOpt.isEmpty()) return totalCost; // 无对应占位效果则不做处理
            Holder<MobEffect> effect = effectOpt.get();
            if (target.hasEffect(effect)) return totalCost; // 已拥有相同附魔状态效果则跳过
            int amplifier = Math.max(bookLevel - 1, 0);
            totalCost += bookLevel;
            applyEnchantEffectInstance(target,
                new MobEffectInstance(effect, Integer.MAX_VALUE, amplifier, false, false, true));
            return totalCost;
        }

        // 无附魔书：追加至多 3 个 1~5 级随机附魔状态效果，若刷出相同效果（含生物已拥有的）则跳过。
        // 例：刷出「锋利III 经验修补I 锋利V 多重射击II 保护IV」→ 最终拥有「锋利III 经验修补 多重射击II」。
        java.util.Set<Holder<MobEffect>> existing = new java.util.HashSet<>();
        for (MobEffectInstance active : target.getActiveEffects()) {
            boolean isEnchantEffect = ModMobEffects.ENCHANTMENT_EFFECTS.stream().anyMatch(active::is)
                || active.is(ModMobEffects.KNOCKBACK);
            if (isEnchantEffect) existing.add(active.getEffect());
        }
        java.util.List<Holder<MobEffect>> chosen = new java.util.ArrayList<>();
        int attempts = 5; // 最多尝试刷取 5 次（刷出重复则跳过，直到凑满 3 个或尝试完）
        for (int i = 0; i < attempts && chosen.size() < 3; i++) {
            java.util.List<net.neoforged.neoforge.registries.DeferredHolder<MobEffect, MobEffect>> candidates =
                new java.util.ArrayList<>(ModMobEffects.ENCHANTMENT_EFFECTS);
            candidates.add(ModMobEffects.KNOCKBACK);
            if (candidates.isEmpty()) break;
            Holder<MobEffect> effect = candidates.get(target.level().random.nextInt(candidates.size()));
            if (existing.contains(effect) || chosen.contains(effect)) continue; // 相同效果跳过
            chosen.add(effect);
        }
        for (Holder<MobEffect> effect : chosen) {
            int level = 1 + target.level().random.nextInt(5); // 等级 1~5
            totalCost += level;
            applyEnchantEffectInstance(target,
                new MobEffectInstance(effect, Integer.MAX_VALUE, level - 1, false, false, true));
        }
        return totalCost;
    }

    /**
     * 为生物施加附魔状态效果。凋灵（WitherBoss）与末影龙（EnderDragon）硬编码覆写 addEffect 直接返回
     * false 免疫一切效果，这里对两者改用 forceAddEffect 强行注入，其它生物走正常 addEffect。
     */
    private static void applyEnchantEffectInstance(LivingEntity target, MobEffectInstance inst) {
        if (target instanceof WitherBoss || target instanceof EnderDragon) {
            target.forceAddEffect(inst, null);
        } else {
            target.addEffect(inst);
        }
    }

    // ==================== Omega 权杖·吸收模式 ====================

    /** Omega 吸收最大距离（格）。 */
    private static final double OMEGA_ABSORB_RANGE = 10.0;
    /** 两碰撞箱最近距离 <= 该值（格）视为“接触”，横向/纵向均可触发。 */
    private static final double OMEGA_ABSORB_CONTACT_DIST = 0.1;
    private static final double OMEGA_ABSORB_CONTACT_DIST_SQ =
        OMEGA_ABSORB_CONTACT_DIST * OMEGA_ABSORB_CONTACT_DIST;
    /** 接触玩家后等待删除的时长（游戏刻，0.5 秒）。 */
    private static final long OMEGA_ABSORB_CONTACT_DELETE_TICKS = 10;
    /** 每 5 刻向客户端发送一次 debug_string 视觉。 */
    private static final int OMEGA_ABSORB_VISUAL_INTERVAL = 5;

    /**
     * Omega 权杖·吸收模式每刻逻辑（服务端调用）：
     * 持续瞄准一个生物则不断把它拉近玩家，期间禁用生物 AI 并无重力；两碰撞箱接触后停止拉动，
     * 锁定生物静止 0.5 秒后直接删除；每 5 刻以生物为球心、半径 3 格内采样 2~3 个端点，
     * 发送 debug_string 视觉让各端点汇聚到玩家。若当前未瞄准任何生物或把视野移开，则取消流程并恢复该生物的重力/AI。
     */
    public static void executeOmegaAbsorbTick(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        ItemStack stack = sp.getMainHandItem();
        if (!(stack.getItem() instanceof cn.autoforged.joes_addons_for_abmc.item.StaffItem)) return;
        if (!"omega".equals(stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"))) return;

        ServerLevel level = sp.serverLevel();
        LivingEntity target = raycastAbsorbTarget(sp, level);
        UUID pid = sp.getUUID();
        OmegaAbsorbState st = OMEGA_ABSORB_STATES.get(pid);

        // 没瞄到生物（或视角移开）→ 取消当前流程，恢复生物状态
        if (target == null) {
            clearOmegaAbsorb(pid);
            return;
        }
        // 换目标或目标已失效 → 结束旧流程并建立新流程
        if (st == null || st.target != target || !st.target.isAlive()) {
            clearOmegaAbsorb(pid);
            st = new OmegaAbsorbState(target);
            OMEGA_ABSORB_STATES.put(pid, st);
            // 立即禁用 AI 并关闭重力：飞行/主动生物（凋灵、末影龙等）的 AI 会不断与拉动对抗，
            // 只有一开始就禁用 AI 才能让它们被稳定拉向玩家。
            st.target.setNoGravity(true);
            setOmegaNoAi(st.target, true);
        }

        // 接触判定：两碰撞箱最近距离很小即视为接触（横向/纵向/斜向均可触发），
        // 不再要求完全相交（原 intersects 只在生物被拉到玩家正下方时才满足）。
        boolean contact = aabbDistanceSqr(sp.getBoundingBox(), st.target.getBoundingBox())
            <= OMEGA_ABSORB_CONTACT_DIST_SQ;
        if (contact) {
            if (st.deleteTick < 0) {
                st.deleteTick = level.getGameTime() + OMEGA_ABSORB_CONTACT_DELETE_TICKS;
                st.contactPos = st.target.position();
            }
            // 按首次接触时的位置锁定生物，避免物理把两者推开导致接触中断、删除计时被反复重置
            if (st.contactPos != null) {
                st.target.setPos(st.contactPos.x, st.contactPos.y, st.contactPos.z);
            }
            st.target.setNoGravity(true);
            st.target.setDeltaMovement(0, 0, 0);
            if (level.getGameTime() >= st.deleteTick) {
                OMEGA_ABSORB_STATES.remove(pid);
                st.target.discard();
                return;
            }
            return; // 已接触，保持静止等待删除，不再拉动
        }
        // 未接触：重置删除计时，继续正常拉动
        st.deleteTick = -1;
        st.contactPos = null;

        // 拉近：按生命上限决定速度（高生命→慢、低生命→快），每刻带轻微随机波动
        double speed = omegaAbsorbSpeed(st.target);
        Vec3 toPlayer = sp.position().subtract(st.target.position());
        double len = toPlayer.length();
        if (len > 1.0E-4) {
            double step = speed / 20.0;
            Vec3 dir = toPlayer.scale(1.0 / len);
            // 使用 teleportTo 而非 setPos：末影龙等特殊实体的 aiStep() 会覆盖 setPos，
            // teleportTo 更强制，且能正确处理多部件实体（末影龙部件同步移动）。
            st.target.teleportTo(
                st.target.getX() + dir.x * step,
                st.target.getY() + dir.y * step,
                st.target.getZ() + dir.z * step);
        }
        st.target.setNoGravity(true);
        st.target.setDeltaMovement(0, 0, 0);

        // 每 5 刻触发一次 debug_string 视觉：以生物为球心、半径 3 格内采样端点，汇聚到玩家
        if (level.getGameTime() % OMEGA_ABSORB_VISUAL_INTERVAL == 0) {
            double[][] pts = sampleOmegaAbsorbSphere(st.target, level.random);
            PacketDistributor.sendToPlayer(sp,
                new cn.autoforged.joes_addons_for_abmc.network.DebugStringPayload(pts));
        }
    }

    /** 松开右键时调用：取消吸收流程、恢复生物重力/AI。冷却由 StaffItem 侧设置。 */
    public static void releaseOmegaAbsorb(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        clearOmegaAbsorb(sp.getUUID());
    }

    /** 取消玩家当前的吸收流程，并把被吸收生物的重力/AI 恢复为原值。 */
    private static void clearOmegaAbsorb(UUID playerId) {
        OmegaAbsorbState st = OMEGA_ABSORB_STATES.remove(playerId);
        if (st != null && st.target.isAlive()) {
            st.target.setNoGravity(st.origNoGravity);
            setOmegaNoAi(st.target, st.origNoAi);
        }
    }

    /** 仅对 Mob 设置 NoAI 标记（LivingEntity 本身无此方法）。 */
    private static void setOmegaNoAi(LivingEntity entity, boolean value) {
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            mob.setNoAi(value);
        }
    }

    // ==================== 红石块权杖：红石射线 ====================

    /** 红石射线单段直线的最大射程（格）。 */
    private static final double REDSTONE_RAY_RANGE = 128.0;
    /** 红石射线最多反射/偏转次数（防止两面相对的紫水晶镜面无限反射）。 */
    private static final int REDSTONE_RAY_MAX_BOUNCES = 12;

    /** 玩家开始发射红石射线：重置会话刻计数并清点残留。 */
    public static void startRedstoneStaff(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        RedstoneStaffState st = REDSTONE_STAFF_STATES.computeIfAbsent(sp.getUUID(), k -> new RedstoneStaffState());
        st.owner = sp.getUUID();
        // 恢复持久化的充能数（退出存档重进后保留）
        if (sp.getPersistentData().contains(REDSTONE_STAFF_CHARGE_TAG)) {
            st.charge = Math.max(1, Math.min(8,
                sp.getPersistentData().getInt(REDSTONE_STAFF_CHARGE_TAG)));
        }
        st.tick = 0;
        st.breakTimer = 0;
        ServerLevel level = sp.serverLevel();
        restoreRedstonePowered(level, st);
        clearStaffPistonTracking(st.owner);
        st.poweredBlocks.clear();
        st.powerApplied = false;
        st.lastTarget = null;
    }

    /** 玩家松开右键：停止发射并恢复此前被强充能的方块（保留充能强度，供下次发射继续使用）。 */
    public static void releaseRedstoneStaff(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        RedstoneStaffState st = REDSTONE_STAFF_STATES.get(sp.getUUID());
        if (st != null) {
            restoreRedstonePowered(sp.serverLevel(), st);
            clearStaffPistonTracking(st.owner);
            st.poweredBlocks.clear();
            st.powerApplied = false;
            st.lastTarget = null;
            st.tick = 0;
        }
    }

    /** 客户端滚轮调整充能强度（1~8）。同时持久化到玩家数据，退出存档重进后保留。 */
    private static void handleRedstoneStaffCharge(Player player, int charge) {
        if (!(player instanceof ServerPlayer sp)) return;
        int c = Math.max(1, Math.min(8, charge));
        sp.getPersistentData().putInt(REDSTONE_STAFF_CHARGE_TAG, c);
        REDSTONE_STAFF_STATES.computeIfAbsent(sp.getUUID(), k -> new RedstoneStaffState()).charge = c;
    }

    /** 红石块权杖每刻逻辑（服务端）：沿视线发射红石射线并处理伤害/强充能/破坏/粒子。 */
    public static void executeRedstoneStaffTick(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        ServerLevel level = sp.serverLevel();
        RedstoneStaffState st = REDSTONE_STAFF_STATES.computeIfAbsent(sp.getUUID(), k -> new RedstoneStaffState());
        st.owner = sp.getUUID();
        st.tick++;

        // 红石块权杖耐久消耗：长按时每 (9-充能数) 游戏刻消耗 1 点耐久（充能 1~8 对应 8~1 刻/点）。
        int costTicks = 9 - st.charge;
        if (costTicks < 1) costTicks = 1;
        if (st.tick % costTicks == 0) {
            ItemStack main = sp.getMainHandItem();
            boolean mainIsStaff = main.getItem() instanceof StaffItem;
            ItemStack staffStack = mainIsStaff ? main : sp.getOffhandItem();
            EquipmentSlot slot = mainIsStaff ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            hurtStaff(staffStack, 1, sp, slot);
        }

        // 沿视线逐段反射地追踪射线（返回折线端点、最终命中目标与撞击后剩余充能）
        // 注意：r.charge 是“这条激光”当前的实际充能（命中紫水晶簇时会衰减），
        // 而 st.charge 是权杖的基础充能（滚轮设置）。二者分开，激光衰减绝不修改权杖充能。
        RedstoneRayResult r = computeRedstoneRay(level, sp, applyLineEmitterOffset(sp, sp.getEyePosition()), sp.getLookAngle(), st.charge);
        int laserCharge = r.charge;
        // 充能归零 -> 射线直接消失（不渲染、不强充、不伤害），等待玩家滚轮重新充能
        if (laserCharge <= 0) {
            if (st.powerApplied) {
                restoreRedstonePowered(level, st);
                st.poweredBlocks.clear();
                st.powerApplied = false;
                st.lastTarget = null;
            }
            return;
        }

        // 以红石粒子渲染整条折线射线（从发射者眼位起完整渲染，各视角一致）
        spawnRedstoneBeamParticles(level, sp, r.points);

        // 命中掉落物：每 10~30 刻按“方块破坏概率的两倍”尝试摧毁（无掉落）。
        // 掉落物不阻挡激光，因此不影响下方对生物/方块的命中处理。
        if (!r.itemTargets.isEmpty()) {
            if (st.itemBreakTimer > 0) {
                st.itemBreakTimer--;
            } else {
                st.itemBreakTimer = 20 + level.random.nextInt(21) - 10; // 10~30 刻
                double chance = (laserCharge - 4) * 0.05 * 2; // 方块破坏概率的两倍
                if (chance > 0) {
                    for (ItemEntity itemEnt : r.itemTargets) {
                        if (itemEnt.isAlive() && level.random.nextDouble() < chance) {
                            // 若该掉落物是变形生物变来的物品：激光摧毁时同样判定为该生物死亡
                            TransmutationData tData = ITEM_TRANSMUTATIONS.remove(itemEnt.getUUID());
                            if (tData != null) {
                                ITEM_TRANSMUTATION_POSITIONS.remove(itemEnt.getUUID());
                                BlockPos diePos = itemEnt.blockPosition();
                                if (tData.playerUuid() != null) {
                                    killTransmutedPlayer(level, tData, diePos, false);
                                } else {
                                    handleTransmutationKillCredit(level, tData, diePos, null);
                                }
                            }
                            itemEnt.discard();
                        }
                    }
                }
            }
        }

        // 命中生物：单格范围（1 格半径）AOE 伤害，每刻对范围内所有生物造成（激光当前充能数）点伤害，
        // 并清空无敌帧使其连续受击。
        if (r.damageTarget != null) {
            Vec3 hitPos = r.points.get(r.points.size() - 1);
            AABB aoeBox = new AABB(hitPos, hitPos).inflate(1.0);
            // 用带直接攻击者（玩家）的 laser 自定义伤害：既保证末影龙的 hurt 能正常扣血，
            // 也能让被击杀的玩家/宠物播报专属死亡信息“XX被天选之子的激光烧穿了”。
            DamageSource laserSource = sp.damageSources().source(ModDamageTypes.LASER.getKey(), sp);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, aoeBox,
                e -> e.isAlive() && e != sp)) {
                victim.hurt(laserSource, laserCharge);
                victim.invulnerableTime = 0;
            }
            if (st.powerApplied) {
                restoreRedstonePowered(level, st);
                st.poweredBlocks.clear();
                st.powerApplied = false;
                st.lastTarget = null;
            }
            return;
        }

        // 命中方块：周期性 3×3×3 强充能 + 破坏判定
        if (r.powerPos != null) {
            boolean nowPower;
            if (laserCharge >= 8) {
                nowPower = true; // 充能 8：一直强充能，不会间断
            } else {
                // 充能 < 8：每 (8-充能) 刻强充能、(8-充能) 刻休息，循环往复
                int cycle = 2 * (8 - laserCharge);
                nowPower = (st.tick % cycle) < (8 - laserCharge);
            }
            // 相位切换（或瞄准点变化）时清空本相位的音效/特殊动作记录，让下一相位重新播放
            if (!nowPower || !st.powerApplied || !r.powerPos.equals(st.lastTarget)) {
                st.phaseActions.clear();
            }
            updateRedstonePowered(level, st, r.powerPos, nowPower);
            // 充能相位内每个 tick 都驱动一次红石器械（发射器周期性触发、活塞用临时红石线保持信号），
            // 放到 updateRedstonePowered 之外，避免其“目标与相位未变则提前返回”导致器械只在切入相位时触发一次。
            if (nowPower) {
                driveRedstoneMachines(level, st, r.powerPos);
            }
            maybeBreakAimedBlock(level, st, r.powerPos, laserCharge);
        } else if (st.powerApplied) {
            restoreRedstonePowered(level, st);
            st.poweredBlocks.clear();
            st.powerApplied = false;
            st.lastTarget = null;
        }
    }

    /**
     * 女仆红石块权杖：充能为 8 的激光攻击（模拟玩家长按右键）。
     * 每刻朝目标方向发射红石射线，仅在满足安全条件时开火：
     * <ul>
     *   <li>女仆与目标之间的连线上不存在玩家或其他宠物（含女仆）；</li>
     *   <li>目标 1 格范围内不存在玩家或其他宠物。</li>
     * </ul>
     * 与玩家版不同：不做方块强充能/破坏（避免女仆大面积改地形）、不消耗权杖耐久、
     * 伤害源为女仆自身，且 AOE 伤害同样排除玩家/宠物。
     *
     * @return 实际发射时返回光束末端点（折线终点，供音效定位），未开火返回 {@code null}
     */
    /**
     * 是否已安装车万女仆（Touhou Little Maid）。
     * 车万女仆只是本 mod 的「可选联动」：未安装时跳过一切直接引用女仆类的地方，
     * 避免因加载不到女仆类而抛出 NoClassDefFoundError，使本 mod 在无车万女仆的环境下也能独立生效。
     */
    public static boolean isMaidModLoaded() {
        return net.neoforged.fml.ModList.get().isLoaded("touhou_little_maid");
    }

    /**
     * 判断实体是否为车万女仆的 EntityMaid。必须用 Class.forName(String) 反射，
     * 而非直接写 `instanceof EntityMaid`：后者会把 EntityMaid 的类常量烧进本类的字节码，
     * 在未安装车万女仆时，NeoForge 加载本类（AutomaticEventSubscriber 的 Class.forName 会
     * 解析整个常量池）就会抛 NoClassDefFoundError，导致整个 mod 无法启动。
     */
    public static boolean isTouhouMaid(Entity entity) {
        return isMaidModLoaded() && isInstanceTouhouMaid(entity);
    }

    private static boolean isInstanceTouhouMaid(Entity entity) {
        try {
            Class<?> maidClass = Class.forName(
                "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid");
            return maidClass.isInstance(entity);
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** 判断女仆是否为指定玩家所有（通过反射调用 EntityMaid.isOwnedBy）。 */
    public static boolean isMaidOwnedBy(Entity maid, Player player) {
        if (!isMaidModLoaded() || maid == null || player == null) return false;
        try {
            Class<?> maidClass = Class.forName(
                "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid");
            if (!maidClass.isInstance(maid)) return false;
            for (java.lang.reflect.Method m : maidClass.getMethods()) {
                if (m.getName().equals("isOwnedBy") && m.getParameterCount() == 1) {
                    return Boolean.TRUE.equals(m.invoke(maid, player));
                }
            }
            return false;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    public static Vec3 executeMaidRedstoneStaffTick(LivingEntity maid, LivingEntity target) {
        if (!(maid.level() instanceof ServerLevel level)) return null;
        if (target == null || !target.isAlive()) return null;

        // 朝目标眼位方向发射
        Vec3 origin = maid.getEyePosition();
        Vec3 toTarget = target.getEyePosition().subtract(origin);
        if (toTarget.lengthSqr() < 1.0E-4) return null;
        Vec3 dir = toTarget.normalize();

        // 安全校验不通过则不开火（等待条件满足）
        if (!isMaidLaserPathClear(level, maid, origin, dir, target)) return null;

        RedstoneRayResult r = computeRedstoneRay(level, maid, origin, dir, 8);
        int laserCharge = r.charge;
        if (laserCharge <= 0) return null;

        // 以红石粒子渲染整条折线射线（从发射者眼位起完整渲染）
        spawnRedstoneBeamParticles(level, maid, r.points);

        // 命中生物：单格范围（1 格半径）AOE 伤害，每刻对范围内生物造成（激光当前充能数）点伤害，
        // 并清空无敌帧使其连续受击；玩家/宠物（含女仆）一律排除，不参与伤害。
        if (r.damageTarget != null) {
            Vec3 hitPos = r.points.get(r.points.size() - 1);
            AABB aoeBox = new AABB(hitPos, hitPos).inflate(1.0);
            DamageSource laserSource = maid.damageSources().source(ModDamageTypes.LASER.getKey(), maid);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, aoeBox,
                e -> e.isAlive() && e != maid && !isProtectedMaidAlly(e))) {
                victim.hurt(laserSource, laserCharge);
                victim.invulnerableTime = 0;
            }
        }
        // 返回光束末端点（折线最后一段的终点），供客户端定位激光持续音效
        return r.points.get(r.points.size() - 1);
    }

    /** 是否受保护的女仆友方：玩家、女仆、已驯服宠物。 */
    private static boolean isProtectedMaidAlly(Entity e) {
        if (e instanceof Player) return true;
        if (isTouhouMaid(e)) return true;
        return e instanceof TamableAnimal t && t.getOwnerUUID() != null;
    }

    /** 女仆红石激光的安全校验：女仆与目标连线之间、以及目标 1 格范围内，均不得存在玩家或其他宠物。 */
    private static boolean isMaidLaserPathClear(ServerLevel level, LivingEntity maid,
                                                Vec3 origin, Vec3 dir, LivingEntity target) {
        // 1) 女仆与目标之间（线段 AABB 内）的玩家/宠物不得与连线相交
        Vec3 end = origin.add(dir.scale(Math.max(0.5, origin.distanceTo(target.getEyePosition()))));
        AABB pathBox = new AABB(origin, end).inflate(0.5);
        for (Entity e : level.getEntities(maid, pathBox,
            e -> e != target && e != maid && isProtectedMaidAlly(e) && e.isAlive())) {
            if (e.getBoundingBox().clip(origin, end).isPresent()) return false;
        }
        // 2) 目标 1 格范围内不得存在玩家/宠物
        AABB targetBox = target.getBoundingBox().inflate(1.0);
        for (Entity e : level.getEntities(target, targetBox,
            e -> e != target && e != maid && isProtectedMaidAlly(e) && e.isAlive())) {
            return false;
        }
        return true;
    }

    /** 射线追踪结果：各段端点（粒子用）与最终命中。 */
    private static final class RedstoneRayResult {
        /** 折线各段端点（含起点与各命中点）。 */
        final List<Vec3> points = new ArrayList<>();
        /** 最终命中的普通方块（强充目标），未命中或命中实体时为 null。 */
        BlockPos powerPos = null;
        /** 最终命中的生物（伤害目标），否则为 null。 */
        LivingEntity damageTarget = null;
        /** 激光路径上穿过的掉落物实体（不阻挡激光，用于按概率摧毁）。 */
        final List<ItemEntity> itemTargets = new ArrayList<>();
        /** 激光当前的实际充能（命中紫水晶簇时衰减），不影响权杖基础充能 {@link RedstoneStaffState#charge}。 */
        int charge;
    }

    /** 逐段追踪红石射线：普通方块停下，紫水晶块/母岩镜面反射（不衰减），紫水晶簇随机偏转并衰减充能。
     *  attacker 用于排除发射者自身并作为射线遮罩（玩家与女仆共用）。 */
    private static RedstoneRayResult computeRedstoneRay(ServerLevel level, LivingEntity attacker,
                                                        Vec3 origin, Vec3 dir, int charge) {
        RedstoneRayResult r = new RedstoneRayResult();
        r.charge = charge;
        int bounces = 0;
        while (bounces++ <= REDSTONE_RAY_MAX_BOUNCES) {
            Vec3 end = origin.add(dir.scale(REDSTONE_RAY_RANGE));

            // 方块命中
            BlockHitResult bhr = level.clip(new ClipContext(origin, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, attacker));
            double blockDist = bhr.getType() == HitResult.Type.BLOCK
                ? origin.distanceToSqr(bhr.getLocation()) : Double.MAX_VALUE;

            // 实体命中（排除发射者自己）
            double bestEntityDist = Double.MAX_VALUE;
            LivingEntity bestEntity = null;
            Vec3 bestEntityHit = null;
            AABB search = new AABB(origin, end).inflate(1.0);
            // 末影龙为多部件实体，射线打中的是 EnderDragonPart（非 LivingEntity），
            // 这里同样纳入检测并将其解析为父实体 EnderDragon，使红石激光能对末影龙造成伤害。
            // 同时记录激光实际打中的位置（而不是实体包围盒中心），避免大体积实体（末影龙）
            // 被击中时激光终点跳向其整体中心，产生“强制锁定”的观感。
            // 掉落物实体不阻挡激光，仅记录进 itemTargets 供按概率摧毁。
            for (Entity e : level.getEntities(attacker, search,
                e -> ((e instanceof LivingEntity || e instanceof EnderDragonPart || e instanceof ItemEntity)
                    && e != attacker && e.isAlive()))) {
                if (e instanceof ItemEntity itemEnt) {
                    AABB bb = e.getBoundingBox();
                    java.util.Optional<Vec3> hit = bb.clip(origin, end);
                    if (hit.isPresent() && !r.itemTargets.contains(itemEnt)) {
                        r.itemTargets.add(itemEnt);
                    }
                    continue;
                }
                LivingEntity target = e instanceof EnderDragonPart part
                    ? part.getParent() : (LivingEntity) e;
                AABB bb = e.getBoundingBox().inflate(0.3);
                java.util.Optional<Vec3> hit = bb.clip(origin, end);
                if (hit.isPresent()) {
                    double d = origin.distanceToSqr(hit.get());
                    if (d < bestEntityDist) {
                        bestEntityDist = d;
                        bestEntity = target;
                        bestEntityHit = hit.get();
                    }
                }
            }

            r.points.add(origin);

            // 实体比方块更近 -> 停止并造成伤害
            if (bestEntity != null && bestEntityDist < blockDist) {
                r.points.add(bestEntityHit);
                r.damageTarget = bestEntity;
                return r;
            }

            if (bhr.getType() != HitResult.Type.BLOCK) {
                // 什么都没命中：到射程尽头停止
                r.points.add(end);
                return r;
            }

            Vec3 hitPoint = bhr.getLocation();
            BlockPos hitPos = bhr.getBlockPos();
            BlockState hitState = level.getBlockState(hitPos);

            // 紫水晶簇/紫水晶芽：随机偏转，充能减少 1~2（不小于 0）
            if (hitState.is(Blocks.AMETHYST_CLUSTER) || hitState.is(Blocks.SMALL_AMETHYST_BUD)
                || hitState.is(Blocks.MEDIUM_AMETHYST_BUD) || hitState.is(Blocks.LARGE_AMETHYST_BUD)) {
                r.points.add(hitPoint);
                r.charge = Math.max(0, r.charge - (1 + level.random.nextInt(2)));
                if (r.charge <= 0) {
                    return r; // 充能归零，射线消失
                }
                dir = randomDeflectDir(level.random, dir);
                origin = hitPoint.add(dir.scale(0.1));
                continue;
            }

            // 紫水晶块/紫水晶母岩：把被照射面视为镜面反射（不会衰减）
            if (hitState.is(Blocks.AMETHYST_BLOCK) || hitState.is(Blocks.BUDDING_AMETHYST)) {
                r.points.add(hitPoint);
                Vec3 normal = Vec3.atLowerCornerOf(bhr.getDirection().getNormal());
                dir = dir.subtract(normal.scale(2.0 * dir.dot(normal)));
                origin = hitPoint.add(dir.scale(0.1));
                continue;
            }

            // 普通方块：停止并作为强充/破坏目标
            r.points.add(hitPoint);
            r.powerPos = hitPos;
            return r;
        }
        return r;
    }

    /** 随机偏转一个方向（任意水平转角 + 一定俯仰偏移）。 */
    private static Vec3 randomDeflectDir(RandomSource random, Vec3 dir) {
        float yaw = random.nextFloat() * (float) Math.PI * 2.0F;
        float pitch = (random.nextFloat() - 0.5F) * 1.5F;
        return dir.yRot(yaw).xRot(pitch);
    }

    /** 根据相位把目标 3×3×3 区域内的红石方块/红石粉设置/恢复为“充能”状态（等级 15）。
     *  采用“先全部写入、再统一通知邻居”的两遍法，避免在循环中逐个写入时
     *  红石网络因邻居尚未更新而错误地回落，确保充能真正生效并传播到相连电路。 */
    private static void updateRedstonePowered(ServerLevel level, RedstoneStaffState st,
                                              BlockPos target, boolean nowPower) {
        if (nowPower) {
            if (st.powerApplied && target.equals(st.lastTarget)) {
                return; // 相位与目标均未变化，无需重复写入
            }
            if (st.powerApplied) {
                // 瞄准点变化：先恢复旧区域再写入新区域
                restoreRedstonePowered(level, st);
                st.poweredBlocks.clear();
            }
            st.lastTarget = target;
            st.powerApplied = true;
            // 第一遍：把区域内所有可被驱动的红石方块写入“充能/激活”状态（仅同步客户端，不立即通知邻居）
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos p = target.offset(dx, dy, dz);
                        BlockState s = level.getBlockState(p);
                        BlockState powered = redstonePoweredState(s);
                        if (powered != null && !powered.equals(s) && !st.poweredBlocks.containsKey(p)) {
                            st.poweredBlocks.put(p, s);
                            level.setBlock(p, powered, 2);
                        }
                    }
                }
            }
            // 第二遍：统一触发邻居更新，让红石网络（红石粉、中继器、比较器、红石灯）正确传播与激活
            for (BlockPos p : st.poweredBlocks.keySet()) {
                BlockState powered = level.getBlockState(p);
                level.updateNeighborsAt(p, powered.getBlock());
                level.updateNeighbourForOutputSignal(p, powered.getBlock());
            }
            // 第三遍：铁轨特殊处理。原版铁轨在相邻方块更新时会通过 neighborChanged->updateState
            // 依据“是否有真实红石信号”重算并覆盖 POWERED（权杖射线不是真实信号，会被重置为 false）。
            // 这里在邻居更新完成后强制把动力铁轨/激活铁轨的 POWERED 重新写回 true，
            // 使矿车能持续获得推进/激活效果。铜灯同理，邻居更新后重写 LIT=true 保证常亮。
            for (BlockPos p : st.poweredBlocks.keySet()) {
                BlockState cur = level.getBlockState(p);
                if (cur.getBlock() instanceof PoweredRailBlock || cur.is(Blocks.ACTIVATOR_RAIL)) {
                    if (!cur.getValue(BlockStateProperties.POWERED)) {
                        level.setBlock(p, cur.setValue(BlockStateProperties.POWERED, true), 3);
                    }
                } else if (cur.getBlock() instanceof CopperBulbBlock) {
                    if (!cur.getValue(BlockStateProperties.LIT)) {
                        level.setBlock(p, cur.setValue(BlockStateProperties.LIT, true), 3);
                    }
                }
            }
            // 第四遍：播放本相位首次写入时方块的开/关音效（门/活板门等）
            playPhaseSoundEffects(level, st);
        } else if (st.powerApplied) {
            restoreRedstonePowered(level, st);
            st.poweredBlocks.clear();
            st.powerApplied = false;
            st.lastTarget = null;
            st.phaseActions.clear();
        }
    }

    /** 返回方块被红石射线“充能/激活”后的状态；若该方块无法被红石射线驱动则返回 null。
     *  覆盖范围：
     *   - 红石粉/目标方块等(POWER=15)
     *   - 红石灯(LIT)
     *   - 中继器/比较器(POWERED)、发射器/投掷器(TRIGGERED)
     *   - 拉杆(POWERED)、按钮(POWERED)、压力板(POWERED)
     *   - 绊线钩(POWERED+ATTACHED)、红石火把(LIT=false)
     *   - 门(OPEN)、活板门(OPEN)
     *   - 侦测器(POWERED)
     *   - 铜灯(LIT，直接控制亮灭)、红石矿石(LIT)
     *   - 栅栏门(OPEN)
     *   - 动力铁轨/激活铁轨(POWERED)
     *  红石块本身始终自带 15 级充能，无需写入。 */
    private static BlockState redstonePoweredState(BlockState s) {
        Block b = s.getBlock();
        // 红石粉/线/POWER 属性方块（如避雷针、目标方块等）
        if (s.hasProperty(BlockStateProperties.POWER)) {
            // 幽匿传感器/阳光传感器的 POWER 由专门的周期性动作逻辑（activate / 切换模式）管理，
            // 若在此直接写入会与它们的相位机状态机冲突，故排除。
            if (b instanceof SculkSensorBlock || b instanceof DaylightDetectorBlock) {
                return null;
            }
            return s.setValue(BlockStateProperties.POWER, 15);
        }
        // 红石灯：直接亮
        if (s.hasProperty(BlockStateProperties.LIT) && s.is(Blocks.REDSTONE_LAMP)) {
            return s.setValue(BlockStateProperties.LIT, true);
        }
        // 中继器/比较器：POWERED
        if (s.hasProperty(BlockStateProperties.POWERED)
            && (s.is(Blocks.REPEATER) || s.is(Blocks.COMPARATOR))) {
            return s.setValue(BlockStateProperties.POWERED, true);
        }
        // 发射器/投掷器：TRIGGERED
        if (s.hasProperty(BlockStateProperties.TRIGGERED)
            && (s.is(Blocks.DISPENSER) || s.is(Blocks.DROPPER))) {
            return s.setValue(BlockStateProperties.TRIGGERED, true);
        }
        // 拉杆：POWERED
        if (b instanceof LeverBlock) {
            return s.setValue(BlockStateProperties.POWERED, true);
        }
        // 按钮：POWERED
        if (b instanceof ButtonBlock) {
            return s.setValue(BlockStateProperties.POWERED, true);
        }
        // 压力板：POWERED
        if (b instanceof PressurePlateBlock) {
            return s.setValue(BlockStateProperties.POWERED, true);
        }
        // 绊线钩：POWERED + ATTACHED
        if (b instanceof TripWireHookBlock) {
            return s.setValue(BlockStateProperties.POWERED, true)
                    .setValue(BlockStateProperties.ATTACHED, true);
        }
        // 红石火把：熄灭
        if (b instanceof RedstoneTorchBlock) {
            return s.setValue(BlockStateProperties.LIT, false);
        }
        // 门：打开
        if (b instanceof DoorBlock) {
            return s.setValue(BlockStateProperties.OPEN, true);
        }
        // 活板门：打开
        if (b instanceof TrapDoorBlock) {
            return s.setValue(BlockStateProperties.OPEN, true);
        }
        // 侦测器：POWERED
        if (b instanceof ObserverBlock) {
            return s.setValue(BlockStateProperties.POWERED, true);
        }
        // 栅栏门：打开
        if (b instanceof FenceGateBlock) {
            return s.setValue(BlockStateProperties.OPEN, true);
        }
        // 铜灯：直接控制亮灭（LIT）
        if (b instanceof CopperBulbBlock) {
            return s.setValue(BlockStateProperties.LIT, true);
        }
        // 红石矿石：亮
        if (b instanceof RedStoneOreBlock) {
            return s.setValue(BlockStateProperties.LIT, true);
        }
        // 动力铁轨/激活铁轨：POWERED（1.21.1 中激活铁轨无独立类，用方块 ID 判断）
        if (b instanceof PoweredRailBlock || s.is(Blocks.ACTIVATOR_RAIL)) {
            return s.setValue(BlockStateProperties.POWERED, true);
        }
        // 侦测铁轨（探测器）：POWERED
        if (b instanceof DetectorRailBlock) {
            return s.setValue(BlockStateProperties.POWERED, true);
        }
        return null;
    }

    /** 驱动 3×3×3 区域内的红石器械（每个充能刻调用一次）：
     *  发射器/投掷器直接调用其 tick 立即触发一次发射/投掷（等价于被红石脉冲激活）；
     *  活塞若未延长，则用瞬时不可见信号直接触发其延长（一推一拉见休息相位的收回）；
     *  其余需要"动作/特性"的装置（音符盒、合成器、幽匿传感器、阳光传感器、TNT、钟等）在此排队触发，
     *  每个充能相位每个方块只触发一次（由 st.phaseActions 去重）。 */
    private static void driveRedstoneMachines(ServerLevel level, RedstoneStaffState st, BlockPos target) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = target.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() instanceof DispenserBlock) {
                        // BlockState.tick 为 public（等价于被调度一次的块更新），
                        // 内部调用受保护的 DispenserBlock.tick，触发一次发射/投掷。
                        s.tick(level, p, level.random);
                    } else if ((s.is(Blocks.PISTON) || s.is(Blocks.STICKY_PISTON))
                        && !s.getValue(BlockStateProperties.EXTENDED)) {
                        extendPiston(level, st, p, s);
                    } else if (drivePeriodicAction(level, st, p, s)) {
                        // 已触发特殊动作；本相位内不再重复触发
                    }
                }
            }
        }
        // TNT 矿车：周期性点燃（区域内所有未引燃的 TNT 矿车，倒计时 5 游戏刻）
        AABB box = new AABB(target)
            .inflate(1.0)
            .expandTowards(1, 1, 1);
        for (MinecartTNT cart : level.getEntitiesOfClass(MinecartTNT.class, box)) {
            if (!cart.isPrimed() && st.phaseActions.add(new BlockPos(cart.blockPosition()))) {
                cart.primeFuse();
                setMinecartTntFuse(cart, 5);
            }
        }
    }

    /** 处理充能相位内需要"动作/特性触发的装置"。返回 true 表示已对该方块执行了动作（本相位只执行一次）。
     *  覆盖：音符盒、合成器、幽匿传感器、阳光传感器、TNT/TNT矿车、钟。 */
    private static boolean drivePeriodicAction(ServerLevel level, RedstoneStaffState st, BlockPos p, BlockState s) {
        Block b = s.getBlock();
        if (b instanceof NoteBlock) {
            if (st.phaseActions.add(p)) {
                // 音符盒：利用领居变化逻辑（INSTRUMENT 需先设置）播放一次音符
                level.blockEvent(p, b, 0, 0);
                level.gameEvent(null, net.minecraft.world.level.gameevent.GameEvent.NOTE_BLOCK_PLAY, p);
                return true;
            }
        } else if (b instanceof CrafterBlock) {
            if (st.phaseActions.add(p)) {
                // 合成器：周期性播放"喷射/张嘴"动画（CRAFTING 推杆），并带音效反馈；
                // 休息相位 restoreRedstonePowered 会把 CRAFTING 复位回 false，形成周期性张嘴/闭嘴。
                st.crafterBlocks.add(p);
                level.setBlock(p, s.setValue(BlockStateProperties.CRAFTING, true), 2);
                if (level.getBlockEntity(p) instanceof CrafterBlockEntity crafter) {
                    crafter.setCraftingTicksRemaining(6);
                }
                level.levelEvent(1050, p, 0);
                return true;
            }
        } else if (b instanceof SculkSensorBlock) {
            if (st.phaseActions.add(p)) {
                // 幽匿传感器：跳过 canActivate 的 INACTIVE 门槛，每个充能相位强制重新激活一次，
                // 否则受制于内部 30 刻激活 + 10 刻冷却，音效只会极偶尔触发。
                // 先强制置回 INACTIVE 再调用 activate，保证每次都走完整的“激活→出声”流程。
                BlockState cur = level.getBlockState(p);
                if (SculkSensorBlock.getPhase(cur) != SculkSensorPhase.INACTIVE) {
                    level.setBlock(p, cur.setValue(BlockStateProperties.SCULK_SENSOR_PHASE, SculkSensorPhase.INACTIVE)
                        .setValue(BlockStateProperties.POWER, 0), 3);
                    cur = level.getBlockState(p);
                }
                ((SculkSensorBlock) b).activate(null, level, p, cur, Math.min(15, Math.max(1, st.charge)), 0);
                // activate 内部仅在非含水时播 SCULK_CLICKING；这里显式补播，保证每个相位都有“咔哒”音效。
                level.playSound(null, p, SoundEvents.SCULK_CLICKING, SoundSource.BLOCKS, 1.0F,
                    level.random.nextFloat() * 0.2F + 0.8F);
                return true;
            }
        } else if (b instanceof DaylightDetectorBlock) {
            if (st.phaseActions.add(p)) {
                // 阳光传感器：周期性切换反相模式并刷新信号强度（等价右键)
                BlockState cycled = s.cycle(BlockStateProperties.INVERTED);
                level.setBlock(p, cycled, 2);
                level.updateNeighborsAt(p, b);
                updateDaylightSignal(level, p, b, cycled);
                return true;
            }
        } else if (b instanceof TntBlock) {
            if (st.phaseActions.add(p)) {
                // TNT：点击即点燃并设倒计时 5 游戏刻（40 刻… 改为 5 刻）
                PrimedTnt tnt = new PrimedTnt(level, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, st.owner != null
                    ? level.getPlayerByUUID(st.owner) : null);
                tnt.setFuse(5);
                level.addFreshEntity(tnt);
                level.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.removeBlock(p, false);
                return true;
            }
        } else if (b instanceof BellBlock) {
            if (st.phaseActions.add(p)) {
                // 钟：敲响一次
                ((BellBlock) b).attemptToRing(level, p, null);
                return true;
            }
        }
        return false;
    }

    /** 通过反射把 TNT 矿车的引信设为指定游戏刻（MinecartTNT.fuse 为私有字段，无公开 setter）。 */
    private static void setMinecartTntFuse(MinecartTNT cart, int fuse) {
        try {
            java.lang.reflect.Field f = MinecartTNT.class.getDeclaredField("fuse");
            f.setAccessible(true);
            f.setInt(cart, fuse);
        } catch (Exception e) {
            LOGGER.warn("无法设置 TNT 矿车引信", e);
        }
    }

    /** 刷新阳光传感器的信号强度（等价其自身方块刻的更新逻辑），保证切换模式后立即反映到输出。 */
    private static void updateDaylightSignal(ServerLevel level, BlockPos pos, Block block, BlockState state) {
        int i = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos) - level.getSkyDarken();
        float f = level.getSunAngle(1.0F);
        boolean inverted = state.getValue(BlockStateProperties.INVERTED);
        if (inverted) {
            i = 15 - i;
        } else if (i > 0) {
            float f1 = f < (float) Math.PI ? 0.0F : (float) (Math.PI * 2);
            f += (f1 - f) * 0.2F;
            i = Math.round((float) i * Mth.cos(f));
        }
        i = Mth.clamp(i, 0, 15);
        if (state.getValue(BlockStateProperties.POWER) != i) {
            level.setBlock(pos, state.setValue(BlockStateProperties.POWER, i), 3);
            level.updateNeighborsAt(pos, block);
        }
    }

    /** 登记某活塞为“权杖驱动延长中”，使 PistonBaseBlockMixin 放行其 getNeighborSignal。 */
    private static void registerPistonExtend(ServerLevel level, BlockPos pos, UUID owner) {
        STAFF_EXTEND_PISTONS.put(new StaffPistonKey(level.dimension(), pos), owner);
    }

    /** 注销某活塞的“权杖延长”登记（收回阶段调用，使 getNeighborSignal 恢复 false）。 */
    private static void unregisterPistonExtend(ServerLevel level, BlockPos pos) {
        STAFF_EXTEND_PISTONS.remove(new StaffPistonKey(level.dimension(), pos));
    }

    /** 供 PistonBaseBlockMixin 查询：该活塞当前是否被权杖驱动延长。 */
    public static boolean isPistonStaffExtending(ResourceKey<Level> dimension, BlockPos pos) {
        return STAFF_EXTEND_PISTONS.containsKey(new StaffPistonKey(dimension, pos));
    }

    /** 清除某玩家残留的“权杖延长”登记（会话开始/结束时的兜底清理）。 */
    private static void clearStaffPistonTracking(UUID owner) {
        STAFF_EXTEND_PISTONS.entrySet().removeIf(e -> e.getValue().equals(owner));
    }

    /** 驱动活塞延长：无需任何临时红石方块。
     *  通过 {@link Level#blockEvent} 把延长事件排队到本刻末尾处理——这正是原版活塞的触发方式：
     *  服务端处理事件时会向附近客户端广播 ClientboundBlockEventPacket，客户端据此在自己的世界中
     *  生成 PistonMovingBlockEntity，从而播放完整的伸出动画（直接调用 triggerEvent 不会广播，
     *  客户端只能看到方块状态瞬间跳变，毫无动画）。 */
    private static void extendPiston(ServerLevel level, RedstoneStaffState st, BlockPos pistonPos, BlockState s) {
        Direction facing = s.getValue(BlockStateProperties.FACING);
        registerPistonExtend(level, pistonPos, st.owner);
        level.blockEvent(pistonPos, s.getBlock(), PistonBaseBlock.TRIGGER_EXTEND, facing.get3DDataValue());
        st.drivenPistons.add(pistonPos);
    }

    /** 恢复此前被红石射线充能/激活的方块，并触发被驱动过的活塞收回（一推一拉）。 */
    private static void restoreRedstonePowered(ServerLevel level, RedstoneStaffState st) {
        for (Map.Entry<BlockPos, BlockState> e : st.poweredBlocks.entrySet()) {
            BlockPos p = e.getKey();
            BlockState original = e.getValue();
            BlockState powered = redstonePoweredState(original);
            // 仅当当前仍是射线写入的状态时才恢复，避免覆盖玩家手动改动/被破坏的方块
            if (powered != null && level.getBlockState(p).equals(powered)) {
                level.setBlock(p, original, 2);
                level.updateNeighborsAt(p, original.getBlock());
                level.updateNeighbourForOutputSignal(p, original.getBlock());
                // 门/活板门关闭时播放关闭音效（门只对下半块播一次，上下两块一起生效）
                if (original.getBlock() instanceof DoorBlock) {
                    if (original.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
                        level.playSound(null, p, getDoorSound(original, false), SoundSource.BLOCKS,
                            1.0F, level.random.nextFloat() * 0.1F + 0.9F);
                    }
                } else if (original.getBlock() instanceof TrapDoorBlock) {
                    level.playSound(null, p, getTrapdoorSound(original, false), SoundSource.BLOCKS,
                        1.0F, level.random.nextFloat() * 0.1F + 0.9F);
                }
            }
        }
        st.poweredBlocks.clear();
        // 复位本相位被触发过周期性动作的合成器（把"张嘴"的 CRAFTING 置回 false，形成闭嘴）
        for (BlockPos p : st.crafterBlocks) {
            BlockState cs = level.getBlockState(p);
            if (cs.getBlock() instanceof CrafterBlock && cs.getValue(BlockStateProperties.CRAFTING)) {
                level.setBlock(p, cs.setValue(BlockStateProperties.CRAFTING, false), 2);
                if (level.getBlockEntity(p) instanceof CrafterBlockEntity crafter) {
                    crafter.setCraftingTicksRemaining(0);
                }
            }
        }
        st.crafterBlocks.clear();
        // 触发本会话驱动过的活塞收回（一推一拉）：同样排队 blockEvent，让客户端播放完整的收回动画。
        for (BlockPos p : st.drivenPistons) {
            // 收回前必须先注销“权杖延长”登记，使 getNeighborSignal 恢复 false，
            // 否则服务端触发收回时会被判定为“仍有信号”而拦截（活塞保持延长）。
            unregisterPistonExtend(level, p);
            BlockState ps = level.getBlockState(p);
            if ((ps.is(Blocks.PISTON) || ps.is(Blocks.STICKY_PISTON))
                && ps.getValue(BlockStateProperties.EXTENDED)) {
                level.blockEvent(p, ps.getBlock(), PistonBaseBlock.TRIGGER_CONTRACT,
                    ps.getValue(BlockStateProperties.FACING).get3DDataValue());
            }
            // 若正处于 MOVING_PISTON 延长动画中则跳过，动画完成后状态由系统自动处理
        }
        st.drivenPistons.clear();
    }

    /** 播放本相位首次写入时方块的开音效（门/活板门），每个相位每个方块只播一次。 */
    private static void playPhaseSoundEffects(ServerLevel level, RedstoneStaffState st) {
        for (BlockPos p : st.poweredBlocks.keySet()) {
            if (!st.phaseActions.add(p)) continue; // 本相位已处理过
            BlockState original = st.poweredBlocks.get(p);
            BlockState powered = redstonePoweredState(original);
            if (powered == null || !level.getBlockState(p).equals(powered)) continue;
            if (original.getBlock() instanceof DoorBlock) {
                // 门：上下两块由 redstonePoweredState 统一写入，这里只对下半块播一次开音效
                if (original.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
                    level.playSound(null, p, getDoorSound(original, true), SoundSource.BLOCKS,
                        1.0F, level.random.nextFloat() * 0.1F + 0.9F);
                }
            } else if (original.getBlock() instanceof TrapDoorBlock) {
                level.playSound(null, p, getTrapdoorSound(original, true), SoundSource.BLOCKS,
                    1.0F, level.random.nextFloat() * 0.1F + 0.9F);
            }
        }
    }

    /** 通过反射读取门/活板门方块携带的 BlockSetType（用于获取对应材质的开/关音效）。 */
    private static BlockSetType getBlockSetTypeOf(Block block) {
        Class<?> c = block.getClass();
        while (c != null && c != Block.class) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField("type");
                f.setAccessible(true);
                Object v = f.get(block);
                if (v instanceof BlockSetType bst) {
                    return bst;
                }
            } catch (Exception ignored) {
                // 继续向父类查找
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static SoundEvent getDoorSound(BlockState state, boolean open) {
        BlockSetType bst = getBlockSetTypeOf(state.getBlock());
        if (bst != null) {
            return open ? bst.doorOpen() : bst.doorClose();
        }
        return open ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE;
    }

    private static SoundEvent getTrapdoorSound(BlockState state, boolean open) {
        BlockSetType bst = getBlockSetTypeOf(state.getBlock());
        if (bst != null) {
            return open ? bst.trapdoorOpen() : bst.trapdoorClose();
        }
        return open ? SoundEvents.WOODEN_TRAPDOOR_OPEN : SoundEvents.WOODEN_TRAPDOOR_CLOSE;
    }

    /** 充能 > 4 时，每 20±10 刻按 ((充能-4)*5%) 概率破坏被铁镐可挖掘的目标方块（无掉落）。 */
    private static void maybeBreakAimedBlock(ServerLevel level, RedstoneStaffState st,
                                             BlockPos target, int charge) {
        if (charge <= 4) return;
        if (st.breakTimer > 0) {
            st.breakTimer--;
            return;
        }
        st.breakTimer = 20 + level.random.nextInt(21) - 10; // 10~30 刻
        double chance = (charge - 4) * 0.05; // 充能 5~8：5%/10%/15%/20%
        if (level.random.nextDouble() >= chance) return;
        BlockState s = level.getBlockState(target);
        if (s.isAir()) return;
        // 可破坏条件：方块本身可被挖掘（getDestroySpeed >= 0，排除基岩等），并且满足以下之一：
        //  1) 铁镐及其以下等级的工具可正确挖掘（石头、铁矿石、钻石矿石等）；
        //  2) 不需要正确工具即可破坏的徒手可挖方块（泥土、原木、沙砾等）。
        if (s.getDestroySpeed(level, target) >= 0
            && (Items.IRON_PICKAXE.getDefaultInstance().isCorrectToolForDrops(s)
                || !s.requiresCorrectToolForDrops())) {
            // 若目标是变形生物变来的方块：激光摧毁时同样判定为该生物死亡（移除数据并结算击杀）
            ResourceLocation dimId = level.dimension().location();
            Map<BlockPos, java.util.List<TransmutationData>> dimMap = BLOCK_TRANSMUTATIONS.get(dimId);
            java.util.List<TransmutationData> list = dimMap != null ? dimMap.remove(target) : null;
            if (list != null && !list.isEmpty()) {
                for (TransmutationData data : new java.util.ArrayList<>(list)) {
                    if (data.playerUuid() != null) {
                        killTransmutedPlayer(level, data, target, true);
                    } else {
                        handleTransmutationKillCredit(level, data, target, null);
                    }
                }
            }
            level.destroyBlock(target, false); // 无掉落物
        }
    }

    /** 把给定的“相对持有者头部”偏移（X 右 / Y 上 / Z 前，均相对持有者头部朝向：含 yaw 与上下摆头俯仰）
     *  旋转后叠加到基准位置 base 上，使发射点/汇聚点相对头部的位置（与角度）保持恒定、与身体无关。
     *  玩家上下摆头时，偏移位置随头部一同旋转（X 右、Y 上、Z 前均以头部视觉角度为准）。 */
    public static Vec3 applyHeadOffset(net.minecraft.world.entity.LivingEntity emitter, Vec3 base,
                                       double ox, double oy, double oz) {
        if (ox == 0 && oy == 0 && oz == 0) return base;
        double yawDeg = emitter.getYHeadRot();
        double pitchDeg = emitter.getXRot();
        float yaw = (float) Math.toRadians(yawDeg);
        float pitch = (float) Math.toRadians(pitchDeg);
        float f1 = -yaw;
        float cosYaw = (float) Math.cos(f1); // = cos(yaw)
        float sinYaw = (float) Math.sin(f1); // = -sin(yaw)
        float cosPitch = (float) Math.cos(pitch);
        float sinPitch = (float) Math.sin(pitch);
        // 头部坐标系三轴（右手系：right × up = fwd）：
        // 前向（视线/头部朝向，随 yaw+俯仰）
        Vec3 fwd = new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
        // 右向（仅随 yaw 的水平右方；面朝南(+Z)时指向 -X=西=右手边）
        Vec3 right = new Vec3(-cosYaw, 0, sinYaw);
        // 上向（随俯仰的头部正上方；平视时 = 世界 Y）
        Vec3 up = new Vec3(sinYaw * sinPitch, cosPitch, cosYaw * sinPitch);
        return base.add(right.scale(ox)).add(up.scale(oy)).add(fwd.scale(oz));
    }

    /** 线/汇聚点偏移：把配置的 X(右)/Y(上)/Z(前) 偏移按“权杖持有者身体朝向”（仅 yaw，不含俯仰）旋转后叠加到基准位置 base 上，
     *  发射点/汇聚点相对玩家的位置保持恒定（低头/抬头不会摆到玩家前后方）。
     *  作用于所有“发射线或具备汇聚点”的权杖（红石激光、蛛网光束、附魔连线、命令反馈线、Omega 吸收汇聚点）。
     *  第三人称偏移仅在非第一人称自持（第三人称/其他持有者）时叠加。 */
    public static Vec3 applyLineEmitterOffset(net.minecraft.world.entity.LivingEntity emitter, Vec3 base) {
        double ox = cn.autoforged.joes_addons_for_abmc.config.ModConfig.CONVERGE_OFFSET_X.get();
        double oy = cn.autoforged.joes_addons_for_abmc.config.ModConfig.CONVERGE_OFFSET_Y.get();
        double oz = cn.autoforged.joes_addons_for_abmc.config.ModConfig.CONVERGE_OFFSET_Z.get();
        double tox = cn.autoforged.joes_addons_for_abmc.config.ModConfig.THIRD_PERSON_OFFSET_X.get();
        double toy = cn.autoforged.joes_addons_for_abmc.config.ModConfig.THIRD_PERSON_OFFSET_Y.get();
        double toz = cn.autoforged.joes_addons_for_abmc.config.ModConfig.THIRD_PERSON_OFFSET_Z.get();
        if (ox == 0 && oy == 0 && oz == 0 && tox == 0 && toy == 0 && toz == 0) return base;
        boolean firstPersonSelf = false;
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            firstPersonSelf = mc != null && mc.player != null
                && mc.player.getUUID().equals(emitter.getUUID())
                && mc.options.getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON;
        } catch (Throwable ignored) {
            // 专用服务器等客户端不可用场景下不叠加第三人称偏移
        }
        Vec3 result = applyHeadOffset(emitter, base, ox, oy, oz);
        // 第三人称 / 其他持有者场景：额外叠加“第三人称相对第一人称”的偏移（同样相对头部朝向），
        // 用于微调两种视角下权杖渲染位置（如高度）的差异。
        if (!firstPersonSelf && (tox != 0 || toy != 0 || toz != 0)) {
            result = applyHeadOffset(emitter, result, tox, toy, toz);
        }
        return result;
    }

    /**
     * 以红石粒子渲染整条折线射线。
     * 从发射者眼位起完整渲染所有粒子，不区分第一人称/第三人称/女仆发射，
     * 不再有“第一人称时近处粒子不渲染”的跳过机制。
     */
    private static void spawnRedstoneBeamParticles(ServerLevel level, LivingEntity shooter, List<Vec3> points) {
        if (points.size() < 2) return;
        org.joml.Vector3f red = new Vec3(0.85, 0.1, 0.1).toVector3f();
        int spawned = 0;
        for (int i = 0; i + 1 < points.size(); i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            double len = a.distanceTo(b);
            int n = Math.min((int) Math.floor(len), 128);
            for (int k = 0; k <= n && spawned < 192; k++) {
                double t = n == 0 ? 0.0 : (double) k / n;
                Vec3 p = a.lerp(b, t);
                level.sendParticles(new DustParticleOptions(red, 1.2F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
                spawned++;
            }
        }
    }

    /** 两碰撞箱之间的最近距离平方（发生重叠时为 0）。 */
    private static double aabbDistanceSqr(AABB a, AABB b) {
        double dx = a.maxX < b.minX ? b.minX - a.maxX : (a.minX > b.maxX ? a.minX - b.maxX : 0.0);
        double dy = a.maxY < b.minY ? b.minY - a.maxY : (a.minY > b.maxY ? a.minY - b.maxY : 0.0);
        double dz = a.maxZ < b.minZ ? b.minZ - a.maxZ : (a.minZ > b.maxZ ? a.minZ - b.maxZ : 0.0);
        return dx * dx + dy * dy + dz * dz;
    }

    /** 射线检测玩家瞄准的生物（排除自己与玩家），返回最近的命中者。
     *  末影龙为多部件实体，射线打中的是 {@link EnderDragonPart}，
     *  此处将其解析为父实体 {@link EnderDragon}。 */
    private static LivingEntity raycastAbsorbTarget(ServerPlayer sp, ServerLevel level) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle();
        AABB searchBox = sp.getBoundingBox()
            .expandTowards(look.scale(OMEGA_ABSORB_RANGE))
            .inflate(1.0);
        LivingEntity best = null;
        double bestDist = OMEGA_ABSORB_RANGE * OMEGA_ABSORB_RANGE;
        for (Entity e : level.getEntities(sp, searchBox,
            e -> (e instanceof LivingEntity || e instanceof EnderDragonPart)
                && e != sp && !(e instanceof Player) && e.isAlive())) {
            // 末影龙部件 → 解析为父实体
            LivingEntity target = e instanceof EnderDragonPart part
                ? part.getParent() : (LivingEntity) e;
            AABB bb = e.getBoundingBox().inflate(0.3);
            java.util.Optional<Vec3> hit = bb.clip(eye, eye.add(look.scale(OMEGA_ABSORB_RANGE)));
            if (hit.isPresent()) {
                double d = eye.distanceToSqr(hit.get());
                if (d < bestDist) {
                    bestDist = d;
                    best = target;
                }
            }
        }
        return best;
    }

    /**
     * 由生物生命上限计算吸收速度（格/秒），范围 0.9~4.5（基础 0.3~1.5 的 3 倍）：
     * 生命越高的生物越慢（凋灵趋近 0.9），生命越低越快（鸡趋近 4.5）。
     */
    private static double omegaAbsorbSpeed(LivingEntity target) {
        double maxHealth = target.getAttributeValue(Attributes.MAX_HEALTH);
        // 指数压缩：生命上限越大，速度越接近下限 0.3
        double base = 1.5 - 1.2 * (1.0 - Math.exp(-maxHealth / 40.0));
        // 每刻轻微随机波动（±10%），让速度更有“浮动感”
        double jitter = 0.9 + 0.2 * target.level().random.nextDouble();
        // 整体吸收速度提升为 3 倍
        return Math.max(0.3, Math.min(1.5, base * jitter)) * 3.0;
    }

    /**
     * 以生物为球心、半径 3 格内均匀采样 2~3 个端点（用于 debug_string 视觉）。
     * 端点数量随机取 2 或 3（3 个时组成三角形）。
     */
    private static double[][] sampleOmegaAbsorbSphere(LivingEntity target, RandomSource random) {
        int n = random.nextDouble() < 0.5 ? 2 : 3;
        double[][] pts = new double[n][3];
        double cx = target.getX();
        double cy = target.getY() + target.getBbHeight() * 0.5;
        double cz = target.getZ();
        for (int i = 0; i < n; i++) {
            double theta = random.nextDouble() * 2.0 * Math.PI;
            double phi = Math.acos(2.0 * random.nextDouble() - 1.0);
            // 三次根使球体内部分布均匀
            double r = 3.0 * Math.cbrt(random.nextDouble());
            pts[i][0] = cx + r * Math.sin(phi) * Math.cos(theta);
            pts[i][1] = cy + r * Math.sin(phi) * Math.sin(theta);
            pts[i][2] = cz + r * Math.cos(phi);
        }
        return pts;
    }

    public static boolean isPlayerBlocking(UUID uuid) {
        return blockingPlayers.contains(uuid);
    }

    public static void clearPlayerBlocking(UUID uuid) {
        blockingPlayers.remove(uuid);
    }

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getPlayer();
        if (player == null) return;

        BlockPos brokenPos = event.getPos();
        if (areaMiningInProgress.contains(brokenPos)) return;

        ItemStack mainStack = player.getMainHandItem();
        if (!(mainStack.getItem() instanceof StaffItem)) return;

        String blockType = mainStack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");

        if ("end_portal_frame".equals(blockType)) {
            event.setCanceled(true);
            return;
        }

        if ("netherite_block".equals(blockType)) {
            Level level = (Level) event.getLevel();
            List<BlockPos> surrounding = getAreaMiningBlocksNetherite(player, brokenPos);

            areaMiningInProgress.add(brokenPos);
            for (BlockPos pos : surrounding) {
                areaMiningInProgress.add(pos);
            }
            try {
                int blocksBroken = 1;
                for (BlockPos pos : surrounding) {
                    BlockState state = level.getBlockState(pos);
                    if (isUnbreakableForNetherite(state)) continue;
                    level.destroyBlock(pos, false, player, 512);
                    blocksBroken++;
                }
                hurtStaff(mainStack, blocksBroken, player, EquipmentSlot.MAINHAND);
            } finally {
                areaMiningInProgress.remove(brokenPos);
                areaMiningInProgress.removeAll(surrounding);
            }
            return;
        }

        if ("gold_block".equals(blockType) || "diamond_block".equals(blockType)) {
            List<BlockPos> surrounding = "diamond_block".equals(blockType)
                ? getAreaMiningBlocksDiamond(player, brokenPos)
                : getAreaMiningBlocks(player, brokenPos);
            areaBreakStaff((Level) event.getLevel(), player, mainStack, brokenPos, surrounding);
            return;
        }

        hurtStaff(mainStack, 1, player, EquipmentSlot.MAINHAND);
    }

    /** 金块/钻石权杖的区域挖掘：将 surrounding 内所有方块破坏并结算手感耐久 */
    private static void areaBreakStaff(Level level, Player player, ItemStack mainStack,
                                       BlockPos brokenPos, List<BlockPos> surrounding) {
        areaMiningInProgress.add(brokenPos);
        for (BlockPos pos : surrounding) {
            areaMiningInProgress.add(pos);
        }
        try {
            int blocksBroken = 1;
            for (BlockPos pos : surrounding) {
                BlockState state = level.getBlockState(pos);
                if (isUnbreakableOrForbidden(state)) continue;
                BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
                level.destroyBlock(pos, false, player, 512);
                boolean canHarvest = state.canHarvestBlock(level, pos, player);
                if (canHarvest) {
                    state.getBlock().playerDestroy(level, player, pos, state, blockEntity, mainStack.copy());
                }
                blocksBroken++;
            }
            hurtStaff(mainStack, blocksBroken, player, EquipmentSlot.MAINHAND);
        } finally {
            areaMiningInProgress.remove(brokenPos);
            areaMiningInProgress.removeAll(surrounding);
        }
    }

    /**
     * 屏障权杖：左键瞬间破坏任何方块（含命令方块、基岩、屏障），并像创造模式一样掉落方块。
     * 在服务端拦截后取消默认挖掘流程，手动执行破坏与掉落。
     */
    private static void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        ItemStack mainStack = player.getMainHandItem();
        if (!(mainStack.getItem() instanceof StaffItem)) return;
        String blockType = mainStack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        if (!"barrier".equals(blockType)) return;

        // 左右键同时按下触发整体平移时，抑制普通破坏
        if (barrierShiftSuppress.containsKey(player.getUUID())) return;

        event.setCanceled(true);
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;

        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        level.destroyBlock(pos, false, player, 512);
        Block.dropResources(state, level, pos, blockEntity, player, mainStack);
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
        hurtStaff(mainStack, 1, player, EquipmentSlot.MAINHAND);
    }

    private static boolean isUnbreakableOrForbidden(BlockState state) {
        if (state.isAir()) return true;
        if (state.getDestroySpeed(null, BlockPos.ZERO) < 0) return true;
        if (state.is(Blocks.DIAMOND_BLOCK)) return true;
        if (state.is(Blocks.NETHERITE_BLOCK)) return true;
        return false;
    }

    private static boolean isUnbreakableForNetherite(BlockState state) {
        if (state.getDestroySpeed(null, BlockPos.ZERO) < 0) return true;
        return false;
    }

    private static List<BlockPos> getAreaMiningBlocksNetherite(Player player, BlockPos center) {
        MobEffectInstance strength = player.getEffect(MobEffects.DAMAGE_BOOST);
        int levelBonus = strength != null ? strength.getAmplifier() + 1 : 0;

        int baseDim = 9 + levelBonus * 2;
        int halfLen = baseDim / 2;

        List<BlockPos> blocks = new ArrayList<>();

        for (int dx = -halfLen; dx <= halfLen; dx++) {
            for (int dz = -halfLen; dz <= halfLen; dz++) {
                for (int dy = 0; dy < baseDim; dy++) {
                    BlockPos offset = center.offset(dx, dy, dz);
                    if (!offset.equals(center)) {
                        blocks.add(offset);
                    }
                }
            }
        }

        return blocks;
    }

    private static List<BlockPos> getAreaMiningBlocks(Player player, BlockPos center) {
        Direction facing = player.getDirection();
        MobEffectInstance strength = player.getEffect(MobEffects.DAMAGE_BOOST);
        int levelBonus = strength != null ? strength.getAmplifier() + 1 : 0;

        int baseW = 3 + levelBonus;
        int baseH = 3 + levelBonus;
        int baseD = 2 + levelBonus;

        List<BlockPos> blocks = new ArrayList<>();

        for (int dx = -baseW / 2; dx <= baseW / 2; dx++) {
            for (int dy = -baseH / 2; dy <= baseH / 2; dy++) {
                for (int dd = 0; dd < baseD; dd++) {
                    BlockPos offset;
                    if (facing.getAxis() == Axis.X) {
                        offset = center.offset(
                            facing.getAxisDirection() == AxisDirection.POSITIVE ? dd : -dd,
                            dy,
                            dx
                        );
                    } else if (facing.getAxis() == Axis.Z) {
                        offset = center.offset(
                            dx,
                            dy,
                            facing.getAxisDirection() == AxisDirection.POSITIVE ? dd : -dd
                        );
                    } else {
                        offset = center.offset(
                            dx,
                            facing.getAxisDirection() == AxisDirection.POSITIVE ? dd : -dd,
                            dy
                        );
                    }
                    if (!offset.equals(center)) {
                        blocks.add(offset);
                    }
                }
            }
        }

        return blocks;
    }

    /**
     * 钻石权杖的区域挖掘：与金块权杖（getAreaMiningBlocks）基本一致，
     * 但每条边长都比金块多 2 格；力量药水的加成同理（在金块基础上始终保持 +2）。
     * 金块：宽 3、高 3、深 2（+力量加成）；钻石：宽 5、高 5、深 4（+力量加成）。
     */
    private static List<BlockPos> getAreaMiningBlocksDiamond(Player player, BlockPos center) {
        Direction facing = player.getDirection();
        MobEffectInstance strength = player.getEffect(MobEffects.DAMAGE_BOOST);
        int levelBonus = strength != null ? strength.getAmplifier() + 1 : 0;

        int baseW = 5 + levelBonus;
        int baseH = 5 + levelBonus;
        int baseD = 4 + levelBonus;

        List<BlockPos> blocks = new ArrayList<>();

        for (int dx = -baseW / 2; dx <= baseW / 2; dx++) {
            for (int dy = -baseH / 2; dy <= baseH / 2; dy++) {
                for (int dd = 0; dd < baseD; dd++) {
                    BlockPos offset;
                    if (facing.getAxis() == Axis.X) {
                        offset = center.offset(
                            facing.getAxisDirection() == AxisDirection.POSITIVE ? dd : -dd,
                            dy,
                            dx
                        );
                    } else if (facing.getAxis() == Axis.Z) {
                        offset = center.offset(
                            dx,
                            dy,
                            facing.getAxisDirection() == AxisDirection.POSITIVE ? dd : -dd
                        );
                    } else {
                        offset = center.offset(
                            dx,
                            facing.getAxisDirection() == AxisDirection.POSITIVE ? dd : -dd,
                            dy
                        );
                    }
                    if (!offset.equals(center)) {
                        blocks.add(offset);
                    }
                }
            }
        }

        return blocks;
    }

    private static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        // 玩家下线时若仍处于变形状态（物品/方块/壳），强制终止变形并清理跟踪状态：
        // 否则 TRANSMUTED_ENTITIES 会残留玩家 UUID，且变形实体随关卡卸载后跟踪数据丢失，
        // 重进后 onMobEffectAdded 会一直被 IN_TRANSMUTED 拦截，无法再次把自己变成物品/方块。
        if (event.getEntity() instanceof ServerPlayer sp) {
            forceEndPlayerTransmutationQuiet(sp);
        }
        COBWEB_PULLING.remove(uuid);
        COBWEB_PULL_END_TIME.remove(uuid);
        blockingPlayers.remove(uuid);
        barrierShiftSuppress.remove(uuid);
        LAPIS_FLIGHT_PLAYERS.remove(uuid);
        COMMAND_FLIGHT_PLAYERS.remove(uuid);
        HEROBRINE_FLIGHT_PLAYERS.remove(uuid);
        LAPIS_GRABBED_ENTITIES.remove(uuid);
        LAPIS_GRAB_XP_TIMERS.remove(uuid);
        BELL_STAFF_IMMUNITY.remove(uuid);
        PHYSICS_NIGHT_VISION_PLAYERS.remove(uuid);
        PortalStaffState existing = PORTAL_STAFF_STATES.remove(uuid);
        if (existing != null && event.getEntity().level() instanceof ServerLevel sl) {
            if (existing.previewId > 0) {
                Entity pe = sl.getEntity(existing.previewId);
                if (pe != null) pe.discard();
            }
            if (existing.pendingEntranceId > 0) {
                Entity e = sl.getEntity(existing.pendingEntranceId);
                if (e != null) e.discard();
            }
        }
        PORTAL_CONTACT_TIMERS.remove(uuid);
        PORTAL_EXIT_LOCKS.remove(uuid);
        PHYSICS_RETURN_TARGETS.remove(uuid);
        CREATIVE_GRANT_ORIGINAL.remove(uuid);
        FURNACE_ACCEL_MAP.values().removeIf(info -> info.playerId.equals(uuid));
        HEROBRINE_TELEPORT_COOLDOWNS.remove(uuid);
        // 红石块权杖：玩家下线时恢复其已被强充能的方块并清除会话状态
        RedstoneStaffState rss = REDSTONE_STAFF_STATES.remove(uuid);
        if (rss != null && event.getEntity().level() instanceof ServerLevel sl) {
            restoreRedstonePowered(sl, rss);
            rss.poweredBlocks.clear();
        }
        // 销毁该玩家所有指令 Text Display（护盾/抓取常驻 + 临时命令文本），
        // 防止退出存档时实体随区块保存，重进后残留不消失
        endShieldTextDisplay(uuid);
        endGrabTextDisplay(uuid);
        COMMAND_ACTIVE_TEXT_DISPLAYS.removeIf(e -> {
            if (e.owner != null && e.owner.getUUID().equals(uuid)) {
                if (e.display != null) e.display.discard();
                return true;
            }
            return false;
        });
    }

    private static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        CompoundTag oldData = event.getOriginal().getPersistentData();
        CompoundTag newData = serverPlayer.getPersistentData();
        if (oldData.getBoolean(ALL_VANILLA_ACHIEVEMENTS_REWARDED_TAG)) {
            newData.putBoolean(ALL_VANILLA_ACHIEVEMENTS_REWARDED_TAG, true);
        }
        if (oldData.getBoolean(ALL_ACHIEVEMENTS_REWARDED_TAG)) {
            newData.putBoolean(ALL_ACHIEVEMENTS_REWARDED_TAG, true);
        }
        if (oldData.getBoolean(STAFF_GIVEN_ON_START_TAG)) {
            newData.putBoolean(STAFF_GIVEN_ON_START_TAG, true);
        }
        // 复活走 Clone 事件而非 Login 事件：若不重发，客户端 HUD 会保留死亡前的旧模式，
        // 与服务端 persistent data 的实际模式不一致（例如显示“远程模式”实际却是“近战模式”）。
        PacketDistributor.sendToPlayer(serverPlayer,
            new cn.autoforged.joes_addons_for_abmc.network.EnchantStaffModePayload(
                newData.getBoolean(ENCHANT_STAFF_CRAZY_TAG)));
        PacketDistributor.sendToPlayer(serverPlayer,
            new cn.autoforged.joes_addons_for_abmc.network.HerobrineStaffModePayload(
                newData.getBoolean(HEROBRINE_STAFF_RANGED_TAG)));
        PacketDistributor.sendToPlayer(serverPlayer,
            new cn.autoforged.joes_addons_for_abmc.network.CommandStaffModePayload(
                getCommandStaffMode(newData)));
    }

    /**
     * 玩家切换游戏模式：变形药水“掷出者免疫”只由击败女巫Boss或
     * /jafa toggletransmutationdebug <true|false> 控制，不受游戏模式切换影响。
     * 容错机制：若玩家正处于变形状态（被变成物品/方块/壳）时切换到创造模式，
     * 则强行终止变形（复原玩家、清除药水效果、取消计时行为）。
     */
    private static void onPlayerChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        GameType cur = event.getCurrentGameMode();
        GameType next = event.getNewGameMode();
        if (cur == next) return;
        var data = serverPlayer.getPersistentData();
        // 切回生存/冒险模式时，若附魔台权杖仍处于疯狂模式则强制切回日常模式
        // （疯狂模式在非创造模式下不可用，见 handleEnchantStaffModeToggle）
        boolean creativeLike = next == GameType.CREATIVE || next == GameType.SPECTATOR;
        if (!creativeLike && data.getBoolean(ENCHANT_STAFF_CRAZY_TAG)) {
            data.putBoolean(ENCHANT_STAFF_CRAZY_TAG, false);
            PacketDistributor.sendToPlayer(serverPlayer,
                new cn.autoforged.joes_addons_for_abmc.network.EnchantStaffModePayload(false));
        }
        // 容错：变身后切到创造模式 → 强行终止变形。
        // 玩家此时正主动切到创造，因此不恢复其变身前的游玩模式（保留创造）。
        if (next == GameType.CREATIVE && isPlayerTransmuted(serverPlayer)) {
            forceEndPlayerTransmutation(serverPlayer, false);
            serverPlayer.displayClientMessage(Component.literal(
                "§e切换为创造模式，已强制解除变形效果。"), true);
        }
    }

    /**
     * 玩家是否正处于变形状态（被变成物品/下落方块/放置方块/生物壳/玩家空壳）。
     */
    private static boolean isPlayerTransmuted(ServerPlayer player) {
        UUID uid = player.getUUID();
        for (TransmutationData d : ITEM_TRANSMUTATIONS.values()) {
            if (uid.equals(d.playerUuid())) return true;
        }
        for (TransmutationData d : FALLING_TRANSMUTATIONS.values()) {
            if (uid.equals(d.playerUuid())) return true;
        }
        for (Map<BlockPos, java.util.List<TransmutationData>> dimMap : BLOCK_TRANSMUTATIONS.values()) {
            for (java.util.List<TransmutationData> list : dimMap.values()) {
                for (TransmutationData d : list) {
                    if (uid.equals(d.playerUuid())) return true;
                }
            }
        }
        for (LivingShellData d : LIVING_SHELLS.values()) {
            if (uid.equals(d.playerUuid())) return true;
        }
        return false;
    }

    /**
     * 强行终止某玩家的变形：销毁被变成的实体、清除倒计时与计时行为，
     * 复原玩家（可选恢复原始游玩模式、取消隐身、恢复第一人称）。
     *
     * @param restoreGameMode 是否恢复玩家变身前的游玩模式。
     *                        true=恢复原模式；false=保留当前模式（用于玩家主动切到创造时，
     *                        不把它改回变身前的模式）。
     */
    private static void forceEndPlayerTransmutation(ServerPlayer player, boolean restoreGameMode) {
        UUID uid = player.getUUID();
        // 物品：销毁跟随玩家的物品实体
        ITEM_TRANSMUTATIONS.entrySet().removeIf(e -> {
            if (uid.equals(e.getValue().playerUuid())) {
                Entity ent = player.serverLevel().getEntity(e.getKey());
                if (ent != null && ent.isAlive()) ent.discard();
                ITEM_TRANSMUTATION_POSITIONS.remove(e.getKey());
                return true;
            }
            return false;
        });
        // 下落方块：销毁跟随玩家的下落方块
        FALLING_TRANSMUTATIONS.entrySet().removeIf(e -> {
            if (uid.equals(e.getValue().playerUuid())) {
                Entity ent = player.serverLevel().getEntity(e.getKey());
                if (ent != null && ent.isAlive()) ent.discard();
                return true;
            }
            return false;
        });
        // 放置方块：移除该玩家的变身数据（方块本身保持不动）
        for (Map<BlockPos, java.util.List<TransmutationData>> dimMap : BLOCK_TRANSMUTATIONS.values()) {
            for (Map.Entry<BlockPos, java.util.List<TransmutationData>> e :
                    new java.util.ArrayList<>(dimMap.entrySet())) {
                java.util.List<TransmutationData> list = e.getValue();
                list.removeIf(d -> uid.equals(d.playerUuid()));
                if (list.isEmpty()) {
                    dimMap.remove(e.getKey());
                }
            }
        }
        BLOCK_TRANSMUTATIONS.values().removeIf(Map::isEmpty);
        // 生物壳/玩家空壳：销毁壳体
        LIVING_SHELLS.entrySet().removeIf(e -> {
            if (uid.equals(e.getValue().playerUuid())) {
                for (ServerLevel l : player.getServer().getAllLevels()) {
                    Entity ent = l.getEntity(e.getKey());
                    if (ent != null && ent.isAlive()) ent.discard();
                }
                return true;
            }
            return false;
        });
        // 复原玩家并清理跟踪状态
        if (restoreGameMode) {
            GameType original = PLAYER_ORIGINAL_GAMEMODE.getOrDefault(uid, GameType.SURVIVAL);
            player.setGameMode(original);
        }
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(ModMobEffects.TRANSMUTATION);
        player.setInvisible(false);
        player.fallDistance = 0.0F;
        restoreTransmutationScale(player);
        restoreTransmutationCamera(player);
        sendTransmutationState(player, false, -1);
        cleanupPlayerTransmutation(uid);
    }

    /**
     * 玩家下线时若仍处于变形状态，强制终止变形并清理跟踪状态。
     * 与 {@link #forceEndPlayerTransmutation} 的区别：不向已断开的客户端发送任何网络包，
     * 也不恢复游玩模式（存档会记录当前模式，重进后即为正常模式）。
     * 关键作用：清除 TRANSMUTED_ENTITIES 中残留的玩家 UUID，避免重进后 onMobEffectAdded 被
     * IN_TRANSMUTED 拦截，导致无论如何都无法再次把自己变成物品/方块。
     */
    private static void forceEndPlayerTransmutationQuiet(ServerPlayer player) {
        UUID uid = player.getUUID();
        // 物品：销毁跟随玩家的物品实体
        ITEM_TRANSMUTATIONS.entrySet().removeIf(e -> {
            if (uid.equals(e.getValue().playerUuid())) {
                Entity ent = player.serverLevel().getEntity(e.getKey());
                if (ent != null && ent.isAlive()) ent.discard();
                ITEM_TRANSMUTATION_POSITIONS.remove(e.getKey());
                return true;
            }
            return false;
        });
        // 下落方块：销毁跟随玩家的下落方块
        FALLING_TRANSMUTATIONS.entrySet().removeIf(e -> {
            if (uid.equals(e.getValue().playerUuid())) {
                Entity ent = player.serverLevel().getEntity(e.getKey());
                if (ent != null && ent.isAlive()) ent.discard();
                return true;
            }
            return false;
        });
        // 放置方块：移除该玩家的变身数据（方块本身保持不动）
        for (Map<BlockPos, java.util.List<TransmutationData>> dimMap : BLOCK_TRANSMUTATIONS.values()) {
            for (Map.Entry<BlockPos, java.util.List<TransmutationData>> e :
                    new java.util.ArrayList<>(dimMap.entrySet())) {
                java.util.List<TransmutationData> list = e.getValue();
                list.removeIf(d -> uid.equals(d.playerUuid()));
                if (list.isEmpty()) {
                    dimMap.remove(e.getKey());
                }
            }
        }
        BLOCK_TRANSMUTATIONS.values().removeIf(Map::isEmpty);
        // 生物壳/玩家空壳：销毁壳体
        LIVING_SHELLS.entrySet().removeIf(e -> {
            if (uid.equals(e.getValue().playerUuid())) {
                for (ServerLevel l : player.getServer().getAllLevels()) {
                    Entity ent = l.getEntity(e.getKey());
                    if (ent != null && ent.isAlive()) ent.discard();
                }
                return true;
            }
            return false;
        });
        // 复原玩家自身状态（无需发包）
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(ModMobEffects.TRANSMUTATION);
        player.setInvisible(false);
        player.fallDistance = 0.0F;
        restoreTransmutationScale(player);
        cleanupPlayerTransmutation(uid);
    }

    /**
     * 玩家是否对自己丢出的变形药水免疫：
     * 由“掷出者免疫”标记决定（击败女巫Boss 授予，或 /jafa toggletransmutationdebug true 设置）。
     */
    private static boolean isThrowerImmuneToPotion(LivingEntity entity) {
        if (entity instanceof ServerPlayer sp) {
            return sp.getPersistentData().getBoolean(TRANSFORM_POTION_IMMUNE_TAG);
        }
        return false;
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        LuckyDimensionBlockEntity.clearUsedTextures();
        FURNACE_ACCEL_MAP.clear();
    }

    /**
     * 区块加载时清理上个存档遗留的指令 Text Display 实体：玩家退出存档时未销毁、随区块保存下来的残留。
     * 只清理带 {@link #COMMAND_TEXT_TAG} 标记、且当前不在「跟随玩家列表」中的实体，避免误删在线玩家的指令文本。
     */
    private static void onChunkLoad(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        net.minecraft.world.level.chunk.ChunkAccess chunk = event.getChunk();
        net.minecraft.world.phys.AABB aabb = new net.minecraft.world.phys.AABB(
            chunk.getPos().getMinBlockX(), serverLevel.getMinBuildHeight(), chunk.getPos().getMinBlockZ(),
            chunk.getPos().getMaxBlockX() + 1.0, serverLevel.getMaxBuildHeight(), chunk.getPos().getMaxBlockZ() + 1.0);
        for (net.minecraft.world.entity.Entity e : serverLevel.getEntities(
                (net.minecraft.world.entity.Entity) null, aabb,
                ent -> ent instanceof net.minecraft.world.entity.Display.TextDisplay
                    && ent.getPersistentData().getBoolean(COMMAND_TEXT_TAG))) {
            boolean tracked = false;
            for (CommandTextDisplayEntry entry : COMMAND_ACTIVE_TEXT_DISPLAYS) {
                if (entry.display == e) { tracked = true; break; }
            }
            if (!tracked) e.discard();
        }
    }

    /** 蛛丝免疫期内的摔落：直接取消 LivingFallEvent（摔落伤害的根事件），
     *  与 onLivingIncomingDamage 的伤害源免疫配合，确保玩家被拉高或下落期间不掉血。 */
    private static void onCobwebPullFall(net.neoforged.neoforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp
            && isCobwebPullDamageImmune(sp.getUUID())) {
            event.setCanceled(true);
        }
    }

    private static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();

        // --- 变形玩家伤害过滤：物品/方块/壳/生物形态仅允许对应来源的伤害，其余免疫
        //     （如同创造模式，不体现受击反馈、身体不变红）。
        if (target instanceof Player transmutedPlayer && TRANSMUTED_ENTITIES.contains(transmutedPlayer.getUUID())) {
            DamageSource tSource = event.getSource();
            // 变形“体验卡到期/被变实体死亡”的结算伤害：必须穿透伤害免疫，确保玩家能被正常击杀
            if (isTransmutationExpiredDamage(tSource)) {
                return;
            }
            PlayerTransmutationInfo info = PLAYER_TRANSMUTATION_INFO.get(transmutedPlayer.getUUID());
            if (info != null) {
                switch (info.form()) {
                    case ITEM -> {
                        // 物品：只受仙人掌、虚空、爆炸、火焰等能伤害到物品的伤害
                        if (!isItemFormDamageAllowed(tSource)) {
                            event.setCanceled(true);
                        }
                    }
                    case BLOCK -> {
                        // 方块：只受爆炸类伤害和来源于挖掘该方块对应工具的伤害
                        if (!isBlockFormDamageAllowed(transmutedPlayer.level(),
                            transmutedPlayer.blockPosition(), info.itemType(), tSource)) {
                            event.setCanceled(true);
                        }
                    }
                    default -> event.setCanceled(true); // 玩家空壳/生物壳：完全免疫
                }
            } else {
                event.setCanceled(true); // 未知形态：免疫
            }
            return;
        }

        // --- 附魔状态效果：生物持有物品时，其“魔咒状态效果”视作该物品的等效附魔（消失诅咒除外），
        //     同种附魔与物品自身等级叠加，使近战攻击伤害等同于“等效附魔武器”造成的伤害（物品不被真正附魔）。
        applyEnchantmentStatusEffectDamage(event);

        // 附魔生物特殊效果：受击侧（耐久/火焰附加/击退/荆棘/保护类减伤/摔落缓冲）
        handleEnchantEffectsOnHit(event);
        if (event.isCanceled()) return;
        // 附魔生物特殊效果：攻击者侧（破甲/引雷/时运/致密/快速装填）
        handleEnchantAttackerHit(event);

        // 蛛丝拉动期间（及断开后 3 秒内）免疫摔落伤害与动能伤害（被拉飞的玩家不会被摔伤/被移动方块砸伤）。
        if (target instanceof ServerPlayer pulledPly
            && isCobwebPullDamageImmune(pulledPly.getUUID())
            && isFallKineticDamage(event.getSource())) {
            event.setCanceled(true);
            return;
        }

        // --- HIM STAFF: a direct melee attack with the Him staff deals damage equal
        // to the target's current health. Setting the amount here (before the armor /
        // magic / resistance reductions are captured) lets those reductions apply.
        DamageSource incomingSource = event.getSource();
        Entity incomingDirect = incomingSource.getDirectEntity();
        Entity incomingEntity = incomingSource.getEntity();
        if (incomingEntity == incomingDirect && incomingEntity instanceof LivingEntity attacker
            && !(target instanceof Player blockingPly && blockingPlayers.contains(blockingPly.getUUID()))) {
            ItemStack weapon = attacker.getMainHandItem();
            if (weapon.getItem() instanceof StaffItem) {
                String blockType = effectiveStaffBlockType(weapon, attacker);
                if ("herobrine_head".equals(blockType)) {
                    event.setAmount((float) Math.max(target.getHealth(), 5.0));
                }
            }
        }

        // --- COMMAND BLOCK STAFF (抓取/AI切换/护盾模式): 与传送门权杖一致，左键无法通过近战攻击实体。
        //     抓取模式下左键用于持续拉拽目标，AI切换/护盾模式下左键也不应造成近战伤害，
        //     故取消玩家用非击杀模式命令方块权杖发起的近战攻击伤害。
        if (incomingEntity == incomingDirect && incomingEntity instanceof Player cmdAttacker) {
            ItemStack weapon = cmdAttacker.getMainHandItem();
            if (weapon.getItem() instanceof StaffItem) {
                String blockType = effectiveStaffBlockType(weapon, cmdAttacker);
                if ("command_block".equals(blockType)
                    && getCommandStaffMode(cmdAttacker.getPersistentData()) != COMMAND_STAFF_MODE_KILL) {
                    event.setCanceled(true);
                    return;
                }
            }
        }

        if (!(target instanceof Player player)) return;
        if (!blockingPlayers.contains(player.getUUID())) return;

        DamageSource source = event.getContainer().getSource();

        ItemStack blockingItem = null;
        InteractionHand blockingHand = null;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof GlisteringMelonKnifeItem || stack.getItem() instanceof StaffItem) {
                blockingItem = stack;
                blockingHand = hand;
                break;
            }
        }
        if (blockingItem == null) return;

        if (isProjectileDamage(source)) {
            Entity projectile = source.getDirectEntity();
            if (projectile != null && isBlockableProjectile(projectile)) {
                event.setCanceled(true);
                applyBlockEffects(player, blockingItem, blockingHand, source, projectile);
                return;
            }
        }

        if (source.getDirectEntity() instanceof LivingEntity) {
            event.setCanceled(true);
            applyMeleeBlockEffects(player, blockingItem, blockingHand);
            return;
        }
    }

    // 物品形态允许的伤害来源：仙人掌、虚空、爆炸、火焰等能伤害到物品的伤害
    private static boolean isItemFormDamageAllowed(DamageSource source) {
        return source.is(DamageTypes.CACTUS)
            || source.is(DamageTypes.FELL_OUT_OF_WORLD)
            || source.is(DamageTypeTags.IS_FIRE)
            || source.is(DamageTypeTags.IS_EXPLOSION);
    }

    // 方块形态允许的伤害来源：爆炸类伤害 + 来源于挖掘该方块对应工具的伤害
    private static boolean isBlockFormDamageAllowed(Level level, BlockPos pos, String itemType, DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return true;
        ResourceLocation rl = ResourceLocation.tryParse(itemType);
        if (rl == null) return false;
        Block block = BuiltInRegistries.BLOCK.get(rl);
        if (block == null || block == Blocks.AIR) return false;
        return isBlockToolDamage(level, pos, block.defaultBlockState(), source);
    }

    // 工具挖掘伤害：攻击者直接近战且手持工具对该方块“有效”。
    // 硬方块（如原木/石头）需持有对应工具（斧/镐等）；软方块（如树叶）任何工具都能造成伤害。
    private static boolean isBlockToolDamage(Level level, BlockPos pos, BlockState state, DamageSource source) {
        if (!(source.getDirectEntity() instanceof LivingEntity attacker)) return false;
        ItemStack stack = attacker.getMainHandItem();
        if (stack.isCorrectToolForDrops(state)) return true;
        float destroySpeed = state.getDestroySpeed(level, pos);
        return destroySpeed >= 0.0F && destroySpeed <= 0.5F;
    }

    // 变形“体验卡到期/被变实体死亡”的结算伤害：必须穿透伤害免疫，确保玩家能被正常击杀
    private static boolean isTransmutationExpiredDamage(DamageSource source) {
        return source.is(ModDamageTypes.TRANSMUTATION_ITEM_EXPIRED.getKey())
            || source.is(ModDamageTypes.TRANSMUTATION_BLOCK_EXPIRED.getKey())
            || source.is(ModDamageTypes.TRANSMUTATION_PLAYER_EXPIRED.getKey())
            || source.is(ModDamageTypes.TRANSMUTATION_BIOM_EXPIRED.getKey());
    }

    /**
     * 附魔状态效果 -> 武器等效附魔（近战伤害加成）：
     * 生物持有物品时，其身上的“魔咒状态效果”视作该物品的额外附魔（消失诅咒除外），
     * 且与物品自身同种附魔的等级叠加；若主手为空（空手），则直接视作生物自带的附魔特点，
     * 如同手持一把仅含该附魔的武器攻击。仅对直接近战（MOB_ATTACK/PLAYER_ATTACK）生效，
     * 玩家侧按攻击强度缩放，与原版附魔伤害计算一致。物品本身不被真正附魔。
     */
    private static void applyEnchantmentStatusEffectDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) return;
        DamageSource source = event.getSource();
        // 仅处理“直接近战攻击”：直接来源与伤害来源实体均为攻击者本人（排除箭矢/弹射物等）
        if (source.getDirectEntity() != source.getEntity()) return;
        if (!(source.getDirectEntity() instanceof LivingEntity attacker)) return;
        if (attacker == event.getEntity()) return;
        if (!source.is(DamageTypes.MOB_ATTACK) && !source.is(DamageTypes.PLAYER_ATTACK)) return;

        // 收集攻击者身上的魔咒状态效果（按注册 ID 的 path 映射到原版附魔，消失诅咒除外）
        java.util.List<Holder<Enchantment>> boostEnchants = new java.util.ArrayList<>();
        java.util.List<Integer> boostLevels = new java.util.ArrayList<>();
        for (MobEffectInstance active : attacker.getActiveEffects()) {
            Holder<Enchantment> ench = enchantmentOfEffect(serverLevel, active.getEffect().value());
            if (ench == null || ench.is(Enchantments.VANISHING_CURSE)) continue;
            boostEnchants.add(ench);
            boostLevels.add(active.getAmplifier() + 1);
        }
        if (boostEnchants.isEmpty()) return;

        ItemStack weapon = attacker.getWeaponItem();
        // 权杖自带特殊攻击机制，不套用等效附魔；空手生物（getWeaponItem 为空）则直接应用
        if (weapon.getItem() instanceof StaffItem) return;

        // 构建“虚拟武器”：原武器附魔 + 魔咒状态效果附魔（同种附魔等级叠加、不封顶）
        ItemEnchantments existing = weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(existing);
        for (int i = 0; i < boostEnchants.size(); i++) {
            int combined = existing.getLevel(boostEnchants.get(i)) + boostLevels.get(i);
            mutable.set(boostEnchants.get(i), combined);
        }
        // 空手时 weapon.copy() 会返回 EMPTY 单例（且 isEmpty() 为 true，某些代码路径会跳过读取附魔），
        // 改用非空的 STICK 物品栈承载等效附魔；EnchantmentHelper 读取栈上附魔计算加成，不依赖物品类型。
        ItemStack virtual = weapon.isEmpty() ? new ItemStack(Items.STICK) : weapon.copy();
        virtual.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());

        // 用原版附魔伤害计算器求差值：等效附魔伤害 - 原武器自带附魔伤害
        float originalBonus = EnchantmentHelper.modifyDamage(serverLevel, weapon, event.getEntity(), source, 0.0F);
        float virtualBonus = EnchantmentHelper.modifyDamage(serverLevel, virtual, event.getEntity(), source, 0.0F);
        float additional = virtualBonus - originalBonus;
        if (additional <= 0.0F) return;

        // 玩家侧：附魔伤害按攻击强度缩放（与原版一致）
        if (attacker instanceof ServerPlayer sp) {
            additional *= sp.getAttackStrengthScale(0.5F);
            if (additional <= 0.0F) return;
        }

        event.setAmount(event.getAmount() + additional);
    }

    /** 将“魔咒状态效果”按注册 ID 的 path 映射为对应的原版附魔 Holder（如 sharpness -> minecraft:sharpness）。 */
    private static Holder<Enchantment> enchantmentOfEffect(ServerLevel level, MobEffect effect) {
        ResourceLocation effectId = BuiltInRegistries.MOB_EFFECT.getKey(effect);
        if (effectId == null) return null;
        Registry<Enchantment> registry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        return registry.getHolder(ResourceLocation.withDefaultNamespace(effectId.getPath())).orElse(null);
    }

    // ==================== 附魔生物特殊效果 ====================

    /** 读取生物身上“魔咒状态效果”映射到的原版附魔等级（amplifier+1），无则返回 0。 */
    private static int enchantLevelOf(LivingEntity mob, ResourceKey<Enchantment> ench) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) return 0;
        for (MobEffectInstance inst : mob.getActiveEffects()) {
            Holder<Enchantment> e = enchantmentOfEffect(serverLevel, inst.getEffect().value());
            if (e != null && e.is(ench)) return inst.getAmplifier() + 1;
        }
        return 0;
    }

    /** 生物是否拥有指定附魔对应的“魔咒状态效果”。 */
    private static boolean hasEnchantEffect(LivingEntity mob, ResourceKey<Enchantment> ench) {
        return enchantLevelOf(mob, ench) > 0;
    }

    /** 供 mixin 借用：读取生物身上“魔咒状态效果”映射到的附魔等级（0 表示无）。 */
    public static int getEnchantLevel(LivingEntity mob, ResourceKey<Enchantment> ench) {
        return enchantLevelOf(mob, ench);
    }

    /** 概率钳制到 [0,1]。 */
    private static double clampProbability(double p) {
        return p >= 1.0 ? 1.0 : Math.max(0.0, p);
    }

    /**
     * 附魔生物·受击类效果入口（在 onLivingIncomingDamage 中调用）：
     * 目标（被打者）拥有魔咒状态效果时，反弹给攻击者或削减自身伤害。
     * 包括：耐久（概率不扣血）、火焰附加（点燃攻击者）、击退（击退攻击者）、
     * 荆棘（反伤）、保护类（减伤）、摔落缓冲（减摔落伤害）。
     */
    private static void handleEnchantEffectsOnHit(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel serverLevel)) return;
        if (ENCH_BREACH_APPLYING.contains(target.getId())) return; // 破甲真实伤害结算中，跳过防递归

        // --- 耐久：受到攻击时有 (等级/100)% 概率不扣除生命值 ---
        int unbreaking = enchantLevelOf(target, Enchantments.UNBREAKING);
        if (unbreaking > 0 && serverLevel.random.nextFloat() < clampProbability(unbreaking / 100.0)) {
            event.setCanceled(true);
            return;
        }

        DamageSource source = event.getSource();
        Entity direct = source.getDirectEntity();
        LivingEntity attacker = (direct instanceof LivingEntity l && l != target) ? l : null;

        // --- 火焰附加：被攻击的生物点燃攻击者，燃烧时长按火焰附加等级（等级×4 秒）计算 ---
        int fireAspect = enchantLevelOf(target, Enchantments.FIRE_ASPECT);
        if (fireAspect > 0 && attacker != null && !attacker.fireImmune()) {
            attacker.setRemainingFireTicks(fireAspect * 4 * 20);
        }

        // --- 击退：拥有击退效果的目标被攻击时，把攻击施加者击退，强度按击退等级计算 ---
        // 走模组已验证可用的击退通道（hurtMarked + 直接给玩家发包），对玩家/生物均生效，
        // 且即使只是单方面被攻击也会把攻击者明确推开。
        int knockback = enchantLevelOf(target, Enchantments.KNOCKBACK);
        if (knockback > 0 && attacker != null) {
            applyKnockbackAway(attacker, (double) knockback * 0.5, target);
        }

        // --- 荆棘：被攻击时按荆棘等级概率反伤给攻击者 ---
        int thorns = enchantLevelOf(target, Enchantments.THORNS);
        if (thorns > 0 && attacker != null) {
            float chance = Math.min(1.0F, 0.15F * thorns);
            if (serverLevel.random.nextFloat() < chance) {
                int reflect = 1 + serverLevel.random.nextInt(1 + Mth.ceil(0.5F * thorns));
                attacker.hurt(target.damageSources().magic(), reflect);
            }
        }

        // --- 保护类护甲附魔减伤（保护/火焰保护/爆炸保护/弹射物保护）---
        int protection = enchantLevelOf(target, Enchantments.PROTECTION);
        int fireProt = enchantLevelOf(target, Enchantments.FIRE_PROTECTION);
        int blastProt = enchantLevelOf(target, Enchantments.BLAST_PROTECTION);
        int projProt = enchantLevelOf(target, Enchantments.PROJECTILE_PROTECTION);
        int epf = 0;
        epf += protection;
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) epf += fireProt;
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) epf += blastProt;
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) epf += projProt;
        if (epf > 0) {
            int capped = Math.min(20, epf);
            float reduce = event.getAmount() * capped / 25.0F;
            event.setAmount(Math.max(0.0F, event.getAmount() - reduce));
        }

        // --- 摔落缓冲：减轻摔落伤害（等级越高减得越多，最大 80%）---
        int feather = enchantLevelOf(target, Enchantments.FEATHER_FALLING);
        if (feather > 0 && source.is(DamageTypes.FALL)) {
            float factor = Math.min(0.8F, feather * 0.12F);
            event.setAmount(event.getAmount() * (1.0F - factor));
        }
    }

    /** 用模组已验证可用的方式施加击退：直接设速度 + hasImpulse + hurtMarked，并给玩家直发运动包，
     *  使 victim 沿“远离 fromEntity”的方向弹开，同时尊重目标的击退抗性。
     *  对玩家也生效（仅靠 hasImpulse 不足以让客户端玩家肉眼可见地被推开）。 */
    private static void applyKnockbackAway(LivingEntity victim, double strength, net.minecraft.world.entity.Entity fromEntity) {
        if (strength <= 0.0) return;
        strength *= 1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (strength <= 0.0) return;
        double dx = victim.getX() - fromEntity.getX();
        double dz = victim.getZ() - fromEntity.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-5) {
            dx = 0.0;
            dz = -1.0;
        } else {
            dx /= len;
            dz /= len;
        }
        Vec3 dir = new Vec3(dx, 0.0, dz).normalize().scale(strength);
        Vec3 cur = victim.getDeltaMovement();
        // dir 已指向“远离 fromEntity”的方向，故叠加（加法）而不是减去
        victim.setDeltaMovement(
            cur.x / 2.0 + dir.x,
            victim.onGround() ? Math.min(0.4, cur.y / 2.0 + strength) : cur.y,
            cur.z / 2.0 + dir.z
        );
        victim.hasImpulse = true;
        victim.hurtMarked = true;
        if (victim instanceof ServerPlayer serverVictim) {
            serverVictim.connection.send(new ClientboundSetEntityMotionPacket(victim));
        }
    }

    /**
     * 附魔生物·被攻击时的攻击者侧效果（攻击者=持有魔咒状态效果的生物）：
     * 破甲（概率真实伤害）、引雷（概率召雷且不伤自己）、时运（攻击时概率使目标掉落一份战利品）、
     * 致密（下坠增伤）、快速装填（减少目标无敌帧）。
     */
    private static void handleEnchantAttackerHit(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel serverLevel)) return;
        DamageSource source = event.getSource();
        // 仅近战直接攻击（排除弹射物/范围伤害）
        if (source.getDirectEntity() != source.getEntity()) return;
        if (!(source.getDirectEntity() instanceof LivingEntity attacker)) return;
        if (attacker == target) return;

        // --- 击退：攻击者（拥有击退状态效果）近战攻击时，把目标击退（等同原版击退附魔，对玩家同样生效）---
        int attackKnockback = enchantLevelOf(attacker, Enchantments.KNOCKBACK);
        if (attackKnockback > 0) {
            applyKnockbackAway(target, (double) attackKnockback * 0.5, attacker);
        }

        // --- 火焰附加：攻击者（拥有火焰附加状态效果）近战攻击时，有 (等级/100)% 概率点燃目标 ---
        int fireAspectAtk = enchantLevelOf(attacker, Enchantments.FIRE_ASPECT);
        if (fireAspectAtk > 0 && !target.fireImmune()
                && serverLevel.random.nextFloat() < clampProbability(fireAspectAtk / 100.0)) {
            target.setRemainingFireTicks(fireAspectAtk * 4 * 20);
        }

        // --- 破甲：概率造成无视保护类魔咒/抗性提升的真实伤害 ---
        int breach = enchantLevelOf(attacker, Enchantments.BREACH);
        if (breach > 0 && serverLevel.random.nextFloat() < clampProbability(breach / 100.0)) {
            float raw = event.getAmount();
            event.setCanceled(true);
            if (!ENCH_BREACH_APPLYING.contains(target.getId())) {
                ENCH_BREACH_APPLYING.add(target.getId());
                try {
                    target.hurt(serverLevel.damageSources().genericKill(), raw);
                } finally {
                    ENCH_BREACH_APPLYING.remove(target.getId());
                }
            }
            return;
        }

        // --- 引雷：概率在被攻击者坐标生成闪电，不伤及攻击者 ---
        int channeling = enchantLevelOf(attacker, Enchantments.CHANNELING);
        if (channeling > 0 && serverLevel.random.nextFloat() < clampProbability(channeling / 100.0)) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (bolt != null) {
                bolt.moveTo(target.getX(), target.getY(), target.getZ());
                bolt.getPersistentData().putUUID(CHANNELING_LIGHTNING_OWNER, attacker.getUUID());
                serverLevel.addFreshEntity(bolt);
            }
        }

        // --- 时运：攻击者持有时运状态效果时，攻击目标有概率模拟“目标被攻击者杀死”产出其死亡掉落 ---
        int fortune = enchantLevelOf(attacker, Enchantments.FORTUNE);
        if (fortune > 0 && !(target instanceof Player)
                && serverLevel.random.nextFloat() < clampProbability(fortune / 100.0)) {
            simulateKillDrop(serverLevel, target, source);
        }

        // --- 致密：下坠过程中造成的伤害按重锤致密公式增算 ---
        int density = enchantLevelOf(attacker, Enchantments.DENSITY);
        if (density > 0 && attacker.fallDistance > 1.5F) {
            float fall = Math.max(0.0F, attacker.fallDistance - 1.5F);
            float add = density * 0.5F * fall;
            if (add > 0) event.setAmount(event.getAmount() + add);
        }

        // --- 快速装填：根据等级减少被攻击目标的无敌帧 ---
        int quickCharge = enchantLevelOf(attacker, Enchantments.QUICK_CHARGE);
        if (quickCharge > 0) {
            int cur = event.getContainer().getPostAttackInvulnerabilityTicks();
            event.getContainer().setPostAttackInvulnerabilityTicks(Math.max(0, cur - quickCharge));
        }
    }

    /** 时运“攻击时”触发：模拟“目标被攻击者杀死”的情景，按其自身死亡战利品表滚动一次，
     *  并把产出的掉落物全部生成到世界（铁傀儡 → 铁锭/虞美人，而非无关的钻石/金子）。
     *  上下文按原版 dropFromLootTable 补全（DAMAGE_SOURCE/攻击者/最后伤害者/目标自身战利品表种子）。 */
    private static void simulateKillDrop(ServerLevel level, LivingEntity target, DamageSource source) {
        try {
            var lootKey = target.getLootTable();
            var lootTable = level.getServer().reloadableRegistries().getLootTable(lootKey);
            LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, target)
                .withParameter(LootContextParams.ORIGIN, target.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity());
            if (source.getEntity() instanceof Player p) {
                builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, p).withLuck(p.getLuck());
            }
            LootParams params = builder.create(LootContextParamSets.ENTITY);
            java.util.List<ItemStack> loot = lootTable.getRandomItems(params, target.getLootTableSeed());
            for (ItemStack stack : loot) {
                if (stack == null || stack.isEmpty()) continue;
                ItemEntity item = new ItemEntity(level, target.getX(), target.getY() + 0.5, target.getZ(), stack.copy());
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
            }
        } catch (Exception e) {
            LOGGER.error("[Enchant] 时运掉落模拟失败 target={} source={}", target.getType(), source, e);
        }
    }

    /** 附魔生物·消失诅咒（死亡不掉落任何物品）与精准采集（掉落刷怪蛋）。时运已改为攻击时生效（见 handleEnchantAttackerHit）。 */
    private static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) return;
        LivingEntity dead = event.getEntity();
        DamageSource source = event.getSource();

        if (enchantLevelOf(dead, Enchantments.VANISHING_CURSE) > 0) {
            event.getDrops().clear();
        }

        Entity causer = source.getEntity();

        int silk = (causer instanceof LivingEntity atk && causer != dead) ? enchantLevelOf(atk, Enchantments.SILK_TOUCH) : 0;
        if (silk > 0 && dead.getType() != EntityType.PLAYER) {
            event.getDrops().clear();
            SpawnEggItem spawnEgg = net.minecraft.world.item.SpawnEggItem.byId(dead.getType());
            if (spawnEgg != null) {
                event.getDrops().add(new ItemEntity(serverLevel, dead.getX(), dead.getY(), dead.getZ(), new ItemStack(spawnEgg)));
            }
        }
    }

    /** 附魔生物·经验修补：优先消耗经验值回复饱食度→饱和度，两者都满才恢复吸经验。 */
    private static void onPlayerPickupXp(PlayerXpEvent.PickupXp event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel)) return;
        int mending = enchantLevelOf(player, Enchantments.MENDING);
        if (mending <= 0) return;
        int xp = event.getOrb().getValue();
        if (xp <= 0) return;
        FoodData food = player.getFoodData();
        int hungerNeed = 20 - food.getFoodLevel();
        int giveHunger = Math.min(hungerNeed, xp);
        if (giveHunger > 0) food.eat(giveHunger, 0.0F);
        int remain = xp - giveHunger;
        if (remain > 0) {
            float satNeed = 20.0F - food.getSaturationLevel();
            float giveSat = Math.min(satNeed, remain);
            if (giveSat > 0) food.setSaturation(food.getSaturationLevel() + giveSat);
            remain -= (int) giveSat;
        }
        // 经验被完全用来回复饱食/饱和度 → 取消原版加经验；若还有剩余（已回满）则正常吸收
        if (remain <= 0) event.setCanceled(true);
    }

    /**
     * 附魔生物·远程类效果：
     * 多重射击（命中时按弩分裂支数、共 3 支角），火矢（点燃目标/爆炸弹射物必带火焰爆炸），
     * 穿透（命中后有概率保持原速度与角度继续前进）。
     */
    private static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) return;
        Projectile proj = event.getProjectile();
        Entity owner = proj.getOwner();
        if (!(owner instanceof LivingEntity shooter)) return;
        HitResult ray = event.getRayTraceResult();

        // 随机/定点/定向传送药水：命中（方块面/实体）即生成一对"单向传送门"，用投掷者尺寸作空间参考
        if (proj instanceof ThrownPotion tp
            && (isRandomTransportPotion(tp.getItem())
                || isPointTransportPotion(tp.getItem())
                || isDirectionalTransportPotion(tp.getItem()))) {
            boolean created = tryCreateRandomPortalPair(serverLevel, shooter, tp.getItem(), ray);
            // 落地/击中：始终播放玻璃瓶碎裂音效；仅当创建失败（找不到传送点/实体已死亡卸载/超边境）时才播放碎裂动画粒子
            double px = proj.getX(), py = proj.getY(), pz = proj.getZ();
            serverLevel.playSound(null, px, py, pz,
                net.minecraft.sounds.SoundEvents.GLASS_BREAK,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
            if (!created) {
                serverLevel.sendParticles(
                    new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM,
                        new ItemStack(Items.GLASS_BOTTLE)),
                    px, py, pz, 6, 0.3, 0.3, 0.3, 0.1);
            }
            event.setCanceled(true);
            proj.discard();
            return;
        }

        if (ray.getType() != HitResult.Type.ENTITY) return;
        Entity victim = ((EntityHitResult) ray).getEntity();
        if (!(victim instanceof LivingEntity livingVictim) || livingVictim == shooter) return;

        // 火矢：点燃目标（爆炸性弹射物的“火焰爆炸”在发射时即已标记，见 onEntityJoinLevel）
        int flame = enchantLevelOf(shooter, Enchantments.FLAME);
        if (flame > 0) {
            livingVictim.setRemainingFireTicks(100);
        }

        // 穿透：命中后有 (等级/100)% 概率不消失，保持原速度与角度继续前进
        int piercing = enchantLevelOf(shooter, Enchantments.PIERCING);
        if (piercing > 0 && serverLevel.random.nextFloat() < clampProbability(piercing / 100.0)) {
            int remain = proj.getPersistentData().getInt(PIERCE_REMAIN_TAG);
            if (remain < ENCHANT_PIERCING_MAX_PASS) {
                proj.getPersistentData().putInt(PIERCE_REMAIN_TAG, remain + 1);
                Vec3 v = proj.getDeltaMovement();
                double sp = v.length();
                if (sp > 1.0E-4) {
                    proj.moveTo(proj.getX() + v.x, proj.getY() + v.y, proj.getZ() + v.z);
                }
                event.setCanceled(true);
            }
        }
    }

    // 对原版“食物和饮品”标签做整理：
    //  · 变形药水三形态均重命名为“变形为§krandom”
    //  · 传送药水：移除直饮+滞留型；留下的喷溅型命名为“随机传送药水”
    private static void onBuildCreativeTab(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != net.minecraft.world.item.CreativeModeTabs.FOOD_AND_DRINKS) return;
        java.util.List<ItemStack> all = new java.util.ArrayList<>();
        for (ItemStack s : event.getParentEntries()) all.add(s);
        java.util.List<ItemStack> toRemove = new java.util.ArrayList<>();
        for (ItemStack s : all) {
            if (isPotionOf(s, ModPotions.TRANSPORTATION)) {
                net.minecraft.world.item.Item it = s.getItem();
                if (it == Items.POTION || it == Items.LINGERING_POTION) {
                    toRemove.add(s);
                } else if (it == Items.SPLASH_POTION) {
                    s.set(ModDataComponents.TRANSPORT_MODE.get(), "random");
                    s.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("随机传送药水"));
                }
            } else if (isPotionOf(s, ModPotions.TRANSMUTATION)) {
                s.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("变形为§krandom"));
            }
        }
        var vis = net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS;
        for (ItemStack s : toRemove) {
            event.remove(s, vis);
        }
    }

    // 该物品是否为某种药水（按 POTION_CONTENTS 的 holder 判断）
    private static boolean isPotionOf(ItemStack s, net.minecraft.core.Holder<Potion> holder) {
        if (s.isEmpty()) return false;
        PotionContents c = s.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return c.potion().map(h -> h.is(holder)).orElse(false);
    }

    /**
     * 多重射击：射出的弹射物沿水平方向均匀分裂出额外 2×level 支（总支数 = 2×level+1，
     * level1 即 3 支，符合弩中箭的分裂次数，随等级递增每级 +2 支）。
     * 分裂用于任意弹射物（箭/雪球/火球等）；副本以 COPY 标记避免再次分裂。
     */
    /**
     * 多重射击等级：优先取射手身上的“魔咒状态效果”，否则取射手手持武器（弓/弩）上的多重射击魔咒。
     * 这样空的骷髅（魔咒状态效果）与手持附魔弓/弩的骷髅都能触发分裂。
     */
    private static int getMultishotLevel(ServerLevel level, LivingEntity shooter) {
        int fromEffect = enchantLevelOf(shooter, Enchantments.MULTISHOT);
        if (fromEffect > 0) return fromEffect;
        ItemStack weapon = shooter.getWeaponItem();
        if (weapon != null && !weapon.isEmpty()) {
            Holder<Enchantment> multi = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).getHolder(Enchantments.MULTISHOT).orElse(null);
            if (multi != null) {
                int fromWeapon = weapon.getEnchantments().getLevel(multi);
                if (fromWeapon > 0) return fromWeapon;
            }
        }
        return 0;
    }

    private static void spawnMultishotCopies(ServerLevel level, LivingEntity shooter, Projectile proj, int multishotLevel) {
        // 实际效果等级决定分裂支数：最少分裂出 3 支箭（含本体），每多一级再多分裂 2 支，
        // 即副本次数 = 2×level，总支数 = 2×level+1（level1=3、level2=5、level3=7……）。
        int extras = 2 * multishotLevel; // 额外分裂支数（不含本体）
        Vec3 v = proj.getDeltaMovement();
        for (int i = 0; i < extras; i++) {
            Projectile copy;
            if (proj instanceof FireworkRocketEntity) {
                // 烟花火箭副本：直接用随机烟花物品构造，使其 lifetime 依据随机飞行时长正确计算
                copy = new FireworkRocketEntity(level, createRandomFireworkStack(level),
                    proj.getX(), proj.getY(), proj.getZ(), true);
            } else {
                copy = (Projectile) proj.getType().create(level);
            }
            if (copy == null) continue;
            copy.setOwner(shooter);
            copy.setPos(proj.getX(), proj.getY(), proj.getZ());
            // 女巫丢出的分裂药水：副本默认只是无效果的水瓶，改为随机效果 + 随机时长的药水
            // （效果可来自原版或其它 Mod，但一定不是变形药水）。
            if (proj instanceof ThrownPotion && copy instanceof ThrownPotion potionCopy) {
                potionCopy.setItem(createRandomSplashPotion(level));
            }
            // 副本各自瞄准发射方向：水平方向均匀散布，半宽 MULTISHOT_HALF_SPREAD
            double t = (extras == 1) ? 0.0 : (i / (double) (extras - 1) - 0.5) * 2.0; // -1..1
            double angle = t * MULTISHOT_HALF_SPREAD;
            copy.setDeltaMovement(rotateScaledVelocity(v, (float) angle));
            copy.hasImpulse = true;
            copy.getPersistentData().putBoolean(MULTISHOT_COPY_TAG, true);
            level.addFreshEntity(copy);
        }
    }

    /**
     * 生成一瓶「随机效果 + 随机时长 + 随机浓度」的喷溅药水，供女巫多重射击分裂副本使用。
     * 效果从已注册状态效果中随机选取（可来自原版或其它 Mod），但一定排除变形药水（变形效果）。
     */
    private static ItemStack createRandomSplashPotion(ServerLevel level) {
        Registry<MobEffect> effectReg = level.registryAccess().registryOrThrow(Registries.MOB_EFFECT);
        // 排除变形药水效果（及其它非战斗杂项可后续再按需扩展）
        java.util.List<Holder<MobEffect>> candidates = effectReg.holders()
            .filter(h -> !h.is(ModMobEffects.TRANSMUTATION))
            .collect(java.util.stream.Collectors.toList());
        Holder<MobEffect> effect = candidates.isEmpty() ? null : candidates.get(TRANSMUTATION_RANDOM.nextInt(candidates.size()));
        // 随机时长 5~60 秒（100~1200 刻）、随机浓度 0~2 级
        int duration = 100 + TRANSMUTATION_RANDOM.nextInt(1101);
        int amplifier = TRANSMUTATION_RANDOM.nextInt(3);
        MobEffectInstance instance = effect == null
            ? null
            : new MobEffectInstance(effect, duration, amplifier, false, true, true);
        PotionContents contents = new PotionContents(Optional.empty(), Optional.empty(),
            instance == null ? java.util.List.of() : java.util.List.of(instance));
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);
        stack.set(DataComponents.POTION_CONTENTS, contents);
        return stack;
    }

    /**
     * 生成一枚「随机效果（形状/拖尾/闪烁）+ 随机颜色 + 随机飞行时长」的烟花火箭物品，
     * 供多重射击下实体发射的烟花火箭（本体与分裂副本）使用。
     */
    private static ItemStack createRandomFireworkStack(ServerLevel level) {
        int flight = 1 + TRANSMUTATION_RANDOM.nextInt(3); // 1~3 段飞行时长（原版上界）
        int explosionsCount = 1 + TRANSMUTATION_RANDOM.nextInt(3); // 1~3 个爆裂效果
        DyeColor[] dyes = DyeColor.values();
        // 随机主颜色（1~3 色）
        it.unimi.dsi.fastutil.ints.IntList colors = new it.unimi.dsi.fastutil.ints.IntArrayList();
        int colorCount = 1 + TRANSMUTATION_RANDOM.nextInt(3);
        for (int i = 0; i < colorCount; i++) {
            colors.add(dyes[TRANSMUTATION_RANDOM.nextInt(dyes.length)].getFireworkColor());
        }
        FireworkExplosion.Shape[] shapes = FireworkExplosion.Shape.values();
        java.util.List<FireworkExplosion> explosions = new java.util.ArrayList<>();
        for (int i = 0; i < explosionsCount; i++) {
            // 随机形状 + 随机拖尾/闪烁 + 约一半概率附带随机褪色色
            FireworkExplosion.Shape shape = shapes[TRANSMUTATION_RANDOM.nextInt(shapes.length)];
            it.unimi.dsi.fastutil.ints.IntList fade = new it.unimi.dsi.fastutil.ints.IntArrayList();
            if (TRANSMUTATION_RANDOM.nextBoolean()) {
                fade.add(dyes[TRANSMUTATION_RANDOM.nextInt(dyes.length)].getFireworkColor());
            }
            explosions.add(new FireworkExplosion(shape, new it.unimi.dsi.fastutil.ints.IntArrayList(colors),
                fade, TRANSMUTATION_RANDOM.nextBoolean(), TRANSMUTATION_RANDOM.nextBoolean()));
        }
        ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
        stack.set(DataComponents.FIREWORKS, new Fireworks(flight, explosions));
        return stack;
    }

    // FireworkRocketEntity 的烟花物品同步数据与寿命字段均未提供公开 setter，用反射访问。
    private static final java.lang.reflect.Field FIREWORK_ITEM_DATA_FIELD = fireworkItemDataField();
    private static final java.lang.reflect.Field FIREWORK_LIFETIME_FIELD = fireworkLifetimeField();

    private static java.lang.reflect.Field fireworkItemDataField() {
        try {
            java.lang.reflect.Field f = FireworkRocketEntity.class.getDeclaredField("DATA_ID_FIREWORKS_ITEM");
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static java.lang.reflect.Field fireworkLifetimeField() {
        try {
            java.lang.reflect.Field f = FireworkRocketEntity.class.getDeclaredField("lifetime");
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** 把已发射的烟花火箭随机化为随机效果/颜色/飞行时长（并按其飞行时长重算寿命）。 */
    private static void randomizeFireworkRocket(ServerLevel level, FireworkRocketEntity rocket) {
        ItemStack stack = createRandomFireworkStack(level);
        try {
            if (FIREWORK_ITEM_DATA_FIELD != null) {
                @SuppressWarnings("unchecked")
                net.minecraft.network.syncher.EntityDataAccessor<ItemStack> acc =
                    (net.minecraft.network.syncher.EntityDataAccessor<ItemStack>) FIREWORK_ITEM_DATA_FIELD.get(null);
                rocket.getEntityData().set(acc, stack);
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[DBG] randomizeFireworkRocket: 无法写入烟花物品数据", e);
        }
        try {
            if (FIREWORK_LIFETIME_FIELD != null) {
                int i = 1 + stack.getOrDefault(DataComponents.FIREWORKS, new Fireworks(1, java.util.List.of())).flightDuration();
                FIREWORK_LIFETIME_FIELD.setInt(rocket,
                    10 * i + rocket.getRandom().nextInt(6) + rocket.getRandom().nextInt(7));
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[DBG] randomizeFireworkRocket: 无法重算烟花寿命", e);
        }
    }

    /** 把速度向量绕世界 Y 轴旋转一定弧度（用于弩多重射击的水平散布）。 */
    private static Vec3 rotateScaledVelocity(Vec3 v, float radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = v.x * cos + v.z * sin;
        double z = -v.x * sin + v.z * cos;
        return new Vec3(x, v.y, z);
    }

    /** 附魔生物·快速装填（受击后减少无敌帧）辅助；当前已在近战攻击侧处理，此处保留钩子无操作。 */
    private static void onLivingDamagePost(LivingDamageEvent.Post event) {
        // 快速装填通过 DamageContainer 直接在受击时减少无敌帧（见 handleEnchantAttackerHit），
        // Post 阶段无需额外处理，保留注册以兼容后续扩展。

        // 方块形态玩家受伤：播放对应方块被破坏（break）的音效
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || !(target instanceof ServerPlayer sp)) return;
        PlayerTransmutationInfo info = PLAYER_TRANSMUTATION_INFO.get(sp.getUUID());
        if (info == null || info.form != TransmutationForm.BLOCK) return;
        ResourceLocation rl = ResourceLocation.tryParse(info.itemType());
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) return;
        net.minecraft.world.level.block.state.BlockState bs =
            BuiltInRegistries.BLOCK.get(rl).defaultBlockState();
        net.minecraft.world.level.block.SoundType st = bs.getSoundType();
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
            st.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.9F);
    }

    /** 附魔生物·迅捷潜行：生物行走时有概率不发出脚步音效（若有脚步音效）。 */
    private static void onPlayLevelSound(PlayLevelSoundEvent event) {
        boolean stepLike = event.getSound() != null
            && event.getSound().value().getLocation().getPath().contains("step");
        if (!stepLike) return;
        // 事件仅在生物发出声音时（AtEntity）关联到该生物
        if (!(event instanceof PlayLevelSoundEvent.AtEntity atEntity)) return;
        if (!(atEntity.getEntity() instanceof LivingEntity living)) return;
        if (!(living.level() instanceof ServerLevel serverLevel)) return;
        int swiftSneak = enchantLevelOf(living, Enchantments.SWIFT_SNEAK);
        if (swiftSneak <= 0) return;
        if (living.isShiftKeyDown() && serverLevel.random.nextFloat() < clampProbability(swiftSneak / 100.0)) {
            event.setCanceled(true);
        }
    }

    /** 附魔生物·魔咒状态效果到期：清除“自体附魔”标记并允许再次附魔。 */
    private static void onMobEffectExpired(MobEffectEvent.Expired event) {
        MobEffectInstance inst = event.getEffectInstance();
        if (inst == null) return;
        if (!(event.getEntity().level() instanceof ServerLevel)) return;
        // 是否为附魔占位效果：映射到原版附魔
        if (enchantmentOfEffect((ServerLevel) event.getEntity().level(), inst.getEffect().value()) == null) return;
        LivingEntity target = event.getEntity();
        if (getEnchantSelf(target)) {
            setEnchantSelf(target, false);
            broadcastEnchantSelf(target);
        }
    }

    /**
     * 附魔生物·持续型效果每刻处理（服务端，onEntityTick 调用）：
     * 效率=急迫、激流、深海探索者、灵魂疾行、冰霜行者、忠诚、绑定诅咒。
     */
    private static void handleEnchantContinuousTick(LivingEntity m) {
        if (!(m.level() instanceof ServerLevel serverLevel)) return;
        if (m.isRemoved() || m.isDeadOrDying()) return;

        boolean wet = m.isInWaterRainOrBubble() || m.isInWater();

        // --- 效率：同急迫状态效果（挖掘加速）；水下速掘在水下时同样加速挖掘 ---
        int efficiency = enchantLevelOf(m, Enchantments.EFFICIENCY);
        int aquaAffinity = enchantLevelOf(m, Enchantments.AQUA_AFFINITY);
        int digLevel = Math.max(efficiency, (aquaAffinity > 0 && wet) ? aquaAffinity : 0);
        if (digLevel > 0) {
            MobEffectInstance haste = m.getEffect(MobEffects.DIG_SPEED);
            if (haste == null || haste.getAmplifier() != digLevel - 1) {
                m.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, digLevel - 1, false, false));
            }
        }
        int riptide = enchantLevelOf(m, Enchantments.RIPTIDE);
        double riptideMod = riptide > 0 && wet ? (riptide + 1) / 25.0 - 1.0 : 0.0;
        applyEnchantMoveModifier(m, RIPTIDE_MODIFIER_ID, riptideMod);

        // --- 深海探索者：水中移动速度增加（每级 +5%）---
        int depthStrider = enchantLevelOf(m, Enchantments.DEPTH_STRIDER);
        double depthMod = depthStrider > 0 && wet ? depthStrider * 0.05 : 0.0;
        applyEnchantMoveModifier(m, DEPTH_STRIDER_MODIFIER_ID, depthMod);

        // --- 灵魂疾行：踩灵魂沙/灵魂土时移动加速 ---
        int soulSpeed = enchantLevelOf(m, Enchantments.SOUL_SPEED);
        double soulMod = 0.0;
        if (soulSpeed > 0 && m.onGround()) {
            BlockState below = serverLevel.getBlockState(m.blockPosition().below());
            if (below.is(Blocks.SOUL_SAND) || below.is(Blocks.SOUL_SOIL)) {
                soulMod = soulSpeed * 0.05;
            }
        }
        applyEnchantMoveModifier(m, SOUL_SPEED_MODIFIER_ID, soulMod);

        // --- 快速装填：提升生物的攻击速度（每级 +25%），使近战攻击节奏更快 ---
        int quickCharge = enchantLevelOf(m, Enchantments.QUICK_CHARGE);
        applyEnchantAttackModifier(m, QUICK_CHARGE_ATTACK_SPEED_MODIFIER_ID,
            quickCharge > 0 ? quickCharge * 0.25 : 0.0);

        // --- 冰霜行者：行走时冻结周围水面（遵循原版：以脚下方块为圆心、等级+2为半径，
        //     区域内容暴露在空气下、且与脚下同高度的水源方块都结成霜冰）---
        int frostWalker = enchantLevelOf(m, Enchantments.FROST_WALKER);
        if (frostWalker > 0 && m.onGround()) {
            BlockPos center = m.getBlockPosBelowThatAffectsMyMovement();
            float radius = Math.min(16.0F, 2 + frostWalker);
            int cy = center.getY();
            for (BlockPos bp : BlockPos.betweenClosed(
                new BlockPos(center.getX() - (int) radius, cy, center.getZ() - (int) radius),
                new BlockPos(center.getX() + (int) radius, cy, center.getZ() + (int) radius))) {
                if (!bp.closerThan(center, radius)) continue;
                BlockState bs = serverLevel.getBlockState(bp);
                if (serverLevel.getFluidState(bp).is(net.minecraft.tags.FluidTags.WATER)
                    && bs.getCollisionShape(serverLevel, bp).isEmpty()
                    && serverLevel.getBlockState(bp.above()).isAir()) {
                    serverLevel.setBlock(bp, Blocks.FROSTED_ICE.defaultBlockState(), 3);
                }
            }
        }

        // --- 忠诚：只传送到给予该状态效果的权杖使用者身边（过远时随机就近传送）---
        if (enchantLevelOf(m, Enchantments.LOYALTY) > 0) {
            UUID granter = getStaffGranterUuid(m);
            MinecraftServer server = serverLevel.getServer();
            if (granter != null) {
                ServerPlayer owner = server.getPlayerList().getPlayer(granter);
                if (owner != null && m.distanceToSqr(owner) > ENCHANT_CURSE_TELEPORT_THRESHOLD_SQ) {
                    enchantTeleportNear(m, owner.position());
                }
            }
        }

        // --- 绑定诅咒：攻击者离索敌目标太远时，就近传送至目标身边 ---
        if (m instanceof Mob mob) {
            LivingEntity targetMob = mob.getTarget();
            if (targetMob != null && targetMob.isAlive()
                && m.distanceToSqr(targetMob) > ENCHANT_CURSE_TELEPORT_THRESHOLD_SQ
                && enchantLevelOf(m, Enchantments.BINDING_CURSE) > 0) {
                enchantTeleportNear(m, targetMob.position());
            }
        }
    }

    /** 读取“忠诚”所需给予该状态的权杖使用者 UUID；无则返回 null。 */
    private static UUID getStaffGranterUuid(LivingEntity m) {
        CompoundTag data = m.getPersistentData();
        return data.hasUUID(ENCHANT_STAFF_GRANTER_TAG) ? data.getUUID(ENCHANT_STAFF_GRANTER_TAG) : null;
    }

    /** 添加/更新/移除单个移动速度属性修改器（避免重复累加）。 */
    private static void applyEnchantMoveModifier(LivingEntity m, ResourceLocation id, double amount) {
        AttributeInstance attr = m.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        if (attr.hasModifier(id)) attr.removeModifier(id);
        if (Math.abs(amount) > 1.0E-4) {
            attr.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    /** 添加/更新/移除单个攻击速度属性修改器（避免重复累加）。 */
    private static void applyEnchantAttackModifier(LivingEntity m, ResourceLocation id, double amount) {
        AttributeInstance attr = m.getAttribute(Attributes.ATTACK_SPEED);
        if (attr == null) return;
        if (attr.hasModifier(id)) attr.removeModifier(id);
        if (Math.abs(amount) > 1.0E-4) {
            attr.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    /** 忠诚/绑定诅咒传送：把生物移动到目标点旁（不播放传送音效）。 */
    private static void enchantTeleportNear(LivingEntity m, Vec3 dest) {
        if (dest == null) return;
        double x = dest.x, y = dest.y, z = dest.z;
        // 简单防卡：取目标点所在列上方空气块
        m.teleportTo(x, y, z);
    }

    private static void applyBlockEffects(Player player, ItemStack blockingItem, InteractionHand blockingHand, DamageSource source, Entity projectile) {
        boolean isLarge = isLargeProjectile(projectile);
        boolean isStaff = blockingItem.getItem() instanceof StaffItem;
        ItemEnchantments enchants = blockingItem.getEnchantments();
        int unbreakingLevel = getUnbreakingLevel(enchants, player.level());

        if (isLarge && !isStaff) {
            float dropChance = 0.1F * (float) Math.pow(0.7, unbreakingLevel);
            if (player.getRandom().nextFloat() < dropChance) {
                ItemStack dropStack = blockingItem.copy();
                blockingItem.shrink(1);
                player.spawnAtLocation(dropStack);
            }
        }

        float durabilityLossChance = 0.3F * (float) Math.pow(0.7, unbreakingLevel);
        if (player.getRandom().nextFloat() < durabilityLossChance) {
            if (blockingItem.isDamageableItem()) {
                hurtStaff(blockingItem, 1, player,
                    blockingHand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }

        if (projectile instanceof ShulkerBullet) {
            projectile.discard();
        } else if (!projectile.isRemoved()) {
            double speed = projectile.getDeltaMovement().length();
            double rx = player.getRandom().nextGaussian();
            double ry = player.getRandom().nextGaussian();
            double rz = player.getRandom().nextGaussian();
            double len = Math.sqrt(rx * rx + ry * ry + rz * rz);
            projectile.setDeltaMovement(rx / len * speed, ry / len * speed, rz / len * speed);
            projectile.hasImpulse = true;
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    private static void applyMeleeBlockEffects(Player player, ItemStack blockingItem, InteractionHand blockingHand) {
        ItemEnchantments enchants = blockingItem.getEnchantments();
        int unbreakingLevel = getUnbreakingLevel(enchants, player.level());
        float durabilityLossChance = 0.3F * (float) Math.pow(0.7, unbreakingLevel);
        if (player.getRandom().nextFloat() < durabilityLossChance) {
            if (blockingItem.isDamageableItem()) {
                hurtStaff(blockingItem, 1, player,
                    blockingHand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    private static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        // --- Omega 游戏图标 / Omega 权杖：持有者免疫除虚空伤害以外的所有伤害 ---
        if (target instanceof Player omegaPlayer && OMEGA_POWER_PLAYERS.contains(omegaPlayer.getUUID())) {
            // 虚空伤害（掉出世界 / 拆解 Omega 权杖造成的 fellOutOfWorld，均映射为 FELL_OUT_OF_WORLD）不免疫
            boolean isVoidDamage = source.is(DamageTypes.FELL_OUT_OF_WORLD);
            if (!isVoidDamage) {
                event.setNewDamage(0);
                return;
            }
        }

        if (isPhysicsDimension(target.level())
            && source == target.level().damageSources().fellOutOfWorld()
            && !PHYSICS_BLOCKS_PLACED_DIMS.contains(target.level().dimension().location())) {
            event.setNewDamage(0);
            return;
        }

        // --- 怪物学校末影人：默认对玩家无伤害（仅攻击动画）；被使用者用羽毛标记后改为一般末影人的 3 倍伤害 ---
        if (target instanceof Player
            && source.getDirectEntity() instanceof EnderMan enderman
            && isMonsterSchool(enderman)) {
            if (isMonsterSchoolEnhanced(enderman)) {
                event.setNewDamage(event.getNewDamage() * 3.0F);
            } else {
                event.setNewDamage(0);
            }
        }

        // --- BLOCKING: check if victim is blocking with our item ---
        if (target instanceof Player player && blockingPlayers.contains(player.getUUID())) {
            if (isProjectileDamage(source)) {
                Entity projectile = source.getDirectEntity();
                if (projectile != null && isBlockableProjectile(projectile)) {
                    handleBlock(player, source, projectile, event);
                    return;
                }
            }
            if (source.getDirectEntity() instanceof LivingEntity) {
                handleStaffMeleeBlock(player, source, event);
                return;
            }
        }

        // --- TOTEM EFFECT: check if victim holds our item ---
        if (target instanceof Player player) {
            float healthAfterDamage = player.getHealth() - event.getNewDamage();
            if (healthAfterDamage <= 0.0F && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                for (InteractionHand hand : InteractionHand.values()) {
                    ItemStack held = player.getItemInHand(hand);
                    if (held.getItem() instanceof GlisteringMelonKnifeItem) {
                        applyTotemEffect(player, held);
                        event.setNewDamage(0);
                        return;
                    }
                }
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
                    ItemStack equipped = player.getItemBySlot(slot);
                    if (equipped.getItem() instanceof GlisteringMelonKnifeItem) {
                        applyTotemEffect(player, equipped);
                        event.setNewDamage(0);
                        return;
                    }
                }
            }
        }

        // --- MAGMA STAFF FIRE ASPECT: hidden fire aspect X for magma_block staff ---
        {
            Entity attacker = source.getEntity();
            if (attacker instanceof LivingEntity livingAttacker
                && attacker == source.getDirectEntity()) {
                ItemStack weapon = livingAttacker.getMainHandItem();
                if (weapon.getItem() instanceof StaffItem) {
                    String staffBlockType = effectiveStaffBlockType(weapon, livingAttacker);
                    if ("magma_block".equals(staffBlockType)) {
                        target.setRemainingFireTicks(80 * 10);
                    }
                }
            }
        }

        // --- STAFF NON-DAMAGE EFFECTS: knockback / special effects only (damage is now attribute-based) ---
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity livingAttacker
            && attacker == event.getSource().getDirectEntity()
            && !(target instanceof Player ply && blockingPlayers.contains(ply.getUUID()))) {
            ItemStack weapon = livingAttacker.getMainHandItem();
            if (weapon.getItem() instanceof StaffItem) {
                String staffBlockType = effectiveStaffBlockType(weapon, livingAttacker);
                if ("end_portal_frame".equals(staffBlockType)) {
                    event.setNewDamage(0);
                    return;
                }
                if ("gold_block".equals(staffBlockType)) {
                    float knockbackStrength = 0.5F;
                    target.knockback(
                        knockbackStrength,
                        Mth.sin(livingAttacker.getYRot() * (float) (Math.PI / 180.0)),
                        -Mth.cos(livingAttacker.getYRot() * (float) (Math.PI / 180.0))
                    );
                } else if ("obsidian".equals(staffBlockType)) {
                    float knockbackStrength = 2.5F;
                    target.knockback(
                        knockbackStrength,
                        Mth.sin(livingAttacker.getYRot() * (float) (Math.PI / 180.0)),
                        -Mth.cos(livingAttacker.getYRot() * (float) (Math.PI / 180.0))
                    );
                } else if ("bell".equals(staffBlockType)) {
                    float knockbackStrength = 2.5F;
                    target.knockback(
                        knockbackStrength,
                        Mth.sin(livingAttacker.getYRot() * (float) (Math.PI / 180.0)),
                        -Mth.cos(livingAttacker.getYRot() * (float) (Math.PI / 180.0))
                    );

                    if (!(livingAttacker instanceof Player)) {
                        applyBellStaffEffect(livingAttacker, target);
                    }
                } else if ("netherite_block".equals(staffBlockType)) {
                    applyNetheriteKnockback(livingAttacker, target);
                }

                // Consume durability for non-player attackers (players handle this in onAttackEntity)
                if (!(livingAttacker instanceof Player)) {
                    hurtStaff(weapon, 2, livingAttacker, EquipmentSlot.MAINHAND);
                }
            }
        }

        // --- CUSTOM ENCHANTMENT DAMAGE: attacker uses our item ---
        if (attacker instanceof LivingEntity livingAttacker) {
            ItemStack weapon = livingAttacker.getMainHandItem();
            if (weapon.getItem() instanceof GlisteringMelonKnifeItem && source.getEntity() == livingAttacker) {
                applyCustomEnchantmentDamage(event, target, weapon, livingAttacker, livingAttacker.level());
            }
        }
    }

    private static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        Entity target = event.getTarget();

        // 变形状态：若攻击目标是自己变成的壳（物品/方块/生物壳/玩家空壳），
        // 取消本次攻击并重定向到视线前方真正要打的目标，避免"打到自己"且攻击不到别的目标。
        if (isOwnTransmutationShell(player, target)) {
            event.setCanceled(true);
            if (player instanceof ServerPlayer serverPlayer) {
                redirectTransmutedAttack(serverPlayer);
            }
            return;
        }

        ItemStack mainStack = player.getMainHandItem();
        if (!(mainStack.getItem() instanceof StaffItem)) return;

        String blockType = mainStack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");

        if ("end_portal_frame".equals(blockType)) {
            event.setCanceled(true);
            return;
        }

        hurtStaff(mainStack, 2, player, EquipmentSlot.MAINHAND);

        if ("netherite_block".equals(blockType)) {
            applyNetheriteKnockback(player, target);
        } else if ("bell".equals(blockType)) {
            applyBellStaffEffect(player, target);
        }
    }

    // 判断攻击目标是否是变形玩家自己的壳（物品/方块/生物壳/玩家空壳）
    private static boolean isOwnTransmutationShell(Player player, Entity target) {
        if (!TRANSMUTED_ENTITIES.contains(player.getUUID())) return false;
        UUID pu = player.getUUID();
        UUID tu = target.getUUID();
        TransmutationData d = ITEM_TRANSMUTATIONS.get(tu);
        if (d != null) return pu.equals(d.playerUuid());
        d = FALLING_TRANSMUTATIONS.get(tu);
        if (d != null) return pu.equals(d.playerUuid());
        LivingShellData s = LIVING_SHELLS.get(tu);
        return s != null && pu.equals(s.playerUuid());
    }

    // 变形玩家攻击自己的壳被取消后，沿视线重定向到真正的攻击目标（排除壳本体），
    // 使攻击能穿过贴附在自己身上的壳命中前方/身后的敌人。
    private static void redirectTransmutedAttack(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        double reach = player.entityInteractionRange();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(reach));
        // 先做方块遮挡裁剪：真实目标必须位于视线与方块命中点之前
        HitResult blockHit = serverLevel.clip(
            new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double maxDistSq = blockHit.getType() == HitResult.Type.MISS
            ? eye.distanceToSqr(end)
            : eye.distanceToSqr(blockHit.getLocation());
        // 再沿视线找最近的实体（排除玩家自身与其变形壳）
        AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        Entity bestTarget = null;
        double bestDistSq = maxDistSq;
        for (Entity e : serverLevel.getEntities(player, box,
                ent -> ent.isAlive() && ent.isPickable() && ent != player
                    && !isOwnTransmutationShell(player, ent))) {
            java.util.Optional<Vec3> hit = e.getBoundingBox().inflate(0.1).clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = eye.distanceToSqr(hit.get());
            if (d <= bestDistSq) {
                bestDistSq = d;
                bestTarget = e;
            }
        }
        if (bestTarget != null) {
            player.attack(bestTarget);
        }
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;

        // 玩家变形后的跟随壳（如羊驼等可交互生物）：取消一切右键互动
        // （防止空手骑乘、持喷溅药水变成饲喂使其吼叫等抢占正常右键行为）
        if (event.getTarget().getPersistentData().getBoolean("jafa_transmutation_follow")) {
            event.setCanceled(true);
            return;
        }

        if (!event.getItemStack().is(Items.FEATHER)) return;
        if (!(event.getTarget() instanceof EnderMan enderman)) return;
        Player player = event.getEntity();
        enderman.setTarget(player);
        enderman.setLastHurtByMob(player);
        // 怪物学校末影人被羽毛标记后，攻击伤害提升为一般末影人的 3 倍
        if (isMonsterSchool(enderman)) {
            enderman.getPersistentData().putBoolean(MONSTER_SCHOOL_ENHANCED_TAG, true);
        }
    }

    private static boolean isMonsterSchool(EnderMan enderman) {
        return enderman.getPersistentData().getBoolean(MONSTER_SCHOOL_TAG);
    }

    private static boolean isMonsterSchoolEnhanced(EnderMan enderman) {
        return enderman.getPersistentData().getBoolean(MONSTER_SCHOOL_ENHANCED_TAG);
    }

    private static void handleMonsterSchoolEndermanTick(EnderMan enderman) {
        if (!(enderman.level() instanceof ServerLevel serverLevel)) return;
        Player nearest = serverLevel.getNearestPlayer(enderman, MONSTER_SCHOOL_AGGRO_RADIUS);
        if (nearest != null && enderman.getTarget() != nearest) {
            enderman.setTarget(nearest);
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("jafa")
            .then(Commands.literal("endie")
                .then(Commands.argument("targets", EntityArgument.entities())
                    .executes(ctx -> {
                        int count = 0;
                        for (Entity e : EntityArgument.getEntities(ctx, "targets")) {
                            if (e instanceof EnderMan enderman) {
                                enderman.getPersistentData().putBoolean(MONSTER_SCHOOL_TAG, true);
                                count++;
                            }
                        }
                        final int tagged = count;
                        ctx.getSource().sendSuccess(() -> Component.literal("已将 " + tagged + " 只末影人标记为怪物学校变种"), true);
                        return count;
                    }))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    EnderMan enderman = EntityType.ENDERMAN.create(player.serverLevel());
                    if (enderman == null) return 0;
                    enderman.setPos(player.getX(), player.getY(), player.getZ());
                    enderman.getPersistentData().putBoolean(MONSTER_SCHOOL_TAG, true);
                    player.serverLevel().addFreshEntity(enderman);
                    return 1;
                }))
            .then(Commands.literal("commandblockvillager")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    Villager villager = EntityType.VILLAGER.create(level);
                    if (villager == null) return 0;
                    villager.setPos(player.getX(), player.getY(), player.getZ());
                    villager.setVillagerData(villager.getVillagerData()
                        .setProfession(VillagerProfession.LIBRARIAN)
                        .setLevel(1)); // 新手级，随交易升级，命令方块交易只在达到大师级后解锁
                    // 强制标签：保证该图书管理员达到大师级时一定出售命令方块（debug 用）
                    villager.getPersistentData().putBoolean(SELLS_COMMAND_BLOCK_TAG, true);
                    level.addFreshEntity(villager);
                    ctx.getSource().sendSuccess(() -> Component.literal("已召唤新手图书管理员（大师级解锁命令方块交易）"), true);
                    return 1;
                }))
            .then(Commands.literal("debug_string")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    // 随机生成 2 或 3 个端点（在玩家视角前方、盾牌格挡范围附近）
                    int n = Math.random() < 0.5 ? 2 : 3;
                    double[][] pts = new double[n][3];
                    StringBuilder msg = new StringBuilder("已生成调试端点：");
                    for (int i = 0; i < n; i++) {
                        pts[i] = sampleShieldPoint(player);
                        msg.append(" P").append(i + 1).append("(")
                            .append(String.format("%.2f", pts[i][0])).append(", ")
                            .append(String.format("%.2f", pts[i][1])).append(", ")
                            .append(String.format("%.2f", pts[i][2])).append(")");
                    }
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new cn.autoforged.joes_addons_for_abmc.network.DebugStringPayload(pts));
                    ctx.getSource().sendSuccess(() -> Component.literal(msg.toString()), true);
                    return 1;
                }))
            .then(Commands.literal("witchboss")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    Witch witch = EntityType.WITCH.create(level);
                    if (witch == null) return 0;
                    witch.setPos(player.getX(), player.getY(), player.getZ());
                    // 直接打上女巫 Boss 标签，供调试/测试（与女巫小屋生成的 Boss 行为一致）
                    witch.getPersistentData().putBoolean(WITCH_BOSS_TAG, true);
                    WITCH_BOSS_TRACKED.add(witch.getUUID());
                    initWitchBossHealth(witch);
                    initWitchBossHome(witch, witch.position()); // 调试召唤的家 = 召唤位
                    initWitchBossAmmo(witch);
                    level.addFreshEntity(witch);
                    ctx.getSource().sendSuccess(() -> Component.literal("已召唤女巫 Boss（迅捷时有 10% 概率改喝隐身药水）"), true);
                    return 1;
                })));

        // /revive <实体id>：从 /kill 存档中随机复活一个同 id 的实体并删除该存档
        // 使用 greedyString 参数，允许输入带命名空间的完整实体 id（如 joes_addons_for_abmc:player_shell）
        event.getDispatcher().register(Commands.literal("revive")
            .then(Commands.argument("entity_id", StringArgumentType.greedyString())
                .executes(ctx -> reviveEntity(ctx.getSource(),
                    StringArgumentType.getString(ctx, "entity_id")))));
        // 开关变形药水掷出者免疫：/jafa toggletransmutationdebug <true|false>。
        // true=开启免疫（对自己丢出的变形药水免疫，无法把自己变成物品/方块）；
        // false=关闭免疫（变形药水可影响掷出者，可把自己变成物品/方块）。
        event.getDispatcher().register(Commands.literal("jafa")
            .then(Commands.literal("toggletransmutationdebug")
                .then(Commands.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                    .executes(ctx -> toggleTransmutationThrowerDebug(ctx.getSource(),
                        com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "enabled")))))
            // /jafa togglewitchbossstage <1-4>：对当前玩家最近的女巫Boss 模拟切换阶段。
            // 1实体(浅蓝条)/2方块(黄条)/3物品(紫条)；4=该Boss被击败（原地粒子+消失）。
            .then(Commands.literal("togglewitchbossstage")
                .then(Commands.argument("stage", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 4))
                    .executes(ctx -> toggleWitchBossStage(ctx.getSource(),
                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "stage")))))
            // /jafa witchhut：连着女巫小屋一起生成女巫Boss（模拟生存中遇到的小屋女巫Boss），
            // 并把该小屋中心认定为女巫Boss的基地（回城补给的“家”）。
            .then(Commands.literal("witchhut")
                .executes(ctx -> spawnWitchBossWithHut(ctx.getSource()))));
    }

    /** /jafa witchhut：在执行者位置手动搭一座小型“女巫小屋”并在其中心召唤女巫Boss，
     *  小屋中心作为基地（home）。 */
    private static int spawnWitchBossWithHut(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer sp)) {
            source.sendFailure(Component.literal("该命令需由玩家在游戏内执行"));
            return 0;
        }
        ServerLevel level = sp.serverLevel();
        BlockPos center = sp.blockPosition();
        buildWitchHut(level, center);
        // 登记该小屋所在区块为“Boss 小屋”：阻止此区域自然刷新普通女巫（只保留下方生成的 WitchBoss）
        SharedCounts counts = getSharedCounts(level);
        counts.bossHutChunks.add(new net.minecraft.world.level.ChunkPos(center).toLong());
        counts.setDirty();
        Vec3 home = new Vec3(center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5);

        Witch witch = EntityType.WITCH.create(level);
        if (witch == null) return 0;
        witch.setPos(home.x, home.y, home.z);
        witch.getPersistentData().putBoolean(WITCH_BOSS_TAG, true);
        WITCH_BOSS_TRACKED.add(witch.getUUID());
        initWitchBossHealth(witch);
        initWitchBossHome(witch, home); // 基地 = 小屋中心（补给点）
        initWitchBossAmmo(witch);
        level.addFreshEntity(witch);
        source.sendSuccess(() -> Component.literal("已生成一座女巫小屋及女巫Boss（基地=小屋中心）"), true);
        return 1;
    }

    /** 照搬原版沼泽小屋（swamp_hut）结构：直接套用原版 {@code /place structure minecraft:swamp_hut}
     *  命令的完整放置逻辑（走命令调度器），可靠生成工作台、炼药锅、花盆+红蘑菇、藤蔓、
     *  四角橡木原木柱、云杉木板与云杉楼梯屋顶等全部原版细节。
     *  结构原点放在“玩家脚下方块”，省去自己逐方块放置所遇到的方块状态/依附判定等坑。 */
    private static void buildWitchHut(ServerLevel level, BlockPos center) {
        net.minecraft.server.MinecraftServer server = level.getServer();
        try {
            var dispatcher = server.getCommands().getDispatcher();
            net.minecraft.commands.CommandSourceStack src = server.createCommandSourceStack();
            String cmd = "place structure minecraft:swamp_hut "
                + center.getX() + " " + (center.getY() - 1) + " " + center.getZ();
            int code = dispatcher.execute(cmd, src);
            LOGGER.info("已通过 /place structure 放置女巫小屋，返回码={}", code);
        } catch (Exception e) {
            LOGGER.warn("/place structure 放置女巫小屋失败：{}", e.getMessage());
        }
    }

    /** /jafa togglewitchbossstage <1-4>：对执行者 128 格内的在场全部女巫Boss 切换阶段（模拟）。 */
    private static int toggleWitchBossStage(CommandSourceStack source, int stage) {
        if (!(source.getEntity() instanceof ServerPlayer sp)) {
            source.sendFailure(Component.literal("该命令需由玩家在游戏内执行"));
            return 0;
        }
        ServerLevel level = sp.serverLevel();
        java.util.List<Witch> bosses = level.getEntitiesOfClass(Witch.class,
            sp.getBoundingBox().inflate(128.0),
            w -> w.isAlive() && w.getPersistentData().getBoolean(WITCH_BOSS_TAG));
        if (bosses.isEmpty()) {
            source.sendFailure(Component.literal("未在 128 格内找到女巫Boss"));
            return 0;
        }
        String stageName = switch (stage) {
            case 1 -> "实体阶段"; case 2 -> "方块阶段"; case 3 -> "物品阶段";
            default -> "被击败"; };
        for (Witch boss : bosses) {
            setWitchBossStage(level, boss, stage);
        }
        source.sendSuccess(() -> Component.literal("已切换 " + bosses.size()
            + " 个在场女巫Boss 到：" + stageName), true);
        return 1;
    }

    /** /jafa toggletransmutationdebug <true|false>：指定当前玩家是否对自己丢出的变形药水免疫。 */
    private static int toggleTransmutationThrowerDebug(CommandSourceStack source, boolean enabled) {
        if (!(source.getEntity() instanceof ServerPlayer sp)) {
            source.sendFailure(Component.literal("该命令需由玩家在游戏内执行"));
            return 0;
        }
        sp.getPersistentData().putBoolean(TRANSFORM_POTION_IMMUNE_TAG, enabled);
        sp.displayClientMessage(Component.literal(
            "§a变形药水掷出者免疫已" + (enabled ? "§c开启" : "§a关闭") + "§r："
            + (enabled ? "变形药水不再影响你自己（无法把自己变成物品/方块）"
                       : "变形药水再次影响你自己（可把自己变成物品/方块）")), true);
        return 1;
    }

    /** 执行 /revive <实体id>：随机抽取一个同 id 的被 /kill 实体存档，在其周围复活并删除该存档。
     *  若无匹配 id 或列表为空则执行失败。 */
    private static int reviveEntity(CommandSourceStack source, String entityId) {
        entityId = entityId.trim();
        ResourceLocation typeId = ResourceLocation.tryParse(entityId);
        java.util.List<CompoundTag> list = null;
        if (typeId != null) {
            list = KILLED_ENTITY_STORAGE.get(typeId);
        }
        // 未命中的浅名（如 player_shell）默认解析为 minecraft:player_shell，可能对应不到 mod 实体。
        // 此时按 path 后缀在所有存档键里查找，以支持 mod 实体（如 joes_addons_for_abmc:player_shell）。
        if ((list == null || list.isEmpty())) {
            for (Map.Entry<ResourceLocation, java.util.List<CompoundTag>> e
                    : KILLED_ENTITY_STORAGE.entrySet()) {
                if (e.getKey().getPath().equals(entityId)) {
                    typeId = e.getKey();
                    list = e.getValue();
                    break;
                }
            }
        }
        if (typeId == null || list == null || list.isEmpty()) {
            source.sendFailure(Component.literal("没有可复活的该实体（该实体从未被 /kill 击杀或已全部复活）"));
            return 0;
        }
        if (!(source.getEntity() instanceof ServerPlayer executor)) {
            source.sendFailure(Component.literal("该命令需由玩家在游戏内执行"));
            return 0;
        }

        // 随机挑选一个同 id 存档并从列表中删除
        CompoundTag nbt = list.remove(source.getLevel().getRandom().nextInt(list.size()));
        if (list.isEmpty()) {
            KILLED_ENTITY_STORAGE.remove(typeId);
        }

        // 用实体 NBT 复活（移除旧 UUID，避免与已消失的旧实体 UUID 冲突）
        ServerLevel level = executor.serverLevel();
        CompoundTag spawnNbt = nbt.copy();
        spawnNbt.remove("UUID");
        double x = executor.getX();
        double y = executor.getY();
        double z = executor.getZ();
        net.minecraft.nbt.ListTag posList = new net.minecraft.nbt.ListTag();
        posList.add(net.minecraft.nbt.DoubleTag.valueOf(x));
        posList.add(net.minecraft.nbt.DoubleTag.valueOf(y));
        posList.add(net.minecraft.nbt.DoubleTag.valueOf(z));
        spawnNbt.put("Pos", posList);
        net.minecraft.nbt.ListTag motion = new net.minecraft.nbt.ListTag();
        motion.add(net.minecraft.nbt.DoubleTag.valueOf(0.0));
        motion.add(net.minecraft.nbt.DoubleTag.valueOf(0.0));
        motion.add(net.minecraft.nbt.DoubleTag.valueOf(0.0));
        spawnNbt.put("Motion", motion);

        java.util.Optional<Entity> opt = EntityType.create(spawnNbt, level);
        if (opt.isEmpty()) {
            source.sendFailure(Component.literal("实体数据无法解析，复活失败"));
            return 0;
        }
        Entity revived = opt.get();
        // 确保复活后满血且不处于死亡/受击动画状态（兜底，防止复用旧存档时再次死亡）
        if (revived instanceof LivingEntity liv) {
            liv.setHealth(liv.getMaxHealth());
            liv.deathTime = 0;
            liv.hurtTime = 0;
        }
        revived.setPos(x, y, z);
        level.addFreshEntity(revived);
        final ResourceLocation revivedType = typeId;
        source.sendSuccess(() -> Component.literal("已复活 " + revivedType), true);
        return 1;
    }

    /** 在玩家视角正前方、贴近盾牌格挡范围附近随机采样一个点。
     *  XZ 波动范围较原版扩大一倍：前方距离 0.5~3.5 格，横向 ±1 格；
     *  Y 以玩家头部高度为基准上下 1 格浮动。 */
    private static double[] sampleShieldPoint(Player player) {
        double yawRad = Math.toRadians(player.getYRot());
        double pitchRad = Math.toRadians(player.getXRot());
        // 视线方向（水平分量）
        double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fz = Math.cos(yawRad) * Math.cos(pitchRad);
        // 右向量 = 前 × 上（仅水平）
        double rx = -fz, rz = fx;
        double rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1.0E-6) {
            rx = 1.0;
            rz = 0.0;
            rl = 1.0;
        }
        rx /= rl;
        rz /= rl;

        double fwd = 0.5 + Math.random() * 3.0; // 前方 0.5~3.5 格（原 0.5~2，翻倍）
        double r1 = (Math.random() - 0.5) * 2.0; // 横向 -1~1（原 ±0.5，翻倍）
        // 端点 Y 以玩家头部高度为基准，上下 1 格浮动
        double headY = player.getY() + player.getEyeHeight();
        return new double[]{
            player.getX() + fx * fwd + rx * r1,
            headY + (Math.random() - 0.5) * 2.0,
            player.getZ() + fz * fwd + rz * r1
        };
    }

    private static void applyNetheriteKnockback(LivingEntity attacker, Entity target) {
        double strength = 40.0;
        double x = Math.sin(attacker.getYRot() * (Math.PI / 180.0));
        double z = -Math.cos(attacker.getYRot() * (Math.PI / 180.0));

        while (x * x + z * z < 1.0E-5F) {
            x = (Math.random() - Math.random()) * 0.01;
            z = (Math.random() - Math.random()) * 0.01;
        }

        Vec3 dir = new Vec3(x, 0.0, z).normalize().scale(strength);
        Vec3 curVel = target.getDeltaMovement();
        target.setDeltaMovement(
            curVel.x / 2.0 - dir.x,
            target.onGround() ? Math.min(0.4, curVel.y / 2.0 + strength) : curVel.y,
            curVel.z / 2.0 - dir.z
        );
        target.hasImpulse = true;
        target.hurtMarked = true;
        if (target instanceof ServerPlayer serverTarget) {
            serverTarget.connection.send(new ClientboundSetEntityMotionPacket(target));
        }

        Level level = target.level();
        double aoeStrength = 10.0;
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
            target.getBoundingBox().inflate(3.5))) {
            if (nearby == target || nearby == attacker) continue;

            double nx = nearby.getX() - target.getX();
            double nz = nearby.getZ() - target.getZ();
            double ndist = Math.sqrt(nx * nx + nz * nz);
            if (ndist < 1.0E-5F) continue;

            Vec3 nDir = new Vec3(nx / ndist, 0.0, nz / ndist).scale(aoeStrength);
            Vec3 nCurVel = nearby.getDeltaMovement();
            nearby.setDeltaMovement(
                nCurVel.x / 2.0 - nDir.x,
                nearby.onGround() ? Math.min(0.4, nCurVel.y / 2.0 + aoeStrength) : nCurVel.y,
                nCurVel.z / 2.0 - nDir.z
            );
            nearby.hasImpulse = true;
            nearby.hurtMarked = true;
            if (nearby instanceof ServerPlayer nearbyServerTarget) {
                nearbyServerTarget.connection.send(new ClientboundSetEntityMotionPacket(nearby));
            }
        }
    }

    private static void applyBellStaffEffect(LivingEntity attacker, Entity target) {
        Level level = attacker.level();

        if (level.isClientSide()) return;

        long currentGameTime = level.getGameTime();
        Long immuneUntil = BELL_STAFF_IMMUNITY.get(target.getUUID());
        if (immuneUntil != null && currentGameTime < immuneUntil) {
            return;
        }
        BELL_STAFF_IMMUNITY.put(target.getUUID(), currentGameTime + 400);

        level.playSound(null, target.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2.0F, 1.0F);

        double lookX = Math.sin(attacker.getYRot() * (Math.PI / 180.0));
        double lookZ = -Math.cos(attacker.getYRot() * (Math.PI / 180.0));
        Vec3 curVel = target.getDeltaMovement();
        target.setDeltaMovement(
            curVel.x + lookX * 2.0,
            Math.min(0.4, curVel.y + 0.4),
            curVel.z + lookZ * 2.0
        );
        target.hasImpulse = true;
        target.hurtMarked = true;
        if (target instanceof ServerPlayer serverTarget) {
            serverTarget.connection.send(new ClientboundSetEntityMotionPacket(target));
        }

        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
            target.getBoundingBox().inflate(2.5))) {
            if (nearby == target || nearby == attacker) continue;
            double dx = nearby.getX() - target.getX();
            double dz = nearby.getZ() - target.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 0.001) continue;
            double nearbyKnockback = 1.0;
            Vec3 nv = nearby.getDeltaMovement();
            nearby.setDeltaMovement(
                nv.x + (dx / dist) * nearbyKnockback,
                Math.min(0.4, nv.y + 0.3),
                nv.z + (dz / dist) * nearbyKnockback
            );
            nearby.hasImpulse = true;
            nearby.hurtMarked = true;
            if (nearby instanceof ServerPlayer nearbySP) {
                nearbySP.connection.send(new ClientboundSetEntityMotionPacket(nearby));
            }
        }

        if (target instanceof LivingEntity livingTarget) {
            livingTarget.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 300, 0));
            livingTarget.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 300, 0));
            livingTarget.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 300, 3));
            livingTarget.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 300, 3));
            livingTarget.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300, 1));
            livingTarget.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
        }

        float pitch = 0.9F + level.random.nextFloat() * 0.2F;
        if (target instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new BellRingPayload(target.getId(), pitch));
        }
    }

    private static void applyTotemEffect(Player player, ItemStack stack) {
        player.setHealth(1.0F);
        player.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.PROTECTED_BY_TOTEM);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        player.level().broadcastEntityEvent(player, (byte) 35);
        stack.shrink(1);
    }

    private static boolean isProjectileDamage(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile) return true;
        return source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE);
    }

    private static boolean isBlockableProjectile(Entity projectile) {
        return isSmallProjectile(projectile) || isLargeProjectile(projectile);
    }

    private static boolean isSmallProjectile(Entity projectile) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType());
        if (key == null) return false;
        return ModConfig.SMALL_PROJECTILES.get().contains(key.toString());
    }

    private static boolean isLargeProjectile(Entity projectile) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType());
        if (key == null) return false;
        return ModConfig.LARGE_PROJECTILES.get().contains(key.toString());
    }

    private static void handleBlock(Player player, DamageSource source, Entity projectile, LivingDamageEvent.Pre event) {
        event.setNewDamage(0);
        boolean isLarge = isLargeProjectile(projectile);

        ItemStack blockingItem = null;
        InteractionHand blockingHand = null;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof GlisteringMelonKnifeItem || stack.getItem() instanceof StaffItem) {
                blockingItem = stack;
                blockingHand = hand;
                break;
            }
        }

        if (blockingItem == null) return;

        boolean isStaff = blockingItem.getItem() instanceof StaffItem;
        ItemEnchantments enchants = blockingItem.getEnchantments();
        int unbreakingLevel = getUnbreakingLevel(enchants, player.level());

        if (isLarge && !isStaff) {
            float dropChance = 0.1F * (float) Math.pow(0.7, unbreakingLevel);
            if (player.getRandom().nextFloat() < dropChance) {
                ItemStack dropStack = blockingItem.copy();
                blockingItem.shrink(1);
                player.spawnAtLocation(dropStack);
            }
        }

        float durabilityLossChance = 0.3F * (float) Math.pow(0.7, unbreakingLevel);
        if (player.getRandom().nextFloat() < durabilityLossChance) {
            if (blockingItem.isDamageableItem()) {
                hurtStaff(blockingItem, 1, player,
                    blockingHand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }

        if (projectile instanceof ShulkerBullet) {
            projectile.discard();
        } else if (!projectile.isRemoved()) {
            double speed = projectile.getDeltaMovement().length();
            double rx = player.getRandom().nextGaussian();
            double ry = player.getRandom().nextGaussian();
            double rz = player.getRandom().nextGaussian();
            double len = Math.sqrt(rx * rx + ry * ry + rz * rz);
            projectile.setDeltaMovement(rx / len * speed, ry / len * speed, rz / len * speed);
            projectile.hasImpulse = true;
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    private static int getUnbreakingLevel(ItemEnchantments enchants, Level level) {
        var holder = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING);
        return enchants.getLevel(holder);
    }

    private static int getMendingLevel(ItemEnchantments enchants, Level level) {
        var holder = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.MENDING);
        return enchants.getLevel(holder);
    }

    private static Map<String, Integer> getBlockDurabilities(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.BLOCK_DURABILITIES.get(), new java.util.HashMap<>());
    }

    private static void saveBlockDurability(ItemStack stack, String blockType, int damage) {
        Map<String, Integer> map = new java.util.HashMap<>(getBlockDurabilities(stack));
        map.put(blockType, damage);
        stack.set(ModDataComponents.BLOCK_DURABILITIES.get(), map);
    }

    private static int getSavedBlockDurability(ItemStack stack, String blockType) {
        return getBlockDurabilities(stack).getOrDefault(blockType, 0);
    }

    private static boolean consumeOneXpForBlock(Player player) {
        if (player.experienceLevel <= 0 && player.experienceProgress <= 0) return false;
        player.giveExperiencePoints(-1);
        return true;
    }

    public static void hurtStaff(ItemStack stack, int amount, LivingEntity entity, EquipmentSlot slot) {
        Level level = entity.level();
        if (level.isClientSide()) return;

        if (entity instanceof Player player && player.getAbilities().instabuild) return;

        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        if ("empty".equals(blockType)) {
            stack.hurtAndBreak(amount, entity, slot);
            return;
        }

        int unbreakingLevel = getUnbreakingLevel(stack.getEnchantments(), level);
        int effective = amount;
        if (unbreakingLevel > 0) {
            effective = 0;
            for (int i = 0; i < amount; i++) {
                if (entity.getRandom().nextInt(unbreakingLevel + 1) == 0) {
                    effective++;
                }
            }
        }

        if (effective <= 0) return;

        int blockDamage = StaffItem.getBlockDamage(stack);
        int newBlockDamage = blockDamage + effective;

        // 经验修复仅对玩家生效（生物无经验值，直接累加方块耐久）
        int mendingLevel = getMendingLevel(stack.getEnchantments(), level);
        if (mendingLevel > 0 && newBlockDamage > 0 && entity instanceof Player mendingPlayer) {
            while (newBlockDamage >= 2 && consumeOneXpForBlock(mendingPlayer)) {
                newBlockDamage -= 2;
            }
            newBlockDamage = Math.max(0, newBlockDamage);
        }

        if (newBlockDamage >= StaffItem.MAX_BLOCK_DURABILITY) {
            int excess = newBlockDamage - StaffItem.MAX_BLOCK_DURABILITY;
            StaffItem.setBlockDamage(stack, 0);
            saveBlockDurability(stack, blockType, 0);

            Item blockItem = STAFF_BLOCKTYPE_REVERSE.get(blockType);
            if (blockItem == null && "omega".equals(blockType)) {
                blockItem = ModItems.OMEGA_GAME_ICON.get();
            }
            if (blockItem == null && "minecraft_game_icon".equals(blockType)) {
                blockItem = ModItems.GAME_ICON.get();
            }
            if (blockItem != null && blockItem != Items.AIR) {
                Block block = Block.byItem(blockItem);
                level.levelEvent(2001, BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ()),
                    Block.getId(block.defaultBlockState()));
            }

            stack.set(ModDataComponents.BLOCKTYPE.get(), "empty");

            if (excess > 0) {
                stack.hurtAndBreak(excess, entity, slot);
            }
        } else {
            StaffItem.setBlockDamage(stack, newBlockDamage);
            saveBlockDurability(stack, blockType, newBlockDamage);
        }
    }

    private static void applyCustomEnchantmentDamage(LivingDamageEvent.Pre event, LivingEntity target,
                                                      ItemStack weapon, LivingEntity attacker, Level level) {
        float attackStrength = attacker instanceof Player player
            ? player.getAttackStrengthScale(0.5F) : 1.0F;
        float scaledBase = 20.0F * (0.2F + attackStrength * attackStrength * 0.8F);
        float originalTotal = event.getOriginalDamage();

        ItemEnchantments enchants = weapon.getEnchantments();
        int sharpnessLevel = getSharpnessLevel(enchants, level);
        int smiteLevel = getSmiteLevel(enchants, level);

        float vanillaEnchantExtra = originalTotal - scaledBase;
        float customEnchantExtra = 0;

        if (sharpnessLevel > 0 && !(smiteLevel > 0 && target.getType().is(EntityTypeTags.SENSITIVE_TO_SMITE))) {
            float vanillaSharpness = (0.5F * sharpnessLevel + 0.5F) * attackStrength;
            vanillaEnchantExtra -= vanillaSharpness;
            customEnchantExtra += vanillaSharpness * 2;
        }

        if (smiteLevel > 0 && target.getType().is(EntityTypeTags.SENSITIVE_TO_SMITE)) {
            float smiteDamage = scaledBase * smiteLevel * 5;
            if (originalTotal > 0) {
                event.setNewDamage(event.getNewDamage() * smiteDamage / originalTotal);
            }
            return;
        }

        float customTotal = scaledBase + vanillaEnchantExtra + customEnchantExtra;
        if (originalTotal > 0 && customTotal > 0) {
            event.setNewDamage(event.getNewDamage() * customTotal / originalTotal);
        }
    }

    private static int getSharpnessLevel(ItemEnchantments enchants, Level level) {
        var holder = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS);
        return enchants.getLevel(holder);
    }

    private static int getSmiteLevel(ItemEnchantments enchants, Level level) {
        var holder = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SMITE);
        return enchants.getLevel(holder);
    }

    private static void handleStaffMeleeBlock(Player player, DamageSource source, LivingDamageEvent.Pre event) {
        ItemStack blockingItem = null;
        InteractionHand blockingHand = null;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof StaffItem || stack.getItem() instanceof GlisteringMelonKnifeItem) {
                blockingItem = stack;
                blockingHand = hand;
                break;
            }
        }
        if (blockingItem == null) return;

        event.setNewDamage(0);

        ItemEnchantments enchants = blockingItem.getEnchantments();
        int unbreakingLevel = getUnbreakingLevel(enchants, player.level());
        float durabilityLossChance = 0.3F * (float) Math.pow(0.7, unbreakingLevel);
        if (player.getRandom().nextFloat() < durabilityLossChance) {
            if (blockingItem.isDamageableItem()) {
                hurtStaff(blockingItem, 1, player,
                    blockingHand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    private static void handleStaffBlockTypeSwap(Player player) {
        // 蜘蛛网权杖：中键优先用于解除瞄准实体的权杖“无效化”（可重复点击），
        // 仅在瞄准无效化实体时覆盖权杖形态切换。
        if (isHoldingCobwebStaff(player) && handleCobwebMiddleClick(player)) {
            return;
        }

        ItemStack staffStack = null;
        InteractionHand staffHand = null;

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof StaffItem) {
                staffStack = stack;
                staffHand = hand;
                break;
            }
        }
        if (staffStack == null) return;

        InteractionHand offHand = staffHand == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack offStack = player.getItemInHand(offHand);
        Item offItem = offStack.getItem();

        String currentBlockType = staffStack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        String offBlockType = STAFF_BLOCKTYPE_WHITELIST.getOrDefault(offItem, null);

        if (offBlockType == null && offItem == ModItems.OMEGA_GAME_ICON.get()) {
            offBlockType = "omega";
        }
        if (offBlockType == null && offItem == ModItems.GAME_ICON.get()) {
            offBlockType = "minecraft_game_icon";
        }

        // The Him staff (herobrine_head) cannot be crafted from any block/item.
        // Middle-clicking it (with an empty offhand) triggers its special dismantle
        // behaviour: a power-3 explosion that does not destroy terrain, plus the
        // return of a player head carrying a random profile name.
        if ("herobrine_head".equals(currentBlockType)) {
            if ("empty".equals(offBlockType)) {
                dismantleHerobrineStaff(player, staffStack, offHand);
            }
            return;
        }

        if (offBlockType == null) {
            return;
        }

        if (offBlockType.equals(currentBlockType)) {
            return;
        }

        // Omega 权杖：无论玩家处于何种模式，都无法拿下 omega game icon
        // （中键拆解或切换为其他方块类型均被拒绝），造成 10 点虚空伤害并显示提示。
        if ("omega".equals(currentBlockType) && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.hurt(serverPlayer.damageSources().fellOutOfWorld(), 10.0F);
            PacketDistributor.sendToPlayer(serverPlayer, new OmegaDismantlePayload());
            return;
        }

        if ("empty".equals(currentBlockType)) {
            int savedDmg = getSavedBlockDurability(staffStack, offBlockType);
            StaffItem.setBlockDamage(staffStack, savedDmg);
            staffStack.set(ModDataComponents.BLOCKTYPE.get(), offBlockType);
            offStack.shrink(1);
        } else if ("empty".equals(offBlockType)) {
            int currentBlockDmg = StaffItem.getBlockDamage(staffStack);
            saveBlockDurability(staffStack, currentBlockType, currentBlockDmg);
            StaffItem.setBlockDamage(staffStack, 0);
            Item returnItem = STAFF_BLOCKTYPE_REVERSE.get(currentBlockType);
            if (returnItem == null && "omega".equals(currentBlockType)) {
                returnItem = ModItems.OMEGA_GAME_ICON.get();
            }
            if (returnItem == null && "minecraft_game_icon".equals(currentBlockType)) {
                returnItem = ModItems.GAME_ICON.get();
            }
            staffStack.set(ModDataComponents.BLOCKTYPE.get(), "empty");
            if (returnItem != null && returnItem != Items.AIR) {
                player.setItemInHand(offHand, new ItemStack(returnItem));
            }
        } else {
            int currentBlockDmg = StaffItem.getBlockDamage(staffStack);
            saveBlockDurability(staffStack, currentBlockType, currentBlockDmg);
            int savedDmg = getSavedBlockDurability(staffStack, offBlockType);
            StaffItem.setBlockDamage(staffStack, savedDmg);
            Item returnItem = STAFF_BLOCKTYPE_REVERSE.get(currentBlockType);
            if (returnItem == null && "omega".equals(currentBlockType)) {
                returnItem = ModItems.OMEGA_GAME_ICON.get();
            }
            if (returnItem == null && "minecraft_game_icon".equals(currentBlockType)) {
                returnItem = ModItems.GAME_ICON.get();
            }
            staffStack.set(ModDataComponents.BLOCKTYPE.get(), offBlockType);
            offStack.shrink(1);
            if (returnItem != null && returnItem != Items.AIR) {
                ItemEntity drop = new ItemEntity(player.level(),
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    new ItemStack(returnItem));
                drop.setPickUpDelay(10);
                player.level().addFreshEntity(drop);
            }
        }

        if (player.level() instanceof ServerLevel serverLevel) {
            String newBlockType = staffStack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
            spawnStaffCraftingParticles(serverLevel, player, newBlockType);
        }
    }

    /**
     * 客户端中键请求：主手与副手都持有 minecraft game icon 时，消耗两者并在主手合成为 omega game icon。
     */
    private static void handleGameIconCraft(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        ItemStack mainStack = player.getMainHandItem();
        ItemStack offStack = player.getOffhandItem();
        if (mainStack.getItem() != ModItems.GAME_ICON.get()) return;
        if (offStack.getItem() != ModItems.GAME_ICON.get()) return;

        mainStack.shrink(1);
        offStack.shrink(1);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.OMEGA_GAME_ICON.get()));
        spawnStaffCraftingParticles(serverLevel, player, "omega");
    }

    private static void dismantleHerobrineStaff(Player player, ItemStack staffStack, InteractionHand offHand) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        saveBlockDurability(staffStack, "herobrine_head", StaffItem.getBlockDamage(staffStack));
        StaffItem.setBlockDamage(staffStack, 0);
        staffStack.set(ModDataComponents.BLOCKTYPE.get(), "empty");

        // Power-3 explosion that does not destroy terrain (ExplosionInteraction.NONE
        // maps to Explosion.BlockInteraction.KEEP, so blocks are kept intact).
        serverLevel.explode(player, player.getX(), player.getY(), player.getZ(), 3.0F,
            Level.ExplosionInteraction.NONE);

        // Return a player head with a random profile name, equivalent to:
        // /give @p minecraft:player_head[minecraft:profile={name:"XXX"}]
        ItemStack head = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, new ResolvableProfile(
            java.util.Optional.of(generateRandomHeadName(player.getRandom())),
            java.util.Optional.empty(),
            new PropertyMap()));
        player.setItemInHand(offHand, head);
    }

    private static String generateRandomHeadName(net.minecraft.util.RandomSource random) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_";
        StringBuilder sb = new StringBuilder();
        int length = 3 + random.nextInt(6);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static void onEntityStruckByLightning(EntityStruckByLightningEvent event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel)) return;

        // 引雷生成、不伤及攻击者的闪电：跳过其施法者（攻击者）被击中。
        if (event.getLightning().getPersistentData().contains(CHANNELING_LIGHTNING_OWNER)
            && entity != null && entity.isAlive()
            && entity.getUUID().equals(event.getLightning().getPersistentData().getUUID(CHANNELING_LIGHTNING_OWNER))) {
            event.setCanceled(true);
            return;
        }

        if (!(entity instanceof LivingEntity living)) return;

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = living.getItemInHand(hand);
            if (stack.getItem() instanceof StaffItem) {
                String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
                if ("player_head".equals(blockType)) {
                    stack.set(ModDataComponents.BLOCKTYPE.get(), "herobrine_head");
                }
            }
        }
    }

    private static void handleCommandStaffAction(Player player, CommandStaffActionPayload payload) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        CommandStaffStorage storage = CommandStaffStorage.getInstance();

        switch (payload.actionType()) {
            case CommandStaffActionPayload.ACTION_EXECUTE: {
                String command = payload.commandText().trim();
                if (command.isEmpty()) break;

                if (command.startsWith("/runpreset ")) {
                    String presetName = command.substring("/runpreset ".length()).trim();
                    handleRunPreset(serverLevel, player, presetName);
                } else {
                    String commandToExecute = command.startsWith("/") ? command.substring(1) : command;
                    executeCommandStaffCommand(serverLevel, player, commandToExecute);
                    storage.tryRecordCommand(command);
                    // 玩家头上方渲染对应指令的 Text Display（单条命令：居中显示 20 刻）
                    showCommandTexts(serverLevel, player, java.util.Collections.singletonList(command));
                }
                break;
            }
            case CommandStaffActionPayload.ACTION_SAVE_PRESET: {
                storage.savePreset(payload.presetName(), payload.presetCommands());
                break;
            }
            case CommandStaffActionPayload.ACTION_DELETE_PRESET: {
                storage.deletePreset(payload.presetName());
                break;
            }
            case CommandStaffActionPayload.ACTION_RENAME_PRESET: {
                storage.renamePreset(payload.presetName(), payload.newPresetName());
                break;
            }
            case CommandStaffActionPayload.ACTION_REQUEST_SYNC:
                break;
        }
        syncCommandStaffData(player);
    }

    private static void handleRunPreset(ServerLevel level, Player player, String presetName) {
        CommandStaffStorage storage = CommandStaffStorage.getInstance();
        java.util.List<String> commands = storage.getPresetCommands(presetName);
        if (commands.isEmpty()) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("Preset not found: " + presetName), false);
            return;
        }
        for (String cmd : commands) {
            String toExecute = cmd.trim();
            if (toExecute.isEmpty()) continue;
            if (toExecute.startsWith("/")) toExecute = toExecute.substring(1);
            executeCommandStaffCommand(level, player, toExecute);
        }
        // 运行预设：玩家头上方以多条 Text Display 逐条渲染命令（每 2 刻一条，最后一条 20 刻）
        showCommandTexts(level, player, commands);
    }

    /** 执行一条命令方块权杖指令：先解析该指令的实体选择器，随后执行命令，并在玩家与每个受影响的实体之间渲染 END_ROD 粒子连线。
     *  每条线仅在执行这一游戏刻发射一批粒子（粒子自身的漂移/消散动画随后自然播完）。 */
    private static void executeCommandStaffCommand(ServerLevel level, Player player, String command) {
        if (command.isEmpty()) return;
        net.minecraft.commands.CommandSourceStack src = player.createCommandSourceStack().withSuppressedOutput();
        java.util.List<? extends net.minecraft.world.entity.Entity> affected =
            resolveCommandAffectedEntities(level, src, command);
        level.getServer().getCommands().performPrefixedCommand(src, command);
        spawnCommandAffectedParticles(level, player, affected);
    }

    /** 解析一条指令的实体选择器（@e/@a/@p/@s 等）实际选中的实体，作为“受该指令影响的实体”。
     *  从命令解析上下文的每个参数值里找出 EntitySelector 并解析；解析失败或被非选择器参数等无需理会。 */
    private static java.util.List<? extends net.minecraft.world.entity.Entity> resolveCommandAffectedEntities(
            ServerLevel level, net.minecraft.commands.CommandSourceStack src, String command) {
        java.util.List<net.minecraft.world.entity.Entity> out = new java.util.ArrayList<>();
        try {
            com.mojang.brigadier.ParseResults<net.minecraft.commands.CommandSourceStack> parse =
                level.getServer().getCommands().getDispatcher().parse(command, src);
            for (com.mojang.brigadier.context.ParsedArgument<net.minecraft.commands.CommandSourceStack, ?> arg :
                    parse.getContext().getArguments().values()) {
                Object v = arg.getResult();
                if (v instanceof net.minecraft.commands.arguments.selector.EntitySelector sel) {
                    out.addAll(sel.findEntities(src));
                } else if (v instanceof net.minecraft.world.entity.Entity e) {
                    out.add(e);
                } else if (v instanceof java.util.Collection<?> c) {
                    for (Object o : c) if (o instanceof net.minecraft.world.entity.Entity e) out.add(e);
                }
            }
        } catch (Throwable ignored) {
            // 指令解析失败则不渲染连线，不影响命令本身的执行
        }
        return out;
    }

    /** 在玩家与每个受影响实体之间渲染一条 END_ROD 粒子线（每 0.4 格一颗粒子）。
     *  默认完整渲染；仅当发射者是本地玩家且处于第一人称视角时，跳过其眼位周围 2.5 格内的粒子
     *  （避免第一人称自持权杖时粒子贴脸遮挡屏幕），其余情况（第三人称、其他玩家、专用服务器）完整渲染。
     *  为避免一次性粒子过多影响表现，最多渲染 256 颗。 */
    private static void spawnCommandAffectedParticles(ServerLevel level, Player player,
            java.util.List<? extends net.minecraft.world.entity.Entity> affected) {
        if (affected.isEmpty()) return;
        net.minecraft.world.phys.Vec3 eye = applyLineEmitterOffset(player, player.getEyePosition());
        boolean firstPersonSelf = false;
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            firstPersonSelf = mc != null && mc.player != null
                && mc.player.getUUID().equals(player.getUUID())
                && mc.options.getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON;
        } catch (Throwable ignored) {
            // 专用服务器等客户端不可用场景下不跳过，完整渲染
        }
        // 每个实体独立受限：单条线最多 256 颗粒子；实体越多总粒子上限也越多（与实体数成正比），
        // 避免单实体场景粒子过多
        for (net.minecraft.world.entity.Entity e : affected) {
            if (e == player || !e.isAlive()) continue;
            net.minecraft.world.phys.Vec3 end = e.getBoundingBox().getCenter();
            double dist = eye.distanceTo(end);
            if (dist < 0.1) continue;
            int perLine = 0;
            for (double d = 0.4; d <= dist && perLine < 256; d += 0.4) {
                net.minecraft.world.phys.Vec3 p = eye.lerp(end, d / dist);
                if (firstPersonSelf && p.distanceToSqr(eye) < 6.25) continue;
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    p.x, p.y, p.z, 1, 0, 0, 0, 0.0);
                perLine++;
            }
        }
    }

    private static void syncCommandStaffData(Player player) {
        CommandStaffStorage storage = CommandStaffStorage.getInstance();
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp,
                new CommandStaffSyncPayload(storage.getHistory(), storage.getPresets()));
        }
    }

    // ---------------------------------------------------------------------
    // 命令方块权杖：指令 Text Display 渲染
    // ---------------------------------------------------------------------
    /** 命令方块权杖的延时任务队列（每服务端刻递减计数，到期执行）。 */
    private static final java.util.ArrayList<CommandTextTask> COMMAND_TEXT_TASKS = new java.util.ArrayList<>();

    /** 一条延时任务：ticksLeft 置零时执行 action。 */
    private static final class CommandTextTask {
        int ticksLeft;
        final Runnable action;
        CommandTextTask(int ticks, Runnable action) {
            this.ticksLeft = ticks;
            this.action = action;
        }
    }

    /** 一处分隔的指令 Text Display：每刻跟随对应玩家，直到到期被销毁。 */
    private static final class CommandTextDisplayEntry {
        final net.minecraft.world.entity.Display.TextDisplay display;
        final net.minecraft.world.entity.player.Player owner;
        final double yOffset; // 相对玩家脚底的垂直偏移
        CommandTextDisplayEntry(net.minecraft.world.entity.Display.TextDisplay display,
                                net.minecraft.world.entity.player.Player owner, double yOffset) {
            this.display = display;
            this.owner = owner;
            this.yOffset = yOffset;
        }
    }

    /** 当前存活、需要每刻跟随玩家的指令 Text Display 列表。 */
    private static final java.util.ArrayList<CommandTextDisplayEntry> COMMAND_ACTIVE_TEXT_DISPLAYS =
        new java.util.ArrayList<>();

    /** 女仆（或任意实体）头顶短时浮动文本：跟随锚点实体、到期待销毁。 */
    private static final java.util.List<MaidTextEntry> COMMAND_MAID_TEXTS = new java.util.ArrayList<>();

    private static final class MaidTextEntry {
        final net.minecraft.world.entity.Display.TextDisplay display;
        final Entity anchor;
        final double yOffset;
        int ticksLeft;
        MaidTextEntry(net.minecraft.world.entity.Display.TextDisplay d, Entity a, double y, int t) {
            this.display = d; this.anchor = a; this.yOffset = y; this.ticksLeft = t;
        }
    }

    /** 在指定实体头顶上方短暂渲染一行文字（20 游戏刻，跟随锚点），供女仆/契约者复用。 */
    public static void spawnMaidFloatingText(ServerLevel level, Entity anchor, String text) {
        // 若锚点已有存活浮动文本：替换其文本并重置寿命（连续执行命令时删旧换新，避免重合）
        for (MaidTextEntry e : COMMAND_MAID_TEXTS) {
            if (e.anchor == anchor && e.display != null && !e.display.isRemoved()) {
                try {
                    invokePrivate(e.display, "setText", net.minecraft.network.chat.Component.class,
                        net.minecraft.network.chat.Component.literal(text));
                } catch (Exception ignored) {}
                e.ticksLeft = 20;
                return;
            }
        }
        double yOff = anchor.getBbHeight() + 20.0 / 16.0;
        net.minecraft.world.entity.Display.TextDisplay td =
            new net.minecraft.world.entity.Display.TextDisplay(net.minecraft.world.entity.EntityType.TEXT_DISPLAY, level);
        try {
            com.mojang.math.Transformation identity = new com.mojang.math.Transformation(
                new org.joml.Vector3f(0, 0, 0), new org.joml.Quaternionf(),
                new org.joml.Vector3f(1, 1, 1), new org.joml.Quaternionf());
            invokePrivate(td, "setTransformation", com.mojang.math.Transformation.class, identity);
            invokePrivate(td, "setBillboardConstraints",
                net.minecraft.world.entity.Display.BillboardConstraints.class,
                net.minecraft.world.entity.Display.BillboardConstraints.CENTER);
            invokePrivate(td, "setText", net.minecraft.network.chat.Component.class,
                net.minecraft.network.chat.Component.literal(text));
            invokePrivate(td, "setLineWidth", int.class, 200);
            invokePrivate(td, "setTextOpacity", byte.class, (byte) 255);
            invokePrivate(td, "setBackgroundColor", int.class, 0x33000000);
        } catch (Exception ignored) {
        }
        td.setPos(anchor.getX(), anchor.getY() + yOff, anchor.getZ());
        COMMAND_MAID_TEXTS.add(new MaidTextEntry(td, anchor, yOff, 20));
        level.addFreshEntity(td);
    }

    /** 添加一条延时任务。 */
    private static void addCommandTextTask(int ticks, Runnable action) {
        COMMAND_TEXT_TASKS.add(new CommandTextTask(ticks, action));
    }

    /** 每服务端刻推进一次延时任务队列，到期的执行并移除。 */
    private static void runCommandTextTasks(net.minecraft.server.MinecraftServer server) {
        // 先让所有活着的指令 Text Display 跟随其玩家移动
        updateCommandTextDisplayPositions();
        // 女仆/契约者头顶浮动文本的跟随与到期销毁
        updateMaidTextDisplays();

        if (COMMAND_TEXT_TASKS.isEmpty()) return;
        // 先将“到期行为”与“未到期任务”分离后再运行，避免在执行 action 期间由
        // addCommandTextTask 往同一队列新增任务，导致迭代 remove 时触发 ConcurrentModificationException。
        java.util.List<Runnable> due = new java.util.ArrayList<>();
        java.util.List<CommandTextTask> keep = new java.util.ArrayList<>();
        for (CommandTextTask t : COMMAND_TEXT_TASKS) {
            t.ticksLeft--;
            if (t.ticksLeft <= 0) {
                due.add(t.action);
            } else {
                keep.add(t);
            }
        }
        COMMAND_TEXT_TASKS.clear();
        COMMAND_TEXT_TASKS.addAll(keep);
        for (Runnable r : due) {
            try {
                r.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /** 每服务端刻将存活中的指令 Text Display 对齐到其所属玩家的头部上方偏移处。 */
    private static void updateCommandTextDisplayPositions() {
        if (COMMAND_ACTIVE_TEXT_DISPLAYS.isEmpty()) return;
        java.util.Iterator<CommandTextDisplayEntry> it = COMMAND_ACTIVE_TEXT_DISPLAYS.iterator();
        while (it.hasNext()) {
            CommandTextDisplayEntry e = it.next();
            if (e.display == null || !e.display.isAlive()
                    || e.owner == null || !e.owner.isAlive()
                    || e.owner.level() != e.display.level()) {
                it.remove();
                continue;
            }
            e.display.setPos(e.owner.getX(), e.owner.getY() + e.yOffset, e.owner.getZ());
        }
    }

    /** 每服务端刻：让女仆/契约者头顶的浮动文本跟随锚点，到期销毁。 */
    private static void updateMaidTextDisplays() {
        if (COMMAND_MAID_TEXTS.isEmpty()) return;
        java.util.Iterator<MaidTextEntry> it = COMMAND_MAID_TEXTS.iterator();
        while (it.hasNext()) {
            MaidTextEntry e = it.next();
            if (e.display == null || !e.display.isAlive()
                || e.anchor == null || !e.anchor.isAlive()
                || e.anchor.level() != e.display.level()) {
                it.remove();
                continue;
            }
            e.display.setPos(e.anchor.getX(), e.anchor.getY() + e.yOffset, e.anchor.getZ());
            if (--e.ticksLeft <= 0) {
                e.display.discard();
                it.remove();
            }
        }
    }

    /** 让一条指令 Text Display 到期销毁：从跟随队列移除再销毁实体。 */
    private static void discardCommandText(net.minecraft.world.entity.Display.TextDisplay td) {
        if (td == null) return;
        COMMAND_ACTIVE_TEXT_DISPLAYS.removeIf(e -> e.display == td);
        td.discard();
    }

    /**
     * 玩家头上方短暂渲染指令 Text Display。
     * <ul>
     *   <li>单条命令：在世界“玩家头上方 20 像素”处居中显示 20 游戏刻；</li>
     *   <li>多条命令（如运行预设）：每 2 游戏刻都在同一高度逐条渲染，最后一条渲染 20 游戏刻。</li>
     * </ul>
     */
    private static void showCommandTexts(ServerLevel level, Player player, java.util.List<String> commands) {
        java.util.List<String> texts = new java.util.ArrayList<>();
        for (String c : commands) {
            String s = c.trim();
            if (!s.isEmpty()) texts.add(s);
        }
        if (texts.isEmpty()) return;

        // 玩家眼睛高度 + 头顶上方 20 像素（16 像素 = 1 格，故 +20/16），相对玩家脚底的偏移
        double headOffset = player.getEyeHeight() + 20.0 / 16.0;

        if (texts.size() == 1) {
            final double yOffset = headOffset;
            final String text = texts.get(0);
            addCommandTextTask(0, () -> {
                net.minecraft.world.entity.Display.TextDisplay d =
                    spawnCommandTextDisplay(level, player, text, yOffset);
                addCommandTextTask(20, () -> discardCommandText(d));
            });
            return;
        }

        // 多条命令：都在同一高度渲染，每 2 刻出现一条，最后一条渲染 20 刻
        int n = texts.size();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final double yOffset = headOffset; // 所有命令渲染在同一高度
            final String text = texts.get(idx);
            addCommandTextTask(idx * 2, () -> {
                net.minecraft.world.entity.Display.TextDisplay d =
                    spawnCommandTextDisplay(level, player, text, yOffset);
                int linger = (idx == n - 1) ? 20 : 2;
                addCommandTextTask(linger, () -> discardCommandText(d));
            });
        }
    }

    /** 在玩家头的指定高度生成一个 billboard 居中的 Text Display 实体并返回它（yOffset 相对玩家脚底）。 */
    private static net.minecraft.world.entity.Display.TextDisplay spawnCommandTextDisplay(
            ServerLevel level, Player player, String text, double yOffset) {
        // 若该玩家已有存活指令文本：直接替换其文本而非新增（连续执行命令时删旧换新，避免重合）
        for (CommandTextDisplayEntry e : COMMAND_ACTIVE_TEXT_DISPLAYS) {
            if (e.owner == player && e.display != null && !e.display.isRemoved()) {
                try {
                    invokePrivate(e.display, "setText", net.minecraft.network.chat.Component.class,
                        net.minecraft.network.chat.Component.literal(text));
                } catch (Exception ignored) {}
                return e.display;
            }
        }
        net.minecraft.world.entity.Display.TextDisplay td =
            new net.minecraft.world.entity.Display.TextDisplay(net.minecraft.world.entity.EntityType.TEXT_DISPLAY, level);
        try {
            com.mojang.math.Transformation identity = new com.mojang.math.Transformation(
                new org.joml.Vector3f(0, 0, 0), new org.joml.Quaternionf(),
                new org.joml.Vector3f(1, 1, 1), new org.joml.Quaternionf());
            invokePrivate(td, "setTransformation", com.mojang.math.Transformation.class, identity);
            invokePrivate(td, "setBillboardConstraints",
                net.minecraft.world.entity.Display.BillboardConstraints.class,
                net.minecraft.world.entity.Display.BillboardConstraints.CENTER);
            invokePrivate(td, "setText", net.minecraft.network.chat.Component.class,
                net.minecraft.network.chat.Component.literal(text));
            invokePrivate(td, "setLineWidth", int.class, 200);
            invokePrivate(td, "setTextOpacity", byte.class, (byte) 255);
            invokePrivate(td, "setBackgroundColor", int.class, 0x33000000); // 半透明底，观感更清晰
        } catch (Exception ignored) {
        }
        td.setPos(player.getX(), player.getY() + yOffset, player.getZ());
        // 先登记进「跟随玩家列表」再加入世界：EntityJoinLevelEvent 里据此区分本次会话正常创建的
        // 指令文本与跨存档遗留实体（遗留的未登记，会被直接拦截、不进入世界）。
        COMMAND_ACTIVE_TEXT_DISPLAYS.add(new CommandTextDisplayEntry(td, player, yOffset));
        level.addFreshEntity(td);
        // 打上持久数据标记：区块加载时据此识别并清理跨存档遗留的指令文本实体
        td.getPersistentData().putBoolean(COMMAND_TEXT_TAG, true);
        return td;
    }

    /** 反射调用 private setter（Mojank Official Mappings 下 Display setter 均为 private，无法直接调用）。 */
    private static void invokePrivate(Object target, String methodName, Class<?> paramType, Object value)
            throws Exception {
        java.lang.reflect.Method m = null;
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                m = c.getDeclaredMethod(methodName, paramType);
                break;
            } catch (NoSuchMethodException ignored) {
            }
        }
        if (m == null) return;
        m.setAccessible(true);
        m.invoke(target, value);
    }

    private static void spawnStaffCraftingParticles(ServerLevel serverLevel, Player player, String blockType) {
        Vector3f color;
        switch (blockType) {
            case "gold_block":      color = new Vector3f(242F / 255F, 219F / 255F, 12F / 255F); break;
            case "netherite_block": color = new Vector3f(75F / 255F, 72F / 255F, 75F / 255F); break;
            case "diamond_block":   color = new Vector3f(99F / 255F, 240F / 255F, 222F / 255F); break;
            case "bedrock":         color = new Vector3f(85F / 255F, 85F / 255F, 85F / 255F); break;
            case "obsidian":        color = new Vector3f(58F / 255F, 38F / 255F, 82F / 255F); break;
            case "bone_block":      color = new Vector3f(228F / 255F, 226F / 255F, 206F / 255F); break;
            case "furnace":         color = new Vector3f(155F / 255F, 155F / 255F, 155F / 255F); break;
            case "bell":            color = new Vector3f(218F / 255F, 165F / 255F, 32F / 255F); break;
            case "anvil":           color = new Vector3f(64F / 255F, 64F / 255F, 64F / 255F); break;
            case "lapis_block":     color = new Vector3f(20F / 255F, 96F / 255F, 212F / 255F); break;
            case "magma_block":     color = new Vector3f(172F / 255F, 56F / 255F, 6F / 255F); break;
            case "omega":           color = new Vector3f(234F / 255F, 139F / 255F, 174F / 255F); break;
            case "command_block":   color = new Vector3f(136F / 255F, 197F / 255F, 234F / 255F); break;
            case "end_portal_frame": color = new Vector3f(26F / 255F, 90F / 255F, 94F / 255F); break;
            default: return;
        }
        var dustOptions = new DustParticleOptions(color, 1.0F);
        for (int i = 0; i < 15; i++) {
            double ox = (player.getRandom().nextDouble() - 0.5) * 1.5;
            double oy = player.getRandom().nextDouble() * 2.0;
            double oz = (player.getRandom().nextDouble() - 0.5) * 1.5;
            serverLevel.sendParticles(
                dustOptions,
                player.getX() + ox, player.getY() + oy, player.getZ() + oz,
                1, 0.0, 0.0, 0.0, 0.0
            );
        }
    }

    // 游戏图标掉落物：永不自然消失、免疫一切伤害、y=-64 处的虚空屏障
    private static void handleIndestructibleGameIconTick(ItemEntity item) {
        // 永不自然消失
        item.lifespan = Integer.MAX_VALUE;
        // 免疫一切伤害（无敌使 hurt() 直接返回 false，涵盖火、熔岩、爆炸、仙人掌等）
        item.setInvulnerable(true);
        // 灭火，避免持续燃烧
        item.clearFire();

        if (item.getY() <= -64.0) {
            // 到达或低于 y=-64：开启无重力，如同存在屏障般停在 -64
            item.setNoGravity(true);
            if (item.getY() < -64.0) {
                // 在 -64 以下（例如被扔入虚空深处）：缓慢上浮至 -64
                item.setDeltaMovement(item.getDeltaMovement().x, 0.1, item.getDeltaMovement().z);
            } else {
                // 恰好 -64：停住，不再下落
                item.setDeltaMovement(0, 0, 0);
            }
        } else {
            // 高于 -64：恢复正常重力
            item.setNoGravity(false);
        }
    }

    private static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        // 冰块权杖：在移动/AI 生效前，记录被霜冰冻住的生物本 tick 起始位置（会飞的生物也适用）。
        if (entity instanceof LivingEntity living
            && !(entity instanceof net.minecraft.world.entity.player.Player)) {
            if (isOverlappingFrost(living)) {
                FROST_PRE_POSITIONS.put(living.getId(), living.position());
            } else {
                FROST_PRE_POSITIONS.remove(living.getId());
            }
        }

        // 附魔生物·持续效果：效率(急迫)、激流/深海探索者/灵魂疾行(移动速度)、
        // 冰霜行者(冻结水面)、忠诚/绑定诅咒(远距传送)。
        if (entity instanceof LivingEntity livingEnch) {
            handleEnchantContinuousTick(livingEnch);
        }

        // 音符方块宇宙：中和凋灵副头攻击。把副头的下一轮更新计数值推到极大值，
        // 使 customServerAiStep 中副头的发射逻辑（含空闲射击与按目标射击）永不执行，
        // 从而既不放凋灵头颅，也不播放攻击音效。
        if (entity instanceof WitherBoss witherBoss
            && entity.level().dimension() == ModDimensions.NOTE_DIM_LEVEL) {
            neutralizeWitherSideHeads(witherBoss);
        }

        if (entity instanceof ItemEntity itemEntity) {
            if (itemEntity.getItem().is(ModItems.GAME_ICON.get())
                || itemEntity.getItem().is(ModItems.OMEGA_GAME_ICON.get())) {
                handleIndestructibleGameIconTick(itemEntity);
            } else if (itemEntity.getItem().is(Items.DROPPER)) {
                Level level = itemEntity.level();
                BlockPos pos = itemEntity.blockPosition();
                FluidState fluid = level.getFluidState(pos);
                if (!fluid.is(FluidTags.WATER)) {
                    pos = pos.below();
                    fluid = level.getFluidState(pos);
                }
                if (fluid.is(FluidTags.WATER) && fluid.isSource() && level instanceof ServerLevel serverLevel) {
                    BlockPos portalCenter = LuckyPortalBlockEntity.detectPortalStructure(serverLevel, pos);
                    if (portalCenter != null) {
                        // 防止同一投掷器在等待期间每 tick 重复触发
                        int eid = itemEntity.getId();
                        boolean alreadyPending = false;
                        for (PortalActivationTask t : PENDING_PORTAL_ACTIVATIONS) {
                            if (t.itemEntityId == eid) { alreadyPending = true; break; }
                        }
                        if (alreadyPending) return;
                        // 投掷器入水后不立即消失，延迟 1~1.5 秒（20~30 tick）后召唤闪电并激活传送门，届时投掷器才消失
                        PortalActivationTask task = new PortalActivationTask();
                        task.level = serverLevel;
                        task.waterPos = pos.immutable();
                        task.itemEntityId = eid;
                        task.tickDelay = 20 + serverLevel.getRandom().nextInt(11);
                        PENDING_PORTAL_ACTIVATIONS.add(task);
                    }
                }
            }
            return;
        }

        if (isPhysicsDimension(entity.level())) {
            handlePhysicsDimensionTick(entity);
        } else if (PHYSICS_DIM_ENTITIES.contains(entity.getUUID())) {
            cleanupPhysicsDimensionState(entity);
        }

        // --- 怪物学校末影人：附近有玩家时立刻敌对（无视是否对视） ---
        if (entity instanceof EnderMan enderman && isMonsterSchool(enderman)) {
            handleMonsterSchoolEndermanTick(enderman);
        }

        // --- 出售命令方块的图书管理员：1% 概率授予大师级交易，已售出后保持锁定 ---
        if (entity instanceof Villager villager
            && villager.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN) {
            handleCommandBlockLibrarianTick(villager);
        }

        if (entity instanceof Player player) {
            handleLapisStaffFlight(player);
            handleCommandStaffFlight(player);
            handleHerobrineStaffFlight(player);
        }
    }

    private static void handleCommandBlockLibrarianTick(Villager villager) {
        CompoundTag tag = villager.getPersistentData();
        boolean forced = tag.getBoolean(SELLS_COMMAND_BLOCK_TAG);
        boolean offered = tag.getBoolean(COMMAND_BLOCK_OFFERED_TAG);

        // 未授予过交易：仅在大师级时授予（强制标签的 debug 村民也会等到大师级才解锁）
        if (!offered && villager.getVillagerData().getLevel() >= VillagerData.MAX_VILLAGER_LEVEL) {
            tag.putBoolean(COMMAND_BLOCK_OFFERED_TAG, true);
            ServerLevel level = (ServerLevel) villager.level();
            SharedCounts counts = getSharedCounts(level);
            boolean grant;
            if (forced) {
                // debug 强制标签：必定授予，不参与共享保底计数
                grant = true;
            } else if (counts.librarianCount >= LIBRARIAN_PITY_COUNT) {
                // 保底：已连续 300 只未卖出 → 本次必给
                grant = true;
                counts.librarianCount = 0;
            } else {
                // 1% 概率授予
                grant = villager.getRandom().nextInt(100) < 1;
                if (grant) {
                    counts.librarianCount = 0;
                } else {
                    counts.librarianCount++;
                }
            }
            if (grant) {
                villager.getOffers().add(createCommandBlockOffer());
                // 卖命令方块的图书管理员永久自带发光状态效果
                villager.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false), villager);
            }
            counts.setDirty();
            return;
        }

        // 已售出：保持所有命令方块交易永久缺货，即使补货也不会恢复
        if (tag.getBoolean(COMMAND_BLOCK_SOLD_TAG)) {
            for (MerchantOffer offer : villager.getOffers()) {
                if (offer.getResult().is(Items.COMMAND_BLOCK)) {
                    offer.setToOutOfStock();
                }
            }
        }
    }

    private static MerchantOffer createCommandBlockOffer() {
        // 潜影盒（未染色，作为展示项；搭配 mixin 后任意颜色均可匹配）内装满 27 × 64 = 1728 个绿宝石块
        NonNullList<ItemStack> contents = NonNullList.withSize(COMMAND_BLOCK_TRADE_SLOTS, ItemStack.EMPTY);
        ItemStack emeraldBlock = new ItemStack(Items.EMERALD_BLOCK, EMERALD_BLOCKS_PER_SLOT);
        for (int i = 0; i < COMMAND_BLOCK_TRADE_SLOTS; i++) {
            contents.set(i, emeraldBlock);
        }
        ItemContainerContents boxContents = ItemContainerContents.fromItems(contents);
        DataComponentPredicate predicate = DataComponentPredicate.builder()
            .expect(DataComponents.CONTAINER, boxContents)
            .build();
        ItemCost cost = new ItemCost(Items.SHULKER_BOX.builtInRegistryHolder(), 1, predicate);
        // maxUses=1：单次交易；配合 SOLD 标记在补货时保持缺货
        return new MerchantOffer(cost, new ItemStack(Items.COMMAND_BLOCK, 1), 1, 5, 0.05F);
    }

    /**
     * 女巫小屋（沼泽小屋）·女巫 Boss 生成判定。
     * 逻辑：
     * 1. 判断本女巫是否在女巫小屋结构内（用 SWAMP_HUT 结构 piece 判定）；
     * 2. 若在，则按共享保底计数决定是否把它变异为女巫 Boss：
     *    - 已累计 200 座小屋未出现（witchHutCount >= 200）→ 本座必出 Boss，计数清零；
     *    - 否则 1% 概率变 Boss，命中则清零，未命中则计数 +1。
     * 每次只判定一次（命中后写入标签，避免反复触发）。
     */
    private static void handleWitchHutBossSpawn(ServerLevel level, Witch witch) {
        // 仅判定处于沼泽小屋结构内的女巫
        StructureStart start = level.structureManager().getStructureWithPieceAt(
            witch.blockPosition(), h -> h.is(BuiltinStructures.SWAMP_HUT));
        if (start == StructureStart.INVALID_START || !start.isValid()) return;

        SharedCounts counts = getSharedCounts(level);
        boolean boss;
        if (counts.witchHutCount >= WITCH_BOSS_PITY_HUTS) {
            // 保底：第 201 座必出
            boss = true;
        } else {
            boss = witch.getRandom().nextInt(100) < 1; // 1%
        }

        if (boss) {
            witch.getPersistentData().putBoolean(WITCH_BOSS_TAG, true);
            WITCH_BOSS_TRACKED.add(witch.getUUID());
            initWitchBossHealth(witch);
            // 记录“家”（小屋中心）坐标，供弹药不足时回城补给
            net.minecraft.world.level.levelgen.structure.BoundingBox bb = start.getBoundingBox();
            if (bb != null && bb.getCenter() != null) {
                net.minecraft.core.BlockPos c = bb.getCenter();
                initWitchBossHome(witch, new Vec3(c.getX(), c.getY() + 1.0, c.getZ()));
            }
            initWitchBossAmmo(witch);
            counts.witchHutCount = 0;
            // 记录该小屋所在区块，此后该小屋不再自然刷新女巫
            counts.bossHutChunks.add(start.getChunkPos().toLong());
        } else {
            counts.witchHutCount++;
        }
        counts.setDirty();
    }

    /**
     * Creeper Clan 维度：自然刷怪规则检查。
     * <ul>
     *  <li>苦力怕：强制通过（SUCCEED），从而无视光照等生成条件，仅受刷怪上限限制。</li>
     *  <li>其他生物：强制失败（FAIL），维度内自然生成只会有苦力怕。</li>
     * </ul>
     * 该事件只在原版自然刷怪（NaturalSpawner）的生成规则检查阶段触发，刷怪笼/结构/玩家召唤不受影响。
     */
    private static void onMobSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != ModDimensions.CREEPER_CLAN_DIM_LEVEL) return;
        // 玩家主动召唤（刷怪蛋/命令/繁殖等）不走此事件，此处仅针对自然刷怪，一律要求苦力怕
        boolean isCreeper = event.getEntityType() == net.minecraft.world.entity.EntityType.CREEPER;
        event.setResult(isCreeper
            ? MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED
            : MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }

    /** 是否属于「世界自动生成」类刷怪类型（需在 Creeper Clan 维度拦截，非苦力怕不放行）。
     *  玩家主动召唤（刷怪蛋、/summon、繁殖、转换、桶装、发射器）均返回 false，即放行。 */
    private static boolean isWorldAutoSpawnType(net.minecraft.world.entity.MobSpawnType type) {
        if (type == null) return true; // 未知类型保守拦截
        return switch (type) {
            case NATURAL, CHUNK_GENERATION, STRUCTURE, SPAWNER,
                 REINFORCEMENT, PATROL, JOCKEY, EVENT, TRIGGERED, TRIAL_SPAWNER -> true;
            default -> false; // SPAWN_EGG / COMMAND / BREEDING / MOB_SUMMONED / CONVERSION / BUCKET / DISPENSER 放行
        };
    }

    /**
     * 女巫小屋·女巫 Boss：拥有 Boss 的小屋（其区块已在 bossHutChunks 中登记）周围
     * WITCH_BOSS_NO_SPAWN_RADIUS 格内不再自然刷新女巫（结构自带的女巫 Boss 除外）。
     * 通过拦截女巫的 FinalizeSpawn 事件实现：生成点距任一已登记 Boss 小屋 ≤ 半径即取消生成。
     */
    private static void onMobFinalizeSpawn(FinalizeSpawnEvent event) {
        // Creeper Clan 维度：拦截「世界自动生成」的非苦力怕生物，使其维度内自然只有苦力怕。
        // 判定维度用 event.getLevel().getLevel()（ServerLevelAccessor 可能是 WorldGenRegion 而非 ServerLevel，
        // 用 instanceof ServerLevel 会在区块初始生成时判断失败，导致拦截失效）。
        // 仅拦截自动生成途径（自然刷怪/区块初始生成/结构/刷怪笼/巡逻/袭击/骑乘等），
        // 玩家主动召唤（刷怪蛋 SPAWN_EGG、/summon COMMAND、繁殖 BREEDING、转换 CONVERSION 等）一律放行。
        // 苦力怕放行，且其无视光照由 onMobSpawnPlacementCheck(SpawnPlacementCheck SUCCEED) 强制实现。
        if (event.getLevel() != null
                && event.getLevel().getLevel() != null
                && event.getLevel().getLevel().dimension() == ModDimensions.CREEPER_CLAN_DIM_LEVEL
                && !(event.getEntity() instanceof net.minecraft.world.entity.monster.Creeper)
                && isWorldAutoSpawnType(event.getSpawnType())) {
            event.setSpawnCancelled(true);
            return;
        }

        if (!(event.getEntity() instanceof Witch witch)) return;
        // Boss 本身不在此列（其由小屋结构一次性生成，不经过 finalizeSpawn）
        if (witch.getPersistentData().getBoolean(WITCH_BOSS_TAG)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        SharedCounts counts = getSharedCounts(level);
        if (counts.bossHutChunks.isEmpty()) return;

        // 以每个 Boss 小屋所在区块中心为参照点，计算水平距离
        int spawnX = (int) event.getX();
        int spawnZ = (int) event.getZ();
        int radiusSq = WITCH_BOSS_NO_SPAWN_RADIUS * WITCH_BOSS_NO_SPAWN_RADIUS;
        for (long packedChunk : counts.bossHutChunks) {
            int hutCenterX = ChunkPos.getX(packedChunk) * 16 + 8;
            int hutCenterZ = ChunkPos.getZ(packedChunk) * 16 + 8;
            int dx = spawnX - hutCenterX;
            int dz = spawnZ - hutCenterZ;
            if (dx * dx + dz * dz <= radiusSq) {
                event.setSpawnCancelled(true);
                return;
            }
        }
    }

    /**
     * 女巫 Boss 玩家察觉检测（每刻调用，见 {@code onEntityTickPost}）：
     * 首次将「玩家」（生存/冒险模式）锁定为攻击目标时写入 {@link #WITCH_BOSS_DISCOVERED_TAG}
     * （发现事件仅一次，创造/旁观模式玩家不计入）。该标记只表示“发现过玩家”：
     * {@code WitchMixin} 会在发现后第一次进入「要喝迅捷药水」的场景时进行一次 70% 隐身 roll
     * （无论成败只 roll 这一次），之后所有场景均为常规 10% roll。
     * 标记持久化在实体 NBT 中，跨存档保持一致。
     */
    private static void handleWitchBossPlayerNotice(Witch witch) {
        if (witch.getPersistentData().getBoolean(WITCH_BOSS_DISCOVERED_TAG)) return;
        // 需“连续索敌”玩家达到一定刻数才标记发现（避免召唤瞬间/擦肩而过就触发，防止立刻喝隐身药水）
        CompoundTag noticeData = witch.getPersistentData();
        int ticks = noticeData.getInt(WITCH_BOSS_NOTICE_TICKS_TAG);
        if (!(witch.getTarget() instanceof Player target)
                || !target.isAlive() || target.isCreative() || target.isSpectator()) {
            if (ticks != 0) noticeData.putInt(WITCH_BOSS_NOTICE_TICKS_TAG, 0);
            return;
        }
        if (ticks < WITCH_BOSS_NOTICE_REQUIRED_TICKS) {
            noticeData.putInt(WITCH_BOSS_NOTICE_TICKS_TAG, ticks + 1);
            return;
        }
        noticeData.remove(WITCH_BOSS_NOTICE_TICKS_TAG);
        witch.getPersistentData().putBoolean(WITCH_BOSS_DISCOVERED_TAG, true);
    }

    /** 女巫Boss近身逃逸（阶段1/2，每刻调用）：
     *  当有存活玩家靠得足够近（< {@link #WITCH_BOSS_TP_AVOID_RANGE} 格）时：
     *  1) 提前计算一个“离该玩家 15~20 格、且能容纳女巫尺寸”的合适坐标；
     *  2) 据此造一瓶点传送药水（TRANSPORT_MODE=point + 目标坐标）并让其手持；
     *  3) 女巫把这瓶药水朝着自身脚下丢出 → 落地生成入口门，出口门在目标坐标旁，女巫随即被传送过去。
     *  带冷却防刷；仅阶段1/2触发（阶段3不触发）。 */
    private static void handleWitchBossRetreatTeleport(Witch witch, ServerLevel level) {
        // 阶段限制：仅阶段1(实体)逃逸传送；阶段2(方块)起改为主动靠近近战，不再传送逃逸
        int stage = getWitchBossStage(witch);
        if (stage != WITCH_BOSS_STAGE_ENTITY) return;
        // 冷却
        long gameTime = level.getGameTime();
        long lastTp = witch.getPersistentData().getLong(WITCH_BOSS_TP_CD_TAG);
        if (gameTime - lastTp < WITCH_BOSS_TP_COOLDOWN) return;

        // 找一个靠得太近的存活玩家（观战/创造不计入近身威胁的“被动触发”，但观战肯定不算）
        Player near = null;
        for (Player p : level.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            if (witch.distanceTo(p) < WITCH_BOSS_TP_AVOID_RANGE) { near = p; break; }
        }
        if (near == null) return;

        // 提前计算合适坐标：在“背对玩家方向/随机方向”上，距玩家15~20格找可容纳女巫的空间
        Vec3 nearPos = near.position();
        java.util.Random rand = new java.util.Random(witch.getRandom().nextLong());
        Vec3 exitPos = null;
        for (int tries = 0; tries < 10 && exitPos == null; tries++) {
            double ang = rand.nextDouble() * Math.PI * 2.0;
            double dist = WITCH_BOSS_TP_DIST_MIN
                + rand.nextDouble() * (WITCH_BOSS_TP_DIST_MAX - WITCH_BOSS_TP_DIST_MIN);
            Vec3 rawTarget = nearPos.add(
                new Vec3(Math.cos(ang), 0.0, Math.sin(ang)).scale(dist));
            if (Math.abs(rawTarget.x) >= 29999984.0 || Math.abs(rawTarget.z) >= 29999984.0) continue;
            exitPos = findPortalExitNear(level, rawTarget, 6, witch.getBbWidth(), witch.getBbHeight());
        }
        if (exitPos == null) return;

        // 造一瓶点传送药水（目标坐标 = 出口位置）
        ItemStack potion = new ItemStack(net.minecraft.world.item.Items.SPLASH_POTION);
        potion.set(ModDataComponents.TRANSPORT_MODE.get(), "point");
        potion.set(ModDataComponents.TARGET_POS.get(), exitPos);

        // 女巫手持该药水，朝自身脚下丢出（入口门落在脚下，出口门在 exitPos 旁 → 被传送）
        witch.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, potion);
        net.minecraft.world.entity.projectile.ThrownPotion tp =
            new net.minecraft.world.entity.projectile.ThrownPotion(level, witch);
        tp.setItem(potion);
        tp.setPos(witch.getX(), witch.getEyeY() - 0.1, witch.getZ());
        tp.shoot((rand.nextDouble() - 0.5) * 0.3, -1.0, (rand.nextDouble() - 0.5) * 0.3, 0.5F, 0.0F);
        level.addFreshEntity(tp);

        // 丢出逃逸传送药水 → 消耗传送药水计数（不足则转回屋补给）
        consumeWitchBossAmmo(witch, WITCH_BOSS_AMMO_TRANSPORT_TAG, 1);
        witch.getPersistentData().putLong(WITCH_BOSS_TP_CD_TAG, gameTime);
    }

    /** 该女巫“此刻是否进入近身逃逸判定”（供 RangedAttackGoalMixin 做优先级判断）：
     *  阶段1/2 + 有靠得太近的存活玩家 + 逃逸冷却已到 → true，表示本应优先实施逃逸，
     *  而不是执行弹道自伤“退位调整”、甚至不应丢弃攻击药水。实际丢药水由
     *  {@link #handleWitchBossRetreatTeleport} 在实体 tick 阶段完成。 */
    public static boolean isWitchBossRetreatingNow(Witch witch, ServerLevel level) {
        int stage = getWitchBossStage(witch);
        if (stage != WITCH_BOSS_STAGE_ENTITY) return false; // 仅阶段1逃逸
        long gameTime = level.getGameTime();
        if (gameTime - witch.getPersistentData().getLong(WITCH_BOSS_TP_CD_TAG) < WITCH_BOSS_TP_COOLDOWN) return false;
        for (Player p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && witch.distanceTo(p) < WITCH_BOSS_TP_AVOID_RANGE) {
                return true;
            }
        }
        return false;
    }

    /** 女巫Boss是否处于近战状态（阶段3持剑近战，用于高位禁止远程投掷/规避/逃逸）。 */
    public static boolean isWitchBossInMelee(Witch witch) {
        return witch.getPersistentData().getBoolean(WITCH_BOSS_MELEE_TAG);
    }

    /** 女巫Boss阶段3近战（每刻调用）：
     *  玩家靠得过近（< {@link #WITCH_BOSS_MELEE_RANGE}）时，以 50% 概率进入近战状态，切换为钻石剑，
     *  按近战间隔挥剑攻击最近玩家；此状态不再传送/不再弹道自伤规避（由调用方据此禁止）。
     *  玩家远离后退出近战状态（收剑），恢复远程行为，下次靠近可再次判定。 */
    private static void handleWitchBossMelee(Witch witch, ServerLevel level) {
        if (getWitchBossStage(witch) != WITCH_BOSS_STAGE_ITEM) return; // 仅阶段3
        CompoundTag data = witch.getPersistentData();

        // 阶段3：若玩家已变形成物品，则只持续打火石，绝不用钻石剑近战
        if (hasItemTransmutedPlayer(level)) {
            if (data.getBoolean(WITCH_BOSS_MELEE_TAG)) {
                data.putBoolean(WITCH_BOSS_MELEE_TAG, false);
                witch.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            return;
        }

        // 找最近存活的非观战玩家
        Player near = null;
        double best = Double.MAX_VALUE;
        for (Player p : level.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            double d = witch.distanceTo(p);
            if (d < best) { best = d; near = p; }
        }
        double dist = (near != null) ? best : Double.MAX_VALUE;
        boolean inRange = near != null && dist < WITCH_BOSS_MELEE_RANGE;

        boolean wasNear = data.getBoolean(WITCH_BOSS_MELEE_WAS_NEAR_TAG);
        data.putBoolean(WITCH_BOSS_MELEE_WAS_NEAR_TAG, inRange);

        if (!data.getBoolean(WITCH_BOSS_MELEE_TAG)) {
            // 非近战状态：只在“刚靠近”那一刻判定一次 50%
            if (inRange && !wasNear && witch.getRandom().nextFloat() < WITCH_BOSS_MELEE_ENTER_CHANCE) {
                data.putBoolean(WITCH_BOSS_MELEE_TAG, true);
                witch.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
            }
            return;
        }

        // 近战状态
        if (!inRange || near == null) {
            // 玩家已远离：退出近战，收剑恢复远程
            data.putBoolean(WITCH_BOSS_MELEE_TAG, false);
            witch.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return;
        }
        // 近战挥剑（带攻击间隔）
        long t = level.getGameTime();
        if (t - data.getLong(WITCH_BOSS_MELEE_LAST_TAG) >= WITCH_BOSS_MELEE_COOLDOWN) {
            near.hurt(level.damageSources().mobAttack(witch), WITCH_BOSS_MELEE_DAMAGE);
            data.putLong(WITCH_BOSS_MELEE_LAST_TAG, t);
        }
    }

    /** 玩家当前是否已被变形（任一形态）。供阶段1“持续投变形药水直至玩家被变形”的判定：变形后不再投。 */
    public static boolean isPlayerTransmuted(Player player) {
        return PLAYER_TRANSMUTATION_INFO.containsKey(player.getUUID());
    }

    /** 该维度是否存在“已变形成方块”的玩家。 */
    private static boolean hasBlockTransmutedPlayer(ServerLevel level) {
        for (Player p : level.players()) {
            PlayerTransmutationInfo info = PLAYER_TRANSMUTATION_INFO.get(p.getUUID());
            if (info != null && info.form == TransmutationForm.BLOCK) return true;
        }
        return false;
    }

    /** 该维度是否存在“已变形成物品”的玩家。 */
    private static boolean hasItemTransmutedPlayer(ServerLevel level) {
        for (Player p : level.players()) {
            PlayerTransmutationInfo info = PLAYER_TRANSMUTATION_INFO.get(p.getUUID());
            if (info != null && info.form == TransmutationForm.ITEM) return true;
        }
        return false;
    }

    /** 阶段2：主动靠近目标并用对应工具攻击。
     *  优先锁定“已变形成方块”的玩家（按其方块种类掏对应工具）；若无变方块玩家，
     *  则锁定最近的存活玩家并主动靠近近战。目标超出攻击范围时用寻路持续追赶（不传送），
     *  追到范围内才挥击。 */
    private static void handleWitchBossToolAttack(Witch witch, ServerLevel level) {
        if (getWitchBossStage(witch) != WITCH_BOSS_STAGE_BLOCK) return;
        Player target = null;
        Player nearest = null;
        double bestD = Double.MAX_VALUE;
        for (Player p : level.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            PlayerTransmutationInfo info = PLAYER_TRANSMUTATION_INFO.get(p.getUUID());
            if (info != null && info.form == TransmutationForm.BLOCK) { target = p; break; }
            double d = witch.distanceTo(p);
            if (d < bestD) { bestD = d; nearest = p; }
        }
        // 无变方块玩家时，退而主动靠近最近玩家（不再原地不动等目标上门）
        if (target == null) target = nearest;
        if (target == null) return;
        String blockId = PLAYER_TRANSMUTATION_INFO.get(target.getUUID()) != null
            ? PLAYER_TRANSMUTATION_INFO.get(target.getUUID()).itemType() : null;
        witch.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
            new ItemStack(pickToolForBlock(blockId)));
        double dist = witch.distanceTo(target);
        if (dist > WITCH_BOSS_MELEE_RANGE) {
            // 超出攻击范围：用寻路持续追赶玩家（写入移动AI路径，而非传送）
            witch.getNavigation().moveTo(target, 1.0D);
            return;
        }
        // 已到范围内：按攻击间隔挥击
        long t = level.getGameTime();
        CompoundTag data = witch.getPersistentData();
        if (t - data.getLong(WITCH_BOSS_TOOL_LAST_TAG) >= WITCH_BOSS_TOOL_COOLDOWN) {
            target.hurt(level.damageSources().mobAttack(witch), WITCH_BOSS_TOOL_DAMAGE);
            data.putLong(WITCH_BOSS_TOOL_LAST_TAG, t);
        }
    }

    /** 根据方块 id 选择对应工具：泥土/沙类→锹，木头/原木→斧，石头/矿石→镐，其余→剑。 */
    private static net.minecraft.world.item.Item pickToolForBlock(String blockId) {
        if (blockId == null) return net.minecraft.world.item.Items.DIAMOND_SWORD;
        String s = blockId.toLowerCase();
        if (s.contains("log") || s.contains("plank") || s.contains("wood") || s.contains("stem")
            || s.contains("melon") || s.contains("pumpkin")) {
            // 木头/原木/茎类用斧；西瓜、南瓜（含雕刻南瓜/南瓜灯）也归斧，否则无法对其造成伤害
            return net.minecraft.world.item.Items.DIAMOND_AXE;
        }
        if (s.contains("dirt") || s.contains("sand") || s.contains("gravel") || s.contains("clay")
            || s.contains("grass") || s.contains("path") || s.contains("snow")) {
            return net.minecraft.world.item.Items.DIAMOND_SHOVEL;
        }
        if (s.contains("stone") || s.contains("cobble") || s.contains("ore") || s.contains("deepslate")
            || s.contains("granite") || s.contains("andesite") || s.contains("diorite")
            || s.contains("basalt") || s.contains("diorite") || s.contains("netherrack")
            || s.contains("nether_brick")) {
            return net.minecraft.world.item.Items.DIAMOND_PICKAXE;
        }
        return net.minecraft.world.item.Items.DIAMOND_SWORD;
    }

    /** 阶段3（非药水/非近战状态下）：手持打火石，每 5 秒记录一次玩家坐标，延迟 3~4 秒后在记录坐标点一次火。 */
    private static void handleWitchBossFlintAndSteel(Witch witch, ServerLevel level) {
        if (getWitchBossStage(witch) != WITCH_BOSS_STAGE_ITEM) return;
        if (isWitchBossInMelee(witch)) return; // 近战时不做打火石
        CompoundTag data = witch.getPersistentData();
        long t = level.getGameTime();
        boolean pending = data.getBoolean(WITCH_BOSS_FLINT_PENDING_TAG);
        if (!pending) {
            // 非打火石记录期：移除打火石期间附加的抗火（若仍残留）
            witch.removeEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
            // 每 5 秒记录一次玩家当前坐标
            if (t - data.getLong(WITCH_BOSS_FLINT_REC_TAG) >= WITCH_BOSS_FLINT_RECORD_INTERVAL) {
                Player p = null;
                double best = Double.MAX_VALUE;
                for (Player pl : level.players()) {
                    if (!pl.isAlive() || pl.isSpectator()) continue;
                    double d = witch.distanceTo(pl);
                    if (d < best) { best = d; p = pl; }
                }
                if (p != null) {
                    Vec3 pv = p.position();
                    data.putDouble(WITCH_BOSS_FLINT_X_TAG, pv.x);
                    data.putDouble(WITCH_BOSS_FLINT_Y_TAG, pv.y);
                    data.putDouble(WITCH_BOSS_FLINT_Z_TAG, pv.z);
                    data.putLong(WITCH_BOSS_FLINT_REC_TAG, t);
                    data.putLong(WITCH_BOSS_FLINT_USE_TAG,
                        t + WITCH_BOSS_FLINT_USE_MIN + (long) (witch.getRandom().nextDouble() * WITCH_BOSS_FLINT_USE_JITTER));
                    data.putBoolean(WITCH_BOSS_FLINT_PENDING_TAG, true);
                    witch.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(net.minecraft.world.item.Items.FLINT_AND_STEEL)); // 手持打火石
                }
            }
        } else {
            // 打火石等待期（手持打火石）：自带抗火且不显示药水粒子
            if (!witch.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)) {
                witch.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 200, 0, false, false, true));
            }
            // 时刻跟随“被变形成物品”的玩家：用寻路（写入移动AI路径），而非传送，避免呆板
            Player itemPlayer = null;
            for (Player pl : level.players()) {
                if (!pl.isAlive() || pl.isSpectator()) continue;
                PlayerTransmutationInfo info = PLAYER_TRANSMUTATION_INFO.get(pl.getUUID());
                if (info != null && info.form == TransmutationForm.ITEM) { itemPlayer = pl; break; }
            }
            if (itemPlayer != null) {
                witch.getNavigation().moveTo(itemPlayer, 1.0D);
            }
            if (t >= data.getLong(WITCH_BOSS_FLINT_USE_TAG)) {
                // 延迟到点：在记录的坐标使用一次打火石（真正点火）
                useFlintAndSteelAt(level, witch,
                    new Vec3(data.getDouble(WITCH_BOSS_FLINT_X_TAG),
                        data.getDouble(WITCH_BOSS_FLINT_Y_TAG), data.getDouble(WITCH_BOSS_FLINT_Z_TAG)));
                data.putBoolean(WITCH_BOSS_FLINT_PENDING_TAG, false);
                witch.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                witch.getNavigation().stop();
                witch.removeEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
            }
        }
    }

    /** 在指定坐标“真正使用一次打火石”：
     *  从记录坐标向下找到第一个“可放置火且有下方支撑”的空气格放火；找不到则在脚底格强行放火。
     *  成功后播放打火石音效与火焰粒子。 */
    private static void useFlintAndSteelAt(ServerLevel level, Witch witch, Vec3 pos) {
        BlockPos base = net.minecraft.core.BlockPos.containing(pos);
        BlockPos place = null;
        // 从记录坐标往下（最多 8 格）找第一个“空气且下方有实体方块支撑”的格子放火
        for (int off = 0; off <= 8; off++) {
            BlockPos cand = base.below(off);
            BlockState below = level.getBlockState(cand.below());
            if (level.getBlockState(cand).isAir()
                    && !below.getCollisionShape(level, cand.below()).isEmpty()
                    && !below.getFluidState().is(net.minecraft.world.level.material.Fluids.WATER)
                    && !below.getFluidState().is(net.minecraft.world.level.material.Fluids.LAVA)) {
                place = cand;
                break;
            }
        }
        // 兜底：就在记录坐标本身放火（即使悬空也尝试）
        if (place == null) {
            if (level.getBlockState(base).isAir()) {
                place = base;
            } else if (level.getBlockState(base.above()).isAir()) {
                place = base.above();
            }
        }
        if (place == null) return;
        level.setBlock(place, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);
        level.playSound(null, place.getX() + 0.5, place.getY() + 0.5, place.getZ() + 0.5,
            net.minecraft.sounds.SoundEvents.FLINTANDSTEEL_USE,
            net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
            place.getX() + 0.5, place.getY() + 0.5, place.getZ() + 0.5, 5, 0.2, 0.2, 0.2, 0.05);
    }

    /** 从候选池里为女巫Boss阶段挑选“必定转化对应类型”的变形药水目标编码：
     *  阶段1实体→生物(mob_shell:xxx)、阶段2方块→方块id、阶段3物品→物品id。 */
    private static String stageTransmutationItemType(int stage, RandomSource rand) {
        if (stage == WITCH_BOSS_STAGE_BLOCK) {
            return WITCH_BOSS_BLOCK_CANDIDATES[rand.nextInt(WITCH_BOSS_BLOCK_CANDIDATES.length)];
        }
        if (stage == WITCH_BOSS_STAGE_ITEM) {
            return WITCH_BOSS_ITEM_CANDIDATES[rand.nextInt(WITCH_BOSS_ITEM_CANDIDATES.length)];
        }
        // 阶段1实体阶段（默认）：生物壳编码
        return "mob_shell:"
            + WITCH_BOSS_MOB_CANDIDATES[rand.nextInt(WITCH_BOSS_MOB_CANDIDATES.length)];
    }

    /** 女巫Boss向目标投掷一批“按其阶段定目标类别的变形药水”（替换原版投掷的伤害/中毒药水）。
     *  每次投掷丢出的数量为 1~4 瓶（随机），多瓶时带轻微的角度/速度散射，避免完全重叠。
     *  仅用于朝敌方投掷；药水内容为变形药水(TRANSMUTATION)，ITEM_TYPE 编码该阶段对应的
     *  目标类别（生物壳/方块/物品），命中后目标即变形。 */
    public static void throwWitchBossTransmutationPotion(Witch witch, LivingEntity target) {
        if (witch.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) witch.level();
        int stage = getWitchBossStage(witch);
        String itemType = stageTransmutationItemType(stage, witch.getRandom());
        if (itemType == null) return;

        ItemStack basePotion = new ItemStack(net.minecraft.world.item.Items.SPLASH_POTION);
        basePotion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
            new net.minecraft.world.item.alchemy.PotionContents(
                cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSMUTATION));
        basePotion.set(ModDataComponents.ITEM_TYPE.get(), itemType);

        Vec3 from = witch.getEyePosition();
        double dx0 = target.getX() - witch.getX();
        double dz0 = target.getZ() - witch.getZ();
        double distXZ = Math.sqrt(dx0 * dx0 + dz0 * dz0);
        double dy0 = (target.getEyeY() - 1.1) - witch.getY() + distXZ * 0.2;

        // 每次投掷 1~4 瓶（原版女巫投掷速度0.75，女巫Boss为1.5倍）；多瓶带轻微散射
        RandomSource rand = witch.getRandom();
        int count = 1 + rand.nextInt(4); // 1~4
        for (int i = 0; i < count; i++) {
            double spread = (count > 1) ? ((rand.nextDouble() - 0.5) * 0.25) : 0.0;
            double ang = (rand.nextDouble() - 0.5) * (count > 1 ? 0.30 : 0.0); // 水平角度偏移(rad)
            double ca = Math.cos(ang), sa = Math.sin(ang);
            double dx = dx0 * ca + dz0 * sa;
            double dz = -dx0 * sa + dz0 * ca;
            // 纵向偏移：丢多瓶药水时，除水平散布外也做纵向随机（压低/抬高抛物线，更易命中）
            double dy = dy0 + (count > 1 ? (rand.nextDouble() - 0.5) * 0.6 : 0.0);
            double speed = 0.75F * 1.5F * (1.0 + (rand.nextDouble() - 0.5) * 0.2);
            // 自动调整丢药角度：在候选 vy 里选“落点最贴近目标”的仰角，避免近距离时药水越过头顶
            dy = solveBossThrowDy(witch, target, dx, dz, speed);

            net.minecraft.world.entity.projectile.ThrownPotion tp =
                new net.minecraft.world.entity.projectile.ThrownPotion(level, witch);
            tp.setItem(basePotion.copy());
            tp.setPos(from.x + (rand.nextDouble() - 0.5) * 0.2, from.y, from.z + (rand.nextDouble() - 0.5) * 0.2);
            tp.shoot(dx + spread * Math.signum(dx0), dy, dz + spread * Math.signum(dz0),
                (float) speed, 8.0F);
            level.addFreshEntity(tp);
        }
        // 丢出变形药水 → 消耗计数器 → 可能触发补给
        consumeWitchBossAmmo(witch, WITCH_BOSS_AMMO_TRANSMUTATION_TAG, count);
    }

    /** 用抛体离线求解：在候选 vy 中选一个使“药水落点（到达目标高度时）”最贴近目标水平的仰角。
     *  返回该 vy（方向仍是朝目标），用于投掷时压低/抬高抛物线，避免近距离越过头顶。 */
    private static double solveBossThrowDy(Witch witch, LivingEntity target, double dx, double dz, double speed) {
        double baseDy = (target.getEyeY() - 1.1) - witch.getY()
            + Math.sqrt(dx * dx + dz * dz) * 0.2;
        double targetGround = target.getY();
        double sx = witch.getX();
        double sy = witch.getEyeY() - 0.15;
        double sz = witch.getZ();
        double bestVy = baseDy;
        double bestErr = Double.MAX_VALUE;
        for (int k = 0; k < 15; k++) {
            double candVy = baseDy - 0.9 + k * (1.2 / 14.0); // 在基础仰角±0.6 附近采样
            double len = Math.sqrt(dx * dx + candVy * candVy + dz * dz);
            if (len < 1.0E-4) continue;
            double ivx = dx / len * speed;
            double ivy = candVy / len * speed;
            double ivz = dz / len * speed;
            double px = sx, py = sy, pz = sz;
            for (int t = 0; t < 400; t++) {
                px += ivx; py += ivy; pz += ivz;
                ivy -= 0.03;
                ivx *= 0.99; ivy *= 0.99; ivz *= 0.99;
                if (py <= targetGround) {
                    double ex = px - target.getX();
                    double ez = pz - target.getZ();
                    double err = ex * ex + ez * ez;
                    if (err < bestErr) { bestErr = err; bestVy = candVy; }
                    break;
                }
            }
        }
        return bestVy;
    }

    /** 玩家被变形成 TNT：女巫持打火石接近点燃它（#4）。
     *  在点燃范围内触发打火石音效，并把该玩家的下落 TNT 方块替换为 fuse=200 的点燃 TNT 实体
     *  （该 TNT 继续跟随玩家；爆炸时玩家被判死亡，创造/旁观模式免疫）。 */
    private static void handleWitchBossIgniteTnt(Witch witch, ServerLevel level) {
        Player tntPlayer = null;
        for (Player p : level.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            PlayerTransmutationInfo info = PLAYER_TRANSMUTATION_INFO.get(p.getUUID());
            if (info != null && info.form == TransmutationForm.BLOCK
                    && "minecraft:tnt".equals(info.itemType())) {
                tntPlayer = p;
                break;
            }
        }
        if (tntPlayer == null) return;
        CompoundTag data = witch.getPersistentData();
        double dist = witch.distanceTo(tntPlayer);
        if (dist > WITCH_BOSS_TNT_IGNITE_RANGE) {
            if (data.getBoolean("jafa_witch_boss_tnt_ignited")) {
                data.putBoolean("jafa_witch_boss_tnt_ignited", false);
            }
            return;
        }
        if (data.getBoolean("jafa_witch_boss_tnt_ignited")) return; // 已点燃过，等待爆炸/复原
        data.putBoolean("jafa_witch_boss_tnt_ignited", true);

        // 找到该玩家的下落 TNT 方块跟随实体并替换为点燃 TNT
        net.minecraft.world.entity.item.FallingBlockEntity follow = null;
        for (net.minecraft.world.entity.Entity e : level.getEntities(tntPlayer,
                new net.minecraft.world.phys.AABB(tntPlayer.getX() - 3, tntPlayer.getY() - 3, tntPlayer.getZ() - 3,
                    tntPlayer.getX() + 3, tntPlayer.getY() + 3, tntPlayer.getZ() + 3),
                ent -> ent instanceof net.minecraft.world.entity.item.FallingBlockEntity f
                    && f.getBlockState().is(net.minecraft.world.level.block.Blocks.TNT))) {
            follow = (net.minecraft.world.entity.item.FallingBlockEntity) e;
            break;
        }
        net.minecraft.world.entity.item.PrimedTnt tnt =
            new net.minecraft.world.entity.item.PrimedTnt(level, follow != null ? follow.getX() : tntPlayer.getX(),
                follow != null ? follow.getY() : tntPlayer.getY(),
                follow != null ? follow.getZ() : tntPlayer.getZ(), null);
        tnt.setFuse(WITCH_BOSS_TNT_FUSE);
        tnt.getPersistentData().putString(WITCH_BOSS_TNT_PLAYER_TAG, tntPlayer.getUUID().toString());
        level.addFreshEntity(tnt);
        if (follow != null) {
            // 先移除该下落 TNT 方块实体的变形跟踪，再 discard——
            // 否则 discard 触发 onEntityLeaveLevel 的“未能落地→按破坏结算”，会把玩家瞬间判死，
            // 而 TNT 本应由 PrimedTnt(200刻) 接管并在 10 秒后爆炸时再带走玩家。
            FALLING_TRANSMUTATIONS.remove(follow.getUUID());
            follow.discard(); // 移除原下落 TNT 方块跟随实体
        } else {
            // 找不到跟随实体：直接清掉该玩家方块变形态以便被 TNT 取代（保守方案：不动）
        }
        level.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(),
            net.minecraft.sounds.SoundEvents.FLINTANDSTEEL_USE,
            net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** 被点燃 TNT 的每刻处理：持续跟随其目标玩家，并在 fuse 即将耗尽（爆炸）时使目标玩家死亡（创造/旁观免疫）。 */
    private static void handleIgnitedTntFollow(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.item.PrimedTnt tnt) {
        String uuidStr = tnt.getPersistentData().getString(WITCH_BOSS_TNT_PLAYER_TAG);
        if (uuidStr.isEmpty()) return;
        java.util.UUID pu;
        try { pu = java.util.UUID.fromString(uuidStr); } catch (Exception ex) { return; }
        Player player = level.getServer().getPlayerList().getPlayer(pu);
        if (player != null && player.isAlive() && !player.isSpectator()) {
            // 跟随玩家
            Vec3 cur = tnt.position();
            Vec3 to = player.position().add(0, 0.5, 0).subtract(cur);
            if (to.length() > 0.5) {
                Vec3 step = to.normalize().scale(0.4);
                tnt.setPos(cur.add(step));
            }
            // 爆炸瞬间（fuse=1 的最后 tick）判死目标玩家（创造/旁观免疫）
            if (tnt.getFuse() <= 1) {
                if (!player.isCreative()) {
                    player.hurt(level.damageSources().explosion(tnt, tnt),
                        Float.MAX_VALUE);
                }
                tnt.getPersistentData().remove(WITCH_BOSS_TNT_PLAYER_TAG);
            }
        }
    }

    /** 记录女巫Boss“家”坐标（小屋中心），供补给回城。 */
    private static void initWitchBossHome(Witch witch, Vec3 home) {
        CompoundTag data = witch.getPersistentData();
        data.putDouble(WITCH_BOSS_HOME_X_TAG, home.x);
        data.putDouble(WITCH_BOSS_HOME_Y_TAG, home.y);
        data.putDouble(WITCH_BOSS_HOME_Z_TAG, home.z);
    }

    /** 读取女巫Boss“家”坐标；未记录则返回 null。 */
    private static Vec3 getWitchBossHome(Witch witch) {
        CompoundTag data = witch.getPersistentData();
        if (!data.contains(WITCH_BOSS_HOME_X_TAG)) return null;
        return new Vec3(data.getDouble(WITCH_BOSS_HOME_X_TAG),
            data.getDouble(WITCH_BOSS_HOME_Y_TAG), data.getDouble(WITCH_BOSS_HOME_Z_TAG));
    }

    /** 初始化女巫Boss随身药水计数器（默认：变形36/解药12/传送9/隐身6）；仅缺失时写入。 */
    private static void initWitchBossAmmo(Witch witch) {
        CompoundTag data = witch.getPersistentData();
        if (!data.contains(WITCH_BOSS_AMMO_TRANSMUTATION_TAG)) data.putInt(WITCH_BOSS_AMMO_TRANSMUTATION_TAG, WITCH_BOSS_AMMO_TRANSMUTATION_MAX);
        if (!data.contains(WITCH_BOSS_AMMO_ANTIDOTE_TAG)) data.putInt(WITCH_BOSS_AMMO_ANTIDOTE_TAG, WITCH_BOSS_AMMO_ANTIDOTE_MAX);
        if (!data.contains(WITCH_BOSS_AMMO_TRANSPORT_TAG)) data.putInt(WITCH_BOSS_AMMO_TRANSPORT_TAG, WITCH_BOSS_AMMO_TRANSPORT_MAX);
        if (!data.contains(WITCH_BOSS_AMMO_INVISIBILITY_TAG)) data.putInt(WITCH_BOSS_AMMO_INVISIBILITY_TAG, WITCH_BOSS_AMMO_INVISIBILITY_MAX);
    }

    /** 把某一类药水计数器恢复至初始满值。 */
    private static void restoreWitchBossAmmo(Witch witch) {
        CompoundTag data = witch.getPersistentData();
        data.putInt(WITCH_BOSS_AMMO_TRANSMUTATION_TAG, WITCH_BOSS_AMMO_TRANSMUTATION_MAX);
        data.putInt(WITCH_BOSS_AMMO_ANTIDOTE_TAG, WITCH_BOSS_AMMO_ANTIDOTE_MAX);
        data.putInt(WITCH_BOSS_AMMO_TRANSPORT_TAG, WITCH_BOSS_AMMO_TRANSPORT_MAX);
        data.putInt(WITCH_BOSS_AMMO_INVISIBILITY_TAG, WITCH_BOSS_AMMO_INVISIBILITY_MAX);
    }

    /** 消耗某一类药水 count 瓶；消耗后若任一类余量 < {@link #WITCH_BOSS_AMMO_LOW} 则触发回屋补给。 */
    private static void consumeWitchBossAmmo(Witch witch, String tag, int count) {
        if (witch.level().isClientSide()) return;
        CompoundTag data = witch.getPersistentData();
        if (!data.contains(tag)) initWitchBossAmmo(witch);
        int left = Math.max(0, data.getInt(tag) - Math.max(0, count));
        data.putInt(tag, left);
        // 任一种药水不足 → 一定丢“传送回小屋中心”的定点传送药水回家补给，并恢复满额（可无限次）
        if (anyAmmoLow(witch)) {
            resupplyWitchBoss(witch);
        }
    }

    /** 是否任一种随身药水余量 < 阈值。 */
    private static boolean anyAmmoLow(Witch witch) {
        CompoundTag data = witch.getPersistentData();
        for (String tag : new String[]{
            WITCH_BOSS_AMMO_TRANSMUTATION_TAG, WITCH_BOSS_AMMO_ANTIDOTE_TAG,
            WITCH_BOSS_AMMO_TRANSPORT_TAG, WITCH_BOSS_AMMO_INVISIBILITY_TAG}) {
            if (data.getInt(tag) < WITCH_BOSS_AMMO_LOW) return true;
        }
        return false;
    }

    /** 药水补给：往自己脚下丢一瓶“定点传送回小屋中心”的传送药水，回家并恢复满额药水（可无限次）。 */
    private static void resupplyWitchBoss(Witch witch) {
        if (witch.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) witch.level();
        Vec3 home = getWitchBossHome(witch);
        if (home == null) { restoreWitchBossAmmo(witch); return; }
        // 造一瓶“定点传送回小屋中心”的传送药水，朝脚下丢出 → 传送回家
        ItemStack potion = new ItemStack(net.minecraft.world.item.Items.SPLASH_POTION);
        potion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
            new net.minecraft.world.item.alchemy.PotionContents(
                cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSPORTATION));
        potion.set(ModDataComponents.TRANSPORT_MODE.get(), "point");
        potion.set(ModDataComponents.TARGET_POS.get(), home);

        net.minecraft.world.entity.projectile.ThrownPotion tp =
            new net.minecraft.world.entity.projectile.ThrownPotion(level, witch);
        tp.setItem(potion);
        tp.setPos(witch.getX(), witch.getEyeY() - 0.1, witch.getZ());
        tp.shoot((witch.getRandom().nextDouble() - 0.5) * 0.3, -1.0,
            (witch.getRandom().nextDouble() - 0.5) * 0.3, 0.5F, 0.0F);
        level.addFreshEntity(tp);
        // 补给完成：药水恢复初始值
        restoreWitchBossAmmo(witch);
    }

    /** 女巫Boss喝下隐身药水 → 消耗隐身药水计数（直饮型）。 */
    public static void onWitchBossDrinkInvisibility(Witch witch) {
        consumeWitchBossAmmo(witch, WITCH_BOSS_AMMO_INVISIBILITY_TAG, 1);
    }

    /** 视觉辅助：实体往自己脚下丢一瓶“变形解药”（喷溅瓶），用于女巫Boss自我变形后解除自己。 */
    private static void spawnAntidoteBottleAtFeet(ServerLevel level, LivingEntity owner) {
        ItemStack potion = new ItemStack(net.minecraft.world.item.Items.SPLASH_POTION);
        potion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
            new net.minecraft.world.item.alchemy.PotionContents(
                cn.autoforged.joes_addons_for_abmc.potion.ModPotions.TRANSMUTATION_ANTIDOTE));
        net.minecraft.world.entity.projectile.ThrownPotion tp =
            new net.minecraft.world.entity.projectile.ThrownPotion(level, owner);
        tp.setItem(potion);
        tp.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        tp.shoot((owner.getRandom().nextDouble() - 0.5) * 0.3, -1.0,
            (owner.getRandom().nextDouble() - 0.5) * 0.3, 0.4F, 0.0F);
        level.addFreshEntity(tp);
    }

    /** 女巫Boss基础数值：血量 = 普通女巫的 {@link #WITCH_BOSS_HEALTH_MULTIPLIER} 倍，并回满血。
     *  普通女巫基础 26，故女巫Boss为 260。重复调用会按当前基础值再翻倍，故仅在成为Boss那一刻调用一次。 */
    private static void initWitchBossHealth(Witch witch) {
        if (witch.level().isClientSide()) return;
        var attr = witch.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(attr.getBaseValue() * WITCH_BOSS_HEALTH_MULTIPLIER);
        }
        witch.setHealth(witch.getMaxHealth());
    }

    /** 读取女巫Boss当前阶段（1实体/2方块/3物品），缺失或越界一律回退为实体阶段(1)。 */
    private static int getWitchBossStage(Witch witch) {
        int stage = witch.getPersistentData().getInt(WITCH_BOSS_STAGE_TAG);
        if (stage < WITCH_BOSS_STAGE_ENTITY || stage > WITCH_BOSS_STAGE_ITEM) return WITCH_BOSS_STAGE_ENTITY;
        return stage;
    }

    /** 根据女巫Boss阶段返回原版Boss条颜色：阶段1实体=蓝、阶段2方块=黄、阶段3物品=紫。 */
    private static net.minecraft.world.BossEvent.BossBarColor witchBossBarColor(int stage) {
        return switch (stage) {
            case WITCH_BOSS_STAGE_BLOCK -> net.minecraft.world.BossEvent.BossBarColor.YELLOW;
            case WITCH_BOSS_STAGE_ITEM -> net.minecraft.world.BossEvent.BossBarColor.PURPLE;
            default -> net.minecraft.world.BossEvent.BossBarColor.BLUE; // 实体阶段：浅蓝系
        };
    }

    /** 每服务端刻更新女巫Boss血条（原版 ServerBossEvent，由原版 HUD 自动渲染）：
     *  遍历 {@link #WITCH_BOSS_TRACKED} 中登记的女巫Boss（而非全图扫描），各自维护一个独立Boss条
     *  （颜色随阶段、显示给所有在线玩家）；已死亡/被击败的Boss移除对应Boss条。
     *  客户端最多同时渲染 4 条并自动与其它Boss条统一计数。 */
    private static void updateWitchBossBar(net.minecraft.server.MinecraftServer server) {
        java.util.Set<java.util.UUID> alive = new java.util.HashSet<>();
        for (UUID u : WITCH_BOSS_TRACKED) {
            Witch w = null;
            for (ServerLevel lv : server.getAllLevels()) {
                if (lv.getEntity(u) instanceof Witch v && v.isAlive()
                        && v.getPersistentData().getBoolean(WITCH_BOSS_TAG)) {
                    w = v;
                    break;
                }
            }
            if (w == null) continue; // 实体已死亡/被移除/未加载：不本刻显示，交由清理逻辑移除
            alive.add(u);
            int stage = getWitchBossStage(w);
            ServerBossEvent ev = WITCH_BOSS_EVENTS.computeIfAbsent(u,
                k -> new ServerBossEvent(
                    net.minecraft.network.chat.Component.literal("女巫Boss"),
                    witchBossBarColor(stage),
                    net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS));
            ev.setName(net.minecraft.network.chat.Component.literal("女巫Boss(阶段" + stage + ")"));
            ev.setColor(witchBossBarColor(stage));
            ev.setProgress(1.0F); // 暂不加入血量扣减
            ev.setVisible(true);
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                ev.addPlayer(sp);
            }
        }
        // 清理：仍在跟踪但实体已消失（死亡/搬运/被击败）的，移除跟踪与Boss条
        WITCH_BOSS_TRACKED.removeIf(u -> {
            if (alive.contains(u)) return false;
            ServerBossEvent ev = WITCH_BOSS_EVENTS.remove(u);
            if (ev != null) {
                ev.setVisible(false);
                ev.removeAllPlayers();
            }
            return true;
        });
        // 兜底：清理未登记但残留的事件（理论不会发生，防御性）
        WITCH_BOSS_EVENTS.entrySet().removeIf(e -> !alive.contains(e.getKey()));
    }

    /** 切换最近女巫Boss的阶段（模拟命令用）：
     *  阶段 1~3 → 设置阶段标签（血条颜色由每刻 updateWitchBossBar 自动跟随）；
     *  阶段 4   → 视为被击败：在原地爆出大量女巫粒子+末影(传送门)粒子，随后移除该Boss。 */
    private static void setWitchBossStage(ServerLevel level, Witch boss, int stage) {
        if (stage == WITCH_BOSS_STAGE_DEFEATED) {
            // 移除该Boss的Boss条（下一刻 updateWitchBossBar 也会自动清理已不在场的条目）
            ServerBossEvent ev = WITCH_BOSS_EVENTS.remove(boss.getUUID());
            if (ev != null) {
                ev.setVisible(false);
                ev.removeAllPlayers();
            }
            spawnWitchBossDefeatParticles(level, boss);
            boss.discard();
            return;
        }
        boss.getPersistentData().putInt(WITCH_BOSS_STAGE_TAG,
            Mth.clamp(stage, WITCH_BOSS_STAGE_ENTITY, WITCH_BOSS_STAGE_ITEM));
    }

    /** 女巫Boss被击败：原地爆发大量女巫粒子与末影(传送门)粒子。 */
    private static void spawnWitchBossDefeatParticles(ServerLevel level, Witch boss) {
        double x = boss.getX(), y = boss.getY() + boss.getBbHeight() * 0.5, z = boss.getZ();
        RandomSource rand = boss.getRandom();
        for (int i = 0; i < 60; i++) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
                x + (rand.nextDouble() - 0.5) * 1.5, y + (rand.nextDouble() - 0.5) * 2.0, z + (rand.nextDouble() - 0.5) * 1.5,
                1, 0, 0, 0, 0.05);
        }
        for (int i = 0; i < 80; i++) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                x + (rand.nextDouble() - 0.5) * 1.5, y + (rand.nextDouble() - 0.5) * 2.0, z + (rand.nextDouble() - 0.5) * 1.5,
                1, 0, 0, 0, 0.6);
        }
    }

    /** 女巫Boss丢药水弹道自伤预测 + 规避（在 RangedAttackGoal 即将投掷前由 mixin 调用）。
     *  用与受击女巫投掷相同的参数模拟喷溅药水抛体轨迹：若预测药水会砸中自己（落点/途经点
     *  过近，溅射到自身），则先把女巫沿弹道水平反方向瞬移开 3 格构成安全落差，再放行投掷。 */
    private static final double POTION_SELF_HIT_RANGE = 3.0D; // 喷溅药水有效溅射半径（格）
    private static final double POTION_THROW_SPEED = 0.75D * 1.5D; // 女巫Boss丢药速度 = 普通女巫(0.75)的1.5倍
    private static final double POTION_MOVE_BACK = 3.0D; // 自伤时沿弹道反方向退开的距离（格）

    public static void prepareWitchBossThrow(Witch witch) {
        if (!witch.getPersistentData().getBoolean(WITCH_BOSS_TAG)) return;
        LivingEntity target = witch.getTarget();
        if (target == null || !target.isAlive()) return;

        // 复刻 Witch.performRangedAttack 的投掷方向（含目标移速补偿与 0.2×距离 的抬头）
        Vec3 tv = target.getDeltaMovement();
        double dx = (target.getX() + tv.x) - witch.getX();
        double dz = (target.getZ() + tv.z) - witch.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        double dy = (target.getEyeY() - 1.1) - witch.getY() + distXZ * 0.2;

        if (!potionWouldSelfHit(witch, dx, dy, dz, POTION_THROW_SPEED)) return;

        // 弹道水平方向的反方向退开，拉大落点，保证不会溅射到自己
        if (distXZ < 1.0E-4) return;
        double mx = -dx / distXZ * POTION_MOVE_BACK;
        double mz = -dz / distXZ * POTION_MOVE_BACK;
        witch.setPos(witch.getX() + mx, witch.getY(), witch.getZ() + mz);
    }

    /** 近似模拟喷溅药水抛体（初速方向 = 归一化(dir)×speed，每刻重力 0.03、阻力 0.99），
     *  返回「整段轨迹任一点或落点」是否距女巫自己过近（< 溅射半径）而会打到自己。 */
    private static boolean potionWouldSelfHit(Witch witch, double dx, double dy, double dz, double speed) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4) return false;
        double vx = dx / len * speed;
        double vy = dy / len * speed;
        double vz = dz / len * speed;

        double px = witch.getX();
        double py = witch.getEyeY() - 0.15;
        double pz = witch.getZ();
        double groundY = witch.getY();
        net.minecraft.world.phys.AABB selfBox = witch.getBoundingBox().inflate(0.5);

        for (int i = 0; i < 400; i++) {
            px += vx;
            py += vy;
            pz += vz;
            vy -= 0.03;
            vx *= 0.99;
            vy *= 0.99;
            vz *= 0.99;

            // 途经点直接命中自己的碰撞箱（+自伤缓冲区）
            if (selfBox.contains(px, py, pz)) return true;
            // 落点判定：碰到地面（y 低于脚底且在下落）
            if (py <= groundY && vy < 0) {
                double hx = px - witch.getX();
                double hz = pz - witch.getZ();
                return Math.sqrt(hx * hx + hz * hz) < POTION_SELF_HIT_RANGE;
            }
        }
        return false;
    }

    private static void onTradeWithVillager(TradeWithVillagerEvent event) {
        if (event.getAbstractVillager() instanceof Villager villager
            && event.getMerchantOffer().getResult().is(Items.COMMAND_BLOCK)) {
            villager.getPersistentData().putBoolean(COMMAND_BLOCK_SOLD_TAG, true);
            event.getMerchantOffer().setToOutOfStock();
        }
    }

    private static void handleLapisStaffFlight(Player player) {
        boolean holdingLapis = false;
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        if (mainHand.getItem() instanceof StaffItem) {
            String bt = mainHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
            if ("lapis_block".equals(bt)) holdingLapis = true;
        }
        if (!holdingLapis && offHand.getItem() instanceof StaffItem) {
            String bt = offHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
            if ("lapis_block".equals(bt)) holdingLapis = true;
        }

        if (player.isCreative() || player.isSpectator()) return;

        if (holdingLapis && player.experienceLevel >= 1) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
            LAPIS_FLIGHT_PLAYERS.add(player.getUUID());
            if (player.getAbilities().flying && player.tickCount % 20 == 0) {
                player.giveExperiencePoints(-1);
            }
        } else if (LAPIS_FLIGHT_PLAYERS.contains(player.getUUID())) {
            boolean wasFlying = player.getAbilities().flying;
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            LAPIS_FLIGHT_PLAYERS.remove(player.getUUID());
            if (wasFlying) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, true));
            }
        }

        if (holdingLapis && player.experienceLevel < 1 && player.tickCount % 20 == 0) {
            int height = getHeightAboveGround(player);
            int durationSeconds = Math.max(1, height);
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, durationSeconds * 20, 0, false, true));
        }
    }

    private static void handleCommandStaffFlight(Player player) {
        if (player.isCreative() || player.isSpectator()) return;

        boolean holdingCommand = false;
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        if (mainHand.getItem() instanceof StaffItem) {
            String bt = mainHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
            if ("command_block".equals(bt)) holdingCommand = true;
        }
        if (!holdingCommand && offHand.getItem() instanceof StaffItem) {
            String bt = offHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
            if ("command_block".equals(bt)) holdingCommand = true;
        }

        if (holdingCommand) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
            COMMAND_FLIGHT_PLAYERS.add(player.getUUID());
            if (player.getAbilities().flying && player.tickCount % 5 == 0 && ModConfig.COMMAND_FLY_SOUND.get()) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.STAFF_COMMAND_FLY.get(), SoundSource.PLAYERS, 0.5F, 1.0F);
            }
        } else if (COMMAND_FLIGHT_PLAYERS.contains(player.getUUID())) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            COMMAND_FLIGHT_PLAYERS.remove(player.getUUID());
        }
    }

    private static void handleHerobrineStaffFlight(Player player) {
        if (player.isCreative() || player.isSpectator()) return;

        boolean holdingHerobrine = false;
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        if (mainHand.getItem() instanceof StaffItem) {
            String bt = mainHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
            if ("herobrine_head".equals(bt)) holdingHerobrine = true;
        }
        if (!holdingHerobrine && offHand.getItem() instanceof StaffItem) {
            String bt = offHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
            if ("herobrine_head".equals(bt)) holdingHerobrine = true;
        }

        if (holdingHerobrine) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
            HEROBRINE_FLIGHT_PLAYERS.add(player.getUUID());
        } else if (HEROBRINE_FLIGHT_PLAYERS.contains(player.getUUID())) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            HEROBRINE_FLIGHT_PLAYERS.remove(player.getUUID());
        }
    }

    private static int getHeightAboveGround(Player player) {
        Level level = player.level();
        int y = player.blockPosition().getY();
        for (int checkY = y; checkY > level.getMinBuildHeight(); checkY--) {
            BlockPos pos = new BlockPos(player.blockPosition().getX(), checkY, player.blockPosition().getZ());
            if (!level.getBlockState(pos).isAir()) {
                return Math.max(1, y - checkY - 1);
            }
        }
        return 1;
    }

    private static boolean isPhysicsDimension(Level level) {
        return level.dimension().location().equals(PHYSICS_DIM_ID);
    }

    private static void handlePhysicsDimensionTick(Entity entity) {
        if (entity instanceof Player player) {
            if (player.isCreative() && player.getAbilities().flying) return;
            if (player.isFallFlying()) return;
        }

        PHYSICS_PRE_TICK_POSITIONS.put(entity.getId(), entity.position());
        PHYSICS_DIM_ENTITIES.add(entity.getUUID());

        Vec3 intendedVel = PHYSICS_DELTA_MOVEMENTS.getOrDefault(entity.getId(), Vec3.ZERO);
        entity.setDeltaMovement(intendedVel);

        if (entity instanceof LivingEntity living) {
            living.xxa = 0;
            living.zza = 0;
            living.yya = 0;
        }
        entity.setNoGravity(true);
    }

    private static void cleanupPhysicsDimensionState(Entity entity) {
        PHYSICS_DELTA_MOVEMENTS.remove(entity.getId());
        PHYSICS_PRE_TICK_POSITIONS.remove(entity.getId());
        PHYSICS_DIM_ENTITIES.remove(entity.getUUID());
        entity.setNoGravity(false);
        entity.setDeltaMovement(Vec3.ZERO);
    }

    private static void onEntityTickPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        // 女巫 Boss：每刻检测是否首次察觉到玩家（创造/旁观不计入），命中则打标记（仅一次）。
        // 同时加速喝药：原版 uses usingTime 每刻 -1（药水使用时长，如 32 刻喝光），这里额外 -2
        // 叠加为 -3/刻 → 喝药时间缩短至 1/3（丢药间隔由 RangedAttackGoalMixin 单独提速）。
        if (entity instanceof Witch witch) {
            if (witch.getPersistentData().getBoolean(WITCH_BOSS_TAG)) {
                if (witch.isDrinkingPotion() && witch.usingTime > 2) {
                    witch.usingTime -= 2;
                }
                // 阶段1/2：玩家靠太近时向脚下丢点传送药水逃逸（近战状态不逃逸）
                if (!isWitchBossInMelee(witch)) {
                    handleWitchBossRetreatTeleport(witch, (net.minecraft.server.level.ServerLevel) entity.level());
                }
                // 阶段3：玩家靠太近时 50% 进入近战攻击状态（持钻石剑）
                handleWitchBossMelee(witch, (net.minecraft.server.level.ServerLevel) entity.level());
                // 阶段2：锁定已变方块玩家，按方块类型掏工具攻击
                handleWitchBossToolAttack(witch, (net.minecraft.server.level.ServerLevel) entity.level());
                // 阶段3（非近战）：手持打火石每5秒记录并延迟3~4秒在玩家坐标打火
                handleWitchBossFlintAndSteel(witch, (net.minecraft.server.level.ServerLevel) entity.level());
                // 玩家被变形成 TNT：女巫持打火石接近点燃它
                handleWitchBossIgniteTnt(witch, (net.minecraft.server.level.ServerLevel) entity.level());
            }
            handleWitchBossPlayerNotice(witch);
        }
        // 被女巫点燃的 TNT（玩家变 TNT）：每刻跟随目标玩家，爆炸瞬间使其目标玩家死亡（创造/旁观免疫）
        if (entity instanceof net.minecraft.world.entity.item.PrimedTnt ignited
                && entity.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            handleIgnitedTntFollow(sl, ignited);
            // 生物变 TNT：fuse 耗尽（爆炸瞬间）才判死原生物，而不是点燃即死
            TransmutationData td = TNT_TRANSMUTATIONS.get(ignited.getUUID());
            if (td != null && ignited.getFuse() <= 1) {
                TNT_TRANSMUTATIONS.remove(ignited.getUUID());
                handleTransmutationKillCredit(sl, td, ignited.blockPosition(), td.killerPlayerUuid());
            }
        }
        // 女巫Boss自我变形成生物：该生物往脚下丢变形解药，把自己还原为 witchboss 本体（只触发一次）
        if (entity.getPersistentData().getBoolean(WITCH_BOSS_SELF_TRANS_TAG)) {
            entity.getPersistentData().putBoolean(WITCH_BOSS_SELF_TRANS_TAG, false);
            if (entity instanceof LivingEntity selfTransLiving) {
                spawnAntidoteBottleAtFeet((net.minecraft.server.level.ServerLevel) entity.level(), selfTransLiving);
            }
        }

        // 冰块权杖：生物碰撞箱与霜冰重叠时禁止移动与跳跃（在任何维度都生效）
        if (entity instanceof LivingEntity living) {
            updateFrostedConstraints(living);
            // 蜘蛛网权杖：持有者在蜘蛛网中不会被减速（清除原版 CobwebBlock 设置的 stuck 倍率）
            if (isHoldingCobwebStaff(living)) {
                clearStuckInCobweb(living);
            }
        }

        if (!isPhysicsDimension(entity.level())) return;

        int id = entity.getId();
        Vec3 prePos = PHYSICS_PRE_TICK_POSITIONS.remove(id);
        Vec3 intendedVel = PHYSICS_DELTA_MOVEMENTS.get(id);
        if (prePos != null && intendedVel != null) {
            Vec3 expectedPos = prePos.add(intendedVel);
            entity.setPos(expectedPos);
            entity.setDeltaMovement(intendedVel);
        }
    }

    private static void onLivingBreathe(LivingBreatheEvent event) {
        if (!isPhysicsDimension(event.getEntity().level())) return;
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player player) {
            if ((player.isCreative() && player.getAbilities().flying) || player.isFallFlying()) {
                return;
            }
        }
        event.setCanBreathe(false);
        event.setRefillAirAmount(0);
    }

    // 判断玩家背包中是否拥有可授予创造模式的特殊物品（Omega 相关物品不再授予创造模式）
    private static boolean hasCreativeGrantingItem(Player player) {
        return player.getInventory().hasAnyMatching(stack -> {
            net.minecraft.world.item.Item item = stack.getItem();
            // minecraft game icon 仍授予创造模式；omega game icon 不再授予
            if (item == ModItems.GAME_ICON.get()) return true;
            if (item == ModItems.OMEGA_GAME_ICON.get()) return false;
            // Minecraft 权杖：staff 物品且 BLOCKTYPE 为 minecraft_game_icon（omega 权杖不再授予）
            if (item != ModItems.STAFF.get()) return false;
            String bt = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
            return "minecraft_game_icon".equals(bt);
        });
    }

    // 判断玩家背包中是否拥有 omega game icon 或 omega 权杖（授予飞行/夜视/免伤特性）
    private static boolean hasOmegaPowerItem(Player player) {
        return player.getInventory().hasAnyMatching(stack -> {
            net.minecraft.world.item.Item item = stack.getItem();
            if (item == ModItems.OMEGA_GAME_ICON.get()) return true;
            if (item != ModItems.STAFF.get()) return false;
            String bt = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
            return "omega".equals(bt);
        });
    }

    /** 服务器启动：委托脚本网络中枢加载全局函数库并按存档初始化程序库。 */
    private static void onServerStart(ServerStartingEvent event) {
        ScriptNetworking.initProgramStore(event.getServer().getWorldPath(LevelResource.ROOT));
    }

    /** 服务器停止：委托脚本网络中枢保存程序库。 */
    private static void onServerStop(ServerStoppingEvent event) {
        ScriptNetworking.onServerStop();
    }

    private static void onServerTickPre(ServerTickEvent.Pre event) {
        LuckyPortalBlock.clearProcessedThisTick();

        // 女巫Boss血条（原版 ServerBossEvent 机制）每刻更新
        updateWitchBossBar(event.getServer());
        // 玩家“渲染替换”变身倒计时 & 复原
        tickMorphRemaining(event.getServer());

        // 沉船溺尸刷新：每 10 分钟在主世界已加载的沉船结构周围刷 10~20 只溺尸
        handleShipwreckDrownedTick(event.getServer());

        // Creeper Clan 维度：贴近地面的空气方块随机触发 TNT 爆炸粒子（无破坏/无音效）
        handleCreeperClanExplosionParticles(event.getServer());

        // 蜘蛛网权杖：拉扯推进 + 无效化到期清理
        handleCobwebPullTick(event.getServer());
        handleCobwebNullifyExpiry(event.getServer());

        // 铁链权杖（铁块权杖）：钩取拉扯推进（物品收拢 / 生物钓竿式拉取与惯性甩出）
        handleChainGrabTick(event.getServer());

        // 驱动脚本调度器：推进所有运行中的图形化程序
        cn.autoforged.joes_addons_for_abmc.script.ScriptScheduler.getInstance().tick(event.getServer());

        // 驱动命令方块权杖的「指令 Text Display」延时任务（出现/销毁）
        runCommandTextTasks(event.getServer());

        // 命令方块权杖：抓取模式每刻拉拽目标生物
        runCommandStaffGrabTick(event.getServer());

        // 命令方块权杖：护盾模式每刻反弹弹射物 + 维持头顶指令文本
        runCommandStaffShieldTick(event.getServer());

        // 拥有特殊物品时强制设为创造模式；物品移除后恢复其原本的游玩模式。
        // 例外：不把旁观模式玩家设为创造模式。
        for (net.minecraft.server.level.ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            UUID pUuid = p.getUUID();
            GameType current = p.gameMode.getGameModeForPlayer();
            if (hasCreativeGrantingItem(p)) {
                if (current != GameType.CREATIVE && current != GameType.SPECTATOR) {
                    // 仅在该玩家首次被动切到创造时记录原始模式，避免重复覆盖
                    CREATIVE_GRANT_ORIGINAL.putIfAbsent(pUuid, current);
                    p.setGameMode(GameType.CREATIVE);
                }
            } else {
                GameType original = CREATIVE_GRANT_ORIGINAL.remove(pUuid);
                if (original != null && current != original) {
                    p.setGameMode(original);
                }
            }
        }

        // Omega 游戏图标 / Omega 权杖：授予飞行、夜视（免疫伤害在 onLivingDamagePre 处理）。
        // 其他权杖的飞行逻辑在实体 tick 中自行管理 mayfly，因此这里失去 Omega 时直接收回即可。
        for (net.minecraft.server.level.ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            UUID pUuid = p.getUUID();
            boolean hasOmega = hasOmegaPowerItem(p);
            if (hasOmega) {
                // 在创造/旁观模式下同样授予（这两种模式本来就自带飞行），因而不必排除它们。
                OMEGA_POWER_PLAYERS.add(pUuid);
                if (!p.getAbilities().mayfly) {
                    p.getAbilities().mayfly = true;
                    p.onUpdateAbilities();
                }
                // 夜视：每秒（20 刻）刷新为 15 秒（300 刻）持续，效果始终新鲜，
                // 避免周期性闪烁/渐隐；缺失（如死亡复活）时立即补上。
                MobEffectInstance nightVision = p.getEffect(MobEffects.NIGHT_VISION);
                if (nightVision == null || p.level().getGameTime() % 20 == 0) {
                    p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false));
                }
            } else {
                if (OMEGA_POWER_PLAYERS.remove(pUuid)) {
                    // 仅当玩家处于生存/冒险等非创造、非旁观模式时才收回飞行，
                    // 否则会破坏原版创造/旁观自带的飞行能力（丢出 omega 后创造失去飞行、旁观下坠）。
                    if (!p.isCreative() && !p.isSpectator()) {
                        p.getAbilities().mayfly = false;
                        p.getAbilities().flying = false;
                        p.onUpdateAbilities();
                    }
                    p.removeEffect(MobEffects.NIGHT_VISION);
                }
            }
        }

        // 屏障权杖整体平移的抑制窗口计时递减
        java.util.Iterator<java.util.Map.Entry<UUID, Integer>> shiftIt = barrierShiftSuppress.entrySet().iterator();
        while (shiftIt.hasNext()) {
            java.util.Map.Entry<UUID, Integer> e = shiftIt.next();
            int v = e.getValue() - 1;
            if (v <= 0) {
                shiftIt.remove();
            } else {
                e.setValue(v);
            }
        }

        java.util.Iterator<StaffMoveTask> it = PENDING_STAFF_MOVES.iterator();
        while (it.hasNext()) {
            StaffMoveTask task = it.next();
            if (task.tickDelay > 0) {
                task.tickDelay--;
                continue;
            }
            ServerLevel level = task.level;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(task.playerId);
            boolean allDone = processStaffMoveStep(task, player != null ? player : null);
            if (allDone) {
                it.remove();
            } else {
                task.tickDelay = 1;
            }
        }

        // 幸运维度传送门激活延迟任务
        java.util.Iterator<PortalActivationTask> patIt = PENDING_PORTAL_ACTIVATIONS.iterator();
        while (patIt.hasNext()) {
            PortalActivationTask task = patIt.next();
            if (task.tickDelay > 0) {
                task.tickDelay--;
                continue;
            }
            patIt.remove();
            // 延迟结束、闪电降下时才让投掷器消失
            Entity itemE = task.level.getEntity(task.itemEntityId);
            if (itemE instanceof ItemEntity) itemE.discard();
            LuckyPortalBlockEntity.createPortalAtWaterStructure(task.level, task.waterPos);
        }

        java.util.Iterator<BonemealTask> bit = PENDING_BONEMEAL_TASKS.iterator();
        while (bit.hasNext()) {
            BonemealTask task = bit.next();

            java.util.Iterator<BlockPos> regIt = task.regularTargets.iterator();
            while (regIt.hasNext()) {
                BlockPos pos = regIt.next();
                BlockState state = task.level.getBlockState(pos);
                if (state.getBlock() instanceof BonemealableBlock bonemealable
                    && bonemealable.isValidBonemealTarget(task.level, pos, state)) {
                    if (bonemealable.isBonemealSuccess(task.level, task.level.random, pos, state)) {
                        bonemealable.performBonemeal(task.level, task.level.random, pos, state);
                    }
                } else {
                    regIt.remove();
                }
            }

            java.util.Iterator<java.util.Map.Entry<BlockPos, Integer>> dupIt = task.duplicationTargets.entrySet().iterator();
            while (dupIt.hasNext()) {
                java.util.Map.Entry<BlockPos, Integer> entry = dupIt.next();
                BlockPos pos = entry.getKey();
                int remaining = entry.getValue();
                BlockState state = task.level.getBlockState(pos);
                if (state.getBlock() instanceof BonemealableBlock bonemealable
                    && bonemealable.isValidBonemealTarget(task.level, pos, state)) {
                    if (bonemealable.isBonemealSuccess(task.level, task.level.random, pos, state)) {
                        bonemealable.performBonemeal(task.level, task.level.random, pos, state);
                    }
                    remaining--;
                    if (remaining <= 0) {
                        dupIt.remove();
                    } else {
                        entry.setValue(remaining);
                    }
                } else {
                    dupIt.remove();
                }
            }

            if (task.regularTargets.isEmpty() && task.duplicationTargets.isEmpty()) {
                bit.remove();
            }
        }

        java.util.Iterator<java.util.Map.Entry<java.util.UUID, Integer>> cooldownIt = SMELT_COOLDOWNS.entrySet().iterator();
        while (cooldownIt.hasNext()) {
            java.util.Map.Entry<java.util.UUID, Integer> entry = cooldownIt.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                cooldownIt.remove();
            } else {
                entry.setValue(remaining);
            }
        }

        java.util.Iterator<java.util.Map.Entry<BlockPos, Integer>> fbcIt = FURNACE_BLOCK_COOLDOWNS.entrySet().iterator();
        while (fbcIt.hasNext()) {
            java.util.Map.Entry<BlockPos, Integer> entry = fbcIt.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                fbcIt.remove();
            } else {
                entry.setValue(remaining);
            }
        }

        // Process furnace acceleration (player sneaking + right-clicking burning furnace with furnace staff)
        if (!FURNACE_ACCEL_MAP.isEmpty()) {
            java.util.Iterator<java.util.Map.Entry<BlockPos, FurnaceAccelInfo>> accelIt = FURNACE_ACCEL_MAP.entrySet().iterator();
            while (accelIt.hasNext()) {
                java.util.Map.Entry<BlockPos, FurnaceAccelInfo> entry = accelIt.next();
                BlockPos pos = entry.getKey();
                FurnaceAccelInfo info = entry.getValue();

                ServerLevel level = event.getServer().getLevel(info.dimension);
                if (level == null) { accelIt.remove(); continue; }

                ServerPlayer player = event.getServer().getPlayerList().getPlayer(info.playerId);
                if (player == null || !player.isShiftKeyDown()) { accelIt.remove(); continue; }

                ItemStack mainHand = player.getMainHandItem();
                if (!(mainHand.getItem() instanceof StaffItem)) { accelIt.remove(); continue; }
                String blockType = mainHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
                if (!"furnace".equals(blockType)) { accelIt.remove(); continue; }

                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof AbstractFurnaceBlock)) { accelIt.remove(); continue; }
                boolean isLit = state.hasProperty(AbstractFurnaceBlock.LIT) && state.getValue(AbstractFurnaceBlock.LIT);
                if (!isLit) { accelIt.remove(); continue; }

                BlockEntity be = level.getBlockEntity(pos);
                if (!(be instanceof AbstractFurnaceBlockEntity furnaceBe)) { accelIt.remove(); continue; }

                int cookingProgress = getFurnaceFieldInt(furnaceBe, "cookingProgress");
                int cookingTotalTime = getFurnaceFieldInt(furnaceBe, "cookingTotalTime");
                int newProgress = cookingProgress + 1;
                if (newProgress > cookingTotalTime - 2) {
                    newProgress = cookingTotalTime - 2;
                }
                setFurnaceFieldInt(furnaceBe, "cookingProgress", newProgress);
            }
        }

        handlePhysicsNightVision(event);

        handleLapisGrabTickAll(event);

        handlePortalPreviewTick(event);

        handlePortalTick(event);

        handleNoteBlockUniverseTick(event);

        handleTransmutationTick(event);

        // 每 200 刻（约 10 秒）检查一次在线玩家是否已完成全部原版成就，用于兜底触发
        // （例如通过 /advancement grant 命令授予成就时，成就事件可能不会被触发）。
        if (event.getServer().getTickCount() % 200 == 0) {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                checkAndRewardAllVanillaAchievements(player);
            }
        }
    }

    /**
     * Note Block Universe：每随机刻空气方块有 10% 概率刷出一个随机颜色的音符粒子。
     *
     * 空气方块在原版中不会触发随机刻，因此这里在服务器刻中，对位于该维度内的每个玩家周围
     * 一定范围内的随机空气方块按 10% 概率生成一个彩色音符粒子（粒子颜色由 x 速度即“音高”决定，
     * 随机 0~1 即可得到随机颜色）。
     */
    private static void handleNoteBlockUniverseTick(ServerTickEvent.Pre event) {
        ServerLevel noteLevel = event.getServer().getLevel(
            cn.autoforged.joes_addons_for_abmc.worldgen.ModDimensions.NOTE_DIM_LEVEL);
        if (noteLevel == null) return;

        // 每 20 刻（1 秒）对音符方块宇宙玩家周围进行一次树叶保护，
        // 兜底处理区块边界/运行时变动导致的树叶重新衰减（靠近音符盒的树叶标记为 PERSISTENT）。
        boolean leafFixThisTick = event.getServer().getTickCount() % 20 == 0;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.level() != noteLevel) continue;
            if (leafFixThisTick) {
                maintainNoteDimensionLeaves(noteLevel, player.blockPosition());
            }
            spawnNoteParticles(noteLevel, player.blockPosition());
        }
    }

    private static final byte[][] NOTE_DIM_DIRS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    /**
     * 音符方块宇宙：以玩家为中心，把半径 16 格内、距离音符盒 6 格以内的树叶标记为 PERSISTENT，
     * 防止其因找不到原木而在随机刻中衰减消失。PERSISTENT 一旦设置即永久生效，故重复扫描开销很低。
     */
    private static void maintainNoteDimensionLeaves(ServerLevel level, BlockPos center) {
        final int R = 16;
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (level.getBlockState(p).is(Blocks.NOTE_BLOCK)) {
                        queue.add(new int[]{p.getX(), p.getY(), p.getZ(), 0});
                    }
                }
            }
        }
        if (queue.isEmpty()) return;
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int dist = cur[3];
            if (dist >= 6) continue;
            for (byte[] dir : NOTE_DIM_DIRS) {
                int nx = cur[0] + dir[0];
                int ny = cur[1] + dir[1];
                int nz = cur[2] + dir[2];
                BlockPos nextPos = new BlockPos(nx, ny, nz);
                BlockState next = level.getBlockState(nextPos);
                if (next.getBlock() instanceof LeavesBlock) {
                    if (!next.getValue(LeavesBlock.PERSISTENT)) {
                        level.setBlock(nextPos, next.setValue(LeavesBlock.PERSISTENT, true), 2);
                    }
                    queue.add(new int[]{nx, ny, nz, dist + 1});
                } else if (next.isAir() || next.is(BlockTags.LOGS)) {
                    queue.add(new int[]{nx, ny, nz, dist + 1});
                }
            }
        }
    }

    private static void spawnNoteParticles(ServerLevel level, BlockPos center) {
        RandomSource random = level.getRandom();
        // 每刻在玩家周围 24x24x24 范围内随机采样空气方块，按 10% 概率生成音符粒子
        final int RANGE = 12;
        final int SAMPLES = 24;
        for (int i = 0; i < SAMPLES; i++) {
            BlockPos pos = center.offset(
                random.nextInt(RANGE * 2 + 1) - RANGE,
                random.nextInt(RANGE * 2 + 1) - RANGE,
                random.nextInt(RANGE * 2 + 1) - RANGE);
            if (random.nextFloat() >= 0.1F) continue;
            if (!level.getBlockState(pos).isAir()) continue;
            double x = pos.getX() + 0.5D;
            double y = pos.getY() + 0.5D;
            double z = pos.getZ() + 0.5D;
            // 注意：count>0 时客户端对颜色取 nextGaussian*MaxSpeed（speed=0 恒为 0，导致全绿），
            // 因此改用 count=0 的“单粒子精确参数”分支：粒子颜色 = speed * 偏移量。
            // 传 speed=1.0、xd=随机[0,1) → 每个粒子独立的随机音符颜色。
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE,
                x, y, z, 0, random.nextFloat(), 0.0D, 0.0D, 1.0D);
        }
    }

    /**
     * Note Block Universe：敌对生物变为中立生物。
     *
     * 通过拦截 LivingChangeTargetEvent，在该维度内禁止敌对生物获得攻击目标，从而表现为中立
     * （不会主动攻击玩家）。
     */
    private static void onLivingChangeTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
        net.minecraft.world.entity.LivingEntity mob = event.getEntity();
        // 变形玩家：任何生物都不得锁定变形中的玩家（玩家已隐身，但近距离仍会被敌对生物探测到）。
        // 在此直接阻止目标锁定，即使玩家贴得很近也不会被察觉并发起攻击。
        if (event.getNewAboutToBeSetTarget() instanceof Player targetPlayer
            && TRANSMUTED_ENTITIES.contains(targetPlayer.getUUID())) {
            event.setNewAboutToBeSetTarget(null);
            return;
        }
        if (mob.level().dimension() != cn.autoforged.joes_addons_for_abmc.worldgen.ModDimensions.NOTE_DIM_LEVEL) {
            return;
        }
        // 音符方块宇宙：所有生物都不锁定攻击目标（包括怪物与生物间的捕食关系，
        // 如狼攻击羊、豹猫攻击鸡、北极熊攻击狐狸），使该维度保持和平共处。
        event.setNewAboutToBeSetTarget(null);
    }

    // 音符方块宇宙：移除村民对亡灵生物的威胁感知（VillagerHostilesSensor），
    // 使 NEAREST_HOSTILE 不再被亡灵生物触发，村民从而不会进入恐慌/躲避亡灵。
    private static void removeVillagerHostilesSensor(Villager villager) {
        try {
            java.lang.reflect.Field sensorField = Brain.class.getDeclaredField("sensors");
            sensorField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Object, Object> sensors = (Map<Object, Object>) sensorField.get(villager.getBrain());
            sensors.remove(SensorType.VILLAGER_HOSTILES);
        } catch (Exception e) {
            LOGGER.warn("[NoteDim] 移除村民亡灵威胁传感器失败", e);
        }
    }

    // 音符方块宇宙：把凋灵两个副头的下一轮更新计数值推到极大值，使副头发射逻辑（含
    // 空闲射击与按目标射击）永不执行，既不发射头颅也不播放攻击音效。
    private static void neutralizeWitherSideHeads(WitherBoss wither) {
        try {
            java.lang.reflect.Field f = WitherBoss.class.getDeclaredField("nextHeadUpdate");
            f.setAccessible(true);
            int[] next = (int[]) f.get(wither);
            next[0] = Integer.MAX_VALUE;
            next[1] = Integer.MAX_VALUE;
        } catch (Exception e) {
            LOGGER.warn("[NoteDim] 中和凋灵副头失败", e);
        }
    }

    // 音符方块宇宙：铁傀儡是否一个“攻击怪物（Mob/Enemy）”的目标 Goal。
    // 铁傀儡注册了两条 NearestAttackableTargetGoal 之一指向 Mob.class（怪物），
    // 另一条指向 Player.class（被激怒时）；仅移除前者，保留对玩家的被动反击。
    private static boolean isMonsterTargetingGoal(Goal goal) {
        if (!(goal instanceof NearestAttackableTargetGoal<?> target)) {
            return false;
        }
        try {
            java.lang.reflect.Field targetTypeField =
                NearestAttackableTargetGoal.class.getDeclaredField("targetType");
            targetTypeField.setAccessible(true);
            Class<?> targetType = (Class<?>) targetTypeField.get(target);
            return targetType == Mob.class;
        } catch (Exception e) {
            return false;
        }
    }

    // 反射读取 AvoidEntityGoal 的目标实体类型；失败返回 null。
    private static Class<?> getAvoidGoalClass(AvoidEntityGoal<?> avoidGoal) {
        try {
            java.lang.reflect.Field avoidClassField =
                AvoidEntityGoal.class.getDeclaredField("avoidClass");
            avoidClassField.setAccessible(true);
            return (Class<?>) avoidClassField.get(avoidGoal);
        } catch (Exception e) {
            return null;
        }
    }

    // 判断 Goal 是否为逃离猫/豹猫的 AvoidEntityGoal（用于移除苦力怕对猫/豹猫的恐惧）
    private static boolean isCatOrOcelotAvoidGoal(Goal goal) {
        if (!(goal instanceof AvoidEntityGoal<?> avoidGoal)) {
            return false;
        }
        Class<?> avoidClass = getAvoidGoalClass(avoidGoal);
        return avoidClass == Cat.class || avoidClass == Ocelot.class;
    }

    // 音符方块宇宙：判断某“逃离”Goal 是否应被移除（仅动物/特定生物）。
    // 覆盖：狼逃羊驼、狐狸逃北极熊/狼、兔子逃狼/怪物、骷髅类逃狼、海豚逃守卫者、
    // 蜘蛛逃犰狳、流浪商人逃怪物、各类动物逃玩家、唤魔者逃玩家。
    private static boolean shouldRemoveAvoidGoal(PathfinderMob mob, Goal goal) {
        if (!(goal instanceof AvoidEntityGoal<?> avoidGoal)) {
            return false;
        }
        Class<?> avoidClass = getAvoidGoalClass(avoidGoal);
        if (avoidClass == null) {
            return false;
        }
        // 狼逃离羊驼
        if (mob instanceof Wolf && avoidClass == Llama.class) {
            return true;
        }
        // 狐狸逃离北极熊
        if (mob instanceof Fox && (avoidClass == PolarBear.class || avoidClass == Wolf.class)) {
            return true;
        }
        // 兔子逃离狼与怪物
        if (mob instanceof Rabbit && (avoidClass == Wolf.class || avoidClass == Monster.class)) {
            return true;
        }
        // 各类骷髅（骷髅/流浪者/凋灵骷髅）逃离狼
        if (mob instanceof AbstractSkeleton && avoidClass == Wolf.class) {
            return true;
        }
        // 海豚逃离守卫者
        if (mob instanceof Dolphin && avoidClass == Guardian.class) {
            return true;
        }
        // 蜘蛛逃离犰狳
        if (mob instanceof Spider && avoidClass == Armadillo.class) {
            return true;
        }
        // 流浪商人逃离各类怪物
        if (mob instanceof WanderingTrader && Monster.class.isAssignableFrom(avoidClass)) {
            return true;
        }
        // 唤魔者逃离玩家
        if (mob instanceof Evoker && avoidClass == Player.class) {
            return true;
        }
        // 各类动物逃离玩家
        if (mob instanceof Animal && avoidClass == Player.class) {
            return true;
        }
        return false;
    }

    // 音符方块宇宙：流浪商人夜间饮隐身药水/白天饮牛奶的 UseItemGoal 是否应被移除
    private static boolean isTraderDrinkingGoal(PathfinderMob mob, Goal goal) {
        return mob instanceof WanderingTrader && goal instanceof UseItemGoal<?>;
    }

    private static void handleTransmutationTick(ServerTickEvent.Pre event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            tickTransmutationFallingLocks(level);
            tickTransmutationItems(level);
            tickTransmutationBlocks(level, level.dimension().location());
        }
        // 壳体可能位于任意维度，因此全局跨维度处理，避免每维度各自递减导致漏查/重复
        tickLivingShells(event.getServer());
    }

    // 凋灵/末影龙对一切状态效果免疫，onMobEffectAdded 永远不会触发。
    // 改为在药水瓶碎裂的瞬间（见 onEntityLeaveLevel）对断裂点范围内的 Boss 直接变形，
    // 使“瓶体碎裂”与“Boss 变成方块/物品”在同一时刻发生。
    private static boolean isTransmutableBoss(LivingEntity e) {
        return e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
            || e instanceof net.minecraft.world.entity.boss.wither.WitherBoss;
    }

    private static void tryTransmuteBoss(ServerLevel level, LivingEntity boss, String itemType) {
        if (TRANSMUTED_ENTITIES.contains(boss.getUUID())
            || LIVING_SHELLS.containsKey(boss.getUUID()) || !boss.isAlive()) return;
        UUID killerUuid = findTransmutationKiller(level, boss);
        // 没有实际的状态效果时长可用，沿用变形药水的默认时长（2000 tick = 100 秒）
        performTransmutation(level, boss, itemType, 2000, killerUuid);
    }

    // 玩家被变成方块后：变身期间下落方块跟随玩家（不再自然下落），并在这里倒计时
    private static void tickTransmutationFallingLocks(ServerLevel level) {
        java.util.Iterator<Map.Entry<UUID, TransmutationData>> it = FALLING_TRANSMUTATIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TransmutationData> entry = it.next();
            TransmutationData data = entry.getValue();
            if (data.playerUuid() == null) continue; // 非玩家源保持原行为（自然下落落地成方块）
            Entity falling = level.getEntity(entry.getKey());
            if (falling == null || !falling.isAlive()) continue;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(data.playerUuid());
            if (player == null) continue;
            // 玩家源：方块跟随玩家，同时倒计时；倒计时结束复原玩家
            makeTransmutedFollowPlayer(falling, player);
            TransmutationData nd = new TransmutationData(data.entityNbt, data.remainingTicks - 1,
                data.killerPlayerUuid(), data.itemType, data.playerUuid());
            entry.setValue(nd);
            if (nd.remainingTicks <= 0) {
                it.remove();
                BlockPos pos = falling.blockPosition();
                falling.discard();
                revertTransmutedPlayer(level, data, pos);
            }
        }
    }

    private static void handleLapisGrabTickAll(ServerTickEvent.Pre event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : level.getPlayers(LivingEntity::isAlive)) {
                if (LAPIS_GRABBED_ENTITIES.containsKey(player.getUUID())) {
                    handleLapisGrabTick(level, player);
                }
            }
        }
    }

    private static void handlePhysicsNightVision(ServerTickEvent.Pre event) {
        ServerLevel overworld = event.getServer().overworld();
        if (overworld == null) return;
        long gameTime = overworld.getGameTime();

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!isPhysicsDimension(player.level())) {
                if (PHYSICS_NIGHT_VISION_PLAYERS.remove(player.getUUID())) {
                    player.removeEffect(MobEffects.NIGHT_VISION);
                }
                continue;
            }

            if (gameTime % 300 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0, false, false));
                PHYSICS_NIGHT_VISION_PLAYERS.add(player.getUUID());
            }
        }
    }

    private static void handlePortalTick(ServerTickEvent.Pre event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            java.util.List<Map.Entry<Integer, Integer>> snapshot =
                new java.util.ArrayList<>(PORTAL_PAIRS.entrySet());
            for (Map.Entry<Integer, Integer> entry : snapshot) {
                int portalId = entry.getKey();
                Entity portalEntity = level.getEntity(portalId);
                if (!(portalEntity instanceof PortalEntity portal) || portal.isRemoved()) continue;

                AABB portalBox = portal.getBoundingBox().inflate(0.5);
                Vec3 portalNormal = portal.getPortalNormal();
                Vec3 portalRight = portal.getPortalRight();
                Vec3 portalUp = portal.getPortalUp();

                for (Entity traveller : level.getEntitiesOfClass(Entity.class, portalBox,
                    e -> !(e instanceof PortalEntity) && e.isAlive())) {
                    UUID travellerUuid = traveller.getUUID();

                    boolean isProjectile = traveller instanceof Projectile;

                    // 传送滞环：非弹射物刚退出某扇门后，在未离开该门接触区前不再被传送（避免来回抖动）
                    if (!isProjectile) {
                        Integer exitLock = PORTAL_EXIT_LOCKS.get(travellerUuid);
                        if (exitLock != null && exitLock == portal.getId()) {
                            // 例外：若实体同时接触同一对传送门的双方，则跳过滞环限制（允许立即双向往返）
                            int pairedId = PORTAL_PAIRS.getOrDefault(portal.getId(), -1);
                            PortalEntity pairedPortal = null;
                            if (pairedId > 0) {
                                pairedPortal = findPortalEntity(event.getServer(), pairedId);
                            }
                            boolean touchingBoth = (pairedPortal != null)
                                && pairedPortal.getBoundingBox().inflate(0.5).intersects(traveller.getBoundingBox());
                            if (!touchingBoth) {
                                continue;
                            }
                        }
                    }

                    int pairId = Math.min(portal.getId(), PORTAL_PAIRS.getOrDefault(portal.getId(), portal.getId()));
                    long currentGameTime = level.getGameTime();

                    if (isPhysicsDimension(level)) {
                        BlockPos returnPos = PHYSICS_RETURN_TARGETS.get(travellerUuid);
                        if (returnPos != null) {
                            ServerLevel overworld = level.getServer().overworld();
                            if (overworld != null) {
                                if (traveller instanceof ServerPlayer sp) {
                                    sp.teleportTo(overworld, returnPos.getX() + 0.5, returnPos.getY() + 1.0,
                                        returnPos.getZ() + 0.5, sp.getYRot(), sp.getXRot());
                                } else {
                                    traveller.changeDimension(new DimensionTransition(
                                        overworld,
                                        new net.minecraft.world.phys.Vec3(returnPos.getX() + 0.5,
                                            returnPos.getY() + 1.0, returnPos.getZ() + 0.5),
                                        traveller.getDeltaMovement(),
                                        traveller.getYRot(), traveller.getXRot(),
                                        DimensionTransition.DO_NOTHING));
                                }
                                PHYSICS_RETURN_TARGETS.remove(travellerUuid);
                                PORTAL_CONTACT_TIMERS.remove(travellerUuid);
                                portal.discard();
                            }
                            continue;
                        }
                    }

                    // 触碰即传送，不做接触刻数延迟
                    int destinationId = PORTAL_PAIRS.get(portal.getId());
                    if (destinationId <= 0) continue;

                    Entity destEntity = level.getEntity(destinationId);
                    if (!(destEntity instanceof PortalEntity destPortal) || destPortal.isRemoved()) continue;

                    Vec3 velocity = traveller.getDeltaMovement();
                    double velNormal = velocity.dot(portalNormal);
                    double velRight = velocity.dot(portalRight);
                    double velUp = velocity.dot(portalUp);

                    Vec3 destVelocity = destPortal.getPortalNormal().scale(-velNormal)
                        .add(destPortal.getPortalRight().scale(velRight))
                        .add(destPortal.getPortalUp().scale(velUp));

                    // 把实体中心对齐到目标传送门实体中心，再沿法线推出到开放侧空气里（避免嵌在墙上）
                    Vec3 destPos = destPortal.position()
                        .add(destPortal.getPortalUp().scale(1.0 - traveller.getBbHeight() / 2.0))
                        .add(destPortal.getPortalNormal().scale(0.25));

                    traveller.teleportTo(destPos.x, destPos.y, destPos.z);
                    traveller.setDeltaMovement(destVelocity);
                    traveller.hurtMarked = true;

                    // 记录实体是从哪扇门退出的，离开其接触区前不再传送
                    if (!isProjectile) {
                        PORTAL_EXIT_LOCKS.put(travellerUuid, destPortal.getId());
                        PORTAL_CONTACT_TIMERS.remove(travellerUuid);
                    }

                    if (traveller instanceof ServerPlayer sp) {
                        sp.connection.teleport(destPos.x, destPos.y, destPos.z, sp.getYRot(), sp.getXRot());
                    } else {
                        // 非玩家实体（含弹射物、下落的方块）：立即向客户端广播位移，避免客户端延迟后突然跳位
                        net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket tp =
                            new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(traveller);
                        for (ServerPlayer viewer : level.getPlayers(p -> p.distanceToSqr(destPos) < 256.0 * 256.0)) {
                            viewer.connection.send(tp);
                            viewer.connection.send(
                                new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(traveller));
                        }
                    }
                }
            }
        }

        // 清理传送滞环：实体一旦离开其刚退出的传送门接触区，即解除锁定，保证可双向传送
        for (Map.Entry<UUID, Integer> lockEntry : new java.util.ArrayList<>(PORTAL_EXIT_LOCKS.entrySet())) {
            UUID lockedUuid = lockEntry.getKey();
            int lockedPortalId = lockEntry.getValue();
            Entity lockedPortalEnt = null;
            for (ServerLevel lvl : event.getServer().getAllLevels()) {
                lockedPortalEnt = lvl.getEntity(lockedPortalId);
                if (lockedPortalEnt != null) break;
            }
            if (!(lockedPortalEnt instanceof PortalEntity lockedPortal) || lockedPortal.isRemoved()) {
                PORTAL_EXIT_LOCKS.remove(lockedUuid);
                continue;
            }
            Entity lockedEntity = null;
            for (ServerLevel lvl : event.getServer().getAllLevels()) {
                lockedEntity = lvl.getEntity(lockedUuid);
                if (lockedEntity != null) break;
            }
            if (lockedEntity == null || lockedEntity.isRemoved()
                || !lockedEntity.getBoundingBox().inflate(0.5).intersects(lockedPortal.getBoundingBox())) {
                PORTAL_EXIT_LOCKS.remove(lockedUuid);
            }
        }

        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!isPhysicsDimension(level)) continue;
            for (Entity portalEnt : level.getEntitiesOfClass(PortalEntity.class,
                new AABB(-30000000.0, level.getMinBuildHeight(), -30000000.0,
                    30000000.0, level.getMaxBuildHeight(), 30000000.0),
                Entity::isAlive)) {
                AABB portalBox = portalEnt.getBoundingBox().inflate(0.5);
                for (Entity traveller : level.getEntitiesOfClass(Entity.class, portalBox,
                    e -> !(e instanceof PortalEntity) && e.isAlive())) {
                    UUID travellerUuid = traveller.getUUID();
                    BlockPos returnPos = PHYSICS_RETURN_TARGETS.get(travellerUuid);
                    if (returnPos != null) {
                        ServerLevel overworld = level.getServer().overworld();
                        if (overworld != null) {
                            if (traveller instanceof ServerPlayer sp) {
                                sp.teleportTo(overworld, returnPos.getX() + 0.5,
                                    returnPos.getY() + 1.0, returnPos.getZ() + 0.5,
                                    sp.getYRot(), sp.getXRot());
                            } else {
                                traveller.changeDimension(new DimensionTransition(
                                    overworld,
                                    new net.minecraft.world.phys.Vec3(
                                        returnPos.getX() + 0.5, returnPos.getY() + 1.0,
                                        returnPos.getZ() + 0.5),
                                    traveller.getDeltaMovement(),
                                    traveller.getYRot(), traveller.getXRot(),
                                    DimensionTransition.DO_NOTHING));
                            }
                            PHYSICS_RETURN_TARGETS.remove(travellerUuid);
                            PORTAL_CONTACT_TIMERS.remove(travellerUuid);
                            portalEnt.discard();
                        }
                    }
                }
            }
        }

        java.util.List<Integer> expiredIds = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Integer, Integer> entry : PORTAL_LIFESPANS.entrySet()) {
            int entityId = entry.getKey();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                expiredIds.add(entityId);
                Integer paired = PORTAL_PAIRS.get(entityId);
                if (paired != null) {
                    expiredIds.add(paired);
                }
            } else {
                PORTAL_LIFESPANS.put(entityId, remaining);
            }
        }
        for (int id : expiredIds) {
            PORTAL_LIFESPANS.remove(id);
            Integer paired = PORTAL_PAIRS.remove(id);
            if (paired != null) {
                PORTAL_PAIRS.remove(paired);
            }
            for (ServerLevel lvl : event.getServer().getAllLevels()) {
                Entity e = lvl.getEntity(id);
                if (e != null) e.discard();
            }
        }

        java.util.List<Map.Entry<UUID, long[]>> triggeredContacts = new java.util.ArrayList<>();
        for (Map.Entry<UUID, long[]> entry : PORTAL_CONTACT_TIMERS.entrySet()) {
            if (entry.getValue()[1] >= 100) {
                triggeredContacts.add(entry);
            }
        }
        for (Map.Entry<UUID, long[]> entry : triggeredContacts) {
            UUID uuid = entry.getKey();
            long[] data = entry.getValue();
            int triggerPairId = (int) data[0];

            Entity theEntity = null;
            ServerLevel entityLevel = null;
            for (ServerLevel lvl : event.getServer().getAllLevels()) {
                theEntity = lvl.getEntity(uuid);
                if (theEntity != null) {
                    entityLevel = lvl;
                    break;
                }
            }
            if (theEntity == null || entityLevel == null) {
                PORTAL_CONTACT_TIMERS.remove(uuid);
                continue;
            }

            ServerLevel physicsLevel = event.getServer().getLevel(
                cn.autoforged.joes_addons_for_abmc.worldgen.ModDimensions.PHYSICS_DIM_LEVEL);
            if (physicsLevel == null) continue;

            teleportToPhysicsDimension(physicsLevel, theEntity);

            java.util.List<Integer> toDiscard = new java.util.ArrayList<>();
            toDiscard.add(triggerPairId);
            Integer pairId = PORTAL_PAIRS.get(triggerPairId);
            if (pairId != null) toDiscard.add(pairId);
            for (int pid : toDiscard) {
                PORTAL_PAIRS.remove(pid);
                PORTAL_LIFESPANS.remove(pid);
                for (ServerLevel lvl : event.getServer().getAllLevels()) {
                    Entity e = lvl.getEntity(pid);
                    if (e != null) e.discard();
                }
            }

            PORTAL_CONTACT_TIMERS.entrySet().removeIf(e ->
                (int) e.getValue()[0] == triggerPairId || (int) e.getValue()[0] == pairId);
        }

        // 传送门权杖 R 键收敛：一对传送门以越来越快的速度相互靠近，重合后传送接触实体到物理维度并删除
        java.util.Iterator<java.util.Map.Entry<Integer, PortalCollapseState>> colIt =
            PORTAL_COLLAPSES.entrySet().iterator();
        while (colIt.hasNext()) {
            java.util.Map.Entry<Integer, PortalCollapseState> colEntry = colIt.next();
            PortalCollapseState cs = colEntry.getValue();
            PortalEntity portalA = findPortalEntity(event.getServer(), cs.portalAId);
            PortalEntity portalB = findPortalEntity(event.getServer(), cs.portalBId);
            if (portalA == null || portalB == null || portalA.isRemoved() || portalB.isRemoved()) {
                colIt.remove();
                continue;
            }
            Vec3 delta = new Vec3(portalB.getX() - portalA.getX(),
                portalB.getY() - portalA.getY(), portalB.getZ() - portalA.getZ());
            double dist = delta.length();
            double step = cs.speed;
            cs.speed += cs.accel;

            if (step * 2 >= dist) {
                // 重合：两者都移到中点，传送所有接触实体到物理维度，再删除这一对
                Vec3 mid = portalA.position().add(delta.scale(0.5));
                portalA.setPos(mid);
                portalB.setPos(mid);
                ServerLevel physicsLevel = event.getServer().getLevel(
                    cn.autoforged.joes_addons_for_abmc.worldgen.ModDimensions.PHYSICS_DIM_LEVEL);
                if (physicsLevel != null) {
                    java.util.List<Entity> toTeleport = new java.util.ArrayList<>();
                    for (ServerLevel lvl : event.getServer().getAllLevels()) {
                        for (PortalEntity p : new PortalEntity[]{portalA, portalB}) {
                            AABB box = p.getBoundingBox().inflate(0.5);
                            toTeleport.addAll(lvl.getEntitiesOfClass(Entity.class, box,
                                e -> !(e instanceof PortalEntity) && e.isAlive()));
                        }
                    }
                    for (Entity traveller : toTeleport) {
                        teleportToPhysicsDimension(physicsLevel, traveller);
                    }
                }
                java.util.List<Integer> toDiscard = java.util.List.of(cs.portalAId, cs.portalBId);
                for (int pid : toDiscard) {
                    PORTAL_PAIRS.remove(pid);
                    PORTAL_LIFESPANS.remove(pid);
                    PLAYER_LATEST_PAIR.entrySet().removeIf(e -> e.getValue() == pid);
                    for (ServerLevel lvl : event.getServer().getAllLevels()) {
                        Entity e = lvl.getEntity(pid);
                        if (e != null) e.discard();
                    }
                }
                PORTAL_CONTACT_TIMERS.entrySet().removeIf(e ->
                    (int) e.getValue()[0] == cs.portalAId || (int) e.getValue()[0] == cs.portalBId);
                colIt.remove();
            } else {
                Vec3 dir = delta.normalize();
                portalA.setPos(portalA.position().add(dir.scale(step)));
                portalB.setPos(portalB.position().subtract(dir.scale(step)));
                syncPortalPosition(portalA);
                syncPortalPosition(portalB);
            }
        } 
    }

    // 在服务端所有世界中按实体 id 查找传送门
    private static PortalEntity findPortalEntity(net.minecraft.server.MinecraftServer server, int id) {
        if (server == null || id <= 0) return null;
        for (ServerLevel lvl : server.getAllLevels()) {
            Entity e = lvl.getEntity(id);
            if (e instanceof PortalEntity pe && !pe.isRemoved()) return pe;
        }
        return null;
    }

    // 将实体传送到物理维度（记录返回点、在 127 高度生成返回传送门）
    private static void teleportToPhysicsDimension(ServerLevel physicsLevel, Entity entity) {
        UUID uuid = entity.getUUID();
        BlockPos returnPos = entity.blockPosition();
        PHYSICS_RETURN_TARGETS.put(uuid, returnPos);
        double px = entity.getX();
        double py = 128.0;
        double pz = entity.getZ();
        PortalEntity physicsReturnPortal = PortalEntity.create(physicsLevel, new Vec3(px, 127.0, pz), 0.0F, 0.0F);
        physicsReturnPortal.lifespan = -1;
        physicsLevel.addFreshEntity(physicsReturnPortal);
        if (entity instanceof ServerPlayer sp) {
            sp.teleportTo(physicsLevel, px, py, pz, sp.getYRot(), sp.getXRot());
        } else {
            entity.changeDimension(new DimensionTransition(
                physicsLevel, new Vec3(px, py, pz),
                entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(),
                DimensionTransition.DO_NOTHING));
        }
    }

    // 向客户端广播传送门实体位置（收敛动画中需要手动同步，因为实体本身不移动）
    private static void syncPortalPosition(PortalEntity portal) {
        if (!(portal.level() instanceof ServerLevel sl)) return;
        net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket tp =
            new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(portal);
        for (ServerPlayer viewer : sl.getPlayers(p -> p.distanceToSqr(portal.position()) < 256.0 * 256.0)) {
            viewer.connection.send(tp);
        }
    }

    private static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        int leavingId = event.getEntity().getId();
        String leavingItemType = TRANSMUTATION_POTION_ITEM_TYPES.remove(leavingId);

        Entity entity = event.getEntity();

        // 变形药水瓶碎裂的瞬间：对断裂点范围内的免疫状态效果的 Boss（凋灵/末影龙）直接变形，
        // 让瓶体碎裂与 Boss 变成方块/物品在同一时刻发生，而非药水仍在飞行时提前变形。
        if (entity instanceof ThrownPotion potion
            && event.getLevel() instanceof ServerLevel serverLevel) {
            String itemType = leavingItemType;
            // 随机变形药水（无固定目标）：碰到的瞬间从原版方块中随机选取一个
            if (itemType == null && isTransmutationPotion(potion.getItem())) {
                itemType = pickRandomTransmutationType();
            }
            if (itemType != null) {
                for (LivingEntity boss : serverLevel.getEntitiesOfClass(LivingEntity.class,
                    potion.getBoundingBox().inflate(4.5), ModMain::isTransmutableBoss)) {
                    tryTransmuteBoss(serverLevel, boss, itemType);
                }
            }
        }

        // 变形解药：喷溅/滞留药水瓶碎裂的瞬间，把范围内的变身方块/物品提前复原为生物。
        if (entity instanceof ThrownPotion potion
            && event.getLevel() instanceof ServerLevel serverLevel
            && isAntidotePotion(potion.getItem())) {
            boolean lingering = potion.getItem().is(Items.LINGERING_POTION);
            double radius = lingering ? 5.0 : 4.5;
            applyAntidoteSplash(serverLevel, potion.getX(), potion.getY(), potion.getZ(), radius);
        }

        if (entity instanceof FallingBlockEntity fallingBlock) {
            TransmutationData data = FALLING_TRANSMUTATIONS.remove(fallingBlock.getUUID());
            if (data != null && entity.level() instanceof ServerLevel serverLevel) {
                BlockPos landPos = fallingBlock.blockPosition();
                BlockState landState = serverLevel.getBlockState(landPos);
                if (!landState.isAir() && landState.getBlock() == fallingBlock.getBlockState().getBlock()) {
                    ResourceLocation dimId = serverLevel.dimension().location();
                    BLOCK_TRANSMUTATIONS
                        .computeIfAbsent(dimId, k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(landPos, k -> new java.util.ArrayList<>())
                        .add(data);
                } else {
                    // 未能正常落地成方块：按破坏处理结算
                    if (data.playerUuid() != null) {
                        killTransmutedPlayer(serverLevel, data, fallingBlock.blockPosition(), true);
                    } else {
                        handleTransmutationKillCredit(serverLevel, data,
                            fallingBlock.blockPosition(), data.killerPlayerUuid());
                    }
                }
            }
        }
        // 注意：物品实体离开关卡（例如区块卸载）不再移除变身数据或计击杀，
        // 以便物体在区块重新加载后仍能按时复原为原生物（与方块的持久化行为一致）。

        // 玩家空壳/生物壳离开关卡：解除倒计时与锁定跟踪。
        // 玩家空壳重新加载时会由 onEntityJoinLevel 依据 NBT 恢复；生物壳不持久化，卸载后视为普通生物。
        if (LIVING_SHELLS.containsKey(entity.getUUID())) {
            LOGGER.info("[DBG] onEntityLeaveLevel: SHELL leaving level type={} uuid={} mapSize={}",
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), entity.getUUID(), LIVING_SHELLS.size());
        }
        LIVING_SHELLS.remove(entity.getUUID());

        PHYSICS_DELTA_MOVEMENTS.remove(event.getEntity().getId());
        PHYSICS_PRE_TICK_POSITIONS.remove(event.getEntity().getId());
        PHYSICS_DIM_ENTITIES.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) {
            LuckyPortalBlock.removePortalTimer(player.getUUID());
            LAPIS_FLIGHT_PLAYERS.remove(player.getUUID());
            COMMAND_FLIGHT_PLAYERS.remove(player.getUUID());
            PHYSICS_NIGHT_VISION_PLAYERS.remove(player.getUUID());
        }
    }

    private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        Entity entity = event.getEntity();

        // Creeper Clan 维度兜底：加入世界的非苦力怕生物，若属于「世界自动生成」途径则阻止加入。
        // FinalizeSpawnEvent 已拦截绝大多数（含区块初始生成），此处作为兜底覆盖任何漏网路径
        // （如区块生成流程中 finalizeSpawn 未触发、或实体延迟加入的情况）。
        // 玩家主动召唤（刷怪蛋/命令/繁殖/转换等）spawnType 不在自动生成列表，正常放行。
        if (serverLevel.dimension() == ModDimensions.CREEPER_CLAN_DIM_LEVEL
            && entity instanceof net.minecraft.world.entity.Mob mob
            && !(mob instanceof net.minecraft.world.entity.monster.Creeper)
            && isWorldAutoSpawnType(mob.getSpawnType())) {
            event.setCanceled(true);
            return;
        }

        // 指令文本 TextDisplay 跨存档清理：上个存档退出时未销毁、随区块保存下来的遗留实体，
        // 重进存档时会经 PersistentEntitySectionManager 从磁盘加载并触发本事件。
        // 若该实体已带 COMMAND_TEXT_TAG 标记、却不在「本次会话仍在跟踪的指令文本列表」中，
        // 说明它是跨存档残留 → 直接阻止其加入世界。这样它不会注册进区块实体存储，也就不会再被
        // 写入存档，从根上杜绝「退出时头顶仍渲染 text display、重进后实体残留不消失」。
        // 本次会话正常创建的实体均已在跟踪列表内（见 spawnCommandTextDisplay），不会被误删。
        if (entity instanceof net.minecraft.world.entity.Display.TextDisplay textDisplay
            && textDisplay.getPersistentData().getBoolean(COMMAND_TEXT_TAG)) {
            boolean tracked = false;
            for (CommandTextDisplayEntry entry : COMMAND_ACTIVE_TEXT_DISPLAYS) {
                if (entry.display == textDisplay) { tracked = true; break; }
            }
            if (!tracked) {
                event.setCanceled(true);
                return;
            }
        }

        // 玩家空壳：重新加载时恢复“玩家锁定”跟踪（原玩家 UUID 等已持久化在实体 NBT 中）
        if (entity instanceof PlayerShellEntity shell) {
            reconstructLivingShell(shell);
        }

        if (entity instanceof ThrownPotion thrownPotion) {
            ItemStack potionStack = thrownPotion.getItem();
            String itemType = potionStack.getOrDefault(ModDataComponents.ITEM_TYPE.get(), null);
            if (itemType != null) {
                TRANSMUTATION_POTION_ITEM_TYPES.put(thrownPotion.getId(), itemType);
            }
        }

        if (entity instanceof AreaEffectCloud cloud) {
            for (ThrownPotion nearby : serverLevel.getEntitiesOfClass(ThrownPotion.class,
                cloud.getBoundingBox().inflate(1.0))) {
                String itemType = TRANSMUTATION_POTION_ITEM_TYPES.get(nearby.getId());
                if (itemType != null) {
                    TRANSMUTATION_POTION_ITEM_TYPES.put(cloud.getId(), itemType);
                    break;
                }
            }
        }

        // --- 女巫小屋·女巫 Boss 变种：1% 概率取代小屋女巫；200 座小屋未出现则第 201 座必出。
        //     女巫小屋结构生成的女巫也会经 PersistentEntitySectionManager 触发本事件（level 为 ServerLevel），
        //     且此时结构引用已写入区块，可正确判定。已处理过的女巫写入标签，避免重复判定。---
        if (entity instanceof Witch witch && !witch.getPersistentData().contains(WITCH_BOSS_TAG)) {
            handleWitchHutBossSpawn(serverLevel, witch);
        }

        // 生物变 TNT：方块被点燃时原版会移除 TNT 方块并生成 PrimedTnt。
        // 把该位置对应的生物数据转交给 PrimedTnt，待其爆炸瞬间才判死生物（而不是点燃即死）。
        if (entity instanceof net.minecraft.world.entity.item.PrimedTnt tnt) {
            ResourceLocation dimId = serverLevel.dimension().location();
            Map<BlockPos, java.util.List<TransmutationData>> dimMap = BLOCK_TRANSMUTATIONS.get(dimId);
            if (dimMap != null) {
                BlockPos tntPos = BlockPos.containing(tnt.position());
                java.util.List<TransmutationData> dataList = dimMap.get(tntPos);
                if (dataList != null && !dataList.isEmpty()
                        && "minecraft:tnt".equals(dataList.get(0).itemType())) {
                    dimMap.remove(tntPos);
                    TNT_TRANSMUTATIONS.put(tnt.getUUID(), dataList.get(0));
                }
            }
        }

        // 重力块（如沙子）下方方块被破坏导致再次下落：把该位置原本追踪的变身数据
        // 挂到新生成的下落方块上，避免落地后丢失“变回生物”的特性（bug 修复）。
        if (entity instanceof FallingBlockEntity fallingBlock) {
            ResourceLocation dimId = serverLevel.dimension().location();
            Map<BlockPos, java.util.List<TransmutationData>> dimMap = BLOCK_TRANSMUTATIONS.get(dimId);
            if (dimMap != null) {
                BlockPos spawnPos = BlockPos.containing(entity.position());
                java.util.List<TransmutationData> transferred = dimMap.remove(spawnPos);
                if (transferred != null && !transferred.isEmpty()) {
                    for (TransmutationData d : transferred) {
                        FALLING_TRANSMUTATIONS.put(fallingBlock.getUUID(), d);
                    }
                }
            }
        }

        // --- 音符方块宇宙 AI 调整（仅该维度生效） ---
        if (entity.level().dimension() == ModDimensions.NOTE_DIM_LEVEL) {
            // 村民：移除“亡灵威胁”传感器，使其不再躲避亡灵生物
            if (entity instanceof Villager villager) {
                removeVillagerHostilesSensor(villager);
            } else if (entity instanceof IronGolem golem) {
                // 铁傀儡：移除攻击怪物（Enemy）的目标 Goal，使其不主动攻击怪物
                golem.targetSelector.removeAllGoals(ModMain::isMonsterTargetingGoal);
            } else if (entity instanceof Creeper creeper) {
                // 苦力怕：移除逃离猫/豹猫的 Goal，使其在该维度不再躲避猫/豹猫
                creeper.goalSelector.removeAllGoals(ModMain::isCatOrOcelotAvoidGoal);
            } else if (entity instanceof WitherSkull skull
                && skull.getOwner() instanceof WitherBoss) {
                // 凋灵：拦截其发射的所有头颅（主头与副头），使凋灵在该维度完全中立
                event.setCanceled(true);
                return;
            }

            // 移除该维度内各类“逃离”行为（狼逃羊驼、狐狸逃北极熊、流浪商人逃怪物、
            // 动物逃玩家、唤魔者逃玩家），以及流浪商人夜间饮隐身药水/白天饮牛奶的行为。
            if (entity instanceof PathfinderMob pathfinderMob) {
                pathfinderMob.goalSelector.removeAllGoals(goal ->
                    ModMain.shouldRemoveAvoidGoal(pathfinderMob, goal)
                        || ModMain.isTraderDrinkingGoal(pathfinderMob, goal));
            }
        }

        // --- 多重射击：子弹/箭矢刚加入世界时（发射瞬间）即在任意维度分裂（不局限于物理维度）。
        //     分裂依据射手“魔咒状态效果”，或射手手持武器（弓/弩）上的多重射击魔咒。
        Entity owner = null;
        if (entity instanceof Projectile projectile) {
            owner = projectile.getOwner();
        } else if (entity instanceof ItemEntity itemEntity) {
            owner = itemEntity.getOwner();
        }
        if (owner instanceof LivingEntity shooter) {
            boolean isMultishotCopy = entity instanceof Projectile pj
                && pj.getPersistentData().getBoolean(MULTISHOT_COPY_TAG);
            if (entity instanceof Projectile proj && !isMultishotCopy) {
                if (!proj.getPersistentData().getBoolean(MULTISHOT_DONE_TAG)) {
                    int multishot = getMultishotLevel(serverLevel, shooter);
                    if (multishot > 0) {
                        proj.getPersistentData().putBoolean(MULTISHOT_DONE_TAG, true);
                        // 实体发射的是烟花火箭：将其本体也随机化为随机效果/颜色/飞行时长
                        if (proj instanceof FireworkRocketEntity firework) {
                            randomizeFireworkRocket(serverLevel, firework);
                        }
                        spawnMultishotCopies(serverLevel, shooter, proj, multishot);
                    }
                }
            }
        }

        if (!isPhysicsDimension(serverLevel)) {
            if (event.getEntity() instanceof LivingEntity living && living.isNoGravity()) {
                living.setNoGravity(false);
                living.setDeltaMovement(Vec3.ZERO);
            }
            return;
        }

        // 物理维度反冲：分裂副本跳过，避免每支分裂箭都让射手额外受击退一次。
        if (owner instanceof LivingEntity shooter) {
            boolean isMultishotCopy = entity instanceof Projectile pj
                && pj.getPersistentData().getBoolean(MULTISHOT_COPY_TAG);

            if (isMultishotCopy) return;

            Vec3 projectileVelocity = entity.getDeltaMovement();
            if (projectileVelocity.lengthSqr() < 0.0001) return;

            double speed;
            if (entity instanceof ThrownTrident) {
                speed = 3.0 / 20.0;
            } else if (entity instanceof AbstractArrow) {
                speed = 1.4 / 20.0;
            } else if (entity instanceof ThrowableProjectile) {
                speed = 0.6 / 20.0;
            } else if (entity instanceof ItemEntity) {
                speed = 0.2 / 20.0;
            } else {
                speed = 0.2 / 20.0;
            }

            Vec3 reverseDir = projectileVelocity.normalize().reverse();
            Vec3 currentVel = PHYSICS_DELTA_MOVEMENTS.getOrDefault(shooter.getId(), shooter.getDeltaMovement());
            Vec3 kickVec = reverseDir.scale(speed);
            Vec3 newVel = currentVel.add(kickVec);
            PHYSICS_DELTA_MOVEMENTS.put(shooter.getId(), newVel);
            shooter.setDeltaMovement(newVel);

            if (shooter instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(serverPlayer));
            }
        }
    }

    private static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && isPhysicsDimension(level)) {
            PHYSICS_BLOCKS_PLACED_DIMS.add(level.dimension().location());
        }
    }

    private static final java.util.Map<java.util.UUID, Integer> PENDING_ENTRANCE = new java.util.HashMap<>();

    private static void handlePortalPlace(net.minecraft.world.entity.player.Player player,
                                          cn.autoforged.joes_addons_for_abmc.network.PortalPlacePayload payload) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        if (!isHoldingEndPortalStaff(player)) return;
        Vec3 pos = new Vec3(payload.x(), payload.y(), payload.z());
        PortalEntity portal = PortalEntity.create(sl, pos, payload.yaw(), payload.pitch());
        portal.setLinkedPortalId(-1);
        portal.lifespan = 400;
        if (payload.flip()) portal.setFlipped(true);
        portal.setYRot(payload.yaw());
        portal.setXRot(payload.pitch());
        sl.addFreshEntity(portal);
        alignPortalToSurface(player, sl, portal);

        if (!payload.placingExit()) {
            PENDING_ENTRANCE.put(player.getUUID(), portal.getId());
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "Entrance placed. Right-click again to place the exit."), true);
        } else {
            PORTAL_LIFESPANS.put(portal.getId(), 400);
            Integer entranceId = PENDING_ENTRANCE.remove(player.getUUID());
            if (entranceId != null) {
                Entity entranceEntity = sl.getEntity(entranceId);
                if (entranceEntity instanceof PortalEntity entrancePortal) {
                    entrancePortal.setLinkedPortalId(portal.getId());
                    portal.setLinkedPortalId(entrancePortal.getId());
                    PORTAL_PAIRS.put(entrancePortal.getId(), portal.getId());
                    PORTAL_PAIRS.put(portal.getId(), entrancePortal.getId());
                    PORTAL_LIFESPANS.put(entrancePortal.getId(), 400);
                    PLAYER_LATEST_PAIR.put(player.getUUID(), entrancePortal.getId());
                }
            }
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.END_PORTAL_SPAWN, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.5F);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "Portal pair created! Portals will close in 20 seconds."), true);
        }
    }

    private static void handlePortalStaffInput(Player player, PortalStaffInputPayload payload) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        UUID uuid = player.getUUID();

        if (payload.action() == PortalStaffInputPayload.ACTION_CANCEL) {
            // 取消激活机制中已放置但尚未成对的入口传送门
            Integer entranceId = PENDING_ENTRANCE.remove(uuid);
            if (entranceId != null) {
                Entity e = sl.getEntity(entranceId);
                if (e != null) e.discard();
                PORTAL_LIFESPANS.remove(entranceId);
                PORTAL_PAIRS.remove(entranceId);
            }
            // 取消旧机制状态
            PortalStaffState existing = PORTAL_STAFF_STATES.remove(uuid);
            if (existing != null) {
                if (existing.previewId > 0) {
                    Entity e = sl.getEntity(existing.previewId);
                    if (e != null) e.discard();
                }
                if (existing.pendingEntranceId > 0) {
                    Entity e = sl.getEntity(existing.pendingEntranceId);
                    if (e != null) e.discard();
                }
            }
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Portal creation cancelled."), true);
            return;
        }

        if (payload.action() == PortalStaffInputPayload.ACTION_COLLAPSE) {
            Integer latestId = PLAYER_LATEST_PAIR.get(uuid);
            if (latestId == null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("No portal pair to collapse."), true);
                return;
            }
            Integer pairedId = PORTAL_PAIRS.get(latestId);
            if (pairedId == null || findPortalEntity(sl.getServer(), latestId) == null
                || findPortalEntity(sl.getServer(), pairedId) == null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("Portal pair no longer exists."), true);
                return;
            }
            PortalCollapseState cs = new PortalCollapseState();
            cs.portalAId = latestId;
            cs.portalBId = pairedId;
            cs.speed = 0.1;
            cs.accel = 0.05;
            PORTAL_COLLAPSES.put(latestId, cs);
            player.playSound(net.minecraft.sounds.SoundEvents.ENDER_EYE_DEATH, 1.0F, 1.0F);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Portals collapsing!"), true);
            return;
        }

        PortalStaffState st = PORTAL_STAFF_STATES.computeIfAbsent(uuid, k -> new PortalStaffState());

        if (payload.action() == PortalStaffInputPayload.ACTION_START) {
            if (st.previewId > 0) return; // 已在放置中
            boolean placingExit = st.pendingEntranceId > 0;
            st.placingExit = placingExit;
            st.previewDistance = 2.0;
            Vec3 startPos = player.getEyePosition().add(player.getLookAngle().scale(2.0));
            PortalEntity preview = PortalEntity.create(sl, startPos, player.getYRot(), player.getXRot());
            preview.setLinkedPortalId(-1);
            preview.lifespan = 400;
            sl.addFreshEntity(preview);
            st.previewId = preview.getId();
            PORTAL_STAFF_STATES.put(uuid, st);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                placingExit
                    ? "Exit: scroll to set distance, left-click flips it, release to place."
                    : "Entrance: scroll to set distance, left-click flips it, release to place."), true);
            return;
        }

        if (payload.action() == PortalStaffInputPayload.ACTION_FLIP) {
            if (st.previewId <= 0) return;
            Entity pe = sl.getEntity(st.previewId);
            if (pe instanceof PortalEntity portal) {
                portal.setFlipped(!portal.isFlipped());
            }
            return;
        }

        // 滚轮：拉近/拉远预览传送门（可越过玩家拉到自己身后）
        if (payload.action() == PortalStaffInputPayload.ACTION_EXTEND
            || payload.action() == PortalStaffInputPayload.ACTION_RETRACT) {
            if (st.previewId <= 0) return;
            st.previewDistance = Math.min(256.0F, Math.max(-10.0F, st.previewDistance + payload.amount()));
            Entity pe = sl.getEntity(st.previewId);
            if (pe instanceof PortalEntity pPortal) {
                positionPreviewAt(player, sl, pPortal, st.previewDistance);
            }
            return;
        }

        if (payload.action() == PortalStaffInputPayload.ACTION_PLACE) {
            if (st.previewId <= 0) return;
            Entity previewEntity = sl.getEntity(st.previewId);
            if (!(previewEntity instanceof PortalEntity previewPortal)) return;

            if (st.placingExit) {
                int entranceId = st.pendingEntranceId;
                previewPortal.setLinkedPortalId(entranceId);
                if (entranceId > 0) {
                    Entity entranceEntity = sl.getEntity(entranceId);
                    if (entranceEntity instanceof PortalEntity entrancePortal) {
                        entrancePortal.setLinkedPortalId(previewPortal.getId());
                    }
                    PORTAL_PAIRS.put(entranceId, previewPortal.getId());
                    PORTAL_PAIRS.put(previewPortal.getId(), entranceId);
                    PORTAL_LIFESPANS.put(entranceId, 400);
                }
                PORTAL_LIFESPANS.put(previewPortal.getId(), 400);
                alignPortalToSurface(player, sl, previewPortal);

                ItemStack main = player.getMainHandItem();
                boolean mainIsStaff = main.getItem() instanceof StaffItem;
                ItemStack staffStack = mainIsStaff ? main : player.getOffhandItem();
                EquipmentSlot slot = mainIsStaff ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                hurtStaff(staffStack, 10, player, slot);

                sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.END_PORTAL_SPAWN, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.5F);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Portal pair created! Portals will close in 20 seconds."), true);
                PORTAL_STAFF_STATES.remove(uuid);
            } else {
                alignPortalToSurface(player, sl, previewPortal);
                st.pendingEntranceId = previewPortal.getId();
                st.previewId = -1;
                PORTAL_STAFF_STATES.put(uuid, st);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Entrance placed. Right-click again to place the exit."), true);
            }
        }
    }

    // 按住右键时，服务端让预览传送门随视线持续向外延伸
    private static void handlePortalPreviewTick(ServerTickEvent.Pre event) {
        java.util.Iterator<Map.Entry<UUID, PortalStaffState>> it = PORTAL_STAFF_STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PortalStaffState> en = it.next();
            PortalStaffState st = en.getValue();
            if (st.previewId <= 0) continue;
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(en.getKey());
            if (player == null) { st.previewId = -1; continue; }
            if (!(player.level() instanceof ServerLevel level)) continue;
            Entity pe = level.getEntity(st.previewId);
            if (!(pe instanceof PortalEntity portal) || portal.isRemoved()) { st.previewId = -1; continue; }
            if (!isHoldingEndPortalStaff(player)) {
                portal.discard();
                if (!st.placingExit && st.pendingEntranceId < 0) {
                    it.remove();
                } else {
                    st.previewId = -1;
                }
                continue;
            }
            // 预览位置/朝向只在“开始放置”和“滚轮调距”时显式设置，
            // 不随玩家移动或转头逐刻刷新，从根上消除放置过程的抖动
        }
    }

    // 按当前视线把预览传送门放到指定距离处，并同步给客户端（只在开始时与滚轮调距时调用）
    private static void positionPreviewAt(net.minecraft.world.entity.player.Player player,
                                          ServerLevel level, PortalEntity portal, double dist) {
        Vec3 pos = player.getEyePosition().add(player.getLookAngle().scale(dist));
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        portal.setPos(pos.x, pos.y, pos.z);
        portal.setYRot(yaw);
        portal.setXRot(pitch);
        portal.setPortalYaw(yaw);
        portal.setPortalPitch(pitch);
        portal.xo = pos.x;
        portal.yo = pos.y;
        portal.zo = pos.z;
        net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket sync =
            new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(portal);
        for (ServerPlayer p : level.getPlayers(q -> q.distanceToSqr(portal) < 256.0 * 256.0)) {
            p.connection.send(sync);
        }
    }

    // 放置时：让传送门垂直于其朝向最近方块表面（墙/地面/天花板）；仅在门被嵌进方块时才微移位置，避免跳动
    private static void alignPortalToSurface(net.minecraft.world.entity.player.Player player, ServerLevel level, PortalEntity portal) {
        Vec3 eye = player.getEyePosition();
        Vec3 target = portal.position();
        net.minecraft.world.phys.HitResult hr = level.clip(new net.minecraft.world.level.ClipContext(
            eye, target,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (hr instanceof net.minecraft.world.phys.BlockHitResult bhr
            && bhr.getType() != net.minecraft.world.phys.BlockHitResult.Type.MISS) {
            Vec3 hitLoc = bhr.getLocation();
            // 收紧容差：仅当表面紧贴目标（≤0.3格）时才贴合，避免把门往外拉远造成跳动
            if (eye.distanceTo(hitLoc) >= eye.distanceTo(target) - 0.3) {
                net.minecraft.core.Direction dir = bhr.getDirection();
                Vec3 surfNormal = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
                float syaw = (float) Math.toDegrees(Math.atan2(-surfNormal.x, surfNormal.z));
                float spitch = (float) Math.toDegrees(Math.asin(-surfNormal.y));
                // 朝向总是对齐到表面
                portal.setYRot(syaw);
                portal.setXRot(spitch);
                portal.setPortalYaw(syaw);
                portal.setPortalPitch(spitch);
                // 只有门本身被嵌进了实心方块才微移位置到表面；在开阔空气中不移动，保持原位
                net.minecraft.core.BlockPos center = net.minecraft.core.BlockPos.containing(target);
                if (!level.getBlockState(center).isAir()) {
                    Vec3 fitted = hitLoc.add(surfNormal.scale(0.05));
                    portal.setPos(fitted.x, fitted.y, fitted.z);
                    portal.xo = fitted.x;
                    portal.yo = fitted.y;
                    portal.zo = fitted.z;
                }
                portal.setFlipped(false);
                net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket sync =
                    new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(portal);
                for (ServerPlayer p : level.getPlayers(q -> q.distanceToSqr(portal) < 256.0 * 256.0)) {
                    p.connection.send(sync);
                }
            }
        }
    }

    private static boolean isHoldingEndPortalStaff(Player player) {
        ItemStack[] hands = { player.getMainHandItem(), player.getOffhandItem() };
        for (ItemStack stack : hands) {
            if (stack.getItem() instanceof StaffItem
                && "end_portal_frame".equalsIgnoreCase(stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"))) {
                return true;
            }
        }
        return false;
    }

    public static void executeObsidianStaffAbility(Player player, ItemStack stack, InteractionHand hand) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 center = eyePos.add(lookVec.scale(3.5));

        Vec3 axisA = lookVec.normalize();
        Vec3 axisB;
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 crossUp = up.cross(axisA);
        if (crossUp.lengthSqr() < 1.0E-6) {
            crossUp = new Vec3(1.0, 0.0, 0.0).cross(axisA);
        }
        axisB = crossUp.normalize();
        Vec3 axisC = axisA.cross(axisB).normalize();

        int r = 4;
        int minX = Mth.floor(center.x - r);
        int maxX = Mth.ceil(center.x + r);
        int minY = Mth.floor(center.y - r);
        int maxY = Mth.ceil(center.y + r);
        int minZ = Mth.floor(center.z - r);
        int maxZ = Mth.ceil(center.z + r);

        java.util.List<BlockMovement> movements = new java.util.ArrayList<>();
        net.minecraft.util.RandomSource random = player.getRandom();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = serverLevel.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (state.is(Blocks.NETHERITE_BLOCK)) continue;
                    if (isUnbreakableForNetherite(state)) continue;

                    Vec3 blockCenter = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    Vec3 diff = blockCenter.subtract(center);
                    double localX = diff.dot(axisA) / 4.0;
                    double localY = diff.dot(axisB) / 2.0;
                    double localZ = diff.dot(axisC) / 2.0;
                    double ellipsoidDist = localX * localX + localY * localY + localZ * localZ;

                    if (ellipsoidDist <= 1.0 && random.nextDouble() < 1.0) {
                        Vec3 posCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        double actualDist = posCenter.distanceTo(center);
                        double baseDisplacement = Math.max(0.0, 4.0 - actualDist);
                        double randomFactor = 0.9 + random.nextDouble() * 0.1;
                        double displacementAmount = baseDisplacement * randomFactor;

                        Vec3 displaceVec = lookVec.scale(displacementAmount);
                        BlockPos destPos = new BlockPos(
                            Mth.floor(pos.getX() + displaceVec.x + 0.5),
                            Mth.floor(pos.getY() + displaceVec.y + 0.5),
                            Mth.floor(pos.getZ() + displaceVec.z + 0.5)
                        );

                        BlockPos blockDiff = destPos.subtract(pos);
                        int steps = Math.max(Math.abs(blockDiff.getX()),
                            Math.max(Math.abs(blockDiff.getY()), Math.abs(blockDiff.getZ())));
                        if (steps == 0) continue;

                        BlockMovement mov = new BlockMovement();
                        mov.startPos = pos;
                        mov.currentPos = pos;
                        mov.finalDestPos = destPos;
                        mov.state = state;
                        mov.remainingSteps = steps;
                        if (state.hasBlockEntity()) {
                            BlockEntity be = serverLevel.getBlockEntity(pos);
                            if (be != null) {
                                mov.beNbt = be.saveWithFullMetadata(serverLevel.registryAccess());
                            }
                        }
                        movements.add(mov);
                    }
                }
            }
        }

        if (movements.isEmpty()) return;

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.METAL_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);

        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        int count = movements.size();
        hurtStaff(stack, count, player, slot);

        StaffMoveTask task = new StaffMoveTask();
        task.level = serverLevel;
        task.playerId = player.getUUID();
        task.slot = slot;
        task.tickDelay = 0;
        task.movements = movements;

        boolean allDone = processStaffMoveStep(task, player);

        if (!allDone) {
            task.tickDelay = 1;
            PENDING_STAFF_MOVES.add(task);
        }
    }

    // ============================ 冰块权杖：冻结并困住生物 ============================
    /** 冰块权杖的“冰冻生效距离”倍数：右击生物将其冻结时沿视线瞄准的最远距离
     *  = 玩家实体交互距离 × 该倍数（生存默认 ~3 格 ×3 ≈ 9 格，创造 ~5 格 ×3 ≈ 15 格）。
     *  这是权杖自身的瞄准射程，与“实体交互距离”相互独立——实体交互距离只决定能否触发
     *  实体交互/打开 GUI，不会随本倍数变化。 */
    private static final double ICE_STAFF_RANGE_MULTIPLIER = 3.0;

    /**
     * 冻结目标生物：替换其碰撞箱触及的方块为霜冰（已生成的霜冰不会被重复替换），
     * 并为其施加 5 秒挖掘疲劳I + 冻结；每次“有效”使用都会累计一次可结算伤害。
     */
    public static void executeIceStaffAbility(Player player, ItemStack stack, InteractionHand hand) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        LivingEntity target = raycastIceTarget(player, serverLevel);
        if (target == null) return;

        // 被困的生物获得 5 秒挖掘疲劳I + 冻结（原版细雪冻结机制）
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 0));
        target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze()));

        // 将生物碰撞箱触及的方块暂时替换为霜冰
        int placed = 0;
        AABB bb = target.getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(
            new BlockPos(Mth.floor(bb.minX), Mth.floor(bb.minY), Mth.floor(bb.minZ)),
            new BlockPos(Mth.floor(bb.maxX), Mth.floor(bb.maxY), Mth.floor(bb.maxZ)))) {
            if (!bb.intersects(AABB.unitCubeFromLowerCorner(new Vec3(pos.getX(), pos.getY(), pos.getZ())))) {
                continue;
            }
            if (replaceBlockWithIce(serverLevel, pos, target.getUUID())) {
                placed++;
            }
        }

        if (placed > 0) {
            registerFrostBlocks(target.getUUID(), placed);
            EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            hurtStaff(stack, 1, player, slot);
        }
    }

    /**
     * 将指定方块位置替换为冰块权杖的“霜冰”。
     * - 已被霜冰替代的方块不会再次被替换；不可破坏方块（如基岩）不替换。
     * - 非空气/可储存方块的方块被替换时不会有掉落物（此处从不调用 destroyBlock，不产生掉落）。
     * - 保存原方块状态与其方块实体数据（含容器内容），融化/破坏时还原。
     */
    private static boolean replaceBlockWithIce(ServerLevel level, BlockPos pos, UUID trappedUuid) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.JOB_FROSTED_ICE.get())) return false;
        if (state.getDestroySpeed(level, pos) < 0.0F) return false;
        CompoundTag beData = null;
        BlockEntity origBe = level.getBlockEntity(pos);
        if (origBe != null) {
            beData = origBe.saveWithFullMetadata(level.registryAccess());
        }
        level.setBlock(pos, ModBlocks.JOB_FROSTED_ICE.get().defaultBlockState(), Block.UPDATE_ALL);
        JobFrostedIceBlockEntity fbe = new JobFrostedIceBlockEntity(pos, level.getBlockState(pos));
        fbe.setOriginal(state, beData, trappedUuid);
        level.setBlockEntity(fbe);
        return true;
    }

    /**
     * 沿视线找出最近的可作为冰块权杖目标的生物。瞄准射程 = 实体交互距离 ×
     * ICE_STAFF_RANGE_MULTIPLIER（权杖自身的“冰冻生效距离”，与实体交互距离相互独立）。
     */
    private static LivingEntity raycastIceTarget(Player player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double range = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE)
            * ICE_STAFF_RANGE_MULTIPLIER;
        if (range <= 0.0) return null;
        AABB searchBox = player.getBoundingBox()
            .expandTowards(look.scale(range))
            .inflate(1.0);
        LivingEntity best = null;
        double bestDist = range * range;
        for (Entity e : level.getEntities(player, searchBox,
            e -> e instanceof LivingEntity && e != player && e.isAlive())) {
            AABB bb = e.getBoundingBox().inflate(0.15);
            java.util.Optional<Vec3> hit = bb.clip(eye, eye.add(look.scale(range)));
            if (hit.isPresent()) {
                double d = eye.distanceToSqr(hit.get());
                if (d < bestDist) {
                    bestDist = d;
                    best = (LivingEntity) e;
                }
            }
        }
        return best;
    }

    // ============================ 冰块权杖：霜冰计数与伤害结算 ============================
    private static final ConcurrentHashMap<UUID, FrostTracker> FROST_TRACKERS = new ConcurrentHashMap<>();

    // 记录本 tick 被霜冰冻住前的位置（按实体 id，非 UUID；用于把会飞的生物“钉”在原地）。
    private static final java.util.Map<Integer, Vec3> FROST_PRE_POSITIONS = new HashMap<>();

    // 记录被冻住的生物进入霜冰时的身体朝向 yaw（按实体 id）；用于让身体无法转动而头部仍可扭动。
    private static final java.util.Map<Integer, Float> FROST_BODY_YAW = new HashMap<>();

    /** 记录某个生物被困期间的霜冰状态。 */
    private static final class FrostTracker {
        int n;         // 本次冻结周期的霜冰基准数（开启周期后一直保持不变）
        int remaining; // 当前仍存在的霜冰总数（融化/破坏各 -1，再次使用 +）
        int destroyed; // 本次周期内被“非融化”挖掉的霜冰累计数
        int uses;      // 剩余可结算次数（累计的有效权杖使用次数）
    }

    /**
     * 权杖冻结生物后登记本次生成的霜冰块数。
     * - 所在周期的冰块已完全化光/被清掉后再冻结：开启新周期，基准数 n 取本次生成数。
     * - 仍有冰块残留时再次使用权杖：仅补充冰块量并增加一次结算名额；基准数 n 保持不变，
     *   已挖掉的计数 destroyed 继续朝同一阈值累加（不因本次使用而重置）。
     */
    private static void registerFrostBlocks(UUID uuid, int placed) {
        if (placed <= 0) return;
        FrostTracker t = FROST_TRACKERS.computeIfAbsent(uuid, k -> new FrostTracker());
        if (t.remaining <= 0) {
            t.n = placed;
            t.remaining = placed;
            t.destroyed = 0;
            t.uses++;
        } else {
            t.remaining += placed;
            t.uses++;
        }
    }

    /**
     * 由霜冰方块在融化/破坏时回调：更新该生物的计数。
     * 破坏的霜冰数一旦超过 n/2 且仍有余量结算次数，则结算一次伤害（结算次数上限=有效使用次数）。
     */
    public static void onFrostIceReverted(ServerLevel level, UUID uuid, boolean isMelt) {
        FrostTracker t = FROST_TRACKERS.get(uuid);
        if (t == null) return;
        t.remaining = Math.max(0, t.remaining - 1);
        // 挖掉的冰块数（destroyed）累计到“超过基准数一半”才结算一次伤害：
        // 只挖一块不会立刻触发，需挖掉半数以上才模拟“冰层被彻底打破”。
        if (!isMelt) {
            t.destroyed++;
            if (t.uses > 0 && t.n > 0 && t.destroyed > Math.floor((double) t.n / 2.0)) {
                // 本次破坏本应结算一次伤害；但若被困生物已脱离霜冰（如驯服宠物因离玩家过远被
                // 瞬移拉回主人身边），则不再结算伤害——冰都困不住它了，挖冰自然不该再伤它。
                // 不结算时同样作废本次触发机会并清空破坏计数，避免记录残留/重复触发。
                if (isEntityInsideFrost(level, uuid)) {
                    fireFrostShatter(level, uuid);
                }
                t.uses--;
                t.destroyed = 0;
            }
        }
        if (t.remaining <= 0 && t.uses <= 0) {
            FROST_TRACKERS.remove(uuid);
        }
    }

    /** 生物是否仍与霜冰有重合（且存活）：用于判断破坏霜冰时是否还有资格结算伤害。 */
    private static boolean isEntityInsideFrost(ServerLevel level, UUID uuid) {
        Entity entity = level.getEntity(uuid);
        return entity instanceof LivingEntity living && !living.isDeadOrDying() && isOverlappingFrost(living);
    }

    /**
     * 霜冰被非融化因素破坏超半时，对被困生物进行生命值结算：
     * 当前生命 > 15 点则扣除当前生命一半（可被护甲/抗性减免），否则直接斩杀。
     */
    private static void fireFrostShatter(ServerLevel level, UUID uuid) {
        Entity entity = level.getEntity(uuid);
        if (!(entity instanceof LivingEntity living) || living.isDeadOrDying()) return;
        float hp = living.getHealth();
        float dmg = hp > 15.0F ? hp * 0.5F : Float.MAX_VALUE;
        DamageSource source;
        try {
            source = level.damageSources().source(ModDamageTypes.FROST_ICE_SHATTER.getKey());
        } catch (Exception e) {
            // 自定义伤害类型异常时兜底，绝不让伤害结算被静默丢弃。
            source = level.damageSources().magic();
        }
        living.hurt(source, dmg);
    }

    // ============================ 冰块权杖：霜冰重叠时冻结移动/跳跃 ============================
    private static final ResourceLocation FROST_JUMP_MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath(MODID, "frost_jump_frozen");
    private static final ResourceLocation FROST_SPEED_MODIFIER_ID =
        ResourceLocation.fromNamespaceAndPath(MODID, "frost_speed_frozen");
    private static final AttributeModifier FROST_JUMP_ZERO =
        new AttributeModifier(FROST_JUMP_MODIFIER_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final AttributeModifier FROST_SPEED_ZERO =
        new AttributeModifier(FROST_SPEED_MODIFIER_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    /** 生物碰撞箱是否与霜冰有重合。冰块权杖持有者免疫（霜冰均为权杖制造，持有者不应被自己的冰块困住）。 */
    public static boolean isOverlappingFrost(LivingEntity living) {
        if (isHoldingIceStaff(living)) return false;
        AABB bb = living.getBoundingBox();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(bb.minX); x <= Mth.floor(bb.maxX); x++) {
            for (int y = Mth.floor(bb.minY); y <= Mth.floor(bb.maxY); y++) {
                for (int z = Mth.floor(bb.minZ); z <= Mth.floor(bb.maxZ); z++) {
                    if (living.level().getBlockState(cursor.set(x, y, z)).is(ModBlocks.JOB_FROSTED_ICE.get())
                        && bb.intersects(AABB.unitCubeFromLowerCorner(new Vec3(x, y, z)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 蜘蛛网权杖：持有者在蜘蛛网中不会被减速。
     * 原版 CobwebBlock.entityInside 会调用 makeStuckInBlock 设置 stuckSpeedMultiplier，
     * 在下一刻移动时把位移乘以 (0.25,0.05,0.25) 造成减速。这里在刻后把它清空，
     * 使持有权杖者在蜘蛛网中移动不受影响（服务端与客户端都调用，保持一致）。
     */
    public static void clearStuckInCobweb(net.minecraft.world.entity.Entity entity) {
        if (entity == null || !isInsideBlock(entity, net.minecraft.world.level.block.Blocks.COBWEB)) return;
        entity.makeStuckInBlock(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Vec3.ZERO);
    }

    /** 实体碰撞箱是否与指定方块有重合。 */
    private static boolean isInsideBlock(net.minecraft.world.entity.Entity entity, net.minecraft.world.level.block.Block block) {
        AABB bb = entity.getBoundingBox();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(bb.minX); x <= Mth.floor(bb.maxX); x++) {
            for (int y = Mth.floor(bb.minY); y <= Mth.floor(bb.maxY); y++) {
                for (int z = Mth.floor(bb.minZ); z <= Mth.floor(bb.maxZ); z++) {
                    if (entity.level().getBlockState(cursor.set(x, y, z)).is(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 生物碰撞箱与霜冰有重合时，把其 Jump Strength 与 movement_speed 属性置为 0，并每 tick 抹平其竖直
     * 速度——由于大多数原版生物没有 JUMP_STRENGTH 属性，仅改属性无法真正禁止跳跃，因此竖直速度归零即可
     * 保证任何生物都无法靠跳跃逃脱（也被困住无法移动）；不重叠时全部恢复默认。
     */
    private static void updateFrostedConstraints(LivingEntity living) {
        boolean inFrost = isOverlappingFrost(living);
        if (inFrost) {
            // 清空整个速度向量：即使是会飞的生物（空中/飞行AI）也完全无法移动。
            living.setDeltaMovement(Vec3.ZERO);
            // 把位置钉回本 tick 起始处：飞行/滞空类生物每 tick 都会重新施加速度并即时移动，
            // 仅靠速度归零或改属性挡不住它们；用“位置还原”确保连飞行的生物也被牢牢冻在冰块里。
            Vec3 pre = FROST_PRE_POSITIONS.get(living.getId());
            if (pre != null) {
                living.setPos(pre.x, pre.y, pre.z);
            }
            // 冻结身体朝向：渲染时身体基准转向取自 yRot（EntityRenderer 底座旋转），
            // 模型 yaw 取自 yBodyRot，需把 yRot/yBodyRot 一并钉死身体才不会转动；
            // 头部 yHeadRot 不受影响，仍可扭头。
            int eid = living.getId();
            float bodyYaw = FROST_BODY_YAW.computeIfAbsent(eid, k -> living.getYRot());
            living.yRotO = bodyYaw;
            living.setYRot(bodyYaw);
            living.yBodyRotO = bodyYaw;
            living.yBodyRot = bodyYaw;
        } else {
            FROST_BODY_YAW.remove(living.getId());
        }
        applyFrostedAttribute(living, Attributes.JUMP_STRENGTH, FROST_JUMP_MODIFIER_ID, FROST_JUMP_ZERO, inFrost);
        applyFrostedAttribute(living, Attributes.MOVEMENT_SPEED, FROST_SPEED_MODIFIER_ID, FROST_SPEED_ZERO, inFrost);
    }

    /**
     * 客户端侧：渲染用的是“客户端实体”这一独立副本，它每 tick 会自行根据头部转向（yHeadRot）重算身体朝向
     * （yRot/yBodyRot），服务端的那份值无法传回渲染端。因此这里直接对客户端实体钉住身体 yaw——
     * 头部 yHeadRot 不触碰，仍可扭头。
     */
    public static void freezeClientBodyRotation(LivingEntity living) {
        float bodyYaw = FROST_BODY_YAW.computeIfAbsent(living.getId(), k -> living.getYRot());
        living.yRotO = bodyYaw;
        living.setYRot(bodyYaw);
        living.yBodyRotO = bodyYaw;
        living.yBodyRot = bodyYaw;
    }

    /** 客户端侧：实体已脱离霜冰时，清理其身体朝向覆盖。 */
    public static void clearClientFrostRotation(int entityId) {
        FROST_BODY_YAW.remove(entityId);
    }

    private static void applyFrostedAttribute(LivingEntity living, Holder<Attribute> attribute, ResourceLocation id, AttributeModifier zero, boolean inFrost) {
        AttributeInstance attr = living.getAttribute(attribute);
        if (attr == null) return;
        if (inFrost) {
            if (attr.getModifier(id) == null) {
                attr.addTransientModifier(zero);
            }
        } else if (attr.getModifier(id) != null) {
            attr.removeModifier(id);
        }
    }

    public static void executeBedrockStaffAbility(Player player, ItemStack stack, InteractionHand hand) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();

        BlockHitResult hitResult = serverLevel.clip(
            new ClipContext(eyePos, eyePos.add(lookVec.scale(7.0)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hitResult.getType() != HitResult.Type.BLOCK) return;
        if (eyePos.distanceTo(hitResult.getLocation()) > 7.0) return;

        Vec3 center = eyePos.add(lookVec.scale(3.5));

        Vec3 axisA = lookVec.normalize();
        Vec3 axisB;
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 crossUp = up.cross(axisA);
        if (crossUp.lengthSqr() < 1.0E-6) {
            crossUp = new Vec3(1.0, 0.0, 0.0).cross(axisA);
        }
        axisB = crossUp.normalize();
        Vec3 axisC = axisA.cross(axisB).normalize();

        int longRadius = 20;
        int shortRadius = 3;
        int hemisphereRadius = 3;
        int r = Math.max(longRadius, hemisphereRadius);
        int minX = Mth.floor(center.x - r);
        int maxX = Mth.ceil(center.x + r);
        int minY = Mth.floor(center.y - r);
        int maxY = Mth.ceil(center.y + r);
        int minZ = Mth.floor(center.z - r);
        int maxZ = Mth.ceil(center.z + r);

        java.util.List<BlockPos> selectedBlocks = new java.util.ArrayList<>();
        java.util.List<BlockState> selectedStates = new java.util.ArrayList<>();
        net.minecraft.util.RandomSource random = player.getRandom();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = serverLevel.getBlockState(pos);
                    if (state.isAir()) continue;

                    Vec3 blockCenter = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    Vec3 diff = blockCenter.subtract(center);
                    double projA = diff.dot(axisA);
                    double projB = diff.dot(axisB);
                    double projC = diff.dot(axisC);

                    boolean inShape;
                    if (projA <= 0.0) {
                        double hSq = (double) hemisphereRadius * hemisphereRadius;
                        inShape = (projA * projA + projB * projB + projC * projC) <= hSq;
                    } else {
                        double ellipDist = (projA * projA) / (double) (longRadius * longRadius)
                            + (projB * projB) / (double) (shortRadius * shortRadius)
                            + (projC * projC) / (double) (shortRadius * shortRadius);
                        inShape = ellipDist <= 1.0;
                    }

                    if (inShape && random.nextDouble() < 1.0) {
                        selectedBlocks.add(pos.immutable());
                        selectedStates.add(state);
                    }
                }
            }
        }

        if (selectedBlocks.isEmpty()) return;

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);

        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;

        Vec3 normalizedLook = lookVec.normalize();
        long creationTime = serverLevel.getGameTime();

        for (int i = 0; i < selectedBlocks.size(); i++) {
            BlockPos pos = selectedBlocks.get(i);
            BlockState state = selectedStates.get(i);

            BedrockFallingBlockEntity entity = new BedrockFallingBlockEntity(
                ModEntities.BEDROCK_FALLING_BLOCK.get(), serverLevel);
            entity.initFromBlock(serverLevel, pos, state);
            entity.moveDirection = normalizedLook;
            entity.speed = 1.5;
            entity.creationGameTime = creationTime;
            entity.setDeltaMovement(normalizedLook.scale(1.5));

            serverLevel.addFreshEntity(entity);
        }

        hurtStaff(stack, selectedBlocks.size(), player, slot);
    }

    /**
     * 屏障权杖：右键放置屏障（相当于无限堆叠的屏障方块）。
     * 对准方块时，在所点击面的相邻格放置屏障；未对准任何方块/实体时，隔 5 格在空中放置。
     * 特例：若放置后的屏障方块碰撞箱会与任何玩家相交（会困住玩家），则不放置。
     */
    public static void executeBarrierStaffPlace(Player player, ItemStack stack, InteractionHand hand) {
        // 左右键同时按下触发整体平移时，抑制普通放置
        if (barrierShiftSuppress.containsKey(player.getUUID())) return;
        executeStaffBlockPlace(player, stack, hand, Blocks.BARRIER);
    }

    /**
     * 通用权杖方块放置：右键方块在相邻格放置目标方块，右键空气则隔 5 格在空中放置。
     * 放置目标方块的碰撞箱若会与任何玩家相交，则放弃放置（用于屏障权杖防卡人）。
     */
    private static void executeStaffBlockPlace(Player player, ItemStack stack, InteractionHand hand, Block block) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();

        BlockHitResult hitResult = serverLevel.clip(
            new ClipContext(eyePos, eyePos.add(lookVec.scale(7.0)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        BlockPos placePos;
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            placePos = hitResult.getBlockPos().offset(hitResult.getDirection().getNormal());
        } else {
            placePos = BlockPos.containing(eyePos.add(lookVec.scale(5.0)));
        }

        BlockState current = serverLevel.getBlockState(placePos);
        if (!current.canBeReplaced()) return;

        // 若放置后目标方块的碰撞箱会与任何玩家相交，则不放置（屏障权杖防困住玩家）
        AABB blockBox = new AABB(placePos);
        for (Player p : serverLevel.players()) {
            if (p.getBoundingBox().intersects(blockBox)) {
                return;
            }
        }

        serverLevel.setBlockAndUpdate(placePos, block.defaultBlockState());
        serverLevel.playSound(null, placePos.getX() + 0.5, placePos.getY() + 0.5, placePos.getZ() + 0.5,
            block.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.PLAYERS, 1.0F, 1.0F);

        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        hurtStaff(stack, 1, player, slot);
    }

    /**
     * TNT 权杖：右键丢出一枚点燃的特制 TNT（引信 9999），命中方块随即爆炸；
     * 有 1% 概率改为丢出一只点燃的特制苦力怕（爆炸半径 2~6、Powered 随机）。
     * 每次丢出消耗 1 点权杖/方块耐久。丢出的爆炸物不伤害投掷者本人。
     */
    public static void executeTntStaffPlace(Player player, ItemStack stack, InteractionHand hand) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        RandomSource random = serverLevel.getRandom();

        Vec3 eyePos = player.getEyePosition();
        // 弹道与雪球一致：使用原版 Projectile.shootFromRotation(player, pitch, yaw, 0, 1.5, 1.0)
        Vec3 launchVel = computeSnowballLaunchVelocity(player, random);

        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;

        // 丢出特制苦力怕（1% 概率）
        if (random.nextDouble() < 0.01) {
            TntStaffCreeper creeper = new TntStaffCreeper(ModEntities.TNT_STAFF_CREEPER.get(), serverLevel);
            creeper.setPos(eyePos.x, eyePos.y, eyePos.z);
            creeper.setDeltaMovement(launchVel);
            creeper.setExplosionRadius(random.nextIntBetweenInclusive(2, 6));
            creeper.setPoweredState(random.nextBoolean());
            creeper.setOwnerUuid(player.getUUID());
            creeper.ignite();
            serverLevel.addFreshEntity(creeper);
            serverLevel.playSound(null, creeper.getX(), creeper.getY(), creeper.getZ(),
                SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.0F, 0.9F + random.nextFloat() * 0.2F);
        } else {
            TntStaffPrimedTnt tnt = new TntStaffPrimedTnt(serverLevel, eyePos.x, eyePos.y, eyePos.z, player);
            tnt.setDeltaMovement(launchVel);
            serverLevel.addFreshEntity(tnt);
            serverLevel.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(),
                SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 0.9F + random.nextFloat() * 0.2F);
            // 多重射击：额外分裂出 2×level 个 TNT（只分裂 TNT，不会分裂出额外苦力怕）
            int multi = getMultishotLevel(serverLevel, player);
            if (multi > 0) {
                int extras = 2 * multi;
                for (int i = 0; i < extras; i++) {
                    double tx = (extras == 1) ? 0.0 : (i / (double) (extras - 1) - 0.5) * 2.0;
                    float ang = (float) (tx * MULTISHOT_HALF_SPREAD);
                    TntStaffPrimedTnt copy = new TntStaffPrimedTnt(serverLevel, eyePos.x, eyePos.y, eyePos.z, player);
                    copy.setDeltaMovement(rotateScaledVelocity(launchVel, ang));
                    copy.hasImpulse = true;
                    serverLevel.addFreshEntity(copy);
                }
            }
        }

        hurtStaff(stack, 1, player, slot);
    }

    /**
     * 弹道与雪球一致：复刻原版 Projectile.shootFromRotation(player, pitch, yaw, 0.0F, 1.5F, 1.0F) 的
     * 初速度计算（方向 + 微小随机散布 + 1.5 倍速 + 投掷者自身移动带来的附加速度）。
     * 由于 TNT/苦力怕不是 Projectile 子类，这里把等价的最终速度向量算出来返回。
     */
    private static Vec3 computeSnowballLaunchVelocity(Player player, RandomSource random) {
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        float f = -Mth.sin(yaw * (float) (Math.PI / 180.0)) * Mth.cos(pitch * (float) (Math.PI / 180.0));
        float f1 = -Mth.sin(pitch * (float) (Math.PI / 180.0));
        float f2 = Mth.cos(yaw * (float) (Math.PI / 180.0)) * Mth.cos(pitch * (float) (Math.PI / 180.0));
        Vec3 vec3 = new Vec3(f, f1, f2)
            .normalize()
            .add(
                random.triangle(0.0, 0.0172275),
                random.triangle(0.0, 0.0172275),
                random.triangle(0.0, 0.0172275)
            )
            .scale(1.5);
        Vec3 movement = player.getKnownMovement();
        return vec3.add(movement.x, player.onGround() ? 0.0 : movement.y, movement.z);
    }

    /** TNT 权杖：投掷者左键按下，立即引爆其丢出的所有特制 TNT/苦力怕（fuse 设为 1）。 */
    public static void handleTntStaffDetonate(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        AABB box = new AABB(
            player.getX() - 128, player.getY() - 128, player.getZ() - 128,
            player.getX() + 128, player.getY() + 128, player.getZ() + 128);
        UUID pid = player.getUUID();
        for (TntStaffPrimedTnt tnt : serverLevel.getEntitiesOfClass(TntStaffPrimedTnt.class, box)) {
            // getOwner() 在存档重载后为 null（owner 不作为原版数据持久化），故用 UUID 判定归属
            if (tnt.getOwner() == player || (tnt.getOwnerUuid() != null && tnt.getOwnerUuid().equals(pid))) {
                tnt.quickFuse();
            }
        }
        for (TntStaffCreeper creeper : serverLevel.getEntitiesOfClass(TntStaffCreeper.class, box)) {
            if (pid.equals(creeper.getOwnerUuid())) creeper.quickFuse();
        }
    }

    // ===== 刷怪笼权杖 =====

    /** 刷怪笼权杖排除的 Boss：末影龙、凋灵、远古守卫者、监守者（按需求视作 boss）；
     *  另排除本模组 TNT 权杖召唤的苦力怕，避免它出现在刷怪笼权杖的被召唤列表中。 */
    private static final java.util.Set<String> SPAWNER_EXCLUDED_TYPES = java.util.Set.of(
        "minecraft:ender_dragon", "minecraft:wither",
        "minecraft:elder_guardian", "minecraft:warden",
        "joes_addons_for_abmc:tnt_staff_creeper");

    /** 不作为“移植头宿主”（身体）的类型：这些生物召唤出来时保持正常形态，不赋予移植头。
     *  包括：两种鱿鱼（用户指定）以及没有真实头部模型、客户端只能回退为原形态的
     *  鱼/史莱姆/岩浆怪/恶魂/烈焰人。若在这里赋予移植头，它们看起来正常但音效会被随机化。 */
    private static final java.util.Set<String> TRANSPLANT_TARGET_EXCLUDED_TYPES = java.util.Set.of(
        "minecraft:squid", "minecraft:glow_squid",
        "minecraft:slime", "minecraft:magma_cube",
        "minecraft:ghast", "minecraft:blaze",
        "minecraft:cod", "minecraft:salmon",
        "minecraft:pufferfish", "minecraft:tropical_fish");

    /** 该生物类型是否被排除作为“移植头宿主”（身体）。 */
    private static boolean isTransplantTargetExcluded(net.minecraft.world.entity.EntityType<?> type) {
        net.minecraft.resources.ResourceLocation rl =
            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return rl != null && TRANSPLANT_TARGET_EXCLUDED_TYPES.contains(rl.toString());
    }

    /**
     * 移植头的“头来源”排除类型：这些生物没有独立的标准头部模型，克隆“头”要么整体成块（史莱姆/
     * 岩浆怪/恶魂/烈焰人/鱿鱼/发光鱿鱼等），要么太扁或成其它形态，作为“头”渲染很怪异。
     * 头来源不受 boss 限制，故此处不排除末影龙/凋灵等 boss。
     */
    private static final java.util.Set<String> HEAD_SOURCE_EXCLUDED_TYPES = java.util.Set.of(
        "minecraft:slime", "minecraft:magma_cube",
        "minecraft:ghast", "minecraft:blaze",
        "minecraft:squid", "minecraft:glow_squid");

    /** 可召唤的随机非 boss 生物类型池（懒加载缓存，运行时实体类固定，无需重复遍历构建）。 */
    private static volatile java.util.List<EntityType<?>> SPAWNABLE_MOB_POOL;

    /** 移植头的 NBT 存储键：值为头来源生物的实体类型资源键（如 "minecraft:zombie"），空串表示无移植头。
     *  头来源类型不受 boss 限制（可含有末影龙/凋灵等），但与身体无关。
     *  因 1.21.1 的实体同步数据走 Builder 模式、无法给随机原版生物后补，故用 NBT 持久 +
     *  服务端广播 TransplantedHeadPayload 到客户端（客户端以实体 id 记录）。 */
    private static final String TRANSPLANTED_HEAD_TAG = "joes_transplanted_head";

    /** 在目标生物身上写入“移植头”类型。注意：只写 NBT，不发包；发包需在实体加入世界后再调用
     *  {@link #broadcastTransplantedHead}，否则 sendToPlayersTrackingEntityAndSelf 因实体尚未被追踪而发不出去。 */
    public static void setTransplantedHead(net.minecraft.world.entity.Mob target, String headTypeId) {
        if (headTypeId == null || headTypeId.isEmpty()) return;
        target.getPersistentData().putString(TRANSPLANTED_HEAD_TAG, headTypeId);
    }

    /** 把实体的“移植头”广播给追踪它的所有玩家以及召唤者本人。必须在 {@code addFreshEntity} 之后再调用，
     *  否则实体未被追踪、包会被丢弃，客户端收不到头类型导致渲染成正常形态。 */
    public static void broadcastTransplantedHead(net.minecraft.world.entity.Entity target) {
        if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) return;
        String head = getTransplantedHead(living);
        if (head.isEmpty()) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(
            target, new cn.autoforged.joes_addons_for_abmc.network.TransplantedHeadPayload(target.getId(), head));
    }

    /** 读取目标生物的“移植头”类型；无则返回空串（服务端读取，通常用于重同步）。 */
    public static String getTransplantedHead(net.minecraft.world.entity.LivingEntity target) {
        try {
            return target.getPersistentData().getString(TRANSPLANTED_HEAD_TAG);
        } catch (Exception e) {
            return "";
        }
    }

    /** 自体附魔（空手生物）的 NBT 存储键：值为 boolean，true=已被自体附魔。 */
    private static final String ENCHANT_SELF_TAG = "joes_enchant_self";

    /** 把“可见”的附魔状态效果以不可见版本重注入时，防止 MobEffectEvent.Added 递归重入的守卫。 */
    private static boolean reinjectingEnchantEffect = false;

    /** 读取目标生物是否已“自体附魔”（服务端）。 */
    public static boolean getEnchantSelf(net.minecraft.world.entity.LivingEntity target) {
        try {
            return target.getPersistentData().getBoolean(ENCHANT_SELF_TAG);
        } catch (Exception e) {
            return false;
        }
    }

    /** 写入“自体附魔”标记；只写 NBT，发包需调用 {@link #broadcastEnchantSelf}。 */
    public static void setEnchantSelf(net.minecraft.world.entity.LivingEntity target, boolean enchanted) {
        target.getPersistentData().putBoolean(ENCHANT_SELF_TAG, enchanted);
    }

    /** 把“自体附魔”状态广播给追踪该实体的所有玩家及祝福者本人，使附魔光效持续显示。 */
    public static void broadcastEnchantSelf(net.minecraft.world.entity.Entity target) {
        if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) return;
        boolean en = getEnchantSelf(living);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(
            target, new cn.autoforged.joes_addons_for_abmc.network.EnchantSelfPayload(target.getId(), en));
    }

    /** 移植脚的 NBT 存储键：值为脚来源生物的实体类型资源键，空串表示无移植脚。逻辑与移植头一致。 */
    private static final String TRANSPLANTED_FEET_TAG = "joes_transplanted_feet";

    /** 在目标生物身上写入“移植脚”类型。只写 NBT，不发包；发包需在实体加入世界后再广播。 */
    private static void setTransplantedFeet(net.minecraft.world.entity.Mob target, String feetTypeId) {
        if (feetTypeId == null || feetTypeId.isEmpty()) return;
        target.getPersistentData().putString(TRANSPLANTED_FEET_TAG, feetTypeId);
    }

    /** 读取目标生物的“移植脚”类型；无则返回空串。 */
    private static String getTransplantedFeet(net.minecraft.world.entity.LivingEntity target) {
        try {
            return target.getPersistentData().getString(TRANSPLANTED_FEET_TAG);
        } catch (Exception e) {
            return "";
        }
    }

    /** 把实体的“移植脚”广播给追踪它的所有玩家以及召唤者本人。必须在 addFreshEntity 之后调用。 */
    private static void broadcastTransplantedFeet(net.minecraft.world.entity.Entity target) {
        if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) return;
        String feet = getTransplantedFeet(living);
        if (feet.isEmpty()) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(
            target, new cn.autoforged.joes_addons_for_abmc.network.TransplantedFeetPayload(target.getId(), feet));
    }

    /** 实体被玩家追踪时，若有移植头/移植脚则重发对应 payload（覆盖登录/重建视距后丢失的客户端状态）。 */
    private static void onStartTracking(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) {
        net.minecraft.world.entity.Entity target = event.getTarget();
        if (target instanceof net.minecraft.world.entity.Mob mob
            && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            String head = getTransplantedHead(mob);
            if (!head.isEmpty()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    player,
                    new cn.autoforged.joes_addons_for_abmc.network.TransplantedHeadPayload(mob.getId(), head));
            }
            String feet = getTransplantedFeet(mob);
            if (!feet.isEmpty()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    player,
                    new cn.autoforged.joes_addons_for_abmc.network.TransplantedFeetPayload(mob.getId(), feet));
            }
            if (getEnchantSelf(mob)) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    player,
                    new cn.autoforged.joes_addons_for_abmc.network.EnchantSelfPayload(mob.getId(), true));
            }
        }
    }

    /** 可作“头”来源的生物类型池：不收 boss 限制（即含末影龙/凋灵等对象），懒加载缓存。 */
    private static volatile java.util.List<EntityType<?>> RANDOM_HEAD_POOL;

    /** 随机挑一个“头”来源生物类型并返回其资源键；头无 boss 限制。 */
    private static String getRandomHeadTypeId(RandomSource random) {
        java.util.List<EntityType<?>> pool = RANDOM_HEAD_POOL;
        if (pool == null) {
            pool = new java.util.ArrayList<>();
            try {
                for (EntityType<?> type : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
                    try {
                        net.minecraft.resources.ResourceLocation rl =
                            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
                        // 排除无独立标准头的类型（史莱姆/恶魂/鱿鱼等），它们作为“头”渲染怪异。
                        if (rl != null && HEAD_SOURCE_EXCLUDED_TYPES.contains(rl.toString())) continue;
                        Class<? extends Entity> base = type.getBaseClass();
                        boolean isMobClass = base != null
                            && net.minecraft.world.entity.Mob.class.isAssignableFrom(base);
                        if (!isMobClass && type.getCategory() == net.minecraft.world.entity.MobCategory.MISC) continue;
                        pool.add(type);
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
            if (pool.isEmpty()) pool.add(net.minecraft.world.entity.EntityType.ZOMBIE);
            RANDOM_HEAD_POOL = pool;
        }
        net.minecraft.resources.ResourceLocation rl =
            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(pool.get(random.nextInt(pool.size())));
        return rl != null ? rl.toString() : "minecraft:zombie";
    }

    private static java.util.List<EntityType<?>> getSpawnableMobPool() {
        java.util.List<EntityType<?>> cached = SPAWNABLE_MOB_POOL;
        if (cached != null) return cached;
        java.util.List<EntityType<?>> pool = new java.util.ArrayList<>();
        try {
            for (EntityType<?> type : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
                try {
                    // 不以 canSummon()/getBaseClass() 为硬性条件：getBaseClass() 在 NeoForge 中对多数类型
                    // 返回通用基类（如 Entity），若当作 Mob 判定会把所有真实生物都筛掉（只剩兜底僵尸）。
                    // 改用“Mob 基类 OR 非 MISC 类别”双保险，既保留全部常规生物，又排除弹射物/物品/载具等非生物。
                    net.minecraft.resources.ResourceLocation rl =
                        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
                    String id = rl != null ? rl.toString() : "";
                    if (SPAWNER_EXCLUDED_TYPES.contains(id)) continue;
                    Class<? extends Entity> base = type.getBaseClass();
                    boolean isMobClass = base != null
                        && net.minecraft.world.entity.Mob.class.isAssignableFrom(base);
                    if (!isMobClass && type.getCategory() == net.minecraft.world.entity.MobCategory.MISC) continue;
                    if (bossClassHasBossEvent(base)) continue;
                    pool.add(type);
                } catch (Exception ignored) {
                    // 跳过无法判定的类型，不中断整个池的构建
                }
            }
        } catch (Exception ignored) {
            // 遍历注册表若整体失败，也不抛到调用方。
        }
        if (pool.isEmpty()) {
            pool.add(net.minecraft.world.entity.EntityType.ZOMBIE); // 兜底，保证必有可召唤生物
        }
        SPAWNABLE_MOB_POOL = pool;
        return pool;
    }

    /** 反射判定实体类是否持有 ServerBossEvent 字段：绝大多数带 boss 血条的 modded 生物都持有它，
     *  借此排除“有 boss 血条”的自定义生物（除按 id 排除的原版四只外）。 */
    private static boolean bossClassHasBossEvent(Class<?> c) {
        try {
            while (c != null && c != Object.class) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (net.minecraft.server.level.ServerBossEvent.class.isAssignableFrom(f.getType())) {
                        return true;
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** 刷怪笼权杖：右键召唤一只随机非 boss 生物。对准方块时在点击面的相邻格召唤，
     *  否则在视角前方 4 格召唤。召唤体 scale 在 0.3~3.0 之间随机，并按该生物最大生命值扣除耐久。 */
    public static void executeSpawnerStaffAbility(Player player, ItemStack stack, InteractionHand hand) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        RandomSource random = serverLevel.getRandom();

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();

        // 召唤位置：对准方块时在相邻格中心，否则在视角前方 4 格
        Vec3 spawnPos;
        BlockHitResult hit = serverLevel.clip(
            new ClipContext(eyePos, eyePos.add(lookVec.scale(128.0)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos adjacent = hit.getBlockPos().offset(hit.getDirection().getNormal());
            spawnPos = new Vec3(adjacent.getX() + 0.5, adjacent.getY() + 0.5, adjacent.getZ() + 0.5);
        } else {
            spawnPos = eyePos.add(lookVec.scale(4.0));
        }

        java.util.List<EntityType<?>> pool = getSpawnableMobPool();
        if (pool.isEmpty()) return;

        // 随机挑选并尝试召唤；若某个类型 create/spawn 时抛异常，则换一个再试，避免整次右键静默失败。
        net.minecraft.world.entity.Mob mob = null;
        int attempts = Math.min(8, pool.size());
        for (int i = 0; i < attempts && mob == null; i++) {
            EntityType<?> chosen = pool.get(random.nextInt(pool.size()));
            try {
                Entity e = chosen.create(serverLevel);
                if (e instanceof net.minecraft.world.entity.Mob m) {
                    mob = m;
                } else if (e != null) {
                    e.discard();
                }
            } catch (Exception ignored) {
                // 该类型创建失败，继续尝试池中其它类型
            }
        }
        if (mob == null) return;

        mob.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        mob.setYRot(0f);
        mob.setYHeadRot(0f);
        mob.setPersistenceRequired();

        float scale = (float) (0.3 + random.nextDouble() * 2.7); // 0.3 ~ 3.0
        net.minecraft.world.entity.ai.attributes.AttributeInstance scaleAttr =
            mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        if (scaleAttr != null) scaleAttr.setBaseValue(scale);

        // 最大生命值与 scale 成正比：大个子更耐打、小个子更脆，让体型与血量直观对应。
        net.minecraft.world.entity.ai.attributes.AttributeInstance healthAttr =
            mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            double baseMax = healthAttr.getBaseValue();
            double scaledMax = baseMax * scale;
            healthAttr.setBaseValue(scaledMax);
            mob.setHealth((float) scaledMax);
        }

        // 移动速度与 scale 成反比：体型越大移动越慢、体型越小移动越快。
        net.minecraft.world.entity.ai.attributes.AttributeInstance moveAttr =
            mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (moveAttr != null) {
            double baseSpeed = moveAttr.getBaseValue();
            moveAttr.setBaseValue(baseSpeed / scale);
        }

        // 身首异处占主导：凡是未被排除宿主的“非 boss”身体，一律赋予随机其它生物的头。
        // （头不受 boss 限制，可含末影龙/凋灵等）。正常形态仅保留给：无真实头部模型的生物
        // （鱼/史莱姆/恶魂等，客户端自动回退为原形态）以及被排除的宿主（鱿鱼等）。
        if (!isTransplantTargetExcluded(mob.getType())) {
            setTransplantedHead(mob, getRandomHeadTypeId(random));
        }

        // 概率随机“移植脚”：约 50% 概率用随机生物的一条腿替换本生物的所有腿（纯外观，不改音效）。
        // 无腿部件的宿主（鱼/鱿鱼/史莱姆等）在客户端会自然回退为原形态。
        if (!isTransplantTargetExcluded(mob.getType()) && random.nextFloat() < 0.5f) {
            setTransplantedFeet(mob, getRandomHeadTypeId(random));
        }

        serverLevel.addFreshEntity(mob);

        // 广播“移植头/移植脚”：实体刚 addFreshEntity 后追踪列表可能尚未建立，立即 sendToPlayersTracking 的包
        // 常被丢弃。故 ① 立即直接发给召唤者本人（单机即唯一玩家，保证即时可见）；② 下个服务端 tick 再
        // 向所有追踪该实体的玩家广播（此时追踪已建立），确保多人/其他玩家也能收到，避免“身首异处占少数”。
        String headId = getTransplantedHead(mob);
        String feetId = getTransplantedFeet(mob);
        boolean hasTransplant = !headId.isEmpty() || !feetId.isEmpty();
        if (hasTransplant) {
            if (!headId.isEmpty()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    (net.minecraft.server.level.ServerPlayer) player,
                    new cn.autoforged.joes_addons_for_abmc.network.TransplantedHeadPayload(mob.getId(), headId));
            }
            if (!feetId.isEmpty()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    (net.minecraft.server.level.ServerPlayer) player,
                    new cn.autoforged.joes_addons_for_abmc.network.TransplantedFeetPayload(mob.getId(), feetId));
            }
            final net.minecraft.world.entity.Mob trackedMob = mob;
            serverLevel.getServer().execute(() -> {
                broadcastTransplantedHead(trackedMob);
                broadcastTransplantedFeet(trackedMob);
            });
        }

        // 消耗（该生物最大生命值）耐久，至少 1 点
        int cost = Math.max(1, (int) Math.ceil(mob.getMaxHealth()));
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        hurtStaff(stack, cost, player, slot);
    }

    // ===== 蜘蛛网权杖 =====

    /** 蜘蛛网权杖：右键发射一束蛛丝，命中方块则拉向自己、命中弹射物则移除、命中生物则无效化/铺蛛网。 */
    public static void executeCobwebStaffAbility(Player player, ItemStack stack, InteractionHand hand) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(COBWEB_MAX_RANGE));

        // 同时做方块与实体射线，取较近者。
        // 方块射线忽略所有液体（Fluid.NONE）：水方块不再被当成目标点。
        BlockHitResult blockHit = serverLevel.clip(
            new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double blockDist = blockHit.getType() == HitResult.Type.BLOCK
            ? eye.distanceTo(blockHit.getLocation())
            : Double.MAX_VALUE;

        double nearest = blockDist;
        Entity target = null;
        Vec3 targetPos = null;
        AABB scanBox = new AABB(eye, end).inflate(1.5);
        for (Entity e : serverLevel.getEntities(player, scanBox,
            e -> e.isAlive() && !e.isSpectator()
                && (e.isPickable() || isCobwebRemovableProjectile(e)))) {
            Vec3 clipPos = e.getBoundingBox().inflate(0.3).clip(eye, end).orElse(null);
            EntityHitResult ehr = clipPos != null ? new EntityHitResult(e, clipPos) : null;
            if (ehr == null) continue;
            double d = eye.distanceTo(ehr.getLocation());
            if (d < nearest) {
                nearest = d;
                target = e;
                targetPos = ehr.getLocation();
            }
        }
        // 若无更近的实体命中，则目标点取方块命中点（若命中）
        if (target == null && blockHit.getType() == HitResult.Type.BLOCK) {
            targetPos = blockHit.getLocation();
        }

        // 选取目标点时：若从眼位到目标点的射线路径上途经岩浆，则选取失败，
        // 除非玩家身上带有抗火效果。
        if (targetPos != null
            && !player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)
            && isLavaOnPath(serverLevel, eye, targetPos)) {
            return;
        }

        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        hurtStaff(stack, 1, player, slot);

        if (target != null) {
            handleCobwebEntityHit(serverLevel, target);
            return;
        }
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            startCobwebPull(player, blockHit.getLocation());
        }
    }

    /** 判断从 from 到 to 的线段路径上是否途经岩浆方块。
     *  沿射线按 0.1 格步长采样，任一采样点所在方块的流体为岩浆即返回 true。 */
    private static boolean isLavaOnPath(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        double dist = dir.length();
        if (dist < 1.0E-4) return false;
        dir = dir.normalize();
        double step = 0.1;
        int steps = (int) Math.ceil(dist / step);
        for (int i = 1; i <= steps; i++) {
            Vec3 p = from.add(dir.scale(Math.min(i * step, dist)));
            if (level.getBlockState(BlockPos.containing(p))
                .getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }

    private static void handleCobwebEntityHit(ServerLevel serverLevel, Entity target) {
        // 弹射物：删除且不触发爆炸
        if (isCobwebRemovableProjectile(target)) {
            target.discard();
            return;
        }
        // 生物
        if (target instanceof LivingEntity living) {
            handleCobwebLivingHit(serverLevel, living);
        }
    }

    private static boolean isCobwebRemovableProjectile(Entity e) {
        if (e instanceof net.minecraft.world.entity.item.PrimedTnt) return true;
        if (e instanceof cn.autoforged.joes_addons_for_abmc.entity.TntStaffCreeper) return true;
        // 覆盖所有弹射物：箭、三叉戟、雪球/蛋/末影珍珠/药水、凋灵之首、火焰弹、
        // 恶魂火球、末影龙火球、潜影贝导弹等（这些类均实现 Projectile）。
        if (e instanceof Projectile) return true;
        // 个别“头颅/火球”类自定义实体若不实现 Projectile，也按弹射物处理（Delete 不触发爆炸）。
        if (e instanceof AbstractArrow) return true;
        if (e instanceof WitherSkull) return true;
        if (e instanceof LargeFireball) return true;
        if (e instanceof SmallFireball) return true;
        if (e instanceof ShulkerBullet) return true;
        if (e instanceof net.minecraft.world.entity.projectile.DragonFireball) return true;
        if (e instanceof ThrowableProjectile) return true;
        if (e instanceof net.minecraft.world.entity.projectile.ThrownEnderpearl) return true;
        return false;
    }

    private static void handleCobwebLivingHit(ServerLevel serverLevel, LivingEntity living) {
        // 主手或副手持有权杖均可被无效化
        ItemStack held = living.getMainHandItem();
        boolean holdingStaff = held.getItem() instanceof StaffItem;
        if (!holdingStaff && living.getOffhandItem().getItem() instanceof StaffItem) {
            held = living.getOffhandItem();
            holdingStaff = true;
        }
        boolean nullified = holdingStaff && COBWEB_NULLIFIED.containsKey(living.getUUID());
        if (holdingStaff && !nullified) {
            String bt = held.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
            // 非豁免类型：无效化该权杖；豁免类型无法无效化，改为铺蛛网
            if (!COBWEB_EXEMPT_BLOCKTYPES.contains(bt)) {
                nullifyEntityStaff(serverLevel, living);
                return;
            }
        }
        placeCobwebsOverEntity(serverLevel, living);
    }

    private static void nullifyEntityStaff(ServerLevel serverLevel, LivingEntity living) {
        long gt = serverLevel.getGameTime();
        COBWEB_NULLIFIED.put(living.getUUID(),
            new CobwebNullifyState(living.getId(), gt + COBWEB_NULLIFY_DURATION));
        broadcastCobwebNullify(living);
    }

    /** 在生物碰撞箱触及的所有坐标放置蜘蛛网（仅覆盖空气与水源），每个放置伴随一次放置音效。 */
    private static void placeCobwebsOverEntity(ServerLevel serverLevel, LivingEntity living) {
        AABB box = living.getBoundingBox();
        int minX = Mth.floor(box.minX), maxX = Mth.floor(box.maxX);
        int minY = Mth.floor(box.minY), maxY = Mth.floor(box.maxY);
        int minZ = Mth.floor(box.minZ), maxZ = Mth.floor(box.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = serverLevel.getBlockState(pos);
                    if (!state.isAir() && !state.getFluidState().is(FluidTags.WATER)) continue;
                    if (serverLevel.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState())) {
                        serverLevel.playSound(null, x + 0.5, y + 0.5, z + 0.5,
                            Blocks.COBWEB.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                }
            }
        }
    }

    /** 命中方块：向目标坐标发起拉扯（S 形加速度，距离越远峰值越大，对数关系）。 */
    private static void startCobwebPull(Player player, Vec3 anchor) {
        double dist = player.position().distanceTo(anchor);
        if (dist < 2.0) {
            if (COBWEB_PULLING.remove(player.getUUID()) != null) {
                recordCobwebPullEnd(player.getUUID());
            }
            return;
        }
        Vec3 dir = anchor.subtract(player.position()).normalize();
        double logD = Math.log1p(Math.max(0.0, dist));
        // 加速度峰值削减为原来的 2/3
        double apex = Mth.clamp(0.10 * logD, 0.06, 0.5) * (2.0 / 3.0);
        // 初速度：一开始就有一个指向目标的初始速度（不从 0 起步），随距离增大而略增。
        double initSpeed = Mth.clamp(0.05 * logD, 0.10, 0.40);
        Vec3 initVel = new Vec3(dir.x * initSpeed, dir.y * initSpeed, dir.z * initSpeed);
        int rampTicks = (int) Mth.clamp(20 + dist * 1.5, 30, 160);
        COBWEB_PULLING.put(player.getUUID(), new CobwebPullState(anchor, rampTicks, apex, initVel));
        // 通知客户端开始渲染“玩家→锚点”蛛丝线段（锚点不动，起始跟随玩家）。
        if (player instanceof ServerPlayer sp) {
            sp.connection.send(new CobwebPullPayload(anchor));
        }
    }

    /** 拉扯每刻推进：加速度先线性升到峰值再线性降至 0（S 形），距离<2 或断开时停止但保留速度。 */
    private static void handleCobwebPullTick(MinecraftServer server) {
        if (COBWEB_PULLING.isEmpty()) return;
        java.util.Iterator<java.util.Map.Entry<java.util.UUID, CobwebPullState>> it = COBWEB_PULLING.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<java.util.UUID, CobwebPullState> en = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null || !p.isAlive()) {
                it.remove();
                continue;
            }
            CobwebPullState st = en.getValue();
            st.elapsed++;
            Vec3 pos = p.position();
            if (pos.distanceTo(st.target) < 2.0) {
                it.remove(); // 距离<2 断开，保留已获得速度
                recordCobwebPullEnd(p.getUUID());
                p.connection.send(new CobwebPullStopPayload()); // 清除客户端蛛丝线段
                continue;
            }
            // 方块阻挡判定（宽松）：检测“玩家→锚点”路径是否被实体方块拦断（命中点明显比锚点更近）。
            net.minecraft.world.phys.BlockHitResult cobwebHit = p.serverLevel().clip(
                new net.minecraft.world.level.ClipContext(pos, st.target,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, p));
            boolean pathBlocked = cobwebHit.getType() != net.minecraft.world.phys.BlockHitResult.Type.MISS
                && pos.distanceTo(cobwebHit.getLocation()) < pos.distanceTo(st.target) - 0.5;
            if (pathBlocked) {
                // 玩家若正朝目标方向快速移动（≥ 理论最大速度 3/4），视为在尝试前进/已越过阻挡 → 重置倒计时。
                Vec3 pullDir = st.target.subtract(pos).normalize();
                double towardSpeed = Math.max(0.0, p.getDeltaMovement().dot(pullDir));
                double refMax = st.vel.length();
                if (towardSpeed >= refMax * 0.75) {
                    st.blockedTicks = 0;
                } else {
                    st.blockedTicks++;
                    // 连续被挡超过 5 秒仍未脱困才断开，保留已获得速度并清除客户端蛛丝线段。
                    if (st.blockedTicks > 100) {
                        it.remove();
                        recordCobwebPullEnd(p.getUUID());
                        p.connection.send(new CobwebPullStopPayload());
                        continue;
                    }
                }
            } else {
                st.blockedTicks = 0; // 阻挡消失 → 重置倒计时
            }
            double half = st.rampTicks / 2.0;
            double f;
            if (st.elapsed <= half) {
                f = st.elapsed / half;
            } else {
                f = Math.max(0.0, 1.0 - (st.elapsed - half) / half);
            }
            double accel = st.apexAccel * f;
            Vec3 dir = st.target.subtract(pos).normalize();
            // 首帧把初速度以矢量形式叠加到玩家原有速度上，保留既有动量（荡来荡去的关键）。
            if (st.elapsed == 1) {
                st.vel = p.getDeltaMovement().add(st.initialVelocity);
            }
            if (f > 0.0) {
                // 加速阶段：以“矢量”方式把通向锚点的加速度叠加到现有速度上，保留玩家原有
                // 的横向/切向动量，从而形成弧形摆动，而不是每次都被直线拽向锚点。
                st.vel = st.vel.add(dir.x * accel, dir.y * accel, dir.z * accel);
            } else {
                // 滑行阶段（加速度降为 0）：保留速度大小，但把方向转向锚点，
                // 既维持加速完成后的峰值速度，又不会让惯性带着玩家越退越远。
                double speed = st.vel.length();
                st.vel = new Vec3(dir.x * speed, dir.y * speed, dir.z * speed);
            }
            p.setDeltaMovement(st.vel);
            p.hasImpulse = true;
            // 服务端对玩家位置的改动（setDeltaMovement/move）会被玩家客户端的位置预测覆盖，根本拉不动。
            // 这里把本次速度直接同步给该玩家客户端，客户端收到速度包后就会按此速度朝目标飞行，
            // 实现真正的“拉扯”；断开后停止发送即可保留所得动量。
            p.connection.send(new ClientboundSetEntityMotionPacket(p));
        }
    }

    /** 无效化到期清理：到期后移除状态并通知客户端取消覆盖层渲染。 */
    private static void handleCobwebNullifyExpiry(MinecraftServer server) {
        if (COBWEB_NULLIFIED.isEmpty()) return;
        long now = server.overworld().getGameTime();
        java.util.Iterator<java.util.Map.Entry<java.util.UUID, CobwebNullifyState>> it = COBWEB_NULLIFIED.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<java.util.UUID, CobwebNullifyState> en = it.next();
            CobwebNullifyState st = en.getValue();
            if (now < st.expireTick) continue;
            it.remove();
            Entity e = findEntityById(server, st.entityId);
            if (e != null) broadcastCobwebClear(e);
        }
    }

    private static Entity findEntityById(MinecraftServer server, int id) {
        for (ServerLevel lvl : server.getAllLevels()) {
            Entity e = lvl.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    private static void broadcastCobwebNullify(Entity e) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new CobwebNullifyPayload(e.getId()));
    }

    private static void broadcastCobwebClear(Entity e) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new CobwebClearPayload(e.getId()));
    }

    // ===== 铁链权杖 =====

    /** 铁链最大射程（格）：发射距离不限，取“已加载区块的最大距离”。 */
    private static final double CHAIN_MAX_RANGE = 512.0;
    /** 物品/掉落物被拉回时，距玩家距离（格）低于该值视为“拉到脚下”。 */
    private static final double CHAIN_ITEM_REACH = 1.1;
    /** 钩取终点同步节流（游戏刻）。数值越小铁链跟手越平滑，但发包更频繁。 */
    private static final int CHAIN_SYNC_INTERVAL = 1;
    /** 持续拉取时的耐久消耗间隔（刻）。 */
    private static final int CHAIN_DURABILITY_TICKS = 20;
    /** 物品模式：朝玩家收拢的加速度幅值。 */
    private static final double CHAIN_ITEM_ACCEL = 0.25;
    /** 拉取速度随“初始距离”线性增长（非正比，含基础截距 1.0）的斜率：每格距离增加的速度倍率。
     *  倍率 = 1.0 + 初始距离 * 本常量：距离越远初速度/加速度越大，近距离仍保留基础速度。 */
    private static final double CHAIN_SPEED_PER_BLOCK = 0.03;

    // ---- 生物模式“离心摆”（圆锥摆）模型：把生物当作系在绕玩家转动的绳子上的小球——
//     绳端始终位于玩家当前朝向（与玩家同步转动，绝不滞后到后方）；
//     玩家静止/不转头时甩动强度≈0 → 绳竖直、生物自然下垂在玩家正下方；
//     玩家转头越快，甩动强度越高 → 绳与竖直方向夹角越大、半径越大、位置越高；
//     玩家停转后强度逐渐回落，生物逐渐下垂归位 ----
    /** 鞭绳平静（无甩动）时的绳长（格）：生物垂挂在绳端（下垂状态）。 */
    private static final double WHIP_LEN_CALM = 2.5;
    /** 鞭绳在玩家大力甩动时可达到的最远绳长（格）：越大甩动越远、越有力。 */
    private static final double WHIP_MAX = 12.0;
    /** 甩动强度（平滑后的玩家转头角速度，弧度/刻）达到该值时，甩动达到最强
     *  （绳长最长、夹角最大）。 */
    private static final double WHIP_SWING_AT_SPIN = 0.20;
    /** 甩动触发阈值（弧度/刻）：玩家转头角速度低于该值时完全不甩动（生物安静垂着），
     *  超过该值才开始甩开生物——防止轻微移动就把生物甩得特别明显。
     *  同时作为“刚性跟手 / 惯性绕圈”的切换阈值。 */
    private static final double WHIP_SPIN_DEADZONE = 0.05;
    /** 玩家单 tick 转角增量上限（弧度/刻）：转头再快也按该值计入，防止瞬间拉满。 */
    private static final double WHIP_MAX_YAW_TICK = 0.60;
    /** 甩动强度上升时的平滑系数（0~1）：转头越快越早被甩开。 */
    private static final double WHIP_SPIN_RISE = 0.50;
    /** 甩动强度回落时的平滑系数（0~1）：停转后按该比例逐渐下垂归位。
     *  越小甩动余韵越久、越有“甩出去”的感觉，但回归下垂越慢。 */
    private static final double WHIP_SPIN_FALL = 0.20;
    /** 甩动最强时，绳与竖直方向的夹角（弧度）：0=完全下垂，约 1.40≈接近水平。 */
    private static final double WHIP_MAX_THETA = 1.40;
    /** 鞭绳长度向目标长度收敛的速度系数（每 tick）。 */
    private static final double WHIP_LEN_EASE = 0.15;
    // ---- 生物“重力/重量”：权杖按碰撞箱体积（宽×高×长，格³，≈质量）成正比例计算生物所受
    //      “重力”，决定生物静止下垂时与玩家的垂直距离——更大更重的生物垂得越低
    //      （重物把绳拉得更长）；摆动时绳长在此基础上按甩动强度继续伸长 ----
    /** 静止下垂的基础深度（格）：所有生物至少垂到该深度（≈原 WHIP_LEN_CALM）。 */
    private static final double CHAIN_HANG_BASE = 2.5;
    /** 每单位碰撞箱体积（宽×高×长，格³ ≈ 质量）额外垂下的深度（格）。 */
    private static final double CHAIN_HANG_SIZE = 0.6;
    // ---- 生物“质量”绳长弹性：甩动时绳长的增幅（拉长量）随生物“重量”（碰撞箱体积）缩放——
    //      更重的生物甩动时能把绳拉得更长（重物惯性把绳绷直拉长）；轻生物几乎不拉长。
    //      与静止下垂深度（CHAIN_HANG_*）解耦：下垂深度决定“静止时垂多低”，本系数决定“甩动时拉多长” ----
    /** 碰撞箱体积达到该值（宽×高×长，格³ ≈ 1 格方块）时，甩动绳长增幅达到 100%；更轻则按比例缩减。 */
    private static final double CHAIN_MASS_STRETCH_VOL = 1.0;
    /** 甩动绳长增幅的最小比例（0~1）：极轻的生物也保留一点拉长，避免完全定长死绳。 */
    private static final double CHAIN_MASS_STRETCH_MIN = 0.10;
    // ---- 链优先·绳摆物理：先把“铁链末端”挂点 P 算出来（离心摆几何），再把
    //      “铁链 + 生物”整体当作一根挂在锚点 A 的绳摆：生物 = 摆锤，受重力下垂、
    //      被弹性拉向 P，并受刚性绳长约束（距锚点 ≤ L，绳不伸长）——绳与生物始终
    //      整体运动（链永远连到生物，无视觉缝隙），但链能自然松弛下垂、甩动时摆锤
    //      带惯性摆动，不再是一根绷直的刚性杆 ----
    /** 未挂接时朝链端 P 收敛的弹簧系数（每 tick）：越大收绳越利落。 */
    private static final double CHAIN_LOCK_STIFF = 0.6;
    /** 未挂接时朝链端 P 移动的最大速度（格/刻）：收绳入位比原软弹簧快。 */
    private static final double CHAIN_LOCK_SPEED = 3.0;
    /** 生物距锚点接近绳长（误差小于该值）即视为“已挂接”，进入绳摆物理。 */
    private static final double CHAIN_LOCK_EPS = 0.05;
    /** 已挂接时，生物距锚点超出“绳长 + 该值”（如被外力推走/玩家瞬移）则掉回收绳状态。 */
    private static final double CHAIN_ATTACH_BREAK = 6.0;
    // ---- 绳摆物理参数（生物已挂接时） ----
    /** 绳摆弹性：每 tick 把摆锤拉向挂点 P 的比例（相对位移）。越大追得越跟手、摆动越小。 */
    private static final double CHAIN_ROPE_STIFF = 0.35;
    /** 绳摆速度阻尼（每 tick 乘，0~1）：越小惯性越大、甩动余韵越久；过小会来回晃。 */
    private static final double CHAIN_ROPE_DAMP = 0.55;
    /** 绳摆重力下垂加速度（格/刻²）：甩到接近水平时摆锤自然下垂的力度（越紧越要调大）。 */
    private static final double CHAIN_ROPE_GRAV = 0.10;
    /** 绳摆最大速度（格/刻）：防止远距离猛冲成瞬移。 */
    private static final double CHAIN_ROPE_MAX_V = 1.6;
    // ---- 碰撞感知（防止窒息）：生物贴近墙面/地面时，从“链优先刚性钉住”切换到“生物优先
    //      碰撞移动”（沿墙滑、落地即停，铁链跟随生物实际位置），杜绝被钉进方块窒息 ----
    /** 判定“贴近墙面/地面”时碰撞箱向外膨胀的余量（格）：越大越早切到碰撞移动、越防卡入。 */
    private static final double CHAIN_COLLISION_MARGIN = 0.3;
    /** 释放甩出时的最大初始速度（格/刻）：防止疯狂旋转后释放把生物射飞过远。 */
    private static final double CHAIN_RELEASE_MAX_V = 2.5;
    // ---- 甩动“惯性绕圈”：玩家停转后，被甩的生物并不立刻归中，而是以当前的角速度继续
    //      绕玩家转圈（像被甩起来的摆锤）——角速度与摆角逐渐衰减、圈子越转越小，
    //      最终自然下垂归位。玩家再次转头时立即恢复“刚性跟手”（拴绳）。
    //      与“长度上限增强”不同：那是放大绳长上限，这里是停转后保留甩动余韵 ----
    /** 惯性绕圈角速度每刻衰减系数（0~1，乘算）：越小停得越快。
     *  0.96 ≈ 约 3 秒转完约 1.5 圈后基本停下。 */
    private static final double ORBIT_OMEGA_DAMP = 0.96;
    /** 惯性绕圈摆角（绳与竖直方向夹角）每刻衰减系数（0~1，乘算）：越小圈子缩得越快。 */
    private static final double ORBIT_THETA_DAMP = 0.96;
    /** 惯性绕圈时绳长向“静止下垂深度”收敛的速度（每刻，比甩动时更慢，让圈逐渐缩小）。 */
    private static final double WHIP_LEN_EASE_ORBIT = 0.06;
    /** 视为绕圈结束、完全下垂的角速度下限（弧度/刻）。 */
    private static final double ORBIT_MIN_OMEGA = 0.005;
    /** 视为绕圈结束、完全下垂的摆角下限（弧度）。 */
    private static final double ORBIT_MIN_THETA = 0.02;

    // ---- 甩动“长度上限增强”：玩家头部高速运动时，把鞭绳长度上限临时放大到 WHIP_BOOST_MAX 倍，
    //      停止甩动后平滑回落到 1 倍——甩得越猛生物能被甩得越远，停下后逐渐收绳归位 ----
    /** 高速甩动时鞭绳长度上限的放大倍数（1=不放大，本值=3 即上限临时放大到 3 倍）。 */
    private static final double WHIP_BOOST_MAX = 3.0;
    /** 转头角速度（弧度/刻）达到该值时，长度上限放大到最大（WHIP_BOOST_MAX 倍）。 */
    private static final double WHIP_BOOST_SPIN_AT_MAX = 0.30;
    /** 增强系数上升（甩动变快）时的平滑系数。 */
    private static final double WHIP_BOOST_RISE = 0.45;
    /** 增强系数回落（停止甩动）时的平滑系数：越小收绳越慢。 */
    private static final double WHIP_BOOST_FALL = 0.20;

    // ---- 垂直“提拉”效果：玩家上下摆头（pitch）像提拉绳子——向上看抬高绳端（生物被提起），
    //      向下看压低绳端（生物下放）；对玩家俯仰角做平滑跟踪，抬头/低头越快提拉越跟手。
    //      与水平“甩动”相互独立：转头控制水平甩动，俯仰角控制垂直提拉。
    //      最大提拉高度可在配置中调整（ModConfig.CHAIN_LIFT_STRENGTH，格，默认 5，0=关闭） ----
    /** 提拉强度（平滑后的俯仰角偏移）上升时（抬头向上提）的平滑系数。 */
    private static final double WHIP_LIFT_RISE = 0.30;
    /** 提拉强度回落时（低头下放）的平滑系数：稍慢，回落更平滑。 */
    private static final double WHIP_LIFT_FALL = 0.15;

    /** 铁链钩取会话：玩家 UUID -> 当前钩取状态。 */
    private static final Map<UUID, ChainGrabState> CHAIN_GRABS = new ConcurrentHashMap<>();

    /** 铁链钩取会话状态。 */
    private static final class ChainGrabState {
        /** 目标类型：物品（MODE_ITEM）或生物（MODE_LIVING），与网络包常量一致。 */
        int mode;
        /** 目标实体 uuid。 */
        UUID targetUuid;
        /** 目标实体 id（生物或物品实体）。 */
        int targetId = -1;
        /** 物品实体 id（掉落物/缴械物）；拉生物本体时为 -1。 */
        int itemId = -1;
        /** 生物模式“离心摆”：当前绳长（格），在 [静止下垂深度, WHIP_MAX] 内随甩动伸缩。 */
        double whipLen = WHIP_LEN_CALM;
        /** 生物模式“离心摆”：甩动强度（平滑后的玩家转头角速度，弧度/刻）。
         *  0=静止下垂；越大绳越斜、生物被甩得越开越高。 */
        double whipOmega = 0.0;
        /** 生物模式“离心摆”：带方向的平滑转头角速度（弧度/刻，正=顺时针/向右转头，
         *  负=逆时针/向左转头）。用于停转后惯性绕圈的方向（圈按最后一次甩动方向转）。 */
        double whipOmegaSigned = 0.0;
        /** 生物模式“离心摆”：上一 tick 玩家 yaw（用于计算转头角速度）。 */
        float lastYaw;
        /** 生物模式“离心摆”：垂直“提拉”偏移（格），平滑跟踪玩家俯仰角（上下摆头）。
         *  正值=绳端被抬高（生物被提起），负值=被压低（下放）。 */
        double pitchLift = 0.0;
        /** 生物模式“离心摆”：甩动长度上限增强系数（1.0~WHIP_BOOST_MAX），随转头速度平滑变化。
         *  1=正常上限，>1 表示绳长上限被临时放大（甩得更远）。 */
        double lenBoost = 1.0;
        /** 生物模式“绳摆物理”：是否已“挂接”在铁链末端（生物 = 摆锤）。
         *  未挂接时朝链端收绳入位。 */
        boolean attached;
        /** 生物模式“绳摆物理”：摆锤速度（格/刻），已挂接时每刻积分（重力 + 弹性 + 阻尼）。 */
        Vec3 pendVel = Vec3.ZERO;
        /** 是否处于“惯性绕圈”阶段（玩家已停转，生物靠惯性绕玩家转圈、圈渐小直至下垂）。 */
        boolean orbiting;
        /** “惯性绕圈”方位角（弧度，绕玩家的当前角度；玩家转头跟手时 = 玩家 yaw 弧度）。 */
        double orbitYaw;
        /** “惯性绕圈”角速度（弧度/刻）：停转后每刻按 ORBIT_OMEGA_DAMP 衰减。 */
        double orbitOmega;
        /** “惯性绕圈”摆角（绳与竖直方向夹角，弧度）：停转后每刻按 ORBIT_THETA_DAMP 衰减。 */
        double orbitTheta;
        /** 碰撞移动（贴近墙/地面“生物优先”）是否使生物实际位置偏离轨道点：为 true 时，
         *  下一次停转接管绕圈会先从生物实际位置反推轨道状态，避免衔接瞬移。 */
        boolean orbitNeedsSync;
        /** 上次向客户端同步终点的游戏刻（节流）。 */
        long lastSyncTick;
        /** 持续拉取耐久计时。 */
        int durabilityTick;
    }

    /** 铁块权杖右键能力：发射铁链（直线、不受重力），抬起目标并拉向玩家。
     *  <ul>
     *   <li>没有对准生物/掉落物 → 铁链自动收回（仅播放发射收回动画），无效果。</li>
     *   <li>对准掉落物 → “钩”住并拉到玩家脚下（铁链随距离变短）。</li>
     *   <li>对准生物 → 若其主手持有物品则“缴械”，把该物品拉到玩家脚下；
     *       否则拉起生物并进入“鞭绳”模式：近似定长的绳子带着生物在 4~8 格内绕玩家转，
     *       玩家甩头时生物被甩得远离（最远 8 格）、平静时逐渐收回 4 格，不自动断开。</li>
     *  </ul>
     *  拉取期间玩家移动/转头铁链跟手（起点跟随玩家实时重算），松开右键或中途按左键可断开铁链。 */
    public static void executeChainStaffAbility(Player player, ItemStack stack, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) return;
        ServerLevel level = sp.serverLevel();
        // 重发前清理旧的钩取会话（目标保持当前速度惯性甩出）。
        cancelChainGrab(sp);

        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        hurtStaff(stack, 1, player, slot);

        Vec3 origin = applyLineEmitterOffset(sp, sp.getEyePosition());
        Vec3 look = sp.getLookAngle();
        Vec3 end = origin.add(look.scale(CHAIN_MAX_RANGE));

        // 命中检测：铁链射线只要与“目标碰撞箱（向外膨胀 0.25 格容错）”接触即命中。
        // 生物与掉落物合并为同一候选集，取射线最近者——避免“生物优先”抢占尸体旁的掉落物，
        // 保证掉落物能被可靠选中。
        final double FUZZ = 0.25;
        double nearest = Double.MAX_VALUE;
        Entity target = null;
        AABB scanBox = new AABB(origin, end).inflate(FUZZ + 2.0);
        // 注意：原版 ItemEntity.isPickable() 恒为 false（原版交互逻辑对掉落物单独特判），
        // 因此掉落物必须与 isPickable() 解耦，否则永远无法被铁链选中。
        for (Entity e : level.getEntities(sp, scanBox,
            e -> e.isAlive() && !e.isSpectator()
                && ((e instanceof ItemEntity)
                    || (e.isPickable() && e instanceof LivingEntity && !(e instanceof ServerPlayer))))) {
            if (!segmentHitsBox(origin, end, e.getBoundingBox().inflate(FUZZ))) continue;
            double d = distToSegment(origin, end, e.position());
            if (d < nearest) {
                nearest = d;
                target = e;
            }
        }

        if (target == null) {
            // 未命中：铁链自动收回（客户端播放短暂发射收回动画），无其他效果。
            sp.connection.send(new cn.autoforged.joes_addons_for_abmc.network.ChainLaunchPayload(end.x, end.y, end.z));
            return;
        }

        Entity hooked;
        int mode;
        if (target instanceof ItemEntity item) {
            hooked = item;
            mode = cn.autoforged.joes_addons_for_abmc.network.ChainGrabPayload.MODE_ITEM;
        } else {
            LivingEntity living = (LivingEntity) target;
            ItemStack held = living.getMainHandItem();
            if (!held.isEmpty()) {
                // 缴械：把主手物品抽出为掉落物，钩住该掉落物拉向玩家脚下。
                ItemStack drop = held.copy();
                held.shrink(held.getCount());
                ItemEntity ie = new ItemEntity(level, living.getX(), living.getEyeY(), living.getZ(), drop);
                ie.setDefaultPickUpDelay();
                level.addFreshEntity(ie);
                hooked = ie;
                mode = cn.autoforged.joes_addons_for_abmc.network.ChainGrabPayload.MODE_ITEM;
            } else {
                // 拉生物本体：改用“鞭绳”模型（近似定长，4~8 格内甩动，不自动断开）。
                hooked = living;
                mode = cn.autoforged.joes_addons_for_abmc.network.ChainGrabPayload.MODE_LIVING;
            }
        }

        ChainGrabState st = new ChainGrabState();
        st.mode = mode;
        st.targetUuid = hooked.getUUID();
        st.targetId = hooked.getId();
        if (hooked instanceof ItemEntity) {
            st.itemId = hooked.getId();
        }
        // 生物模式：鞭绳初始长度 = 当前距离，但限制在 [静止下垂深度, WHIP_MAX] 内（后续自适应）；
        // 静止下垂深度由生物“重力/重量”（碰撞箱体积）决定，越大越重垂得越低。
        // 初始甩动强度为 0（静止下垂），记录玩家朝向用于计算转头角速度。
        double calmLen = chainHangDepth(hooked);
        st.whipLen = net.minecraft.util.Mth.clamp(origin.distanceTo(hooked.position()),
            calmLen, WHIP_MAX);
        st.lastYaw = sp.getYRot();
        st.lastSyncTick = level.getGameTime();
        CHAIN_GRABS.put(sp.getUUID(), st);
        sendChainGrabSync(level, sp, st);
    }

    /** 每游戏刻推进铁链钩取（服务端每刻调用）：把目标拉向玩家。
     *  <p>起点跟随玩家实时重算（移动/转头铁链跟手）。物品被拉到玩家脚下即完成；
     *  生物采用“链优先·刚性钉住”——先算铁链末端 P，再把生物固定在 P 上（挂在链末端）。 */
    private static void handleChainGrabTick(MinecraftServer server) {
        if (CHAIN_GRABS.isEmpty()) return;
        java.util.Iterator<Map.Entry<UUID, ChainGrabState>> it = CHAIN_GRABS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ChainGrabState> en = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(en.getKey());
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            ChainGrabState st = en.getValue();
            ServerLevel level = player.serverLevel();
            Entity target = level.getEntity(st.itemId >= 0 ? st.itemId : st.targetId);
            if (target == null || !target.isAlive() || target.isRemoved()) {
                // 目标消失（物品被拾取/生物死亡）：断开并清除铁链。
                it.remove();
                player.connection.send(new cn.autoforged.joes_addons_for_abmc.network.ChainGrabStopPayload());
                continue;
            }
            // 玩家不再持有铁链权杖（切手/换物品）：断开，生物以摆锤速度甩出。
            if (!isHoldingChainStaff(player)) {
                it.remove();
                flingChainTarget(player, st);
                player.connection.send(new cn.autoforged.joes_addons_for_abmc.network.ChainGrabStopPayload());
                continue;
            }
            // 持续拉取耐久消耗（每 CHAIN_DURABILITY_TICKS 刻 1 点）。
            st.durabilityTick++;
            if (st.durabilityTick >= CHAIN_DURABILITY_TICKS) {
                st.durabilityTick = 0;
                ItemStack main = player.getMainHandItem();
                boolean mainIsStaff = main.getItem() instanceof StaffItem;
                ItemStack staffStack = mainIsStaff ? main : player.getOffhandItem();
                EquipmentSlot slot = mainIsStaff ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                hurtStaff(staffStack, 1, player, slot);
            }

            Vec3 playerPos = player.position();
            Vec3 tpos = target.position();
            Vec3 to = playerPos.subtract(tpos);
            double dist = to.length();
            Vec3 dir = dist < 1.0E-4 ? Vec3.ZERO : to.scale(1.0 / dist);

            if (st.mode == cn.autoforged.joes_addons_for_abmc.network.ChainGrabPayload.MODE_ITEM) {
                // 物品：拉到玩家脚下即完成（停稳，留在原地可拾取）。
                if (dist < CHAIN_ITEM_REACH) {
                    target.setDeltaMovement(Vec3.ZERO);
                    target.hasImpulse = true;
                    it.remove();
                    player.connection.send(new cn.autoforged.joes_addons_for_abmc.network.ChainGrabStopPayload());
                    continue;
                }
                // 被链头甩动后仍未入怀的：直接朝玩家收拢（加速度随当前距离线性放大）。
                double spd = 1.0 + dist * CHAIN_SPEED_PER_BLOCK;
                Vec3 vel = target.getDeltaMovement();
                vel = vel.add(dir.scale(CHAIN_ITEM_ACCEL * spd));
                double maxV = 0.8 * spd;
                if (vel.length() > maxV) {
                    vel = vel.normalize().scale(maxV);
                }
                target.setDeltaMovement(vel);
                target.hasImpulse = true;
            } else {
                // 生物：链优先·绳摆物理——先算“铁链末端”P，再把生物当作绳端摆锤（挂在链末端）。
                updateChainWhip(player, st, target, playerPos);
            }

            if (level.getGameTime() - st.lastSyncTick >= CHAIN_SYNC_INTERVAL) {
                st.lastSyncTick = level.getGameTime();
                sendChainGrabSync(level, player, st);
            }
        }
    }

    /** 生物“重力/重量”对应的静止下垂深度（格）：按碰撞箱<b>体积</b>（宽×高×长，格³ ≈ 质量）
     *  成正比例计算——更大更重的生物垂得越低（重物把绳拉得更长）。 */
    private static double chainHangDepth(Entity e) {
        double vol = e.getBbWidth() * e.getBbHeight() * e.getBoundingBox().getZsize();
        return CHAIN_HANG_BASE + CHAIN_HANG_SIZE * vol;
    }

    /** 生物是否贴近墙面/地面（防止窒息）：判断生物以 pos 为位置（脚底）时，碰撞箱向外膨胀
     *  margin 后是否与任何实心方块相交。贴近时说明铁链正把生物拖向墙体/地面，此时应切换到
     *  “生物优先”的碰撞移动（沿墙滑、落地即停），让铁链跟随生物实际位置，而不是把它钉进方块。 */
    private static boolean chainNearSolid(Entity e, Vec3 pos, double margin) {
        double w = e.getBbWidth() / 2.0;
        double h = e.getBbHeight();
        BlockPos min = BlockPos.containing(pos.x - w - margin, pos.y - margin, pos.z - w - margin);
        BlockPos max = BlockPos.containing(pos.x + w + margin, pos.y + h + margin, pos.z + w + margin);
        for (BlockPos b : BlockPos.betweenClosed(min, max)) {
            if (e.level().getBlockState(b).isSolid()) return true;
        }
        return false;
    }

    /** 生物模式“链优先·绳摆物理”：先把“铁链末端”挂点 P 算出来（离心摆几何），
     *  再把“铁链 + 生物”整体当作一根挂在锚点 A 的绳摆——生物 = 摆锤，受重力下垂、
     *  被弹性拉向 P，并有刚性绳长约束（距 A ≤ L）。绳与生物始终整体运动（链永远连到
     *  生物，无视觉缝隙），但链能自然松弛下垂、甩动时摆锤带惯性摆动。
     *  <ul>
     *   <li>玩家静止/不转头时：甩动强度≈0 → P 垂在锚点正下方 → 生物垂在绳端下方（重力体现）。</li>
     *   <li>玩家转头/转身时：甩动强度（平滑后的转头角速度）上升，P 绕玩家摆动（绳长越长、
     *       绳与竖直夹角越大、水平半径越大、位置越高），摆锤被绳带着沿弧线甩出、自然下垂。</li>
     *   <li>玩家头部高速运动时，绳长上限临时放大到 WHIP_BOOST_MAX 倍（甩得越猛甩得越远），
     *       停止甩动后平滑回落回 1 倍并收绳归位。</li>
     *   <li><b>惯性绕圈：</b>玩家正在转头时 P 始终位于玩家当前朝向（刚性跟手、绝不滞后到后方）；
     *       玩家<b>停转后生物并不立刻归中</b>，而是以当时的角速度继续绕玩家转圈——角速度与
     *       摆角逐渐衰减、圈子越转越小，最终自然下垂归位（像被甩起的摆锤慢慢停下）。</li>
     *   <li>未挂接（首抓/距离过远）时朝 P 收绳入位（弹簧 + 速度上限防瞬移）；接近绳长即挂接
     *       进入绳摆；被外力推离超过阈值才掉回收绳状态。</li>
     *   <li>不自动断开：除非玩家左键（cancel）、切手或目标消失。</li>
     *  </ul> */
    private static void updateChainWhip(ServerPlayer player, ChainGrabState st, Entity target,
                                        Vec3 playerPos) {
        // 0) 甩动强度：玩家转头角速度（水平面 yaw，单 tick 增量钳制）的平滑值。
        //    转头越快 → 强度越高；停转后强度逐渐回落（自然下垂归位）。
        //    另维护一个“带方向”的平滑值（正=顺时针/向右转头，负=逆时针/向左转头），
        //    用于停转后惯性绕圈保持最后一次甩动的方向。
        float yawNow = player.getYRot();
        float yawDelta = net.minecraft.util.Mth.wrapDegrees(yawNow - st.lastYaw);
        st.lastYaw = yawNow;
        double signedSpin = Math.toRadians(yawDelta);
        double spin = Math.abs(signedSpin);
        if (spin > WHIP_MAX_YAW_TICK) {
            spin = WHIP_MAX_YAW_TICK;
            signedSpin = Math.copySign(WHIP_MAX_YAW_TICK, signedSpin);
        }
        double ease = spin > st.whipOmega ? WHIP_SPIN_RISE : WHIP_SPIN_FALL;
        st.whipOmega += (spin - st.whipOmega) * ease;
        double easeSigned = Math.abs(signedSpin) > Math.abs(st.whipOmegaSigned)
                ? WHIP_SPIN_RISE : WHIP_SPIN_FALL;
        st.whipOmegaSigned += (signedSpin - st.whipOmegaSigned) * easeSigned;

        // 0b) 甩动长度上限增强：头部高速运动 → 绳长上限临时放大到 WHIP_BOOST_MAX 倍（甩得更远），
        //     停止甩动后平滑回落到 1 倍（收绳归位）。低于甩动阈值不增强。
        double boostTarget = 1.0;
        if (spin > WHIP_SPIN_DEADZONE) {
            boostTarget = 1.0 + (WHIP_BOOST_MAX - 1.0)
                    * Math.min(1.0, (spin - WHIP_SPIN_DEADZONE)
                            / (WHIP_BOOST_SPIN_AT_MAX - WHIP_SPIN_DEADZONE));
        }
        double boostEase = boostTarget > st.lenBoost ? WHIP_BOOST_RISE : WHIP_BOOST_FALL;
        st.lenBoost += (boostTarget - st.lenBoost) * boostEase;
        double maxLen = WHIP_MAX * st.lenBoost;

        // 1) 垂直“提拉”偏移：平滑跟踪玩家俯仰角（抬头 pitch<0 → 抬高绳端把生物提起来，
        //    低头 pitch>0 → 压低绳端下放生物）。抬头/低头越快，提拉越跟手。
        //    最大提拉高度由配置 CHAIN_LIFT_STRENGTH 控制（默认 5 格，0=关闭）。
        float pitchNow = player.getXRot();
        double liftTarget = -pitchNow / 90.0 * cn.autoforged.joes_addons_for_abmc.config.ModConfig.CHAIN_LIFT_STRENGTH.get();
        double liftEase = Math.abs(liftTarget) > Math.abs(st.pitchLift)
                ? WHIP_LIFT_RISE : WHIP_LIFT_FALL;
        st.pitchLift += (liftTarget - st.pitchLift) * liftEase;

        // 2) 链优先：先算“绳长”与甩动几何（绳端挂点 P 在步骤 3 由锚点 + 绳长 + 摆角确定）。
        //    甩动越猛 → 绳长越长、绳与竖直夹角越大 → 水平半径越大、位置越高。
        //    静止下垂深度 = 基础深度 + 生物“重量”（碰撞箱体积宽×高×长）→ 更大更重垂得更低。
        //    质量绳长弹性：甩动时绳长增幅随生物“重量”缩放——越轻越不容易拉长（轻生物甩不出长绳），
        //    越重越能拉长（重物惯性把绳绷直拉长）。
        double swing = 0;
        if (st.whipOmega > WHIP_SPIN_DEADZONE) {
            swing = Math.min(1.0, (st.whipOmega - WHIP_SPIN_DEADZONE)
                    / (WHIP_SWING_AT_SPIN - WHIP_SPIN_DEADZONE));
        }
        double calmLen = chainHangDepth(target);
        double vol = target.getBbWidth() * target.getBbHeight() * target.getBoundingBox().getZsize();
        double massStretch = CHAIN_MASS_STRETCH_MIN
                + (1.0 - CHAIN_MASS_STRETCH_MIN) * Math.min(1.0, vol / CHAIN_MASS_STRETCH_VOL);

        Vec3 anchor = new Vec3(playerPos.x, playerPos.y + 1.0 + st.pitchLift, playerPos.z);

        // 3) 驱动 / 惯性绕圈：玩家正在转头 → 生物刚性跟手（拴绳零延迟），并记录当前的
        //    绕圈状态（角速度 = 转头角速度、摆角 = 当前甩动摆角），供停转后接管；
        //    玩家停转 → 生物靠惯性继续绕玩家转圈，角速度与摆角逐渐衰减、圈子渐小直至下垂。
        boolean driving = spin > WHIP_SPIN_DEADZONE;
        if (driving) {
            st.orbiting = false;
            st.orbitNeedsSync = false;
            double len = calmLen + (maxLen - calmLen) * swing * massStretch;
            st.whipLen += (len - st.whipLen) * WHIP_LEN_EASE;   // 绳长平滑过渡
            double theta = swing * WHIP_MAX_THETA;
            st.orbitYaw = Math.toRadians(yawNow);
            st.orbitOmega = st.whipOmegaSigned;
            st.orbitTheta = theta;
            double sinT = Math.sin(theta), cosT = Math.cos(theta);
            Vec3 dir = new Vec3(-Math.sin(st.orbitYaw) * sinT, -cosT, Math.cos(st.orbitYaw) * sinT);
            Vec3 p = anchor.add(dir.scale(st.whipLen));
            applyChainHangMove(st, target, anchor, p, st.whipLen);
        } else {
            // 停转接管：若此前被碰撞移动（生物实际位置偏离轨道点），先从实际位置反推
            // 轨道状态避免衔接瞬移；否则沿用跟手阶段记录的角速度/摆角（惯性）。
            if (!st.orbiting) {
                st.orbiting = true;
                st.orbitOmega = st.whipOmegaSigned;
                if (st.orbitNeedsSync) syncOrbitFromPos(st, target, anchor);
                st.orbitNeedsSync = false;
            }
            st.orbitOmega *= ORBIT_OMEGA_DAMP;                  // 圈越转越慢
            st.orbitTheta *= ORBIT_THETA_DAMP;                  // 圈越转越小
            if (Math.abs(st.orbitOmega) < ORBIT_MIN_OMEGA) st.orbitOmega = 0;
            if (st.orbitTheta < ORBIT_MIN_THETA) st.orbitTheta = 0;
            st.orbitYaw += st.orbitOmega;
            st.whipLen += (calmLen - st.whipLen) * WHIP_LEN_EASE_ORBIT;  // 绳长逐渐回收到位
            double theta = st.orbitTheta;
            double sinT = Math.sin(theta), cosT = Math.cos(theta);
            Vec3 dir = new Vec3(-Math.sin(st.orbitYaw) * sinT, -cosT, Math.cos(st.orbitYaw) * sinT);
            Vec3 p = anchor.add(dir.scale(st.whipLen));
            applyChainHangMove(st, target, anchor, p, st.whipLen);
        }
        freeMob(target);
    }

    /** 把被抓取生物移动到“铁链末端”挂点 P：
     *  空旷处“链优先”把生物刚性钉在 P（拴绳零延迟）；贴近墙面/地面时“生物优先”改用原版
     *  碰撞规则移动生物（沿墙滑、落地即停），铁链跟随生物实际位置（防窒息），并标记轨道
     *  状态需要同步；未挂接（首抓/被外力拉离）时朝 P 快速收绳入位（速度上限防瞬移）。 */
    private static void applyChainHangMove(ChainGrabState st, Entity target,
                                           Vec3 anchor, Vec3 p, double ropeLen) {
        Vec3 toP = p.subtract(target.position());
        Vec3 relA = target.position().subtract(anchor);
        double dA = relA.length();
        if (st.attached && dA < ropeLen + CHAIN_ATTACH_BREAK) {
            Vec3 cur = target.position();
            boolean nearGeo = chainNearSolid(target, p, CHAIN_COLLISION_MARGIN);
            if (nearGeo) {
                // 生物优先：摆锤积分（重力下垂 + 弹性拉向 P + 阻尼）后用碰撞移动，撞墙/落地即停。
                Vec3 vel = st.pendVel;
                vel = vel.add(0, -CHAIN_ROPE_GRAV, 0);
                vel = vel.add(toP.scale(CHAIN_ROPE_STIFF));
                vel = vel.scale(CHAIN_ROPE_DAMP);
                double vLen = vel.length();
                if (vLen > CHAIN_ROPE_MAX_V) vel = vel.scale(CHAIN_ROPE_MAX_V / vLen);
                st.pendVel = vel;
                target.move(MoverType.SELF, vel);
                target.hasImpulse = true;
                // 撞墙/落地后消去对应方向的速度，避免每刻反复顶墙抖动。
                if (target.horizontalCollision) vel = new Vec3(0, vel.y, 0);
                if (target.verticalCollision) vel = new Vec3(vel.x, 0, vel.z);
                st.pendVel = vel;
                target.setDeltaMovement(Vec3.ZERO);
                // 生物实际位置偏离轨道点：跟随/绕圈中同步回实际位置，避免随后接管时瞬移。
                st.orbitNeedsSync = true;
                if (st.orbiting) syncOrbitFromPos(st, target, anchor);
            } else {
                // 链优先·拴绳：把生物刚性钉在铁链末端 P，与链头零延迟、同点移动（像拴绳一样
                // 跟手，甩动不再有多刻延迟）。记录链端实际移动速度，供释放时甩出。
                Vec3 pv = p.subtract(cur);
                double pvl = pv.length();
                if (pvl > CHAIN_RELEASE_MAX_V) pv = pv.scale(CHAIN_RELEASE_MAX_V / pvl);
                st.pendVel = pv;
                target.setPos(p.x, p.y, p.z);
                target.setDeltaMovement(Vec3.ZERO);
                target.hasImpulse = true;
            }
        } else {
            // 收绳入位（未挂接）：朝挂点 P 收敛，接近绳长即挂接。
            st.attached = false;
            st.pendVel = Vec3.ZERO;
            Vec3 move = toP.scale(CHAIN_LOCK_STIFF);
            double maxV = CHAIN_LOCK_SPEED;
            if (move.length() > maxV) move = move.normalize().scale(maxV);
            target.setDeltaMovement(move);
            target.hasImpulse = true;
            if (dA <= ropeLen + CHAIN_LOCK_EPS) {
                st.attached = true;
                target.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    /** 由生物实际位置反推“惯性绕圈”的方位角与摆角（碰撞移动后生物偏离轨道点时调用，
     *  保证随后无缝继续绕圈/下垂）。 */
    private static void syncOrbitFromPos(ChainGrabState st, Entity target, Vec3 anchor) {
        Vec3 rel = target.position().subtract(anchor);
        double hor = Math.sqrt(rel.x * rel.x + rel.z * rel.z);
        double rope = Math.max(st.whipLen, 1.0E-4);
        double theta = Math.asin(Math.min(1.0, hor / rope));
        if (theta > Math.PI / 2) theta = Math.PI / 2;
        st.orbitTheta = theta;
        st.orbitYaw = Math.atan2(-rel.x, rel.z);
    }

    /** 让被钩生物停止自主行动（不走动、不寻敌），表现为被铁链持续拖拽。 */
    private static void freeMob(Entity target) {
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
            mob.setLastHurtByMob(null);
            if (mob.getBrain() != null) {
                mob.getBrain().eraseMemory(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            }
        }
    }

    /** 向指定玩家发送当前铁链钩取的起点与目标终点（用于渲染）。 */
    private static void sendChainGrabSync(ServerLevel level, ServerPlayer player, ChainGrabState st) {
        Entity target = level.getEntity(st.itemId >= 0 ? st.itemId : st.targetId);
        if (target == null) return;
        Vec3 origin = applyLineEmitterOffset(player, player.getEyePosition());
        Vec3 t = target.position();
        player.connection.send(new cn.autoforged.joes_addons_for_abmc.network.ChainGrabPayload(
            st.mode, origin.x, origin.y, origin.z,
            t.x, t.y + target.getBbHeight() * 0.3, t.z,
            target.getId()));
    }

    /** 玩家当前主手/副手是否持有铁块（铁链）权杖。 */
    private static boolean isHoldingChainStaff(Player p) {
        for (ItemStack s : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()}) {
            if (s.getItem() instanceof StaffItem
                && "iron_block".equals(s.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"))) {
                return true;
            }
        }
        return false;
    }

    /** 点 p 到线段 a→b 的最短距离。 */
    private static double distToSegment(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1.0E-8) return p.distanceTo(a);
        double t = p.subtract(a).dot(ab) / len2;
        t = Math.max(0.0, Math.min(1.0, t));
        Vec3 proj = a.add(ab.scale(t));
        return p.distanceTo(proj);
    }

    /** 线段 a→b 是否与 AABB 盒子相交（slab 法，含端点）。用于铁链射线命中判定。 */
    private static boolean segmentHitsBox(Vec3 a, Vec3 b, AABB box) {
        Vec3 d = b.subtract(a);
        double tmin = 0.0, tmax = 1.0;
        double[] o = {a.x, a.y, a.z};
        double[] dv = {d.x, d.y, d.z};
        double[] lo = {box.minX, box.minY, box.minZ};
        double[] hi = {box.maxX, box.maxY, box.maxZ};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(dv[i]) < 1.0E-8) {
                if (o[i] < lo[i] || o[i] > hi[i]) return false;
            } else {
                double t1 = (lo[i] - o[i]) / dv[i];
                double t2 = (hi[i] - o[i]) / dv[i];
                if (t1 > t2) {
                    double tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                tmin = Math.max(tmin, t1);
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) return false;
            }
        }
        return true;
    }

    /** 铁块权杖：左键中断（ChainCancelPayload）时断开钩取，目标以当前摆锤速度惯性甩出。 */
    public static void cancelChainGrab(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        ChainGrabState st = CHAIN_GRABS.remove(player.getUUID());
        if (st != null) {
            flingChainTarget(sp, st);
            sp.connection.send(new cn.autoforged.joes_addons_for_abmc.network.ChainGrabStopPayload());
        }
    }

    /** 断开铁链时把被钩生物甩出：把摆锤当前速度（甩动惯性）转移到实体真实速度，
     *  实现“甩出去”的飞出效果，而不是垂直自由落体；物品模式无需处理。 */
    private static void flingChainTarget(ServerPlayer player, ChainGrabState st) {
        if (st.mode != cn.autoforged.joes_addons_for_abmc.network.ChainGrabPayload.MODE_LIVING) return;
        Entity target = player.serverLevel().getEntity(st.targetId);
        if (target == null || !target.isAlive()) return;
        target.setDeltaMovement(st.pendVel);
        target.hasImpulse = true;
    }

    /** 蜘蛛网权杖：持有者左键按下，主动断开当前拉扯的蛛丝。 */
    public static void handleCobwebDisconnect(Player player) {
        java.util.UUID id = player.getUUID();
        if (COBWEB_PULLING.remove(id) != null && player instanceof ServerPlayer sp) {
            recordCobwebPullEnd(id);
            sp.connection.send(new CobwebPullStopPayload()); // 清除客户端蛛丝线段
        }
    }

    /** 蜘蛛网权杖：中键优先用于解除瞄准实体的权杖无效化；已解除/未命中时返回 false。 */
    private static boolean handleCobwebMiddleClick(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return false;
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 end = eye.add(player.getLookAngle().scale(9.0));
        Entity hit = null;
        double best = Double.MAX_VALUE;
        for (Entity e : serverLevel.getEntities(player, new AABB(eye, end).inflate(1.0),
            e -> e instanceof LivingEntity && e.isAlive() && e.isPickable())) {
            Vec3 clipPos = e.getBoundingBox().inflate(0.3).clip(eye, end).orElse(null);
            EntityHitResult ehr = clipPos != null ? new EntityHitResult(e, clipPos) : null;
            if (ehr == null) continue;
            double d = eye.distanceTo(ehr.getLocation());
            if (d < best) {
                best = d;
                hit = e;
            }
        }
        if (hit == null) return false;
        CobwebNullifyState st = COBWEB_NULLIFIED.get(hit.getUUID());
        if (st == null) return false;
        st.clickCount++;
        serverLevel.playSound(null, hit.getX(), hit.getY(), hit.getZ(),
            Blocks.COBWEB.defaultBlockState().getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        if (st.clickCount >= COBWEB_CANCEL_CLICKS) {
            COBWEB_NULLIFIED.remove(hit.getUUID());
            broadcastCobwebClear(hit);
        }
        return true;
    }

    /** 判断生物当前是否持有蜘蛛网权杖（任一手）。 */
    public static boolean isHoldingCobwebStaff(net.minecraft.world.entity.LivingEntity holder) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = holder.getItemInHand(hand);
            if (s.getItem() instanceof StaffItem
                && "cobweb".equals(s.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"))) {
                return true;
            }
        }
        return false;
    }

    /** 判断生物当前是否持有冰块权杖（任一手）。 */
    public static boolean isHoldingIceStaff(net.minecraft.world.entity.LivingEntity holder) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = holder.getItemInHand(hand);
            if (s.getItem() instanceof StaffItem
                && "ice".equals(s.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"))) {
                return true;
            }
        }
        return false;
    }

    /** 判断玩家（作为被无效化的实体的持有者）权杖当前是否被无效化。 */
    public static boolean isPlayerStaffNullified(Player player) {
        return isStaffNullified(player);
    }

    /** 通用判断：任意生物持有的权杖当前是否被无效化（适用于玩家、女仆及其它权杖持有生物）。 */
    public static boolean isStaffNullified(net.minecraft.world.entity.LivingEntity holder) {
        return holder != null && COBWEB_NULLIFIED.containsKey(holder.getUUID());
    }

    /**
     * 功能层无效化：返回权杖“实际生效”的 blockType。
     * 若持有者当前处于无效化状态，则视作 blocktype 为 empty（白板权杖）——
     * 只保留基础近战攻击，失去权杖自身的全部特性（岩浆引燃、击退、末地传送门等特殊效果均不触发）。
     */
    private static String effectiveStaffBlockType(ItemStack weapon,
                                                  net.minecraft.world.entity.LivingEntity holder) {
        String bt = weapon.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        if (!"empty".equals(bt) && isStaffNullified(holder)) {
            return "empty";
        }
        return bt;
    }

    /** 是否为摔落/动能类伤害：包括摔落（fall、石笋）以及撞墙、落块、落砧、落钟乳石等动能伤害。 */
    private static boolean isFallKineticDamage(net.minecraft.world.damagesource.DamageSource source) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) return true;
        var key = source.typeHolder().unwrapKey();
        if (key.isPresent()) {
            String path = key.get().location().getPath();
            return "falling_block".equals(path) || "falling_anvil".equals(path)
                || "falling_stalactite".equals(path) || "fly_into_wall".equals(path);
        }
        return false;
    }

    /**
     * 滴水石权杖：右键方块【顶面】，召唤自下而上的滴水石锥下落方块群组。
     *
     * 群组为柱形（横截面 1×1），自下而上 thickness 依次为 base/middle/frustum/tip
     * （空间不足时裁剪：4=base,middle,frustum,tip；3=base,frustum,tip；2=frustum,tip；1=tip），
     * vertical direction 均为 up，以 15±1 格/秒的初速度向上射出。
     * 群组上升时无视周围方块；当 base 即将离开地表时，整组回落到地表位置固化成滴水石柱。
     * 飞行中任意一块接触生物时，造成 |竖向速度差|*5 的动能伤害并把生物上抛 "上升速度-0.5"。
     */
    public static void executeDripstoneStaffAbility(Player player, ItemStack stack, InteractionHand hand) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();

        BlockHitResult hitResult = serverLevel.clip(
            new ClipContext(eyePos, eyePos.add(lookVec.scale(7.0)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hitResult.getType() != HitResult.Type.BLOCK) return;
        if (eyePos.distanceTo(hitResult.getLocation()) > 7.0) return;
        // 只能右击方块的顶面
        if (hitResult.getDirection() != Direction.UP) {
            player.displayClientMessage(Component.translatable("message.joes_addons_for_abmc.dripstone_require_top"), true);
            return;
        }

        BlockPos anchor = hitResult.getBlockPos();

        // 统计上方连续空间（最多 4 格）
        int space = 0;
        for (int i = 1; i <= 4; i++) {
            BlockState check = serverLevel.getBlockState(anchor.above(i));
            if (check.isAir() || check.canBeReplaced()) {
                space++;
            } else {
                break;
            }
        }
        if (space < 1) {
            player.displayClientMessage(Component.translatable("message.joes_addons_for_abmc.dripstone_no_space"), true);
            return;
        }
        int count = Math.min(space, 4);

        // 自下而上的 thickness 列表（顶部始终为 TIP）
        java.util.List<DripstoneThickness> bottomUp = switch (count) {
            case 4 -> java.util.List.of(DripstoneThickness.BASE, DripstoneThickness.MIDDLE, DripstoneThickness.FRUSTUM, DripstoneThickness.TIP);
            case 3 -> java.util.List.of(DripstoneThickness.BASE, DripstoneThickness.FRUSTUM, DripstoneThickness.TIP);
            case 2 -> java.util.List.of(DripstoneThickness.FRUSTUM, DripstoneThickness.TIP);
            default -> java.util.List.of(DripstoneThickness.TIP);
        };

        RandomSource random = serverLevel.getRandom();
        int groupId = random.nextInt();
        double launch = 15.0 + (random.nextDouble() * 2.0 - 1.0); // 15 ± 1 格/秒

        // 先创建并加入 base（领袖），好让其它成员引用它的实体 id
        int columnCount = bottomUp.size();
        DripstoneFallingBlockEntity leader = createDripstoneMember(serverLevel, anchor, groupId, 0,
            bottomUp.get(0), launch, player.getId(), columnCount);
        serverLevel.addFreshEntity(leader);
        leader.leaderId = leader.getId();

        for (int i = 1; i < bottomUp.size(); i++) {
            DripstoneFallingBlockEntity member = createDripstoneMember(serverLevel, anchor, groupId, i,
                bottomUp.get(i), launch, player.getId(), columnCount);
            member.leaderId = leader.getId();
            serverLevel.addFreshEntity(member);
        }

        serverLevel.playSound(null, anchor.getX() + 0.5, anchor.getY() + 1.5, anchor.getZ() + 0.5,
            SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.PLAYERS, 1.0F, 1.2F);

        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        hurtStaff(stack, 1, player, slot);
    }

    /** 创建单个群组成员（columnIndex 0 为 base）并放置到锚点上方对应格 */
    private static DripstoneFallingBlockEntity createDripstoneMember(ServerLevel level, BlockPos anchor,
        int groupId, int columnIndex, DripstoneThickness thickness, double launch, int summonerId, int columnCount) {
        BlockState state = net.minecraft.world.level.block.Blocks.POINTED_DRIPSTONE.defaultBlockState()
            .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP)
            .setValue(PointedDripstoneBlock.THICKNESS, thickness);

        DripstoneFallingBlockEntity entity = new DripstoneFallingBlockEntity(
            ModEntities.DRIPSTONE_FALLING_BLOCK.get(), level);
        entity.initFromState(state);
        entity.groupId = groupId;
        entity.columnIndex = columnIndex;
        entity.launchSpeedBlkPerSec = launch;
        entity.anchorX = anchor.getX();
        entity.anchorY = anchor.getY();
        entity.anchorZ = anchor.getZ();
        entity.summonerId = summonerId;
        entity.riseHeight = columnCount;
        entity.vy = launch / 20.0;

        // 初始位置：整柱整体下移 (columnCount-1) 格，使 tip 恰好贴住方块上表面，
        // 之后整柱恒定上移，直到 base 到达方块上表面时固化（“从地里钻出来”）。
        double y = anchor.getY() + columnIndex - columnCount + 2;
        entity.setPos(anchor.getX() + 0.5, y, anchor.getZ() + 0.5);
        entity.setDeltaMovement(0.0, launch / 20.0, 0.0);
        entity.setClientVy(entity.vy);
        return entity;
    }

    /**
     * 屏障权杖：左右键同时按下时触发“整体平移屏障群”。
     *
     * 流程：
     * 1. 判定玩家朝向，映射到 26 种可能方向之一（上下/东南西北 + 各类斜角，即 6+12+8=26）。
     * 2. 沿视线前方寻找到第一块屏障方块。
     * 3. 以该方块为起点 BFS 连锁选中相邻的屏障方块（数量上限 20）。
     * 4. 将整群屏障朝该方向整体平移一格，并破坏平移目标处的任意方块（含命令方块、基岩、
     *    屏障本身），播放破坏动画。
     */
    private static void handleBarrierShift(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        // 进入整体平移模式，抑制随后的普通放置/破坏（持续若干 tick）
        barrierShiftSuppress.put(player.getUUID(), 12);

        // 1. 判定朝向（26 方向）
        int[] dir = closest26Direction(player.getLookAngle());

        // 2. 沿视线前方找到第一块屏障方块
        BlockPos start = findBarrierInFront(serverLevel, player);
        if (start == null) return;

        // 3. BFS 连锁选中相邻屏障群（上限 20）
        Set<BlockPos> group = collectBarrierGroup(serverLevel, start, 20);
        if (group.isEmpty()) return;

        // 4. 整体平移一格并破坏目标方块
        moveBarrierGroup(serverLevel, group, dir);
    }

    /**
     * 将归一化视线向量近似到 26 种方向之一（分量均为 -1/0/1 且不全为 0）。
     * 通过比较视线向量与各归一化方向向量的点积，取最接近者。
     */
    private static int[] closest26Direction(Vec3 look) {
        int[] best = new int[]{0, 0, 0};
        double bestDot = -Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double dot = (look.x * dx + look.y * dy + look.z * dz) / len;
                    if (dot > bestDot) {
                        bestDot = dot;
                        best = new int[]{dx, dy, dz};
                    }
                }
            }
        }
        return best;
    }

    /**
     * 沿玩家视线方向逐点扫描，找到第一块屏障方块。
     */
    private static BlockPos findBarrierInFront(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double maxDist = 32.0;
        for (double t = 0.0; t <= maxDist; t += 0.5) {
            BlockPos pos = BlockPos.containing(eye.add(look.scale(t)));
            if (level.getBlockState(pos).is(Blocks.BARRIER)) {
                return pos;
            }
        }
        return null;
    }

    /**
     * 从起点 BFS 连锁选中相邻的屏障方块（六方向连通），数量上限为 maxSize。
     */
    private static Set<BlockPos> collectBarrierGroup(ServerLevel level, BlockPos start, int maxSize) {
        Set<BlockPos> group = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        group.add(start);
        queue.add(start);
        while (!queue.isEmpty() && group.size() < maxSize) {
            BlockPos pos = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos neighbor = pos.relative(d);
                if (group.contains(neighbor)) continue;
                if (level.getBlockState(neighbor).is(Blocks.BARRIER)) {
                    group.add(neighbor);
                    if (group.size() >= maxSize) break;
                    queue.add(neighbor);
                }
            }
        }
        return group;
    }

    /**
     * 将整群屏障朝 dir 方向整体平移一格，并破坏平移目标处的任意方块（播放破坏动画）。
     */
    private static void moveBarrierGroup(ServerLevel level, Set<BlockPos> group, int[] dir) {
        Set<BlockPos> destinations = new HashSet<>();
        for (BlockPos pos : group) {
            destinations.add(pos.offset(dir[0], dir[1], dir[2]));
        }

        // 1. 破坏平移目标处的方块（含命令方块、基岩、屏障等），播放破坏动画
        for (BlockPos dest : destinations) {
            if (!group.contains(dest)) {
                level.destroyBlock(dest, false);
            }
        }

        // 2. 清空原位置
        for (BlockPos pos : group) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }

        // 3. 在目标处放置屏障
        for (BlockPos dest : destinations) {
            level.setBlock(dest, Blocks.BARRIER.defaultBlockState(), 3);
            level.playSound(null, dest.getX() + 0.5, dest.getY() + 0.5, dest.getZ() + 0.5,
                Blocks.BARRIER.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    public static void executeLapisStaffAbility(Player player, ItemStack stack, InteractionHand hand) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();

        boolean isGrabbing = LAPIS_GRABBED_ENTITIES.containsKey(player.getUUID());

        if (isGrabbing) {
            launchGrabbedEntity(player, serverLevel);
            return;
        }

        FallingBlockEntity targetFallingBlock = getFallingBlockInCrosshair(player, 20.0);
        if (targetFallingBlock != null && !(targetFallingBlock instanceof LapisFallingBlockEntity lfe && lfe.getLapisState() == LapisFallingBlockEntity.STATE_LAUNCHED)) {
            if (player.totalExperience < 5) return;

            player.giveExperiencePoints(-5);
            int grabbedId = targetFallingBlock.getId();
            LAPIS_GRABBED_ENTITIES.put(player.getUUID(), grabbedId);
            LAPIS_GRAB_XP_TIMERS.put(player.getUUID(), player.tickCount);
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F, 1.5F);
            return;
        }

        BlockHitResult hitResult = serverLevel.clip(
            new ClipContext(eyePos, eyePos.add(lookVec.scale(20.0)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hitResult.getType() != HitResult.Type.BLOCK) return;
        if (eyePos.distanceTo(hitResult.getLocation()) > 20.0) return;

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = serverLevel.getBlockState(pos);
        if (state.isAir()) return;
        if (state.getDestroySpeed(serverLevel, pos) < 0) return;

        if (player.totalExperience < 5) return;

        player.giveExperiencePoints(-5);

        LapisFallingBlockEntity entity = new LapisFallingBlockEntity(
            ModEntities.LAPIS_FALLING_BLOCK.get(), serverLevel);
        entity.initFromBlock(serverLevel, pos, state);
        entity.setLapisState(LapisFallingBlockEntity.STATE_FLOATING);
        entity.setHasGlint(true);
        entity.setDeltaMovement(0, 0.05, 0);
        serverLevel.addFreshEntity(entity);

        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        hurtStaff(stack, 1, player, slot);
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F, 1.2F);
    }

    private static FallingBlockEntity getFallingBlockInCrosshair(Player player, double maxDistance) {
        Level level = player.level();
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(maxDistance));
        AABB searchBox = new AABB(eyePos, endPos).inflate(1.0);
        FallingBlockEntity closest = null;
        double closestDist = maxDistance;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, searchBox, e -> e instanceof FallingBlockEntity && !(e instanceof LapisFallingBlockEntity lfe && lfe.getLapisState() == LapisFallingBlockEntity.STATE_LAUNCHED))) {
            AABB entityBox = entity.getBoundingBox().inflate(0.3);
            var hit = entityBox.clip(eyePos, endPos);
            if (hit.isPresent()) {
                double dist = eyePos.distanceTo(hit.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = (FallingBlockEntity) entity;
                }
            }
        }
        return closest;
    }

    private static void launchGrabbedEntity(Player player, ServerLevel serverLevel) {
        Integer entityId = LAPIS_GRABBED_ENTITIES.remove(player.getUUID());
        LAPIS_GRAB_XP_TIMERS.remove(player.getUUID());
        if (entityId == null) return;

        Entity grabbed = serverLevel.getEntity(entityId);
        if (grabbed == null || grabbed.isRemoved()) return;

        BlockState state;
        BlockPos startPos;
        if (grabbed instanceof FallingBlockEntity fbe) {
            state = fbe.getBlockState();
            startPos = fbe.getStartPos();
        } else {
            state = Blocks.STONE.defaultBlockState();
            startPos = grabbed.blockPosition();
        }

        LapisFallingBlockEntity launched = new LapisFallingBlockEntity(
            ModEntities.LAPIS_FALLING_BLOCK.get(), serverLevel);
        launched.initFromBlockState(state);
        launched.setStartPos(startPos);
        launched.setPos(grabbed.position());
        launched.xo = launched.getX();
        launched.yo = launched.getY();
        launched.zo = launched.getZ();
        launched.setLapisState(LapisFallingBlockEntity.STATE_LAUNCHED);
        launched.setHasGlint(false);
        launched.launchDirection = player.getLookAngle().normalize();
        launched.launchSpeed = 1.5;
        launched.setDeltaMovement(launched.launchDirection.scale(launched.launchSpeed));

        if (grabbed instanceof LapisFallingBlockEntity lfe) {
            lfe.grabbed = false;
        }
        grabbed.discard();
        serverLevel.addFreshEntity(launched);

        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F, 0.8F);
    }

    private static void handleLapisGrabTick(ServerLevel level, Player player) {
        Integer entityId = LAPIS_GRABBED_ENTITIES.get(player.getUUID());
        if (entityId == null) return;

        Entity grabbed = level.getEntity(entityId);
        if (grabbed == null || grabbed.isRemoved()) {
            LAPIS_GRABBED_ENTITIES.remove(player.getUUID());
            LAPIS_GRAB_XP_TIMERS.remove(player.getUUID());
            if (grabbed instanceof LapisFallingBlockEntity lfe) {
                lfe.grabbed = false;
                lfe.setNoGravity(false);
            } else if (grabbed != null) {
                grabbed.setNoGravity(false);
            }
            return;
        }

        boolean holdingLapis = false;
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        if (mainHand.getItem() instanceof StaffItem) {
            String bt = mainHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
            if ("lapis_block".equals(bt)) holdingLapis = true;
        }
        if (!holdingLapis && offHand.getItem() instanceof StaffItem) {
            String bt = offHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
            if ("lapis_block".equals(bt)) holdingLapis = true;
        }
        if (!holdingLapis) {
            LAPIS_GRABBED_ENTITIES.remove(player.getUUID());
            LAPIS_GRAB_XP_TIMERS.remove(player.getUUID());
            if (grabbed instanceof LapisFallingBlockEntity lfe) {
                lfe.grabbed = false;
                lfe.setNoGravity(false);
            } else {
                grabbed.setNoGravity(false);
            }
            return;
        }

        if (grabbed instanceof LapisFallingBlockEntity lfe) {
            lfe.grabbed = true;
            lfe.setNoGravity(true);
        } else {
            grabbed.setNoGravity(true);
        }

        Vec3 targetPos = player.getEyePosition().add(player.getLookAngle().scale(7.0));
        // Use direct teleportation for smooth movement, bypassing physics collision
        grabbed.setPos(targetPos.x, targetPos.y, targetPos.z);
        grabbed.setDeltaMovement(Vec3.ZERO);
        // Force client-side position sync every tick: the entity tracker doesn't detect
        // position changes when setPos is called before the entity tick, so we manually
        // broadcast a teleport entity packet to all nearby players
        net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket syncPacket =
            new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(grabbed);
        for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(grabbed) < 256.0 * 256.0)) {
            p.connection.send(syncPacket);
        }

        if (grabbed instanceof FallingBlockEntity fbe) {
            fbe.time = 0;
        }

        Integer lastDrain = LAPIS_GRAB_XP_TIMERS.get(player.getUUID());
        if (lastDrain != null && player.tickCount - lastDrain >= 20) {
            if (player.experienceLevel > 0 || player.experienceProgress > 0) {
                player.giveExperiencePoints(-1);
                LAPIS_GRAB_XP_TIMERS.put(player.getUUID(), player.tickCount);
            } else {
                LAPIS_GRABBED_ENTITIES.remove(player.getUUID());
                LAPIS_GRAB_XP_TIMERS.remove(player.getUUID());
                if (grabbed instanceof LapisFallingBlockEntity lfe) {
                    lfe.grabbed = false;
                    lfe.setNoGravity(false);
                } else {
                    grabbed.setNoGravity(false);
                }
            }
        }
    }

    public static void executeBoneStaffAbility(Player player, ItemStack stack, InteractionHand hand) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();

        BlockHitResult hitResult = serverLevel.clip(
            new ClipContext(eyePos, eyePos.add(lookVec.scale(7.0)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        if (hitResult.getType() != HitResult.Type.BLOCK) return;

        Vec3 sphereCenter = hitResult.getLocation();
        BlockPos contactBlockPos = hitResult.getBlockPos();

        boolean allowGrassNylium = false;
        boolean contactIsFlowerOrLily = false;
        if (contactBlockPos != null) {
            BlockState contactState = serverLevel.getBlockState(contactBlockPos);
            if (contactState.is(Blocks.GRASS_BLOCK) || contactState.is(Blocks.WARPED_NYLIUM)
                || contactState.is(Blocks.CRIMSON_NYLIUM)) {
                allowGrassNylium = true;
            }
            if (contactState.getBlock() instanceof FlowerBlock || contactState.is(Blocks.LILY_PAD)) {
                contactIsFlowerOrLily = true;
            }
        }

        double radius = 1.5;
        int minX = Mth.floor(sphereCenter.x - radius);
        int maxX = Mth.ceil(sphereCenter.x + radius);
        int minY = Mth.floor(sphereCenter.y - radius);
        int maxY = Mth.ceil(sphereCenter.y + radius);
        int minZ = Mth.floor(sphereCenter.z - radius);
        int maxZ = Mth.ceil(sphereCenter.z + radius);

        double radiusSq = radius * radius;

        java.util.List<BlockPos> sugarCanePositions = new java.util.ArrayList<>();
        java.util.List<BlockPos> cactusPositions = new java.util.ArrayList<>();
        java.util.List<BlockPos> netherWartPositions = new java.util.ArrayList<>();
        java.util.List<BlockPos> underwaterFullBlocks = new java.util.ArrayList<>();
        java.util.List<BlockPos> regularTargets = new java.util.ArrayList<>();
        java.util.Map<BlockPos, Integer> duplicationTargets = new java.util.LinkedHashMap<>();
        java.util.Set<BlockPos> alreadySeen = new java.util.HashSet<>();

        java.util.List<BlockPos> stemPositions = new java.util.ArrayList<>();
        java.util.List<BlockPos> mushroomPositions = new java.util.ArrayList<>();
        java.util.List<BlockPos> flowerLilyPositions = new java.util.ArrayList<>();
        java.util.List<BlockPos> leafPositions = new java.util.ArrayList<>();
        java.util.List<BlockPos> chorusFlowerPositions = new java.util.ArrayList<>();
        java.util.List<BlockPos> mossyPositions = new java.util.ArrayList<>();
        java.util.List<BlockPos> mossCarpetPositions = new java.util.ArrayList<>();
        java.util.List<BlockPos> grassSpreadPositions = new java.util.ArrayList<>();
        java.util.List<BlockPos> myceliumSpreadPositions = new java.util.ArrayList<>();
        java.util.List<BlockPos> sculkCatalystPositions = new java.util.ArrayList<>();

        boolean playerUnderwater = player.isInWater();

        if (playerUnderwater && contactBlockPos != null) {
            boolean anyAffected = false;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = contactBlockPos.offset(dx, 0, dz);
                    BlockState state = serverLevel.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (processBoneStaffBlock(serverLevel, pos, state)) {
                        anyAffected = true;
                    }
                }
            }
            if (anyAffected) {
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
                EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                hurtStaff(stack, 1, player, slot);
            }
            return;
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!alreadySeen.add(pos)) continue;

                    BlockState state = serverLevel.getBlockState(pos);
                    if (state.isAir()) continue;

                    Vec3 blockCenter = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    if (blockCenter.distanceToSqr(sphereCenter) > radiusSq) continue;

                    Block block = state.getBlock();

                    if (!allowGrassNylium) {
                        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.WARPED_NYLIUM)
                            || state.is(Blocks.CRIMSON_NYLIUM)) {
                            continue;
                        }
                    }

                    if (state.is(Blocks.SUGAR_CANE)) {
                        sugarCanePositions.add(pos);
                        continue;
                    }

                    if (state.is(Blocks.CACTUS)) {
                        cactusPositions.add(pos);
                        continue;
                    }

                    if (state.is(Blocks.NETHER_WART)) {
                        netherWartPositions.add(pos);
                        continue;
                    }

                    if (state.is(Blocks.MELON_STEM) || state.is(Blocks.PUMPKIN_STEM)
                        || state.is(Blocks.ATTACHED_MELON_STEM) || state.is(Blocks.ATTACHED_PUMPKIN_STEM)) {
                        stemPositions.add(pos);
                        continue;
                    }

                    if (block instanceof MushroomBlock) {
                        mushroomPositions.add(pos);
                        continue;
                    }

                    if (contactIsFlowerOrLily && (block instanceof FlowerBlock || state.is(Blocks.LILY_PAD))) {
                        flowerLilyPositions.add(pos);
                        continue;
                    }

                    if (state.is(BlockTags.LEAVES) && !state.is(Blocks.FLOWERING_AZALEA_LEAVES)) {
                        leafPositions.add(pos);
                        continue;
                    }

                    if (state.is(Blocks.CHORUS_FLOWER)) {
                        chorusFlowerPositions.add(pos);
                        continue;
                    }

                    if (isMossyBlock(state)) {
                        mossyPositions.add(pos);
                        continue;
                    }

                    if (state.is(Blocks.MOSS_CARPET)) {
                        mossCarpetPositions.add(pos);
                        continue;
                    }

                    if (state.is(Blocks.GRASS_BLOCK)) {
                        if (hasDirtNearby(serverLevel, pos)) {
                            grassSpreadPositions.add(pos);
                        } else {
                            regularTargets.add(pos);
                        }
                        continue;
                    }

                    if (state.is(Blocks.MYCELIUM)) {
                        if (hasDirtNearby(serverLevel, pos)) {
                            myceliumSpreadPositions.add(pos);
                        } else {
                            regularTargets.add(pos);
                        }
                        continue;
                    }

                    if (state.is(Blocks.SCULK_CATALYST)) {
                        sculkCatalystPositions.add(pos);
                        continue;
                    }

                    if (isUnderwaterFullBlock(serverLevel, pos, state)) {
                        underwaterFullBlocks.add(pos);
                        continue;
                    }

                    if (block instanceof BonemealableBlock bonemealable
                        && bonemealable.isValidBonemealTarget(serverLevel, pos, state)) {

                        if (isDuplicationPlant(block)) {
                            duplicationTargets.put(pos, 5);
                        } else {
                            regularTargets.add(pos);
                        }
                    }
                }
            }
        }

        int immediateCount = 0;

        java.util.Set<BlockPos> caneTops = findSugarCaneTops(serverLevel, sugarCanePositions);
        for (BlockPos top : caneTops) {
            BlockPos above = top.above();
            if (serverLevel.getBlockState(above).isAir()) {
                serverLevel.setBlock(above, Blocks.SUGAR_CANE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        java.util.Set<BlockPos> cactusTops = findCactusTops(serverLevel, cactusPositions);
        for (BlockPos top : cactusTops) {
            BlockPos above = top.above();
            if (serverLevel.getBlockState(above).isAir() && canCactusSurviveAt(serverLevel, above)) {
                serverLevel.setBlock(above, Blocks.CACTUS.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        for (BlockPos pos : netherWartPositions) {
            BlockState state = serverLevel.getBlockState(pos);
            serverLevel.setBlock(pos, state.setValue(NetherWartBlock.AGE, 3), Block.UPDATE_ALL);
        }

        java.util.Set<BlockPos> allSeagrassFullBlocks = new java.util.HashSet<>();
        for (BlockPos target : underwaterFullBlocks) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos checkPos = target.offset(dx, dy, dz);
                        BlockState checkState = serverLevel.getBlockState(checkPos);
                        if (checkState.isCollisionShapeFullBlock(serverLevel, checkPos)
                            && !checkState.hasBlockEntity()) {
                            allSeagrassFullBlocks.add(checkPos);
                        }
                    }
                }
            }
        }

        immediateCount += placeSeagrassOnFullBlocks(serverLevel, allSeagrassFullBlocks);

        for (BlockPos pos : stemPositions) {
            BlockState state = serverLevel.getBlockState(pos);
            processStemWithFruitSpawn(serverLevel, pos, state);
            immediateCount++;
        }

        for (BlockPos pos : mushroomPositions) {
            BlockState state = serverLevel.getBlockState(pos);
            if (state.getBlock() instanceof MushroomBlock mushroomBlock) {
                mushroomBlock.growMushroom(serverLevel, pos, state, serverLevel.random);
            }
            immediateCount++;
        }

        for (BlockPos pos : flowerLilyPositions) {
            BlockState state = serverLevel.getBlockState(pos);
            spreadFlower(serverLevel, pos, state);
            immediateCount++;
        }

        for (BlockPos pos : leafPositions) {
            BlockState state = serverLevel.getBlockState(pos);
            dropSaplingFromLeaf(serverLevel, pos, state);
            immediateCount++;
        }

        for (BlockPos pos : chorusFlowerPositions) {
            BlockState state = serverLevel.getBlockState(pos);
            growChorusFlower(serverLevel, pos, state);
            immediateCount++;
        }

        for (BlockPos pos : mossyPositions) {
            spreadMoss(serverLevel, pos);
            immediateCount++;
        }

        for (BlockPos pos : mossCarpetPositions) {
            serverLevel.setBlock(pos, Blocks.MOSS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            immediateCount++;
        }

        for (BlockPos pos : grassSpreadPositions) {
            spreadGrassOrMycelium(serverLevel, pos, Blocks.GRASS_BLOCK);
            immediateCount++;
        }

        for (BlockPos pos : myceliumSpreadPositions) {
            spreadGrassOrMycelium(serverLevel, pos, Blocks.MYCELIUM);
            immediateCount++;
        }

        for (BlockPos pos : sculkCatalystPositions) {
            triggerSculkCatalyst(serverLevel, pos);
            immediateCount++;
        }

        int totalAffected = sugarCanePositions.size() + cactusPositions.size()
            + netherWartPositions.size() + underwaterFullBlocks.size()
            + regularTargets.size() + duplicationTargets.size()
            + stemPositions.size() + mushroomPositions.size()
            + flowerLilyPositions.size() + leafPositions.size()
            + chorusFlowerPositions.size() + mossyPositions.size()
            + mossCarpetPositions.size()
            + grassSpreadPositions.size() + myceliumSpreadPositions.size()
            + sculkCatalystPositions.size();

        if (totalAffected == 0) return;

        for (int i = 0; i < totalAffected; i++) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        hurtStaff(stack, totalAffected, player, slot);

        if (!regularTargets.isEmpty() || !duplicationTargets.isEmpty()) {
            BonemealTask task = new BonemealTask();
            task.level = serverLevel;
            task.regularTargets = regularTargets;
            task.duplicationTargets = duplicationTargets;
            PENDING_BONEMEAL_TASKS.add(task);
        }
    }

    public static void executeFurnaceStaffAbility(Player player, ItemStack stack, InteractionHand hand) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();

        BlockHitResult hitResult = serverLevel.clip(
            new ClipContext(eyePos, eyePos.add(lookVec.scale(7.0)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        if (hitResult.getType() != HitResult.Type.BLOCK) return;

        Vec3 sphereCenter = hitResult.getLocation();
        if (eyePos.distanceTo(sphereCenter) > 7.0) return;

        BlockPos hitPos = hitResult.getBlockPos();
        BlockState hitState = serverLevel.getBlockState(hitPos);
        BlockEntity be = serverLevel.getBlockEntity(hitPos);

        if (be instanceof AbstractFurnaceBlockEntity furnaceBe) {
            handleFurnaceBlockInteraction(player, stack, hand, serverLevel, hitPos, hitState, furnaceBe);
            return;
        }

        double radius = 1.5;
        double radiusSq = radius * radius;

        AABB sphere = new AABB(
            sphereCenter.x - radius, sphereCenter.y - radius, sphereCenter.z - radius,
            sphereCenter.x + radius, sphereCenter.y + radius, sphereCenter.z + radius);

        java.util.List<ItemEntity> toSmelt = new java.util.ArrayList<>();
        for (ItemEntity itemEntity : serverLevel.getEntitiesOfClass(ItemEntity.class, sphere)) {
            if (SMELT_COOLDOWNS.containsKey(itemEntity.getUUID())) continue;
            if (itemEntity.position().distanceToSqr(sphereCenter) > radiusSq) continue;
            toSmelt.add(itemEntity);
        }

        if (toSmelt.isEmpty()) return;

        int smeltedCount = 0;
        float totalXp = 0;
        net.minecraft.world.item.crafting.RecipeManager recipeManager = serverLevel.getRecipeManager();

        for (ItemEntity itemEntity : toSmelt) {
            ItemStack itemStack = itemEntity.getItem();
            int count = itemStack.getCount();
            ItemStack result = ItemStack.EMPTY;
            float xp = 0;

            java.util.Optional<RecipeHolder<SmeltingRecipe>> smeltResult =
                recipeManager.getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(itemStack), serverLevel);
            if (smeltResult.isPresent()) {
                result = smeltResult.get().value().getResultItem(serverLevel.registryAccess());
                xp = smeltResult.get().value().getExperience();
            }

            if (result.isEmpty()) {
                java.util.Optional<RecipeHolder<BlastingRecipe>> blastResult =
                    recipeManager.getRecipeFor(RecipeType.BLASTING, new SingleRecipeInput(itemStack), serverLevel);
                if (blastResult.isPresent()) {
                    result = blastResult.get().value().getResultItem(serverLevel.registryAccess());
                    xp = blastResult.get().value().getExperience();
                }
            }

            if (result.isEmpty()) {
                java.util.Optional<RecipeHolder<SmokingRecipe>> smokeResult =
                    recipeManager.getRecipeFor(RecipeType.SMOKING, new SingleRecipeInput(itemStack), serverLevel);
                if (smokeResult.isPresent()) {
                    result = smokeResult.get().value().getResultItem(serverLevel.registryAccess());
                    xp = smokeResult.get().value().getExperience();
                }
            }

            if (!result.isEmpty()) {
                ItemStack finalResult = result.copy();
                finalResult.setCount(result.getCount() * count);
                itemEntity.setItem(finalResult);
                itemEntity.setPickUpDelay(40);
                SMELT_COOLDOWNS.put(itemEntity.getUUID(), 10);
                smeltedCount++;
                totalXp += xp * count;
            }
        }

        if (smeltedCount > 0) {
            int xpOrbs = Math.round(totalXp);
            if (xpOrbs > 0) {
                ExperienceOrb.award(serverLevel, sphereCenter, xpOrbs);
            }
            EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            hurtStaff(stack, smeltedCount, player, slot);
        }
    }

    public static void executeMagmaStaffAbility(Player player, ItemStack stack, InteractionHand hand) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();

        Entity aimedEntity = getEntityInCrosshair(player, 7.0);

        if (player.isShiftKeyDown()) {
            if (aimedEntity != null) {
                BlockPos entityPos = aimedEntity.blockPosition().below();
                BlockState existingState = serverLevel.getBlockState(entityPos);
                if (existingState.isAir() || existingState.canBeReplaced()) {
                    serverLevel.setBlockAndUpdate(entityPos, Blocks.MAGMA_BLOCK.defaultBlockState());
                    serverLevel.playSound(null, entityPos, SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
                    EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                    hurtStaff(stack, 1, player, slot);
                }
                return;
            } else {
                SmallFireball fireball = new SmallFireball(serverLevel, player, lookVec);
                fireball.setPos(eyePos.x, eyePos.y - 0.2, eyePos.z);
                serverLevel.addFreshEntity(fireball);
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.5F, 1.0F);
                EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                hurtStaff(stack, 1, player, slot);
                return;
            }
        }

        LargeFireball fireball = new LargeFireball(serverLevel, player, lookVec, 1);
        fireball.setPos(eyePos.x, eyePos.y - 0.2, eyePos.z);
        fireball.setDeltaMovement(lookVec.normalize().scale(3.0 / 20.0));
        serverLevel.addFreshEntity(fireball);
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 0.5F, 1.0F);
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        hurtStaff(stack, 1, player, slot);
    }

    private static Entity getEntityInCrosshair(Player player, double maxDistance) {
        Level level = player.level();
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(maxDistance));
        AABB searchBox = new AABB(eyePos, endPos).inflate(1.0);
        Entity closest = null;
        double closestDist = maxDistance;
        for (Entity entity : level.getEntitiesOfClass(Entity.class, searchBox, e -> e instanceof LivingEntity && e != player)) {
            AABB entityBox = entity.getBoundingBox().inflate(0.3);
            var hit = entityBox.clip(eyePos, endPos);
            if (hit.isPresent()) {
                double dist = eyePos.distanceTo(hit.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = entity;
                }
            }
        }
        return closest;
    }

    /**
     * Right-click teleport of the Him staff: teleports the player to a random
     * point on the sphere (radius 2.5) around the nearest entity within 100 blocks
     * that the player is looking at, playing the ender pearl teleport sound, then
     * forces the player to look at the entity. Has a 10-tick cooldown.
     * If the first random spot is blocked, it keeps re-rolling; if no spot on the
     * sphere is free of blocks, the teleport fails and nothing happens.
     */
    public static void executeHerobrineTeleport(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        // Cooldown check (10 ticks)
        long currentTick = serverLevel.getGameTime();
        Long lastTeleportTick = HEROBRINE_TELEPORT_COOLDOWNS.get(player.getUUID());
        if (lastTeleportTick != null && currentTick - lastTeleportTick < 10) {
            return;
        }
        HEROBRINE_TELEPORT_COOLDOWNS.put(player.getUUID(), currentTick);

        Entity target = getEntityInCrosshair(player, 100.0);
        if (target == null) return;

        Vec3 center = target.getBoundingBox().getCenter();
        double radius = 2.5;
        Vec3 chosen = null;
        for (int attempt = 0; attempt < 256; attempt++) {
            double u = player.getRandom().nextDouble();
            double theta = player.getRandom().nextDouble() * 2.0 * Math.PI;
            double phi = Math.acos(2.0 * u - 1.0);
            Vec3 offset = new Vec3(Math.sin(phi) * Math.cos(theta), Math.cos(phi), Math.sin(phi) * Math.sin(theta)).scale(radius);
            Vec3 candidate = center.add(offset);
            if (isTeleportSpotClear(serverLevel, candidate)) {
                chosen = candidate;
                break;
            }
        }
        if (chosen == null) return;

        player.teleportTo(chosen.x, chosen.y, chosen.z);
        serverLevel.playSound(null, chosen.x, chosen.y, chosen.z,
            SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (player instanceof ServerPlayer sp) {
            sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                target.getBoundingBox().getCenter());
        }
    }

    private static boolean isTeleportSpotClear(ServerLevel level, Vec3 pos) {
        BlockPos p = BlockPos.containing(pos);
        BlockPos pAbove = p.above();
        BlockState state = level.getBlockState(p);
        BlockState stateAbove = level.getBlockState(pAbove);
        return (state.isAir() || state.getBlock() == net.minecraft.world.level.block.Blocks.WATER || state.getCollisionShape(level, p).isEmpty())
            && (stateAbove.isAir() || stateAbove.getBlock() == net.minecraft.world.level.block.Blocks.WATER || stateAbove.getCollisionShape(level, pAbove).isEmpty());
    }

    /**
     * Launches a single Herobrine head from the Him staff toward where the player
     * is looking. Called every 10 ticks while the right button is held past 20 ticks.
     */
    public static void executeHerobrineHeadShoot(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        Vec3 lookVec = player.getLookAngle();
        HerobrineHeadEntity skull = new HerobrineHeadEntity(serverLevel, player, lookVec.normalize());
        skull.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
        serverLevel.addFreshEntity(skull);
        serverLevel.levelEvent(null, 1024, player.blockPosition(), 0);
    }

    private static void handleFurnaceBlockInteraction(Player player, ItemStack stack, InteractionHand hand,
            ServerLevel serverLevel, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnaceBe) {
        Integer cooldownRemaining = FURNACE_BLOCK_COOLDOWNS.get(pos);
        if (cooldownRemaining != null && cooldownRemaining > 0) {
            return;
        }

        boolean isLit = state.hasProperty(AbstractFurnaceBlock.LIT) && state.getValue(AbstractFurnaceBlock.LIT);

        if (!isLit) {
            setFurnaceFieldInt(furnaceBe, "litTime", 16000);
            setFurnaceFieldInt(furnaceBe, "litDuration", 16000);
            serverLevel.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, true), Block.UPDATE_ALL);
            FURNACE_BLOCK_COOLDOWNS.put(pos, 10);
            serverLevel.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else if (player.isShiftKeyDown()) {
            // Sneaking: start continuous acceleration (+1 per tick, capped at totalTime - 2)
            FURNACE_ACCEL_MAP.put(pos, new FurnaceAccelInfo(player.getUUID(), player.level().dimension()));
            serverLevel.levelEvent(2003, pos, 0); // smoke particles
        } else {
            // Normal right-click: single boost
            int boostAmount;
            Block hitBlock = state.getBlock();
            if (hitBlock == Blocks.BLAST_FURNACE || hitBlock == Blocks.SMOKER) {
                boostAmount = 2;
            } else {
                boostAmount = 1;
            }
            int cookingProgress = getFurnaceFieldInt(furnaceBe, "cookingProgress");
            int cookingTotalTime = getFurnaceFieldInt(furnaceBe, "cookingTotalTime");
            int newProgress = cookingProgress + boostAmount;
            if (newProgress > cookingTotalTime) {
                newProgress = cookingTotalTime;
            }
            setFurnaceFieldInt(furnaceBe, "cookingProgress", newProgress);
            FURNACE_BLOCK_COOLDOWNS.put(pos, 1);
        }

        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        hurtStaff(stack, 1, player, slot);
    }

    private static int getFurnaceFieldInt(AbstractFurnaceBlockEntity be, String fieldName) {
        try {
            java.lang.reflect.Field field = AbstractFurnaceBlockEntity.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(be);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void setFurnaceFieldInt(AbstractFurnaceBlockEntity be, String fieldName, int value) {
        try {
            java.lang.reflect.Field field = AbstractFurnaceBlockEntity.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setInt(be, value);
        } catch (Exception ignored) {
        }
    }

    private static boolean isUnderwaterFullBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.isCollisionShapeFullBlock(level, pos)) return false;
        if (state.hasBlockEntity()) return false;
        BlockPos above = pos.above();
        return level.getBlockState(above).is(Blocks.WATER)
            && level.getFluidState(above).getAmount() == 8;
    }

    private static boolean isDuplicationPlant(Block block) {
        return block == Blocks.SUNFLOWER || block == Blocks.PINK_PETALS
            || block == Blocks.SEA_PICKLE || block == Blocks.TALL_GRASS
            || block == Blocks.LARGE_FERN;
    }

    private static boolean isMossyBlock(BlockState state) {
        return state.is(Blocks.MOSSY_COBBLESTONE)
            || state.is(Blocks.MOSSY_COBBLESTONE_SLAB)
            || state.is(Blocks.MOSSY_COBBLESTONE_STAIRS)
            || state.is(Blocks.MOSSY_COBBLESTONE_WALL)
            || state.is(Blocks.MOSSY_STONE_BRICKS)
            || state.is(Blocks.MOSSY_STONE_BRICK_SLAB)
            || state.is(Blocks.MOSSY_STONE_BRICK_STAIRS)
            || state.is(Blocks.MOSSY_STONE_BRICK_WALL);
    }

    private static boolean processBoneStaffBlock(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        if (state.is(Blocks.MELON_STEM) || state.is(Blocks.PUMPKIN_STEM)
            || state.is(Blocks.ATTACHED_MELON_STEM) || state.is(Blocks.ATTACHED_PUMPKIN_STEM)) {
            processStemWithFruitSpawn(serverLevel, pos, state);
        } else if (block instanceof MushroomBlock mushroomBlock) {
            mushroomBlock.growMushroom(serverLevel, pos, state, serverLevel.random);
        } else if (block instanceof FlowerBlock || state.is(Blocks.LILY_PAD)) {
            spreadFlower(serverLevel, pos, state);
        } else if (state.is(BlockTags.LEAVES) && !state.is(Blocks.FLOWERING_AZALEA_LEAVES)) {
            dropSaplingFromLeaf(serverLevel, pos, state);
        } else if (state.is(Blocks.CHORUS_FLOWER)) {
            growChorusFlower(serverLevel, pos, state);
        } else if (isMossyBlock(state)) {
            spreadMoss(serverLevel, pos);
        } else if (state.is(Blocks.MOSS_CARPET)) {
            serverLevel.setBlock(pos, Blocks.MOSS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        } else if (state.is(Blocks.GRASS_BLOCK)) {
            if (hasDirtNearby(serverLevel, pos)) {
                spreadGrassOrMycelium(serverLevel, pos, Blocks.GRASS_BLOCK);
            } else if (block instanceof BonemealableBlock bonemealable
                && bonemealable.isValidBonemealTarget(serverLevel, pos, state)) {
                if (bonemealable.isBonemealSuccess(serverLevel, serverLevel.random, pos, state)) {
                    bonemealable.performBonemeal(serverLevel, serverLevel.random, pos, state);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else if (state.is(Blocks.MYCELIUM)) {
            if (hasDirtNearby(serverLevel, pos)) {
                spreadGrassOrMycelium(serverLevel, pos, Blocks.MYCELIUM);
            } else if (block instanceof BonemealableBlock bonemealable
                && bonemealable.isValidBonemealTarget(serverLevel, pos, state)) {
                if (bonemealable.isBonemealSuccess(serverLevel, serverLevel.random, pos, state)) {
                    bonemealable.performBonemeal(serverLevel, serverLevel.random, pos, state);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else if (state.is(Blocks.SCULK_CATALYST)) {
            triggerSculkCatalyst(serverLevel, pos);
        } else if (state.is(Blocks.SUGAR_CANE)) {
            BlockPos top = pos;
            while (serverLevel.getBlockState(top.above()).is(Blocks.SUGAR_CANE)) {
                top = top.above();
            }
            if (serverLevel.getBlockState(top.above()).isAir()) {
                serverLevel.setBlock(top.above(), Blocks.SUGAR_CANE.defaultBlockState(), Block.UPDATE_ALL);
            }
        } else if (state.is(Blocks.CACTUS)) {
            BlockPos top = pos;
            while (serverLevel.getBlockState(top.above()).is(Blocks.CACTUS)) {
                top = top.above();
            }
            if (serverLevel.getBlockState(top.above()).isAir() && canCactusSurviveAt(serverLevel, top.above())) {
                serverLevel.setBlock(top.above(), Blocks.CACTUS.defaultBlockState(), Block.UPDATE_ALL);
            }
        } else if (state.is(Blocks.NETHER_WART)) {
            serverLevel.setBlock(pos, state.setValue(NetherWartBlock.AGE, 3), Block.UPDATE_ALL);
        } else if (isUnderwaterFullBlock(serverLevel, pos, state)) {
            placeSeagrassAtBlock(serverLevel, pos);
        } else if (block instanceof BonemealableBlock bonemealable
            && bonemealable.isValidBonemealTarget(serverLevel, pos, state)) {
            if (bonemealable.isBonemealSuccess(serverLevel, serverLevel.random, pos, state)) {
                bonemealable.performBonemeal(serverLevel, serverLevel.random, pos, state);
            } else {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    private static void processSingleBoneStaffTarget(ServerLevel serverLevel, BlockPos pos, BlockState state, Player player, ItemStack stack, InteractionHand hand) {
        if (processBoneStaffBlock(serverLevel, pos, state)) {
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            hurtStaff(stack, 1, player, slot);
        }
    }

    private static void placeSeagrassAtBlock(ServerLevel level, BlockPos fullPos) {
        BlockPos waterPos = fullPos.above();
        if (!level.getBlockState(waterPos).is(Blocks.WATER)
            || level.getFluidState(waterPos).getAmount() != 8) return;
        BlockState toPlace = Blocks.SEAGRASS.defaultBlockState();
        var biome = level.getBiome(waterPos);
        if (biome.is(BiomeTags.PRODUCES_CORALS_FROM_BONEMEAL)) {
            if (level.random.nextInt(4) == 0) {
                toPlace = BuiltInRegistries.BLOCK
                    .getRandomElementOf(BlockTags.UNDERWATER_BONEMEALS, level.random)
                    .map(p -> p.value().defaultBlockState())
                    .orElse(toPlace);
            }
        }
        level.setBlock(waterPos, toPlace, Block.UPDATE_ALL);
    }

    private static void processStemWithFruitSpawn(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof BonemealableBlock bonemealable
            && bonemealable.isValidBonemealTarget(serverLevel, pos, state)) {
            if (bonemealable.isBonemealSuccess(serverLevel, serverLevel.random, pos, state)) {
                bonemealable.performBonemeal(serverLevel, serverLevel.random, pos, state);
            }
        }

        BlockState newState = serverLevel.getBlockState(pos);
        boolean isPumpkin = newState.is(Blocks.PUMPKIN_STEM) || newState.is(Blocks.ATTACHED_PUMPKIN_STEM);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos sidePos = pos.relative(dir);
            BlockState sideState = serverLevel.getBlockState(sidePos);
            if (sideState.is(isPumpkin ? Blocks.PUMPKIN : Blocks.MELON)) return;
        }

        Block fruitBlock = isPumpkin ? Blocks.PUMPKIN : Blocks.MELON;
        Block attachedBlock = isPumpkin ? Blocks.ATTACHED_PUMPKIN_STEM : Blocks.ATTACHED_MELON_STEM;

        java.util.List<Direction> dirs = new java.util.ArrayList<>(java.util.Arrays.asList(Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new)));
        java.util.Collections.shuffle(dirs, new java.util.Random());

        for (Direction dir : dirs) {
            BlockPos fruitPos = pos.relative(dir);
            BlockPos belowFruit = fruitPos.below();
            BlockState below = serverLevel.getBlockState(belowFruit);
            if (serverLevel.isEmptyBlock(fruitPos)
                && (below.getBlock() instanceof FarmBlock || below.is(BlockTags.DIRT))) {
                serverLevel.setBlockAndUpdate(fruitPos, fruitBlock.defaultBlockState());
                serverLevel.setBlockAndUpdate(pos, attachedBlock.defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, dir));
                return;
            }
        }
    }

    private static void spreadFlower(ServerLevel level, BlockPos centerPos, BlockState centerState) {
        Block flowerBlock = centerState.getBlock();
        int range = 2;

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos targetPos = centerPos.offset(dx, 0, dz);
                BlockState targetState = level.getBlockState(targetPos);
                if (!targetState.isAir()) continue;

                if (centerState.is(Blocks.LILY_PAD)) {
                    if (level.getFluidState(targetPos.below()).is(FluidTags.WATER)) {
                        level.setBlock(targetPos, centerState, Block.UPDATE_ALL);
                    }
                } else {
                    BlockPos belowPos = targetPos.below();
                    BlockState belowState = level.getBlockState(belowPos);
                    if (belowState.is(BlockTags.DIRT) || belowState.is(Blocks.FARMLAND)
                        || belowState.is(Blocks.GRASS_BLOCK)) {
                        level.setBlock(targetPos, centerState, Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    private static void dropSaplingFromLeaf(ServerLevel level, BlockPos pos, BlockState state) {
        Item sapling = null;
        if (state.is(Blocks.OAK_LEAVES)) sapling = Items.OAK_SAPLING;
        else if (state.is(Blocks.SPRUCE_LEAVES)) sapling = Items.SPRUCE_SAPLING;
        else if (state.is(Blocks.BIRCH_LEAVES)) sapling = Items.BIRCH_SAPLING;
        else if (state.is(Blocks.JUNGLE_LEAVES)) sapling = Items.JUNGLE_SAPLING;
        else if (state.is(Blocks.ACACIA_LEAVES)) sapling = Items.ACACIA_SAPLING;
        else if (state.is(Blocks.DARK_OAK_LEAVES)) sapling = Items.DARK_OAK_SAPLING;
        else if (state.is(Blocks.MANGROVE_LEAVES)) sapling = Items.MANGROVE_PROPAGULE;
        else if (state.is(Blocks.CHERRY_LEAVES)) sapling = Items.CHERRY_SAPLING;
        else if (state.is(Blocks.AZALEA_LEAVES)) sapling = Items.AZALEA;

        if (sapling != null) {
            ItemStack drop = new ItemStack(sapling);
            Block.popResource(level, pos, drop);
        }
    }

    private static void growChorusFlower(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState newState = state.setValue(ChorusFlowerBlock.AGE, 0);
        level.setBlock(pos, newState, Block.UPDATE_ALL);
        try {
            java.lang.reflect.Method method = ChorusFlowerBlock.class.getDeclaredMethod(
                "randomTick", BlockState.class, ServerLevel.class, BlockPos.class, net.minecraft.util.RandomSource.class);
            method.setAccessible(true);
            method.invoke(Blocks.CHORUS_FLOWER, newState, level, pos, level.random);
        } catch (Exception e) {
        }
    }

    private static void spreadMoss(ServerLevel level, BlockPos centerPos) {
        int range = 2;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos targetPos = centerPos.offset(dx, dy, dz);
                    BlockState targetState = level.getBlockState(targetPos);
                    Block mossyVariant = MOSS_CONVERSION.get(targetState.getBlock());
                    if (mossyVariant != null) {
                        BlockState newState = mossyVariant.defaultBlockState();
                        if (targetState.hasProperty(BlockStateProperties.WATERLOGGED)
                            && newState.hasProperty(BlockStateProperties.WATERLOGGED)) {
                            newState = newState.setValue(BlockStateProperties.WATERLOGGED,
                                targetState.getValue(BlockStateProperties.WATERLOGGED));
                        }
                        level.setBlock(targetPos, newState, Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    private static boolean hasDirtNearby(ServerLevel level, BlockPos centerPos) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (level.getBlockState(centerPos.offset(dx, 0, dz)).is(Blocks.DIRT)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void spreadGrassOrMycelium(ServerLevel level, BlockPos centerPos, Block spreadBlock) {
        int range = 2;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos targetPos = centerPos.offset(dx, 0, dz);
                BlockState targetState = level.getBlockState(targetPos);
                if (!targetState.is(Blocks.DIRT)) continue;
                BlockPos abovePos = targetPos.above();
                BlockState aboveState = level.getBlockState(abovePos);
                if (aboveState.isCollisionShapeFullBlock(level, abovePos)) continue;
                level.setBlockAndUpdate(targetPos, spreadBlock.defaultBlockState());
            }
        }
    }

    private static void triggerSculkCatalyst(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SculkCatalystBlockEntity catalyst) {
            var listener = catalyst.getListener();
            var spreader = listener.getSculkSpreader();
            spreader.addCursors(pos, 50);
            level.setBlock(pos, level.getBlockState(pos).setValue(
                SculkCatalystBlock.PULSE, Boolean.valueOf(true)), Block.UPDATE_ALL);
            level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 8);
            level.playSound(null, pos, SoundEvents.SCULK_CATALYST_BLOOM,
                SoundSource.BLOCKS, 2.0F, 0.6F + level.random.nextFloat() * 0.4F);
        }
    }

    private static void spreadVines(ServerLevel level, BlockPos centerPos) {
        int range = 2;
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    BlockPos pos = centerPos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof VineBlock)) continue;

                    BlockPos below = pos.below();
                    BlockState belowState = level.getBlockState(below);
                    if (belowState.isAir()) {
                        for (Direction face : Direction.Plane.HORIZONTAL) {
                            if (VineBlock.isAcceptableNeighbour(level, below.relative(face.getOpposite()), face)) {
                                BlockState vineBelow = Blocks.VINE.defaultBlockState()
                                    .setValue(VineBlock.getPropertyForFace(face), Boolean.valueOf(true));
                                level.setBlock(below, vineBelow, Block.UPDATE_ALL);
                                break;
                            }
                        }
                    }

                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        BlockPos sidePos = pos.relative(dir);
                        if (!level.getBlockState(sidePos).isAir()) continue;
                        if (VineBlock.isAcceptableNeighbour(level, sidePos.relative(dir.getOpposite()), dir)) {
                            BlockState vineSide = Blocks.VINE.defaultBlockState()
                                .setValue(VineBlock.getPropertyForFace(dir), Boolean.valueOf(true));
                            level.setBlock(sidePos, vineSide, Block.UPDATE_ALL);
                        }
                    }
                }
            }
        }
    }

    private static java.util.Set<BlockPos> findSugarCaneTops(ServerLevel level, java.util.List<BlockPos> positions) {
        java.util.Set<BlockPos> tops = new java.util.HashSet<>();
        for (BlockPos pos : positions) {
            BlockPos top = pos;
            while (level.getBlockState(top.above()).is(Blocks.SUGAR_CANE)) {
                top = top.above();
            }
            tops.add(top);
        }
        return tops;
    }

    private static java.util.Set<BlockPos> findCactusTops(ServerLevel level, java.util.List<BlockPos> positions) {
        java.util.Set<BlockPos> tops = new java.util.HashSet<>();
        for (BlockPos pos : positions) {
            BlockPos top = pos;
            while (level.getBlockState(top.above()).is(Blocks.CACTUS)) {
                top = top.above();
            }
            tops.add(top);
        }
        return tops;
    }

    private static boolean canCactusSurviveAt(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(dir)).isSolid()) return false;
        }
        return true;
    }

    private static int placeSeagrassOnFullBlocks(ServerLevel level, java.util.Set<BlockPos> fullBlockPositions) {
        int count = 0;
        java.util.Random random = new java.util.Random();
        for (BlockPos fullPos : fullBlockPositions) {
            BlockPos waterPos = fullPos.above();
            if (!level.getBlockState(waterPos).is(Blocks.WATER)
                || level.getFluidState(waterPos).getAmount() != 8) continue;

            BlockState toPlace = Blocks.SEAGRASS.defaultBlockState();

            var biome = level.getBiome(waterPos);
            if (biome.is(BiomeTags.PRODUCES_CORALS_FROM_BONEMEAL)) {
                if (random.nextInt(4) == 0) {
                    toPlace = BuiltInRegistries.BLOCK
                        .getRandomElementOf(BlockTags.UNDERWATER_BONEMEALS, level.random)
                        .map(p -> p.value().defaultBlockState())
                        .orElse(toPlace);
                }
            }

            level.setBlock(waterPos, toPlace, Block.UPDATE_ALL);
            count++;
        }
        return count;
    }

    private static int getBlockMiningTier(BlockState state) {
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) return 3;
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) return 2;
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) return 1;
        return 0;
    }

    private static class StaffMoveTask {
        ServerLevel level;
        java.util.UUID playerId;
        EquipmentSlot slot;
        int tickDelay;
        java.util.List<BlockMovement> movements;
    }

    private static class PortalActivationTask {
        ServerLevel level;
        BlockPos waterPos;
        int itemEntityId;
        int tickDelay;
    }

    private static class BlockMovement {
        BlockPos startPos;
        BlockPos currentPos;
        BlockPos finalDestPos;
        BlockState state;
        CompoundTag beNbt;
        int remainingSteps;
        int movedDistance;
        boolean failed;
        int stepX;
        int stepY;
        int stepZ;
    }

    private static class BonemealTask {
        ServerLevel level;
        java.util.List<BlockPos> regularTargets;
        java.util.Map<BlockPos, Integer> duplicationTargets;
    }

    private static boolean processStaffMoveStep(StaffMoveTask task, Player player) {
        ServerLevel level = task.level;
        java.util.Map<BlockPos, BlockPos> destMapping = new java.util.LinkedHashMap<>();
        java.util.Map<BlockPos, BlockState> stateMap = new java.util.LinkedHashMap<>();
        java.util.Map<BlockPos, CompoundTag> nbtMap = new java.util.LinkedHashMap<>();
        java.util.Set<BlockPos> failures = new java.util.LinkedHashSet<>();

        java.util.Set<BlockPos> srcPositions = new java.util.HashSet<>();
        for (BlockMovement mov : task.movements) {
            if (mov.remainingSteps > 0 && !mov.failed) {
                srcPositions.add(mov.currentPos);
            }
        }
        java.util.Set<BlockPos> claimedDests = new java.util.HashSet<>();

        for (BlockMovement mov : task.movements) {
            if (mov.remainingSteps <= 0 || mov.failed) continue;

            BlockPos diff = mov.finalDestPos.subtract(mov.currentPos);
            int stepX = (int) Math.signum(diff.getX());
            int stepY = (int) Math.signum(diff.getY());
            int stepZ = (int) Math.signum(diff.getZ());

            if (stepX == 0 && stepY == 0 && stepZ == 0) {
                mov.remainingSteps = 0;
                continue;
            }

            BlockPos nextPos = mov.currentPos.offset(stepX, stepY, stepZ);

            if (!level.isInWorldBounds(nextPos)) {
                failures.add(mov.currentPos);
                mov.failed = true;
                continue;
            }

            if (claimedDests.contains(nextPos)) {
                failures.add(mov.currentPos);
                mov.failed = true;
                continue;
            }

            if (srcPositions.contains(nextPos)) {
                claimedDests.add(nextPos);
                destMapping.put(mov.currentPos, nextPos);
                stateMap.put(mov.currentPos, mov.state);
                if (mov.beNbt != null) nbtMap.put(mov.currentPos, mov.beNbt);
            } else {
                BlockState destState = level.getBlockState(nextPos);
                if (destState.is(Blocks.NETHERITE_BLOCK) || isUnbreakableForNetherite(destState)) {
                    failures.add(mov.currentPos);
                    mov.failed = true;
                } else if (!destState.isAir() && getBlockMiningTier(destState) > getBlockMiningTier(mov.state)) {
                    failures.add(mov.currentPos);
                    mov.failed = true;
                } else {
                    claimedDests.add(nextPos);
                    destMapping.put(mov.currentPos, nextPos);
                    stateMap.put(mov.currentPos, mov.state);
                    if (mov.beNbt != null) nbtMap.put(mov.currentPos, mov.beNbt);
                }
            }
        }

        for (BlockPos src : destMapping.keySet()) {
            level.removeBlock(src, false);
        }

        java.util.Map<BlockPos, BlockEntity> failureBEs = new java.util.LinkedHashMap<>();
        for (BlockPos src : failures) {
            if (!destMapping.containsKey(src)) {
                failureBEs.put(src, level.getBlockEntity(src));
                level.removeBlock(src, false);
            }
        }

        for (java.util.Map.Entry<BlockPos, BlockPos> entry : destMapping.entrySet()) {
            BlockPos src = entry.getKey();
            BlockPos dest = entry.getValue();
            BlockState state = stateMap.get(src);

            BlockState existingDest = level.getBlockState(dest);
            if (!existingDest.isAir()) {
                level.playSound(null, dest, existingDest.getSoundType().getBreakSound(),
                    SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            level.setBlock(dest, state, Block.UPDATE_ALL);
            CompoundTag nbt = nbtMap.get(src);
            if (nbt != null) {
                BlockEntity newBe = level.getBlockEntity(dest);
                if (newBe != null) {
                    newBe.loadWithComponents(nbt, level.registryAccess());
                }
            }
        }

        for (BlockPos src : failures) {
            for (BlockMovement mov : task.movements) {
                if (mov.failed && mov.currentPos.equals(src)) {
                    BlockEntity be = failureBEs.get(src);
                    java.util.List<ItemStack> drops = Block.getDrops(mov.state,
                        level, src, be, player, ItemStack.EMPTY);
                    for (ItemStack drop : drops) {
                        Block.popResource(level, src, drop);
                    }
                    break;
                }
            }
        }

        for (java.util.Map.Entry<BlockPos, BlockPos> entry : destMapping.entrySet()) {
            BlockPos src = entry.getKey();
            BlockPos dest = entry.getValue();
            for (BlockMovement mov : task.movements) {
                if (!mov.failed && mov.currentPos.equals(src)) {
                    mov.currentPos = dest;
                    mov.remainingSteps--;
                    break;
                }
            }
        }

        for (BlockMovement mov : task.movements) {
            if (mov.remainingSteps > 0 && !mov.failed) {
                return false;
            }
        }
        return true;
    }

    // ==================== BREWING RECIPES ====================

    private static void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
        var builder = event.getBuilder();

        builder.addMix(Potions.WATER, Items.SOUL_SAND, ModPotions.HAUNTED);

        builder.addMix(ModPotions.HAUNTED, Items.CARVED_PUMPKIN, ModPotions.AWAKENING);
        builder.addMix(ModPotions.AWAKENING, Items.REDSTONE, ModPotions.LONG_AWAKENING);

        builder.addMix(ModPotions.HAUNTED, Items.ENDER_PEARL, ModPotions.PRE_TRANSPORTATION);
        // 准传送药水 + 地狱疣 的配方由 TransmutationBrewingRecipe 处理（无名地狱疣→随机传送药水）

        builder.addMix(ModPotions.HAUNTED, Items.NETHER_WART, ModPotions.PRE_TRANSMUTATION);
        builder.addMix(Potions.AWKWARD, Items.SOUL_SAND, ModPotions.PRE_TRANSMUTATION);

        // 变形解药：闹鬼的药水 + 发酵蛛眼
        builder.addMix(ModPotions.HAUNTED, Items.FERMENTED_SPIDER_EYE, ModPotions.TRANSMUTATION_ANTIDOTE);

        builder.addRecipe(new TransmutationBrewingRecipe());
    }

    // ==================== TRANSMUTATION SYSTEM ====================

    private static void onMobEffectAdded(MobEffectEvent.Added event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null) return;
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel serverLevel)) return;

        // === 附魔状态效果（魔咒占位效果，含击退）：无论由权杖还是 /effect 指令施加，
        //     一律隐藏药水粒子并呈现附魔光效（而非渲染粒子）。 ===
        if (enchantmentOfEffect(serverLevel, instance.getEffect().value()) != null
            || instance.getEffect().is(ModMobEffects.KNOCKBACK)) {
            // 若当前效果仍可见（如 /effect 指令施加的），以不可见版本重注入一次，隐藏粒子
            if (!reinjectingEnchantEffect && instance.isVisible()) {
                reinjectingEnchantEffect = true;
                try {
                    MobEffectInstance hidden = new MobEffectInstance(
                        instance.getEffect(), instance.getDuration(), instance.getAmplifier(),
                        instance.isAmbient(), false, instance.showIcon());
                    applyEnchantEffectInstance(target, hidden);
                } finally {
                    reinjectingEnchantEffect = false;
                }
            }
            // 触发附魔光效（持续显示），到期由 onMobEffectExpired 清除
            if (!getEnchantSelf(target)) {
                setEnchantSelf(target, true);
                broadcastEnchantSelf(target);
            }
            return;
        }

        // 变形解药（含强效版）：无论直接饮用还是被喷溅/滞留云命中，只要该实体被加上解药效果，
        // 就立即解除其自身变形（玩家空壳/生物壳复原、玩家复原）。这样喝下解药也能提前移除变形效果，
        // 而不必必须是喷溅型药水。
        if (instance.getEffect().is(ModMobEffects.TRANSMUTATION_ANTIDOTE)) {
            if (serverLevel != null) {
                applyAntidoteToEntity(serverLevel, target);
            }
            target.removeEffect(ModMobEffects.TRANSMUTATION_ANTIDOTE);
            return;
        }

        if (!instance.getEffect().is(ModMobEffects.TRANSMUTATION)) return;

        // 女巫Boss免疫「除了她自己以外」任何来源的变形药水：她自己扔出的变形药水可对自己生效
        // （看后续流程），其余任何来源（玩家/其它生物/发射器）一律免疫，移除效果并直接返回。
        if (target.getPersistentData().getBoolean(WITCH_BOSS_TAG)) {
            if (!isThrownByTarget(serverLevel, target)) {
                LOGGER.info("[DBG] onMobEffectAdded: WITCH_BOSS EXTERNAL IMMUNE target={}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
                target.removeEffect(ModMobEffects.TRANSMUTATION);
                return;
            }
            // 自己扔出的变形药水：不豁免，继续下方变形流程
        }

        // 玩家空壳/生物壳实体免疫变形效果（避免被持续存在的作用域云二次变形，导致倒计时被重置）
        if (target instanceof PlayerShellEntity || LIVING_SHELLS.containsKey(target.getUUID())) {
            LOGGER.info("[DBG] onMobEffectAdded: SHELL IMMUNE target={} isPlayerShell={} inMap={}",
                BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()), target instanceof PlayerShellEntity,
                LIVING_SHELLS.containsKey(target.getUUID()));
            target.removeEffect(ModMobEffects.TRANSMUTATION);
            return;
        }

        // 已处于变身流程中的生物/玩家：忽略过期/重复施加的效果（避免二次变身）
        if (TRANSMUTED_ENTITIES.contains(target.getUUID())) {
            LOGGER.info("[DBG] onMobEffectAdded: IN_TRANSMUTED target={}", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
            target.removeEffect(ModMobEffects.TRANSMUTATION);
            return;
        }

        // 现在所有生物（凋灵、末影龙、玩家等）都受影响。只有持“掷出者免疫”标记的玩家不受自己扔出的
        // 药水影响（击败女巫Boss 授予，或用 /jafa toggletransmutationdebug <true|false> 开关）；其余玩家会被
        // 自己丢出的变形药水影响。发射器发射的变形药水（无投掷者）不受该条件限制，
        // isThrownByTarget 对无主投掷物恒为 false，因此永远作用到所有生物。
        if (isThrownByTarget(serverLevel, target) && isThrowerImmuneToPotion(target)) {
            LOGGER.info("[DBG] onMobEffectAdded: SELF_THROWN target={}", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
            target.removeEffect(ModMobEffects.TRANSMUTATION);
            return;
        }

        String itemType = findTransmutationItemType(serverLevel, target);
        if (itemType == null) {
            LOGGER.info("[DBG] onMobEffectAdded: NO_ITEM_TYPE target={}", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
            target.removeEffect(ModMobEffects.TRANSMUTATION);
            return;
        }

        UUID killerUuid = findTransmutationKiller(serverLevel, target);

        int remainingTicks = instance.getDuration();
        LOGGER.info("[DBG] onMobEffectAdded: transmute target={} itemType={} remainingTicks={}",
            BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()), itemType, remainingTicks);

        target.removeEffect(ModMobEffects.TRANSMUTATION);
        performTransmutation(serverLevel, target, itemType, remainingTicks, killerUuid);
    }

    // 药水是否由该生物自己抛出（用于投掷者免疫）
    private static boolean isThrownByTarget(ServerLevel level, LivingEntity target) {
        for (ThrownPotion potion : level.getEntitiesOfClass(ThrownPotion.class,
            target.getBoundingBox().inflate(64.0))) {
            if (potion.getOwner() instanceof Entity owner && owner.getUUID().equals(target.getUUID())) {
                return true;
            }
        }
        for (AreaEffectCloud cloud : level.getEntitiesOfClass(AreaEffectCloud.class,
            target.getBoundingBox().inflate(64.0))) {
            if (cloud.getOwner() instanceof Entity owner && owner.getUUID().equals(target.getUUID())) {
                return true;
            }
        }
        return false;
    }

    private static String findTransmutationItemType(ServerLevel level, LivingEntity target) {
        // 瓶装变形药水饮用：效果在 finishUsingItem 内被施加时，玩家仍在使用该药水，
        // 从 getUseItem() 读取其 ITEM_TYPE（否则饮用型永远取不到目标而无法变形）
        ItemStack using = target.getUseItem();
        if (using != null && !using.isEmpty() && isTransmutationPotion(using)) {
            String usingType = using.getOrDefault(ModDataComponents.ITEM_TYPE.get(), null);
            if (usingType != null) return usingType;
            return pickRandomTransmutationType(); // 无固定目标的随机变形
        }

        for (AreaEffectCloud cloud : level.getEntitiesOfClass(AreaEffectCloud.class,
            target.getBoundingBox().inflate(64.0))) {
            String itemType = TRANSMUTATION_POTION_ITEM_TYPES.get(cloud.getId());
            if (itemType != null) return itemType;
        }

        for (ThrownPotion potion : level.getEntitiesOfClass(ThrownPotion.class,
            target.getBoundingBox().inflate(64.0))) {
            String itemType = TRANSMUTATION_POTION_ITEM_TYPES.get(potion.getId());
            if (itemType != null) return itemType;
            ItemStack potionStack = potion.getItem();
            itemType = potionStack.getOrDefault(ModDataComponents.ITEM_TYPE.get(), null);
            if (itemType != null) return itemType;
            // 未绑定固定目标但确实是变形药水：随机选取一个目标
            if (isTransmutationPotion(potionStack)) {
                return pickRandomTransmutationType();
            }
        }

        return null;
    }

    private static UUID findTransmutationKiller(ServerLevel level, LivingEntity target) {
        for (ThrownPotion potion : level.getEntitiesOfClass(ThrownPotion.class,
            target.getBoundingBox().inflate(64.0))) {
            if (potion.getOwner() instanceof Player player) {
                return player.getUUID();
            }
        }
        for (AreaEffectCloud cloud : level.getEntitiesOfClass(AreaEffectCloud.class,
            target.getBoundingBox().inflate(64.0))) {
            if (cloud.getOwner() instanceof Player player) {
                return player.getUUID();
            }
        }
        return null;
    }

    /** 变身开始时把玩家视角切到第三人称并把初始俯仰设为斜向下45°（配置开启时生效）。
     *  Minecraft 的 xRot 正值=低头、负值=抬头，故用 +45 实现斜向下。 */
    private static void forceTransmutationCamera(ServerPlayer player) {
        if (ModConfig.FORCE_THIRD_PERSON_ON_TRANSMUTATION.get()) {
            PacketDistributor.sendToPlayer(player,
                new cn.autoforged.joes_addons_for_abmc.network.TransmutationCameraPayload(true, 45.0F));
        }
    }

    /** 变身开始时初始化玩家：切冒险模式 + 隐身（不被其它生物/宠物察觉）+ 初始化视角。 */
    private static void beginTransmutationView(ServerPlayer sp) {
        sp.setGameMode(GameType.ADVENTURE);
        sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
            Integer.MAX_VALUE, 0, false, false, true));
        sp.setInvisible(true);
        forceTransmutationCamera(sp);
        // 清除已锁定该玩家的敌对/中立生物目标，配合 onLivingChangeTarget 的拦截，
        // 确保变形期间任何生物都不会察觉并攻击玩家（即使贴得很近）。
        clearMobsTargetingPlayer(sp);
    }

    // 清除附近所有已锁定该玩家的生物目标（变形开始时一次性清理）
    private static void clearMobsTargetingPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48.0))) {
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
        }
    }

    /** 复原时恢复玩家第一人称（配置开启时）。 */
    private static void restoreTransmutationCamera(ServerPlayer player) {
        if (ModConfig.FORCE_THIRD_PERSON_ON_TRANSMUTATION.get()) {
            PacketDistributor.sendToPlayer(player,
                new cn.autoforged.joes_addons_for_abmc.network.TransmutationCameraPayload(false, 0.0F));
        }
    }

    /** 通知客户端玩家变形状态：true=开始（携带被变成实体的实体ID），false=结束（非生物形态）。 */
    private static void sendTransmutationState(ServerPlayer player, boolean transmuted, int followEntityId) {
        sendTransmutationState(player, transmuted, followEntityId, "");
    }

    /** 通知客户端玩家变形状态（生物形态用渲染替换：携带要呈现的生物实体类型 id）。 */
    private static void sendTransmutationState(ServerPlayer player, boolean transmuted, int followEntityId,
            String morphEntityType) {
        PacketDistributor.sendToPlayer(player,
            new cn.autoforged.joes_addons_for_abmc.network.TransmutationStatePayload(
                transmuted, followEntityId, morphEntityType == null ? "" : morphEntityType));
    }

    /** 该玩家是否正处于“变形中”（生物壳/物品/方块/玩家壳变形期间为 true，之前若再次变形则为 true）。
     *  供 {@code EntityRightClickMixin} 判定玩家本体在变形期间应不可被右键射线拾取。 */
    public static boolean isPlayerTransmuting(UUID uuid) {
        return TRANSMUTED_ENTITIES.contains(uuid);
    }

    @SuppressWarnings("deprecation")
    private static void performTransmutation(ServerLevel level, LivingEntity entity,
            String itemType, int remainingTicks, UUID killerUuid) {
        // 女巫Boss 被自己的变形药水命中且目标是“生物”：它确实会变成那个生物；但由
        // performMobShellTransmutation 打上“自我变形”标记，变形后的生物稍后会往脚下丢应对
        // 变形解药把自己还原为 witchboss 本体（变形解药计数 -1）。变方块/物品则无此行为。
        if (itemType.startsWith("mob_shell:")
                && entity instanceof Witch witchHandle && witchHandle.getPersistentData().getBoolean(WITCH_BOSS_TAG)) {
            consumeWitchBossAmmo(witchHandle, WITCH_BOSS_AMMO_ANTIDOTE_TAG, 1);
        }

        // 玩家空壳变形：原生物变成指定名字的玩家空壳
        if (itemType.startsWith("player_shell:")) {
            String skinName = decodePlayerShellName(itemType);
            LOGGER.info("[DBG] performTransmutation: PLAYER_SHELL skin={} remainingTicks={}",
                skinName, remainingTicks);
            performPlayerShellTransmutation(level, entity, skinName, remainingTicks, killerUuid);
            return;
        }

        // 生物壳变形：原生物变成刷怪蛋对应的生物（使用其 AI）
        if (itemType.startsWith("mob_shell:")) {
            String entityTypeId = decodeMobShellName(itemType);
            LOGGER.info("[DBG] performTransmutation: MOB_SHELL type={} remainingTicks={}",
                entityTypeId, remainingTicks);
            performMobShellTransmutation(level, entity, entityTypeId, remainingTicks, killerUuid);
            return;
        }

        CompoundTag entityNbt = new CompoundTag();
        entity.save(entityNbt);

        ResourceLocation itemTypeRl = ResourceLocation.tryParse(itemType);
        if (itemTypeRl == null) return;

        // 黑名单方块（带方块实体的储存类方块）不可作为变形目标：退回随机安全目标，
        // 防止变身后渲染成空白方块或在落地时死亡（随机目标选择同样已排除黑名单）。
        if (BuiltInRegistries.BLOCK.containsKey(itemTypeRl)) {
            Block targetBlock = BuiltInRegistries.BLOCK.get(itemTypeRl);
            if (isTransmutationBlockBlacklisted(targetBlock)) {
                itemType = pickRandomTransmutationType();
                itemTypeRl = ResourceLocation.tryParse(itemType);
                if (itemTypeRl == null) return;
            }
        }

        double spawnX = entity.getX();
        double spawnY = entity.getY() + 0.2;
        double spawnZ = entity.getZ();

        boolean isBlock = BuiltInRegistries.BLOCK.containsKey(itemTypeRl);
        boolean isPlayer = entity instanceof ServerPlayer;
        UUID playerUuid = isPlayer ? entity.getUUID() : null;

        TransmutationData data = new TransmutationData(entityNbt, remainingTicks,
            killerUuid != null ? killerUuid : new UUID(0, 0), itemType, playerUuid);

        if (isPlayer) {
            // 玩家：切冒险模式并隐身（被变成的实体跟随玩家），绝不 discard 玩家。
            // 记录到 TRANSMUTED_ENTITIES 以阻止滞留云对同一玩家重复二次变身。
            ServerPlayer sp = (ServerPlayer) entity;
            PLAYER_ORIGINAL_GAMEMODE.put(sp.getUUID(), sp.gameMode.getGameModeForPlayer());
            TRANSMUTED_ENTITIES.add(sp.getUUID());
            // 记录变形形态（伤害免疫规则用）并设置玩家缩放（物品 0.13 / 方块 0.5）
            PLAYER_TRANSMUTATION_INFO.put(sp.getUUID(),
                new PlayerTransmutationInfo(isBlock ? TransmutationForm.BLOCK : TransmutationForm.ITEM, itemType));
            applyTransmutationScale(sp, isBlock ? 0.5 : 0.13);
            beginTransmutationView(sp);
        } else {
            entity.discard();
        }

        if (isBlock) {
            Block block = BuiltInRegistries.BLOCK.get(itemTypeRl);
            if (block != null) {
                Entity fallingBlock;
                if (isPlayer) {
                    // 玩家变方块：使用专用下落方块实体——贴地面跟随玩家、永不固化成方块、不因超时消失
                    TransmutationFallingBlockEntity tfb = new TransmutationFallingBlockEntity(
                        ModEntities.TRANSMUTATION_FALLING_BLOCK.get(), level);
                    tfb.initFromBlock(level, BlockPos.containing(spawnX, spawnY, spawnZ),
                        block.defaultBlockState());
                    level.addFreshEntity(tfb);
                    fallingBlock = tfb;
                } else {
                    FallingBlockEntity vanillaFall = FallingBlockEntity.fall(level,
                        BlockPos.containing(spawnX, spawnY, spawnZ), block.defaultBlockState());
                    vanillaFall.setPos(spawnX, spawnY, spawnZ);
                    vanillaFall.setDeltaMovement(Vec3.ZERO);
                    vanillaFall.dropItem = false;
                    fallingBlock = vanillaFall;
                }
                FALLING_TRANSMUTATIONS.put(fallingBlock.getUUID(), data);
                if (isPlayer) {
                    sendTransmutationState((ServerPlayer) entity, true, fallingBlock.getId());
                }
            }
        } else {
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(itemTypeRl);
            if (item != null) {
                ItemStack itemStack = new ItemStack(item);
                ItemEntity itemEntity = new ItemEntity(level, spawnX, spawnY, spawnZ, itemStack);
                itemEntity.setDeltaMovement(Vec3.ZERO);
                itemEntity.setPickUpDelay(Integer.MAX_VALUE);
                itemEntity.lifespan = Integer.MAX_VALUE;
                itemEntity.setUnlimitedLifetime();
                level.addFreshEntity(itemEntity);
                ITEM_TRANSMUTATIONS.put(itemEntity.getUUID(), data);
                ITEM_TRANSMUTATION_POSITIONS.put(itemEntity.getUUID(),
                    BlockPos.containing(spawnX, spawnY, spawnZ));
                if (isPlayer) {
                    sendTransmutationState((ServerPlayer) entity, true, itemEntity.getId());
                }
            }
        }
    }

    // ==================== LIVING SHELL TRANSMUTATION ====================
    // 玩家空壳（命名牌药水）与生物壳（刷怪蛋药水）共用一套：倒计时复原、解药复原、死亡击杀。

    // 从 ITEM_TYPE 中解码玩家空壳的名字
    private static String decodePlayerShellName(String itemType) {
        String base64 = itemType.substring("player_shell:".length());
        try {
            return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    // 从 ITEM_TYPE 中解码生物壳的实体类型 ID
    private static String decodeMobShellName(String itemType) {
        return itemType.substring("mob_shell:".length());
    }

    // 将原生物变成指定名字的玩家空壳
    private static void performPlayerShellTransmutation(ServerLevel level, LivingEntity entity,
            String skinName, int remainingTicks, UUID killerUuid) {
        CompoundTag entityNbt = new CompoundTag();
        entity.save(entityNbt);

        double spawnX = entity.getX();
        double spawnY = entity.getY();
        double spawnZ = entity.getZ();

        boolean isPlayer = entity instanceof ServerPlayer;
        UUID playerUuid = isPlayer ? entity.getUUID() : null;

        if (isPlayer) {
            // 玩家：切冒险模式并隐身（被变成的壳跟随玩家），绝不 discard 玩家
            ServerPlayer sp = (ServerPlayer) entity;
            PLAYER_ORIGINAL_GAMEMODE.put(sp.getUUID(), sp.gameMode.getGameModeForPlayer());
            TRANSMUTED_ENTITIES.add(sp.getUUID());
            // 玩家空壳形态：伤害完全免疫；不改缩放
            PLAYER_TRANSMUTATION_INFO.put(sp.getUUID(),
                new PlayerTransmutationInfo(TransmutationForm.PLAYER_SHELL, "player_shell:" + skinName));
            beginTransmutationView(sp);
        } else {
            entity.discard();
        }

        PlayerShellEntity shell = new PlayerShellEntity(ModEntities.PLAYER_SHELL.get(), level);
        shell.setSkinTexture(skinName);
        shell.setPos(spawnX, spawnY, spawnZ);
        shell.setPersistenceRequired();
        // 玩家变身的壳：禁用 AI，避免其自主寻路/移动与“跟随玩家”贴附逻辑互相拉扯造成抽搐
        if (playerUuid != null) {
            shell.setNoAi(true);
        }
        shell.setTransmutationOrigin(entityNbt, playerUuid,
            killerUuid != null ? killerUuid : new UUID(0, 0));
        shell.setRemainingTicks(remainingTicks);
        level.addFreshEntity(shell);

        LIVING_SHELLS.put(shell.getUUID(), new LivingShellData(entityNbt, playerUuid,
            killerUuid != null ? killerUuid : new UUID(0, 0), remainingTicks, true));
        if (playerUuid != null) {
            sendTransmutationState((ServerPlayer) entity, true, shell.getId());
        }
        LOGGER.info("[DBG] playerShell CREATED uuid={} playerUuid={} remainingTicks={} mapSize={}",
            shell.getUUID(), playerUuid, remainingTicks, LIVING_SHELLS.size());
    }

    // 将原生物变成刷怪蛋对应的生物（使用其 AI）
    private static void performMobShellTransmutation(ServerLevel level, LivingEntity entity,
            String entityTypeId, int remainingTicks, UUID killerUuid) {
        boolean wasWitchBoss = entity instanceof Witch ww && ww.getPersistentData().getBoolean(WITCH_BOSS_TAG);
        CompoundTag entityNbt = new CompoundTag();
        entity.save(entityNbt);

        double spawnX = entity.getX();
        double spawnY = entity.getY();
        double spawnZ = entity.getZ();

        boolean isPlayer = entity instanceof ServerPlayer;
        UUID playerUuid = isPlayer ? entity.getUUID() : null;

        if (isPlayer) {
            // 玩家：渲染替换（Morph 式）。不再切冒险/隐形，也不生成独立生物壳——玩家本体保持操控，
            // 客户端把玩家渲染成目标生物。生物形态受致死伤害时按玩家正常流程死亡（创造/旁观免疫）。
            ServerPlayer sp = (ServerPlayer) entity;
            TRANSMUTED_ENTITIES.add(sp.getUUID());
            PLAYER_TRANSMUTATION_INFO.put(sp.getUUID(),
                new PlayerTransmutationInfo(TransmutationForm.MOB, entityTypeId));
            // 把玩家碰撞箱设为该生物的默认尺寸
            ResourceLocation morphRl = ResourceLocation.tryParse(entityTypeId);
            if (morphRl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(morphRl)) {
                MORPH_DIMENSIONS.put(sp.getUUID(),
                    BuiltInRegistries.ENTITY_TYPE.get(morphRl).getDimensions());
                sp.refreshDimensions();
            }
            // 把玩家最大生命值设为该生物的最大生命值，并按比例换算当前生命（向上取整）
            applyMorphMaxHealth(sp, level, morphRl);
            MORPH_REMAINING.put(sp.getUUID(), remainingTicks);
            sendTransmutationState(sp, true, 0, entityTypeId);
            LOGGER.info("[DBG] morphRender CREATED player={} type={} remainingTicks={}",
                sp.getUUID(), entityTypeId, remainingTicks);
            return; // 玩家走渲染替换，不再生成跟随生物壳
        }
        entity.discard();

        ResourceLocation rl = ResourceLocation.tryParse(entityTypeId);
        if (rl == null) return;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
        if (type == null) return;
        Entity mob = type.create(level);
        if (!(mob instanceof net.minecraft.world.entity.Mob mobEntity)) return;
        mobEntity.setPersistenceRequired();
        mobEntity.setPos(spawnX, spawnY, spawnZ);
        // 玩家变身的壳：禁用 AI，避免其自主寻路/移动与“跟随玩家”贴附逻辑互相拉扯造成抽搐
        if (playerUuid != null) {
            mobEntity.setNoAi(true);
            // 标记为“玩家变形跟随壳”：右键点击可穿透（不阻挡互动）
            mobEntity.getPersistentData().putBoolean("jafa_transmutation_follow", true);
            // 生物形态缩放：碰撞箱高度 <= 1.8 时按 (高度/1.8) 缩放，否则不做处理（保持原比例）
            double h = mobEntity.getBbHeight();
            if (h > 0 && h <= 1.8) {
                ServerPlayer sp = level.getServer().getPlayerList().getPlayer(playerUuid);
                if (sp != null) {
                    applyTransmutationScale(sp, h / 1.8);
                }
            }
        }
        level.addFreshEntity(mobEntity);

        if (playerUuid != null) {
            sendTransmutationState((ServerPlayer) entity, true, mob.getId());
        }
        LOGGER.info("[DBG] mobShell afterAdd: uuid={} alive={} dim={} getEntity={}",
            mob.getUUID(), mobEntity.isAlive(),
            mobEntity.level().dimension().location(),
            level.getEntity(mob.getUUID()));

        LIVING_SHELLS.put(mob.getUUID(), new LivingShellData(entityNbt, playerUuid,
            killerUuid != null ? killerUuid : new UUID(0, 0), remainingTicks, false));
        // 女巫Boss自我变形：给变形后的生物打标记，让它稍后往脚下丢变形解药把自己还原为 witchboss 本体
        if (wasWitchBoss && playerUuid == null) {
            mobEntity.getPersistentData().putBoolean(WITCH_BOSS_SELF_TRANS_TAG, true);
        }
        LOGGER.info("[DBG] mobShell CREATED uuid={} type={} playerUuid={} remainingTicks={} mapSize={}",
            mob.getUUID(), entityTypeId, playerUuid, remainingTicks, LIVING_SHELLS.size());
    }

    // 玩家空壳重新加载时，依据持久化的 NBT 恢复其变形跟踪
    private static void reconstructLivingShell(PlayerShellEntity shell) {
        if (shell.getOriginNbt() == null && shell.getOriginPlayerUuid() == null) return;
        if (shell.getRemainingTicks() < 0) return;
        LIVING_SHELLS.put(shell.getUUID(), new LivingShellData(
            shell.getOriginNbt(),
            shell.getOriginPlayerUuid(),
            shell.getOriginKillerUuid(),
            shell.getRemainingTicks(),
            true));
    }

    // 生物壳/玩家空壳被杀死：连带杀死原生物（玩家/宠物/普通生物）
    public static void handleLivingShellDeath(ServerLevel level, LivingEntity shell, DamageSource source) {
        LivingShellData data = LIVING_SHELLS.remove(shell.getUUID());
        if (data == null) return;

        UUID playerUuid = data.playerUuid();
        UUID killerUuid = data.killerUuid();
        boolean isPlayerShell = data.isPlayerShell();
        BlockPos pos = shell.blockPosition();

        if (playerUuid != null) {
            // 原生物是玩家：还原游玩模式后，用自定义伤害击杀并播报“XX体验卡到期/过期了”
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUuid);
            if (player != null) {
                GameType original = PLAYER_ORIGINAL_GAMEMODE.getOrDefault(playerUuid, GameType.SURVIVAL);
                player.setGameMode(original);
                player.removeEffect(MobEffects.INVISIBILITY);
                player.setInvisible(false);
                // 被变成的壳死亡：恢复第一人称与原始缩放（避免死亡/重生后残留缩放）
                restoreTransmutationScale(player);
                restoreTransmutationCamera(player);
                player.hurt(livingShellExpiredDamage(level, isPlayerShell), Float.MAX_VALUE);
                sendTransmutationState(player, false, -1);
            }
            cleanupPlayerTransmutation(playerUuid);
        } else if (data.entityNbt() != null && data.entityNbt().contains("id")) {
            // 原生物是生物/宠物：重建并击杀结算
            handleLivingShellKillCredit(level, data.entityNbt(), killerUuid, pos,
                livingShellExpiredDamage(level, isPlayerShell));
        }
    }

    // 重建原生物并击杀；宠物用对应“体验卡”自定义伤害，普通生物用通用伤害
    private static void handleLivingShellKillCredit(ServerLevel level, CompoundTag originNbt,
            UUID killerUuid, BlockPos pos, DamageSource petDamageSource) {
        String entityId = originNbt.getString("id");
        if (!originNbt.contains("id")) return;
        ResourceLocation entityRl = ResourceLocation.tryParse(entityId);
        if (entityRl == null) return;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityRl);
        if (type == null) return;

        CompoundTag nbt = originNbt.copy();
        nbt.remove("UUID");

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.2;
        double z = pos.getZ() + 0.5;

        net.minecraft.nbt.ListTag posList = new net.minecraft.nbt.ListTag();
        posList.add(net.minecraft.nbt.DoubleTag.valueOf(x));
        posList.add(net.minecraft.nbt.DoubleTag.valueOf(y));
        posList.add(net.minecraft.nbt.DoubleTag.valueOf(z));
        nbt.put("Pos", posList);
        nbt.put("Motion", new net.minecraft.nbt.ListTag());
        nbt.getList("Motion", net.minecraft.nbt.Tag.TAG_DOUBLE).add(net.minecraft.nbt.DoubleTag.valueOf(0.0));
        nbt.getList("Motion", net.minecraft.nbt.Tag.TAG_DOUBLE).add(net.minecraft.nbt.DoubleTag.valueOf(0.0));
        nbt.getList("Motion", net.minecraft.nbt.Tag.TAG_DOUBLE).add(net.minecraft.nbt.DoubleTag.valueOf(0.0));

        java.util.Optional<Entity> opt = EntityType.create(nbt, level);
        if (opt.isEmpty() || !(opt.get() instanceof LivingEntity revived)) return;
        // 标记为“变形重建击杀”：跳过 /kill 存档（防批量处理时内存膨胀卡顿）
        revived.getPersistentData().putBoolean(TRANSMUTATION_REKILL_TAG, true);
        revived.setPos(x, y, z);
        level.addFreshEntity(revived);

        boolean isTamedPet = revived instanceof TamableAnimal tamed && tamed.getOwnerUUID() != null;

        if (killerUuid != null && !killerUuid.equals(new UUID(0, 0))) {
            ServerPlayer creditedPlayer = level.getServer().getPlayerList().getPlayer(killerUuid);
            if (creditedPlayer != null) {
                creditedPlayer.awardStat(net.minecraft.stats.Stats.ENTITY_KILLED.get(type));
            }
        }

        // 宠物：用绑定“体验卡”消息的自定义伤害击杀；普通生物：用通用伤害击杀
        DamageSource deathSource = isTamedPet ? petDamageSource : level.damageSources().genericKill();
        if (!revived.hurt(deathSource, Float.MAX_VALUE)) {
            revived.discard();
        }
    }

    // 与“玩家/生物体验卡到期/过期”死亡消息绑定的自定义伤害来源
    private static DamageSource livingShellExpiredDamage(ServerLevel level, boolean isPlayerShell) {
        return level.damageSources().source(isPlayerShell
            ? ModDamageTypes.TRANSMUTATION_PLAYER_EXPIRED.getKey()
            : ModDamageTypes.TRANSMUTATION_BIOM_EXPIRED.getKey());
    }

    // 每刻：倒计时递减、锁定玩家坐标，倒计时结束时变回原生物
    private static void tickLivingShells(net.minecraft.server.MinecraftServer server) {
        java.util.Iterator<Map.Entry<UUID, LivingShellData>> it = LIVING_SHELLS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, LivingShellData> entry = it.next();
            UUID uuid = entry.getKey();
            LivingShellData data = entry.getValue();
            // 跨维度查找壳体：壳体可能位于任意维度（玩家/生物可能被传送）
            ServerLevel shellLevel = null;
            Entity shell = null;
            for (ServerLevel l : server.getAllLevels()) {
                Entity e = l.getEntity(uuid);
                if (e != null) { shell = e; shellLevel = l; break; }
            }
            if (shell == null || !shell.isAlive()) {
                LOGGER.info("[DBG] tickLivingShells: SHELL MISSING/GONE uuid={} alive={} findNull={} mapSize={}",
                    uuid, shell != null && shell.isAlive(), shell == null, LIVING_SHELLS.size());
                // 死亡事件已负责击杀结算；实体消失（如卸载）则清除跟踪，
                // 同时清理该壳对应玩家残留的变形状态（防 TRANSMUTED_ENTITIES 卡死玩家）。
                if (data.playerUuid() != null) {
                    cleanupPlayerTransmutation(data.playerUuid());
                }
                it.remove();
                continue;
            }
            // 玩家变身：被变成的壳跟随玩家（玩家以冒险模式自由移动），离线则暂停倒计时等待其回归
            if (data.playerUuid() != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(data.playerUuid());
                if (player == null) {
                    LOGGER.info("[DBG] tickLivingShells: PLAYER OFFLINE uuid={} playerUuid={}",
                        uuid, data.playerUuid());
                    continue;
                }
                makeTransmutedFollowPlayer(shell, player);
            }
            LivingShellData nd = new LivingShellData(data.entityNbt(), data.playerUuid(),
                data.killerUuid(), data.remainingTicks() - 1, data.isPlayerShell());
            entry.setValue(nd);
            if (nd.remainingTicks() <= 0) {
                LOGGER.info("[DBG] tickLivingShells: COUNTDOWN ZERO -> revert uuid={} playerUuid={} dim={}",
                    uuid, data.playerUuid(), shellLevel.dimension().location());
                it.remove();
                BlockPos pos = shell.blockPosition();
                shell.discard();
                revertLivingShell(shellLevel, data, pos);
            }
        }
    }

    // 复原玩家空壳/生物壳：玩家恢复游玩模式并传送回来，普通生物重新生成回原形态
    private static void revertLivingShell(ServerLevel level, LivingShellData data, BlockPos pos) {
        if (data.playerUuid() != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(data.playerUuid());
            if (player != null) {
                GameType original = PLAYER_ORIGINAL_GAMEMODE.getOrDefault(player.getUUID(), GameType.SURVIVAL);
                player.setGameMode(original);
                player.removeEffect(MobEffects.INVISIBILITY);
                player.setInvisible(false);
                player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
                player.fallDistance = 0.0F;
                restoreTransmutationScale(player);
                restoreTransmutationCamera(player);
                sendTransmutationState(player, false, -1);
                LOGGER.info("[DBG] revertLivingShell: PLAYER restored playerUuid={} gamemode={}",
                    data.playerUuid(), player.gameMode.getGameModeForPlayer());
            } else {
                LOGGER.info("[DBG] revertLivingShell: PLAYER not found/offline playerUuid={}",
                    data.playerUuid());
            }
            cleanupPlayerTransmutation(data.playerUuid());
        } else if (data.entityNbt() != null && data.entityNbt().contains("id")) {
            CompoundTag nbt = data.entityNbt().copy();
            nbt.remove("UUID");
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.2;
            double z = pos.getZ() + 0.5;
            net.minecraft.nbt.ListTag posList = new net.minecraft.nbt.ListTag();
            posList.add(net.minecraft.nbt.DoubleTag.valueOf(x));
            posList.add(net.minecraft.nbt.DoubleTag.valueOf(y));
            posList.add(net.minecraft.nbt.DoubleTag.valueOf(z));
            nbt.put("Pos", posList);
            nbt.put("Motion", new net.minecraft.nbt.ListTag());
            nbt.getList("Motion", net.minecraft.nbt.Tag.TAG_DOUBLE).add(net.minecraft.nbt.DoubleTag.valueOf(0.0));
            nbt.getList("Motion", net.minecraft.nbt.Tag.TAG_DOUBLE).add(net.minecraft.nbt.DoubleTag.valueOf(0.0));
            nbt.getList("Motion", net.minecraft.nbt.Tag.TAG_DOUBLE).add(net.minecraft.nbt.DoubleTag.valueOf(0.0));
            java.util.Optional<Entity> opt = EntityType.create(nbt, level);
            if (opt.isPresent()) {
                Entity revived = opt.get();
                revived.setPos(x, y, z);
                level.addFreshEntity(revived);
                LOGGER.info("[DBG] revertLivingShell: MOB spawned id={} at={}",
                    nbt.getString("id"), pos);
            } else {
                LOGGER.info("[DBG] revertLivingShell: MOB spawn failed id={} at={}",
                    data.entityNbt().getString("id"), pos);
            }
        }
    }

    // ===== Creeper Clan 维度：贴近地面的空气方块随机触发 TNT 爆炸粒子 =====

    /** Creeper Clan 爆炸粒子：每个采样坐标每次触发概率（0.05 = 5%）。 */
    private static final double CREEPER_CLAN_BOOM_CHANCE = 0.05;
    /** Creeper Clan 爆炸粒子：单次触发时在触发点与相邻随机点之间，最多额外生成多少个爆炸点。 */
    private static final int CREEPER_CLAN_BOOM_EXTRA = 3;
    /** Creeper Clan 爆炸粒子：围绕玩家采样环的内半径（格）。4 区块（64 格）以外的区域才渲染。 */
    private static final int CREEPER_CLAN_BOOM_MIN_DIST = 64;
    /** Creeper Clan 爆炸粒子：围绕玩家采样环的外半径（格）。与刷怪上限距离一致，玩家可见。 */
    private static final int CREEPER_CLAN_BOOM_MAX_DIST = 128;
    /** Creeper Clan 爆炸粒子：每刻对每个玩家随机采样的坐标数量（控制开销）。 */
    private static final int CREEPER_CLAN_BOOM_SAMPLES_PER_PLAYER = 8;

    /**
     * 服务端每刻调用：在 Creeper Clan 维度中，距玩家 4 区块（64 格）以外、128 格以内的环形区域，
     * 贴近地面（该 xz 坐标最高的非空气方块）的空气方块，以每采样 5% 的概率随机触发 TNT 爆炸粒子动画。
     * <p>
     * 实现说明：原版「随机刻」逐方块触发开销过高，这里等价地改为——每刻对每个玩家周围
     * 64~128 格环形区域内随机采样若干 xz 坐标，用 {@code getHeight(MOTION_BLOCKING)} 定位地面，
     * 在地面之上（贴近地面的空气方块）处按 5% 概率播放爆炸粒子（EXPLOSION 闪白 + LARGE_SMOKE/Poof 烟雾），
     * 并在触发点相邻 1~3 格随机偏移处再补几个爆炸点，模拟一次小范围 TNT 爆炸的视觉效果。
     * 只发送粒子，不生成 TNT、不破坏方块、不播放爆炸音效。
     */
    private static void handleCreeperClanExplosionParticles(MinecraftServer server) {
        ServerLevel level = server.getLevel(ModDimensions.CREEPER_CLAN_DIM_LEVEL);
        if (level == null) return;
        if (level.players().isEmpty()) return;
        java.util.Random random = new java.util.Random(level.getGameTime() * 7919L + level.random.nextInt(100000));

        for (ServerPlayer player : level.players()) {
            double px = player.getX();
            double pz = player.getZ();
            java.util.List<ServerPlayer> viewers = level.players();
            for (int s = 0; s < CREEPER_CLAN_BOOM_SAMPLES_PER_PLAYER; s++) {
                if (random.nextDouble() >= CREEPER_CLAN_BOOM_CHANCE) continue;
                // 在 64~128 格环形区域内随机取一个方向与距离（避开玩家紧邻的 4 区块范围）
                double angle = random.nextDouble() * Math.PI * 2.0;
                double dist = CREEPER_CLAN_BOOM_MIN_DIST
                    + random.nextDouble() * (CREEPER_CLAN_BOOM_MAX_DIST - CREEPER_CLAN_BOOM_MIN_DIST);
                int x = (int) Math.floor(px + Math.cos(angle) * dist);
                int z = (int) Math.floor(pz + Math.sin(angle) * dist);
                spawnCreeperClanBoom(level, x, z, random, viewers);
            }
        }
    }

    /** 在指定 xz 坐标的地面之上触发一次「模拟 TNT 爆炸」粒子组。
     *  用 longDistance=true 逐玩家发送粒子包，强制客户端无视距离渲染（否则 64 格外的粒子会被跳过）。 */
    private static void spawnCreeperClanBoom(ServerLevel level, int x, int z, java.util.Random random,
                                             java.util.List<ServerPlayer> viewers) {
        int groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
        if (groundY <= level.getMinBuildHeight()) return;
        // 贴近地面的空气方块：地面之上 1~2 格内
        int baseY = groundY + 1;
        BlockState above = level.getBlockState(new net.minecraft.core.BlockPos(x, baseY, z));
        if (!above.isAir()) return; // 地面之上不是空气（如树/水），跳过

        // 触发点本体：爆炸白闪 + 大烟雾
        double bx = x + 0.5;
        double by = baseY + 0.5;
        double bz = z + 0.5;
        sendParticlesLongDistance(level, viewers,
            net.minecraft.core.particles.ParticleTypes.EXPLOSION, bx, by, bz, 1, 0.0, 0.0, 0.0, 0.0);
        sendParticlesLongDistance(level, viewers,
            net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, bx, by, bz, 3, 0.3, 0.3, 0.3, 0.02);

        // 相邻 1~3 格随机偏移处再补爆炸点
        int extra = 1 + random.nextInt(CREEPER_CLAN_BOOM_EXTRA);
        for (int i = 0; i < extra; i++) {
            int ox = x + random.nextInt(7) - 3;
            int oz = z + random.nextInt(7) - 3;
            // 偏移点的地面可能不同，用其自身列的最高地面定位贴近空气层
            int oy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, ox, oz);
            if (oy <= level.getMinBuildHeight()) continue;
            BlockState oAbove = level.getBlockState(new net.minecraft.core.BlockPos(ox, oy + 1, oz));
            if (!oAbove.isAir()) continue;
            double oxx = ox + 0.5;
            double oyy = oy + 1.5;
            double ozz = oz + 0.5;
            sendParticlesLongDistance(level, viewers,
                net.minecraft.core.particles.ParticleTypes.EXPLOSION, oxx, oyy, ozz, 1, 0.0, 0.0, 0.0, 0.0);
            sendParticlesLongDistance(level, viewers,
                net.minecraft.core.particles.ParticleTypes.POOF, oxx, oyy, ozz, 2, 0.3, 0.3, 0.3, 0.01);
        }
    }

    /** 对维度内每个玩家发送长距离（longDistance=true）粒子包，强制客户端无视距离渲染。
     *  与广播 sendParticles 不同，广播版 longDistance=false，客户端会跳过相机距离过远的粒子。 */
    private static void sendParticlesLongDistance(ServerLevel level, java.util.List<ServerPlayer> viewers,
                                                  net.minecraft.core.particles.ParticleOptions particle,
                                                  double x, double y, double z, int count,
                                                  double dx, double dy, double dz, double speed) {
        for (ServerPlayer viewer : viewers) {
            level.sendParticles(viewer, particle, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    // ===== Creeper Clan 传送门激活逻辑 =====

    /** Creeper Clan 传送门点燃的 TNT 引信时长（游戏刻）。 */
    public static final int CREEPER_PORTAL_FUSE_TICKS = 80;

    /**
     * 点火激活 Creeper Clan 传送门：
     * 1. 识别框架内所有 TNT（含边角）并同时点燃——替换为引燃的 {@link net.minecraft.world.entity.item.PrimedTnt}
     *    （fuse=120、NoGravity=true、无速度悬浮）；
     * 2. 记录这些 TNT 的原始位置（用于复原）与传送门框架信息；
     * 3. 安排 120 刻后的延迟任务：把 TNT 实体移除（避免原版爆炸破坏地形），在每个位置各触发一次
     *    「不破坏地形、不伤害生物、仅冲击波」的爆炸，随后复原 TNT 方块并开启传送门。
     * 由于 ServerTickEvent.Pre 早于实体 tick，延迟任务会先移除 TNT，杜绝原版破坏性爆炸。
     */
    public static void activateCreeperPortal(Level level, cn.autoforged.joes_addons_for_abmc.block.CreeperPortalShape shape) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        java.util.List<BlockPos> tntPositions = shape.collectFrameTnt();
        if (tntPositions.isEmpty()) return;

        // 同时点燃所有框架 TNT（替换为引燃的 TNT 实体：悬浮、无速度、80 刻引信）。
        // Y 用方块底部坐标（与原版 TntBlock 点燃一致），避免 TNT 实体上浮半格。
        // 每个 TNT 点燃时都播放「TNT 被激活」音效（与原版 TntBlock 点火一致）。
        java.util.List<net.minecraft.world.entity.item.PrimedTnt> tntEntities = new java.util.ArrayList<>();
        for (BlockPos pos : tntPositions) {
            if (!serverLevel.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.TNT)) continue;
            serverLevel.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            net.minecraft.world.entity.item.PrimedTnt tnt = new net.minecraft.world.entity.item.PrimedTnt(
                serverLevel, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, null);
            tnt.setFuse(CREEPER_PORTAL_FUSE_TICKS);
            tnt.setNoGravity(true);
            tnt.setDeltaMovement(0.0, 0.0, 0.0);
            serverLevel.addFreshEntity(tnt);
            serverLevel.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(),
                net.minecraft.sounds.SoundEvents.TNT_PRIMED,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            serverLevel.gameEvent(null, net.minecraft.world.level.gameevent.GameEvent.PRIME_FUSE,
                net.minecraft.core.BlockPos.containing(tnt.position()));
            tntEntities.add(tnt);
        }

        // 记录「爆炸后复原的 TNT 位置」（即使 TNT 被点燃移除，也要在爆炸后放回原处）
        java.util.List<BlockPos> restorePositions = new java.util.ArrayList<>(tntPositions);

        addCommandTextTask(CREEPER_PORTAL_FUSE_TICKS, () -> {
            // 1) 移除所有 TNT 实体（避免其原版爆炸破坏地形）
            for (net.minecraft.world.entity.item.PrimedTnt tnt : tntEntities) {
                if (tnt != null && !tnt.isRemoved()) {
                    tnt.discard();
                }
            }
            // 2) 每个 TNT 位置各触发一次「无破坏/无伤害/仅冲击波」的爆炸
            for (BlockPos pos : restorePositions) {
                creeperPortalShockwave(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            }
            // 3) 复原框架 TNT 方块（若边角原来也是 TNT 也会一并复原）
            for (BlockPos pos : restorePositions) {
                if (serverLevel.getBlockState(pos).isAir()
                    || serverLevel.getBlockState(pos).getBlock()
                        == net.minecraft.world.level.block.Blocks.FIRE) {
                    serverLevel.setBlock(pos, net.minecraft.world.level.block.Blocks.TNT.defaultBlockState(), 3);
                }
            }
            // 4) 开启传送门
            shape.createPortalBlocks();
        });
    }

    /**
     * 在指定位置触发一次「不破坏地形、不伤害生物、仅产生冲击波」的 TNT 爆炸。
     * 通过自定义 {@link net.minecraft.world.level.ExplosionDamageCalculator} 关闭对实体的伤害，
     * 并用 {@link net.minecraft.world.level.Level.ExplosionInteraction#NONE} 关闭方块破坏。
     * 爆炸音效用 {@link net.minecraft.sounds.SoundEvents#EMPTY}（无声），避免产生爆炸音效。
     */
    private static void creeperPortalShockwave(ServerLevel level, double x, double y, double z) {
        net.minecraft.world.level.ExplosionDamageCalculator calc = new net.minecraft.world.level.ExplosionDamageCalculator() {
            @Override
            public boolean shouldDamageEntity(net.minecraft.world.level.Explosion explosion, net.minecraft.world.entity.Entity entity) {
                return false; // 不伤害生物（冲击波仍保留击退）
            }
        };
        level.explode(
            null,
            net.minecraft.world.level.Explosion.getDefaultDamageSource(level, null),
            calc,
            x, y, z,
            4.0F,
            false,
            net.minecraft.world.level.Level.ExplosionInteraction.NONE,
            net.minecraft.core.particles.ParticleTypes.EXPLOSION,
            net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
            net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(net.minecraft.sounds.SoundEvents.EMPTY));
    }

    // ===== 沉船溺尸刷新逻辑 =====

    /** 服务端每刻调用：周期性补刷「世界生成必刷」持久批次 + 10~20 分钟随机间隔刷新常规批次。 */
    private static void handleShipwreckDrownedTick(MinecraftServer server) {
        // 1) 世界生成必刷：每 SHIPWRECK_INITIAL_CHECK_INTERVAL 刻检查已加载沉船，为每座未刷过的补一波持久溺尸
        //    （不在 ChunkEvent.Load 里同步生成，避免在区块生成流程中强制加载区块导致卡地形加载）
        if (++shipwreckInitialCheckTick >= SHIPWRECK_INITIAL_CHECK_INTERVAL) {
            shipwreckInitialCheckTick = 0;
            for (ServerLevel level : server.getAllLevels()) {
                // 原版沉船只会生成在主世界海面下
                if (!Level.OVERWORLD.equals(level.dimension())) continue;
                tryInitialShipwreckSpawns(level);
            }
        }

        // 2) 10~20 分钟周期刷新：到点前正常累加；一旦到点但条件未满足（光照、玩家距离、领袖已存在等），保持到点等待
        if (shipwreckDrownedTick < shipwreckSpawnInterval) {
            shipwreckDrownedTick++;
        }
        if (shipwreckDrownedTick < shipwreckSpawnInterval) return; // 尚未到点

        // 到点后：条件未满足时暂停计时，并降频（每 40 刻 ≈ 2 秒）重试，
        // 避免每个 tick 都做整轮沉船扫描拖慢服务端主线程（也影响退出存档保存）。
        if (shipwreckRetryTick++ % SHIPWRECK_RETRY_INTERVAL != 0) return;

        boolean spawnedAny = false;
        for (ServerLevel level : server.getAllLevels()) {
            // 原版沉船只会生成在主世界海面下
            if (!Level.OVERWORLD.equals(level.dimension())) continue;
            spawnedAny |= trySpawnShipwreckBatches(level);
        }

        // 只有真正刷出一波才重新计时并取新的 10~20 分钟随机间隔；
        // 否则保持到点状态，降频重试（直到成功刷出一波才重新计时）。
        if (spawnedAny) {
            shipwreckDrownedTick = 0;
            shipwreckRetryTick = 0;
            shipwreckSpawnInterval = server.overworld().getRandom()
                .nextIntBetweenInclusive(SHIPWRECK_MIN_INTERVAL_TICKS, SHIPWRECK_MAX_INTERVAL_TICKS);
        }
    }

    /** 扫描主世界已加载的沉船，为每座尚未刷过「世界生成必刷批次」的沉船补刷一波持久溺尸（光照不满足则下次再试）。 */
    private static void tryInitialShipwreckSpawns(ServerLevel level) {
        java.util.Set<BlockPos> processedStarts = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            ChunkPos center = new ChunkPos(player.blockPosition());
            for (int cx = center.x - SHIPWRECK_SCAN_RADIUS_CHUNKS;
                 cx <= center.x + SHIPWRECK_SCAN_RADIUS_CHUNKS; cx++) {
                for (int cz = center.z - SHIPWRECK_SCAN_RADIUS_CHUNKS;
                     cz <= center.z + SHIPWRECK_SCAN_RADIUS_CHUNKS; cz++) {
                    // 绝不强制生成区块：仅检查已生成到 STRUCTURE_STARTS 的区块
                    ChunkAccess chunk = level.getChunkSource()
                        .getChunk(cx, cz, ChunkStatus.STRUCTURE_STARTS, false);
                    if (chunk == null) continue;
                    for (StructureStart start : chunk.getAllStarts().values()) {
                        if (!start.isValid() || !isShipwreck(level, start.getStructure())) continue;
                        // 用实际地形高度修正包围盒 Y（原版包围盒 Y 在新生成区块中不可信）
                        BoundingBox box = effectiveShipwreckBox(level, start);
                        // 同一座沉船本周期只处理一次（用包围盒中心去重）
                        if (!processedStarts.add(box.getCenter())) continue;
                        long key = box.getCenter().asLong();
                        // 已刷过初始必刷批次的沉船跳过
                        if (SHIPWRECK_INITIAL_SPAWNED.contains(key)) continue;
                        // 刷出成功才记入已处理；条件不满足时保持未处理，下次继续尝试
                        // 第一波无视光照：白天也能在沉船周围刷出（PersistenceRequired=true）
                        if (spawnShipwreckDrownedGroup(level, box, level.getRandom(), true, true)) {
                            SHIPWRECK_INITIAL_SPAWNED.add(key);
                        }
                    }
                }
            }
        }
    }

    /**
     * 原版问题（PaperMC#10975）：新生成的区块中 StructureStart.getBoundingBox() 的 Y 是硬编码值
     * （沉船为 Y=90），结构被放置到实际地形高度后该包围盒并未同步，导致其 Y 偏高而不可信（重启后正常）。
     * 这里用结构中心列实际最顶端的“非空气且非流体”方块高度来修正 Y（XZ 始终可信，保持不变）。
     */
    private static BoundingBox effectiveShipwreckBox(ServerLevel level, StructureStart start) {
        BoundingBox box = start.getBoundingBox();
        BlockPos center = box.getCenter();
        int topY = center.getY(); // 兜底：中心列扫描不到实心方块时退回结构包围盒中心 Y
        for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--) {
            BlockState state = level.getBlockState(new BlockPos(center.getX(), y, center.getZ()));
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                topY = y;
                break;
            }
        }
        // 沉船船体高度通常不超过 10 格，取「船顶下方 12 格 ~ 船顶上方 2 格」作为生成 Y 范围
        int minY = Math.max(topY - 12, level.getMinBuildHeight());
        int maxY = Math.min(topY + 2, level.getMaxBuildHeight());
        return new BoundingBox(box.minX(), minY, box.minZ(), box.maxX(), maxY, box.maxZ());
    }

    /** 扫描主世界已加载的沉船并按条件刷新；返回本批次是否真实刷出了至少一波。 */
    private static boolean trySpawnShipwreckBatches(ServerLevel level) {
        java.util.Set<BlockPos> processedStarts = new HashSet<>();
        boolean spawnedAny = false;
        for (ServerPlayer player : level.players()) {
            ChunkPos center = new ChunkPos(player.blockPosition());
            for (int cx = center.x - SHIPWRECK_SCAN_RADIUS_CHUNKS;
                 cx <= center.x + SHIPWRECK_SCAN_RADIUS_CHUNKS; cx++) {
                for (int cz = center.z - SHIPWRECK_SCAN_RADIUS_CHUNKS;
                     cz <= center.z + SHIPWRECK_SCAN_RADIUS_CHUNKS; cz++) {
                    // 绝不强制生成区块：仅检查已生成到 STRUCTURE_STARTS 的区块
                    ChunkAccess chunk = level.getChunkSource()
                        .getChunk(cx, cz, ChunkStatus.STRUCTURE_STARTS, false);
                    if (chunk == null) continue;
                    for (StructureStart start : chunk.getAllStarts().values()) {
                        if (!start.isValid() || !isShipwreck(level, start.getStructure())) continue;
                        // 用实际地形高度修正包围盒 Y（原版包围盒 Y 在新生成区块中不可信）
                        BoundingBox box = effectiveShipwreckBox(level, start);
                        // 同一座沉船本周期只刷一次（用包围盒中心去重）
                        if (!processedStarts.add(box.getCenter())) continue;
                        // 沉船周围 50 格内若已存在溺尸领袖，则本波不再刷新
                        if (hasShipwreckLeaderNearby(level, box)) continue;
                        // 玩家距结构过近(≤24)或过远(>120)也不刷新
                        if (!hasPlayerAtValidDistance(level, box)) continue;
                        if (spawnShipwreckDrownedGroup(level, box, level.getRandom(), false, false)) {
                            spawnedAny = true;
                        }
                    }
                }
            }
        }
        return spawnedAny;
    }

    /** 玩家距沉船结构的最近距离是否落在有效区间 (24, 120] 内。 */
    private static boolean hasPlayerAtValidDistance(ServerLevel level, BoundingBox box) {
        double minDistSq = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            double dx = clampToAxis(p.getX(), box.minX(), box.maxX() + 1);
            double dy = clampToAxis(p.getY(), box.minY(), box.maxY() + 1);
            double dz = clampToAxis(p.getZ(), box.minZ(), box.maxZ() + 1);
            double dsq = dx * dx + dy * dy + dz * dz;
            if (dsq < minDistSq) minDistSq = dsq;
        }
        double minDist = Math.sqrt(minDistSq);
        return minDist > SHIPWRECK_PLAYER_MIN_DISTANCE && minDist <= SHIPWRECK_PLAYER_MAX_DISTANCE;
    }

    /** 计算坐标相对一条轴区间 [min,max) 的最短偏移量（区间内为 0）。 */
    private static double clampToAxis(double v, double min, double max) {
        return v < min ? min - v : (v > max ? v - max : 0.0);
    }

    /** 判断结构是否为原版沉船（minecraft:shipwreck）。 */
    private static boolean isShipwreck(ServerLevel level, Structure structure) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE)
            .getResourceKey(structure).map(key -> key.equals(SHIPWRECK_STRUCTURE)).orElse(false);
    }

    /** 沉船周围 SHIPWRECK_LEADER_EXIST_RANGE 格内是否已存在溺尸领袖。 */
    private static boolean hasShipwreckLeaderNearby(ServerLevel level, BoundingBox box) {
        AABB area = new AABB(box.minX() - SHIPWRECK_LEADER_EXIST_RANGE, box.minY() - SHIPWRECK_LEADER_EXIST_RANGE,
            box.minZ() - SHIPWRECK_LEADER_EXIST_RANGE, box.maxX() + 1 + SHIPWRECK_LEADER_EXIST_RANGE,
            box.maxY() + 1 + SHIPWRECK_LEADER_EXIST_RANGE, box.maxZ() + 1 + SHIPWRECK_LEADER_EXIST_RANGE);
        // 原版水下也可有溺尸，这里直接按坐标区域检索本 mod 标记过的领袖
        return level.getEntitiesOfClass(Drowned.class, area, e ->
            e != null && e.getPersistentData().getBoolean(SHIPWRECK_LEADER_TAG)).stream()
            .findAny().isPresent();
    }

    /** 在一座沉船结构周围 ±10 格范围内刷新 10~20 只溺尸，其中必有 1 只为领袖（全套钻石甲+三叉戟）。
     * @param persistenceRequired 为 true 时所有溺尸设为 PersistenceRequired（不计入刷怪上限/不被清除）。
     * @param ignoreLight 为 true 时忽略光照抑制（仅用于「世界生成必刷」的第一波，白天也能刷出）。
     * @return 是否至少刷出了 1 只（用于判断本波是否真实成交）。 */
    private static boolean spawnShipwreckDrownedGroup(ServerLevel level, BoundingBox box, RandomSource random,
                                                      boolean persistenceRequired, boolean ignoreLight) {
        int count = random.nextIntBetweenInclusive(SHIPWRECK_DROWNED_MIN, SHIPWRECK_DROWNED_MAX);
        java.util.List<BlockPos> spots = new ArrayList<>();
        int attempts = 200;
        while (spots.size() < count && attempts-- > 0) {
            BlockPos pos = randomSpotAround(level, box, random);
            if (canSpawnDrownedAt(level, pos, ignoreLight)) {
                spots.add(pos);
            }
        }
        if (spots.isEmpty()) return false; // 条件全部抑制时本批不刷

        // 领袖取第一个合法生成位，其余为普通溺尸
        spawnShipwreckLeader(level, spots.get(0), random, persistenceRequired);
        for (int i = 1; i < spots.size(); i++) {
            Drowned drowned = EntityType.DROWNED.create(level);
            if (drowned == null) continue;
            drowned.moveTo(spots.get(i).getX() + 0.5, spots.get(i).getY(), spots.get(i).getZ() + 0.5);
            if (persistenceRequired) drowned.setPersistenceRequired();
            level.addFreshEntity(drowned);
        }
        return true;
    }

    /** 在沉船结构包围盒 ±10 格范围内取一个随机整数坐标。 */
    private static BlockPos randomSpotAround(ServerLevel level, BoundingBox box, RandomSource random) {
        int x = random.nextIntBetweenInclusive(box.minX() - SHIPWRECK_SPAWN_RANGE,
            box.maxX() + SHIPWRECK_SPAWN_RANGE);
        int y = random.nextIntBetweenInclusive(box.minY() - SHIPWRECK_SPAWN_RANGE,
            box.maxY() + SHIPWRECK_SPAWN_RANGE);
        int z = random.nextIntBetweenInclusive(box.minZ() - SHIPWRECK_SPAWN_RANGE,
            box.maxZ() + SHIPWRECK_SPAWN_RANGE);
        return new BlockPos(x, y, z);
    }

    /** 生成条件判定：生成位及其上方一格均须为非实心（水或空气皆可，贴合沉船周围水体，也避免卡进船体）；光照可抑制刷新。 */
    private static boolean canSpawnDrownedAt(ServerLevel level, BlockPos pos, boolean ignoreLight) {
        if (!ignoreLight && level.getMaxLocalRawBrightness(pos) > SHIPWRECK_SPAWN_MAX_LIGHT) return false;
        return !level.getBlockState(pos).isSolid()
            && !level.getBlockState(pos.above()).isSolid();
    }

    /** 生成溺尸领袖：全套钻石甲、手持三叉戟、装备掉落率全为 0、被玩家击杀掉 4 倍经验，并套用原版「领袖」机制。 */
    private static void spawnShipwreckLeader(ServerLevel level, BlockPos pos, RandomSource random, boolean persistenceRequired) {
        Drowned leader = EntityType.DROWNED.create(level);
        if (leader == null) return;
        leader.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        leader.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        leader.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        leader.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        leader.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        leader.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        leader.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        // 装备掉落率均为 0（盔甲 + 三叉戟都不掉落）
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            leader.setDropChance(slot, 0.0F);
        }

        // 套用原版「领袖溺尸」机制：把 spawn_reinforcements（刷增援概率）基础值设入原版领袖区间 0.5~0.75，
        // 使其在原版领袖机制下可正常召唤增援（而非自创新机制）。
        Holder<Attribute> reinforcements = level.registryAccess().registryOrThrow(Registries.ATTRIBUTE)
            .getHolder(ResourceLocation.withDefaultNamespace("spawn_reinforcements"))
            .orElse(null);
        if (reinforcements != null) {
            AttributeInstance inst = leader.getAttribute(reinforcements);
            if (inst != null) {
                inst.setBaseValue(0.5 + random.nextDouble() * 0.25);
            }
        }

        leader.getPersistentData().putBoolean(SHIPWRECK_LEADER_TAG, true);
        if (persistenceRequired) leader.setPersistenceRequired();
        level.addFreshEntity(leader);
    }

    /** 沉船领袖被玩家或其驯服狼击杀时，额外补足 3 倍经验（加上原版基础经验共 4 倍）。 */
    private static void handleShipwreckLeaderExpReward(LivingEntity dead, DamageSource source) {
        if (!(dead instanceof Drowned)) return;
        if (!dead.getPersistentData().getBoolean(SHIPWRECK_LEADER_TAG)) return;
        Entity direct = source.getDirectEntity();
        Entity cause = source.getEntity();
        boolean byPlayer = cause instanceof Player || direct instanceof Player;
        boolean byTamedWolf = (cause instanceof Wolf w && w.isTame()) || (direct instanceof Wolf dw && dw.isTame());
        if (!byPlayer && !byTamedWolf) return;
        if (dead.level() instanceof ServerLevel level) {
            int base = dead.getExperienceReward(level, dead);
            if (base > 0) {
                ExperienceOrb.award(level, dead.position(), base * 3);
            }
        }
    }

    // 生物壳/玩家空壳被杀死时结算击杀（玩家空壳的 die() 也会调用 handleLivingShellDeath，
    // 此处 map 已移除对应条目，第二次调用会直接返回，不会重复结算）。
    private static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) return;
        // 玩家“渲染替换”变身期间死亡：清除变形状态（重生后不再残留生物渲染），本体死亡即“生物死亡”
        if (event.getEntity() instanceof ServerPlayer deadSp && MORPH_REMAINING.containsKey(deadSp.getUUID())) {
            revertMorphRenderPlayer(deadSp);
        }
        handleShipwreckLeaderExpReward(event.getEntity(), event.getSource());
        handleLivingShellDeath(serverLevel, event.getEntity(), event.getSource());
        if (event.getEntity().getPersistentData().getBoolean(WITCH_BOSS_TAG)) {
            // 若正显示它的Boss条，则在被击杀时立即移除（updateWitchBossBar 也会自动兜底清理）
            ServerBossEvent be = WITCH_BOSS_EVENTS.remove(event.getEntity().getUUID());
            if (be != null) {
                be.setVisible(false);
                be.removeAllPlayers();
            }
            handleWitchBossDefeatReward(serverLevel, event.getEntity(), event.getSource());
        }
        // TNT 权杖特制苦力怕炸死玩家/宠物：播报专属死亡信息（被自己/被某人召唤的苦力怕）。
        if (event.getSource().getEntity() instanceof TntStaffCreeper creeper) {
            handleTntStaffCreeperDeath(serverLevel, event.getEntity(), creeper);
        }
        // Him 权杖头颅击杀直接复用原版死亡消息（death.attack.herobrine.head），无需在此手动播报。
        // /revive 机制：捕获被 /kill 命令（genericKill 伤害）杀死的非玩家生物，存档供 /revive 复活。
        // 使用 entity.save() 存储完整实体信息（含 id），与变形药水的存储格式一致。
        // 注意：变形物品被摧毁/被漏斗吸走时重建并击杀的原生物同样走 genericKill，但它们不是
        // 真正的 /kill 击杀，且批量发生时会把大量完整 NBT 塞进内存导致膨胀与卡顿，需跳过。
        LivingEntity dead = event.getEntity();
        if (!(dead instanceof Player)
            && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)
            && !dead.getPersistentData().getBoolean(TRANSMUTATION_REKILL_TAG)) {
            ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType());
            if (typeId != null) {
                CompoundTag nbt = new CompoundTag();
                dead.save(nbt);
                // 保存的是死亡瞬间的数据：生命已为 0、死亡/受击计数值已写入。
                // 移除这些字段，避免复活时再次播放死亡动画并立即死亡。
                nbt.remove("Health");
                nbt.remove("DeathTime");
                nbt.remove("HurtTime");
                KILLED_ENTITY_STORAGE
                    .computeIfAbsent(typeId, k -> new java.util.ArrayList<>()).add(nbt);
            }
        }
    }

    /**
     * 女巫Boss被击败的奖励：授予击杀者“变形药水掷出者免疫”（对自己丢出的变形药水永久免疫）。
     * 通过 Source.getEntity() 识别击杀者；离线/非玩家击杀不发放。
     */
    private static void handleWitchBossDefeatReward(ServerLevel level, LivingEntity dead, DamageSource source) {
        if (!(source.getEntity() instanceof Player killer)) return;
        ServerPlayer sp = level.getServer().getPlayerList().getPlayer(killer.getUUID());
        if (sp == null) return;
        boolean wasImmune = sp.getPersistentData().getBoolean(TRANSFORM_POTION_IMMUNE_TAG);
        sp.getPersistentData().putBoolean(TRANSFORM_POTION_IMMUNE_TAG, true);
        sp.displayClientMessage(Component.literal("§a击败女巫Boss！变形药水将不再影响掷出者（你自己）。"
            + (wasImmune ? "（此前已免疫）" : "")), true);
    }

    /**
     * TNT 权杖特制苦力怕炸死玩家/宠物时，播报专属死亡信息：
     * - 死亡者就是该苦力怕的召唤者（投掷者本人）→“XX被自己召唤的苦力怕炸死了”；
     * - 否则（其他玩家 / 被驯服宠物 / 女仆）→“XX被[召唤者]召唤的苦力怕炸死了”。
     * 非玩家、非宠物的普通生物不在死亡信息里的 chat 中播报（保持原版行为）。
     */
    private static void handleTntStaffCreeperDeath(ServerLevel level, LivingEntity dead, TntStaffCreeper creeper) {
        boolean isPlayer = dead instanceof Player;
        boolean isPet = dead instanceof TamableAnimal tamed && tamed.getOwnerUUID() != null;
        // 未安装车万女仆时必须短路，避免加载不到 EntityMaid 时抛 NoClassDefFoundError。
        boolean isMaid = isTouhouMaid(dead);
        if (!isPlayer && !isPet && !isMaid) return;

        Component victim = dead.getDisplayName();
        UUID summonerUuid = creeper.getOwnerUuid();
        Component message;
        if (summonerUuid != null && summonerUuid.equals(dead.getUUID())) {
            // 投掷者本人被自己召唤的苦力怕炸死
            message = Component.translatable("message.joes_tnt_staff_creeper.self", victim);
        } else {
            message = Component.translatable("message.joes_tnt_staff_creeper.other",
                victim, resolveSummonerName(level, summonerUuid));
        }
        if (level.getServer() != null) {
            level.getServer().getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    /** 解析召唤者显示名：在线玩家优先，离线用 ProfileCache 兜底，都找不到则用占位文案。 */
    private static Component resolveSummonerName(ServerLevel level, @Nullable UUID summonerUuid) {
        MinecraftServer server = level.getServer();
        if (summonerUuid == null || server == null) {
            return Component.translatable("message.joes_tnt_staff_creeper.unknown");
        }
        ServerPlayer online = server.getPlayerList().getPlayer(summonerUuid);
        if (online != null) return online.getDisplayName();
        if (server.getProfileCache() != null) {
            Optional<GameProfile> profile = server.getProfileCache().get(summonerUuid);
            if (profile.isPresent() && profile.get().getName() != null) {
                return Component.literal(profile.get().getName());
            }
        }
        return Component.translatable("message.joes_tnt_staff_creeper.unknown");
    }

    private static void onBlockBreakTransmutation(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        BlockPos pos = event.getPos();
        ResourceLocation dimId = ((Level) event.getLevel()).dimension().location();
        Map<BlockPos, java.util.List<TransmutationData>> dimMap = BLOCK_TRANSMUTATIONS.get(dimId);
        if (dimMap == null) return;
        java.util.List<TransmutationData> list = dimMap.remove(pos);
        if (list == null || list.isEmpty()) return;

        net.minecraft.server.level.ServerLevel serverLevel = (ServerLevel) event.getLevel();

        // 该方块是生物变身而来：阻止原版掉落（不掉落方块材料），
        // 手动静默移除方块，只结算击杀（掉落生物本身的战利品）。
        event.setCanceled(true);
        event.getPlayer().awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(event.getState().getBlock()));
        event.getPlayer().causeFoodExhaustion(0.005F);
        serverLevel.destroyBlock(pos, false, null);

        for (TransmutationData data : list) {
            if (data.playerUuid() != null) {
                killTransmutedPlayer(serverLevel, data, pos, true);
            } else {
                handleTransmutationKillCredit(serverLevel, data, pos, event.getPlayer().getUUID());
            }
        }
    }

    // 爆炸破坏时同样结算击杀：移除变身数据并静默清除方块（放弃材料掉落），掉生物战利品
    private static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        // 附魔生物·火矢：带火焰爆炸的爆炸性弹射物，在其爆炸影响区域放火（参考下界床/主世界重生锚）。
        if (!FIRE_EXPLOSIVE_PROJECTILES.isEmpty()) {
            boolean foundFire = false;
            Entity direct = event.getExplosion().getDirectSourceEntity();
            if (direct != null && FIRE_EXPLOSIVE_PROJECTILES.remove(direct.getId()) != null) {
                foundFire = true;
            }
            if (foundFire) {
                for (BlockPos pos : event.getAffectedBlocks()) {
                    if (serverLevel.getBlockState(pos).isAir() && serverLevel.random.nextFloat() < 0.3F) {
                        serverLevel.setBlock(pos, Blocks.FIRE.defaultBlockState(), 3);
                    }
                }
            }
        }

        ResourceLocation dimId = serverLevel.dimension().location();
        Map<BlockPos, java.util.List<TransmutationData>> dimMap = BLOCK_TRANSMUTATIONS.get(dimId);
        if (dimMap == null) return;

        java.util.List<BlockPos> affected = event.getAffectedBlocks();
        java.util.List<BlockPos> toHandle = new java.util.ArrayList<>();
        for (BlockPos pos : affected) {
            if (dimMap.containsKey(pos)) toHandle.add(pos);
        }

        for (BlockPos pos : toHandle) {
            java.util.List<TransmutationData> list = dimMap.remove(pos);
            serverLevel.destroyBlock(pos, false, null);
            if (list != null) {
                for (TransmutationData data : new java.util.ArrayList<>(list)) {
                    if (data.playerUuid() != null) {
                        killTransmutedPlayer(serverLevel, data, pos, true);
                    } else {
                        handleTransmutationKillCredit(serverLevel, data, pos, null);
                    }
                }
            }
        }

        if (!toHandle.isEmpty()) {
            affected.removeAll(toHandle);
        }
    }

    // 真正的击杀结算：重建原生物，并由责任玩家击杀，触发原版完整的击杀流程
    // （怪物猎人成就、玩家击杀统计、击杀分数与经验）。即使没有责任玩家，
    // 也会用通用伤害击杀，以保证被命名的宠物仍能播报“体验卡过期”与原版死亡消息。
    private static void handleTransmutationKillCredit(ServerLevel serverLevel, TransmutationData data,
            BlockPos pos, UUID destroyerUuid) {
        String entityId = data.entityNbt.getString("id");
        if (!data.entityNbt.contains("id")) return;
        ResourceLocation entityRl = ResourceLocation.tryParse(entityId);
        if (entityRl == null) return;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityRl);
        if (type == null) return;

        boolean isBlock = BuiltInRegistries.BLOCK.containsKey(ResourceLocation.tryParse(data.itemType));

        CompoundTag nbt = data.entityNbt.copy();
        nbt.remove("UUID");

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.2;
        double z = pos.getZ() + 0.5;

        net.minecraft.nbt.ListTag posList = new net.minecraft.nbt.ListTag();
        posList.add(net.minecraft.nbt.DoubleTag.valueOf(x));
        posList.add(net.minecraft.nbt.DoubleTag.valueOf(y));
        posList.add(net.minecraft.nbt.DoubleTag.valueOf(z));
        nbt.put("Pos", posList);
        nbt.put("Motion", new net.minecraft.nbt.ListTag());
        net.minecraft.nbt.ListTag motion = nbt.getList("Motion", net.minecraft.nbt.Tag.TAG_DOUBLE);
        motion.add(net.minecraft.nbt.DoubleTag.valueOf(0.0));
        motion.add(net.minecraft.nbt.DoubleTag.valueOf(0.0));
        motion.add(net.minecraft.nbt.DoubleTag.valueOf(0.0));

        java.util.Optional<Entity> opt = EntityType.create(nbt, serverLevel);
        if (opt.isEmpty() || !(opt.get() instanceof LivingEntity revived)) {
            return;
        }
        // 标记为“变形重建击杀”：onLivingDeath 将跳过把它存进 /kill 存档，
        // 防止批量吸走/摧毁变形物品时在内存里堆积大量完整 NBT 造成卡顿。
        revived.getPersistentData().putBoolean(TRANSMUTATION_REKILL_TAG, true);
        revived.setPos(x, y, z);
        serverLevel.addFreshEntity(revived);

        // 只有“宠物”（被驯服的 TamableAnimal，如狼/猫）才绑定“体验卡过期”的死亡消息。
        // 它通过自定义伤害类型被击杀，由原版死亡播报展示文案，因此不会出现双重播报。
        boolean isTamedPet = revived instanceof TamableAnimal tamed && tamed.getOwnerUUID() != null;

        UUID effectiveKiller = data.killerPlayerUuid;
        if (effectiveKiller.equals(new UUID(0, 0))
            && destroyerUuid != null && !destroyerUuid.equals(new UUID(0, 0))) {
            effectiveKiller = destroyerUuid;
        }
        ServerPlayer creditedPlayer = serverLevel.getServer().getPlayerList().getPlayer(effectiveKiller);
        if (creditedPlayer != null) {
            creditedPlayer.awardStat(net.minecraft.stats.Stats.ENTITY_KILLED.get(type));
        }

        // 宠物：用绑定“体验卡过期”消息的自定义伤害击杀；普通生物：用通用伤害击杀（不播报自定义消息）
        net.minecraft.world.damagesource.DamageSource deathSource = isTamedPet
            ? transmutationDamage(serverLevel, isBlock)
            : serverLevel.damageSources().genericKill();
        if (!revived.hurt(deathSource, Float.MAX_VALUE)) {
            // 极端情况下无法被摧毁，就地移除兜底
            revived.discard();
        }
    }

    // 与“物品/方块体验卡过期”死亡消息绑定的自定义伤害来源
    private static net.minecraft.world.damagesource.DamageSource transmutationDamage(
            ServerLevel level, boolean isBlock) {
        net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> key = isBlock
            ? ModDamageTypes.TRANSMUTATION_BLOCK_EXPIRED.getKey()
            : ModDamageTypes.TRANSMUTATION_ITEM_EXPIRED.getKey();
        return level.damageSources().source(key);
    }

    private static void respawnTransmutedEntity(ServerLevel level, TransmutationData data, BlockPos pos) {
        CompoundTag entityNbt = data.entityNbt.copy();
        if (!entityNbt.contains("id")) return;

        entityNbt.remove("UUID");

        double spawnX = pos.getX() + 0.5;
        double spawnY = pos.getY() + 0.2;
        double spawnZ = pos.getZ() + 0.5;

        net.minecraft.nbt.ListTag posList = new net.minecraft.nbt.ListTag();
        posList.add(net.minecraft.nbt.DoubleTag.valueOf(spawnX));
        posList.add(net.minecraft.nbt.DoubleTag.valueOf(spawnY));
        posList.add(net.minecraft.nbt.DoubleTag.valueOf(spawnZ));
        entityNbt.put("Pos", posList);

        entityNbt.put("Motion", new net.minecraft.nbt.ListTag());
        entityNbt.getList("Motion", net.minecraft.nbt.Tag.TAG_DOUBLE).add(net.minecraft.nbt.DoubleTag.valueOf(0.0));
        entityNbt.getList("Motion", net.minecraft.nbt.Tag.TAG_DOUBLE).add(net.minecraft.nbt.DoubleTag.valueOf(0.0));
        entityNbt.getList("Motion", net.minecraft.nbt.Tag.TAG_DOUBLE).add(net.minecraft.nbt.DoubleTag.valueOf(0.0));

        java.util.Optional<Entity> optEntity = EntityType.create(entityNbt, level);
        if (optEntity.isPresent()) {
            Entity resurrected = optEntity.get();
            resurrected.setPos(spawnX, spawnY, spawnZ);
            level.addFreshEntity(resurrected);
        }
    }

    // ==================== PLAYER TRANSMUTATION HELPERS ====================

    // 玩家标识清理
    private static void cleanupPlayerTransmutation(UUID playerUuid) {
        PLAYER_ORIGINAL_GAMEMODE.remove(playerUuid);
        TRANSMUTED_ENTITIES.remove(playerUuid);
        PLAYER_TRANSMUTATION_INFO.remove(playerUuid);
        PLAYER_ORIGINAL_SCALE.remove(playerUuid);
        MORPH_REMAINING.remove(playerUuid);
        MORPH_DIMENSIONS.remove(playerUuid);
        MORPH_HEALTH_INFO.remove(playerUuid);
    }

    /** 玩家“渲染替换”变身：把最大生命值设为目标生物的最大生命值，并按比例换算当前生命（向上取整）。
     *  例如 10/20 的玩家变鸡(最大4) → 生命=ceil(10/20*4)=2。 */
    private static void applyMorphMaxHealth(ServerPlayer sp, ServerLevel level, ResourceLocation morphRl) {
        try {
            if (morphRl == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(morphRl)) return;
            net.minecraft.world.entity.Entity mobTmp = BuiltInRegistries.ENTITY_TYPE.get(morphRl).create(level);
            if (!(mobTmp instanceof net.minecraft.world.entity.LivingEntity mobLe)) return;
            float originalMax = sp.getMaxHealth();
            float mobMax = mobLe.getMaxHealth();
            if (mobMax <= 0) return;
            MORPH_HEALTH_INFO.put(sp.getUUID(), new MorphHealthInfo(originalMax, mobMax));
            net.minecraft.world.entity.ai.attributes.AttributeInstance maxHpAttr =
                sp.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (maxHpAttr != null) {
                maxHpAttr.setBaseValue(mobMax);
            }
            float newHealth = sp.getHealth() / originalMax * mobMax;
            sp.setHealth(newHealth);
        } catch (Throwable ignored) {
            // 个别生物类型临时创建失败时，跳过生命换算，不影响变形主流程
        }
    }

    /** 复原“渲染替换”变身的玩家：撤销形态与碰撞箱尺寸（玩家本体保持原样，不切模式/隐形），通知客户端停止生物渲染。 */
    private static void revertMorphRenderPlayer(ServerPlayer player) {
        boolean hadDims = MORPH_DIMENSIONS.remove(player.getUUID()) != null;
        MorphHealthInfo healthInfo = MORPH_HEALTH_INFO.remove(player.getUUID());
        // 恢复玩家最大生命值，并按比例把当前生命换算回玩家比例（向上取整）
        if (healthInfo != null) {
            net.minecraft.world.entity.ai.attributes.AttributeInstance maxHpAttr =
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (maxHpAttr != null) {
                float cur = player.getHealth();
                maxHpAttr.setBaseValue(healthInfo.originalMaxHealth());
                if (healthInfo.mobMaxHealth() > 0 && healthInfo.originalMaxHealth() > 0) {
                    float newHealth =
                        cur / healthInfo.mobMaxHealth() * healthInfo.originalMaxHealth();
                    player.setHealth(newHealth);
                }
            }
        }
        cleanupPlayerTransmutation(player.getUUID());
        if (hadDims) {
            player.refreshDimensions(); // 恢复玩家默认碰撞箱
        }
        sendTransmutationState(player, false, -1, "");
    }

    /** 玩家变形为生物时，把其碰撞箱尺寸改成对应生物的尺寸（随后用 refreshDimensions 触发本事件）。 */
    private static void onEntitySize(net.neoforged.neoforge.event.entity.EntityEvent.Size event) {
        net.minecraft.world.entity.Entity e = event.getEntity();
        net.minecraft.world.entity.EntityDimensions dims = MORPH_DIMENSIONS.get(e.getUUID());
        if (dims != null) {
            event.setNewSize(dims);
        }
    }

    /** 供 {@code PlayerDimensionsMixin} 读取：该玩家变形时对应的生物碰撞箱尺寸（无则 null）。 */
    public static net.minecraft.world.entity.EntityDimensions getMorphDimensions(java.util.UUID uuid) {
        return MORPH_DIMENSIONS.get(uuid);
    }

    /** 每服务端刻递减渲染替换变身的剩余时间，到 0 即复原。
     *  注意：不要用 entrySet().removeIf 里 setValue（其 entry 不可变会抛异常），须用迭代器。 */
    private static void tickMorphRemaining(net.minecraft.server.MinecraftServer server) {
        java.util.Iterator<Map.Entry<UUID, Integer>> it = MORPH_REMAINING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            if (sp == null) continue; // 离线：保留剩余计时，等回归后再倒计时
            // 每 tick 强制玩家碰撞箱为生物尺寸，抵抗姿势切换/其它 refreshDimensions 用默认箱覆盖
            net.minecraft.world.entity.EntityDimensions md = MORPH_DIMENSIONS.get(sp.getUUID());
            if (md != null) {
                sp.dimensions = md;
                // 低矮空间：MC 会按玩家身高把其置为“游泳/爬行(crawl)”导致减速；箱已变小可直接站立钻过，
                // 非水中却处于 SWIMMING(即爬行) 时强制回站立，以正常走路速度通过一格高空间。
                if (sp.getPose() == net.minecraft.world.entity.Pose.SWIMMING && !sp.isInWater()) {
                    sp.setPose(net.minecraft.world.entity.Pose.STANDING);
                }
            }
            int remain = e.getValue() - 1;
            if (remain > 0) {
                e.setValue(remain);
            } else {
                it.remove();
                revertMorphRenderPlayer(sp);
            }
        }
    }

    // 玩家变形时设置 SCALE（物品 0.13 / 方块 0.5 / 生物按碰撞箱高度比例 n/1.8）。
    // 仅在首次记录原始 SCALE，避免多次变形互相覆盖原始值。
    private static void applyTransmutationScale(ServerPlayer player, double scale) {
        if (scale <= 0) return;
        AttributeInstance attr = player.getAttribute(Attributes.SCALE);
        if (attr == null) return;
        PLAYER_ORIGINAL_SCALE.putIfAbsent(player.getUUID(), attr.getBaseValue());
        attr.setBaseValue(scale);
    }

    // 复原玩家时恢复其原始 SCALE（无记录则不处理）
    private static void restoreTransmutationScale(ServerPlayer player) {
        Double original = PLAYER_ORIGINAL_SCALE.remove(player.getUUID());
        if (original != null) {
            AttributeInstance attr = player.getAttribute(Attributes.SCALE);
            if (attr != null) {
                attr.setBaseValue(original);
            }
        }
    }

    // 复原玩家（变身时间结束）：恢复原始游玩模式、取消隐身并传送回所在位置
    private static void revertTransmutedPlayer(ServerLevel level, TransmutationData data, BlockPos pos) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(data.playerUuid());
        if (player != null) {
            GameType original = PLAYER_ORIGINAL_GAMEMODE.getOrDefault(player.getUUID(), GameType.SURVIVAL);
            player.setGameMode(original);
            player.removeEffect(MobEffects.INVISIBILITY);
            player.setInvisible(false);
            player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5,
                player.getYRot(), player.getXRot());
            player.fallDistance = 0.0F;
            restoreTransmutationScale(player);
            restoreTransmutationCamera(player);
            sendTransmutationState(player, false, -1);
        }
        // 玩家离线时也清理状态，避免地图残留
        cleanupPlayerTransmutation(data.playerUuid());
    }

    // 被变成的物品/方块被摧毁时：强行击杀该玩家并播报死亡消息
    private static void killTransmutedPlayer(ServerLevel level, TransmutationData data, BlockPos pos, boolean isBlock) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(data.playerUuid());
        if (player != null) {
            GameType original = PLAYER_ORIGINAL_GAMEMODE.getOrDefault(player.getUUID(), GameType.SURVIVAL);
            player.setGameMode(original);
            player.removeEffect(MobEffects.INVISIBILITY);
            player.setInvisible(false);
            // 被变成的实体死亡：恢复第一人称与原始缩放（避免死亡/重生后残留缩放）
            restoreTransmutationScale(player);
            restoreTransmutationCamera(player);
            // 用绑定“体验卡过期”消息的自定义伤害击杀，玩家死亡消息自然展示为自定义文案
            player.hurt(transmutationDamage(level, isBlock), Float.MAX_VALUE);
            sendTransmutationState(player, false, -1);
        }
        cleanupPlayerTransmutation(data.playerUuid());
    }

    // ==================== ANTIDOTE & RANDOM-TARGET HELPERS ====================

    private static boolean isTransmutationPotion(ItemStack stack) {
        if (stack.isEmpty()) return false;
        PotionContents c = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return isTransmutationPotionContents(c);
    }

    private static boolean isTransmutationPotionHolder(Holder<Potion> h) {
        return h != null && (h.is(ModPotions.TRANSMUTATION) || h.is(ModPotions.LONG_TRANSMUTATION));
    }

    private static boolean isTransmutationPotionContents(PotionContents c) {
        return c.potion().map(ModMain::isTransmutationPotionHolder).orElse(false);
    }

    private static boolean isAntidotePotion(ItemStack stack) {
        if (stack.isEmpty()) return false;
        PotionContents c = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return c.potion()
            .map(h -> h.is(ModPotions.TRANSMUTATION_ANTIDOTE))
            .orElse(false);
    }

    // 是否随机传送药水：传送药水的变体，带 TRANSPORT_MODE="random" 标记（酿造/创造标签的“随机传送药水”）
    private static boolean isRandomTransportPotion(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return "random".equals(stack.getOrDefault(ModDataComponents.TRANSPORT_MODE.get(), ""));
    }

    // 是否定点传送药水：带 TRANSPORT_MODE="point" 且含目标坐标或目标实体 UUID
    private static boolean isPointTransportPotion(ItemStack stack) {
        if (stack.isEmpty()) return false;
        boolean mode = "point".equals(stack.getOrDefault(ModDataComponents.TRANSPORT_MODE.get(), ""));
        return mode && (stack.has(ModDataComponents.TARGET_POS.get())
            || stack.has(ModDataComponents.TARGET_ENTITY_UUID.get()));
    }

    // 是否定向传送药水：带 TRANSPORT_MODE="directional" 且含前进格数
    private static boolean isDirectionalTransportPotion(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return "directional".equals(stack.getOrDefault(ModDataComponents.TRANSPORT_MODE.get(), ""))
            && stack.has(ModDataComponents.TARGET_DIST.get());
    }

    // 传送药水命中处理：在命中处创建入口传送门，并按药水类型（随机/定点）确定出口传送门。
    // 单向（入口具备传送能力）；找不到合适出口位置则任何门都不创建。
    private static boolean tryCreateRandomPortalPair(ServerLevel sl, LivingEntity shooter, ItemStack potionStack, HitResult ray) {
        Vec3 entranceCenter;
        float entranceYaw;
        float entrancePitch;
        if (ray instanceof net.minecraft.world.phys.BlockHitResult bhr
            && bhr.getType() != net.minecraft.world.phys.BlockHitResult.Type.MISS) {
            net.minecraft.core.Direction dir = bhr.getDirection();
            Vec3 n = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            Vec3 hit = bhr.getLocation();
            if (dir.getAxis() == net.minecraft.core.Direction.Axis.Y) {
                // 命中方块上/下表面：1×1 贴图、紧贴该面、方向改为上下朝向；
                // 上表面向上偏移 0.01 格，下表面向下偏移 0.01 格（用面法线 n，UP 为 +Y、DOWN 为 -Y）
                entranceCenter = hit.add(n.scale(0.01));
                entranceYaw = 0.0F;
                entrancePitch = (dir == net.minecraft.core.Direction.UP) ? -90.0F : 90.0F;
            } else {
                // 命中侧向一面：贴图紧贴该方块表面，留 0.01 格偏移防 z-fighting（法线决定门朝向）
                entranceCenter = hit.add(n.scale(0.01));
                entranceYaw = (float) Math.toDegrees(Math.atan2(-n.x, n.z));
                entrancePitch = 0.0F;
            }
        } else if (ray instanceof net.minecraft.world.phys.EntityHitResult ehr) {
            // 命中生物/实体：在其正下方（碰撞箱最低 y 向下取整的高度）开水平朝上的门，并向上偏移 0.01
            Entity vic = ehr.getEntity();
            double footY = Mth.floor(vic.getBoundingBox().minY) + 0.01;
            entranceCenter = new Vec3(vic.getX(), footY, vic.getZ());
            entranceYaw = 0.0F;
            entrancePitch = -90.0F;
        } else {
            return false; // 未命中（MISS）
        }

        // 确定出口传送门位置：定点(坐标/实体5格)、定向(投掷方向水平前进X格)、随机(100格)各自处理
        Vec3 exitPos;
        if (isPointTransportPotion(potionStack)) {
            double w = shooter.getBbWidth(), h = shooter.getBbHeight();
            Vec3 center;
            boolean hasPos = potionStack.has(ModDataComponents.TARGET_POS.get());
            if (hasPos) {
                center = potionStack.getOrDefault(ModDataComponents.TARGET_POS.get(), Vec3.ZERO);
            } else {
                java.util.UUID uuid = potionStack.getOrDefault(ModDataComponents.TARGET_ENTITY_UUID.get(), null);
                Entity targetEnt = findEntityByUuid(sl, uuid);
                if (targetEnt == null) return false;
                center = targetEnt.position();
            }
            Vec3 found = findPortalExitNear(sl, center, hasPos ? 48 : 5, w, h);
            if (found == null) return false;
            exitPos = found;
        } else if (isDirectionalTransportPotion(potionStack)) {
            double dist = potionStack.getOrDefault(ModDataComponents.TARGET_DIST.get(), 0.0);
            Vec3 dir = facingHorizontal(shooter.getYRot());
            Vec3 targetPoint = entranceCenter.add(dir.scale(dist));
            if (Math.abs(targetPoint.x) >= 29999984.0 || Math.abs(targetPoint.z) >= 29999984.0) return false; // 超出世界边境
            Vec3 found = findPortalExitNear(sl, targetPoint, 1, shooter.getBbWidth(), shooter.getBbHeight());
            if (found == null) return false;
            exitPos = found;
        } else {
            java.util.Optional<Vec3> o = findRandomPortalExitPos(sl, entranceCenter);
            if (o.isEmpty()) return false;
            exitPos = o.get();
        }

        float exitYaw = TRANSMUTATION_RANDOM.nextFloat() * 360.0F;
        // 出口传送门贴方块上表面（水平、朝上）；统一向上偏移 0.01 格防 z-fighting
        PotionPortalEntity entrance = PotionPortalEntity.create(sl, entranceCenter, entranceYaw, entrancePitch, true);
        PotionPortalEntity exit = PotionPortalEntity.create(sl, exitPos.add(new Vec3(0.0, 0.01, 0.0)), exitYaw, -90.0F, false);
        sl.addFreshEntity(exit);
        entrance.partnerId = exit.getId();
        sl.addFreshEntity(entrance);
        exit.partnerId = entrance.getId();
        return true;
    }

    // 在入口门位置半径 100 格内，随机选取一个“有碰撞箱方块的上表面”作为出口传送点。
    // 该方块必须有碰撞箱（水源/蜘蛛网等无碰撞箱不计入；讲台/台阶/半砖等不完整但有碰撞箱计入），
    // 且其上方两格（门占用 1×2）无碰撞。
    private static java.util.Optional<Vec3> findRandomPortalExitPos(ServerLevel sl, Vec3 center) {
        for (int attempt = 0; attempt < 200; attempt++) {
            double r = TRANSMUTATION_RANDOM.nextDouble() * 100.0;
            double ang = TRANSMUTATION_RANDOM.nextDouble() * Math.PI * 2.0;
            int bx = Mth.floor(center.x + Math.cos(ang) * r);
            int bz = Mth.floor(center.z + Math.sin(ang) * r);
            int by = Mth.floor(center.y + (TRANSMUTATION_RANDOM.nextDouble() * 2.0 - 1.0) * 40.0);
            by = Mth.clamp(by, sl.getMinBuildHeight(), sl.getMaxBuildHeight() - 2);
            BlockPos pos = new BlockPos(bx, by, bz);
            BlockState bs = sl.getBlockState(pos);
            if (bs.getCollisionShape(sl, pos).isEmpty()) continue; // 必须有碰撞箱
            if (bs.is(ENDERMAN_NO_TP)) continue; // 标记为不可传送到的方块不计入
            BlockPos above1 = pos.above();
            BlockPos above2 = pos.above(2);
            if (!sl.getBlockState(above1).getCollisionShape(sl, above1).isEmpty()) continue;
            if (!sl.getBlockState(above2).getCollisionShape(sl, above2).isEmpty()) continue;
            return java.util.Optional.of(new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5));
        }
        return java.util.Optional.empty();
    }

    // 末影人不可传送到的方块标签：随机/定点出口搜索均排除这些方块
    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> ENDERMAN_NO_TP =
        net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "enderman_does_not_teleport_to"));

    // 按 UUID 在所有已加载维度中查找实体；找不到返回 null
    private static Entity findEntityByUuid(ServerLevel sl, java.util.UUID uuid) {
        if (uuid == null) return null;
        for (ServerLevel l : sl.getServer().getAllLevels()) {
            Entity e = l.getEntity(uuid);
            if (e != null) return e;
        }
        return null;
    }

    // 由朝向角（yaw，单位：度）计算水平方向向量（用于定向传送药水投掷方向）
    private static Vec3 facingHorizontal(float yaw) {
        float rad = (float) Math.toRadians(yaw);
        return new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
    }

    // 定点/定向传送药水：在目标附近找“离目标最近、可容纳指定尺寸生物的空间”，
    // 返回该空间底部（地面方块上表面）坐标；找不到返回 null。
    private static Vec3 findPortalExitNear(ServerLevel sl, Vec3 target, int maxRadius, double w, double h) {
        int cx = Mth.floor(target.x);
        int cz = Mth.floor(target.z);
        int cy = Mth.clamp(Mth.floor(target.y), sl.getMinBuildHeight(), sl.getMaxBuildHeight() - 2);
        for (int radius = 0; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int bx = cx + dx, bz = cz + dz;
                    for (int footY = cy; footY > sl.getMinBuildHeight(); footY--) {
                        if (canPortalFootAt(sl, bx, footY, bz, w, h)) {
                            return new Vec3(bx + 0.5, footY - 1.0, bz + 0.5); // 空间底部 = 地面 topY
                        }
                    }
                    for (int footY = cy + 1; footY < sl.getMaxBuildHeight() - 2; footY++) {
                        if (canPortalFootAt(sl, bx, footY, bz, w, h)) {
                            return new Vec3(bx + 0.5, footY - 1.0, bz + 0.5);
                        }
                    }
                }
            }
        }
        return null;
    }

    // 该位置（生物脚底在 footY）能否容纳生物：底部有可站支撑，且生物 AABB 覆盖的方块均可通过/不窒息
    private static boolean canPortalFootAt(ServerLevel sl, int bx, int footY, int bz, double w, double h) {
        BlockPos below = new BlockPos(bx, footY - 1, bz);
        BlockState belowState = sl.getBlockState(below);
        if (belowState.isAir()) return false;
        if (belowState.getFluidState().is(net.minecraft.world.level.material.Fluids.LAVA)) return false;
        if (belowState.is(ENDERMAN_NO_TP)) return false;
        if (belowState.getCollisionShape(sl, below).isEmpty()) return false; // 无支撑

        int minX = Mth.floor(bx + 0.5 - w / 2.0);
        int maxX = Mth.floor(bx + 0.5 + w / 2.0);
        int minZ = Mth.floor(bz + 0.5 - w / 2.0);
        int maxZ = Mth.floor(bz + 0.5 + w / 2.0);
        int minY = footY;
        int maxY = Mth.floor(footY + h);
        for (int yy = minY; yy <= maxY; yy++) {
            for (int xx = minX; xx <= maxX; xx++) {
                for (int zz = minZ; zz <= maxZ; zz++) {
                    if (!canPortalFootBlock(sl, xx, yy, zz)) return false;
                }
            }
        }
        return true;
    }

    // 某格是否可被生物“通过/容纳”（不窒息）：空气、水、无碰撞方块、玻璃等均可；岩浆与 enderman 标签排除
    private static boolean canPortalFootBlock(ServerLevel sl, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState s = sl.getBlockState(pos);
        if (s.isAir()) return true;
        net.minecraft.world.level.material.FluidState f = s.getFluidState();
        if (f.is(net.minecraft.world.level.material.Fluids.LAVA)) return false;
        if (f.is(net.minecraft.world.level.material.Fluids.WATER)) return true;
        if (s.is(ENDERMAN_NO_TP)) return false;
        if (s.getCollisionShape(sl, pos).isEmpty()) return true; // 草丛、雪层等可穿过
        if (s.is(net.minecraft.world.level.block.Blocks.GLASS)
            || s.is(net.minecraft.world.level.block.Blocks.GLASS_PANE)
            || s.is(net.minecraft.world.level.block.Blocks.TINTED_GLASS)) return true; // 玻璃不会使生物窒息
        return false;
    }

    private static final java.util.Random TRANSMUTATION_RANDOM = new java.util.Random();

    // 全虚空 LevelReader：所有位置均为 void_air、无流体、无方块实体、无光照。
    // 仅用于依附型方块判定（BlockState#canSurvive 需要 LevelReader 形参）：
    // 任何需要底面/侧面支撑或特定材质（泥土/沙子/耕地/水）的方块在此环境下都通不过。
    private static final net.minecraft.world.level.LevelReader TRANSMUTATION_VOID_READER =
        new net.minecraft.world.level.LevelReader() {
            @Override
            public BlockState getBlockState(BlockPos pos) {
                return net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState();
            }

            @Override
            public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) {
                return net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
            }

            @Override
            public net.minecraft.world.level.chunk.ChunkAccess getChunk(int x, int z,
                    net.minecraft.world.level.chunk.status.ChunkStatus status, boolean load) {
                return null;
            }

            @Override
            public boolean hasChunk(int x, int z) {
                return false;
            }

            @Override
            public int getSkyDarken() {
                return 0;
            }

            @Override
            public net.minecraft.world.level.biome.BiomeManager getBiomeManager() {
                return null;
            }

            @Override
            public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getNoiseBiome(int x, int y, int z) {
                return null;
            }

            @Override
            public net.minecraft.world.level.border.WorldBorder getWorldBorder() {
                return null;
            }

            @Override
            public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() {
                return null;
            }

            @Override
            public int getHeight() {
                return 0;
            }

            @Override
            public int getMinBuildHeight() {
                return 0;
            }

            @Override
            public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() {
                return net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS;
            }

            @Override
            public net.minecraft.core.RegistryAccess registryAccess() {
                return null;
            }

            @Override
            public net.minecraft.world.level.dimension.DimensionType dimensionType() {
                return null;
            }

            @Override
            public int getSeaLevel() {
                return 63;
            }

            @Override
            public boolean isClientSide() {
                return false;
            }

            @Override
            public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getUncachedNoiseBiome(int x, int y, int z) {
                return null;
            }

            @Override
            public int getHeight(net.minecraft.world.level.levelgen.Heightmap.Types type, int x, int z) {
                return 0;
            }

            @Override
            public float getShade(net.minecraft.core.Direction direction, boolean shadeAmbient) {
                return shadeAmbient ? switch (direction) {
                    case DOWN -> 0.5F;
                    case UP -> 1.0F;
                    case NORTH, SOUTH -> 0.8F;
                    case WEST, EAST -> 0.6F;
                } : 1.0F;
            }

            @Override
            public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public java.util.List<net.minecraft.world.phys.shapes.VoxelShape> getEntityCollisions(
                    net.minecraft.world.entity.Entity entity, net.minecraft.world.phys.AABB area) {
                return java.util.List.of();
            }
        };

    // 变形黑名单：以下方块不可作为变形目标。
    // 1) 所有带方块实体（EntityBlock）的方块：下落方块实体只携带方块状态、不携带方块实体数据，
    //    变形后会渲染成空白方块，且生物变成这类方块落地时会直接死亡。这一条规则可一口气排除
    //    所有能储存物品的容器（箱子、木桶、熔炉、发射器、投掷器、漏斗等）及其它所有方块实体方块。
    // 2) 所有依附型方块（canSurvive 依赖环境支撑）：珊瑚扇、火把、梯子、活板门、按钮、拉杆、
    //    藤蔓、花草/树苗/作物、雪片、地毯、栏杆、压力板、门、耕地、仙人掌、甘蔗、滴水石锥、
    //    脚手架、孢子花等。用“全虚空”环境测试默认状态能否存活即可一次性识别——任何需要
    //    底面/侧面支撑或特定材质（泥土/沙子/耕地/水）的方块都通不过。这类方块变成下落方块
    //    落地放置后会被邻居更新立即弹出成掉落物或转成空气，导致落地校验失败、走“破坏结算”
    //    误杀原生物（或留下一块凭空消失的隐形追踪数据）。
    private static boolean isTransmutationBlockBlacklisted(Block block) {
        if (block instanceof net.minecraft.world.level.block.EntityBlock) {
            return true;
        }
        // 全虚空测试：只有“任何环境都可存活”的方块才能通过
        return !block.defaultBlockState()
            .canSurvive(TRANSMUTATION_VOID_READER, BlockPos.ZERO);
    }

    // 随机变形目标：从已注册方块+物品中随机选取。
    // 排除：流体源方块、黑名单方块实体方块、空气类方块（air/cave_air/void_air），以及
    // 无可见模型的隐形/技术方块（light/barrier/structure_void/bubble_column 等，渲染形状非 MODEL）。
    // 原因：这些方块变成下落方块后不可见；空气类更会在原版 FallingBlockEntity.tick 开头被
    // isAir 检查立刻 discard，触发 onEntityLeaveLevel 的“未能正常落地 → 按破坏处理结算”，
    // 导致原生物被误杀。所有物品都会保留。
    private static String pickRandomTransmutationType() {
        java.util.List<String> pool = new java.util.ArrayList<>();
        java.util.Set<String> invisibleBlocks = java.util.Set.of(
            "minecraft:barrier", "minecraft:light", "minecraft:structure_void");
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState defaultState = block.defaultBlockState();
            if (defaultState.isAir()
                || defaultState.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL
                || defaultState.getFluidState().isSource()
                || isTransmutationBlockBlacklisted(block)
                || invisibleBlocks.contains(BuiltInRegistries.BLOCK.getKey(block).toString())) {
                continue;
            }
            pool.add(BuiltInRegistries.BLOCK.getKey(block).toString());
        }
        for (Item item : BuiltInRegistries.ITEM) {
            // 物品表里包含方块物品（如箱子、木桶等），对应黑名单方块的条目同样排除，
            // 否则随机结果经 performTransmutation 判定为方块后会被再次重掷，形成隐患。
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (BuiltInRegistries.BLOCK.containsKey(itemId)
                && isTransmutationBlockBlacklisted(BuiltInRegistries.BLOCK.get(itemId))) {
                continue;
            }
            pool.add(itemId.toString());
        }
        if (pool.isEmpty()) return "minecraft:stone";
        return pool.get(TRANSMUTATION_RANDOM.nextInt(pool.size()));
    }

    // 变形解药作用于单个实体：直接饮用解药时，立刻解除该实体自身的变形。
    // 按“该实体是否为原生物(playerUuid)”匹配四种变形形态（方块/物品/下落方块/壳）整体复原，
    // 与 applyAntidoteSplash 的逻辑一致，但作用对象精确到饮用者自身而非按半径范围。
    private static void applyAntidoteToEntity(ServerLevel level, LivingEntity target) {
        UUID targetUuid = target.getUUID();
        ResourceLocation dimId = level.dimension().location();

        // 1) 变身为方块
        Map<BlockPos, java.util.List<TransmutationData>> dimMap = BLOCK_TRANSMUTATIONS.get(dimId);
        if (dimMap != null) {
            java.util.Iterator<Map.Entry<BlockPos, java.util.List<TransmutationData>>> it =
                dimMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockPos, java.util.List<TransmutationData>> e = it.next();
                BlockPos pos = e.getKey();
                java.util.List<TransmutationData> list = e.getValue();
                java.util.List<TransmutationData> matched = new java.util.ArrayList<>();
                for (TransmutationData data : list) {
                    if (targetUuid.equals(data.playerUuid())) matched.add(data);
                }
                if (matched.isEmpty()) continue;
                // 若该格数据全部属于饮用者，移除该格并复原
                if (list.size() == matched.size()) it.remove();
                else list.removeAll(matched);
                level.destroyBlock(pos, false);
                for (TransmutationData data : matched) revertTransmutationData(level, data, pos);
            }
        }

        // 2) 变身为物品
        java.util.Iterator<Map.Entry<UUID, TransmutationData>> itItem =
            ITEM_TRANSMUTATIONS.entrySet().iterator();
        while (itItem.hasNext()) {
            Map.Entry<UUID, TransmutationData> e = itItem.next();
            if (!targetUuid.equals(e.getValue().playerUuid())) continue;
            UUID uuid = e.getKey();
            Entity ent = level.getEntity(uuid);
            BlockPos itemPos = ITEM_TRANSMUTATION_POSITIONS.getOrDefault(uuid,
                ent != null ? ent.blockPosition() : null);
            itItem.remove();
            ITEM_TRANSMUTATION_POSITIONS.remove(uuid);
            if (ent != null && ent.isAlive()) ent.discard();
            if (itemPos != null) revertTransmutationData(level, e.getValue(), itemPos);
        }

        // 3) 仍在掉落过程中、尚未落地的变身方块
        java.util.Iterator<Map.Entry<UUID, TransmutationData>> itFall =
            FALLING_TRANSMUTATIONS.entrySet().iterator();
        while (itFall.hasNext()) {
            Map.Entry<UUID, TransmutationData> e = itFall.next();
            if (!targetUuid.equals(e.getValue().playerUuid())) continue;
            UUID uuid = e.getKey();
            Entity ent = level.getEntity(uuid);
            itFall.remove();
            BlockPos fallPos = ent != null ? ent.blockPosition() : null;
            if (ent != null && ent.isAlive()) ent.discard();
            if (fallPos != null) revertTransmutationData(level, e.getValue(), fallPos);
        }

        // 4) 玩家空壳 / 生物壳：原生物(playerUuid)命中时整体复原为原生物
        java.util.Iterator<Map.Entry<UUID, LivingShellData>> itShell =
            LIVING_SHELLS.entrySet().iterator();
        while (itShell.hasNext()) {
            Map.Entry<UUID, LivingShellData> e = itShell.next();
            if (!targetUuid.equals(e.getValue().playerUuid())
                && !e.getKey().equals(targetUuid)) continue;
            Entity shellEnt = level.getEntity(e.getKey());
            itShell.remove();
            BlockPos pos = shellEnt != null ? shellEnt.blockPosition() : target.blockPosition();
            if (shellEnt != null && shellEnt.isAlive()) shellEnt.discard();
            revertLivingShell(level, e.getValue(), pos);
        }

        // 最后清理玩家残留的变形状态（游玩模式、缩放、相机、形态免疫）；
        // “渲染替换”形态额外通知客户端停止生物渲染。
        if (TRANSMUTED_ENTITIES.contains(targetUuid)) {
            if (target instanceof ServerPlayer antidoteSp && MORPH_REMAINING.containsKey(targetUuid)) {
                revertMorphRenderPlayer(antidoteSp);
            } else {
                cleanupPlayerTransmutation(targetUuid);
            }
        }
    }

    // 变形解药喷洒：让范围内的变身方块/物品（由生物变来的）提前复原为生物形态。
    private static void applyAntidoteSplash(ServerLevel level, double x, double y, double z, double radius) {
        double r2 = radius * radius;
        BlockPos center = BlockPos.containing(x, y, z);
        ResourceLocation dimId = level.dimension().location();
        Map<BlockPos, java.util.List<TransmutationData>> dimMap = BLOCK_TRANSMUTATIONS.get(dimId);
        if (dimMap != null) {
            java.util.Iterator<Map.Entry<BlockPos, java.util.List<TransmutationData>>> it =
                dimMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockPos, java.util.List<TransmutationData>> e = it.next();
                BlockPos pos = e.getKey();
                if (pos.distSqr(center) > r2) continue;
                java.util.List<TransmutationData> list = e.getValue();
                it.remove();
                level.destroyBlock(pos, false);
                for (TransmutationData data : new java.util.ArrayList<>(list)) {
                    revertTransmutationData(level, data, pos);
                }
            }
        }

        // 2) 变身为物品的生物
        java.util.Iterator<Map.Entry<UUID, TransmutationData>> itItem =
            ITEM_TRANSMUTATIONS.entrySet().iterator();
        while (itItem.hasNext()) {
            Map.Entry<UUID, TransmutationData> e = itItem.next();
            UUID uuid = e.getKey();
            Entity ent = level.getEntity(uuid);
            BlockPos itemPos = ITEM_TRANSMUTATION_POSITIONS.getOrDefault(uuid,
                ent != null ? ent.blockPosition() : null);
            if (itemPos == null || itemPos.distSqr(center) > r2) continue;
            itItem.remove();
            ITEM_TRANSMUTATION_POSITIONS.remove(uuid);
            if (ent != null && ent.isAlive()) ent.discard();
            revertTransmutationData(level, e.getValue(), itemPos);
        }

        // 3) 仍在掉落过程中、尚未落地的变身方块
        java.util.Iterator<Map.Entry<UUID, TransmutationData>> itFall =
            FALLING_TRANSMUTATIONS.entrySet().iterator();
        while (itFall.hasNext()) {
            Map.Entry<UUID, TransmutationData> e = itFall.next();
            UUID uuid = e.getKey();
            Entity ent = level.getEntity(uuid);
            if (ent == null) continue;
            BlockPos fallPos = ent.blockPosition();
            if (fallPos.distSqr(center) > r2) continue;
            itFall.remove();
            ent.discard();
            revertTransmutationData(level, e.getValue(), fallPos);
        }

        // 4) 玩家空壳 / 生物壳：提前复原为原生物
        LOGGER.info("[DBG] applyAntidoteSplash: shell pass start mapSize={} dim={}",
            LIVING_SHELLS.size(), dimId);
        java.util.Iterator<Map.Entry<UUID, LivingShellData>> itShell =
            LIVING_SHELLS.entrySet().iterator();
        while (itShell.hasNext()) {
            Map.Entry<UUID, LivingShellData> e = itShell.next();
            Entity ent = level.getEntity(e.getKey());
            if (ent == null) {
                LOGGER.info("[DBG] applyAntidoteSplash: shell entity null uuid={}", e.getKey());
                continue;
            }
            BlockPos shellPos = ent.blockPosition();
            if (shellPos.distSqr(center) > r2) {
                LOGGER.info("[DBG] applyAntidoteSplash: shell out of range uuid={} distSqr={}",
                    e.getKey(), shellPos.distSqr(center));
                continue;
            }
            LOGGER.info("[DBG] applyAntidoteSplash: shell HIT uuid={} at={} playerUuid={}",
                e.getKey(), shellPos, e.getValue().playerUuid());
            itShell.remove();
            ent.discard();
            revertLivingShell(level, e.getValue(), shellPos);
        }

        // 5) “渲染替换”变形玩家：解药溅射范围内复原其生物形态
        if (!MORPH_REMAINING.isEmpty()) {
            for (Player p : level.players()) {
                if (MORPH_REMAINING.containsKey(p.getUUID())
                        && p.distanceToSqr(x, y, z) <= r2) {
                    if (p instanceof ServerPlayer sp) {
                        revertMorphRenderPlayer(sp);
                    }
                }
            }
        }
    }

    // 统一复原：玩家恢复游玩模式并传送回来，普通生物重新生成回原形态。
    private static void revertTransmutationData(ServerLevel level, TransmutationData data, BlockPos pos) {
        if (data.playerUuid() != null) {
            revertTransmutedPlayer(level, data, pos);
        } else {
            respawnTransmutedEntity(level, data, pos);
        }
    }

    // ==================== TRANSMUTATION TICK HANDLING ====================

    // 每 tick 最多处理的“终结操作”数量（物品被摧毁/被漏斗吸走/倒计时到期的重建击杀）。
    // 大量变形物品被漏斗矿车/漏斗批量吸走并“击杀”时，若一次性全部重建+击杀原生物，
    // 会在单 tick 内产生大量实体创建与死亡结算，造成严重卡顿，因此分批平滑处理。
    private static final int MAX_TRANSMUTATION_TERMINAL_PER_TICK = 16;

    private static void tickTransmutationItems(ServerLevel level) {
        int terminalProcessed = 0;
        java.util.Iterator<Map.Entry<UUID, TransmutationData>> it = ITEM_TRANSMUTATIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TransmutationData> entry = it.next();
            UUID uuid = entry.getKey();
            TransmutationData data = entry.getValue();
            Entity entity = level.getEntity(uuid);

            if (entity == null) {
                // 区块卸载期间实体不在关卡内：等重新加载后继续倒计时。
                // 若所在区块仍加载却找不到实体，说明物品已被摧毁（烧掉/消失），按破坏结算。
                BlockPos lastPos = ITEM_TRANSMUTATION_POSITIONS.get(uuid);
                if (lastPos == null) {
                    it.remove();
                    // 玩家变形物品丢失且无位置记录：清理其残留变形状态（防 TRANSMUTED_ENTITIES 卡死玩家）
                    if (data.playerUuid() != null) {
                        cleanupPlayerTransmutation(data.playerUuid());
                    }
                    continue;
                }
                if (level.isLoaded(lastPos)) {
                    // 批量终结：本 tick 已达处理上限则留到下一 tick，平滑掉卡顿尖峰
                    if (terminalProcessed >= MAX_TRANSMUTATION_TERMINAL_PER_TICK) break;
                    terminalProcessed++;
                    it.remove();
                    ITEM_TRANSMUTATION_POSITIONS.remove(uuid);
                    // 若物品是被漏斗吸走而消失：删除漏斗里吸收的该物品（堆叠则减一）
                    removeTransmutedItemFromHoppers(level, lastPos, data.itemType());
                    if (data.playerUuid() != null) {
                        killTransmutedPlayer(level, data, lastPos, false);
                    } else {
                        handleTransmutationKillCredit(level, data, lastPos, null);
                    }
                }
                continue;
            }
            if (!entity.isAlive()) {
                // 物品实体被摧毁（着火/岩浆/仙人掌等）：按“体验卡过期”结算击杀，
                // 确保被命名的宠物/玩家也能正确播报死亡消息与原版死亡流程，而不是静默消失。
                // 批量终结：本 tick 已达处理上限则留到下一 tick，平滑掉卡顿尖峰
                if (terminalProcessed >= MAX_TRANSMUTATION_TERMINAL_PER_TICK) break;
                terminalProcessed++;
                it.remove();
                BlockPos diePos = entity.blockPosition();
                ITEM_TRANSMUTATION_POSITIONS.remove(uuid);
                // 若物品是被漏斗吸走而消失：删除漏斗里吸收的该物品（堆叠则减一）
                removeTransmutedItemFromHoppers(level, diePos, data.itemType());
                if (data.playerUuid() != null) {
                    killTransmutedPlayer(level, data, diePos, false);
                } else {
                    handleTransmutationKillCredit(level, data, diePos, data.killerPlayerUuid());
                }
                continue;
            }
            ITEM_TRANSMUTATION_POSITIONS.put(uuid, entity.blockPosition());

            // 玩家变身：被变成的物品跟随玩家（玩家以冒险模式自由移动），离线则暂停倒计时等待其回归
            if (data.playerUuid() != null) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(data.playerUuid());
                if (player == null) {
                    continue;
                }
                makeTransmutedFollowPlayer(entity, player);
                TransmutationData nd = new TransmutationData(data.entityNbt, data.remainingTicks - 1,
                    data.killerPlayerUuid(), data.itemType, data.playerUuid());
                ITEM_TRANSMUTATIONS.put(uuid, nd);
                if (nd.remainingTicks() <= 0) {
                    BlockPos blockPos = entity.blockPosition();
                    it.remove();
                    ITEM_TRANSMUTATION_POSITIONS.remove(uuid);
                    entity.discard();
                    revertTransmutedPlayer(level, data, blockPos);
                }
                continue;
            }

            TransmutationData newData = new TransmutationData(data.entityNbt, data.remainingTicks - 1,
                data.killerPlayerUuid(), data.itemType, data.playerUuid());
            ITEM_TRANSMUTATIONS.put(uuid, newData);

            if (newData.remainingTicks <= 0) {
                BlockPos blockPos = entity.blockPosition();
                it.remove();
                ITEM_TRANSMUTATION_POSITIONS.remove(uuid);
                entity.discard();
                respawnTransmutedEntity(level, data, blockPos);
            }
        }
    }

    // 玩家/生物变身为物品时，若物品是被漏斗吸走（物品实体消失）而导致“体验卡过期”死亡，
    // 则把漏斗里吸收的该物品删去一份（堆叠则数量减一），避免物品凭空多出来。
    // 既处理方块漏斗，也处理漏斗矿车（实体），否则被矿车吸走的部分会残留在矿车里。
    private static void removeTransmutedItemFromHoppers(Level level, BlockPos pos, String itemType) {
        ResourceLocation rl = ResourceLocation.tryParse(itemType);
        if (rl == null) return;
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == null || item == Items.AIR) return;
        // 1) 方块漏斗：搜索附近 5×6×5 区域的方块漏斗
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -3; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos bp = pos.offset(dx, dy, dz);
                    if (!level.isLoaded(bp)) continue;
                    if (level.getBlockEntity(bp) instanceof HopperBlockEntity hopper) {
                        if (shrinkTransmutedItemFromContainer(hopper, item)) {
                            hopper.setChanged();
                            return;
                        }
                    }
                }
            }
        }
        // 2) 漏斗矿车：搜索附近 4 格范围内的漏斗矿车（实体），同样删去一份吸收的变形物品
        if (level instanceof ServerLevel serverLevel) {
            for (MinecartHopper cart : serverLevel.getEntitiesOfClass(MinecartHopper.class,
                new AABB(pos).inflate(4.0))) {
                if (shrinkTransmutedItemFromContainer(cart, item)) {
                    return;
                }
            }
        }
    }

    // 在容器中删除一份匹配的变形物品（堆叠则减一）；找到并处理则返回 true
    private static boolean shrinkTransmutedItemFromContainer(Container container, Item item) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    /**
     * 变身模式下：让被变成的实体（物品/下落方块/生物壳/玩家空壳）跟随玩家。
     * 玩家以冒险模式自由移动并受方块碰撞约束，实体每 tick 被贴到玩家脚下；
     * 同时把玩家摔落距离清零（玩家不会因任何下落而受摔落伤害）。
     */
    private static void makeTransmutedFollowPlayer(Entity entity, ServerPlayer player) {
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        // 下落方块：底部贴地面（实体 Y = 玩家脚底 = 地面），无高度落差
        // 生物/壳：脚底对齐玩家脚底；物品：以其中心对齐玩家脚底附近
        double y;
        if (entity instanceof TransmutationFallingBlockEntity || entity instanceof LivingEntity) {
            y = player.getY();
        } else {
            y = player.getY() + entity.getBbHeight() * 0.5;
        }
        // 生物/玩家空壳：看向玩家看向的方向（yRot/xRot 跟随玩家视角）
        float yRot = entity.getYRot();
        float xRot = entity.getXRot();
        if (entity instanceof LivingEntity) {
            yRot = player.getYRot();
            xRot = player.getXRot();
        }
        entity.moveTo(player.getX(), y, player.getZ(), yRot, xRot);
        // 生物壳/玩家空壳额外同步头部朝向，避免头部与身体转向不一致
        if (entity instanceof Mob mob) {
            mob.yHeadRot = yRot;
            mob.yHeadRotO = yRot;
        }
        player.fallDistance = 0.0F;
    }

    /** 从 {@code origin} 向四周做有限 BFS，寻找最近的仍为 {@code expected} 的同类型方块位置。
     *  用于捕捉生物变身方块被活塞/粘液块推、弹后的新坐标（活塞单次最多推 12 格）。 */
    private static BlockPos findMovedTransmutedBlock(ServerLevel level, BlockPos origin,
            net.minecraft.world.level.block.Block expected) {
        int limit = 12;
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Map<BlockPos, Integer> depth = new java.util.HashMap<>();
        queue.add(origin);
        depth.put(origin, 0);
        // 6 个正交方向的邻格偏移
        int[][] neighbors = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}};
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            int d = depth.get(cur);
            if (d >= limit) continue;
            for (int[] nd : neighbors) {
                BlockPos next = cur.offset(nd[0], nd[1], nd[2]);
                if (next.getY() < level.getMinBuildHeight() || next.getY() > level.getMaxBuildHeight()) continue;
                if (depth.containsKey(next)) continue;
                depth.put(next, d + 1);
                if (level.getBlockState(next).is(expected)) {
                    return next;
                }
                queue.add(next);
            }
        }
        return null;
    }

    private static void tickTransmutationBlocks(ServerLevel level, ResourceLocation dimId) {
        Map<BlockPos, java.util.List<TransmutationData>> dimMap = BLOCK_TRANSMUTATIONS.get(dimId);
        if (dimMap == null || dimMap.isEmpty()) return;

        java.util.Iterator<Map.Entry<BlockPos, java.util.List<TransmutationData>>> it = dimMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, java.util.List<TransmutationData>> entry = it.next();
            BlockPos pos = entry.getKey();
            java.util.List<TransmutationData> list = entry.getValue();
            if (list == null || list.isEmpty()) {
                it.remove();
                continue;
            }

            // --- 位置/类型校验：生物变方块被推拉(活塞/粘液块)后坐标跟随；
            //      方块被破坏或变成其它方块(如树苗→原木)时，该生物死亡 ---
            String fItemType = list.get(0).itemType();
            ResourceLocation fRl = fItemType != null ? ResourceLocation.tryParse(fItemType) : null;
            net.minecraft.world.level.block.Block expected = fRl != null && BuiltInRegistries.BLOCK.containsKey(fRl)
                ? BuiltInRegistries.BLOCK.get(fRl) : null;
            if (expected == null) {
                it.remove();
                for (TransmutationData d : list) {
                    if (d.playerUuid() != null) killTransmutedPlayer(level, d, pos, true);
                    else handleTransmutationKillCredit(level, d, pos, d.killerPlayerUuid());
                }
                continue;
            }
            if (!level.getBlockState(pos).is(expected)) {
                // 原位方块已不是该生物对应的方块：先在邻接范围搜索被推/弹到的新位置
                BlockPos newPos = findMovedTransmutedBlock(level, pos, expected);
                if (newPos != null && !newPos.equals(pos)) {
                    it.remove();
                    dimMap.computeIfAbsent(newPos, k -> new java.util.ArrayList<>()).addAll(list);
                    LOGGER.info("生物变身方块被推拉：从 {} 移动到 {}", pos, newPos);
                    continue; // 已迁移，本刻不倒计时，下刻在新位置按原位逻辑继续
                }
                // 原位是空气：方块被推拉/破坏移走但未能在邻域定位。不判死生物（仅保留倒计时，
                // 由下方倒计时逻辑在原位置复原），避免活塞/粘液推动导致误杀。
                if (level.getBlockState(pos).isAir()) {
                    // 位置跟踪暂不可用：保持当前倒计时，不做死亡结算
                } else {
                    // 原位被其它方块取代（类型变化，如“树苗→原木”）：该生物死亡
                    it.remove();
                    for (TransmutationData d : list) {
                        if (d.playerUuid() != null) killTransmutedPlayer(level, d, pos, true);
                        else handleTransmutationKillCredit(level, d, pos, d.killerPlayerUuid());
                    }
                    continue;
                }
            }

            // 对同一个位置上的每条生物数据逐条倒计时，满足条件的一条条复原
            for (int i = list.size() - 1; i >= 0; i--) {
                TransmutationData data = list.get(i);
                // 玩家变身为方块时不会进入本分支（下落方块在 FALLING_TRANSMUTATIONS 内跟随玩家，
                // 永不落地成放置方块）。此处仅作兜底：离线则暂停倒计时等待其回归。
                if (data.playerUuid() != null) {
                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(data.playerUuid());
                    if (player == null) {
                        continue;
                    }
                    player.fallDistance = 0.0F;
                }
                TransmutationData newData = new TransmutationData(data.entityNbt, data.remainingTicks - 1,
                    data.killerPlayerUuid(), data.itemType, data.playerUuid());
                list.set(i, newData);
                if (newData.remainingTicks <= 0) {
                    list.remove(i);
                    if (data.playerUuid() != null) {
                        // 玩家复原：恢复游玩模式并传送到该位置；方块若为空会被下方逻辑销毁
                        revertTransmutedPlayer(level, data, pos);
                    } else {
                        respawnTransmutedEntity(level, data, pos);
                    }
                }
            }

            if (list.isEmpty()) {
                it.remove();
                level.destroyBlock(pos, false);
            }
        }
    }
}
