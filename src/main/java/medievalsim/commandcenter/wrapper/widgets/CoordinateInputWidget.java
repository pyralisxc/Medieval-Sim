package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import medievalsim.ui.fixes.InputFocusManager;
import necesse.engine.localization.message.StaticMessage;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormInputSize;

/**
 * Widget for coordinate input (RELATIVE_INT parameter type).
 * 
 * Simple design: Two text inputs (X and Y) that user types directly into.
 * Supports both absolute coordinates (1000 2000) and relative syntax (%+100 %-50).
 * 
 * The widget returns the X input as the main component.
 * CommandCenterPanel must add BOTH xInput and yInput to the form.
 * 
 * Examples:
 *   - Absolute: Type "1000" in X, "2000" in Y → Returns "1000 2000"
 *   - Relative: Type "%+100" in X, "%-50" in Y → Returns "%+100 %-50"
 */
public class CoordinateInputWidget extends ParameterWidget {
    
    private InputFocusManager.EnhancedTextInput xInput;
    private InputFocusManager.EnhancedTextInput yInput;
    
    private static final int INPUT_WIDTH = 85;
    
    /**
     * Constructor
     * 
     * @param parameter Parameter metadata
     * @param x X position
     * @param y Y position
     */
    public CoordinateInputWidget(ParameterMetadata parameter, int x, int y) {
        super(parameter);
        
        // X coordinate input (x, y, size, width, maxDrawWidth, maxLength)
        xInput = new InputFocusManager.EnhancedTextInput(
            x, y,
            FormInputSize.SIZE_16,
            INPUT_WIDTH,
            200,
            10
        );
        xInput.placeHolder = new StaticMessage("X (e.g. 1000)");
        
        // Y coordinate input - positioned next to X
        yInput = new InputFocusManager.EnhancedTextInput(
            x + INPUT_WIDTH + 10, y,
            FormInputSize.SIZE_16,
            INPUT_WIDTH,
            200,
            10
        );
        yInput.placeHolder = new StaticMessage("Y (e.g. 2000)");
    }
    
    @Override
    public FormComponent getComponent() {
        // Return X input as primary component
        return xInput;
    }
    
    /**
     * Get the Y input component (CommandCenterPanel must add this separately)
     */
    public InputFocusManager.EnhancedTextInput getYInput() {
        return yInput;
    }
    
    @Override
    public String getValue() {
        String xValue = xInput.getText().trim();
        String yValue = yInput.getText().trim();
        
        // Handle empty values
        if (xValue.isEmpty()) xValue = "0";
        if (yValue.isEmpty()) yValue = "0";
        
        // Return space-separated coordinates
        // User types the syntax they want: "1000 2000" or "%+100 %-50"
        return xValue + " " + yValue;
    }
    
    @Override
    public void setValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            xInput.setText("");
            yInput.setText("");
            return;
        }
        
        // Parse space-separated coordinates
        String[] parts = value.trim().split("\\s+");
        
        if (parts.length >= 1) {
            xInput.setText(parts[0]);
        }
        
        if (parts.length >= 2) {
            yInput.setText(parts[1]);
        }
    }
    
    @Override
    protected boolean validateValue() {
        String xValue = xInput.getText().trim();
        String yValue = yInput.getText().trim();
        
        // Empty is valid if not required
        if (xValue.isEmpty() && yValue.isEmpty()) {
            return !parameter.isRequired();
        }
        
        // Both must be provided if one is provided
        if (xValue.isEmpty() || yValue.isEmpty()) {
            validationError = "Both X and Y coordinates required";
            return false;
        }
        
        // Validate X coordinate
        if (!isValidCoordinate(xValue)) {
            validationError = "Invalid X coordinate (use number or %+100 syntax)";
            return false;
        }
        
        // Validate Y coordinate
        if (!isValidCoordinate(yValue)) {
            validationError = "Invalid Y coordinate (use number or %+100 syntax)";
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if a coordinate value is valid (absolute or relative)
     */
    private boolean isValidCoordinate(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        
        // Check if it's relative syntax (%+100 or %-50)
        if (value.startsWith("%")) {
            String numPart = value.substring(1);
            if (numPart.isEmpty()) return false;
            
            // Must have + or - prefix
            if (!numPart.startsWith("+") && !numPart.startsWith("-")) {
                return false;
            }
            
            // Validate the number part
            String number = numPart.substring(1);
            try {
                Integer.parseInt(number);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        } else {
            // Must be a valid integer (absolute coordinate)
            try {
                Integer.parseInt(value);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
    
    @Override
    public void reset() {
        xInput.setText("");
        yInput.setText("");
        isValid = parameter.isOptional();
        validationError = null;
    }
    
    @Override
    public void onFocus() {
        // Focus on X input by default
        xInput.setTyping(true);
    }
    
    @Override
    public void onBlur() {
        // Validate when focus is lost
        validate();
    }
}
