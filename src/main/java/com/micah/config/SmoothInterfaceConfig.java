package com.micah.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.micah.SmoothInterface;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

/**
 * 动画参数配置。
 *
 * 所有可调项都存在 {@code config/smooth-interface.json} 里，游戏内通过
 * ModMenu → Smooth Interface → 设置按钮打开配置界面（界面由 Cloth Config 渲染）。
 *
 * 单位约定：为了让滑块好用，配置里时长统一存「毫秒」（int）、
 * 强度统一存「百分比」（int），只在真正使用时才换算成秒 / 倍数 ——
 * 也就是各个 {@code *Seconds()} / {@code *Scale()} 方法。
 *
 * 字段名直接就是 JSON 里的键名，所以**不要随便改字段名**，改了等于把玩家已有的配置丢掉。
 */
public final class SmoothInterfaceConfig {

	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	private static final String FILE_NAME = "smooth-interface.json";

	/** 当前生效的配置。整个模组都通过 {@link #get()} 来读。 */
	private static SmoothInterfaceConfig instance = new SmoothInterfaceConfig();

	// ==================== 取值范围（配置界面与校验共用一套） ====================

	/** 时长上限（毫秒）。 */
	public static final int MS_MAX = 1000;

	/** 缩放百分比的下限，再小内容就看不清了。 */
	public static final int SCALE_MIN_PERCENT = 50;

	/** 位移上限（像素）。 */
	public static final int OFFSET_MAX_PX = 20;

	/** 波浪延迟「每格」的上限（毫秒）。 */
	public static final int STAGGER_PER_CELL_MAX_MS = 100;

	/** 一个槽位的边长（像素），把屏幕坐标换算成「第几格」时用。 */
	public static final int ITEM_COUNT_SLOT_SIZE = 18;

	/** 单个逻辑帧允许的最大时间步长（秒），防止卡顿后动画跳变。不对外开放。 */
	public static final float MAX_FRAME_TIME = 0.1F;

	// ==================== 配置内容 ====================

	/**
	 * 出厂默认值。
	 *
	 * 字段初始值和配置界面里「重置」按钮用的默认值都取这里，
	 * 保证两边不会各写一份然后对不上。
	 */
	public static final class Defaults {
		private Defaults() {
		}

		public static final boolean TOOLTIP_ENABLED = true;
		public static final int TOOLTIP_FADE_IN_MS = 240;
		public static final int TOOLTIP_FADE_OUT_MS = 200;
		public static final int TOOLTIP_START_SCALE_PERCENT = 90;
		public static final int TOOLTIP_START_OFFSET_PX = 5;

		public static final boolean SCREEN_ENABLED = true;
		public static final int SCREEN_FADE_IN_MS = 380;
		public static final int SCREEN_FADE_OUT_MS = 180;
		public static final int SCREEN_MAX_DIM_PERCENT = 78;

		public static final boolean ITEM_COUNT_ENABLED = true;
		public static final int ITEM_COUNT_FADE_IN_MS = 500;
		public static final int ITEM_COUNT_FADE_OUT_MS = 400;
		public static final int ITEM_COUNT_CHANGE_MIN_ALPHA_PERCENT = 35;
		public static final int ITEM_COUNT_STAGGER_PER_CELL_MS = 12;
		public static final int ITEM_COUNT_STAGGER_MAX_MS = 240;
	}

	public Tooltip tooltip = new Tooltip();

	public ScreenFade screen = new ScreenFade();

	public ItemCount itemCount = new ItemCount();

	private SmoothInterfaceConfig() {
	}

	/** 物品提示（tooltip）。 */
	public static final class Tooltip {
		public boolean enabled = Defaults.TOOLTIP_ENABLED;
		/** 淡入时长（毫秒）。 */
		public int fadeInMs = Defaults.TOOLTIP_FADE_IN_MS;
		/** 淡出时长（毫秒）。 */
		public int fadeOutMs = Defaults.TOOLTIP_FADE_OUT_MS;
		/** 出现瞬间的缩放百分比（100 = 不做缩放）。 */
		public int startScalePercent = Defaults.TOOLTIP_START_SCALE_PERCENT;
		/** 出现瞬间的纵向偏移（像素），会从下方浮到最终位置。 */
		public int startOffsetPx = Defaults.TOOLTIP_START_OFFSET_PX;

		public float fadeInSeconds() {
			return Math.max(0, fadeInMs) / 1000.0F;
		}

		public float fadeOutSeconds() {
			return Math.max(0, fadeOutMs) / 1000.0F;
		}

		public float startScale() {
			return Mth.clamp(startScalePercent / 100.0F, 0.1F, 1.0F);
		}

		public float startOffset() {
			return startOffsetPx;
		}
	}

	/** 打开界面时的白色遮罩淡出。 */
	public static final class ScreenFade {
		public boolean enabled = Defaults.SCREEN_ENABLED;
		/** 遮罩淡出（= 界面淡入）的时长（毫秒）。 */
		public int fadeInMs = Defaults.SCREEN_FADE_IN_MS;
		/** 需要提前收起遮罩时的时长（毫秒）。 */
		public int fadeOutMs = Defaults.SCREEN_FADE_OUT_MS;
		/** 界面刚打开瞬间遮罩的不透明度（百分比）。 */
		public int maxDimPercent = Defaults.SCREEN_MAX_DIM_PERCENT;

		public float fadeInSeconds() {
			return Math.max(0, fadeInMs) / 1000.0F;
		}

		public float fadeOutSeconds() {
			return Math.max(0, fadeOutMs) / 1000.0F;
		}

		public float maxDim() {
			return Mth.clamp(maxDimPercent / 100.0F, 0.0F, 1.0F);
		}
	}

	/** 槽位右下角的物品数量数字。 */
	public static final class ItemCount {
		public boolean enabled = Defaults.ITEM_COUNT_ENABLED;
		/** 淡入时长（毫秒）。 */
		public int fadeInMs = Defaults.ITEM_COUNT_FADE_IN_MS;
		/** 淡出时长（毫秒）。 */
		public int fadeOutMs = Defaults.ITEM_COUNT_FADE_OUT_MS;
		/**
		 * 数字发生变化时从多少亮度开始回弹（百分比）。
		 *
		 * 不从 0 开始是为了避免连续拾取 / 连续转移时数字一直处于全透明状态。
		 */
		public int changeMinAlphaPercent = Defaults.ITEM_COUNT_CHANGE_MIN_ALPHA_PERCENT;
		/** 界面刚打开时的波浪延迟：每往右 / 往下一格多等多少毫秒。 */
		public int staggerPerCellMs = Defaults.ITEM_COUNT_STAGGER_PER_CELL_MS;
		/** 波浪延迟的上限（毫秒），避免大界面里最后一个槽位等太久。 */
		public int staggerMaxMs = Defaults.ITEM_COUNT_STAGGER_MAX_MS;

		public float fadeInSeconds() {
			return Math.max(0, fadeInMs) / 1000.0F;
		}

		public float fadeOutSeconds() {
			return Math.max(0, fadeOutMs) / 1000.0F;
		}

		public float changeMinAlpha() {
			return Mth.clamp(changeMinAlphaPercent / 100.0F, 0.0F, 1.0F);
		}

		public float staggerPerCellSeconds() {
			return Math.max(0, staggerPerCellMs) / 1000.0F;
		}

		public float staggerMaxSeconds() {
			return Math.max(0, staggerMaxMs) / 1000.0F;
		}
	}

	// ==================== 读写 ====================

	public static SmoothInterfaceConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/**
	 * 从磁盘读配置。文件不存在时用默认值并立刻写一份出来，
	 * 方便玩家（和自己）照着文件改。
	 */
	public static void load() {
		Path file = path();

		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				SmoothInterfaceConfig loaded = GSON.fromJson(reader, SmoothInterfaceConfig.class);

				if (loaded != null) {
					loaded.sanitize();
					instance = loaded;
				}
			} catch (Exception exception) {
				SmoothInterface.LOGGER.error("[Smooth Interface] 配置文件读取失败，改用默认值：{}", file, exception);
				instance = new SmoothInterfaceConfig();
			}
		}

		// 顺手写回去：文件不存在时生成模板，存在时把缺的字段补齐
		save();
	}

	public static void save() {
		Path file = path();

		try {
			Files.createDirectories(file.getParent());

			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException exception) {
			SmoothInterface.LOGGER.error("[Smooth Interface] 配置文件写入失败：{}", file, exception);
		}
	}

	/** 把所有字段夹到合法范围，顺便补上缺失的分节（手改 JSON 改坏了也能救回来）。 */
	private void sanitize() {
		if (tooltip == null) {
			tooltip = new Tooltip();
		}

		if (screen == null) {
			screen = new ScreenFade();
		}

		if (itemCount == null) {
			itemCount = new ItemCount();
		}

		tooltip.fadeInMs = Mth.clamp(tooltip.fadeInMs, 0, MS_MAX);
		tooltip.fadeOutMs = Mth.clamp(tooltip.fadeOutMs, 0, MS_MAX);
		tooltip.startScalePercent = Mth.clamp(tooltip.startScalePercent, SCALE_MIN_PERCENT, 100);
		tooltip.startOffsetPx = Mth.clamp(tooltip.startOffsetPx, 0, OFFSET_MAX_PX);

		screen.fadeInMs = Mth.clamp(screen.fadeInMs, 0, MS_MAX);
		screen.fadeOutMs = Mth.clamp(screen.fadeOutMs, 0, MS_MAX);
		screen.maxDimPercent = Mth.clamp(screen.maxDimPercent, 0, 100);

		itemCount.fadeInMs = Mth.clamp(itemCount.fadeInMs, 0, MS_MAX);
		itemCount.fadeOutMs = Mth.clamp(itemCount.fadeOutMs, 0, MS_MAX);
		itemCount.changeMinAlphaPercent = Mth.clamp(itemCount.changeMinAlphaPercent, 0, 100);
		itemCount.staggerPerCellMs = Mth.clamp(itemCount.staggerPerCellMs, 0, STAGGER_PER_CELL_MAX_MS);
		itemCount.staggerMaxMs = Mth.clamp(itemCount.staggerMaxMs, 0, MS_MAX);
	}
}
