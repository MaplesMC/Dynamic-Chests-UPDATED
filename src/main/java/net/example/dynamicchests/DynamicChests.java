package net.example.dynamicchests;

import net.example.dynamicchests.block.entity.SoulChestBlockEntity;
import net.example.dynamicchests.data.SoulChestSavedData;
import net.example.dynamicchests.gambler.config.GamblerConfig;
import net.example.dynamicchests.pocket.PocketChunkGenerator;
import net.example.dynamicchests.pocket.PocketConfig;
import net.example.dynamicchests.pocket.PocketCommands;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.example.dynamicchests.pocket.PocketManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.example.dynamicchests.registry.ModRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicChests implements ModInitializer {

	public static final String MOD_ID = "dynamicchests";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Dynamic Chests");

		GamblerConfig.load();
		ModRegistry.init();
		net.example.dynamicchests.sahur.SahurChest.init(); // SAHUR-CHEST (delete with the sahur package)

		PocketConfig.load();
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.fromNamespaceAndPath(MOD_ID, "pocket"), PocketChunkGenerator.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(PocketManager::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> PocketCommands.register(dispatcher));
		ServerLifecycleEvents.SERVER_STARTED.register(PocketManager::applyClockSettings);

		registerSoulChestCapture();
	}

	private void registerSoulChestCapture() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
			if (!(entity instanceof ServerPlayer player)) return true;
			if (!(entity.level() instanceof ServerLevel level)) return true;

			SoulChestBlockEntity chest = SoulChestSavedData
					.getOrCreate(level.getServer())
					.findNearest(level.getServer(), level.dimension(), entity.blockPosition());

			if (chest != null) {
				chest.capturePlayerInventory(player);
			}

			return true;
		});
	}
}
