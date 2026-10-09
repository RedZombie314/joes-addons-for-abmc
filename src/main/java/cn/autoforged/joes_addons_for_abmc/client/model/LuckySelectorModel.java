package cn.autoforged.joes_addons_for_abmc.client.model;

import cn.autoforged.joes_addons_for_abmc.ModMain;
import cn.autoforged.joes_addons_for_abmc.entity.LuckySelectorEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 幸运方块选择器（Lucky Selector）的模型：一个「选中框」。
 * <p>
 * 由 Blockbench 5.1.6 导出（Mojang 映射）后移植，相对导出件只做了必要修改：
 * <ul>
 *   <li>补 package / import（Blockbench 不写这两样）；</li>
 *   <li><b>骨骼名 {@code 1}~{@code 8} 改成 {@code part1}~{@code part8}</b>——导出件里
 *       {@code private final ModelPart 1;} / {@code this.1 = ...} / {@code PartDefinition 1 = ...}
 *       是非法 Java 标识符（标识符不能以数字开头），原样放进项目连编译都过不去；</li>
 *   <li>{@code new ResourceLocation("modid", ...)} 改成 {@code ResourceLocation.fromNamespaceAndPath(...)}
 *       并填真实 modid：1.21.1 里那个两参构造函数已经是 private；</li>
 *   <li>{@code EntityModel<T>} 泛型特化为 {@link LuckySelectorEntity}，{@code setupAnim} 参数同步收紧；</li>
 *   <li>父类从 {@code EntityModel} 改成 {@link HierarchicalModel}：动画助手 {@code animate(...)} 只有它（以及
 *       更下面的子类）才有，{@code EntityModel} 上没有；而且它已经实现了 {@code renderToBuffer}（渲染 {@code root()}），
 *       所以导出件里那个 8 个 float 的 {@code renderToBuffer} 直接删掉。
 *       <br>（踩过的坑：NeoForge 21.1.235 里 {@code Model} 的抽象版本是带 ARGB 颜色的 5 参版
 *       {@code (PoseStack, VertexConsumer, int, int, int color)}，Blockbench 导出的 8 个 float 版本不存在。）</li>
 *   <li>{@code setupAnim} 接上三段动画（见 {@link LuckySelectorAnimations}）。</li>
 * </ul>
 * <p>
 * 下面这些 {@code bone1}/{@code bone2}/{@code part1}~{@code part8} 字段沿用导出件：三段动画由
 * {@code AnimationDefinition} <b>按骨骼名</b>驱动，并不经过这些字段；留着它们是为了以后要写代码式姿态时能直接用。
 * <p>
 * <b>形状</b>：{@code bone1} 是 16 单位见方（=1 格）的线框立方体，贴地（模型空间 y=24 是脚底，方块落在 y=8~24）；
 * {@code bone2} 的 8 个子节点是围成 14 单位、稍小一圈的角落标记。贴图 64×64。
 */
public class LuckySelectorModel extends HierarchicalModel<LuckySelectorEntity> {
    /** 模型根节点：父类的 renderToBuffer 与动画助手都基于它。 */
    private final ModelPart root;
    /** 模型层：由 ClientEvents#registerLayerDefinitions 注册，渲染器用 bakeLayer 取用。 */
    public static final ModelLayerLocation LAYER_LOCATION =
        new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "lucky_selector"), "main");

    private final ModelPart bone1;
    private final ModelPart bone2;
    private final ModelPart part1;
    private final ModelPart part2;
    private final ModelPart part3;
    private final ModelPart part4;
    private final ModelPart part5;
    private final ModelPart part6;
    private final ModelPart part7;
    private final ModelPart part8;

    public LuckySelectorModel(ModelPart root) {
        // 半透明渲染层：模型默认的 entityCutoutNoCull 是「cutout」——alpha==0 的像素被剔除，
        // 其余像素**一律按不透明画**，所以贴图里半透明的笔触会被压成实心（本贴图整张 alpha 只有 127/191）。
        // entityTranslucent 才真正做 alpha 混合；它同时是 NO_CULL，线框盒子的内侧面也会画出来。
        super(RenderType::entityTranslucent);
        this.root = root;
        this.bone1 = root.getChild("bone1");
        this.bone2 = root.getChild("bone2");
        this.part1 = this.bone2.getChild("part1");
        this.part2 = this.bone2.getChild("part2");
        this.part3 = this.bone2.getChild("part3");
        this.part4 = this.bone2.getChild("part4");
        this.part5 = this.bone2.getChild("part5");
        this.part6 = this.bone2.getChild("part6");
        this.part7 = this.bone2.getChild("part7");
        this.part8 = this.bone2.getChild("part8");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition bone1 = partdefinition.addOrReplaceChild("bone1", CubeListBuilder.create().texOffs(4, 17).addBox(0.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(18, 10).addBox(15.0F, -1.0F, -10.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(28, 30).addBox(2.0F, -16.0F, -16.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(24, 30).addBox(4.0F, -16.0F, -16.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(20, 30).addBox(6.0F, -16.0F, -16.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(16, 30).addBox(8.0F, -16.0F, -16.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(12, 30).addBox(10.0F, -16.0F, -16.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(30, 10).addBox(12.0F, -16.0F, -16.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(30, 8).addBox(14.0F, -16.0F, -16.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(18, 8).addBox(12.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(18, 6).addBox(10.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(18, 4).addBox(8.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(18, 2).addBox(6.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(18, 0).addBox(4.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(8, 17).addBox(2.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-8.0F, 24.0F, 8.0F));

        PartDefinition cube_r1 = bone1.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(12, 18).addBox(14.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(16, 18).addBox(2.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(4, 19).addBox(4.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(8, 19).addBox(6.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(0, 20).addBox(8.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(12, 20).addBox(10.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(20, 12).addBox(12.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -17.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition cube_r2 = bone1.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(4, 29).addBox(2.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(8, 29).addBox(4.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(0, 30).addBox(6.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(30, 0).addBox(8.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(30, 2).addBox(10.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(30, 4).addBox(12.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(30, 6).addBox(14.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(15.0F, -15.0F, -17.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition cube_r3 = bone1.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(4, 27).addBox(0.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(26, 6).addBox(0.0F, 1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(26, 8).addBox(0.0F, 3.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(26, 10).addBox(0.0F, 5.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(12, 26).addBox(0.0F, 7.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(16, 26).addBox(0.0F, 9.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(20, 26).addBox(0.0F, 11.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(15.0F, -15.0F, -1.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition cube_r4 = bone1.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(24, 26).addBox(0.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(20, 18).addBox(6.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(15.0F, 0.0F, -8.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition cube_r5 = bone1.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(24, 24).addBox(0.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(4, 25).addBox(0.0F, 1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(8, 25).addBox(0.0F, 3.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(0, 26).addBox(0.0F, 5.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(26, 0).addBox(0.0F, 7.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(26, 2).addBox(0.0F, 9.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(26, 4).addBox(0.0F, 11.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -14.0F, -1.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition cube_r6 = bone1.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(8, 27).addBox(0.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(24, 14).addBox(0.0F, 1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(16, 24).addBox(0.0F, 3.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(24, 16).addBox(0.0F, 5.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(24, 18).addBox(0.0F, 7.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(20, 24).addBox(0.0F, 9.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(24, 20).addBox(0.0F, 11.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(24, 22).addBox(0.0F, 13.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(0, 28).addBox(2.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(12, 28).addBox(4.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(28, 12).addBox(6.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(28, 14).addBox(8.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(16, 28).addBox(12.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(28, 16).addBox(10.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(28, 18).addBox(14.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -15.0F, -16.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition cube_r7 = bone1.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(8, 23).addBox(0.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(0, 24).addBox(0.0F, 1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(12, 24).addBox(0.0F, 3.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(24, 12).addBox(0.0F, 5.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(20, 22).addBox(0.0F, 7.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(4, 23).addBox(0.0F, 9.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(16, 22).addBox(0.0F, 11.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(20, 14).addBox(0.0F, 13.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(16, 20).addBox(2.0F, 13.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(20, 16).addBox(4.0F, 13.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(15.0F, -14.0F, -16.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition cube_r8 = bone1.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(20, 20).addBox(8.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(15.0F, 0.0F, -14.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition cube_r9 = bone1.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(4, 21).addBox(10.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(14.0F, 0.0F, -11.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition cube_r10 = bone1.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(8, 21).addBox(12.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(15.0F, -1.0F, -13.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition cube_r11 = bone1.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(0, 22).addBox(14.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(15.0F, 0.0F, -18.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition cube_r12 = bone1.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(22, 0).addBox(14.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(22, 2).addBox(2.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(22, 4).addBox(4.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(22, 6).addBox(6.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(22, 8).addBox(8.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(22, 10).addBox(10.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(12, 22).addBox(12.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(16.0F, 0.0F, -16.0F, 0.0F, 3.1416F, 0.0F));

        PartDefinition cube_r13 = bone1.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(20, 28).addBox(2.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(28, 20).addBox(4.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(28, 22).addBox(6.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(24, 28).addBox(8.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(28, 24).addBox(10.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(28, 26).addBox(12.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(28, 28).addBox(14.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(16.0F, -15.0F, -1.0F, 0.0F, 3.1416F, 0.0F));

        PartDefinition bone2 = partdefinition.addOrReplaceChild("bone2", CubeListBuilder.create(), PartPose.offset(7.0F, 24.0F, -7.0F));

        PartDefinition part1 = bone2.addOrReplaceChild("part1", CubeListBuilder.create(), PartPose.offset(-14.0F, -1.0F, 0.0F));

        PartDefinition cube_r14 = part1.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(12, 0).addBox(-3.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(8, 31).addBox(-1.0F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(0, 14).addBox(-1.0F, -3.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(0, 3).addBox(-1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.5708F, 0.0F));

        PartDefinition part2 = bone2.addOrReplaceChild("part2", CubeListBuilder.create().texOffs(4, 31).addBox(-1.0F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(12, 12).addBox(-1.0F, -3.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(0, 0).addBox(-1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(0, 12).addBox(-3.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.0F, 0.0F));

        PartDefinition part3 = bone2.addOrReplaceChild("part3", CubeListBuilder.create(), PartPose.offset(0.0F, -1.0F, 14.0F));

        PartDefinition cube_r15 = part3.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(0, 32).addBox(-1.0F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(4, 14).addBox(-1.0F, -3.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(0, 6).addBox(-1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(12, 2).addBox(-3.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition part4 = bone2.addOrReplaceChild("part4", CubeListBuilder.create(), PartPose.offset(-14.0F, -1.0F, 14.0F));

        PartDefinition cube_r16 = part4.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(12, 32).addBox(-1.0F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(8, 14).addBox(-1.0F, -3.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(6, 0).addBox(-1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(12, 4).addBox(-3.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.1416F, 0.0F));

        PartDefinition part5 = bone2.addOrReplaceChild("part5", CubeListBuilder.create(), PartPose.offset(-14.0F, -15.0F, 14.0F));

        PartDefinition cube_r17 = part5.addOrReplaceChild("cube_r17", CubeListBuilder.create().texOffs(32, 12).addBox(-1.0F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(12, 15).addBox(-1.0F, -3.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(6, 3).addBox(-1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(6, 12).addBox(-3.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, 3.1416F, 0.0F));

        PartDefinition part6 = bone2.addOrReplaceChild("part6", CubeListBuilder.create(), PartPose.offset(-14.0F, -15.0F, 0.0F));

        PartDefinition cube_r18 = part6.addOrReplaceChild("cube_r18", CubeListBuilder.create().texOffs(32, 14).addBox(-1.0F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(16, 12).addBox(-1.0F, -3.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(6, 6).addBox(-1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(12, 6).addBox(-3.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, 1.5708F, 0.0F));

        PartDefinition part7 = bone2.addOrReplaceChild("part7", CubeListBuilder.create(), PartPose.offset(0.0F, -15.0F, 0.0F));

        PartDefinition cube_r19 = part7.addOrReplaceChild("cube_r19", CubeListBuilder.create().texOffs(0, 17).addBox(-1.0F, -3.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(32, 16).addBox(-1.0F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(12, 10).addBox(-3.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(6, 9).addBox(-1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, 0.0F, 0.0F));

        PartDefinition part8 = bone2.addOrReplaceChild("part8", CubeListBuilder.create(), PartPose.offset(0.0F, -15.0F, 14.0F));

        PartDefinition cube_r20 = part8.addOrReplaceChild("cube_r20", CubeListBuilder.create().texOffs(16, 15).addBox(-1.0F, -3.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(16, 32).addBox(-1.0F, -1.0F, 0.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(12, 8).addBox(-3.0F, -1.0F, 0.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(0, 9).addBox(-1.0F, -1.0F, 1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, -1.5708F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    /** 模型根节点（{@link HierarchicalModel} 要求实现；父类的 renderToBuffer 就是渲染它）。 */
    @Override
    public ModelPart root() {
        return this.root;
    }

    /**
     * 原版惯例：先把所有骨骼复位，再把当前在播的动画叠加上去
     * （{@code KeyframeAnimations} 是<b>叠加</b>着写的，不复位会残留上一帧姿态）。
     * <p>
     * 三段动画动的都是 {@code bone2} 下的 {@code part1}~{@code part8} 同一批骨骼，
     * 所以同一时刻只应有一段在播——这个约束由 {@link LuckySelectorEntity} 的启动逻辑保证。
     */
    @Override
    public void setupAnim(LuckySelectorEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        this.animate(entity.stretchAnimationState, LuckySelectorAnimations.STRETCH, ageInTicks);
        this.animate(entity.retreatAnimationState, LuckySelectorAnimations.RETREAT, ageInTicks);
        this.animate(entity.sendAnimationState, LuckySelectorAnimations.SEND, ageInTicks);
    }
}
