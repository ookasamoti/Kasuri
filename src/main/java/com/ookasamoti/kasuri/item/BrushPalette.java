package com.ookasamoti.kasuri.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * The Block Brush's internal inventory: the blocks it can paint with and which one is selected. Slots only hold
 * a reference to a block item; nothing is ever taken from or given back to the player.
 */
public record BrushPalette(ItemContainerContents contents, int selected) {
	public static final int SIZE = 27;
	public static final BrushPalette EMPTY = new BrushPalette(ItemContainerContents.EMPTY, 0);
	public static final Codec<BrushPalette> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		ItemContainerContents.CODEC.optionalFieldOf("contents", ItemContainerContents.EMPTY).forGetter(BrushPalette::contents),
		Codec.intRange(0, SIZE - 1).optionalFieldOf("selected", 0).forGetter(BrushPalette::selected)
	).apply(instance, BrushPalette::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, BrushPalette> STREAM_CODEC = StreamCodec.composite(
		ItemContainerContents.STREAM_CODEC, BrushPalette::contents,
		ByteBufCodecs.VAR_INT, BrushPalette::selected,
		BrushPalette::new
	);

	public static BrushPalette of(final ItemStack brush) {
		return brush.getOrDefault(ModDataComponents.BRUSH_PALETTE, EMPTY);
	}

	/**
	 * Whether {@code stack} can be put in the palette.
	 */
	public static boolean accepts(final ItemStack stack) {
		return stack.getItem() instanceof BlockItem && !(stack.getItem() instanceof BlockBrushItem);
	}

	/**
	 * Strips the count and components, the palette only cares about which block it is.
	 */
	public static ItemStack entry(final Item item) {
		return new ItemStack(item);
	}

	public NonNullList<ItemStack> items() {
		NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		this.contents.copyInto(items);
		return items;
	}

	public ItemStack selectedItem() {
		return this.items().get(this.selected);
	}

	public @Nullable Block selectedBlock() {
		return this.selectedItem().getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
	}

	public BrushPalette withItems(final List<ItemStack> items) {
		return new BrushPalette(ItemContainerContents.fromItems(items), this.selected);
	}

	public BrushPalette withSelected(final int selected) {
		return new BrushPalette(this.contents, selected);
	}

	/**
	 * Moves the selection {@code direction} slots at a time, skipping empty slots and wrapping around.
	 */
	public BrushPalette cycle(final int direction) {
		NonNullList<ItemStack> items = this.items();
		for (int step = 1; step <= SIZE; step++) {
			int slot = Math.floorMod(this.selected + step * direction, SIZE);
			if (!items.get(slot).isEmpty()) {
				return this.withSelected(slot);
			}
		}

		return this;
	}

	/**
	 * Returns the slot holding {@code item}, or -1.
	 */
	public int indexOf(final Item item) {
		NonNullList<ItemStack> items = this.items();
		for (int slot = 0; slot < SIZE; slot++) {
			if (items.get(slot).is(item)) {
				return slot;
			}
		}

		return -1;
	}

	/**
	 * Returns the slot a new entry is appended to: the one after the last filled slot, or the first gap left by a
	 * removed entry when that is the last slot. Returns -1 when the palette is full.
	 */
	public int appendSlot() {
		NonNullList<ItemStack> items = this.items();
		int last = SIZE - 1;
		while (last >= 0 && items.get(last).isEmpty()) {
			last--;
		}

		if (last < SIZE - 1) {
			return last + 1;
		}

		for (int slot = 0; slot < SIZE; slot++) {
			if (items.get(slot).isEmpty()) {
				return slot;
			}
		}

		return -1;
	}
}
