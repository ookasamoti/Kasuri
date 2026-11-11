package com.ookasamoti.kasuri.menu;

import com.ookasamoti.kasuri.item.BrushPalette;
import com.ookasamoti.kasuri.item.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Edits a Block Brush's palette. Palette slots are ghost slots: clicking one with a block copies it in without
 * consuming it, clicking with an empty cursor selects it and the drop key clears it. The player's inventory below
 * works as usual, except that the brush itself cannot be moved while the menu is open.
 */
public class BrushPaletteMenu extends AbstractContainerMenu {
	private static final int ROWS = BrushPalette.SIZE / 9;

	private final Container palette;
	/**
	 * The brush being edited; only known on the server, the client just mirrors the slots.
	 */
	private final @Nullable ItemStack brush;

	public BrushPaletteMenu(final int containerId, final Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(BrushPalette.SIZE), null);
	}

	public BrushPaletteMenu(final int containerId, final Inventory inventory, final ItemStack brush) {
		this(containerId, inventory, new PaletteContainer(brush), brush);
	}

	private BrushPaletteMenu(final int containerId, final Inventory inventory, final Container palette, final @Nullable ItemStack brush) {
		super(ModMenus.BRUSH_PALETTE, containerId);
		checkContainerSize(palette, BrushPalette.SIZE);
		this.palette = palette;
		this.brush = brush;

		for (int y = 0; y < ROWS; y++) {
			for (int x = 0; x < 9; x++) {
				this.addSlot(new GhostSlot(palette, x + y * 9, 8 + x * 18, 18 + y * 18));
			}
		}

		this.addStandardInventorySlots(inventory, 8, 18 + ROWS * 18 + 13);
	}

	@Override
	public void clicked(final int slotIndex, final int buttonNum, final ContainerInput containerInput, final Player player) {
		if (slotIndex >= 0 && slotIndex < BrushPalette.SIZE) {
			this.clickPalette(this.slots.get(slotIndex), buttonNum, containerInput, player);
			return;
		}

		if (this.isBrushSlot(slotIndex, player) || containerInput == ContainerInput.SWAP && buttonNum == player.getInventory().getSelectedSlot()) {
			return;
		}

		if (containerInput == ContainerInput.QUICK_MOVE && slotIndex >= 0) {
			this.addToPalette(this.slots.get(slotIndex).getItem());
			return;
		}

		super.clicked(slotIndex, buttonNum, containerInput, player);
	}

	private void clickPalette(final Slot slot, final int buttonNum, final ContainerInput containerInput, final Player player) {
		switch (containerInput) {
			case THROW -> slot.set(ItemStack.EMPTY);
			case PICKUP, QUICK_MOVE -> {
				ItemStack carried = this.getCarried();
				if (BrushPalette.accepts(carried)) {
					slot.set(BrushPalette.entry(carried.getItem()));
				} else if (carried.isEmpty() && slot.hasItem()) {
					this.select(slot.getContainerSlot());
				}
			}
			// Number keys copy the block from that hotbar slot (or the off hand) into the hovered slot
			case SWAP -> {
				ItemStack source = player.getInventory().getItem(buttonNum);
				if (BrushPalette.accepts(source)) {
					slot.set(BrushPalette.entry(source.getItem()));
				}
			}
			default -> {
			}
		}
	}

	private void addToPalette(final ItemStack stack) {
		if (!BrushPalette.accepts(stack) || this.indexOf(stack) >= 0) {
			return;
		}

		int slot = BrushPalette.EMPTY.withItems(this.paletteItems()).appendSlot();
		if (slot >= 0) {
			this.palette.setItem(slot, BrushPalette.entry(stack.getItem()));
		}
	}

	private int indexOf(final ItemStack stack) {
		return BrushPalette.EMPTY.withItems(this.paletteItems()).indexOf(stack.getItem());
	}

	private List<ItemStack> paletteItems() {
		List<ItemStack> items = new ArrayList<>(BrushPalette.SIZE);
		for (int slot = 0; slot < BrushPalette.SIZE; slot++) {
			items.add(this.palette.getItem(slot));
		}

		return items;
	}

	private void select(final int slot) {
		if (this.brush != null) {
			this.brush.set(ModDataComponents.BRUSH_PALETTE, BrushPalette.of(this.brush).withSelected(slot));
		}
	}

	private boolean isBrushSlot(final int slotIndex, final Player player) {
		if (slotIndex < 0 || slotIndex >= this.slots.size()) {
			return false;
		}

		Slot slot = this.slots.get(slotIndex);
		return slot.container == player.getInventory() && slot.getContainerSlot() == player.getInventory().getSelectedSlot();
	}

	@Override
	public boolean canTakeItemForPickAll(final ItemStack carried, final Slot target) {
		return !(target instanceof GhostSlot) && super.canTakeItemForPickAll(carried, target);
	}

	@Override
	public boolean canDragTo(final Slot slot) {
		return !(slot instanceof GhostSlot) && super.canDragTo(slot);
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int slotIndex) {
		// Shift clicks are handled in clicked(), nothing ever moves between the palette and the inventory
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(final Player player) {
		return this.brush == null || player.getMainHandItem() == this.brush;
	}

	private static class GhostSlot extends Slot {
		GhostSlot(final Container container, final int slot, final int x, final int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(final ItemStack itemStack) {
			return false;
		}

		@Override
		public boolean mayPickup(final Player player) {
			return false;
		}
	}

	/**
	 * Server side palette storage that writes every change straight back to the brush.
	 */
	private static class PaletteContainer extends SimpleContainer {
		private final ItemStack brush;

		PaletteContainer(final ItemStack brush) {
			super(BrushPalette.SIZE);
			this.brush = brush;
			BrushPalette.of(brush).contents().copyInto(this.items);
		}

		@Override
		public void setChanged() {
			super.setChanged();
			this.brush.set(ModDataComponents.BRUSH_PALETTE, BrushPalette.of(this.brush).withItems(this.items));
		}
	}
}
