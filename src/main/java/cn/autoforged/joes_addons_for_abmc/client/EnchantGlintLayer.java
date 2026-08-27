package cn.autoforged.joes_addons_for_abmc.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import cn.autoforged.joes_addons_for_abmc.item.StaffClientState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * “自体附魔”光效层：当附魔台权杖瞄准的某个生物“空手”时，替该生物在**材质上**叠一层明显可见的
 * 附魔光效——用带光晕的频谱半透明渲染把整个模型重绘一次（保证可见），再叠加一层流动的附魔 glint
 * 波纹（原版物品附魔光效），两者都是透明的叠加层，不遮盖本体。
 *
 * 是否生效由 {@link StaffClientState#enchantSelfTargetId}（每刻更新的当前自附魔目标）决定。
 */
public final class EnchantGlintLayer extends RenderLayer<LivingEntity, EntityModel<LivingEntity>> {

    public EnchantGlintLayer(RenderLayerParent<LivingEntity, EntityModel<LivingEntity>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffer, int packedLight,
                       LivingEntity entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;
        if (!StaffClientState.isEnchantSelf(entity.getId())) return;

        EntityModel<?> model = getParentModel();
        if (model == null) return;
        // 优先取单一根节点（HierarchicalModel 及带 root 字段的模型）一次渲染整模；
        // 否则（猪/马等非层级模型，各部位是独立的 ModelPart 字段）退化为逐部位渲染。
        List<ModelPart> parts = renderParts(model);
        if (parts.isEmpty()) return;

        // 给生物自身叠一层半透明、明显可见的紫红附魔光晕（贴附材质）。用生物自身纹理做“着色”重绘，
        // 半透明但不透明度适中，保证多图层生物（村民等）的原生图层仍能透出、不会被覆盖缺失；
        // 不叠加第二次整模重绘，避免与本体 z-fighting 导致某些生物看不到光效。
        // 末尾参数为 0xARGB 打包颜色（alpha=80 半透明偏明显，R=C8,G=78,B=FF 饱和紫红）。
        ResourceLocation tex = textureOf(entity);
        if (tex == null) return;
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(tex));
        ps.pushPose();
        for (ModelPart part : parts) {
            ps.pushPose();
            part.render(ps, vc, packedLight, OverlayTexture.NO_OVERLAY, 0x80C878FF);
            ps.popPose();
        }
        ps.popPose();
    }

    /** 取该实体要重绘的所有模型部位：
     * 1. 有单一 root（HierarchicalModel / 带 root 字段）→ 只返回 root，一次覆盖整模；
     * 2. 否则反射收集模型所有顶层 ModelPart 字段构造候选几何，再只保留其中“顶层部位”
     *    ——即不被任何其它候选部位作为子节点包含的部位。这样自动洗掉字段间父子/重复
     *    引用（同一几何被父节点与子节点/多个字段重复引用时只画一次），既保证全身覆盖，
     *    又不会出现重复（例如马渲染出第二个上翘的尾巴，或只剩头有光）。 */
    private static List<ModelPart> renderParts(EntityModel<?> model) {
        ModelPart root = rootOf(model);
        if (root != null) {
            List<ModelPart> out = new ArrayList<>();
            out.add(root);
            return out;
        }

        List<ModelPart> candidates = new ArrayList<>();
        for (Class<?> c = model.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (!ModelPart.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    Object v = f.get(model);
                    if (v instanceof ModelPart mp && !candidates.contains(mp)) candidates.add(mp);
                }
            } catch (Exception ignored) {
            }
        }
        if (candidates.isEmpty()) return candidates;

        // 收集所有被作为子节点引用的部位集合（按引用身份去重）
        Set<ModelPart> children = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (ModelPart c : candidates) {
            collectChildren(c, children);
        }
        // 顶层部位 = 候选里不被任何部位作为子节点包含的那些
        List<ModelPart> top = new ArrayList<>();
        for (ModelPart c : candidates) {
            if (!children.contains(c)) top.add(c);
        }
        // 极端兜底：若全部互为嵌套导致为空，退回使用全部候选
        return top.isEmpty() ? candidates : top;
    }

    /** 递归收集 p 的所有子节点（不含 p 自身）到一个按引用身份去重的集合。 */
    @SuppressWarnings("unchecked")
    private static void collectChildren(ModelPart p, Set<ModelPart> acc) {
        for (ModelPart child : modelPartChildren(p)) {
            acc.add(child);
            collectChildren(child, acc);
        }
    }

    private static final java.lang.reflect.Field CHILDREN_FIELD = findChildrenField();

    private static java.lang.reflect.Field findChildrenField() {
        try {
            java.lang.reflect.Field f = ModelPart.class.getDeclaredField("children");
            f.setAccessible(true);
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    /** 反射读取 ModelPart 的私有 children 映射；失败时返回空集。 */
    @SuppressWarnings("unchecked")
    private static java.util.List<ModelPart> modelPartChildren(ModelPart p) {
        try {
            if (CHILDREN_FIELD == null) return java.util.Collections.emptyList();
            java.util.Map<String, ModelPart> map =
                (java.util.Map<String, ModelPart>) CHILDREN_FIELD.get(p);
            return map == null ? java.util.Collections.emptyList() : new ArrayList<>(map.values());
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    /** 取该实体渲染用的纹理；通过渲染派发器获取其渲染器再取纹理。 */
    private ResourceLocation textureOf(LivingEntity entity) {
        try {
            LivingEntityRenderer<LivingEntity, EntityModel<LivingEntity>> renderer =
                (LivingEntityRenderer<LivingEntity, EntityModel<LivingEntity>>) net.minecraft.client.Minecraft
                    .getInstance().getEntityRenderDispatcher().getRenderer(entity);
            return renderer.getTextureLocation(entity);
        } catch (Exception ignored) {
            return null;
        }
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
}