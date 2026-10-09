package cn.autoforged.joes_addons_for_abmc.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 附体体系的"危险地形"判定与回避工具：<b>水、岩浆、火（含灵魂火）、凋灵玫瑰</b>。
 *
 * <p>原版骷髅那套走位（{@code RangedBowAttackGoal} 的移动部分，本模组照搬到
 * {@link OrbPossessionKiteGoal}）完全不管脚下是什么，于是空壳会自己走进火里、
 * 围着岩浆侧移、贴着凋灵玫瑰转圈。这里把判定单独抽出来，走位时用它改写方向。
 *
 * <p>注意"水"也算危险：空壳掉进水里虽然不会死，但会失去走位能力、被动挨打，
 * 而且水里的三叉戟/弓箭手感全变，所以一并规避。
 */
public final class OrbHazardAvoidance {

    private OrbHazardAvoidance() {
    }

    /** 这个方块状态（含它承载的流体）是不是要避开的地形。 */
    public static boolean isHazard(BlockState state) {
        if (state.is(Blocks.WITHER_ROSE)) {
            return true;   // 凋灵玫瑰：碰到持续凋零
        }
        if (state.is(BlockTags.FIRE)) {
            return true;   // 火 / 灵魂火
        }
        if (state.getFluidState().is(FluidTags.LAVA)) {
            return true;   // 岩浆（含流动岩浆）
        }
        return state.getFluidState().is(FluidTags.WATER);   // 水（含含水方块）
    }

    /** 指定位置是不是危险地形。 */
    public static boolean isHazardAt(BlockGetter level, BlockPos pos) {
        return isHazard(level.getBlockState(pos));
    }

    /**
     * 实体当前位置附近有没有危险地形：扫它碰撞箱覆盖的方块，再额外看脚下一格
     * （脚下一格常常正是"站在岩浆/火/水里"的那个方块）。
     *
     * @return 第一个命中的危险方块位置（不可变副本）；没有返回 null
     */
    @Nullable
    public static BlockPos hazardAt(Entity entity) {
        AABB box = entity.getBoundingBox();
        BlockPos min = BlockPos.containing(box.minX + 1.0E-4, box.minY - 0.2, box.minZ + 1.0E-4);
        BlockPos max = BlockPos.containing(box.maxX - 1.0E-4, box.maxY - 1.0E-4, box.maxZ - 1.0E-4);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (isHazardAt(entity.level(), pos)) {
                return pos.immutable();
            }
        }
        return null;
    }

    /**
     * 沿"某个走位指令"的方向往前探一段距离，看看那里是不是危险地形。
     * <p>
     * 指令是世界无关的输入对（{@code forward} = 前后、{@code sideways} = 左右，即 {@code zza/xxa}），
     * 这里用游戏自己的输入向量公式把它换算成世界方向（{@code Entity#getInputVector} 同款），
     * 所以符号不会搞反。
     */
    public static boolean hazardAhead(Entity entity, double forward, double sideways, double distance) {
        Vec3 direction = inputToWorld(forward, sideways, entity.getYRot());
        if (direction.lengthSqr() < 1.0E-6) {
            return false;
        }
        Vec3 probe = entity.position().add(direction.normalize().scale(distance));
        // 探针取"脚所在那一格"与"脚下一格"：前者管走进火里，后者管走进浅岩浆/水里
        BlockPos pos = BlockPos.containing(probe.x, entity.getY() + 0.1, probe.z);
        return isHazardAt(entity.level(), pos) || isHazardAt(entity.level(), pos.below());
    }

    /**
     * 把 {@code (前后, 左右)} 输入换算成世界水平方向（{@code Entity#getInputVector} 的公式）。
     */
    public static Vec3 inputToWorld(double forward, double sideways, float yawDegrees) {
        float sin = Mth.sin(yawDegrees * (float) (Math.PI / 180.0));
        float cos = Mth.cos(yawDegrees * (float) (Math.PI / 180.0));
        return new Vec3(sideways * (double) cos - forward * (double) sin, 0.0,
            forward * (double) cos + sideways * (double) sin);
    }

    /**
     * 把世界水平方向换算回 {@code (前后, 左右)} 输入（上面那个公式的逆），
     * 让调用方能用 {@code MoveControl#strafe} 朝任意世界方向走。
     *
     * @return 长度为 2 的数组：{@code [前后, 左右]}
     */
    public static double[] worldToInput(Vec3 world, float yawDegrees) {
        float sin = Mth.sin(yawDegrees * (float) (Math.PI / 180.0));
        float cos = Mth.cos(yawDegrees * (float) (Math.PI / 180.0));
        double forward = world.z * (double) cos - world.x * (double) sin;
        double sideways = world.x * (double) cos + world.z * (double) sin;
        return new double[] { forward, sideways };
    }
}
