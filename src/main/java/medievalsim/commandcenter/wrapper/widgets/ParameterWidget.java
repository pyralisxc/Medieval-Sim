package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import necesse.gfx.forms.components.FormComponent;

/**
 * Base class for all parameter input widgets in the Command Center UI.
 * 
 * Wraps Necesse's FormComponent system to provide a unified interface
 * for collecting command parameter values from the user.
 */
public abstract class ParameterWidget {
    
    protected final ParameterMetadata parameter;
    protected String currentValue;
    protected boolean isValid;
    protected String validationError;
    protected Runnable onValueChangedCallback;
    
    /**
     * Create a parameter widget.
     * 
     * @param parameter Metadata about the parameter this widget represents
     */
    public ParameterWidget(ParameterMetadata parameter) {
        this.parameter = parameter;
        this.currentValue = "";
        this.isValid = parameter.isOptional(); // Optional params are valid when empty
        this.validationError = null;
        this.onValueChangedCallback = null;
    }
    
    /**
     * Set a callback to be invoked when the value changes.
     * Used by CommandCenterPanel to update button states.
     */
    public void setOnValueChanged(Runnable callback) {
        this.onValueChangedCallback = callback;
    }
    
    /**
     * Notify that the value has changed (for subclasses to call).
     */
    protected void notifyValueChanged() {
        if (onValueChangedCallback != null) {
            onValueChangedCallback.run();
        }
    }
    
    /**
     * Get the current value as a string (for command string building).
     * 
     * @return The current value, or empty string if not set
     */
    public String getValue() {
        return currentValue != null ? currentValue : "";
    }
    
    /**
     * Set the value programmatically.
     * 
     * @param value The value to set
     */
    public abstract void setValue(String value);
    
    /**
     * Validate the current value against parameter constraints.
     * 
     * @return true if valid, false otherwise
     */
    public boolean validate() {
        // If optional and empty, it's valid
        if (parameter.isOptional() && (currentValue == null || currentValue.trim().isEmpty())) {
            isValid = true;
            validationError = null;
            return true;
        }
        
        // If required and empty, it's invalid
        if (!parameter.isOptional() && (currentValue == null || currentValue.trim().isEmpty())) {
            isValid = false;
            validationError = "Required parameter";
            return false;
        }
        
        // Delegate to subclass for type-specific validation
        boolean result = validateValue();
        return result;
    }
    
    /**
     * Subclass-specific validation logic.
     * Override this to add type-specific validation.
     * 
     * @return true if valid, false otherwise
     */
    protected abstract boolean validateValue();
    
    /**
     * Get the underlying Necesse UI component for rendering.
     * Subclasses must implement this to return their wrapped FormComponent.
     * 
     * @return The FormComponent
     */
    public abstract FormComponent getComponent();
    
    /**
     * Get the parameter metadata.
     * 
     * @return Parameter metadata
     */
    public ParameterMetadata getParameter() {
        return parameter;
    }
    
    /**
     * Check if the current value is valid.
     * 
     * @return true if valid
     */
    public boolean isValid() {
        return isValid;
    }
    
    /**
     * Get the validation error message if invalid.
     * 
     * @return Error message, or null if valid
     */
    public String getValidationError() {
        return validationError;
    }
    
    /**
     * Reset the widget to its default state.
     */
    public void reset() {
        setValue("");
        isValid = parameter.isOptional();
        validationError = null;
    }
    
    /**
     * Called when the widget gains focus.
     */
    public void onFocus() {
        // Override in subclasses if needed
    }
    
    /**
     * Called when the widget loses focus.
     */
    public void onBlur() {
        // Override in subclasses if needed
        validate();
    }
}
