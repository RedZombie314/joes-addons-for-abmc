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
 * “移植头”渲染层：附加到所有 {@link LivingEntityRenderer} 上，为客户端状态表记录有移植头的生物，
 * 在其**原有头部**（颈关节 pivot）位置绘制一个随机其它生物的头部模型（头不受 boss 限制）。
 *
 * 参考移植的 TransplantedHeadLayer：
 * 1. 沿 目标身体模型 root→head 路径调用 translateAndRotate，把坐标变换到原头 pivot，
 *    因而外来头与“原头本应所在的位置”完全重合；
 * 2. 根据外来头与原头包围盒的后缘 maxZ 做 Z 对齐，长脸生物（牛/羊等）面部自然前伸；
 * 3. 原始头则由 {@link #hideBodyHead}/{@link #restoreBodyHead} 在
 *    RenderLivingEvent.Pre/Post 中隐藏（RenderLevelStage 世界空间无法隐藏原头）。
 */
public final class TransplantedHeadLayer extends RenderLayer<LivingEntity, EntityModel<LivingEntity>> {

    private static final ResourceLocation FALLBACK_TEX =
        ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper.png");

    /** 头类型资源键 -> 克隆后的头 ModelPart */
    private static final Map<String, ModelPart> HEAD_CLONES = new HashMap<>();
    /** 头类型资源键 -> 纹理 */
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    /** 已打印过“克隆失败”日志的头类型（每种只记录一次，避免刷屏）。 */
    private static final java.util.Set<String> LOGGED_FAILURES = new java.util.HashSet<>();

    public TransplantedHeadLayer(RenderLayerParent<LivingEntity, EntityModel<LivingEntity>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffer, int packedLight,
                       LivingEntity entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;
        String headType = TransplantedHeadClientState.getHeadType(entity.getId());
        if (headType.isEmpty()) return;
        // 带移植头的生物一律显示外来头：克隆失败时 getOrClone 会回退为僵尸头占位，
        // 保证该实体绝不会因渲染失败而看似正常（否则它与“头来源音效”会错位）。
        if (!isHeadRenderable(headType)) return;

        ModelPart foreign = getOrClone(headType);
        if (foreign == null) return;

        EntityModel<?> model = getParentModel();
        if (model == null) return;
        List<ModelPart> path = bodyHeadPath(model);
        if (path == null || path.isEmpty()) return;

        float[] foreignBounds = computeTotalBounds(foreign);
        float[] targetBounds = computeTotalBounds(path.get(path.size() - 1));

        ResourceLocation tex = TEXTURES.get(headType);
        if (tex == null) tex = FALLBACK_TEX;

        // 有鼻子的头（村民/女巫等）根立方体延伸到脖子以下，整体偏高，向下微调
        float yOffset = hasNoseChild(foreign) ? 1.5F / 16.0F : 0.0F;

        ps.pushPose();
        for (ModelPart part : path) {
            part.translateAndRotate(ps);
        }
        // Z 对齐头部后方(maxZ)：长脸生物（牛/羊/山羊）面部自然前伸
        ps.translate(0, yOffset, (targetBounds[5] - foreignBounds[5]) / 16.0F);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(tex));
        foreign.render(ps, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    // ===== 原头隐藏（RenderLivingEvent.Pre/Post 调用） =====

    /**
     * 外来头是否确实能渲染出可见模型：克隆成功且非空（有立方体或子部件）。
     * 不能渲染的头类型（例如无标准头部 / 无法在客户端生成代理实体的类型）在 pool 中也会被排除，
     * 此处作为客户端兜底，两端任意一处判定不可用时都绝不隐藏原头，从而避免无头生物。
     */
    public static boolean isHeadRenderable(String headType) {
        if (headType == null || headType.isEmpty()) return false;
        try {
            ModelPart foreign = getOrClone(headType);
            if (foreign == null) return false;
            return !(cubesOf(foreign).isEmpty() && childrenOf(foreign).isEmpty());
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Pre：若该实体有“可渲染”的移植头，隐藏其模型的原头部件。返回是否隐藏成功。
     * 只有当 (a) 外来头可渲染 且 (b) 目标模型存在可绘制替换头的路径 时才隐藏，
     * 否则保留原头，杜绝“原头消失却无替代头”的无头现象。
     */
    public static boolean hideBodyHead(EntityModel<?> model, int entityId) {
        String headType = TransplantedHeadClientState.getHeadType(entityId);
        if (headType.isEmpty()) return false;
        if (!isHeadRenderable(headType)) return false;
        List<ModelPart> path = bodyHeadPath(model);
        if (path == null || path.isEmpty()) return false;
        setBodyHeadVisible(model, false);
        return true;
    }

    /** Post：恢复被隐藏的原头部件可见性（模型实例跨实体共享，必须还原）。 */
    public static void restoreBodyHead(EntityModel<?> model, int entityId) {
        if (TransplantedHeadClientState.getHeadType(entityId).isEmpty()) return;
        setBodyHeadVisible(model, true);
    }

    /**
     * 在目标身体模型上定位“头”部件路径，供把外来头变换到原头 pivot 并决定是否隐藏原头。
     * 优先取对外暴露的 {@code head} 字段（HumanoidModel 一族 / SheepModel 等没有 root 字段，
     * 但头部是独立的字段 ModelPart）；否则回退到 root→head 层级遍历。
     */
    private static List<ModelPart> bodyHeadPath(EntityModel<?> model) {
        ModelPart fieldHead = findPartField(model, "head");
        if (fieldHead != null) return List.of(fieldHead);
        ModelPart root = rootOf(model);
        if (root == null) return null;
        return findPathToHead(root);
    }

    /** 隐藏 / 恢复身体原头：优先 head/hat 字段，否则回退 root 遍历的 head。 */
    private static void setBodyHeadVisible(EntityModel<?> model, boolean visible) {
        ModelPart fieldHead = findPartField(model, "head");
        ModelPart fieldHat = findPartField(model, "hat");
        if (fieldHead != null) {
            fieldHead.visible = visible;
            if (fieldHat != null) fieldHat.visible = visible;
            return;
        }
        ModelPart root = rootOf(model);
        if (root == null) return;
        ModelPart head = findHeadStrict(root);
        if (head != null) head.visible = visible;
    }

    // ===== 外来头克隆 =====

    /**
     * 取外来头克隆；若指定类型无法在客户端克隆，回退为（必定能渲染的）僵尸头占位。
     * 这样保证“带移植头”的生物一定呈现外来头，不会因某头类型克隆失败而看似正常
     * （从而避免“看起来正常却按头来源发随机音效”的错位）。
     */
    private static ModelPart getOrClone(String headType) {
        ModelPart cached = HEAD_CLONES.get(headType);
        if (cached != null) return cached;
        ModelPart direct = tryCloneHead(headType);
        if (direct != null) return direct;
        // 回退：该头类型无法克隆，改用僵尸头占位并缓存，确保渲染/隐藏原头两步行为一致。
        ModelPart fallback = tryCloneHead("minecraft:zombie");
        if (fallback != null) {
            HEAD_CLONES.put(headType, fallback);
            TEXTURES.put(headType, TEXTURES.getOrDefault("minecraft:zombie", FALLBACK_TEX));
        }
        return fallback;
    }

    private static ModelPart tryCloneHead(String headType) {
        ModelPart cached = HEAD_CLONES.get(headType);
        if (cached != null) return cached;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.tryParse(headType));
        if (type == null) {
            logOnce(headType, "entity type not found");
            return null;
        }
        // 玩家头无法在客户端独立创建，用僵尸代理（两者头模一致）；其余类型用其自身。
        Entity entity = "minecraft:player".equals(headType)
            ? EntityType.ZOMBIE.create(mc.level)
            : type.create(mc.level);
        if (entity == null) {
            logOnce(headType, "create returned null");
            return null;
        }
        try {
            @SuppressWarnings("rawtypes")
            EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
            if (!(renderer instanceof LivingEntityRenderer lr)) {
                logOnce(headType, "renderer=" + renderer);
                return null;
            }
            EntityModel<?> model = (EntityModel<?>) lr.getModel();
            if (model == null) {
                logOnce(headType, "model is null");
                return null;
            }
            ModelPart head = findHeadFieldOf(model);
            if (head == null) {
                ModelPart root = rootOf(model);
                if (root == null) {
                    logOnce(headType, "root not found for model " + model.getClass().getSimpleName());
                    return null;
                }
                head = findHead(root);
            }
            ModelPart clone = deepClonePartWithZeroPose(head);
            ResourceLocation tex = renderer.getTextureLocation(entity);
            HEAD_CLONES.put(headType, clone);
            TEXTURES.put(headType, tex != null ? tex : FALLBACK_TEX);
            return clone;
        } catch (Exception e) {
            // 打印一次完整异常，便于定位克隆失败的头类型
            if (LOGGED_FAILURES.add("exp|" + headType)) {
                com.mojang.logging.LogUtils.getLogger().warn("[移植头] 克隆头类型 {} 失败: ", headType, e);
            }
            return null;
        } finally {
            entity.discard();
        }
    }

    /** 仅打印一次日志（同键），避免 render 高频刷屏。 */
    private static void logOnce(String headType, String reason) {
        if (LOGGED_FAILURES.add(headType + "|" + reason)) {
            com.mojang.logging.LogUtils.getLogger().warn("[移植头] 无法克隆头类型 {}, 原因: {}", headType, reason);
        }
    }

    // ===== 模型 root / 头部字段访问 =====

    /** 反射读取模型类及其父类上暴露的某名字 {@link ModelPart} 字段（如 head/hat）。
     *  没有 root 字段的模型（HumanoidModel 一族 / SheepModel 等）用字段直接拿头部件。 */
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

    /** 找克隆外来头用的头部字段；没有则返回 null（由调用方回退到 root 遍历）。 */
    private static ModelPart findHeadFieldOf(EntityModel<?> model) {
        return findPartField(model, "head");
    }

    private static ModelPart rootOf(EntityModel<?> model) {
        if (model instanceof HierarchicalModel<?> hierarchical) {
            try {
                return hierarchical.root();
            } catch (Exception ignored) {
            }
        }
        for (Class<?> c = model.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (FIELD_ROOT == null && FIELD_ROOT_CLASS != c) {
                try {
                    FIELD_ROOT = c.getDeclaredField("root");
                    FIELD_ROOT.setAccessible(true);
                    FIELD_ROOT_CLASS = c;
                } catch (Exception ignored) {
                    FIELD_ROOT_CLASS = c;
                }
            }
            if (FIELD_ROOT != null) {
                try {
                    Object v = FIELD_ROOT.get(model);
                    if (v instanceof ModelPart mp) return mp;
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    // ===== 找头：克隆外来头用 findHead，定位身体原头路径用 findPathToHead =====

    /** 找代表“头部”的部件（用于克隆外来头）：head → 凋灵头 → head_parts → 深层递归 → body → 整根 */
    private static ModelPart findHead(ModelPart part) {
        ModelPart found = findHeadStrict(part);
        return found != null ? found : part;
    }

    private static ModelPart findHeadStrict(ModelPart part) {
        if (part.hasChild("head")) return part.getChild("head");
        for (String name : new String[]{"center_head", "right_head", "left_head"}) {
            if (part.hasChild(name)) return part.getChild(name);
        }
        if (part.hasChild("head_parts")) return part.getChild("head_parts");
        for (Map.Entry<String, ModelPart> entry : childrenOf(part).entrySet()) {
            if (entry.getKey().toLowerCase().contains("head")) continue;
            ModelPart found = findHeadStrict(entry.getValue());
            if (found != null) return found;
        }
        for (Map.Entry<String, ModelPart> entry : childrenOf(part).entrySet()) {
            String name = entry.getKey().toLowerCase();
            if (!name.equals("head_parts") && name.contains("head")) {
                ModelPart inner = findHeadStrict(entry.getValue());
                if (inner != null) return inner;
                return entry.getValue();
            }
            ModelPart found = findHeadStrict(entry.getValue());
            if (found != null) return found;
        }
        for (Map.Entry<String, ModelPart> entry : childrenOf(part).entrySet()) {
            if (entry.getKey().toLowerCase().equals("body")) return entry.getValue();
        }
        for (Map.Entry<String, ModelPart> entry : childrenOf(part).entrySet()) {
            if (entry.getKey().toLowerCase().contains("head")) continue;
            if (entry.getValue().hasChild("body")) return entry.getValue().getChild("body");
        }
        return null;
    }

    /** 找 root→head 路径（不含 root、含 head，按层级序）；用于把外来头变换到身体原头 pivot */
    private static List<ModelPart> findPathToHead(ModelPart part) {
        return findPathToHeadStrict(part);
    }

    private static List<ModelPart> findPathToHeadStrict(ModelPart part) {
        if (part.hasChild("head")) return List.of(part.getChild("head"));
        for (String name : new String[]{"center_head", "right_head", "left_head"}) {
            if (part.hasChild(name)) return List.of(part.getChild(name));
        }
        if (part.hasChild("head_parts")) {
            ModelPart headParts = part.getChild("head_parts");
            if (headParts.hasChild("head")) return List.of(headParts, headParts.getChild("head"));
            return List.of(headParts);
        }
        for (Map.Entry<String, ModelPart> entry : childrenOf(part).entrySet()) {
            String name = entry.getKey().toLowerCase();
            if (name.contains("head")) continue;
            List<ModelPart> subPath = findPathToHeadStrict(entry.getValue());
            if (subPath != null) {
                List<ModelPart> path = new ArrayList<>();
                path.add(entry.getValue());
                path.addAll(subPath);
                return path;
            }
        }
        for (Map.Entry<String, ModelPart> entry : childrenOf(part).entrySet()) {
            String name = entry.getKey().toLowerCase();
            if (!name.equals("head_parts") && name.contains("head")) {
                List<ModelPart> inner = findPathToHeadStrict(entry.getValue());
                if (inner != null) {
                    List<ModelPart> path = new ArrayList<>();
                    path.add(entry.getValue());
                    path.addAll(inner);
                    return path;
                }
                return List.of(entry.getValue());
            }
            List<ModelPart> subPath = findPathToHeadStrict(entry.getValue());
            if (subPath != null) {
                List<ModelPart> path = new ArrayList<>();
                path.add(entry.getValue());
                path.addAll(subPath);
                return path;
            }
        }
        // 无“真头”（head/center_head/head_parts 等）即视为没有可移植头：鱼、鱿鱼等整身为一条的
        // 生物不可移植。切勿回退到把整具身体当作“头”（否则会隐藏全身、只剩一颗外来头漂浮），
        // 由 hideBodyHead 保留原头，使其保持正常形态。
        return null;
    }

    // ===== 包围盒 / 对齐辅助 =====

    private static boolean hasNoseChild(ModelPart part) {
        for (String name : childrenOf(part).keySet()) {
            if (name.equalsIgnoreCase("nose")) return true;
        }
        return false;
    }

    /** 整棵子树所有立方体的包围盒并集，累加子部件 initialPose。返回像素（相对根 pivot）。 */
    private static float[] computeTotalBounds(ModelPart headPart) {
        float[] total = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
            -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        collectCubesWithOffset(headPart, 0, 0, 0, total);
        if (total[0] == Float.MAX_VALUE) {
            return new float[]{-4, -4, -4, 4, 4, 4};
        }
        return total;
    }

    private static void collectCubesWithOffset(ModelPart part, float offX, float offY, float offZ, float[] total) {
        for (ModelPart.Cube cube : cubesOf(part)) {
            total[0] = Math.min(total[0], cube.minX + offX);
            total[1] = Math.min(total[1], cube.minY + offY);
            total[2] = Math.min(total[2], cube.minZ + offZ);
            total[3] = Math.max(total[3], cube.maxX + offX);
            total[4] = Math.max(total[4], cube.maxY + offY);
            total[5] = Math.max(total[5], cube.maxZ + offZ);
        }
        for (ModelPart child : childrenOf(part).values()) {
            PartPose p = child.getInitialPose();
            collectCubesWithOffset(child, offX + p.x, offY + p.y, offZ + p.z, total);
        }
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

    // ===== ModelPart 私有字段反射（cubes/children 为 private） =====

    private static java.lang.reflect.Field FIELD_CUBES;
    private static java.lang.reflect.Field FIELD_CHILDREN;
    private static java.lang.reflect.Field FIELD_ROOT;
    private static Class<?> FIELD_ROOT_CLASS;

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