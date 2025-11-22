package medievalsim.util;

import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;

/**
 * Utility methods for zone operations and validation.
 */
public final class ZoneUtil {
    
    private ZoneUtil() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Check if a client is on the same team as the zone owner.
     * Uses Necesse's native Team API for reliable team membership checking.
     * 
     * @param ownerAuth The authentication ID of the zone owner
     * @param client The client to check
     * @param level The level containing the teams
     * @return true if the client is on the owner's team, false otherwise
     */
    public static boolean isOnOwnerTeam(long ownerAuth, ServerClient client, Level level) {
        if (ownerAuth == -1L || client == null || level == null || level.getServer() == null) {
            return false;
        }
        
        // Use Necesse's Team API to get the owner's team
        int ownerTeamID = level.getServer().world.getTeams().getPlayerTeamID(ownerAuth);
        if (ownerTeamID == -1) {
            return false; // Owner has no team
        }
        
        // Get the PlayerTeam object for richer API access
        necesse.engine.team.PlayerTeam ownerTeam = level.getServer().world.getTeams().getTeam(ownerTeamID);
        if (ownerTeam == null) {
            return false; // Team doesn't exist (shouldn't happen)
        }
        
        // Use native API to check membership - more reliable than manual ID comparison
        return ownerTeam.isMember(client.authentication);
    }
}
