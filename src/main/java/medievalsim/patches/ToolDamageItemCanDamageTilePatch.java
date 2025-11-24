package medievalsim.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.ToolDamageItem;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import medievalsim.util.ZoneProtectionValidator;

/**
 * Patches ToolDamageItem.canDamageTile() to check ProtectedZones.
 * This is the CORE breaking protection mechanism in Necesse.
 * 
 * ARCHITECTURE:
 * - Necesse uses canDamageTile() to check if a tool can break a tile/object
 * - Called before applying damage to tiles/objects when player uses tools
 * - Has PlayerMob context, allowing us to check permissions
 * - This was the missing link - we were patching placement but not breaking
 * 
 * DESIGN:
 * - Return false to prevent breaking if zone permissions deny it
 * - Shows appropriate message to player via ZoneProtectionValidator
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
        
        // Check zone permissions for breaking
        // Note: tileX and tileY are already tile coordinates, not position coordinates
        ZoneProtectionValidator.ValidationResult validation = 
            ZoneProtectionValidator.validateBreak(level, tileX, tileY, client);
        
        if (!validation.isAllowed()) {
            // Send message to player explaining why breaking is blocked
            String message = necesse.engine.localization.Localization.translate("ui", validation.getReason());
            client.sendChatMessage(message);
            // Block the damage
            returnValue = false;
        }
    }
}
