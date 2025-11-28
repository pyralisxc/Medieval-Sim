package medievalsim.zones.settlement;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.Settings;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.BuffRegistry;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import necesse.level.maps.levelData.settlementData.NetworkSettlementData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;

/**
 * Tracks which Settlement with protection a player is currently inside and applies
 * a cosmetic buff to surface that information. This mirrors the ProtectedZoneTracker
 * pattern but for settlements.
 * 
 * Thread-safe implementation using ConcurrentHashMap for server environment.
 */
public class SettlementProtectionTracker {

    private static final Map<Long, Integer> currentSettlements = new ConcurrentHashMap<>();

    private SettlementProtectionTracker() {
    }

    public static void updatePlayerSettlement(ServerClient client, int tileX, int tileY) {
        if (client == null || client.playerMob == null) {
            return;
        }

        Level level = client.playerMob.getLevel();
        if (level == null || !level.isServer()) {
            return;
        }

        // Check if global settlement protection is enabled
        if (!ModConfig.Settlements.protectionEnabled) {
            return;
        }

        // Get settlement at this tile
        SettlementsWorldData settlementsData = SettlementsWorldData.getSettlementsData(level.getServer());
        if (settlementsData == null) {
            return;
        }

        ServerSettlementData settlement = settlementsData.getServerDataAtTile(level.getIdentifier(), tileX, tileY);
        
        long auth = client.authentication;
        Integer previous = currentSettlements.get(auth);
        int newSettlementId = settlement != null ? settlement.uniqueID : -1;

        if (previous != null && previous == newSettlementId) {
            return; // No change - don't log every tick
        }

        // Remove buff if leaving all protected settlements
        if (settlement == null) {
            if (previous != null) {
                currentSettlements.remove(auth);
                if (client.playerMob != null && client.playerMob.buffManager != null) {
                    client.playerMob.buffManager.removeBuff(BuffRegistry.getBuffID("settlementprotection"), true);
                }
            }
            return;
        }

        // Check if this settlement has protection enabled
        LevelData protectionData = level.getLevelData("settlementprotectiondata");
        if (!(protectionData instanceof SettlementProtectionLevelData)) {
            return;
        }

        SettlementProtectionLevelData spld = (SettlementProtectionLevelData) protectionData;
        NetworkSettlementData networkData = settlement.networkData;
        
        if (!spld.getManager().isProtectionEnabled(networkData.getTileX(), networkData.getTileY())) {
            // Protection not enabled for this settlement
            if (previous != null) {
                currentSettlements.remove(auth);
                if (client.playerMob != null && client.playerMob.buffManager != null) {
                    client.playerMob.buffManager.removeBuff(BuffRegistry.getBuffID("settlementprotection"), true);
                }
            }
            return;
        }

        // Entering or switching settlements: apply/update buff with settlement info
        currentSettlements.put(auth, newSettlementId);

        try {
            int buffID = BuffRegistry.getBuffID("settlementprotection");
            ActiveBuff active = client.playerMob.buffManager.getBuff(buffID);
            
            // Check if player has elevated access
            boolean isElevated = hasElevatedAccess(client, settlement);
            
            // Get settlement name and owner name
            String settlementName = networkData.getSettlementName() != null ? 
                networkData.getSettlementName().translate() : "Unknown Settlement";
            String ownerName = networkData.getOwnerName();
            
            if (active == null) {
                // Create new buff with GND data set BEFORE adding
                active = new ActiveBuff("settlementprotection", client.playerMob, 0, null);
                
                // Store settlement info in GND data for client-side tooltip
                active.getGndData().setString("settlementName", settlementName);
                active.getGndData().setString("ownerName", ownerName);
                active.getGndData().setBoolean("isElevated", isElevated);
                
                // Store individual permissions (only relevant if not elevated)
                if (!isElevated) {
                    SettlementProtectionData data = spld.getManager().getProtectionData(
                        networkData.getTileX(), networkData.getTileY());
                    active.getGndData().setBoolean("canPlace", data.getCanPlace());
                    active.getGndData().setBoolean("canBreak", data.getCanBreak());
                    active.getGndData().setBoolean("canDoors", data.getCanInteractDoors());
                    active.getGndData().setBoolean("canChests", data.getCanInteractContainers());
                    active.getGndData().setBoolean("canStations", data.getCanInteractStations());
                    active.getGndData().setBoolean("canSwitches", data.getCanInteractSwitches());
                    active.getGndData().setBoolean("canFurniture", data.getCanInteractFurniture());
                }
                
                // Now add buff with all GND data already set
                client.playerMob.addBuff(active, true);
            } else {
                // Buff already exists - remove and re-add to force client refresh
                client.playerMob.buffManager.removeBuff(buffID, true);
                
                // Create fresh buff with updated GND data
                active = new ActiveBuff("settlementprotection", client.playerMob, 0, null);
                active.getGndData().setString("settlementName", settlementName);
                active.getGndData().setString("ownerName", ownerName);
                active.getGndData().setBoolean("isElevated", isElevated);
                
                if (!isElevated) {
                    SettlementProtectionData data = spld.getManager().getProtectionData(
                        networkData.getTileX(), networkData.getTileY());
                    active.getGndData().setBoolean("canPlace", data.getCanPlace());
                    active.getGndData().setBoolean("canBreak", data.getCanBreak());
                    active.getGndData().setBoolean("canDoors", data.getCanInteractDoors());
                    active.getGndData().setBoolean("canChests", data.getCanInteractContainers());
                    active.getGndData().setBoolean("canStations", data.getCanInteractStations());
                    active.getGndData().setBoolean("canSwitches", data.getCanInteractSwitches());
                    active.getGndData().setBoolean("canFurniture", data.getCanInteractFurniture());
                }

                client.playerMob.addBuff(active, true);
            }
        } catch (Exception e) {
            ModLogger.error("Failed to apply settlement protection buff", e);
        }
    }

    public static void cleanupPlayer(ServerClient client) {
        if (client == null) {
            return;
        }
        currentSettlements.remove(client.authentication);
        if (client.playerMob != null && client.playerMob.buffManager != null) {
            client.playerMob.buffManager.removeBuff(BuffRegistry.getBuffID("settlementprotection"), true);
        }
    }

    /**
     * Check if client has elevated access to a settlement.
     * Elevated access = world owner, settlement owner, or team member (if allowOwnerTeam is true).
     */
    private static boolean hasElevatedAccess(ServerClient client, ServerSettlementData settlement) {
        if (client == null || settlement == null) {
            return false;
        }

        NetworkSettlementData networkData = settlement.networkData;
        Level level = client.playerMob.getLevel();

        // World owner always has access
        if (Settings.serverOwnerAuth != -1L && client.authentication == Settings.serverOwnerAuth) {
            return true;
        }

        // Settlement owner has access
        long settlementOwner = networkData.getOwnerAuth();
        if (settlementOwner == client.authentication) {
            return true;
        }

        // Check team access
        LevelData protectionData = level.getLevelData("settlementprotectiondata");
        if (protectionData instanceof SettlementProtectionLevelData) {
            SettlementProtectionLevelData spld = (SettlementProtectionLevelData) protectionData;
            SettlementProtectionData data = spld.getManager().getProtectionData(
                networkData.getTileX(), networkData.getTileY());

            if (data.getAllowOwnerTeam()) {
                int settlementTeamID = networkData.getTeamID();
                if (settlementTeamID != -1 && client.playerMob != null) {
                    int clientTeamID = client.getTeamID();
                    if (clientTeamID == settlementTeamID) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
