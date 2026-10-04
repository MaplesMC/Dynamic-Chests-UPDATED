package net.example.dynamicchests.mixin;

import com.mojang.serialization.Lifecycle;
import net.example.dynamicchests.DynamicChests;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla marks every dimension other than the overworld, nether and end as "experimental", which
 * makes world creation show an "experimental settings" warning. The pocket dimension is a normal,
 * supported part of this mod, so its own dimensions are reported as stable.
 */
@Mixin(WorldDimensions.class)
public abstract class WorldDimensionsMixin {

	@Inject(method = "checkStability", at = @At("HEAD"), cancellable = true)
	private static void dynamicchests$treatOwnDimensionsAsStable(ResourceKey<LevelStem> key, LevelStem stem,
			CallbackInfoReturnable<Lifecycle> cir) {
		if (DynamicChests.MOD_ID.equals(key.identifier().getNamespace())) {
			cir.setReturnValue(Lifecycle.stable());
		}
	}
}
