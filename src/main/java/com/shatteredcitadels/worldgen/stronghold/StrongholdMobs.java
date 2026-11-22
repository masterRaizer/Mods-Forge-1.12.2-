package com.shatteredcitadels.worldgen.stronghold;

import com.shatteredcitadels.config.ConfigCache;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Mobs of the stronghold's spawners: the config list, filtered to registered entities; the defaults otherwise. */
public final class StrongholdMobs {

    private StrongholdMobs() {
    }

    public static ResourceLocation[] configured() {
        List<ResourceLocation> mobs = new ArrayList<>();
        for (String name : ConfigCache.get().getStrongholdSpawnerMobs()) {
            ResourceLocation id = new ResourceLocation(name.trim());
            if (ForgeRegistries.ENTITIES.containsKey(id)) {
                mobs.add(id);
            }
        }
        return mobs.isEmpty() ? StrongholdBuilder.DEFAULT_MOBS : mobs.toArray(new ResourceLocation[0]);
    }
}
