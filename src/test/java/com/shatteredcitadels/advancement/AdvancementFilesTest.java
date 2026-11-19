package com.shatteredcitadels.advancement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Structural checks of the 12 advancements: parents, triggers and lang keys all resolve. */
public class AdvancementFilesTest {

    private static final Set<String> TRIGGERS = new HashSet<>(Arrays.asList(
            "shatteredcitadels:enter_structure", "shatteredcitadels:floor_reached", "shatteredcitadels:floor_cleared",
            "shatteredcitadels:spawner_completed", "shatteredcitadels:boss_defeated", "shatteredcitadels:lore_collected",
            "minecraft:inventory_changed"));

    @Test
    public void advancementsAreConsistent() throws IOException {
        File dir = new File("src/main/resources/assets/shatteredcitadels/advancements");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        assertNotNull(files);
        assertEquals(12, files.length);
        Set<String> ids = new HashSet<>();
        for (File f : files) {
            ids.add("shatteredcitadels:" + f.getName().replace(".json", ""));
        }
        Set<String> en = keys("en_us.lang");
        Set<String> ru = keys("ru_ru.lang");
        int roots = 0;
        for (File f : files) {
            JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("parent")) {
                assertTrue(f.getName() + " parent", ids.contains(root.get("parent").getAsString()));
            } else {
                roots++;
            }
            JsonObject display = root.getAsJsonObject("display");
            for (String part : new String[]{"title", "description"}) {
                String key = display.getAsJsonObject(part).get("translate").getAsString();
                assertTrue(key, en.contains(key) && ru.contains(key));
            }
            for (Map.Entry<String, JsonElement> criterion : root.getAsJsonObject("criteria").entrySet()) {
                String trigger = criterion.getValue().getAsJsonObject().get("trigger").getAsString();
                assertTrue(f.getName() + " trigger " + trigger, TRIGGERS.contains(trigger));
            }
        }
        assertEquals(1, roots);
    }

    private static Set<String> keys(String file) throws IOException {
        Set<String> keys = new HashSet<>();
        for (String line : Files.readAllLines(Paths.get("src/main/resources/assets/shatteredcitadels/lang/" + file), StandardCharsets.UTF_8)) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                keys.add(line.substring(0, eq));
            }
        }
        return keys;
    }
}
