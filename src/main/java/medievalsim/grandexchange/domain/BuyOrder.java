package medievalsim.grandexchange.domain;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.inventory.item.Item;

/**
 * Represents a buy order in the Grand Exchange.
 * 
 * Buy orders allow players to bid for items they want to purchase.
 * When enabled (checkbox), coins are escrowed and the order actively matches
 * against sell offers. When disabled, order is in draft state.
 * 
 * Architecture:
 * - Player selects item, sets quantity, price, duration
 * - Checkbox unchecked: DRAFT state (no coin escrow, no matching)
 * - Checkbox checked: ACTIVE state (coins escrowed, matching enabled)
 * - Matches execute when compatible sell offer is created/enabled
 */
public class BuyOrder {
    
    public enum BuyOrderState {
        DRAFT,          // Created but not enabled (no escrow, no matching)
        ACTIVE,         // Enabled, coins escrowed, matching against sell offers
        PARTIAL,        // Partially filled (some items received)
        COMPLETED,      // Fully filled (all items received)
        EXPIRED,        // Duration exceeded
        CANCELLED       // Manually cancelled by player
    }
    
    private final long orderID;
    private final long playerAuth;
    private final String playerName;
    private final int slotIndex;          // Which buy order slot (0-2)
    
    private String itemStringID;          // Item to purchase (can be changed in DRAFT state)
    private int quantityTotal;            // Total quantity requested
    private int quantityRemaining;        // How many still needed
    private int pricePerItem;             // Maximum price willing to pay
    
    private boolean enabled;              // Checkbox state (DRAFT when false, ACTIVE when true)
    private BuyOrderState state;
    
    private final long creationTime;
    private int durationDays;             // How long order stays active
    private long expirationTime;          // Calculated from creationTime + duration
    
    private long lastModifiedTime;
    
    /**
     * Create new buy order (initially in DRAFT state).
     */
    public BuyOrder(long orderID, long playerAuth, String playerName, int slotIndex) {
        this.orderID = orderID;
        this.playerAuth = playerAuth;
        this.playerName = playerName;
        this.slotIndex = slotIndex;
        
        this.itemStringID = null;
        this.quantityTotal = 0;
        this.quantityRemaining = 0;
        this.pricePerItem = 0;
        
        this.enabled = false;
        this.state = BuyOrderState.DRAFT;
        
        this.creationTime = System.currentTimeMillis();
        this.durationDays = ModConfig.GrandExchange.offerExpirationHours / 24;
        this.expirationTime = calculateExpirationTime();
        this.lastModifiedTime = this.creationTime;
    }
    
    /**
     * Private constructor for loading from save data.
     */
    private BuyOrder(long orderID, long playerAuth, String playerName, int slotIndex,
                     String itemStringID, int quantityTotal, int quantityRemaining, int pricePerItem,
                     boolean enabled, BuyOrderState state, long creationTime, int durationDays,
                     long expirationTime, long lastModifiedTime) {
        this.orderID = orderID;
        this.playerAuth = playerAuth;
        this.playerName = playerName;
        this.slotIndex = slotIndex;
        this.itemStringID = itemStringID;
        this.quantityTotal = quantityTotal;
        this.quantityRemaining = quantityRemaining;
        this.pricePerItem = pricePerItem;
        this.enabled = enabled;
        this.state = state;
        this.creationTime = creationTime;
        this.durationDays = durationDays;
        this.expirationTime = expirationTime;
        this.lastModifiedTime = lastModifiedTime;
    }
    
    /**
     * Factory method for creating BuyOrder from packet data (client-side).
     * Used when client receives sync packet from server.
     */
    public static BuyOrder fromPacketData(long orderID, long playerAuth, int slotIndex,
                                          String itemStringID, int quantityTotal, int quantityRemaining,
                                          int pricePerItem, boolean enabled, BuyOrderState state, int durationDays) {
        long now = System.currentTimeMillis();
        long expirationTime = now + (durationDays * 24L * 60 * 60 * 1000);

        return new BuyOrder(
            orderID, playerAuth,
            "Unknown", // playerName not sent in packet (not needed client-side)
            slotIndex, itemStringID, quantityTotal, quantityRemaining, pricePerItem,
            enabled,
            state, now, durationDays, expirationTime, now
        );
    }
    
    // ===== CORE ACCESSORS =====
    
    public long getOrderID() {
        return orderID;
    }
    
    public long getPlayerAuth() {
        return playerAuth;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public int getSlotIndex() {
        return slotIndex;
    }
    
    public String getItemStringID() {
        return itemStringID;
    }
    
    public int getQuantityTotal() {
        return quantityTotal;
    }
    
    public int getQuantityRemaining() {
        return quantityRemaining;
    }
    
    public int getQuantityFilled() {
        return quantityTotal - quantityRemaining;
    }
    
    public int getPricePerItem() {
        return pricePerItem;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public BuyOrderState getState() {
        return state;
    }
    
    public long getCreationTime() {
        return creationTime;
    }
    
    public int getDurationDays() {
        return durationDays;
    }
    
    public long getExpirationTime() {
        return expirationTime;
    }
    
    public long getLastModifiedTime() {
        return lastModifiedTime;
    }
    
    /**
     * Fill quantity (reduce remaining after purchase).
     */
    public boolean fillQuantity(int amount) {
        if (amount > quantityRemaining) {
            ModLogger.error("Cannot fill more than remaining: amount=%d, remaining=%d", amount, quantityRemaining);
            return false;
        }
        
        this.quantityRemaining -= amount;
        this.lastModifiedTime = System.currentTimeMillis();
        
        // Update state based on completion
        if (quantityRemaining == 0) {
            this.state = BuyOrderState.COMPLETED;
        } else if (quantityRemaining < quantityTotal) {
            this.state = BuyOrderState.PARTIAL;
        }
        
        return true;
    }
    
    /**
     * Set quantity remaining (for rollback operations only).
     */
    public void setQuantityRemaining(int quantity) {
        this.quantityRemaining = quantity;
        this.lastModifiedTime = System.currentTimeMillis();
    }
    
    // ===== STATE MANAGEMENT =====
    
    /**
     * Configure buy order (only in DRAFT state).
     */
    public boolean configure(String itemStringID, int quantity, int pricePerItem, int durationDays) {
        if (state != BuyOrderState.DRAFT) {
            ModLogger.warn("Cannot configure buy order %d: not in DRAFT state (state=%s)", orderID, state);
            return false;
        }
        
        // Validate item
        Item item = ItemRegistry.getItem(itemStringID);
        if (item == null) {
            ModLogger.warn("Cannot configure buy order %d: invalid item '%s'", orderID, itemStringID);
            return false;
        }
        
        // Validate quantity
        if (quantity <= 0) {
            ModLogger.warn("Cannot configure buy order %d: invalid quantity %d", orderID, quantity);
            return false;
        }
        
        // Validate price
        if (!ModConfig.GrandExchange.isValidPrice(pricePerItem)) {
            ModLogger.warn("Cannot configure buy order %d: invalid price %d", orderID, pricePerItem);
            return false;
        }
        
        // Validate duration
        int maxDays = ModConfig.GrandExchange.offerExpirationHours / 24;
        if (durationDays < 1 || durationDays > maxDays) {
            ModLogger.warn("Cannot configure buy order %d: invalid duration %d days", orderID, durationDays);
            return false;
        }
        
        this.itemStringID = itemStringID;
        this.quantityTotal = quantity;
        this.quantityRemaining = quantity;
        this.pricePerItem = pricePerItem;
        this.durationDays = durationDays;
        this.expirationTime = calculateExpirationTime();
        this.lastModifiedTime = System.currentTimeMillis();
        
        ModLogger.debug("Configured buy order %d: %d x %s @ %d coins/ea, duration=%d days",
            orderID, quantity, itemStringID, pricePerItem, durationDays);
        
        return true;
    }
    
    /**
     * Enable buy order (DRAFT → ACTIVE, requires coin escrow).
     */
    public boolean enable() {
        if (state != BuyOrderState.DRAFT) {
            ModLogger.warn("Cannot enable buy order %d: not in DRAFT state (state=%s)", orderID, state);
            return false;
        }
        
        if (itemStringID == null || quantityRemaining <= 0) {
            ModLogger.warn("Cannot enable buy order %d: not configured", orderID);
            return false;
        }
        
        this.enabled = true;
        this.state = BuyOrderState.ACTIVE;
        this.lastModifiedTime = System.currentTimeMillis();
        
        ModLogger.info("Enabled buy order %d: %d x %s @ %d coins/ea", 
            orderID, quantityRemaining, itemStringID, pricePerItem);
        
        return true;
    }
    
    /**
     * Disable buy order (ACTIVE → DRAFT, refunds escrowed coins).
     */
    public boolean disable() {
        if (state != BuyOrderState.ACTIVE && state != BuyOrderState.PARTIAL) {
            ModLogger.warn("Cannot disable buy order %d: not active (state=%s)", orderID, state);
            return false;
        }
        
        this.enabled = false;
        this.state = BuyOrderState.DRAFT;
        this.lastModifiedTime = System.currentTimeMillis();
        
        ModLogger.info("Disabled buy order %d (refund %d coins)", 
            orderID, quantityRemaining * pricePerItem);
        
        return true;
    }
    
    /**
     * Record a purchase (partial fill).
     */
    public boolean recordPurchase(int quantity, int actualPricePerItem) {
        if (state != BuyOrderState.ACTIVE && state != BuyOrderState.PARTIAL) {
            ModLogger.warn("Cannot record purchase for buy order %d: not active (state=%s)", orderID, state);
            return false;
        }
        
        if (quantity <= 0 || quantity > quantityRemaining) {
            ModLogger.warn("Invalid purchase quantity %d for buy order %d (remaining=%d)", 
                quantity, orderID, quantityRemaining);
            return false;
        }
        
        quantityRemaining -= quantity;
        lastModifiedTime = System.currentTimeMillis();
        
        if (quantityRemaining == 0) {
            state = BuyOrderState.COMPLETED;
            enabled = false;
            ModLogger.info("Buy order %d completed: purchased %d x %s", 
                orderID, quantityTotal, itemStringID);
        } else {
            state = BuyOrderState.PARTIAL;
            ModLogger.info("Buy order %d partial: purchased %d, remaining %d", 
                orderID, quantity, quantityRemaining);
        }
        
        return true;
    }
    
    /**
     * Cancel buy order (any state → CANCELLED).
     */
    public void cancel() {
        this.state = BuyOrderState.CANCELLED;
        this.enabled = false;
        this.lastModifiedTime = System.currentTimeMillis();
        ModLogger.info("Buy order %d cancelled", orderID);
    }
    
    /**
     * Expire buy order (time limit exceeded).
     */
    public void expire() {
        if (state == BuyOrderState.COMPLETED || state == BuyOrderState.CANCELLED) {
            return; // Already finished
        }
        
        this.state = BuyOrderState.EXPIRED;
        this.enabled = false;
        this.lastModifiedTime = System.currentTimeMillis();
        ModLogger.info("Buy order %d expired (remaining=%d)", orderID, quantityRemaining);
    }
    
    // ===== QUERIES =====
    
    public boolean isActive() {
        return enabled && (state == BuyOrderState.ACTIVE || state == BuyOrderState.PARTIAL);
    }
    
    public boolean isCompleted() {
        return state == BuyOrderState.COMPLETED;
    }
    
    public boolean isExpired() {
        return state == BuyOrderState.EXPIRED;
    }
    
    public boolean isCancelled() {
        return state == BuyOrderState.CANCELLED;
    }
    
    public boolean isConfigured() {
        return itemStringID != null && quantityTotal > 0 && pricePerItem > 0;
    }
    
    public boolean canMatch() {
        return isActive() && quantityRemaining > 0;
    }
    
    /**
     * Check if this buy order can match a sell offer.
     */
    public boolean canMatchSellOffer(GEOffer sellOffer) {
        if (!canMatch() || sellOffer == null || !sellOffer.isActive()) {
            return false;
        }
        
        // Must be same item
        if (!itemStringID.equals(sellOffer.getItemStringID())) {
            return false;
        }
        
        // Buy price must be >= sell price
        if (pricePerItem < sellOffer.getPricePerItem()) {
            return false;
        }
        
        // Can't buy from yourself
        if (playerAuth == sellOffer.getPlayerAuth()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Calculate total coins required (remaining quantity * price).
     */
    public int getTotalCoinsRequired() {
        return quantityRemaining * pricePerItem;
    }
    
    /**
     * Calculate total coins spent (filled quantity * price).
     */
    public int getTotalCoinsSpent() {
        return getQuantityFilled() * pricePerItem;
    }
    
    // ===== UTILITY =====
    
    private long calculateExpirationTime() {
        long ticksPerDay = 24 * 60 * 60 * 1000; // milliseconds per day
        return creationTime + (durationDays * ticksPerDay);
    }
    
    public Item getItem() {
        return itemStringID != null ? ItemRegistry.getItem(itemStringID) : null;
    }
    
    public String getItemDisplayName() {
        Item item = getItem();
        return item != null ? item.getDisplayName(item.getDefaultItem(null, 1)) : (itemStringID != null ? itemStringID : "None");
    }
    
    // ===== PERSISTENCE =====
    
    public SaveData getSaveData() {
        SaveData save = new SaveData("BUY_ORDER");
        save.addLong("orderID", orderID);
        save.addLong("playerAuth", playerAuth);
        save.addUnsafeString("playerName", playerName);
        save.addInt("slotIndex", slotIndex);
        
        save.addUnsafeString("itemStringID", itemStringID != null ? itemStringID : "");
        save.addInt("quantityTotal", quantityTotal);
        save.addInt("quantityRemaining", quantityRemaining);
        save.addInt("pricePerItem", pricePerItem);
        
        save.addBoolean("enabled", enabled);
        save.addUnsafeString("state", state.name());
        
        save.addLong("creationTime", creationTime);
        save.addInt("durationDays", durationDays);
        save.addLong("expirationTime", expirationTime);
        save.addLong("lastModifiedTime", lastModifiedTime);
        
        return save;
    }
    
    public static BuyOrder fromSaveData(LoadData load) {
        long orderID = load.getLong("orderID");
        long playerAuth = load.getLong("playerAuth");
        String playerName = load.getUnsafeString("playerName");
        int slotIndex = load.getInt("slotIndex");
        
        String itemStringID = load.getUnsafeString("itemStringID");
        if (itemStringID.isEmpty()) itemStringID = null;
        
        int quantityTotal = load.getInt("quantityTotal");
        int quantityRemaining = load.getInt("quantityRemaining");
        int pricePerItem = load.getInt("pricePerItem");
        
        boolean enabled = load.getBoolean("enabled");
        BuyOrderState state = BuyOrderState.valueOf(load.getUnsafeString("state", "DRAFT"));
        
        long creationTime = load.getLong("creationTime");
        int durationDays = load.getInt("durationDays");
        long expirationTime = load.getLong("expirationTime");
        long lastModifiedTime = load.getLong("lastModifiedTime");
        
        return new BuyOrder(orderID, playerAuth, playerName, slotIndex,
            itemStringID, quantityTotal, quantityRemaining, pricePerItem,
            enabled, state, creationTime, durationDays, expirationTime, lastModifiedTime);
    }
    
    @Override
    public String toString() {
        return String.format("BuyOrder{id=%d, player=%s, slot=%d, item=%s, qty=%d/%d, price=%d, enabled=%b, state=%s}",
            orderID, playerName, slotIndex, itemStringID, quantityRemaining, quantityTotal, 
            pricePerItem, enabled, state);
    }
}
