package medievalsim.packets;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

import medievalsim.guilds.GuildSymbolDesign;
import medievalsim.guilds.ui.CrestDesignerForm;

/**
 * Server -> Client: Open the crest editor with current design.
 */
public class PacketOpenCrestEditor extends Packet {
    private int guildID;
    private int backgroundShape;
    private int primaryColor;
    private int secondaryColor;
    private int emblemID;
    private int emblemColor;
    private int borderStyle;
    
    public PacketOpenCrestEditor() {}
    
    public PacketOpenCrestEditor(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.guildID = reader.getNextInt();
        this.backgroundShape = reader.getNextInt();
        this.primaryColor = reader.getNextInt();
        this.secondaryColor = reader.getNextInt();
        this.emblemID = reader.getNextInt();
        this.emblemColor = reader.getNextInt();
        this.borderStyle = reader.getNextInt();
    }
    
    public PacketOpenCrestEditor(int guildID, GuildSymbolDesign crest) {
        PacketWriter writer = new PacketWriter(this);
        writer.putNextInt(guildID);
        writer.putNextInt(crest.getBackgroundShape());
        writer.putNextInt(crest.getPrimaryColor());
        writer.putNextInt(crest.getSecondaryColor());
        writer.putNextInt(crest.getEmblemID());
        writer.putNextInt(crest.getEmblemColor());
        writer.putNextInt(crest.getBorderStyle());
    }
    
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        // Server doesn't process this
    }
    
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        GuildSymbolDesign design = new GuildSymbolDesign(
            backgroundShape, primaryColor, secondaryColor,
            emblemID, emblemColor, borderStyle
        );
        
        CrestDesignerForm.showDesigner(client, guildID, design);
    }
}
