package medievalsim.packets;

import java.util.ArrayList;
import java.util.List;

import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.grandexchange.domain.MarketSnapshot;
import medievalsim.grandexchange.ui.GrandExchangeContainer;
import medievalsim.util.ModLogger;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.Container;

/**
 * Server -> client packet carrying the paginated Grand Exchange market snapshot.
 */
public class PacketGESync extends medievalsim.packets.core.AbstractPayloadPacket {

    public final long viewerAuth;
    public final int page;
    public final int totalPages;
    public final int totalResults;
    public final int pageSize;
    public final String filter;
    public final String category;
    public final int sortMode;
    public final List<EntryPayload> entries;

    /**
     * Client-side constructor.
     */
    public PacketGESync(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.viewerAuth = reader.getNextLong();
        this.page = reader.getNextInt();
        this.totalPages = reader.getNextInt();
        this.totalResults = reader.getNextInt();
        this.pageSize = reader.getNextInt();
        this.filter = reader.getNextString();
        this.category = reader.getNextString();
        this.sortMode = reader.getNextInt();

        int listingCount = reader.getNextInt();
        this.entries = new ArrayList<>(listingCount);
        for (int i = 0; i < listingCount; i++) {
            long offerId = reader.getNextLong();
            String itemStringID = reader.getNextString();
            int quantityTotal = reader.getNextInt();
            int quantityRemaining = reader.getNextInt();
            int pricePerItem = reader.getNextInt();
            long sellerAuth = reader.getNextLong();
            String sellerName = reader.getNextString();
            long expirationTime = reader.getNextLong();
            long createdTime = reader.getNextLong();
            int stateOrdinal = reader.getNextByteUnsigned();
            entries.add(new EntryPayload(offerId, itemStringID, quantityTotal, quantityRemaining,
                pricePerItem, sellerAuth, sellerName, expirationTime, createdTime, stateOrdinal));
        }
    }

    /**
     * Server-side constructor from a snapshot.
     */
    public PacketGESync(long viewerAuth, MarketSnapshot snapshot) {
        this.viewerAuth = viewerAuth;
        this.page = snapshot.getPage();
        this.totalPages = snapshot.getTotalPages();
        this.totalResults = snapshot.getTotalResults();
        this.pageSize = snapshot.getPageSize();
        this.filter = snapshot.getFilter();
        this.category = snapshot.getCategory();
        this.sortMode = snapshot.getSortMode();
        this.entries = new ArrayList<>();

        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(viewerAuth);
        writer.putNextInt(page);
        writer.putNextInt(totalPages);
        writer.putNextInt(totalResults);
        writer.putNextInt(pageSize);
        writer.putNextString(filter);
        writer.putNextString(category);
        writer.putNextInt(sortMode);

        List<MarketSnapshot.Entry> snapshotEntries = snapshot.getEntries();
        writer.putNextInt(snapshotEntries.size());
        for (MarketSnapshot.Entry entry : snapshotEntries) {
            writer.putNextLong(entry.getOfferId());
            writer.putNextString(entry.getItemStringID());
            writer.putNextInt(entry.getQuantityTotal());
            writer.putNextInt(entry.getQuantityRemaining());
            writer.putNextInt(entry.getPricePerItem());
            writer.putNextLong(entry.getSellerAuth());
            writer.putNextString(entry.getSellerName() == null ? "Unknown" : entry.getSellerName());
            writer.putNextLong(entry.getExpirationTime());
            writer.putNextLong(entry.getCreatedTime());
            writer.putNextByteUnsigned(entry.getState().ordinal());

            entries.add(new EntryPayload(
                entry.getOfferId(),
                entry.getItemStringID(),
                entry.getQuantityTotal(),
                entry.getQuantityRemaining(),
                entry.getPricePerItem(),
                entry.getSellerAuth(),
                entry.getSellerName(),
                entry.getExpirationTime(),
                entry.getCreatedTime(),
                entry.getState().ordinal()
            ));
        }
    }

    @Override
    public void processServer(NetworkPacket packet, Server server, ServerClient client) {
        ModLogger.warn("PacketGESync.processServer called - this packet is server -> client only");
    }

    @Override
    public void processClient(NetworkPacket packet, Client client) {
        Container activeContainer = client.getContainer();
        if (!(activeContainer instanceof GrandExchangeContainer geContainer)) {
            ModLogger.warn("Received GE sync but container was not GrandExchange");
            return;
        }

        if (geContainer.playerAuth != viewerAuth) {
            ModLogger.warn("GE sync auth mismatch: container=%d packet=%d", geContainer.playerAuth, viewerAuth);
            return;
        }

        geContainer.marketPage = page;
        geContainer.marketTotalPages = totalPages;
        geContainer.marketTotalResults = totalResults;
        geContainer.marketPageSize = pageSize;
        geContainer.marketFilter = filter;
        geContainer.marketCategory = category;
        geContainer.marketSort = sortMode;

        geContainer.marketListings.clear();
        GEOffer.OfferState[] states = GEOffer.OfferState.values();
        for (EntryPayload payload : entries) {
            GEOffer.OfferState state = (payload.stateOrdinal >= 0 && payload.stateOrdinal < states.length)
                ? states[payload.stateOrdinal]
                : GEOffer.OfferState.DRAFT;

            geContainer.marketListings.add(new GrandExchangeContainer.MarketListingView(
                payload.offerId,
                payload.itemStringID,
                payload.quantityTotal,
                payload.quantityRemaining,
                payload.pricePerItem,
                payload.sellerAuth,
                payload.sellerName,
                payload.expirationTime,
                payload.createdTime,
                state
            ));
        }

        geContainer.onMarketListingsUpdated();
    }

    public static final class EntryPayload {
        public final long offerId;
        public final String itemStringID;
        public final int quantityTotal;
        public final int quantityRemaining;
        public final int pricePerItem;
        public final long sellerAuth;
        public final String sellerName;
        public final long expirationTime;
        public final long createdTime;
        public final int stateOrdinal;

        public EntryPayload(long offerId, String itemStringID, int quantityTotal, int quantityRemaining,
                             int pricePerItem, long sellerAuth, String sellerName,
                             long expirationTime, long createdTime, int stateOrdinal) {
            this.offerId = offerId;
            this.itemStringID = itemStringID;
            this.quantityTotal = quantityTotal;
            this.quantityRemaining = quantityRemaining;
            this.pricePerItem = pricePerItem;
            this.sellerAuth = sellerAuth;
            this.sellerName = sellerName;
            this.expirationTime = expirationTime;
            this.createdTime = createdTime;
            this.stateOrdinal = stateOrdinal;
        }
    }
}

