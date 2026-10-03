package net.example.dynamicchests.gambler.effects;

import net.example.dynamicchests.gambler.logic.GambleOutcome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/** Server-driven particle bursts that make each outcome look different. */
public final class GamblerParticles {

	private GamblerParticles() {
	}

	/** Small sparkle while the chest is rolling. */
	public static void rolling(ServerLevel level, BlockPos pos) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.0;
		double z = pos.getZ() + 0.5;
		level.sendParticles(ParticleTypes.GLOW, x, y, z, 2, 0.25, 0.1, 0.25, 0.01);
	}

	public static void outcome(ServerLevel level, BlockPos pos, GambleOutcome outcome) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.0;
		double z = pos.getZ() + 0.5;
		switch (outcome) {
			case JACKPOT -> {
				level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 90, 0.5, 0.6, 0.5, 0.45);
				level.sendParticles(ParticleTypes.FIREWORK, x, y + 0.3, z, 70, 0.4, 0.8, 0.4, 0.25);
				level.sendParticles(ParticleTypes.GLOW, x, y, z, 30, 0.6, 0.4, 0.6, 0.05);
			}
			case TRIPLE -> {
				level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.2, z, 70, 0.5, 0.5, 0.5, 0.7);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 20, 0.4, 0.3, 0.4, 0.0);
				level.sendParticles(ParticleTypes.FIREWORK, x, y + 0.2, z, 20, 0.3, 0.5, 0.3, 0.12);
			}
			case DOUBLE, UPGRADE -> {
				level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.2, z, 45, 0.5, 0.5, 0.5, 0.6);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 14, 0.4, 0.3, 0.4, 0.0);
			}
			case RETURN -> level.sendParticles(ParticleTypes.CRIT, x, y, z, 10, 0.3, 0.2, 0.3, 0.05);
			case LOSS -> {
				level.sendParticles(ParticleTypes.SMOKE, x, y, z, 25, 0.35, 0.2, 0.35, 0.03);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 8, 0.3, 0.2, 0.3, 0.02);
				level.sendParticles(ParticleTypes.ASH, x, y + 0.2, z, 40, 0.5, 0.4, 0.5, 0.02);
			}
		}
	}
}
