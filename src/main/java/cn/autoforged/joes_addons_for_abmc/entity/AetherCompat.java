package cn.autoforged.joes_addons_for_abmc.entity;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.entity.OrbPossessedAttackEvents.PossessedAttackEvent;
import com.aetherteam.aether.client.AetherSoundEvents;
import com.aetherteam.aether.entity.projectile.crystal.CloudCrystal;
import com.aetherteam.aether.entity.projectile.dart.PoisonDart;
import com.aetherteam.aether.entity.projectile.weapon.HammerProjectile;
import com.aetherteam.aether.entity.projectile.weapon.ThrownLightningKnife;
import com.aetherteam.aether.item.AetherItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <b>天境（Aether）联动</b>：装了天境时，附体空壳会额外抽取下面这八张签。
 * 每一张都照着天境 jar 里的源码实现（武器/动作/伤害/特殊效果全部对齐），
 * 而不是"拿着同名的白板武器随便打一下"。
 *
 * <h2>软依赖怎么保证安全（6.5.9 踩过的坑，务必照做）</h2>
 * <ul>
 *   <li>{@code build.gradle} 里只有 {@code compileOnly libs/aether-....jar}：
 *       <b>不打包、不声明依赖</b>，没装天境时游戏照常启动；</li>
 *   <li><b>绝不能在"天境缺席时"被加载</b>：本类的方法体里全是天境的类型，卸载天境后
 *       一旦被加载，JVM 解析 {@code CloudCrystal} 等类型就会抛
 *       {@code NoClassDefFoundError}（实测：卸载天境后进存档、第一个服务端刻就崩在
 *       {@code ModMain#onServerTickPre} 的 {@code AetherCompat.tick()} 那一行）。
 *       所以<b>所有调用点都先问 {@link OptionalMods#isAetherLoaded()}</b>——
 *       那个类不引用任何天境类型，问它不会把本类加载起来；</li>
 *   <li>本类<b>自身</b>也不直接引用天境类型（连 {@code CloudCrystal} 都挪进了
 *       {@link CloudStaffEvent} 这个嵌套类），这样即使哪天有谁漏了那道门，光加载本类也不会炸。</li>
 * </ul>
 *
 * <h2>八张签（括号里是天境源码里的依据）</h2>
 * <ol>
 *   <li><b>近战 · 重力晶剑</b>（{@code gravitite_sword}）：命中时把目标<b>挑飞</b>
 *       （{@code GravititeSwordItem#hurtEnemy} → {@code GravititeWeapon#launchEntity}：
 *       {@code push(0, 1, 0)}，前提是目标不在 {@code aether:unlaunchable} 标签里、
 *       且正踩在地上或泡在流体里）；</li>
 *   <li><b>近战 · 疾电长剑</b>（{@code lightning_sword}）：命中时在目标身上<b>落一道真正的闪电</b>
 *       （{@code LightningSwordItem#hurtEnemy} 生成 {@code LIGHTNING_BOLT}）；</li>
 *   <li><b>近战 · 吸血魔剑</b>（{@code vampire_blade}）：命中时<b>回 1 点血</b>（不满血才回）
 *       —— 天境源码里非玩家攻击者走的就是 {@code attacker.heal(1.0F)} 这一支；</li>
 *   <li><b>远程 · 剧毒镖箭</b>：手持<b>剧毒镖箭发射器</b>（{@code poison_dart_shooter}，
 *       副手亮出毒镖），每 1~2gt"吹"出一只 {@code PoisonDart}，一梭子 3~4 只。
 *       速度/散布/无重力照 {@code DartShooterItem}（3.1 格/刻、散布 1.2、{@code setNoGravity(true)}），
 *       命中会挂上天境的<b>酩酊</b>效果（{@code PoisonDart#doPostHurtEffects}）；</li>
 *   <li><b>远程 · 惊雷飞刀</b>：每 1~2gt 甩出一只 {@code ThrownLightningKnife}，一梭子 2~3 只。
 *       速度 0.8、散布 1.0（照 {@code LightningKnifeItem#use}），
 *       <b>落点会劈下一道雷</b>（{@code ThrownLightningKnife#onHit} → {@code EntityUtil#summonLightningFromProjectile}）；</li>
 *   <li><b>远程 · 凤舞长弓</b>：拉弓 20 刻，射出 1~2 支<b>火焰箭</b>
 *       （{@code PhoenixBowItem} + {@code AbilityHooks#phoenixArrowHit}：命中点燃 20 秒，
 *       见 {@link OrbPhoenixArrow}）；</li>
 *   <li><b>远程 · 创始者飞锤</b>：掷出一枚 {@code HammerProjectile}
 *       （{@code HammerOfKingbdogzItem#use}：速度 3.0、散布 1.0、无重力）。
 *       命中实体 7 点伤害 + 上挑，命中方块会把<b>周围 5 格内的所有实体</b>一起挑飞
 *       （{@code HammerProjectile#launchTarget}）；</li>
 *   <li><b>近战 · 境云权杖</b>：举起 {@code cloud_staff} 用一次（放出天境那把杖的召唤粒子），
 *       随后朝着目标<b>"左键"4~6 次</b>，每次从两边肩膀各射出一发云水晶
 *       （{@code CloudStaffItem}：先召出左右两只云从者，之后每次挥手臂都让它们开火
 *       {@code CloudCrystal}）。</li>
 * </ol>
 *
 * <h2>和天境本体的两处差别（都是被迫的，已在报告里说明）</h2>
 * <ul>
 *   <li><b>云从者不能真的召唤出来</b>：{@code CloudMinion#getOwner()} 是
 *       {@code (Player) level.getEntity(...)} —— 硬转玩家，主人换成空壳会每刻抛
 *       {@code ClassCastException}。所以这里改成"由空壳自己从两肩位置射出同样的云水晶"
 *       （伤害 5 点仍由 {@code CloudCrystal#onHitEntity} 自己结算）；</li>
 *   <li><b>近战特效不由物品触发而是显式调用</b>：天境剑的效果写在
 *       {@code Item#hurtEnemy(...)} 里，而原版只有<b>玩家</b>挥砍才会走到那里
 *       （{@code ItemStack#hurtEnemy} 的参数就是 {@code Player}）。空壳走的是
 *       {@link OrbPossessedAttackEvents#swingWeapon}，那里显式调了一次
 *       {@code weapon.getItem().hurtEnemy(weapon, target, attacker)}，效果与玩家挥砍一致。</li>
 * </ul>
 */
public final class AetherCompat {

    /** 天境的 modid（真正的"装没装"判断在 {@link OptionalMods#isAetherLoaded()}）。 */
    public static final String MOD_ID = OptionalMods.AETHER;

    /** 近战起手（举剑）刻数：与模组其它近战（闪烁西瓜刀/暮色剑）的 6 刻一致。 */
    private static final int MELEE_WIND_UP_TICKS = 6;

    /** 近战出手间隔（刻）：1.5 秒，与其它近战一致。 */
    private static final int MELEE_COOLDOWN_TICKS = 30;

    /** 拉弓类远程的起手刻数（20 刻 = 1 秒，与模组对齐的两张弓一样）。 */
    private static final int BOW_WIND_UP_TICKS = 20;

    /** 投掷类远程的起手刻数（甩飞刀 / 掷锤）。 */
    private static final int THROW_WIND_UP_TICKS = 10;

    /** 远程出手间隔（刻）：2 秒。 */
    private static final int RANGED_COOLDOWN_TICKS = 40;

    /** 惊雷飞刀那签单独的出手间隔（刻）：3 秒（会落雷，比普通远程更贵）。 */
    private static final int KNIFE_COOLDOWN_TICKS = 60;

    /** 创始者飞锤的出手间隔（刻）：照天境配置 {@code hammer_of_kingbdogz_cooldown} 的默认值 50。 */
    private static final int HAMMER_COOLDOWN_TICKS = 50;

    /**
     * 境云权杖两次"左键"之间的间隔（刻）：<b>0.5 秒</b>（10 刻）。
     * <p>
     * 用户的要求是"随后面向目标'左键' 4~6 次"——听上去就是连着点几下，
     * 所以这里按玩家平砍的节奏（10 刻）来，一整套 4~6 次在 2~3 秒内打完。
     * <p>
     * 说明：天境本体里 {@code CloudStaffItem#onEntitySwing} 给这把杖上的是配置项
     * {@code cloud_staff_cooldown}（默认 40 刻）的冷却，那是<b>限制玩家</b>多久能指挥一次云从者；
     * 换成"连着点 4~6 下"的观感就太拖了（要 8~12 秒），所以这里取 10 刻。
     * 要改成天境原值就把这个常量改成 40。
     */
    private static final int CLOUD_STAFF_SWING_INTERVAL_TICKS = 10;

    /**
     * 境云权杖整套动作的出手间隔（刻）：5 秒。
     * <p>
     * 一整套本身要 2~3 秒打完，这个冷却只是"别一套接一套地刷"。
     */
    private static final int CLOUD_STAFF_COOLDOWN_TICKS = 100;

    /** 境云权杖那套动作总次数：4~6 次"左键"（用户指定）。 */
    private static final int CLOUD_STAFF_MIN_SWINGS = 4;
    private static final int CLOUD_STAFF_MAX_SWINGS = 6;

    /** 云从者停在主人两侧的距离（格）：照 {@code CloudMinion#setPositionFromOwner} 里的 1.05。 */
    private static final double CLOUD_MINION_SIDE_OFFSET = 1.05D;

    /** 云从者的悬浮高度（相对主人脚底，格）：{@code CloudMinion#setPositionFromOwner} 里的 +1.0。 */
    private static final double CLOUD_MINION_HEIGHT_OFFSET = 1.0D;

    /** 云水晶的出膛速度：照 {@code CloudMinion#tick} 里那句 {@code shootFromRotation(..., 1.0F, 1.0F)} 的 1.0。 */
    private static final float CLOUD_CRYSTAL_SPEED = 1.0F;

    /**
     * 云水晶的散布：<b>0</b>（用户反馈后改的，天境本体是 1.0）。
     * <p>
     * 天境那两只云从者是"沿主人的朝向"开火的（它们没有自己的目标），所以布局是
     * "两发平行飞出去、隔着 2.1 格" —— 换成空壳来用就会变成"往两侧散开、根本打不着目标"。
     * 这里改成<b>每一发都从自己那个肩膀位置直接瞄目标</b>（见 {@code swingAndShoot}），
     * 既然方向已经逐发算准了，散布就没有必要留着，于是取 0。
     */
    private static final float CLOUD_CRYSTAL_INACCURACY = 0.0F;

    /** 剧毒镖箭的弹速与散布：照 {@code DartShooterItem#finishUsingItem} 的 {@code shoot(..., 3.1F, 1.2F)}。 */
    private static final float DART_SPEED = 3.1F;
    private static final float DART_INACCURACY = 1.2F;

    /** 剧毒镖箭一梭子几只（用户指定 3~4）。 */
    private static final int MIN_DARTS = 3;
    private static final int MAX_DARTS = 4;

    /** 惊雷飞刀的弹速与散布：照 {@code LightningKnifeItem#use} 的 {@code shootFromRotation(..., 0.8F, 1.0F)}。 */
    private static final float KNIFE_SPEED = 0.8F;
    private static final float KNIFE_INACCURACY = 1.0F;

    /** 惊雷飞刀一梭子几只（用户指定 2~3）。 */
    private static final int MIN_KNIVES = 2;
    private static final int MAX_KNIVES = 3;

    /** 凤舞长弓的弹速与散布：原版满蓄力弓（3 格/刻、散布 1.0）。 */
    private static final float PHOENIX_ARROW_SPEED = 3.0F;
    private static final float PHOENIX_ARROW_INACCURACY = 1.0F;

    /** 凤舞长弓一梭子几支（用户指定 1~2）。 */
    private static final int MIN_PHOENIX_ARROWS = 1;
    private static final int MAX_PHOENIX_ARROWS = 2;

    /** 创始者飞锤的出膛速度与散布：照 {@code HammerOfKingbdogzItem#use} 的 {@code shoot(..., 3.0F, 1.0F)}。 */
    private static final float HAMMER_SPEED = 3.0F;
    private static final float HAMMER_INACCURACY = 1.0F;

    // ===== 事件实例（都是本 mod 的类型，可以安全地作为静态字段） =====
    private static PossessedAttackEvent rangedPoisonDart;
    private static PossessedAttackEvent rangedLightningKnife;
    private static PossessedAttackEvent rangedPhoenixBow;
    private static PossessedAttackEvent rangedHammer;
    private static PossessedAttackEvent meleeGravititeSword;
    private static PossessedAttackEvent meleeLightningSword;
    private static PossessedAttackEvent meleeVampireBlade;
    private static PossessedAttackEvent meleeCloudStaff;

    private AetherCompat() {
    }

    /** 把天境的远程手段加进远程池（只在装了天境时调用）。 */
    static void addRangedEvents(List<PossessedAttackEvent> list) {
        rangedPoisonDart = new PoisonDartEvent();
        rangedLightningKnife = new LightningKnifeEvent();
        rangedPhoenixBow = new PhoenixBowEvent();
        rangedHammer = new HammerEvent();
        list.add(rangedPoisonDart);
        list.add(rangedLightningKnife);
        list.add(rangedPhoenixBow);
        list.add(rangedHammer);
    }

    /** 把天境的近战手段加进近战池（只在装了天境时调用）。 */
    static void addMeleeEvents(List<PossessedAttackEvent> list) {
        meleeGravititeSword = new AetherSwordEvent(SwordKind.GRAVITITE);
        meleeLightningSword = new AetherSwordEvent(SwordKind.LIGHTNING);
        meleeVampireBlade = new AetherSwordEvent(SwordKind.VAMPIRE);
        meleeCloudStaff = new CloudStaffEvent();
        list.add(meleeGravititeSword);
        list.add(meleeLightningSword);
        list.add(meleeVampireBlade);
        list.add(meleeCloudStaff);
    }

    // ====================== 每刻调度（境云权杖那套"左键"） ======================

    /** 一串还没打完的"左键"：谁打的、打谁、还剩几次、下一次在第几刻。 */
    private record Volley(ServerLevel level, UUID shellUuid, UUID targetUuid, int remaining, long nextTick) {
    }

    private static final List<Volley> VOLLEYS = new ArrayList<>();

    /**
     * 境云权杖那套动作的推进（由 {@code ModMain} 每服务端刻调用一次；
     * 没装天境时列表恒空，本方法就是空转）。
     */
    public static void tick() {
        if (VOLLEYS.isEmpty()) {
            return;
        }
        java.util.ListIterator<Volley> iterator = VOLLEYS.listIterator();
        while (iterator.hasNext()) {
            Volley volley = iterator.next();
            ServerLevel level = volley.level();
            if (level.getGameTime() < volley.nextTick()) {
                continue;
            }
            Entity shellEntity = level.getEntity(volley.shellUuid());
            Entity targetEntity = level.getEntity(volley.targetUuid());
            if (!(shellEntity instanceof LivingEntity shell) || !shell.isAlive()
                || !(targetEntity instanceof LivingEntity target) || !target.isAlive()
                || !canStillAttack(shell)) {
                iterator.remove();
                continue;
            }
            CloudStaffEvent.swingAndShoot(level, shell, target);
            int left = volley.remaining() - 1;
            if (left <= 0) {
                iterator.remove();
            } else {
                iterator.set(new Volley(level, volley.shellUuid(), volley.targetUuid(), left,
                    level.getGameTime() + CLOUD_STAFF_SWING_INTERVAL_TICKS));
            }
        }
    }

    /**
     * 取消某具空壳名下还没打完的权杖连击（{@code NoAI} 冻住、附体结束等）。
     * <p>
     * 与 {@link OrbBurstShots#cancel} 同理：这套动作是<b>跨刻</b>的（4~6 次、每次隔 10 刻），
     * "不许出手"必须能把队列一起清掉，否则收场之后它还会在原地挥空。
     */
    public static void cancelVolleys(LivingEntity shell) {
        UUID id = shell.getUUID();
        VOLLEYS.removeIf(volley -> volley.shellUuid().equals(id));
    }

    /** 这具空壳现在还能不能出手（{@code NoAI} 时不能；不是附体空壳时不管）。 */
    private static boolean canStillAttack(LivingEntity shell) {
        return !(shell instanceof PlayerShellEntity playerShell) || playerShell.canPossessAttack();
    }

    /** 起一串"左键"（第一次由调用方当场打完，这里只排剩下的几次）。 */
    private static void startVolley(ServerLevel level, LivingEntity shell, LivingEntity target) {
        int swings = CLOUD_STAFF_MIN_SWINGS
            + level.getRandom().nextInt(CLOUD_STAFF_MAX_SWINGS - CLOUD_STAFF_MIN_SWINGS + 1);
        VOLLEYS.removeIf(volley -> volley.shellUuid().equals(shell.getUUID()));
        if (swings <= 1) {
            return;
        }
        VOLLEYS.add(new Volley(level, shell.getUUID(), target.getUUID(), swings - 1,
            level.getGameTime() + CLOUD_STAFF_SWING_INTERVAL_TICKS));
    }

    // ====================== 近战：三把天境剑 ======================

    /** 三把天境剑（引用天境类的代码只在这个枚举的方法体里）。 */
    private enum SwordKind {
        GRAVITITE, LIGHTNING, VAMPIRE;

        net.minecraft.world.item.Item item() {
            return switch (this) {
                case GRAVITITE -> AetherItems.GRAVITITE_SWORD.get();
                case LIGHTNING -> AetherItems.LIGHTNING_SWORD.get();
                case VAMPIRE -> AetherItems.VAMPIRE_BLADE.get();
            };
        }

        String label() {
            return switch (this) {
                case GRAVITITE -> "重力晶剑";
                case LIGHTNING -> "疾电长剑";
                case VAMPIRE -> "吸血魔剑";
            };
        }
    }

    /**
     * 近战：手持某把天境剑贴身劈砍。
     * <p>
     * 伤害与特效都走 {@link OrbPossessedAttackEvents#swingWeapon} ——
     * 那里会显式调一次 {@code Item#hurtEnemy}，三把剑的招牌效果（挑飞 / 落雷 / 回血）
     * 正是写在天境的 {@code hurtEnemy} 里，所以这里不需要各写一遍。
     * <p>
     * 不给附魔：天境这几把剑本体就没有附魔（用户要求"照天境源码来"），
     * 白板伤害 = 空壳基础 1 + 剑自身的攻击力加成 6（{@code SwordItem.createAttributes(tier, 3.0F, ...)}
     * 再叠该材质固有的 +3）= <b>7 点</b>。
     */
    private static final class AetherSwordEvent implements PossessedAttackEvent {

        private final SwordKind kind;

        AetherSwordEvent(SwordKind kind) {
            this.kind = kind;
        }

        @Override
        public int windUpTicks() {
            return MELEE_WIND_UP_TICKS;
        }

        @Override
        public int cooldownTicks() {
            return MELEE_COOLDOWN_TICKS;
        }

        @Override
        public boolean canRun(LivingEntity attacker, LivingEntity target) {
            return attacker.distanceToSqr(target)
                <= OrbPossessedAttackEvents.MELEE_RANGE * OrbPossessedAttackEvents.MELEE_RANGE;
        }

        @Override
        public boolean shouldPauseMovement(LivingEntity attacker, LivingEntity target) {
            // 够得着才站住；够不着就继续走位（和其它近战一个规矩）
            return this.canRun(attacker, target);
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(this.kind.item()));
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            OrbPossessedAttackEvents.swingWeapon(attacker, target, new ItemStack(this.kind.item()));
        }
    }

    // ====================== 远程：剧毒镖箭（连发） ======================

    /**
     * 远程：<b>剧毒镖箭发射器 + 剧毒镖箭</b>，每 1~2gt 吹出一只毒镖，一梭子 3~4 只。
     * <p>
     * 天境里毒镖是<b>弹药</b>（{@code PoisonDartItem} 是 {@code ArrowItem}，自己没有"使用"动作），
     * 要吹出去必须有把发射器 —— 所以主手亮的是 {@code poison_dart_shooter}、
     * 副手亮出毒镖，和模组里海晶弓"主手弓、副手箭"的做法一致。
     * <p>
     * 拉弓姿态用的是天境那把发射器自己的 {@code UseAnim.BOW}
     * （{@code DartShooterItem#getUseAnimation}，使用时长 10 刻）。
     */
    private static final class PoisonDartEvent implements PossessedAttackEvent {

        @Override
        public int windUpTicks() {
            return BOW_WIND_UP_TICKS;
        }

        @Override
        public int cooldownTicks() {
            return RANGED_COOLDOWN_TICKS;
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            attacker.setItemInHand(InteractionHand.MAIN_HAND, dartShooter());
            attacker.setItemInHand(InteractionHand.OFF_HAND, dartItem());
            attacker.startUsingItem(InteractionHand.MAIN_HAND);
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            OrbBurstShots.start(level, attacker, target, this::fireOne, MIN_DARTS, MAX_DARTS);
        }

        @Override
        public void endWindUp(LivingEntity attacker) {
            attacker.stopUsingItem();
        }

        /** 吹出一只毒镖：速度/散布/无重力照 {@code DartShooterItem}，音效用天境的"吹箭"声。 */
        private void fireOne(ServerLevel level, LivingEntity shooter, LivingEntity target, Vec3 direction) {
            Vec3 from = shooter.getEyePosition();
            // "发射武器"传手上那把发射器：天境的伤害结算会用它算附魔加成（没有附魔就是白板）
            ItemStack weapon = shooter.getMainHandItem().is(AetherItems.POISON_DART_SHOOTER.get())
                ? shooter.getMainHandItem() : dartShooter();
            PoisonDart dart = new PoisonDart(level, shooter, dartItem(), weapon);
            dart.setNoGravity(true);   // DartShooterItem#createProjectile 里那一句
            dart.setPos(from.x, from.y - 0.1, from.z);
            dart.shoot(direction.x, direction.y, direction.z, DART_SPEED, DART_INACCURACY);
            level.addFreshEntity(dart);
            level.playSound(null, from.x, from.y, from.z,
                AetherSoundEvents.ITEM_DART_SHOOTER_SHOOT.get(), SoundSource.HOSTILE, 1.0F,
                1.0F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        }

        /** 一把剧毒镖箭发射器（引用天境类的代码只在方法体里）。 */
        private static ItemStack dartShooter() {
            return new ItemStack(AetherItems.POISON_DART_SHOOTER.get());
        }

        /** 一只剧毒镖箭（弹药）。 */
        private static ItemStack dartItem() {
            return new ItemStack(AetherItems.POISON_DART.get());
        }
    }

    // ====================== 远程：惊雷飞刀（连发） ======================

    /**
     * 远程：每 1~2gt 甩出一只 {@code ThrownLightningKnife}，一梭子 2~3 只。
     * <p>
     * 速度 0.8、散布 1.0 照 {@code LightningKnifeItem#use}；飞刀命中后会劈下一道雷
     * （{@code ThrownLightningKnife#onHit} 里调的 {@code EntityUtil#summonLightningFromProjectile}），
     * 这一段是天境弹体自己的逻辑，我们只管把它甩出去。
     */
    private static final class LightningKnifeEvent implements PossessedAttackEvent {

        @Override
        public int windUpTicks() {
            return THROW_WIND_UP_TICKS;
        }

        @Override
        public int cooldownTicks() {
            return KNIFE_COOLDOWN_TICKS;
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            attacker.setItemInHand(InteractionHand.MAIN_HAND, knifeStack());
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            // 掷出动作：挥一下手臂（天境那边飞刀是右键甩出去的，这里用挥臂代替）
            attacker.swing(InteractionHand.MAIN_HAND);
            OrbBurstShots.start(level, attacker, target, this::fireOne, MIN_KNIVES, MAX_KNIVES);
        }

        /** 甩出一只飞刀：速度/散布照 {@code LightningKnifeItem}，音效用天境的"飞刀"声。 */
        private void fireOne(ServerLevel level, LivingEntity shooter, LivingEntity target, Vec3 direction) {
            Vec3 from = shooter.getEyePosition();
            ThrownLightningKnife knife = new ThrownLightningKnife(shooter, level);
            knife.setPos(from.x, from.y - 0.1, from.z);
            knife.shoot(direction.x, direction.y, direction.z, KNIFE_SPEED, KNIFE_INACCURACY);
            level.addFreshEntity(knife);
            level.playSound(null, from.x, from.y, from.z,
                AetherSoundEvents.ITEM_LIGHTNING_KNIFE_SHOOT.get(), SoundSource.HOSTILE, 1.0F,
                1.0F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        }

        /** 一把惊雷飞刀（天境那边一叠 16 把，这里只做手持外观）。 */
        private static ItemStack knifeStack() {
            return new ItemStack(AetherItems.LIGHTNING_KNIFE.get());
        }
    }

    // ====================== 远程：凤舞长弓（1~2 支火焰箭） ======================

    /**
     * 远程：拉起<b>凤舞长弓</b>，射出 1~2 支火焰箭。
     * <p>
     * 天境本体是 {@code PhoenixBowItem extends BowItem}，箭被 {@code customArrow} 打上
     * "凤凰箭"标记后由 {@code AbilityHooks#phoenixArrowHit} <b>点燃目标 20 秒</b>；
     * 这里用 {@link OrbPhoenixArrow} 复刻同一效果（含"末影人豁免"）。
     */
    private static final class PhoenixBowEvent implements PossessedAttackEvent {

        @Override
        public int windUpTicks() {
            return BOW_WIND_UP_TICKS;
        }

        @Override
        public int cooldownTicks() {
            return RANGED_COOLDOWN_TICKS;
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            attacker.setItemInHand(InteractionHand.MAIN_HAND, bow());
            attacker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW));
            attacker.startUsingItem(InteractionHand.MAIN_HAND);
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            OrbBurstShots.start(level, attacker, target, this::fireOne,
                MIN_PHOENIX_ARROWS, MAX_PHOENIX_ARROWS);
        }

        @Override
        public void endWindUp(LivingEntity attacker) {
            attacker.stopUsingItem();
        }

        /**
         * 射出一支火箭：满蓄力弓的速度与散布；箭本身带火、命中再点燃目标 20 秒
         * （见 {@link OrbPhoenixArrow}）。"发射武器"传手上那把凤舞长弓。
         */
        private void fireOne(ServerLevel level, LivingEntity shooter, LivingEntity target, Vec3 direction) {
            Vec3 from = shooter.getEyePosition();
            ItemStack weapon = shooter.getMainHandItem().is(AetherItems.PHOENIX_BOW.get())
                ? shooter.getMainHandItem() : bow();
            OrbPhoenixArrow arrow = new OrbPhoenixArrow(level, shooter, new ItemStack(Items.ARROW), weapon);
            arrow.setPos(from.x, from.y - 0.1, from.z);
            arrow.shoot(direction.x, direction.y, direction.z,
                PHOENIX_ARROW_SPEED, PHOENIX_ARROW_INACCURACY);
            level.addFreshEntity(arrow);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.ARROW_SHOOT,
                SoundSource.HOSTILE, 1.0F, 1.2F);
        }

        /** 一把凤舞长弓。 */
        private static ItemStack bow() {
            return new ItemStack(AetherItems.PHOENIX_BOW.get());
        }
    }

    // ====================== 远程：创始者飞锤（一次） ======================

    /**
     * 远程：<b>掷出一枚创始者飞锤</b>（一次就一枚）。
     * <p>
     * 照 {@code HammerOfKingbdogzItem#use}：飞锤无重力、出膛 3.0 格/刻、散布 1.0；
     * 命中实体 7 点伤害并上挑，命中方块则把 5 格内的所有实体一起挑飞
     * （{@code HammerProjectile#onHitBlock}）。锤子自己会记住主人，所以不会砸到扔它的空壳。
     */
    private static final class HammerEvent implements PossessedAttackEvent {

        @Override
        public int windUpTicks() {
            return THROW_WIND_UP_TICKS;
        }

        @Override
        public int cooldownTicks() {
            return HAMMER_COOLDOWN_TICKS;
        }

        /**
         * <b>带多重射击效果时不再掷飞锤</b>（用户指定）。
         * <p>
         * 飞锤是<b>弹射物</b>，会吃到 {@code ModMain} 那条"弹射物加入世界即按多重射击公式分裂"
         * （见 {@code spawnMultishotCopies}）：本来"这套动作一次只掷一枚"的飞锤，一到手就成了
         * 3~7 枚齐射 —— 既不是这张签的本意，威力也离谱。所以这一刻直接判成"用不了"：
         * 抽签会<b>跳过</b>它去选别的远程攻击（而不是把远程窗口整个空掉，那是 {@code canRun}
         * 返回 false 时 {@code drawFrom} 的正常行为）。
         *
         * <p>"检测到天境"这一条是结构性保证：本类只在装了天境时才会被碰到
         * （门在 {@code OptionalMods.isAetherLoaded()}，见 {@code ModMain} 的各调用点），
         * 没装天境时压根没有这把飞锤可掷。
         */
        @Override
        public boolean canRun(LivingEntity attacker, LivingEntity target) {
            return !ModMain.hasMultishot(attacker);
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            attacker.setItemInHand(InteractionHand.MAIN_HAND, hammer());
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
            attacker.setItemInHand(InteractionHand.MAIN_HAND, hammer());
            attacker.swing(InteractionHand.MAIN_HAND);

            HammerProjectile hammer = new HammerProjectile(attacker, level);
            hammer.setPos(from.x, from.y - 0.1, from.z);
            hammer.shoot(direction.x, direction.y, direction.z, HAMMER_SPEED, HAMMER_INACCURACY);
            level.addFreshEntity(hammer);
            level.playSound(null, from.x, from.y, from.z,
                AetherSoundEvents.ITEM_HAMMER_OF_KINGBDOGZ_SHOOT.get(), SoundSource.HOSTILE, 1.0F,
                1.0F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        }

        /** 一把创始者飞锤。 */
        private static ItemStack hammer() {
            return new ItemStack(AetherItems.HAMMER_OF_KINGBDOGZ.get());
        }
    }

    // ====================== 近战：境云权杖（用一次 + 4~6 次"左键"） ======================

    /**
     * 近战：举起<b>境云权杖</b>用一次，随后朝目标"左键" 4~6 次。
     * <p>
     * 天境本体（{@code CloudStaffItem}）是"右键召出左右两只云从者 → 之后每次挥手
     * （{@code onEntitySwing}）让它们各射一发云水晶"。云从者那两只实体<b>没法照搬</b>
     * （它的 {@code getOwner()} 硬转 {@code Player}，主人是空壳会每刻抛异常），
     * 所以这里保留同一套节奏与同样的弹药：用一次 → 每 10 刻挥一次手臂、
     * 每次从两肩位置各射出一发 {@code CloudCrystal}。
     * <p>
     * 归在<b>近战</b>类（用户指定），所以和其它近战一样要求目标在
     * {@link OrbPossessedAttackEvents#MELEE_RANGE} 格内，并且起手期间站定施法。
     */
    private static final class CloudStaffEvent implements PossessedAttackEvent {

        /** 举杖起手刻数：0.5 秒，够看清手里那把权杖。 */
        private static final int WIND_UP_TICKS = 10;

        @Override
        public int windUpTicks() {
            return WIND_UP_TICKS;
        }

        @Override
        public int cooldownTicks() {
            return CLOUD_STAFF_COOLDOWN_TICKS;
        }

        @Override
        public boolean canRun(LivingEntity attacker, LivingEntity target) {
            return attacker.distanceToSqr(target)
                <= OrbPossessedAttackEvents.MELEE_RANGE * OrbPossessedAttackEvents.MELEE_RANGE;
        }

        @Override
        public boolean shouldPauseMovement(LivingEntity attacker, LivingEntity target) {
            // 施法：够得着就站定、面朝目标（用户要求"面向目标"）
            return this.canRun(attacker, target);
        }

        @Override
        public void beginWindUp(LivingEntity attacker) {
            attacker.setItemInHand(InteractionHand.MAIN_HAND, staff());
        }

        @Override
        public void run(LivingEntity attacker, LivingEntity target) {
            if (!(attacker.level() instanceof ServerLevel level)) {
                return;
            }
            attacker.setItemInHand(InteractionHand.MAIN_HAND, staff());
            attacker.swing(InteractionHand.MAIN_HAND);        // "用一次"
            summonParticles(level, attacker);
            swingAndShoot(level, attacker, target);            // 第 1 次"左键"
            startVolley(level, attacker, target);              // 剩下的 3~5 次排队
        }

        /** 一把境云权杖。 */
        private static ItemStack staff() {
            return new ItemStack(AetherItems.CLOUD_STAFF.get());
        }

        /**
         * 挥一次手臂 + 让"云从者"各射一发云水晶。
         * <p>
         * 位置照 {@code CloudMinion#setPositionFromOwner}：主人两侧 1.05 格、抬高 1.0 格；
         * 弹药照 {@code CloudMinion#tick}：{@code CloudCrystal}、速度 1.0，
         * 并配上音效 {@code aether:entity.cloud_minion.shoot}（天境那边是云从者自己拨的）。
         *
         * <h2>瞄准：每一发都从"它自己那个肩膀"指向目标</h2>
         * 天境本体是让云从者沿<b>主人朝向</b>开火（云从者没有自己的目标），两发于是<b>平行</b>飞出去、
         * 各自偏向一侧 1.05 格 —— 照搬到空壳身上就是"往两侧散开、打不着目标"（用户实测反馈）。
         * 这里改成：先取目标该瞄的那个点（{@code aimPointOf}，九头蛇那种瞄头的特例也一并生效），
         * 再<b>从每一发的出生点</b>算方向，于是两发会自然收拢到同一个点上；
         * 散布同时归零（见 {@link #CLOUD_CRYSTAL_INACCURACY}）。
         * <p>
         * <b>为什么它在本嵌套类里、而不是外层类里</b>：外层类必须做到"即使被误加载也不碰天境类型"
         * （见类注释里那条 {@code NoClassDefFoundError} 崩溃的教训），所以凡是 {@code new CloudCrystal}
         * 这类语句一律关进嵌套类。
         */
        static void swingAndShoot(ServerLevel level, LivingEntity shell, LivingEntity target) {
            Vec3 aimPoint = OrbPossessedAttackEvents.aimPointOf(target);
            for (int side = 0; side < 2; side++) {
                boolean right = side == 0;
                Vec3 from = cloudMinionPosition(shell, right);
                Vec3 direction = aimPoint.subtract(from);
                if (direction.lengthSqr() < 1.0E-6) {
                    continue;   // 肩膀和目标重合（贴脸到极致）：这一发没方向，跳过
                }
                direction = direction.normalize();
                CloudCrystal crystal = new CloudCrystal(level);
                crystal.setPos(from.x, from.y, from.z);
                crystal.setOwner(shell);
                crystal.shoot(direction.x, direction.y, direction.z,
                    CLOUD_CRYSTAL_SPEED, CLOUD_CRYSTAL_INACCURACY);
                level.addFreshEntity(crystal);
                level.playSound(null, from.x, from.y, from.z,
                    AetherSoundEvents.ENTITY_CLOUD_MINION_SHOOT.get(), SoundSource.HOSTILE, 0.75F,
                    (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2F + 1.0F);
            }
        }
    }

    /**
     * 某一侧"云从者"该待的位置：照 {@code CloudMinion#setPositionFromOwner} 的公式
     * （主人朝向左右各偏 90°、水平 1.05 格，高度 = 主人脚底 + 1.0）。
     */
    private static Vec3 cloudMinionPosition(LivingEntity shell, boolean right) {
        double yaw = shell.getYRot() + (right ? -90.0 : 90.0);
        yaw /= -180.0F / (float) Math.PI;
        return new Vec3(
            shell.getX() + Math.sin(yaw) * CLOUD_MINION_SIDE_OFFSET,
            shell.getY() + CLOUD_MINION_HEIGHT_OFFSET,
            shell.getZ() + Math.cos(yaw) * CLOUD_MINION_SIDE_OFFSET);
    }

    /** 权杖"用一次"那一下的召唤粒子（照天境 {@code EntityUtil#spawnSummoningExplosionParticles}：20 个 POOF）。 */
    private static void summonParticles(ServerLevel level, LivingEntity shell) {
        level.sendParticles(ParticleTypes.POOF,
            shell.getX(), shell.getY() + shell.getBbHeight() * 0.5D, shell.getZ(),
            20, 0.2D, 0.2D, 0.2D, 0.02D);
    }

    // ====================== 调试辅助 ======================

    /**
     * 某张签的中文名（给 {@code /jafa orbinfo} 的"抽签"字段用）；不是本类的签返回 null。
     * <p>
     * 抽签结果原本只显示类的简单名（{@code AetherSwordEvent} 之类），
     * 三把天境剑还共用一个类，光看类名分不出手里是哪把 —— 所以补一个中文名。
     */
    @Nullable
    public static String labelOf(PossessedAttackEvent event) {
        if (event instanceof AetherSwordEvent sword) {
            return sword.kind.label();
        }
        if (event == rangedPoisonDart) {
            return "剧毒镖箭";
        }
        if (event == rangedLightningKnife) {
            return "惊雷飞刀";
        }
        if (event == rangedPhoenixBow) {
            return "凤舞长弓";
        }
        if (event == rangedHammer) {
            return "创始者飞锤";
        }
        if (event == meleeCloudStaff) {
            return "境云权杖";
        }
        return null;
    }
}
