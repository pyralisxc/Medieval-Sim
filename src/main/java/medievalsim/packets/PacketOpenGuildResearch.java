/*
 * Packet to open Guild Research UI through the Mage NPC.
 * Client sends this when clicking "Guild Research" option.
 */
package medievalsim.packets;

import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildManager;
import medievalsim.guilds.GuildRank;
import medievalsim.guilds.research.GuildResearchContainer;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.gfx.GameColor;

/**
 * Request to open the Guild Research UI.
 * Sent from client when interacting with a Mage while in a guild.
 */
public class PacketOpenGuildResearch extends Packet {

    public PacketOpenGuildResearch(byte[] data) {
        super(data);
    }

    public PacketOpenGuildResearch() {
        // No additional data needed - server will look up player's guild
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        ModLogger.debug("Processing PacketOpenGuildResearch from %s", client.getName());

        // Get player's guild
        GuildManager manager = GuildManager.get(client.getServer().world);
        if (manager == null) {
            ModLogger.warn("GuildManager not found");
            return;
        }

        GuildData guild = manager.getPlayerPrimaryGuild(client.authentication);
        if (guild == null) {
            // Player not in a guild - send chat message
            client.sendChatMessage(GameColor.RED.getColorCode() + "You must be in a guild to access research.");
            ModLogger.debug("Player %s is not in a guild", client.getName());
            return;
        }

        // Get player's rank
        GuildRank rank = guild.getMemberRank(client.authentication);
        if (rank == null) {
            rank = GuildRank.RECRUIT;
        }

        // Open the research UI (pass guild for initial state)
        GuildResearchContainer.openResearchUI(client, guild.getGuildID(), rank, guild);
        ModLogger.info("Opened research UI for %s in guild %s", client.getName(), guild.getName());
    }
}
