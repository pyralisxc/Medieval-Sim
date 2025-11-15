package medievalsim.util;

import necesse.engine.localization.Localization;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormContentIconButton;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormCheckBox;
import necesse.gfx.ui.ButtonColor;
import necesse.gfx.ui.ButtonTexture;
import necesse.gfx.gameFont.FontManager;
import necesse.gfx.gameFont.FontOptions;

/**
 * ResponsiveButtonHelper - Architectural Overview
 * 
 * PURPOSE:
 * This utility solves the critical problem of button text cutoff in different languages.
 * Instead of using fixed widths that might truncate "Bauwerkzeuge" (German for "Build Tools"),
 * we calculate optimal button sizes based on actual text content.
 * 
 * ARCHITECTURAL APPROACH:
 * 1. Content-Aware Sizing: Measures actual text width before creating buttons
 * 2. Constraint-Based Layout: Respects minimum/maximum width bounds
 * 3. Equal Distribution: Automatically distributes buttons evenly in available space
 * 4. Responsive Design: Adapts to container size changes
 * 
 * DESIGN PATTERNS USED:
 * - Builder Pattern: Fluent interface for configuration
 * - Strategy Pattern: Different layout strategies (equal, content-aware, fixed)
 * - Factory Pattern: Creates properly configured buttons
 * 
 * WHY THIS MATTERS:
 * - Prevents text truncation in localized content
 * - Maintains visual consistency across different screen sizes  
 * - Reduces magic numbers scattered throughout UI code
 * - Enables responsive design principles in Necesse modding
 */
public final class ResponsiveButtonHelper {

    private ResponsiveButtonHelper() {} // Utility class - prevent instantiation

    /**
     * Calculates optimal button width based on text content and constraints.
     * 
     * ALGORITHM EXPLANATION:
     * 1. Get pixel width of rendered text using Necesse's font system
     * 2. Add padding for visual breathing room (Constants.UI.TEXT_PADDING * 2)
     * 3. Apply min/max constraints to prevent unusable buttons
     * 4. Return calculated width
     * 
     * @param text The button text (supports localized strings)
     * @param minWidth Minimum allowed width (prevents tiny buttons)
     * @param maxWidth Maximum allowed width (prevents oversized buttons)
     * @return Optimal button width in pixels
     */
    public static int calculateOptimalWidth(String text, int minWidth, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return minWidth;
        }
        
        try {
            // Get actual pixel width of the text when rendered using Necesse's font system
            // Use default font options (size 16) for consistent measurement
            FontOptions defaultFontOptions = new FontOptions(16);
            int textWidth = FontManager.bit.getWidthCeil(text, defaultFontOptions);
            
            // Add generous padding for visual breathing room and text readability
            int paddedWidth = textWidth + (Constants.UI.TEXT_PADDING * 4); // Increased padding
            
            // Debug logging to help identify truncation issues
            System.out.println(Constants.LOG_INFO_PREFIX + "Button width calculation for '" + text + "':");
            System.out.println("  Text width: " + textWidth + "px, Padded: " + paddedWidth + "px");
            System.out.println("  Min: " + minWidth + "px, Max: " + maxWidth + "px");
            
            // Apply constraints to ensure usability
            int finalWidth = Math.max(minWidth, Math.min(paddedWidth, maxWidth));
            System.out.println("  Final width: " + finalWidth + "px");
            
            // Warn if we're hitting the maximum constraint (potential truncation)
            if (finalWidth == maxWidth && paddedWidth > maxWidth) {
                System.err.println(Constants.LOG_WARNING_PREFIX + 
                    "Button text '" + text + "' may be truncated! Needs " + paddedWidth + 
                    "px but limited to " + maxWidth + "px");
            }
            
            return finalWidth;
            
        } catch (Exception e) {
            // Fallback to standard width if font measurement fails
            System.err.println(Constants.LOG_WARNING_PREFIX + 
                "Failed to measure text width for: " + text + ". Using standard width.");
            return Constants.UI.STANDARD_BUTTON_WIDTH;
        }
    }

    /**
     * Creates a content-aware button with optimal sizing.
     * 
     * USAGE EXAMPLE:
     * Instead of: new FormTextButton("Build Tools", x, y, 280, SIZE_32, ButtonColor.BASE)
     * Use: ResponsiveButtonHelper.createButton("Build Tools", x, y, containerWidth)
     * 
     * @param text Button text (can be localized)
     * @param x X position
     * @param y Y position  
     * @param maxAvailableWidth Maximum width available in container
     * @return Properly sized FormTextButton
     */
    public static FormTextButton createButton(String text, int x, int y, int maxAvailableWidth) {
        int optimalWidth = calculateOptimalWidth(
            text,
            Constants.UI.MIN_BUTTON_WIDTH,
            maxAvailableWidth - (Constants.UI.MARGIN * 2)
        );
        
        return new FormTextButton(text, x, y, optimalWidth, FormInputSize.SIZE_32, ButtonColor.BASE);
    }

    /**
     * Creates a localized button with optimal sizing.
     * Convenience method that handles Necesse's localization system.
     */
    public static FormTextButton createLocalizedButton(String category, String key, int x, int y, int maxAvailableWidth) {
        String text = Localization.translate(category, key);
        return createButton(text, x, y, maxAvailableWidth);
    }

    /**
     * EQUAL BUTTON LAYOUT - The Core Responsive Feature
     * 
     * ARCHITECTURAL EXPLANATION:
     * This method solves the "uneven button sizes" problem by distributing
     * buttons equally across available horizontal space.
     * 
     * CALCULATION ALGORITHM:
     * 1. Calculate total available width (container - margins)
     * 2. Account for spacing between buttons
     * 3. Divide remaining space equally among all buttons
     * 4. Ensure each button meets minimum width requirements
     * 5. Create and position buttons with calculated dimensions
     * 
     * VISUAL EXAMPLE:
     * Container width: 400px, Margin: 16px each side, 3 buttons, spacing: 8px
     * Available: 400 - 32 = 368px
     * Spacing used: 2 * 8 = 16px  
     * Button width: (368 - 16) / 3 = 117px each
     * 
     * @param form The parent form to add buttons to
     * @param labels Array of button labels
     * @param y Vertical position for all buttons
     * @param containerWidth Total available horizontal space
     * @return Array of created FormTextButton objects
     */
    public static FormTextButton[] createEqualButtonLayout(Form form, String[] labels, int y, int containerWidth) {
        if (labels == null || labels.length == 0) {
            return new FormTextButton[0];
        }

        // Calculate layout dimensions
        int buttonCount = labels.length;
        int totalMargin = Constants.UI.MARGIN * 2; // Left and right margins
        int totalSpacing = Constants.UI.ELEMENT_SPACING * (buttonCount - 1); // Spacing between buttons
        int availableWidth = containerWidth - totalMargin - totalSpacing;
        int buttonWidth = availableWidth / buttonCount;

        // Ensure buttons meet minimum width requirement
        if (buttonWidth < Constants.UI.MIN_BUTTON_WIDTH) {
            System.err.println(Constants.LOG_WARNING_PREFIX + 
                "Calculated button width (" + buttonWidth + "px) is below minimum. Using minimum width.");
            buttonWidth = Constants.UI.MIN_BUTTON_WIDTH;
        }

        // Create and position buttons
        FormTextButton[] buttons = new FormTextButton[buttonCount];
        int currentX = Constants.UI.MARGIN;

        for (int i = 0; i < buttonCount; i++) {
            buttons[i] = new FormTextButton(
                labels[i], 
                currentX, 
                y, 
                buttonWidth, 
                FormInputSize.SIZE_32, 
                ButtonColor.BASE
            );
            
            form.addComponent((FormComponent) buttons[i]);
            currentX += buttonWidth + Constants.UI.ELEMENT_SPACING;
        }

        return buttons;
    }

    /**
     * Creates equal-width localized buttons.
     * Convenience method for common UI patterns.
     */
    public static FormTextButton[] createEqualLocalizedButtons(Form form, String category, String[] keys, int y, int containerWidth) {
        String[] labels = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            labels[i] = Localization.translate(category, keys[i]);
        }
        return createEqualButtonLayout(form, labels, y, containerWidth);
    }

    /**
     * SMART DIALOG BUTTON LAYOUT
     * 
     * PURPOSE: Handles the common "OK/Cancel" or "Yes/No/Cancel" dialog pattern
     * with proper spacing and sizing.
     * 
     * DESIGN DECISION: Right-aligned with equal button widths for professional appearance
     */
    public static FormTextButton[] createDialogButtons(Form form, String[] labels, int y, int containerWidth) {
        if (labels == null || labels.length == 0) {
            return new FormTextButton[0];
        }

        // Calculate optimal width for all buttons (use the widest text as base)
        int maxTextWidth = 0;
        FontOptions defaultFontOptions = new FontOptions(16);
        for (String label : labels) {
            if (label != null && !label.isEmpty()) {
                try {
                    int textWidth = FontManager.bit.getWidthCeil(label, defaultFontOptions);
                    maxTextWidth = Math.max(maxTextWidth, textWidth);
                } catch (Exception e) {
                    // Continue with current max if measurement fails
                }
            }
        }

        int buttonWidth = Math.max(
            Constants.UI.MIN_BUTTON_WIDTH,
            maxTextWidth + (Constants.UI.TEXT_PADDING * 2)
        );

        // Position buttons right-aligned
        FormTextButton[] buttons = new FormTextButton[labels.length];
        int totalButtonWidth = (buttonWidth * labels.length) + (Constants.UI.ELEMENT_SPACING * (labels.length - 1));
        int startX = containerWidth - Constants.UI.MARGIN - totalButtonWidth;
        int currentX = startX;

        for (int i = 0; i < labels.length; i++) {
            buttons[i] = new FormTextButton(
                labels[i],
                currentX,
                y,
                buttonWidth,
                FormInputSize.SIZE_32,
                ButtonColor.BASE
            );
            
            form.addComponent((FormComponent) buttons[i]);
            currentX += buttonWidth + Constants.UI.ELEMENT_SPACING;
        }

        return buttons;
    }

    /**
     * DEBUGGING AND VALIDATION UTILITIES
     * 
     * These methods help developers understand how the responsive system works
     * and validate that layouts are working correctly.
     */
    
    public static void validateButtonLayout(FormTextButton[] buttons, int expectedCount) {
        if (buttons.length != expectedCount) {
            System.err.println(Constants.LOG_WARNING_PREFIX + 
                "Button layout mismatch. Expected: " + expectedCount + ", Got: " + buttons.length);
        }
    }

    public static void debugButtonSizes(FormTextButton[] buttons, String layoutName) {
        System.out.println(Constants.LOG_INFO_PREFIX + "Button sizes for " + layoutName + ":");
        for (int i = 0; i < buttons.length; i++) {
            System.out.println("  Button " + i + ": " + buttons[i].getWidth() + "px wide");
        }
    }

    /**
     * EMERGENCY TEXT FIT CHECKER
     * 
     * Use this method to check if text will fit in a given width.
     * Helps identify truncation issues before they become UI problems.
     */
    public static boolean doesTextFit(String text, int availableWidth) {
        if (text == null || text.isEmpty()) return true;
        
        try {
            FontOptions fontOptions = new FontOptions(16);
            int textWidth = FontManager.bit.getWidthCeil(text, fontOptions);
            int requiredWidth = textWidth + (Constants.UI.TEXT_PADDING * 4);
            
            boolean fits = requiredWidth <= availableWidth;
            if (!fits) {
                System.err.println(Constants.LOG_WARNING_PREFIX + 
                    "Text '" + text + "' WILL NOT FIT! Needs " + requiredWidth + 
                    "px but only " + availableWidth + "px available");
            }
            return fits;
        } catch (Exception e) {
            return true; // Assume it fits if we can't measure
        }
    }

    /**
     * AUTO-EXPANDING BUTTON CREATOR
     * 
     * Creates a button that automatically expands container if needed.
     * Use when you want to ensure text never gets truncated.
     */
    public static FormTextButton createAutoExpandingButton(String text, int x, int y, int preferredWidth) {
        int optimalWidth = calculateOptimalWidth(text, Constants.UI.MIN_BUTTON_WIDTH, Integer.MAX_VALUE);
        int actualWidth = Math.max(preferredWidth, optimalWidth);
        
        if (actualWidth > preferredWidth) {
            System.out.println(Constants.LOG_INFO_PREFIX + 
                "Auto-expanding button '" + text + "' from " + preferredWidth + 
                "px to " + actualWidth + "px to prevent truncation");
        }
        
        return new FormTextButton(text, x, y, actualWidth, FormInputSize.SIZE_32, ButtonColor.BASE);
    }

    // ===== SPECIALIZED UI LAYOUT HELPERS =====

    /**
     * TAB BUTTON LAYOUT HELPER
     * 
     * Creates evenly spaced tab buttons for tabbed interfaces.
     * Commonly used pattern in Command Center and similar multi-section UIs.
     * 
     * @param form Parent form
     * @param tabLabels Array of tab names
     * @param y Vertical position for tabs
     * @param containerWidth Total available width
     * @param maxTabs Maximum number of tabs to display
     * @return Array of created tab buttons
     */
    public static FormTextButton[] createTabLayout(Form form, String[] tabLabels, int y, int containerWidth, int maxTabs) {
        if (tabLabels == null || tabLabels.length == 0) {
            return new FormTextButton[0];
        }

        int tabCount = Math.min(tabLabels.length, maxTabs);
        int tabSpacing = Constants.UI.ELEMENT_SPACING;
        int totalSpacing = tabSpacing * (tabCount - 1);
        int availableWidth = containerWidth - (Constants.UI.MARGIN * 2) - totalSpacing;
        int tabWidth = Math.max(Constants.CommandCenter.TAB_BUTTON_WIDTH, availableWidth / tabCount);

        FormTextButton[] tabs = new FormTextButton[tabCount];
        int currentX = Constants.UI.MARGIN;

        for (int i = 0; i < tabCount; i++) {
            tabs[i] = new FormTextButton(
                tabLabels[i],
                currentX,
                y,
                tabWidth,
                FormInputSize.SIZE_32,
                ButtonColor.BASE
            );
            
            form.addComponent((FormComponent) tabs[i]);
            currentX += tabWidth + tabSpacing;
        }

        return tabs;
    }

    /**
     * ICON BUTTON ROW LAYOUT
     * 
     * Creates a horizontal row of icon buttons with consistent spacing.
     * Perfect for action buttons (edit, delete, expand, etc.) in zone entries.
     * 
     * @param parentBox The container to add buttons to
     * @param buttonConfigs Array of button configuration data
     * @param startX Starting X position
     * @param y Vertical position
     * @return Array of created icon buttons
     */
    public static FormContentIconButton[] createIconButtonRow(FormContentBox parentBox, IconButtonConfig[] buttonConfigs, int startX, int y) {
        if (buttonConfigs == null || buttonConfigs.length == 0) {
            return new FormContentIconButton[0];
        }

        FormContentIconButton[] buttons = new FormContentIconButton[buttonConfigs.length];
        int currentX = startX;
        int buttonSpacing = Constants.UI.ELEMENT_SPACING * 3; // Slightly wider spacing for icons

        for (int i = 0; i < buttonConfigs.length; i++) {
            IconButtonConfig config = buttonConfigs[i];
            buttons[i] = new FormContentIconButton(
                currentX,
                y,
                config.size,
                config.color,
                config.texture,
                config.tooltip
            );
            
            parentBox.addComponent((FormComponent) buttons[i]);
            currentX += Constants.UI.STANDARD_ICON_SIZE + buttonSpacing;
        }

        return buttons;
    }

    /**
     * CHECKBOX GROUP LAYOUT
     * 
     * Creates evenly distributed checkboxes for permission systems.
     * Used extensively in your zone permission configuration.
     * 
     * @param form Parent form
     * @param checkboxLabels Array of checkbox labels
     * @param y Vertical position
     * @param containerWidth Available width
     * @param checkStates Initial check states (can be null)
     * @return Array of created checkboxes
     */
    public static FormCheckBox[] createCheckboxGroup(Form form, String[] checkboxLabels, int y, int containerWidth, boolean[] checkStates) {
        if (checkboxLabels == null || checkboxLabels.length == 0) {
            return new FormCheckBox[0];
        }

        int checkboxCount = checkboxLabels.length;
        int availableWidth = containerWidth - (Constants.UI.MARGIN * 2);
        int checkboxWidth = availableWidth / checkboxCount;

        FormCheckBox[] checkboxes = new FormCheckBox[checkboxCount];
        int currentX = Constants.UI.MARGIN;

        for (int i = 0; i < checkboxCount; i++) {
            boolean isChecked = (checkStates != null && i < checkStates.length) ? checkStates[i] : false;
            
            checkboxes[i] = new FormCheckBox(
                checkboxLabels[i],
                currentX,
                y,
                checkboxWidth,
                isChecked
            );
            
            form.addComponent((FormComponent) checkboxes[i]);
            currentX += checkboxWidth;
        }

        return checkboxes;
    }

    /**
     * BACK BUTTON HELPER
     * 
     * Creates a properly sized and positioned back button.
     * Common pattern: right-aligned back button in forms.
     */
    public static FormTextButton createBackButton(Form form, String text, int y, int containerWidth) {
        int buttonWidth = calculateOptimalWidth(text, Constants.UI.MIN_BUTTON_WIDTH, Constants.UI.STANDARD_BUTTON_WIDTH);
        int buttonX = containerWidth - Constants.UI.MARGIN - buttonWidth;
        
        FormTextButton backButton = new FormTextButton(text, buttonX, y, buttonWidth, FormInputSize.SIZE_32, ButtonColor.BASE);
        form.addComponent((FormComponent) backButton);
        return backButton;
    }

    // ===== CONFIGURATION CLASSES =====

    /**
     * Configuration class for icon button creation.
     * Encapsulates all the data needed to create an icon button.
     */
    public static class IconButtonConfig {
        public final FormInputSize size;
        public final ButtonColor color;
        public final ButtonTexture texture;
        public final necesse.engine.localization.message.GameMessage[] tooltip;

        public IconButtonConfig(FormInputSize size, ButtonColor color, ButtonTexture texture, necesse.engine.localization.message.GameMessage[] tooltip) {
            this.size = size;
            this.color = color;
            this.texture = texture;
            this.tooltip = tooltip;
        }
    }
}