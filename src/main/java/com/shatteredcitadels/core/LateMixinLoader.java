package com.shatteredcitadels.core;

import com.shatteredcitadels.Reference;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Late mixin loader discovered by MixinBooter through the ASM data table (implementers of
 * {@link ILateMixinLoader}). Late configurations are queued after every mod jar, including
 * Castle Dungeons, has been discovered, so mod classes can be targeted safely.
 *
 * <p>Each configuration may be bound to a mod ID: it is queued only when that mod is present,
 * which keeps optional integrations from failing when a mod is missing from the pack.
 *
 * <p>This class lives outside the mixin package on purpose: classes inside a mixin package
 * cannot be loaded directly.
 */
public final class LateMixinLoader implements ILateMixinLoader {

    private static final Logger LOGGER = LogManager.getLogger(Reference.MODID + "-mixins");

    /** Sentinel meaning "no mod required". */
    private static final String ALWAYS = "";

    /** Mixin config file -> mod ID that must be loaded (or {@link #ALWAYS}). Insertion ordered. */
    private static final Map<String, String> CONFIGS;

    static {
        Map<String, String> configs = new LinkedHashMap<>();
        configs.put(Reference.MIXIN_CONFIG_CORE, ALWAYS);
        configs.put(Reference.MIXIN_CONFIG_CASTLE_DUNGEONS, "castle_dungeons");
        CONFIGS = Collections.unmodifiableMap(configs);
    }

    private static final List<String> QUEUED = new CopyOnWriteArrayList<>();

    @Override
    public List<String> getMixinConfigs() {
        return new ArrayList<>(CONFIGS.keySet());
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        String requiredMod = CONFIGS.get(mixinConfig);
        if (requiredMod == null) {
            // Not one of ours: never claim foreign configurations.
            return false;
        }
        if (ALWAYS.equals(requiredMod)) {
            return true;
        }
        boolean present = Loader.isModLoaded(requiredMod);
        if (!present) {
            LOGGER.info("Skipping mixin config {}: optional mod '{}' is not installed", mixinConfig, requiredMod);
        }
        return present;
    }

    @Override
    public void onMixinConfigQueued(String mixinConfig) {
        if (!QUEUED.contains(mixinConfig)) {
            QUEUED.add(mixinConfig);
        }
        LOGGER.info("Queued mixin config {}", mixinConfig);
    }

    /** @return immutable snapshot of the configurations MixinBooter accepted. */
    public static List<String> getQueuedConfigs() {
        return Collections.unmodifiableList(new ArrayList<>(QUEUED));
    }
}
