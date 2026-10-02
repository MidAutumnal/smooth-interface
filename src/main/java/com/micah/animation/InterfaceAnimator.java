package com.micah.animation;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import com.micah.config.SmoothInterfaceConfig;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * 所有平滑效果的调度中心。
 *
 * <p>一帧的流程（由 {@code GuiMixin} 驱动）：</p>
 * <ol>
 *     <li>{@link #beginFrame} —— 一帧开始。用「上一帧有没有出现 tooltip」决定目标状态，
 *         再用本帧 dt 推进补间。放在最前面是为了让本帧渲染时读到的透明度已经是新的值。</li>
 *     <li>帧中间的渲染阶段 —— 绘制 tooltip / 物品数量时会打开「透明度作用域」，
 *         此时所有文字 / 精灵图的颜色 alpha 都会被乘以当前透明度，见 {@link #shouldApplyFade()}。</li>
 *     <li>{@link #endOfExtraction()} —— 界面提取结束。如果这一帧压根没画过 tooltip，
 *         就把上一帧缓存的 tooltip 用递减的透明度再画一遍，实现淡出。</li>
 * </ol>
 *
 * <p>为什么淡出要「补画」？因为原版是按需绘制的：鼠标离开物品后 Minecraft 就不再
 * 提交 tooltip 了，如果不自己补一帧，tooltip 会瞬间消失，根本来不及淡出。</p>
 *
 * <p>所有参数都从 {@link SmoothInterfaceConfig} 现读，所以在配置界面里改完
 * 调一次 {@link #applyConfig()} 就立刻生效，不需要重启游戏。</p>
 */
public final class InterfaceAnimator {

	/** 物品提示缓存下来的绘制参数，用于淡出时重绘。 */
	public record TooltipSnapshot(Font font, List<ClientTooltipComponent> lines, int x, int y,
			ClientTooltipPositioner positioner, @Nullable Identifier style) {
	}

	/** tooltip 的淡入淡出。时长由 {@link #applyConfig()} 推上来。 */
	private static final FadeAnimator TOOLTIP_FADE = new FadeAnimator(0.001F, 0.001F);

	/** 打开界面时黑色遮罩的淡出。 */
	private static final FadeAnimator SCREEN_FADE = createScreenFade();

	/** 本帧实际被绘制的 tooltip 参数（tooltip() 头部记录）。 */
	private static @Nullable TooltipSnapshot tooltipSnapshot;

	/**
	 * 本帧是否出现过 tooltip。
	 *
	 * <p>在 {@link #beginFrame} 里读到的就是这个字段「上一帧」的结果，
	 * 读完立刻清零，本帧的绘制过程再重新置位。</p>
	 */
	private static boolean tooltipSeenThisFrame;

	/** 正在补画缓存的 tooltip，用来避免把补画当成新的一帧 tooltip。 */
	private static boolean renderingCachedTooltip;

	/** 当前帧的 GUI 渲染上下文（GuiGraphicsExtractor 构造时记录）。 */
	private static @Nullable GuiGraphicsExtractor graphics;

	/** 帧计数器，只用来判断某个槽位还在不在屏幕上。 */
	private static int frame;

	// ==================== 透明度作用域 ====================
	/** 作用域嵌套深度。同一个 tooltip 内部可能再嵌套调用 tooltip()（比如收纳袋预览）。 */
	private static int scopeDepth;
	private static float scopeAlpha = 1.0F;

	/** 物品数量数字的作用域（和 tooltip 作用域互相独立，最终透明度取两者的乘积）。 */
	private static int countScopeDepth;
	private static float countAlpha = 1.0F;

	/** 重定向守卫：我们自己去调用被拦截的方法时，不要再被拦截一次，否则无限递归。 */
	private static boolean bypassing;

	// ==================== 物品数量数字 ====================
	/** 每个槽位一个动画，key 由槽位的 (x, y) 组成。 */
	private static final Map<Long, ItemCountFade> COUNT_FADES = new HashMap<>();

	/** 「界面正在出现」的剩余时间（秒）。开着的时候新槽位才会跟着做淡入。 */
	private static float appearingFor;

	// ==================== tooltip 缩放 / 位移 ====================
	private static int transformDepth;
	private static boolean tooltipTransformPushed;

	private InterfaceAnimator() {
	}

	private static FadeAnimator createScreenFade() {
		SmoothInterfaceConfig.ScreenFade config = SmoothInterfaceConfig.get().screen;
		FadeAnimator fade = new FadeAnimator(config.fadeInSeconds(), config.fadeOutSeconds());
		// 初始进度为 1 -> 遮罩透明度为 0，保证游戏刚启动时画面正常
		fade.reset(1.0F);
		fade.setVisible(true);
		return fade;
	}

	/**
	 * 把配置里的时长推给各个动画器。启动时和「配置界面点了保存」后各调一次。
	 */
	public static void applyConfig() {
		SmoothInterfaceConfig config = SmoothInterfaceConfig.get();

		TOOLTIP_FADE.setDurations(config.tooltip.fadeInSeconds(), config.tooltip.fadeOutSeconds());
		SCREEN_FADE.setDurations(config.screen.fadeInSeconds(), config.screen.fadeOutSeconds());

		for (ItemCountFade fade : COUNT_FADES.values()) {
			fade.applyConfig();
		}
	}

	// ==================== 每帧驱动 ====================

	/**
	 * 一帧开始。必须在本帧任何渲染发生之前调用。
	 *
	 * @param deltaTracker 客户端计时器，getRealtimeDeltaTicks() 不受暂停影响，适合驱动 UI 动画
	 */
	public static void beginFrame(DeltaTracker deltaTracker) {
		// 1 tick = 50ms，所以 tick * 0.05 = 秒
		float deltaSeconds = Mth.clamp(deltaTracker.getRealtimeDeltaTicks() * 0.05F,
				0.0F, SmoothInterfaceConfig.MAX_FRAME_TIME);

		// 用上一帧的可见性决定这一帧往哪走
		TOOLTIP_FADE.setVisible(SmoothInterfaceConfig.get().tooltip.enabled && tooltipSeenThisFrame);
		TOOLTIP_FADE.advance(deltaSeconds);
		SCREEN_FADE.advance(deltaSeconds);
		advanceItemCountFades(deltaSeconds);

		if (appearingFor > 0.0F) {
			appearingFor -= deltaSeconds;
		}

		tooltipSeenThisFrame = false;
		frame++;
	}

	/** 界面提取结束（Gui.extractRenderState 的 TAIL）。 */
	public static void endOfExtraction() {
		GuiGraphicsExtractor current = graphics;

		if (current != null
				&& !tooltipSeenThisFrame
				&& tooltipSnapshot != null
				&& TOOLTIP_FADE.progress() > 0.0F) {
			renderCachedTooltip(current);
		}

		// 完全淡出后就不再需要缓存了
		if (TOOLTIP_FADE.progress() <= 0.0F) {
			tooltipSnapshot = null;
		}

		graphics = null;
	}

	/** 记录当前帧的渲染上下文，供淡出补画时使用。 */
	public static void setCurrentExtractor(GuiGraphicsExtractor extractor) {
		graphics = extractor;
	}

	/** 界面切换（Gui.setScreen 结束时）。 */
	public static void onScreenChanged(@Nullable Screen screen) {
		// 换界面时旧的 tooltip 已经没意义了，直接丢弃，避免“幽灵提示”
		tooltipSnapshot = null;
		tooltipSeenThisFrame = false;
		TOOLTIP_FADE.setVisible(false);
		TOOLTIP_FADE.reset(0.0F);

		// 槽位坐标换了一整套，旧的动画状态作废
		COUNT_FADES.clear();

		if (screen != null) {
			SmoothInterfaceConfig config = SmoothInterfaceConfig.get();

			// 打开新界面：这一小段时间里所有槽位的数字会按坐标波浪式地依次淡入
			appearingFor = config.itemCount.staggerMaxSeconds()
					+ config.itemCount.fadeInSeconds()
					+ 0.05F;

			if (config.screen.enabled) {
				// 从全遮罩开始淡入
				SCREEN_FADE.reset(0.0F);
				SCREEN_FADE.setVisible(true);
			}
		} else {
			// 回到游戏内 HUD：没有遮罩，快捷栏的数字也不该再波浪式地淡入
			appearingFor = 0.0F;
			SCREEN_FADE.setVisible(false);
			SCREEN_FADE.reset(1.0F);
		}
	}

	// ==================== tooltip ====================

	/** tooltip 真正被绘制时调用（GuiGraphicsExtractor.tooltip 的 HEAD）。 */
	public static void onTooltipRendered(Font font, List<ClientTooltipComponent> lines,
			int x, int y, ClientTooltipPositioner positioner, @Nullable Identifier style) {
		tooltipSeenThisFrame = true;

		// 补画时不要覆盖缓存，否则参数会被自己反复写坏
		if (!renderingCachedTooltip) {
			tooltipSnapshot = new TooltipSnapshot(font, lines, x, y, positioner, style);
		}
	}

	public static boolean isRenderingCachedTooltip() {
		return renderingCachedTooltip;
	}

	/** 把缓存的 tooltip 用当前（正在衰减的）透明度再画一次。 */
	private static void renderCachedTooltip(GuiGraphicsExtractor current) {
		TooltipSnapshot snapshot = tooltipSnapshot;
		if (snapshot == null) {
			return;
		}

		renderingCachedTooltip = true;

		try {
			// 单独一层，保证画在最上面
			current.nextStratum();
			current.tooltip(snapshot.font(), snapshot.lines(), snapshot.x(), snapshot.y(),
					snapshot.positioner(), snapshot.style());
		} finally {
			renderingCachedTooltip = false;
		}
	}

	// ==================== tooltip 的缩放 + 位移 ====================

	/**
	 * 给 tooltip 加一层「从起始缩放放大到 1.0、同时往上浮几像素」的变换。
	 *
	 * <p>做法是往 GUI 的 2D 矩阵栈里压一层变换：26.2 的每个绘制指令在提交时都会
	 * 拷贝一份当前矩阵（{@code new Matrix3x2f(pose)}），所以在这里压栈，
	 * tooltip 里的文字、背景、物品图标就会整体一起平移和缩放。</p>
	 *
	 * <p>必须以 {@link #popTooltipTransform} 配对收尾。</p>
	 *
	 * @param xo,yo 原始鼠标坐标，拿来当缩放的中心（tooltip 会朝鼠标点收缩）
	 * @return 是否真的压了栈；只有返回 true 时调用方才需要 pop
	 */
	public static boolean pushTooltipTransform(GuiGraphicsExtractor extractor, int xo, int yo) {
		// 嵌套调用（比如收纳袋预览）只让最外层做一次变换，避免叠加
		boolean outermost = transformDepth == 0;
		transformDepth++;

		SmoothInterfaceConfig.Tooltip config = SmoothInterfaceConfig.get().tooltip;

		if (!outermost || !config.enabled) {
			return false;
		}

		float eased = TOOLTIP_FADE.easedProgress();
		float scale = Mth.lerp(eased, config.startScale(), 1.0F);
		float offset = Mth.lerp(eased, config.startOffset(), 0.0F);

		if (scale == 1.0F && offset == 0.0F) {
			return false;
		}

		Matrix3x2fStack pose = extractor.pose();
		pose.pushMatrix();
		// 以鼠标点为原点缩放：先移到原点，缩放，再移回去
		pose.translate(xo, yo);
		pose.scale(scale, scale);
		pose.translate(-xo, -yo);
		pose.translate(0.0F, offset);

		tooltipTransformPushed = true;
		return true;
	}

	/** 撤销 {@link #pushTooltipTransform}。 */
	public static void popTooltipTransform(GuiGraphicsExtractor extractor) {
		if (transformDepth > 0) {
			transformDepth--;
		}

		if (tooltipTransformPushed) {
			extractor.pose().popMatrix();
			tooltipTransformPushed = false;
		}
	}

	// ==================== tooltip 透明度作用域 ====================

	/** tooltip 开始绘制：进入作用域，锁定本次要用的透明度。 */
	public static void beginFadeScope() {
		if (scopeDepth++ == 0) {
			scopeAlpha = TOOLTIP_FADE.easedProgress();
		}
	}

	/** tooltip 绘制结束：退出作用域。 */
	public static void endFadeScope() {
		if (scopeDepth > 0) {
			scopeDepth--;
		}

		if (scopeDepth == 0) {
			scopeAlpha = 1.0F;
		}
	}

	// ==================== 物品数量数字 ====================

	/** 某个槽位开始画物品数量数字（GuiGraphicsExtractor.itemCount 的 HEAD）。 */
	public static void beginItemCountScope(int x, int y, int count) {
		if (countScopeDepth++ == 0) {
			countAlpha = SmoothInterfaceConfig.get().itemCount.enabled
					? trackItemCount(x, y, count)
					: 1.0F;
		}
	}

	/** 数量数字画完（GuiGraphicsExtractor.itemCount 的 RETURN）。 */
	public static void endItemCountScope() {
		if (countScopeDepth > 0) {
			countScopeDepth--;
		}

		if (countScopeDepth == 0) {
			countAlpha = 1.0F;
		}
	}

	private static float trackItemCount(int x, int y, int count) {
		long key = ((long) x << 32) ^ (y & 0xFFFFFFFFL);
		ItemCountFade fade = COUNT_FADES.get(key);

		if (fade == null) {
			fade = new ItemCountFade();
			COUNT_FADES.put(key, fade);
		}

		return fade.alphaFor(count, staggerFor(x, y), isAppearing(), frame);
	}

	/** 界面刚打开的一小段时间。 */
	public static boolean isAppearing() {
		return appearingFor > 0.0F;
	}

	/** 距离左上角越远，出场越晚，形成一道斜向的波浪。 */
	private static float staggerFor(int x, int y) {
		int slotSize = SmoothInterfaceConfig.ITEM_COUNT_SLOT_SIZE;
		int cells = Math.max(0, x / slotSize) + Math.max(0, y / slotSize);
		SmoothInterfaceConfig.ItemCount config = SmoothInterfaceConfig.get().itemCount;

		return Math.min(config.staggerMaxSeconds(), cells * config.staggerPerCellSeconds());
	}

	private static void advanceItemCountFades(float deltaSeconds) {
		if (COUNT_FADES.isEmpty()) {
			return;
		}

		Iterator<ItemCountFade> iterator = COUNT_FADES.values().iterator();

		while (iterator.hasNext()) {
			ItemCountFade fade = iterator.next();
			fade.advance(deltaSeconds);

			// 连续几帧都没被画到，说明这个槽位已经不在屏幕上了
			if (frame - fade.lastSeenFrame > 2) {
				iterator.remove();
			}
		}
	}

	// ==================== 通用 ====================

	/** 当前生效的透明度系数（tooltip 与数量数字作用域的乘积）。 */
	public static float currentAlpha() {
		return scopeAlpha * countAlpha;
	}

	/** 当前是否需要对颜色做 alpha 衰减。 */
	public static boolean shouldApplyFade() {
		// alpha == 1 时直接放行，正常游玩时零开销
		return !bypassing && currentAlpha() < 1.0F;
	}

	/** 在守卫内执行 action：期间 {@link #shouldApplyFade()} 恒为 false。 */
	public static void withoutFade(Runnable action) {
		boolean previous = bypassing;
		bypassing = true;

		try {
			action.run();
		} finally {
			bypassing = previous;
		}
	}

	// ==================== 界面遮罩 ====================

	/** 当前界面遮罩的不透明度（0 = 不画）。 */
	public static float screenDim() {
		SmoothInterfaceConfig.ScreenFade config = SmoothInterfaceConfig.get().screen;

		if (!config.enabled) {
			return 0.0F;
		}

		float remaining = 1.0F - SCREEN_FADE.easedProgress();
		return remaining * config.maxDim();
	}
}
