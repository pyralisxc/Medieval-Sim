/*
 * PacketRequestBuyBanner - Client requests to open the buy banner modal
 * Part of Medieval Sim Mod guild management system.
 * Per docs: this opens a modal showing cost and placement options for guild banners.
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

/**
 * Client->Server packet requesting to open the Buy Banner modal for a specific guild.
 */
public class PacketRequestBuyBanner extends Packet {

    private int guildID;

    public PacketRequestBuyBanner(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
    }

    public PacketRequestBuyBanner(int guildID) {
        this.guildID = guildID;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
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
                ModLogger.debug("Player %d not in any guild", client.authentication);
                return;
            }

            if (!playerData.isInGuild(guildID)) {
                ModLogger.debug("Player %d is not a member of guild %d", client.authentication, guildID);
                return;
            }

            // Check permission (Officer+ can buy banners)
            GuildRank rank = playerData.getRankInGuild(guildID);
            if (rank == null || rank.level < GuildRank.OFFICER.level) {
                ModLogger.debug("Player %d lacks permission to buy banners for guild %d", 
                    client.authentication, guildID);
                return;
            }

            GuildData guild = gm.getGuild(guildID);
            if (guild == null) {
                ModLogger.debug("Guild %d not found", guildID);
                return;
            }

            // Build response with banner cost and guild info
            Packet content = new Packet();
            PacketWriter writer = new PacketWriter(content);
            writer.putNextInt(guildID);
            writer.putNextString(guild.getName());
            // Get banner cost from config (per docs)
            writer.putNextLong(medievalsim.config.ModConfig.Guilds.bannerCost);

            // Compute current banners owned by this player for this guild across loaded levels
            int currentCount = 0;
            for (necesse.level.maps.Level lvl : client.getServer().world.levelManager.getLoadedLevels()) {
                if (lvl == null) continue;
                for (necesse.entity.objectEntity.ObjectEntity oe : lvl.entityManager.objectEntities) {
                    if (!(oe instanceof medievalsim.guilds.objects.GuildBannerObjectEntity)) continue;
                    medievalsim.guilds.objects.GuildBannerObjectEntity b = (medievalsim.guilds.objects.GuildBannerObjectEntity) oe;
                    if (b.getGuildID() == guildID && b.getPlacedByAuth() == client.authentication) currentCount++;
                }
            }

            // Compute eligible max banners based on how many settlements this player owns (per-settlement cap)
            int ownedSettlements = 0;
            try {
                necesse.engine.world.worldData.SettlementsWorldData settlements = necesse.engine.world.worldData.SettlementsWorldData.getSettlementsData(client.getServer());
                if (settlements != null) {
                    long owned = settlements.streamSettlements().filter(e -> e.getOwnerAuth() == client.authentication).count();
                    ownedSettlements = (int) owned;
                }
            } catch (Exception ex) {
                // Fallback: if we cannot determine, default to 1
                ownedSettlements = 1;
            }

            int perSettlementLimit = medievalsim.config.ModConfig.Guilds.maxBannersPerSettlementPerGuild;
            int eligibleMax = Math.max(0, ownedSettlements * perSettlementLimit);

            writer.putNextInt(currentCount);  // Current banners owned by this player in this guild
            writer.putNextInt(eligibleMax); // Max banners allowed for this player (based on owned settlements)

            // Open the buy banner container/modal
            medievalsim.guilds.ui.BuyBannerContainer.openBuyBannerUI(client, content);
            ModLogger.debug("Opened buy banner modal for player %d, guild %d (current=%d, max=%d)", client.authentication, guildID, currentCount, eligibleMax);

        } catch (Exception e) {
            ModLogger.error("Error processing buy banner request: %s", e.getMessage());
        }
    }
}
