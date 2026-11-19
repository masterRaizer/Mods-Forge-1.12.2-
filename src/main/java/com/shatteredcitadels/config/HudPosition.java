package com.shatteredcitadels.config;

/**
 * Screen anchor for the floor HUD. Kept free of client classes so the config loads on the
 * dedicated server. The defaults avoid the usual corners: Xaero's Minimap (top-right),
 * Hwyla/Jade (top-centre), vanilla potion icons (top-right) and chat (bottom-left).
 */
public enum HudPosition {
    TOP_LEFT(0.0F, 0.0F),
    TOP_CENTER(0.5F, 0.0F),
    TOP_RIGHT(1.0F, 0.0F),
    CENTER_LEFT(0.0F, 0.5F),
    CENTER_RIGHT(1.0F, 0.5F),
    BOTTOM_LEFT(0.0F, 1.0F),
    BOTTOM_RIGHT(1.0F, 1.0F);

    private final float anchorX;
    private final float anchorY;

    HudPosition(float anchorX, float anchorY) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
    }

    /** Horizontal anchor: 0 = left edge, 0.5 = centre, 1 = right edge. */
    public float getAnchorX() {
        return anchorX;
    }

    /** Vertical anchor: 0 = top edge, 0.5 = centre, 1 = bottom edge. */
    public float getAnchorY() {
        return anchorY;
    }

    /**
     * Resolves the top-left pixel of a box of the given size so that it stays fully on screen.
     *
     * @return {x, y}
     */
    public int[] place(int screenWidth, int screenHeight, int boxWidth, int boxHeight, int offsetX, int offsetY) {
        int x = Math.round((screenWidth - boxWidth) * anchorX);
        int y = Math.round((screenHeight - boxHeight) * anchorY);
        // Offsets push away from the anchored edge; centred axes treat the offset as a shift.
        x += anchorX > 0.75F ? -offsetX : offsetX;
        y += anchorY > 0.75F ? -offsetY : offsetY;
        x = Math.max(0, Math.min(x, Math.max(0, screenWidth - boxWidth)));
        y = Math.max(0, Math.min(y, Math.max(0, screenHeight - boxHeight)));
        return new int[]{x, y};
    }
}
