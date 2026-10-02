package com.micah.animation;

import net.minecraft.util.Mth;

/**
 * 与帧率无关的 0 → 1 补间器。
 *
 * <p>它只维护“进度”，具体表现（透明度、缩放、位移……）由调用方决定。
 * 这样同一个动画器既能驱动 tooltip 的不透明度，也能驱动界面的遮罩。</p>
 *
 * <p>用法：每帧调用 {@link #setVisible(boolean)} 声明目标状态（true = 想显示），
 * 再调用 {@link #advance(float)} 传入本帧经过的秒数。因为用的是“时间 × 速度”，
 * 所以 60 帧和 240 帧的玩家看到的动画时长完全一致。</p>
 */
public final class FadeAnimator {

	/** 淡入所需的秒数（0 到 1） */
	private float fadeInTime;
	/** 淡出所需的秒数（1 到 0） */
	private float fadeOutTime;

	private float progress;
	private boolean visible;

	public FadeAnimator(float fadeInTime, float fadeOutTime) {
		setDurations(fadeInTime, fadeOutTime);
	}

	/**
	 * 更新时长。配置界面里改了参数后由 {@link InterfaceAnimator#applyConfig()} 调用，
	 * 所以这里不能是 final 的。
	 */
	public void setDurations(float fadeInTime, float fadeOutTime) {
		// 避免除以 0
		this.fadeInTime = Math.max(fadeInTime, 0.001F);
		this.fadeOutTime = Math.max(fadeOutTime, 0.001F);
	}

	/** 设置目标状态。true = 渐显到 1，false = 渐隐到 0。 */
	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public boolean isVisible() {
		return this.visible;
	}

	/**
	 * 推进动画。
	 *
	 * @param deltaSeconds 本帧经过的秒数
	 */
	public void advance(float deltaSeconds) {
		float target = this.visible ? 1.0F : 0.0F;
		if (this.progress == target) {
			return;
		}

		// 用“时长”换算成每秒的进度增量，实现帧率无关
		float speed = 1.0F / (this.visible ? this.fadeInTime : this.fadeOutTime);
		float step = speed * deltaSeconds;

		this.progress = this.progress < target
				? Math.min(target, this.progress + step)
				: Math.max(target, this.progress - step);
	}

	/** 线性进度，范围 [0, 1]。 */
	public float progress() {
		return this.progress;
	}

	/** 带缓动的进度，通常直接拿来当透明度用。 */
	public float easedProgress() {
		return Easing.outCubic(this.progress);
	}

	/** 强制把进度拨到某个位置（用来做“瞬间出现/瞬间消失”）。 */
	public void reset(float progress) {
		this.progress = Mth.clamp(progress, 0.0F, 1.0F);
	}

	/** 是否已经停在终点，不再需要每帧推进。 */
	public boolean isIdle() {
		return this.progress == (this.visible ? 1.0F : 0.0F);
	}
}
