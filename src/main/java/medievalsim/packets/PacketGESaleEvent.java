package medievalsim.packets;

import medievalsim.grandexchange.model.event.SellOfferSaleEvent;
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
 * Server -> client pulse notifying that a specific sell offer just fulfilled
 * part or all of its quantity. Keeps the sell tab responsive without having to
 * wait for the next inventory snapshot packet.
 */
public class PacketGESaleEvent extends AbstractPayloadPacket {
    private final long ownerAuth;
    private final SellOfferSaleEvent event;

    public PacketGESaleEvent(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.ownerAuth = reader.getNextLong();
        int slotIndex = reader.getNextInt();
        String itemStringID = reader.getNextString();
        int quantitySold = reader.getNextInt();
        int quantityRemaining = reader.getNextInt();
        int pricePerItem = reader.getNextInt();
        long timestamp = reader.getNextLong();
        this.event = new SellOfferSaleEvent(slotIndex, itemStringID, quantitySold, quantityRemaining, pricePerItem, timestamp);
    }

    public PacketGESaleEvent(long ownerAuth, SellOfferSaleEvent event) {
        this.ownerAuth = ownerAuth;
        this.event = event;

        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(ownerAuth);
        writer.putNextInt(event.slotIndex());
        writer.putNextString(event.itemStringID());
        writer.putNextInt(event.quantitySold());
        writer.putNextInt(event.quantityRemaining());
        writer.putNextInt(event.pricePerItem());
        writer.putNextLong(event.timestamp());
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        Container container = client.getContainer();
        if (!(container instanceof GrandExchangeContainer geContainer)) {
            ModLogger.warn("Received GE sale event but no GE container is active");
            return;
        }
        if (geContainer.playerAuth != ownerAuth) {
            ModLogger.warn("Sale event auth mismatch: expected %d but received %d", geContainer.playerAuth, ownerAuth);
            return;
        }
        geContainer.handleSaleEvent(event);
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        ModLogger.warn("PacketGESaleEvent.processServer called - server-to-client only!");
    }
}
