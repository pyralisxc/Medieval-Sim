package medievalsim.commandcenter.settings;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Generic wrapper for a configurable setting
 * @param <T> The type of the setting value
 */
public class AdminSetting<T> {
    
    private final String id;
    private final String displayName;
    private final String description;
    private final String category;
    private final SettingType type;
    private final T defaultValue;
    private final T minValue;
    private final T maxValue;
    private final boolean requiresRestart;
    private final Supplier<T> getter;
    private final Consumer<T> setter;
    
    private AdminSetting(Builder<T> builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.category = builder.category;
        this.type = builder.type;
        this.defaultValue = builder.defaultValue;
        this.minValue = builder.minValue;
        this.maxValue = builder.maxValue;
        this.requiresRestart = builder.requiresRestart;
        this.getter = builder.getter;
        this.setter = builder.setter;
    }
    
    // Getters
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public SettingType getType() { return type; }
    public T getDefaultValue() { return defaultValue; }
    public T getMinValue() { return minValue; }
    public T getMaxValue() { return maxValue; }
    public boolean requiresRestart() { return requiresRestart; }
    
    /**
     * Get the current value
     */
    public T getValue() {
        return getter != null ? getter.get() : defaultValue;
    }
    
    /**
     * Set the value (if setter is provided)
     */
    public boolean setValue(T value) {
        if (setter == null) {
            return false; // Read-only setting
        }
        
        // Validate range for numeric types
        if (type == SettingType.INTEGER || type == SettingType.FLOAT) {
            if (!isValidRange(value)) {
                return false;
            }
        }
        
        setter.accept(value);
        return true;
    }
    
    /**
     * Check if a value is within valid range
     */
    private boolean isValidRange(T value) {
        if (value instanceof Integer) {
            int intValue = (Integer) value;
            int min = minValue != null ? (Integer) minValue : Integer.MIN_VALUE;
            int max = maxValue != null ? (Integer) maxValue : Integer.MAX_VALUE;
            return intValue >= min && intValue <= max;
        } else if (value instanceof Float) {
            float floatValue = (Float) value;
            float min = minValue != null ? (Float) minValue : Float.MIN_VALUE;
            float max = maxValue != null ? (Float) maxValue : Float.MAX_VALUE;
            return floatValue >= min && floatValue <= max;
        } else if (value instanceof Long) {
            long longValue = (Long) value;
            long min = minValue != null ? (Long) minValue : Long.MIN_VALUE;
            long max = maxValue != null ? (Long) maxValue : Long.MAX_VALUE;
            return longValue >= min && longValue <= max;
        }
        return true;
    }
    
    /**
     * Check if this setting is read-only
     */
    public boolean isReadOnly() {
        return setter == null;
    }
    
    /**
     * Builder for creating AdminSettings
     */
    public static class Builder<T> {
        private String id;
        private String displayName;
        private String description = "";
        private String category = "General";
        private SettingType type;
        private T defaultValue;
        private T minValue;
        private T maxValue;
        private boolean requiresRestart = false;
        private Supplier<T> getter;
        private Consumer<T> setter;
        
        public Builder(String id, String displayName, SettingType type) {
            this.id = id;
            this.displayName = displayName;
            this.type = type;
        }
        
        public Builder<T> description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder<T> category(String category) {
            this.category = category;
            return this;
        }
        
        public Builder<T> defaultValue(T value) {
            this.defaultValue = value;
            return this;
        }
        
        public Builder<T> range(T min, T max) {
            this.minValue = min;
            this.maxValue = max;
            return this;
        }
        
        public Builder<T> requiresRestart(boolean requires) {
            this.requiresRestart = requires;
            return this;
        }
        
        public Builder<T> requiresRestart() {
            return requiresRestart(true);
        }
        
        public Builder<T> getter(Supplier<T> getter) {
            this.getter = getter;
            return this;
        }
        
        public Builder<T> setter(Consumer<T> setter) {
            this.setter = setter;
            return this;
        }
        
        public AdminSetting<T> build() {
            return new AdminSetting<>(this);
        }
    }
}
