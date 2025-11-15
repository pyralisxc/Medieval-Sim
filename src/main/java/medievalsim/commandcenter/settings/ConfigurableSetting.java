package medievalsim.commandcenter.settings;

import necesse.engine.modLoader.LoadedMod;
import java.lang.reflect.Field;

/**
 * Represents a single configurable setting discovered via reflection.
 * Wraps a Field with its metadata (description, min/max, type, etc.)
 */
public class ConfigurableSetting {
    private final LoadedMod mod;
    private final String sectionName;
    private final Field field;
    private final String fieldName;
    private final String description;
    private final SettingType type;
    private final double minValue;
    private final double maxValue;
    private final String defaultValue;
    private final boolean runtime;
    
    public ConfigurableSetting(LoadedMod mod, String sectionName, Field field, 
                               String description, SettingType type, 
                               double minValue, double maxValue, 
                               String defaultValue, boolean runtime) {
        this.mod = mod;
        this.sectionName = sectionName;
        this.field = field;
        this.fieldName = field.getName();
        this.description = description;
        this.type = type;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.defaultValue = defaultValue;
        this.runtime = runtime;
        
        // Ensure field is accessible
        field.setAccessible(true);
    }
    
    // ===== GETTERS =====
    
    public LoadedMod getMod() {
        return mod;
    }
    
    public String getSectionName() {
        return sectionName;
    }
    
    public Field getField() {
        return field;
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public SettingType getType() {
        return type;
    }
    
    public double getMinValue() {
        return minValue;
    }
    
    public double getMaxValue() {
        return maxValue;
    }
    
    public String getDefaultValue() {
        return defaultValue;
    }
    
    public boolean isRuntime() {
        return runtime;
    }
    
    /**
     * Get a human-readable display name for this setting
     */
    public String getDisplayName() {
        // Convert camelCase to Title Case
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (i == 0) {
                result.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c)) {
                result.append(' ').append(c);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
    
    // ===== VALUE ACCESS =====
    
    /**
     * Get the current value of this setting
     */
    public Object getValue() {
        try {
            return field.get(null);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to get value for " + fieldName, e);
        }
    }
    
    /**
     * Set the value of this setting
     */
    public void setValue(Object value) {
        try {
            field.set(null, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set value for " + fieldName, e);
        }
    }
    
    /**
     * Get the current value as an integer
     */
    public int getIntValue() {
        Object value = getValue();
        if (value instanceof Integer) {
            return (Integer) value;
        }
        throw new IllegalStateException("Setting " + fieldName + " is not an integer");
    }
    
    /**
     * Get the current value as a long
     */
    public long getLongValue() {
        Object value = getValue();
        if (value instanceof Long) {
            return (Long) value;
        }
        throw new IllegalStateException("Setting " + fieldName + " is not a long");
    }
    
    /**
     * Get the current value as a float
     */
    public float getFloatValue() {
        Object value = getValue();
        if (value instanceof Float) {
            return (Float) value;
        }
        throw new IllegalStateException("Setting " + fieldName + " is not a float");
    }
    
    /**
     * Get the current value as a boolean
     */
    public boolean getBooleanValue() {
        Object value = getValue();
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        throw new IllegalStateException("Setting " + fieldName + " is not a boolean");
    }
    
    /**
     * Get the current value as a string
     */
    public String getStringValue() {
        Object value = getValue();
        if (value instanceof String) {
            return (String) value;
        }
        return String.valueOf(value);
    }
    
    /**
     * Set the value from an integer
     */
    public void setIntValue(int value) {
        setValue(value);
    }
    
    /**
     * Set the value from a long
     */
    public void setLongValue(long value) {
        setValue(value);
    }
    
    /**
     * Set the value from a float
     */
    public void setFloatValue(float value) {
        setValue(value);
    }
    
    /**
     * Set the value from a boolean
     */
    public void setBooleanValue(boolean value) {
        setValue(value);
    }
    
    /**
     * Set the value from a string
     */
    public void setStringValue(String value) {
        setValue(value);
    }

    /**
     * Reset this setting to its default value
     */
    public void resetToDefault() {
        if (defaultValue == null || defaultValue.isEmpty()) {
            return; // No default value specified
        }

        try {
            switch (type) {
                case INTEGER:
                    setIntValue(Integer.parseInt(defaultValue));
                    break;
                case LONG:
                    setLongValue(Long.parseLong(defaultValue));
                    break;
                case FLOAT:
                    setFloatValue(Float.parseFloat(defaultValue));
                    break;
                case BOOLEAN:
                    setBooleanValue(Boolean.parseBoolean(defaultValue));
                    break;
                case STRING:
                    setStringValue(defaultValue);
                    break;
                case ENUM:
                    // TODO: Implement enum reset when enum support is added
                    break;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to reset " + fieldName + " to default: " + defaultValue, e);
        }
    }

    @Override
    public String toString() {
        return "ConfigurableSetting{" +
               "mod=" + mod.id +
               ", section=" + sectionName +
               ", field=" + fieldName +
               ", type=" + type +
               ", value=" + getValue() +
               "}";
    }
}

