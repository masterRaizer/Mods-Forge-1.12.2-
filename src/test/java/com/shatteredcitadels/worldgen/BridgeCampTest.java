package com.shatteredcitadels.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shatteredcitadels.worldgen.bridge.BridgeBuilder;
import com.shatteredcitadels.worldgen.bridge.BridgeCamp;
import com.shatteredcitadels.worldgen.bridge.BridgeStyle;
import net.minecraft.block.BlockChest;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BridgeCampTest {

    private static final int SEA = 63;
    private static final BridgeStyle[] CAMP_STYLES = {BridgeStyle.TIMBER, BridgeStyle.STONE_ARCH, BridgeStyle.SPRUCE,
            BridgeStyle.SANDSTONE, BridgeStyle.SNOW_STONE, BridgeStyle.TURRETED};

    @BeforeClass
    public static void blocks() {
        TestBlocks.install();
    }

    @Test
    public void guardCountIsThreeToFive() {
        Random rand = new Random(9);
        boolean three = false;
        boolean five = false;
        for (int i = 0; i < 500; i++) {
            int count = BridgeCamp.guardCount(rand);
            assertTrue(count >= 3 && count <= 5);
            three |= count == 3;
            five |= count == 5;
        }
        assertTrue(three && five);
    }

    @Test
    public void guardsFallBackWithoutPillager() {
        ResourceLocation[] ids = BridgeCamp.guardIds(id -> false);
        assertEquals("minecraft:vindication_illager", ids[0].toString());
        assertEquals("minecraft:skeleton", ids[1].toString());
    }

    @Test
    public void guardsUsePillagerWhenRegistered() {
        assertEquals("raids:pillager", BridgeCamp.guardIds(id -> id.getPath().equals("pillager"))[1].toString());
        assertEquals("modernbackports:pillager",
                BridgeCamp.guardIds(id -> id.toString().equals("modernbackports:pillager"))[1].toString());
    }

    @Test
    public void campFitsEveryStyleBothWays() {
        for (BridgeStyle style : CAMP_STYLES) {
            for (boolean alongX : new boolean[]{true, false}) {
                for (int width : new int[]{9, 24}) {
                    String what = style + " " + width + (alongX ? " x" : " z");
                    BlockWriter w = new BlockWriter(new Blueprint());
                    BridgeBuilder b = new BridgeBuilder(w, new Random(4), style, alongX, 200, 200 + width - 1, 50, SEA);
                    b.build();
                    BridgeCamp camp = new BridgeCamp(w, new Random(4), b, style, SEA);
                    camp.build();
                    // Each barricade leaves exactly one column to walk through.
                    for (int a : camp.barricades()) {
                        int open = 0;
                        for (int c = -b.half(); c <= b.half(); c++) {
                            boolean low = get(w, b.at(a, b.deckY() + 1, c)).getMaterial() == Material.AIR;
                            boolean high = get(w, b.at(a, b.deckY() + 2, c)).getMaterial() == Material.AIR;
                            assertEquals(what, low, high);
                            if (low) {
                                open++;
                            }
                        }
                        assertEquals(what, 1, open);
                    }
                    assertEquals(what, 1, camp.chestCount());
                    assertNotNull(what, camp.chestPos());
                    assertTrue(what, get(w, camp.chestPos()).getBlock() instanceof BlockChest);
                    assertTrue(what, get(w, camp.chestPos().up()).getMaterial() == Material.AIR);
                    StructureBoundingBox box = BridgeBuilder.footprint(style, alongX, 200, 200 + width - 1, 50, SEA);
                    int inside = 0;
                    for (int x = box.minX; x <= box.maxX; x++) {
                        for (int z = box.minZ; z <= box.maxZ; z++) {
                            for (int y = box.minY; y <= box.maxY; y++) {
                                if (w.isPlanned(x, y, z)) {
                                    inside++;
                                }
                            }
                        }
                    }
                    assertEquals(what + " outside the footprint", w.getWritten(), inside);
                }
            }
        }
    }

    private static IBlockState get(BlockWriter w, BlockPos pos) {
        return w.get(pos.getX(), pos.getY(), pos.getZ());
    }

    @Test
    public void lootTableFileIsValidJson() throws Exception {
        File file = new File("src/main/resources/assets/shatteredcitadels/loot_tables/chests/bridge_camp.json");
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        JsonObject table = new JsonParser().parse(text).getAsJsonObject();
        assertEquals(1, table.getAsJsonArray("pools").size());
        assertEquals(9, table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").size());
        assertEquals("shatteredcitadels:chests/bridge_camp", BridgeCamp.LOOT.toString());
    }
}
