package net.example.dynamicchests.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;

/**
 * An animated entity texture that cross-fades between frames the way vanilla's {@code "interpolate": true}
 * block animations do (a blast furnace's lit front uses {@code frametime 10}). Entity textures are not part of
 * the block atlas, so a {@code .mcmeta} file cannot animate them; this keeps one dynamic texture and rewrites
 * its pixels once per game tick.
 */
public final class BlendedFrameTexture {

	private final Identifier[] frames;
	private final Identifier id;
	private final int frameTicks;

	private NativeImage[] images;
	private DynamicTexture texture;
	private long lastStep = Long.MIN_VALUE;

	public BlendedFrameTexture(Identifier id, int frameTicks, Identifier... frames) {
		this.id = id;
		this.frameTicks = Math.max(1, frameTicks);
		this.frames = frames;
	}

	/** The texture to draw with at the given game time, refreshed if a new tick has started. */
	public Identifier at(long gameTime) {
		if (this.texture == null && !load()) {
			return this.frames[0];
		}
		if (gameTime != this.lastStep) {
			this.lastStep = gameTime;
			int count = this.images.length;
			int frame = (int) ((gameTime / this.frameTicks) % count);
			NativeImage from = this.images[frame];
			NativeImage to = this.images[(frame + 1) % count];
			int mix = (int) (gameTime % this.frameTicks); // 0..frameTicks-1, so the fade completes at the next frame
			NativeImage out = this.texture.getPixels();
			for (int y = 0; y < out.getHeight(); y++) {
				for (int x = 0; x < out.getWidth(); x++) {
					out.setPixel(x, y, blend(from.getPixel(x, y), to.getPixel(x, y), mix, this.frameTicks));
				}
			}
			this.texture.upload();
		}
		return this.id;
	}

	private boolean load() {
		try {
			Minecraft client = Minecraft.getInstance();
			NativeImage[] loaded = new NativeImage[this.frames.length];
			for (int i = 0; i < loaded.length; i++) {
				try (InputStream in = client.getResourceManager().getResourceOrThrow(this.frames[i]).open()) {
					loaded[i] = NativeImage.read(in);
				}
			}
			this.images = loaded;
			this.texture = new DynamicTexture(() -> this.id.toString(), loaded[0].getWidth(), loaded[0].getHeight(), false);
			client.getTextureManager().register(this.id, this.texture);
			this.lastStep = Long.MIN_VALUE;
			return true;
		} catch (IOException | RuntimeException e) {
			return false;
		}
	}

	/** Per-channel linear mix of two packed pixels. */
	private static int blend(int a, int b, int step, int steps) {
		int result = 0;
		for (int shift = 0; shift < 32; shift += 8) {
			int ca = (a >>> shift) & 0xFF;
			int cb = (b >>> shift) & 0xFF;
			result |= ((ca * (steps - step) + cb * step) / steps & 0xFF) << shift;
		}
		return result;
	}
}
