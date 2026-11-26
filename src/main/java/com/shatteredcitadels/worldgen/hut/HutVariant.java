package com.shatteredcitadels.worldgen.hut;

/** The looks of a witch hut. */
public enum HutVariant {
    /** One low room. */
    SMALL("small", 3, 3),
    /** A loft under a high roof. */
    TALL("tall", 3, 7),
    /** A wider hut with a pier and a boat. */
    DOCK("dock", 4, 3);

    public final String id;
    /** Half of the cabin's width: the side walls stand at +- half. */
    public final int half;
    /** Wall rows above the floor. */
    public final int wallHeight;

    HutVariant(String id, int half, int wallHeight) {
        this.id = id;
        this.half = half;
        this.wallHeight = wallHeight;
    }

    /** @return the variant of that id, or null. */
    public static HutVariant byId(String id) {
        for (HutVariant variant : values()) {
            if (variant.id.equalsIgnoreCase(id)) {
                return variant;
            }
        }
        return null;
    }

    public static String[] ids() {
        HutVariant[] all = values();
        String[] ids = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            ids[i] = all[i].id;
        }
        return ids;
    }
}
