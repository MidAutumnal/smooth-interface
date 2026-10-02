package com.micah.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.micah.animation.InterfaceAnimator;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.ARGB;

/**
 * 界面打开时的淡入。
 *
 * 这里没有用「改颜色」那一套，原因是：整个界面的绘制入口太多（背景纹理、按钮、
 * 物品图标、3D 预览……），而且物品图标是在另一阶段由物品图集统一转成 blit 的，
 * 逐个改 alpha 既容易漏也容易崩。
 *
 * 所以改用一个更稳的经典做法：在最上面盖一层黑色遮罩，界面刚出现时它最黑，
 * 之后在 200ms 内淡到全透明 —— 视觉上就是「界面从暗到亮平滑浮现」。
 * 好处是：不管界面里画了什么，遮罩都一视同仁，绝对不会出现「文字淡了但物品没淡」的撕裂感。
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
			at = @At("TAIL"))
	private void smoothinterface$fadeInOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick, CallbackInfo ci) {
		float dim = InterfaceAnimator.screenDim();

		if (dim <= 0.0F) {
			return;
		}

		// 单独开一层，保证遮罩盖在整个界面（含 tooltip）之上。
		// ARGB.black(alpha) 就是「纯黑 + 指定透明度」。
		graphics.nextStratum();
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.black(dim));
	}
}
