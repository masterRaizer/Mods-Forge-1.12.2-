package com.shatteredcitadels.worldgen.hut;

import com.shatteredcitadels.Reference;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.CitadelWorldSavedData;
import com.shatteredcitadels.structure.StructureType;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

/**
 * Keeps the witch spawning of the swamp hut: inside the cabin box of our hut the only monster that
 * can spawn is the witch, as in the vanilla hut. Bounded by the saved record, no spawner involved.
 */
@Mod.EventBusSubscriber(modid = Reference.MODID)
public final class WitchHutSpawns {

    /** One instance: the spawner compares the entries of the two lists it asks for by identity. */
    private static final Biome.SpawnListEntry WITCH = new Biome.SpawnListEntry(EntityWitch.class, 1, 1, 1);

    private WitchHutSpawns() {
    }

    @SubscribeEvent
    public static void onPotentialSpawns(WorldEvent.PotentialSpawns event) {
        World world = event.getWorld();
        if (world == null || world.isRemote || event.getType() != EnumCreatureType.MONSTER) {
            return;
        }
        if (isHut(CitadelWorldSavedData.get(world).getCitadelAt(event.getPos()), event.getPos())) {
            List<Biome.SpawnListEntry> list = event.getList();
            list.clear();
            list.add(WITCH);
        }
    }

    /** @return true if {@code pos} lies in the cabin of a witch hut record. */
    public static boolean isHut(CitadelInstance record, BlockPos pos) {
        return record != null && record.getType() == StructureType.WITCH_HUT && record.isInPart(pos);
    }
}
