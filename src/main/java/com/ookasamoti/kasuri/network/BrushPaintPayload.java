package com.ookasamoti.kasuri.network;

import com.ookasamoti.kasuri.Kasuri;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent by the client when the Block Brush should paint a block.
 *
 * @param restore false paints with the off hand block (right click), true paints with the remembered original block (left click)
 */
public record BrushPaintPayload(BlockPos pos, boolean restore) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BrushPaintPayload> TYPE = new CustomPacketPayload.Type<>(Kasuri.id("brush_paint"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BrushPaintPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, BrushPaintPayload::pos,
		ByteBufCodecs.BOOL, BrushPaintPayload::restore,
		BrushPaintPayload::new
	);

	@Override
	public CustomPacketPayload.Type<BrushPaintPayload> type() {
		return TYPE;
	}
}
