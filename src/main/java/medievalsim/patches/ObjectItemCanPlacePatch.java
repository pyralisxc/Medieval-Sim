package medievalsim.patches;
import java.awt.geom.Line2D;
import medievalsim.util.ZoneProtectionValidator;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.placeableItem.objectItem.ObjectItem;
import necesse.level.gameObject.ObjectPlaceOption;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

public class ObjectItemCanPlacePatch {

    @ModMethodPatch(target=ObjectItem.class, name="canPlace", arguments={Level.class, ObjectPlaceOption.class, PlayerMob.class, Line2D.class, InventoryItem.class, GNDItemMap.class})
    public static class CanPlace {
        @Advice.OnMethodExit
        static void onExit(@Advice.This ObjectItem objectItem, @Advice.Argument(value=0) Level level, @Advice.Argument(value=1) ObjectPlaceOption po, @Advice.Argument(value=2) PlayerMob player, @Advice.Argument(value=5) GNDItemMap mapContent, @Advice.Return(readOnly=false) String result) {
            if (po == null) {
                return;
            }

            // Build mode range override
            if (result != null && result.equals("outofrange") && mapContent != null && mapContent.getBoolean("medievalsim_buildmode")) {
                result = null;
            }
            if (result != null) {
                return;
            }

            // Server-side protection check (authoritative) using centralized validator
            // Note: Client-side optimistic placement is allowed, but server will reject unauthorized attempts
            if (level.isServer() && player != null && player.isServerClient()) {
                ZoneProtectionValidator.ValidationResult validation = 
                    ZoneProtectionValidator.validatePlacement(level, po.tileX, po.tileY, player.getServerClient());
                if (!validation.isAllowed()) {
                    result = validation.getReason();
                }
            }
        }
    }
}

