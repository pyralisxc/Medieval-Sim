package medievalsim.ui.components;

import medievalsim.util.Constants;
import necesse.engine.localization.Localization;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;

/**
 * Base class for form components that provides standardized UI element creation.
 * 
 * <p>Reduces code duplication by providing consistent helper methods for common
 * form patterns used throughout Medieval Sim.</p>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * public class MyCustomForm extends BaseForm {
 *     protected void buildUI() {
 *         int currentY = 50;
 *         
 *         // Use helpers for consistent UI
 *         FormLabel header = createSectionHeader("ui", "myheader", 10, currentY);
 *         currentY = nextRow(currentY);
 *         
 *         FormTextButton button = createStandardButton("ui", "mybutton", 
 *             10, currentY, () -> handleButtonClick());
 *     }
 * }
 * }</pre>
 * 
 * @since 1.0
 */
public abstract class BaseForm {
    
    /**
     * Standard font for section headers (size 16).
     */
    protected static final FontOptions HEADER_FONT = new FontOptions(16);
    
    /**
     * Standard font for body text (size 14).
     */
    protected static final FontOptions BODY_FONT = new FontOptions(14);
    
    /**
     * Standard font for small text/hints (size 12).
     */
    protected static final FontOptions SMALL_FONT = new FontOptions(12);
    
    // ===== LABEL CREATION =====
    
    /**
     * Create a section header label with standard styling.
     * 
     * @param locCategory Localization category
     * @param locKey Localization key
     * @param x X position
     * @param y Y position
     * @return Configured FormLabel for section header
     */
    protected FormLabel createSectionHeader(String locCategory, String locKey, int x, int y) {
        return new FormLabel(
            Localization.translate(locCategory, locKey), 
            HEADER_FONT, 
            -1, // center = -1
            x, 
            y
        );
    }
    
    /**
     * Create a standard body text label.
     * 
     * @param text Display text (can be localized string or plain text)
     * @param x X position
     * @param y Y position
     * @return Configured FormLabel for body text
     */
    protected FormLabel createBodyLabel(String text, int x, int y) {
        return new FormLabel(text, BODY_FONT, -1, x, y);
    }
    
    /**
     * Create a small hint/description label.
     * 
     * @param text Display text
     * @param x X position
     * @param y Y position
     * @return Configured FormLabel for small text
     */
    protected FormLabel createHintLabel(String text, int x, int y) {
        return new FormLabel(text, SMALL_FONT, -1, x, y);
    }
    
    /**
     * Create a feedback label for displaying status messages.
     * 
     * <p>Typically used for form validation or action results.</p>
     * 
     * @param x X position
     * @param y Y position
     * @return Empty FormLabel configured for feedback messages
     */
    protected FormLabel createFeedbackLabel(int x, int y) {
        return new FormLabel("", BODY_FONT, -1, x, y);
    }
    
    // ===== BUTTON CREATION =====
    
    /**
     * Create a standard-sized button with localization.
     * 
     * @param locCategory Localization category
     * @param locKey Localization key for button text
     * @param x X position
     * @param y Y position
     * @param color Button color scheme
     * @return Configured FormTextButton
     */
    protected FormTextButton createButton(String locCategory, String locKey, 
                                          int x, int y, ButtonColor color) {
        return new FormTextButton(
            Localization.translate(locCategory, locKey),
            x, y,
            Constants.UI.MIN_BUTTON_WIDTH,
            FormInputSize.SIZE_32,
            color
        );
    }
    
    /**
     * Create a standard-sized button with plain text.
     * 
     * @param text Button text
     * @param x X position
     * @param y Y position
     * @param color Button color scheme
     * @return Configured FormTextButton
     */
    protected FormTextButton createButton(String text, int x, int y, ButtonColor color) {
        return new FormTextButton(
            text,
            x, y,
            Constants.UI.MIN_BUTTON_WIDTH,
            FormInputSize.SIZE_32,
            color
        );
    }
    
    /**
     * Create a wide button for emphasized actions.
     * 
     * @param locCategory Localization category
     * @param locKey Localization key for button text
     * @param x X position
     * @param y Y position
     * @param color Button color scheme
     * @return Configured FormTextButton with wide width
     */
    protected FormTextButton createWideButton(String locCategory, String locKey, 
                                               int x, int y, ButtonColor color) {
        return new FormTextButton(
            Localization.translate(locCategory, locKey),
            x, y,
            Constants.UI.WIDE_BUTTON_WIDTH,
            FormInputSize.SIZE_32,
            color
        );
    }
    
    /**
     * Create a custom-sized button.
     * 
     * @param text Button text
     * @param x X position
     * @param y Y position
     * @param width Button width
     * @param height Button height (typically SIZE_24 or SIZE_32)
     * @param color Button color scheme
     * @return Configured FormTextButton
     */
    protected FormTextButton createCustomButton(String text, int x, int y, 
                                                 int width, FormInputSize height, ButtonColor color) {
        return new FormTextButton(text, x, y, width, height, color);
    }
    
    // ===== INPUT CREATION =====
    
    /**
     * Create a number input field with standard sizing.
     * 
     * @param x X position
     * @param y Y position
     * @param width Input width
     * @param maxLength Maximum character length
     * @return Configured FormTextInput for numeric input
     */
    protected FormTextInput createNumberInput(int x, int y, int width, int maxLength) {
        return new FormTextInput(x, y, FormInputSize.SIZE_32, width, maxLength);
    }
    
    /**
     * Create a text input field with standard sizing.
     * 
     * @param x X position
     * @param y Y position
     * @param width Input width
     * @param maxLength Maximum character length
     * @return Configured FormTextInput
     */
    protected FormTextInput createTextInput(int x, int y, int width, int maxLength) {
        return new FormTextInput(x, y, FormInputSize.SIZE_32, width, maxLength);
    }
    
    // ===== LAYOUT HELPERS =====
    
    /**
     * Add vertical spacing to current Y position.
     * 
     * @param currentY Current Y position
     * @param spacing Pixels to add
     * @return New Y position
     */
    protected int addVerticalSpacing(int currentY, int spacing) {
        return currentY + spacing;
    }
    
    /**
     * Move to next row using standard element spacing.
     * 
     * <p>Uses Constants.UI.ELEMENT_SPACING + Constants.UI.BUTTON_HEIGHT for consistent row advancement.</p>
     * 
     * @param currentY Current Y position
     * @return New Y position for next row
     */
    protected int nextRow(int currentY) {
        return currentY + Constants.UI.BUTTON_HEIGHT + Constants.UI.ELEMENT_SPACING;
    }
    
    /**
     * Move to next section with larger spacing.
     * 
     * <p>Uses Constants.UI.SECTION_SPACING for visual separation between sections.</p>
     * 
     * @param currentY Current Y position
     * @return New Y position for next section
     */
    protected int nextSection(int currentY) {
        return currentY + Constants.UI.SECTION_SPACING;
    }
    
    /**
     * Calculate centered X position for an element.
     * 
     * @param containerWidth Width of container
     * @param elementWidth Width of element to center
     * @return X position for centered element
     */
    protected int centerX(int containerWidth, int elementWidth) {
        return (containerWidth - elementWidth) / 2;
    }
}
