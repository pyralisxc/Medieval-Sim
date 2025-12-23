package medievalsim.packets;

import medievalsim.grandexchange.model.snapshot.DefaultsConfigSnapshot;
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
 * Server -> client packet carrying the Defaults tab configuration snapshot.
 */
public class PacketGEDefaultsConfig extends Packet {
    private final DefaultsConfigSnapshot snapshot;

    public PacketGEDefaultsConfig(DefaultsConfigSnapshot snapshot) {
        this.snapshot = snapshot;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(snapshot.ownerAuth());
        writer.putNextInt(snapshot.sellSlotMin());
        writer.putNextInt(snapshot.sellSlotMax());
        writer.putNextInt(snapshot.sellSlotConfigured());
        writer.putNextInt(snapshot.buySlotMin());
        writer.putNextInt(snapshot.buySlotMax());
        writer.putNextInt(snapshot.buySlotConfigured());
        writer.putNextBoolean(snapshot.autoClearEnabled());
        writer.putNextInt(snapshot.stagingSlotIndex());
    }

    public PacketGEDefaultsConfig(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        long ownerAuth = reader.getNextLong();
        int sellSlotMin = reader.getNextInt();
        int sellSlotMax = reader.getNextInt();
        int sellSlotConfigured = reader.getNextInt();
        int buySlotMin = reader.getNextInt();
        int buySlotMax = reader.getNextInt();
        int buySlotConfigured = reader.getNextInt();
        boolean autoClearEnabled = reader.getNextBoolean();
        int stagingSlotIndex = reader.getNextInt();
        this.snapshot = new DefaultsConfigSnapshot(
            ownerAuth,
            sellSlotMin,
            sellSlotMax,
            sellSlotConfigured,
            buySlotMin,
            buySlotMax,
            buySlotConfigured,
            autoClearEnabled,
            stagingSlotIndex
        );
    }

    public DefaultsConfigSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        ModLogger.warn("PacketGEDefaultsConfig received on server - this packet is server -> client only");
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        Container container = client.getContainer();
        if (!(container instanceof GrandExchangeContainer geContainer)) {
            ModLogger.warn("Received defaults config sync but player is not viewing the Grand Exchange");
            return;
        }
        geContainer.applyDefaultsConfig(snapshot);
    }
}
