package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import necesse.gfx.forms.components.FormDropdownSelectionButton;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.ui.ButtonColor;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.commands.parameterHandlers.EnumParameterHandler;

import java.lang.reflect.Field;

/**
 * Generic enum dropdown widget for ENUM parameters.
 * Uses reflection to extract enum values from the EnumParameterHandler.
 */
public class EnumDropdownWidget extends ParameterWidget {
    
    private FormDropdownSelectionButton<Enum<?>> enumDropdown;
    private Enum<?>[] enumValues;
    
    /**
     * Create an enum dropdown widget.
     * 
     * @param parameter The parameter metadata
     * @param x X position
     * @param y Y position
     */
    public EnumDropdownWidget(ParameterMetadata parameter, int x, int y) {
        super(parameter);
        
        // Extract enum values from the handler using reflection
        this.enumValues = extractEnumValues(parameter);
        
        // Create dropdown
        this.enumDropdown = new FormDropdownSelectionButton<Enum<?>>(
            x, y,
            FormInputSize.SIZE_32,
            ButtonColor.BASE,
            200, // width
            new StaticMessage("Select value...")
        );
        
        // Add all enum values with formatted names
        if (enumValues != null && enumValues.length > 0) {
            for (Enum<?> value : enumValues) {
                String displayName = formatEnumName(value.name());
                enumDropdown.options.add(value, new StaticMessage(displayName));
            }
            
            // Auto-select the first value to prevent null return
            Enum<?> defaultValue = enumValues[0];
            String displayName = formatEnumName(defaultValue.name());
            enumDropdown.setSelected(defaultValue, new StaticMessage(displayName));
            currentValue = defaultValue.name();
        }
        
        // Listen for selection changes
        enumDropdown.onSelected(event -> {
            currentValue = event.value != null ? event.value.name() : null;
        });
    }
    
    /**
     * Format enum constant name for display.
     * Converts "PEACEFUL" to "Peaceful", "WORLD_BOSS" to "World Boss", etc.
     */
    private String formatEnumName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        
        // Split on underscores and capitalize first letter of each word
        String[] parts = name.split("_");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            String part = parts[i];
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1).toLowerCase());
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * Extract enum values from EnumParameterHandler using reflection.
     * EnumParameterHandler has a private field 'values' that contains the enum constants.
     */
    private Enum<?>[] extractEnumValues(ParameterMetadata parameter) {
        try {
            if (!(parameter.getHandler() instanceof EnumParameterHandler)) {
                medievalsim.util.ModLogger.error("EnumDropdownWidget: Handler is not EnumParameterHandler: %s", parameter.getHandler().getClass().getName());
                return null;
            }
            
            EnumParameterHandler<?> enumHandler = (EnumParameterHandler<?>) parameter.getHandler();
            
            // Access private 'values' field from EnumParameterHandler
            Field valuesField = EnumParameterHandler.class.getDeclaredField("values");
            valuesField.setAccessible(true);
            Enum<?>[] values = (Enum<?>[]) valuesField.get(enumHandler);
            
            return values;
            
        } catch (Exception e) {
            // Failed to extract enum values via reflection
            return null;
        }
    }
    
    @Override
    public String getValue() {
        Enum<?> selected = enumDropdown.getSelected();
        String result = selected != null ? selected.name() : null;
        
        // Ensure currentValue stays synchronized with dropdown selection
        currentValue = result;
        
        return result;
    }
    
    @Override
    public void setValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            currentValue = null;
            return;
        }
        
        // Find enum value by name (case-insensitive)
        if (enumValues != null) {
            for (Enum<?> enumValue : enumValues) {
                if (enumValue.name().equalsIgnoreCase(value.trim())) {
                    String displayName = formatEnumName(enumValue.name());
                    enumDropdown.setSelected(enumValue, new StaticMessage(displayName));
                    currentValue = value.trim();
                    return;
                }
            }
        }
        
        // Enum value not found
        currentValue = null;
    }
    
    @Override
    public boolean validateValue() {
        String value = getValue();
        
        // Required params must have a value
        if (parameter.isRequired() && (value == null || value.trim().isEmpty())) {
            validationError = "Please select a value";
            return false;
        }
        
        validationError = null;
        return true;
    }
    
    @Override
    public necesse.gfx.forms.components.FormComponent getComponent() {
        return enumDropdown;
    }
}
