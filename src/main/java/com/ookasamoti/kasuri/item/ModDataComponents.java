package com.ookasamoti.kasuri.item;

import com.ookasamoti.kasuri.Kasuri;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModDataComponents {
	public static final DataComponentType<BrushPalette> BRUSH_PALETTE = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Kasuri.id("brush_palette"),
		DataComponentType.<BrushPalette>builder().persistent(BrushPalette.CODEC).networkSynchronized(BrushPalette.STREAM_CODEC).build()
	);

	private ModDataComponents() {
	}

	public static void initialize() {
		// Loads the class so the static fields above get registered
	}
}
