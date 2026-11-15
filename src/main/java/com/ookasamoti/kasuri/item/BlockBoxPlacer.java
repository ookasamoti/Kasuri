package com.ookasamoti.kasuri.item;

import com.ookasamoti.kasuri.Kasuri;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * Server side logic of the Block Box: places palette blocks, including the block state picked with pick block.
 */
final class BlockBoxPlacer {
	/**
	 * Value types of properties that come from where and how a block is placed (its orientation, which half of a
	 * double block it is, how it connects to its neighbours) rather than from what the block is.
	 */
	private static final Set<Class<?>> PLACEMENT_VALUE_TYPES = Set.of(
		Direction.class, Direction.Axis.class, FrontAndTop.class, AttachFace.class, Half.class, StairsShape.class,
		RailShape.class, DoubleBlockHalf.class, BedPart.class, ChestType.class, DoorHingeSide.class,
		BellAttachType.class, WallSide.class, RedstoneSide.class
	);
	private static final Set<String> PLACEMENT_PROPERTY_NAMES = Set.of(
		"waterlogged", "north", "east", "south", "west", "up", "down", "rotation", "in_wall", "powered",
		"signal_fire", "hanging", "attached"
	);

	private static final Identifier PALETTE_BLOCK_MODEL = Kasuri.id("palette_block");

	private BlockBoxPlacer() {
	}

	/**
	 * Creates the palette entry for picking {@code state}: the block's own item, remembering any state beyond its
	 * orientation (an unlit campfire, three candles) and a banner's patterns. Blocks without an item, like potted plants, are shown with
	 * their pick block item and placed from the remembered state alone. Returns an empty stack when nothing fits.
	 */
	static ItemStack entry(final LevelReader level, final BlockPos pos, final BlockState state) {
		if (state.isAir()) {
			return ItemStack.EMPTY;
		}

		Block block = state.getBlock();
		BlockState kept = normalize(state);
		if (block.asItem() instanceof BlockItem item) {
			ItemStack stack = BrushPalette.entry(item);
			if (!kept.equals(normalize(block.defaultBlockState()))) {
				setState(stack, kept);
			}

			// Placing applies these to the new banner, just like a patterned banner item would
			if (level.getBlockEntity(pos) instanceof BannerBlockEntity banner && !banner.getPatterns().layers().isEmpty()) {
				stack.set(DataComponents.BANNER_PATTERNS, banner.getPatterns());
			}

			return stack;
		}

		ItemStack shown = state.getCloneItemStack(level, pos, false);
		if (shown.isEmpty()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = BrushPalette.entry(shown.getItem());
		setState(stack, kept);
		stack.set(DataComponents.ITEM_NAME, block.getName());
		return stack;
	}

	/**
	 * Remembers {@code state} on the entry and has it drawn with the block's model (see BlockStateItemModel), since
	 * the item alone would not show the difference.
	 */
	private static void setState(final ItemStack stack, final BlockState state) {
		stack.set(ModDataComponents.PALETTE_STATE, state);
		stack.set(DataComponents.ITEM_MODEL, PALETTE_BLOCK_MODEL);
	}

	/**
	 * Places {@code selected} against the clicked face like using it would, then applies its remembered state.
	 */
	static void place(final ServerPlayer player, final BlockHitResult hit, final ItemStack selected) {
		BlockState stored = selected.get(ModDataComponents.PALETTE_STATE);
		// A copy each time, so nothing is ever used up
		BlockPlaceContext context = new BlockPlaceContext(player.level(), player, InteractionHand.MAIN_HAND, selected.copy(), hit);
		BlockState placed;
		if (stored != null && !(stored.getBlock().asItem() instanceof BlockItem)) {
			placed = placeWithoutItem(player, context, stored);
		} else if (selected.getItem() instanceof BlockItem blockItem) {
			placed = placeWithItem(context, blockItem, stored);
		} else {
			placed = null;
		}

		if (placed != null) {
			playPlaceSound(player, context.getClickedPos(), placed, stored != null && !(stored.getBlock().asItem() instanceof BlockItem));
		}
	}

	private static @Nullable BlockState placeWithItem(final BlockPlaceContext context, final BlockItem blockItem, final @Nullable BlockState stored) {
		if (!blockItem.place(context).consumesAction()) {
			return null;
		}

		ServerLevel level = (ServerLevel) context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState placed = level.getBlockState(pos);
		if (stored != null) {
			BlockState restored = Block.updateFromNeighbourShapes(copyKept(stored, placed), level, pos);
			if (restored != placed && level.setBlock(pos, restored, Block.UPDATE_ALL)) {
				placed = restored;
			}
		}

		return placed;
	}

	/**
	 * Places a block that has no item of its own (a potted plant), following what BlockItem does.
	 */
	private static @Nullable BlockState placeWithoutItem(final ServerPlayer player, final BlockPlaceContext context, final BlockState stored) {
		if (!context.canPlace()) {
			return null;
		}

		ServerLevel level = player.level();
		BlockPos pos = context.getClickedPos();
		BlockState state = stored.getBlock().getStateForPlacement(context);
		if (state == null) {
			return null;
		}

		state = Block.updateFromNeighbourShapes(copyKept(stored, state), level, pos);
		if (!state.canSurvive(level, pos) || !level.isUnobstructed(state, pos, CollisionContext.placementContext(player))) {
			return null;
		}

		if (!level.setBlock(pos, state, Block.UPDATE_ALL_IMMEDIATE)) {
			return null;
		}

		state.getBlock().setPlacedBy(level, pos, state, player, context.getItemInHand());
		level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, state));
		return state;
	}

	/**
	 * BlockItem plays the place sound for everyone but the placing player, whose client normally plays it itself
	 * but knows nothing about this placement.
	 */
	private static void playPlaceSound(final ServerPlayer player, final BlockPos pos, final BlockState state, final boolean forOthers) {
		SoundType soundType = state.getSoundType();
		float volume = (soundType.getVolume() + 1.0F) / 2.0F;
		float pitch = soundType.getPitch() * 0.8F;
		if (forOthers) {
			player.level().playSound(player, pos, soundType.getPlaceSound(), SoundSource.BLOCKS, volume, pitch);
		}

		player.connection.send(new ClientboundSoundPacket(
			BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundType.getPlaceSound()), SoundSource.BLOCKS,
			pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, volume, pitch, player.getRandom().nextLong()
		));
	}

	/**
	 * Resets the properties that placing decides, so the same block picked in any orientation gives the same entry.
	 */
	private static BlockState normalize(final BlockState state) {
		BlockState defaults = state.getBlock().defaultBlockState();
		BlockState normalized = state;
		for (Property<?> property : state.getProperties()) {
			if (isPlacementProperty(state, property)) {
				normalized = copyValue(defaults, property, normalized);
			}
		}

		return normalized;
	}

	/**
	 * Copies the remembered properties of {@code stored} onto {@code placed} by name, which also carries them over
	 * between related blocks such as a wall torch and a torch.
	 */
	private static BlockState copyKept(final BlockState stored, final BlockState placed) {
		BlockState result = placed;
		for (Property<?> property : stored.getProperties()) {
			if (!isPlacementProperty(stored, property)) {
				Property<?> target = placed.getBlock().getStateDefinition().getProperty(property.getName());
				if (target != null) {
					result = withValue(result, target, valueName(stored, property));
				}
			}
		}

		return result;
	}

	private static boolean isPlacementProperty(final BlockState state, final Property<?> property) {
		if (property == BlockStateProperties.SLAB_TYPE) {
			// Top or bottom depends on where it is placed, but a double slab is a block of its own
			return state.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE;
		}

		return PLACEMENT_VALUE_TYPES.contains(property.getValueClass()) || PLACEMENT_PROPERTY_NAMES.contains(property.getName());
	}

	private static <T extends Comparable<T>> BlockState copyValue(final BlockState from, final Property<T> property, final BlockState to) {
		return to.setValue(property, from.getValue(property));
	}

	private static <T extends Comparable<T>> String valueName(final BlockState state, final Property<T> property) {
		return property.getName(state.getValue(property));
	}

	private static <T extends Comparable<T>> BlockState withValue(final BlockState state, final Property<T> property, final String valueName) {
		return property.getValue(valueName).map(value -> state.setValue(property, value)).orElse(state);
	}
}
