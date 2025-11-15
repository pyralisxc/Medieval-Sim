package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormComponent;
import necesse.engine.localization.message.StaticMessage;

/**
 * Simple text input widget for TEAM parameters.
 * Currently accepts team ID as text input.
 * 
 * Note: Team management in Necesse is complex and teams are created dynamically.
 * Text input is the most reliable approach until we need advanced team browsing.
 * 
 * <p>Follows Necesse best practices for parameter widgets.
 */
public class TeamDropdownWidget extends ParameterWidget {
    
    private FormTextInput textInput;
    
    /**
     * Create a team input widget.
     * 
     * @param parameter The parameter metadata
     * @param x X position
     * @param y Y position
     */
    public TeamDropdownWidget(ParameterMetadata parameter, int x, int y) {
        super(parameter);
        
        // Create text input for team ID entry
        this.textInput = new FormTextInput(x, y, FormInputSize.SIZE_32, 200, 200, 50);
        this.textInput.placeHolder = new StaticMessage("Team ID (number)");
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
        
        // Check if value is a valid team ID (numeric)
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            validationError = "Team ID must be a number";
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
        // Future: Refresh team list when we add dropdown
    }
    
    @Override
    public FormComponent getComponent() {
        return textInput;
    }
}
