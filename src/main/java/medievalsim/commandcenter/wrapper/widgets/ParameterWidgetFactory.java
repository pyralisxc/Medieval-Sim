package medievalsim.commandcenter.wrapper.widgets;

import medievalsim.commandcenter.wrapper.ParameterMetadata;
import medievalsim.commandcenter.wrapper.ParameterMetadata.ParameterHandlerType;
import medievalsim.util.ModLogger;
import necesse.engine.commands.parameterHandlers.*;
import necesse.engine.network.client.Client;

import java.lang.reflect.Field;

/**
 * Factory for creating appropriate UI widgets based on parameter metadata.
 * 
 * Optimized for SERVER ADMINISTRATION commands (creative types removed):
 * - STRING → TextInputWidget or DropdownWidget (if presets)
 * - INT/FLOAT → NumberInputWidget  
 * - BOOL → ToggleButtonWidget
 * - RELATIVE_INT → RelativeIntInputWidget (coordinates)
 * - SERVER_CLIENT → PlayerDropdownWidget ✅
 * - ENUM → EnumDropdownWidget ✅ 
 * - MULTI → MultiChoiceWidget
 * - TEAM → TeamDropdownWidget
 * 
 * REMOVED: Creative-focused widgets (ITEM, BUFF, ENCHANTMENT, ARMOR_SET, BIOME, TILE)
 * These parameter types now fallback to TextInputWidget since their commands were filtered out.
 */
public class ParameterWidgetFactory {
    
    /**
     * Extract default value from a ParameterHandler following Necesse's pattern.
     * Tries getDefault() method first, then falls back to reflection on common fields.
     */
    private static String extractDefaultValue(ParameterHandler<?> handler) {
        if (handler == null) {
            return null;
        }
        
        try {
            // First try Necesse's standard getDefault() method (requires context)
            // Note: We can't call this without Client/Server context, so skip for now
            // The getDefault() method is context-dependent and should be called during validation
            
            // Try common default field patterns via reflection
            String[] fieldNames = {"defaultValue", "def", "defValue"};
            
            for (String fieldName : fieldNames) {
                try {
                    Field field = handler.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object defaultVal = field.get(handler);
                    
                    if (defaultVal != null) {
                        // Convert to string representation
                        if (defaultVal instanceof Boolean) {
                            return String.valueOf(defaultVal);
                        } else if (defaultVal instanceof Number) {
                            return String.valueOf(defaultVal);
                        } else if (defaultVal instanceof Enum<?>) {
                            return ((Enum<?>) defaultVal).name();
                        } else {
                            return defaultVal.toString();
                        }
                    }
                } catch (NoSuchFieldException e) {
                    // Try next field name
                    continue;
                }
            }
        } catch (Exception e) {
            // Silent fail - default extraction is optional
            ModLogger.debug("Could not extract default for handler %s: %s", 
                handler.getClass().getSimpleName(), e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Create the appropriate widget for a parameter.
     * 
     * @param parameter The parameter metadata
     * @param x X position for the widget
     * @param y Y position for the widget
     * @param client The client instance (needed for PlayerDropdownWidget)
     * @return A ParameterWidget instance
     */
    public static ParameterWidget createWidget(ParameterMetadata parameter, int x, int y, Client client) {
        return createWidget(parameter, x, y, client, null);
    }
    
    /**
     * Create the appropriate widget for a parameter with command context.
     * 
     * @param parameter The parameter metadata
     * @param x X position for the widget
     * @param y Y position for the widget
     * @param client The client instance (needed for PlayerDropdownWidget)
     * @param commandId The ID of the command this parameter belongs to (for context-aware filtering)
     * @return A ParameterWidget instance
     */
    public static ParameterWidget createWidget(ParameterMetadata parameter, int x, int y, Client client, String commandId) {
        ParameterHandlerType type = parameter.getHandlerType();
        
        // Extract default value (if any)
        String defaultValue = extractDefaultValue(parameter.getHandler());
        
        switch (type) {
            // Core types (Phase 1)
            case STRING:
                if (parameter.hasPresets()) {
                    return new DropdownWidget(parameter, x, y, parameter.getPresets());
                } else {
                    return new TextInputWidget(parameter, x, y, 200, defaultValue);
                }
                
            case INT:
                return new NumberInputWidget(parameter, x, y, 100, false, defaultValue);
                
            case FLOAT:
                return new NumberInputWidget(parameter, x, y, 100, true, defaultValue);
                
            case BOOL:
                return new CheckboxWidget(parameter, x, y, client, defaultValue);
                
            case RELATIVE_INT:
                return new RelativeIntInputWidget(parameter, x, y);
                
            case SERVER_CLIENT:
                return new PlayerDropdownWidget(parameter, x, y, client);
                
            case MULTI:
                return new MultiChoiceWidget(parameter, x, y, client);
            
            // Phase 2: Extended types (server admin focused)
            case TEAM:
                return new TeamDropdownWidget(parameter, x, y);
                
            // ❌ REMOVED: Creative-focused widgets (commands filtered out)
            // case ITEM → ItemDropdownWidget (give command removed)
            // case BUFF → BuffDropdownWidget (buff commands removed)  
            // case ENCHANTMENT → EnchantmentDropdownWidget (enchant command removed)
            // case ARMOR_SET → ItemDropdownWidget (armorset command removed)
            // case BIOME → BiomeDropdownWidget (no biome commands in admin focus)
            // case TILE → TileDropdownWidget (cleararea command removed)
                
            case LANGUAGE:
                return new LanguageDropdownWidget(parameter, x, y);
                
            case PERMISSION_LEVEL:
                return new PermissionLevelDropdownWidget(parameter, x, y);
                
            case ENUM:
                return new EnumDropdownWidget(parameter, x, y);
                
            case CLIENT_CLIENT:
                // Similar to SERVER_CLIENT, reuse PlayerDropdownWidget
                return new PlayerDropdownWidget(parameter, x, y, client);
                
            case PRESET_STRING:
                // PresetStringParameterHandler has preset values - use dropdown
                if (parameter.hasPresets()) {
                    return new DropdownWidget(parameter, x, y, parameter.getPresets());
                } else {
                    return new TextInputWidget(parameter, x, y, 200, defaultValue);
                }
                
            case STORED_PLAYER:
            case LEVEL_IDENTIFIER:
            case CMD_NAME:
            case UNBAN:
                // Text input is appropriate for these
                return new TextInputWidget(parameter, x, y, 200, defaultValue);
                
            case REST_STRING:
                // Multi-line text input for "rest of command line"
                return new TextInputWidget(parameter, x, y, 400, defaultValue); // Wider for longer text
                
            // ❌ REMOVED CREATIVE TYPES: Fallback to text input (should not occur after filtering)
            case ITEM:
            case BUFF:
            case ENCHANTMENT:
            case ARMOR_SET:
            case BIOME:
            case TILE:
                ModLogger.warn("Attempted to create widget for filtered creative parameter type: %s", type);
                return new TextInputWidget(parameter, x, y, 200, defaultValue);
                
            case UNKNOWN:
            default:
                // Fallback to text input
                return new TextInputWidget(parameter, x, y, 200, defaultValue);
        }
    }
    
    /**
     * Backward compatibility - creates widget without client reference
     */
    public static ParameterWidget createWidget(ParameterMetadata parameter, int x, int y) {
        return createWidget(parameter, x, y, null);
    }
    
    /**
     * Calculate the recommended height for a widget of the given type.
     * 
     * @param type The parameter handler type
     * @return Recommended height in pixels
     */
    public static int getWidgetHeight(ParameterHandlerType type) {
        switch (type) {
            case ITEM:
            case BUFF:
                // Future enhancement: Browser widgets could use more space (200px)
                // Currently using text input (30px)
                return 30;
            case MULTI:
                // Future enhancement: Multiple choice could use space for options (80px)
                // Currently using text input (30px)
                return 30;
            default:
                return 30; // Standard input height
        }
    }
}
