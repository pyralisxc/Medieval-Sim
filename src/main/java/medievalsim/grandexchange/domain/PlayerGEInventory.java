package medievalsim.grandexchange.domain;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.save.levelData.InventorySave;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-player Grand Exchange data (enhanced multi-tab system).
 * 
 * Architecture:
 * - Sell inventory: 10 slots for creating sell offers
 * - Buy orders: 3 slots for creating buy orders
 * - Collection box: Unlimited item list for purchased/returned items
 * - Sale history: Last 20 sale notifications
 * - Settings: Auto-bank toggle, notification preferences
 * 
 * Pattern: Similar to PlayerBank but with multiple storage types.
 */
public class PlayerGEInventory {

    private final long ownerAuth;
    
    // ===== SELL SYSTEM =====
    private final Inventory sellInventory;     // 10 slots for items being sold
    private final GEOffer[] sellOffers;        // Links slot → active sell offer (null if no offer)
    
    // ===== BUY SYSTEM =====
    private final BuyOrder[] buyOrders;        // 3 buy order slots
    
    // ===== COLLECTION & NOTIFICATIONS =====
    private final List<CollectionItem> collectionBox;       // Unlimited item storage (not inventory slots)
    private final List<SaleNotification> saleHistory;       // Last N sale notifications
    
    // ===== SETTINGS =====
    private boolean autoSendToBank;            // Auto-send purchases to bank (default true)
    private boolean notifyPartialSales;        // Notify on every partial sale (default true)
    private boolean playSoundOnSale;           // Play sound when sale completes (default true)
    
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
        
        // Settings (defaults)
        this.autoSendToBank = true;
        this.notifyPartialSales = true;
        this.playSoundOnSale = true;
        
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
    
    // Collection box accessors
    public List<CollectionItem> getCollectionBox() {
        return collectionBox;
    }
    
    public int getCollectionBoxSize() {
        return collectionBox.size();
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
        for (int i = 0; i < sellInventory.getSize(); i++) {
            if (!hasActiveOffer(i)) {
                count++;
            }
        }
        return count;
    }

    public boolean canCreateSellOffer() {
        return getActiveOfferCount() < ModConfig.GrandExchange.maxActiveOffersPerPlayer;
    }

    /**
     * Find first available sell slot (no active offer).
     * @return Slot index, or -1 if all slots occupied
     */
    public int findAvailableSellSlot() {
        for (int i = 0; i < sellInventory.getSize(); i++) {
            if (!hasActiveOffer(i)) {
                return i;
            }
        }
        return -1;
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
        if (quantity <= 0) {
            return;
        }
        
        // Try to merge with existing item
        for (CollectionItem existing : collectionBox) {
            if (existing.getItemStringID().equals(itemStringID)) {
                existing.addQuantity(quantity);
                ModLogger.debug("Merged %d x %s into collection box (player auth=%d), new total=%d",
                    quantity, itemStringID, ownerAuth, existing.getQuantity());
                updateAccessTime();
                return;
            }
        }
        
        // Add new item
        CollectionItem newItem = new CollectionItem(itemStringID, quantity, source);
        collectionBox.add(newItem);
        ModLogger.debug("Added %d x %s to collection box (player auth=%d, source=%s)",
            quantity, itemStringID, ownerAuth, source);
        updateAccessTime();
    }
    
    /**
     * Remove item from collection box by index.
     */
    public CollectionItem removeFromCollectionBox(int index) {
        if (index < 0 || index >= collectionBox.size()) {
            return null;
        }
        CollectionItem item = collectionBox.remove(index);
        updateAccessTime();
        return item;
    }

    /**
     * Reinsert an item into the collection box at a specific index (used for rollbacks).
     */
    public void insertIntoCollectionBox(int index, CollectionItem item) {
        if (item == null) {
            return;
        }
        if (index < 0 || index > collectionBox.size()) {
            collectionBox.add(item);
        } else {
            collectionBox.add(index, item);
        }
        updateAccessTime();
    }
    
    /**
     * Clear entire collection box.
     */
    public void clearCollectionBox() {
        collectionBox.clear();
        updateAccessTime();
    }
    
    // ===== SALE HISTORY MANAGEMENT =====
    
    /**
     * Add sale notification to history.
     * Keeps only last N notifications (default 20).
     */
    public void addSaleNotification(SaleNotification notification) {
        saleHistory.add(0, notification);  // Add to front (most recent first)
        
        // Trim old notifications
        int maxHistory = 20;  // Could be configurable
        while (saleHistory.size() > maxHistory) {
            saleHistory.remove(saleHistory.size() - 1);
        }
        
        updateAccessTime();
        ModLogger.debug("Added sale notification for player auth=%d: %s", 
            ownerAuth, notification.formatUIMessage());
    }
    
    /**
     * Clear sale history.
     */
    public void clearSaleHistory() {
        saleHistory.clear();
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
    
    // ===== LEGACY COMPATIBILITY =====
    
    /**
     * @deprecated Use getSellInventory() instead
     */
    @Deprecated(since = "1.0", forRemoval = true)
    /**
     * @deprecated Use `getSellInventory()` instead. This compatibility wrapper is scheduled
     * for removal in a future release — update call sites to `getSellInventory()`.
     */
    public Inventory getInventory() {
        return sellInventory;
    }
    
    /**
     * @deprecated Use getSellSlotCount() instead
     */
    @Deprecated(since = "1.0", forRemoval = true)
    /**
     * @deprecated Use `getSellSlotCount()` instead. This compatibility wrapper is scheduled
     * for removal in a future release — update call sites to `getSellSlotCount()`.
     */
    public int getSlotCount() {
        return sellInventory.getSize();
    }
    
    /**
     * @deprecated Use isValidSellSlot() instead
     */
    @Deprecated(since = "1.0", forRemoval = true)
    /**
     * @deprecated Use `isValidSellSlot(int)` instead. This wrapper will be removed in a
     * future release — update call sites accordingly.
     */
    public boolean isValidSlot(int slot) {
        return isValidSellSlot(slot);
    }
    
    /**
     * @deprecated Use recordSellOfferCreated() instead
     */
    @Deprecated(since = "1.0", forRemoval = true)
    /**
     * @deprecated Use `recordSellOfferCreated()` instead. This compatibility method is
     * scheduled for removal — migrate to the newer method.
     */
    public void recordOfferCreated() {
        recordSellOfferCreated();
    }
    
    /**
     * @deprecated Use recordSellOfferCancelled() instead
     */
    @Deprecated(since = "1.0", forRemoval = true)
    /**
     * @deprecated Use `recordSellOfferCancelled()` instead. This compatibility wrapper will
     * be removed in a future release.
     */
    public void recordOfferCancelled() {
        recordSellOfferCancelled();
    }
    
    /**
     * @deprecated Use recordSellOfferCompleted() instead
     */
    @Deprecated(since = "1.0", forRemoval = true)
    /**
     * @deprecated Use `recordSellOfferCompleted()` instead. This wrapper remains for
     * backward compatibility and will be removed later.
     */
    public void recordOfferCompleted() {
        recordSellOfferCompleted();
    }
    
    /**
     * @deprecated Use getTotalSellOffersCreated() instead
     */
    @Deprecated(since = "1.0", forRemoval = true)
    /**
     * @deprecated Use `getTotalSellOffersCreated()` instead. Remove usage in future.
     */
    public int getTotalOffersCreated() {
        return totalSellOffersCreated;
    }
    
    /**
     * @deprecated Use getTotalSellOffersCancelled() instead
     */
    @Deprecated(since = "1.0", forRemoval = true)
    /**
     * @deprecated Use `getTotalSellOffersCancelled()` instead. This will be removed.
     */
    public int getTotalOffersCancelled() {
        return totalSellOffersCancelled;
    }
    
    /**
     * @deprecated Use getTotalSellOffersCompleted() instead
     */
    @Deprecated(since = "1.0", forRemoval = true)
    /**
     * @deprecated Use `getTotalSellOffersCompleted()` instead. Update call sites.
     */
    public int getTotalOffersCompleted() {
        return totalSellOffersCompleted;
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
        for (CollectionItem item : collectionBox) {
            collectionData.addSaveData(item.getSaveData());
        }
        save.addSaveData(collectionData);
        
        // Save sale history
        SaveData historyData = new SaveData("SALE_HISTORY");
        for (SaleNotification notification : saleHistory) {
            historyData.addSaveData(notification.getSaveData());
        }
        save.addSaveData(historyData);
        
        // Save settings
        save.addBoolean("autoSendToBank", autoSendToBank);
        save.addBoolean("notifyPartialSales", notifyPartialSales);
        save.addBoolean("playSoundOnSale", playSoundOnSale);
        
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

        ModLogger.debug("Saved GE data for auth=%d: %d sell slots, %d buy orders, %d collection items, %d notifications",
            ownerAuth, sellInventory.getSize(), buyOrders.length, collectionBox.size(), saleHistory.size());
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
        
        // Load sale history
        saleHistory.clear();
        LoadData historyData = save.getFirstLoadDataByName("SALE_HISTORY");
        if (historyData != null) {
            for (LoadData notifData : historyData.getLoadDataByName("SALE_NOTIFICATION")) {
                saleHistory.add(SaleNotification.fromSaveData(notifData));
            }
        }
        
        // Load settings
        this.autoSendToBank = save.getBoolean("autoSendToBank", true);
        this.notifyPartialSales = save.getBoolean("notifyPartialSales", true);
        this.playSoundOnSale = save.getBoolean("playSoundOnSale", true);
        
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
            ownerAuth, sellInventory.getSize(), getActiveBuyOrderCount(), collectionBox.size(), saleHistory.size());
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
}
