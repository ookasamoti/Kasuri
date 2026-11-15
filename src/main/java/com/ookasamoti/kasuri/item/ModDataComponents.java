package com.ookasamoti.kasuri.item;

import com.ookasamoti.kasuri.Kasuri;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ModDataComponents {
	public static final DataComponentType<BrushPalette> BRUSH_PALETTE = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Kasuri.id("brush_palette"),
		DataComponentType.<BrushPalette>builder().persistent(BrushPalette.CODEC).networkSynchronized(BrushPalette.STREAM_CODEC).build()
	);

	/**
	 * Set on Block Box palette entries picked from the world: the block state to place, apart from orientation.
	 */
	public static final DataComponentType<BlockState> PALETTE_STATE = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Kasuri.id("palette_state"),
		DataComponentType.<BlockState>builder().persistent(BlockState.CODEC).networkSynchronized(ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY)).build()
	);

	private ModDataComponents() {
	}

	public static void initialize() {
		// Loads the class so the static fields above get registered
	}
}
