/*
 * PacketOpenManageGuilds - Client requests to open the Manage Guilds form
 * Part of Medieval Sim Mod guild management system.
 * Per docs: this form handles per-guild leave, buy banner, etc.
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

import java.util.List;

/**
 * Client->Server packet requesting to open the Manage Guilds UI.
 * Server responds by opening the ManageGuildsContainer with player's guild data.
 */
public class PacketOpenManageGuilds extends Packet {

    public PacketOpenManageGuilds(byte[] data) {
        super(data);
    }

    public PacketOpenManageGuilds() {
        // No parameters needed
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        try {
            GuildManager gm = GuildManager.get(server.world);
            if (gm == null) {
                ModLogger.warn("GuildManager null when processing manage guilds request");
                return;
            }

            // Get player's guild memberships
            PlayerGuildData playerData = gm.getPlayerData(client.authentication);
            if (playerData == null || !playerData.isInAnyGuild()) {
                // Player not in any guild - nothing to manage
                ModLogger.debug("Player %d requested manage guilds but has no memberships", client.authentication);
                return;
            }

            // Build response data for ManageGuildsContainer
            java.util.Map<Integer, GuildRank> memberships = playerData.getMemberships();
            int count = memberships.size();
            
            Packet content = new Packet();
            PacketWriter writer = new PacketWriter(content);
            
            // Write number of guilds
            writer.putNextInt(count);
            
            // Write each guild's info
            for (java.util.Map.Entry<Integer, GuildRank> entry : memberships.entrySet()) {
                int guildID = entry.getKey();
                GuildRank rank = entry.getValue();
                GuildData guild = gm.getGuild(guildID);
                if (guild != null) {
                    writer.putNextInt(guild.getGuildID());
                    writer.putNextString(guild.getName());
                    writer.putNextInt(rank.level);
                    writer.putNextLong(guild.getTreasury());
                    writer.putNextInt(guild.getMemberCount());
                    // Banner count for this guild in player's settlements
                    // TODO: Implement banner counting per settlement
                    writer.putNextInt(0);
                }
            }
            
            // Open the container
            medievalsim.guilds.ui.ManageGuildsContainer.openManageGuildsUI(client, content);
            ModLogger.debug("Opened manage guilds UI for player %d with %d guild(s)", 
                client.authentication, count);
                
        } catch (Exception e) {
            ModLogger.error("Error processing manage guilds request: %s", e.getMessage());
        }
    }
}
