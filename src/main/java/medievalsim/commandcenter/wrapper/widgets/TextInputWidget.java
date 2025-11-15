package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.forms.components.FormInputSize;

/**
 * Text input widget for STRING and UNKNOWN parameter types.
 * Wraps Necesse's FormTextInput with validation.
 */
public class TextInputWidget extends ParameterWidget {
    
    private FormTextInput textInput;
    private int maxLength;
    
    /**
     * Create a text input widget.
     * 
     * @param parameter The parameter metadata
     * @param x X position
     * @param y Y position
     * @param width Width in pixels
     */
    public TextInputWidget(ParameterMetadata parameter, int x, int y, int width) {
        this(parameter, x, y, width, null);
    }
    
    /**
     * Create a text input widget with default value.
     * 
     * @param parameter The parameter metadata
     * @param x X position
     * @param y Y position
     * @param width Width in pixels
     * @param defaultValue Default value to pre-fill (null for empty)
     */
    public TextInputWidget(ParameterMetadata parameter, int x, int y, int width, String defaultValue) {
        super(parameter);
        
        // Necesse text inputs have a max length (default to 100)
        this.maxLength = 100;
        
        // Create the text input (x, y, FormInputSize, width, maxDrawWidth, maxLength)
        this.textInput = new FormTextInput(x, y, FormInputSize.SIZE_16, width, 200, maxLength);
        
        // Apply default value if provided
        if (defaultValue != null && !defaultValue.isEmpty()) {
            textInput.setText(defaultValue);
        }
        
        // Note: Necesse's FormTextInput doesn't have setPlaceholder in base API
        // Placeholder functionality can be added through custom rendering if needed
    }
    
    @Override
    public String getValue() {
        String value = textInput.getText().trim();
        return value.isEmpty() ? null : value;
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
        
        // Check max length
        if (value.length() > maxLength) {
            validationError = "Maximum length is " + maxLength + " characters";
            return false;
        }
        
        return true;
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
     * Used to add the widget to a FormManager.
     */
    public FormTextInput getComponent() {
        return textInput;
    }
    
    /**
     * Set maximum input length.
     */
    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
        // Note: Would need to recreate component to change max length
        // For now, just update the field
    }
}
