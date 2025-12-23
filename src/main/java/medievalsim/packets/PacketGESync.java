package medievalsim.packets;

import java.util.ArrayList;
import java.util.List;

import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.grandexchange.domain.MarketInsightsSummary;
import medievalsim.grandexchange.domain.MarketSnapshot;
import medievalsim.grandexchange.model.snapshot.MarketListingSnapshot;
import medievalsim.grandexchange.model.snapshot.MarketTabSnapshot;
import medievalsim.grandexchange.ui.GrandExchangeContainer;
import medievalsim.grandexchange.ui.viewmodel.GrandExchangeViewModel;
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
    private final MarketTabSnapshot snapshot;

    public MarketTabSnapshot getSnapshot() {
        return snapshot;
    }

    /**
     * Client-side constructor.
     */
    public PacketGESync(byte[] data) {
        super(data);
        PacketReader reader = new PacketReader(this);
        this.viewerAuth = reader.getNextLong();
        int page = reader.getNextInt();
        int totalPages = reader.getNextInt();
        int totalResults = reader.getNextInt();
        int pageSize = reader.getNextInt();
        String filter = reader.getNextString();
        String category = reader.getNextString();
        int sortMode = reader.getNextInt();

        int listingCount = reader.getNextInt();
        List<MarketListingSnapshot> listings = new ArrayList<>(listingCount);
        MarketInsightsSummary insights;
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
            listings.add(new MarketListingSnapshot(
                offerId,
                itemStringID,
                quantityTotal,
                quantityRemaining,
                pricePerItem,
                sellerAuth,
                sellerName,
                expirationTime,
                createdTime,
                resolveState(stateOrdinal)
            ));
        }

        if (reader.getNextBoolean()) {
            long generatedAt = reader.getNextLong();
            long totalTrades = reader.getNextLong();
            long totalCoins = reader.getNextLong();
            int trackedCount = reader.getNextInt();
            List<MarketInsightsSummary.ItemInsight> topVolume = readInsightList(reader);
            List<MarketInsightsSummary.ItemInsight> topSpread = readInsightList(reader);
            insights = new MarketInsightsSummary(
                generatedAt,
                totalTrades,
                totalCoins,
                trackedCount,
                topVolume,
                topSpread
            );
        } else {
            insights = null;
        }

        this.snapshot = new MarketTabSnapshot(
            viewerAuth,
            page,
            totalPages,
            totalResults,
            pageSize,
            filter,
            category,
            sortMode,
            listings,
            insights
        );
    }

    /**
     * Server-side constructor from a snapshot.
     */
    public PacketGESync(long viewerAuth, MarketSnapshot snapshot) {
        this.viewerAuth = viewerAuth;
        List<MarketListingSnapshot> listings = new ArrayList<>(snapshot.getEntries().size());
        for (MarketSnapshot.Entry entry : snapshot.getEntries()) {
            listings.add(new MarketListingSnapshot(
                entry.getOfferId(),
                entry.getItemStringID(),
                entry.getQuantityTotal(),
                entry.getQuantityRemaining(),
                entry.getPricePerItem(),
                entry.getSellerAuth(),
                entry.getSellerName(),
                entry.getExpirationTime(),
                entry.getCreatedTime(),
                entry.getState()
            ));
        }
        this.snapshot = new MarketTabSnapshot(
            viewerAuth,
            snapshot.getPage(),
            snapshot.getTotalPages(),
            snapshot.getTotalResults(),
            snapshot.getPageSize(),
            snapshot.getFilter(),
            snapshot.getCategory(),
            snapshot.getSortMode(),
            listings,
            snapshot.getInsightsSummary()
        );

        PacketWriter writer = new PacketWriter(this);
        writer.putNextLong(viewerAuth);
        writer.putNextInt(this.snapshot.page());
        writer.putNextInt(this.snapshot.totalPages());
        writer.putNextInt(this.snapshot.totalResults());
        writer.putNextInt(this.snapshot.pageSize());
        writer.putNextString(this.snapshot.filter());
        writer.putNextString(this.snapshot.category());
        writer.putNextInt(this.snapshot.sortMode());

        writer.putNextInt(listings.size());
        for (MarketListingSnapshot listing : listings) {
            writer.putNextLong(listing.offerId());
            writer.putNextString(listing.itemStringID());
            writer.putNextInt(listing.quantityTotal());
            writer.putNextInt(listing.quantityRemaining());
            writer.putNextInt(listing.pricePerItem());
            writer.putNextLong(listing.sellerAuth());
            writer.putNextString(listing.sellerName() == null ? "Unknown" : listing.sellerName());
            writer.putNextLong(listing.expirationTime());
            writer.putNextLong(listing.createdTime());
            writer.putNextByteUnsigned(listing.state().ordinal());
        }

        if (this.snapshot.insightsSummary() != null) {
            writer.putNextBoolean(true);
            writer.putNextLong(this.snapshot.insightsSummary().getGeneratedAt());
            writer.putNextLong(this.snapshot.insightsSummary().getTotalTradesLogged());
            writer.putNextLong(this.snapshot.insightsSummary().getTotalCoinsTraded());
            writer.putNextInt(this.snapshot.insightsSummary().getTrackedItemCount());
            writeInsightList(writer, this.snapshot.insightsSummary().getTopVolumeItems());
            writeInsightList(writer, this.snapshot.insightsSummary().getWidestSpreadItems());
        } else {
            writer.putNextBoolean(false);
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
        try {
            GrandExchangeViewModel viewModel = geContainer.getViewModel();
            viewModel.applyMarketSnapshot(snapshot);
            geContainer.onMarketListingsUpdated();
            geContainer.onMarketInsightsUpdated();
        } catch (IllegalStateException ex) {
            ModLogger.error("Failed to apply market snapshot on client: %s", ex.getMessage());
        }
    }

    private static List<MarketInsightsSummary.ItemInsight> readInsightList(PacketReader reader) {
        int count = reader.getNextInt();
        List<MarketInsightsSummary.ItemInsight> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String itemID = reader.getNextString();
            int guidePrice = reader.getNextInt();
            int averagePrice = reader.getNextInt();
            int vwap = reader.getNextInt();
            int high24h = reader.getNextInt();
            int low24h = reader.getNextInt();
            int spread = reader.getNextInt();
            int tradeVolume = reader.getNextInt();
            int tradeCount = reader.getNextInt();
            long lastTradeTimestamp = reader.getNextLong();
            list.add(new MarketInsightsSummary.ItemInsight(
                itemID,
                guidePrice,
                averagePrice,
                vwap,
                high24h,
                low24h,
                spread,
                tradeVolume,
                tradeCount,
                lastTradeTimestamp
            ));
        }
        return list;
    }

    private static GEOffer.OfferState resolveState(int ordinal) {
        GEOffer.OfferState[] values = GEOffer.OfferState.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return GEOffer.OfferState.DRAFT;
        }
        return values[ordinal];
    }

    private static void writeInsightList(PacketWriter writer, List<MarketInsightsSummary.ItemInsight> list) {
        writer.putNextInt(list.size());
        for (MarketInsightsSummary.ItemInsight insight : list) {
            writer.putNextString(insight.getItemStringID());
            writer.putNextInt(insight.getGuidePrice());
            writer.putNextInt(insight.getAveragePrice());
            writer.putNextInt(insight.getVolumeWeightedPrice());
            writer.putNextInt(insight.getHigh24h());
            writer.putNextInt(insight.getLow24h());
            writer.putNextInt(insight.getSpread());
            writer.putNextInt(insight.getTradeVolume());
            writer.putNextInt(insight.getTradeCount());
            writer.putNextLong(insight.getLastTradeTimestamp());
        }
    }
}

