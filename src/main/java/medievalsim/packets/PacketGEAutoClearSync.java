package medievalsim.packets;

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
 * Server -> client packet that keeps the sell-tab auto-clear preference in sync.
 */
public class PacketGEAutoClearSync extends Packet {
    private final boolean autoClearEnabled;

    public PacketGEAutoClearSync(boolean autoClearEnabled) {
        this.autoClearEnabled = autoClearEnabled;
        PacketWriter writer = new PacketWriter(this);
        writer.putNextBoolean(autoClearEnabled);
    }

    public PacketGEAutoClearSync(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.autoClearEnabled = reader.getNextBoolean();
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        ModLogger.warn("PacketGEAutoClearSync received on server - this packet is server -> client only");
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        Container container = client.getContainer();
        if (!(container instanceof GrandExchangeContainer geContainer)) {
            ModLogger.warn("Received auto-clear sync but player is not viewing the Grand Exchange");
            return;
        }
        geContainer.applyAutoClearSync(autoClearEnabled);
    }
}
