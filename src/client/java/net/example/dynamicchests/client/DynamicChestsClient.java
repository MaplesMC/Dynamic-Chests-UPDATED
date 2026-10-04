package net.example.dynamicchests.client;

import net.example.dynamicchests.registry.ModRegistry;
import net.example.dynamicchests.render.VaultChestBlockEntityRenderer;
import net.example.dynamicchests.render.GamblerChestBlockEntityRenderer;
import net.example.dynamicchests.screen.GamblerChestScreen;
import net.example.dynamicchests.woodcutter.WoodcutterChestRenderer;
import net.example.dynamicchests.woodcutter.WoodcutterScreen;
import net.example.dynamicchests.screen.FurnaceChestScreen;
import net.example.dynamicchests.screen.VoidChestScreen;
import net.example.dynamicchests.screen.ShadowChestScreen;
import net.example.dynamicchests.screen.SoulChestScreen;
import net.example.dynamicchests.render.ShadowChestBlockEntityRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.Identifier;

public class DynamicChestsClient implements ClientModInitializer {

	private static Identifier tex(String name) {
		return Identifier.fromNamespaceAndPath("dynamicchests", "textures/entity/chest/" + name + ".png");
	}

	@Override
	public void onInitializeClient() {
		net.example.dynamicchests.sahur.SahurChestClient.init(); // SAHUR-CHEST (delete with the sahur package)
		MenuScreens.register(ModRegistry.VOID_CHEST_MENU, VoidChestScreen::new);
		MenuScreens.register(ModRegistry.SHADOW_CHEST_MENU, ShadowChestScreen::new);
		MenuScreens.register(ModRegistry.SOUL_CHEST_MENU, SoulChestScreen::new);
		MenuScreens.register(ModRegistry.GAMBLER_CHEST_MENU, GamblerChestScreen::new);
		MenuScreens.register(ModRegistry.WOODCUTTER_MENU, WoodcutterScreen::new);
		BlockEntityRenderers.register(ModRegistry.WOODCUTTER_BLOCK_ENTITY, WoodcutterChestRenderer::new);
		MenuScreens.register(ModRegistry.FURNACE_CHEST_MENU, FurnaceChestScreen::new);

		BlockEntityRenderers.register(ModRegistry.VOID_CHEST_BLOCK_ENTITY,
				context -> new VaultChestBlockEntityRenderer<>(context,
						tex("void_chest"), tex("void_chest_left"), tex("void_chest_right")));

		BlockEntityRenderers.register(ModRegistry.SHADOW_CHEST_BLOCK_ENTITY,
				context -> new ShadowChestBlockEntityRenderer(context));

		BlockEntityRenderers.register(ModRegistry.GAMBLER_CHEST_BLOCK_ENTITY,
				context -> new GamblerChestBlockEntityRenderer(context,
						tex("gambler_chest"), tex("gambler_chest_active"), tex("gambler_chest_jackpot")));

		BlockEntityRenderers.register(ModRegistry.POCKET_CHEST_BLOCK_ENTITY,
				context -> new VaultChestBlockEntityRenderer<>(context,
						tex("pocket_chest"), tex("pocket_chest"), tex("pocket_chest")));

		BlockEntityRenderers.register(ModRegistry.FURNACE_CHEST_BLOCK_ENTITY,
				context -> new VaultChestBlockEntityRenderer<>(context,
						tex("furnace_chest"), tex("furnace_chest"), tex("furnace_chest")));

		BlockEntityRenderers.register(ModRegistry.SOUL_CHEST_BLOCK_ENTITY,
				context -> new VaultChestBlockEntityRenderer<>(context,
						tex("soul_chest"), tex("soul_chest_left"), tex("soul_chest_right")));
	}
}
