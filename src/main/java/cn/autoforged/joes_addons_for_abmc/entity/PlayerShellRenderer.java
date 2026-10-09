package cn.autoforged.joes_addons_for_abmc.entity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家空壳的渲染器：与玩家渲染同套机制（继承 LivingEntityRenderer），
 * 因此模型朝向、缩放、阴影、位置计算与真实玩家完全一致，不会再出现颠倒/幼体/浮空问题。
 *
 * <h2>皮肤：整套交回原版（用户指定：改用 {@link SkinManager}）</h2>
 * 以前是"自己解析名字 → 自己下 PNG → 自己注册 {@code DynamicTexture}"，实测有三处硬伤，
 * 正好对应玩家反馈的两个症状（<b>某些特定皮肤显示故障</b> / <b>好好的皮肤突然故障</b>）：
 * <ol>
 *   <li><b>64×32 旧版皮肤没做布局转换</b>。原版拿到皮肤图后<b>必须</b>过一道
 *       {@code HttpTexture#processLegacySkin}：把旧版布局的肢体复制到现代布局的左臂/左腿槽位、
 *       再把头部/躯干/四肢的 alpha 强制成 255（{@code setNoAlpha}）、顺带处理旧版帽子层
 *       （{@code doNotchTransparencyHack}）。自己下载就等于整道跳过 —— 现代模型的左臂
 *       {@code texOffs(32,48)}、左腿 {@code texOffs(16,48)} 的 v 落在 48..63，而图只有 32 高，
 *       于是<b>左侧两条肢体取到图外的行</b>（GL 默认 REPEAT 会绕回上半张图取到躯干的贴图）→ 花图。
 *       实测：{@code MHF_Herobrine} 的官方皮肤正是 64×32（1137 字节、与 Mojang 纹理 URL 的哈希一致）；
 *       同一张图在真人身上一切正常，就是因为原版替他做了这步转换。</li>
 *   <li><b>粗臂/细臂判定是死的</b>。以前那套"抽 x48..51, y20..31 看透明像素"实测对任何皮肤都不透明
 *       （Dream、MHF_Steve、Technoblade、MHF_Herobrine 全是 0/48），于是永远判成粗臂；
 *       真正的判据是<b>右臂 x54..55 / 左臂 x46..47 整列透明</b>（细臂皮肤实测 16/16 透明）。
 *       结果就是细臂皮肤（Dream、MHF_Steve —— Dream 还在刷怪蛋的随机名单里）一旦查不到元数据
 *       就被套上粗臂模型 → 手臂错位/拉伸。</li>
 *   <li><b>周期性重新下载</b>。原来"该不该重试"的判断排在"是不是已经就绪"<b>前面</b>，
 *       而且成功时间戳不刷新（只在开工时写），于是首次加载成功 10 秒之后，每一帧都会重新下载，
 *       并在下完之前一直返回默认皮肤 → 好好的皮肤<b>每隔 10 秒闪成原版默认皮肤</b>；
 *       更糟的是这一轮里粗/细臂会被重算，元数据若查询失败，细臂会直接翻成粗臂。
 *       重下还会把同一个纹理路径重新注册一遍，而 {@code TextureManager#register} 会
 *       {@code safeClose} 掉旧纹理（删掉 GL id）→ 那一轮就是缺图/白块。</li>
 * </ol>
 * 现在只做一件事：<b>名字 → UUID → {@link GameProfile} → {@link SkinManager#getOrLoad}</b>。
 * 纹理下载、64×32 转换、alpha 修补、粗/细臂（以档案元数据为权威）、{@code assets/skins/} 磁盘缓存、
 * 按皮肤哈希去重，全部由原版负责 —— 也就是渲染真人玩家用的同一套代码。
 * <p>
 * 在线玩家更进一步：{@link PlayerInfo#getSkin()} 直接用原版"玩家列表皮肤"
 * （{@code PlayerRenderer} 每帧取的就是它），<b>一次 HTTP 都不用</b>。
 * 名字不是在线玩家时（离线玩家、刷怪蛋随机名/自定义名）才走上面的名字解析，
 * 未就绪期间用 {@link LoadedSkin#fallback} 顶着 —— 它按<b>名字</b>的离线 UUID 生成，
 * 同一个名字永远得到同一张，所以不会再出现"默认皮肤与真皮肤来回闪"。
 */
@OnlyIn(Dist.CLIENT)
public class PlayerShellRenderer extends LivingEntityRenderer<PlayerShellEntity, PlayerModel<PlayerShellEntity>> {
    private static final Logger LOGGER = LoggerFactory.getLogger("PlayerShellRenderer");

    /**
     * 真人玩家的模型缩放：原版 {@code PlayerRenderer#scale} 里的 <b>0.9375</b>（见 {@link #scale}）。
     */
    private static final float PLAYER_MODEL_SCALE = 0.9375F;

    /**
     * 解析失败后的首次重试间隔（毫秒），之后按 {@link #RETRY_MAX_MS} 逐次翻倍退避。
     * <p>
     * 只对<b>网络类</b>失败生效：名字明确不存在（HTTP 404/204）时永久放弃，因为再查也没用。
     */
    private static final long RETRY_MIN_MS = 30_000L;

    /** 重试间隔的上限（毫秒）。 */
    private static final long RETRY_MAX_MS = 300_000L;

    private final PlayerModel<PlayerShellEntity> slimModel;
    private final PlayerModel<PlayerShellEntity> wideModel;

    /** 每帧使用的皮肤（在 render 中解析，供 getTextureLocation 复用）。 */
    @Nullable
    private PlayerSkin currentSkin;

    /** 按玩家名缓存解析状态，避免每帧重复发起请求（同一名字的所有空壳共用一份）。 */
    private static final Map<String, LoadedSkin> SKIN_CACHE = new HashMap<>();

    /**
     * 已经就"皮肤名为空"提示过的实体。
     * <p>
     * 这个分支每帧都会走到（{@code /summon} 出来的空壳默认就没有皮肤名），不加这道闸门
     * 就是<b>每帧一行 INFO</b>，会把真正的诊断信息淹掉。
     */
    private static final Set<UUID> BLANK_SKIN_LOGGED = new HashSet<>();

    /** 在线玩家路径上次记录的纹理，用来只在"皮肤变了"（默认 → 真皮肤）时打一行日志。 */
    private static final Map<String, ResourceLocation> ONLINE_SKIN_LOGGED = new HashMap<>();

    /**
     * 一个玩家名的皮肤解析状态。
     * <p>
     * 三条不变式（都是为了避免"闪"）：
     * <ul>
     *   <li>{@link #fallback} 在构造时就定下来 —— 它按<b>名字</b>的离线 UUID 生成，
     *       同一个名字永远得到同一张原版默认皮肤（Steve/Alex 那一套 18 张之一）；</li>
     *   <li>{@link #resolved} 一旦非空就<b>只增不改</b>，之后的每一帧都直接返回它
     *       （"就绪"的判定必须在最前面，见类注释第 3 条）；</li>
     *   <li>名字确定不存在 → {@link #permanentFailure}，不再联网；网络类失败 →
     *       按 {@link #nextAttemptAt} 退避重试。</li>
     * </ul>
     */
    private static class LoadedSkin {
        final String name;
        /** 未就绪时的兜底皮肤（按名字的离线 UUID，稳定不变）。 */
        final PlayerSkin fallback;
        /** 原版皮肤管线解析出来的皮肤；null = 还没好。 */
        volatile PlayerSkin resolved;
        /** 一次解析是否在飞行中（防止每帧重复发起；直到结果落地才复位）。 */
        private volatile boolean running;
        /** 名字确定不存在：永久停在兜底皮肤，不再联网。 */
        private volatile boolean permanentFailure;
        /** 下一次允许尝试的时刻（毫秒）。 */
        private volatile long nextAttemptAt;
        /** 当前的重试间隔（指数退避用）。 */
        private volatile long retryDelay = RETRY_MIN_MS;

        LoadedSkin(String name) {
            this.name = name;
            this.fallback = DefaultPlayerSkin.get(opaqueUuid(name));
        }

        /** 首次（或退避到期后）起一次后台解析；正在跑/已就绪/永久失败则什么都不做。 */
        void ensureStarted() {
            if (this.running || this.resolved != null || this.permanentFailure) {
                return;
            }
            if (System.currentTimeMillis() < this.nextAttemptAt) {
                return;
            }
            this.running = true;
            Util.backgroundExecutor().execute(this::resolve);
        }

        /** 后台线程：名字 → UUID → 玩家档案 → 交给原版 {@link SkinManager}。 */
        private void resolve() {
            try {
                NameLookup lookup = resolveUuid(this.name);
                if (lookup.uuid() == null) {
                    if (lookup.unknownName()) {
                        // Mojang 明确回答"没有这个人"：再查一万次也没用，永久停在兜底皮肤
                        this.permanentFailure = true;
                        this.running = false;
                        LOGGER.info("[PlayerShell] 玩家名不存在，停在默认皮肤: '{}'", this.name);
                    } else {
                        this.fail("名字解析失败（网络）");
                    }
                    return;
                }
                Minecraft mc = Minecraft.getInstance();
                ProfileResult result = mc.getMinecraftSessionService()
                    .fetchProfile(toUuid(lookup.uuid()), false);
                GameProfile profile = result == null ? null : result.profile();
                if (profile == null) {
                    this.fail("拉取玩家档案失败");
                    return;
                }
                if (!profile.getProperties().containsKey("textures")) {
                    // 档案里没有皮肤属性（该账号确实没设皮肤）：原版会给它自己的默认皮肤，
                    // 这就是最终答案，别再重试了
                    this.resolved = DefaultPlayerSkin.get(profile);
                    this.running = false;
                    LOGGER.info("[PlayerShell] 档案里没有皮肤属性，使用默认皮肤: '{}'", this.name);
                    return;
                }
                // 关键一步：交给原版皮肤管线。
                // 这里在后台线程调用是安全的：SkinManager 把"注册纹理"那一步丢回主线程执行
                // （它的构造参数 executor 就是 Minecraft 自己，见 Minecraft 构造器里
                // new SkinManager(textureManager, ..., this)），下载本身再由 HttpTexture
                // 丢一次后台线程。也就是说：转换/上传全在原版该在的线程上。
                mc.getSkinManager().getOrLoad(profile).whenComplete((skin, error) -> {
                    if (skin != null) {
                        this.resolved = skin;
                        this.running = false;
                        LOGGER.info("[PlayerShell] 皮肤就绪（原版管线）: '{}' 模型={} 纹理={}",
                            this.name, skin.model().id(), skin.texture());
                    } else {
                        this.fail("原版皮肤管线失败: " + error);
                    }
                });
            } catch (Exception e) {
                this.fail("解析异常: " + e);
            }
        }

        /** 这次解析没成：复位飞行标记并按指数退避安排下次。 */
        private void fail(String reason) {
            this.running = false;
            long delay = this.retryDelay;
            this.retryDelay = Math.min(RETRY_MAX_MS, delay * 2L);
            this.nextAttemptAt = System.currentTimeMillis() + delay;
            LOGGER.info("[PlayerShell] 皮肤解析失败，{} 秒后重试: '{}'（{}）",
                delay / 1000L, this.name, reason);
        }
    }

    public PlayerShellRenderer(EntityRendererProvider.Context context) {
        super(context,
            new PlayerModel<>(context.getModelSet().bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.slimModel = new PlayerModel<>(context.getModelSet().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.wideModel = new PlayerModel<>(context.getModelSet().bakeLayer(ModelLayers.PLAYER), false);
        // 手持物品：与 HumanoidMobRenderer / PlayerRenderer 同款图层，按手臂姿态摆在手上
        // （附体空壳的武器外观全靠它：闪烁西瓜刀、海晶弓+海晶箭、三叉戟）
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        // 翅膀（{@code joes_addons_for_abmc:wings}）穿在胸甲栏时画在背上，并按"是否离地"正弦扇动 ——
        // 附体特攻"飞行"就是靠它看得见（见 OrbPossessedAttackEvents.FlightEvent）。
        this.addLayer(new cn.autoforged.joes_addons_for_abmc.client.WingsLayer<>(this, context.getModelSet()));
        // 鞘翅：附体特攻"重锤"的爬升/俯冲阶段胸甲栏穿的是鞘翅。
        // 这里<b>不用</b>原版 ElytraLayer —— 它只认 entity.isFallFlying()，而空壳在客户端拿不到那个标记，
        // 结果就是"身体躺平了、翅膀却还收着"。改用 ShellElytraLayer：它同时认同步过来的
        // Pose.FALL_FLYING，只要身体躺平就一定张开（见该类的注释）。
        this.addLayer(new cn.autoforged.joes_addons_for_abmc.client.ShellElytraLayer<>(this, context.getModelSet()));
        // 附体形态头顶那颗幸运核心光球：<b>模型空间的渲染层</b>（用户指定），
        // 挂在头部那一节上，所以头看向哪、身体是站是躺（重锤滑翔）它都紧贴头顶。
        // 以前是在 render() 里事后按"身体朝向 + 硬编码头顶高度"补画一颗公告板，见该层注释。
        this.addLayer(new cn.autoforged.joes_addons_for_abmc.client.ShellHeadOrbLayer(this));
        // 盔甲：重锤动作中途换上的钻石胸甲在这里显示。
        // （护甲值本来就由装备槽提供，这个图层只负责"看得见"。）
        this.addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<>(
            this,
            new net.minecraft.client.model.HumanoidModel<>(context.getModelSet()
                .bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_INNER_ARMOR)),
            new net.minecraft.client.model.HumanoidModel<>(context.getModelSet()
                .bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager()));
    }

    @Override
    public void render(PlayerShellEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        PlayerSkin skin = resolveSkinData(entity);
        this.currentSkin = skin;
        // 根据皮肤手臂模型切换 细臂(slim)/粗臂(wide)：模型由原版按档案元数据给出，不再自己猜
        this.model = skin.model() == PlayerSkin.Model.SLIM ? this.slimModel : this.wideModel;
        // 必须在 super.render 之前设手臂姿态：模型的 setupAnim 只是"读取"它们
        // （PlayerRenderer 就是这么做拉弓/举矛姿态的），设晚了这一帧就还是旧姿态
        this.setModelProperties(entity);
        // 头顶光球与其它图层都在这里被 super.render 按顺序画掉（见构造器里注册的 ShellHeadOrbLayer）
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /** 与 {@code PlayerRenderer#setModelProperties} 同款：把双手的姿态交给模型。 */
    private void setModelProperties(PlayerShellEntity entity) {
        PlayerModel<PlayerShellEntity> model = this.model;
        model.crouching = entity.isCrouching();
        HumanoidModel.ArmPose mainPose = getArmPose(entity, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose offPose = getArmPose(entity, InteractionHand.OFF_HAND);
        // 双手动作（拉弓）时副手不再摆自己的姿态：只当"手里拿着箭"
        if (mainPose.isTwoHanded()) {
            offPose = entity.getOffhandItem().isEmpty()
                ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        }
        if (entity.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = mainPose;
            model.leftArmPose = offPose;
        } else {
            model.rightArmPose = offPose;
            model.leftArmPose = mainPose;
        }
    }

    /**
     * 手臂姿态：照搬 {@code PlayerRenderer#getArmPose}。
     * <p>
     * 关键是"正在使用某件物品"这个状态：空壳在起手时调了 {@code startUsingItem(MAIN_HAND)}，
     * 服务端把 {@code DATA_LIVING_ENTITY_FLAGS} 同步给客户端，客户端的 {@code LivingEntity}
     * 据此重建 {@code useItem}/{@code useItemRemaining}（见 LivingEntity#onSyncedDataUpdated），
     * 所以这里 {@code getUseItemRemainingTicks() > 0} 成立 →
     * {@code UseAnim.BOW} 给出 {@code BOW_AND_ARROW}（右手拉弓、左手搭箭）、
     * {@code UseAnim.SPEAR} 给出 {@code THROW_SPEAR}（举起三叉戟准备投掷）。
     */
    private static HumanoidModel.ArmPose getArmPose(PlayerShellEntity entity, InteractionHand hand) {
        ItemStack stack = entity.getItemInHand(hand);
        if (stack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }
        if (entity.getUsedItemHand() == hand && entity.getUseItemRemainingTicks() > 0) {
            UseAnim anim = stack.getUseAnimation();
            if (anim == UseAnim.BOW) {
                return HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            if (anim == UseAnim.SPEAR) {
                return HumanoidModel.ArmPose.THROW_SPEAR;
            }
            if (anim == UseAnim.BLOCK) {
                return HumanoidModel.ArmPose.BLOCK;
            }
            if (anim == UseAnim.CROSSBOW) {
                return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            }
            if (anim == UseAnim.SPYGLASS) {
                return HumanoidModel.ArmPose.SPYGLASS;
            }
            if (anim == UseAnim.TOOT_HORN) {
                return HumanoidModel.ArmPose.TOOT_HORN;
            }
            if (anim == UseAnim.BRUSH) {
                return HumanoidModel.ArmPose.BRUSH;
            }
        } else if (!entity.swinging && stack.getItem() instanceof CrossbowItem
            && CrossbowItem.isCharged(stack)) {
            // 装填好的弩：举在身前瞄准。判据照抄原版 {@code PlayerRenderer#getArmPose}
            // （"没在挥臂 + 是弩 + 已装填"）—— 烟花火箭那张签起手时手里就是这样一把弩
            // （见 {@code OrbPossessedAttackEvents} 的 ExplosiveFireworkEvent）。
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        return HumanoidModel.ArmPose.ITEM;
    }

    /**
     * <b>滑翔/俯冲时按视线俯角把身体压下去</b>（模仿玩家）。
     *
     * <h2>为什么必须自己写</h2>
     * 原版"身体跟着俯角倾倒"这套只在 {@code PlayerRenderer#setupRotations} 里
     * （{@code poseStack.mulPose(Axis.XP.rotationDegrees(f3 * (-90.0F - 视线俯角)))}），
     * 基类 {@code LivingEntityRenderer#setupRotations} <b>根本没有滑翔分支</b> ——
     * 空壳是走基类的，所以它俯冲时身体一直保持"直立/朝上"的姿态（实测反馈）。
     *
     * <p>判定用 {@code isFallFlying() || getPose() == Pose.FALL_FLYING}：
     * 后者是服务端同步过来的姿势，客户端一定拿得到（同 {@code ShellElytraLayer}）。
     * 渐入系数直接取 1（原版那段用的是 {@code getFallFlyingTicks()}，那是服务端本地计数、客户端拿到的是 0）。
     *
     * <p>切回胸甲（鞘翅离身）之后滑翔标记与姿势都会复位 ⇒ 这里自然不再生效、身体恢复直立。
     */
    @Override
    protected void setupRotations(PlayerShellEntity entity, PoseStack poseStack, float bob,
                                  float yBodyRot, float partialTick, float scale) {
        super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
        if (entity.isFallFlying() || entity.getPose() == net.minecraft.world.entity.Pose.FALL_FLYING) {
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(
                -90.0F - entity.getViewXRot(partialTick)));
            // 与真人玩家一样：滑翔时身体还会"朝着实际飞行方向"偏航
            // （原版 PlayerRenderer#setupRotations 里那段叉乘求夹角的写法，逐行照搬）。
            // 两处简化：① 原版用的是插值速度 getDeltaMovementLerped（空壳没有这个方法，用瞬时速度，
            // 一帧的差别看不出来）；② 原版用 getFallFlyingTicks() 做渐入 —— 那个计数在客户端这边是 0，
            // 照抄等于把俯身整个关掉，所以这里和上面一样直接取 1（见类注释）。
            Vec3 look = entity.getViewVector(partialTick);
            Vec3 motion = entity.getDeltaMovement();
            double motionH = motion.horizontalDistanceSqr();
            double lookH = look.horizontalDistanceSqr();
            if (motionH > 0.0D && lookH > 0.0D) {
                double dot = (motion.x * look.x + motion.z * look.z) / Math.sqrt(motionH * lookH);
                double cross = motion.x * look.z - motion.z * look.x;
                poseStack.mulPose(com.mojang.math.Axis.YP.rotation(
                    (float) (Math.signum(cross) * Math.acos(Mth.clamp(dot, -1.0D, 1.0D)))));
            }
        } else if (entity.isVisuallySwimming()) {
            // 与真人玩家一致：游泳时身体也压平（原版 PlayerRenderer 的游泳分支）。
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F - entity.getXRot()));
            poseStack.translate(0.0F, -1.0F, 0.3F);
        }
    }

    /**
     * <b>模型缩放 = 0.9375，与真人玩家一模一样</b>（用户指定："相对渲染位置也要和玩家一致"）。
     *
     * <p>原版 {@code PlayerRenderer#scale} 把玩家模型整体缩到 {@value #PLAYER_MODEL_SCALE}。
     * 基类 {@code LivingEntityRenderer#render} 的变换顺序是
     * <pre>
     *   scale(-1, -1, 1) → {@code scale(entity, ...)} → translate(0, -1.501, 0)
     * </pre>
     * 人形模型的原始高度是 <b>2.0 格</b>（32 像素），最后那次位移（-1.501）会被前面的缩放一起放大，
     * 所以真人渲染出来是 <b>2.0 × 0.9375 = 1.875 格</b>高、双脚正好落在实体坐标上；
     * 空壳少了 0.9375 那一步 → 渲染出来是整整 2.0 格，<b>比真人高 6.7%</b>
     * （脚的位置倒是一样，因为"模型脚底到原点"和"那步位移"是同一个系数）。
     * 于是站在真人旁边一眼就能看出对不上；补上这一步就完全一致了。
     */
    @Override
    protected void scale(PlayerShellEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE, PLAYER_MODEL_SCALE);
    }

    /**
     * <b>潜行时模型下移 2 像素</b>：原版 {@code PlayerRenderer#getRenderOffset}
     * （{@code isCrouching() ? (0, getScale() * -2 / 16, 0) : super}）的逐字照搬。
     */
    @Override
    public Vec3 getRenderOffset(PlayerShellEntity entity, float partialTicks) {
        return entity.isCrouching()
            ? new Vec3(0.0D, (double) (entity.getScale() * -2.0F) / 16.0D, 0.0D)
            : super.getRenderOffset(entity, partialTicks);
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerShellEntity entity) {
        PlayerSkin skin = this.currentSkin;
        if (skin == null) {
            skin = resolveSkinData(entity);
            this.currentSkin = skin;
        }
        return skin.texture();
    }

    /**
     * 解析这具空壳这一帧该用谁的皮肤。三级来源，全部走原版：
     * <ol>
     *   <li><b>皮肤名为空</b> → {@link DefaultPlayerSkin#get(UUID)}：按实体 UUID 稳定给一张原版默认皮肤
     *       （Steve/Alex 那一套 18 张之一，模型自带，不会出现"细臂图配粗臂模型"）；</li>
     *   <li><b>名字对应一位在线玩家</b> → {@link PlayerInfo#getSkin()}，也就是
     *       {@code PlayerRenderer} 每帧取皮肤的同一个入口：零 HTTP，未就绪时原版自己先给默认皮肤、
     *       下好之后自动换成真皮肤（一次性过渡，不会有周期性的闪烁）；</li>
     *   <li><b>其它名字</b>（离线玩家、刷怪蛋随机名/自定义名）→ 起一次后台解析
     *       （名字 → UUID → 档案 → {@link SkinManager#getOrLoad}），没就绪之前用按名字生成的兜底皮肤。</li>
     * </ol>
     * <b>就绪判定必须在最前面</b>：这就是原来"好好的皮肤每 10 秒闪一下"的病根
     * （见类注释第 3 条），现在只要 {@code resolved} 非空就直接返回，不再有任何周期性动作。
     */
    private PlayerSkin resolveSkinData(PlayerShellEntity entity) {
        String skinName = entity.getSkinTexture();
        // 为空：用实体 UUID 产生一张稳定的默认皮肤（只提示一次，别每帧刷日志）
        if (skinName == null || skinName.isBlank()) {
            if (BLANK_SKIN_LOGGED.add(entity.getUUID())) {
                LOGGER.info("[PlayerShell] SkinTexture 为空或未同步，使用默认皮肤 (entityUuid={})",
                    entity.getUUID());
            }
            return DefaultPlayerSkin.get(entity.getUUID());
        }
        // ① 在线玩家：直接用原版玩家列表里的皮肤（完全交给原版加载与回退）
        PlayerSkin online = onlineSkin(skinName);
        if (online != null) {
            return online;
        }
        // ② 其余名字：自己的"名字 → 档案"解析 + 原版皮肤管线
        LoadedSkin loaded = SKIN_CACHE.computeIfAbsent(skinName, LoadedSkin::new);
        PlayerSkin resolved = loaded.resolved;
        if (resolved != null) {
            return resolved;
        }
        loaded.ensureStarted();
        return loaded.fallback;
    }

    /**
     * 名字正好是服务器里一位在线玩家时，返回原版给他的皮肤；否则 null。
     * <p>
     * 一次网络请求都不用：玩家列表里的档案（含皮肤属性）是登录时服务端发过来的，
     * {@code PlayerInfo} 内部已经用 {@link SkinManager#getOrLoad} 起好了加载，
     * {@link PlayerInfo#getSkin()} 只是把它读出来（未就绪时给默认皮肤）。
     * 顺带说明为什么这里每帧调用没问题：{@code PlayerRenderer} 本来就是每帧这么取的。
     */
    @Nullable
    private static PlayerSkin onlineSkin(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return null;
        }
        PlayerInfo info = mc.getConnection().getPlayerInfo(name);
        if (info == null) {
            return null;
        }
        PlayerSkin skin = info.getSkin();
        // 只在纹理真的换了的时候打日志：原版先把默认皮肤给出来、下载完再换成真皮肤，
        // 所以正常会看到两行（wide/steve.png → 那位玩家的皮肤），这正是"皮肤就绪"的信号。
        ResourceLocation previous = ONLINE_SKIN_LOGGED.put(name, skin.texture());
        if (!skin.texture().equals(previous)) {
            LOGGER.info("[PlayerShell] 皮肤（在线玩家，原版玩家列表）: '{}' 模型={} 纹理={}",
                name, skin.model().id(), skin.texture());
        }
        return skin;
    }

    /** 名字 → UUID 的查询结果。 */
    private record NameLookup(@Nullable String uuid, boolean unknownName) {
    }

    /**
     * 通过 Mojang 公开接口把玩家名换成无连字符的 UUID 十六进制串。
     * <p>
     * {@code unknownName = true} 表示 Mojang 明确回答"没有这个人"（HTTP 404 或 204）——
     * 这种情况<b>再重试一万次也没用</b>，调用方据此永久停在兜底皮肤；其它失败（超时、DNS、5xx、
     * 接口限流）只是"这次没查成"，退避之后可以再来。
     * <p>
     * 注意只有走到这里才需要联网：名字是在线玩家时走 {@link #onlineSkin}，压根不查名字。
     */
    private static NameLookup resolveUuid(String name) {
        try {
            String encoded = URLEncoder.encode(name, "UTF-8");
            HttpURLConnection conn = (HttpURLConnection)
                new URL("https://api.mojang.com/users/profiles/minecraft/" + encoded).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Joe's Addons for ABMC)");
            int code = conn.getResponseCode();
            if (code == 404 || code == 204) {
                return new NameLookup(null, true);
            }
            if (code != 200) {
                return new NameLookup(null, false);
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }
            JsonObject obj = JsonParser.parseString(body.toString()).getAsJsonObject();
            return new NameLookup(obj.has("id") ? obj.get("id").getAsString() : null, false);
        } catch (Exception e) {
            return new NameLookup(null, false);
        }
    }

    /**
     * 生成稳定兜底皮肤的 key：离线风格 UUID。
     * <p>
     * 用"名字"而不是实体 UUID，是为了<b>同一个名字永远得到同一张默认皮肤</b> ——
     * 否则同名的两具空壳会长得不一样，玩家一眼就能看出"这不是同一个人"。
     */
    private static UUID opaqueUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
    }

    /** 把无连字符的 32 位十六进制字符串解析为 UUID。 */
    private static UUID toUuid(String s) {
        return UUID.fromString(s.replaceFirst(
            "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
    }
}
