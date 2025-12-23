/*
 * Guild Chest Object for Medieval Sim Mod
 * Storage object for guild items.
 * 
 * Uses the same pattern as StorageBoxInventoryObject.
 */
package medievalsim.guilds.objects;

import java.awt.Color;
import java.awt.Rectangle;

import necesse.engine.registries.ObjectRegistry;
import necesse.inventory.item.toolItem.ToolType;
import necesse.gfx.gameTexture.GameTexture;
import necesse.level.gameObject.container.InventoryObject;
import necesse.level.maps.Level;

/**
 * Guild chest storage object.
 * Extends InventoryObject for proper storage functionality.
 */
public class GuildChestObject extends InventoryObject {
    
    public static final int GUILD_CHEST_SLOTS = 40;
    
    public GuildChestObject() {
        // Texture name matches: objects/guild_chest.png and objects/guild_chest_open.png
        super("guild_chest", GUILD_CHEST_SLOTS, new Rectangle(32, 32), ToolType.ALL, new Color(139, 90, 43)); // Brown color for guild theme
        // Use valid Necesse categories - "misc" for general items
        this.setItemCategory("misc");
        this.displayMapTooltip = true;
    }
    
    @Override
    public Rectangle getCollision(Level level, int x, int y, int rotation) {
        // Same collision pattern as StorageBoxInventoryObject
        if (rotation % 2 == 0) {
            return new Rectangle(x * 32 + 3, y * 32 + 6, 26, 20);
        }
        return new Rectangle(x * 32 + 6, y * 32 + 4, 20, 24);
    }

    @Override
    public GameTexture generateItemTexture() {
        // Use the object texture as the inventory/creative icon
        return GameTexture.fromFile("objects/guild_chest");
    }
    
    /**
     * Register this object with the game.
     */
    public static void registerObject() {
        ObjectRegistry.registerObject("guildchest", new GuildChestObject(), 20.0f, true);
    }
}
