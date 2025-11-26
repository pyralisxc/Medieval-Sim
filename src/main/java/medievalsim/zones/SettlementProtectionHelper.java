/*
 * Settlement Protection Helper for Medieval Sim Mod
 *
 * This is the integration point for settlement protection validation.
 * It provides utility methods for checking if a tile is within a protected settlement
 * and whether a player has permission to perform actions within that settlement.
 *
 * Used by ZoneProtectionValidator to integrate settlement protection into the
 * existing zone protection system.
 */
package medievalsim.zones;

import medievalsim.config.ModConfig;
import necesse.engine.Settings;
import necesse.engine.network.server.ServerClient;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;
import necesse.level.maps.levelData.settlementData.NetworkSettlementData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;

/**
 * Helper class for checking settlement protection permissions.
 * Integrates settlement protection into the zone protection validation system.
 */
public class SettlementProtectionHelper {

    /**
     * Check if a tile is within a protected settlement.
     * Returns the settlement data if protected, null otherwise.
     */
    public static ServerSettlementData getProtectedSettlementAt(Level level, int tileX, int tileY) {
        if (level == null || !level.isServer()) {
            return null;
        }

        // Check if global settlement protection is enabled
        if (!ModConfig.Settlements.protectionEnabled) {
            return null;
        }

        // Get settlements world data
        SettlementsWorldData settlementsData = SettlementsWorldData.getSettlementsData(level.getServer());
        if (settlementsData == null) {
            return null;
        }

        // Get settlement at this tile
        ServerSettlementData settlement = settlementsData.getServerDataAtTile(level.getIdentifier(), tileX, tileY);
        if (settlement == null) {
            return null;
        }

        // Check if this settlement has protection enabled
        LevelData protectionData = level.getLevelData("settlementprotectiondata");
        if (protectionData instanceof SettlementProtectionLevelData) {
            SettlementProtectionLevelData spld = (SettlementProtectionLevelData) protectionData;
            NetworkSettlementData networkData = settlement.networkData;
            if (spld.getManager().isProtectionEnabled(networkData.getTileX(), networkData.getTileY())) {
                return settlement;
            }
        }

        return null;
    }
    
    /**
     * Check if a client can break blocks in a settlement.
     */
    public static boolean canClientBreak(ServerClient client, Level level, int tileX, int tileY) {
        ServerSettlementData settlement = getProtectedSettlementAt(level, tileX, tileY);
        if (settlement == null) {
            return true; // No protected settlement at this location
        }

        // Check if client has elevated access (world owner, settlement owner, team member)
        if (hasElevatedAccess(client, level, settlement)) {
            return true;
        }

        // Check permission
        LevelData protectionData = level.getLevelData("settlementprotectiondata");
        if (protectionData instanceof SettlementProtectionLevelData) {
            SettlementProtectionLevelData spld = (SettlementProtectionLevelData) protectionData;
            NetworkSettlementData networkData = settlement.networkData;
            SettlementProtectionData data = spld.getManager().getProtectionData(
                networkData.getTileX(), networkData.getTileY());
            return data.getCanBreak();
        }

        return false;
    }

    /**
     * Check if a client can place blocks in a settlement.
     */
    public static boolean canClientPlace(ServerClient client, Level level, int tileX, int tileY) {
        ServerSettlementData settlement = getProtectedSettlementAt(level, tileX, tileY);
        if (settlement == null) {
            return true; // No protected settlement at this location
        }

        // Check if client has elevated access
        if (hasElevatedAccess(client, level, settlement)) {
            return true;
        }

        // Check permission
        LevelData protectionData = level.getLevelData("settlementprotectiondata");
        if (protectionData instanceof SettlementProtectionLevelData) {
            SettlementProtectionLevelData spld = (SettlementProtectionLevelData) protectionData;
            NetworkSettlementData networkData = settlement.networkData;
            SettlementProtectionData data = spld.getManager().getProtectionData(
                networkData.getTileX(), networkData.getTileY());
            return data.getCanPlace();
        }

        return false;
    }

    /**
     * Check if a client has elevated access to a settlement.
     * Elevated access = world owner, settlement owner, or team member (if allowOwnerTeam is true).
     */
    private static boolean hasElevatedAccess(ServerClient client, Level level, ServerSettlementData settlement) {
        if (client == null || level == null || settlement == null) {
            return false;
        }

        NetworkSettlementData networkData = settlement.networkData;

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

