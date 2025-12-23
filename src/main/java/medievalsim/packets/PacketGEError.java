package medievalsim.packets;

import medievalsim.grandexchange.net.GEFeedbackChannel;
import medievalsim.grandexchange.net.GEFeedbackLevel;
import medievalsim.grandexchange.ui.GrandExchangeContainer;
import medievalsim.util.ModLogger;
import necesse.engine.localization.Localization;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.Container;

/**
 * Packet for sending Grand Exchange error notifications to the client.
 * Allows server to display user-friendly error messages when GE operations fail.
 */
public class PacketGEError extends Packet {
    
    private final long ownerAuth;
    private final GEFeedbackChannel channel;
    private final String errorKey;
    private final String[] parameters;
    
    /**
     * Create error packet with localization key and optional parameters.
     * Parameters will be substituted into the localized message.
     */
    public PacketGEError(long ownerAuth, GEFeedbackChannel channel, String errorKey, String... parameters) {
        this.ownerAuth = ownerAuth;
        this.channel = channel != null ? channel : GEFeedbackChannel.MARKET;
        this.errorKey = errorKey;
        this.parameters = parameters != null ? parameters : new String[0];
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(this.ownerAuth);
        writer.putNextByteUnsigned(this.channel.ordinal());
        writer.putNextString(this.errorKey);
        writer.putNextShortUnsigned(this.parameters.length);
        for (String param : this.parameters) {
            writer.putNextString(param);
        }
    }
    
    /**
     * Constructor for deserializing received packet data.
     */
    public PacketGEError(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.ownerAuth = reader.getNextLong();
        this.channel = GEFeedbackChannel.values()[reader.getNextByteUnsigned()];
        this.errorKey = reader.getNextString();
        int paramCount = reader.getNextShortUnsigned();
        this.parameters = new String[paramCount];
        for (int i = 0; i < paramCount; i++) {
            this.parameters[i] = reader.getNextString();
        }
    }
    
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        // Server should not receive error packets (client-bound only)
        ModLogger.warn("PacketGEError received on server; this packet should only flow server -> client");
    }
    
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        Container container = client.getContainer();
        if (!(container instanceof GrandExchangeContainer geContainer)) {
            ModLogger.warn("Received GE error but no GE container is active");
            return;
        }
        if (geContainer.playerAuth != ownerAuth) {
            ModLogger.warn("Ignoring GE error intended for %d (current auth %d)", ownerAuth, geContainer.playerAuth);
            return;
        }
        
        // Translate error message
        String translatedError = Localization.translate("grandexchange.error", errorKey);
        
        // Replace parameters if present
        for (int i = 0; i < parameters.length; i++) {
            translatedError = translatedError.replace("<param" + i + ">", parameters[i]);
        }
        
        // Post error to feedback bus
        geContainer.getViewModel()
            .getFeedbackBus()
            .post(channel, translatedError, GEFeedbackLevel.ERROR, System.currentTimeMillis());
    }
}
