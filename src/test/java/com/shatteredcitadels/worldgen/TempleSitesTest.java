package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.worldgen.temple.BopTempleSites;
import com.shatteredcitadels.worldgen.temple.TempleSites;
import com.shatteredcitadels.worldgen.temple.VanillaTempleSites;
import net.minecraft.init.Biomes;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TempleSitesTest {

    @BeforeClass
    public static void bootstrap() {
        TestBlocks.install();
    }

    private static List<Biome> list(Biome b) {
        return Collections.singletonList(b);
    }

    @Test
    public void vanillaKinds() {
        TempleSites s = VanillaTempleSites.INSTANCE;
        assertEquals(TempleSites.Kind.DESERT, s.kindOf(Biomes.DESERT));
        assertEquals(TempleSites.Kind.DESERT, s.kindOf(Biomes.DESERT_HILLS));
        assertEquals(TempleSites.Kind.JUNGLE, s.kindOf(Biomes.JUNGLE_HILLS));
        assertEquals(TempleSites.Kind.OTHER, s.kindOf(Biomes.SWAMPLAND));
        assertEquals(TempleSites.Kind.OTHER, s.kindOf(Biomes.COLD_TAIGA));
        assertEquals(TempleSites.Kind.NONE, s.kindOf(Biomes.PLAINS));
    }

    @Test
    public void bopKindsFromLists() {
        TempleSites s = new BopTempleSites(list(Biomes.MESA), list(Biomes.FOREST), list(Biomes.SWAMPLAND),
                list(Biomes.ICE_PLAINS), (world, rand, cx, cz) -> null);
        assertEquals(TempleSites.Kind.DESERT, s.kindOf(Biomes.MESA));
        assertEquals(TempleSites.Kind.JUNGLE, s.kindOf(Biomes.FOREST));
        assertEquals(TempleSites.Kind.OTHER, s.kindOf(Biomes.SWAMPLAND));
        assertEquals(TempleSites.Kind.OTHER, s.kindOf(Biomes.ICE_PLAINS));
        assertEquals(TempleSites.Kind.NONE, s.kindOf(Biomes.PLAINS));
    }

    @Test
    public void bopFromNonBopGeneratorFails() {
        assertNull(BopTempleSites.from(new MapGenScatteredFeature()));
    }
}
