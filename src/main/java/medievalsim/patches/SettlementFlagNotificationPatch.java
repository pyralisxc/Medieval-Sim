package medievalsim.patches;

import medievalsim.util.ModLogger;
import medievalsim.util.SettlementSpacingHelper;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.network.packet.PacketChangeObject;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.placeableItem.objectItem.SettlementFlagObjectItem;
import necesse.level.gameObject.ObjectPlaceOption;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

/**
 * Patches vanilla settlement flag placement to add detailed spacing notifications.
 */
public class SettlementFlagNotificationPatch {

    @ModMethodPatch(
        target = SettlementFlagObjectItem.class,
        name = "onAttemptPlace",
        arguments = {Level.class, int.class, int.class, PlayerMob.class, 
                    InventoryItem.class, GNDItemMap.class, String.class}
    )
    public static class OnAttemptPlacePatch {
        
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        static boolean onEnter(
            @Advice.This SettlementFlagObjectItem flagItem,
            @Advice.Argument(0) Level level,
            @Advice.Argument(1) int x,
            @Advice.Argument(2) int y,
            @Advice.Argument(3) PlayerMob player,
            @Advice.Argument(5) GNDItemMap mapContent,
            @Advice.Argument(6) String error
        ) {
            // Only intercept closesettlement errors
            if ("closesettlement".equals(error) && player != null && level.isServer()) {
                // Get actual TILE coordinates from place option (x,y parameters are NOT tile coordinates!)
                ObjectPlaceOption po = flagItem.getPlaceOptionFromMap(mapContent);
                if (po != null) {
                    ModLogger.debug("Intercepting vanilla settlement flag placement for enhanced notification");
                    SettlementSpacingHelper.sendDetailedSpacingInfo(level, po.tileX, po.tileY, player);
                    
                    // Send packet update to refresh client state
                    player.getServerClient().sendPacket(new PacketChangeObject(level, 0, po.tileX, po.tileY));
                }
                
                // Skip original method by returning true (non-default value)
                return true;
            }
            
            // Continue with vanilla behavior
            return false;
        }
        
        @Advice.OnMethodExit
        static void onExit(
            @Advice.Return(readOnly = false) InventoryItem returnValue,
            @Advice.Argument(4) InventoryItem originalItem,
            @Advice.Enter boolean skipped
        ) {
            // If we skipped the original method, return the original item
            if (skipped) {
                returnValue = originalItem;
            }
        }
    }
}
