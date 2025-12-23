package medievalsim.ui;

import java.awt.Color;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.ui.ButtonColor;

/**
 * Centralized UI style constants for consistent styling across all Medieval Sim forms.
 * 
 * <p>This class ensures uniform appearance by providing:</p>
 * <ul>
 *   <li>Standard font sizes for titles, headers, body text, and hints</li>
 *   <li>Consistent color palette for text and UI elements</li>
 *   <li>Standard spacing and sizing constants</li>
 *   <li>Common button configurations</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Use standard fonts
 * FormLabel title = new FormLabel("My Form", UIStyleConstants.TITLE_FONT, -1, x, y);
 * FormLabel body = new FormLabel("Description", UIStyleConstants.BODY_FONT, -1, x, y);
 * 
 * // Apply standard colors
 * label.setColor(UIStyleConstants.PRIMARY_TEXT_COLOR);
 * 
 * // Use standard button sizes
 * FormTextButton btn = new FormTextButton("Click", x, y, 
 *     UIStyleConstants.BUTTON_WIDTH_MEDIUM, 
 *     UIStyleConstants.BUTTON_SIZE_STANDARD, 
 *     ButtonColor.BASE);
 * }</pre>
 * 
 * @since 1.1
 */
public final class UIStyleConstants {
    
    // ========================================================================
    // FONT DEFINITIONS
    // ========================================================================
    
    /**
     * Title font for form headers and main headings (size 20).
     * Use for: Form titles, primary headers
     */
    public static final FontOptions TITLE_FONT = new FontOptions(20);
    
    /**
     * Header font for section headings (size 16).
     * Use for: Section headers, subheadings
     */
    public static final FontOptions HEADER_FONT = new FontOptions(16);
    
    /**
     * Body font for standard text and labels (size 14).
     * Use for: Primary labels, descriptions, standard text
     */
    public static final FontOptions BODY_FONT = new FontOptions(14);
    
    /**
     * Small font for hints, metadata, and secondary information (size 12).
     * Use for: Helper text, timestamps, counts, metadata
     */
    public static final FontOptions SMALL_FONT = new FontOptions(12);
    
    /**
     * Tiny font for very small UI elements (size 10).
     * Use for: Badges, compact labels, micro-text
     */
    public static final FontOptions TINY_FONT = new FontOptions(10);
    
    // ========================================================================
    // COLORED FONT VARIANTS (for dark backgrounds like Admin Tools)
    // ========================================================================
    
    /**
     * White title font for dark backgrounds (size 20).
     * Use for: Admin tools, command center titles
     */
    public static final FontOptions WHITE_TITLE_FONT = new FontOptions(20).color(Color.WHITE);
    
    /**
     * White header font for dark backgrounds (size 16).
     * Use for: Admin tools, command center section headers
     */
    public static final FontOptions WHITE_HEADER_FONT = new FontOptions(16).color(Color.WHITE);
    
    /**
     * White body font for dark backgrounds (size 14).
     * Use for: Admin tools, command center labels
     */
    public static final FontOptions WHITE_BODY_FONT = new FontOptions(14).color(Color.WHITE);
    
    /**
     * White small font for dark backgrounds (size 11).
     * Use for: Admin tools hints, metadata
     */
    public static final FontOptions WHITE_SMALL_FONT = new FontOptions(11).color(Color.WHITE);
    
    // ========================================================================
    // COLOR PALETTE
    // ========================================================================
    
    /**
     * Primary text color for main content (dark gray/black).
     * Use for: Labels, descriptions, primary text
     */
    public static final Color PRIMARY_TEXT_COLOR = new Color(20, 20, 20);
    
    /**
     * Secondary text color for less prominent information (medium gray).
     * Use for: Hints, metadata, secondary information
     */
    public static final Color SECONDARY_TEXT_COLOR = new Color(100, 100, 100);
    
    /**
     * Disabled/inactive text color (light gray).
     * Use for: Disabled components, inactive states
     */
    public static final Color DISABLED_TEXT_COLOR = new Color(150, 150, 150);
    
    /**
     * Success/positive feedback color (green).
     * Use for: Success messages, positive indicators
     */
    public static final Color SUCCESS_COLOR = new Color(46, 125, 50);
    
    /**
     * Error/negative feedback color (red).
     * Use for: Error messages, warnings, critical states
     */
    public static final Color ERROR_COLOR = new Color(183, 28, 28);
    
    /**
     * Info/neutral feedback color (blue).
     * Use for: Information messages, neutral indicators
     */
    public static final Color INFO_COLOR = new Color(25, 118, 210);
    
    /**
     * Warning color (orange/amber).
     * Use for: Warning messages, caution indicators
     */
    public static final Color WARNING_COLOR = new Color(245, 124, 0);
    
    // ========================================================================
    // BUTTON CONFIGURATIONS
    // ========================================================================
    
    /**
     * Standard button size for most UI interactions (SIZE_32).
     * Use for: Primary actions, navigation buttons
     */
    public static final FormInputSize BUTTON_SIZE_STANDARD = FormInputSize.SIZE_32;
    
    /**
     * Medium button size for compact layouts (SIZE_24).
     * Use for: Secondary actions, inline buttons
     */
    public static final FormInputSize BUTTON_SIZE_MEDIUM = FormInputSize.SIZE_24;
    
    /**
     * Small button size for icon buttons and compact controls (SIZE_20).
     * Use for: Icon buttons, dropdown toggles
     */
    public static final FormInputSize BUTTON_SIZE_SMALL = FormInputSize.SIZE_20;
    
    /**
     * Tiny button size for minimized controls (SIZE_16).
     * Use for: Minimize buttons, tiny icon controls
     */
    public static final FormInputSize BUTTON_SIZE_TINY = FormInputSize.SIZE_16;
    
    /**
     * Standard button width for primary actions (200px).
     */
    public static final int BUTTON_WIDTH_LARGE = 200;
    
    /**
     * Medium button width for secondary actions (150px).
     */
    public static final int BUTTON_WIDTH_MEDIUM = 150;
    
    /**
     * Small button width for compact buttons (100px).
     */
    public static final int BUTTON_WIDTH_SMALL = 100;
    
    /**
     * Extra small button width for minimal buttons (80px).
     */
    public static final int BUTTON_WIDTH_TINY = 80;
    
    // ========================================================================
    // SPACING CONSTANTS
    // ========================================================================
    
    /**
     * Standard margin for form edges (10px).
     */
    public static final int MARGIN_STANDARD = 10;
    
    /**
     * Large margin for significant spacing (20px).
     */
    public static final int MARGIN_LARGE = 20;
    
    /**
     * Small margin for tight layouts (5px).
     */
    public static final int MARGIN_SMALL = 5;
    
    /**
     * Standard vertical spacing between rows (30-35px).
     */
    public static final int ROW_SPACING_STANDARD = 35;
    
    /**
     * Compact vertical spacing for dense layouts (25px).
     */
    public static final int ROW_SPACING_COMPACT = 25;
    
    /**
     * Large vertical spacing for separated sections (45px).
     */
    public static final int ROW_SPACING_LARGE = 45;
    
    /**
     * Standard horizontal spacing between components (10px).
     */
    public static final int COMPONENT_SPACING = 10;
    
    // ========================================================================
    // COMMON DIMENSIONS
    // ========================================================================
    
    /**
     * Standard input field width (200px).
     */
    public static final int INPUT_WIDTH_STANDARD = 200;
    
    /**
     * Wide input field width (300px).
     */
    public static final int INPUT_WIDTH_WIDE = 300;
    
    /**
     * Narrow input field width (100px).
     */
    public static final int INPUT_WIDTH_NARROW = 100;
    
    /**
     * Standard form content width for scrollable content (typical 400-600px).
     */
    public static final int CONTENT_WIDTH_STANDARD = 500;
    
    // Private constructor to prevent instantiation
    private UIStyleConstants() {
        throw new AssertionError("UIStyleConstants should not be instantiated");
    }
}
