package medievalsim.packets;

import medievalsim.grandexchange.GrandExchangeLevelData;
import medievalsim.grandexchange.MarketListing;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;

import java.util.List;

/**
 * Packet to sync Grand Exchange data from server to clients.
 * Sent when listings change (create, purchase, expire).
 *
 * For Phase 8, this is a simplified version that just logs on client side.
 * Phase 10 will implement full client-side sync with UI updates.
 */
public class PacketGESync extends Packet {

    private List<MarketListing> listings;

    /**
     * Receiving constructor (client-side).
     */
    public PacketGESync(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(new Packet(data));

        // Read listings count
        int count = reader.getNextInt();
        this.listings = new java.util.ArrayList<>(count);

        // Read each listing (simplified - just read fields directly)
        for (int i = 0; i < count; i++) {
            long listingID = reader.getNextLong();
            long sellerAuth = reader.getNextLong();
            String sellerName = reader.getNextString();
            String itemStringID = reader.getNextString();
            int quantity = reader.getNextInt();
            int pricePerItem = reader.getNextInt();
            long createdTime = reader.getNextLong();
            long expirationTime = reader.getNextLong();
            boolean active = reader.getNextBoolean();

            MarketListing listing = MarketListing.fromSyncData(listingID, sellerAuth, sellerName,
                itemStringID, quantity, pricePerItem, createdTime, expirationTime, active);
            this.listings.add(listing);
        }

        ModLogger.debug("Received GE sync with %d listings", count);
    }

    /**
     * Sending constructor (server-side).
     * @param geData Grand Exchange data to sync
     */
    public PacketGESync(GrandExchangeLevelData geData) {
        PacketWriter writer = new PacketWriter(this);

        // Get all active listings
        List<MarketListing> activeListings = geData.getAllListings();

        // Write listings count
        writer.putNextInt(activeListings.size());

        // Write each listing (simplified - just write fields directly)
        for (MarketListing listing : activeListings) {
            writer.putNextLong(listing.getListingID());
            writer.putNextLong(listing.getSellerAuth());
            writer.putNextString(listing.getSellerName());
            writer.putNextString(listing.getItemStringID());
            writer.putNextInt(listing.getQuantity());
            writer.putNextInt(listing.getPricePerItem());
            writer.putNextLong(listing.getCreatedTime());
            writer.putNextLong(listing.getExpirationTime());
            writer.putNextBoolean(listing.isActive());
        }

        ModLogger.debug("Sending GE sync with %d listings", activeListings.size());
    }
    
    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        // Not used - sync is server-to-client only
    }
    
    @Override
    public void processClient(NetworkPacket packet, Client client) {
        // Update client-side GE data
        // For now, just log - full client-side sync will be implemented in Phase 10
        ModLogger.debug("Client received GE sync with %d listings", this.listings.size());
        
        // TODO Phase 10: Update client-side GE container with new listings
        // This will require accessing the open container and refreshing the UI
    }
}

