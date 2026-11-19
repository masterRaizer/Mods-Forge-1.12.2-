package com.shatteredcitadels.mob;

import net.minecraft.entity.Entity;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.EntityEntry;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MobClassificationTest {

    @BeforeClass
    public static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    public void scoreAndTiers() {
        assertEquals(1, MobPower.tierFor(MobPower.score(20, 2, 0)));
        assertEquals(2, MobPower.tierFor(MobPower.score(20, 3, 2)));
        assertEquals(3, MobPower.tierFor(MobPower.score(40, 4, 4)));
        assertEquals(4, MobPower.tierFor(MobPower.score(60, 8, 6)));
        assertEquals(5, MobPower.tierFor(MobPower.score(300, 20, 20)));
        assertEquals(1, MobPower.tierFor(MobPower.score(Double.NaN, Double.POSITIVE_INFINITY, -5)));
    }

    @Test
    public void undergroundKeywords() {
        assertTrue(MobPower.looksUnderground("cave_goblin"));
        assertTrue(MobPower.looksUnderground("Kobold_Warrior"));
        assertTrue(MobPower.looksUnderground("forest_troll"));
        assertFalse(MobPower.looksUnderground("sky_knight"));
        assertFalse(MobPower.looksUnderground(null));
    }

    @Test
    public void scannerSurvivesThrowingAndNullFactories() {
        EntityEntry throwsRuntime = entry("broken_runtime", new IllegalStateException("constructor boom"));
        EntityEntry throwsLinkage = entry("broken_linkage", new NoClassDefFoundError("missing/Class"));
        EntityEntry returnsNull = entry("returns_null", null);
        assertNull(ModMobTierScanner.classify(throwsRuntime, null, -1, false));
        assertNull(ModMobTierScanner.classify(throwsLinkage, null, -1, false));
        assertNull(ModMobTierScanner.classify(returnsNull, null, -1, false));
        assertNull(ModMobTierScanner.classify(returnsNull, null, 3, true));
    }

    @Test
    public void comboIsDistinctSizedAndNeverEmpty() {
        Map<Integer, List<MobEntry>> tiers = new HashMap<>();
        for (int t = 1; t <= 5; t++) {
            List<MobEntry> list = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                list.add(new MobEntry(new ResourceLocation("testmod", "t" + t + "_" + i), t, t * 20, false, false, false));
            }
            tiers.put(t, list);
        }
        List<MobEntry> underground = Arrays.asList(
                new MobEntry(new ResourceLocation("testmod", "cave_goblin"), 3, 50, true, false, false),
                new MobEntry(new ResourceLocation("testmod", "kobold"), 2, 40, true, false, false));
        MobTierRegistry registry = new MobTierRegistry(tiers, underground, true);
        Random random = new Random(5);
        for (int i = 0; i < 200; i++) {
            int count = 2 + random.nextInt(3);
            List<MobEntry> combo = registry.pickCombo(random, 1 + random.nextInt(5), random.nextBoolean(), count);
            assertEquals(count, combo.size());
            Set<ResourceLocation> ids = new HashSet<>();
            for (MobEntry entry : combo) {
                assertTrue(ids.add(entry.getId()));
            }
        }
        MobTierRegistry empty = new MobTierRegistry(new HashMap<Integer, List<MobEntry>>(), new ArrayList<MobEntry>(), false);
        List<MobEntry> fallback = empty.pickCombo(random, 4, true, 3);
        assertEquals(1, fallback.size());
        assertEquals(new ResourceLocation("minecraft", "zombie"), fallback.get(0).getId());
    }

    private static EntityEntry entry(String name, final Throwable failure) {
        EntityEntry entry = new EntityEntry(Entity.class, name) {
            @Override
            public Entity newInstance(World world) {
                if (failure instanceof RuntimeException) {
                    throw (RuntimeException) failure;
                }
                if (failure instanceof Error) {
                    throw (Error) failure;
                }
                return null;
            }
        };
        entry.setRegistryName(new ResourceLocation("testmod", name));
        return entry;
    }
}
