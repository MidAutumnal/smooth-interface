package com.micah.animation;

/** 缓动函数。 */
public final class Easing {

	private Easing() {
	}

	/**
	 * 三次缓出：开头快、结尾慢。
	 *
	 * <p>做淡入淡出时，线性插值会显得“一顿一顿”，缓出曲线会自然很多。
	 *
	 * @param t 线性进度，范围 [0, 1]
	 * @return 缓动后的进度，范围 [0, 1]
	 */
	public static float outCubic(float t) {
		float f = 1.0F - t;
		return 1.0F - f * f * f;
	}
}
