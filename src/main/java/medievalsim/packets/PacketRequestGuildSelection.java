/*
 * PacketRequestGuildSelection - Client requests list of guilds they belong to
 * Part of Medieval Sim Mod guild management system.
 * Used when player is in multiple guilds and clicks "View Guild Info".
 */
package medievalsim.packets;

import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildManager;
import medievalsim.guilds.GuildRank;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Client->Server packet requesting list of guilds the player belongs to.
 * Server responds with PacketGuildSelectionResponse.
 */
public class PacketRequestGuildSelection extends Packet {

    public PacketRequestGuildSelection(byte[] data) {
        super(data);
        // No data needed - server uses client.authentication
    }

    public PacketRequestGuildSelection() {
        // No data to write
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            GuildManager gm = GuildManager.get(client.getServer().world);
            if (gm == null) {
                ModLogger.warn("GuildManager null when processing guild selection request");
                return;
            }

            long playerAuth = client.authentication;
            
            // Find all guilds this player belongs to
            List<GuildSelectionEntry> entries = new ArrayList<>();
            
            for (GuildData guild : gm.getAllGuilds()) {
                if (guild.isMember(playerAuth)) {
                    GuildRank rank = guild.getMemberRank(playerAuth);
                    int memberCount = guild.getMemberCount();
                    
                    entries.add(new GuildSelectionEntry(
                        guild.getGuildID(),
                        guild.getName(),
                        rank != null ? rank.level : 0,
                        memberCount
                    ));
                }
            }

            if (entries.isEmpty()) {
                ModLogger.debug("Player %s has no guild memberships", client.getName());
                return;
            }

            // Send response
            client.sendPacket(new PacketGuildSelectionResponse(entries));
            ModLogger.debug("Sent guild selection list with %d guilds to %s", 
                entries.size(), client.getName());
                
        } catch (Exception e) {
            ModLogger.error("Error processing guild selection request", e);
        }
    }

    /**
     * Lightweight guild entry for selection list.
     */
    public static class GuildSelectionEntry {
        public final int guildID;
        public final String guildName;
        public final int rankLevel;
        public final int memberCount;

        public GuildSelectionEntry(int guildID, String guildName, int rankLevel, int memberCount) {
            this.guildID = guildID;
            this.guildName = guildName;
            this.rankLevel = rankLevel;
            this.memberCount = memberCount;
        }
    }
}
