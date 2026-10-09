package cn.autoforged.joes_addons_for_abmc.client;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 翅膀的模型：直接复用原版<b>鞘翅</b>烘焙好的网格（{@code ModelLayers.ELYTRA}，部件名
 * {@code left_wing} / {@code right_wing}），只是把"设置角度"的接口开放出来。
 *
 * <p>为什么不用 {@code ElytraModel} 本体：它的两个翅膀字段是 <b>private</b>，角度只能在
 * {@code setupAnim} 里由它自己按"是否滑翔"写死，外部无法改成我们想要的"两个角度之间按正弦插值"。
 * 网格数据本身是可以共用的，所以这里只包一层（继承 {@code AgeableListModel} 与鞘翅模型一致）。
 */
@OnlyIn(Dist.CLIENT)
public class WingsModel<T extends LivingEntity> extends AgeableListModel<T> {
    private final ModelPart leftWing;
    private final ModelPart rightWing;

    public WingsModel(ModelPart root) {
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
    }

    @Override
    protected Iterable<ModelPart> headParts() {
        return ImmutableList.of();
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(this.leftWing, this.rightWing);
    }

    /** 角度留空：本模型的姿态完全由 {@link #setWingAngles} 指定（渲染层每帧算好再写进来）。 */
    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
    }

    /**
     * 设置两翼角度（左右镜像），字段含义与 {@code ElytraModel#setupAnim} 里那份代码完全一致：
     *
     * @param yOffset 两翼整体上下偏移（原版潜行姿态用 3.0F）
     * @param xRot    俯仰（原版：不滑翔 π/12、滑翔 π/9）
     * @param yRot    偏航（原版：潜行 0.08726646F，其余 0）
     * @param zRot    翻滚（原版：不滑翔 -π/12、滑翔 -π/2）
     */
    public void setWingAngles(float yOffset, float xRot, float yRot, float zRot) {
        this.leftWing.y = yOffset;
        this.leftWing.xRot = xRot;
        this.leftWing.yRot = yRot;
        this.leftWing.zRot = zRot;

        this.rightWing.y = yOffset;
        this.rightWing.xRot = xRot;
        this.rightWing.yRot = -yRot;
        this.rightWing.zRot = -zRot;
    }
}
