package com.ookasamoti.kasuri.client;

import com.ookasamoti.kasuri.item.BlockBrushItem;
import com.ookasamoti.kasuri.network.BrushPaintPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Turns held mouse buttons into paint requests. Vanilla calls into here every tick while use or attack is held
 * (see MinecraftMixin), so dragging the view paints every block the crosshair passes over.
 */
public final class BlockBrushClient {
	private static long tick;
	private static long lastPaintTick = Long.MIN_VALUE;
	private static boolean lastRestore;
	private static @Nullable BlockPos lastPos;

	private BlockBrushClient() {
	}

	public static boolean isHoldingBrush(final @Nullable LocalPlayer player) {
		return player != null && player.getMainHandItem().getItem() instanceof BlockBrushItem;
	}

	public static void onEndTick(final Minecraft minecraft) {
		tick++;
	}

	public static void paint(final Minecraft minecraft, final boolean restore) {
		// A stroke continues while the same button keeps being reported every tick
		boolean continuing = lastPaintTick >= tick - 1 && lastRestore == restore;
		lastPaintTick = tick;
		lastRestore = restore;
		if (!continuing) {
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
		ClientPlayNetworking.send(new BrushPaintPayload(lastPos, restore));
		LocalPlayer player = minecraft.player;
		player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), false);
	}
}
