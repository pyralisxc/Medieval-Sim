package medievalsim.util;

import medievalsim.zones.domain.AdminZonesLevelData;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;

/**
 * Centralized validation for zone-related packets.
 * Eliminates duplicate permission checks, level validation, and zone data validation
 * across all zone management packets.
 */
public class ZonePacketValidator {
    
    /**
     * Result of packet validation containing validated data
     */
    public static class ValidationResult {
        public final boolean isValid;
        public final Level level;
        public final AdminZonesLevelData zoneData;
        
        private ValidationResult(boolean isValid, Level level, AdminZonesLevelData zoneData) {
            this.isValid = isValid;
            this.level = level;
            this.zoneData = zoneData;
        }
        
        public static ValidationResult valid(Level level, AdminZonesLevelData zoneData) {
            return new ValidationResult(true, level, zoneData);
        }
        
        public static ValidationResult invalid() {
            return new ValidationResult(false, null, null);
        }
    }
    
    /**
     * Validates admin permission, level existence, and zone data availability.
     * This is the standard validation required by all zone management packets.
     * 
     * @param server The server instance
     * @param client The client attempting the operation
     * @param packetName The name of the packet (for logging purposes)
     * @return ValidationResult containing validated level and zone data, or invalid result
     */
    public static ValidationResult validateZonePacket(
            Server server, 
            ServerClient client, 
            String packetName) {
        
        // Permission check - require ADMIN level
        if (client.getPermissionLevel().getLevel() < PermissionLevel.ADMIN.getLevel()) {
            ModLogger.warn("Player " + client.getName() + " attempted " + packetName + " without admin permission");
            return ValidationResult.invalid();
        }
        
        // Level validation
        Level level = server.world.getLevel(client);
        if (level == null) {
            ModLogger.error("Failed to get level for client " + client.getName() + " in " + packetName);
            return ValidationResult.invalid();
        }
        
        // Zone data validation
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level);
        if (zoneData == null) {
            ModLogger.error("Failed to get zone data for level " + level.getIdentifier() + " in " + packetName);
            return ValidationResult.invalid();
        }
        
        return ValidationResult.valid(level, zoneData);
    }
    
    /**
     * Validates zone name according to mod constraints.
     * Trims whitespace and enforces maximum length.
     * 
     * @param name The zone name to validate
     * @return Validated zone name (trimmed and truncated if necessary)
     */
    public static String validateZoneName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Unnamed Zone";
        }
        
        String trimmed = name.trim();
        if (trimmed.length() > Constants.Zones.MAX_ZONE_NAME_LENGTH) {
            return trimmed.substring(0, Constants.Zones.MAX_ZONE_NAME_LENGTH);
        }
        
        return trimmed;
    }
}

