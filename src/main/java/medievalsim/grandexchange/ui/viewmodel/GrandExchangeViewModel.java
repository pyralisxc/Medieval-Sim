package medievalsim.grandexchange.ui.viewmodel;

import medievalsim.config.ModConfig;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.grandexchange.model.snapshot.BuyOrdersSnapshot;
import medievalsim.grandexchange.model.snapshot.CollectionPageSnapshot;
import medievalsim.grandexchange.model.snapshot.DefaultsConfigSnapshot;
import medievalsim.grandexchange.model.snapshot.HistoryDeltaPayload;
import medievalsim.grandexchange.model.snapshot.HistoryTabSnapshot;
import medievalsim.grandexchange.model.snapshot.MarketTabSnapshot;
import medievalsim.grandexchange.model.snapshot.SellOffersSnapshot;
import medievalsim.grandexchange.ui.GrandExchangeContainer;

/**
 * Lightweight view model that groups per-tab UI state so the container form
 * no longer carries dozens of ad-hoc fields. Phase 1 focuses on the sell tab
 * staging flow; additional tabs can migrate over time.
 */
public class GrandExchangeViewModel {
    private final GrandExchangeContainer container;
    private final long ownerAuth;
    private final SellTabState sellTabState;
    private final BuyTabState buyTabState;
    private final MarketTabState marketTabState;
    private final CollectionTabState collectionTabState;
    private final HistoryTabState historyTabState;
    private final DefaultsTabState defaultsTabState;
    private MarketTabSnapshot marketSnapshot;
    private BuyOrdersSnapshot buyOrdersSnapshot;
    private SellOffersSnapshot sellOffersSnapshot;
    private CollectionPageSnapshot collectionSnapshot;
    private HistoryTabSnapshot historySnapshot;
    private DefaultsConfigSnapshot defaultsSnapshot;
    private final FeedbackBus feedbackBus;

    public GrandExchangeViewModel(GrandExchangeContainer container) {
        this.container = container;
        this.ownerAuth = container.playerAuth;
        this.sellTabState = new SellTabState(
            container.getStagingInventorySlotIndex(),
            ModConfig.GrandExchange.autoClearSellStagingSlot);
        this.sellTabState.setAutoClearLocked(!container.canEditAutoClearPreference());
        this.buyTabState = new BuyTabState();
        this.marketTabState = new MarketTabState();
        PlayerGEInventory inventory = container.playerInventory;
        boolean autoSendToBank = inventory != null && inventory.isAutoSendToBank();
        boolean notifyPartial = inventory != null && inventory.isNotifyPartialSales();
        boolean playSound = inventory != null && inventory.isPlaySoundOnSale();
        this.collectionTabState = new CollectionTabState(
            container.collectionPageIndex,
            container.collectionPageSize,
            container.collectionTotalItems,
            container.collectionTotalPages,
            container.collectionDepositToBankPreferred,
            autoSendToBank,
            notifyPartial,
            playSound);
        this.historyTabState = new HistoryTabState();
        this.defaultsTabState = new DefaultsTabState(
            ModConfig.GrandExchange.geInventorySlots,
            ModConfig.GrandExchange.buyOrderSlots);
        this.defaultsTabState.setVisible(container.isWorldOwner());
        this.marketSnapshot = MarketTabSnapshot.empty(ownerAuth);
        this.buyOrdersSnapshot = BuyOrdersSnapshot.empty(ownerAuth);
        this.sellOffersSnapshot = SellOffersSnapshot.empty(ownerAuth, ModConfig.GrandExchange.geInventorySlots);
        this.collectionSnapshot = CollectionPageSnapshot.empty(ownerAuth);
        this.historySnapshot = HistoryTabSnapshot.empty(ownerAuth);
        this.defaultsSnapshot = null;
        this.feedbackBus = new FeedbackBus();
        this.historyTabState.applySnapshot(this.historySnapshot);
    }

    public GrandExchangeContainer getContainer() {
        return container;
    }

    public SellTabState getSellTabState() {
        return sellTabState;
    }

    public BuyTabState getBuyTabState() {
        return buyTabState;
    }

    public MarketTabState getMarketTabState() {
        return marketTabState;
    }

    public CollectionTabState getCollectionTabState() {
        return collectionTabState;
    }

    public HistoryTabState getHistoryTabState() {
        return historyTabState;
    }

    public DefaultsTabState getDefaultsTabState() {
        return defaultsTabState;
    }

    public MarketTabSnapshot getMarketSnapshot() {
        return marketSnapshot;
    }

    public BuyOrdersSnapshot getBuyOrdersSnapshot() {
        return buyOrdersSnapshot;
    }

    public SellOffersSnapshot getSellOffersSnapshot() {
        return sellOffersSnapshot;
    }

    public CollectionPageSnapshot getCollectionSnapshot() {
        return collectionSnapshot;
    }

    public HistoryTabSnapshot getHistorySnapshot() {
        return historySnapshot;
    }

    public DefaultsConfigSnapshot getDefaultsSnapshot() {
        return defaultsSnapshot;
    }

    public FeedbackBus getFeedbackBus() {
        return feedbackBus;
    }

    public void applyMarketSnapshot(MarketTabSnapshot snapshot) {
        this.marketSnapshot = snapshot == null ? MarketTabSnapshot.empty(ownerAuth) : snapshot;
    }

    public void applyBuyOrdersSnapshot(BuyOrdersSnapshot snapshot) {
        this.buyOrdersSnapshot = snapshot == null ? BuyOrdersSnapshot.empty(ownerAuth) : snapshot;
        this.buyTabState.updateCooldowns(
            this.buyOrdersSnapshot.creationCooldownSeconds(),
            this.buyOrdersSnapshot.toggleCooldownSeconds());
    }

    public void applySellOffersSnapshot(SellOffersSnapshot snapshot) {
        this.sellOffersSnapshot = snapshot == null
            ? SellOffersSnapshot.empty(ownerAuth, ModConfig.GrandExchange.geInventorySlots)
            : snapshot;
    }

    public void applyCollectionSnapshot(CollectionPageSnapshot snapshot) {
        this.collectionSnapshot = snapshot == null ? CollectionPageSnapshot.empty(ownerAuth) : snapshot;
        this.collectionTabState.updateMetadata(
            collectionSnapshot.pageIndex(),
            collectionSnapshot.pageSize(),
            collectionSnapshot.totalItems(),
            collectionSnapshot.totalPages(),
            collectionSnapshot.depositToBankPreferred(),
            collectionSnapshot.autoSendToBank(),
            collectionSnapshot.notifyPartialSales(),
            collectionSnapshot.playSoundOnSale());
    }

    public void applyHistorySnapshot(HistoryTabSnapshot snapshot) {
        this.historySnapshot = snapshot == null ? HistoryTabSnapshot.empty(ownerAuth) : snapshot;
        this.historyTabState.applySnapshot(this.historySnapshot);
    }

    public void applyHistoryDelta(HistoryDeltaPayload payload) {
        if (payload == null) {
            return;
        }
        this.historyTabState.applyDelta(payload);
    }
    
    public void applyHistoryBadge(int unseenCount, long latestTimestamp, long serverBaselineTimestamp) {
        this.historyTabState.synchronizeBadge(unseenCount, latestTimestamp, serverBaselineTimestamp);
    }

    public void applyDefaultsSnapshot(DefaultsConfigSnapshot snapshot) {
        this.defaultsSnapshot = snapshot;
        this.defaultsTabState.applySnapshot(snapshot);
    }
}
