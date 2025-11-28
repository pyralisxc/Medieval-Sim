package medievalsim.grandexchange.domain;

import medievalsim.util.ModLogger;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.registries.ItemRegistry;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;

/**
 * Represents a single item listing on the Grand Exchange.
 * 
 * Features:
 * - Item being sold
 * - Quantity available
 * - Price per item (in coins)
 * - Seller information
 * - Listing creation time
 * - Expiration time (optional)
 */
public class MarketListing {
    
    // Unique listing ID
    private final long listingID;
    
    // Seller information
    private final long sellerAuth;
    private final String sellerName;
    
    // Item information
    private final String itemStringID;
    private int quantity;
    private final int pricePerItem; // In coins
    
    // Timestamps
    private final long createdTime;
    private long expirationTime; // 0 = no expiration
    
    // Status
    private boolean active;
    
    /**
     * Create a new market listing.
     * @param listingID Unique listing ID
     * @param sellerAuth Seller's authentication ID
     * @param sellerName Seller's display name
     * @param itemStringID Item string ID
     * @param quantity Quantity available
     * @param pricePerItem Price per item in coins
     */
    public MarketListing(long listingID, long sellerAuth, String sellerName,
                        String itemStringID, int quantity, int pricePerItem) {
        this(listingID, sellerAuth, sellerName, itemStringID, quantity, pricePerItem,
            System.currentTimeMillis(), 0, true);

        ModLogger.debug("Created market listing ID=%d: %s x%d @ %d coins each (seller=%s)",
            listingID, itemStringID, quantity, pricePerItem, sellerName);
    }

    /**
     * Private constructor for loading from save data.
     */
    private MarketListing(long listingID, long sellerAuth, String sellerName,
                         String itemStringID, int quantity, int pricePerItem,
                         long createdTime, long expirationTime, boolean active) {
        this.listingID = listingID;
        this.sellerAuth = sellerAuth;
        this.sellerName = sellerName;
        this.itemStringID = itemStringID;
        this.quantity = quantity;
        this.pricePerItem = pricePerItem;
        this.createdTime = createdTime;
        this.expirationTime = expirationTime;
        this.active = active;
    }
    
    // ===== GETTERS =====
    
    public long getListingID() {
        return listingID;
    }
    
    public long getSellerAuth() {
        return sellerAuth;
    }
    
    public String getSellerName() {
        return sellerName;
    }
    
    public String getItemStringID() {
        return itemStringID;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    public int getPricePerItem() {
        return pricePerItem;
    }
    
    public int getTotalPrice() {
        return quantity * pricePerItem;
    }
    
    public long getCreatedTime() {
        return createdTime;
    }
    
    public long getExpirationTime() {
        return expirationTime;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public boolean isExpired() {
        if (expirationTime == 0) {
            return false;
        }
        return System.currentTimeMillis() >= expirationTime;
    }
    
    // ===== SETTERS =====
    
    public void setExpirationTime(long expirationTime) {
        this.expirationTime = expirationTime;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    /**
     * Reduce the quantity by the specified amount.
     * @param amount Amount to reduce
     * @return true if successful, false if not enough quantity
     */
    public boolean reduceQuantity(int amount) {
        if (amount <= 0 || amount > quantity) {
            return false;
        }
        quantity -= amount;
        if (quantity == 0) {
            active = false;
        }
        return true;
    }
    
    /**
     * Get the item as an InventoryItem.
     * @return InventoryItem or null if item doesn't exist
     */
    public InventoryItem getInventoryItem() {
        Item item = ItemRegistry.getItem(itemStringID);
        if (item == null) {
            ModLogger.error("Failed to get item for listing ID=%d: item '%s' not found",
                listingID, itemStringID);
            return null;
        }
        return new InventoryItem(item, quantity);
    }

    // ===== SAVE/LOAD =====

    /**
     * Save listing data to SaveData.
     */
    public void addSaveData(SaveData save) {
        save.addLong("listingID", listingID);
        save.addLong("sellerAuth", sellerAuth);
        save.addUnsafeString("sellerName", sellerName);
        save.addUnsafeString("itemStringID", itemStringID);
        save.addInt("quantity", quantity);
        save.addInt("pricePerItem", pricePerItem);
        save.addLong("createdTime", createdTime);
        save.addLong("expirationTime", expirationTime);
        save.addBoolean("active", active);
    }

    /**
     * Load listing data from LoadData.
     */
    public static MarketListing fromLoadData(LoadData load) {
        long listingID = load.getLong("listingID");
        long sellerAuth = load.getLong("sellerAuth");
        String sellerName = load.getUnsafeString("sellerName");
        String itemStringID = load.getUnsafeString("itemStringID");
        int quantity = load.getInt("quantity");
        int pricePerItem = load.getInt("pricePerItem");
        long createdTime = load.getLong("createdTime");
        long expirationTime = load.getLong("expirationTime");
        boolean active = load.getBoolean("active");

        return new MarketListing(listingID, sellerAuth, sellerName,
            itemStringID, quantity, pricePerItem, createdTime, expirationTime, active);
    }

    /**
     * Create a MarketListing from synchronized packet data.
     */
    public static MarketListing fromSyncData(long listingID, long sellerAuth, String sellerName,
                                             String itemStringID, int quantity, int pricePerItem,
                                             long createdTime, long expirationTime, boolean active) {
        return new MarketListing(listingID, sellerAuth, sellerName, itemStringID, quantity, pricePerItem,
            createdTime, expirationTime, active);
    }
}

