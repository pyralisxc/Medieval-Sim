package medievalsim.objects;

import medievalsim.config.ModConfig;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.network.packet.PacketChangeObject;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.GameMath;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.placeableItem.objectItem.ObjectItem;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.ObjectPlaceOption;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.OneWorldNPCVillageData;
import necesse.level.maps.levelData.settlementData.CachedSettlementData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementBoundsManager;

import java.awt.geom.Line2D;
import java.awt.Rectangle;
import java.util.Comparator;

/**
 * Item for placing plot flags.
 * Validates placement similar to settlement flags, respecting spacing and max settlements.
 */
public class PlotFlagObjectItem extends ObjectItem {

    public PlotFlagObjectItem(GameObject object) {
        super(object);
    }

    @Override
    public String canPlace(Level level, ObjectPlaceOption po, PlayerMob player, Line2D playerPositionLine, 
                          InventoryItem item, GNDItemMap mapContent) {
        // Do settlement validation FIRST (like vanilla SettlementFlagObjectItem)
        if (level.isServer() && player != null) {
            Server server = level.getServer();
            SettlementsWorldData settlementsData = SettlementsWorldData.getSettlementsData(server);
            ServerClient client = player.getServerClient();
            
            // Check max settlements per player
            int max = server.world.settings.maxSettlementsPerPlayer;
            if (max >= 0) {
                long current = settlementsData.streamSettlements()
                    .filter(e -> e.getOwnerAuth() == client.authentication && 
                                !e.isTileWithinBounds(po.tileX, po.tileY))
                    .count();
                if (current >= max) {
                    return "maxsettlements";
                }
            }
            
            // Determine flag tier (plot flags use tier 0)
            ServerSettlementData serverData = settlementsData.getServerDataAtTile(
                level.getIdentifier(), po.tileX, po.tileY);
            int flagTier = serverData != null ? serverData.getFlagTier() : 0;
            
            // Check settlement spacing - this will be intercepted by our patch for custom spacing
            if (!settlementsData.canPlaceSettlementFlagAt(level.getIdentifier(), po.tileX, po.tileY, flagTier)) {
                return "closesettlement";
            }
            
            // Check village proximity
            OneWorldNPCVillageData villageData = OneWorldNPCVillageData.getVillageData(level, false);
            if (villageData != null && !villageData.canPlaceSettlementFlagAt(po.tileX, po.tileY, flagTier)) {
                return "closevillage";
            }
        }
        
        // THEN check base object placement rules (like vanilla)
        return super.canPlace(level, po, player, playerPositionLine, item, mapContent);
    }

    @Override
    public InventoryItem onAttemptPlace(Level level, int x, int y, PlayerMob player, 
                                       InventoryItem item, GNDItemMap mapContent, String error) {
        
        // Log the actual error for debugging
        if (error != null) {
            if (medievalsim.config.ModConfig.Logging.verboseDebug) {
                medievalsim.util.ModLogger.debug("PlotFlag onAttemptPlace: error='%s' at (%d,%d)", error, x, y);
            }
        }
        
        boolean sendUpdate = false;
        
        // Handle specific errors that require reverting optimistic placement
        switch (error) {
            case "maxsettlements": {
                Server server = level.getServer();
                player.getServerClient().sendChatMessage(new LocalMessage("misc", "maxsettlementsreached", 
                    "count", server.world.settings.maxSettlementsPerPlayer));
                sendUpdate = true;
                break;
            }
            case "closesettlement": {
                // Get actual tile coordinates from place option
                ObjectPlaceOption po = this.getPlaceOptionFromMap(mapContent);
                if (po != null) {
                    // Send detailed spacing information using shared helper with TILE coordinates
                    medievalsim.util.SettlementSpacingHelper.sendDetailedSpacingInfo(level, po.tileX, po.tileY, player);
                }
                sendUpdate = true;
                break;
            }
            case "closevillage": {
                player.getServerClient().sendChatMessage(new LocalMessage("misc", "tooclosevillage"));
                sendUpdate = true;
                break;
            }
        }
        
        // Revert optimistic placement if we handled an error (exactly like vanilla)
        if (sendUpdate) {
            ObjectPlaceOption po = this.getPlaceOptionFromMap(mapContent);
            if (po != null) {
                player.getServerClient().sendPacket(new PacketChangeObject(level, 0, po.tileX, po.tileY));
            }
            return item;
        }
        
        // For any other error or success, use default behavior
        return super.onAttemptPlace(level, x, y, player, item, mapContent, error);
    }

    // Spacing info handled by medievalsim.util.SettlementSpacingHelper
    
    @Override
    public boolean onPlaceObject(GameObject object, Level level, int layerID, int tileX, int tileY, 
                                int rotation, ServerClient client, InventoryItem item) {
        // Standard placement - no special ownership handling since plot flags are purchased later
        return super.onPlaceObject(object, level, layerID, tileX, tileY, rotation, client, item);
    }
}
