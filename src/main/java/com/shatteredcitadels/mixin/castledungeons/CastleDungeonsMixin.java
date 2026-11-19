package com.shatteredcitadels.mixin.castledungeons;

import com.shatteredcitadels.compat.castledungeons.CastleDungeonsIntegration;
import net.puffish.castle.builder.Generator;
import net.puffish.castle.builder.WorldEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

/**
 * Castle Dungeons placement: applies our section size (rarer castles) and the minimum distance
 * to every tracked citadel/castle on top of Castle Dungeons' own one-chunk-per-section pick.
 */
@Mixin(value = Generator.class, remap = false)
public abstract class CastleDungeonsMixin {

    @Shadow
    private int SECTION_SIZE;

    @Unique
    private int shatteredcitadels$originalSectionSize;

    @Inject(method = "setConfig", at = @At("RETURN"))
    private void shatteredcitadels$applySectionSize(int maxY, int minY, int maxFoundation, int sectionSize,
                                                    int maxCastleSize, int minCastleSize, CallbackInfo ci) {
        shatteredcitadels$originalSectionSize = sectionSize;
        SECTION_SIZE = Math.max(1, CastleDungeonsIntegration.sectionSize(sectionSize));
    }

    @Inject(method = "generate(Ljava/util/Random;IIJLnet/puffish/castle/builder/WorldEditor;)V", at = @At("HEAD"))
    private void shatteredcitadels$refreshSectionSize(Random random, int chunkX, int chunkZ, long seed,
                                                      WorldEditor editor, CallbackInfo ci) {
        // Picks up in-game config edits; the original value is kept for when the override is off.
        if (shatteredcitadels$originalSectionSize > 0) {
            SECTION_SIZE = Math.max(1, CastleDungeonsIntegration.sectionSize(shatteredcitadels$originalSectionSize));
        }
    }

    @Inject(method = "checkChunk", at = @At("RETURN"), cancellable = true)
    private void shatteredcitadels$enforceDistance(long seed, int chunkX, int chunkZ, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !CastleDungeonsIntegration.allowCastle(chunkX, chunkZ)) {
            cir.setReturnValue(false);
        }
    }
}
