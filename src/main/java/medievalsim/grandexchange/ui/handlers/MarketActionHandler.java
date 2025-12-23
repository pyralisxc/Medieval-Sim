package medievalsim.grandexchange.ui.handlers;

import medievalsim.banking.domain.PlayerBank;
import medievalsim.grandexchange.application.GrandExchangeLedger;
import medievalsim.grandexchange.domain.GrandExchangeLevelData;
import medievalsim.grandexchange.domain.MarketSnapshot;
import medievalsim.grandexchange.net.GEFeedbackChannel;
import medievalsim.grandexchange.net.GEFeedbackLevel;
import medievalsim.util.ModLogger;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;

/**
 * Handles market browser operations for the Grand Exchange.
 * Extracted from GrandExchangeContainer to reduce its size and improve testability.
 * 
 * <p>Operations handled:</p>
 * <ul>
 *   <li>Buy from market - Purchase items from an active offer</li>
 *   <li>Refresh market - Rebuild market snapshot with current filters</li>
 *   <li>Apply snapshot - Update local state from server snapshot</li>
 * </ul>
 */
public class MarketActionHandler {

    private final ServerClient serverClient;
    private final long playerAuth;
    private final GrandExchangeLevelData geData;
    private final GrandExchangeLedger ledger;
    private final PlayerBank bank;
    private final ContainerSyncManager syncManager;

    // Market browser state
    private String marketFilter = "";
    private String marketCategory = "all";
    private int marketPage = 0;
    private int marketSort = 1; // 1=price↑, 2=price↓, 3=quantity↓, 4=time↑

    public MarketActionHandler(
            ServerClient serverClient,
            long playerAuth,
            GrandExchangeLevelData geData,
            GrandExchangeLedger ledger,
            PlayerBank bank,
            ContainerSyncManager syncManager) {
        this.serverClient = serverClient;
        this.playerAuth = playerAuth;
        this.geData = geData;
        this.ledger = ledger;
        this.bank = bank;
        this.syncManager = syncManager;
    }

    // ===== BUY FROM MARKET =====

    /**
     * Purchase items from an active market offer.
     * Deducts coins from buyer's bank, adds to seller's collection, moves items to buyer's collection.
     * 
     * @param offerID The ID of the offer to purchase from
     * @param requestedQuantity How many items to purchase
     * @param onComplete Callback to run after the purchase (typically refreshMarketListings)
     */
    public void handleBuyFromMarket(long offerID, int requestedQuantity, Runnable onComplete) {
        if (geData == null || serverClient == null) {
            ModLogger.warn("Market purchase attempted on client or without GE data");
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        Level level = serverClient.getLevel();
        String buyerName = serverClient.getName();
        boolean success = geData.processMarketPurchase(level, playerAuth, buyerName, offerID, requestedQuantity);
        
        if (!success) {
            ModLogger.warn("Market purchase failed for offer ID=%d (player auth=%d)", offerID, playerAuth);
            syncManager.sendFeedback(GEFeedbackChannel.MARKET, GEFeedbackLevel.ERROR,
                "Purchase failed. The offer may no longer be available.");
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        // Sync coin count after successful purchase
        syncManager.syncCoinCount();
        
        syncManager.sendFeedback(GEFeedbackChannel.MARKET, GEFeedbackLevel.INFO,
            "Purchase successful! Items added to collection.");
        syncManager.sendCollectionSync();
        
        if (onComplete != null) {
            onComplete.run();
        }
    }

    // ===== REFRESH MARKET LISTINGS =====

    /**
     * Refresh market listings by asking the ledger for a new snapshot.
     * Sends the snapshot to the client via packet.
     * 
     * @param applyCallback Callback to apply the snapshot to container state
     */
    public void refreshMarketListings(java.util.function.Consumer<MarketSnapshot> applyCallback) {
        if (ledger == null || serverClient == null) {
            return;
        }

        MarketSnapshot snapshot = ledger.buildMarketSnapshot(
            marketFilter,
            marketCategory,
            marketSort,
            marketPage
        );

        if (applyCallback != null) {
            applyCallback.accept(snapshot);
        }
        
        syncManager.sendMarketSnapshot(snapshot);
        
        ModLogger.debug("Market snapshot refreshed: page=%d totalResults=%d filter='%s' category='%s' sort=%d",
            marketPage, snapshot.getTotalResults(), marketFilter, marketCategory, marketSort);
    }

    // ===== FILTER METHODS =====

    /**
     * Set the market filter string and reset to page 0.
     */
    public void setFilter(String filter) {
        this.marketFilter = filter == null ? "" : filter;
        this.marketPage = 0;
        ModLogger.debug("Market filter set to: %s", this.marketFilter);
    }

    /**
     * Set the market category filter and reset to page 0.
     */
    public void setCategory(String category) {
        this.marketCategory = category == null ? "all" : category;
        this.marketPage = 0;
        ModLogger.debug("Market category set to: %s", this.marketCategory);
    }

    /**
     * Set the current market page.
     */
    public void setPage(int page) {
        this.marketPage = Math.max(0, page);
    }

    /**
     * Set the market sort mode and reset to page 0.
     */
    public void setSort(int sortMode) {
        this.marketSort = sortMode;
        this.marketPage = 0;
    }

    // ===== GETTERS =====

    public String getFilter() {
        return marketFilter;
    }

    public String getCategory() {
        return marketCategory;
    }

    public int getPage() {
        return marketPage;
    }

    public int getSort() {
        return marketSort;
    }
}
