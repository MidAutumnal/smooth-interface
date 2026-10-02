package com.micah.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.micah.animation.InterfaceAnimator;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;

/**
 * 挂在 {@link Gui#extractRenderState} 上，拿到「每帧一次」的钩子。
 *
 * 整帧的渲染顺序是：
 *
 *   Gui.extractRenderState           ← beginFrame() 在这里
 *     ├─ Hud.extractRenderState
 *     ├─ Screen.extractRenderStateWithTooltipAndSubtitles
 *     │    ├─ extractBackground
 *     │    ├─ extractRenderState
 *     │    └─ extractDeferredElements   ← tooltip 在这里画
 *     └─ ...
 *                                   ← endOfExtraction() 在这里（淡出补画）
 *   GuiRenderer.render()             ← 之后才真正提交 GPU
 *
 * 也就是说「提取」阶段把所有绘制指令收集起来，后面才统一渲染，
 * 所以我们在提取阶段修改颜色是来得及的。
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V", at = @At("HEAD"))
	private void smoothinterface$beginFrame(DeltaTracker deltaTracker, boolean shouldRenderLevel,
			boolean resourcesLoaded, CallbackInfo ci) {
		InterfaceAnimator.beginFrame(deltaTracker);
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V", at = @At("TAIL"))
	private void smoothinterface$endOfExtraction(DeltaTracker deltaTracker, boolean shouldRenderLevel,
			boolean resourcesLoaded, CallbackInfo ci) {
		InterfaceAnimator.endOfExtraction();
	}

	@Inject(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("TAIL"))
	private void smoothinterface$onScreenChanged(Screen requestedScreen, CallbackInfo ci) {
		// 注意：参数 requestedScreen 在方法内部可能被重新赋值（比如退回到标题界面），
		// 所以这里读字段的 getter 才是最终结果
		InterfaceAnimator.onScreenChanged(((Gui) (Object) this).screen());
	}
}
