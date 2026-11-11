package com.ookasamoti.kasuri.client.mixin;

import com.ookasamoti.kasuri.client.BlockBrushClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces vanilla use/attack/pick handling while the Block Brush is in the main hand, so that right click does not
 * place blocks or open containers, left click does not break blocks and pick block edits the palette instead.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow
	public @Nullable LocalPlayer player;

	// Called on click and, since the cancel skips setting rightClickDelay, every tick while use is held
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void kasuri$useBrush(CallbackInfo ci) {
		if (BlockBrushClient.isHoldingBrush(this.player)) {
			BlockBrushClient.use((Minecraft) (Object) this);
			ci.cancel();
		}
	}

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void kasuri$restoreOnClick(CallbackInfoReturnable<Boolean> cir) {
		if (BlockBrushClient.isHoldingBrush(this.player)) {
			BlockBrushClient.attack((Minecraft) (Object) this);
			cir.setReturnValue(false);
		}
	}

	// Called every tick; down is true while attack is held with no screen open
	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void kasuri$restoreWhileHeld(boolean down, CallbackInfo ci) {
		if (BlockBrushClient.isHoldingBrush(this.player)) {
			if (down) {
				BlockBrushClient.attack((Minecraft) (Object) this);
			}

			ci.cancel();
		}
	}

	@Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
	private void kasuri$pickIntoPalette(CallbackInfo ci) {
		if (BlockBrushClient.isHoldingBrush(this.player)) {
			BlockBrushClient.pick((Minecraft) (Object) this);
			ci.cancel();
		}
	}
}
