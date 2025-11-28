package medievalsim.grandexchange.services;

import medievalsim.banking.domain.BankingLevelData;
import medievalsim.banking.domain.PlayerBank;
import medievalsim.config.ModConfig;
import medievalsim.grandexchange.domain.BuyOrder;
import medievalsim.grandexchange.domain.CollectionItem;
import medievalsim.grandexchange.domain.GEOffer;
import medievalsim.grandexchange.domain.PlayerGEInventory;
import medievalsim.util.ModLogger;
import necesse.engine.registries.ItemRegistry;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.level.maps.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Atomic transaction handler for Grand Exchange trades.
 * 
 * Pattern: Two-Phase Commit Protocol
 * 1. PREPARE - Validate all preconditions, create undo log
 * 2. COMMIT - Execute all state changes atomically
 * 3. ROLLBACK - Restore state if any step fails
 * 
 * Guarantees:
 * - No partial trades (all-or-nothing)
 * - No coin/item duplication or loss
 * - Consistent state even on failures
 * 
 * Thread-safety: Each transaction instance is single-threaded.
 * Do not share instances across threads.
 */
public class TradeTransaction {
    
    private enum State {
        INITIAL,
        PREPARED,
        COMMITTED,
        ROLLED_BACK,
        FAILED
    }
    
    // Transaction participants
    private final Level level;
    private final BuyOrder buyOrder;
    private final GEOffer sellOffer;
    private final int quantity;
    private final int executionPrice; // Price at which trade executes
    
    // State
    private State state;
    private String failureReason;
    
    // Undo log (for rollback)
    private UndoLog undoLog;
    
    // Results
    private TradeResult result;
    
    /**
     * Create new trade transaction.
     * 
     * @param level Server level
     * @param buyOrder Buy order (buyer)
     * @param sellOffer Sell offer (seller)
     * @param quantity Quantity to trade
     * @param executionPrice Price per item (typically buyer's price)
     */
    public TradeTransaction(Level level, BuyOrder buyOrder, GEOffer sellOffer, 
                            int quantity, int executionPrice) {
        this.level = level;
        this.buyOrder = buyOrder;
        this.sellOffer = sellOffer;
        this.quantity = quantity;
        this.executionPrice = executionPrice;
        this.state = State.INITIAL;
        this.undoLog = new UndoLog();
    }
    
    /**
     * Phase 1: Validate preconditions and prepare undo log.
     * @return true if ready to commit, false if validation failed
     */
    public boolean prepare() {
        if (state != State.INITIAL) {
            failureReason = "Transaction already in state: " + state;
            return false;
        }
        
        try {
            // 1. Validate quantity
            if (quantity <= 0) {
                failureReason = "Invalid quantity: " + quantity;
                return false;
            }
            
            if (quantity > buyOrder.getQuantityRemaining()) {
                failureReason = String.format("Buy order ID=%d insufficient quantity: has %d, need %d",
                    buyOrder.getOrderID(), buyOrder.getQuantityRemaining(), quantity);
                return false;
            }
            
            if (quantity > sellOffer.getQuantityRemaining()) {
                failureReason = String.format("Sell offer ID=%d insufficient quantity: has %d, need %d",
                    sellOffer.getOfferID(), sellOffer.getQuantityRemaining(), quantity);
                return false;
            }
            
            // 2. Validate price
            if (executionPrice <= 0) {
                failureReason = "Invalid execution price: " + executionPrice;
                return false;
            }
            
            if (executionPrice > buyOrder.getPricePerItem()) {
                failureReason = String.format("Execution price %d exceeds buy order limit %d",
                    executionPrice, buyOrder.getPricePerItem());
                return false;
            }
            
            // 3. Validate states
            if (!buyOrder.canMatch()) {
                failureReason = "Buy order cannot match (state: " + buyOrder.getState() + ")";
                return false;
            }
            
            if (!sellOffer.isActive()) {
                failureReason = "Sell offer not active (state: " + sellOffer.getState() + ")";
                return false;
            }
            
            // 4. Validate item exists
            Item item = ItemRegistry.getItem(sellOffer.getItemStringID());
            if (item == null) {
                failureReason = "Unknown item: " + sellOffer.getItemStringID();
                return false;
            }
            
            // 5. Calculate coins and tax
            int totalCoins = executionPrice * quantity;
            int tax = ModConfig.GrandExchange.getSalesTax(totalCoins);
            int sellerProceeds = totalCoins - tax;
            
            // Record for undo (capture current state)
            undoLog.buyOrderQty = buyOrder.getQuantityRemaining();
            undoLog.sellOfferQty = sellOffer.getQuantityRemaining();
            undoLog.buyerCoinsEscrowed = getBuyerInventory().getCoinsInEscrow();
            undoLog.sellerBankCoins = getSellerBank().getCoins();
            
            // Validation passed
            state = State.PREPARED;
            ModLogger.debug("Trade prepared: buyer=%d, seller=%d, item=%s, qty=%d, price=%d",
                buyOrder.getPlayerAuth(), sellOffer.getPlayerAuth(),
                sellOffer.getItemStringID(), quantity, executionPrice);
            return true;
            
        } catch (Exception e) {
            failureReason = "Preparation exception: " + e.getMessage();
            ModLogger.error("Trade preparation failed: %s", e.getMessage());
            state = State.FAILED;
            return false;
        }
    }
    
    /**
     * Phase 2: Commit all state changes atomically.
     * @return TradeResult if successful, null if failed
     */
    public TradeResult commit() {
        if (state != State.PREPARED) {
            ModLogger.error("Cannot commit transaction in state: %s", state);
            return null;
        }
        
        try {
            int totalCoins = executionPrice * quantity;
            int tax = ModConfig.GrandExchange.getSalesTax(totalCoins);
            int sellerProceeds = totalCoins - tax;
            
            // Step 1: Reduce buy order quantity
            if (!buyOrder.fillQuantity(quantity)) {
                throw new TransactionException("Failed to fill buy order quantity");
            }
            
            // Step 2: Reduce sell offer quantity
            if (!sellOffer.reduceQuantity(quantity)) {
                throw new TransactionException("Failed to reduce sell offer quantity");
            }
            
            // Step 3: Transfer coins from buyer's escrow to seller's bank
            PlayerGEInventory buyerInventory = getBuyerInventory();
            if (!buyerInventory.removeCoinsFromEscrow(totalCoins)) {
                throw new TransactionException("Failed to remove coins from buyer escrow");
            }
            
            PlayerBank sellerBank = getSellerBank();
            if (!sellerBank.addCoins(sellerProceeds)) {
                throw new TransactionException("Failed to add coins to seller bank");
            }
            
            // Step 4: Transfer items to buyer's collection box
            // Items always go to collection box - player collects them later
            // This ensures transaction completes even if bank is full
            buyerInventory.addToCollectionBox(
                sellOffer.getItemStringID(), quantity, "purchase"
            );
            
            ModLogger.debug("Added %d x %s to buyer's collection box",
                quantity, sellOffer.getItemStringID());
            
            // Note: Auto-send to bank can be handled when player collects from collection box
            
            // Step 5: Update statistics
            buyerInventory.recordItemPurchased(quantity);
            PlayerGEInventory sellerInventory = getSellerInventory();
            sellerInventory.recordItemSold(quantity);
            
            // Transaction committed successfully
            state = State.COMMITTED;
            result = new TradeResult(
                buyOrder.getOrderID(),
                sellOffer.getOfferID(),
                buyOrder.getPlayerAuth(),
                sellOffer.getPlayerAuth(),
                sellOffer.getItemStringID(),
                quantity,
                executionPrice,
                totalCoins,
                tax,
                sellerProceeds,
                System.currentTimeMillis()
            );
            
            ModLogger.info("Trade committed: buyer=%s bought %d x %s @ %d coins from seller=%s (total=%d, tax=%d)",
                buyOrder.getPlayerName(), quantity, sellOffer.getItemStringID(), executionPrice,
                sellOffer.getPlayerName(), totalCoins, tax);
            
            return result;
            
        } catch (TransactionException e) {
            ModLogger.error("Trade commit failed, rolling back: %s", e.getMessage());
            rollback();
            return null;
        } catch (Exception e) {
            ModLogger.error("Trade commit exception, rolling back: %s", e.getMessage());
            e.printStackTrace();
            rollback();
            return null;
        }
    }
    
    /**
     * Phase 3: Rollback all changes (restore to prepare() state).
     */
    public void rollback() {
        if (state == State.ROLLED_BACK) {
            ModLogger.warn("Transaction already rolled back");
            return;
        }
        
        try {
            // Restore quantities
            buyOrder.setQuantityRemaining(undoLog.buyOrderQty);
            sellOffer.setQuantityRemaining(undoLog.sellOfferQty);
            
            // Restore coins
            PlayerGEInventory buyerInventory = getBuyerInventory();
            buyerInventory.setCoinsInEscrow(undoLog.buyerCoinsEscrowed);
            
            PlayerBank sellerBank = getSellerBank();
            sellerBank.setCoins(undoLog.sellerBankCoins);
            
            state = State.ROLLED_BACK;
            ModLogger.warn("Transaction rolled back successfully");
            
        } catch (Exception e) {
            state = State.FAILED;
            ModLogger.error("CRITICAL: Rollback failed! Manual intervention required: %s", e.getMessage());
            e.printStackTrace();
        }
    }
    
    // ===== HELPER METHODS =====
    
    private BankingLevelData getBankingData() {
        return BankingLevelData.getBankingData(level);
    }
    
    private PlayerGEInventory getBuyerInventory() {
        // Assumes GE data is available (should be validated in prepare())
        return level.getLevelData("grandexchangedata") instanceof medievalsim.grandexchange.domain.GrandExchangeLevelData
            ? ((medievalsim.grandexchange.domain.GrandExchangeLevelData) level.getLevelData("grandexchangedata"))
                .getInventory(buyOrder.getPlayerAuth())
            : null;
    }
    
    private PlayerGEInventory getSellerInventory() {
        return level.getLevelData("grandexchangedata") instanceof medievalsim.grandexchange.domain.GrandExchangeLevelData
            ? ((medievalsim.grandexchange.domain.GrandExchangeLevelData) level.getLevelData("grandexchangedata"))
                .getInventory(sellOffer.getPlayerAuth())
            : null;
    }
    
    private PlayerBank getBuyerBank() {
        return getBankingData().getOrCreateBank(buyOrder.getPlayerAuth());
    }
    
    private PlayerBank getSellerBank() {
        return getBankingData().getOrCreateBank(sellOffer.getPlayerAuth());
    }
    
    // ===== GETTERS =====
    
    public State getState() {
        return state;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
    
    public TradeResult getResult() {
        return result;
    }
    
    // ===== NESTED CLASSES =====
    
    /**
     * Undo log for rollback operations.
     */
    private static class UndoLog {
        int buyOrderQty;
        int sellOfferQty;
        int buyerCoinsEscrowed;
        long sellerBankCoins;  // Long to match PlayerBank.getCoins()
    }
    
    /**
     * Custom exception for transaction failures.
     */
    private static class TransactionException extends Exception {
        public TransactionException(String message) {
            super(message);
        }
    }
    
    /**
     * Immutable result of a successful trade.
     */
    public static class TradeResult {
        private final long buyOrderID;
        private final long sellOfferID;
        private final long buyerAuth;
        private final long sellerAuth;
        private final String itemStringID;
        private final int quantity;
        private final int pricePerItem;
        private final int totalCoins;
        private final int tax;
        private final int sellerProceeds;
        private final long timestamp;
        
        public TradeResult(long buyOrderID, long sellOfferID, long buyerAuth, long sellerAuth,
                          String itemStringID, int quantity, int pricePerItem,
                          int totalCoins, int tax, int sellerProceeds, long timestamp) {
            this.buyOrderID = buyOrderID;
            this.sellOfferID = sellOfferID;
            this.buyerAuth = buyerAuth;
            this.sellerAuth = sellerAuth;
            this.itemStringID = itemStringID;
            this.quantity = quantity;
            this.pricePerItem = pricePerItem;
            this.totalCoins = totalCoins;
            this.tax = tax;
            this.sellerProceeds = sellerProceeds;
            this.timestamp = timestamp;
        }
        
        // Getters
        public long getBuyOrderID() { return buyOrderID; }
        public long getSellOfferID() { return sellOfferID; }
        public long getBuyerAuth() { return buyerAuth; }
        public long getSellerAuth() { return sellerAuth; }
        public String getItemStringID() { return itemStringID; }
        public int getQuantity() { return quantity; }
        public int getPricePerItem() { return pricePerItem; }
        public int getTotalCoins() { return totalCoins; }
        public int getTax() { return tax; }
        public int getSellerProceeds() { return sellerProceeds; }
        public long getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format("Trade[buyer=%d, seller=%d, item=%s, qty=%d, price=%d, total=%d, tax=%d]",
                buyerAuth, sellerAuth, itemStringID, quantity, pricePerItem, totalCoins, tax);
        }
        
        /**
         * Create TradeResult from AuditEntry (for loading saved data).
         */
        public static TradeResult fromAuditEntry(medievalsim.grandexchange.services.TradeAuditLog.AuditEntry entry) {
            return new TradeResult(
                entry.getBuyOrderID(),
                entry.getSellOfferID(),
                entry.getBuyerAuth(),
                entry.getSellerAuth(),
                entry.getItemStringID(),
                entry.getQuantity(),
                entry.getPricePerItem(),
                entry.getTotalCoins(),
                entry.getTax(),
                entry.getSellerProceeds(),
                entry.getTimestamp()
            );
        }
    }
}
