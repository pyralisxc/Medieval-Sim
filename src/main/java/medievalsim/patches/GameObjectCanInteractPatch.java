/*
 * Patch: LevelObject.interact
 * Purpose: Block interaction with objects in protected zones based on permissions
 * 
 * Note: We patch LevelObject.interact() not GameObject.interact() because:
 * - GameObject.interact() is client-side only (exits immediately on server)
 * - LevelObject.interact() is called server-side by PacketObjectInteract
 */
package medievalsim.patches;

import medievalsim.zones.AdminZonesLevelData;
import medievalsim.zones.ProtectedZone;
import necesse.engine.localization.Localization;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.PlayerMob;
import necesse.level.maps.LevelObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(
    target = LevelObject.class,
    name = "interact",
    arguments = {PlayerMob.class}
)
public class GameObjectCanInteractPatch {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static Boolean onEnter(
        @Advice.This LevelObject levelObject,
        @Advice.Argument(0) PlayerMob player
    ) {
        // Get level and coordinates from LevelObject
        Level level = levelObject.level;
        int tileX = levelObject.tileX;
        int tileY = levelObject.tileY;
        necesse.level.gameObject.GameObject gameObject = levelObject.object;
        
        // Only check on server side and for real players
        if (!level.isServer() || player == null || !player.isPlayer) {
            return null; // Continue with original method
        }

        ServerClient client = player.getServerClient();
        if (client == null) {
            return null; // Continue with original method
        }
        
        // Check if position is in a protected zone
        AdminZonesLevelData zoneData = AdminZonesLevelData.getZoneData(level, false);
        if (zoneData == null) {
            return null; // No zones, continue with original method
        }

        ProtectedZone zone = zoneData.getProtectedZoneAt(tileX, tileY);
        if (zone == null) {
            return null; // Not in a protected zone, continue with original method
        }

        // Use granular interaction check with GameObject type detection
        if (!zone.canClientInteract(client, level, gameObject)) {
            // Block interaction by skipping the method
            String messageKey = getMessageKeyForObject(gameObject);
            String message = Localization.translate("ui", messageKey);
            client.sendChatMessage(message);
            
            return true; // Skip method execution (block interaction)
        }
        
        return null; // Continue with original method (allow interaction)
    }
    
    // Helper method to determine error message based on GameObject type
    // MUST be public for ByteBuddy advice to access it from injected code
    public static String getMessageKeyForObject(necesse.level.gameObject.GameObject gameObject) {
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
