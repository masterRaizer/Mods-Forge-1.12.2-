package com.shatteredcitadels.compat.castledungeons;

import com.shatteredcitadels.ShatteredCitadelsMod;
import com.shatteredcitadels.config.ConfigCache;
import com.shatteredcitadels.spawner.VanillaFallbackSpawnerPlacer;
import net.minecraft.util.ResourceLocation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File-level fallback for Castle Dungeons, independent of the mixins: before Castle Dungeons
 * reads its configuration (its post-init; we are ordered before it), rewrites
 * {@code section-size} in {@code config.cfg} and removes blacklisted mobs (creepers, witches...)
 * from the {@code spawner-*} lines of every theme. Idempotent; keeps a one-time backup.
 */
public final class CastleConfigPatcher {

    private static final Pattern SECTION_SIZE = Pattern.compile("^(\\s*section-size\\s*=\\s*)(\\d+)(.*)$");
    private static final Pattern SPAWNER_LINE = Pattern.compile("^(\\s*spawner-(easy|medium|hard)\\s*=\\s*)(.*)$");
    private static final String BACKUP_SUFFIX = ".shatteredcitadels.bak";

    private CastleConfigPatcher() {
    }

    /** @param configRoot the game's config directory. */
    public static void patch(File configRoot) {
        ConfigCache.Snapshot config = ConfigCache.get();
        if (!config.isCastleDungeonsConfigPatching() || configRoot == null) {
            return;
        }
        File dir = new File(configRoot, CastleDungeonsIntegration.MODID);
        if (!dir.isDirectory()) {
            ShatteredCitadelsMod.LOGGER.info("Castle Dungeons config directory not found, nothing to patch");
            return;
        }
        int changed = 0;
        if (config.isCastleDungeonsSectionOverride()) {
            changed += patchFile(new File(dir, "config.cfg"), true);
        }
        File themes = new File(dir, "themes");
        File[] files = themes.listFiles((d, name) -> name.endsWith(".cfg"));
        if (files != null) {
            for (File theme : files) {
                changed += patchFile(theme, false);
            }
        }
        ShatteredCitadelsMod.LOGGER.info("Castle Dungeons config fallback: {} file(s) updated", changed);
    }

    private static int patchFile(File file, boolean mainConfig) {
        if (!file.isFile()) {
            return 0;
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> out = new ArrayList<>(lines.size());
            boolean modified = false;
            for (String line : lines) {
                String patched = mainConfig ? patchSectionSize(line) : patchSpawnerLine(line);
                modified |= !patched.equals(line);
                out.add(patched);
            }
            if (!modified) {
                return 0;
            }
            File backup = new File(file.getPath() + BACKUP_SUFFIX);
            if (!backup.exists()) {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
            Files.write(file.toPath(), out, StandardCharsets.UTF_8);
            return 1;
        } catch (IOException | RuntimeException e) {
            ShatteredCitadelsMod.LOGGER.warn("Could not patch Castle Dungeons file {}", file.getName(), e);
            return 0;
        }
    }

    static String patchSectionSize(String line) {
        Matcher m = SECTION_SIZE.matcher(line);
        if (!m.matches()) {
            return line;
        }
        int wanted = ConfigCache.get().getCastleSectionSize();
        return m.group(2).equals(Integer.toString(wanted)) ? line : m.group(1) + wanted + m.group(3);
    }

    static String patchSpawnerLine(String line) {
        Matcher m = SPAWNER_LINE.matcher(line);
        if (!m.matches()) {
            return line;
        }
        ConfigCache.Snapshot config = ConfigCache.get();
        List<String> kept = new ArrayList<>();
        boolean removed = false;
        for (String token : m.group(3).trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            String id = token.toLowerCase(Locale.ROOT);
            if (id.indexOf(':') > 0 && config.isBlacklisted(new ResourceLocation(id))) {
                removed = true;
            } else {
                kept.add(token);
            }
        }
        if (!removed) {
            return line;
        }
        if (kept.isEmpty()) {
            int tier = "easy".equals(m.group(2)) ? 1 : "medium".equals(m.group(2)) ? 2 : 3;
            kept.add(VanillaFallbackSpawnerPlacer.chooseMob(tier, new Random(tier)).toString());
        }
        return m.group(1) + String.join(" ", kept);
    }
}
