package medievalsim.zones.service;

import medievalsim.zones.domain.ProtectedZone;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import medievalsim.util.ModLogger;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.BuffRegistry;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.level.maps.Level;

/**
 * Tracks which Protected Zone a player is currently inside and applies
 * a cosmetic buff to surface that information. This mirrors the
 * PvPZoneTracker pattern but intentionally only handles presence/zone
 * name, leaving gameplay restrictions to the ProtectedZone logic
 * and patches.
 * 
 * Thread-safe implementation using ConcurrentHashMap for server environment.
 */
public class ProtectedZoneTracker {

    private static final Map<Long, Integer> currentZones = new ConcurrentHashMap<>();

    private ProtectedZoneTracker() {
    }

    public static void updatePlayerZone(ServerClient client, ProtectedZone zone) {
        if (client == null || client.playerMob == null) {
            return;
        }

        long auth = client.authentication;
        Integer previous = currentZones.get(auth);
        int newZoneId = zone != null ? zone.uniqueID : -1;

        if (previous != null && previous == newZoneId) {
            return; // No change - don't log every tick
        }

        // Remove buff if leaving all protected zones
        if (zone == null) {
            if (previous != null) {
                // Only log actual transitions FROM a zone TO outside
                currentZones.remove(auth);
                if (client.playerMob != null && client.playerMob.buffManager != null) {
                    client.playerMob.buffManager.removeBuff(BuffRegistry.getBuffID("protectedzone"), true);
                }
            }
            return;
        }

        // Entering or switching zones: apply/update buff with zone name
        currentZones.put(auth, newZoneId);

        try {
            int buffID = BuffRegistry.getBuffID("protectedzone");
            ActiveBuff active = client.playerMob.buffManager.getBuff(buffID);
            
            // Check if player has elevated access
            Level level = client.playerMob.getLevel();
            boolean isElevated = zone.isWorldOwner(client, level) || 
                               zone.isOwner(client) || 
                               zone.isCreator(client) ||
                               (zone.getAllowOwnerTeam() && isOnOwnerTeam(zone, client, level));
            
            if (active == null) {
                // Create new buff with GND data set BEFORE adding
                active = new ActiveBuff("protectedzone", client.playerMob, 0, null);
                
                // Store zone info in GND data for client-side tooltip
                active.getGndData().setString("zoneName", zone.name);
                active.getGndData().setBoolean("isElevated", isElevated);
                
                // Store individual permissions (only relevant if not elevated)
                if (!isElevated) {
                    active.getGndData().setBoolean("canPlace", zone.getCanPlace());
                    active.getGndData().setBoolean("canBreak", zone.getCanBreak());
                    active.getGndData().setBoolean("canDoors", zone.getCanInteractDoors());
                    active.getGndData().setBoolean("canChests", zone.getCanInteractContainers());
                    active.getGndData().setBoolean("canStations", zone.getCanInteractStations());
                    active.getGndData().setBoolean("canSwitches", zone.getCanInteractSwitches());
                    active.getGndData().setBoolean("canFurniture", zone.getCanInteractFurniture());
                }
                
                // Now add buff with all GND data already set
                client.playerMob.addBuff(active, true);
            } else {
                // Buff already exists - remove and re-add to force client refresh
                // sendUpdatePacket() doesn't reliably update tooltips, so we remove and re-add
                client.playerMob.buffManager.removeBuff(buffID, true);
                
                // Create fresh buff with updated GND data
                active = new ActiveBuff("protectedzone", client.playerMob, 0, null);
                active.getGndData().setString("zoneName", zone.name);
                active.getGndData().setBoolean("isElevated", isElevated);
                
                if (!isElevated) {
                    active.getGndData().setBoolean("canPlace", zone.getCanPlace());
                    active.getGndData().setBoolean("canBreak", zone.getCanBreak());
                    active.getGndData().setBoolean("canDoors", zone.getCanInteractDoors());
                    active.getGndData().setBoolean("canChests", zone.getCanInteractContainers());
                    active.getGndData().setBoolean("canStations", zone.getCanInteractStations());
                    active.getGndData().setBoolean("canSwitches", zone.getCanInteractSwitches());
                    active.getGndData().setBoolean("canFurniture", zone.getCanInteractFurniture());
                }
                
                client.playerMob.addBuff(active, true);
            }
        } catch (Exception e) {
            ModLogger.error("Failed to apply protected zone buff", e);
        }
    }

    public static void cleanupPlayer(ServerClient client) {
        if (client == null) {
            return;
        }
        currentZones.remove(client.authentication);
        if (client.playerMob != null && client.playerMob.buffManager != null) {
            client.playerMob.buffManager.removeBuff(BuffRegistry.getBuffID("protectedzone"), true);
        }
    }
    
    // Helper to check if client is on owner's team
    private static boolean isOnOwnerTeam(ProtectedZone zone, ServerClient client, Level level) {
        return medievalsim.util.ZoneAPI.isOnOwnerTeam(zone.getOwnerAuth(), client, level);
    }
}
