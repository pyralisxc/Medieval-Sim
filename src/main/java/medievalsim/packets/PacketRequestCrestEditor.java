package medievalsim.packets;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

import medievalsim.guilds.GuildSymbolDesign;
import medievalsim.guilds.GuildData;
import medievalsim.guilds.GuildManager;
import medievalsim.guilds.GuildRank;
import medievalsim.guilds.PlayerGuildData;

/**
 * Client -> Server: Request to open crest editor.
 * Server validates permissions and sends back current crest design.
 */
public class PacketRequestCrestEditor extends Packet {
    
    public PacketRequestCrestEditor() {}
    
    public PacketRequestCrestEditor(byte[] data) {
        super(data);
    }
    
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        GuildManager gm = GuildManager.get(server.world);
        if (gm == null) return;
        
        PlayerGuildData playerData = gm.getPlayerData(client.authentication);
        if (playerData == null || !playerData.isInAnyGuild()) {
            return; // Player not in a guild
        }
        
        // Get player's primary guild
        int primaryGuildID = playerData.getPrimaryGuildID();
        GuildData guild = gm.getGuild(primaryGuildID);
        if (guild == null) return;
        
        GuildRank rank = guild.getMemberRank(client.authentication);
        if (rank == null || rank.ordinal() > GuildRank.OFFICER.ordinal()) {
            // Only officers and above can edit crest
            return;
        }
        
        // Send current crest design to client to open editor
        client.sendPacket(new PacketOpenCrestEditor(
            guild.getGuildID(),
            guild.getSymbolDesign()
        ));
    }
    
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        // Client doesn't process this
    }
}
