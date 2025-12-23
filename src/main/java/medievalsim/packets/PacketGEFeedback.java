package medievalsim.packets;

import medievalsim.grandexchange.net.GEFeedbackChannel;
import medievalsim.grandexchange.net.GEFeedbackLevel;
import medievalsim.grandexchange.ui.GrandExchangeContainer;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.Container;

/**
 * Lightweight server -> client transport for tab-scoped feedback messages.
 * This lets the UI highlight authoritative server responses without waiting
 * for a full snapshot refresh.
 */
public class PacketGEFeedback extends Packet {
    private final long ownerAuth;
    private final GEFeedbackChannel channel;
    private final GEFeedbackLevel level;
    private final String message;
    private final long timestamp;

    public PacketGEFeedback(long ownerAuth,
                             GEFeedbackChannel channel,
                             GEFeedbackLevel level,
                             String message,
                             long timestamp) {
        this.ownerAuth = ownerAuth;
        this.channel = channel == null ? GEFeedbackChannel.MARKET : channel;
        this.level = level == null ? GEFeedbackLevel.INFO : level;
        this.message = message == null ? "" : message;
        this.timestamp = timestamp <= 0 ? System.currentTimeMillis() : timestamp;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(this.ownerAuth);
        writer.putNextByteUnsigned(this.channel.ordinal());
        writer.putNextByteUnsigned(this.level.ordinal());
        writer.putNextString(this.message);
        writer.putNextLong(this.timestamp);
    }

    public PacketGEFeedback(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.ownerAuth = reader.getNextLong();
        this.channel = GEFeedbackChannel.values()[reader.getNextByteUnsigned()];
        this.level = GEFeedbackLevel.values()[reader.getNextByteUnsigned()];
        this.message = reader.getNextString();
        this.timestamp = reader.getNextLong();
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        ModLogger.warn("PacketGEFeedback received on server; this packet should only flow server -> client");
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        Container container = client.getContainer();
        if (!(container instanceof GrandExchangeContainer geContainer)) {
            ModLogger.warn("Received GE feedback but no GE container is active");
            return;
        }
        if (geContainer.playerAuth != ownerAuth) {
            ModLogger.warn("Ignoring GE feedback intended for %d (current auth %d)", ownerAuth, geContainer.playerAuth);
            return;
        }
        geContainer.getViewModel()
            .getFeedbackBus()
            .post(channel, message, level, timestamp);
    }
}
