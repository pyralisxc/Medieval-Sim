package medievalsim.grandexchange.domain;

import medievalsim.banking.domain.BankingLevelData;
import medievalsim.banking.domain.PlayerBank;
import medievalsim.config.ModConfig;
import medievalsim.grandexchange.model.event.SellOfferSaleEvent;
import medievalsim.grandexchange.model.snapshot.HistoryDeltaPayload;
import medievalsim.grandexchange.model.snapshot.HistoryEntrySnapshot;
import medievalsim.grandexchange.ui.GrandExchangeContainer;
import medievalsim.grandexchange.net.SellActionResultCode;
import medievalsim.grandexchange.repository.InMemoryOfferRepository;
import medievalsim.grandexchange.repository.OfferRepository;
import medievalsim.grandexchange.services.*;
import medievalsim.grandexchange.util.CollectionPaginator;
import medievalsim.packets.PacketGECollectionSync;
import medievalsim.packets.PacketGEHistoryBadge;
import medievalsim.packets.PacketGEHistoryDelta;
import medievalsim.packets.PacketGEHistorySync;
import medievalsim.packets.PacketGESaleEvent;
import medievalsim.util.ModLogger;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemCategory;
import necesse.engine.registries.ItemRegistry;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.LevelData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * World-level storage for Grand Exchange (RuneScape-style inventory system).
 * 
 * Architecture:
 * - Each player has a PlayerGEInventory (10 slots for items being traded)
 * - Offers (GEOffer) are linked to inventory slots
 * - Supports instant trade matching (sell/buy offers)
 * - Tracks price history for market analytics
 * 
 * Pattern: Similar to BankingLevelData - manages per-player domain objects.
 */
public class GrandExchangeLevelData extends LevelData {
    
    public static final String DATA_KEY = "grandexchangedata";
    
    // ===== CORE DATA STRUCTURES =====
    
    // Player inventories (one 10-slot inventory per player)
    private final ConcurrentHashMap<Long, PlayerGEInventory> inventories = new ConcurrentHashMap<>();
    
    // All offers (offerID -> GEOffer)
    private final ConcurrentHashMap<Long, GEOffer> offers = new ConcurrentHashMap<>();
    
    // Offers indexed by item for fast matching (itemStringID -> List<offerID>)
    private final ConcurrentHashMap<String, List<Long>> offersByItem = new ConcurrentHashMap<>();
    
    // Buy orders indexed by item for fast matching (itemStringID -> List<orderID>)
    private final ConcurrentHashMap<String, List<Long>> buyOrdersByItem = new ConcurrentHashMap<>();
    
    // All buy orders (orderID -> BuyOrder)
    private final ConcurrentHashMap<Long, BuyOrder> buyOrders = new ConcurrentHashMap<>();
    
    // Price history (itemStringID -> list of recent sale prices)
    private final ConcurrentHashMap<String, List<Integer>> priceHistory = new ConcurrentHashMap<>();

    // ID generators
    private final AtomicLong nextOfferID = new AtomicLong(1);
    private final AtomicLong nextBuyOrderID = new AtomicLong(1);

    // Statistics
    private long totalInventoriesCreated = 0;
    private long totalOffersCreated = 0;
    private long totalTradesCompleted = 0;
    private long totalVolumeTraded = 0L;  // Total coins traded

    // Cleanup tracking (run cleanup every 5 minutes)
    private int tickCounter = 0;
    private static final int CLEANUP_INTERVAL_TICKS = 20 * 60 * 5; // 5 minutes

    // Persistence + diagnostics tracking
    private static final int PERSIST_INTERVAL_TICKS = 20 * 60 * 30; // 30 minutes in ticks
    private int persistenceTickCounter = 0;
    private final Deque<DiagnosticsSnapshot> diagnosticsHistory = new ArrayDeque<>(getConfiguredDiagnosticsCapacity());
    private static int getConfiguredDiagnosticsCapacity() {
        return Math.max(5, ModConfig.GrandExchange.diagnosticsHistorySize);
    }
    
    // ===== ENTERPRISE SERVICES =====
    
    private final ConcurrentHashMap<String, OrderBook> orderBooksByItem = new ConcurrentHashMap<>();
    private MarketAnalyticsService analyticsService;
    private RateLimitService rateLimitService;
    private TradeAuditLog auditLog;
    private NotificationService notificationService;
    private PerformanceMetrics performanceMetrics;
    private OfferRepository repository;
    
    public GrandExchangeLevelData() {
        super();
        initializeServices();
    }
    
    /**
     * Initialize enterprise services.
     */
    private void initializeServices() {
        this.repository = new InMemoryOfferRepository();
        // OrderBooks are per-item, created on demand in orderBooksByItem map
        this.analyticsService = new MarketAnalyticsService(
            ModConfig.GrandExchange.priceHistorySize,
            this::markPersistenceDirty
        );
        this.rateLimitService = new RateLimitService(this::markPersistenceDirty);
        this.auditLog = new TradeAuditLog(
            ModConfig.GrandExchange.auditLogSize,
            ModConfig.GrandExchange.auditLogSize,
            ModConfig.GrandExchange.auditLogSize
        );
        this.notificationService = new NotificationService();
        this.performanceMetrics = new PerformanceMetrics();
        // TradeTransaction instances are created per-trade, no persistent field needed
        
        ModLogger.info("Grand Exchange Level Data initialized with enterprise services");
    }

    private void markPersistenceDirty() {
        persistenceTickCounter = 0;
    }

    public void requestPersistenceSave() {
        markPersistenceDirty();
    }

    /**
     * Completely reset all Grand Exchange data.
     * This is called when the GE system is toggled off and back on.
     * 
     * WARNING: This permanently deletes ALL offers, orders, inventories,
     * price history, and statistics. Items in offers are LOST.
     */
    public void resetAllData() {
        ModLogger.warn("=== GRAND EXCHANGE DATA RESET INITIATED ===");
        ModLogger.warn("Clearing %d inventories, %d offers, %d buy orders",
            inventories.size(), offers.size(), buyOrders.size());

        // Clear all data structures
        inventories.clear();
        offers.clear();
        offersByItem.clear();
        buyOrders.clear();
        buyOrdersByItem.clear();
        priceHistory.clear();
        orderBooksByItem.clear();

        // Reset ID generators
        nextOfferID.set(1);
        nextBuyOrderID.set(1);

        // Reset statistics
        totalInventoriesCreated = 0;
        totalOffersCreated = 0;
        totalTradesCompleted = 0;
        totalVolumeTraded = 0L;

        // Clear repository
        if (repository != null) {
            repository.clearAll();
        }

        // Clear audit log
        if (auditLog != null) {
            auditLog.clearAll();
        }

        // Clear diagnostics history
        diagnosticsHistory.clear();

        markPersistenceDirty();
        ModLogger.warn("=== GRAND EXCHANGE DATA RESET COMPLETE ===");
    }


    // ===== STATIC HELPERS =====
    
    
    /**
     * Get or create GrandExchangeLevelData for a level.
     * If a GE reset is pending (from re-enabling in settings), the data is wiped.
     */
    public static GrandExchangeLevelData getGrandExchangeData(Level level) {
        if (level == null || !level.isServer()) {
            return null;
        }

        GrandExchangeLevelData data = (GrandExchangeLevelData) level.getLevelData(DATA_KEY);
        if (data == null) {
            data = new GrandExchangeLevelData();
            level.addLevelData(DATA_KEY, data);
            ModLogger.debug("Created new GrandExchangeLevelData for level %s", level.getIdentifier());
        }
        
        // Check if a reset was requested (GE was toggled off then back on)
        if (medievalsim.config.ModConfig.Banking.grandExchangeResetPending) {
            ModLogger.warn("GE reset pending flag detected - wiping all GE data!");
            data.resetAllData();
            medievalsim.config.ModConfig.Banking.grandExchangeResetPending = false;
        }
        
        return data;
    }    // ===== INVENTORY MANAGEMENT =====
    
    /**
     * Get or create player's GE inventory.
     */
    public PlayerGEInventory getOrCreateInventory(long playerAuth) {
        return inventories.computeIfAbsent(playerAuth, auth -> {
            totalInventoriesCreated++;
            markPersistenceDirty();
            ModLogger.info("Created GE inventory for player auth=%d (total inventories: %d)", 
                auth, totalInventoriesCreated);
            return new PlayerGEInventory(auth);
        });
    }
    
    /**
     * Get player's GE inventory (returns null if doesn't exist).
     */
    public PlayerGEInventory getInventory(long playerAuth) {
        return inventories.get(playerAuth);
    }
    
    /**
     * Check if player has a GE inventory.
     */
    public boolean hasInventory(long playerAuth) {
        return inventories.containsKey(playerAuth);
    }
    
    /**
     * Get all player inventories (for market browsing).
     */
    public java.util.Collection<PlayerGEInventory> getAllInventories() {
        return inventories.values();
    }

    public boolean canApplySellSlotCount(int newSlotCount) {
        for (PlayerGEInventory inventory : inventories.values()) {
            if (!inventory.canApplySellSlotCount(newSlotCount)) {
                return false;
            }
        }
        return true;
    }

    public boolean canApplyBuySlotCount(int newSlotCount) {
        for (PlayerGEInventory inventory : inventories.values()) {
            if (!inventory.canApplyBuySlotCount(newSlotCount)) {
                return false;
            }
        }
        return true;
    }

    public void resizeAllSellInventories(int newSlotCount) {
        inventories.values().forEach(inv -> inv.resizeSellSlots(newSlotCount));
        markPersistenceDirty();
    }

    public void resizeAllBuyInventories(int newSlotCount) {
        inventories.values().forEach(inv -> inv.resizeBuySlots(newSlotCount));
        markPersistenceDirty();
    }
    
    // ===== OFFER MANAGEMENT =====
    
    /**
     * Create a new SELL offer from player's GE inventory slot.
     * @param playerAuth Player authentication ID
     * @param slot Inventory slot (0-9)
     * @param itemStringID Item being sold
     * @param quantity Quantity to sell
     * @param pricePerItem Price per item
     * @return The created offer, or null if failed
     */
    public GEOffer createSellOffer(long playerAuth, String playerName, int slot,
                                   String itemStringID, int quantity, int pricePerItem) {
        // Validate parameters
        if (!ModConfig.GrandExchange.isValidPrice(pricePerItem)) {
            ModLogger.warn("Invalid price for offer: %d (player auth=%d)", pricePerItem, playerAuth);
            return null;
        }
        
        if (quantity <= 0) {
            ModLogger.warn("Invalid quantity for offer: %d (player auth=%d)", quantity, playerAuth);
            return null;
        }
        
        // Get or create player's GE inventory
        PlayerGEInventory inventory = getOrCreateInventory(playerAuth);

        if (!rateLimitService.canCreateSellOffer(playerAuth)) {
            float remaining = rateLimitService.getRemainingCooldownForSellOffer(playerAuth);
            ModLogger.warn("Player %d rate limited when creating sell offer, %.1fs remaining", playerAuth, remaining);
            return null;
        }
        
        // Validate slot
        if (!inventory.isValidSellSlot(slot)) {
            slot = inventory.findAvailableSellSlot();
            if (slot < 0) {
                ModLogger.error("No reusable sell slots for player auth=%d", playerAuth);
                return null;
            }
        }
        
        // Check existing offer in slot
        GEOffer existingOffer = inventory.getSlotOffer(slot);
        if (existingOffer != null) {
            GEOffer.OfferState state = existingOffer.getState();
            ModLogger.info("[SERVER SELL] Slot %d has existing offer ID=%d, state=%s, enabled=%s, isActive=%s", 
                slot, existingOffer.getOfferID(), state, existingOffer.isEnabled(), existingOffer.isActive());
            
            // Check if this offer still exists in the offers map
            GEOffer offerFromMap = offers.get(existingOffer.getOfferID());
            if (offerFromMap == null) {
                ModLogger.warn("[SERVER SELL] Slot %d offer ID=%d NOT in offers map - orphaned reference! Cleaning up...", 
                    slot, existingOffer.getOfferID());
                cleanupOrphanedOffer(existingOffer, inventory, slot);
                existingOffer = null; // Treat as no offer
            } else if (offerFromMap != existingOffer) {
                ModLogger.warn("[SERVER SELL] Slot %d offer reference mismatch! Slot=%s, Map=%s", 
                    slot, existingOffer, offerFromMap);
                // Use the one from map as source of truth
                existingOffer = offerFromMap;
                inventory.setSlotOffer(slot, offerFromMap);
            }
        }
        
        if (existingOffer != null) {
            GEOffer.OfferState state = existingOffer.getState();
            
            // If offer is active/draft/partial, can't replace it
            if (existingOffer.isActive() || state == GEOffer.OfferState.DRAFT || state == GEOffer.OfferState.PARTIAL) {
                ModLogger.warn("[SERVER SELL] Slot %d already has an active offer (player auth=%d, state=%s, enabled=%s)", 
                    slot, playerAuth, state, existingOffer.isEnabled());
                return null;
            }
            
            // Offer is finished (COMPLETED/EXPIRED/CANCELLED) - clean it up properly
            ModLogger.info("[SERVER SELL] Removing finished offer ID=%d (state=%s) from slot %d to make room", 
                existingOffer.getOfferID(), state, slot);
            cleanupFinishedOffer(existingOffer);
            inventory.setSlotOffer(slot, null);
        }
        
        // Check max offers per player
        if (!inventory.canCreateSellOffer()) {
            ModLogger.warn("Player auth=%d has too many active sell offers (%d/%d)",
                playerAuth, inventory.getActiveOfferCount(), ModConfig.GrandExchange.maxActiveOffersPerPlayer);
            return null;
        }
        
        // Create the offer
        long offerID = nextOfferID.getAndIncrement();
        GEOffer offer = GEOffer.createSellOffer(offerID, playerAuth, playerName,
            slot, itemStringID, quantity, pricePerItem);
        
        if (offer == null) {
            return null;
        }
        
        // Store offer in DRAFT state (user must enable via checkbox)
        offers.put(offerID, offer);
        inventory.setSlotOffer(slot, offer);
        inventory.recordSellOfferCreated();
        rateLimitService.recordSellOfferCreation(playerAuth);
        totalOffersCreated++;
        
        // Save to repository
        repository.saveSellOffer(offer);
        
        // Note: Do NOT add to offersByItem index yet - only when enabled
        // Note: Do NOT activate offer - it starts in DRAFT state
        // Note: Do NOT match offers - only match when enabled
        
        ModLogger.info("Created DRAFT sell offer ID=%d: %s x%d @ %d coins (slot=%d, player=%s)",
            offerID, itemStringID, quantity, pricePerItem, slot, playerName);

        markPersistenceDirty();
        
        return offer;
    }
    
    /**
     * Clean up an orphaned offer reference (offer in slot but not in main map).
     * Removes from all data structures to prevent leaks.
     */
    private void cleanupOrphanedOffer(GEOffer offer, PlayerGEInventory inventory, int slot) {
        long offerID = offer.getOfferID();
        
        // Remove from slot
        inventory.setSlotOffer(slot, null);
        
        removeSellOfferFromIndexes(offer);

        // Remove from repository
        repository.deleteSellOffer(offerID);
        
        ModLogger.info("Cleaned up orphaned offer ID=%d from all data structures", offerID);
        markPersistenceDirty();
    }
    
    /**
     * Clean up a finished offer (COMPLETED/EXPIRED/CANCELLED).
     * Removes from all tracking structures.
     */
    private void cleanupFinishedOffer(GEOffer offer) {
        long offerID = offer.getOfferID();

        // Remove from main map
        offers.remove(offerID);

        // Remove from repository
        repository.deleteSellOffer(offerID);

        removeSellOfferFromIndexes(offer);

        ModLogger.debug("Cleaned up finished offer ID=%d (state=%s)", offerID, offer.getState());
        markPersistenceDirty();
    }
    
    /**
     * Cancel an offer (returns item to player inventory).
     */
    public boolean cancelOffer(long offerID) {
        return cancelOffer(null, offerID);
    }

    /**
     * Cancel an offer with optional Level to send client syncs.
     */
    public synchronized boolean cancelOffer(Level level, long offerID) {
        GEOffer offer = offers.get(offerID);
        if (offer == null) {
            return false;
        }

        if (!offer.isCancellable()) {
            ModLogger.warn("Offer ID=%d cannot be cancelled (state=%s)", offerID, offer.getState());
            return false;
        }

        offer.cancel();

        // Update inventory
        PlayerGEInventory inventory = inventories.get(offer.getPlayerAuth());
        if (inventory != null) {
            inventory.setSlotOffer(offer.getInventorySlot(), null);
            inventory.recordSellOfferCancelled();
        }

        refundOfferItems(level, offer, inventory, "Offer cancelled",
            inventory != null && inventory.isCollectionDepositToBankPreferred());

        removeSellOfferFromIndexes(offer);

        // Remove from repository and main map
        offers.remove(offerID);
        repository.deleteSellOffer(offerID);

        ModLogger.info("Cancelled offer ID=%d (player auth=%d, slot=%d)",
            offerID, offer.getPlayerAuth(), offer.getInventorySlot());

        // Notify player client if we have level context
        if (level != null) {
            notifyPlayerSellInventory(level, offer.getPlayerAuth());
            sendHistoryUpdate(level, offer.getPlayerAuth(), Collections.emptyList());
        }

        markPersistenceDirty();

        return true;
    }
    
    /**
     * Get an offer by ID.
     * Uses repository for consistent data access.
     */
    public GEOffer getOffer(long offerID) {
        // Try repository first, fallback to map for backwards compatibility
        return repository.findSellOfferById(offerID)
            .orElseGet(() -> offers.get(offerID));
    }
    
    /**
     * Get all active offers for an item.
     * Uses repository for consistent data access.
     */
    public List<GEOffer> getActiveOffersForItem(String itemStringID) {
        return repository.findActiveSellOffersByItem(itemStringID);
    }
    
    /**
     * Get all offers for a player.
     * Uses repository for consistent data access.
     */
    public List<GEOffer> getPlayerOffers(long playerAuth) {
        return repository.findSellOffersByPlayer(playerAuth);
    }
    
    /**
     * Get all active offers.
     * Uses repository for consistent data access.
     */
    public List<GEOffer> getAllActiveOffers() {
        return repository.findAllActiveSellOffers();
    }

    /**
     * Build a filtered + sorted + paginated view of the market for the client UI.
     */
    public MarketSnapshot buildMarketSnapshot(String filter, String category, int sortMode, int requestedPage) {
        List<GEOffer> activeOffers = new ArrayList<>(getAllActiveOffers());

        String normalizedFilter = filter == null ? "" : filter.trim();
        String normalizedCategory = (category == null || category.isBlank()) ? "all" : category.trim();

        if (!normalizedFilter.isEmpty()) {
            String lowerFilter = normalizedFilter.toLowerCase();
            activeOffers.removeIf(offer -> offer.getItemStringID() == null ||
                !offer.getItemStringID().toLowerCase().contains(lowerFilter));
        }

        if (!normalizedCategory.equalsIgnoreCase("all")) {
            activeOffers.removeIf(offer -> !matchesCategory(offer.getItemStringID(), normalizedCategory));
        }

        Comparator<GEOffer> comparator;
        switch (sortMode) {
            case 2: // Price high -> low
                comparator = Comparator.comparingInt(GEOffer::getPricePerItem).reversed();
                break;
            case 3: // Quantity high -> low
                comparator = Comparator.comparingInt(GEOffer::getQuantityRemaining).reversed();
                break;
            case 4: // Expiration approaching first
                comparator = Comparator.comparingLong(this::getComparableExpirationTime);
                break;
            case 1:
            default: // Price low -> high
                comparator = Comparator.comparingInt(GEOffer::getPricePerItem);
                break;
        }
        activeOffers.sort(comparator);

        int pageSize = Math.max(1, ModConfig.GrandExchange.maxListingsPerPage);
        int totalResults = activeOffers.size();
        int totalPages = totalResults == 0 ? 1 : (int) Math.ceil((double) totalResults / pageSize);
        int safePage = Math.max(0, Math.min(requestedPage, totalPages - 1));

        int startIndex = Math.min(totalResults, safePage * pageSize);
        int endIndex = Math.min(totalResults, startIndex + pageSize);

        List<MarketSnapshot.Entry> entries = new ArrayList<>();
        for (int i = startIndex; i < endIndex; i++) {
            GEOffer offer = activeOffers.get(i);
            entries.add(new MarketSnapshot.Entry(
                offer.getOfferID(),
                offer.getItemStringID(),
                offer.getQuantityTotal(),
                offer.getQuantityRemaining(),
                offer.getPricePerItem(),
                offer.getPlayerAuth(),
                offer.getPlayerName(),
                offer.getExpirationTime(),
                offer.getCreatedTime(),
                offer.getState()
            ));
        }

        MarketInsightsSummary insights = analyticsService.buildInsightsSummary(
            ModConfig.GrandExchange.marketInsightTopEntries
        );

        return new MarketSnapshot(safePage, totalPages, totalResults, pageSize,
            normalizedFilter, normalizedCategory, sortMode, entries, insights);
    }

    private boolean matchesCategory(String itemStringID, String targetCategoryId) {
        if (itemStringID == null || targetCategoryId == null || targetCategoryId.equalsIgnoreCase("all")) {
            return true;
        }

        Item item = ItemRegistry.getItem(itemStringID);
        if (item == null) {
            return false;
        }

        ItemCategory category = ItemCategory.getItemsCategory(item);
        ItemCategory current = category;
        while (current != null) {
            if (current.stringID != null && current.stringID.equalsIgnoreCase(targetCategoryId)) {
                return true;
            }
            current = current.parent;
        }
        return false;
    }

    private long getComparableExpirationTime(GEOffer offer) {
        long expiration = offer.getExpirationTime();
        return expiration == 0L ? Long.MAX_VALUE : expiration;
    }
    
    // ===== PRICE MATCHING & INSTANT TRADES =====
    
    /**
     * Attempt to match buy/sell offers for instant trades.
     * Currently supports SELL offers only (buy offers in Phase 10).
     */
    /**
     * Match offers for a specific item using the OrderBook service.
     * This is the new enterprise-grade matching engine (O(log n)).
     */
    public void matchOffers(String itemStringID) {
        if (!ModConfig.GrandExchange.enableInstantTrades) {
            return;
        }
        
        // Get or create order book for this item
        OrderBook itemOrderBook = new OrderBook(itemStringID);
        
        // Add all active sell offers for this item (take snapshot under lock)
        List<Long> sellOfferIDs = offersByItem.get(itemStringID);
        if (sellOfferIDs != null) {
            List<Long> sellSnapshot;
            synchronized (sellOfferIDs) {
                sellSnapshot = new ArrayList<>(sellOfferIDs);
            }
            for (Long offerID : sellSnapshot) {
                GEOffer offer = offers.get(offerID);
                if (offer != null && offer.isActive()) {
                    itemOrderBook.addSellOffer(offer);
                }
            }
        }
        
        // Add all active buy orders for this item
        List<Long> buyOrderIDs = buyOrdersByItem.get(itemStringID);
        if (buyOrderIDs != null) {
            List<Long> buySnapshot;
            synchronized (buyOrderIDs) {
                buySnapshot = new ArrayList<>(buyOrderIDs);
            }
            for (Long orderID : buySnapshot) {
                BuyOrder order = buyOrders.get(orderID);
                if (order != null && order.canMatch()) {
                    itemOrderBook.addBuyOrder(order);
                }
            }
        }
        
        // Match all buy orders and sell offers
        // Note: OrderBook doesn't have findAllMatches(), we need to match per-offer/order
        // For now, this method is a stub - use matchSellOffersNew/matchBuyOrdersNew instead
        ModLogger.debug("matchOffers() called for item %s (use specific matching methods)", itemStringID);
    }
    
    // ===== SELL OFFER ENABLE/DISABLE =====
    
    /**
     * Enable a sell offer (checkbox checked).
     * Moves offer from DRAFT → ACTIVE, adds to market.
     * NEW: Uses rate limiting and OrderBook-based matching.
     * Thread-safe: synchronized to prevent race conditions during matching.
     */
    public synchronized SellActionResultCode enableSellOffer(Level level, long playerAuth, int slotIndex) {
        if (!rateLimitService.canToggleSellOffer(playerAuth)) {
            ModLogger.warn("Player %d must wait before toggling sell offers again", playerAuth);
            return SellActionResultCode.TOGGLE_COOLDOWN;
        }

        PlayerGEInventory inventory = getInventory(playerAuth);
        if (inventory == null) {
            ModLogger.warn("No GE inventory for player auth=%d", playerAuth);
            return SellActionResultCode.UNKNOWN_FAILURE;
        }

        GEOffer offer = inventory.getSlotOffer(slotIndex);
        if (offer == null) {
            ModLogger.warn("No sell offer in slot %d for player auth=%d", slotIndex, playerAuth);
            return SellActionResultCode.NO_OFFER_IN_SLOT;
        }

        if (offer.getState() != GEOffer.OfferState.DRAFT) {
            ModLogger.warn("Sell offer ID=%d cannot be enabled (state=%s)", offer.getOfferID(), offer.getState());
            return SellActionResultCode.OFFER_STATE_LOCKED;
        }

        if (!offer.enable()) {
            return SellActionResultCode.UNKNOWN_FAILURE;
        }

        List<Long> itemList = offersByItem.computeIfAbsent(offer.getItemStringID(), k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (itemList) {
            if (!itemList.contains(offer.getOfferID())) {
                itemList.add(offer.getOfferID());
            }
        }

        repository.saveSellOffer(offer);
        rateLimitService.recordSellToggle(playerAuth);

        ModLogger.info("Enabled sell offer ID=%d (player auth=%d, slot=%d)",
            offer.getOfferID(), playerAuth, slotIndex);

        if (ModConfig.GrandExchange.enableInstantTrades) {
            OrderBook itemOrderBook = orderBooksByItem.computeIfAbsent(offer.getItemStringID(), k -> new OrderBook(k));
            itemOrderBook.addSellOffer(offer);
            List<OrderBook.Match> matches = itemOrderBook.findMatchesForSellOffer(offer);
            ModLogger.debug("OrderBook found %d matches for sell offer ID=%d", matches.size(), offer.getOfferID());
            for (OrderBook.Match match : matches) {
                BuyOrder buyOrder = match.getBuyOrder();
                if (buyOrder == null || !buyOrder.canMatch()) continue;

                synchronized (offer) {
                    if (!offer.isActive() || offer.getQuantityRemaining() <= 0) break;
                    executeTradeWithTransaction(level, buyOrder, offer, match.getQuantity(), match.getExecutionPrice());
                }
            }
        }

        notifyPlayerSellInventory(level, playerAuth);
        markPersistenceDirty();
        return SellActionResultCode.SUCCESS;
    }
    
    /**
     * Disable a sell offer (checkbox unchecked).
     * Moves offer from ACTIVE → DRAFT, removes from market.
     * Thread-safe: synchronized to prevent race conditions.
     */
    public synchronized SellActionResultCode disableSellOffer(Level level, long playerAuth, int slotIndex) {
        if (!rateLimitService.canToggleSellOffer(playerAuth)) {
            ModLogger.warn("Player %d must wait before toggling sell offers again", playerAuth);
            return SellActionResultCode.TOGGLE_COOLDOWN;
        }

        PlayerGEInventory inventory = getInventory(playerAuth);
        if (inventory == null) {
            ModLogger.warn("No GE inventory for player auth=%d", playerAuth);
            return SellActionResultCode.UNKNOWN_FAILURE;
        }

        GEOffer offer = inventory.getSlotOffer(slotIndex);
        if (offer == null) {
            ModLogger.warn("No sell offer in slot %d for player auth=%d", slotIndex, playerAuth);
            return SellActionResultCode.NO_OFFER_IN_SLOT;
        }

        GEOffer.OfferState state = offer.getState();
        if (state != GEOffer.OfferState.ACTIVE && state != GEOffer.OfferState.PARTIAL) {
            ModLogger.warn("Sell offer ID=%d cannot be disabled (state=%s)", offer.getOfferID(), offer.getState());
            return SellActionResultCode.OFFER_STATE_LOCKED;
        }

        removeSellOfferFromIndexes(offer);

        if (!offer.disable()) {
            return SellActionResultCode.UNKNOWN_FAILURE;
        }

        repository.saveSellOffer(offer);

        ModLogger.info("Disabled sell offer ID=%d (player auth=%d, slot=%d)",
            offer.getOfferID(), playerAuth, slotIndex);

        rateLimitService.recordSellToggle(playerAuth);
        notifyPlayerSellInventory(level, playerAuth);
        markPersistenceDirty();
        return SellActionResultCode.SUCCESS;
    }
    
    // ===== BUY ORDER MANAGEMENT =====
    
    /**
     * Create a new buy order (DRAFT state, not yet enabled).
     * Player must enable it to activate and escrow coins.
     */
    public BuyOrder createBuyOrder(long playerAuth,
                                   String playerName,
                                   int slotIndex,
                                   String itemStringID,
                                   int quantity,
                                   int pricePerItem,
                                   int durationDays) {
        // Validation
        PlayerGEInventory inventory = getOrCreateInventory(playerAuth);
        if (!rateLimitService.canCreateBuyOrder(playerAuth)) {
            float remaining = rateLimitService.getRemainingCooldownForBuyOrder(playerAuth);
            ModLogger.warn("Player %d rate limited when creating buy order, %.1fs remaining", playerAuth, remaining);
            return null;
        }
        
        if (slotIndex < 0 || slotIndex >= ModConfig.GrandExchange.buyOrderSlots) {
            ModLogger.warn("Invalid buy order slot %d for player auth=%d", slotIndex, playerAuth);
            return null;
        }
        
        BuyOrder existingOrder = inventory.getBuyOrder(slotIndex);
        if (existingOrder != null) {
            // If existing order is active, can't replace it
            if (existingOrder.isActive()) {
                ModLogger.warn("Slot %d already has an active buy order for player auth=%d", slotIndex, playerAuth);
                return null;
            }
            
            // If existing order is DRAFT, reconfigure it instead of creating new
            if (existingOrder.getState() == BuyOrder.BuyOrderState.DRAFT) {
                if (existingOrder.configure(itemStringID, quantity, pricePerItem, durationDays)) {
                    ModLogger.info("Reconfigured existing DRAFT buy order ID=%d in slot %d", 
                        existingOrder.getOrderID(), slotIndex);
                    return existingOrder;
                } else {
                    ModLogger.warn("Failed to reconfigure existing buy order in slot %d", slotIndex);
                    return null;
                }
            }
            
            // For completed/cancelled/expired orders, remove them and create new
            ModLogger.debug("Removing %s buy order ID=%d from slot %d", 
                existingOrder.getState(), existingOrder.getOrderID(), slotIndex);
            buyOrders.remove(existingOrder.getOrderID());
            inventory.setBuyOrder(slotIndex, null);
        }
        
        // Create buy order (DRAFT state)
        long orderID = nextBuyOrderID.getAndIncrement();
        String resolvedPlayerName = (playerName == null || playerName.isBlank()) ? "Player" : playerName;
        BuyOrder order = new BuyOrder(orderID, playerAuth, resolvedPlayerName, slotIndex);
        
        // Configure the order
        if (!order.configure(itemStringID, quantity, pricePerItem, durationDays)) {
            ModLogger.warn("Failed to configure buy order for player auth=%d", playerAuth);
            return null;
        }
        
        // Store in inventory
        inventory.setBuyOrder(slotIndex, order);
        inventory.recordBuyOrderCreated();
        rateLimitService.recordBuyOrderCreation(playerAuth);
        
        // Store in global map (but NOT in buyOrdersByItem index until enabled)
        buyOrders.put(orderID, order);
        
        ModLogger.info("Created buy order ID=%d (player auth=%d, slot=%d, item=%s, qty=%d, price=%d) [DRAFT]",
            orderID, playerAuth, slotIndex, itemStringID, quantity, pricePerItem);
        markPersistenceDirty();
        
        return order;
    }
    
    /**
     * Enable a buy order (checkbox checked).
     * Deducts coins from player's bank and adds to escrow.
     * Returns true if successful, false if insufficient funds.
     * NEW: Uses rate limiting to prevent spam.
     */
    public synchronized boolean enableBuyOrder(Level level, long playerAuth, int slotIndex) {
        PlayerGEInventory inventory = getInventory(playerAuth);
        if (inventory == null) {
            ModLogger.warn("No GE inventory for player auth=%d", playerAuth);
            return false;
        }
        
        BuyOrder order = inventory.getBuyOrder(slotIndex);
        if (order == null) {
            ModLogger.warn("No buy order in slot %d for player auth=%d", slotIndex, playerAuth);
            return false;
        }
        
        if (order.getState() != BuyOrder.BuyOrderState.DRAFT) {
            ModLogger.warn("Buy order ID=%d cannot be enabled (state=%s)", order.getOrderID(), order.getState());
            return false;
        }

        if (rateLimitService != null && !rateLimitService.canToggleBuyOrder(playerAuth)) {
            float remaining = rateLimitService.getRemainingCooldownForBuyToggle(playerAuth);
            ModLogger.warn("Player %d rate limited when enabling buy order, %.1fs remaining", playerAuth, remaining);
            return false;
        }
        
        // Calculate required coins
        int coinsRequired = order.getTotalCoinsRequired();
        
        // Get player's bank and check balance
        BankingLevelData bankingData = BankingLevelData.getBankingData(level);
        if (bankingData == null) {
            ModLogger.error("Banking system not available for buy order escrow");
            return false;
        }
        
        PlayerBank bank = bankingData.getOrCreateBank(playerAuth);
        if (bank.getCoins() < coinsRequired) {
            ModLogger.warn("Player auth=%d has insufficient bank coins for buy order: has %d, needs %d",
                playerAuth, bank.getCoins(), coinsRequired);
            return false;
        }
        
        // Deduct coins from bank
        if (!bank.removeCoins(coinsRequired)) {
            ModLogger.error("Failed to deduct %d coins from bank for player auth=%d", coinsRequired, playerAuth);
            return false;
        }
        
        // Add to escrow
        inventory.addCoinsToEscrow(coinsRequired);
        
        // Enable the order
        order.enable();
        
        // Add to item index for matching (use synchronized list)
        List<Long> orderList = buyOrdersByItem.computeIfAbsent(order.getItemStringID(), k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (orderList) {
            if (!orderList.contains(order.getOrderID())) {
                orderList.add(order.getOrderID());
            }
        }
        
        // Save to repository
        repository.saveBuyOrder(order);
        
        ModLogger.info("Enabled buy order ID=%d (player auth=%d, slot=%d, escrowed %d coins)",
            order.getOrderID(), playerAuth, slotIndex, coinsRequired);
        
        // Attempt to match with existing sell offers using new OrderBook system
        if (ModConfig.GrandExchange.enableInstantTrades) {
            // Add to persistent OrderBook and match
            OrderBook itemOrderBook = orderBooksByItem.computeIfAbsent(order.getItemStringID(), k -> new OrderBook(k));
            itemOrderBook.addBuyOrder(order);
            List<OrderBook.Match> matches = itemOrderBook.findMatchesForBuyOrder(order);
            ModLogger.debug("OrderBook found %d matches for buy order ID=%d", matches.size(), order.getOrderID());
            for (OrderBook.Match match : matches) {
                GEOffer sellOffer = match.getSellOffer();
                if (sellOffer == null || !sellOffer.isActive()) continue;

                synchronized (order) {
                    if (!order.canMatch()) break;
                    executeTradeWithTransaction(level, order, sellOffer, match.getQuantity(), match.getExecutionPrice());
                }
            }
        }
        
        // Notify player client so buy order UI stays in sync (if online)
        notifyPlayerBuyOrders(level, playerAuth);
        if (rateLimitService != null) {
            rateLimitService.recordBuyToggle(playerAuth);
        }
        markPersistenceDirty();

        return true;
    }
    
    /**
     * Disable a buy order (checkbox unchecked).
     * Refunds escrowed coins back to player's bank.
     */
    public synchronized boolean disableBuyOrder(Level level, long playerAuth, int slotIndex) {
        // Check rate limit to prevent rapid enable/disable spam
        if (rateLimitService != null && !rateLimitService.canToggleBuyOrder(playerAuth)) {
            float remaining = rateLimitService.getRemainingCooldownForBuyToggle(playerAuth);
            ModLogger.warn("Player %d rate limited for order state change, cooldown: %.1f seconds", playerAuth, remaining);
            return false;
        }
        
        PlayerGEInventory inventory = getInventory(playerAuth);
        if (inventory == null) {
            ModLogger.warn("No GE inventory for player auth=%d", playerAuth);
            return false;
        }
        
        BuyOrder order = inventory.getBuyOrder(slotIndex);
        if (order == null) {
            ModLogger.warn("No buy order in slot %d for player auth=%d", slotIndex, playerAuth);
            return false;
        }
        
        BuyOrder.BuyOrderState state = order.getState();
        if (state != BuyOrder.BuyOrderState.ACTIVE && state != BuyOrder.BuyOrderState.PARTIAL) {
            ModLogger.warn("Buy order ID=%d cannot be disabled (state=%s)", order.getOrderID(), order.getState());
            return false;
        }
        
        // Calculate coins to refund (only remaining quantity)
        int coinsToRefund = order.getTotalCoinsRequired();

        removeBuyOrderFromIndexes(order);
        
        // Disable the order
        order.disable();
        
        // Refund coins to bank
        if (coinsToRefund > 0) {
            int refunded = refundBuyOrderEscrow(level, inventory, order, true);
            ModLogger.info("Disabled buy order ID=%d (player auth=%d, slot=%d, refunded %d coins)",
                order.getOrderID(), playerAuth, slotIndex, refunded);
        }
        
        // Notify player client so buy order UI stays in sync (if online)
        notifyPlayerBuyOrders(level, playerAuth);
        if (rateLimitService != null) {
            rateLimitService.recordBuyToggle(playerAuth);
        }
        markPersistenceDirty();

        return true;
    }
    
    /**
     * Cancel a buy order (remove completely).
     * Refunds any escrowed coins and clears the slot.
     */
    public synchronized boolean cancelBuyOrder(Level level, long playerAuth, int slotIndex) {
        PlayerGEInventory inventory = getInventory(playerAuth);
        if (inventory == null) {
            return false;
        }
        
        BuyOrder order = inventory.getBuyOrder(slotIndex);
        if (order == null) {
            return false;
        }
        
        // If enabled/active, refund coins first
        if (order.isActive()) {
            refundBuyOrderEscrow(level, inventory, order, false);
            removeBuyOrderFromIndexes(order);
        }
        
        // Cancel the order
        order.cancel();
        
        // Clear from inventory slot
        inventory.setBuyOrder(slotIndex, null);
        inventory.recordBuyOrderCancelled();
        
        // Remove from global map
        buyOrders.remove(order.getOrderID());
        
        ModLogger.info("Cancelled buy order ID=%d (player auth=%d, slot=%d)",
            order.getOrderID(), playerAuth, slotIndex);
        this.markPersistenceDirty();
        
        // Notify player client so buy order UI stays in sync (if online)
        notifyPlayerBuyOrders(level, playerAuth);

        return true;
    }

    /**
     * Process a manual market purchase (Tab 0) using the shared trade transaction pipeline.
     * Buys the entire remaining quantity of the specified offer at the asking price.
     */
    public synchronized boolean processMarketPurchase(Level level, long buyerAuth, String buyerName, long offerID, int requestedQuantity) {
        if (level == null) {
            ModLogger.warn("Cannot process market purchase without level context");
            return false;
        }

        PlayerGEInventory buyerInventory = getOrCreateInventory(buyerAuth);
        if (buyerInventory == null) {
            ModLogger.warn("No GE inventory for buyer auth=%d", buyerAuth);
            return false;
        }

        GEOffer offer = getOffer(offerID);
        if (offer == null || !offer.isActive()) {
            ModLogger.warn("Offer ID=%d not available for purchase", offerID);
            return false;
        }

        if (!offer.isEnabled()) {
            ModLogger.warn("Offer ID=%d is disabled", offerID);
            return false;
        }

        if (offer.getPlayerAuth() == buyerAuth) {
            ModLogger.warn("Player auth=%d attempted to buy their own offer ID=%d", buyerAuth, offerID);
            return false;
        }

        int availableQuantity = offer.getQuantityRemaining();
        if (availableQuantity <= 0) {
            ModLogger.warn("Offer ID=%d has no remaining quantity", offerID);
            return false;
        }

        int quantity = requestedQuantity <= 0 ? availableQuantity : requestedQuantity;
        quantity = Math.min(quantity, availableQuantity);
        if (quantity <= 0) {
            ModLogger.warn("Requested quantity for offer ID=%d resolved to zero", offerID);
            return false;
        }

        int pricePerItem = offer.getPricePerItem();
        long totalCostLong = (long) pricePerItem * (long) quantity;
        if (totalCostLong <= 0L) {
            ModLogger.warn("Invalid total cost for offer ID=%d (qty=%d, price=%d)", offerID, quantity, pricePerItem);
            return false;
        }
        if (totalCostLong > Integer.MAX_VALUE) {
            ModLogger.warn("Total cost exceeds escrow limit for offer ID=%d (cost=%d)", offerID, totalCostLong);
            return false;
        }
        int totalCost = (int) totalCostLong;

        BankingLevelData bankingData = BankingLevelData.getBankingData(level);
        if (bankingData == null) {
            ModLogger.error("Banking system unavailable for market purchase");
            return false;
        }

        PlayerBank buyerBank = bankingData.getOrCreateBank(buyerAuth);
        if (buyerBank == null) {
            ModLogger.error("No bank found for player auth=%d", buyerAuth);
            return false;
        }

        if (buyerBank.getCoins() < totalCostLong) {
            ModLogger.warn("Player auth=%d lacks coins for purchase: has %d, needs %d",
                buyerAuth, buyerBank.getCoins(), totalCostLong);
            return false;
        }

        if (!buyerBank.removeCoins(totalCostLong)) {
            ModLogger.error("Failed to remove %d coins from bank for player auth=%d", totalCostLong, buyerAuth);
            return false;
        }

        buyerInventory.addCoinsToEscrow(totalCost);

        long orderID = nextBuyOrderID.getAndIncrement();
        String resolvedBuyerName = (buyerName != null && !buyerName.isEmpty()) ? buyerName : "Player";
        BuyOrder instantOrder = new BuyOrder(orderID, buyerAuth, resolvedBuyerName, -1);
        if (!instantOrder.configure(offer.getItemStringID(), quantity, pricePerItem, 1)) {
            buyerInventory.removeCoinsFromEscrow(totalCost);
            buyerBank.addCoins(totalCostLong);
            ModLogger.warn("Failed to configure instant buy order for offer ID=%d", offerID);
            return false;
        }

        if (!instantOrder.enable()) {
            buyerInventory.removeCoinsFromEscrow(totalCost);
            buyerBank.addCoins(totalCostLong);
            ModLogger.warn("Failed to enable instant buy order for offer ID=%d", offerID);
            return false;
        }

        TradeTransaction.TradeResult tradeResult = executeTradeWithTransaction(level, instantOrder, offer, quantity, pricePerItem);
        if (tradeResult == null) {
            buyerInventory.removeCoinsFromEscrow(totalCost);
            buyerBank.addCoins(totalCostLong);
            ModLogger.warn("Trade transaction failed for manual purchase (offer ID=%d)", offerID);
            return false;
        }

        ModLogger.info("Manual market purchase: buyer auth=%d bought offer ID=%d (qty=%d, price=%d)",
            buyerAuth, offerID, quantity, pricePerItem);

        return true;
    }

    // ===== TRADE EXECUTION & COLLECTION MANAGEMENT =====

    /**
     * Execute a trade using the TradeTransaction service for atomic operations.
     * Uses prepare/commit/rollback pattern for atomicity.
     */
    private TradeTransaction.TradeResult executeTradeWithTransaction(Level level, BuyOrder buyOrder, GEOffer sellOffer, 
                                            int quantity, int pricePerItem) {
        // CRITICAL: Prevent self-trading exploit
        if (buyOrder.getPlayerAuth() == sellOffer.getPlayerAuth()) {
            ModLogger.warn("Blocked self-trade attempt: player auth=%d trying to buy their own offer",
                buyOrder.getPlayerAuth());
            return null;
        }
        
        long startTime = System.currentTimeMillis();
        
        // Create transaction
        TradeTransaction transaction = new TradeTransaction(level, buyOrder, sellOffer, quantity, pricePerItem);
        
        // Phase 1: Prepare
        if (!transaction.prepare()) {
            ModLogger.error("Trade preparation failed: %s", transaction.getFailureReason());
            logTradeFailure("prepare", buyOrder, sellOffer, quantity, pricePerItem, transaction);
            return null;
        }
        
        // Phase 2: Commit (returns TradeResult or null on failure)
        TradeTransaction.TradeResult result = transaction.commit();
        if (result == null) {
            ModLogger.error("Trade commit failed: %s", transaction.getFailureReason());
            logTradeFailure("commit", buyOrder, sellOffer, quantity, pricePerItem, transaction);
            return null;
        }
        
        long executionTime = System.currentTimeMillis() - startTime;
        boolean isEphemeralOrder = buyOrder.getSlotIndex() < 0;
        
        // Record trade in enterprise services
        if (ModConfig.GrandExchange.enablePerformanceMetrics) {
            performanceMetrics.recordTrade(sellOffer.getItemStringID(), quantity, result.getTotalCoins(), executionTime);
        }
        
        analyticsService.recordTrade(result);
        auditLog.logTrade(result);
        
        // Send notifications with individual parameters (not TradeResult object)
        int totalCoins = result.getTotalCoins();
        if (!isEphemeralOrder) {
            notificationService.notifyBuyOrderFilled(buyOrder, quantity, totalCoins);
        }
        
        if (sellOffer.getQuantityRemaining() == 0) {
            notificationService.notifyOfferFilled(sellOffer, sellOffer.getQuantityTotal(), 
                (int)sellOffer.getTotalCoinsReceived());
        } else {
            notificationService.notifyOfferPartiallyFilled(sellOffer, quantity, pricePerItem, totalCoins);
        }

        PlayerGEInventory sellerInventory = getInventory(sellOffer.getPlayerAuth());
        SellOfferSaleEvent saleEvent = new SellOfferSaleEvent(
            sellOffer.getInventorySlot(),
            sellOffer.getItemStringID(),
            quantity,
            sellOffer.getQuantityRemaining(),
            pricePerItem,
            System.currentTimeMillis()
        );
        notifyPlayerSaleEvent(level, sellOffer.getPlayerAuth(), saleEvent);

        // Create seller history entry (sale)
        if (sellerInventory != null) {
            SaleNotification saleNotification = new SaleNotification(
                sellOffer.getItemStringID(),
                quantity,
                pricePerItem,
                totalCoins,
                sellOffer.getQuantityRemaining() > 0,
                buyOrder.getPlayerName(),
                true  // isSale = true for seller
            );
            sellerInventory.addSaleNotification(saleNotification);
            HistoryEntrySnapshot entrySnapshot = toHistoryEntrySnapshot(saleNotification);
            List<HistoryEntrySnapshot> deltaEntries = entrySnapshot == null
                ? Collections.emptyList()
                : List.of(entrySnapshot);
            sendHistoryUpdate(level, sellOffer.getPlayerAuth(), deltaEntries);
        }

        // Create buyer history entry (purchase) - only for non-ephemeral orders
        if (!isEphemeralOrder) {
            PlayerGEInventory buyerInventory = getInventory(buyOrder.getPlayerAuth());
            if (buyerInventory != null) {
                SaleNotification purchaseNotification = new SaleNotification(
                    sellOffer.getItemStringID(),
                    quantity,
                    pricePerItem,
                    totalCoins,
                    buyOrder.getQuantityRemaining() > 0,
                    sellOffer.getPlayerName(),
                    false  // isSale = false for buyer (this is a purchase)
                );
                buyerInventory.addSaleNotification(purchaseNotification);
                HistoryEntrySnapshot entrySnapshot = toHistoryEntrySnapshot(purchaseNotification);
                List<HistoryEntrySnapshot> deltaEntries = entrySnapshot == null
                    ? Collections.emptyList()
                    : List.of(entrySnapshot);
                sendHistoryUpdate(level, buyOrder.getPlayerAuth(), deltaEntries);
            }
        }
        
        // Update indexes if orders/offers are complete
        if (buyOrder.getState() == BuyOrder.BuyOrderState.COMPLETED) {
            removeBuyOrderFromIndexes(buyOrder);
        }

        if (sellOffer.getState() == GEOffer.OfferState.COMPLETED) {
            removeSellOfferFromIndexes(sellOffer);
        }
        
        // Save to repository
        if (!isEphemeralOrder) {
            repository.saveBuyOrder(buyOrder);
        }
        repository.saveSellOffer(sellOffer);
        
        // Update legacy statistics
        totalTradesCompleted++;
        totalVolumeTraded += result.getTotalCoins();
        recordSale(sellOffer.getItemStringID(), pricePerItem, quantity, result.getTotalCoins());
        
        ModLogger.info("Trade executed with transaction service: buy order ID=%d + sell offer ID=%d, " +
            "qty=%d, price=%d/ea, execution time=%dms",
            buyOrder.getOrderID(), sellOffer.getOfferID(), quantity, pricePerItem, executionTime);

        notifyPlayerCollectionBox(level, buyOrder.getPlayerAuth());
        markPersistenceDirty();
        return result;
    }

    private void logTradeFailure(String phase,
                                 BuyOrder buyOrder,
                                 GEOffer sellOffer,
                                 int quantity,
                                 int pricePerItem,
                                 TradeTransaction transaction) {
        String reason = transaction != null && transaction.getFailureReason() != null
            ? transaction.getFailureReason()
            : "unknown";
        long buyOrderId = buyOrder != null ? buyOrder.getOrderID() : -1L;
        String buyState = buyOrder != null ? buyOrder.getState().name() : "null";
        int buyRemaining = buyOrder != null ? buyOrder.getQuantityRemaining() : -1;
        long buyAuth = buyOrder != null ? buyOrder.getPlayerAuth() : -1L;

        long sellOfferId = sellOffer != null ? sellOffer.getOfferID() : -1L;
        String sellState = sellOffer != null ? sellOffer.getState().name() : "null";
        int sellRemaining = sellOffer != null ? sellOffer.getQuantityRemaining() : -1;
        long sellAuth = sellOffer != null ? sellOffer.getPlayerAuth() : -1L;

        ModLogger.error(
            "[GE TRADE FAILURE] phase=%s reason=%s buyOrder{id=%d,state=%s,remaining=%d,price=%d,auth=%d} " +
                "sellOffer{id=%d,state=%s,remaining=%d,price=%d,auth=%d} qty=%d execPrice=%d",
            phase,
            reason,
            buyOrderId,
            buyState,
            buyRemaining,
            buyOrder != null ? buyOrder.getPricePerItem() : -1,
            buyAuth,
            sellOfferId,
            sellState,
            sellRemaining,
            sellOffer != null ? sellOffer.getPricePerItem() : -1,
            sellAuth,
            quantity,
            pricePerItem
        );
    }

    
    /**
     * Collect an item from player's collection box.
     * Transfers to player's inventory (via container).
     */
    public CollectionItem collectFromCollectionBox(long playerAuth, int index) {
        PlayerGEInventory inventory = getInventory(playerAuth);
        if (inventory == null) {
            return null;
        }
        
        CollectionItem removed = inventory.removeFromCollectionBox(index);
        if (removed != null) {
            markPersistenceDirty();
        }
        return removed;
    }

    public void setCollectionDepositPreference(long playerAuth, boolean preferBank) {
        PlayerGEInventory inventory = getOrCreateInventory(playerAuth);
        inventory.setCollectionDepositToBankPreferred(preferBank);
        markPersistenceDirty();
    }

    public CollectionPaginator.Page getCollectionPage(long playerAuth, int requestedPage) {
        PlayerGEInventory inventory = getOrCreateInventory(playerAuth);
        CollectionPaginator.Page page = CollectionPaginator.paginate(
            inventory.getCollectionBox(),
            requestedPage
        );
        inventory.setCollectionPageIndex(page.getPageIndex());
        return page;
    }

    public CollectionPaginator.Page getCollectionPage(long playerAuth) {
        PlayerGEInventory inventory = getOrCreateInventory(playerAuth);
        return getCollectionPage(playerAuth, inventory.getCollectionPageIndex());
    }
    
    /**
     * Collect all items from collection box to bank.
     * Returns number of items successfully transferred.
     */
    public int collectAllToBank(Level level, long playerAuth) {
        PlayerGEInventory inventory = getInventory(playerAuth);
        if (inventory == null) {
            return 0;
        }
        
        BankingLevelData bankingData = BankingLevelData.getBankingData(level);
        if (bankingData == null) {
            return 0;
        }
        
        PlayerBank bank = bankingData.getOrCreateBank(playerAuth);
        int transferred = 0;

        for (int i = inventory.getCollectionBoxSize() - 1; i >= 0; i--) {
            CollectionItem item = inventory.removeFromCollectionBox(i);
            if (item == null) {
                continue;
            }

            InventoryItem invItem = item.toInventoryItem();
            if (bank.getInventory().addItem(level, null, invItem, "geCollection", null)) {
                transferred++;
            } else {
                inventory.insertIntoCollectionBox(i, item);
            }
        }

        ModLogger.info("Collected %d items from collection box to bank for player auth=%d", transferred, playerAuth);
        notifyPlayerCollectionBox(level, playerAuth);
        if (transferred > 0) {
            markPersistenceDirty();
        }
        return transferred;
    }
    
    /**
     * Get a buy order by ID.
     */
    public BuyOrder getBuyOrder(long orderID) {
        return buyOrders.get(orderID);
    }
    
    /**
     * Get all active buy orders for an item.
     */
    public List<BuyOrder> getActiveBuyOrdersForItem(String itemStringID) {
        List<Long> orderIDs = buyOrdersByItem.get(itemStringID);
        if (orderIDs == null) {
            return new ArrayList<>();
        }

        List<Long> snapshot;
        synchronized (orderIDs) {
            snapshot = new ArrayList<>(orderIDs);
        }

        return snapshot.stream()
            .map(buyOrders::get)
            .filter(o -> o != null && o.canMatch())
            .sorted(Comparator.comparingInt(BuyOrder::getPricePerItem).reversed())
            .collect(Collectors.toList());
    }
    
    // ===== CHAT NOTIFICATIONS =====
    
    // chat notification helper removed; use NotificationService for player notifications
    
    // ===== PRICE TRACKING =====
    
    /**
     * Record a sale price for an item.
     */
    public void recordSale(String itemStringID, int pricePerItem, int quantity, long totalCoins) {
        if (!ModConfig.GrandExchange.enablePriceTracking) {
            return;
        }
        
        List<Integer> history = priceHistory.computeIfAbsent(itemStringID, k -> new ArrayList<>());
        history.add(pricePerItem);

        // Trim history if too long
        int maxHistory = ModConfig.GrandExchange.priceHistorySize;
        if (history.size() > maxHistory) {
            history.remove(0);
        }
        
        // Update statistics
        totalTradesCompleted++;
        totalVolumeTraded += totalCoins;
        
        ModLogger.debug("Recorded sale: %s x%d @ %d coins (total: %d coins)",
            itemStringID, quantity, pricePerItem, totalCoins);
        markPersistenceDirty();
    }

    /**
     * Get the average "going price" for an item based on recent sales.
     * @return Average price, or 0 if no history
     */
    public int getAveragePrice(String itemStringID) {
        List<Integer> history = priceHistory.get(itemStringID);
        if (history == null || history.isEmpty()) {
            return 0;
        }

        // Calculate average of recent sales
        long sum = 0;
        for (int price : history) {
            sum += price;
        }
        return (int)(sum / history.size());
    }

    /**
     * Get the lowest current offer price for an item.
     * @return Lowest price, or 0 if no offers
     */
    public int getLowestPrice(String itemStringID) {
        return getActiveOffersForItem(itemStringID).stream()
            .mapToInt(GEOffer::getPricePerItem)
            .min()
            .orElse(0);
    }
    
    /**
     * Get the highest current offer price for an item.
     * @return Highest price, or 0 if no offers
     */
    public int getHighestPrice(String itemStringID) {
        return getActiveOffersForItem(itemStringID).stream()
            .mapToInt(GEOffer::getPricePerItem)
            .max()
            .orElse(0);
    }

    // ===== STATISTICS =====
    
    public long getTotalInventoriesCreated() {
        return totalInventoriesCreated;
    }
    
    public long getTotalOffersCreated() {
        return totalOffersCreated;
    }
    
    public long getTotalTradesCompleted() {
        return totalTradesCompleted;
    }
    
    public long getTotalVolumeTraded() {
        return totalVolumeTraded;
    }
    
    public int getActiveOfferCount() {
        return (int) offers.values().stream().filter(GEOffer::isActive).count();
    }
    
    // ===== SERVICE LAYER ACCESS =====
    
    /**
     * Get the order book service for efficient offer matching.
     */
    public java.util.Map<String, OrderBook> getOrderBooksByItem() {
        return orderBooksByItem;
    }
    
    /**
     * Get the market analytics service for price discovery.
     */
    public MarketAnalyticsService getAnalyticsService() {
        return analyticsService;
    }
    
    /**
     * Get the trade audit log for transaction history and fraud detection.
     */
    public TradeAuditLog getAuditLog() {
        return auditLog;
    }
    
    /**
     * Get the performance metrics service for market health monitoring.
     */
    public PerformanceMetrics getPerformanceMetrics() {
        return performanceMetrics;
    }
    
    /**
     * Get the notification service for player alerts.
     */
    public NotificationService getNotificationService() {
        return notificationService;
    }
    
    /**
     * Get the rate limit service for spam prevention.
     */
    public RateLimitService getRateLimitService() {
        return rateLimitService;
    }

    // ===== DIAGNOSTICS & PERSISTENCE =====

    public DiagnosticsReport buildDiagnosticsReport() {
        java.util.Collection<PlayerGEInventory> inventoryValues = getAllInventories();
        int activeInventories = inventoryValues.size();
        long backlogEntries = 0L;
        long backlogPlayers = 0L;
        long playersWithOffers = 0L;
        long totalEscrow = 0L;
        for (PlayerGEInventory inventory : inventoryValues) {
            int collectionSize = inventory.getCollectionBoxSize();
            backlogEntries += collectionSize;
            if (collectionSize > 0) {
                backlogPlayers++;
            }
            if (inventory.hasAnyActiveOffers()) {
                playersWithOffers++;
            }
            totalEscrow += inventory.getCoinsInEscrow();
        }

        int activeSellOffers = getActiveOfferCount();
        int activeBuyOrders = orderBooksByItem.values().stream()
            .mapToInt(OrderBook::getBuyOrderCount)
            .sum();
        int trackedItems = orderBooksByItem.size();

        int trackedRatePlayers = rateLimitService != null ? rateLimitService.getTrackedPlayerCount() : 0;
        int rateChecks = rateLimitService != null ? rateLimitService.getTotalChecks() : 0;
        float denialRate = rateLimitService != null ? rateLimitService.getDenialRate() : 0f;

        return new DiagnosticsReport(
            System.currentTimeMillis(),
            activeInventories,
            totalInventoriesCreated,
            backlogEntries,
            backlogPlayers,
            playersWithOffers,
            totalEscrow,
            activeSellOffers,
            activeBuyOrders,
            trackedItems,
            totalTradesCompleted,
            totalVolumeTraded,
            trackedRatePlayers,
            rateChecks,
            denialRate
        );
    }

    public DiagnosticsSnapshot captureDiagnosticsSnapshot(String requestedBy, long requestedAuth) {
        DiagnosticsReport report = buildDiagnosticsReport();
        DiagnosticsSnapshot snapshot = new DiagnosticsSnapshot(
            requestedBy == null ? "system" : requestedBy,
            requestedAuth,
            report
        );
        synchronized (diagnosticsHistory) {
            diagnosticsHistory.addFirst(snapshot);
            int maxSnapshots = getConfiguredDiagnosticsCapacity();
            while (diagnosticsHistory.size() > maxSnapshots) {
                diagnosticsHistory.removeLast();
            }
        }
        markPersistenceDirty();
        return snapshot;
    }

    public java.util.List<DiagnosticsSnapshot> getDiagnosticsHistory(int limit) {
        int maxSnapshots = getConfiguredDiagnosticsCapacity();
        int normalized = limit <= 0 ? maxSnapshots : Math.min(limit, maxSnapshots);
        java.util.List<DiagnosticsSnapshot> snapshots = new ArrayList<>(normalized);
        synchronized (diagnosticsHistory) {
            int index = 0;
            for (DiagnosticsSnapshot snapshot : diagnosticsHistory) {
                if (index++ >= normalized) {
                    break;
                }
                snapshots.add(snapshot.copy());
            }
        }
        return snapshots;
    }

    public void forcePersistenceFlush(String reason) {
        DiagnosticsSnapshot snapshot = captureDiagnosticsSnapshot(
            reason == null ? "manual" : reason,
            -1L
        );
        ModLogger.info("Grand Exchange persistence snapshot captured (%s) at %d", reason, snapshot.getReport().getTimestamp());
    }
    
    /**
     * Get the offer repository for data access.
     */
    public OfferRepository getRepository() {
        return repository;
    }
    
    // getBankForPlayer removed — use BankingLevelData.getBankingData(level) when needed
    
    /**
     * Periodic cleanup for service layer.
     * Call this from the tick() method.
     */
    public void performServiceCleanup() {
        rateLimitService.cleanup();
        notificationService.cleanup();
        ModLogger.debug("GE service cleanup completed");
    }
    
    // ===== TICK & CLEANUP =====
    
    /**
     * Tick handler - called every game tick by LevelDataManager.
     * Periodically cleans up expired offers.
     */
    @Override
    public void tick() {
        super.tick();

        // Only run on server
        if (this.level == null || !this.level.isServer()) {
            return;
        }

        tickCounter++;
        if (tickCounter >= CLEANUP_INTERVAL_TICKS) {
            tickCounter = 0;
            if (ModConfig.GrandExchange.enableOfferExpiration) {
                cleanupExpiredOffers(this.level);
            }
            // Clean up service layer
            performServiceCleanup();
        }

        persistenceTickCounter++;
        if (persistenceTickCounter >= PERSIST_INTERVAL_TICKS) {
            persistenceTickCounter = 0;
            DiagnosticsSnapshot snapshot = captureDiagnosticsSnapshot("system-autosave", -1L);
            ModLogger.debug("[GE] Captured periodic diagnostics snapshot at %d (sell=%d buy=%d)",
                snapshot.getReport().getTimestamp(),
                snapshot.getReport().getActiveSellOffers(),
                snapshot.getReport().getActiveBuyOrders());
        }
    }

    /**
     * Clean up expired offers.
     * Returns unsold items to seller's bank if enabled.
     */
    public void cleanupExpiredOffers(Level level) {
        int expired = 0;
        
        Iterator<Map.Entry<Long, GEOffer>> iterator = offers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, GEOffer> entry = iterator.next();
            GEOffer offer = entry.getValue();

            if (offer.isExpired() && offer.isActive()) {
                offer.expire();
                
                // Get player's GE inventory
                PlayerGEInventory inventory = inventories.get(offer.getPlayerAuth());
                if (inventory != null) {
                    int slot = offer.getInventorySlot();
                    inventory.setSlotOffer(slot, null);
                    refundOfferItems(level, offer, inventory, "Offer expired",
                        ModConfig.GrandExchange.returnExpiredToBank && ModConfig.Banking.enabled);
                    inventory.recordSellOfferCompleted();
                }
                
                removeSellOfferFromIndexes(offer);
                
                expired++;
            }
        }
        
        if (expired > 0) {
            ModLogger.info("Cleaned up %d expired offers", expired);
            markPersistenceDirty();
        }
    }

    // ===== PERSISTENCE =====

    /**
     * Send sell inventory sync packet to a specific client.
     * Public method for use by packet handlers.
     */
    public void sendSellInventorySyncToClient(ServerClient client, long playerAuth) {
        PlayerGEInventory inventory = getInventory(playerAuth);
        if (inventory == null) {
            ModLogger.warn("Cannot send sell inventory sync: no inventory for player auth=%d", playerAuth);
            return;
        }
        
        medievalsim.packets.PacketGESellInventorySync packet =
            new medievalsim.packets.PacketGESellInventorySync(
                playerAuth,
                inventory.getSellOffers()
            );
        
        client.sendPacket(packet);
        ModLogger.debug("Sent sell inventory sync to player auth=%d", playerAuth);
    }

    /**
     * Helper: notify player's client to refresh sell inventory UI (if online).
     */
    private void notifyPlayerSellInventory(Level level, long playerAuth) {
        if (level == null || !level.isServer()) return;
        Server server = level.getServer();
        if (server == null) return;
        ServerClient client = server.getClientByAuth(playerAuth);
        if (client != null) {
            sendSellInventorySyncToClient(client, playerAuth);
        }
    }

    /**
     * Helper: notify player's client to refresh buy order UI (if online).
     */
    private void notifyPlayerBuyOrders(Level level, long playerAuth) {
        if (level == null || !level.isServer()) return;
        Server server = level.getServer();
        if (server == null) return;
        ServerClient client = server.getClientByAuth(playerAuth);
        if (client != null) {
            PlayerGEInventory inventory = getPlayerInventorySnapshot(playerAuth);
            RateLimitStatus creationStatus = rateLimitStatus(RateLimitedAction.BUY_CREATE, playerAuth);
            RateLimitStatus toggleStatus = rateLimitStatus(RateLimitedAction.BUY_TOGGLE, playerAuth);

            medievalsim.packets.PacketGEBuyOrderSync packet =
                new medievalsim.packets.PacketGEBuyOrderSync(
                    playerAuth,
                    inventory.getBuyOrders(),
                    analyticsService,
                    inventory,
                    creationStatus,
                    toggleStatus
                );
            client.sendPacket(packet);
        }
    }

    private RateLimitStatus rateLimitStatus(RateLimitedAction action, long playerAuth) {
        if (rateLimitService == null) {
            return RateLimitStatus.inactive(action);
        }
        return rateLimitService.snapshot(action, playerAuth);
    }

    private void notifyPlayerCollectionBox(Level level, long playerAuth) {
        if (level == null || !level.isServer()) return;
        Server server = level.getServer();
        if (server == null) return;
        ServerClient client = server.getClientByAuth(playerAuth);
        if (client == null) {
            return;
        }

        PlayerGEInventory inventory = getOrCreateInventory(playerAuth);
        CollectionPaginator.Page page = CollectionPaginator.paginate(
            inventory.getCollectionBox(),
            inventory.getCollectionPageIndex()
        );
        client.sendPacket(new PacketGECollectionSync(
            playerAuth,
            page,
            inventory.isCollectionDepositToBankPreferred(),
            inventory.isAutoSendToBank(),
            inventory.isNotifyPartialSales(),
            inventory.isPlaySoundOnSale()
        ));
    }

    public void sendHistoryUpdate(Level level, long playerAuth, List<HistoryEntrySnapshot> newEntries) {
        sendHistoryUpdate(level, playerAuth, newEntries, false);
    }

    public void sendHistoryUpdate(Level level, long playerAuth, List<HistoryEntrySnapshot> newEntries, boolean fallbackToSnapshot) {
        if (newEntries == null) {
            sendHistorySnapshot(level, playerAuth);
            return;
        }
        boolean delivered = sendHistoryDelta(level, playerAuth, newEntries);
        if (!delivered && fallbackToSnapshot) {
            sendHistorySnapshot(level, playerAuth);
        }
    }

    private boolean sendHistoryDelta(Level level, long playerAuth, List<HistoryEntrySnapshot> newEntries) {
        if (level == null || !level.isServer()) {
            return false;
        }
        Server server = level.getServer();
        if (server == null) {
            return false;
        }
        ServerClient client = server.getClientByAuth(playerAuth);
        if (client == null || !(client.getContainer() instanceof GrandExchangeContainer)) {
            return false;
        }
        PlayerGEInventory inventory = getOrCreateInventory(playerAuth);
        HistoryDeltaPayload payload = buildHistoryDeltaPayload(inventory, newEntries);
        if (payload == null) {
            return false;
        }
        client.sendPacket(new PacketGEHistoryDelta(payload));
        sendHistoryBadge(client, inventory);
        return true;
    }

    private void sendHistorySnapshot(Level level, long playerAuth) {
        if (level == null || !level.isServer()) {
            return;
        }
        Server server = level.getServer();
        if (server == null) {
            return;
        }
        ServerClient client = server.getClientByAuth(playerAuth);
        if (client == null || !(client.getContainer() instanceof GrandExchangeContainer)) {
            return;
        }
        PlayerGEInventory inventory = getOrCreateInventory(playerAuth);
        if (inventory == null) {
            return;
        }
        client.sendPacket(new PacketGEHistorySync(playerAuth, inventory));
        sendHistoryBadge(client, inventory);
    }

    private void sendHistoryBadge(ServerClient client, PlayerGEInventory inventory) {
        if (client == null || !(client.getContainer() instanceof GrandExchangeContainer)) {
            return;
        }
        if (inventory == null) {
            return;
        }
        client.sendPacket(new PacketGEHistoryBadge(
            inventory.getOwnerAuth(),
            inventory.getUnseenHistoryCount(),
            inventory.getLatestHistoryTimestamp(),
            inventory.getLastHistoryViewedTimestamp()
        ));
    }

    private HistoryDeltaPayload buildHistoryDeltaPayload(PlayerGEInventory inventory, List<HistoryEntrySnapshot> newEntries) {
        if (inventory == null) {
            return null;
        }
        List<HistoryEntrySnapshot> safeEntries = (newEntries == null || newEntries.isEmpty())
            ? Collections.emptyList()
            : newEntries;
        return new HistoryDeltaPayload(
            inventory.getOwnerAuth(),
            safeEntries,
            inventory.getTotalItemsPurchased(),
            inventory.getTotalItemsSold(),
            inventory.getTotalSellOffersCreated(),
            inventory.getTotalSellOffersCompleted(),
            inventory.getTotalBuyOrdersCreated(),
            inventory.getTotalBuyOrdersCompleted(),
            inventory.getLatestHistoryTimestamp(),
            inventory.getLastHistoryViewedTimestamp()
        );
    }

    private HistoryEntrySnapshot toHistoryEntrySnapshot(SaleNotification notification) {
        if (notification == null) {
            return null;
        }
        return new HistoryEntrySnapshot(
            notification.getItemStringID(),
            notification.getQuantityTraded(),
            notification.getPricePerItem(),
            notification.getTotalCoins(),
            notification.isPartial(),
            notification.getCounterpartyName(),
            notification.getTimestamp(),
            notification.isSale()
        );
    }

    private void notifyPlayerSaleEvent(Level level, long playerAuth, SellOfferSaleEvent event) {
        if (event == null) {
            return;
        }

        PlayerGEInventory inventory = getInventory(playerAuth);
        boolean deliveredToActiveContainer = false;

        if (level != null && level.isServer()) {
            Server server = level.getServer();
            if (server != null) {
                ServerClient client = server.getClientByAuth(playerAuth);
                if (client != null && client.getContainer() instanceof GrandExchangeContainer) {
                    client.sendPacket(new PacketGESaleEvent(playerAuth, event));
                    deliveredToActiveContainer = true;
                }
            }
        }

        if (!deliveredToActiveContainer && inventory != null) {
            inventory.queuePendingSaleEvent(event);
            markPersistenceDirty();
        }
    }

    // Small helper to safely fetch a PlayerGEInventory or a minimal snapshot used for buy sync creation
    private PlayerGEInventory getPlayerInventorySnapshot(long playerAuth) {
        PlayerGEInventory inv = inventories.get(playerAuth);
        return inv == null ? new PlayerGEInventory(playerAuth) : inv;
    }

    private void removeSellOfferFromIndexes(GEOffer offer) {
        if (offer == null) {
            return;
        }

        String itemID = offer.getItemStringID();
        if (itemID != null) {
            offersByItem.computeIfPresent(itemID, (k, list) -> {
                synchronized (list) {
                    list.remove(offer.getOfferID());
                    return list.isEmpty() ? null : list;
                }
            });
        }

        OrderBook book = itemID == null ? null : orderBooksByItem.get(itemID);
        if (book != null) {
            book.removeSellOffer(offer.getOfferID());
            if (book.isEmpty()) {
                orderBooksByItem.remove(itemID);
            }
        }
    }

    private void removeBuyOrderFromIndexes(BuyOrder order) {
        if (order == null) {
            return;
        }

        String itemID = order.getItemStringID();
        if (itemID != null) {
            buyOrdersByItem.computeIfPresent(itemID, (k, list) -> {
                synchronized (list) {
                    list.remove(order.getOrderID());
                    return list.isEmpty() ? null : list;
                }
            });
        }

        OrderBook book = itemID == null ? null : orderBooksByItem.get(itemID);
        if (book != null) {
            book.removeBuyOrder(order.getOrderID());
            if (book.isEmpty()) {
                orderBooksByItem.remove(itemID);
            }
        }
    }

    private int refundBuyOrderEscrow(Level level,
                                     PlayerGEInventory inventory,
                                     BuyOrder order,
                                     boolean addNotification) {
        if (order == null) {
            return 0;
        }

        int coinsToRefund = order.getTotalCoinsRequired();
        if (coinsToRefund <= 0) {
            return 0;
        }

        BankingLevelData bankingData = BankingLevelData.getBankingData(level);
        if (bankingData == null) {
            ModLogger.error("Banking system not available for buy order refund!");
            return 0;
        }

        PlayerBank bank = bankingData.getOrCreateBank(order.getPlayerAuth());
        bank.addCoins(coinsToRefund);

        if (inventory != null) {
            inventory.removeCoinsFromEscrow(coinsToRefund);
            if (addNotification) {
                SaleNotification refundNotification = new SaleNotification(
                    "coins",
                    coinsToRefund,
                    1,
                    coinsToRefund,
                    false,
                    "System"
                );
                inventory.addSaleNotification(refundNotification);
            }
        }

        return coinsToRefund;
    }

    private void refundOfferItems(Level level, GEOffer offer, PlayerGEInventory inventory,
                                   String source, boolean preferBankDeposit) {
        if (offer == null) {
            return;
        }

        InventoryItem slotItem = inventory != null
            ? inventory.getSellInventory().getItem(offer.getInventorySlot())
            : null;
        InventoryItem refundStack = buildRefundStack(offer, slotItem);
        if (refundStack == null || refundStack.item == null || refundStack.getAmount() <= 0) {
            ModLogger.debug("No refundable items for offer ID=%d", offer.getOfferID());
            if (inventory != null) {
                inventory.getSellInventory().setItem(offer.getInventorySlot(), null);
            }
            return;
        }

        boolean delivered = tryGiveItemToPlayer(level, offer.getPlayerAuth(), refundStack.copy());
        if (!delivered && preferBankDeposit) {
            delivered = tryDepositToBank(level, offer.getPlayerAuth(), refundStack.copy());
        }

        if (!delivered && inventory != null) {
            inventory.addToCollectionBox(refundStack.item.getStringID(), refundStack.getAmount(), source);
            notifyPlayerCollectionBox(level, offer.getPlayerAuth());
            delivered = true;
        }

        if (!delivered) {
            ModLogger.warn("Failed to deliver refund for offer ID=%d (player auth=%d)",
                offer.getOfferID(), offer.getPlayerAuth());
        }

        if (inventory != null) {
            inventory.getSellInventory().setItem(offer.getInventorySlot(), null);
        }
    }

    private InventoryItem buildRefundStack(GEOffer offer, InventoryItem slotItem) {
        if (slotItem != null && slotItem.item != null && slotItem.getAmount() > 0) {
            return slotItem.copy();
        }

        int quantity = Math.max(0, offer.getQuantityRemaining());
        if (quantity <= 0) {
            return null;
        }

        Item item = ItemRegistry.getItem(offer.getItemStringID());
        if (item == null) {
            ModLogger.warn("Cannot refund offer ID=%d: unknown item %s",
                offer.getOfferID(), offer.getItemStringID());
            return null;
        }

        InventoryItem fallback = new InventoryItem(item, quantity);
        fallback.setAmount(quantity);
        return fallback;
    }

    private boolean tryGiveItemToPlayer(Level level, long playerAuth, InventoryItem stack) {
        if (stack == null || stack.item == null || level == null || !level.isServer()) {
            return false;
        }
        Server server = level.getServer();
        if (server == null) {
            return false;
        }
        ServerClient client = server.getClientByAuth(playerAuth);
        if (client == null || client.playerMob == null) {
            return false;
        }
        InventoryItem copy = stack.copy();
        boolean added = client.playerMob.getInv().main.addItem(
            level,
            client.playerMob,
            copy,
            "grandexchange_refund",
            null
        );
        if (added) {
            ModLogger.info("Returned %s x%d to player auth=%d",
                copy.item.getStringID(), copy.getAmount(), playerAuth);
        }
        return added;
    }

    private boolean tryDepositToBank(Level level, long playerAuth, InventoryItem stack) {
        if (!ModConfig.Banking.enabled || level == null || stack == null || stack.item == null) {
            return false;
        }
        BankingLevelData bankingData = BankingLevelData.getBankingData(level);
        if (bankingData == null) {
            return false;
        }
        PlayerBank bank = bankingData.getOrCreateBank(playerAuth);
        InventoryItem copy = stack.copy();
        boolean added = bank.getInventory().addItem(
            level,
            null,
            copy,
            "grandexchange_refund_bank",
            null
        );
        if (added) {
            ModLogger.info("Deposited %s x%d to bank for player auth=%d",
                copy.item.getStringID(), copy.getAmount(), playerAuth);
        }
        return added;
    }
    
    @Override
    public void addSaveData(SaveData save) {
        super.addSaveData(save); // CRITICAL: Save parent LevelData state
        
        ModLogger.debug("Saving GrandExchangeLevelData: %d inventories, %d offers, %d buy orders",
            inventories.size(), offers.size(), buyOrders.size());
        
        // Save ID generators
        save.addLong("nextOfferID", nextOfferID.get());
        save.addLong("nextBuyOrderID", nextBuyOrderID.get());
        
        // Save statistics
        save.addLong("totalInventoriesCreated", totalInventoriesCreated);
        save.addLong("totalOffersCreated", totalOffersCreated);
        save.addLong("totalTradesCompleted", totalTradesCompleted);
        save.addLong("totalVolumeTraded", totalVolumeTraded);

        // Save all player inventories
        SaveData inventoriesData = new SaveData("INVENTORIES");
        for (PlayerGEInventory inventory : inventories.values()) {
            SaveData inventorySave = new SaveData("INVENTORY");
            inventory.addSaveData(inventorySave);
            inventoriesData.addSaveData(inventorySave);
        }
        save.addSaveData(inventoriesData);

        // Save all offers
        SaveData offersData = new SaveData("OFFERS");
        for (GEOffer offer : offers.values()) {
            SaveData offerSave = new SaveData("OFFER");
            offer.addSaveData(offerSave);
            offersData.addSaveData(offerSave);
        }
        save.addSaveData(offersData);
        
        // Save all buy orders
        SaveData buyOrdersData = new SaveData("BUY_ORDERS");
        for (BuyOrder order : buyOrders.values()) {
            SaveData orderSave = new SaveData("ORDER");
            orderSave.addSaveData(order.getSaveData());
            buyOrdersData.addSaveData(orderSave);
        }
        save.addSaveData(buyOrdersData);

        // Save price history
        SaveData historyData = new SaveData("PRICE_HISTORY");
        for (Map.Entry<String, List<Integer>> entry : priceHistory.entrySet()) {
            SaveData itemHistory = new SaveData("ITEM");
            itemHistory.addUnsafeString("itemID", entry.getKey());
            itemHistory.addIntArray("prices", entry.getValue().stream().mapToInt(i -> i).toArray());
            historyData.addSaveData(itemHistory);
        }
        save.addSaveData(historyData);
        
        // Save audit log
        if (auditLog != null) {
            SaveData auditSave = new SaveData("AuditLog");
            auditLog.addSaveData(auditSave);
            save.addSaveData(auditSave);
        }

        if (analyticsService != null) {
            SaveData analyticsSave = new SaveData("MARKET_ANALYTICS");
            analyticsService.addSaveData(analyticsSave);
            save.addSaveData(analyticsSave);
        }

        if (rateLimitService != null) {
            SaveData rateLimitSave = new SaveData("RATE_LIMIT");
            rateLimitService.addSaveData(rateLimitSave);
            save.addSaveData(rateLimitSave);
        }

        SaveData diagnosticsData = new SaveData("DIAGNOSTICS_HISTORY");
        synchronized (diagnosticsHistory) {
            for (DiagnosticsSnapshot snapshot : diagnosticsHistory) {
                SaveData snapshotSave = new SaveData("SNAPSHOT");
                snapshot.addSaveData(snapshotSave);
                diagnosticsData.addSaveData(snapshotSave);
            }
        }
        save.addSaveData(diagnosticsData);
        
        ModLogger.debug("Saved %d inventories, %d offers, %d buy orders, %d price histories",
            inventories.size(), offers.size(), buyOrders.size(), priceHistory.size());
    }

    @Override
    public void applyLoadData(LoadData load) {
        super.applyLoadData(load); // CRITICAL: Restore parent LevelData state
        
        ModLogger.debug("Loading GrandExchangeLevelData...");
        
        repository.clearAll();
        
        // Load ID generators
        nextOfferID.set(load.getLong("nextOfferID", 1L));
        nextBuyOrderID.set(load.getLong("nextBuyOrderID", 1L));
        
        // Load statistics
        this.totalInventoriesCreated = load.getLong("totalInventoriesCreated", 0L);
        this.totalOffersCreated = load.getLong("totalOffersCreated", 0L);
        this.totalTradesCompleted = load.getLong("totalTradesCompleted", 0L);
        this.totalVolumeTraded = load.getLong("totalVolumeTraded", 0L);

        // Load all player inventories
        LoadData inventoriesData = load.getFirstLoadDataByName("INVENTORIES");
        if (inventoriesData != null) {
            inventories.clear();
            for (LoadData inventoryLoad : inventoriesData.getLoadDataByName("INVENTORY")) {
                try {
                    long ownerAuth = inventoryLoad.getLong("ownerAuth", 0L);
                    if (ownerAuth == 0L) {
                        ModLogger.warn("Skipping GE inventory with invalid ownerAuth=0");
                        continue;
                    }

                    PlayerGEInventory inventory = new PlayerGEInventory(ownerAuth);
                    inventory.applyLoadData(inventoryLoad);
                    inventories.put(ownerAuth, inventory);
                } catch (Exception e) {
                    ModLogger.error("Failed to load GE inventory", e);
                }
            }
        }

        // Load all offers
        LoadData offersData = load.getFirstLoadDataByName("OFFERS");
        if (offersData != null) {
            offers.clear();
            offersByItem.clear();
            
            for (LoadData offerLoad : offersData.getLoadDataByName("OFFER")) {
                try {
                    GEOffer offer = GEOffer.fromLoadData(offerLoad);
                    offers.put(offer.getOfferID(), offer);
                    
                    // Rebuild item index
                    offersByItem.computeIfAbsent(offer.getItemStringID(), k -> new ArrayList<>())
                        .add(offer.getOfferID());
                    repository.saveSellOffer(offer);
                } catch (Exception e) {
                    ModLogger.error("Failed to load GE offer", e);
                }
            }
        }
        
        // Link offers back to inventory slots using direct slotIndex from GEOffer (SIMPLIFIED)
        for (GEOffer offer : offers.values()) {
            PlayerGEInventory inventory = inventories.get(offer.getPlayerAuth());
            if (inventory != null) {
                inventory.setSlotOffer(offer.getInventorySlot(), offer);
            } else {
                ModLogger.warn("Could not link offer ID=%d to inventory (player auth=%d not found)",
                    offer.getOfferID(), offer.getPlayerAuth());
            }
        }

        // Load all buy orders
        LoadData buyOrdersData = load.getFirstLoadDataByName("BUY_ORDERS");
        if (buyOrdersData != null) {
            buyOrders.clear();
            buyOrdersByItem.clear();
            
            for (LoadData orderLoad : buyOrdersData.getLoadDataByName("ORDER")) {
                try {
                    LoadData buyOrderData = orderLoad.getFirstLoadDataByName("BUY_ORDER");
                    if (buyOrderData == null) {
                        ModLogger.warn("Skipping buy order with null data");
                        continue;
                    }
                    BuyOrder order = BuyOrder.fromSaveData(buyOrderData);
                    if (order != null) {
                        buyOrders.put(order.getOrderID(), order);
                        
                        // Rebuild item index (only for active orders)
                        if (order.isActive()) {
                            buyOrdersByItem.computeIfAbsent(order.getItemStringID(), k -> new ArrayList<>())
                                .add(order.getOrderID());
                        }
                        
                        // Link back to player inventory
                        PlayerGEInventory inventory = inventories.get(order.getPlayerAuth());
                        if (inventory != null) {
                            inventory.setBuyOrder(order.getSlotIndex(), order);
                        }

                        repository.saveBuyOrder(order);
                    }
                } catch (Exception e) {
                    ModLogger.error("Failed to load buy order", e);
                }
            }
        }
        
        // Load price history
        LoadData historyData = load.getFirstLoadDataByName("PRICE_HISTORY");
        if (historyData != null) {
            priceHistory.clear();
            for (LoadData itemHistory : historyData.getLoadDataByName("ITEM")) {
                try {
                    String itemID = itemHistory.getUnsafeString("itemID");
                    int[] prices = itemHistory.getIntArray("prices");
                    List<Integer> priceList = new ArrayList<>();
                    for (int price : prices) {
                        priceList.add(price);
                    }
                    priceHistory.put(itemID, priceList);
                } catch (Exception e) {
                    ModLogger.error("Failed to load price history: %s", e.getMessage());
                }
            }
        }
        
        // Load audit log
        LoadData auditLogData = load.getFirstLoadDataByName("AuditLog");
        if (auditLogData != null && auditLog != null) {
            try {
                auditLog.applyLoadData(auditLogData);
                ModLogger.info("Loaded audit entries from save");
            } catch (Exception e) {
                ModLogger.error("Failed to load audit log", e);
            }
        }

        LoadData analyticsData = load.getFirstLoadDataByName("MARKET_ANALYTICS");
        if (analyticsService != null && analyticsData != null) {
            analyticsService.applyLoadData(analyticsData);
        }

        LoadData rateLimitData = load.getFirstLoadDataByName("RATE_LIMIT");
        if (rateLimitService != null && rateLimitData != null) {
            rateLimitService.applyLoadData(rateLimitData);
        }

        LoadData diagnosticsData = load.getFirstLoadDataByName("DIAGNOSTICS_HISTORY");
        if (diagnosticsData != null) {
            synchronized (diagnosticsHistory) {
                diagnosticsHistory.clear();
                for (LoadData snapshotLoad : diagnosticsData.getLoadDataByName("SNAPSHOT")) {
                    DiagnosticsSnapshot snapshot = DiagnosticsSnapshot.fromLoadData(snapshotLoad);
                    if (snapshot != null) {
                        diagnosticsHistory.addLast(snapshot);
                    }
                }
            }
        }
        
        ModLogger.info("Loaded GE data: %d inventories, %d offers, %d buy orders, %d price histories (trades: %d, volume: %d coins)",
            inventories.size(), offers.size(), buyOrders.size(), priceHistory.size(), totalTradesCompleted, totalVolumeTraded);
    }

    public static final class DiagnosticsReport {
        private final long timestamp;
        private final int activeInventories;
        private final long totalInventoriesCreated;
        private final long backlogEntries;
        private final long backlogPlayers;
        private final long playersWithOffers;
        private final long totalEscrow;
        private final int activeSellOffers;
        private final int activeBuyOrders;
        private final int trackedItems;
        private final long totalTrades;
        private final long totalVolume;
        private final int trackedRatePlayers;
        private final int rateChecks;
        private final float denialRate;

        public DiagnosticsReport(long timestamp,
                                  int activeInventories,
                                  long totalInventoriesCreated,
                                  long backlogEntries,
                                  long backlogPlayers,
                                  long playersWithOffers,
                                  long totalEscrow,
                                  int activeSellOffers,
                                  int activeBuyOrders,
                                  int trackedItems,
                                  long totalTrades,
                                  long totalVolume,
                                  int trackedRatePlayers,
                                  int rateChecks,
                                  float denialRate) {
            this.timestamp = timestamp;
            this.activeInventories = activeInventories;
            this.totalInventoriesCreated = totalInventoriesCreated;
            this.backlogEntries = backlogEntries;
            this.backlogPlayers = backlogPlayers;
            this.playersWithOffers = playersWithOffers;
            this.totalEscrow = totalEscrow;
            this.activeSellOffers = activeSellOffers;
            this.activeBuyOrders = activeBuyOrders;
            this.trackedItems = trackedItems;
            this.totalTrades = totalTrades;
            this.totalVolume = totalVolume;
            this.trackedRatePlayers = trackedRatePlayers;
            this.rateChecks = rateChecks;
            this.denialRate = denialRate;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public int getActiveInventories() {
            return activeInventories;
        }

        public long getTotalInventoriesCreated() {
            return totalInventoriesCreated;
        }

        public long getBacklogEntries() {
            return backlogEntries;
        }

        public long getBacklogPlayers() {
            return backlogPlayers;
        }

        public long getPlayersWithOffers() {
            return playersWithOffers;
        }

        public long getTotalEscrow() {
            return totalEscrow;
        }

        public int getActiveSellOffers() {
            return activeSellOffers;
        }

        public int getActiveBuyOrders() {
            return activeBuyOrders;
        }

        public int getTrackedItems() {
            return trackedItems;
        }

        public long getTotalTrades() {
            return totalTrades;
        }

        public long getTotalVolume() {
            return totalVolume;
        }

        public int getTrackedRatePlayers() {
            return trackedRatePlayers;
        }

        public int getRateChecks() {
            return rateChecks;
        }

        public float getDenialRate() {
            return denialRate;
        }

        public DiagnosticsReport copy() {
            return new DiagnosticsReport(
                timestamp,
                activeInventories,
                totalInventoriesCreated,
                backlogEntries,
                backlogPlayers,
                playersWithOffers,
                totalEscrow,
                activeSellOffers,
                activeBuyOrders,
                trackedItems,
                totalTrades,
                totalVolume,
                trackedRatePlayers,
                rateChecks,
                denialRate
            );
        }

        public void addSaveData(SaveData save) {
            save.addLong("timestamp", timestamp);
            save.addInt("activeInventories", activeInventories);
            save.addLong("totalInventoriesCreated", totalInventoriesCreated);
            save.addLong("backlogEntries", backlogEntries);
            save.addLong("backlogPlayers", backlogPlayers);
            save.addLong("playersWithOffers", playersWithOffers);
            save.addLong("totalEscrow", totalEscrow);
            save.addInt("activeSellOffers", activeSellOffers);
            save.addInt("activeBuyOrders", activeBuyOrders);
            save.addInt("trackedItems", trackedItems);
            save.addLong("totalTrades", totalTrades);
            save.addLong("totalVolume", totalVolume);
            save.addInt("trackedRatePlayers", trackedRatePlayers);
            save.addInt("rateChecks", rateChecks);
            save.addFloat("denialRate", denialRate);
        }

        public static DiagnosticsReport fromLoadData(LoadData load) {
            if (load == null) {
                return null;
            }
            return new DiagnosticsReport(
                load.getLong("timestamp", System.currentTimeMillis()),
                load.getInt("activeInventories", 0),
                load.getLong("totalInventoriesCreated", 0L),
                load.getLong("backlogEntries", 0L),
                load.getLong("backlogPlayers", 0L),
                load.getLong("playersWithOffers", 0L),
                load.getLong("totalEscrow", 0L),
                load.getInt("activeSellOffers", 0),
                load.getInt("activeBuyOrders", 0),
                load.getInt("trackedItems", 0),
                load.getLong("totalTrades", 0L),
                load.getLong("totalVolume", 0L),
                load.getInt("trackedRatePlayers", 0),
                load.getInt("rateChecks", 0),
                load.getFloat("denialRate", 0f)
            );
        }
    }

    public static final class DiagnosticsSnapshot {
        private final String requestedBy;
        private final long requestedAuth;
        private final DiagnosticsReport report;

        public DiagnosticsSnapshot(String requestedBy, long requestedAuth, DiagnosticsReport report) {
            this.requestedBy = requestedBy == null || requestedBy.isEmpty() ? "system" : requestedBy;
            this.requestedAuth = requestedAuth;
            this.report = report;
        }

        public String getRequestedBy() {
            return requestedBy;
        }

        public long getRequestedAuth() {
            return requestedAuth;
        }

        public DiagnosticsReport getReport() {
            return report;
        }

        public DiagnosticsSnapshot copy() {
            return new DiagnosticsSnapshot(requestedBy, requestedAuth, report.copy());
        }

        public void addSaveData(SaveData save) {
            save.addUnsafeString("requestedBy", requestedBy);
            save.addLong("requestedAuth", requestedAuth);
            SaveData reportSave = new SaveData("REPORT");
            report.addSaveData(reportSave);
            save.addSaveData(reportSave);
        }

        public static DiagnosticsSnapshot fromLoadData(LoadData load) {
            if (load == null) {
                return null;
            }
            DiagnosticsReport report = DiagnosticsReport.fromLoadData(load.getFirstLoadDataByName("REPORT"));
            if (report == null) {
                return null;
            }
            return new DiagnosticsSnapshot(
                load.getUnsafeString("requestedBy") != null ? load.getUnsafeString("requestedBy") : "system",
                load.getLong("requestedAuth", -1L),
                report
            );
        }
    }
}

