package medievalsim.grandexchange.domain;

import medievalsim.config.ModConfig;
import medievalsim.util.ModLogger;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.registries.ItemRegistry;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;

import java.util.concurrent.TimeUnit;

/**
 * Represents a Grand Exchange offer (buy or sell).
 * 
 * STATE MACHINE ARCHITECTURE:
 * ============================
 * 
 * The offer has TWO independent state concepts:
 * 
 * 1. OfferState (business state): Tracks the offer's trading lifecycle
 *    DRAFT → ACTIVE → PARTIAL → COMPLETED/EXPIRED/CANCELLED
 *    
 * 2. enabled (boolean): Checkbox state controlled by player
 *    - false: Offer is DRAFT (not visible on market, not matchable)
 *    - true: Offer is ACTIVE/PARTIAL (visible on market, can be matched)
 * 
 * STATE TRANSITION RULES:
 * - DRAFT + enabled=false: Initial state, user hasn't checked checkbox
 * - DRAFT → ACTIVE: User checks checkbox (enable() method)
 * - ACTIVE + enabled=true: On market, can be matched
 * - ACTIVE → PARTIAL: Partially fulfilled (some quantity traded)
 * - PARTIAL + enabled=true: Still on market, remaining quantity matchable
 * - PARTIAL → COMPLETED: Fully fulfilled (all quantity traded)
 * - ACTIVE/PARTIAL → DRAFT: User unchecks checkbox (disable() method)
 * - Any → CANCELLED: User cancels offer
 * - Any → EXPIRED: Time limit reached
 * 
 * IMPORTANT: isActive() returns (enabled && (state==ACTIVE || state==PARTIAL))
 * This ensures only enabled, tradable offers appear in market.
 * 
 * Key Features:
 * - Linked to player's GE inventory slot (0-9 or configurable)
 * - Supports partial fulfillment (sell 100, 50 sold = PARTIAL state)
 * - Buy offers: bid for items with coins in escrow
 * - Tracks transaction history (coins received/spent, transaction count)
 * - Optional expiration based on config
 */
public class GEOffer {

    // ===== ENUMS =====

    public enum OfferType {
        SELL,  // Selling items (item in inventory slot)
        BUY    // Buying items (coins in escrow) - Phase 10
    }

    /**
     * Business lifecycle state of the offer.
     * Independent from enabled checkbox state.
     */
    public enum OfferState {
        DRAFT,      // Created, checkbox unchecked (not on market)
        ACTIVE,     // Checkbox checked, on market, can be matched
        PARTIAL,    // Some quantity traded, some remaining, still matchable
        COMPLETED,  // Fully traded (quantity remaining = 0)
        EXPIRED,    // Time limit reached
        CANCELLED   // Player cancelled
    }

    // ===== FIELDS =====

    // Identity
    private final long offerID;
    private final long playerAuth;
    private final String playerName;
    private final int inventorySlot;  // 0-9 (or 0-N based on config)

    // Offer Details
    private final OfferType type;
    private OfferState state;
    private boolean enabled;              // Checkbox state: false=DRAFT, true=ACTIVE (NEW)
    private final String itemStringID;
    private final int quantityTotal;      // Original quantity
    private int quantityRemaining;        // How much left to trade
    private final int pricePerItem;       // In coins

    // Duration & Timestamps
    private final int durationHours;     // Total duration in hours (per-offer)
    private final long createdTime;
    private long lastUpdateTime;
    private long expirationTime;          // 0 = no expiration

    // Transaction Tracking
    private long totalCoinsReceived;      // For sellers (from sales)
    private long totalCoinsSpent;         // For buyers (purchases)
    private int transactionCount;         // Number of trades

    // ===== CONSTRUCTORS =====

    /**
     * Create a new SELL offer (item in inventory slot).
     */
    public static GEOffer createSellOffer(long offerID, long playerAuth, String playerName,
                                          int inventorySlot, String itemStringID,
                                          int quantity, int pricePerItem, int durationHours) {
        return new GEOffer(offerID, playerAuth, playerName, inventorySlot,
            OfferType.SELL, itemStringID, quantity, pricePerItem, durationHours);
    }

    /** Convenience overload that uses config default duration. */
    public static GEOffer createSellOffer(long offerID, long playerAuth, String playerName,
                                          int inventorySlot, String itemStringID,
                                          int quantity, int pricePerItem) {
        return createSellOffer(offerID, playerAuth, playerName, inventorySlot, itemStringID,
            quantity, pricePerItem, ModConfig.GrandExchange.getDefaultSellDurationHours());
    }

    /**
     * Create a new BUY offer (coins in escrow) - Phase 10.
     */
    public static GEOffer createBuyOffer(long offerID, long playerAuth, String playerName,
                                         int inventorySlot, String itemStringID,
                                         int quantity, int pricePerItem) {
        if (!ModConfig.GrandExchange.enableBuyOffers) {
            ModLogger.warn("Buy offers disabled in config, cannot create buy offer");
            return null;
        }
        return new GEOffer(offerID, playerAuth, playerName, inventorySlot,
            OfferType.BUY, itemStringID, quantity, pricePerItem,
            ModConfig.GrandExchange.getDefaultSellDurationHours());
    }

    /**
     * Private constructor for new offers.
     */
    private GEOffer(long offerID, long playerAuth, String playerName, int inventorySlot,
                    OfferType type, String itemStringID, int quantity, int pricePerItem,
                    int durationHours) {
        this.offerID = offerID;
        this.playerAuth = playerAuth;
        this.playerName = playerName;
        this.inventorySlot = inventorySlot;
        this.type = type;
        this.state = OfferState.DRAFT;    // Start as DRAFT (checkbox unchecked)
        this.enabled = false;              // Checkbox starts unchecked
        this.itemStringID = itemStringID;
        this.quantityTotal = quantity;
        this.quantityRemaining = quantity;
        this.pricePerItem = pricePerItem;
        this.createdTime = System.currentTimeMillis();
        this.lastUpdateTime = this.createdTime;
        this.durationHours = ModConfig.GrandExchange.normalizeSellDurationHours(durationHours);
        
        // Set expiration if enabled
        if (ModConfig.GrandExchange.enableOfferExpiration) {
            long expirationMs = this.durationHours > 0
                ? TimeUnit.HOURS.toMillis(this.durationHours)
                : ModConfig.GrandExchange.getOfferExpirationMs();
            this.expirationTime = expirationMs > 0 ? createdTime + expirationMs : 0L;
        } else {
            this.expirationTime = 0L;
        }
        
        this.totalCoinsReceived = 0L;
        this.totalCoinsSpent = 0L;
        this.transactionCount = 0;

        ModLogger.debug("Created %s offer ID=%d: %s x%d @ %d coins (slot=%d, player=%s)",
            type, offerID, itemStringID, quantity, pricePerItem, inventorySlot, playerName);
    }

    /**
     * Private constructor for loading from save data.
     */
    private GEOffer(long offerID, long playerAuth, String playerName, int inventorySlot,
                    OfferType type, OfferState state, boolean enabled, String itemStringID,
                    int quantityTotal, int quantityRemaining, int pricePerItem,
                    long createdTime, long lastUpdateTime, long expirationTime,
                    long totalCoinsReceived, long totalCoinsSpent, int transactionCount,
                    int durationHours) {
        this.offerID = offerID;
        this.playerAuth = playerAuth;
        this.playerName = playerName;
        this.inventorySlot = inventorySlot;
        this.type = type;
        this.state = state;
        this.enabled = enabled;
        this.itemStringID = itemStringID;
        this.quantityTotal = quantityTotal;
        this.quantityRemaining = quantityRemaining;
        this.pricePerItem = pricePerItem;
        this.createdTime = createdTime;
        this.lastUpdateTime = lastUpdateTime;
        this.expirationTime = expirationTime;
        this.totalCoinsReceived = totalCoinsReceived;
        this.totalCoinsSpent = totalCoinsSpent;
        this.transactionCount = transactionCount;
        this.durationHours = ModConfig.GrandExchange.normalizeSellDurationHours(durationHours);
    }

    /**
     * Factory method for creating GEOffer from packet data (client-side).
     * Used when client receives sync packet from server.
     * Creates a lightweight client-side representation without full history.
     */
    public static GEOffer fromPacketData(long offerID, long playerAuth, int inventorySlot,
                                         String itemStringID, int quantityTotal, int quantityRemaining,
                                         int pricePerItem, boolean enabled, OfferState state,
                                         int durationHours) {
        long now = System.currentTimeMillis();
        
        // Create offer with minimal data (client doesn't need full history)
        return new GEOffer(
            offerID, playerAuth,
            "Unknown", // playerName not needed client-side
            inventorySlot,
            OfferType.SELL, // Currently only sell offers use this
            state,
            enabled,
            itemStringID,
            quantityTotal,
            quantityRemaining,
            pricePerItem,
            now, // createdTime (use current time as placeholder)
            now, // lastUpdateTime
            0L,  // expirationTime (not sent in packet)
            0L,  // totalCoinsReceived (not needed client-side)
            0L,  // totalCoinsSpent
            0,   // transactionCount
            durationHours
        );
    }

    /** Convenience overload when duration is not transmitted (legacy packets). */
    public static GEOffer fromPacketData(long offerID, long playerAuth, int inventorySlot,
                                         String itemStringID, int quantityTotal, int quantityRemaining,
                                         int pricePerItem, boolean enabled, OfferState state) {
        return fromPacketData(offerID, playerAuth, inventorySlot, itemStringID, quantityTotal,
            quantityRemaining, pricePerItem, enabled, state,
            ModConfig.GrandExchange.getDefaultSellDurationHours());
    }

    // ===== GETTERS =====

    public long getOfferID() {
        return offerID;
    }

    public long getPlayerAuth() {
        return playerAuth;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getInventorySlot() {
        return inventorySlot;
    }

    public OfferType getType() {
        return type;
    }

    public OfferState getState() {
        return state;
    }
    
    /**
     * Set the offer state directly (internal use for trade matching).
     */
    public void setState(OfferState newState) {
        this.state = newState;
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Update quantity remaining (internal use for trade matching).
     */
    public boolean reduceQuantity(int amount) {
        if (amount > quantityRemaining) {
            ModLogger.error("Cannot reduce quantity by more than remaining: amount=%d, remaining=%d", amount, quantityRemaining);
            return false;
        }
        this.quantityRemaining -= amount;
        this.lastUpdateTime = System.currentTimeMillis();
        
        // Update state based on completion
        if (quantityRemaining == 0) {
            this.state = OfferState.COMPLETED;
        } else if (quantityRemaining < quantityTotal) {
            this.state = OfferState.PARTIAL;
        }
        
        return true;
    }
    
    /**
     * Set quantity remaining (for rollback operations only).
     */
    public void setQuantityRemaining(int quantity) {
        this.quantityRemaining = quantity;
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    public boolean isEnabled() {
        return enabled;
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

    public int getQuantityTraded() {
        return quantityTotal - quantityRemaining;
    }

    public int getPricePerItem() {
        return pricePerItem;
    }

    public long getTotalValue() {
        return (long) quantityTotal * pricePerItem;
    }

    public long getRemainingValue() {
        return (long) quantityRemaining * pricePerItem;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public long getTotalCoinsReceived() {
        return totalCoinsReceived;
    }

    public long getTotalCoinsSpent() {
        return totalCoinsSpent;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public int getDurationHours() {
        return durationHours;
    }

    // ===== STATE CHECKS =====

    public boolean isActive() {
        return enabled && (state == OfferState.ACTIVE || state == OfferState.PARTIAL);
    }

    /**
     * Returns true if this offer can be cancelled.
     * DRAFT, ACTIVE, and PARTIAL offers can be cancelled.
     * COMPLETED and CANCELLED offers cannot.
     */
    public boolean isCancellable() {
        return state == OfferState.DRAFT || state == OfferState.ACTIVE || state == OfferState.PARTIAL;
    }

    public boolean isDraft() {
        return state == OfferState.DRAFT;
    }

    public boolean isCompleted() {
        return state == OfferState.COMPLETED;
    }

    public boolean isExpired() {
        if (expirationTime == 0L) {
            return false;
        }
        return System.currentTimeMillis() >= expirationTime;
    }
    
    /**
     * Check if offer can be enabled (checkbox can be checked).
     * Only DRAFT offers with checkbox unchecked can be enabled.
     */
    public boolean canBeEnabled() {
        return !enabled && state == OfferState.DRAFT;
    }
    
    public boolean canBeDisabled() {
        return enabled && (state == OfferState.ACTIVE || state == OfferState.PARTIAL);
    }

    public boolean canBeFulfilled() {
        return isActive() && quantityRemaining > 0;
    }

    public float getCompletionPercent() {
        if (quantityTotal == 0) return 0f;
        return ((float) getQuantityTraded() / quantityTotal) * 100f;
    }

    // ===== STATE TRANSITIONS =====
    
    /**
     * Enable offer (checkbox checked: DRAFT → ACTIVE).
     * This makes the offer visible on the market and matchable.
     * 
     * State Changes:
     * - Sets enabled = true (checkbox checked)
     * - Sets state = ACTIVE (on market)
     * - Updates lastUpdateTime
     * 
     * After this call:
     * - isActive() returns true
     * - Offer appears in market listings
     * - Offer can be matched with buyers/sellers
     * 
     * @return true if enabled successfully, false if already enabled or invalid state
     */
    public boolean enable() {
        if (!canBeEnabled()) {
            ModLogger.warn("Cannot enable offer ID=%d: state=%s, enabled=%b", 
                offerID, state, enabled);
            return false;
        }
        
        this.enabled = true;
        this.state = OfferState.ACTIVE;
        this.lastUpdateTime = System.currentTimeMillis();
        
        ModLogger.info("Offer ID=%d enabled: %s %s x%d @ %d coins (slot=%d)",
            offerID, type, itemStringID, quantityRemaining, pricePerItem, inventorySlot);
        return true;
    }
    
    /**
     * Disable offer (checkbox unchecked: ACTIVE/PARTIAL → DRAFT).
     * This removes the offer from the market but keeps it in the slot.
     * 
     * State Changes:
     * - Sets enabled = false (checkbox unchecked)
     * - Sets state = DRAFT (off market)
     * - Updates lastUpdateTime
     * - PRESERVES remaining quantity (can re-enable later)
     * 
     * After this call:
     * - isActive() returns false
     * - Offer removed from market listings
     * - Offer cannot be matched
     * - Item remains in inventory slot
     * 
     * @return true if disabled successfully, false if already disabled or invalid state
     */
    public boolean disable() {
        if (!canBeDisabled()) {
            ModLogger.warn("Cannot disable offer ID=%d: state=%s, enabled=%b", 
                offerID, state, enabled);
            return false;
        }
        
        this.enabled = false;
        this.state = OfferState.DRAFT;
        this.lastUpdateTime = System.currentTimeMillis();
        
        ModLogger.info("Offer ID=%d disabled: %s %s x%d @ %d coins (slot=%d)",
            offerID, type, itemStringID, quantityRemaining, pricePerItem, inventorySlot);
        return true;
    }

    public void cancel() {
        if (isActive() || state == OfferState.DRAFT) {
            state = OfferState.CANCELLED;
            enabled = false;
            lastUpdateTime = System.currentTimeMillis();
            ModLogger.info("Offer ID=%d cancelled by player (remaining: %d/%d)",
                offerID, quantityRemaining, quantityTotal);
        }
    }

    public void expire() {
        if (isActive() || state == OfferState.DRAFT) {
            state = OfferState.EXPIRED;
            enabled = false;
            lastUpdateTime = System.currentTimeMillis();
            ModLogger.info("Offer ID=%d expired (unfulfilled: %d/%d)",
                offerID, quantityRemaining, quantityTotal);
        }
    }

    /**
     * Record a trade transaction (partial or full fulfillment).
     * @param quantityTraded How many items were traded
     * @param coinsExchanged Coin amount (positive for seller, negative for buyer)
     * @return true if offer completed, false if still partial
     */
    public boolean recordTrade(int quantityTraded, long coinsExchanged) {
        if (quantityTraded <= 0 || quantityTraded > quantityRemaining) {
            ModLogger.error("Invalid trade quantity for offer ID=%d: %d (remaining: %d)",
                offerID, quantityTraded, quantityRemaining);
            return false;
        }

        quantityRemaining -= quantityTraded;
        transactionCount++;
        lastUpdateTime = System.currentTimeMillis();

        if (type == OfferType.SELL) {
            totalCoinsReceived += coinsExchanged;
        } else {
            totalCoinsSpent += Math.abs(coinsExchanged);
        }

        if (quantityRemaining == 0) {
            state = OfferState.COMPLETED;
            ModLogger.info("Offer ID=%d completed: %s %s x%d @ %d coins (total: %d coins)",
                offerID, type, itemStringID, quantityTotal, pricePerItem,
                type == OfferType.SELL ? totalCoinsReceived : totalCoinsSpent);
            return true;
        } else {
            if (state == OfferState.ACTIVE) {
                state = OfferState.PARTIAL;
            }
            ModLogger.debug("Offer ID=%d partial trade: %d/%d traded, %d remaining",
                offerID, getQuantityTraded(), quantityTotal, quantityRemaining);
            return false;
        }
    }

    // ===== INVENTORY ITEM =====

    /**
     * Get the item as an InventoryItem (for SELL offers).
     * @return InventoryItem or null if item doesn't exist
     */
    public InventoryItem getInventoryItem() {
        Item item = ItemRegistry.getItem(itemStringID);
        if (item == null) {
            ModLogger.error("Failed to get item for offer ID=%d: item '%s' not found",
                offerID, itemStringID);
            return null;
        }
        return new InventoryItem(item, quantityRemaining);
    }

    /**
     * Get the full quantity item (for returns/cancellations).
     */
    public InventoryItem getFullInventoryItem() {
        Item item = ItemRegistry.getItem(itemStringID);
        if (item == null) {
            return null;
        }
        return new InventoryItem(item, quantityTotal);
    }

    // ===== VALIDATION =====

    /**
     * Validate offer can be created with current config.
     */
    public boolean validateOfferRules() {
        if (!ModConfig.GrandExchange.isValidPrice(pricePerItem)) {
            ModLogger.warn("Offer ID=%d has invalid price: %d (min=%d, max=%d)",
                offerID, pricePerItem, ModConfig.GrandExchange.minPricePerItem,
                ModConfig.GrandExchange.maxPricePerItem);
            return false;
        }

        if (type == OfferType.BUY && !ModConfig.GrandExchange.enableBuyOffers) {
            ModLogger.warn("Offer ID=%d is BUY type but buy offers are disabled", offerID);
            return false;
        }

        if (quantityTotal <= 0 || quantityRemaining < 0) {
            ModLogger.error("Offer ID=%d has invalid quantities: total=%d, remaining=%d",
                offerID, quantityTotal, quantityRemaining);
            return false;
        }

        return true;
    }

    // ===== PERSISTENCE =====

    /**
     * Save offer to SaveData.
     */
    public void addSaveData(SaveData save) {
        save.addLong("offerID", offerID);
        save.addLong("playerAuth", playerAuth);
        save.addUnsafeString("playerName", playerName);
        save.addInt("inventorySlot", inventorySlot);
        save.addInt("type", type.ordinal());
        save.addInt("state", state.ordinal());
        save.addBoolean("enabled", enabled);
        save.addUnsafeString("itemStringID", itemStringID);
        save.addInt("quantityTotal", quantityTotal);
        save.addInt("quantityRemaining", quantityRemaining);
        save.addInt("pricePerItem", pricePerItem);
        save.addLong("createdTime", createdTime);
        save.addLong("lastUpdateTime", lastUpdateTime);
        save.addLong("expirationTime", expirationTime);
        save.addLong("totalCoinsReceived", totalCoinsReceived);
        save.addLong("totalCoinsSpent", totalCoinsSpent);
        save.addInt("transactionCount", transactionCount);
        save.addInt("durationHours", durationHours);
    }

    /**
     * Load offer from LoadData.
     */
    public static GEOffer fromLoadData(LoadData load) {
        long offerID = load.getLong("offerID");
        long playerAuth = load.getLong("playerAuth");
        String playerName = load.getUnsafeString("playerName");
        int inventorySlot = load.getInt("inventorySlot");
        OfferType type = OfferType.values()[load.getInt("type")];
        OfferState state = OfferState.values()[load.getInt("state")];
        boolean enabled = load.getBoolean("enabled", false);  // Default false for old saves
        String itemStringID = load.getUnsafeString("itemStringID");
        int quantityTotal = load.getInt("quantityTotal");
        int quantityRemaining = load.getInt("quantityRemaining");
        int pricePerItem = load.getInt("pricePerItem");
        long createdTime = load.getLong("createdTime");
        long lastUpdateTime = load.getLong("lastUpdateTime");
        long expirationTime = load.getLong("expirationTime");
        long totalCoinsReceived = load.getLong("totalCoinsReceived");
        long totalCoinsSpent = load.getLong("totalCoinsSpent");
        int transactionCount = load.getInt("transactionCount");
        int durationHours = load.getInt("durationHours", ModConfig.GrandExchange.getDefaultSellDurationHours());

        return new GEOffer(offerID, playerAuth, playerName, inventorySlot, type, state, enabled,
            itemStringID, quantityTotal, quantityRemaining, pricePerItem, createdTime,
            lastUpdateTime, expirationTime, totalCoinsReceived, totalCoinsSpent, transactionCount,
            durationHours);
    }

    @Override
    public String toString() {
        return String.format("GEOffer[ID=%d, %s, %s, enabled=%b, %s x%d@%dc, slot=%d, player=%s]",
            offerID, type, state, enabled, itemStringID, quantityRemaining, pricePerItem,
            inventorySlot, playerName);
    }
}
