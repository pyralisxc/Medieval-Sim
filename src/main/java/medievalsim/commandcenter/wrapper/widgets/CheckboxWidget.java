package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import necesse.gfx.forms.components.FormCheckBox;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.gameFont.FontOptions;
import necesse.engine.network.client.Client;

import java.awt.Color;
import java.lang.reflect.Field;

/**
 * Checkbox widget for BOOL parameter types.
 * Clean, compact UI following Necesse's standard checkbox pattern.
 * 
 * Replaces ToggleButtonWidget with more conventional checkbox interface.
 */
public class CheckboxWidget extends ParameterWidget {
    
    private final FormCheckBox checkbox;
    
    /**
     * Create a checkbox widget.
     * 
     * @param parameter The parameter metadata
     * @param x X position
     * @param y Y position
     * @param client Client instance (to read current world settings)
     */
    public CheckboxWidget(ParameterMetadata parameter, int x, int y, Client client) {
        this(parameter, x, y, client, null);
    }
    
    /**
     * Create a checkbox widget with default value.
     * 
     * @param parameter The parameter metadata
     * @param x X position
     * @param y Y position
     * @param client Client instance (to read current world settings)
     * @param defaultValue Default value ("true" or "false", null to read from world)
     */
    public CheckboxWidget(ParameterMetadata parameter, int x, int y, Client client, String defaultValue) {
        super(parameter);
        
        // Determine initial state
        boolean initialState;
        if (defaultValue != null) {
            initialState = Boolean.parseBoolean(defaultValue);
        } else {
            initialState = getCurrentStateFromWorld(parameter.getName());
        }
        
        // Create checkbox with parameter display name
        this.checkbox = new FormCheckBox(
            parameter.getDisplayName(),
            x, y,
            200, // Width
            initialState
        );
        
        // Set white text color via reflection (FormCheckBox doesn't expose fontOptions setter)
        setCheckboxTextColor(this.checkbox, Color.WHITE);
        
        // Trigger validation callback when checked state changes
        checkbox.onClicked(e -> {
            notifyValueChanged();
        });
    }
    
    /**
     * Try to read current state from world settings.
     * Checks common boolean world settings that might match the parameter name.
     */
    private boolean getCurrentStateFromWorld(String parameterName) {
        // Default to false - reading world settings is complex and context-dependent
        // Most boolean command parameters default to false anyway
        return false;
    }
    
    /**
     * Set the text color of a checkbox using reflection.
     * FormCheckBox doesn't expose a public setter for fontOptions.
     */
    private void setCheckboxTextColor(FormCheckBox checkbox, Color color) {
        try {
            Field fontOptionsField = FormCheckBox.class.getDeclaredField("fontOptions");
            fontOptionsField.setAccessible(true);
            FontOptions currentOptions = (FontOptions) fontOptionsField.get(checkbox);
            
            // Create new FontOptions with white color
            FontOptions whiteOptions = new FontOptions(currentOptions.getSize()).color(color);
            fontOptionsField.set(checkbox, whiteOptions);
            
            // Force text rebuild with new font options
            checkbox.setText(checkbox.getText());
        } catch (Exception e) {
            // Silent fail - checkbox will just use default color
            System.err.println("CheckboxWidget: Could not set text color: " + e.getMessage());
        }
    }
    
    @Override
    public FormComponent getComponent() {
        return checkbox;
    }
    
    @Override
    public String getValue() {
        return String.valueOf(checkbox.checked);
    }
    
    @Override
    public void setValue(String value) {
        if (value != null) {
            checkbox.checked = Boolean.parseBoolean(value);
        }
    }
    
    @Override
    public void reset() {
        checkbox.checked = false;
    }
    
    @Override
    public boolean isValid() {
        // Checkboxes are always valid (boolean values)
        return true;
    }
    
    @Override
    protected boolean validateValue() {
        // Checkboxes are always valid - they're always either true or false
        this.isValid = true;
        this.validationError = null;
        return true;
    }
    
    /**
     * Get the checkbox component directly (for advanced usage).
     */
    public FormCheckBox getCheckbox() {
        return checkbox;
    }
}
