package com.shatteredcitadels.worldgen;

import com.shatteredcitadels.block.ModBlocks;
import com.shatteredcitadels.palette.CitadelArchetype;
import com.shatteredcitadels.structure.CitadelInstance;
import com.shatteredcitadels.structure.StructureType;
import com.shatteredcitadels.structure.VaultLock;
import com.shatteredcitadels.worldgen.stronghold.StrongholdLayout;
import com.shatteredcitadels.worldgen.temple.JungleKind;
import com.shatteredcitadels.worldgen.temple.JungleTemple;
import com.shatteredcitadels.worldgen.temple.JungleTempleBuilder;
import com.shatteredcitadels.worldgen.temple.JungleTemplePlan;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JungleTempleTest {

    static final int SY = 70;

    @BeforeClass
    public static void bootstrap() {
        TestBlocks.install();
    }

    static CitadelInstance record() {
        return new CitadelInstance(UUID.randomUUID(), "", StructureType.JUNGLE_TEMPLE, CitadelArchetype.KEEPERS_CITADEL, 0,
                BlockPos.ORIGIN, new StructureBoundingBox(-60, 5, -60, 60, 120, 60));
    }

    static StrongholdLayout layout(long seed) {
        return StrongholdLayout.generate(seed, 0, SY - 16, 0, JungleKind.CATALOGUE);
    }

    @Test
    public void exactlyOneGoal() {
        for (long seed = 1; seed <= 100; seed++) {
            StrongholdLayout layout = layout(seed);
            assertNotNull("seed " + seed, layout);
            int goals = 0;
            for (StrongholdLayout.Piece piece : layout.pieces()) {
                goals += piece.kind == JungleKind.GOAL ? 1 : 0;
                assertTrue(piece.box.maxX <= 30 && piece.box.minX >= -30 && piece.box.maxZ <= 30 && piece.box.minZ >= -30);
            }
            assertEquals("seed " + seed, 1, goals);
        }
    }

    @Test
    public void shellIsClosed() {
        for (long seed = 1; seed <= 4; seed++) {
            StrongholdLayout layout = layout(seed);
            BlockWriter writer = new BlockWriter(new Blueprint());
            new JungleTempleBuilder(writer, record(), new Random(seed), seed % 2 == 0).build(layout);
            for (StrongholdLayout.Piece piece : layout.pieces()) {
                StructureBoundingBox b = piece.box;
                for (int x = b.minX; x <= b.maxX; x++) {
                    for (int y = b.minY; y <= b.maxY; y++) {
                        for (int z = b.minZ; z <= b.maxZ; z++) {
                            if (!writer.isPlanned(x, y, z) || writer.get(x, y, z).getMaterial().blocksMovement()) {
                                continue;
                            }
                            for (EnumFacing d : EnumFacing.values()) {
                                assertTrue("seed " + seed + " " + piece.kind + ": open " + x + "," + y + "," + z + " next to raw " + d,
                                        writer.isPlanned(x + d.getXOffset(), y + d.getYOffset(), z + d.getZOffset()));
                            }
                        }
                    }
                }
            }
        }
    }

    private static int count(BlockWriter w, StructureBoundingBox b, Block block) {
        int n = 0;
        for (int x = b.minX; x <= b.maxX; x++) {
            for (int y = b.minY; y <= b.maxY; y++) {
                for (int z = b.minZ; z <= b.maxZ; z++) {
                    n += w.get(x, y, z).getBlock() == block ? 1 : 0;
                }
            }
        }
        return n;
    }

    @Test
    public void vaultVariantHasThreeIdolsAndThreeSockets() {
        for (long seed = 1; seed <= 6; seed++) {
            CitadelInstance rec = record();
            BlockWriter writer = new BlockWriter(new Blueprint());
            Random rand = new Random(seed);
            JungleTempleBuilder under = new JungleTempleBuilder(writer, rec, rand, false);
            under.build(layout(seed));
            JungleTemple surface = new JungleTemple(writer, rand, 0, SY, 0, false, rec);
            surface.build();
            assertEquals("seed " + seed, 3, under.idolCaches() + surface.idolCaches());
            VaultLock vault = rec.getVault();
            assertNotNull(vault);
            assertEquals(3, vault.sockets().size());
            StructureBoundingBox b = vault.box();
            assertEquals(3, count(writer, b, ModBlocks.IDOL_SOCKET));
            for (BlockPos socket : vault.sockets()) {
                assertEquals(ModBlocks.IDOL_SOCKET, writer.get(socket.getX(), socket.getY(), socket.getZ()).getBlock());
            }
            for (int x = b.minX; x <= b.maxX; x++) {
                for (int y = b.minY; y <= b.maxY; y++) {
                    for (int z = b.minZ; z <= b.maxZ; z++) {
                        boolean face = x == b.minX || x == b.maxX || y == b.minY || y == b.maxY || z == b.minZ || z == b.maxZ;
                        if (!face) {
                            continue;
                        }
                        Block block = writer.get(x, y, z).getBlock();
                        assertTrue("seed " + seed + ": vault face " + x + "," + y + "," + z + " is " + block,
                                block == ModBlocks.VAULT_BRICK || block == ModBlocks.SEALED_GATE || block == ModBlocks.IDOL_SOCKET);
                    }
                }
            }
            int gates = 0;
            for (CitadelInstance.GatePoint gate : rec.getGates()) {
                gates += gate.requiredFloor == VaultLock.GATE_FLOOR ? 1 : 0;
            }
            assertTrue("vault gates " + gates, gates >= 3);
            assertTrue(rec.isUnprotected());
        }
    }

    @Test
    public void bossVariantHasNoVaultAndPrefersBarako() {
        CitadelInstance rec = record();
        BlockWriter writer = new BlockWriter(new Blueprint());
        Random rand = new Random(5);
        JungleTempleBuilder under = new JungleTempleBuilder(writer, rec, rand, true);
        under.build(layout(5));
        JungleTemple surface = new JungleTemple(writer, rand, 0, SY, 0, true, rec);
        surface.build();
        assertNull(rec.getVault());
        assertEquals(0, under.idolCaches() + surface.idolCaches());
        assertEquals("mowziesmobs:barako", String.valueOf(rec.getPreferredBoss()));
        StructureBoundingBox arena = rec.getBossArenaBounds();
        assertNotNull(arena);
        assertTrue("arena at " + arena.minY, arena.minY >= SY + 20);
        int cx = (arena.minX + arena.maxX) / 2;
        int cz = (arena.minZ + arena.maxZ) / 2;
        for (int y = arena.minY + 2; y < 256; y++) {
            assertFalse("roof over the arena at " + y, writer.isSolid(cx, y, cz));
        }
        assertFalse(rec.isUnprotected());
    }

    @Test
    public void foundationFillsNeverPourIntoOpenings() {
        for (long seed = 1; seed <= 4; seed++) {
            BlockWriter writer = new BlockWriter(new Blueprint());
            new JungleTemplePlan(300, SY, -200, seed, 0, seed % 2 == 0).build(writer);
            Blueprint blueprint = new Blueprint();
            new JungleTemplePlan(300, SY, -200, seed, 0, seed % 2 == 0).build(new BlockWriter(blueprint));
            blueprint.seal();
            for (Blueprint.ChunkPart part : blueprint.parts().values()) {
                for (Blueprint.ColumnOp op : part.fills) {
                    if (op.kind != Blueprint.ColumnOp.FILL_DOWN) {
                        continue;
                    }
                    for (int d = 0; d < op.maxDepth; d++) {
                        int y = op.y - d;
                        if (writer.isPlanned(op.x, y, op.z) && writer.isSolid(op.x, y, op.z)) {
                            break;
                        }
                        assertFalse("seed " + seed + ": fill at " + op.x + "," + op.z + " pours into planned opening at y " + y,
                                writer.isPlanned(op.x, y, op.z));
                    }
                }
            }
        }
    }

    /** Every chest opens once found: nothing solid on top, except an under-floor cache's own tile. */
    @Test
    public void chestsCanOpen() {
        for (long seed = 1; seed <= 6; seed++) {
            BlockWriter writer = new BlockWriter(new Blueprint());
            new JungleTemplePlan(0, SY, 0, seed, 0, seed % 2 == 0).build(writer);
            for (int x = -60; x <= 60; x++) {
                for (int z = -60; z <= 60; z++) {
                    for (int y = 20; y <= SY + 40; y++) {
                        if (writer.get(x, y, z).getBlock() != Blocks.CHEST) {
                            continue;
                        }
                        Block above = writer.get(x, y + 1, z).getBlock();
                        boolean cacheTile = above == Blocks.STONEBRICK
                                && writer.get(x, y + 1, z) == JungleTemple.CACHE_TILE;
                        assertTrue("seed " + seed + ": chest at " + x + "," + y + "," + z + " is covered by " + above,
                                cacheTile || !writer.isSolid(x, y + 1, z));
                    }
                }
            }
        }
    }

    @Test
    public void spiralReachesTheUnderground() {
        BlockWriter writer = new BlockWriter(new Blueprint());
        new JungleTemplePlan(300, SY, -200, 7L, 0, true).build(writer);
        int start = JungleTemplePlan.undergroundFloor(SY);
        for (int[] step : com.shatteredcitadels.worldgen.catacomb.CatacombBuilder.spiralPath(SY, start)) {
            net.minecraft.block.state.IBlockState state = writer.get(300 + step[0], step[1], -200 + step[2]);
            assertTrue("step at " + step[1] + " is " + state, state.getBlock() instanceof net.minecraft.block.BlockStairs);
            assertFalse(writer.isSolid(300 + step[0], step[1] + 1, -200 + step[2]));
            assertFalse(writer.isSolid(300 + step[0], step[1] + 2, -200 + step[2]));
        }
    }
}
