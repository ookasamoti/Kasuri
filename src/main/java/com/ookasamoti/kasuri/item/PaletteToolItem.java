package com.ookasamoti.kasuri.item;

import net.minecraft.world.item.Item;

/**
 * An operator building tool that works with blocks from a {@link BrushPalette}: the Block Brush and the Block Box.
 * They share the palette menu, hotkey + wheel cycling, pick block and the selected block overlay.
 */
public abstract class PaletteToolItem extends Item {
	protected PaletteToolItem(final Item.Properties properties) {
		super(properties);
	}
}
