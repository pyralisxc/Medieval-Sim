package medievalsim.packets;

import medievalsim.grandexchange.domain.CollectionItem;
import medievalsim.packets.core.AbstractPayloadPacket;
import medievalsim.util.ModLogger;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Packet to sync collection box items from server to client.
 * Collection box can have unlimited items (list-based, not inventory slots).
 */
public class PacketGECollectionSync extends AbstractPayloadPacket {
    public final long ownerAuth;
    public final List<CollectionItemData> items;
    
    public static class CollectionItemData {
        public final String itemStringID;
        public final int quantity;
        public final long timestamp;
        public final String source;
        
        public CollectionItemData(String itemStringID, int quantity, long timestamp, String source) {
            this.itemStringID = itemStringID;
            this.quantity = quantity;
            this.timestamp = timestamp;
            this.source = source;
        }
    }
    
    /**
     * Receiving constructor (client-side).
     */
    public PacketGECollectionSync(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.ownerAuth = reader.getNextLong();
        
        int itemCount = reader.getNextShortUnsigned();
        this.items = new ArrayList<>(itemCount);
        
        for (int i = 0; i < itemCount; i++) {
            String itemStringID = reader.getNextString();
            int quantity = reader.getNextInt();
            long timestamp = reader.getNextLong();
            String source = reader.getNextString();
            items.add(new CollectionItemData(itemStringID, quantity, timestamp, source));
        }
    }
    
    /**
     * Sending constructor (server-side).
     */
    public PacketGECollectionSync(long ownerAuth, List<CollectionItem> collectionBox) {
        this.ownerAuth = ownerAuth;
        this.items = new ArrayList<>(collectionBox.size());
        
        for (CollectionItem item : collectionBox) {
            items.add(new CollectionItemData(
                item.getItemStringID(),
                item.getQuantity(),
                item.getTimestamp(),
                item.getSource()
            ));
        }
        
        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(ownerAuth);
        writer.putNextShortUnsigned(items.size());
        
        for (CollectionItemData item : items) {
            writer.putNextString(item.itemStringID);
            writer.putNextInt(item.quantity);
            writer.putNextLong(item.timestamp);
            writer.putNextString(item.source);
        }
    }
    
    @Override
    public void processClient(necesse.engine.network.NetworkPacket packet, Client client) {
        // Will be handled by GrandExchangeContainer on client side
        ModLogger.debug("Client: Received collection box sync for player auth=%d (%d items)",
            ownerAuth, items.size());
    }
    
    @Override
    public void processServer(necesse.engine.network.NetworkPacket packet, Server server, ServerClient client) {
        ModLogger.warn("PacketGECollectionSync.processServer called - server-to-client only!");
    }
}
