package medievalsim.util;

import necesse.engine.localization.Localization;
import necesse.level.gameObject.GameObject;

/**
 * Centralized GameObject type detection and classification system.
 * 
 * <p>This utility eliminates duplication of GameObject type checking logic
 * across patches and protection systems. It provides a single source of truth
 * for determining how GameObjects should be classified for permission checks.</p>
 * 
 * <h3>Critical Design Notes:</h3>
 * <ul>
 *   <li><b>Check Order Matters!</b> CraftingStationObject extends InventoryObject,
 *       so crafting stations MUST be checked before containers.</li>
 *   <li><b>Necesse Compliance:</b> The instanceof checks follow Necesse's class hierarchy.</li>
 *   <li><b>Extensibility:</b> New GameObject types (altars, portals, traps) can be added
 *       by extending the InteractionType enum.</li>
 * </ul>
 * 
 * <h3>Usage Examples:</h3>
 * <pre>{@code
 * // Classify a GameObject
 * InteractionType type = GameObjectClassifier.classify(gameObject);
 * 
 * // Get error message for blocked interaction
 * String message = GameObjectClassifier.getBlockedMessage(gameObject);
 * 
 * // Get display name for tooltips
 * String displayName = GameObjectClassifier.getDisplayName(gameObject);
 * }</pre>
 * 
 * @author Medieval Sim Team
 * @version 1.1.0
 * @see medievalsim.zones.domain.ProtectedZone
 * @see medievalsim.patches.GameObjectCanInteractPatch
 */
public final class GameObjectClassifier {
    
    /**
     * Private constructor - utility class should not be instantiated.
     */
    private GameObjectClassifier() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
    
    /**
     * GameObject interaction categories aligned with zone protection permissions.
     * 
     * <p>Each enum value contains three keys for different use cases:
     * <ul>
     *   <li><b>permissionKey:</b> Matches ProtectedZone boolean fields (e.g., "canInteractDoors")</li>
     *   <li><b>errorMessageKey:</b> Localization key for "blocked interaction" messages</li>
     *   <li><b>displayNameKey:</b> Localization key for user-friendly type names in UI</li>
     * </ul>
     */
    public enum InteractionType {
        /**
         * Doors - Any GameObject with isDoor=true.
         */
        DOOR("doors", "nopermissiondoors", "objecttype.door"),
        
        /**
         * Crafting stations - CraftingStationObject or FueledCraftingStationObject.
         * <b>MUST be checked before CONTAINER</b> (CraftingStation extends InventoryObject).
         */
        CRAFTING_STATION("stations", "nopermissionstations", "objecttype.station"),
        
        /**
         * Containers/Chests - InventoryObject (but NOT crafting stations).
         */
        CONTAINER("containers", "nopermissioncontainers", "objecttype.container"),
        
        /**
         * Signs - SignObject.
         */
        SIGN("signs", "nopermissionsigns", "objecttype.sign"),
        
        /**
         * Switches/Levers - GameObjects with isSwitch=true or isPressurePlate=true.
         */
        SWITCH("switches", "nopermissionswitches", "objecttype.switch"),
        
        /**
         * Furniture - FurnitureObject (beds, chairs, decorative items).
         */
        FURNITURE("furniture", "nopermissionfurniture", "objecttype.furniture"),
        
        /**
         * Unknown/Unclassified - Fallback for GameObjects not matching other categories.
         */
        UNKNOWN("unknown", "nopermissioninteract", "objecttype.object");
        
        /**
         * Permission key matching ProtectedZone field names.
         * Example: "doors" maps to ProtectedZone.canInteractDoors
         */
        public final String permissionKey;
        
        /**
         * Localization key for error messages when interaction is blocked.
         * Example: "nopermissiondoors" → "You don't have permission to use doors in this zone"
         */
        public final String errorMessageKey;
        
        /**
         * Localization key for user-friendly type names in tooltips.
         * Example: "objecttype.door" → "Door"
         */
        public final String displayNameKey;
        
        InteractionType(String permissionKey, String errorMessageKey, String displayNameKey) {
            this.permissionKey = permissionKey;
            this.errorMessageKey = errorMessageKey;
            this.displayNameKey = displayNameKey;
        }
    }
    
    /**
     * Classify a GameObject into its interaction type category.
     * 
     * <p><b>Critical:</b> Check order follows Necesse class hierarchy.
     * CraftingStationObject is checked BEFORE InventoryObject because
     * it extends InventoryObject.</p>
     * 
     * @param gameObject The GameObject to classify (can be null)
     * @return The classified InteractionType (never null - returns UNKNOWN for null input)
     */
    public static InteractionType classify(GameObject gameObject) {
        if (gameObject == null) {
            return InteractionType.UNKNOWN;
        }
        
        // Order CRITICAL - CraftingStation extends InventoryObject!
        
        // 1. Doors - simplest check (boolean flag)
        if (gameObject.isDoor) {
            return InteractionType.DOOR;
        }
        
        // 2. Crafting stations - MUST check before containers
        if (gameObject instanceof necesse.level.gameObject.container.CraftingStationObject ||
            gameObject instanceof necesse.level.gameObject.container.FueledCraftingStationObject) {
            return InteractionType.CRAFTING_STATION;
        }
        
        // 3. Containers - after crafting stations
        if (gameObject instanceof necesse.level.gameObject.container.InventoryObject) {
            return InteractionType.CONTAINER;
        }
        
        // 4. Signs
        if (gameObject instanceof necesse.level.gameObject.SignObject) {
            return InteractionType.SIGN;
        }
        
        // 5. Switches/Levers
        if (gameObject.isSwitch || gameObject.isPressurePlate) {
            return InteractionType.SWITCH;
        }
        
        // 6. Furniture
        if (gameObject instanceof necesse.level.gameObject.furniture.FurnitureObject) {
            return InteractionType.FURNITURE;
        }
        
        // 7. Unknown/Unclassified
        ModLogger.debug("Unclassified GameObject: %s (ID: %s)", 
            gameObject.getClass().getSimpleName(), 
            gameObject.getStringID());
        return InteractionType.UNKNOWN;
    }
    
    /**
     * Get a localized error message for a blocked GameObject interaction.
     * 
     * <p>This is used by patches to send chat messages when a player
     * attempts an interaction they don't have permission for.</p>
     * 
     * @param gameObject The GameObject that was blocked
     * @return Localized error message (e.g., "You don't have permission to use doors")
     */
    public static String getBlockedMessage(GameObject gameObject) {
        InteractionType type = classify(gameObject);
        return Localization.translate("ui", type.errorMessageKey);
    }
    
    /**
     * Get a localized display name for a GameObject type.
     * 
     * <p>This is used in tooltips and UI to show human-readable object type names
     * (e.g., "Door", "Crafting Station", "Container").</p>
     * 
     * @param gameObject The GameObject to get a display name for
     * @return Localized display name (e.g., "Door", "Chest", "Sign")
     */
    public static String getDisplayName(GameObject gameObject) {
        InteractionType type = classify(gameObject);
        return Localization.translate("ui", type.displayNameKey);
    }
    
    /**
     * Get the permission field name for a GameObject type.
     * 
     * <p>This maps GameObject types to ProtectedZone boolean field names.
     * Example: DOOR → "canInteractDoors"</p>
     * 
     * @param gameObject The GameObject to get permission key for
     * @return Permission field name (e.g., "doors", "stations", "containers")
     */
    public static String getPermissionKey(GameObject gameObject) {
        InteractionType type = classify(gameObject);
        return type.permissionKey;
    }
    
    /**
     * Check if a GameObject is a known/classified type.
     * 
     * @param gameObject The GameObject to check
     * @return true if classified (not UNKNOWN), false if UNKNOWN or null
     */
    public static boolean isKnownType(GameObject gameObject) {
        return classify(gameObject) != InteractionType.UNKNOWN;
    }
}
