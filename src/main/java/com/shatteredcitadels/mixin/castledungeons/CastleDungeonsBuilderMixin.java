package com.shatteredcitadels.mixin.castledungeons;

import com.shatteredcitadels.compat.castledungeons.CastleDungeonsIntegration;
import net.puffish.castle.builder.Builder;
import net.puffish.castle.builder.Coord;
import net.puffish.castle.generator.Castle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/** Registers every Castle Dungeons castle in the floor tracker right before it is built. */
@Mixin(value = Builder.class, remap = false)
public abstract class CastleDungeonsBuilderMixin {

    @Inject(method = "buildCastle", at = @At("HEAD"))
    private void shatteredcitadels$track(Coord position, Castle castle, Random random, CallbackInfo ci) {
        if (position != null && castle != null && castle.getLayers() != null) {
            CastleDungeonsIntegration.onCastleStart(position.getX(), position.getY(), position.getZ(),
                    castle.getWidth(), castle.getHeight(), castle.getLayers().size());
        }
    }
}
