/*
 * PacketBuyGuildBanner - Client requests to purchase a guild banner
 * Part of Medieval Sim Mod guild management system.
 * Per docs: handles banner purchase with placement or inventory options.
 */
package medievalsim.packets;

import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildManager;
import medievalsim.guilds.GuildRank;
import medievalsim.guilds.PlayerGuildData;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.level.maps.Level;

/**
 * Client->Server packet to purchase a guild banner.
 */
public class PacketBuyGuildBanner extends Packet {

    private int guildID;
    private boolean immediatePlace;

    public PacketBuyGuildBanner(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.immediatePlace = reader.getNextBoolean();
    }

    public PacketBuyGuildBanner(int guildID, boolean immediatePlace) {
        this.guildID = guildID;
        this.immediatePlace = immediatePlace;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextBoolean(immediatePlace);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            GuildManager gm = GuildManager.get(server.world);
            if (gm == null) {
                ModLogger.warn("GuildManager null when processing buy banner request");
                return;
            }

            // Validate player is in the guild
            PlayerGuildData playerData = gm.getPlayerData(client.authentication);
            if (playerData == null) {
                client.sendChatMessage("You are not in any guild.");
                return;
            }

            if (!playerData.isInGuild(guildID)) {
                client.sendChatMessage("You are not a member of this guild.");
                return;
            }

            // Check permission (Officer+ can buy banners)
            GuildRank rank = playerData.getRankInGuild(guildID);
            if (rank == null || rank.level < GuildRank.OFFICER.level) {
                client.sendChatMessage("You need Officer rank or higher to buy banners.");
                return;
            }

            GuildData guild = gm.getGuild(guildID);
            if (guild == null) {
                client.sendChatMessage("Guild not found.");
                return;
            }

            // Get banner cost from config (per docs)
            long bannerCost = medievalsim.config.ModConfig.Guilds.bannerCost;
            
            // Check player mob and level
            if (client.playerMob == null || client.playerMob.getLevel() == null) {
                client.sendChatMessage("Unable to process purchase.");
                return;
            }
            
            Level level = client.playerMob.getLevel();
            
            // Get coin item
            Item coinItem = ItemRegistry.getItem("coin");
            if (coinItem == null) {
                client.sendChatMessage("System error - coin item not found.");
                return;
            }
            
            // Check player has enough gold
            int playerGold = client.playerMob.getInv().main.getAmount(level, client.playerMob, coinItem, "buyBanner");
            if (playerGold < bannerCost) {
                client.sendChatMessage("Not enough gold! Need " + bannerCost + " gold.");
                return;
            }
            
            // Deduct gold from player
            int removed = client.playerMob.getInv().main.removeItems(level, client.playerMob, coinItem, (int)bannerCost, "buyBanner");
            if (removed < bannerCost) {
                // Failed to remove full amount - this shouldn't happen after the check above
                client.sendChatMessage("Failed to process payment.");
                return;
            }
            
            // Recompute current banner count and eligible max to avoid race conditions
            int currentCount = 0;
            for (necesse.level.maps.Level lvl : client.getServer().world.levelManager.getLoadedLevels()) {
                if (lvl == null) continue;
                for (necesse.entity.objectEntity.ObjectEntity oe : lvl.entityManager.objectEntities) {
                    if (!(oe instanceof medievalsim.guilds.objects.GuildBannerObjectEntity)) continue;
                    medievalsim.guilds.objects.GuildBannerObjectEntity b = (medievalsim.guilds.objects.GuildBannerObjectEntity) oe;
                    if (b.getGuildID() == guildID && b.getPlacedByAuth() == client.authentication) currentCount++;
                }
            }
            int ownedSettlements = 0;
            try {
                necesse.engine.world.worldData.SettlementsWorldData settlements = necesse.engine.world.worldData.SettlementsWorldData.getSettlementsData(client.getServer());
                if (settlements != null) {
                    long owned = settlements.streamSettlements().filter(e -> e.getOwnerAuth() == client.authentication).count();
                    ownedSettlements = (int) owned;
                }
            } catch (Exception ex) {
                ownedSettlements = 1;
            }
            int perSettlementLimit = medievalsim.config.ModConfig.Guilds.maxBannersPerSettlementPerGuild;
            int eligibleMax = Math.max(0, ownedSettlements * perSettlementLimit);

            if (currentCount >= eligibleMax) {
                client.sendChatMessage("Cannot purchase banner: maximum banners reached for your settlements.");
                return;
            }

            // Create the banner item - use the placeable object as item
            String bannerObjectID = "guildbanner";
            if (ObjectRegistry.getObject(bannerObjectID) == null) {
                // Refund gold
                InventoryItem refund = new InventoryItem(coinItem, (int)bannerCost);
                client.playerMob.getInv().main.addItem(level, client.playerMob, refund, "refund", null);
                client.sendChatMessage("Banner item not found in registry.");
                return;
            }
            
            // Create banner item and give to player
            InventoryItem bannerItem = new InventoryItem(bannerObjectID, 1);
            // Store guild ID in banner item so when placed it links to the guild
            medievalsim.guilds.objects.GuildBannerObjectItem.setGuildID(bannerItem, guildID);
            // Pre-fill symbol design from guild
            try {
                medievalsim.guilds.objects.GuildBannerObjectItem.setSymbolDesign(bannerItem, guild.getSymbolDesign());
            } catch (Exception e) {
                // ignore design failures
            }
            
            client.playerMob.getInv().main.addItem(level, client.playerMob, bannerItem, "buyBanner", null);
            int leftover = bannerItem.getAmount();
            if (leftover > 0) {
                // Inventory full - refund gold
                InventoryItem refund = new InventoryItem(coinItem, (int)bannerCost);
                client.playerMob.getInv().main.addItem(level, client.playerMob, refund, "refund", null);
                client.sendChatMessage("Inventory full! Cannot add banner.");
                return;
            }
            
            ModLogger.info("Player %d purchased banner for guild %d, cost=%d, immediatePlace=%b (current=%d, max=%d)", 
                client.authentication, guildID, bannerCost, immediatePlace, currentCount + 1, eligibleMax);
            
            if (immediatePlace) {
                client.sendChatMessage("Banner purchased! Place it in your settlement to activate.");
            } else {
                client.sendChatMessage("Banner added to your inventory!");
            }
                
        } catch (Exception e) {
            ModLogger.error("Error processing buy banner request: %s", e.getMessage());
            client.sendChatMessage("Failed to purchase banner.");
        }
    }
}
