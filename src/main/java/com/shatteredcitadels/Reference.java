package com.shatteredcitadels;

/**
 * Compile-time constants shared by the mod container, configuration and mixin loader.
 * {@link #VERSION} is verified against {@code gradle.properties} by the build.
 */
public final class Reference {

    public static final String MODID = "shatteredcitadels";
    public static final String NAME = "Citadels of the Shattered Crown";
    public static final String VERSION = "0.1.0";

    public static final String MC_VERSIONS = "[1.12.2]";

    /**
     * Forge 14.23.5.2860 is the pack target; 2847+ is accepted because the API is identical.
     * MixinBooter is the only hard dependency. Every other integration is soft (after:) and
     * guarded at runtime with {@code Loader.isModLoaded}.
     */
    public static final String DEPENDENCIES = "required-after:forge@[14.23.5.2847,);"
            + "required-after:mixinbooter@[8.0,);"
            + "after:lootr;"
            + "after:baubles;"
            + "after:apotheosis";

    public static final String CLIENT_PROXY = "com.shatteredcitadels.proxy.ClientProxy";
    public static final String SERVER_PROXY = "com.shatteredcitadels.proxy.CommonProxy";

    public static final String MIXIN_CONFIG_CORE = "mixins." + MODID + ".json";

    private Reference() {
    }
}
