package cn.autoforged.joes_addons_for_abmc.event;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.block.entity.ModBlockEntities;
import cn.autoforged.joes_addons_for_abmc.block.renderer.LuckyDimensionBlockRenderer;
import cn.autoforged.joes_addons_for_abmc.block.renderer.LuckyPortalBlockRenderer;
import cn.autoforged.joes_addons_for_abmc.entity.BedrockFallingBlockRenderer;
import cn.autoforged.joes_addons_for_abmc.entity.DripstoneFallingBlockRenderer;
import cn.autoforged.joes_addons_for_abmc.entity.HerobrineHeadRenderer;
import cn.autoforged.joes_addons_for_abmc.entity.LapisFallingBlockRenderer;
import cn.autoforged.joes_addons_for_abmc.entity.PortalRenderer;
import cn.autoforged.joes_addons_for_abmc.entity.PlayerShellRenderer;
import cn.autoforged.joes_addons_for_abmc.entity.ModEntities;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.TntRenderer;
import cn.autoforged.joes_addons_for_abmc.item.ModDataComponents;
import cn.autoforged.joes_addons_for_abmc.item.ModItems;
import cn.autoforged.joes_addons_for_abmc.item.StaffBakedModel;
import cn.autoforged.joes_addons_for_abmc.item.StaffClientState;
import cn.autoforged.joes_addons_for_abmc.item.StaffItem;
import cn.autoforged.joes_addons_for_abmc.network.BlockingStatePayload;
import cn.autoforged.joes_addons_for_abmc.network.EnchantStaffModePayload;
import cn.autoforged.joes_addons_for_abmc.network.EnchantStaffModeTogglePayload;
import cn.autoforged.joes_addons_for_abmc.network.HerobrineStaffModePayload;
import cn.autoforged.joes_addons_for_abmc.network.HerobrineStaffModeTogglePayload;
import cn.autoforged.joes_addons_for_abmc.network.StaffBlockTypePayload;
import cn.autoforged.joes_addons_for_abmc.network.BellRingPayload;
import cn.autoforged.joes_addons_for_abmc.network.CobwebDisconnectPayload;
import cn.autoforged.joes_addons_for_abmc.client.CobwebClientState;
import cn.autoforged.joes_addons_for_abmc.network.CommandStaffSyncPayload;
import cn.autoforged.joes_addons_for_abmc.network.GameIconCraftPayload;
import cn.autoforged.joes_addons_for_abmc.network.PortalStaffInputPayload;
import cn.autoforged.joes_addons_for_abmc.network.TntDetonatePayload;
import cn.autoforged.joes_addons_for_abmc.client.CommandStaffDataCache;
import cn.autoforged.joes_addons_for_abmc.client.BellRingClientHandler;
import cn.autoforged.joes_addons_for_abmc.client.BarrierStaffHelper;
import cn.autoforged.joes_addons_for_abmc.client.DebugStringRenderer;
import cn.autoforged.joes_addons_for_abmc.client.RedstoneLaserSounds;
import cn.autoforged.joes_addons_for_abmc.client.MaidRedstoneLaserSounds;
import cn.autoforged.joes_addons_for_abmc.network.MaidLaserSoundPayload;
import cn.autoforged.joes_addons_for_abmc.potion.TransmutationBrewingRecipe;
import cn.autoforged.joes_addons_for_abmc.config.ModConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(value = Dist.CLIENT, modid = ModMain.MODID)
public class ClientEvents {
    public static final KeyMapping BLOCK_KEY = new KeyMapping(
        "key.joes_addons_for_abmc.block",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_Z,
        "key.categories.joes_addons_for_abmc"
    );

    private static final ResourceLocation STAFF_GOLD_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_gold");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_GOLD_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_GOLD_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_NETHERITE_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_netherite");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_NETHERITE_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_NETHERITE_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_DIAMOND_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_diamond");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_DIAMOND_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_DIAMOND_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_BEDROCK_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_bedrock");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_BEDROCK_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_BEDROCK_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_OBSIDIAN_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_obsidian");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_OBSIDIAN_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_OBSIDIAN_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_BONE_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_bone");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_BONE_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_BONE_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_FURNACE_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_furnace");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_FURNACE_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_FURNACE_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_FURNACE_ON_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_furnace_on");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_FURNACE_ON_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_FURNACE_ON_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_BELL_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_bell");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_BELL_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_BELL_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_ANVIL_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_anvil");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_ANVIL_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_ANVIL_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_LAPIS_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_lapis");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_LAPIS_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_LAPIS_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_MAGMA_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_magma");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_MAGMA_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_MAGMA_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_OMEGA_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_omega");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_OMEGA_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_OMEGA_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_COMMAND_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_command");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_COMMAND_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_COMMAND_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_END_PORTAL_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_end_portal");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_END_PORTAL_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_END_PORTAL_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_ENCHANT_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_enchant");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_ENCHANT_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_ENCHANT_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_STEVE_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_steve");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_STEVE_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_STEVE_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_HEROBRINE_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_herobrine");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_HEROBRINE_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_HEROBRINE_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_BARRIER_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_barrier");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_BARRIER_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_BARRIER_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_DRIPSTONE_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_dripstone");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_DRIPSTONE_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_DRIPSTONE_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_CAULDRON_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_cauldron");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_CAULDRON_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_CAULDRON_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_CRAFTING_TABLE_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_crafting_table");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_CRAFTING_TABLE_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_CRAFTING_TABLE_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_EMERALD_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_emerald");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_EMERALD_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_EMERALD_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_ICE_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_ice");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_ICE_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_ICE_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_IRON_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_iron");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_IRON_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_IRON_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_NETHERRACK_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_netherrack");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_NETHERRACK_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_NETHERRACK_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_NOTEBLOCK_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_noteblock");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_NOTEBLOCK_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_NOTEBLOCK_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_OAK_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_oak");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_OAK_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_OAK_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_PISTON_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_piston");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_PISTON_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_PISTON_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_RED_MUSHROOM_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_red_mushroom");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_RED_MUSHROOM_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_RED_MUSHROOM_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_REDSTONE_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_redstone");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_REDSTONE_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_REDSTONE_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_SNOW_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_snow");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_SNOW_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_SNOW_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_BEE_NEST_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_bee_nest");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_BEE_NEST_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_BEE_NEST_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_AMETHYST_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_amethyst");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_AMETHYST_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_AMETHYST_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_COBWEB_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_cobweb");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_COBWEB_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_COBWEB_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_SPAWNER_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_spawner");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_SPAWNER_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_SPAWNER_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_TNT_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_tnt");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_TNT_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_TNT_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    private static final ResourceLocation STAFF_MC_MODEL_ID =
        ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "item/staff_minecraft");

    private static final net.minecraft.client.resources.model.ModelResourceLocation STAFF_MC_STANDALONE =
        new net.minecraft.client.resources.model.ModelResourceLocation(STAFF_MC_MODEL_ID,
            net.minecraft.client.resources.model.ModelResourceLocation.STANDALONE_VARIANT);

    /**
     * 玩家右击女仆时，若“当前交互手”持有权杖，则阻止权杖的右键行为（useItem 回退）被触发，
     * 同时保证已驯服女仆的 GUI 仍能正常打开。
     * <p>
     * 客户端交互链路（{@code Minecraft.startUseItem}）：
     * <ol>
     *   <li>{@code gameMode.interactAt} → 触发 {@link PlayerInteractEvent.EntityInteractSpecific}（客户端）；</li>
     *   <li>未消费 → {@code gameMode.interact}（发 INTERACT 包 + {@code player.interactOn → mobInteract}）；</li>
     *   <li>仍返回 PASS → 回退 {@code gameMode.useItem}，从而误触发权杖右键行为。</li>
     * </ol>
     * 只要“当前交互手”持有权杖就取消事件并返回 {@code SUCCESS}，使 {@code startUseItem} 直接 return，
     * 彻底阻断回退到 useItem；而取消后自然流程的 INTERACT 包不再发出，interactAt 预先发送的 INTERACT_AT
     * 包在服务端对女仆返回 PASS（不打开 GUI），故对“已驯服女仆 + 主手”手动补发 INTERACT 包，
     * 让服务端 {@code mobInteract} 仍能打开女仆 GUI。
     * <p>
     * 注意：必须用 {@link PlayerInteractEvent.EntityInteractSpecific}（客户端在 interactAt 阶段触发），
     * 而非 {@link PlayerInteractEvent.EntityInteract}（该事件只在服务端触发，客户端永不触发）。
     */
    @SubscribeEvent
    public static void onInteractMaidWithStaff(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getLevel().isClientSide()) return;
        // 不用 `instanceof EntityMaid`：那会把 EntityMaid 类常量烧进本类字节码，未装车万女仆时
        // 本类被 NeoForge 加载（Class.forName 解析常量池）即抛 NoClassDefFoundError。改用反射判断。
        if (!cn.autoforged.joes_addons_for_abmc.ModMain.isTouhouMaid(event.getTarget())) return;
        net.minecraft.world.entity.Entity maid = event.getTarget();
        Player player = event.getEntity();
        if (player == null) return;
        // 仅当“当前交互手”自身持有权杖时才拦截；若当前手为驯服物品/空手（权杖在另一手），
        // 则由原版流程正常驯服/打开 GUI，不拦截。
        if (!(player.getItemInHand(event.getHand()).getItem() instanceof StaffItem)) return;

        // 取消事件并返回 SUCCESS，使 startUseItem 在 interactAt 阶段直接 return，
        // 不再回退到 gameMode.useItem，从而不发送 use-item 包、权杖右键行为不再触发。
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        // 已驯服女仆 + 主手：interactAt 已发出 INTERACT_AT 包（服务端 interactAt 对女仆返回 PASS，
        // 不会打开 GUI），而取消后又不会再发出 INTERACT 包，故这里手动补发 INTERACT 包，
        // 让服务端 mobInteract 仍然打开女仆 GUI。
        if (cn.autoforged.joes_addons_for_abmc.ModMain.isMaidOwnedBy(maid, player)
                && event.getHand() == InteractionHand.MAIN_HAND) {
            if (Minecraft.getInstance().getConnection() != null) {
                Minecraft.getInstance().getConnection().getConnection().send(
                    ServerboundInteractPacket.createInteractionPacket(
                        maid, player.isShiftKeyDown(), InteractionHand.MAIN_HAND));
            }
        }

        // 权杖右键行为已被取消，但红石权杖的激光音效状态机由右键输入独立驱动，
        // 交互瞬间若仍在播放 laser_middle 循环不会自行停止，故强制停止并复位，
        // 避免“右击女仆仍听到激光音效”。若正处于 laser_start 阶段则直接复位。
        RedstoneLaserSounds.stopAndReset(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        // 注入变形药水中位主色提取函数（材质仅在客户端资源管理器中可用）
        TransmutationBrewingRecipe.setClientColorProvider(ClientEvents::readItemMedianColor);
        event.getItemColors().register(
            (stack, tintIndex) -> tintIndex == 0 ? GrassColor.getDefaultColor() : -1,
            ModItems.GAME_ICON.get()
        );
    }

    // 从物品/方块的默认贴图中提取“中位主色”：不透明像素颜色升序排序后取中位数
    private static int readItemMedianColor(Item item) {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(item);
        List<ResourceLocation> candidates = new ArrayList<>();
        candidates.add(ResourceLocation.fromNamespaceAndPath(
            itemKey.getNamespace(), "textures/item/" + itemKey.getPath() + ".png"));
        Block block = BuiltInRegistries.BLOCK.get(itemKey);
        if (block != null) {
            candidates.add(ResourceLocation.fromNamespaceAndPath(
                itemKey.getNamespace(), "textures/block/" + itemKey.getPath() + ".png"));
        }

        List<Integer> colors = new ArrayList<>();
        for (ResourceLocation path : candidates) {
            Optional<Resource> resource = resourceManager.getResource(path);
            if (resource.isEmpty()) continue;
            try (InputStream in = resource.get().open()) {
                BufferedImage image = ImageIO.read(in);
                if (image == null) continue;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int argb = image.getRGB(x, y);
                        if (((argb >>> 24) & 0xFF) < 128) continue;
                        colors.add(argb & 0xFFFFFF);
                    }
                }
            } catch (Exception ignored) {
                // 忽略单张贴图解码失败
            }
            if (!colors.isEmpty()) break;
        }

        if (colors.isEmpty()) return -1;
        colors.sort(Integer::compareTo);
        return colors.get(colors.size() / 2);
    }

    @SubscribeEvent
    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(STAFF_GOLD_STANDALONE);
        event.register(STAFF_NETHERITE_STANDALONE);
        event.register(STAFF_DIAMOND_STANDALONE);
        event.register(STAFF_BEDROCK_STANDALONE);
        event.register(STAFF_OBSIDIAN_STANDALONE);
        event.register(STAFF_BONE_STANDALONE);
        event.register(STAFF_FURNACE_STANDALONE);
        event.register(STAFF_FURNACE_ON_STANDALONE);
        event.register(STAFF_BELL_STANDALONE);
        event.register(STAFF_ANVIL_STANDALONE);
        event.register(STAFF_LAPIS_STANDALONE);
        event.register(STAFF_MAGMA_STANDALONE);
        event.register(STAFF_OMEGA_STANDALONE);
        event.register(STAFF_COMMAND_STANDALONE);
        event.register(STAFF_END_PORTAL_STANDALONE);
        event.register(STAFF_ENCHANT_STANDALONE);
        event.register(STAFF_STEVE_STANDALONE);
        event.register(STAFF_HEROBRINE_STANDALONE);
        event.register(STAFF_BARRIER_STANDALONE);
        event.register(STAFF_DRIPSTONE_STANDALONE);
        event.register(STAFF_CAULDRON_STANDALONE);
        event.register(STAFF_CRAFTING_TABLE_STANDALONE);
        event.register(STAFF_EMERALD_STANDALONE);
        event.register(STAFF_ICE_STANDALONE);
        event.register(STAFF_IRON_STANDALONE);
        event.register(STAFF_NETHERRACK_STANDALONE);
        event.register(STAFF_NOTEBLOCK_STANDALONE);
        event.register(STAFF_OAK_STANDALONE);
        event.register(STAFF_PISTON_STANDALONE);
        event.register(STAFF_RED_MUSHROOM_STANDALONE);
        event.register(STAFF_REDSTONE_STANDALONE);
        event.register(STAFF_SNOW_STANDALONE);
        event.register(STAFF_BEE_NEST_STANDALONE);
        event.register(STAFF_AMETHYST_STANDALONE);
        event.register(STAFF_COBWEB_STANDALONE);
        event.register(STAFF_SPAWNER_STANDALONE);
        event.register(STAFF_TNT_STANDALONE);
        event.register(STAFF_MC_STANDALONE);
    }

    public static final KeyMapping STAFF_SWAP_BLOCKTYPE = new KeyMapping(
        "key.joes_addons_for_abmc.staff_swap_blocktype",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.MOUSE,
        GLFW.GLFW_MOUSE_BUTTON_3,
        "key.categories.joes_addons_for_abmc"
    );

    // 屏障权杖：调整屏障贴图相对权杖的 X/Y/Z 轴位移量（视觉位置）
    public static final KeyMapping STAFF_OFFSET_X_NEG = new KeyMapping(
        "key.joes_addons_for_abmc.staff_offset_x_neg",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_X,
        "key.categories.joes_addons_for_abmc"
    );

    public static final KeyMapping STAFF_OFFSET_X_POS = new KeyMapping(
        "key.joes_addons_for_abmc.staff_offset_x_pos",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_C,
        "key.categories.joes_addons_for_abmc"
    );

    public static final KeyMapping STAFF_OFFSET_Y_NEG = new KeyMapping(
        "key.joes_addons_for_abmc.staff_offset_y_neg",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,
        "key.categories.joes_addons_for_abmc"
    );

    public static final KeyMapping STAFF_OFFSET_Y_POS = new KeyMapping(
        "key.joes_addons_for_abmc.staff_offset_y_pos",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_B,
        "key.categories.joes_addons_for_abmc"
    );

    public static final KeyMapping STAFF_OFFSET_Z_NEG = new KeyMapping(
        "key.joes_addons_for_abmc.staff_offset_z_neg",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_N,
        "key.categories.joes_addons_for_abmc"
    );

    public static final KeyMapping STAFF_OFFSET_Z_POS = new KeyMapping(
        "key.joes_addons_for_abmc.staff_offset_z_pos",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_M,
        "key.categories.joes_addons_for_abmc"
    );

    // 屏障权杖：将当前 X/Y/Z 位移量导出到 JSON 文件，便于玩家把数值反馈给作者永久调整
    public static final KeyMapping STAFF_OFFSET_EXPORT = new KeyMapping(
        "key.joes_addons_for_abmc.staff_offset_export",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_P,
        "key.categories.joes_addons_for_abmc"
    );

    // 屏障权杖：按住左Alt 键整体平移屏障群（带发送冷却，可重复平移）
    // 注意：按键名故意取新值（barrier_shift），从而覆盖旧版本在 options.txt 里记住的 R 键绑定，
    // 强制玩家使用左Alt（若沿用旧名，已保存的 R 仍会覆盖新的左Alt默认值）。
    public static final KeyMapping STAFF_BARRIER_SHIFT = new KeyMapping(
        "key.joes_addons_for_abmc.barrier_shift",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_LEFT_ALT,
        "key.categories.joes_addons_for_abmc"
    );

    // 传送门权杖：收敛/塌缩已改为左键（非创建状态时）；左Alt 保留给「取消放置」
    public static final KeyMapping PORTAL_CANCEL_KEY = new KeyMapping(
        "key.joes_addons_for_abmc.portal_cancel_key",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_LEFT_ALT,
        "key.categories.joes_addons_for_abmc"
    );

    private static boolean wasBlocking = false;
    public static boolean isClientBlocking = false;

    private static boolean propertiesRegistered = false;

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(BLOCK_KEY);
        event.register(STAFF_SWAP_BLOCKTYPE);
        event.register(STAFF_OFFSET_X_NEG);
        event.register(STAFF_OFFSET_X_POS);
        event.register(STAFF_OFFSET_Y_NEG);
        event.register(STAFF_OFFSET_Y_POS);
        event.register(STAFF_OFFSET_Z_NEG);
        event.register(STAFF_OFFSET_Z_POS);
        event.register(STAFF_OFFSET_EXPORT);
        event.register(STAFF_BARRIER_SHIFT);
        event.register(PORTAL_CANCEL_KEY);
        if (!propertiesRegistered) {
            propertiesRegistered = true;
            net.minecraft.client.renderer.item.ItemProperties.register(
                ModItems.GLISTERING_MELON_KNIFE.get(),
                ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "blocking"),
                (stack, level, entity, seed) -> isClientBlocking ? 1.0F : 0.0F
            );
        }
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 音符传送门：与下界传送门一样用半透明渲染层
        net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
            cn.autoforged.joes_addons_for_abmc.block.ModBlocks.NOTE_PORTAL.get(),
            RenderType.translucent());
        // 冰块权杖霜冰：强制半透明渲染层，使 50% 透明度的贴图 alpha 生效（否则会被当作不透明方块而忽略 alpha）。
        net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
            cn.autoforged.joes_addons_for_abmc.block.ModBlocks.JOB_FROSTED_ICE.get(),
            RenderType.translucent());
        // 霜冰原方块若被还原出不可见/其它半透明方块则由原版处理，无需设置。
        event.registerEntityRenderer(ModEntities.THROWN_GLISTERING_MELON_KNIFE.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.PRISMARINE_ARROW.get(), PrismarineArrowRenderer::new);
        event.registerEntityRenderer(ModEntities.BEDROCK_FALLING_BLOCK.get(), BedrockFallingBlockRenderer::new);
        event.registerEntityRenderer(ModEntities.LAPIS_FALLING_BLOCK.get(), LapisFallingBlockRenderer::new);
        event.registerEntityRenderer(ModEntities.TRANSMUTATION_FALLING_BLOCK.get(),
            cn.autoforged.joes_addons_for_abmc.entity.TransmutationFallingBlockRenderer::new);
        event.registerEntityRenderer(ModEntities.DRIPSTONE_FALLING_BLOCK.get(), DripstoneFallingBlockRenderer::new);
        event.registerEntityRenderer(ModEntities.PORTAL.get(), PortalRenderer::new);
        event.registerEntityRenderer(ModEntities.HEROBRINE_HEAD.get(), HerobrineHeadRenderer::new);
        event.registerEntityRenderer(ModEntities.PLAYER_SHELL.get(), PlayerShellRenderer::new);
        event.registerEntityRenderer(ModEntities.TNT_STAFF_PRIMED_TNT.get(), TntRenderer::new);
        event.registerEntityRenderer(ModEntities.TNT_STAFF_CREEPER.get(), CreeperRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LUCKY_DIMENSION_BLOCK_ENTITY.get(), LuckyDimensionBlockRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LUCKY_PORTAL_BLOCK_ENTITY.get(), LuckyPortalBlockRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LUCKY_DIMENSION_BLOCK_ENTITY.get(), LuckyDimensionBlockRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LUCKY_PORTAL_BLOCK_ENTITY.get(), LuckyPortalBlockRenderer::new);
    }

    /** 给所有 LivingEntityRenderer 附加“移植头/移植脚”层，使 spawner 权杖召唤的生物正确渲染外来头/外来脚。 */
    @SubscribeEvent
    public static void addTransplantedHeadLayers(EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.world.entity.EntityType<?> type : event.getEntityTypes()) {
            try {
                net.minecraft.client.renderer.entity.EntityRenderer<?> renderer = event.getRenderer(type);
                if (renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer lr) {
                    lr.addLayer(new cn.autoforged.joes_addons_for_abmc.client.TransplantedHeadLayer(lr));
                    lr.addLayer(new cn.autoforged.joes_addons_for_abmc.client.TransplantedFeetLayer(lr));
                    lr.addLayer(new cn.autoforged.joes_addons_for_abmc.client.EnchantGlintLayer(lr));
                }
            } catch (Exception ignored) {
                // 个别类型添加层失败，不影响其余类型
            }
        }
    }

    /** 移植头/移植脚（Pre）：渲染身体前隐藏其原头/原腿部部件；模型实例跨实体共享，需在 Post 还原。 */
    @SubscribeEvent
    public static void onRenderLivingPre(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre event) {
        try {
            cn.autoforged.joes_addons_for_abmc.client.TransplantedHeadLayer.hideBodyHead(
                event.getRenderer().getModel(), event.getEntity().getId());
            cn.autoforged.joes_addons_for_abmc.client.TransplantedFeetLayer.hideBodyFeet(
                event.getRenderer().getModel(), event.getEntity().getId());
        } catch (Exception ignored) {
        }
    }

    /** 变形中：彻底隐藏本地玩家自身的渲染（含手持物与穿戴装备），实现完全隐身。 */
    @SubscribeEvent
    public static void onRenderLivingHideTransmuted(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre event) {
        try {
            if (event.getEntity() == net.minecraft.client.Minecraft.getInstance().player
                && cn.autoforged.joes_addons_for_abmc.client.TransmutationCameraClient.isTransmuted()) {
                event.setCanceled(true);
            }
        } catch (Exception ignored) {
        }
    }

    /** 移植头/移植脚（Post）：还原被隐藏的原头/原腿部部件，避免影响同类型其余实体。 */
    @SubscribeEvent
    public static void onRenderLivingPost(net.neoforged.neoforge.client.event.RenderLivingEvent.Post event) {
        try {
            cn.autoforged.joes_addons_for_abmc.client.TransplantedHeadLayer.restoreBodyHead(
                event.getRenderer().getModel(), event.getEntity().getId());
            cn.autoforged.joes_addons_for_abmc.client.TransplantedFeetLayer.restoreBodyFeet(
                event.getRenderer().getModel(), event.getEntity().getId());
        } catch (Exception ignored) {
        }
    }

    /** 变形中：原版第三人称镜头距离会乘上玩家缩放（物品 0.13 / 方块 0.5），导致镜头被拉到极近。
     *  这里把距离除以缩放，使各形态下第三人称镜头距离与 scale=1 时保持一致（默认约 4 格）。 */
    @SubscribeEvent
    public static void onCalculateCameraDistance(
            net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent event) {
        try {
            if (cn.autoforged.joes_addons_for_abmc.client.TransmutationCameraClient.isTransmuted()) {
                float f = event.getEntityScalingFactor();
                if (f > 0.001F && Math.abs(f - 1.0F) > 0.001F) {
                    event.setDistance(event.getDistance() / f);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static final net.minecraft.client.model.geom.ModelLayerLocation HEROBRINE_HEAD_LAYER =
        new net.minecraft.client.model.geom.ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "herobrine_head"), "main");

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HEROBRINE_HEAD_LAYER, HerobrineHeadRenderer::createHerobrineHeadLayer);
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ResourceLocation staffModelId = ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "staff");
        net.minecraft.client.resources.model.ModelResourceLocation staffMRL =
            new net.minecraft.client.resources.model.ModelResourceLocation(staffModelId, "inventory");

        if (event.getModels().containsKey(staffMRL)
            && event.getModels().containsKey(STAFF_GOLD_STANDALONE)
            && event.getModels().containsKey(STAFF_NETHERITE_STANDALONE)
            && event.getModels().containsKey(STAFF_DIAMOND_STANDALONE)
            && event.getModels().containsKey(STAFF_BEDROCK_STANDALONE)
            && event.getModels().containsKey(STAFF_OBSIDIAN_STANDALONE)
            && event.getModels().containsKey(STAFF_BONE_STANDALONE)
            && event.getModels().containsKey(STAFF_FURNACE_STANDALONE)
            && event.getModels().containsKey(STAFF_FURNACE_ON_STANDALONE)
            && event.getModels().containsKey(STAFF_BELL_STANDALONE)
            && event.getModels().containsKey(STAFF_ANVIL_STANDALONE)
            && event.getModels().containsKey(STAFF_LAPIS_STANDALONE)
            && event.getModels().containsKey(STAFF_MAGMA_STANDALONE)
            && event.getModels().containsKey(STAFF_OMEGA_STANDALONE)
            && event.getModels().containsKey(STAFF_COMMAND_STANDALONE)
            && event.getModels().containsKey(STAFF_END_PORTAL_STANDALONE)
            && event.getModels().containsKey(STAFF_ENCHANT_STANDALONE)
            && event.getModels().containsKey(STAFF_STEVE_STANDALONE)
            && event.getModels().containsKey(STAFF_HEROBRINE_STANDALONE)
            && event.getModels().containsKey(STAFF_BARRIER_STANDALONE)
            && event.getModels().containsKey(STAFF_DRIPSTONE_STANDALONE)
            && event.getModels().containsKey(STAFF_CAULDRON_STANDALONE)
            && event.getModels().containsKey(STAFF_CRAFTING_TABLE_STANDALONE)
            && event.getModels().containsKey(STAFF_EMERALD_STANDALONE)
            && event.getModels().containsKey(STAFF_ICE_STANDALONE)
            && event.getModels().containsKey(STAFF_IRON_STANDALONE)
            && event.getModels().containsKey(STAFF_NETHERRACK_STANDALONE)
            && event.getModels().containsKey(STAFF_NOTEBLOCK_STANDALONE)
            && event.getModels().containsKey(STAFF_OAK_STANDALONE)
            && event.getModels().containsKey(STAFF_PISTON_STANDALONE)
            && event.getModels().containsKey(STAFF_RED_MUSHROOM_STANDALONE)
            && event.getModels().containsKey(STAFF_REDSTONE_STANDALONE)
            && event.getModels().containsKey(STAFF_SNOW_STANDALONE)
            && event.getModels().containsKey(STAFF_BEE_NEST_STANDALONE)
            && event.getModels().containsKey(STAFF_AMETHYST_STANDALONE)
            && event.getModels().containsKey(STAFF_COBWEB_STANDALONE)
            && event.getModels().containsKey(STAFF_SPAWNER_STANDALONE)
            && event.getModels().containsKey(STAFF_TNT_STANDALONE)
            && event.getModels().containsKey(STAFF_MC_STANDALONE)) {
            net.minecraft.client.resources.model.BakedModel defaultModel = event.getModels().get(staffMRL);
            net.minecraft.client.resources.model.BakedModel goldModel = event.getModels().get(STAFF_GOLD_STANDALONE);
            net.minecraft.client.resources.model.BakedModel netheriteModel = event.getModels().get(STAFF_NETHERITE_STANDALONE);
            net.minecraft.client.resources.model.BakedModel diamondModel = event.getModels().get(STAFF_DIAMOND_STANDALONE);
            net.minecraft.client.resources.model.BakedModel bedrockModel = event.getModels().get(STAFF_BEDROCK_STANDALONE);
            net.minecraft.client.resources.model.BakedModel obsidianModel = event.getModels().get(STAFF_OBSIDIAN_STANDALONE);
            net.minecraft.client.resources.model.BakedModel boneModel = event.getModels().get(STAFF_BONE_STANDALONE);
            net.minecraft.client.resources.model.BakedModel furnaceModel = event.getModels().get(STAFF_FURNACE_STANDALONE);
            net.minecraft.client.resources.model.BakedModel furnaceOnModel = event.getModels().get(STAFF_FURNACE_ON_STANDALONE);
            net.minecraft.client.resources.model.BakedModel bellModel = event.getModels().get(STAFF_BELL_STANDALONE);
            net.minecraft.client.resources.model.BakedModel anvilModel = event.getModels().get(STAFF_ANVIL_STANDALONE);
            net.minecraft.client.resources.model.BakedModel lapisModel = event.getModels().get(STAFF_LAPIS_STANDALONE);
            net.minecraft.client.resources.model.BakedModel magmaModel = event.getModels().get(STAFF_MAGMA_STANDALONE);
            net.minecraft.client.resources.model.BakedModel omegaModel = event.getModels().get(STAFF_OMEGA_STANDALONE);
            net.minecraft.client.resources.model.BakedModel commandModel = event.getModels().get(STAFF_COMMAND_STANDALONE);
            net.minecraft.client.resources.model.BakedModel endPortalModel = event.getModels().get(STAFF_END_PORTAL_STANDALONE);
            net.minecraft.client.resources.model.BakedModel enchantModel = event.getModels().get(STAFF_ENCHANT_STANDALONE);
            net.minecraft.client.resources.model.BakedModel playerHeadModel = event.getModels().get(STAFF_STEVE_STANDALONE);
            net.minecraft.client.resources.model.BakedModel herobrineModel = event.getModels().get(STAFF_HEROBRINE_STANDALONE);
            net.minecraft.client.resources.model.BakedModel barrierModel = event.getModels().get(STAFF_BARRIER_STANDALONE);
            net.minecraft.client.resources.model.BakedModel dripstoneModel = event.getModels().get(STAFF_DRIPSTONE_STANDALONE);
            net.minecraft.client.resources.model.BakedModel cauldronModel = event.getModels().get(STAFF_CAULDRON_STANDALONE);
            net.minecraft.client.resources.model.BakedModel craftingTableModel = event.getModels().get(STAFF_CRAFTING_TABLE_STANDALONE);
            net.minecraft.client.resources.model.BakedModel emeraldModel = event.getModels().get(STAFF_EMERALD_STANDALONE);
            net.minecraft.client.resources.model.BakedModel iceModel = event.getModels().get(STAFF_ICE_STANDALONE);
            net.minecraft.client.resources.model.BakedModel ironModel = event.getModels().get(STAFF_IRON_STANDALONE);
            net.minecraft.client.resources.model.BakedModel netherrackModel = event.getModels().get(STAFF_NETHERRACK_STANDALONE);
            net.minecraft.client.resources.model.BakedModel noteblockModel = event.getModels().get(STAFF_NOTEBLOCK_STANDALONE);
            net.minecraft.client.resources.model.BakedModel oakModel = event.getModels().get(STAFF_OAK_STANDALONE);
            net.minecraft.client.resources.model.BakedModel pistonModel = event.getModels().get(STAFF_PISTON_STANDALONE);
            net.minecraft.client.resources.model.BakedModel redMushroomModel = event.getModels().get(STAFF_RED_MUSHROOM_STANDALONE);
            net.minecraft.client.resources.model.BakedModel redstoneModel = event.getModels().get(STAFF_REDSTONE_STANDALONE);
            net.minecraft.client.resources.model.BakedModel snowModel = event.getModels().get(STAFF_SNOW_STANDALONE);
            net.minecraft.client.resources.model.BakedModel beeNestModel = event.getModels().get(STAFF_BEE_NEST_STANDALONE);
            net.minecraft.client.resources.model.BakedModel amethystModel = event.getModels().get(STAFF_AMETHYST_STANDALONE);
            net.minecraft.client.resources.model.BakedModel cobwebModel = event.getModels().get(STAFF_COBWEB_STANDALONE);
            net.minecraft.client.resources.model.BakedModel spawnerModel = event.getModels().get(STAFF_SPAWNER_STANDALONE);
            net.minecraft.client.resources.model.BakedModel tntModel = event.getModels().get(STAFF_TNT_STANDALONE);
            net.minecraft.client.resources.model.BakedModel mcModel = event.getModels().get(STAFF_MC_STANDALONE);
            event.getModels().put(staffMRL, new StaffBakedModel(defaultModel, goldModel, netheriteModel, diamondModel, bedrockModel, obsidianModel, boneModel, furnaceModel, furnaceOnModel, bellModel, anvilModel, lapisModel, magmaModel, omegaModel, commandModel, endPortalModel, enchantModel, playerHeadModel, herobrineModel, barrierModel, dripstoneModel, cauldronModel, craftingTableModel, emeraldModel, iceModel, ironModel, netherrackModel, noteblockModel, oakModel, pistonModel, redMushroomModel, redstoneModel, snowModel, beeNestModel, amethystModel, cobwebModel, spawnerModel, tntModel, mcModel));
        }
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean holdingLapis = false;
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        if (mainHand.getItem() instanceof StaffItem) {
            String bt = mainHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
            if ("lapis_block".equals(bt)) holdingLapis = true;
        }
        if (!holdingLapis && offHand.getItem() instanceof StaffItem) {
            String bt = offHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
            if ("lapis_block".equals(bt)) holdingLapis = true;
        }

        if (holdingLapis && mc.player.experienceLevel < 1) {
            GuiGraphics guiGraphics = event.getGuiGraphics();
            Font font = mc.font;
            String text = "经验不足";
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            int textWidth = font.width(text);
            guiGraphics.drawString(font, text, (screenWidth - textWidth) / 2, staffModeTextY(mc, screenHeight - 60), 0xFFFF5555);
        }

        // 附魔台权杖：屏幕中下方显示当前模式（日常模式 / 疯狂模式）
        if (isHoldingEnchantStaff()) {
            GuiGraphics guiGraphics = event.getGuiGraphics();
            Font font = mc.font;
            String text = StaffClientState.enchantCrazyMode ? "疯狂模式" : "日常模式";
            int color = StaffClientState.enchantCrazyMode ? 0xFFFF5555 : 0xFF55FF55;
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            int textWidth = font.width(text);
            guiGraphics.drawString(font, text, (screenWidth - textWidth) / 2, staffModeTextY(mc, screenHeight - 60), color);
        }

        // 红石块权杖：按住右键（调整/发射充能）时，在屏幕中下方显示当前充能数指示器
        if (isHoldingRedstoneStaff() && mc.options.keyUse.isDown()) {
            GuiGraphics guiGraphics = event.getGuiGraphics();
            Font font = mc.font;
            String text = "充能: " + ClientTickHandler.redstoneStaffCharge;
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            int textWidth = font.width(text);
            guiGraphics.drawString(font, text, (screenWidth - textWidth) / 2, staffModeTextY(mc, screenHeight - 60), 0xFFFFFFFF);
        }

        // Him 权杖：屏幕中下方显示当前模式（近战模式 / 远程模式），与青金石/附魔台权杖一致
        if (isHoldingHerobrineStaff()) {
            GuiGraphics guiGraphics = event.getGuiGraphics();
            Font font = mc.font;
            String text = StaffClientState.herobrineRanged ? "远程模式" : "近战模式";
            int color = StaffClientState.herobrineRanged ? 0xFF55C8FF : 0xFFFFFFFF;
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            int textWidth = font.width(text);
            guiGraphics.drawString(font, text, (screenWidth - textWidth) / 2, staffModeTextY(mc, screenHeight - 60), color);
        }

        // 命令方块权杖：切换能力后在屏幕中下方显示 3 秒提示并淡出
        if (isHoldingCommandStaff() && StaffClientState.commandStaffModeFlashTicks > 0) {
            GuiGraphics guiGraphics = event.getGuiGraphics();
            Font font = mc.font;
            int mode = StaffClientState.commandStaffMode;
            String text;
            int color;
            switch (mode) {
                case 0 -> { text = "无模式"; color = 0xAAAAAA; }
                case 1 -> { text = "击杀模式"; color = 0xFF5555; }
                case 2 -> { text = "抓取模式"; color = 0xFFCC44; }
                case 3 -> { text = "启用/禁用AI"; color = 0x55FF88; }
                case 4 -> { text = "护盾模式"; color = 0x55C8FF; }
                default -> { text = "未知模式"; color = 0xFFFFFF; }
            }
            int alpha = Math.min(255,
                (int) ((StaffClientState.commandStaffModeFlashTicks / 60.0f) * 255.0f));
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            int textWidth = font.width(text);
            guiGraphics.drawString(font, text, (screenWidth - textWidth) / 2, staffModeTextY(mc, screenHeight - 60),
                (alpha << 24) | (color & 0xFFFFFF));
        }

        // Omega 权杖：生存/冒险模式中键拆解被拒绝时，在屏幕中下方显示提示
        if (StaffClientState.omegaDismantleForbiddenTicks > 0) {
            GuiGraphics guiGraphics = event.getGuiGraphics();
            Font font = mc.font;
            String text = "既然装上了，就要为此负责……";
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            int textWidth = font.width(text);
            guiGraphics.drawString(font, text, (screenWidth - textWidth) / 2, staffModeTextY(mc, screenHeight - 60), 0xFFFF5555);
        }
    }

    /** 计算权杖模式文本的 Y 坐标。创造/旁观模式下原版物品名称提示会额外下移 14 像素（见 Gui#renderSelectedItemName），
     *  这里同样处理使模式文本跟随；再叠加配置里的垂直偏移（默认 -20，整体上移，避免与物品描述文本重叠）。 */
    private static int staffModeTextY(Minecraft mc, int baseY) {
        int follow = mc.gameMode.canHurtPlayer() ? 0 : 14;
        return baseY + follow + ModConfig.MODE_TEXT_Y_OFFSET.get();
    }

    /** 判断当前是否持有红石块权杖（主手或副手）。 */
    private static boolean isHoldingRedstoneStaff() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandItem();
        if (mainHand.getItem() instanceof StaffItem
            && "redstone_block".equals(mainHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"))) {
            return true;
        }
        ItemStack offHand = mc.player.getOffhandItem();
        return offHand.getItem() instanceof StaffItem
            && "redstone_block".equals(offHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"));
    }

    /** 判断当前是否持有附魔台权杖（主手或副手）。 */
    private static boolean isHoldingEnchantStaff() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandItem();
        if (mainHand.getItem() instanceof StaffItem
            && "enchanting_table".equals(mainHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"))) {
            return true;
        }
        ItemStack offHand = mc.player.getOffhandItem();
        return offHand.getItem() instanceof StaffItem
            && "enchanting_table".equals(offHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"));
    }

    /** 判断当前是否持有 TNT 权杖（主手或副手）。 */
    private static boolean isHoldingTntStaff() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandItem();
        if (mainHand.getItem() instanceof StaffItem
            && "tnt".equals(mainHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"))) {
            return true;
        }
        ItemStack offHand = mc.player.getOffhandItem();
        return offHand.getItem() instanceof StaffItem
            && "tnt".equals(offHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"));
    }

    /** 判断当前是否持有 Him 权杖（主手或副手）。 */
    private static boolean isHoldingHerobrineStaff() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandItem();
        if (mainHand.getItem() instanceof StaffItem
            && "herobrine_head".equals(mainHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"))) {
            return true;
        }
        ItemStack offHand = mc.player.getOffhandItem();
        return offHand.getItem() instanceof StaffItem
            && "herobrine_head".equals(offHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"));
    }

    /** 判断当前主手是否持有命令方块权杖。仅主手生效：副手中的命令方块权杖视为普通物品，不触发任何模式能力或文本。 */
    private static boolean isHoldingCommandStaff() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandItem();
        return mainHand.getItem() instanceof StaffItem
            && "command_block".equals(mainHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"));
    }

    /** 判断当前是否持有蜘蛛网权杖（主手或副手）。 */
    private static boolean isHoldingCobwebStaff() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandItem();
        if (mainHand.getItem() instanceof StaffItem
            && "cobweb".equals(mainHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"))) {
            return true;
        }
        ItemStack offHand = mc.player.getOffhandItem();
        return offHand.getItem() instanceof StaffItem
            && "cobweb".equals(offHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"));
    }

    /** 判断当前是否持有铁块权杖（主手或副手）。 */
    private static boolean isHoldingIronChainStaff() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandItem();
        if (mainHand.getItem() instanceof StaffItem
            && "iron_block".equals(mainHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"))) {
            return true;
        }
        ItemStack offHand = mc.player.getOffhandItem();
        return offHand.getItem() instanceof StaffItem
            && "iron_block".equals(offHand.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"));
    }

    @SubscribeEvent
    public static void registerClientPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModMain.MODID);
        registrar.playToClient(
            BellRingPayload.TYPE,
            BellRingPayload.STREAM_CODEC,
            BellRingClientHandler::handleBellRing
        );
        registrar.playToClient(
            CommandStaffSyncPayload.TYPE,
            CommandStaffSyncPayload.STREAM_CODEC,
            (payload, context) -> {
                CommandStaffDataCache.commandHistory = payload.history();
                CommandStaffDataCache.presets = payload.presets();
            }
        );
        registrar.playToClient(
            EnchantStaffModePayload.TYPE,
            EnchantStaffModePayload.STREAM_CODEC,
            (payload, context) -> StaffClientState.enchantCrazyMode = payload.crazy()
        );
        registrar.playToClient(
            MaidLaserSoundPayload.TYPE,
            MaidLaserSoundPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() ->
                MaidRedstoneLaserSounds.handleMaidLaser(payload.maidId(), payload.action(),
                    payload.x(), payload.y(), payload.z(),
                    payload.endX(), payload.endY(), payload.endZ()))
        );
        registrar.playToClient(
            HerobrineStaffModePayload.TYPE,
            HerobrineStaffModePayload.STREAM_CODEC,
            (payload, context) -> StaffClientState.herobrineRanged = payload.ranged()
        );
        registrar.playToClient(
            cn.autoforged.joes_addons_for_abmc.network.CommandStaffModePayload.TYPE,
            cn.autoforged.joes_addons_for_abmc.network.CommandStaffModePayload.STREAM_CODEC,
            (payload, context) -> {
                StaffClientState.commandStaffMode = payload.mode();
                // 切换能力后显示 3 秒（60 刻）提示并淡出
                StaffClientState.commandStaffModeFlashTicks = 60;
            }
        );
    }

    // 离开世界（回到标题画面/切换存档）时清空调试端点/线段，并把汇聚偏移重置为默认值，
    // 避免旧的粒子与偏移量跨存档残留。
    @SubscribeEvent
    public static void onClientLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        DebugStringRenderer.reset();
        CobwebClientState.reset();
        cn.autoforged.joes_addons_for_abmc.client.TransplantedHeadClientState.clear();
        cn.autoforged.joes_addons_for_abmc.client.TransplantedFeetClientState.clear();
        cn.autoforged.joes_addons_for_abmc.item.StaffClientState.clearEnchantSelf();
        cn.autoforged.joes_addons_for_abmc.client.TransmutationCameraClient.reset();
        MaidRedstoneLaserSounds.reset();
        beamSmoothReady = false;
        beamSmoothAnchor = null;
    }

    @EventBusSubscriber(value = Dist.CLIENT, modid = ModMain.MODID)
    public static class ClientTickHandler {
        /** 附魔台权杖：客户端每刻更新连线粒子（配合服务端每 4 刻的真实附魔）。自体附魔的光效由
         *  EnchantSelfPayload（持久）驱动，无需在此记录目标。 */
        private static void updateEnchantStaffParticles(net.minecraft.client.Minecraft mc) {
            if (mc.level == null || mc.player == null) return;
            net.minecraft.world.entity.player.Player player = mc.player;
            // 仅在玩家正按住右键使用附魔台权杖时渲染
            if (!player.isUsingItem()) return;
            net.minecraft.world.item.ItemStack useStack = player.getUseItem();
            if (!(useStack.getItem() instanceof StaffItem)) return;
            if (!"enchanting_table".equals(useStack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"))) return;

            // 与服务端 executeEnchantStaffTick 相同的射线检测，找出瞄准的生物（排除自己）
            double range = 20.0;
            net.minecraft.world.phys.Vec3 eye = player.getEyePosition(1.0F);
            net.minecraft.world.phys.Vec3 look = player.getLookAngle();
            net.minecraft.world.phys.AABB searchBox = player.getBoundingBox()
                .expandTowards(look.scale(range)).inflate(1.0);
            net.minecraft.world.entity.LivingEntity target = null;
            double bestDist = range * range;
            for (net.minecraft.world.entity.Entity e : mc.level.getEntities(player, searchBox,
                e -> e instanceof net.minecraft.world.entity.LivingEntity && e != player)) {
                net.minecraft.world.phys.AABB bb = e.getBoundingBox().inflate(0.3);
                java.util.Optional<net.minecraft.world.phys.Vec3> hit = bb.clip(eye, eye.add(look.scale(range)));
                if (hit.isPresent()) {
                    double d = eye.distanceToSqr(hit.get());
                    if (d < bestDist) {
                        bestDist = d;
                        target = (net.minecraft.world.entity.LivingEntity) e;
                    }
                }
            }
            if (target == null) return;

            // 无论生物是否空手，都在玩家与被瞄准生物之间的连线上渲染附魔粒子
            long gt = mc.level.getGameTime();
            net.minecraft.world.phys.Vec3 start = ModMain.applyLineEmitterOffset(player, eye);
            net.minecraft.world.phys.Vec3 end = target.getBoundingBox().getCenter();
            int steps = 6;
            for (int i = 0; i < steps; i++) {
                double t = (i + mc.level.random.nextDouble()) / steps;
                net.minecraft.world.phys.Vec3 p = start.lerp(end, t)
                    .add(0, 0.15 * Math.sin(gt * 0.3 + i), 0);
                mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    p.x, p.y, p.z, 0, 0, 0);
            }
        }

        /** 命令方块权杖：沿准星视线探测被瞄准的生物。
         * 击杀模式(1)不限距离（渲染范围内即可被选中）；抓取(2)/启用AI(3)模式限制 30 格以内。
         * 无(0)/护盾(4)模式不响应左键，无需瞄准。返回被瞄准的最近生物实体（排除玩家自身）。 */
        private static net.minecraft.world.entity.Entity pickAimedEntity(net.minecraft.client.Minecraft mc) {
            net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition(1.0F);
            net.minecraft.world.phys.Vec3 look = mc.player.getLookAngle();
            double range = StaffClientState.commandStaffMode == 1 ? 128.0 : 30.0;
            net.minecraft.world.phys.AABB searchBox = mc.player.getBoundingBox()
                .expandTowards(look.scale(range)).inflate(1.0);
            net.minecraft.world.entity.Entity target = null;
            double bestDist = range * range;
            for (net.minecraft.world.entity.Entity e : mc.level.getEntities(mc.player, searchBox,
                e -> e instanceof net.minecraft.world.entity.LivingEntity && e != mc.player)) {
                net.minecraft.world.phys.AABB bb = e.getBoundingBox().inflate(0.3);
                java.util.Optional<net.minecraft.world.phys.Vec3> hit = bb.clip(eye, eye.add(look.scale(range)));
                if (hit.isPresent()) {
                    double d = eye.distanceToSqr(hit.get());
                    if (d < bestDist) {
                        bestDist = d;
                        target = e;
                    }
                }
            }
            return target;
        }

        private static boolean wasSwapPressed = false;
        private static boolean wasPortalUsePressed = false;
        private static boolean wasPortalFlipPressed = false;
        private static boolean wasAltPressed = false;
        // 屏障权杖：左右键同时按下触发整体平移的发送冷却
        private static int barrierShiftCooldown = 0;
        // 屏障权杖：导出位移键的边沿检测状态
        private static boolean wasOffsetExportPressed = false;
        // 端点汇聚偏移每次按键的步长（世界坐标块）
        private static final float CONVERGE_STEP = 0.1F;
        // 附魔台权杖：攻击键（左键）按下状态，用于边沿检测切换模式
        private static boolean wasEnchantAttackPressed = false;
        // TNT 权杖：攻击键（左键）按下状态，用于边沿检测触发即时引爆
        private static boolean wasTntAttackPressed = false;
        // 蜘蛛网权杖：攻击键（左键）按下状态，用于边沿检测触发蛛丝断开
        private static boolean wasCobwebAttackPressed = false;
        // 铁链权杖：攻击键（左键）按下状态，用于边沿检测触发铁链断开（甩出目标）
        private static boolean wasChainAttackPressed = false;
        // 命令方块权杖：攻击键（左键）按下状态，用于边沿检测触发能力动作
        private static boolean wasCommandStaffAttackPressed = false;
        // 命令方块权杖：是否正手持的边沿检测（刚拿起时显示一次当前模式）
        private static boolean wasCommandStaffHeld = false;
        // 客户端本地维护的传送门预览状态（逐帧渲染幽灵，避免服务端20Hz同步造成步进式跳动）
        private static double portalDist = 2.0;
        private static boolean portalAimExit = false;
        private static boolean portalFlipNow = false;
        // 红石块权杖：客户端本地维护的射线充能强度（1~8，默认 5），滚轮调整时同步给服务端
        private static int redstoneStaffCharge = 5;

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Pre event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            if (StaffClientState.furnaceOnTicks > 0) {
                StaffClientState.furnaceOnTicks--;
            }
            if (StaffClientState.omegaDismantleForbiddenTicks > 0) {
                StaffClientState.omegaDismantleForbiddenTicks--;
            }

            updateEnchantStaffParticles(mc);

            boolean holdingKnifeMain = mc.player.getMainHandItem().getItem() == ModItems.GLISTERING_MELON_KNIFE.get();
            boolean holdingKnifeOff = mc.player.getOffhandItem().getItem() == ModItems.GLISTERING_MELON_KNIFE.get();
            boolean holdingStaffMain = mc.player.getMainHandItem().getItem() instanceof StaffItem;
            boolean holdingStaffOff = mc.player.getOffhandItem().getItem() instanceof StaffItem;

            boolean blocking = BLOCK_KEY.isDown() && (holdingKnifeMain || holdingKnifeOff || holdingStaffMain || holdingStaffOff);

            if (blocking != wasBlocking) {
                wasBlocking = blocking;
                isClientBlocking = blocking;
                PacketDistributor.sendToServer(new BlockingStatePayload(blocking));
            }

            boolean holdingStaff = holdingStaffMain || holdingStaffOff;

            // 主手与副手都持有 minecraft game icon 时，中键触发 omega game icon 合成
            boolean holdingGameIconMain = mc.player.getMainHandItem().getItem() == ModItems.GAME_ICON.get();
            boolean holdingGameIconOff = mc.player.getOffhandItem().getItem() == ModItems.GAME_ICON.get();
            boolean gameIconCraft = holdingGameIconMain && holdingGameIconOff;

            if (holdingStaff || gameIconCraft) {
                boolean swapPressed = STAFF_SWAP_BLOCKTYPE.isDown();
                if (swapPressed && !wasSwapPressed) {
                    if (holdingStaff) {
                        PacketDistributor.sendToServer(new StaffBlockTypePayload());
                    } else {
                        PacketDistributor.sendToServer(new GameIconCraftPayload());
                    }
                }
                wasSwapPressed = swapPressed;
            } else {
                wasSwapPressed = false;
            }

            // 端点汇聚目标偏移：X/C/V/B/N/M 调整相对玩家头部的 X/Y/Z 偏移，P 键导出。
            // 该偏移仅用于客户端调试线段汇聚方向，调整后按 P 导出数值交作者固化。
            if (STAFF_OFFSET_X_NEG.isDown()) {
                DebugStringRenderer.convergeOffsetX -= CONVERGE_STEP;
            }
            if (STAFF_OFFSET_X_POS.isDown()) {
                DebugStringRenderer.convergeOffsetX += CONVERGE_STEP;
            }
            if (STAFF_OFFSET_Y_NEG.isDown()) {
                DebugStringRenderer.convergeOffsetY -= CONVERGE_STEP;
            }
            if (STAFF_OFFSET_Y_POS.isDown()) {
                DebugStringRenderer.convergeOffsetY += CONVERGE_STEP;
            }
            if (STAFF_OFFSET_Z_NEG.isDown()) {
                DebugStringRenderer.convergeOffsetZ -= CONVERGE_STEP;
            }
            if (STAFF_OFFSET_Z_POS.isDown()) {
                DebugStringRenderer.convergeOffsetZ += CONVERGE_STEP;
            }

            // 导出当前汇聚偏移到 JSON 文件（边沿触发，避免每帧重复写入）
            boolean exportPressed = STAFF_OFFSET_EXPORT.isDown();
            if (exportPressed && !wasOffsetExportPressed) {
                exportConvergeOffset(mc);
            }
            wasOffsetExportPressed = exportPressed;

            // 屏障权杖：按住 R 键整体平移屏障群（带发送冷却，按住可重复平移）
            boolean shiftCombo = BarrierStaffHelper.isHoldingBarrierStaff() && STAFF_BARRIER_SHIFT.isDown();
            if (shiftCombo) {
                if (barrierShiftCooldown <= 0) {
                    PacketDistributor.sendToServer(new cn.autoforged.joes_addons_for_abmc.network.BarrierShiftPayload());
                    barrierShiftCooldown = 10;
                } else {
                    barrierShiftCooldown--;
                }
            } else {
                barrierShiftCooldown = 0;
            }

            // 附魔台权杖：持有权杖时按攻击键（左键）切换模式
            boolean holdingEnchantStaff = isHoldingEnchantStaff();
            boolean enchantAttackPressed = holdingEnchantStaff && mc.options.keyAttack.isDown();
            if (enchantAttackPressed && !wasEnchantAttackPressed) {
                PacketDistributor.sendToServer(new EnchantStaffModeTogglePayload());
            }
            wasEnchantAttackPressed = enchantAttackPressed;

            // TNT 权杖：持有权杖时按下攻击键（左键），立即引爆该权杖丢出的 TNT/苦力怕
            boolean holdingTntStaff = isHoldingTntStaff();
            boolean tntAttackPressed = holdingTntStaff && mc.options.keyAttack.isDown();
            if (tntAttackPressed && !wasTntAttackPressed) {
                PacketDistributor.sendToServer(new TntDetonatePayload());
            }
            wasTntAttackPressed = tntAttackPressed;

            // 蜘蛛网权杖：持有权杖时按下攻击键（左键），主动断开当前拉扯的蛛丝
            boolean holdingCobwebStaff = isHoldingCobwebStaff();
            boolean cobwebAttackPressed = holdingCobwebStaff && mc.options.keyAttack.isDown();
            if (cobwebAttackPressed && !wasCobwebAttackPressed) {
                PacketDistributor.sendToServer(new CobwebDisconnectPayload());
            }
            wasCobwebAttackPressed = cobwebAttackPressed;

            // 铁链权杖：持有权杖时按下攻击键（左键），主动断开当前拉扯的铁链（目标按当前速度甩出）
            boolean holdingChainStaff = isHoldingIronChainStaff();
            boolean chainAttackPressed = holdingChainStaff && mc.options.keyAttack.isDown();
            if (chainAttackPressed && !wasChainAttackPressed) {
                PacketDistributor.sendToServer(
                    new cn.autoforged.joes_addons_for_abmc.network.ChainCancelPayload());
            }
            wasChainAttackPressed = chainAttackPressed;

            // 命令方块权杖：持有权杖时按下攻击键（左键），按当前能力对准被瞄准的生物执行动作。
            // 仅击杀(1)/抓取(2)/启用AI(3)模式响应左键；无(0)模式左键无任何行为，护盾(4)为持续生效模式。
            boolean holdingCommandStaff = isHoldingCommandStaff();
            // 拿到 / 手持命令方块权杖时，顺便在屏幕中下方显示一次当前模式
            if (holdingCommandStaff && !wasCommandStaffHeld) {
                StaffClientState.commandStaffModeFlashTicks = 60;
            }
            wasCommandStaffHeld = holdingCommandStaff;

            int commandStaffModeLocal = StaffClientState.commandStaffMode;
            boolean commandStaffAttackPressed = holdingCommandStaff && mc.options.keyAttack.isDown();
            if (commandStaffAttackPressed && !wasCommandStaffAttackPressed
                    && (commandStaffModeLocal == 1 || commandStaffModeLocal == 2 || commandStaffModeLocal == 3)) {
                net.minecraft.world.entity.Entity aimTarget = pickAimedEntity(mc);
                if (aimTarget != null) {
                    PacketDistributor.sendToServer(
                        new cn.autoforged.joes_addons_for_abmc.network.CommandStaffTargetPayload(
                            aimTarget.getStringUUID(), commandStaffModeLocal));
                }
            }
            wasCommandStaffAttackPressed = commandStaffAttackPressed;
            if (StaffClientState.commandStaffModeFlashTicks > 0) {
                StaffClientState.commandStaffModeFlashTicks--;
            }

            boolean holdingPortalStaff = false;
            if (holdingStaff) {
                ItemStack held = mc.player.getMainHandItem();
                if (held.getItem() instanceof StaffItem) {
                    String bt = held.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
                    if ("end_portal_frame".equals(bt)) {
                        holdingPortalStaff = true;
                    }
                }
                if (!holdingPortalStaff) {
                    held = mc.player.getOffhandItem();
                    if (held.getItem() instanceof StaffItem) {
                        String bt = held.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
                        if ("end_portal_frame".equals(bt)) {
                            holdingPortalStaff = true;
                        }
                    }
                }
            }

            if (holdingPortalStaff) {
                // 按住右键 = 瞄准并预览幽灵；松开 = 放置
                boolean useDown = mc.options.keyUse.isDown();
                if (useDown && !wasPortalUsePressed) {
                    portalDist = 3.0;
                    portalFlipNow = false;
                } else if (!useDown && wasPortalUsePressed) {
                    Vec3 ghostPos = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(portalDist))
                        .subtract(0.0, 0.5, 0.0);
                    float gy = mc.player.getYRot();
                    float gp = mc.player.getXRot();
                    PacketDistributor.sendToServer(new cn.autoforged.joes_addons_for_abmc.network.PortalPlacePayload(
                        portalAimExit, ghostPos.x, ghostPos.y, ghostPos.z, gy, gp, portalFlipNow));
                    if (!portalAimExit) {
                        portalAimExit = true;
                        portalFlipNow = false;
                    } else {
                        portalAimExit = false;
                        portalFlipNow = false;
                    }
                }
                wasPortalUsePressed = useDown;

                // 左键：正在创建传送门（存在未匹配实体或正在预览瞄准）→ 反转朝向；
                //       否则（没有未匹配的传送门实体）→ 执行塌缩，让最新一对传送门相互收敛
                boolean flipDown = mc.options.keyAttack.isDown();
                if (flipDown && !wasPortalFlipPressed) {
                    if (portalAimExit || useDown) {
                        portalFlipNow = !portalFlipNow;
                    } else {
                        PacketDistributor.sendToServer(new PortalStaffInputPayload(PortalStaffInputPayload.ACTION_COLLAPSE));
                    }
                }
                wasPortalFlipPressed = flipDown;

                // ALT = 取消本次放置：重置本地状态并通知服务端移除已放置的入口传送门
                boolean altDown = PORTAL_CANCEL_KEY.isDown();
                if (altDown && !wasAltPressed) {
                    portalAimExit = false;
                    portalFlipNow = false;
                    PacketDistributor.sendToServer(new PortalStaffInputPayload(PortalStaffInputPayload.ACTION_CANCEL));
                }
                wasAltPressed = altDown;
            } else {
                wasPortalUsePressed = false;
                wasPortalFlipPressed = false;
                wasAltPressed = false;
                portalAimExit = false;
                portalFlipNow = false;
            }

        }

        /** 客户端刻后处理：驱动红石激光音效状态机。
         *  必须用 Post 而非 Pre —— Pre 在 Minecraft.runTick 开头先于 startUseItem 运行，
         *  会在“右击女仆”的交互拦截（EntityInteractSpecific → RedstoneLaserSounds.stopAndReset）
         *  生效之前就抢先播放 laser_start；Post 在交互事件处理完后运行，此时 stopAndReset
         *  已置起抑制标记，右击女仆将完全静默（不再播放任何激光音效）。 */
        @SubscribeEvent
        public static void onClientTickPost(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            // 变形平滑跟随必须在 Post 执行：Post 在本 tick 实体 tick 完成、服务端位置包处理之后、
            // 渲染之前触发，此时设置实体位置并保留 xo/yo/zo，渲染器才能做 xo→x 插值（/tp 同款平滑动画）。
            cn.autoforged.joes_addons_for_abmc.client.TransmutationCameraClient.tickFollow(mc);
            // 红石块权杖：激光音效（laser_start → 循环 laser_middle → laser_end）状态机
            RedstoneLaserSounds.tick(isHoldingRedstoneStaff(), mc.options.keyUse.isDown());
            // 女仆红石块权杖：清理已消失女仆的激光循环音效
            MaidRedstoneLaserSounds.tick();
            // 霜冰：客户端实体是独立副本，会自行随头部转向重算身体朝向（服务端已冻结但传不回渲染端）。
            // 这里对可见范围内被霜冰冻住的客户端实体钉住身体 yaw，头部仍可扭动。
            net.minecraft.world.phys.AABB frostBox = mc.player.getBoundingBox().inflate(64.0);
                for (net.minecraft.world.entity.Entity e : mc.level.getEntities(mc.player, frostBox,
                        ent -> ent instanceof net.minecraft.world.entity.LivingEntity)) {
                    net.minecraft.world.entity.LivingEntity living = (net.minecraft.world.entity.LivingEntity) e;
                    if (cn.autoforged.joes_addons_for_abmc.ModMain.isOverlappingFrost(living)) {
                        cn.autoforged.joes_addons_for_abmc.ModMain.freezeClientBodyRotation(living);
                    } else {
                        cn.autoforged.joes_addons_for_abmc.ModMain.clearClientFrostRotation(living.getId());
                    }
                }
            // 蜘蛛网权杖：持有者在蜘蛛网中不会被减速。客户端本地模拟同样清除 stuck 倍率，
            // 否则客户端仍按减速模拟、服务端按正常速度移动，会造成来回拉扯。
            if (isHoldingCobwebStaff()) {
                cn.autoforged.joes_addons_for_abmc.ModMain.clearStuckInCobweb(mc.player);
            }
        }

        // 滚轮：控制预览传送门的远近（可越过玩家放到自己身后）；红石块权杖发射射线时调节充能强度
        @SubscribeEvent
        public static void onMouseScroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (mc.screen != null) return;

            ItemStack main = mc.player.getMainHandItem();
            ItemStack off = mc.player.getOffhandItem();
            boolean holdingPortal = (main.getItem() instanceof StaffItem
                && "end_portal_frame".equals(main.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty")))
                || (off.getItem() instanceof StaffItem
                && "end_portal_frame".equals(off.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty")));
            boolean holdingRedstone = (main.getItem() instanceof StaffItem
                && "redstone_block".equals(main.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty")))
                || (off.getItem() instanceof StaffItem
                && "redstone_block".equals(off.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty")));

            // Him 权杖：按住左Alt 滚动滚轮切换「近战模式 / 远程模式」
            if (isHoldingHerobrineStaff() && PORTAL_CANCEL_KEY.isDown()) {
                PacketDistributor.sendToServer(new HerobrineStaffModeTogglePayload());
                event.setCanceled(true);
                return;
            }

            // 命令方块权杖：按住左Alt 滚动滚轮切换「无 / 击杀 / 抓取 / 启用禁用AI / 护盾」，
            // 向上滚动切换到前一个模式、向下滚动切换到后一个模式（参照红石块权杖充能）。
            if (isHoldingCommandStaff() && PORTAL_CANCEL_KEY.isDown()) {
                double scrollDelta = event.getScrollDeltaY();
                int modeDir = scrollDelta > 0 ? -1 : (scrollDelta < 0 ? 1 : 0);
                if (modeDir != 0) {
                    PacketDistributor.sendToServer(
                        new cn.autoforged.joes_addons_for_abmc.network.CommandStaffModeTogglePayload(modeDir));
                    event.setCanceled(true);
                }
                return;
            }

            if (!holdingPortal && !holdingRedstone) return;

            // 仅在按住右键（发射射线/放置传送门）时拦截滚轮，其它情况保持默认（切换快捷栏）
            if (!mc.options.keyUse.isDown()) return;

            double delta = event.getScrollDeltaY();
            if (holdingRedstone) {
                // 调整红石射线充能强度（1~8），变化时同步给服务端
                int dir = delta > 0 ? 1 : (delta < 0 ? -1 : 0);
                if (dir != 0) {
                    redstoneStaffCharge = Math.max(1, Math.min(8, redstoneStaffCharge + dir));
                    PacketDistributor.sendToServer(
                        new cn.autoforged.joes_addons_for_abmc.network.RedstoneStaffChargePayload(redstoneStaffCharge));
                }
            } else {
                // 客户端本地平滑累加距离，逐帧渲染，无需发送给服务端
                portalDist = Math.min(256.0, Math.max(-10.0, portalDist + delta * 0.35));
            }
            // 返回 true 取消默认行为（避免切换快捷栏物品）
            event.setCanceled(true);
        }

        // 将当前端点汇聚偏移（相对玩家头部）导出到 JSON 文件，并在聊天栏提示路径与数值，
        // 便于玩家把调整后的数值反馈给作者，用于在 mod 源码中永久固化。
        private static void exportConvergeOffset(Minecraft mc) {
            float x = DebugStringRenderer.convergeOffsetX;
            float y = DebugStringRenderer.convergeOffsetY;
            float z = DebugStringRenderer.convergeOffsetZ;
            String json = "{\n"
                + "  \"convergeOffsetX\": " + formatFloat(x) + ",\n"
                + "  \"convergeOffsetY\": " + formatFloat(y) + ",\n"
                + "  \"convergeOffsetZ\": " + formatFloat(z) + "\n"
                + "}\n";
            try {
                Path dir = mc.gameDirectory.toPath().resolve("joes_addons_for_abmc");
                Files.createDirectories(dir);
                Path file = dir.resolve("converge_offset.json");
                Files.writeString(file, json, StandardCharsets.UTF_8);
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                            "§a[§b汇聚偏移§a] 已导出 §7X=" + formatFloat(x) + " Y=" + formatFloat(y) + " Z=" + formatFloat(z)
                                + "§a → §f" + file.toAbsolutePath()),
                        false);
                }
            } catch (Exception e) {
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§c[汇聚偏移] 导出失败: " + e.getMessage()), false);
                }
            }
        }

        // 去掉浮点尾数，输出简洁小数（如 5.0/3.0/-2.0）
        private static String formatFloat(float v) {
            if (v == Math.rint(v) && !Float.isInfinite(v)) {
                return String.valueOf((long) v);
            }
            return Float.toString(v);
        }

    }

    // 逐帧渲染传送门“幽灵”预览：直接使用本帧的视线，平滑且不抖动
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // 调试线段：/jafa debug_string 触发，渲染 whitedot + string
        cn.autoforged.joes_addons_for_abmc.client.DebugStringRenderer.render(event);

        // 蛛丝线段：持续在“玩家→锚点”之间渲染（到达目的地/手动断开/发射新蛛丝前不会消失，
        // 起始点跟随玩家，且相对于玩家第一视角稍向右、上偏移）
        renderCobwebBeam(mc, event);

        // 铁链线段（铁块权杖）：钩取中渲染“玩家发射点→目标”，未命中时播放发射收回动画
        renderChainBeam(mc, event);

        if (!mc.options.keyUse.isDown()) return; // 仅按住右键瞄准时显示
        if (!isHoldingEndPortalStaffClient(mc.player)) return;

        Vec3 pos = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(ClientTickHandler.portalDist))
            .subtract(0.0, 0.5, 0.0);
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        Vec3 cam = event.getCamera().getPosition();
        ps.translate(pos.x - cam.x, pos.y + 0.75 - cam.y, pos.z - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.translate(-1.0, -1.0, 0.0);
        ps.scale(2.0F, 2.0F, 1.0F);

        VertexConsumer consumer = mc.renderBuffers().bufferSource()
            .getBuffer(RenderType.entityTranslucent(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/end_portal.png")));
        Matrix4f matrix = ps.last().pose();
        consumer.addVertex(matrix, 0, 0, 0).setColor(255, 255, 255, 180)
            .setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(15728880).setNormal(0, 0, 1);
        consumer.addVertex(matrix, 0, 1, 0).setColor(255, 255, 255, 180)
            .setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(15728880).setNormal(0, 0, 1);
        consumer.addVertex(matrix, 1, 1, 0).setColor(255, 255, 255, 180)
            .setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(15728880).setNormal(0, 0, 1);
        consumer.addVertex(matrix, 1, 0, 0).setColor(255, 255, 255, 180)
            .setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(15728880).setNormal(0, 0, 1);

        ps.popPose();
    }

    private static boolean isHoldingEndPortalStaffClient(net.minecraft.world.entity.player.Player player) {
        net.minecraft.world.item.ItemStack[] hands = {
            player.getMainHandItem(), player.getOffhandItem() };
        for (net.minecraft.world.item.ItemStack stack : hands) {
            if (stack.getItem() instanceof StaffItem) {
                String bt = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
                if ("end_portal_frame".equals(bt)) return true;
            }
        }
        return false;
    }

    /** 守卫者激光光束纹理（原版守护者攻击光束，白色发光）。该纹理为 32×32、
     *  横向双帧：左半与右半各是一条光束（原版交替闪烁实现“变形”动画），渲染时只取其一。 */
    private static final ResourceLocation GUARDIAN_BEAM_TEX =
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/guardian_beam.png");

    // 蛛丝起始点低通平滑滤波器：随玩家移动/转头逐帧向目标点收敛，降低丝带起始端的帧间抖动。
    private static boolean beamSmoothReady;
    private static Vec3 beamSmoothAnchor;
    private static double beamSmoothX, beamSmoothY, beamSmoothZ;

    /** 持续渲染“玩家→锚点”的蛛丝线段：起始跟随玩家，第一视角相对稍向右、上偏移。
     *  采用与 DebugStringRenderer 相同的 immediate 缓冲 + endBatch 模式（该模式经实测稳定），
     *  渲染为一条固定竖直厚度方向的丝带，因线段长度随玩家与锚点距离实时变化而自然“拉长”。
     *  起始点做低通平滑处理，抵消玩家移动/转头带来的帧间抖动，使丝带更丝滑。 */
    private static void renderCobwebBeam(Minecraft mc, RenderLevelStageEvent event) {
        Vec3 anchor = cn.autoforged.joes_addons_for_abmc.client.CobwebBeamClient.getAnchor();
        if (anchor == null || mc.player == null || mc.level == null) return;

        Vec3 eye = mc.player.getEyePosition();
        // 发射点 = 玩家眼位 + 配置的线/汇聚点偏移（第一人称随头部转动，其余随身体转动）
        Vec3 inst = ModMain.applyLineEmitterOffset(mc.player, eye);

        // 低通平滑起始点：消除头摇/转身引起的帧间抖动。新锚点出现时首帧直接贴合，避免跳变。
        // k 取较大值：优先保证“玩家一移动，发射点立刻跟上”，避免飞行时激光慢半拍。
        // 残留的轻微平滑（贴近目标但非完全贴合）用于缓解头摇/转身造成的细小抖动。
        double k = 1.0;
        if (beamSmoothReady && beamSmoothAnchor == anchor) {
            k = 0.95;
        }
        beamSmoothX += (inst.x - beamSmoothX) * k;
        beamSmoothY += (inst.y - beamSmoothY) * k;
        beamSmoothZ += (inst.z - beamSmoothZ) * k;
        beamSmoothAnchor = anchor;
        beamSmoothReady = true;
        Vec3 start = new Vec3(beamSmoothX, beamSmoothY, beamSmoothZ);

        Vec3 cam = event.getCamera().getPosition();
        try {
            MultiBufferSource.BufferSource buf =
                MultiBufferSource.immediate(new ByteBufferBuilder(1 << 16));
            renderGuardianLaser(event.getPoseStack(), cam, start, anchor, buf);
            buf.endBatch();
        } catch (Throwable t) {
            // 单帧渲染异常不影响后续帧与主渲染管线
        }
    }

    /** 在 a、b 之间沿线段排列一串“守卫者激光”贴图：每个贴图保持原版守卫者光束贴图的
     *  正常大小（不拉伸），面向相机（billboard）渲染，每格排列一个。数量随 a→b 距离成正比
     *  增减（距离越大贴图越多），仿佛在一条线上排列了多个激光贴图。
     *  <p>guardian_beam.png 是 32×32、横向双帧纹理（左半与右半各是一条光束，原版通过
     *  U 0..0.5 / 0.5..1.0 交替闪烁实现“变形”动画）。这里 U 固定只取 0..0.5（其中一帧），
     *  避免两帧同时渲染在贴图上造成“多个贴图叠加”。 */
    private static void renderGuardianLaser(PoseStack ps, Vec3 cam, Vec3 a, Vec3 b,
                                            MultiBufferSource ms) {
        double rx = b.x - a.x, ry = b.y - a.y, rz = b.z - a.z;
        double lenSq = rx * rx + ry * ry + rz * rz;
        if (lenSq < 1.0E-8) return;
        double len = Math.sqrt(lenSq);
        Vec3 dir = new Vec3(rx / len, ry / len, rz / len);

        // 沿线方向每 0.35 格一个贴图：个数随距离增减，至少 1 个。
        // 间距小于贴图边长（0.5），使相邻贴图在激光方向上互相重叠，
        // 消除“贴图之间相隔太远”造成的视觉断开，透明区也被相邻贴图覆盖。
        double spacing = 0.35;
        int count = Math.max(1, (int) Math.floor(len / spacing));

        // billboard 朝向：正对相机的“完整 billboard”，同时用世界竖直方向对齐贴图的上方——
        // right = look × worldUp（水平横轴），up = right × look（屏幕上的竖直方向）。
        // 这样贴图始终面向玩家视角，且贴图内容（纵向光束）保持竖直、不会旋转 90°。
        Vec3 mid = new Vec3((a.x + b.x) * 0.5, (a.y + b.y) * 0.5, (a.z + b.z) * 0.5);
        Vec3 toCam = new Vec3(cam.x - mid.x, cam.y - mid.y, cam.z - mid.z);
        double tcl = toCam.length();
        Vec3 right, up;
        if (tcl < 1.0E-6) {
            right = new Vec3(1, 0, 0);
            up = new Vec3(0, 1, 0);
        } else {
            Vec3 look = toCam.scale(1.0 / tcl);
            // look × worldUp = (-look.z, 0, look.x)：水平且垂直于视线
            right = new Vec3(-look.z, 0, look.x);
            double rl = right.length();
            if (rl < 1.0E-6) {
                // 视线竖直（正上/正下看）：横轴退化，退化为固定竖直贴图
                right = new Vec3(1, 0, 0);
                up = new Vec3(0, 1, 0);
            } else {
                right = right.scale(1.0 / rl);
                up = right.cross(look);
                double ul = up.length();
                up = ul < 1.0E-6 ? new Vec3(0, 1, 0) : up.scale(1.0 / ul);
            }
        }

        // 贴图边长（正常大小的一半，不随距离拉伸）
        float size = 0.5F;
        float h = size * 0.5F;
        float hx = (float) (right.x * h), hy = (float) (right.y * h), hz = (float) (right.z * h);
        float ux = (float) (up.x * h), uy = (float) (up.y * h), uz = (float) (up.z * h);

        Matrix4f mat = ps.last().pose();
        VertexConsumer c = ms.getBuffer(RenderType.entityTranslucent(GUARDIAN_BEAM_TEX));
        int light = 15728880;
        for (int i = 0; i < count; i++) {
            // 贴图中心沿线段的位置：仅 1 个时居中，否则每格一个
            double t = count == 1 ? len / 2.0 : (i + 0.5) * spacing;
            float px = (float) (a.x + dir.x * t - cam.x);
            float py = (float) (a.y + dir.y * t - cam.y);
            float pz = (float) (a.z + dir.z * t - cam.z);
            c.addVertex(mat, px + hx + ux, py + hy + uy, pz + hz + uz)
                .setColor(255, 255, 255, 255).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
            c.addVertex(mat, px - hx + ux, py - hy + uy, pz - hz + uz)
                .setColor(255, 255, 255, 255).setUv(1, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
            c.addVertex(mat, px - hx - ux, py - hy - uy, pz - hz - uz)
                .setColor(255, 255, 255, 255).setUv(0, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
            c.addVertex(mat, px + hx - ux, py + hy - uy, pz + hz - uz)
                .setColor(255, 255, 255, 255).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        }
    }

    /** 原版铁链方块贴图（16×16，单个链环，中心镂空）。 */
    private static final ResourceLocation CHAIN_TEX =
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/chain.png");

    /** 铁链线段渲染（铁块权杖）：
     *  钩取中在“玩家发射点（配置偏移）→ 服务端同步终点”之间绘制；
     *  未命中时按三角波进度（0→1→0）播放“发射到最远点再收回”的短动画。 */
    private static void renderChainBeam(Minecraft mc, RenderLevelStageEvent event) {
        boolean active = cn.autoforged.joes_addons_for_abmc.client.ChainBeamClient.isActive();
        boolean launching = cn.autoforged.joes_addons_for_abmc.client.ChainBeamClient.isLaunching();
        if (!active && !launching) return;
        if (mc.player == null) return;

        Vec3 eye = mc.player.getEyePosition();
        // 发射点 = 玩家眼位 + 配置的线/汇聚点偏移（与蛛丝同规则）
        Vec3 start = ModMain.applyLineEmitterOffset(mc.player, eye);
        // 推进鞭子物理（未命中动画计时也在这里递减）；传入渲染帧插值系数，让链头与生物画面严格重合
        cn.autoforged.joes_addons_for_abmc.client.ChainBeamClient.tick(start,
            event.getPartialTick().getGameTimeDeltaPartialTick(false));

        Vec3 cam = event.getCamera().getPosition();
        try {
            MultiBufferSource.BufferSource buf =
                MultiBufferSource.immediate(new ByteBufferBuilder(1 << 16));
            if (active) {
                // 钩取中：沿锁链曲线节点折线渲染（弧度很小：轻微下垂 + 甩头滞后拖曳）
                renderChainPolyline(event.getPoseStack(), cam,
                    cn.autoforged.joes_addons_for_abmc.client.ChainBeamClient.getNodes(), buf);
            } else {
                // 未命中：进度 p 0→1，三角波 t 0→1→0 实现“伸出→收回”，仍为直线
                float p = 1.0F - (float) cn.autoforged.joes_addons_for_abmc.client.ChainBeamClient.getLaunchTicks()
                    / cn.autoforged.joes_addons_for_abmc.client.ChainBeamClient.LAUNCH_DURATION;
                float t = p < 0.5F ? p * 2.0F : (1.0F - p) * 2.0F;
                Vec3 end = start.add(cn.autoforged.joes_addons_for_abmc.client.ChainBeamClient.getLaunchEnd()
                    .subtract(start).scale(t));
                renderChainLine(event.getPoseStack(), cam, start, end, buf);
            }
            buf.endBatch();
        } catch (Throwable t) {
            // 单帧渲染异常不影响后续帧与主渲染管线
        }
    }

    /** 在 a、b 之间沿线段排列一串“十字”铁链链节：每个链节由两片互相垂直的 billboard
     *  平面（十字交叉）构成，面向相机，贴图为原版铁链方块材质 chain.png。每格排列一个
     *  链节，数量随 a→b 距离成正比增减，仿佛一根由铁环串成的铁链。 */
    private static void renderChainLine(PoseStack ps, Vec3 cam, Vec3 a, Vec3 b,
                                        MultiBufferSource ms) {
        double rx = b.x - a.x, ry = b.y - a.y, rz = b.z - a.z;
        double lenSq = rx * rx + ry * ry + rz * rz;
        if (lenSq < 1.0E-8) return;
        double len = Math.sqrt(lenSq);
        Vec3 dir = new Vec3(rx / len, ry / len, rz / len);

        // 沿线方向每 0.25 格一个链节：数量随距离增减，至少 1 个。
        double spacing = 0.25;
        int count = Math.max(1, (int) Math.floor(len / spacing));

        // billboard 朝向：正对相机的“完整 billboard”，并用世界竖直方向对齐贴图的上方，
        // 使链环贴图内容（纵向）保持竖直且始终面向玩家视角。
        Vec3 mid = new Vec3((a.x + b.x) * 0.5, (a.y + b.y) * 0.5, (a.z + b.z) * 0.5);
        Vec3 toCam = new Vec3(cam.x - mid.x, cam.y - mid.y, cam.z - mid.z);
        double tcl = toCam.length();
        Vec3 right, up;
        if (tcl < 1.0E-6) {
            right = new Vec3(1, 0, 0);
            up = new Vec3(0, 1, 0);
        } else {
            Vec3 look = toCam.scale(1.0 / tcl);
            right = new Vec3(-look.z, 0, look.x);
            double rl = right.length();
            if (rl < 1.0E-6) {
                right = new Vec3(1, 0, 0);
                up = new Vec3(0, 1, 0);
            } else {
                right = right.scale(1.0 / rl);
                up = right.cross(look);
                double ul = up.length();
                up = ul < 1.0E-6 ? new Vec3(0, 1, 0) : up.scale(1.0 / ul);
            }
        }

        // 链节半边长（正常大小，不随距离拉伸）
        float h = 0.3F * 0.5F;
        float hx1 = (float) (right.x * h), hy1 = (float) (right.y * h), hz1 = (float) (right.z * h);
        float ux1 = (float) (up.x * h), uy1 = (float) (up.y * h), uz1 = (float) (up.z * h);
        // 十字第二片：绕视线方向旋转 90°（right2=up，up2=-right），与第一片垂直。
        float hx2 = ux1, hy2 = uy1, hz2 = uz1;
        float ux2 = -hx1, uy2 = -hy1, uz2 = -hz1;

        Matrix4f mat = ps.last().pose();
        VertexConsumer c = ms.getBuffer(RenderType.entityTranslucent(CHAIN_TEX));
        int light = 15728880;
        for (int i = 0; i < count; i++) {
            // 链节中心沿线段的位置：仅 1 个时居中，否则每格一个
            double t = count == 1 ? len / 2.0 : (i + 0.5) * spacing;
            float px = (float) (a.x + dir.x * t - cam.x);
            float py = (float) (a.y + dir.y * t - cam.y);
            float pz = (float) (a.z + dir.z * t - cam.z);
            addChainQuad(c, mat, px, py, pz, hx1, hy1, hz1, ux1, uy1, uz1, light);
            addChainQuad(c, mat, px, py, pz, hx2, hy2, hz2, ux2, uy2, uz2, light);
        }
    }

    /** 沿“鞭子”链节点折线渲染铁链链节：把折线按弧长均匀铺满“十字”链节（与直线版同款
     *  billboard），数量随折线总长成正比增减，从而把弯曲的鞭子链画出来。 */
    private static void renderChainPolyline(PoseStack ps, Vec3 cam, Vec3[] pts,
                                            MultiBufferSource ms) {
        if (pts == null || pts.length < 2) return;
        // 折线总长
        double total = 0.0;
        for (int i = 0; i < pts.length - 1; i++) {
            total += pts[i].distanceTo(pts[i + 1]);
        }
        if (total < 1.0E-6) return;

        // 链节间距（与直线版一致）：数量随总长增减
        double spacing = 0.25;
        int count = Math.max(1, (int) Math.floor(total / spacing));

        // billboard 朝向：正对相机的“完整 billboard”，以折线中点为参考，
        // 用世界竖直方向对齐贴图上方，使链环贴图保持竖直。
        Vec3 mid = pts[0].add(pts[pts.length - 1]).scale(0.5);
        Vec3 toCam = new Vec3(cam.x - mid.x, cam.y - mid.y, cam.z - mid.z);
        double tcl = toCam.length();
        Vec3 right, up;
        if (tcl < 1.0E-6) {
            right = new Vec3(1, 0, 0);
            up = new Vec3(0, 1, 0);
        } else {
            Vec3 look = toCam.scale(1.0 / tcl);
            right = new Vec3(-look.z, 0, look.x);
            double rl = right.length();
            if (rl < 1.0E-6) {
                right = new Vec3(1, 0, 0);
                up = new Vec3(0, 1, 0);
            } else {
                right = right.scale(1.0 / rl);
                up = right.cross(look);
                double ul = up.length();
                up = ul < 1.0E-6 ? new Vec3(0, 1, 0) : up.scale(1.0 / ul);
            }
        }

        // 链节半边长（正常大小，不随距离拉伸）
        float h = 0.3F * 0.5F;
        float hx1 = (float) (right.x * h), hy1 = (float) (right.y * h), hz1 = (float) (right.z * h);
        float ux1 = (float) (up.x * h), uy1 = (float) (up.y * h), uz1 = (float) (up.z * h);
        // 十字第二片：绕视线方向旋转 90°（right2=up，up2=-right），与第一片垂直。
        float hx2 = ux1, hy2 = uy1, hz2 = uz1;
        float ux2 = -hx1, uy2 = -hy1, uz2 = -hz1;

        Matrix4f mat = ps.last().pose();
        VertexConsumer c = ms.getBuffer(RenderType.entityTranslucent(CHAIN_TEX));
        int light = 15728880;

        // 沿折线按弧长均匀放置链节（游标步进，跨线段线性插值）
        double step = total / count;
        int segIdx = 0;
        double segStart = 0.0;
        for (int k = 0; k < count; k++) {
            double target = step * (k + 0.5);
            while (segIdx < pts.length - 2) {
                double segLen = pts[segIdx].distanceTo(pts[segIdx + 1]);
                if (segStart + segLen >= target) break;
                segStart += segLen;
                segIdx++;
            }
            double segLen = pts[segIdx].distanceTo(pts[segIdx + 1]);
            double t = segLen < 1.0E-8 ? 0.0 : Math.min(1.0, (target - segStart) / segLen);
            Vec3 p = pts[segIdx].lerp(pts[segIdx + 1], t);
            float px = (float) (p.x - cam.x);
            float py = (float) (p.y - cam.y);
            float pz = (float) (p.z - cam.z);
            addChainQuad(c, mat, px, py, pz, hx1, hy1, hz1, ux1, uy1, uz1, light);
            addChainQuad(c, mat, px, py, pz, hx2, hy2, hz2, ux2, uy2, uz2, light);
        }
    }

    /** 绘制一片面向相机的四边形（铁链链节的一片平面），UV 覆盖整张 chain.png。 */
    private static void addChainQuad(VertexConsumer c, Matrix4f mat,
                                     float px, float py, float pz,
                                     float hx, float hy, float hz,
                                     float ux, float uy, float uz, int light) {
        // 顶角 (+R+U)、v0；再按顺时针依次 v1(-R+U)、v2(-R-U)、v3(+R-U)。
        // UV 覆盖整张 chain.png；以下坐标将贴图再多转 90°（相对上一次调整）。
        c.addVertex(mat, px + hx + ux, py + hy + uy, pz + hz + uz)
            .setColor(255, 255, 255, 255).setUv(0, 1)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        c.addVertex(mat, px - hx + ux, py - hy + uy, pz - hz + uz)
            .setColor(255, 255, 255, 255).setUv(1, 1)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        c.addVertex(mat, px - hx - ux, py - hy - uy, pz - hz - uz)
            .setColor(255, 255, 255, 255).setUv(1, 0)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        c.addVertex(mat, px + hx - ux, py + hy - uy, pz + hz - uz)
            .setColor(255, 255, 255, 255).setUv(0, 0)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
    }
}
