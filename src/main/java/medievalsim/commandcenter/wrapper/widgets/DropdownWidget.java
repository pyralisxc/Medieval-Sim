package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import necesse.gfx.forms.components.FormDropdownSelectionButton;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.ui.ButtonColor;
import necesse.engine.localization.message.StaticMessage;

/**
 * Dropdown widget for STRING parameters with preset values.
 * Wraps Necesse's FormDropdownSelectionButton.
 */
public class DropdownWidget extends ParameterWidget {
    
    private FormDropdownSelectionButton<String> dropdown;
    private String[] presetOptions;
    
    /**
     * Create a dropdown widget.
     * 
     * @param parameter The parameter metadata
     * @param x X position
     * @param y Y position
     * @param options Available options for the dropdown
     */
    public DropdownWidget(ParameterMetadata parameter, int x, int y, String[] options) {
        super(parameter);
        
        this.presetOptions = options;
        
        // Create the dropdown (x, y, size, color, width, startMessage)
        this.dropdown = new FormDropdownSelectionButton<>(
            x, y, 
            FormInputSize.SIZE_16, 
            ButtonColor.BASE, 
            200, 
            new StaticMessage("Select " + parameter.getDisplayName())
        );
        
        // Add all options
        for (String option : options) {
            dropdown.options.add(option, new StaticMessage(option));
        }
        
        // Select first option by default (prevents showing "Select..." placeholder)
        if (options.length > 0) {
            dropdown.setSelected(options[0], new StaticMessage(options[0]));
            currentValue = options[0];
        }
        
        // Listen to selection events to update our value
        dropdown.onSelected(event -> {
            currentValue = event.value;
        });
    }
    
    @Override
    public String getValue() {
        String selected = dropdown.getSelected();
        
        // Return null for optional params if nothing selected
        if (selected == null && !parameter.isRequired()) {
            return null;
        }
        
        return selected;
    }
    
    @Override
    public void setValue(String value) {
        if (value == null) {
            // For optional params, we can deselect
            if (!parameter.isRequired()) {
                dropdown.setSelected(null, new StaticMessage(""));
            }
        } else {
            dropdown.setSelected(value, new StaticMessage(value));
        }
        currentValue = value;
    }
    
    @Override
    public boolean validateValue() {
        String selected = dropdown.getSelected();
        
        // Required params must have a selection
        if (parameter.isRequired() && selected == null) {
            validationError = "Please select a value";
            return false;
        }
        
        // Verify selection is in the valid options
        if (selected != null) {
            boolean found = false;
            for (String option : presetOptions) {
                if (option.equals(selected)) {
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                validationError = "Invalid selection";
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    public void reset() {
        if (parameter.isRequired() && presetOptions.length > 0) {
            dropdown.setSelected(presetOptions[0], new StaticMessage(presetOptions[0]));
            currentValue = presetOptions[0];
        } else {
            dropdown.setSelected(null, new StaticMessage(""));
            currentValue = null;
        }
        isValid = true;
        validationError = null;
    }
    
    @Override
    public void onFocus() {
        // Dropdown handles focus internally
    }
    
    @Override
    public void onBlur() {
        validate();
    }
    
    /**
     * Get the underlying FormDropdownSelectionButton component.
     */
    @Override
    public FormDropdownSelectionButton<String> getComponent() {
        return dropdown;
    }
    
    /**
     * Add a new option to the dropdown.
     */
    public void addOption(String option) {
        dropdown.options.add(option, new StaticMessage(option));
    }
    
    /**
     * Get all available options.
     */
    public String[] getOptions() {
        return presetOptions;
    }
}
