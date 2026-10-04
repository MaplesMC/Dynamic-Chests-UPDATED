package net.example.dynamicchests.sahur;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** Client hookup of the Sahur Chest (see {@link SahurChest} for how to remove the whole feature). */
public final class SahurChestClient {

	private SahurChestClient() {
	}

	public static void init() {
		BlockEntityRenderers.register(SahurChest.BLOCK_ENTITY, SahurChestRenderer::new);
	}
}
