package com.micah;

import com.micah.animation.InterfaceAnimator;
import com.micah.config.SmoothInterfaceConfig;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmoothInterface implements ModInitializer {
	public static final String MOD_ID = "smooth-interface";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 先读配置再碰动画系统：InterfaceAnimator 的静态字段是在类初始化时建的，
		// 顺序反了的话动画器会先拿着默认值初始化一遍
		SmoothInterfaceConfig.load();
		InterfaceAnimator.applyConfig();

		LOGGER.info("Smooth Interface loaded: tooltip fade + scale, screen fade in, item count fade. Config: {}",
				FabricLoader.getInstance().getConfigDir().resolve("smooth-interface.json"));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
