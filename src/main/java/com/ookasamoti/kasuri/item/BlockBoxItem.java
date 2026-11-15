package com.ookasamoti.kasuri.item;

import net.minecraft.world.item.Item;

/**
 * Places the selected palette block like the block item itself would, without using one up. Placing is driven by
 * the client (see BlockBrushClient) so that dragging the view places every block the crosshair passes over.
 */
public class BlockBoxItem extends PaletteToolItem {
	public BlockBoxItem(final Item.Properties properties) {
		super(properties);
	}
}
