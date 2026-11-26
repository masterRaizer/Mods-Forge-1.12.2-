package com.shatteredcitadels.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.worldgen.hut.CellarBuilder;
import com.shatteredcitadels.worldgen.hut.CellarLayout;
import com.shatteredcitadels.worldgen.hut.HutVariant;
import com.shatteredcitadels.worldgen.hut.WitchHutPlan;
import com.shatteredcitadels.worldgen.hut.WitchHutSpawns;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class WitchHutTest {

    private static final int SURFACE = 62;

    @BeforeClass
    public static void blocks() {
        TestBlocks.install();
    }

    private static CellarLayout layout(long seed) {
        return CellarLayout.generate(seed, 300, WitchHutPlan.cellarFloor(SURFACE), -200);
    }

    @Test
    public void layoutIsDeterministic() {
        for (long seed = 0; seed < 40; seed++) {
            CellarLayout a = layout(seed);
            CellarLayout b = layout(seed);
            assertEquals(a.rooms.size(), b.rooms.size());
            assertEquals(a.links.size(), b.links.size());
            for (int i = 0; i < a.rooms.size(); i++) {
                assertEquals(a.rooms.get(i).box().toString(), b.rooms.get(i).box().toString());
                assertEquals(a.rooms.get(i).kind(), b.rooms.get(i).kind());
            }
            for (int i = 0; i < a.links.size(); i++) {
                assertEquals(a.links.get(i).box().toString(), b.links.get(i).box().toString());
                assertEquals(a.links.get(i).trapped(), b.links.get(i).trapped());
            }
        }
    }

    @Test
    public void roomCountInRangeAndAllSizesOccur() {
        Set<Integer> sizes = new HashSet<>();
        for (long seed = 0; seed < 300; seed++) {
            int rooms = layout(seed).rooms.size();
            assertTrue(rooms >= CellarLayout.MIN_ROOMS && rooms <= CellarLayout.MAX_ROOMS);
            sizes.add(rooms);
        }
        assertEquals(CellarLayout.MAX_ROOMS - CellarLayout.MIN_ROOMS + 1, sizes.size());
    }

    @Test
    public void roomsDoNotOverlapAndLinksFormATree() {
        for (long seed = 0; seed < 200; seed++) {
            CellarLayout layout = layout(seed);
            for (CellarLayout.Room a : layout.rooms) {
                for (CellarLayout.Room b : layout.rooms) {
                    if (a != b) {
                        assertFalse("seed " + seed, a.box().intersectsWith(b.box()));
                    }
                }
            }
            assertEquals(layout.rooms.size() - 1, layout.links.size());
            Set<CellarLayout.Room> reached = new HashSet<>();
            reached.add(layout.entry());
            boolean trapped = false;
            for (CellarLayout.Link link : layout.links) {
                assertTrue("seed " + seed, reached.contains(link.from));
                assertTrue("seed " + seed, reached.add(link.to));
                trapped |= link.trapped();
            }
            assertEquals(layout.rooms.size(), reached.size());
            assertTrue("seed " + seed + " has no trap", trapped);
            assertSame(CellarLayout.RoomKind.ENTRY, layout.entry().kind());
            assertSame(CellarLayout.RoomKind.RITUAL, layout.ritual().kind());
            assertTrue(layout.ritual() != layout.entry());
        }
    }

    @Test
    public void cellarLiesUnderTheHutInsideTheFootprint() {
        for (long seed = 0; seed < 200; seed++) {
            WitchHutPlan plan = new WitchHutPlan(300, SURFACE, -200, seed, 0, HutVariant.SMALL, true, 3);
            StructureBoundingBox foot = plan.footprint();
            StructureBoundingBox bounds = layout(seed).bounds();
            assertTrue("seed " + seed, bounds.minX >= foot.minX && bounds.maxX <= foot.maxX
                    && bounds.minZ >= foot.minZ && bounds.maxZ <= foot.maxZ && bounds.minY >= foot.minY);
            // Solid ground stays between the cellar's ceiling and the water.
            assertTrue("seed " + seed, bounds.maxY <= SURFACE - 3);
            assertTrue(bounds.maxY < WitchHutPlan.hutFloor(SURFACE));
        }
    }

    @Test
    public void everyBlockStaysInTheFootprint() {
        for (HutVariant variant : HutVariant.values()) {
            for (long seed = 0; seed < 40; seed++) {
                WitchHutPlan plan = new WitchHutPlan(300, SURFACE, -200, seed, 0, variant, seed % 3 != 0, 4);
                BlockWriter w = new BlockWriter(new Blueprint());
                CitadelInstance record = plan.build(w);
                StructureBoundingBox foot = plan.footprint();
                int inside = 0;
                for (int x = foot.minX; x <= foot.maxX; x++) {
                    for (int z = foot.minZ; z <= foot.maxZ; z++) {
                        for (int y = foot.minY; y <= foot.maxY; y++) {
                            if (w.isPlanned(x, y, z)) {
                                inside++;
                            }
                        }
                    }
                }
                assertEquals(variant + " seed " + seed + " outside the footprint", w.getWritten(), inside);
                assertSame(StructureType.WITCH_HUT, record.getType());
                assertEquals(1, record.getParts().size());
                StructureBoundingBox cabin = record.getParts().get(0);
                assertTrue(cabin.minY >= WitchHutPlan.hutFloor(SURFACE));
                BlockPos inCabin = new BlockPos((cabin.minX + cabin.maxX) / 2, cabin.minY + 1, (cabin.minZ + cabin.maxZ) / 2);
                assertTrue(WitchHutSpawns.isHut(record, inCabin));
                assertFalse(WitchHutSpawns.isHut(record, new BlockPos(300, WitchHutPlan.cellarFloor(SURFACE) + 1, -200)));
                assertFalse(WitchHutSpawns.isHut(null, inCabin));
                assertTrue(record.isUnprotected());
            }
        }
    }

    @Test
    public void hutHasAChestACauldronAndAHatchOverTheLadder() {
        for (HutVariant variant : HutVariant.values()) {
            for (long seed = 0; seed < 20; seed++) {
                WitchHutPlan plan = new WitchHutPlan(300, SURFACE, -200, seed, 0, variant, true, 4);
                BlockWriter w = new BlockWriter(new Blueprint());
                CitadelInstance record = plan.build(w);
                int floor = WitchHutPlan.hutFloor(SURFACE);
                assertSame(Blocks.TRAPDOOR, w.get(300, floor, -200).getBlock());
                for (int y = WitchHutPlan.cellarFloor(SURFACE) + 1; y < floor; y++) {
                    assertSame(variant + " seed " + seed + " ladder at " + y, Blocks.LADDER, w.get(300, y, -200).getBlock());
                }
                boolean cauldron = false;
                StructureBoundingBox cabin = record.getParts().get(0);
                for (int x = cabin.minX; x <= cabin.maxX; x++) {
                    for (int z = cabin.minZ; z <= cabin.maxZ; z++) {
                        cauldron |= w.get(x, floor + 1, z).getBlock() == Blocks.CAULDRON;
                    }
                }
                assertTrue(cauldron);
                boolean hutChest = false;
                for (BlockPos chest : record.getChestPoints()) {
                    assertSame(Blocks.CHEST, w.get(chest.getX(), chest.getY(), chest.getZ()).getBlock());
                    assertFalse("chest blocked", w.isSolid(chest.getX(), chest.getY() + 1, chest.getZ()));
                    hutChest |= chest.getY() > floor;
                }
                assertTrue(hutChest);
            }
        }
    }

    @Test
    public void hutWithoutCellarHasNoHatchAndNothingUnderground() {
        WitchHutPlan plan = new WitchHutPlan(300, SURFACE, -200, 5, 0, HutVariant.TALL, false, 4);
        BlockWriter w = new BlockWriter(new Blueprint());
        plan.build(w);
        int floor = WitchHutPlan.hutFloor(SURFACE);
        assertTrue(w.get(300, floor, -200).getBlock() != Blocks.TRAPDOOR);
        assertFalse(w.isPlanned(300, WitchHutPlan.cellarFloor(SURFACE) + 1, -200));
    }

    @Test
    public void cellarHasTrapsWitchesAndAFinalChest() {
        for (long seed = 0; seed < 60; seed++) {
            CellarLayout layout = layout(seed);
            BlockWriter w = new BlockWriter(new Blueprint());
            CitadelInstance record = new CitadelInstance(new UUID(seed, 1), "t", StructureType.WITCH_HUT,
                    CitadelArchetype.KEEPERS_CITADEL, 0, new BlockPos(300, SURFACE, -200),
                    new WitchHutPlan(300, SURFACE, -200, seed, 0, HutVariant.SMALL, true, 0).footprint());
            CellarBuilder cellar = new CellarBuilder(w, record, new Random(seed), layout, seed);
            int asked = (int) (seed % 7);
            cellar.build(WitchHutPlan.hutFloor(SURFACE), asked);
            assertTrue("seed " + seed, cellar.traps() >= 1);
            assertEquals("seed " + seed, asked, cellar.witches());
            BlockPos chest = cellar.finalChest();
            assertNotNull(chest);
            assertTrue(layout.ritual().box().isVecInside(chest));
            assertSame(Blocks.CHEST, w.get(chest.getX(), chest.getY(), chest.getZ()).getBlock());
            assertFalse(w.isSolid(chest.getX(), chest.getY() + 1, chest.getZ()));
            int dispensers = 0;
            int plates = 0;
            StructureBoundingBox b = layout.bounds();
            for (int x = b.minX - 1; x <= b.maxX + 1; x++) {
                for (int z = b.minZ - 1; z <= b.maxZ + 1; z++) {
                    for (int y = b.minY; y <= b.maxY; y++) {
                        if (w.get(x, y, z).getBlock() == Blocks.DISPENSER) {
                            dispensers++;
                        } else if (w.get(x, y, z).getBlock() == Blocks.STONE_PRESSURE_PLATE) {
                            plates++;
                        }
                    }
                }
            }
            assertEquals(cellar.traps(), dispensers);
            assertEquals(cellar.traps(), plates);
            // Every room and corridor is walkable: air at head height in the middle.
            for (CellarLayout.Room room : layout.rooms) {
                assertFalse(w.isSolid(room.centreX, room.floorY + 2, room.centreZ));
                assertTrue(w.isSolid(room.centreX, room.floorY, room.centreZ));
                assertTrue(w.isSolid(room.centreX + room.half, room.floorY + CellarLayout.ROOM_HEIGHT, room.centreZ + room.half));
            }
            for (CellarLayout.Link link : layout.links) {
                for (int i = link.start(); i <= link.end(); i++) {
                    int x = link.alongX() ? i : link.line();
                    int z = link.alongX() ? link.line() : i;
                    assertFalse("seed " + seed, w.isSolid(x, link.from.floorY + 2, z));
                    assertTrue("seed " + seed, w.isSolid(x, link.from.floorY, z));
                    assertTrue("seed " + seed, w.isSolid(x, link.from.floorY + CellarLayout.CORRIDOR_HEIGHT + 1, z));
                }
            }
        }
    }

    @Test
    public void witchCountStaysInRange() {
        Random random = new Random(11);
        boolean low = false;
        boolean high = false;
        for (int i = 0; i < 400; i++) {
            int count = WitchHutPlan.witchCount(2, 5, random);
            assertTrue(count >= 2 && count <= 5);
            low |= count == 2;
            high |= count == 5;
            int swapped = WitchHutPlan.witchCount(5, 2, random);
            assertTrue(swapped >= 2 && swapped <= 5);
        }
        assertTrue(low && high);
        assertEquals(0, WitchHutPlan.witchCount(0, 0, random));
        assertEquals(0, WitchHutPlan.witchCount(-3, 0, random));
    }

    @Test
    public void planSurvivesNbt() {
        WitchHutPlan plan = new WitchHutPlan(-345, 61, 7890, 0x1234567890ABCDEFL, 0, HutVariant.DOCK, true, 4);
        NBTTagCompound tag = plan.writeToNBT();
        StructurePlan read = StructurePlans.read(tag);
        assertTrue(read instanceof WitchHutPlan);
        WitchHutPlan copy = (WitchHutPlan) read;
        assertEquals(plan.centreX, copy.centreX);
        assertEquals(plan.surfaceY, copy.surfaceY);
        assertEquals(plan.centreZ, copy.centreZ);
        assertEquals(plan.seed, copy.seed);
        assertEquals(plan.dimension(), copy.dimension());
        assertSame(plan.variant, copy.variant);
        assertEquals(plan.cellar, copy.cellar);
        assertEquals(plan.witches, copy.witches);
        assertEquals(plan.footprint().toString(), copy.footprint().toString());
        BlockWriter a = new BlockWriter(new Blueprint());
        BlockWriter b = new BlockWriter(new Blueprint());
        CitadelInstance first = plan.build(a);
        CitadelInstance second = copy.build(b);
        assertEquals(a.getWritten(), b.getWritten());
        assertEquals(first.getCitadelId(), second.getCitadelId());
        assertEquals(first.getChestPoints(), second.getChestPoints());
        // An unknown variant of an edited save falls back instead of failing.
        tag.setString("variant", "gone");
        assertSame(HutVariant.SMALL, ((WitchHutPlan) StructurePlans.read(tag)).variant);
    }

    @Test
    public void variantsAreFoundById() {
        assertSame(HutVariant.TALL, HutVariant.byId("tall"));
        assertSame(HutVariant.DOCK, HutVariant.byId("DOCK"));
        assertNull(HutVariant.byId("castle"));
        assertNull(HutVariant.byId(null));
        assertEquals(HutVariant.values().length, HutVariant.ids().length);
    }

    @Test
    public void lootTablesAreValidJson() throws Exception {
        for (String name : new String[]{"cellar", "hut"}) {
            File file = new File("src/main/resources/assets/shatteredcitadels/loot_tables/witch_hut/" + name + ".json");
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JsonObject table = new JsonParser().parse(text).getAsJsonObject();
            JsonArray pools = table.getAsJsonArray("pools");
            assertTrue(pools.size() >= 2);
            Set<String> poolNames = new HashSet<>();
            for (JsonElement p : pools) {
                JsonObject pool = p.getAsJsonObject();
                assertTrue(name + ": pool name repeats", poolNames.add(pool.get("name").getAsString()));
                assertTrue(pool.has("rolls"));
                Set<String> entries = new HashSet<>();
                for (JsonElement e : pool.getAsJsonArray("entries")) {
                    JsonObject entry = e.getAsJsonObject();
                    assertTrue(entry.get("weight").getAsInt() > 0);
                    String id = entry.has("entryName") ? entry.get("entryName").getAsString() : entry.get("name").getAsString();
                    assertTrue(name + ": entry repeats: " + id, entries.add(id));
                    if (entry.has("functions")) {
                        for (JsonElement f : entry.getAsJsonArray("functions")) {
                            JsonObject function = f.getAsJsonObject();
                            if ("set_nbt".equals(function.get("function").getAsString())) {
                                assertTrue(function.get("tag").getAsString().startsWith("{Potion:\"minecraft:"));
                            }
                        }
                    }
                }
            }
        }
        String cellar = new String(Files.readAllBytes(new File(
                "src/main/resources/assets/shatteredcitadels/loot_tables/witch_hut/cellar.json").toPath()), StandardCharsets.UTF_8);
        for (String must : new String[]{"minecraft:nether_wart", "minecraft:blaze_powder", "minecraft:ghast_tear",
                "minecraft:rabbit_foot", "minecraft:emerald", "strong_healing", "shatteredcitadels:crown_shard"}) {
            assertTrue(must, cellar.contains(must));
        }
    }
}
