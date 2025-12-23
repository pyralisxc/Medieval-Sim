package medievalsim.packets;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

import medievalsim.guilds.GuildManager;
import medievalsim.guilds.PlayerGuildData;
import medievalsim.guilds.bank.GuildBankContainer;

/**
 * Client -> Server: Request to open the guild bank.
 * Server validates permissions and opens the bank container.
 */
public class PacketOpenGuildBank extends Packet {
    
    private int tabIndex;
    
    public PacketOpenGuildBank() {
        this.tabIndex = 0;
    }
    
    public PacketOpenGuildBank(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.tabIndex = reader.getNextInt();
    }
    
    public PacketOpenGuildBank(int tabIndex) {
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(tabIndex);
        this.tabIndex = tabIndex;
    }
    
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        GuildManager gm = GuildManager.get(server.world);
        if (gm == null) return;
        
        PlayerGuildData playerData = gm.getPlayerData(client.authentication);
        if (playerData == null || !playerData.isInAnyGuild()) {
            return; // Player not in a guild
        }
        
        // Use player's primary guild
        int guildID = playerData.getPrimaryGuildID();
        
        // GuildBankContainer handles permission checks internally
        GuildBankContainer.openGuildBank(client, guildID, tabIndex);
    }
    
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        // Client doesn't process this - it's a server request
    }
}
