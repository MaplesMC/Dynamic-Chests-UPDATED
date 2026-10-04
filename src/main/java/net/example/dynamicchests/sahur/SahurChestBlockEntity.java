package net.example.dynamicchests.sahur;

import net.example.dynamicchests.block.entity.AbstractVaultChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Sahur Chest: 27 slots like a normal chest, but it BANGS. Opening it plays nine drum hits
 * ("tung" x9) and then the wake-up call ("SAHUR!"); each hit swings the bat on its lid. Before
 * dawn it wakes up by itself when players are close and does the whole routine on its own.
 */
public class SahurChestBlockEntity extends AbstractVaultChestBlockEntity implements MenuProvider {

	public static final int TUNGS = 9;
	private static final int TICKS_BETWEEN_TUNGS = 4;
	private static final int TICKS_BEFORE_SAHUR = 9;
	private static final int BLOCK_EVENT_HIT = 3;
	private static final int WAKE_COOLDOWN_TICKS = 600;
	private static final double HEARING_RANGE = 24.0;
	/** Roughly the hours before sunrise, in ticks of the day. */
	private static final long SAHUR_FROM = 22300;
	private static final long SAHUR_TO = 23600;

	/** -1 = idle, 0..TUNGS-1 = next tung to play, TUNGS = the SAHUR finale. */
	private int step = -1;
	private int timer;
	private long nextWakeTime;

	/** Client side: game time of the most recent hit, for the bat animation. */
	private long lastHitGameTime = Long.MIN_VALUE / 2;

	public SahurChestBlockEntity(BlockPos pos, BlockState state) {
		super(SahurChest.BLOCK_ENTITY, pos, state, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE);
	}

	@Override
	public int getSingleContainerSize() {
		return 27;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.dynamicchests.sahur_chest");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return ChestMenu.threeRows(syncId, inventory, this);
	}

	// ------------------------------------------------------------------
	// Drumming
	// ------------------------------------------------------------------

	public void startDrumming() {
		if (this.step < 0) {
			this.step = 0;
			this.timer = 0;
		}
	}

	public void serverTick(ServerLevel level) {
		if (this.step < 0) {
			maybeWakeUp(level);
			return;
		}
		if (--this.timer > 0) {
			return;
		}
		if (this.step < TUNGS) {
			tung(level, this.step);
			this.step++;
			this.timer = this.step == TUNGS ? TICKS_BEFORE_SAHUR : TICKS_BETWEEN_TUNGS;
		} else {
			sahur(level);
			this.step = -1;
		}
	}

	/** Before sunrise, a chest with company in earshot wakes up and drums on its own. */
	private void maybeWakeUp(ServerLevel level) {
		long gameTime = level.getGameTime();
		if (gameTime % 20 != 0 || gameTime < this.nextWakeTime) {
			return;
		}
		long timeOfDay = Math.floorMod(level.getDefaultClockTime(), 24000L);
		if (timeOfDay < SAHUR_FROM || timeOfDay > SAHUR_TO) {
			return;
		}
		if (!listeners(level).isEmpty()) {
			this.nextWakeTime = gameTime + WAKE_COOLDOWN_TICKS;
			startDrumming();
		}
	}

	private List<ServerPlayer> listeners(ServerLevel level) {
		double x = this.worldPosition.getX() + 0.5;
		double y = this.worldPosition.getY() + 0.5;
		double z = this.worldPosition.getZ() + 0.5;
		return level.players().stream()
				.filter(player -> player.distanceToSqr(x, y, z) <= HEARING_RANGE * HEARING_RANGE)
				.toList();
	}

	/** One "TUNG": deep drum, a little wooden knock, a note, the bat swings, the actionbar grows. */
	private void tung(ServerLevel level, int index) {
		play(level, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.4f, 0.62f + 0.025f * index);
		play(level, SoundEvents.NOTE_BLOCK_BASS.value(), 0.9f, 0.5f + 0.02f * index);
		play(level, SoundEvents.WOOD_HIT, 0.7f, 1.4f - 0.03f * index);
		double x = this.worldPosition.getX() + 0.5;
		double y = this.worldPosition.getY() + 1.6;
		double z = this.worldPosition.getZ() + 0.5;
		level.sendParticles(ParticleTypes.NOTE, x, y, z, 2, 0.35, 0.2, 0.35, 1.0);
		level.sendParticles(ParticleTypes.CRIT, x, y - 0.4, z, 4, 0.3, 0.1, 0.3, 0.05);
		level.blockEvent(this.worldPosition, getBlockState().getBlock(), BLOCK_EVENT_HIT, index);

		MutableComponent line = Component.empty();
		for (int i = 0; i <= index; i++) {
			line.append(Component.translatable("sahur.dynamicchests.tung")).append(" ");
		}
		announce(level, line.withStyle(Style.EMPTY.withColor(0xD9A441).withBold(true)));
	}

	/** The finale: SAHUR! with a bell chord, snare, fireworks and a big gold message. */
	private void sahur(ServerLevel level) {
		play(level, SoundEvents.NOTE_BLOCK_SNARE.value(), 1.6f, 1.0f);
		play(level, SoundEvents.NOTE_BLOCK_BELL.value(), 1.5f, 1.0f);
		play(level, SoundEvents.NOTE_BLOCK_BELL.value(), 1.2f, 1.5f);
		play(level, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 2.0f);
		play(level, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.8f, 0.5f);
		double x = this.worldPosition.getX() + 0.5;
		double y = this.worldPosition.getY() + 1.7;
		double z = this.worldPosition.getZ() + 0.5;
		level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 60, 0.5, 0.7, 0.5, 0.2);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 40, 0.5, 0.6, 0.5, 0.4);
		level.sendParticles(ParticleTypes.NOTE, x, y, z, 12, 0.6, 0.4, 0.6, 1.0);
		level.blockEvent(this.worldPosition, getBlockState().getBlock(), BLOCK_EVENT_HIT, TUNGS);
		announce(level, Component.translatable("sahur.dynamicchests.sahur")
				.withStyle(Style.EMPTY.withColor(0xFFD84A).withBold(true)));
	}

	private void play(ServerLevel level, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, this.worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
	}

	private void announce(ServerLevel level, Component message) {
		for (ServerPlayer player : listeners(level)) {
			player.sendSystemMessage(message, true);
		}
	}

	// ------------------------------------------------------------------
	// Client: bat swing timing
	// ------------------------------------------------------------------

	@Override
	public boolean triggerEvent(int id, int type) {
		if (id == BLOCK_EVENT_HIT) {
			if (this.level != null) {
				this.lastHitGameTime = this.level.getGameTime();
			}
			return true;
		}
		return super.triggerEvent(id, type);
	}

	/** Ticks since the last hit (with partial tick), large when the chest has been quiet for a while. */
	public float getHitAge(float partialTick) {
		if (this.level == null) {
			return 1000.0f;
		}
		return Math.min(1000.0f, this.level.getGameTime() + partialTick - this.lastHitGameTime);
	}
}
