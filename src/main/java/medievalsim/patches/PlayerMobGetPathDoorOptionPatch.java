/*
 * Patch: PlayerMob.getPathDoorOption
 * Purpose: Prevent auto-door from working in protected zones without DOORS permission
 * 
 * Context: When a player has auto-door enabled, Necesse's pathfinding automatically opens
 * doors during movement. This happens CLIENT-SIDE with optimistic updates, meaning the door
 * opens locally before the server validates. Even though our GameObjectCanInteractPatch blocks
 * the server-side interaction, the client has already opened the door and can walk through
 * briefly until the sync happens.
 * 
 * Solution: Override getPathDoorOption() to return CANNOT_OPEN_CAN_CLOSE_DOORS_OPTIONS when
 * the player is in a protected zone without DOORS permission. This prevents pathfinding from
 * even attempting to open doors, eliminating the optimistic client-side bypass entirely.
 */
package medievalsim.patches;

import medievalsim.zones.AdminZonesLevelData;
import medievalsim.zones.ProtectedZone;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PathDoorOption;
import necesse.entity.mobs.PlayerMob;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(
    target = PlayerMob.class,
    name = "getPathDoorOption",
    arguments = {}
)
public class PlayerMobGetPathDoorOptionPatch {

    @Advice.OnMethodExit
    public static void onExit(
        @Advice.This PlayerMob player,
        @Advice.Return(readOnly = false) PathDoorOption result
    ) {
        // Only patch if auto-door is enabled (result is CAN_OPEN)
        if (result == null) {
            return;
        }
        
        Level level = player.getLevel();
        if (level == null) {
            return;
        }
        
        // Only check on client side (where pathfinding decisions happen)
        if (!level.isClient()) {
            return;
        }
        
        // Get player's tile coordinates
        int tileX = player.getTileX();
        int tileY = player.getTileY();
        
        // Check if position is in a protected zone
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData == null) {
            return; // No zones exist
        }

        ProtectedZone zone = zoneData.getProtectedZoneAt(tileX, tileY);
        if (zone == null) {
            return; // Not in a protected zone
        }

        // Client-side check: Does this zone allow door interactions?
        // We check:
        // 1. If player is world owner (always has access)
        // 2. If player is zone owner or creator (always has access)
        // 3. If player is on owner's team (when allowOwnerTeam is enabled)
        // 4. If the zone has door permission enabled for non-elevated users
        
        boolean hasPermission = false;
        necesse.engine.network.NetworkClient networkClient = player.getNetworkClient();
        
        if (networkClient != null) {
            long playerAuth = networkClient.authentication;
            
            // Check world owner
            if (necesse.engine.Settings.serverOwnerAuth != -1L && playerAuth == necesse.engine.Settings.serverOwnerAuth) {
                hasPermission = true;
            }
            // Check zone owner or creator
            else if (zone.getOwnerAuth() == playerAuth || zone.creatorAuth == playerAuth) {
                hasPermission = true;
            }
            // Check if player is on owner's team (when enabled)
            else if (zone.getAllowOwnerTeam() && zone.getOwnerTeamID() != -1) {
                int playerTeamID = networkClient.getTeamID();
                if (playerTeamID != -1 && playerTeamID == zone.getOwnerTeamID()) {
                    hasPermission = true;
                }
            }
            // Check if zone allows door interactions for everyone
            else if (zone.getCanInteractDoors()) {
                hasPermission = true;
            }
        }
        
        if (!hasPermission) {
            // Disable auto-door in this protected zone by returning CANNOT_OPEN option
            // This prevents pathfinding from attempting to open doors at all
            result = level.regionManager.CANNOT_OPEN_CAN_CLOSE_DOORS_OPTIONS;
        }
    }
}
