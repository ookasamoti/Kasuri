package com.ookasamoti.kasuri.item;

import com.google.common.base.Suppliers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import com.ookasamoti.kasuri.menu.BrushPaletteMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Server side logic of the Block Brush, which swaps a block for another one while keeping its shape and state, and
 * of the Block Box, which places blocks. Both share the palette handling.
 */
public final class BlockBrushPainter {
	/**
	 * Block shapes that may only be swapped for a block of the same shape (stairs for stairs, slab for slab, ...),
	 * with the id suffix blocks of that shape usually have.
	 */
	private static final Map<Class<? extends Block>, String> VARIANT_SHAPES = Map.of(
		StairBlock.class, "stairs",
		SlabBlock.class, "slab",
		WallBlock.class, "wall",
		FenceBlock.class, "fence",
		FenceGateBlock.class, "fence_gate",
		TrapDoorBlock.class, "trapdoor",
		BasePressurePlateBlock.class, "pressure_plate",
		ButtonBlock.class, "button",
		IronBarsBlock.class, "pane",
		CarpetBlock.class, "carpet"
	);

	/**
	 * Id suffixes of base blocks that their variants drop (oak_planks -> oak_stairs, white_wool -> white_carpet).
	 */
	private static final List<String> BASE_SUFFIXES = List.of("_planks", "_wool", "_block");

	/**
	 * Shaped variants of vanilla block families mapped to their base block (oak_stairs -> oak_planks).
	 */
	private static final Supplier<Map<Block, Block>> VARIANT_BASES = Suppliers.memoize(() -> {
		Map<Block, Block> bases = new HashMap<>();
		BlockFamilies.getAllFamilies().forEach(family -> family.getVariants().values().forEach(variant -> {
			if (variantShape(variant) != null) {
				bases.putIfAbsent(variant, family.getBaseBlock());
			}
		}));
		return bases;
	});

	/**
	 * The block replaced by each player's most recent palette paint, used by left click.
	 */
	private static final Map<UUID, Block> LAST_REPLACED = new ConcurrentHashMap<>();

	private BlockBrushPainter() {
	}

	public static void paint(final ServerPlayer player, final BlockPos pos, final boolean restore) {
		ItemStack brush = heldBrush(player);
		if (brush == null || !canReach(player, pos)) {
			return;
		}

		ServerLevel level = player.level();
		Block source = restore ? LAST_REPLACED.get(player.getUUID()) : BrushPalette.of(brush).selectedBlock();
		if (source == null) {
			return;
		}

		BlockState target = level.getBlockState(pos);
		BlockState replacement = createReplacement(target, source, restore);
		if (replacement == null) {
			return;
		}

		replacement = Block.updateFromNeighbourShapes(replacement, level, pos);
		if (!level.setBlock(pos, replacement, Block.UPDATE_ALL)) {
			return;
		}

		if (!restore) {
			// Remember the material rather than the shape, so left click restores cherry stairs onto any shape as cherry
			LAST_REPLACED.put(player.getUUID(), findBase(target.getBlock()));
		}

		SoundType soundType = replacement.getSoundType();
		level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
	}

	public static void forget(final ServerPlayer player) {
		LAST_REPLACED.remove(player.getUUID());
	}

	public static void openPalette(final ServerPlayer player) {
		ItemStack brush = heldTool(player);
		if (brush != null) {
			player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> new BrushPaletteMenu(containerId, inventory, brush), brush.getHoverName()));
		}
	}

	public static void cyclePalette(final ServerPlayer player, final int direction) {
		ItemStack brush = heldTool(player);
		if (brush == null) {
			return;
		}

		BrushPalette palette = BrushPalette.of(brush).cycle(Integer.signum(direction));
		brush.set(ModDataComponents.BRUSH_PALETTE, palette);
		showSelected(player, palette);
	}

	/**
	 * Selects the block at {@code pos} in the palette, appending it when it is not there yet. Like left click it
	 * goes by material, so picking cherry stairs selects cherry planks.
	 */
	public static void pick(final ServerPlayer player, final BlockPos pos) {
		ItemStack brush = heldTool(player);
		if (brush == null || !canReach(player, pos)) {
			return;
		}

		ItemStack entry = BrushPalette.entry(findBase(player.level().getBlockState(pos).getBlock()).asItem());
		if (!BrushPalette.accepts(entry)) {
			return;
		}

		BrushPalette palette = BrushPalette.of(brush);
		int slot = palette.indexOf(entry.getItem());
		if (slot < 0) {
			slot = palette.appendSlot();
			if (slot < 0) {
				player.sendOverlayMessage(Component.translatable("item.kasuri.block_brush.palette_full"));
				return;
			}

			NonNullList<ItemStack> items = palette.items();
			items.set(slot, entry);
			palette = palette.withItems(items);
		}

		palette = palette.withSelected(slot);
		brush.set(ModDataComponents.BRUSH_PALETTE, palette);
		showSelected(player, palette);
	}

	private static void showSelected(final ServerPlayer player, final BrushPalette palette) {
		ItemStack selected = palette.selectedItem();
		if (!selected.isEmpty()) {
			player.sendOverlayMessage(selected.getHoverName());
		}
	}

	/**
	 * Places the Block Box's selected block against the clicked face, the same way using the block item would.
	 */
	public static void place(final ServerPlayer player, final BlockHitResult hit) {
		ItemStack box = heldTool(player);
		if (!(box != null && box.getItem() instanceof BlockBoxItem) || !canReach(player, hit.getBlockPos())) {
			return;
		}

		// A fresh stack each time, so nothing is ever used up
		ItemStack selected = BrushPalette.of(box).selectedItem();
		if (!(selected.getItem() instanceof BlockItem blockItem)) {
			return;
		}

		BlockPlaceContext context = new BlockPlaceContext(player.level(), player, InteractionHand.MAIN_HAND, selected, hit);
		if (!blockItem.place(context).consumesAction()) {
			return;
		}

		// Vanilla leaves the place sound to the placing client, which knows nothing about this placement
		BlockPos pos = context.getClickedPos();
		BlockState placed = player.level().getBlockState(pos);
		SoundType soundType = placed.getSoundType();
		player.connection.send(new ClientboundSoundPacket(
			BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundType.getPlaceSound()), SoundSource.BLOCKS,
			pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
			(soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F, player.getRandom().nextLong()
		));
	}

	/**
	 * Returns the Block Brush in the player's main hand, or null when there is none or the player may not use it.
	 */
	private static @Nullable ItemStack heldBrush(final ServerPlayer player) {
		ItemStack stack = heldTool(player);
		return stack != null && stack.getItem() instanceof BlockBrushItem ? stack : null;
	}

	/**
	 * Returns the palette tool (Block Brush or Block Box) in the player's main hand, or null when there is none or
	 * the player may not use it.
	 */
	private static @Nullable ItemStack heldTool(final ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		return stack.getItem() instanceof PaletteToolItem && player.canUseGameMasterBlocks() ? stack : null;
	}

	private static boolean canReach(final ServerPlayer player, final BlockPos pos) {
		ServerLevel level = player.level();
		return level.isLoaded(pos) && player.isWithinBlockInteractionRange(pos, 1.0) && level.mayInteract(player, pos);
	}

	/**
	 * Returns the state {@code source} should take when it replaces {@code target}, or null if it may not replace it.
	 *
	 * @param keepShape whether a variant source may only replace the same variant, even on a full block target
	 */
	private static @Nullable BlockState createReplacement(final BlockState target, final Block source, final boolean keepShape) {
		if (target.isAir() || isMultiBlock(target)) {
			return null;
		}

		// A full block paints a variant target with its own variant of that shape (oak planks on stairs -> oak stairs)
		Class<? extends Block> targetShape = variantShape(target.getBlock());
		Class<? extends Block> sourceShape = variantShape(source);
		if (keepShape && targetShape == null && sourceShape != null) {
			return null;
		}

		Block block = targetShape == null || targetShape == sourceShape ? source : findVariant(source, targetShape);
		if (block == null || block == target.getBlock() || isMultiBlock(block.defaultBlockState())) {
			return null;
		}

		BlockState replacement = block.defaultBlockState();
		for (Property<?> property : target.getProperties()) {
			replacement = copyProperty(target, property, replacement);
		}

		return replacement;
	}

	private static @Nullable Class<? extends Block> variantShape(final Block block) {
		for (Class<? extends Block> shape : VARIANT_SHAPES.keySet()) {
			if (shape.isInstance(block)) {
				return shape;
			}
		}

		return null;
	}

	/**
	 * Finds the block of the given shape made from {@code base}, using vanilla block families first and falling back
	 * to id naming conventions for blocks without a family (mostly modded ones).
	 */
	private static @Nullable Block findVariant(final Block base, final Class<? extends Block> shape) {
		BlockFamily family = BlockFamilies.getFamily(base);
		if (family != null) {
			for (Block variant : family.getVariants().values()) {
				if (shape.isInstance(variant)) {
					return variant;
				}
			}
		}

		Identifier id = BuiltInRegistries.BLOCK.getKey(base);
		String suffix = "_" + VARIANT_SHAPES.get(shape);
		for (String name : baseNames(id.getPath())) {
			Block variant = BuiltInRegistries.BLOCK.getValue(id.withPath(name + suffix));
			if (shape.isInstance(variant)) {
				return variant;
			}
		}

		return null;
	}

	/**
	 * The reverse of {@link #findVariant}: returns the base block {@code block} is a variant of (cherry stairs ->
	 * cherry planks), or {@code block} itself when it is not a variant or its base cannot be found.
	 */
	private static Block findBase(final Block block) {
		Class<? extends Block> shape = variantShape(block);
		if (shape == null) {
			return block;
		}

		Block base = VARIANT_BASES.get().get(block);
		if (base != null) {
			return base;
		}

		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		String suffix = "_" + VARIANT_SHAPES.get(shape);
		if (id.getPath().endsWith(suffix)) {
			String name = id.getPath().substring(0, id.getPath().length() - suffix.length());
			List<String> candidates = new ArrayList<>(List.of(name, name + "s"));
			for (String baseSuffix : BASE_SUFFIXES) {
				candidates.add(name + baseSuffix);
			}

			for (String candidate : candidates) {
				Block candidateBlock = BuiltInRegistries.BLOCK.getValue(id.withPath(candidate));
				// Only accept it if it really leads back here, which also rules out air for missing ids
				if (variantShape(candidateBlock) == null && findVariant(candidateBlock, shape) == block) {
					return candidateBlock;
				}
			}
		}

		return block;
	}

	private static List<String> baseNames(final String path) {
		List<String> names = new ArrayList<>(List.of(path));
		for (String suffix : BASE_SUFFIXES) {
			if (path.endsWith(suffix)) {
				names.add(path.substring(0, path.length() - suffix.length()));
			}
		}

		// stone_bricks -> stone_brick_stairs
		if (path.endsWith("s")) {
			names.add(path.substring(0, path.length() - 1));
		}

		return names;
	}

	/**
	 * Doors, beds, tall plants and the like span several blocks, and swapping only one part of them would break them.
	 */
	private static boolean isMultiBlock(final BlockState state) {
		return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.hasProperty(BlockStateProperties.BED_PART);
	}

	/**
	 * Copies the value of {@code property} (facing, type, waterlogged, ...) to a property of the same name, when the
	 * replacement has one that accepts the value.
	 */
	private static <T extends Comparable<T>> BlockState copyProperty(final BlockState from, final Property<T> property, final BlockState to) {
		Property<?> targetProperty = to.getBlock().getStateDefinition().getProperty(property.getName());
		return targetProperty == null ? to : withValue(to, targetProperty, property.getName(from.getValue(property)));
	}

	private static <T extends Comparable<T>> BlockState withValue(final BlockState state, final Property<T> property, final String valueName) {
		return property.getValue(valueName).map(value -> state.setValue(property, value)).orElse(state);
	}
}
