package medievalsim.patches;
import java.awt.geom.Line2D;
import medievalsim.zones.protection.ProtectionFacade;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.placeableItem.tileItem.TileItem;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

public class TileItemCanPlacePatch {

    @ModMethodPatch(target=TileItem.class, name="canPlace", arguments={Level.class, int.class, int.class, PlayerMob.class, Line2D.class, InventoryItem.class, GNDItemMap.class})
    public static class CanPlace {
        @Advice.OnMethodExit
        static void onExit(@Advice.This TileItem tileItem, @Advice.Argument(value=0) Level level, @Advice.Argument(value=1) int x, @Advice.Argument(value=2) int y, @Advice.Argument(value=3) PlayerMob player, @Advice.Argument(value=6) GNDItemMap mapContent, @Advice.Return(readOnly=false) String result) {
            // Build mode checking done inline
            if (result != null && result.equals("outofrange") && mapContent != null && mapContent.getBoolean("medievalsim_buildmode")) {
                result = null;
            }
            if (result != null) {
                return;
            }

            // Server-side protection check (authoritative)
            // Checks both settlement protection (precedence #1) and zone protection (precedence #2)
            // Note: Client-side optimistic placement is allowed, but server will reject unauthorized attempts
            // This is standard Necesse behavior - the server is authoritative and will sync the correct state
            if (level.isServer() && player != null && player.isServerClient()) {
                int tileX = GameMath.getTileCoordinate(x);
                int tileY = GameMath.getTileCoordinate(y);
                ProtectionFacade.ProtectionResult protection = 
                    ProtectionFacade.canPlace(player.getServerClient(), level, tileX, tileY, false);
                if (!protection.isAllowed()) {
                    result = protection.getMessage();
                }
            }
        }
    }
}

