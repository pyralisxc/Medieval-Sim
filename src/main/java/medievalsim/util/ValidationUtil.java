package medievalsim.util;

import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;

/**
 * Common validation utilities for Medieval Sim
 * Reduces code duplication and provides consistent validation patterns
 */
public final class ValidationUtil {
    
    private ValidationUtil() {
        // Utility class - no instantiation
    }
    
    /**
     * Validate that level is non-null and is a server level
     * @param level Level to validate
     * @return true if valid server level, false otherwise
     */
    public static boolean isValidServerLevel(Level level) {
        return level != null && level.isServer();
    }
    
    /**
     * Validate that client is non-null and authenticated
     * @param client Client to validate
     * @return true if valid authenticated client, false otherwise
     */
    public static boolean isValidClient(ServerClient client) {
        return client != null && client.authentication != -1L;
    }
    
    /**
     * Validate server level and log appropriate error if invalid
     * @param level Level to validate
     * @param operation Description of operation being attempted
     * @return true if valid, false if invalid (and logged)
     */
    public static boolean validateServerLevel(Level level, String operation) {
        if (!isValidServerLevel(level)) {
            ModLogger.warn("Attempted %s with invalid level (null or not server)", operation);
            return false;
        }
        return true;
    }
    
    /**
     * Validate client and log appropriate error if invalid
     * @param client Client to validate
     * @param operation Description of operation being attempted
     * @return true if valid, false if invalid (and logged)
     */
    public static boolean validateClient(ServerClient client, String operation) {
        if (!isValidClient(client)) {
            ModLogger.warn("Attempted %s with invalid client (null or not authenticated)", operation);
            return false;
        }
        return true;
    }
    
    /**
     * Validate string is non-null and not empty after trimming
     * @param value String to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidString(String value) {
        return value != null && !value.trim().isEmpty();
    }
    
    /**
     * Validate coordinate is within reasonable bounds
     * Necesse uses tile coordinates, so very large values may cause issues
     * @param coordinate Coordinate to validate
     * @return true if coordinate is reasonable, false otherwise
     */
    public static boolean isValidCoordinate(int coordinate) {
        // Reasonable bounds for Necesse tile coordinates
        return coordinate >= -100000 && coordinate <= 100000;
    }
    
    /**
     * Validate rectangle has positive dimensions and reasonable coordinates
     * @param x X coordinate
     * @param y Y coordinate  
     * @param width Width (must be positive)
     * @param height Height (must be positive)
     * @return true if valid rectangle, false otherwise
     */
    public static boolean isValidRectangle(int x, int y, int width, int height) {
        return isValidCoordinate(x) && isValidCoordinate(y) && 
               width > 0 && height > 0 && 
               width <= 10000 && height <= 10000; // Prevent excessive sizes
    }
    
    // ===== INPUT VALIDATION =====
    
    /**
     * Parse and validate a positive integer from string input.
     * 
     * <p>Common use: UI text inputs for quantities, prices, etc.</p>
     * 
     * @param input User input string
     * @param fieldName Field name for error messages
     * @return Parsed positive integer
     * @throws IllegalArgumentException if input is invalid or not positive
     */
    public static int validatePositiveInteger(String input, String fieldName) {
        if (!isValidString(input)) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        
        try {
            int value = Integer.parseInt(input.trim());
            if (value <= 0) {
                throw new IllegalArgumentException(fieldName + " must be greater than zero");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid number");
        }
    }
    
    /**
     * Validate an integer is within the specified range (inclusive).
     * 
     * @param value Value to validate
     * @param min Minimum allowed value (inclusive)
     * @param max Maximum allowed value (inclusive)
     * @param fieldName Field name for error messages
     * @return The validated value
     * @throws IllegalArgumentException if value is outside range
     */
    public static int validateRange(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                String.format("%s must be between %d and %d (got %d)", fieldName, min, max, value));
        }
        return value;
    }
    
    /**
     * Validate a float is within the specified range (inclusive).
     * 
     * @param value Value to validate
     * @param min Minimum allowed value (inclusive)
     * @param max Maximum allowed value (inclusive)
     * @param fieldName Field name for error messages
     * @return The validated value
     * @throws IllegalArgumentException if value is outside range
     */
    public static float validateRange(float value, float min, float max, String fieldName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                String.format("%s must be between %.2f and %.2f (got %.2f)", fieldName, min, max, value));
        }
        return value;
    }
    
    /**
     * Validate and sanitize a non-empty string with maximum length.
     * 
     * <p>Trims whitespace and enforces length constraints.</p>
     * 
     * @param input User input string
     * @param maxLength Maximum allowed length after trimming
     * @param fieldName Field name for error messages
     * @return Sanitized string (trimmed)
     * @throws IllegalArgumentException if input is invalid
     */
    public static String validateNonEmptyString(String input, int maxLength, String fieldName) {
        if (!isValidString(input)) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        
        String sanitized = input.trim();
        if (sanitized.length() > maxLength) {
            throw new IllegalArgumentException(
                String.format("%s cannot exceed %d characters (got %d)", 
                    fieldName, maxLength, sanitized.length()));
        }
        
        return sanitized;
    }
    
    /**
     * Validate price input for Grand Exchange.
     * 
     * <p>Ensures price is positive integer within reasonable bounds.</p>
     * 
     * @param input User input string
     * @return true if valid price, false otherwise
     */
    public static boolean validatePriceInput(String input) {
        if (!isValidString(input)) {
            return false;
        }
        
        try {
            int price = Integer.parseInt(input.trim());
            return price > 0 && price <= Integer.MAX_VALUE;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Validate quantity input.
     * 
     * <p>Ensures quantity is positive and does not exceed maximum.</p>
     * 
     * @param input User input string
     * @param maxAllowed Maximum allowed quantity
     * @return true if valid quantity, false otherwise
     */
    public static boolean validateQuantityInput(String input, int maxAllowed) {
        if (!isValidString(input)) {
            return false;
        }
        
        try {
            int quantity = Integer.parseInt(input.trim());
            return quantity > 0 && quantity <= maxAllowed;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Parse integer with default fallback.
     * 
     * <p>Safe parsing that returns default value instead of throwing exception.</p>
     * 
     * @param input User input string
     * @param defaultValue Value to return if parsing fails
     * @return Parsed integer or default value
     */
    public static int parseIntOrDefault(String input, int defaultValue) {
        if (!isValidString(input)) {
            return defaultValue;
        }
        
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Parse float with default fallback.
     * 
     * <p>Safe parsing that returns default value instead of throwing exception.</p>
     * 
     * @param input User input string
     * @param defaultValue Value to return if parsing fails
     * @return Parsed float or default value
     */
    public static float parseFloatOrDefault(String input, float defaultValue) {
        if (!isValidString(input)) {
            return defaultValue;
        }
        
        try {
            return Float.parseFloat(input.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
