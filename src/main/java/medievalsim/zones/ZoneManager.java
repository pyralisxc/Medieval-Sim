package medievalsim.zones;

import java.awt.Rectangle;
import java.util.Map;
import java.util.Random;

import medievalsim.util.ValidationUtil;
import medievalsim.zones.events.ZoneEventBus;
import medievalsim.zones.events.ZoneCreatedEvent;
import medievalsim.zones.events.ZoneDeletedEvent;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;

public class ZoneManager {
    private static final Random colorRandom = new Random();

    private ZoneManager() {
    }
    
    /**
     * Helper method to safely get zone data, with validation
     * @param level The level to get zone data for
     * @param createIfMissing Whether to create zone data if it doesn't exist
     * @return AdminZonesLevelData or null if level is invalid or not server
     */
    private static AdminZonesLevelData getValidatedZoneData(Level level, boolean createIfMissing) {
        if (!ValidationUtil.isValidServerLevel(level)) {
            return null;
        }
        return AdminZonesLevelData.getZoneData(level, createIfMissing);
    }

    public static ProtectedZone createProtectedZone(Level level, String name, ServerClient creator) {
        AdminZonesLevelData zoneData = getValidatedZoneData(level, true);
        if (zoneData == null) {
            return null;
        }
        long creatorAuth = creator != null ? creator.authentication : -1L;
        int colorHue = ZoneManager.generateRandomColorHue();
    String useName = (name == null || name.trim().isEmpty()) ? zoneData.getUniqueZoneName() : name;
    ProtectedZone zone = zoneData.addProtectedZone(useName, creatorAuth, colorHue);
        
        // Set owner name if creator is present
        if (creator != null) {
            zone.setOwnerName(creator.getName());
            
            // Add creator's team if they have one
            int teamID = creator.getTeamID();
            if (teamID != -1) {
                zone.addAllowedTeam(teamID);
            }
        }
        
        // Fire zone created event
        ZoneEventBus.fire(new ZoneCreatedEvent(zone, creator));
        
        return zone;
    }

    public static PvPZone createPvPZone(Level level, String name, ServerClient creator) {
        AdminZonesLevelData zoneData = getValidatedZoneData(level, true);
        if (zoneData == null) {
            return null;
        }
        long creatorAuth = creator != null ? creator.authentication : -1L;
        int colorHue = ZoneManager.generateRandomColorHue();
        String useName = (name == null || name.trim().isEmpty()) ? zoneData.getUniqueZoneName() : name;
        PvPZone zone = zoneData.addPvPZone(useName, creatorAuth, colorHue);
        
        // Fire zone created event
        ZoneEventBus.fire(new ZoneCreatedEvent(zone, creator));
        
        return zone;
    }

    public static void deleteProtectedZone(Level level, int uniqueID, ServerClient deleter) {
        AdminZonesLevelData zoneData = getValidatedZoneData(level, false);
        if (zoneData != null) {
            ProtectedZone zone = zoneData.getProtectedZone(uniqueID);
            if (zone != null) {
                zoneData.removeProtectedZone(uniqueID);
                ZoneEventBus.fire(new ZoneDeletedEvent(zone, deleter));
            }
        }
    }

    public static void deletePvPZone(Level level, int uniqueID, ServerClient deleter) {
        AdminZonesLevelData zoneData = getValidatedZoneData(level, false);
        if (zoneData != null) {
            PvPZone zone = zoneData.getPvPZone(uniqueID);
            if (zone != null) {
                zoneData.removePvPZone(uniqueID);
                ZoneEventBus.fire(new ZoneDeletedEvent(zone, deleter));
            }
        }
    }

    public static ProtectedZone getProtectedZone(Level level, int uniqueID) {
        AdminZonesLevelData zoneData = getValidatedZoneData(level, false);
        return zoneData != null ? zoneData.getProtectedZone(uniqueID) : null;
    }

    public static PvPZone getPvPZone(Level level, int uniqueID) {
        AdminZonesLevelData zoneData = getValidatedZoneData(level, false);
        return zoneData != null ? zoneData.getPvPZone(uniqueID) : null;
    }

    public static Map<Integer, ProtectedZone> getAllProtectedZones(Level level) {
        if (level == null) {
            return Map.of();
        }
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        return zoneData != null ? zoneData.getProtectedZones() : Map.of();
    }

    public static Map<Integer, PvPZone> getAllPvPZones(Level level) {
        if (level == null) {
            return Map.of();
        }
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        return zoneData != null ? zoneData.getPvPZones() : Map.of();
    }

    public static boolean expandZone(Level level, AdminZone zone, Rectangle rectangle) {
        if (zone == null || rectangle == null) {
            return false;
        }
        return zone.expand(rectangle);
    }

    public static boolean shrinkZone(Level level, AdminZone zone, Rectangle rectangle) {
        if (zone == null || rectangle == null) {
            return false;
        }
        return zone.shrink(rectangle);
    }

    public static boolean canClientModifyTile(Level level, ServerClient client, int tileX, int tileY) {
        if (level == null || !level.isServer()) {
            return true;
        }
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData == null) {
            return true;
        }
        return zoneData.canClientModifyTile(client, tileX, tileY);
    }

    public static PvPZone getPvPZoneAt(Level level, int tileX, int tileY) {
        if (level == null) {
            return null;
        }
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData == null) {
            return null;
        }
        // Convert tile coordinates to world coordinates
        float x = tileX * 32f + 16f; // Center of tile
        float y = tileY * 32f + 16f;
        return zoneData.getPvPZoneAt(x, y);
    }

    public static boolean areBothInPvPZone(Level level, float x1, float y1, float x2, float y2) {
        if (level == null) {
            return false;
        }
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData == null) {
            return false;
        }
        return zoneData.areBothInPvPZone(x1, y1, x2, y2);
    }

    private static int generateRandomColorHue() {
        return colorRandom.nextInt(360);
    }

    public static void renameZone(AdminZone zone, String newName) {
        if (zone != null && newName != null) {
            zone.name = newName;
        }
    }

    public static void changeZoneColor(AdminZone zone, int colorHue) {
        if (zone != null) {
            zone.colorHue = Math.max(0, Math.min(360, colorHue));
        }
    }

    public static void addAllowedTeam(ProtectedZone zone, int teamID) {
        if (zone != null) {
            zone.addAllowedTeam(teamID);
        }
    }

    public static void removeAllowedTeam(ProtectedZone zone, int teamID) {
        if (zone != null) {
            zone.removeAllowedTeam(teamID);
        }
    }
}

