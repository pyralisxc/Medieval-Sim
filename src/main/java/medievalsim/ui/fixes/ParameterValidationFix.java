package medievalsim.ui.fixes;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import medievalsim.commandcenter.wrapper.NecesseCommandMetadata;
import medievalsim.util.ModLogger;

import java.util.Map;
import java.util.HashMap;

/**
 * Parameter Validation Fix - Addresses issues with parameter validation failing
 * 
 * Problem: Commands fail with "Required parameter" errors even when values are selected
 * Solution: Enhanced parameter validation and value conversion
 */
public class ParameterValidationFix {
    
    /**
     * Validate and fix parameter values before command execution
     * 
     * @param commandId The command being executed
     * @param parameterValues Raw parameter values from UI
     * @param parameterMetadata Metadata about expected parameters
     * @return Fixed parameter values ready for command execution
     */
    public static Map<String, String> validateAndFixParameters(
            String commandId, 
            Map<String, String> parameterValues, 
            Map<String, ParameterMetadata> parameterMetadata) {
        
        Map<String, String> fixedValues = new HashMap<>();
        
        for (Map.Entry<String, ParameterMetadata> entry : parameterMetadata.entrySet()) {
            String paramName = entry.getKey();
            ParameterMetadata metadata = entry.getValue();
            String rawValue = parameterValues.get(paramName);
            
            // Fix the parameter value based on its type and requirements
            String fixedValue = fixParameterValue(paramName, rawValue, metadata);
            
            if (fixedValue != null) {
                fixedValues.put(paramName, fixedValue);
                ModLogger.info("[ParameterFix] Fixed parameter '{}': '{}' -> '{}'", paramName, rawValue, fixedValue);
            } else if (!metadata.isOptional()) {
                ModLogger.warn("[ParameterFix] Required parameter '{}' is missing or invalid: '{}'", paramName, rawValue);
            }
        }
        
        return fixedValues;
    }
    
    /**
     * Fix an individual parameter value based on its metadata
     */
    private static String fixParameterValue(String paramName, String rawValue, ParameterMetadata metadata) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            if (metadata.isOptional()) {
                return null; // Optional parameter can be null
            } else {
                // Required parameter is missing
                return null;
            }
        }
        
        String trimmedValue = rawValue.trim();
        
        // Handle different parameter types
        switch (metadata.getHandlerType()) {
            case ENUM:
                return fixEnumParameter(paramName, trimmedValue, metadata);
            case STRING:
            case PRESET_STRING:
            case REST_STRING:
                return fixStringParameter(paramName, trimmedValue, metadata);
            case INT:
                return fixIntParameter(paramName, trimmedValue, metadata);
            case FLOAT:
                return fixFloatParameter(paramName, trimmedValue, metadata);
            case BOOL:
                return fixBooleanParameter(paramName, trimmedValue, metadata);
            case SERVER_CLIENT:
            case STORED_PLAYER:
                return fixPlayerParameter(paramName, trimmedValue, metadata);
            default:
                return trimmedValue;
        }
    }
    
    /**
     * Fix enum parameter values (like list/penalty, list/frequency)
     */
    private static String fixEnumParameter(String paramName, String value, ParameterMetadata metadata) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        
        // For enum parameters, the value should be the enum constant name
        // Handle common cases where UI shows display names but needs enum values
        
        // Special handling for common problematic parameters
        if (paramName.contains("list/penalty")) {
            return fixDeathPenaltyEnum(value);
        } else if (paramName.contains("list/frequency")) {
            return fixRaidFrequencyEnum(value);
        }
        
        // For other enums, return the value as-is (assuming it's already correct)
        return value.toUpperCase(); // Most Necesse enums are uppercase
    }
    
    /**
     * Fix death penalty enum values
     */
    private static String fixDeathPenaltyEnum(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        
        // Convert display names to enum values
        String upper = value.toUpperCase();
        switch (upper) {
            case "NONE":
            case "NO PENALTY":
                return "NONE";
            case "DROP_MATS":
            case "DROP MATS":
            case "DROP MATERIALS":
                return "DROP_MATS";
            case "DROP_MAIN_INVENTORY":
            case "DROP MAIN INVENTORY":
            case "DROP MAIN":
                return "DROP_MAIN_INVENTORY";
            case "DROP_FULL_INVENTORY":
            case "DROP FULL INVENTORY":
            case "DROP ALL":
                return "DROP_FULL_INVENTORY";
            case "HARDCORE":
                return "HARDCORE";
            default:
                // If it's already a valid enum value, return it
                return upper;
        }
    }
    
    /**
     * Fix raid frequency enum values
     */
    private static String fixRaidFrequencyEnum(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        
        String upper = value.toUpperCase();
        switch (upper) {
            case "OFTEN":
                return "OFTEN";
            case "OCCASIONALLY":
            case "SOMETIMES":
                return "OCCASIONALLY";
            case "RARELY":
            case "SELDOM":
                return "RARELY";
            case "NEVER":
            case "OFF":
            case "DISABLED":
                return "NEVER";
            default:
                return upper;
        }
    }
    
    /**
     * Fix string parameter values
     */
    private static String fixStringParameter(String paramName, String value, ParameterMetadata metadata) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        // For string parameters, just return the trimmed value
        // unless there are specific formatting requirements
        return value.trim();
    }
    
    /**
     * Fix integer parameter values
     */
    private static String fixIntParameter(String paramName, String value, ParameterMetadata metadata) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        try {
            int intValue = Integer.parseInt(value.trim());
            return String.valueOf(intValue);
        } catch (NumberFormatException e) {
            ModLogger.warn("[ParameterFix] Invalid integer value for '{}': '{}'", paramName, value);
            return null;
        }
    }
    
    /**
     * Fix float parameter values
     */
    private static String fixFloatParameter(String paramName, String value, ParameterMetadata metadata) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        try {
            float floatValue = Float.parseFloat(value.trim());
            return String.valueOf(floatValue);
        } catch (NumberFormatException e) {
            ModLogger.warn("[ParameterFix] Invalid float value for '{}': '{}'", paramName, value);
            return null;
        }
    }
    
    /**
     * Fix boolean parameter values
     */
    private static String fixBooleanParameter(String paramName, String value, ParameterMetadata metadata) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        String lower = value.trim().toLowerCase();
        switch (lower) {
            case "true":
            case "yes":
            case "on":
            case "1":
            case "enabled":
                return "true";
            case "false":
            case "no":
            case "off":
            case "0":
            case "disabled":
                return "false";
            default:
                ModLogger.warn("[ParameterFix] Invalid boolean value for '{}': '{}'", paramName, value);
                return null;
        }
    }
    
    /**
     * Fix player parameter values (for ban, kick, etc.)
     */
    private static String fixPlayerParameter(String paramName, String value, ParameterMetadata metadata) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        // For player parameters, the value should be the player name or authentication
        // Handle both online and offline players
        String trimmedValue = value.trim();
        
        // Remove any display formatting if present (like "PlayerName (Online)")
        if (trimmedValue.contains(" (")) {
            trimmedValue = trimmedValue.substring(0, trimmedValue.indexOf(" ("));
        }
        
        return trimmedValue;
    }
    
    /**
     * Enhanced validation for specific problematic commands
     */
    public static class CommandSpecificFixes {
        
        /**
         * Fix parameters specifically for the 'ban' command
         */
        public static Map<String, String> fixBanCommandParameters(Map<String, String> params) {
            Map<String, String> fixed = new HashMap<>(params);
            
            // The ban command expects 'authentication/name' parameter
            String authName = fixed.get("authentication/name");
            if (authName != null && !authName.trim().isEmpty()) {
                // Ensure it's a clean player name
                authName = authName.trim();
                if (authName.contains(" (")) {
                    authName = authName.substring(0, authName.indexOf(" ("));
                }
                fixed.put("authentication/name", authName);
            }
            
            return fixed;
        }
        
        /**
         * Fix parameters specifically for the 'deathpenalty' command
         */
        public static Map<String, String> fixDeathPenaltyCommandParameters(Map<String, String> params) {
            Map<String, String> fixed = new HashMap<>(params);
            
            // The deathpenalty command requires a list/penalty parameter
            String penalty = fixed.get("list/penalty");
            if (penalty == null || penalty.trim().isEmpty()) {
                ModLogger.warn("[ParameterFix] Death penalty command missing required list/penalty parameter");
                return fixed;
            }
            
            // Fix the penalty value
            penalty = fixDeathPenaltyEnum(penalty);
            if (penalty != null) {
                fixed.put("list/penalty", penalty);
            }
            
            return fixed;
        }
    }
    
    /**
     * Array-based parameter validation for Command Center integration
     * Validates and fixes parameter values provided as String array
     * 
     * @param command The command metadata
     * @param parameterValues String array of parameter values in order
     * @return Fixed parameter values as String array
     */
    public static String[] validateAndFixParameters(NecesseCommandMetadata command, String[] parameterValues) {
        if (command == null || parameterValues == null) {
            return parameterValues;
        }
        
        try {
            // Convert array to map for existing validation logic
            Map<String, String> valueMap = new HashMap<>();
            Map<String, ParameterMetadata> metadataMap = new HashMap<>();
            
            java.util.List<ParameterMetadata> parameters = command.getParameters();
            for (int i = 0; i < parameters.size() && i < parameterValues.length; i++) {
                ParameterMetadata param = parameters.get(i);
                String value = parameterValues[i];
                
                valueMap.put(param.getName(), value);
                metadataMap.put(param.getName(), param);
            }
            
            // Apply validation fixes
            Map<String, String> fixedValues = validateAndFixParameters(command.getId(), valueMap, metadataMap);
            
            // Convert back to array format
            String[] result = new String[parameterValues.length];
            for (int i = 0; i < parameters.size() && i < result.length; i++) {
                ParameterMetadata param = parameters.get(i);
                String fixedValue = fixedValues.get(param.getName());
                result[i] = fixedValue != null ? fixedValue : parameterValues[i];
            }
            
            return result;
            
        } catch (Exception e) {
            ModLogger.error("[ParameterFix] Error validating parameters for command " + command.getId(), e);
            return parameterValues; // Return original on error
        }
    }
}
