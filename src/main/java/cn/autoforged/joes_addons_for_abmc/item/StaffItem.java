package cn.autoforged.joes_addons_for_abmc.item;

import java.util.List;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.client.BarrierStaffBEWLR;
import cn.autoforged.joes_addons_for_abmc.client.RedstoneLaserSounds;
import cn.autoforged.joes_addons_for_abmc.client.StaffClientProxy;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class StaffItem extends Item {

    public static final int MAX_STAFF_DURABILITY = 100000;
    public static final int MAX_BLOCK_DURABILITY = 100000;

    private static final ResourceLocation BLOCK_REACH_ID =
        ResourceLocation.fromNamespaceAndPath("joes_addons_for_abmc", "staff_block_reach");
    private static final ResourceLocation ENTITY_REACH_ID =
        ResourceLocation.fromNamespaceAndPath("joes_addons_for_abmc", "staff_entity_reach");
    private static final ResourceLocation ATTACK_SPEED_ID =
        ResourceLocation.fromNamespaceAndPath("joes_addons_for_abmc", "staff_attack_speed");
    private static final ResourceLocation MOVE_SPEED_ID =
        ResourceLocation.fromNamespaceAndPath("joes_addons_for_abmc", "staff_move_speed");
    private static final ResourceLocation KNOCKBACK_RESIST_ID =
        ResourceLocation.fromNamespaceAndPath("joes_addons_for_abmc", "staff_knockback_resist");
    private static final ResourceLocation ANVIL_MOVE_SPEED_ID =
        ResourceLocation.fromNamespaceAndPath("joes_addons_for_abmc", "staff_anvil_move_speed");
    private static final ResourceLocation ANVIL_JUMP_STRENGTH_ID =
        ResourceLocation.fromNamespaceAndPath("joes_addons_for_abmc", "staff_anvil_jump_strength");
    private static final ResourceLocation ANVIL_GRAVITY_ID =
        ResourceLocation.fromNamespaceAndPath("joes_addons_for_abmc", "staff_anvil_gravity");

    private static final ItemAttributeModifiers EMPTY_ATTRIBUTES = ItemAttributeModifiers.builder()
        .add(Attributes.ATTACK_SPEED,
            new AttributeModifier(ATTACK_SPEED_ID, -1.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND)
        .add(Attributes.BLOCK_INTERACTION_RANGE,
            new AttributeModifier(BLOCK_REACH_ID, 2.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .add(Attributes.ENTITY_INTERACTION_RANGE,
            new AttributeModifier(ENTITY_REACH_ID, 2.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .build();

    private static final ItemAttributeModifiers GOLD_ATTRIBUTES = ItemAttributeModifiers.builder()
        .add(Attributes.ATTACK_DAMAGE,
            new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 20.0, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND)
        .add(Attributes.ATTACK_SPEED,
            new AttributeModifier(ATTACK_SPEED_ID, -2.0, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND)
        .add(Attributes.BLOCK_INTERACTION_RANGE,
            new AttributeModifier(BLOCK_REACH_ID, 2.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .add(Attributes.ENTITY_INTERACTION_RANGE,
            new AttributeModifier(ENTITY_REACH_ID, 2.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .build();

    private static final ItemAttributeModifiers NETHERITE_ATTRIBUTES = ItemAttributeModifiers.builder()
        .add(Attributes.ATTACK_DAMAGE,
            new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 300.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND)
        .add(Attributes.ATTACK_SPEED,
            new AttributeModifier(ATTACK_SPEED_ID, -3.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND)
        .add(Attributes.BLOCK_INTERACTION_RANGE,
            new AttributeModifier(BLOCK_REACH_ID, 2.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .add(Attributes.ENTITY_INTERACTION_RANGE,
            new AttributeModifier(ENTITY_REACH_ID, 2.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .add(Attributes.MOVEMENT_SPEED,
            new AttributeModifier(MOVE_SPEED_ID, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
            EquipmentSlotGroup.HAND)
        .add(Attributes.KNOCKBACK_RESISTANCE,
            new AttributeModifier(KNOCKBACK_RESIST_ID, 0.9, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .build();

    private static final ItemAttributeModifiers OBSIDIAN_ATTRIBUTES = ItemAttributeModifiers.builder()
        .add(Attributes.ATTACK_DAMAGE,
            new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 6.0, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND)
        .add(Attributes.ATTACK_SPEED,
            new AttributeModifier(ATTACK_SPEED_ID, -2.0, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND)
        .add(Attributes.BLOCK_INTERACTION_RANGE,
            new AttributeModifier(BLOCK_REACH_ID, 2.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .add(Attributes.ENTITY_INTERACTION_RANGE,
            new AttributeModifier(ENTITY_REACH_ID, 2.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .build();

    private static final ItemAttributeModifiers BELL_ATTRIBUTES = ItemAttributeModifiers.builder()
        .add(Attributes.ATTACK_DAMAGE,
            new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 3.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND)
        .add(Attributes.ATTACK_SPEED,
            new AttributeModifier(ATTACK_SPEED_ID, -1.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND)
        .add(Attributes.BLOCK_INTERACTION_RANGE,
            new AttributeModifier(BLOCK_REACH_ID, 2.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .add(Attributes.ENTITY_INTERACTION_RANGE,
            new AttributeModifier(ENTITY_REACH_ID, 2.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .build();

    private static final ItemAttributeModifiers ANVIL_ATTRIBUTES = ItemAttributeModifiers.builder()
        .add(Attributes.ATTACK_SPEED,
            new AttributeModifier(ATTACK_SPEED_ID, -1.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND)
        .add(Attributes.BLOCK_INTERACTION_RANGE,
            new AttributeModifier(BLOCK_REACH_ID, 2.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .add(Attributes.ENTITY_INTERACTION_RANGE,
            new AttributeModifier(ENTITY_REACH_ID, 2.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .add(Attributes.MOVEMENT_SPEED,
            new AttributeModifier(ANVIL_MOVE_SPEED_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
            EquipmentSlotGroup.HAND)
        .add(Attributes.JUMP_STRENGTH,
            new AttributeModifier(ANVIL_JUMP_STRENGTH_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
            EquipmentSlotGroup.HAND)
        .add(Attributes.GRAVITY,
            new AttributeModifier(ANVIL_GRAVITY_ID, 0.92, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .build();

    private static final ItemAttributeModifiers DIAMOND_ATTRIBUTES = ItemAttributeModifiers.builder()
        .add(Attributes.ATTACK_DAMAGE,
            new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 0.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND)
        .add(Attributes.ATTACK_SPEED,
            new AttributeModifier(ATTACK_SPEED_ID, -1.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND)
        .add(Attributes.BLOCK_INTERACTION_RANGE,
            new AttributeModifier(BLOCK_REACH_ID, 2.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .add(Attributes.ENTITY_INTERACTION_RANGE,
            new AttributeModifier(ENTITY_REACH_ID, 2.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .build();

    private static final ItemAttributeModifiers HEROBRINE_ATTRIBUTES = ItemAttributeModifiers.builder()
        .add(Attributes.ATTACK_DAMAGE,
            new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 0.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND)
        .add(Attributes.ATTACK_SPEED,
            new AttributeModifier(ATTACK_SPEED_ID, -1.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND)
        .add(Attributes.BLOCK_INTERACTION_RANGE,
            new AttributeModifier(BLOCK_REACH_ID, 2.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .add(Attributes.ENTITY_INTERACTION_RANGE,
            new AttributeModifier(ENTITY_REACH_ID, 2.5, AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.HAND)
        .build();

    public StaffItem(Properties properties) {
        super(properties);
    }

    public static int getBlockDamage(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.BLOCK_DAMAGE.get(), 0);
    }

    public static void setBlockDamage(ItemStack stack, int damage) {
        stack.set(ModDataComponents.BLOCK_DAMAGE.get(), damage);
    }

    /** 权杖与方块耐久不以耐久条形式呈现（改为 F3+H 高级提示中显示）。 */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return false;
    }

    /** 权杖耐久消耗（无论何种方式）不触发第一人称“重新装备”摇晃动画；仅在物品种类或方块形态变化时播放。 */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (!oldStack.is(newStack.getItem())) return true;
        String oldBt = oldStack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        String newBt = newStack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        return !oldBt.equals(newBt);
    }

    /** 仅在 F3+H（原版高级提示）开启时，于物品提示中显示权杖与方块耐久。 */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        if (!tooltipFlag.isAdvanced()) return;
        // 权杖耐久
        int staffRemain = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        tooltipComponents.add(Component.translatable(
            "tooltip.joes_addons_for_abmc.staff_durability", staffRemain, stack.getMaxDamage()));
        // 方块耐久（仅有方块形态的权杖才显示）
        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        if (!"empty".equals(blockType)) {
            int blockRemain = Math.max(0, MAX_BLOCK_DURABILITY - getBlockDamage(stack));
            tooltipComponents.add(Component.translatable(
                "tooltip.joes_addons_for_abmc.block_durability", blockRemain, MAX_BLOCK_DURABILITY));
        }
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        if ("gold_block".equals(blockType)) {
            return GOLD_ATTRIBUTES;
        }
        if ("diamond_block".equals(blockType)) {
            return DIAMOND_ATTRIBUTES;
        }
        if ("netherite_block".equals(blockType)) {
            return NETHERITE_ATTRIBUTES;
        }
        if ("obsidian".equals(blockType)) {
            return OBSIDIAN_ATTRIBUTES;
        }
        if ("bone_block".equals(blockType)) {
            return EMPTY_ATTRIBUTES;
        }
        if ("furnace".equals(blockType)) {
            return EMPTY_ATTRIBUTES;
        }
        if ("bedrock".equals(blockType)) {
            return EMPTY_ATTRIBUTES;
        }
        if ("bell".equals(blockType)) {
            return BELL_ATTRIBUTES;
        }
        if ("anvil".equals(blockType)) {
            return ANVIL_ATTRIBUTES;
        }
        if ("lapis_block".equals(blockType)) {
            return EMPTY_ATTRIBUTES;
        }
        if ("magma_block".equals(blockType)) {
            return EMPTY_ATTRIBUTES;
        }
        if ("omega".equals(blockType)) {
            return EMPTY_ATTRIBUTES;
        }
        if ("command_block".equals(blockType)) {
            return EMPTY_ATTRIBUTES;
        }
        if ("end_portal_frame".equals(blockType)) {
            return EMPTY_ATTRIBUTES;
        }
        if ("enchanting_table".equals(blockType)) {
            return EMPTY_ATTRIBUTES;
        }
        if ("player_head".equals(blockType)) {
            return EMPTY_ATTRIBUTES;
        }
        if ("herobrine_head".equals(blockType)) {
            return HEROBRINE_ATTRIBUTES;
        }
        if ("barrier".equals(blockType)) {
            return EMPTY_ATTRIBUTES;
        }
        return EMPTY_ATTRIBUTES;
    }

    @Override
    public Component getName(ItemStack stack) {
        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        if (!"empty".equals(blockType)) {
            return Component.translatable(this.getDescriptionId(stack) + "." + blockType);
        }
        return super.getName(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        // 附魔台权杖与 Him 权杖：使用拉弓动画表示“长按”
        if ("enchanting_table".equals(blockType) || "herobrine_head".equals(blockType)) {
            return UseAnim.BOW;
        }
        // 红石块权杖：长按右键发射红石射线，同样用拉弓动画表示“长按”
        if ("redstone_block".equals(blockType)) {
            return UseAnim.BOW;
        }
        // Omega 权杖：吸收模式需要全程按住右键，同样用拉弓动画表示“长按”
        if ("omega".equals(blockType)) {
            return UseAnim.BOW;
        }
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        // 持有者权杖被“无效化”（非豁免类型）期间，权杖表现为空权杖，无法使用。
        if (!level.isClientSide() && ModMain.isStaffNullified(player)
            && !("minecraft_game_icon".equals(blockType) || "omega".equals(blockType) || "cobweb".equals(blockType))) {
            return InteractionResultHolder.pass(stack);
        }
        if ("obsidian".equals(blockType)) {
            if (!level.isClientSide()) {
                ModMain.executeObsidianStaffAbility(player, stack, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if ("bone_block".equals(blockType)) {
            if (!level.isClientSide()) {
                ModMain.executeBoneStaffAbility(player, stack, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if ("furnace".equals(blockType)) {
            if (level.isClientSide()) {
                StaffClientState.furnaceOnTicks = 4;
            } else {
                ModMain.executeFurnaceStaffAbility(player, stack, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if ("bedrock".equals(blockType)) {
            if (!level.isClientSide()) {
                ModMain.executeBedrockStaffAbility(player, stack, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if ("lapis_block".equals(blockType)) {
            if (!level.isClientSide()) {
                ModMain.executeLapisStaffAbility(player, stack, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if ("magma_block".equals(blockType)) {
            if (!level.isClientSide()) {
                ModMain.executeMagmaStaffAbility(player, stack, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if ("omega".equals(blockType)) {
            // 吸收模式：冷却中直接返回，否则进入“使用中”状态（拉弓动画），由 onUseTick 驱动吸收。
            if (player.getCooldowns().isOnCooldown(stack.getItem())) {
                return InteractionResultHolder.pass(stack);
            }
            OmegaStaffMode mode = OmegaStaffMode.get(stack);
            switch (mode) {
                case ABSORB -> {
                    player.startUsingItem(hand);
                    return InteractionResultHolder.consume(stack);
                }
            }
            return InteractionResultHolder.pass(stack);
        }
        if ("command_block".equals(blockType)) {
            if (level.isClientSide()) {
                StaffClientProxy.openCommandStaffScreen();
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if ("end_portal_frame".equals(blockType)) {
            // 放置由客户端按住右键驱动（START/FLIP/PLACE 包），use() 不做任何事，避免重复生成
            return InteractionResultHolder.pass(stack);
        }
        if ("enchanting_table".equals(blockType)) {
            // 附魔台权杖：按住右键持续使用（拉弓动画）。必须显式调用 startUsingItem，
            // 否则客户端与服务端都不会真正进入“使用中”状态，拉弓动画与 onUseTick 均不会生效。
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if ("player_head".equals(blockType)) {
            return InteractionResultHolder.pass(stack);
        }
        if ("herobrine_head".equals(blockType)) {
            if (ModMain.isHerobrineRanged(player)) {
                // 远程模式：按住右键进入使用状态（拉弓），以雪球频率持续发射头颅。
                player.startUsingItem(hand);
                return InteractionResultHolder.consume(stack);
            }
            // 近战模式：右键立即传送到瞄准的生物身边。
            if (!level.isClientSide()) {
                ModMain.executeHerobrineTeleport(player);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if ("barrier".equals(blockType)) {
            // 屏障权杖：相当于无限堆叠的屏障方块。右键方块在相邻格放置屏障，右键空气则隔 5 格在空中放置。
            if (!level.isClientSide()) {
                ModMain.executeBarrierStaffPlace(player, stack, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if ("dripstone_block".equals(blockType)) {
            // 滴水石权杖：右键方块顶面，召唤自下而上 tip/frustum/middle/base 的滴水石锥下落方块群组。
            if (!level.isClientSide()) {
                ModMain.executeDripstoneStaffAbility(player, stack, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if ("redstone_block".equals(blockType)) {
            // 红石块权杖：按住右键持续发射红石射线（拉弓动画）。
            player.startUsingItem(hand);
            if (!level.isClientSide()) {
                ModMain.startRedstoneStaff(player);
            }
            return InteractionResultHolder.consume(stack);
        }
        if ("tnt".equals(blockType)) {
            // TNT 权杖：右键丢出一枚点燃的 TNT（0.1% 概率为苦力怕），命中方块随即引爆。
            if (!level.isClientSide()) {
                ModMain.executeTntStaffPlace(player, stack, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if ("cobweb".equals(blockType)) {
            // 蜘蛛网权杖：右键发射一束蛛丝（最远 128 格），命中方块拉向自己，
            // 命中实体则移除弹射物/无效化权杖或铺蛛网。
            if (!level.isClientSide()) {
                ModMain.executeCobwebStaffAbility(player, stack, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if ("spawner".equals(blockType)) {
            // 刷怪笼权杖：右键召唤随机非 boss 生物（对准方块在旁边召唤，否则在视角前方 4 格召唤）。
            if (!level.isClientSide()) {
                ModMain.executeSpawnerStaffAbility(player, stack, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if ("ice".equals(blockType)) {
            // 冰块权杖：右键生物（须在交互范围内），将其碰撞箱触及的方块替换为霜冰并困住生物。
            if (!level.isClientSide()) {
                ModMain.executeIceStaffAbility(player, stack, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if ("iron_block".equals(blockType)) {
            // 铁块（铁链）权杖：按下一次右键即发射直线铁链并自动持续拉取（无需按住维持）；
            // 中途按左键或切换权杖可断开，目标按当前速度惯性甩出。
            if (!level.isClientSide()) {
                ModMain.executeChainStaffAbility(player, stack, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide()) return;
        if (!(livingEntity instanceof Player player)) return;
        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        // 无效化（非豁免）期间即使已长按使用，也立即停止每刻逻辑，确保权杖失去功能。
        if (ModMain.isStaffNullified(player)
            && !("minecraft_game_icon".equals(blockType) || "omega".equals(blockType) || "cobweb".equals(blockType))) {
            return;
        }
        if ("enchanting_table".equals(blockType)) {
            // 附魔台权杖：每 4 刻尝试对瞄准的生物主手物品附魔一次
            int usedTicks = this.getUseDuration(stack, livingEntity) - remainingUseDuration;
            if (usedTicks > 0 && usedTicks % 4 == 0) {
                ModMain.executeEnchantStaffTick(player);
            }
            return;
        }
        if ("omega".equals(blockType)) {
            // Omega 权杖：按当前模式分发每刻逻辑。吸收模式每刻把瞄准的生物拉近玩家。
            OmegaStaffMode mode = OmegaStaffMode.get(stack);
            switch (mode) {
                case ABSORB -> ModMain.executeOmegaAbsorbTick(player);
            }
            return;
        }
        if ("redstone_block".equals(blockType)) {
            // 红石块权杖：每刻沿玩家视线发射红石射线（伤害/强充能/破坏/粒子均由服务端执行）。
            ModMain.executeRedstoneStaffTick(player);
            return;
        }
        if (!"herobrine_head".equals(blockType)) return;
        // 仅远程模式持续发射；近战模式不进入使用状态，此分支不会触发。
        if (!ModMain.isHerobrineRanged(player)) return;
        int usedTicks = this.getUseDuration(stack, livingEntity) - remainingUseDuration;
        // 取消前摇：以丢雪球的频率（每 4 刻，即 0.2 秒）持续朝玩家看向的方向发射 Herobrine 头颅
        if (usedTicks % 4 == 0) {
            ModMain.executeHerobrineHeadShoot(player);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeLeft) {
        if (level.isClientSide()) {
            // 客户端松开右键：红石块权杖松开后强制停止/复位激光音效状态机。
            // 松开右键、切换物品等任何使 item use 结束的路径都会走到这里，
            // 确保循环的 laser_middle 音效停止（此时若正在循环则顺带播放一次 laser_end）。
            if (livingEntity instanceof Player
                && "redstone_block".equals(stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty"))) {
                RedstoneLaserSounds.stopAndReset(net.minecraft.client.Minecraft.getInstance());
            }
            return;
        }
        if (!(livingEntity instanceof Player player)) return;
        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        // 无效化（非豁免）期间松开右键同样不执行收尾逻辑（如传送/吸收恢复）。
        if (ModMain.isStaffNullified(player)
            && !("minecraft_game_icon".equals(blockType) || "omega".equals(blockType) || "cobweb".equals(blockType))) {
            return;
        }
        if ("omega".equals(blockType)) {
            // 松开右键：恢复被吸收生物的重力/AI，并进入 1 秒（20 刻）冷却。
            ModMain.releaseOmegaAbsorb(player);
            player.getCooldowns().addCooldown(stack.getItem(), 20);
            return;
        }
        if ("redstone_block".equals(blockType)) {
            // 松开右键：停止发射红石射线，并恢复此前被强充能的方块。
            ModMain.releaseRedstoneStaff(player);
            return;
        }
        if (!"herobrine_head".equals(blockType)) return;
        // 近战模式的传送已在 use() 中即时触发（不进入使用状态，不会走到这里）；
        // 远程模式的持续发射已在 onUseTick() 中完成，松开右键无需额外处理。
    }

    @Override
    public int getEnchantmentValue() {
        return 25;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return this.getMaxStackSize(stack) == 1 && stack.isDamageableItem();
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return true;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return true;
    }

    @Override
    public boolean canDisableShield(ItemStack stack, ItemStack shield, LivingEntity entity, LivingEntity attacker) {
        return true;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        if ("netherite_block".equals(blockType)) return true;
        if ("obsidian".equals(blockType)) {
            if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) return true;
            return state.is(BlockTags.MINEABLE_WITH_PICKAXE);
        }
        if ("bone_block".equals(blockType)) return false;
        if ("furnace".equals(blockType)) return false;
        if ("bedrock".equals(blockType)) return true;
        if ("bell".equals(blockType)) return false;
        if ("anvil".equals(blockType)) return false;
        if ("lapis_block".equals(blockType)) return false;
        if ("magma_block".equals(blockType)) return false;
        if ("omega".equals(blockType)) return false;
        if ("command_block".equals(blockType)) return false;
        if ("end_portal_frame".equals(blockType)) return false;
        if ("enchanting_table".equals(blockType)) return false;
        if ("player_head".equals(blockType)) return false;
        if ("herobrine_head".equals(blockType)) return false;
        if ("barrier".equals(blockType)) return true;
        if (!"gold_block".equals(blockType)) return false;
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        String blockType = stack.getOrDefault(ModDataComponents.BLOCKTYPE.get(), "empty");
        if ("netherite_block".equals(blockType)) {
            if (state.isAir()) return 1.0F;
            if (state.getDestroySpeed(null, BlockPos.ZERO) < 0) return 1.0F;
            return 1500.0F;
        }
        if ("obsidian".equals(blockType)) {
            if (state.isAir()) return 1.0F;
            if (state.getDestroySpeed(null, BlockPos.ZERO) < 0) return 1.0F;
            if (state.is(Blocks.NETHERITE_BLOCK)) return 1.0F;
            return 1500.0F;
        }
        if ("bone_block".equals(blockType)) return 1.0F;
        if ("furnace".equals(blockType)) return 1.0F;
        if ("bedrock".equals(blockType)) return 1500.0F;
        if ("bell".equals(blockType)) return 1.0F;
        if ("anvil".equals(blockType)) return 1.0F;
        if ("lapis_block".equals(blockType)) return 1.0F;
        if ("magma_block".equals(blockType)) return 1.0F;
        if ("omega".equals(blockType)) return 1.0F;
        if ("command_block".equals(blockType)) return 1.0F;
        if ("end_portal_frame".equals(blockType)) return 1.0F;
        if ("enchanting_table".equals(blockType)) return 1.0F;
        if ("player_head".equals(blockType)) return 1.0F;
        if ("herobrine_head".equals(blockType)) return 1.0F;
        if ("barrier".equals(blockType)) return 1.0F;
        if (!"gold_block".equals(blockType)) return 1.0F;
        if (state.isAir()) return 1.0F;
        if (state.getDestroySpeed(null, BlockPos.ZERO) < 0) return 1.0F;
        if (state.is(Blocks.DIAMOND_BLOCK)) return 1.0F;
        if (state.is(Blocks.NETHERITE_BLOCK)) return 1.0F;
        return 1500.0F;
    }

    // 屏障权杖的模型标记为 isCustomRenderer()==true，因此仅屏障权杖会走此 BEWLR 渲染路径。
    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(java.util.function.Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return BarrierStaffBEWLR.INSTANCE;
            }
        });
    }
}
