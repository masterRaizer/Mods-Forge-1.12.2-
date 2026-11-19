package com.shatteredcitadels.core;

import com.shatteredcitadels.compat.castledungeons.CastleDungeonsMixinState;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.net.URL;
import java.util.List;
import java.util.Set;

/**
 * Guards the Castle Dungeons mixins: a mixin is applied only if its target class really exists
 * in the installed Castle Dungeons jar. Combined with {@code defaultRequire: 0} and the
 * mod-gated late loader, a missing or different Castle Dungeons version can never cause a
 * ClassNotFoundException / NoClassDefFoundError or a mixin crash.
 */
public final class CastleDungeonsMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LogManager.getLogger("shatteredcitadels-mixins");

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("Castle Dungeons mixin plugin loaded for {}", mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean present = classExists(targetClassName);
        if (!present) {
            CastleDungeonsMixinState.markSkipped(mixinClassName);
            LOGGER.warn("Castle Dungeons class {} not found; {} disabled", targetClassName, mixinClassName);
        }
        return present;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        CastleDungeonsMixinState.markApplied(mixinClassName);
        LOGGER.info("Applied {} to {}", mixinClassName, targetClassName);
    }

    private static boolean classExists(String className) {
        String resource = className.replace('.', '/') + ".class";
        try {
            ClassLoader loader = Launch.classLoader != null ? Launch.classLoader : CastleDungeonsMixinPlugin.class.getClassLoader();
            URL url = loader.getResource(resource);
            return url != null;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
