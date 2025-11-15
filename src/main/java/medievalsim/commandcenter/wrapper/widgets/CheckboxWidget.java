package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import necesse.gfx.forms.components.FormCheckBox;

/**
 * Checkbox widget for BOOL parameter types.
 * Wraps Necesse's FormCheckBox.
 */
public class CheckboxWidget extends ParameterWidget {
    
    private FormCheckBox checkbox;
    
    /**
     * Create a checkbox widget.
     * 
     * @param parameter The parameter metadata
     * @param x X position
     * @param y Y position
     */
    public CheckboxWidget(ParameterMetadata parameter, int x, int y) {
        super(parameter);
        
        // Create checkbox with parameter name as label
        String label = parameter.getDisplayName();
        this.checkbox = new FormCheckBox(label, x, y);
        
        // Default to false (unchecked)
        checkbox.checked = false;
    }
    
    @Override
    public String getValue() {
        // Return "true" or "false" as string for command
        return checkbox.checked ? "true" : "false";
    }
    
    @Override
    public void setValue(String value) {
        if (value == null) {
            checkbox.checked = false;
        } else {
            // Parse various boolean representations
            checkbox.checked = value.equalsIgnoreCase("true") || 
                              value.equals("1") || 
                              value.equalsIgnoreCase("yes");
        }
    }
    
    @Override
    public boolean validateValue() {
        // Boolean values are always valid
        // Optional boolean defaults to false if not set
        return true;
    }
    
    @Override
    public void reset() {
        checkbox.checked = false;
        isValid = true;
        validationError = null;
    }
    
    @Override
    public void onFocus() {
        // Checkboxes don't need focus handling
    }
    
    @Override
    public void onBlur() {
        // No validation needed for boolean
    }
    
    /**
     * Get the underlying FormCheckBox component.
     */
    public FormCheckBox getComponent() {
        return checkbox;
    }
    
    /**
     * Set the checked state.
     */
    public void setChecked(boolean checked) {
        checkbox.checked = checked;
    }
    
    /**
     * Get the checked state.
     */
    public boolean isChecked() {
        return checkbox.checked;
    }
}
