package medievalsim.zones.service;

import medievalsim.zones.domain.PvPZone;

import medievalsim.zones.domain.ProtectedZone;

import medievalsim.zones.domain.GuildZone;

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

public class ZoneHelper {
    private static final Random colorRandom = new Random();

    private ZoneHelper() {
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

    /**
     * Create a new protected zone at runtime.
     * 
     * <p>Protected zones restrict interactions (chests, doors, crafting stations) and block
     * placement/breaking to authorized players only. Access is controlled by the zone owner
     * and optionally by team membership.</p>
     * 
     * <p><b>Requirements:</b></p>
     * <ul>
     *   <li>Level must be server-side</li>
     *   <li>Zone name must be 1-50 characters (enforced by Constants.Zones.MAX_ZONE_NAME_LENGTH)</li>
     *   <li>Creator must be authenticated (if provided)</li>
     * </ul>
     * 
     * <p><b>Important Notes:</b></p>
     * <ul>
     *   <li>Creator's team is NOT automatically granted access (must be added explicitly)</li>
     *   <li>Zone starts with no area coverage (use {@link #expandZone} to define boundaries)</li>
     *   <li>Random color is auto-generated for visual identification</li>
     *   <li>Zone count limits are checked and logged (soft/critical thresholds)</li>
     * </ul>
     * 
     * <p><b>Example:</b></p>
     * <pre>{@code
     * ProtectedZone zone = ZoneHelper.createProtectedZone(level, "My Base", client);
     * if (zone != null) {
     *     // Expand zone to cover an area
     *     Rectangle bounds = new Rectangle(x, y, 20, 20);
     *     ZoneHelper.expandZone(level, zone, bounds);
     *     
     *     // Optionally add team access
     *     zone.addAllowedTeam(teamID);
     * }
     * }</pre>
     * 
     * @param level Server-side level where zone will be created (must not be null)
     * @param name Zone display name (1-50 chars, will be trimmed; auto-generated if null/empty)
     * @param creator ServerClient creating the zone (null allowed for system-created zones)
     * @return New ProtectedZone instance, or null if validation failed
     * @see #deleteProtectedZone(Level, int, ServerClient)
     * @see #expandZone(Level, AdminZone, Rectangle)
     * @see ProtectedZone
     */
    public static ProtectedZone createProtectedZone(Level level, String name, ServerClient creator) {
        AdminZonesLevelData zoneData = getValidatedZoneData(level, true);
        if (zoneData == null) {
            return null;
        }

        // Check zone count limits before creating
        checkZoneCountLimits(zoneData, true);

        long creatorAuth = creator != null ? creator.authentication : -1L;
        int colorHue = ZoneHelper.generateRandomColorHue();
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

    /**
     * Create a new PvP zone at runtime.
     * 
     * <p>PvP zones enable player-vs-player combat with configurable damage multipliers,
     * spawn immunity periods, and optional spawn points for fair engagement.</p>
     * 
     * <p><b>Requirements:</b></p>
     * <ul>
     *   <li>Level must be server-side</li>
     *   <li>Zone name must be 1-50 characters</li>
     *   <li>Creator must be authenticated (if provided)</li>
     * </ul>
     * 
     * <p><b>Default Configuration:</b></p>
     * <ul>
     *   <li>Damage multiplier: {@link ModConfig.Zones#defaultDamageMultiplier}</li>
     *   <li>Spawn immunity: {@link ModConfig.Zones#pvpSpawnImmunitySeconds}</li>
     *   <li>Re-entry cooldown: {@link ModConfig.Zones#pvpReentryCooldownMs}</li>
     *   <li>Teleport-to-spawn enabled by default</li>
     * </ul>
     * 
     * <p><b>Example:</b></p>
     * <pre>{@code
     * PvPZone zone = ZoneHelper.createPvPZone(level, "Arena", client);
     * if (zone != null) {
     *     // Define arena boundaries
     *     Rectangle bounds = new Rectangle(x, y, 50, 50);
     *     ZoneHelper.expandZone(level, zone, bounds);
     *     
     *     // Configure damage multiplier (0.5 = 50% damage)
     *     zone.setDamageMultiplier(0.5f);
     *     
     *     // Set spawn point for defeated players
     *     zone.setSpawnTile(new Point(spawnX, spawnY));
     * }
     * }</pre>
     * 
     * @param level Server-side level where zone will be created (must not be null)
     * @param name Zone display name (1-50 chars, auto-generated if null/empty)
     * @param creator ServerClient creating the zone (null allowed for system-created zones)
     * @return New PvPZone instance, or null if validation failed
     * @see #deletePvPZone(Level, int, ServerClient)
     * @see PvPZone
     */
    public static PvPZone createPvPZone(Level level, String name, ServerClient creator) {
        AdminZonesLevelData zoneData = getValidatedZoneData(level, true);
        if (zoneData == null) {
            return null;
        }

        // Check zone count limits before creating
        checkZoneCountLimits(zoneData, false);

        long creatorAuth = creator != null ? creator.authentication : -1L;
        int colorHue = ZoneHelper.generateRandomColorHue();
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

    /**
     * Create a new Guild zone at runtime.
     * 
     * <p>Guild zones are territory claimed by a guild. Access and permissions are 
     * controlled by the guild's permission system rather than individual zone settings.</p>
     * 
     * <p><b>Requirements:</b></p>
     * <ul>
     *   <li>Level must be server-side</li>
     *   <li>Zone name must be 1-50 characters</li>
     *   <li>Creator must be authenticated (if provided)</li>
     * </ul>
     * 
     * <p><b>Usage:</b></p>
     * <pre>{@code
     * GuildZone zone = ZoneHelper.createGuildZone(level, "Guild Territory", client);
     * if (zone != null) {
     *     zone.setGuildID(guildID);
     *     zone.setGuildName(guildName);
     *     Rectangle bounds = new Rectangle(x, y, 100, 100);
     *     ZoneHelper.expandZone(level, zone, bounds);
     * }
     * }</pre>
     * 
     * @param level Server-side level where zone will be created (must not be null)
     * @param name Zone display name (1-50 chars, auto-generated if null/empty)
     * @param creator ServerClient creating the zone (null allowed for system-created zones)
     * @return New GuildZone instance, or null if validation failed
     * @see #deleteGuildZone(Level, int, ServerClient)
     * @see GuildZone
     */
    public static GuildZone createGuildZone(Level level, String name, ServerClient creator) {
        AdminZonesLevelData zoneData = getValidatedZoneData(level, true);
        if (zoneData == null) {
            return null;
        }

        long creatorAuth = creator != null ? creator.authentication : -1L;
        int colorHue = ZoneHelper.generateRandomColorHue();
        String useName = (name == null || name.trim().isEmpty()) ? zoneData.getUniqueZoneName() : name;
        GuildZone zone = zoneData.addGuildZone(useName, creatorAuth, colorHue);
        
        // Log zone creation
        if (creator != null) {
            ModLogger.info("Guild zone '%s' created by %s", useName, creator.getName());
        } else {
            ModLogger.info("Guild zone '%s' created", useName);
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

    public static void deleteGuildZone(Level level, int uniqueID, ServerClient deleter) {
        AdminZonesLevelData zoneData = getValidatedZoneData(level, false);
        if (zoneData != null) {
            GuildZone zone = zoneData.getGuildZone(uniqueID);
            if (zone != null) {
                String zoneName = zone.name;
                zoneData.removeGuildZone(uniqueID);
                if (deleter != null) {
                    ModLogger.info("Guild zone '%s' deleted by %s", zoneName, deleter.getName());
                } else {
                    ModLogger.info("Guild zone '%s' deleted", zoneName);
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

    public static GuildZone getGuildZone(Level level, int uniqueID) {
        AdminZonesLevelData zoneData = getValidatedZoneData(level, false);
        return zoneData != null ? zoneData.getGuildZone(uniqueID) : null;
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

    public static Map<Integer, GuildZone> getAllGuildZones(Level level) {
        if (level == null) {
            return Map.of();
        }
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        return zoneData != null ? zoneData.getGuildZones() : Map.of();
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

    /**
     * Remove all guild zones associated with a specific guild ID.
     * This is called when a guild is disbanded to clean up orphaned zones.
     * 
     * <p>Since guild zones are stored per-level in AdminZonesLevelData,
     * this method requires access to the Server to iterate through all active levels.</p>
     * 
     * @param server The server instance to access levels
     * @param guildID The guild ID whose zones should be removed
     * @return Number of zones removed
     */
    public static int removeAllGuildZonesForGuild(necesse.engine.network.server.Server server, int guildID) {
        if (server == null || server.world == null) {
            return 0;
        }

        int removedCount = 0;
        
        // Iterate through all levels that have zone data
        for (necesse.level.maps.Level level : server.world.levelManager.getLoadedLevels()) {
            AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
            if (zoneData == null) {
                continue;
            }
            
            // Collect zone IDs to remove (avoid concurrent modification)
            java.util.List<Integer> zonesToRemove = new java.util.ArrayList<>();
            Map<Integer, GuildZone> guildZonesMap = zoneData.getGuildZonesInternal();
            synchronized (guildZonesMap) {
                for (Map.Entry<Integer, GuildZone> entry : guildZonesMap.entrySet()) {
                    if (entry.getValue().getGuildID() == guildID) {
                        zonesToRemove.add(entry.getKey());
                    }
                }
            }
            
            // Remove zones
            for (int zoneID : zonesToRemove) {
                zoneData.removeGuildZone(zoneID);
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            ModLogger.info("Removed %d guild zones for disbanded guild %d", removedCount, guildID);
        }
        
        return removedCount;
    }
}

