package medievalsim.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.ToolDamageItem;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import medievalsim.zones.protection.ProtectionFacade;

/**
 * Patches ToolDamageItem.canDamageTile() to check settlement and zone protection.
 * This is the CORE breaking protection mechanism in Necesse.
 * 
 * ARCHITECTURE:
 * - Necesse uses canDamageTile() to check if a tool can break a tile/object
 * - Called before applying damage to tiles/objects when player uses tools
 * - Has PlayerMob context, allowing us to check permissions
 * - This was the missing link - we were patching placement but not breaking
 * 
 * DESIGN:
 * - Return false to prevent breaking if settlement/zone permissions deny it
 * - Settlement protection takes precedence over zone protection
 * - Shows appropriate message to player via ProtectionFacade
 * - Only enforces on server-side (client follows server's response)
 */
@ModMethodPatch(target = ToolDamageItem.class, name = "canDamageTile", arguments = {Level.class, int.class, int.class, int.class, ItemAttackerMob.class, InventoryItem.class})
public class ToolDamageItemCanDamageTilePatch {
    
    /**
     * Intercepts damage checks to add ProtectedZone validation.
     * 
     * PARAMETERS:
     * @param level The level where damage is being attempted
     * @param layerID The object layer ID (-1 for tiles, 0+ for objects)
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @param attackerMob The mob attempting to damage (usually PlayerMob)
     * @param item The tool item being used
     * @param returnValue The original canDamageTile result
     */
    @Advice.OnMethodExit
    public static void onMethodExit(
        @Advice.Argument(0) Level level,
        @Advice.Argument(1) int layerID,
        @Advice.Argument(2) int tileX,
        @Advice.Argument(3) int tileY,
        @Advice.Argument(4) ItemAttackerMob attackerMob,
        @Advice.Argument(5) InventoryItem item,
        @Advice.Return(readOnly = false) boolean returnValue
    ) {
        // Skip if vanilla already denied
        if (!returnValue) {
            return;
        }
        
        // Only enforce on server
        if (!level.isServer()) {
            return;
        }
        
        // Only check for player attackers
        if (!(attackerMob instanceof PlayerMob)) {
            return;
        }
        
        PlayerMob player = (PlayerMob) attackerMob;
        ServerClient client = player.getServerClient();
        
        if (client == null) {
            return;
        }
        
        // Check protection using unified facade (settlement + zone, with settlement precedence)
        // Note: tileX and tileY are already tile coordinates, not position coordinates
        // layerID: -1 for tiles, 0+ for objects
        boolean isObject = layerID >= 0;
        ProtectionFacade.ProtectionResult protection = 
            ProtectionFacade.canBreak(client, level, tileX, tileY, isObject);
        
        if (!protection.isAllowed()) {
            // Send message to player explaining why breaking is blocked
            client.sendChatMessage(protection.getMessage());
            // Block the damage
            returnValue = false;
        }
    }
}
