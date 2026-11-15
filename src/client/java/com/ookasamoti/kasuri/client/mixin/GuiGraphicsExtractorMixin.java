package com.ookasamoti.kasuri.client.mixin;

import com.ookasamoti.kasuri.item.BrushPalette;
import com.ookasamoti.kasuri.item.PaletteToolItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the selected palette block of the Block Brush or Block Box at half size in the bottom left corner of the brush's slot.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
	@Shadow
	public abstract Matrix3x2fStack pose();

	@Shadow
	public abstract void fakeItem(ItemStack itemStack, int x, int y);

	@Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
	private void kasuri$drawSelectedBlock(Font font, ItemStack itemStack, int x, int y, @Nullable String countText, CallbackInfo ci) {
		if (!(itemStack.getItem() instanceof PaletteToolItem)) {
			return;
		}

		ItemStack selected = BrushPalette.of(itemStack).selectedItem();
		if (selected.isEmpty()) {
			return;
		}

		Matrix3x2fStack pose = this.pose();
		pose.pushMatrix();
		pose.translate(x, y + 8);
		pose.scale(0.5F, 0.5F);
		this.fakeItem(selected, 0, 0);
		pose.popMatrix();
	}
}
