package medievalsim.grandexchange.ui.layout;

import java.awt.Color;
import necesse.engine.Settings;
import necesse.gfx.gameFont.FontOptions;

/**
 * Shared font presets and colors for the Grand Exchange UI.
 * Uses Necesse's native Settings.UI colors for proper theme integration.
 */
public final class GrandExchangeFonts {
    public static final FontOptions TITLE = new FontOptions(20);
    public static final FontOptions HEADER = new FontOptions(16);
    public static final FontOptions BODY = new FontOptions(14);
    public static final FontOptions SMALL = new FontOptions(12);

    /**
     * Returns the primary text color from Necesse's UI settings.
     * Used for main content, labels, and item names.
     */
    public static Color getPrimaryText() {
        return Settings.UI.activeTextColor;
    }

    /**
     * Returns the secondary text color from Necesse's UI settings.
     * Used for hints, metadata, and less prominent text.
     */
    public static Color getSecondaryText() {
        return Settings.UI.inactiveTextColor;
    }

    /**
     * Returns the highlight text color from Necesse's UI settings.
     * Used for hovered or emphasized text.
     */
    public static Color getHighlightText() {
        return Settings.UI.highlightTextColor;
    }

    // ========================================================================
    // LEGACY STATIC COLORS - For backward compatibility
    // These map to Necesse's default active text color for consistent appearance.
    // New code should prefer the getter methods above.
    // ========================================================================

    /** Use {@link #getPrimaryText()} for dynamic theme support */
    @Deprecated
    public static final Color PRIMARY_TEXT = new Color(20, 20, 20);

    /** Use {@link #getSecondaryText()} for dynamic theme support */
    @Deprecated
    public static final Color SECONDARY_TEXT = new Color(100, 100, 100);

    /** Unified text color - status communicated via notification bar */
    public static final Color SUCCESS_TEXT = new Color(20, 20, 20);

    /** Unified text color - status communicated via notification bar */
    public static final Color ERROR_TEXT = new Color(20, 20, 20);

    /** Unified text color - status communicated via notification bar */
    public static final Color INFO_TEXT = new Color(20, 20, 20);

    /** Unified text color - status communicated via notification bar */
    public static final Color BADGE_TEXT = new Color(20, 20, 20);

    /** Unified text color - status communicated via notification bar */
    public static final Color COOLDOWN_TEXT = new Color(20, 20, 20);

    /** Unified text color - status communicated via notification bar */
    public static final Color WARNING_TEXT = new Color(20, 20, 20);

    /** Gold/coin text - same as primary for unified appearance */
    public static final Color GOLD_TEXT = new Color(20, 20, 20);

    private GrandExchangeFonts() {
    }
}
