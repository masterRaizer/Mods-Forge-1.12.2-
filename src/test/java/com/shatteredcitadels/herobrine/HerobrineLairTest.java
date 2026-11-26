package com.shatteredcitadels.herobrine;

import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.worldgen.BlockWriter;
import com.shatteredcitadels.worldgen.Blueprint;
import com.shatteredcitadels.worldgen.StructurePlans;
import com.shatteredcitadels.worldgen.TestBlocks;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HerobrineLairTest {

    @BeforeClass
    public static void blocks() {
        TestBlocks.install();
    }

    private static HerobrineLairPlan plan(LairVariant variant) {
        return new HerobrineLairPlan(variant, 1000, -520, 70, 99L + variant.ordinal(), 0);
    }

    private static AltarPattern.Cells cells(BlockWriter writer, BlockPos netherrack) {
        return (dx, dy, dz) -> {
            Block block = writer.get(netherrack.getX() + dx, netherrack.getY() + dy, netherrack.getZ() + dz).getBlock();
            if (block == Blocks.GOLD_BLOCK) {
                return AltarPattern.Cell.GOLD;
            }
            if (block == Blocks.NETHERRACK) {
                return AltarPattern.Cell.NETHERRACK;
            }
            return block == Blocks.REDSTONE_TORCH ? AltarPattern.Cell.TORCH : AltarPattern.Cell.OTHER;
        };
    }

    @Test
    public void altarPatternNeedsEveryPiece() {
        AltarPattern.Cells whole = (dx, dy, dz) -> {
            if (dy == -1 && Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                return AltarPattern.Cell.GOLD;
            }
            if (dy == 0 && dx == 0 && dz == 0) {
                return AltarPattern.Cell.NETHERRACK;
            }
            return dy == 0 && Math.abs(dx) + Math.abs(dz) == 1 ? AltarPattern.Cell.TORCH : AltarPattern.Cell.OTHER;
        };
        assertTrue(AltarPattern.matches(whole));
        assertEquals(4, AltarPattern.torches().length);
        for (int hole = 0; hole < 14; hole++) {
            final int index = hole;
            // 0..8 a gold block, 9..12 a torch, 13 the netherrack itself.
            AltarPattern.Cells broken = (dx, dy, dz) -> {
                boolean gold = index < 9 && dy == -1 && dx == index % 3 - 1 && dz == index / 3 - 1;
                boolean torch = index >= 9 && index < 13 && dy == 0
                        && dx == AltarPattern.torches()[index - 9][0] && dz == AltarPattern.torches()[index - 9][1];
                boolean centre = index == 13 && dx == 0 && dy == 0 && dz == 0;
                return gold || torch || centre ? AltarPattern.Cell.OTHER : whole.at(dx, dy, dz);
            };
            assertFalse("hole " + hole, AltarPattern.matches(broken));
        }
    }

    @Test
    public void everyVariantHoldsAWholeAltarAndStaysInsideItsFootprint() {
        for (LairVariant variant : LairVariant.values()) {
            HerobrineLairPlan plan = plan(variant);
            BlockWriter writer = new BlockWriter(new Blueprint());
            LairBuilder builder = plan.write(writer, new Random(5L));
            assertEquals(variant.id(), plan.altarPos(), builder.altar());
            assertTrue(variant.id(), AltarPattern.matches(cells(writer, builder.altar())));
            assertSame(variant.id(), Blocks.CHEST, writer.get(builder.chest().getX(), builder.chest().getY(), builder.chest().getZ()).getBlock());
            // Room to stand above the altar: the boss appears there.
            assertFalse(variant.id(), writer.isSolid(builder.altar().getX(), builder.altar().getY() + 1, builder.altar().getZ()));
            assertFalse(variant.id(), writer.isSolid(builder.altar().getX(), builder.altar().getY() + 2, builder.altar().getZ()));
            StructureBoundingBox box = plan.footprint();
            int[] t = builder.touched();
            assertTrue(variant.id() + " min", t[0] >= box.minX && t[1] >= box.minY && t[2] >= box.minZ);
            assertTrue(variant.id() + " max", t[3] <= box.maxX && t[4] <= box.maxY && t[5] <= box.maxZ);
            assertTrue(variant.id(), box.maxX - box.minX <= 16 && box.maxZ - box.minZ <= 16);
        }
    }

    @Test
    public void nbtRoundTripBuildsTheSameLair() {
        for (LairVariant variant : LairVariant.values()) {
            HerobrineLairPlan plan = plan(variant);
            HerobrineLairPlan copy = (HerobrineLairPlan) StructurePlans.read(plan.writeToNBT());
            assertNotNull(variant.id(), copy);
            assertSame(variant, copy.variant());
            assertEquals(plan.describe(), copy.describe());
            assertEquals(plan.footprint().toString(), copy.footprint().toString());
            Blueprint first = new Blueprint();
            Blueprint second = new Blueprint();
            CitadelInstance record = plan.build(new BlockWriter(first));
            CitadelInstance again = copy.build(new BlockWriter(second));
            assertTrue(variant.id(), first.blockCount() > 200);
            assertEquals(variant.id(), first.blockCount(), second.blockCount());
            assertEquals(record.getCitadelId(), again.getCitadelId());
            assertSame(StructureType.HEROBRINE_LAIR, record.getType());
            assertTrue(record.isUnprotected());
            assertEquals(1, record.getChestPoints().size());
        }
    }

    @Test
    public void lairTypeIsAppendedAndFound() {
        assertSame(StructureType.HEROBRINE_LAIR, StructureType.byName("HEROBRINE_LAIR"));
        assertEquals(StructureType.BRIDGE.ordinal() + 1, StructureType.HEROBRINE_LAIR.ordinal());
    }

    @Test
    public void placementIsDeterministicAndKeepsItsMargins() {
        int spacing = 112;
        int present = 0;
        boolean[] variants = new boolean[LairVariant.values().length];
        for (int rx = -6; rx <= 6; rx++) {
            for (int rz = -6; rz <= 6; rz++) {
                LairPlacement.Site site = LairPlacement.siteOf(4242L, rx, rz, spacing, 0.6D);
                LairPlacement.Site same = LairPlacement.siteOf(4242L, rx, rz, spacing, 0.6D);
                assertEquals(site.chunkX, same.chunkX);
                assertEquals(site.chunkZ, same.chunkZ);
                assertEquals(site.seed, same.seed);
                assertEquals(rx, LairPlacement.region(site.chunkX, spacing));
                assertEquals(rz, LairPlacement.region(site.chunkZ, spacing));
                int localX = site.chunkX - rx * spacing;
                int localZ = site.chunkZ - rz * spacing;
                assertTrue(localX >= LairPlacement.MAX_MARGIN && localX < spacing - LairPlacement.MAX_MARGIN);
                assertTrue(localZ >= LairPlacement.MAX_MARGIN && localZ < spacing - LairPlacement.MAX_MARGIN);
                if (site.present) {
                    present++;
                    variants[site.variant.ordinal()] = true;
                    assertNotNull(LairPlacement.siteAt(4242L, site.chunkX, site.chunkZ, spacing, 0.6D));
                    assertNull(LairPlacement.siteAt(4242L, site.chunkX + 1, site.chunkZ, spacing, 0.6D));
                } else {
                    assertNull(LairPlacement.siteAt(4242L, site.chunkX, site.chunkZ, spacing, 0.6D));
                }
            }
        }
        // 169 regions at 60%: far from none and far from all.
        assertTrue("present " + present, present > 70 && present < 135);
        for (boolean seen : variants) {
            assertTrue(seen);
        }
        assertNull(LairPlacement.siteOf(4242L, 0, 0, spacing, 0.0D).present ? "present" : null);
    }

    @Test
    public void neighbouringRegionsRollUnrelated() {
        int same = 0;
        for (int rx = 0; rx < 200; rx++) {
            LairPlacement.Site a = LairPlacement.siteOf(1L, rx, 0, 112, 0.5D);
            LairPlacement.Site b = LairPlacement.siteOf(1L, rx + 1, 0, 112, 0.5D);
            if (a.present == b.present) {
                same++;
            }
        }
        assertTrue("same " + same, same > 60 && same < 140);
    }

    @Test
    public void spawnAndGroundRules() {
        assertTrue(LairPlacement.isNearSpawn(10, -10, 0, 0, 32));
        assertTrue(LairPlacement.isNearSpawn(32, 0, 0, 0, 32));
        assertFalse(LairPlacement.isNearSpawn(33, 0, 0, 0, 32));
        assertFalse(LairPlacement.isGroundUsable(49));
        assertTrue(LairPlacement.isGroundUsable(64));
        assertFalse(LairPlacement.isGroundUsable(181));
    }

    @Test
    public void apparitionRule() {
        assertEquals(1000L + 8 * 1200L, ApparitionRule.nextAttempt(1000L, 8));
        assertTrue(ApparitionRule.isDue(500L, 500L));
        assertFalse(ApparitionRule.isDue(499L, 500L));
        assertFalse(ApparitionRule.isMoment(false, false, false));
        assertTrue(ApparitionRule.isMoment(true, false, false));
        assertTrue(ApparitionRule.isMoment(false, false, true));
        // Only inside the radius of a lair whose master still lives.
        assertTrue(ApparitionRule.isHaunted(100.0D, 0.0D, 0, 0, false, 256));
        assertTrue(ApparitionRule.isHaunted(256.0D, 0.0D, 0, 0, false, 256));
        assertFalse(ApparitionRule.isHaunted(257.0D, 0.0D, 0, 0, false, 256));
        assertFalse(ApparitionRule.isHaunted(200.0D, 200.0D, 0, 0, false, 256));
        assertFalse(ApparitionRule.isHaunted(10.0D, 0.0D, 0, 0, true, 256));
        assertFalse(ApparitionRule.isHaunted(0.0D, 0.0D, 0, 0, false, 0));
        Random random = new Random(3L);
        for (int i = 0; i < 300; i++) {
            int[] spot = ApparitionRule.spot(random, 0.5D, 0.5D);
            double distance = Math.sqrt(Math.pow(spot[0] + 0.5D - 0.5D, 2) + Math.pow(spot[1] + 0.5D - 0.5D, 2));
            assertTrue("distance " + distance, distance >= ApparitionRule.MIN_DISTANCE - 1 && distance <= ApparitionRule.MAX_DISTANCE + 1);
        }
        assertFalse(ApparitionRule.rolls(new Random(1L), 0.0D));
        assertTrue(ApparitionRule.rolls(new Random(1L), 1.0D));
        assertTrue(ApparitionRule.isLookedAt(0.999D, 40.0D));
        assertFalse(ApparitionRule.isLookedAt(0.9D, 40.0D));
        assertFalse(ApparitionRule.shouldVanish(10, 40.0D, 0));
        assertTrue(ApparitionRule.shouldVanish(10, 8.0D, 0));
        assertTrue(ApparitionRule.shouldVanish(10, 40.0D, ApparitionRule.VANISH_LOOK_TICKS));
        assertTrue(ApparitionRule.shouldVanish(ApparitionRule.LIFETIME_TICKS, 40.0D, 0));
    }

    @Test
    public void savedLairsSurviveReloadAndOnlyMoveForward() {
        HerobrineSavedData data = new HerobrineSavedData();
        BlockPos first = new BlockPos(100, 71, -40);
        BlockPos second = new BlockPos(-3000, 57, 900);
        data.register(first);
        data.register(second);
        data.register(first);
        assertEquals(2, data.lairs().size());
        assertTrue(data.lairAt(first).isActive());
        data.advance(first, HerobrineSavedData.SUMMONED);
        assertFalse(data.lairAt(first).isActive());
        data.rearm(first);
        assertTrue(data.lairAt(first).isActive());
        data.advance(second, HerobrineSavedData.DEFEATED);
        data.advance(second, HerobrineSavedData.SUMMONED);
        data.rearm(second);
        assertTrue(data.lairAt(second).isDefeated());

        HerobrineSavedData loaded = new HerobrineSavedData();
        loaded.readFromNBT(data.writeToNBT(new NBTTagCompound()));
        assertEquals(2, loaded.lairs().size());
        assertTrue(loaded.lairAt(first).isActive());
        assertTrue(loaded.lairAt(second).isDefeated());
        assertNull(loaded.lairAt(new BlockPos(0, 0, 0)));
        assertTrue(loaded.isHaunted(200.0D, -40.0D, 256));
        assertFalse(loaded.isHaunted(-3000.0D, 900.0D, 256));
        assertFalse(loaded.isHaunted(5000.0D, 5000.0D, 256));
        // An old world has no such data at all.
        HerobrineSavedData empty = new HerobrineSavedData();
        empty.readFromNBT(new NBTTagCompound());
        assertTrue(empty.lairs().isEmpty());
    }
}
