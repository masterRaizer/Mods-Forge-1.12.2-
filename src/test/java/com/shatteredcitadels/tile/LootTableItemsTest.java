package com.shatteredcitadels.tile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Every loot entry must name a real 1.12.2 item (catches 1.13+ IDs) and be unique per pool. */
public class LootTableItemsTest {

    /** Our own items are registered by Forge at runtime, not in this unit test. */
    private static final Set<String> MOD_ITEMS = new HashSet<>(java.util.Arrays.asList(
            "shatteredcitadels:crown_shard", "shatteredcitadels:cloudspire_aegis",
            "shatteredcitadels:goblin_forge_heart", "shatteredcitadels:architect_blade"));

    @BeforeClass
    public static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    public void allSpawnerLootTablesUseValidItems() throws IOException {
        check("spawner", 6);
    }

    @Test
    public void allChestLootTablesUseValidItems() throws IOException {
        check("chests", 12);
    }

    @Test
    public void allVaseLootTablesUseValidItems() throws IOException {
        check("vases", 3);
    }

    private static void check(String folder, int expectedFiles) throws IOException {
        File dir = new File("src/main/resources/assets/shatteredcitadels/loot_tables/" + folder);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        assertNotNull(files);
        assertEquals(expectedFiles, files.length);
        for (File file : files) {
            JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (JsonElement poolElement : root.getAsJsonArray("pools")) {
                JsonObject pool = poolElement.getAsJsonObject();
                assertTrue(file.getName() + ": pool without name", pool.has("name"));
                Set<String> entryNames = new HashSet<>();
                JsonArray entries = pool.getAsJsonArray("entries");
                for (JsonElement entryElement : entries) {
                    JsonObject entry = entryElement.getAsJsonObject();
                    if ("empty".equals(entry.get("type").getAsString())) {
                        assertTrue(entryNames.add(entry.has("entryName") ? entry.get("entryName").getAsString() : "empty"));
                        continue;
                    }
                    String item = entry.get("name").getAsString();
                    assertTrue(file.getName() + ": unknown item " + item,
                            Item.getByNameOrId(item) != null || MOD_ITEMS.contains(item));
                    String entryName = entry.has("entryName") ? entry.get("entryName").getAsString() : item;
                    assertTrue(file.getName() + ": duplicate entry " + entryName, entryNames.add(entryName));
                }
            }
        }
    }
}
