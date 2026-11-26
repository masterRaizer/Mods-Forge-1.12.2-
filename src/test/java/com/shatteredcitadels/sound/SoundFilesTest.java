package com.shatteredcitadels.sound;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** sounds.json, ModSounds and the lang files agree with each other; the mod ships no audio of its own. */
public class SoundFilesTest {

    private static final String ASSETS = "src/main/resources/assets/shatteredcitadels/";
    private static final String MAIN = "src/main/java/com/shatteredcitadels/";
    private static final Set<String> HEROBRINE = new HashSet<>(Arrays.asList(
            "herobrine.appear", "herobrine.teleport", "herobrine.lightning", "herobrine.summon",
            "herobrine.phase", "herobrine.death", "herobrine.apparition", "herobrine.whisper"));

    @Test
    public void everyRegisteredSoundIsDefined() throws IOException {
        Set<String> registered = registered();
        JsonObject sounds = sounds();
        assertTrue(registered.toString(), registered.containsAll(HEROBRINE));
        for (String name : registered) {
            assertTrue(name + " is missing from sounds.json", sounds.has(name));
        }
        for (Map.Entry<String, JsonElement> entry : sounds.entrySet()) {
            assertTrue(entry.getKey() + " is never registered", registered.contains(entry.getKey()));
        }
    }

    @Test
    public void everySoundHasSubtitlesInBothLanguagesAndUsesVanillaAudio() throws IOException {
        Set<String> en = keys("en_us.lang");
        Set<String> ru = keys("ru_ru.lang");
        for (Map.Entry<String, JsonElement> entry : sounds().entrySet()) {
            JsonObject sound = entry.getValue().getAsJsonObject();
            String subtitle = sound.get("subtitle").getAsString();
            assertEquals("shatteredcitadels.subtitle." + entry.getKey(), subtitle);
            assertTrue(subtitle + " en_us", en.contains(subtitle));
            assertTrue(subtitle + " ru_ru", ru.contains(subtitle));
            assertTrue(entry.getKey() + " has no audio", sound.getAsJsonArray("sounds").size() > 0);
            for (JsonElement file : sound.getAsJsonArray("sounds")) {
                String name = file.getAsJsonObject().get("name").getAsString();
                assertTrue(entry.getKey() + " -> " + name, name.startsWith("minecraft:"));
            }
        }
        try (Stream<Path> own = Files.walk(Paths.get(ASSETS))) {
            assertFalse("the mod must not ship .ogg files", own.anyMatch(p -> p.toString().endsWith(".ogg")));
        }
    }

    /** Each Herobrine sound field is really played somewhere in the herobrine package. */
    @Test
    public void everyHerobrineSoundIsUsedInCode() throws IOException {
        String code;
        try (Stream<Path> files = Files.walk(Paths.get(MAIN + "herobrine"))) {
            code = files.filter(p -> p.toString().endsWith(".java")).map(SoundFilesTest::read).collect(Collectors.joining("\n"));
        }
        for (String name : HEROBRINE) {
            String field = "ModSounds." + name.toUpperCase().replace('.', '_');
            assertTrue(field + " is never played", code.contains(field));
        }
    }

    private static Set<String> registered() {
        Set<String> names = new HashSet<>();
        Matcher m = Pattern.compile("create\\(\"([a-z_.]+)\"\\)").matcher(read(Paths.get(MAIN + "boss/ModSounds.java")));
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private static JsonObject sounds() {
        return new JsonParser().parse(read(Paths.get(ASSETS + "sounds.json"))).getAsJsonObject();
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> keys(String file) throws IOException {
        Set<String> keys = new HashSet<>();
        for (String line : Files.readAllLines(Paths.get(ASSETS + "lang/" + file), StandardCharsets.UTF_8)) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                keys.add(line.substring(0, eq));
            }
        }
        return keys;
    }
}
