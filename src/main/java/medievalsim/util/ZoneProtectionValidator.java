package medievalsim.util;

import necesse.engine.network.server.ServerClient;
import necesse.engine.util.GameMath;
import necesse.level.maps.Level;
import medievalsim.zones.AdminZonesLevelData;
import medievalsim.zones.ProtectedZone;

/**
 * Centralized validator for zone protection checks across all patches.
 * 
 * This utility consolidates protection logic that was previously duplicated
 * in ObjectItemCanPlacePatch, TileItemCanPlacePatch, ObjectItemOnAttackPatch,
 * and TileItemOnAttackPatch.
 * 
 * Benefits:
 * - Single source of truth for protection validation
 * - Consistent behavior across all item types
 * - Easier to extend (e.g., build zones, event zones)
 * - Reduced code duplication (~100 lines saved)
 */
public final class ZoneProtectionValidator {
    
    private ZoneProtectionValidator() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Result of a protection validation check.
     */
    public static class ValidationResult {
        private final boolean allowed;
        private final String reason;
        
        private ValidationResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public String getReason() {
            return reason;
        }
        
        public static ValidationResult allow() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult deny(String reason) {
            return new ValidationResult(false, reason);
        }
    }
    
    /**
     * Validate if a player can place an object/tile at the specified coordinates.
     * 
     * @param level The level/world
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @param client The player's server client
     * @return ValidationResult indicating if placement is allowed
     */
    public static ValidationResult validatePlacement(Level level, int tileX, int tileY, ServerClient client) {
        // Only perform validation on server-side
        if (!ValidationUtil.isValidServerLevel(level) || client == null) {
            return ValidationResult.allow();
        }
        
        // Get zone data for this level
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData == null) {
            return ValidationResult.allow();
        }
        
        // Check if this location is in a protected zone
        ProtectedZone zone = zoneData.getProtectedZoneAt(tileX, tileY);
        if (zone == null) {
            return ValidationResult.allow();
        }
        
        // Check if player has permission to place in this zone
        if (!zone.canClientPlace(client, level)) {
            return ValidationResult.deny("protectedzone");
        }
        
        return ValidationResult.allow();
    }
    
    /**
     * Validate if a player can place an object at position coordinates (converts to tile coords).
     * 
     * @param level The level/world
     * @param x Position X coordinate (will be converted to tile)
     * @param y Position Y coordinate (will be converted to tile)
     * @param client The player's server client
     * @return ValidationResult indicating if placement is allowed
     */
    public static ValidationResult validatePlacementAtPosition(Level level, int x, int y, ServerClient client) {
        int tileX = GameMath.getTileCoordinate(x);
        int tileY = GameMath.getTileCoordinate(y);
        return validatePlacement(level, tileX, tileY, client);
    }
    
    /**
     * Validate if a player can break/attack an object/tile at the specified coordinates.
     * 
     * @param level The level/world
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @param client The player's server client
     * @return ValidationResult indicating if breaking is allowed
     */
    public static ValidationResult validateBreak(Level level, int tileX, int tileY, ServerClient client) {
        // Only perform validation on server-side
        if (!ValidationUtil.isValidServerLevel(level) || client == null) {
            return ValidationResult.allow();
        }
        
        // Get zone data for this level
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData == null) {
            return ValidationResult.allow();
        }
        
        // Check if this location is in a protected zone
        ProtectedZone zone = zoneData.getProtectedZoneAt(tileX, tileY);
        if (zone == null) {
            return ValidationResult.allow();
        }
        
        // Check if player has permission to break in this zone
        if (!zone.canClientBreak(client, level)) {
            return ValidationResult.deny("protectedzone");
        }
        
        return ValidationResult.allow();
    }
    
    /**
     * Validate if a player can break/attack at position coordinates (converts to tile coords).
     * 
     * @param level The level/world
     * @param x Position X coordinate (will be converted to tile)
     * @param y Position Y coordinate (will be converted to tile)
     * @param client The player's server client
     * @return ValidationResult indicating if breaking is allowed
     */
    public static ValidationResult validateBreakAtPosition(Level level, int x, int y, ServerClient client) {
        int tileX = GameMath.getTileCoordinate(x);
        int tileY = GameMath.getTileCoordinate(y);
        return validateBreak(level, tileX, tileY, client);
    }
}
