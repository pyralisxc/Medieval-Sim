package medievalsim.ui.helpers;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import medievalsim.commandcenter.wrapper.widgets.ParameterWidget;
import medievalsim.commandcenter.wrapper.widgets.ParameterWidgetFactory;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.gameFont.FontOptions;

import java.awt.Color;
import java.util.List;
import java.util.Map;

/**
 * Helper class for building parameter input forms.
 * Extracted from ConsoleCommandsTab to improve maintainability and separation of concerns.
 * 
 * Responsibilities:
 * - Layout and positioning of parameter widgets
 * - Creating labels and hints for parameters
 * - Restoring cached parameter values
 * - Generating context-aware hint text
 */
public class ParameterFormBuilder {

    // Font options
    private static final FontOptions WHITE_TEXT_14 = new FontOptions(14).color(Color.WHITE);
    private static final FontOptions WHITE_TEXT_11 = new FontOptions(11).color(Color.WHITE);

    // Layout constants
    private static final int WIDGET_X = 10;
    private static final int LABEL_HEIGHT = 20;
    private static final int HINT_HEIGHT = 28;
    private static final int WIDGET_HEIGHT = 45;

    private final Client client;

    public ParameterFormBuilder(Client client) {
        this.client = client;
    }

    /**
     * Build parameter widgets into a scroll area.
     * 
     * @param params List of parameter metadata
     * @param parentForm The parent form (needed for dropdown menus)
     * @param parameterScrollArea The scroll area to add widgets to
     * @param currentParameterWidgets List to collect created widgets
     * @param commandParameterCache Cache for restoring previous values
     * @param commandId Current command ID for cache lookup
     * @param onValueChangedCallback Callback when widget value changes
     * @return The final Y position after all widgets
     */
    public int buildParameterWidgets(
            List<ParameterMetadata> params,
            Form parentForm,
            FormContentBox parameterScrollArea,
            List<ParameterWidget> currentParameterWidgets,
            Map<String, Map<String, String>> commandParameterCache,
            String commandId,
            Runnable onValueChangedCallback) {

        int yPos = 10;
        int widgetWidth = parameterScrollArea.getWidth() - 20;

        for (ParameterMetadata param : params) {
            if (!param.isPartOfUsage()) {
                continue;
            }

            // Parameter label
            yPos = addParameterLabel(parameterScrollArea, param, yPos, widgetWidth);

            // Parameter hint
            yPos = addParameterHint(parameterScrollArea, param, yPos, widgetWidth);

            // Create and configure widget
            ParameterWidget widget = createAndConfigureWidget(
                param, 
                yPos, 
                commandId,
                commandParameterCache,
                onValueChangedCallback
            );

            // Add widget to scroll area and tracking list
            parameterScrollArea.addComponent(widget.getComponent());
            currentParameterWidgets.add(widget);
            
            // Handle multi-component widgets
            if (widget instanceof medievalsim.commandcenter.wrapper.widgets.PlayerDropdownWidget) {
                // PlayerDropdownWidget has both text input and dropdown
                medievalsim.commandcenter.wrapper.widgets.PlayerDropdownWidget playerWidget = 
                    (medievalsim.commandcenter.wrapper.widgets.PlayerDropdownWidget) widget;
                parameterScrollArea.addComponent(playerWidget.getDropdown());
                yPos += WIDGET_HEIGHT; // Extra space for dropdown
            } else if (widget instanceof medievalsim.commandcenter.wrapper.widgets.MultiChoiceWidget) {
                // MultiChoiceWidget has type selector dropdown + currently selected sub-widget
                // Note: Only the SELECTED sub-widget should be displayed, not all options
                medievalsim.commandcenter.wrapper.widgets.MultiChoiceWidget multiWidget = 
                    (medievalsim.commandcenter.wrapper.widgets.MultiChoiceWidget) widget;
                
                // CRITICAL: Set parent form so the widget can dynamically swap sub-widgets
                multiWidget.setParentForm(parameterScrollArea);
                
                FormComponent[] selectedSubComponents = multiWidget.getAllSelectedSubComponents();
                if (selectedSubComponents != null) {
                    for (FormComponent subComponent : selectedSubComponents) {
                        if (subComponent != null) {
                            parameterScrollArea.addComponent(subComponent);
                        }
                    }
                    // Add extra vertical space for the sub-widget(s)
                    yPos += WIDGET_HEIGHT * selectedSubComponents.length;
                }
            }

            yPos += WIDGET_HEIGHT;
        }

        return yPos;
    }

    /**
     * Add parameter label to the form
     */
    private int addParameterLabel(FormContentBox scrollArea, ParameterMetadata param, int yPos, int width) {
        String labelText = param.getDisplayName() + (param.isRequired() ? " (Required)" : " (Optional)");
        FormLabel paramLabel = new FormLabel(
            labelText,
            WHITE_TEXT_14,
            FormLabel.ALIGN_LEFT,
            WIDGET_X, yPos, width
        );
        scrollArea.addComponent(paramLabel);
        return yPos + LABEL_HEIGHT;
    }

    /**
     * Add parameter hint text to the form
     */
    private int addParameterHint(FormContentBox scrollArea, ParameterMetadata param, int yPos, int width) {
        FormLabel hintLabel = new FormLabel(
            getParameterHint(param),
            WHITE_TEXT_11,
            FormLabel.ALIGN_LEFT,
            WIDGET_X, yPos, width
        );
        scrollArea.addComponent(hintLabel);
        return yPos + HINT_HEIGHT;
    }

    /**
     * Create widget and configure it with cached values and callbacks
     */
    private ParameterWidget createAndConfigureWidget(
            ParameterMetadata param,
            int yPos,
            String commandId,
            Map<String, Map<String, String>> commandParameterCache,
            Runnable onValueChangedCallback) {

        // Create widget via factory
        ParameterWidget widget = ParameterWidgetFactory.createWidget(
            param, WIDGET_X, yPos, client, commandId
        );

        // Set up value change callback
        widget.setOnValueChanged(() -> {
            widget.validate();
            if (onValueChangedCallback != null) {
                onValueChangedCallback.run();
            }
        });

        // Restore cached value if available
        Map<String, String> cachedValues = commandParameterCache.get(commandId);
        if (cachedValues != null && cachedValues.containsKey(param.getName())) {
            widget.setValue(cachedValues.get(param.getName()));
        }

        return widget;
    }

    /**
     * Get context-aware hint text for a parameter based on its type.
     * Provides user-friendly guidance on what input is expected.
     */
    public String getParameterHint(ParameterMetadata param) {
        switch (param.getHandlerType()) {
            case SERVER_CLIENT:
                return "Select a player or enter username (use 'self' for yourself)";
            
            case INT:
                return param.isOptional() ?
                    "Enter a whole number (e.g., 100) - Leave empty to use default" :
                    "Enter a whole number (e.g., 100) - Required";
            
            case BOOL:
                return "Click checkbox to enable, uncheck to disable";
            
            case STRING:
                return param.isOptional() ? 
                    "Enter text - Leave empty to use default" : 
                    "Enter text - Required";
            
            default:
                return param.isOptional() ?
                    "Enter a value - Leave empty to use default" :
                    "Enter a value - Required";
        }
    }

    /**
     * Calculate the required content height for a parameter list
     */
    public int calculateContentHeight(List<ParameterMetadata> params, int minHeight) {
        if (params == null || params.isEmpty()) {
            return minHeight;
        }

        int paramCount = 0;
        for (ParameterMetadata param : params) {
            if (param.isPartOfUsage()) {
                paramCount++;
            }
        }

        int calculatedHeight = paramCount * (LABEL_HEIGHT + HINT_HEIGHT + WIDGET_HEIGHT) + 20;
        return Math.max(calculatedHeight, minHeight);
    }
}
