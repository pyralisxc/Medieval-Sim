package medievalsim.util;

import necesse.engine.localization.Localization;

/**
 * Error message builder for consistent error formatting.
 * 
 * <p>Centralizes error message creation to ensure consistent patterns,
 * proper localization support, and maintainable error handling.</p>
 * 
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // Permission denied message
 * String error = ErrorMessageBuilder.buildPermissionDeniedMessage("Settlement Alpha", "place blocks");
 * 
 * // Invalid input message
 * String error = ErrorMessageBuilder.buildInvalidInputMessage("price", "must be greater than zero");
 * 
 * // Cooldown message
 * String error = ErrorMessageBuilder.buildCooldownMessage(30.5f); // "Please wait 30.5 seconds"
 * }</pre>
 * 
 * @since 1.0
 */
public final class ErrorMessageBuilder {
    
    /**
     * Private constructor - utility class should not be instantiated.
     */
    private ErrorMessageBuilder() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
    
    // ===== PERMISSION ERRORS =====
    
    /**
     * Build a permission denied message for location-specific actions.
     * 
     * <p>Example: "You don't have permission to place blocks in Settlement Alpha"</p>
     * 
     * @param location Location name (e.g., zone name, settlement name)
     * @param action Action that was denied (e.g., "place blocks", "break tiles")
     * @return Formatted error message
     */
    public static String buildPermissionDeniedMessage(String location, String action) {
        return String.format("You don't have permission to %s in %s", action, location);
    }
    
    /**
     * Build a generic permission denied message.
     * 
     * <p>Example: "You don't have permission to use this feature"</p>
     * 
     * @param action Action that was denied
     * @return Formatted error message
     */
    public static String buildPermissionDeniedMessage(String action) {
        return String.format("You don't have permission to %s", action);
    }
    
    /**
     * Build a localized permission denied message.
     * 
     * @param locKey Localization key for the specific denial reason
     * @param locationName Location name to insert
     * @return Localized error message
     */
    public static String buildLocalizedPermissionDenied(String locKey, String locationName) {
        return Localization.translate("zones", locKey, "name", locationName);
    }
    
    // ===== INPUT VALIDATION ERRORS =====
    
    /**
     * Build an invalid input error message.
     * 
     * <p>Example: "Invalid price: must be greater than zero"</p>
     * 
     * @param fieldName Field name (e.g., "price", "quantity")
     * @param reason Reason for invalidity
     * @return Formatted error message
     */
    public static String buildInvalidInputMessage(String fieldName, String reason) {
        return String.format("Invalid %s: %s", fieldName, reason);
    }
    
    /**
     * Build a required field error message.
     * 
     * <p>Example: "Zone name is required"</p>
     * 
     * @param fieldName Field name
     * @return Formatted error message
     */
    public static String buildRequiredFieldMessage(String fieldName) {
        return String.format("%s is required", fieldName);
    }
    
    /**
     * Build an out-of-range error message.
     * 
     * <p>Example: "Price must be between 1 and 999999 (got 0)"</p>
     * 
     * @param fieldName Field name
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @param actual Actual value provided
     * @return Formatted error message
     */
    public static String buildOutOfRangeMessage(String fieldName, int min, int max, int actual) {
        return String.format("%s must be between %d and %d (got %d)", 
            fieldName, min, max, actual);
    }
    
    /**
     * Build an out-of-range error message for floating point values.
     * 
     * @param fieldName Field name
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @param actual Actual value provided
     * @return Formatted error message
     */
    public static String buildOutOfRangeMessage(String fieldName, float min, float max, float actual) {
        return String.format("%s must be between %.2f and %.2f (got %.2f)", 
            fieldName, min, max, actual);
    }
    
    // ===== COOLDOWN ERRORS =====
    
    /**
     * Build a cooldown message with remaining time.
     * 
     * <p>Example: "Please wait 30.5 seconds before trying again"</p>
     * 
     * @param remainingSeconds Seconds remaining on cooldown
     * @return Formatted cooldown message
     */
    public static String buildCooldownMessage(float remainingSeconds) {
        if (remainingSeconds <= 0) {
            return "Ready";
        }
        
        if (remainingSeconds < 60) {
            return String.format("Please wait %.1f seconds before trying again", remainingSeconds);
        } else {
            float minutes = remainingSeconds / 60.0f;
            return String.format("Please wait %.1f minutes before trying again", minutes);
        }
    }
    
    /**
     * Build a cooldown message for specific action.
     * 
     * <p>Example: "You must wait 30.5 seconds before creating another offer"</p>
     * 
     * @param action Action on cooldown
     * @param remainingSeconds Seconds remaining
     * @return Formatted cooldown message
     */
    public static String buildCooldownMessage(String action, float remainingSeconds) {
        if (remainingSeconds <= 0) {
            return String.format("You can now %s", action);
        }
        
        if (remainingSeconds < 60) {
            return String.format("You must wait %.1f seconds before %s", remainingSeconds, action);
        } else {
            float minutes = remainingSeconds / 60.0f;
            return String.format("You must wait %.1f minutes before %s", minutes, action);
        }
    }
    
    // ===== RESOURCE ERRORS =====
    
    /**
     * Build a "not found" error message.
     * 
     * <p>Example: "Zone with ID 42 not found"</p>
     * 
     * @param resourceType Type of resource (e.g., "Zone", "Offer", "Player")
     * @param identifier Resource identifier
     * @return Formatted error message
     */
    public static String buildNotFoundMessage(String resourceType, String identifier) {
        return String.format("%s with ID %s not found", resourceType, identifier);
    }
    
    /**
     * Build a "not found" error message for numeric IDs.
     * 
     * @param resourceType Type of resource
     * @param id Numeric identifier
     * @return Formatted error message
     */
    public static String buildNotFoundMessage(String resourceType, int id) {
        return String.format("%s with ID %d not found", resourceType, id);
    }
    
    /**
     * Build an "already exists" error message.
     * 
     * <p>Example: "A zone with name 'MyZone' already exists"</p>
     * 
     * @param resourceType Type of resource
     * @param identifier Resource identifier
     * @return Formatted error message
     */
    public static String buildAlreadyExistsMessage(String resourceType, String identifier) {
        return String.format("A %s with name '%s' already exists", resourceType, identifier);
    }
    
    /**
     * Build an "insufficient resources" error message.
     * 
     * <p>Example: "Insufficient coins: need 1000, have 500"</p>
     * 
     * @param resourceName Resource name (e.g., "coins", "items")
     * @param required Amount required
     * @param available Amount available
     * @return Formatted error message
     */
    public static String buildInsufficientResourcesMessage(String resourceName, int required, int available) {
        return String.format("Insufficient %s: need %d, have %d", 
            resourceName, required, available);
    }
    
    // ===== OPERATION ERRORS =====
    
    /**
     * Build an operation failed message.
     * 
     * <p>Example: "Failed to create zone: invalid coordinates"</p>
     * 
     * @param operation Operation that failed
     * @param reason Reason for failure
     * @return Formatted error message
     */
    public static String buildOperationFailedMessage(String operation, String reason) {
        return String.format("Failed to %s: %s", operation, reason);
    }
    
    /**
     * Build a generic operation failed message.
     * 
     * @param operation Operation that failed
     * @return Formatted error message
     */
    public static String buildOperationFailedMessage(String operation) {
        return String.format("Failed to %s", operation);
    }
    
    // ===== SUCCESS MESSAGES =====
    
    /**
     * Build a success message for operations.
     * 
     * <p>Example: "Successfully created zone 'MyZone'"</p>
     * 
     * @param operation Operation that succeeded
     * @param target Target of operation
     * @return Formatted success message
     */
    public static String buildSuccessMessage(String operation, String target) {
        return String.format("Successfully %s '%s'", operation, target);
    }
    
    /**
     * Build a generic success message.
     * 
     * @param operation Operation that succeeded
     * @return Formatted success message
     */
    public static String buildSuccessMessage(String operation) {
        return String.format("Successfully %s", operation);
    }
}
