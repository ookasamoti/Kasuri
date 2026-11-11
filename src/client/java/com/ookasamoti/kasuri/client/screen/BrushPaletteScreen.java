package com.ookasamoti.kasuri.client.screen;

import com.ookasamoti.kasuri.item.BrushPalette;
import com.ookasamoti.kasuri.menu.BrushPaletteMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * A three row chest screen that also outlines the palette's selected slot.
 */
public class BrushPaletteScreen extends AbstractContainerScreen<BrushPaletteMenu> {
	private static final Identifier CONTAINER_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final int ROWS = BrushPalette.SIZE / 9;
	private static final int SELECTION_COLOR = 0xFFFFD84A;

	public BrushPaletteScreen(final BrushPaletteMenu menu, final Inventory inventory, final Component title) {
		super(menu, inventory, title, 176, 114 + ROWS * 18);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int xo = (this.width - this.imageWidth) / 2;
		int yo = (this.height - this.imageHeight) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, xo, yo, 0.0F, 0.0F, this.imageWidth, ROWS * 18 + 17, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, xo, yo + ROWS * 18 + 17, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);

		// The selection lives on the brush, which the server keeps in sync through the player's inventory
		Slot selected = this.menu.slots.get(BrushPalette.of(this.minecraft.player.getMainHandItem()).selected());
		int x = xo + selected.x - 1;
		int y = yo + selected.y - 1;
		graphics.fill(x, y, x + 18, y + 1, SELECTION_COLOR);
		graphics.fill(x, y + 17, x + 18, y + 18, SELECTION_COLOR);
		graphics.fill(x, y + 1, x + 1, y + 17, SELECTION_COLOR);
		graphics.fill(x + 17, y + 1, x + 18, y + 17, SELECTION_COLOR);
	}
}
