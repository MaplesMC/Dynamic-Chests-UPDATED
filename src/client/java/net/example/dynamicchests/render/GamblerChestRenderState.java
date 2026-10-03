package net.example.dynamicchests.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/** Per-frame snapshot for the Gambler's Chest: lid angle, facing and which glow state to draw. */
public class GamblerChestRenderState extends BlockEntityRenderState {

	public float lidAngle;
	public float facingYaw;
	/** One of the {@code VISUAL_*} constants of the block entity. */
	public int visualState;
	/** Game time in ticks plus partial tick, drives the pulsing glow. */
	public float animationTime;
}
