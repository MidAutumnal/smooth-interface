package com.micah.mixin;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.micah.animation.InterfaceAnimator;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * 平滑效果的核心。
 *
 * <p>26.2 里 GUI 不再「边算边画」，而是先把所有绘制指令丢进
 * {@code GuiRenderState}，最后统一提交。所有绘制最终都收敛到几个方法：</p>
 * <ul>
 *     <li>{@code text(...)} —— 文字，内部会 new 一个 GuiTextRenderState</li>
 *     <li>{@code blitSprite(...)} —— 精灵图（tooltip 的圆角背景、边框就是它画的）</li>
 *     <li>{@code itemCount(...)} —— 槽位右下角的物品数量数字，它内部也是调 text()</li>
 * </ul>
 *
 * <p>所以只要在这些方法入口处，把颜色参数的 alpha 乘上一个系数，
 * 就能让「这一段时间内提交的所有内容」整体变透明。再配合
 * {@link InterfaceAnimator} 打开/关闭的作用域，就能把影响范围精确限制在
 * tooltip 和物品数量数字上。</p>
 *
 * <p>关于彩色文字：物品名常带颜色（附魔、自定义名），那种颜色是写在
 * {@code Style} 里的。好消息是原版 {@code Font} 计算最终颜色时是
 * {@code ARGB.color(ARGB.alpha(基础颜色), 样式颜色)}，也就是「基础颜色的 alpha 会被沿用」。
 * 所以我们只改基础颜色的 alpha，带颜色的文字也会跟着一起淡入淡出。</p>
 *
 * <p>另外 tooltip 还会额外做一次缩放 + 位移，那是通过往 GUI 的 2D 矩阵栈里压一层变换实现的，
 * 见 {@link InterfaceAnimator#pushTooltipTransform}。</p>
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {

	/** 记录当前帧的渲染上下文，淡出补画时需要它。 */
	@Inject(method = "<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/renderer/state/gui/GuiRenderState;II)V",
			at = @At("TAIL"))
	private void smoothinterface$rememberInstance(Minecraft minecraft, GuiRenderState renderState, int mouseX,
			int mouseY, CallbackInfo ci) {
		InterfaceAnimator.setCurrentExtractor((GuiGraphicsExtractor) (Object) this);
	}

	/** tooltip 开始绘制：记录参数 + 打开透明度作用域 + 压入缩放/位移变换。 */
	@Inject(method = "tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V",
			at = @At("HEAD"))
	private void smoothinterface$beforeTooltip(Font font, List<ClientTooltipComponent> lines, int xo, int yo,
			ClientTooltipPositioner positioner, @Nullable Identifier style, CallbackInfo ci) {
		if (!InterfaceAnimator.isRenderingCachedTooltip()) {
			InterfaceAnimator.onTooltipRendered(font, lines, xo, yo, positioner, style);
		}

		InterfaceAnimator.beginFadeScope();

		// 这条要在方法体自己的 pose.pushMatrix() 之前执行，pop 则等方法返回后再做
		InterfaceAnimator.pushTooltipTransform((GuiGraphicsExtractor) (Object) this, xo, yo);
	}

	/** tooltip 绘制结束：撤销变换 + 关闭作用域。 */
	@Inject(method = "tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V",
			at = @At("RETURN"))
	private void smoothinterface$afterTooltip(Font font, List<ClientTooltipComponent> lines, int xo, int yo,
			ClientTooltipPositioner positioner, @Nullable Identifier style, CallbackInfo ci) {
		InterfaceAnimator.popTooltipTransform((GuiGraphicsExtractor) (Object) this);
		InterfaceAnimator.endFadeScope();
	}

	/**
	 * 槽位里的物品数量数字。
	 *
	 * 原版实现就是「拿到数量 -> 算个右下角坐标 -> 调 text()」，
	 * 所以在这里开一个作用域就够了，真正改颜色的是下面拦截 text 的那一段。
	 */
	@Inject(method = "itemCount(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
			at = @At("HEAD"))
	private void smoothinterface$beforeItemCount(Font font, ItemStack itemStack, int x, int y,
			@Nullable String countText, CallbackInfo ci) {
		InterfaceAnimator.beginItemCountScope(x, y, itemStack.getCount());
	}

	@Inject(method = "itemCount(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
			at = @At("RETURN"))
	private void smoothinterface$afterItemCount(Font font, ItemStack itemStack, int x, int y,
			@Nullable String countText, CallbackInfo ci) {
		InterfaceAnimator.endItemCountScope();
	}

	/**
	 * 文字：把颜色 alpha 乘上当前透明度。
	 *
	 * 用 cancellable + 重新调用带新颜色的重载，比 @ModifyArg 更直观，
	 * 代价是需要一个守卫防止自己拦截自己（见 withoutFade）。
	 */
	@Inject(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
			at = @At("HEAD"), cancellable = true)
	private void smoothinterface$fadeText(Font font, FormattedCharSequence text, int x, int y, int color,
			boolean dropShadow, CallbackInfo ci) {
		if (!InterfaceAnimator.shouldApplyFade()) {
			return;
		}

		GuiGraphicsExtractor self = (GuiGraphicsExtractor) (Object) this;
		int faded = ARGB.multiplyAlpha(color, InterfaceAnimator.currentAlpha());

		InterfaceAnimator.withoutFade(() -> self.text(font, text, x, y, faded, dropShadow));
		ci.cancel();
	}

	/** 精灵图（tooltip 背景 / 边框）：同样乘上 alpha。 */
	@Inject(method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
			at = @At("HEAD"), cancellable = true)
	private void smoothinterface$fadeSprite(RenderPipeline pipeline, Identifier location, int x, int y, int width,
			int height, CallbackInfo ci) {
		if (!InterfaceAnimator.shouldApplyFade()) {
			return;
		}

		GuiGraphicsExtractor self = (GuiGraphicsExtractor) (Object) this;
		int faded = ARGB.white(InterfaceAnimator.currentAlpha());

		InterfaceAnimator.withoutFade(() -> self.blitSprite(pipeline, location, x, y, width, height, faded));
		ci.cancel();
	}
}
