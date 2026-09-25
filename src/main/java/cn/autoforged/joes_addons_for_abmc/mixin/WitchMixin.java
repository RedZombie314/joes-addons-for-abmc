package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.pathfinder.PathType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 女巫 Boss 变种（携带 {@code jafa_is_witch_boss} 标签）的战斗行为微调。
 * <ul>
 *   <li>隐身药水：原版女巫在战斗中本应喝下「迅捷药水」时，会有 10% 概率改喝「隐身药水」。
 *       若 Boss 已发现过玩家，则发现后第一次进入「要喝迅捷药水」的场景时进行一次 70% 隐身 roll
 *       （无论成败仅此一次），之后所有场景均为常规 10% roll。</li>
 *   <li>只投变形药水：Boss 永远不会投掷伤害/中毒等原版攻击药水，只向敌方投掷变形药水，且
 *       每个阶段投对应阶段名字的药水——阶段1实体→必定变形成生物、阶段2方块→变方块、阶段3物品→变物品。</li>
 * </ul>
 * 其余行为与普通女巫完全一致。
 */
@Mixin(Witch.class)
public abstract class WitchMixin {

    /** 女巫Boss寻路避水：原版女巫的 MobType 为 UNDEFINED（可浮水），因此其
     *  GroundPathNavigation 寻路时会把水面当作可通行节点（WATER/WATER_BORDER 默认
     *  malus=8.0），导致 Boss 经常直接下水游过去。这里在服务端一次性把水路的寻路代价
     *  大幅调高，使其在存在陆地绕行路线时优先走陆地，尽量避免踏入水方块。
     *  采用较大但有限的代价而非 MAX，保证当四周确实无水陆绕行路线时仍可涉水通过，避免卡死。 */
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void jafa_witchBossAvoidWater(CallbackInfo ci) {
        Witch self = (Witch) (Object) this;
        var data = self.getPersistentData();
        if (data.getBoolean("jafa_is_witch_boss")
                && !data.getBoolean("jafa_witch_boss_water_avoid_done")
                && !self.level().isClientSide()) {
            data.putBoolean("jafa_witch_boss_water_avoid_done", true);
            float waterAvoidMalus = 100.0F;
            self.setPathfindingMalus(PathType.WATER, waterAvoidMalus);
            self.setPathfindingMalus(PathType.WATER_BORDER, waterAvoidMalus);
        }
    }

    @Redirect(
        method = "aiStep",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/item/alchemy/Potions;SWIFTNESS:Lnet/minecraft/core/Holder;")
    )
    private Holder<Potion> jafa_maybeInvisibilityInsteadOfSwiftness() {
        Witch self = (Witch) (Object) this;
        var data = self.getPersistentData();
        if (data.getBoolean("jafa_is_witch_boss")) {
            // 已处于隐身状态时不重复喝隐身（避免无限叠加/持续隐身导致无法锁定）
            boolean alreadyInvisible = self.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            // 发现玩家后的一次性 7/10 roll：在本方法（= 进入迅捷场景）首次被调用时进行，无论成败只此一次
            if (!alreadyInvisible && data.getBoolean("jafa_witch_boss_discovered")
                    && !data.getBoolean("jafa_witch_boss_first_roll_used")) {
                data.putBoolean("jafa_witch_boss_first_roll_used", true);
                if (self.getRandom().nextFloat() < 0.7F) {
                    ModMain.onWitchBossDrinkInvisibility(self); // 直饮隐身 → 消耗隐身药水计数
                    return Potions.INVISIBILITY;
                }
                return Potions.SWIFTNESS;
            }
            // 常规 10% roll（已隐身则跳过，避免一直隐身）
            if (!alreadyInvisible && self.getRandom().nextFloat() < 0.1F) {
                ModMain.onWitchBossDrinkInvisibility(self); // 直饮隐身 → 消耗隐身药水计数
                return Potions.INVISIBILITY;
            }
        }
        return Potions.SWIFTNESS;
    }

    /** 女巫Boss：原版 aiStep 喝治疗药水时用的是 Potions.HEALING（瞬间治疗 I），
     *  本 redirect 让 Boss 全阶段都改为 Potions.STRONG_HEALING（瞬间治疗 II）。
     *  覆盖原版自动喝药和我们 handleWitchBossStage2Heal 手动喝药两条路径——
     *  后者虽然已经用 STRONG_HEALING，但原版 aiStep 可能先抢先设置主手药水，
     *  等我们的 Post tick 回调运行时 isDrinkingPotion() 已为 true，导致被跳过。 */
    @Redirect(
        method = "aiStep",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/item/alchemy/Potions;HEALING:Lnet/minecraft/core/Holder;")
    )
    private Holder<Potion> jafa_bossDrinkStrongHealing() {
        Witch self = (Witch) (Object) this;
        if (self.getPersistentData().getBoolean("jafa_is_witch_boss")) {
            return Potions.STRONG_HEALING;
        }
        return Potions.HEALING;
    }

    /** 女巫Boss：投掷替换为“按阶段确定的变形药水”，永不投掷伤害/中毒等原版攻击药水。
     *  所有阶段先检查目标玩家是否已被变形：若未变形则持续投变形药水（直至其被变形）；
     *  若目标玩家已被变形则停投（阶段1不攻击已变形的动物/其它，转到各阶段专属行为）。 */
    @Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
    private void jafa_witchBossThrowTransmutation(LivingEntity target, float p, CallbackInfo ci) {
        Witch self = (Witch) (Object) this;
        if (self.getPersistentData().getBoolean("jafa_is_witch_boss")) {
            // 阶段3近战状态：不投远程变形药水（专注近战）
            boolean melee = ModMain.isWitchBossInMelee(self);
            // 目标玩家已被变形 → 停投（直至其被变形）
            boolean targetTransmuted = (target instanceof net.minecraft.server.level.ServerPlayer sp)
                && ModMain.isPlayerTransmuted(sp);
            // 阶段2：目标若已是“生物形态”的玩家，仍投（把它由生物→方块，像对待正常玩家一样）
            boolean tossToMorphedMob = (target instanceof net.minecraft.server.level.ServerPlayer sp2)
                && ModMain.shouldTossBlockPotionAtMorphedMob(self, sp2);
            if (!melee && target != null && (!targetTransmuted || tossToMorphedMob)) {
                ModMain.throwWitchBossTransmutationPotion(self, target);
            }
            ci.cancel(); // 不再执行原版投掷（伤害/迅捷/治疗/隐身等均被替换）
        }
    }

    @ModifyArg(
        method = "performRangedAttack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ThrownPotion;shoot(DDDFF)V"),
        index = 3
    )
    private float jafa_witchBossThrowSpeed(float velocity) {
        Witch self = (Witch) (Object) this;
        if (self.getPersistentData().getBoolean("jafa_is_witch_boss")) {
            return velocity * 1.5F; // throw速度提升为1.5倍
        }
        return velocity;
    }
}