package medievalsim.grandexchange.domain;

import necesse.engine.registries.ItemRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.inventory.item.Item;

/**
 * Represents a notification of a completed sale in the Grand Exchange.
 * 
 * Sale notifications are stored per-player and displayed in the Settings/Notifications
 * tab. Players can see recent sales with details about what sold, quantity, price, etc.
 * 
 * Also used for chat notifications when sales complete.
 */
public class SaleNotification {
    
    private final long timestamp;
    private final String itemStringID;
    private final int quantitySold;
    private final int pricePerItem;
    private final int totalCoins;
    private final boolean isPartial;     // True if offer still has remaining quantity
    private final String buyerName;      // Optional: who bought it (may be null)
    
    /**
     * Create new sale notification.
     */
    public SaleNotification(String itemStringID, int quantitySold, int pricePerItem, 
                           int totalCoins, boolean isPartial, String buyerName) {
        this.timestamp = System.currentTimeMillis();
        this.itemStringID = itemStringID;
        this.quantitySold = quantitySold;
        this.pricePerItem = pricePerItem;
        this.totalCoins = totalCoins;
        this.isPartial = isPartial;
        this.buyerName = buyerName;
    }
    
    /**
     * Private constructor for loading from save data.
     */
    private SaleNotification(long timestamp, String itemStringID, int quantitySold, 
                            int pricePerItem, int totalCoins, boolean isPartial, String buyerName) {
        this.timestamp = timestamp;
        this.itemStringID = itemStringID;
        this.quantitySold = quantitySold;
        this.pricePerItem = pricePerItem;
        this.totalCoins = totalCoins;
        this.isPartial = isPartial;
        this.buyerName = buyerName;
    }
    
    // ===== ACCESSORS =====
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getItemStringID() {
        return itemStringID;
    }
    
    public int getQuantitySold() {
        return quantitySold;
    }
    
    public int getPricePerItem() {
        return pricePerItem;
    }
    
    public int getTotalCoins() {
        return totalCoins;
    }
    
    public boolean isPartial() {
        return isPartial;
    }
    
    public String getBuyerName() {
        return buyerName;
    }
    
    public Item getItem() {
        return ItemRegistry.getItem(itemStringID);
    }
    
    public String getItemDisplayName() {
        Item item = getItem();
        return item != null ? item.getDisplayName(item.getDefaultItem(null, 1)) : itemStringID;
    }
    
    // ===== FORMATTING =====
    
    /**
     * Format notification for chat message.
     * Example: "Iron Ore x64 sold for 10 coins each (Total: 640 coins)"
     * Example (partial): "Iron Ore x32 sold for 10 coins each (Total: 320 coins) [PARTIAL]"
     */
    public String formatChatMessage() {
        StringBuilder msg = new StringBuilder();
        msg.append(getItemDisplayName())
           .append(" x").append(quantitySold)
           .append(" sold for ").append(pricePerItem)
           .append(" coins each (Total: ").append(totalCoins)
           .append(" coins)");
        
        if (isPartial) {
            msg.append(" [PARTIAL]");
        }
        
        if (buyerName != null && !buyerName.isEmpty()) {
            msg.append(" - Buyer: ").append(buyerName);
        }
        
        return msg.toString();
    }
    
    /**
     * Format notification for UI display (shorter).
     * Example: "Iron Ore x64 → 640 coins"
     * Example (partial): "Iron Ore x32 → 320 coins [PARTIAL]"
     */
    public String formatUIMessage() {
        StringBuilder msg = new StringBuilder();
        msg.append(getItemDisplayName())
           .append(" x").append(quantitySold)
           .append(" → ").append(totalCoins)
           .append(" coins");
        
        if (isPartial) {
            msg.append(" [PARTIAL]");
        }
        
        return msg.toString();
    }
    
    /**
     * Format notification with timestamp.
     * Example: "Iron Ore x64 sold for 640 coins (5 minutes ago)"
     */
    public String formatWithTime() {
        return formatUIMessage() + " (" + getTimeSinceText() + ")";
    }
    
    /**
     * Get human-readable time since notification.
     */
    public String getTimeSinceText() {
        long elapsedMs = System.currentTimeMillis() - timestamp;
        long minutes = elapsedMs / 60000;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + " day" + (days == 1 ? "" : "s") + " ago";
        } else if (hours > 0) {
            return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes == 1 ? "" : "s") + " ago";
        } else {
            return "Just now";
        }
    }
    
    /**
     * Get minutes since notification.
     */
    public long getMinutesSince() {
        return (System.currentTimeMillis() - timestamp) / 60000;
    }
    
    /**
     * Get hours since notification.
     */
    public long getHoursSince() {
        return (System.currentTimeMillis() - timestamp) / 3600000;
    }
    
    // ===== PERSISTENCE =====
    
    public SaveData getSaveData() {
        SaveData save = new SaveData("SALE_NOTIFICATION");
        save.addLong("timestamp", timestamp);
        save.addUnsafeString("itemStringID", itemStringID);
        save.addInt("quantitySold", quantitySold);
        save.addInt("pricePerItem", pricePerItem);
        save.addInt("totalCoins", totalCoins);
        save.addBoolean("isPartial", isPartial);
        save.addUnsafeString("buyerName", buyerName != null ? buyerName : "");
        return save;
    }
    
    public static SaleNotification fromSaveData(LoadData load) {
        long timestamp = load.getLong("timestamp");
        String itemStringID = load.getUnsafeString("itemStringID");
        int quantitySold = load.getInt("quantitySold");
        int pricePerItem = load.getInt("pricePerItem");
        int totalCoins = load.getInt("totalCoins");
        boolean isPartial = load.getBoolean("isPartial");
        String buyerName = load.getUnsafeString("buyerName");
        if (buyerName.isEmpty()) buyerName = null;
        
        return new SaleNotification(timestamp, itemStringID, quantitySold, 
            pricePerItem, totalCoins, isPartial, buyerName);
    }
    
    @Override
    public String toString() {
        return String.format("SaleNotification{item=%s, qty=%d, price=%d, total=%d, partial=%b, buyer=%s, age=%dm}",
            itemStringID, quantitySold, pricePerItem, totalCoins, isPartial, buyerName, getMinutesSince());
    }
}
