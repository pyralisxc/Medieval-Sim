/*
 * Patch: LevelObject.interact
 * Purpose: Block interaction with objects in protected zones/settlements based on permissions
 * 
 * Settlement protection takes precedence over zone protection.
 * 
 * Note: We patch LevelObject.interact() not GameObject.interact() because:
 * - GameObject.interact() is client-side only (exits immediately on server)
 * - LevelObject.interact() is called server-side by PacketObjectInteract
 */
package medievalsim.patches;

import medievalsim.zones.protection.ProtectionFacade;
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
        
        // Check protection using unified facade (settlement precedence > zone protection)
        ProtectionFacade.ProtectionResult protection = 
            ProtectionFacade.canInteract(client, level, tileX, tileY, gameObject);
        
        if (!protection.isAllowed()) {
            // Block interaction by skipping the method
            client.sendChatMessage(protection.getMessage());
            return true; // Skip method execution (block interaction)
        }
        
        return null; // Continue with original method (allow interaction)
    }
}
