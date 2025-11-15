package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import necesse.gfx.forms.components.FormDropdownSelectionButton;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.ui.ButtonColor;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.localization.message.StaticMessage;

/**
 * Permission level dropdown widget for PERMISSION_LEVEL parameters.
 * Uses FormDropdownSelectionButton to select from available permission levels.
 */
public class PermissionLevelDropdownWidget extends ParameterWidget {
    
    private FormDropdownSelectionButton<PermissionLevel> permissionDropdown;
    
    /**
     * Create a permission level dropdown widget.
     * 
     * @param parameter The parameter metadata
     * @param x X position
     * @param y Y position
     */
    public PermissionLevelDropdownWidget(ParameterMetadata parameter, int x, int y) {
        super(parameter);
        
        // Create dropdown
        this.permissionDropdown = new FormDropdownSelectionButton<PermissionLevel>(
            x, y,
            FormInputSize.SIZE_32,
            ButtonColor.BASE,
            150, // width
            new StaticMessage("Select permission...")
        );
        
        // Add all permission levels
        for (PermissionLevel level : PermissionLevel.values()) {
            permissionDropdown.options.add(level, new StaticMessage(level.name()));
        }
        
        // Listen for selection changes
        permissionDropdown.onSelected(event -> {
            currentValue = event.value != null ? event.value.name().toLowerCase() : null;
        });
    }
    
    @Override
    public String getValue() {
        PermissionLevel selected = permissionDropdown.getSelected();
        return selected != null ? selected.name().toLowerCase() : null;
    }
    
    @Override
    public void setValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            currentValue = null;
            return;
        }
        
        // Find permission level by name (case-insensitive)
        for (PermissionLevel level : PermissionLevel.values()) {
            if (level.name().equalsIgnoreCase(value.trim())) {
                permissionDropdown.setSelected(level, new StaticMessage(level.name()));
                currentValue = value.trim().toLowerCase();
                return;
            }
        }
        
        // Permission level not found
        currentValue = null;
    }
    
    @Override
    public boolean validateValue() {
        String value = getValue();
        
        // Required params must have a value
        if (parameter.isRequired() && (value == null || value.trim().isEmpty())) {
            validationError = "Please select a permission level";
            return false;
        }
        
        validationError = null;
        return true;
    }
    
    @Override
    public necesse.gfx.forms.components.FormComponent getComponent() {
        return permissionDropdown;
    }
}
