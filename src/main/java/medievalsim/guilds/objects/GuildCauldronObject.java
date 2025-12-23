/*
 * Guild Cauldron Object for Medieval Sim Mod
 * Interactive cauldron that can brew guild-wide buffs.
 * 
 * Guild members can add ingredients to create beneficial effects.
 */
package medievalsim.guilds.objects;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.List;

import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildManager;
import medievalsim.guilds.PermissionType;
import medievalsim.util.ModLogger;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.Localization;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.particle.Particle;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.texture.TextureDrawOptionsEnd;
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
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;

/**
 * Guild cauldron - a brewing station for guild-wide buffs.
 * When active, produces bubbling particles and provides benefits.
 */
public class GuildCauldronObject extends GameObject {

    public ObjectDamagedTextureArray texture;
    private GameRandom drawRandom;

    public GuildCauldronObject() {
        super(new Rectangle(2, 2, 28, 28));
        this.mapColor = new Color(80, 60, 100); // Purple-ish for magic
        this.displayMapTooltip = true;
        this.objectHealth = 80;
        this.toolType = ToolType.ALL;
        this.isLightTransparent = true;
        this.lightLevel = 50; // Soft glow when active
        this.lightHue = 120f; // Green-ish (potion color)
        this.lightSat = 0.7f;
        this.rarity = Item.Rarity.UNCOMMON;
        this.hoverHitbox = new Rectangle(0, -16, 32, 48);
        this.roomProperties.add("lights");
        this.drawRandom = new GameRandom();
        this.setItemCategory("objects", "misc");
        this.setCraftingCategory("objects", "misc");
    }

    @Override
    public void loadTextures() {
        super.loadTextures();
        this.texture = ObjectDamagedTextureArray.loadAndApplyOverlay(this, "objects/guild_cauldron");
    }

    @Override
    public ListGameTooltips getItemTooltips(InventoryItem item, PlayerMob perspective) {
        ListGameTooltips tooltips = super.getItemTooltips(item, perspective);
        tooltips.add(Localization.translate("itemtooltip", "guildcauldrontent"));
        tooltips.add(Localization.translate("itemtooltip", "guildcauldrontent2"));
        return tooltips;
    }

    @Override
    public void addDrawables(List<LevelSortedDrawable> list, OrderableDrawables tileList, Level level, int tileX, int tileY, TickManager tickManager, GameCamera camera, PlayerMob perspective) {
        GameLight light = level.getLightLevel(tileX, tileY);
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        
        GameTexture tex = this.texture.getDamagedTexture(this, level, tileX, tileY);
        
        // Animation frames for bubbling effect
        int spriteX = 0;
        if (tex.getWidth() > 32) {
            spriteX = (int)((level.getWorldEntity().getTime() / 200) % (tex.getWidth() / 32));
        }
        
        final TextureDrawOptionsEnd options = tex.initDraw()
            .sprite(spriteX, 0, 32, tex.getHeight())
            .light(light)
            .pos(drawX, drawY - tex.getHeight() + 32);
        
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
    public void drawPreview(Level level, int tileX, int tileY, int rotation, float alpha, PlayerMob player, GameCamera camera) {
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        GameTexture tex = this.texture.getDamagedTexture(0.0f);
        tex.initDraw()
            .sprite(0, 0, 32, tex.getHeight())
            .alpha(alpha)
            .draw(drawX, drawY - tex.getHeight() + 32);
    }

    @Override
    public void tickEffect(Level level, int layerID, int x, int y) {
        super.tickEffect(level, layerID, x, y);
        
        // Spawn bubbling particles periodically
        if (level.isClient()) {
            this.drawRandom.setSeed((long)x * 31 + y);
            if (this.drawRandom.nextInt(10) == 0) {
                float particleX = (x * 32) + 8 + this.drawRandom.nextFloat() * 16;
                float particleY = (y * 32) + 10; // Near the top of cauldron
                
                // Bubble particles rising up
                level.entityManager.addParticle(
                    particleX,
                    particleY,
                    Particle.GType.IMPORTANT_COSMETIC
                ).color(new Color(100, 200, 100, 150))
                 .movesConstant(0, -0.5f)
                 .sizeFades(3, 6)
                 .lifeTime(500);
            }
        }
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
            
            // Check if player is in a guild
            GuildManager gm = GuildManager.get(level.getServer().world);
            if (gm == null) {
                client.sendChatMessage(GameColor.RED.getColorCode() + "Guild system not available.");
                return;
            }
            
            GuildData guild = gm.getPlayerPrimaryGuild(client.authentication);
            if (guild == null) {
                client.sendChatMessage(GameColor.RED.getColorCode() + "You must be in a guild to use this.");
                return;
            }
            
            // Check permission
            if (!guild.hasPermission(client.authentication, PermissionType.USE_CAULDRON)) {
                client.sendChatMessage(GameColor.RED.getColorCode() + "You don't have permission to use guild cauldrons.");
                return;
            }
            
            // TODO: Open cauldron brewing UI
            // For now, just acknowledge
            client.sendChatMessage(GameColor.GRAY.getColorCode() + "Guild cauldron bubbles invitingly. (Brewing system coming soon)");
            ModLogger.debug("Player %s interacted with guild cauldron at %d,%d", 
                client.getName(), x, y);
        }
    }

    @Override
    public ObjectEntity getNewObjectEntity(Level level, int x, int y) {
        // Could add entity to track active brews
        return null;
    }

    @Override
    public GameTexture generateItemTexture() {
        return GameTexture.fromFile("objects/guild_cauldron");
    }

    /**
     * Register this object with the game.
     */
    public static void registerObject() {
        ObjectRegistry.registerObject("guildcauldron", new GuildCauldronObject(), 20.0f, true);
    }
}
