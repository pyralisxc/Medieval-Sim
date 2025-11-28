package medievalsim.grandexchange.domain;

import necesse.engine.registries.ItemRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;

/**
 * Represents an item in a player's GE collection box.
 * 
 * Collection box is NOT an Inventory - it's a list of items that can accumulate
 * without slot limitations. Similar to settler mission rewards or mail attachments.
 * 
 * Items are added to collection box when:
 * - Player purchases from market (if auto-send to bank is disabled or bank is full)
 * - Offer expires and item returns (slot needs to be freed)
 * - Sale completes and unsold portion returns
 */
public class CollectionItem {
    
    private String itemStringID;
    private int quantity;
    private long timestamp;        // When item was added
    private String source;         // "purchase", "expired_offer", "cancelled_offer", "partial_sale"
    
    /**
     * Create new collection item.
     */
    public CollectionItem(String itemStringID, int quantity, String source) {
        this.itemStringID = itemStringID;
        this.quantity = quantity;
        this.timestamp = System.currentTimeMillis();
        this.source = source != null ? source : "unknown";
    }
    
    /**
     * Private constructor for loading from save data.
     */
    private CollectionItem(String itemStringID, int quantity, long timestamp, String source) {
        this.itemStringID = itemStringID;
        this.quantity = quantity;
        this.timestamp = timestamp;
        this.source = source;
    }
    
    // ===== ACCESSORS =====
    
    public String getItemStringID() {
        return itemStringID;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    public void setQuantity(int quantity) {
        this.quantity = Math.max(0, quantity);
    }
    
    public void addQuantity(int amount) {
        this.quantity += amount;
    }
    
    public void removeQuantity(int amount) {
        this.quantity = Math.max(0, this.quantity - amount);
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getSource() {
        return source;
    }
    
    public Item getItem() {
        return ItemRegistry.getItem(itemStringID);
    }
    
    public String getItemDisplayName() {
        Item item = getItem();
        return item != null ? item.getDisplayName(item.getDefaultItem(null, 1)) : itemStringID;
    }
    
    /**
     * Convert to InventoryItem for transfer to player inventory.
     */
    public InventoryItem toInventoryItem() {
        Item item = getItem();
        if (item == null) {
            return null;
        }
        return new InventoryItem(item, quantity);
    }
    
    /**
     * Check if this item can be merged with another (same item type).
     */
    public boolean canMergeWith(CollectionItem other) {
        return other != null && this.itemStringID.equals(other.itemStringID);
    }
    
    /**
     * Merge another collection item into this one (combines quantities).
     */
    public void mergeWith(CollectionItem other) {
        if (canMergeWith(other)) {
            this.quantity += other.quantity;
        }
    }
    
    /**
     * Check if item is valid (exists in registry).
     */
    public boolean isValid() {
        return getItem() != null;
    }
    
    // ===== PERSISTENCE =====
    
    /**
     * Save to SaveData.
     */
    public SaveData getSaveData() {
        SaveData save = new SaveData("COLLECTION_ITEM");
        save.addUnsafeString("itemStringID", itemStringID);
        save.addInt("quantity", quantity);
        save.addLong("timestamp", timestamp);
        save.addUnsafeString("source", source);
        return save;
    }
    
    /**
     * Load from SaveData.
     */
    public static CollectionItem fromSaveData(LoadData load) {
        String itemStringID = load.getUnsafeString("itemStringID");
        int quantity = load.getInt("quantity", 1);
        long timestamp = load.getLong("timestamp", System.currentTimeMillis());
        String source = load.getUnsafeString("source", "unknown");
        return new CollectionItem(itemStringID, quantity, timestamp, source);
    }
    
    // ===== UTILITY =====
    
    @Override
    public String toString() {
        return String.format("CollectionItem{item=%s, qty=%d, source=%s, age=%dms}",
            itemStringID, quantity, source, System.currentTimeMillis() - timestamp);
    }
    
    /**
     * Get formatted description for UI.
     */
    public String getDescription() {
        return String.format("%s x%d", getItemDisplayName(), quantity);
    }
    
    /**
     * Get formatted source description for UI.
     */
    public String getSourceDescription() {
        switch (source) {
            case "purchase":
                return "Purchased from market";
            case "expired_offer":
                return "Returned from expired offer";
            case "cancelled_offer":
                return "Returned from cancelled offer";
            case "partial_sale":
                return "Unsold items from partial sale";
            default:
                return "Unknown source";
        }
    }
    
    /**
     * Get time since added (in minutes).
     */
    public long getMinutesSinceAdded() {
        return (System.currentTimeMillis() - timestamp) / 60000;
    }
}
