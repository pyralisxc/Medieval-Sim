package medievalsim.guilds.objects;

import medievalsim.guilds.GuildSymbolDesign;
import medievalsim.guilds.crest.GuildCrestRenderer;
import necesse.engine.network.gameNetworkData.GNDItemMap;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.util.GameBlackboard;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.placeableItem.objectItem.ObjectItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.engine.localization.message.LocalMessage;

import java.awt.Color;

/**
 * ObjectItem for Guild Banners.
 * Stores guild ID and symbol design, handles placement validation.
 */
public class GuildBannerObjectItem extends ObjectItem {

    public GuildBannerObjectItem() {
        super(ObjectRegistry.getObject("guildbanner"));
    }

    @Override
    public void drawIcon(InventoryItem item, PlayerMob perspective, int x, int y, int size, Color color) {
        GuildSymbolDesign design = getSymbolDesign(item);
        if (design != null) {
            try {
                GuildCrestRenderer.drawSymbolOnItem(design, "guildbanner", x, y, size);
            } catch (Exception e) {
                super.drawIcon(item, perspective, x, y, size, color);
            }
        } else {
            super.drawIcon(item, perspective, x, y, size, color);
        }
    }

    @Override
    public ListGameTooltips getTooltips(InventoryItem item, PlayerMob perspective, GameBlackboard blackboard) {
        ListGameTooltips tooltips = super.getTooltips(item, perspective, blackboard);
        
        int guildID = getGuildID(item);
        if (guildID >= 0) {
            tooltips.add(new LocalMessage("ui", "guildid", "id", String.valueOf(guildID)));
        }
        
        return tooltips;
    }

    @Override
    public boolean onPlaceObject(GameObject object, Level level, int layerID, int tileX, int tileY, int rotation, ServerClient client, InventoryItem item) {
        boolean success = super.onPlaceObject(object, level, layerID, tileX, tileY, rotation, client, item);
        
        if (success && item != null) {
            // Transfer guild data from item to entity
            ObjectEntity ent = level.entityManager.getObjectEntity(tileX, tileY);
            if (ent instanceof GuildBannerObjectEntity) {
                GuildBannerObjectEntity bannerEntity = (GuildBannerObjectEntity) ent;
                int guildID = getGuildID(item);
                GuildSymbolDesign design = getSymbolDesign(item);
                
                if (guildID >= 0) {
                    bannerEntity.setGuildID(guildID);
                }
                if (design != null) {
                    bannerEntity.setSymbolDesign(design);
                }
                // Track placer auth for per-player limits (if available)
                if (client != null) {
                    bannerEntity.setPlacedByAuth(client.authentication);
                }
            }
        }
        
        return success;
    }

    // Guild data accessors
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
}
