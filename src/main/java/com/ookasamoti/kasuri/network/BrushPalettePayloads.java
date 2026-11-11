package com.ookasamoti.kasuri.network;

import com.ookasamoti.kasuri.Kasuri;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client requests that edit the Block Brush's palette.
 */
public final class BrushPalettePayloads {
	private BrushPalettePayloads() {
	}

	/**
	 * Hotkey + right click: opens the palette.
	 */
	public record Open() implements CustomPacketPayload {
		public static final Open INSTANCE = new Open();
		public static final CustomPacketPayload.Type<Open> TYPE = new CustomPacketPayload.Type<>(Kasuri.id("brush_palette_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public CustomPacketPayload.Type<Open> type() {
			return TYPE;
		}
	}

	/**
	 * Hotkey + mouse wheel: moves the selection to the next filled slot in {@code direction} (+1 or -1).
	 */
	public record Cycle(int direction) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Cycle> TYPE = new CustomPacketPayload.Type<>(Kasuri.id("brush_palette_cycle"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Cycle> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Cycle::direction,
			Cycle::new
		);

		@Override
		public CustomPacketPayload.Type<Cycle> type() {
			return TYPE;
		}
	}

	/**
	 * Pick block: selects the looked at block, adding it to the palette when missing.
	 */
	public record Pick(BlockPos pos) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Pick> TYPE = new CustomPacketPayload.Type<>(Kasuri.id("brush_palette_pick"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Pick> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, Pick::pos,
			Pick::new
		);

		@Override
		public CustomPacketPayload.Type<Pick> type() {
			return TYPE;
		}
	}
}
