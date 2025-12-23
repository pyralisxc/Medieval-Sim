package medievalsim.guilds.objects;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;

import medievalsim.guilds.GuildSymbolDesign;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.Localization;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.DrawOptions;
import necesse.gfx.drawOptions.texture.TextureDrawOptionsEnd;
import necesse.gfx.drawables.LevelSortedDrawable;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.gameTexture.GameTexture;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.ObjectDamagedTextureArray;
import necesse.level.gameObject.ObjectHoverHitbox;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.NetworkSettlementData;
import necesse.level.maps.light.GameLight;

/**
 * Guild flag - placed ON TOP of settlement flags to claim for guild.
 * Maintains all settlement functionality while adding guild features.
 * Uses settlement flag as the base object (doesn't replace it).
 */
public class GuildFlagObject extends GameObject {

    public ObjectDamagedTextureArray texture;
    private GameTexture guildSymbolOverlay;

    public GuildFlagObject() {
        // Match settlement flag collision box
        super(new Rectangle(6, 6, 20, 20));
        this.mapColor = new Color(139, 0, 0); // Dark red for guild
        this.isLightTransparent = true;
        this.displayMapTooltip = true;
        this.rarity = Item.Rarity.UNCOMMON;
        setItemCategory("objects", "misc");
        setCraftingCategory("objects", "misc");
    }

    @Override
    public String canPlace(Level level, int layerID, int x, int y, int rotation, boolean byPlayer, boolean ignoreOtherLayers) {
        // Guild flags must be placed on settlement flags
        GameObject objectAtTile = level.getObject(layerID, x, y);
        if (objectAtTile == null || !objectAtTile.getStringID().equals("settlementflag")) {
            return "needssettlementflag";
        }
        return null;
    }

    @Override
    public void loadTextures() {
        super.loadTextures();
        // Load as overlay texture that goes on top of settlement flag
        texture = ObjectDamagedTextureArray.loadAndApplyOverlay(this, "objects/guild_flag");
    }

    @Override
    public void addDrawables(List<LevelSortedDrawable> list, OrderableDrawables tileList, Level level, int tileX, int tileY, TickManager tickManager, GameCamera camera, PlayerMob perspective) {
        GameLight light = level.getLightLevel(tileX, tileY);
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        
        // Get guild entity data
        GuildFlagObjectEntity entity = (GuildFlagObjectEntity) level.entityManager.getObjectEntity(tileX, tileY);
        GuildSymbolDesign design = entity != null ? entity.getSymbolDesign() : null;
        
        // Get settlement data for displaying settlement owner look (like vanilla)
        SettlementsWorldData settlementsData = SettlementsWorldData.getSettlementsData(level);
        NetworkSettlementData networkData = settlementsData.getNetworkDataAtTile(level.getIdentifier(), tileX, tileY);
        
        // Draw guild flag base texture
        GameTexture flagTexture = this.texture.getDamagedTexture(this, level, tileX, tileY);
        final TextureDrawOptionsEnd baseOptions = flagTexture.initDraw()
            .sprite(0, 0, 32, flagTexture.getHeight())
            .light(light)
            .pos(drawX, drawY - flagTexture.getHeight() + 32);

        // TODO: Add guild symbol rendering on top of flag
        // Will need to render the symbol design if it exists
        
        list.add(new LevelSortedDrawable(this, tileX, tileY) {
            @Override
            public int getSortY() {
                return 16;
            }

            @Override
            public void draw(TickManager tickManager) {
                baseOptions.draw();
                // TODO: Draw guild symbol overlay here
            }
        });
    }

    @Override
    public void drawPreview(Level level, int tileX, int tileY, int rotation, float alpha, PlayerMob player, GameCamera camera) {
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        GameTexture flagTexture = this.texture.getDamagedTexture(0.0f);
        flagTexture.initDraw()
            .sprite(0, 0, 32, flagTexture.getHeight())
            .alpha(alpha)
            .draw(drawX, drawY - flagTexture.getHeight() + 32);
    }

    @Override
    public List<ObjectHoverHitbox> getHoverHitboxes(Level level, int layerID, int tileX, int tileY) {
        // Match settlement flag hover hitboxes
        List<ObjectHoverHitbox> list = super.getHoverHitboxes(level, layerID, tileX, tileY);
        list.add(new ObjectHoverHitbox(layerID, tileX, tileY, 4, -38, 24, 38));
        return list;
    }

    @Override
    public ObjectEntity getNewObjectEntity(Level level, int x, int y) {
        return new GuildFlagObjectEntity(level, x, y, this.texture);
    }

    @Override
    public ListGameTooltips getItemTooltips(InventoryItem item, PlayerMob perspective) {
        ListGameTooltips tooltips = super.getItemTooltips(item, perspective);
        tooltips.add(Localization.translate("itemtooltip", "guildflagtip"));
        return tooltips;
    }

    @Override
    public GameTexture generateItemTexture() {
        return GameTexture.fromFile("items/guildflag");
    }

    @Override
    public String getInteractTip(Level level, int x, int y, PlayerMob perspective, boolean debug) {
        // Interact opens guild UI instead of settlement UI
        return Localization.translate("controls", "usetip");
    }

    @Override
    public boolean canInteract(Level level, int x, int y, PlayerMob player) {
        return true;
    }

    @Override
    public int getInteractRange(Level level, int tileX, int tileY) {
        return 32000; // Match settlement flag range
    }

    @Override
    public void interact(Level level, int x, int y, PlayerMob player) {
        // TODO: Open guild management UI
        // For now, could open settlement UI with guild overlay
        if (level.isServer()) {
            GuildFlagObjectEntity entity = (GuildFlagObjectEntity) level.entityManager.getObjectEntity(x, y);
            if (entity != null) {
                int guildID = entity.getGuildID();
                // TODO: Open guild UI with this guild ID
            }
        }
    }

    @Override
    public Item generateNewObjectItem() {
        return new medievalsim.guilds.items.GuildFlagObjectItem(this);
    }
    
    public static void registerObject() {
        ObjectRegistry.registerObject("guildflag", new GuildFlagObject(), 15.0f, true);
    }
}
