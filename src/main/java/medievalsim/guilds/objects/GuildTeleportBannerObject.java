/*
 * Guild Teleport Banner Object for Medieval Sim Mod
 * Placeable banner that allows teleportation between guild locations.
 * 
 * Based on Necesse's BannerObject with added interaction for teleportation.
 */
package medievalsim.guilds.objects;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;
import java.util.function.Consumer;

import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildManager;
import medievalsim.guilds.PermissionType;
import medievalsim.guilds.teleport.GuildTeleportContainer;
import medievalsim.util.ModLogger;
import necesse.engine.Settings;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.Localization;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.util.GameRandom;
import necesse.engine.util.GameUtils;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.gfx.GameResources;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.texture.TextureDrawOptionsEnd;
import necesse.gfx.drawOptions.texture.TextureDrawOptionsPositionMod;
import necesse.gfx.drawables.LevelSortedDrawable;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.GameColor;
import necesse.gfx.gameTexture.GameTexture;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.ObjectDamagedTextureArray;
import necesse.level.gameObject.ObjectHoverHitbox;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;

/**
 * Guild teleport banner - allows guild members to teleport between banner locations.
 * Waving animation like regular banners, but interactive for teleportation.
 */
public class GuildTeleportBannerObject extends GameObject {

    public ObjectDamagedTextureArray texture;
    protected int xOffset = 0;
    protected final GameRandom drawRandom;
    final int animTime = 1600;

    public GuildTeleportBannerObject() {
        super(new Rectangle(5, 4, 22, 20));
        this.mapColor = new Color(100, 150, 200); // Blue-ish for teleport
        this.displayMapTooltip = true;
        this.stackSize = 10;
        this.objectHealth = 100;
        this.toolType = ToolType.ALL;
        this.isLightTransparent = true;
        this.lightLevel = 30; // Subtle glow to indicate it's special
        this.lightHue = 200f; // Blue
        this.lightSat = 0.3f;
        this.rarity = Item.Rarity.UNCOMMON;
        this.drawRandom = new GameRandom();
        this.setItemCategory("objects", "decorations", "banners");
        this.setCraftingCategory("objects", "decorations", "banners");
    }

    @Override
    public void loadTextures() {
        super.loadTextures();
        this.texture = ObjectDamagedTextureArray.loadAndApplyOverlay(this, "objects/guildteleportbanner");
    }

    @Override
    public void addDrawables(List<LevelSortedDrawable> list, OrderableDrawables tileList, Level level, 
                            int tileX, int tileY, TickManager tickManager, GameCamera camera, PlayerMob perspective) {
        Consumer<TextureDrawOptionsPositionMod> waveChange;
        GameLight light = level.getLightLevel(tileX, tileY);
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        GameTexture tex = this.texture.getDamagedTexture(this, level, tileX, tileY);
        int textureWidth = tex.getWidth() / 4; // 4 animation frames
        int textureHeight = tex.getHeight();
        int sprite = GameUtils.getAnim(Math.abs(level.getTime() + getTileSeed(tileX, tileY, 52)), 4, 1600);
        
        synchronized (this.drawRandom) {
            this.drawRandom.setSeed(getTileSeed(tileX, tileY));
            waveChange = GameResources.waveShader.setupGrassWaveMod(level, tileX, tileY, 1000L, 0.02f, 2, 
                this.drawRandom, getTileSeed(tileX, tileY, 0), false, 3.0f);
        }
        
        final TextureDrawOptionsEnd options = tex.initDraw()
            .sprite(sprite, 0, textureWidth, textureHeight)
            .light(light)
            .addPositionMod(waveChange)
            .pos(drawX - textureWidth / 4 + this.xOffset, drawY - textureHeight + 32);
        
        list.add(new LevelSortedDrawable(this, tileX, tileY) {
            @Override
            public int getSortY() {
                return 16;
            }

            @Override
            public void draw(TickManager tickManager) {
                options.draw();
            }
        });
    }

    @Override
    public void tickEffect(Level level, int layerID, int tileX, int tileY) {
        super.tickEffect(level, layerID, tileX, tileY);
        
        // Wind effects like regular banners
        if (!Settings.windEffects) return;
        
        float windSpeed = level.weatherLayer.getWindSpeed();
        if (windSpeed > 0.2f) {
            float windAmount = level.weatherLayer.getWindAmount(tileX, tileY) * 3.0f;
            if (windAmount > 0.5f) {
                float buffer = 0.016666668f * windAmount * windSpeed;
                if (buffer >= 1.0f || GameRandom.globalRandom.getChance(buffer)) {
                    level.makeGrassWeave(tileX, tileY, 1600, false);
                }
            }
        }
    }

    @Override
    public void drawPreview(Level level, int tileX, int tileY, int rotation, float alpha, PlayerMob player, GameCamera camera) {
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        GameTexture tex = this.texture.getDamagedTexture(0.0f);
        int textureWidth = tex.getWidth() / 4;
        int textureHeight = tex.getHeight();
        tex.initDraw()
            .sprite(0, 0, textureWidth, textureHeight)
            .alpha(alpha)
            .draw(drawX - textureWidth / 4 + this.xOffset, drawY - textureHeight + 32);
    }

    @Override
    public ListGameTooltips getItemTooltips(InventoryItem item, PlayerMob perspective) {
        ListGameTooltips tooltips = super.getItemTooltips(item, perspective);
        tooltips.add(Localization.translate("itemtooltip", "guildteleportbannertip1"));
        tooltips.add(Localization.translate("itemtooltip", "guildteleportbannertip2"));
        return tooltips;
    }

    @Override
    public List<ObjectHoverHitbox> getHoverHitboxes(Level level, int layerID, int tileX, int tileY) {
        List<ObjectHoverHitbox> list = super.getHoverHitboxes(level, layerID, tileX, tileY);
        list.add(new ObjectHoverHitbox(layerID, tileX, tileY, 0, -32, 32, 32));
        return list;
    }

    @Override
    public String getInteractTip(Level level, int x, int y, PlayerMob perspective, boolean debug) {
        return Localization.translate("controls", "usetip");
    }

    @Override
    public boolean canInteract(Level level, int x, int y, PlayerMob player) {
        return true;
    }

    @Override
    public void interact(Level level, int x, int y, PlayerMob player) {
        if (level.isServer()) {
            ServerClient client = player.getServerClient();
            if (client == null) return;
            
            // Get banner entity
            ObjectEntity oe = level.entityManager.getObjectEntity(x, y);
            if (!(oe instanceof GuildTeleportBannerObjectEntity)) {
                client.sendChatMessage(GameColor.RED.getColorCode() + "This banner is not properly configured.");
                return;
            }
            
            GuildTeleportBannerObjectEntity bannerEntity = (GuildTeleportBannerObjectEntity) oe;
            int bannerGuildID = bannerEntity.getOwnerGuildID();
            
            // Get player's guild
            GuildManager gm = GuildManager.get(level.getServer().world);
            if (gm == null) {
                client.sendChatMessage(GameColor.RED.getColorCode() + "Guild system not available.");
                return;
            }
            
            GuildData playerGuild = gm.getPlayerPrimaryGuild(client.authentication);
            if (playerGuild == null) {
                client.sendChatMessage(GameColor.RED.getColorCode() + "You must be in a guild to use this banner.");
                return;
            }
            
            // If banner is not owned yet, claim it for the player's guild
            if (bannerGuildID <= 0) {
                bannerEntity.setOwnerGuildID(playerGuild.getGuildID());
                bannerGuildID = playerGuild.getGuildID();
                client.sendChatMessage(GameColor.GREEN.getColorCode() + "Banner claimed for " + playerGuild.getName() + "!");
                ModLogger.debug("Banner claimed by guild %s at %d,%d", playerGuild.getName(), x, y);
            }
            
            // Check if banner belongs to player's guild
            if (bannerGuildID != playerGuild.getGuildID()) {
                client.sendChatMessage(GameColor.RED.getColorCode() + "This banner belongs to another guild.");
                return;
            }
            
            // Check permission
            if (!playerGuild.hasPermission(client.authentication, PermissionType.USE_TELEPORT_STAND)) {
                client.sendChatMessage(GameColor.RED.getColorCode() + "You don't have permission to use guild teleport banners.");
                return;
            }
            
            // Open teleport UI
            GuildTeleportContainer.openTeleportUI(client, playerGuild.getGuildID(), x, y, level);
            ModLogger.debug("Player %s opened guild teleport banner at %d,%d", client.getName(), x, y);
        }
    }

    @Override
    public ObjectEntity getNewObjectEntity(Level level, int x, int y) {
        return new GuildTeleportBannerObjectEntity(level, x, y);
    }

    @Override
    public GameTexture generateItemTexture() {
        return GameTexture.fromFile("items/guildteleportbanner");
    }

    /**
     * Register this object with the game.
     */
    public static void registerObject() {
        ObjectRegistry.registerObject("guildteleportbanner", new GuildTeleportBannerObject(), 20.0f, true);
    }
}
