package medievalsim.zones.service;

import medievalsim.zones.domain.PvPZone;

import medievalsim.zones.domain.AdminZonesLevelData;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import medievalsim.config.ModConfig;
import medievalsim.packets.PacketPvPZoneSpawnDialog;
import medievalsim.util.ModLogger;

import necesse.engine.network.Packet;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.BuffRegistry;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.level.maps.Level;

/**
 * Tracks PvP zone state for players including combat status, cooldowns, and zone transitions.
 * 
 * Thread-safe implementation using ConcurrentHashMap for server environment.
 */
public class PvPZoneTracker {
    private static final Map<Long, PlayerPvPState> playerStates = new ConcurrentHashMap<>();

    public static PlayerPvPState getPlayerState(ServerClient client) {
        return playerStates.computeIfAbsent(client.authentication, k -> new PlayerPvPState());
    }

    public static void cleanupPlayerState(ServerClient client) {
        if (client != null) {
            playerStates.remove(client.authentication);
            // Remove damage reduction buff on cleanup
            if (client.playerMob != null && client.playerMob.buffManager != null) {
                int buffID = BuffRegistry.getBuffID("pvpdamagereduction");
                client.playerMob.buffManager.removeBuff(buffID, true);
            }
        }
    }

    public static void cleanupPlayerState(long authentication) {
        playerStates.remove(authentication);
    }

    public static boolean isInPvPZone(ServerClient client) {
        PlayerPvPState state = playerStates.get(client.authentication);
        return state != null && state.getCurrentZoneID() != -1;
    }

    public static PvPZone getCurrentZone(ServerClient client, Level level) {
        PlayerPvPState state = playerStates.get(client.authentication);
        if (state == null || level == null) {
            return null;
        }
        int zoneID = state.getCurrentZoneID();
        if (zoneID == -1) {
            return null;
        }
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        return zoneData != null ? zoneData.getPvPZone(zoneID) : null;
    }

    public static void enterZone(ServerClient client, PvPZone zone) {
        PlayerPvPState state = PvPZoneTracker.getPlayerState(client);
        state.enterZone(zone.uniqueID);
    }

    public static void exitZone(ServerClient client, long serverTime) {
        PlayerPvPState state = PvPZoneTracker.getPlayerState(client);
        state.exitZone(serverTime);

        // Remove damage reduction buff when exiting zone
        if (client != null && client.playerMob != null && client.playerMob.buffManager != null) {
            int buffID = BuffRegistry.getBuffID("pvpdamagereduction");
            client.playerMob.buffManager.removeBuff(buffID, true);
        }
    }

    public static boolean canReEnter(ServerClient client, long serverTime) {
        PlayerPvPState state = playerStates.get(client.authentication);
        if (state == null) {
            return true;
        }
        long lastExit = state.getLastExitTime();
        if (lastExit == 0L) {
            return true;
        }
        return serverTime - lastExit >= ModConfig.Zones.pvpReentryCooldownMs;
    }

    public static int getRemainingReEntryCooldown(ServerClient client, long serverTime) {
        PlayerPvPState state = playerStates.get(client.authentication);
        if (state == null) {
            return 0;
        }
        long lastExit = state.getLastExitTime();
        if (lastExit == 0L) {
            return 0;
        }
        long elapsed = serverTime - lastExit;
        long remaining = ModConfig.Zones.pvpReentryCooldownMs - elapsed;
        return remaining > 0L ? (int)Math.ceil((double)remaining / 1000.0) : 0;
    }

    public static void recordCombat(ServerClient client, long serverTime) {
        PlayerPvPState state = PvPZoneTracker.getPlayerState(client);
        state.setLastCombatTime(serverTime);
    }

    public static boolean isInCombat(ServerClient client, Level level, long serverTime) {
        PlayerPvPState state = playerStates.get(client.authentication);
        if (state == null || level == null) {
            return false;
        }
        int zoneID = state.getCurrentZoneID();
        long lastCombat = state.getLastCombatTime();
        if (zoneID == -1 || lastCombat == 0L) {
            return false;
        }
        PvPZone zone = PvPZoneTracker.getCurrentZone(client, level);
        if (zone == null) {
            return false;
        }
        long combatLockMs = (long)zone.combatLockSeconds * 1000L;
        return serverTime - lastCombat < combatLockMs;
    }

    public static int getRemainingCombatLockSeconds(ServerClient client, Level level, long serverTime) {
        PlayerPvPState state = playerStates.get(client.authentication);
        if (state == null || level == null) {
            return 0;
        }
        int zoneID = state.getCurrentZoneID();
        if (zoneID == -1) {
            return 0;
        }
        PvPZone zone = PvPZoneTracker.getCurrentZone(client, level);
        if (zone == null) {
            return 0;
        }
        long combatLockMs = (long)zone.combatLockSeconds * 1000L;
        long lastCombat = state.getLastCombatTime();
        long elapsed = serverTime - lastCombat;
        long remaining = combatLockMs - elapsed;
        return remaining > 0L ? (int)Math.ceil((double)remaining / 1000.0) : 0;
    }

    public static void handleSpawnInZone(ServerClient client, PvPZone zone, Server server, long serverTime) {
        PlayerPvPState state = PvPZoneTracker.getPlayerState(client);
        if (state.hasShownSpawnDialog()) {
            return;
        }
        state.setHasShownSpawnDialog(true);
        client.sendPacket((Packet)new PacketPvPZoneSpawnDialog(zone));
    }

    public static Point findClosestTileInZone(PvPZone zone, float playerX, float playerY) {
        int playerTileX = (int)(playerX / 32.0f);
        int playerTileY = (int)(playerY / 32.0f);
        if (zone.containsTile(playerTileX, playerTileY) && !zone.zoning.isEdgeTile(playerTileX, playerTileY)) {
            return new Point(playerTileX, playerTileY);
        }
        Rectangle bounds = zone.zoning.getTileBounds();
        if (bounds == null) {
            return null;
        }
        int maxSearchRadius = Math.max(bounds.width, bounds.height);
        for (int searchRadius = 1; searchRadius <= maxSearchRadius; ++searchRadius) {
            for (int dx = -searchRadius; dx <= searchRadius; ++dx) {
                for (int dy = -searchRadius; dy <= searchRadius; ++dy) {
                    int checkY;
                    int checkX;
                    if (Math.abs(dx) != searchRadius && Math.abs(dy) != searchRadius || !zone.containsTile(checkX = playerTileX + dx, checkY = playerTileY + dy) || zone.zoning.isEdgeTile(checkX, checkY)) continue;
                    return new Point(checkX, checkY);
                }
            }
        }
        int centerX = bounds.x + bounds.width / 2;
        int centerY = bounds.y + bounds.height / 2;
        return new Point(centerX, centerY);
    }

    public static Point findClosestTileOutsideZone(PvPZone zone, float playerX, float playerY) {
        int playerTileX = (int)(playerX / 32.0f);
        int playerTileY = (int)(playerY / 32.0f);
        if (!zone.containsTile(playerTileX, playerTileY)) {
            return new Point(playerTileX, playerTileY);
        }
        Rectangle bounds = zone.zoning.getTileBounds();
        if (bounds == null) {
            return null;
        }
        int[] dx = new int[]{-1, 1, 0, 0};
        int[] dy = new int[]{0, 0, -1, 1};
        Point closest = null;
        double closestDist = Double.MAX_VALUE;
        for (int i = 0; i < 4; ++i) {
            int checkX = playerTileX;
            int checkY = playerTileY;
            while (zone.containsTile(checkX, checkY)) {
                checkX += dx[i];
                checkY += dy[i];
            }
            double dist = Math.sqrt(Math.pow(checkX - playerTileX, 2.0) + Math.pow(checkY - playerTileY, 2.0));
            if (!(dist < closestDist)) continue;
            closestDist = dist;
            closest = new Point(checkX, checkY);
        }
        return closest != null ? closest : new Point(playerTileX, playerTileY);
    }

    /**
     * Updates the PvP damage reduction buff for a player based on their current zone.
     * Applies the buff when in a PvP zone, removes it when outside.
     * 
     * @param client The player client
     * @param zone The current PvP zone (null if not in any zone)
     */
    public static void updatePlayerZoneBuff(ServerClient client, PvPZone zone) {
        if (client == null || client.playerMob == null) {
            return;
        }

        try {
            int buffID = BuffRegistry.getBuffID("pvpdamagereduction");
            ActiveBuff existingBuff = client.playerMob.buffManager.getBuff(buffID);

            if (zone == null) {
                // Player is not in any PvP zone - remove buff if present
                if (existingBuff != null) {
                    ModLogger.debug("Removing PvP damage reduction buff from %s", client.getName());
                    client.playerMob.buffManager.removeBuff(buffID, true);
                }
                return;
            }

            // Player is in a PvP zone - apply/update buff with zone info
            if (existingBuff == null) {
                // Create new buff with zone data
                ModLogger.debug("Applying PvP damage reduction buff to %s in zone %s", client.getName(), zone.name);
                ActiveBuff buff = new ActiveBuff("pvpdamagereduction", client.playerMob, 0, null);
                buff.getGndData().setString("zoneName", zone.name);
                buff.getGndData().setFloat("damageMultiplier", zone.damageMultiplier);
                buff.getGndData().setInt("zoneID", zone.uniqueID);
                client.playerMob.addBuff(buff, true);
            } else {
                // Buff exists - only update if zone changed
                int currentZoneID = existingBuff.getGndData().getInt("zoneID", -1);
                
                if (currentZoneID != zone.uniqueID) {
                    // Zone changed - remove and re-add with new data
                    ModLogger.debug("Updating PvP damage reduction buff for %s (zone changed to %s)", client.getName(), zone.name);
                    client.playerMob.buffManager.removeBuff(buffID, true);
                    ActiveBuff buff = new ActiveBuff("pvpdamagereduction", client.playerMob, 0, null);
                    buff.getGndData().setString("zoneName", zone.name);
                    buff.getGndData().setFloat("damageMultiplier", zone.damageMultiplier);
                    buff.getGndData().setInt("zoneID", zone.uniqueID);
                    client.playerMob.addBuff(buff, true);
                }
                // If same zone ID, buff persists unchanged (no flickering)
            }
        } catch (Exception e) {
            ModLogger.error("Failed to apply PvP zone damage reduction buff", e);
        }
    }

    /**
     * Thread-safe state container for player PvP zone tracking.
     * All field access is synchronized to prevent race conditions.
     */
    public static class PlayerPvPState {
        private int currentZoneID = -1;
        private long lastCombatTime = 0L;
        private long lastExitTime = 0L;
        private boolean hasShownSpawnDialog = false;

        // Synchronized getters
        public synchronized int getCurrentZoneID() {
            return currentZoneID;
        }

        public synchronized long getLastCombatTime() {
            return lastCombatTime;
        }

        public synchronized long getLastExitTime() {
            return lastExitTime;
        }

        public synchronized boolean hasShownSpawnDialog() {
            return hasShownSpawnDialog;
        }

        // Synchronized setters
        public synchronized void setCurrentZoneID(int zoneID) {
            this.currentZoneID = zoneID;
        }

        public synchronized void setLastCombatTime(long time) {
            this.lastCombatTime = time;
        }

        public synchronized void setLastExitTime(long time) {
            this.lastExitTime = time;
        }

        public synchronized void setHasShownSpawnDialog(boolean shown) {
            this.hasShownSpawnDialog = shown;
        }

        // Compound operations that need to be atomic
        public synchronized void enterZone(int zoneID) {
            this.currentZoneID = zoneID;
        }

        public synchronized void exitZone(long serverTime) {
            this.currentZoneID = -1;
            this.lastCombatTime = 0L;
            this.lastExitTime = serverTime;
            this.hasShownSpawnDialog = false;
        }
    }
}

