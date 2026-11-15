package com.ookasamoti.kasuri.client.model;

import com.mojang.serialization.MapCodec;
import com.ookasamoti.kasuri.item.ModDataComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
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
import org.joml.Vector3f;
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
		if (baked == null || baked.isEmpty()) {
			// Blocks drawn by a block entity renderer have no quads, so show the entry's item as usual instead
			ItemStack plain = item.copy();
			plain.set(DataComponents.ITEM_MODEL, item.getItem().components().get(DataComponents.ITEM_MODEL));
			resolver.appendItemLayers(output, plain, displayContext, level, owner, seed);
			return;
		}

		output.appendModelIdentityElement(this);
		output.appendModelIdentityElement(state);
		if (!baked.quads().isEmpty()) {
			ItemStackRenderState.LayerRenderState layer = this.newLayer(output, baked, displayContext);
			for (int tint : baked.tints()) {
				layer.tintLayers().add(tint);
			}

			layer.setQuads(baked.quads());
		}

		if (!baked.shaded().quads().isEmpty()) {
			this.newLayer(output, baked, displayContext).setupSpecialModel(ShadedQuadRenderer.INSTANCE, baked.shaded());
		}

		if (baked.model().hasMaterialFlag(BakedQuad.FLAG_ANIMATED)) {
			output.setAnimated();
		}
	}

	private ItemStackRenderState.LayerRenderState newLayer(final ItemStackRenderState output, final Baked baked, final ItemDisplayContext displayContext) {
		ItemStackRenderState.LayerRenderState layer = output.newLayer();
		layer.setExtents(baked.extents());
		layer.setLocalTransform(this.transformation);
		layer.setUsesBlockLight(this.usesBlockLight);
		layer.setParticleMaterial(baked.model().particleMaterial());
		layer.setItemTransform(this.transforms.getTransform(displayContext));
		return layer;
	}

	private static Baked bakeState(final BlockState state) {
		Minecraft minecraft = Minecraft.getInstance();
		BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(42L), parts);

		List<BakedQuad> all = new ArrayList<>();
		List<BakedQuad> plain = new ArrayList<>();
		List<BakedQuad> shaded = new ArrayList<>();
		int maxTint = -1;
		for (BlockStateModelPart part : parts) {
			for (Direction direction : QUAD_DIRECTIONS) {
				for (BakedQuad quad : part.getQuads(direction)) {
					all.add(quad);
					(shadeDirection(quad) == null ? plain : shaded).add(quad);
					maxTint = Math.max(maxTint, quad.materialInfo().tintIndex());
				}
			}
		}

		// Item rendering looks tints up by layer, so give it the block's colors without a world position
		BlockColors blockColors = minecraft.getBlockColors();
		int[] tints = new int[maxTint + 1];
		for (int tintIndex = 0; tintIndex <= maxTint; tintIndex++) {
			BlockTintSource source = blockColors.getTintSource(state, tintIndex);
			tints[tintIndex] = source == null ? -1 : ARGB.opaque(source.color(state));
		}

		List<BakedQuad> allQuads = List.copyOf(all);
		return new Baked(
			model, ItemQuads.split(plain), new ShadedQuads(List.copyOf(shaded), tints),
			Suppliers.memoize(() -> CuboidItemModelWrapper.computeExtents(allQuads)), tints
		);
	}

	/**
	 * Returns the direction a quad should be lit as instead of its own, or null to light it normally. That is its
	 * shade direction override, or up for faces at an angle, like the crossed planes of plants: some models (such
	 * as the flower pots Sodium replaces) still mark those with the old "shade": false, which 26.3 no longer reads.
	 */
	private static @Nullable Direction shadeDirection(final BakedQuad quad) {
		Direction override = quad.materialInfo().shadeDirectionOverride();
		if (override != null) {
			return override;
		}

		Vector3f normal = new Vector3f(quad.position(1)).sub(quad.position(0))
			.cross(new Vector3f(quad.position(2)).sub(quad.position(0)));
		if (normal.lengthSquared() < 1.0E-8F) {
			return null;
		}

		normal.normalize();
		float axisAligned = Math.max(Math.abs(normal.x()), Math.max(Math.abs(normal.y()), Math.abs(normal.z())));
		return axisAligned > 0.999F ? null : Direction.UP;
	}

	private record Baked(BlockStateModel model, ItemQuads quads, ShadedQuads shaded, Supplier<Vector3fc[]> extents, int[] tints) {
		boolean isEmpty() {
			return this.quads.isEmpty() && this.shaded.quads().isEmpty();
		}
	}

	/**
	 * Faces lit by another direction than their own (see {@link #shadeDirection}), such as the crossed planes of a
	 * potted plant, which the block renderer lights as if they faced up.
	 */
	private record ShadedQuads(List<BakedQuad> quads, int[] tints) {
	}

	/**
	 * Draws {@link ShadedQuads} with their shade direction as the vertex normal. Item rendering otherwise lights a face by
	 * its own direction (or, with Sodium, by its geometry), which leaves the diagonal planes dark.
	 */
	private static final class ShadedQuadRenderer implements SpecialModelRenderer<ShadedQuads> {
		static final ShadedQuadRenderer INSTANCE = new ShadedQuadRenderer();

		@Override
		public void submit(
			final @Nullable ShadedQuads argument,
			final PoseStack poseStack,
			final SubmitNodeCollector submitNodeCollector,
			final int lightCoords,
			final int overlayCoords,
			final boolean hasFoil,
			final int outlineColor
		) {
			if (argument == null) {
				return;
			}

			Map<RenderType, List<BakedQuad>> byRenderType = new LinkedHashMap<>();
			for (BakedQuad quad : argument.quads()) {
				byRenderType.computeIfAbsent(quad.materialInfo().itemRenderType(), type -> new ArrayList<>()).add(quad);
			}

			byRenderType.forEach((renderType, quads) -> submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
				for (BakedQuad quad : quads) {
					BakedQuad.MaterialInfo material = quad.materialInfo();
					int color = material.isTinted() && material.tintIndex() < argument.tints().length ? argument.tints()[material.tintIndex()] : -1;
					Vector3f normal = pose.transformNormal(shadeDirection(quad).getUnitVec3f(), new Vector3f());
					for (int vertex = 0; vertex < 4; vertex++) {
						Vector3f position = pose.pose().transformPosition(quad.position(vertex), new Vector3f());
						long uv = quad.packedUV(vertex);
						buffer.addVertex(
							position.x(), position.y(), position.z(), color, UVPair.unpackU(uv), UVPair.unpackV(uv),
							overlayCoords, lightCoords, normal.x(), normal.y(), normal.z()
						);
					}
				}
			}));
		}

		@Override
		public void getExtents(final Consumer<Vector3fc> output) {
		}

		@Override
		public @Nullable ShadedQuads extractArgument(final ItemStack stack) {
			return null;
		}
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
