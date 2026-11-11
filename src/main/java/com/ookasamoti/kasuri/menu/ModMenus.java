package com.ookasamoti.kasuri.menu;

import com.ookasamoti.kasuri.Kasuri;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
	public static final MenuType<BrushPaletteMenu> BRUSH_PALETTE = Registry.register(
		BuiltInRegistries.MENU, Kasuri.id("brush_palette"), new MenuType<>(BrushPaletteMenu::new, FeatureFlags.VANILLA_SET)
	);

	private ModMenus() {
	}

	public static void initialize() {
		// Loads the class so the static fields above get registered
	}
}
