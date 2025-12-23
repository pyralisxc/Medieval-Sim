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
 * Server -> Client: Confirmation that crest was updated.
 * Triggers UI refresh with new crest design.
 */
public class PacketGuildSymbolUpdated extends Packet {
    private int backgroundShape;
    private int primaryColor;
    private int secondaryColor;
    private int emblemID;
    private int emblemColor;
    private int borderStyle;
    
    public PacketGuildSymbolUpdated() {}
    
    public PacketGuildSymbolUpdated(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.backgroundShape = reader.getNextInt();
        this.primaryColor = reader.getNextInt();
        this.secondaryColor = reader.getNextInt();
        this.emblemID = reader.getNextInt();
        this.emblemColor = reader.getNextInt();
        this.borderStyle = reader.getNextInt();
    }
    
    public PacketGuildSymbolUpdated(GuildSymbolDesign design) {
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
        // Server doesn't process this - it sends it
    }
    
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        GuildSymbolDesign design = new GuildSymbolDesign(
            backgroundShape, primaryColor, secondaryColor,
            emblemID, emblemColor, borderStyle
        );
        
        // Notify any open crest designer form that update was successful
        CrestDesignerForm.notifyUpdateSuccess(design);
    }
}
