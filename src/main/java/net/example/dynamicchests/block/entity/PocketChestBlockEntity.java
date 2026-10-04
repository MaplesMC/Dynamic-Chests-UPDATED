package net.example.dynamicchests.block.entity;

import net.example.dynamicchests.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Block entity of the Pocket-Dimension Chest. It holds no items, only who placed it: the chest leads
 * to that player's pocket. Whoever places a chest becomes its owner.
 */
public class PocketChestBlockEntity extends AbstractVaultChestBlockEntity {

	@Nullable
	private UUID ownerId;
	private String ownerName = "";

	public PocketChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModRegistry.POCKET_CHEST_BLOCK_ENTITY, pos, state, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE);
	}

	@Override
	public int getSingleContainerSize() {
		return 0;
	}

	@Nullable
	public UUID getOwnerId() {
		return this.ownerId;
	}

	public String getOwnerName() {
		return this.ownerName;
	}

	public void setOwner(UUID id, String name) {
		this.ownerId = id;
		this.ownerName = name;
		setChanged();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (this.ownerId != null) {
			output.putString("owner", this.ownerId.toString());
			output.putString("owner_name", this.ownerName);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.ownerId = null;
		String text = input.getStringOr("owner", "");
		if (!text.isEmpty()) {
			try {
				this.ownerId = UUID.fromString(text);
			} catch (IllegalArgumentException ignored) {
				// unreadable owner: treated as unowned
			}
		}
		this.ownerName = input.getStringOr("owner_name", "");
	}
}
