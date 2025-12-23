package medievalsim.guilds.items;

import necesse.engine.registries.MobRegistry;
import necesse.gfx.gameTexture.GameSprite;
import necesse.inventory.item.placeableItem.MobSpawnItem;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;

/**
 * Custom spawn item for the Guild Artisan so the spawn icon is sized correctly in UI.
 */
public class GuildArtisanSpawnItem extends MobSpawnItem {

    public GuildArtisanSpawnItem() {
        super(1, true, "guildartisan");
    }

    @Override
    public GameSprite getItemSprite(InventoryItem item, PlayerMob perspective) {
        // Use a 16x16 icon for consistent appearance in menus
        return new GameSprite(MobRegistry.getMobIcon(this.mobStringID), 16);
    }
}
