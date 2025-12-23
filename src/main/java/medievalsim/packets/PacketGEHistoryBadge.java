package medievalsim.packets;

import medievalsim.grandexchange.ui.GrandExchangeContainer;
import medievalsim.packets.core.AbstractPayloadPacket;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.Container;

/**
 * Lightweight server -> client packet that keeps the history badge state in sync.
 */
public class PacketGEHistoryBadge extends AbstractPayloadPacket {
    private final long ownerAuth;
    private final int unseenCount;
    private final long latestTimestamp;
    private final long serverBaselineTimestamp;

    public PacketGEHistoryBadge(long ownerAuth, int unseenCount, long latestTimestamp, long serverBaselineTimestamp) {
        this.ownerAuth = ownerAuth;
        this.unseenCount = Math.max(0, unseenCount);
        this.latestTimestamp = Math.max(0L, latestTimestamp);
        this.serverBaselineTimestamp = Math.max(0L, serverBaselineTimestamp);
        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(this.ownerAuth);
        writer.putNextInt(this.unseenCount);
        writer.putNextLong(this.latestTimestamp);
        writer.putNextLong(this.serverBaselineTimestamp);
    }

    public PacketGEHistoryBadge(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.ownerAuth = reader.getNextLong();
        this.unseenCount = Math.max(0, reader.getNextInt());
        this.latestTimestamp = Math.max(0L, reader.getNextLong());
        this.serverBaselineTimestamp = Math.max(0L, reader.getNextLong());
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        ModLogger.warn("PacketGEHistoryBadge received on server - this packet is server -> client only");
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        if (client == null) {
            return;
        }
        Container container = client.getContainer();
        if (!(container instanceof GrandExchangeContainer geContainer)) {
            ModLogger.warn("History badge packet received but no Grand Exchange container is active");
            return;
        }
        geContainer.getViewModel().applyHistoryBadge(unseenCount, latestTimestamp, serverBaselineTimestamp);
        geContainer.onHistoryBadgeUpdated();
    }
}
