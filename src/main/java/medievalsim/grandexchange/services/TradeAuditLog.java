package medievalsim.grandexchange.services;

import medievalsim.grandexchange.services.TradeTransaction.TradeResult;
import medievalsim.util.ModLogger;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Comprehensive audit logging system for Grand Exchange trades.
 * 
 * Purpose:
 * - Track all completed trades for analytics and fraud detection
 * - Provide audit trail for economic analysis
 * - Enable admin oversight of market activity
 * 
 * Storage:
 * - Last N trades globally (ring buffer)
 * - Last N trades per item (ring buffer)
 * - Last N trades per player (ring buffer)
 * 
 * Thread-safe using concurrent collections.
 */
public class TradeAuditLog {
    
    // Configuration
    private final int maxGlobalTrades;
    private final int maxTradesPerItem;
    private final int maxTradesPerPlayer;
    
    // Global trade log (ring buffer via LinkedList)
    private final LinkedList<AuditEntry> globalLog = new LinkedList<>();
    
    // Per-item trade logs
    private final Map<String, LinkedList<AuditEntry>> itemLogs = new ConcurrentHashMap<>();
    
    // Per-player trade logs (buyer and seller)
    private final Map<Long, LinkedList<AuditEntry>> playerLogs = new ConcurrentHashMap<>();
    
    // Statistics
    private long totalTradesLogged = 0;
    private long totalCoinsTraded = 0;
    
    /**
     * Create audit log with configurable limits.
     */
    public TradeAuditLog(int maxGlobalTrades, int maxTradesPerItem, int maxTradesPerPlayer) {
        this.maxGlobalTrades = maxGlobalTrades;
        this.maxTradesPerItem = maxTradesPerItem;
        this.maxTradesPerPlayer = maxTradesPerPlayer;
    }
    
    /**
     * Log a completed trade.
     */
    public synchronized void logTrade(TradeResult trade) {
        AuditEntry entry = new AuditEntry(
            trade.getBuyOrderID(),
            trade.getSellOfferID(),
            trade.getBuyerAuth(),
            trade.getSellerAuth(),
            trade.getItemStringID(),
            trade.getQuantity(),
            trade.getPricePerItem(),
            trade.getTotalCoins(),
            trade.getTax(),
            trade.getSellerProceeds(),
            trade.getTimestamp()
        );
        
        // Add to global log
        globalLog.addLast(entry);
        if (globalLog.size() > maxGlobalTrades) {
            globalLog.removeFirst();
        }
        
        // Add to item log
        String itemID = trade.getItemStringID();
        LinkedList<AuditEntry> itemLog = itemLogs.computeIfAbsent(itemID, k -> new LinkedList<>());
        itemLog.addLast(entry);
        if (itemLog.size() > maxTradesPerItem) {
            itemLog.removeFirst();
        }
        
        // Add to buyer's log
        addToPlayerLog(trade.getBuyerAuth(), entry);
        
        // Add to seller's log
        addToPlayerLog(trade.getSellerAuth(), entry);
        
        // Update statistics
        totalTradesLogged++;
        totalCoinsTraded += trade.getTotalCoins();
        
        ModLogger.debug("Logged trade: %s", entry);
    }
    
    private void addToPlayerLog(long playerAuth, AuditEntry entry) {
        LinkedList<AuditEntry> playerLog = playerLogs.computeIfAbsent(playerAuth, k -> new LinkedList<>());
        playerLog.addLast(entry);
        if (playerLog.size() > maxTradesPerPlayer) {
            playerLog.removeFirst();
        }
    }
    
    /**
     * Get recent global trades.
     */
    public synchronized List<AuditEntry> getRecentGlobalTrades(int limit) {
        int count = Math.min(limit, globalLog.size());
        return new ArrayList<>(globalLog.subList(Math.max(0, globalLog.size() - count), globalLog.size()));
    }
    
    /**
     * Get recent trades for an item.
     */
    public synchronized List<AuditEntry> getRecentTradesForItem(String itemID, int limit) {
        LinkedList<AuditEntry> itemLog = itemLogs.get(itemID);
        if (itemLog == null) {
            return Collections.emptyList();
        }
        
        int count = Math.min(limit, itemLog.size());
        return new ArrayList<>(itemLog.subList(Math.max(0, itemLog.size() - count), itemLog.size()));
    }
    
    /**
     * Get recent trades for a player (both buying and selling).
     */
    public synchronized List<AuditEntry> getRecentTradesForPlayer(long playerAuth, int limit) {
        LinkedList<AuditEntry> playerLog = playerLogs.get(playerAuth);
        if (playerLog == null) {
            return Collections.emptyList();
        }
        
        int count = Math.min(limit, playerLog.size());
        return new ArrayList<>(playerLog.subList(Math.max(0, playerLog.size() - count), playerLog.size()));
    }
    
    /**
     * Find suspicious trades (fraud detection).
     * Returns trades that match suspicious patterns.
     */
    public synchronized List<AuditEntry> findSuspiciousTrades() {
        List<AuditEntry> suspicious = new ArrayList<>();
        
        // Pattern 1: Self-trading (buyer == seller, shouldn't happen but check anyway)
        for (AuditEntry entry : globalLog) {
            if (entry.getBuyerAuth() == entry.getSellerAuth()) {
                suspicious.add(entry);
            }
        }
        
        // Pattern 2: Extreme price outliers (10x+ market average)
        Map<String, List<AuditEntry>> byItem = globalLog.stream()
            .collect(Collectors.groupingBy(AuditEntry::getItemStringID));
        
        for (Map.Entry<String, List<AuditEntry>> itemEntry : byItem.entrySet()) {
            List<AuditEntry> trades = itemEntry.getValue();
            if (trades.size() < 5) continue; // Need enough data
            
            // Calculate average price
            double avgPrice = trades.stream()
                .mapToInt(AuditEntry::getPricePerItem)
                .average()
                .orElse(0);
            
            // Find outliers (10x average)
            for (AuditEntry trade : trades) {
                if (trade.getPricePerItem() > avgPrice * 10) {
                    suspicious.add(trade);
                }
            }
        }
        
        return suspicious;
    }
    
    /**
     * Get trading statistics for a player.
     */
    public synchronized PlayerTradingStats getPlayerStats(long playerAuth) {
        LinkedList<AuditEntry> playerLog = playerLogs.get(playerAuth);
        if (playerLog == null) {
            return new PlayerTradingStats(playerAuth, 0, 0, 0, 0, 0, 0);
        }
        
        int buyCount = 0;
        int sellCount = 0;
        long coinsSpent = 0;
        long coinsReceived = 0;
        int itemsBought = 0;
        int itemsSold = 0;
        
        for (AuditEntry entry : playerLog) {
            if (entry.getBuyerAuth() == playerAuth) {
                buyCount++;
                coinsSpent += entry.getTotalCoins();
                itemsBought += entry.getQuantity();
            }
            if (entry.getSellerAuth() == playerAuth) {
                sellCount++;
                coinsReceived += entry.getSellerProceeds();
                itemsSold += entry.getQuantity();
            }
        }
        
        return new PlayerTradingStats(playerAuth, buyCount, sellCount, 
            coinsSpent, coinsReceived, itemsBought, itemsSold);
    }
    
    /**
     * Get market summary statistics.
     */
    public synchronized MarketStats getMarketStats() {
        Map<String, Integer> tradesByItem = new HashMap<>();
        Map<Long, Integer> tradesByPlayer = new HashMap<>();
        
        for (AuditEntry entry : globalLog) {
            tradesByItem.merge(entry.getItemStringID(), 1, Integer::sum);
            tradesByPlayer.merge(entry.getBuyerAuth(), 1, Integer::sum);
            tradesByPlayer.merge(entry.getSellerAuth(), 1, Integer::sum);
        }
        
        String mostTradedItem = tradesByItem.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("none");
        
        long mostActivePlayer = tradesByPlayer.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(0L);
        
        return new MarketStats(
            totalTradesLogged,
            totalCoinsTraded,
            tradesByItem.size(),
            tradesByPlayer.size(),
            mostTradedItem,
            mostActivePlayer
        );
    }
    
    /**
     * Clear all logs (admin function).
     */
    public synchronized void clearAll() {
        globalLog.clear();
        itemLogs.clear();
        playerLogs.clear();
        ModLogger.info("Cleared all trade audit logs");
    }
    
    /**
     * Save audit log data.
     */
    public synchronized void addSaveData(SaveData save) {
        // Save global log
        SaveData globalSave = new SaveData("GLOBAL_LOG");
        globalSave.addInt("size", globalLog.size());
        int i = 0;
        for (AuditEntry entry : globalLog) {
            SaveData entrySave = new SaveData("ENTRY_" + i);
            entry.addSaveData(entrySave);
            globalSave.addSaveData(entrySave);
            i++;
        }
        save.addSaveData(globalSave);
        
        // Save statistics
        save.addLong("totalTradesLogged", totalTradesLogged);
        save.addLong("totalCoinsTraded", totalCoinsTraded);
    }
    
    /**
     * Load audit log data.
     */
    public synchronized void applyLoadData(LoadData save) {
        // Load global log
        LoadData globalLoad = save.getFirstLoadDataByName("GLOBAL_LOG");
        if (globalLoad != null) {
            int size = globalLoad.getInt("size", 0);
            for (int i = 0; i < size; i++) {
                LoadData entryLoad = globalLoad.getFirstLoadDataByName("ENTRY_" + i);
                if (entryLoad != null) {
                    AuditEntry entry = AuditEntry.fromLoadData(entryLoad);
                    if (entry != null) {
                        logTrade(TradeResult.fromAuditEntry(entry));
                    }
                }
            }
        }
        
        // Load statistics
        totalTradesLogged = save.getLong("totalTradesLogged", 0);
        totalCoinsTraded = save.getLong("totalCoinsTraded", 0);
    }
    
    // ===== NESTED CLASSES =====
    
    /**
     * Immutable audit entry for a single trade.
     */
    public static class AuditEntry {
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
        
        public AuditEntry(long buyOrderID, long sellOfferID, long buyerAuth, long sellerAuth,
                         String itemStringID, int quantity, int pricePerItem, int totalCoins,
                         int tax, int sellerProceeds, long timestamp) {
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
        
        public void addSaveData(SaveData save) {
            save.addLong("buyOrderID", buyOrderID);
            save.addLong("sellOfferID", sellOfferID);
            save.addLong("buyerAuth", buyerAuth);
            save.addLong("sellerAuth", sellerAuth);
            save.addUnsafeString("itemStringID", itemStringID);
            save.addInt("quantity", quantity);
            save.addInt("pricePerItem", pricePerItem);
            save.addInt("totalCoins", totalCoins);
            save.addInt("tax", tax);
            save.addInt("sellerProceeds", sellerProceeds);
            save.addLong("timestamp", timestamp);
        }
        
        public static AuditEntry fromLoadData(LoadData save) {
            return new AuditEntry(
                save.getLong("buyOrderID"),
                save.getLong("sellOfferID"),
                save.getLong("buyerAuth"),
                save.getLong("sellerAuth"),
                save.getUnsafeString("itemStringID"),
                save.getInt("quantity"),
                save.getInt("pricePerItem"),
                save.getInt("totalCoins"),
                save.getInt("tax"),
                save.getInt("sellerProceeds"),
                save.getLong("timestamp")
            );
        }
        
        @Override
        public String toString() {
            return String.format("Trade[buyer=%d, seller=%d, item=%s, qty=%d, price=%d]",
                buyerAuth, sellerAuth, itemStringID, quantity, pricePerItem);
        }
    }
    
    /**
     * Player trading statistics.
     */
    public static class PlayerTradingStats {
        private final long playerAuth;
        private final int buyCount;
        private final int sellCount;
        private final long coinsSpent;
        private final long coinsReceived;
        private final int itemsBought;
        private final int itemsSold;
        
        public PlayerTradingStats(long playerAuth, int buyCount, int sellCount,
                                 long coinsSpent, long coinsReceived,
                                 int itemsBought, int itemsSold) {
            this.playerAuth = playerAuth;
            this.buyCount = buyCount;
            this.sellCount = sellCount;
            this.coinsSpent = coinsSpent;
            this.coinsReceived = coinsReceived;
            this.itemsBought = itemsBought;
            this.itemsSold = itemsSold;
        }
        
        // Getters
        public long getPlayerAuth() { return playerAuth; }
        public int getBuyCount() { return buyCount; }
        public int getSellCount() { return sellCount; }
        public long getCoinsSpent() { return coinsSpent; }
        public long getCoinsReceived() { return coinsReceived; }
        public int getItemsBought() { return itemsBought; }
        public int getItemsSold() { return itemsSold; }
        public long getNetCoins() { return coinsReceived - coinsSpent; }
    }
    
    /**
     * Market-wide statistics.
     */
    public static class MarketStats {
        private final long totalTrades;
        private final long totalCoins;
        private final int uniqueItems;
        private final int uniquePlayers;
        private final String mostTradedItem;
        private final long mostActivePlayer;
        
        public MarketStats(long totalTrades, long totalCoins, int uniqueItems,
                          int uniquePlayers, String mostTradedItem, long mostActivePlayer) {
            this.totalTrades = totalTrades;
            this.totalCoins = totalCoins;
            this.uniqueItems = uniqueItems;
            this.uniquePlayers = uniquePlayers;
            this.mostTradedItem = mostTradedItem;
            this.mostActivePlayer = mostActivePlayer;
        }
        
        // Getters
        public long getTotalTrades() { return totalTrades; }
        public long getTotalCoins() { return totalCoins; }
        public int getUniqueItems() { return uniqueItems; }
        public int getUniquePlayers() { return uniquePlayers; }
        public String getMostTradedItem() { return mostTradedItem; }
        public long getMostActivePlayer() { return mostActivePlayer; }
    }
}
