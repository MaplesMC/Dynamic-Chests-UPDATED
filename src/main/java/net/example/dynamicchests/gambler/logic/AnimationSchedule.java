package net.example.dynamicchests.gambler.logic;

/**
 * Timing of the roll animation, shared by server (rolling sounds) and client (fake results) so
 * both tick over in step. The roll starts fast and slows down towards the reveal.
 */
public final class AnimationSchedule {

	private AnimationSchedule() {
	}

	private static final int FAST_INTERVAL = 2;
	private static final int SLOW_EXTRA = 10;

	/** Ticks between two fake results at the given moment of the animation. */
	public static int interval(int elapsed, int total) {
		float progress = Math.min(1.0f, Math.max(0.0f, elapsed / (float) Math.max(1, total)));
		return FAST_INTERVAL + Math.round(progress * progress * SLOW_EXTRA);
	}

	/** True when a new fake result is shown on exactly this tick. */
	public static boolean isStepTick(int elapsed, int total) {
		int t = 0;
		while (t <= elapsed && t < total) {
			if (t == elapsed) {
				return true;
			}
			t += interval(t, total);
		}
		return false;
	}

	/** How many fake results have been shown up to and including this tick. */
	public static int stepIndex(int elapsed, int total) {
		int steps = 0;
		int t = 0;
		while (t <= elapsed && t < total) {
			steps++;
			t += interval(t, total);
		}
		return steps;
	}
}
