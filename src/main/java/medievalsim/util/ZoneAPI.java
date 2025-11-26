package medievalsim.util;

import java.awt.Point;

import medievalsim.config.ModConfig;
import medievalsim.zones.AdminZonesLevelData;
import medievalsim.zones.PvPZone;
import medievalsim.zones.PvPZoneTracker;
import medievalsim.zones.ProtectedZone;
import necesse.engine.localization.Localization;
import necesse.engine.network.Packet;
import necesse.engine.network.packet.PacketPlayerMovement;
import necesse.engine.network.packet.PacketPlayerPvP;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.level.maps.Level;
import necesse.level.maps.regionSystem.RegionPositionGetter;

/**
 * Fluent builder API for zone-related packet operations.
 * Reduces 30+ line boilerplate validation to 3-5 lines.
 * 
 * <p>Example usage:
 * <pre>
 * ZoneContext ctx = ZoneAPI.forClient(client)
 *     .requirePvPZone(zoneID)
 *     .checkCombatLock(server)
 *     .build();
 * 
 * if (!ctx.isValid()) return; // All errors handled automatically
 * 
 * // Use validated objects:
 * PvPZone zone = ctx.getPvPZone();
 * Level level = ctx.getLevel();
 * </pre>
 */
public class ZoneAPI {
    private final ServerClient client;
    private Level level;
    private AdminZonesLevelData zoneData;
    private PvPZone pvpZone;
    private ProtectedZone protectedZone;
    private boolean valid = true;
    private String packetName = "Packet";
    
    private ZoneAPI(ServerClient client) {
        this.client = client;
    }
    
    /**
     * Start building a zone context for the given client.
     * 
     * @param client The ServerClient to validate
     * @return A new ZoneAPI builder instance
     */
    public static ZoneAPI forClient(ServerClient client) {
        return new ZoneAPI(client);
    }
    
    /**
     * Set the packet name for better error logging.
     * 
     * @param packetName Name of the packet (e.g., "PacketPvPZoneEntryResponse")
     * @return This builder for chaining
     */
    public ZoneAPI withPacketName(String packetName) {
        this.packetName = packetName;
        return this;
    }
    
    /**
     * Validate client has a player mob and get the level.
     * This is automatically called by requirePvPZone() and requireProtectedZone().
     * 
     * @return This builder for chaining
     */
    public ZoneAPI validateClient() {
        if (!valid) return this;
        
        if (client.playerMob == null) {
            ModLogger.error(packetName + " received for client with null playerMob: " + client.getName());
            valid = false;
            return this;
        }
        
        level = client.playerMob.getLevel();
        if (level == null) {
            ModLogger.error("Failed to get level for player " + client.getName() + " in " + packetName);
            valid = false;
            return this;
        }
        
        return this;
    }
    
    /**
     * Get zone data for the level.
     * This is automatically called by requirePvPZone() and requireProtectedZone().
     * 
     * @return This builder for chaining
     */
    public ZoneAPI getZoneData() {
        if (!valid) return this;
        
        if (level == null) {
            validateClient();
            if (!valid) return this;
        }
        
        zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData == null) {
            ModLogger.error("Failed to get zone data for level " + level.getIdentifier() + " in " + packetName);
            valid = false;
            return this;
        }
        
        return this;
    }
    
    /**
     * Require a specific PvP zone to exist. Sends error message to client if not found.
     * 
     * @param zoneID The zone ID to look up
     * @return This builder for chaining
     */
    public ZoneAPI requirePvPZone(int zoneID) {
        if (!valid) return this;
        
        if (zoneData == null) {
            getZoneData();
            if (!valid) return this;
        }
        
        pvpZone = zoneData.getPvPZone(zoneID);
        if (pvpZone == null) {
            client.sendChatMessage(Localization.translate("message", "zone.pvp.error.notexists"));
            ModLogger.warn("Player " + client.getName() + " attempted to access non-existent PvP zone ID " + zoneID + " in " + packetName);
            valid = false;
            return this;
        }
        
        return this;
    }
    
    /**
     * Require a specific Protected zone to exist.
     * 
     * @param zoneID The zone ID to look up
     * @return This builder for chaining
     */
    public ZoneAPI requireProtectedZone(int zoneID) {
        if (!valid) return this;
        
        if (zoneData == null) {
            getZoneData();
            if (!valid) return this;
        }
        
        protectedZone = zoneData.getProtectedZone(zoneID);
        if (protectedZone == null) {
            ModLogger.warn("Player " + client.getName() + " attempted to access non-existent Protected zone ID " + zoneID + " in " + packetName);
            valid = false;
            return this;
        }
        
        return this;
    }
    
    /**
     * Require a zone of either type (Protected or PvP) to exist.
     * This is useful for polymorphic handlers that operate on any zone type.
     * 
     * @param zoneID The zone ID to look up
     * @param isPvP True to look up a PvP zone, false for Protected zone
     * @return This builder for chaining
     */
    public ZoneAPI requireAnyZone(int zoneID, boolean isPvP) {
        if (isPvP) {
            return requirePvPZone(zoneID);
        } else {
            return requireProtectedZone(zoneID);
        }
    }
    
    /**
     * Validate and sanitize a zone name.
     * Trims whitespace, limits length to 30 characters, and filters invalid characters.
     * Only allows: alphanumeric, spaces, and basic punctuation (.-_'!?)
     *
     * @param name The zone name to validate
     * @return The validated zone name (trimmed, sanitized, and length-limited)
     */
    public static String validateZoneName(String name) {
        if (name == null) return "";
        String trimmed = name.trim();

        // Filter to allowed characters: alphanumeric, spaces, and basic punctuation
        // Allowed: a-z, A-Z, 0-9, space, period, hyphen, underscore, apostrophe, exclamation, question mark
        StringBuilder sanitized = new StringBuilder();
        for (char c : trimmed.toCharArray()) {
            if (Character.isLetterOrDigit(c) ||
                c == ' ' || c == '.' || c == '-' || c == '_' ||
                c == '\'' || c == '!' || c == '?') {
                sanitized.append(c);
            }
            // Silently skip invalid characters
        }

        String result = sanitized.toString().trim(); // Trim again in case filtering removed edge chars

        // Limit length to 30 characters
        if (result.length() > 30) {
            result = result.substring(0, 30);
        }

        return result;
    }
    
    /**
     * Check if player is in combat and cannot exit the zone.
     * Sends error message to client if combat locked.
     * 
     * @param server The game server instance
     * @return This builder for chaining
     */
    public ZoneAPI checkCombatLock(Server server) {
        if (!valid || pvpZone == null) return this;
        
        long serverTime = server.world.worldEntity.getTime();
        if (pvpZone.combatLockSeconds > 0 && PvPZoneTracker.isInCombat(client, level, serverTime)) {
            int remainingSeconds = PvPZoneTracker.getRemainingCombatLockSeconds(client, level, serverTime);
            client.sendChatMessage(Localization.translate("message", "zone.pvp.combatlock", "seconds", remainingSeconds));
            valid = false;
            return this;
        }
        
        return this;
    }
    
    /**
     * Check if player can re-enter a PvP zone (cooldown check).
     * Sends error message to client if on cooldown.
     * 
     * @param server The game server instance
     * @return This builder for chaining
     */
    public ZoneAPI checkReEntryCooldown(Server server) {
        if (!valid) return this;
        
        long serverTime = server.world.worldEntity.getTime();
        if (!PvPZoneTracker.canReEnter(client, serverTime)) {
            int remainingSeconds = PvPZoneTracker.getRemainingReEntryCooldown(client, serverTime);
            client.sendChatMessage(Localization.translate("message", "zone.pvp.reentrycooldown", "seconds", remainingSeconds));
            valid = false;
            return this;
        }
        
        return this;
    }
    
    /**
     * Build the final context object.
     * 
     * @return A ZoneContext with all validated data
     */
    public ZoneContext build() {
        return new ZoneContext(valid, client, level, zoneData, pvpZone, protectedZone);
    }
    
    /**
     * Immutable context object containing validated zone data.
     */
    public static class ZoneContext {
        private final boolean valid;
        private final ServerClient client;
        private final Level level;
        private final AdminZonesLevelData zoneData;
        private final PvPZone pvpZone;
        private final ProtectedZone protectedZone;
        
        private ZoneContext(boolean valid, ServerClient client, Level level, 
                          AdminZonesLevelData zoneData, PvPZone pvpZone, ProtectedZone protectedZone) {
            this.valid = valid;
            this.client = client;
            this.level = level;
            this.zoneData = zoneData;
            this.pvpZone = pvpZone;
            this.protectedZone = protectedZone;
        }
        
        /** Returns true if all validations passed */
        public boolean isValid() {
            return valid;
        }
        
        /** Get the validated ServerClient */
        public ServerClient getClient() {
            return client;
        }
        
        /** Get the validated Level */
        public Level getLevel() {
            return level;
        }
        
        /** Get the validated AdminZonesLevelData */
        public AdminZonesLevelData getZoneData() {
            return zoneData;
        }
        
        /** Get the validated PvPZone (may be null if not requested) */
        public PvPZone getPvPZone() {
            return pvpZone;
        }
        
        /** Get the validated ProtectedZone (may be null if not requested) */
        public ProtectedZone getProtectedZone() {
            return protectedZone;
        }
        
        /** 
         * Get either zone type as AdminZone for polymorphic operations.
         * Returns PvP zone if present, otherwise Protected zone.
         * 
         * @return The zone as an AdminZone, or null if neither type was validated
         */
        public medievalsim.zones.AdminZone getAdminZone() {
            if (pvpZone != null) return pvpZone;
            if (protectedZone != null) return protectedZone;
            return null;
        }
        
        /**
         * Helper: Teleport player to closest tile inside the PvP zone.
         * 
         * @param server The game server
         */
        public void teleportIntoZone(Server server) {
            if (!valid || pvpZone == null) return;
            
            Point entryTile = PvPZoneTracker.findClosestTileInZone(pvpZone, client.playerMob.x, client.playerMob.y);
            if (entryTile != null) {
                float entryX = entryTile.x * 32 + 16;
                float entryY = entryTile.y * 32 + 16;
                client.playerMob.dx = 0.0f;
                client.playerMob.dy = 0.0f;
                client.playerMob.setPos(entryX, entryY, true);
                server.network.sendToClientsWithEntity((Packet)new PacketPlayerMovement(client, true), (RegionPositionGetter)client.playerMob);
            }
        }
        
        /**
         * Helper: Teleport player to closest tile outside the PvP zone.
         * 
         * @param server The game server
         */
        public void teleportOutOfZone(Server server) {
            if (!valid || pvpZone == null) return;
            
            Point exitTile = PvPZoneTracker.findClosestTileOutsideZone(pvpZone, client.playerMob.x, client.playerMob.y);
            if (exitTile != null) {
                float exitX = exitTile.x * 32 + 16;
                float exitY = exitTile.y * 32 + 16;
                client.playerMob.dx = 0.0f;
                client.playerMob.dy = 0.0f;
                client.playerMob.setPos(exitX, exitY, true);
                server.network.sendToClientsWithEntity((Packet)new PacketPlayerMovement(client, true), (RegionPositionGetter)client.playerMob);
            }
        }
        
        /**
         * Helper: Enable PvP for the player and broadcast to clients.
         * 
         * @param server The game server
         */
        public void enablePvP(Server server) {
            if (!valid) return;
            
            if (!client.pvpEnabled && !server.world.settings.forcedPvP) {
                client.pvpEnabled = true;
                server.network.sendToAllClients((Packet)new PacketPlayerPvP(client.slot, true));
            }
        }
        
        /**
         * Helper: Disable PvP for the player and broadcast to clients.
         * 
         * @param server The game server
         */
        public void disablePvP(Server server) {
            if (!valid) return;
            
            if (client.pvpEnabled && !server.world.settings.forcedPvP) {
                client.pvpEnabled = false;
                server.network.sendToAllClients((Packet)new PacketPlayerPvP(client.slot, false));
            }
        }
        
        /**
         * Helper: Grant PvP spawn immunity buff.
         */
        public void grantSpawnImmunity() {
            if (!valid || client.playerMob == null) return;
            
            client.playerMob.addBuff(new ActiveBuff("pvpimmunity", (Mob)client.playerMob, 
                ModConfig.Zones.pvpSpawnImmunitySeconds, null), true);
        }
        
        /**
         * Helper: Enter the PvP zone (tracking).
         */
        public void enterZone() {
            if (!valid || pvpZone == null) return;
            PvPZoneTracker.enterZone(client, pvpZone);
        }
        
        /**
         * Helper: Exit the PvP zone (tracking).
         * 
         * @param serverTime Current server time
         */
        public void exitZone(long serverTime) {
            if (!valid) return;
            PvPZoneTracker.exitZone(client, serverTime);
        }
    }
}
