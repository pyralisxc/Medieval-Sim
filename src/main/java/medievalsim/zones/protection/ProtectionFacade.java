package medievalsim.zones.protection;

import medievalsim.util.GameObjectClassifier;
import medievalsim.zones.domain.AdminZonesLevelData;
import medievalsim.zones.domain.ProtectedZone;
import medievalsim.zones.settlement.SettlementProtectionData;
import medievalsim.zones.settlement.SettlementProtectionHelper;
import necesse.engine.localization.Localization;
import necesse.engine.network.server.ServerClient;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;

/**
 * Unified protection enforcement facade for Medieval Sim mod.
 * 
 * <p>This facade consolidates ALL protection checks across zones and settlements,
 * eliminating duplication in patches and providing a single source of truth for
 * protection logic.</p>
 * 
 * <h3>Protection Precedence (IMPORTANT):</h3>
 * <ol>
 *   <li><b>Settlement Protection</b> - Checked first and takes precedence</li>
 *   <li><b>Zone Protection</b> - Checked second if settlement allows</li>
 * </ol>
 * 
 * <p>This precedence ensures that player-owned settlements (personal territory)
 * have higher authority than admin-created zones (server-managed areas).</p>
 * 
 * <h3>Usage in Patches:</h3>
 * <pre>{@code
 * // Before: 10+ lines of duplication per patch
 * AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
 * if (zoneData != null) {
 *     ProtectedZone zone = zoneData.getProtectedZoneAt(tileX, tileY);
 *     if (zone != null && !zone.canPlace) {
 *         // ... check permissions ...
 *     }
 * }
 * // ... also check settlement protection separately ...
 * 
 * // After: 3 lines with ProtectionFacade
 * ProtectionResult result = ProtectionFacade.canPlace(client, level, tileX, tileY, isObject);
 * if (result.isDenied()) {
 *     client.sendChatMessage(result.getMessage());
 *     return true; // Block action
 * }
 * }</pre>
 * 
 * @author Medieval Sim Team
 * @version 1.1.0
 * @see medievalsim.zones.domain.ProtectedZone
 * @see medievalsim.zones.settlement.SettlementProtectionHelper
 */
public final class ProtectionFacade {
    
    /**
     * Private constructor - utility class should not be instantiated.
     */
    private ProtectionFacade() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
    
    /**
     * Check if a client can place a tile or object at the given position.
     * 
     * <p><b>Protection Precedence:</b></p>
     * <ol>
     *   <li>Settlement protection checked first (if in settlement)</li>
     *   <li>Zone protection checked second (if in protected zone)</li>
     * </ol>
     * 
     * @param client The client attempting to place
     * @param level The level where placement is attempted
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @param isObject true for GameObject placement, false for tile placement
     * @return ProtectionResult indicating allowed/denied with message
     */
    public static ProtectionResult canPlace(
        ServerClient client,
        Level level,
        int tileX,
        int tileY,
        boolean isObject
    ) {
        if (client == null || level == null || !level.isServer()) {
            return ProtectionResult.allowed();
        }
        
        // PRECEDENCE #1: Settlement Protection (checked first)
        ServerSettlementData settlement = SettlementProtectionHelper.getProtectedSettlementAt(level, tileX, tileY);
        if (settlement != null) {
            if (!SettlementProtectionHelper.canClientPlace(client, level, tileX, tileY)) {
                String settlementName = settlement.networkData.getSettlementName() != null ?
                    settlement.networkData.getSettlementName().translate() : "Unknown Settlement";
                return ProtectionResult.denied("nopermissionbuild", settlementName);
            }
        }
        
        // PRECEDENCE #2: Zone Protection (checked second if settlement allowed)
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData != null) {
            ProtectedZone zone = zoneData.getProtectedZoneAt(tileX, tileY);
            if (zone != null) {
                if (!zone.canClientPlace(client, level)) {
                    return ProtectionResult.denied("nopermissionbuild", zone.name);
                }
            }
        }
        
        return ProtectionResult.allowed();
    }
    
    /**
     * Check if a client can break a tile or object at the given position.
     * 
     * @param client The client attempting to break
     * @param level The level where breaking is attempted
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @param isObject true for GameObject breaking, false for tile breaking
     * @return ProtectionResult indicating allowed/denied with message
     */
    public static ProtectionResult canBreak(
        ServerClient client,
        Level level,
        int tileX,
        int tileY,
        boolean isObject
    ) {
        if (client == null || level == null || !level.isServer()) {
            return ProtectionResult.allowed();
        }
        
        // PRECEDENCE #1: Settlement Protection
        ServerSettlementData settlement = SettlementProtectionHelper.getProtectedSettlementAt(level, tileX, tileY);
        if (settlement != null) {
            if (!SettlementProtectionHelper.canClientBreak(client, level, tileX, tileY)) {
                String settlementName = settlement.networkData.getSettlementName() != null ?
                    settlement.networkData.getSettlementName().translate() : "Unknown Settlement";
                return ProtectionResult.denied("nopermissionbreak", settlementName);
            }
        }
        
        // PRECEDENCE #2: Zone Protection
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData != null) {
            ProtectedZone zone = zoneData.getProtectedZoneAt(tileX, tileY);
            if (zone != null) {
                if (!zone.canClientBreak(client, level)) {
                    return ProtectionResult.denied("nopermissionbreak", zone.name);
                }
            }
        }
        
        return ProtectionResult.allowed();
    }
    
    /**
     * Check if a client can interact with a GameObject.
     * Uses GameObjectClassifier for consistent type detection.
     * 
     * @param client The client attempting interaction
     * @param level The level where interaction is attempted
     * @param tileX Tile X coordinate of the GameObject
     * @param tileY Tile Y coordinate of the GameObject
     * @param gameObject The GameObject being interacted with
     * @return ProtectionResult indicating allowed/denied with message
     */
    public static ProtectionResult canInteract(
        ServerClient client,
        Level level,
        int tileX,
        int tileY,
        GameObject gameObject
    ) {
        if (client == null || level == null || !level.isServer() || gameObject == null) {
            return ProtectionResult.allowed();
        }
        
        // Classify GameObject type using centralized classifier
        GameObjectClassifier.InteractionType type = GameObjectClassifier.classify(gameObject);
        
        // PRECEDENCE #1: Settlement Protection
        SettlementProtectionHelper.SettlementProtectionContext context = 
            SettlementProtectionHelper.getProtectionContext(level, tileX, tileY);
        if (context != null) {
            boolean hasPermission = checkSettlementInteractionPermission(client, level, context, type);
            if (!hasPermission) {
                String settlementName = context.settlement().networkData.getSettlementName() != null ?
                    context.settlement().networkData.getSettlementName().translate() : "Unknown Settlement";
                return ProtectionResult.denied(type.errorMessageKey, settlementName);
            }
        }
        
        // PRECEDENCE #2: Zone Protection
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData != null) {
            ProtectedZone zone = zoneData.getProtectedZoneAt(tileX, tileY);
            if (zone != null) {
                if (!zone.canClientInteract(client, level, gameObject)) {
                    return ProtectionResult.denied(type.errorMessageKey, zone.name);
                }
            }
        }
        
        return ProtectionResult.allowed();
    }
    
    /**
     * Check settlement interaction permission based on GameObject type.
     * 
     * @param client The client attempting interaction
     * @param level The level
     * @param context Settlement protection context
     * @param type The classified GameObject interaction type
     * @return true if client has permission, false otherwise
     */
    private static boolean checkSettlementInteractionPermission(
        ServerClient client,
        Level level,
        SettlementProtectionHelper.SettlementProtectionContext context,
        GameObjectClassifier.InteractionType type
    ) {
        // Check elevated access first (owner/team bypass permissions)
        if (SettlementProtectionHelper.hasElevatedAccess(client, level, context.settlement())) {
            return true;
        }
        
        // Check granular permissions based on GameObject type
        SettlementProtectionData data = context.data();
        switch (type) {
            case DOOR:
                return data.getCanInteractDoors();
            case CRAFTING_STATION:
                return data.getCanInteractStations();
            case CONTAINER:
                return data.getCanInteractContainers();
            case SIGN:
                return data.getCanInteractSigns();
            case SWITCH:
                return data.getCanInteractSwitches();
            case FURNITURE:
                return data.getCanInteractFurniture();
            case UNKNOWN:
            default:
                // Unknown types denied by default in protected areas
                return false;
        }
    }
    
    /**
     * Result of a protection check.
     * 
     * <p>Contains both allow/deny status and localized error message for
     * displaying to users when actions are blocked.</p>
     */
    public static class ProtectionResult {
        private final boolean allowed;
        private final String messageKey;
        private final String zoneName;
        
        private ProtectionResult(boolean allowed, String messageKey, String zoneName) {
            this.allowed = allowed;
            this.messageKey = messageKey;
            this.zoneName = zoneName;
        }
        
        /**
         * Create an "allowed" result (action permitted).
         */
        public static ProtectionResult allowed() {
            return new ProtectionResult(true, null, null);
        }
        
        /**
         * Create a "denied" result with error message details.
         * 
         * @param messageKey Localization key for the error message
         * @param zoneName Name of the zone/settlement blocking the action
         */
        public static ProtectionResult denied(String messageKey, String zoneName) {
            return new ProtectionResult(false, messageKey, zoneName);
        }
        
        /**
         * Check if the action is allowed.
         * @return true if allowed, false if denied
         */
        public boolean isAllowed() {
            return allowed;
        }
        
        /**
         * Check if the action is denied.
         * @return true if denied, false if allowed
         */
        public boolean isDenied() {
            return !allowed;
        }
        
        /**
         * Get the localized error message to display to the user.
         * Only valid if isDenied() == true.
         * 
         * @return Localized error message (e.g., "You don't have permission to build in Settlement Name")
         */
        public String getMessage() {
            if (allowed || messageKey == null) {
                return "";
            }
            return Localization.translate("ui", messageKey);
        }
        
        /**
         * Get the name of the zone/settlement that blocked the action.
         * Only valid if isDenied() == true.
         * 
         * @return Zone or settlement name
         */
        public String getZoneName() {
            return zoneName;
        }
    }
}
