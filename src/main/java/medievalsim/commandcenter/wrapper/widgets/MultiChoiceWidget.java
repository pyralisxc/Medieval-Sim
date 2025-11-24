package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import medievalsim.util.ModLogger;
import necesse.engine.commands.parameterHandlers.ParameterHandler;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormDropdownSelectionButton;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.ui.ButtonColor;
import necesse.engine.localization.message.StaticMessage;

/**
 * Widget for MULTI parameter type (OR logic - multiple handler options).
 * 
 * Uses reflection to inspect MultiParameterHandler and extract sub-handlers.
 * Provides a dropdown to select which handler type to use, then shows appropriate widget.
 * 
 * Two common patterns:
 * 
 * Pattern 1: ALTERNATE INPUTS (same data, different input methods)
 *   Example: "player OR preset location"
 *   - User selects "Player Name" → Shows PlayerDropdownWidget
 *   - User selects "Preset" → Shows PresetDropdownWidget
 *   Both represent the same logical parameter, just different ways to specify it.
 * 
 * Pattern 2: COMMAND BRANCHING (different actions)
 *   Example: "'list' OR set penalty"
 *   - User selects "'list'" → Auto-fills preset, executes list action
 *   - User selects "Set Death Penalty" → Shows enum dropdown, sets penalty
 *   These are different command paths, not different ways to specify same data.
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
        
        // Use cached handlers from ParameterMetadata (no reflection needed)
        ParameterHandler<?>[] cachedHandlers = parameter.getMultiHandlers();
        this.handlers = (cachedHandlers != null) ? cachedHandlers : new ParameterHandler<?>[] { parameter.getHandler() };
        
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
        
        // Store initial sub-component (first option selected by default).
        // For multi-component widgets like PlayerDropdownWidget we mirror the
        // logic used in getSelectedSubComponent so that the initial render has
        // all expected child components wired up before the first tick.
        if (subWidgets.length > 0) {
            ParameterWidget widget = subWidgets[0];
            if (widget instanceof PlayerDropdownWidget) {
                PlayerDropdownWidget playerWidget = (PlayerDropdownWidget) widget;
                currentSubComponents = new FormComponent[] {
                    playerWidget.getTextInput(),
                    playerWidget.getDropdown()
                };
                currentSubComponent = playerWidget.getTextInput();
            } else {
                currentSubComponent = widget.getComponent();
            }
        }
    }
    
    /**
     * Check if a handler is a single-preset handler that doesn't need user input.
     * For these handlers, we can auto-fill the value and skip showing a sub-widget.
     */
    private boolean isSinglePresetHandler(ParameterHandler<?> handler) {
        String className = handler.getClass().getSimpleName();
        if ("PresetStringParameterHandler".equals(className)) {
            try {
                java.lang.reflect.Field presetsField = handler.getClass().getDeclaredField("presets");
                presetsField.setAccessible(true);
                String[] presets = (String[]) presetsField.get(handler);
                return presets != null && presets.length == 1;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
    
    /**
     * Get the single preset value from a PresetStringParameterHandler.
     * Only call this after confirming isSinglePresetHandler() returns true.
     */
    private String getSinglePresetValue(ParameterHandler<?> handler) {
        try {
            java.lang.reflect.Field presetsField = handler.getClass().getDeclaredField("presets");
            presetsField.setAccessible(true);
            String[] presets = (String[]) presetsField.get(handler);
            if (presets != null && presets.length == 1) {
                return presets[0];
            }
        } catch (Exception e) {
            ModLogger.error("MultiChoiceWidget: Could not extract single preset: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get display name for a handler showing the actual choice/action available
     * rather than the technical handler type.
     */
    private String getHandlerDisplayName(ParameterHandler<?> handler) {
        String className = handler.getClass().getSimpleName();

        // For PresetStringParameterHandler, show the actual preset values as the choice
        if ("PresetStringParameterHandler".equals(className)) {
            try {
                java.lang.reflect.Field presetsField = handler.getClass().getDeclaredField("presets");
                presetsField.setAccessible(true);
                String[] presets = (String[]) presetsField.get(handler);
                
                if (presets != null && presets.length > 0) {
                    // If single preset, show it as the action: "'list'"
                    if (presets.length == 1) {
                        return "'" + presets[0] + "'";
                    }
                    // Multiple presets: show as choices
                    return "Choose: " + String.join(" / ", presets);
                }
            } catch (Exception e) {
                ModLogger.error("MultiChoiceWidget: Could not extract presets: " + e.getMessage());
            }
            return "Preset Value";
        }

        // For EnumParameterHandler, extract the enum type and format it nicely
        if ("EnumParameterHandler".equals(className)) {
            try {
                java.lang.reflect.Field valuesField = handler.getClass().getDeclaredField("values");
                valuesField.setAccessible(true);
                Enum<?>[] values = (Enum<?>[]) valuesField.get(handler);
                
                if (values != null && values.length > 0) {
                    // Get enum class name and format it
                    String enumClassName = values[0].getClass().getSimpleName();
                    // Remove common prefixes/suffixes
                    enumClassName = enumClassName.replace("Game", "").replace("Type", "");
                    // Convert camelCase to spaced: "DeathPenalty" -> "Death Penalty"
                    String formatted = enumClassName.replaceAll("([A-Z])", " $1").trim();
                    return "Set " + formatted;
                }
            } catch (Exception e) {
                ModLogger.error("MultiChoiceWidget: Could not extract enum type: " + e.getMessage());
            }
            return "Enum Value";
        }

        // For ServerClientParameterHandler, this is selecting a player
        if ("ServerClientParameterHandler".equals(className)) {
            return "Player Name";
        }

        // For basic string input
        if ("StringParameterHandler".equals(className)) {
            return "Text Input";
        }

        // Remove "ParameterHandler" suffix if present
        if (className.endsWith("ParameterHandler")) {
            className = className.substring(0, className.length() - "ParameterHandler".length());
        }

        // Add spaces before capital letters (camelCase 9 Camel Case)
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
        if (this.parentForm == null) {
            medievalsim.util.ModLogger.error("MultiChoiceWidget: Cannot swap sub-widget - parentForm is null! Call setParentForm() first.");
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
                medievalsim.util.ModLogger.error("MultiChoiceWidget: Failed to remove old sub-component: %s", e.getMessage());
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
                medievalsim.util.ModLogger.error("MultiChoiceWidget: Failed to add new sub-component: %s", e.getMessage());
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
            // For initial render, mirror the behavior used in swapSubWidget: if the
            // selected widget is a PlayerDropdownWidget, return its primary text
            // input component. CommandCenterPanel already has special logic to add
            // both the text input and dropdown when it detects a PlayerDropdownWidget
            // at the top level, and swapSubWidget handles multi-component cases
            // when selection changes.
            if (widget instanceof PlayerDropdownWidget) {
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
     * Get all FormComponents for the currently selected sub-widget.
     * For PlayerDropdownWidget, returns both text input and dropdown.
     * For other widgets, returns single-element array.
     * 
     * @return Array of components, or null if none selected
     */
    public FormComponent[] getAllSelectedSubComponents() {
        Integer selected = choiceDropdown.getSelected();
        if (selected != null && selected >= 0 && selected < subWidgets.length) {
            selectedIndex = selected;
            
            // Optimization: Skip rendering sub-widget for single-preset handlers
            // The value is auto-filled, no user input needed
            if (isSinglePresetHandler(handlers[selectedIndex])) {
                return new FormComponent[0]; // Return empty array, no components to display
            }
            
            ParameterWidget widget = subWidgets[selectedIndex];
            if (widget instanceof PlayerDropdownWidget) {
                PlayerDropdownWidget playerWidget = (PlayerDropdownWidget) widget;
                return new FormComponent[] {
                    playerWidget.getTextInput(),
                    playerWidget.getDropdown()
                };
            }
            return new FormComponent[] { widget.getComponent() };
        }
        // Default to first widget
        if (subWidgets.length > 0) {
            // Check if first handler is single-preset
            if (isSinglePresetHandler(handlers[0])) {
                return new FormComponent[0];
            }
            
            ParameterWidget widget = subWidgets[0];
            if (widget instanceof PlayerDropdownWidget) {
                PlayerDropdownWidget playerWidget = (PlayerDropdownWidget) widget;
                return new FormComponent[] {
                    playerWidget.getTextInput(),
                    playerWidget.getDropdown()
                };
            }
            return new FormComponent[] { widget.getComponent() };
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
        // Optimization: For single-preset handlers, return the preset directly
        // without needing to query the sub-widget
        int index = getSelectedIndex();
        if (index >= 0 && index < handlers.length) {
            ParameterHandler<?> selectedHandler = handlers[index];
            if (isSinglePresetHandler(selectedHandler)) {
                String presetValue = getSinglePresetValue(selectedHandler);
                if (presetValue != null) {
                    return presetValue;
                }
            }
        }
        
        // Normal case: Return value from currently selected sub-widget
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
