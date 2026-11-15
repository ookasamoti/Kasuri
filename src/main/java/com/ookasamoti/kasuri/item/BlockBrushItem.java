package com.ookasamoti.kasuri.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Painting itself is driven by the client (see BlockBrushClient), which sends the hovered block to the server
 * every tick while a mouse button is held. The item only has to make sure vanilla never breaks blocks with it.
 */
public class BlockBrushItem extends PaletteToolItem {
	public BlockBrushItem(final Item.Properties properties) {
		super(properties);
	}

	@Override
	public boolean canDestroyBlock(final ItemStack itemStack, final BlockState state, final Level level, final BlockPos pos, final LivingEntity user) {
		return false;
	}
}
