package medievalsim.packets;

import java.util.Collections;

import medievalsim.grandexchange.application.GrandExchangeContext;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.packets.core.AbstractPayloadPacket;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;

/**
 * Client -> server acknowledgement that the active player has viewed their
 * history up to a specific timestamp. Used to keep history badges in sync
 * without forcing full snapshot refreshes.
 */
public class PacketGEHistoryAck extends AbstractPayloadPacket {
    private final long ackTimestamp;

    public PacketGEHistoryAck(long ackTimestamp) {
        this.ackTimestamp = Math.max(0L, ackTimestamp);
        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(this.ackTimestamp);
    }

    public PacketGEHistoryAck(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.ackTimestamp = Math.max(0L, reader.getNextLong());
    }

    public long getAckTimestamp() {
        return ackTimestamp;
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        ModLogger.warn("PacketGEHistoryAck received on client - this packet is client -> server only");
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        if (server == null || client == null) {
            return;
        }
        Level level = client.getLevel();
        GrandExchangeContext context = GrandExchangeContext.resolve(level);
        if (context == null) {
            ModLogger.warn("History ack dropped: unable to resolve GE context for auth=%d", client.authentication);
            return;
        }
        PlayerGEInventory inventory = context.getOrCreateInventory(client.authentication);
        if (inventory == null) {
            ModLogger.warn("History ack dropped: inventory missing for auth=%d", client.authentication);
            return;
        }
        long currentBaseline = inventory.getLastHistoryViewedTimestamp();
        if (ackTimestamp <= currentBaseline) {
            return;
        }
        inventory.markHistoryViewed(ackTimestamp);
        context.getLevelData().requestPersistenceSave();
        context.getLevelData().sendHistoryUpdate(level, client.authentication, Collections.emptyList());
        ModLogger.debug("History ack updated to %d for auth=%d", ackTimestamp, client.authentication);
    }
}
