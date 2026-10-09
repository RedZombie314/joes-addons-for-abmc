package cn.autoforged.joes_addons_for_abmc.client.model;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * 幸运方块选择器的三段动画：{@link #STRETCH}（展开）、{@link #RETREAT}（收回）、{@link #SEND}（发送）。
 * <p>
 * 由 Blockbench 5.2.1 导出后移植，改动只有：
 * <ul>
 *   <li>补 package / import；</li>
 *   <li>类名 {@code selectorAnimation} → {@code LuckySelectorAnimations}，常量
 *       {@code stretch/retreat/send} → {@code STRETCH/RETREAT/SEND}（跟随工程里常量大写的写法）；</li>
 *   <li><b>骨骼名 {@code "1"}~{@code "8"} → {@code "part1"}~{@code "part8"}</b>，
 *       与 {@link LuckySelectorModel} 里的键一致（模型那边因为 {@code 1} 不是合法 Java 标识符而改的名）。</li>
 * </ul>
 * 关键帧数值一律原样保留。
 */
public class LuckySelectorAnimations {
	public static final AnimationDefinition STRETCH = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("part8", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.25F, KeyframeAnimations.posVec(0.1F, 0.1F, 0.1F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.2917F, KeyframeAnimations.posVec(1.2F, 1.2F, 1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(1.2F, 1.2F, 1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(1.2F, -22.8F, 1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.posVec(1.2F, -22.8F, 1.2F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part1", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.25F, KeyframeAnimations.posVec(-0.2F, -0.2F, -0.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.2917F, KeyframeAnimations.posVec(-1.2F, -1.2F, -1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(-1.2F, -1.2F, -1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(-1.2F, -25.2F, -1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.posVec(-1.2F, -25.2F, -1.2F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part2", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.25F, KeyframeAnimations.posVec(0.2F, -0.2F, -0.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.2917F, KeyframeAnimations.posVec(1.2F, -1.2F, -1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(1.2F, -1.2F, -1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(1.2F, -25.2F, -1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.posVec(1.2F, -25.2F, -1.2F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part3", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.25F, KeyframeAnimations.posVec(0.2F, -0.2F, 0.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.2917F, KeyframeAnimations.posVec(1.2F, -1.2F, 1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(1.2F, -1.2F, 1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(1.2F, -25.2F, 1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.posVec(1.2F, -25.2F, 1.2F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part4", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.25F, KeyframeAnimations.posVec(-0.2F, -0.2F, 0.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.2917F, KeyframeAnimations.posVec(-1.2F, -1.2F, 1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(-1.2F, -1.2F, 1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(-1.2F, -25.2F, 1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.posVec(-1.2F, -25.2F, 1.2F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part5", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.25F, KeyframeAnimations.posVec(-0.2F, 0.2F, 0.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.2917F, KeyframeAnimations.posVec(-1.2F, 1.2F, 1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(-1.2F, 1.2F, 1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(-1.2F, -22.8F, 1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.posVec(-1.2F, -22.8F, 1.2F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part6", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.25F, KeyframeAnimations.posVec(-0.2F, 0.2F, -0.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.2917F, KeyframeAnimations.posVec(-1.2F, 1.2F, -1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(-1.2F, 1.2F, -1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(-1.2F, -22.8F, -1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.posVec(-1.2F, -22.8F, -1.2F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part7", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.25F, KeyframeAnimations.posVec(0.2F, 0.2F, -0.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.2917F, KeyframeAnimations.posVec(1.2F, 1.2F, -1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(1.2F, 1.2F, -1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(1.2F, -22.8F, -1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.posVec(1.2F, -22.8F, -1.2F), AnimationChannel.Interpolations.LINEAR)
		))
		.build();

	public static final AnimationDefinition RETREAT = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("part1", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(-1.2F, -25.2F, -1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.25F, KeyframeAnimations.posVec(-1.0F, -25.0F, -1.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.2917F, KeyframeAnimations.posVec(0.0F, -24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, -24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part2", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(1.2F, -25.2F, -1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.25F, KeyframeAnimations.posVec(1.0F, -25.0F, -1.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.2917F, KeyframeAnimations.posVec(0.0F, -24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, -24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part3", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(1.2F, -25.2F, 1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.25F, KeyframeAnimations.posVec(1.0F, -25.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.2917F, KeyframeAnimations.posVec(0.0F, -24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, -24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part4", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(-1.2F, -25.2F, 1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.25F, KeyframeAnimations.posVec(-1.0F, -25.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.2917F, KeyframeAnimations.posVec(0.0F, -24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, -24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part5", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(-1.2F, -22.8F, 1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.25F, KeyframeAnimations.posVec(-1.0F, -23.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.2917F, KeyframeAnimations.posVec(0.0F, -24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, -24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part6", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(-1.2F, -22.8F, -1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.25F, KeyframeAnimations.posVec(-1.0F, -23.0F, -1.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.2917F, KeyframeAnimations.posVec(0.4F, -24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.4F, -24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part7", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(1.2F, -22.8F, -1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.25F, KeyframeAnimations.posVec(1.0F, -23.0F, -1.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.2917F, KeyframeAnimations.posVec(0.0F, -24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, -24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part8", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(1.2F, -22.8F, 1.2F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.25F, KeyframeAnimations.posVec(1.0F, -23.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.2917F, KeyframeAnimations.posVec(0.0F, -24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, -24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.build();

	public static final AnimationDefinition SEND = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("part1", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.9583F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part1", new AnimationChannel(AnimationChannel.Targets.SCALE, 
			new Keyframe(0.5F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.scaleVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.9583F, KeyframeAnimations.scaleVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part2", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.9583F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part2", new AnimationChannel(AnimationChannel.Targets.SCALE, 
			new Keyframe(0.5F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.scaleVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.9583F, KeyframeAnimations.scaleVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part3", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.9583F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part3", new AnimationChannel(AnimationChannel.Targets.SCALE, 
			new Keyframe(0.5F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.scaleVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.9583F, KeyframeAnimations.scaleVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part4", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.9583F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part4", new AnimationChannel(AnimationChannel.Targets.SCALE, 
			new Keyframe(0.5F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.scaleVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.9583F, KeyframeAnimations.scaleVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part5", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.9583F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part5", new AnimationChannel(AnimationChannel.Targets.SCALE, 
			new Keyframe(0.5F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.scaleVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.9583F, KeyframeAnimations.scaleVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part6", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.9583F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part6", new AnimationChannel(AnimationChannel.Targets.SCALE, 
			new Keyframe(0.5F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.scaleVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.9583F, KeyframeAnimations.scaleVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part7", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.9583F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part7", new AnimationChannel(AnimationChannel.Targets.SCALE, 
			new Keyframe(0.5F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.scaleVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.9583F, KeyframeAnimations.scaleVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part8", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 24.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.9583F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("part8", new AnimationChannel(AnimationChannel.Targets.SCALE, 
			new Keyframe(0.5F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.75F, KeyframeAnimations.scaleVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.9583F, KeyframeAnimations.scaleVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(1.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.build();
}