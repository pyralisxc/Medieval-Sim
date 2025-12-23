package medievalsim.grandexchange.domain;

import medievalsim.config.ModConfig;
import medievalsim.grandexchange.model.event.SellOfferSaleEvent;
import medievalsim.util.ModLogger;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.save.levelData.InventorySave;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Per-player Grand Exchange data (enhanced multi-tab system).
 * 
 * Architecture:
 * - Sell inventory: 10 slots for creating sell offers
 * - Buy orders: 3 slots for creating buy orders
 * - Collection box: Unlimited item list for purchased/returned items
 * - Sale history: Last 50 sale notifications
 * - Settings: Auto-bank toggle, notification preferences
 * 
 * Pattern: Similar to PlayerBank but with multiple storage types.
 */
public class PlayerGEInventory {

    private static final int MAX_PURCHASE_HISTORY_RECORDS = 50;
    private static final int MAX_SALE_HISTORY_RECORDS = 50;
    private static final int MAX_PENDING_SALE_EVENTS = 6;

    private final long ownerAuth;
    
    // ===== SELL SYSTEM =====
    private final Inventory sellInventory;     // 10 slots for items being sold
    private GEOffer[] sellOffers;              // Links slot → active sell offer (null if no offer)
    
    // ===== BUY SYSTEM =====
    private BuyOrder[] buyOrders;              // 3 buy order slots
    
    // ===== COLLECTION & NOTIFICATIONS =====
    private final List<CollectionItem> collectionBox;       // Unlimited item storage (not inventory slots)
    private final List<SaleNotification> saleHistory;       // Last N sale notifications
    private final Deque<SellOfferSaleEvent> pendingSaleEvents; // Buffered sale pulses for reconnects
    private final Object collectionLock = new Object();
    private final Deque<PersonalTradeRecord> purchaseHistory; // Chronological personal buy history
    private final Object recentPurchaseLock = new Object();
    
    // ===== SETTINGS =====
    private boolean autoSendToBank;            // Auto-send purchases to bank (default true)
    private boolean notifyPartialSales;        // Notify on every partial sale (default true)
    private boolean playSoundOnSale;           // Play sound when sale completes (default true)
    private boolean collectionDepositToBankPreferred; // Preferred destination when collecting items
    private int collectionPageIndex;           // Last viewed collection page (pagination state)
    
    // ===== ESCROW =====
    private int coinsInEscrow;                 // Total coins locked in active buy orders
    
    // ===== METADATA =====
    private long lastAccessTime;
    private long creationTime;
    private int totalSellOffersCreated;
    private int totalSellOffersCancelled;
    private int totalSellOffersCompleted;
    private int totalBuyOrdersCreated;
    private int totalBuyOrdersCompleted;
    private int totalItemsPurchased;
    private int totalItemsSold;
    private long lastHistoryViewedTimestamp;                // Server-tracked history acknowledgement

    /**
     * Create new GE data for player.
     * @param ownerAuth Player authentication ID
     */
    public PlayerGEInventory(long ownerAuth) {
        this.ownerAuth = ownerAuth;
        
        // Sell system
        int sellSlotCount = ModConfig.GrandExchange.getPlayerSlotCount();
        this.sellInventory = new Inventory(sellSlotCount);
        this.sellOffers = new GEOffer[sellSlotCount];
        
        // Buy system
        int buySlotCount = ModConfig.GrandExchange.buyOrderSlots;
        this.buyOrders = new BuyOrder[buySlotCount];
        
        // Collection & notifications
        this.collectionBox = new ArrayList<>();
        this.saleHistory = new ArrayList<>();
        this.pendingSaleEvents = new ArrayDeque<>(MAX_PENDING_SALE_EVENTS + 2);
        this.purchaseHistory = new ArrayDeque<>(MAX_PURCHASE_HISTORY_RECORDS + 2);
        
        // Settings (defaults)
        this.autoSendToBank = true;
        this.notifyPartialSales = true;
        this.playSoundOnSale = true;
        this.collectionDepositToBankPreferred = ModConfig.GrandExchange.getDefaultCollectionDepositPreference();
        this.collectionPageIndex = 0;
        
        // Escrow
        this.coinsInEscrow = 0;
        
        // Metadata
        this.creationTime = System.currentTimeMillis();
        this.lastAccessTime = this.creationTime;
        this.totalSellOffersCreated = 0;
        this.totalSellOffersCancelled = 0;
        this.totalSellOffersCompleted = 0;
        this.totalBuyOrdersCreated = 0;
        this.totalBuyOrdersCompleted = 0;
        this.totalItemsPurchased = 0;
        this.totalItemsSold = 0;
        this.lastHistoryViewedTimestamp = 0L;

        ModLogger.debug("Created GE data for player auth=%d: %d sell slots, %d buy order slots", 
            ownerAuth, sellSlotCount, buySlotCount);
    }

    // ===== CORE ACCESSORS =====

    public long getOwnerAuth() {
        return ownerAuth;
    }

    // Sell inventory accessors
    public Inventory getSellInventory() {
        return sellInventory;
    }

    public int getSellSlotCount() {
        return sellInventory.getSize();
    }

    public synchronized boolean canApplySellSlotCount(int newSlotCount) {
        if (newSlotCount >= sellInventory.getSize()) {
            return true;
        }
        for (int i = newSlotCount; i < sellInventory.getSize(); i++) {
            if (sellInventory.getItem(i) != null) {
                return false;
            }
            if (sellOffers[i] != null) {
                return false;
            }
        }
        return true;
    }

    public synchronized void resizeSellSlots(int newSlotCount) {
        if (newSlotCount == sellInventory.getSize()) {
            return;
        }
        if (newSlotCount < sellInventory.getSize() && !canApplySellSlotCount(newSlotCount)) {
            ModLogger.warn("Cannot shrink sell slots to %d for auth=%d; extra slots still populated", newSlotCount, ownerAuth);
            return;
        }
        sellInventory.changeSize(newSlotCount);
        GEOffer[] newOffers = new GEOffer[newSlotCount];
        System.arraycopy(sellOffers, 0, newOffers, 0, Math.min(sellOffers.length, newSlotCount));
        sellOffers = newOffers;
    }

    public InventoryItem getSlotItem(int slot) {
        if (!isValidSellSlot(slot)) {
            return null;
        }
        return sellInventory.getItem(slot);
    }

    public boolean setSlotItem(int slot, InventoryItem item) {
        if (!isValidSellSlot(slot)) {
            return false;
        }
        sellInventory.setItem(slot, item);
        updateAccessTime();
        return true;
    }

    public boolean clearSlot(int slot) {
        if (!isValidSellSlot(slot)) {
            return false;
        }
        sellInventory.setItem(slot, null);
        sellOffers[slot] = null;
        updateAccessTime();
        return true;
    }
    
    // Buy order accessors
    public BuyOrder[] getBuyOrders() {
        return buyOrders;
    }
    
    public BuyOrder getBuyOrder(int slot) {
        if (slot < 0 || slot >= buyOrders.length) {
            return null;
        }
        return buyOrders[slot];
    }
    
    public boolean setBuyOrder(int slot, BuyOrder order) {
        if (slot < 0 || slot >= buyOrders.length) {
            return false;
        }
        buyOrders[slot] = order;
        updateAccessTime();
        return true;
    }

    public synchronized boolean canApplyBuySlotCount(int newSlotCount) {
        if (newSlotCount >= buyOrders.length) {
            return true;
        }
        for (int i = newSlotCount; i < buyOrders.length; i++) {
            if (buyOrders[i] != null) {
                if (!buyOrders[i].isCancelled() && !buyOrders[i].isCompleted()) {
                    return false;
                }
                // Even completed/cancelled orders are kept to avoid silent data loss.
                return false;
            }
        }
        return true;
    }

    public synchronized void resizeBuySlots(int newSlotCount) {
        if (newSlotCount == buyOrders.length) {
            return;
        }
        if (newSlotCount < buyOrders.length && !canApplyBuySlotCount(newSlotCount)) {
            ModLogger.warn("Cannot shrink buy slots to %d for auth=%d; trailing slots still contain orders", newSlotCount, ownerAuth);
            return;
        }
        BuyOrder[] newArray = new BuyOrder[newSlotCount];
        System.arraycopy(buyOrders, 0, newArray, 0, Math.min(buyOrders.length, newSlotCount));
        buyOrders = newArray;
    }
    
    // Collection box accessors
    public List<CollectionItem> getCollectionBox() {
        synchronized (collectionLock) {
            return new ArrayList<>(collectionBox);
        }
    }
    
    public int getCollectionBoxSize() {
        synchronized (collectionLock) {
            return collectionBox.size();
        }
    }
    
    // Sale history accessors
    public List<SaleNotification> getSaleHistory() {
        return saleHistory;
    }
    
    // Settings accessors
    public boolean isAutoSendToBank() {
        return autoSendToBank;
    }
    
    public void setAutoSendToBank(boolean value) {
        this.autoSendToBank = value;
        updateAccessTime();
    }
    
    public boolean isNotifyPartialSales() {
        return notifyPartialSales;
    }
    
    public void setNotifyPartialSales(boolean value) {
        this.notifyPartialSales = value;
        updateAccessTime();
    }
    
    public boolean isPlaySoundOnSale() {
        return playSoundOnSale;
    }
    
    public void setPlaySoundOnSale(boolean value) {
        this.playSoundOnSale = value;
        updateAccessTime();
    }

    public boolean isCollectionDepositToBankPreferred() {
        return collectionDepositToBankPreferred;
    }

    public void setCollectionDepositToBankPreferred(boolean value) {
        this.collectionDepositToBankPreferred = value;
        updateAccessTime();
    }

    public int getCollectionPageIndex() {
        return Math.max(0, collectionPageIndex);
    }

    public void setCollectionPageIndex(int pageIndex) {
        int normalized = Math.max(0, pageIndex);
        if (this.collectionPageIndex != normalized) {
            this.collectionPageIndex = normalized;
            updateAccessTime();
        }
    }
    
    // Escrow accessors
    public int getCoinsInEscrow() {
        return coinsInEscrow;
    }
    
    public void addCoinsToEscrow(int amount) {
        this.coinsInEscrow += amount;
        updateAccessTime();
    }
    
    public boolean removeCoinsFromEscrow(int amount) {
        if (amount > coinsInEscrow) {
            ModLogger.error("Cannot remove more than escrowed: amount=%d, escrowed=%d", amount, coinsInEscrow);
            return false;
        }
        this.coinsInEscrow -= amount;
        updateAccessTime();
        return true;
    }
    
    /**
     * Set coins in escrow (for rollback operations only).
     */
    public void setCoinsInEscrow(int amount) {
        this.coinsInEscrow = amount;
        updateAccessTime();
    }

    // ===== SELL OFFER MANAGEMENT =====

    public GEOffer[] getSellOffers() {
        return sellOffers;
    }

    public GEOffer getSlotOffer(int slot) {
        if (!isValidSellSlot(slot)) {
            return null;
        }
        return sellOffers[slot];
    }

    public boolean setSlotOffer(int slot, GEOffer offer) {
        if (!isValidSellSlot(slot)) {
            ModLogger.warn("Attempted to set offer for invalid slot %d (player auth=%d)", slot, ownerAuth);
            return false;
        }
        
        if (offer != null && offer.getInventorySlot() != slot) {
            ModLogger.error("Offer slot mismatch: offer.slot=%d, target slot=%d (player auth=%d)",
                offer.getInventorySlot(), slot, ownerAuth);
            return false;
        }
        
        sellOffers[slot] = offer;
        updateAccessTime();
        return true;
    }

    public boolean hasActiveOffer(int slot) {
        if (!isValidSellSlot(slot)) {
            return false;
        }
        GEOffer offer = sellOffers[slot];
        return offer != null && offer.isActive();
    }

    public boolean hasAnyActiveOffers() {
        for (int i = 0; i < sellOffers.length; i++) {
            if (hasActiveOffer(i)) {
                return true;
            }
        }
        return false;
    }

    public int getActiveOfferCount() {
        int count = 0;
        for (int i = 0; i < sellOffers.length; i++) {
            if (hasActiveOffer(i)) {
                count++;
            }
        }
        return count;
    }

    public int getAvailableSellSlotCount() {
        int count = 0;
        for (int i = 0; i < sellOffers.length; i++) {
            if (isSlotReusableForNewOffer(i)) {
                count++;
            }
        }
        return count;
    }

    public boolean canCreateSellOffer() {
        if (findAvailableSellSlot() < 0) {
            return false;
        }
        return getActiveOfferCount() < ModConfig.GrandExchange.maxActiveOffersPerPlayer;
    }

    /**
     * Find first available sell slot (no active or draft offer occupying it).
     * @return Slot index, or -1 if all slots occupied
     */
    public int findAvailableSellSlot() {
        for (int i = 0; i < sellOffers.length; i++) {
            if (isSlotReusableForNewOffer(i)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isSlotReusableForNewOffer(int slot) {
        if (!isValidSellSlot(slot)) {
            return false;
        }
        GEOffer offer = sellOffers[slot];
        if (offer == null) {
            return true;
        }
        GEOffer.OfferState state = offer.getState();
        return state == GEOffer.OfferState.COMPLETED
            || state == GEOffer.OfferState.CANCELLED
            || state == GEOffer.OfferState.EXPIRED;
    }
    
    // ===== BUY ORDER MANAGEMENT =====
    
    public int getActiveBuyOrderCount() {
        int count = 0;
        for (BuyOrder order : buyOrders) {
            if (order != null && order.isActive()) {
                count++;
            }
        }
        return count;
    }
    
    public boolean hasActiveBuyOrders() {
        return getActiveBuyOrderCount() > 0;
    }
    
    /**
     * Find first available buy order slot (null or completed/cancelled).
     */
    public int findAvailableBuySlot() {
        for (int i = 0; i < buyOrders.length; i++) {
            if (buyOrders[i] == null || 
                buyOrders[i].isCompleted() || 
                buyOrders[i].isCancelled()) {
                return i;
            }
        }
        return -1;
    }
    
    // ===== COLLECTION BOX MANAGEMENT =====
    
    /**
     * Add item to collection box.
     * Attempts to merge with existing items of same type.
     */
    public void addToCollectionBox(String itemStringID, int quantity, String source) {
        if (itemStringID == null || quantity <= 0) {
            return;
        }

        synchronized (collectionLock) {
            for (CollectionItem existing : collectionBox) {
                if (existing.getItemStringID().equals(itemStringID)) {
                    existing.addQuantity(quantity);
                    ModLogger.debug("Merged %d x %s into collection box (player auth=%d), new total=%d",
                        quantity, itemStringID, ownerAuth, existing.getQuantity());
                    updateAccessTime();
                    return;
                }
            }

            CollectionItem newItem = new CollectionItem(itemStringID, quantity, source);
            collectionBox.add(newItem);
            ModLogger.debug("Added %d x %s to collection box (player auth=%d, source=%s)",
                quantity, itemStringID, ownerAuth, source);
            updateAccessTime();
        }
    }
    
    /**
     * Remove item from collection box by index.
     */
    public CollectionItem removeFromCollectionBox(int index) {
        synchronized (collectionLock) {
            if (index < 0 || index >= collectionBox.size()) {
                return null;
            }
            CollectionItem item = collectionBox.remove(index);
            updateAccessTime();
            return item;
        }
    }

    /**
     * Reinsert an item into the collection box at a specific index (used for rollbacks).
     */
    public void insertIntoCollectionBox(int index, CollectionItem item) {
        if (item == null) {
            return;
        }
        synchronized (collectionLock) {
            if (index < 0 || index > collectionBox.size()) {
                collectionBox.add(item);
            } else {
                collectionBox.add(index, item);
            }
            updateAccessTime();
        }
    }
    
    /**
     * Clear entire collection box.
     */
    public void clearCollectionBox() {
        synchronized (collectionLock) {
            collectionBox.clear();
            updateAccessTime();
        }
    }

    public void replaceCollectionBox(List<CollectionItem> snapshot) {
        synchronized (collectionLock) {
            collectionBox.clear();
            if (snapshot != null && !snapshot.isEmpty()) {
                collectionBox.addAll(snapshot);
            }
            updateAccessTime();
        }
    }
    
    // ===== SALE HISTORY MANAGEMENT =====
    
    /**
     * Add sale notification to history.
     * Keeps only last N notifications (default 20).
     */
    public void addSaleNotification(SaleNotification notification) {
        saleHistory.add(0, notification);  // Add to front (most recent first)
        
        // Trim old notifications
        while (saleHistory.size() > MAX_SALE_HISTORY_RECORDS) {
            saleHistory.remove(saleHistory.size() - 1);
        }
        
        updateAccessTime();
        ModLogger.debug("Added sale notification for player auth=%d: %s", 
            ownerAuth, notification.formatUIMessage());
    }

    public void queuePendingSaleEvent(SellOfferSaleEvent saleEvent) {
        if (saleEvent == null) {
            return;
        }
        synchronized (pendingSaleEvents) {
            pendingSaleEvents.addLast(saleEvent);
            while (pendingSaleEvents.size() > MAX_PENDING_SALE_EVENTS) {
                pendingSaleEvents.removeFirst();
            }
        }
    }

    public List<SellOfferSaleEvent> drainPendingSaleEvents() {
        synchronized (pendingSaleEvents) {
            if (pendingSaleEvents.isEmpty()) {
                return Collections.emptyList();
            }
            List<SellOfferSaleEvent> snapshot = new ArrayList<>(pendingSaleEvents);
            pendingSaleEvents.clear();
            return snapshot;
        }
    }

    public long getLatestHistoryTimestamp() {
        if (saleHistory.isEmpty()) {
            return 0L;
        }
        return saleHistory.get(0).getTimestamp();
    }

    public long getLastHistoryViewedTimestamp() {
        return lastHistoryViewedTimestamp;
    }

    public void markHistoryViewed(long timestamp) {
        long normalized = Math.max(0L, timestamp);
        if (normalized > lastHistoryViewedTimestamp) {
            lastHistoryViewedTimestamp = normalized;
            updateAccessTime();
        }
    }

    public void markHistoryViewedUpToLatest() {
        markHistoryViewed(getLatestHistoryTimestamp());
    }
    
    public int getUnseenHistoryCount() {
        if (saleHistory.isEmpty()) {
            return 0;
        }
        long baseline = lastHistoryViewedTimestamp;
        int count = 0;
        for (SaleNotification notification : saleHistory) {
            if (notification.getTimestamp() > baseline) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * Clear sale history.
     */
    public void clearSaleHistory() {
        saleHistory.clear();
        lastHistoryViewedTimestamp = 0L;
        updateAccessTime();
    }

    // ===== VALIDATION =====

    public boolean isValidSellSlot(int slot) {
        return slot >= 0 && slot < sellInventory.getSize();
    }

    public boolean isSlotEmpty(int slot) {
        if (!isValidSellSlot(slot)) {
            return false;
        }
        InventoryItem item = sellInventory.getItem(slot);
        return item == null || item.getAmount() <= 0;
    }

    public boolean canPlaceItemInSlot(int slot) {
        return isValidSellSlot(slot) && !hasActiveOffer(slot);
    }

    // ===== STATISTICS =====

    public void recordSellOfferCreated() {
        totalSellOffersCreated++;
        updateAccessTime();
    }

    public void recordSellOfferCancelled() {
        totalSellOffersCancelled++;
        updateAccessTime();
    }

    public void recordSellOfferCompleted() {
        totalSellOffersCompleted++;
        updateAccessTime();
    }
    
    public void recordBuyOrderCreated() {
        totalBuyOrdersCreated++;
        updateAccessTime();
    }
    
    public void recordBuyOrderCancelled() {
        // Note: We don't track cancelled buy orders separately yet,
        // but method exists for consistency with sell offers
        updateAccessTime();
    }
    
    public void recordBuyOrderCompleted() {
        totalBuyOrdersCompleted++;
        updateAccessTime();
    }
    
    public void recordItemPurchased(int quantity) {
        totalItemsPurchased += quantity;
        updateAccessTime();
    }
    
    public void recordItemSold(int quantity) {
        totalItemsSold += quantity;
        updateAccessTime();
    }

    public void recordPersonalPurchase(String itemStringID, int quantity, int pricePerItem) {
        if (itemStringID == null || itemStringID.isEmpty() || pricePerItem <= 0) {
            return;
        }
        synchronized (recentPurchaseLock) {
            purchaseHistory.addFirst(new PersonalTradeRecord(itemStringID, pricePerItem,
                Math.max(1, quantity), System.currentTimeMillis()));
            while (purchaseHistory.size() > MAX_PURCHASE_HISTORY_RECORDS) {
                purchaseHistory.removeLast();
            }
        }
    }

    public PersonalTradeRecord getLastPurchaseRecord(String itemStringID) {
        if (itemStringID == null) {
            return null;
        }
        synchronized (recentPurchaseLock) {
            if (purchaseHistory.isEmpty()) {
                return null;
            }
            for (PersonalTradeRecord record : purchaseHistory) {
                if (itemStringID.equals(record.getItemStringID())) {
                    return record;
                }
            }
            return null;
        }
    }

    public List<PersonalTradeRecord> getRecentPurchases(int limit) {
        int resolvedLimit = Math.max(1, limit);
        List<PersonalTradeRecord> snapshot;
        synchronized (recentPurchaseLock) {
            snapshot = new ArrayList<>(purchaseHistory);
        }
        if (snapshot.isEmpty()) {
            return Collections.emptyList();
        }
        snapshot.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        if (snapshot.size() > resolvedLimit) {
            return new ArrayList<>(snapshot.subList(0, resolvedLimit));
        }
        return snapshot;
    }

    public int getTotalSellOffersCreated() {
        return totalSellOffersCreated;
    }

    public int getTotalSellOffersCancelled() {
        return totalSellOffersCancelled;
    }

    public int getTotalSellOffersCompleted() {
        return totalSellOffersCompleted;
    }
    
    public int getTotalBuyOrdersCreated() {
        return totalBuyOrdersCreated;
    }
    
    public int getTotalBuyOrdersCompleted() {
        return totalBuyOrdersCompleted;
    }
    
    public int getTotalItemsPurchased() {
        return totalItemsPurchased;
    }
    
    public int getTotalItemsSold() {
        return totalItemsSold;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void updateAccessTime() {
        lastAccessTime = System.currentTimeMillis();
    }

    // ===== PERSISTENCE =====

    /**
     * Save GE data to SaveData.
     */
    public void addSaveData(SaveData save) {
        save.addLong("ownerAuth", ownerAuth);
        
        // Save sell inventory
        save.addSaveData(InventorySave.getSave(sellInventory, "SELL_INVENTORY"));
        
        // Save sell offer references (just offerIDs, offers saved separately)
        SaveData sellOffersData = new SaveData("SELL_OFFERS");
        for (int i = 0; i < sellOffers.length; i++) {
            if (sellOffers[i] != null) {
                SaveData slotData = new SaveData("SLOT");
                slotData.addInt("slot", i);
                slotData.addLong("offerID", sellOffers[i].getOfferID());
                sellOffersData.addSaveData(slotData);
            }
        }
        save.addSaveData(sellOffersData);
        
        // Save buy orders
        SaveData buyOrdersData = new SaveData("BUY_ORDERS");
        for (int i = 0; i < buyOrders.length; i++) {
            if (buyOrders[i] != null) {
                buyOrdersData.addSaveData(buyOrders[i].getSaveData());
            }
        }
        save.addSaveData(buyOrdersData);
        
        // Save collection box
        SaveData collectionData = new SaveData("COLLECTION_BOX");
        List<CollectionItem> collectionSnapshot;
        synchronized (collectionLock) {
            collectionSnapshot = new ArrayList<>(collectionBox);
        }
        for (CollectionItem item : collectionSnapshot) {
            collectionData.addSaveData(item.getSaveData());
        }
        save.addSaveData(collectionData);
        
        // Save sale history
        SaveData historyData = new SaveData("SALE_HISTORY");
        for (SaleNotification notification : saleHistory) {
            historyData.addSaveData(notification.getSaveData());
        }
        save.addSaveData(historyData);

        // Save pending sale events (short buffer for reconnect)
        SaveData pendingEventsData = new SaveData("PENDING_SALE_EVENTS");
        synchronized (pendingSaleEvents) {
            for (SellOfferSaleEvent event : pendingSaleEvents) {
                SaveData eventData = new SaveData("SALE_EVENT");
                eventData.addInt("slotIndex", event.slotIndex());
                eventData.addUnsafeString("itemStringID", event.itemStringID());
                eventData.addInt("quantitySold", event.quantitySold());
                eventData.addInt("quantityRemaining", event.quantityRemaining());
                eventData.addInt("pricePerItem", event.pricePerItem());
                eventData.addLong("timestamp", event.timestamp());
                pendingEventsData.addSaveData(eventData);
            }
        }
        save.addSaveData(pendingEventsData);

        // Save recent purchases
        SaveData purchaseData = new SaveData("RECENT_PURCHASES");
        synchronized (recentPurchaseLock) {
            for (PersonalTradeRecord record : purchaseHistory) {
                SaveData recordData = new SaveData("PURCHASE");
                recordData.addUnsafeString("itemStringID", record.getItemStringID());
                recordData.addInt("pricePerItem", record.getPricePerItem());
                recordData.addInt("quantity", record.getQuantity());
                recordData.addLong("timestamp", record.getTimestamp());
                purchaseData.addSaveData(recordData);
            }
        }
        save.addSaveData(purchaseData);
        
        // Save settings
        save.addBoolean("autoSendToBank", autoSendToBank);
        save.addBoolean("notifyPartialSales", notifyPartialSales);
        save.addBoolean("playSoundOnSale", playSoundOnSale);
        save.addBoolean("collectionDepositToBankPreferred", collectionDepositToBankPreferred);
        save.addInt("collectionPageIndex", collectionPageIndex);
        
        // Save escrow
        save.addInt("coinsInEscrow", coinsInEscrow);
        
        // Save metadata
        save.addLong("lastAccessTime", lastAccessTime);
        save.addLong("creationTime", creationTime);
        save.addInt("totalSellOffersCreated", totalSellOffersCreated);
        save.addInt("totalSellOffersCancelled", totalSellOffersCancelled);
        save.addInt("totalSellOffersCompleted", totalSellOffersCompleted);
        save.addInt("totalBuyOrdersCreated", totalBuyOrdersCreated);
        save.addInt("totalBuyOrdersCompleted", totalBuyOrdersCompleted);
        save.addInt("totalItemsPurchased", totalItemsPurchased);
        save.addInt("totalItemsSold", totalItemsSold);
        save.addLong("lastHistoryViewedTimestamp", lastHistoryViewedTimestamp);

        ModLogger.debug("Saved GE data for auth=%d: %d sell slots, %d buy orders, %d collection items, %d notifications",
            ownerAuth, sellInventory.getSize(), buyOrders.length, getCollectionBoxSize(), saleHistory.size());
    }

    /**
     * Load GE data from LoadData.
     * Note: Sell offers must be linked separately after all offers are loaded.
     */
    public void applyLoadData(LoadData save) {
        // Load sell inventory
        LoadData inventoryLoad = save.getFirstLoadDataByName("SELL_INVENTORY");
        if (inventoryLoad != null) {
            Inventory loadedInventory = InventorySave.loadSave(inventoryLoad);
            this.sellInventory.override(loadedInventory, false, false);
        }
        
        // Load buy orders
        LoadData buyOrdersData = save.getFirstLoadDataByName("BUY_ORDERS");
        if (buyOrdersData != null) {
            int index = 0;
            for (LoadData orderData : buyOrdersData.getLoadDataByName("BUY_ORDER")) {
                if (index < buyOrders.length) {
                    buyOrders[index++] = BuyOrder.fromSaveData(orderData);
                }
            }
        }
        
        // Load collection box
        synchronized (collectionLock) {
            collectionBox.clear();
            LoadData collectionData = save.getFirstLoadDataByName("COLLECTION_BOX");
            if (collectionData != null) {
                for (LoadData itemData : collectionData.getLoadDataByName("COLLECTION_ITEM")) {
                    CollectionItem item = CollectionItem.fromSaveData(itemData);
                    if (item.isValid()) {
                        collectionBox.add(item);
                    }
                }
            }
        }
        
        // Load sale history
        saleHistory.clear();
        LoadData historyData = save.getFirstLoadDataByName("SALE_HISTORY");
        if (historyData != null) {
            for (LoadData notifData : historyData.getLoadDataByName("SALE_NOTIFICATION")) {
                saleHistory.add(SaleNotification.fromSaveData(notifData));
            }
        }

        synchronized (pendingSaleEvents) {
            pendingSaleEvents.clear();
            LoadData pendingEventsData = save.getFirstLoadDataByName("PENDING_SALE_EVENTS");
            if (pendingEventsData != null) {
                for (LoadData eventData : pendingEventsData.getLoadDataByName("SALE_EVENT")) {
                    int slotIndex = eventData.getInt("slotIndex", -1);
                    if (slotIndex < 0) {
                        continue;
                    }
                    String itemStringID = eventData.getUnsafeString("itemStringID");
                    if (itemStringID == null) {
                        itemStringID = "";
                    }
                    int quantitySold = eventData.getInt("quantitySold", 0);
                    int quantityRemaining = eventData.getInt("quantityRemaining", 0);
                    int pricePerItem = eventData.getInt("pricePerItem", 0);
                    long timestamp = eventData.getLong("timestamp", System.currentTimeMillis());
                    pendingSaleEvents.addLast(new SellOfferSaleEvent(slotIndex, itemStringID,
                        quantitySold, quantityRemaining, pricePerItem, timestamp));
                    while (pendingSaleEvents.size() > MAX_PENDING_SALE_EVENTS) {
                        pendingSaleEvents.removeFirst();
                    }
                }
            }
        }

        // Load recent purchases
        synchronized (recentPurchaseLock) {
            purchaseHistory.clear();
            LoadData purchaseData = save.getFirstLoadDataByName("RECENT_PURCHASES");
            if (purchaseData != null) {
                for (LoadData recordData : purchaseData.getLoadDataByName("PURCHASE")) {
                    String itemID = recordData.getUnsafeString("itemStringID");
                    if (itemID == null || itemID.isEmpty()) {
                        continue;
                    }
                    int price = recordData.getInt("pricePerItem", 0);
                    int quantity = recordData.getInt("quantity", 0);
                    long timestamp = recordData.getLong("timestamp", System.currentTimeMillis());
                    purchaseHistory.addLast(new PersonalTradeRecord(itemID, price, quantity, timestamp));
                    while (purchaseHistory.size() > MAX_PURCHASE_HISTORY_RECORDS) {
                        purchaseHistory.removeFirst();
                    }
                }
            }
        }

        this.lastHistoryViewedTimestamp = save.getLong("lastHistoryViewedTimestamp", getLatestHistoryTimestamp());
        
        // Load settings
        this.autoSendToBank = save.getBoolean("autoSendToBank", true);
        this.notifyPartialSales = save.getBoolean("notifyPartialSales", true);
        this.playSoundOnSale = save.getBoolean("playSoundOnSale", true);
        this.collectionDepositToBankPreferred = save.getBoolean(
            "collectionDepositToBankPreferred",
            this.autoSendToBank
        );
        this.collectionPageIndex = save.getInt("collectionPageIndex", 0);
        
        // Load escrow
        this.coinsInEscrow = save.getInt("coinsInEscrow", 0);
        
        // Load metadata
        this.lastAccessTime = save.getLong("lastAccessTime", System.currentTimeMillis());
        this.creationTime = save.getLong("creationTime", System.currentTimeMillis());
        this.totalSellOffersCreated = save.getInt("totalSellOffersCreated", 0);
        this.totalSellOffersCancelled = save.getInt("totalSellOffersCancelled", 0);
        this.totalSellOffersCompleted = save.getInt("totalSellOffersCompleted", 0);
        this.totalBuyOrdersCreated = save.getInt("totalBuyOrdersCreated", 0);
        this.totalBuyOrdersCompleted = save.getInt("totalBuyOrdersCompleted", 0);
        this.totalItemsPurchased = save.getInt("totalItemsPurchased", 0);
        this.totalItemsSold = save.getInt("totalItemsSold", 0);

        ModLogger.debug("Loaded GE data for auth=%d: %d sell slots, %d buy orders, %d collection items, %d notifications",
            ownerAuth, sellInventory.getSize(), getActiveBuyOrderCount(), getCollectionBoxSize(), saleHistory.size());
    }

    /**
     * Get slot→offerID mappings for persistence.
     * Used by GrandExchangeLevelData to link offers after loading.
     */
    public static java.util.Map<Integer, Long> loadSlotOfferMappings(LoadData save) {
        java.util.Map<Integer, Long> mappings = new java.util.HashMap<>();
        LoadData offersData = save.getFirstLoadDataByName("SELL_OFFERS");
        if (offersData != null) {
            for (LoadData slotData : offersData.getLoadDataByName("SLOT")) {
                int slot = slotData.getInt("slot");
                long offerID = slotData.getLong("offerID");
                mappings.put(slot, offerID);
            }
        }
        return mappings;
    }

    // ===== INNER CLASSES =====

    public static final class PersonalTradeRecord {
        private final String itemStringID;
        private final int pricePerItem;
        private final int quantity;
        private final long timestamp;

        public PersonalTradeRecord(String itemStringID, int pricePerItem, int quantity, long timestamp) {
            this.itemStringID = itemStringID;
            this.pricePerItem = pricePerItem;
            this.quantity = quantity;
            this.timestamp = timestamp;
        }

        public String getItemStringID() {
            return itemStringID;
        }

        public int getPricePerItem() {
            return pricePerItem;
        }

        public int getQuantity() {
            return quantity;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
