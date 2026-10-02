package com.micah.animation;

import com.micah.config.SmoothInterfaceConfig;

/**
 * 某一个槽位上「物品数量数字」的淡入淡出状态。
 *
 * <p>这里按「槽位在屏幕上的坐标」来区分不同槽位，每个槽位记住自己上次画出来的数量：</p>
 * <ul>
 *     <li>界面刚打开 —— 所有槽位都是新的，按坐标做波浪式延迟后依次淡入（0 → 1）；</li>
 *     <li>数量变了（比如 shift 转移物品）—— 从半亮弹回全亮，读起来就是「数字刷新了一下」；</li>
 *     <li>其它情况下新出现的槽位 —— 直接就是可见的，不做动画。</li>
 * </ul>
 *
 * <p>最后一条很关键：鼠标拖着的那一摞物品，它的坐标每帧都在变，
 * 如果「新槽位」也从头淡入，那个数字会永远是透明的。所以只有界面刚打开的那一小段时间
 * 才让新槽位参与淡入（见 {@link InterfaceAnimator#isAppearing()}）。</p>
 */
final class ItemCountFade {

	/** 还没记录过任何数量。 */
	private static final int UNSET = Integer.MIN_VALUE;

	private final FadeAnimator animator = new FadeAnimator(0.001F, 0.001F);

	/** 上一次画出来的数量，用来判断数字有没有变。 */
	private int count = UNSET;

	/** 还剩下的启动延迟（秒），做波浪效果用。 */
	private float delay;

	/** 最后一次被绘制的帧号，用来回收已经不在屏幕上的槽位。 */
	int lastSeenFrame;

	ItemCountFade() {
		applyConfig();
	}

	/** 把配置里的时长推到这个动画器上（配置变更时调用）。 */
	void applyConfig() {
		SmoothInterfaceConfig.ItemCount config = SmoothInterfaceConfig.get().itemCount;
		this.animator.setDurations(config.fadeInSeconds(), config.fadeOutSeconds());
	}

	/**
	 * 返回本次绘制应该使用的透明度，并在需要时重播淡入。
	 *
	 * @param count      当前要显示的数量
	 * @param stagger    这个槽位的波浪延迟（秒）
	 * @param animateNew 新出现的槽位是否也要淡入（只在界面刚打开时为 true）
	 * @param frame      当前帧号
	 */
	float alphaFor(int count, float stagger, boolean animateNew, int frame) {
		if (this.count == UNSET) {
			this.count = count;
			this.delay = animateNew ? stagger : 0.0F;
			// 不参与动画的槽位直接从「已经显示完」的状态开始
			this.animator.reset(animateNew ? 0.0F : 1.0F);
		} else if (count != this.count) {
			// 数字变了：从半亮弹回全亮，连续变化时也不会一直看不见
			this.count = count;
			this.delay = 0.0F;
			this.animator.reset(SmoothInterfaceConfig.get().itemCount.changeMinAlpha());
		}

		this.lastSeenFrame = frame;
		this.animator.setVisible(true);
		return this.animator.easedProgress();
	}

	/** 推进动画。延迟还没走完就一直保持全透明。 */
	void advance(float deltaSeconds) {
		float step = deltaSeconds;

		if (this.delay > 0.0F) {
			this.delay -= step;
			// 延迟用不完，这一帧不推进；用完了就把剩下的时间继续用上
			step = -this.delay;

			if (step <= 0.0F) {
				return;
			}
		}

		this.animator.advance(step);
	}
}
