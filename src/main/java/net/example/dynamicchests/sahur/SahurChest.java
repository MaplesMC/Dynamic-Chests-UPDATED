package net.example.dynamicchests.sahur;

import net.example.dynamicchests.DynamicChests;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * THE TUNG TUNG TUNG SAHUR CHEST (novelty chest; a Creaking drops it 50% of the time).
 *
 * <p>Self-contained on purpose. To REMOVE it completely:
 * <ol>
 *   <li>delete the package {@code net.example.dynamicchests.sahur} in BOTH src/main/java and src/client/java;</li>
 *   <li>delete the two lines marked {@code SAHUR-CHEST} in {@code DynamicChests.java} and
 *       {@code DynamicChestsClient.java};</li>
 *   <li>delete every file whose name contains {@code sahur_chest} under src/main/resources
 *       (blockstate, item definition, block model, entity texture, block loot table);</li>
 *   <li>delete the lines containing {@code sahur} from {@code lang/en_us.json}.</li>
 * </ol>
 * Nothing else in the mod references this package.
 */
public final class SahurChest {

	private SahurChest() {
	}

	public static final Block BLOCK = registerBlock();
	public static final Item ITEM = registerItem();
	public static final BlockEntityType<SahurChestBlockEntity> BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Identifier.fromNamespaceAndPath(DynamicChests.MOD_ID, "sahur_chest"),
			FabricBlockEntityTypeBuilder.create(SahurChestBlockEntity::new, BLOCK).build());

	private static Block registerBlock() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(DynamicChests.MOD_ID, "sahur_chest"));
		return Registry.register(BuiltInRegistries.BLOCK, key, new SahurChestBlock(BlockBehaviour.Properties.of()
				.setId(key)
				.mapColor(MapColor.WOOD)
				// Breakable like a normal chest (it now drops from the Creaking, so survival players can use it).
				.strength(2.5f)
				.sound(SoundType.WOOD)
				.noOcclusion()));
	}

	private static Item registerItem() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(DynamicChests.MOD_ID, "sahur_chest"));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new BlockItem(BLOCK, new Item.Properties().setId(key).useBlockDescriptionPrefix().rarity(Rarity.EPIC)));
	}

	/** Chance that a Creaking drops the chest when it dies. */
	private static final float CREAKING_DROP_CHANCE = 0.5f;
	private static final Identifier CREAKING_LOOT_TABLE = Identifier.withDefaultNamespace("entities/creaking");

	/** Called once from the mod initializer. */
	public static void init() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (key.identifier().equals(CREAKING_LOOT_TABLE)) {
				tableBuilder.withPool(LootPool.lootPool()
						.setRolls(ConstantValue.exactly(1.0f))
						.when(LootItemRandomChanceCondition.randomChance(CREAKING_DROP_CHANCE))
						.add(LootItem.lootTableItem(ITEM)));
			}
		});
		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
				Identifier.fromNamespaceAndPath(DynamicChests.MOD_ID, "general"));
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> output.accept(ITEM));
	}
}
