package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import medievalsim.ui.fixes.InputFocusManager;
import necesse.gfx.forms.components.FormInputSize;

/**
 * Number input widget for INT and FLOAT parameter types.
 * Extends TextInputWidget with numeric validation.
 */
public class NumberInputWidget extends ParameterWidget {
    
    private InputFocusManager.EnhancedTextInput textInput;
    private boolean allowDecimals;
    private Double minValue;
    private Double maxValue;
    
    /**
     * Create a number input widget.
     * 
     * @param parameter The parameter metadata
     * @param x X position
     * @param y Y position
     * @param width Width in pixels
     * @param allowDecimals True for FLOAT, false for INT
     */
    public NumberInputWidget(ParameterMetadata parameter, int x, int y, int width, boolean allowDecimals) {
        this(parameter, x, y, width, allowDecimals, null);
    }
    
    /**
     * Create a number input widget with default value.
     * 
     * @param parameter The parameter metadata
     * @param x X position
     * @param y Y position
     * @param width Width in pixels
     * @param allowDecimals True for FLOAT, false for INT
     * @param defaultValue Default value to pre-fill (null for empty)
     */
    public NumberInputWidget(ParameterMetadata parameter, int x, int y, int width, boolean allowDecimals, String defaultValue) {
        super(parameter);
        
        this.allowDecimals = allowDecimals;
        this.minValue = null;
        this.maxValue = null;
        
        // Create the text input (x, y, FormInputSize, width, maxDrawWidth, maxLength)
        this.textInput = new InputFocusManager.EnhancedTextInput(x, y, FormInputSize.SIZE_16, width, 200, 20);
        
        // Apply default value if provided
        if (defaultValue != null && !defaultValue.isEmpty()) {
            textInput.setText(defaultValue);
        }
    }
    
    @Override
    public String getValue() {
        String value = textInput.getText().trim();
        
        if (value.isEmpty()) {
            return null;
        }
        
        // Parse and format the number to ensure valid syntax
        try {
            if (allowDecimals) {
                double num = Double.parseDouble(value);
                return String.valueOf(num);
            } else {
                int num = Integer.parseInt(value);
                return String.valueOf(num);
            }
        } catch (NumberFormatException e) {
            return null; // Invalid number
        }
    }
    
    @Override
    public void setValue(String value) {
        if (value == null) {
            textInput.setText("");
        } else {
            textInput.setText(value);
        }
    }
    
    @Override
    public boolean validateValue() {
        String value = textInput.getText().trim();
        
        // Empty is valid for optional parameters
        if (value.isEmpty()) {
            return !parameter.isRequired();
        }
        
        // Check if it's a valid number
        try {
            double numValue;
            
            if (allowDecimals) {
                numValue = Double.parseDouble(value);
            } else {
                // For integers, check there's no decimal point
                if (value.contains(".")) {
                    validationError = "Must be a whole number (no decimals)";
                    return false;
                }
                numValue = Integer.parseInt(value);
            }
            
            // Check min/max bounds if set
            if (minValue != null && numValue < minValue) {
                validationError = "Value must be at least " + minValue;
                return false;
            }
            
            if (maxValue != null && numValue > maxValue) {
                validationError = "Value must be at most " + maxValue;
                return false;
            }
            
            return true;
            
        } catch (NumberFormatException e) {
            validationError = allowDecimals ? 
                "Must be a valid number" : 
                "Must be a valid integer";
            return false;
        }
    }
    
    @Override
    public void reset() {
        textInput.setText("");
        isValid = true;
        validationError = null;
    }
    
    @Override
    public void onFocus() {
        // Necesse handles focus internally
    }
    
    @Override
    public void onBlur() {
        // Validate when user leaves the field
        validate();
    }
    
    /**
     * Get the underlying FormTextInput component.
     */
    public InputFocusManager.EnhancedTextInput getComponent() {
        return textInput;
    }
    
    /**
     * Set minimum allowed value.
     */
    public void setMinValue(double min) {
        this.minValue = min;
    }
    
    /**
     * Set maximum allowed value.
     */
    public void setMaxValue(double max) {
        this.maxValue = max;
    }
    
    /**
     * Set allowed range.
     */
    public void setRange(double min, double max) {
        this.minValue = min;
        this.maxValue = max;
    }
}
