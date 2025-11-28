package medievalsim.zones.service;

import medievalsim.zones.domain.PvPZone;

import medievalsim.zones.domain.ProtectedZone;

import medievalsim.zones.domain.AdminZone;

import medievalsim.zones.domain.AdminZonesLevelData;

import java.awt.Rectangle;
import java.util.Map;
import java.util.Random;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import medievalsim.util.ValidationUtil;
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

    /**
     * Check zone count limits and log warnings/errors as appropriate.
     * @param zoneData The zone data to check
     * @param isProtectedZone True for protected zones, false for PvP zones
     */
    private static void checkZoneCountLimits(AdminZonesLevelData zoneData, boolean isProtectedZone) {
        if (zoneData == null) {
            return;
        }

        int currentCount;
        int softLimit;
        int criticalLimit;
        String zoneType;

        if (isProtectedZone) {
            currentCount = zoneData.getProtectedZones().size();
            softLimit = ModConfig.Zones.protectedZoneSoftLimit;
            criticalLimit = ModConfig.Zones.protectedZoneCriticalLimit;
            zoneType = "protected";
        } else {
            currentCount = zoneData.getPvPZones().size();
            softLimit = ModConfig.Zones.pvpZoneSoftLimit;
            criticalLimit = ModConfig.Zones.pvpZoneCriticalLimit;
            zoneType = "PvP";
        }

        if (currentCount >= criticalLimit) {
            ModLogger.error("CRITICAL: %s zone count (%d) has reached critical limit (%d)! Performance degradation likely. Consider cleanup.",
                zoneType, currentCount, criticalLimit);
        } else if (currentCount >= softLimit) {
            ModLogger.warn("WARNING: %s zone count (%d) has exceeded soft limit (%d). Consider cleanup to maintain performance.",
                zoneType, currentCount, softLimit);
        } else if (currentCount >= softLimit * 0.8) {
            ModLogger.info("INFO: %s zone count (%d) approaching soft limit (%d).",
                zoneType, currentCount, softLimit);
        }
    }

    public static ProtectedZone createProtectedZone(Level level, String name, ServerClient creator) {
        AdminZonesLevelData zoneData = getValidatedZoneData(level, true);
        if (zoneData == null) {
            return null;
        }

        // Check zone count limits before creating
        checkZoneCountLimits(zoneData, true);

        long creatorAuth = creator != null ? creator.authentication : -1L;
        int colorHue = ZoneManager.generateRandomColorHue();
    String useName = (name == null || name.trim().isEmpty()) ? zoneData.getUniqueZoneName() : name;
    ProtectedZone zone = zoneData.addProtectedZone(useName, creatorAuth, colorHue);
        
        // Set owner name if creator is present
        if (creator != null) {
            zone.setOwnerName(creator.getName());
            
            // DO NOT auto-add creator's team to allowedTeamIDs
            // Team access should only be granted explicitly via zone configuration
            // Otherwise, everyone on the default team (ID 0) would have access
        }
        
        // Log zone creation
        if (creator != null) {
            ModLogger.info("Protected zone '%s' created by %s", useName, creator.getName());
        } else {
            ModLogger.info("Protected zone '%s' created", useName);
        }
        
        return zone;
    }

    public static PvPZone createPvPZone(Level level, String name, ServerClient creator) {
        AdminZonesLevelData zoneData = getValidatedZoneData(level, true);
        if (zoneData == null) {
            return null;
        }

        // Check zone count limits before creating
        checkZoneCountLimits(zoneData, false);

        long creatorAuth = creator != null ? creator.authentication : -1L;
        int colorHue = ZoneManager.generateRandomColorHue();
        String useName = (name == null || name.trim().isEmpty()) ? zoneData.getUniqueZoneName() : name;
        PvPZone zone = zoneData.addPvPZone(useName, creatorAuth, colorHue);
        
        // Log zone creation
        if (creator != null) {
            ModLogger.info("PvP zone '%s' created by %s", useName, creator.getName());
        } else {
            ModLogger.info("PvP zone '%s' created", useName);
        }
        
        return zone;
    }

    public static void deleteProtectedZone(Level level, int uniqueID, ServerClient deleter) {
        AdminZonesLevelData zoneData = getValidatedZoneData(level, false);
        if (zoneData != null) {
            ProtectedZone zone = zoneData.getProtectedZone(uniqueID);
            if (zone != null) {
                String zoneName = zone.name;
                zoneData.removeProtectedZone(uniqueID);
                if (deleter != null) {
                    ModLogger.info("Protected zone '%s' deleted by %s", zoneName, deleter.getName());
                } else {
                    ModLogger.info("Protected zone '%s' deleted", zoneName);
                }
            }
        }
    }

    public static void deletePvPZone(Level level, int uniqueID, ServerClient deleter) {
        AdminZonesLevelData zoneData = getValidatedZoneData(level, false);
        if (zoneData != null) {
            PvPZone zone = zoneData.getPvPZone(uniqueID);
            if (zone != null) {
                String zoneName = zone.name;
                zoneData.removePvPZone(uniqueID);
                if (deleter != null) {
                    ModLogger.info("PvP zone '%s' deleted by %s", zoneName, deleter.getName());
                } else {
                    ModLogger.info("PvP zone '%s' deleted", zoneName);
                }
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

