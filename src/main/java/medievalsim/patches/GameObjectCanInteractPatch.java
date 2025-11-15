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

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static Boolean onEnter(
        @Advice.This GameObject gameObject,
        @Advice.Argument(0) Level level,
        @Advice.Argument(1) int x,
        @Advice.Argument(2) int y,
        @Advice.Argument(3) PlayerMob player
    ) {
        // Only check on server side and for real players
        if (!level.isServer() || player == null || !player.isPlayer) {
            return null; // Continue normal execution
        }

        ServerClient client = player.getServerClient();
        if (client == null) {
            return null; // Continue normal execution
        }

        // Get tile coordinates
        int tileX = GameMath.getTileCoordinate(x);
        int tileY = GameMath.getTileCoordinate(y);

        // Check if position is in a protected zone
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData == null) {
            return null; // No zones, continue normal execution
        }

        ProtectedZone zone = zoneData.getProtectedZoneAt(tileX, tileY);
        if (zone == null) {
            return null; // Not in a protected zone, continue normal execution
        }

        // Enhancement #5: Use new granular interaction check with GameObject type detection
        if (!zone.canClientInteract(client, level, gameObject)) {
            // Send specific feedback message based on object type
            String messageKey = getMessageKeyForObject(gameObject);
            String message = Localization.translate("ui", messageKey);
            client.sendChatMessage(message);
            return false; // Block interaction
        }

        // Player has permission, continue normal execution
        return null;
    }
    
    // Enhancement #5: Helper method to determine error message based on GameObject type
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
        // Generic fallback (shouldn't reach here if ProtectedZone logic is correct)
        return "nopermissioninteract";
    }
}
