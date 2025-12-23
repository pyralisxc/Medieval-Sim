package medievalsim.grandexchange.ui;

import medievalsim.grandexchange.application.GrandExchangeContext;
import medievalsim.grandexchange.application.GrandExchangeLedger;
import medievalsim.grandexchange.application.workflow.BuyOrderWorkflow;
import medievalsim.grandexchange.application.workflow.SellOfferWorkflow;
import medievalsim.grandexchange.domain.GrandExchangeLevelData;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.grandexchange.domain.MarketSnapshot;
import medievalsim.grandexchange.domain.MarketInsightsSummary;
import medievalsim.grandexchange.model.event.SellOfferSaleEvent;
import medievalsim.grandexchange.model.snapshot.DefaultsConfigSnapshot;
import medievalsim.grandexchange.net.SellActionResultMessage;
import medievalsim.grandexchange.net.SellActionType;
import medievalsim.grandexchange.ui.handlers.BuyOrderActionHandler;
import medievalsim.grandexchange.ui.handlers.CollectionActionHandler;
import medievalsim.grandexchange.ui.handlers.ContainerSyncManager;
import medievalsim.grandexchange.ui.handlers.DefaultsActionHandler;
import medievalsim.grandexchange.ui.handlers.MarketActionHandler;
import medievalsim.grandexchange.ui.handlers.SellOfferActionHandler;
import medievalsim.grandexchange.ui.viewmodel.GrandExchangeViewModel;
import medievalsim.grandexchange.ui.viewmodel.HistoryTabState;
import medievalsim.inventory.customaction.LongIntCustomAction;
import medievalsim.banking.domain.PlayerBank;
import medievalsim.banking.service.BankingService;
import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.Settings;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.network.NetworkClient;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.Container;
import necesse.inventory.container.customAction.EmptyCustomAction;
import necesse.inventory.container.customAction.IntCustomAction;
import necesse.inventory.container.customAction.PointCustomAction;
import necesse.inventory.container.customAction.StringCustomAction;
import necesse.inventory.container.customAction.BooleanCustomAction;
import necesse.inventory.container.customAction.LongCustomAction;
import necesse.inventory.container.customAction.LongBooleanCustomAction;
import necesse.inventory.container.slots.ContainerSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Grand Exchange Container - 4-Tab System (Refactored)
 *
 * Architecture:
 * - Tab 0: Market Browser (buy from active offers)
 * - Tab 1: Buy Orders (3 slots with bank coin escrow)
 * - Tab 2: Sell Offers (10 inventory slots)
 * - Tab 3: Settings & Collection (unlimited collection box)
 *
 * Handler Delegation:
 * - ContainerSyncManager: All packet sync operations
 * - SellOfferActionHandler: Sell offer create/enable/disable/cancel
 * - BuyOrderActionHandler: Buy order enable/disable/cancel
 * - MarketActionHandler: Market browser and purchase operations
 * - CollectionActionHandler: Collection box operations
 * - DefaultsActionHandler: Admin configuration operations
 */
public class GrandExchangeContainer extends Container {

    // ===== CORE DATA =====
    public final GrandExchangeLevelData geData;
    public final long playerAuth;
    private final boolean serverContainer;
    private GrandExchangeViewModel viewModel;
    public PlayerGEInventory playerInventory;
    private final GrandExchangeLedger ledger;

    // ===== HANDLERS (Server only) =====
    private ContainerSyncManager syncManager;
    private SellOfferActionHandler sellHandler;
    private BuyOrderActionHandler buyHandler;
    private MarketActionHandler marketHandler;
    private CollectionActionHandler collectionHandler;
    private DefaultsActionHandler defaultsHandler;

    // ===== BANKING INTEGRATION =====
    private final BankingService bankingService;
    private final PlayerBank bank;
    public long clientCoinCount = 0;
    public LongCustomAction syncCoinCount;
    private final boolean isWorldOwner;

    private Runnable coinCountUpdateCallback;
    private Runnable collectionUpdateCallback;
    private Runnable defaultsUpdateCallback;
    private boolean autoClearEnabled;

    // ===== TAB SYSTEM =====
    public int activeTab = 0;

    // ===== TAB 0: MARKET BROWSER STATE =====
    public List<MarketListingView> marketListings = new ArrayList<>();
    public String marketFilter = "";
    public String marketCategory = "all";
    public int marketPage = 0;
    public int marketSort = 1;
    public int marketTotalPages = 1;
    public int marketTotalResults = 0;
    public int marketPageSize = ModConfig.GrandExchange.maxListingsPerPage;
    public MarketInsightsSummary marketInsightsSummary = null;

    // ===== TAB 2: SELL OFFERS =====
    public int GE_SELL_SLOTS_START = -1;
    public int GE_SELL_SLOTS_END = -1;
    public int GE_STAGING_INVENTORY_SLOT_INDEX = -1;

    // ===== COLLECTION STATE =====
    public int collectionPageIndex = 0;
    public int collectionTotalPages = 1;
    public int collectionTotalItems = 0;
    public int collectionPageSize = ModConfig.GrandExchange.getCollectionPageSize();
    public boolean collectionDepositToBankPreferred = ModConfig.GrandExchange.getDefaultCollectionDepositPreference();
    public int[] collectionPageGlobalIndices = new int[0];

    // ===== CUSTOM ACTIONS =====
    public LongIntCustomAction buyFromMarket;
    public StringCustomAction setMarketFilter;
    public StringCustomAction setMarketCategory;
    public IntCustomAction setMarketPage;
    public IntCustomAction setMarketSort;
    public IntCustomAction createBuyOrder;
    public IntCustomAction enableBuyOrder;
    public IntCustomAction disableBuyOrder;
    public IntCustomAction cancelBuyOrder;
    public PointCustomAction createSellOffer;
    public IntCustomAction enableSellOffer;
    public IntCustomAction disableSellOffer;
    public IntCustomAction cancelSellOffer;
    public IntCustomAction collectItem;
    public EmptyCustomAction collectAllToBank;
    public BooleanCustomAction toggleAutoBank;
    public BooleanCustomAction toggleNotifyPartial;
    public BooleanCustomAction togglePlaySound;
    public IntCustomAction updateSellSlotConfig;
    public IntCustomAction updateBuySlotConfig;
    public BooleanCustomAction updateAutoClearPreference;
    public IntCustomAction setCollectionPage;
    public BooleanCustomAction toggleCollectionDepositPreference;
    public LongBooleanCustomAction collectSelectedEntries;
    public IntCustomAction setActiveTab;

    // ===== UI REFRESH CALLBACKS =====
    private Runnable buyOrdersUpdateCallback = null;
    private Runnable sellOffersUpdateCallback = null;
    private Runnable marketListingsUpdateCallback = null;
    private Runnable marketInsightsUpdateCallback = null;
    private Consumer<SellActionResultMessage> sellActionResultCallback = null;
    private Runnable historyUpdateCallback = null;
    private Runnable historyIndicatorCallback = null;

    // ===== CALLBACK SETTERS =====

    public void setBuyOrdersUpdateCallback(Runnable callback) {
        this.buyOrdersUpdateCallback = callback;
    }

    public void setSellOffersUpdateCallback(Runnable callback) {
        this.sellOffersUpdateCallback = callback;
    }

    public void setMarketListingsUpdateCallback(Runnable callback) {
        this.marketListingsUpdateCallback = callback;
    }

    public void setMarketInsightsUpdateCallback(Runnable callback) {
        this.marketInsightsUpdateCallback = callback;
    }

    public void setSellActionResultCallback(Consumer<SellActionResultMessage> callback) {
        this.sellActionResultCallback = callback;
    }

    public void setHistoryUpdateCallback(Runnable callback) {
        this.historyUpdateCallback = callback;
    }

    public void setHistoryIndicatorCallback(Runnable callback) {
        this.historyIndicatorCallback = callback;
    }

    public void setCoinCountUpdateCallback(Runnable callback) {
        this.coinCountUpdateCallback = callback;
    }

    public void setCollectionUpdateCallback(Runnable callback) {
        this.collectionUpdateCallback = callback;
    }

    public void setDefaultsUpdateCallback(Runnable callback) {
        this.defaultsUpdateCallback = callback;
    }

    // ===== ACCESSORS =====

    public boolean isWorldOwner() {
        return isWorldOwner;
    }

    public boolean canEditAutoClearPreference() {
        return isWorldOwner();
    }

    private boolean canAccessDefaultsTab() {
        return isWorldOwner();
    }

    public int getStagingInventorySlotIndex() {
        return GE_STAGING_INVENTORY_SLOT_INDEX >= 0 ? GE_STAGING_INVENTORY_SLOT_INDEX : 0;
    }

    public boolean isAutoClearEnabled() {
        return autoClearEnabled;
    }

    public boolean isServerContainer() {
        return serverContainer;
    }

    public GrandExchangeViewModel getViewModel() {
        if (serverContainer) {
            throw new IllegalStateException("GrandExchangeViewModel is only available on client containers");
        }
        if (viewModel == null) {
            viewModel = new GrandExchangeViewModel(this);
        }
        return viewModel;
    }

    // ===== UPDATE CALLBACKS =====

    public void onBuyOrdersUpdated() {
        ModLogger.info("[CALLBACK TRACE] onBuyOrdersUpdated called, callback is %s",
            buyOrdersUpdateCallback != null ? "registered" : "NULL");
        if (buyOrdersUpdateCallback != null) {
            buyOrdersUpdateCallback.run();
        }
    }

    public void onSellOffersUpdated() {
        if (sellOffersUpdateCallback != null) {
            sellOffersUpdateCallback.run();
        }
    }

    public void handleSaleEvent(SellOfferSaleEvent event) {
        if (event == null || serverContainer) {
            return;
        }
        GrandExchangeViewModel model = getViewModel();
        model.getSellTabState().registerSalePulse(event.slotIndex(), event);
        if (sellOffersUpdateCallback != null) {
            sellOffersUpdateCallback.run();
        }
    }

    public void onMarketListingsUpdated() {
        if (marketListingsUpdateCallback != null) {
            marketListingsUpdateCallback.run();
        }
    }

    public void onMarketInsightsUpdated() {
        if (marketInsightsUpdateCallback != null) {
            marketInsightsUpdateCallback.run();
        }
    }

    public void onDefaultsConfigUpdated() {
        if (defaultsUpdateCallback != null) {
            defaultsUpdateCallback.run();
        }
    }

    public void onCollectionBoxUpdated() {
        if (collectionUpdateCallback != null) {
            collectionUpdateCallback.run();
        }
    }

    public void onHistoryUpdated() {
        handleHistoryUpdate(true);
    }

    public void onHistoryBadgeUpdated() {
        handleHistoryUpdate(false);
    }

    private void handleHistoryUpdate(boolean autoAcknowledge) {
        if (!serverContainer && autoAcknowledge && activeTab == 4) {
            HistoryTabState state = getViewModel().getHistoryTabState();
            state.markEntriesSeen();
        }
        if (historyIndicatorCallback != null) {
            historyIndicatorCallback.run();
        }
        if (autoAcknowledge && historyUpdateCallback != null) {
            historyUpdateCallback.run();
        }
    }

    private void notifyCoinCountUpdated() {
        if (coinCountUpdateCallback != null) {
            coinCountUpdateCallback.run();
        }
    }

    // ===== CONSTRUCTOR =====

    public GrandExchangeContainer(NetworkClient client, int uniqueSeed, Packet content) {
        super(client, uniqueSeed);
        this.serverContainer = client.isServer();

        PacketReader reader = new PacketReader(content);
        this.playerAuth = reader.getNextLong();
        this.clientCoinCount = reader.getNextLong();
        boolean ownerFlagFromPacket = reader.getNextBoolean();
        int sellSlotCountFromServer = reader.getNextInt();
        int buySlotCountFromServer = reader.getNextInt();
        boolean autoClearFromServer = reader.getNextBoolean();
        
        ModLogger.info("[BANK SYNC] Container constructor read initial coin count: %d (isServer=%s)",
            clientCoinCount, client.isServer());
        
        boolean resolvedAutoClear = client.isServer()
            ? ModConfig.GrandExchange.autoClearSellStagingSlot
            : autoClearFromServer;
        if (!client.isServer()) {
            ModConfig.GrandExchange.setGeInventorySlots(sellSlotCountFromServer);
            ModConfig.GrandExchange.setBuyOrderSlots(buySlotCountFromServer);
            ModConfig.GrandExchange.setAutoClearSellStagingSlot(resolvedAutoClear);
        }
        this.autoClearEnabled = resolvedAutoClear;

        // Initialize server-side data and handlers
        if (client.isServer()) {
            GrandExchangeContext context = GrandExchangeContext.resolve(client.getServerClient().getLevel());
            if (context == null) {
                ModLogger.error("Failed to get GE data for player auth=%d", playerAuth);
                throw new IllegalStateException("GE data not found");
            }

            this.geData = context.getLevelData();
            this.playerInventory = context.getOrCreateInventory(playerAuth);
            this.ledger = context.getLedger();
            this.bankingService = new BankingService(client.getServerClient());
            this.bank = bankingService.getBank();
            
            if (this.bank == null) {
                ModLogger.error("Failed to get bank for player auth=%d", playerAuth);
                throw new IllegalStateException("Bank not found for player");
            }
            
            this.clientCoinCount = bank.getCoins();
            this.isWorldOwner = isServerOwner(client.getServerClient());
            this.collectionDepositToBankPreferred = playerInventory.isCollectionDepositToBankPreferred();
            this.collectionPageIndex = playerInventory.getCollectionPageIndex();
            this.collectionTotalItems = playerInventory.getCollectionBoxSize();
            this.collectionTotalPages = Math.max(1, (int) Math.ceil(collectionTotalItems /
                (double) ModConfig.GrandExchange.getCollectionPageSize()));

        } else {
            this.geData = null;
            this.playerInventory = new PlayerGEInventory(playerAuth);
            this.bankingService = null;
            this.bank = null;
            this.isWorldOwner = ownerFlagFromPacket;
            this.ledger = null;
        }

        if (!serverContainer) {
            this.viewModel = new GrandExchangeViewModel(this);
        }

        // Add staging slots
        int configuredSellSlots = playerInventory.getSellInventory().getSize();
        int stagingSlots = Math.max(1, Math.min(1, configuredSellSlots));
        for (int i = 0; i < stagingSlots; i++) {
            int index = this.addSlot(new ContainerSlot(playerInventory.getSellInventory(), i));
            if (GE_SELL_SLOTS_START == -1) {
                GE_SELL_SLOTS_START = index;
            }
            GE_SELL_SLOTS_END = index;
            if (GE_STAGING_INVENTORY_SLOT_INDEX == -1) {
                GE_STAGING_INVENTORY_SLOT_INDEX = i;
            }
        }

        // Register actions
        registerActions();

        // Initialize handlers and sync (server only)
        if (client.isServer()) {
            initializeHandlers(client.getServerClient());
            sellHandler.reclaimHiddenInventory();
            refreshMarketListings();
            syncManager.sendInitialSyncToClient();
        }
    }

    private void initializeHandlers(ServerClient serverClient) {
        SellOfferWorkflow sellWorkflow = new SellOfferWorkflow(ledger);
        BuyOrderWorkflow buyWorkflow = new BuyOrderWorkflow(ledger);
        
        this.syncManager = new ContainerSyncManager(
            serverClient, playerAuth, playerInventory, geData, ledger, bank, syncCoinCount);
        
        this.sellHandler = new SellOfferActionHandler(
            serverClient, playerAuth, playerInventory, geData, sellWorkflow, syncManager);
        
        this.buyHandler = new BuyOrderActionHandler(
            serverClient, playerAuth, playerInventory, buyWorkflow, bankingService, bank, syncManager);
        
        this.marketHandler = new MarketActionHandler(
            serverClient, playerAuth, geData, ledger, bank, syncManager);
        
        this.collectionHandler = new CollectionActionHandler(
            serverClient, playerAuth, playerInventory, geData, bankingService, bank, syncManager);
        
        this.defaultsHandler = new DefaultsActionHandler(
            serverClient, playerAuth, geData, syncManager, isWorldOwner);
    }

    private boolean isServerOwner(ServerClient serverClient) {
        if (serverClient == null) {
            return false;
        }
        if (serverClient.getPermissionLevel() == PermissionLevel.OWNER) {
            return true;
        }
        return Settings.serverOwnerAuth != -1L && serverClient.authentication == Settings.serverOwnerAuth;
    }

    // ===== ACTION REGISTRATION =====

    private void registerActions() {
        // Tab switching
        this.setActiveTab = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int tabIndex) {
                int maxTab = canAccessDefaultsTab() ? 5 : 4;
                if (tabIndex < 0 || tabIndex > maxTab) return;
                if (!canAccessDefaultsTab() && tabIndex == 5) return;
                activeTab = tabIndex;
                if (client.isServer()) {
                    if (tabIndex == 0) refreshMarketListings();
                    else if (tabIndex == 3) syncManager.sendCollectionSync();
                    else if (tabIndex == 4) syncManager.sendHistorySnapshot();
                }
            }
        });

        // Market browser
        this.buyFromMarket = this.registerAction(new LongIntCustomAction() {
            @Override
            protected void run(long offerID, int quantity) {
                if (client.isServer()) {
                    marketHandler.handleBuyFromMarket(offerID, quantity, 
                        GrandExchangeContainer.this::refreshMarketListings);
                }
            }
        });

        this.setMarketFilter = this.registerAction(new StringCustomAction() {
            @Override
            protected void run(String filter) {
                marketFilter = filter == null ? "" : filter;
                marketPage = 0;
                if (client.isServer()) {
                    marketHandler.setFilter(filter);
                    refreshMarketListings();
                }
            }
        });

        this.setMarketCategory = this.registerAction(new StringCustomAction() {
            @Override
            protected void run(String category) {
                marketCategory = category == null ? "all" : category;
                marketPage = 0;
                if (client.isServer()) {
                    marketHandler.setCategory(category);
                    refreshMarketListings();
                }
            }
        });

        this.setMarketPage = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int page) {
                if (client.isServer()) {
                    marketPage = Math.max(0, page);
                    marketHandler.setPage(page);
                    refreshMarketListings();
                }
            }
        });

        this.setMarketSort = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int sortMode) {
                if (client.isServer()) {
                    marketSort = sortMode;
                    marketPage = 0;
                    marketHandler.setSort(sortMode);
                    refreshMarketListings();
                }
            }
        });

        // Buy orders
        this.createBuyOrder = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int slotIndex) {
                // Client-side opens form, actual creation via PacketGECreateBuyOrder
            }
        });

        this.enableBuyOrder = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int slotIndex) {
                if (client.isServer()) buyHandler.handleEnable(slotIndex);
            }
        });

        this.disableBuyOrder = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int slotIndex) {
                if (client.isServer()) buyHandler.handleDisable(slotIndex);
            }
        });

        this.cancelBuyOrder = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int slotIndex) {
                if (client.isServer()) buyHandler.handleCancel(slotIndex);
            }
        });

        // Sell offers
        this.createSellOffer = this.registerAction(new PointCustomAction() {
            @Override
            protected void run(int slotIndex, int pricePerItem) {
                if (client.isServer()) sellHandler.handleCreate(slotIndex, pricePerItem);
            }
        });

        this.enableSellOffer = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int slotIndex) {
                if (client.isServer()) {
                    sellHandler.handleEnable(slotIndex);
                    refreshMarketListings();
                }
            }
        });

        this.disableSellOffer = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int slotIndex) {
                if (client.isServer()) {
                    sellHandler.handleDisable(slotIndex);
                    refreshMarketListings();
                }
            }
        });

        this.cancelSellOffer = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int slotIndex) {
                if (client.isServer()) {
                    sellHandler.handleCancel(slotIndex);
                    refreshMarketListings();
                }
            }
        });

        // Collection
        this.collectItem = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int collectionIndex) {
                if (client.isServer()) collectionHandler.handleCollectItem(collectionIndex);
            }
        });

        this.collectAllToBank = this.registerAction(new EmptyCustomAction() {
            @Override
            protected void run() {
                if (client.isServer()) collectionHandler.handleCollectAllToBank();
            }
        });

        this.toggleAutoBank = this.registerAction(new BooleanCustomAction() {
            @Override
            protected void run(boolean enabled) {
                if (client.isServer()) collectionHandler.handleToggleAutoBank(enabled);
            }
        });

        this.toggleNotifyPartial = this.registerAction(new BooleanCustomAction() {
            @Override
            protected void run(boolean enabled) {
                if (client.isServer()) collectionHandler.handleToggleNotifyPartial(enabled);
            }
        });

        this.togglePlaySound = this.registerAction(new BooleanCustomAction() {
            @Override
            protected void run(boolean enabled) {
                if (client.isServer()) collectionHandler.handleTogglePlaySound(enabled);
            }
        });

        this.setCollectionPage = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int pageIndex) {
                if (client.isServer()) collectionHandler.handleSetCollectionPage(pageIndex);
            }
        });

        this.toggleCollectionDepositPreference = this.registerAction(new BooleanCustomAction() {
            @Override
            protected void run(boolean preferBank) {
                if (client.isServer()) collectionHandler.handleToggleCollectionDepositPreference(preferBank);
            }
        });

        this.collectSelectedEntries = this.registerAction(new LongBooleanCustomAction() {
            @Override
            protected void run(long selectionMask, boolean sendToBank) {
                if (client.isServer()) collectionHandler.handleCollectSelected(selectionMask, sendToBank);
            }
        });

        // Admin defaults
        this.updateSellSlotConfig = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int newSlotCount) {
                if (client.isServer()) defaultsHandler.handleUpdateSellSlotConfig(newSlotCount);
            }
        });

        this.updateBuySlotConfig = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int newSlotCount) {
                if (client.isServer()) defaultsHandler.handleUpdateBuySlotConfig(newSlotCount);
            }
        });

        this.updateAutoClearPreference = this.registerAction(new BooleanCustomAction() {
            @Override
            protected void run(boolean enabled) {
                if (client.isServer()) defaultsHandler.handleUpdateAutoClearPreference(enabled);
            }
        });

        // Coin sync
        this.syncCoinCount = this.registerAction(new LongCustomAction() {
            @Override
            protected void run(long coinCount) {
                if (!client.isServer()) {
                    long oldCount = clientCoinCount;
                    clientCoinCount = coinCount;
                    ModLogger.info("[BANK SYNC] Client received coin update: %d -> %d", oldCount, coinCount);
                    notifyCoinCountUpdated();
                }
            }
        });
    }

    // ===== MARKET METHODS =====

    private void refreshMarketListings() {
        if (marketHandler == null || !client.isServer()) {
            return;
        }
        marketHandler.refreshMarketListings(this::applyMarketSnapshot);
    }

    public void applyMarketSnapshot(MarketSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        this.marketPage = snapshot.getPage();
        this.marketTotalPages = snapshot.getTotalPages();
        this.marketTotalResults = snapshot.getTotalResults();
        this.marketPageSize = snapshot.getPageSize();
        this.marketFilter = snapshot.getFilter();
        this.marketCategory = snapshot.getCategory();
        this.marketSort = snapshot.getSortMode();
        this.marketInsightsSummary = snapshot.getInsightsSummary();

        this.marketListings.clear();
        for (MarketSnapshot.Entry entry : snapshot.getEntries()) {
            this.marketListings.add(MarketListingView.fromSnapshot(entry));
        }

        onMarketListingsUpdated();
        onMarketInsightsUpdated();
    }

    public List<MarketListingView> getCurrentPageListings() {
        return new ArrayList<>(marketListings);
    }

    public int getTotalPages() {
        return Math.max(1, marketTotalPages);
    }

    // ===== SYNC APPLY METHODS (called by packets) =====

    public void applyAutoClearSync(boolean enabled) {
        if (serverContainer) {
            return;
        }
        this.autoClearEnabled = enabled;
        ModConfig.GrandExchange.setAutoClearSellStagingSlot(enabled);
        GrandExchangeViewModel vm = getViewModel();
        vm.getSellTabState().setAutoClearEnabled(enabled);
        vm.getDefaultsTabState().setAutoClearAuthoritative(enabled);
        onSellOffersUpdated();
    }

    public void applyDefaultsConfig(DefaultsConfigSnapshot snapshot) {
        if (serverContainer || snapshot == null) {
            return;
        }
        this.autoClearEnabled = snapshot.autoClearEnabled();
        ModConfig.GrandExchange.setGeInventorySlots(snapshot.sellSlotConfigured());
        ModConfig.GrandExchange.setBuyOrderSlots(snapshot.buySlotConfigured());
        ModConfig.GrandExchange.setAutoClearSellStagingSlot(snapshot.autoClearEnabled());
        GrandExchangeViewModel vm = getViewModel();
        vm.applyDefaultsSnapshot(snapshot);
        onDefaultsConfigUpdated();
    }

    public void notifySellActionResult(SellActionResultMessage message) {
        if (message == null) {
            return;
        }
        if (sellHandler != null &&
            message.getSlotIndex() == sellHandler.getPendingSellSlot() &&
            message.getAction() == sellHandler.getPendingSellAction()) {
            sellHandler.clearPendingAction();
        }
        if (sellActionResultCallback != null) {
            sellActionResultCallback.accept(message);
        }
    }

    // ===== MARKET LISTING VIEW =====

    public static final class MarketListingView {
        public final long offerId;
        public final String itemStringID;
        public final int quantityTotal;
        public final int quantityRemaining;
        public final int pricePerItem;
        public final long sellerAuth;
        public final String sellerName;
        public final long expirationTime;
        public final long createdTime;
        public final GEOffer.OfferState state;

        public MarketListingView(long offerId, String itemStringID, int quantityTotal, int quantityRemaining,
                                 int pricePerItem, long sellerAuth, String sellerName,
                                 long expirationTime, long createdTime, GEOffer.OfferState state) {
            this.offerId = offerId;
            this.itemStringID = itemStringID;
            this.quantityTotal = quantityTotal;
            this.quantityRemaining = quantityRemaining;
            this.pricePerItem = pricePerItem;
            this.sellerAuth = sellerAuth;
            this.sellerName = sellerName;
            this.expirationTime = expirationTime;
            this.createdTime = createdTime;
            this.state = state;
        }

        public static MarketListingView fromSnapshot(MarketSnapshot.Entry entry) {
            return new MarketListingView(
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
            );
        }

        public int getTotalPrice() {
            return pricePerItem * Math.max(0, quantityRemaining);
        }
    }
}

