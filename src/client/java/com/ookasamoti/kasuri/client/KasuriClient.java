package com.ookasamoti.kasuri.client;

import com.ookasamoti.kasuri.Kasuri;
import com.ookasamoti.kasuri.client.model.BlockStateItemModel;
import com.ookasamoti.kasuri.client.screen.BrushPaletteScreen;
import com.ookasamoti.kasuri.menu.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.client.player.ClientHotbarScrollEvents;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemModels;

public class KasuriClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ItemModels.ID_MAPPER.put(Kasuri.id("block_state"), BlockStateItemModel.Unbaked.MAP_CODEC);
		KeyMappingHelper.registerKeyMapping(BlockBrushClient.PALETTE_KEY);
		MenuScreens.register(ModMenus.BRUSH_PALETTE, BrushPaletteScreen::new);
		ClientTickEvents.END_CLIENT_TICK.register(BlockBrushClient::onEndTick);
		ClientHotbarScrollEvents.ALLOW.register((inventory, oldSlot, newSlot, scrollX, scrollY) ->
			BlockBrushClient.onHotbarScroll(oldSlot, newSlot)
		);
	}
}
