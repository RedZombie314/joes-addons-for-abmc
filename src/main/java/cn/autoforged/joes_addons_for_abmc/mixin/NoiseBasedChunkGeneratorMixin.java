package cn.autoforged.joes_addons_for_abmc.mixin;

import cn.autoforged.joes_addons_for_abmc.block.ModBlocks;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>幸运维度不再生成地底岩浆</b>：把该维度的"含水层流体选择器"换成不含岩浆的版本。
 *
 * <h2>症状与排查</h2>
 * 幸运维度地底（{@code y ≤ -55}）成片岩浆。它<b>不是</b>刷出来的特征：
 * {@code data/joes_addons_for_abmc/worldgen/biome/lucky_plains.json} 里
 * {@code "features": []}、{@code "carvers": {}} —— 这个生物群系一个特征、一个雕刻器都没有，
 * 所以 {@code minecraft:lake_lava} / {@code lake_lava_underground} 这种岩浆湖特征根本不可能出现。
 * 岩浆来自<b>噪声地形生成器自己写死的一条规则</b>。
 *
 * <h2>原版规则</h2>
 * {@code NoiseBasedChunkGenerator#createFluidPicker(NoiseGeneratorSettings)}（源代码 68-74 行）里：
 * <pre>
 * FluidStatus 岩浆 = new FluidStatus(-54, LAVA);            // 深层岩浆：液面固定 -54
 * int 海平面 = settings.seaLevel();
 * FluidStatus 默认 = new FluidStatus(海平面, settings.defaultFluid());
 * return (x, y, z) -&gt; y &lt; Math.min(-54, 海平面) ? 岩浆 : 默认;
 * </pre>
 * 配合 {@code Aquifer.FluidStatus#at(int y)}（{@code Aquifer.java:82-84}）的
 * {@code y < fluidLevel ? fluidType : AIR} —— 也就是"<b>液面以下才是这种流体</b>"。
 *
 * <h2>为什么偏偏这个维度中招</h2>
 * <ol>
 *   <li>本维度的噪声设置 {@code lucky_dim.json} 里 {@code "aquifers_enabled": false}，
 *       于是 {@code NoiseChunk} 构造函数（{@code NoiseChunk.java:140-141}）走
 *       {@code Aquifer.createDisabled(...)}：<b>不做任何噪声插值</b>，
 *       凡是密度 ≤ 0（非实心）的格子直接照上面这个选择器填（{@code Aquifer.java:43-56}）。</li>
 *   <li>本维度 {@code "sea_level": 0}、{@code default_fluid} 是空气，
 *       所以 {@code Math.min(-54, 0) = -54}：<b>y &lt; -54 一律选中岩浆那一档</b>，
 *       而岩浆档的液面恰好也是 -54，于是每个非实心格子都填成岩浆。
 *       y ≥ -54 那档是"海平面 0 的空气"，什么都不填 —— 所以岩浆只出现在最底下。</li>
 *   <li>本维度 {@code min_y = -64}（{@code ModDimensions.LUCKY_DIM_TYPE}，见 {@code ModDimensions.java:111-112}），
 *       于是 {@code y ∈ [-64, -55]} 这 <b>10 层</b>全部变成岩浆 —— 就是玩家看到的"地底岩浆湖"。</li>
 * </ol>
 * 换句话说：岩浆不是幸运维度"多加"的东西，而是原版<b>主世界深层岩浆层</b>的规则
 * 在一个"海平面 0 + 默认流体是空气 + 关掉含水层"的维度里被原样套用了。
 *
 * <h2>修复方式</h2>
 * 在 {@code createFluidPicker} 的 {@code RETURN} 处，如果这份噪声设置的默认方块就是幸运维度方块
 * （这是该维度噪声设置的唯一标识：{@code ModNoiseSettings} 里只有 {@code LUCKY_DIM}
 * 用它当 {@code defaultBlock}，见 {@code ModNoiseSettings.java:31}），
 * 就把整个选择器换成"永远只返回默认流体"的版本：
 * <b>只删掉原版那条深层岩浆，其余（海平面以下填默认流体）行为一字不改</b>。
 *
 * <p>本 mixin 只认这一种噪声设置，所以主世界、音符盒维度、苦力怕氏族维度
 * （它们用的是 {@code NoiseGeneratorSettings.OVERWORLD}，默认方块是石头）仍然保留原版的深层岩浆；
 * 物理虚空维度走的是 {@code FlatLevelSource}，根本不经过这里。</p>
 *
 * <p>{@code require = 0}：万一将来原版改了这段生成代码，注入失效时只是"岩浆照旧"
 * （幸运维度恢复原版行为），不会崩游戏。</p>
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {

    // cancellable = true 是必须的：@Inject 里只要用了 CallbackInfo#cancel / CallbackInfoReturnable#setReturnValue，
    // 就必须把注入点声明成可取消，否则运行时会抛
    // CancellationException: The call createFluidPicker is not cancellable（进维度时直接崩）。
    // 注意：这条规则编译期不校验 —— 注解处理器只检查注入点和 handler 签名，漏了照样 BUILD SUCCESSFUL。
    @Inject(method = "createFluidPicker", at = @At("RETURN"), cancellable = true, require = 0)
    private static void joes_addons_for_abmc$noDeepLava(
        NoiseGeneratorSettings settings, CallbackInfoReturnable<Aquifer.FluidPicker> cir
    ) {
        if (!joes_addons_for_abmc$isLuckyDimension(settings)) {
            return;
        }
        // 对应原版 createFluidPicker 里的 "FluidStatus 默认 = new FluidStatus(seaLevel, defaultFluid)"：
        // 保留默认流体与海平面，只是不再往选择器里放那个液面 -54 的岩浆档。
        Aquifer.FluidStatus defaultFluidOnly = new Aquifer.FluidStatus(settings.seaLevel(), settings.defaultFluid());
        cir.setReturnValue((x, y, z) -> defaultFluidOnly);
    }

    /** 幸运维度噪声设置的判别依据：默认方块是幸运维度方块。 */
    private static boolean joes_addons_for_abmc$isLuckyDimension(NoiseGeneratorSettings settings) {
        return settings.defaultBlock().is(ModBlocks.LUCKY_DIMENSION_BLOCK.get());
    }
}
