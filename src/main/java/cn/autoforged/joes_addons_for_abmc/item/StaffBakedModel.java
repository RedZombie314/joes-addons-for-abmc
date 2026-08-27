package cn.autoforged.joes_addons_for_abmc.item;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

public class StaffBakedModel extends BakedModelWrapper<BakedModel> {

    private final BakedModel goldModel;
    private final BakedModel netheriteModel;
    private final BakedModel diamondModel;
    private final BakedModel bedrockModel;
    private final BakedModel obsidianModel;
    private final BakedModel boneModel;
    private final BakedModel furnaceModel;
    private final BakedModel furnaceOnModel;
    private final BakedModel bellModel;
    private final BakedModel anvilModel;
    private final BakedModel lapisModel;
    private final BakedModel magmaModel;
    private final BakedModel omegaModel;
    private final BakedModel commandModel;
    private final BakedModel endPortalModel;
    private final BakedModel enchantModel;
    private final BakedModel playerHeadModel;
    private final BakedModel herobrineModel;
    private final BakedModel barrierModel;
    private final BakedModel dripstoneModel;
    private final BakedModel cauldronModel;
    private final BakedModel craftingTableModel;
    private final BakedModel emeraldModel;
    private final BakedModel iceModel;
    private final BakedModel ironModel;
    private final BakedModel netherrackModel;
    private final BakedModel noteblockModel;
    private final BakedModel oakModel;
    private final BakedModel pistonModel;
    private final BakedModel redMushroomModel;
    private final BakedModel redstoneModel;
    private final BakedModel snowModel;
    private final BakedModel beeNestModel;
    private final BakedModel amethystModel;
    private final BakedModel cobwebModel;
    private final BakedModel spawnerModel;
    private final BakedModel tntModel;
    private final BakedModel mcModel;
    // 被“无效化”的权杖：渲染空权杖 + 蛛网覆盖层（自定义渲染器）
    private final BakedModel cobwebNullifiedModel;

    // 记录最近一次被 resolve 的持有实体 id（无持有者则为 -1），供自定义渲染器判断是否“无效化”。
    // 注意：renderByItem 内部会用 mc.player 再次解析模型覆盖该值，因此渲染器须在最前面读取。
    private static final ThreadLocal<Integer> CURRENT_HOLDER = new ThreadLocal<>();

    /** 读取最近一次 ItemOverrides.resolve 传入的持有实体 id（无持有者为 -1）。 */
    public static int getCurrentHolderId() {
        Integer v = CURRENT_HOLDER.get();
        return v == null ? -1 : v;
    }

    public StaffBakedModel(BakedModel defaultModel, BakedModel goldModel, BakedModel netheriteModel,
                           BakedModel diamondModel, BakedModel bedrockModel, BakedModel obsidianModel,
                           BakedModel boneModel, BakedModel furnaceModel, BakedModel furnaceOnModel,
                           BakedModel bellModel, BakedModel anvilModel, BakedModel lapisModel,
                           BakedModel magmaModel, BakedModel omegaModel, BakedModel commandModel,
                           BakedModel endPortalModel, BakedModel enchantModel, BakedModel playerHeadModel,
                           BakedModel herobrineModel, BakedModel barrierModel, BakedModel dripstoneModel,
                           BakedModel cauldronModel, BakedModel craftingTableModel, BakedModel emeraldModel,
                           BakedModel iceModel, BakedModel ironModel, BakedModel netherrackModel,
                           BakedModel noteblockModel, BakedModel oakModel, BakedModel pistonModel,
                           BakedModel redMushroomModel, BakedModel redstoneModel, BakedModel snowModel,
                           BakedModel beeNestModel, BakedModel amethystModel, BakedModel cobwebModel,
                           BakedModel spawnerModel, BakedModel tntModel, BakedModel mcModel) {
        super(new RotationDelegate(defaultModel));
        this.goldModel = new RotationDelegate(goldModel);
        this.netheriteModel = new RotationDelegate(netheriteModel);
        this.diamondModel = new RotationDelegate(diamondModel);
        this.bedrockModel = new RotationDelegate(bedrockModel);
        this.obsidianModel = new RotationDelegate(obsidianModel);
        this.boneModel = new RotationDelegate(boneModel);
        this.furnaceModel = new RotationDelegate(furnaceModel);
        this.furnaceOnModel = new RotationDelegate(furnaceOnModel);
        this.bellModel = new RotationDelegate(bellModel);
        this.anvilModel = new RotationDelegate(anvilModel);
        this.lapisModel = new RotationDelegate(lapisModel);
        this.magmaModel = new RotationDelegate(magmaModel);
        this.omegaModel = new RotationDelegate(omegaModel);
        this.commandModel = new RotationDelegate(commandModel);
        this.endPortalModel = new RotationDelegate(endPortalModel);
        this.enchantModel = new RotationDelegate(enchantModel);
        this.playerHeadModel = new RotationDelegate(playerHeadModel);
        this.herobrineModel = new RotationDelegate(herobrineModel);
        // 屏障权杖使用自定义渲染器（BEWLR），以便呈现始终面向玩家的屏障粒子
        this.barrierModel = new BarrierDelegate(barrierModel);
        this.dripstoneModel = new RotationDelegate(dripstoneModel);
        this.cauldronModel = new RotationDelegate(cauldronModel);
        this.craftingTableModel = new RotationDelegate(craftingTableModel);
        this.emeraldModel = new RotationDelegate(emeraldModel);
        this.iceModel = new RotationDelegate(iceModel);
        this.ironModel = new RotationDelegate(ironModel);
        this.netherrackModel = new RotationDelegate(netherrackModel);
        this.noteblockModel = new RotationDelegate(noteblockModel);
        this.oakModel = new RotationDelegate(oakModel);
        this.pistonModel = new RotationDelegate(pistonModel);
        this.redMushroomModel = new RotationDelegate(redMushroomModel);
        this.redstoneModel = new RotationDelegate(redstoneModel);
        this.snowModel = new RotationDelegate(snowModel);
        this.beeNestModel = new RotationDelegate(beeNestModel);
        this.amethystModel = new RotationDelegate(amethystModel);
        this.cobwebModel = new RotationDelegate(cobwebModel);
        this.spawnerModel = new RotationDelegate(spawnerModel);
        this.tntModel = new RotationDelegate(tntModel);
        this.mcModel = new RotationDelegate(mcModel);
        // 无效化权杖 = 空权杖模型 + 蛛网覆盖层（BEWLR 渲染，空权杖模型作为基础）
        this.cobwebNullifiedModel = new CobwebNullifiedDelegate(defaultModel);
        cn.autoforged.joes_addons_for_abmc.client.CobwebStaffBEWLR.INSTANCE.setBaseModel(defaultModel);
        // 自定义渲染器（BarrierStaffBEWLR）统一处理屏障覆盖层与蛛网覆盖层，这里注入空权杖基础模型，
        // 供“无效化”时作为被蛛网覆盖的权杖本体。
        cn.autoforged.joes_addons_for_abmc.client.BarrierStaffBEWLR.INSTANCE.setBaseModel(defaultModel);
    }

    @Override
    public ItemOverrides getOverrides() {
        return StaffItemOverrides.INSTANCE;
    }

    private BakedModel resolveModel(ItemStack stack, @Nullable net.minecraft.world.entity.LivingEntity holder) {
        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");

        // 权杖被“无效化”：除豁免类型（minecraft/omega/spider_web）外，一律渲染为
        // “空权杖 + 蛛网覆盖层”（自定义渲染器）。豁免类型无法被无效化，保持原样。
        if (isCobwebNullified(holder) && !isCobwebExempt(blockType)) {
            return cobwebNullifiedModel;
        }

        if ("gold_block".equals(blockType)) {
            return goldModel;
        }
        if ("netherite_block".equals(blockType)) {
            return netheriteModel;
        }
        if ("diamond_block".equals(blockType)) {
            return diamondModel;
        }
        if ("bedrock".equals(blockType)) {
            return bedrockModel;
        }
        if ("obsidian".equals(blockType)) {
            return obsidianModel;
        }
        if ("bone_block".equals(blockType)) {
            return boneModel;
        }
        if ("furnace".equals(blockType)) {
            return StaffClientState.furnaceOnTicks > 0 ? furnaceOnModel : furnaceModel;
        }
        if ("bell".equals(blockType)) {
            return bellModel;
        }
        if ("anvil".equals(blockType)) {
            return anvilModel;
        }
        if ("lapis_block".equals(blockType)) {
            return lapisModel;
        }
        if ("magma_block".equals(blockType)) {
            return magmaModel;
        }
        if ("omega".equals(blockType)) {
            return omegaModel;
        }
        if ("command_block".equals(blockType)) {
            return commandModel;
        }
        if ("end_portal_frame".equals(blockType)) {
            return endPortalModel;
        }
        if ("enchanting_table".equals(blockType)) {
            return enchantModel;
        }
        if ("player_head".equals(blockType)) {
            return playerHeadModel;
        }
        if ("herobrine_head".equals(blockType)) {
            return herobrineModel;
        }
        if ("barrier".equals(blockType)) {
            return barrierModel;
        }
        if ("dripstone_block".equals(blockType)) {
            return dripstoneModel;
        }
        if ("cauldron".equals(blockType)) {
            return cauldronModel;
        }
        if ("crafting_table".equals(blockType)) {
            return craftingTableModel;
        }
        if ("emerald_block".equals(blockType)) {
            return emeraldModel;
        }
        if ("ice".equals(blockType)) {
            return iceModel;
        }
        if ("iron_block".equals(blockType)) {
            return ironModel;
        }
        if ("netherrack".equals(blockType)) {
            return netherrackModel;
        }
        if ("note_block".equals(blockType)) {
            return noteblockModel;
        }
        if ("oak_log".equals(blockType)) {
            return oakModel;
        }
        if ("piston".equals(blockType)) {
            return pistonModel;
        }
        if ("red_mushroom_block".equals(blockType)) {
            return redMushroomModel;
        }
        if ("redstone_block".equals(blockType)) {
            return redstoneModel;
        }
        if ("snow_block".equals(blockType)) {
            return snowModel;
        }
        if ("bee_nest".equals(blockType)) {
            return beeNestModel;
        }
        if ("amethyst_block".equals(blockType)) {
            return amethystModel;
        }
        if ("cobweb".equals(blockType)) {
            return cobwebModel;
        }
        if ("spawner".equals(blockType)) {
            return spawnerModel;
        }
        if ("tnt".equals(blockType)) {
            return tntModel;
        }
        if ("minecraft_game_icon".equals(blockType)) {
            return mcModel;
        }
        return originalModel;
    }

    private static class RotationDelegate extends BakedModelWrapper<BakedModel> {
        RotationDelegate(BakedModel original) {
            super(original);
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext transformType, PoseStack poseStack, boolean leftHand) {
            originalModel.applyTransform(transformType, poseStack, leftHand);
            if (transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
                poseStack.mulPose(new Quaternionf().rotateY((float) Math.PI));
            }
            return this;
        }
    }

    private static class StaffItemOverrides extends ItemOverrides {
        static final StaffItemOverrides INSTANCE = new StaffItemOverrides();

        @Nullable
        @Override
        public BakedModel resolve(BakedModel original, ItemStack stack, @Nullable net.minecraft.client.multiplayer.ClientLevel level, @Nullable LivingEntity entity, int seed) {
            // 记录持有实体 id（无持有者写 -1，避免 ThreadLocal 残留），自定义渲染器据此判断“无效化”。
            CURRENT_HOLDER.set(entity == null ? -1 : entity.getId());
            if (original instanceof StaffBakedModel staffModel) {
                return staffModel.resolveModel(stack, entity);
            }
            return original;
        }
    }

    private static boolean isCobwebNullified(@Nullable net.minecraft.world.entity.LivingEntity holder) {
        return holder != null
            && cn.autoforged.joes_addons_for_abmc.client.CobwebClientState.isNullified(holder.getId());
    }

    /** 豁免无效化的权杖类型：minecraft / omega / spider_web / 屏障 / 绿宝石块。 */
    private static boolean isCobwebExempt(String blockType) {
        return "minecraft_game_icon".equals(blockType)
            || "omega".equals(blockType)
            || "cobweb".equals(blockType)
            || "barrier".equals(blockType)
            || "emerald_block".equals(blockType);
    }

    // 屏障权杖的模型标记为 isCustomRenderer()==true，使 ItemRenderer 走 BEWLR 渲染路径
    private static class BarrierDelegate extends BakedModelWrapper<BakedModel> {
        BarrierDelegate(BakedModel original) {
            super(original);
        }

        @Override
        public boolean isCustomRenderer() {
            return true;
        }

        // 继承自 BakedModelWrapper 的 applyTransform 会返回 originalModel，导致 isCustomRenderer()
        // 在 handleCameraTransforms 后丢失，从而无法进入 BEWLR 渲染路径。这里在应用变换后返回 this，
        // 确保 continue 走自定义渲染器。
        @Override
        public BakedModel applyTransform(ItemDisplayContext transformType, PoseStack poseStack, boolean leftHand) {
            originalModel.applyTransform(transformType, poseStack, leftHand);
            return this;
        }
    }

    // 被无效化权杖的模型标记为 isCustomRenderer()==true，使 ItemRenderer 走 BEWLR 渲染路径
    // （渲染空权杖 + 蛛网覆盖层）。应用空权杖的 display 变换后返回 this，保留自定义渲染路径。
    private static class CobwebNullifiedDelegate extends BakedModelWrapper<BakedModel> {
        CobwebNullifiedDelegate(BakedModel original) {
            super(original);
        }

        @Override
        public boolean isCustomRenderer() {
            return true;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext transformType, PoseStack poseStack, boolean leftHand) {
            originalModel.applyTransform(transformType, poseStack, leftHand);
            return this;
        }
    }
}

