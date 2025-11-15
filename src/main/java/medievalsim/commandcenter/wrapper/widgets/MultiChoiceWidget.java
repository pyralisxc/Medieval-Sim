package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import necesse.engine.commands.parameterHandlers.MultiParameterHandler;
import necesse.engine.commands.parameterHandlers.ParameterHandler;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormDropdownSelectionButton;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.ui.ButtonColor;
import necesse.engine.localization.message.StaticMessage;

import java.lang.reflect.Field;

/**
 * Widget for MULTI parameter type (OR logic - multiple handler options).
 * 
 * Uses reflection to inspect MultiParameterHandler and extract sub-handlers.
 * Provides a dropdown to select which handler type to use, then shows appropriate widget.
 * 
 * Example: "player OR preset location"
 *   - User selects "Player" → Shows PlayerDropdownWidget
 *   - User selects "Preset" → Shows PresetDropdownWidget
 */
public class MultiChoiceWidget extends ParameterWidget {
    
    private FormDropdownSelectionButton<Integer> choiceDropdown;
    private ParameterHandler<?>[] handlers;
    private ParameterWidget[] subWidgets;
    private int selectedIndex;
    private int previousSelectedIndex;
    private necesse.gfx.forms.components.FormContentBox parentForm; // Reference to parent form for dynamic updates
    private FormComponent currentSubComponent; // Primary visible sub-component (for simple widgets)

    // When a sub-widget has multiple components (like PlayerDropdownWidget), we
    // track them separately so we can add/remove them correctly from the parent
    // form without hardcoding any command-specific behavior.
    private FormComponent[] currentSubComponents;
    
    private static final int DROPDOWN_WIDTH = 180;
    private static final int SUB_WIDGET_Y_OFFSET = 30; // Space below dropdown for sub-widget
    
    /**
     * Constructor
     * 
     * @param parameter Parameter metadata
     * @param x X position
     * @param y Y position
     * @param client Client instance (needed for some sub-widgets)
     */
    public MultiChoiceWidget(ParameterMetadata parameter, int x, int y, Client client) {
        super(parameter);
        
        this.selectedIndex = 0;
        this.previousSelectedIndex = 0;
        this.parentForm = null; // Will be set later via setParentForm()
        this.currentSubComponent = null;
        this.currentSubComponents = null;
        
        // Extract handlers from MultiParameterHandler using reflection
        this.handlers = extractHandlers(parameter);
        
        // Create dropdown for handler selection
        choiceDropdown = new FormDropdownSelectionButton<>(
            x, y,
            FormInputSize.SIZE_16,
            ButtonColor.BASE,
            DROPDOWN_WIDTH,
            new StaticMessage("Select Type")
        );
        
        // Populate dropdown with handler options
        for (int i = 0; i < handlers.length; i++) {
            String handlerName = getHandlerDisplayName(handlers[i]);
            choiceDropdown.options.add(i, new StaticMessage(handlerName));
        }
        
        // Select first option by default
        if (handlers.length > 0) {
            choiceDropdown.setSelected(0, new StaticMessage(getHandlerDisplayName(handlers[0])));
        }
        
        // Create sub-widgets for each handler (positioned below dropdown)
        subWidgets = new ParameterWidget[handlers.length];
        for (int i = 0; i < handlers.length; i++) {
            // Create a temporary ParameterMetadata for the sub-handler
            ParameterMetadata subParam = createSubParameterMetadata(parameter, handlers[i]);
            // Create widget at position below dropdown (y + SUB_WIDGET_Y_OFFSET)
            subWidgets[i] = ParameterWidgetFactory.createWidget(subParam, x, y + SUB_WIDGET_Y_OFFSET, client);
        }
        
        // Store initial sub-component (first option selected by default)
        if (subWidgets.length > 0) {
            currentSubComponent = subWidgets[0].getComponent();
        }
    }
    
    /**
     * Extract handlers array from MultiParameterHandler using reflection
     */
    private ParameterHandler<?>[] extractHandlers(ParameterMetadata parameter) {
        try {
            Object handler = parameter.getHandler();
            
            if (handler instanceof MultiParameterHandler) {
                // Use reflection to access the private 'handlers' field
                Field handlersField = MultiParameterHandler.class.getDeclaredField("handlers");
                handlersField.setAccessible(true);
                return (ParameterHandler<?>[]) handlersField.get(handler);
            }
            
            // Fallback: single handler
            return new ParameterHandler<?>[] { parameter.getHandler() };
            
        } catch (Exception e) {
            System.err.println("Failed to extract handlers from MultiParameterHandler: " + e.getMessage());
            e.printStackTrace();
            // Fallback to single handler
            return new ParameterHandler<?>[] { parameter.getHandler() };
        }
    }
    
    /**
     * Get display name for a handler (based on class name)
     */
    private String getHandlerDisplayName(ParameterHandler<?> handler) {
        String className = handler.getClass().getSimpleName();
        
        // Remove "ParameterHandler" suffix if present
        if (className.endsWith("ParameterHandler")) {
            className = className.substring(0, className.length() - "ParameterHandler".length());
        }
        
        // Add spaces before capital letters (camelCase → Camel Case)
        String spaced = className.replaceAll("([A-Z])", " $1").trim();
        
        return spaced;
    }
    
    /**
     * Create a sub-parameter metadata for a specific handler
     */
    private ParameterMetadata createSubParameterMetadata(ParameterMetadata parent, ParameterHandler<?> handler) {
        // Create a new ParameterMetadata with the sub-handler.
        // These synthetic entries are not backed by a real CmdParameter, so sourceParameter = null.
        return new ParameterMetadata(
            parent.getName(),
            parent.isOptional(),
            parent.isRequired(), // partOfUsage
            handler,
            ParameterMetadata.determineHandlerType(handler),
            new ParameterMetadata[0], // No extra params for sub-handlers
            null
        );
    }

    /**
     * Set the parent form reference for dynamic widget swapping.
     * MUST be called after construction and before user interaction.
     * 
     * @param form The parent FormContentBox (typically the parameter scroll area)
     */
    public void setParentForm(necesse.gfx.forms.components.FormContentBox form) {
        this.parentForm = form;
    }
    
    /**
     * Check if the dropdown selection has changed and swap sub-widget if needed.
     * Should be called from tick() or similar periodic update method.
     * 
     * @return true if selection changed and widgets were swapped
     */
    public boolean updateSelectionIfChanged() {
        Integer currentSelection = choiceDropdown.getSelected();
        if (currentSelection == null) {
            return false;
        }
        
        int newIndex = currentSelection;
        
        // Check if selection changed
        if (newIndex != previousSelectedIndex) {
            swapSubWidget(previousSelectedIndex, newIndex);
            previousSelectedIndex = newIndex;
            selectedIndex = newIndex;
            return true;
        }
        
        return false;
    }
    
    /**
     * Swap from old sub-widget to new sub-widget.
     * Removes old component from parent form and adds new one.
     * 
     * @param oldIndex Index of previous selection
     * @param newIndex Index of new selection
     */
    private void swapSubWidget(int oldIndex, int newIndex) {
        if (parentForm == null) {
            System.err.println("[MultiChoiceWidget] Cannot swap sub-widget - parentForm is null! Call setParentForm() first.");
            return;
        }
        
        // Remove old sub-widget component(s)
        if (oldIndex >= 0 && oldIndex < subWidgets.length) {
            try {
                // If we previously added multiple components for this widget
                // (for example, PlayerDropdownWidget's text input + dropdown),
                // remove them all. Otherwise, remove the single component.
                if (currentSubComponents != null) {
                    for (FormComponent comp : currentSubComponents) {
                        if (comp != null) {
                            parentForm.removeComponent(comp);
                        }
                    }
                } else if (currentSubComponent != null) {
                    parentForm.removeComponent(currentSubComponent);
                }
            } catch (Exception e) {
                System.err.println("[MultiChoiceWidget] Failed to remove old sub-component: " + e.getMessage());
            }
        }
        
        // Clear old sub-widget value (Option A: fresh start)
        if (oldIndex >= 0 && oldIndex < subWidgets.length) {
            subWidgets[oldIndex].reset();
        }
        
        // Add new sub-widget component(s)
        currentSubComponents = null;
        if (newIndex >= 0 && newIndex < subWidgets.length) {
            ParameterWidget newWidget = subWidgets[newIndex];

            try {
                if (newWidget instanceof PlayerDropdownWidget) {
                    // PlayerDropdownWidget has a text input + dropdown stacked vertically
                    PlayerDropdownWidget playerWidget = (PlayerDropdownWidget) newWidget;
                    FormComponent textInput = playerWidget.getTextInput();
                    FormComponent dropdown = playerWidget.getDropdown();
                    parentForm.addComponent(textInput);
                    parentForm.addComponent(dropdown);
                    currentSubComponents = new FormComponent[] { textInput, dropdown };
                    currentSubComponent = textInput;
                } else if (newWidget instanceof RelativeIntInputWidget) {
                    // RelativeIntInputWidget manages multiple internal components as well,
                    // but exposes them through its primary component. For now we treat it
                    // as a single component here.
                    currentSubComponent = newWidget.getComponent();
                    parentForm.addComponent(currentSubComponent);
                } else {
                    currentSubComponent = newWidget.getComponent();
                    parentForm.addComponent(currentSubComponent);
                }
            } catch (Exception e) {
                System.err.println("[MultiChoiceWidget] Failed to add new sub-component: " + e.getMessage());
            }
        }
    }
    
    @Override
    public FormComponent getComponent() {
        return choiceDropdown;
    }
    
    /**
     * Get the FormComponent of the currently selected sub-widget.
     * This should be added to the UI below the dropdown.
     * 
     * @return The selected sub-widget's component, or null if none selected
     */
    public FormComponent getSelectedSubComponent() {
        Integer selected = choiceDropdown.getSelected();
        if (selected != null && selected >= 0 && selected < subWidgets.length) {
            selectedIndex = selected;
            ParameterWidget widget = subWidgets[selectedIndex];
            if (widget instanceof PlayerDropdownWidget) {
                // Return the primary text input as the representative component; the
                // dropdown will be added separately via swapSubWidget/initial wiring.
                return ((PlayerDropdownWidget) widget).getTextInput();
            }
            return widget.getComponent();
        }
        // Default to first widget
        if (subWidgets.length > 0) {
            ParameterWidget widget = subWidgets[0];
            if (widget instanceof PlayerDropdownWidget) {
                return ((PlayerDropdownWidget) widget).getTextInput();
            }
            return widget.getComponent();
        }
        return null;
    }
    
    /**
     * Get the index of the currently selected option
     */
    public int getSelectedIndex() {
        Integer selected = choiceDropdown.getSelected();
        if (selected != null && selected >= 0 && selected < subWidgets.length) {
            return selected;
        }
        return 0;
    }
    
    /**
     * Get the currently selected sub-widget
     */
    public ParameterWidget getSelectedWidget() {
        Integer selected = choiceDropdown.getSelected();
        if (selected != null && selected >= 0 && selected < subWidgets.length) {
            selectedIndex = selected;
            return subWidgets[selectedIndex];
        }
        return subWidgets[0]; // Default to first
    }
    
    /**
     * Get all sub-widgets (for adding to form)
     */
    public ParameterWidget[] getSubWidgets() {
        return subWidgets;
    }
    
    @Override
    public String getValue() {
        // Return value from currently selected sub-widget
        ParameterWidget selectedWidget = getSelectedWidget();
        return selectedWidget != null ? selectedWidget.getValue() : "";
    }
    
    @Override
    public void setValue(String value) {
        // Try to set value in all sub-widgets (the correct one will accept it)
        for (ParameterWidget widget : subWidgets) {
            try {
                widget.setValue(value);
            } catch (Exception e) {
                // Ignore - widget doesn't accept this value type
            }
        }
    }
    
    @Override
    protected boolean validateValue() {
        // Validate currently selected sub-widget
        ParameterWidget selectedWidget = getSelectedWidget();
        if (selectedWidget != null) {
            boolean valid = selectedWidget.validate();
            if (!valid) {
                validationError = selectedWidget.validationError;
            }
            return valid;
        }
        return !parameter.isRequired();
    }
    
    @Override
    public void reset() {
        // Reset all sub-widgets
        for (ParameterWidget widget : subWidgets) {
            widget.reset();
        }
        // Reset to first option
        if (handlers.length > 0) {
            choiceDropdown.setSelected(0, new StaticMessage(getHandlerDisplayName(handlers[0])));
            selectedIndex = 0;
        }
        isValid = parameter.isOptional();
        validationError = null;
    }
    
    @Override
    public void onFocus() {
        // Focus on selected sub-widget
        ParameterWidget selectedWidget = getSelectedWidget();
        if (selectedWidget != null) {
            selectedWidget.onFocus();
        }
    }
    
    @Override
    public void onBlur() {
        // Validate selected sub-widget
        validate();
    }
}
