package cn.autoforged.joes_addons_for_abmc.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * “移植脚”渲染层：附加到所有 {@link LivingEntityRenderer} 上，为客户端状态表记录有移植脚的生物，
 * 用另一个生物的一条腿（“脚来源”）替换本生物的所有腿部件。
 *
 * 与“移植头”类似：
 * 1. 沿目标身体模型的每一条腿（root→…→leg）调用 translateAndRotate，把坐标变换到该腿 pivot，
 *    在该 pivot 绘制外来的“脚来源”腿部件克隆；
 * 2. 原始腿部件由 {@link #hideBodyFeet}/{@link #restoreBodyFeet} 在
 *    RenderLivingEvent.Pre/Post 中隐藏/还原（模型实例跨实体共享，必须还原）。
 * 3. 没有可识别腿部件的生物（鱼/鱿鱼等）视为不可移植，保持原形态。
 */
public final class TransplantedFeetLayer extends RenderLayer<LivingEntity, EntityModel<LivingEntity>> {

    private static final ResourceLocation FALLBACK_TEX =
        ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper.png");

    /** 常见腿字段名（Mojmap 命名的模型字段）。递归 fallback 会兜底其它命名。 */
    private static final String[] LEG_FIELD_NAMES = {
        "leftLeg", "rightLeg", "left_leg", "right_leg",
        "leftHindLeg", "rightHindLeg", "leftFrontLeg", "rightFrontLeg",
        "left_hind_leg", "right_hind_leg", "left_front_leg", "right_front_leg",
        "leg1", "leg2", "leg3", "leg4", "leg0"
    };

    /** 脚来源类型资源键 -> 克隆后的脚（腿）ModelPart */
    private static final Map<String, ModelPart> FEET_CLONES = new HashMap<>();
    /** 脚来源类型资源键 -> 纹理 */
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    /** 已打印过“克隆失败”日志的脚类型（每种只记录一次）。 */
    private static final java.util.Set<String> LOGGED_FAILURES = new java.util.HashSet<>();

    public TransplantedFeetLayer(RenderLayerParent<LivingEntity, EntityModel<LivingEntity>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffer, int packedLight,
                       LivingEntity entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;
        String feetType = TransplantedFeetClientState.getFeetType(entity.getId());
        if (feetType.isEmpty()) return;
        if (!isFeetRenderable(feetType)) return;

        ModelPart foreign = getOrCloneFeet(feetType);
        if (foreign == null) return;

        EntityModel<?> model = getParentModel();
        if (model == null) return;
        List<ModelPart> bodyLegs = legPartsOf(model);
        if (bodyLegs.isEmpty()) return;

        ResourceLocation tex = TEXTURES.get(feetType);
        if (tex == null) tex = FALLBACK_TEX;
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(tex));

        // 每一条身体腿：变换到该腿 pivot 后绘制外来脚。外来脚自身以零姿态渲染（防止双重旋转）。
        for (ModelPart leg : bodyLegs) {
            ps.pushPose();
            leg.translateAndRotate(ps);
            foreign.render(ps, consumer, packedLight, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }

    // ===== 原始脚隐藏（RenderLivingEvent.Pre/Post 调用） =====

    public static boolean isFeetRenderable(String feetType) {
        if (feetType == null || feetType.isEmpty()) return false;
        try {
            ModelPart foreign = getOrCloneFeet(feetType);
            if (foreign == null) return false;
            return !(cubesOf(foreign).isEmpty() && childrenOf(foreign).isEmpty());
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Pre：若该实体有“可渲染”的移植脚且身体可识别出腿，隐藏其模型的所有腿部件。 */
    public static boolean hideBodyFeet(EntityModel<?> model, int entityId) {
        String feetType = TransplantedFeetClientState.getFeetType(entityId);
        if (feetType.isEmpty()) return false;
        if (!isFeetRenderable(feetType)) return false;
        List<ModelPart> legs = legPartsOf(model);
        if (legs.isEmpty()) return false;
        setBodyLegsVisible(model, false);
        return true;
    }

    /** Post：恢复被隐藏的腿部件的可见性（模型实例跨实体共享，必须还原）。 */
    public static void restoreBodyFeet(EntityModel<?> model, int entityId) {
        if (TransplantedFeetClientState.getFeetType(entityId).isEmpty()) return;
        setBodyLegsVisible(model, true);
    }

    // ===== 腿部件定位 =====

    /** 取目标身体模型上的“腿”部件集合：优先从 root 递归收集“整条腿”；若无，再回退到常见字段。
     * 递归能覆盖全部整条肢体（含分段腿生物），字段仅作无“leg”命名的兜底。 */
    private static List<ModelPart> legPartsOf(EntityModel<?> model) {
        java.util.Set<ModelPart> found = new java.util.LinkedHashSet<>();
        ModelPart root = rootOf(model);
        if (root != null) collectLegParts(root, found);
        if (found.isEmpty()) {
            for (String f : LEG_FIELD_NAMES) {
                ModelPart p = findPartField(model, f);
                if (p != null && legTarget(p)) found.add(p);
            }
        }
        return new ArrayList<>(found);
    }

    /** 概率随机“移植脚”定位：收集“最外层腿”（整条肢体）。名字含 leg、可渲染的节点即视为一整条腿，
     * 不再下钻到其内部腿段。这样隐藏时把整条肢体都隐藏、渲染时用外来整条肢体替换，避免出现
     * “外层腿未移除、外来腿又叠加上去”的现象（人形两条腿、蜘蛛等分段腿都能整肢处理）。 */
    private static void collectLegParts(ModelPart part, java.util.Set<ModelPart> out) {
        for (Map.Entry<String, ModelPart> entry : childrenOf(part).entrySet()) {
            ModelPart child = entry.getValue();
            boolean childIsLeg = entry.getKey().toLowerCase().contains("leg") && legTarget(child);
            // 是腿即作为一整条腿收集（隐藏/替换整肢），不再下钻；否则继续向下寻找腿。
            if (childIsLeg) {
                out.add(child);
            } else {
                collectLegParts(child, out);
            }
        }
    }

    private static boolean legTarget(ModelPart part) {
        return !(cubesOf(part).isEmpty() && childrenOf(part).isEmpty());
    }

    private static void setBodyLegsVisible(EntityModel<?> model, boolean visible) {
        for (ModelPart leg : legPartsOf(model)) {
            try {
                leg.visible = visible;
            } catch (Exception ignored) {
            }
        }
    }

    // ===== 外来脚克隆 =====

    /** 取外来脚克隆；若指定类型无法在客户端克隆，回退为（必定可渲染的）僵尸腿占位。 */
    private static ModelPart getOrCloneFeet(String feetType) {
        ModelPart cached = FEET_CLONES.get(feetType);
        if (cached != null) return cached;
        ModelPart direct = tryCloneFeet(feetType);
        if (direct != null) return direct;
        ModelPart fallback = tryCloneFeet("minecraft:zombie");
        if (fallback != null) {
            FEET_CLONES.put(feetType, fallback);
            TEXTURES.put(feetType, TEXTURES.getOrDefault("minecraft:zombie", FALLBACK_TEX));
        }
        return fallback;
    }

    private static ModelPart tryCloneFeet(String feetType) {
        ModelPart cached = FEET_CLONES.get(feetType);
        if (cached != null) return cached;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.tryParse(feetType));
        if (type == null) {
            logOnce(feetType, "entity type not found");
            return null;
        }
        Entity entity = "minecraft:player".equals(feetType)
            ? EntityType.ZOMBIE.create(mc.level)
            : type.create(mc.level);
        if (entity == null) {
            logOnce(feetType, "create returned null");
            return null;
        }
        try {
            @SuppressWarnings("rawtypes")
            EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
            if (!(renderer instanceof LivingEntityRenderer lr)) {
                logOnce(feetType, "renderer=" + renderer);
                return null;
            }
            EntityModel<?> model = (EntityModel<?>) lr.getModel();
            if (model == null) {
                logOnce(feetType, "model is null");
                return null;
            }
            List<ModelPart> legs = legPartsOf(model);
            if (legs.isEmpty()) {
                logOnce(feetType, "no leg parts for model " + model.getClass().getSimpleName());
                return null;
            }
            ModelPart clone = deepClonePartWithZeroPose(legs.get(0));
            ResourceLocation tex = renderer.getTextureLocation(entity);
            FEET_CLONES.put(feetType, clone);
            TEXTURES.put(feetType, tex != null ? tex : FALLBACK_TEX);
            return clone;
        } catch (Exception e) {
            if (LOGGED_FAILURES.add("exp|" + feetType)) {
                com.mojang.logging.LogUtils.getLogger().warn("[移植脚] 克隆脚类型 {} 失败: ", feetType, e);
            }
            return null;
        } finally {
            entity.discard();
        }
    }

    private static void logOnce(String feetType, String reason) {
        if (LOGGED_FAILURES.add(feetType + "|" + reason)) {
            com.mojang.logging.LogUtils.getLogger().warn("[移植脚] 无法克隆脚类型 {}, 原因: {}", feetType, reason);
        }
    }

    // ===== 模型 root / 字段访问（与 TransplantedHeadLayer 一致） =====

    private static ModelPart findPartField(EntityModel<?> model, String name) {
        for (Class<?> c = model.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(model);
                if (v instanceof ModelPart mp) return mp;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static ModelPart rootOf(EntityModel<?> model) {
        if (model instanceof HierarchicalModel<?> hierarchical) {
            try {
                return hierarchical.root();
            } catch (Exception ignored) {
            }
        }
        for (Class<?> c = model.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField("root");
                f.setAccessible(true);
                Object v = f.get(model);
                if (v instanceof ModelPart mp) return mp;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    // ===== 深拷贝 =====

    private static ModelPart deepClonePartWithZeroPose(ModelPart source) {
        Map<String, ModelPart> clonedChildren = new LinkedHashMap<>();
        for (Map.Entry<String, ModelPart> entry : childrenOf(source).entrySet()) {
            clonedChildren.put(entry.getKey(), deepClonePartKeepPose(entry.getValue()));
        }
        return new ModelPart(cubesOf(source), clonedChildren);
    }

    private static ModelPart deepClonePartKeepPose(ModelPart source) {
        Map<String, ModelPart> clonedChildren = new LinkedHashMap<>();
        for (Map.Entry<String, ModelPart> entry : childrenOf(source).entrySet()) {
            clonedChildren.put(entry.getKey(), deepClonePartKeepPose(entry.getValue()));
        }
        ModelPart clone = new ModelPart(cubesOf(source), clonedChildren);
        PartPose pose = source.getInitialPose();
        if (pose != null) {
            clone.setInitialPose(pose);
            clone.loadPose(pose);
        }
        return clone;
    }

    // ===== ModelPart 私有字段反射 =====

    private static java.lang.reflect.Field FIELD_CUBES;
    private static java.lang.reflect.Field FIELD_CHILDREN;

    private static java.lang.reflect.Field fieldOf(String name) {
        try {
            java.lang.reflect.Field f = ModelPart.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ModelPart> childrenOf(ModelPart part) {
        if (FIELD_CHILDREN == null) FIELD_CHILDREN = fieldOf("children");
        if (FIELD_CHILDREN != null) {
            try {
                return (Map<String, ModelPart>) FIELD_CHILDREN.get(part);
            } catch (Exception ignored) {
            }
        }
        return java.util.Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static List<ModelPart.Cube> cubesOf(ModelPart part) {
        if (FIELD_CUBES == null) FIELD_CUBES = fieldOf("cubes");
        if (FIELD_CUBES != null) {
            try {
                return (List<ModelPart.Cube>) FIELD_CUBES.get(part);
            } catch (Exception ignored) {
            }
        }
        return java.util.Collections.emptyList();
    }
}