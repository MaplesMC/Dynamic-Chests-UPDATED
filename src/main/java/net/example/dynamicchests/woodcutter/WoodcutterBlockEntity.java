package net.example.dynamicchests.woodcutter;

import net.example.dynamicchests.block.entity.AbstractVaultChestBlockEntity;
import net.example.dynamicchests.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity of the Woodcutter. It holds no items; it only carries the chest lid animation (and the open and
 * close sounds), which the shared chest base class already does: the lid opens while somebody has the Woodcutter's
 * menu open, and that is announced to every nearby player.
 */
public class WoodcutterBlockEntity extends AbstractVaultChestBlockEntity {

	public WoodcutterBlockEntity(BlockPos pos, BlockState state) {
		super(ModRegistry.WOODCUTTER_BLOCK_ENTITY, pos, state, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE);
	}

	@Override
	public int getSingleContainerSize() {
		return 0;
	}
}
