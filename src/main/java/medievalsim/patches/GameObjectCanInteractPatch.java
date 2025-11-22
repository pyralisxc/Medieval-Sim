/*
 * Patch: GameObject.canInteract
 * Purpose: Block interaction with objects in protected zones based on permissions
 */
package medievalsim.patches;

import medievalsim.zones.AdminZonesLevelData;
import medievalsim.zones.ProtectedZone;
import necesse.engine.localization.Localization;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(
    target = GameObject.class,
    name = "canInteract",
    arguments = {Level.class, int.class, int.class, PlayerMob.class}
)
public class GameObjectCanInteractPatch {

    @Advice.OnMethodExit
    public static void onExit(
        @Advice.This GameObject gameObject,
        @Advice.Argument(0) Level level,
        @Advice.Argument(1) int x,
        @Advice.Argument(2) int y,
        @Advice.Argument(3) PlayerMob player,
        @Advice.Return(readOnly = false) boolean result
    ) {
        // Only check on server side and for real players
        if (!level.isServer() || player == null || !player.isPlayer) {
            return; // Keep original result
        }

        ServerClient client = player.getServerClient();
        if (client == null) {
            return; // Keep original result
        }

        // Get tile coordinates
        int tileX = GameMath.getTileCoordinate(x);
        int tileY = GameMath.getTileCoordinate(y);

        // Check if position is in a protected zone
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData == null) {
            return; // No zones, keep original result
        }

        ProtectedZone zone = zoneData.getProtectedZoneAt(tileX, tileY);
        if (zone == null) {
            return; // Not in a protected zone, keep original result
        }

        // Use granular interaction check with GameObject type detection
        if (!zone.canClientInteract(client, level, gameObject)) {
            // Override result to block interaction
            result = false;
            
            // Send specific feedback message based on object type
            String messageKey = getMessageKeyForObject(gameObject);
            String message = Localization.translate("message", messageKey);
            client.sendChatMessage(message);
        }
        // If player has permission, keep whatever the original method returned
    }
    
    // Helper method to determine error message based on GameObject type
    private static String getMessageKeyForObject(GameObject gameObject) {
        if (gameObject.isDoor) {
            return "nopermissiondoors";
        }
        if (gameObject instanceof necesse.level.gameObject.container.CraftingStationObject || 
            gameObject instanceof necesse.level.gameObject.container.FueledCraftingStationObject) {
            return "nopermissionstations";
        }
        if (gameObject instanceof necesse.level.gameObject.container.InventoryObject) {
            return "nopermissioncontainers";
        }
        if (gameObject instanceof necesse.level.gameObject.SignObject) {
            return "nopermissionsigns";
        }
        if (gameObject.isSwitch || gameObject.isPressurePlate) {
            return "nopermissionswitches";
        }
        if (gameObject instanceof necesse.level.gameObject.furniture.FurnitureObject) {
            return "nopermissionfurniture";
        }
        // Generic fallback
        return "nopermissioninteract";
    }
}
