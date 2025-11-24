package medievalsim.commandcenter.wrapper;

import necesse.engine.commands.CmdParameter;
import necesse.engine.commands.parameterHandlers.*;
import medievalsim.util.ModLogger;

import java.lang.reflect.Field;

/**
 * Metadata for a single command parameter extracted from Necesse's CmdParameter.
 * 
 * Stores information about:
 * - Parameter name and type
 * - Whether it's optional
 * - The handler type (determines UI widget)
 * - Extra nested parameters
 */
public class ParameterMetadata {

    private final String name;
    private final boolean optional;
    private final boolean partOfUsage;
    private final ParameterHandler<?> handler;
    private final ParameterHandlerType handlerType;
    private final ParameterMetadata[] extraParams;
    /**
     * Backing CmdParameter instance from Necesse.
     * This lets us map back into engine state (getCurrentArguments / autocomplete).
     */
    private final CmdParameter sourceParameter;
    
    // Cached reflection results (extracted once during construction)
    private final String[] cachedPresets;
    private final Enum<?>[] cachedEnumValues;
    private final ParameterHandler<?>[] cachedHandlers;

    public ParameterMetadata(String name, boolean optional, boolean partOfUsage,
                             ParameterHandler<?> handler, ParameterHandlerType handlerType,
                             ParameterMetadata[] extraParams, CmdParameter sourceParameter) {
        this.name = name;
        this.optional = optional;
        this.partOfUsage = partOfUsage;
        this.handler = handler;
        this.handlerType = handlerType;
        this.extraParams = extraParams;
        this.sourceParameter = sourceParameter;
        
        // Extract reflection data once during construction to avoid repeated runtime reflection
        this.cachedPresets = extractPresetsOnce(handler, handlerType);
        this.cachedEnumValues = extractEnumValuesOnce(handler);
        this.cachedHandlers = extractHandlersOnce(handler);
    }
    
    /**
     * Extract preset values once during construction (avoids repeated reflection).
     */
    private static String[] extractPresetsOnce(ParameterHandler<?> handler, ParameterHandlerType handlerType) {
        // Check for PresetStringParameterHandler first (more specific)
        if (handler.getClass().getSimpleName().equals("PresetStringParameterHandler")) {
            try {
                Field presetsField = handler.getClass().getDeclaredField("presets");
                presetsField.setAccessible(true);
                String[] presets = (String[]) presetsField.get(handler);
                return (presets != null && presets.length > 0) ? presets : null;
            } catch (Exception e) {
                return null;
            }
        }
        
        // Also check StringParameterHandler
        if (handlerType == ParameterHandlerType.STRING && handler instanceof StringParameterHandler) {
            try {
                Field presetsField = StringParameterHandler.class.getDeclaredField("presets");
                presetsField.setAccessible(true);
                String[] presets = (String[]) presetsField.get(handler);
                return (presets != null && presets.length > 0) ? presets : null;
            } catch (Exception e) {
                return null;
            }
        }
        
        return null;
    }
    
    /**
     * Extract enum values once during construction (avoids repeated reflection).
     */
    private static Enum<?>[] extractEnumValuesOnce(ParameterHandler<?> handler) {
        if (!(handler instanceof EnumParameterHandler)) {
            return null;
        }
        
        try {
            EnumParameterHandler<?> enumHandler = (EnumParameterHandler<?>) handler;
            Field valuesField = EnumParameterHandler.class.getDeclaredField("values");
            valuesField.setAccessible(true);
            return (Enum<?>[]) valuesField.get(enumHandler);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Extract handlers from MultiParameterHandler once during construction.
     */
    private static ParameterHandler<?>[] extractHandlersOnce(ParameterHandler<?> handler) {
        if (!(handler instanceof MultiParameterHandler)) {
            return null;
        }
        
        try {
            Field handlersField = MultiParameterHandler.class.getDeclaredField("handlers");
            handlersField.setAccessible(true);
            return (ParameterHandler<?>[]) handlersField.get(handler);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse parameter metadata from Necesse's CmdParameter using reflection.
     */
    public static ParameterMetadata fromCmdParameter(CmdParameter cmdParam) {
        try {
            // Access fields via reflection (they're public final in CmdParameter)
            String name = cmdParam.name;
            boolean optional = cmdParam.optional;
            boolean partOfUsage = cmdParam.partOfUsage;
            ParameterHandler<?> handler = cmdParam.param;

            // Determine handler type for UI widget selection
            ParameterHandlerType handlerType = determineHandlerType(handler);

            // Parse extra parameters recursively
            CmdParameter[] extraCmdParams = cmdParam.extraParams;
            ParameterMetadata[] extraParams = new ParameterMetadata[extraCmdParams.length];
            for (int i = 0; i < extraCmdParams.length; i++) {
                extraParams[i] = fromCmdParameter(extraCmdParams[i]);
            }

            return new ParameterMetadata(name, optional, partOfUsage, handler, handlerType, extraParams, cmdParam);

        } catch (Exception e) {
            ModLogger.error("Failed to parse parameter metadata for: " + cmdParam.name, e);
            return null;
        }
    }

    /**
     * Determine the parameter handler type for UI widget selection.
     */
    public static ParameterHandlerType determineHandlerType(ParameterHandler<?> handler) {
        // Check for specific handler types (order matters - most specific first)
        
        // ArmorSet extends ItemParameterHandler, so check it before ITEM
        if (handler.getClass().getSimpleName().equals("ArmorSetParameterHandler")) {
            return ParameterHandlerType.ARMOR_SET;
        }
        
        // Core types (already implemented)
        if (handler instanceof ServerClientParameterHandler) {
            return ParameterHandlerType.SERVER_CLIENT;
        } else if (handler instanceof ItemParameterHandler) {
            return ParameterHandlerType.ITEM;
        } else if (handler instanceof BuffParameterHandler) {
            return ParameterHandlerType.BUFF;
        } else if (handler instanceof RelativeIntParameterHandler) {
            return ParameterHandlerType.RELATIVE_INT;
        } else if (handler instanceof IntParameterHandler) {
            return ParameterHandlerType.INT;
        } else if (handler instanceof FloatParameterHandler) {
            return ParameterHandlerType.FLOAT;
        } else if (handler instanceof BoolParameterHandler) {
            return ParameterHandlerType.BOOL;
        } else if (handler instanceof MultiParameterHandler) {
            return ParameterHandlerType.MULTI;
        }
        
        // Extended types (Phase 2) - check by class name for non-public handlers
        String className = handler.getClass().getSimpleName();
        
        switch (className) {
            case "BiomeParameterHandler":
                return ParameterHandlerType.BIOME;
            case "EnchantmentParameterHandler":
                return ParameterHandlerType.ENCHANTMENT;
            case "TileParameterHandler":
                return ParameterHandlerType.TILE;
            case "ClientClientParameterHandler":
                return ParameterHandlerType.CLIENT_CLIENT;
            case "TeamParameterHandler":
                return ParameterHandlerType.TEAM;
            case "LanguageParameterHandler":
                return ParameterHandlerType.LANGUAGE;
            case "LevelIdentifierParameterHandler":
                return ParameterHandlerType.LEVEL_IDENTIFIER;
            case "PermissionLevelParameterHandler":
                return ParameterHandlerType.PERMISSION_LEVEL;
            case "EnumParameterHandler":
                return ParameterHandlerType.ENUM;
            case "StoredPlayerParameterHandler":
                return ParameterHandlerType.STORED_PLAYER;
            case "PresetStringParameterHandler":
                return ParameterHandlerType.PRESET_STRING;
            case "RestStringParameterHandler":
                return ParameterHandlerType.REST_STRING;
            case "CmdNameParameterHandler":
                return ParameterHandlerType.CMD_NAME;
            case "UnbanParameterHandler":
                return ParameterHandlerType.UNBAN;
            case "StringParameterHandler":
                return ParameterHandlerType.STRING;
            default:
                return ParameterHandlerType.UNKNOWN;
        }
    }
    
    // Getters
    public String getName() { return name; }
    public boolean isOptional() { return optional; }
    public boolean isRequired() { return !optional; }
    public boolean isPartOfUsage() { return partOfUsage; }
    public ParameterHandler<?> getHandler() { return handler; }
    public ParameterHandlerType getHandlerType() { return handlerType; }
    public ParameterMetadata[] getExtraParams() { return extraParams; }
    /**
     * Original CmdParameter backing this metadata (for engine lookups).
     */
    public CmdParameter getSourceParameter() { return sourceParameter; }

    /**
     * Get a display-friendly version of the parameter name.
     * Converts "playerName" to "Player Name"
     */
    public String getDisplayName() {
        if (name == null || name.isEmpty()) {
            return "";
        }
        // Simple camelCase to Title Case conversion
        StringBuilder result = new StringBuilder();
        result.append(Character.toUpperCase(name.charAt(0)));
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append(' ');
            }
            result.append(c);
        }
        return result.toString();
    }
    
    /**
     * Check if this parameter supports world-click coordinate selection.
     * Only RelativeIntParameterHandler (used for coordinates) supports this.
     */
    public boolean supportsWorldClick() {
        return handlerType == ParameterHandlerType.RELATIVE_INT;
    }
    
    /**
     * Check if this parameter has preset values (for dropdown UI).
     * Uses cached values extracted during construction (no reflection).
     */
    public boolean hasPresets() {
        return cachedPresets != null && cachedPresets.length > 0;
    }
    
    /**
     * Get preset values if this is a StringParameterHandler with presets.
     * Uses cached values extracted during construction (no reflection).
     */
    public String[] getPresets() {
        return cachedPresets != null ? cachedPresets : new String[0];
    }
    
    /**
     * Get enum values if this is an EnumParameterHandler.
     * Uses cached values extracted during construction (no reflection).
     */
    public Enum<?>[] getEnumValues() {
        return cachedEnumValues;
    }
    
    /**
     * Get handlers if this is a MultiParameterHandler.
     * Uses cached values extracted during construction (no reflection).
     */
    public ParameterHandler<?>[] getMultiHandlers() {
        return cachedHandlers;
    }
    
    @Override
    public String toString() {
        return String.format("ParameterMetadata{name='%s', type=%s, optional=%b}",
                name, handlerType, optional);
    }
    
    /**
     * Enum representing different parameter handler types for UI widget selection.
     */
    public enum ParameterHandlerType {
        // Core types (already implemented)
        SERVER_CLIENT,    // Player dropdown (online players)
        ITEM,             // Item browser/dropdown
        BUFF,             // Buff browser/dropdown
        RELATIVE_INT,     // Coordinate input (supports relative syntax: %+10, %-5)
        INT,              // Number input
        FLOAT,            // Decimal input
        BOOL,             // Checkbox/toggle
        STRING,           // Text input (may have presets for dropdown)
        MULTI,            // Multiple choice (radio buttons)
        
        // Extended types (Phase 2)
        ARMOR_SET,        // Armor items only (filtered Item dropdown)
        BIOME,            // Biome dropdown
        ENCHANTMENT,      // Enchantment dropdown
        TILE,             // Tile dropdown
        CLIENT_CLIENT,    // Client-side player (similar to SERVER_CLIENT)
        TEAM,             // Team dropdown
        LANGUAGE,         // Language dropdown
        LEVEL_IDENTIFIER, // Level/world identifier
        PERMISSION_LEVEL, // Permission level dropdown
        ENUM,             // Generic enum dropdown
        STORED_PLAYER,    // Stored/offline player (text input)
        PRESET_STRING,    // String with presets (handled by STRING)
        REST_STRING,      // Rest of command line (multi-line text)
        CMD_NAME,         // Command name (for command suggestions)
        UNBAN,            // Unban parameter (ban list)
        
        UNKNOWN           // Fallback to text input
    }
}
