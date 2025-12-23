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
 * Client -> Server: Request to update guild crest design.
 * Only officers and leaders can update the crest.
 */
public class PacketUpdateGuildSymbol extends Packet {
    private int backgroundShape;
    private int primaryColor;
    private int secondaryColor;
    private int emblemID;
    private int emblemColor;
    private int borderStyle;
    
    public PacketUpdateGuildSymbol() {}
    
    public PacketUpdateGuildSymbol(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.backgroundShape = reader.getNextInt();
        this.primaryColor = reader.getNextInt();
        this.secondaryColor = reader.getNextInt();
        this.emblemID = reader.getNextInt();
        this.emblemColor = reader.getNextInt();
        this.borderStyle = reader.getNextInt();
    }
    
    public PacketUpdateGuildSymbol(GuildSymbolDesign design) {
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(design.getBackgroundShape());
        writer.putNextInt(design.getPrimaryColor());
        writer.putNextInt(design.getSecondaryColor());
        writer.putNextInt(design.getEmblemID());
        writer.putNextInt(design.getEmblemColor());
        writer.putNextInt(design.getBorderStyle());
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
        
        // Update symbol design
        GuildSymbolDesign newDesign = new GuildSymbolDesign(
            backgroundShape, primaryColor, secondaryColor,
            emblemID, emblemColor, borderStyle
        );
        guild.updateSymbol(newDesign);
        
        // Send confirmation back to client
        client.sendPacket(new PacketGuildSymbolUpdated(newDesign));
        
        // TODO: Optionally broadcast to all guild members so their UIs update
    }
    
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        // Client doesn't process this - it's a server request
    }
}
