package com.ookasamoti.kasuri;

import com.ookasamoti.kasuri.item.BlockBrushPainter;
import com.ookasamoti.kasuri.item.ModDataComponents;
import com.ookasamoti.kasuri.item.ModItems;
import com.ookasamoti.kasuri.menu.ModMenus;
import com.ookasamoti.kasuri.network.BoxPlacePayload;
import com.ookasamoti.kasuri.network.BrushPaintPayload;
import com.ookasamoti.kasuri.network.BrushPalettePayloads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Kasuri implements ModInitializer {
	public static final String MOD_ID = "kasuri";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModDataComponents.initialize();
		ModItems.initialize();
		ModMenus.initialize();

		// Operator Utilities tab only shows its contents to players with the "Operator Items Tab" option enabled
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.OP_BLOCKS)
			.register(output -> {
				if (output.shouldShowOpRestrictedItems()) {
					output.insertAfter(Items.DEBUG_STICK, ModItems.BLOCK_BRUSH, ModItems.BLOCK_BOX);
				}
			});

		PayloadTypeRegistry.serverboundPlay().register(BrushPaintPayload.TYPE, BrushPaintPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BrushPaintPayload.TYPE, (payload, context) ->
			BlockBrushPainter.paint(context.player(), payload.pos(), payload.restore())
		);
		PayloadTypeRegistry.serverboundPlay().register(BoxPlacePayload.TYPE, BoxPlacePayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BoxPlacePayload.TYPE, (payload, context) ->
			BlockBrushPainter.place(context.player(), payload.hit())
		);
		PayloadTypeRegistry.serverboundPlay().register(BrushPalettePayloads.Open.TYPE, BrushPalettePayloads.Open.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BrushPalettePayloads.Open.TYPE, (payload, context) ->
			BlockBrushPainter.openPalette(context.player())
		);
		PayloadTypeRegistry.serverboundPlay().register(BrushPalettePayloads.Cycle.TYPE, BrushPalettePayloads.Cycle.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BrushPalettePayloads.Cycle.TYPE, (payload, context) ->
			BlockBrushPainter.cyclePalette(context.player(), payload.direction())
		);
		PayloadTypeRegistry.serverboundPlay().register(BrushPalettePayloads.Pick.TYPE, BrushPalettePayloads.Pick.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BrushPalettePayloads.Pick.TYPE, (payload, context) ->
			BlockBrushPainter.pick(context.player(), payload.pos())
		);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BlockBrushPainter.forget(handler.getPlayer()));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
