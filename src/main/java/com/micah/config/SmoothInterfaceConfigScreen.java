package com.micah.config;

import java.util.function.Consumer;

import com.micah.animation.InterfaceAnimator;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import me.shedaniel.clothconfig2.gui.entries.TextListEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 游戏内配置界面。
 *
 * <p>界面本身交给 Cloth Config 渲染，我们只负责「有哪些选项、取值范围多少、改完存哪」。
 * 三个分类对应三大块功能，每个条目都带一句中文说明。</p>
 *
 * <p>入口是 ModMenu 的模组列表 → Smooth Interface → 设置按钮，
 * 见 {@link ModMenuIntegration}。</p>
 *
 * <p><b>关于单位</b>：时长一律用「毫秒」的整数滑块而不是浮点数输入框 ——
 * 拖一下就能调、能直接看出快慢，也不会因为手打数字写出离谱的值。</p>
 */
public final class SmoothInterfaceConfigScreen {

	/** 数值单位，决定滑块右边显示什么后缀。 */
	private enum Unit {
		MILLISECONDS("smooth-interface.config.value.ms"),
		PERCENT("smooth-interface.config.value.percent"),
		PIXELS("smooth-interface.config.value.px");

		private final String key;

		Unit(String key) {
			this.key = key;
		}

		Component format(int value) {
			return Component.translatable(this.key, value);
		}
	}

	private SmoothInterfaceConfigScreen() {
	}

	/** 构造配置界面。ModMenu 每次打开都会调用一次，所以读到的都是最新值。 */
	public static Screen create(Screen parent) {
		SmoothInterfaceConfig config = SmoothInterfaceConfig.get();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("smooth-interface.config.title"))
				.setSavingRunnable(SmoothInterfaceConfigScreen::saveAndApply);

		ConfigEntryBuilder entries = builder.entryBuilder();

		buildTooltipCategory(builder, entries, config.tooltip);
		buildScreenCategory(builder, entries, config.screen);
		buildItemCountCategory(builder, entries, config.itemCount);

		return builder.build();
	}

	/** 点「保存」时：写文件 + 立刻把新参数推给动画系统，不用重启游戏。 */
	private static void saveAndApply() {
		SmoothInterfaceConfig.save();
		InterfaceAnimator.applyConfig();
	}

	// ==================== 三个分类 ====================

	private static void buildTooltipCategory(ConfigBuilder builder, ConfigEntryBuilder entries,
			SmoothInterfaceConfig.Tooltip tooltip) {
		ConfigCategory category = builder.getOrCreateCategory(
				Component.translatable("smooth-interface.config.category.tooltip"));

		category.addEntry(description(entries, "smooth-interface.config.category.tooltip.desc"));

		category.addEntry(toggle(entries, "smooth-interface.config.tooltip.enabled",
				tooltip.enabled, SmoothInterfaceConfig.Defaults.TOOLTIP_ENABLED,
				value -> tooltip.enabled = value));

		category.addEntry(slider(entries, "smooth-interface.config.tooltip.fadeIn",
				tooltip.fadeInMs, SmoothInterfaceConfig.Defaults.TOOLTIP_FADE_IN_MS,
				0, SmoothInterfaceConfig.MS_MAX, Unit.MILLISECONDS,
				value -> tooltip.fadeInMs = value));

		category.addEntry(slider(entries, "smooth-interface.config.tooltip.fadeOut",
				tooltip.fadeOutMs, SmoothInterfaceConfig.Defaults.TOOLTIP_FADE_OUT_MS,
				0, SmoothInterfaceConfig.MS_MAX, Unit.MILLISECONDS,
				value -> tooltip.fadeOutMs = value));

		category.addEntry(slider(entries, "smooth-interface.config.tooltip.startScale",
				tooltip.startScalePercent, SmoothInterfaceConfig.Defaults.TOOLTIP_START_SCALE_PERCENT,
				SmoothInterfaceConfig.SCALE_MIN_PERCENT, 100, Unit.PERCENT,
				value -> tooltip.startScalePercent = value));

		category.addEntry(slider(entries, "smooth-interface.config.tooltip.startOffset",
				tooltip.startOffsetPx, SmoothInterfaceConfig.Defaults.TOOLTIP_START_OFFSET_PX,
				0, SmoothInterfaceConfig.OFFSET_MAX_PX, Unit.PIXELS,
				value -> tooltip.startOffsetPx = value));
	}

	private static void buildScreenCategory(ConfigBuilder builder, ConfigEntryBuilder entries,
			SmoothInterfaceConfig.ScreenFade screen) {
		ConfigCategory category = builder.getOrCreateCategory(
				Component.translatable("smooth-interface.config.category.screen"));

		category.addEntry(description(entries, "smooth-interface.config.category.screen.desc"));

		category.addEntry(toggle(entries, "smooth-interface.config.screen.enabled",
				screen.enabled, SmoothInterfaceConfig.Defaults.SCREEN_ENABLED,
				value -> screen.enabled = value));

		category.addEntry(slider(entries, "smooth-interface.config.screen.fadeIn",
				screen.fadeInMs, SmoothInterfaceConfig.Defaults.SCREEN_FADE_IN_MS,
				0, SmoothInterfaceConfig.MS_MAX, Unit.MILLISECONDS,
				value -> screen.fadeInMs = value));

		category.addEntry(slider(entries, "smooth-interface.config.screen.fadeOut",
				screen.fadeOutMs, SmoothInterfaceConfig.Defaults.SCREEN_FADE_OUT_MS,
				0, SmoothInterfaceConfig.MS_MAX, Unit.MILLISECONDS,
				value -> screen.fadeOutMs = value));

		category.addEntry(slider(entries, "smooth-interface.config.screen.maxDim",
				screen.maxDimPercent, SmoothInterfaceConfig.Defaults.SCREEN_MAX_DIM_PERCENT,
				0, 100, Unit.PERCENT,
				value -> screen.maxDimPercent = value));
	}

	private static void buildItemCountCategory(ConfigBuilder builder, ConfigEntryBuilder entries,
			SmoothInterfaceConfig.ItemCount itemCount) {
		ConfigCategory category = builder.getOrCreateCategory(
				Component.translatable("smooth-interface.config.category.itemCount"));

		category.addEntry(description(entries, "smooth-interface.config.category.itemCount.desc"));

		category.addEntry(toggle(entries, "smooth-interface.config.itemCount.enabled",
				itemCount.enabled, SmoothInterfaceConfig.Defaults.ITEM_COUNT_ENABLED,
				value -> itemCount.enabled = value));

		category.addEntry(slider(entries, "smooth-interface.config.itemCount.fadeIn",
				itemCount.fadeInMs, SmoothInterfaceConfig.Defaults.ITEM_COUNT_FADE_IN_MS,
				0, SmoothInterfaceConfig.MS_MAX, Unit.MILLISECONDS,
				value -> itemCount.fadeInMs = value));

		category.addEntry(slider(entries, "smooth-interface.config.itemCount.fadeOut",
				itemCount.fadeOutMs, SmoothInterfaceConfig.Defaults.ITEM_COUNT_FADE_OUT_MS,
				0, SmoothInterfaceConfig.MS_MAX, Unit.MILLISECONDS,
				value -> itemCount.fadeOutMs = value));

		category.addEntry(slider(entries, "smooth-interface.config.itemCount.changeMinAlpha",
				itemCount.changeMinAlphaPercent,
				SmoothInterfaceConfig.Defaults.ITEM_COUNT_CHANGE_MIN_ALPHA_PERCENT,
				0, 100, Unit.PERCENT,
				value -> itemCount.changeMinAlphaPercent = value));

		category.addEntry(slider(entries, "smooth-interface.config.itemCount.staggerPerCell",
				itemCount.staggerPerCellMs, SmoothInterfaceConfig.Defaults.ITEM_COUNT_STAGGER_PER_CELL_MS,
				0, SmoothInterfaceConfig.STAGGER_PER_CELL_MAX_MS, Unit.MILLISECONDS,
				value -> itemCount.staggerPerCellMs = value));

		category.addEntry(slider(entries, "smooth-interface.config.itemCount.staggerMax",
				itemCount.staggerMaxMs, SmoothInterfaceConfig.Defaults.ITEM_COUNT_STAGGER_MAX_MS,
				0, SmoothInterfaceConfig.MS_MAX, Unit.MILLISECONDS,
				value -> itemCount.staggerMaxMs = value));
	}

	// ==================== 条目构造 ====================

	private static BooleanListEntry toggle(ConfigEntryBuilder entries, String key, boolean value,
			boolean defaultValue, Consumer<Boolean> save) {
		return entries.startBooleanToggle(label(key), value)
				.setDefaultValue(defaultValue)
				.setTooltip(tooltip(key))
				.setSaveConsumer(save)
				.build();
	}

	private static IntegerSliderEntry slider(ConfigEntryBuilder entries, String key, int value,
			int defaultValue, int min, int max, Unit unit, Consumer<Integer> save) {
		return entries.startIntSlider(label(key), value, min, max)
				.setDefaultValue(defaultValue)
				.setTextGetter(unit::format)
				.setTooltip(tooltip(key))
				.setSaveConsumer(save)
				.build();
	}

	/** 分类开头的一段灰字说明。 */
	private static TextListEntry description(ConfigEntryBuilder entries, String key) {
		return entries.startTextDescription(Component.translatable(key)).build();
	}

	private static Component label(String key) {
		return Component.translatable(key);
	}

	/** 每个条目的说明文字统一放在 {@code <键>.tooltip}。 */
	private static Component tooltip(String key) {
		return Component.translatable(key + ".tooltip");
	}
}
