package medievalsim.guilds.items;

import medievalsim.guilds.GuildSymbolDesign;
import medievalsim.guilds.crest.GuildCrestRenderer;
import medievalsim.util.ModLogger;
import necesse.engine.registries.ItemRegistry;
import necesse.inventory.item.trinketItem.TrinketItem;
import necesse.entity.mobs.buffs.staticBuffs.armorBuffs.trinketBuffs.TrinketBuff;
import necesse.inventory.lootTable.lootItem.OneOfLootItems;
import necesse.inventory.InventoryItem;
import necesse.entity.mobs.PlayerMob;
import necesse.engine.util.GameBlackboard;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.gameNetworkData.GNDItemMap;

import java.awt.Color;

public class GuildCrestItem extends TrinketItem {

    public GuildCrestItem() {
        super(
            Rarity.UNCOMMON,
            0,
            new OneOfLootItems()
        );
        this.keyWords.add("guild");
        this.keyWords.add("crest");
    }

    @Override
    public TrinketBuff[] getBuffs(InventoryItem item) {
        return new TrinketBuff[0];
    }

    @Override
    public void drawIcon(InventoryItem item, PlayerMob perspective, int x, int y, int size, Color color) {
        GuildSymbolDesign design = getSymbolDesign(item);
        if (design != null) {
            try {
                GuildCrestRenderer.drawSymbolOnItem(design, "guildcrest", x, y, size);
            } catch (Exception e) {
                necesse.engine.GameLog.warn.println("Failed to render guild crest symbol: " + e.getMessage());
                // Fallback to default icon if rendering fails
                super.drawIcon(item, perspective, x, y, size, color);
            }
        } else {
            // No guild data - show default icon
            super.drawIcon(item, perspective, x, y, size, color);
        }
    }

    @Override
    public ListGameTooltips getPostEnchantmentTooltips(InventoryItem item, PlayerMob perspective, GameBlackboard blackboard) {
        ListGameTooltips tooltips = super.getPostEnchantmentTooltips(item, perspective, blackboard);
        tooltips.add(new LocalMessage("itemtooltip", "guildcrestdesc"));
        
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
        GuildCrestItem item = new GuildCrestItem();
        ItemRegistry.registerItem("guildcrest", item, 25.0f, true);
        ModLogger.info("Registered guild crest item");
    }
}