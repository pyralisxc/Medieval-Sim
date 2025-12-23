package medievalsim.guilds.items;

import java.awt.geom.Line2D;

import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildSymbolDesign;
import medievalsim.guilds.items.GuildBannerItem;
import medievalsim.guilds.objects.GuildFlagObject;
import medievalsim.guilds.objects.GuildFlagObjectEntity;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.network.packet.PacketChangeObject;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.placeableItem.objectItem.ObjectItem;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.ObjectPlaceOption;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;

/**
 * Guild Flag Object Item - handles placing guild flags on settlement flags.
 * When placed:
 * - Replaces settlement flag with guild flag
 * - Returns settlement flag item to player inventory
 * - Maintains all settlement functionality
 * - Adds guild claim and features
 */
public class GuildFlagObjectItem extends ObjectItem {

    // Static methods for setting guild data on items
    public static int getGuildID(InventoryItem item) {
        if (item == null || item.getGndData() == null) return -1;
        return item.getGndData().getInt("guildID", -1);
    }

    public static void setGuildID(InventoryItem item, int guildID) {
        if (item == null) return;
        item.getGndData().setInt("guildID", guildID);
    }

    public static GuildSymbolDesign getSymbolDesign(InventoryItem item) {
        if (item == null || item.getGndData() == null) return null;
        GNDItemMap data = item.getGndData();
        if (!data.hasKey("bgShape")) return null;
        return new GuildSymbolDesign(
            data.getInt("bgShape", 0),
            data.getInt("primaryColor", 0x0000FF),
            data.getInt("secondaryColor", 0x000000),
            data.getInt("emblemID", 0),
            data.getInt("emblemColor", 0xFFFFFF),
            data.getInt("borderStyle", 1)
        );
    }

    public static void setSymbolDesign(InventoryItem item, GuildSymbolDesign design) {
        if (item == null || design == null) return;
        GNDItemMap data = item.getGndData();
        data.setInt("bgShape", design.getBackgroundShape());
        data.setInt("primaryColor", design.getPrimaryColor());
        data.setInt("secondaryColor", design.getSecondaryColor());
        data.setInt("emblemID", design.getEmblemID());
        data.setInt("emblemColor", design.getEmblemColor());
        data.setInt("borderStyle", design.getBorderStyle());
    }

    public GuildFlagObjectItem(GuildFlagObject object) {
        super(object);
        this.translatedTypeName = new LocalMessage("object", "guildflag");
    }

    @Override
    public String canPlace(Level level, ObjectPlaceOption po, PlayerMob player, Line2D playerPositionLine, InventoryItem item, GNDItemMap mapContent) {
        if (level.isServer() && player != null) {
            ServerClient client = player.getServerClient();
            GameObject objectAtTile = level.getObject(po.tileX, po.tileY);
            
            // Check if there's a settlement flag (GameObject.canPlace will validate this)
            if (objectAtTile == null || !objectAtTile.getStringID().equals("settlementflag")) {
                return "needssettlementflag";
            }
            
            // Check ownership
            SettlementsWorldData settlementsData = SettlementsWorldData.getSettlementsData(level);
            ServerSettlementData settlementData = settlementsData.getServerDataAtTile(level.getIdentifier(), po.tileX, po.tileY);
            if (settlementData == null || settlementData.networkData.getOwnerAuth() != client.authentication) {
                return "notyoursettlement";
            }
            
            // All checks passed - allow placement
            return null;
        }

        return super.canPlace(level, po, player, playerPositionLine, item, mapContent);
    }

    @Override
    public InventoryItem onAttemptPlace(Level level, int x, int y, PlayerMob player, InventoryItem item, GNDItemMap mapContent, String error) {
        boolean sendUpdate = false;

        switch (error) {
            case "needssettlementflag":
                player.getServerClient().sendChatMessage(new LocalMessage("misc", "guildflagsneedsettlementflag"));
                sendUpdate = true;
                break;
            case "notyoursettlement":
                player.getServerClient().sendChatMessage(new LocalMessage("misc", "notyoursettlement"));
                sendUpdate = true;
                break;
            case "alreadyguildflag":
                player.getServerClient().sendChatMessage(new LocalMessage("misc", "alreadyhasguildflag"));
                sendUpdate = true;
                break;
        }

        if (sendUpdate) {
            ObjectPlaceOption po = this.getPlaceOptionFromMap(mapContent);
            if (po != null) {
                player.getServerClient().sendPacket(new PacketChangeObject(level, 0, po.tileX, po.tileY));
            }
            return item;
        }

        return super.onAttemptPlace(level, x, y, player, item, mapContent, error);
    }

    @Override
    public boolean onPlaceObject(GameObject object, Level level, int layerID, int tileX, int tileY, int rotation, ServerClient client, InventoryItem item) {
        if (client == null) {
            return super.onPlaceObject(object, level, layerID, tileX, tileY, rotation, client, item);
        }

        // TEMP: Guild flags are disabled until we redesign settlement integration
        client.sendChatMessage("Guild flags are temporarily disabled. Use guild banners instead!");
        return false;
    }
}
