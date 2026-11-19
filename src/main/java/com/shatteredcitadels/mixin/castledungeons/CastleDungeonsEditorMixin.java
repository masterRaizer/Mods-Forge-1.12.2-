package com.shatteredcitadels.mixin.castledungeons;

import com.shatteredcitadels.compat.castledungeons.CastleDungeonsIntegration;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.puffish.castle.ForgeWorldEditor;
import net.puffish.castle.builder.Coord;
import net.puffish.castle.builder.MonsterType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/**
 * Replaces Castle Dungeons' vanilla spawners (which include creepers and witches) with citadel
 * wave spawners. If our placer fails, the original Castle Dungeons spawner is placed.
 */
@Mixin(value = ForgeWorldEditor.class, remap = false)
public abstract class CastleDungeonsEditorMixin {

    @Shadow
    private World world;

    @Shadow
    private Random random;

    @Inject(method = "setSpawner", at = @At("HEAD"), cancellable = true)
    private void shatteredcitadels$replaceSpawner(Coord coord, MonsterType type, CallbackInfo ci) {
        if (coord == null) {
            return;
        }
        int difficulty = type == null ? 0 : type.ordinal();
        BlockPos pos = new BlockPos(coord.getX(), coord.getY(), coord.getZ());
        if (CastleDungeonsIntegration.replaceSpawner(world, pos, difficulty, random)) {
            ci.cancel();
        }
    }
}
