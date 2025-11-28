package medievalsim.grandexchange.ui;

import medievalsim.grandexchange.domain.GrandExchangeLevelData;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.grandexchange.domain.BuyOrder;
import medievalsim.grandexchange.domain.CollectionItem;
import medievalsim.grandexchange.domain.MarketSnapshot;
import medievalsim.banking.domain.PlayerBank;
import medievalsim.banking.service.BankingService;
import medievalsim.banking.service.BankingResult;
import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import medievalsim.packets.PacketGEBuyOrderSync;
import medievalsim.packets.PacketGESync;
import necesse.engine.Settings;
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
import necesse.inventory.container.slots.ContainerSlot;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Grand Exchange Container - 4-Tab System (Phase 9)
 * 
 * Architecture:
 * - Tab 0: Market Browser (buy from active offers)
 * - Tab 1: Buy Orders (3 slots with bank coin escrow)
 * - Tab 2: Sell Offers (10 inventory slots)
 * - Tab 3: Settings & Collection (unlimited collection box)
 * 
 * Client/Server Split:
 * - Server: Uses real PlayerGEInventory from GrandExchangeLevelData
 * - Client: Uses temporary PlayerGEInventory synced via packets
 */
public class GrandExchangeContainer extends Container {
    
    // ===== CORE DATA =====
    public final GrandExchangeLevelData geData;
    public final long playerAuth;
    public PlayerGEInventory playerInventory; // Real (server) or temporary (client)
    
    // ===== BANKING INTEGRATION =====
    private final BankingService bankingService; // Server only
    private final PlayerBank bank; // Server: real, Client: null
    public long clientCoinCount = 0; // Synced from server
    public necesse.inventory.container.customAction.LongCustomAction syncCoinCount;
    private final boolean isWorldOwner;
    private Runnable coinCountUpdateCallback;
    
    // ===== TAB SYSTEM =====
    public int activeTab = 0; // 0=Market, 1=BuyOrders, 2=Sell, 3=Collection, 4=History
    
    // ===== TAB 0: MARKET BROWSER =====
    public List<MarketListingView> marketListings = new ArrayList<>(); // Active offers for current page
    public String marketFilter = ""; // Item name search
    public String marketCategory = "all"; // Category filter
    public int marketPage = 0; // Current page
    public int marketSort = 1; // 1=price↑, 2=price↓, 3=quantity↓, 4=time↑
    public int marketTotalPages = 1;
    public int marketTotalResults = 0;
    public int marketPageSize = ModConfig.GrandExchange.maxListingsPerPage;
    
    // ===== TAB 1: BUY ORDERS =====
    // Data stored in playerInventory.buyOrders[3]
    
    // ===== TAB 2: SELL OFFERS =====
    // 10 inventory slots from playerInventory.sellInventory
    public int GE_SELL_SLOTS_START = -1;
    public int GE_SELL_SLOTS_END = -1;

    // ===== TAB 3: SETTINGS & COLLECTION =====
    // Data stored in playerInventory.collectionBox + saleHistory
    
    // ===== CUSTOM ACTIONS =====
    
    // Tab 0: Market Browser
    public LongCustomAction buyFromMarket; // offerID → buy at asking price
    public StringCustomAction setMarketFilter; // filter string
    public StringCustomAction setMarketCategory; // category filter
    public IntCustomAction setMarketPage; // page number
    public IntCustomAction setMarketSort; // sort mode
    
    // Tab 1: Buy Orders
    public IntCustomAction createBuyOrder; // Triggers form - actual creation via packet
    public IntCustomAction enableBuyOrder; // slotIndex → escrow coins from bank
    public IntCustomAction disableBuyOrder; // slotIndex → refund coins to bank
    public IntCustomAction cancelBuyOrder; // slotIndex → remove order with refund
    
    // Tab 2: Sell Offers
    public PointCustomAction createSellOffer; // x=slotIndex, y=pricePerItem
    public IntCustomAction enableSellOffer; // slotIndex → activate offer
    public IntCustomAction disableSellOffer; // slotIndex → deactivate offer
    public IntCustomAction cancelSellOffer; // slotIndex → remove offer
    
    // Tab 3: Settings & Collection
    public IntCustomAction collectItem; // collectionIndex → to inventory
    public EmptyCustomAction collectAllToBank; // Mass collect to bank
    public BooleanCustomAction toggleAutoBank; // Enable/disable auto-bank
    public BooleanCustomAction toggleNotifyPartial; // Enable/disable partial notifications
    public BooleanCustomAction togglePlaySound; // Enable/disable sale sounds
    public IntCustomAction updateSellSlotConfig; // Admin: update sell slot count
    public IntCustomAction updateBuySlotConfig; // Admin: update buy slot count
    
    // General
    public IntCustomAction setActiveTab; // Change active tab
    
    // ===== UI REFRESH CALLBACKS =====
    private Runnable buyOrdersUpdateCallback = null;
    private Runnable sellOffersUpdateCallback = null;
    private Runnable marketListingsUpdateCallback = null;
    
    public void setBuyOrdersUpdateCallback(Runnable callback) {
        this.buyOrdersUpdateCallback = callback;
    }
    
    public void setSellOffersUpdateCallback(Runnable callback) {
        this.sellOffersUpdateCallback = callback;
    }

    public void setMarketListingsUpdateCallback(Runnable callback) {
        this.marketListingsUpdateCallback = callback;
    }

    public void setCoinCountUpdateCallback(Runnable callback) {
        this.coinCountUpdateCallback = callback;
    }

    public boolean isWorldOwner() {
        return isWorldOwner;
    }
    
    /**
     * Called when buy orders are updated (e.g., from sync packet).
     * Triggers UI refresh if callback is registered.
     */
    public void onBuyOrdersUpdated() {
        ModLogger.info("[CALLBACK TRACE] onBuyOrdersUpdated called, callback is %s", 
            buyOrdersUpdateCallback != null ? "registered" : "NULL");
        if (buyOrdersUpdateCallback != null) {
            ModLogger.info("[CALLBACK TRACE] Executing callback...");
            buyOrdersUpdateCallback.run();
            ModLogger.info("[CALLBACK TRACE] Callback execution completed");
        } else {
            ModLogger.warn("[CALLBACK TRACE] No callback registered! UI will not update.");
        }
    }
    
    /**
     * Called when sell offers are updated (e.g., from sync packet).
     * Triggers UI refresh if callback is registered.
     */
    public void onSellOffersUpdated() {
        if (sellOffersUpdateCallback != null) {
            sellOffersUpdateCallback.run();
        }
    }

    /**
     * Called when market listings snapshot changes (client + server).
     */
    public void onMarketListingsUpdated() {
        if (marketListingsUpdateCallback != null) {
            marketListingsUpdateCallback.run();
        }
    }
    
    /**
     * Constructor for both client and server.
     * @param client NetworkClient
     * @param uniqueSeed Unique container seed
     * @param content Packet content with player auth
     */
    public GrandExchangeContainer(NetworkClient client, int uniqueSeed, Packet content) {
        super(client, uniqueSeed);
        
        PacketReader reader = new PacketReader(content);
        this.playerAuth = reader.getNextLong();
        this.clientCoinCount = reader.getNextLong(); // Read initial coin count
        boolean ownerFlagFromPacket = reader.getNextBoolean();
        int sellSlotCountFromServer = reader.getNextInt();
        int buySlotCountFromServer = reader.getNextInt();
        ModLogger.info("[BANK SYNC] Container constructor read initial coin count: %d (isServer=%s)", 
            clientCoinCount, client.isServer());
        if (!client.isServer()) {
            ModConfig.GrandExchange.setGeInventorySlots(sellSlotCountFromServer);
            ModConfig.GrandExchange.setBuyOrderSlots(buySlotCountFromServer);
        }
        
        // Get GE data and player inventory
        if (client.isServer()) {
            // Server-side: get actual GE data + real PlayerGEInventory
            this.geData = GrandExchangeLevelData.getGrandExchangeData(
                client.getServerClient().getLevel());
            if (this.geData == null) {
                ModLogger.error("Failed to get GE data for player auth=%d", playerAuth);
                throw new IllegalStateException("GE data not found");
            }
            
            // Get or create player's GE inventory
            this.playerInventory = geData.getOrCreateInventory(playerAuth);
            ModLogger.debug("Server: Loaded PlayerGEInventory for auth=%d", playerAuth);
            
            // Initialize banking service
            this.bankingService = new BankingService(client.getServerClient());
            this.bank = bankingService.getBank();
            if (this.bank == null) {
                ModLogger.error("Failed to get bank for player auth=%d", playerAuth);
                throw new IllegalStateException("Bank not found for player");
            }
            this.clientCoinCount = bank.getCoins();
            ModLogger.info("[BANK SYNC] Server initialized GE container: auth=%d, bank coins=%d, clientCoinCount=%d", 
                playerAuth, bank.getCoins(), clientCoinCount);
            this.isWorldOwner = isServerOwner(client.getServerClient());
        } else {
            // Client-side: create temporary inventory (will be synced via packets)
            this.geData = null;
            this.playerInventory = new PlayerGEInventory(playerAuth);
            this.bankingService = null;
            this.bank = null;
            ModLogger.debug("Client: Created temporary PlayerGEInventory for auth=%d", playerAuth);
            this.isWorldOwner = ownerFlagFromPacket;
        }
        
        // Add sell inventory slots (10 slots from playerInventory.sellInventory)
        int configuredSellSlots = playerInventory.getSellInventory().getSize();
        for (int i = 0; i < configuredSellSlots; i++) {
            int index = this.addSlot(new ContainerSlot(playerInventory.getSellInventory(), i));
            if (GE_SELL_SLOTS_START == -1) {
                GE_SELL_SLOTS_START = index;
            }
            GE_SELL_SLOTS_END = index;
        }
        
        ModLogger.debug("Added %d GE sell slots (indices %d-%d)",
            configuredSellSlots, GE_SELL_SLOTS_START, GE_SELL_SLOTS_END);
        
        // Register custom actions
        registerActions();
        
        // Load initial data (server only)
        if (client.isServer()) {
            refreshMarketListings();
            
            // Send initial sync packets to client
            sendInitialSyncToClient();
        }
    }
    
    /**
     * Send initial sync packets when container opens.
     */
    private void sendInitialSyncToClient() {
        if (!client.isServer()) return;
        
        // Sync buy orders
        PacketGEBuyOrderSync buyOrderPacket = new PacketGEBuyOrderSync(
            playerAuth, playerInventory.getBuyOrders());
        client.getServerClient().sendPacket(buyOrderPacket);
        
        // Sync sell inventory
        sendSellInventorySync();
        
        ModLogger.debug("Sent initial GE sync packets to client auth=%d", playerAuth);
    }
    
    /**
     * Register custom actions for all 4 tabs.
     */
    private void registerActions() {
        // ===== TAB SWITCHING =====
        this.setActiveTab = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int tabIndex) {
                if (tabIndex >= 0 && tabIndex <= 5) {
                    activeTab = tabIndex;
                    ModLogger.debug("Switched to tab %d", tabIndex);
                }
            }
        });
        
        // ===== TAB 0: MARKET BROWSER =====
        this.buyFromMarket = this.registerAction(new LongCustomAction() {
            @Override
            protected void run(long offerID) {
                if (client.isServer()) {
                    handleBuyFromMarket(offerID);
                }
            }
        });
        
        this.setMarketFilter = this.registerAction(new StringCustomAction() {
            @Override
            protected void run(String filter) {
                marketFilter = filter == null ? "" : filter;
                marketPage = 0;
                if (client.isServer()) {
                    refreshMarketListings();
                }
                ModLogger.debug("Market filter set to: %s", marketFilter);
            }
        });
        
        this.setMarketCategory = this.registerAction(new StringCustomAction() {
            @Override
            protected void run(String category) {
                marketCategory = category == null ? "all" : category;
                marketPage = 0;
                if (client.isServer()) {
                    refreshMarketListings();
                }
                ModLogger.debug("Market category set to: %s", marketCategory);
            }
        });
        
        this.setMarketPage = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int page) {
                if (client.isServer()) {
                    marketPage = Math.max(0, page);
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
                    refreshMarketListings();
                }
            }
        });
        
        // ===== TAB 1: BUY ORDERS =====
        // Note: createBuyOrder opens form, actual creation via PacketGECreateBuyOrder
        this.createBuyOrder = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int slotIndex) {
                ModLogger.debug("Opening buy order form for slot %d", slotIndex);
                // Client-side: Opens buy order form (Phase 12)
                // Form sends PacketGECreateBuyOrder when submitted
            }
        });
        
        this.enableBuyOrder = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int slotIndex) {
                if (client.isServer()) {
                    handleEnableBuyOrder(slotIndex);
                }
            }
        });
        
        this.disableBuyOrder = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int slotIndex) {
                if (client.isServer()) {
                    handleDisableBuyOrder(slotIndex);
                }
            }
        });
        
        this.cancelBuyOrder = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int slotIndex) {
                if (client.isServer()) {
                    handleCancelBuyOrder(slotIndex);
                }
            }
        });
        
        // ===== TAB 2: SELL OFFERS =====
        this.createSellOffer = this.registerAction(new PointCustomAction() {
            @Override
            protected void run(int slotIndex, int pricePerItem) {
                if (client.isServer()) {
                    handleCreateSellOffer(slotIndex, pricePerItem);
                }
            }
        });
        
        this.enableSellOffer = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int slotIndex) {
                if (client.isServer()) {
                    handleEnableSellOffer(slotIndex);
                }
            }
        });
        
        this.disableSellOffer = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int slotIndex) {
                if (client.isServer()) {
                    handleDisableSellOffer(slotIndex);
                }
            }
        });
        
        this.cancelSellOffer = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int slotIndex) {
                if (client.isServer()) {
                    handleCancelSellOffer(slotIndex);
                }
            }
        });
        
        // ===== TAB 3: SETTINGS & COLLECTION =====
        this.collectItem = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int collectionIndex) {
                if (client.isServer()) {
                    handleCollectItem(collectionIndex);
                }
            }
        });
        
        this.collectAllToBank = this.registerAction(new EmptyCustomAction() {
            @Override
            protected void run() {
                if (client.isServer()) {
                    handleCollectAllToBank();
                }
            }
        });
        
        this.toggleAutoBank = this.registerAction(new BooleanCustomAction() {
            @Override
            protected void run(boolean enabled) {
                if (client.isServer()) {
                    playerInventory.setAutoSendToBank(enabled);
                    ModLogger.debug("Auto-bank toggled: %s", enabled);
                }
            }
        });
        
        this.toggleNotifyPartial = this.registerAction(new BooleanCustomAction() {
            @Override
            protected void run(boolean enabled) {
                if (client.isServer()) {
                    playerInventory.setNotifyPartialSales(enabled);
                    ModLogger.debug("Notify partial sales toggled: %s", enabled);
                }
            }
        });
        
        this.togglePlaySound = this.registerAction(new BooleanCustomAction() {
            @Override
            protected void run(boolean enabled) {
                if (client.isServer()) {
                    playerInventory.setPlaySoundOnSale(enabled);
                    ModLogger.debug("Play sound on sale toggled: %s", enabled);
                }
            }
        });

        this.updateSellSlotConfig = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int newSlotCount) {
                handleUpdateSellSlotConfig(newSlotCount);
            }
        });

        this.updateBuySlotConfig = this.registerAction(new IntCustomAction() {
            @Override
            protected void run(int newSlotCount) {
                handleUpdateBuySlotConfig(newSlotCount);
            }
        });
        
        // ===== BANKING SYNC =====
        this.syncCoinCount = this.registerAction(new necesse.inventory.container.customAction.LongCustomAction() {
            @Override
            protected void run(long coinCount) {
                if (!client.isServer()) {
                    long oldCount = clientCoinCount;
                    clientCoinCount = coinCount;
                    ModLogger.info("[BANK SYNC] Client received coin update: %d -> %d", oldCount, coinCount);
                    notifyCoinCountUpdated();
                } else {
                    ModLogger.info("[BANK SYNC] Server coin sync action called with value: %d (bank actual: %d)", 
                        coinCount, bank != null ? bank.getCoins() : -1);
                }
            }
        });
    }

    private void notifyCoinCountUpdated() {
        if (coinCountUpdateCallback != null) {
            coinCountUpdateCallback.run();
        }
    }

    private void handleUpdateSellSlotConfig(int requestedSlots) {
        if (!client.isServer()) {
            return;
        }
        if (!isWorldOwner) {
            sendAdminMessage("Only the world owner can change Grand Exchange slots.");
            return;
        }
        int minSlots = 5;
        int maxSlots = 20;
        if (requestedSlots < minSlots || requestedSlots > maxSlots) {
            sendAdminMessage(String.format("Sell slots must be between %d and %d.", minSlots, maxSlots));
            return;
        }
        int current = ModConfig.GrandExchange.geInventorySlots;
        if (requestedSlots == current) {
            sendAdminMessage("Sell slot count is already set to that value.");
            return;
        }
        if (requestedSlots < current && geData != null && !geData.canApplySellSlotCount(requestedSlots)) {
            sendAdminMessage("Cannot reduce sell slots while higher slots still contain items or offers.");
            return;
        }
        ModConfig.GrandExchange.setGeInventorySlots(requestedSlots);
        if (geData != null) {
            geData.resizeAllSellInventories(requestedSlots);
        }
        Settings.saveServerSettings();
        ModLogger.info("World owner %d updated GE sell slots to %d", playerAuth, requestedSlots);
        sendAdminMessage(String.format("Sell slots updated to %d. Reopen the GE to apply changes.", requestedSlots));
    }

    private void handleUpdateBuySlotConfig(int requestedSlots) {
        if (!client.isServer()) {
            return;
        }
        if (!isWorldOwner) {
            sendAdminMessage("Only the world owner can change Grand Exchange slots.");
            return;
        }
        int minSlots = 1;
        int maxSlots = 10;
        if (requestedSlots < minSlots || requestedSlots > maxSlots) {
            sendAdminMessage(String.format("Buy order slots must be between %d and %d.", minSlots, maxSlots));
            return;
        }
        int current = ModConfig.GrandExchange.buyOrderSlots;
        if (requestedSlots == current) {
            sendAdminMessage("Buy order slot count is already set to that value.");
            return;
        }
        if (requestedSlots < current && geData != null && !geData.canApplyBuySlotCount(requestedSlots)) {
            sendAdminMessage("Cannot reduce buy order slots while higher slots still contain orders.");
            return;
        }
        ModConfig.GrandExchange.setBuyOrderSlots(requestedSlots);
        if (geData != null) {
            geData.resizeAllBuyInventories(requestedSlots);
        }
        Settings.saveServerSettings();
        ModLogger.info("World owner %d updated GE buy order slots to %d", playerAuth, requestedSlots);
        sendAdminMessage(String.format("Buy order slots updated to %d. Reopen the GE to apply changes.", requestedSlots));
    }

    private void sendAdminMessage(String message) {
        if (!client.isServer()) {
            return;
        }
        ServerClient serverClient = client.getServerClient();
        if (serverClient != null) {
            serverClient.sendChatMessage(message);
        }
    }

    private boolean isServerOwner(ServerClient serverClient) {
        return serverClient != null
            && Settings.serverOwnerAuth != -1L
            && serverClient.authentication == Settings.serverOwnerAuth;
    }
    
    // ===== TAB 0: MARKET BROWSER METHODS =====
    
    /**
     * Refresh market listings by asking the level data for a new snapshot.
     * Server-only; client relies on PacketGESync to push updates.
     */
    private void refreshMarketListings() {
        if (geData == null || !client.isServer()) {
            return;
        }

        MarketSnapshot snapshot = geData.buildMarketSnapshot(
            marketFilter,
            marketCategory,
            marketSort,
            marketPage
        );

        applyMarketSnapshot(snapshot);
        client.getServerClient().sendPacket(new PacketGESync(playerAuth, snapshot));
        ModLogger.debug("Market snapshot refreshed: page=%d totalResults=%d filter='%s' category='%s' sort=%d",
            marketPage, marketTotalResults, marketFilter, marketCategory, marketSort);
    }

    /**
     * Called by both server refreshes and client packets to align container state.
     */
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

        this.marketListings.clear();
        for (MarketSnapshot.Entry entry : snapshot.getEntries()) {
            this.marketListings.add(MarketListingView.fromSnapshot(entry));
        }

        onMarketListingsUpdated();
    }

    /**
     * Returns the listings for the currently synced page.
     */
    public List<MarketListingView> getCurrentPageListings() {
        return new ArrayList<>(marketListings);
    }

    /**
     * Cached total pages from the snapshot metadata.
     */
    public int getTotalPages() {
        return Math.max(1, marketTotalPages);
    }

    // ===== ACTION HANDLERS =====
    
    // --- Tab 0: Market Browser ---
    
    /**
     * Buy item from market at asking price (adds to collection box).
     */
    private void handleBuyFromMarket(long offerID) {
        if (geData == null || !client.isServer()) {
            ModLogger.warn("Market purchase attempted on client or without GE data");
            return;
        }

        Level level = client.getServerClient().getLevel();
        String buyerName = client.getServerClient().getName();
        boolean success = geData.processMarketPurchase(level, playerAuth, buyerName, offerID);
        if (!success) {
            ModLogger.warn("Market purchase failed for offer ID=%d (player auth=%d)", offerID, playerAuth);
            return;
        }

        // Sync coin count and collection after server-side transaction
        if (bank != null) {
            syncCoinCount.runAndSend(bank.getCoins());
        }
        sendCollectionSync();
        refreshMarketListings();
    }
    
    // --- Tab 1: Buy Orders ---
    
    /**
     * Enable buy order (escrows coins from bank).
     */
    private void handleEnableBuyOrder(int slotIndex) {
        if (!validateSlot(slotIndex, 0, ModConfig.GrandExchange.buyOrderSlots)) {
            return;
        }
        
        BuyOrder order = playerInventory.getBuyOrder(slotIndex);
        if (order == null) {
            ModLogger.warn("No buy order in slot %d", slotIndex);
            return;
        }
        
        int quantityRemaining = order.getQuantityRemaining();
        if (quantityRemaining <= 0) {
            ModLogger.warn("Buy order slot %d has no remaining quantity to enable", slotIndex);
            return;
        }

        // Calculate escrow amount based on remaining units
        int escrowAmount = order.getPricePerItem() * quantityRemaining;
        
        // Withdraw from bank for escrow
        if (bankingService == null) {
            ModLogger.error("Banking service not available");
            return;
        }
        
        BankingResult result = bankingService.withdrawCoins(escrowAmount);
        if (!result.isSuccess()) {
            ModLogger.warn("Failed to escrow %d coins: %s", escrowAmount, result.getMessageKey());
            return;
        }
        
        // Enable the order
        geData.enableBuyOrder(client.getServerClient().getLevel(), playerAuth, slotIndex);
        ModLogger.info("Enabled buy order slot %d for player auth=%d (escrowed %d coins for %d items)",
            slotIndex, playerAuth, escrowAmount, quantityRemaining);
        
        syncCoinCount.runAndSend(bank.getCoins());
        sendBuyOrderSync();
    }
    
    /**
     * Disable buy order (refunds coins to bank).
     */
    private void handleDisableBuyOrder(int slotIndex) {
        if (!validateSlot(slotIndex, 0, ModConfig.GrandExchange.buyOrderSlots)) {
            return;
        }
        
        BuyOrder order = playerInventory.getBuyOrder(slotIndex);
        if (order == null) {
            ModLogger.warn("No buy order in slot %d", slotIndex);
            return;
        }
        
        // Calculate refund (unfilled portion)
        int refundAmount = order.getPricePerItem() * order.getQuantityRemaining();
        
        // Disable the order first
        geData.disableBuyOrder(client.getServerClient().getLevel(), playerAuth, slotIndex);
        
        // Refund coins to bank
        if (bankingService != null && refundAmount > 0) {
            bankingService.depositCoins(refundAmount);
            ModLogger.info("Disabled buy order slot %d, refunded %d coins to bank",
                slotIndex, refundAmount);
            syncCoinCount.runAndSend(bank.getCoins());
        }
        
        sendBuyOrderSync();
    }
    
    /**
     * Cancel buy order (removes order with refund).
     */
    private void handleCancelBuyOrder(int slotIndex) {
        if (!validateSlot(slotIndex, 0, ModConfig.GrandExchange.buyOrderSlots)) {
            return;
        }
        
        BuyOrder order = playerInventory.getBuyOrder(slotIndex);
        if (order == null) {
            ModLogger.warn("No buy order in slot %d", slotIndex);
            return;
        }
        
        // Calculate refund (full remaining amount)
        int refundAmount = order.getPricePerItem() * order.getQuantityRemaining();
        
        // Cancel the order
        geData.cancelBuyOrder(client.getServerClient().getLevel(), playerAuth, slotIndex);
        
        // Refund coins to bank
        if (bankingService != null && refundAmount > 0) {
            bankingService.depositCoins(refundAmount);
            long newBalance = bank.getCoins();
            ModLogger.info("[BANK SYNC] Canceled buy order slot %d, refunded %d coins, new bank balance: %d",
                slotIndex, refundAmount, newBalance);
            ModLogger.info("[BANK SYNC] Sending coin sync to client: %d", newBalance);
            syncCoinCount.runAndSend(newBalance);
        }
        
        sendBuyOrderSync();
    }
    
    // --- Tab 2: Sell Offers ---
    
    /**
     * Create sell offer (DRAFT state).
     */
    private void handleCreateSellOffer(int slotIndex, int pricePerItem) {
        if (!validateSlot(slotIndex, 0, ModConfig.GrandExchange.geInventorySlots)) {
            return;
        }
        
        // Get item from sell inventory
        InventoryItem item = playerInventory.getSellInventory().getItem(slotIndex);
        if (item == null || item.item == null) {
            ModLogger.warn("No item in sell slot %d", slotIndex);
            return;
        }
        
        // Validate price
        if (pricePerItem <= 0 || !ModConfig.GrandExchange.isValidPrice(pricePerItem)) {
            ModLogger.warn("Invalid price: %d (min=%d, max=%d)", pricePerItem,
                ModConfig.GrandExchange.minPricePerItem,
                ModConfig.GrandExchange.maxPricePerItem);
            return;
        }
        
        // Validate quantity
        int quantity = item.getAmount();
        if (quantity <= 0 || quantity > item.item.getStackSize()) {
            ModLogger.warn("Invalid item quantity in slot %d: %d (max stack=%d)",
                slotIndex, quantity, item.item.getStackSize());
            return;
        }
        
        // CRITICAL: Verify item is actually in slot before creating offer
        // This prevents duplication exploits where players modify inventory externally
        InventoryItem currentItem = playerInventory.getSellInventory().getItem(slotIndex);
        if (currentItem == null || currentItem.item == null ||
            !currentItem.item.getStringID().equals(item.item.getStringID()) ||
            currentItem.getAmount() != quantity) {
            ModLogger.warn("Item validation failed for slot %d: inventory state changed", slotIndex);
            return;
        }
        
        // Create DRAFT sell offer using GrandExchangeLevelData
        // This properly generates offerID and creates the offer
        GEOffer offer = geData.createSellOffer(
            playerAuth,
            client.getServerClient().getName(),
            slotIndex,
            item.item.getStringID(),
            quantity,
            pricePerItem
        );
        
        if (offer != null) {
            ModLogger.info("Created DRAFT sell offer slot %d: %s x%d @ %d coins",
                slotIndex, item.item.getStringID(), quantity, pricePerItem);
            sendSellInventorySync();
        } else {
            ModLogger.warn("Failed to create sell offer for slot %d", slotIndex);
        }
    }
    
    /**
     * Enable sell offer (activates offer, adds to market).
     */
    private void handleEnableSellOffer(int slotIndex) {
        if (!validateSlot(slotIndex, 0, ModConfig.GrandExchange.geInventorySlots)) {
            return;
        }
        
        geData.enableSellOffer(client.getServerClient().getLevel(), playerAuth, slotIndex);
        ModLogger.info("Enabled sell offer slot %d for player auth=%d", slotIndex, playerAuth);
        
        sendSellInventorySync();
    }
    
    /**
     * Disable sell offer (deactivates offer, removes from market).
     */
    private void handleDisableSellOffer(int slotIndex) {
        if (!validateSlot(slotIndex, 0, ModConfig.GrandExchange.geInventorySlots)) {
            return;
        }
        
        geData.disableSellOffer(client.getServerClient().getLevel(), playerAuth, slotIndex);
        ModLogger.info("Disabled sell offer slot %d for player auth=%d", slotIndex, playerAuth);
        
        sendSellInventorySync();
    }
    
    /**
     * Cancel sell offer (removes offer, returns item to player inventory).
     */
    private void handleCancelSellOffer(int slotIndex) {
        if (!validateSlot(slotIndex, 0, ModConfig.GrandExchange.geInventorySlots)) {
            return;
        }
        
        GEOffer offer = playerInventory.getSlotOffer(slotIndex);
        if (offer == null) {
            ModLogger.warn("No offer in slot %d", slotIndex);
            return;
        }
        
        Level level = client.getServerClient().getLevel();
        if (!geData.cancelOffer(level, offer.getOfferID())) {
            ModLogger.warn("Failed to cancel sell offer ID=%d for slot %d", offer.getOfferID(), slotIndex);
            return;
        }

        // Attempt to return items to the player's main inventory after cancellation
        InventoryItem slotItem = playerInventory.getSellInventory().getItem(slotIndex);
        if (slotItem != null && slotItem.item != null) {
            InventoryItem returnCopy = slotItem.copy();
            boolean added = client.getServerClient().playerMob.getInv().main.addItem(
                level,
                client.getServerClient().playerMob,
                returnCopy,
                "grandexchange_cancel",
                null
            );

            if (added) {
                playerInventory.getSellInventory().setItem(slotIndex, null);
                ModLogger.info("Returned %s x%d to player auth=%d after cancelling offer %d",
                    slotItem.item.getStringID(), slotItem.getAmount(), playerAuth, offer.getOfferID());
            } else {
                ModLogger.warn("Inventory full for player auth=%d; item remains in GE slot %d after cancellation",
                    playerAuth, slotIndex);
            }
        }
        
        ModLogger.info("Canceled sell offer slot %d for player auth=%d", slotIndex, playerAuth);
        
        sendSellInventorySync();
    }
    
    // --- Tab 3: Settings & Collection ---
    
    /**
     * Collect item from collection box to player inventory.
     * Fixed: Properly handles index removal to prevent shifting bugs.
     */
    private void handleCollectItem(int collectionIndex) {
        List<CollectionItem> collectionBox = playerInventory.getCollectionBox();
        
        if (collectionIndex < 0 || collectionIndex >= collectionBox.size()) {
            ModLogger.warn("Invalid collection index: %d (size=%d)", collectionIndex, collectionBox.size());
            return;
        }
        
        // Get item BEFORE removal to avoid index issues
        CollectionItem item = collectionBox.get(collectionIndex);
        if (item == null) {
            ModLogger.warn("Null item at collection index %d", collectionIndex);
            return;
        }
        
        ServerClient serverClient = client.getServerClient();
        if (serverClient == null) {
            ModLogger.warn("No server client available for collection request (auth=%d)", playerAuth);
            return;
        }
        Level playerLevel = serverClient.getLevel();
        
        // If auto-bank enabled, send directly to bank
        if (playerInventory.isAutoSendToBank()) {
            if (bankingService != null && bank != null) {
                // Add item to bank inventory
                InventoryItem invItem = new InventoryItem(item.getItemStringID(), item.getQuantity());
                boolean added = bank.getInventory().addItem(
                    playerLevel,
                    serverClient.playerMob,
                    invItem,
                    "grandexchange_collect",
                    null
                );
                
                if (added) {
                    // Only remove from collection if successfully added to bank
                    collectionBox.remove(collectionIndex);
                    ModLogger.info("Collected item %d to bank (auto-bank enabled): %s x%d",
                        collectionIndex, item.getItemStringID(), item.getQuantity());
                } else {
                    ModLogger.warn("Failed to add item to bank, keeping in collection box");
                }
            } else {
                ModLogger.warn("Banking service not available for auto-collect");
            }
        } else {
            CollectionItem removed = geData.collectFromCollectionBox(playerAuth, collectionIndex);
            if (removed == null) {
                ModLogger.warn("Failed to remove collection index %d for player auth=%d", collectionIndex, playerAuth);
                return;
            }
            InventoryItem invItem = new InventoryItem(removed.getItemStringID(), removed.getQuantity());
            if (invItem.item == null) {
                playerInventory.insertIntoCollectionBox(collectionIndex, removed);
                ModLogger.warn("Unknown item string ID '%s' at collection index %d for auth=%d; restored to collection box",
                    removed.getItemStringID(), collectionIndex, playerAuth);
                sendCollectionSync();
                return;
            }
            boolean added = serverClient.playerMob.getInv().main.addItem(
                playerLevel,
                serverClient.playerMob,
                invItem,
                "grandexchange_collect",
                null
            );
            if (added) {
                ModLogger.info("Collected item %d from collection box for player auth=%d: %s x%d",
                    collectionIndex, playerAuth, removed.getItemStringID(), removed.getQuantity());
            } else {
                playerInventory.insertIntoCollectionBox(collectionIndex, removed);
                ModLogger.warn("Inventory full when collecting %s x%d for player auth=%d; restored to collection box",
                    removed.getItemStringID(), removed.getQuantity(), playerAuth);
            }
        }
        
        sendCollectionSync();
    }
    
    /**
     * Collect all items from collection box to bank.
     * Fixed: Collects in reverse order to prevent index shifting issues.
     */
    private void handleCollectAllToBank() {
        if (bankingService == null || bank == null) {
            ModLogger.warn("Banking service not available for collect all");
            return;
        }
        
        List<CollectionItem> collectionBox = playerInventory.getCollectionBox();
        int collectedCount = 0;
        int failedCount = 0;
        
        // Collect in REVERSE order to avoid index shifting issues
        for (int i = collectionBox.size() - 1; i >= 0; i--) {
            CollectionItem item = collectionBox.get(i);
            if (item != null) {
                InventoryItem invItem = new InventoryItem(item.getItemStringID(), item.getQuantity());
                boolean added = bank.getInventory().addItem(
                    client.getServerClient().getLevel(),
                    client.getServerClient().playerMob,
                    invItem,
                    "grandexchange_collect_all",
                    null
                );
                
                if (added) {
                    collectionBox.remove(i);
                    collectedCount++;
                } else {
                    failedCount++;
                    ModLogger.warn("Failed to add item to bank: %s x%d",
                        item.getItemStringID(), item.getQuantity());
                }
            }
        }
        
        ModLogger.info("Collected %d items to bank for player auth=%d (%d failed)",
            collectedCount, playerAuth, failedCount);
        
        sendCollectionSync();
    }
    
    // ===== HELPER METHODS =====
    
    /**
     * Validate slot index.
     */
    private boolean validateSlot(int slotIndex, int min, int max) {
        if (slotIndex < min || slotIndex >= max) {
            ModLogger.warn("Invalid slot index: %d (valid: %d-%d)", slotIndex, min, max - 1);
            return false;
        }
        return true;
    }
    
    // ===== PACKET SYNC METHODS (Phase 11) =====
    
    /**
     * Send buy order sync packet to client.
     */
    private void sendBuyOrderSync() {
        if (!client.isServer()) return;
        
        medievalsim.packets.PacketGEBuyOrderSync packet = 
            new medievalsim.packets.PacketGEBuyOrderSync(playerAuth, playerInventory.getBuyOrders());
        
        client.getServerClient().sendPacket(packet);
        ModLogger.debug("Sent buy order sync to player auth=%d", playerAuth);
    }
    
    /**
     * Send sell inventory sync packet to client.
     */
    private void sendSellInventorySync() {
        if (!client.isServer()) return;
        
        // Extract offer data from playerInventory
        int slotCount = ModConfig.GrandExchange.geInventorySlots;
        long[] offerIDs = new long[slotCount];
        String[] itemStringIDs = new String[slotCount];
        int[] quantityTotal = new int[slotCount];
        boolean[] offerEnabled = new boolean[slotCount];
        int[] offerStates = new int[slotCount];
        int[] offerPrices = new int[slotCount];
        int[] offerQuantitiesRemaining = new int[slotCount];
        
        for (int i = 0; i < slotCount; i++) {
            GEOffer offer = playerInventory.getSlotOffer(i);
            if (offer != null) {
                offerIDs[i] = offer.getOfferID();
                itemStringIDs[i] = offer.getItemStringID();
                quantityTotal[i] = offer.getQuantityTotal();
                offerEnabled[i] = offer.isEnabled();
                offerStates[i] = offer.getState().ordinal();
                offerPrices[i] = offer.getPricePerItem();
                offerQuantitiesRemaining[i] = offer.getQuantityRemaining();
            } else {
                offerIDs[i] = 0;
                itemStringIDs[i] = "";
                quantityTotal[i] = 0;
                offerEnabled[i] = false;
                offerStates[i] = 0;
                offerPrices[i] = 0;
                offerQuantitiesRemaining[i] = 0;
            }
        }
        
        medievalsim.packets.PacketGESellInventorySync packet = 
            new medievalsim.packets.PacketGESellInventorySync(
                playerAuth, offerIDs, itemStringIDs, quantityTotal, 
                offerEnabled, offerStates, offerPrices, offerQuantitiesRemaining);
        
        client.getServerClient().sendPacket(packet);
        ModLogger.debug("Sent sell inventory sync to player auth=%d", playerAuth);
    }
    
    /**
     * Send collection box sync packet to client.
     */
    private void sendCollectionSync() {
        if (!client.isServer()) return;
        
        medievalsim.packets.PacketGECollectionSync packet = 
            new medievalsim.packets.PacketGECollectionSync(playerAuth, playerInventory.getCollectionBox());
        
        client.getServerClient().sendPacket(packet);
        ModLogger.debug("Sent collection sync to player auth=%d", playerAuth);
    }

    /**
     * Lightweight client-facing DTO for a market listing row.
     */
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

