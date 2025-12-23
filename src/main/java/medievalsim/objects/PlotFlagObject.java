package medievalsim.objects;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.localization.Localization;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.localization.message.StaticMessage;
import java.util.Collections;
import necesse.engine.network.packet.PacketChangeObject;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ContainerRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.registries.RecipeTechRegistry;
import necesse.gfx.gameTexture.GameTexture;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.GameColor;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.container.SettlementNameContainer;
import necesse.inventory.item.Item;
import necesse.inventory.item.toolItem.ToolType;
import necesse.inventory.recipe.Ingredient;
import necesse.inventory.recipe.Recipe;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.ObjectDamagedTextureArray;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;

import java.awt.Color;
import java.awt.Rectangle;

/**
 * Plot flag that can be purchased by players to create a settlement.
 * Admin places these, and players walk up and pay the cost to convert it to their settlement.
 */
public class PlotFlagObject extends GameObject {
    @SuppressWarnings("deprecation")
    public ObjectDamagedTextureArray texture;

    public PlotFlagObject() {
        super(new Rectangle(6, 6, 20, 20));
        this.mapColor = new Color(200, 150, 50); // Distinct color from settlement flags
        this.toolType = ToolType.ALL;
        this.isLightTransparent = true;
        this.setItemCategory("objects", "misc");
        this.setCraftingCategory("objects", "misc");
    }

    @SuppressWarnings("deprecation")
    @Override
    public void loadTextures() {
        super.loadTextures();
        // Load the plot flag texture (uses settlement flag sprite)
        this.texture = ObjectDamagedTextureArray.loadAndApplyOverlay(this, "objects/plotflag");
    }

    @Override
    public void addDrawables(java.util.List<necesse.gfx.drawables.LevelSortedDrawable> list, 
                            necesse.gfx.drawables.OrderableDrawables tileList, 
                            Level level, int tileX, int tileY, 
                            necesse.engine.gameLoop.tickManager.TickManager tickManager, 
                            necesse.gfx.camera.GameCamera camera, 
                            PlayerMob perspective) {
        necesse.level.maps.light.GameLight light = level.getLightLevel(tileX, tileY);
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        
        GameTexture texture = this.texture.getDamagedTexture(this, level, tileX, tileY);
        final necesse.gfx.drawOptions.texture.TextureDrawOptionsEnd drawOptions = 
            texture.initDraw()
                .sprite(0, 0, 32, texture.getHeight())
                .light(light)
                .pos(drawX, drawY - texture.getHeight() + 32);
        
        list.add(new necesse.gfx.drawables.LevelSortedDrawable(this, tileX, tileY) {
            @Override
            public int getSortY() {
                return 16;
            }

            @Override
            public void draw(necesse.engine.gameLoop.tickManager.TickManager tickManager) {
                drawOptions.draw();
            }
        });
    }

    @Override
    public void drawPreview(Level level, int tileX, int tileY, int rotation, float alpha, PlayerMob player, 
                           necesse.gfx.camera.GameCamera camera) {
        int drawX = camera.getTileDrawX(tileX);
        int drawY = camera.getTileDrawY(tileY);
        GameTexture texture = this.texture.getDamagedTexture(0.0f);
        texture.initDraw()
            .sprite(0, 0, 32, texture.getHeight())
            .alpha(alpha)
            .draw(drawX, drawY - texture.getHeight() + 32);
    }

    @Override
    public String canPlace(Level level, int layerID, int x, int y, int rotation, boolean byPlayer, boolean ignoreOtherLayers) {
        String error = super.canPlace(level, layerID, x, y, rotation, byPlayer, ignoreOtherLayers);
        if (error != null) {
            return error;
        }
        if (!level.getIdentifier().equals(LevelIdentifier.SURFACE_IDENTIFIER)) {
            return "notsurface";
        }
        
        // Note: Settlement spacing validation is ONLY done in PlotFlagObjectItem.canPlace()
        // Do NOT duplicate validation here - causes double-checking with wrong coordinates
        // The ByteBuddy spacing patch intercepts canPlaceSettlementFlagAt() calls from the Item
        
        return null;
    }

    @Override
    public ListGameTooltips getItemTooltips(InventoryItem item, PlayerMob perspective) {
        ListGameTooltips tooltips = super.getItemTooltips(item, perspective);
        tooltips.add(Localization.translate("ui", "plotflagtip1"));
        tooltips.add(Localization.translate("ui", "plotflagtip2"));
        return tooltips;
    }
    @Override
    public Item generateNewObjectItem() {
        return new PlotFlagObjectItem(this);
    }

    @Override
    public String getInteractTip(Level level, int x, int y, PlayerMob perspective, boolean debug) {
        // Get coin cost from config
        int coinCost = ModConfig.Settlements.plotFlagCoinCost;
        return Localization.translate("ui", "plotflaginteract", "coins", coinCost);
    }

    @Override
    public boolean canInteract(Level level, int x, int y, PlayerMob player) {
        return true;
    }

    @Override
    public int getInteractRange(Level level, int tileX, int tileY) {
        return 32000;
    }

    @Override
    public void interact(Level level, int x, int y, PlayerMob player) {
        if (!level.isServer()) {
            return;
        }

        ServerClient client = player.getServerClient();
        if (client == null) {
            return;
        }

        // Get coin cost from config
        int coinCost = ModConfig.Settlements.plotFlagCoinCost;

        // Create recipe for coin payment
        Ingredient[] ingredients = new Ingredient[] {
            new Ingredient("coin", coinCost)
        };
        Recipe paymentRecipe = new Recipe("air", RecipeTechRegistry.NONE, ingredients);

        // Check if player can afford it
        if (!paymentRecipe.canCraft(level, player, Collections.singletonList(player.getInv().main), false).canCraft()) {
            client.sendChatMessage(new LocalMessage("ui", "plotflagcannotafford", 
                "coins", coinCost));
            return;
        }

        // Check settlement placement validity
        SettlementsWorldData settlementsData = SettlementsWorldData.getSettlementsData(level);
        int flagTier = 0; // Plot flags always start at tier 0

        // Check max settlements per player
        int max = level.getServer().world.settings.maxSettlementsPerPlayer;
        if (max >= 0) {
            long current = settlementsData.streamSettlements()
                .filter(e -> e.getOwnerAuth() == client.authentication && 
                            !e.isTileWithinBounds(x, y))
                .count();
            if (current >= max) {
                client.sendChatMessage(new LocalMessage("misc", "maxsettlementsreached", "count", max));
                return;
            }
        }

        // Check settlement spacing (vanilla validation)
        if (!settlementsData.canPlaceSettlementFlagAt(level.getIdentifier(), x, y, flagTier)) {
            client.sendChatMessage(new LocalMessage("misc", "tooclosesettlement"));
            return;
        }

        // Take payment from player
        paymentRecipe.craft(level, player, Collections.singletonList(player.getInv().main));

        // Send coins to world owner's bank (if banking is enabled and world has an owner)
        if (ModConfig.Banking.enabled && necesse.engine.Settings.serverOwnerAuth != -1L) {
            medievalsim.banking.domain.BankingLevelData bankingData =
                medievalsim.banking.domain.BankingLevelData.getBankingData(level);

            if (bankingData != null) {
                medievalsim.banking.domain.PlayerBank worldOwnerBank =
                    bankingData.getOrCreateBank(necesse.engine.Settings.serverOwnerAuth);

                // Add coins to world owner's bank (always succeeds - unlimited storage)
                worldOwnerBank.addCoins(coinCost);
                ModLogger.debug("Sent %d coins from plot purchase to world owner's bank (auth=%d)",
                    coinCost, necesse.engine.Settings.serverOwnerAuth);
            }
        }

        // Get settlement flag ID
        GameObject settlementFlagObject = ObjectRegistry.getObject("settlementflag");
        if (settlementFlagObject == null) {
            client.sendChatMessage(new StaticMessage(GameColor.RED.getColorCode() + 
                "Error: Settlement flag object not found"));
            ModLogger.error("Settlement flag object not found in registry");
            return;
        }

        // Replace plot flag with settlement flag (layer 0)
        level.objectLayer.setObject(0, x, y, settlementFlagObject.getID());
        level.objectLayer.setObjectRotation(0, x, y, 0);
        level.objectLayer.setIsPlayerPlaced(0, x, y, true);

        // Set up settlement data with player ownership (following vanilla pattern)
        ServerSettlementData serverData = settlementsData.getOrCreateServerData(level, x, y);
        serverData.networkData.setFlagTile(x, y);
        settlementsData.updateSettlement(serverData);
        serverData.networkData.setOwner(client);
        serverData.clearOutsideBounds();
        serverData.networkData.markDirty(true);

        // Achievement tracking
        if (client.achievementsLoaded()) {
            client.achievements().START_SETTLEMENT.markCompleted(client);
        }

        // Open settlement naming container if name not set (vanilla behavior)
        if (!serverData.networkData.isSettlementNameSet()) {
            ContainerRegistry.openAndSendContainer(client, 
                new PacketOpenContainer(ContainerRegistry.SETTLEMENT_NAME_CONTAINER, 
                    SettlementNameContainer.getContainerContent(serverData.uniqueID)));
        }

        // Send success message
        client.sendChatMessage(new LocalMessage("ui", "plotflagpurchased"));

        // Update client tile
        client.sendPacket(new PacketChangeObject(level, 0, x, y));

        ModLogger.debug("Player %s purchased plot flag at (%d, %d)", client.getName(), x, y);
    }
}
