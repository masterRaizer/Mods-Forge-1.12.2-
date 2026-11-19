package com.shatteredcitadels.lore;

import com.shatteredcitadels.ShatteredCitadelsMod;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Drops the BetterQuesting questline ({@code config/betterquesting/citadels_questline.json}) next
 * to the pack's config when BetterQuesting is installed and the file is not there yet. Pack makers
 * import it from the BetterQuesting editor; an existing (possibly edited) file is never touched.
 */
public final class QuestlineInstaller {

    private static final String RESOURCE = "/assets/shatteredcitadels/questline/citadels_questline.json";

    private QuestlineInstaller() {
    }

    public static void install(File configDir) {
        if (configDir == null || !Loader.isModLoaded("betterquesting")) {
            return;
        }
        File target = new File(new File(configDir, "betterquesting"), "citadels_questline.json");
        if (target.exists()) {
            return;
        }
        try (InputStream in = QuestlineInstaller.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                ShatteredCitadelsMod.LOGGER.warn("Bundled BetterQuesting questline is missing from the jar");
                return;
            }
            Files.createDirectories(target.getParentFile().toPath());
            Files.copy(in, target.toPath());
            ShatteredCitadelsMod.LOGGER.info("Installed BetterQuesting questline to {}", target.getPath());
        } catch (IOException | RuntimeException e) {
            ShatteredCitadelsMod.LOGGER.warn("Could not install the BetterQuesting questline", e);
        }
    }
}
