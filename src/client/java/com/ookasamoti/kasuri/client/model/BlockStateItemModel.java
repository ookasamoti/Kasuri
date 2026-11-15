package com.ookasamoti.kasuri.client.model;

import com.mojang.serialization.MapCodec;
import com.ookasamoti.kasuri.item.ModDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import com.google.common.base.Suppliers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Item model type {@code kasuri:block_state}: draws the block state remembered by a Block Box palette entry with its
 * block model, held like a block item, so a potted plant or an unlit campfire looks like what will be placed.
 */
public class BlockStateItemModel implements ItemModel {
	/**
	 * Block items take their display transforms (the tilted view in the GUI and so on) from this model.
	 */
	private static final Identifier TRANSFORMS_MODEL = Identifier.withDefaultNamespace("block/block");
	private static final Direction[] QUAD_DIRECTIONS = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

	private final ItemTransforms transforms;
	private final boolean usesBlockLight;
	private final Matrix4fc transformation;
	/**
	 * Models are baked again on every resource reload, which also drops this cache.
	 */
	private final Map<BlockState, Baked> byState = new HashMap<>();

	private BlockStateItemModel(final ItemTransforms transforms, final boolean usesBlockLight, final Matrix4fc transformation) {
		this.transforms = transforms;
		this.usesBlockLight = usesBlockLight;
		this.transformation = new Matrix4f(transformation);
	}

	@Override
	public void update(
		final ItemStackRenderState output,
		final ItemStack item,
		final ItemModelResolver resolver,
		final ItemDisplayContext displayContext,
		final @Nullable ClientLevel level,
		final @Nullable ItemOwner owner,
		final int seed
	) {
		BlockState state = item.get(ModDataComponents.PALETTE_STATE);
		Baked baked = state == null ? null : this.byState.computeIfAbsent(state, BlockStateItemModel::bakeState);
		if (baked == null || baked.quads().isEmpty()) {
			// Blocks drawn by a block entity renderer have no quads, so show the entry's item as usual instead
			ItemStack plain = item.copy();
			plain.set(DataComponents.ITEM_MODEL, item.getItem().components().get(DataComponents.ITEM_MODEL));
			resolver.appendItemLayers(output, plain, displayContext, level, owner, seed);
			return;
		}

		output.appendModelIdentityElement(this);
		output.appendModelIdentityElement(state);
		ItemStackRenderState.LayerRenderState layer = output.newLayer();
		layer.tintLayers().addAll(baked.tints());
		layer.setExtents(baked.extents());
		layer.setLocalTransform(this.transformation);
		layer.setUsesBlockLight(this.usesBlockLight);
		layer.setParticleMaterial(baked.model().particleMaterial());
		layer.setItemTransform(this.transforms.getTransform(displayContext));
		layer.setQuads(baked.quads());
		if (baked.model().hasMaterialFlag(BakedQuad.FLAG_ANIMATED)) {
			output.setAnimated();
		}
	}

	private static Baked bakeState(final BlockState state) {
		Minecraft minecraft = Minecraft.getInstance();
		BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(42L), parts);

		List<BakedQuad> quads = new ArrayList<>();
		int maxTint = -1;
		for (BlockStateModelPart part : parts) {
			for (Direction direction : QUAD_DIRECTIONS) {
				for (BakedQuad quad : part.getQuads(direction)) {
					quads.add(withItemShading(quad));
					maxTint = Math.max(maxTint, quad.materialInfo().tintIndex());
				}
			}
		}

		// Item rendering looks tints up by layer, so give it the block's colors without a world position
		BlockColors blockColors = minecraft.getBlockColors();
		List<Integer> tints = new ArrayList<>();
		for (int tintIndex = 0; tintIndex <= maxTint; tintIndex++) {
			BlockTintSource source = blockColors.getTintSource(state, tintIndex);
			tints.add(source == null ? -1 : ARGB.opaque(source.color(state)));
		}

		List<BakedQuad> allQuads = List.copyOf(quads);
		return new Baked(model, ItemQuads.split(allQuads), Suppliers.memoize(() -> CuboidItemModelWrapper.computeExtents(allQuads)), tints);
	}

	/**
	 * Block rendering lights faces with a shade direction override (the crossed planes of a potted plant are lit as
	 * if facing up) by that direction, but item rendering only looks at the face direction, which leaves the
	 * diagonal planes dark. Face the quad that way instead so it is lit the same.
	 */
	private static BakedQuad withItemShading(final BakedQuad quad) {
		Direction shade = quad.materialInfo().shadeDirectionOverride();
		if (shade == null || shade == quad.direction()) {
			return quad;
		}

		return new BakedQuad(
			quad.position0(), quad.position1(), quad.position2(), quad.position3(),
			quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
			shade, quad.materialInfo()
		);
	}

	private record Baked(BlockStateModel model, ItemQuads quads, Supplier<Vector3fc[]> extents, List<Integer> tints) {
	}

	public record Unbaked() implements ItemModel.Unbaked {
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public void resolveDependencies(final ResolvableModel.Resolver resolver) {
			resolver.markDependency(TRANSFORMS_MODEL);
		}

		@Override
		public ItemModel bake(final ItemModel.BakingContext context, final Matrix4fc transformation) {
			ResolvedModel model = context.blockModelBaker().getModel(TRANSFORMS_MODEL);
			return new BlockStateItemModel(model.getTopTransforms(), model.getTopGuiLight().lightLikeBlock(), transformation);
		}
	}
}
