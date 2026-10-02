package com.micah.compat;

import com.micah.config.SmoothInterfaceConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import net.minecraft.client.gui.screens.Screen;

/**
 * ModMenu 的接入点：在模组列表里给 Smooth Interface 挂一个「设置」按钮。
 *
 * 这个类只有在玩到 ModMenu 时才会被加载（{@code fabric.mod.json} 里的
 * {@code modmenu} entrypoint），所以没装 ModMenu 也不会报错，只是看不到那个按钮。
 * 配置文件本身照样在 {@code config/smooth-interface.json}。</p>
 */
public class ModMenuIntegration implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		// 每次点开设置都重新构造一次，这样读到的一定是当前生效的值
		return new ConfigScreenFactory<Screen>() {
			@Override
			public Screen create(Screen parent) {
				return SmoothInterfaceConfigScreen.create(parent);
			}
		};
	}
}
