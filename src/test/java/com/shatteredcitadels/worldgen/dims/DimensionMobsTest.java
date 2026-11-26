package com.shatteredcitadels.worldgen.dims;

import com.shatteredcitadels.config.ModConfig;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The garrison lists only name mobs that exist, and a wrong name costs one log line, not a crash. */
public class DimensionMobsTest {

    /** Entity ids read from the Atum 2 jar of the pack (2.0.20). */
    private static final Set<String> ATUM = new HashSet<>(Arrays.asList(
            "assassin", "warlord", "barbarian", "bonestorm", "brigand", "camel", "desert_wolf", "forsaken", "mummy",
            "nomad", "pharaoh", "desert_rabbit", "scarab", "stoneguard", "stonewarden", "sunspeaker", "tarantula", "wraith"));

    @Test
    public void defaultAtumGarrisonIsPinnedToVerifiedIds() {
        String[] mobs = new ModConfig.Dimensions().atumMobs;
        assertArrayEquals(new String[]{"atum:mummy", "atum:forsaken", "atum:bonestorm", "atum:wraith", "atum:stoneguard",
                "minecraft:husk"}, mobs);
        for (String mob : mobs) {
            if (mob.startsWith("atum:")) {
                assertTrue(mob, ATUM.contains(mob.substring(5)));
            }
        }
    }

    @Test
    public void builtInAtumBossesExistInAtum() throws IOException {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/shatteredcitadels/config/BuiltInModBosses.java")), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\"atum:([a-z_]+)\"").matcher(source);
        int found = 0;
        while (m.find()) {
            found++;
            assertTrue(m.group(1), ATUM.contains(m.group(1)));
        }
        assertTrue(found >= 3);
    }

    @Test
    public void withoutAtumNothingIsKnownAndNothingIsReported() {
        String[] mobs = {"atum:mummy", "atum:forsaken"};
        assertTrue(DimensionMobs.known(mobs, id -> false).isEmpty());
        assertTrue(DimensionMobs.unknown(mobs, id -> false, mod -> false).isEmpty());
        assertNull(DimensionMobs.warning("atumMobs", DimensionMobs.unknown(mobs, id -> false, mod -> false)));
    }

    @Test
    public void wrongNameOfAnInstalledModIsReportedInOneLine() {
        String[] mobs = {"atum:mummy", "atum:mumy", " ", "minecraft:husk", "minecraft:huskk", "nomod:thing"};
        Set<String> real = new HashSet<>(Arrays.asList("atum:mummy", "minecraft:husk"));
        List<ResourceLocation> known = DimensionMobs.known(mobs, id -> real.contains(id.toString()));
        assertEquals(Arrays.asList(new ResourceLocation("atum:mummy"), new ResourceLocation("minecraft:husk")), known);
        List<ResourceLocation> unknown = DimensionMobs.unknown(mobs, id -> real.contains(id.toString()), "atum"::equals);
        assertEquals(Arrays.asList(new ResourceLocation("atum:mumy"), new ResourceLocation("minecraft:huskk")), unknown);
        String warning = DimensionMobs.warning("atumMobs", unknown);
        assertTrue(warning, warning.contains("atumMobs") && warning.contains("atum:mumy") && warning.contains("minecraft:huskk"));
        assertFalse(warning.contains("\n"));
        assertFalse(warning.contains("nomod"));
    }
}
