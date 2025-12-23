package medievalsim.guilds.items;

import medievalsim.guilds.GuildSymbolDesign;
import medievalsim.guilds.crest.GuildCrestRenderer;
import medievalsim.util.ModLogger;
import necesse.engine.registries.ItemRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.matItem.MatItem;
import necesse.engine.util.GameBlackboard;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.gameNetworkData.GNDItemMap;

import java.awt.Color;

/**
 * Guild banner item - displays guild symbol.
 * Decorative material item (can be placed in banner stands).
 */
public class GuildBannerItem extends MatItem {

    public GuildBannerItem() {
        super(10, Rarity.UNCOMMON);
        this.setItemCategory("misc");
        this.keyWords.add("guild");
        this.keyWords.add("banner");
    }

    @Override
    public void drawIcon(InventoryItem item, PlayerMob perspective, int x, int y, int size, Color color) {
        GuildSymbolDesign design = getSymbolDesign(item);
        if (design != null) {
            try {
                GuildCrestRenderer.drawSymbolOnItem(design, "guildbanner", x, y, size);
            } catch (Exception e) {
                // Fallback to default icon if rendering fails
                super.drawIcon(item, perspective, x, y, size, color);
            }
        } else {
            // No guild data - show default icon
            super.drawIcon(item, perspective, x, y, size, color);
        }
    }

    @Override
    public ListGameTooltips getTooltips(InventoryItem item, PlayerMob perspective, GameBlackboard blackboard) {
        ListGameTooltips tooltips = super.getTooltips(item, perspective, blackboard);
        tooltips.add(new LocalMessage("itemtooltip", "guildbannerdesc"));
        
        int guildID = getGuildID(item);
        if (guildID >= 0) {
            tooltips.add(new LocalMessage("ui", "guildid", "id", String.valueOf(guildID)));
        }
        
        return tooltips;
    }

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
        data.setInt("backgroundColor", design.getBackgroundColor());
        data.setInt("emblemID", design.getEmblemID());
        data.setInt("emblemColor", design.getEmblemColor());
        data.setInt("borderStyle", design.getBorderStyle());
    }

    public static void registerItem() {
        GuildBannerItem item = new GuildBannerItem();
        ItemRegistry.registerItem("guildbanner", item, 15.0f, true);
        ModLogger.info("Registered guild banner item");
    }
}
