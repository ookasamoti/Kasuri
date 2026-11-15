package com.ookasamoti.kasuri.network;

import com.ookasamoti.kasuri.Kasuri;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Sent by the client when the Block Box should place its selected block against the clicked face.
 */
public record BoxPlacePayload(BlockHitResult hit) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BoxPlacePayload> TYPE = new CustomPacketPayload.Type<>(Kasuri.id("box_place"));
	private static final StreamCodec<RegistryFriendlyByteBuf, BlockHitResult> HIT_CODEC = StreamCodec.composite(
		Vec3.STREAM_CODEC, BlockHitResult::getLocation,
		Direction.STREAM_CODEC, BlockHitResult::getDirection,
		BlockPos.STREAM_CODEC, BlockHitResult::getBlockPos,
		ByteBufCodecs.BOOL, BlockHitResult::isInside,
		BlockHitResult::new
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, BoxPlacePayload> STREAM_CODEC = HIT_CODEC.map(BoxPlacePayload::new, BoxPlacePayload::hit);

	@Override
	public CustomPacketPayload.Type<BoxPlacePayload> type() {
		return TYPE;
	}
}
