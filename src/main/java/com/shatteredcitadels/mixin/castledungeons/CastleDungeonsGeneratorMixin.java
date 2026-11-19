package com.shatteredcitadels.mixin.castledungeons;

import com.shatteredcitadels.compat.castledungeons.CastleDungeonsIntegration;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.puffish.castle.ForgeGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/** Provides the world and chunk of the current Castle Dungeons generation to our hooks. */
@Mixin(value = ForgeGenerator.class, remap = false)
public abstract class CastleDungeonsGeneratorMixin {

    private static final String GENERATE = "generate(Ljava/util/Random;IILnet/minecraft/world/World;"
            + "Lnet/minecraft/world/gen/IChunkGenerator;Lnet/minecraft/world/chunk/IChunkProvider;)V";

    @Inject(method = GENERATE, at = @At("HEAD"))
    private void shatteredcitadels$begin(Random random, int chunkX, int chunkZ, World world, IChunkGenerator generator,
                                         IChunkProvider provider, CallbackInfo ci) {
        CastleDungeonsIntegration.beginGenerate(world, chunkX, chunkZ);
    }

    @Inject(method = GENERATE, at = @At("RETURN"))
    private void shatteredcitadels$end(Random random, int chunkX, int chunkZ, World world, IChunkGenerator generator,
                                       IChunkProvider provider, CallbackInfo ci) {
        CastleDungeonsIntegration.endGenerate();
    }
}
