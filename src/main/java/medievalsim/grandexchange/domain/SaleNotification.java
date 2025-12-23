package medievalsim.grandexchange.domain;

import medievalsim.util.TimeConstants;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.inventory.item.Item;

/**
 * Represents a notification of a completed trade (sale or purchase) in the Grand Exchange.
 *
 * Trade notifications are stored per-player and displayed in the History tab.
 * Players can see recent trades with details about what was traded, quantity, price, etc.
 *
 * Also used for chat notifications when trades complete.
 */
public class SaleNotification {

    private final long timestamp;
    private final String itemStringID;
    private final int quantityTraded;
    private final int pricePerItem;
    private final int totalCoins;
    private final boolean isPartial;        // True if offer still has remaining quantity
    private final String counterpartyName;  // The other party in the trade (buyer or seller name)
    private final boolean isSale;           // True if this player sold, false if purchased

    /**
     * Create new trade notification.
     */
    public SaleNotification(String itemStringID, int quantityTraded, int pricePerItem,
                           int totalCoins, boolean isPartial, String counterpartyName, boolean isSale) {
        this.timestamp = System.currentTimeMillis();
        this.itemStringID = itemStringID;
        this.quantityTraded = quantityTraded;
        this.pricePerItem = pricePerItem;
        this.totalCoins = totalCoins;
        this.isPartial = isPartial;
        this.counterpartyName = counterpartyName;
        this.isSale = isSale;
    }

    /**
     * Backward-compatible constructor for sale notifications (defaults to isSale=true).
     * @deprecated Use the full constructor with isSale parameter.
     * Migration timeline: Will be removed in version 1.2 (Q1 2026).
     * Update callers to use: new SaleNotification(itemID, qty, price, total, partial, counterparty, true)
     */
    @Deprecated
    public SaleNotification(String itemStringID, int quantityTraded, int pricePerItem,
                           int totalCoins, boolean isPartial, String counterpartyName) {
        this(itemStringID, quantityTraded, pricePerItem, totalCoins, isPartial, counterpartyName, true);
    }

    /**
     * Private constructor for loading from save data.
     */
    private SaleNotification(long timestamp, String itemStringID, int quantityTraded,
                            int pricePerItem, int totalCoins, boolean isPartial,
                            String counterpartyName, boolean isSale) {
        this.timestamp = timestamp;
        this.itemStringID = itemStringID;
        this.quantityTraded = quantityTraded;
        this.pricePerItem = pricePerItem;
        this.totalCoins = totalCoins;
        this.isPartial = isPartial;
        this.counterpartyName = counterpartyName;
        this.isSale = isSale;
    }

    // ===== ACCESSORS =====

    public long getTimestamp() {
        return timestamp;
    }

    public String getItemStringID() {
        return itemStringID;
    }

    /**
     * Get quantity traded (buy or sell).
     * @deprecated Use {@link #getQuantityTraded()} instead.
     * Migration timeline: Will be removed in version 1.2 (Q1 2026).
     */
    @Deprecated
    public int getQuantitySold() {
        return quantityTraded;
    }

    public int getQuantityTraded() {
        return quantityTraded;
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

    /**
     * Get counterparty name (buyer for sales, seller for purchases).
     * @deprecated Use {@link #getCounterpartyName()} instead.
     * Migration timeline: Will be removed in version 1.2 (Q1 2026).
     */
    @Deprecated
    public String getBuyerName() {
        return counterpartyName;
    }

    public String getCounterpartyName() {
        return counterpartyName;
    }

    public boolean isSale() {
        return isSale;
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
     * Example (sale): "Iron Ore x64 sold for 10 coins each (Total: 640 coins)"
     * Example (purchase): "Iron Ore x64 bought for 10 coins each (Total: 640 coins)"
     */
    public String formatChatMessage() {
        StringBuilder msg = new StringBuilder();
        String action = isSale ? "sold" : "bought";
        msg.append(getItemDisplayName())
           .append(" x").append(quantityTraded)
           .append(" ").append(action).append(" for ").append(pricePerItem)
           .append(" coins each (Total: ").append(totalCoins)
           .append(" coins)");

        if (isPartial) {
            msg.append(" [PARTIAL]");
        }

        if (counterpartyName != null && !counterpartyName.isEmpty()) {
            String label = isSale ? "Buyer" : "Seller";
            msg.append(" - ").append(label).append(": ").append(counterpartyName);
        }

        return msg.toString();
    }

    /**
     * Format notification for UI display (shorter).
     * Example (sale): "Iron Ore x64 → 640 coins"
     * Example (purchase): "Iron Ore x64 ← 640 coins"
     */
    public String formatUIMessage() {
        StringBuilder msg = new StringBuilder();
        String arrow = isSale ? " → " : " ← ";
        msg.append(getItemDisplayName())
           .append(" x").append(quantityTraded)
           .append(arrow).append(totalCoins)
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
        long minutes = elapsedMs / TimeConstants.MILLIS_PER_MINUTE;
        long hours = minutes / TimeConstants.MINUTES_PER_HOUR;
        long days = hours / TimeConstants.HOURS_PER_DAY;

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
        return (System.currentTimeMillis() - timestamp) / TimeConstants.MILLIS_PER_MINUTE;
    }

    /**
     * Get hours since notification.
     */
    public long getHoursSince() {
        return (System.currentTimeMillis() - timestamp) / TimeConstants.MILLIS_PER_HOUR;
    }

    // ===== PERSISTENCE =====

    public SaveData getSaveData() {
        SaveData save = new SaveData("SALE_NOTIFICATION");
        save.addLong("timestamp", timestamp);
        save.addUnsafeString("itemStringID", itemStringID);
        save.addInt("quantityTraded", quantityTraded);
        save.addInt("pricePerItem", pricePerItem);
        save.addInt("totalCoins", totalCoins);
        save.addBoolean("isPartial", isPartial);
        save.addUnsafeString("counterpartyName", counterpartyName != null ? counterpartyName : "");
        save.addBoolean("isSale", isSale);
        return save;
    }

    public static SaleNotification fromSaveData(LoadData load) {
        long timestamp = load.getLong("timestamp");
        String itemStringID = load.getUnsafeString("itemStringID");
        // Backward compatibility: try new field name first, fall back to old
        int quantityTraded = load.hasLoadDataByName("quantityTraded") 
            ? load.getInt("quantityTraded") 
            : load.getInt("quantitySold");
        int pricePerItem = load.getInt("pricePerItem");
        int totalCoins = load.getInt("totalCoins");
        boolean isPartial = load.getBoolean("isPartial");
        // Backward compatibility: try new field name first, fall back to old
        String counterpartyName = load.hasLoadDataByName("counterpartyName")
            ? load.getUnsafeString("counterpartyName")
            : load.getUnsafeString("buyerName");
        if (counterpartyName.isEmpty()) counterpartyName = null;
        // Backward compatibility: default to true (sale) for old entries
        boolean isSale = load.hasLoadDataByName("isSale") ? load.getBoolean("isSale") : true;

        return new SaleNotification(timestamp, itemStringID, quantityTraded,
            pricePerItem, totalCoins, isPartial, counterpartyName, isSale);
    }

    @Override
    public String toString() {
        String type = isSale ? "Sale" : "Purchase";
        return String.format("SaleNotification{type=%s, item=%s, qty=%d, price=%d, total=%d, partial=%b, counterparty=%s, age=%dm}",
            type, itemStringID, quantityTraded, pricePerItem, totalCoins, isPartial, counterpartyName, getMinutesSince());
    }
}
