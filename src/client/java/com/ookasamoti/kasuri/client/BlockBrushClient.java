package com.ookasamoti.kasuri.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ookasamoti.kasuri.Kasuri;
import com.ookasamoti.kasuri.item.BlockBoxItem;
import com.ookasamoti.kasuri.item.BlockBrushItem;
import com.ookasamoti.kasuri.item.PaletteToolItem;
import com.ookasamoti.kasuri.network.BoxPlacePayload;
import com.ookasamoti.kasuri.network.BrushPaintPayload;
import com.ookasamoti.kasuri.network.BrushPalettePayloads;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Turns held mouse buttons into Block Brush and Block Box requests. Vanilla calls into here every tick while use or attack is
 * held (see MinecraftMixin), so dragging the view paints every block the crosshair passes over.
 */
public final class BlockBrushClient {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Kasuri.id("block_brush"));
	/**
	 * Held together with right click to open the palette, or with the mouse wheel to cycle it.
	 */
	public static final KeyMapping PALETTE_KEY = new KeyMapping(
		"key.kasuri.block_brush_palette", InputConstants.KEY_LCONTROL, CATEGORY
	);

	private enum Action {
		PAINT,
		RESTORE,
		PLACE,
		OPEN_PALETTE
	}

	private static long tick;
	private static long lastActionTick = Long.MIN_VALUE;
	private static @Nullable Action lastAction;
	private static @Nullable BlockPos lastPos;
	/**
	 * Where the Block Box is expected to have placed during the current stroke, so that it builds a layer across
	 * the dragged over blocks instead of a pillar out of the blocks it just placed.
	 */
	private static final Set<BlockPos> PLACED = new HashSet<>();

	private BlockBrushClient() {
	}

	public static boolean isHoldingBrush(final @Nullable LocalPlayer player) {
		return player != null && player.getMainHandItem().getItem() instanceof BlockBrushItem;
	}

	/**
	 * Whether the player holds the Block Brush or the Block Box.
	 */
	public static boolean isHoldingTool(final @Nullable LocalPlayer player) {
		return player != null && player.getMainHandItem().getItem() instanceof PaletteToolItem;
	}

	public static void onEndTick(final Minecraft minecraft) {
		tick++;
	}

	public static void use(final Minecraft minecraft) {
		if (!PALETTE_KEY.isDown()) {
			if (minecraft.player.getMainHandItem().getItem() instanceof BlockBoxItem) {
				place(minecraft);
			} else {
				paint(minecraft, Action.PAINT);
			}
		} else if (!continueAction(Action.OPEN_PALETTE)) {
			// Only once per press, vanilla keeps calling this while the button is held
			ClientPlayNetworking.send(BrushPalettePayloads.Open.INSTANCE);
		}
	}

	public static void attack(final Minecraft minecraft) {
		paint(minecraft, Action.RESTORE);
	}

	public static void pick(final Minecraft minecraft) {
		if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			ClientPlayNetworking.send(new BrushPalettePayloads.Pick(hit.getBlockPos()));
		}
	}

	/**
	 * Hotkey + wheel cycles the palette instead of the hotbar. Returns whether vanilla may still scroll the hotbar.
	 */
	public static boolean onHotbarScroll(final int oldSlot, final int newSlot) {
		if (!isHoldingTool(Minecraft.getInstance().player) || !PALETTE_KEY.isDown()) {
			return true;
		}

		// Follow the hotbar's direction, minding its wrap around
		int delta = newSlot - oldSlot;
		if (delta > 4) {
			delta -= 9;
		} else if (delta < -4) {
			delta += 9;
		}

		ClientPlayNetworking.send(new BrushPalettePayloads.Cycle(Integer.signum(delta)));
		return false;
	}

	private static void paint(final Minecraft minecraft, final Action action) {
		if (!continueAction(action)) {
			lastPos = null;
		}

		if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockPos pos = hit.getBlockPos();
		if (pos.equals(lastPos)) {
			return;
		}

		lastPos = pos.immutable();
		ClientPlayNetworking.send(new BrushPaintPayload(lastPos, action == Action.RESTORE));
		LocalPlayer player = minecraft.player;
		player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), false);
	}

	private static void place(final Minecraft minecraft) {
		if (!continueAction(Action.PLACE)) {
			lastPos = null;
			PLACED.clear();
		}

		if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BlockPos pos = hit.getBlockPos();
		if (pos.equals(lastPos) || PLACED.contains(pos)) {
			return;
		}

		lastPos = pos.immutable();
		PLACED.add(lastPos.relative(hit.getDirection()));
		ClientPlayNetworking.send(new BoxPlacePayload(hit));
		LocalPlayer player = minecraft.player;
		player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), false);
	}

	/**
	 * Records {@code action} for this tick and returns whether it continues the one from the previous tick, i.e.
	 * the same button is still being held.
	 */
	private static boolean continueAction(final Action action) {
		boolean continuing = lastActionTick >= tick - 1 && lastAction == action;
		lastActionTick = tick;
		lastAction = action;
		return continuing;
	}
}
