package medievalsim.guilds.objects;

import medievalsim.guilds.GuildSymbolDesign;
import medievalsim.guilds.crest.GuildCrestRenderer;
import medievalsim.util.ModLogger;
import necesse.inventory.InventoryItem;
import necesse.engine.Settings;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.Localization;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.util.GameRandom;
import necesse.engine.util.GameUtils;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.gfx.GameResources;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.texture.TextureDrawOptionsEnd;
import necesse.gfx.drawOptions.texture.TextureDrawOptionsPositionMod;
import necesse.gfx.drawables.LevelSortedDrawable;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.gfx.gameTexture.GameTexture;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.placeableItem.objectItem.ObjectItem;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.ObjectDamagedTextureArray;
import necesse.level.gameObject.ObjectHoverHitbox;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;

import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Guild Banner - Placeable object that displays guild symbols in settlements.
 * Limits: One guild banner per settlement.
 * Similar to Banner of Peace but with guild customization.
 */
public class GuildBannerObject extends GameObject {
    public ObjectDamagedTextureArray texture;
    protected final GameRandom drawRandom;
    final int animTime = 1200;

    public GuildBannerObject() {
        super(new Rectangle(6, 6, 20, 20));
        this.displayMapTooltip = true;
        this.stackSize = 1;
        this.objectHealth = 100;
        this.isLightTransparent = true;
        this.rarity = Item.Rarity.UNCOMMON;
        this.drawRandom = new GameRandom();
        this.setItemCategory("objects", "misc");
    }

    @Override
    public void loadTextures() {
        super.loadTextures();
        // Use guild banner texture (copied from Banner of Peace)
        this.texture = ObjectDamagedTextureArray.loadAndApplyOverlay(this, "objects/guildbanner");
    }

    @Override
    public void addDrawables(List<LevelSortedDrawable> list, OrderableDrawables tileList, Level level, int tileX, int tileY, TickManager tickManager, GameCamera camera, PlayerMob perspective) {
        GameLight light = level.getLightLevel(tileX, tileY);
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        
        GameTexture texture = this.texture.getDamagedTexture(this, level, tileX, tileY);
        int sprite = GameUtils.getAnim(level.getWorldEntity().getTime() + Math.abs(getTileSeed(tileX, tileY, 52)), 4, 1200);
        
        // Setup wave animation like Banner of Peace
        Consumer<TextureDrawOptionsPositionMod> waveChange;
        synchronized (this.drawRandom) {
            this.drawRandom.setSeed(getTileSeed(tileX, tileY));
            waveChange = GameResources.waveShader.setupGrassWaveMod(level, tileX, tileY, 1000L, 0.02f, 2, this.drawRandom, getTileSeed(tileX, tileY, 0), false, 3.0f);
        }

        // Get guild data from entity
        ObjectEntity ent = level.entityManager.getObjectEntity(tileX, tileY);
        GuildSymbolDesign design = null;
        if (ent instanceof GuildBannerObjectEntity) {
            design = ((GuildBannerObjectEntity) ent).getSymbolDesign();
        }

        final GuildSymbolDesign finalDesign = design;
        final TextureDrawOptionsEnd options = ((TextureDrawOptionsEnd) texture.initDraw()
                .sprite(sprite, 0, 64, texture.getHeight())
                .light(light)
                .addPositionMod(waveChange))
                .pos(drawX - 16, drawY - (texture.getHeight() - 32));

        list.add(new LevelSortedDrawable(this, tileX, tileY) {
            @Override
            public int getSortY() {
                return 16;
            }

            @Override
            public void draw(TickManager tickManager) {
                options.draw();
                
                // Draw guild symbol on top if available
                if (finalDesign != null) {
                    try {
                        // Render symbol centered on banner
                        GuildCrestRenderer.drawSymbolOnItem(finalDesign, "guildbanner", 
                            drawX, drawY - 16, 32);
                    } catch (Exception e) {
                        // Silently fail if rendering has issues
                    }
                }
            }
        });
    }

    @Override
    public void drawPreview(Level level, int tileX, int tileY, int rotation, float alpha, PlayerMob player, GameCamera camera) {
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        GameTexture texture = this.texture.getDamagedTexture(0.0f);
        texture.initDraw().sprite(0, 0, 64, texture.getHeight()).alpha(alpha).draw(drawX - 16, drawY - (texture.getHeight() - 32));
    }

    @Override
    public void tickEffect(Level level, int layerID, int tileX, int tileY) {
        super.tickEffect(level, layerID, tileX, tileY);
        if (!Settings.windEffects) {
            return;
        }
        float windSpeed = level.weatherLayer.getWindSpeed();
        if (windSpeed > 0.2f) {
            float windAmount = level.weatherLayer.getWindAmount(tileX, tileY) * 3.0f;
            if (windAmount > 0.5f) {
                float buffer = 0.016666668f * windAmount * windSpeed;
                if (buffer >= 1.0f || GameRandom.globalRandom.getChance(buffer)) {
                    level.makeGrassWeave(tileX, tileY, 1200, false);
                }
            }
        }
    }

    @Override
    public ObjectEntity getNewObjectEntity(Level level, int x, int y) {
        return new GuildBannerObjectEntity(level, x, y);
    }

    @Override
    public ObjectItem generateNewObjectItem() {
        return new GuildBannerObjectItem();
    }

    @Override
    public ListGameTooltips getItemTooltips(InventoryItem item, PlayerMob perspective) {
        ListGameTooltips tooltips = super.getItemTooltips(item, perspective);
        tooltips.add(Localization.translate("itemtooltip", "guildbannerdesc"), 400);
        tooltips.add(Localization.translate("itemtooltip", "placeinanysettlement"));
        return tooltips;
    }

    @Override
    public String canPlace(Level level, int layerID, int x, int y, int rotation, boolean byPlayer, boolean ignoreOtherLayers) {
        String superError = super.canPlace(level, layerID, x, y, rotation, byPlayer, ignoreOtherLayers);
        if (superError != null) {
            return superError;
        }
        
        // Must be in a settlement
        boolean hasSettlement = SettlementsWorldData.getSettlementsData(level).hasSettlementAtTile(level, x, y);
        if (!hasSettlement) {
            return "guildbannermustsettlement";
        }
        
        // Check for existing guild banner in this settlement
        try {
            necesse.engine.world.worldData.SettlementsWorldData settlementsData = SettlementsWorldData.getSettlementsData(level);
            if (settlementsData != null) {
                var currentSettlement = settlementsData.getNetworkDataAtTile(level.getIdentifier(), x, y);
                if (currentSettlement != null) {
                    for (ObjectEntity oe : level.entityManager.objectEntities) {
                        if (!(oe instanceof GuildBannerObjectEntity)) continue;
                        GuildBannerObjectEntity b = (GuildBannerObjectEntity) oe;
                        // If there's a banner in the same settlement, deny placement
                        var bannerSettlement = settlementsData.getNetworkDataAtTile(level.getIdentifier(), b.tileX, b.tileY);
                        if (bannerSettlement != null && bannerSettlement == currentSettlement) {
                            return "guildbannermustoneguild";
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If something goes wrong, fail safe and allow placement (server-side will enforce)
        }

        return null;
    }

    @Override
    public List<ObjectHoverHitbox> getHoverHitboxes(Level level, int layerID, int tileX, int tileY) {
        List<ObjectHoverHitbox> list = super.getHoverHitboxes(level, layerID, tileX, tileY);
        list.add(new ObjectHoverHitbox(layerID, tileX, tileY, 0, -32, 32, 32));
        return list;
    }

    public static void registerObject() {
        GuildBannerObject obj = new GuildBannerObject();
        ObjectRegistry.registerObject("guildbanner", obj, 15.0f, true);
        ModLogger.info("Registered guild banner object");
    }
}
