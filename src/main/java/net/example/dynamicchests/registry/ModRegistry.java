package net.example.dynamicchests.registry;

import net.example.dynamicchests.DynamicChests;
import net.example.dynamicchests.block.GamblerChestBlock;
import net.example.dynamicchests.block.FurnaceChestBlock;
import net.example.dynamicchests.block.PocketChestBlock;
import net.example.dynamicchests.block.PocketReturnGateBlock;
import net.example.dynamicchests.block.VoidChestBlock;
import net.example.dynamicchests.block.ShadowChestBlock;
import net.example.dynamicchests.block.SoulChestBlock;
import net.example.dynamicchests.block.entity.GamblerChestBlockEntity;
import net.example.dynamicchests.block.entity.FurnaceChestBlockEntity;
import net.example.dynamicchests.block.entity.PocketChestBlockEntity;
import net.example.dynamicchests.block.entity.VoidChestBlockEntity;
import net.example.dynamicchests.block.entity.ShadowChestBlockEntity;
import net.example.dynamicchests.block.entity.SoulChestBlockEntity;
import net.example.dynamicchests.gambler.effects.GamblerSounds;
import net.example.dynamicchests.screen.GamblerChestMenu;
import net.example.dynamicchests.woodcutter.WoodcutterBlock;
import net.example.dynamicchests.woodcutter.WoodcutterBlockEntity;
import net.example.dynamicchests.woodcutter.WoodcutterMenu;
import net.example.dynamicchests.screen.FurnaceChestMenu;
import net.example.dynamicchests.screen.VoidChestMenu;
import net.example.dynamicchests.screen.ShadowChestMenu;
import net.example.dynamicchests.screen.SoulChestMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;

import java.util.function.Function;

/**
 * Central registry for all blocks, items, block entities and menu (screen handler) types
 * added by Dynamic Chests.
 */
public final class ModRegistry {

	private ModRegistry() {
	}

	// ---------------------------------------------------------------
	// Blocks
	// ---------------------------------------------------------------

	public static final VoidChestBlock VOID_CHEST_BLOCK = registerBlock(
			"void_chest",
			key -> new VoidChestBlock(BlockBehaviour.Properties.of()
					.setId(key)
					.mapColor(MapColor.COLOR_BLACK)
					.strength(2.5f)
					.sound(SoundType.DEEPSLATE)
					.noOcclusion())
	);

	public static final ShadowChestBlock SHADOW_CHEST_BLOCK = registerBlock(
			"shadow_chest",
			key -> new ShadowChestBlock(BlockBehaviour.Properties.of()
					.setId(key)
					.mapColor(MapColor.COLOR_BLACK)
					.strength(2.5f)
					.sound(SoundType.WOOD)
					.noOcclusion())
	);

	public static final SoulChestBlock SOUL_CHEST_BLOCK = registerBlock(
			"soul_chest",
			key -> new SoulChestBlock(BlockBehaviour.Properties.of()
					.setId(key)
					.mapColor(MapColor.COLOR_LIGHT_BLUE)
					.strength(2.5f)
					.sound(SoundType.SOUL_SAND)
					.noOcclusion())
	);

	public static final GamblerChestBlock GAMBLER_CHEST_BLOCK = registerBlock(
			"gambler_chest",
			key -> new GamblerChestBlock(BlockBehaviour.Properties.of()
					.setId(key)
					.mapColor(MapColor.COLOR_RED)
					.strength(2.5f)
					.sound(SoundType.WOOD)
					.noOcclusion())
	);

	public static final PocketChestBlock POCKET_CHEST_BLOCK = registerBlock(
			"pocket_chest",
			key -> new PocketChestBlock(BlockBehaviour.Properties.of()
					.setId(key)
					.mapColor(MapColor.SNOW)
					.strength(2.5f)
					.sound(SoundType.WOOD)
					.noOcclusion())
	);

	/** Doorway inside the pocket that leads back out. Unbreakable and walk-through. */
	public static final PocketReturnGateBlock POCKET_RETURN_GATE_BLOCK = registerBlock(
			"pocket_return_gate",
			key -> new PocketReturnGateBlock(BlockBehaviour.Properties.of()
					.setId(key)
					.mapColor(MapColor.SNOW)
					.strength(-1.0f, 3600000.0f)
					.noCollision()
					.noOcclusion()
					.noLootTable()
					.lightLevel(state -> 10)
					.sound(SoundType.AMETHYST))
	);

	public static final FurnaceChestBlock FURNACE_CHEST_BLOCK = registerBlock(
			"furnace_chest",
			key -> new FurnaceChestBlock(BlockBehaviour.Properties.of()
					.setId(key)
					.mapColor(MapColor.STONE)
					.strength(3.5f)
					.requiresCorrectToolForDrops()
					.sound(SoundType.STONE)
					.noOcclusion())
	);

	public static final WoodcutterBlock WOODCUTTER_BLOCK = registerBlock(
			"wood_cutter",
			key -> new WoodcutterBlock(BlockBehaviour.Properties.of()
					.setId(key)
					.mapColor(MapColor.WOOD)
					.strength(2.5f)
					.sound(SoundType.WOOD)
					.noOcclusion())
	);

	// ---------------------------------------------------------------
	// Items
	// ---------------------------------------------------------------

	public static final Item VOID_CHEST_ITEM = registerItem(
			"void_chest",
			key -> new BlockItem(VOID_CHEST_BLOCK, new Item.Properties().setId(key).useBlockDescriptionPrefix().rarity(Rarity.EPIC))
	);

	public static final Item SHADOW_CHEST_ITEM = registerItem(
			"shadow_chest",
			key -> new BlockItem(SHADOW_CHEST_BLOCK, new Item.Properties().setId(key).useBlockDescriptionPrefix().rarity(Rarity.UNCOMMON))
	);

	public static final Item SOUL_CHEST_ITEM = registerItem(
			"soul_chest",
			key -> new BlockItem(SOUL_CHEST_BLOCK, new Item.Properties().setId(key).useBlockDescriptionPrefix().rarity(Rarity.UNCOMMON))
	);

	public static final Item GAMBLER_CHEST_ITEM = registerItem(
			"gambler_chest",
			key -> new BlockItem(GAMBLER_CHEST_BLOCK, new Item.Properties().setId(key).useBlockDescriptionPrefix().rarity(Rarity.RARE))
	);

	/** Creative-only: right-click a Gambler's Chest with it to force its next gamble to be a jackpot. */
	public static final Item RNG_OVERRIDE_CHIP = registerItem(
			"rng_override_chip",
			key -> new Item(new Item.Properties().setId(key).rarity(Rarity.EPIC).stacksTo(16)
					.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true))
	);

	public static final Item POCKET_CHEST_ITEM = registerItem(
			"pocket_chest",
			key -> new BlockItem(POCKET_CHEST_BLOCK, new Item.Properties().setId(key).useBlockDescriptionPrefix().rarity(Rarity.EPIC))
	);

	public static final Item FURNACE_CHEST_ITEM = registerItem(
			"furnace_chest",
			key -> new BlockItem(FURNACE_CHEST_BLOCK, new Item.Properties().setId(key).useBlockDescriptionPrefix().rarity(Rarity.UNCOMMON))
	);

	public static final Item WOODCUTTER_ITEM = registerItem(
			"wood_cutter",
			key -> new BlockItem(WOODCUTTER_BLOCK, new Item.Properties().setId(key).useBlockDescriptionPrefix())
	);

	// ---------------------------------------------------------------
	// Block entities
	// ---------------------------------------------------------------
	// Using Fabric API's FabricBlockEntityTypeBuilder instead of vanilla
	// BlockEntityType.Builder, since the vanilla builder's exact current shape in
	// 26.2 couldn't be confirmed. FabricBlockEntityTypeBuilder is a stable Fabric
	// API wrapper that has worked the same way across many Minecraft versions.

	public static final BlockEntityType<VoidChestBlockEntity> VOID_CHEST_BLOCK_ENTITY = registerBlockEntity(
			"void_chest",
			FabricBlockEntityTypeBuilder.create(VoidChestBlockEntity::new, VOID_CHEST_BLOCK).build()
	);

	public static final BlockEntityType<ShadowChestBlockEntity> SHADOW_CHEST_BLOCK_ENTITY = registerBlockEntity(
			"shadow_chest",
			FabricBlockEntityTypeBuilder.create(ShadowChestBlockEntity::new, SHADOW_CHEST_BLOCK).build()
	);

	public static final BlockEntityType<SoulChestBlockEntity> SOUL_CHEST_BLOCK_ENTITY = registerBlockEntity(
			"soul_chest",
			FabricBlockEntityTypeBuilder.create(SoulChestBlockEntity::new, SOUL_CHEST_BLOCK).build()
	);

	public static final BlockEntityType<GamblerChestBlockEntity> GAMBLER_CHEST_BLOCK_ENTITY = registerBlockEntity(
			"gambler_chest",
			FabricBlockEntityTypeBuilder.create(GamblerChestBlockEntity::new, GAMBLER_CHEST_BLOCK).build()
	);

	public static final BlockEntityType<PocketChestBlockEntity> POCKET_CHEST_BLOCK_ENTITY = registerBlockEntity(
			"pocket_chest",
			FabricBlockEntityTypeBuilder.create(PocketChestBlockEntity::new, POCKET_CHEST_BLOCK).build()
	);

	public static final BlockEntityType<FurnaceChestBlockEntity> FURNACE_CHEST_BLOCK_ENTITY = registerBlockEntity(
			"furnace_chest",
			FabricBlockEntityTypeBuilder.create(FurnaceChestBlockEntity::new, FURNACE_CHEST_BLOCK).build()
	);

	public static final BlockEntityType<WoodcutterBlockEntity> WOODCUTTER_BLOCK_ENTITY = registerBlockEntity(
			"wood_cutter",
			FabricBlockEntityTypeBuilder.create(WoodcutterBlockEntity::new, WOODCUTTER_BLOCK).build()
	);

	// ---------------------------------------------------------------
	// Menu (screen handler) types
	// ---------------------------------------------------------------
	// Extended menu types: the row count is not fixed for Shadow/Void Chest,
	// so it must travel from server to client in the screen-opening packet. Each
	// block entity supplies that row count via ExtendedMenuProvider#getScreenOpeningData.

	public static final ExtendedMenuType<VoidChestMenu, Integer> VOID_CHEST_MENU = registerMenu(
			"void_chest",
			new ExtendedMenuType<>(VoidChestMenu::fromNetwork, ByteBufCodecs.VAR_INT.cast())
	);

	public static final ExtendedMenuType<ShadowChestMenu, Integer> SHADOW_CHEST_MENU = registerMenu(
			"shadow_chest",
			new ExtendedMenuType<>(ShadowChestMenu::fromNetwork, ByteBufCodecs.VAR_INT.cast())
	);

	public static final ExtendedMenuType<SoulChestMenu, Integer> SOUL_CHEST_MENU = registerMenu(
			"soul_chest",
			new ExtendedMenuType<>(SoulChestMenu::fromNetwork, ByteBufCodecs.VAR_INT.cast())
	);

	public static final ExtendedMenuType<GamblerChestMenu, Integer> GAMBLER_CHEST_MENU = registerMenu(
			"gambler_chest",
			new ExtendedMenuType<>(GamblerChestMenu::fromNetwork, ByteBufCodecs.VAR_INT.cast())
	);

	public static final ExtendedMenuType<FurnaceChestMenu, Integer> FURNACE_CHEST_MENU = registerMenu(
			"furnace_chest",
			new ExtendedMenuType<>(FurnaceChestMenu::fromNetwork, ByteBufCodecs.VAR_INT.cast())
	);

	public static final ExtendedMenuType<WoodcutterMenu, Integer> WOODCUTTER_MENU = registerMenu(
			"wood_cutter",
			new ExtendedMenuType<>(WoodcutterMenu::fromNetwork, ByteBufCodecs.VAR_INT.cast())
	);

	// ---------------------------------------------------------------
	// Creative mode tab
	// ---------------------------------------------------------------
	// Declared after all items so the field references below are already initialized.

	public static final CreativeModeTab DYNAMIC_CHESTS_TAB = Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath(DynamicChests.MOD_ID, "general"),
			CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
					.title(Component.translatable("itemGroup.dynamicchests"))
					.icon(() -> new ItemStack(VOID_CHEST_ITEM))
					.displayItems((params, output) -> {
						output.accept(SOUL_CHEST_ITEM);
						output.accept(SHADOW_CHEST_ITEM);
						output.accept(VOID_CHEST_ITEM);
						output.accept(GAMBLER_CHEST_ITEM);
						output.accept(WOODCUTTER_ITEM);
						output.accept(FURNACE_CHEST_ITEM);
						output.accept(POCKET_CHEST_ITEM);
						output.accept(RNG_OVERRIDE_CHIP);
					})
					.build()
	);

	// ---------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------

	/**
	 * Builds the block's ResourceKey first, then hands it to the factory so the block's
	 * Properties can call {@code .setId(key)} — required since 1.21.2, or registration
	 * throws at startup.
	 */
	private static <T extends Block> T registerBlock(String path, Function<ResourceKey<Block>, T> factory) {
		Identifier id = Identifier.fromNamespaceAndPath(DynamicChests.MOD_ID, path);
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
		T block = factory.apply(key);
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	private static <T extends Item> T registerItem(String path, Function<ResourceKey<Item>, T> factory) {
		Identifier id = Identifier.fromNamespaceAndPath(DynamicChests.MOD_ID, path);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		T item = factory.apply(key);
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private static <T extends BlockEntityType<?>> T registerBlockEntity(String path, T type) {
		Identifier id = Identifier.fromNamespaceAndPath(DynamicChests.MOD_ID, path);
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, type);
	}

	private static <T extends MenuType<?>> T registerMenu(String path, T type) {
		Identifier id = Identifier.fromNamespaceAndPath(DynamicChests.MOD_ID, path);
		return Registry.register(BuiltInRegistries.MENU, id, type);
	}

	public static void init() {
		GamblerSounds.init();
		// Static initializer trigger — referencing this class loads all fields above.
	}
}
