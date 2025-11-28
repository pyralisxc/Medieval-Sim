package medievalsim.grandexchange.domain;

import medievalsim.banking.domain.BankingLevelData;
import medievalsim.banking.domain.PlayerBank;
import medievalsim.config.ModConfig;
import medievalsim.grandexchange.repository.InMemoryOfferRepository;
import medievalsim.grandexchange.repository.OfferRepository;
import medievalsim.grandexchange.services.*;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Collections;
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
            ModConfig.GrandExchange.priceHistorySize
        );
        this.rateLimitService = new RateLimitService();
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
    
    // ===== STATIC HELPERS =====
    
    /**
     * Get or create GrandExchangeLevelData for a level.
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
        return data;
    }
    
    // ===== INVENTORY MANAGEMENT =====
    
    /**
     * Get or create player's GE inventory.
     */
    public PlayerGEInventory getOrCreateInventory(long playerAuth) {
        return inventories.computeIfAbsent(playerAuth, auth -> {
            totalInventoriesCreated++;
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
    }

    public void resizeAllBuyInventories(int newSlotCount) {
        inventories.values().forEach(inv -> inv.resizeBuySlots(newSlotCount));
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
        
        // Validate slot
        if (!inventory.isValidSellSlot(slot)) {
            ModLogger.error("Invalid slot: %d (player auth=%d)", slot, playerAuth);
            return null;
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
        totalOffersCreated++;
        
        // Save to repository
        repository.saveSellOffer(offer);
        
        // Note: Do NOT add to offersByItem index yet - only when enabled
        // Note: Do NOT activate offer - it starts in DRAFT state
        // Note: Do NOT match offers - only match when enabled
        
        ModLogger.info("Created DRAFT sell offer ID=%d: %s x%d @ %d coins (slot=%d, player=%s)",
            offerID, itemStringID, quantity, pricePerItem, slot, playerName);
        
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
        
        // Remove from item index (atomically)
        if (offer.getItemStringID() != null) {
            offersByItem.computeIfPresent(offer.getItemStringID(), (k, list) -> {
                synchronized (list) {
                    list.remove(offerID);
                    return list.isEmpty() ? null : list;
                }
            });
        }
        
        // Remove from persistent OrderBook if present
        OrderBook book = orderBooksByItem.get(offer.getItemStringID());
        if (book != null) {
            book.removeSellOffer(offerID);
            if (book.isEmpty()) {
                orderBooksByItem.remove(offer.getItemStringID());
            }
        }

        // Remove from repository
        repository.deleteSellOffer(offerID);
        
        ModLogger.info("Cleaned up orphaned offer ID=%d from all data structures", offerID);
    }
    
    /**
     * Clean up a finished offer (COMPLETED/EXPIRED/CANCELLED).
     * Removes from all tracking structures.
     */
    private void cleanupFinishedOffer(GEOffer offer) {
        long offerID = offer.getOfferID();

        // Remove from main map
        offers.remove(offerID);

        // Remove from item index (atomically)
        if (offer.getItemStringID() != null) {
            offersByItem.computeIfPresent(offer.getItemStringID(), (k, list) -> {
                synchronized (list) {
                    list.remove(offerID);
                    return list.isEmpty() ? null : list;
                }
            });
        }

        // Remove from repository
        repository.deleteSellOffer(offerID);

        // Remove from persistent OrderBook if present
        OrderBook book = orderBooksByItem.get(offer.getItemStringID());
        if (book != null) {
            book.removeSellOffer(offerID);
            if (book.isEmpty()) {
                orderBooksByItem.remove(offer.getItemStringID());
            }
        }

        ModLogger.debug("Cleaned up finished offer ID=%d (state=%s)", offerID, offer.getState());
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
    public boolean cancelOffer(Level level, long offerID) {
        GEOffer offer = offers.get(offerID);
        if (offer == null) {
            return false;
        }

        if (!offer.isActive()) {
            ModLogger.warn("Offer ID=%d is not active (state=%s)", offerID, offer.getState());
            return false;
        }

        offer.cancel();

        // Update inventory
        PlayerGEInventory inventory = inventories.get(offer.getPlayerAuth());
        if (inventory != null) {
            inventory.setSlotOffer(offer.getInventorySlot(), null);
            inventory.recordSellOfferCancelled();
        }

        // Remove from item index
        List<Long> itemOffers = offersByItem.get(offer.getItemStringID());
        if (itemOffers != null) {
            itemOffers.remove(offerID);
        }

        // Remove from persistent OrderBook if present
        OrderBook book = orderBooksByItem.get(offer.getItemStringID());
        if (book != null) {
            book.removeSellOffer(offerID);
            if (book.isEmpty()) {
                orderBooksByItem.remove(offer.getItemStringID());
            }
        }

        // Remove from repository and main map
        offers.remove(offerID);
        repository.deleteSellOffer(offerID);

        ModLogger.info("Cancelled offer ID=%d (player auth=%d, slot=%d)",
            offerID, offer.getPlayerAuth(), offer.getInventorySlot());

        // Notify player client if we have level context
        if (level != null) {
            notifyPlayerSellInventory(level, offer.getPlayerAuth());
        }

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

        return new MarketSnapshot(safePage, totalPages, totalResults, pageSize,
            normalizedFilter, normalizedCategory, sortMode, entries);
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
    public synchronized boolean enableSellOffer(Level level, long playerAuth, int slotIndex) {
        // 1. Check rate limit
        if (!rateLimitService.canCreateSellOffer(playerAuth)) {
            float remaining = rateLimitService.getRemainingCooldownForSellOffer(playerAuth);
            ModLogger.warn("Player %d rate limited, cooldown: %.1f seconds remaining", playerAuth, remaining);
            // TODO: Send packet to client with error message
            return false;
        }
        
        PlayerGEInventory inventory = getInventory(playerAuth);
        if (inventory == null) {
            ModLogger.warn("No GE inventory for player auth=%d", playerAuth);
            return false;
        }
        
        GEOffer offer = inventory.getSlotOffer(slotIndex);
        if (offer == null) {
            ModLogger.warn("No sell offer in slot %d for player auth=%d", slotIndex, playerAuth);
            return false;
        }
        
        if (offer.getState() != GEOffer.OfferState.DRAFT) {
            ModLogger.warn("Sell offer ID=%d cannot be enabled (state=%s)", offer.getOfferID(), offer.getState());
            return false;
        }
        
        // Enable the offer
        if (!offer.enable()) {
            return false;
        }
        
        // Add to item index for matching (use synchronized list for thread-safety)
        List<Long> itemList = offersByItem.computeIfAbsent(offer.getItemStringID(), k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (itemList) {
            if (!itemList.contains(offer.getOfferID())) {
                itemList.add(offer.getOfferID());
            }
        }
        
        // Save to repository (within synchronized block for consistency)
        repository.saveSellOffer(offer);
        
        // Record rate limit
        rateLimitService.recordSellOfferCreation(playerAuth);
        
        ModLogger.info("Enabled sell offer ID=%d (player auth=%d, slot=%d)",
            offer.getOfferID(), playerAuth, slotIndex);
        
        // Attempt to match with existing buy orders using new OrderBook system
        // This happens within the synchronized block to prevent race conditions
        if (ModConfig.GrandExchange.enableInstantTrades) {
            // Add to persistent OrderBook and try matching against existing buy orders
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
        
        // Notify player client so UI stays in sync (if online)
        notifyPlayerSellInventory(level, playerAuth);

        return true;
    }
    
    /**
     * Disable a sell offer (checkbox unchecked).
     * Moves offer from ACTIVE → DRAFT, removes from market.
     * Thread-safe: synchronized to prevent race conditions.
     */
    public synchronized boolean disableSellOffer(Level level, long playerAuth, int slotIndex) {
        // Check rate limit to prevent rapid enable/disable spam
        if (!rateLimitService.canCreateSellOffer(playerAuth)) {
            float remaining = rateLimitService.getRemainingCooldownForSellOffer(playerAuth);
            ModLogger.warn("Player %d rate limited for offer state change, cooldown: %.1f seconds", playerAuth, remaining);
            return false;
        }
        
        PlayerGEInventory inventory = getInventory(playerAuth);
        if (inventory == null) {
            ModLogger.warn("No GE inventory for player auth=%d", playerAuth);
            return false;
        }
        
        GEOffer offer = inventory.getSlotOffer(slotIndex);
        if (offer == null) {
            ModLogger.warn("No sell offer in slot %d for player auth=%d", slotIndex, playerAuth);
            return false;
        }
        
        GEOffer.OfferState state = offer.getState();
        if (state != GEOffer.OfferState.ACTIVE && state != GEOffer.OfferState.PARTIAL) {
            ModLogger.warn("Sell offer ID=%d cannot be disabled (state=%s)", offer.getOfferID(), offer.getState());
            return false;
        }
        
        // Remove from item index (atomically)
        if (offer.getItemStringID() != null) {
            offersByItem.computeIfPresent(offer.getItemStringID(), (k, list) -> {
                synchronized (list) {
                    list.remove(offer.getOfferID());
                    return list.isEmpty() ? null : list;
                }
            });
        }
        
        // Disable the offer
        if (!offer.disable()) {
            return false;
        }
        
        // Update repository
        repository.saveSellOffer(offer);
        
        // Remove from persistent OrderBook if present
        OrderBook book = orderBooksByItem.get(offer.getItemStringID());
        if (book != null) {
            book.removeSellOffer(offer.getOfferID());
        }
        
        ModLogger.info("Disabled sell offer ID=%d (player auth=%d, slot=%d)",
            offer.getOfferID(), playerAuth, slotIndex);
        
        // Notify player client so UI stays in sync (if online)
        notifyPlayerSellInventory(level, playerAuth);

        return true;
    }
    
    // ===== BUY ORDER MANAGEMENT =====
    
    /**
     * Create a new buy order (DRAFT state, not yet enabled).
     * Player must enable it to activate and escrow coins.
     */
    public BuyOrder createBuyOrder(long playerAuth, int slotIndex, String itemStringID, 
                                    int quantity, int pricePerItem, int durationDays) {
        // Validation
        PlayerGEInventory inventory = getOrCreateInventory(playerAuth);
        
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
        String playerName = "Player"; // TODO: Get from ServerClient if available
        BuyOrder order = new BuyOrder(orderID, playerAuth, playerName, slotIndex);
        
        // Configure the order
        if (!order.configure(itemStringID, quantity, pricePerItem, durationDays)) {
            ModLogger.warn("Failed to configure buy order for player auth=%d", playerAuth);
            return null;
        }
        
        // Store in inventory
        inventory.setBuyOrder(slotIndex, order);
        inventory.recordBuyOrderCreated();
        
        // Store in global map (but NOT in buyOrdersByItem index until enabled)
        buyOrders.put(orderID, order);
        
        ModLogger.info("Created buy order ID=%d (player auth=%d, slot=%d, item=%s, qty=%d, price=%d) [DRAFT]",
            orderID, playerAuth, slotIndex, itemStringID, quantity, pricePerItem);
        
        return order;
    }
    
    /**
     * Enable a buy order (checkbox checked).
     * Deducts coins from player's bank and adds to escrow.
     * Returns true if successful, false if insufficient funds.
     * NEW: Uses rate limiting to prevent spam.
     */
    public boolean enableBuyOrder(Level level, long playerAuth, int slotIndex) {
        // Check rate limit
        if (!rateLimitService.canCreateBuyOrder(playerAuth)) {
            float remaining = rateLimitService.getRemainingCooldownForBuyOrder(playerAuth);
            ModLogger.warn("Player %d rate limited for buy orders, cooldown: %.1f seconds remaining", playerAuth, remaining);
            // TODO: Send packet to client with error message
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
        
        if (order.getState() != BuyOrder.BuyOrderState.DRAFT) {
            ModLogger.warn("Buy order ID=%d cannot be enabled (state=%s)", order.getOrderID(), order.getState());
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
        
        // Record rate limit
        rateLimitService.recordBuyOrderCreation(playerAuth);
        
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

        return true;
    }
    
    /**
     * Disable a buy order (checkbox unchecked).
     * Refunds escrowed coins back to player's bank.
     */
    public boolean disableBuyOrder(Level level, long playerAuth, int slotIndex) {
        // Check rate limit to prevent rapid enable/disable spam
        if (!rateLimitService.canCreateBuyOrder(playerAuth)) {
            float remaining = rateLimitService.getRemainingCooldownForBuyOrder(playerAuth);
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
        
        // Remove from item index (atomically)
        if (order.getItemStringID() != null) {
            buyOrdersByItem.computeIfPresent(order.getItemStringID(), (k, list) -> {
                synchronized (list) {
                    list.remove(order.getOrderID());
                    return list.isEmpty() ? null : list;
                }
            });
        }
        // Remove from persistent OrderBook if present
        OrderBook book = orderBooksByItem.get(order.getItemStringID());
        if (book != null) {
            book.removeBuyOrder(order.getOrderID());
            if (book.isEmpty()) {
                orderBooksByItem.remove(order.getItemStringID());
            }
        }
        
        // Disable the order
        order.disable();
        
        // Refund coins to bank
        if (coinsToRefund > 0) {
            BankingLevelData bankingData = BankingLevelData.getBankingData(level);
            if (bankingData != null) {
                PlayerBank bank = bankingData.getOrCreateBank(playerAuth);
                bank.addCoins(coinsToRefund);
                
                // Remove from escrow
                inventory.removeCoinsFromEscrow(coinsToRefund);
                
                // Add notification to collection box so player knows coins were refunded
                SaleNotification refundNotification = new SaleNotification(
                    "coins",
                    coinsToRefund,
                    1, // price per "item" is 1 coin
                    coinsToRefund,
                    false, // not partial
                    "System"
                );
                inventory.addSaleNotification(refundNotification);
                
                ModLogger.info("Disabled buy order ID=%d (player auth=%d, slot=%d, refunded %d coins)",
                    order.getOrderID(), playerAuth, slotIndex, coinsToRefund);
            } else {
                ModLogger.error("Banking system not available for buy order refund!");
            }
        }
        
        // Notify player client so buy order UI stays in sync (if online)
        notifyPlayerBuyOrders(level, playerAuth);

        return true;
    }
    
    /**
     * Cancel a buy order (remove completely).
     * Refunds any escrowed coins and clears the slot.
     */
    public boolean cancelBuyOrder(Level level, long playerAuth, int slotIndex) {
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
            int coinsToRefund = order.getTotalCoinsRequired();
            if (coinsToRefund > 0) {
                BankingLevelData bankingData = BankingLevelData.getBankingData(level);
                if (bankingData != null) {
                    PlayerBank bank = bankingData.getOrCreateBank(playerAuth);
                    bank.addCoins(coinsToRefund);
                    inventory.removeCoinsFromEscrow(coinsToRefund);
                }
            }
            
            // Remove from item index
            List<Long> itemOrders = buyOrdersByItem.get(order.getItemStringID());
            if (itemOrders != null) {
                itemOrders.remove(order.getOrderID());
            }
            // Remove from persistent OrderBook if present
            OrderBook book = orderBooksByItem.get(order.getItemStringID());
            if (book != null) {
                book.removeBuyOrder(order.getOrderID());
                if (book.isEmpty()) {
                    orderBooksByItem.remove(order.getItemStringID());
                }
            }
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
        
        // Notify player client so buy order UI stays in sync (if online)
        notifyPlayerBuyOrders(level, playerAuth);

        return true;
    }

    /**
     * Process a manual market purchase (Tab 0) using the shared trade transaction pipeline.
     * Buys the entire remaining quantity of the specified offer at the asking price.
     */
    public boolean processMarketPurchase(Level level, long buyerAuth, String buyerName, long offerID) {
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

        int quantity = offer.getQuantityRemaining();
        if (quantity <= 0) {
            ModLogger.warn("Offer ID=%d has no remaining quantity", offerID);
            return false;
        }

        int pricePerItem = offer.getPricePerItem();
        int totalCost = pricePerItem * quantity;

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

        if (buyerBank.getCoins() < totalCost) {
            ModLogger.warn("Player auth=%d lacks coins for purchase: has %d, needs %d",
                buyerAuth, buyerBank.getCoins(), totalCost);
            return false;
        }

        if (!buyerBank.removeCoins(totalCost)) {
            ModLogger.error("Failed to remove %d coins from bank for player auth=%d", totalCost, buyerAuth);
            return false;
        }

        buyerInventory.addCoinsToEscrow(totalCost);

        long orderID = nextBuyOrderID.getAndIncrement();
        String resolvedBuyerName = (buyerName != null && !buyerName.isEmpty()) ? buyerName : "Player";
        BuyOrder instantOrder = new BuyOrder(orderID, buyerAuth, resolvedBuyerName, -1);
        if (!instantOrder.configure(offer.getItemStringID(), quantity, pricePerItem, 1)) {
            buyerInventory.removeCoinsFromEscrow(totalCost);
            buyerBank.addCoins(totalCost);
            ModLogger.warn("Failed to configure instant buy order for offer ID=%d", offerID);
            return false;
        }

        if (!instantOrder.enable()) {
            buyerInventory.removeCoinsFromEscrow(totalCost);
            buyerBank.addCoins(totalCost);
            ModLogger.warn("Failed to enable instant buy order for offer ID=%d", offerID);
            return false;
        }

        TradeTransaction.TradeResult tradeResult = executeTradeWithTransaction(level, instantOrder, offer, quantity, pricePerItem);
        if (tradeResult == null) {
            buyerInventory.removeCoinsFromEscrow(totalCost);
            buyerBank.addCoins(totalCost);
            ModLogger.warn("Trade transaction failed for manual purchase (offer ID=%d)", offerID);
            return false;
        }

        ModLogger.info("Manual market purchase: buyer auth=%d bought offer ID=%d (qty=%d, price=%d)",
            buyerAuth, offerID, quantity, pricePerItem);

        return true;
    }
    
    // Legacy matching helpers removed — replaced by persistent OrderBook flows.
    
    // ===== DEPRECATED LEGACY MATCHING (TO BE REMOVED) =====

    
    /**
     * Execute a trade using the TradeTransaction service for atomic operations.
     * NEW ENTERPRISE VERSION: Uses prepare/commit/rollback pattern.
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
            return null;
        }
        
        // Phase 2: Commit (returns TradeResult or null on failure)
        TradeTransaction.TradeResult result = transaction.commit();
        if (result == null) {
            ModLogger.error("Trade commit failed: %s", transaction.getFailureReason());
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
        if (sellerInventory != null) {
            SaleNotification saleNotification = new SaleNotification(
                sellOffer.getItemStringID(),
                quantity,
                pricePerItem,
                totalCoins,
                sellOffer.getQuantityRemaining() > 0,
                buyOrder.getPlayerName()
            );
            sellerInventory.addSaleNotification(saleNotification);
        }
        
        // Update indexes if orders/offers are complete
        if (buyOrder.getState() == BuyOrder.BuyOrderState.COMPLETED) {
            if (buyOrder.getItemStringID() != null) {
                buyOrdersByItem.computeIfPresent(buyOrder.getItemStringID(), (k, list) -> {
                    synchronized (list) {
                        list.remove(buyOrder.getOrderID());
                        return list.isEmpty() ? null : list;
                    }
                });
            }
            // Also remove from persistent OrderBook
            OrderBook buyBook = orderBooksByItem.get(buyOrder.getItemStringID());
            if (buyBook != null) {
                buyBook.removeBuyOrder(buyOrder.getOrderID());
                if (buyBook.isEmpty()) {
                    orderBooksByItem.remove(buyOrder.getItemStringID());
                }
            }
        }

        if (sellOffer.getState() == GEOffer.OfferState.COMPLETED) {
            if (sellOffer.getItemStringID() != null) {
                offersByItem.computeIfPresent(sellOffer.getItemStringID(), (k, list) -> {
                    synchronized (list) {
                        list.remove(sellOffer.getOfferID());
                        return list.isEmpty() ? null : list;
                    }
                });
            }
            // Also remove from persistent OrderBook
            OrderBook sellBook = orderBooksByItem.get(sellOffer.getItemStringID());
            if (sellBook != null) {
                sellBook.removeSellOffer(sellOffer.getOfferID());
                if (sellBook.isEmpty()) {
                    orderBooksByItem.remove(sellOffer.getItemStringID());
                }
            }
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

        return result;
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
        
        return inventory.removeFromCollectionBox(index);
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
        List<CollectionItem> collectionBox = new ArrayList<>(inventory.getCollectionBox());
        int transferred = 0;
        
        for (int i = collectionBox.size() - 1; i >= 0; i--) {
            CollectionItem item = collectionBox.get(i);
            InventoryItem invItem = item.toInventoryItem();
            
            if (bank.getInventory().addItem(level, null, invItem, "geCollection", null)) {
                inventory.removeFromCollectionBox(i);
                transferred++;
            }
        }
        
        ModLogger.info("Collected %d items from collection box to bank for player auth=%d", transferred, playerAuth);
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
    }
    
    /**
     * @deprecated Use recordSale(String, int, int, long) with full parameters. Phase 6 removal.
     */
    @Deprecated
    public void recordSale(String itemStringID, int pricePerItem) {
        recordSale(itemStringID, pricePerItem, 1, pricePerItem);
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
    
    // ===== LEGACY COMPATIBILITY (Phase 6 Removal) =====
    
    /**
     * @deprecated Use getAllActiveOffers() instead. This is a temporary compatibility method
     * for old container code. Will be removed in Phase 6 when container is rewritten.
     */
    @Deprecated
    public List<medievalsim.grandexchange.domain.MarketListing> getAllListings() {
        // Return empty list - old MarketListing system deprecated
        ModLogger.warn("getAllListings() called - deprecated, returning empty list. Phase 6 rewrite needed.");
        return new ArrayList<>();
    }
    
    /**
     * @deprecated Use getPlayerOffers() instead. Phase 6 removal.
     */
    @Deprecated
    public List<medievalsim.grandexchange.domain.MarketListing> getListingsBySeller(long playerAuth) {
        ModLogger.warn("getListingsBySeller() called - deprecated, returning empty list. Phase 6 rewrite needed.");
        return new ArrayList<>();
    }
    
    /**
     * @deprecated Use getOffer() instead. Phase 6 removal.
     */
    @Deprecated
    public medievalsim.grandexchange.domain.MarketListing getListing(long offerID) {
        ModLogger.warn("getListing() called - deprecated, returning null. Phase 6 rewrite needed.");
        return null;
    }
    
    /**
     * @deprecated Use createSellOffer() instead. Phase 6 removal.
     */
    @Deprecated
    public medievalsim.grandexchange.domain.MarketListing createListing(ServerClient seller, 
                                                                         String itemStringID, 
                                                                         int quantity, 
                                                                         int pricePerItem) {
        ModLogger.warn("createListing() called - deprecated. Phase 6 rewrite needed.");
        return null;
    }
    
    /**
     * @deprecated Use cancelOffer() instead. Phase 6 removal.
     */
    @Deprecated
    public boolean removeListing(long offerID) {
        ModLogger.warn("removeListing() called - deprecated. Phase 6 rewrite needed.");
        return false;
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
    }

    /**
     * Clean up expired offers.
     * Returns unsold items to seller's bank if enabled.
     */
    public void cleanupExpiredOffers(Level level) {
        int expired = 0;
        int returned = 0;
        
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
                    
                    // Return items to bank if enabled
                    if (ModConfig.GrandExchange.returnExpiredToBank &&
                        ModConfig.Banking.enabled &&
                        offer.getType() == GEOffer.OfferType.SELL) {

                        medievalsim.banking.domain.BankingLevelData bankingData =
                            medievalsim.banking.domain.BankingLevelData.getBankingData(level);

                        if (bankingData != null) {
                            medievalsim.banking.domain.PlayerBank playerBank =
                                bankingData.getOrCreateBank(offer.getPlayerAuth());

                            necesse.inventory.InventoryItem unsoldItem = inventory.getSlotItem(slot);
                            if (unsoldItem != null && unsoldItem.getAmount() > 0) {
                                boolean success = playerBank.getInventory().addItem(
                                    level,
                                    null,
                                    unsoldItem,
                                    "grandexchange_expired",
                                    null
                                );

                                if (success) {
                                    // Clear GE slot after returning to bank
                                    inventory.clearSlot(slot);
                                    returned++;
                                    ModLogger.debug("Returned %d x %s to bank (offer expired, player auth=%d)",
                                        unsoldItem.getAmount(), offer.getItemStringID(), offer.getPlayerAuth());
                                } else {
                                    ModLogger.warn("Failed to return expired items to bank (bank full?) - items remain in GE slot");
                                }
                            }
                        }
                    } else {
                        // Just clear the slot offer link (items stay in GE slot for manual retrieval)
                        inventory.setSlotOffer(slot, null);
                    }
                    
                    inventory.recordSellOfferCompleted();
                }
                
                // Remove from item index
                List<Long> itemOffers = offersByItem.get(offer.getItemStringID());
                if (itemOffers != null) {
                    itemOffers.remove(offer.getOfferID());
                }
                
                expired++;
            }
        }
        
        if (expired > 0) {
            ModLogger.info("Cleaned up %d expired offers (%d items returned to banks)", expired, returned);
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
            GEOffer offer = inventory.getSlotOffer(i);
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
            medievalsim.packets.PacketGEBuyOrderSync packet =
                new medievalsim.packets.PacketGEBuyOrderSync(playerAuth, getPlayerInventorySnapshot(playerAuth).getBuyOrders());
            client.sendPacket(packet);
        }
    }

    // Small helper to safely fetch a PlayerGEInventory or a minimal snapshot used for buy sync creation
    private PlayerGEInventory getPlayerInventorySnapshot(long playerAuth) {
        PlayerGEInventory inv = inventories.get(playerAuth);
        return inv == null ? new PlayerGEInventory(playerAuth) : inv;
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
        
        ModLogger.debug("Saved %d inventories, %d offers, %d buy orders, %d price histories",
            inventories.size(), offers.size(), buyOrders.size(), priceHistory.size());
    }

    @Override
    public void applyLoadData(LoadData load) {
        super.applyLoadData(load); // CRITICAL: Restore parent LevelData state
        
        ModLogger.debug("Loading GrandExchangeLevelData...");
        
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
                    ModLogger.error("Failed to load GE inventory: %s", e.getMessage());
                    e.printStackTrace();
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
                } catch (Exception e) {
                    ModLogger.error("Failed to load GE offer: %s", e.getMessage());
                    e.printStackTrace();
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
                    }
                } catch (Exception e) {
                    ModLogger.error("Failed to load buy order: %s", e.getMessage());
                    e.printStackTrace();
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
                ModLogger.error("Failed to load audit log: %s", e.getMessage());
                e.printStackTrace();
            }
        }
        
        ModLogger.info("Loaded GE data: %d inventories, %d offers, %d buy orders, %d price histories (trades: %d, volume: %d coins)",
            inventories.size(), offers.size(), buyOrders.size(), priceHistory.size(), totalTradesCompleted, totalVolumeTraded);
    }
}

