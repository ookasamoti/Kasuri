package com.ookasamoti.kasuri.item;

import com.ookasamoti.kasuri.Kasuri;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	public static final Item BLOCK_BRUSH = register("block_brush", BlockBrushItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Kasuri.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void initialize() {
		// Loads the class so the static fields above get registered
	}
}
