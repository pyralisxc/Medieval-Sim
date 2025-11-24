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
        medievalsim.util.ModLogger.info("===== PLACEMENT VALIDATION START =====");
        medievalsim.util.ModLogger.info("validatePlacement called: player=%s, tileX=%d, tileY=%d", 
            client != null ? client.getName() : "null", tileX, tileY);
        
        // Only perform validation on server-side
        if (!ValidationUtil.isValidServerLevel(level) || client == null) {
            medievalsim.util.ModLogger.info("validatePlacement: ALLOW - not valid server level or client is null");
            return ValidationResult.allow();
        }
        
        // Get zone data for this level
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData == null) {
            medievalsim.util.ModLogger.info("validatePlacement: ALLOW - no zone data for this level");
            return ValidationResult.allow();
        }
        
        // Check if this location is in a protected zone
        ProtectedZone zone = zoneData.getProtectedZoneAt(tileX, tileY);
        if (zone == null) {
            medievalsim.util.ModLogger.info("validatePlacement: ALLOW - no protected zone at this location");
            return ValidationResult.allow();
        }
        
        medievalsim.util.ModLogger.info("validatePlacement: Found zone '%s' at location, checking permissions...", zone.name);
        
        // Check if player has permission to place in this zone
        boolean canPlace = zone.canClientPlace(client, level);
        medievalsim.util.ModLogger.info("validatePlacement: player=%s, zone=%s, canPlace=%s, zonePerm=%s",
            client.getName(), zone.name, canPlace, zone.getCanPlace());
        
        if (!canPlace) {
            medievalsim.util.ModLogger.info("validatePlacement: DENY - player lacks permission");
            return ValidationResult.deny("nopermissionplace");
        }
        
        medievalsim.util.ModLogger.info("validatePlacement: ALLOW - player has permission");
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
        medievalsim.util.ModLogger.info("===== BREAK VALIDATION START =====");
        medievalsim.util.ModLogger.info("validateBreak called: player=%s, tileX=%d, tileY=%d", 
            client != null ? client.getName() : "null", tileX, tileY);
        
        // Only perform validation on server-side
        if (!ValidationUtil.isValidServerLevel(level) || client == null) {
            medievalsim.util.ModLogger.info("validateBreak: ALLOW - not valid server level or client is null");
            return ValidationResult.allow();
        }
        
        // Get zone data for this level
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData == null) {
            medievalsim.util.ModLogger.info("validateBreak: ALLOW - no zone data for this level");
            return ValidationResult.allow();
        }
        
        // Check if this location is in a protected zone
        ProtectedZone zone = zoneData.getProtectedZoneAt(tileX, tileY);
        if (zone == null) {
            medievalsim.util.ModLogger.info("validateBreak: ALLOW - no protected zone at this location");
            return ValidationResult.allow();
        }
        
        medievalsim.util.ModLogger.info("validateBreak: Found zone '%s' at location, checking permissions...", zone.name);
        
        // Check if player has permission to break in this zone
        boolean canBreak = zone.canClientBreak(client, level);
        medievalsim.util.ModLogger.info("validateBreak: player=%s, zone=%s, canBreak=%s, zonePerm=%s",
            client.getName(), zone.name, canBreak, zone.getCanBreak());
        
        if (!canBreak) {
            medievalsim.util.ModLogger.info("validateBreak: DENY - player lacks permission");
            return ValidationResult.deny("nopermissionbreak");
        }
        
        medievalsim.util.ModLogger.info("validateBreak: ALLOW - player has permission");
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
